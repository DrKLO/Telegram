#include "animations.h"

#include "css.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <limits>
#include <set>

using twidget::Json;

namespace twlite {

namespace {

constexpr double INF = std::numeric_limits<double>::infinity();

bool is_digit(char c) { return c >= '0' && c <= '9'; }
bool is_letter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }

const std::unordered_map<std::string, std::string>& initial_values() {
    static const std::unordered_map<std::string, std::string> v = {
        {"opacity", "1"}, {"transform", "none"}, {"translate", "0px"}, {"scale", "1"}, {"rotate", "0deg"}, {"filter", "none"}, {"letter-spacing", "0px"},
    };
    return v;
}

const std::string* initial_value(const std::string& property) {
    auto it = initial_values().find(property);
    return it == initial_values().end() ? nullptr : &it->second;
}

void merge(StyleValues& into, const std::string& property, std::string value) {
    into.erase(std::remove_if(into.begin(), into.end(), [&](const auto& p) { return p.first == property; }), into.end());
    into.emplace_back(property, std::move(value));
}

const std::string* last_value(const StyleValues& values, const std::string& property) {
    for (auto it = values.rbegin(); it != values.rend(); ++it)
        if (it->first == property) return &it->second;
    return nullptr;
}

double bezier_at(double x1, double y1, double x2, double y2, double x) {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    double cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
    double cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
    auto sample_x = [&](double t) { return ((ax * t + bx) * t + cx) * t; };
    auto sample_y = [&](double t) { return ((ay * t + by) * t + cy) * t; };
    auto derivative_x = [&](double t) { return (3 * ax * t + 2 * bx) * t + cx; };
    double t = x;
    for (int i = 0; i < 8; i++) {
        double error = sample_x(t) - x;
        if (std::fabs(error) < 1e-6) return sample_y(t);
        double d = derivative_x(t);
        if (std::fabs(d) < 1e-6) break;
        t -= error / d;
    }
    double lo = 0, hi = 1;
    t = x;
    for (int i = 0; i < 30; i++) {
        double value = sample_x(t);
        if (std::fabs(value - x) < 1e-6) break;
        if (value < x) lo = t; else hi = t;
        t = (lo + hi) / 2;
    }
    return sample_y(t);
}

std::optional<Timing> linear_function(std::string_view body) {
    struct Point { double output; std::optional<double> input; };
    std::vector<Point> points;
    for (const std::string& item : css::split_top_level(body, ',')) {
        std::vector<std::string> parts;
        for (const std::string& p : css::split_top_level(item, ' '))
            if (!css::trim(p).empty()) parts.push_back(css::trim(p));
        if (parts.empty()) return std::nullopt;
        auto output = css::number(parts[0]);
        if (!output) return std::nullopt;
        if (parts.size() == 1) points.push_back({*output, std::nullopt});
        for (size_t i = 1; i < parts.size(); i++) {
            if (!css::ends_with(parts[i], "%")) return std::nullopt;
            auto input = css::number(std::string_view(parts[i]).substr(0, parts[i].size() - 1));
            if (!input) return std::nullopt;
            points.push_back({*output, *input / 100});
        }
        if (points.size() > 10000) return std::nullopt;
    }
    Timing t;
    t.kind = Timing::Points;
    if (points.empty()) return std::nullopt;
    if (points.size() == 1) {
        t.points = {{0, points[0].output}, {1, points[0].output}};
        return t;
    }
    if (!points[0].input) points[0].input = 0;
    if (!points.back().input) {
        double largest = 1;
        for (const auto& p : points) if (p.input) largest = std::max(largest, *p.input);
        points.back().input = largest;
    }
    double largest = *points[0].input;
    for (auto& p : points) {
        if (p.input) {
            largest = std::max(largest, *p.input);
            p.input = largest;
        }
    }
    for (size_t i = 0; i < points.size(); i++) {
        if (points[i].input) continue;
        size_t start = i - 1, end = i;
        while (!points[end].input) end++;
        double from = *points[start].input, to = *points[end].input;
        for (size_t k = start + 1; k < end; k++) points[k].input = from + (to - from) * double(k - start) / double(end - start);
        i = end;
    }
    for (const auto& p : points) t.points.emplace_back(*p.input, p.output);
    return t;
}

// ── interpolation tokens ──

struct Token {
    bool number = false;
    double value = 0;
    std::string text;  // the unit of a number, or the text between numbers
};

std::vector<Token> tokenize(std::string_view s) {
    std::vector<Token> tokens;
    std::string pending;
    auto flush = [&] {
        if (!pending.empty()) tokens.push_back(Token{false, 0, std::move(pending)});
        pending.clear();
    };
    size_t i = 0, n = s.size();
    while (i < n) {
        char c = s[i];
        bool starts = is_digit(c) || (c == '.' && i + 1 < n && is_digit(s[i + 1])) || ((c == '-' || c == '+') && i + 1 < n && (is_digit(s[i + 1]) || s[i + 1] == '.'));
        char previous = i > 0 ? s[i - 1] : 0;
        bool ident_part = i > 0 && (is_letter(previous) || (previous == '-' && i > 1 && is_letter(s[i - 2])));
        if (starts && !(ident_part && c != '-') && previous != '#') {
            size_t j = i + 1;
            while (j < n && (is_digit(s[j]) || s[j] == '.' || ((s[j] == 'e' || s[j] == 'E') && j + 1 < n && (is_digit(s[j + 1]) || s[j + 1] == '-')))) {
                if (s[j] == 'e' || s[j] == 'E') j++;
                j++;
            }
            size_t k = j;
            while (k < n && (is_letter(s[k]) || s[k] == '%')) k++;
            if (auto v = css::number(s.substr(i, j - i))) {
                flush();
                tokens.push_back(Token{true, *v, std::string(s.substr(j, k - j))});
                i = k;
                continue;
            }
        }
        pending.push_back(c);
        i++;
    }
    flush();
    return tokens;
}

/// The functions of a transform list: (name lowercased, whole `name(args)` text).
std::vector<std::pair<std::string, std::string>> functions(std::string_view transform, std::string* rest_out = nullptr) {
    std::vector<std::pair<std::string, std::string>> out;
    size_t pos = 0;
    for (;;) {
        size_t open = transform.find('(', pos);
        if (open == std::string_view::npos) break;
        size_t close = transform.find(')', open);
        if (close == std::string_view::npos) break;
        out.emplace_back(css::lower(css::trim(transform.substr(pos, open - pos))), css::trim(transform.substr(pos, close + 1 - pos)));
        pos = close + 1;
    }
    if (rest_out) *rest_out = std::string(transform.substr(pos));
    return out;
}

/// Each function's arguments joined back as `name(a, b)`.
std::vector<std::string> arguments(const std::string& fn) {
    size_t open = fn.find('('), close = fn.rfind(')');
    std::vector<std::string> out;
    if (open == std::string::npos || close == std::string::npos || close < open) return out;
    for (const std::string& a : css::split_top_level(std::string_view(fn).substr(open + 1, close - open - 1), ',')) out.push_back(css::trim(a));
    return out;
}

/// One-argument shorthands rewritten to their two-argument forms, so `translateX(4px)` pairs with `translate(0, 8px)`.
std::optional<std::string> primitive(std::string_view transform) {
    std::string rest;
    auto list = functions(transform, &rest);
    if (list.empty() || !css::trim(rest).empty()) return std::nullopt;
    std::string out;
    for (const auto& [name, whole] : list) {
        auto args = arguments(whole);
        std::string item;
        if (args.size() == 1) {
            const std::string& a = args[0];
            if (name == "translatex" || name == "translate") item = "translate(" + a + ", 0)";
            else if (name == "translatey") item = "translate(0, " + a + ")";
            else if (name == "scalex") item = "scale(" + a + ", 1)";
            else if (name == "scaley") item = "scale(1, " + a + ")";
            else if (name == "scale") item = "scale(" + a + ", " + a + ")";
            else if (name == "skewx" || name == "skew") item = "skew(" + a + ", 0)";
            else if (name == "skewy") item = "skew(0, " + a + ")";
        }
        if (item.empty()) {
            item = name + "(";
            for (size_t i = 0; i < args.size(); i++) item += (i ? ", " : "") + args[i];
            item += ")";
        }
        if (!out.empty()) out += ' ';
        out += item;
    }
    return out;
}

/// The identity of each function in a transform or filter list: `rotate(30deg)` → `rotate(0deg)`.
std::string identity(std::string_view transform) {
    static const std::set<std::string> ones = {"scale", "scalex", "scaley", "scale3d", "brightness", "contrast", "opacity", "saturate"};
    auto list = functions(transform);
    if (list.empty()) return std::string(transform);
    std::string out;
    for (const auto& [name, whole] : list) {
        size_t open = whole.find('('), close = whole.rfind(')');
        std::string original = whole.substr(0, open);
        std::string inner = whole.substr(open + 1, close - open - 1);
        std::string args;
        bool first = true;
        for (const std::string& arg : css::split_top_level(inner, ',')) {
            size_t lead = 0;
            while (lead < arg.size() && (arg[lead] == ' ' || arg[lead] == '\t')) lead++;
            std::string value;
            if (ones.count(name)) value = "1";
            else {
                std::string t = css::trim(arg);
                size_t u = 0;
                while (u < t.size() && (is_digit(t[u]) || t[u] == '.' || t[u] == '-' || t[u] == '+')) u++;
                value = "0" + t.substr(u);
            }
            args += (first ? "" : ",") + arg.substr(0, lead) + value;
            first = false;
        }
        out += (out.empty() ? "" : " ") + original + "(" + args + ")";
    }
    return out;
}

/// A shorter transform list padded with the identities of the longer one's extra functions.
std::optional<std::pair<std::string, std::string>> padded(const std::string& a, const std::string& b) {
    auto x = functions(a), y = functions(b);
    bool a_short = x.size() < y.size();
    const auto& shorter = a_short ? x : y;
    const auto& longer = a_short ? y : x;
    if (shorter.empty() && longer.empty()) return std::nullopt;
    for (size_t i = 0; i < shorter.size(); i++)
        if (shorter[i].first != longer[i].first) return std::nullopt;
    std::string extra;
    for (size_t i = shorter.size(); i < longer.size(); i++) extra += (extra.empty() ? "" : " ") + identity(longer[i].second);
    if (a_short) return std::make_pair(a + " " + extra, b);
    return std::make_pair(a, b + " " + extra);
}

bool translation_list(std::string_view text) {
    std::string t = css::lower(css::trim(text));
    std::string rest;
    auto list = functions(t, &rest);
    if (list.empty() || !css::trim(rest).empty()) return false;
    for (const auto& [name, whole] : list) {
        if (name != "translate" && name != "translatex" && name != "translatey") return false;
        size_t open = whole.find('(');
        if (whole.find('(', open + 1) != std::string::npos) return false;
    }
    return true;
}

std::string scaled_tokens(std::string_view text, double factor) {
    std::string out;
    for (const Token& t : tokenize(text)) out += t.number ? css::format_number(t.value * factor) + t.text : t.text;
    return out;
}

std::optional<std::pair<std::vector<Token>, std::vector<Token>>> pair_tokens(const std::string& a, const std::string& b) {
    thread_local std::unordered_map<std::string, std::optional<std::pair<std::vector<Token>, std::vector<Token>>>> cache;
    std::string key = a + '\x01' + b;
    if (auto it = cache.find(key); it != cache.end()) return it->second;
    std::string from = css::normalize_colors(a), to = css::normalize_colors(b);
    if (css::iequals(from, "none") && to.find('(') != std::string::npos) from = identity(to);
    else if (css::iequals(to, "none") && from.find('(') != std::string::npos) to = identity(from);
    auto left = tokenize(from), right = tokenize(to);
    if (left.size() != right.size()) {
        auto pa = primitive(from), pb = primitive(to);
        if (pa && pb) {
            left = tokenize(*pa);
            right = tokenize(*pb);
            if (left.size() != right.size()) {
                if (auto p = padded(*pa, *pb)) {
                    left = tokenize(p->first);
                    right = tokenize(p->second);
                }
            }
        }
    }
    std::optional<std::pair<std::vector<Token>, std::vector<Token>>> result;
    if (left.size() == right.size()) result = std::make_pair(std::move(left), std::move(right));
    if (cache.size() >= 2048) cache.clear();
    if (key.size() <= 4096) cache.emplace(std::move(key), result);
    return result;
}

// ── Web Animations composite (add / accumulate) ──

std::vector<Token> web_tokenize(std::string_view s) {
    std::vector<Token> tokens;
    std::string pending;
    size_t i = 0, n = s.size();
    while (i < n) {
        char c = s[i];
        char next = i + 1 < n ? s[i + 1] : 0;
        char previous = i > 0 ? s[i - 1] : 0;
        bool starts = is_digit(c) || (c == '.' && is_digit(next)) || ((c == '-' || c == '+') && (is_digit(next) || next == '.'));
        bool in_word = i > 0 && (is_letter(previous) || previous == '#' || (previous == '-' && c != '-'));
        if (starts && !in_word) {
            size_t j = i + 1;
            while (j < n && (is_digit(s[j]) || s[j] == '.' || ((s[j] == 'e' || s[j] == 'E') && j + 1 < n && (is_digit(s[j + 1]) || s[j + 1] == '-'))))
                j += (s[j] == 'e' || s[j] == 'E') ? 2 : 1;
            size_t k = j;
            while (k < n && (is_letter(s[k]) || s[k] == '%')) k++;
            if (auto v = css::number(s.substr(i, j - i))) {
                if (!pending.empty()) tokens.push_back(Token{false, 0, std::move(pending)});
                pending.clear();
                tokens.push_back(Token{true, *v, std::string(s.substr(j, k - j))});
                i = k;
                continue;
            }
        }
        pending.push_back(c);
        i++;
    }
    if (!pending.empty()) tokens.push_back(Token{false, 0, std::move(pending)});
    return tokens;
}

std::string web_render(const std::vector<Token>& tokens) {
    std::string out;
    for (const Token& t : tokens) out += t.number ? css::format_number(t.value, 6) + t.text : t.text;
    return out;
}

/// The function a text token opens: `"rotate("` → `rotate`, `") scale("` → `scale`.
std::optional<std::string> opened_function(const std::string& text) {
    size_t open = text.rfind('(');
    if (open == std::string::npos) return std::nullopt;
    std::string before = text.substr(0, open);
    size_t start = before.find_last_of(" ,");
    return css::lower(start == std::string::npos ? before : before.substr(start + 1));
}

bool unit_scale(const std::string& fn) { return fn == "scale" || fn == "scalex" || fn == "scaley" || fn == "scale3d"; }

std::optional<std::string> web_combine(const std::string& a, const std::string& b, bool scale_aware) {
    auto left = web_tokenize(css::normalize_colors(a)), right = web_tokenize(css::normalize_colors(b));
    if (left.size() != right.size()) return std::nullopt;
    std::vector<Token> out;
    std::string function;
    int color_channel = -1;
    for (size_t i = 0; i < left.size(); i++) {
        const Token &x = left[i], &y = right[i];
        if (!x.number && !y.number && css::iequals(x.text, y.text)) {
            out.push_back(x);
            if (auto fn = opened_function(x.text)) {
                function = *fn;
                color_channel = function == "rgba" ? 0 : -1;
            } else if (color_channel >= 0) color_channel++;
        } else if (x.number && y.number && (x.text == y.text || x.value == 0 || y.value == 0)) {
            double value = x.value + y.value;
            if (scale_aware && unit_scale(function)) value = x.value + y.value - 1;
            if (color_channel >= 0) value = color_channel < 3 ? std::clamp(value, 0.0, 255.0) : std::clamp(value, 0.0, 1.0);
            out.push_back(Token{true, value, x.text.empty() ? y.text : x.text});
        } else return std::nullopt;
    }
    return web_render(out);
}

std::string web_add(const std::string& underlying, const std::string& value, const std::string& property, bool accumulate) {
    if (property == "transform") {
        std::string u = css::trim(underlying), v = css::trim(value);
        if (u.empty() || css::iequals(u, "none")) return v;
        if (v.empty() || css::iequals(v, "none")) return u;
        if (accumulate) {
            if (auto c = web_combine(u, v, true)) return *c;
        }
        return u + " " + v;
    }
    if (auto c = web_combine(underlying, value, false)) return *c;
    return value;
}

std::string web_scaled(const std::string& value, double factor) {
    std::string function;
    auto tokens = web_tokenize(css::normalize_colors(value));
    for (Token& t : tokens) {
        if (!t.number) {
            if (auto fn = opened_function(t.text)) function = *fn;
        } else if (unit_scale(function)) t.value = (t.value - 1) * factor + 1;
        else t.value *= factor;
    }
    return web_render(tokens);
}

// ── Web Animations timing ──

struct Computed {
    bool before = false, after = false;
    std::optional<double> active_time;
    double progress = 0;
    double iteration = 0;
};

std::optional<Computed> compute(const WebAnimation& a, std::optional<double> local_time, bool forwards) {
    if (!local_time || !std::isfinite(*local_time)) return std::nullopt;
    double local = *local_time;
    double active = (a.duration == 0 || a.iterations == 0) ? 0 : a.duration * a.iterations;
    double end = std::max(a.delay + active + a.end_delay, 0.0);
    double before_active = std::max(std::min(a.delay, end), 0.0);
    double active_after = std::max(std::min(a.delay + active, end), 0.0);
    bool fills_backwards = a.fill == "backwards" || a.fill == "both";
    bool fills_forwards = a.fill == "forwards" || a.fill == "both";
    Computed r;
    if (local < before_active || (!forwards && local == before_active)) r.before = true;
    else if (local > active_after || (forwards && local == active_after)) r.after = true;
    if (r.before) r.active_time = fills_backwards ? std::optional<double>(std::max(local - a.delay, 0.0)) : std::nullopt;
    else if (r.after) r.active_time = fills_forwards ? std::optional<double>(std::max(std::min(local - a.delay, active), 0.0)) : std::nullopt;
    else r.active_time = local - a.delay;
    if (!r.active_time) return r;
    double active_time = *r.active_time;
    double overall = a.duration == 0 ? (r.before ? 0 : a.iterations) : active_time / a.duration;
    overall += a.iteration_start;
    double simple = std::isinf(overall) ? std::fmod(a.iteration_start, 1.0) : std::fmod(overall, 1.0);
    if (simple == 0 && !r.before && active_time == active && a.iterations != 0) simple = 1;
    double iteration;
    if (r.after && std::isinf(a.iterations)) iteration = INF;
    else if (simple == 1) iteration = std::floor(overall) - 1;
    else iteration = std::floor(overall);
    r.iteration = iteration;
    bool forwards_direction = true;
    if (a.direction == "reverse") forwards_direction = false;
    else if ((a.direction == "alternate" || a.direction == "alternate-reverse") && std::isfinite(iteration)) {
        double d = iteration + (a.direction == "alternate-reverse" ? 1 : 0);
        forwards_direction = std::fmod(d, 2.0) == 0;
    }
    double directed = forwards_direction ? simple : 1 - simple;
    bool before_flag = (forwards_direction && r.before) || (!forwards_direction && r.after);
    r.progress = a.easing.web(directed, before_flag);
    return r;
}

std::optional<double> local_time(const WebAnimation& a, double now) {
    if (a.hold_ms) return *a.hold_ms;
    if (!a.start_ms) return std::nullopt;
    return (now * 1000 - *a.start_ms) * a.rate;
}

std::optional<std::string> web_value(const WebAnimation& a, const std::string& property, double progress, double iteration,
                                     const std::function<std::string(const std::string&)>& underlying) {
    struct F {
        double offset;
        const std::string* value;
        const Timing* easing;
        std::string composite;
    };
    static const Timing LINEAR;
    std::vector<F> frames;
    for (const auto& frame : a.frames) {
        if (const std::string* v = last_value(frame.values, property))
            frames.push_back({frame.offset, v, &frame.easing, frame.composite.empty() ? a.composite : frame.composite});
    }
    if (frames.empty()) return std::nullopt;
    if (std::none_of(frames.begin(), frames.end(), [](const F& f) { return f.offset == 0; })) frames.insert(frames.begin(), {0, nullptr, &LINEAR, "replace"});
    if (std::none_of(frames.begin(), frames.end(), [](const F& f) { return f.offset == 1; })) frames.push_back({1, nullptr, &LINEAR, "replace"});
    std::string base = underlying(property);
    const std::string* last = nullptr;
    for (auto it = frames.rbegin(); it != frames.rend(); ++it)
        if (it->offset == 1) { last = it->value; break; }
    auto resolve = [&](size_t index) -> std::string {
        const F& frame = frames[index];
        if (!frame.value) return base;
        std::string value = *frame.value;
        if (a.iteration_composite == "accumulate" && iteration > 0 && std::isfinite(iteration) && last)
            value = web_add(web_scaled(*last, iteration), value, property, true);
        if (frame.composite == "add") return web_add(base, value, property, false);
        if (frame.composite == "accumulate") return web_add(base, value, property, true);
        return value;
    };
    std::vector<size_t> zeros, ones;
    for (size_t i = 0; i < frames.size(); i++) {
        if (frames[i].offset == 0) zeros.push_back(i);
        if (frames[i].offset == 1) ones.push_back(i);
    }
    if (progress < 0 && zeros.size() > 1) return resolve(zeros.front());
    if (progress >= 1 && ones.size() > 1) return resolve(ones.back());
    size_t start = 0;
    if (progress < 0) start = 0;
    else if (progress >= 1) start = frames.size() - 2;
    else {
        for (size_t i = 0; i < frames.size(); i++)
            if (frames[i].offset <= progress && frames[i].offset < 1) start = i;
    }
    size_t end = std::min(start + 1, frames.size() - 1);
    if (start == end) return resolve(start);
    const F &fa = frames[start], &fb = frames[end];
    double span = fb.offset - fa.offset;
    double local = span == 0 ? 0 : (progress - fa.offset) / span;
    return interpolate(resolve(start), resolve(end), fa.easing->web(local, false));
}

} // namespace

