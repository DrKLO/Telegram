#include "html.h"
#include "el_form.h"
#include "el_text.h"
#include "document.h"
#include "document_container.h"
#include "render_item.h"
#include "css_parser.h"
#include <cmath>
#include <cstdlib>

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#endif
#include <cstdio>

namespace litehtml
{
    namespace
    {
        const web_color sentinel_border(1, 2, 3, 1);
        const web_color sentinel_back(3, 2, 1, 1);
        const web_color sentinel_text(2, 3, 1, 1);

        struct palette
        {
            web_color field;
            web_color field_border;
            web_color text;
            web_color control_text;
            web_color disabled_text;
            web_color placeholder;
            web_color box_border;
            web_color box_border_off;
            web_color box_on;
            web_color box_on_off;
            web_color mark;
            web_color mark_off;
            web_color track_fill;
            web_color track_rest;
            web_color track_fill_off;
            web_color track_rest_off;
            web_color thumb;
            web_color thumb_shadow;
            web_color well;
            web_color bezel;
            web_color bezel_off;
            web_color stepper;
            web_color stepper_line;
            web_color stepper_arrow;
            web_color button_face;
            web_color button_border;
            web_color legacy_field_border;
            web_color fieldset_border;
        };

        const palette& colors(bool dark)
        {
            static const palette light = {
                web_color(255, 255, 255), web_color(197, 197, 197), web_color(0, 0, 0),
                web_color(0, 0, 0, 216),  web_color(0, 0, 0, 64),   web_color(169, 169, 169),
                web_color(128, 128, 128), web_color(191, 191, 191), web_color(243, 243, 243),
                web_color(248, 248, 248), web_color(37, 37, 37),    web_color(143, 143, 143),
                web_color(170, 170, 170), web_color(230, 230, 230), web_color(220, 220, 220),
                web_color(245, 245, 245), web_color(255, 255, 255), web_color(0, 0, 0, 56),
                web_color(243, 243, 243), web_color(235, 235, 235), web_color(247, 247, 247),
                web_color(243, 243, 243), web_color(228, 228, 228), web_color(122, 122, 122),
                web_color(192, 192, 192), web_color(192, 192, 192), web_color(128, 128, 128),
                web_color(192, 192, 192),
            };
            static const palette night = {
                web_color(30, 30, 30),    web_color(84, 84, 86),    web_color(255, 255, 255),
                web_color(255, 255, 255, 216), web_color(255, 255, 255, 64), web_color(169, 169, 169),
                web_color(154, 154, 154), web_color(92, 92, 92),    web_color(40, 40, 40),
                web_color(35, 35, 35),    web_color(222, 222, 222), web_color(128, 128, 128),
                web_color(102, 102, 102), web_color(52, 52, 52),    web_color(59, 59, 60),
                web_color(38, 38, 39),    web_color(255, 255, 255), web_color(0, 0, 0, 110),
                web_color(39, 39, 41),    web_color(47, 47, 47),    web_color(36, 36, 37),
                web_color(41, 41, 41),    web_color(54, 54, 54),    web_color(158, 158, 158),
                web_color(90, 90, 90),    web_color(90, 90, 90),    web_color(128, 128, 128),
                web_color(90, 90, 90),
            };
            return dark ? night : light;
        }

        void append_content(html_tag* parent, const char* tag, const std::string& style, const std::string& text)
        {
            auto doc   = parent->get_document();
            auto child = std::make_shared<html_tag>(doc);
            child->set_tagName(tag);
            child->set_attr("style", style.c_str());
            if(!text.empty())
            {
                child->appendChild(std::make_shared<el_text>(text.c_str(), doc));
            }
            parent->appendChild(child);
        }

        std::string date_pattern()
        {
#ifdef __APPLE__
            CFLocaleRef locale  = CFLocaleCopyCurrent();
            CFStringRef pattern = CFDateFormatterCreateDateFormatFromTemplate(nullptr, CFSTR("ddMMyyyy"), 0, locale);
            CFRelease(locale);
            if(pattern)
            {
                char buffer[64] = {0};
                bool ok         = CFStringGetCString(pattern, buffer, sizeof(buffer), kCFStringEncodingUTF8);
                CFRelease(pattern);
                if(ok && *buffer)
                {
                    return buffer;
                }
            }
#endif
            return "MM/dd/yyyy";
        }

        std::string format_date(const std::string& value, bool& placeholder)
        {
            std::string pattern = date_pattern();
            int         year = 0, month = 0, day = 0;
            char        tail = 0;
            if(value.size() != 10 || sscanf(value.c_str(), "%4d-%2d-%2d%c", &year, &month, &day, &tail) != 3 ||
               month < 1 || month > 12 || day < 1 || day > 31 || year < 1)
            {
                placeholder = true;
                std::string hint;
                for(char ch : pattern)
                {
                    hint += ch == 'M' ? 'm' : ch == 'y' ? 'y' : ch == 'd' ? 'd' : ch;
                }
                return hint;
            }
            placeholder = false;
            std::string out;
            char        number[8];
            for(size_t i = 0; i < pattern.size();)
            {
                char   ch  = pattern[i];
                size_t run = 1;
                while(i + run < pattern.size() && pattern[i + run] == ch)
                {
                    run++;
                }
                if(ch == 'y')
                {
                    snprintf(number, sizeof(number), "%04d", year);
                    out += number;
                } else if(ch == 'M' || ch == 'd')
                {
                    snprintf(number, sizeof(number), run >= 2 ? "%02d" : "%d", ch == 'M' ? month : day);
                    out += number;
                } else if(ch != '\'')
                {
                    out.append(run, ch);
                }
                i += run;
            }
            return out;
        }

