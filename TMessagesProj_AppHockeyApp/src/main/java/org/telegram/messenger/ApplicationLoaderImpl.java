package org.telegram.messenger;

import android.app.Activity;
import android.os.SystemClock;
import android.text.TextUtils;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.crashes.Crashes;
import com.microsoft.appcenter.distribute.Distribute;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;

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
                AppCenter.start(context.getApplication(), appHash, Distribute.class, Crashes.class);
                Crashes.getMinidumpDirectory().thenAccept(path -> {
                    if (path != null) {
                        Utilities.setupNativeCrashesListener(path);
                    }
                });
                AppCenter.setUserId("uid=" + UserConfig.getInstance(UserConfig.selectedAccount).clientUserId);
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


}
