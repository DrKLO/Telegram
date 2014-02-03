package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;

public class HockeyServiceWrapper {
    public static void registerCrashManager(Context context) {
        CrashManager.register(context, ConnectionsManager.HOCKEY_APP_HASH);
    }

    public static void registerUpdateManager(Activity activity) {
        UpdateManager.register(activity, ConnectionsManager.HOCKEY_APP_HASH);
    }
}
