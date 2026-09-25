package com.ari.blocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object AppUpdater {
    private const val LATEST_APK_URL =
        "https://github.com/ari900630-tech/Blocker-of-adult-content/releases/latest"

    fun openUpdatePage(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("עדכון האפליקציה")
            .setMessage("נפתח עמוד העדכון הרשמי. הורד את הגרסה החדשה והתקן אותה על גבי הגרסה הקיימת.")
            .setPositiveButton("עדכון") { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_APK_URL)))
            }
            .setNegativeButton("ביטול", null)
            .show()
    }
}
