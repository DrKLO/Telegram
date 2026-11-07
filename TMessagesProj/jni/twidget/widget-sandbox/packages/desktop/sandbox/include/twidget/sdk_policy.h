// SDK v1 checks (docs/sdk-v1.md §2.5, §2.6, §2.8, §2.9, §2.12–§2.15, §3): the C++ twin of
// the checked part of packages/core/src/shared/sdk-policy.ts, next to
// SdkPolicy.swift and SdkPolicy.kt. Pure and side-effect free; checked against
// tests/fixtures/sdk-policy.json (copied from packages/core/tests/fixtures by
// `pnpm build:desktop`), so a rule change goes to the four copies and the
// fixture together. The theme defaults and the renderer's CSS variables are
// TS only: the renderer core builds them from the host's `theme` message.
//
// Strings. Everything here is UTF-8 bytes. A JS string may hold a lone
// surrogate; QuickJS hands it to C++ in its 3-byte WTF-8 form (ED A0..BF xx),
// and the engine parses bridge calls and storage commits with
// `Json::parse(…, Json::Surrogates::Keep)` so that the JSON route gives the
// same bytes. `utf8Length` is the byte length, which is what the TS
// `utf8Length` counts (a lone surrogate is 3 bytes in both); `checkLink`
// rejects invalid UTF-8 and every surrogate code point, as the TS version
// rejects lone surrogates. The strings of the bot's data and requests (the bot's data, a request's
// data and answer, a text to copy) are refused the same way: `wellFormed`
// means valid UTF-8 without a surrogate, what the TS `wellFormed` (no lone
// surrogate) means for a JS string.
#pragma once

#include "twidget/json.h"
#include "twidget/version.h"

#include <array>
#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace twidget {

/// One Web Storage area: keys and values as the widget set them (see "Strings" above).
using StorageArea = std::map<std::string, std::string>;

/// Web Storage of one message as the client keeps it (docs/sdk-v1.md §2.8, §5.4): the preload at start.
struct WidgetStorage {
    StorageArea local;
    StorageArea session;
};

/// What a widget asks the client to do, after the glue's checks (docs/sdk-v1.md §3.2, §4.3).
struct WidgetAction {
    enum Kind { Ready, OpenLink, OpenTelegramLink, Haptic } kind = Ready;
    /// OpenLink, OpenTelegramLink: the URL to open (scheme and host lowercase, non-ASCII percent-encoded).
    std::string url;
    /// Haptic: "impact", "notification" or "selection".
    std::string hapticKind;
    /// Haptic: the impact style or the notification type; empty for "selection".
    std::string hapticValue;

    bool operator==(const WidgetAction& o) const { return kind == o.kind && url == o.url && hapticKind == o.hapticKind && hapticValue == o.hapticValue; }
    /// The TS `WidgetAction` shape: {kind:'ready'} | {kind:'openLink', url} | {kind:'haptic', haptic, value?}.
    Json toJson() const;
};

// ── the bot's data, where the widget is, requests (docs/sdk-v1.md §2.13–§2.15, §4.4) ──

/// The bot's two strings as the client has them for this message (none = nullopt), the full state each time: at
/// creation (`WidgetHostOptions::data`) and in `setData`. `chatData` stays in this chat (a copy never has it),
/// `publicData` a copy gets too. Each 1–1024 bytes of UTF-8 text without NUL; "" is none (how the bot clears a field);
/// anything longer or malformed is none and reported (`data-rejected`).
struct WidgetData {
    std::optional<std::string> chatData;
    std::optional<std::string> publicData;
    bool operator==(const WidgetData& o) const { return chatData == o.chatData && publicData == o.publicData; }
};

/// Who looks at the widget, as the client passes it (round 6: the fields of Mini Apps' `WebAppUser`): the Telegram user
/// id the bot gets with a request (1 … 2^53 − 1; anything else is no viewer) and, when the account has them, its first
/// and last name (1–64 UTF-16 code units each), its username (5–32 of A–Z, a–z, 0–9, _) and Premium. A field that
/// breaks its rule is left out (`data-rejected`).
struct WidgetViewer {
    int64_t id = 0;
    std::optional<std::string> firstName;
    std::optional<std::string> lastName;
    std::optional<std::string> username;
    bool isPremium = false;
    bool operator==(const WidgetViewer& o) const {
        return id == o.id && firstName == o.firstName && lastName == o.lastName && username == o.username && isPremium == o.isPremium;
    }
};

