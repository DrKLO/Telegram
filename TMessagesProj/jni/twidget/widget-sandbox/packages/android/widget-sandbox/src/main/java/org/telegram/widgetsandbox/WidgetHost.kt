// WidgetHost: one widget = one SandboxEngine (QuickJS on its own thread) + one
// TrustedValidator + one RendererWebView. The only place where the three meet;
// nothing from the engine reaches the renderer without passing the validator.
// Mirrors src/host/widget-host.ts and ios/.../WidgetHost.swift. All public API
// is main-thread.

package org.telegram.widgetsandbox

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class DiagEntry(var count: Int = 0, val details: ArrayList<String> = ArrayList()) {
    fun copy() = DiagEntry(count, ArrayList(details))
}

/** Snapshot of what the host knows about a widget; `onChange` delivers copies on the main thread. */
class WidgetDiagnostics(
    val logs: ArrayList<Pair<String, String>> = ArrayList(),
    val diags: LinkedHashMap<String, DiagEntry> = LinkedHashMap(),
    val bridge: ArrayList<Pair<String, String>> = ArrayList(),
    var stats: SandboxStats = SandboxStats(),
    var nodes: Int = 0,
    var loaded: Boolean = false,
    /**
     * Fatal: the runtime is gone (memory, CPU, layout queries, or a counted cap passed under strict: `Text limit exceeded: …`,
     * `Ops limit exceeded: …`, …). The renderer keeps the last state (frozen); the owner removes the view with `destroy()`.
     */
    var dead: String? = null,
    /** Document height in CSS px (= dp). */
    var height: Double = 0.0,
    /** Last uncaught widget error (non-fatal), cleared once a later task completes without one. */
    var lastError: String? = null,
    /** Wall clock from WidgetHost creation to the renderer's `ready`, to the first `size` report that follows validated ops (first content on screen) and to `loaded`; same definitions as iOS. */
    var rendererReadyMs: Double? = null,
    var firstContentMs: Double? = null,
    var loadedMs: Double? = null,
    /** Which parts came prewarmed from the pools ("validator"), empty when none. */
    var prewarmed: String = "",
    /** What `onAction` got, in order (the first 1000). */
    val actions: ArrayList<WidgetAction> = ArrayList(),
    /**
     * The edge of the bubble that stays put on screen when `height` changes (`TelegramWidget.setResizeAnchor`,
     * docs/sdk-v1.md §2.12 and §5.6, since v0.1.3): "top" (the default: the bubble grows and shrinks downward, the
     * content below moves) or "bottom" (it grows and shrinks upward, its bottom stays, the content above moves). The
     * widget's latest call; it reaches this state before the size report of the content its task changed.
     */
    var resizeAnchor: String = SdkPolicy.DEFAULT_RESIZE_ANCHOR,
    /**
     * Whether the widget watches its shared `public_data` (its latest `TelegramWidget.watchPublicData()` /
     * `unwatchPublicData()`, docs/sdk-v1.md §2.13), false until it calls one. Kept after the widget stops or dies.
     */
    var watchesPublicData: Boolean = false,
) {
    fun copy() = WidgetDiagnostics(
        ArrayList(logs), LinkedHashMap(diags.mapValues { it.value.copy() }), ArrayList(bridge), stats.copy(), nodes, loaded, dead, height, lastError,
        rendererReadyMs, firstContentMs, loadedMs, prewarmed, ArrayList(actions), resizeAnchor, watchesPublicData,
    )
}

/**
 * The chat's theme for the bubble the widget sits in (docs/sdk-v1.md §2.3, §2.4, §5.1): its own colours (an
 * outgoing bubble's when the user sent or forwarded the widget), the client's button and its message text;
 * pass it at creation and call `setTheme` on every change.
 */
data class WidgetTheme(
    /** "light" or "dark"; anything but "dark" is light. */
    val colorScheme: String = "light",
    /** Telegram Mini Apps theme keys (`bg_color`, `text_color`, … [SdkPolicy.THEME_KEYS]) → `#rrggbb`; other keys and values are dropped, missing keys take the scheme's defaults. */
    val params: Map<String, String> = emptyMap(),
    /** The client's own button, for the widget's `<button>`. */
    val button: WidgetButtonTokens = WidgetButtonTokens(),
    /** The client's message text: the root's font size and line height, the bubble's padding. */
    val text: WidgetTextTokens = WidgetTextTokens(),
)

/** The client's button (docs/sdk-v1.md §2.4); every field optional. The renderer takes only values that match their grammar, the template's defaults stand for the rest. */
data class WidgetButtonTokens(
    /** `#rrggbb` */
    val bg: String? = null,
    /** `#rrggbb` */
    val text: String? = null,
    /** A CSS length: `10px`, `0.5em`, `1rem` */
    val radius: String? = null,
    /** A font shorthand with generic families only: `600 15px/1.2 system-ui` */
    val font: String? = null,
    /** One to four CSS lengths: `8px 14px` */
    val padding: String? = null,
    /** A CSS length */
    val height: String? = null,
    /** 0–1, at most three decimals */
    val pressedOpacity: Double? = null,
    /** 0–1, at most three decimals */
    val disabledOpacity: Double? = null,
)

/**
 * The client's message text (docs/sdk-v1.md §2.4); every field optional, the reference set stands for the rest
 * (15, 1.35, `8px 12px`). The renderer takes only values that match their grammar and sets them as `--tg-text-*`.
 */
data class WidgetTextTokens(
    /** The chat's message font size in dp (= CSS px), 8–64: follows the client's text-size setting. */
    val fontSize: Double? = null,
    /** Unitless, 1–3: a multiple of the font size. */
    val lineHeight: Double? = null,
    /** Between the bubble's edge and the widget's content, one to four CSS lengths: `8px 12px` */
    val padding: String? = null,
)