        bool has_generated(html_tag* parent)
        {
            for(const auto& child : parent->children())
            {
                const char* tag = child->get_tagName();
                if(tag && !strncmp(tag, "lh-", 3))
                {
                    return true;
                }
            }
            return false;
        }

        std::string textarea_text(html_tag* parent)
        {
            std::string text;
            for(const auto& child : parent->children())
            {
                const char* tag = child->get_tagName();
                if(!tag || strncmp(tag, "lh-", 3))
                {
                    child->get_text(text);
                }
            }
            return text;
        }

        void remove_generated(html_tag* parent)
        {
            std::vector<element::ptr> generated;
            for(const auto& child : parent->children())
            {
                const char* tag = child->get_tagName();
                if(tag && !strncmp(tag, "lh-", 3))
                {
                    generated.push_back(child);
                }
            }
            for(const auto& child : generated)
            {
                parent->removeChild(child);
            }
        }

        void make_flex(css_properties& css, flex_direction direction, flex_justify_content justify,
                       flex_align_items align)
        {
            switch(css.get_display())
            {
            case display_block:
                css.set_display(display_flex);
                break;
            case display_inline:
            case display_inline_block:
                css.set_display(display_inline_flex);
                break;
            default:
                return;
            }
            css.set_flex_layout(direction, justify, align);
        }

        void fill_box(uint_ptr hdc, document_container* container, const position& box, pixel_t radius,
                      const web_color& color)
        {
            if(box.width <= 0_px || box.height <= 0_px || !color.alpha)
            {
                return;
            }
            background_layer layer;
            layer.border_box = layer.clip_box = layer.origin_box = box;
            pixel_t r = std::min(radius, std::min(box.width, box.height) / 2_px);
            layer.border_radius.top_left_x = layer.border_radius.top_left_y = r;
            layer.border_radius.top_right_x = layer.border_radius.top_right_y = r;
            layer.border_radius.bottom_right_x = layer.border_radius.bottom_right_y = r;
            layer.border_radius.bottom_left_x = layer.border_radius.bottom_left_y = r;
            layer.repeat = background_repeat_no_repeat;
            container->draw_solid_fill(hdc, layer, color);
        }

        void add_rounded(vector_path& path, float x, float y, float w, float h, float r)
        {
            r              = std::max(0.f, std::min(r, std::min(w, h) / 2));
            const float k  = 0.5523f * r;
            auto        to = [&](vector_path::op_t op, std::initializer_list<float> points) {
                path.ops.push_back(op);
                path.points.insert(path.points.end(), points);
            };
            to(vector_path::move, {x + r, y});
            to(vector_path::line, {x + w - r, y});
            to(vector_path::cubic, {x + w - r + k, y, x + w, y + r - k, x + w, y + r});
            to(vector_path::line, {x + w, y + h - r});
            to(vector_path::cubic, {x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h});
            to(vector_path::line, {x + r, y + h});
            to(vector_path::cubic, {x + r - k, y + h, x, y + h - r + k, x, y + h - r});
            to(vector_path::line, {x, y + r});
            to(vector_path::cubic, {x, y + r - k, x + r - k, y, x + r, y});
            to(vector_path::close, {});
        }

        void stroke_box(uint_ptr hdc, document_container* container, const position& box, float radius, float width,
                        const web_color& color)
        {
            if(box.width <= 0_px || box.height <= 0_px || !color.alpha)
            {
                return;
            }
            vector_path path;
            float       half = width / 2;
            add_rounded(path, box.x.value() + half, box.y.value() + half, box.width.value() - width,
                        box.height.value() - width, std::max(0.f, radius - half));
            path.stroke       = true;
            path.stroke_width = width;
            path.stroke_color = color;
            container->draw_path(hdc, path);
        }

        void stroke_lines(uint_ptr hdc, document_container* container, std::initializer_list<float> points, float width,
                          const web_color& color)
        {
            vector_path path;
            bool        first = true;
            auto        it    = points.begin();
            while(it != points.end())
            {
                float px = *it++;
                if(it == points.end())
                {
                    break;
                }
                float py = *it++;
                path.ops.push_back(first ? vector_path::move : vector_path::line);
                path.points.push_back(px);
                path.points.push_back(py);
                first = false;
            }
            path.stroke       = true;
            path.stroke_width = width;
            path.stroke_color = color;
            path.line_cap     = 1;
            path.line_join    = 1;
            container->draw_path(hdc, path);
        }

