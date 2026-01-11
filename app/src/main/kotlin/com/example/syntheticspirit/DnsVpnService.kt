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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
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
    
    private lateinit var db: AppDatabase
    private lateinit var blocklistManager: BlocklistManager

    private val upstreamDns = "8.8.8.8"
    private val dnsExecutor = Executors.newFixedThreadPool(10)

    private val CHANNEL_ID = "vpn_notifications"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        blocklistManager = BlocklistManager(this)
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
                    FileInputStream(filterFile).use { fis ->
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

        // Start Foreground immediately for Android 15 compliance
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
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Changed to lock icon for visibility
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

                // THE KEY ICON FIX: You must add a route for the system to show the icon.
                // This captures all traffic so we can filter the DNS parts.
                builder.addAddress("10.0.0.2", 32)
                builder.addRoute("0.0.0.0", 0) 
                builder.addDnsServer("10.0.0.2")

                // IPv6 Support
                builder.addAddress("fd00::2", 128)
                builder.addRoute("::", 0)
                builder.addDnsServer("fd00::2")

                // Prevent the app from filtering its own requests (Prevents loops)
                try {
                    builder.addDisallowedApplication(packageName)
                    builder.addDisallowedApplication("com.android.captiveportallogin")
                } catch (e: Exception) {
                    // Packages might not exist, ignore
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                vpnInterface = builder.establish()
                Log.d("DnsVpnService", "VPN Interface established. Key icon should appear now.")

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
            input.close()
            output.close()
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
        if (protocol != 17) return // Only UDP

        packet.position(posBefore + 12)
        val srcIpInt = packet.int
        val dstIpInt = packet.int

        val ihl = (firstByte and 0x0F) * 4
        packet.position(posBefore + ihl)
        
        if (packet.remaining() < 8) return
        val srcPort = packet.short.toInt() and 0xFFFF
        val dstPort = packet.short.toInt() and 0xFFFF
        val udpLength = packet.short.toInt() and 0xFFFF
        
        if (dstPort != 53) return // Only DNS

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
        val isDomainBlocked = runBlocking(Dispatchers.IO) { 
            db.blockedDomainDao().isBlocked(domain.toString()) || 
            (bloomFilter?.mightContain(domain.toString()) ?: false)
        }

        val logItem = DnsLog(
            domain = domain.toString(),
            isBlocked = isDomainBlocked,
            timestamp = System.currentTimeMillis()
        )

        GlobalScope.launch(Dispatchers.IO) {
            db.dnsLogDao().insert(logItem)
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
            protect(socket)
            socket.soTimeout = 2000 

            val upstreamAddr = InetAddress.getByName(upstreamDns)
            val queryPacket = DatagramPacket(data, data.size, upstreamAddr, 53)
            socket.send(queryPacket)

            val responseBuffer = ByteArray(1024)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            responsePacket.data.copyOf(responsePacket.length)
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun parseDnsDomain(packet: ByteBuffer, dnsDataSize: Int): CharSequence? {
        val sb = StringBuilder()
        val startPos = packet.position()
        var pos = startPos + 12 // Skip DNS header
        
        try {
            while (pos < startPos + dnsDataSize) {
                val len = packet.get(pos).toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append(".")
                for (i in 0 until len) {
                    sb.append(packet.get(pos + i + 1).toChar())
                }
                pos += len + 1
            }
        } catch (e: Exception) { return null }
        
        return sb.toString().lowercase()
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
        buffer.putShort(0.toShort())
        buffer.putInt(srcIpInt)
        buffer.putInt(dstIpInt)
        
        buffer.putShort(10, calculateChecksum(out, 0, 20))
        
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort()) 
        
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
            manager.createNotificationChannel(channel)
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
        dnsExecutor.shutdownNow()
        super.onDestroy()
    }
}
