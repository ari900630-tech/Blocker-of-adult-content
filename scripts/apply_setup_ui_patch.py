from pathlib import Path
import re

p = Path("app/src/main/java/com/ari/blocker/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# Keep exactly one explanatory line at the top of the setup screen.
s = s.replace(
    '        addText("אין אפשרות לדלג. יש לאשר את כל ההרשאות לפי הסדר, ורק בסוף להגדיר סיסמת פתיחה.")\n',
    '        addText("יש לאשר את כל ההרשאות כדי להפעיל את מגן +")\n'
)

# Remove explanatory text below the permission button and below the password step.
s = s.replace('            addText("הסיסמה תופיע רק לאחר שכל ארבע ההרשאות שלמעלה אושרו.")\n', '')
s = s.replace('            addText("לא ניתן להפעיל את החסימה או לעבור למסך הראשי לפני שמירת הסיסמה.")\n', '')
s = s.replace('        addText("זהו השלב האחרון. לאחר אישור ה‑VPN תופעל החסימה ותעבור למסך הראשי.")\n', '')

# Refresh the setup screen after Android settings/permission screens return.
# Match the whole Kotlin method rather than depending on one historical onResume body.
new_on_resume = '''    override fun onResume() {
        super.onResume()
        if (hasStartedOnce && prefs.getString("pin_hash", null) != null && !unlocked && !setupFlowActive) {
            showEntryScreen()
        }
        if (setupFlowActive) {
            window.decorView.post {
                if (!isFinishing && setupFlowActive) showSetup()
            }
        }
        if (!hasStartedOnce) hasStartedOnce = true
        if (unlocked && !setupFlowActive) {
            when (selectedNav) {
                0 -> showHome()
                2 -> showSettings()
            }
        }
    }'''

pattern = r'(?ms)^    override fun onResume\(\) \{.*?^    \}\n\n    override fun onStop\('
replacement = new_on_resume + '\n\n    override fun onStop('
s, count = re.subn(pattern, replacement, s, count=1)
if count != 1:
    raise SystemExit("Could not locate onResume method in MainActivity.kt")

p.write_text(s, encoding="utf-8")