/// Where the widget is and what it may do, from the message (`messageMediaWidget`'s flags, which the server computes)
/// and the chat; fixed for the host's life (docs/sdk-v1.md §2.14, §4.4). The framework derives nothing itself; all
/// false (the default): no requests.
struct WidgetContext {
    /// `is_copy`: the message is a forwarded copy: never any chatData.
    bool isCopy = false;
    /// `can_query`: requests are allowed, on a tap.
    bool canQuery = false;
    /// `background_query`: requests without a tap too (the original in the private chat with the bot): one when the
    /// widget appears on screen, then one per 5 s while visible; one made off screen waits for it to appear.
    bool backgroundQuery = false;
    /// "private" | "group" | "supergroup" | "channel" (`Chat.type` of the Bot API); "" or anything else is none. A copy
    /// (with `canQuery` or not) and a preview get none: the bot learns nothing about where its widget was forwarded.
    std::string chatType;
    /// The account that looks at the widget. A copy without `canQuery` gets none.
    std::optional<WidgetViewer> viewer;
    /// An inline result shown before it is sent (no message yet, docs/sdk-v1.md §2.16): the widget gets its data and runs
    /// as usual, but no input, no bridge calls but `ready` / `setResizeAnchor` (`PREVIEW_METHODS`), no chat type or
    /// viewer, and Web Storage that is never preloaded or committed. The request flags are ignored.
    bool preview = false;
    /// The client has Gram Wallet for this user: it supports the wallet and the wallet is available in the user's
    /// country (docs/sdk-v1.md §2.18). The widget gets `TelegramWidget.wallet` only with `canQuery` too, never in a preview.
    bool wallet = false;
};

/// What the client sends to the bot, as a callback button's press: `data` (1–64 bytes). Whether it came from a tap is
/// the framework's business only (the request gate): the server and the bot cannot tell.
struct WidgetRequest {
    std::string data;
    /// The widget asked for the user's wallet (only on a tap, only with `WidgetContext::wallet`): ask the server to attach
    /// the wallet card (`with_wallet`); it creates the wallet if the user had none.
    bool wallet = false;
    bool operator==(const WidgetRequest& o) const { return data == o.data && wallet == o.wallet; }
};

/// One message of a transaction, as TON Connect's `sendTransaction` has it: the destination, nanotons as a decimal
/// string, a body and a `StateInit` (bags of cells in base64).
struct WalletMessage {
    std::string address;
    std::string amount;
    std::optional<std::string> payload;
    std::optional<std::string> stateInit;
    bool operator==(const WalletMessage& o) const { return address == o.address && amount == o.amount && payload == o.payload && stateInit == o.stateInit; }
    Json toJson() const;
};

/// A transaction the widget asks the user to send (`TelegramWidget.wallet.sendTransaction`, docs/sdk-v1.md §2.18,
/// checked): TON Connect's `sendTransaction` request. The client hands it to its TON Connect window as is (`toJson`:
/// the protocol's field names); the wallet checks the network and the sender against the account, the cells, the expiry.
struct WalletTransaction {
    /// `valid_until`, Unix seconds.
    std::optional<int64_t> validUntil;
    /// "-239" (the mainnet) or "-3" (the testnet).
    std::optional<std::string> network;
    std::optional<std::string> from;
    /// 1–4 messages.
    std::vector<WalletMessage> messages;
    bool operator==(const WalletTransaction& o) const { return validUntil == o.validUntil && network == o.network && from == o.from && messages == o.messages; }
    Json toJson() const;
};

/// What the widget asks the user to sign (`TelegramWidget.wallet.signData`, checked): TON Connect's `signData` payload,
/// `type` "text" (`text`), "binary" (`bytes`, base64) or "cell" (`cell`, base64, with its TL-B `schema`). The client signs
/// for the bot of the message (the domain `bot<bot_id>`), never a domain the widget names.
struct SignDataPayload {
    std::string type;
    std::optional<std::string> text, bytes, schema, cell, network, from;
    bool operator==(const SignDataPayload& o) const {
        return type == o.type && text == o.text && bytes == o.bytes && schema == o.schema && cell == o.cell && network == o.network && from == o.from;
    }
    Json toJson() const;
};

/// A wallet call that passed its checks (docs/sdk-v1.md §2.18): the client shows its own wallet window with the bot of
/// the message, sends the transaction (or signs the payload) on the user's confirmation, sends the outcome to the bot as
/// a query with `data` (1–64 bytes, a request's) and completes with the bot's answer. `transaction` is set for
/// `SendTransaction`, `payload` for `SignData`.
struct WalletCall {
    enum class Method { SendTransaction, SignData } method = Method::SendTransaction;
    WalletTransaction transaction;
    SignDataPayload payload;
    std::string data;
    /// "sendTransaction" or "signData".
    std::string_view methodName() const { return method == Method::SignData ? "signData" : "sendTransaction"; }
    bool operator==(const WalletCall& o) const {
        return method == o.method && data == o.data && (method == Method::SignData ? payload == o.payload : transaction == o.transaction);
    }
    /// {method, transaction | payload, data}, as the fixture writes it.
    Json toJson() const;
};

