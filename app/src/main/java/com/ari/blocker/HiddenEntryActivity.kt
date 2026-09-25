package com.ari.blocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Hidden entry screen used when the launcher icon is disabled.
 * There are no visible controls. The owner knows the secret location:
 * double-tap the bottom-right corner to open the main screen.
 */
class HiddenEntryActivity : Activity() {
    private var lastSecretTap = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels
                val density = resources.displayMetrics.density
                val secretSize = (96 * density).toInt()

                val inSecretArea =
                    event.x >= width - secretSize &&
                    event.y >= height - secretSize

                if (inSecretArea) {
                    val now = System.currentTimeMillis()
                    if (now - lastSecretTap <= 900L) {
                        lastSecretTap = 0L
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        lastSecretTap = now
                        handler.postDelayed({ lastSecretTap = 0L }, 950L)
                    }
                }
                true
            }
        }

        setContentView(root)
    }
}
