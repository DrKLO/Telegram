#include "twidget/sdk_policy.h"

#include <algorithm>
#include <cmath>
#include <initializer_list>

namespace twidget {

Json WidgetAction::toJson() const {
    Json o = Json::object();
    switch (kind) {
    case Ready: o.set("kind", "ready"); break;
    case OpenLink: o.set("kind", "openLink").set("url", url); break;
    case OpenTelegramLink: o.set("kind", "openTelegramLink").set("url", url); break;
    case Haptic:
        o.set("kind", "haptic").set("haptic", hapticKind);
        if (!hapticValue.empty()) o.set("value", hapticValue);
        break;
    }
    return o;
}

namespace sdk {

namespace {

template <size_t N>
bool contains(const std::array<std::string_view, N>& list, std::string_view s) {
    return std::find(list.begin(), list.end(), s) != list.end();
}

bool isHex(unsigned char c) { return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'); }
bool isAlpha(unsigned char c) { return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'); }
bool isDigit(unsigned char c) { return c >= '0' && c <= '9'; }

/// A JSON number that is an integer within [min, max] (the TS `Number.isInteger` and the range test): not 1.5, not "1".
std::optional<int64_t> integerIn(const Json& v, double min, double max) {
    double n = v.asNumber();
    if (!v.isNumber() || !std::isfinite(n) || std::floor(n) != n || n < min || n > max) return std::nullopt;
    return int64_t(n);
}

/// Lowercases A–Z only: a Unicode mapping (U+212A KELVIN SIGN → `k`) must not turn a non-ASCII host into an ASCII one.
std::string asciiLower(std::string_view s) {
    std::string out(s);
    for (char& c : out) if (c >= 'A' && c <= 'Z') c = char(c + 32);
    return out;
}

bool endsWith(std::string_view s, std::string_view suffix) { return s.size() >= suffix.size() && s.substr(s.size() - suffix.size()) == suffix; }

bool isTelegramHost(std::string_view host) {
    for (std::string_view t : TELEGRAM_HOSTS)
        if (host == t || (host.size() > t.size() && endsWith(host, t) && host[host.size() - t.size() - 1] == '.')) return true;
    return false;
}

/// ASCII that may stand unescaped after the authority: RFC 3986 unreserved, reserved and `%`.
bool isUrlAscii(unsigned char c) {
    if (isAlpha(c) || isDigit(c)) return true;
    static constexpr std::string_view other = "-._~:/?#[]@!$&'()*+,;=%";
    return other.find(char(c)) != std::string_view::npos;
}

/// Length of the well-formed UTF-8 sequence of a scalar value at `s[i]` (a non-ASCII lead byte), 0 when it is
/// not one: a bad lead or continuation byte, an overlong form, a surrogate (WTF-8 ED A0..BF), above U+10FFFF, truncated.
size_t utf8Sequence(std::string_view s, size_t i) {
    auto at = [&](size_t k) -> unsigned { return i + k < s.size() ? (unsigned char)s[i + k] : 0x100; };
    auto cont = [&](size_t k, unsigned lo = 0x80, unsigned hi = 0xBF) { unsigned b = at(k); return b >= lo && b <= hi; };
    unsigned c = at(0);
    if (c >= 0xC2 && c <= 0xDF) return cont(1) ? 2 : 0;
    if (c == 0xE0) return cont(1, 0xA0) && cont(2) ? 3 : 0;
    if ((c >= 0xE1 && c <= 0xEC) || c == 0xEE || c == 0xEF) return cont(1) && cont(2) ? 3 : 0;
    if (c == 0xED) return cont(1, 0x80, 0x9F) && cont(2) ? 3 : 0; // ED A0..BF would be U+D800..DFFF
    if (c == 0xF0) return cont(1, 0x90) && cont(2) && cont(3) ? 4 : 0;
    if (c >= 0xF1 && c <= 0xF3) return cont(1) && cont(2) && cont(3) ? 4 : 0;
    if (c == 0xF4) return cont(1, 0x80, 0x8F) && cont(2) && cont(3) ? 4 : 0;
    return 0;
}

/// The member a JS object built by JSON.parse would have: with a key given twice, the last value.
const Json* member(const Json& object, std::string_view key) {
    const Json* found = nullptr;
    for (const auto& [k, v] : object.obj()) if (k == key) found = &v;
    return found;
}

bool isThemeColor(const std::string& v) {
    if (v.size() != 7 || v[0] != '#') return false;
    for (size_t i = 1; i < 7; i++) if (!isHex((unsigned char)v[i])) return false;
    return true;
}

} // namespace

bool isPrivilegedMethod(std::string_view method) { return contains(PRIVILEGED_METHODS, method); }
bool allowedInPreview(std::string_view method) { return contains(PREVIEW_METHODS, method); }

size_t utf16Length(std::string_view s) {
    size_t n = 0;
    for (size_t i = 0; i < s.size();) {
        size_t len = (unsigned char)s[i] < 0x80 ? 1 : utf8Sequence(s, i);
        n += len == 4 ? 2 : 1; // a supplementary character is a surrogate pair in UTF-16
        i += len ? len : 1;
    }
    return n;
}

bool wellFormed(std::string_view s) {
    for (size_t i = 0; i < s.size();) {
        size_t len = (unsigned char)s[i] < 0x80 ? 1 : utf8Sequence(s, i);
        if (len == 0) return false;
        i += len;
    }
    return true;
}

std::optional<std::string> checkLink(std::string_view method, std::string_view url) {
    if (utf8Length(url) > size_t(MAX_LINK_LENGTH)) return std::nullopt;
    if (url.size() < 8 || asciiLower(url.substr(0, 8)) != "https://") return std::nullopt;
    size_t end = 8;
    while (end < url.size() && url[end] != '/' && url[end] != '?' && url[end] != '#') end++;
    std::string_view authority = url.substr(8, end - 8);
    if (authority.find('@') != std::string_view::npos) return std::nullopt;
    size_t colon = authority.find(':');
    std::string host = asciiLower(colon == std::string_view::npos ? authority : authority.substr(0, colon));
    std::optional<std::string_view> port;
    if (colon != std::string_view::npos) port = authority.substr(colon + 1);
    if (port) {
        if (port->empty() || port->size() > 5) return std::nullopt;
        long value = 0;
        for (char c : *port) {
            if (!isDigit((unsigned char)c)) return std::nullopt;
            value = value * 10 + (c - '0');
        }
        if (value > 65535) return std::nullopt;
    }
    if (host.empty() || host.size() > 253) return std::nullopt;
    for (size_t start = 0;;) {
        size_t dot = host.find('.', start);
        std::string_view label = std::string_view(host).substr(start, dot == std::string::npos ? std::string::npos : dot - start);
        if (label.empty() || label.size() > 63 || label.front() == '-' || label.back() == '-') return std::nullopt;
        for (char c : label) if (!(isDigit((unsigned char)c) || (c >= 'a' && c <= 'z') || c == '-')) return std::nullopt;
        if (dot == std::string::npos) break;
        start = dot + 1;
    }
    if (isTelegramHost(host) != (method == "openTelegramLink")) return std::nullopt;
    static constexpr char HEX[] = "0123456789ABCDEF";
    std::string rest;
    for (size_t i = end; i < url.size();) {
        unsigned char c = (unsigned char)url[i];
        if (c < 0x80) {
            if (!isUrlAscii(c)) return std::nullopt;
            if (c == '%' && !(i + 2 < url.size() && isHex((unsigned char)url[i + 1]) && isHex((unsigned char)url[i + 2]))) return std::nullopt;
            rest.push_back(char(c));
            i++;
            continue;
        }
        size_t n = utf8Sequence(url, i);
        if (n == 0) return std::nullopt;
        for (size_t k = 0; k < n; k++) {
            unsigned char b = (unsigned char)url[i + k];
            rest.push_back('%');
            rest.push_back(HEX[b >> 4]);
            rest.push_back(HEX[b & 15]);
        }
        i += n;
    }
    std::string out = "https://" + host + (port ? ":" + std::string(*port) : std::string()) + rest;
    if (out.size() > size_t(MAX_LINK_LENGTH)) return std::nullopt;
    return out;
}

std::optional<std::string> checkLinkValue(std::string_view method, const Json& url) {
    if (!url.isString()) return std::nullopt;
    return checkLink(method, url.asString());
}

std::optional<HapticCall> checkHaptic(const Json& args) {
    if (!args.isArray()) return std::nullopt;
    const Json& kind = args[0];
    const Json& value = args[1];
    if (kind.isString() && kind.asString() == "impact" && args.size() == 2 && value.isString() && contains(HAPTIC_IMPACT_STYLES, value.asString()))
        return HapticCall{"impact", value.asString()};
    if (kind.isString() && kind.asString() == "notification" && args.size() == 2 && value.isString() && contains(HAPTIC_NOTIFICATION_TYPES, value.asString()))
        return HapticCall{"notification", value.asString()};
    if (kind.isString() && kind.asString() == "selection" && args.size() == 1) return HapticCall{"selection", std::nullopt};
    return std::nullopt;
}

std::optional<std::string> checkResizeAnchor(std::string_view edge) {
    if (!contains(RESIZE_ANCHORS, edge)) return std::nullopt;
    return std::string(edge);
}

std::optional<std::string> checkResizeAnchorValue(const Json& edge) {
    return edge.isString() ? checkResizeAnchor(edge.asString()) : std::nullopt;
}

Json BridgeCall::toJson() const {
    if (action) return action->toJson();
    Json o = Json::object();
    if (resizeAnchor) o.set("kind", "setResizeAnchor").set("edge", *resizeAnchor);
    if (request) {
        o.set("kind", "request").set("id", request->id).set("data", request->data);
        if (request->wallet) o.set("wallet", true);
    }
    if (wallet) o.set("kind", "wallet").set("id", wallet->id).set("call", wallet->call.toJson());
    if (copyText) o.set("kind", "copyText").set("text", *copyText);
    if (watchPublicData) o.set("kind", "watchPublicData").set("on", *watchPublicData);
    return o;
}

}  // namespace sdk

Json WalletMessage::toJson() const {
    Json o = Json::object();
    o.set("address", address).set("amount", amount);
    if (payload) o.set("payload", *payload);
    if (stateInit) o.set("stateInit", *stateInit);
    return o;
}

Json WalletTransaction::toJson() const {
    Json list = Json::array();
    for (const auto& m : messages) list.push(m.toJson());
    Json o = Json::object();
    if (validUntil) o.set("valid_until", *validUntil);
    if (network) o.set("network", *network);
    if (from) o.set("from", *from);
    o.set("messages", std::move(list));
    return o;
}

Json SignDataPayload::toJson() const {
    Json o = Json::object();
    o.set("type", type);
    for (const auto& [k, v] : {std::pair{"text", &text}, {"bytes", &bytes}, {"schema", &schema}, {"cell", &cell}, {"network", &network}, {"from", &from}})
        if (*v) o.set(k, **v);
    return o;
}

Json WalletCall::toJson() const {
    Json o = Json::object();
    o.set("method", methodName());
    if (method == Method::SignData) o.set("payload", payload.toJson());
    else o.set("transaction", transaction.toJson());
    o.set("data", data);
    return o;
}

namespace sdk {

std::optional<BridgeCall> checkBridgeCall(const Json& method, const Json& args) {
    if (!args.isArray() || !method.isString()) return std::nullopt;
    const std::string& m = method.asString();
    BridgeCall call;
    WidgetAction action;
    if (m == "ready") {
        if (args.size() != 0) return std::nullopt;
        action.kind = WidgetAction::Ready;
        call.action = action;
        return call;
    }
    if (m == "openLink" || m == "openTelegramLink") {
        std::optional<std::string> url = args.size() == 1 ? checkLinkValue(m, args[0]) : std::nullopt;
        if (!url) return std::nullopt;
        action.kind = m == "openLink" ? WidgetAction::OpenLink : WidgetAction::OpenTelegramLink;
        action.url = std::move(*url);
        call.action = action;
        return call;
    }
    if (m == "haptic") {
        std::optional<HapticCall> h = checkHaptic(args);
        if (!h) return std::nullopt;
        action.kind = WidgetAction::Haptic;
        action.hapticKind = h->haptic;
        action.hapticValue = h->value.value_or("");
        call.action = action;
        return call;
    }
    if (m == "setResizeAnchor") {
        call.resizeAnchor = args.size() == 1 ? checkResizeAnchorValue(args[0]) : std::nullopt;
        if (!call.resizeAnchor) return std::nullopt;
        return call;
    }
    if (m == "request") {
        // [id, data] or [id, data, options]: an integer id 1 … 2147483647 (not 1.5, not "1"), then the data's own check.
        std::optional<std::string> data = args.size() == 2 || args.size() == 3 ? checkRequestDataValue(args[1]) : std::nullopt;
        std::optional<bool> wallet = args.size() == 3 ? checkRequestOptions(args[2]) : std::optional<bool>(false);
        std::optional<int64_t> id = integerIn(args[0], 1, double(MAX_REQUEST_ID));
        if (!data || !wallet || !id) return std::nullopt;
        call.request = BridgeCall::Request{*id, std::move(*data), *wallet};
        return call;
    }
    if (m == "wallet") {
        // [id, method, the method's argument, data]
        std::optional<int64_t> id = integerIn(args[0], 1, double(MAX_REQUEST_ID));
        std::optional<std::string> data = args.size() == 4 ? checkRequestDataValue(args[3]) : std::nullopt;
        if (!id || !data) return std::nullopt;
        WalletCall w;
        w.data = std::move(*data);
        if (args[1].isString() && args[1].asString() == "sendTransaction") {
            std::optional<WalletTransaction> tx = checkWalletTransaction(args[2]);
            if (!tx) return std::nullopt;
            w.transaction = std::move(*tx);
        } else if (args[1].isString() && args[1].asString() == "signData") {
            std::optional<SignDataPayload> p = checkSignDataPayload(args[2]);
            if (!p) return std::nullopt;
            w.method = WalletCall::Method::SignData;
            w.payload = std::move(*p);
        } else {
            return std::nullopt;
        }
        call.wallet = BridgeCall::Wallet{*id, std::move(w)};
        return call;
    }
    if (m == "watchPublicData") {
        // Exactly one boolean (not "true", not 1).
        if (args.size() != 1 || !args[0].isBool()) return std::nullopt;
        call.watchPublicData = args[0].asBool();
        return call;
    }
    if (m == "copyText") {
        call.copyText = args.size() == 1 ? checkCopyTextValue(args[0]) : std::nullopt;
        if (!call.copyText) return std::nullopt;
        return call;
    }
    return std::nullopt;
}

std::optional<bool> checkRequestOptions(const Json& options) {
    if (!options.isObject()) return std::nullopt;
    for (const auto& [k, v] : options.obj()) if (k != "wallet") return std::nullopt;
    const Json& w = options["wallet"];
    if (w.isNull()) return false;
    if (!w.isBool()) return std::nullopt;
    return w.asBool();
}

namespace {

/// A–Z, a–z, 0–9, `+`, `/`, `_`, `-`: base64 and base64url together.
bool isBase64Char(unsigned char c) { return isAlpha(c) || isDigit(c) || c == '+' || c == '/' || c == '_' || c == '-'; }

bool onlyKeys(const Json& o, std::initializer_list<std::string_view> keys) {
    for (const auto& [k, v] : o.obj())
        if (std::find(keys.begin(), keys.end(), k) == keys.end()) return false;
    return true;
}

/// Base64 or base64url of 1 to `max` characters, with at most two `=` at the end.
std::optional<std::string> base64(const Json& v, size_t max) {
    if (!v.isString() || v.asString().size() > max) return std::nullopt;
    std::string_view s = v.asString();
    for (int i = 0; i < 2 && !s.empty() && s.back() == '='; i++) s.remove_suffix(1);
    if (s.empty() || !std::all_of(s.begin(), s.end(), [](char c) { return isBase64Char((unsigned char)c); })) return std::nullopt;
    return v.asString();
}

/// Nanotons: a decimal string of at most 37 digits without a leading zero (`0` itself is an amount).
bool isAmount(std::string_view s) {
    return !s.empty() && s.size() <= 37 && std::all_of(s.begin(), s.end(), [](char c) { return isDigit((unsigned char)c); }) && (s.size() == 1 || s[0] != '0');
}

/// Text the server and the bot can take: well-formed, no NUL, 1 to `max` UTF-8 bytes.
std::optional<std::string> signText(const Json& v, size_t max) {
    if (!v.isString()) return std::nullopt;
    const std::string& s = v.asString();
    if (s.empty() || !wellFormed(s) || s.find('\0') != std::string::npos || utf8Length(s) > max) return std::nullopt;
    return s;
}

/// The optional `network` and `from` of a transaction or a payload; false when one breaks its rule.
bool walletTarget(const Json& input, std::optional<std::string>& network, std::optional<std::string>& from) {
    if (const Json& n = input["network"]; !n.isNull()) {
        if (!n.isString() || !contains(WALLET_NETWORKS, n.asString())) return false;
        network = n.asString();
    }
    if (const Json& f = input["from"]; !f.isNull()) {
        from = checkWalletAddress(f);
        if (!from) return false;
    }
    return true;
}

}  // namespace

std::optional<std::string> checkWalletAddress(const Json& address) {
    if (!address.isString()) return std::nullopt;
    const std::string& s = address.asString();
    if (s.size() == 48 && std::all_of(s.begin(), s.end(), [](char c) { return isBase64Char((unsigned char)c); })) return s;
    size_t prefix = s.rfind("0:", 0) == 0 ? 2 : s.rfind("-1:", 0) == 0 ? 3 : 0;
    if (prefix == 0 || s.size() != prefix + 64 || !std::all_of(s.begin() + long(prefix), s.end(), [](char c) { return isHex((unsigned char)c); })) return std::nullopt;
    return s;
}

std::optional<std::string> checkWalletDestination(const Json& address) {
    if (!address.isString()) return std::nullopt;
    const std::string& s = address.asString();
    if (s.size() != 48 || !std::all_of(s.begin(), s.end(), [](char c) { return isBase64Char((unsigned char)c); })) return std::nullopt;
    return s;
}

std::optional<WalletTransaction> checkWalletTransaction(const Json& tx) {
    if (!tx.isObject() || !onlyKeys(tx, {"valid_until", "network", "from", "messages"})) return std::nullopt;
    WalletTransaction out;
    if (const Json& v = tx["valid_until"]; !v.isNull()) {
        out.validUntil = integerIn(v, 1, 4294967295.0);
        if (!out.validUntil) return std::nullopt;
    }
    if (!walletTarget(tx, out.network, out.from)) return std::nullopt;
    const Json& list = tx["messages"];
    if (!list.isArray() || list.size() < 1 || list.size() > MAX_WALLET_MESSAGES) return std::nullopt;
    size_t total = 0;
    for (const Json& m : list.arr()) {
        if (!m.isObject() || !onlyKeys(m, {"address", "amount", "payload", "stateInit"})) return std::nullopt;
        std::optional<std::string> address = checkWalletDestination(m["address"]);
        if (!address || !m["amount"].isString() || !isAmount(m["amount"].asString())) return std::nullopt;
        WalletMessage message{*address, m["amount"].asString(), std::nullopt, std::nullopt};
        if (!m["payload"].isNull() && !(message.payload = base64(m["payload"], MAX_WALLET_BOC_LENGTH))) return std::nullopt;
        if (!m["stateInit"].isNull() && !(message.stateInit = base64(m["stateInit"], MAX_WALLET_BOC_LENGTH))) return std::nullopt;
        total += message.payload.value_or("").size() + message.stateInit.value_or("").size();
        out.messages.push_back(std::move(message));
    }
    if (total > MAX_WALLET_BOC_TOTAL) return std::nullopt;
    return out;
}

std::optional<SignDataPayload> checkSignDataPayload(const Json& payload) {
    if (!payload.isObject() || !payload["type"].isString()) return std::nullopt;
    const std::string& type = payload["type"].asString();
    SignDataPayload out;
    out.type = type;
    if (type == "text") {
        if (!onlyKeys(payload, {"type", "network", "from", "text"}) || !(out.text = signText(payload["text"], MAX_SIGN_DATA_LENGTH))) return std::nullopt;
    } else if (type == "binary") {
        if (!onlyKeys(payload, {"type", "network", "from", "bytes"}) || !(out.bytes = base64(payload["bytes"], MAX_SIGN_DATA_LENGTH))) return std::nullopt;
    } else if (type == "cell") {
        if (!onlyKeys(payload, {"type", "network", "from", "schema", "cell"}) || !(out.schema = signText(payload["schema"], MAX_SIGN_DATA_SCHEMA_BYTES)) ||
            !(out.cell = base64(payload["cell"], MAX_SIGN_DATA_LENGTH)))
            return std::nullopt;
    } else {
        return std::nullopt;
    }
    if (!walletTarget(payload, out.network, out.from)) return std::nullopt;
    return out;
}

std::optional<std::string> checkRequestData(std::string_view data) {
    if (!wellFormed(data)) return std::nullopt;
    size_t n = utf8Length(data);
    if (n < 1 || n > size_t(MAX_REQUEST_DATA_BYTES)) return std::nullopt;
    return std::string(data);
}

std::optional<std::string> checkRequestDataValue(const Json& data) {
    return data.isString() ? checkRequestData(data.asString()) : std::nullopt;
}

std::optional<std::string> checkCopyText(std::string_view text) {
    if (!wellFormed(text)) return std::nullopt;
    size_t n = utf16Length(text);
    if (n < 1 || n > size_t(MAX_COPY_TEXT_LENGTH)) return std::nullopt;
    return std::string(text);
}

std::optional<std::string> checkCopyTextValue(const Json& text) {
    return text.isString() ? checkCopyText(text.asString()) : std::nullopt;
}

std::optional<std::string> checkWidgetDataValue(std::string_view value) {
    if (value.empty() || !wellFormed(value) || value.find('\0') != std::string_view::npos || utf8Length(value) > size_t(MAX_WIDGET_DATA_BYTES)) return std::nullopt;
    return std::string(value);
}

static Json orNull(const std::optional<std::string>& s) { return s ? Json(*s) : Json(); }

Json CheckedWidgetData::toJson() const {
    Json o = Json::object();
    o.set("chatData", orNull(chatData)).set("publicData", orNull(publicData));
    Json r = Json::array();
    for (const std::string& field : rejected) r.push(field);
    o.set("rejected", std::move(r));
    return o;
}

CheckedWidgetData checkWidgetData(const WidgetData& data, bool copy) {
    CheckedWidgetData out;
    // A field the client set to anything but none ("" included) that fails the check is reported; a copy's chatData never is.
    auto check = [&](const std::optional<std::string>& v, std::optional<std::string>& field, const char* name) {
        if (v) field = checkWidgetDataValue(*v);
        if (!field && v && !v->empty()) out.rejected.emplace_back(name);
    };
    if (!copy) check(data.chatData, out.chatData, "chatData");
    check(data.publicData, out.publicData, "publicData");
    return out;
}

CheckedViewer checkViewer(const std::optional<WidgetViewer>& viewer) {
    CheckedViewer out;
    if (!viewer || viewer->id < 1 || viewer->id > MAX_VIEWER_ID) return out;
    WidgetViewer v;
    v.id = viewer->id;
    // Names count UTF-16 code units, as the JS `length` does (a lone surrogate arrives as WTF-8 and is refused).
    auto name = [&](const std::optional<std::string>& s, std::optional<std::string>& field, const char* key) {
        if (!s) return;
        size_t n = utf16Length(*s);
        if (wellFormed(*s) && n >= 1 && n <= size_t(MAX_VIEWER_NAME_LENGTH)) field = *s;
        else out.rejected.emplace_back(key);
    };
    name(viewer->firstName, v.firstName, "first_name");
    name(viewer->lastName, v.lastName, "last_name");
    if (viewer->username) {
        const std::string& u = *viewer->username;
        bool ok = u.size() >= size_t(MIN_VIEWER_USERNAME_LENGTH) && u.size() <= size_t(MAX_VIEWER_USERNAME_LENGTH);
        for (char c : u) ok = ok && (isAlpha((unsigned char)c) || isDigit((unsigned char)c) || c == '_');
        if (ok) v.username = u;
        else out.rejected.emplace_back("username");
    }
    v.isPremium = viewer->isPremium; // a bool: false is left out of the widget's view, never rejected
    out.viewer = std::move(v);
    return out;
}

Json viewerJson(const WidgetViewer& viewer) {
    Json o = Json::object();
    o.set("id", viewer.id);
    if (viewer.firstName) o.set("first_name", *viewer.firstName);
    if (viewer.lastName) o.set("last_name", *viewer.lastName);
    if (viewer.username) o.set("username", *viewer.username);
    if (viewer.isPremium) o.set("is_premium", true);
    return o;
}

Json CheckedViewer::toJson() const {
    Json o = Json::object();
    Json r = Json::array();
    for (const std::string& field : rejected) r.push(field);
    o.set("viewer", viewer ? viewerJson(*viewer) : Json()).set("rejected", std::move(r));
    return o;
}

std::string_view viewerFieldRule(std::string_view field) {
    if (field == "first_name" || field == "last_name") return "not text of 1–64 UTF-16 code units";
    if (field == "username") return "not 5–32 characters of A–Z, a–z, 0–9 and _";
    if (field == "is_premium") return "not a boolean";
    return "broke its rule";
}

std::optional<std::string> checkChatType(std::string_view chatType) {
    if (!contains(CHAT_TYPES, chatType)) return std::nullopt;
    return std::string(chatType);
}

Json CheckedAnswer::toJson() const {
    Json o = Json::object();
    o.set("result", orNull(result));
    return o;
}

std::optional<CheckedAnswer> checkRequestAnswer(const WidgetRequestAnswer& answer) {
    const std::optional<std::string>& r = answer.result;
    if (r && (!wellFormed(*r) || r->find('\0') != std::string::npos || utf8Length(*r) > size_t(MAX_REQUEST_RESULT_BYTES))) return std::nullopt;
    return CheckedAnswer{r};
}

std::string_view requestFailureCode(WidgetRequestFailure failure) {
    switch (failure) {
    case WidgetRequestFailure::Timeout: return "TIMEOUT";
    case WidgetRequestFailure::RateLimited: return "RATE_LIMITED";
    default: return "FAILED";
    }
}

std::string_view walletFailureCode(WidgetRequestFailure failure) {
    switch (failure) {
    case WidgetRequestFailure::Rejected: return "REJECTED";
    case WidgetRequestFailure::BadRequest: return "BAD_REQUEST";
    default: return requestFailureCode(failure);
    }
}

std::string_view requestVerdictName(RequestVerdict verdict) {
    switch (verdict) {
    case RequestVerdict::Foreground: return "foreground";
    case RequestVerdict::Background: return "background";
    case RequestVerdict::Held: return "held";
    case RequestVerdict::Granted: return "granted";
    case RequestVerdict::Disabled: return "disabled";
    case RequestVerdict::NoWallet: return "no-wallet";
    case RequestVerdict::Busy: return "busy";
    case RequestVerdict::NoActivation: return "no-activation";
    case RequestVerdict::RateLimited: return "rate-limited";
    }
    return "no-activation";
}

std::optional<std::string_view> requestRefusal(RequestVerdict verdict) {
    switch (verdict) {
    case RequestVerdict::Foreground: case RequestVerdict::Background: case RequestVerdict::Held: case RequestVerdict::Granted: return std::nullopt;
    case RequestVerdict::Busy: return "BUSY";
    case RequestVerdict::RateLimited: return "RATE_LIMITED";
    default: return "NOT_ALLOWED";
    }
}

RequestVerdict RequestGate::decide(double now, const Conditions& s) {
    if (!s.canQuery) return RequestVerdict::Disabled;
    if (s.withWallet && !s.wallet) return RequestVerdict::NoWallet;
    if (inFlight_) return RequestVerdict::Busy;
    if (s.gesture && s.visible) return out(RequestVerdict::Foreground);
    if (!s.backgroundQuery || s.withWallet) return RequestVerdict::NoActivation;
    if (!s.visible) {
        inFlight_ = held_ = true;
        return RequestVerdict::Held;
    }
    if (lastBackground_ && now - *lastBackground_ < BACKGROUND_REQUEST_INTERVAL_MS) return RequestVerdict::RateLimited;
    lastBackground_ = now;
    inFlight_ = true;
    return RequestVerdict::Background;
}

RequestVerdict RequestGate::decideWallet(double now, const WalletConditions& s) {
    if (!s.canQuery) return RequestVerdict::Disabled;
    if (!s.wallet) return RequestVerdict::NoWallet;
    if (inFlight_) return RequestVerdict::Busy;
    if (s.gesture && s.visible) return out(RequestVerdict::Foreground);
    if (s.visible && grantAt_ && now - *grantAt_ <= WALLET_GRANT_MS) return out(RequestVerdict::Granted);
    return RequestVerdict::NoActivation;
}

bool RequestGate::show(double now) {
    lastBackground_.reset();
    if (!held_) return false;
    held_ = false;
    lastBackground_ = now;
    return true;
}

HapticGate::Decision HapticGate::decide(double now, bool visible) {
    if (last_) tokens_ = std::min(double(HAPTIC_BURST), tokens_ + (std::max(0.0, now - *last_) * HAPTIC_PER_SECOND) / 1000);
    last_ = now;
    if (down_ && now - *down_ > HAPTIC_PRESS_MAX_MS) release(*down_ + HAPTIC_PRESS_MAX_MS);
    const bool touching = down_.has_value() || (lifted_ && now - *lifted_ <= HAPTIC_TOUCH_WINDOW_MS);
    bool allowed = visible;
    if (allowed && !touching && !(series_ && now - *series_ < HAPTIC_SERIES_MS)) {
        // A new series: after a touch at any time (one per touch), else 10 s after the last one began.
        const bool touchedSince = lifted_ && (!series_ || *lifted_ > *series_);
        if (!series_ || touchedSince || now - *series_ >= HAPTIC_SERIES_GAP_MS) series_ = now;
        else allowed = false;
    }
    if (allowed && tokens_ >= 1) {
        tokens_ -= 1;
        return Decision::Play;
    }
    if (reported_ && now - *reported_ < HAPTIC_REPORT_MS) return Decision::Drop;
    reported_ = now;
    return Decision::Report;
}

std::map<std::string, std::string> checkThemeParams(const Json& params) {
    std::map<std::string, std::string> out;
    if (!params.isObject()) return out;
    for (std::string_view key : THEME_KEYS) {
        const Json* v = member(params, key);
        if (v && v->isString() && isThemeColor(v->asString())) out.emplace(std::string(key), asciiLower(v->asString()));
    }
    return out;
}

std::map<std::string, std::string> checkThemeParams(const std::map<std::string, std::string>& params) {
    std::map<std::string, std::string> out;
    for (std::string_view key : THEME_KEYS) {
        auto it = params.find(std::string(key));
        if (it != params.end() && isThemeColor(it->second)) out.emplace(std::string(key), asciiLower(it->second));
    }
    return out;
}

Json themeParamsJson(const std::map<std::string, std::string>& checked) {
    Json o = Json::object();
    for (std::string_view key : THEME_KEYS) {
        auto it = checked.find(std::string(key));
        if (it != checked.end()) o.set(it->first, it->second);
    }
    return o;
}

std::optional<std::string> checkLocale(std::string_view locale) {
    if (locale.empty() || locale.size() > size_t(MAX_LOCALE_LENGTH)) return std::nullopt;
    // ^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$
    size_t i = 0;
    while (i < locale.size() && isAlpha((unsigned char)locale[i])) i++;
    if (i < 2 || i > 8) return std::nullopt;
    while (i < locale.size()) {
        if (locale[i] != '-') return std::nullopt;
        size_t start = ++i;
        while (i < locale.size() && (isAlpha((unsigned char)locale[i]) || isDigit((unsigned char)locale[i]))) i++;
        if (i - start < 1 || i - start > 8) return std::nullopt;
    }
    return std::string(locale);
}

std::optional<std::string> checkLocaleValue(const Json& locale) {
    if (!locale.isString()) return std::nullopt;
    return checkLocale(locale.asString());
}

size_t storageBytes(const StorageArea& area) {
    size_t n = 0;
    for (const auto& [k, v] : area) n += utf8Length(k) + utf8Length(v);
    return n;
}

std::optional<StorageArea> checkStorageArea(const Json& area) {
    if (!area.isObject()) return std::nullopt;
    std::map<std::string, const Json*> members;
    for (const auto& [k, v] : area.obj()) members[k] = &v; // the last value of a repeated key, as JSON.parse keeps it
    StorageArea out;
    for (const auto& [k, v] : members) {
        if (!v->isString()) return std::nullopt;
        out.emplace(k, v->asString());
    }
    if (storageBytes(out) > size_t(STORAGE_QUOTA_BYTES)) return std::nullopt;
    return out;
}

bool storageAreaFits(const StorageArea& area) { return storageBytes(area) <= size_t(STORAGE_QUOTA_BYTES); }

Json StorageCommit::toJson() const {
    Json o = Json::object();
    auto area = [](const StorageArea& a) {
        Json j = Json::object();
        for (const auto& [k, v] : a) j.set(k, v);
        return j;
    };
    if (local) o.set("local", area(*local));
    if (session) o.set("session", area(*session));
    return o;
}

std::optional<StorageCommit> checkStorageCommit(const Json& commit) {
    if (!commit.isObject()) return std::nullopt;
    StorageCommit out;
    std::map<std::string, const Json*> members;
    for (const auto& [k, v] : commit.obj()) members[k] = &v;
    for (const auto& [k, v] : members) {
        if (k != "local" && k != "session") return std::nullopt;
        std::optional<StorageArea> area = checkStorageArea(*v);
        if (!area) return std::nullopt;
        (k == "local" ? out.local : out.session) = std::move(area);
    }
    return out;
}

Json storagePreloadJson(const WidgetStorage& storage, bool* accepted) {
    bool ok = storageAreaFits(storage.local) && storageAreaFits(storage.session);
    if (accepted) *accepted = ok;
    if (!ok) return Json::object();
    return StorageCommit{storage.local, storage.session}.toJson();
}

} // namespace sdk
} // namespace twidget
