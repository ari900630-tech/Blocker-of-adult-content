package com.ari.blocker

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

object AppUpdater {
    private const val APK_URL =
        "https://github.com/ari900630-tech/Blocker-of-adult-content/releases/latest/download/app-latest.apk"

    fun downloadAndInstall(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            AlertDialog.Builder(activity)
                .setTitle("נדרש אישור לעדכון")
                .setMessage("כדי שמגן התוכן יוכל להתקין עדכונים שהורדו מתוך האפליקציה, יש לאפשר התקנת אפליקציות ממקור זה.")
                .setPositiveButton("פתיחת הגדרות") { _, _ ->
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                }
                .setNegativeButton("ביטול", null)
                .show()
            return
        }

        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(APK_URL))
            .setTitle("עדכון מגן התוכן")
            .setDescription("מוריד את הגרסה החדשה…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                "blocker-update.apk"
            )

        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                try {
                    val uri = manager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        activity.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        )
                    } else {
                        showError(activity, "לא ניתן לפתוח את קובץ העדכון.")
                    }
                } catch (e: Exception) {
                    showError(activity, "התקנת העדכון נכשלה: ${e.message ?: "שגיאה לא ידועה"}")
                } finally {
                    try { activity.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        AlertDialog.Builder(activity)
            .setTitle("העדכון התחיל")
            .setMessage("הגרסה החדשה יורדת עכשיו. בסיום ייפתח מסך ההתקנה.")
            .setPositiveButton("אישור", null)
            .show()
    }

    private fun showError(activity: Activity, message: String) {
        AlertDialog.Builder(activity)
            .setTitle("עדכון")
            .setMessage(message)
            .setPositiveButton("אישור", null)
            .show()
    }
}
