#include "render_inline_context.h"
#include "document.h"
#include "iterators.h"
#include "types.h"
#include "el_text.h"
#include "html_tag.h"
#include "document_container.h"
#include <string_view>

namespace
{
    class wrapped_text_item : public litehtml::line_box_item
    {
      public:
        wrapped_text_item(const std::shared_ptr<litehtml::render_item>& item, litehtml::pixel_t minimum) : line_box_item(item)
        {
            set_rendered_min_width(minimum);
        }
        litehtml::pixel_t get_rendered_natural_width() const override { return width(); }
    };
}

litehtml::rendered_width litehtml::render_item_inline_context::measure(pixel_t x, pixel_t y,
    const containing_block_context& cb, formatting_context* fmt_ctx, bool second_pass, pixel_t* measured_height)
{
    if(!measured_height && (cb.size_mode & containing_block_context::size_mode_content) &&
       css().get_width().is_predefined() && css().get_min_width().is_predefined() && css().get_max_width().is_predefined() &&
       !css().contains(contain_size) && !css().contains(contain_inline_size) &&
       (css().get_list_style_type() == list_style_type_none || css().get_list_style_position() != list_style_position_inside) &&
       css().get_text_indent().val() == 0 &&
       !m_children.empty())
    {
        calc_outlines(cb.width);
        auto self_size = calculate_containing_block_context(cb);
        if(static_cast<pixel_t>(self_size.render_width) <= 0_px)
        {
            pixel_t minimum = 0_px;
            bool supported = true;
            for(const auto& child : m_children)
            {
                auto text = std::dynamic_pointer_cast<el_text>(child->src_el());
                if(!text || text->css().get_wrapping() != overflow_wrap_anywhere ||
                   is_one_of(text->css().get_white_space(), white_space_pre, white_space_nowrap))
                {
                    supported = false;
                    break;
                }
                if(text->is_white_space())
                    continue;
                auto breaker = text->breaker();
                if(!breaker)
                {
                    supported = false;
                    break;
                }
                minimum = std::max(minimum, breaker->minimum_width());
            }
            if(supported)
            {
                rendered_width result;
                result.natural_width = minimum + content_offset_width();
                result.min_width = is_one_of(css().get_overflow(), overflow_visible, overflow_clip) ?
                    result.natural_width : content_offset_width();
                return result;
            }
        }
    }
    return render_item::measure(x, y, cb, fmt_ctx, second_pass, measured_height);
}

litehtml::size litehtml::render_item_inline_context::text_content_size() const
{
    pixel_t width = m_rendered_width.natural_width;
    pixel_t height = m_line_boxes.empty() ? 0_px : m_line_boxes.back()->bottom();
    if(src_el()->tag() == _textarea_)
    {
        const pixel_t line = css().line_height().computed_value;
        height = pixel_t(static_cast<float>(m_line_boxes.size())) * line;
        if(!m_children.empty() && m_children.back()->src_el()->is_break())
            height += line;
        height = std::max(height, line);
        if(css().get_white_space() == white_space_pre_wrap)
        {
            width = 0_px;
            for(const auto& child : m_children)
                if(!child->src_el()->is_space())
                    width = std::max(width, child->right());
        }
    }
    return size(width, height);
}

