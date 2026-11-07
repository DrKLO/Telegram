// Minimal JSON value for the trusted glue: parse what the sandbox / shim sends
// (untrusted strings — bounded depth, strict grammar) and serialise what the
// glue sends back. Deliberately small; no external dependency.
#pragma once

#include <charconv>
#include <clocale>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <limits>
#include <locale>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>
#if defined(__APPLE__)
#include <xlocale.h>
#endif

// Floating-point std::to_chars needs macOS 13.3 / iOS 16.3 on Apple's libc++;
// older deployment targets (Telegram macOS supports 10.13) take the
// shortest-precision fallback in Json::number.
#if !defined(__APPLE__)
#define TWIDGET_JSON_TO_CHARS_DOUBLE 1
#elif defined(_LIBCPP_AVAILABILITY_HAS_TO_CHARS_FLOATING_POINT) && _LIBCPP_AVAILABILITY_HAS_TO_CHARS_FLOATING_POINT
#define TWIDGET_JSON_TO_CHARS_DOUBLE 1
#else
#define TWIDGET_JSON_TO_CHARS_DOUBLE 0
#endif

namespace twidget {

class Json {
public:
    enum class Type { Null, Bool, Number, String, Array, Object };
    using Array = std::vector<Json>;
    using Object = std::vector<std::pair<std::string, Json>>;

    Json() = default;
    Json(std::nullptr_t) {}
    Json(bool b) : type_(Type::Bool), bool_(b) {}
    Json(double d) : type_(Type::Number), num_(d) {}
    Json(int i) : Json(double(i)) {}
    Json(long i) : Json(double(i)) {}
    Json(long long i) : Json(double(i)) {}
    Json(unsigned i) : Json(double(i)) {}
    Json(unsigned long i) : Json(double(i)) {}
    Json(unsigned long long i) : Json(double(i)) {}
    Json(const char* s) : type_(Type::String), str_(s) {}
    Json(std::string s) : type_(Type::String), str_(std::move(s)) {}
    Json(std::string_view s) : type_(Type::String), str_(s) {}
    Json(Array a) : type_(Type::Array), arr_(std::move(a)) {}
    Json(Object o) : type_(Type::Object), obj_(std::move(o)) {}

    static Json array() { return Json(Array{}); }
    static Json object() { return Json(Object{}); }

    Type type() const { return type_; }
    bool isNull() const { return type_ == Type::Null; }
    bool isBool() const { return type_ == Type::Bool; }
    bool isNumber() const { return type_ == Type::Number; }
    bool isString() const { return type_ == Type::String; }
    bool isArray() const { return type_ == Type::Array; }
    bool isObject() const { return type_ == Type::Object; }

    bool asBool(bool def = false) const { return type_ == Type::Bool ? bool_ : def; }
    double asNumber(double def = 0) const { return type_ == Type::Number ? num_ : def; }
    int asInt(int def = 0) const { return type_ == Type::Number && std::isfinite(num_) ? int(num_) : def; }
    const std::string& asString() const { static const std::string empty; return type_ == Type::String ? str_ : empty; }
    const Array& arr() const { static const Array empty; return type_ == Type::Array ? arr_ : empty; }
    const Object& obj() const { static const Object empty; return type_ == Type::Object ? obj_ : empty; }
    size_t size() const { return type_ == Type::Array ? arr_.size() : type_ == Type::Object ? obj_.size() : 0; }

    /// Object member or nullptr.
    const Json* find(std::string_view key) const {
        if (type_ != Type::Object) return nullptr;
        for (const auto& [k, v] : obj_) if (k == key) return &v;
        return nullptr;
    }
    const Json& operator[](size_t i) const { static const Json null; return type_ == Type::Array && i < arr_.size() ? arr_[i] : null; }
    const Json& operator[](std::string_view key) const { static const Json null; const Json* v = find(key); return v ? *v : null; }

    Json& set(std::string key, Json value) {
        if (type_ != Type::Object) { *this = object(); }
        for (auto& [k, v] : obj_) if (k == key) { v = std::move(value); return *this; }
        obj_.emplace_back(std::move(key), std::move(value));
        return *this;
    }
    Json& push(Json value) {
        if (type_ != Type::Array) { *this = array(); }
        arr_.push_back(std::move(value));
        return *this;
    }

    std::string dump() const {
        std::string out;
        dumpTo(out);
        return out;
    }

    /// Quoted, escaped JSON string literal.
    static std::string quote(std::string_view s) {
        std::string out;
        out.reserve(s.size() + 2);
        out.push_back('"');
        for (unsigned char c : s) {
            switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            default:
                if (c < 0x20) {
                    static const char hex[] = "0123456789abcdef";
                    out += "\\u00";
                    out.push_back(hex[c >> 4]);
                    out.push_back(hex[c & 15]);
                } else {
                    out.push_back(char(c));
                }
            }
        }
        out.push_back('"');
        return out;
    }

