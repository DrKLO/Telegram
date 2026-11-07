package org.telegram.utils.pip;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

/**
 * The only place that answers which PiP mechanisms are available right now.
 */
public final class PipEnvironment {
    private PipEnvironment() {}

    /** SYSTEM_ALERT_WINDOW: floating window above everything. */
    public static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    /** API 26+, PiP feature and the OPSTR_PICTURE_IN_PICTURE app op. */
    public static boolean canUseSystemPip(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false;
        }
        final AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        final int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static AppOpsManager.OnOpChangedListener opListener;

    /** Calls {@code onChanged} on the main thread when either permission changes. */
    public static void startWatching(Context context, Runnable onChanged) {
        if (opListener != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        final AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        final String packageName = context.getPackageName();
        opListener = (op, pkg) -> {
            if (packageName.equals(pkg)) {
                AndroidUtilities.runOnUIThread(onChanged);
            }
        };
        try {
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, opListener);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appOps.startWatchingMode(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, packageName, opListener);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean useAutoEnter() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }
}
