#include "paint_policy.h"

#include <cctype>
#include <cmath>
#include <cstdlib>
#include <limits>

namespace twlite::paint {

namespace {

std::string_view trim(std::string_view s) {
    while (!s.empty() && std::isspace(static_cast<unsigned char>(s.front()))) s.remove_prefix(1);
    while (!s.empty() && std::isspace(static_cast<unsigned char>(s.back()))) s.remove_suffix(1);
    return s;
}

bool equals_lower(std::string_view s, std::string_view lower) {
    if (s.size() != lower.size()) return false;
    for (size_t i = 0; i < s.size(); i++)
        if (std::tolower(static_cast<unsigned char>(s[i])) != lower[i]) return false;
    return true;
}

bool is_none(std::string_view v) {
    v = trim(v);
    return v.empty() || equals_lower(v, "none");
}

/// The comma-separated layers of a list value, commas inside parentheses kept; empty layers dropped.
template <class Visit> void layers(std::string_view value, Visit visit) {
    int depth = 0;
    size_t from = 0;
    for (size_t i = 0; i <= value.size(); i++) {
        char c = i < value.size() ? value[i] : ',';
        if (c == '(') depth++;
        else if (c == ')') depth = depth > 0 ? depth - 1 : 0;
        else if (c == ',' && (depth == 0 || i == value.size())) {
            std::string_view layer = trim(value.substr(from, i - from));
            if (!layer.empty()) visit(layer);
            from = i + 1;
        }
    }
}

/// A CSS length token: a number with an optional exponent, then any unit letters or %.
bool length_token(std::string_view t, double& out) {
    size_t i = 0;
    if (i < t.size() && (t[i] == '+' || t[i] == '-')) i++;
    size_t digits = 0;
    while (i < t.size() && std::isdigit(static_cast<unsigned char>(t[i]))) i++, digits++;
    if (i < t.size() && t[i] == '.') {
        i++;
        while (i < t.size() && std::isdigit(static_cast<unsigned char>(t[i]))) i++, digits++;
    }
    if (!digits) return false;
    if (i < t.size() && (t[i] == 'e' || t[i] == 'E')) {
        size_t j = i + 1;
        if (j < t.size() && (t[j] == '+' || t[j] == '-')) j++;
        size_t exp = j;
        while (j < t.size() && std::isdigit(static_cast<unsigned char>(t[j]))) j++;
        if (j > exp) i = j;
    }
    for (size_t j = i; j < t.size(); j++)
        if (!std::isalpha(static_cast<unsigned char>(t[j])) && t[j] != '%') return false;
    out = std::strtod(std::string(t).c_str(), nullptr);
    return true;
}

/// Whether a shadow layer's third length, its blur radius, is above 0; a math function is a length of unknown size.
bool layer_blurred(std::string_view layer) {
    int found = 0;
    size_t i = 0;
    while (i < layer.size()) {
        if (std::isspace(static_cast<unsigned char>(layer[i]))) {
            i++;
            continue;
        }
        size_t j = i;
        while (j < layer.size() && !std::isspace(static_cast<unsigned char>(layer[j])) && layer[j] != '(') j++;
        std::string_view token = layer.substr(i, j - i);
        double length = 0;
        if (j < layer.size() && layer[j] == '(') {
            // a colour function is skipped, a math one is a length
            int depth = 0;
            size_t k = j;
            for (; k < layer.size(); k++) {
                if (layer[k] == '(') depth++;
                else if (layer[k] == ')' && --depth == 0) {
                    k++;
                    break;
                }
            }
            if (equals_lower(token, "calc") || equals_lower(token, "min") || equals_lower(token, "max") || equals_lower(token, "clamp")) {
                if (++found == 3) return true;
            }
            i = k;
            continue;
        }
        if (length_token(token, length) && ++found == 3) return !(length <= 0);
        i = j;
    }
    return false;
}

/// Rule 4: a number or a percentage below 1; what is no number is 1.
bool transparent(std::string_view v) {
    v = trim(v);
    if (v.empty()) return false;
    std::string text(v);
    char* end = nullptr;
    double n = std::strtod(text.c_str(), &end);
    if (end == text.c_str()) return false;
    if (!text.empty() && text.back() == '%') n /= 100;
    return n < 1;
}

} // namespace

bool box_shadow_blurred(std::string_view value) {
    if (is_none(value)) return false;
    bool blurred = false;
    layers(value, [&](std::string_view layer) { blurred = blurred || layer_blurred(layer); });
    return blurred;
}

std::string box_shadow_without_blur(std::string_view value) {
    if (is_none(value)) return "none";
    std::string out;
    layers(value, [&](std::string_view layer) {
        if (layer_blurred(layer)) return;
        if (!out.empty()) out += ", ";
        out += layer;
    });
    return out.empty() ? "none" : out;
}

bool hides_subtree(std::string_view display) { return equals_lower(trim(display), "none"); }

std::optional<BoxPaint> box_paint(const Style& style, bool pseudo) {
    std::string_view display = trim(style.display);
    if (equals_lower(display, "none") || (!pseudo && equals_lower(display, "contents"))) return std::nullopt;
    if (pseudo) {
        std::string_view content = trim(style.content);
        if (content.empty() || equals_lower(content, "none") || equals_lower(content, "normal")) return std::nullopt;
    }
    BoxPaint out;
    out.backdrop = !is_none(style.backdrop_filter);
    bool dimmed = !pseudo && style.button != Style::Button::None;
    out.heavy = out.backdrop || !is_none(style.filter) || (!dimmed && transparent(style.opacity)) || box_shadow_blurred(style.box_shadow);
    return out;
}

void AnimationPaint::property(std::string_view name, std::optional<std::string_view> value) {
    if (equals_lower(name, "offset") || equals_lower(name, "computedoffset") || equals_lower(name, "easing") || equals_lower(name, "composite")) return;
    // camelCase (as getKeyframes names them) to the CSS name, the vendor prefix dropped
    std::string css;
    css.reserve(name.size() + 4);
    for (char c : name) {
        if (std::isupper(static_cast<unsigned char>(c))) css.push_back('-');
        css.push_back(char(std::tolower(static_cast<unsigned char>(c))));
    }
    std::string_view n = css;
    for (std::string_view prefix : {"-webkit-", "-moz-", "-ms-", "-o-"}) {
        if (n.substr(0, prefix.size()) == prefix) {
            n.remove_prefix(prefix.size());
            break;
        }
    }
    if (n == "backdrop-filter") heavy_ = backdrop_ = true;
    else if (n == "filter" || n == "opacity") heavy_ = true;
    else if (n == "box-shadow") {
        shadow_ = true;
        if (!value || box_shadow_blurred(*value)) shadow_blurred_ = true;
        if (at_ && *at_ == 0) shadow_first_ = true;
        if (at_ && *at_ == 1) shadow_last_ = true;
    }
}

std::optional<BoxPaint> AnimationPaint::result() const {
    bool heavy = heavy_ || (shadow_ && (shadow_blurred_ || !shadow_first_ || !shadow_last_));
    if (!heavy) return std::nullopt;
    return BoxPaint{true, backdrop_};
}

} // namespace twlite::paint
