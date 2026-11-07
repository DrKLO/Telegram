#include "css.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iterator>

namespace twlite::css {

namespace {

bool space(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f'; }
bool alpha(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
bool digit(char c) { return c >= '0' && c <= '9'; }

bool valid_name(std::string_view name, size_t max, bool (*first)(char), bool (*rest)(char)) {
    if (name.empty() || name.size() > max || !first(name[0])) return false;
    for (size_t i = 1; i < name.size(); i++)
        if (!rest(name[i])) return false;
    return true;
}

struct Named {
    const char* name;
    uint32_t rgb;
};

// CSS Color 4 named colours.
const Named NAMED[] = {
    {"aliceblue", 0xf0f8ff}, {"antiquewhite", 0xfaebd7}, {"aqua", 0x00ffff}, {"aquamarine", 0x7fffd4}, {"azure", 0xf0ffff},
    {"beige", 0xf5f5dc}, {"bisque", 0xffe4c4}, {"black", 0x000000}, {"blanchedalmond", 0xffebcd}, {"blue", 0x0000ff},
    {"blueviolet", 0x8a2be2}, {"brown", 0xa52a2a}, {"burlywood", 0xdeb887}, {"cadetblue", 0x5f9ea0}, {"chartreuse", 0x7fff00},
    {"chocolate", 0xd2691e}, {"coral", 0xff7f50}, {"cornflowerblue", 0x6495ed}, {"cornsilk", 0xfff8dc}, {"crimson", 0xdc143c},
    {"cyan", 0x00ffff}, {"darkblue", 0x00008b}, {"darkcyan", 0x008b8b}, {"darkgoldenrod", 0xb8860b}, {"darkgray", 0xa9a9a9},
    {"darkgreen", 0x006400}, {"darkgrey", 0xa9a9a9}, {"darkkhaki", 0xbdb76b}, {"darkmagenta", 0x8b008b}, {"darkolivegreen", 0x556b2f},
    {"darkorange", 0xff8c00}, {"darkorchid", 0x9932cc}, {"darkred", 0x8b0000}, {"darksalmon", 0xe9967a}, {"darkseagreen", 0x8fbc8f},
    {"darkslateblue", 0x483d8b}, {"darkslategray", 0x2f4f4f}, {"darkslategrey", 0x2f4f4f}, {"darkturquoise", 0x00ced1}, {"darkviolet", 0x9400d3},
    {"deeppink", 0xff1493}, {"deepskyblue", 0x00bfff}, {"dimgray", 0x696969}, {"dimgrey", 0x696969}, {"dodgerblue", 0x1e90ff},
    {"firebrick", 0xb22222}, {"floralwhite", 0xfffaf0}, {"forestgreen", 0x228b22}, {"fuchsia", 0xff00ff}, {"gainsboro", 0xdcdcdc},
    {"ghostwhite", 0xf8f8ff}, {"gold", 0xffd700}, {"goldenrod", 0xdaa520}, {"gray", 0x808080}, {"green", 0x008000},
    {"greenyellow", 0xadff2f}, {"grey", 0x808080}, {"honeydew", 0xf0fff0}, {"hotpink", 0xff69b4}, {"indianred", 0xcd5c5c},
    {"indigo", 0x4b0082}, {"ivory", 0xfffff0}, {"khaki", 0xf0e68c}, {"lavender", 0xe6e6fa}, {"lavenderblush", 0xfff0f5},
    {"lawngreen", 0x7cfc00}, {"lemonchiffon", 0xfffacd}, {"lightblue", 0xadd8e6}, {"lightcoral", 0xf08080}, {"lightcyan", 0xe0ffff},
    {"lightgoldenrodyellow", 0xfafad2}, {"lightgray", 0xd3d3d3}, {"lightgreen", 0x90ee90}, {"lightgrey", 0xd3d3d3}, {"lightpink", 0xffb6c1},
    {"lightsalmon", 0xffa07a}, {"lightseagreen", 0x20b2aa}, {"lightskyblue", 0x87cefa}, {"lightslategray", 0x778899}, {"lightslategrey", 0x778899},
    {"lightsteelblue", 0xb0c4de}, {"lightyellow", 0xffffe0}, {"lime", 0x00ff00}, {"limegreen", 0x32cd32}, {"linen", 0xfaf0e6},
    {"magenta", 0xff00ff}, {"maroon", 0x800000}, {"mediumaquamarine", 0x66cdaa}, {"mediumblue", 0x0000cd}, {"mediumorchid", 0xba55d3},
    {"mediumpurple", 0x9370db}, {"mediumseagreen", 0x3cb371}, {"mediumslateblue", 0x7b68ee}, {"mediumspringgreen", 0x00fa9a}, {"mediumturquoise", 0x48d1cc},
    {"mediumvioletred", 0xc71585}, {"midnightblue", 0x191970}, {"mintcream", 0xf5fffa}, {"mistyrose", 0xffe4e1}, {"moccasin", 0xffe4b5},
    {"navajowhite", 0xffdead}, {"navy", 0x000080}, {"oldlace", 0xfdf5e6}, {"olive", 0x808000}, {"olivedrab", 0x6b8e23},
    {"orange", 0xffa500}, {"orangered", 0xff4500}, {"orchid", 0xda70d6}, {"palegoldenrod", 0xeee8aa}, {"palegreen", 0x98fb98},
    {"paleturquoise", 0xafeeee}, {"palevioletred", 0xdb7093}, {"papayawhip", 0xffefd5}, {"peachpuff", 0xffdab9}, {"peru", 0xcd853f},
    {"pink", 0xffc0cb}, {"plum", 0xdda0dd}, {"powderblue", 0xb0e0e6}, {"purple", 0x800080}, {"rebeccapurple", 0x663399},
    {"red", 0xff0000}, {"rosybrown", 0xbc8f8f}, {"royalblue", 0x4169e1}, {"saddlebrown", 0x8b4513}, {"salmon", 0xfa8072},
    {"sandybrown", 0xf4a460}, {"seagreen", 0x2e8b57}, {"seashell", 0xfff5ee}, {"sienna", 0xa0522d}, {"silver", 0xc0c0c0},
    {"skyblue", 0x87ceeb}, {"slateblue", 0x6a5acd}, {"slategray", 0x708090}, {"slategrey", 0x708090}, {"snow", 0xfffafa},
    {"springgreen", 0x00ff7f}, {"steelblue", 0x4682b4}, {"tan", 0xd2b48c}, {"teal", 0x008080}, {"thistle", 0xd8bfd8},
    {"tomato", 0xff6347}, {"turquoise", 0x40e0d0}, {"violet", 0xee82ee}, {"wheat", 0xf5deb3}, {"white", 0xffffff},
    {"whitesmoke", 0xf5f5f5}, {"yellow", 0xffff00}, {"yellowgreen", 0x9acd32},
};

bool named_color(std::string_view word, Color& out) {
    std::string l = lower(std::string(word));
    if (l == "transparent") {
        out = Color{0, 0, 0, 0};
        return true;
    }
    for (const Named& n : NAMED) {
        if (l == n.name) {
            out = Color{uint8_t(n.rgb >> 16), uint8_t(n.rgb >> 8), uint8_t(n.rgb), 255};
            return true;
        }
    }
    return false;
}

int hex_value(char c) {
    if (digit(c)) return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

bool parse_hex(std::string_view hex, double rgba[4]) {
    for (char c : hex)
        if (hex_value(c) < 0) return false;
    switch (hex.size()) {
    case 3: case 4:
        for (size_t i = 0; i < hex.size(); i++) rgba[i] = hex_value(hex[i]) * 17;
        rgba[3] = hex.size() == 4 ? rgba[3] / 255 : 1;
        return true;
    case 6: case 8:
        for (size_t i = 0; i < hex.size() / 2; i++) rgba[i] = hex_value(hex[2 * i]) * 16 + hex_value(hex[2 * i + 1]);
        rgba[3] = hex.size() == 8 ? rgba[3] / 255 : 1;
        return true;
    default:
        return false;
    }
}

/// The numbers inside `rgb(...)`/`hsl(...)`, split at commas, spaces and `/`; `%` divides by 100.
std::vector<double> components(std::string_view inner, bool* percent = nullptr) {
    std::vector<double> out;
    std::string cur;
    auto flush = [&] {
        if (cur.empty()) return;
        bool pct = cur.back() == '%';
        if (pct) cur.pop_back();
        if (ends_with(cur, "deg")) cur.resize(cur.size() - 3);
        if (auto v = number(cur)) {
            out.push_back(pct ? *v / 100 : *v);
            if (percent && out.size() <= 3) percent[out.size() - 1] = pct;
        }
        cur.clear();
    };
    for (char c : inner) {
        if (c == ',' || c == '/' || space(c)) flush();
        else cur.push_back(c);
    }
    flush();
    return out;
}

void rgba_string(const double c[4], std::string& out) {
    out += "rgba(";
    out += format_number(c[0]);
    out += ", ";
    out += format_number(c[1]);
    out += ", ";
    out += format_number(c[2]);
    out += ", ";
    out += format_number(c[3]);
    out += ")";
}

uint8_t channel(double v) { return uint8_t(std::lround(std::clamp(v, 0.0, 255.0))); }

// calc() of times (as twidget's CSSTime) or of lengths in px and %, with the % resolved against
// `percent_of`: + and - of like quantities, * and / by numbers.
struct Quantity {
    double value;
    bool unit;
};

struct MathScanner {
    std::string_view s;
    bool lengths = false;
    double percent_of = 0;
    size_t i = 0;

    void skip() { while (i < s.size() && space(s[i])) i++; }
    bool take(char c) {
        skip();
        if (i < s.size() && s[i] == c) { i++; return true; }
        return false;
    }
    bool at_end() { skip(); return i == s.size(); }

    std::optional<Quantity> sum(int depth) {
        if (depth >= 16) return std::nullopt;
        auto left = product(depth);
        if (!left) return std::nullopt;
        for (;;) {
            if (take('+')) {
                auto right = product(depth);
                if (!right || right->unit != left->unit) return std::nullopt;
                left->value += right->value;
            } else if (take('-')) {
                auto right = product(depth);
                if (!right || right->unit != left->unit) return std::nullopt;
                left->value -= right->value;
            } else return left;
        }
    }
    std::optional<Quantity> product(int depth) {
        auto left = factor(depth);
        if (!left) return std::nullopt;
        for (;;) {
            if (take('*')) {
                auto right = factor(depth);
                if (!right || (left->unit && right->unit)) return std::nullopt;
                left = Quantity{left->value * right->value, left->unit || right->unit};
            } else if (take('/')) {
                auto right = factor(depth);
                if (!right || right->unit || right->value == 0) return std::nullopt;
                left->value /= right->value;
            } else return left;
        }
    }
    std::optional<Quantity> factor(int depth) {
        skip();
        if (s.substr(i, 5) == "calc(") i += 4;
        if (take('(')) {
            auto inner = sum(depth + 1);
            if (!inner || !take(')')) return std::nullopt;
            return inner;
        }
        size_t start = i;
        if (i < s.size() && (s[i] == '-' || s[i] == '+')) i++;
        while (i < s.size() && (digit(s[i]) || s[i] == '.')) i++;
        if (i == start) return std::nullopt;
        auto v = number(s.substr(start, i - start));
        if (!v) return std::nullopt;
        if (lengths) {
            if (s.substr(i, 2) == "px") { i += 2; return Quantity{*v, true}; }
            if (i < s.size() && s[i] == '%') { i++; return Quantity{*v * percent_of / 100, true}; }
        } else {
            if (s.substr(i, 2) == "ms") { i += 2; return Quantity{*v / 1000, true}; }
            if (i < s.size() && s[i] == 's') { i++; return Quantity{*v, true}; }
        }
        return Quantity{*v, false};
    }
};

} // namespace

std::string trim(std::string_view s) {
    size_t a = 0, b = s.size();
    while (a < b && space(s[a])) a++;
    while (b > a && space(s[b - 1])) b--;
    return std::string(s.substr(a, b - a));
}

std::string lower(std::string s) {
    for (auto& c : s) c = char(std::tolower((unsigned char)c));
    return s;
}

bool iequals(std::string_view a, std::string_view b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); i++)
        if (std::tolower((unsigned char)a[i]) != std::tolower((unsigned char)b[i])) return false;
    return true;
}

bool starts_with(std::string_view s, std::string_view prefix) { return s.substr(0, prefix.size()) == prefix; }
bool ends_with(std::string_view s, std::string_view suffix) { return s.size() >= suffix.size() && s.substr(s.size() - suffix.size()) == suffix; }

std::optional<double> number(std::string_view s) {
    std::string t = trim(s);
    if (t.empty()) return std::nullopt;
    char* end = nullptr;
    double v = std::strtod(t.c_str(), &end);
    if (end != t.c_str() + t.size() || !std::isfinite(v)) return std::nullopt;
    return v;
}

std::vector<std::string> split_top_level(std::string_view text, char separator) {
    std::vector<std::string> parts;
    std::string current;
    int depth = 0;
    char quote = 0;
    bool escaped = false;
    for (char ch : text) {
        if (escaped) { escaped = false; current.push_back(ch); continue; }
        if (ch == '\\') { escaped = true; current.push_back(ch); continue; }
        if (quote) {
            if (ch == quote) quote = 0;
            current.push_back(ch);
            continue;
        }
        if (ch == '"' || ch == '\'') quote = ch;
        else if (ch == '(') depth++;
        else if (ch == ')') depth = std::max(0, depth - 1);
        if (ch == separator && depth == 0) {
            parts.push_back(std::move(current));
            current.clear();
        } else current.push_back(ch);
    }
    if (!current.empty()) parts.push_back(std::move(current));
    return parts;
}

std::vector<Declaration> parse_declarations(std::string_view text) {
    std::vector<Declaration> result;
    for (const std::string& part : split_top_level(text, ';')) {
        size_t colon = part.find(':');
        if (colon == std::string::npos) continue;
        std::string name = trim(std::string_view(part).substr(0, colon));
        std::string value = trim(std::string_view(part).substr(colon + 1));
        if (name.empty() || value.empty()) continue;
        bool important = false;
        if (value.size() >= 10 && iequals(std::string_view(value).substr(value.size() - 10), "!important")) {
            important = true;
            value = trim(std::string_view(value).substr(0, value.size() - 10));
        }
        if (!starts_with(name, "--")) name = lower(name);
        if (!is_property_name(name)) continue;
        result.erase(std::remove_if(result.begin(), result.end(), [&](const Declaration& d) { return d.name == name; }), result.end());
        result.push_back({std::move(name), std::move(value), important});
    }
    return result;
}

bool is_tag_name(std::string_view name) {
    return valid_name(name, 64, alpha, [](char c) { return alpha(c) || digit(c) || c == '-'; });
}

bool is_attribute_name(std::string_view name) {
    return valid_name(name, 64, [](char c) { return alpha(c) || c == '_' || c == ':'; },
                      [](char c) { return alpha(c) || digit(c) || c == '_' || c == ':' || c == '.' || c == '-'; });
}

bool is_property_name(std::string_view name) {
    std::string_view body = starts_with(name, "--") ? name.substr(2) : starts_with(name, "-") ? name.substr(1) : name;
    auto ok = [](char c) { return alpha(c) || digit(c) || c == '-' || c == '_'; };
    return valid_name(body, 128, ok, ok);
}

bool ident_char(char c) { return alpha(c) || digit(c) || c == '-' || c == '_' || uint8_t(c) >= 0x80; }

size_t skip_opaque(std::string_view css, size_t i) {
    if (i >= css.size()) return i;
    char c = css[i];
    if (c == '"' || c == '\'') {
        // To the closing quote, past escapes; a newline ends a bad string, as the tokenizer has it.
        for (size_t j = i + 1; j < css.size(); j++) {
            if (css[j] == '\\') j++;
            else if (css[j] == c) return j + 1;
            else if (css[j] == '\n') return j;
        }
        return css.size();
    }
    if (c == '/' && i + 1 < css.size() && css[i + 1] == '*') {
        size_t end = css.find("*/", i + 2);
        return end == std::string_view::npos ? css.size() : end + 2;
    }
    if (c == '\\') return std::min(css.size(), i + 2);
    // An unquoted url(…) is one token: its text is the URL's.
    if ((c == 'u' || c == 'U') && (i == 0 || !ident_char(css[i - 1])) && iequals(css.substr(i, 4), "url(")) {
        size_t j = i + 4;
        while (j < css.size() && (css[j] == ' ' || css[j] == '\t' || css[j] == '\n')) j++;
        if (j < css.size() && (css[j] == '"' || css[j] == '\'')) return i;
        for (; j < css.size(); j++) {
            if (css[j] == '\\') j++;
            else if (css[j] == ')') return j + 1;
        }
        return css.size();
    }
    return i;
}

std::string resolve_light_dark(std::string_view css, bool dark) {
    static constexpr std::string_view KEY = "light-dark(";
    // A function token: outside strings, comments and URLs, and not the end of a longer name.
    auto find = [](std::string_view s, size_t from) {
        for (size_t i = from; i + KEY.size() <= s.size();) {
            if (size_t skip = skip_opaque(s, i); skip != i) {
                i = skip;
                continue;
            }
            if ((i == 0 || !ident_char(s[i - 1])) && iequals(s.substr(i, KEY.size()), KEY)) return i;
            i++;
        }
        return std::string_view::npos;
    };
    if (find(css, 0) == std::string_view::npos) return std::string(css);
    std::string out;
    size_t pos = 0;
    for (int guard = 0; guard < 4096; guard++) {
        size_t at = find(css, pos);
        if (at == std::string_view::npos) break;
        out.append(css.substr(pos, at - pos));
        size_t i = at + KEY.size();
        int depth = 1;
        size_t close = i;
        while (close < css.size()) {
            if (size_t skip = skip_opaque(css, close); skip != close) {
                close = skip;
                continue;
            }
            if (css[close] == '(') depth++;
            else if (css[close] == ')' && --depth == 0) break;
            close++;
        }
        auto parts = split_top_level(css.substr(i, close - i), ',');
        std::string chosen = parts.size() == 2 ? parts[dark ? 1 : 0] : parts.empty() ? std::string() : parts[0];
        // The chosen side may hold another light-dark().
        out += resolve_light_dark(trim(chosen), dark);
        pos = close < css.size() ? close + 1 : css.size();
    }
    out.append(css.substr(pos));
    return out;
}

std::optional<double> parse_time(std::string_view text) {
    std::string t = lower(trim(text));
    if (starts_with(t, "calc(")) {
        if (t.size() > 512) return std::nullopt;
        MathScanner scanner{t};
        auto q = scanner.sum(0);
        if (!q || !scanner.at_end() || !q->unit || !std::isfinite(q->value)) return std::nullopt;
        return q->value;
    }
    if (ends_with(t, "ms")) {
        if (auto v = number(std::string_view(t).substr(0, t.size() - 2))) return *v / 1000;
        return std::nullopt;
    }
    if (ends_with(t, "s")) return number(std::string_view(t).substr(0, t.size() - 1));
    return std::nullopt;
}

bool parse_color(std::string_view text, Color& out) {
    std::string t = lower(trim(text));
    if (t.empty()) return false;
    if (t[0] == '#') {
        double c[4];
        if (!parse_hex(std::string_view(t).substr(1), c)) return false;
        out = Color{channel(c[0]), channel(c[1]), channel(c[2]), channel(c[3] * 255)};
        return true;
    }
    size_t open = t.find('(');
    if (open != std::string::npos && t.back() == ')') {
        std::string fn = t.substr(0, open);
        std::string_view inner = std::string_view(t).substr(open + 1, t.size() - open - 2);
        if (fn == "rgb" || fn == "rgba") {
            bool pct[3] = {false, false, false};
            auto v = components(inner, pct);
            if (v.size() < 3) return false;
            for (int i = 0; i < 3; i++) if (pct[i]) v[i] *= 255;
            out = Color{channel(v[0]), channel(v[1]), channel(v[2]), channel((v.size() > 3 ? v[3] : 1) * 255)};
            return true;
        }
        if (fn == "hsl" || fn == "hsla") {
            auto v = components(inner);
            if (v.size() < 3) return false;
            double h = std::fmod(std::fmod(v[0], 360) + 360, 360) / 360, s = std::clamp(v[1], 0.0, 1.0), l = std::clamp(v[2], 0.0, 1.0);
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
            auto hue = [&](double t0) {
                double t1 = t0 < 0 ? t0 + 1 : t0 > 1 ? t0 - 1 : t0;
                if (t1 < 1.0 / 6) return p + (q - p) * 6 * t1;
                if (t1 < 1.0 / 2) return q;
                if (t1 < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t1) * 6;
                return p;
            };
            out = Color{channel(hue(h + 1.0 / 3) * 255), channel(hue(h) * 255), channel(hue(h - 1.0 / 3) * 255), channel((v.size() > 3 ? v[3] : 1) * 255)};
            return true;
        }
        return false;
    }
    if (t == "currentcolor") {
        out = Color{0, 0, 0, 255};
        return true;
    }
    return named_color(t, out);
}

std::string normalize_colors(std::string_view text) {
    std::string out;
    out.reserve(text.size() + 16);
    size_t i = 0;
    while (i < text.size()) {
        char c = text[i];
        if (c == '#') {
            size_t j = i + 1;
            while (j < text.size() && hex_value(text[j]) >= 0) j++;
            double rgba[4];
            if (parse_hex(text.substr(i + 1, j - i - 1), rgba)) {
                rgba_string(rgba, out);
                i = j;
                continue;
            }
        }
        if (alpha(c) && (i == 0 || !(alpha(text[i - 1]) || digit(text[i - 1]) || text[i - 1] == '-'))) {
            size_t j = i;
            while (j < text.size() && (alpha(text[j]) || text[j] == '-')) j++;
            std::string_view word = text.substr(i, j - i);
            if (j < text.size() && text[j] == '(') {
                if (iequals(word, "rgb") || iequals(word, "rgba")) {
                    size_t close = text.find(')', j);
                    if (close != std::string_view::npos) {
                        bool pct[3] = {false, false, false};
                        auto v = components(text.substr(j + 1, close - j - 1), pct);
                        if (v.size() >= 3) {
                            for (int k = 0; k < 3; k++) if (pct[k]) v[k] *= 255;
                            double rgba[4] = {v[0], v[1], v[2], v.size() > 3 ? v[3] : 1};
                            rgba_string(rgba, out);
                            i = close + 1;
                            continue;
                        }
                    }
                }
            } else {
                Color named;
                if (named_color(word, named)) {
                    double rgba[4] = {double(named.r), double(named.g), double(named.b), named.a / 255.0};
                    rgba_string(rgba, out);
                    i = j;
                    continue;
                }
            }
            out.append(word);
            i = j;
            continue;
        }
        out.push_back(c);
        i++;
    }
    return out;
}

std::string format_number(double value, int digits) {
    if (!std::isfinite(value)) return "0";
    if (value == std::round(value) && std::fabs(value) < 1e9) return std::to_string((long long)value);
    char buf[64];
    std::snprintf(buf, sizeof buf, "%.*f", digits, value);
    std::string s = buf;
    while (!s.empty() && s.back() == '0') s.pop_back();
    if (!s.empty() && s.back() == '.') s.pop_back();
    if (s == "-0") s = "0";
    return s;
}

Font parse_font(std::string_view font) {
    Font f;
    std::vector<std::string> tokens;
    {
        std::string cur;
        char quote = 0;
        for (char c : font) {
            if (quote) {
                if (c == quote) quote = 0;
                cur.push_back(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                cur.push_back(c);
            } else if (space(c)) {
                if (!cur.empty()) tokens.push_back(cur);
                cur.clear();
            } else cur.push_back(c);
        }
        if (!cur.empty()) tokens.push_back(cur);
    }
    size_t i = 0;
    for (; i < tokens.size(); i++) {
        std::string t = lower(tokens[i]);
        if (t == "italic" || t == "oblique") f.italic = true;
        else if (t == "bold" || t == "bolder") f.weight = 700;
        else if (t == "lighter") f.weight = 300;
        else if (t == "normal" || t == "small-caps") {}
        else if (t.size() == 3 && digit(t[0]) && digit(t[1]) && digit(t[2])) f.weight = std::atoi(t.c_str());
        else if (digit(t[0]) || t[0] == '.') {
            std::string size = t.substr(0, t.find('/'));
            float v = float(std::atof(size.c_str()));
            if (ends_with(size, "pt")) v *= 96.0f / 72.0f;
            else if (ends_with(size, "rem") || ends_with(size, "em")) v *= 16.0f;
            else if (ends_with(size, "%")) v *= 16.0f / 100.0f;
            if (v > 0 && v < 4096) f.size = v;
            i++;
            break;
        } else break;
    }
    if (i < tokens.size()) {
        f.family.clear();
        for (; i < tokens.size(); i++) {
            if (!f.family.empty()) f.family += ' ';
            f.family += tokens[i];
        }
    }
    return f;
}

// ── transforms ──

namespace {

constexpr double PI = 3.14159265358979323846;

/// A number with its unit, lowercased: `12.5px` → (12.5, "px").
std::optional<std::pair<double, std::string>> dimension(std::string_view text) {
    std::string t = trim(text);
    size_t i = 0;
    if (i < t.size() && (t[i] == '+' || t[i] == '-')) i++;
    while (i < t.size() && (digit(t[i]) || t[i] == '.')) i++;
    if (i < t.size() && (t[i] == 'e' || t[i] == 'E') && i + 1 < t.size() && (digit(t[i + 1]) || t[i + 1] == '-' || t[i + 1] == '+')) {
        i += 2;
        while (i < t.size() && digit(t[i])) i++;
    }
    auto value = number(std::string_view(t).substr(0, i));
    if (!value) return std::nullopt;
    return std::make_pair(*value, lower(t.substr(i)));
}

std::optional<double> length(std::string_view text, double percent_of) {
    std::string t = lower(trim(text));
    if (starts_with(t, "calc(")) {
        if (t.size() > 512) return std::nullopt;
        MathScanner scanner{t, true, percent_of};
        auto q = scanner.sum(0);
        // A bare 0 is a length; any other unitless result is not.
        if (!q || !scanner.at_end() || (!q->unit && q->value != 0) || !std::isfinite(q->value)) return std::nullopt;
        return q->value;
    }
    auto d = dimension(text);
    if (!d) return std::nullopt;
    if (d->second == "px" || (d->second.empty() && d->first == 0)) return d->first;
    if (d->second == "%") return d->first * percent_of / 100;
    return std::nullopt;
}

std::optional<double> angle(std::string_view text) {
    auto d = dimension(text);
    if (!d) return std::nullopt;
    const std::string& u = d->second;
    if (u == "deg" || (u.empty() && d->first == 0)) return d->first * PI / 180;
    if (u == "rad") return d->first;
    if (u == "grad") return d->first * PI / 200;
    if (u == "turn") return d->first * 2 * PI;
    return std::nullopt;
}

std::optional<double> scalar(std::string_view text) {
    auto d = dimension(text);
    if (!d) return std::nullopt;
    if (d->second.empty()) return d->first;
    if (d->second == "%") return d->first / 100;
    return std::nullopt;
}

std::vector<std::string> arguments(std::string_view inner) {
    std::vector<std::string> out;
    for (const std::string& part : split_top_level(inner, ',')) {
        std::string token;
        int depth = 0;
        for (char c : part) {
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
            if (space(c) && depth == 0) {
                if (!token.empty()) out.push_back(std::move(token));
                token.clear();
            } else token.push_back(c);
        }
        if (!token.empty()) out.push_back(std::move(token));
    }
    return out;
}

Affine rotation(double radians) {
    float c = float(std::cos(radians)), s = float(std::sin(radians));
    return Affine{c, s, -s, c, 0, 0};
}

std::optional<Affine> function(const std::string& name, const std::vector<std::string>& args, float w, float h) {
    size_t n = args.size();
    if (name == "matrix") {
        if (n != 6) return std::nullopt;
        float v[6];
        for (size_t i = 0; i < 6; i++) {
            auto x = number(args[i]);
            if (!x) return std::nullopt;
            v[i] = float(*x);
        }
        return Affine{v[0], v[1], v[2], v[3], v[4], v[5]};
    }
    if (name == "translate" || name == "translate3d") {
        if (n < 1 || n > (name == "translate" ? 2u : 3u)) return std::nullopt;
        auto x = length(args[0], w);
        auto y = n > 1 ? length(args[1], h) : std::optional<double>(0);
        // Without perspective a z offset or z scale does not move anything on screen, so it is accepted and dropped.
        if (!x || !y || (n == 3 && !length(args[2], 0))) return std::nullopt;
        return Affine::translate(float(*x), float(*y));
    }
    if (name == "translatex" || name == "translatey") {
        if (n != 1) return std::nullopt;
        auto v = length(args[0], name == "translatex" ? w : h);
        if (!v) return std::nullopt;
        return name == "translatex" ? Affine::translate(float(*v), 0) : Affine::translate(0, float(*v));
    }
    if (name == "translatez") return n == 1 && length(args[0], 0) ? std::optional<Affine>(Affine{}) : std::nullopt;
    if (name == "scale" || name == "scale3d") {
        if (n < 1 || n > (name == "scale" ? 2u : 3u)) return std::nullopt;
        auto x = scalar(args[0]);
        auto y = n > 1 ? scalar(args[1]) : x;
        if (!x || !y || (n == 3 && !scalar(args[2]))) return std::nullopt;
        return Affine{float(*x), 0, 0, float(*y), 0, 0};
    }
    if (name == "scalex" || name == "scaley") {
        auto v = n == 1 ? scalar(args[0]) : std::nullopt;
        if (!v) return std::nullopt;
        return name == "scalex" ? Affine{float(*v), 0, 0, 1, 0, 0} : Affine{1, 0, 0, float(*v), 0, 0};
    }
    if (name == "rotate" || name == "rotatez") {
        auto a = n == 1 ? angle(args[0]) : std::nullopt;
        if (!a) return std::nullopt;
        return rotation(*a);
    }
    if (name == "skew" || name == "skewx" || name == "skewy") {
        if (n < 1 || n > (name == "skew" ? 2u : 1u)) return std::nullopt;
        auto ax = name == "skewy" ? std::optional<double>(0) : angle(args[0]);
        auto ay = name == "skewy" ? angle(args[0]) : n > 1 ? angle(args[1]) : std::optional<double>(0);
        if (!ax || !ay) return std::nullopt;
        return Affine{1, float(std::tan(*ay)), float(std::tan(*ax)), 1, 0, 0};
    }
    return std::nullopt;
}

} // namespace

std::optional<Affine> parse_transform(std::string_view text, float w, float h) {
    std::string t = trim(text);
    if (t.empty() || iequals(t, "none")) return Affine{};
    Affine result;
    size_t pos = 0;
    while (true) {
        while (pos < t.size() && space(t[pos])) pos++;
        if (pos == t.size()) break;
        size_t open = t.find('(', pos);
        if (open == std::string::npos) return std::nullopt;
        size_t close = open + 1;
        for (int depth = 1; close < t.size(); close++) {
            if (t[close] == '(') depth++;
            else if (t[close] == ')' && --depth == 0) break;
        }
        if (close == t.size()) return std::nullopt;
        std::string name = lower(trim(std::string_view(t).substr(pos, open - pos)));
        auto args = arguments(std::string_view(t).substr(open + 1, close - open - 1));
        auto m = function(name, args, w, h);
        if (!m) return std::nullopt;
        result = result * *m;
        pos = close + 1;
    }
    return result;
}

std::optional<Affine> parse_translate(std::string_view text, float w, float h) {
    std::string t = trim(text);
    if (t.empty() || iequals(t, "none")) return Affine{};
    auto args = arguments(t);
    if (args.empty() || args.size() > 3) return std::nullopt;
    auto x = length(args[0], w);
    auto y = args.size() > 1 ? length(args[1], h) : std::optional<double>(0);
    if (!x || !y || (args.size() == 3 && length(args[2], 0) != 0.0)) return std::nullopt;
    return Affine::translate(float(*x), float(*y));
}

std::optional<Affine> parse_rotate(std::string_view text) {
    std::string t = trim(text);
    if (t.empty() || iequals(t, "none")) return Affine{};
    auto args = arguments(t);
    if (args.size() == 2 && iequals(args[0], "z")) args.erase(args.begin());
    auto a = args.size() == 1 ? angle(args[0]) : std::nullopt;
    if (!a) return std::nullopt;
    return rotation(*a);
}

std::optional<Affine> parse_scale(std::string_view text) {
    std::string t = trim(text);
    if (t.empty() || iequals(t, "none")) return Affine{};
    auto args = arguments(t);
    if (args.empty() || args.size() > 3) return std::nullopt;
    auto x = scalar(args[0]);
    auto y = args.size() > 1 ? scalar(args[1]) : x;
    if (!x || !y || (args.size() == 3 && scalar(args[2]) != 1.0)) return std::nullopt;
    return Affine{float(*x), 0, 0, float(*y), 0, 0};
}

std::optional<std::pair<float, float>> parse_origin(std::string_view text, float w, float h) {
    auto args = arguments(trim(text));
    if (args.empty()) return std::make_pair(w / 2, h / 2);
    if (args.size() > 3 || (args.size() == 3 && length(args[2], 0) != 0.0)) return std::nullopt;
    // A vertical keyword first (`top left`) means the same as the other order.
    if (args.size() >= 2) {
        std::string a = lower(args[0]), b = lower(args[1]);
        if (a == "top" || a == "bottom" || b == "left" || b == "right") std::swap(args[0], args[1]);
    } else {
        std::string a = lower(args[0]);
        if (a == "top" || a == "bottom") args = {"center", args[0]};
    }
    auto axis = [](const std::string& v, float size, bool horizontal) -> std::optional<float> {
        std::string k = lower(v);
        if (k == "center") return size / 2;
        if (horizontal ? k == "left" : k == "top") return 0.0f;
        if (horizontal ? k == "right" : k == "bottom") return size;
        auto l = length(v, size);
        return l ? std::optional<float>(float(*l)) : std::nullopt;
    };
    auto x = axis(args[0], w, true);
    auto y = args.size() > 1 ? axis(args[1], h, false) : std::optional<float>(h / 2);
    if (!x || !y) return std::nullopt;
    return std::make_pair(*x, *y);
}

std::optional<float> parse_opacity(std::string_view text) {
    auto v = scalar(text);
    if (!v) return std::nullopt;
    return float(std::clamp(*v, 0.0, 1.0));
}

// ───────────────────────── theme variables ─────────────────────────

namespace {

constexpr const char* THEME_KEYS[] = {
    "bg_color", "text_color", "hint_color", "link_color", "button_color", "button_text_color",
    "secondary_bg_color", "accent_text_color", "subtitle_text_color", "destructive_text_color",
    "section_bg_color", "section_header_text_color", "section_separator_color", "header_bg_color", "bottom_bar_bg_color",
};
constexpr size_t THEME_KEY_COUNT = std::size(THEME_KEYS);

// DEFAULT_THEME_PARAMS in sdk-policy.ts, light then dark, in THEME_KEYS order.
constexpr const char* THEME_DEFAULTS[2][THEME_KEY_COUNT] = {
    {"#ffffff", "#000000", "#707579", "#3390ec", "#3390ec", "#ffffff", "#f4f4f5", "#3390ec", "#707579", "#e53935",
     "#ffffff", "#3390ec", "#e7e7e7", "#ffffff", "#ffffff"},
    {"#212121", "#ffffff", "#aaaaaa", "#8774e1", "#8774e1", "#ffffff", "#181818", "#8774e1", "#aaaaaa", "#ff595a",
     "#212121", "#8774e1", "#3d3d3d", "#212121", "#212121"},
};

constexpr const char* BUTTON_VARS[][2] = {
    {"bg", "--tg-button-bg"},
    {"text", "--tg-button-text"},
    {"radius", "--tg-button-radius"},
    {"font", "--tg-button-font"},
    {"padding", "--tg-button-padding"},
    {"height", "--tg-button-height"},
    {"pressedOpacity", "--tg-button-pressed-opacity"},
    {"disabledOpacity", "--tg-button-disabled-opacity"},
};
/// The text tokens and the reference set that stands for any the host leaves out (DEFAULT_TEXT_TOKENS).
constexpr const char* TEXT_VARS[][3] = {
    {"fontSize", "--tg-text-font-size", "15px"},
    {"lineHeight", "--tg-text-line-height", "1.35"},
    {"padding", "--tg-text-padding", "8px 12px"},
};

constexpr const char* GENERIC_FAMILIES[] = {"system-ui", "sans-serif", "serif", "monospace", "ui-sans-serif", "ui-serif", "ui-monospace", "ui-rounded"};

bool is_digit(char c) { return c >= '0' && c <= '9'; }

bool hex_color(std::string_view v) {
    if (v.size() != 7 || v[0] != '#') return false;
    for (char c : v.substr(1))
        if (!std::isxdigit(static_cast<unsigned char>(c))) return false;
    return true;
}

// Consumes `[0-9]{1,int_digits}(\.[0-9]{1,frac_digits})?` at `i`.
bool consume_number(std::string_view v, size_t& i, size_t int_digits, size_t frac_digits) {
    size_t start = i;
    while (i < v.size() && is_digit(v[i]) && i - start < int_digits) i++;
    if (i == start) return false;
    if (i < v.size() && v[i] == '.') {
        size_t frac = ++i;
        while (i < v.size() && is_digit(v[i]) && i - frac < frac_digits) i++;
        if (i == frac) return false;
    }
    return true;
}

bool consume_unit(std::string_view v, size_t& i) {
    for (std::string_view unit : {"px", "rem", "em"}) {
        if (v.substr(i, unit.size()) == unit) {
            i += unit.size();
            return true;
        }
    }
    return false;
}

// `0`, or a length in px, em or rem: what a button token may hold.
bool token_length(std::string_view v) {
    if (v == "0") return true;
    size_t i = 0;
    return consume_number(v, i, 4, 3) && consume_unit(v, i) && i == v.size();
}

// A number in [min, max] with at most `decimals` decimals, given as a number or as `[0-9]{1,2}(.d…)` + `unit`;
// written back rounded, with the unit.
std::optional<std::string> bounded(const twidget::Json& value, double min, double max, size_t decimals, std::string_view unit) {
    double n;
    if (value.isNumber()) {
        n = value.asNumber();
    } else if (value.isString()) {
        std::string_view v = value.asString();
        size_t i = 0;
        if (!consume_number(v, i, 2, decimals) || v.substr(i) != unit) return std::nullopt;
        n = twidget::Json::parseDouble(value.asString().c_str());
    } else {
        return std::nullopt;
    }
    if (!std::isfinite(n) || n < min || n > max) return std::nullopt;
    double f = std::pow(10.0, double(decimals));
    return twidget::Json::number(std::round(n * f) / f) + std::string(unit);
}

// `[normal|italic] [normal|bold|N00] size[/line-height] family, …` with generic families only.
bool token_font(std::string_view v) {
    size_t i = 0;
    auto word = [&](std::initializer_list<std::string_view> words) {
        for (std::string_view w : words) {
            if (v.substr(i, w.size()) == w && i + w.size() < v.size() && v[i + w.size()] == ' ') {
                i += w.size() + 1;
                return true;
            }
        }
        return false;
    };
    word({"normal", "italic"});
    if (!word({"normal", "bold"}) && i + 3 < v.size() && v[i] >= '1' && v[i] <= '9' && v.substr(i + 1, 3) == "00 ") i += 4;
    if (!consume_number(v, i, 3, 2) || !consume_unit(v, i)) return false;
    if (i < v.size() && v[i] == '/') {
        i++;
        if (!consume_number(v, i, 3, 2)) return false;
        consume_unit(v, i);
    }
    if (i >= v.size() || v[i] != ' ') return false;
    std::string_view families = v.substr(i + 1);
    if (families.empty()) return false;
    size_t start = 0;
    while (start <= families.size()) {
        size_t comma = families.find(',', start);
        std::string_view family = families.substr(start, comma == std::string_view::npos ? std::string_view::npos : comma - start);
        if (comma != std::string_view::npos && comma + 1 < families.size() && families[comma + 1] == ' ') comma++;
        if (family.empty() || std::find(std::begin(GENERIC_FAMILIES), std::end(GENERIC_FAMILIES), family) == std::end(GENERIC_FAMILIES)) return false;
        if (comma == std::string_view::npos) break;
        start = comma + 1;
    }
    return true;
}

// 0 to 1 with at most three decimals: `0`, `.5`, `0.25`, `1`, `1.0`.
bool token_opacity(std::string_view v) {
    size_t i = 0;
    if (v.empty()) return false;
    if (v[0] == '1') {
        i = 1;
        if (i < v.size() && v[i] == '.') {
            size_t frac = ++i;
            while (i < v.size() && v[i] == '0' && i - frac < 3) i++;
            if (i == frac) return false;
        }
        return i == v.size();
    }
    if (v[0] == '0') i = 1;
    if (i < v.size() && v[i] == '.') {
        size_t frac = ++i;
        while (i < v.size() && is_digit(v[i]) && i - frac < 3) i++;
        if (i == frac) return false;
    }
    return i > 0 && i == v.size();
}

std::optional<std::string> button_token(std::string_view field, const twidget::Json& value) {
    if (field == "pressedOpacity" || field == "disabledOpacity") {
        std::string s = value.isNumber() ? twidget::Json::number(value.asNumber()) : value.asString();
        return token_opacity(s) ? std::optional(s) : std::nullopt;
    }
    if (!value.isString()) return std::nullopt;
    const std::string& v = value.asString();
    if (field == "bg" || field == "text") return hex_color(v) ? std::optional(lower(v)) : std::nullopt;
    if (field == "radius" || field == "height") return token_length(v) ? std::optional(v) : std::nullopt;
    if (field == "font") return token_font(v) ? std::optional(v) : std::nullopt;
    // padding: one to four lengths, one space apart
    size_t parts = 0, start = 0;
    while (true) {
        size_t space = v.find(' ', start);
        if (++parts > 4 || !token_length(std::string_view(v).substr(start, space == std::string::npos ? std::string::npos : space - start))) return std::nullopt;
        if (space == std::string::npos) return v;
        start = space + 1;
    }
}

std::string theme_variable_name(const char* key) {
    std::string name = std::string("--tg-theme-") + key;
    std::replace(name.begin(), name.end(), '_', '-');
    return name;
}

} // namespace

std::vector<std::pair<std::string, std::string>> theme_variables(const twidget::Json& theme) {
    bool dark = theme["colorScheme"].asString() == "dark";
    const twidget::Json& params = theme["params"];
    std::vector<std::pair<std::string, std::string>> vars;
    vars.reserve(THEME_KEY_COUNT + 1 + std::size(BUTTON_VARS) + std::size(TEXT_VARS));
    for (size_t k = 0; k < THEME_KEY_COUNT; k++) {
        const std::string& v = params[THEME_KEYS[k]].asString();
        vars.emplace_back(theme_variable_name(THEME_KEYS[k]), hex_color(v) ? lower(v) : THEME_DEFAULTS[dark][k]);
    }
    vars.emplace_back("--tg-color-scheme", dark ? "dark" : "light");
    const twidget::Json& button = theme["button"];
    for (const auto& [field, name] : BUTTON_VARS) {
        if (const twidget::Json* value = button.find(field))
            if (auto token = button_token(field, *value)) vars.emplace_back(name, std::move(*token));
    }
    const twidget::Json& text = theme["text"];
    for (const auto& [field, name, fallback] : TEXT_VARS) {
        std::optional<std::string> token;
        if (const twidget::Json* value = text.find(field)) {
            std::string_view f = field;
            token = f == "fontSize" ? bounded(*value, 8, 64, 2, "px") : f == "lineHeight" ? bounded(*value, 1, 3, 3, "") : button_token("padding", *value);
        }
        vars.emplace_back(name, token ? std::move(*token) : std::string(fallback));
    }
    return vars;
}

namespace {

constexpr const char* SAFE_AREA_VARS[][2] = {
    {"top", "--tg-content-safe-area-inset-top"},
    {"right", "--tg-content-safe-area-inset-right"},
    {"bottom", "--tg-content-safe-area-inset-bottom"},
    {"left", "--tg-content-safe-area-inset-left"},
    {"cornerLeft", "--tg-content-safe-area-corner-left"},
    {"cornerRight", "--tg-content-safe-area-corner-right"},
};

} // namespace

std::vector<std::pair<std::string, std::string>> content_safe_area_variables(const twidget::Json& inset) {
    std::vector<std::pair<std::string, std::string>> vars;
    vars.reserve(std::size(SAFE_AREA_VARS));
    for (const auto& [field, name] : SAFE_AREA_VARS) {
        const twidget::Json& value = inset[field];
        double max = std::string_view(field).rfind("corner", 0) == 0 ? 200 : 64;
        double n = value.isNumber() ? value.asNumber() : 0;
        if (!std::isfinite(n) || n < 0 || n > max) n = 0;
        vars.emplace_back(name, twidget::Json::number(std::round(n * 100) / 100) + "px");
    }
    return vars;
}

bool is_theme_variable(std::string_view name) {
    if (name == "--tg-color-scheme") return true;
    for (const auto& [field, var] : SAFE_AREA_VARS)
        if (name == var) return true;
    for (const auto& [field, var] : BUTTON_VARS)
        if (name == var) return true;
    for (const auto& [field, var, fallback] : TEXT_VARS)
        if (name == var) return true;
    for (const char* key : THEME_KEYS)
        if (name == theme_variable_name(key)) return true;
    return false;
}

std::string substitute_variables(std::string_view css, const std::vector<std::pair<std::string, std::string>>& vars) {
    std::string out;
    out.reserve(css.size());
    size_t i = 0;
    while (i < css.size()) {
        size_t at = css.find("var(", i);
        if (at == std::string_view::npos) break;
        // The matching ')': a fallback may hold functions of its own, `rgba(…)`.
        size_t end = at + 4, comma = std::string_view::npos;
        int depth = 1;
        for (; end < css.size() && depth; end++) {
            if (css[end] == '(') depth++;
            else if (css[end] == ')') depth--;
            else if (css[end] == ',' && depth == 1 && comma == std::string_view::npos) comma = end;
        }
        if (depth) break;
        std::string name = trim(css.substr(at + 4, (comma == std::string_view::npos ? end - 1 : comma) - at - 4));
        out.append(css.substr(i, at - i));
        auto var = std::find_if(vars.begin(), vars.end(), [&](const auto& v) { return v.first == name; });
        if (var != vars.end()) out += var->second;
        else if (comma != std::string_view::npos) out.append(trim(css.substr(comma + 1, end - 2 - comma)));
        else out.append(css.substr(at, end - at));
        i = end;
    }
    out.append(css.substr(i));
    return out;
}

std::string unwrap_where(std::string_view css) {
    std::string out;
    out.reserve(css.size());
    size_t i = 0;
    while (i < css.size()) {
        size_t at = css.find(":where(", i);
        if (at == std::string_view::npos) break;
        size_t end = at + 7;
        int depth = 1;
        bool list = false;
        for (; end < css.size() && depth; end++) {
            if (css[end] == '(') depth++;
            else if (css[end] == ')') depth--;
            else if (css[end] == ',' && depth == 1) list = true;
        }
        if (depth) break;
        out.append(css.substr(i, at - i));
        // A list followed by more of its compound selector cannot be unwrapped: `:where(a,b).x` is not `a,b.x`.
        char next = end < css.size() ? css[end] : '{';
        if (list && next != '{' && next != ',') out.append(css.substr(at, end - at));
        else out.append(css.substr(at + 7, end - 1 - (at + 7)));
        i = end;
    }
    out.append(css.substr(i));
    return out;
}

} // namespace twlite::css
