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
import android.widget.ProgressBar
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
            text = "מגן +\nApp Lock"
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
                    root.post {
                        when (index) {
                            0 -> showHome()
                            1 -> showAppControl()
                            2 -> showSettings()
                        }
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
        content.setPadding(dp(18), dp(12), dp(18), dp(18))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = rounded(Color.rgb(8, 67, 151), 30)
        }
        card.addView(TextView(this).apply { text="🛡️🔒"; textSize=46f; gravity=Gravity.CENTER },
            LinearLayout.LayoutParams(-1, dp(62)))
        card.addView(TextView(this).apply {
            text="פתח את מגן +"; textSize=23f; typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity=Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(40)))
        card.addView(TextView(this).apply {
            text="הזן את הקוד"; textSize=15f; setTextColor(Color.rgb(241,238,255)); gravity=Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))

        val mode = prefs.getString("auth_mode", "PIN4")
        if (mode == "PIN4") {
            val input = EditText(this).apply {
                tag = "main_pin_input"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                textSize = 24f; gravity = Gravity.CENTER; isSingleLine = true
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.argb(180,255,255,255))
                hint = "—  —  —  —"
            }
            card.addView(input, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin=dp(8) })
            val keys = arrayOf(arrayOf("1","2","3"),arrayOf("4","5","6"),arrayOf("7","8","9"),arrayOf("","0","⌫"))
            keys.forEach { rowValues ->
                val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
                rowValues.forEach { key ->
                    val b=TextView(this).apply {
                        text=key; textSize=24f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
                        background=rounded(if(key.isEmpty()) Color.TRANSPARENT else Color.argb(45,255,255,255), 22)
                        setOnClickListener {
                            when(key) {
                                "⌫" -> if(input.text.isNotEmpty()) input.text.delete(input.text.length-1,input.text.length)
                                "" -> {}
                                else -> if(input.text.length<4) { input.append(key); if(input.text.length==4) verifyMainCode(input) }
                            }
                        }
                    }
                    row.addView(b, LinearLayout.LayoutParams(dp(62),dp(52)).apply { leftMargin=dp(5);rightMargin=dp(5);topMargin=dp(4);bottomMargin=dp(4) })
                }
                card.addView(row)
            }
        } else if (mode == "PATTERN") {
            card.addView(TextView(this).apply {
                text = "צייר את סיסמת ההחלקה"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(34)))
            val pattern = PatternLockView(this)
            pattern.onPatternComplete = { value ->
                if (hash("PATTERN:" + value.joinToString(",")) == prefs.getString("pin_hash", null)) {
                    unlocked = true
                    showHome()
                } else {
                    pattern.clearPattern()
                    showMessage("סיסמה שגויה. נסה שוב.")
                }
            }
            card.addView(pattern, LinearLayout.LayoutParams(-1, dp(230)))
        } else {
            val input=EditText(this).apply {
                hint="קוד גישה"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                textSize=20f; gravity=Gravity.CENTER; setSingleLine(true)
                setTextColor(Color.WHITE)
            }
            card.addView(input,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(10)})
            addButton("✓ אישור",Color.rgb(88,231,226)){verifyMainCode(input)}
        }
        if (mode == "BIOMETRIC") addButton("טביעת אצבע / ביומטריה") { authenticateMainBiometric() }
        content.addView(card,LinearLayout.LayoutParams(-1,-2))
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
            .setTitle("מגן +")
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
        content.setPadding(dp(14), dp(4), dp(14), dp(12))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            background = rounded(Color.argb(235, 125, 96, 226), 30)
        }

        hero.addView(TextView(this).apply {
            text = "🛡️"
            textSize = 58f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(78)))

        hero.addView(TextView(this).apply {
            text = "מגן +"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(42)))

        hero.addView(TextView(this).apply {
            text = "הגנה חכמה לאפליקציות שלך"
            textSize = 15f
            setTextColor(Color.rgb(239, 236, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))

        val active = BlockerVpnService.isProtectionActive
        hero.addView(addCuteProtectionSwitch(active), LinearLayout.LayoutParams(-1, dp(82)).apply {
            topMargin = dp(14)
            bottomMargin = dp(10)
        })

        hero.addView(TextView(this).apply {
            text = if (active) "✓ ההגנה פעילה" else "○ ההגנה כבויה"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(28)))

        content.addView(hero, LinearLayout.LayoutParams(-1, dp(310)).apply {
            bottomMargin = dp(14)
        })

        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(10), dp(8))
            background = rounded(Color.argb(245, 255, 255, 255), 24)
            setOnClickListener { requestProtectedSearch() }
        }
        search.addView(TextView(this).apply {
            text = "🔎"
            textSize = 25f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(54)))
        search.addView(TextView(this).apply {
            text = "חיפוש מוגן"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(76, 53, 140))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        search.addView(TextView(this).apply {
            text = "›"
            textSize = 28f
            setTextColor(Color.rgb(118, 91, 220))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(34), dp(54)))
        content.addView(search, LinearLayout.LayoutParams(-1, dp(70)).apply {
            bottomMargin = dp(12)
        })

    }

    private fun addCuteProtectionSwitch(active: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(14), dp(8))
            background = rounded(Color.argb(245, 255, 255, 255), 25)
        }
        row.addView(TextView(this).apply {
            text = if (active) "הגנה פעילה" else "הפעל הגנה"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(91, 62, 160))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(64), 1f))
        val protectionSwitch = android.widget.Switch(this).apply {
            isChecked = active
            text = ""
            scaleX = 1.18f
            scaleY = 1.18f
            contentDescription = if (active) "כיבוי ההגנה" else "הפעלת ההגנה"
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    requestVpnPermission()
                } else {
                    isChecked = true
                    requestStopProtection()
                }
            }
        }
        row.addView(protectionSwitch, LinearLayout.LayoutParams(dp(66), dp(58)))
        row.setOnClickListener {
            protectionSwitch.performClick()
        }
        return row
    }

    private fun homeQuickCard(icon: String, label: String, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(Color.argb(225, 255, 255, 255), 22)
            setOnClickListener { action() }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 23f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(32)))
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(91, 62, 160))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(28)))
        }
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
        content.removeAllViews()
        content.setPadding(dp(14), dp(6), dp(14), dp(12))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.argb(150, 255, 255, 255), 22)
        }
        header.addView(TextView(this).apply {
            text = "📱"
            textSize = 28f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(50)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBox.addView(TextView(this).apply {
            text = "האפליקציות שלי"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-1, dp(32)))
        titleBox.addView(TextView(this).apply {
            text = "טוען אפליקציות..."
            textSize = 13f
            setTextColor(Color.rgb(239, 236, 255))
        }, LinearLayout.LayoutParams(-1, dp(22)))
        header.addView(titleBox, LinearLayout.LayoutParams(0, dp(54), 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(10) })

        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(35), dp(10), dp(35))
        }
        loading.addView(ProgressBar(this).apply { isIndeterminate = true },
            LinearLayout.LayoutParams(dp(54), dp(54)).apply { gravity = Gravity.CENTER })
        loading.addView(TextView(this).apply {
            text = "טוען אפליקציות..."
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(42)))
        content.addView(loading, LinearLayout.LayoutParams(-1, 0, 1f))

        content.post {
            Thread {
                val apps = getLauncherApps()
                runOnUiThread {
                    if (selectedNav != 1 || isFinishing) return@runOnUiThread

                    content.removeAllViews()
                    content.setPadding(dp(14), dp(6), dp(14), dp(12))
                    val finalHeader = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        background = rounded(Color.argb(150, 255, 255, 255), 22)
                    }
                    finalHeader.addView(TextView(this).apply {
                        text = "📱"
                        textSize = 28f
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(46), dp(50)))
                    finalHeader.addView(TextView(this).apply {
                        text = "האפליקציות שלי"
                        textSize = 22f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER_VERTICAL
                    }, LinearLayout.LayoutParams(0, dp(50), 1f))
                    finalHeader.addView(TextView(this).apply {
                        text = apps.size.toString() + " אפליקציות"
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(92), dp(50)))
                    content.addView(finalHeader, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(10) })

                    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    val blockedPrefs = getSharedPreferences("app_control", MODE_PRIVATE)

                    apps.forEach { app ->
                        val pkg = app.activityInfo.packageName
                        val label = runCatching { app.activityInfo.loadLabel(packageManager).toString() }.getOrDefault(pkg)
                        val icon = runCatching { app.activityInfo.loadIcon(packageManager) }.getOrNull()
                        val locked = blockedPrefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                            background = rounded(if (locked) Color.rgb(226,255,245) else Color.argb(245,255,255,255), 20)
                        }
                        if (icon != null) row.addView(android.widget.ImageView(this).apply {
                            setImageDrawable(icon)
                            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                            leftMargin = dp(6); rightMargin = dp(8)
                        })
                        row.addView(TextView(this).apply {
                            text = label
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.rgb(63,45,115))
                            gravity = Gravity.CENTER_VERTICAL
                        }, LinearLayout.LayoutParams(0, dp(62), 1f))
                        val appSwitch = android.widget.Switch(this).apply {
                            isChecked = locked
                            scaleX = 1.08f
                            scaleY = 1.08f
                            contentDescription = "נעילת " + label
                            setOnCheckedChangeListener { _, checked ->
                                val set = blockedPrefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                                if (checked) set.add(pkg) else set.remove(pkg)
                                blockedPrefs.edit().putStringSet("blocked_apps", set).apply()
                                row.background = rounded(if (checked) Color.rgb(226,255,245) else Color.argb(245,255,255,255), 20)
                            }
                        }
                        row.addView(appSwitch, LinearLayout.LayoutParams(dp(62), dp(58)))
                        row.setOnClickListener { appSwitch.performClick() }
                        list.addView(row, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
                    }

                    if (apps.isEmpty()) list.addView(TextView(this).apply {
                        text = "לא נמצאו אפליקציות עם סמל במסך הבית."
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setPadding(dp(10), dp(35), dp(10), dp(35))
                    }, LinearLayout.LayoutParams(-1, dp(90)))

                    content.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
                }
            }.start()
        }
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
        content.setPadding(dp(14),dp(6),dp(14),dp(12))
        addCardTitle("⚙️ הגדרות")
        val cards=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        cards.addView(settingsSection("🔐","אבטחה","סוג קוד / טביעת אצבע"){setPin()})
        val iconVisible = isLauncherIconVisible()
        cards.addView(settingsSection("🙈","סמל האפליקציה", if (iconVisible) "לחץ כדי להסתיר" else "לחץ כדי להחזיר") { toggleLauncherIcon() })
        cards.addView(settingsSection("↻","עדכון","התקן את הגרסה האחרונה"){AppUpdater.downloadAndInstall(this)})
        cards.addView(settingsSection("🌐","הגנת גלישה","הגדרות VPN"){startActivity(Intent(Settings.ACTION_VPN_SETTINGS))})
        cards.addView(settingsSection("⏸","כיבוי ההגנה","כיבוי מוגן בקוד"){
            if(BlockerVpnService.isProtectionActive) requestStopProtection() else showMessage("ההגנה כבר כבויה.")
        })
        content.addView(cards)
        addDeviceManagementControls()
        addButton("🔐 בחירת הסיסמה", Color.rgb(88, 231, 226)) { setPin() }
    }

    private fun settingsSection(icon:String,title:String,subtitle:String,action:()->Unit):LinearLayout{
        return LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(7),dp(12),dp(7));background=rounded(Color.argb(220,255,255,255),22)
            setOnClickListener{action()}
            addView(TextView(this@MainActivity).apply{text=icon;textSize=24f;gravity=Gravity.CENTER},
                LinearLayout.LayoutParams(dp(48),dp(58)))
            addView(LinearLayout(this@MainActivity).apply{
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(72,50,130))})
                addView(TextView(this@MainActivity).apply{text=subtitle;textSize=12f;setTextColor(Color.rgb(120,105,160))})
            },LinearLayout.LayoutParams(0,dp(58),1f))
            addView(TextView(this@MainActivity).apply{text="›";textSize=25f;setTextColor(Color.rgb(125,96,226));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(30),dp(58)))
        }.also{ it.layoutParams=LinearLayout.LayoutParams(-1,dp(74)).apply{bottomMargin=dp(8)} }
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
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "הרשאה זו מאפשרת למגן + להשתלב במצב ניהול המכשיר. חסימת הסרה מתוך הגדרות תעבוד רק לאחר שהמכשיר הוגדר כ-Device Owner/Profile Owner.")
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
            AlertDialog.Builder(this)
                .setTitle("הפעלת הגנה")
                .setMessage("Android צריך אישור חד־פעמי לחיבור ההגנה. לאחר האישור תחזור אוטומטית ל־מגן +.")
                .setPositiveButton("המשך", null)
                .setNegativeButton("ביטול", null)
                .create().also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            dialog.dismiss()
                            startActivityForResult(intent, VPN_REQUEST)
                        }
                    }
                }.show()
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
        val modes = arrayOf("4 ספרות בלבד", "מספרים ומילים — באורך חופשי", "סיסמת פס החלקה")
        val keys = arrayOf("PIN4", "PASSWORD", "PATTERN")
        AlertDialog.Builder(this)
            .setTitle("בחירת הסיסמה")
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
            hint = if (oldHash == null) "אין סיסמה קודמת" else "סיסמה נוכחית"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        if (oldHash != null) box.addView(oldInput, LinearLayout.LayoutParams(-1, dp(56)))

        if (mode == "PATTERN") {
            box.addView(TextView(this).apply {
                text = "צייר סיסמה חדשה על 9 העיגולים"
                textSize = 15f
                setTextColor(Color.rgb(72,50,130))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(4))
            }, LinearLayout.LayoutParams(-1, dp(42)))
            val pattern = PatternLockView(this)
            box.addView(pattern, LinearLayout.LayoutParams(-1, dp(250)))
            var chosen: List<Int>? = null
            pattern.onPatternComplete = { value ->
                chosen = value
            }

            AlertDialog.Builder(this)
                .setTitle("בחירת סיסמת פס החלקה")
                .setView(box)
                .setPositiveButton("שמירה", null)
                .setNegativeButton("ביטול", null)
                .create().also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if (oldHash != null && hash(oldInput.text.toString()) != oldHash) {
                                oldInput.error = "סיסמה נוכחית שגויה"
                                return@setOnClickListener
                            }
                            val patternValue = chosen
                            if (patternValue == null || patternValue.size < 4) {
                                showMessage("יש לצייר לפחות 4 עיגולים.")
                                return@setOnClickListener
                            }
                            prefs.edit()
                                .putString("pin_hash", hash("PATTERN:" + patternValue.joinToString(",")))
                                .putString("auth_mode", "PATTERN")
                                .apply()
                            unlocked = true
                            dialog.dismiss()
                            showMessage("סיסמת ההחלקה נשמרה.")
                        }
                    }
                }.show()
            return
        }

        val newInput = EditText(this).apply {
            hint = when (mode) {
                "PIN4" -> "סיסמה חדשה — בדיוק 4 ספרות"
                else -> "סיסמה חדשה — מספרים ומילים"
            }
            inputType = if (mode == "PIN4")
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
        }
        box.addView(newInput, LinearLayout.LayoutParams(-1, dp(56)))

        AlertDialog.Builder(this)
            .setTitle("בחירת הסיסמה")
            .setView(box)
            .setPositiveButton("שמירה", null)
            .setNegativeButton("ביטול", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (oldHash != null && hash(oldInput.text.toString()) != oldHash) {
                            oldInput.error = "סיסמה נוכחית שגויה"
                            return@setOnClickListener
                        }
                        val value = newInput.text.toString()
                        val valid = if (mode == "PIN4") value.length == 4 && value.all { it.isDigit() } else value.length >= 4
                        if (!valid) {
                            newInput.error = "הסיסמה לא תקינה"
                            return@setOnClickListener
                        }
                        prefs.edit().putString("pin_hash", hash(value)).putString("auth_mode", mode).apply()
                        unlocked = true
                        dialog.dismiss()
                        showMessage("הסיסמה נשמרה.")
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

    private fun isLauncherIconVisible(): Boolean {
        val visible = ComponentName(this, "com.ari.blocker.LauncherAlias")
        val state = packageManager.getComponentEnabledSetting(visible)
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun toggleLauncherIcon() {
        val visible = ComponentName(this, "com.ari.blocker.LauncherAlias")
        val hidden = ComponentName(this, "com.ari.blocker.HiddenLauncherAlias")
        if (isLauncherIconVisible()) {
            packageManager.setComponentEnabledSetting(visible, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            packageManager.setComponentEnabledSetting(hidden, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            showMessage("הסמל הוסתר.")
        } else {
            packageManager.setComponentEnabledSetting(hidden, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            packageManager.setComponentEnabledSetting(visible, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            showMessage("הסמל הוחזר.")
        }
        showSettings()
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