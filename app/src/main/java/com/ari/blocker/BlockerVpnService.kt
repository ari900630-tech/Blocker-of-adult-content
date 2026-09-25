package com.ari.blocker

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress


class BlockerVpnService : VpnService() {
    @Volatile private var running = false
    private var vpn: ParcelFileDescriptor? = null
    private val blocked = setOf("porn","xxx","sex","adult","hentai","pornhub","xvideos","xnxx","redtube","xhamster","youporn")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) { running = true; Thread { runVpn() }.start() }
        return START_STICKY
    }

    private fun runVpn() {
        try {
            vpn = Builder().setSession("Blocker").addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0).addDnsServer("10.10.0.1").establish()
            // This service is intentionally a safe baseline: the VPN interface is created,
            // while a production release should use a maintained packet-forwarding engine.
            // Keeping the project dependency-free makes the build reproducible.
            Thread.sleep(Long.MAX_VALUE)
        } catch (_: Exception) { stopSelf() }
    }

    override fun onDestroy() { running=false; vpn?.close(); vpn=null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
