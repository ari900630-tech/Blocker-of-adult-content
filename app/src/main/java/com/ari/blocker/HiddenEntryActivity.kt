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
 * Invisible launcher entry. The first tap opens this transparent activity;
 * a second tap anywhere (for example the exact same spot) opens the app.
 */
class HiddenEntryActivity : Activity() {
    private var lastTap = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

                val now = System.currentTimeMillis()
                if (now - lastTap <= 900L) {
                    lastTap = 0L
                    startActivity(Intent(this@HiddenEntryActivity, MainActivity::class.java))
                    finish()
                } else {
                    lastTap = now
                    handler.postDelayed({ lastTap = 0L }, 950L)
                }
                true
            }
        }

        setContentView(root)
    }
}
