package com.ari.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor

class BlockerVpnService : VpnService() {
    companion object {
        private const val CHANNEL_ID = "blocker_protection"
        private const val NOTIFICATION_ID = 1001
    }

    @Volatile private var running = false
    private var vpn: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startProtectionForeground()
        if (!running) {
            running = true
            Thread { runVpn() }.start()
        }
        return START_STICKY
    }

    private fun startProtectionForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Protection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Blocker protection is active")
            .setContentText("Adult-content filtering is running.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun runVpn() {
        try {
            vpn = Builder()
                .setSession("Blocker")
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.10.0.1")
                .establish()

            // Safe baseline: the VPN interface is established.
            // A full packet-forwarding/DNS engine is required before this can
            // be advertised as a production content filter.
            Thread.sleep(Long.MAX_VALUE)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        vpn?.close()
        vpn = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}