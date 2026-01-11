package com.example.syntheticspirit

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import com.example.syntheticspirit.data.AppDatabase
import kotlinx.coroutines.runBlocking
import androidx.collection.LruCache
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf

data class DnsLogItem(val domain: String, val isBlocked: Boolean, val timestamp: Long)

class DnsVpnService : VpnService() {
    companion object {
        private val _isRunning = mutableStateOf(false)
        val isRunning: State<Boolean> = _isRunning

        private val _blockedCount = mutableStateOf(0)
        val blockedCount: State<Int> = _blockedCount

        private val _serviceStartTime = mutableLongStateOf(0L)
        val serviceStartTime: State<Long> = _serviceStartTime
        
        val dnsLogs = mutableStateListOf<DnsLogItem>()
        
        fun clearLogs() {
            dnsLogs.clear()
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    
    private val lookupCache = LruCache<String, Boolean>(2000)
    
    private var bloomFilter: BloomFilter<CharSequence>? = null
    
    private lateinit var db: AppDatabase
    private lateinit var whitelistManager: WhitelistManager

    private val upstreamDns = "8.8.8.8"
    private val dnsExecutor = Executors.newFixedThreadPool(10)

    private val CHANNEL_ID = "vpn_notifications"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        whitelistManager = WhitelistManager(this)
        createNotificationChannel()
        loadBloomFilter()
        
        val prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE)
        _blockedCount.value = prefs.getInt("threats_blocked", 0)
    }

    private fun loadBloomFilter(forceRebuild: Boolean = false) {
        val filterFile = java.io.File(filesDir, "bloom.bin")
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!forceRebuild && filterFile.exists()) {
                    java.io.FileInputStream(filterFile).use { fis ->
                        bloomFilter = BloomFilter.readFrom(fis, Funnels.stringFunnel(Charset.defaultCharset()))
                        Log.d("DnsVpnService", "Bloom Filter loaded from disk cache.")
                        return@launch
                    }
                }

                Log.d("DnsVpnService", "Rebuilding Bloom Filter from assets/blocked_domains.txt...")

                val filter = BloomFilter.create(
                    Funnels.stringFunnel(Charset.defaultCharset()),
                    50000, 
                    0.01
                )

