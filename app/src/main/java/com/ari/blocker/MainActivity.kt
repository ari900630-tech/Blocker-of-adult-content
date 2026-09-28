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
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
    private lateinit var bottomNav: LinearLayout
    private var selectedNav = 0
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        buildShell()
        showEntryScreen()
        requestNotificationPermissionIfNeeded()
    }

    private fun buildShell() {
        status = TextView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = purpleGradient()
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(6), dp(18), dp(12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = rounded(Color.argb(245, 255, 255, 255), 20)
        }
        val navItems = listOf(
            Triple("⌂\nראשי", "", 0),
            Triple("▦\nאפליקציות", "", 1),
            Triple("⚙\nהגדרות", "", 2),
            Triple("קוד\nסיסמה", "", 3)
        )
        navItems.forEach { (labelText, unused, index) ->
            bottomNav.addView(TextView(this).apply {
                navButtons.add(this)
                text = labelText
                textSize = 11f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.rgb(58, 37, 104))
                contentDescription = labelText.replace("\n", " ")
                background = navButtonBackground()
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
                            3 -> showPasswordSelection()
                        }
                    }
                }
            }, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                leftMargin = dp(3); rightMargin = dp(3)
            })
        }
        root.addView(bottomNav, LinearLayout.LayoutParams(-1, dp(58)))
        setContentView(root)
        refreshNavSelection()
    }

    private fun showNav() {
        bottomNav.visibility = View.VISIBLE
    }

    private fun refreshNavSelection() {
        navButtons.forEachIndexed { index, button ->
            button.isSelected = index == selectedNav
            button.background = navButtonBackground()
        }
    }

    private fun showSetup() {
        showNav()
        unlocked = true
        content.removeAllViews()
        content.setPadding(dp(14), dp(10), dp(14), dp(14))

        addCardTitle("הגדרת מגן +")
        addButton("🔐 הגדרת סיסמה", Color.rgb(125, 96, 226)) { showPasswordSelection() }
        addButton("🛡️ הפעלת ההגנה", Color.rgb(125, 96, 226)) { requestVpnPermission() }
        addButton("📱 בחירת אפליקציות", Color.rgb(125, 96, 226)) { showAppControl() }
    }

    private fun showEntryScreen() {
        bottomNav.visibility = View.GONE
        unlocked = false
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)

        val entry = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(26), dp(24), dp(26))
            background = rounded(Color.rgb(91, 62, 160), 32)
            scaleX = 0.72f
            scaleY = 0.72f
            alpha = 0f
        }
        entry.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_blocker_shield)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(-1, dp(120)))

        entry.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            val animator = android.animation.ValueAnimator.ofInt(0, 100)
            animator.duration = 900L
            animator.addUpdateListener { progress = it.animatedValue as Int }
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isFinishing) {
                        if (prefs.getString("pin_hash", null) != null) {
                            showLockScreen()
                        } else {
                            showSetup()
                        }
                    }
                }
            })
            animator.start()
        }, LinearLayout.LayoutParams(dp(190), dp(6)).apply { topMargin = dp(16) })
        content.addView(entry, LinearLayout.LayoutParams(dp(250), dp(220)).apply { gravity = Gravity.CENTER })

        entry.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).start()
    }

    private fun showLockScreen() {
        showNav()
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
        showNav()
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

        val active = BlockerVpnService.isProtectionActive || prefs.getBoolean("protection_enabled", false)
        hero.addView(addCuteProtectionSwitch(active), LinearLayout.LayoutParams(-1, dp(82)).apply {
            topMargin = dp(14)
            bottomMargin = dp(10)
        })

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
            scaleX = 1f
            scaleY = 1f
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
        row.addView(protectionSwitch, LinearLayout.LayoutParams(dp(72), dp(58)).apply { leftMargin = dp(2); rightMargin = dp(2) })
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
        openProtectedSearch()
    }

    private fun showAppControl() {
        showNav()
        content.removeAllViews()
        content.setPadding(dp(10), dp(2), dp(10), dp(8))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(Color.WHITE, 20)
        }
        header.addView(TextView(this).apply {
            text = "📱"
            textSize = 24f
            setTextColor(Color.rgb(72, 50, 130))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(38), dp(48)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBox.addView(TextView(this).apply {
            text = "האפליקציות שלי"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(72, 50, 130))
        }, LinearLayout.LayoutParams(-1, dp(27)))
        titleBox.addView(TextView(this).apply {
            text = "טוען..."
            textSize = 12f
            setTextColor(Color.rgb(120, 105, 160))
        }, LinearLayout.LayoutParams(-1, dp(20)))
        header.addView(titleBox, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(6) })

        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        loading.addView(ProgressBar(this).apply { isIndeterminate = true },
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { gravity = Gravity.CENTER })
        loading.addView(TextView(this).apply {
            text = "טוען אפליקציות..."
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(34)))
        content.addView(loading, LinearLayout.LayoutParams(-1, 0, 1f))

        content.post {
            Thread {
                val apps = getLauncherApps()
                runOnUiThread {
                    if (selectedNav != 1 || isFinishing) return@runOnUiThread

                    content.removeAllViews()
                    content.setPadding(dp(10), dp(4), dp(10), dp(8))

                    val finalHeader = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        background = rounded(Color.WHITE, 20)
                    }
                    finalHeader.addView(TextView(this).apply {
                        text = "📱"
                        textSize = 24f
                        setTextColor(Color.rgb(72, 50, 130))
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(38), dp(46)))
                    finalHeader.addView(TextView(this).apply {
                        text = "האפליקציות שלי"
                        textSize = 19f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.rgb(72, 50, 130))
                        gravity = Gravity.CENTER_VERTICAL
                    }, LinearLayout.LayoutParams(0, dp(46), 1f))
                    finalHeader.addView(TextView(this).apply {
                        text = apps.size.toString() + " אפליקציות"
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.rgb(100, 82, 145))
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(82), dp(46)))
                    content.addView(finalHeader, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(6) })

                    val list = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    val blockedPrefs = getSharedPreferences("app_control", MODE_PRIVATE)

                    apps.forEach { appInfo ->
                        val pkg = appInfo.activityInfo.packageName
                        val label = runCatching { appInfo.activityInfo.loadLabel(packageManager).toString() }.getOrDefault(pkg)
                        val icon = runCatching { appInfo.activityInfo.loadIcon(packageManager) }.getOrNull()
                        val locked = blockedPrefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
                            setPadding(dp(10), dp(7), dp(10), dp(7))
                            background = navButtonBackground()
                        }

                        val appIcon = ImageView(this).apply {
                            setImageDrawable(icon ?: getDrawable(android.R.drawable.sym_def_app_icon))
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            contentDescription = label
                        }

                        val appSwitch = android.widget.Switch(this).apply {
                            isChecked = locked
                            scaleX = 0.82f
                            scaleY = 0.82f
                            contentDescription = "נעילת $label"
                            setOnCheckedChangeListener { _, checked ->
                                val set = blockedPrefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                                if (checked) set.add(pkg) else set.remove(pkg)
                                blockedPrefs.edit().putStringSet("blocked_apps", set).apply()
                            }
                        }

                        // RTL: icon first, app name on the right, switch on the left.
                        row.addView(appIcon, LinearLayout.LayoutParams(dp(48), dp(52)).apply {
                            marginStart = dp(8)
                            marginEnd = dp(8)
                        })
                        row.addView(TextView(this).apply {
                            text = label
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.rgb(45, 35, 70))
                            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }, LinearLayout.LayoutParams(0, dp(52), 1f))
                        row.addView(appSwitch, LinearLayout.LayoutParams(dp(64), dp(52)))

                        // The entire app row is clickable. One tap toggles the switch.
                        row.setOnClickListener {
                            appSwitch.isChecked = !appSwitch.isChecked
                        }

                        list.addView(row, LinearLayout.LayoutParams(-1, dp(66)).apply {
                            bottomMargin = dp(7)
                        })
                    }

                    if (apps.isEmpty()) list.addView(TextView(this).apply {
                        text = "לא נמצאו אפליקציות עם סמל במסך הבית."
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setPadding(dp(8), dp(25), dp(8), dp(25))
                    }, LinearLayout.LayoutParams(-1, dp(80)))

                    // Use the screen's main ScrollView; avoid a nested ScrollView.
                    content.addView(list, LinearLayout.LayoutParams(-1, -2))
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

    private fun showPasswordSelection() {
        showNav()
        content.removeAllViews()
        content.setPadding(dp(12), dp(2), dp(12), dp(8))
        addCardTitle("🔐 בחירת הסיסמה")

        val modes = listOf(
            Triple("🔢", "4 ספרות", "קוד של 4 ספרות"),
            Triple("🔤", "מספרים ומילים", "סיסמה באורך חופשי"),
            Triple("🔵", "פס החלקה", "תבנית עם 9 עיגולים"),
            Triple("👆", "טביעת אצבע", "כניסה באמצעות ביומטריה")
        )
        modes.forEachIndexed { index, (icon, title, subtitle) ->
            val mode = arrayOf("PIN4", "PASSWORD", "PATTERN", "BIOMETRIC")[index]
            val card = settingsSection(icon, title, subtitle) { askForNewCode(mode) }
            content.addView(card)
        }
    }

    private fun showSettings() {
        showNav()
        content.removeAllViews()
        content.setPadding(dp(14),dp(6),dp(14),dp(12))
        addCardTitle("⚙️ הגדרות")
        val cards=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        cards.addView(settingsSection("🔐","בחירת הסיסמה","4 ספרות, מילים, פס החלקה או טביעת אצבע"){showPasswordSelection()})
        val requireCode = prefs.getBoolean("require_code_for_blocked_apps", true)
        cards.addView(settingsSection("🔒","קוד לאפליקציות חסומות", if (requireCode) "פעיל — נדרש קוד בכניסה" else "כבוי") {
            prefs.edit().putBoolean("require_code_for_blocked_apps", !requireCode).apply()
            showSettings()
        })
        val iconVisible = isLauncherIconVisible()
        cards.addView(settingsSection("🙈","סמל האפליקציה", if (iconVisible) "לחץ כדי להסתיר" else "לחץ כדי להחזיר") { toggleLauncherIcon() })
        cards.addView(settingsSection("↻","עדכון","התקן את הגרסה האחרונה"){AppUpdater.downloadAndInstall(this)})
        cards.addView(settingsSection("🌐","הגנת גלישה","הגדרות VPN"){startActivity(Intent(Settings.ACTION_VPN_SETTINGS))})
        val accessibilityGranted = isAccessibilityServiceEnabled()
        cards.addView(settingsSection("◉","שירות חסימה",
            if (accessibilityGranted) "מאושר — החסימה יכולה להגן על אפליקציות" else "נדרש אישור") { openAccessibilitySettings() })
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        cards.addView(settingsSection("▣","מעל אפליקציות אחרות",
            if (overlayGranted) "מאושר" else "נדרש אישור") { requestOverlayPermission() })
        addDeviceManagementControls()
        cards.addView(settingsSection("⏸","כיבוי ההגנה","כיבוי ההגנה"){
            if(BlockerVpnService.isProtectionActive) requestStopProtection() else showMessage("ההגנה כבר כבויה.")
        })
        content.addView(cards)
        // Device-admin/removal controls stay out of the settings UI.
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
        val isAdmin = dpm.isAdminActive(BlockerDeviceAdminReceiver.component(this))

        if (isOwner) {
            addButton("✓ הגנת הסרה פעילה", Color.rgb(46,125,50), selected = true) {
                BlockerDeviceAdminReceiver.enforceUninstallBlocked(this)
                showMessage("הגנת ההסרה פעילה.")
            }
        } else {
            val subtitle = if (isAdmin) {
                "מנהל המכשיר מאושר. חסימת הסרה מלאה דורשת ניהול מכשיר."
            } else {
                "נדרש אישור כדי להפעיל את הגנת ההסרה."
            }
            content.addView(settingsSection("▣", "הגנת הסרת האפליקציה", subtitle) {
                if (!isAdmin) {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, BlockerDeviceAdminReceiver.component(this@MainActivity))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "אישור זה מאפשר למגן + להשתמש בהרשאות ניהול המכשיר. הגנת הסרה מלאה זמינה כאשר האפליקציה מוגדרת כבעלת המכשיר או הפרופיל.")
                    }
                    startActivity(intent)
                } else {
                    showMessage("הרשאת מנהל המכשיר כבר מאושרת. במכשיר רגיל Android עדיין עשוי לאפשר הסרה לאחר ביטול הרשאת הניהול.")
                }
            })
        }
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = event.x
                swipeDownY = event.y
                swipeTracking = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeTracking && event.actionMasked == MotionEvent.ACTION_UP) {
                    val dx = event.x - swipeDownX
                    val dy = event.y - swipeDownY
                    if (kotlin.math.abs(dx) >= dp(90) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.35f) {
                        val pattern = findPatternLockView(content)
                        val insidePattern = pattern != null && isPointInsideView(pattern, swipeDownX, swipeDownY)
                        if (!insidePattern) {
                            if (dx < 0) navigateBySwipe(1) else navigateBySwipe(-1)
                            swipeTracking = false
                            return true
                        }
                    }
                }
                swipeTracking = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun navigateBySwipe(direction: Int) {
        if (!unlocked && prefs.getString("pin_hash", null) != null) {
            showLockScreen()
            return
        }
        val next = (selectedNav + direction).coerceIn(0, 3)
        if (next == selectedNav) return
        selectedNav = next
        refreshNavSelection()
        when (selectedNav) {
            0 -> showHome()
            1 -> showAppControl()
            2 -> showSettings()
            3 -> showPasswordSelection()
        }
    }

    private fun findPatternLockView(view: View): PatternLockView? {
        if (view is PatternLockView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findPatternLockView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun isPointInsideView(view: View, x: Float, y: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val rootLocation = IntArray(2)
        window.decorView.getLocationOnScreen(rootLocation)
        val left = location[0] - rootLocation[0]
        val top = location[1] - rootLocation[1]
        return x >= left && x <= left + view.width && y >= top && y <= top + view.height
    }

    private fun requestVpnPermission() {
        if (!isAccessibilityServiceEnabled()) {
            showMessage("כדי שהחסימה תעבוד גם כשעוברים לאפליקציה אחרת, יש לאשר את שירות הנגישות של מגן +.")
            openAccessibilitySettings()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showMessage("כדי שמסך הקוד יוכל להופיע מעל האפליקציה החסומה, יש לאשר את ההרשאה „מעל אפליקציות אחרות”.")
            requestOverlayPermission()
            return
        }

        val intent = VpnService.prepare(this)
        if (intent == null) {
            startProtection()
            showHome()
            return
        }

        // Launch Android's VPN approval screen immediately.
        // No intermediate in-app confirmation screen.
        startActivityForResult(intent, VPN_REQUEST)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(this, AppBlockAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showMessage("הרשאת „מעל אפליקציות אחרות” אינה נדרשת בגרסת Android הזו.")
            return
        }
        if (Settings.canDrawOverlays(this)) {
            showMessage("הרשאת „מעל אפליקציות אחרות” כבר מאושרת.")
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
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
        stopProtection()
    }

    private fun setPin() {
        showPasswordSelection()
    }

    private fun askForNewCode(mode: String) {
        content.removeAllViews()
        content.setPadding(dp(10), dp(2), dp(10), dp(8))

        val oldHash = prefs.getString("pin_hash", null)

        val screenColor = when (mode) {
            "PIN4" -> Color.rgb(8, 67, 151)
            "PATTERN" -> Color.rgb(5, 34, 48)
            else -> Color.rgb(105, 76, 220)
        }

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = rounded(screenColor, 30)
        }

        if (mode != "PATTERN") {
            screen.addView(TextView(this).apply {
                text = "🛡️🔒"
                textSize = 48f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(68)))

            screen.addView(TextView(this).apply {
                text = when (mode) {
                    "PIN4" -> "הגדרת סיסמת פתיחה"
                    else -> "בחר סיסמת פתיחה"
                }
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(42)))
        }

        if (oldHash != null) {
            val current = EditText(this).apply {
                hint = "הסיסמה הנוכחית"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.argb(190,255,255,255))
                gravity = Gravity.CENTER
            }
            screen.addView(current, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
            screen.tag = current
        }

        when (mode) {
            "PIN4" -> {
                val input = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.WHITE)
                    hint = "—  —  —  —"
                    setBackgroundColor(Color.TRANSPARENT)
                }
                screen.addView(input, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(6) })

                val keys = arrayOf(arrayOf("1","2","3"), arrayOf("4","5","6"), arrayOf("7","8","9"), arrayOf("","0","⌫"))
                keys.forEach { values ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    values.forEach { key ->
                        row.addView(TextView(this).apply {
                            text = key
                            textSize = 24f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            background = rounded(
                                if (key.isEmpty()) Color.TRANSPARENT else Color.argb(35,255,255,255), 24
                            )
                            setOnClickListener {
                                when (key) {
                                    "⌫" -> if (input.text.isNotEmpty()) input.text.delete(input.text.length - 1, input.text.length)
                                    "" -> {}
                                    else -> if (input.text.length < 4) input.append(key)
                                }
                            }
                        }, LinearLayout.LayoutParams(dp(64), dp(50)).apply {
                            leftMargin = dp(5); rightMargin = dp(5); topMargin = dp(2); bottomMargin = dp(2)
                        })
                    }
                    screen.addView(row)
                }

                addPasswordActionButtons(screen, oldHash, mode, input)
            }

            "PATTERN" -> {
                // Match the supplied reference: a clean dark pattern-lock panel with
                // only the 3x3 pattern and the two bottom actions.
                screen.setPadding(0, dp(8), 0, dp(12))
                screen.background = rounded(Color.rgb(5, 34, 48), 28)

                val pattern = PatternLockView(this).apply {
                    gapRatio = 0.35f
                    dotRadiusRatio = 0.063f
                }
                var chosen: List<Int>? = null
                pattern.onPatternComplete = { value -> chosen = value }
                screen.addView(pattern, LinearLayout.LayoutParams(-1, dp(280)))

                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(dp(42), 0, dp(42), 0)
                }

                fun actionButton(label: String, action: () -> Unit) =
                    TextView(this@MainActivity).apply {
                        text = label
                        textSize = 20f
                        setTextColor(Color.rgb(35, 35, 35))
                        gravity = Gravity.CENTER
                        background = rounded(Color.rgb(211, 211, 211), 3)
                        setOnClickListener { action() }
                    }

                // RTL layout: Continue is on the left and Cancel on the right,
                // exactly as in the supplied reference image.
                actions.addView(actionButton("Continue") {
                    val current = screen.tag as? EditText
                    if (oldHash != null && (current == null || hash(current.text.toString()) != oldHash)) {
                        current?.error = "סיסמה נוכחית שגויה"
                    } else {
                        val value = chosen
                        if (value == null || value.size < 4) {
                            showMessage("צייר לפחות 4 עיגולים.")
                        } else {
                            prefs.edit()
                                .putString("pin_hash", hash("PATTERN:" + value.joinToString(",")))
                                .putString("auth_mode", "PATTERN")
                                .apply()
                            unlocked = true
                            showHome()
                        }
                    }
                }, LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                    rightMargin = dp(5)
                })

                actions.addView(actionButton("Cancel") {
                    showPasswordSelection()
                }, LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                    leftMargin = dp(5)
                })

                screen.addView(actions, LinearLayout.LayoutParams(-1, dp(60)))
            }

            "BIOMETRIC" -> {
                screen.addView(TextView(this).apply {
                    text = "אימות באמצעות טביעת אצבע או ביומטריה של המכשיר"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, dp(52)))
                addPasswordActionButtons(screen, oldHash, mode, EditText(this))
            }

            else -> {
                screen.addView(TextView(this).apply {
                    text = "מספרים ומילים — באורך חופשי"
                    textSize = 14f
                    setTextColor(Color.rgb(239,236,255))
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, dp(30)))

                val input = EditText(this).apply {
                    hint = "סיסמה חדשה"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.argb(190,255,255,255))
                }
                screen.addView(input, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(12) })
                addPasswordActionButtons(screen, oldHash, mode, input)
            }
        }

        content.addView(screen, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addPasswordActionButtons(
        screen: LinearLayout,
        oldHash: String?,
        mode: String,
        input: EditText
    ) {
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(Button(this).apply {
            text = "ביטול"
            isAllCaps = false
            setOnClickListener { showPasswordSelection() }
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { rightMargin = dp(8) })
        actions.addView(Button(this).apply {
            text = "שמירה"
            isAllCaps = false
            setOnClickListener {
                val current = screen.tag as? EditText
                if (oldHash != null && (current == null || hash(current.text.toString()) != oldHash)) {
                    current?.error = "סיסמה נוכחית שגויה"
                    return@setOnClickListener
                }
                if (mode == "BIOMETRIC") {
                    authenticateAndSaveBiometric(oldHash, screen)
                    return@setOnClickListener
                }
                val value = input.text.toString()
                val valid = if (mode == "PIN4") value.length == 4 && value.all { it.isDigit() } else value.length >= 4
                if (!valid) {
                    input.error = if (mode == "PIN4") "יש להזין בדיוק 4 ספרות" else "יש להזין לפחות 4 תווים"
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString("pin_hash", hash(value))
                    .putString("auth_mode", mode)
                    .apply()
                unlocked = true
                showHome()
            }
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(8) })
        screen.addView(actions, LinearLayout.LayoutParams(-1, dp(66)))
    }

    private fun authenticateAndSaveBiometric(oldHash: String?, screen: LinearLayout) {
        if (Build.VERSION.SDK_INT < 28) {
            showMessage("טביעת אצבע אינה זמינה בגרסת Android זו.")
            return
        }
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle("מגן +")
            .setSubtitle("הגדרת טביעת אצבע")
            .setDescription("אשר את הזהות שלך כדי להפעיל כניסה ביומטרית.")
            .setNegativeButton("ביטול", executor) { _, _ -> }
            .build()
        prompt.authenticate(android.os.CancellationSignal(), executor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                    runOnUiThread {
                        prefs.edit()
                            .putString("pin_hash", hash("BIOMETRIC:" + packageName))
                            .putString("auth_mode", "BIOMETRIC")
                            .apply()
                        unlocked = true
                        showHome()
                    }
                }
                override fun onAuthenticationFailed() {
                    runOnUiThread { showMessage("האימות הביומטרי לא הצליח.") }
                }
            })
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

    private fun navButtonBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.rgb(225, 216, 255), 18))
        addState(intArrayOf(android.R.attr.state_selected), rounded(Color.rgb(237, 232, 255), 18))
        addState(intArrayOf(), rounded(Color.argb(245, 255, 255, 255), 18))
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
        if (unlocked) {
            when (selectedNav) {
                0 -> showHome()
                2 -> showSettings()
            }
        }
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