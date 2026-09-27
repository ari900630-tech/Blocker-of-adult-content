package com.ari.blocker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
import android.widget.TextView

class AppControlActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("app_control", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(246, 248, 252))
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "📱  כל האפליקציות בטלפון"
            textSize = 26f
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "כאן מופיעות האפליקציות המותקנות במכשיר. לחץ על אפליקציה כדי לנעול או לפתוח אותה. אפליקציה נעולה תבקש קוד גישה בעת הפתיחה."
            textSize = 15f
            setTextColor(Color.rgb(80, 100, 120))
            setPadding(0, 0, 0, dp(12))
        })

        val accessibilityOn = isAccessibilityEnabled()
        root.addView(TextView(this).apply {
            text = if (accessibilityOn) "✓ בקרת אפליקציות פעילה" else "⚠ בקרת אפליקציות כבויה"
            textSize = 16f
            setTextColor(if (accessibilityOn) Color.rgb(46,125,50) else Color.rgb(183,28,28))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 18)
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })

        if (!accessibilityOn) {
            root.addView(android.widget.Button(this).apply {
                text = "▶ הפעל בקרת אפליקציות"
                isAllCaps = false
                setOnClickListener { openAccessibilitySettings() }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) })
        }

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }

        val apps = getLauncherApps()

        list.addView(TextView(this).apply {
            text = "${apps.size} אפליקציות נמצאו — לחץ על שורה כדי לשנות נעילה"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16,42,67))
            setPadding(0, dp(8), 0, dp(8))
        })

        apps.forEach { app ->
            val pkg = app.activityInfo.packageName
            if (pkg == packageName) return@forEach
            val label = runCatching { app.activityInfo.loadLabel(packageManager).toString() }.getOrDefault(pkg)
            val icon = runCatching { app.activityInfo.loadIcon(packageManager) }.getOrNull()
            val initiallyLocked = prefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                background = rounded(if (initiallyLocked) Color.rgb(232,244,236) else Color.WHITE, 18)
            }
            if (icon != null) row.addView(ImageView(this).apply {
                setImageDrawable(icon)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { leftMargin = dp(8); rightMargin = dp(8) })

            val title = TextView(this).apply {
                text = if (initiallyLocked) "✓  $label" else label
                textSize = 16f
                typeface = if (initiallyLocked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (initiallyLocked) Color.rgb(27,94,32) else Color.rgb(30,45,60))
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(title, LinearLayout.LayoutParams(0, dp(58), 1f))
            val state = TextView(this).apply {
                text = if (initiallyLocked) "✓ נעולה" else "פתוחה"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (initiallyLocked) Color.rgb(27,94,32) else Color.rgb(80,100,120))
            }
            row.addView(state, LinearLayout.LayoutParams(dp(72), dp(58)))
            row.setOnClickListener {
                val currentLocked = prefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true
                if (currentLocked) askToDisableLock(pkg, label, row, title, state)
                else updateLockState(pkg, true, row, title, state, label)
            }
            list.addView(row, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun getLauncherApps(): List<android.content.pm.ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return try {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .filter { it.activityInfo?.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { runCatching { it.activityInfo.loadLabel(packageManager).toString() }.getOrDefault(it.activityInfo.packageName) }
        } catch (_: Exception) { emptyList() }
    }

    private fun updateLockState(pkg: String, locked: Boolean, row: LinearLayout, title: TextView, state: TextView, label: String) {
        val current = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (locked) current.add(pkg) else current.remove(pkg)
        prefs.edit().putStringSet("blocked_apps", current).apply()
        title.text = if (locked) "✓  $label" else label
        title.typeface = if (locked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        title.setTextColor(if (locked) Color.rgb(27,94,32) else Color.rgb(30,45,60))
        state.text = if (locked) "✓ נעולה" else "פתוחה"
        state.setTextColor(if (locked) Color.rgb(27,94,32) else Color.rgb(80,100,120))
        row.background = rounded(if (locked) Color.rgb(232,244,236) else Color.WHITE, 18)
    }

    private fun askToDisableLock(pkg: String, label: String, row: LinearLayout, title: TextView, state: TextView) {
        val pinHash = getSharedPreferences("settings", MODE_PRIVATE).getString("pin_hash", null)
        if (pinHash == null) { showMessage("כדי לפתוח אפליקציה נעולה צריך להגדיר קוד גישה."); return }
        val input = android.widget.EditText(this).apply {
            hint = "קוד גישה"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("פתיחת אפליקציה")
            .setMessage("כדי להסיר את הנעילה מ־$label יש לאשר עם קוד הגישה.")
            .setView(input)
            .setPositiveButton("פתח", null)
            .setNegativeButton("ביטול", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (hash(input.text.toString()) != pinHash) input.error = "קוד שגוי"
                        else { dialog.dismiss(); updateLockState(pkg, false, row, title, state, label) }
                    }
                }
            }.show()
    }

    private fun hash(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun showMessage(message: String) {
        android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("אישור", null).show()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            if (intent.resolveActivity(packageManager) != null) startActivity(intent) else startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val manager = getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
            if (!manager.isEnabled) return false
            manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info -> info.resolveInfo?.serviceInfo?.packageName == packageName }
        } catch (_: Exception) { false }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
