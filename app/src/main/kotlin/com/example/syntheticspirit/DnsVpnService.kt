package com.example.syntheticspirit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.collection.LruCache
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.DnsLog
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class DnsVpnService : VpnService() {
    companion object {
        private val _isRunning = mutableStateOf(false)
        val isRunning: State<Boolean> = _isRunning

        private val _blockedCount = mutableStateOf(0)
        val blockedCount: State<Int> = _blockedCount

        private val _serviceStartTime = mutableLongStateOf(0L)
        val serviceStartTime: State<Long> = _serviceStartTime
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    
    private val lookupCache = LruCache<String, Boolean>(2000)
    private var bloomFilter: BloomFilter<CharSequence>? = null
    private lateinit var whitelistManager: WhitelistManager
    
    private lateinit var db: AppDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val upstreamDns = "8.8.8.8"
    private val dnsExecutor = Executors.newFixedThreadPool(10)
    private var persistentSocket: DatagramSocket? = null

    private val CHANNEL_ID = "vpn_notifications"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        whitelistManager = WhitelistManager(this)
        createNotificationChannel()
        loadBloomFilter()
        
        try {
            persistentSocket = DatagramSocket()
            protect(persistentSocket)
            persistentSocket?.soTimeout = 2000
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to create persistent socket", e)
        }

        val prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE)
        _blockedCount.value = prefs.getInt("threats_blocked", 0)
    }

    private fun loadBloomFilter(forceRebuild: Boolean = false) {
        val filterFile = java.io.File(filesDir, "bloom.bin")
        
        serviceScope.launch {
            try {
                if (!forceRebuild && filterFile.exists()) {
                    FileInputStream(filterFile).use { fis ->
                        bloomFilter = BloomFilter.readFrom(fis, Funnels.stringFunnel(Charset.defaultCharset()))
                        Log.d("DnsVpnService", "Bloom Filter loaded from disk cache.")
                        return@launch
                    }
                }

                Log.d("DnsVpnService", "Rebuilding Bloom Filter from assets/blocked_domains.txt...")

                val filter = BloomFilter.create(
                    Funnels.stringFunnel(Charset.defaultCharset()),
                    200000,
                    0.01
                )

                assets.open("blocked_domains.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).forEachLine { line ->
                        val domain = line.trim().lowercase()
                        if (domain.isNotEmpty() && !domain.startsWith("#")) {
                            filter.put(domain)
                        }
                    }
                }

                FileOutputStream(filterFile).use { fos ->
                    filter.writeTo(fos)
                }
                
                bloomFilter = filter
                Log.d("DnsVpnService", "Bloom Filter rebuilt and persisted.")
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Failed to manage Bloom Filter", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        if (intent?.action == "RELOAD_BLOCKLIST") {
            lookupCache.evictAll()
            loadBloomFilter(forceRebuild = true)
            return START_STICKY
        }

        showNotification()

        if (!_isRunning.value) {
            _serviceStartTime.value = System.currentTimeMillis()
            _isRunning.value = true
            startVpn()
        }
        
        return START_STICKY
    }

    private fun showNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Synthetic Spirit Shield Active")
            .setContentText("Local DNS filtering is protecting your device.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startVpn() {
        if (vpnThread != null) return

        vpnThread = thread(start = true, name = "VpnLoop") {
            try {
                val builder = Builder()
                builder.setSession("SyntheticSpirit")

                builder.addAddress("10.0.0.2", 32)
                builder.addRoute("0.0.0.0", 0) 
                builder.addDnsServer("10.0.0.2")

                builder.addAddress("fd00::2", 128)
                builder.addRoute("::", 0)
                builder.addDnsServer("fd00::2")

                try {
                    builder.addDisallowedApplication(packageName)
                    builder.addDisallowedApplication("com.android.captiveportallogin")
                } catch (e: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                vpnInterface = builder.establish()
                Log.d("DnsVpnService", "VPN Interface established.")

                runVpnLoop()
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Error in VPN thread", e)
            } finally {
                stopVpn()
            }
        }
    }

    private fun runVpnLoop() {
        val vpnFileDescriptor = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(vpnFileDescriptor)
        val output = FileOutputStream(vpnFileDescriptor)
        val buffer = ByteBuffer.allocateDirect(16384)

        try {
            while (!Thread.interrupted() && _isRunning.value) {
                buffer.clear()
                val length = input.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)
                    handlePacket(buffer, output)
                }
            }
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Loop error", e)
        } finally {
            try { input.close() } catch (e: Exception) {}
            try { output.close() } catch (e: Exception) {}
        }
    }

    private fun handlePacket(packet: ByteBuffer, output: FileOutputStream) {
        if (packet.remaining() < 20) return
        val posBefore = packet.position()
        val firstByte = packet.get().toInt() and 0xFF
        val version = (firstByte shr 4) and 0x0F

        val protocol: Int
        val ihl: Int
        val srcIp: ByteArray
        val dstIp: ByteArray

        if (version == 4) {
            ihl = (firstByte and 0x0F) * 4
            packet.position(posBefore + 9)
            protocol = packet.get().toInt() and 0xFF
            packet.position(posBefore + 12)
            srcIp = ByteArray(4)
            dstIp = ByteArray(4)
            packet.get(srcIp)
            packet.get(dstIp)
        } else if (version == 6) {
            if (packet.remaining() < 40) return
            packet.position(posBefore + 6)
            protocol = packet.get().toInt() and 0xFF
            packet.position(posBefore + 8)
            srcIp = ByteArray(16)
            dstIp = ByteArray(16)
            packet.get(srcIp)
            packet.get(dstIp)
            ihl = 40
        } else {
            return
        }

        if (protocol != 17) return // Only UDP

        packet.position(posBefore + ihl)
        if (packet.remaining() < 8) return
        val srcPort = packet.short.toInt() and 0xFFFF
        val dstPort = packet.short.toInt() and 0xFFFF
        val udpLength = packet.short.toInt() and 0xFFFF
        
        if (dstPort != 53) return // Only DNS

        val dnsDataSize = udpLength - 8
        if (dnsDataSize <= 0 || packet.remaining() < dnsDataSize) return
        
        val dnsStartPos = packet.position()
        val domain = parseDnsDomain(packet, dnsDataSize) ?: return

        val originalDnsData = ByteArray(dnsDataSize)
        packet.position(dnsStartPos)
        packet.get(originalDnsData)

        processDnsQuery(originalDnsData, domain, srcIp, srcPort, dstIp, dstPort, output)
    }

    private fun processDnsQuery(
        dnsData: ByteArray, 
        domain: CharSequence,
        clientIp: ByteArray,
        clientPort: Int, 
        serverIp: ByteArray,
        serverPort: Int,
        output: FileOutputStream
    ) {
        serviceScope.launch {
            val domainStr = domain.toString()

            // 1. Whitelist Check (Fastest)
            if (whitelistManager.isWhitelisted(domainStr)) {
                forwardAndLog(dnsData, domainStr, false, clientIp, clientPort, serverIp, serverPort, output)
                return@launch
            }

            // 2. LRU Cache Check (Fast)
            val cachedResult = lookupCache.get(domainStr)
            if (cachedResult != null) {
                if (cachedResult) {
                    blockAndLog(dnsData, domainStr, clientIp, clientPort, serverIp, serverPort, output)
                } else {
                    forwardAndLog(dnsData, domainStr, false, clientIp, clientPort, serverIp, serverPort, output)
                }
                return@launch
            }

            // 3. Bloom Filter & DB Check
            val isDomainBlocked = withContext(Dispatchers.IO) {
                // Consult DB directly to ensure newly imported domains are detected
                db.blockedDomainDao().isBlocked(domainStr)
            }

            lookupCache.put(domainStr, isDomainBlocked)

            if (isDomainBlocked) {
                blockAndLog(dnsData, domainStr, clientIp, clientPort, serverIp, serverPort, output)
            } else {
                forwardAndLog(dnsData, domainStr, true, clientIp, clientPort, serverIp, serverPort, output)
            }
        }
    }

    private fun blockAndLog(
        dnsData: ByteArray,
        domain: String,
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        serverPort: Int,
        output: FileOutputStream
    ) {
        Log.i("DnsVpnService", "Blocked: $domain")
        _blockedCount.value++
        getSharedPreferences("vpn_prefs", MODE_PRIVATE).edit()
            .putInt("threats_blocked", _blockedCount.value).apply()

        val logItem = DnsLog(domain = domain, isBlocked = true, timestamp = System.currentTimeMillis())
        serviceScope.launch(Dispatchers.IO) { db.dnsLogDao().insert(logItem) }

        val response = createNxDomainResponse(dnsData, clientIp, clientPort, serverIp, serverPort)
        synchronized(output) {
            try {
                output.write(response)
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Failed to write block response", e)
                stopVpn()
            }
        }
    }

    private fun forwardAndLog(
        dnsData: ByteArray,
        domain: String,
        shouldLog: Boolean,
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        serverPort: Int,
        output: FileOutputStream
    ) {
        if (shouldLog) {
            val logItem = DnsLog(domain = domain, isBlocked = false, timestamp = System.currentTimeMillis())
            serviceScope.launch(Dispatchers.IO) { db.dnsLogDao().insert(logItem) }
        }

        dnsExecutor.execute {
            try {
                val dnsResponse = forwardDnsPacket(dnsData)
                if (dnsResponse != null) {
                    val finalPacket = wrapUdpIp(dnsResponse, clientIp, clientPort, serverIp, serverPort)
                    synchronized(output) {
                        try {
                            output.write(finalPacket)
                        } catch (e: Exception) {
                            Log.e("DnsVpnService", "Failed to write forward response", e)
                            stopVpn()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Upstream error for $domain", e)
            }
        }
    }

    private fun forwardDnsPacket(data: ByteArray): ByteArray? {
        val socket = persistentSocket ?: return null
        return try {
            val upstreamAddr = InetAddress.getByName(upstreamDns)
            val queryPacket = DatagramPacket(data, data.size, upstreamAddr, 53)
            synchronized(socket) {
                socket.send(queryPacket)
                val responseBuffer = ByteArray(1024)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                responsePacket.data.copyOf(responsePacket.length)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDnsDomain(packet: ByteBuffer, dnsDataSize: Int): CharSequence? {
        val sb = StringBuilder()
        val dnsHeaderSize = 12
        val startPos = packet.position()
        var pos = startPos + dnsHeaderSize
        
        try {
            while (pos < startPos + dnsDataSize) {
                val lenByte = packet.get(pos).toInt() and 0xFF
                if (lenByte == 0) break

                if ((lenByte and 0xC0) == 0xC0) break

                if (sb.isNotEmpty()) sb.append(".")
                for (i in 0 until lenByte) {
                    sb.append((packet.get(pos + i + 1).toInt() and 0xFF).toChar())
                }
                pos += lenByte + 1
            }
        } catch (e: Exception) { return null }
        
        return if (sb.isEmpty()) null else sb.toString().lowercase()
    }

    private fun createNxDomainResponse(
        queryDnsData: ByteArray,
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        serverPort: Int
    ): ByteArray {
        val responseDns = queryDnsData.copyOf()
        if (responseDns.size < 12) return ByteArray(0)
        responseDns[2] = (responseDns[2].toInt() or 0x80).toByte() 
        responseDns[3] = (responseDns[3].toInt() and 0xF0 or 0x83).toByte() 
        return wrapUdpIp(responseDns, clientIp, clientPort, serverIp, serverPort)
    }

    private fun wrapUdpIp(
        dnsData: ByteArray,
        dstIp: ByteArray,
        dstPort: Int,
        srcIp: ByteArray,
        srcPort: Int
    ): ByteArray {
        val isIpv6 = dstIp.size == 16
        val ipHeaderLen = if (isIpv6) 40 else 20
        val udpLen = dnsData.size + 8
        val totalLen = ipHeaderLen + udpLen
        
        val out = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(out)
        
        if (!isIpv6) {
            buffer.put(0x45.toByte())
            buffer.put(0x00.toByte())
            buffer.putShort(totalLen.toShort())
            buffer.putShort(0.toShort())
            buffer.putShort(0x4000.toShort())
            buffer.put(64.toByte())
            buffer.put(17.toByte())
            buffer.putShort(0.toShort()) // Checksum placeholder
            buffer.put(srcIp)
            buffer.put(dstIp)
            buffer.putShort(10, calculateChecksum(out, 0, 20))
        } else {
            // IPv6 Header
            buffer.put(0x60.toByte())
            buffer.put(0.toByte())
            buffer.put(0.toByte())
            buffer.put(0.toByte())
            buffer.putShort(udpLen.toShort()) // Payload length
            buffer.put(17.toByte()) // Next header (UDP)
            buffer.put(64.toByte()) // Hop limit
            buffer.put(srcIp)
            buffer.put(dstIp)
        }
        
        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        if (isIpv6) {
            buffer.putShort(calculateIPv6UdpChecksum(out, dnsData))
        } else {
            buffer.putShort(0.toShort()) // UDP Checksum (optional/0 for IPv4)
        }
        
        buffer.put(dnsData)
        return out
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = (data[i].toInt() and 0xFF) shl 8
            val low = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            sum += (high or low)
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Protection Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopVpn() {
        _isRunning.value = false
        _serviceStartTime.value = 0L
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        dnsExecutor.shutdownNow()
        persistentSocket?.close()
        super.onDestroy()
    }

    private fun calculateIPv6UdpChecksum(packet: ByteArray, dnsData: ByteArray): Short {
        var sum = 0L
        // IPv6 Pseudo-header
        for (i in 8 until 40 step 2) { // Src & Dst IPs
            sum += (packet[i].toInt() and 0xFF shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        val udpLen = dnsData.size + 8
        sum += udpLen
        sum += 17 // Next Header (UDP)

        // UDP Header (except checksum)
        sum += (packet[40].toInt() and 0xFF shl 8) or (packet[41].toInt() and 0xFF) // Src Port
        sum += (packet[42].toInt() and 0xFF shl 8) or (packet[43].toInt() and 0xFF) // Dst Port
        sum += udpLen // Length

        // UDP Payload
        for (i in 0 until dnsData.size step 2) {
            val high = dnsData[i].toInt() and 0xFF shl 8
            val low = if (i + 1 < dnsData.size) dnsData[i + 1].toInt() and 0xFF else 0
            sum += (high or low)
        }

        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        var checksum = (sum.inv() and 0xFFFF).toShort()
        if (checksum.toInt() == 0) checksum = 0xFFFF.toShort()
        return checksum
    }
}