litehtml::rendered_width litehtml::render_item_inline_context::_render_content(
    pixel_t /*x*/, pixel_t /*y*/, bool /*second_pass*/, const containing_block_context& self_size,
    formatting_context* fmt_ctx)
{
    m_line_boxes.clear();
    m_fragmented_texts.clear();
    m_rendered_width.reset();
    m_va_applied = 0_px;

    white_space ws          = src_el()->css().get_white_space();
    bool        skip_spaces = false;
    if(ws == white_space_normal || ws == white_space_nowrap || ws == white_space_pre_line)
    {
        skip_spaces = true;
    }

    bool was_space = false;

    go_inside_inline  go_inside_inlines_selector;
    inline_selector   select_inlines;
    elements_iterator inlines_iter(true, &go_inside_inlines_selector, &select_inlines);

    std::vector<std::pair<std::shared_ptr<render_item>, iterator_item_type>> events;
    inlines_iter.process(shared_from_this(), [&](const std::shared_ptr<render_item>& el, iterator_item_type item_type) {
        events.emplace_back(el, item_type);
    });
    for(size_t index = 0; index < events.size(); index++)
    {
        const auto& el        = events[index].first;
        const auto  item_type = events[index].second;
        m_trailing            = 0_px;
        if(item_type == iterator_item_type_child)
        {
            size_t next = index + 1;
            for(; next < events.size() && events[next].second == iterator_item_type_end_parent; next++)
                m_trailing += lbi_end(events[next].first).width();
            if(next > index + 1 && next < events.size() && events[next].second == iterator_item_type_child &&
               el->src_el()->is_text() && !el->src_el()->is_white_space() && !el->src_el()->is_break())
            {
                auto text = std::dynamic_pointer_cast<el_text>(events[next].first->src_el());
                if(text && !text->display_text().empty() &&
                   std::string_view(",.;:!?%)]}").find(text->display_text().front()) != std::string_view::npos)
                {
                    size measured;
                    events[next].first->src_el()->get_content_size(measured, self_size.render_width);
                    m_trailing += measured.width;
                    for(next++; next < events.size() && events[next].second == iterator_item_type_end_parent; next++)
                        m_trailing += lbi_end(events[next].first).width();
                }
            }
        }
        [&]() {
        switch(item_type)
        {
        case iterator_item_type_child:
            {
                // skip spaces to make rendering a bit faster
                if(skip_spaces)
                {
                    if(el->src_el()->is_white_space())
                    {
                        if(was_space)
                        {
                            el->skip(true);
                            return;
                        }
                        was_space = true;
                    } else
                    {
                        // skip all spaces after line break
                        was_space = el->src_el()->is_break();
                    }
                }
                // place element into rendering flow
                place_inline(std::make_unique<line_box_item>(el), self_size, fmt_ctx);
            }
            break;

        case iterator_item_type_start_parent:
            {
                el->clear_inline_boxes();
                el->pos() = position();
                place_inline(std::make_unique<lbi_start>(el), self_size, fmt_ctx);
            }
            break;

        case iterator_item_type_end_parent:
            {
                place_inline(std::make_unique<lbi_end>(el), self_size, fmt_ctx);
            }
            break;
        }
        }();
    }
    m_trailing = 0_px;

    finish_last_box(true, self_size);
    for(const auto& text : m_fragmented_texts)
    {
        text->update_fragment_bounds();
        if(text->css().get_wrapping() != overflow_wrap_anywhere || static_cast<pixel_t>(self_size.render_width) > 0_px)
        {
            size unbroken;
            text->src_el()->get_content_size(unbroken, self_size.render_width);
            m_rendered_width.natural_width = std::max(m_rendered_width.natural_width, unbroken.width);
        }
    }

    apply_ellipsis(self_size.render_width);

    if(!m_line_boxes.empty())
    {
        if(collapse_top_margin())
        {
            pixel_t old_top = m_margins.top;
            m_margins.top   = std::max(m_line_boxes.front()->top_margin(), m_margins.top);
            if(m_margins.top != old_top)
            {
                fmt_ctx->update_floats(m_margins.top - old_top, shared_from_this());
            }
        }
        if(collapse_bottom_margin())
        {
            m_margins.bottom = std::max(m_line_boxes.back()->bottom_margin(), m_margins.bottom);
            m_pos.height     = m_line_boxes.back()->bottom() - m_line_boxes.back()->bottom_margin();
        } else
        {
            m_pos.height = m_line_boxes.back()->bottom();
        }
    }

    return m_rendered_width;
}

