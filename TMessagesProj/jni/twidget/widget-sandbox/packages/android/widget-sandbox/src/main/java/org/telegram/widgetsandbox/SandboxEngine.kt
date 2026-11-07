// The sandbox engine: the native counterpart of src/host/worker.ts (and a 1:1
// port of ios/.../SandboxEngine.swift). Owns one QuickJS runtime per widget on
// its own JSThread, exposes exactly one capability to the untrusted code
// (`__host(kind, arg)` + `__hostBinary()`), enforces CPU and memory limits,
// and drives timers / frames / scripts.
//
// Everything that comes out of the context is UNTRUSTED: the delegate must
// pass it through the validator before it reaches the renderer.

package org.telegram.widgetsandbox

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Mirrors `SandboxConfig` / `LIMIT_DEFAULTS` in src/shared/protocol.ts (docs/decisions.md, "Strict limits from the start"). */
data class SandboxConfig(
    var innerWidth: Double,
    var innerHeight: Double,
    var devicePixelRatio: Double,
    var theme: String,
    /** QuickJS heap limit (JS_SetMemoryLimit); exceeding it terminates the widget. */
    var memoryLimitBytes: Long = 8L shl 20,
    /** Longest continuous run of widget JS (one task) before the widget is terminated. */
    var cpuLimitMs: Double = 500.0,
    /** JS time the widget may use per `cpuQuotaWindowMs` (sliding window); beyond it timers and frames are deferred, never killed. */
    var cpuQuotaMs: Double = 300.0,
    var cpuQuotaWindowMs: Double = 1000.0,
    /** Kill after this long of uninterrupted throttling; 0 disables (default). */
    var cpuKillAfterMs: Double = 0.0,
    /**
     * The host's main-thread work for the widget per `cpuQuotaWindowMs`, apart from the JS quota: the time its layout
     * queries block the renderer and the modelled cost of the ops it sends ([CpuBudget.opsCostMs]). Beyond it timers
     * and frames are deferred like under the JS quota.
     */
    var mainThreadQuotaMs: Double = 100.0,
    /** Live timers a widget may hold; the shim throws RangeError beyond it, the engine refuses as a backstop. */
    var maxTimers: Int = 64,
    /** Initial visibility (see `setVisible`): hidden widgets get no frames and timers at 1 Hz. */
    var visible: Boolean = true,
    /**
     * "strict" (default): privileged bridge calls need a user gesture, text values are capped at 32 KB,
     * visible text is budgeted, and past a counted cap (text, attributes, ops, canvas commands, CSS rules)
     * the widget stops. "compat": the behaviour before that hardening, for tests and tooling.
     * Anything but exactly "compat" is strict ([isStrictPolicy]), so a typo never weakens the sandbox.
     */
    var policy: String = "strict",
    /**
     * Opt-in sandbox features (docs/protocol.md, "Features"). This runtime implements
     * [RUNTIME_FEATURES]; the rest (canvasWords, binaryOps, morphUpdates: Swift only) is dropped.
     */
    var features: Set<String> = emptySet(),
    /** The chat's palette (docs/sdk-v1.md §2.3): themeParams keys → `#rrggbb`; the engine drops the rest (SdkPolicy.checkThemeParams), the shim fills missing keys from the scheme's defaults. */
    var themeParams: Map<String, String> = emptyMap(),
    /** The client's UI language, BCP 47 (`navigator.language`, the Intl default); `en-US` when malformed (SdkPolicy.checkLocale). */
    var locale: String = SdkPolicy.DEFAULT_LOCALE,
    /** The client's Web Storage preload (docs/sdk-v1.md §2.8); over the quota it is dropped whole with `storage-rejected` and the widget starts empty. */
    var storage: WidgetStorage? = null,
    /** The bot's `chatData` and `publicData` at start (docs/sdk-v1.md §2.13): a field that is not text of at most 1024 bytes is null with `data-rejected`, a copy gets no chatData. Later edits: [SandboxEngine.setData]. */
    var data: WidgetData = WidgetData(),
    /** The message's flags from the server (docs/sdk-v1.md §4.4: `isCopy`, `canQuery`, `backgroundQuery`), the chat type and the viewer; fixed for the engine's life. */
    var context: WidgetContext = WidgetContext(),
    /** What the client draws over the widget at boot (docs/sdk-v1.md §2.17); later changes: [SandboxEngine.contentSafeArea]. */
    var contentSafeArea: WidgetContentSafeArea = WidgetContentSafeArea(),
)

/** The features the Kotlin glue implements; the twin of Swift `runtimeFeatures` and `WEB_FEATURES` in worker.ts. */
val RUNTIME_FEATURES: Set<String> = setOf("webAnimations", "noInPlaceText", "noAttrFast", "noCanvasRect")

/** Fail-closed policy parsing, the twin of `resolvePolicy` in src/shared/protocol.ts. */
fun isStrictPolicy(policy: String): Boolean = policy != "compat"

data class SandboxStats(
    var jsMs: Double = 0.0,
    var tasks: Int = 0,
    var memoryBytes: Long = 0,
    var ops: Int = 0,
    var queries: Int = 0,
    var queryMs: Double = 0.0,
    var queryCacheHits: Int = 0,
    var missedMutations: Int = 0,
    /** Tasks deferred by the CPU quota or the main-thread window. */
    var throttledTasks: Int = 0,
    /** JS time used in the current quota window at the time of the report. */
    var cpuWindowMs: Double = 0.0,
    /** The validator's CPU time on this widget's messages ([SandboxEngine.chargeValidator]), charged to the JS quota next to [jsMs]. */
    var validatorMs: Double = 0.0,
)

private const val ACTIVATION_PREFIX = "activation:"
/** Two full areas of the quota at 12 JSON characters per byte (`\uXXXX` escapes plus the quotes, colon and comma of tiny pairs): a longer commit cannot be within it (worker.ts). */
private const val MAX_STORAGE_COMMIT_CHARS = 2 * 12 * SdkPolicy.STORAGE_QUOTA_BYTES + 64
/** `bridge-rejected`, `activation-blocked`, `storage-rejected`, `request-blocked` and `preview-blocked` are each reported at most this often per widget. */
private const val MAX_REJECTED_REPORTS = 20
/** Why the request gate refused a request (the `request-blocked` detail; widget-host.ts REQUEST_REFUSED). */
private val REQUEST_REFUSED = mapOf(
    RequestVerdict.DISABLED to "the server allows no requests for this message (no can_query: a widget sent without allow_queries, or a forwarded copy of one sent without fwd_allow_queries)",
    RequestVerdict.BUSY to "a request is already in flight",
    RequestVerdict.NO_ACTIVATION to "it needs a tap on the visible widget, one request per tap; this message allows no background requests (no background_query)",
    RequestVerdict.RATE_LIMITED to "background requests at most once per 5 s",
    RequestVerdict.NO_WALLET to "the client has no wallet for this user (no WidgetContext.wallet)",
)
/** Where a wallet call's refusal differs from a request's (widget-host.ts WALLET_REFUSED). */
private val WALLET_REFUSED = mapOf(
    RequestVerdict.NO_ACTIVATION to "it needs a tap on the visible widget, or the answer to a call made by a tap within 10 s; one call each",
    RequestVerdict.BUSY to "a request or a wallet call is already in flight",
)
private const val UNSUPPORTED_DETAIL = "request refused: the client handles no requests (onRequest)"
private const val WALLET_UNSUPPORTED_DETAIL = "wallet call refused: the client handles no wallet calls (onWallet)"