        void draw_chevrons(uint_ptr hdc, document_container* container, float cx, float cy, float w, float h,
                           float gap, float width, const web_color& color)
        {
            float half = w / 2;
            stroke_lines(hdc, container, {cx - half, cy - gap, cx, cy - gap - h, cx + half, cy - gap}, width, color);
            stroke_lines(hdc, container, {cx - half, cy + gap, cx, cy + gap + h, cx + half, cy + gap}, width, color);
        }

        float attr_number(const html_tag* el, const char* name, float def)
        {
            const char* value = el->get_attr(name);
            if(!value || !*value)
            {
                return def;
            }
            char* end    = nullptr;
            float result = std::strtof(value, &end);
            return end == value || !std::isfinite(result) ? def : result;
        }

        bool radius_set(const css_borders& borders, float ua)
        {
            const auto& r = borders.radius;
            for(const css_length* len : {&r.top_left_x, &r.top_right_x, &r.bottom_right_x, &r.bottom_left_x})
            {
                if(std::fabs(len->val() - ua) > 0.01f)
                {
                    return true;
                }
            }
            return false;
        }

        bool has_image(const background& bg)
        {
            for(const auto& img : bg.m_image)
            {
                if(!img.is_empty())
                {
                    return true;
                }
            }
            return false;
        }

        bool authored_box(const css_properties& css, float ua_radius)
        {
            const css_borders& b = css.get_borders();
            for(const css_border* side : {&b.top, &b.right, &b.bottom, &b.left})
            {
                if(side->color != sentinel_border)
                {
                    return true;
                }
            }
            const background& bg = css.get_bg();
            return bg.m_color != sentinel_back || has_image(bg) || radius_set(b, ua_radius);
        }

        void resolve_box(css_properties& css, bool native, const web_color& border, const web_color& back)
        {
            css_borders borders = css.get_borders();
            for(css_border* side : {&borders.top, &borders.right, &borders.bottom, &borders.left})
            {
                if(native || side->color == sentinel_border)
                {
                    side->color = native ? web_color::transparent : border;
                }
            }
            css.set_borders(borders);
            background bg = css.get_bg();
            if(native || bg.m_color == sentinel_back)
            {
                bg.m_color = native ? web_color::transparent : back;
                css.set_bg(bg);
            }
        }

        void resolve_text(css_properties& css, const web_color& color)
        {
            if(css.get_color() == sentinel_text)
            {
                css.set_color(color);
            }
        }

        void add_padding(css_properties& css, float top, float right, float bottom, float left, bool replace)
        {
            css_margins padding = css.get_padding();
            auto        bump    = [&](css_length& len, float v) {
                float base = replace || len.units() == css_units_percentage ? 0.f : len.val();
                len.set_value(base + v, css_units_px);
            };
            bump(padding.top, top);
            bump(padding.right, right);
            bump(padding.bottom, bottom);
            bump(padding.left, left);
            css.set_padding(padding);
        }

        bool ua_padding(const css_properties& css)
        {
            const css_margins& p = css.get_padding();
            auto               is = [](const css_length& len, float v) {
                return len.units() == css_units_px && std::fabs(len.val() - v) < 0.0001f;
            };
            return is(p.top, 1.01f) && is(p.bottom, 1.01f) && is(p.left, 2.01f) &&
                   (is(p.right, 2.01f) || is(p.right, 2.02f));
        }

        void button_padding(css_properties& css, bool native)
        {
            const css_margins& p  = css.get_padding();
            auto               is = [](const css_length& len, float v) {
                return len.units() == css_units_px && std::fabs(len.val() - v) < 0.0001f;
            };
            if(!is(p.top, 0.01f) || !is(p.right, 6.01f) || !is(p.bottom, 1.01f) || !is(p.left, 6.01f))
            {
                return;
            }
            css_margins padding = p;
            padding.top.set_value(native ? 0.f : 2.f, css_units_px);
            padding.bottom.set_value(native ? 1.f : 3.f, css_units_px);
            padding.left.set_value(6.f, css_units_px);
            padding.right.set_value(6.f, css_units_px);
            css.set_padding(padding);
        }

        void field_padding(css_properties& css, bool native, bool number)
        {
            if(!ua_padding(css))
            {
                return;
            }
            float       size    = css.get_font_size().value();
            float       v       = native ? std::round(size * 0.2f) : 1.f;
            float       h       = native ? std::floor(size * 0.5f) : 1.f;
            css_margins padding = css.get_padding();
            padding.top.set_value(v, css_units_px);
            padding.bottom.set_value(v, css_units_px);
            padding.left.set_value(h, css_units_px);
            padding.right.set_value(number && native ? 2.f : h, css_units_px);
            css.set_padding(padding);
        }

        void refresh_active(form_look& look, const css_properties& css, document_container* container)
        {
            look.active = container->controls_active();
            web_color accent = css.get_accent_color();
            look.accent      = accent.alpha ? accent : container->control_accent();
        }

        position border_box(const std::shared_ptr<render_item>& ri, pixel_t x, pixel_t y)
        {
            position pos  = ri->pos();
            pos.x        += x;
            pos.y        += y;
            pos          += ri->get_paddings();
            pos          += ri->get_borders();
            return pos;
        }

