package org.telegram.utils.twidget;

import android.content.Context;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Wallet.GramWalletWidget;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RichWidgetBlock;

/**
 * Widget messages (layer 233, `messageMediaWidget`) in chats (widget-sandbox docs/integration.md,
 * docs/sdk-v1.md §5).
 *
 * <p>A widget message is laid out as a rich message: {@link #richMessage} gives it one embed whose url
 * names the widget here (the caption is the message's media caption, drawn by the cell below it).
 * {@link RichWidgetBlock} shows the embed: it finds the {@link WidgetSandboxMessage} here, takes its size
 * from the message's anchors ({@link #widgetWidthPx}, {@link #contentHeightPx}) and reports when it is shown and hidden; the
 * widgets near the screen run, the others keep their last frame and then only their height. A widget with no
 * height for the width of its layout runs off screen for it first ({@link #measure}).
 *
 * <p>Per message (account, chat, message id) the client keeps the widget's Web Storage `local` area
 * (`widget_state`) and its content height at the last {@link #KEEP_WIDTHS} widget widths (`widget_heights`:
 * the height depends on the width, which changes with the screen, rotation and split screen), deleted with
 * the message, its chat and "clear local database"; `session` lives in memory (docs/sdk-v1.md §5.4). A
 * forwarded copy is another message with its own storage; a bot's edit is a new widget on the same storage.
 */
public final class WidgetMedia {

    public static final String URL_PREFIX = "tg-widget:";
    /** The shared constants of widget-sandbox v0.1.0, in CSS px (dp). */
    public static final int MIN_WIDTH = 200, MIN_HEIGHT = 50, MAX_HEIGHT = 600;
    /** Widgets kept running off screen, besides the shown ones. */
    private static final int KEEP_RUNNING = 4;
    /** Widgets that keep their last frame once stopped; the rest keep only their height. */
    private static final int KEEP_SNAPSHOTS = 12;
    /** A widget that died this many times in a row stays stopped until the app restarts or it is edited (D22). */
    private static final int MAX_CRASHES = 2;
    /** Widget widths whose content height is kept per message. */
    private static final int KEEP_WIDTHS = 4;
    /** Widgets measured off screen at once ({@link #measure}). */
    private static final int MEASURE_AT_ONCE = 3;
    /** A measurement ends this long after it started even without a loaded frame (one that never loads). */
    private static final long MEASURE_LIMIT_MS = 4000;
    /** `local` is written this long after the widget's last change, at once when it is hidden or the app pauses. */
    private static final long STORAGE_DELAY_MS = 1000;

    /** One widget message: what the server sent and, once shown, its runtime. */
    static final class Entry {
        final String url;
        final int account;
        final long dialogId;
        final int messageId;
        final int width, height;
        /** The compressed HTML (inflate). */
        final byte[] payload;
        /**
         * Where the widget is (docs/sdk-v1.md §5.11), the server's flags as they are: a forwarded copy, requests on a
         * tap, requests without one; the shared widget's id (0: none); the kind of chat ("private", "group",
         * "supergroup", "channel"); an inline result not sent yet (no input, no requests).
         */
        final boolean isCopy, canQuery, backgroundQuery, preview;
        final long widgetId;
        final String chatType;
        /**
         * The bot the widget is from (via_bot_id, else the author or the forward's author), 0 when unknown: the wallet
         * window shows it and signs for `bot<id>` (docs/sdk-v1.md §5.13). Checked to be a bot when used.
         */
        final long botId;
        /** UI: the widget's state as the client has it now, null for none; the shared state's hash from the server. */
        String chatData, publicData;
        long publicDataHash;
        /** The media the state came with: a new state is written into it, so the message keeps it. */
        TLRPC.TL_messageMediaWidget media;
        String html;
        WidgetSandboxMessage message;
        RichWidgetBlock block;
        /**
         * UI: the blocks of this widget attached to a view. More than one while a cell animates from
         * an old layout of the message to a new one; the newest of them runs the widget (owner).
         */
        final ArrayList<RichWidgetBlock> attached = new ArrayList<>();
        RichWidgetBlock owner;
        int shown;
        long lastShown;
        // UI thread from here on.
        /** widget_state was read; until then the widget does not start (its storage would be empty). */
        boolean stateLoaded;
        boolean activateOnLoad;
        int crashes, crashedSession;
        boolean dead;
        /** The width a layout asked the height at, and whether its bubble is outgoing ({@link #measure}). */
        int measureWidthPx;
        boolean measureOut;
        final Runnable measured = () -> onMeasured(this);

        Entry(String url, int account, long dialogId, int messageId, long botId, TLRPC.TL_messageMediaWidget media, boolean preview) {
            this.url = url;
            this.account = account;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.botId = botId;
            this.width = Math.max(0, media.width);
            this.height = Math.max(0, media.height);
            this.payload = media.payload;
            this.preview = preview;
            this.isCopy = !preview && media.is_copy;
            // A message being sent has no id for the server yet: its widget is made anew once it has (onMessageSent).
            this.canQuery = !preview && media.can_query && messageId > 0;
            this.backgroundQuery = !preview && media.background_query && messageId > 0;
            this.widgetId = media.widget_id;
            this.chatType = preview || isCopy ? null : chatTypeOf(account, dialogId);
            this.media = media;
            this.chatData = text(media.chat_data);
            this.publicData = text(media.public_data);
            this.publicDataHash = media.public_data_hash;
        }

        String storageKey() {
            return WidgetMedia.storageKey(account, dialogId, messageId);
        }
    }

    private static final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    /** The content heights per message (auto height), so the list does not jump. */
    private static final ConcurrentHashMap<String, Heights> heights = new ConcurrentHashMap<>();

    /** Content heights of one message by widget width, CSS px, most recent first. Any thread. */
    static final class Heights {
        private final ArrayList<int[]> byWidth = new ArrayList<>();

        /** [width, height] of the width nearest to `width`, or null when none is known. */
        synchronized int[] nearest(int width) {
            int[] best = null;
            for (int[] item : byWidth) {
                if (best == null || Math.abs(item[0] - width) < Math.abs(best[0] - width)) best = item;
            }
            return best;
        }

        /** The height at `width`, or at the nearest width known; 0 when none is. */
        synchronized int near(int width) {
            int best = 0, distance = Integer.MAX_VALUE;
            for (int[] item : byWidth) {
                int d = Math.abs(item[0] - width);
                if (d < distance) {
                    distance = d;
                    best = item[1];
                }
            }
            return best;
        }