    static std::string number(double d) {
        if (!std::isfinite(d)) return "null";
        if (d == std::floor(d) && std::fabs(d) < 1e15) {
            char buf[32];
            auto r = std::to_chars(buf, buf + sizeof buf, (long long)d);
            return std::string(buf, r.ptr);
        }
#if TWIDGET_JSON_TO_CHARS_DOUBLE
        char buf[64];
        auto r = std::to_chars(buf, buf + sizeof buf, d);
        return std::string(buf, r.ptr);
#else
        // Shortest of 15, 16, 17 significant digits that reads back as the same
        // double: 0.1 stays "0.1", not max_digits10's "0.10000000000000001".
        std::string out;
        for (int precision = 15; precision <= 17; precision++) {
            std::ostringstream stream;
            stream.imbue(std::locale::classic());
            stream.precision(precision);
            stream << d;
            out = stream.str();
            if (parseDouble(out.c_str()) == d) break;
        }
        return out;
#endif
    }

    /// strtod in the C locale. Plain strtod follows LC_NUMERIC: a Qt host running
    /// under ru_RU reads "16.5" as 16.
    static double parseDouble(const char* text) {
#if defined(_WIN32)
        static const _locale_t c = _create_locale(LC_NUMERIC, "C");
        return _strtod_l(text, nullptr, c);
#elif defined(__ANDROID__)
        return std::strtod(text, nullptr); // bionic: LC_NUMERIC is always "C"
#else
        static const locale_t c = newlocale(LC_NUMERIC_MASK, "C", (locale_t)0);
        return strtod_l(text, nullptr, c);
#endif
    }

    /// What a `\uD800`-style escape of a lone surrogate becomes: U+FFFD (the default), or the
    /// surrogate's own 3-byte WTF-8 form (ED A0..BF xx), which is how QuickJS hands a JS string with
    /// a lone surrogate to C++ outside JSON. `Keep` is for the SDK checks (sdk_policy.h): a URL with a
    /// lone surrogate must stay refusable, and Web Storage values must round-trip unchanged.
    enum class Surrogates { Replace, Keep };

    /// Strict parse; nullopt on any error. `maxDepth` bounds nesting (hostile input).
    static std::optional<Json> parse(std::string_view text, int maxDepth = 128) { return parse(text, Surrogates::Replace, maxDepth); }
    static std::optional<Json> parse(std::string_view text, Surrogates surrogates, int maxDepth = 128) {
        Parser p{text, 0, maxDepth, surrogates == Surrogates::Keep};
        p.ws();
        Json v;
        if (!p.value(v, 0)) return std::nullopt;
        p.ws();
        if (p.pos != text.size()) return std::nullopt;
        return v;
    }

private:
    void dumpTo(std::string& out) const {
        switch (type_) {
        case Type::Null: out += "null"; break;
        case Type::Bool: out += bool_ ? "true" : "false"; break;
        case Type::Number: out += number(num_); break;
        case Type::String: out += quote(str_); break;
        case Type::Array:
            out.push_back('[');
            for (size_t i = 0; i < arr_.size(); i++) { if (i) out.push_back(','); arr_[i].dumpTo(out); }
            out.push_back(']');
            break;
        case Type::Object:
            out.push_back('{');
            for (size_t i = 0; i < obj_.size(); i++) {
                if (i) out.push_back(',');
                out += quote(obj_[i].first);
                out.push_back(':');
                obj_[i].second.dumpTo(out);
            }
            out.push_back('}');
            break;
        }
    }

    struct Parser {
        std::string_view s;
        size_t pos;
        int maxDepth;
        bool keepSurrogates;