/** Widget clocks tick in 100 µs steps, like a browser's `performance.now()`, so they cannot time cache or pool effects of other widgets. */
internal fun coarseNowMs(ms: Double): Double = kotlin.math.floor(ms / 0.1) * 0.1
/** The key of a canvas op's commands in an ops batch (JSON): outside a string only, inside one a quote is `\"`. */
private const val CMDS_KEY = "\"cmds\":["
private const val HIDE_HOST_GLOBALS = "(function(g){for(const n of ['__host','__hostBinary','__hostCanvas','__hostOps','__twDispatch'])if(!delete g[n])throw new Error(n);})(globalThis);"
private const val WALL_BACKSTOP_FACTOR = 10.0
private const val WALL_BACKSTOP_MIN_MS = 5000.0

class SandboxEngine(
    private val html: String,
    private val config: SandboxConfig,
    private val resources: SandboxResources,
) {
    class QueryAnswer(val json: String, val binary: ByteArray?)

    /** All delegate calls happen on the engine's thread, never on the main thread. `query` is synchronous: it blocks the engine thread (and must not block the main thread). */
    interface Delegate {
        fun onOps(engine: SandboxEngine, json: String)
        fun onQuery(engine: SandboxEngine, json: String): QueryAnswer
        fun onListen(engine: SandboxEngine, json: String)
        fun onPreventDefault(engine: SandboxEngine, json: String)
        fun onRequestFrame(engine: SandboxEngine)
        fun onLog(engine: SandboxEngine, level: String, text: String)
        fun onDiag(engine: SandboxEngine, kind: String, detail: String)
        /** A bridge call that passed every check (docs/sdk-v1.md §3.2): the method and its arguments, user activation for links, the haptic limit. */
        fun onAction(engine: SandboxEngine, action: WidgetAction) {}
        /** Deprecated since v1 (`onAction`): the raw method and arguments of each call `onAction` got, right after it. */
        fun onBridge(engine: SandboxEngine, method: String, argsJson: String) {}
        /** The new full content of a Web Storage area ("local" | "session"), within the quota, at the end of each task that changed it. */
        fun onStorageCommit(engine: SandboxEngine, area: String, values: Map<String, String>) {}
        /**
         * `TelegramWidget.setResizeAnchor(edge)` (docs/sdk-v1.md §2.12, since v0.1.3): "top" or "bottom", the edge of the
         * bubble that stays put when the height changes. Not an action (no `onAction` / `onBridge`), no user gesture needed,
         * not gated by visibility; the shim sends only changes, during the task and before that task's ops.
         */
        fun onResizeAnchor(engine: SandboxEngine, edge: String) {}
        /**
         * `TelegramWidget.watchPublicData()` (true) / `unwatchPublicData()` (false) (docs/sdk-v1.md §2.13): whether the widget
         * wants its shared `public_data` polled while on screen. Like the anchor: not an action, no gesture, not gated by
         * visibility; the shim sends only changes.
         */
        fun onWatchPublicData(engine: SandboxEngine, on: Boolean) {}
        /**
         * A request the gate let through (docs/sdk-v1.md §2.14: [WidgetContext.canQuery] and a press, or a background
         * request where [WidgetContext.backgroundQuery] allows it; one in flight per widget). Call `respond` once, from any thread,
         * with the bot's answer or a failure (`Result.failure(WidgetRequestException(…))`; any other exception is FAILED);
         * later calls, and calls after the widget died, are ignored. Until then the widget's next request is BUSY. A client
         * that handles no requests completes with [WidgetRequestUnsupported] (the default): NOT_ALLOWED in the widget,
         * `request-blocked` with the reason `unsupported`; WidgetHost decides that on the main thread at delivery.
         */
        fun onRequest(engine: SandboxEngine, request: WidgetRequest, respond: (Result<WidgetRequestAnswer>) -> Unit) {
            respond(Result.failure(WidgetRequestUnsupported()))
        }
        /**
         * A wallet call the gate let through (docs/sdk-v1.md §2.18: [WidgetContext.wallet], a press or the grant, one in
         * flight with the requests). Show the client's wallet window with the bot of the message, send the transaction
         * (or sign the payload) on the user's confirmation, send the outcome to the bot and call `respond` once, from any
         * thread, with the bot's answer or a failure (REJECTED, BAD_REQUEST, a request's). The default refuses it.
         */
        fun onWallet(engine: SandboxEngine, call: WalletCall, respond: (Result<WidgetRequestAnswer>) -> Unit) {
            respond(Result.failure(WidgetRequestUnsupported()))
        }
        /** `TelegramWidget.copyText(text)` that passed its checks (1–256 UTF-16 units; one per press under the strict policy, shared with the link calls). Not an action (no `onAction` / `onBridge`). */
        fun onCopyText(engine: SandboxEngine, text: String) {}
        fun onLoaded(engine: SandboxEngine)
        fun onStats(engine: SandboxEngine, stats: SandboxStats)
        fun onDied(engine: SandboxEngine, reason: String)
        fun onAnim(engine: SandboxEngine, json: String) {}
    }

    /** Everything below runs on this thread (see JSThread for why not an executor). */
    val thread = JSThread("widget.sandbox")
    @Volatile var delegate: Delegate? = null

    private var js: QuickJSContext? = null
    private var dead = false
    private val stats = SandboxStats()
    private val timers = HashMap<Int, JSThread.Scheduled>()
    private var lastBinary: ByteArray? = null
    private var lastStatsPost = 0L
    private var lastMemCheck = 0L
    private var timerLimitReported = false
    /** Timer delays are clamped to 24 h on every platform (MAX_TIMER_DELAY_MS in protocol.ts). */
    private val maxTimerDelayMs = 86_400_000.0

    // Visibility and the deferred-task queue (mirrors src/host/worker.ts).
    //
    // Hidden widget (`setVisible(false)`): no frames are requested from the
    // renderer (one is requested on return if the shim asked), timers fire at
    // most once per second, batched into one wake-up, and the shim gets
    // `visibilitychange`. Deferred tasks keep their order; one `fire` per timer
    // id, one frame at most.
    private var visible = config.visible
    private var frameWanted = false
    private class Deferred(val kind: String, var arg: String)
    private val deferred = ArrayList<Deferred>()
    private var wakeTimer: JSThread.Scheduled? = null
    private var wakeAtNs = Long.MAX_VALUE
    /** Next 1 Hz boundary at which hidden timers fire (0 = none scheduled); nanoNow() nanoseconds. */
    private var hiddenTickAtNs = 0L

    // CPU quota (mirrors worker.ts): decide before a task, record after it. Times in ms of nowNs().
    private val budget = CpuBudget(config)
    /** The main-thread window (`mainThreadQuotaMs`): layout queries and the cost of the ops, apart from the JS quota. */
    private val mainBudget = CpuBudget(config.cpuLimitMs, config.mainThreadQuotaMs, config.cpuQuotaWindowMs, 0.0)
    /** Slice given to the running task in ms (for the kill message); the boot task gets cpuLimitMs. */
    private var currentSliceMs = 0.0
    /** Wall time this task's layout queries have blocked for, how many the host answered, and which limit they passed. */
    private var taskQueryMs = 0.0
    private var taskQueries = 0
    /** Modelled main-thread cost of the ops this task sent ([CpuBudget.opsCostMs]). */
    private var taskOpsCostMs = 0.0
    private var queryLimitHit: String? = null
    /** Set from any thread by [stop]: the host stopped the widget; the engine dies at the widget's next host call or task. */
    @Volatile private var stopReason: String? = null
    private var currentKind = "boot"
    /** JS time of the current task that is ours, not the widget's (evaluating the sandbox bundle), ms. */
    private var excludeMs = 0.0
    private var lastThrottleDiagMs = Double.NEGATIVE_INFINITY
    private val throttledSinceDiag = LinkedHashMap<String, Int>()
    private fun nowMs(): Double = nowNs() / 1e6
    /** Inputs that arrived before the runtime existed (the renderer reports its size before `ready`); replayed after boot. */
    private val pendingBeforeBoot = ArrayList<Pair<String, String>>()
    private val random = SecureRandom()

    private fun nowNs(): Long = QuickJSContext.nativeNow()
    private fun msSince(t0: Long): Double = (nowNs() - t0) / 1e6

    // MARK: lifecycle

    fun start() {
        thread.post { boot() }
    }

    /** Kills the widget, frees the runtime and stops the thread; `onDied` gets [reason] when the widget was still alive. */
    fun terminate(reason: String = "terminated") {
        thread.post {
            kill(reason)
            js?.close()
            js = null
            thread.stop()
        }
    }

    /**
     * The host stopped the widget (WidgetHost: the validator's `limit`, a counted cap passed under strict), from any
     * thread: the widget's next host call is refused and interrupts its JS, then the engine dies with [reason] and
     * frees the runtime and the thread, as [terminate] does.
     */
    fun stop(reason: String) {
        stopReason = reason
        terminate(reason)
    }

    private fun boot() {
        val ctx = QuickJSContext(config.memoryLimitBytes, JSThread.JS_STACK_LIMIT)
        js = ctx
        ctx.registerFunction("__host") { args -> hostCall(args.getOrElse(0) { "" }, args.getOrElse(1) { "" }) }
        ctx.registerFunction("__hostBinary") {
            val data = lastBinary ?: return@registerFunction HostReturn.Null
            lastBinary = null
            HostReturn.ArrayBuffer(data)
        }
        task("boot", config.cpuLimitMs) {
            val t0 = nowNs()
            // Generous one-off budget for our own bundle; its time is ours, not the widget's.
            ctx.deadlineNanos = nowNs() + (maxOf(config.cpuLimitMs, 5000.0) * 1e6).toLong()
            ctx.cpuDeadlineNanos = Long.MAX_VALUE
            val how = evalBundle(resources.sandboxBundle, resources.sandboxBytecode, "sandbox-bundle.js")
            excludeMs = msSince(t0)
            // Fail closed: a runtime whose shim did not load completely (its network
            // stubs and DOM capture may be missing) never runs widget code.
            if (how == "failed") {
                if (!dead) kill("sandbox bundle failed to load")
                return@task
            }
            // The bundle holds private references to the host functions; widget code
            // must not reach them, nor the shim's dispatcher, through the global object.
            val hidden = ctx.captureGlobalFunction("__twDispatch") &&
                try {
                    ctx.evaluate(HIDE_HOST_GLOBALS, "hide-host.js", false)
                    true
                } catch (e: QuickJSException) {
                    false
                }
            if (!hidden) {
                if (!dead) kill("sandbox bundle failed to load")
                return@task
            }
            delegate?.onDiag(this, "boot", "sandbox bundle eval ${excludeMs.toInt()} ms ($how)")
            resetDeadline()
            val bootArg = JSONObject()
                .put("html", html)
                .put(
                    "config",
                    JSONObject()
                        .put("memoryLimitBytes", config.memoryLimitBytes)
                        .put("cpuLimitMs", config.cpuLimitMs)
                        .put("cpuQuotaMs", config.cpuQuotaMs)
                        .put("cpuQuotaWindowMs", config.cpuQuotaWindowMs)
                        .put("cpuKillAfterMs", config.cpuKillAfterMs)
                        .put("mainThreadQuotaMs", config.mainThreadQuotaMs)
                        .put("maxTimers", config.maxTimers)
                        // The shim caps canvas commands and script animations from it (anything but compat is strict).
                        .put("policy", if (isStrictPolicy(config.policy)) "strict" else "compat")
                        .put("visible", visible)
                        .put("viewport", JSONObject().put("innerWidth", config.innerWidth).put("innerHeight", config.innerHeight).put("dpr", config.devicePixelRatio))
                        .put("theme", config.theme)
                        .put("themeParams", JSONObject(SdkPolicy.checkThemeParams(config.themeParams)))
                        .put("locale", SdkPolicy.checkLocale(config.locale) ?: SdkPolicy.DEFAULT_LOCALE)
                        .put("contentSafeArea", config.contentSafeArea.json())
                        .put("profile", SdkPolicy.PROFILE_VERSION)
                        // A preview has no message yet: no storage of its own (it lives in the sandbox for this run, never committed).
                        .put("storage", if (preview) JSONObject() else preload())
                        .also { putData(it, checkData(config.data)) }
                        .put("isCopy", copy)
                        .put("canQuery", canQuery)
                        .put("isPreview", preview)
                        // No copy says where it was forwarded (a bot could learn it from the widget's 64-byte requests), nor does a
                        // preview; the viewer only where the widget may send requests (canQuery, never in a preview).
                        .put("chatType", (if (preview || copy) null else SdkPolicy.checkChatType(config.context.chatType)) ?: JSONObject.NULL)
                        .put("viewer", (if (hideViewer) null else viewer())?.let(::viewerJson) ?: JSONObject.NULL)
                        .put("wallet", wallet)
                        .put("features", JSONArray(config.features.intersect(RUNTIME_FEATURES).sorted())),
                ).toString()
            callTw("boot", bootArg)
        }
        task("boot", config.cpuLimitMs) { callTw("loaded", "") }
        if (!dead) delegate?.onLoaded(this)
        postStats(force = true)
        val queued = ArrayList(pendingBeforeBoot)
        pendingBeforeBoot.clear()
        for ((kind, arg) in queued) if (!dead) dispatch(kind, arg)
    }

    // MARK: inputs from the host (all asynchronous)

    fun event(json: String) = enqueue("event", json)

    /** A real user input event from the renderer (press, click, key press); every event of one physical gesture carries the same increasing `gesture` number. */
    fun activationEvent(json: String, gesture: Int) = enqueue(ACTIVATION_PREFIX + gesture, json)

    private val activation = ActivationGate()
    private val haptics = HapticGate()
    private val requests = RequestGate()
    /** A background request the widget made off screen: it goes to the client when the widget appears ([setVisible]); engine thread only. */
    private var heldRequest: Pair<Int, String>? = null
    /** A preview (an inline result not sent yet, docs/sdk-v1.md §2.16) asks nothing and is no copy, whatever the flags say. */
    private val preview = config.context.preview
    private val copy = config.context.isCopy && !preview
    private val canQuery = config.context.canQuery && !preview
    private val backgroundQuery = config.context.backgroundQuery && !preview
    /** The client has the wallet for this user and the widget may ask the bot (docs/sdk-v1.md §2.18). */
    private val wallet = config.context.wallet && canQuery
    /** The widget knows its viewer only where it may send requests (`canQuery`, never in a preview), as the network marker. */
    private val hideViewer = !canQuery
    private var previewBlockedReported = 0
    private var blockedBridgeReported = 0
    private var blockedRequestsReported = 0
    private var rejectedBridgeReported = 0
    private var rejectedStorageReported = 0
    fun frame(time: Double) = enqueue("frame", coarseNowMs(time).toString())
    fun viewport(innerWidth: Double, innerHeight: Double, dpr: Double) =
        enqueue("viewport", JSONObject().put("innerWidth", innerWidth).put("innerHeight", innerHeight).put("dpr", dpr).toString())
    fun resize(entriesJson: String) = enqueue("resize", entriesJson)

    /**
     * A primary press began on the widget (the renderer's claim at pointerdown: trusted input, never the widget's events):
     * haptics are free while it lasts and 5 s after (HapticGate, docs/sdk-v1.md §2.6). Timestamped now, on the gate's clock.
     */
    fun press() {
        val at = nowMs()
        thread.post { haptics.press(at) }
    }

    /** The primary press ended (the renderer's `release`: pointerup or pointercancel). */
    fun release() {
        val at = nowMs()
        thread.post { haptics.release(at) }
    }
    /** The colour scheme and the palette (checked here). The shim fills keys that are missing from the scheme's defaults, so pass the full palette at every change. */
    fun theme(theme: String, themeParams: Map<String, String> = emptyMap()) =
        enqueue("theme", JSONObject().put("theme", theme).put("themeParams", JSONObject(SdkPolicy.checkThemeParams(themeParams))).toString())

    /**
     * What the client draws over the widget changed (docs/sdk-v1.md §2.17): `TelegramWidget.contentSafeAreaInset` and
     * `contentSafeAreaChanged` in one task; the shim checks the values. Before boot it only replaces the config's.
     */
    fun contentSafeArea(area: WidgetContentSafeArea) = thread.post {
        if (dead) return@post
        if (js == null) { config.contentSafeArea = area; return@post }
        accept("safeArea", area.json().toString())
    }

    /**
     * The bot's data changed (docs/sdk-v1.md §2.13): the full state of both strings, checked here (`data-rejected`;
     * a copy keeps no chatData, its publicData applies). The widget gets `chatDataChanged`, then `publicDataChanged`, for
     * each that differs: a `bridge` task, deferred under the CPU quota like a theme change, in order.
     */
    fun setData(data: WidgetData) = thread.post {
        if (dead) return@post
        accept("bridge", putData(JSONObject().put("type", "data"), checkData(data)).toString())
    }

    /** The host knows whether the widget is on screen; the sandbox does not. */
    fun setVisible(v: Boolean) {
        thread.post {
            if (dead || visible == v) return@post
            visible = v
            // On screen again: the widget's next background request may go out at once, the one it made off screen now
            // (docs/sdk-v1.md §2.14), before the widget hears it is visible.
            if (v && requests.show(nowMs())) heldRequest?.let { (id, data) -> heldRequest = null; deliverRequest(id, data) }
            if (js == null) return@post // boot reads `visible` for the shim
            dispatch("visibility", "{\"visible\":$v}")
            if (v) {
                if (frameWanted) { frameWanted = false; delegate?.onRequestFrame(this) }
                if (deferred.isNotEmpty()) wake()
            }
        }
    }

    private fun enqueue(kind: String, arg: String) = thread.post { accept(kind, arg) }

    /** An input on the engine thread, outside any task: kept until boot, a hidden widget's frame dropped, else dispatched. */
    private fun accept(kind: String, arg: String) {
        if (dead) return
        if (js == null) {
            pendingBeforeBoot.add(kind to arg)
            return
        }
        // A frame that arrives after the widget was hidden is dropped; the shim's callbacks wait for the next visible frame.
        if (kind == "frame" && !visible) { frameWanted = true; return }
        dispatch(kind, arg)
    }

    /** Runs one task for an input (event, timer, frame, …) if the CPU quota and the main-thread window allow, otherwise defers it (returns false). Events and visibility always run. */
    private fun dispatch(kind: String, arg: String): Boolean {
        if (dead) return true
        if (kind.startsWith(ACTIVATION_PREFIX)) {
            activation.begin(kind.removePrefix(ACTIVATION_PREFIX).toIntOrNull() ?: 0, visible)
            try {
                return dispatch("event", arg)
            } finally {
                activation.end()
            }
        }
        val now = nowMs()
        // A deferrable task waits for both windows (the JS quota and the main thread's); the slice is the JS quota's.
        val js = budget.decide(kind, now)
        val main = mainBudget.decide(kind, now)
        if (js is CpuDecision.Run && main is CpuDecision.Run) {
            task(kind, js.sliceMs) { callTw(kind, arg) }
            return true
        }
        if (budget.shouldKill(now)) {
            kill(String.format(java.util.Locale.ROOT, "CPU quota: throttled for %.1f s (%d ms per %d ms, cpuKillAfterMs=%d)", budget.throttledFor(now) / 1000, config.cpuQuotaMs.toInt(), config.cpuQuotaWindowMs.toInt(), config.cpuKillAfterMs.toInt()))
            return true
        }
        defer(kind, arg)
        stats.throttledTasks += 1
        throttledSinceDiag[kind] = (throttledSinceDiag[kind] ?: 0) + 1
        if (now - lastThrottleDiagMs >= 1000) {
            lastThrottleDiagMs = now
            val parts = throttledSinceDiag.entries.sortedBy { it.key }.map { "${it.value} ${it.key}${if (it.value == 1) "" else "s"}" }
            throttledSinceDiag.clear()
            val window = if (js is CpuDecision.Run) {
                "${mainBudget.used(now).toInt()} ms of the ${config.mainThreadQuotaMs.toInt()} ms per ${config.cpuQuotaWindowMs.toInt()} ms main-thread budget used by layout queries and ops"
            } else {
                "${budget.used(now).toInt()} ms of the ${config.cpuQuotaMs.toInt()} ms per ${config.cpuQuotaWindowMs.toInt()} ms budget used"
            }
            delegate?.onDiag(this, "cpu-throttled", "deferred ${parts.joinToString(", ")}: $window")
        }
        val retryAt = maxOf((js as? CpuDecision.Defer)?.retryAt ?: 0.0, (main as? CpuDecision.Defer)?.retryAt ?: 0.0)
        scheduleWake((retryAt * 1e6).toLong())
        return false
    }

    private fun defer(kind: String, arg: String) {
        if (kind == "frame") {
            val f = deferred.firstOrNull { it.kind == "frame" }
            if (f != null) { f.arg = arg; return }
        } else if (kind == "timer" && deferred.any { it.kind == "timer" && it.arg == arg }) {
            return // intervals do not accumulate missed ticks
        }
        deferred.add(Deferred(kind, arg))
    }

    private fun scheduleWake(atNs: Long) {
        if (wakeTimer != null && atNs >= wakeAtNs) return
        wakeTimer?.cancel()
        wakeAtNs = atNs
        wakeTimer = thread.after(maxOf(0L, (atNs - nowNs()) / 1_000_000L)) { wake() }
    }

    /** Runs deferred tasks in order; stops (and re-queues the rest) if one is deferred again. */
    private fun wake() {
        wakeTimer = null
        wakeAtNs = Long.MAX_VALUE
        hiddenTickAtNs = 0L
        val items = ArrayList(deferred)
        deferred.clear()
        for (i in items.indices) {
            if (dead) return
            if (!dispatch(items[i].kind, items[i].arg)) {
                deferred.addAll(0, items.subList(i, items.size))
                return
            }
        }
    }

    private fun onTimerFired(id: Int) {
        timers.remove(id)
        if (!visible) {
            defer("timer", id.toString())
            if (hiddenTickAtNs == 0L) hiddenTickAtNs = nowNs() + 1_000_000_000L
            scheduleWake(hiddenTickAtNs)
            return
        }
        dispatch("timer", id.toString())
    }

    // MARK: the single capability

    private fun hostCall(kind: String, arg: String): HostReturn {
        // Stopped by the host: nothing more leaves the widget, its JS is interrupted at the next poll.
        stopReason?.let { reason ->
            kill(reason)
            js?.cpuDeadlineNanos = Long.MIN_VALUE
            return HostReturn.Undefined
        }
        val delegate = delegate
        when (kind) {
            "ops" -> {
                // Exact: inside a JSON string a quote is always `\"`.
                val ops = countOccurrences("\"op\":", arg)
                stats.ops += ops
                taskOpsCostMs += CpuBudget.opsCostMs(ops, countCanvasCommands(arg))
                delegate?.onOps(this, arg)
            }
            "query" -> return HostReturn.Str(syncQuery(arg))
            "now" -> return HostReturn.Num(coarseNowMs(nowNs() / 1e6))
            "timer" -> {
                val pair = try { JSONArray(arg) } catch (e: Exception) { return HostReturn.Undefined }
                if (pair.length() != 2) return HostReturn.Undefined
                val id = pair.optInt(0, Int.MIN_VALUE)
                if (id == Int.MIN_VALUE) return HostReturn.Undefined
                var delayMs = minOf(maxOf(0.0, pair.optDouble(1, 0.0)), maxTimerDelayMs)
                // Hidden: 1 Hz, like a background tab. The timers keep the order of their own delays: all at 1 s, the
                // queue's order by deadline would run the ones set first first (setTimeout 20, 10, 0 → 20, 10, 0).
                if (!visible) delayMs = maxOf(delayMs, 1000.0 + delayMs / 10)
                // An id that has a timer replaces it, and does not count against the cap: overwriting the handle left the old
                // one running outside the map, past maxTimers and out of kill()'s reach (a shim told to reuse an id, its
                // JSON.stringify replaced; worker.ts, sandbox_engine.cpp).
                val replaced = timers.remove(id)
                if (replaced != null) {
                    replaced.cancel()
                } else if (timers.size >= config.maxTimers) {
                    // Backstop: the shim should have thrown RangeError before asking. Reaching this means a shim bug (or a tampered shim).
                    if (!timerLimitReported) {
                        timerLimitReported = true
                        delegate?.onDiag(this, "timer-limit-enforced", "engine refused a timer beyond maxTimers=${config.maxTimers}; the shim did not stop it")
                    }
                    return HostReturn.Undefined
                }
                timers[id] = thread.after(delayMs.toLong()) {
                    if (dead) return@after
                    onTimerFired(id)
                }
            }
            "clearTimer" -> arg.toIntOrNull()?.let { timers.remove(it)?.cancel() }
            "raf" -> if (!visible) frameWanted = true else delegate?.onRequestFrame(this) // hidden: one frame is requested on return
            "log" -> {
                val (level, text) = splitPrefix(arg)
                delegate?.onLog(this, level, text.take(4000))
            }
            "diag" -> {
                val (k, detail) = splitPrefix(arg)
                delegate?.onDiag(this, k.take(64), detail.take(4000))
            }
            "listen" -> delegate?.onListen(this, arg)
            "pd" -> delegate?.onPreventDefault(this, arg)
            "anim" -> delegate?.onAnim(this, arg)
            "bridge" -> bridgeCall(arg)
            "storage" -> storageCommit(arg)
            "intl" -> return HostReturn.Str(IntlBridge.format(arg))
            "random" -> {
                val n = (arg.toIntOrNull() ?: 0).coerceIn(0, 65536)
                val bytes = ByteArray(n)
                random.nextBytes(bytes)
                return HostReturn.Str(bytes.joinToString(",", "[", "]") { (it.toInt() and 0xff).toString() })
            }
            "stat" -> {
                try {
                    val s = JSONObject(arg)
                    stats.queryCacheHits = s.optInt("cacheHits", 0)
                    stats.missedMutations = s.optInt("missed", 0)
                } catch (e: Exception) {
                }
            }
        }
        return HostReturn.Undefined
    }

    /**
     * `__host('bridge', JSON [method, args])` (docs/sdk-v1.md §3.2), checked in this order:
     * the method and its arguments (`bridge-rejected`), in a preview its allowed methods (`preview-blocked`),
     * user activation for the link calls and `copyText` under the strict policy (`activation-blocked`), the
     * haptic limit (`haptic-limit`).
     * `setResizeAnchor` is no action: it goes to `onResizeAnchor` once its argument passed; `request`
     * has its own gate ([request]); `copyText` goes to `onCopyText`, not `onAction`.
     */
    private fun bridgeCall(arg: String) {
        val call = (try { JSONTokener(arg).nextValue() } catch (e: Exception) { null }) as? JSONArray
        val method = call?.opt(0)
        val args = call?.opt(1)
        val name = (method?.toString() ?: "undefined").take(64)
        val c = (if (call == null) null else SdkPolicy.checkBridgeCall(method, args)) ?: run {
            if (rejectedBridgeReported < MAX_REJECTED_REPORTS) {
                rejectedBridgeReported += 1
                delegate?.onDiag(this, "bridge-rejected", "$name: unknown method or malformed arguments")
            }
            return
        }
        // A preview may only say it is ready and set its anchor: no request, link, copy or haptic (docs/sdk-v1.md §2.16).
        if (preview && !SdkPolicy.allowedInPreview(method)) {
            if (previewBlockedReported < MAX_REJECTED_REPORTS) {
                previewBlockedReported += 1
                delegate?.onDiag(this, "preview-blocked", "$name ignored: a preview (an inline result not sent yet) takes no actions")
            }
            val id = (c as? BridgeCall.Request)?.id ?: (c as? BridgeCall.Wallet)?.id
            if (id != null) enqueue("bridge", JSONObject().put("type", "answer").put("id", id).put("error", "NOT_ALLOWED").put("reason", "preview").toString())
            return
        }
        val checked = when (c) {
            // Not an action: the host keeps the latest anchor next to the height (no gesture, no visibility gate).
            is BridgeCall.SetResizeAnchor -> {
                delegate?.onResizeAnchor(this, c.edge)
                return
            }
            // Not an action either: the host keeps whether the widget watches its public_data and tells the client on a change.
            is BridgeCall.WatchPublicData -> {
                delegate?.onWatchPublicData(this, c.on)
                return
            }
            // A request has its own gate (a press or the background rules) and goes to `onRequest`, not `onAction`.
            is BridgeCall.Request -> {
                request(c.id, c.data, c.wallet)
                return
            }
            // A wallet call shares the request gate (a press or the grant, never the background) and goes to `onWallet`.
            is BridgeCall.Wallet -> {
                walletCall(c.id, c.call)
                return
            }
            else -> c
        }
        if (isStrictPolicy(config.policy) && name in SdkPolicy.PRIVILEGED_METHODS && !activation.consume(visible)) {
            if (blockedBridgeReported < MAX_REJECTED_REPORTS) {
                blockedBridgeReported += 1
                delegate?.onDiag(this, "activation-blocked", "$name ignored: it needs a tap on the visible widget and runs in that tap's handler, one call per tap (called from the $currentKind task)")
            }
            return
        }
        if (checked is BridgeCall.CopyText) {
            delegate?.onCopyText(this, checked.text)
            return
        }
        val action = checked as? WidgetAction ?: return
        if (action is WidgetAction.Haptic) {
            val d = haptics.decide(nowMs(), visible)
            if (d == HapticDecision.REPORT) delegate?.onDiag(this, "haptic-limit", if (visible) "haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped" else "haptics of a hidden widget are dropped")
            if (d != HapticDecision.PLAY) return
        }
        delegate?.onAction(this, action)
        delegate?.onBridge(this, name, JSONObject.wrap(args)?.toString() ?: "null")
    }

    /**
     * docs/sdk-v1.md §2.14: a press unlocks one request (peeked here, consumed only when the request goes out as the
     * press's), background ones only where the context allows them, one made off screen held until the widget appears
     * ([setVisible]); one in flight. Whether the client handles requests
     * is decided at delivery (a client may set `onRequest` after the constructor, before a boot-time request reaches
     * it): [WidgetRequestUnsupported] comes back like an answer. Every outcome goes back into the widget as a `bridge`
     * answer task, never inside the task that asked.
     */
    private fun request(id: Int, data: String, withWallet: Boolean = false) {
        if (delegate == null) return refuseRequest(id, "unsupported", "NOT_ALLOWED", UNSUPPORTED_DETAIL)
        val strict = isStrictPolicy(config.policy)
        val pressed = !strict || activation.available(visible)
        val verdict = requests.decide(nowMs(), gesture = pressed, visible = visible, canQuery = canQuery, backgroundQuery = backgroundQuery, withWallet = withWallet, wallet = wallet)
        SdkPolicy.requestRefusal(verdict)?.let {
            val why = if (withWallet && verdict == RequestVerdict.NO_ACTIVATION) "a request for the wallet needs a tap on the visible widget, one request per tap" else REQUEST_REFUSED[verdict] ?: verdict.value
            return refuseRequest(id, verdict.value, it, "request refused (${verdict.value}): $why")
        }
        if (verdict == RequestVerdict.FOREGROUND && strict) activation.consume(visible)
        if (verdict == RequestVerdict.HELD) heldRequest = id to data else deliverRequest(id, data, withWallet)
    }

    /**
     * docs/sdk-v1.md §2.18: a wallet call goes out on a press or on the grant (the answer to a call made by a press,
     * within 10 s), never in the background; one call in flight with the requests. Its outcome settles like a request's.
     */
    private fun walletCall(id: Int, call: WalletCall) {
        val strict = isStrictPolicy(config.policy)
        val pressed = !strict || activation.available(visible)
        val verdict = requests.decideWallet(nowMs(), gesture = pressed, visible = visible, canQuery = canQuery, wallet = wallet)
        SdkPolicy.requestRefusal(verdict)?.let {
            return refuseRequest(id, verdict.value, it, "wallet.${call.method} refused (${verdict.value}): ${WALLET_REFUSED[verdict] ?: REQUEST_REFUSED[verdict] ?: verdict.value}")
        }
        if (verdict == RequestVerdict.FOREGROUND && strict) activation.consume(visible)
        val delegate = delegate ?: run {
            requests.finish()
            return refuseRequest(id, "unsupported", "NOT_ALLOWED", WALLET_UNSUPPORTED_DETAIL)
        }
        val respond = completion(id, wallet = true)
        try {
            delegate.onWallet(this, call, respond)
        } catch (e: Exception) {
            respond(Result.failure(e))
        }
    }

    /** Any thread, honoured once; the gate lives on the engine thread. A stopped thread drops the post (destroyed). */
    private fun completion(id: Int, wallet: Boolean): (Result<WidgetRequestAnswer>) -> Unit {
        val once = AtomicBoolean(false)
        return { outcome -> if (once.compareAndSet(false, true)) thread.post { answerRequest(id, outcome, wallet) } }
    }

    /** A request the gate let through, to the delegate; its outcome frees the gate ([answerRequest]). */
    private fun deliverRequest(id: Int, data: String, withWallet: Boolean = false) {
        val delegate = delegate ?: run {
            requests.finish()
            return refuseRequest(id, "unsupported", "NOT_ALLOWED", UNSUPPORTED_DETAIL)
        }
        val respond = completion(id, wallet = false)
        try {
            delegate.onRequest(this, WidgetRequest(data, withWallet), respond)
        } catch (e: Exception) {
            respond(Result.failure(e)) // a delegate that throws has failed the request (FAILED), the gate does not stay in flight
        }
    }

    private fun refuseRequest(id: Int, reason: String, error: String, detail: String) {
        if (blockedRequestsReported < MAX_REJECTED_REPORTS) {
            blockedRequestsReported += 1
            delegate?.onDiag(this, "request-blocked", detail)
        }
        enqueue("bridge", JSONObject().put("type", "answer").put("id", id).put("error", error).put("reason", reason).toString())
    }

    /**
     * The client's answer or failure, on the engine thread outside any task: checked, then into the widget (the promise
     * settles in a `bridge` task). The gate is free again; an answer brings the grant when the call had a press or the
     * grant; the wallet's own failure codes only for a wallet call.
     */
    private fun answerRequest(id: Int, outcome: Result<WidgetRequestAnswer>, wallet: Boolean = false) {
        if (dead) return
        val event = JSONObject().put("type", "answer").put("id", id)
        val answer = outcome.getOrElse { e ->
            requests.finish()
            if (e is WidgetRequestUnsupported) return refuseRequest(id, "unsupported", "NOT_ALLOWED", if (wallet) WALLET_UNSUPPORTED_DETAIL else UNSUPPORTED_DETAIL)
            return accept("bridge", event.put("error", if (wallet) SdkPolicy.walletFailure(e) else SdkPolicy.requestFailure(e)).toString())
        }
        val checked = SdkPolicy.checkRequestAnswer(mapOf("result" to answer.result))
        if (checked == null) {
            requests.finish()
            delegate?.onDiag(this, "answer-rejected", "the answer's result is not text of at most ${SdkPolicy.MAX_REQUEST_RESULT_BYTES} bytes (UTF-8, no NUL); the widget gets INVALID_ANSWER")
            return accept("bridge", event.put("error", "INVALID_ANSWER").toString())
        }
        requests.finish(nowMs())
        accept("bridge", event.put("result", checked.result ?: JSONObject.NULL).toString())
    }

    /** The client's data as SdkPolicy takes it (an object of `chatData` / `publicData`; null = none). */
    private fun dataMap(d: WidgetData) = mapOf("chatData" to d.chatData, "publicData" to d.publicData)

    /** The client's data at boot and in `setData` (widget-host.ts checkData): the full state of both strings, a copy without chatData, a field over the limit null and reported. */
    private fun checkData(data: WidgetData): CheckedWidgetData {
        val d = SdkPolicy.checkWidgetData(dataMap(data), copy)
        if (d.rejected.isNotEmpty()) delegate?.onDiag(this, "data-rejected", "${d.rejected.joinToString(" and ")}: not text of at most ${SdkPolicy.MAX_WIDGET_DATA_BYTES} bytes (UTF-8, no NUL); the widget sees null")
        return d
    }

    /** `chatData` and `publicData` into a boot config or a `bridge` event, real JSON nulls for none. */
    private fun putData(o: JSONObject, d: CheckedWidgetData): JSONObject =
        o.put("chatData", d.chatData ?: JSONObject.NULL).put("publicData", d.publicData ?: JSONObject.NULL)

    /** The viewer the boot config carries, checked (no id of 1 … 2^53 − 1: none); a field that breaks its rule is left out and reported (`data-rejected`). */
    private fun viewer(): WidgetViewer? {
        val v = config.context.viewer ?: return null
        val checked = SdkPolicy.checkViewer(mapOf("id" to v.id, "first_name" to v.firstName, "last_name" to v.lastName, "username" to v.username, "is_premium" to v.isPremium))
        for (field in checked.rejected) delegate?.onDiag(this, "data-rejected", "viewer.$field: ${SdkPolicy.VIEWER_FIELD_RULES[field]}; the widget does not see it")
        return checked.viewer
    }

    /** The viewer in the widget's field names (`WebAppUser`): the fields it has, `is_premium` only when true. */
    private fun viewerJson(v: WidgetViewer): JSONObject = JSONObject().put("id", v.id).apply {
        v.firstName?.let { put("first_name", it) }
        v.lastName?.let { put("last_name", it) }
        v.username?.let { put("username", it) }
        if (v.isPremium) put("is_premium", true)
    }

    /** `__host('storage', JSON commit)`: the full areas a task changed, at most once per task, after its ops (docs/sdk-v1.md §3.2). */
    private fun storageCommit(arg: String) {
        if (preview) return // a preview's Web Storage lives in this run only: there is no message to keep it for
        val tooLong = arg.length > MAX_STORAGE_COMMIT_CHARS
        val commit = if (tooLong) null else try { SdkPolicy.checkStorageCommit(JSONTokener(arg).nextValue()) } catch (e: Exception) { null }
        if (commit == null || (commit.local == null && commit.session == null)) {
            if (rejectedStorageReported < MAX_REJECTED_REPORTS) {
                rejectedStorageReported += 1
                val what = if (tooLong) "a storage commit of ${arg.length} characters" else "a storage commit that is not { local?, session? } objects of strings within ${SdkPolicy.STORAGE_QUOTA_BYTES} bytes each"
                delegate?.onDiag(this, "storage-rejected", "$what; the client keeps the previous content")
            }
            return
        }
        commit.local?.let { delegate?.onStorageCommit(this, "local", it) }
        commit.session?.let { delegate?.onStorageCommit(this, "session", it) }
    }

    /** The client's preload as the boot config carries it; over the quota: `storage-rejected` and empty storage. */
    private fun preload(): JSONObject {
        val s = config.storage ?: return JSONObject()
        val checked = SdkPolicy.checkStorageCommit(mapOf("local" to s.local, "session" to s.session))
        if (checked == null) {
            delegate?.onDiag(this, "storage-rejected", "the preload is not { local, session } objects of strings within ${SdkPolicy.STORAGE_QUOTA_BYTES} bytes each; the widget starts with empty storage")
            return JSONObject()
        }
        val out = JSONObject()
        checked.local?.let { out.put("local", JSONObject(it)) }
        checked.session?.let { out.put("session", JSONObject(it)) }
        return out
    }

    /** Blocks the engine thread until the host answers (the host must never wait for the engine). */
    private fun syncQuery(json: String): String {
        val delegate = delegate ?: return "{\"error\":\"no host\"}"
        if (queryLimitHit != null) return "{\"error\":\"query budget exceeded\"}"
        val t0 = nowNs()
        val answer = delegate.onQuery(this, json)
        val dt = nowNs() - t0
        js?.let { it.deadlineNanos = it.deadlineNanos + dt } // time spent waiting is not widget CPU time
        stats.queries += 1
        stats.queryMs += dt / 1e6
        taskQueryMs += dt / 1e6
        taskQueries += 1
        queryLimitHit = CpuBudget.queryLimitHit(taskQueries, taskQueryMs)
        if (queryLimitHit != null) {
            // The host answered every query, but this task has asked too many or held the renderer too long: interrupt it at the next poll.
            js?.cpuDeadlineNanos = Long.MIN_VALUE
        }
        lastBinary = answer.binary
        return answer.json
    }

    // MARK: running code

    private fun kill(reason: String) {
        if (dead) return
        dead = true
        heldRequest = null // never reaches the client
        pendingBeforeBoot.clear()
        for (t in timers.values) t.cancel()
        timers.clear()
        deferred.clear()
        wakeTimer?.cancel()
        wakeTimer = null
        delegate?.onDied(this, reason)
    }

    private fun handleError(error: QuickJSException, location: String) {
        val text = error.message ?: error.toString()
        val hit = queryLimitHit
        if (js?.interrupted == true && hit != null) {
            return kill("${CpuBudget.queryLimitReason(hit, currentKind)} ($location)")
        }
        if (js?.interrupted == true) {
            return kill("CPU limit exceeded: more than ${currentSliceMs.toInt()} ms of continuous execution ($location)")
        }
        if (text.lowercase().contains("out of memory")) return kill("Memory limit exceeded (${config.memoryLimitBytes shr 20} MB)")
        delegate?.onLog(this, "error", ("Uncaught $text").take(4000))
    }

    private fun drainJobs() {
        js?.executePendingJobs { handleError(it, "promise job") }
    }

    private fun callTw(kind: String, arg: String): String? {
        if (dead) return null
        val ctx = js ?: return null
        return try {
            ctx.call("__twDispatch", listOf(kind, arg)).string
        } catch (e: QuickJSException) {
            handleError(e, kind)
            null
        }
    }

    private fun settle() {
        for (i in 0 until 20) {
            if (dead) return
            drainJobs()
            if (dead) return
            val more = callTw("endTick", "")
            if (more != "1" && js?.hasPendingJob != true) break
        }
    }

    /**
     * Evaluates one of our own bundles: the precompiled `.qbc` when it is shipped and
     * matches the source, else the source. Returns "bytecode" or "source" for the diag.
     */
    private fun evalBundle(source: String, bytecode: ByteArray?, filename: String): String {
        // Bundles of another release are refused before anything runs, as a stale `.qbc` is (BundleStamp.kt).
        resources.refusal?.let {
            delegate?.onDiag(this, "bundle-mismatch", it)
            return "failed"
        }
        val ctx = js
        if (ctx != null && bytecode != null) {
            try {
                ctx.evaluateBytecode(bytecode, source)
                return "bytecode"
            } catch (e: QuickJSException) {
                delegate?.onDiag(this, "bytecode", "$filename: falling back to source — ${e.message?.lineSequence()?.firstOrNull()}")
            }
        }
        if (ctx == null) return "failed"
        try {
            ctx.evaluate(source, filename, false)
        } catch (e: QuickJSException) {
            handleError(e, filename)
            return "failed"
        }
        return "source"
    }

    private fun evalScript(code: String, filename: String, asModule: Boolean = false) {
        val ctx = js ?: return
        try {
            ctx.evaluate(code, filename, asModule)
        } catch (e: QuickJSException) {
            handleError(e, filename)
        }
    }

    private fun pumpScripts() {
        for (i in 0 until 1000) {
            if (dead) return
            val next = callTw("nextScript", "")
            if (next.isNullOrEmpty()) break
            val s = try { JSONObject(next) } catch (e: Exception) { break }
            val id = s.optInt("id", 0)
            var ok = true
            resetDeadline()
            val src = s.optString("src", "")
            if (s.has("src") && src.isNotEmpty()) {
                // Nothing is fetched and nothing is bundled (profile 1: no libraries): an external script never runs.
                ok = false
                delegate?.onDiag(this, "script-skipped", "external script not available: ${src.take(200)}")
            } else if (s.has("code")) {
                evalScript(s.optString("code"), "inline-script-$id.js", s.optString("type") == "module")
            }
            if (dead) return
            settle()
            callTw("scriptDone", "[$id,$ok]")
            settle()
        }
    }

    /**
     * The slice is measured in the JS thread's CPU time, so a busy device does not
     * kill widgets; wall-clock time is only a backstop for a thread that never runs.
     */
    private fun resetDeadline() {
        val ctx = js ?: return
        ctx.resetInterrupted()
        ctx.cpuDeadlineNanos = ctx.threadCpuNanos() + (currentSliceMs * 1e6).toLong()
        ctx.deadlineNanos = nowNs() + (maxOf(currentSliceMs * WALL_BACKSTOP_FACTOR, WALL_BACKSTOP_MIN_MS) * 1e6).toLong()
    }

    /** One task of `kind` with `sliceMs` of continuous execution; its JS time is recorded against the quota, its layout queries and ops against the main-thread window. */
    private fun task(kind: String, sliceMs: Double, body: () -> Unit) {
        stopReason?.let { kill(it) }
        if (dead) return
        currentKind = kind
        currentSliceMs = sliceMs
        excludeMs = 0.0
        taskQueryMs = 0.0
        taskQueries = 0
        taskOpsCostMs = 0.0
        queryLimitHit = null
        val c0 = js?.threadCpuNanos() ?: 0L
        resetDeadline()
        body()
        if (!dead) settle()
        if (!dead) pumpScripts()
        // Past the limit but done before the interrupt landed (a loop that ended on the error answers): stopped all the same.
        queryLimitHit?.let { if (!dead) kill(CpuBudget.queryLimitReason(it, kind)) }
        stats.tasks += 1
        val cpuMs = ((js?.threadCpuNanos() ?: c0) - c0) / 1e6 - excludeMs
        stats.jsMs += cpuMs
        budget.record(kind, cpuMs, nowMs())
        mainBudget.record(kind, taskQueryMs + taskOpsCostMs, nowMs()) // what the task cost the host's main thread
        stats.cpuWindowMs = budget.used(nowMs())
        val now = nowNs()
        if (now - lastMemCheck > 1_000_000_000L && !dead) {
            lastMemCheck = now
            stats.memoryBytes = js?.memoryUsed ?: 0
        }
        postStats(force = false)
    }

    /**
     * The validator's CPU time for this widget's messages ([TrustedValidator.cpuSink], ms of its thread's clock):
     * recorded against the JS quota on the engine thread as it arrives ([SandboxStats.validatorMs]; with the debt rule a
     * late record still counts in full; docs/decisions.md, "The validator's time counts"). Any thread.
     */
    fun chargeValidator(ms: Double) {
        if (!(ms > 0)) return
        thread.post {
            if (dead) return@post
            val now = nowMs()
            budget.record("validator", ms, now)
            stats.validatorMs += ms
            stats.cpuWindowMs = budget.used(now)
            postStats(force = false)
        }
    }

    private var trailingStats: JSThread.Scheduled? = null

    /** At most one stats message per 200 ms, but the last state always gets out. */
    private fun postStats(force: Boolean) {
        val now = nowNs()
        if (force || now - lastStatsPost > 200_000_000L) {
            lastStatsPost = now
            trailingStats?.cancel()
            trailingStats = null
            delegate?.onStats(this, stats.copy())
        } else if (trailingStats == null) {
            trailingStats = thread.after(200) {
                trailingStats = null
                postStats(force = true)
            }
        }
    }

    // MARK: helpers

    private fun splitPrefix(s: String): Pair<String, String> {
        val i = s.indexOf(':')
        return if (i < 0) s to "" else s.substring(0, i) to s.substring(i + 1)
    }

    /**
     * Commands in the canvas ops of an ops batch (JSON): the elements of each `"cmds":[...]` array, counted at its
     * depth with strings skipped (the cost model's canvas commands, [CpuBudget.opsCostMs]).
     */
    private fun countCanvasCommands(json: String): Int {
        var total = 0
        var i = json.indexOf(CMDS_KEY)
        while (i >= 0) {
            var j = i + CMDS_KEY.length
            var depth = 0
            var elements = 0
            var empty = true
            scan@ while (j < json.length) {
                when (json[j]) {
                    '"' -> {
                        j++
                        while (j < json.length && json[j] != '"') j += if (json[j] == '\\') 2 else 1
                        empty = false
                    }
                    '[', '{' -> { depth++; empty = false }
                    ']', '}' -> { if (depth == 0) break@scan; depth-- }
                    ',' -> if (depth == 0) elements++
                    ' ', '\n', '\r', '\t' -> {}
                    else -> empty = false
                }
                j++
            }
            if (!empty) total += elements + 1
            i = json.indexOf(CMDS_KEY, j)
        }
        return total
    }

    private fun countOccurrences(needle: String, text: String): Int {
        var count = 0
        var i = text.indexOf(needle)
        while (i >= 0) {
            count++
            i = text.indexOf(needle, i + needle.length)
        }
        return count
    }
}