void litehtml::render_item_inline_context::apply_ellipsis(pixel_t room)
{
    const auto tag     = std::dynamic_pointer_cast<html_tag>(src_el());
    std::string mode   = tag ? tag->raw_declaration("text-overflow") : std::string();
    const bool  active = !mode.empty() && lowcase(trim(mode)) == "ellipsis" && css().clips_contents() &&
                        css().get_direction() != direction_rtl;
    // Most boxes have no ellipsis and never had one: their lines are left alone.
    if(!active && !m_cut)
        return;
    m_cut = active;
    m_ellipses.clear();
    auto* container = src_el()->get_document()->container();
    const pixel_t mark = active ? container->text_width("\xE2\x80\xA6", css().get_font()) : 0_px;
    for(const auto& line : m_line_boxes)
    {
        pixel_t right = 0_px;
        for(const auto& item : line->items())
        {
            if(auto text = std::dynamic_pointer_cast<render_text>(item->get_el()))
                text->cut = -1;
            right = std::max(right, item->right());
        }
        if(!active || right <= room)
            continue;
        // Whole characters up to the room the mark leaves, as browsers cut; what follows is not drawn.
        const pixel_t limit = room - mark;
        bool          done  = false;
        position      at(line->left(), line->top(), 0_px, line->height());
        for(const auto& item : line->items())
        {
            auto text = std::dynamic_pointer_cast<render_text>(item->get_el());
            if(done)
            {
                if(text)
                    text->cut = 0;
                continue;
            }
            if(item->right() <= limit)
            {
                at.x = std::max(at.x, item->right());
                continue;
            }
            done = true;
            if(!text || !text->src_el()->parent())
                continue;
            const std::string& shown = text->is_fragment ? text->fragment_text
                                                         : static_cast<const el_text*>(text->src_el().get())->display_text();
            uint_ptr font = text->src_el()->parent()->css().get_font();
            size_t   fit  = 0;
            pixel_t  used = 0_px;
            for(size_t i = 0; i < shown.size();)
            {
                size_t next = i + 1;
                while(next < shown.size() && (static_cast<unsigned char>(shown[next]) & 0xC0) == 0x80)
                    next++;
                pixel_t width = container->text_width(shown.substr(0, next).c_str(), font);
                if(item->left() + width > limit)
                    break;
                fit  = next;
                used = width;
                i    = next;
            }
            text->cut = static_cast<int>(fit);
            at.x      = item->left() + used;
            at.y      = text->pos().y;
            at.height = text->pos().height;
        }
        at.width = mark;
        m_ellipses.push_back(at);
    }
}

void litehtml::render_item_inline_context::draw_children(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                                         draw_flag flag, int zindex)
{
    render_item_block::draw_children(hdc, x, y, clip, flag, zindex);
    if(flag != draw_inlines || m_ellipses.empty() || !css().get_font())
        return;
    for(position at : m_ellipses)
    {
        at.x += m_pos.x + x;
        at.y += m_pos.y + y;
        src_el()->get_document()->container()->draw_text(hdc, "\xE2\x80\xA6", css().get_font(), css().get_color(), at);
    }
}

void litehtml::render_item_inline_context::fix_line_width(element_float flt, const containing_block_context& self_size,
                                                          formatting_context* fmt_ctx)
{
    if(!m_line_boxes.empty())
    {
        auto el_front = m_line_boxes.back()->get_first_text_part();

        std::vector<std::shared_ptr<render_item>> els;
        bool                                      was_cleared = false;
        if(el_front && el_front->src_el()->css().get_clear() != clear_none)
        {
            if(el_front->src_el()->css().get_clear() == clear_both)
            {
                was_cleared = true;
            } else
            {
                if((flt == float_left && el_front->src_el()->css().get_clear() == clear_left) ||
                   (flt == float_right && el_front->src_el()->css().get_clear() == clear_right))
                {
                    was_cleared = true;
                }
            }
        }

        if(!was_cleared)
        {
            std::list<std::unique_ptr<line_box_item>> items = std::move(m_line_boxes.back()->items());
            m_line_boxes.pop_back();

            for(auto& item : items)
            {
                place_inline(std::move(item), self_size, fmt_ctx);
            }
        } else
        {
            pixel_t line_top = 0_px;
            line_top         = m_line_boxes.back()->top();

            pixel_t line_left  = 0_px;
            pixel_t line_right = self_size.render_width;
            fmt_ctx->get_line_left_right(line_top, self_size.render_width, line_left, line_right);

            if(m_line_boxes.size() == 1)
            {
                if(src_el()->css().get_list_style_type() != list_style_type_none &&
                   src_el()->css().get_list_style_position() == list_style_position_inside)
                {
                    pixel_t sz_font  = src_el()->css().get_font_size();
                    line_left       += sz_font;
                }

                if(pixel_t(src_el()->css().get_text_indent().val()) != 0_px)
                {
                    line_left += src_el()->css().get_text_indent().calc_percent(self_size.width);
                }
            }

            auto items = m_line_boxes.back()->new_width(line_left, line_right);
            for(auto& item : items)
            {
                place_inline(std::move(item), self_size, fmt_ctx);
            }
        }
    }
}

