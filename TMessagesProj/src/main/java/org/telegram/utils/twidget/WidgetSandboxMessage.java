package org.telegram.utils.twidget;

import android.content.Context;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.WidgetSandboxPlayer;

/**
 * One widget: a chat's widget message (WidgetMedia). Outlives the Views that show it and owns, by
 * stage:
 *
 * <ol>
 *   <li>{@link #activate} (UI, shortly before it shows): scripts run; height changes happen while
 *       it is still off screen.
 *   <li>{@link #unload} (UI, far off screen): the session closes, the last frame stays as a
 *       snapshot with its height; {@link #dropSnapshot} keeps the height only.
 *   <li>{@link #activate} again: a fresh session runs with scripts; the snapshot shows until the
 *       widget has loaded.
 * </ol>
 *
 * Not a protocol or storage format: the storage and the size rules are WidgetMedia's.
 */
public final class WidgetSandboxMessage implements WidgetSandboxSession.Listener {
    /** UI-thread events for the screen. */
    public interface Events {
        void onDiagnostics(WidgetSandboxMessage message, JSONObject diagnostics);

        /** What the widget asked the client to do, checked (WidgetSandboxSession.Listener#onAction). */
        void onAction(WidgetSandboxMessage message, JSONObject action);

        void onFailure(WidgetSandboxMessage message, String error);

        /**
         * UI: the engine's process died under the session, which is unloaded now (its last frame stays). `restart`: start
         * the widget again, as a shown widget starts (WidgetMedia: through the block that shows it).
         */
        default void onEngineDied(WidgetSandboxMessage message, boolean restart) {
            if (restart) message.activate();
        }

        /** The widget's height or its resize anchor changed, whether or not a view shows it. */
        default void onResized(WidgetSandboxMessage message) {}

        /** The first frame of what the widget built on load was published (once per session): its height holds. */
        default void onLoadedFrame(WidgetSandboxMessage message) {}

        /** The new full content of a Web Storage area, "local" or "session" (docs/sdk-v1.md §5.4). */
        default void onStorageCommit(WidgetSandboxMessage message, String area, JSONObject values) {}

        /** A request to the bot (docs/sdk-v1.md §5.11, `wallet`: §5.13): answer it once with {@link #answerRequest}. */
        default void onRequest(WidgetSandboxMessage message, long id, String data, boolean wallet) {}

        /** A wallet call (docs/sdk-v1.md §5.13): answer it once with {@link #answerRequest}. */
        default void onWallet(WidgetSandboxMessage message, long id, JSONObject call) {}

        /** TelegramWidget.copyText on a tap. */
        default void onCopyText(WidgetSandboxMessage message, String text) {}

        /** The widget started or stopped watching its shared state ({@link #watchesPublicData}). */
        default void onWatchPublicData(WidgetSandboxMessage message) {}
    }

    public static final int PLACEHOLDER_DP = 160;

    public final int index;
    public final String file;
    public final String title;
    private final String html;
    private final Context context;
    private final int cpuMultiplier;
    private Events events;
    private WidgetSandboxSession session;
    private WidgetSandboxSession.Frame snapshot;
    private WidgetSandboxPlayer player;
    private int heightPx, widthPx;
    /** The width the last shown frame was laid out at: {@link #heightPx} belongs to it. */
    private int frameWidthPx;
    // The resize anchor (docs/sdk-v1.md §2.12) the last shown frame came with: kept with heightPx, across sessions.
    private boolean anchorBottom;
    // A widget off screen is hidden from its start (docs/sdk-v1.md §2.7): its load scripts run, its frames
    // and full-rate timers wait until it is shown. What it builds on load is still laid out and painted.
    private boolean dark, visible, active, paused;
    private int sessions;
    // The session whose loaded frame was told (Events#onLoadedFrame).
    private int loadedFrameSession;
    // SDK v1 (docs/sdk-v1.md §5): the chat theme as JSON (null: the scheme alone), the UI language,
    // the Web Storage preload of the next session.
    private String theme, locale, storage;
    // The widget's state, {"chatData","publicData"}, and where it is, {"isCopy","canQuery",…} (docs/sdk-v1.md §5.11).
    private String data = "{}", contextJson = "{}";
    // What the client draws over the widget (docs/sdk-v1.md §2.17): the cell's own numbers once one laid the message
    // out (exact), before that the layout's estimate; with the time over the widget or not.
    private volatile String safeArea;
    private boolean safeAreaExact, safeAreaOver;
    // The live session's latest watchPublicData() / unwatchPublicData(): poll the shared state while on.
    private boolean watchesPublicData;

