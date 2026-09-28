from pathlib import Path
import re

p = Path("app/src/main/java/com/ari/blocker/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# Compact setup text.
s = s.replace('        addText("אין אפשרות לדלג. יש לאשר את כל ההרשאות לפי הסדר, ורק בסוף להגדיר סיסמת פתיחה.")\n', '        addText("יש לאשר את כל ההרשאות כדי להפעיל את מגן +")\n')
s = s.replace('            addText("הסיסמה תופיע רק לאחר שכל ארבע ההרשאות שלמעלה אושרו.")\n', '')
s = s.replace('            addText("לא ניתן להפעיל את החסימה או לעבור למסך הראשי לפני שמירת הסיסמה.")\n', '')
s = s.replace('        addText("זהו השלב האחרון. לאחר אישור ה‑VPN תופעל החסימה ותעבור למסך הראשי.")\n', '')

# Make the post-code home screen compact enough for one-screen display.
s = s.replace('        content.setPadding(dp(14), dp(4), dp(14), dp(12))\n', '        content.setPadding(dp(10), dp(2), dp(10), dp(6))\n')
s = s.replace('            setPadding(dp(18), dp(20), dp(18), dp(18))\n', '            setPadding(dp(10), dp(7), dp(10), dp(7))\n')
s = s.replace('        }, LinearLayout.LayoutParams(-1, dp(78)))\n', '        }, LinearLayout.LayoutParams(-1, dp(45)))\n', 1)
s = s.replace('        }, LinearLayout.LayoutParams(-1, dp(42)))\n', '        }, LinearLayout.LayoutParams(-1, dp(32)))\n', 1)
s = s.replace('        }, LinearLayout.LayoutParams(-1, dp(30)))\n', '        }, LinearLayout.LayoutParams(-1, dp(22)))\n', 1)
s = s.replace('        hero.addView(addCuteProtectionSwitch(active), LinearLayout.LayoutParams(-1, dp(82)).apply {\n            topMargin = dp(14)\n            bottomMargin = dp(10)\n', '        hero.addView(addCuteProtectionSwitch(active), LinearLayout.LayoutParams(-1, dp(52)).apply {\n            topMargin = dp(3)\n            bottomMargin = dp(3)\n')
s = s.replace('        content.addView(hero, LinearLayout.LayoutParams(-1, dp(310)).apply {\n            bottomMargin = dp(14)\n', '        content.addView(hero, LinearLayout.LayoutParams(-1, dp(170)).apply {\n            bottomMargin = dp(7)\n')
s = s.replace('            setPadding(dp(16), dp(8), dp(10), dp(8))\n', '            setPadding(dp(10), dp(3), dp(7), dp(3))\n')
s = s.replace('        }, LinearLayout.LayoutParams(dp(46), dp(54)))\n', '        }, LinearLayout.LayoutParams(dp(38), dp(46)))\n', 1)
s = s.replace('        }, LinearLayout.LayoutParams(0, dp(54), 1f))\n', '        }, LinearLayout.LayoutParams(0, dp(46), 1f))\n', 1)
s = s.replace('        }, LinearLayout.LayoutParams(dp(34), dp(54)))\n', '        }, LinearLayout.LayoutParams(dp(28), dp(46)))\n', 1)
s = s.replace('        content.addView(search, LinearLayout.LayoutParams(-1, dp(70)).apply {\n            bottomMargin = dp(12)\n', '        content.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply {\n            bottomMargin = dp(5)\n')
s = s.replace('            setPadding(dp(16), dp(8), dp(14), dp(8))\n', '            setPadding(dp(10), dp(2), dp(8), dp(2))\n')
s = s.replace('        }, LinearLayout.LayoutParams(0, dp(64), 1f))\n', '        }, LinearLayout.LayoutParams(0, dp(44), 1f))\n', 1)
s = s.replace('        row.addView(protectionSwitch, LinearLayout.LayoutParams(dp(72), dp(58)).apply { leftMargin = dp(2); rightMargin = dp(2) })\n', '        row.addView(protectionSwitch, LinearLayout.LayoutParams(dp(62), dp(44)).apply { leftMargin = dp(2); rightMargin = dp(2) })\n')

# Return to the app immediately after Android Accessibility/permission settings.
new_on_resume = '''    override fun onResume() {
        super.onResume()
        if (setupFlowActive) {
            window.decorView.post {
                if (!isFinishing && setupFlowActive) showSetup()
            }
        }
        if (!hasStartedOnce) hasStartedOnce = true
        if (unlocked && !setupFlowActive) {
            when (selectedNav) {
                0 -> showHome()
                1 -> showAppControl()
                2 -> showSettings()
                3 -> showPasswordSelection()
            }
        }
    }
'''

if re.search(r'(?m)^    override fun onResume\(\) \{', s):
    s, _ = re.subn(
        r'(?ms)^    override fun onResume\(\) \{.*?^    \}\n\n    override fun onStop\(',
        new_on_resume + '\n    override fun onStop(',
        s,
        count=1,
    )
else:
    marker = '    override fun onStop() {'
    if marker not in s:
        raise SystemExit("Could not locate onStop method in MainActivity.kt")
    s = s.replace(marker, new_on_resume + '\n' + marker, 1)

p.write_text(s, encoding="utf-8")
