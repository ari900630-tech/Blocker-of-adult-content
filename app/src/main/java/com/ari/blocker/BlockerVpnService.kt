package com.ari.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

class BlockerVpnService : VpnService() {
    companion object {
        private const val CHANNEL_ID = "blocker_protection"
        private const val NOTIFICATION_ID = 1001
        private const val DNS_IP = "10.10.0.1"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val BLOCKLIST_URL =
            "https://raw.githubusercontent.com/ari900630-tech/Blocker-of-adult-content/main/blocklist/domains.txt"

        @Volatile private var instance: BlockerVpnService? = null
        @Volatile var isProtectionActive: Boolean = false
            private set

        fun reloadCustomBlocks() {
            instance?.loadCustomBlocks()
        }
    }

    @Volatile private var running = false
    private var vpn: ParcelFileDescriptor? = null
    private val blockedDomains = CopyOnWriteArraySet<String>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startProtectionForeground()
        if (!running) {
            running = true
            isProtectionActive = true
            Thread {
                loadBlocklist()
                runDnsVpn()
            }.start()
        }
        return START_STICKY
    }

    private fun startProtectionForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "מגן התוכן", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("מגן התוכן פעיל")
            .setContentText("סינון DNS פועל")
            .setSmallIcon(R.drawable.ic_blocker_shield)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun loadBlocklist() {
        loadCustomBlocks()
        try {
            assets.open("domains.txt").use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines -> lines.forEach { addDomain(it) } }
            }
        } catch (_: Exception) {}

        Thread {
            try {
                val connection = URL(BLOCKLIST_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                if (connection.responseCode in 200..299) {
                    BufferedReader(InputStreamReader(connection.inputStream)).useLines { lines ->
                        lines.forEach { addDomain(it) }
                    }
                }
                connection.disconnect()
            } catch (_: Exception) {}
        }.start()
    }

    private fun loadCustomBlocks() {
        getSharedPreferences("custom_blocks", MODE_PRIVATE).all.keys.forEach { addDomain(it) }
    }

    private fun addDomain(raw: String) {
        val value = raw.trim().lowercase(Locale.US)
        if (value.isNotEmpty() && !value.startsWith("#")) blockedDomains.add(value.removePrefix(".").removeSuffix("."))
    }

    private fun isBlocked(domain: String): Boolean {
        var current = domain.lowercase(Locale.US).trimEnd('.')
        while (current.isNotEmpty()) {
            if (blockedDomains.contains(current)) return true
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
        return false
    }

    private fun runDnsVpn() {
        try {
            // DNS-only VPN: keep normal Chrome/Internet traffic outside the VPN.
            // This avoids the previous bug where non-DNS packets were dropped.
            vpn = Builder()
                .setSession("מגן התוכן - DNS")
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("10.10.0.1", 32)
                .addDnsServer(DNS_IP)
                .establish()

            val descriptor = vpn ?: return
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)

            while (running) {
                val buffer = ByteArray(32767)
                val length = input.read(buffer)
                if (length > 0) handlePacket(buffer, length, output)
            }
        } catch (_: Exception) {
            if (running) stopSelf()
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 20) return
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version == 4) handleIpv4Udp(packet, length, output)
    }

    private fun handleIpv4Udp(packet: ByteArray, length: Int, output: FileOutputStream) {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return
        if ((packet[9].toInt() and 0xFF) != 17) return

        val udpOffset = ihl
        val srcPort = u16(packet, udpOffset)
        val dstPort = u16(packet, udpOffset + 2)
        val udpLength = u16(packet, udpOffset + 4)
        if (dstPort != 53 || udpLength < 8 || udpOffset + udpLength > length) return

        val dnsQuery = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)
        val domain = readDnsQuestionName(dnsQuery) ?: return
        val responseDns = if (isBlocked(domain)) blockedDnsResponse(dnsQuery) else forwardDns(dnsQuery) ?: return

        val response = buildIpv4UdpResponse(packet, srcPort, responseDns)
        output.write(response)
        output.flush()
    }

    private fun readDnsQuestionName(data: ByteArray): String? {
        if (data.size < 12) return null
        var offset = 12
        val labels = ArrayList<String>()
        while (offset < data.size) {
            val size = data[offset].toInt() and 0xFF
            offset++
            if (size == 0) break
            if (size > 63 || offset + size > data.size) return null
            labels.add(String(data, offset, size, Charsets.US_ASCII))
            offset += size
        }
        return if (labels.isEmpty()) null else labels.joinToString(".").lowercase(Locale.US)
    }

    private fun blockedDnsResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        response[2] = (response[2].toInt() or 0x80 or 0x04).toByte()
        response[3] = ((response[3].toInt() and 0xF0) or 0x03).toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                socket.soTimeout = 2500
                val address = java.net.InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(query, query.size, address, 53))
                val receive = ByteArray(4096)
                val packet = DatagramPacket(receive, receive.size)
                socket.receive(packet)
                packet.data.copyOf(packet.length)
            }
        } catch (_: Exception) { null }
    }

    private fun buildIpv4UdpResponse(request: ByteArray, requestSourcePort: Int, dnsResponse: ByteArray): ByteArray {
        val totalLength = 20 + 8 + dnsResponse.size
        val response = ByteArray(totalLength)
        response[0] = 0x45
        putU16(response, 2, totalLength)
        putU16(response, 4, u16(request, 4))
        response[8] = 64
        response[9] = 17
        System.arraycopy(request, 16, response, 12, 4)
        System.arraycopy(request, 12, response, 16, 4)
        val udp = 20
        putU16(response, udp, 53)
        putU16(response, udp + 2, requestSourcePort)
        putU16(response, udp + 4, 8 + dnsResponse.size)
        putU16(response, udp + 6, 0)
        System.arraycopy(dnsResponse, 0, response, udp + 8, dnsResponse.size)
        putU16(response, 10, checksum(response, 0, 20))
        return response
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(data, i)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    override fun onRevoke() {
        running = false
        isProtectionActive = false
        vpn?.close()
        vpn = null
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        isProtectionActive = false
        vpn?.close()
        vpn = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
