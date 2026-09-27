package com.ari.blocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest
import java.util.concurrent.Executors

class AppGateActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var packageNameBlocked: String
    private lateinit var codeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageNameBlocked = intent.getStringExtra("blocked_package") ?: run {
            finish()
            return
        }
        if (!intent.getBooleanExtra("allow_authentication", true) &&
            AppBlockAccessibilityService.PROTECTED_BROWSER_PACKAGES.contains(packageNameBlocked)) {
            showBrowserOnlyMessage()
            return
        }
        showGate()
    }

    override fun onBackPressed() {
        // Do not reveal the protected app by pressing Back.
    }

    private fun showBrowserOnlyMessage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(246, 248, 252))
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply {
            text = "🛡️"
            textSize = 54f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "הדפדפן נעול"
            textSize = 25f
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "אפשר להיכנס לדפדפן רק דרך חיפוש מוגן בתוך מגן התוכן."
            textSize = 16f
            setTextColor(Color.rgb(80, 100, 120))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        })
        root.addView(Button(this).apply {
            text = "חזרה למגן התוכן"
            isAllCaps = false
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launch)
                }
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
    }

    private fun showGate() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(246, 248, 252))
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "🛡️"
            textSize = 54f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "האפליקציה מוגנת"
            textSize = 25f
            setTextColor(Color.rgb(16, 42, 67))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "כדי להיכנס לאפליקציה הזו צריך לאשר גישה."
            textSize = 16f
            setTextColor(Color.rgb(80, 100, 120))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        })

        val mode = prefs.getString("auth_mode", "PIN4")
        if (mode == "BIOMETRIC" && Build.VERSION.SDK_INT >= 28) {
            val bio = Button(this).apply {
                text = "אימות בטביעת אצבע / ביומטריה"
                isAllCaps = false
                setOnClickListener { authenticateBiometric() }
            }
            root.addView(bio, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })
            root.addView(TextView(this).apply {
                text = "אם הביומטריה לא זמינה, אפשר לחזור לקוד."
                textSize = 13f
                gravity = Gravity.CENTER
            })
            addCodeInput(root)
        } else {
            addCodeInput(root)
        }

        setContentView(root)
        codeInput.requestFocus()
    }

    private fun addCodeInput(root: LinearLayout) {
        codeInput = EditText(this).apply {
            hint = if (prefs.getString("auth_mode", "PIN4") == "PIN4") "4 ספרות" else "קוד"
            inputType = if (prefs.getString("auth_mode", "PIN4") == "PIN4")
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 20f
            gravity = Gravity.CENTER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    verifyCode()
                    true
                } else false
            }
        }
        root.addView(codeInput, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })

        root.addView(Button(this).apply {
            text = "אישור"
            isAllCaps = false
            setOnClickListener { verifyCode() }
        }, LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun verifyCode() {
        val value = codeInput.text.toString()
        val mode = prefs.getString("auth_mode", "PIN4")
        val validFormat = if (mode == "PIN4") value.length == 4 && value.all { it.isDigit() } else value.length >= 4
        if (!validFormat || hash(value) != prefs.getString("pin_hash", null)) {
            codeInput.error = "קוד שגוי"
            codeInput.selectAll()
            return
        }
        allowAndClose()
    }

    private fun authenticateBiometric() {
        if (Build.VERSION.SDK_INT < 28) return
        val executor = Executors.newSingleThreadExecutor()
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("מגן התוכן")
            .setSubtitle("אימות כדי לפתוח את האפליקציה")
            .setDescription("אימות ביומטרי מאפשר כניסה לאפליקציה המוגנת.")
            .setNegativeButton("שימוש בקוד", executor) { _, _ -> }
            .build()

        val cancel = CancellationSignal()
        prompt.authenticate(cancel, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                runOnUiThread { allowAndClose() }
            }
        })
    }

    private fun allowAndClose() {
        val service = AppBlockAccessibilityServiceHolder.service
        service?.allowCurrentPackage(packageNameBlocked)
        finish()
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