// ───────────────────────── Timing ─────────────────────────

Timing Timing::ease() {
    Timing t;
    t.kind = Bezier;
    t.x1 = 0.25; t.y1 = 0.1; t.x2 = 0.25; t.y2 = 1;
    t.text = "ease";
    return t;
}

std::optional<Timing> Timing::parse(std::string_view raw) {
    std::string t = css::lower(css::trim(raw));
    Timing out;
    out.text = t;
    auto bezier = [&](double a, double b, double c, double d) {
        out.kind = Bezier;
        out.x1 = a; out.y1 = b; out.x2 = c; out.y2 = d;
        return out;
    };
    if (t == "linear") { out.kind = Linear; return out; }
    if (t == "ease") return bezier(0.25, 0.1, 0.25, 1);
    if (t == "ease-in") return bezier(0.42, 0, 1, 1);
    if (t == "ease-out") return bezier(0, 0, 0.58, 1);
    if (t == "ease-in-out") return bezier(0.42, 0, 0.58, 1);
    if (t == "step-start" || t == "step-end") {
        out.kind = Steps;
        out.steps = 1;
        out.jump = t == "step-start" ? JumpStart : JumpEnd;
        return out;
    }
    if (css::starts_with(t, "cubic-bezier(") && css::ends_with(t, ")")) {
        auto parts = css::split_top_level(std::string_view(t).substr(13, t.size() - 14), ',');
        if (parts.size() != 4) return std::nullopt;
        double v[4];
        for (int i = 0; i < 4; i++) {
            auto n = css::number(parts[i]);
            if (!n) return std::nullopt;
            v[i] = *n;
        }
        return bezier(v[0], v[1], v[2], v[3]);
    }
    if (css::starts_with(t, "steps(") && css::ends_with(t, ")")) {
        auto parts = css::split_top_level(std::string_view(t).substr(6, t.size() - 7), ',');
        if (parts.empty() || parts.size() > 2) return std::nullopt;
        auto n = css::number(parts[0]);
        if (!n || *n < 1 || *n != std::floor(*n) || *n > 100000) return std::nullopt;
        std::string position = parts.size() > 1 ? css::trim(parts[1]) : "end";
        out.kind = Steps;
        out.steps = int(*n);
        if (position == "start" || position == "jump-start") out.jump = JumpStart;
        else if (position == "end" || position == "jump-end") out.jump = JumpEnd;
        else if (position == "jump-both") out.jump = JumpBoth;
        else if (position == "jump-none") out.jump = JumpNone;
        else return std::nullopt;
        return out;
    }
    if (css::starts_with(t, "linear(") && css::ends_with(t, ")")) {
        auto f = linear_function(std::string_view(t).substr(7, t.size() - 8));
        if (f) f->text = t;
        return f;
    }
    return std::nullopt;
}

