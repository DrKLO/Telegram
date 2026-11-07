// SDK v1 checks (docs/sdk-v1.md §2.5, §2.6, §2.8, §2.9, §2.12–§2.15, §3): a line-for-line
// twin of the checked part of src/shared/sdk-policy.ts (see the comments
// there), SdkPolicy.swift and sdk_policy.cpp. The theme defaults and the
// renderer's CSS variables are TS only: the renderer core builds them from the
// `theme` message.
//
// Pure and side-effect free; checked against tests/fixtures/sdk-policy.json
// (copied to src/androidTest/assets by `pnpm build:android`), so a rule change
// goes to the four copies and the fixture together.
//
// Inputs are JSON values as org.json parses them (JSONObject, JSONArray,
// String, Number, Boolean, JSONObject.NULL) or the Kotlin equivalents (Map
// with String keys, List); anything else is "not an object" / "not an array".

package org.telegram.widgetsandbox

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A bridge call that passed its checks (the TS `BridgeCall`, docs/sdk-v1.md §3.2): an action for the client
 * ([WidgetAction], `onAction`); the resize anchor ([SetResizeAnchor]), which no action carries: the host keeps
 * the latest one next to the height it reports (WidgetDiagnostics.resizeAnchor); a request to the bot ([Request]:
 * the request gate, then the client's `onRequest`); a text to copy ([CopyText]: `onCopyText`); whether the widget
 * watches its shared `public_data` ([WatchPublicData]: kept like the anchor, WidgetDiagnostics.watchesPublicData).
 * Since v0.1.3, the result of [SdkPolicy.checkBridgeCall]; [WidgetAction] and its cases are unchanged (the bot's
 * requests add no action).
 */
sealed interface BridgeCall {
    /** `TelegramWidget.setResizeAnchor(edge)`: [SdkPolicy.RESIZE_ANCHORS], "top" | "bottom". */
    data class SetResizeAnchor(val edge: String) : BridgeCall
    /**
     * `TelegramWidget.request(data, options)` (docs/sdk-v1.md §2.14): the shim's id (1…[SdkPolicy.MAX_REQUEST_ID]) and the data (1–64
     * UTF-8 bytes); `wallet`: the widget asks the server to attach the user's wallet (§2.18).
     */
    data class Request(val id: Int, val data: String, val wallet: Boolean = false) : BridgeCall
    /** `TelegramWidget.wallet.sendTransaction` / `.signData` (docs/sdk-v1.md §2.18): the request gate, then the client's `onWallet`. */
    data class Wallet(val id: Int, val call: WalletCall) : BridgeCall
    /** `TelegramWidget.copyText(text)` (§2.15): 1–256 UTF-16 code units, no lone surrogate. */
    data class CopyText(val text: String) : BridgeCall
    /** `TelegramWidget.watchPublicData()` / `unwatchPublicData()` (docs/sdk-v1.md §2.13): exactly one boolean; the shim sends only changes. */
    data class WatchPublicData(val on: Boolean) : BridgeCall
}

/** What the widget asked the client to do (docs/sdk-v1.md §3.2, §4.2), after the glue's checks. */
sealed class WidgetAction : BridgeCall {
    /** The widget has rendered its initial state. */
    data object Ready : WidgetAction()
    /** An external https: page; the client confirms with the full URL. Scheme and host lowercase, non-ASCII percent-encoded. */
    data class OpenLink(val url: String) : WidgetAction()
    /** A t.me / telegram.me / telegram.dog link, for the client's own handling. */
    data class OpenTelegramLink(val url: String) : WidgetAction()
    /** `kind`: impact | notification | selection; `value`: the impact style or notification type, null for selection. */
    data class Haptic(val kind: String, val value: String?) : WidgetAction()
}

/** A storage commit or preload: the areas present, each in full (null = not part of it). */
data class StorageCommit(val local: Map<String, String>? = null, val session: Map<String, String>? = null)

/** The bot's data as the widget sees it (null for none), and the fields the client passed that broke the rules ("chatData", "publicData", in this order; the glue reports `data-rejected`). */
data class CheckedWidgetData(val chatData: String?, val publicData: String?, val rejected: List<String> = emptyList())

/** An answer the glue passes into the widget: the bot's `result`, null for none (the request closes without one). */
data class CheckedAnswer(val result: String?)

/** The viewer as the widget may see it (null: no viewer), and the fields that broke their rules ([SdkPolicy.VIEWER_FIELD_RULES] order; the glue reports `data-rejected`). */
data class CheckedViewer(val viewer: WidgetViewer?, val rejected: List<String> = emptyList())

/**
 * The verdict on a request (docs/sdk-v1.md §2.14): [FOREGROUND] (it consumes the task's press) and [BACKGROUND] go
 * to the client; [HELD] (a background request from a widget off screen) waits in the glue and goes to the client when
 * the widget appears on screen ([RequestGate.show]); the rest are refused: [DISABLED] (the server allows no requests
 * for the message: no `can_query`), [BUSY] (one request in flight per widget, a held one included), [NO_ACTIVATION]
 * (no press and no `background_query`), [RATE_LIMITED] (a background request sooner than 5 s after the last one). A
 * wallet call ([RequestGate.decideWallet]), or a request that asks for the wallet, goes out only as [FOREGROUND] or
 * [GRANTED] (no press, but within 10 s of the answer to a call made by a tap); [NO_WALLET] when the client has no
 * wallet for this user.
 */
enum class RequestVerdict(val value: String) {
    FOREGROUND("foreground"), BACKGROUND("background"), HELD("held"), GRANTED("granted"), DISABLED("disabled"), NO_WALLET("no-wallet"), BUSY("busy"),
    NO_ACTIVATION("no-activation"), RATE_LIMITED("rate-limited"),
}

enum class HapticDecision { PLAY, DROP, REPORT }

/**
 * Haptics need a visible widget and room in the bucket (5 calls, refilled at 10 per second), and a touch: while a
 * finger is down on the widget and for 5 s after it is lifted they are free (under the rate limit); outside that, the
 * first call opens a series whose first second passes, then nothing until the next touch, or 10 s after the series
 * began when there was none (docs/sdk-v1.md §2.6; a widget cannot buzz the phone from a timer). [press] / [release]
 * are the host's trusted presses (the renderer's claim at a primary pointerdown and its release), never the widget's
 * events. PLAY delivers; DROP drops silently; REPORT drops and reports `haptic-limit` (at most once per second).
 * Times in ms.
 */
class HapticGate {
    private var tokens = SdkPolicy.HAPTIC_BURST.toDouble()
    private var last: Double? = null
    private var reported: Double? = null
    /** A press is on since then. */
    private var down: Double? = null
    /** The last press ended then. */
    private var lifted: Double? = null
    /** The last series (haptics outside a touch) began then. */
    private var series: Double? = null

    /** A finger or a mouse button went down on the widget (trusted input only). */
    fun press(now: Double) {
        down = now
    }

    /** The press ended: the finger was lifted or the press was cancelled. */
    fun release(now: Double) {
        down = null
        lifted = now
    }

    fun decide(now: Double, visible: Boolean): HapticDecision {
        last?.let { tokens = min(SdkPolicy.HAPTIC_BURST.toDouble(), tokens + (max(0.0, now - it) * SdkPolicy.HAPTIC_PER_SECOND) / 1000) }
        last = now
        down?.let { if (now - it > SdkPolicy.HAPTIC_PRESS_MAX_MS) release(it + SdkPolicy.HAPTIC_PRESS_MAX_MS) }
        val touching = down != null || lifted.let { it != null && now - it <= SdkPolicy.HAPTIC_TOUCH_WINDOW_MS }
        var allowed = visible
        if (allowed && !touching && !series.let { it != null && now - it < SdkPolicy.HAPTIC_SERIES_MS }) {
            // A new series: after a touch at any time (one per touch), else 10 s after the last one began.
            val s = series
            val l = lifted
            val touchedSince = l != null && (s == null || l > s)
            if (s == null || touchedSince || now - s >= SdkPolicy.HAPTIC_SERIES_GAP_MS) series = now else allowed = false
        }
        if (allowed && tokens >= 1) {
            tokens -= 1
            return HapticDecision.PLAY
        }
        reported?.let { if (now - it < SdkPolicy.HAPTIC_REPORT_MS) return HapticDecision.DROP }
        reported = now
        return HapticDecision.REPORT
    }
}

/**
 * The server's flags decide: no `canQuery`, no request; one request in flight per widget, a held one included; a press
 * unlocks one; without a press only with `backgroundQuery` (the original in the private chat with the bot): one when
 * the widget appears on screen ([show]) and then at most one per 5 s while it stays visible, counted from the start of
 * the last background request; one made while it is off screen is held (HELD) and is the one of its next appearance
 * ([show] returns true: the glue sends it then). `gesture`: the task has a press no privileged call consumed yet (the
 * glue consumes it on FOREGROUND). [finish] when the answer or the failure has gone into the widget, or when the glue
 * drops a held request (the widget stopped); `answeredAt` when an answer with no error went in then. Times in ms.
 *
 * The wallet (docs/sdk-v1.md §2.18): only with `wallet` (the client has Gram Wallet for this user). A request that asks
 * for the wallet (`withWallet`) and a wallet call ([decideWallet]) need the visible widget and a press, or the grant:
 * the answer to the last call made by a press or a grant (a request or a wallet call) lets one wallet call follow within
 * 10 s. Any call that goes out as FOREGROUND or GRANTED uses up the grant; its answer brings the next one.
 */
class RequestGate {
    private var inFlight = false
    private var held = false
    private var lastBackground: Double? = null
    /** The call in flight went out on a press or the grant: its answer grants one wallet call. */
    private var granting = false
    private var grantAt: Double? = null

    val busy: Boolean get() = inFlight

    fun decide(now: Double, gesture: Boolean, visible: Boolean, canQuery: Boolean, backgroundQuery: Boolean, withWallet: Boolean = false, wallet: Boolean = false): RequestVerdict {
        if (!canQuery) return RequestVerdict.DISABLED
        if (withWallet && !wallet) return RequestVerdict.NO_WALLET
        if (inFlight) return RequestVerdict.BUSY
        if (gesture && visible) return out(RequestVerdict.FOREGROUND)
        if (!backgroundQuery || withWallet) return RequestVerdict.NO_ACTIVATION
        if (!visible) {
            inFlight = true
            held = true
            return RequestVerdict.HELD
        }
        lastBackground?.let { if (now - it < SdkPolicy.BACKGROUND_REQUEST_INTERVAL_MS) return RequestVerdict.RATE_LIMITED }
        lastBackground = now
        inFlight = true
        return RequestVerdict.BACKGROUND
    }

    /** A wallet call (`sendTransaction`, `signData`): on a press, or on the grant within 10 s of its answer. */
    fun decideWallet(now: Double, gesture: Boolean, visible: Boolean, canQuery: Boolean, wallet: Boolean): RequestVerdict {
        if (!canQuery) return RequestVerdict.DISABLED
        if (!wallet) return RequestVerdict.NO_WALLET
        if (inFlight) return RequestVerdict.BUSY
        if (gesture && visible) return out(RequestVerdict.FOREGROUND)
        grantAt?.let { if (visible && now - it <= SdkPolicy.WALLET_GRANT_MS) return out(RequestVerdict.GRANTED) }
        return RequestVerdict.NO_ACTIVATION
    }

    private fun out(verdict: RequestVerdict): RequestVerdict {
        inFlight = true
        granting = true
        grantAt = null
        return verdict
    }

    fun finish(answeredAt: Double? = null) {
        if (granting && answeredAt != null) grantAt = answeredAt
        inFlight = false
        held = false
        granting = false
    }

    /**
     * The widget appeared on screen (a hidden → visible transition): its next background request may go out at once.
     * True when a held request goes out now, as this appearance's background request (the glue sends it; it stays in flight).
     */
    fun show(now: Double): Boolean {
        lastBackground = null
        if (!held) return false
        held = false
        lastBackground = now
        return true
    }
}

object SdkPolicy {
    const val MAX_LINK_LENGTH = 256
    /** Bytes of keys and values (UTF-8) per storage area and message (4 KB: a 50-item to-do list; docs/decisions.md, "Strict limits from the start"). */
    const val STORAGE_QUOTA_BYTES = 4096
    const val MAX_LOCALE_LENGTH = 35
    const val DEFAULT_LOCALE = "en-US"
    const val HAPTIC_BURST = 5
    const val HAPTIC_PER_SECOND = 10
    /** `haptic-limit` is reported at most this often. */
    const val HAPTIC_REPORT_MS = 1000
    /** Haptics are free (under the rate limit) while a finger or a mouse button is down on the widget and this long after it is lifted. */
    const val HAPTIC_TOUCH_WINDOW_MS = 5000
    /** Outside a touch, the first haptic opens a series: the calls of its first second pass (under the rate limit), later ones are dropped. */
    const val HAPTIC_SERIES_MS = 1000
    /** Without a touch since the last series, the next one may start this long after the last one began. */
    const val HAPTIC_SERIES_GAP_MS = 10000
    /** A press whose end the host never saw (the finger left the view) counts as lifted this long after it began. */
    const val HAPTIC_PRESS_MAX_MS = 30000
    /** The widget profile this glue enforces (VERSION.json's `profile`, as `PROFILE_VERSION` in profile-tables.ts; docs/profile-v1.md). */
    const val PROFILE_VERSION = WidgetSandboxVersion.PROFILE
    /** Until the widget calls `setResizeAnchor`: the bubble grows and shrinks downward (docs/sdk-v1.md §2.12). */
    const val DEFAULT_RESIZE_ANCHOR = "top"
    /** The edges of the bubble that can stay put on screen when the widget's height changes (`setResizeAnchor`). */
    val RESIZE_ANCHORS = listOf("top", "bottom")

    // ── bot data and requests (docs/sdk-v1.md §2.13–§2.15) ──

    /** UTF-8 bytes of `chatData` and of `publicData` each (the server's limit, `chat_data` / `public_data`). */
    const val MAX_WIDGET_DATA_BYTES = 1024
    /** UTF-8 bytes of a request's `data` (as `callback_data`). */
    const val MAX_REQUEST_DATA_BYTES = 64
    /** UTF-8 bytes of an answer's `result`. */
    const val MAX_REQUEST_RESULT_BYTES = 1024
    /** UTF-16 code units of a `copyText` text (as the `copy_text` button's). */
    const val MAX_COPY_TEXT_LENGTH = 256
    /** A background request (not from a press) at most this often, per widget. */
    const val BACKGROUND_REQUEST_INTERVAL_MS = 5000
    /** The request ids the shim numbers its calls with. */
    const val MAX_REQUEST_ID = 2147483647
    /** A Telegram user id fits in 53 bits: a JS number holds it exactly. */
    const val MAX_VIEWER_ID = 9007199254740991L
    /** UTF-16 code units of the viewer's first and last name (each 1 to this). */
    const val MAX_VIEWER_NAME_LENGTH = 64
    /** The viewer's username: 5–32 characters of A–Z, a–z, 0–9 and _ (ASCII only). */
    val VIEWER_USERNAME = Regex("[A-Za-z0-9_]{5,32}")
    /** Why a viewer field was left out (`data-rejected`: `viewer.<field>: <why>; the widget does not see it`), in the order the check lists them. */
    val VIEWER_FIELD_RULES = linkedMapOf(
        "first_name" to "not text of 1–64 UTF-16 code units",
        "last_name" to "not text of 1–64 UTF-16 code units",
        "username" to "not 5–32 characters of A–Z, a–z, 0–9 and _",
        "is_premium" to "not a boolean",
    )
    /** The chat kinds a host may report (`Chat.type` of the Bot API); a value outside them is no chat type (null). */
    val CHAT_TYPES = listOf("private", "group", "supergroup", "channel")
    /** What a refused or failed request rejects with in the widget (`error.code`); REJECTED and BAD_REQUEST only for a wallet call (the user declined, the wallet refused it). */
    val REQUEST_ERRORS = listOf("NOT_ALLOWED", "BUSY", "RATE_LIMITED", "TIMEOUT", "FAILED", "INVALID_ANSWER", "REJECTED", "BAD_REQUEST")

    // ── the wallet (docs/sdk-v1.md §2.18): TON Connect's formats, Gram Wallet only ──

    /** Messages in one transaction: what Gram Wallet takes (its TON Connect `maxMessages`). */
    const val MAX_WALLET_MESSAGES = 255
    /** Characters of a message's `payload` or `stateInit` (a bag of cells in base64). */
    const val MAX_WALLET_BOC_LENGTH = 8192
    /** Characters of every message's `payload` and `stateInit` together. */
    const val MAX_WALLET_BOC_TOTAL = 65536
    /** UTF-8 bytes of a `signData` text; characters of its `bytes` or `cell` (base64). */
    const val MAX_SIGN_DATA_LENGTH = 4096
    /** UTF-8 bytes of a `cell` payload's TL-B schema. */
    const val MAX_SIGN_DATA_SCHEMA_BYTES = 1024
    /** After the answer to a call made by a tap (a request or a wallet call) reached the widget, one wallet call may follow without a new tap this long. */
    const val WALLET_GRANT_MS = 10000
    /** TON Connect's network ids: the mainnet and the testnet. */
    val WALLET_NETWORKS = listOf("-239", "-3")

    val THEME_KEYS = listOf(
        "bg_color", "text_color", "hint_color", "link_color", "button_color", "button_text_color",
        "secondary_bg_color", "accent_text_color", "subtitle_text_color", "destructive_text_color",
        "section_bg_color", "section_header_text_color", "section_separator_color", "header_bg_color", "bottom_bar_bg_color",
    )
    val HAPTIC_IMPACT_STYLES = listOf("light", "medium", "heavy", "rigid", "soft")
    val HAPTIC_NOTIFICATION_TYPES = listOf("error", "success", "warning")
    val TELEGRAM_HOSTS = listOf("t.me", "telegram.me", "telegram.dog")
    /** Methods that need the task's user gesture (docs/protocol.md, "User activation"); one press unlocks one of them. `request` has its own gate ([RequestGate]). */
    val PRIVILEGED_METHODS = setOf("openLink", "openTelegramLink", "copyText")
    /**
     * The bridge methods a preview (an inline result before it is sent: [WidgetContext.preview]) may call; everything
     * else is refused there: no request, link, copy or haptic. A preview gets no input either (its host drops the
     * renderer's events and gesture claims).
     */
    val PREVIEW_METHODS = setOf("ready", "setResizeAnchor")

    fun allowedInPreview(method: Any?): Boolean = method is String && method in PREVIEW_METHODS

    /** UTF-8 length of a UTF-16 string; a lone surrogate counts 3 bytes (what U+FFFD takes). */
    fun utf8Length(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i].code
            if (c < 0x80) n += 1
            else if (c < 0x800) n += 2
            else if (c in 0xd800..0xdbff && i + 1 < s.length && (s[i + 1].code and 0xfc00) == 0xdc00) { n += 4; i++ }
            else n += 3
            i++
        }
        return n
    }

    private const val HEX = "0123456789ABCDEF"
    private fun isHex(c: Int) = (c in 48..57) || (c in 65..70) || (c in 97..102)
    /** ASCII that may stand unescaped after the authority: RFC 3986 unreserved, reserved and `%`. */
    private const val URL_ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%"

    /** Lowercases A–Z only: a Unicode mapping (U+212A KELVIN SIGN → `k`) must not turn a non-ASCII host into an ASCII one. */
    private fun asciiLower(s: String): String {
        val b = StringBuilder(s.length)
        for (ch in s) b.append(if (ch in 'A'..'Z') ch + 32 else ch)
        return b.toString()
    }

    private fun isTelegramHost(host: String) = TELEGRAM_HOSTS.any { host == it || host.endsWith(".$it") }
    private fun isLdhLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 63 || label[0] == '-' || label[label.length - 1] == '-') return false
        return label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
    }

    /**
     * The URL a link call opens, or null when it breaks the rules: an absolute
     * `https:` URL, no user name or password, an ASCII host of LDH labels (an IDN
     * in its `xn--` form), an optional port, at most 256 characters once non-ASCII
     * characters after the host are percent-encoded as UTF-8. `openTelegramLink`
     * takes only t.me, telegram.me and telegram.dog (and their subdomains);
     * `openLink` every other host. Scheme and host come out lowercase.
     */
    fun checkLink(method: String, url: Any?): String? {
        if (url !is String || utf8Length(url) > MAX_LINK_LENGTH) return null
        if (asciiLower(url.take(8)) != "https://") return null
        var end = 8
        while (end < url.length && url[end] != '/' && url[end] != '?' && url[end] != '#') end++
        val authority = url.substring(8, end)
        if (authority.contains('@')) return null
        val colon = authority.indexOf(':')
        val host = asciiLower(if (colon < 0) authority else authority.substring(0, colon))
        val port = if (colon < 0) null else authority.substring(colon + 1)
        if (port != null && (port.length !in 1..5 || !port.all { it in '0'..'9' })) return null
        if (port != null && port.toInt() > 65535) return null
        if (host.isEmpty() || host.length > 253) return null
        for (label in host.split('.')) if (!isLdhLabel(label)) return null
        if (isTelegramHost(host) != (method == "openTelegramLink")) return null
        val rest = StringBuilder()
        var i = end
        while (i < url.length) {
            val c = url[i].code
            if (c < 0x80) {
                if (URL_ASCII.indexOf(url[i]) < 0) return null
                if (c == 37 && !(i + 2 < url.length && isHex(url[i + 1].code) && isHex(url[i + 2].code))) return null
                rest.append(url[i])
                i++
                continue
            }
            var cp = c
            if (c in 0xd800..0xdbff && i + 1 < url.length && (url[i + 1].code and 0xfc00) == 0xdc00) cp = 0x10000 + ((c - 0xd800) shl 10) + (url[++i].code - 0xdc00)
            else if (c in 0xd800..0xdfff) return null
            val bytes = when {
                cp < 0x800 -> intArrayOf(0xc0 or (cp shr 6), 0x80 or (cp and 63))
                cp < 0x10000 -> intArrayOf(0xe0 or (cp shr 12), 0x80 or ((cp shr 6) and 63), 0x80 or (cp and 63))
                else -> intArrayOf(0xf0 or (cp shr 18), 0x80 or ((cp shr 12) and 63), 0x80 or ((cp shr 6) and 63), 0x80 or (cp and 63))
            }
            for (b in bytes) rest.append('%').append(HEX[b shr 4]).append(HEX[b and 15])
            i++
        }
        val out = "https://" + host + (if (port == null) "" else ":$port") + rest
        return if (out.length > MAX_LINK_LENGTH) null else out
    }

    /** The haptic of a `haptic` bridge call's args, or null: `['impact', style]`, `['notification', type]`, `['selection']`. */
    fun checkHaptic(args: Any?): WidgetAction.Haptic? {
        val a = items(args) ?: return null
        val first = a.getOrNull(0)
        val second = a.getOrNull(1)
        if (first == "impact" && a.size == 2 && second is String && second in HAPTIC_IMPACT_STYLES) return WidgetAction.Haptic("impact", second)
        if (first == "notification" && a.size == 2 && second is String && second in HAPTIC_NOTIFICATION_TYPES) return WidgetAction.Haptic("notification", second)
        if (first == "selection" && a.size == 1) return WidgetAction.Haptic("selection", null)
        return null
    }

    /** The resize anchor of a `setResizeAnchor` call's argument, or null: exactly "top" or "bottom" (case-sensitive, no trimming). */
    fun checkResizeAnchor(edge: Any?): String? = if (edge == "top" || edge == "bottom") edge as String else null

    /**
     * The action of a bridge call (or, for `setResizeAnchor`, the anchor the host keeps: [BridgeCall.SetResizeAnchor];
     * for `request` and `copyText`, [BridgeCall.Request] and [BridgeCall.CopyText]), or null when the method is unknown
     * or its arguments are wrong (`bridge-rejected`).
     */
    fun checkBridgeCall(method: Any?, args: Any?): BridgeCall? {
        val a = items(args) ?: return null
        return when (method) {
            "ready" -> if (a.isEmpty()) WidgetAction.Ready else null
            "openLink" -> (if (a.size == 1) checkLink("openLink", a[0]) else null)?.let { WidgetAction.OpenLink(it) }
            "openTelegramLink" -> (if (a.size == 1) checkLink("openTelegramLink", a[0]) else null)?.let { WidgetAction.OpenTelegramLink(it) }
            "haptic" -> checkHaptic(a)
            "setResizeAnchor" -> (if (a.size == 1) checkResizeAnchor(a[0]) else null)?.let { BridgeCall.SetResizeAnchor(it) }
            "request" -> {
                val id = requestId(a.getOrNull(0))
                val data = if (a.size == 2 || a.size == 3) checkRequestData(a[1]) else null
                val wallet = if (a.size == 3) checkRequestOptions(a[2]) else false
                if (data != null && id != null && wallet != null) BridgeCall.Request(id, data, wallet) else null
            }
            "wallet" -> {
                // [id, method, the method's argument, data]
                val id = requestId(a.getOrNull(0))
                val data = if (a.size == 4) checkRequestData(a[3]) else null
                if (id == null || data == null) null
                else when (a[1]) {
                    "sendTransaction" -> checkWalletTransaction(a[2])?.let { BridgeCall.Wallet(id, WalletCall.SendTransaction(it, data)) }
                    "signData" -> checkSignDataPayload(a[2])?.let { BridgeCall.Wallet(id, WalletCall.SignData(it, data)) }
                    else -> null
                }
            }
            "copyText" -> (if (a.size == 1) checkCopyText(a[0]) else null)?.let { BridgeCall.CopyText(it) }
            "watchPublicData" -> (if (a.size == 1) a[0] as? Boolean else null)?.let { BridgeCall.WatchPublicData(it) }
            else -> null
        }
    }

    /** No lone surrogate: the string has a UTF-8 form (what the server and the clipboard take). */
    private fun wellFormed(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i].code
            if (c in 0xd800..0xdfff) {
                if (c > 0xdbff || i + 1 >= s.length || (s[i + 1].code and 0xfc00) != 0xdc00) return false
                i++
            }
            i++
        }
        return true
    }

    /** A request's `data`, or null: a well-formed string of 1–64 UTF-8 bytes. */
    fun checkRequestData(data: Any?): String? {
        if (data !is String || !wellFormed(data)) return null
        val n = utf8Length(data)
        return if (n >= 1 && n <= MAX_REQUEST_DATA_BYTES) data else null
    }

    private fun requestId(v: Any?): Int? = integer(v)?.takeIf { it >= 1 && it <= MAX_REQUEST_ID }?.toInt()

    /** Whether a `request`'s options ask for the wallet, or null: an object with at most `wallet`, a boolean (absent or null: false). */
    fun checkRequestOptions(options: Any?): Boolean? {
        val o = entries(options) ?: return null
        if (!o.keys.all { it == "wallet" }) return null
        return when (val w = o["wallet"]) {
            null -> false
            is Boolean -> w
            else -> null
        }
    }

    // ── the wallet ──

    private fun isDigit(c: Char) = c in '0'..'9'
    /** A–Z, a–z, 0–9, `+`, `/`, `_`, `-`: base64 and base64url together. */
    private fun isBase64Char(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || isDigit(c) || c == '+' || c == '/' || c == '_' || c == '-'

    /** A message's destination, or null: user-friendly only (TON Connect: the wallet must refuse a raw one; the bounce flag is in it). */
    fun checkWalletDestination(address: Any?): String? =
        if (address is String && address.length == 48 && address.all(::isBase64Char)) address else null

    /** A TON address, or null: raw (`0:` or `-1:` and 64 hex digits) or user-friendly (48 characters of base64 or base64url); a transaction's `from`. The wallet checks the rest. */
    fun checkWalletAddress(address: Any?): String? {
        if (address !is String) return null
        if (address.length == 48 && address.all(::isBase64Char)) return address
        val prefix = if (address.startsWith("0:")) 2 else if (address.startsWith("-1:")) 3 else return null
        return if (address.length == prefix + 64 && (prefix until address.length).all { isHex(address[it].code) }) address else null
    }

    /** Base64 or base64url of 1 to `max` characters, with at most two `=` at the end. */
    private fun base64(v: Any?, max: Int): String? {
        if (v !is String || v.length > max) return null
        var end = v.length
        repeat(2) { if (end > 0 && v[end - 1] == '=') end-- }
        return if (end > 0 && (0 until end).all { isBase64Char(v[it]) }) v else null
    }

    /** Nanotons: a decimal string of at most 37 digits without a leading zero (`0` itself is an amount). */
    private fun isAmount(s: String) = s.length in 1..37 && s.all(::isDigit) && (s.length == 1 || s[0] != '0')

    /** The optional `network` and `from` of a transaction or a payload into a pair; null when one breaks its rule. */
    private fun walletTarget(input: Map<String, Any?>): Pair<String?, String?>? {
        val network = input["network"]?.let { n -> (n as? String)?.takeIf { it in WALLET_NETWORKS } ?: return null }
        val from = input["from"]?.let { checkWalletAddress(it) ?: return null }
        return network to from
    }

    /**
     * A transaction for `sendTransaction`, or null: TON Connect's fields only (`valid_until`, `network`, `from`,
     * `messages`), 1–255 messages of `address`, `amount` and optionally `payload` and `stateInit`. `valid_until` an
     * integer of 1 to 2^32 − 1, `network` "-239" or "-3", `from` raw or user-friendly, a message's `address` user-friendly
     * only, amounts decimal strings of nanotons (at most 37 digits, no leading zero), payloads base64 of at most 8192
     * characters each and 65536 together. Absent and null fields are left out. The wallet checks the rest and refuses
     * with BAD_REQUEST.
     */
    fun checkWalletTransaction(tx: Any?): WalletTransaction? {
        val t = entries(tx) ?: return null
        if (!t.keys.all { it in setOf("valid_until", "network", "from", "messages") }) return null
        val validUntil = t["valid_until"]?.let { v -> integer(v)?.takeIf { it in 1L..4294967295L } ?: return null }
        val (network, from) = walletTarget(t) ?: return null
        val list = items(t["messages"]) ?: return null
        if (list.size !in 1..MAX_WALLET_MESSAGES) return null
        val messages = list.map { item ->
            val m = entries(item) ?: return null
            if (!m.keys.all { it in setOf("address", "amount", "payload", "stateInit") }) return null
            val address = checkWalletDestination(m["address"]) ?: return null
            val amount = (m["amount"] as? String)?.takeIf(::isAmount) ?: return null
            val payload = m["payload"]?.let { base64(it, MAX_WALLET_BOC_LENGTH) ?: return null }
            val stateInit = m["stateInit"]?.let { base64(it, MAX_WALLET_BOC_LENGTH) ?: return null }
            WalletMessage(address, amount, payload, stateInit)
        }
        val total = messages.sumOf { (it.payload?.length ?: 0) + (it.stateInit?.length ?: 0) }
        return if (total <= MAX_WALLET_BOC_TOTAL) WalletTransaction(validUntil, network, from, messages) else null
    }

    /** Text the server and the bot can take: well-formed, no NUL, 1 to `max` UTF-8 bytes. */
    private fun signText(v: Any?, max: Int): String? =
        if (v is String && v.isNotEmpty() && wellFormed(v) && v.indexOf('\u0000') < 0 && utf8Length(v) <= max) v else null

    /**
     * A `signData` payload, or null: TON Connect's three kinds with their fields only, and optionally `network` and
     * `from` as a transaction's. "text": 1–4096 UTF-8 bytes; "binary": `bytes`, base64 of at most 4096 characters;
     * "cell": `schema` (TL-B, 1–1024 UTF-8 bytes) and `cell`, base64 of at most 4096 characters. Absent and null fields
     * are left out. No domain: the client signs for the bot of the message (`bot<bot_id>`).
     */
    fun checkSignDataPayload(payload: Any?): SignDataPayload? {
        val p = entries(payload) ?: return null
        val common = setOf("type", "network", "from")
        fun only(vararg keys: String) = p.keys.all { it in common || it in keys }
        val out = when (p["type"]) {
            "text" -> if (only("text")) signText(p["text"], MAX_SIGN_DATA_LENGTH)?.let { SignDataPayload("text", text = it) } else null
            "binary" -> if (only("bytes")) base64(p["bytes"], MAX_SIGN_DATA_LENGTH)?.let { SignDataPayload("binary", bytes = it) } else null
            "cell" -> if (only("schema", "cell")) {
                val schema = signText(p["schema"], MAX_SIGN_DATA_SCHEMA_BYTES)
                val cell = base64(p["cell"], MAX_SIGN_DATA_LENGTH)
                if (schema != null && cell != null) SignDataPayload("cell", schema = schema, cell = cell) else null
            } else null
            else -> null
        } ?: return null
        val (network, from) = walletTarget(p) ?: return null
        return out.copy(network = network, from = from)
    }

    /** A `copyText` text, or null: a well-formed string of 1–256 UTF-16 code units. */
    fun checkCopyText(text: Any?): String? =
        if (text is String && text.length >= 1 && text.length <= MAX_COPY_TEXT_LENGTH && wellFormed(text)) text else null

    /** One of `chatData` / `publicData`: text of 1–1024 UTF-8 bytes (well-formed, no NUL); null for none (null, absent, the empty string the bot clears it with) and for anything else. */
    fun checkWidgetDataValue(value: Any?): String? =
        if (value is String && value.isNotEmpty() && wellFormed(value) && value.indexOf('\u0000') < 0 && utf8Length(value) <= MAX_WIDGET_DATA_BYTES) value else null

    /**
     * The full state of the bot's two strings, as the client passes it at creation and in `setData` (an object with
     * `chatData` / `publicData`; WidgetHost passes a Map). A forwarded copy (`is_copy`) has no `chatData` (the server
     * never copies it): the glue drops what the client passed, silently; its `publicData` still applies.
     */
    fun checkWidgetData(data: Any?, copy: Boolean): CheckedWidgetData {
        val d = entries(data) ?: emptyMap()
        var chatData: String? = null
        var publicData: String? = null
        val rejected = ArrayList<String>()
        for (field in listOf("chatData", "publicData")) {
            val v = d[field]
            if (field == "chatData" && copy) continue
            val checked = checkWidgetDataValue(v)
            if (field == "chatData") chatData = checked else publicData = checked
            if (checked == null && v != null && v != "") rejected.add(field)
        }
        return CheckedWidgetData(chatData, publicData, rejected)
    }

    /**
     * Who looks at the widget, as the widget may see it (Mini Apps' `WebAppUser`). The input has the widget's field names
     * (`id`, `first_name`, `last_name`, `username`, `is_premium`; WidgetHost passes a Map). No viewer (null) without a
     * positive integer id of at most 53 bits, not reported; a field that breaks its rule is left out and listed in
     * `rejected` (in this order): a name 1–64 UTF-16 code units without a lone surrogate, a username 5–32 characters of
     * A–Z, a–z, 0–9 and _, `is_premium` a boolean (false is left out silently). Absent or null fields are left out without
     * a word.
     */
    fun checkViewer(viewer: Any?): CheckedViewer {
        val v = entries(viewer) ?: emptyMap()
        val id = integer(v["id"])
        if (id == null || id < 1 || id > MAX_VIEWER_ID) return CheckedViewer(null)
        val rejected = ArrayList<String>()
        fun name(field: String): String? {
            val s = v[field] ?: return null
            if (s is String && s.length >= 1 && s.length <= MAX_VIEWER_NAME_LENGTH && wellFormed(s)) return s
            rejected.add(field)
            return null
        }
        val firstName = name("first_name")
        val lastName = name("last_name")
        val username = v["username"]?.let { u ->
            if (u is String && VIEWER_USERNAME.matches(u)) u else {
                rejected.add("username")
                null
            }
        }
        val isPremium = when (v["is_premium"]) {
            null, false -> false
            true -> true
            else -> {
                rejected.add("is_premium")
                false
            }
        }
        return CheckedViewer(WidgetViewer(id, firstName, lastName, username, isPremium), rejected)
    }

    /** Exactly one of [CHAT_TYPES], else null. */
    fun checkChatType(chatType: Any?): String? = if (chatType is String && chatType in CHAT_TYPES) chatType else null

    /**
     * The server's answer to a request, `{ result? }` (WidgetHost passes a Map): no result (absent or null) → `{ result:
     * null }`; text of at most 1024 UTF-8 bytes (well-formed, no NUL; empty allowed) → kept; anything else → null (the
     * widget gets `INVALID_ANSWER`). The bot's data changes reach the client as message edits (`setData`), never here.
     */
    fun checkRequestAnswer(answer: Any?): CheckedAnswer? {
        val r = entries(answer)?.get("result") ?: return CheckedAnswer(null)
        return if (r is String && wellFormed(r) && r.indexOf('\u0000') < 0 && utf8Length(r) <= MAX_REQUEST_RESULT_BYTES) CheckedAnswer(r) else null
    }

    /** What a client's failed wallet call becomes in the widget: REJECTED, BAD_REQUEST, or a request's failure. */
    fun walletFailure(reason: Any?): String {
        val code = (reason as? WidgetRequestException)?.failure?.name ?: (reason as? WidgetRequestFailure)?.name ?: reason
        return if (code == "REJECTED" || code == "BAD_REQUEST") code as String else requestFailure(reason)
    }

    /** What a client's failed request becomes in the widget: TIMEOUT, RATE_LIMITED, or FAILED for anything else ([WidgetRequestException] carries the first two). */
    fun requestFailure(reason: Any?): String {
        val code = when (reason) {
            is WidgetRequestException -> reason.failure.name
            is WidgetRequestFailure -> reason.name
            else -> reason
        }
        return if (code == "TIMEOUT" || code == "RATE_LIMITED") code as String else "FAILED"
    }

    /** What the widget's promise rejects with for a refused request ([REQUEST_ERRORS]), null for one that goes to the client. */
    fun requestRefusal(verdict: RequestVerdict): String? = when (verdict) {
        RequestVerdict.FOREGROUND, RequestVerdict.BACKGROUND, RequestVerdict.HELD, RequestVerdict.GRANTED -> null
        RequestVerdict.BUSY -> "BUSY"
        RequestVerdict.RATE_LIMITED -> "RATE_LIMITED"
        else -> "NOT_ALLOWED"
    }

    private fun isColor(v: String) = v.length == 7 && v[0] == '#' && (1..6).all { isHex(v[it].code) }

    /** The theme keys of `params` whose values are `#rrggbb` (lowercased); everything else is dropped. */
    fun checkThemeParams(params: Any?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val obj = entries(params) ?: return out
        for (key in THEME_KEYS) {
            val v = obj[key]
            if (v is String && isColor(v)) out[key] = asciiLower(v)
        }
        return out
    }

    private val LOCALE = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")

    /** A BCP 47 tag of at most 35 characters (`ru`, `en-US`, `pt-BR`, `zh-Hant-TW`), or null. */
    fun checkLocale(locale: Any?): String? {
        if (locale !is String || locale.isEmpty() || locale.length > MAX_LOCALE_LENGTH) return null
        return if (LOCALE.matches(locale)) locale else null
    }

    /** UTF-8 bytes of an area's keys and values. */
    fun storageBytes(area: Map<String, String>): Int {
        var n = 0
        for ((k, v) in area) n += utf8Length(k) + utf8Length(v)
        return n
    }

    /** An object of strings within the quota, or null. */
    fun checkStorageArea(area: Any?): Map<String, String>? {
        val obj = entries(area) ?: return null
        val out = LinkedHashMap<String, String>()
        for ((k, v) in obj) {
            if (v !is String) return null
            out[k] = v
        }
        return if (storageBytes(out) <= STORAGE_QUOTA_BYTES) out else null
    }

    /** A commit (or a preload) of `local` and/or `session`, each checked whole; null when anything is wrong (`storage-rejected`). */
    fun checkStorageCommit(commit: Any?): StorageCommit? {
        val obj = entries(commit) ?: return null
        var local: Map<String, String>? = null
        var session: Map<String, String>? = null
        for ((k, v) in obj) {
            val area = checkStorageArea(v) ?: return null
            when (k) {
                "local" -> local = area
                "session" -> session = area
                else -> return null
            }
        }
        return StorageCommit(local, session)
    }

    // ── JSON shapes ──

    /** The members of a JSON object (insertion order), or null when `v` is not one; JSONObject.NULL becomes null. */
    private fun entries(v: Any?): Map<String, Any?>? {
        val m = LinkedHashMap<String, Any?>()
        when (v) {
            is JSONObject -> for (k in v.keys()) m[k] = v.opt(k).takeUnless { it == JSONObject.NULL }
            is Map<*, *> -> for ((k, x) in v) m[k as? String ?: return null] = x.takeUnless { it == JSONObject.NULL }
            else -> return null
        }
        return m
    }

    /**
     * A JSON number that is an integer (the TS `Number.isInteger`: 1 and 1.0, not 1.5 or "1"), as a Long; null for
     * anything else and for one outside Long. org.json gives Int, Long, Double, and on the JVM BigInteger / BigDecimal.
     */
    private fun integer(v: Any?): Long? = when (v) {
        is Int -> v.toLong()
        is Long -> v
        is Short, is Byte -> (v as Number).toLong()
        is Double, is Float -> (v as Number).toDouble().let { if (it.isFinite() && it == floor(it) && abs(it) < 9.0e18) it.toLong() else null }
        is BigInteger -> if (v.bitLength() < 64) v.toLong() else null
        is BigDecimal -> try { v.toBigIntegerExact().takeIf { it.bitLength() < 64 }?.toLong() } catch (e: ArithmeticException) { null }
        else -> null
    }

    /** The items of a JSON array, or null when `v` is not one; JSONObject.NULL becomes null. */
    private fun items(v: Any?): List<Any?>? = when (v) {
        is JSONArray -> List(v.length()) { i -> v.opt(i).takeUnless { it == JSONObject.NULL } }
        is List<*> -> v.map { x -> x.takeUnless { it == JSONObject.NULL } }
        else -> null
    }
}
