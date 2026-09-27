package com.ari.blocker

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: android.content.Intent) {
        super.onEnabled(context, intent)
        enforceUninstallBlocked(context)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, BlockerDeviceAdminReceiver::class.java)

        fun enforceUninstallBlocked(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = component(context)
            if (dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)) {
                dpm.setUninstallBlocked(admin, context.packageName, true)
            }
        }
    }
}