/// The bot's answer as the client hands it over: the server's `result` (text of at most 1024 bytes, no NUL; none is
/// none). The bot changes its data with `setData`'s edits, not with an answer. There is nothing for the client to show:
/// the widget shows the result.
struct WidgetRequestAnswer {
    std::optional<std::string> result;
};

/// A request the client could not complete: the bot did not answer in time, a flood wait, anything else. `None`: answered.
/// For a wallet call also `Rejected` (the user declined in the wallet window) and `BadRequest` (the wallet refused the
/// transaction or the payload); a request's failure turns them into FAILED.
enum class WidgetRequestFailure { None, Timeout, RateLimited, Failed, Rejected, BadRequest };

/// What the client reports for a request (`respond`): the answer when `failure` is `None`, else the failure.
struct WidgetRequestOutcome {
    WidgetRequestFailure failure = WidgetRequestFailure::None;
    WidgetRequestAnswer answer;
};

namespace sdk {

inline constexpr int MAX_LINK_LENGTH = 256;
/// Bytes of keys and values (UTF-8) per storage area and message (4 KB: a 50-item to-do list; docs/decisions.md,
/// "Strict limits from the start").
inline constexpr int STORAGE_QUOTA_BYTES = 4096;
inline constexpr int MAX_LOCALE_LENGTH = 35;
inline constexpr std::string_view DEFAULT_LOCALE = "en-US";
inline constexpr int HAPTIC_BURST = 5;
inline constexpr int HAPTIC_PER_SECOND = 10;
/// `haptic-limit` is reported at most this often.
inline constexpr double HAPTIC_REPORT_MS = 1000;
/// Haptics are free (under the rate limit) while a finger or a mouse button is down on the widget and this long after it is lifted (v0.2.2).
inline constexpr double HAPTIC_TOUCH_WINDOW_MS = 5000;
/// Outside a touch, the first haptic opens a series: the calls of its first second pass (under the rate limit), later ones are dropped.
inline constexpr double HAPTIC_SERIES_MS = 1000;
/// Without a touch since the last series, the next one may start this long after the last one began.
inline constexpr double HAPTIC_SERIES_GAP_MS = 10000;
/// A press whose end the host never saw (the finger left the view) counts as lifted this long after it began.
inline constexpr double HAPTIC_PRESS_MAX_MS = 30000;
/// Handlers per `onEvent` type in the shim (here for completeness; the glue never sees them).
inline constexpr int MAX_HANDLERS_PER_EVENT = 16;
/// The widget profile this glue enforces (VERSION.json's `profile`, as `PROFILE_VERSION` in profile-tables.ts; docs/profile-v1.md).
inline constexpr int PROFILE_VERSION = version::PROFILE;
/// Two full areas of the quota at 12 JSON bytes per byte (`\uXXXX` escapes plus the quotes, colon and
/// comma of tiny pairs): a longer `storage` host call cannot be within the quota (worker.ts
/// MAX_STORAGE_COMMIT_CHARS; JSON bytes ≥ JSON characters, and the bound holds for bytes too).
inline constexpr size_t MAX_STORAGE_COMMIT_BYTES = 2 * 12 * STORAGE_QUOTA_BYTES + 64;
/// The diagnostics `bridge-rejected`, `activation-blocked` and `storage-rejected` are reported at most this often per widget.
inline constexpr int MAX_REJECTION_REPORTS = 20;

inline constexpr std::array<std::string_view, 15> THEME_KEYS = {
    "bg_color", "text_color", "hint_color", "link_color", "button_color", "button_text_color",
    "secondary_bg_color", "accent_text_color", "subtitle_text_color", "destructive_text_color",
    "section_bg_color", "section_header_text_color", "section_separator_color", "header_bg_color", "bottom_bar_bg_color",
};
inline constexpr std::array<std::string_view, 5> HAPTIC_IMPACT_STYLES = {"light", "medium", "heavy", "rigid", "soft"};
inline constexpr std::array<std::string_view, 3> HAPTIC_NOTIFICATION_TYPES = {"error", "success", "warning"};
inline constexpr std::array<std::string_view, 3> TELEGRAM_HOSTS = {"t.me", "telegram.me", "telegram.dog"};
/// Methods that need the task's user gesture (docs/protocol.md, "User activation"); one press unlocks one of them.
/// `request` has its own gate (`RequestGate`).
inline constexpr std::array<std::string_view, 3> PRIVILEGED_METHODS = {"openLink", "openTelegramLink", "copyText"};
/// The bridge methods a preview (`WidgetContext::preview`) may call; everything else is dropped there (`preview-blocked`):
/// no request, link, copy or haptic.
inline constexpr std::array<std::string_view, 2> PREVIEW_METHODS = {"ready", "setResizeAnchor"};
/// The edges of the bubble that can stay put on screen when the widget's height changes (`setResizeAnchor`,
/// docs/sdk-v1.md §2.12): "top" (the bubble grows and shrinks downward, the content below moves) or "bottom" (it
/// grows upward, its bottom stays, the content above moves).
inline constexpr std::array<std::string_view, 2> RESIZE_ANCHORS = {"top", "bottom"};
/// Until the widget calls `setResizeAnchor`: the bubble grows and shrinks downward.
inline constexpr std::string_view DEFAULT_RESIZE_ANCHOR = "top";

// ── bot data and requests (docs/sdk-v1.md §2.13–§2.15) ──

/// UTF-8 bytes of `chatData` and of `publicData` each (the server's limit, `chat_data` / `public_data`).
inline constexpr int MAX_WIDGET_DATA_BYTES = 1024;
/// UTF-8 bytes of a request's `data` (as `callback_data`).
inline constexpr int MAX_REQUEST_DATA_BYTES = 64;
/// UTF-8 bytes of an answer's `result`.
inline constexpr int MAX_REQUEST_RESULT_BYTES = 1024;
/// UTF-16 code units of a `copyText` text (as the `copy_text` button's).
inline constexpr int MAX_COPY_TEXT_LENGTH = 256;
/// A background request (not from a press) at most this often, per widget.
inline constexpr double BACKGROUND_REQUEST_INTERVAL_MS = 5000;
/// The request ids the shim numbers its calls with.
inline constexpr int64_t MAX_REQUEST_ID = 2147483647;
/// A Telegram user id fits in 53 bits: a JS number holds it exactly.
inline constexpr int64_t MAX_VIEWER_ID = 9007199254740991;
/// UTF-16 code units of the viewer's first and last name (each 1 to this).
inline constexpr int MAX_VIEWER_NAME_LENGTH = 64;
/// The viewer's username: 5–32 characters of A–Z, a–z, 0–9 and _ (the TS `VIEWER_USERNAME`).
inline constexpr int MIN_VIEWER_USERNAME_LENGTH = 5;
inline constexpr int MAX_VIEWER_USERNAME_LENGTH = 32;
/// The chat kinds a host may report (`Chat.type` of the Bot API); a value outside them is no chat type.
inline constexpr std::array<std::string_view, 4> CHAT_TYPES = {"private", "group", "supergroup", "channel"};
/// What a refused or failed request rejects with in the widget (`error.code`).
/// What a refused or failed request rejects with in the widget (`error.code`); REJECTED and BAD_REQUEST only for a wallet call.
inline constexpr std::array<std::string_view, 8> REQUEST_ERRORS = {"NOT_ALLOWED", "BUSY", "RATE_LIMITED", "TIMEOUT", "FAILED", "INVALID_ANSWER", "REJECTED", "BAD_REQUEST"};

// ── the wallet (docs/sdk-v1.md §2.18): TON Connect's formats, Gram Wallet only ──

/// Messages in one transaction: what Gram Wallet takes (its TON Connect `maxMessages`).
inline constexpr size_t MAX_WALLET_MESSAGES = 255;
/// Characters of a message's `payload` or `stateInit` (a bag of cells in base64).
inline constexpr size_t MAX_WALLET_BOC_LENGTH = 8192;
/// Characters of every message's `payload` and `stateInit` together.
inline constexpr size_t MAX_WALLET_BOC_TOTAL = 65536;
/// UTF-8 bytes of a `signData` text; characters of its `bytes` or `cell` (base64).
inline constexpr size_t MAX_SIGN_DATA_LENGTH = 4096;
/// UTF-8 bytes of a `cell` payload's TL-B schema.
inline constexpr size_t MAX_SIGN_DATA_SCHEMA_BYTES = 1024;
/// After the answer to a call made by a tap (a request or a wallet call) reached the widget, one wallet call may follow without a new tap this long.
inline constexpr double WALLET_GRANT_MS = 10000;
/// TON Connect's network ids: the mainnet and the testnet.
inline constexpr std::array<std::string_view, 2> WALLET_NETWORKS = {"-239", "-3"};

bool isPrivilegedMethod(std::string_view method);
/// Exactly one of `PREVIEW_METHODS` (case-sensitive).
bool allowedInPreview(std::string_view method);

/// UTF-8 length of a string: its byte length (a lone surrogate in WTF-8 is 3 bytes, as the TS version counts it).
inline size_t utf8Length(std::string_view s) { return s.size(); }
/// UTF-16 length of a UTF-8 string (the JS `length`): 2 for each 4-byte sequence, 1 for every other one (and for each
/// byte that starts none: only `wellFormed` strings are measured here).
size_t utf16Length(std::string_view s);
/// Valid UTF-8 without a surrogate code point (WTF-8 ED A0..BF xx): the string has a UTF-8 form, what the server and
/// the clipboard take (the TS `wellFormed`: no lone surrogate).
bool wellFormed(std::string_view s);

/**
 * The URL a link call opens, or nullopt when it breaks the rules: an absolute
 * `https:` URL, no user name or password, an ASCII host of LDH labels (an IDN in
 * its `xn--` form), an optional port, at most 256 bytes as given and at most
 * 256 characters once non-ASCII characters after the host are percent-encoded
 * as UTF-8. `openTelegramLink` takes only t.me, telegram.me and telegram.dog
 * (and their subdomains), any path; any other `method` is `openLink`, which
 * takes every other host. Scheme and host come out lowercase (A–Z only), the
 * rest as given. Invalid UTF-8 and surrogate code points are refused.
 */
std::optional<std::string> checkLink(std::string_view method, std::string_view url);
/// The same for a JSON value: anything but a string is nullopt.
std::optional<std::string> checkLinkValue(std::string_view method, const Json& url);

struct HapticCall {
    std::string haptic;                // "impact" | "notification" | "selection"
    std::optional<std::string> value;  // the style or type; none for "selection"
    bool operator==(const HapticCall& o) const { return haptic == o.haptic && value == o.value; }
};

/// The haptic of a `haptic` bridge call's args, or nullopt: `['impact', style]`, `['notification', type]`, `['selection']`.
std::optional<HapticCall> checkHaptic(const Json& args);

/// The resize anchor of a `setResizeAnchor` call's argument, or nullopt: exactly "top" or "bottom" (case-sensitive, no trimming).
std::optional<std::string> checkResizeAnchor(std::string_view edge);
/// The same for a JSON value: anything but a string is nullopt.
std::optional<std::string> checkResizeAnchorValue(const Json& edge);

/**
 * A bridge call that passed its checks (the TS `BridgeCall`): an action for the client (`onAction`); for
 * `setResizeAnchor`, the edge, which no action carries: the host keeps the latest one next to the height it reports
 * (`WidgetDiagnostics::resizeAnchor`); for `request`, the shim's id and the data: the request gate, then the
 * client's `onRequest`; for `copyText`, the text: the client's `onCopyText`; for `watchPublicData`, the flag the host
 * keeps (round 7). Exactly one is set. Since v0.1.3 (the edge) and the bot's requests (the request, the text, the
 * flag); `WidgetAction` is unchanged.
 */
struct BridgeCall {
    struct Request {
        /// The shim's number for the call, 1 … 2147483647: the answer goes back with it.
        int64_t id = 0;
        /// 1–64 bytes of UTF-8, well-formed.
        std::string data;
        /// The widget asks the server to attach the user's wallet (docs/sdk-v1.md §2.18).
        bool wallet = false;
        bool operator==(const Request& o) const { return id == o.id && data == o.data && wallet == o.wallet; }
    };
    /// `wallet`: a wallet call, the shim's id and the checked call (the request gate, then the client's `onWallet`).
    struct Wallet {
        int64_t id = 0;
        WalletCall call;
        bool operator==(const Wallet& o) const { return id == o.id && call == o.call; }
    };
    std::optional<WidgetAction> action;
    /// `setResizeAnchor`: "top" or "bottom".
    std::optional<std::string> resizeAnchor;
    /// `request`.
    std::optional<Request> request;
    /// `wallet` (docs/sdk-v1.md §2.18).
    std::optional<Wallet> wallet;
    /// `copyText`: 1–256 UTF-16 code units, well-formed.
    std::optional<std::string> copyText;
    /// `watchPublicData` (round 7): whether the widget watches its shared `public_data` (`watchPublicData()` /
    /// `unwatchPublicData()`); not an action: the host keeps the latest, the client polls only for widgets that watch.
    std::optional<bool> watchPublicData;

