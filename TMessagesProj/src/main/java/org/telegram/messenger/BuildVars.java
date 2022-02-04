/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import ua.itaysonlab.catogram.CatogramConfig;

public class BuildVars {

    public static boolean DEBUG_VERSION = false;
    public static boolean LOGS_ENABLED = false;
    public static boolean DEBUG_PRIVATE_VERSION = false;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = false;
    public static boolean NO_SCOPED_STORAGE = (isStandaloneApp() && !CatogramConfig.INSTANCE.getEnableSaf()) || Build.VERSION.SDK_INT <= 29;
    public static int BUILD_VERSION = 2566;
    public static String BUILD_VERSION_STRING = "8.5.4";
    public static int APP_ID = BuildConfig.APP_ID; //obtain your own APP_ID at https://core.telegram.org/api/obtaining_api_id
    public static String APP_HASH = BuildConfig.APP_HASH; //obtain your own APP_HASH at https://core.telegram.org/api/obtaining_api_id

    public static String SMS_HASH = isStandaloneApp() ? "w0lkcmTZkKh" : ("oLeq9AcOZkT");
    public static String PLAYSTORE_APP_URL = "https://github.com/CatogramX/CatogramX/releases/latest";

    static {
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
            LOGS_ENABLED = DEBUG_VERSION || sharedPreferences.getBoolean("logsEnabled", false);
        }
    }

    public static boolean isStandaloneApp() {
        return !BuildConfig.BUILD_TYPE.equals("gplay");
    }

    public static boolean isBetaApp() {
        return BuildConfig.BUILD_TYPE.equals("HA");
    }
}
