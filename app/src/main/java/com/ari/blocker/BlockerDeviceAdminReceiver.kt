package com.ari.blocker

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: android.content.Intent) {
        super.onEnabled(context, intent)
        enforceProtection(context)
    }

    override fun onDisableRequested(
        context: Context,
        intent: android.content.Intent
    ): CharSequence {
        return "אזהרה: ביטול מנהל המכשיר מבטל את הגנת ההסרה והגנת השליטה באפליקציה."
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, BlockerDeviceAdminReceiver::class.java)

        fun enforceProtection(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
            val isProfileOwner = dpm.isProfileOwnerApp(context.packageName)

            if (isDeviceOwner || isProfileOwner) {
                // Device/Profile Owner mode is the system-level protection path.
                // These restrictions prevent ordinary Settings app-control actions
                // such as uninstalling and controlling apps. The Accessibility
                // service remains the password gate for the Settings UI.
                try {
                    dpm.setUninstallBlocked(admin, context.packageName, true)
                } catch (_: SecurityException) {
                    // Some OEM builds restrict this call even for a profile owner.
                }

                try {
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                } catch (_: SecurityException) {
                }

                try {
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                } catch (_: SecurityException) {
                }
            }
        }

        fun releaseProtectionForUninstall(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            val isOwner = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
            if (isOwner) {
                try {
                    dpm.setUninstallBlocked(admin, context.packageName, false)
                } catch (_: SecurityException) {
                }
                try {
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                } catch (_: SecurityException) {
                }
                try {
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                } catch (_: SecurityException) {
                }
            }
        }

        fun isDeviceOrProfileOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        }
    }
}
