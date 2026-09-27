package com.ari.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockAccessibilityService : AccessibilityService() {
    private var unlockedPackage: String? = null
    private var temporaryAllowedPackage: String? = null
    private var temporaryAllowedUntil = 0L
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

        val isProtectedBrowser = PROTECTED_BROWSER_PACKAGES.contains(pkg)
        val blockedByUser = getSharedPreferences("app_control", MODE_PRIVATE)
            .getStringSet("blocked_apps", emptySet())
            ?.contains(pkg) == true

        if (!isProtectedBrowser && !blockedByUser) {
            unlockedPackage = null
            return
        }

        if (temporaryAllowedPackage == pkg && System.currentTimeMillis() < temporaryAllowedUntil) {
            unlockedPackage = pkg
            temporaryAllowedPackage = null
            temporaryAllowedUntil = 0L
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
        temporaryAllowedPackage = null
        temporaryAllowedUntil = 0L
    }

    fun allowPackageFromProtectedApp(pkg: String, durationMs: Long = 30_000L) {
        temporaryAllowedPackage = pkg
        temporaryAllowedUntil = System.currentTimeMillis() + durationMs
        unlockedPackage = pkg
    }

    override fun onInterrupt() {}

    companion object {
        val PROTECTED_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.chromium.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser"
        )
    }
}

object AppBlockAccessibilityServiceHolder {
    @Volatile var service: AppBlockAccessibilityService? = null
}