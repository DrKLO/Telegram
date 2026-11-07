// SDK v1 checks (docs/sdk-v1.md §2.5, §2.6, §2.8, §2.9, §2.13–§2.15, §3): a
// line-for-line mirror of the checked part of src/shared/sdk-policy.ts (see the
// comments there). The shim checks the same calls, but it is untrusted, so the
// glue checks them again: link URLs, haptic arguments and the 5 + 10/s gate,
// theme params, the locale, storage commits and preloads, bridge calls (the
// resize anchor, requests, wallet calls and copyText among them); and what goes
// into the widget: the bot's data, the viewer, the chat type, a request's
// answer, the request gate (with the wallet's grant).
//
// Pure and side-effect free; checked against tests/fixtures/sdk-policy.json
// (copied to Tests/WidgetSandboxTests/Fixtures by `pnpm build:ios`), shared
// with sdk-policy.ts, SdkPolicy.kt and sdk_policy.cpp: a rule change goes to
// the four copies and the fixture together. Strings are measured in UTF-16
// code units where the TS measures `.length`, so the limits are the same.

import Foundation

public enum SdkPolicy {
    public static let maxLinkLength = 256
    /// Bytes of keys and values (UTF-8) per storage area and message (4 KB: a 50-item to-do list; docs/decisions.md, "Strict limits from the start").
    public static let storageQuotaBytes = 4096
    public static let maxLocaleLength = 35
    public static let defaultLocale = "en-US"
    public static let hapticBurst = 5.0
    public static let hapticPerSecond = 10.0
    /// `haptic-limit` is reported at most this often.
    public static let hapticReportMs = 1000.0
    /// Haptics are free (under the rate limit) while a finger or a mouse button is down on the widget and this long after it is lifted.
    public static let hapticTouchWindowMs = 5000.0
    /// Outside a touch, the first haptic opens a series: the calls of its first second pass (under the rate limit), later ones are dropped.
    public static let hapticSeriesMs = 1000.0
    /// Without a touch since the last series, the next one may start this long after the last one began.
    public static let hapticSeriesGapMs = 10000.0
    /// A press whose end the host never saw (the finger left the view) counts as lifted this long after it began.
    public static let hapticPressMaxMs = 30000.0
    /// The widget profile this glue enforces (VERSION.json's `profile`, as `PROFILE_VERSION` in profile-tables.ts); the shim reports a mismatch with its own.
    public static let profileVersion = WidgetSandboxVersion.profileVersion

    public static let themeKeys = [
        "bg_color", "text_color", "hint_color", "link_color", "button_color", "button_text_color",
        "secondary_bg_color", "accent_text_color", "subtitle_text_color", "destructive_text_color",
        "section_bg_color", "section_header_text_color", "section_separator_color", "header_bg_color", "bottom_bar_bg_color",
    ]
    public static let hapticImpactStyles = ["light", "medium", "heavy", "rigid", "soft"]
    public static let hapticNotificationTypes = ["error", "success", "warning"]
    public static let telegramHosts = ["t.me", "telegram.me", "telegram.dog"]
    /// Methods that need the task's user gesture (docs/protocol.md, "User activation"); one press unlocks one of them.
    /// `request` has its own gate (`RequestGate`).
    public static let privilegedMethods: Set<String> = ["openLink", "openTelegramLink", "copyText"]
    /// The bridge methods a preview (an inline result before it is sent: `WidgetContext.preview`) may call; everything
    /// else is refused there: no request, link, copy or haptic. A preview gets no input either (its host drops the
    /// renderer's events and gesture claims).
    public static let previewMethods: Set<String> = ["ready", "setResizeAnchor"]

    public static func allowedInPreview(_ method: Any?) -> Bool {
        guard let method = method as? String else { return false }
        return previewMethods.contains(method)
    }

    // Bot data and requests (docs/sdk-v1.md §2.13–§2.15).

    /// UTF-8 bytes of `chatData` and of `publicData` each (the server's limit, `chat_data` / `public_data`).
    public static let maxWidgetDataBytes = 1024
    /// UTF-8 bytes of a request's `data` (as `callback_data`).
    public static let maxRequestDataBytes = 64
    /// UTF-8 bytes of an answer's `result`.
    public static let maxRequestResultBytes = 1024
    /// UTF-16 code units of a `copyText` text (as the `copy_text` button's).
    public static let maxCopyTextLength = 256
    /// A background request (not from a press) at most this often, per widget.
    public static let backgroundRequestIntervalMs = 5000.0
    /// The request ids the shim numbers its calls with.
    public static let maxRequestId = 2147483647
    /// A Telegram user id fits in 53 bits: a JS number holds it exactly.
    public static let maxViewerId: Int64 = 9007199254740991
    /// UTF-16 code units of the viewer's first and last name (each 1 to this).
    public static let maxViewerNameLength = 64
    /// Why a viewer field was left out (`data-rejected`: `viewer.<field>: <why>; the widget does not see it`).
    public static let viewerFieldRules: [String: String] = [
        "first_name": "not text of 1–64 UTF-16 code units",
        "last_name": "not text of 1–64 UTF-16 code units",
        "username": "not 5–32 characters of A–Z, a–z, 0–9 and _",
        "is_premium": "not a boolean",
    ]