double Timing::css(double x) const {
    switch (kind) {
    case Linear: return x;
    case Bezier: return bezier_at(x1, y1, x2, y2, x);
    case Steps: {
        double n = steps;
        double step = jump == JumpStart ? std::ceil(x * n) : std::floor(x * n);
        return std::clamp(step / n, 0.0, 1.0);
    }
    case Points: {
        const auto& p = points;
        auto segment = [&](size_t a, size_t b) {
            if (p[b].first == p[a].first) return x <= p[a].first ? p[a].second : p[b].second;
            return p[a].second + (p[b].second - p[a].second) * (x - p[a].first) / (p[b].first - p[a].first);
        };
        if (x <= p[0].first) return p[1].first == p[0].first ? p[0].second : segment(0, 1);
        for (size_t k = 1; k < p.size(); k++) {
            if (x <= p[k].first) return p[k].first == p[k - 1].first ? p[k].second : segment(k - 1, k);
        }
        size_t n = p.size();
        return p[n - 1].first == p[n - 2].first ? p[n - 1].second : segment(n - 2, n - 1);
    }
    }
    return x;
}

double Timing::web(double x, bool before) const {
    if (kind == Steps) {
        double n = steps;
        double step = std::floor(x * n);
        if (jump == JumpStart || jump == JumpBoth) step += 1;
        if (before && std::fmod(x * n, 1.0) == 0) step -= 1;
        if (x >= 0 && step < 0) step = 0;
        double jumps = jump == JumpBoth ? n + 1 : jump == JumpNone ? n - 1 : n;
        if (x <= 1 && step > jumps) step = jumps;
        return step / jumps;
    }
    if (kind == Bezier) {
        // Outside [0, 1] a cubic-bézier continues along its end tangents.
        if (x < 0) {
            double slope = x1 > 0 ? y1 / x1 : (y1 == 0 && x2 > 0 ? y2 / x2 : 0);
            return slope * x;
        }
        if (x > 1) {
            double slope = x2 < 1 ? (y2 - 1) / (x2 - 1) : (y2 == 1 && x1 < 1 ? (y1 - 1) / (x1 - 1) : 0);
            return 1 + slope * (x - 1);
        }
    }
    return css(x);
}