    public WidgetSandboxMessage(
            Context context, int index, String file, String title, String html, boolean dark,
            int cpuMultiplier) {
        this.context = context.getApplicationContext();
        this.index = index;
        this.file = file;
        this.title = title;
        this.html = html;
        this.dark = dark;
        this.cpuMultiplier = cpuMultiplier;
    }

    private WidgetSandboxSession newSession() {
        WidgetSandboxSession created = theme == null && locale == null && storage == null
                ? new WidgetSandboxSession(context, html, dark, cpuMultiplier)
                : new WidgetSandboxSession(context, html, theme != null ? theme : WidgetSandboxTheme.scheme(dark), cpuMultiplier, locale, storage);
        created.setSdk("{\"data\":" + data + ",\"context\":" + contextJson + "}");
        created.setSafeArea(safeArea);
        return created;
    }

    /** Where the widget is, for every session: the server's flags, the chat type, the viewer, preview (fixed). */
    public void setContext(String context) {
        if (context != null) contextJson = context;
    }

    /** UI: the widget's full state, {"chatData","publicData"} (null for none); the live session gets it at once. */
    public void setData(String data) {
        if (data == null || data.equals(this.data)) return;
        this.data = data;
        if (session != null) session.setData(data);
    }

    /**
     * The time over the widget's bottom corner (docs/sdk-v1.md §2.17, §5.12): the band's height and the corner's width
     * in CSS px, 0 and 0 with a caption below the widget. An estimate (a layout before any cell) gives way to the cell's
     * exact numbers and is taken after them only when the time moves on or off the widget (a caption edited in or out).
     */
    public synchronized void setSafeArea(float bottom, float corner, boolean exact) {
        boolean over = bottom > 0;
        if (!exact && safeAreaExact && over == safeAreaOver) return;
        safeAreaExact = exact;
        safeAreaOver = over;
        String area = safeAreaJson(bottom, corner);
        if (area.equals(safeArea)) return;
        safeArea = area;
        // Any thread (a layout's estimate); the session is the UI's.
        AndroidUtilities.runOnUIThread(() -> {
            if (session != null) session.setSafeArea(safeArea);
        });
    }

    /** The content safe area JSON, the corner on the time's side: the right one, as the chat draws it. */
    static String safeAreaJson(float bottom, float corner) {
        return String.format(java.util.Locale.US,
                "{\"top\":0,\"right\":0,\"bottom\":%.2f,\"left\":0,\"cornerLeft\":0,\"cornerRight\":%.2f}", bottom, corner);
    }

    /** UI: the client's answer to a request ({@link Events#onRequest}); see WidgetSandboxSession#answerRequest. */
    public void answerRequest(long id, int failure, String result) {
        if (session != null) session.answerRequest(id, failure, result);
    }

    /** The live session watches its shared state (TelegramWidget.watchPublicData). */
    public boolean watchesPublicData() {
        return session != null && watchesPublicData;
    }

