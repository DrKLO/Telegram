package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Kite: "join everywhere" tasks from the admin panel (server/stats).
 * When enabled in Settings → Kite, the app asks the panel for new tasks and joins
 * every signed-in account to the channel or group of each task, then reports the result.
 * Off by default, so only phones where the owner turned it on follow the panel.
 */
public final class KiteRemote {

    private static final long POLL_INTERVAL_MS = 2 * 60 * 1000L;
    private static final long DELAY_BETWEEN_JOINS_MS = 3000;
    private static final long REQUEST_TIMEOUT_S = 30;
    private static final int MAX_DONE = 2000;

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{4,32}");
    private static final Pattern INVITE_HASH = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private static final DispatchQueue queue = new DispatchQueue("KiteRemote");
    private static boolean started;
    private static final Runnable pollRunnable = KiteRemote::pollLoop;

    private KiteRemote() {
    }

    /**
     * Only builds carrying a private key (KITE_REMOTE_KEY, set in your own local.properties,
     * never in the public release) follow the panel. The public build has no key, so it never
     * polls and strangers' accounts are never touched.
     */
    public static boolean isEnabled() {
        return !TextUtils.isEmpty(BuildConfig.KITE_REMOTE_KEY);
    }

    /** Starts polling once per process; safe to call on every chat list open. */
    public static void start() {
        if (TextUtils.isEmpty(BuildConfig.KITE_STATS_URL) || !isEnabled()) {
            return;
        }
        queue.postRunnable(() -> {
            if (started) {
                return;
            }
            started = true;
            pollLoop();
        });
    }

    private static void pollLoop() {
        if (!isEnabled()) {
            started = false;
            return;
        }
        try {
            poll();
        } catch (Exception e) {
            FileLog.e(e);
        }
        queue.cancelRunnable(pollRunnable);
        queue.postRunnable(pollRunnable, POLL_INTERVAL_MS);
    }

    private static String baseUrl() {
        String url = BuildConfig.KITE_STATS_URL;
        return url.endsWith("/") ? url : url + "/";
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kite_remote", Context.MODE_PRIVATE);
    }