// ───────────────────────── keyframes and specs ─────────────────────────

void parse_keyframes(std::string_view css_text, KeyframeRules& rules) {
    size_t pos = 0;
    for (;;) {
        size_t at = std::string_view::npos;
        for (size_t i = pos; i + 9 <= css_text.size(); i++) {
            if (css::iequals(css_text.substr(i, 9), "keyframes")) { at = i; break; }
        }
        if (at == std::string_view::npos) return;
        std::string_view before = css_text.substr(0, at);
        size_t open = css_text.find('{', at + 9);
        if (!(css::ends_with(before, "@") || css::ends_with(before, "@-webkit-")) || open == std::string_view::npos) {
            pos = at + 9;
            continue;
        }
        std::string name = css::trim(css_text.substr(at + 9, open - at - 9));
        while (!name.empty() && (name.front() == '"' || name.front() == '\'')) name.erase(name.begin());
        while (!name.empty() && (name.back() == '"' || name.back() == '\'')) name.pop_back();
        int depth = 0;
        size_t close = std::string_view::npos;
        for (size_t i = open; i < css_text.size(); i++) {
            if (css_text[i] == '{') depth++;
            else if (css_text[i] == '}' && --depth == 0) { close = i; break; }
        }
        if (close == std::string_view::npos) return;
        std::string_view body = css_text.substr(open + 1, close - open - 1);
        std::vector<Keyframe> frames;
        size_t p = 0;
        for (;;) {
            size_t fo = body.find('{', p);
            if (fo == std::string_view::npos) break;
            size_t fc = body.find('}', fo);
            if (fc == std::string_view::npos) break;
            StyleValues declarations;
            std::optional<Timing> timing;
            for (const auto& d : css::parse_declarations(body.substr(fo + 1, fc - fo - 1))) {
                if (d.name == "animation-timing-function") timing = Timing::parse(d.value);
                else declarations.emplace_back(d.name, d.value);
            }
            for (const std::string& raw : css::split_top_level(body.substr(p, fo - p), ',')) {
                std::string selector = css::lower(css::trim(raw));
                std::optional<double> offset;
                if (selector == "from") offset = 0;
                else if (selector == "to") offset = 1;
                else if (css::ends_with(selector, "%")) {
                    if (auto v = css::number(std::string_view(selector).substr(0, selector.size() - 1))) offset = *v / 100;
                }
                if (offset) frames.push_back(Keyframe{*offset, declarations, timing});
            }
            p = fc + 1;
        }
        std::stable_sort(frames.begin(), frames.end(), [](const Keyframe& a, const Keyframe& b) { return a.offset < b.offset; });
        rules[name] = std::move(frames);
        pos = close + 1;
    }
}

Timing AnimationSpec::timing() const {
    auto t = Timing::parse(timing_text);
    return t ? *t : Timing::ease();
}

std::vector<AnimationSpec> AnimationSpec::list(const Declared& declarations) {
    std::vector<AnimationSpec> specs;
    if (auto it = declarations.find("animation"); it != declarations.end()) {
        for (const std::string& item : css::split_top_level(it->second, ',')) {
            AnimationSpec spec;
            bool saw_duration = false;
            for (const std::string& token : css::split_top_level(css::trim(item), ' ')) {
                if (token.empty()) continue;
                std::string t = css::lower(token);
                if (auto time = css::parse_time(t)) {
                    if (saw_duration) spec.delay = *time;
                    else { spec.duration = *time; saw_duration = true; }
                } else if (Timing::parse(t)) spec.timing_text = t;
                else if (t == "infinite") spec.iterations = INF;
                else if (auto n = css::number(t)) spec.iterations = *n;
                else if (t == "normal" || t == "reverse" || t == "alternate" || t == "alternate-reverse") spec.direction = t;
                else if (t == "forwards" || t == "backwards" || t == "both") spec.fill = t;
                else if (t == "paused" || t == "running") spec.paused = t == "paused";
                else spec.name = token;
            }
            specs.push_back(std::move(spec));
        }
    }
    auto longhand = [&](const char* key, auto apply) {
        auto it = declarations.find(key);
        if (it == declarations.end()) return;
        std::vector<std::string> items;
        for (const std::string& s : css::split_top_level(it->second, ',')) items.push_back(css::trim(s));
        if (items.empty()) return;
        if (specs.empty()) specs.resize(items.size());
        for (size_t i = 0; i < specs.size(); i++) apply(specs[i], items[i % items.size()]);
    };
    longhand("animation-name", [](AnimationSpec& s, const std::string& v) { s.name = v; });
    longhand("animation-duration", [](AnimationSpec& s, const std::string& v) { s.duration = css::parse_time(v).value_or(0); });
    longhand("animation-timing-function", [](AnimationSpec& s, const std::string& v) { s.timing_text = v; });
    longhand("animation-delay", [](AnimationSpec& s, const std::string& v) { s.delay = css::parse_time(v).value_or(0); });
    longhand("animation-iteration-count", [](AnimationSpec& s, const std::string& v) { s.iterations = v == "infinite" ? INF : css::number(v).value_or(1); });
    longhand("animation-direction", [](AnimationSpec& s, const std::string& v) { s.direction = v; });
    longhand("animation-fill-mode", [](AnimationSpec& s, const std::string& v) { s.fill = v; });
    longhand("animation-play-state", [](AnimationSpec& s, const std::string& v) { s.paused = v == "paused"; });
    specs.erase(std::remove_if(specs.begin(), specs.end(), [](const AnimationSpec& s) { return s.name == "none" || s.name.empty(); }), specs.end());
    return specs;
}

std::vector<TransitionSpec> TransitionSpec::list(const Declared& declarations) {
    std::vector<TransitionSpec> specs;
    if (auto it = declarations.find("transition"); it != declarations.end()) {
        for (const std::string& item : css::split_top_level(it->second, ',')) {
            TransitionSpec spec;
            spec.property = "all";
            bool saw_duration = false;
            for (const std::string& token : css::split_top_level(css::trim(item), ' ')) {
                if (token.empty()) continue;
                if (auto time = css::parse_time(token)) {
                    if (saw_duration) spec.delay = *time;
                    else { spec.duration = *time; saw_duration = true; }
                } else if (auto timing = Timing::parse(token)) spec.timing = *timing;
                else spec.property = css::lower(token);
            }
            specs.push_back(std::move(spec));
        }
    }
    auto items = [&](const char* key) {
        std::vector<std::string> out;
        if (auto it = declarations.find(key); it != declarations.end())
            for (const std::string& s : css::split_top_level(it->second, ',')) out.push_back(css::trim(s));
        return out;
    };
    auto properties = items("transition-property");
    auto durations = items("transition-duration");
    if (!properties.empty()) {
        auto timings = items("transition-timing-function");
        auto delays = items("transition-delay");
        std::vector<TransitionSpec> next;
        for (size_t i = 0; i < properties.size(); i++) {
            TransitionSpec base = i < specs.size() ? specs[i] : TransitionSpec{properties[i], 0, Timing::ease(), 0};
            TransitionSpec s;
            s.property = css::lower(properties[i]);
            s.duration = durations.empty() ? base.duration : css::parse_time(durations[i % durations.size()]).value_or(0);
            s.timing = timings.empty() ? base.timing : Timing::parse(timings[i % timings.size()]).value_or(Timing::ease());
            s.delay = delays.empty() ? base.delay : css::parse_time(delays[i % delays.size()]).value_or(0);
            next.push_back(std::move(s));
        }
        specs = std::move(next);
    } else if (!durations.empty()) {
        for (size_t i = 0; i < specs.size(); i++) specs[i].duration = css::parse_time(durations[i % durations.size()]).value_or(0);
    }
    specs.erase(std::remove_if(specs.begin(), specs.end(), [](const TransitionSpec& s) { return s.duration <= 0 || s.property == "none"; }), specs.end());
    return specs;
}