/**
 * What the client draws over the widget, in dp = CSS px (docs/sdk-v1.md §2.17, §5.12). A widget with no caption below it gets
 * the message time and status over its bottom corner, as on a photo: `bottom` is the height of that band and
 * `cornerRight` (or `cornerLeft` in a right-to-left interface: a physical side) the width of the corner the time takes in
 * it; with a caption below it everything is 0 (the time is in the caption's line; a caption shown above it, `invert_media`, is as none). The widget sees it as `env(safe-area-inset-*)`,
 * the `--tg-content-safe-area-*` variables and `TelegramWidget.contentSafeAreaInset`. Passed as given: the renderer core
 * and the shim keep an inset of 0–64 and a corner of 0–200, anything else is 0.
 */
data class WidgetContentSafeArea(
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
    val left: Double = 0.0,
    val cornerLeft: Double = 0.0,
    val cornerRight: Double = 0.0,
) {
    /** The TS `ContentSafeArea` shape; a value that is not finite is 0 (JSON has no NaN). */
    internal fun json(): JSONObject {
        val out = JSONObject()
        for ((k, v) in listOf("top" to top, "right" to right, "bottom" to bottom, "left" to left, "cornerLeft" to cornerLeft, "cornerRight" to cornerRight)) {
            out.put(k, if (v.isFinite()) v else 0.0)
        }
        return out
    }
}

/** Web Storage of the message as the client keeps it (docs/sdk-v1.md §2.8, §5.4): the stored `local` area and the in-memory `session` one. */
data class WidgetStorage(val local: Map<String, String> = emptyMap(), val session: Map<String, String> = emptyMap())

/**
 * The bot's two strings as the message has them (docs/sdk-v1.md §2.13), the full state each time: `chatData`
 * (this chat only, never in a copy) and `publicData` (a copy gets it too, and its changes). Null or "" for none; a field
 * that is not text of at most 1024 bytes (UTF-8, no NUL) reaches the widget as null (`data-rejected`).
 */
data class WidgetData(val chatData: String? = null, val publicData: String? = null)

/**
 * The account that looks at the widget (docs/sdk-v1.md §4): its Telegram user id (1 … 2^53 − 1; anything else is no
 * viewer) and, when the client has them, its names, username and Premium. The widget sees it as Mini Apps' `WebAppUser`
 * (`{ id, first_name?, last_name?, username?, is_premium? }`), only with `canQuery`; a field that breaks its rule (a name
 * 1–64 UTF-16 code units, a username 5–32 characters of A–Z, a–z, 0–9 and _) is left out with `data-rejected`.
 */
data class WidgetViewer(
    val id: Long,
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null,
    val isPremium: Boolean = false,
)

/**
 * The message's flags from the server (docs/sdk-v1.md §4.4), the chat type and the viewer; fixed for the host's life.
 * All false by default: a client that passes no flags gets no requests.
 */
data class WidgetContext(
    /** `is_copy`: the message is a forwarded copy: never any chatData (`TelegramWidget.isForward` in the widget). */
    val isCopy: Boolean = false,
    /** `can_query`: requests are allowed, on a tap (an original of a widget sent with `allow_queries`, or a copy of one sent with `fwd_allow_queries`). */
    val canQuery: Boolean = false,
    /** `background_query`: requests without a tap too (the original in the private chat with the bot): one when the widget appears on screen, then one per 5 s while visible; one made off screen waits for it to appear. */
    val backgroundQuery: Boolean = false,
    /** "private" | "group" | "supergroup" | "channel" ([SdkPolicy.CHAT_TYPES]); anything else is none. No copy gets it, with `canQuery` or not (a bot must learn nothing about where its widget was forwarded), nor a preview. */
    val chatType: String? = null,
    /** A copy without `canQuery` gets none. */
    val viewer: WidgetViewer? = null,
    /**
     * An inline result shown before it is sent (no message yet, docs/sdk-v1.md §2.16): the widget gets its data and runs
     * as usual, but no input (a tap on it is the client's: it selects the result), no requests or other bridge calls but
     * `ready` / `setResizeAnchor`, no chat type or viewer, and Web Storage that is never committed (the preload is
     * ignored). The request flags and `isCopy` are ignored.
     */
    val preview: Boolean = false,
    /**
     * The client has Gram Wallet for this user: it supports the wallet and the wallet is available in the user's country
     * (docs/sdk-v1.md §2.18). The widget gets `TelegramWidget.wallet` only with [canQuery] too, never in a preview.
     */
    val wallet: Boolean = false,
)

/**
 * What the client sends to the bot like a callback button's press: `data` (1–64 bytes, UTF-8). Whether it came from a
 * press or in the background is the glue's decision (the request gate); the server and the bot cannot tell them apart.
 */
data class WidgetRequest(
    val data: String,
    /** The widget asked for the user's wallet (only on a tap, only with [WidgetContext.wallet]): ask the server to attach the wallet card (`with_wallet`); it creates the wallet if the user had none. */
    val wallet: Boolean = false,
)

/** One message of a transaction, as TON Connect's `sendTransaction` has it: the destination, nanotons as a decimal string, a body and a `StateInit` (bags of cells in base64). */
data class WalletMessage(val address: String, val amount: String, val payload: String? = null, val stateInit: String? = null) {
    fun json(): JSONObject = JSONObject().put("address", address).put("amount", amount).also { o ->
        payload?.let { o.put("payload", it) }
        stateInit?.let { o.put("stateInit", it) }
    }
}

