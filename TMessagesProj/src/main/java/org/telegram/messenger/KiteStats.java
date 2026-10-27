package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Kite: anonymous usage counters for the admin panel (server/stats).
 * Sends only a random install id, the event name, the number of signed-in accounts,
 * how many of them are in the Kite channel and the build number. No phone numbers, names, user ids or messages.
 */
public final class KiteStats {

    public static final String EVENT_OPEN = "open";
    public static final String EVENT_LOGIN = "login";
    public static final String EVENT_LOGOUT = "logout";
    public static final String EVENT_JOIN = "join";

    // Login and logout change the account list a moment later, so count after it settles.
    private static final long ACCOUNT_CHANGE_DELAY_MS = 5000;

    private static boolean openReported;

    private KiteStats() {
    }

    public static void reportOpen() {
        if (openReported) {
            return;
        }
        openReported = true;
        report(EVENT_OPEN, 0);
    }

    public static void reportLogin() {
        report(EVENT_LOGIN, ACCOUNT_CHANGE_DELAY_MS);
    }

    public static void reportLogout() {
        report(EVENT_LOGOUT, ACCOUNT_CHANGE_DELAY_MS);
    }

    public static void reportJoin() {
        report(EVENT_JOIN, ACCOUNT_CHANGE_DELAY_MS);
    }

    /** Signed-in accounts that are members of the Kite channel, or -1 while that is unknown. */
    private static int subscribedAccounts() {
        final long channelDialogId = KitePromo.getChannelDialogId();
        if (channelDialogId == 0) {
            return -1;
        }
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            final TLRPC.Chat chat = MessagesController.getInstance(a).getChat(-channelDialogId);
            if (chat == null) {
                return -1;
            }
            if (!ChatObject.isNotInChat(chat)) {
                count++;
            }
        }
        return count;
    }

    private static void report(String event, long delayMs) {
        if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL)) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> send(event), delayMs);
    }

    private static String installId() {
        final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("kite_stats", Context.MODE_PRIVATE);
        String id = prefs.getString("install_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("install_id", id).apply();
        }
        return id;
    }

    private static void send(String event) {
        HttpURLConnection connection = null;
        try {
            final JSONObject body = new JSONObject();
            body.put("id", installId());
            body.put("event", event);
            body.put("accounts", UserConfig.getActivatedAccountsCount());
            body.put("subscribed", subscribedAccounts());
            body.put("build", BuildConfig.KITE_BUILD);
            final byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

            String url = BuildConfig.KITE_STATS_URL;
            if (!url.endsWith("/")) {
                url += "/";
            }
            connection = (HttpURLConnection) new URL(url + "event").openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            connection.getResponseCode();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