        void ws() { while (pos < s.size() && (s[pos] == ' ' || s[pos] == '\t' || s[pos] == '\n' || s[pos] == '\r')) pos++; }
        bool lit(std::string_view word) {
            if (s.substr(pos, word.size()) != word) return false;
            pos += word.size();
            return true;
        }
        static void utf8(std::string& out, uint32_t cp) {
            if (cp < 0x80) out.push_back(char(cp));
            else if (cp < 0x800) { out.push_back(char(0xC0 | (cp >> 6))); out.push_back(char(0x80 | (cp & 63))); }
            else if (cp < 0x10000) { out.push_back(char(0xE0 | (cp >> 12))); out.push_back(char(0x80 | ((cp >> 6) & 63))); out.push_back(char(0x80 | (cp & 63))); }
            else { out.push_back(char(0xF0 | (cp >> 18))); out.push_back(char(0x80 | ((cp >> 12) & 63))); out.push_back(char(0x80 | ((cp >> 6) & 63))); out.push_back(char(0x80 | (cp & 63))); }
        }
        bool hex4(uint32_t& v) {
            if (pos + 4 > s.size()) return false;
            v = 0;
            for (int i = 0; i < 4; i++) {
                char c = s[pos++];
                v <<= 4;
                if (c >= '0' && c <= '9') v |= uint32_t(c - '0');
                else if (c >= 'a' && c <= 'f') v |= uint32_t(c - 'a' + 10);
                else if (c >= 'A' && c <= 'F') v |= uint32_t(c - 'A' + 10);
                else return false;
            }
            return true;
        }
        bool string(std::string& out) {
            if (pos >= s.size() || s[pos] != '"') return false;
            pos++;
            while (pos < s.size()) {
                unsigned char c = (unsigned char)s[pos++];
                if (c == '"') return true;
                if (c < 0x20) return false;
                if (c != '\\') { out.push_back(char(c)); continue; }
                if (pos >= s.size()) return false;
                char e = s[pos++];
                switch (e) {
                case '"': out.push_back('"'); break;
                case '\\': out.push_back('\\'); break;
                case '/': out.push_back('/'); break;
                case 'b': out.push_back('\b'); break;
                case 'f': out.push_back('\f'); break;
                case 'n': out.push_back('\n'); break;
                case 'r': out.push_back('\r'); break;
                case 't': out.push_back('\t'); break;
                case 'u': {
                    uint32_t cp;
                    if (!hex4(cp)) return false;
                    if (cp >= 0xD800 && cp <= 0xDBFF) {
                        // A pair only with a low surrogate right after; any other escape after it is read on its own.
                        uint32_t lo = 0;
                        if (s.substr(pos, 2) == "\\u") {
                            size_t at = pos;
                            pos += 2;
                            if (!hex4(lo)) return false;
                            if (lo < 0xDC00 || lo > 0xDFFF) pos = at;
                        }
                        if (lo >= 0xDC00 && lo <= 0xDFFF) cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                        else if (!keepSurrogates) cp = 0xFFFD;
                    } else if (cp >= 0xDC00 && cp <= 0xDFFF) {
                        if (!keepSurrogates) cp = 0xFFFD;
                    }
                    utf8(out, cp);
                    break;
                }
                default: return false;
                }
            }
            return false;
        }
        bool number(Json& out) {
            size_t start = pos;
            if (pos < s.size() && s[pos] == '-') pos++;
            if (pos >= s.size()) return false;
            if (s[pos] == '0') pos++;
            else if (s[pos] >= '1' && s[pos] <= '9') { while (pos < s.size() && s[pos] >= '0' && s[pos] <= '9') pos++; }
            else return false;
            if (pos < s.size() && s[pos] == '.') {
                pos++;
                size_t d = pos;
                while (pos < s.size() && s[pos] >= '0' && s[pos] <= '9') pos++;
                if (pos == d) return false;
            }
            if (pos < s.size() && (s[pos] == 'e' || s[pos] == 'E')) {
                pos++;
                if (pos < s.size() && (s[pos] == '+' || s[pos] == '-')) pos++;
                size_t d = pos;
                while (pos < s.size() && s[pos] >= '0' && s[pos] <= '9') pos++;
                if (pos == d) return false;
            }
            std::string tmp(s.substr(start, pos - start));
            out = Json(parseDouble(tmp.c_str()));
            return true;
        }
        bool value(Json& out, int depth) {
            if (depth > maxDepth) return false;
            if (pos >= s.size()) return false;
            char c = s[pos];
            if (c == '{') {
                pos++;
                Object obj;
                ws();
                if (pos < s.size() && s[pos] == '}') { pos++; out = Json(std::move(obj)); return true; }
                for (;;) {
                    ws();
                    std::string key;
                    if (!string(key)) return false;
                    ws();
                    if (pos >= s.size() || s[pos] != ':') return false;
                    pos++;
                    ws();
                    Json v;
                    if (!value(v, depth + 1)) return false;
                    obj.emplace_back(std::move(key), std::move(v));
                    ws();
                    if (pos >= s.size()) return false;
                    if (s[pos] == ',') { pos++; continue; }
                    if (s[pos] == '}') { pos++; out = Json(std::move(obj)); return true; }
                    return false;
                }
            }
            if (c == '[') {
                pos++;
                Array arr;
                ws();
                if (pos < s.size() && s[pos] == ']') { pos++; out = Json(std::move(arr)); return true; }
                for (;;) {
                    ws();
                    Json v;
                    if (!value(v, depth + 1)) return false;
                    arr.push_back(std::move(v));
                    ws();
                    if (pos >= s.size()) return false;
                    if (s[pos] == ',') { pos++; continue; }
                    if (s[pos] == ']') { pos++; out = Json(std::move(arr)); return true; }
                    return false;
                }
            }
            if (c == '"') { std::string str; if (!string(str)) return false; out = Json(std::move(str)); return true; }
            if (c == 't') { if (!lit("true")) return false; out = Json(true); return true; }
            if (c == 'f') { if (!lit("false")) return false; out = Json(false); return true; }
            if (c == 'n') { if (!lit("null")) return false; out = Json(); return true; }
            return number(out);
        }
    };

    Type type_ = Type::Null;
    bool bool_ = false;
    double num_ = 0;
    std::string str_;
    Array arr_;
    Object obj_;
};

} // namespace twidget
