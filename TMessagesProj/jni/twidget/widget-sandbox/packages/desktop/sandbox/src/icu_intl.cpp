#include "twidget/icu_intl.h"

#include "twidget/json.h"

#include <icu.h>
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <memory>
#include <type_traits>
#include <vector>

#pragma comment(lib, "icu.lib")

// Linked: only the C API that Windows 10 1903's icu.dll (ICU 64) exports. icu.h gates the newer functions by
// NTDDI_VERSION, which a build for the latest SDK does not stop, and one imported function the system's
// icu.dll lacks keeps the whole module from loading: those are looked up by name (`newer()`) instead.

namespace twidget {
namespace {

using U16 = std::basic_string<UChar>;

/// ICU functions past Windows 10 1903's, from icu.dll when it has them; null when it does not.
struct Newer {
    decltype(&unumf_openForSkeletonAndLocale) numberOpen = nullptr;
    decltype(&unumf_close) numberClose = nullptr;
    decltype(&unumf_openResult) resultOpen = nullptr;
    decltype(&unumf_closeResult) resultClose = nullptr;
    decltype(&unumf_formatDouble) formatDouble = nullptr;
    decltype(&uplrules_selectFormatted) pluralSelect = nullptr;
    decltype(&ulistfmt_openForType) listOpen = nullptr;

