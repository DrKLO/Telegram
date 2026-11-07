#include "computed_style.h"

#include "css.h"

#include <litehtml/el_before_after.h>
#include <litehtml/el_svg.h>
#include <litehtml/html_tag.h>
#include <litehtml/render_item.h>

#include <cmath>

namespace twlite {

namespace {

using twidget::Json;

std::string px(double v) {
    return css::format_number(v, 3) + "px";
}

std::string color(const litehtml::web_color& c) {
    std::string rgb = std::to_string(c.red) + ", " + std::to_string(c.green) + ", " + std::to_string(c.blue);
    if (c.alpha == 255) return "rgb(" + rgb + ")";
    // Chromium keeps 8 bits of alpha and writes the shortest of 2 or 3 decimals that maps back to them.
    double a = c.alpha / 255.0;
    double two = std::round(a * 100) / 100;
    std::string alpha = int(std::lround(two * 255)) == c.alpha ? css::format_number(two, 2) : css::format_number(a, 3);
    return "rgba(" + rgb + ", " + alpha + ")";
}

/// A declared length as a computed one: px for absolute and font-relative units, % kept.
std::string length(const litehtml::css_length& l, double font_size, const char* predefined) {
    if (l.is_predefined()) return predefined;
    double v = l.val();
    switch (l.units()) {
    case litehtml::css_units_percentage: return css::format_number(v, 3) + "%";
    case litehtml::css_units_em: return px(v * font_size);
    case litehtml::css_units_rem: return px(v * 16);
    case litehtml::css_units_pt: return px(v * 4 / 3);
    case litehtml::css_units_none:
    case litehtml::css_units_px:
    // The fork keeps no text for math that waits for layout: what it makes of no size is the best answer.
    case litehtml::css_units_calc: return px(v);
    default: return css::format_number(v, 3) + std::string(litehtml::css_units_strings[l.units()]);
    }
}

std::string name_of(std::string_view s) {
    return std::string(s);
}

/// `top right bottom left` as Chromium shortens a box shorthand.
std::string box_shorthand(const std::string& top, const std::string& right, const std::string& bottom, const std::string& left) {
    if (left == right) {
        if (top == bottom) return top == right ? top : top + " " + right;
        return top + " " + right + " " + bottom;
    }
    return top + " " + right + " " + bottom + " " + left;
}

std::string raw(const litehtml::html_tag* tag, const char* name, const char* fallback) {
    std::string v = tag ? css::trim(tag->raw_declaration(name)) : std::string();
    return v.empty() ? fallback : v;
}

const char* text_decoration_line(int bits) {
    bool under = bits & litehtml::text_decoration_line_underline, over = bits & litehtml::text_decoration_line_overline,
         through = bits & litehtml::text_decoration_line_line_through;
    if (under && over && through) return "underline overline line-through";
    if (under && over) return "underline overline";
    if (under && through) return "underline line-through";
    if (over && through) return "overline line-through";
    if (under) return "underline";
    if (over) return "overline";
    if (through) return "line-through";
    return "none";
}

/// The element's ::before or ::after child, which the fork keeps as an ordinary child element.
litehtml::element::ptr pseudo_of(const litehtml::element::ptr& el, std::string_view pseudo) {
    bool before = pseudo == "::before" || pseudo == ":before";
    bool after = pseudo == "::after" || pseudo == ":after";
    if (!before && !after) return nullptr;
    auto tag = std::dynamic_pointer_cast<litehtml::html_tag>(el);
    if (!tag) return nullptr;
    for (const auto& child : tag->children()) {
        if (before && std::dynamic_pointer_cast<litehtml::el_before>(child)) return child;
        if (after && std::dynamic_pointer_cast<litehtml::el_after>(child)) return child;
    }
    return nullptr;
}

} // namespace

std::string computed_custom_property(const litehtml::element::ptr& el, const std::string& name) {
    auto tag = std::dynamic_pointer_cast<litehtml::html_tag>(el);
    litehtml::css_token_vector tokens;
    if (!tag || !tag->get_custom_property(litehtml::_id(name), tokens)) return {};
    // The fork drops a declaration's whitespace as it parses: one space goes back between two values, as in
    // `8px 12px`, and none next to punctuation (`,` `/`), whose types are their characters, the values' negative.
    std::string out;
    for (size_t i = 0; i < tokens.size(); i++) {
        if (i && tokens[i - 1].type < 0 && tokens[i].type < 0) out += ' ';
        out += tokens[i].get_repr(false);
    }
    return css::trim(out);
}

std::string computed_touch_action(const litehtml::element::ptr& el) {
    auto tag = std::dynamic_pointer_cast<litehtml::html_tag>(el);
    return raw(tag.get(), "touch-action", "auto");
}

std::string computed_pointer_events(const litehtml::element::ptr& el) {
    for (auto e = el; e; e = e->parent()) {
        auto tag = std::dynamic_pointer_cast<litehtml::html_tag>(e);
        std::string v = raw(tag.get(), "pointer-events", "");
        if (!v.empty() && v != "inherit" && v != "unset") return v == "initial" ? "auto" : v;
    }
    return "auto";
}

Json computed_style(const litehtml::element::ptr& element, std::string_view pseudo, const ComputedExtras& extras) {
    Json o = Json::object();
    litehtml::element::ptr el = pseudo.empty() ? element : pseudo_of(element, pseudo);
    if (!el) {
        // A pseudo-element nothing generates: Chromium answers its initial values.
        if (!pseudo.empty()) o.set("content", "none").set("display", "inline");
        return o;
    }
    const litehtml::css_properties& css = el->css();
    auto tag = std::dynamic_pointer_cast<litehtml::html_tag>(el);
    auto ri = el->get_render_item();
    double font_size = double(css.get_font_size());
    auto len = [&](const litehtml::css_length& l, const char* predefined) { return length(l, font_size, predefined); };

    std::string display = name_of(litehtml::style_display_strings[css.get_display()]);
    if (display == "inline-text") display = "inline";
    o.set("display", display);
    o.set("position", name_of(litehtml::element_position_strings[css.get_position()]));
    o.set("float", name_of(litehtml::element_float_strings[css.get_float()]));
    o.set("clear", name_of(litehtml::element_clear_strings[css.get_clear()]));
    o.set("box-sizing", name_of(litehtml::box_sizing_strings[css.get_box_sizing()]));
    o.set("visibility", name_of(litehtml::visibility_strings[css.get_visibility()]));
    o.set("z-index", css.get_position() == litehtml::element_position_static || css.get_z_index() == 0 ? std::string("auto") : std::to_string(css.get_z_index()));
    o.set("direction", name_of(litehtml::direction_strings[css.get_direction()])).set("unicode-bidi", "normal");

    // The box: what layout used, as getComputedStyle resolves width, height, margins and padding.
    std::string margin[4], padding[4], border_width[4];
    const litehtml::css_borders& borders = css.get_borders();
    const litehtml::css_border* sides[4] = {&borders.top, &borders.right, &borders.bottom, &borders.left};
    if (ri) {
        const auto& m = ri->get_margins();
        const auto& p = ri->get_paddings();
        const auto& b = ri->get_borders();
        double ms[4] = {double(m.top), double(m.right), double(m.bottom), double(m.left)};
        // Layout collapses adjoining margins to 0; getComputedStyle keeps the element's own. Auto and
        // percentage margins are only known from layout.
        const auto& declared = css.get_margins();
        const litehtml::css_length* dm[4] = {&declared.top, &declared.right, &declared.bottom, &declared.left};
        for (int i = 0; i < 4; i++)
            if (!dm[i]->is_predefined() && dm[i]->units() != litehtml::css_units_percentage) ms[i] = std::stod(length(*dm[i], font_size, "0px"));
        double ps[4] = {double(p.top), double(p.right), double(p.bottom), double(p.left)};
        double bs[4] = {double(b.top), double(b.right), double(b.bottom), double(b.left)};
        for (int i = 0; i < 4; i++) {
            margin[i] = px(ms[i]);
            padding[i] = px(ps[i]);
            border_width[i] = px(bs[i]);
        }
        litehtml::position content = el->get_placement();
        double w = double(content.width), h = double(content.height);
        if (css.get_box_sizing() == litehtml::box_sizing_border_box) {
            w += ps[1] + ps[3] + bs[1] + bs[3];
            h += ps[0] + ps[2] + bs[0] + bs[2];
        }
        bool inline_box = css.get_display() == litehtml::display_inline || css.get_display() == litehtml::display_inline_text;
        o.set("width", inline_box ? std::string("auto") : px(w)).set("height", inline_box ? std::string("auto") : px(h));
    } else {
        const auto& m = css.get_margins();
        const auto& p = css.get_padding();
        const litehtml::css_length* ms[4] = {&m.top, &m.right, &m.bottom, &m.left};
        const litehtml::css_length* ps[4] = {&p.top, &p.right, &p.bottom, &p.left};
        for (int i = 0; i < 4; i++) {
            margin[i] = len(*ms[i], "0px");
            padding[i] = len(*ps[i], "0px");
            border_width[i] = sides[i]->style == litehtml::border_style_none || sides[i]->style == litehtml::border_style_hidden ? "0px" : len(sides[i]->width, "3px");
        }
        o.set("width", len(css.get_width(), "auto")).set("height", len(css.get_height(), "auto"));
    }
    const char* edges[4] = {"top", "right", "bottom", "left"};
    for (int i = 0; i < 4; i++) {
        o.set(std::string("margin-") + edges[i], margin[i]);
        o.set(std::string("padding-") + edges[i], padding[i]);
        o.set(std::string("border-") + edges[i] + "-width", border_width[i]);
        o.set(std::string("border-") + edges[i] + "-style", name_of(litehtml::border_style_strings[sides[i]->style]));
        o.set(std::string("border-") + edges[i] + "-color", color(sides[i]->color));
    }
    o.set("margin", box_shorthand(margin[0], margin[1], margin[2], margin[3]));
    o.set("padding", box_shorthand(padding[0], padding[1], padding[2], padding[3]));
    o.set("border-width", box_shorthand(border_width[0], border_width[1], border_width[2], border_width[3]));
    std::string styles[4], colors[4];
    for (int i = 0; i < 4; i++) {
        styles[i] = name_of(litehtml::border_style_strings[sides[i]->style]);
        colors[i] = color(sides[i]->color);
    }
    o.set("border-style", box_shorthand(styles[0], styles[1], styles[2], styles[3]));
    o.set("border-color", box_shorthand(colors[0], colors[1], colors[2], colors[3]));
    bool uniform = border_width[0] == border_width[1] && border_width[0] == border_width[2] && border_width[0] == border_width[3] && styles[0] == styles[1] &&
                   styles[0] == styles[2] && styles[0] == styles[3] && colors[0] == colors[1] && colors[0] == colors[2] && colors[0] == colors[3];
    o.set("border", uniform ? border_width[0] + " " + styles[0] + " " + colors[0] : std::string());
    auto radius = [&](const litehtml::css_length& x, const litehtml::css_length& y) {
        std::string a = len(x, "0px"), b = len(y, "0px");
        return a == b ? a : a + " " + b;
    };
    const auto& r = borders.radius;
    std::string radii[4] = {radius(r.top_left_x, r.top_left_y), radius(r.top_right_x, r.top_right_y), radius(r.bottom_right_x, r.bottom_right_y),
                            radius(r.bottom_left_x, r.bottom_left_y)};
    o.set("border-top-left-radius", radii[0]).set("border-top-right-radius", radii[1]).set("border-bottom-right-radius", radii[2]).set("border-bottom-left-radius", radii[3]);
    o.set("border-radius", box_shorthand(radii[0], radii[1], radii[2], radii[3]));
    o.set("border-collapse", name_of(litehtml::border_collapse_strings[css.get_border_collapse()]));
    o.set("border-spacing", len(css.get_border_spacing_x(), "0px") + " " + len(css.get_border_spacing_y(), "0px"));
    o.set("caption-side", name_of(litehtml::caption_side_strings[css.get_caption_side()]));

    o.set("min-width", len(css.get_min_width(), "auto")).set("min-height", len(css.get_min_height(), "auto"));
    o.set("max-width", len(css.get_max_width(), "none")).set("max-height", len(css.get_max_height(), "none"));
    const auto& offsets = css.get_offsets();
    o.set("top", len(offsets.top, "auto")).set("right", len(offsets.right, "auto")).set("bottom", len(offsets.bottom, "auto")).set("left", len(offsets.left, "auto"));

    std::string ox = name_of(litehtml::overflow_strings[css.get_overflow_x()]), oy = name_of(litehtml::overflow_strings[css.get_overflow_y()]);
    o.set("overflow-x", ox).set("overflow-y", oy).set("overflow", ox == oy ? ox : ox + " " + oy);
    o.set("white-space", name_of(litehtml::white_space_strings[css.get_white_space()]));
    std::string wrap = name_of(litehtml::overflow_wrap_strings[css.get_overflow_wrap()]);
    o.set("overflow-wrap", wrap).set("word-wrap", wrap);
    o.set("word-break", name_of(litehtml::word_break_strings[css.get_word_break()]));

    // Text and fonts.
    o.set("color", color(css.get_color()));
    o.set("font-size", px(font_size));
    int weight = int(css.font_weight().val());
    o.set("font-weight", std::to_string(weight > 0 ? weight : 400));
    std::string style = name_of(litehtml::font_style_strings[css.font_style_value()]);
    o.set("font-style", style);
    o.set("font-family", css.font_family());
    std::string line_height = css.line_height().css_value.is_predefined() ? std::string("normal") : px(double(css.line_height().computed_value));
    o.set("line-height", line_height);
    o.set("font", (style == "normal" ? std::string() : style + " ") + (weight == 400 || weight <= 0 ? std::string() : std::to_string(weight) + " ") + px(font_size) +
                      (line_height == "normal" ? std::string() : " / " + line_height) + " " + css.font_family());
    o.set("font-variant-numeric", raw(tag.get(), "font-variant-numeric", "normal"));
    o.set("text-align", name_of(litehtml::text_align_strings[css.get_text_align()]));
    o.set("text-indent", len(css.get_text_indent(), "0px"));
    o.set("text-transform", name_of(litehtml::text_transform_strings[css.get_text_transform()]));
    o.set("letter-spacing", double(css.get_letter_spacing()) == 0 ? std::string("normal") : px(double(css.get_letter_spacing())));
    o.set("word-spacing", px(double(css.get_word_spacing())));
    o.set("vertical-align", name_of(litehtml::vertical_align_strings[css.get_vertical_align()]));
    std::string decoration = text_decoration_line(css.get_text_decoration_line());
    std::string decoration_style = name_of(litehtml::style_text_decoration_style_strings[css.get_text_decoration_style()]);
    std::string decoration_color = color(css.get_text_decoration_color().alpha ? css.get_text_decoration_color() : css.get_color());
    o.set("text-decoration-line", decoration).set("text-decoration-style", decoration_style).set("text-decoration-color", decoration_color);
    o.set("text-decoration", decoration + " " + decoration_style + " " + decoration_color);
    // What the widget asked for. The renderer selects no text yet, an exception v0.1.1 allows a native renderer.
    const char* select = css.get_user_select() == litehtml::user_select_text ? "text" : css.get_user_select() == litehtml::user_select_none ? "none" : "auto";
    o.set("user-select", select).set("-webkit-user-select", select);

    // Lists.
    o.set("list-style-type", name_of(litehtml::list_style_type_strings[css.get_list_style_type()]));
    {
        std::string quotes = "auto";
        for (auto e = el; e; e = e->parent()) {
            std::string v = raw(std::dynamic_pointer_cast<litehtml::html_tag>(e).get(), "quotes", "");
            if (!v.empty() && v != "inherit" && v != "unset") {
                quotes = v == "initial" ? "auto" : v;
                break;
            }
        }
        o.set("quotes", quotes);
    }
    o.set("list-style-position", name_of(litehtml::list_style_position_strings[css.get_list_style_position()]));
    o.set("list-style-image", css.get_list_style_image().empty() ? std::string("none") : "url(\"" + css.get_list_style_image() + "\")");

    // Backgrounds.
    const litehtml::background& bg = css.get_bg();
    o.set("background-color", color(bg.m_color));
    std::string images, sizes, positions, repeats;
    for (size_t i = 0; i < bg.m_image.size(); i++) {
        const litehtml::image& image = bg.m_image[i];
        std::string one = "none";
        if (image.type == litehtml::image::type_url) one = "url(\"" + image.url + "\")";
        else if (image.type == litehtml::image::type_gradient) one = litehtml::_s(image.m_gradient.m_type) + "(...)";
        images += (i ? ", " : "") + one;
        if (i < bg.m_size.size()) {
            const auto& s = bg.m_size[i];
            std::string w = len(s.width, "auto"), h = len(s.height, "auto");
            sizes += (i ? ", " : "") + (s.width.is_predefined() && s.width.predef() != 0 ? name_of(litehtml::background_size_strings[s.width.predef()]) : h == "auto" && w == "auto" ? w : w + " " + h);
        }
        if (i < bg.m_position_x.size() && i < bg.m_position_y.size())
            positions += (i ? ", " : "") + len(bg.m_position_x[i], "0%") + " " + len(bg.m_position_y[i], "0%");
        if (i < bg.m_repeat.size()) repeats += (i ? ", " : "") + name_of(litehtml::background_repeat_strings[bg.m_repeat[i]]);
    }
    o.set("background-image", images.empty() ? std::string("none") : images);
    o.set("background-size", sizes.empty() ? std::string("auto") : sizes);
    o.set("background-position", positions.empty() ? std::string("0% 0%") : positions);
    o.set("background-repeat", repeats.empty() ? std::string("repeat") : repeats);

    // Effects.
    o.set("opacity", css::format_number(css.get_opacity(), 3));
    o.set("cursor", css.get_cursor().empty() ? std::string("auto") : css.get_cursor());
    o.set("pointer-events", computed_pointer_events(el));
    auto declared = [&](const char* name, const char* fallback) {
        if (extras.animated && pseudo.empty())
            for (auto it = extras.animated->rbegin(); it != extras.animated->rend(); ++it)
                if (it->first == name) return css::trim(it->second);
        return raw(tag.get(), name, fallback);
    };
    o.set("touch-action", raw(tag.get(), "touch-action", "auto"));
    o.set("filter", declared("filter", "none")).set("backdrop-filter", declared("backdrop-filter", "none"));
    o.set("box-shadow", declared("box-shadow", "none"));
    o.set("translate", declared("translate", "none")).set("rotate", declared("rotate", "none")).set("scale", declared("scale", "none"));
    {
        // Chromium resolves a transform to the matrix it draws with, about the border box.
        litehtml::position content = el->get_placement();
        double bw = double(content.width), bh = double(content.height);
        if (ri) {
            const auto& p = ri->get_paddings();
            const auto& b = ri->get_borders();
            bw += double(p.left) + double(p.right) + double(b.left) + double(b.right);
            bh += double(p.top) + double(p.bottom) + double(b.top) + double(b.bottom);
        }
        std::string transform = declared("transform", "none");
        if (transform != "none") {
            if (auto m = css::parse_transform(transform, float(bw), float(bh))) {
                auto f = [](float v) { return css::format_number(v, 6); };
                transform = "matrix(" + f(m->a) + ", " + f(m->b) + ", " + f(m->c) + ", " + f(m->d) + ", " + f(m->e) + ", " + f(m->f) + ")";
            }
        }
        o.set("transform", transform);
        auto origin = [&](const litehtml::css_length& l, double size) {
            return l.units() == litehtml::css_units_percentage ? px(l.val() * size / 100) : len(l, "0px");
        };
        o.set("transform-origin", origin(css.get_transform_origin_x(), bw) + " " + origin(css.get_transform_origin_y(), bh));
    }
    const litehtml::css_border& outline = css.get_outline();
    bool outlined = outline.style != litehtml::border_style_none && outline.style != litehtml::border_style_hidden;
    o.set("outline-style", name_of(litehtml::border_style_strings[outline.style]));
    o.set("outline-width", outlined ? len(outline.width, "3px") : std::string("0px"));
    o.set("outline-color", color(outline.color.alpha ? outline.color : css.get_color()));
    o.set("outline-offset", px(double(css.get_outline_offset())));
    for (bool fill : {true, false}) {
        // A paint server stays as written. Otherwise the paint is the one litehtml draws with: inherited
        // (through a <use> to what it draws, too), currentColor resolved.
        const char* name = fill ? "fill" : "stroke";
        std::string value = declared(name, "");
        Color c;
        litehtml::web_color painted;
        if (value.find("url(") != std::string::npos) o.set(name, value);
        else if (css::parse_color(value, c)) o.set(name, color(litehtml::web_color(c.r, c.g, c.b, c.a)));
        else if (litehtml::svg_paint_color(el, fill, painted, el->get_document()->container())) o.set(name, color(painted));
        else o.set(name, "none");
    }

    // Flex and grid.
    o.set("flex-direction", name_of(litehtml::flex_direction_strings[css.get_flex_direction()]));
    o.set("flex-wrap", name_of(litehtml::flex_wrap_strings[css.get_flex_wrap()]));
    o.set("justify-content", name_of(litehtml::flex_justify_content_strings[css.get_flex_justify_content()]));
    o.set("align-items", name_of(litehtml::flex_align_items_strings[css.get_flex_align_items()]));
    o.set("align-self", name_of(litehtml::flex_align_items_strings[css.get_flex_align_self()]));
    o.set("align-content", name_of(litehtml::flex_align_content_strings[css.get_flex_align_content()]));
    o.set("flex-grow", css::format_number(css.get_flex_grow(), 3)).set("flex-shrink", css::format_number(css.get_flex_shrink(), 3));
    o.set("flex-basis", css.get_flex_basis().is_predefined() ? name_of(litehtml::flex_basis_strings[css.get_flex_basis().predef()]) : len(css.get_flex_basis(), "auto"));
    o.set("order", std::to_string(css.get_order()));
    std::string row_gap = len(css.get_row_gap(), "normal"), column_gap = len(css.get_column_gap(), "normal");
    o.set("row-gap", row_gap).set("column-gap", column_gap).set("gap", row_gap == column_gap ? row_gap : row_gap + " " + column_gap);
    o.set("aspect-ratio", css.get_aspect_ratio() > 0 ? css::format_number(css.get_aspect_ratio(), 4) + " / 1" : std::string("auto"));

    // Generated content: a pseudo-element's own text, quoted as Chromium writes it; keywords and
    // functions as written.
    if (!pseudo.empty()) {
        std::string content = css::trim(css.get_content());
        if (content.size() >= 2 && (content.front() == '\'' || content.front() == '"') && content.back() == content.front())
            content = "\"" + content.substr(1, content.size() - 2) + "\"";
        o.set("content", content.empty() ? std::string("none") : content);
    } else {
        o.set("content", "normal");
    }
    return o;
}

} // namespace twlite