std::list<std::unique_ptr<litehtml::line_box_item>> litehtml::render_item_inline_context::finish_last_box(
    bool end_of_render, const containing_block_context& self_size)
{
    std::list<std::unique_ptr<line_box_item>> ret;

    if(!m_line_boxes.empty())
    {
        ret = m_line_boxes.back()->finish(end_of_render, self_size);

        if(m_line_boxes.back()->is_empty() && end_of_render)
        {
            // remove the last empty line
            m_line_boxes.pop_back();
        } else
        {
            rendered_width line_width = m_line_boxes.back()->get_rendered_width();
            // The first line starts after an inside list marker and the text indent, which its items
            // do not count; without them a max-content width is short and the last word wraps.
            if(m_line_boxes.size() == 1)
            {
                if(src_el()->css().get_list_style_type() != list_style_type_none &&
                   src_el()->css().get_list_style_position() == list_style_position_inside)
                {
                    line_width.natural_width += src_el()->css().get_font_size();
                }
                if(pixel_t(src_el()->css().get_text_indent().val()) != 0_px)
                {
                    line_width.natural_width += src_el()->css().get_text_indent().calc_percent(self_size.width);
                }
            }
            m_rendered_width.merge(line_width);
        }
    }
    return ret;
}

litehtml::pixel_t litehtml::render_item_inline_context::new_box(const std::unique_ptr<line_box_item>& el,
                                                                const containing_block_context&       self_size,
                                                                formatting_context*                   fmt_ctx)
{
    auto    items    = finish_last_box(false, self_size);
    pixel_t line_top = 0_px;
    if(!m_line_boxes.empty())
    {
        line_top = m_line_boxes.back()->bottom();
    }
    line_top = fmt_ctx->get_cleared_top(el->get_el(), line_top);

    pixel_t first_line_margin = 0_px;
    pixel_t text_indent       = 0_px;
    if(m_line_boxes.empty())
    {
        if(src_el()->css().get_list_style_type() != list_style_type_none &&
           src_el()->css().get_list_style_position() == list_style_position_inside)
        {
            first_line_margin = src_el()->css().get_font_size();
        }
        if(pixel_t(src_el()->css().get_text_indent().val()) != 0_px)
        {
            text_indent = src_el()->css().get_text_indent().calc_percent(self_size.width);
        }
    }

    formatting_context::el_position el_pos;
    el_pos.container_width  = self_size.render_width;
    el_pos.el_pos           = el->pos();
    el_pos.el_pos.y         = line_top;
    el_pos.el_pos.x         = 0;
    el_pos.el_pos.width    += text_indent + first_line_margin;
    auto new_pos            = fmt_ctx->place_to_left(el_pos);

    m_line_boxes.emplace_back(std::make_unique<line_box>(new_pos.top, new_pos.left + first_line_margin + text_indent,
                                                         new_pos.left + new_pos.width, css().line_height(),
                                                         css().get_font_metrics(), css().get_text_align(),
                                                         css().get_direction() == direction_rtl));

    // Add items returned by finish_last_box function into the new line
    for(auto& it : items)
    {
        m_line_boxes.back()->add_item(std::move(it));
    }

    return new_pos.top;
}

