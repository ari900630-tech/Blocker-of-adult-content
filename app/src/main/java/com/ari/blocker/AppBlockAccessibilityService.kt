package com.ari.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
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
        val blockedByUser = controlPrefs.getStringSet("blocked_apps", emptySet())?.contains(pkg) == true
        val now = System.currentTimeMillis()
        val persistedUnlockUntil = controlPrefs.getLong("unlock_until_$pkg", 0L)

        if (!isProtectedBrowser && !blockedByUser) {
            unlockedPackage = null
            return
        }

        if (isProtectedBrowser) {
            val browserAllowedUntil = controlPrefs.getLong("browser_allowed_until", 0L)
            val temporarilyAllowed = temporaryAllowedPackage == pkg && now < temporaryAllowedUntil
            val persistedAllowed = now < browserAllowedUntil

            if (!temporarilyAllowed && !persistedAllowed) {
                unlockedPackage = null
                launchGate(pkg, allowAuthentication = false)
                return
            }

            temporaryAllowedPackage = pkg
            temporaryAllowedUntil = maxOf(temporaryAllowedUntil, browserAllowedUntil)

            if (isNonGoogleBrowserUrlVisible()) {
                controlPrefs.edit().remove("browser_allowed_until").apply()
                temporaryAllowedPackage = null
                temporaryAllowedUntil = 0L
                unlockedPackage = null
                launchGate(pkg, allowAuthentication = false)
                return
            }
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

    private fun isNonGoogleBrowserUrlVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val candidates = mutableListOf<String>()
            collectAddressBarCandidates(root, candidates)
            candidates.any { it.trim().let(::looksLikeUrl) && !isAllowedGoogleUrl(it) }
        } catch (_: Exception) {
            false
        } finally {
            root.recycle()
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        val v = value.trim().lowercase()
        return v.startsWith("http://") || v.startsWith("https://") || v.startsWith("www.")
    }

    private fun collectAddressBarCandidates(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val className = node.className?.toString()?.lowercase().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val text = node.text?.toString()?.trim().orEmpty()
        val hint = node.hintText?.toString()?.lowercase().orEmpty()

        val looksLikeAddressBar =
            className.contains("edittext") ||
            viewId.contains("url") || viewId.contains("omnibox") || viewId.contains("address") ||
            hint.contains("address") || hint.contains("כתובת")

        if (looksLikeAddressBar && text.isNotBlank()) out.add(text)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAddressBarCandidates(child, out)
                child.recycle()
            }
        }
    }

    private fun isAllowedGoogleUrl(value: String): Boolean {
        val normalized = value.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val host = normalized.substringBefore('/').substringBefore('?').substringBefore('#')
        return host == "google.com" || host.endsWith(".google.com") || host.endsWith(".google.co.il")
    }

    private fun launchGate(pkg: String, allowAuthentication: Boolean) {
        startActivity(Intent(this, AppGateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("blocked_package", pkg)
            putExtra("allow_authentication", allowAuthentication)
        })
    }

    fun allowCurrentPackage(pkg: String) {
        val until = System.currentTimeMillis() + 5 * 60_000L
        getSharedPreferences("app_control", MODE_PRIVATE).edit()
            .putLong("unlock_until_$pkg", until).apply()
        unlockedPackage = pkg
        temporaryAllowedPackage = null
        temporaryAllowedUntil = 0L
    }

    fun allowPackageFromProtectedApp(pkg: String, durationMs: Long = 30_000L) {
        val until = System.currentTimeMillis() + durationMs
        getSharedPreferences("app_control", MODE_PRIVATE).edit()
            .putLong("browser_allowed_until", until).apply()
        temporaryAllowedPackage = pkg
        temporaryAllowedUntil = until
        unlockedPackage = pkg
    }

    override fun onInterrupt() {}

    companion object {
        val PROTECTED_BROWSER_PACKAGES = setOf(
            "com.android.chrome", "org.chromium.chrome", "org.mozilla.firefox",
            "com.microsoft.emmx", "com.brave.browser", "com.opera.browser",
            "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android",
            "com.vivaldi.browser", "com.kiwibrowser.browser"
        )
    }
}

object AppBlockAccessibilityServiceHolder {
    @Volatile var service: AppBlockAccessibilityService? = null
}