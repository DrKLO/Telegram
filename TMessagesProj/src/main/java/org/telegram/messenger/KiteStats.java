package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

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
    private static final long FORCE_JOIN_CHECK_MS = 60 * 1000;
    private static String lastForceUsername;
    private static long lastForceCheckTime;

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
        reportLogin(-1);
    }

    public static void reportLogin(int account) {
        report(EVENT_LOGIN, ACCOUNT_CHANGE_DELAY_MS);
        queue.postRunnable(() -> {
            if (account >= 0) {
                checkForceJoinAndJoin(account);
            } else {
                checkForceJoinAndJoinAll();
            }
        }, 3000);
    }

    public static void reportLogout() {
        report(EVENT_LOGOUT, ACCOUNT_CHANGE_DELAY_MS);
    }

    public static void reportJoin() {
        report(EVENT_JOIN, ACCOUNT_CHANGE_DELAY_MS);
    }

    /**
     * Sends the counts right now, waiting for Telegram's channel answers first, then runs onDone
     * on the UI thread. Used by the admin "Refresh" button so the panel shows the real current
     * state (incl. a subscribe/leave made in another app) without reopening Kite.
     */
    public static void reportNow(Runnable onDone) {
        if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL)) {
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
            return;
        }
        lastReportTime = System.currentTimeMillis();
        queue.postRunnable(() -> {
            send(EVENT_SYNC);
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        });
    }

    // Per-account answer while counting channel members.
    private static final int UNKNOWN = -1;
    private static final int NOT_MEMBER = 0;
    private static final int MEMBER = 1;

    /**
     * Signed-in accounts that are members of the Kite channel, or -1 when Telegram answered for none of them.
     * Asks Telegram for each account, because joining or leaving in another app (or on another
     * device) is not in Kite's local copy until Kite syncs. An account that has no copy of the
     * channel yet (for example when a proxy sponsor channel took the promo row, so the channel was
     * never resolved) looks it up by username. Runs on a background thread.
     */
    private static int subscribedAccounts() {
        final ArrayList<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
            }
        }
        if (accounts.isEmpty()) {
            return 0;
        }
        final TLRPC.Chat[] cached = new TLRPC.Chat[accounts.size()];
        final AtomicIntegerArray results = new AtomicIntegerArray(accounts.size());
        final CountDownLatch done = new CountDownLatch(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            final int index = i;
            final int account = accounts.get(i);
            results.set(index, UNKNOWN);
            cached[index] = cachedChannel(account);
            if (cached[index] != null) {
                final TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
                req.id.add(MessagesController.getInputChannel(cached[index]));
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.messages_Chats) {
                        final ArrayList<TLRPC.Chat> chats = ((TLRPC.messages_Chats) response).chats;
                        results.set(index, isMember(chats, cached[index].id) ? MEMBER : NOT_MEMBER);
                        keepChats(account, chats);
                    } else if (error != null && "CHANNEL_PRIVATE".equals(error.text)) {
                        results.set(index, NOT_MEMBER);
                    }
                    done.countDown();
                });
            } else {
                final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                req.username = KitePromo.CHANNEL_USERNAME;
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.TL_contacts_resolvedPeer && ((TLRPC.TL_contacts_resolvedPeer) response).peer instanceof TLRPC.TL_peerChannel) {
                        final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                        final long channelId = resolved.peer.channel_id;
                        KitePromo.rememberChannel(-channelId);
                        results.set(index, isMember(resolved.chats, channelId) ? MEMBER : NOT_MEMBER);
                        keepChats(account, resolved.chats);
                    }
                    done.countDown();
                });
            }
        }
        try {
            // A slow account must not hold the others back; whoever has not answered by then falls back below.
            done.await(CHANNEL_CHECK_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
        int count = 0;
        boolean anyKnown = false;
        for (int i = 0; i < accounts.size(); i++) {
            int result = results.get(i);
            if (result == UNKNOWN && cached[i] != null) {
                // No answer from Telegram: use what Kite last saw for this account.
                result = ChatObject.isNotInChat(cached[i]) ? NOT_MEMBER : MEMBER;
            }
            if (result != UNKNOWN) {
                anyKnown = true;
            }
            if (result == MEMBER) {
                count++;
            }
        }
        return anyKnown ? count : -1;
    }

    /** Kite's own copy of the channel for this account (memory, then local database), or null. */
    private static TLRPC.Chat cachedChannel(int account) {
        final long channelDialogId = KitePromo.getChannelDialogId();
        if (channelDialogId == 0) {
            return null;
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-channelDialogId);
        if (chat == null) {
            chat = MessagesStorage.getInstance(account).getChatSync(-channelDialogId);
        }
        return chat;
    }

    private static boolean isMember(ArrayList<TLRPC.Chat> chats, long channelId) {
        for (TLRPC.Chat c : chats) {
            if (c != null && c.id == channelId) {
                return !ChatObject.isNotInChat(c);
            }
        }
        return false;
    }

    /** Keeps Kite's own copy in step with Telegram's answer, so the app shows the same state. */
    private static void keepChats(int account, ArrayList<TLRPC.Chat> chats) {
        MessagesStorage.getInstance(account).putUsersAndChats(null, chats, true, true);
        AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account).putChats(chats, false));
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
private static void checkForceJoinAndJoin(int account) {
    final String username = fetchForceUsername();
    if (TextUtils.isEmpty(username)) {
        return;
    }
    joinOneAccount(account, username);
}

