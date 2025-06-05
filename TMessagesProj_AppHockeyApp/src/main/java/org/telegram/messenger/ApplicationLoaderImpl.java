package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Pair;
import android.view.ViewGroup;

import androidx.core.content.FileProvider;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.CustomProperties;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.analytics.EventProperties;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.distribute.Distribute;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.Components.UpdateButton;
import org.telegram.ui.Components.UpdateLayout;
import org.telegram.ui.IUpdateButton;
import org.telegram.ui.IUpdateLayout;

import java.io.File;
import java.util.Locale;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }


    @Override
    protected void startAppCenterInternal(Activity context) {
        if (org.telegram.messenger.BuildConfig.DEBUG) {
            return;
        }
        try {
            if (BuildVars.DEBUG_VERSION) {
                String userId = "" + UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
                if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null) {
                    final String username = UserObject.getPublicUsername(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
                    if (!TextUtils.isEmpty(username))
                        userId = "@" + username;
                }

                final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                crashlytics.setUserId(userId);
                crashlytics.setCustomKey("version", BuildVars.DEBUG_PRIVATE_VERSION ? "private" : "public");
                crashlytics.setCustomKey("model", Build.MODEL);
                crashlytics.setCustomKey("manufacturer", Build.MANUFACTURER);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    crashlytics.setCustomKey("soc_model", Build.SOC_MODEL);
                    crashlytics.setCustomKey("soc_manufacturer", Build.SOC_MANUFACTURER);
                }
                crashlytics.setCustomKey("device", Build.DEVICE);
                crashlytics.setCustomKey("product", Build.PRODUCT);
                crashlytics.setCustomKey("hardware", Build.HARDWARE);
                crashlytics.setCustomKey("user", Build.USER);
                crashlytics.setCrashlyticsCollectionEnabled(true);
            }
            if (BuildVars.DEBUG_VERSION) {
                Distribute.setEnabledForDebuggableBuild(true);
                String appHash = org.telegram.messenger.BuildConfig.APP_CENTER_HASH;
                if (TextUtils.isEmpty(appHash)) {
                    throw new RuntimeException("App Center hash is empty. add to local.properties field APP_CENTER_HASH_PRIVATE and APP_CENTER_HASH_PUBLIC");
                }
                AppCenter.start(context.getApplication(), appHash, Distribute.class, Crashes.class, Analytics.class);
                Crashes.getMinidumpDirectory().thenAccept(path -> {
                    if (path != null) {
                        Utilities.setupNativeCrashesListener(path);
                    }
                });
                CustomProperties props = new CustomProperties();
                props.set("model", Build.MODEL);
                props.set("manufacturer", Build.MANUFACTURER);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    props.set("soc_model", Build.SOC_MODEL);
                    props.set("soc_manufacturer", Build.SOC_MANUFACTURER);
                }
                props.set("device", Build.DEVICE);
                props.set("product", Build.PRODUCT);
                props.set("hardware", Build.HARDWARE);
                props.set("user", Build.USER);
                AppCenter.setCustomProperties(props);
                String userId = "uid=" + UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
                if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null) {
                    final String username = UserObject.getPublicUsername(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
                    if (!TextUtils.isEmpty(username))
                        userId += " @" + username;
                }
                AppCenter.setUserId(userId);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static long lastUpdateCheckTime;
    @Override
    protected void checkForUpdatesInternal() {
        try {
            if (BuildVars.DEBUG_VERSION) {
                if (SystemClock.elapsedRealtime() - lastUpdateCheckTime < 60 * 60 * 1000) {
                    return;
                }
                lastUpdateCheckTime = SystemClock.elapsedRealtime();
                Distribute.checkForUpdate();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    protected void appCenterLogInternal(Throwable e) {
        try {
            FirebaseCrashlytics.getInstance().recordException(e);
        } catch (Throwable recordException) {
            FileLog.e(recordException, false);
        }
        try {
            Crashes.trackError(e);
        } catch (Throwable ignore) {

        }
    }

    protected void logDualCameraInternal(boolean success, boolean vendor) {
//        try {
//            Analytics.trackEvent("dual-camera[" + (Build.MANUFACTURER + " " + Build.DEVICE).toUpperCase() + "]",
//                new EventProperties()
//                    .set("success", success)
//                    .set("vendor", vendor)
//                    .set("product", Build.PRODUCT + "")
//                    .set("model", Build.MODEL)
//            );
//        } catch (Throwable ignore) {
//
//        }
    }

    @Override
    public boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }

    @Override
    public boolean openApkInstall(Activity activity, TLRPC.Document document) {
        boolean exists = false;
        try {
            final String fileName = FileLoader.getAttachFileName(document);
            final File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
            if (exists = f.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (Build.VERSION.SDK_INT >= 24) {
                    intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", f), "application/vnd.android.package-archive");
                } else {
                    intent.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
                }
                try {
                    activity.startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return exists;
    }


    @Override
    protected boolean isBeta() {
        return true;
    }

    @Override
    public boolean isCustomUpdate() {
        return !TextUtils.isEmpty(org.telegram.messenger.BuildConfig.BETA_URL);
    }

    @Override
    public BetaUpdate getUpdate() {
        if (!isCustomUpdate()) return null;
        return BetaUpdaterController.getInstance().getUpdate();
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        if (!isCustomUpdate()) return;
        BetaUpdaterController.getInstance().checkForUpdate(force, whenDone);
    }

    @Override
    public void downloadUpdate() {
        if (!isCustomUpdate()) return;
        BetaUpdaterController.getInstance().downloadUpdate();
    }

    @Override
    public void cancelDownloadingUpdate() {
        if (!isCustomUpdate()) return;
        BetaUpdaterController.getInstance().cancelDownloadingUpdate();
    }

    @Override
    public boolean isDownloadingUpdate() {
        if (!isCustomUpdate()) return false;
        return BetaUpdaterController.getInstance().isDownloading();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        if (!isCustomUpdate()) return 0;
        return BetaUpdaterController.getInstance().getDownloadingProgress();
    }

    @Override
    public File getDownloadedUpdateFile() {
        if (!isCustomUpdate()) return null;
        return BetaUpdaterController.getInstance().getDownloadedFile();
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenu, ViewGroup sideMenuContainer) {
        if (!isCustomUpdate()) return null;
        return new UpdateLayout(activity, sideMenu, sideMenuContainer);
    }

    @Override
    public IUpdateButton takeUpdateButton(Context context) {
        if (!isCustomUpdate()) return null;
        return new UpdateButton(context);
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        try {
            (new UpdateAppAlertDialog(context, update, account)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }
}
