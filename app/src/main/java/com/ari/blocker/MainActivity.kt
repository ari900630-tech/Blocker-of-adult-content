package com.ari.blocker

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var hideButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNotificationPermissionIfNeeded()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(22))
            setBackgroundColor(Color.rgb(245, 248, 252))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val shield = TextView(this).apply {
            text = "🛡"
            textSize = 56f
            gravity = Gravity.CENTER
        }
        header.addView(shield, LinearLayout.LayoutParams(-1, dp(82)))

        val title = TextView(this).apply {
            text = "Blocker"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
        }
        header.addView(title)

        val subtitle = TextView(this).apply {
            text = "Protection against unwanted adult domains"
            textSize = 15f
            setTextColor(Color.rgb(98, 125, 152))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(18))
        }
        header.addView(subtitle)
        root.addView(header)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.WHITE, 18)
        }

        status = TextView(this).apply {
            text = "●  Protection is off"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(98, 125, 152))
        }
        card.addView(status)

        val info = TextView(this).apply {
            text = "DNS requests are checked against the local and central blocklists. Normal web traffic is not routed through the VPN."
            textSize = 14f
            setTextColor(Color.rgb(98, 125, 152))
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(info)
        root.addView(card, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        enableButton = Button(this).apply {
            text = "Enable protection"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(21, 101, 192), 14)
            setOnClickListener { requestVpnPermission() }
        }
        root.addView(enableButton, LinearLayout.LayoutParams(-1, dp(54)).apply {
            bottomMargin = dp(10)
        })

        hideButton = Button(this).apply {
            text = "Hide app icon"
            textSize = 15f
            setOnClickListener { hideLauncherIcon() }
        }
        root.addView(hideButton, LinearLayout.LayoutParams(-1, dp(52)))

        val restore = Button(this).apply {
            text = "Show app icon again"
            textSize = 14f
            setOnClickListener { showLauncherIcon() }
        }
        root.addView(restore, LinearLayout.LayoutParams(-1, dp(48)))

        val footer = TextView(this).apply {
            text = "Tip: after hiding the icon, the active protection notification can still open the app."
            textSize = 13f
            setTextColor(Color.rgb(98, 125, 152))
            setPadding(dp(4), dp(14), dp(4), 0)
        }
        root.addView(footer)

        setContentView(root)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        status.text = "●  Protection is starting"
        status.setTextColor(Color.rgb(21, 101, 192))
    }

    private fun hideLauncherIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        hideButton.text = "Icon hidden"
        hideButton.isEnabled = false
    }

    private fun showLauncherIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        hideButton.text = "Hide app icon"
        hideButton.isEnabled = true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            startProtection()
        }
    }

    companion object {
        private const val VPN_REQUEST = 100
        private const val NOTIFICATION_REQUEST = 101
    }
}