/**
 * A transaction the widget asks the user to send (`TelegramWidget.wallet.sendTransaction`, docs/sdk-v1.md §2.18,
 * checked): TON Connect's `sendTransaction` request. The client hands it to its TON Connect window as is ([json]: the
 * protocol's field names); the wallet checks the network and the sender against the account, the cells and the expiry.
 */
data class WalletTransaction(
    /** `valid_until`, Unix seconds. */
    val validUntil: Long? = null,
    /** "-239" (the mainnet) or "-3" (the testnet). */
    val network: String? = null,
    val from: String? = null,
    /** 1–4 messages. */
    val messages: List<WalletMessage>,
) {
    fun json(): JSONObject = JSONObject().put("messages", JSONArray(messages.map { it.json() })).also { o ->
        validUntil?.let { o.put("valid_until", it) }
        network?.let { o.put("network", it) }
        from?.let { o.put("from", it) }
    }
}

/**
 * What the widget asks the user to sign (`TelegramWidget.wallet.signData`, checked): TON Connect's `signData` payload,
 * `type` "text" (`text`), "binary" (`bytes`, base64) or "cell" (`cell`, base64, with its TL-B `schema`). The client signs
 * for the bot of the message (the domain `bot<bot_id>`), never a domain the widget names.
 */
data class SignDataPayload(
    val type: String,
    val text: String? = null,
    val bytes: String? = null,
    val schema: String? = null,
    val cell: String? = null,
    val network: String? = null,
    val from: String? = null,
) {
    fun json(): JSONObject = JSONObject().put("type", type).also { o ->
        for ((k, v) in listOf("text" to text, "bytes" to bytes, "schema" to schema, "cell" to cell, "network" to network, "from" to from)) v?.let { o.put(k, it) }
    }
}

/**
 * A wallet call that passed its checks (docs/sdk-v1.md §2.18): the client shows its own wallet window with the bot of the
 * message, sends the transaction (or signs the payload) on the user's confirmation, sends the outcome to the bot as a
 * query with [data] (1–64 bytes, a request's) and completes with the bot's answer.
 */
sealed class WalletCall {
    abstract val data: String
    /** "sendTransaction" or "signData". */
    abstract val method: String
    data class SendTransaction(val transaction: WalletTransaction, override val data: String) : WalletCall() {
        override val method get() = "sendTransaction"
    }
    data class SignData(val payload: SignDataPayload, override val data: String) : WalletCall() {
        override val method get() = "signData"
    }
}

/**
 * The server's answer as the client hands it to the host: `result`, text of at most 1024 bytes (UTF-8, no NUL; anything
 * else is INVALID_ANSWER in the widget), null for none. The bot's data changes come as message edits (`setData`); the
 * client shows the answer's text, alert and URL itself, as for a callback button.
 */
data class WidgetRequestAnswer(val result: String? = null)

/**
 * Why a client could not complete a request: the bot did not answer in time, flood wait, anything else; for a wallet
 * call also REJECTED (the user declined in the wallet window) and BAD_REQUEST (the wallet refused the transaction or the
 * payload), which a request's failure turns into FAILED.
 */
enum class WidgetRequestFailure { TIMEOUT, RATE_LIMITED, FAILED, REJECTED, BAD_REQUEST }

/** A failed request, as `onRequest` reports it: `Result.failure(WidgetRequestException(…))`; any other exception is FAILED. */
class WidgetRequestException(val failure: WidgetRequestFailure) : Exception(failure.name)

/**
 * The internal outcome of a request no client handles (WidgetHost: `onRequest` is null when the request reaches the main
 * thread): the engine refuses it as NOT_ALLOWED with the reason `unsupported`. Not a [WidgetRequestFailure]: a client
 * that has `onRequest` always delivers or fails.
 */
internal class WidgetRequestUnsupported : Exception("the client handles no requests")

