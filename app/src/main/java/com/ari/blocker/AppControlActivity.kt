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
            text = if (accessibilityOn) "✓ בקרת אפליקציות פעילה" else "⚠ יש להפעיל בקרת אפליקציות"
            textSize = 16f
            setTextColor(if (accessibilityOn) Color.rgb(46,125,50) else Color.rgb(183,28,28))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        root.addView(access)

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

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(6), dp(8), dp(6))
                background = rounded(Color.WHITE, 18)
            }

            val title = TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.rgb(30, 45, 60))
                layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
            }
            row.addView(title, LinearLayout.LayoutParams(0, dp(54), 1f))

            val sw = Switch(this).apply {
                text = "נעילה"
                isChecked = prefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true
                setOnCheckedChangeListener { _, checked ->
                    val current = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (checked) current.add(pkg) else current.remove(pkg)
                    prefs.edit().putStringSet("blocked_apps", current).apply()
                }
            }
            row.addView(sw, LinearLayout.LayoutParams(dp(90), dp(54)))
            list.addView(row, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(8) })
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
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