// ───────────────────────── interpolation ─────────────────────────

std::string interpolate(const std::string& a, const std::string& b, double t) {
    if (t == 0 || !std::isfinite(t)) return a;
    if (t == 1) return b;
    auto flip = [&] { return t < 0.5 ? a : b; };
    auto pair = pair_tokens(a, b);
    if (!pair) return flip();
    std::string out;
    const auto& [left, right] = *pair;
    for (size_t i = 0; i < left.size(); i++) {
        const Token &x = left[i], &y = right[i];
        if (x.number && y.number && (x.text == y.text || x.value == 0 || y.value == 0)) {
            out += css::format_number(x.value + (y.value - x.value) * t);
            out += x.text.empty() ? y.text : x.text;
        } else if (!x.number && !y.number && x.text == y.text) {
            out += x.text;
        } else {
            // Mixed units or different functions: two translation lists still compose by weight.
            if (translation_list(a) && translation_list(b)) return scaled_tokens(css::trim(a), 1 - t) + " " + scaled_tokens(css::trim(b), t);
            return flip();
        }
    }
    return out;
}

// ───────────────────────── Web Animations records ─────────────────────────

std::optional<WebAnimation> WebAnimation::parse(const Json& o, double clock_offset_ms) {
    constexpr double MAX_MS = 86400.0 * 1000;
    constexpr size_t MAX_KEYFRAMES = 64, MAX_VALUES = 64;
    const Json* aid = o.find("aid");
    const Json* target = o.find("target");
    const Json* frames = o.find("keyframes");
    if (!aid || !aid->isNumber() || !target || !target->isNumber() || !frames || !frames->isArray() || frames->arr().size() > MAX_KEYFRAMES) return std::nullopt;
    WebAnimation a;
    a.aid = int64_t(aid->asNumber());
    a.target = int64_t(target->asNumber());
    const Json& timing = o["timing"];
    auto ms = [&](const char* key, double fallback) -> std::optional<double> {
        const Json* v = timing.find(key);
        if (!v || v->isNull()) return fallback;
        if (!v->isNumber() || !std::isfinite(v->asNumber()) || std::fabs(v->asNumber()) > MAX_MS) return std::nullopt;
        return v->asNumber();
    };
    auto duration = ms("duration", 0), delay = ms("delay", 0), end_delay = ms("endDelay", 0);
    if (!duration || *duration < 0 || !delay || !end_delay) return std::nullopt;
    a.duration = *duration;
    a.delay = *delay;
    a.end_delay = *end_delay;
    if (const Json* it = timing.find("iterations")) {
        if (it->isNull()) a.iterations = INF;
        else if (!it->isNumber() || it->asNumber() < 0 || it->asNumber() > 1e9) return std::nullopt;
        else a.iterations = it->asNumber();
    }
    if (const Json* start = timing.find("iterationStart"); start && start->isNumber()) {
        if (start->asNumber() < 0 || start->asNumber() > 1e9) return std::nullopt;
        a.iteration_start = start->asNumber();
    }
    std::string direction = timing["direction"].isString() ? timing["direction"].asString() : "normal";
    std::string fill = timing["fill"].isString() ? timing["fill"].asString() : "none";
    if (direction != "normal" && direction != "reverse" && direction != "alternate" && direction != "alternate-reverse") return std::nullopt;
    if (fill != "none" && fill != "forwards" && fill != "backwards" && fill != "both" && fill != "auto") return std::nullopt;
    a.direction = direction;
    a.fill = fill == "auto" ? "none" : fill;
    auto parse_easing = [](const Json& v) -> std::optional<Timing> {
        std::string text = v.isString() ? v.asString() : "linear";
        if (text.empty() || text.size() > 4096) return std::nullopt;
        auto t = Timing::parse(text);
        if (!t) return std::nullopt;
        // Stricter than CSS: control points stay in x ∈ [0, 1], and linear() is bounded.
        if (t->kind == Timing::Bezier && (t->x1 < 0 || t->x1 > 1 || t->x2 < 0 || t->x2 > 1)) return std::nullopt;
        if (t->kind == Timing::Steps && t->jump == Timing::JumpNone && t->steps < 2) return std::nullopt;
        if (t->kind == Timing::Points && t->points.size() > 256) return std::nullopt;
        return t;
    };
    auto easing = parse_easing(timing["easing"]);
    if (!easing) return std::nullopt;
    a.easing = *easing;
    double previous = 0;
    for (const Json& raw : frames->arr()) {
        const Json* offset = raw.find("offset");
        if (!offset || !offset->isNumber() || offset->asNumber() < previous || offset->asNumber() > 1) return std::nullopt;
        auto frame_easing = parse_easing(raw["easing"]);
        if (!frame_easing) return std::nullopt;
        previous = offset->asNumber();
        Frame f;
        f.offset = previous;
        f.easing = *frame_easing;
        if (const Json* c = raw.find("composite"); c && c->isString()) {
            if (c->asString() != "replace" && c->asString() != "add" && c->asString() != "accumulate") return std::nullopt;
            f.composite = c->asString();
        }
        const auto& values = raw["values"].obj();
        if (values.size() > MAX_VALUES) return std::nullopt;
        std::vector<std::pair<std::string, std::string>> sorted;
        for (const auto& [name, value] : values)
            if (value.isString()) sorted.emplace_back(name, value.asString());
        std::sort(sorted.begin(), sorted.end());
        for (auto& [name, value] : sorted) {
            if (std::find(a.properties.begin(), a.properties.end(), name) == a.properties.end()) a.properties.push_back(name);
            f.values.emplace_back(std::move(name), std::move(value));
        }
        a.frames.push_back(std::move(f));
    }
    a.composite = o["composite"].isString() ? o["composite"].asString() : "replace";
    a.iteration_composite = o["iterationComposite"].isString() ? o["iterationComposite"].asString() : "replace";
    if (a.composite != "replace" && a.composite != "add" && a.composite != "accumulate") return std::nullopt;
    if (a.iteration_composite != "replace" && a.iteration_composite != "accumulate") return std::nullopt;
    if (const Json* s = o.find("startTime"); s && s->isNumber()) {
        if (!std::isfinite(s->asNumber())) return std::nullopt;
        a.start_ms = s->asNumber() + clock_offset_ms;
    }
    if (const Json* h = o.find("holdTime"); h && h->isNumber()) {
        if (!std::isfinite(h->asNumber()) || std::fabs(h->asNumber()) > 1e12) return std::nullopt;
        a.hold_ms = h->asNumber();
    }
    a.rate = o["playbackRate"].isNumber() ? o["playbackRate"].asNumber() : 1;
    if (!std::isfinite(a.rate) || std::fabs(a.rate) > 1000 || (!a.start_ms && !a.hold_ms)) return std::nullopt;
    return a;
}

// ───────────────────────── Animator ─────────────────────────

const std::vector<std::string>& Animator::watched() {
    static const std::vector<std::string> names = {
        "animation", "animation-name", "animation-duration", "animation-timing-function", "animation-delay", "animation-iteration-count",
        "animation-direction", "animation-fill-mode", "animation-play-state", "transition", "transition-property", "transition-duration",
        "transition-timing-function", "transition-delay", "transform", "opacity", "color", "background-color", "background", "width", "height",
        "top", "left", "right", "bottom", "box-shadow", "border-color", "max-height", "stroke-dashoffset", "fill", "stroke", "translate",
        "scale", "rotate", "filter", "letter-spacing", "font-size", "margin-top", "margin-left", "flex-grow", "transform-origin",
        "backdrop-filter",
    };
    return names;
}

const std::vector<std::string>& Animator::entry_properties() {
    static const std::vector<std::string> names = {"animation", "animation-name", "transition", "transition-property", "backdrop-filter", "filter", "box-shadow"};
    return names;
}

void Animator::set_style_sheets(const std::vector<std::string>& texts) {
    KeyframeRules rules;
    for (const std::string& text : texts) parse_keyframes(text, rules);
    keyframes_ = std::move(rules);
}

std::string Animator::underlying_value(int64_t id, const std::string& property) const {
    if (auto b = bases_.find(id); b != bases_.end())
        if (auto v = b->second.find(property); v != b->second.end()) return v->second;
    if (auto u = underlying.find(id); u != underlying.end())
        if (auto v = u->second.find(property); v != u->second.end()) return v->second;
    if (const std::string* initial = initial_value(property)) return *initial;
    return {};
}

