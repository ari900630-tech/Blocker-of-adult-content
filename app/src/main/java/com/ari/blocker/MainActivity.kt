package com.ari.blocker

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.AlertDialog
import android.content.ComponentName
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
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import java.security.MessageDigest

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var unlocked = false
    private val navButtons = mutableListOf<TextView>()
    private var selectedNav = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        if (prefs.getString("pin_hash", null) != null) showLockScreen()
        else showSetup()
        requestNotificationPermissionIfNeeded()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = purpleGradient()
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
        }
        top.addView(TextView(this).apply {
            text = "🛡️"
            textSize = 34f
        }, LinearLayout.LayoutParams(dp(50), dp(54)))
        top.addView(TextView(this).apply {
            text = "מגן התוכן\nApp Lock"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(60), 1f))
        root.addView(top)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = rounded(Color.argb(245, 255, 255, 255), 20)
        }
        val navItems = listOf(
            Triple("⌂", "ראשי", 0),
            Triple("▦", "אפליקציות", 1),
            Triple("⚙", "הגדרות", 2)
        )
        navItems.forEach { (symbol, label, index) ->
            nav.addView(TextView(this).apply {
                navButtons.add(this)
                text = symbol
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(58, 37, 104))
                contentDescription = label
                background = rounded(if (index == 0) Color.rgb(88, 231, 226) else Color.argb(245, 255, 255, 255), 18)
                setOnClickListener {
                    if (!unlocked && prefs.getString("pin_hash", null) != null) {
                        showLockScreen()
                        return@setOnClickListener
                    }
                    selectedNav = index
                    refreshNavSelection()
                    when (index) {
                        0 -> showHome()
                        1 -> showAppControl()
                        2 -> showSettings()
                    }
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            })
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
        refreshNavSelection()
    }

    private fun refreshNavSelection() {
        navButtons.forEachIndexed { index, button ->
            button.isSelected = index == selectedNav
            button.background = rounded(if (index == selectedNav) Color.rgb(88, 231, 226) else Color.argb(245, 255, 255, 255), 18)
        }
    }

    private fun showSetup() {
        unlocked = true
        content.removeAllViews()
        addCardTitle("ברוכים הבאים")
        addText("בפעם הראשונה יש להפעיל את ההגנה ולהגדיר דרך כניסה. לאחר מכן האפליקציה תוכל להגן על אתרים ואפליקציות שבחרת.")
        addButton("1. הגדר קוד / ביומטריה", Color.rgb(21, 101, 192)) { setPin() }
        addButton("2. הפעל הגנה", Color.rgb(46, 125, 50)) { requestVpnPermission() }
        addButton("3. הגדר אפליקציות") { showAppControl() }
        addText("חשוב: Android לא מאפשר לאפליקציה רגילה לנעול את כפתור הבית/החזרה או להפעיל שירות נגישות בלי אישור מפורש שלך.")
    }

    private fun showLockScreen() {
        unlocked = false
        content.removeAllViews()
        addCardTitle("🔐 קוד גישה")
        addText("הזן את הקוד שהגדרת. אפשר לאשר גם דרך כפתור ✓ במקלדת.")
        val mode = prefs.getString("auth_mode", "PIN4")
        val input = EditText(this)
        input.apply {
            hint = if (mode == "PIN4") "4 ספרות" else "קוד גישה"
            inputType = if (mode == "PIN4") InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 20f
            gravity = Gravity.CENTER
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    verifyMainCode(input)
                    true
                } else false
            }
        }
        content.addView(input, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(12) })
        addButton("✓ אישור", Color.rgb(21, 101, 192)) { verifyMainCode(input) }
        if (mode == "BIOMETRIC") addButton("טביעת אצבע / ביומטריה") { authenticateMainBiometric() }
    }

    private fun verifyMainCode(input: EditText) {
        if (hash(input.text.toString()) == prefs.getString("pin_hash", null)) {
            unlocked = true
            showHome()
        } else {
            input.selectAll()
            input.error = "קוד שגוי"
        }
    }

    private fun authenticateMainBiometric() {
        if (Build.VERSION.SDK_INT < 28) {
            showMessage("ביומטריה אינה זמינה בגרסת Android זו.")
            return
        }
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle("מגן התוכן")
            .setSubtitle("אימות ביומטרי")
            .setDescription("אשר כניסה באמצעות טביעת אצבע או ביומטריה של המכשיר.")
            .setNegativeButton("שימוש בקוד", executor) { _, _ -> }
            .build()
        prompt.authenticate(android.os.CancellationSignal(), executor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                    runOnUiThread {
                        unlocked = true
                        showHome()
                    }
                }
            })
    }

    private fun showHome() {
        content.removeAllViews()
        addCardTitle("App Lock")
        addText("הגנה ונעילת אפליקציות")
        val active = BlockerVpnService.isProtectionActive

        addProtectionSwitch(active)
        addButton("🌐  חיפוש מוגן", Color.rgb(88, 231, 226)) { requestProtectedSearch() }
    }

    private fun addProtectionSwitch(active: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
            background = rounded(Color.argb(245, 255, 255, 255), 24)
        }
        row.addView(TextView(this).apply {
            text = if (active) "●  Protection ON" else "○  Protection OFF"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(58, 37, 104))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(62), 1f))
        row.addView(android.widget.Switch(this).apply {
            isChecked = active
            text = ""
            contentDescription = if (active) "כיבוי ההגנה" else "הפעלת ההגנה"
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    requestVpnPermission()
                } else {
                    // כיבוי בלבד דורש קוד; הפעלה אינה דורשת קוד.
                    isChecked = true
                    requestStopProtection()
                }
            }
        }, LinearLayout.LayoutParams(dp(64), dp(56)))
        content.addView(row, LinearLayout.LayoutParams(-1, dp(78)).apply { bottomMargin = dp(12) })
    }

    private fun requestProtectedSearch() {
        val pinHash = prefs.getString("pin_hash", null)
        if (pinHash == null) { showMessage("כדי לפתוח חיפוש מוגן צריך להגדיר קוד גישה."); return }
        val input = EditText(this).apply { hint = "קוד גישה"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; imeOptions = EditorInfo.IME_ACTION_DONE; setSingleLine(true) }
        val dialog = AlertDialog.Builder(this).setTitle("פתיחת חיפוש מוגן").setMessage("הזן את קוד הגישה כדי לפתוח את החיפוש המוגן.").setView(input).setPositiveButton("פתח", null).setNegativeButton("ביטול", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (hash(input.text.toString()) != pinHash) input.error = "קוד שגוי" else { dialog.dismiss(); openProtectedSearch() } } }
        dialog.show()
    }

    private fun showAppControl() {
        startActivity(Intent(this, AppControlActivity::class.java))
    }

    private fun showAppControl() {
        selectedNav = 1
        refreshNavSelection()
        content.removeAllViews()
        addCardTitle("App Lock")
        addText("בחר אפליקציות לנעילה")

        if (!isAccessibilityEnabled()) {
            addButton("▶ הפעל בקרת אפליקציות", Color.rgb(88, 231, 226)) { openAccessibilitySettings() }
        }

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        getLauncherApps().forEach { app ->
            val pkg = app.activityInfo.packageName
            if (pkg == packageName) return@forEach
            val label = runCatching { app.activityInfo.loadLabel(packageManager).toString() }.getOrDefault(pkg)
            val icon = runCatching { app.activityInfo.loadIcon(packageManager) }.getOrNull()
            val locked = prefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(8), dp(4))
                background = rounded(Color.argb(245, 255, 255, 255), 18)
            }
            if (icon != null) row.addView(android.widget.ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6); rightMargin = dp(8) })
            row.addView(TextView(this).apply {
                text = label
                textSize = 15f
                typeface = if (locked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.rgb(58, 37, 104))
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(50), 1f))
            row.addView(android.widget.Switch(this).apply {
                isChecked = locked
                text = ""
                contentDescription = "נעילת $label"
                setOnCheckedChangeListener { _, checked ->
                    val current = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (checked) current.add(pkg) else current.remove(pkg)
                    prefs.edit().putStringSet("blocked_apps", current).apply()
                }
            }, LinearLayout.LayoutParams(dp(56), dp(50)))
            list.addView(row, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(7) })
        }
        val scroll = ScrollView(this).apply { addView(list) }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun getLauncherApps(): List<android.content.pm.ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return try {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .filter { it.activityInfo?.packageName != packageName }
                .groupBy { it.activityInfo.packageName }
                .mapNotNull { (_, entries) -> entries.firstOrNull() }
                .sortedBy { runCatching { it.activityInfo.loadLabel(packageManager).toString() }.getOrDefault(it.activityInfo.packageName) }
        } catch (_: Exception) { emptyList() }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val manager = getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
            if (!manager.isEnabled) return false
            manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info -> info.resolveInfo?.serviceInfo?.packageName == packageName }
        } catch (_: Exception) { false }
    }

    private fun openAccessibilitySettings() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openProtectedSearch() {
        val chromePackage = "com.android.chrome"
        if (packageManager.getLaunchIntentForPackage(chromePackage) == null) {
            showMessage("Chrome לא מותקן במכשיר.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?safe=active")).apply {
            setPackage(chromePackage)
        }
        AppBlockAccessibilityServiceHolder.service?.allowPackageFromProtectedApp(chromePackage, 5 * 60_000L)
        startActivity(intent)
    }

    private fun showBlocks() {
        content.removeAllViews()
        addCardTitle("חסימת אתרים")
        addText("הוסף דומיינים שתרצה לחסום.")
        val input = EditText(this).apply {
            hint = "לדוגמה: example.com"
            textSize = 16f
        }
        content.addView(input, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
        addButton("＋ הוסף חסימה", Color.rgb(21,101,192)) {
            val domain = input.text.toString().trim().lowercase()
            if (domain.matches(Regex("[a-z0-9.-]+")) && domain.contains(".")) {
                getSharedPreferences("custom_blocks", MODE_PRIVATE).edit().putBoolean(domain, true).apply()
                BlockerVpnService.reloadCustomBlocks()
                showBlocks()
            } else {
                input.error = "דומיין לא תקין"
            }
        }
        val blocks = getSharedPreferences("custom_blocks", MODE_PRIVATE).all.keys.map { it.lowercase() }.sorted()
        if (blocks.isNotEmpty()) {
            addText("חסימות שהוספת")
            blocks.forEach { domain ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), 0, dp(8), 0)
                    background = rounded(Color.WHITE, 18)
                }
                row.addView(TextView(this).apply {
                    text = domain
                    textSize = 16f
                    setTextColor(Color.rgb(30,45,60))
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
                row.addView(Button(this).apply {
                    text = "ביטול חסימה"
                    isAllCaps = false
                    setOnClickListener {
                        getSharedPreferences("custom_blocks", MODE_PRIVATE).edit().remove(domain).apply()
                        BlockerVpnService.reloadCustomBlocks()
                        showBlocks()
                    }
                }, LinearLayout.LayoutParams(dp(120), dp(52)))
                content.addView(row, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun showSettings() {
        content.removeAllViews()
        addCardTitle("הגדרות המגן")
        addText("הגדרות אבטחה, קוד, בקרת אפליקציות ועדכונים.")
        addButton("🔐 סוג קוד / טביעת אצבע", Color.rgb(21,101,192)) { setPin() }
        addButton("🙈 הסתר את סמל האפליקציה") { hideLauncherIcon() }
        addButton("👁️ הצג את סמל האפליקציה") { showLauncherIcon() }
        addButton("↻ עדכון האפליקציה", Color.rgb(46,125,50)) { AppUpdater.downloadAndInstall(this) }
        addButton("⚙ פתח הגדרות VPN") { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
        addButton("⏸ השבת את ההגנה", Color.rgb(183,28,28)) {
            if (BlockerVpnService.isProtectionActive) requestStopProtection()
            else showMessage("ההגנה כבר מושבתת.")
        }
        addDeviceManagementControls()
    }

    private fun addDeviceManagementControls() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isOwner = dpm.isDeviceOwnerApp(packageName) || dpm.isProfileOwnerApp(packageName)
        val text = if (isOwner) "🔒 הגנת הסרה פעילה" else "🔓 הגנת הסרה לא הופעלה"
        addText(text)
        if (isOwner) {
            addButton("✓ הגנת הסרה פעילה", Color.rgb(46,125,50), selected = true) {
                BlockerDeviceAdminReceiver.enforceUninstallBlocked(this)
                showMessage("הגנת ההסרה מופעלת.")
            }
        } else {
            addButton("🔐 הפעל הרשאת מנהל המכשיר", Color.rgb(55,78,102)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, BlockerDeviceAdminReceiver.component(this@MainActivity))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "הרשאה זו מאפשרת למגן התוכן להשתלב במצב ניהול המכשיר. חסימת הסרה מתוך הגדרות תעבוד רק לאחר שהמכשיר הוגדר כ-Device Owner/Profile Owner.")
                }
                startActivity(intent)
            }
        }
        addText("הערה: הרשאת מנהל המכשיר לבדה אינה הופכת את האפליקציה ל-Device Owner. במכשיר שכבר מוגדר לשימוש, Android דורש תהליך ניהול/Provisioning מתאים כדי לקבל את היכולת לחסום הסרה. המראה המדויק של מסך ההגדרות תלוי בגרסת Android וביצרן.")
    }

    private fun addCardTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(12))
        })
    }

    private fun addText(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(14))
        })
    }

    private fun addButton(
        text: String,
        color: Int = Color.WHITE,
        selected: Boolean = false,
        action: () -> Unit
    ) {
        content.addView(Button(this).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            isSelected = selected
            stateListAnimator = null
            setTextColor(if (color == Color.WHITE) Color.rgb(30,45,60) else Color.WHITE)
            background = buttonStates(color, selected)
            setOnClickListener {
                isPressed = true
                postDelayed({ isPressed = false }, 120L)
                action()
            }
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(10) })
    }

    private fun buttonStates(base: Int, selected: Boolean): StateListDrawable {
        val states = StateListDrawable()
        val pressed = if (base == Color.WHITE) Color.rgb(225, 232, 240) else Color.rgb(
            (Color.red(base) * 0.78f).toInt(),
            (Color.green(base) * 0.78f).toInt(),
            (Color.blue(base) * 0.78f).toInt()
        )
        val selectedColor = if (base == Color.WHITE) Color.rgb(215, 230, 245) else base
        states.addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, 18))
        states.addState(intArrayOf(android.R.attr.state_selected), rounded(selectedColor, 18))
        states.addState(intArrayOf(), rounded(base, 18))
        return states
    }

    private fun requestVpnPermission() {
        status.text = "…  ממתין לאישור ההגנה"
        status.setTextColor(Color.rgb(21, 101, 192))
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST)
        } else {
            startProtection()
            showHome()
        }
    }

    private fun startProtection() {
        prefs.edit().putBoolean("protection_enabled", true).apply()
        val serviceIntent = Intent(this, BlockerVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        if (::status.isInitialized) {
            status.text = "●  ההגנה פעילה"
            status.setTextColor(Color.rgb(46,125,50))
        }
    }

    private fun stopProtection() {
        prefs.edit().putBoolean("protection_enabled", false).apply()
        BlockerVpnService.forceStop()
        stopService(Intent(this, BlockerVpnService::class.java))
        if (::status.isInitialized) {
            status.text = "○  ההגנה כבויה"
            status.setTextColor(Color.rgb(183,28,28))
        }
        showHome()
    }

    private fun requestStopProtection() {
        val pinHash = prefs.getString("pin_hash", null)
        if (pinHash == null) {
            showMessage("כדי לכבות את ההגנה צריך להגדיר קוד גישה.")
            return
        }
        val input = EditText(this).apply {
            hint = "קוד גישה"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("כיבוי הגנת הגלישה")
            .setMessage("ההגנה פעילה. כדי לכבות אותה יש לאשר עם קוד הגישה.")
            .setView(input)
            .setPositiveButton("כיבוי", null)
            .setNegativeButton("ביטול", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (hash(input.text.toString()) != pinHash) {
                            input.error = "קוד שגוי"
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        stopProtection()
                    }
                }
            }.show()
    }

    private fun setPin() {
        val modes = arrayOf("4 ספרות", "קוד באורך חופשי", "טביעת אצבע / ביומטריה")
        val keys = arrayOf("PIN4", "PASSWORD", "BIOMETRIC")
        AlertDialog.Builder(this)
            .setTitle("בחר דרך כניסה")
            .setSingleChoiceItems(modes, -1) { dialog, which ->
                dialog.dismiss()
                askForNewCode(keys[which])
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun askForNewCode(mode: String) {
        val oldHash = prefs.getString("pin_hash", null)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
        }
        val oldInput = EditText(this).apply {
            hint = if (oldHash == null) "אין קוד קודם" else "קוד נוכחי"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        if (oldHash != null) box.addView(oldInput, LinearLayout.LayoutParams(-1, dp(56)))
        val newInput = EditText(this).apply {
            hint = when (mode) {
                "PIN4" -> "קוד חדש — בדיוק 4 ספרות"
                "PASSWORD" -> "קוד חדש — באורך לבחירתך"
                else -> "קוד גיבוי — לפחות 4 תווים"
            }
            inputType = if (mode == "PIN4") InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
        }
        box.addView(newInput, LinearLayout.LayoutParams(-1, dp(56)))

        AlertDialog.Builder(this)
            .setTitle("הגדרת דרך כניסה")
            .setView(box)
            .setPositiveButton("שמירה", null)
            .setNegativeButton("ביטול", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (oldHash != null && hash(oldInput.text.toString()) != oldHash) {
                            oldInput.error = "קוד נוכחי שגוי"
                            return@setOnClickListener
                        }
                        val value = newInput.text.toString()
                        val valid = if (mode == "PIN4") value.length == 4 && value.all { it.isDigit() } else value.length >= 4
                        if (!valid) {
                            newInput.error = "קוד לא תקין"
                            return@setOnClickListener
                        }
                        prefs.edit().putString("pin_hash", hash(value)).putString("auth_mode", mode).apply()
                        unlocked = true
                        dialog.dismiss()
                        showMessage("ההגדרה נשמרה.")
                    }
                }
            }.show()
    }

    private fun requestUninstall() {
        val pinHash = prefs.getString("pin_hash", null) ?: run { showMessage("הגדר קוד גישה לפני הסרה."); return }
        val input = EditText(this).apply { hint = "קוד גישה"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; imeOptions = EditorInfo.IME_ACTION_DONE; setSingleLine(true) }
        val dialog = AlertDialog.Builder(this).setTitle("הסרת האפליקציה").setMessage("הזן את קוד הגישה כדי להמשיך.").setView(input).setPositiveButton("המשך", null).setNegativeButton("ביטול", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hash(input.text.toString()) != pinHash) input.error = "קוד שגוי"
                else {
                    dialog.dismiss()
                    if (BlockerVpnService.isProtectionActive) stopProtection()
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                }
            }
        }
        dialog.show()
    }

    private fun hideLauncherIcon() {
        val visible = ComponentName(this, "com.ari.blocker.LauncherAlias")
        val hidden = ComponentName(this, "com.ari.blocker.HiddenLauncherAlias")
        packageManager.setComponentEnabledSetting(visible, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(hidden, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        showMessage("הסמל הוסתר. לחיצה כפולה על המקום השקוף פותחת את האפליקציה.")
    }

    private fun showLauncherIcon() {
        val visible = ComponentName(this, "com.ari.blocker.LauncherAlias")
        val hidden = ComponentName(this, "com.ari.blocker.HiddenLauncherAlias")
        packageManager.setComponentEnabledSetting(hidden, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(visible, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        showMessage("הסמל הוחזר.")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("אישור", null).show()
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun purpleGradient() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(151, 133, 247), Color.rgb(91, 42, 190), Color.rgb(48, 10, 120))
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    override fun onResume() {
        super.onResume()
        if (unlocked && selectedNav == 0) showHome()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                startProtection()
                showHome()
            } else {
                showHome()
                showMessage("הפעלת ההגנה בוטלה. אפשר לנסות שוב.")
            }
        }
    }

    companion object {
        private const val VPN_REQUEST = 100
        private const val NOTIFICATION_REQUEST = 101
    }
}