    private static void poll() throws Exception {
        final String response = http("GET", baseUrl() + "tasks?id=" + URLEncoder.encode(KiteStats.installId(), "UTF-8"), null);
        if (response == null) {
            return;
        }
        final JSONArray tasks = new JSONObject(response).optJSONArray("tasks");
        if (tasks == null || tasks.length() == 0) {
            return;
        }
        final Set<String> done = new HashSet<>(prefs().getStringSet("done", new HashSet<>()));
        boolean joinedSomething = false;
        for (int i = 0; i < tasks.length(); i++) {
            final JSONObject task = tasks.getJSONObject(i);
            final long taskId = task.optLong("id");
            final String kind = task.optString("kind");
            final String value = task.optString("value");
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                final UserConfig config = UserConfig.getInstance(account);
                if (!config.isClientActivated() || config.getCurrentUser() == null) {
                    continue;
                }
                final TLRPC.User me = config.getCurrentUser();
                final String key = taskId + ":" + me.id;
                if (done.contains(key)) {
                    continue;
                }
                if (joinedSomething) {
                    Thread.sleep(DELAY_BETWEEN_JOINS_MS);
                }
                joinedSomething = true;
                final String[] result = join(account, kind, value);
                if (result == null) {
                    // No answer from Telegram (offline?): try again on the next poll.
                    continue;
                }
                done.add(key);
                if (done.size() > MAX_DONE) {
                    done.clear();
                    done.add(key);
                }
                prefs().edit().putStringSet("done", new HashSet<>(done)).apply();
                report(taskId, me, result[0], result[1]);
            }
        }
    }

    /** Returns {status, error} or null when Telegram did not answer in time. */
    private static String[] join(int account, String kind, String value) throws InterruptedException {
        if ("invite".equals(kind)) {
            if (!INVITE_HASH.matcher(value).matches()) {
                return new String[]{"error", "bad invite link"};
            }
            final TLRPC.TL_messages_importChatInvite req = new TLRPC.TL_messages_importChatInvite();
            req.hash = value;
            return sendJoin(account, req);
        }
        if (!"username".equals(kind) || !USERNAME.matcher(value).matches()) {
            return new String[]{"error", "bad username"};
        }
        final TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
        resolve.username = value;
        final Object[] answer = send(account, resolve);
        if (answer == null) {
            return null;
        }
        if (answer[1] != null) {
            return new String[]{"error", ((TLRPC.TL_error) answer[1]).text};
        }
        if (!(answer[0] instanceof TLRPC.TL_contacts_resolvedPeer)) {
            return new String[]{"error", "not found"};
        }
        final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) answer[0];
        if (!(resolved.peer instanceof TLRPC.TL_peerChannel)) {
            return new String[]{"error", "not a channel or group"};
        }
        TLRPC.Chat chat = null;
        for (TLRPC.Chat c : resolved.chats) {
            if (c.id == resolved.peer.channel_id) {
                chat = c;
            }
        }
        if (chat == null) {
            return new String[]{"error", "not found"};
        }
        final MessagesController controller = MessagesController.getInstance(account);
        AndroidUtilities.runOnUIThread(() -> {
            controller.putUsers(resolved.users, false);
            controller.putChats(resolved.chats, false);
        });
        if (!ChatObject.isNotInChat(chat)) {
            return new String[]{"already", null};
        }
        final TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
        final TLRPC.TL_inputChannel input = new TLRPC.TL_inputChannel();
        input.channel_id = chat.id;
        input.access_hash = chat.access_hash;
        req.channel = input;
        return sendJoin(account, req);
    }

    private static String[] sendJoin(int account, TLObject req) throws InterruptedException {
        final Object[] answer = send(account, req);
        if (answer == null) {
            return null;
        }
        if (answer[1] != null) {
            final String text = ((TLRPC.TL_error) answer[1]).text;
            if ("USER_ALREADY_PARTICIPANT".equals(text)) {
                return new String[]{"already", null};
            }
            if ("INVITE_REQUEST_SENT".equals(text)) {
                return new String[]{"request", null};
            }
            return new String[]{"error", text};
        }
        TLRPC.Updates updates = null;
        if (answer[0] instanceof TLRPC.TL_chatInviteJoinResultOk) {
            updates = ((TLRPC.TL_chatInviteJoinResultOk) answer[0]).updates;
        } else if (answer[0] instanceof TLRPC.Updates) {
            updates = (TLRPC.Updates) answer[0];
        } else if (answer[0] instanceof TLRPC.TL_chatInviteJoinResultWebView) {
            return new String[]{"captcha", "join through the app: the group asks for a check"};
        }
        if (updates != null) {
            MessagesController.getInstance(account).processUpdates(updates, false);
        }
        return new String[]{"ok", null};
    }

    /** Sends a request and waits for the answer: {response, error}, or null on timeout. */
    private static Object[] send(int account, TLObject req) throws InterruptedException {
        final Object[] answer = new Object[2];
        final CountDownLatch latch = new CountDownLatch(1);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            answer[0] = response;
            answer[1] = error;
            latch.countDown();
        });
        return latch.await(REQUEST_TIMEOUT_S, TimeUnit.SECONDS) ? answer : null;
    }

    private static void report(long taskId, TLRPC.User me, String status, String error) {
        try {
            final JSONObject body = new JSONObject();
            body.put("id", KiteStats.installId());
            body.put("task", taskId);
            body.put("user_id", me.id);
            String name = UserObject.getPublicUsername(me);
            name = !TextUtils.isEmpty(name) ? "@" + name : UserObject.getUserName(me);
            body.put("name", name);
            body.put("status", status);
            if (error != null) {
                body.put("error", error);
            }
            http("POST", baseUrl() + "result", body.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String http(String method, String url, String json) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod(method);
            if (!TextUtils.isEmpty(BuildConfig.KITE_REMOTE_KEY)) {
                connection.setRequestProperty("X-Kite-Key", BuildConfig.KITE_REMOTE_KEY);
            }
            if (json != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString("UTF-8");
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