    bool operator==(const BridgeCall& o) const {
        return action == o.action && resizeAnchor == o.resizeAnchor && request == o.request && wallet == o.wallet && copyText == o.copyText && watchPublicData == o.watchPublicData;
    }
    /// The TS `BridgeCall` shape: the action's (`WidgetAction::toJson`), {kind:'setResizeAnchor', edge},
    /// {kind:'request', id, data[, wallet: true]}, {kind:'wallet', id, call}, {kind:'copyText', text} or {kind:'watchPublicData', on}.
    Json toJson() const;
};

/// The checked call, or nullopt when the method is unknown or its arguments are wrong (`bridge-rejected`).
/// Before v0.1.3 this returned `std::optional<WidgetAction>`; the action is now `->action`.
std::optional<BridgeCall> checkBridgeCall(const Json& method, const Json& args);

/// Whether a `request`'s options ask for the wallet, or nullopt: an object with at most `wallet`, a boolean (absent or null: false).
std::optional<bool> checkRequestOptions(const Json& options);
/// A TON address, or nullopt: raw (`0:` or `-1:` and 64 hex digits) or user-friendly (48 characters of base64 or base64url); a transaction's `from`.
std::optional<std::string> checkWalletAddress(const Json& address);
/// A message's destination, or nullopt: user-friendly only (TON Connect: the wallet must refuse a raw one).
std::optional<std::string> checkWalletDestination(const Json& address);
/// A transaction for `sendTransaction`, or nullopt: TON Connect's fields only (`valid_until`, `network`, `from`,
/// `messages`), 1–255 messages of `address`, `amount` and optionally `payload` and `stateInit`. `valid_until` an integer
/// of 1 to 2^32 − 1, `network` "-239" or "-3", `from` raw or user-friendly, a message's `address` user-friendly only,
/// amounts decimal strings of nanotons (at most 37 digits, no leading zero), payloads base64 of at most 8192 characters
/// each and 65536 together. Null fields are left out.
std::optional<WalletTransaction> checkWalletTransaction(const Json& tx);
/// A `signData` payload, or nullopt: TON Connect's three kinds with their fields only, and optionally `network` and
/// `from` as a transaction's. "text": 1–4096 UTF-8 bytes; "binary": `bytes`, base64 of at most 4096 characters; "cell":
/// `schema` (TL-B, 1–1024 UTF-8 bytes) and `cell`, base64 of at most 4096 characters. Null fields are left out.
std::optional<SignDataPayload> checkSignDataPayload(const Json& payload);

/// A request's `data`, or nullopt: a well-formed string of 1–64 UTF-8 bytes.
std::optional<std::string> checkRequestData(std::string_view data);
std::optional<std::string> checkRequestDataValue(const Json& data);
/// A `copyText` text, or nullopt: a well-formed string of 1–256 UTF-16 code units.
std::optional<std::string> checkCopyText(std::string_view text);
std::optional<std::string> checkCopyTextValue(const Json& text);
/// One of `chatData` / `publicData`: a well-formed string of 1–1024 UTF-8 bytes without NUL (other control characters
/// are text); nullopt for none (the empty string the bot clears it with) and for anything else.
std::optional<std::string> checkWidgetDataValue(std::string_view value);

/// The bot's data as the widget sees it, and the fields the client passed that broke the rules (the glue reports
/// `data-rejected`): "chatData" and/or "publicData", in that order.
struct CheckedWidgetData {
    std::optional<std::string> chatData;
    std::optional<std::string> publicData;
    std::vector<std::string> rejected;
    /// The TS `CheckedWidgetData` shape: {chatData, publicData, rejected} with JSON nulls for none.
    Json toJson() const;
};

/// The full state of the bot's two strings, as the client passes it at creation and in `setData`. A copy has no
/// `chatData` (the server never copies it): the glue drops what the client passed, silently; its publicData can change.
CheckedWidgetData checkWidgetData(const WidgetData& data, bool copy);

/// The viewer as the widget sees it, and the fields the client passed that broke their rules.
struct CheckedViewer {
    /// None without an id of 1 … 2^53 − 1 (not reported); else the id and the fields that passed.
    std::optional<WidgetViewer> viewer;
    /// "first_name", "last_name", "username" (in this order; `is_premium` is a bool here and cannot break its rule).
    std::vector<std::string> rejected;
    /// The TS shape: {viewer: {id, first_name?, last_name?, username?, is_premium?: true} | null, rejected}.
    Json toJson() const;
};

/// The viewer's check (the TS `checkViewer`): no viewer without a valid id; a name of 1–64 UTF-16 code units, well-formed
/// (an empty one is rejected); a username of 5–32 ASCII letters, digits and _; Premium kept only when true.
CheckedViewer checkViewer(const std::optional<WidgetViewer>& viewer);
/// The viewer in the widget's field names (`TelegramWidget.viewer`, Mini Apps' `WebAppUser`): `id`, then `first_name`,
/// `last_name`, `username` when set, `is_premium` only when true. Unchecked: pass a checked viewer.
Json viewerJson(const WidgetViewer& viewer);
/// Why a viewer field was left out, for `data-rejected` (`viewer.<field>: <rule>; the widget does not see it`; the TS
/// `VIEWER_FIELD_RULES`): "first_name", "last_name", "username", "is_premium".
std::string_view viewerFieldRule(std::string_view field);
/// Exactly "private", "group", "supergroup" or "channel", else nullopt.
std::optional<std::string> checkChatType(std::string_view chatType);

/// An answer the glue passes into the widget: the bot's `result` (none for none).
struct CheckedAnswer {
    std::optional<std::string> result;
    /// The TS shape: {result} with JSON null for none.
    Json toJson() const;
};

/// The client's answer: `result` none, or text the glue passes on: well-formed, no NUL, at most 1024 UTF-8 bytes (empty
/// allowed). Nullopt for anything else (the widget gets `INVALID_ANSWER`, `answer-rejected`).
std::optional<CheckedAnswer> checkRequestAnswer(const WidgetRequestAnswer& answer);

/// What the widget's promise rejects with for a client's failure: "TIMEOUT", "RATE_LIMITED", anything else "FAILED".
std::string_view requestFailureCode(WidgetRequestFailure failure);
/// The same for a wallet call: also "REJECTED" and "BAD_REQUEST".
std::string_view walletFailureCode(WidgetRequestFailure failure);

/**
 * The verdict on a request (docs/sdk-v1.md §2.14): `Foreground` (it consumes the task's press) and `Background` go to
 * the client; `Held` (a background request from a widget off screen) waits in the glue and goes to the client when the
 * widget appears on screen (`RequestGate::show`); the rest are refused: `Disabled` (the message has no `canQuery`),
 * `Busy` (one request in flight per widget, a held one included), `NoActivation` (no press and no `backgroundQuery`),
 * `RateLimited` (a background request sooner than 5 s after the last one, unless the widget appeared on screen since).
 * A wallet call (`RequestGate::decideWallet`), or a request that asks for the wallet, goes out only as `Foreground` or
 * `Granted` (no press, but within 10 s of the answer to a call made by a tap); `NoWallet` when the client has no wallet.
 */
enum class RequestVerdict { Foreground, Background, Held, Granted, Disabled, NoWallet, Busy, NoActivation, RateLimited };
/// The TS verdict: "foreground", "background", "held", "granted", "disabled", "no-wallet", "busy", "no-activation", "rate-limited".
std::string_view requestVerdictName(RequestVerdict verdict);
/// What the widget's promise rejects with for a refused request ("BUSY", "RATE_LIMITED", "NOT_ALLOWED"); nullopt for
/// the three that go to the client.
std::optional<std::string_view> requestRefusal(RequestVerdict verdict);

/**
 * Only where the message allows requests (`canQuery`); one in flight per widget, a held one included; a press unlocks
 * one; without a press only with `backgroundQuery` (the original in the private chat with the bot): one when the
 * widget appears on screen (`show`, and at creation), then at most one per 5 s counted from the start of the last
 * background request, while it stays visible; one made while it is off screen is held (`Held`) and is the one of its
 * next appearance (`show` returns true: the glue sends it then). `gesture`: the task has a press no privileged call
 * consumed yet (the glue consumes it on `Foreground`). `finish` when the answer or the failure has gone into the
 * widget, or when the glue drops a held request (the widget stopped); `answeredAtMs` when an answer with no error went
 * in then. Same order of checks as the TS class.
 *
 * The wallet (docs/sdk-v1.md §2.18): only with `wallet` (the client has Gram Wallet for this user). A request that asks
 * for the wallet (`withWallet`) and a wallet call (`decideWallet`) need the visible widget and a press, or the grant: the
 * answer to the last call made by a press or a grant (a request or a wallet call) lets one wallet call follow within
 * 10 s. Any call that goes out as `Foreground` or `Granted` uses up the grant; its answer brings the next one.
 */
class RequestGate {
public:
    struct Conditions {
        bool gesture = false;
        bool visible = false;
        bool canQuery = false;
        bool backgroundQuery = false;
        bool withWallet = false;
        bool wallet = false;
    };
    struct WalletConditions {
        bool gesture = false;
        bool visible = false;
        bool canQuery = false;
        bool wallet = false;
    };
    RequestVerdict decide(double nowMs, const Conditions& s);
    /// A wallet call (`sendTransaction`, `signData`): on a press, or on the grant within 10 s of its answer.
    RequestVerdict decideWallet(double nowMs, const WalletConditions& s);
    void finish(std::optional<double> answeredAtMs = std::nullopt) {
        if (granting_ && answeredAtMs) grantAt_ = answeredAtMs;
        inFlight_ = held_ = granting_ = false;
    }
    /// The widget appeared on screen (it was hidden): its next background request may go out at once. True when a held
    /// request goes out now, as this appearance's background request (the glue sends it; it stays in flight).
    bool show(double nowMs);
    bool busy() const { return inFlight_; }

private:
    RequestVerdict out(RequestVerdict verdict) {
        inFlight_ = granting_ = true;
        grantAt_.reset();
        return verdict;
    }
    bool inFlight_ = false;
    bool held_ = false;
    std::optional<double> lastBackground_;
    /// The call in flight went out on a press or the grant: its answer grants one wallet call.
    bool granting_ = false;
    std::optional<double> grantAt_;
};

/**
 * Haptics need a visible widget and room in the bucket (5 calls, refilled at 10 per second), and a touch (v0.2.2):
 * while a finger is down on the widget and for 5 s after it is lifted they are free (under the rate limit); outside
 * that, the first call opens a series whose first second passes, then nothing until the next touch, or 10 s after the
 * series began when there was none (docs/sdk-v1.md §2.6; a widget cannot buzz the phone from a timer). `press` /
 * `release` are the host's trusted presses (the renderer's claim at a primary pointerdown and its release), never the
 * widget's events. `Play` delivers; `Drop` drops silently; `Report` drops and reports `haptic-limit` (at most once per
 * second). Same arithmetic, in the same order, as the TS class (the fixture steps through the floats).
 */
class HapticGate {
public:
    enum class Decision { Play, Drop, Report };
    Decision decide(double nowMs, bool visible);
    /// A finger or a mouse button went down on the widget (trusted input only).
    void press(double nowMs) { down_ = nowMs; }
    /// The press ended: the finger was lifted or the press was cancelled.
    void release(double nowMs) {
        down_.reset();
        lifted_ = nowMs;
    }
    static std::string_view name(Decision d) { return d == Decision::Play ? "play" : d == Decision::Drop ? "drop" : "report"; }

private:
    double tokens_ = HAPTIC_BURST;
    std::optional<double> last_;
    std::optional<double> reported_;
    /// A press is on since then.
    std::optional<double> down_;
    /// The last press ended then.
    std::optional<double> lifted_;
    /// The last series (haptics outside a touch) began then.
    std::optional<double> series_;
};

/// The theme keys of `params` whose values are `#rrggbb` (lowercased); everything else is dropped.
std::map<std::string, std::string> checkThemeParams(const Json& params);
std::map<std::string, std::string> checkThemeParams(const std::map<std::string, std::string>& params);
Json themeParamsJson(const std::map<std::string, std::string>& checked);

/// A BCP 47 tag of at most 35 characters (`ru`, `en-US`, `pt-BR`, `zh-Hant-TW`), or nullopt.
std::optional<std::string> checkLocale(std::string_view locale);
std::optional<std::string> checkLocaleValue(const Json& locale);

/// UTF-8 bytes of an area's keys and values.
size_t storageBytes(const StorageArea& area);

/// An object of strings within the quota, or nullopt. A key given twice counts once, with its last value (JSON.parse).
std::optional<StorageArea> checkStorageArea(const Json& area);
/// The same for an area the client passed (only the quota can be wrong).
bool storageAreaFits(const StorageArea& area);

struct StorageCommit {
    std::optional<StorageArea> local;
    std::optional<StorageArea> session;
    Json toJson() const;
};

/// A commit (or a preload) of `local` and/or `session`, each checked whole; nullopt when anything is wrong (`storage-rejected`).
std::optional<StorageCommit> checkStorageCommit(const Json& commit);

/// `{"local": {…}, "session": {…}}` of a client preload, or `{}` (and false) when either area is over the quota.
Json storagePreloadJson(const WidgetStorage& storage, bool* accepted = nullptr);

} // namespace sdk
} // namespace twidget