        position inset(position box, float dx, float dy)
        {
            box.x      += pixel_t(dx);
            box.y      += pixel_t(dy);
            box.width  -= pixel_t(2 * dx);
            box.height -= pixel_t(2 * dy);
            return box;
        }

        void draw_bezel(uint_ptr hdc, document_container* container, const position& box, const form_look& look)
        {
            const palette& p = colors(look.dark);
            fill_box(hdc, container, inset(box, 2, 1.5f), 5_px, look.disabled ? p.bezel_off : p.bezel);
        }

        void draw_field(uint_ptr hdc, document_container* container, const position& box, const form_look& look,
                        float radius = 6)
        {
            const palette& p = colors(look.dark);
            fill_box(hdc, container, box, pixel_t(radius), p.field);
            stroke_box(hdc, container, box, radius, 0.5f, p.field_border);
        }
    }

    el_input::el_input(const document::ptr& doc) :
        html_tag(doc)
    {
    }

    bool el_input::is_text_like() const
    {
        return !is_one_of(m_type, "hidden", "checkbox", "radio", "range", "color", "button", "submit", "reset",
                          "image", "file");
    }

    bool el_input::is_button_like() const
    {
        return is_one_of(m_type, "button", "submit", "reset");
    }

    void el_input::parse_attributes()
    {
        html_tag::parse_attributes();
        remove_generated(this);
        const char* type = get_attr("type", "text");
        m_type           = lowcase(type ? type : "text");
        m_checked        = get_attr("checked") != nullptr;
        const char* value = get_attr("value");
        if(is_text_like())
        {
            std::string text        = value ? value : "";
            bool        placeholder = false;
            if(m_type == "date")
            {
                text = format_date(text, placeholder);
            } else if(text.empty())
            {
                const char* hint = get_attr("placeholder");
                if(hint && *hint)
                {
                    text        = hint;
                    placeholder = true;
                }
            }
            if(m_type == "password" && !placeholder)
            {
                std::string dots;
                for(unsigned char ch : text)
                {
                    if((ch & 0xC0) != 0x80)
                    {
                        dots += "\xE2\x80\xA2";
                    }
                }
                text = dots;
            }
            float size  = attr_number(this, "size", 20);
            size        = size >= 1 && size <= 1000 ? size : 20;
            float width = size * 0.636f + 0.182f - (m_type == "number" ? 0.91f : 0.f);
            std::string style = "display:block;flex:1 1 auto;min-width:0;overflow:hidden;white-space:pre;width:" +
                                std::to_string(width) + "em";
            if(placeholder)
            {
                style += ";color:#a9a9a9";
            }
            append_content(this, "lh-input-text", style, text.empty() ? "\xE2\x80\x8B" : text);
            if(m_type == "number")
            {
                append_content(this, "lh-input-spin", "display:block;flex:none;width:10px;align-self:stretch", "");
            }
        } else if(is_button_like())
        {
            std::string text = value ? value : (m_type == "reset" ? "Reset" : m_type == "submit" ? "Submit" : "");
            append_content(this, "lh-input-text", "display:block;white-space:pre",
                           text.empty() ? "\xE2\x80\x8B" : text);
        } else if(m_type == "range")
        {
            float min  = attr_number(this, "min", 0);
            float max  = attr_number(this, "max", 100);
            float val  = attr_number(this, "value", min + (max - min) / 2);
            m_fraction = max > min ? std::max(0.f, std::min(1.f, (val - min) / (max - min))) : 0.f;
            char percent[32];
            snprintf(percent, sizeof(percent), "%g%%", m_fraction * 100);
            auto doc   = get_document();
            auto track = std::make_shared<html_tag>(doc);
            track->set_tagName("lh-range-track");
            track->set_attr("style", "display:block;flex:1 1 auto;position:relative;min-width:0");
            auto thumb = std::make_shared<html_tag>(doc);
            thumb->set_tagName("lh-range-thumb");
            thumb->set_attr("style", (std::string("display:block;position:absolute;top:0;left:") + percent +
                                      ";transform:translateX(-" + percent + ")")
                                         .c_str());
            track->appendChild(thumb);
            appendChild(track);
            track->apply_stylesheet(doc->master_css());
        } else if(m_type == "color")
        {
            m_swatch = web_color::black;
            if(value && *value)
            {
                auto tokens = normalize(value, f_componentize | f_remove_whitespace);
                if(tokens.size() == 1)
                {
                    web_color color;
                    if(parse_color(tokens[0], color, get_document()->container()))
                    {
                        m_swatch = color;
                    }
                }
            }
        }
    }