std::optional<std::string> Animator::transition_value(int64_t id, const std::string& property, double now) const {
    auto list = transitions_.find(id);
    if (list == transitions_.end()) return std::nullopt;
    auto it = list->second.find(property);
    if (it == list->second.end()) return std::nullopt;
    const Transition& t = it->second;
    double elapsed = now - t.start - t.spec.delay;
    if (elapsed <= 0) return t.from;
    double progress = std::min(1.0, elapsed / t.spec.duration);
    return interpolate(t.from, t.to, t.spec.timing.css(progress));
}

void Animator::update(const std::unordered_map<int64_t, Declared>& declarations, double now) {
    std::unordered_map<int64_t, std::vector<Running>> next_running;
    for (const auto& [id, values] : declarations) {
        auto specs = AnimationSpec::list(values);
        if (!specs.empty()) {
            const auto& previous = running_[id];
            auto& next = next_running[id];
            for (auto& spec : specs) {
                auto existing = std::find_if(previous.begin(), previous.end(), [&](const Running& r) { return r.spec.name == spec.name; });
                if (existing != previous.end()) {
                    if (existing->spec == spec) next.push_back(*existing);
                    else next.push_back(Running{spec, existing->start, existing->iteration, false});
                    continue;
                }
                if (keyframes_.count(spec.name)) events_.push_back({"animationstart", id, spec.name, 0});
                next.push_back(Running{spec, now, 0, false});
            }
        }
        auto transition_specs = TransitionSpec::list(values);
        bool known = bases_.count(id) != 0;
        Declared base = known ? bases_[id] : Declared{};
        for (const std::string& property : watched()) {
            if (css::starts_with(property, "animation") || css::starts_with(property, "transition")) continue;
            auto nv = values.find(property);
            const std::string* new_value = nv == values.end() ? nullptr : &nv->second;
            std::optional<std::string> old_value;
            if (auto ov = base.find(property); ov != base.end()) old_value = ov->second;
            // A transition from unset starts at the property's initial value.
            else if (known && new_value) {
                if (const std::string* initial = initial_value(property)) old_value = *initial;
            }
            if (old_value && new_value && *old_value != *new_value) {
                auto spec = std::find_if(transition_specs.rbegin(), transition_specs.rend(), [&](const TransitionSpec& s) { return s.property == property || s.property == "all"; });
                if (spec == transition_specs.rend()) {
                    if (auto t = transitions_.find(id); t != transitions_.end()) t->second.erase(property);
                } else {
                    std::string current = transition_value(id, property, now).value_or(*old_value);
                    transitions_[id][property] = Transition{std::move(current), *new_value, now, *spec};
                }
            } else if (!new_value) {
                if (auto t = transitions_.find(id); t != transitions_.end()) t->second.erase(property);
            }
            if (new_value) base[property] = *new_value;
            else base.erase(property);
        }
        bases_[id] = std::move(base);
        if (auto t = transitions_.find(id); t != transitions_.end() && t->second.empty()) transitions_.erase(t);
    }
    for (auto it = bases_.begin(); it != bases_.end();) {
        if (!declarations.count(it->first)) {
            transitions_.erase(it->first);
            it = bases_.erase(it);
        } else ++it;
    }
    running_ = std::move(next_running);
    prune_overrides();
}

StyleValues Animator::values(const std::vector<Keyframe>& frames, const Declared& base, double progress, const AnimationSpec& spec) const {
    std::vector<std::string> properties;
    for (const auto& f : frames)
        for (const auto& [name, value] : f.declarations)
            if (std::find(properties.begin(), properties.end(), name) == properties.end()) properties.push_back(name);
    StyleValues result;
    Timing spec_timing = spec.timing();
    for (const std::string& property : properties) {
        struct Stop {
            double offset;
            std::string value;
            const std::optional<Timing>* timing;
        };
        static const std::optional<Timing> NONE;
        std::vector<Stop> stops;
        for (const auto& f : frames)
            if (const std::string* v = last_value(f.declarations, property)) stops.push_back({f.offset, *v, &f.timing});
        const std::string* own = nullptr;
        if (auto b = base.find(property); b != base.end()) own = &b->second;
        else own = initial_value(property);
        if (own && !stops.empty()) {
            if (stops.front().offset > 0) stops.insert(stops.begin(), Stop{0, *own, &frames.front().timing});
            if (stops.back().offset < 1) stops.push_back(Stop{1, *own, &NONE});
        }
        if (stops.empty()) continue;
        if (progress <= stops.front().offset) { result.emplace_back(property, stops.front().value); continue; }
        if (progress >= stops.back().offset) { result.emplace_back(property, stops.back().value); continue; }
        for (size_t i = 0; i + 1 < stops.size(); i++) {
            const Stop &a = stops[i], &b = stops[i + 1];
            if (progress >= a.offset && progress <= b.offset) {
                double span = std::max(b.offset - a.offset, 0.000001);
                double local = (progress - a.offset) / span;
                const Timing& timing = a.timing->has_value() ? **a.timing : spec_timing;
                result.emplace_back(property, interpolate(a.value, b.value, timing.css(local)));
                break;
            }
        }
    }
    return result;
}

std::optional<StyleValues> Animator::sample_animation(int64_t id, Running& item, const std::vector<Keyframe>& frames, double now, std::vector<AnimationEvent>* events) const {
    const AnimationSpec& spec = item.spec;
    double local = now - item.start - spec.delay;
    if (auto o = overrides_.find(id); o != overrides_.end()) {
        if (auto it = o->second.find("animation:" + spec.name); it != o->second.end()) {
            if (it->second.cancelled) return std::nullopt;
            local = it->second.local(now) - spec.delay;
        }
    }
    static const Declared EMPTY;
    auto b = bases_.find(id);
    const Declared& base = b == bases_.end() ? EMPTY : b->second;
    auto reversed = [&](int iteration) {
        if (spec.direction == "reverse") return true;
        if (spec.direction == "alternate") return iteration % 2 == 1;
        if (spec.direction == "alternate-reverse") return iteration % 2 == 0;
        return false;
    };
    if (local < 0) {
        if (spec.fill == "backwards" || spec.fill == "both")
            return values(frames, base, css::starts_with(spec.direction, "reverse") || spec.direction == "alternate-reverse" ? 1 : 0, spec);
        return std::nullopt;
    }
    double duration = std::max(spec.duration, 0.0001);
    double total = spec.iterations * duration;
    if (local >= total) {
        if (!item.ended) {
            item.ended = true;
            if (events) events->push_back({"animationend", id, spec.name, total});
        }
        if (spec.fill == "forwards" || spec.fill == "both") {
            int last_iteration = std::max(0, int(std::ceil(std::min(spec.iterations, 1e9))) - 1);
            double fraction = std::fmod(spec.iterations, 1.0);
            double progress = fraction == 0 ? 1.0 : fraction;
            if (reversed(last_iteration)) progress = 1 - progress;
            return values(frames, base, progress, spec);
        }
        return std::nullopt;
    }
    int iteration = int(std::min(local / duration, 1e9));
    if (iteration != item.iteration) {
        item.iteration = iteration;
        if (events) events->push_back({"animationiteration", id, spec.name, local});
    }
    double progress = (local - iteration * duration) / duration;
    if (reversed(iteration)) progress = 1 - progress;
    return values(frames, base, progress, spec);
}

StyleOverrides Animator::sample(double now) {
    StyleOverrides out;
    for (auto& [id, list] : running_) {
        for (Running& item : list) {
            auto frames = keyframes_.find(item.spec.name);
            if (frames == keyframes_.end() || frames->second.empty()) continue;
            if (auto values = sample_animation(id, item, frames->second, now, &events_))
                for (auto& [property, value] : *values) merge(out[id], property, std::move(value));
        }
    }
    for (auto it = transitions_.begin(); it != transitions_.end();) {
        int64_t id = it->first;
        auto& list = it->second;
        for (auto t = list.begin(); t != list.end();) {
            const std::string& property = t->first;
            const Transition& transition = t->second;
            double elapsed = now - transition.start - transition.spec.delay;
            if (auto o = overrides_.find(id); o != overrides_.end()) {
                if (auto ov = o->second.find("transition:" + property); ov != o->second.end()) {
                    if (ov->second.cancelled) { t = list.erase(t); continue; }
                    elapsed = ov->second.local(now) - transition.spec.delay;
                }
            }
            if (elapsed >= transition.spec.duration) {
                events_.push_back({"transitionend", id, property, transition.spec.duration});
                t = list.erase(t);
                continue;
            }
            double progress = std::max(0.0, elapsed) / transition.spec.duration;
            merge(out[id], property, elapsed <= 0 ? transition.from : interpolate(transition.from, transition.to, transition.spec.timing.css(progress)));
            ++t;
        }
        it = list.empty() ? transitions_.erase(it) : std::next(it);
    }
    if (!web_.empty()) sample_web(out, now);
    for (auto it = out.begin(); it != out.end();) it = it->second.empty() ? out.erase(it) : std::next(it);
    return out;
}