    /// What a refused or failed request rejects with in the widget (`error.code`); `rejected` and `badRequest` only
    /// for a wallet call: the user declined in the client's window, the wallet refused the transaction or the payload.
    public enum RequestError: String, CaseIterable {
        case notAllowed = "NOT_ALLOWED"
        case busy = "BUSY"
        case rateLimited = "RATE_LIMITED"
        case timeout = "TIMEOUT"
        case failed = "FAILED"
        case invalidAnswer = "INVALID_ANSWER"
        case rejected = "REJECTED"
        case badRequest = "BAD_REQUEST"
    }

    // The wallet (docs/sdk-v1.md §2.18): TON Connect's formats, Gram Wallet only.

    /// Messages in one transaction: what Gram Wallet takes (its TON Connect `maxMessages`).
    public static let maxWalletMessages = 255
    /// Characters of a message's `payload` or `stateInit` (a bag of cells in base64).
    public static let maxWalletBocLength = 8192
    /// Characters of every message's `payload` and `stateInit` together.
    public static let maxWalletBocTotal = 65536
    /// UTF-8 bytes of a `signData` text; characters of its `bytes` or `cell` (base64).
    public static let maxSignDataLength = 4096
    /// UTF-8 bytes of a `cell` payload's TL-B schema.
    public static let maxSignDataSchemaBytes = 1024
    /// After the answer to a call made by a tap (a request or a wallet call) reached the widget, one wallet call may follow without a new tap this long.
    public static let walletGrantMs = 10000.0
    /// TON Connect's network ids: the mainnet and the testnet.
    public static let walletNetworks = ["-239", "-3"]

    /// A checked haptic: `impact` / `notification` with their value, `selection` without.
    public struct HapticCall: Equatable {
        public var haptic: String
        public var value: String?
    }

    /// Until the widget calls `setResizeAnchor`: the bubble grows and shrinks downward.
    public static let defaultResizeAnchor = WidgetResizeAnchor.top

    /// A checked bridge call (the TS `BridgeCall`): an action for the client (the TS `WidgetAction`; the URL as the
    /// check gives it, a string); the resize anchor, which no `WidgetAction` carries (the host keeps the latest one
    /// next to the height it reports); whether the widget watches its shared `public_data` (the host keeps the latest,
    /// the client polls only for widgets that watch); a request to the bot (the request gate, then the client's
    /// `onRequest`; `wallet`: the widget asks the server to attach the user's wallet); a wallet call (the request gate,
    /// then the client's `onWallet`); a text to copy (`onCopyText`). The last five are never a `WidgetAction`.
    public enum Action: Equatable {
        case ready
        case openLink(String)
        case openTelegramLink(String)
        case haptic(HapticCall)
        case setResizeAnchor(WidgetResizeAnchor)
        case watchPublicData(Bool)
        case request(id: Int, data: String, wallet: Bool)
        case wallet(id: Int, call: WalletCall)
        case copyText(String)
    }

    /// A checked storage commit (or preload): the areas present, each in full.
    public struct StorageCommit: Equatable {
        public var local: [String: String]?
        public var session: [String: String]?
        public var isEmpty: Bool { local == nil && session == nil }
    }

    /// UTF-8 length of a string (a Swift string holds no lone surrogates, which the TS counts 3 bytes).
    public static func utf8Length(_ s: String) -> Int { s.utf8.count }

    // MARK: links

    private static let hexDigits = Array("0123456789ABCDEF".utf8)
    private static func isHex(_ c: UInt16) -> Bool { (c >= 48 && c <= 57) || (c >= 65 && c <= 70) || (c >= 97 && c <= 102) }
    /// ASCII that may stand unescaped after the authority: RFC 3986 unreserved, reserved and `%`.
    private static let urlASCII = Set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=%".utf16)
    /// Lowercases A–Z only: a Unicode mapping (U+212A KELVIN SIGN → `k`) must not turn a non-ASCII host into an ASCII one.
    private static func asciiLower<S: Sequence>(_ units: S) -> [UInt16] where S.Element == UInt16 {
        units.map { $0 >= 65 && $0 <= 90 ? $0 + 32 : $0 }
    }
    private static func isTelegramHost(_ host: String) -> Bool {
        telegramHosts.contains { host == $0 || host.hasSuffix("." + $0) }
    }

