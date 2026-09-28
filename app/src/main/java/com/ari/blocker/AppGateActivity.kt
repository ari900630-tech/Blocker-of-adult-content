package com.ari.blocker

import android.app.Activity
import android.app.AlertDialog
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

class AppGateActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var packageNameBlocked: String
    private lateinit var codeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageNameBlocked = intent.getStringExtra("blocked_package") ?: run { finish(); return }
        if (!intent.getBooleanExtra("allow_authentication", true) &&
            AppBlockAccessibilityService.PROTECTED_BROWSER_PACKAGES.contains(packageNameBlocked)) {
            showBrowserOnlyMessage()
            return
        }
        showGate()
    }

    override fun onBackPressed() {}

    private fun showBrowserOnlyMessage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(246, 248, 252))
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply { text="🛡️"; textSize=54f; gravity=Gravity.CENTER })
        root.addView(TextView(this).apply {
            text="הדפדפן נעול"; textSize=25f; setTextColor(Color.rgb(16,42,67)); gravity=Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text="אפשר להשתמש בדפדפן רק דרך חיפוש מוגן בתוך מגן +."
            textSize=16f; setTextColor(Color.rgb(80,100,120)); gravity=Gravity.CENTER
            setPadding(0,dp(10),0,dp(18))
        })
        root.addView(Button(this).apply {
            text="חזרה למגן +"; isAllCaps=false
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(it)
                }
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
    }

    private fun showGate() {
        val root = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
            setPadding(dp(24),dp(20),dp(24),dp(20))
            setBackgroundColor(Color.rgb(8,67,151))
            layoutDirection=LinearLayout.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply{text="🛡️🔒";textSize=50f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,dp(72)))
        root.addView(TextView(this).apply{
            text="מסך נעילה";textSize=24f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(-1,dp(40)))
        root.addView(TextView(this).apply{
            text="הזן את סיסמת הפתיחה";textSize=15f;setTextColor(Color.rgb(241,238,255));gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(-1,dp(34)))
        val mode = prefs.getString("auth_mode","PIN4")
        if (mode == "BIOMETRIC") {
            authenticateBiometric()
            return
        }
        if(mode=="PIN4") {
            val input=EditText(this).apply{
                codeInput=this;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                textSize=25f;gravity=Gravity.CENTER;setSingleLine(true)
                setTextColor(Color.WHITE);setHintTextColor(Color.WHITE);hint="—  —  —  —"
                setBackgroundColor(Color.TRANSPARENT)
            }
            root.addView(input,LinearLayout.LayoutParams(-1,dp(50)))
            arrayOf(arrayOf("1","2","3"),arrayOf("4","5","6"),arrayOf("7","8","9"),arrayOf("","0","⌫")).forEach{ values->
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
                values.forEach{key->
                    row.addView(TextView(this).apply{
                        text=key;textSize=24f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
                        background=android.graphics.drawable.GradientDrawable().apply{setColor(if(key.isEmpty())Color.TRANSPARENT else Color.argb(38,255,255,255));cornerRadius=dp(26).toFloat()}
                        setOnClickListener{
                            when(key){
                                "⌫"->if(input.text.isNotEmpty())input.text.delete(input.text.length-1,input.text.length)
                                ""->{}
                                else->if(input.text.length<4){input.append(key);if(input.text.length==4)verifyCode()}
                            }
                        }
                    },LinearLayout.LayoutParams(dp(64),dp(56)).apply{leftMargin=dp(5);rightMargin=dp(5);topMargin=dp(4);bottomMargin=dp(4)})
                }
                root.addView(row)
            }
        } else if (mode=="PATTERN") {
            root.addView(TextView(this).apply{
                text="צייר את סיסמת ההחלקה"
                textSize=15f
                setTextColor(Color.WHITE)
                gravity=Gravity.CENTER
            },LinearLayout.LayoutParams(-1,dp(34)))
            val pattern=PatternLockView(this)
            pattern.onPatternComplete={value->
                if(hash("PATTERN:"+value.joinToString(","))==prefs.getString("pin_hash",null)){
                    allowAndClose()
                } else {
                    pattern.clearPattern()
                    val message=TextView(this).apply{
                        text="סיסמה שגויה — נסה שוב"
                        textSize=14f
                        setTextColor(Color.WHITE)
                        gravity=Gravity.CENTER
                    }
                    root.addView(message,LinearLayout.LayoutParams(-1,dp(32)))
                }
            }
            root.addView(pattern,LinearLayout.LayoutParams(-1,dp(250)))
        } else {
            addCodeInput(root)
        }
        setContentView(root)
        if(::codeInput.isInitialized) codeInput.requestFocus()
    }

    private fun addCodeInput(root: LinearLayout) {
        codeInput=EditText(this).apply {
            hint=if(prefs.getString("auth_mode","PIN4")=="PIN4") "4 ספרות" else "קוד"
            inputType=if(prefs.getString("auth_mode","PIN4")=="PIN4")
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize=20f; gravity=Gravity.CENTER; imeOptions=EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setOnEditorActionListener { _,actionId,event ->
                if(actionId==EditorInfo.IME_ACTION_DONE || event?.keyCode==KeyEvent.KEYCODE_ENTER){ verifyCode(); true } else false
            }
        }
        root.addView(codeInput,LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(12)})
        root.addView(Button(this).apply{ text="אישור"; isAllCaps=false; setOnClickListener{verifyCode()} },
            LinearLayout.LayoutParams(-1,dp(56)))
    }

    private fun verifyCode() {
        val value=codeInput.text.toString()
        val mode=prefs.getString("auth_mode","PIN4")
        val validFormat=if(mode=="PIN4") value.length==4 && value.all{it.isDigit()} else value.length>=4
        if(!validFormat || hash(value)!=prefs.getString("pin_hash",null)){
            codeInput.error="קוד שגוי"; codeInput.selectAll(); return
        }
        allowAndClose()
    }

    private fun authenticateBiometric() {
        if(Build.VERSION.SDK_INT<28) return
        val executor=mainExecutor
        val prompt=BiometricPrompt.Builder(this)
            .setTitle("מגן +").setSubtitle("אימות כדי לפתוח את האפליקציה")
            .setDescription("אימות ביומטרי מאפשר כניסה לאפליקציה המוגנת.")
            .setNegativeButton("שימוש בקוד",executor){_,_->}.build()
        val cancel=CancellationSignal()
        prompt.authenticate(cancel,executor,object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult?){ allowAndClose() }
            override fun onAuthenticationFailed(){ if(::codeInput.isInitialized) codeInput.error="הטביעה לא זוהתה. נסה שוב או השתמש בקוד." }
            override fun onAuthenticationError(errorCode:Int,errString:CharSequence?){
                if(errorCode!=10 && errorCode!=13){
                    val msg=if(errString.isNullOrBlank()) "אימות ביומטרי לא הצליח." else "אימות ביומטרי לא הצליח: $errString"
                    AlertDialog.Builder(this@AppGateActivity).setMessage(msg).setPositiveButton("אישור",null).show()
                }
            }
        })
    }

    private fun allowAndClose() {
        AppBlockAccessibilityServiceHolder.service?.allowCurrentPackage(packageNameBlocked)
        packageManager.getLaunchIntentForPackage(packageNameBlocked)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(launch)
        }
        finish()
    }

    private fun hash(value:String):String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}

    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}