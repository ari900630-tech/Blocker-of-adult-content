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
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import java.security.MessageDigest

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        if (prefs.getString("pin_hash", null) != null) {
            showLockScreen()
        } else {
            showHome()
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 248, 252))
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        top.addView(TextView(this).apply {
            text = "🛡️"
            textSize = 40f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(52)))
        top.addView(TextView(this).apply {
            text = "מגן התוכן"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
        })
        root.addView(top)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(4))
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(14))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.WHITE, 20)
        }
        listOf("ראשי", "חסימות", "הגדרות").forEachIndexed { index, label ->
            val b = Button(this).apply {
                text = label
                textSize = 13f
                isAllCaps = false
                setOnClickListener {
                    if (!unlocked && prefs.getString("pin_hash", null) != null) {
                        showLockScreen()
                        return@setOnClickListener
                    }
                    when (index) {
                        0 -> showHome()
                        1 -> showBlocks()
                        2 -> showSettings()
                    }
                }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(70)))
        setContentView(root)
    }

    private fun showLockScreen() {
        unlocked = false
        content.removeAllViews()
        addTitle("הזן קוד גישה")
        addText("כדי להיכנס למגן התוכן יש להזין את הקוד שהגדרת.")
        val input = EditText(this).apply {
            hint = "קוד גישה"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 18f
        }
        content.addView(input, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
        addButton("פתיחה", Color.rgb(21, 101, 192)) {
            if (hash(input.text.toString()) == prefs.getString("pin_hash", null)) {
                unlocked = true
                showHome()
            } else {
                input.text.clear()
                input.error = "קוד שגוי"
            }
        }
    }

    private fun showHome() {
        content.removeAllViews()
        addTitle("הגנה")
        val active = BlockerVpnService.isProtectionActive
        status = TextView(this).apply {
            text = if (active) "●  ההגנה פעילה" else "●  ההגנה אינה פעילה"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (active) Color.rgb(46, 125, 50) else Color.rgb(98, 125, 152))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.WHITE, 18)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })
        addText("החסימה פועלת דרך DNS. אתרים ברשימת החסימה ייחסמו, ובמקביל הגלישה הרגילה בכרום תמשיך לעבוד.")
        addButton(if (active) "ההגנה פעילה" else "הפעל הגנה", Color.rgb(21, 101, 192)) {
            if (!active) requestVpnPermission()
        }
        addButton("פתח חיפוש חדש") {
            openProtectedSearch()
        }
        addButton("הסתר את סמל האפליקציה") { hideLauncherIcon() }
    }

    private fun openProtectedSearch() {
        // Open Google through the app's protected search route.
        // The SafeSearch URL parameter requests filtered results, while the
        // app's DNS protection remains the network-level block layer.
        val protectedSearch = Uri.parse("https://www.google.com/search?safe=active")
        startActivity(Intent(Intent.ACTION_VIEW, protectedSearch))
    }

    private fun showBlocks() {
        content.removeAllViews()
        addTitle("רשימת חסימות")
        addText("הוסף דומיינים שתרצה לחסום. לדוגמה: example.com")
        val input = EditText(this).apply {
            hint = "דומיין לחסימה"
            textSize = 16f
        }
        content.addView(input, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(10) })
        addButton("הוסף חסימה", Color.rgb(21, 101, 192)) {
            val domain = input.text.toString().trim().lowercase()
            if (domain.matches(Regex("[a-z0-9.-]+")) && domain.contains(".")) {
                getSharedPreferences("custom_blocks", MODE_PRIVATE).edit().putBoolean(domain, true).commit()
                BlockerVpnService.reloadCustomBlocks()
                AlertDialog.Builder(this)
                    .setTitle("נשמר")
                    .setMessage("הדומיין נוסף לרשימת החסימה.")
                    .setPositiveButton("אישור", null)
                    .show()
                input.text.clear()
            } else {
                AlertDialog.Builder(this)
                    .setMessage("הזן דומיין תקין, למשל example.com")
                    .setPositiveButton("אישור", null)
                    .show()
            }
        }
    }

    private fun showSettings() {
        content.removeAllViews()
        addTitle("הגדרות")
        addText("הגנת ההסרה משתמשת במנהל המכשיר של Android. Android עצמו עדיין שולט בחיבור ה-VPN ובהתראות המערכת.")
        addButton("הפעל הגנת הסרה") { requestDeviceAdmin() }
        addButton("הגדר / שנה קוד גישה", Color.rgb(21, 101, 192)) { setPin() }
        addButton("הסרת האפליקציה", Color.rgb(183, 28, 28)) { requestUninstall() }
        addButton("הצג את סמל האפליקציה") { showLauncherIcon() }
        addButton("עדכון האפליקציה", Color.rgb(46, 125, 50)) { AppUpdater.downloadAndInstall(this) }
        addButton("פתח הגדרות VPN") {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        }
    }

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 42, 67))
            setPadding(0, dp(8), 0, dp(12))
        })
    }

    private fun addText(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(80, 100, 120))
            setPadding(0, 0, 0, dp(14))
        })
    }

    private fun addButton(text: String, color: Int = Color.WHITE, action: () -> Unit) {
        val b = Button(this).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            setOnClickListener { action() }
            if (color != Color.WHITE) {
                setTextColor(Color.WHITE)
                background = rounded(color, 14)
            } else {
                background = rounded(Color.WHITE, 14)
            }
        }
        content.addView(b, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, VPN_REQUEST) else startProtection()
    }

    private fun startProtection() {
        val serviceIntent = Intent(this, BlockerVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        status.text = "●  ההגנה מופעלת"
        status.setTextColor(Color.rgb(46, 125, 50))
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
        val oldHash = prefs.getString("pin_hash", null)
        val oldInput = EditText(this).apply {
            hint = if (oldHash == null) "אין קוד קודם" else "קוד נוכחי"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val newInput = EditText(this).apply {
            hint = "קוד חדש — לפחות 4 ספרות"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            if (oldHash != null) addView(oldInput, LinearLayout.LayoutParams(-1, dp(56)))
            addView(newInput, LinearLayout.LayoutParams(-1, dp(56)))
        }

        AlertDialog.Builder(this)
            .setTitle(if (oldHash == null) "יצירת קוד גישה" else "שינוי קוד גישה")
            .setView(box)
            .setPositiveButton("שמירה", null)
            .setNegativeButton("ביטול", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val oldOk = oldHash == null || hash(oldInput.text.toString()) == oldHash
                        val newPin = newInput.text.toString()
                        if (!oldOk) {
                            oldInput.error = "הקוד הנוכחי שגוי"
                            return@setOnClickListener
                        }
                        if (newPin.length < 4 || !newPin.all { it.isDigit() }) {
                            newInput.error = "יש להזין לפחות 4 ספרות"
                            return@setOnClickListener
                        }
                        val saved = prefs.edit().putString("pin_hash", hash(newPin)).commit()
                        if (saved) {
                            unlocked = true
                            dialog.dismiss()
                            AlertDialog.Builder(this)
                                .setTitle("הקוד נשמר")
                                .setMessage("מהפעם הבאה שתפתח את האפליקציה יידרש הקוד.")
                                .setPositiveButton("אישור", null)
                                .show()
                        }
                    }
                }
            }.show()
    }

    private fun requestUninstall() {
        if (prefs.getString("pin_hash", null) == null) {
            AlertDialog.Builder(this).setMessage("הגדר קודם קוד גישה.").setPositiveButton("אישור", null).show()
            return
        }
        val input = EditText(this).apply {
            hint = "קוד גישה"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
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
        setColor(color)
        cornerRadius = dp(radius).toFloat()
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