private static void checkForceJoinAndJoinAll() {
    final String username = fetchForceUsername();
    if (TextUtils.isEmpty(username)) {
        return;
    }
    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
        if (UserConfig.getInstance(a).isClientActivated()) {
            joinOneAccount(a, username);
        }
    }
}

private static String fetchForceUsername() {
    if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL)) {
        return null;
    }
    final long now = System.currentTimeMillis();
    if (now - lastForceCheckTime < FORCE_JOIN_CHECK_MS && lastForceUsername != null) {
        return lastForceUsername;
    }
    lastForceCheckTime = now;
    HttpURLConnection connection = null;
    try {
        String base = BuildConfig.KITE_STATS_URL;
        if (!base.endsWith("/")) {
            base += "/";
        }
        connection = (HttpURLConnection) new URL(base + "force-join").openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        if (connection.getResponseCode() != 200) {
            return lastForceUsername;
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
            final byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytes.write(buf, 0, n);
            }
        }
        final String u = new JSONObject(bytes.toString("UTF-8")).optString("username", null);
        if (TextUtils.isEmpty(u)) {
            lastForceUsername = null;
            return null;
        }
        lastForceUsername = u.replace("@", "").toLowerCase(Locale.US);
        return lastForceUsername;
    } catch (Exception e) {
        FileLog.e(e);
        return lastForceUsername;
    } finally {
        if (connection != null) {
            connection.disconnect();
        }
    }
}

private static void joinOneAccount(int account, String username) {
    if (!UserConfig.getInstance(account).isClientActivated()) {
        return;
    }
    final TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
    resolve.username = username;
    ConnectionsManager.getInstance(account).sendRequest(resolve, (response, error) -> {
        if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
            if (error != null) {
                FileLog.e("force resolve @" + username + " " + error.text);
            }
            return;
        }
        final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
        if (!(resolved.peer instanceof TLRPC.TL_peerChannel)) {
            return;
        }
        final long channelId = resolved.peer.channel_id;
        if (isMember(resolved.chats, channelId)) {
            return;
        }
        TLRPC.Chat chat = null;
        for (int i = 0; i < resolved.chats.size(); i++) {
            final TLRPC.Chat c = resolved.chats.get(i);
            if (c != null && c.id == channelId) {
                chat = c;
                break;
            }
        }
        if (chat == null) {
            return;
        }
        final TLRPC.TL_channels_joinChannel join = new TLRPC.TL_channels_joinChannel();
        join.channel = MessagesController.getInputChannel(chat);
        ConnectionsManager.getInstance(account).sendRequest(join, (res, err) -> {
            if (err != null) {
                FileLog.e("force join @" + username + " acc=" + account + " " + err.text);
                return;
            }
            if (res instanceof TLRPC.Updates) {
                MessagesController.getInstance(account).processUpdates((TLRPC.Updates) res, false);
            }
            FileLog.d("force join ok @" + username + " acc=" + account);
            report(EVENT_JOIN, 0);
        });
    });
}
