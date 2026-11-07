#include "html.h"
#include "el_text.h"
#include "render_item.h"
#include "render_inline.h"
#include "document_container.h"

litehtml::el_text::el_text(const char* text, const document::ptr& doc) :
    element(doc)
{
    if(text)
    {
        m_text = text;
    }
    m_use_transformed = false;
    m_draw_spaces     = true;
    css_w().set_display(display_inline_text);
}

void litehtml::el_text::get_content_size(size& sz, pixel_t /*max_width*/)
{
    sz = m_size;
}

void litehtml::el_text::get_text(std::string& text) const
{
    text += m_text;
}

int litehtml::el_text::strong_direction() const
{
    if(m_strong_direction != 2)
        return m_strong_direction;
    m_strong_direction = 0;
    for(size_t i = 0; i < m_text.size();)
    {
        unsigned char c = m_text[i];
        if(c < 0x80)
        {
            if((c | 0x20) >= 'a' && (c | 0x20) <= 'z')
            {
                m_strong_direction = -1;
                break;
            }
            i++;
            continue;
        }
        size_t   n  = std::min<size_t>((c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : 4, m_text.size() - i);
        uint32_t cp = n == 2 ? c & 0x1F : n == 3 ? c & 0x0F : c & 0x07;
        for(size_t k = 1; k < n; k++)
            cp = (cp << 6) | (static_cast<unsigned char>(m_text[i + k]) & 0x3F);
        i += n;
        // Hebrew, Arabic, Syriac, Thaana, NKo, Samaritan and their presentation forms
        if((cp >= 0x0590 && cp <= 0x08FF) || (cp >= 0xFB1D && cp <= 0xFDFF) || (cp >= 0xFE70 && cp <= 0xFEFF) ||
           (cp >= 0x10800 && cp <= 0x10FFF) || (cp >= 0x1E800 && cp <= 0x1EFFF))
        {
            m_strong_direction = 1;
            break;
        }
        // Not punctuation, symbols, combining marks, variation selectors or emoji: a letter of a left to right script.
        if(cp >= 0xC0 && cp != 0xD7 && cp != 0xF7 && !(cp >= 0x0300 && cp <= 0x036F) && !(cp >= 0x2000 && cp <= 0x2BFF) &&
           !(cp >= 0x3000 && cp <= 0x303F) && !(cp >= 0xFE00 && cp <= 0xFE6F) && !(cp >= 0xFF00 && cp <= 0xFF20) && cp < 0x1F000)
        {
            m_strong_direction = -1;
            break;
        }
    }
    return m_strong_direction;
}

void litehtml::el_text::set_text(const std::string& text)
{
    m_text = text;
    m_strong_direction = 2;
    m_transformed_text.clear();
    m_use_transformed = false;
    m_draw_spaces     = true;
    m_size            = size();
    m_measured_font   = 0;
    m_breaker.reset();
    mark_layout_dirty();
    css_w()           = css_properties();
    css_w().set_display(display_inline_text);
}

void litehtml::el_text::compute_styles(bool /*recursive*/)
{
    mark_layout_dirty();
    element::ptr el_parent = parent();
    if(el_parent)
    {
        css_w().line_height_w() = el_parent->css().line_height();
        css_w().set_font(el_parent->css().get_font());
        css_w().set_font_metrics(el_parent->css().get_font_metrics());
        css_w().set_white_space(el_parent->css().get_white_space());
        css_w().set_overflow_wrap(el_parent->css().get_overflow_wrap());
        css_w().set_word_break(el_parent->css().get_word_break());
        css_w().set_text_transform(el_parent->css().get_text_transform());
    }
    css_w().set_display(display_inline_text);
    css_w().set_float(float_none);

    if(m_css.get_text_transform() != text_transform_none)
    {
        m_transformed_text = m_text;
        m_use_transformed  = true;
        get_document()->container()->transform_text(m_transformed_text, m_css.get_text_transform());
    } else
    {
        m_use_transformed = false;
    }

    element::ptr p = parent();
    while(p && p->css().get_display() == display_inline)
    {
        if(p->css().get_position() == element_position_relative)
        {
            css_w().set_offsets(p->css().get_offsets());
            css_w().set_position(element_position_relative);
            break;
        }
        p = p->parent();
    }
    if(p)
    {
        css_w().set_position(element_position_static);
    }

    if(is_white_space())
    {
        m_transformed_text = " ";
        m_use_transformed  = true;
    } else
    {
        if(m_text == "\t")
        {
            m_transformed_text = "    ";
            m_use_transformed  = true;
        }
        if(m_text == "\n" || m_text == "\r")
        {
            m_transformed_text = "";
            m_use_transformed  = true;
        }
    }

    font_metrics fm;
    uint_ptr     font = 0;
    if(el_parent)
    {
        font = el_parent->css().get_font();
        fm   = el_parent->css().get_font_metrics();
    }
    if(is_break() || !font)
    {
        m_size.height      = 0;
        m_size.width       = 0;
        m_measured_font    = 0;
    } else
    {
        m_size.height = fm.height;
        const int transform = m_use_transformed ? (is_white_space() ? -2 : int(m_css.get_text_transform())) : -3;
        if(m_measured_font != font || m_measured_transform != transform)
        {
            m_breaker.reset();
            m_size.width = get_document()->container()->text_width(
                m_use_transformed ? m_transformed_text.c_str() : m_text.c_str(), font);
            m_measured_font      = font;
            m_measured_transform = transform;
        }
    }
    m_draw_spaces = fm.draw_spaces;
}

std::shared_ptr<litehtml::text_breaker> litehtml::el_text::breaker()
{
    if(!m_breaker)
        m_breaker = doc_ptr()->container()->create_text_breaker(display_text(), css().get_font());
    return m_breaker;
}

void litehtml::el_text::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                             const std::shared_ptr<render_item>& ri)
{
    if(is_white_space() && !m_draw_spaces)
    {
        return;
    }

    const auto text_item = std::dynamic_pointer_cast<render_text>(ri);
    if(text_item && !text_item->fragments.empty())
    {
        for(const auto& fragment : text_item->fragments)
            draw(hdc, x, y, clip, fragment);
        return;
    }

    position pos  = ri->pos();
    pos.x        += x;
    pos.y        += y;
    {
        const float k = pixel_snap_scale();
        pos.y         = std::round(pos.y.value() * k) / k;
    }

    position ink = pos;
    {
        pixel_t slack = std::max(pixel_t(4), ink.height / 2_px);
        if(auto owner = parent())
        {
            float reach = 0;
            for(const auto& shadow : owner->css().get_text_shadows())
                reach = std::max(reach, std::abs(shadow.blur.val()) * 2 + std::abs(shadow.spread.val()) +
                                           std::max(std::abs(shadow.x.val()), std::abs(shadow.y.val())));
            slack += pixel_t(reach);
        }
        ink.x     -= slack;
        ink.y     -= slack;
        ink.width += slack * 2_px;
        ink.height += slack * 2_px;
    }
    if(ink.does_intersect(clip))
    {
        element::ptr el_parent = parent();
        if(el_parent)
        {
            document* doc = doc_ptr();

            uint_ptr font = el_parent->css().get_font();
            if(font)
            {
                web_color color = el_parent->css().get_color();
                const std::string& shown = text_item && text_item->is_fragment ? text_item->fragment_text : display_text();
                if(text_item && text_item->cut >= 0)
                {
                    // text-overflow: ellipsis cut it (render_item_inline_context::apply_ellipsis).
                    if(text_item->cut > 0)
                        doc->container()->draw_text(hdc, shown.substr(0, text_item->cut).c_str(), font, color, pos);
                } else
                {
                    doc->container()->draw_text(hdc, shown.c_str(), font, color, pos);
                }
            }
        }
    }
}

std::string litehtml::el_text::dump_get_name()
{
    return "text: \"" + get_escaped_string(m_text) + "\"";
}

std::vector<std::tuple<std::string, std::string>> litehtml::el_text::dump_get_attrs()
{
    return {};
}
