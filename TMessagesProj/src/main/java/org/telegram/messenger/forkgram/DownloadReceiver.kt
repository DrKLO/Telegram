package org.telegram.messenger.forkgram

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (AppUpdater.downloadId == id) {
                installApk(context, id)
                AppUpdater.downloadId = 0L
            }
        } else if (intent.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) {
            val viewDownloadIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            viewDownloadIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(viewDownloadIntent)
        }
    }

    private fun installApk(context: Context, downloadApkId: Long) {
        val dManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val install = Intent(Intent.ACTION_VIEW)
        val downloadFileUri = dManager.getUriForDownloadedFile(downloadApkId)
        if (downloadFileUri != null) {
            Log.d("DownloadManager", downloadFileUri.toString())
            install.setDataAndType(downloadFileUri, "application/vnd.android.package-archive")
            if ((Build.VERSION.SDK_INT >= 24)) {
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (install.resolveActivity(context.packageManager) != null) {
                context.startActivity(install)
            } else {
                Log.e("installApk","Automatic installation failed, please install manually.")
            }
        } else {
            Log.e("DownloadManager", "Download error")
        }
    }
}
