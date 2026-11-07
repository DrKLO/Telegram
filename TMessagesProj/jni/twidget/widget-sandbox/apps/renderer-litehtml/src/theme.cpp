#include "theme.h"

#include <cmath>
#include <regex>

namespace twlite::theme {

using twidget::Json;

namespace {

const char* const THEME_KEYS[] = {
    "bg_color", "text_color", "hint_color", "link_color", "button_color", "button_text_color",
    "secondary_bg_color", "accent_text_color", "subtitle_text_color", "destructive_text_color",
    "section_bg_color", "section_header_text_color", "section_separator_color", "header_bg_color", "bottom_bar_bg_color",
};
// DEFAULT_THEME_PARAMS, in THEME_KEYS order.
const char* const LIGHT[] = {
    "#ffffff", "#000000", "#707579", "#3390ec", "#3390ec", "#ffffff", "#f4f4f5", "#3390ec", "#707579", "#e53935",
    "#ffffff", "#3390ec", "#e7e7e7", "#ffffff", "#ffffff",
};
const char* const DARK[] = {
    "#212121", "#ffffff", "#aaaaaa", "#8774e1", "#8774e1", "#ffffff", "#181818", "#8774e1", "#aaaaaa", "#ff595a",
    "#212121", "#8774e1", "#3d3d3d", "#212121", "#212121",
};
// DEFAULT_TEXT_TOKENS
const char* const TEXT_DEFAULTS[] = {"15px", "1.35", "8px 12px"};

const std::regex& LENGTH() { static const std::regex r("^(0|[0-9]{1,4}(\\.[0-9]{1,3})?(px|em|rem))$"); return r; }
const std::regex& COLOR() { static const std::regex r("^#[0-9a-fA-F]{6}$"); return r; }
const std::regex& FONT() {
    static const std::regex r("^(?:(normal|italic) )?(?:(normal|bold|[1-9]00) )?[0-9]{1,3}(?:\\.[0-9]{1,2})?(?:px|em|rem)"
                              "(?:/[0-9]{1,3}(?:\\.[0-9]{1,2})?(?:px|em|rem)?)? ([a-z-]+(?:, ?[a-z-]+)*)$");
    return r;
}
const char* const GENERIC[] = {"system-ui", "sans-serif", "serif", "monospace", "ui-sans-serif", "ui-serif", "ui-monospace", "ui-rounded"};

std::string lower(std::string s) {
    for (char& c : s) if (c >= 'A' && c <= 'Z') c = char(c - 'A' + 'a');
    return s;
}

std::string trim(const std::string& s) {
    size_t a = s.find_first_not_of(" \t\n\r\f"), b = s.find_last_not_of(" \t\n\r\f");
    return a == std::string::npos ? std::string() : s.substr(a, b - a + 1);
}

using Check = std::string (*)(const Json&);  // "" = refused

std::string color(const Json& v) { return v.isString() && std::regex_match(v.asString(), COLOR()) ? lower(v.asString()) : ""; }
std::string length(const Json& v) { return v.isString() && std::regex_match(v.asString(), LENGTH()) ? v.asString() : ""; }

std::string font(const Json& v) {
    std::smatch m;
    if (!v.isString() || !std::regex_match(v.asString(), m, FONT())) return "";
    std::string families = m[3].str();
    size_t start = 0;
    while (start <= families.size()) {
        size_t comma = families.find(',', start);
        std::string f = trim(families.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
        bool generic = false;
        for (const char* g : GENERIC) generic = generic || f == g;
        if (!generic) return "";
        if (comma == std::string::npos) break;
        start = comma + 1;
    }
    return v.asString();
}

std::string padding(const Json& v) {
    if (!v.isString()) return "";
    const std::string& s = v.asString();
    static const std::regex shape("^\\S+( \\S+){0,3}$");
    if (!std::regex_match(s, shape)) return "";
    size_t start = 0;
    while (true) {
        size_t sp = s.find(' ', start);
        if (!std::regex_match(s.substr(start, sp == std::string::npos ? std::string::npos : sp - start), LENGTH())) return "";
        if (sp == std::string::npos) break;
        start = sp + 1;
    }
    return s;
}

std::string opacity(const Json& v) {
    std::string s = v.isNumber() ? Json::number(v.asNumber()) : v.isString() ? v.asString() : "";
    static const std::regex r("^(0(\\.[0-9]{1,3})?|1(\\.0{1,3})?|\\.[0-9]{1,3})$");
    return std::regex_match(s, r) ? s : "";
}

/// A number in [min, max] with at most `decimals` decimals, as a number or its plain decimal string (+ unit).
std::string bounded(const Json& v, double min, double max, int decimals, const char* unit) {
    double n;
    if (v.isNumber()) n = v.asNumber();
    else if (v.isString() && std::regex_match(v.asString(), std::regex("^[0-9]{1,2}(\\.[0-9]{1," + std::to_string(decimals) + "})?" + unit + "$")))
        n = Json::parseDouble(v.asString().c_str());
    else return "";
    if (!std::isfinite(n) || n < min || n > max) return "";
    double f = std::pow(10.0, decimals);
    return Json::number(std::round(n * f) / f) + unit;
}

std::string font_size(const Json& v) { return bounded(v, 8, 64, 2, "px"); }
std::string line_height(const Json& v) { return bounded(v, 1, 3, 3, ""); }

struct Var { const char* field; const char* name; Check check; };
const Var BUTTON_VARS[] = {
    {"bg", "--tg-button-bg", color},
    {"text", "--tg-button-text", color},
    {"radius", "--tg-button-radius", length},
    {"font", "--tg-button-font", font},
    {"padding", "--tg-button-padding", padding},
    {"height", "--tg-button-height", length},
    {"pressedOpacity", "--tg-button-pressed-opacity", opacity},
    {"disabledOpacity", "--tg-button-disabled-opacity", opacity},
};
const Var TEXT_VARS[] = {
    {"fontSize", "--tg-text-font-size", font_size},
    {"lineHeight", "--tg-text-line-height", line_height},
    {"padding", "--tg-text-padding", padding},
};

struct SafeAreaVar { const char* field; const char* name; double max; };
const SafeAreaVar SAFE_AREA_VARS[] = {
    {"top", "--tg-content-safe-area-inset-top", 64},
    {"right", "--tg-content-safe-area-inset-right", 64},
    {"bottom", "--tg-content-safe-area-inset-bottom", 64},
    {"left", "--tg-content-safe-area-inset-left", 64},
    {"cornerLeft", "--tg-content-safe-area-corner-left", 200},
    {"cornerRight", "--tg-content-safe-area-corner-right", 200},
};

std::string var_name(const char* key) {
    std::string name = std::string("--tg-theme-") + key;
    for (char& c : name) if (c == '_') c = '-';
    return name;
}

}  // namespace

std::vector<std::pair<std::string, std::string>> variables(const Json& theme) {
    std::vector<std::pair<std::string, std::string>> vars;
    bool dark = theme["colorScheme"].asString() == "dark";
    const Json& params = theme["params"];
    for (size_t i = 0; i < std::size(THEME_KEYS); i++) {
        std::string given = color(params[THEME_KEYS[i]]);
        vars.emplace_back(var_name(THEME_KEYS[i]), given.empty() ? (dark ? DARK[i] : LIGHT[i]) : given);
    }
    vars.emplace_back("--tg-color-scheme", dark ? "dark" : "light");
    const Json& button = theme["button"];
    for (const Var& v : BUTTON_VARS) {
        std::string value = v.check(button[v.field]);
        if (!value.empty()) vars.emplace_back(v.name, value);
    }
    const Json& text = theme["text"];
    for (size_t i = 0; i < std::size(TEXT_VARS); i++) {
        std::string value = TEXT_VARS[i].check(text[TEXT_VARS[i].field]);
        vars.emplace_back(TEXT_VARS[i].name, value.empty() ? TEXT_DEFAULTS[i] : value);
    }
    return vars;
}

std::vector<std::pair<std::string, std::string>> safe_area_variables(const Json& inset) {
    std::vector<std::pair<std::string, std::string>> vars;
    for (const SafeAreaVar& v : SAFE_AREA_VARS) {
        const Json& given = inset[v.field];
        double n = given.isNumber() ? given.asNumber() : 0;
        if (!std::isfinite(n) || n < 0 || n > v.max) n = 0;
        vars.emplace_back(v.name, Json::number(std::round(n * 100) / 100) + "px");
    }
    return vars;
}

bool is_variable(const std::string& name) {
    if (name == "--tg-color-scheme") return true;
    for (const SafeAreaVar& v : SAFE_AREA_VARS) if (name == v.name) return true;
    for (const char* k : THEME_KEYS) if (name == var_name(k)) return true;
    for (const Var& v : BUTTON_VARS) if (name == v.name) return true;
    for (const Var& v : TEXT_VARS) if (name == v.name) return true;
    return false;
}

std::string substitute(const std::string& css, const std::vector<std::pair<std::string, std::string>>& vars) {
    std::string out;
    out.reserve(css.size());
    size_t i = 0;
    while (i < css.size()) {
        size_t at = css.find("var(", i);
        if (at == std::string::npos) break;
        // the matching ')' (fallbacks may hold functions: rgba(…))
        size_t end = at + 4;
        int depth = 1;
        size_t comma = std::string::npos;
        for (; end < css.size() && depth; end++) {
            char c = css[end];
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 1 && comma == std::string::npos) comma = end;
        }
        if (depth) break;
        // end is one past ')'
        std::string name = trim(css.substr(at + 4, (comma == std::string::npos ? end - 1 : comma) - at - 4));
        std::string replacement;
        bool found = false;
        for (const auto& [k, v] : vars) if (k == name) { replacement = v; found = true; break; }
        if (!found && comma != std::string::npos) { replacement = trim(css.substr(comma + 1, end - 1 - comma - 1)); found = true; }
        out.append(css, i, at - i);
        if (found) out += replacement;
        else out.append(css, at, end - at);
        i = end;
    }
    out.append(css, i, std::string::npos);
    return out;
}

std::string unwrap_where(const std::string& css) {
    std::string out;
    out.reserve(css.size());
    size_t i = 0;
    while (i < css.size()) {
        size_t at = css.find(":where(", i);
        if (at == std::string::npos) break;
        size_t end = at + 7;
        int depth = 1;
        bool list = false;
        for (; end < css.size() && depth; end++) {
            char c = css[end];
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 1) list = true;
        }
        if (depth) break;
        // end is one past ')'
        char next = end < css.size() ? css[end] : '{';
        out.append(css, i, at - i);
        if (list && next != '{' && next != ',') out.append(css, at, end - at);
        else out.append(css, at + 7, end - 1 - (at + 7));
        i = end;
    }
    out.append(css, i, std::string::npos);
    return out;
}

}  // namespace twlite::theme