        /** The resize anchor of the most recent height: true when its bottom edge stays put (docs/sdk-v1.md §5.6). */
        synchronized boolean anchorBottom() {
            return !byWidth.isEmpty() && byWidth.get(0).length > 2 && byWidth.get(0)[2] != 0;
        }

        synchronized boolean put(int width, int height) {
            return put(width, height, false);
        }

        /** Keeps `height` at `width` as the most recent, with its resize anchor; false when it was already so. */
        synchronized boolean put(int width, int height, boolean anchorBottom) {
            int anchor = anchorBottom ? 1 : 0;
            for (int i = 0; i < byWidth.size(); i++) {
                int[] item = byWidth.get(i);
                if (item[0] != width) continue;
                if (item[1] == height && item[2] == anchor && i == 0) return false;
                byWidth.remove(i);
                break;
            }
            byWidth.add(0, new int[]{width, height, anchor});
            while (byWidth.size() > KEEP_WIDTHS) byWidth.remove(byWidth.size() - 1);
            return true;
        }

        /** Storage queue, at load: the rows of widget_heights ([width, height, anchor]), most recent first. */
        synchronized void load(ArrayList<int[]> rows) {
            if (!byWidth.isEmpty()) return;
            for (int i = 0; i < rows.size() && i < KEEP_WIDTHS; i++) byWidth.add(rows.get(i));
        }
    }

    private static Heights heightsOf(String key) {
        Heights h = heights.get(key);
        if (h == null) {
            Heights created = new Heights();
            h = heights.putIfAbsent(key, created);
            if (h == null) h = created;
        }
        return h;
    }
    /** Web Storage per message: {"local":{..},"session":{..}}. `local` mirrors widget_state. UI thread. */
    private static final HashMap<String, JSONObject> storages = new HashMap<>();
    /** `local` areas waiting for their write, by storage key. UI thread. */
    private static final HashMap<String, Entry> pendingWrites = new HashMap<>();
    private static final Runnable writePending = WidgetMedia::flushStorage;
    /** UI: widgets a layout asked the height of, and those running for it now ({@link #measure}). */
    private static final ArrayList<Entry> toMeasure = new ArrayList<>(), measuring = new ArrayList<>();
    /** UI: what waits for the shown widgets' heights ({@link #whenShownHeightsKnown}). */
    private static final ArrayList<Runnable> heightWaiters = new ArrayList<>();
    private static boolean linkDialogShown, appPaused;

    private WidgetMedia() {
    }


    public static boolean isWidget(TLRPC.MessageMedia media) {
        return media instanceof TLRPC.TL_messageMediaWidget;
    }


