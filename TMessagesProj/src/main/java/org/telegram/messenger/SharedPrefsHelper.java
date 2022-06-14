package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefsHelper {
    private static String WEB_VIEW_SHOWN_DIALOG_FORMAT = "confirm_shown_%d_%d";

    private static SharedPreferences webViewBotsPrefs;

    public static void init(Context ctx) {
        webViewBotsPrefs = ctx.getSharedPreferences("webview_bots", Context.MODE_PRIVATE);
    }

    public static boolean isWebViewConfirmShown(int currentAccount, long botId) {
        return webViewBotsPrefs.getBoolean(String.format(WEB_VIEW_SHOWN_DIALOG_FORMAT, currentAccount, botId), false);
    }

    public static void setWebViewConfirmShown(int currentAccount, long botId, boolean shown) {
        webViewBotsPrefs.edit().putBoolean(String.format(WEB_VIEW_SHOWN_DIALOG_FORMAT, currentAccount, botId), shown).apply();
    }

    public static void cleanupAccount(int account) {
        if (webViewBotsPrefs != null) {
            SharedPreferences.Editor editor = webViewBotsPrefs.edit();
            for (String key : webViewBotsPrefs.getAll().keySet()) {
                if (key.startsWith("confirm_shown_" + account + "_")) {
                    editor.remove(key);
                }
            }
            editor.apply();
        }
    }

    public static SharedPreferences getWebViewBotsPrefs() {
        return webViewBotsPrefs;
    }
}
