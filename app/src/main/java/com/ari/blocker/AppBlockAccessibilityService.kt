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
        val controlPrefs = getSharedPreferences("app_control", MODE_PRIVATE)
        val blockedByUser = controlPrefs
            .getStringSet("blocked_apps", emptySet())
            ?.contains(pkg) == true
        val now = System.currentTimeMillis()
        val persistedUnlockUntil = controlPrefs.getLong("unlock_until_$pkg", 0L)

        if (!isProtectedBrowser && !blockedByUser) {
            unlockedPackage = null
            return
        }

        // Protected browsers may only be opened through the protected search
        // inside this app. A direct launch is never authorized by the gate.
        val browserAllowedUntil = controlPrefs.getLong("browser_allowed_until", 0L)
        if (isProtectedBrowser && now >= browserAllowedUntil && temporaryAllowedPackage != pkg) {
            unlockedPackage = null
            launchGate(pkg, allowAuthentication = false)
            return
        }

        if (isProtectedBrowser && (temporaryAllowedPackage == pkg || now < browserAllowedUntil)) {
            temporaryAllowedPackage = pkg
            temporaryAllowedUntil = maxOf(temporaryAllowedUntil, browserAllowedUntil)
            return
        }

        if (temporaryAllowedPackage == pkg && now < temporaryAllowedUntil) {
            unlockedPackage = pkg
            temporaryAllowedPackage = null
            temporaryAllowedUntil = 0L
            return
        }

        if (persistedUnlockUntil > now) {
            unlockedPackage = pkg
            return
        }

        if (unlockedPackage == pkg) return

        if (now - gateLaunchAt < 1200L) return
        gateLaunchAt = now

        launchGate(pkg, allowAuthentication = true)
    }

    private fun launchGate(pkg: String, allowAuthentication: Boolean) {
        startActivity(
            Intent(this, AppGateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_package", pkg)
                putExtra("allow_authentication", allowAuthentication)
            }
        )
    }

    fun allowCurrentPackage(pkg: String) {
        val until = System.currentTimeMillis() + 5 * 60_000L
        getSharedPreferences("app_control", MODE_PRIVATE)
            .edit()
            .putLong("unlock_until_$pkg", until)
            .apply()
        unlockedPackage = pkg
        temporaryAllowedPackage = null
        temporaryAllowedUntil = 0L
    }

    fun allowPackageFromProtectedApp(pkg: String, durationMs: Long = 30_000L) {
        val until = System.currentTimeMillis() + durationMs
        getSharedPreferences("app_control", MODE_PRIVATE)
            .edit()
            .putLong("browser_allowed_until", until)
            .apply()
        temporaryAllowedPackage = pkg
        temporaryAllowedUntil = until
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