    public static boolean isWidgetUrl(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    private static String storageKey(int account, long dialogId, int messageId) {
        return account + "/" + dialogId + "/" + messageId;
    }

    /**
     * The rich layout of a widget message: the widget alone (the cell draws the caption). The url
     * carries the payload's hash, so a bot's edit is a new widget (on the same storage). Any thread.
     */
    public static TL_iv.RichMessage richMessage(MessageObject object, TLRPC.TL_messageMediaWidget media) {
        String url = URL_PREFIX + object.currentAccount + "/" + object.getDialogId() + "/" + object.messageOwner.id + "/"
                + Arrays.hashCode(media.payload);
        entry(object, url, media);
        return layout(object, url);
    }

    /**
     * UI: a message sent from this device got its id. What its widget kept under the temporary one (heights, the
     * `local` area) moves there, before the widget is made anew for the new id (MessageObject#onWidgetMessageSent).
     */
    public static void onMessageSent(int account, long dialogId, int oldId, int newId) {
        if (oldId == newId) return;
        flushStorage();
        String from = storageKey(account, dialogId, oldId), to = storageKey(account, dialogId, newId);
        Heights known = heights.remove(from);
        if (known != null) heights.putIfAbsent(to, known);
        JSONObject storage = storages.remove(from);
        if (storage != null && !storages.containsKey(to)) storages.put(to, storage);
        MessagesStorage.getInstance(account).moveWidgetState(dialogId, oldId, newId);
        // The height it has now, kept only for a widget off screen: the new one takes the bubble from there.
        for (Entry entry : entries.values()) {
            if (entry.account != account || entry.dialogId != dialogId || entry.messageId != oldId || entry.message == null || entry.height != 0) continue;
            int width = css(entry.message.frameWidthPx());
            int height = clamp(css(entry.message.heightPx()));
            boolean anchorBottom = entry.message.anchorBottom();
            if (width > 0 && entry.message.heightPx() > 0 && heightsOf(to).put(width, height, anchorBottom)) {
                MessagesStorage.getInstance(account).putWidgetHeight(dialogId, newId, width, height, anchorBottom ? 1 : 0, KEEP_WIDTHS);
            }
        }
    }

    /** The bot a widget message is from: the inline bot, else the author of the original (a bot sends widgets). */
    private static long botOf(MessageObject object) {
        TLRPC.Message message = object.messageOwner;
        if (message.via_bot_id != 0) return message.via_bot_id;
        if (message.fwd_from != null) {
            return message.fwd_from.from_id instanceof TLRPC.TL_peerUser ? message.fwd_from.from_id.user_id : 0;
        }
        long from = object.getFromChatId();
        return from > 0 ? from : 0;
    }

    private static Entry entry(MessageObject object, String url, TLRPC.TL_messageMediaWidget media) {
        Entry entry = entries.get(url);
        if (entry == null) {
            entry = new Entry(url, object.currentAccount, object.getDialogId(), object.messageOwner.id, botOf(object), media, false);
            Entry raced = entries.putIfAbsent(url, entry);
            if (raced != null) {
                entry = raced;
            } else {
                loadState(entry);
            }
        } else if (entry.media != media) {
            // Another copy of the message (an edit of a channel post carries the bot's new state): its state wins.
            Entry known = entry;
            AndroidUtilities.runOnUIThread(() -> {
                known.media = media;
                known.publicDataHash = media.public_data_hash;
                applyData(known, text(media.chat_data), text(media.public_data));
            });
        }
        return entry;
    }

    private static TL_iv.RichMessage layout(MessageObject object, String url) {
        TL_iv.pageBlockEmbed embed = new TL_iv.pageBlockEmbed();
        embed.url = url;
        embed.html = "";
        embed.caption = new TL_iv.PageCaption();
        embed.caption.text = new TL_iv.textEmpty();
        embed.caption.credit = new TL_iv.textEmpty();
        // The caption is the message's own media caption, drawn by the cell below the widget.
        TL_iv.RichMessage rich = new TL_iv.RichMessage();
        rich.blocks.add(embed);
        return rich;
    }

    /**
     * The widget behind an embed's url, its runtime made on first use (nothing runs yet), or null when the
     * payload does not unpack. Any thread.
     */
    public static WidgetSandboxMessage find(String url) {
        Entry entry = isWidgetUrl(url) ? entries.get(url) : null;
        if (entry == null) return null;
        synchronized (entry) {
            if (entry.message == null) {
                if (entry.html == null) entry.html = inflate(entry.payload);
                if (entry.html == null) return null;
                Context context = ApplicationLoader.applicationContext;
                WidgetSandboxMessage message = new WidgetSandboxMessage(
                        context, entry.messageId, entry.url, "", entry.html, false, 1);
                message.setSdk(locale(), null);
                message.setContext(contextOf(entry));
                message.setData(dataOf(entry.chatData, entry.publicData));
                message.setEvents(events);
                entry.message = message;
            }
            return entry.message;
        }
    }

    /** The widget's width in px: the bubble's (`width` 0) or the anchor clamped to [200, bubble]. */
    public static int widgetWidthPx(String url, int bubbleWidthPx) {
        Entry entry = isWidgetUrl(url) ? entries.get(url) : null;
        if (entry == null || entry.width == 0) return bubbleWidthPx;
        return Math.min(bubbleWidthPx, AndroidUtilities.dp(Math.max(MIN_WIDTH, entry.width)));
    }

    /**
     * The bubble's height for a widget `widthPx` wide, in px: the `height` anchor clamped to [50, 600]; with
     * `height` 0 the content height clamped the same way: the live one when the last frame has this width,
     * else the one kept for this width or the nearest width known, else the placeholder.
     */
    public static int contentHeightPx(String url, WidgetSandboxMessage message, int widthPx) {
        Entry entry = isWidgetUrl(url) ? entries.get(url) : null;
        if (entry == null) return 0;
        if (entry.height > 0) return AndroidUtilities.dp(clamp(entry.height));
        int px = message != null && Math.abs(message.frameWidthPx() - widthPx) <= 1 ? message.heightPx() : 0;
        if (px > 0) return Math.min(AndroidUtilities.dp(MAX_HEIGHT), Math.max(AndroidUtilities.dp(MIN_HEIGHT), px));
        Heights known = heights.get(entry.storageKey());
        int css = known != null ? known.near(css(widthPx)) : 0;
        return AndroidUtilities.dp(clamp(css > 0 ? css : WidgetSandboxMessage.PLACEHOLDER_DP));
    }

    /**
     * The edge of the bubble that stays put when its height changes (docs/sdk-v1.md §2.12, §5.6): the one the shown
     * frame came with, else the one kept with the cached heights (a previous run's), else the top.
     */
    public static boolean anchorBottom(String url, WidgetSandboxMessage message) {
        if (message != null && message.heightPx() > 0) return message.anchorBottom();
        Entry entry = isWidgetUrl(url) ? entries.get(url) : null;
        Heights known = entry != null ? heights.get(entry.storageKey()) : null;
        return known != null && known.anchorBottom();
    }

    /**
     * Where {@link #contentHeightPx} takes the height from, for the log: the bot's anchor, the live frame,
     * the cache at this width or at the nearest one, or the placeholder.
     */
    public static String heightSource(String url, WidgetSandboxMessage message, int widthPx) {
        Entry entry = isWidgetUrl(url) ? entries.get(url) : null;
        if (entry == null) return "none";
        if (entry.height > 0) return "anchor " + entry.height;
        if (message != null && Math.abs(message.frameWidthPx() - widthPx) <= 1 && message.heightPx() > 0) return "frame";
        Heights known = heights.get(entry.storageKey());
        int[] near = known != null ? known.nearest(css(widthPx)) : null;
        if (near == null || near[1] <= 0) return "placeholder";
        return near[0] == css(widthPx) ? "cache" : "cache of width " + near[0];
    }

    private static int css(int px) {
        return Math.round(px / AndroidUtilities.density);
    }

    private static int clamp(int dp) {
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, dp));
    }

    // ── widget_state ──

    /** Reads the message's heights and `local` area; the widget starts once they are known. */
    @SuppressWarnings("unchecked")
    private static void loadState(Entry entry) {
        MessagesStorage storage = MessagesStorage.getInstance(entry.account);
        storage.getStorageQueue().postRunnable(() -> {
            Object[] state = storage.getWidgetState(entry.dialogId, entry.messageId);
            ArrayList<int[]> rows = (ArrayList<int[]>) state[1];
            if (!rows.isEmpty()) heightsOf(entry.storageKey()).load(rows);
            AndroidUtilities.runOnUIThread(() -> onStateLoaded(entry, (String) state[0]));
        });
    }

    private static void onStateLoaded(Entry entry, String local) {
        entry.stateLoaded = true;
        String key = entry.storageKey();
        if (local != null && !storages.containsKey(key)) {
            try {
                JSONObject storage = new JSONObject();
                storage.put("local", new JSONObject(local));
                storages.put(key, storage);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        WidgetSandboxMessage message = entry.message;
        if (message != null) {
            JSONObject storage = storages.get(key);
            message.setSdk(locale(), storage == null ? null : storage.toString());
        }
        // The cached height came after the first layout: the message takes it, as after a resize away.
        if (entry.block != null && (message == null || message.heightPx() <= 0)) entry.block.onResizedAway("cached heights read");
        if (entry.activateOnLoad) {
            entry.activateOnLoad = false;
            if (entry.shown > 0 && message != null) start(entry);
        }
        measureNext();
    }

    // ── heights before they are shown ──

    /**
     * Any thread: a layout of the message has no height for its widget at `widthPx` (none kept, or only at
     * another width). The widget runs off screen, a few at a time and the nearest to the screen first, until
     * what it builds on load is laid out: its height reaches the layout while no cell shows it
     * ({@link RichWidgetBlock#onResizedAway}) and is kept, so the bubble shows at its height rather than
     * moving once the widget is up on screen.
     */
    public static void measure(String url, int widthPx, boolean out) {
        AndroidUtilities.runOnUIThread(() -> {
            Entry entry = entries.get(url);
            if (entry == null || entry.message == null) return;
            if (measuring.contains(entry)) {
                // A newer layout of another width (the chat's side menu, a rotation): the run goes on at it.
                entry.measureWidthPx = widthPx;
                entry.message.setWidthPx(widthPx);
                return;
            }
            entry.measureWidthPx = widthPx;
            entry.measureOut = out;
            toMeasure.remove(entry);
            toMeasure.add(entry);
            measureNext();
        });
    }

    private static void measureNext() {
        Entry entry;
        while (measuring.size() < MEASURE_AT_ONCE && !appPaused && (entry = nextToMeasure()) != null) {
            toMeasure.remove(entry);
            WidgetSandboxMessage message = entry.message;
            if (message == null || entry.dead || entry.shown > 0) continue;
            String source = heightSource(entry.url, message, entry.measureWidthPx);
            if (!source.equals("placeholder") && !source.startsWith("cache of width")) continue;
            message.setWidthPx(entry.measureWidthPx);
            // The theme's text tokens set the height, its palette the frame kept: the app's theme for the
            // bubble until a cell gives the chat's (the same text, the same colours unless a chat theme).
            if (!message.hasTheme()) message.setTheme(WidgetSandboxTheme.build(null, entry.measureOut));
            // Already running off screen: the new width brings a frame of its own, nothing to wait for.
            if (message.isActive()) continue;
            measuring.add(entry);
            start(entry);
            AndroidUtilities.runOnUIThread(entry.measured, MEASURE_LIMIT_MS);
        }
    }

    /**
     * The widget to measure first: the one nearest to a shown widget of its chat (by message id: the list
     * scrolls to its neighbours next), else the latest asked. Only those whose kept heights were read: they
     * may hold this width already.
     */
    private static Entry nextToMeasure() {
        Entry best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int i = toMeasure.size() - 1; i >= 0; i--) {
            Entry entry = toMeasure.get(i);
            if (!entry.stateLoaded) continue;
            long distance = Long.MAX_VALUE - 1;
            for (Entry shown : entries.values()) {
                if (shown.shown == 0 || shown.account != entry.account || shown.dialogId != entry.dialogId) continue;
                distance = Math.min(distance, Math.abs((long) shown.messageId - entry.messageId));
            }
            if (distance < bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * UI: runs `then` once the widgets on screen have their heights, at most `limitMs` from now. A widget
     * shown with no height kept for its width (a chat opening on messages never seen at this width) has
     * one once it has loaded, some tens of ms after it starts: a chat that waits for it opens with its
     * bubbles at their heights instead of moving while it slides in.
     */
    public static void whenShownHeightsKnown(Runnable then, long limitMs) {
        if (shownHeightsKnown()) {
            then.run();
            return;
        }
        Runnable once = new Runnable() {
            @Override
            public void run() {
                if (!heightWaiters.remove(this)) return;
                AndroidUtilities.cancelRunOnUIThread(this);
                then.run();
            }
        };
        heightWaiters.add(once);
        AndroidUtilities.runOnUIThread(once, limitMs);
    }

    private static boolean shownHeightsKnown() {
        for (Entry entry : entries.values()) {
            WidgetSandboxMessage message = entry.message;
            RichWidgetBlock block = entry.owner;
            if (entry.shown == 0 || message == null || block == null || entry.dead || message.hasLoadedFrame()) continue;
            String source = heightSource(entry.url, message, block.widthPx());
            if (source.equals("placeholder") || source.startsWith("cache of width")) return false;
        }
        return true;
    }

    /** UI: a widget loaded or changed its height: what waited for the shown ones runs once they all have theirs. */
    private static void checkShownHeights() {
        if (heightWaiters.isEmpty() || !shownHeightsKnown()) return;
        // After the frames already posted: the blocks take the loaded heights first.
        for (Runnable waiter : new ArrayList<>(heightWaiters)) AndroidUtilities.runOnUIThread(waiter);
    }

    /** UI: the widget measured (its loaded frame came), died or ran out of time: the next one starts. */
    private static void onMeasured(Entry entry) {
        AndroidUtilities.cancelRunOnUIThread(entry.measured);
        if (!measuring.remove(entry)) return;
        trim();
        measureNext();
    }

    /** UI: writes the `local` areas changed since the last write. */
    private static void flushStorage() {
        AndroidUtilities.cancelRunOnUIThread(writePending);
        if (pendingWrites.isEmpty()) return;
        for (Entry entry : pendingWrites.values()) {
            JSONObject storage = storages.get(entry.storageKey());
            JSONObject local = storage != null ? storage.optJSONObject("local") : null;
            String value = local == null || local.length() == 0 ? "" : local.toString();
            MessagesStorage.getInstance(entry.account).putWidgetStorage(entry.dialogId, entry.messageId, value);
        }
        pendingWrites.clear();
    }

    /** MessagesStorage cleared the account's local database: the state in memory goes too. Any thread. */
    public static void onStateCleared(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            String prefix = account + "/";
            removeAccount(heights.keySet(), prefix);
            removeAccount(storages.keySet(), prefix);
            removeAccount(pendingWrites.keySet(), prefix);
        });
    }

    /**
     * MessagesStorage forgot the account's widget heights (development). The heights in memory go too, and
     * the widgets not attached now start over: no last frame and no height, as never shown. Any thread.
     */
    public static void onHeightsCleared(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            removeAccount(heights.keySet(), account + "/");
            for (Entry entry : entries.values()) {
                if (entry.account == account && entry.attached.isEmpty() && entry.message != null) entry.message.forget();
            }
        });
    }

    // ── what runs ──

    /** UI: the block now showing a widget; its height changes away from the screen go there. */
    /** UI: the block last registered for `url`: the message's previous layout while a new one is made. */
    public static RichWidgetBlock registered(String url) {
        Entry entry = entries.get(url);
        return entry == null ? null : entry.block;
    }

    public static void register(String url, RichWidgetBlock block) {
        Entry entry = entries.get(url);
        if (entry != null) entry.block = block;
    }

    /**
     * UI: `block` was attached to a view. True when it runs the widget: the newest attached block of
     * the message does (an older layout attached for a change animation only shows its frame). An owner
     * it takes over from is demoted first.
     */
    public static boolean attach(String url, RichWidgetBlock block) {
        Entry entry = entries.get(url);
        if (entry == null) return true;
        if (!entry.attached.contains(block)) entry.attached.add(block);
        RichWidgetBlock newest = newest(entry.attached);
        if (newest != block) return false;
        RichWidgetBlock previous = entry.owner;
        entry.owner = block;
        if (previous != null && previous != block) previous.demote();
        return true;
    }

    /** UI: `block` left its view; the newest block still attached, if any, runs the widget from now on. */
    public static void detach(String url, RichWidgetBlock block) {
        Entry entry = entries.get(url);
        if (entry == null) return;
        entry.attached.remove(block);
        if (entry.owner != block) return;
        entry.owner = newest(entry.attached);
        if (entry.owner != null) entry.owner.promote();
    }

    private static RichWidgetBlock newest(ArrayList<RichWidgetBlock> blocks) {
        RichWidgetBlock newest = null;
        for (RichWidgetBlock b : blocks) {
            if (newest == null || b.serial > newest.serial) newest = b;
        }
        return newest;
    }

    /** UI: a block shows the widget: it runs (once its storage is read); far from the screen others stop. */
        public static void onShown(WidgetSandboxMessage message) {
        Entry entry = entryOf(message);
        if (entry == null) return;
        entry.shown++;
        entry.lastShown = System.currentTimeMillis();
        message.setPaused(appPaused);
        if (entry.stateLoaded) start(entry);
        else entry.activateOnLoad = true;
        trim();
        updatePolls();
    }

    /**
     * Runs the widget. A widget that died is started anew the next time it shows, keeping its last frame
     * until the new one loads; after {@link #MAX_CRASHES} deaths it stays stopped with its badge.
     */
    private static void start(Entry entry) {
        WidgetSandboxMessage message = entry.message;
        if (entry.dead) {
            if (entry.crashes >= MAX_CRASHES) return;
            entry.dead = false;
            message.unload();
        }
        message.activate();
    }

    /** UI: a block stopped showing the widget. */
    public static void onHidden(WidgetSandboxMessage message) {
        Entry entry = entryOf(message);
        if (entry == null) return;
        entry.shown = Math.max(0, entry.shown - 1);
        entry.lastShown = System.currentTimeMillis();
        trim();
        flushStorage();
        updatePolls();
    }

    /** UI: the app went to the background or came back: widgets pause (no frames, 1 Hz timers), storage is written. */
    public static void onAppPaused(boolean paused) {
        if (appPaused == paused) return;
        appPaused = paused;
        for (Entry entry : entries.values()) {
            if (entry.message != null) entry.message.setPaused(paused);
        }
        if (paused) flushStorage();
        else measureNext();
        updatePolls();
    }

    /** Keeps the shown widgets and the few last shown running; stops the rest, newest first kept. */
    private static void trim() {
        ArrayList<Entry> hidden = new ArrayList<>();
        for (Entry entry : entries.values()) {
            // One measured runs until its loaded frame (onMeasured).
            if (entry.message != null && entry.shown == 0 && !measuring.contains(entry)) hidden.add(entry);
        }
        Collections.sort(hidden, (a, b) -> Long.compare(b.lastShown, a.lastShown));
        for (int i = 0; i < hidden.size(); i++) {
            WidgetSandboxMessage message = hidden.get(i).message;
            if (i >= KEEP_RUNNING) message.unload();
            if (i >= KEEP_SNAPSHOTS) message.dropSnapshot();
        }
    }

    private static void removeAccount(java.util.Set<String> keys, String prefix) {
        for (java.util.Iterator<String> i = keys.iterator(); i.hasNext(); ) {
            if (i.next().startsWith(prefix)) i.remove();
        }
    }

    private static Entry entryOf(WidgetSandboxMessage message) {
        return message == null ? null : entries.get(message.file);
    }

    private static String locale() {
        try {
            return LocaleController.getInstance().getCurrentLocale().toLanguageTag();
        } catch (Exception e) {
            return null;
        }
    }

    // ── the widget's state, where it is, requests (docs/sdk-v1.md §5.11) ──

    /** A state field; none (null or empty: how the bot clears it) is null. */
    private static String text(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** The kind of chat as the Bot API names it: "private", "group", "supergroup" or "channel". */
    private static String chatTypeOf(int account, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) return "private";
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null) return null;
        if (!ChatObject.isChannel(chat)) return "group";
        return chat.megagroup ? "supergroup" : "channel";
    }

    /** {"chatData","publicData"}: the state for WidgetSandboxMessage#setData. */
    private static String dataOf(String chatData, String publicData) {
        try {
            JSONObject data = new JSONObject();
            data.put("chatData", chatData == null ? JSONObject.NULL : chatData);
            data.put("publicData", publicData == null ? JSONObject.NULL : publicData);
            return data.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Where the widget is, as the server's flags say, never derived (docs/sdk-v1.md §5.11): no chat type for a copy or
     * a preview, the account as the viewer only where requests are allowed.
     */
    private static String contextOf(Entry entry) {
        try {
            JSONObject context = new JSONObject();
            context.put("isCopy", entry.isCopy);
            context.put("canQuery", entry.canQuery);
            context.put("backgroundQuery", entry.backgroundQuery);
            context.put("preview", entry.preview);
            context.put("wallet", walletAvailable(entry));
            if (entry.chatType != null) context.put("chatType", entry.chatType);
            TLRPC.User me = entry.canQuery ? UserConfig.getInstance(entry.account).getCurrentUser() : null;
            if (me != null) {
                JSONObject viewer = new JSONObject();
                viewer.put("id", me.id);
                if (!TextUtils.isEmpty(me.first_name)) viewer.put("firstName", me.first_name);
                if (!TextUtils.isEmpty(me.last_name)) viewer.put("lastName", me.last_name);
                String username = UserObject.getPublicUsername(me);
                if (!TextUtils.isEmpty(username)) viewer.put("username", username);
                viewer.put("isPremium", me.premium);
                context.put("viewer", viewer);
            }
            return context.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** UI: a new state of the widget: kept with the message (its media) and given to the live widget. */
    private static void applyData(Entry entry, String chatData, String publicData) {
        if (TextUtils.equals(entry.chatData, chatData) && TextUtils.equals(entry.publicData, publicData)) return;
        entry.chatData = chatData;
        entry.publicData = publicData;
        if (entry.media != null) {
            entry.media.chat_data = chatData;
            entry.media.public_data = publicData;
            entry.media.public_data_hash = entry.publicDataHash;
        }
        if (entry.message != null) entry.message.setData(dataOf(chatData, publicData));
    }

    /**
     * UI: the bot edited a message's widget state (updateMessageWidgetData; the storage has it already). A field that is
     * not in the update stays, an empty one clears it.
     */
    public static void onDataEdited(int account, long dialogId, int messageId, String publicData, long publicDataHash, String chatData) {
        for (Entry entry : entries.values()) {
            if (entry.preview || entry.account != account || entry.dialogId != dialogId || entry.messageId != messageId) continue;
            if (publicData != null) {
                entry.publicDataHash = publicDataHash;
                if (entry.media != null) entry.media.public_data_hash = publicDataHash;
            }
            applyData(entry, chatData != null ? text(chatData) : entry.chatData, publicData != null ? text(publicData) : entry.publicData);
        }
    }

    /**
     * A widget in a list of inline results (botInlineMessageWidget) as a preview (docs/sdk-v1.md §2.16): its state, no
     * flags, no input, no requests, no storage. The url of its embed-like runtime ({@link #find}); a sent result is a
     * new widget message.
     */
    public static String previewUrl(int account, TLRPC.BotInlineResult result) {
        TLRPC.TL_botInlineMessageWidget widget = (TLRPC.TL_botInlineMessageWidget) result.send_message;
        String url = URL_PREFIX + "preview/" + account + "/" + result.query_id + "/" + result.id + "/" + Arrays.hashCode(widget.payload);
        if (!entries.containsKey(url)) {
            TLRPC.TL_messageMediaWidget media = new TLRPC.TL_messageMediaWidget();
            media.width = widget.width;
            media.height = widget.height;
            media.payload = widget.payload;
            media.public_data = widget.public_data;
            media.public_data_hash = widget.public_data_hash;
            media.chat_data = widget.chat_data;
            media.widget_id = widget.widget_id;
            Entry entry = new Entry(url, account, 0, 0, 0, media, true);
            entry.stateLoaded = true;
            entries.putIfAbsent(url, entry);
        }
        return url;
    }

    /** A widget in a list of inline results: no input reaches it (docs/sdk-v1.md §2.16). */
    public static boolean isPreview(String url) {
        Entry entry = isWidgetUrl(url) ? entries.get(url) : null;
        return entry != null && entry.preview;
    }

    /** How long a request may wait for the network before it fails: the server answers WIDGET_RESPONSE_TIMEOUT itself. */
    private static final long REQUEST_LIMIT_MS = 30_000;

    /**
     * UI: the widget's request (checked by the framework): messages.getBotWidgetQueryAnswer with a data query,
     * `with_wallet` when it asks for the user's wallet card (docs/sdk-v1.md §5.13).
     */
    private static void request(Entry entry, WidgetSandboxMessage message, long id, String data, boolean wallet) {
        if (entry == null || !entry.canQuery || wallet && !walletAvailable(entry)) {
            message.answerRequest(id, WidgetSandboxSession.REQUEST_FAILED, null);
            return;
        }
        TLRPC.TL_widgetQueryTypeData type = new TLRPC.TL_widgetQueryTypeData();
        type.with_wallet = wallet;
        query(entry, message, id, type, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * UI: the widget's query to its bot (a request, or a wallet call's outcome), answered once with the bot's result or a
     * failure: WIDGET_RESPONSE_TIMEOUT a timeout, FLOOD_WAIT rate limited, anything else failed.
     */
    private static void query(Entry entry, WidgetSandboxMessage message, long id, TLRPC.WidgetQueryType type, byte[] data) {
        boolean[] answered = new boolean[1];
        ConnectionsManager connections = ConnectionsManager.getInstance(entry.account);
        int[] requestId = new int[1];
        Runnable limit = () -> {
            if (answered[0]) return;
            answered[0] = true;
            connections.cancelRequest(requestId[0], true);
            message.answerRequest(id, WidgetSandboxSession.REQUEST_FAILED, null);
        };
        sendQuery(entry, type, data, requestId, (res, err) -> {
            if (answered[0]) return;
            answered[0] = true;
            AndroidUtilities.cancelRunOnUIThread(limit);
            if (res != null) {
                message.answerRequest(id, WidgetSandboxSession.REQUEST_ANSWERED, text(res.result));
            } else {
                String error = err != null ? err.text : "";
                int failure = "WIDGET_RESPONSE_TIMEOUT".equals(error) ? WidgetSandboxSession.REQUEST_TIMEOUT
                        : error != null && error.startsWith("FLOOD_WAIT") ? WidgetSandboxSession.REQUEST_RATE_LIMITED
                        : WidgetSandboxSession.REQUEST_FAILED;
                message.answerRequest(id, failure, null);
            }
        });
        AndroidUtilities.runOnUIThread(limit, REQUEST_LIMIT_MS);
    }

    /** Accounts whose server has no query types yet (production until the scheme is rolled out): the old form at once. */
    private static final boolean[] legacyQuery = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    /**
     * messages.getBotWidgetQueryAnswer with `type`; a server that does not know the method yet gets a plain data query
     * again in the form without the query type. A query that needs the types (the wallet's) fails there: the old form
     * would drop what the bot needs.
     */
    private static void sendQuery(Entry entry, TLRPC.WidgetQueryType type, byte[] data, int[] requestId, Utilities.Callback2<TLRPC.TL_messages_botWidgetQueryAnswer, TLRPC.TL_error> done) {
        final boolean plain = type instanceof TLRPC.TL_widgetQueryTypeData && !((TLRPC.TL_widgetQueryTypeData) type).with_wallet;
        final boolean legacy = legacyQuery[entry.account];
        if (legacy && !plain) {
            TLRPC.TL_error error = new TLRPC.TL_error();
            error.code = 400;
            error.text = "WIDGET_QUERY_TYPE_UNSUPPORTED";
            AndroidUtilities.runOnUIThread(() -> done.run(null, error));
            return;
        }
        TLRPC.TL_messages_getBotWidgetQueryAnswer req = legacy ? new TLRPC.TL_messages_getBotWidgetQueryAnswer_layer233() : new TLRPC.TL_messages_getBotWidgetQueryAnswer();
        req.peer = MessagesController.getInstance(entry.account).getInputPeer(entry.dialogId);
        req.msg_id = entry.messageId;
        req.query_type = type;
        req.data = data;
        requestId[0] = ConnectionsManager.getInstance(entry.account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (!legacy && err != null && err.text != null && (err.text.startsWith("INPUT_METHOD_INVALID") || err.text.startsWith("INPUT_CONSTRUCTOR_INVALID"))) {
                legacyQuery[entry.account] = true;
                sendQuery(entry, type, data, requestId, done);
                return;
            }
            done.run(res, err);
        });
    }

    /** Gram Wallet for this user where the widget may ask its bot (docs/sdk-v1.md §5.13): `context.wallet`. */
    private static boolean walletAvailable(Entry entry) {
        return entry.canQuery && !entry.preview && MessagesController.getInstance(entry.account).config.walletAvailable.get();
    }

    /**
     * UI: a wallet call (checked by the framework: its form, a tap or the grant, one call in flight): the wallet's own
     * window with the bot of the message; the outcome goes to the bot as the widget's query with the call's `data`, and
     * the widget gets the bot's answer (docs/sdk-v1.md §5.13).
     */
    private static void wallet(Entry entry, WidgetSandboxMessage message, long id, JSONObject call) {
        TLRPC.User bot = entry == null || entry.botId == 0 ? null : MessagesController.getInstance(entry.account).getUser(entry.botId);
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (entry == null || !walletAvailable(entry) || bot == null || !bot.bot) {
            message.answerRequest(id, WidgetSandboxSession.REQUEST_BAD_REQUEST, null);
            return;
        }
        if (fragment == null || fragment.getContext() == null) {
            message.answerRequest(id, WidgetSandboxSession.REQUEST_FAILED, null);
            return;
        }
        byte[] data = call.optString("data").getBytes(StandardCharsets.UTF_8);
        GramWalletWidget.open(fragment.getContext(), entry.account, bot, call, fragment.getResourceProvider(), new GramWalletWidget.Delegate() {
            @Override
            public void sendOutcome(TLRPC.WidgetQueryType queryType) {
                query(entry, message, id, queryType, data);
            }

            @Override
            public void rejected() {
                message.answerRequest(id, WidgetSandboxSession.REQUEST_REJECTED, null);
            }

            @Override
            public void badRequest() {
                message.answerRequest(id, WidgetSandboxSession.REQUEST_BAD_REQUEST, null);
            }

            @Override
            public void failed() {
                message.answerRequest(id, WidgetSandboxSession.REQUEST_FAILED, null);
            }
        });
    }

    /** UI: TelegramWidget.copyText on a tap: the clipboard, and a toast that says what was copied. */
    private static void copyText(String text) {
        if (TextUtils.isEmpty(text)) return;
        AndroidUtilities.addToClipboard(text);
        // Android 13+ shows the copied text itself; the toast would cover it.
        BaseFragment fragment = AndroidUtilities.shouldShowClipboardToast() ? LaunchActivity.getSafeLastFragment() : null;
        if (fragment == null) return;
        String shown = text.length() > 80 ? text.substring(0, 80) + "…" : text;
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.copy, LocaleController.formatString(R.string.WidgetTextCopied, shown)).show();
    }

    // ── a shared widget's shared state, polled while watched (docs/integration.md) ──

    /** The server's period when it gave none, and its bounds, in seconds. */
    private static final int POLL_DEFAULT_S = 30, POLL_MIN_S = 5, POLL_MAX_S = 3600;

    /** The polling of one shared widget (account and widget_id) while a widget of it on screen watches. */
    private static final class Poll {
        final int account;
        final long widgetId;
        long hash;
        int periodS = POLL_DEFAULT_S;
        int requestId;
        final Runnable run = () -> poll(this);

        Poll(int account, long widgetId) {
            this.account = account;
            this.widgetId = widgetId;
        }
    }

    /** UI: by "account/widgetId". */
    private static final HashMap<String, Poll> polls = new HashMap<>();

    /** UI: polls exactly the shared widgets with a watching widget on screen; the first poll after one period. */
    private static void updatePolls() {
        HashMap<String, Entry> wanted = new HashMap<>();
        for (Entry entry : entries.values()) {
            if (entry.widgetId == 0 || entry.preview || entry.shown == 0 || appPaused) continue;
            if (entry.message == null || !entry.message.watchesPublicData()) continue;
            wanted.put(entry.account + "/" + entry.widgetId, entry);
        }
        for (java.util.Iterator<java.util.Map.Entry<String, Poll>> i = polls.entrySet().iterator(); i.hasNext(); ) {
            java.util.Map.Entry<String, Poll> item = i.next();
            if (wanted.containsKey(item.getKey())) continue;
            Poll poll = item.getValue();
            AndroidUtilities.cancelRunOnUIThread(poll.run);
            if (poll.requestId != 0) ConnectionsManager.getInstance(poll.account).cancelRequest(poll.requestId, true);
            i.remove();
        }
        for (java.util.Map.Entry<String, Entry> item : wanted.entrySet()) {
            if (polls.containsKey(item.getKey())) continue;
            Entry entry = item.getValue();
            Poll poll = new Poll(entry.account, entry.widgetId);
            // The shared state the message came with: the server answers "not modified" while it holds.
            poll.hash = entry.publicDataHash;
            polls.put(item.getKey(), poll);
            AndroidUtilities.runOnUIThread(poll.run, poll.periodS * 1000L);
        }
    }

    /** UI: one carrier on screen of the shared widget is asked for its shared state; a new one goes to every widget of it. */
    private static void poll(Poll poll) {
        Entry carrier = null;
        for (Entry entry : entries.values()) {
            if (entry.account == poll.account && entry.widgetId == poll.widgetId && !entry.preview && entry.shown > 0 && entry.messageId > 0) {
                carrier = entry;
                break;
            }
        }
        if (carrier == null) {
            updatePolls();
            return;
        }
        TLRPC.TL_messages_getWidgetPublicData req = new TLRPC.TL_messages_getWidgetPublicData();
        req.peer = MessagesController.getInstance(carrier.account).getInputPeer(carrier.dialogId);
        req.msg_id = carrier.messageId;
        req.hash = poll.hash;
        poll.requestId = ConnectionsManager.getInstance(poll.account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            poll.requestId = 0;
            if (polls.get(poll.account + "/" + poll.widgetId) != poll) return;
            if (res != null) {
                poll.periodS = res.period <= 0 ? POLL_DEFAULT_S : Math.max(POLL_MIN_S, Math.min(POLL_MAX_S, res.period));
                if (res instanceof TLRPC.TL_messages_widgetPublicData) {
                    TLRPC.TL_messages_widgetPublicData changed = (TLRPC.TL_messages_widgetPublicData) res;
                    poll.hash = changed.hash;
                    onPublicData(poll.account, poll.widgetId, changed.data, changed.hash);
                }
            }
            AndroidUtilities.runOnUIThread(poll.run, poll.periodS * 1000L);
        });
    }

    /** UI: a shared widget's new shared state: every widget of it gets it, every message of it keeps it. */
    private static void onPublicData(int account, long widgetId, String data, long hash) {
        String publicData = text(data);
        for (Entry entry : entries.values()) {
            if (entry.account != account || entry.widgetId != widgetId || entry.preview) continue;
            if (TextUtils.equals(entry.publicData, publicData) && entry.publicDataHash == hash) continue;
            entry.publicDataHash = hash;
            applyData(entry, entry.chatData, publicData);
            if (entry.media != null) entry.media.public_data_hash = hash;
            MessagesStorage.getInstance(account).updateMessageWidgetData(entry.dialogId, entry.messageId, data == null ? "" : data, hash, null);
        }
    }

    /**
     * The widget's HTML from `payload`: a zlib stream (RFC 1950, the server's gzcompress level 9; docs/tgw-format.md,
     * "Inflating the payload"), or gzip (magic 1f 8b) in messages sent before the server switched to zlib.
     */
    private static String inflate(byte[] payload) {
        if (payload == null) return null;
        boolean gzip = payload.length >= 2 && (payload[0] & 0xFF) == 0x1F && (payload[1] & 0xFF) == 0x8B;
        try (InputStream input = gzip
                        ? new GZIPInputStream(new ByteArrayInputStream(payload))
                        : new InflaterInputStream(new ByteArrayInputStream(payload));
                ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length * 4)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            if (BuildVars.LOGS_ENABLED) FileLog.d("widget payload: " + (gzip ? "gzip" : "zlib") + ", " + payload.length + " -> " + output.size() + " bytes");
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    // ── what the widgets ask, UI thread ──

    private static final WidgetSandboxMessage.Events events = new WidgetSandboxMessage.Events() {
        @Override
        public void onDiagnostics(WidgetSandboxMessage message, JSONObject diagnostics) {
            String dead = diagnostics.optString("dead");
            if (TextUtils.isEmpty(dead)) return;
            Entry entry = entryOf(message);
            if (entry == null || entry.crashedSession == message.sessions()) return;
            entry.crashedSession = message.sessions();
            entry.crashes++;
            entry.dead = true;
            FileLog.d("widget " + message.file + " stopped (" + entry.crashes + "): " + dead);
            onMeasured(entry);
            checkShownHeights();
        }

        @Override
        public void onLoadedFrame(WidgetSandboxMessage message) {
            checkShownHeights();
            Entry entry = entryOf(message);
            if (entry != null) onMeasured(entry);
        }

        @Override
        public void onAction(WidgetSandboxMessage message, JSONObject action) {
            Entry entry = entryOf(message);
            String kind = action.optString("kind");
            if ("openLink".equals(kind) || "openTelegramLink".equals(kind)) {
                openLink(action.optString("url"), "openTelegramLink".equals(kind));
            } else if ("haptic".equals(kind)) {
                // Only a widget on screen plays one: one running off screen for its height is not seen (§2.7).
                View view = entry != null && entry.shown > 0 && entry.owner != null ? entry.owner.hostView() : null;
                if (view != null) {
                    String haptic = action.optString("haptic");
                    int feedback = "selection".equals(haptic) ? HapticFeedbackConstants.CLOCK_TICK
                            : "notification".equals(haptic) ? HapticFeedbackConstants.CONFIRM
                            : HapticFeedbackConstants.KEYBOARD_TAP;
                    view.performHapticFeedback(feedback);
                }
            }
        }

        @Override
        public void onRequest(WidgetSandboxMessage message, long id, String data, boolean wallet) {
            request(entryOf(message), message, id, data, wallet);
        }

        @Override
        public void onWallet(WidgetSandboxMessage message, long id, JSONObject call) {
            wallet(entryOf(message), message, id, call);
        }

        @Override
        public void onCopyText(WidgetSandboxMessage message, String text) {
            copyText(text);
        }

        @Override
        public void onWatchPublicData(WidgetSandboxMessage message) {
            updatePolls();
        }

        @Override
        public void onFailure(WidgetSandboxMessage message, String error) {
            FileLog.e("widget " + message.file + ": " + error);
        }

        @Override
        public void onResized(WidgetSandboxMessage message) {
            Entry entry = entryOf(message);
            if (entry == null || message.heightPx() <= 0) return;
            int width = css(message.frameWidthPx());
            int height = clamp(css(message.heightPx()));
            boolean anchorBottom = message.anchorBottom();
            if (width > 0 && entry.height == 0 && !entry.preview && heightsOf(entry.storageKey()).put(width, height, anchorBottom)) {
                MessagesStorage.getInstance(entry.account).putWidgetHeight(entry.dialogId, entry.messageId, width, height, anchorBottom ? 1 : 0, KEEP_WIDTHS);
            }
            if (entry.block != null) entry.block.onResizedAway("frame off screen");
        }

        @Override
        public void onStorageCommit(WidgetSandboxMessage message, String area, JSONObject values) {
            Entry entry = entryOf(message);
            if (entry == null) return;
            String key = entry.storageKey();
            JSONObject storage = storages.get(key);
            if (storage == null) storage = new JSONObject();
            try {
                storage.put(area, values);
            } catch (Exception ignored) {
            }
            storages.put(key, storage);
            // Every widget of this message (an edit is a new one) starts from it next time.
            for (Entry other : entries.values()) {
                if (other.message != null && key.equals(other.storageKey())) other.message.setSdk(locale(), storage.toString());
            }
            if ("local".equals(area)) {
                pendingWrites.put(key, entry);
                AndroidUtilities.cancelRunOnUIThread(writePending);
                AndroidUtilities.runOnUIThread(writePending, STORAGE_DELAY_MS);
            }
        }
    };

    /**
     * A link the widget asked to open from a real tap: confirmed with the full URL, then the browser or
     * the client's own t.me handling (docs/sdk-v1.md §5.2). One dialog at a time.
     */
    private static void openLink(String url, boolean telegram) {
        if (TextUtils.isEmpty(url) || linkDialogShown) return;
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();
        if (telegram) {
            Browser.openUrl(context, url);
            return;
        }
        linkDialogShown = true;
        AlertDialog dialog = new AlertDialog.Builder(context, fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.WidgetOpenLinkTitle))
                .setMessage(LocaleController.formatString(R.string.WidgetOpenLinkText, url))
                .setPositiveButton(LocaleController.getString(R.string.Open), (d, w) -> Browser.openUrl(context, url, true, false))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        dialog.setOnDismissListener(d -> linkDialogShown = false);
        if (fragment.showDialog(dialog) == null) linkDialogShown = false;
    }
}