    bool numbers() const { return numberOpen && numberClose && resultOpen && resultClose && formatDouble; }
};

const Newer& newer() {
    static const Newer functions = [] {
        Newer n;
        HMODULE icu = GetModuleHandleW(L"icu.dll");
        if (!icu) return n;
        auto get = [&](auto& fn, const char* name) {
            fn = reinterpret_cast<std::remove_reference_t<decltype(fn)>>(reinterpret_cast<void*>(GetProcAddress(icu, name)));
        };
        get(n.numberOpen, "unumf_openForSkeletonAndLocale");
        get(n.numberClose, "unumf_close");
        get(n.resultOpen, "unumf_openResult");
        get(n.resultClose, "unumf_closeResult");
        get(n.formatDouble, "unumf_formatDouble");
        get(n.pluralSelect, "uplrules_selectFormatted");
        get(n.listOpen, "ulistfmt_openForType");
        return n;
    }();
    return functions;
}

U16 u16(const std::string& s) {
    UErrorCode e = U_ZERO_ERROR;
    int32_t n = 0;
    u_strFromUTF8(nullptr, 0, &n, s.data(), int32_t(s.size()), &e);
    U16 out(size_t(n), u'\0');
    e = U_ZERO_ERROR;
    u_strFromUTF8(out.data(), n, nullptr, s.data(), int32_t(s.size()), &e);
    return U_FAILURE(e) ? U16() : out;
}

std::string utf8(const UChar* s, int32_t length) {
    UErrorCode e = U_ZERO_ERROR;
    int32_t n = 0;
    u_strToUTF8(nullptr, 0, &n, s, length, &e);
    std::string out(size_t(n), '\0');
    e = U_ZERO_ERROR;
    u_strToUTF8(out.data(), n, nullptr, s, length, &e);
    return U_FAILURE(e) ? std::string() : out;
}

/// Calls an ICU function that writes UTF-16 into (buffer, capacity), growing the buffer when it asks.
template <class Call>
bool written(U16& out, Call call) {
    UErrorCode e = U_ZERO_ERROR;
    out.resize(128);
    int32_t n = call(out.data(), int32_t(out.size()), &e);
    if (e == U_BUFFER_OVERFLOW_ERROR) {
        e = U_ZERO_ERROR;
        out.resize(size_t(n));
        n = call(out.data(), n, &e);
    }
    if (U_FAILURE(e)) return false;
    out.resize(size_t(n));
    return true;
}

template <class T, void (*Close)(T*)>
struct Closer {
    void operator()(T* p) const { if (p) Close(p); }
};
using Number = std::unique_ptr<UNumberFormat, Closer<UNumberFormat, unum_close>>;
using Date = std::unique_ptr<UDateFormat, Closer<UDateFormat, udat_close>>;
using Generator = std::unique_ptr<UDateTimePatternGenerator, Closer<UDateTimePatternGenerator, udatpg_close>>;
using Plurals = std::unique_ptr<UPluralRules, Closer<UPluralRules, uplrules_close>>;
using Relative = std::unique_ptr<URelativeDateTimeFormatter, Closer<URelativeDateTimeFormatter, ureldatefmt_close>>;
using List = std::unique_ptr<UListFormatter, Closer<UListFormatter, ulistfmt_close>>;
using Collator = std::unique_ptr<UCollator, Closer<UCollator, ucol_close>>;
using Fields = std::unique_ptr<UFieldPositionIterator, Closer<UFieldPositionIterator, ufieldpositer_close>>;

/// The ICU locale ID of a BCP 47 tag, or of the first of a list; empty when it is none.
std::string locale_id(const Json& locale) {
    const std::string& tag = locale.isArray() ? locale[size_t(0)].asString() : locale.asString();
    if (tag.empty()) return {};
    char id[ULOC_FULLNAME_CAPACITY] = {};
    int32_t parsed = 0;
    UErrorCode e = U_ZERO_ERROR;
    uloc_forLanguageTag(tag.c_str(), id, int32_t(sizeof id), &parsed, &e);
    return U_SUCCESS(e) && parsed > 0 ? std::string(id) : std::string();
}

std::string language_tag(const std::string& id) {
    char tag[ULOC_FULLNAME_CAPACITY] = {};
    UErrorCode e = U_ZERO_ERROR;
    uloc_toLanguageTag(id.c_str(), tag, int32_t(sizeof tag), false, &e);
    return U_SUCCESS(e) ? std::string(tag) : id;
}

std::string upper(std::string s) {
    for (char& c : s) c = char(std::toupper(static_cast<unsigned char>(c)));
    return s;
}

bool significant(const Json& opts) { return opts["minimumSignificantDigits"].isNumber() || opts["maximumSignificantDigits"].isNumber(); }

Number open_number(const char* locale, const Json& opts) {
    const std::string& style = opts["style"].asString();
    const std::string& display = opts["currencyDisplay"].asString();
    UNumberFormatStyle kind = style == "percent" ? UNUM_PERCENT
                              : style != "currency" ? UNUM_DECIMAL
                              : display == "code"   ? UNUM_CURRENCY_ISO
                              : display == "name"   ? UNUM_CURRENCY_PLURAL
                                                    : UNUM_CURRENCY;
    UErrorCode e = U_ZERO_ERROR;
    Number f(unum_open(kind, nullptr, 0, locale, nullptr, &e));
    if (U_FAILURE(e)) return nullptr;
    if (style == "currency") {
        U16 code = u16(upper(opts["currency"].asString()));
        if (code.size() == 3) unum_setTextAttribute(f.get(), UNUM_CURRENCY_CODE, code.data(), 3, &e);
        if (U_FAILURE(e)) return nullptr;
    }
    // ECMA-402 rounds half away from zero (ICU's half-up), and groups as the locale's data says (CLDR's
    // minimumGroupingDigits: es writes 1234,5); the legacy formatter does neither by default.
    unum_setAttribute(f.get(), UNUM_ROUNDING_MODE, UNUM_ROUND_HALFUP);
    unum_setAttribute(f.get(), UNUM_MINIMUM_GROUPING_DIGITS, UNUM_MINIMUM_GROUPING_DIGITS_AUTO);
    // A minimum past the maximum raises it, as the spec's SetNumberFormatDigitOptions does.
    if (opts["minimumIntegerDigits"].isNumber()) unum_setAttribute(f.get(), UNUM_MIN_INTEGER_DIGITS, opts["minimumIntegerDigits"].asInt());
    if (significant(opts)) {
        unum_setAttribute(f.get(), UNUM_SIGNIFICANT_DIGITS_USED, 1);
        unum_setAttribute(f.get(), UNUM_MIN_SIGNIFICANT_DIGITS, opts["minimumSignificantDigits"].isNumber() ? opts["minimumSignificantDigits"].asInt() : 1);
        unum_setAttribute(f.get(), UNUM_MAX_SIGNIFICANT_DIGITS, opts["maximumSignificantDigits"].isNumber() ? opts["maximumSignificantDigits"].asInt() : 21);
    } else {
        if (opts["minimumFractionDigits"].isNumber()) unum_setAttribute(f.get(), UNUM_MIN_FRACTION_DIGITS, opts["minimumFractionDigits"].asInt());
        if (opts["maximumFractionDigits"].isNumber()) unum_setAttribute(f.get(), UNUM_MAX_FRACTION_DIGITS, opts["maximumFractionDigits"].asInt());
    }
    if (opts["useGrouping"].isBool()) unum_setAttribute(f.get(), UNUM_GROUPING_USED, opts["useGrouping"].asBool());
    return f;
}

U16 format_number(UNumberFormat* f, double value) {
    U16 out;
    written(out, [&](UChar* b, int32_t c, UErrorCode* e) { return unum_formatDouble(f, value, b, c, nullptr, e); });
    return out;
}

/// `signDisplay`: the value to format, and whether a positive one shows a plus (`show_plus` sets the formatter
/// up for it). Whether a value is zero is decided after rounding: -0.0001 at no fraction digits is a zero.
std::pair<double, bool> sign_display(UNumberFormat* f, double value, const std::string& display) {
    if (display.empty() || display == "auto" || std::isnan(value)) return {value, false};
    bool negative = std::signbit(value);
    double magnitude = std::fabs(value);
    if (display == "never") return {magnitude, false};
    bool zero = format_number(f, magnitude) == format_number(f, 0);
    if (display == "always") return {value, !negative};
    if (display == "exceptZero") return zero ? std::pair{0.0, false} : std::pair{value, !negative};
    if (display == "negative") return {negative && !zero ? value : magnitude, false};
    return {value, false};
}

/// A positive number written as the negative one with the locale's plus sign for its minus: `+$5.00` in en-US,
/// the plus before the number and the euro sign after it in de-DE.
void show_plus(UNumberFormat* f) {
    U16 minus, plus;
    written(minus, [&](UChar* b, int32_t c, UErrorCode* e) { return unum_getSymbol(f, UNUM_MINUS_SIGN_SYMBOL, b, c, e); });
    written(plus, [&](UChar* b, int32_t c, UErrorCode* e) { return unum_getSymbol(f, UNUM_PLUS_SIGN_SYMBOL, b, c, e); });
    if (minus.empty() || plus.empty()) return;
    for (auto [from, to] : {std::pair{UNUM_NEGATIVE_PREFIX, UNUM_POSITIVE_PREFIX}, std::pair{UNUM_NEGATIVE_SUFFIX, UNUM_POSITIVE_SUFFIX}}) {
        U16 text;
        if (!written(text, [&](UChar* b, int32_t c, UErrorCode* e) { return unum_getTextAttribute(f, from, b, c, e); })) continue;
        for (size_t at; (at = text.find(minus)) != U16::npos;) text.replace(at, minus.size(), plus);
        UErrorCode e = U_ZERO_ERROR;
        unum_setTextAttribute(f, to, text.data(), int32_t(text.size()), &e);
    }
}

/// ECMA-402 ToDateTimeOptions: no date or time field and no style means numeric year, month and day
/// (DateTimeFormat, toLocaleDateString), hour, minute and second (toLocaleTimeString), or both
/// (toLocaleString).
Json date_defaults(const Json& opts, const std::string& method) {
    std::string required = method == "toLocaleDateString" ? "date" : method == "toLocaleTimeString" ? "time" : "any";
    std::string defaults = method == "toLocaleTimeString" ? "time" : method == "toLocaleString" ? "all" : "date";
    if (opts.find("dateStyle") || opts.find("timeStyle")) return opts;
    auto any = [&](std::initializer_list<const char*> keys) {
        return std::any_of(keys.begin(), keys.end(), [&](const char* k) { return opts.find(k) != nullptr; });
    };
    if (required != "time" && any({"weekday", "year", "month", "day"})) return opts;
    if (required != "date" && any({"dayPeriod", "hour", "minute", "second", "fractionalSecondDigits"})) return opts;
    Json out = opts.isObject() ? opts : Json::object();
    if (defaults != "time") out.set("year", "numeric").set("month", "numeric").set("day", "numeric");
    if (defaults != "date") out.set("hour", "numeric").set("minute", "numeric").set("second", "numeric");
    return out;
}

/// A CLDR skeleton for the fields `opts` asks for; empty when it asks for styles instead.
U16 skeleton_of(const Json& opts) {
    std::string s;
    auto field = [&](const char* key) -> const std::string& { return opts[key].asString(); };
    if (!field("era").empty()) s += field("era") == "long" ? "GGGG" : field("era") == "narrow" ? "GGGGG" : "G";
    if (!field("weekday").empty()) s += field("weekday") == "long" ? "EEEE" : field("weekday") == "narrow" ? "EEEEE" : "EEE";
    if (!field("year").empty()) s += field("year") == "2-digit" ? "yy" : "y";
    if (!field("month").empty()) {
        const std::string& m = field("month");
        s += m == "long" ? "MMMM" : m == "short" ? "MMM" : m == "narrow" ? "MMMMM" : m == "2-digit" ? "MM" : "M";
    }
    if (!field("day").empty()) s += field("day") == "2-digit" ? "dd" : "d";
    if (!field("hour").empty()) {
        // hour12 outranks hourCycle, as in ECMA-402; with neither, the locale's own cycle.
        const std::string& cycle = field("hourCycle");
        char h = opts["hour12"].isBool() ? (opts["hour12"].asBool() ? 'h' : 'H')
                 : cycle == "h23"        ? 'H'
                 : cycle == "h24"        ? 'k'
                 : cycle == "h11"        ? 'K'
                 : cycle == "h12"        ? 'h'
                                         : 'j';
        s += std::string(field("hour") == "2-digit" ? 2 : 1, h);
    }
    if (!field("minute").empty()) s += field("minute") == "2-digit" ? "mm" : "m";
    if (!field("second").empty()) s += field("second") == "2-digit" ? "ss" : "s";
    if (opts["fractionalSecondDigits"].isNumber()) s += std::string(size_t(std::clamp(opts["fractionalSecondDigits"].asInt(), 1, 3)), 'S');
    if (!field("timeZoneName").empty()) s += field("timeZoneName") == "long" ? "zzzz" : "z";
    return u16(s);
}

UDateFormatStyle date_style(const std::string& s) {
    return s == "full" ? UDAT_FULL : s == "long" ? UDAT_LONG : s == "medium" ? UDAT_MEDIUM : s == "short" ? UDAT_SHORT : UDAT_NONE;
}

Date open_date(const char* locale, const Json& raw, const std::string& method) {
    Json opts = date_defaults(raw, method);
    U16 zone = u16(opts["timeZone"].asString());
    const UChar* tz = zone.empty() ? nullptr : zone.data();
    int32_t tz_length = zone.empty() ? 0 : int32_t(zone.size());
    UErrorCode e = U_ZERO_ERROR;
    U16 skeleton = skeleton_of(opts);
    if (skeleton.empty()) {
        Date f(udat_open(date_style(opts["timeStyle"].asString()), date_style(opts["dateStyle"].asString()), locale, tz, tz_length, nullptr, -1, &e));
        if (U_FAILURE(e)) return nullptr;
        return f;
    }
    Generator generator(udatpg_open(locale, &e));
    if (U_FAILURE(e)) return nullptr;
    U16 pattern;
    if (!written(pattern, [&](UChar* out, int32_t capacity, UErrorCode* status) {
            return udatpg_getBestPattern(generator.get(), skeleton.data(), int32_t(skeleton.size()), out, capacity, status);
        }))
        return nullptr;
    Date f(udat_open(UDAT_PATTERN, UDAT_PATTERN, locale, tz, tz_length, pattern.data(), int32_t(pattern.size()), &e));
    if (U_FAILURE(e)) return nullptr;
    return f;
}

/// JS formatToParts: each UTF-16 unit takes the innermost field covering it, and runs of the same
/// type become one part.
std::string parts_json(const U16& text, UFieldPositionIterator* fields, const char* (*name)(int32_t)) {
    std::vector<std::pair<int32_t, const char*>> owner(text.size(), {INT32_MAX, "literal"});
    int32_t begin = 0, end = 0;
    for (int32_t field; (field = ufieldpositer_next(fields, &begin, &end)) >= 0;) {
        const char* type = name(field);
        for (int32_t i = std::max(begin, 0); i < end && i < int32_t(text.size()); i++)
            if (end - begin < owner[size_t(i)].first) owner[size_t(i)] = {end - begin, type};
    }
    Json out = Json::array();
    for (size_t i = 0; i < text.size();) {
        size_t j = i;
        while (j < text.size() && std::string_view(owner[j].second) == owner[i].second) j++;
        out.push(Json::object().set("type", owner[i].second).set("value", utf8(text.data() + i, int32_t(j - i))));
        i = j;
    }
    return out.dump();
}

const char* number_part(int32_t field) {
    switch (field) {
    case UNUM_INTEGER_FIELD: return "integer";
    case UNUM_FRACTION_FIELD: return "fraction";
    case UNUM_DECIMAL_SEPARATOR_FIELD: return "decimal";
    case UNUM_EXPONENT_SYMBOL_FIELD: return "exponentSeparator";
    case UNUM_EXPONENT_SIGN_FIELD: return "exponentMinusSign";
    case UNUM_EXPONENT_FIELD: return "exponentInteger";
    case UNUM_GROUPING_SEPARATOR_FIELD: return "group";
    case UNUM_CURRENCY_FIELD: return "currency";
    case UNUM_PERCENT_FIELD: return "percentSign";
    case UNUM_SIGN_FIELD: return "minusSign";
    default: return "literal";
    }
}

const char* number_part_plus(int32_t field) { return field == UNUM_SIGN_FIELD ? "plusSign" : number_part(field); }

const char* date_part(int32_t field) {
    switch (field) {
    case UDAT_ERA_FIELD: return "era";
    case UDAT_YEAR_FIELD: case UDAT_EXTENDED_YEAR_FIELD: case UDAT_YEAR_WOY_FIELD: return "year";
    case UDAT_MONTH_FIELD: case UDAT_STANDALONE_MONTH_FIELD: return "month";
    case UDAT_DATE_FIELD: return "day";
    case UDAT_DAY_OF_WEEK_FIELD: case UDAT_STANDALONE_DAY_FIELD: case UDAT_DOW_LOCAL_FIELD: return "weekday";
    case UDAT_AM_PM_FIELD: case UDAT_AM_PM_MIDNIGHT_NOON_FIELD: case UDAT_FLEXIBLE_DAY_PERIOD_FIELD: return "dayPeriod";
    case UDAT_HOUR_OF_DAY1_FIELD: case UDAT_HOUR_OF_DAY0_FIELD: case UDAT_HOUR1_FIELD: case UDAT_HOUR0_FIELD: return "hour";
    case UDAT_MINUTE_FIELD: return "minute";
    case UDAT_SECOND_FIELD: return "second";
    case UDAT_FRACTIONAL_SECOND_FIELD: return "fractionalSecond";
    case UDAT_TIMEZONE_FIELD: case UDAT_TIMEZONE_RFC_FIELD: case UDAT_TIMEZONE_GENERIC_FIELD: case UDAT_TIMEZONE_SPECIAL_FIELD:
    case UDAT_TIMEZONE_LOCALIZED_GMT_OFFSET_FIELD: case UDAT_TIMEZONE_ISO_FIELD: case UDAT_TIMEZONE_ISO_LOCAL_FIELD:
        return "timeZoneName";
    default: return "literal";
    }
}

URelativeDateTimeUnit relative_unit(std::string unit) {
    if (unit.size() > 1 && unit.back() == 's') unit.pop_back();
    if (unit == "year") return UDAT_REL_UNIT_YEAR;
    if (unit == "quarter") return UDAT_REL_UNIT_QUARTER;
    if (unit == "month") return UDAT_REL_UNIT_MONTH;
    if (unit == "week") return UDAT_REL_UNIT_WEEK;
    if (unit == "day") return UDAT_REL_UNIT_DAY;
    if (unit == "hour") return UDAT_REL_UNIT_HOUR;
    if (unit == "minute") return UDAT_REL_UNIT_MINUTE;
    return UDAT_REL_UNIT_SECOND;
}

} // namespace

IcuIntl::IcuIntl(std::string fallbackLocale) : fallback_(std::move(fallbackLocale)) {}

std::string IcuIntl::format(const Json& req) {
    const std::string& kind = req["kind"].asString();
    const Json& opts = req["opts"];
    std::string id = locale_id(req["locale"]);
    if (id.empty()) id = locale_id(Json(fallback_));
    if (id.empty()) id = "en_US";
    const char* locale = id.c_str();
    const double value = req["value"].asNumber();
    U16 out;

    if (kind == "number" || kind == "numberParts" || kind == "numberOptions") {
        Number f = open_number(locale, opts);
        if (!f) return basic_.format(req);
        if (kind == "numberOptions") {
            Json o = Json::object();
            o.set("locale", language_tag(id)).set("numberingSystem", "latn");
            o.set("style", opts["style"].asString().empty() ? std::string("decimal") : opts["style"].asString());
            if (opts["style"].asString() == "currency") {
                o.set("currency", upper(opts["currency"].asString()));
                o.set("currencyDisplay", opts["currencyDisplay"].asString().empty() ? std::string("symbol") : opts["currencyDisplay"].asString());
            }
            o.set("minimumIntegerDigits", unum_getAttribute(f.get(), UNUM_MIN_INTEGER_DIGITS));
            if (significant(opts)) {
                o.set("minimumSignificantDigits", unum_getAttribute(f.get(), UNUM_MIN_SIGNIFICANT_DIGITS));
                o.set("maximumSignificantDigits", unum_getAttribute(f.get(), UNUM_MAX_SIGNIFICANT_DIGITS));
            } else {
                o.set("minimumFractionDigits", unum_getAttribute(f.get(), UNUM_MIN_FRACTION_DIGITS));
                o.set("maximumFractionDigits", unum_getAttribute(f.get(), UNUM_MAX_FRACTION_DIGITS));
            }
            o.set("useGrouping", unum_getAttribute(f.get(), UNUM_GROUPING_USED) != 0);
            o.set("signDisplay", opts["signDisplay"].asString().empty() ? std::string("auto") : opts["signDisplay"].asString());
            return o.dump();
        }
        auto [number, plus] = sign_display(f.get(), value, opts["signDisplay"].asString());
        if (plus) show_plus(f.get());
        if (kind == "number") {
            if (!written(out, [&](UChar* b, int32_t c, UErrorCode* e) { return unum_formatDouble(f.get(), number, b, c, nullptr, e); })) return basic_.format(req);
            return utf8(out.data(), int32_t(out.size()));
        }
        UErrorCode e = U_ZERO_ERROR;
        Fields fields(ufieldpositer_open(&e));
        if (U_FAILURE(e)) return basic_.format(req);
        // Formatting again for the fields: a growing buffer would leave the iterator with the first try's.
        if (!written(out, [&](UChar* b, int32_t c, UErrorCode* s) { return unum_formatDouble(f.get(), number, b, c, nullptr, s); })) return basic_.format(req);
        U16 text(out.size() + 1, u'\0');
        int32_t n = unum_formatDoubleForFields(f.get(), number, text.data(), int32_t(text.size()), fields.get(), &e);
        if (U_FAILURE(e)) return basic_.format(req);
        text.resize(size_t(n));
        return parts_json(text, fields.get(), plus ? number_part_plus : number_part);
    }

    if (kind == "date" || kind == "dateMethod" || kind == "dateParts" || kind == "dateOptions") {
        const std::string& method = req["method"].asString();
        Date f = open_date(locale, opts, method);
        if (!f) return basic_.format(req);
        if (kind == "dateOptions") {
            Json o = Json::object();
            const Json asked = date_defaults(opts, method);
            for (const auto& [key, v] : asked.obj())
                if (v.isString() || v.isNumber() || v.isBool()) o.set(key, v);
            U16 zone;
            if (!opts["timeZone"].asString().empty()) zone = u16(opts["timeZone"].asString());
            else written(zone, [](UChar* b, int32_t c, UErrorCode* e) { return ucal_getDefaultTimeZone(b, c, e); });
            o.set("locale", language_tag(id)).set("calendar", "gregory").set("numberingSystem", "latn").set("timeZone", utf8(zone.data(), int32_t(zone.size())));
            return o.dump();
        }
        if (kind != "dateParts") {
            if (!written(out, [&](UChar* b, int32_t c, UErrorCode* e) { return udat_format(f.get(), value, b, c, nullptr, e); })) return basic_.format(req);
            return utf8(out.data(), int32_t(out.size()));
        }
        UErrorCode e = U_ZERO_ERROR;
        Fields fields(ufieldpositer_open(&e));
        if (U_FAILURE(e) || !written(out, [&](UChar* b, int32_t c, UErrorCode* s) { return udat_format(f.get(), value, b, c, nullptr, s); }))
            return basic_.format(req);
        U16 text(out.size() + 1, u'\0');
        int32_t n = udat_formatForFields(f.get(), value, text.data(), int32_t(text.size()), fields.get(), &e);
        if (U_FAILURE(e)) return basic_.format(req);
        text.resize(size_t(n));
        return parts_json(text, fields.get(), date_part);
    }

    if (kind == "plural") {
        UErrorCode e = U_ZERO_ERROR;
        Plurals rules(uplrules_openForType(locale, opts["type"].asString() == "ordinal" ? UPLURAL_TYPE_ORDINAL : UPLURAL_TYPE_CARDINAL, &e));
        if (U_FAILURE(e)) return basic_.format(req);
        // With digit options the rules see the formatted number: 1 at one fraction digit is "1.0", whose v
        // operand is 1, so English says "other". That takes the number formatter newer ICUs have; without it,
        // the bare value.
        const Newer& icu = newer();
        bool digits = significant(opts) || opts["minimumFractionDigits"].isNumber() || opts["maximumFractionDigits"].isNumber();
        if (digits && icu.numbers() && icu.pluralSelect) {
            std::string skeleton = "rounding-mode-half-up ";
            if (significant(opts)) {
                int lo = opts["minimumSignificantDigits"].isNumber() ? opts["minimumSignificantDigits"].asInt() : 1;
                int hi = opts["maximumSignificantDigits"].isNumber() ? opts["maximumSignificantDigits"].asInt() : 21;
                skeleton += std::string(size_t(std::clamp(lo, 1, 21)), '@') + std::string(size_t(std::clamp(hi - lo, 0, 20)), '#');
            } else {
                int lo = opts["minimumFractionDigits"].isNumber() ? opts["minimumFractionDigits"].asInt() : 0;
                int hi = std::max(lo, opts["maximumFractionDigits"].isNumber() ? opts["maximumFractionDigits"].asInt() : std::max(lo, 3));
                skeleton += hi == 0 ? std::string("precision-integer") : "." + std::string(size_t(std::clamp(lo, 0, 20)), '0') + std::string(size_t(std::clamp(hi - lo, 0, 20)), '#');
            }
            U16 sk = u16(skeleton);
            UNumberFormatter* formatter = icu.numberOpen(sk.data(), int32_t(sk.size()), locale, &e);
            UFormattedNumber* result = U_SUCCESS(e) ? icu.resultOpen(&e) : nullptr;
            if (U_SUCCESS(e)) icu.formatDouble(formatter, value, result, &e);
            bool selected = U_SUCCESS(e) && written(out, [&](UChar* b, int32_t c, UErrorCode* s) { return icu.pluralSelect(rules.get(), result, b, c, s); });
            if (result) icu.resultClose(result);
            if (formatter) icu.numberClose(formatter);
            if (selected) return utf8(out.data(), int32_t(out.size()));
            e = U_ZERO_ERROR;
        }
        if (!written(out, [&](UChar* b, int32_t c, UErrorCode* s) { return uplrules_select(rules.get(), value, b, c, s); }))
            return basic_.format(req);
        return utf8(out.data(), int32_t(out.size()));
    }

    if (kind == "relative") {
        const std::string& style = opts["style"].asString();
        UErrorCode e = U_ZERO_ERROR;
        Relative f(ureldatefmt_open(locale, nullptr, style == "short" ? UDAT_STYLE_SHORT : style == "narrow" ? UDAT_STYLE_NARROW : UDAT_STYLE_LONG,
                                    UDISPCTX_CAPITALIZATION_NONE, &e));
        if (U_FAILURE(e)) return basic_.format(req);
        URelativeDateTimeUnit unit = relative_unit(req["unit"].asString());
        bool automatic = opts["numeric"].asString() == "auto";
        if (!written(out, [&](UChar* b, int32_t c, UErrorCode* s) {
                return automatic ? ureldatefmt_format(f.get(), value, unit, b, c, s) : ureldatefmt_formatNumeric(f.get(), value, unit, b, c, s);
            }))
            return basic_.format(req);
        return utf8(out.data(), int32_t(out.size()));
    }

    if (kind == "list") {
        const std::string& type = opts["type"].asString();
        const std::string& style = opts["style"].asString();
        bool plain = (type.empty() || type == "conjunction") && (style.empty() || style == "long");
        // Other types and styles take ulistfmt_openForType (ICU 67); without it, BasicIntl's English.
        auto open_for_type = newer().listOpen;
        if (!plain && !open_for_type) return basic_.format(req);
        std::vector<U16> items;
        for (const Json& item : req["value"].arr()) items.push_back(u16(item.asString()));
        std::vector<const UChar*> strings;
        std::vector<int32_t> lengths;
        for (const U16& item : items) {
            strings.push_back(item.data());
            lengths.push_back(int32_t(item.size()));
        }
        UErrorCode e = U_ZERO_ERROR;
        List f(plain ? ulistfmt_open(locale, &e)
                     : open_for_type(locale, type == "disjunction" ? ULISTFMT_TYPE_OR : type == "unit" ? ULISTFMT_TYPE_UNITS : ULISTFMT_TYPE_AND,
                                     style == "short" ? ULISTFMT_WIDTH_SHORT : style == "narrow" ? ULISTFMT_WIDTH_NARROW : ULISTFMT_WIDTH_WIDE, &e));
        if (U_FAILURE(e) || !written(out, [&](UChar* b, int32_t c, UErrorCode* s) {
                return ulistfmt_format(f.get(), strings.data(), lengths.data(), int32_t(items.size()), b, c, s);
            }))
            return basic_.format(req);
        return utf8(out.data(), int32_t(out.size()));
    }

    if (kind == "compare") {
        UErrorCode e = U_ZERO_ERROR;
        Collator c(ucol_open(locale, &e));
        if (U_FAILURE(e)) return basic_.format(req);
        const std::string& sensitivity = opts["sensitivity"].asString();
        if (sensitivity == "base") ucol_setStrength(c.get(), UCOL_PRIMARY);
        else if (sensitivity == "accent") ucol_setStrength(c.get(), UCOL_SECONDARY);
        // case: letters and their case, not accents, as ECMA-402 maps it onto ICU.
        else if (sensitivity == "case") {
            ucol_setStrength(c.get(), UCOL_PRIMARY);
            ucol_setAttribute(c.get(), UCOL_CASE_LEVEL, UCOL_ON, &e);
        }
        if (opts["numeric"].asBool()) ucol_setAttribute(c.get(), UCOL_NUMERIC_COLLATION, UCOL_ON, &e);
        U16 a = u16(req["a"].asString()), b = u16(req["b"].asString());
        UCollationResult r = ucol_strcoll(c.get(), a.data(), int32_t(a.size()), b.data(), int32_t(b.size()));
        return r == UCOL_LESS ? "-1" : r == UCOL_GREATER ? "1" : "0";
    }

    if (kind == "upper" || kind == "lower") {
        U16 text = u16(req["value"].asString());
        if (!written(out, [&](UChar* b, int32_t c, UErrorCode* e) {
                return kind == "upper" ? u_strToUpper(b, c, text.data(), int32_t(text.size()), locale, e) : u_strToLower(b, c, text.data(), int32_t(text.size()), locale, e);
            }))
            return basic_.format(req);
        return utf8(out.data(), int32_t(out.size()));
    }

    return basic_.format(req);
}

} // namespace twidget