class WidgetHost(
    context: Context,
    html: String,
    /** The chat's theme; `setTheme` changes it. */
    theme: WidgetTheme = WidgetTheme(),
    /** Width of the widget in CSS px (= dp). */
    widthDp: Double = 360.0,
    options: Options = Options(),
    resources: SandboxResources = SandboxResources.get(context),
    /** The renderer; the WebView renderer when null. */
    rendererFactory: RendererFactory? = null,
) : SandboxEngine.Delegate {
    @Deprecated("Since SDK v1: pass a WidgetTheme", ReplaceWith("WidgetHost(context, html, WidgetTheme(theme), widthDp, options, resources, rendererFactory)"))
    constructor(
        context: Context,
        html: String,
        theme: String,
        widthDp: Double = 360.0,
        options: Options = Options(),
        resources: SandboxResources = SandboxResources.get(context),
        rendererFactory: RendererFactory? = null,
    ) : this(context, html, WidgetTheme(theme), widthDp, options, resources, rendererFactory)

    /** Limits for the widget's QuickJS runtime (see SandboxConfig; docs/decisions.md, "Strict limits from the start"). */
    data class Options(
        val memoryLimitBytes: Long = 8L shl 20,
        val cpuLimitMs: Double = 500.0,
        val cpuQuotaMs: Double = 300.0,
        val cpuQuotaWindowMs: Double = 1000.0,
        val cpuKillAfterMs: Double = 0.0,
        /** The host's main-thread work for this widget per window (layout queries + 25 µs per op + 2 µs per canvas command); timers and frames wait beyond it. */
        val mainThreadQuotaMs: Double = 100.0,
        val maxTimers: Int = 64,
        /** Initial visibility; change it later with `setVisible`. */
        val visible: Boolean = true,
        /** The bubble height bounds in dp; 50 and 600 are the shared v0.1.0 values (docs/integration.md, "Bubble sizing"). */
        val minHeightDp: Double = 50.0,
        val maxHeightDp: Double = 600.0,
        /** Badge text shown by the renderer over a dead widget (user-facing, no error details). */
        val stoppedLabel: String = "Widget stopped",
        /** One JSThread for every validator instead of a thread per widget (TrustedValidator.sharedThread). */
        val sharedValidatorThread: Boolean = false,
        /** Take a prewarmed validator from ValidatorPool when it has one, and recycle it on destroy (implies the shared thread). */
        val pools: Boolean = false,
        /** "strict" (default; anything but "compat") or "compat": the gesture gate, the text budgets and the counted caps past which the widget stops (32 KB of text, …). */
        val policy: String = "strict",
        /** Opt-in sandbox features, e.g. "webAnimations"; see [SandboxConfig.features]. */
        val features: Set<String> = emptySet(),
        /** The client's UI language, BCP 47 (`ru`, `pt-BR`): `navigator.language` and the Intl default; `en-US` when malformed. */
        val locale: String = SdkPolicy.DEFAULT_LOCALE,
        /** Web Storage of this message as the client keeps it (the preload); over the 4 KB quota it is dropped whole (`storage-rejected`). */
        val storage: WidgetStorage = WidgetStorage(),
        /** The bot's `chatData` and `publicData` as the message has them now (docs/sdk-v1.md §2.13); later edits go through `setData`. */
        val data: WidgetData = WidgetData(),
        /** The message's flags from the server (`isCopy`, `canQuery`, `backgroundQuery`), the chat type and the viewer (docs/sdk-v1.md §4.4); without `canQuery` every request is refused. */
        val context: WidgetContext = WidgetContext(),
        /**
         * What the client draws over the widget (docs/sdk-v1.md §2.17, §5.12): for a widget with no caption below it the band of
         * the message time at the bottom and its corner; zero (the default) with a caption below it. Pass it here, before the first
         * render, so the first height already has the band; later only with the bubble's layout ([setContentSafeArea]).
         */
        val contentSafeArea: WidgetContentSafeArea = WidgetContentSafeArea(),
    )

    val view: View get() = renderer.view
    val diagnostics = WidgetDiagnostics()
    var onChange: ((WidgetDiagnostics) -> Unit)? = null
    /** What the widget asked the client to do, checked (URL rules, one link per tap, the haptic rate limit; docs/sdk-v1.md §5.2, §5.3). */
    var onAction: ((WidgetAction) -> Unit)? = null
    /** The new full content of a Web Storage area ("local" | "session") at the end of each task that changed it, in order (docs/sdk-v1.md §5.4). */
    var onStorageCommit: ((area: String, values: Map<String, String>) -> Unit)? = null
    /**
     * A request the widget sends to the bot (`TelegramWidget.request`, docs/sdk-v1.md §2.14), checked: the data,
     * [WidgetContext.canQuery], a press (or the background rules of [WidgetContext.backgroundQuery]), one in flight. Send it like a callback
     * button's press and call the completion once, from any thread, with the bot's answer (its toast, alert and URL the
     * client shows itself) or `Result.failure(WidgetRequestException(…))`; any other exception is FAILED. Later calls,
     * and calls after `destroy()` or the widget's death, are ignored. Runs on the main thread, and is read there when a
     * request arrives, so it may be set after the constructor; null then refuses the request (NOT_ALLOWED, `unsupported`).
     */
    var onRequest: ((WidgetRequest, (Result<WidgetRequestAnswer>) -> Unit) -> Unit)? = null
    /**
     * A wallet call (`TelegramWidget.wallet`, docs/sdk-v1.md §2.18; checked: the transaction or the payload, a press or the
     * grant, one call in flight with the requests). Only with [WidgetContext.wallet]. Show the client's own wallet window
     * with the bot of the message; on confirmation send the transaction (or sign for `bot<bot_id>`), send the outcome to
     * the bot as a query with `call.data` and call the completion once, from any thread, with the bot's answer, or
     * `Result.failure(WidgetRequestException(REJECTED | BAD_REQUEST | …))`. Main thread, read at delivery; null then
     * refuses the call (NOT_ALLOWED, `unsupported`).
     */
    var onWallet: ((WalletCall, (Result<WidgetRequestAnswer>) -> Unit) -> Unit)? = null
    /** Copy this text to the clipboard and show a toast that says what was copied (`TelegramWidget.copyText`, checked: 1–256 characters, one per press). Main thread. */
    var onCopyText: ((String) -> Unit)? = null
    /**
     * The widget started (true) or stopped (false) watching its shared `public_data` (`TelegramWidget.watchPublicData`,
     * docs/sdk-v1.md §2.13): poll `getWidgetPublicData` for it only while it watches and is on screen, and only when the
     * message has a `widget_id`, then `setData`. Called on a change only, on the main thread; `diagnostics.watchesPublicData`
     * has the latest.
     */
    var onWatchPublicData: ((Boolean) -> Unit)? = null
    /**
     * On every primary pointerdown: what of this press the widget claims with CSS `touch-action` (docs/sdk-v1.md §5.5,
     * GesturePolicy.kt): horizontal drags (`x`) and holds (`hold`: no message menu); taps and double taps are always the
     * widget's, vertical drags always the chat's (`y` is always false since v0.1.3). A client's recognizers feed it to a
     * GestureArbiter with the press; it comes a few ms after the touch starts. The WebView renderer keeps its parent from
     * intercepting while a `hold` press stays within the slop and after it leaves it horizontally; a vertical drag is
     * always left to the list (RendererWebView.gestureDecision).
     */
    var onGesture: ((WidgetGestureClaim) -> Unit)? = null
    /** The claim's drags, right after `onGesture` (`y` always false since v0.1.3). */
    @Deprecated("Since v0.1.2: onGesture, whose claim also carries hold")
    var onGestureClaim: ((x: Boolean, y: Boolean) -> Unit)? = null
    /** The raw method and arguments JSON of each call `onAction` gets, right after it. */
    @Deprecated("Since SDK v1: onAction")
    var onBridge: ((String, String) -> Unit)? = null
    /** Non-fatal uncaught exception in widget code (also in `diagnostics.logs`). */
    var onError: ((String) -> Unit)? = null
    private var tasksAtError = 0

    private val main = Handler(Looper.getMainLooper())
    private val engine: SandboxEngine
    private val gestures = GestureCounter()
    private val validator: TrustedValidator
    private val renderer: Renderer
    private val options = options
    /** [WidgetContext.preview]: an inline result not sent yet takes no input. */
    private val preview = options.context.preview
    /** The client's theme, its scheme normalised and its palette checked. */
    private var theme = checkedTheme(theme)
    private val scheme get() = theme.colorScheme
    private var rendererReady = false
    private val pendingToRenderer = ArrayList<String>()
    @Volatile private var destroyed = false
    private var viewport = Triple(widthDp, 300.0, context.resources.displayMetrics.density.toDouble())
    private var ownerVisible = options.visible
    private var inWindow = false
    private var qid = 0
    private var pendingQuery: PendingQuery? = null
    private val queryLock = Any()
    private val createdAt = System.nanoTime()
    private var opsSent = 0
    private fun sinceCreatedMs() = (System.nanoTime() - createdAt) / 1e6

    init {
        renderer = rendererFactory?.create(context, resources, scheme, widthDp) ?: RendererWebView(context, resources.rendererBundle, scheme)
        val diagSink: (String, String) -> Unit = { k, d -> main.post { if (!destroyed) diag(k, d) } }
        val pooled = if (options.pools) ValidatorPool.take(scheme) else null
        if (pooled != null) {
            pooled.onDiag = diagSink
            validator = pooled
            diagnostics.prewarmed = "validator"
        } else {
            validator = TrustedValidator(scheme, resources, if (options.pools || options.sharedValidatorThread) TrustedValidator.sharedThread else null, diagSink)
        }
        val config = SandboxConfig(
            innerWidth = viewport.first, innerHeight = viewport.second, devicePixelRatio = viewport.third, theme = scheme,
            memoryLimitBytes = options.memoryLimitBytes, cpuLimitMs = options.cpuLimitMs,
            cpuQuotaMs = options.cpuQuotaMs, cpuQuotaWindowMs = options.cpuQuotaWindowMs, cpuKillAfterMs = options.cpuKillAfterMs,
            mainThreadQuotaMs = options.mainThreadQuotaMs, maxTimers = options.maxTimers, visible = options.visible, policy = options.policy, features = options.features,
            themeParams = this.theme.params, locale = options.locale, contentSafeArea = options.contentSafeArea, storage = options.storage,
            data = options.data, context = options.context,
        )
        if (isStrictPolicy(options.policy)) validator.setPolicy("strict")
        engine = SandboxEngine(html, config, resources)
        engine.delegate = this
        // The validator's time on the widget's messages counts against its JS quota (docs/decisions.md, "The validator's time counts").
        validator.cpuSink = { ms -> engine.chargeValidator(ms) }
        renderer.onDiag = { k, d -> diag(k, d) }
        renderer.onMessage = { json -> onRenderer(json) }
        // A preview takes no input: the renderer leaves every press to the client (the inline-results list) at once.
        if (preview) renderer.setPreview(true)
        renderer.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { inWindow = true; pushVisibility() }
            override fun onViewDetachedFromWindow(v: View) { inWindow = false; pushVisibility() }
        })
        // The theme goes first (queued until `ready`, which flushes it before the engine starts).
        toRenderer(themeMessage())
        toRenderer(safeAreaMessage(options.contentSafeArea))
        // Under strict the renderer counts heavy boxes and reports `limit` past them (docs/decisions.md "Heavy boxes"); compat counts nothing.
        toRenderer(JSONObject().put("type", "policy").put("policy", if (isStrictPolicy(options.policy)) "strict" else "compat").toString())
        // The engine starts once the renderer announced itself, so the very first
        // layout queries have something to measure.
        renderer.load()
    }

    /**
     * The host owns the theme: stylesheets are re-sanitized against the scheme
     * (prefers-color-scheme, color-scheme, light-dark()), matchMedia queries are
     * answered from it, the renderer gets the variables, and the sandbox gets the
     * scheme and the palette (`themeChanged` in the widget).
     */
    fun setTheme(theme: WidgetTheme) {
        this.theme = checkedTheme(theme)
        toRenderer(themeMessage())
        toRenderer("{\"type\":\"ops\",\"ops\":${validator.setTheme(scheme)}}")
        if (stopOverLimit()) return // a sheet re-sanitized for the scheme passed a cap
        engine.theme(scheme, this.theme.params)
    }

    @Deprecated("Since SDK v1: setTheme(WidgetTheme)", ReplaceWith("setTheme(WidgetTheme(theme))"))
    fun setTheme(theme: String) = setTheme(WidgetTheme(theme))

    /**
     * What the client draws over the widget changed with the bubble's layout (docs/sdk-v1.md §2.17, §5.12): a caption
     * added or removed by an edit, an "edited" mark that widens the time. The renderer gets the root variables and the
     * widget `contentSafeAreaInset` and `contentSafeAreaChanged`, from this one call; never per frame.
     */
    fun setContentSafeArea(area: WidgetContentSafeArea) {
        if (destroyed) return
        toRenderer(safeAreaMessage(area))
        engine.contentSafeArea(area)
    }

    /** `{type:'safeArea', inset}` (docs/sdk-v1.md §3.4): the renderer core checks the values and sets the `--tg-content-safe-area-*` variables. */
    private fun safeAreaMessage(area: WidgetContentSafeArea) = JSONObject().put("type", "safeArea").put("inset", area.json()).toString()

    /**
     * The bot's data changed (an edit the client got from the server): the full state of both strings. The widget gets
     * `chatDataChanged`, then `publicDataChanged`, for each that differs; a copy keeps no `chatData`, its `publicData` applies.
     */
    fun setData(data: WidgetData) = engine.setData(data)

    private fun checkedTheme(t: WidgetTheme) = t.copy(colorScheme = if (t.colorScheme == "dark") "dark" else "light", params = SdkPolicy.checkThemeParams(t.params))

    /** `{type:'theme', theme: WidgetTheme}` with the TS field names (docs/sdk-v1.md §3.4): the renderer core checks the tokens and sets the CSS variables. */
    private fun themeMessage(): String {
        val t = JSONObject().put("colorScheme", scheme).put("params", JSONObject(theme.params))
        val b = theme.button
        val button = JSONObject()
        b.bg?.let { button.put("bg", it) }
        b.text?.let { button.put("text", it) }
        b.radius?.let { button.put("radius", it) }
        b.font?.let { button.put("font", it) }
        b.padding?.let { button.put("padding", it) }
        b.height?.let { button.put("height", it) }
        b.pressedOpacity?.takeIf { it.isFinite() }?.let { button.put("pressedOpacity", it) }
        b.disabledOpacity?.takeIf { it.isFinite() }?.let { button.put("disabledOpacity", it) }
        if (button.length() > 0) t.put("button", button)
        val x = theme.text
        val text = JSONObject()
        x.fontSize?.takeIf { it.isFinite() }?.let { text.put("fontSize", it) }
        x.lineHeight?.takeIf { it.isFinite() }?.let { text.put("lineHeight", it) }
        x.padding?.let { text.put("padding", it) }
        if (text.length() > 0) t.put("text", text)
        return JSONObject().put("type", "theme").put("theme", t).toString()
    }

    /**
     * Off-screen pause. The owner reports whether the bubble is on screen (scrolled away, activity
     * stopped, …); the view's own window attachment is combined with it. Hidden: no animation frames,
     * timers at 1 Hz, `document.hidden` / `visibilitychange` inside the widget.
     */
    fun setVisible(visible: Boolean) {
        ownerVisible = visible
        pushVisibility()
    }

    private fun pushVisibility() = engine.setVisible(ownerVisible && inWindow)

    /** Trusted script evaluated in the renderer (tests and diagnostics only). */
    fun evaluateInRenderer(script: String, callback: (String) -> Unit) = renderer.evaluate(script) { callback(it ?: "null") }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        engine.terminate()
        renderer.destroy()
        if (options.pools) ValidatorPool.recycle(validator) else validator.close()
        resolveQuery("{\"error\":\"dead\"}", null)
    }

    // MARK: engine → host (engine thread)

    override fun onOps(engine: SandboxEngine, json: String) {
        main.post {
            if (destroyed || diagnostics.dead != null) return@post
            val clean = validator.processOps(json)
            if (stopOverLimit()) return@post
            if (clean != "[]") {
                toRenderer("{\"type\":\"ops\",\"ops\":$clean}")
                opsSent += 1
            }
            diagnostics.nodes = validator.nodeCount
        }
    }

    override fun onQuery(engine: SandboxEngine, json: String): SandboxEngine.QueryAnswer {
        val pending = PendingQuery()
        synchronized(queryLock) { pendingQuery = pending }
        main.post {
            if (destroyed) return@post resolveQuery("{\"error\":\"dead\"}", null)
            var clean = validator.validateQuery(json) ?: return@post resolveQuery("{\"error\":\"invalid query\"}", null)
            if (clean.contains("\"matchMedia\"")) {
                try {
                    val obj = JSONObject(clean)
                    if (obj.has("query")) {
                        obj.put("query", validator.rewriteMediaQuery(obj.getString("query")))
                        clean = obj.toString()
                    }
                } catch (e: Exception) {
                }
            }
            qid += 1
            pending.id = qid
            toRenderer("{\"type\":\"query\",\"qid\":$qid,\"query\":$clean}")
        }
        if (!pending.semaphore.tryAcquire(3, TimeUnit.SECONDS)) {
            synchronized(queryLock) { if (pendingQuery === pending) pendingQuery = null }
            return SandboxEngine.QueryAnswer("{\"error\":\"timeout\"}", null)
        }
        return SandboxEngine.QueryAnswer(pending.json, pending.binary)
    }

    private fun resolveQuery(json: String, binary: ByteArray?) {
        val p = synchronized(queryLock) { pendingQuery.also { pendingQuery = null } } ?: return
        p.json = json
        p.binary = binary
        p.semaphore.release()
    }

    override fun onListen(engine: SandboxEngine, json: String) {
        main.post { if (!destroyed) toRenderer("{\"type\":\"listen\",\"types\":${validator.validateListen(json)}}") }
    }
    override fun onPreventDefault(engine: SandboxEngine, json: String) {
        main.post { if (!destroyed) toRenderer("{\"type\":\"preventDefault\",\"keys\":${validator.validatePreventDefault(json)}}") }
    }
    override fun onAnim(engine: SandboxEngine, json: String) {
        main.post {
            if (destroyed) return@post
            val anims = validator.validateAnim(json)
            if (anims != "[]") toRenderer("{\"type\":\"anim\",\"now\":${QuickJSContext.nativeNow() / 1e6},\"anims\":$anims}")
        }
    }
    override fun onRequestFrame(engine: SandboxEngine) {
        main.post { toRenderer("{\"type\":\"requestFrame\"}") }
    }
    override fun onLog(engine: SandboxEngine, level: String, text: String) {
        main.post {
            if (diagnostics.logs.size < 5000) diagnostics.logs.add(level to text)
            if (level == "error" && text.startsWith("Uncaught ")) {
                diagnostics.lastError = text
                tasksAtError = diagnostics.stats.tasks
                onError?.invoke(text)
            }
            changed()
        }
    }
    override fun onDiag(engine: SandboxEngine, kind: String, detail: String) {
        main.post { diag(kind, detail) }
    }
    override fun onAction(engine: SandboxEngine, action: WidgetAction) {
        main.post {
            if (destroyed || diagnostics.dead != null) return@post
            if (diagnostics.actions.size < 1000) diagnostics.actions.add(action)
            onAction?.invoke(action)
            changed()
        }
    }
    override fun onBridge(engine: SandboxEngine, method: String, argsJson: String) {
        main.post {
            diagnostics.bridge.add(method to argsJson)
            @Suppress("DEPRECATION")
            onBridge?.invoke(method, argsJson)
            changed()
        }
    }
    override fun onStorageCommit(engine: SandboxEngine, area: String, values: Map<String, String>) {
        main.post { if (!destroyed && diagnostics.dead == null) onStorageCommit?.invoke(area, values) }
    }
    /**
     * `onRequest` is read here, on the main thread at delivery, not when the widget asked: a client that sets it after the
     * constructor still gets a request the widget made at boot. The engine's `respond` honours the first call only, from
     * any thread; a client that throws has failed the request (FAILED).
     */
    override fun onRequest(engine: SandboxEngine, request: WidgetRequest, respond: (Result<WidgetRequestAnswer>) -> Unit) {
        main.post {
            if (destroyed || diagnostics.dead != null) return@post
            val handler = onRequest ?: return@post respond(Result.failure(WidgetRequestUnsupported()))
            try {
                handler(request, respond)
            } catch (e: Exception) {
                respond(Result.failure(e))
            }
        }
    }
    /** `onWallet`, read on the main thread at delivery as `onRequest` is. */
    override fun onWallet(engine: SandboxEngine, call: WalletCall, respond: (Result<WidgetRequestAnswer>) -> Unit) {
        main.post {
            if (destroyed || diagnostics.dead != null) return@post
            val handler = onWallet ?: return@post respond(Result.failure(WidgetRequestUnsupported()))
            try {
                handler(call, respond)
            } catch (e: Exception) {
                respond(Result.failure(e))
            }
        }
    }
    override fun onCopyText(engine: SandboxEngine, text: String) {
        main.post { if (!destroyed && diagnostics.dead == null) onCopyText?.invoke(text) }
    }
    /**
     * The latest anchor goes into the state next to the height (no callback of its own: `onChange`, coalesced). Posted
     * from the engine thread during the task, so it runs on main before that task's ops (posted at its end), hence
     * before the renderer lays them out and before the `size` report that follows.
     */
    override fun onResizeAnchor(engine: SandboxEngine, edge: String) {
        main.post {
            if (destroyed || diagnostics.resizeAnchor == edge) return@post
            diagnostics.resizeAnchor = edge
            changed()
        }
    }
    /** Like the anchor, into the state; the client's callback on a change only. */
    override fun onWatchPublicData(engine: SandboxEngine, on: Boolean) {
        main.post {
            if (destroyed || diagnostics.watchesPublicData == on) return@post
            diagnostics.watchesPublicData = on
            onWatchPublicData?.invoke(on)
            changed()
        }
    }
    override fun onLoaded(engine: SandboxEngine) {
        val loadedMs = sinceCreatedMs()
        main.post {
            // After the loading tasks' ops (posted before this): text changes are rate-limited from here on.
            if (!destroyed) validator.markLoaded()
            diagnostics.loaded = true; diagnostics.loadedMs = loadedMs; changed()
        }
    }
    override fun onStats(engine: SandboxEngine, stats: SandboxStats) {
        main.post {
            diagnostics.stats = stats
            if (diagnostics.lastError != null && stats.tasks > tasksAtError + 1) diagnostics.lastError = null
            changed()
        }
    }
    override fun onDied(engine: SandboxEngine, reason: String) {
        main.post { die(reason) }
    }

    /** The widget is gone for good (memory, CPU, layout queries, a counted cap); main thread. */
    private fun die(reason: String) {
        if (diagnostics.dead != null) return
        diagnostics.dead = reason
        changed()
        resolveQuery("{\"error\":\"dead\"}", null)
        // The WebView stays: last render as fallback, badge from the renderer, no more events or frames.
        toRenderer(JSONObject().put("type", "frozen").put("reason", options.stoppedLabel).put("fatal", true).toString())
    }

    /**
     * After a validator call: a counted cap passed under strict (the validator's `limit`; its batch and every later one
     * came back empty) stops the widget like memory or CPU does, with the cap as the reason. The engine refuses the
     * widget's next host call and frees the runtime; what it sent after the batch is dropped here.
     */
    private fun stopOverLimit(): Boolean {
        val reason = validator.stopped ?: return false
        die(reason)
        engine.stop(reason)
        return true
    }

    // MARK: renderer → host (main thread)

    private fun onRenderer(json: String) {
        if (destroyed) return
        val msg = try { JSONObject(json) } catch (e: Exception) { return }
        when (msg.optString("type")) {
            "ready" -> {
                if (rendererReady) return
                rendererReady = true
                diagnostics.rendererReadyMs = sinceCreatedMs()
                val queued = ArrayList(pendingToRenderer)
                pendingToRenderer.clear()
                queued.forEach(renderer::send)
                engine.start()
            }
            // A preview takes no input: a tap on it selects the result (the client's), and the widget gets nothing.
            "event" -> if (!preview) (msg.opt("ev") as? JSONObject)?.let { ev ->
                // Any pointerdown begins a press, trusted or not: a second finger's is neither trusted nor claimed (`gesture`
                // is the primary pointer's), and without this its release continued the press before it.
                if (ev.optString("type") == "pointerdown") gestures.pressBegan()
                // Only input the renderer saw as real (the DOM event's isTrusted; every input event of a native renderer) is a gesture.
                val gesture = if (msg.opt("trusted") == true) gestures.gestureFor(ev.optString("type"), ev.optDouble("detail", 0.0)) else 0
                if (gesture > 0) engine.activationEvent(ev.toString(), gesture) else engine.event(ev.toString())
            }
            "gesture" -> {
                // A primary press began (renderer-made: the widget cannot fake it): haptics are free while it lasts, and 5 s after.
                // Before the preview check: harmless, a preview gets no input and no haptics anyway.
                engine.press()
                gestures.pressBegan()
                if (preview) return
                // A preview claims nothing (no onGesture / onGestureClaim); its renderer already leaves every press to the client.
                // Vertical drags are the chat's (v0.1.3): the claim's `y` is false whatever the renderer sends. The renderer
                // core still sends `wants` / `y` (always false now); the Blitz and litehtml prototypes send only `wants` (their
                // old vertical claim), so they now claim nothing.
                val claim = WidgetGestureClaim(msg.optBoolean("x", false), y = false, hold = msg.optBoolean("hold", false))
                renderer.gestureDecision(claim)
                onGesture?.invoke(claim)
                @Suppress("DEPRECATION")
                onGestureClaim?.invoke(claim.x, claim.y)
            }
            "result" -> {
                val id = msg.optInt("qid", -1)
                val match = synchronized(queryLock) { pendingQuery?.id == id }
                if (!match) return
                val binary = msg.optString("binaryBase64", "").takeIf { it.isNotEmpty() }?.let { try { Base64.decode(it, Base64.DEFAULT) } catch (e: Exception) { null } }
                val result = msg.opt("result")
                resolveQuery(if (result == null) "null" else JSONObject.wrap(result)?.toString() ?: "null", binary)
            }
            "size" -> {
                val height = msg.optDouble("height", 0.0).coerceIn(options.minHeightDp, options.maxHeightDp)
                if (diagnostics.firstContentMs == null && opsSent > 0) {
                    diagnostics.firstContentMs = sinceCreatedMs()
                    changed()
                }
                if (height != diagnostics.height) {
                    diagnostics.height = height
                    changed()
                }
                val vp = Triple(msg.optDouble("innerWidth", viewport.first), msg.optDouble("innerHeight", viewport.second), msg.optDouble("dpr", viewport.third))
                if (vp != viewport) {
                    viewport = vp
                    engine.viewport(vp.first, vp.second, vp.third)
                }
            }
            // The primary press ended (pointerup or pointercancel), listened to by the widget or not: the haptic gate's touch window starts.
            "release" -> engine.release()
            "resize" -> msg.optJSONArray("entries")?.let { engine.resize(it.toString()) }
            "frame" -> engine.frame(msg.optDouble("time", 0.0))
            // OS-level change inside the renderer: the host theme is authoritative, just re-notify.
            "media" -> engine.theme(scheme, theme.params)
            "violation" -> diag("renderer-violation", msg.optString("detail"))
            "limit" -> {
                // Past a paint limit (16 heavy boxes, 2 with backdrop-filter; the WebView renderer's message or a client
                // renderer's `limitExceeded`): the renderer drew the frame without the effects, and the widget stops as
                // for memory or CPU. compat counts nothing.
                val limit = WidgetPaintLimit.of(msg.optString("code"))
                val count = msg.opt("count") as? Number
                if (limit == null || count == null || !count.toDouble().isFinite() || count.toDouble() < 0) {
                    diag("renderer-violation", "a limit report with an unknown code or no count")
                    return
                }
                if (!isStrictPolicy(options.policy) || diagnostics.dead != null) return
                val reason = limit.message(count.toDouble().toInt())
                diag(limit.code, reason)
                die(reason)
                engine.stop(reason)
            }
        }
    }

    private fun toRenderer(json: String) {
        if (destroyed) return
        if (rendererReady) renderer.send(json) else pendingToRenderer.add(json)
    }

    private fun diag(kind: String, detail: String) {
        val entry = diagnostics.diags.getOrPut(kind) { DiagEntry() }
        entry.count += 1
        if (entry.details.size < 50) entry.details.add(detail)
        changed()
    }

    private var changeScheduled = false
    private fun changed() {
        // Coalesce UI updates: a misbehaving widget can emit thousands of diagnostics per second.
        if (changeScheduled) return
        changeScheduled = true
        main.post {
            changeScheduled = false
            if (!destroyed) onChange?.invoke(diagnostics.copy())
        }
    }

    private class PendingQuery {
        val semaphore = Semaphore(0)
        var id = 0
        var json = "null"
        var binary: ByteArray? = null
    }
}