void Animator::sample_web(StyleOverrides& out, double now, int64_t only) const {
    // By target, then in aid order: later animations composite onto earlier ones.
    std::map<int64_t, std::vector<const WebAnimation*>> by_target;
    for (const auto& [aid, a] : web_)
        if (!only || a.target == only) by_target[a.target].push_back(&a);
    for (const auto& [id, list] : by_target) {
        for (const WebAnimation* a : list) {
            auto state = compute(*a, local_time(*a, now), a->rate >= 0);
            if (!state || !state->active_time) continue;
            auto underlying_of = [&](const std::string& property) {
                if (auto o = out.find(id); o != out.end())
                    if (const std::string* v = last_value(o->second, property)) return *v;
                return underlying_value(id, property);
            };
            StyleValues values;
            for (const std::string& property : a->properties)
                if (auto v = web_value(*a, property, state->progress, state->iteration, underlying_of)) values.emplace_back(property, std::move(*v));
            for (auto& [property, value] : values) merge(out[id], property, std::move(value));
        }
    }
}

bool Animator::active(double now) const {
    static const std::unordered_set<int64_t> NONE;
    return active(now, NONE);
}

bool Animator::active(double now, const std::unordered_set<int64_t>& skip) const {
    for (const auto& [id, list] : transitions_)
        if (!skip.count(id)) return true;
    for (const auto& [aid, a] : web_) {
        if (!a.running() || skip.count(a.target)) continue;
        auto state = compute(a, local_time(a, now), a.rate >= 0);
        if (!state) continue;
        bool pending = a.rate > 0 ? state->before : state->after;
        if ((!state->before && !state->after) || pending) return true;
    }
    for (const auto& [id, list] : running_) {
        if (skip.count(id)) continue;
        for (const Running& r : list)
            if (!r.ended && !r.spec.paused && keyframes_.count(r.spec.name)) return true;
    }
    return false;
}

const Animator::Override* Animator::override_of(int64_t id, const std::string& key) const {
    auto o = overrides_.find(id);
    if (o == overrides_.end()) return nullptr;
    auto it = o->second.find(key);
    return it == o->second.end() ? nullptr : &it->second;
}

std::vector<int64_t> Animator::animated_ids(double now) const {
    std::vector<int64_t> out;
    for (const auto& [id, list] : running_) {
        for (const Running& r : list) {
            if (r.ended || !keyframes_.count(r.spec.name)) continue;
            const Override* o = override_of(id, "animation:" + r.spec.name);
            if (o ? o->cancelled || o->hold.has_value() : r.spec.paused) continue;
            out.push_back(id);
            break;
        }
    }
    for (const auto& [id, list] : transitions_) out.push_back(id);
    for (const auto& [aid, a] : web_) {
        if (!a.running()) continue;
        auto state = compute(a, local_time(a, now), a.rate >= 0);
        if (state && !(a.rate > 0 ? state->after : state->before)) out.push_back(a.target);
    }
    std::sort(out.begin(), out.end());
    out.erase(std::unique(out.begin(), out.end()), out.end());
    return out;
}

std::vector<std::string> Animator::animated_properties(int64_t id) const {
    std::vector<std::string> out;
    auto add = [&](const std::string& p) {
        if (std::find(out.begin(), out.end(), p) == out.end()) out.push_back(p);
    };
    if (auto r = running_.find(id); r != running_.end())
        for (const Running& item : r->second)
            if (auto frames = keyframes_.find(item.spec.name); frames != keyframes_.end())
                for (const Keyframe& f : frames->second)
                    for (const auto& [property, value] : f.declarations) add(property);
    if (auto t = transitions_.find(id); t != transitions_.end())
        for (const auto& [property, transition] : t->second) add(property);
    for (const auto& [aid, a] : web_)
        if (a.target == id)
            for (const std::string& p : a.properties) add(p);
    return out;
}

StyleValues Animator::peek(int64_t id, double t) const {
    StyleOverrides out;
    if (auto r = running_.find(id); r != running_.end()) {
        for (Running item : r->second) {
            auto frames = keyframes_.find(item.spec.name);
            if (frames == keyframes_.end() || frames->second.empty()) continue;
            if (auto values = sample_animation(id, item, frames->second, t, nullptr))
                for (auto& [property, value] : *values) merge(out[id], property, std::move(value));
        }
    }
    if (auto list = transitions_.find(id); list != transitions_.end()) {
        for (const auto& [property, transition] : list->second) {
            double elapsed = t - transition.start - transition.spec.delay;
            if (const Override* o = override_of(id, "transition:" + property)) {
                if (o->cancelled) continue;
                elapsed = o->local(t) - transition.spec.delay;
            }
            // An ended transition leaves the declared value, which the element already has.
            if (elapsed >= transition.spec.duration) continue;
            double progress = std::max(0.0, elapsed) / transition.spec.duration;
            merge(out[id], property, elapsed <= 0 ? transition.from : interpolate(transition.from, transition.to, transition.spec.timing.css(progress)));
        }
    }
    if (!web_.empty()) sample_web(out, t, id);
    auto it = out.find(id);
    return it == out.end() ? StyleValues{} : std::move(it->second);
}

std::optional<Animator::Plan> Animator::plan(int64_t id, double now, bool iterations) const {
    Plan plan;
    double end = INF, loop = 0, wait = INF, next_iteration = INF;
    bool looping = false;
    // An animation's local time runs `rate` times as fast as the clock from `origin`; it begins
    // after `delay` local seconds and repeats every `period` local seconds.
    auto add = [&](double origin, double rate, double delay, double duration, double count, bool alternate) {
        if (rate <= 0 || duration <= 0) return false;
        double begin = origin + delay / rate;
        if (now < begin) {
            wait = std::min(wait, begin);
            return true;
        }
        double iteration = duration / rate;
        if (std::isinf(count)) {
            double period = iteration * (alternate ? 2 : 1);
            if (looping && std::fabs(period - loop) > 1e-6) return false;
            looping = true;
            loop = period;
        } else {
            end = std::min(end, begin + count * iteration);
        }
        double done = std::floor((now - begin) / iteration) + 1;
        if (std::isinf(count) || done < count) next_iteration = std::min(next_iteration, begin + done * iteration);
        return true;
    };

    if (auto r = running_.find(id); r != running_.end()) {
        for (const Running& item : r->second) {
            if (item.ended || !keyframes_.count(item.spec.name)) continue;
            const AnimationSpec& spec = item.spec;
            double origin = item.start, rate = 1;
            if (const Override* o = override_of(id, "animation:" + spec.name)) {
                if (o->cancelled || o->hold) continue;
                origin = *o->start;
                rate = o->rate;
            } else if (spec.paused) continue;
            bool alternate = spec.direction == "alternate" || spec.direction == "alternate-reverse";
            if (!add(origin, rate, spec.delay, std::max(spec.duration, 0.0001), spec.iterations, alternate)) return std::nullopt;
        }
    }
    if (auto list = transitions_.find(id); list != transitions_.end()) {
        for (const auto& [property, transition] : list->second) {
            double origin = transition.start, rate = 1;
            if (const Override* o = override_of(id, "transition:" + property)) {
                if (o->cancelled || o->hold) continue;
                origin = *o->start;
                rate = o->rate;
            }
            if (!add(origin, rate, transition.spec.delay, transition.spec.duration, 1, false)) return std::nullopt;
        }
    }
    for (const auto& [aid, a] : web_) {
        if (a.target != id || !a.running()) continue;
        if (a.iteration_composite != "replace") return std::nullopt;
        bool alternate = a.direction == "alternate" || a.direction == "alternate-reverse";
        // Local milliseconds: (now·1000 − start) · rate, so the origin is start / 1000 seconds.
        if (!add(*a.start_ms / 1000, a.rate, a.delay / 1000, a.duration / 1000, a.iterations, alternate)) return std::nullopt;
    }

    plan.wake = std::min(wait, end);
    if (iterations) plan.wake = std::min(plan.wake, next_iteration);
    if (wait < INF && end == INF && !looping) {
        plan.ready = false;
        return plan;
    }
    // Something still waiting changes the picture at `wait`: bake only up to there.
    end = std::min(end, wait);
    plan.start = now;
    if (end < INF) {
        plan.duration = end - now;
    } else if (looping) {
        plan.duration = loop;
        plan.repeat = true;
    } else {
        return std::nullopt;
    }
    if (plan.duration <= 0) return std::nullopt;
    return plan;
}