    void el_input::compute_styles(bool recursive)
    {
        html_tag::compute_styles(false);
        css_properties& c = css_w();
        m_look.dark       = c.get_dark_scheme();
        m_look.disabled   = get_attr("disabled") != nullptr;
        bool appearance   = c.get_appearance() != appearance_none;
        const palette& p  = colors(m_look.dark);
        if(is_text_like())
        {
            m_look.native = appearance && !authored_box(c, 0);
            field_padding(c, m_look.native, m_type == "number");
            resolve_box(c, m_look.native, p.legacy_field_border, p.field);
            resolve_text(c, m_look.disabled ? p.disabled_text : p.text);
            make_flex(c, flex_direction_row, flex_justify_content_flex_start, flex_align_items_center);
        } else if(is_button_like())
        {
            m_look.native = appearance && !authored_box(c, 0);
            button_padding(c, m_look.native);
            resolve_box(c, m_look.native, p.button_border, p.button_face);
            resolve_text(c, m_look.disabled ? p.disabled_text : p.control_text);
            c.set_center_content(true);
        } else
        {
            m_look.native = appearance;
            if(m_type == "range")
            {
                if(!m_look.native)
                {
                    make_flex(c, flex_direction_row, flex_justify_content_flex_start, flex_align_items_center);
                }
            }
            resolve_box(c, false, web_color::transparent, web_color::transparent);
            resolve_text(c, p.text);
            if(m_look.native && is_one_of(m_type, "checkbox", "radio", "range", "color"))
            {
                background bg = c.get_bg();
                if(!has_image(bg))
                {
                    bg.m_color = web_color::transparent;
                    c.set_bg(bg);
                }
            }
        }
        if(recursive)
        {
            for(const auto& el : m_children)
            {
                el->compute_styles();
            }
        }
        if(m_type == "range" && m_look.native)
        {
            for(const auto& el : m_children)
            {
                el->css_w().set_display(display_none);
            }
        }
    }