                assets.open("blocked_domains.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).forEachLine { line ->
                        val domain = line.trim().lowercase()
                        if (domain.isNotEmpty()) {
                            filter.put(domain)
                        }
                    }
                }

                java.io.FileOutputStream(filterFile).use { fos ->
                    filter.writeTo(fos)
                }
                
                bloomFilter = filter
                Log.d("DnsVpnService", "Bloom Filter rebuilt and persisted.")
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Failed to manage Bloom Filter", e)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("DnsVpnService", "Task removed, staying active.")
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Protection Status",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when DNS protection is active"
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Synthetic Spirit Active")
            .setContentText("Your digital space is currently protected.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopVpn()
                return START_NOT_STICKY
            }
            "RELOAD_BLOCKLIST" -> {
                lookupCache.evictAll()
                loadBloomFilter(forceRebuild = true)
                return START_STICKY
            }
        }
        if (!_isRunning.value) {
            _serviceStartTime.value = System.currentTimeMillis()
        }
        _isRunning.value = true
        showNotification()
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnThread != null) return

        vpnThread = thread {
            try {
                val builder = Builder()
                builder.setSession("SyntheticSpirit")

                builder.addAddress("10.0.0.2", 24)
                builder.addDnsServer("10.0.0.1")

                builder.addAddress("fd00::2", 120)
                builder.addDnsServer("fd00::1")

                try {
                    builder.addDisallowedApplication("com.android.captiveportallogin")
                } catch (e: Exception) {
                    // Package not found on this device, skip
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                vpnInterface = builder.establish()
                Log.d("DnsVpnService", "VPN Interface established")

                runVpnLoop()
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Error in VPN thread", e)
            } finally {
                Log.d("DnsVpnService", "VPN thread finishing, stopping service")
                stopSelf()
            }
        }
    }

    private fun runVpnLoop() {
        val vpnFileDescriptor = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(vpnFileDescriptor)
        val output = FileOutputStream(vpnFileDescriptor)
        val inChannel = input.channel
        val buffer = ByteBuffer.allocateDirect(16384)

        try {
            while (!Thread.interrupted() && _isRunning.value) {
                buffer.clear()
                val length = inChannel.read(buffer)
                if (length > 0) {
                    buffer.flip()
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
        if (version != 4) return

        packet.position(posBefore + 9)
        val protocol = packet.get().toInt() and 0xFF
        if (protocol != 17) return // Not UDP

        packet.position(posBefore + 12)
        val srcIpInt = packet.int
        val dstIpInt = packet.int

        val ihl = (firstByte and 0x0F) * 4
        packet.position(posBefore + ihl)
        
        if (packet.remaining() < 8) return
        val srcPort = packet.short.toInt() and 0xFFFF
        val dstPort = packet.short.toInt() and 0xFFFF
        val udpLength = packet.short.toInt() and 0xFFFF
        
        if (dstPort != 53) return

        val dnsDataSize = udpLength - 8
        if (dnsDataSize <= 0 || packet.remaining() < dnsDataSize) return
        
        val domain = parseDnsDomain(packet, dnsDataSize) ?: return

        val originalDnsData = ByteArray(dnsDataSize)
        packet.position(packet.position() - dnsDataSize)
        packet.get(originalDnsData)

        processDnsQuery(originalDnsData, domain, srcIpInt, srcPort, dstIpInt, dstPort, output)
    }

    private fun processDnsQuery(
        dnsData: ByteArray, 
        domain: CharSequence,
        clientIpInt: Int, 
        clientPort: Int, 
        serverIpInt: Int, 
        serverPort: Int,
        output: FileOutputStream
    ) {
        val isDomainBlocked = isBlocked(domain)

        val logItem = DnsLogItem(domain.toString(), isDomainBlocked, System.currentTimeMillis())
        synchronized(dnsLogs) {
            dnsLogs.add(0, logItem)
            if (dnsLogs.size > 100) {
                dnsLogs.removeLast()
            }
        }

        if (isDomainBlocked) {
            Log.i("DnsVpnService", "Blocked: $domain")
            _blockedCount.value++
            getSharedPreferences("vpn_prefs", MODE_PRIVATE).edit()
                .putInt("threats_blocked", _blockedCount.value).apply()
            
            val response = createNxDomainResponse(dnsData, clientIpInt, clientPort, serverIpInt, serverPort)
            synchronized(output) { output.write(response) }
        } else {
            dnsExecutor.execute {
                try {
                    val dnsResponse = forwardDnsPacket(dnsData)
                    if (dnsResponse != null) {
                        val finalPacket = wrapUdpIp(dnsResponse, clientIpInt, clientPort, serverIpInt, serverPort)
                        synchronized(output) {
                            output.write(finalPacket)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DnsVpnService", "Upstream error for $domain", e)
                }
            }
        }
    }

    private fun forwardDnsPacket(data: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.bind(InetSocketAddress(0))
            protect(socket)

            socket.soTimeout = 2000 // 2 second timeout

            val upstreamAddr = InetAddress.getByName(upstreamDns)
            val queryPacket = DatagramPacket(data, data.size, upstreamAddr, 53)
            socket.send(queryPacket)

            val responseBuffer = ByteArray(1024)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            responsePacket.data.copyOf(responsePacket.length)
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to forward DNS packet", e)
            null
        } finally {
            socket?.close()
        }
    }

    private fun isBlocked(query: CharSequence?): Boolean {
        if (query == null) return false

        if (bloomFilter?.mightContain(query) == false) {
            return false
        }

        val domain = query.toString()

        lookupCache.get(domain)?.let { return it }

        if (whitelistManager.isWhitelisted(domain)) {
            lookupCache.put(domain, false)
            return false
        }

        val isExact = runBlocking(Dispatchers.IO) {
            db.blockedDomainDao().isBlocked(domain)
        }

        lookupCache.put(domain, isExact)
        return isExact
    }

    private val dnsStringBuilder = object : ThreadLocal<StringBuilder>() {
        override fun initialValue() = StringBuilder(256)
    }

    private fun parseDnsDomain(packet: ByteBuffer, dnsDataSize: Int): CharSequence? {
        if (dnsDataSize < 12) return null
        val sb = dnsStringBuilder.get()
        sb.setLength(0)
        
        val startPos = packet.position()
        var pos = startPos + 12
        val endPos = startPos + dnsDataSize
        var jumped = false

        fun readSegment(currentPos: Int): Int {
            var p = currentPos
            while (true) {
                if (p >= endPos) return -1
                val len = packet.get(p).toInt() and 0xFF
                if (len == 0) return p + 1

                if ((len and 0xC0) == 0xC0) { // Pointer
                    if (p + 1 >= endPos) return -1
                    val offset = ((len and 0x3F) shl 8) or (packet.get(p + 1).toInt() and 0xFF)
                    if (!jumped) {
                        jumped = true
                        readSegment(startPos - 12 + offset)
                    }
                    return p + 2
                }

                p++
                if (p + len > endPos) return -1

                if (sb.isNotEmpty()) sb.append('.')
                for (i in 0 until len) {
                    sb.append(packet.get(p + i).toInt().toChar().lowercaseChar())
                }
                p += len
            }
        }

        val finalPos = readSegment(pos)
        return if (finalPos == -1 || sb.isEmpty()) null else sb
    }


    private fun createNxDomainResponse(
        queryDnsData: ByteArray,
        clientIpInt: Int,
        clientPort: Int,
        serverIpInt: Int,
        serverPort: Int
    ): ByteArray {
        val responseDns = queryDnsData.copyOf()
        if (responseDns.size < 12) return ByteArray(0)
        responseDns[2] = (responseDns[2].toInt() or 0x80).toByte() 
        responseDns[3] = (responseDns[3].toInt() and 0xF0 or 0x83).toByte() 
        return wrapUdpIp(responseDns, clientIpInt, clientPort, serverIpInt, serverPort)
    }

    private fun wrapUdpIp(
        dnsData: ByteArray,
        dstIpInt: Int,
        dstPort: Int,
        srcIpInt: Int,
        srcPort: Int
    ): ByteArray {
        val udpLen = dnsData.size + 8
        val ipLen = udpLen + 20
        val out = ByteArray(ipLen)
        val buffer = ByteBuffer.wrap(out)
        
        buffer.put(0x45.toByte()) 
        buffer.put(0x00.toByte()) 
        buffer.putShort(ipLen.toShort())
        buffer.putShort(0.toShort()) 
        buffer.putShort(0x4000.toShort()) 
        buffer.put(64.toByte()) 
        buffer.put(17.toByte()) 
        buffer.putShort(0.toShort()) // Checksum placeholder
        buffer.putInt(srcIpInt)
        buffer.putInt(dstIpInt)
        
        val ipChecksum = calculateChecksum(out, 0, 20)
        buffer.putShort(10, ipChecksum)
        
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort()) // No checksum (permissible for DNS)
        
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

    private fun stopVpn() {
        _isRunning.value = false
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        _isRunning.value = false
        stopVpn()
        dnsExecutor.shutdownNow()
        super.onDestroy()
    }
}