uint64_t Animator::signature(int64_t id) const {
    std::string s;
    auto num = [&](double v) {
        s += css::format_number(v, 6);
        s.push_back('|');
    };
    auto text = [&](const std::string& v) {
        s += v;
        s.push_back('|');
    };
    auto control = [&](const std::string& key) {
        if (const Override* o = override_of(id, key)) {
            text(key);
            num(o->origin);
            num(o->start ? *o->start : -1);
            num(o->hold ? *o->hold : -1);
            num(o->rate);
            num(o->cancelled);
        }
    };
    if (auto r = running_.find(id); r != running_.end()) {
        for (const Running& item : r->second) {
            const AnimationSpec& spec = item.spec;
            text(spec.name);
            num(item.start);
            num(spec.duration);
            text(spec.timing_text);
            num(spec.delay);
            num(spec.iterations);
            text(spec.direction);
            text(spec.fill);
            num(spec.paused);
            num(item.ended);
            control("animation:" + spec.name);
        }
    }
    if (auto list = transitions_.find(id); list != transitions_.end()) {
        for (const auto& [property, transition] : list->second) {
            text(property);
            text(transition.from);
            text(transition.to);
            num(transition.start);
            num(transition.spec.duration);
            num(transition.spec.delay);
            text(transition.spec.timing.text);
            control("transition:" + property);
        }
    }
    for (const auto& [aid, a] : web_) {
        if (a.target != id) continue;
        num(double(aid));
        num(a.start_ms ? *a.start_ms : -1);
        num(a.hold_ms ? *a.hold_ms : -1);
        num(a.rate);
        num(a.duration);
        num(a.delay);
        num(a.iterations);
        text(a.direction);
        text(a.fill);
        text(a.easing.text);
        text(a.composite);
        for (const auto& frame : a.frames) {
            num(frame.offset);
            text(frame.easing.text);
            for (const auto& [property, value] : frame.values) {
                text(property);
                text(value);
            }
        }
    }
    for (const char* property : {"transform", "opacity", "translate", "rotate", "scale", "transform-origin"}) text(base_value(id, property));
    return std::hash<std::string>()(s);
}

void Animator::apply_web(const Json& message, double clock_offset_ms) {
    constexpr size_t MAX_ANIMATIONS = 1024;
    size_t count = 0;
    for (const Json& item : message["anims"].arr()) {
        if (++count > 4096) break;
        const Json* aid = item.find("aid");
        if (!aid || !aid->isNumber()) continue;
        if (const Json* css_record = item.find("css"); css_record && css_record->isObject()) {
            const Json* target = item.find("target");
            const Json* kind = css_record->find("kind");
            const Json* name = css_record->find("name");
            const Json* origin = css_record->find("origin");
            if (!target || !target->isNumber() || !kind || !name || !name->isString() || !origin || !origin->isNumber()) continue;
            if (kind->asString() != "animation" && kind->asString() != "transition") continue;
            int64_t id = int64_t(target->asNumber());
            std::string key = kind->asString() + ":" + name->asString();
            if (item["remove"].asBool()) {
                if (auto o = overrides_.find(id); o != overrides_.end()) {
                    o->second.erase(key);
                    if (o->second.empty()) overrides_.erase(o);
                }
                continue;
            }
            double rate = item["playbackRate"].isNumber() ? item["playbackRate"].asNumber() : 1;
            if (!std::isfinite(rate) || std::fabs(rate) > 1000) continue;
            Override o;
            o.origin = origin->asNumber() / 1000;
            o.rate = rate;
            o.cancelled = item["cancelled"].asBool();
            if (item["startTime"].isNumber()) o.start = (item["startTime"].asNumber() + clock_offset_ms) / 1000;
            if (item["holdTime"].isNumber()) o.hold = item["holdTime"].asNumber() / 1000;
            if (!o.cancelled && !o.start && !o.hold) continue;
            if ((o.start && !std::isfinite(*o.start)) || (o.hold && !std::isfinite(*o.hold))) continue;
            if (!overrides_.count(id) && overrides_.size() >= MAX_ANIMATIONS) continue;
            overrides_[id][key] = o;
            continue;
        }
        int64_t key = int64_t(aid->asNumber());
        if (item["remove"].asBool()) {
            web_.erase(key);
            continue;
        }
        auto animation = WebAnimation::parse(item, clock_offset_ms);
        if (!animation) {
            web_.erase(key);
            continue;
        }
        if (!web_.count(key) && web_.size() >= MAX_ANIMATIONS) continue;
        web_[key] = std::move(*animation);
    }
}

std::vector<int64_t> Animator::web_targets() const {
    std::vector<int64_t> out;
    for (const auto& [aid, a] : web_)
        if (std::find(out.begin(), out.end(), a.target) == out.end()) out.push_back(a.target);
    return out;
}

std::vector<std::string> Animator::web_properties(int64_t id) const {
    std::vector<std::string> out;
    for (const auto& [aid, a] : web_)
        if (a.target == id)
            for (const auto& p : a.properties)
                if (std::find(out.begin(), out.end(), p) == out.end()) out.push_back(p);
    return out;
}

void Animator::prune(const std::function<bool(int64_t)>& alive) {
    for (auto it = web_.begin(); it != web_.end();) it = alive(it->second.target) ? std::next(it) : web_.erase(it);
}

void Animator::prune_overrides() {
    for (auto it = overrides_.begin(); it != overrides_.end();) {
        int64_t id = it->first;
        for (auto o = it->second.begin(); o != it->second.end();) {
            const std::string& key = o->first;
            bool alive = false;
            if (css::starts_with(key, "animation:")) {
                std::string name = key.substr(10);
                if (auto r = running_.find(id); r != running_.end())
                    alive = std::any_of(r->second.begin(), r->second.end(), [&](const Running& x) { return x.spec.name == name && std::fabs(x.start - o->second.origin) < 0.002; });
            } else {
                std::string property = key.substr(11);
                alive = o->second.cancelled;
                if (auto t = transitions_.find(id); t != transitions_.end())
                    if (auto p = t->second.find(property); p != t->second.end()) alive = std::fabs(p->second.start - o->second.origin) < 0.002;
            }
            o = alive ? std::next(o) : it->second.erase(o);
        }
        it = it->second.empty() ? overrides_.erase(it) : std::next(it);
    }
}

Json Animator::css_animations(const std::vector<int64_t>& ids, double now) const {
    Json list = Json::array();
    for (int64_t id : ids) {
        const Override* none = nullptr;
        auto find_override = [&](const std::string& key) -> const Override* {
            auto o = overrides_.find(id);
            if (o == overrides_.end()) return none;
            auto it = o->second.find(key);
            return it == o->second.end() ? none : &it->second;
        };
        if (auto t = transitions_.find(id); t != transitions_.end()) {
            for (const auto& [property, transition] : t->second) {
                const Override* o = find_override("transition:" + property);
                if (o && o->cancelled) continue;
                Json e = Json::object();
                e.set("kind", "transition").set("id", double(id)).set("name", property).set("index", 0);
                e.set("origin", transition.start * 1000).set("currentTime", (o ? o->local(now) : now - transition.start) * 1000);
                e.set("playbackRate", o ? o->rate : 1).set("paused", o && o->hold.has_value());
                e.set("duration", transition.spec.duration * 1000).set("delay", transition.spec.delay * 1000).set("iterations", 1);
                e.set("direction", "normal").set("fill", "backwards").set("easing", transition.spec.timing.text);
                list.push(std::move(e));
            }
        }
        if (auto r = running_.find(id); r != running_.end()) {
            int index = 0;
            for (const Running& item : r->second) {
                int i = index++;
                if (!keyframes_.count(item.spec.name)) continue;
                const Override* o = find_override("animation:" + item.spec.name);
                if (o && o->cancelled) continue;
                Json e = Json::object();
                e.set("kind", "animation").set("id", double(id)).set("name", item.spec.name).set("index", i);
                e.set("origin", item.start * 1000).set("currentTime", (o ? o->local(now) : now - item.start) * 1000);
                e.set("playbackRate", o ? o->rate : 1).set("paused", o ? o->hold.has_value() : item.spec.paused);
                e.set("duration", item.spec.duration * 1000).set("delay", item.spec.delay * 1000);
                e.set("direction", item.spec.direction).set("fill", item.spec.fill).set("easing", item.spec.timing_text);
                e.set("iterations", std::isfinite(item.spec.iterations) ? Json(item.spec.iterations) : Json());
                list.push(std::move(e));
            }
        }
    }
    return list;
}

} // namespace twlite