    void el_input::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                        const std::shared_ptr<render_item>& ri)
    {
        html_tag::draw(hdc, x, y, clip, ri);
        position box = border_box(ri, x, y);
        if(box.width <= 0_px || box.height <= 0_px)
        {
            return;
        }
        auto           container = get_document()->container();
        const palette& p         = colors(m_look.dark);
        refresh_active(m_look, css(), container);
        bool           tint      = m_look.active && !m_look.disabled;
        if(is_text_like())
        {
            if(m_look.native)
            {
                draw_field(hdc, container, box, m_look, m_type == "search" ? box.height.value() / 2 : 6.f);
            }
            if(m_type == "number" && css().get_appearance() != appearance_none)
            {
                for(const auto& child : ri->children())
                {
                    const char* tag = child->src_el()->get_tagName();
                    if(!tag || strcmp(tag, "lh-input-spin") != 0)
                    {
                        continue;
                    }
                    position spin = child->pos();
                    spin.x       += x + ri->pos().x;
                    spin.y       += y + ri->pos().y;
                    float h       = std::min(12.5f, box.height.value() - 6);
                    if(h < 6)
                    {
                        break;
                    }
                    position bezel(spin.x, box.y + (box.height - pixel_t(h)) / 2_px, spin.width, pixel_t(h));
                    fill_box(hdc, container, bezel, 3_px, p.stepper);
                    float cx = (bezel.x + bezel.width / 2_px).value();
                    float cy = (bezel.y + bezel.height / 2_px).value();
                    fill_box(hdc, container, position(bezel.x + 1_px, pixel_t(cy - 0.5f), bezel.width - 2_px, 1_px), 0_px,
                             p.stepper_line);
                    float a = std::min(2.f, h / 6);
                    draw_chevrons(hdc, container, cx, cy, 5, a, h / 4 - a / 2 + 0.25f, 1, p.stepper_arrow);
                    break;
                }
            }
        } else if(!m_look.native)
        {
            return;
        } else if(is_button_like())
        {
            draw_bezel(hdc, container, box, m_look);
        } else if(m_type == "checkbox" || m_type == "radio")
        {
            float    side  = std::min(box.width.value(), box.height.value());
            position check(box.x + (box.width - pixel_t(side)) / 2_px, box.y + (box.height - pixel_t(side)) / 2_px,
                           pixel_t(side), pixel_t(side));
            float    radius = m_type == "radio" ? side / 2 : side * 0.29f;
            float    unit   = side / 12;
            if(m_checked)
            {
                fill_box(hdc, container, check, pixel_t(radius), tint ? m_look.accent : m_look.disabled ? p.box_on_off : p.box_on);
                web_color mark = tint ? web_color::white : m_look.disabled ? p.mark_off : p.mark;
                float     ox   = check.x.value();
                float     oy   = check.y.value();
                if(m_type == "radio")
                {
                    float d = 4.5f * unit;
                    fill_box(hdc, container,
                             position(pixel_t(ox + (side - d) / 2), pixel_t(oy + (side - d) / 2), pixel_t(d), pixel_t(d)),
                             pixel_t(d / 2), mark);
                } else
                {
                    stroke_lines(hdc, container,
                                 {ox + 3.4f * unit, oy + 6.4f * unit, ox + 5.3f * unit, oy + 8.4f * unit,
                                  ox + 8.7f * unit, oy + 3.7f * unit},
                                 1.45f * unit, mark);
                }
            } else
            {
                fill_box(hdc, container, check, pixel_t(radius), p.field);
                stroke_box(hdc, container, check, radius, 1.5f * unit,
                           m_look.disabled ? p.box_border_off : p.box_border);
            }
        } else if(m_type == "range")
        {
            float    thumb_w = std::min(24.f, box.width.value());
            float    thumb_h = 16;
            float    track_h = 8;
            float    cy      = (box.y + box.height / 2_px).value();
            float    travel  = std::max(0.f, box.width.value() - thumb_w);
            float    center  = box.x.value() + thumb_w / 2 + travel * m_fraction;
            position rest(box.x + 1_px, pixel_t(cy - track_h / 2), box.width - 2_px, pixel_t(track_h));
            fill_box(hdc, container, rest, pixel_t(track_h / 2), m_look.disabled ? p.track_rest_off : p.track_rest);
            position filled(rest.x, rest.y, pixel_t(center) - rest.x, rest.height);
            fill_box(hdc, container, filled, pixel_t(track_h / 2), tint ? m_look.accent : m_look.disabled ? p.track_fill_off : p.track_fill);
            position knob(pixel_t(center - thumb_w / 2), pixel_t(cy - thumb_h / 2), pixel_t(thumb_w), pixel_t(thumb_h));
            border_radiuses round;
            round.top_left_x = round.top_left_y = round.top_right_x = round.top_right_y = pixel_t(thumb_h / 2);
            round.bottom_left_x = round.bottom_left_y = round.bottom_right_x = round.bottom_right_y = pixel_t(thumb_h / 2);
            shadow_paint shadow;
            shadow.x      = 0_px;
            shadow.y      = 0_px;
            shadow.blur   = 3_px;
            shadow.spread = 0_px;
            shadow.color  = p.thumb_shadow;
            container->draw_box_shadow(hdc, knob, round, shadow);
            fill_box(hdc, container, knob, pixel_t(thumb_h / 2), p.thumb);
        } else if(m_type == "color")
        {
            fill_box(hdc, container, box, box.height / 2_px, p.well);
            position swatch = inset(box, 3, 3);
            fill_box(hdc, container, swatch, swatch.height / 2_px, m_swatch);
        }
    }

    el_select::el_select(const document::ptr& doc) :
        html_tag(doc)
    {
    }

    void el_select::parse_attributes()
    {
        html_tag::parse_attributes();
        remove_generated(this);
        std::string                                     label;
        bool                                            found = false;
        std::function<void(const element::ptr&, bool)> visit = [&](const element::ptr& el, bool any) {
            for(const auto& child : el->children())
            {
                if(found)
                {
                    return;
                }
                if(child->tag() == _option_)
                {
                    if(any || child->get_attr("selected"))
                    {
                        child->get_text(label);
                        found = true;
                        return;
                    }
                } else if(child->tag() == _optgroup_)
                {
                    visit(child, any);
                }
            }
        };
        visit(shared_from_this(), false);
        if(!found)
        {
            visit(shared_from_this(), true);
        }
        std::string trimmed = label;
        trim(trimmed);
        append_content(this, "lh-select-value", "display:block;flex:1 1 auto;min-width:0;overflow:hidden;white-space:pre",
                       trimmed.empty() ? "\xE2\x80\x8B" : trimmed);
    }

    void el_select::compute_styles(bool recursive)
    {
        html_tag::compute_styles(false);
        css_properties& c = css_w();
        const palette&  p = colors(c.get_dark_scheme());
        m_look.dark       = c.get_dark_scheme();
        m_look.disabled   = get_attr("disabled") != nullptr;
        bool appearance   = c.get_appearance() != appearance_none;
        m_look.native     = appearance && !authored_box(c, 5);
        m_arrow           = appearance;
        resolve_box(c, m_look.native, p.control_text, p.field);
        resolve_text(c, m_look.disabled ? p.disabled_text : p.control_text);
        if(m_look.native)
        {
            add_padding(c, 2.5f, 22.7f, 2.5f, 8.5f, false);
        } else if(appearance)
        {
            float v = c.get_font_size().value() * 0.1f;
            add_padding(c, v, 22.7f, v, 8.5f, true);
        }
        make_flex(c, flex_direction_row, flex_justify_content_flex_start, flex_align_items_center);
        if(recursive)
        {
            for(const auto& el : m_children)
            {
                el->compute_styles();
            }
        }
    }

    void el_select::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                         const std::shared_ptr<render_item>& ri)
    {
        html_tag::draw(hdc, x, y, clip, ri);
        position box = border_box(ri, x, y);
        if(box.width <= 0_px || box.height <= 0_px)
        {
            return;
        }
        auto container = get_document()->container();
        if(m_look.native)
        {
            draw_bezel(hdc, container, box, m_look);
        }
        if(m_arrow && box.width > 24_px)
        {
            float cx = (box.x + box.width).value() - 10.5f - 3.f;
            float cy = (box.y + box.height / 2_px).value();
            draw_chevrons(hdc, container, cx, cy, 6, 3, 1.25f, 1.3f, css().get_color());
        }
    }

    el_button::el_button(const document::ptr& doc) :
        html_tag(doc)
    {
    }

    void el_button::compute_styles(bool recursive)
    {
        html_tag::compute_styles(false);
        css_properties& c = css_w();
        const palette&  p = colors(c.get_dark_scheme());
        m_look.dark       = c.get_dark_scheme();
        m_look.disabled   = get_attr("disabled") != nullptr;
        m_look.native     = c.get_appearance() != appearance_none && !authored_box(c, 0);
        button_padding(c, m_look.native);
        resolve_box(c, m_look.native, p.button_border, p.button_face);
        resolve_text(c, m_look.disabled ? p.disabled_text : p.control_text);
        c.set_center_content(true);
        if(recursive)
        {
            for(const auto& el : m_children)
            {
                el->compute_styles();
            }
        }
    }

    void el_button::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                         const std::shared_ptr<render_item>& ri)
    {
        html_tag::draw(hdc, x, y, clip, ri);
        if(m_look.native)
        {
            draw_bezel(hdc, get_document()->container(), border_box(ri, x, y), m_look);
        }
    }

    el_textarea::el_textarea(const document::ptr& doc) :
        html_tag(doc)
    {
    }

    void el_textarea::parse_attributes()
    {
        html_tag::parse_attributes();
        remove_generated(this);
        const char* hint = get_attr("placeholder");
        if(hint && *hint && textarea_text(this).empty())
        {
            append_content(this, "lh-placeholder", "display:inline", hint);
            m_children.back()->apply_stylesheet(get_document()->master_css());
        }
    }

    void el_textarea::compute_styles(bool recursive)
    {
        if(has_generated(this) && !textarea_text(this).empty())
        {
            remove_generated(this);
        }
        html_tag::compute_styles(false);
        css_properties& c = css_w();
        const palette&  p = colors(c.get_dark_scheme());
        m_look.dark       = c.get_dark_scheme();
        m_look.disabled   = get_attr("disabled") != nullptr;
        m_look.native     = c.get_appearance() != appearance_none && !authored_box(c, 0);
        resolve_box(c, m_look.native, p.legacy_field_border, p.field);
        resolve_text(c, m_look.disabled ? p.disabled_text : p.text);
        if(recursive)
        {
            for(const auto& el : m_children)
            {
                el->compute_styles();
            }
        }
    }

    void el_textarea::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                           const std::shared_ptr<render_item>& ri)
    {
        auto     container = get_document()->container();
        position box       = border_box(ri, x, y);
        if(m_look.native)
        {
            draw_field(hdc, container, box, m_look);
        }
        html_tag::draw(hdc, x, y, clip, ri);
        if(box.width > 12_px && box.height > 12_px && css().get_resize() != resize_none)
        {
            float          rx = (box.x + box.width).value() - 4;
            float          by = (box.y + box.height).value() - 4;
            const palette& p  = colors(m_look.dark);
            stroke_lines(hdc, container, {rx - 8, by, rx, by - 8}, 1, p.stepper_arrow);
            stroke_lines(hdc, container, {rx - 4, by, rx, by - 4}, 1, p.stepper_arrow);
        }
    }

    el_meter::el_meter(const document::ptr& doc) :
        html_tag(doc)
    {
    }

    void el_meter::compute_styles(bool recursive)
    {
        html_tag::compute_styles(recursive);
        m_look.dark   = css().get_dark_scheme();
        m_look.native = css().get_appearance() != appearance_none;
    }

    void el_meter::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                        const std::shared_ptr<render_item>& ri)
    {
        html_tag::draw(hdc, x, y, clip, ri);
        position box = border_box(ri, x, y);
        if(!m_look.native || box.width <= 2_px || box.height <= 0_px)
        {
            return;
        }
        auto           container = get_document()->container();
        const palette& p         = colors(m_look.dark);
        bool           meter     = tag() == _meter_;
        float          track_h   = std::min(box.height.value(), meter ? 12.f : 8.f);
        position       track(box.x + 1_px, box.y + (box.height - pixel_t(track_h)) / 2_px, box.width - 2_px, pixel_t(track_h));
        fill_box(hdc, container, track, pixel_t(track_h / 2), p.track_rest);
        refresh_active(m_look, css(), container);
        float     fraction = 0;
        web_color fill     = m_look.active ? m_look.accent : p.track_fill;
        if(meter)
        {
            float min   = attr_number(this, "min", 0);
            float max   = std::max(min, attr_number(this, "max", 1));
            float value = std::max(min, std::min(max, attr_number(this, "value", 0)));
            float low   = std::max(min, std::min(max, attr_number(this, "low", min)));
            float high  = std::max(low, std::min(max, attr_number(this, "high", max)));
            float best  = std::max(min, std::min(max, attr_number(this, "optimum", (min + max) / 2)));
            fraction    = max > min ? (value - min) / (max - min) : 0;
            auto region = [&](float v) { return v < low ? 0 : v > high ? 2 : 1; };
            int  gap    = std::abs(region(value) - region(best));
            fill        = gap == 0 ? (m_look.dark ? web_color(48, 209, 88) : web_color(52, 199, 89))
                          : gap == 1 ? (m_look.dark ? web_color(255, 214, 10) : web_color(255, 204, 0))
                                     : (m_look.dark ? web_color(255, 69, 58) : web_color(255, 59, 48));
        } else if(get_attr("value"))
        {
            float max = attr_number(this, "max", 1);
            max       = max > 0 ? max : 1;
            fraction  = std::max(0.f, std::min(1.f, attr_number(this, "value", 0) / max));
        }
        if(fraction > 0)
        {
            position filled = track;
            filled.width    = pixel_t(track.width.value() * fraction);
            fill_box(hdc, container, filled, pixel_t(track_h / 2), fill);
        }
    }

    el_fieldset::el_fieldset(const document::ptr& doc) :
        html_tag(doc)
    {
    }

    element::ptr el_fieldset::legend() const
    {
        for(const auto& child : m_children)
        {
            if(child->tag() == _legend_)
            {
                const auto& lc = child->css();
                if(lc.get_display() == display_none || lc.get_float() != float_none ||
                   lc.get_position() == element_position_absolute || lc.get_position() == element_position_fixed)
                {
                    return nullptr;
                }
                return child;
            }
            if(!child->is_white_space() && !child->is_comment())
            {
                return nullptr;
            }
        }
        return nullptr;
    }

    void el_fieldset::compute_styles(bool recursive)
    {
        html_tag::compute_styles(recursive);
        auto first = legend();
        if(!first)
        {
            return;
        }
        const css_properties& c       = css();
        float                 border  = c.get_borders().top.style > border_style_hidden ? c.get_borders().top.width.val() : 0;
        float                 padding = c.get_padding().top.units() == css_units_percentage ? 0 : c.get_padding().top.val();
        css_properties&       lc      = first->css_w();
        float                 lh      = lc.line_height().computed_value.value();
        const css_margins&    lp      = lc.get_padding();
        const css_borders&    lb      = lc.get_borders();
        auto                  px      = [](const css_length& len) { return len.units() == css_units_percentage ? 0.f : len.val(); };
        float legend_h = lh + px(lp.top) + px(lp.bottom) +
                         (lb.top.style > border_style_hidden ? lb.top.width.val() : 0) +
                         (lb.bottom.style > border_style_hidden ? lb.bottom.width.val() : 0);
        css_margins margins = lc.get_margins();
        margins.top.set_value(-(border + padding), css_units_px);
        margins.bottom.set_value(padding + std::max(0.f, border - legend_h), css_units_px);
        lc.set_margins(margins);
    }

    void el_fieldset::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                           const std::shared_ptr<render_item>& ri)
    {
        auto first = legend();
        std::shared_ptr<render_item> legend_ri;
        if(first)
        {
            for(const auto& child : ri->children())
            {
                if(child->src_el() == first)
                {
                    legend_ri = child;
                    break;
                }
            }
        }
        if(!legend_ri)
        {
            html_tag::draw(hdc, x, y, clip, ri);
            return;
        }
        auto            container = get_document()->container();
        position        box       = border_box(ri, x, y);
        const auto&     cb        = css().get_borders();
        pixel_t         top_w     = ri->get_borders().top;
        position        legend_box = legend_ri->pos();
        legend_box.x             += x + ri->pos().x;
        legend_box.y             += y + ri->pos().y;
        legend_box               += legend_ri->get_paddings();
        legend_box               += legend_ri->get_borders();
        pixel_t shift             = std::max(0_px, (legend_box.height - top_w) / 2_px);
        position frame            = box;
        frame.y                  += shift;
        frame.height             -= shift;
        border_radiuses radius    = cb.radius.calc_percents(frame.width, frame.height);

        const background* bg = get_background();
        if(bg)
        {
            position content = frame;
            content         -= ri->get_borders();
            content         -= ri->get_paddings();
            for(int i = bg->get_layers_number() - 1; i >= 0; i--)
            {
                background_layer layer;
                if(!bg->get_layer(i, content, this, ri, layer))
                {
                    continue;
                }
                layer.border_box    = frame;
                layer.border_radius = radius;
                layer.border_box.round();
                layer.clip_box.round();
                layer.origin_box.round();
                bg->draw_layer(hdc, i, layer, container);
            }
        }
        if(cb.is_visible())
        {
            borders b = cb;
            b.radius  = radius;
            frame.round();
            border_radiuses none{};
            pixel_t gap_l = legend_box.x;
            pixel_t gap_r = legend_box.x + legend_box.width;
            pixel_t left  = frame.x - 1_px;
            pixel_t right = frame.x + frame.width + 1_px;
            pixel_t above = frame.y - 1_px;
            pixel_t below = frame.y + frame.height + 1_px;
            position parts[3] = {
                position(left, above, gap_l - left, below - above),
                position(gap_r, above, right - gap_r, below - above),
                position(gap_l, frame.y + top_w, gap_r - gap_l, below - frame.y - top_w),
            };
            for(const auto& part : parts)
            {
                if(part.width <= 0_px || part.height <= 0_px)
                {
                    continue;
                }
                container->set_clip(part, none);
                container->draw_borders(hdc, b, frame, false);
                container->del_clip();
            }
        }
    }
} // namespace litehtml
