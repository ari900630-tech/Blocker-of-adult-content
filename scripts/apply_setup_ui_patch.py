from pathlib import Path
import re

p = Path("app/src/main/java/com/ari/blocker/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# Keep exactly one explanatory line at the top of the setup screen.
s = s.replace('        addText("אין אפשרות לדלג. יש לאשר את כל ההרשאות לפי הסדר, ורק בסוף להגדיר סיסמת פתיחה.")\n',
              '        addText("יש לאשר את כל ההרשאות כדי להפעיל את מגן +")\n')

# Remove explanatory text below the permission button and below the password step.
s = s.replace('            addText("הסיסמה תופיע רק לאחר שכל ארבע ההרשאות שלמעלה אושרו.")\n', '')
s = s.replace('            addText("לא ניתן להפעיל את החסימה או לעבור למסך הראשי לפני שמירת הסיסמה.")\n', '')
s = s.replace('        addText("זהו השלב האחרון. לאחר אישור ה‑VPN תופעל החסימה ותעבור למסך הראשי.")\n', '')

# Refresh the setup screen immediately when returning from Android permission/settings screens.
old = '''    override fun onResume() {\n        super.onResume()\n        if (hasStartedOnce && prefs.getString("pin_hash", null) != null && !unlocked && !setupFlowActive) {\n            showEntryScreen()\n        }\n        if (setupFlowActive) {\n            showSetup()\n        }\n        if (!hasStartedOnce) hasStartedOnce = true\n    }'''
new = '''    override fun onResume() {\n        super.onResume()\n        if (hasStartedOnce && prefs.getString("pin_hash", null) != null && !unlocked && !setupFlowActive) {\n            showEntryScreen()\n        }\n        if (setupFlowActive) {\n            window.decorView.post {\n                if (!isFinishing && setupFlowActive) showSetup()\n            }\n        }\n        if (!hasStartedOnce) hasStartedOnce = true\n    }'''
if old in s:
    s = s.replace(old, new)
else:
    raise SystemExit("Expected onResume block was not found")

p.write_text(s, encoding="utf-8")
