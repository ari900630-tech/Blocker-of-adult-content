package com.ari.blocker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class AppControlActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("app_control", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(246, 248, 252))
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "🔐  נעילת אפליקציות"
            textSize = 26f
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "בחר אפליקציות שייפתחו רק אחרי סיסמה. אפשר להדליק או לכבות נעילה לכל אפליקציה בנפרד."
            textSize = 15f
            setTextColor(Color.rgb(80, 100, 120))
            setPadding(0, 0, 0, dp(12))
        })

        val accessibilityOn = isAccessibilityEnabled()
        val access = TextView(this).apply {
            text = if (accessibilityOn) "✓ בקרת אפליקציות פעילה" else "⚠ בקרת אפליקציות כבויה"
            textSize = 16f
            setTextColor(if (accessibilityOn) Color.rgb(46,125,50) else Color.rgb(183,28,28))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 18)
        }
        root.addView(access, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })

        root.addView(TextView(this).apply {
            text = if (accessibilityOn) "אין צורך להפעיל שוב. אפשר לבחור עכשיו אילו אפליקציות לנעול." else "לחץ כאן כדי להפעיל את בקרת האפליקציות במערכת."
            textSize = 14f
            setTextColor(Color.rgb(80, 100, 120))
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        if (!accessibilityOn) {
            root.addView(android.widget.Button(this).apply {
                text = "▶ הפעל בקרת אפליקציות"
                isAllCaps = false
                setOnClickListener { openAccessibilitySettings() }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) })
        }

        root.addView(TextView(this).apply {
            text = "האפליקציות שלי"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16,42,67))
            setPadding(0, dp(12), 0, dp(10))
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }

        val apps = try {
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DEFAULT_ONLY
            ).mapNotNull { it.activityInfo?.applicationInfo }
                .filter { it.packageName != packageName }
                .distinctBy { it.packageName }
                .sortedBy {
                    runCatching { packageManager.getApplicationLabel(it).toString() }
                        .getOrDefault(it.packageName)
                }
        } catch (_: Exception) {
            emptyList()
        }

        if (apps.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "לא הצלחתי לטעון את רשימת האפליקציות. נסה לפתוח שוב."
                textSize = 16f
                setTextColor(Color.rgb(80, 100, 120))
                setPadding(dp(12), dp(20), dp(12), dp(20))
            })
        }

        apps.forEach { app ->
            val pkg = app.packageName
            val label = runCatching { packageManager.getApplicationLabel(app).toString() }
                .getOrDefault(pkg)

            val initiallyLocked = prefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(6), dp(8), dp(6))
                background = rounded(if (initiallyLocked) Color.rgb(232,244,236) else Color.WHITE, 18)
            }

            val title = TextView(this).apply {
                text = if (initiallyLocked) "✓  $label" else label
                textSize = 16f
                typeface = if (initiallyLocked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (initiallyLocked) Color.rgb(27,94,32) else Color.rgb(30,45,60))
                layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
            }
            row.addView(title, LinearLayout.LayoutParams(0, dp(54), 1f))

            val sw = Switch(this).apply {
                text = if (initiallyLocked) "✓ נעולה" else "נעילה"
                isChecked = initiallyLocked
                setOnCheckedChangeListener { button, checked ->
                    if (checked) {
                        updateLockState(pkg, true, button, row, title, label)
                    } else {
                        val pinHash = getSharedPreferences("settings", MODE_PRIVATE).getString("pin_hash", null)
                        if (pinHash == null) {
                            button.setOnCheckedChangeListener(null)
                            button.isChecked = true
                            button.setOnCheckedChangeListener { b, c ->
                                if (c) updateLockState(pkg, true, b, row, title, label)
                                else askToDisableLock(pkg, label, b, row, title, pinHash)
                            }
                            showMessage("כדי לבטל נעילה צריך קודם להגדיר קוד גישה.")
                            return@setOnCheckedChangeListener
                        }
                        button.setOnCheckedChangeListener(null)
                        button.isChecked = true
                        button.setOnCheckedChangeListener { b, c ->
                            if (c) updateLockState(pkg, true, b, row, title, label)
                            else askToDisableLock(pkg, label, b, row, title, pinHash)
                        }
                        askToDisableLock(pkg, label, button, row, title, pinHash)
                    }
                }
            }
            row.addView(sw, LinearLayout.LayoutParams(dp(90), dp(54)))
            list.addView(row, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(8) })
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun updateLockState(
        pkg: String,
        locked: Boolean,
        button: android.widget.CompoundButton,
        row: LinearLayout,
        title: TextView,
        label: String
    ) {
        val current = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (locked) current.add(pkg) else current.remove(pkg)
        prefs.edit().putStringSet("blocked_apps", current).apply()
        button.text = if (locked) "✓ נעולה" else "נעילה"
        button.isChecked = locked
        title.text = if (locked) "✓  $label" else label
        title.typeface = if (locked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        title.setTextColor(if (locked) Color.rgb(27,94,32) else Color.rgb(30,45,60))
        row.background = rounded(if (locked) Color.rgb(232,244,236) else Color.WHITE, 18)
    }

    private fun askToDisableLock(
        pkg: String,
        label: String,
        button: android.widget.CompoundButton,
        row: LinearLayout,
        title: TextView,
        pinHash: String?
    ) {
        if (pinHash == null) {
            showMessage("כדי לבטל נעילה צריך קודם להגדיר קוד גישה.")
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = "קוד גישה"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("ביטול נעילה")
            .setMessage("כדי להסיר את הנעילה מ־$label יש לאשר עם קוד הגישה.")
            .setView(input)
            .setPositiveButton("ביטול נעילה", null)
            .setNegativeButton("השאר נעול", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (hash(input.text.toString()) != pinHash) {
                            input.error = "קוד שגוי"
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        updateLockState(pkg, false, button, row, title, label)
                    }
                }
            }.show()
    }

    private fun hash(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun showMessage(message: String) {
        android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("אישור", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val manager = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                ?: return false
            if (!manager.isEnabled) return false
            manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info ->
                info.resolveInfo?.serviceInfo?.packageName == packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
