package com.ari.blocker

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply { text = "Blocker of Adult Content\nDNS protection"; textSize = 24f; setPadding(32,64,32,32) }
        val status = TextView(this).apply { text = "Protection is off"; textSize = 18f; setPadding(32,16,32,16) }
        val button = Button(this).apply { text = "Enable protection" }
        button.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, 100)
            else startService(Intent(this, BlockerVpnService::class.java))
        }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(status); addView(button) })
    }
    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==100 && resultCode==RESULT_OK) startService(Intent(this, BlockerVpnService::class.java))
    }
}