    /// The URL a link call opens, or nil when it breaks the rules: an absolute `https:` URL, no user
    /// name or password, an ASCII host of LDH labels (an IDN in its `xn--` form), an optional port, at
    /// most 256 characters once non-ASCII characters after the host are percent-encoded as UTF-8.
    /// `openTelegramLink` takes only t.me, telegram.me and telegram.dog (and their subdomains), any
    /// path; `openLink` takes every other host. Scheme and host come out lowercase, the rest as given.
    public static func checkLink(_ method: String, _ url: Any?) -> String? {
        guard let url = url as? String, utf8Length(url) <= maxLinkLength else { return nil }
        let u = Array(url.utf16)
        guard u.count >= 8, asciiLower(u[0..<8]) == Array("https://".utf16) else { return nil }
        var end = 8
        while end < u.count, u[end] != 0x2F, u[end] != 0x3F, u[end] != 0x23 { end += 1 } // / ? #
        let authority = u[8..<end]
        if authority.contains(0x40) { return nil } // @
        let colon = authority.firstIndex(of: 0x3A)
        let host = asciiLower(colon.map { authority[authority.startIndex..<$0] } ?? authority)
        let port = colon.map { Array(authority[authority.index(after: $0)...]) }
        if let port {
            guard (1...5).contains(port.count), port.allSatisfy({ $0 >= 48 && $0 <= 57 }) else { return nil }
            guard let n = Int(String(decoding: port, as: UTF16.self)), n <= 65535 else { return nil }
        }
        guard !host.isEmpty, host.count <= 253 else { return nil }
        for label in host.split(separator: 0x2E, omittingEmptySubsequences: false) { // .
            guard !label.isEmpty, label.count <= 63, label.first != 0x2D, label.last != 0x2D,
                  label.allSatisfy({ ($0 >= 97 && $0 <= 122) || ($0 >= 48 && $0 <= 57) || $0 == 0x2D }) else { return nil }
        }
        let hostString = String(decoding: host, as: UTF16.self)
        guard isTelegramHost(hostString) == (method == "openTelegramLink") else { return nil }
        var rest: [UInt8] = []
        var i = end
        while i < u.count {
            let c = u[i]
            if c < 0x80 {
                guard urlASCII.contains(c) else { return nil }
                if c == 0x25, !(i + 2 < u.count && isHex(u[i + 1]) && isHex(u[i + 2])) { return nil } // %
                rest.append(UInt8(c))
                i += 1
                continue
            }
            var cp = UInt32(c)
            if c >= 0xD800, c <= 0xDBFF, i + 1 < u.count, u[i + 1] & 0xFC00 == 0xDC00 {
                i += 1
                cp = 0x10000 + ((UInt32(c) - 0xD800) << 10) + (UInt32(u[i]) - 0xDC00)
            } else if c >= 0xD800, c <= 0xDFFF {
                return nil
            }
            let bytes: [UInt32] = cp < 0x800 ? [0xC0 | (cp >> 6), 0x80 | (cp & 63)]
                : cp < 0x10000 ? [0xE0 | (cp >> 12), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63)]
                : [0xF0 | (cp >> 18), 0x80 | ((cp >> 12) & 63), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63)]
            for b in bytes { rest += [0x25, hexDigits[Int(b >> 4)], hexDigits[Int(b & 15)]] }
            i += 1
        }
        let out = "https://" + hostString + (port.map { ":" + String(decoding: $0, as: UTF16.self) } ?? "") + String(decoding: rest, as: UTF8.self)
        return out.utf16.count > maxLinkLength ? nil : out
    }

    // MARK: haptics and bridge calls

    /// The haptic of a `haptic` bridge call's args, or nil: `['impact', style]`, `['notification', type]`, `['selection']`.
    public static func checkHaptic(_ args: Any?) -> HapticCall? {
        guard let args = args as? [Any], let kind = args.first as? String else { return nil }
        let value = args.count == 2 ? args[1] as? String : nil
        if kind == "impact", args.count == 2, let value, hapticImpactStyles.contains(value) { return HapticCall(haptic: "impact", value: value) }
        if kind == "notification", args.count == 2, let value, hapticNotificationTypes.contains(value) { return HapticCall(haptic: "notification", value: value) }
        if kind == "selection", args.count == 1 { return HapticCall(haptic: "selection", value: nil) }
        return nil
    }

    /// The resize anchor of a `setResizeAnchor` call's argument, or nil: exactly `"top"` or `"bottom"` (case-sensitive, no trimming).
    public static func checkResizeAnchor(_ edge: Any?) -> WidgetResizeAnchor? {
        guard let edge = edge as? String, edge == "top" || edge == "bottom" else { return nil }
        return WidgetResizeAnchor(rawValue: edge)
    }

    /// The action of a bridge call (or, for `setResizeAnchor`, the anchor the host keeps), or nil when the method is
    /// unknown or its arguments are wrong (`bridge-rejected`).
    public static func checkBridgeCall(_ method: Any?, _ args: Any?) -> Action? {
        guard let args = args as? [Any], let method = method as? String else { return nil }
        switch method {
        case "ready":
            return args.isEmpty ? .ready : nil
        case "openLink", "openTelegramLink":
            guard args.count == 1, let url = checkLink(method, args[0]) else { return nil }
            return method == "openLink" ? .openLink(url) : .openTelegramLink(url)
        case "haptic":
            return checkHaptic(args).map { .haptic($0) }
        case "setResizeAnchor":
            return args.count == 1 ? checkResizeAnchor(args[0]).map { .setResizeAnchor($0) } : nil
        case "watchPublicData":
            // Exactly one JSON boolean (not a number that bridges to one).
            guard args.count == 1, let on = boolean(args[0]) else { return nil }
            return .watchPublicData(on)
        case "request":
            guard args.count == 2 || args.count == 3, let id = integer(args[0], in: 1...Int64(maxRequestId)), let data = checkRequestData(args[1]) else { return nil }
            guard let wallet = args.count == 3 ? checkRequestOptions(args[2]) : false else { return nil }
            return .request(id: Int(id), data: data, wallet: wallet)
        case "wallet":
            // [id, method, the method's argument, data]
            guard args.count == 4, let id = integer(args[0], in: 1...Int64(maxRequestId)), let data = checkRequestData(args[3]) else { return nil }
            switch args[1] as? String {
            case "sendTransaction": return checkWalletTransaction(args[2]).map { .wallet(id: Int(id), call: .sendTransaction($0, data: data)) }
            case "signData": return checkSignDataPayload(args[2]).map { .wallet(id: Int(id), call: .signData($0, data: data)) }
            default: return nil
            }
        case "copyText":
            return args.count == 1 ? checkCopyText(args[0]).map { .copyText($0) } : nil
        default:
            return nil
        }
    }

    // MARK: bot data and requests

    /// A JSON number that is an integer within `range`, as `Number.isInteger` and the range test take it: not a
    /// boolean, not a numeric string, not 1.5.
    static func integer(_ value: Any?, in range: ClosedRange<Int64>) -> Int64? {
        guard let n = value as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() else { return nil }
        let d = n.doubleValue
        guard d.isFinite, d.rounded() == d, d >= Double(range.lowerBound), d <= Double(range.upperBound) else { return nil }
        return n.int64Value
    }

    /// A JSON boolean (not a number that bridges to one).
    static func boolean(_ value: Any?) -> Bool? {
        guard let n = value as? NSNumber, CFGetTypeID(n) == CFBooleanGetTypeID() else { return nil }
        return n.boolValue
    }

    // The TS checks below also refuse a lone surrogate (`wellFormed`). A Swift string never holds one (bridging a
    // Foundation string turns it into U+FFFD) and JSONSerialization refuses a `\ud800` escape, so a bridge call that
    // carries one is `bridge-rejected` before any check: here every string is well-formed.

    /// A request's `data`, or nil: a string of 1–64 UTF-8 bytes.
    public static func checkRequestData(_ data: Any?) -> String? {
        guard let s = data as? String else { return nil }
        let n = utf8Length(s)
        return n >= 1 && n <= maxRequestDataBytes ? s : nil
    }

    /// Whether a `request`'s options ask for the wallet, or nil: an object with at most `wallet`, a boolean (absent or null: false).
    public static func checkRequestOptions(_ options: Any?) -> Bool? {
        guard let o = options as? [String: Any], o.keys.allSatisfy({ $0 == "wallet" }) else { return nil }
        guard let w = o["wallet"], !(w is NSNull) else { return false }
        return boolean(w)
    }

    // MARK: the wallet

    private static func isDigit(_ b: UInt8) -> Bool { b >= 48 && b <= 57 }
    private static func isHexByte(_ b: UInt8) -> Bool { isDigit(b) || (b >= 65 && b <= 70) || (b >= 97 && b <= 102) }
    /// A–Z, a–z, 0–9, `+`, `/`, `_`, `-`: base64 and base64url together.
    private static func isBase64Byte(_ b: UInt8) -> Bool { isDigit(b) || (b >= 65 && b <= 90) || (b >= 97 && b <= 122) || b == 43 || b == 47 || b == 95 || b == 45 }
    /// Absent: nil or JSON null (a null the bot wrote).
    private static func absent(_ v: Any?) -> Bool { v == nil || v is NSNull }
    private static func onlyKeys(_ o: [String: Any], _ keys: Set<String>) -> Bool { o.keys.allSatisfy(keys.contains) }

    /// A message's destination, or nil: user-friendly only (TON Connect: the wallet must refuse a raw one; the bounce flag is in it).
    public static func checkWalletDestination(_ address: Any?) -> String? {
        guard let s = address as? String else { return nil }
        let u = Array(s.utf8)
        return u.count == 48 && u.allSatisfy(isBase64Byte) ? s : nil
    }

    /// A TON address, or nil: raw (`0:` or `-1:` and 64 hex digits) or user-friendly (48 characters of base64 or base64url); a transaction's `from`. The wallet checks the rest.
    public static func checkWalletAddress(_ address: Any?) -> String? {
        guard let s = address as? String else { return nil }
        let u = Array(s.utf8)
        if u.count == 48 && u.allSatisfy(isBase64Byte) { return s }
        let prefix = u.starts(with: [48, 58]) ? 2 : u.starts(with: [45, 49, 58]) ? 3 : 0
        return prefix > 0 && u.count == prefix + 64 && u[prefix...].allSatisfy(isHexByte) ? s : nil
    }

    /// Base64 or base64url of 1 to `max` characters, with at most two `=` at the end.
    private static func base64(_ v: Any?, max: Int) -> String? {
        guard let s = v as? String else { return nil }
        var u = Array(s.utf8)
        guard u.count <= max else { return nil }
        for _ in 0..<2 where u.last == 61 { u.removeLast() }
        return !u.isEmpty && u.allSatisfy(isBase64Byte) ? s : nil
    }

    /// Nanotons: a decimal string of at most 37 digits without a leading zero (`0` itself is an amount).
    private static func isAmount(_ s: String) -> Bool {
        let u = Array(s.utf8)
        return (1...37).contains(u.count) && u.allSatisfy(isDigit) && (u.count == 1 || u[0] != 48)
    }

    /// The optional `network` and `from` of a transaction or a payload; nil when one breaks its rule.
    private static func walletTarget(_ input: [String: Any]) -> (network: String?, from: String?)? {
        var out: (network: String?, from: String?) = (nil, nil)
        if !absent(input["network"]) {
            guard let n = input["network"] as? String, walletNetworks.contains(n) else { return nil }
            out.network = n
        }
        if !absent(input["from"]) {
            guard let f = checkWalletAddress(input["from"]) else { return nil }
            out.from = f
        }
        return out
    }

    /// A transaction for `sendTransaction`, or nil: TON Connect's fields only (`valid_until`, `network`, `from`,
    /// `messages`), 1–255 messages of `address`, `amount` and optionally `payload` and `stateInit`. `valid_until` an
    /// integer of 1 to 2^32 − 1, `network` `-239` or `-3`, `from` raw or user-friendly, a message's `address`
    /// user-friendly only, amounts decimal strings of nanotons (at most 37 digits, no leading zero), payloads base64 of
    /// at most 8192 characters each and 65536 together. Absent and null fields are left out. The wallet checks the rest
    /// and refuses with `BAD_REQUEST`.
    public static func checkWalletTransaction(_ tx: Any?) -> WalletTransaction? {
        guard let t = tx as? [String: Any], onlyKeys(t, ["valid_until", "network", "from", "messages"]) else { return nil }
        var validUntil: Int64?
        if !absent(t["valid_until"]) {
            guard let v = integer(t["valid_until"], in: 1...4294967295) else { return nil }
            validUntil = v
        }
        guard let target = walletTarget(t), let list = t["messages"] as? [Any], (1...maxWalletMessages).contains(list.count) else { return nil }
        var messages: [WalletMessage] = []
        var total = 0
        for item in list {
            guard let m = item as? [String: Any], onlyKeys(m, ["address", "amount", "payload", "stateInit"]),
                  let address = checkWalletDestination(m["address"]), let amount = m["amount"] as? String, isAmount(amount) else { return nil }
            var message = WalletMessage(address: address, amount: amount)
            if !absent(m["payload"]) {
                guard let p = base64(m["payload"], max: maxWalletBocLength) else { return nil }
                message.payload = p
                total += p.utf8.count
            }
            if !absent(m["stateInit"]) {
                guard let p = base64(m["stateInit"], max: maxWalletBocLength) else { return nil }
                message.stateInit = p
                total += p.utf8.count
            }
            messages.append(message)
        }
        guard total <= maxWalletBocTotal else { return nil }
        return WalletTransaction(validUntil: validUntil, network: target.network, from: target.from, messages: messages)
    }

    /// Text the server and the bot can take: no NUL, 1 to `max` UTF-8 bytes.
    private static func signText(_ v: Any?, max: Int) -> String? {
        guard let s = v as? String, !s.isEmpty, hasNoNul(s), utf8Length(s) <= max else { return nil }
        return s
    }

    /// A `signData` payload, or nil: TON Connect's three kinds with their fields only, and optionally `network` and
    /// `from` as a transaction's. `text`: 1–4096 UTF-8 bytes; `binary`: `bytes`, base64 of at most 4096 characters;
    /// `cell`: `schema` (TL-B, 1–1024 UTF-8 bytes) and `cell`, base64 of at most 4096 characters. Absent and null
    /// fields are left out. No domain: the client signs for the bot of the message (`bot<bot_id>`).
    public static func checkSignDataPayload(_ payload: Any?) -> SignDataPayload? {
        guard let p = payload as? [String: Any] else { return nil }
        let common: Set<String> = ["type", "network", "from"]
        var out: SignDataPayload
        switch p["type"] as? String {
        case "text":
            guard onlyKeys(p, common.union(["text"])), let text = signText(p["text"], max: maxSignDataLength) else { return nil }
            out = SignDataPayload(type: .text, text: text)
        case "binary":
            guard onlyKeys(p, common.union(["bytes"])), let bytes = base64(p["bytes"], max: maxSignDataLength) else { return nil }
            out = SignDataPayload(type: .binary, bytes: bytes)
        case "cell":
            guard onlyKeys(p, common.union(["schema", "cell"])), let schema = signText(p["schema"], max: maxSignDataSchemaBytes),
                  let cell = base64(p["cell"], max: maxSignDataLength) else { return nil }
            out = SignDataPayload(type: .cell, schema: schema, cell: cell)
        default:
            return nil
        }
        guard let target = walletTarget(p) else { return nil }
        out.network = target.network
        out.from = target.from
        return out
    }

    /// A `copyText` text, or nil: a string of 1–256 UTF-16 code units.
    public static func checkCopyText(_ text: Any?) -> String? {
        guard let s = text as? String, (1...maxCopyTextLength).contains(s.utf16.count) else { return nil }
        return s
    }

    /// Text as the server takes it for its text fields: no NUL (other control characters are text).
    static func hasNoNul(_ s: String) -> Bool { !s.utf8.contains(0) }

    /// One of `chatData` / `publicData`: a string of 1–1024 UTF-8 bytes without a NUL (the server's rule for its text
    /// fields); nil for none (nil, NSNull, the empty string the bot clears it with) and for anything else.
    public static func checkWidgetDataValue(_ value: Any?) -> String? {
        guard let s = value as? String, !s.isEmpty, hasNoNul(s), utf8Length(s) <= maxWidgetDataBytes else { return nil }
        return s
    }

    /// The bot's data as the widget sees it, and the fields the client passed that broke the rules (the glue reports `data-rejected`).
    public struct CheckedWidgetData: Equatable {
        public var chatData: String?
        public var publicData: String?
        /// `"chatData"`, `"publicData"`, in this order.
        public var rejected: [String]
    }

    /// The full state of the bot's two strings, as the client passes it at creation and in `setData` (a `WidgetData`,
    /// or the TS shape as a dictionary). A forwarded copy has no `chatData` (the server never copies it): the glue
    /// drops what the client passed, silently; its `publicData` still applies.
    public static func checkWidgetData(_ data: Any?, copy: Bool) -> CheckedWidgetData {
        let d = (data as? WidgetData)?.json ?? (data as? [String: Any]) ?? [:]
        var out = CheckedWidgetData(chatData: nil, publicData: nil, rejected: [])
        for field in ["chatData", "publicData"] {
            if field == "chatData" && copy { continue }
            let v = d[field]
            let checked = checkWidgetDataValue(v)
            if field == "chatData" { out.chatData = checked } else { out.publicData = checked }
            if checked == nil, let v, !(v is NSNull), (v as? String) != "" { out.rejected.append(field) }
        }
        return out
    }

    /// The viewer as the widget sees it, and the fields the client passed that broke their rules (left out; the glue
    /// reports `data-rejected`), in the order `first_name`, `last_name`, `username`, `is_premium`.
    public struct CheckedViewer: Equatable {
        public var viewer: WidgetViewer?
        public var rejected: [String]
    }

    /// A username: 5–32 characters of A–Z, a–z, 0–9 and _ (ASCII only).
    static func isUsername(_ s: String) -> Bool {
        (5...32).contains(s.utf8.count) && s.utf8.allSatisfy { ($0 >= 65 && $0 <= 90) || ($0 >= 97 && $0 <= 122) || ($0 >= 48 && $0 <= 57) || $0 == 95 }
    }

    /// Who looks at the widget, as the widget sees it (Mini Apps' `WebAppUser`; a `WidgetViewer`, or the widget's field
    /// names as a dictionary). No viewer without a positive integer id of at most 53 bits; a field that breaks its
    /// rule is left out and listed in `rejected`: a name of 1–64 UTF-16 code units, a username of 5–32 characters of
    /// A–Z, a–z, 0–9 and _, `is_premium` a boolean, kept only when true. Absent (nil or NSNull) fields are left out
    /// without a word.
    public static func checkViewer(_ viewer: Any?) -> CheckedViewer {
        let v = (viewer as? WidgetViewer)?.json ?? (viewer as? [String: Any]) ?? [:]
        guard let id = integer(v["id"], in: 1...maxViewerId) else { return CheckedViewer(viewer: nil, rejected: []) }
        var out = WidgetViewer(id: id)
        var rejected: [String] = []
        let present = { (field: String) -> Any? in v[field].flatMap { $0 is NSNull ? nil : $0 } }
        for field in ["first_name", "last_name"] {
            guard let value = present(field) else { continue }
            guard let s = value as? String, (1...maxViewerNameLength).contains(s.utf16.count) else {
                rejected.append(field)
                continue
            }
            if field == "first_name" { out.firstName = s } else { out.lastName = s }
        }
        if let value = present("username") {
            if let s = value as? String, isUsername(s) { out.username = s } else { rejected.append("username") }
        }
        if let value = present("is_premium") {
            // A JSON (or Swift) boolean, not a number that bridges to one.
            if let n = value as? NSNumber, CFGetTypeID(n) == CFBooleanGetTypeID() { out.isPremium = n.boolValue } else { rejected.append("is_premium") }
        }
        return CheckedViewer(viewer: out, rejected: rejected)
    }

    /// Exactly `private`, `group`, `supergroup` or `channel` (`Chat.type` of the Bot API), else nil.
    public static func checkChatType(_ chatType: Any?) -> WidgetChatType? {
        if let t = chatType as? WidgetChatType { return t }
        return (chatType as? String).flatMap(WidgetChatType.init(rawValue:))
    }

    /// An answer the glue passes into the widget: the bot's `result` (nil for none).
    public struct CheckedAnswer: Equatable {
        public var result: String?
    }

    /// The bot's answer to a request as the widget gets it (a `WidgetRequestAnswer`, or the TS shape as a dictionary):
    /// `result`, a string of at most 1024 UTF-8 bytes without a NUL (empty allowed), or none. Nil when it breaks the
    /// rules (the widget gets `INVALID_ANSWER`). An answer carries no data: the bot edits its data with separate calls,
    /// which reach the host as `setData`.
    public static func checkRequestAnswer(_ answer: Any?) -> CheckedAnswer? {
        let a = (answer as? WidgetRequestAnswer)?.json ?? (answer as? [String: Any]) ?? [:]
        guard let r = a["result"], !(r is NSNull) else { return CheckedAnswer(result: nil) }
        guard let s = r as? String, hasNoNul(s), utf8Length(s) <= maxRequestResultBytes else { return nil }
        return CheckedAnswer(result: s)
    }

    /// The verdict on a request (docs/sdk-v1.md §2.14): `foreground` (it consumes the task's press) and `background`
    /// go to the client; `held` (a background request from a widget off screen) waits in the glue and goes to the
    /// client when the widget appears on screen (`RequestGate.show`); the rest are refused: `disabled` (no `canQuery`:
    /// a widget sent without `allow_queries`, or a forwarded copy of one sent without `fwd_allow_queries`), `busy` (one
    /// request in flight per widget, a held one included), `noActivation` (no press, and no `backgroundQuery`),
    /// `rateLimited` (a background request sooner than 5 s after the last one, unless the widget appeared on screen since).
    /// A wallet call (`decideWallet`), or a request that asks for the wallet, goes out only as `foreground` or `granted`
    /// (no press, but within 10 s of the answer to a call made by a tap); `noWallet` when the client has no wallet for this user.
    public enum RequestVerdict: String {
        case foreground, background, held, granted, disabled, busy
        case noWallet = "no-wallet"
        case noActivation = "no-activation"
        case rateLimited = "rate-limited"
    }

    /// What the widget's promise rejects with for a refused request; nil for the four that go to the client.
    public static func requestRefusal(_ verdict: RequestVerdict) -> RequestError? {
        switch verdict {
        case .foreground, .background, .held, .granted: return nil
        case .busy: return .busy
        case .rateLimited: return .rateLimited
        case .disabled, .noWallet, .noActivation: return .notAllowed
        }
    }

    /// The client's sandbox policy for requests, from the server's flags only: on a tap whenever `canQuery`; without
    /// one only with `backgroundQuery`: one when the widget appears on screen (`show`, and at creation), then at most
    /// one per 5 s counted from the start of the last background request, while it stays visible; one made while it
    /// is off screen is held (`held`) and is the one of its next appearance (`show` returns true: the glue sends it
    /// then). One request in flight, a held one included. `gesture`: the task has a press no privileged call consumed
    /// yet (the glue consumes it on `foreground`). `finish` when the answer or the failure has gone into the widget,
    /// or when the glue drops a held request (the widget stopped); `answeredAtMs` when an answer with no error went in then.
    ///
    /// The wallet (docs/sdk-v1.md §2.18): only with `wallet` (the client has Gram Wallet for this user). A request that
    /// asks for the wallet (`withWallet`) and a wallet call (`decideWallet`) need the visible widget and a press, or the
    /// grant: the answer to the last call made by a press or a grant (a request or a wallet call) lets one wallet call
    /// follow within 10 s. Any call that goes out as `foreground` or `granted` uses up the grant; its answer brings the next one.
    public struct RequestGate {
        private var inFlight = false
        private var held = false
        private var lastBackground: Double?
        /// The call in flight went out on a press or the grant: its answer grants one wallet call.
        private var granting = false
        private var grantAt: Double?

        public init() {}

        public var busy: Bool { inFlight }

        public mutating func decide(nowMs now: Double, gesture: Bool, visible: Bool, canQuery: Bool, backgroundQuery: Bool, withWallet: Bool = false, wallet: Bool = false) -> RequestVerdict {
            if !canQuery { return .disabled }
            if withWallet && !wallet { return .noWallet }
            if inFlight { return .busy }
            if gesture && visible { return out(.foreground) }
            if !backgroundQuery || withWallet { return .noActivation }
            if !visible {
                inFlight = true
                held = true
                return .held
            }
            if let lastBackground, now - lastBackground < SdkPolicy.backgroundRequestIntervalMs { return .rateLimited }
            lastBackground = now
            inFlight = true
            return .background
        }

        /// A wallet call (`sendTransaction`, `signData`): on a press, or on the grant within 10 s of its answer.
        public mutating func decideWallet(nowMs now: Double, gesture: Bool, visible: Bool, canQuery: Bool, wallet: Bool) -> RequestVerdict {
            if !canQuery { return .disabled }
            if !wallet { return .noWallet }
            if inFlight { return .busy }
            if gesture && visible { return out(.foreground) }
            if visible, let grantAt, now - grantAt <= SdkPolicy.walletGrantMs { return out(.granted) }
            return .noActivation
        }

        private mutating func out(_ verdict: RequestVerdict) -> RequestVerdict {
            inFlight = true
            granting = true
            grantAt = nil
            return verdict
        }

        public mutating func finish(answeredAtMs: Double? = nil) {
            if granting, let answeredAtMs { grantAt = answeredAtMs }
            inFlight = false
            held = false
            granting = false
        }

        /// The widget appeared on screen (it was hidden): its next background request may go out at once. True when a
        /// held request goes out now, as this appearance's background request (the glue sends it; it stays in flight).
        public mutating func show(nowMs now: Double) -> Bool {
            lastBackground = nil
            if !held { return false }
            held = false
            lastBackground = now
            return true
        }
    }

    public enum HapticDecision: String {
        /// Deliver.
        case play
        /// Drop silently.
        case drop
        /// Drop and report `haptic-limit` (at most once per second).
        case report
    }

    /// Haptics need a visible widget and room in the bucket (5 calls, refilled at 10 per second), and a touch: while a
    /// finger is down on the widget and for 5 s after it is lifted they are free (under the rate limit); outside that,
    /// the first call opens a series whose first second passes, then nothing until the next touch, or 10 s after the
    /// series began when there was none (docs/sdk-v1.md §2.6; a widget cannot buzz the phone from a timer). `press` /
    /// `release` are the host's trusted presses (the renderer's claim at a primary pointerdown and its release), never
    /// the widget's events.
    public struct HapticGate {
        private var tokens = SdkPolicy.hapticBurst
        private var last: Double?
        private var reported: Double?
        /// A press is on since then.
        private var down: Double?
        /// The last press ended then.
        private var lifted: Double?
        /// The last series (haptics outside a touch) began then.
        private var series: Double?

        public init() {}

        /// A finger or a mouse button went down on the widget (trusted input only).
        public mutating func press(nowMs now: Double) {
            down = now
        }

        /// The press ended: the finger was lifted or the press was cancelled.
        public mutating func release(nowMs now: Double) {
            down = nil
            lifted = now
        }

        public mutating func decide(nowMs now: Double, visible: Bool) -> HapticDecision {
            if let last { tokens = min(SdkPolicy.hapticBurst, tokens + (max(0, now - last) * SdkPolicy.hapticPerSecond) / 1000) }
            last = now
            if let down, now - down > SdkPolicy.hapticPressMaxMs { release(nowMs: down + SdkPolicy.hapticPressMaxMs) }
            let touching = down != nil || (lifted.map { now - $0 <= SdkPolicy.hapticTouchWindowMs } ?? false)
            var allowed = visible
            if allowed, !touching, !(series.map { now - $0 < SdkPolicy.hapticSeriesMs } ?? false) {
                // A new series: after a touch at any time (one per touch), else 10 s after the last one began.
                let touchedSince = lifted.map { lifted in series.map { lifted > $0 } ?? true } ?? false
                if series == nil || touchedSince || now - series! >= SdkPolicy.hapticSeriesGapMs { series = now } else { allowed = false }
            }
            if allowed, tokens >= 1 {
                tokens -= 1
                return .play
            }
            if let reported, now - reported < SdkPolicy.hapticReportMs { return .drop }
            reported = now
            return .report
        }
    }

    // MARK: theme and locale

    private static func isColor(_ v: String) -> Bool {
        let u = Array(v.utf16)
        return u.count == 7 && u[0] == 0x23 && u[1...].allSatisfy(isHex)
    }

    /// The theme keys of `params` whose values are `#rrggbb` (lowercased); everything else is dropped.
    public static func checkThemeParams(_ params: Any?) -> [String: String] {
        guard let params = params as? [String: Any] else { return [:] }
        var out: [String: String] = [:]
        for key in themeKeys {
            if let v = params[key] as? String, isColor(v) { out[key] = v.lowercased() }
        }
        return out
    }

    private static func isASCIILetter(_ c: UInt16) -> Bool { (c >= 65 && c <= 90) || (c >= 97 && c <= 122) }

    /// A BCP 47 tag of at most 35 characters (`ru`, `en-US`, `pt-BR`, `zh-Hant-TW`), or nil.
    public static func checkLocale(_ locale: Any?) -> String? {
        guard let locale = locale as? String else { return nil }
        let u = Array(locale.utf16)
        guard !u.isEmpty, u.count <= maxLocaleLength else { return nil }
        // ^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$
        let parts = u.split(separator: 0x2D, omittingEmptySubsequences: false) // -
        guard let first = parts.first, (2...8).contains(first.count), first.allSatisfy(isASCIILetter) else { return nil }
        for part in parts.dropFirst() {
            guard (1...8).contains(part.count), part.allSatisfy({ isASCIILetter($0) || ($0 >= 48 && $0 <= 57) }) else { return nil }
        }
        return locale
    }

    // MARK: storage

    /// UTF-8 bytes of an area's keys and values.
    public static func storageBytes(_ area: [String: String]) -> Int {
        area.reduce(0) { $0 + utf8Length($1.key) + utf8Length($1.value) }
    }

    /// An object of strings within the quota, or nil.
    public static func checkStorageArea(_ area: Any?) -> [String: String]? {
        guard let area = area as? [String: Any] else { return nil }
        var out: [String: String] = [:]
        for (k, v) in area {
            guard let s = v as? String else { return nil }
            out[k] = s
        }
        return storageBytes(out) <= storageQuotaBytes ? out : nil
    }

    /// A commit (or a preload) of `local` and/or `session`, each checked whole; nil when anything is wrong (`storage-rejected`).
    public static func checkStorageCommit(_ commit: Any?) -> StorageCommit? {
        guard let commit = commit as? [String: Any] else { return nil }
        var out = StorageCommit()
        for (k, v) in commit {
            guard k == "local" || k == "session", let area = checkStorageArea(v) else { return nil }
            if k == "local" { out.local = area } else { out.session = area }
        }
        return out
    }
}
