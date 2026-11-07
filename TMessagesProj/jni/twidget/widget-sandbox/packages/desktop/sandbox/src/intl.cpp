#include "twidget/intl.h"

#include "twidget/json.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <utility>
#include <vector>

// BasicIntl answers like V8 in English (en-US) for the cases tests/fixtures/intl-cases.json marks
// `basic`, without locale data: what the shim's Intl needs when the host plugs in nothing better.
// Approximate on purpose: numbers take the separators of a few dozen languages but place the
// currency as en-US does; dates, relative times and lists are en-US in every locale; plural
// rules are CLDR's (as PluralRules.swift has them); dates are always in UTC; collation folds
// Latin-1 only; case mapping is ASCII.

namespace twidget {

using Parts = std::vector<std::pair<std::string, std::string>>;

static const char* const NBSP = "\xC2\xA0";
static const char* const NNBSP = "\xE2\x80\xAF";

static std::string joined(const Parts& parts) {
    std::string out;
    for (const auto& p : parts) out += p.second;
    return out;
}

static std::string partsJson(const Parts& parts) {
    std::string out = "[";
    for (const auto& p : parts) {
        if (out.size() > 1) out += ",";
        out += "{\"type\":" + Json(p.first).dump() + ",\"value\":" + Json(p.second).dump() + "}";
    }
    return out + "]";
}

static std::string localeOf(const Json& req) {
    const Json& l = req["locale"];
    if (l.isString() && !l.asString().empty()) return l.asString();
    if (l.isArray() && l[0].isString()) return l[0].asString();
    return "en-US";
}

namespace {

/// CLDR's language for the rules: the primary subtag, and pt-PT apart from pt (Brazil).
std::string languageKey(const std::string& locale) {
    std::vector<std::string> parts;
    std::string cur;
    for (char c : locale) {
        if (c == '-' || c == '_') { parts.push_back(cur); cur.clear(); }
        else cur.push_back(char(std::tolower((unsigned char)c)));
    }
    parts.push_back(cur);
    if (parts.empty() || parts[0].empty()) return "en";
    if (parts[0] == "pt") {
        for (size_t k = 1; k < parts.size(); k++) if (parts[k].size() == 2) return parts[k] == "br" ? "pt" : "pt-pt";
    }
    return parts[0];
}

bool in(const std::string& key, std::initializer_list<const char*> list) {
    for (const char* k : list) if (key == k) return true;
    return false;
}

} // namespace

// ───────────────────────── numbers ─────────────────────────

/// Separators by language (V8's for 1234.5 and 12345.5): `minGrouping` 2 leaves four-digit integers ungrouped.
struct Symbols { const char* group; const char* decimal; int minGrouping; bool percentSpace; bool percentFirst; };

static Symbols symbolsFor(const std::string& locale) {
    std::string lang = languageKey(locale);
    bool spaced = in(lang, {"ru", "be", "cs", "sk", "sv", "nb", "no", "nn", "fi", "fr", "lt", "da", "de", "es", "ro", "hr", "sl", "ca"});
    if (lang == "fr") return {NNBSP, ",", 1, true, false};
    if (in(lang, {"ru", "uk", "kk", "uz", "cs", "sk", "sv", "nb", "no", "nn", "fi", "lt"})) return {NBSP, ",", 1, spaced, false};
    if (in(lang, {"be", "pl", "pt-pt", "bg", "hu", "lv", "et", "ka", "hy"})) return {NBSP, ",", 2, spaced, false};
    if (in(lang, {"da", "de", "nl", "pt", "tr", "id", "ro", "el", "vi", "sr", "hr", "ca", "az"})) return {".", ",", 1, spaced, lang == "tr"};
    if (in(lang, {"es", "it", "sl"})) return {".", ",", 2, spaced, false};
    return {",", ".", 1, false, false};
}


/// The shortest decimal that reads back as `a` (what V8 rounds from), written out without an exponent.
static std::string plainDecimal(double a) {
    std::string s = Json::number(a);
    size_t e = s.find_first_of("eE");
    if (e == std::string::npos) return s;
    std::string mant = s.substr(0, e);
    int exp = std::atoi(s.c_str() + e + 1);
    size_t dot = mant.find('.');
    std::string digits = dot == std::string::npos ? mant : mant.substr(0, dot) + mant.substr(dot + 1);
    int point = int(dot == std::string::npos ? mant.size() : dot) + exp;
    if (point <= 0) return "0." + std::string(size_t(-point), '0') + digits;
    if (point >= int(digits.size())) return digits + std::string(size_t(point) - digits.size(), '0');
    return digits.substr(0, size_t(point)) + "." + digits.substr(size_t(point));
}

/// Adds one to the last digit of `digits`; true when it carried out of the first ("99" → "100").
static bool increment(std::string& digits) {
    int i = int(digits.size()) - 1;
    while (i >= 0 && digits[size_t(i)] == '9') digits[size_t(i--)] = '0';
    if (i < 0) { digits.insert(digits.begin(), '1'); return true; }
    digits[size_t(i)]++;
    return false;
}

struct Digits { std::string integer, fraction; };

static Digits split(const std::string& s) {
    size_t dot = s.find('.');
    return {dot == std::string::npos ? s : s.substr(0, dot), dot == std::string::npos ? "" : s.substr(dot + 1)};
}

/// `s` rounded half away from zero (ECMA-402's halfExpand) to `frac` fraction digits, then trimmed of
/// trailing zeros down to `minFrac`.
static Digits roundFraction(const std::string& s, int minFrac, int frac) {
    Digits d = split(s);
    bool up = int(d.fraction.size()) > frac && d.fraction[size_t(frac)] >= '5';
    d.fraction.resize(size_t(frac), '0');
    std::string all = d.integer + d.fraction;
    if (up) increment(all);
    Digits out{all.substr(0, all.size() - size_t(frac)), all.substr(all.size() - size_t(frac))};
    while (int(out.fraction.size()) > minFrac && out.fraction.back() == '0') out.fraction.pop_back();
    return out;
}

/// `s` rounded half away from zero to `maxSig` significant digits, padded to `minSig`.
static Digits roundSignificant(const std::string& s, int minSig, int maxSig) {
    Digits d = split(s);
    std::string all = d.integer + d.fraction;
    int point = int(d.integer.size());
    size_t first = all.find_first_not_of('0');
    if (first == std::string::npos) return {"0", std::string(size_t(std::max(minSig - 1, 0)), '0')};
    size_t keep = first + size_t(maxSig);
    if (keep < all.size()) {
        bool up = all[keep] >= '5';
        all.resize(keep);
        if (up && increment(all)) point++;
    }
    if (int(all.size()) < point) all.append(size_t(point) - all.size(), '0');
    size_t lead = all.find_first_not_of('0');
    size_t significant = lead == std::string::npos ? 0 : all.size() - lead;
    if (significant < size_t(minSig)) all.append(size_t(minSig) - significant, '0');
    Digits out{all.substr(0, size_t(point)), all.substr(size_t(point))};
    // Trailing fraction zeros go while the minimum stays shown (1.10 with maxSig 3 is 1.1, 1 with minSig 3 is 1.00).
    auto shown = [&]() { std::string x = out.integer + out.fraction; size_t l = x.find_first_not_of('0'); return l == std::string::npos ? 0 : x.size() - l; };
    while (!out.fraction.empty() && out.fraction.back() == '0' && shown() > size_t(minSig)) out.fraction.pop_back();
    out.integer.erase(0, std::min(out.integer.find_first_not_of('0'), out.integer.size()));
    if (out.integer.empty()) out.integer = "0";
    return out;
}

/// ECMA-402's fraction-digit defaults and their interplay (currency 2, percent 0, decimal 0–3).
static void fractionDigits(const Json& opts, int& minFrac, int& maxFrac) {
    const std::string& style = opts["style"].asString();
    std::string code = opts["currency"].asString();
    for (auto& c : code) c = char(std::toupper((unsigned char)c));
    int defMin = 0, defMax = 3;
    if (style == "currency") defMin = defMax = (code == "JPY" || code == "KRW") ? 0 : 2;
    else if (style == "percent") defMax = 0;
    bool hasMin = opts["minimumFractionDigits"].isNumber(), hasMax = opts["maximumFractionDigits"].isNumber();
    minFrac = std::clamp(hasMin ? opts["minimumFractionDigits"].asInt() : defMin, 0, 100);
    maxFrac = std::clamp(hasMax ? opts["maximumFractionDigits"].asInt() : std::max(defMax, minFrac), 0, 100);
    if (hasMax && !hasMin) minFrac = std::min(defMin, maxFrac);
    minFrac = std::min(minFrac, maxFrac);
}

static std::string currencyName(const std::string& code, bool one) {
    if (code == "USD") return one ? "US dollar" : "US dollars";
    if (code == "EUR") return one ? "euro" : "euros";
    if (code == "GBP") return one ? "British pound" : "British pounds";
    if (code == "JPY") return "Japanese yen";
    return code;
}

static std::string currencySymbol(const std::string& code) {
    if (code == "USD") return "$";
    if (code == "EUR") return "\xE2\x82\xAC";
    if (code == "GBP") return "\xC2\xA3";
    if (code == "JPY") return "\xC2\xA5";
    return code + NBSP;
}

static Parts numberParts(double value, const Json& opts, const std::string& locale = "en-US") {
    if (std::isnan(value)) return {{"nan", "NaN"}};
    const std::string& style = opts["style"].asString();
    bool negative = value < 0 || (value == 0 && std::signbit(value));
    Digits d;
    if (std::isinf(value)) {
        d.integer = "\xE2\x88\x9E";
    } else {
        std::string s = plainDecimal(std::fabs(value));
        if (style == "percent") {
            // ×100 on the decimal digits: 0.256 is 25.6, not 25.600000000000001.
            Digits p = split(s);
            p.fraction.append(2, '0');
            s = p.integer + p.fraction.substr(0, 2) + "." + p.fraction.substr(2);
            s.erase(0, std::min(s.find_first_not_of('0'), s.find('.') - 1));
        }
        if (opts["minimumSignificantDigits"].isNumber() || opts["maximumSignificantDigits"].isNumber()) {
            int minSig = std::clamp(opts["minimumSignificantDigits"].asInt(1), 1, 21);
            int maxSig = std::clamp(opts["maximumSignificantDigits"].asInt(21), minSig, 21);
            d = roundSignificant(s, minSig, maxSig);
        } else {
            int minFrac, maxFrac;
            fractionDigits(opts, minFrac, maxFrac);
            d = roundFraction(s, minFrac, maxFrac);
        }
        int minInt = std::clamp(opts["minimumIntegerDigits"].asInt(1), 1, 21);
        if (int(d.integer.size()) < minInt) d.integer.insert(0, size_t(minInt) - d.integer.size(), '0');
    }
    bool zero = d.integer.find_first_not_of('0') == std::string::npos && d.fraction.find_first_not_of('0') == std::string::npos;
    std::string sign = opts["signDisplay"].asString();
    if (sign.empty()) sign = "auto";
    std::string signPart;
    if (sign == "auto") signPart = negative ? "-" : "";
    else if (sign == "always") signPart = negative ? "-" : "+";
    else if (sign == "exceptZero") signPart = zero ? "" : negative ? "-" : "+";
    else if (sign == "negative") signPart = negative && !zero ? "-" : "";

    Parts out;
    if (!signPart.empty()) out.emplace_back(signPart == "-" ? "minusSign" : "plusSign", signPart);
    std::string code = opts["currency"].asString();
    for (auto& c : code) c = char(std::toupper((unsigned char)c));
    std::string display = opts["currencyDisplay"].asString();
    bool currency = style == "currency" && !code.empty();
    if (currency && display != "name") {
        if (display == "code") { out.emplace_back("currency", code); out.emplace_back("literal", NBSP); }
        else {
            std::string symbol = currencySymbol(code);
            if (symbol.size() > code.size()) { out.emplace_back("currency", code); out.emplace_back("literal", NBSP); }
            else out.emplace_back("currency", symbol);
        }
    }
    Symbols sym = symbolsFor(locale);
    bool percent = style == "percent";
    if (percent && sym.percentFirst) out.emplace_back("percentSign", "%");
    if (std::isinf(value)) out.emplace_back("infinity", d.integer);
    else {
        int n = int(d.integer.size());
        bool grouping = (!opts["useGrouping"].isBool() || opts["useGrouping"].asBool()) && n >= 3 + sym.minGrouping;
        int head = grouping ? (n % 3 == 0 ? 3 : n % 3) : n;
        out.emplace_back("integer", d.integer.substr(0, size_t(head)));
        for (int i = head; i < n; i += 3) { out.emplace_back("group", sym.group); out.emplace_back("integer", d.integer.substr(size_t(i), 3)); }
        if (!d.fraction.empty()) { out.emplace_back("decimal", sym.decimal); out.emplace_back("fraction", d.fraction); }
    }
    if (percent && !sym.percentFirst) {
        if (sym.percentSpace) out.emplace_back("literal", NBSP);
        out.emplace_back("percentSign", "%");
    }
    if (currency && display == "name") {
        out.emplace_back("literal", " ");
        out.emplace_back("currency", currencyName(code, d.integer == "1" && d.fraction.empty()));
    }
    return out;
}

static std::string formatNumber(double value, const Json& opts) { return joined(numberParts(value, opts)); }

static std::string numberOptions(const Json& req) {
    const Json& opts = req["opts"];
    std::string style = opts["style"].asString().empty() ? "decimal" : opts["style"].asString();
    int minFrac, maxFrac;
    fractionDigits(opts, minFrac, maxFrac);
    Json out = Json::object();
    out.set("locale", localeOf(req)).set("numberingSystem", "latn").set("style", style);
    if (style == "currency" && !opts["currency"].asString().empty()) {
        std::string code = opts["currency"].asString();
        for (auto& c : code) c = char(std::toupper((unsigned char)c));
        out.set("currency", code);
    }
    out.set("minimumIntegerDigits", std::clamp(opts["minimumIntegerDigits"].asInt(1), 1, 21))
        .set("minimumFractionDigits", minFrac).set("maximumFractionDigits", maxFrac);
    if (opts["useGrouping"].isBool() && !opts["useGrouping"].asBool()) out.set("useGrouping", false);
    else out.set("useGrouping", "auto");
    out.set("signDisplay", opts["signDisplay"].asString().empty() ? "auto" : opts["signDisplay"].asString());
    return out.dump();
}

// ───────────────────────── dates (en-US, UTC) ─────────────────────────

static std::string two(int v) { char b[8]; snprintf(b, sizeof b, "%02d", v); return b; }

static Parts dateParts(double epochMs, const Json& rawOpts, const std::string& method) {
    static const char* const months[] = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    static const char* const days[] = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    if (!std::isfinite(epochMs)) return {{"literal", "Invalid Date"}};
    double seconds = std::floor(epochMs / 1000);
    int millis = int(epochMs - seconds * 1000);
    time_t t = time_t(seconds);
    struct tm tmv {};
#ifdef _WIN32
    gmtime_s(&tmv, &t);
#else
    gmtime_r(&t, &tmv);
#endif
    Json opts = rawOpts.isObject() ? rawOpts : Json::object();
    auto str = [&](const char* k) -> std::string { return opts[k].asString(); };
    auto has = [&](const char* k) { return !opts[k].isNull(); };
    // The shim sends explicit fields; a request without any (another caller) gets ECMA-402's defaults.
    if (!has("weekday") && !has("era") && !has("year") && !has("month") && !has("day") && !has("hour") && !has("minute") && !has("second")
        && !has("fractionalSecondDigits") && !has("dateStyle") && !has("timeStyle")) {
        if (method != "toLocaleTimeString") opts.set("year", "numeric").set("month", "numeric").set("day", "numeric");
        if (method == "toLocaleTimeString" || method == "toLocaleString") opts.set("hour", "numeric").set("minute", "numeric").set("second", "numeric");
    }
    std::string dateStyle = str("dateStyle"), timeStyle = str("timeStyle");
    if (!dateStyle.empty() || !timeStyle.empty()) {
        Json f = Json::object();
        if (dateStyle == "full") f.set("weekday", "long");
        if (dateStyle == "full" || dateStyle == "long") f.set("month", "long");
        if (dateStyle == "medium") f.set("month", "short");
        if (dateStyle == "short") f.set("month", "numeric").set("year", "2-digit");
        else if (!dateStyle.empty()) f.set("year", "numeric");
        if (!dateStyle.empty()) f.set("day", "numeric");
        if (!timeStyle.empty()) f.set("hour", "numeric").set("minute", "2-digit");
        if (timeStyle == "full" || timeStyle == "long" || timeStyle == "medium") f.set("second", "2-digit");
        if (timeStyle == "full") f.set("timeZoneName", "long");
        if (timeStyle == "long") f.set("timeZoneName", "short");
        for (const char* k : {"hour12", "hourCycle"}) if (has(k)) f.set(k, opts[k]);
        opts = f;
    }

    Parts out;
    auto field = [&](const char* type, const std::string& text) { out.emplace_back(type, text); };
    auto lit = [&](const std::string& text) {
        if (!out.empty() && out.back().first == "literal") out.back().second += text;
        else out.emplace_back("literal", text);
    };
    std::string month = str("month");
    bool textMonth = month == "long" || month == "short" || month == "narrow";
    int year = tmv.tm_year + 1900;
    auto yearText = [&]() { return str("year") == "2-digit" ? two(year % 100) : std::to_string(year); };
    auto dayText = [&]() { return str("day") == "2-digit" ? two(tmv.tm_mday) : std::to_string(tmv.tm_mday); };
    bool anyDate = has("weekday") || has("era") || has("year") || has("month") || has("day");
    if (has("weekday")) {
        std::string w = days[tmv.tm_wday];
        field("weekday", str("weekday") == "long" ? w : str("weekday") == "narrow" ? w.substr(0, 1) : w.substr(0, 3));
        if (has("year") || has("month") || has("day")) lit(", ");
    }
    if (textMonth) {
        std::string m = months[tmv.tm_mon];
        field("month", month == "long" ? m : month == "narrow" ? m.substr(0, 1) : m.substr(0, 3));
        if (has("day")) { lit(" "); field("day", dayText()); }
        if (has("year")) { lit(has("day") ? ", " : " "); field("year", yearText()); }
    } else {
        bool first = true;
        auto next = [&]() { if (!first) lit("/"); first = false; };
        if (has("month")) { next(); field("month", month == "2-digit" ? two(tmv.tm_mon + 1) : std::to_string(tmv.tm_mon + 1)); }
        if (has("day")) { next(); field("day", dayText()); }
        if (has("year")) { next(); field("year", yearText()); }
    }
    if (has("era")) { lit(" "); field("era", str("era") == "long" ? (year > 0 ? "Anno Domini" : "Before Christ") : str("era") == "narrow" ? (year > 0 ? "A" : "B") : (year > 0 ? "AD" : "BC")); }

    bool anyTime = has("hour") || has("minute") || has("second") || has("fractionalSecondDigits");
    if (anyDate && anyTime) lit(month == "long" ? " at " : ", ");
    std::string cycle = str("hourCycle");
    bool h12 = opts["hour12"].isBool() ? opts["hour12"].asBool() : !(cycle == "h23" || cycle == "h24");
    if (opts["hour12"].isBool() && opts["hour12"].asBool()) cycle = "h12";
    if (has("hour")) {
        int h = tmv.tm_hour;
        std::string text;
        if (h12) { int v = cycle == "h11" ? h % 12 : (h % 12 == 0 ? 12 : h % 12); text = str("hour") == "2-digit" ? two(v) : std::to_string(v); }
        else text = two(cycle == "h24" && h == 0 ? 24 : h);
        field("hour", text);
    }
    if (has("minute")) { if (has("hour")) lit(":"); field("minute", has("hour") || str("minute") == "2-digit" ? two(tmv.tm_min) : std::to_string(tmv.tm_min)); }
    if (has("second")) { if (has("hour") || has("minute")) lit(":"); field("second", has("hour") || has("minute") || str("second") == "2-digit" ? two(tmv.tm_sec) : std::to_string(tmv.tm_sec)); }
    int fraction = opts["fractionalSecondDigits"].asInt(0);
    if (fraction >= 1 && fraction <= 3) {
        if (has("second")) lit(".");
        char ms[8];
        snprintf(ms, sizeof ms, "%03d", millis);
        field("fractionalSecond", std::string(ms, size_t(fraction)));
    }
    if (has("hour") && h12) { lit(NNBSP); field("dayPeriod", tmv.tm_hour < 12 ? "AM" : "PM"); }
    if (has("timeZoneName")) {
        std::string z = str("timeZoneName");
        lit(" ");
        field("timeZoneName", z == "long" || z == "longGeneric" ? "Coordinated Universal Time" : z == "shortOffset" || z == "longOffset" ? "GMT" : "UTC");
    }
    return out;
}

static std::string dateOptions(const Json& req) {
    const Json& opts = req["opts"];
    Json out = Json::object();
    out.set("locale", localeOf(req)).set("calendar", "gregory").set("numberingSystem", "latn")
        .set("timeZone", opts["timeZone"].asString().empty() ? "UTC" : opts["timeZone"].asString());
    for (const char* k : {"hourCycle", "hour12", "weekday", "era", "year", "month", "day", "hour", "minute", "second", "fractionalSecondDigits", "timeZoneName", "dateStyle", "timeStyle"}) {
        const Json& v = opts[k];
        if (v.isString() || v.isNumber() || v.isBool()) out.set(k, v);
    }
    return out.dump();
}

// ───────────────────────── plural rules (CLDR, as PluralRules.swift) ─────────────────────────

namespace {

struct Operands {
    double n;
    long long i, v, f, t;
    bool integral() const { return t == 0; }
};

/// The number as `Intl.NumberFormat` would show it (default 0–3 fraction digits), read as CLDR operands.
Operands operands(double value, const Json& opts) {
    int minFrac = std::clamp(opts["minimumFractionDigits"].asInt(0), 0, 15);
    int maxFrac = std::clamp(opts["maximumFractionDigits"].isNumber() ? opts["maximumFractionDigits"].asInt() : std::max(minFrac, 3), minFrac, 15);
    Digits d = roundFraction(plainDecimal(std::fabs(value)), minFrac, maxFrac);
    std::string significant = d.fraction;
    while (!significant.empty() && significant.back() == '0') significant.pop_back();
    std::string integer = d.integer.size() > 18 ? d.integer.substr(d.integer.size() - 18) : d.integer;
    return {std::atof((d.integer + "." + (d.fraction.empty() ? "0" : d.fraction)).c_str()), std::atoll(integer.c_str()), (long long)d.fraction.size(),
            d.fraction.empty() ? 0 : std::atoll(d.fraction.c_str()), significant.empty() ? 0 : std::atoll(significant.c_str())};
}

bool between(long long x, long long lo, long long hi) { return x >= lo && x <= hi; }

const char* cardinal(const std::string& lang, const Operands& o) {
    long long i = o.i, v = o.v, f = o.f, t = o.t;
    double n = o.n;
    bool n1 = n == 1;
    bool millions = i != 0 && i % 1000000 == 0 && v == 0;
    if (in(lang, {"id", "in", "ja", "ko", "ms", "th", "vi", "zh", "lo", "km", "my", "jv", "yue", "su", "bo", "dz", "ig", "kea", "sah", "ses", "sg", "to", "wo", "yo", "ii", "nqo", "osa", "kde"}))
        return "other";
    if (in(lang, {"af", "an", "asa", "az", "bal", "bem", "bez", "bg", "brx", "ce", "cgg", "chr", "ckb", "dv", "ee", "el", "eo", "eu", "fo", "fur", "gsw", "ha", "haw", "hu",
                  "jgo", "jmc", "ka", "kaj", "kcg", "kk", "kkj", "kl", "ks", "ksb", "ku", "ky", "lb", "lg", "mas", "mgo", "ml", "mn", "mr", "nah", "nb", "nd", "ne", "nn",
                  "nnh", "no", "nr", "ny", "nyn", "om", "or", "os", "pap", "ps", "rm", "rof", "rwk", "saq", "sd", "sdh", "seh", "sn", "so", "sq", "ss", "ssy", "st", "syr",
                  "ta", "te", "teo", "tig", "tk", "tn", "tr", "ts", "ug", "uz", "ve", "vo", "vun", "wae", "xh", "xog"}))
        return n1 ? "one" : "other";
    if (lang == "es") return n1 ? "one" : millions ? "many" : "other";
    if (in(lang, {"ca", "it", "pt-pt"})) return i == 1 && v == 0 ? "one" : millions ? "many" : "other";
    if (in(lang, {"fr", "pt"})) return i == 0 || i == 1 ? "one" : millions ? "many" : "other";
    if (in(lang, {"ff", "hy", "kab"})) return i == 0 || i == 1 ? "one" : "other";
    if (in(lang, {"am", "as", "bn", "doi", "fa", "gu", "hi", "kn", "pcm", "zu"})) return i == 0 || n1 ? "one" : "other";
    if (in(lang, {"ak", "bho", "guw", "ln", "mg", "nso", "pa", "ti", "wa"})) return n == 0 || n == 1 ? "one" : "other";
    if (lang == "da") return n1 || (t != 0 && (i == 0 || i == 1)) ? "one" : "other";
    if (lang == "is") return (t == 0 && i % 10 == 1 && i % 100 != 11) || (t % 10 == 1 && t % 100 != 11) ? "one" : "other";
    if (lang == "mk") return (v == 0 && i % 10 == 1 && i % 100 != 11) || (f % 10 == 1 && f % 100 != 11) ? "one" : "other";
    if (lang == "si") return n == 0 || n == 1 || (i == 0 && f == 1) ? "one" : "other";
    if (in(lang, {"fil", "tl"})) {
        auto bad = [](long long x) { return x == 4 || x == 6 || x == 9; };
        return (v == 0 && between(i, 1, 3)) || (v == 0 && !bad(i % 10)) || (v != 0 && !bad(f % 10)) ? "one" : "other";
    }
    if (in(lang, {"ru", "uk"})) {
        if (v != 0) return "other";
        if (i % 10 == 1 && i % 100 != 11) return "one";
        if (between(i % 10, 2, 4) && !between(i % 100, 12, 14)) return "few";
        return "many";
    }
    if (lang == "be") {
        if (!o.integral()) return "other";
        if (i % 10 == 1 && i % 100 != 11) return "one";
        if (between(i % 10, 2, 4) && !between(i % 100, 12, 14)) return "few";
        return "many";
    }
    if (lang == "pl") {
        if (v != 0) return "other";
        if (i == 1) return "one";
        if (between(i % 10, 2, 4) && !between(i % 100, 12, 14)) return "few";
        return "many";
    }
    if (in(lang, {"cs", "sk"})) {
        if (i == 1 && v == 0) return "one";
        if (between(i, 2, 4) && v == 0) return "few";
        return v != 0 ? "many" : "other";
    }
    if (in(lang, {"hr", "sr", "bs", "sh"})) {
        if ((v == 0 && i % 10 == 1 && i % 100 != 11) || (f % 10 == 1 && f % 100 != 11)) return "one";
        if ((v == 0 && between(i % 10, 2, 4) && !between(i % 100, 12, 14)) || (between(f % 10, 2, 4) && !between(f % 100, 12, 14))) return "few";
        return "other";
    }
    if (in(lang, {"ro", "mo"})) {
        if (i == 1 && v == 0) return "one";
        return v != 0 || n == 0 || (!n1 && o.integral() && between(i % 100, 1, 19)) ? "few" : "other";
    }
    if (lang == "lt") {
        if (o.integral() && i % 10 == 1 && !between(i % 100, 11, 19)) return "one";
        if (o.integral() && between(i % 10, 2, 9) && !between(i % 100, 11, 19)) return "few";
        return f != 0 ? "many" : "other";
    }
    if (lang == "lv") {
        if ((o.integral() && (i % 10 == 0 || between(i % 100, 11, 19))) || (v == 2 && between(f % 100, 11, 19))) return "zero";
        if ((o.integral() && i % 10 == 1 && i % 100 != 11) || (v == 2 && f % 10 == 1 && f % 100 != 11) || (v != 2 && f % 10 == 1)) return "one";
        return "other";
    }
    if (in(lang, {"ar", "ars"})) {
        if (!o.integral()) return "other";
        if (n == 0) return "zero";
        if (n == 1) return "one";
        if (n == 2) return "two";
        if (between(i % 100, 3, 10)) return "few";
        if (between(i % 100, 11, 99)) return "many";
        return "other";
    }
    if (in(lang, {"he", "iw"})) {
        if ((i == 1 && v == 0) || (i == 0 && v != 0)) return "one";
        if (i == 2 && v == 0) return "two";
        return "other";
    }
    if (lang == "sl") {
        if (v == 0 && i % 100 == 1) return "one";
        if (v == 0 && i % 100 == 2) return "two";
        if ((v == 0 && between(i % 100, 3, 4)) || v != 0) return "few";
        return "other";
    }
    return i == 1 && v == 0 ? "one" : "other";
}

const char* ordinal(const std::string& lang, const Operands& o) {
    long long n = o.i;
    auto any = [](long long x, std::initializer_list<long long> list) { return std::find(list.begin(), list.end(), x) != list.end(); };
    if (lang == "ka") {
        if (n == 1) return "one";
        return n == 0 || between(n % 100, 2, 20) || any(n % 100, {40, 60, 80}) ? "many" : "other";
    }
    if (lang == "az") {
        if (any(n % 10, {1, 2, 5, 7, 8}) || any(n % 100, {20, 50, 70, 80})) return "one";
        if (any(n % 10, {3, 4}) || any(n % 1000, {100, 200, 300, 400, 500, 600, 700, 800, 900})) return "few";
        return n == 0 || n % 10 == 6 || any(n % 100, {40, 60, 90}) ? "many" : "other";
    }
    if (lang == "mk") {
        if (n % 10 == 1 && n % 100 != 11) return "one";
        if (n % 10 == 2 && n % 100 != 12) return "two";
        return any(n % 10, {7, 8}) && !any(n % 100, {17, 18}) ? "many" : "other";
    }
    if (!o.integral()) return "other";
    if (lang == "en") {
        if (n % 10 == 1 && n % 100 != 11) return "one";
        if (n % 10 == 2 && n % 100 != 12) return "two";
        if (n % 10 == 3 && n % 100 != 13) return "few";
        return "other";
    }
    if (in(lang, {"fr", "ro", "mo", "ms", "hy", "fil", "tl", "vi", "ga", "lo"})) return n == 1 ? "one" : "other";
    if (lang == "ca") return n == 1 || n == 3 ? "one" : n == 2 ? "two" : n == 4 ? "few" : "other";
    if (lang == "it") return any(n, {8, 11, 80, 800}) ? "many" : "other";
    if (lang == "sv") return any(n % 10, {1, 2}) && !any(n % 100, {11, 12}) ? "one" : "other";
    if (lang == "hu") return n == 1 || n == 5 ? "one" : "other";
    if (lang == "kk") return n % 10 == 6 || n % 10 == 9 || (n % 10 == 0 && o.n != 0) ? "many" : "other";
    if (lang == "uk") return n % 10 == 3 && n % 100 != 13 ? "few" : "other";
    if (lang == "be") return any(n % 10, {2, 3}) && !any(n % 100, {12, 13}) ? "few" : "other";
    if (lang == "sq") return n == 1 ? "one" : n % 10 == 4 && n % 100 != 14 ? "many" : "other";
    if (lang == "tk") return any(n % 10, {6, 9}) || n == 10 ? "few" : "other";
    if (in(lang, {"hi", "gu"})) return n == 1 ? "one" : n == 2 || n == 3 ? "two" : n == 4 ? "few" : n == 6 ? "many" : "other";
    if (in(lang, {"bn", "as"})) return any(n, {1, 5, 7, 8, 9, 10}) ? "one" : n == 2 || n == 3 ? "two" : n == 4 ? "few" : n == 6 ? "many" : "other";
    if (lang == "mr") return n == 1 ? "one" : n == 2 || n == 3 ? "two" : n == 4 ? "few" : "other";
    if (lang == "ne") return between(n, 1, 4) ? "one" : "other";
    return "other";
}

} // namespace

static std::string plural(const Json& req) {
    double value = req["value"].asNumber();
    if (!std::isfinite(value)) return "other";
    const Json& opts = req["opts"];
    Operands o = operands(value, opts);
    std::string lang = languageKey(localeOf(req));
    return opts["type"].asString() == "ordinal" ? ordinal(lang, o) : cardinal(lang, o);
}

// ───────────────────────── relative time, lists (English) ─────────────────────────

static std::string relative(const Json& req) {
    const Json& opts = req["opts"];
    double value = req["value"].asNumber();
    std::string unit = req["unit"].asString();
    if (!unit.empty() && unit.back() == 's') unit.pop_back();
    std::string style = opts["style"].asString();
    bool narrow = style == "narrow", shortStyle = style == "short";
    if (opts["numeric"].asString() == "auto" && value == std::floor(value) && std::fabs(value) <= 1) {
        int v = int(value);
        if (unit == "day") return v < 0 ? "yesterday" : v > 0 ? "tomorrow" : "today";
        if (v == 0 && unit == "second") return "now";
        if (v == 0 && (unit == "minute" || unit == "hour")) return "this " + unit;
        if (unit == "week" || unit == "month" || unit == "quarter" || unit == "year") {
            std::string word = narrow || shortStyle ? (unit == "week" ? "wk." : unit == "month" ? "mo." : unit == "quarter" ? "qtr." : "yr.") : unit;
            return std::string(v < 0 ? "last " : v > 0 ? "next " : "this ") + word;
        }
    }
    std::string count = formatNumber(std::fabs(value), Json::object());
    bool one = count == "1";
    std::string amount;
    if (narrow) {
        const char* suffix = unit == "second" ? "s" : unit == "minute" ? "m" : unit == "hour" ? "h" : unit == "day" ? "d" : unit == "week" ? "w" : unit == "month" ? "mo" : unit == "quarter" ? "q" : "y";
        amount = count + suffix;
    } else if (shortStyle) {
        std::string word = unit == "second" ? "sec." : unit == "minute" ? "min." : unit == "hour" ? "hr." : unit == "day" ? (one ? "day" : "days")
                         : unit == "week" ? "wk." : unit == "month" ? "mo." : unit == "quarter" ? (one ? "qtr." : "qtrs.") : "yr.";
        amount = count + " " + word;
    } else {
        amount = count + " " + unit + (one ? "" : "s");
    }
    return value < 0 || (value == 0 && std::signbit(value)) ? amount + " ago" : "in " + amount;
}

static std::string list(const Json& req) {
    const Json& opts = req["opts"];
    std::string type = opts["type"].asString(), style = opts["style"].asString();
    const auto& items = req["value"].arr();
    std::string last;  // the separator before the last item
    if (type == "unit") last = style == "narrow" ? " " : ", ";
    else if (type == "disjunction") last = items.size() == 2 ? " or " : ", or ";
    else if (style == "narrow") last = ", ";
    else if (style == "short") last = items.size() == 2 ? " & " : ", & ";
    else last = items.size() == 2 ? " and " : ", and ";
    std::string middle = type == "unit" && style == "narrow" ? " " : ", ";
    std::string out;
    for (size_t i = 0; i < items.size(); i++) {
        if (i > 0) out += i == items.size() - 1 ? last : middle;
        out += items[i].asString();
    }
    return out;
}

// ───────────────────────── collation ─────────────────────────

namespace {

/// One collation element: primary (class, then value), the accent and whether it is upper case.
struct Element { int klass; long long primary; int accent; int upper; };

std::vector<char32_t> codePoints(const std::string& s) {
    std::vector<char32_t> out;
    for (size_t i = 0; i < s.size();) {
        unsigned char c = (unsigned char)s[i];
        int n = c < 0x80 ? 1 : (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : (c >> 3) == 30 ? 4 : 1;
        char32_t cp = n == 1 ? c : n == 2 ? (c & 0x1F) : n == 3 ? (c & 0x0F) : (c & 0x07);
        for (int k = 1; k < n && i + size_t(k) < s.size(); k++) cp = (cp << 6) | ((unsigned char)s[i + size_t(k)] & 0x3F);
        out.push_back(cp);
        i += size_t(n);
    }
    return out;
}

/// Latin-1 letters as base letter + accent (1 grave, 2 acute, 3 circumflex, 4 tilde, 5 diaeresis, 6 ring, 7 cedilla, 8 stroke).
bool latin1(char32_t cp, char& base, int& accent) {
    static const char* const map = "AAAAAAACEEEEIIIIDNOOOOO*OUUUUY";
    static const int accents[] = {1, 2, 3, 4, 5, 6, 0, 7, 1, 2, 3, 5, 1, 2, 3, 5, 9, 4, 1, 2, 3, 4, 5, 0, 8, 1, 2, 3, 5, 2};
    char32_t upper = cp >= 0xE0 && cp <= 0xFE ? cp - 0x20 : cp;
    if (upper < 0xC0 || upper > 0xDD || upper == 0xD7 || upper == 0xC6) return false;
    base = map[upper - 0xC0];
    accent = accents[upper - 0xC0];
    return true;
}

std::vector<Element> elements(const std::string& s, bool numeric) {
    static const std::string punctuation = " \t\n\r_-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$";
    std::vector<Element> out;
    auto cps = codePoints(s);
    for (size_t i = 0; i < cps.size(); i++) {
        char32_t cp = cps[i];
        if (cp < 0x80 && std::isdigit(int(cp))) {
            if (numeric) {
                // A run of digits is one element ordered by its value: leading zeros dropped, longer is larger.
                size_t j = i;
                std::string run;
                while (j < cps.size() && cps[j] < 0x80 && std::isdigit(int(cps[j]))) run.push_back(char(cps[j++]));
                run.erase(0, std::min(run.find_first_not_of('0'), run.size() - 1));
                long long key = (long long)run.size() << 40;
                for (size_t k = 0; k < std::min<size_t>(run.size(), 10); k++) key += (long long)(run[k] - '0') << (36 - 4 * k);
                out.push_back({1, key, 0, 0});
                i = j - 1;
            } else {
                out.push_back({1, cp - '0', 0, 0});
            }
            continue;
        }
        if (cp < 0x80 && std::isalpha(int(cp))) { out.push_back({2, std::tolower(int(cp)) - 'a', 0, std::isupper(int(cp)) ? 1 : 0}); continue; }
        char base; int accent;
        if (latin1(cp, base, accent)) {
            if (base == '*') { out.push_back({3, cp, 0, 0}); continue; }
            out.push_back({2, std::tolower(base) - 'a', accent, cp < 0xE0 ? 1 : 0});
            continue;
        }
        if (cp == 0xDF) { out.push_back({2, 's' - 'a', 0, 0}); out.push_back({2, 's' - 'a', 0, 0}); continue; }  // ß as ss
        if (cp == 0xC6 || cp == 0xE6) { int up = cp == 0xC6; out.push_back({2, 0, 0, up}); out.push_back({2, 'e' - 'a', 0, up}); continue; }  // æ as ae
        size_t p = cp < 0x80 ? punctuation.find(char(cp)) : std::string::npos;
        if (p != std::string::npos) { out.push_back({0, (long long)p, 0, 0}); continue; }
        out.push_back({cp < 0xC0 ? 0 : 3, (long long)cp + 0x100, 0, 0});
    }
    return out;
}

} // namespace

static int compare(const Json& req) {
    const Json& opts = req["opts"];
    std::string sensitivity = opts["sensitivity"].asString();
    bool numeric = opts["numeric"].asBool();
    auto a = elements(req["a"].asString(), numeric), b = elements(req["b"].asString(), numeric);
    auto level = [&](auto key) {
        for (size_t i = 0; i < std::min(a.size(), b.size()); i++) {
            auto x = key(a[i]), y = key(b[i]);
            if (x != y) return x < y ? -1 : 1;
        }
        return a.size() == b.size() ? 0 : a.size() < b.size() ? -1 : 1;
    };
    int c = level([](const Element& e) { return std::make_pair(e.klass, e.primary); });
    if (c != 0 || sensitivity == "base") return c;
    if (sensitivity != "case") {
        c = level([](const Element& e) { return e.accent; });
        if (c != 0 || sensitivity == "accent") return c;
    }
    return level([](const Element& e) { return e.upper; });
}

std::string BasicIntl::format(const Json& req) {
    const std::string& kind = req["kind"].asString();
    const Json& opts = req["opts"];
    if (kind == "number") return joined(numberParts(req["value"].asNumber(), opts, localeOf(req)));
    if (kind == "numberParts") return partsJson(numberParts(req["value"].asNumber(), opts, localeOf(req)));
    if (kind == "numberOptions") return numberOptions(req);
    if (kind == "date" || kind == "dateMethod") return joined(dateParts(req["value"].asNumber(), opts, req["method"].asString()));
    if (kind == "dateParts") return partsJson(dateParts(req["value"].asNumber(), opts, req["method"].asString()));
    if (kind == "dateOptions") return dateOptions(req);
    if (kind == "plural") return plural(req);
    if (kind == "relative") return relative(req);
    if (kind == "list") return list(req);
    if (kind == "compare") return std::to_string(compare(req));
    if (kind == "upper" || kind == "lower") {
        std::string s = req["value"].asString();
        for (auto& ch : s) ch = char(kind == "upper" ? std::toupper((unsigned char)ch) : std::tolower((unsigned char)ch));
        return s;
    }
    return "";
}

} // namespace twidget
