package com.ari.blocker

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Blocker of Adult Content\nProtection"
            textSize = 24f
            setPadding(32, 64, 32, 32)
        }

        status = TextView(this).apply {
            text = "Protection is off"
            textSize = 18f
            setPadding(32, 16, 32, 16)
        }

        val enable = Button(this).apply {
            text = "Enable protection"
            setOnClickListener { requestVpnPermission() }
        }

        val note = TextView(this).apply {
            text = "The app uses Android VPN permission. You must approve the VPN connection before protection can start."
            textSize = 14f
            setPadding(32, 16, 32, 16)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(status)
            addView(enable)
            addView(note)
        })
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST)
        } else {
            startProtection()
        }
    }

    private fun startProtection() {
        val serviceIntent = Intent(this, BlockerVpnService::class.java)
        startForegroundService(serviceIntent)
        status.text = "Protection requested"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            startProtection()
        }
    }

    companion object {
        private const val VPN_REQUEST = 100
    }
}
