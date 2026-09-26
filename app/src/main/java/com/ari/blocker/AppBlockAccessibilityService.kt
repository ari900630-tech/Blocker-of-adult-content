package com.ari.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockAccessibilityService : AccessibilityService() {
    private var unlockedPackage: String? = null
    private var gateLaunchAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppBlockAccessibilityServiceHolder.service = this
    }

    override fun onDestroy() {
        AppBlockAccessibilityServiceHolder.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        val blocked = getSharedPreferences("app_control", MODE_PRIVATE)
            .getStringSet("blocked_apps", emptySet())
            ?.contains(pkg) == true

        if (!blocked) {
            unlockedPackage = null
            return
        }

        if (unlockedPackage == pkg) return

        val now = System.currentTimeMillis()
        if (now - gateLaunchAt < 1200L) return
        gateLaunchAt = now

        startActivity(
            Intent(this, AppGateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_package", pkg)
            }
        )
    }

    fun allowCurrentPackage(pkg: String) {
        unlockedPackage = pkg
    }

    override fun onInterrupt() {}
}
