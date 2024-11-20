package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.CustomProperties;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.analytics.EventProperties;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.distribute.Distribute;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AlertsCreator;

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
                    props.set("model", Build.SOC_MODEL);
                    props.set("manufacturer", Build.SOC_MANUFACTURER);
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
            Crashes.trackError(e);
        } catch (Throwable ignore) {

        }
    }

    protected void logDualCameraInternal(boolean success, boolean vendor) {
        try {
            Analytics.trackEvent("dual-camera[" + (Build.MANUFACTURER + " " + Build.DEVICE).toUpperCase() + "]",
                new EventProperties()
                    .set("success", success)
                    .set("vendor", vendor)
                    .set("product", Build.PRODUCT + "")
                    .set("model", Build.MODEL)
            );
        } catch (Throwable ignore) {

        }
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
            String fileName = FileLoader.getAttachFileName(document);
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
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


}
