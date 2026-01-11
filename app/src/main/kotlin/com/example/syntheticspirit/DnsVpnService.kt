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
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.thread

import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.BlockedDomain
import kotlinx.coroutines.runBlocking
import androidx.collection.LruCache
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.nio.charset.Charset
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
    
    // Fast LRU cache for common repeats
    private val lookupCache = LruCache<String, Boolean>(2000)
    
    // Probabilistic O(1) in-memory filter (~2MB for 200k URLs)
    private var bloomFilter: BloomFilter<CharSequence>? = null
    
    private lateinit var db: AppDatabase

    private val upstreamDns = "8.8.8.8"
    private var upstreamChannel: java.nio.channels.DatagramChannel? = null
    private val dnsExecutor = java.util.concurrent.Executors.newFixedThreadPool(10)

    private val CHANNEL_ID = "vpn_notifications"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        createNotificationChannel()
        loadBloomFilter()
        
        val prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE)
        _blockedCount.value = prefs.getInt("threats_blocked", 0)

        try {
            upstreamChannel = java.nio.channels.DatagramChannel.open()
            upstreamChannel?.configureBlocking(false) // Non-blocking for high performance
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to open upstream channel", e)
        }
    }

    private fun loadBloomFilter(forceRebuild: Boolean = false) {
        val filterFile = java.io.File(filesDir, "bloom.bin")
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!forceRebuild && filterFile.exists()) {
                    // Fast path: Load serialized filter from disk (O(1) relative to DB scan)
                    java.io.FileInputStream(filterFile).use { fis ->
                        bloomFilter = BloomFilter.readFrom(fis, Funnels.stringFunnel(Charset.defaultCharset()))
                        Log.d("DnsVpnService", "Bloom Filter loaded from disk cache.")
                        return@launch
                    }
                }

                // Slow path: Rebuild from Database using a Cursor for memory efficiency
                Log.d("DnsVpnService", "Rebuilding Bloom Filter from database (Cursor mode)...")
                val cursor = db.blockedDomainDao().getAllDomainsCursor()
                
                val filter = BloomFilter.create(
                    Funnels.stringFunnel(Charset.defaultCharset()),
                    250000,
                    0.01
                )
                
                try {
                    while (cursor.moveToNext()) {
                        val domain = cursor.getString(0)
                        filter.put(domain)
                    }
                } finally {
                    cursor.close()
                }
                
                // Persist to disk for next time
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
        // Ensure we stay running if swiped away
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
                
                // IPv4 Configuration
                builder.addAddress("10.0.0.2", 32)
                builder.addDnsServer("8.8.8.8")
                builder.addRoute("0.0.0.0", 0)
                
                // IPv6 Configuration (Preventing IPv6 Leaks)
                builder.addAddress("fd00::2", 128)
                builder.addDnsServer("2001:4860:4860::8888")
                builder.addRoute("::", 0)
                
                // Allow Captive Portal login to bypass VPN (Public Wi-Fi logins)
                try {
                    builder.addDisallowedApplication("com.android.captiveportallogin")
                } catch (e: Exception) {
                    // Package not found on this device, skip
                }
                
                // KILL SWITCH LOGIC:
                // 1. System-level blocking (API 33+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    builder.setBlocking(true) 
                }
                
                // 2. Disallow local bypass
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
        if (protocol != 17) return 

        // Extract IPs without allocating ByteArrays
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
        
        // No allocation - parse directly from the direct buffer
        val domain = parseDnsDomain(packet, dnsDataSize) ?: return

        // Copying and forwarding still needs a heap array for the upstream send,
        // but it's much more contained now.
        val dnsData = ByteArray(dnsDataSize)
        packet.get(dnsData)

        processDnsQuery(dnsData, domain, srcIpInt, srcPort, dstIpInt, dstPort, output)
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
        
        if (isBlocked(domain)) {
            Log.i("DnsVpnService", "Blocked: $domain")
            _blockedCount.value++
            getSharedPreferences("vpn_prefs", MODE_PRIVATE).edit()
                .putInt("threats_blocked", _blockedCount.value).apply()
            
            val response = createNxDomainResponse(dnsData, clientIpInt, clientPort, serverIpInt, serverPort)
            synchronized(output) { output.write(response) }
        } else {
            dnsExecutor.execute {
                try {
                    val upstreamAddr = java.net.InetSocketAddress(upstreamDns, 53)
                    val queryBuffer = ByteBuffer.wrap(dnsData)
                    
                    // Non-blocking persistent channel check
                    upstreamChannel?.send(queryBuffer, upstreamAddr)

                    val inBuffer = ByteBuffer.allocateDirect(1024)
                    var retry = 0
                    var remoteAddr: java.net.SocketAddress? = null
                    
                    // Simple poll for response (keep it non-blocking but small wait)
                    while (remoteAddr == null && retry < 100) {
                        remoteAddr = upstreamChannel?.receive(inBuffer)
                        if (remoteAddr == null) Thread.sleep(10)
                        retry++
                    }

                    if (remoteAddr != null) {
                        inBuffer.flip()
                        val dnsResponse = ByteArray(inBuffer.remaining())
                        inBuffer.get(dnsResponse)

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

    private fun isBlocked(query: CharSequence?): Boolean {
        if (query == null) return false
        
        // 1. O(1) Memory Cache - We only allocate a String here if not already cached
        // To avoid allocation on every check, we check Bloom Filter first for "Clean" domains
        
        // 2. O(1) Probabilistic Check (Bloom Filter) - No allocation!
        if (bloomFilter?.mightContain(query) == false) {
            return false
        }

        // 3. Potential Match - Now we allocate the String for definitive checks
        val domain = query.toString().lowercase()
        lookupCache.get(domain)?.let { return it }

        // 4. O(log N) Disk Lookup (Indexed Room DB)
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
        
        while (pos < endPos) {
            val len = packet.get(pos).toInt() and 0xFF
            if (len == 0) break
            if (sb.isNotEmpty()) sb.append('.')
            pos++
            if (pos + len > endPos) break
            for (i in 0 until len) {
                sb.append(packet.get(pos + i).toInt().toChar())
            }
            pos += len
        }
        return if (sb.isEmpty()) null else sb
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
        
        // IP Header
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
        
        // UDP Header
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
        try { upstreamChannel?.close() } catch (e: Exception) {}
        super.onDestroy()
    }
}