void litehtml::render_item_inline_context::place_inline(std::unique_ptr<line_box_item>  item,
                                                        const containing_block_context& self_size,
                                                        formatting_context*             fmt_ctx)
{
    if(item->get_el()->src_el()->css().get_display() == display_none)
    {
        return;
    }

    if(item->get_el()->src_el()->is_float())
    {
        pixel_t line_top = 0_px;
        if(!m_line_boxes.empty())
        {
            line_top = m_line_boxes.back()->top();
        }
        auto rw = place_float(item->get_el(), line_top, self_size, fmt_ctx);
        m_rendered_width.merge(rw);
        return;
    }

    if(item->get_type() == line_box_item::type_text_part)
    {
        if(item->get_el()->src_el()->is_inline_box())
        {
            pixel_t min_rendered_width = item->get_el()->render(0_px, 0_px, self_size, fmt_ctx).natural_width;
            if(min_rendered_width < item->get_el()->width() &&
               item->get_el()->src_el()->css().get_width().is_predefined())
            {
                item->get_el()->render(0_px, 0_px, self_size.new_width(min_rendered_width), fmt_ctx);
            }
            item->set_rendered_min_width(min_rendered_width);
        } else if(item->get_el()->src_el()->css().get_display() == display_inline_text)
        {
            auto text_item = std::dynamic_pointer_cast<render_text>(item->get_el());
            if(text_item && !text_item->is_fragment)
            {
                text_item->fragments.clear();
                litehtml::size sz;
                item->get_el()->src_el()->get_content_size(sz, self_size.render_width);
                item->get_el()->pos() = sz;
                item->set_rendered_min_width(sz.width);
                auto text = std::dynamic_pointer_cast<el_text>(item->get_el()->src_el());
                const auto wrapping = text_item->css().get_wrapping();
                const auto whitespace = text_item->css().get_white_space();
                if(text && !text->is_space() && !text->is_break() && wrapping != overflow_wrap_normal &&
                   whitespace != white_space_nowrap && whitespace != white_space_pre)
                {
                    pixel_t line_left = 0_px;
                    pixel_t line_right = self_size.render_width;
                    fmt_ctx->get_line_left_right(m_line_boxes.empty() ? 0_px : m_line_boxes.back()->top(),
                        self_size.render_width, line_left, line_right);
                    if(m_line_boxes.empty())
                    {
                        line_left += src_el()->css().get_text_indent().calc_percent(self_size.width);
                        if(src_el()->css().get_list_style_type() != list_style_type_none &&
                           src_el()->css().get_list_style_position() == list_style_position_inside)
                            line_left += src_el()->css().get_font_size();
                    } else
                        line_left = std::max(line_left, m_line_boxes.back()->left());
                    const pixel_t line_width = line_right - line_left;
                    auto breaker = wrapping == overflow_wrap_anywhere || sz.width + m_trailing > line_width ? text->breaker() : nullptr;
                    if(breaker)
                    {
                        const pixel_t minimum = wrapping == overflow_wrap_anywhere ? breaker->minimum_width() : sz.width;
                        item = std::make_unique<wrapped_text_item>(text_item, minimum);
                        if(sz.width + m_trailing > line_width)
                        {
                            const std::string& content = text->display_text();
                            const bool needs_line = m_line_boxes.empty() || !m_line_boxes.back()->can_hold(item, whitespace, m_trailing);
                            text_item->pos().width = breaker->minimum_width();
                            if(needs_line)
                                new_box(item, self_size, fmt_ctx);
                            const pixel_t trailing = m_trailing;
                            for(size_t start = 0; start < content.size();)
                            {
                                pixel_t available = m_line_boxes.back()->line_right() - m_line_boxes.back()->right();
                                size_t end = breaker->fit(start, available);
                                if(end <= start || end > content.size())
                                    break;
                                if(end == content.size() && trailing > 0_px)
                                    end = breaker->fit(start, available - trailing);
                                auto fragment = std::make_shared<render_text>(text_item->src_el());
                                fragment->parent(text_item->parent());
                                fragment->is_fragment = true;
                                fragment->fragment_text = content.substr(start, end - start);
                                fragment->pos() = size(text->doc_ptr()->container()->text_width(fragment->fragment_text.c_str(), text->css().get_font()), sz.height);
                                text_item->fragments.push_back(fragment);
                                m_trailing = end == content.size() ? trailing : 0_px;
                                place_inline(std::make_unique<wrapped_text_item>(fragment, minimum), self_size, fmt_ctx);
                                start = end;
                                if(start < content.size())
                                    new_box(item, self_size, fmt_ctx);
                            }
                            m_trailing = trailing;
                            if(!text_item->fragments.empty())
                            {
                                m_fragmented_texts.push_back(text_item);
                                return;
                            }
                            text_item->pos() = sz;
                        }
                    }
                }
            }
        }
    }

    bool add_box = true;

    if(!m_line_boxes.empty())
    {
        if(m_line_boxes.back()->can_hold(item, src_el()->css().get_white_space(), item->get_type() == line_box_item::type_text_part ? m_trailing : 0_px))
        {
            add_box = false;
        }
    }
    if(add_box)
    {
        new_box(item, self_size, fmt_ctx);
    }

    if(!item->get_el()->src_el()->is_inline())
    {
        if(m_line_boxes.size() == 1)
        {
            if(collapse_top_margin())
            {
                pixel_t shift = item->get_el()->margin_top();
                if(shift >= 0_px)
                {
                    m_line_boxes.back()->y_shift(-shift);
                }
            }
        } else
        {
            pixel_t shift = 0_px;

            pixel_t prev_margin = m_line_boxes[m_line_boxes.size() - 2]->bottom_margin();

            if(prev_margin > item->get_el()->margin_top())
            {
                shift = item->get_el()->margin_top();
            } else
            {
                shift = prev_margin;
            }
            if(shift >= 0_px)
            {
                m_line_boxes.back()->y_shift(-shift);
            }
        }
    }

    m_line_boxes.back()->add_item(std::move(item));
}

