package com.ari.blocker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import java.security.MessageDigest

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var selected = 0
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        showHome()
        requestNotificationPermissionIfNeeded()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 248, 252))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
        }
        val shield = TextView(this).apply { text = "🛡️"; textSize = 44f; gravity = Gravity.CENTER }
        top.addView(shield, LinearLayout.LayoutParams(-1, dp(58)))
        val title = TextView(this).apply {
            text = "מגן התוכן"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
        }
        top.addView(title)
        root.addView(top)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.WHITE, 18)
        }
        listOf("ראשי", "חסימות", "הגדרות").forEachIndexed { index, label ->
            val b = Button(this).apply {
                text = label
                textSize = 13f
                setOnClickListener {
                    selected = index
                    when (index) { 0 -> showHome(); 1 -> showBlocks(); 2 -> showSettings() }
                }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(72)))
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        status = TextView(this).apply {
            text = "●  ההגנה אינה פעילה"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(98, 125, 152))
        }
        content.addView(status, cardParams())

        addText("מגן התוכן בודק בקשות DNS מול רשימת החסימה. במצב הנוכחי תעבורת IPv4 מנותבת דרך ה-VPN.")
        addButton("הפעל הגנה", Color.rgb(21,101,192)) { requestVpnPermission() }
        addButton("הסתר את סמל האפליקציה") { hideLauncherIcon() }
    }

    private fun showBlocks() {
        content.removeAllViews()
        addTitle("רשימת חסימות")
        addText("ניתן להוסיף דומיין לרשימה המקומית. לדוגמה: example.com")
        val input = EditText(this).apply {
            hint = "דומיין לחסימה"
            textSize = 16f
        }
        content.addView(input, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        addButton("הוסף חסימה", Color.rgb(21,101,192)) {
            val domain = input.text.toString().trim().lowercase()
            if (domain.matches(Regex("[a-z0-9.-]+")) && domain.contains(".")) {
                getSharedPreferences("custom_blocks", MODE_PRIVATE).edit().putBoolean(domain, true).apply()
                AlertDialog.Builder(this).setMessage("הדומיין נוסף לחסימה.").setPositiveButton("אישור", null).show()
                input.text.clear()
            } else {
                AlertDialog.Builder(this).setMessage("הזן דומיין תקין, למשל example.com").setPositiveButton("אישור", null).show()
            }
        }
    }

    private fun showSettings() {
        content.removeAllViews()
        addTitle("הגדרות")
        addText("הגנת הסרה משתמשת במנהל המכשיר של Android. היא מונעת הסרה רגילה כל עוד הרשאת מנהל המכשיר פעילה; Android עדיין מאפשר למשתמש לבטל את ההרשאה דרך הגדרות המערכת.")
        addButton("הפעל הגנת הסרה") { requestDeviceAdmin() }
        addButton("הגדר / שנה קוד גישה", Color.rgb(21,101,192)) { setPin() }
        addButton("הסרת האפליקציה", Color.rgb(183,28,28)) { requestUninstall() }
        addButton("הצג את סמל האפליקציה") { showLauncherIcon() }
        addButton("עדכון האפליקציה", Color.rgb(46,125,50)) { AppUpdater.downloadAndInstall(this) }
    }

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16,42,67))
            setPadding(0, dp(8), 0, dp(12))
        })
    }

    private fun addText(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(80,100,120))
            setPadding(0, 0, 0, dp(14))
        })
    }

    private fun addButton(text: String, color: Int = Color.WHITE, action: () -> Unit) {
        val b = Button(this).apply {
            this.text = text
            textSize = 15f
            setOnClickListener { action() }
            if (color != Color.WHITE) {
                setTextColor(Color.WHITE)
                background = rounded(color, 14)
            }
        }
        content.addView(b, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
    }

    private fun cardParams() = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(14)
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, VPN_REQUEST) else startProtection()
    }

    private fun startProtection() {
        val serviceIntent = Intent(this, BlockerVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        status.text = "●  ההגנה מתחילה..."
        status.setTextColor(Color.rgb(21,101,192))
    }

    private fun requestDeviceAdmin() {
        val component = ComponentName(this, BlockerDeviceAdminReceiver::class.java)
        val manager = getSystemService(DevicePolicyManager::class.java)
        if (manager.isAdminActive(component)) {
            AlertDialog.Builder(this).setMessage("הגנת ההסרה כבר פעילה.").setPositiveButton("אישור", null).show()
            return
        }
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "הפעלת הגנת ההסרה של מגן התוכן.")
        })
    }

    private fun setPin() {
        val input = EditText(this).apply {
            hint = "קוד בן 4 ספרות לפחות"
            inputType = 2
        }
        AlertDialog.Builder(this)
            .setTitle("קוד גישה")
            .setView(input)
            .setPositiveButton("שמירה") { _, _ ->
                val pin = input.text.toString()
                if (pin.length >= 4) prefs.edit().putString("pin_hash", hash(pin)).apply()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun requestUninstall() {
        if (prefs.getString("pin_hash", null) == null) {
            AlertDialog.Builder(this).setMessage("הגדר קודם קוד גישה.").setPositiveButton("אישור", null).show()
            return
        }
        val input = EditText(this).apply { hint = "קוד גישה"; inputType = 2 }
        AlertDialog.Builder(this)
            .setTitle("אישור הסרה")
            .setView(input)
            .setPositiveButton("המשך") { _, _ ->
                if (hash(input.text.toString()) == prefs.getString("pin_hash", null)) {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                } else {
                    AlertDialog.Builder(this).setMessage("קוד שגוי.").setPositiveButton("אישור", null).show()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun hideLauncherIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun showLauncherIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) startProtection()
    }

    companion object {
        private const val VPN_REQUEST = 100
        private const val NOTIFICATION_REQUEST = 101
    }
}

class BlockerDeviceAdminReceiver : DeviceAdminReceiver()
