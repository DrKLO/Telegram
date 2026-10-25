package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks the latest GitHub release of Kite and offers to install it.
 * Only CI builds (BuildConfig.KITE_BUILD > 0) check for updates. Android always
 * asks the user to confirm installing an APK, so the update cannot be silent.
 */
public final class KiteUpdater {

    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/Garricho/kite-android/releases/latest";
    private static final String APK_NAME = "Kite.apk";
    private static final String TAG_PREFIX = "build-";
    private static final long CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L;

    private static final String PREF_LAST_CHECK = "kite_update_last_check";
    private static final String PREF_SKIPPED = "kite_update_skipped_build";

    private static boolean checking;
    private static long downloadId = -1;

    private KiteUpdater() {
    }

    public static void checkForUpdate(BaseFragment fragment) {
        if (BuildConfig.KITE_BUILD <= 0 || checking) {
            return;
        }
        final SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        final long now = System.currentTimeMillis();
        if (Math.abs(now - prefs.getLong(PREF_LAST_CHECK, 0)) < CHECK_INTERVAL_MS) {
            return;
        }
        checking = true;
        Utilities.globalQueue.postRunnable(() -> {
            Release release = null;
            try {
                release = fetchLatestRelease();
            } catch (Exception e) {
                FileLog.e(e);
            }
            final Release result = release;
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                if (result == null) {
                    return;
                }
                prefs.edit().putLong(PREF_LAST_CHECK, now).apply();
                if (result.build > BuildConfig.KITE_BUILD && result.build != prefs.getInt(PREF_SKIPPED, 0)) {
                    showUpdateDialog(fragment, result);
                }
            });
        });
    }

    /** Settings button: check right away and start the download if a newer build exists. */
    public static void checkManually(BaseFragment fragment) {
        if (checking || fragment.getParentActivity() == null) {
            return;
        }
        checking = true;
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        Utilities.globalQueue.postRunnable(() -> {
            Release release = null;
            try {
                release = fetchLatestRelease();
            } catch (Exception e) {
                FileLog.e(e);
            }
            final Release result = release;
            AndroidUtilities.runOnUIThread(() -> {
                checking = false;
                try {
                    progress.dismiss();
                } catch (Exception ignore) {
                }
                if (fragment.getParentActivity() == null) {
                    return;
                }
                // The settings tab is embedded in MainTabsActivity, where fragment bulletins are not
                // visible, so the result is shown as a plain dialog.
                if (result == null) {
                    showResult(fragment, getString(R.string.KiteUpdateCheckFailed));
                } else if (result.build > BuildConfig.KITE_BUILD) {
                    MessagesController.getGlobalMainSettings().edit().remove(PREF_SKIPPED).apply();
                    download(fragment, result);
                } else {
                    showResult(fragment, getString(R.string.KiteUpdateLatest));
                }
            });
        });
    }

    private static void showResult(BaseFragment fragment, String text) {
        try {
            new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle(getString(R.string.KiteUpdateTitle))
                .setMessage(text)
                .setPositiveButton(getString(R.string.OK), null)
                .show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static final class Release {
        int build;
        String apkUrl;
        String notes;
    }

    private static Release fetchLatestRelease() throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (connection.getResponseCode() != 200) {
                return null;
            }
            final JSONObject json;
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                json = new JSONObject(out.toString("UTF-8"));
            }
            final String tag = json.optString("tag_name", "");
            if (!tag.startsWith(TAG_PREFIX)) {
                return null;
            }
            final Release release = new Release();
            release.build = Utilities.parseInt(tag.substring(TAG_PREFIX.length()));
            final JSONArray assets = json.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                final JSONObject asset = assets.getJSONObject(i);
                if (APK_NAME.equals(asset.optString("name"))) {
                    release.apkUrl = asset.optString("browser_download_url", null);
                }
            }
            return release.build > 0 && release.apkUrl != null ? release : null;
        } finally {
            connection.disconnect();
        }
    }

    private static void showUpdateDialog(BaseFragment fragment, Release release) {
        final BaseFragment host = KiteChannelPrompt.chatListHost(fragment);
        if (host == null || host.getVisibleDialog() != null) {
            // Try again on the next check instead of waiting the full interval.
            MessagesController.getGlobalMainSettings().edit().remove(PREF_LAST_CHECK).apply();
            return;
        }
        host.showDialog(new AlertDialog.Builder(host.getParentActivity())
            .setTitle(getString(R.string.KiteUpdateTitle))
            .setMessage(formatString(R.string.KiteUpdateText, release.build))
            .setPositiveButton(getString(R.string.KiteUpdateInstall), (dialog, which) -> download(host, release))
            .setNegativeButton(getString(R.string.KiteUpdateLater), (dialog, which) ->
                MessagesController.getGlobalMainSettings().edit().putInt(PREF_SKIPPED, release.build).apply())
            .create());
    }

    private static void download(BaseFragment fragment, Release release) {
        final Context context = ApplicationLoader.applicationContext;
        final DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apkUrl))
                .setTitle(getString(R.string.KiteUpdateTitle))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "Kite-" + release.build + ".apk");
            registerInstallReceiver(context);
            downloadId = manager.enqueue(request);
            BulletinFactory.global().createSimpleBulletin(R.raw.download_arrow, getString(R.string.KiteUpdateDownloading)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static boolean receiverRegistered;

    private static void registerInstallReceiver(Context context) {
        if (receiverRegistered) {
            return;
        }
        receiverRegistered = true;
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                final long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == -1 || id != downloadId) {
                    return;
                }
                final DownloadManager manager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                final Uri uri = manager == null ? null : manager.getUriForDownloadedFile(id);
                if (uri == null) {
                    return;
                }
                try {
                    final Intent install = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(install);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        };
        final IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }
}
