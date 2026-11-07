#include "renderer.h"

#include "css.h"
#include "theme.h"
#include "tw_ua_stylesheet.h"

#include <litehtml.h>
#include <litehtml/html_tag.h>
#include <litehtml/render_item.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <functional>
#include <set>

using twidget::Json;

namespace twlite {

namespace {

constexpr double MIN_HEIGHT = 24.0;
constexpr double MAX_HEIGHT = 900.0;
constexpr int MAX_PASSES = 4;
/// A finger jitters a few px during a tap (see renderer.rs TAP_SLOP): moves inside this radius are not forwarded.
constexpr double TAP_SLOP = 8.0;
const char* const ALWAYS_FORWARD[] = {"click", "input", "change", "submit", "reset", "load", "error", "toggle"};

/// Chromium/WebKit UA rules litehtml's master.css lacks and widgets rely on. Form controls get a
/// visible box: litehtml has no controls of its own (the container would own them), so they are
/// bordered inline blocks here, with the value as text where the shadow tree knows it. The profile's
/// UA layer (TW_UA_STYLESHEET, generated from packages/core/profile.json) comes after it: the root and
/// text defaults on the theme variables and the client-styled <button>, as every renderer draws them.
const char* const UA_EXTRA = R"css(
button,input,select,textarea,meter,progress{box-sizing:border-box}
input,select,textarea,button{display:inline-block;border:1px solid #8f8f8f;border-radius:3px;background:#fff;color:#000;padding:2px 6px;font:13.333px -apple-system,system-ui,sans-serif;min-height:1.8em;min-width:2em;vertical-align:middle}
button{background:#efefef;padding:1px 6px;text-align:center}
input[type=checkbox],input[type=radio]{min-width:13px;width:13px;height:13px;min-height:13px;padding:0;margin:3px 3px 3px 4px;vertical-align:middle}
input[type=radio]{border-radius:50%}
input[type=range]{border:none;background:transparent;padding:0}
input[type=color]{width:50px;height:27px;padding:2px}
input[type=hidden]{display:none}
textarea{display:block;min-height:3em;white-space:pre-wrap}
progress,meter{display:inline-block;width:10em;height:1em;background:#e6e6e6;border-radius:2px}
canvas{display:inline-block;background:transparent}
svg{display:inline-block}
details>summary{display:block;cursor:pointer}
details:not([open])>*:not(summary){display:none}
dialog:not([open]){display:none}
[hidden]{display:none}
)css";

const char* const VOID_TAGS[] = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"};

bool is_void(const std::string& tag) {
    for (const char* v : VOID_TAGS)
        if (tag == v) return true;
    return false;
}

bool starts_with_on(const std::string& name) {
    return name.size() >= 2 && (name[0] == 'o' || name[0] == 'O') && (name[1] == 'n' || name[1] == 'N');
}

std::string lower(std::string s) {
    for (auto& c : s) c = char(std::tolower((unsigned char)c));
    return s;
}

void escape_text(const std::string& s, std::string& out) {
    for (char c : s) {
        switch (c) {
        case '&': out += "&amp;"; break;
        case '<': out += "&lt;"; break;
        case '>': out += "&gt;"; break;
        default: out.push_back(c);
        }
    }
}

void escape_attr(const std::string& s, std::string& out) {
    for (char c : s) {
        switch (c) {
        case '&': out += "&amp;"; break;
        case '<': out += "&lt;"; break;
        case '"': out += "&quot;"; break;
        default: out.push_back(c);
        }
    }
}

Color color_of(const litehtml::web_color& c) {
    return Color{c.red, c.green, c.blue, c.alpha};
}

Rect rect_of(const litehtml::position& p) {
    return Rect{float(p.x), float(p.y), float(p.width), float(p.height)};
}

/// The border box of an element in document coordinates (`get_placement()` is the content box;
/// getBoundingClientRect / offsetWidth are border boxes).
Rect border_box_of(const litehtml::element::ptr& el) {
    litehtml::position pos = el->get_placement();
    Rect r = rect_of(pos);
    if (auto ri = el->get_render_item()) {
        const auto& pad = ri->get_paddings();
        const auto& bd = ri->get_borders();
        r.x -= float(pad.left) + float(bd.left);
        r.y -= float(pad.top) + float(bd.top);
        r.w += float(pad.left) + float(pad.right) + float(bd.left) + float(bd.right);
        r.h += float(pad.top) + float(pad.bottom) + float(bd.top) + float(bd.bottom);
    }
    return r;
}

Radii radii_of(const litehtml::border_radiuses& r) {
    return Radii{float(r.top_left_x), float(r.top_left_y), float(r.top_right_x), float(r.top_right_y),
                 float(r.bottom_right_x), float(r.bottom_right_y), float(r.bottom_left_x), float(r.bottom_left_y)};
}

Json rect_json(const Rect& r) {
    Json o = Json::object();
    o.set("x", r.x).set("y", r.y).set("width", r.w).set("height", r.h);
    o.set("top", r.y).set("left", r.x).set("right", r.x + r.w).set("bottom", r.y + r.h);
    return o;
}

double now_ms() {
    using namespace std::chrono;
    return duration<double, std::milli>(steady_clock::now().time_since_epoch()).count();
}

std::string base64_encode(const std::vector<uint8_t>& in) {
    static const char* A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    out.reserve((in.size() + 2) / 3 * 4);
    size_t i = 0;
    for (; i + 2 < in.size(); i += 3) {
        uint32_t v = (uint32_t(in[i]) << 16) | (uint32_t(in[i + 1]) << 8) | in[i + 2];
        out += A[v >> 18]; out += A[(v >> 12) & 63]; out += A[(v >> 6) & 63]; out += A[v & 63];
    }
    if (i + 1 == in.size()) {
        uint32_t v = uint32_t(in[i]) << 16;
        out += A[v >> 18]; out += A[(v >> 12) & 63]; out += "==";
    } else if (i + 2 == in.size()) {
        uint32_t v = (uint32_t(in[i]) << 16) | (uint32_t(in[i + 1]) << 8);
        out += A[v >> 18]; out += A[(v >> 12) & 63]; out += A[(v >> 6) & 63]; out += '=';
    }
    return out;
}

/// What a compositor layer animates; an element animating anything else is painted per frame.
bool is_layer_property(const std::string& property) {
    return property == "transform" || property == "opacity" || property == "translate" || property == "rotate" || property == "scale";
}

/// The `style` attribute is the inline style itself, as in the DOM: setting it replaces what the
/// style ops set, and a style op that removes a property removes it from what the attribute set.
void set_inline_style(std::vector<std::pair<std::string, std::string>>& styles, const std::string& text) {
    styles.clear();
    for (auto& d : css::parse_declarations(text)) styles.emplace_back(std::move(d.name), d.important ? d.value + " !important" : std::move(d.value));
}

/// Move-type events: only the latest one per type and pointer goes out, once per frame.
bool coalesced(const std::string& type) {
    return type == "pointermove" || type == "mousemove" || type == "touchmove" || type == "wheel";
}

constexpr int MAX_CANVASES = 64;
/// Layers: at most this many per widget, this many device pixels in all, and no track shorter
/// than this (a frame or two is cheaper to paint than to hand over).
constexpr size_t MAX_LAYERS = 64;
constexpr double MAX_LAYER_PIXELS = 4096.0 * 4096.0;
constexpr double MIN_LAYER_SPAN = 0.1;
constexpr float LAYER_MARGIN = 48;
constexpr double DOUBLE_CLICK_MS = 500;

} // namespace

// ───────────────────────── color scheme rewrite ─────────────────────────

std::string rewrite_color_scheme(const std::string& css, bool dark) {
    static const std::string KEY = "prefers-color-scheme";
    std::string out;
    out.reserve(css.size());
    size_t pos = 0;
    while (true) {
        size_t i = css.find(KEY, pos);
        if (i == std::string::npos) {
            out.append(css, pos, std::string::npos);
            break;
        }
        out.append(css, pos, i - pos);
        size_t j = i + KEY.size();
        while (j < css.size() && (css[j] == ' ' || css[j] == '\t' || css[j] == '\n' || css[j] == ':')) j++;
        size_t k = j;
        while (k < css.size() && std::isalpha((unsigned char)css[k])) k++;
        std::string value = lower(css.substr(j, k - j));
        bool matches = value.empty() ? true : (value == "dark") == dark;  // bare `(prefers-color-scheme)` is true
        out += matches ? "min-width:0px" : "min-width:999999px";
        pos = k;
    }
    return out;
}

// ───────────────────────── shadow tree ─────────────────────────

struct Renderer::Node {
    enum Kind { Element, Text, Comment } kind = Element;
    int64_t id = 0;  // widget node id, 0 until adopted/registered
    std::string tag;
    bool svg = false;
    std::vector<std::pair<std::string, std::string>> attrs;
    std::vector<std::pair<std::string, std::string>> styles;  // inline style properties set by `style` ops
    std::string data;                                         // text / comment data; stylesheet text of <style>
    Node* parent = nullptr;
    std::vector<Node*> children;
};

// ───────────────────────── litehtml container ─────────────────────────

class Container : public litehtml::document_container {
public:
    Container(Renderer& r, Backend& backend) : r_(r), backend_(backend) {}
    ~Container() override {
        // The document releases its fonts through this container on destruction: drop it
        // (and the element handles that keep its nodes alive) while the font table still exists.
        elements_.clear();
        doc_ = nullptr;
    }

    // document lifecycle
    /// True when the last build patched the previous document instead of parsing a new one.
    bool patched() const { return patched_; }

    /// The master CSS of the next document; a change drops the current one (a patch keeps the master it was made with).
    void set_master(std::string css) {
        if (css == master_css_) return;
        master_css_ = std::move(css);
        elements_.clear();
        doc_ = nullptr;
    }

    void build(const std::string& html, int vp_height) {
        bool viewport_changed = vp_height != vp_height_;
        vp_height_ = vp_height;
        patched_ = false;
#ifdef TW_LITEHTML_TWIDGET
        // twidget's litehtml fork patches a document made by createUpdatableFromString in place when the
        // new HTML only changes what it can patch (styles, text, classes, attributes, child lists), and
        // refuses otherwise, leaving the document as it was.
        bool changed = true;
        if (doc_ && doc_->update_from_string(html, &changed)) {
            patched_ = true;
            if (viewport_changed) doc_->media_changed();
            float width = float(r_.width_);
            if (!changed && !viewport_changed && width == rendered_width_) return;
            if (!doc_->last_patch_paint_only() || viewport_changed || width != rendered_width_) {
                doc_->render(litehtml::pixel_t(width));
                rendered_width_ = width;
                layouts_++;
            }
            // A child-list patch replaces elements; a paint-only one cannot.
            if (!doc_->last_patch_paint_only()) index();
            return;
        }
#endif
        elements_.clear();
        doc_ = nullptr;
#ifdef TW_LITEHTML_TWIDGET
        doc_ = litehtml::document::createUpdatableFromString(html, this, master_css_);
#else
        doc_ = litehtml::document::createFromString(litehtml::estring(html), this, master_css_);
#endif
        if (!doc_) return;
        rendered_width_ = float(r_.width_);
        doc_->render(litehtml::pixel_t(rendered_width_));
        layouts_++;
        index();
    }

    void index() {
        elements_.clear();
        if (auto root = doc_->root()) {
            for (auto& el : root->select_all("[data-twid]")) {
                if (const char* a = el->get_attr("data-twid")) elements_[std::strtoll(a, nullptr, 10)] = el;
            }
        }
    }
    double height() const { return doc_ ? double(doc_->height()) : 0.0; }
#ifdef TW_LITEHTML_TWIDGET
    /// The declared values of `names` on every displayed element with a widget id that declares
    /// one of `gates`, or whose id is in `always`. What the fork records as raw declarations.
    std::unordered_map<int64_t, Declared> declarations(const std::vector<std::string>& names, const std::vector<std::string>& gates, const std::vector<int64_t>& always) const {
        std::unordered_map<int64_t, Declared> out;
        if (!doc_ || !doc_->root()) return out;
        std::function<void(const litehtml::element::ptr&)> visit = [&](const litehtml::element::ptr& el) {
            if (el->css().get_display() == litehtml::display_none) return;
            if (auto tag = std::dynamic_pointer_cast<litehtml::html_tag>(el)) {
                if (const char* a = tag->get_attr("data-twid")) {
                    int64_t id = std::strtoll(a, nullptr, 10);
                    bool listed = std::find(always.begin(), always.end(), id) != always.end();
                    bool open = listed;
                    for (size_t i = 0; !open && i < gates.size(); i++) open = !tag->raw_declaration(gates[i]).empty();
                    if (open) {
                        Declared found;
                        for (const std::string& name : names) {
                            std::string raw = tag->raw_declaration(name);
                            if (!raw.empty()) found.emplace(name, std::move(raw));
                        }
                        if (!found.empty() || listed) out[id] = std::move(found);
                    }
                }
            }
            for (const auto& child : el->children()) visit(child);
        };
        visit(doc_->root());
        return out;
    }
    /// Replaces the style attribute of elements in place (the fork's style holes), relaying out
    /// only when a value can move boxes. False when the document cannot patch them.
    bool restyle(const std::vector<std::pair<litehtml::element::ptr, std::string>>& patches) {
        if (!doc_) return false;
        bool changed = false;
        if (!doc_->update_styles(patches, &changed)) return false;
        patched_ = true;
        if (changed && !doc_->last_patch_paint_only()) {
            rendered_width_ = float(r_.width_);
            doc_->render(litehtml::pixel_t(rendered_width_));
            layouts_++;
        }
        return true;
    }

    // compositor layers

    /// Counts layouts: boxes, and so what can cover a layer, change only with one.
    uint64_t layouts() const { return layouts_; }
    /// `el` can be painted on its own as a layer: a box with no effects of its own, under no
    /// ancestor that groups its paint. `box` gets its border box.
    bool layer_candidate(const litehtml::element::ptr& el, Rect& box) const {
        auto ri = el->get_render_item();
        if (!doc_ || !ri || !ri->is_visible() || (el->is_inline() && !el->is_inline_box())) return false;
        const auto& css = el->css();
        if (!css.get_filter().empty() || !css.get_backdrop_filter().empty()) return false;
        for (auto p = ri->parent(); p; p = p->parent())
            if (p->src_el()->css().is_paint_group()) return false;
        box = border_box_of(el);
        return box.w > 0 && box.h > 0 && box.w * box.h < 2'000'000;
    }
    /// The ancestor that clips `el`, if one does: its padding box and inner radii. False when two
    /// do; a layer carries one clip.
    bool clip_of(const litehtml::element::ptr& el, bool& has, Rect& rect, Radii& radii) const {
        has = false;
        auto ri = el->get_render_item();
        if (!ri) return false;
        for (auto p = ri->parent(); p; p = p->parent()) {
            if (!p->src_el()->css().clips_contents()) continue;
            if (has) return false;
            has = true;
            Rect border = border_box_of(p->src_el());
            const auto& b = p->get_borders();
            rect = Rect{border.x + float(b.left), border.y + float(b.top), border.w - float(b.left) - float(b.right), border.h - float(b.top) - float(b.bottom)};
            auto r = p->src_el()->css().get_borders().radius.calc_percents(litehtml::pixel_t(border.w), litehtml::pixel_t(border.h));
            radii = Radii{std::max(0.0f, float(r.top_left_x) - float(b.left)), std::max(0.0f, float(r.top_left_y) - float(b.top)),
                          std::max(0.0f, float(r.top_right_x) - float(b.right)), std::max(0.0f, float(r.top_right_y) - float(b.top)),
                          std::max(0.0f, float(r.bottom_right_x) - float(b.right)), std::max(0.0f, float(r.bottom_right_y) - float(b.bottom)),
                          std::max(0.0f, float(r.bottom_left_x) - float(b.left)), std::max(0.0f, float(r.bottom_left_y) - float(b.bottom))};
        }
        return true;
    }
    /// Render items in the order a full paint draws them.
    std::vector<const litehtml::render_item*> paint_order() {
        std::vector<const litehtml::render_item*> trace;
        if (!doc_) return trace;
        litehtml::g_paint_trace = &trace;
        draw();
        litehtml::g_paint_trace = nullptr;
        return trace;
    }
    /// Something painted after `el` meets `area`, other than `el`'s descendants and what `ignore`
    /// holds (other layers, ordered among themselves by paint order).
    bool covered(const litehtml::element::ptr& el, const Rect& area, const std::vector<const litehtml::render_item*>& trace,
                 const std::unordered_set<const litehtml::element*>& ignore) const {
        auto ri = el->get_render_item();
        auto self = std::find(trace.begin(), trace.end(), ri.get());
        if (self == trace.end()) return true;
        for (auto it = std::next(self); it != trace.end(); ++it) {
            const litehtml::render_item* item = *it;
            if (item->src_el()->is_space()) continue;
            bool skip = false;
            for (const litehtml::render_item* walk = item; walk && !skip; walk = walk->parent().get())
                skip = walk == ri.get() || ignore.count(walk->src_el().get());
            if (skip) continue;
            Rect box = border_box_of(item->src_el());
            if (box.w <= 0 || box.h <= 0) continue;
            if (box.x < area.x + area.w && area.x < box.x + box.w && box.y < area.y + area.h && area.y < box.y + box.h) return true;
        }
        return false;
    }
    /// Paints the document without the elements in `skip`.
    DisplayList draw(const std::vector<litehtml::element::ptr>& skip) {
        if (!doc_) return {};
        for (const auto& el : skip) doc_->skip_paint.insert(el.get());
        DisplayList list = draw();
        doc_->skip_paint.clear();
        return list;
    }
    /// `el` and its descendants alone, without `el`'s own transform, opacity and stacking group
    /// (the layer applies them) and without the other layers in `skip`. `bounds` gets the area the
    /// paint can reach.
    DisplayList draw_layer(const litehtml::element::ptr& el, const std::vector<litehtml::element::ptr>& skip, Rect& bounds) {
        list_.clear();
        groups_.clear();
        auto ri = el->get_render_item();
        if (!doc_ || !ri) return {};
        for (const auto& other : skip)
            if (other != el) doc_->skip_paint.insert(other.get());
        litehtml::position placement = ri->get_placement();
        litehtml::pixel_t x = placement.x - ri->pos().x, y = placement.y - ri->pos().y;
        el->draw(0, x, y, nullptr, ri);
        if (ri->is_visible()) draw_stacking_context(ri, x, y);
        doc_->skip_paint.clear();
        Rect box = border_box_of(el);
        litehtml::position redraw(litehtml::pixel_t(box.x), litehtml::pixel_t(box.y), litehtml::pixel_t(box.w), litehtml::pixel_t(box.h));
        ri->get_redraw_box(redraw, x, y);
        // The redraw box leaves out shadows and anti-aliasing: a margin keeps them.
        float x0 = std::min(box.x, float(redraw.x)) - LAYER_MARGIN, y0 = std::min(box.y, float(redraw.y)) - LAYER_MARGIN;
        float x1 = std::max(box.x + box.w, float(redraw.x) + float(redraw.width)) + LAYER_MARGIN;
        float y1 = std::max(box.y + box.h, float(redraw.y) + float(redraw.height)) + LAYER_MARGIN;
        bounds = Rect{std::floor(x0), std::floor(y0), std::ceil(x1) - std::floor(x0), std::ceil(y1) - std::floor(y0)};
        return std::move(list_);
    }
#endif
    /// The pointer left the view: :hover ends. True when something must repaint.
    bool leave() {
        if (!doc_) return false;
        bool redraw = false;
        doc_->on_mouse_leave([&](const litehtml::position&) { redraw = true; });
        return redraw;
    }
    /// A decoded image for the canvas's drawImage: loads `url` like an <img> would.
    bool image_of(const std::string& url, int& id, float& w, float& h) {
        load_image(url.c_str(), nullptr, false);
        auto it = images_.find(url);
        if (it == images_.end() || !it->second.id) return false;
        id = it->second.id;
        w = it->second.w;
        h = it->second.h;
        return true;
    }
    /// An asynchronously decoded image got its size; true when a known image changed.
    bool image_ready(int id, float w, float h) {
        for (auto& [url, img] : images_) {
            if (img.id == id) {
                if (img.w == w && img.h == h) return false;
                img.w = w;
                img.h = h;
                return true;
            }
        }
        return false;
    }
    litehtml::document* doc() const { return doc_.get(); }
    litehtml::element::ptr element(int64_t id) const {
        auto it = elements_.find(id);
        return it == elements_.end() ? nullptr : it->second;
    }
    /// The widget id of a litehtml element or of its nearest ancestor with one (0 when none).
    int64_t widget_id(litehtml::element::ptr el, Rect* placement) const {
        while (el) {
            if (const char* a = el->get_attr("data-twid")) {
                if (placement) *placement = border_box_of(el);
                return std::strtoll(a, nullptr, 10);
            }
            el = el->parent();
        }
        return 0;
    }
    litehtml::element::ptr element_at(double x, double y) const {
        if (!doc_ || !doc_->root_render()) return nullptr;
        auto px = litehtml::pixel_t(float(x)), py = litehtml::pixel_t(float(y));
        return doc_->root_render()->get_element_by_point(px, py, px, py, nullptr);
    }
    /// Pointer state for :hover / :active; true when something must repaint. A lifted finger leaves
    /// :hover; a mouse released over the view keeps it.
    bool mouse(int action, double x, double y, bool hovers = false) {
        if (!doc_) return false;
        bool redraw = false;
        auto box = [&](const litehtml::position&) { redraw = true; };
        auto px = litehtml::pixel_t(float(x)), py = litehtml::pixel_t(float(y));
        switch (action) {
        case 0: doc_->on_mouse_over(px, py, px, py, box); doc_->on_lbutton_down(px, py, px, py, box); break;
        case 1: doc_->on_mouse_over(px, py, px, py, box); break;
        case 2:
            doc_->on_lbutton_up(px, py, px, py, box);
            if (!hovers) doc_->on_mouse_leave(box);
            break;
        default: doc_->on_button_cancel(box); doc_->on_mouse_leave(box); break;
        }
        return redraw;
    }
    DisplayList draw() {
        list_.clear();
#ifdef TW_LITEHTML_TWIDGET
        groups_.clear();
#endif
        if (doc_) {
            litehtml::position clip(0, 0, litehtml::pixel_t(float(r_.width_)), litehtml::pixel_t(float(vp_height_)));
            doc_->draw(0, 0, 0, &clip);
        }
        return std::move(list_);
    }
    /// A font for `measureText` (CSS font shorthand parsed loosely).
    Json measure_text(const std::string& font, const std::string& text) {
        float size = 10;
        bool italic = false;
        int weight = 400;
        std::string family = "sans-serif";
        std::vector<std::string> tokens;
        {
            std::string cur;
            bool quoted = false;
            for (char c : font) {
                if (c == '"' || c == '\'') quoted = !quoted;
                if (c == ' ' && !quoted) {
                    if (!cur.empty()) tokens.push_back(cur);
                    cur.clear();
                } else cur.push_back(c);
            }
            if (!cur.empty()) tokens.push_back(cur);
        }
        size_t i = 0;
        for (; i < tokens.size(); i++) {
            const std::string t = lower(tokens[i]);
            if (t == "italic" || t == "oblique") italic = true;
            else if (t == "bold" || t == "bolder") weight = 700;
            else if (t == "normal" || t == "lighter" || t == "small-caps") {}
            else if (std::isdigit((unsigned char)t[0]) && t.find("px") == std::string::npos && t.find('/') == std::string::npos && t.size() == 3) weight = std::atoi(t.c_str());
            else if (std::isdigit((unsigned char)t[0]) || t[0] == '.') {
                size = float(std::atof(t.c_str()));
                if (t.find("pt") != std::string::npos) size *= 96.0f / 72.0f;
                else if (t.find("em") != std::string::npos) size *= 16.0f;
                i++;
                break;
            } else break;
        }
        if (i < tokens.size()) {
            family.clear();
            for (; i < tokens.size(); i++) {
                if (!family.empty()) family += ' ';
                family += tokens[i];
            }
        }
        FontMetrics fm;
        int handle = backend_.create_font(family, size, weight, italic, fm);
        Json o = Json::object();
        float width = handle ? backend_.text_width(handle, text) : 0.0f;
        if (handle) backend_.delete_font(handle);
        o.set("width", width);
        o.set("actualBoundingBoxLeft", 0).set("actualBoundingBoxRight", width);
        o.set("actualBoundingBoxAscent", fm.ascent * 0.8f).set("actualBoundingBoxDescent", fm.descent * 0.6f);
        o.set("fontBoundingBoxAscent", fm.ascent).set("fontBoundingBoxDescent", fm.descent);
        o.set("emHeightAscent", fm.ascent).set("emHeightDescent", fm.descent);
        o.set("hangingBaseline", fm.ascent * 0.8f).set("alphabeticBaseline", 0).set("ideographicBaseline", -fm.descent);
        return o;
    }

    // ── document_container: fonts and text ──
    litehtml::uint_ptr create_font(const litehtml::font_description& descr, const litehtml::document*, litehtml::font_metrics* fm) override {
        FontMetrics m;
        int handle = backend_.create_font(descr.family, float(descr.size), descr.weight, descr.style == litehtml::font_style_italic, m);
        if (!handle) return 0;
        Font f;
        f.handle = handle;
        f.metrics = m;
        f.size = float(descr.size);
        f.decoration = descr.decoration_line;
        f.decoration_color = descr.decoration_color;
        if (fm) {
            fm->font_size = descr.size;
            fm->ascent = litehtml::pixel_t(m.ascent);
            fm->descent = litehtml::pixel_t(m.descent);
            fm->height = litehtml::pixel_t(std::max(m.height, m.ascent + m.descent));
            fm->x_height = litehtml::pixel_t(m.x_height > 0 ? m.x_height : m.ascent * 0.5f);
            fm->ch_width = litehtml::pixel_t(m.zero_width);
            fm->draw_spaces = descr.decoration_line != litehtml::text_decoration_line_none;
            fm->sub_shift = litehtml::pixel_t(f.size / 5);
            fm->super_shift = litehtml::pixel_t(f.size / 3);
        }
        litehtml::uint_ptr id = ++next_font_;
        fonts_[id] = f;
        return id;
    }
    void delete_font(litehtml::uint_ptr h) override {
        auto it = fonts_.find(h);
        if (it == fonts_.end()) return;
        backend_.delete_font(it->second.handle);
        fonts_.erase(it);
    }
    litehtml::pixel_t text_width(const char* text, litehtml::uint_ptr h) override {
        auto it = fonts_.find(h);
        if (it == fonts_.end() || !text) return 0;
        std::string key = std::to_string(it->second.handle);
        key.push_back('\0');
        key += text;
        auto c = widths_.find(key);
        if (c != widths_.end()) return litehtml::pixel_t(c->second);
        float w = backend_.text_width(it->second.handle, text);
        if (widths_.size() > 20000) widths_.clear();
        widths_[key] = w;
        return litehtml::pixel_t(w);
    }
    void draw_text(litehtml::uint_ptr, const char* text, litehtml::uint_ptr h, litehtml::web_color color, const litehtml::position& pos) override {
        auto it = fonts_.find(h);
        if (it == fonts_.end() || !text || !*text) return;
        const Font& f = it->second;
        Command c;
        c.kind = Cmd::Text;
        c.font = f.handle;
        c.text = text;
        c.color = color_of(color);
        c.x = float(pos.x);
        // litehtml hands the line box; the baseline sits `descent` above its bottom (as the cairo container does).
        c.y = float(pos.y) + float(pos.height) - f.metrics.descent;
        list_.push_back(std::move(c));
        if (f.decoration != litehtml::text_decoration_line_none) {
            float w = float(pos.width);
            if (w <= 0) w = backend_.text_width(f.handle, text);
            float thickness = std::max(1.0f, f.size / 14.0f);
            Color dc = f.decoration_color.is_current_color ? color_of(color) : color_of(f.decoration_color);
            float baseline = float(pos.y) + float(pos.height) - f.metrics.descent;
            auto line = [&](float y) {
                Command l;
                l.kind = Cmd::FillRect;
                l.rect = Rect{float(pos.x), y, w, thickness};
                l.color = dc;
                list_.push_back(std::move(l));
            };
            if (f.decoration & litehtml::text_decoration_line_underline) line(baseline + std::max(1.0f, f.metrics.descent * 0.35f));
            if (f.decoration & litehtml::text_decoration_line_line_through) line(baseline - f.metrics.ascent * 0.3f - thickness / 2);
            if (f.decoration & litehtml::text_decoration_line_overline) line(float(pos.y));
        }
    }
    litehtml::pixel_t pt_to_px(float pt) const override { return litehtml::pixel_t(pt * 96.0f / 72.0f); }
    litehtml::pixel_t get_default_font_size() const override { return 16; }
    const char* get_default_font_name() const override { return "-apple-system, system-ui, sans-serif"; }
    void draw_list_marker(litehtml::uint_ptr, const litehtml::list_marker& marker) override {
        Command c;
        c.rect = rect_of(marker.pos);
        c.color = color_of(marker.color);
        switch (marker.marker_type) {
        case litehtml::list_style_type_none: return;
        case litehtml::list_style_type_disc: c.kind = Cmd::FillEllipse; break;
        case litehtml::list_style_type_circle: c.kind = Cmd::StrokeEllipse; c.width = 1; break;
        case litehtml::list_style_type_square: c.kind = Cmd::FillRect; break;
        default: {
            auto it = fonts_.find(marker.font);
            if (it == fonts_.end()) return;
            c.kind = Cmd::Text;
            c.font = it->second.handle;
            c.text = marker_text(marker.marker_type, marker.index);
            c.x = float(marker.pos.x);
            c.y = float(marker.pos.y) + float(marker.pos.height) - it->second.metrics.descent;
        }
        }
        list_.push_back(std::move(c));
    }

    // ── images: data: only, decoded by the backend, referenced by id ──
    void load_image(const char* src, const char*, bool) override {
        if (!src) return;
        std::string url = src;
        if (images_.count(url)) return;
        Image img;
        if (url.rfind("tw-canvas:", 0) == 0) {
            // The renderer's own source for a <canvas>: tw-canvas:<node id>/<w>x<h>, drawn from the backend's bitmap.
            int id = 0, w = 0, h = 0;
            if (std::sscanf(url.c_str() + 10, "%d/%dx%d", &id, &w, &h) == 3) {
                img.canvas = id;
                img.w = float(w);
                img.h = float(h);
            }
        } else if (url.rfind("data:", 0) == 0) {
            // The backend may answer the size later (`image_ready`): the id stays, the size is 0 until then.
            img.id = ++next_image_;
            if (!backend_.load_image(img.id, url, img.w, img.h)) img.id = 0;
        } else {
            r_.pending_violations_.push_back("image refused: " + url.substr(0, 80));
        }
        images_[url] = img;
    }
    void get_image_size(const char* src, const char*, litehtml::size& sz) override {
        auto it = src ? images_.find(src) : images_.end();
        if (it == images_.end()) { sz.width = 0; sz.height = 0; return; }
        sz.width = litehtml::pixel_t(it->second.w);
        sz.height = litehtml::pixel_t(it->second.h);
    }
    void draw_image(litehtml::uint_ptr, const litehtml::background_layer& layer, const std::string& url, const std::string&) override {
        auto it = images_.find(url);
        if (it == images_.end() || (!it->second.id && !it->second.canvas)) return;
        Command c;
        c.kind = Cmd::Image;
        c.image = it->second.id;
        c.canvas = it->second.canvas;
        c.rect = rect_of(layer.origin_box);
        c.radii = radii_of(layer.border_radius);
        c.clip = rect_of(layer.clip_box);
        c.has_clip = true;
        // the border box bounds the drawing too
        Rect b = rect_of(layer.border_box);
        clip_intersect(c.clip, b);
        list_.push_back(std::move(c));
    }

    // ── backgrounds ──
    void draw_solid_fill(litehtml::uint_ptr, const litehtml::background_layer& layer, const litehtml::web_color& color) override {
        if (color.alpha == 0) return;
        Command c;
        c.kind = Cmd::FillRect;
        c.color = color_of(color);
        if (layer.is_root) {
            // the root background covers the whole canvas, like a browser's
            c.rect = Rect{0, 0, float(r_.width_), float(vp_height_)};
        } else {
            c.rect = rect_of(layer.border_box);
            c.radii = radii_of(layer.border_radius);
            c.clip = rect_of(layer.clip_box);
            c.has_clip = true;
        }
        list_.push_back(std::move(c));
    }
    void gradient_common(Command& c, const litehtml::background_layer& layer, const litehtml::background_layer::gradient_base& g) {
        c.rect = rect_of(layer.border_box);
        c.radii = radii_of(layer.border_radius);
        c.clip = rect_of(layer.clip_box);
        c.has_clip = true;
        for (const auto& p : g.color_points) c.stops.push_back(Stop{p.offset, color_of(p.color)});
    }
    void draw_linear_gradient(litehtml::uint_ptr, const litehtml::background_layer& layer, const litehtml::background_layer::linear_gradient& g) override {
        Command c;
        c.kind = Cmd::Linear;
        gradient_common(c, layer, g);
        c.ax = g.start.x; c.ay = g.start.y; c.bx = g.end.x; c.by = g.end.y;
        list_.push_back(std::move(c));
    }
    void draw_radial_gradient(litehtml::uint_ptr, const litehtml::background_layer& layer, const litehtml::background_layer::radial_gradient& g) override {
        Command c;
        c.kind = Cmd::Radial;
        gradient_common(c, layer, g);
        c.ax = g.position.x; c.ay = g.position.y; c.bx = g.radius.x; c.by = g.radius.y;
        list_.push_back(std::move(c));
    }
    void draw_conic_gradient(litehtml::uint_ptr, const litehtml::background_layer& layer, const litehtml::background_layer::conic_gradient& g) override {
        Command c;
        c.kind = Cmd::Conic;
        gradient_common(c, layer, g);
        c.ax = g.position.x; c.ay = g.position.y; c.bx = g.angle; c.by = g.radius;
        list_.push_back(std::move(c));
    }

    // ── borders: one stroke when uniform, otherwise a strip per side (dashed/dotted drawn solid) ──
    void draw_borders(litehtml::uint_ptr, const litehtml::borders& b, const litehtml::position& draw_pos, bool) override {
        auto visible = [](const litehtml::border& s) { return float(s.width) > 0 && s.style != litehtml::border_style_none && s.style != litehtml::border_style_hidden && s.color.alpha != 0; };
        bool l = visible(b.left), t = visible(b.top), rr = visible(b.right), bt = visible(b.bottom);
        if (!l && !t && !rr && !bt) return;
        auto same = [](const litehtml::border& a, const litehtml::border& c) { return a.width == c.width && a.color == c.color; };
        if (l && t && rr && bt && same(b.left, b.top) && same(b.left, b.right) && same(b.left, b.bottom)) {
            float w = float(b.left.width);
            Command c;
            c.kind = Cmd::StrokeRect;
            c.width = w;
            c.color = color_of(b.left.color);
            c.rect = Rect{float(draw_pos.x) + w / 2, float(draw_pos.y) + w / 2, float(draw_pos.width) - w, float(draw_pos.height) - w};
            Radii r = radii_of(b.radius);
            auto shrink = [&](float& v) { v = std::max(0.0f, v - w / 2); };
            shrink(r.tlx); shrink(r.tly); shrink(r.trx); shrink(r.try_); shrink(r.brx); shrink(r.bry); shrink(r.blx); shrink(r.bly);
            c.radii = r;
            list_.push_back(std::move(c));
            return;
        }
        Rect p = rect_of(draw_pos);
        auto strip = [&](const litehtml::border& s, Rect rect) {
            Command c;
            c.kind = Cmd::FillRect;
            c.color = color_of(s.color);
            c.rect = rect;
            list_.push_back(std::move(c));
        };
        if (t) strip(b.top, Rect{p.x, p.y, p.w, float(b.top.width)});
        if (bt) strip(b.bottom, Rect{p.x, p.y + p.h - float(b.bottom.width), p.w, float(b.bottom.width)});
        if (l) strip(b.left, Rect{p.x, p.y, float(b.left.width), p.h});
        if (rr) strip(b.right, Rect{p.x + p.w - float(b.right.width), p.y, float(b.right.width), p.h});
    }

    // ── chrome the renderer has none of ──
    void set_caption(const char*) override {}
    void set_base_url(const char*) override {}
    void link(const std::shared_ptr<litehtml::document>&, const litehtml::element::ptr&) override {}
    void on_anchor_click(const char*, const litehtml::element::ptr&) override {}  // navigation is the sandbox's business (openLink)
    void on_mouse_event(const litehtml::element::ptr&, litehtml::mouse_event) override {}
    void set_cursor(const char*) override {}
    void transform_text(std::string& text, litehtml::text_transform tt) override {
        // ASCII only; the platforms' Unicode case mapping would need a backend call.
        switch (tt) {
        case litehtml::text_transform_uppercase: for (auto& c : text) c = char(std::toupper((unsigned char)c)); break;
        case litehtml::text_transform_lowercase: for (auto& c : text) c = char(std::tolower((unsigned char)c)); break;
        case litehtml::text_transform_capitalize: {
            bool start = true;
            for (auto& c : text) {
                if (start) c = char(std::toupper((unsigned char)c));
                start = c == ' ';
            }
            break;
        }
        default: break;
        }
    }
    void import_css(std::string& text, const std::string&, std::string&) override { text.clear(); }  // @import is dropped by the validator anyway
    void set_clip(const litehtml::position& pos, const litehtml::border_radiuses& r) override {
        Command c;
        c.kind = Cmd::ClipPush;
        c.rect = rect_of(pos);
        c.radii = radii_of(r);
        list_.push_back(std::move(c));
    }
    void del_clip() override {
        Command c;
        c.kind = Cmd::ClipPop;
        list_.push_back(std::move(c));
    }
#ifdef TW_LITEHTML_TWIDGET
    void draw_box_shadow(litehtml::uint_ptr, const litehtml::position& box, const litehtml::border_radiuses& radius, const litehtml::shadow_paint& shadow) override {
        if (shadow.color.alpha == 0) return;
        Command c;
        c.kind = Cmd::Shadow;
        c.rect = rect_of(box);
        c.radii = radii_of(radius);
        c.color = color_of(shadow.color);
        c.ax = float(shadow.x);
        c.ay = float(shadow.y);
        c.bx = float(shadow.blur);
        c.by = float(shadow.spread);
        c.inset = shadow.inset;
        list_.push_back(std::move(c));
    }
    void begin_group(litehtml::uint_ptr, const litehtml::paint_group& group) override {
        // Backface visibility needs the accumulated matrix: a flip undone by an outer flip is a front face.
        litehtml::transform_matrix total = groups_.empty() ? group.matrix : group.matrix.then(groups_.back());
        groups_.push_back(total);
        Command c;
        c.kind = Cmd::GroupPush;
        const auto& m = group.matrix;
        float values[6] = {m.a, m.b, m.c, m.d, m.tx, m.ty};
        std::copy(std::begin(values), std::end(values), c.matrix);
        c.opacity = group.opacity;
        c.hidden = group.backface_hidden && total.a * total.d - total.b * total.c < 0;
        c.rect = rect_of(group.bounds);
        list_.push_back(std::move(c));
    }
    void end_group(litehtml::uint_ptr) override {
        if (!groups_.empty()) groups_.pop_back();
        Command c;
        c.kind = Cmd::GroupPop;
        list_.push_back(std::move(c));
    }
    void draw_path(litehtml::uint_ptr, const litehtml::vector_path& path) override {
        if (!path.fill && !path.stroke) return;
        auto p = std::make_shared<Path>();
        p->ops.reserve(path.ops.size());
        for (auto op : path.ops) p->ops.push_back(Path::Op(op));
        p->points = path.points;
        p->fill = path.fill;
        p->evenodd = path.evenodd;
        p->fill_color = color_of(path.fill_color);
        const auto& g = path.fill_gradient;
        p->gradient = g.type;
        p->x1 = g.x1; p->y1 = g.y1; p->x2 = g.x2; p->y2 = g.y2; p->r = g.r; p->fx = g.fx; p->fy = g.fy;
        for (const auto& [offset, color] : g.stops) p->stops.push_back(Stop{offset, color_of(color)});
        p->stroke = path.stroke;
        p->stroke_color = color_of(path.stroke_color);
        p->stroke_width = path.stroke_width;
        p->line_cap = path.line_cap;
        p->line_join = path.line_join;
        p->miter_limit = path.miter_limit;
        p->dashes = path.dashes;
        p->dash_offset = path.dash_offset;
        Command c;
        c.kind = Cmd::Path;
        c.path = std::move(p);
        list_.push_back(std::move(c));
    }
#endif
    void get_viewport(litehtml::position& v) const override {
        v.x = 0; v.y = 0;
        v.width = litehtml::pixel_t(float(r_.width_));
        v.height = litehtml::pixel_t(float(vp_height_));
    }
    litehtml::element::ptr create_element(const char*, const litehtml::string_map&, const std::shared_ptr<litehtml::document>&) override { return nullptr; }
    void get_media_features(litehtml::media_features& m) const override {
        m.type = litehtml::media_type_screen;
        m.width = litehtml::pixel_t(float(r_.width_));
        m.height = litehtml::pixel_t(float(vp_height_));
        m.device_width = m.width;
        m.device_height = m.height;
        m.color = 8;
        m.color_index = 256;
        m.monochrome = 0;
        m.resolution = litehtml::pixel_t(float(96.0 * r_.dpr_));
    }
    void get_language(std::string& language, std::string& culture) const override {
        language = "en";
        culture.clear();
    }

private:
    struct Font {
        int handle = 0;
        FontMetrics metrics;
        float size = 16;
        int decoration = 0;
        litehtml::web_color decoration_color;
    };
    struct Image {
        int id = 0;
        int canvas = 0;
        float w = 0, h = 0;
    };

#ifdef TW_LITEHTML_TWIDGET
    static void positioned_z(const std::shared_ptr<litehtml::render_item>& item, std::set<int>& z) {
        for (const auto& child : item->children()) {
            if (child->src_el()->is_positioned()) z.insert(child->src_el()->css().get_z_index());
            else if (!child->src_el()->css().is_paint_group()) positioned_z(child, z);
        }
    }
    /// An element's children in stacking order, as if it formed a stacking context: what litehtml's
    /// own group paint does after the element's background, without the group.
    static void draw_stacking_context(const std::shared_ptr<litehtml::render_item>& ri, litehtml::pixel_t x, litehtml::pixel_t y) {
        std::set<int> z;
        positioned_z(ri, z);
        for (int index : z)
            if (index < 0) ri->draw_children(0, x, y, nullptr, litehtml::draw_positioned, index);
        ri->draw_children(0, x, y, nullptr, litehtml::draw_block, 0);
        ri->draw_children(0, x, y, nullptr, litehtml::draw_floats, 0);
        ri->draw_children(0, x, y, nullptr, litehtml::draw_inlines, 0);
        for (int index : z)
            if (index >= 0) ri->draw_children(0, x, y, nullptr, litehtml::draw_positioned, index);
    }
#endif

    static void clip_intersect(Rect& a, const Rect& b) {
        float x0 = std::max(a.x, b.x), y0 = std::max(a.y, b.y);
        float x1 = std::min(a.x + a.w, b.x + b.w), y1 = std::min(a.y + a.h, b.y + b.h);
        a = Rect{x0, y0, std::max(0.0f, x1 - x0), std::max(0.0f, y1 - y0)};
    }

    static std::string marker_text(litehtml::list_style_type t, int index) {
        auto alpha = [](int n, char base) {
            std::string s;
            n = std::max(n, 1);
            while (n > 0) { n--; s.insert(s.begin(), char(base + n % 26)); n /= 26; }
            return s;
        };
        auto roman = [](int n, bool upper) {
            static const char* U[] = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
            static const char* L[] = {"m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i"};
            static const int V[] = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
            std::string s;
            n = std::max(n, 1);
            for (int i = 0; i < 13; i++) while (n >= V[i]) { s += upper ? U[i] : L[i]; n -= V[i]; }
            return s;
        };
        switch (t) {
        case litehtml::list_style_type_decimal_leading_zero: return (index < 10 ? "0" : "") + std::to_string(index) + ".";
        case litehtml::list_style_type_lower_alpha:
        case litehtml::list_style_type_lower_latin: return alpha(index, 'a') + ".";
        case litehtml::list_style_type_upper_alpha:
        case litehtml::list_style_type_upper_latin: return alpha(index, 'A') + ".";
        case litehtml::list_style_type_lower_roman: return roman(index, false) + ".";
        case litehtml::list_style_type_upper_roman: return roman(index, true) + ".";
        default: return std::to_string(index) + ".";
        }
    }

    Renderer& r_;
    Backend& backend_;
    litehtml::document::ptr doc_;
#ifdef TW_LITEHTML_TWIDGET
    std::vector<litehtml::transform_matrix> groups_;  // accumulated, during draw()
#endif
    int vp_height_ = int(MIN_HEIGHT);
    std::string master_css_;
    float rendered_width_ = 0;
    uint64_t layouts_ = 0;
    bool patched_ = false;
    std::unordered_map<int64_t, litehtml::element::ptr> elements_;
    std::unordered_map<litehtml::uint_ptr, Font> fonts_;
    litehtml::uint_ptr next_font_ = 0;
    std::unordered_map<std::string, float> widths_;
    std::unordered_map<std::string, Image> images_;
    int next_image_ = 0;
    DisplayList list_;
};

// ───────────────────────── Renderer ─────────────────────────

Renderer::Renderer(double width, double dpr, bool dark, Backend& backend)
    : container_(std::make_unique<Container>(*this, backend)), backend_(backend), width_(width), dpr_(dpr), dark_(dark), vp_height_(int(MIN_HEIGHT)) {
    auto make = [](const char* tag) {
        auto n = std::make_unique<Node>();
        n->tag = tag;
        return n;
    };
    html_node_ = make("html");
    head_node_ = make("head");
    body_node_ = make("body");
    head_node_->parent = html_node_.get();
    body_node_->parent = html_node_.get();
    html_node_->children = {head_node_.get(), body_node_.get()};
    // The scheme's defaults until the host's theme message (its first one) replaces them.
    apply_theme(Json::object().set("colorScheme", dark_ ? "dark" : "light"));
    rebuild();
}

void Renderer::apply_theme(const Json& theme) {
    theme_ = theme;
    theme_vars_ = theme::variables(theme);
    // The client's chrome over the widget (docs/sdk-v1.md §2.17): always set, 0px until the host's safeArea message.
    if (safe_area_vars_.empty()) safe_area_vars_ = theme::safe_area_variables(Json());
    theme_vars_.insert(theme_vars_.end(), safe_area_vars_.begin(), safe_area_vars_.end());
    // The UA layer with the theme's values in place of its var() (litehtml substitutes a var() in a shorthand after the
    // cascade, so `padding: var(…)` from the master CSS would beat the widget's own `padding: 0`) and without :where()
    // (theme.h, unwrap_where).
    container_->set_master(std::string(litehtml::master_css) + UA_EXTRA + theme::unwrap_where(theme::substitute(TW_UA_STYLESHEET, theme_vars_)));
}

Renderer::~Renderer() {
    for (const auto& [id, canvas] : canvases_) backend_.canvas_release(canvas->id());
    for (const auto& [css, font] : canvas_fonts_) backend_.delete_font(font.first);
}

double Renderer::now_s() { return now_ms() / 1000; }

uint32_t Renderer::px_width() const { return uint32_t(std::ceil(width_ * dpr_)); }
uint32_t Renderer::px_height() const { return uint32_t(std::ceil(vp_height_ * dpr_)); }

std::vector<Json> Renderer::receive(const Json& msg) {
    std::vector<Json> out;
    const std::string& type = msg["type"].asString();
    if (type == "ops") {
        for (const Json& op : msg["ops"].arr()) apply_op(op, out);
        rebuild();
        report_size(out);
    } else if (type == "query") {
        Json res = Json::object();
        binary_.clear();
        res.set("type", "result").set("qid", msg["qid"]);
        res.set("result", msg.find("query") ? answer(msg["query"]) : Json::object().set("error", "no query"));
        if (!binary_.empty()) res.set("binaryBase64", std::move(binary_));
        binary_.clear();
        out.push_back(std::move(res));
    } else if (type == "anim") {
        if (!frozen_) {
            // `now` is the sandbox clock when the batch was sent: it maps the records' start times onto ours.
            double now = now_ms();
            animator_.apply_web(msg, now - msg["now"].asNumber(now));
            animate();
            report_size(out);
        }
    } else if (type == "listen") {
        interest_.clear();
        for (const Json& t : msg["types"].arr()) if (t.isString()) interest_.insert(t.asString());
    } else if (type == "preventDefault") {
        prevent_.clear();
        for (const Json& k : msg["keys"].arr()) if (k.isString()) prevent_.insert(k.asString());
    } else if (type == "frozen") {
        freeze(msg["reason"].asString(), msg["fatal"].asBool());
        rebuild();
    } else if (type == "theme") {
        apply_theme(msg["theme"]);
        rebuild();
        report_size(out);
    } else if (type == "safeArea") {
        safe_area_vars_ = theme::safe_area_variables(msg["inset"]);
        apply_theme(Json(theme_));
        rebuild();
        report_size(out);
    }
    flush_pending(out);
    return out;
}

void Renderer::flush_pending(std::vector<Json>& out) {
    for (auto& ev : pending_events_) out.push_back(std::move(ev));
    pending_events_.clear();
    for (auto& v : pending_violations_) out.push_back(Json::object().set("type", "violation").set("detail", v));
    pending_violations_.clear();
}

void Renderer::set_width(double width, std::vector<Json>& out) {
    if (std::fabs(width - width_) < 0.01) return;
    width_ = width;
    rebuild();
    report_size(out);
}

void Renderer::set_theme(bool dark, std::vector<Json>& out) {
    if (dark == dark_) return;
    dark_ = dark;
    rebuild();
    report_size(out);
}

// ───────────────────────── ops ─────────────────────────

Renderer::Node* Renderer::node(int64_t id) const {
    auto it = nodes_.find(id);
    if (it != nodes_.end()) return it->second.get();
    auto a = aliases_.find(id);
    return a == aliases_.end() ? nullptr : a->second;
}

void Renderer::apply_op(const Json& op, std::vector<Json>& out) {
    const std::string& kind = op["op"].asString();
    int64_t id = int64_t(op["id"].asNumber());
    auto str = [&](const char* k) -> const std::string& { return op[k].asString(); };
    if (kind == "el") {
        const std::string& adopt_name = str("adopt");
        if (!adopt_name.empty()) {
            Node* n = adopt_name == "html" ? html_node_.get() : adopt_name == "head" ? head_node_.get() : adopt_name == "body" ? body_node_.get() : nullptr;
            if (!n) return;
            adopt(id, n, op["attrs"]);
            return;
        }
        if (nodes_.count(id)) return;  // the validator never reuses a live id; keep the first
        auto n = std::make_unique<Node>();
        n->kind = Node::Element;
        if (!css::is_tag_name(str("tag"))) {
            out.push_back(Json::object().set("type", "violation").set("detail", "element name"));
            return;
        }
        n->id = id;
        n->tag = lower(str("tag"));
        n->svg = op["ns"].asString() == "svg";
        for (const Json& pair : op["attrs"].arr()) {
            if (!pair[0].isString() || !pair[1].isString()) continue;
            if (starts_with_on(pair[0].asString())) continue;
            if (pair[0].asString() == "style") set_inline_style(n->styles, pair[1].asString());
            else n->attrs.emplace_back(pair[0].asString(), pair[1].asString());
        }
        nodes_[id] = std::move(n);
        return;
    }
    if (kind == "text" || kind == "comment") {
        if (nodes_.count(id)) return;
        auto n = std::make_unique<Node>();
        n->kind = kind == "text" ? Node::Text : Node::Comment;
        n->id = id;
        n->data = str("data");
        nodes_[id] = std::move(n);
        return;
    }
    Node* n = node(id);
    if (!n) return;
    bool is_root = n == html_node_.get() || n == head_node_.get() || n == body_node_.get();
    if (kind == "attr") {
        const std::string& name = str("name");
        if (starts_with_on(name) || !css::is_attribute_name(name)) {
            out.push_back(Json::object().set("type", "violation").set("detail", "attribute " + name.substr(0, 64)));
            return;
        }
        if (name == "style") {
            set_inline_style(n->styles, str("value"));
            return;
        }
        bool found = false;
        for (auto& a : n->attrs) if (a.first == name) { a.second = str("value"); found = true; }
        if (!found) n->attrs.emplace_back(name, str("value"));
        // Setting a canvas's width or height clears its bitmap, as in a browser.
        if (n->tag == "canvas" && (name == "width" || name == "height")) {
            if (auto it = canvases_.find(n->id); it != canvases_.end()) {
                int w = it->second->width(), h = it->second->height();
                (name == "width" ? w : h) = std::max(0, std::atoi(str("value").c_str()));
                std::vector<CanvasOp> ops;
                it->second->resize(w, h, ops);
                backend_.canvas_run(it->second->id(), it->second->width(), it->second->height(), ops);
            }
        }
    } else if (kind == "rattr") {
        const std::string& name = str("name");
        if (name == "style") n->styles.clear();
        n->attrs.erase(std::remove_if(n->attrs.begin(), n->attrs.end(), [&](auto& a) { return a.first == name; }), n->attrs.end());
    } else if (kind == "data") {
        if (n->kind != Node::Element) n->data = str("data");
    } else if (kind == "ins") {
        if (is_root) return;
        int64_t parent = int64_t(op["parent"].asNumber());
        detach(n);
        if (parent == 0) return;
        Node* p = node(parent);
        if (!p || p->kind != Node::Element) return;
        // no cycles: the validator forbids them, but a hostile batch must not hang the renderer
        for (Node* a = p; a; a = a->parent) if (a == n) return;
        n->parent = p;
        const Json* before = op.find("before");
        if (before && before->isNumber()) {
            Node* b = node(int64_t(before->asNumber()));
            if (b && b->parent == p) {
                auto it = std::find(p->children.begin(), p->children.end(), b);
                p->children.insert(it, n);
                return;
            }
        }
        p->children.push_back(n);
    } else if (kind == "rm") {
        if (is_root) return;
        detach(n);
        forget(n);
    } else if (kind == "style") {
        std::string name = css::starts_with(str("name"), "--") ? str("name") : lower(str("name"));
        // The host's theme variables on the root are not the widget's to change (it can shadow them on its own elements).
        if (n == html_node_.get() && theme::is_variable(name)) return;
        if (!css::is_property_name(name)) {
            out.push_back(Json::object().set("type", "violation").set("detail", "property name"));
            return;
        }
        std::string value = str("value");
        if (op["important"].asBool() && !value.empty()) value += " !important";
        auto it = std::find_if(n->styles.begin(), n->styles.end(), [&](auto& s) { return s.first == name; });
        if (value.empty()) {
            if (it != n->styles.end()) n->styles.erase(it);
        } else if (it != n->styles.end()) it->second = value;
        else n->styles.emplace_back(name, value);
    } else if (kind == "css") {
        if (n->tag == "style") n->data = str("text");
    } else if (kind == "prop") {
        const std::string& name = str("name");
        const Json& value = op["value"];
        auto set_attr = [&](const std::string& a, const std::string& v) {
            for (auto& p : n->attrs) if (p.first == a) { p.second = v; return; }
            n->attrs.emplace_back(a, v);
        };
        auto remove_attr = [&](const std::string& a) {
            n->attrs.erase(std::remove_if(n->attrs.begin(), n->attrs.end(), [&](auto& p) { return p.first == a; }), n->attrs.end());
        };
        if (name == "value") {
            std::string v = value.isString() ? value.asString() : value.isNull() ? std::string() : value.dump();
            if (n->tag == "textarea") n->data = v, n->children.clear();
            else set_attr("value", v);
        } else if (name == "checked" || name == "selected" || name == "indeterminate") {
            if (value.asBool()) set_attr(name, ""); else remove_attr(name);
        } else if (name == "selectedIndex") {
            int idx = value.asInt(-1), i = 0;
            for (Node* c : n->children) {
                if (c->tag != "option") continue;
                c->attrs.erase(std::remove_if(c->attrs.begin(), c->attrs.end(), [](auto& p) { return p.first == "selected"; }), c->attrs.end());
                if (i++ == idx) c->attrs.emplace_back("selected", "");
            }
        }
        // scrollTop/scrollLeft: litehtml has no scrolling boxes to move
    } else if (kind == "call") {
        const std::string& method = str("method");
        if (method == "showModal" || method == "show" || method == "showPopover") {
            for (auto& p : n->attrs) if (p.first == "open") return;
            n->attrs.emplace_back("open", "");
        } else if (method == "close" || method == "hidePopover") {
            n->attrs.erase(std::remove_if(n->attrs.begin(), n->attrs.end(), [](auto& p) { return p.first == "open"; }), n->attrs.end());
        }
        // focus/blur/scroll*/select*: nothing to do without a focus model or scrolling
    } else if (kind == "canvas") {
        if (n->tag == "canvas" && !n->svg) {
            if (Canvas* canvas = canvas_for(n)) run_canvas(*canvas, op["cmds"]);
        }
    }
}

void Renderer::adopt(int64_t id, Node* n, const Json& attrs) {
    if (n->id != 0 && n->id != id) aliases_.erase(n->id);
    n->id = id;
    // a non-owning alias: the fixed nodes live in html_node_/head_node_/body_node_
    nodes_.erase(id);
    aliases_[id] = n;
    for (const Json& pair : attrs.arr()) {
        if (!pair[0].isString() || !pair[1].isString() || starts_with_on(pair[0].asString())) continue;
        if (pair[0].asString() == "style") {
            set_inline_style(n->styles, pair[1].asString());
            continue;
        }
        bool found = false;
        for (auto& a : n->attrs) if (a.first == pair[0].asString()) { a.second = pair[1].asString(); found = true; }
        if (!found) n->attrs.emplace_back(pair[0].asString(), pair[1].asString());
    }
}

void Renderer::detach(Node* n) {
    if (!n->parent) return;
    auto& ch = n->parent->children;
    ch.erase(std::remove(ch.begin(), ch.end(), n), ch.end());
    n->parent = nullptr;
}

void Renderer::forget(Node* n) {
    forget_canvas(n->id);
    for (Node* c : n->children) {
        c->parent = nullptr;
        forget(c);
    }
    n->children.clear();
    nodes_.erase(n->id);  // deletes n
}

// ───────────────────────── serialisation ─────────────────────────

void Renderer::serialize_node(const Node* n, std::string& out) const {
    switch (n->kind) {
    case Node::Comment: return;
    case Node::Text: escape_text(n->data, out); return;
    case Node::Element: break;
    }
    if (n->tag == "script" || n->tag == "template") return;  // never forwarded by the validator; belt and braces
    if (!css::is_tag_name(n->tag)) return;
    out.push_back('<');
    out += n->tag;
    if (n->id) {
        out += " data-twid=\"";
        out += std::to_string(n->id);
        out.push_back('"');
    }
    // The fork lays a <canvas> out as an image: its source is the backend's bitmap.
    const Canvas* canvas = nullptr;
    if (n->tag == "canvas" && !n->svg) {
        if (auto it = canvases_.find(n->id); it != canvases_.end()) canvas = it->second.get();
    }
    if (canvas) out += " src=\"tw-canvas:" + std::to_string(canvas->id()) + "/" + std::to_string(canvas->width()) + "x" + std::to_string(canvas->height()) + "\"";
    bool styled = !n->styles.empty() || overrides_.count(n->id);
    for (const auto& [name, value] : n->attrs) {
        if (name == "style") { styled = true; continue; }
        if ((canvas && name == "src") || !css::is_attribute_name(name)) continue;
        out.push_back(' ');
        out += name;
        out += "=\"";
        escape_attr(value, out);
        out.push_back('"');
    }
    if (styled) {
        out += " style=\"";
        write_style(n, out);
        out.push_back('"');
    }
    out.push_back('>');
    if (!n->svg && is_void(n->tag)) return;
    if (n->tag == "style") {
        std::string css = css::resolve_light_dark(rewrite_color_scheme(n->data, dark_), dark_);
        size_t p = 0;
        while ((p = css.find("</", p)) != std::string::npos) { css.replace(p, 2, "<\\/"); p += 3; }
        out += css;
    } else if (n->tag == "textarea") {
        escape_text(n->data, out);
        for (const Node* c : n->children) serialize_node(c, out);
    } else {
        for (const Node* c : n->children) serialize_node(c, out);
    }
    out += "</";
    out += n->tag;
    out.push_back('>');
}

void Renderer::write_style(const Node* n, std::string& out) const {
    // Built plain, escaped once: the same text is also handed to the fork's style holes, which must
    // match what this writes into the HTML byte for byte.
    std::string plain;
    auto declare = [&](const std::string& name, const std::string& value) {
        if (!plain.empty() && plain.back() != ';') plain.push_back(';');
        plain += name;
        plain.push_back(':');
        plain += css::resolve_light_dark(value, dark_);
    };
    if (n == html_node_.get())
        for (const auto& [name, value] : theme_vars_) declare(name, value + " !important");
    for (const auto& [name, value] : n->styles)
        if (css::is_property_name(name)) declare(name, value);
    if (auto it = overrides_.find(n->id); n->id && it != overrides_.end()) {
        // The fork records nothing after this marker as declared, so animated values never feed back
        // into the next scan as the element's own.
        declare("-lh-anim", "1");
        for (const auto& [name, value] : it->second)
            if (css::is_property_name(name)) declare(name, value);
    }
    escape_attr(plain, out);
}

std::string Renderer::serialize() const {
    std::string out;
    out.reserve(4096);
    out += "<!DOCTYPE html>";
    // the frozen chrome: html attribute, badge style in head, badge in body (renderer elements, never in the widget's table)
    const Node* html = html_node_.get();
    out += "<html";
    if (html->id) out += " data-twid=\"" + std::to_string(html->id) + "\"";
    if (frozen_) out += frozen_fatal_ ? " data-tw-frozen=\"fatal\"" : " data-tw-frozen=\"paused\"";
    bool styled = !html->styles.empty() || overrides_.count(html->id) || !theme_vars_.empty();
    for (const auto& [name, value] : html->attrs) {
        if (name == "style") { styled = true; continue; }
        if (!css::is_attribute_name(name)) continue;
        out += " " + name + "=\"";
        escape_attr(value, out);
        out.push_back('"');
    }
    if (styled) {
        out += " style=\"";
        write_style(html, out);
        out.push_back('"');
    }
    out.push_back('>');
    for (const Node* c : html->children) {
        if (c == body_node_.get() && frozen_) {
            // badge before the body's own children would shift them; append it inside instead
            std::string body;
            serialize_node(c, body);
            size_t close = body.rfind("</body>");
            if (close != std::string::npos) {
                std::string badge = "<div data-tw-badge=\"" + std::string(frozen_fatal_ ? "fatal" : "paused") + "\">";
                escape_text(frozen_reason_, badge);
                badge += "</div>";
                body.insert(close, badge);
            }
            out += body;
        } else if (c == head_node_.get() && frozen_) {
            std::string head;
            serialize_node(c, head);
            size_t close = head.rfind("</head>");
            const char* css = "<style>[data-tw-frozen=fatal] body{opacity:.85}[data-tw-badge]{position:fixed;right:6px;bottom:6px;z-index:2147483647;font:11px/1.3 -apple-system,system-ui,sans-serif;color:#fff;background:rgba(60,60,60,.75);padding:2px 7px;border-radius:9px}</style>";
            if (close != std::string::npos) head.insert(close, css);
            out += head;
        } else {
            serialize_node(c, out);
        }
    }
    out += "</html>";
    return out;
}

void Renderer::rebuild() {
    build_document();
    animate();
}

void Renderer::build_document() {
    double t0 = now_ms();
    html_ = serialize();
    double t1 = now_ms();
    stats_.serialize_ms = t1 - t0;
    stats_.html_bytes = html_.size();
    stats_.parse_ms = 0;
    stats_.passes = 0;
    // Like the web iframe: the viewport follows the document height (100vh must not explode), converging in a few passes.
    for (int pass = 0; pass < MAX_PASSES; pass++) {
        double a = now_ms();
        container_->build(html_, vp_height_);
        double b = now_ms();
        stats_.parse_ms += b - a;  // litehtml lays out inside createFromString+render; not separable
        stats_.passes++;
        if (pass == 0 && container_->patched()) stats_.patched++;
        int h = int(std::ceil(std::clamp(container_->height(), MIN_HEIGHT, MAX_HEIGHT)));
        if (h == vp_height_) break;
        vp_height_ = h;
    }
    doc_height_ = std::ceil(container_->height());
    stats_.builds++;
    dirty_ = true;
}

// ───────────────────────── animations ─────────────────────────

void Renderer::animate() {
#ifdef TW_LITEHTML_TWIDGET
    if (animating_ || frozen_) return;
    animating_ = true;
    double now = now_s();
    std::vector<std::string> sheets;
    std::string key;
    std::function<void(const Node*)> visit = [&](const Node* n) {
        if (n->kind != Node::Element) return;
        if (n->tag == "style") {
            sheets.push_back(css::resolve_light_dark(rewrite_color_scheme(n->data, dark_), dark_));
            key += sheets.back();
            key.push_back('\x01');
            return;
        }
        for (const Node* c : n->children) visit(c);
    };
    visit(html_node_.get());
    if (key != sheets_key_) {
        sheets_key_ = std::move(key);
        animator_.set_style_sheets(sheets);
    }
    animator_.prune([this](int64_t id) { return node(id) != nullptr; });
    auto declarations = container_->declarations(Animator::watched(), Animator::entry_properties(), {});
    animator_.underlying.clear();
    auto targets = animator_.web_targets();
    if (!targets.empty()) {
        // Web Animations targets are read whatever they declare: their keyframes compose onto these values.
        std::vector<std::string> names = Animator::watched();
        for (int64_t id : targets)
            for (const std::string& p : animator_.web_properties(id))
                if (std::find(names.begin(), names.end(), p) == names.end()) names.push_back(p);
        for (auto& [id, values] : container_->declarations(names, {}, targets)) {
            auto& declared = declarations[id];
            for (const auto& [k, v] : values)
                if (!css::starts_with(k, "animation") && !css::starts_with(k, "transition")) declared.emplace(k, v);
            animator_.underlying[id] = std::move(values);
        }
    }
    animator_.update(declarations, now);
    update_composition(now);
    auto sampled = animator_.sample(now);
    strip_layers(sampled);
    queue_animation_events();
    if (sampled != overrides_) {
        overrides_ = std::move(sampled);
        build_document();
    }
    animating_ = false;
#endif
}

void Renderer::apply_overrides(StyleOverrides sampled) {
    if (sampled == overrides_) return;
    std::vector<int64_t> changed;
    for (const auto& [id, values] : sampled) {
        auto it = overrides_.find(id);
        if (it == overrides_.end() || it->second != values) changed.push_back(id);
    }
    for (const auto& [id, values] : overrides_)
        if (!sampled.count(id)) changed.push_back(id);
    overrides_ = std::move(sampled);
#ifdef TW_LITEHTML_TWIDGET
    // An animation frame changes style attributes only: patch them in place instead of serialising
    // the whole tree. Falls back to a build when an element has no style attribute yet, or the
    // document height moved.
    std::vector<std::pair<litehtml::element::ptr, std::string>> patches;
    bool ok = true;
    for (int64_t id : changed) {
        const Node* n = node(id);
        auto el = container_->element(id);
        if (!n || !el) {
            ok = false;
            break;
        }
        std::string style;
        write_style(n, style);
        patches.emplace_back(std::move(el), std::move(style));
    }
    double t0 = now_ms();
    if (ok && container_->restyle(patches)) {
        int h = int(std::ceil(std::clamp(container_->height(), MIN_HEIGHT, MAX_HEIGHT)));
        if (h == vp_height_) {
            doc_height_ = std::ceil(container_->height());
            stats_.parse_ms = now_ms() - t0;
            stats_.serialize_ms = 0;
            stats_.passes = 1;
            stats_.builds++;
            stats_.patched++;
            dirty_ = true;
            return;
        }
    }
#endif
    build_document();
}

void Renderer::queue_animation_events() {
    for (const AnimationEvent& e : animator_.take_events()) {
        if (frozen_ || !interest_.count(e.type)) continue;
        Json ev = Json::object();
        ev.set("type", e.type).set("targetId", double(e.target));
        ev.set(e.type == "transitionend" ? "propertyName" : "animationName", e.name);
        ev.set("elapsedTime", e.elapsed);
        pending_events_.push_back(Json::object().set("type", "event").set("ev", std::move(ev)));
    }
}

std::vector<Json> Renderer::tick() {
    std::vector<Json> out;
    flush_moves(out);
    double now = now_s();
    if (!frozen_ && (animator_.active(now) || now >= wake_at_)) {
        update_composition(now);
        auto sampled = animator_.sample(now);
        strip_layers(sampled);
        queue_animation_events();
        apply_overrides(std::move(sampled));
        report_size(out);
    }
    flush_pending(out);
    return out;
}

bool Renderer::wants_tick() const {
    return !frozen_ && (!pending_moves_.empty() || animator_.active(now_s(), layer_ids_));
}

double Renderer::next_tick_in() const {
    if (frozen_) return std::numeric_limits<double>::infinity();
    return std::max(0.0, wake_at_ - now_s());
}

// ───────────────────────── compositor layers ─────────────────────────

void Renderer::set_compositing(bool enabled) {
    if (enabled == compositing_) return;
    compositing_ = enabled;
    composition_key_ = 0;
    rebuild();
}

void Renderer::strip_layers(StyleOverrides& sampled) const {
    for (int64_t id : layer_ids_) {
        auto it = sampled.find(id);
        if (it == sampled.end()) continue;
        auto& values = it->second;
        values.erase(std::remove_if(values.begin(), values.end(), [](const auto& v) { return is_layer_property(v.first); }), values.end());
        if (values.empty()) sampled.erase(it);
    }
}

void Renderer::update_composition(double now) {
    wake_at_ = std::numeric_limits<double>::infinity();
    auto drop_all = [&] {
        if (promoted_.empty()) return;
        promoted_.clear();
        layer_ids_.clear();
        composition_key_ = 0;
        dirty_ = true;
    };
#ifdef TW_LITEHTML_TWIDGET
    if (!compositing_ || frozen_) return drop_all();
    struct Want {
        litehtml::element::ptr el;
        Animator::Plan plan;
        Rect box;
        uint64_t signature;
    };
    std::vector<std::pair<int64_t, Want>> wanted;
    bool iterations = interest_.count("animationiteration") > 0;
    auto ids = animator_.animated_ids(now);
    uint64_t key = container_->layouts() * 0x9E3779B97F4A7C15ull ^ std::hash<double>()(width_);
    for (int64_t id : ids) {
        auto plan = animator_.plan(id, now, iterations);
        if (!plan) continue;
        wake_at_ = std::min(wake_at_, plan->wake);
        if (!plan->ready || (!plan->repeat && plan->duration < MIN_LAYER_SPAN)) continue;
        auto properties = animator_.animated_properties(id);
        if (!std::all_of(properties.begin(), properties.end(), [](const std::string& p) { return is_layer_property(p); })) continue;
        const Node* n = node(id);
        auto el = container_->element(id);
        Rect box;
        if (!n || n->svg || n->tag == "svg" || !el || !container_->layer_candidate(el, box)) continue;
        // A layer holds a still picture: nothing inside or around it may animate.
        if (std::any_of(ids.begin(), ids.end(), [&](int64_t other) { return other != id && (is_ancestor(other, id) || is_ancestor(id, other)); })) continue;
        uint64_t signature = animator_.signature(id);
        key = (key * 1099511628211ull) ^ uint64_t(id) ^ (signature << 1) ^ uint64_t(plan->repeat);
        wanted.emplace_back(id, Want{std::move(el), *plan, box, signature});
    }
    if (wanted.empty()) return drop_all();
    if (key == composition_key_) return;
    composition_key_ = key;

    std::map<int64_t, Promotion> next;
    std::unordered_map<int64_t, litehtml::element::ptr> elements;
    for (auto& [id, want] : wanted) {
        Promotion p;
        p.box = want.box;
        p.signature = want.signature;
        if (!container_->clip_of(want.el, p.has_clip, p.clip, p.clip_radii)) continue;
        // A running track stays when nothing about it changed, and while it still covers what the
        // animations will do: the end of a one-shot track, or the same loop.
        auto old = promoted_.find(id);
        bool same_span = old != promoted_.end() &&
                         (want.plan.repeat ? old->second.track.repeat && std::fabs(old->second.track.duration - want.plan.duration) < 1e-6
                                           : !old->second.track.repeat && std::fabs(old->second.track.start + old->second.track.duration - (want.plan.start + want.plan.duration)) < 1e-3);
        if (same_span && old->second.signature == want.signature && std::memcmp(&old->second.box, &want.box, sizeof(Rect)) == 0) {
            p.track = old->second.track;
            p.track_version = old->second.track_version;
        } else {
            const Rect& box = want.box;
            auto origin = css::parse_origin(animator_.base_value(id, "transform-origin"), box.w, box.h);
            if (!origin) continue;
            Affine to = Affine::translate(box.x + origin->first, box.y + origin->second);
            Affine from = Affine::translate(-(box.x + origin->first), -(box.y + origin->second));
            const Animator::Plan& plan = want.plan;
            int64_t target = id;
            auto sample = [&](double t, LayerKey& out) {
                StyleValues values = animator_.peek(target, plan.start + t * plan.duration);
                auto value = [&](const char* property) {
                    for (auto it = values.rbegin(); it != values.rend(); ++it)
                        if (it->first == property) return it->second;
                    return animator_.base_value(target, property);
                };
                auto translate = css::parse_translate(value("translate"), box.w, box.h);
                auto rotate = css::parse_rotate(value("rotate"));
                auto scale = css::parse_scale(value("scale"));
                auto transform = css::parse_transform(value("transform"), box.w, box.h);
                auto opacity = css::parse_opacity(value("opacity"));
                if (!translate || !rotate || !scale || !transform || !opacity) return false;
                out.matrix = to * *translate * *rotate * *scale * *transform * from;
                out.opacity = *opacity;
                return true;
            };
            p.track.start = plan.start;
            p.track.duration = plan.duration;
            p.track.repeat = plan.repeat;
            if (!bake_track(sample, box, p.track.keys)) continue;
            p.track_version = next_version_++;
        }
        // What the layer can cover, and how fine to rasterise it.
        float x0 = 1e30f, y0 = 1e30f, x1 = -1e30f, y1 = -1e30f, scale = 1;
        for (const LayerKey& k : p.track.keys) {
            const float corners[4][2] = {{p.box.x, p.box.y}, {p.box.x + p.box.w, p.box.y}, {p.box.x, p.box.y + p.box.h}, {p.box.x + p.box.w, p.box.y + p.box.h}};
            for (const auto& c : corners) {
                float x = k.matrix.apply_x(c[0], c[1]), y = k.matrix.apply_y(c[0], c[1]);
                x0 = std::min(x0, x); y0 = std::min(y0, y); x1 = std::max(x1, x); y1 = std::max(y1, y);
            }
            scale = std::max(scale, std::sqrt(std::fabs(k.matrix.a * k.matrix.d - k.matrix.b * k.matrix.c)));
        }
        p.raster_scale = std::min(scale, 3.0f);
        p.reach = Rect{x0, y0, x1 - x0, y1 - y0};
        if (p.has_clip) {
            float cx0 = std::max(p.reach.x, p.clip.x), cy0 = std::max(p.reach.y, p.clip.y);
            float cx1 = std::min(p.reach.x + p.reach.w, p.clip.x + p.clip.w), cy1 = std::min(p.reach.y + p.reach.h, p.clip.y + p.clip.h);
            p.reach = Rect{cx0, cy0, std::max(0.0f, cx1 - cx0), std::max(0.0f, cy1 - cy0)};
        }
        elements[id] = want.el;
        next.emplace(id, std::move(p));
    }

    // Only a layer nothing paints over can go above the document. Dropping one can uncover
    // another's neighbour, so this settles over a few rounds.
    auto trace = container_->paint_order();
    for (int round = 0; round < 4 && !next.empty(); round++) {
        std::unordered_set<const litehtml::element*> ignore;
        for (const auto& [id, p] : next) ignore.insert(elements[id].get());
        std::vector<std::pair<int64_t, double>> uncovered;
        for (const auto& [id, p] : next) {
            if (p.reach.empty() || container_->covered(elements[id], p.reach, trace, ignore)) continue;
            double s = dpr_ * p.raster_scale;
            uncovered.emplace_back(id, double(p.box.w + 2 * LAYER_MARGIN) * (p.box.h + 2 * LAYER_MARGIN) * s * s);
        }
        std::sort(uncovered.begin(), uncovered.end(), [](const auto& a, const auto& b) { return a.second < b.second; });
        std::map<int64_t, Promotion> admitted;
        double pixels = 0;
        for (const auto& [id, cost] : uncovered) {
            if (admitted.size() >= MAX_LAYERS || pixels + cost > MAX_LAYER_PIXELS) continue;
            pixels += cost;
            admitted.emplace(id, std::move(next[id]));
        }
        bool stable = admitted.size() == next.size();
        next = std::move(admitted);
        if (stable) break;
    }
    for (auto& [id, p] : next) {
        auto ri = elements[id]->get_render_item();
        p.order = size_t(std::find(trace.begin(), trace.end(), ri.get()) - trace.begin());
    }

    bool changed = next.size() != promoted_.size();
    for (const auto& [id, p] : next) {
        auto old = promoted_.find(id);
        changed = changed || old == promoted_.end() || old->second.track_version != p.track_version || old->second.order != p.order ||
                  std::memcmp(&old->second.box, &p.box, sizeof(Rect)) != 0;
    }
    promoted_ = std::move(next);
    layer_ids_.clear();
    for (const auto& [id, p] : promoted_) layer_ids_.insert(id);
    if (changed) dirty_ = true;
#else
    (void)now;
    drop_all();
#endif
}

// ───────────────────────── canvas ─────────────────────────

Canvas* Renderer::canvas_for(Node* n) {
    if (auto it = canvases_.find(n->id); it != canvases_.end()) return it->second.get();
    if (canvases_.size() >= MAX_CANVASES) return nullptr;
    auto size = [&](const char* name, int fallback) {
        for (const auto& [k, v] : n->attrs) {
            std::string t = css::trim(v);
            if (k == name && !t.empty() && std::isdigit((unsigned char)t[0])) return std::atoi(t.c_str());
        }
        return fallback;
    };
    auto canvas = std::make_unique<Canvas>(int(n->id), size("width", 300), size("height", 150), static_cast<CanvasHost&>(*this));
    Canvas* raw = canvas.get();
    canvases_[n->id] = std::move(canvas);
    CanvasOp reset;
    reset.kind = CanvasOp::Reset;
    backend_.canvas_run(raw->id(), raw->width(), raw->height(), {reset});
    return raw;
}

void Renderer::run_canvas(Canvas& canvas, const Json& cmds) {
    // Fonts are held across one batch at most, so a widget cycling through font strings cannot grow this.
    if (canvas_fonts_.size() > 64) {
        for (const auto& [font_css, font] : canvas_fonts_) backend_.delete_font(font.first);
        canvas_fonts_.clear();
    }
    std::vector<CanvasOp> ops;
    canvas.run(cmds, ops);
    if (ops.empty()) return;
    backend_.canvas_run(canvas.id(), canvas.width(), canvas.height(), ops);
    dirty_ = true;
}

void Renderer::forget_canvas(int64_t id) {
    auto it = canvases_.find(id);
    if (it == canvases_.end()) return;
    backend_.canvas_release(it->second->id());
    canvases_.erase(it);
}

int Renderer::canvas_font(const std::string& font, FontMetrics& metrics) {
    if (auto it = canvas_fonts_.find(font); it != canvas_fonts_.end()) {
        metrics = it->second.second;
        return it->second.first;
    }
    css::Font f = css::parse_font(font);
    int handle = backend_.create_font(f.family, f.size, f.weight, f.italic, metrics);
    if (handle) canvas_fonts_.emplace(font, std::make_pair(handle, metrics));
    return handle;
}

float Renderer::canvas_text_width(int font, const std::string& text) { return backend_.text_width(font, text); }

bool Renderer::canvas_source(int64_t id, int& image, int& canvas, float& w, float& h) {
    if (auto it = canvases_.find(id); it != canvases_.end()) {
        canvas = it->second->id();
        w = float(it->second->width());
        h = float(it->second->height());
        return true;
    }
    const Node* n = node(id);
    if (!n || n->tag != "img") return false;
    for (const auto& [k, v] : n->attrs)
        if (k == "src" && v.rfind("data:", 0) == 0) return container_->image_of(v, image, w, h);
    return false;
}

int64_t Renderer::canvas_allowance(int id) {
    int64_t used = 0;
    for (const auto& [node_id, canvas] : canvases_)
        if (canvas->id() != id) used += canvas->pixels();
    return Canvas::MAX_PIXELS - used;
}

void Renderer::report_size(std::vector<Json>& out) {
    Json msg = Json::object();
    msg.set("type", "size").set("width", width_).set("height", doc_height_);
    msg.set("innerWidth", width_).set("innerHeight", vp_height_).set("dpr", dpr_);
    std::string key = msg.dump();
    if (key != last_size_) {
        last_size_ = key;
        out.push_back(std::move(msg));
    }
}

DisplayList Renderer::paint() {
    dirty_ = false;
#ifdef TW_LITEHTML_TWIDGET
    if (!promoted_.empty() || !composition_.layers.empty()) {
        std::vector<std::pair<size_t, int64_t>> order;
        std::vector<litehtml::element::ptr> skip;
        for (const auto& [id, p] : promoted_) {
            if (auto el = container_->element(id)) {
                order.emplace_back(p.order, id);
                skip.push_back(std::move(el));
            }
        }
        DisplayList list = container_->draw(skip);
        std::sort(order.begin(), order.end());
        Composition next;
        bool changed = order.size() != composition_.layers.size();
        for (const auto& [index, id] : order) {
            const Promotion& p = promoted_.at(id);
            Layer layer;
            layer.id = id;
            layer.content = container_->draw_layer(container_->element(id), skip, layer.bounds);
            // A canvas repaints without its command changing: such a layer is always new.
            bool canvas = std::any_of(layer.content.begin(), layer.content.end(), [](const Command& c) { return c.canvas != 0; });
            layer.content_version = canvas ? next_version_++ : paint_digest(layer.content);
            layer.raster_scale = p.raster_scale;
            layer.has_clip = p.has_clip;
            layer.clip = p.clip;
            layer.clip_radii = p.clip_radii;
            layer.track_version = p.track_version;
            layer.track = p.track;
            size_t i = next.layers.size();
            if (!changed) {
                const Layer& old = composition_.layers[i];
                changed = old.id != id || old.content_version != layer.content_version || old.track_version != layer.track_version ||
                          std::memcmp(&old.bounds, &layer.bounds, sizeof(Rect)) != 0 || old.has_clip != layer.has_clip ||
                          std::memcmp(&old.clip, &layer.clip, sizeof(Rect)) != 0 || std::memcmp(&old.clip_radii, &layer.clip_radii, sizeof(Radii)) != 0;
            }
            next.layers.push_back(std::move(layer));
        }
        next.version = changed ? next_version_++ : composition_.version;
        composition_ = std::move(next);
        return list;
    }
#endif
    return container_->draw();
}

// ───────────────────────── queries ─────────────────────────

Json Renderer::answer(const Json& q) {
    const std::string& kind = q["q"].asString();
    litehtml::element::ptr el;
    const Node* shadow = nullptr;
    if (q.find("id")) {
        int64_t id = int64_t(q["id"].asNumber());
        shadow = node(id);
        el = container_->element(id);
        if (!shadow || shadow->kind != Node::Element) return Json::object().set("error", "no such element");
    }
    auto placement = [&]() -> Rect { return el ? border_box_of(el) : Rect{}; };
    if (kind == "rect") return rect_json(placement());
    if (kind == "rects") return el ? Json::array().push(rect_json(placement())) : Json::array();
    if (kind == "box") {
        Rect r = placement();
        float bl = 0, bt = 0, br = 0, bb = 0;
        if (auto ri = el ? el->get_render_item() : nullptr) {
            const auto& bd = ri->get_borders();
            bl = float(bd.left); bt = float(bd.top); br = float(bd.right); bb = float(bd.bottom);
        }
        int64_t parent = shadow && shadow->parent ? shadow->parent->id : 0;
        Json o = Json::object();
        o.set("offsetWidth", r.w).set("offsetHeight", r.h).set("offsetTop", r.y).set("offsetLeft", r.x).set("offsetParent", double(parent));
        o.set("clientWidth", std::max(0.0f, r.w - bl - br)).set("clientHeight", std::max(0.0f, r.h - bt - bb)).set("clientTop", bt).set("clientLeft", bl);
        o.set("scrollWidth", std::max(0.0f, r.w - bl - br)).set("scrollHeight", std::max(0.0f, r.h - bt - bb)).set("scrollTop", 0).set("scrollLeft", 0);
        return o;
    }
    if (kind == "cs") {
        // litehtml keeps resolved values as typed fields; the handful widgets read most are answered, the rest is "".
        Json o = Json::object();
        if (el) {
            const auto& css = el->css();
            const char* display = "block";
            switch (css.get_display()) {
            case litehtml::display_none: display = "none"; break;
            case litehtml::display_inline: display = "inline"; break;
            case litehtml::display_inline_block: display = "inline-block"; break;
            case litehtml::display_flex: display = "flex"; break;
            case litehtml::display_inline_flex: display = "inline-flex"; break;
            case litehtml::display_table: display = "table"; break;
            case litehtml::display_list_item: display = "list-item"; break;
            default: break;
            }
            auto color = [](const litehtml::web_color& c) {
                char buf[48];
                std::snprintf(buf, sizeof buf, "rgba(%d, %d, %d, %g)", c.red, c.green, c.blue, c.alpha / 255.0);
                return std::string(buf);
            };
            Rect r = placement();
            o.set("display", display);
            o.set("visibility", css.get_visibility() == litehtml::visibility_hidden ? "hidden" : "visible");
            o.set("color", color(css.get_color()));
            o.set("background-color", color(css.get_bg().m_color));
            o.set("font-size", std::to_string(int(css.get_font_size())) + "px");
            o.set("width", std::to_string(r.w) + "px").set("height", std::to_string(r.h) + "px");
        }
        if (q.find("prop")) {
            const Json* v = o.find(q["prop"].asString());
            return v ? *v : Json("");
        }
        return o;
    }
    if (kind == "elementFromPoint") return double(widget_id_at(q["x"].asNumber(), q["y"].asNumber(), nullptr));
    if (kind == "measureText") return container_->measure_text(q["font"].isString() ? q["font"].asString() : "10px sans-serif", q["text"].asString());
    if (kind == "validity") return Json::object().set("valid", true).set("message", "");
    if (kind == "matchMedia") return Json::object().set("matches", match_media(q["query"].asString()));
    if (kind == "viewport") return Json::object().set("innerWidth", width_).set("innerHeight", double(vp_height_)).set("dpr", dpr_);
    if (kind == "anims") {
        std::vector<int64_t> ids;
        if (shadow) {
            std::function<void(const Node*)> visit = [&](const Node* n) {
                if (n->id && ids.size() < 100000) ids.push_back(n->id);
                if (q["subtree"].asBool())
                    for (const Node* c : n->children) visit(c);
            };
            visit(shadow);
        }
        return Json::object().set("list", animator_.css_animations(ids, now_s()));
    }
    if (kind == "imageData") {
        int w = std::clamp(q["w"].asInt(), 0, Canvas::MAX_SIDE), h = std::clamp(q["h"].asInt(), 0, Canvas::MAX_SIDE);
        if (int64_t(w) * h > Canvas::MAX_PIXELS) return Json::object().set("error", "too large");
        std::vector<uint8_t> rgba;
        auto it = shadow ? canvases_.find(shadow->id) : canvases_.end();
        if (it == canvases_.end() || !backend_.canvas_read(it->second->id(), q["x"].asInt(), q["y"].asInt(), w, h, rgba) || rgba.size() != size_t(w) * h * 4)
            rgba.assign(size_t(w) * h * 4, 0);
        binary_ = base64_encode(rgba);
        return Json::object().set("width", w).set("height", h);
    }
    if (kind == "canvasCall") {
        auto it = shadow ? canvases_.find(shadow->id) : canvases_.end();
        const std::string& method = q["method"].asString();
        if (it == canvases_.end()) {
            if (method == "getTransform") return Json::array().push(1).push(0).push(0).push(1).push(0).push(0);
            if (method == "getLineDash") return Json::array();
            if (method == "isPointInPath" || method == "isPointInStroke") return false;
            return Json::object().set("error", "no canvas");
        }
        if (method == "toDataURL") return Json::object().set("error", "toDataURL unsupported by the litehtml renderer");
        return it->second->call(method, q["args"]);
    }
    if (kind == "svg") return Json::object().set("error", kind + " unsupported by the litehtml renderer");
    return Json::object().set("error", "unknown query");
}

bool Renderer::match_media(const std::string& query) const {
    std::string q = lower(query);
    if (q.find("prefers-color-scheme") != std::string::npos) return (q.find("dark") != std::string::npos) == dark_;
    auto px = [&](const char* key) -> double {
        size_t i = q.find(key);
        if (i == std::string::npos) return NAN;
        size_t j = i + std::strlen(key);
        while (j < q.size() && (q[j] == ' ' || q[j] == ':')) j++;
        return std::atof(q.c_str() + j);
    };
    double v;
    if (!std::isnan(v = px("max-width"))) return width_ <= v;
    if (!std::isnan(v = px("min-width"))) return width_ >= v;
    if (q.find("hover") != std::string::npos) return q.find("none") != std::string::npos;
    if (q.find("pointer") != std::string::npos) return q.find("coarse") != std::string::npos;
    return false;
}

int64_t Renderer::widget_id_at(double x, double y, Rect* placement) const {
    return container_->widget_id(container_->element_at(x, y), placement);
}

// ───────────────────────── events ─────────────────────────

bool Renderer::wants_gesture(int64_t target) const {
    const Node* n = node(target);
    while (n) {
        if (n->id && (prevent_.count(std::to_string(n->id) + ":pointerdown") || prevent_.count(std::to_string(n->id) + ":touchstart"))) return true;
        if (n->tag == "input") {
            for (const auto& a : n->attrs) if (a.first == "type" && lower(a.second) == "range") return true;
        }
        for (const auto& s : n->styles) {
            if (s.first == "touch-action") {
                std::string v = lower(s.second);
                if (v == "none" || (v.find("pan-y") == std::string::npos && v != "auto" && v != "manipulation")) return true;
            }
        }
        n = n->parent;
    }
    return false;
}

Json Renderer::event_json(const char* type, int64_t target, const Rect& target_rect, const Input& in) const {
    Json ev = Json::object();
    ev.set("type", type).set("targetId", double(target));
    ev.set("clientX", in.x).set("clientY", in.y).set("pageX", in.x).set("pageY", in.y);
    ev.set("offsetX", in.x - target_rect.x).set("offsetY", in.y - target_rect.y);
    ev.set("button", 0).set("buttons", in.buttons).set("detail", in.detail).set("modifiers", in.modifiers);
    if (in.related) ev.set("relatedId", double(in.related));
    std::string name = type;
    if (name.rfind("pointer", 0) == 0 || name == "click" || name == "dblclick") {
        ev.set("pointerId", double(in.mouse ? 1 : in.pointer + 2)).set("pointerType", in.mouse ? "mouse" : "touch").set("isPrimary", in.primary).set("pressure", in.buttons ? 0.5 : 0.0);
    }
    // Form control state travels with every event (the sandbox mirror relies on it).
    const Node* n = node(target);
    if (n && (n->tag == "input" || n->tag == "textarea" || n->tag == "select" || n->tag == "button")) {
        bool checkbox = false;
        for (const auto& a : n->attrs) if (a.first == "type") checkbox = lower(a.second) == "checkbox" || lower(a.second) == "radio";
        if (checkbox) {
            bool checked = false;
            for (const auto& a : n->attrs) if (a.first == "checked") checked = true;
            ev.set("checked", checked);
        }
        if (n->tag == "textarea") ev.set("value", n->data);
        else for (const auto& a : n->attrs) if (a.first == "value") ev.set("value", a.second);
    }
    return ev;
}

void Renderer::dispatch(Json ev, std::vector<Json>& out) {
    if (frozen_) return;
    const std::string& type = ev["type"].asString();
    bool always = false;
    for (const char* a : ALWAYS_FORWARD) if (type == a) always = true;
    if (!always && !interest_.count(type)) return;
    if (coalesced(type)) {
        std::string key = type + ":" + std::to_string(int64_t(ev["pointerId"].asNumber()));
        for (auto& [k, pending] : pending_moves_) {
            if (k != key) continue;
            // Wheel deltas add up over the frame; a move only keeps its latest position.
            if (type == "wheel") {
                ev.set("deltaX", ev["deltaX"].asNumber() + pending["deltaX"].asNumber());
                ev.set("deltaY", ev["deltaY"].asNumber() + pending["deltaY"].asNumber());
            }
            pending = std::move(ev);
            return;
        }
        pending_moves_.emplace_back(std::move(key), std::move(ev));
        return;
    }
    // Anything else keeps its order relative to the moves before it. Every event here comes from real
    // input (no script runs in this document): trusted, like the WebView renderer's isTrusted, which the
    // hosts require before they count a user gesture.
    flush_moves(out);
    out.push_back(Json::object().set("type", "event").set("ev", std::move(ev)).set("trusted", true));
}

void Renderer::flush_moves(std::vector<Json>& out) {
    for (auto& [key, ev] : pending_moves_) out.push_back(Json::object().set("type", "event").set("ev", std::move(ev)));
    pending_moves_.clear();
}

void Renderer::emit_event(const char* type, int64_t target, const Rect& target_rect, const Touch& t, int action, std::vector<Json>& out) {
    if (t.mouse && std::strncmp(type, "touch", 5) == 0) return;
    Input in;
    in.x = t.x;
    in.y = t.y;
    in.pointer = t.pointer;
    in.primary = t.primary;
    in.mouse = t.mouse;
    in.modifiers = t.modifiers;
    in.buttons = action == 0 || action == 1 ? 1 : 0;
    in.detail = std::strcmp(type, "click") == 0 || std::strcmp(type, "dblclick") == 0 ? click_count_ : 0;
    dispatch(event_json(type, target, target_rect, in), out);
}

bool Renderer::is_ancestor(int64_t ancestor, int64_t id) const {
    if (!ancestor) return false;
    const Node* n = node(id);
    for (const Node* p = n ? n->parent : nullptr; p; p = p->parent)
        if (p->id == ancestor) return true;
    return false;
}

std::vector<int64_t> Renderer::chain(int64_t id) const {
    std::vector<int64_t> out;
    for (const Node* n = node(id); n; n = n->parent)
        if (n->id) out.push_back(n->id);
    return out;
}

std::vector<Json> Renderer::touch(int action, double x, double y, int pointer, bool primary, bool mouse, int modifiers) {
    std::vector<Json> out;
    if (frozen_) return out;
    if (action == 0) {
        Touch t{x, y, x, y, 0, Rect{}, false, primary, pointer, mouse, modifiers};
        t.target = widget_id_at(x, y, &t.target_rect);
        touches_[pointer] = t;
        if (primary) out.push_back(Json::object().set("type", "gesture").set("wants", wants_gesture(t.target)));
        if (container_->mouse(0, x, y)) dirty_ = true;
        if (t.target) {
            emit_event("pointerdown", t.target, t.target_rect, t, 0, out);
            emit_event("mousedown", t.target, t.target_rect, t, 0, out);
            emit_event("touchstart", t.target, t.target_rect, t, 0, out);
        }
        flush_pending(out);
        return out;
    }
    auto it = touches_.find(pointer);
    if (it == touches_.end()) return out;
    Touch& t = it->second;
    t.x = x;
    t.y = y;
    t.modifiers = modifiers;
    if (action == 1) {
        if (!t.moved) {
            if (std::fabs(x - t.x0) < TAP_SLOP && std::fabs(y - t.y0) < TAP_SLOP) return out;
            t.moved = true;
        }
        if (container_->mouse(1, x, y)) dirty_ = true;
        if (t.target) {
            emit_event("pointermove", t.target, t.target_rect, t, 1, out);
            emit_event("mousemove", t.target, t.target_rect, t, 1, out);
            emit_event("touchmove", t.target, t.target_rect, t, 1, out);
        }
        return out;
    }
    Touch done = t;
    touches_.erase(it);
    if (container_->mouse(action, x, y, done.mouse && action == 2)) dirty_ = true;
    if (action == 2) {
        Rect up_rect;
        int64_t up_target = widget_id_at(x, y, &up_rect);
        if (done.target) {
            emit_event("pointerup", done.target, done.target_rect, done, 2, out);
            emit_event("mouseup", done.target, done.target_rect, done, 2, out);
            emit_event("touchend", done.target, done.target_rect, done, 2, out);
        }
        // A click: the press and the release land on the same element, or one inside the other
        // (the click goes to the outer one). A finger must also have stayed inside the slop.
        int64_t click = 0;
        if (done.target && (done.mouse || !done.moved)) {
            if (up_target == done.target || is_ancestor(done.target, up_target)) click = done.target;
            else if (is_ancestor(up_target, done.target)) click = up_target;
        }
        if (done.primary && click) {
            double now = now_ms();
            if (now - last_click_ms_ < DOUBLE_CLICK_MS && std::hypot(x - last_click_x_, y - last_click_y_) < (done.mouse ? 5 : TAP_SLOP * 2)) click_count_++;
            else click_count_ = 1;
            last_click_ms_ = now;
            last_click_x_ = x;
            last_click_y_ = y;
            Rect rect = click == done.target ? done.target_rect : up_rect;
            emit_event("click", click, rect, done, 2, out);
            if (click_count_ == 2) emit_event("dblclick", click, rect, done, 2, out);
        }
    } else if (done.target) {
        emit_event("pointercancel", done.target, done.target_rect, done, 3, out);
        emit_event("touchcancel", done.target, done.target_rect, done, 3, out);
    }
    flush_pending(out);
    return out;
}

std::vector<Json> Renderer::hover(int action, double x, double y, int modifiers) {
    std::vector<Json> out;
    if (frozen_) return out;
    auto box = [this](int64_t id) {
        auto el = container_->element(id);
        return el ? border_box_of(el) : Rect{};
    };
    Input in;
    in.mouse = true;
    in.pointer = 1;
    in.modifiers = modifiers;
    int64_t previous = hover_;
    int64_t target = 0;
    if (action == 0) {
        in.x = hover_x_ = x;
        in.y = hover_y_ = y;
        if (container_->mouse(1, x, y)) dirty_ = true;
        target = widget_id_at(x, y, nullptr);
    } else {
        in.x = hover_x_;
        in.y = hover_y_;
        if (container_->leave()) dirty_ = true;
    }
    if (target != previous) {
        hover_ = target;
        // out/over bubble and name the other element; leave/enter go to each ancestor the pointer
        // actually left or entered, innermost first for leave and outermost first for enter.
        auto before = chain(previous), after = chain(target);
        auto in_chain = [](const std::vector<int64_t>& c, int64_t id) { return std::find(c.begin(), c.end(), id) != c.end(); };
        if (previous) {
            in.related = target;
            dispatch(event_json("pointerout", previous, box(previous), in), out);
            dispatch(event_json("mouseout", previous, box(previous), in), out);
            for (int64_t id : before) {
                if (in_chain(after, id)) continue;
                dispatch(event_json("pointerleave", id, box(id), in), out);
                dispatch(event_json("mouseleave", id, box(id), in), out);
            }
        }
        if (target) {
            in.related = previous;
            dispatch(event_json("pointerover", target, box(target), in), out);
            dispatch(event_json("mouseover", target, box(target), in), out);
            for (auto it = after.rbegin(); it != after.rend(); ++it) {
                if (in_chain(before, *it)) continue;
                dispatch(event_json("pointerenter", *it, box(*it), in), out);
                dispatch(event_json("mouseenter", *it, box(*it), in), out);
            }
        }
        in.related = 0;
    }
    if (target) {
        dispatch(event_json("pointermove", target, box(target), in), out);
        dispatch(event_json("mousemove", target, box(target), in), out);
    }
    flush_pending(out);
    return out;
}

std::vector<Json> Renderer::wheel(double x, double y, double dx, double dy, int mode, int modifiers) {
    std::vector<Json> out;
    if (frozen_) return out;
    Rect rect;
    int64_t target = widget_id_at(x, y, &rect);
    if (!target) return out;
    Input in;
    in.x = x;
    in.y = y;
    in.mouse = true;
    in.pointer = 1;
    in.modifiers = modifiers;
    Json ev = event_json("wheel", target, rect, in);
    ev.set("deltaX", dx).set("deltaY", dy).set("deltaMode", mode);
    dispatch(std::move(ev), out);
    return out;
}

std::vector<Json> Renderer::key(const std::string& type, const std::string& key, const std::string& code, bool repeat, int modifiers) {
    std::vector<Json> out;
    if (frozen_ || (type != "keydown" && type != "keyup")) return out;
    int64_t target = body_node_->id ? body_node_->id : html_node_->id;
    if (!target) return out;
    Json ev = Json::object();
    ev.set("type", type).set("targetId", double(target)).set("key", key).set("code", code).set("repeat", repeat).set("modifiers", modifiers);
    dispatch(std::move(ev), out);
    flush_pending(out);
    return out;
}

std::vector<Json> Renderer::image_ready(int id, float w, float h) {
    std::vector<Json> out;
    if (!container_->image_ready(id, w, h)) return out;
    rebuild();
    report_size(out);
    return out;
}

// ───────────────────────── frozen ─────────────────────────

void Renderer::freeze(const std::string& reason, bool fatal) {
    if (frozen_) return;
    frozen_ = true;
    frozen_reason_ = reason;
    frozen_fatal_ = fatal;
    touches_.clear();
    pending_moves_.clear();
    hover_ = 0;
}

// ───────────────────────── display list JSON ─────────────────────────

std::string to_json(const DisplayList& list) {
    std::string out;
    out.reserve(list.size() * 64 + 2);
    auto num = [&](float v) { out += twidget::Json::number(std::round(double(v) * 100) / 100); };
    auto rect = [&](const char* key, const Rect& r) {
        out += ",\"";
        out += key;
        out += "\":[";
        num(r.x); out.push_back(','); num(r.y); out.push_back(','); num(r.w); out.push_back(','); num(r.h);
        out.push_back(']');
    };
    auto color = [&](const Color& c) {
        out += ",\"c\":";
        out += std::to_string((uint32_t(c.a) << 24) | (uint32_t(c.r) << 16) | (uint32_t(c.g) << 8) | c.b);
    };
    out.push_back('[');
    bool first = true;
    for (const Command& c : list) {
        if (!first) out.push_back(',');
        first = false;
        out += "{\"k\":";
        out += std::to_string(int(c.kind));
        if (c.kind != Cmd::ClipPop && c.kind != Cmd::Text) rect("r", c.rect);
        if (c.radii.any()) {
            out += ",\"q\":[";
            const float v[] = {c.radii.tlx, c.radii.tly, c.radii.trx, c.radii.try_, c.radii.brx, c.radii.bry, c.radii.blx, c.radii.bly};
            for (int i = 0; i < 8; i++) { if (i) out.push_back(','); num(v[i]); }
            out.push_back(']');
        }
        if (c.has_clip) rect("l", c.clip);
        switch (c.kind) {
        case Cmd::FillRect: case Cmd::FillEllipse: color(c.color); break;
        case Cmd::StrokeRect: case Cmd::StrokeEllipse: color(c.color); out += ",\"w\":"; num(c.width); break;
        case Cmd::Text:
            color(c.color);
            out += ",\"f\":" + std::to_string(c.font) + ",\"x\":"; num(c.x);
            out += ",\"y\":"; num(c.y);
            out += ",\"t\":" + twidget::Json::quote(c.text);
            break;
        case Cmd::Image: out += ",\"i\":" + std::to_string(c.image); break;
        case Cmd::Linear: case Cmd::Radial: case Cmd::Conic:
            out += ",\"a\":["; num(c.ax); out.push_back(','); num(c.ay); out.push_back(','); num(c.bx); out.push_back(','); num(c.by); out.push_back(']');
            out += ",\"s\":[";
            for (size_t i = 0; i < c.stops.size(); i++) {
                if (i) out.push_back(',');
                num(c.stops[i].offset);
                out.push_back(',');
                const Color& s = c.stops[i].color;
                out += std::to_string((uint32_t(s.a) << 24) | (uint32_t(s.r) << 16) | (uint32_t(s.g) << 8) | s.b);
            }
            out.push_back(']');
            break;
        case Cmd::ClipPush: case Cmd::ClipPop: break;
        }
        out.push_back('}');
    }
    out.push_back(']');
    return out;
}

} // namespace twlite