    /** The UI language and this message's Web Storage, for the sessions started from now on. */
    public void setSdk(String locale, String storage) {
        this.locale = locale;
        this.storage = storage;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    /** UI: runs the widget's scripts, starting a new session when the old one was unloaded. */
    public void activate() {
        if (active) return;
        // Kill switch: nothing starts, the last frame (if any) stays as it is.
        if (WidgetSandboxSession.disabledByConfig()) return;
        active = true;
        if (session != null) {
            session.releaseScripts();
            return;
        }
        WidgetSandboxSession created = newSession();
        created.setListener(this);
        created.setVisible(visible && !paused);
        session = created;
        sessions++;
        int width = widthPx;
        WidgetSandboxResources.IO.execute(
                () -> {
                    try {
                        created.start(width, false);
                    } catch (Exception ignored) {
                        // Reported through onFailure on UI.
                    }
                });
    }

    /** UI: closes the session; its last frame stays as the snapshot. */
    public void unload() {
        active = false;
        if (session == null) return;
        WidgetSandboxSession.Frame frame = frame();
        if (frame != null) snapshot = frame;
        session.close();
        session = null;
        if (watchesPublicData) {
            watchesPublicData = false;
            if (events != null) events.onWatchPublicData(this);
        }
    }

    /** UI: only the height remains. */
    public void dropSnapshot() {
        if (session == null) snapshot = null;
    }

    /** UI: back to a fresh start the next time it is activated. */
    public void reload() {
        unload();
        snapshot = null;
    }

    /** UI, development: as never shown, without a frame or a height (WidgetMedia#onHeightsCleared). */
    public void forget() {
        reload();
        heightPx = 0;
        frameWidthPx = 0;
        anchorBottom = false;
    }

    public void release() {
        unload();
        snapshot = null;
        player = null;
        events = null;
    }

    /** The live frame once the widget loaded (or when there is nothing else), else the snapshot. */
    public WidgetSandboxSession.Frame frame() {
        WidgetSandboxSession.Frame live = session == null ? null : session.frame();
        if (live != null && (snapshot == null || session.loaded())) return live;
        if (snapshot != null && Math.abs(snapshot.width - widthPx) <= 1) return snapshot;
        // At another width the live frame comes soon; until then any frame beats none (drawn scaled).
        return live != null ? live : snapshot;
    }

    public boolean dark() {
        return dark;
    }

    public int heightPx() {
        return heightPx;
    }

    /** The width {@link #heightPx} was measured at, 0 before the first frame. */
    public int frameWidthPx() {
        return frameWidthPx;
    }

    /**
     * The edge of the bubble that stays put when {@link #heightPx} changes (TelegramWidget.setResizeAnchor,
     * docs/sdk-v1.md §2.12): true for `bottom`, false for `top` (the default, also before the first frame).
     */
    public boolean anchorBottom() {
        return anchorBottom;
    }

    /** The live session's frame shows what the widget built on load (WidgetSandboxSession#hasLoadedFrame). */
    public boolean hasLoadedFrame() {
        return session != null && session.hasLoadedFrame();
    }

    /** A chat theme was given ({@link #setTheme}); without one the widget has the plain scheme. */
    public boolean hasTheme() {
        return theme != null;
    }

    /** Its scripts run: {@link #activate}d and not unloaded since. */
    public boolean isActive() {
        return active;
    }

    public boolean hasSession() {
        return session != null;
    }

    public boolean scriptsRunning() {
        return session != null && session.scriptsReleased();
    }

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    /** Sessions created so far, the first included. */
    public int sessions() {
        return sessions;
    }

    public WidgetSandboxSession session() {
        return session;
    }

    public void setWidthPx(int width) {
        if (width <= 0 || width == widthPx) return;
        widthPx = width;
        if (session != null) session.setWidthPx(width);
    }

    public void setDarkTheme(boolean dark) {
        this.dark = dark;
        if (session != null) session.setDarkTheme(dark);
    }

    /** A {@link WidgetSandboxTheme} JSON: the scheme, the palette and the button tokens. */
    public void setTheme(String theme) {
        if (theme == null || theme.equals(this.theme)) return;
        this.theme = theme;
        dark = theme.contains("\"dark\"");
        if (session != null) session.setTheme(theme);
    }

    /** The app is in the background: the widget pauses as off screen, whatever its view does. */
    void setPaused(boolean paused) {
        if (this.paused == paused) return;
        this.paused = paused;
        if (session != null) session.setVisible(visible && !paused);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (session != null) session.setVisible(visible && !paused);
    }

    public void bind(WidgetSandboxPlayer player) {
        this.player = player;
    }

    public void unbind(WidgetSandboxPlayer player) {
        if (this.player == player) this.player = null;
    }

    public void ensureDisplayList() {
        if (session != null) session.ensureDisplayList();
    }

    public void touch(int action, float x, float y, int pointer, boolean primary, boolean mouse, int modifiers, int button) {
        if (session != null) session.touch(action, x, y, pointer, primary, mouse, modifiers, button);
    }

    public void hover(int action, float x, float y, int modifiers) {
        if (session != null) session.hover(action, x, y, modifiers);
    }

    public String failure() {
        return session == null ? null : session.failure();
    }

    // ── session events, UI thread ──

    @Override
    public void onFrame(WidgetSandboxSession from) {
        if (from != session) return;
        WidgetSandboxSession.Frame live = from.frame();
        if (live == null) return;
        boolean shown = snapshot == null || from.loaded();
        if (!shown) return; // The snapshot keeps its place until the new session has loaded.
        snapshot = null;
        frameWidthPx = live.width;
        boolean anchored = live.anchorBottom != anchorBottom;
        anchorBottom = live.anchorBottom;
        int height = Math.max(1, live.height);
        boolean resized = height != heightPx;
        heightPx = height;
        if (player != null) player.onMessageFrame(resized);
        // The anchor is kept with the height (WidgetMedia's cache): a new one alone is told too.
        if ((resized || anchored) && events != null) events.onResized(this);
        // Loaded at the height it had: the block still learns that this height is the loaded widget's.
        if (loadedFrameNow() && !resized && player != null) player.onMessageFrame(true);
    }

    /** UI: true once per session, when its loaded frame is first seen published; Events#onLoadedFrame then. */
    private boolean loadedFrameNow() {
        if (loadedFrameSession == sessions || session == null || !session.hasLoadedFrame()) return false;
        loadedFrameSession = sessions;
        if (events != null) events.onLoadedFrame(this);
        return true;
    }

    @Override
    public void onGesture(boolean x, boolean y, boolean hold) {
        if (player != null) player.onGesture(x, y, hold);
    }

    @Override
    public void onRequest(long id, String data, boolean wallet) {
        if (events != null) events.onRequest(this, id, data, wallet);
        else answerRequest(id, WidgetSandboxSession.REQUEST_FAILED, null);
    }

    @Override
    public void onWallet(long id, JSONObject call) {
        if (events != null) events.onWallet(this, id, call);
        else answerRequest(id, WidgetSandboxSession.REQUEST_FAILED, null);
    }

    @Override
    public void onCopyText(String text) {
        if (events != null) events.onCopyText(this, text);
    }

    @Override
    public void onWatchPublicData(boolean on) {
        if (watchesPublicData == on) return;
        watchesPublicData = on;
        if (events != null) events.onWatchPublicData(this);
    }

    @Override
    public void onCursor(String cursor) {
        if (player != null) player.onCursor(cursor);
    }

    @Override
    public void onDiagnostics(JSONObject diagnostics) {
        if (events != null) events.onDiagnostics(this, diagnostics);
        // A loaded widget's frame may already be waiting behind the snapshot.
        if (diagnostics.optBoolean("loaded") && snapshot != null && session != null)
            onFrame(session);
        // The frame shown may already be the loaded widget's (no new one comes): its height counts now.
        else if (loadedFrameNow() && player != null)
            player.onMessageFrame(true);
    }

    // Engine processes that died under this message's sessions in the minute since `engineDeathsSince`.
    private int engineDeaths;
    private long engineDeathsSince;

    /**
     * The engine's process died (WidgetRemoteEngine): a widget there (this bot's, or any in the shared one) may have
     * crashed it, or the system reclaimed it. A shown widget starts again from its last frame, twice a minute at
     * most; then it stays as it was.
     */
    @Override
    public void onEngineDied() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - engineDeathsSince > 60_000) {
            engineDeathsSince = now;
            engineDeaths = 0;
        }
        boolean restart = active && engineDeaths++ < 2;
        unload();
        if (events != null) events.onEngineDied(this, restart);
        else if (restart) activate();
        if (!restart) onFailure("Widget engine stopped");
    }

    @Override
    public void onAction(JSONObject action) {
        if (events != null) events.onAction(this, action);
    }

    @Override
    public void onStorageCommit(String area, JSONObject values) {
        if (events != null) events.onStorageCommit(this, area, values);
    }

    @Override
    public void onFailure(String message) {
        if (events != null) events.onFailure(this, message);
        if (player != null) player.onMessageFailure();
    }
}
