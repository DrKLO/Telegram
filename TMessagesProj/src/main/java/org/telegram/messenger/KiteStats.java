package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    // Fresh channel counts when the user comes back to Kite; not shown as a daily counter.
    public static final String EVENT_SYNC = "sync";

    // Login and logout change the account list a moment later, so count after it settles.
    private static final long ACCOUNT_CHANGE_DELAY_MS = 5000;

    // Coming back to Kite resends the counts, but not more often than this.
    private static final long SYNC_INTERVAL_MS = 2 * 60 * 1000;
    private static final long CHANNEL_CHECK_TIMEOUT_SEC = 15;

    // Own queue: sending waits for Telegram's answers and must not hold up the app's shared queues.
    private static final DispatchQueue queue = new DispatchQueue("kiteStats");

    private static boolean openReported;
    private static long lastReportTime;

    private KiteStats() {
    }

    /** Called whenever the chat list is shown: "open" once per launch, then a throttled "sync". */
    public static void reportOpen() {
        if (!openReported) {
            openReported = true;
            report(EVENT_OPEN, 0);
        } else if (System.currentTimeMillis() - lastReportTime >= SYNC_INTERVAL_MS) {
            report(EVENT_SYNC, 0);
        }
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

    /**
     * Signed-in accounts that are members of the Kite channel, or -1 while the channel is not resolved yet.
     * Asks Telegram for each account, because leaving the channel in another app (or on another
     * device) is not in Kite's local copy until Kite syncs. Runs on a background thread.
     */
    private static int subscribedAccounts() {
        final long channelDialogId = KitePromo.getChannelDialogId();
        if (channelDialogId == 0) {
            return -1;
        }
        final ArrayList<Integer> accounts = new ArrayList<>();
        final ArrayList<TLRPC.Chat> cached = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            // Accounts other than the open one have no chats in memory, so fall back to their
            // local database. No entry means the account has never been in the channel.
            TLRPC.Chat chat = MessagesController.getInstance(a).getChat(-channelDialogId);
            if (chat == null) {
                chat = MessagesStorage.getInstance(a).getChatSync(-channelDialogId);
            }
            if (chat != null) {
                accounts.add(a);
                cached.add(chat);
            }
        }
        final AtomicInteger count = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            final int account = accounts.get(i);
            final TLRPC.Chat chat = cached.get(i);
            final TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
            req.id.add(MessagesController.getInputChannel(chat));
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                boolean member;
                if (response instanceof TLRPC.messages_Chats) {
                    final ArrayList<TLRPC.Chat> chats = ((TLRPC.messages_Chats) response).chats;
                    TLRPC.Chat fresh = null;
                    for (TLRPC.Chat c : chats) {
                        if (c.id == chat.id) {
                            fresh = c;
                        }
                    }
                    member = fresh != null && !ChatObject.isNotInChat(fresh);
                    // Keep Kite's own copy in step, so the app shows the same state.
                    MessagesStorage.getInstance(account).putUsersAndChats(null, chats, true, true);
                    AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account).putChats(chats, false));
                } else if (error != null && "CHANNEL_PRIVATE".equals(error.text)) {
                    member = false;
                } else {
                    member = !ChatObject.isNotInChat(chat);
                }
                if (member) {
                    count.incrementAndGet();
                }
                done.countDown();
            });
        }
        try {
            if (!done.await(CHANNEL_CHECK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                // No answer from Telegram (offline): a guess would be wrong either way, keep the last known count.
                return -1;
            }
        } catch (InterruptedException e) {
            return -1;
        }
        return count.get();
    }

    private static void report(String event, long delayMs) {
        if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL)) {
            return;
        }
        lastReportTime = System.currentTimeMillis();
        queue.postRunnable(() -> send(event), delayMs);
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
