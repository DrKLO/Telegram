package org.telegram.utils.twidget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.view.Choreographer;

import org.json.JSONObject;
import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One native widget session (WidgetHost + TwidgetCore) without a View: it lives while its message
 * is near the screen, records frames on its worker and publishes immutable recorded frames (WidgetFrameNode) to UI.
 * A View binds to it only for drawing and input. Single use: once closed, a new session replaces it.
 *
 * <p>Threads: {@link #start} may run on any background thread; everything else is UI-thread API.
 * Worker callbacks ({@link #onWorkerSignal}, {@link #onWorkerFrame}) never touch UI state directly.
 */
public final class WidgetSandboxSession {
    /** UI-thread notifications for whoever shows the session. */
    public interface Listener {
        /** A new frame was published; its height may differ from the previous one. */
        void onFrame(WidgetSandboxSession session);

        /**
         * What the widget claims of the press that just started, from `touch-action` (docs/sdk-v1.md §5.5):
         * horizontal drags (`x`) and holds (`hold`: no message menu, it gets contextmenu); `y` is always false
         * since v0.1.3 (vertical drags are the chat's).
         */
        void onGesture(boolean x, boolean y, boolean hold);

        /** The CSS cursor where the mouse is over the widget: "pointer", "text", …, "auto" when it says nothing. */
        default void onCursor(String cursor) {}

        /**
         * A request to the bot, checked (docs/sdk-v1.md §5.11): `data` as TelegramWidget.request sent it; `wallet`: it
         * asks for the user's wallet card (`with_wallet`, §5.13). Answer it exactly once with {@link #answerRequest};
         * until then the widget's next request is BUSY.
         */
        default void onRequest(long id, String data, boolean wallet) {}

        /**
         * A wallet call, checked (docs/sdk-v1.md §5.13; only with `context.wallet`): {"method":"sendTransaction"|
         * "signData","transaction"|"payload",…,"data"} in TON Connect's field names. Answer it exactly once with
         * {@link #answerRequest}, the bot's answer to the outcome or a failure ({@link #REQUEST_REJECTED},
         * {@link #REQUEST_BAD_REQUEST} or a request's).
         */
        default void onWallet(long id, JSONObject call) {}

        /** TelegramWidget.copyText on a tap: copy it and show what was copied. */
        default void onCopyText(String text) {}

        /** The widget watches its shared state (watchPublicData) or stopped: poll it only while on and shown. */
        default void onWatchPublicData(boolean on) {}

        void onDiagnostics(JSONObject diagnostics);

        /**
         * What the widget asks the client to do, already checked: {"kind":"openLink"|"openTelegramLink",
         * "url"}, {"kind":"haptic","haptic","value"?} or {"kind":"ready"} (docs/sdk-v1.md §5.2, §5.3).
         */
        default void onAction(JSONObject action) {}

        /** The new full content of a Web Storage area, "local" or "session" (docs/sdk-v1.md §5.4). */
        default void onStorageCommit(String area, JSONObject values) {}

        void onFailure(String message);

        /**
         * The isolated engine's process died with this session in it (the widget's own code or the system ended it):
         * the session is over, its last frame stays. A new session starts the widget afresh.
         */
        default void onEngineDied() {
            onFailure("Widget engine stopped");
        }
    }

    /** The answers to a request or a wallet call ({@link #answerRequest}); the last two only for a wallet call. */
    public static final int REQUEST_ANSWERED = 0, REQUEST_TIMEOUT = 1, REQUEST_RATE_LIMITED = 2, REQUEST_FAILED = 3,
            REQUEST_REJECTED = 4, REQUEST_BAD_REQUEST = 5;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final WidgetSandboxBackend backend = new WidgetSandboxBackend(this);
    // The isolated process's or this one's (WidgetEngine#isolated when the session was made).
    private final WidgetEngine engine = WidgetEngine.current();
    // The client's own check of the engine's actions and requests, by the context setSdk gave.
    private volatile WidgetGate gate = new WidgetGate(null);
    private final Context context;
    private final String html;
    private final int cpuMultiplier;
    private final String locale, storage;
    // The widget's state and context at creation (docs/sdk-v1.md §5.11): {"data":{..},"context":{..}}.
    private volatile String sdk = "{}";
    // What the client draws over the widget (docs/sdk-v1.md §2.17), {"bottom","cornerRight",…} in CSS px; null for none.
    private volatile String safeArea;
    final float density;
    private final long clockOrigin = System.nanoTime();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean tickInFlight = new AtomicBoolean();
    private final AtomicBoolean frameWanted = new AtomicBoolean();
    // Native animations: every frame while `animating`, once when a delayed wake is due.
    private final AtomicBoolean animating = new AtomicBoolean();
    private final AtomicBoolean animationDue = new AtomicBoolean();
    private final Object publicationLock = new Object();
    // The height of the frame recorded last, px; whether one was recorded.
    private volatile int lastFrameHeight;
    private volatile boolean framed;
    private volatile long session;
    private volatile boolean closed;
    private volatile boolean loaded;
    // The renderer applied what the widget built on load (WidgetHost's `rendered`): a frame recorded from
    // then on is the loaded widget's (Frame#loaded); so is the last one before, when it has the height the
    // renderer reported then (`renderedHeight`).
    private volatile boolean rendered, renderedHeight;
    // TelegramWidget.setResizeAnchor (docs/sdk-v1.md §2.12): the bottom edge stays put. The host reports it before
    // the ops whose size it anchors, so the frame recorded next carries it (Frame#anchorBottom).
    private volatile boolean anchorBottom;
    private String theme;
    private boolean dark, scriptsReleased, frameScheduled, repaintPending;
    // Read by start() on its background thread: the widget boots shown or hidden.
    private volatile boolean visible;
    private int widthPx;
    private Frame current, pending;
    private JSONObject pendingDiagnostics;
    private boolean publicationScheduled;
    private Listener listener;
    // Debug: the conformance runner's ear for `data-conformance` (WidgetConformanceRunner).
    private volatile Utilities.Callback<String> conformanceListener;
    // Debug: the stress runner's ear for `data-stress` (16) and the validated ops of every batch (17), on the binder thread.
    private volatile Utilities.Callback2<Integer, String> stressListener;
    // Debug: the stress runner's ear for each recorded frame's work, ms: receive, animate, paint and serialise (native,
    // signal 18), then parse and canvas (WidgetSandboxBackend#record). On the worker.
    private volatile Utilities.Callback<float[]> workListener;
    private float[] nativeWork;
    // Debug: the attack runner's ear for leaks (WidgetAttackRunner).
    private volatile Utilities.Callback<String> attackListener;
    // Outlives close(): commits on their way while the native host is destroyed still reach it.
    private Listener storageListener;
    private String failure;

    // The last recorded display list (binary, WidgetDisplayList) and composition (JSON), for the test screen's dumps.
    volatile byte[] lastList, lastComposition;

    // UI-readable metrics of the last published frame. None of these measure GPU completion.
    public float layoutMs, recordMs;
    public int layoutPasses, nativeBuilds, displayOps, records;
    public JSONObject diagnostics = new JSONObject();

    /** Debug: `{"list": display list, "composition": layers}` of the last recorded frame, or null. */
    public String dump() {
        byte[] list = lastList, composition = lastComposition;
        if (list == null) return null;
        return "{\"list\":"
                + WidgetDisplayList.toJson(list)
                + ",\"composition\":"
                + (composition == null ? "null" : new String(composition, StandardCharsets.UTF_8))
                + "}";
    }

    private final Runnable publish = this::publishPending;
    private final Runnable wake = this::scheduleFrame;
    private final Runnable animationWake =
            () -> {
                animationDue.set(true);
                scheduleFrame();
            };
    private final Choreographer.FrameCallback frameCallback =
            nanos -> {
                frameScheduled = false;
                long id = session;
                if (closed || id == 0 || !tickInFlight.compareAndSet(false, true)) return;
                // Off screen only paints (sizes stay current); frames and animations wait for it.
                boolean animate = visible && (animationDue.getAndSet(false) | animating.get());
                boolean frame = visible && frameWanted.getAndSet(false);
                if (frame) frameSentAt = nanos;
                engine.tick(
                        id, (nanos - clockOrigin) / 1e6f, frame, animate, dirty.getAndSet(false));
            };
    // When the last rAF frame went to the widget (System.nanoTime, as Choreographer's frame times).
    private long frameSentAt;
    /**
     * The widget asked for a frame (requestAnimationFrame). It answers its frame with ops and asks for the next one
     * a few ms later, which mostly lands after the vsync that would have taken it: waiting for the next one lost
     * every other vsync. So when no frame went to the widget in this vsync's interval, it gets one now; paints and
     * animations stay on vsync, and the widget still gets at most one frame per interval.
     */
    private final Runnable frameRequested =
            () -> {
                long id = session;
                long now = System.nanoTime();
                long period = (long) (1e9 / Math.max(30, AndroidUtilities.screenRefreshRate));
                if (closed || id == 0 || !visible || !frameWanted.get() || now - frameSentAt < period * 9 / 10
                        || !tickInFlight.compareAndSet(false, true)) {
                    scheduleFrame();
                    return;
                }
                frameWanted.set(false);
                frameSentAt = now;
                engine.tick(id, (now - clockOrigin) / 1e6f, true, false, false);
            };

    public WidgetSandboxSession(Context context, String html, boolean dark, int cpuMultiplier) {
        this(context, html, WidgetSandboxTheme.scheme(dark), cpuMultiplier, null, null);
    }

    /**
     * @param theme a {@link WidgetSandboxTheme} JSON
     * @param locale the UI language, BCP 47; null for en-US
     * @param storage this message's Web Storage, {"local":{..},"session":{..}}; null for empty
     */
    public WidgetSandboxSession(Context context, String html, String theme, int cpuMultiplier, String locale, String storage) {
        if (cpuMultiplier < 1 || cpuMultiplier > 4)
            throw new IllegalArgumentException("CPU multiplier must be 1..4");
        this.context = context.getApplicationContext();
        this.html = html;
        this.theme = theme;
        this.dark = theme.contains("\"dark\"");
        this.cpuMultiplier = cpuMultiplier;
        this.locale = locale == null ? "en-US" : locale;
        this.storage = storage == null ? "{}" : storage;
        density = context.getResources().getDisplayMetrics().density;
    }

    /**
     * Before {@link #start}: the widget's state and where it is, {"data":{"chatData","publicData"},"context":{"isCopy",
     * "canQuery","backgroundQuery","chatType","viewer","preview"}} (WidgetMedia#sdk). Without it: no state, no requests.
     */
    public void setSdk(String json) {
        if (json == null) return;
        sdk = json;
        try {
            gate = new WidgetGate(new JSONObject(json).optJSONObject("context"));
        } catch (Exception e) {
            gate = new WidgetGate(null);
        }
    }

    /**
     * What the client draws over the widget, {"top","right","bottom","left","cornerLeft","cornerRight"} in CSS px
     * (docs/sdk-v1.md §2.17): before {@link #start} for the boot, after it only when the bubble's layout changes.
     */
    void setSafeArea(String area) {
        if (closed || area == null || area.equals(safeArea)) return;
        safeArea = area;
        long id = session;
        if (id != 0) engine.safeArea(id, area.getBytes(StandardCharsets.UTF_8));
    }

    /** The widget's full state changed: {"chatData","publicData"}, null for none. */
    void setData(String data) {
        long id = session;
        if (!closed && id != 0) engine.data(id, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The client's answer to request or wallet call `request` ({@link Listener#onRequest}, {@link Listener#onWallet}):
     * see WidgetSandboxNative#answerRequest.
     */
    void answerRequest(long request, int failure, String result) {
        gate.answered(failure == REQUEST_ANSWERED);
        long id = session;
        if (!closed && id != 0) engine.answerRequest(id, request, failure, result == null ? null : result.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Debug, private builds only (the runners' `--ez inProcess true`): sessions made from now on run their engine in
     * this process, as before the isolated one, for comparisons. Every other build always isolates. On the API 28
     * emulator an in-process run right after an isolated one in the same app process got no verdicts (2026-11-08,
     * not seen on API 23, the Fold or the Redmi; not found): start such a run in a fresh process.
     */
    public static void setInProcessEngine(boolean inProcess) {
        WidgetEngine.isolated = !(inProcess && org.telegram.messenger.BuildVars.DEBUG_PRIVATE_VERSION);
    }

    /**
     * Starts the isolated engine now and keeps it (WidgetRemoteEngine#prewarm): called once the app is up. Not on
     * low-RAM devices, where the engine starts with the first widget and goes 30 s after the last; not while the kill
     * switch is on. Background thread.
     */
    public static void prewarmEngine(Context context) {
        if (!WidgetEngine.isolated || disabledByConfig()) return;
        android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null || activityManager.isLowRamDevice()) return;
        try {
            WidgetRemoteEngine.INSTANCE.prewarm(context);
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
        // An engine that died while the app was in the background is bound again when it comes back.
        if (!foregroundWatched) {
            org.telegram.ui.Components.ForegroundDetector detector = org.telegram.ui.Components.ForegroundDetector.getInstance();
            if (detector != null) {
                foregroundWatched = true;
                detector.addListener(new org.telegram.ui.Components.ForegroundDetector.Listener() {
                    @Override
                    public void onBecameForeground() {
                        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> prewarmEngine(context));
                    }

                    @Override
                    public void onBecameBackground() {
                    }
                });
            }
        }
    }

    private static boolean foregroundWatched;

    /**
     * The kill switch (AppGlobalConfig#widgetsDisabled): true when the config of any logged-in account turns
     * widgets off. No native session is created then, so no widget code runs in the client. Any thread.
     */
    public static boolean disabledByConfig() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && AppGlobalConfig.getInstance(a).widgetsDisabled.get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the native session on the calling background thread. `holdScripts` is kept for the
     * test screens: widget-sandbox v0.1.0 has no held boot, the scripts always run at once.
     */
    public void start(int widthPx, boolean holdScripts) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Start WidgetSandboxSession off UI");
        // The last gate before any widget code runs: every session, the chat's and the debug runners', comes here.
        if (disabledByConfig()) throw new IllegalStateException("Widgets are disabled by the app config");
        // Set before the session exists: nothing reads it until the creator hands this object over.
        if (this.widthPx == 0) this.widthPx = widthPx;
        Trace.beginSection("WidgetSandbox:Create");
        long id = 0;
        try {
            if (closed) return;
            final String createdSafeArea = safeArea;
            String sdk = this.sdk;
            if (createdSafeArea != null) sdk = new JSONObject(sdk).put("contentSafeArea", new JSONObject(createdSafeArea)).toString();
            id =
                    engine.create(
                            context,
                            html,
                            theme,
                            widthPx / density,
                            density,
                            cpuMultiplier,
                            locale,
                            storage,
                            sdk,
                            LocaleController.getString(R.string.WidgetStopped),
                            visible,
                            backend);
            final String createdTheme = theme;
            final long created = id;
            main.post(
                    () -> {
                        if (closed) {
                            destroyInBackground(created);
                            return;
                        }
                        session = created;
                        if (this.widthPx != widthPx && this.widthPx > 0)
                            engine.width(created, this.widthPx / density);
                        if (!this.theme.equals(createdTheme))
                            engine.theme(created, this.theme.getBytes(StandardCharsets.UTF_8));
                        if (safeArea != null && !safeArea.equals(createdSafeArea))
                            engine.safeArea(created, safeArea.getBytes(StandardCharsets.UTF_8));
                        scriptsReleased = true;
                        engine.visible(created, visible);
                        scheduleFrame();
                    });
        } catch (Exception error) {
            if (id != 0) destroyInBackground(id);
            main.post(
                    () -> {
                        failure = error.toString();
                        if (!closed && listener != null) listener.onFailure(failure);
                    });
            throw error;
        } finally {
            Trace.endSection();
        }
    }

    /** Debug: leaks, on the bridge's threads, for a session made while WidgetSandboxNative#attackScan is on. */
    void setAttackListener(Utilities.Callback<String> listener) {
        attackListener = listener;
    }

    /** Debug: `data-conformance` values on the UI thread, while WidgetSandboxNative#conformance is on. */
    void setStressListener(Utilities.Callback2<Integer, String> listener) {
        stressListener = listener;
    }

    void setWorkListener(Utilities.Callback<float[]> listener) {
        workListener = listener;
    }

    /** Worker: the Java part of a recorded frame's work, after its native part (signal 18). */
    void onRecorded(float parseMs, float canvasMs) {
        Utilities.Callback<float[]> listener = workListener;
        float[] work = nativeWork;
        nativeWork = null;
        if (listener == null || work == null) return;
        work[4] = parseMs;
        work[5] = canvasMs;
        listener.run(work);
    }

    void setConformanceListener(Utilities.Callback<String> listener) {
        conformanceListener = listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) storageListener = listener;
    }

    /** Kept for the test screens: the scripts run at start (no held boot in v0.1.0). */
    public void releaseScripts() {
        scriptsReleased = true;
    }

    public boolean scriptsReleased() {
        return scriptsReleased;
    }

    /** The widget's scripts ran and `load` fired. */
    public boolean loaded() {
        return loaded;
    }

    /** UI: the frame published last shows what the widget built on load: its height is the loaded widget's. */
    public boolean hasLoadedFrame() {
        Frame frame = current;
        return frame != null && (frame.loaded || renderedHeight);
    }

    public void setWidthPx(int width) {
        if (width <= 0 || width == widthPx || closed) return;
        widthPx = width;
        long id = session;
        if (id != 0) engine.width(id, width / density);
    }

    public int widthPx() {
        return widthPx;
    }

    public void setDarkTheme(boolean dark) {
        if (this.dark == dark || closed) return;
        setTheme(WidgetSandboxTheme.scheme(dark));
    }

    /** A {@link WidgetSandboxTheme} JSON; the widget gets `themeChanged` when it differs. */
    public void setTheme(String theme) {
        if (closed || theme == null || theme.equals(this.theme)) return;
        this.theme = theme;
        dark = theme.contains("\"dark\"");
        long id = session;
        if (id != 0) engine.theme(id, theme.getBytes(StandardCharsets.UTF_8));
    }

    /** On screen: animation frames, rAF and full-rate timers; off screen only layout changes paint. */
    public void setVisible(boolean visible) {
        if (closed || this.visible == visible) return;
        this.visible = visible;
        long id = session;
        if (id != 0) engine.visible(id, visible);
        if (visible) scheduleFrame();
    }

    public String failure() {
        return failure;
    }

    /**
     * The last published frame, of the current width or, while one at a new width is recorded, of the
     * one before (the player scales it): the widget is never blank through a width change.
     */
    Frame frame() {
        return current;
    }

    /** UI: a bound View found the published nodes without display lists; record them again. */
    void ensureDisplayList() {
        if (closed || session == 0 || repaintPending) return;
        Frame frame = current;
        if (frame != null && frameMatchesWidth(frame) && frame.recorded()) return;
        repaintPending = true;
        // A normal dirty tick cannot repaint a clean native document. Explicitly request it.
        engine.repaint(session);
    }

    void touch(int action, float x, float y, int pointer, boolean primary) {
        touch(action, x, y, pointer, primary, false, 0, 0);
    }

    void touch(int action, float x, float y, int pointer, boolean primary, boolean mouse, int modifiers, int button) {
        if (action == 0 && visible) gate.press();
        long id = session;
        if (!closed && id != 0) engine.touch(id, action, x, y, pointer, primary, mouse, modifiers, button);
    }

    void hover(int action, float x, float y, int modifiers) {
        long id = session;
        if (!closed && id != 0) engine.hover(id, action, x, y, modifiers);
    }

    private boolean frameMatchesWidth(Frame frame) {
        return widthPx == 0 || Math.abs(frame.width - widthPx) <= 1;
    }

    private void scheduleFrame() {
        if (closed
                || session == 0
                || frameScheduled
                || tickInFlight.get()
                || !(dirty.get()
                        || visible
                                && (frameWanted.get() || animating.get() || animationDue.get())))
            return;
        frameScheduled = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    // Called on a native worker. Native callbacks never access UI-thread state.
    void onWorkerSignal(int kind, String message) {
        if (kind == 8) {
            if (!WidgetGate.storageCommit(message)) return;
            // Storage commits are kept even after close(): the widget's last state is worth keeping.
            main.post(
                    () -> {
                        if (storageListener == null) return;
                        try {
                            JSONObject commit = new JSONObject(message);
                            JSONObject values = commit.optJSONObject("values");
                            storageListener.onStorageCommit(commit.optString("area"), values == null ? new JSONObject() : values);
                        } catch (Exception ignored) {
                        }
                    });
            return;
        }
        if (kind == 10) {
            // Debug (WidgetSandboxNative#attackScan on): a URL that could reach the network.
            Utilities.Callback<String> report = attackListener;
            if (report != null) report.run(message);
            return;
        }
        if (closed) return;
        if (kind == 12 || kind == 19) {
            long id;
            String data = null;
            JSONObject call = null;
            boolean wallet = false;
            try {
                JSONObject request = new JSONObject(message);
                id = request.getLong("id");
                if (kind == 19) call = request.getJSONObject("call");
                else {
                    data = request.getString("data");
                    wallet = request.optBoolean("wallet");
                }
            } catch (Exception ignored) {
                return;
            }
            boolean allowed = kind == 19 ? gate.walletCall(call.toString(), visible) : gate.request(data, wallet, visible);
            if (!allowed) {
                long engineId = session;
                if (engineId != 0) engine.answerRequest(engineId, id, REQUEST_FAILED, null);
                return;
            }
            final String requestData = data;
            final JSONObject walletCall = call;
            final boolean withWallet = wallet;
            main.post(
                    () -> {
                        if (closed || listener == null) return;
                        if (kind == 19) listener.onWallet(id, walletCall);
                        else listener.onRequest(id, requestData, withWallet);
                    });
            return;
        }
        if (kind == 13 || kind == 14) {
            if (kind == 13 && !(WidgetGate.copyText(message) && gate.privileged(visible))) return;
            main.post(
                    () -> {
                        if (closed || listener == null) return;
                        if (kind == 13) listener.onCopyText(message);
                        else listener.onWatchPublicData("1".equals(message));
                    });
            return;
        }
        if (kind == 11) {
            main.post(
                    () -> {
                        if (!closed && listener != null) listener.onCursor(message);
                    });
            return;
        }
        if (kind == 18) {
            try {
                JSONObject work = new JSONObject(message);
                nativeWork = new float[] {
                        (float) work.optDouble("receive"), (float) work.optDouble("animate"),
                        (float) work.optDouble("paint"), (float) work.optDouble("serialize"), 0, 0,
                        (float) work.optDouble("ticks"), (float) work.optDouble("framesSent"),
                        (float) work.optDouble("opsLatency"), (float) work.optDouble("validate", -1),
                        (float) work.optDouble("hop", -1)};
            } catch (Exception ignored) {
            }
            return;
        }
        if (kind == 16 || kind == 17) {
            Utilities.Callback2<Integer, String> stress = stressListener;
            if (stress != null) stress.run(kind, message);
            return;
        }
        if (kind == 9) {
            // Debug (WidgetNative#conformance on): the widget's `data-conformance` verdict.
            Utilities.Callback<String> report = conformanceListener;
            if (report != null) main.post(() -> report.run(message));
            return;
        }
        if (kind == 6) {
            tickInFlight.set(false);
            main.post(wake);
        } else if (kind == 1) {
            frameWanted.set(true);
            main.post(frameRequested);
        } else if (kind == 2) {
            dirty.set(true);
            main.removeCallbacks(wake);
            main.post(wake);
        } else if (kind == 3) {
            boolean x, y, hold;
            try {
                JSONObject claim = new JSONObject(message);
                x = claim.optBoolean("x");
                y = claim.optBoolean("y");
                hold = claim.optBoolean("hold");
            } catch (Exception ignored) {
                return;
            }
            main.post(
                    () -> {
                        if (!closed && listener != null) listener.onGesture(x, y, hold);
                    });
        } else if (kind == 4) {
            try {
                JSONObject value = new JSONObject(message);
                if (value.optBoolean("loaded")) loaded = true;
                anchorBottom = "bottom".equals(value.optString("resizeAnchor"));
                if (value.optBoolean("rendered") && !rendered) {
                    double height = value.optDouble("height", -1);
                    double last = Math.min(600, Math.max(50, lastFrameHeight / density));
                    if (framed && height > 0 && Math.abs(last - height) <= 1) renderedHeight = true;
                    rendered = true;
                }
                synchronized (publicationLock) {
                    if (closed) return;
                    pendingDiagnostics = value;
                    schedulePublicationLocked();
                }
            } catch (Exception ignored) {
            }
        } else if (kind == 5) {
            JSONObject action;
            try {
                action = new JSONObject(message);
            } catch (Exception ignored) {
                return;
            }
            String actionKind = action.optString("kind");
            boolean allowed;
            if ("openLink".equals(actionKind) || "openTelegramLink".equals(actionKind)) {
                allowed = WidgetGate.link(action.optString("url"), "openTelegramLink".equals(actionKind)) && gate.privileged(visible);
            } else if ("haptic".equals(actionKind)) {
                allowed = gate.haptic(visible);
            } else {
                allowed = "ready".equals(actionKind);
            }
            if (!allowed) return;
            main.post(
                    () -> {
                        if (!closed && listener != null) listener.onAction(action);
                    });
        } else if (kind == 7) {
            // When the core wants its next tick: 0 every frame, > 0 after that many ms, < 0 never.
            float delay;
            try {
                delay = Float.parseFloat(message);
            } catch (NumberFormatException error) {
                return;
            }
            animating.set(delay == 0);
            main.post(
                    () -> {
                        if (closed) return;
                        main.removeCallbacks(animationWake);
                        if (delay > 0) main.postDelayed(animationWake, (long) Math.ceil(delay));
                        else if (delay == 0) scheduleFrame();
                    });
        }
    }

    void onWorkerFrame(
            WidgetFrameNode node,
            Layer[] layers,
            double clockOffset,
            int width,
            int height,
            float layout,
            float record,
            int passes,
            int builds,
            int ops,
            WidgetText text) {
        boolean loaded = rendered;
        synchronized (publicationLock) {
            if (closed) {
                node.discard();
                return;
            }
            // At most one unpublished frame. A stalled UI does not accumulate display lists.
            if (pending != null) pending.node.discard();
            pending =
                    new Frame(
                            node, layers, clockOffset, width, height, layout, record, passes,
                            builds, ops, text, loaded, anchorBottom);
            schedulePublicationLocked();
        }
        lastFrameHeight = height;
        framed = true;
    }

    private void schedulePublicationLocked() {
        if (publicationScheduled) return;
        publicationScheduled = true;
        main.post(publish);
    }

    private void publishPending() {
        Frame frame;
        JSONObject snapshot;
        synchronized (publicationLock) {
            frame = pending;
            pending = null;
            snapshot = pendingDiagnostics;
            pendingDiagnostics = null;
            publicationScheduled = false;
        }
        if (closed) {
            if (frame != null) frame.node.discard();
            return;
        }
        if (frame != null && !frameMatchesWidth(frame)) {
            // Never published, so it has no RenderThread consumers. A frame at the new width follows.
            frame.node.discard();
            frame = null;
            repaintPending = false;
        }
        if (frame != null) {
            // The previous frame may still be drawn by RenderThread: drop the reference, never mutate.
            current = frame;
            repaintPending = false;
            layoutMs = frame.layout;
            recordMs = frame.record;
            layoutPasses = frame.passes;
            nativeBuilds = frame.builds;
            displayOps = frame.ops;
            records++;
            if (listener != null) listener.onFrame(this);
        }
        if (snapshot != null) {
            diagnostics = snapshot;
            if (listener != null) listener.onDiagnostics(snapshot);
        }
    }

    /** Stops JS and native work. The last frame stays readable for a snapshot. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        listener = null;
        main.removeCallbacks(wake);
        main.removeCallbacks(publish);
        main.removeCallbacks(animationWake);
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
        synchronized (publicationLock) {
            if (pending != null) pending.node.discard();
            pending = null;
            pendingDiagnostics = null;
        }
        long id = session;
        session = 0;
        if (id != 0) destroyInBackground(id);
    }

    public boolean closed() {
        return closed;
    }

    /** Any thread: the isolated engine's process died with this session in it (WidgetRemoteEngine). */
    void onEngineDied() {
        main.post(
                () -> {
                    if (closed || session == 0) return;
                    // Nothing more goes to it; close() has nothing left to destroy.
                    session = 0;
                    tickInFlight.set(false);
                    failure = "Widget engine stopped";
                    if (listener != null) listener.onEngineDied();
                });
    }

    private void destroyInBackground(long id) {
        WidgetSandboxResources.IO.execute(
                () -> {
                    Trace.beginSection("WidgetSandbox:Destroy");
                    try {
                        engine.destroy(id);
                    } finally {
                        Trace.endSection();
                    }
                });
    }

    /** A compositor layer of one frame: content node, ancestor clip and its baked track. */
    public static final class Layer {
        public final WidgetFrameNode node;
        public final float[] clip, clipRadii;
        public final double start, duration;
        public final boolean repeat;
        // Flat keys: t, a, b, c, d, e, f, opacity.
        public final float[] keys;

        Layer(
                WidgetFrameNode node,
                float[] clip,
                float[] clipRadii,
                double start,
                double duration,
                boolean repeat,
                float[] keys) {
            this.node = node;
            this.clip = clip;
            this.clipRadii = clipRadii;
            this.start = start;
            this.duration = duration;
            this.repeat = repeat;
            this.keys = keys;
        }
    }

    /** A published, immutable frame: document node plus layers. Outlives its session as a snapshot. */
    public static final class Frame {
        public final WidgetFrameNode node;
        public final Layer[] layers;
        // Native renderer clock minus System.nanoTime(), in seconds: layer tracks run on it.
        public final double clockOffset;
        public final int width, height, passes, builds, ops;
        final float layout, record;
        /** The text the widget made selectable, as this frame lays it out; null for none. */
        public final WidgetText text;
        /** Recorded after the renderer applied what the widget built on load (WidgetHost's `rendered`). */
        final boolean loaded;
        /** The widget's resize anchor when this frame was recorded: true for `bottom`, false for `top`. */
        final boolean anchorBottom;

        Frame(
                WidgetFrameNode node,
                Layer[] layers,
                double clockOffset,
                int width,
                int height,
                float layout,
                float record,
                int passes,
                int builds,
                int ops,
                WidgetText text,
                boolean loaded,
                boolean anchorBottom) {
            this.text = text;
            this.loaded = loaded;
            this.anchorBottom = anchorBottom;
            this.node = node;
            this.layers = layers;
            this.clockOffset = clockOffset;
            this.width = width;
            this.height = height;
            this.layout = layout;
            this.record = record;
            this.passes = passes;
            this.builds = builds;
            this.ops = ops;
        }

        public boolean recorded() {
            if (!node.recorded()) return false;
            for (Layer layer : layers) if (!layer.node.recorded()) return false;
            return true;
        }
    }
}