void litehtml::render_item_inline_context::apply_vertical_align()
{
    if(!m_line_boxes.empty())
    {
        pixel_t add = 0_px;

        pixel_t content_height = m_line_boxes.back()->bottom() - m_va_applied;

        if(m_pos.height > content_height)
        {
            switch(src_el()->css().get_vertical_align())
            {
            case va_middle:
                add = (m_pos.height - content_height) / 2;
                break;
            case va_bottom:
                add = m_pos.height - content_height;
                break;
            default:
                add = 0_px;
                break;
            }
        }

        if(add == m_va_applied)
            return;
        pixel_t shift = add;
        if(m_va_applied != 0_px)
        {
            if(m_last_layout.valid)
            {
                const position placed = m_pos;
                m_layout_dirty        = true;
                render(m_last_x, m_last_y, m_last_layout.cb, nullptr, m_last_layout.second_pass);
                m_pos = placed;
            } else
                shift = add - m_va_applied;
        }
        if(shift != 0_px)
        {
            for(auto& box : m_line_boxes)
            {
                box->y_shift(shift);
            }
        }
        m_va_applied = add;
        for(const auto& text : m_fragmented_texts)
            text->update_fragment_bounds();
    }
}

litehtml::pixel_t litehtml::render_item_inline_context::control_baseline_inset() const
{
    if(src_el()->tag() != _input_)
    {
        return 0_px;
    }
    const char* type = src_el()->get_attr("type");
    return type && (!t_strcasecmp(type, "checkbox") || !t_strcasecmp(type, "radio")) ? 2_px : 0_px;
}

litehtml::pixel_t litehtml::render_item_inline_context::get_first_baseline()
{
    pixel_t bl;
    if(!m_line_boxes.empty())
    {
        const auto& line = m_line_boxes.front();
        bl               = line->bottom() - line->baseline() + content_offset_top();
    } else
    {
        bl = height() - margin_bottom() - control_baseline_inset();
    }
    return bl;
}

litehtml::pixel_t litehtml::render_item_inline_context::get_last_baseline()
{
    pixel_t bl;
    if(!m_line_boxes.empty())
    {
        const auto& line = m_line_boxes.back();
        bl               = line->bottom() - line->baseline() + content_offset_top();
    } else
    {
        bl = src_el()->tag() == _input_ ? height() - margin_bottom() - control_baseline_inset() : height();
    }
    return bl;
}
