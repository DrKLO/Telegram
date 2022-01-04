package ua.itaysonlab.catogram

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import ua.itaysonlab.catogram.translate.impl.GoogleTranslateImpl.translateText
import ua.itaysonlab.extras.CatogramExtras


object OTA : CoroutineScope by MainScope() {

    var needDownload = false

    lateinit var changelog: String
    lateinit var parseddString: String

    lateinit var version: String

    lateinit var handler: CoroutineExceptionHandler

    lateinit var broadcastReceiver: BroadcastReceiver

    private fun checkBS(callback: (Boolean) -> Unit) {
        launch(handler) {
            try {
                val request: Request =
                        Request.Builder().url("https://api.github.com/repos/catogram/catogram/releases/latest").build()
                withContext(Dispatchers.IO) {
                    val response = OkHttpClient().newCall(request).execute()
                    parseddString = response.body!!.string()
                    val parsedString = JSONObject(parseddString)
                    if (parsedString.getString("name") != CatogramExtras.CG_VERSION) {
                        version = parsedString.getString("name")
                        changelog = parsedString.getString("body")
                        needDownload = true
                    } else needDownload = false
                }
                callback.invoke(needDownload)
            } catch (e: java.lang.Exception) {
                throw e
            }
        }
    }

    @JvmStatic
    fun download(context: Context, b: Boolean) {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when (intent.extras!!.getString("action_name")) {
                    "action_download" -> {
                        downloadApk(context!!)
                    }

                    "action_changelog" -> {
                        showAlert(context!!)
                    }
                }
            }
        }

        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
        }

        handler = CoroutineExceptionHandler { _, _ -> }

        checkBS { needDownload ->
            if (needDownload && b) {
                showAlert(context)
            } else if (needDownload && !b) {
                translateText(changelog, false) { txt ->
                    context.registerReceiver(broadcastReceiver, IntentFilter("OTA_NOTIF"))
                    if (Build.VERSION.SDK_INT >= 26) {
                        val channel = NotificationChannel("channel01", "name",
                            NotificationManager.IMPORTANCE_HIGH) // for heads up notifications

                        channel.description = "description"

                        val notificationManager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

                        notificationManager!!.createNotificationChannel(channel)
                    }

                    val intentDownload = Intent(context, NotificationActionService::class.java)
                        .setAction("action_download")
                    val pendingIntentDownload = PendingIntent.getBroadcast(
                        context, 0,
                        intentDownload, PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val intentChangelog = Intent(context, NotificationActionService::class.java)
                        .setAction("action_changelog")

                    val pendingIntentChangelog = PendingIntent.getBroadcast(
                        context, 0,
                        intentChangelog, PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val notification: Notification = NotificationCompat.Builder(context, "channel01")
                        .setSmallIcon(R.drawable.cg_notification)
                        .setContentTitle(LocaleController.getString("CG_Found", R.string.CG_Found))
                        .setContentText(version)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .addAction(R.drawable.download_outline_28, LocaleController.getString("CG_Download", R.string.CG_Download), pendingIntentDownload)
                        .addAction(R.drawable.download_outline_28, LocaleController.getString("CG_Changelog", R.string.CG_Changelog), pendingIntentChangelog)
                        .setStyle(NotificationCompat.BigTextStyle()
                            .bigText(txt))
                        .build()

                    val notificationManager = NotificationManagerCompat.from(context)

                    notificationManager.notify(1337, notification)
                }

            } else if (b) Toast.makeText(context, LocaleController.getString("CG_Not_Found", R.string.CG_Not_Found), Toast.LENGTH_SHORT).show()
        }
    }

    fun showAlert(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.cancel(1337)
        } catch (e: Exception) {
        }
        val builder = org.telegram.ui.ActionBar.AlertDialog.Builder(context)
        builder.setTitle(LocaleController.getString("CG_Found", R.string.CG_Found) + " • " + version)
                .setMessage(changelog)
                .setPositiveButton(LocaleController.getString("CG_Download", R.string.CG_Download)) { _, _ ->
                    downloadApk(context)
                }
        builder.show()
    }

    fun downloadApk(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(1337)
        val request = DownloadManager.Request(Uri.parse("https://github.com/catogram/catogram/releases/latest/download/app.apk"))

        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        request.setTitle("Catogram v$version")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ota.apk")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}