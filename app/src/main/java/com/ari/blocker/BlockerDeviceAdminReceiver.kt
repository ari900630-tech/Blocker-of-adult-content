package com.ari.blocker

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: android.content.Intent) {
        super.onEnabled(context, intent)
        enforceProtection(context)
    }

    override fun onDisableRequested(
        context: Context,
        intent: android.content.Intent
    ): CharSequence {
        return "אזהרה: ביטול מנהל המכשיר יבטל את הגנת ההסרה של מגן +. כל עוד ההרשאה פעילה, לא ניתן להסיר את האפליקציה בהסרה רגילה."
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, BlockerDeviceAdminReceiver::class.java)

        fun enforceProtection(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            val isOwner = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
            if (isOwner) {
                // Keep this app protected from uninstall. Android Settings can then
                // show app controls without allowing the app itself to be removed.
                dpm.setUninstallBlocked(admin, context.packageName, true)
            }
        }

        fun releaseProtectionForUninstall(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            val isOwner = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
            if (isOwner) {
                dpm.setUninstallBlocked(admin, context.packageName, false)
            }
        }

        fun isDeviceOrProfileOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        }
    }
}
