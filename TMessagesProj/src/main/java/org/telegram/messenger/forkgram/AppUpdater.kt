package org.telegram.messenger.forkgram

import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.os.AsyncTask
import android.widget.Toast

import org.json.JSONObject

import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog

import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {
    private const val kCheckInterval = 30 * 60 * 1000 // 30 minutes.
    private const val title = "The latest Forkgram version"
    private const val desc = ""

    private var downloadBroadcastReceiver: DownloadReceiver? = null
    private var lastTimestampOfCheck = 0L
    var downloadId = 0L

    @JvmStatic
    fun clearCachedInstallers(context: Context) {
        val externalDir: File = context.getExternalFilesDir(null) ?: return;
        val downloadDir = "${externalDir.absolutePath}/${android.os.Environment.DIRECTORY_DOWNLOADS}";

        val f = File(downloadDir);
        if (!f.exists() || !f.isDirectory) {
            return;
        }
        val apks = f.listFiles { f -> (f.name.startsWith(title) && f.name.endsWith(".apk")) } ?: return;
        for (apk in apks) {
            android.util.Log.i("Fork Client", "File was removed. Path: " + apk.absolutePath);
            apk.delete();
        }
    }

    @JvmStatic
    fun checkNewVersion(
            parentActivity: Activity,
            context: Context,
            callback: (AlertDialog.Builder?) -> Int,
            manual: Boolean = false) {

        if (!manual && System.currentTimeMillis() - lastTimestampOfCheck < kCheckInterval) {
            return
        }
        if (downloadId != 0L) {
            return
        }
        val currentVersion = BuildVars.BUILD_VERSION_STRING
        val userRepo = BuildVars.USER_REPO
        if (userRepo.isEmpty()) {
            return
        }

        HttpTask {
            if (it == null) {
                if (manual) {
                    Toast.makeText(
                        context,
                        "Can't check, please try later",
                        Toast.LENGTH_SHORT).show()
                }
                println("Connection error.")
                return@HttpTask
            }
            lastTimestampOfCheck = System.currentTimeMillis();

            val root = JSONObject(it)
            val tag = root.getString("tag_name")

            if (tag <= currentVersion) {
                if (manual) {
                    Toast.makeText(context,"No updates", Toast.LENGTH_SHORT).show()
                }
                return@HttpTask
            }

            // New version!
            val body = root.getString("body")
            val assets = root.getJSONArray("assets");
            if (assets.length() == 0) {
                return@HttpTask;
            }
            val url = assets
                .getJSONObject(0)
                .getString("browser_download_url")

            val builder = AlertDialog.Builder(parentActivity)
            builder.setTitle("New version $tag")
            builder.setMessage("Release notes:\n$body")
            builder.setMessageTextViewClickable(false)
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
            builder.setPositiveButton("Install") { _, _ ->
                downloadBroadcastReceiver = DownloadReceiver()
                val intentFilter = IntentFilter()
                intentFilter.addAction("android.intent.action.DOWNLOAD_COMPLETE")
                intentFilter.addAction("android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED")
                parentActivity.registerReceiver(downloadBroadcastReceiver, intentFilter)

                val dm = DownloadManagerUtil(context)
                if (dm.checkDownloadManagerEnable()) {
                    if (downloadId != 0L) {
                        dm.clearCurrentTask(downloadId)
                    }
                    downloadId = dm.download(url, title, desc)
                } else {
                    Toast.makeText(context,"Please open Download Manager", Toast.LENGTH_SHORT).show()
                }
            }
            
            callback(builder)
        }.execute("GET", "https://api.github.com/repos/$userRepo/releases/latest")
    }

    class HttpTask(callback: (String?) -> Unit) : AsyncTask<String, Unit, String>()  {

        val TIMEOUT = 10 * 1000
        var callback = callback

        override fun doInBackground(vararg params: String): String? {
            val url = URL(params[1])
            val httpClient = url.openConnection() as HttpURLConnection
            httpClient.readTimeout = TIMEOUT
            httpClient.connectTimeout = TIMEOUT
            httpClient.requestMethod = params[0]

            try {
                if (httpClient.responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = BufferedInputStream(httpClient.inputStream)
                    return readStream(inputStream = stream)
                } else {
                    println("ERROR ${httpClient.responseCode}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                httpClient.disconnect()
            }

            return null
        }

        private fun readStream(inputStream: BufferedInputStream): String {
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            bufferedReader.forEachLine { stringBuilder.append(it) }
            return stringBuilder.toString()
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            callback(result)
        }
    }
}
