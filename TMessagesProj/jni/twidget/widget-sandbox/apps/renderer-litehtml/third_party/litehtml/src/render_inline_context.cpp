#include "render_inline_context.h"
#include "document.h"
#include "iterators.h"
#include "types.h"

litehtml::rendered_width litehtml::render_item_inline_context::_render_content(
    pixel_t /*x*/, pixel_t /*y*/, bool /*second_pass*/, const containing_block_context& self_size,
    formatting_context* fmt_ctx)
{
    m_line_boxes.clear();
    m_rendered_width.reset();

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

    inlines_iter.process(shared_from_this(), [&](const std::shared_ptr<render_item>& el, iterator_item_type item_type) {
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
                place_inline(std::make_unique<lbi_start>(el), self_size, fmt_ctx);
            }
            break;

        case iterator_item_type_end_parent:
            {
                place_inline(std::make_unique<lbi_end>(el), self_size, fmt_ctx);
            }
            break;
        }
    });

    finish_last_box(true, self_size);

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
            m_rendered_width.merge(m_line_boxes.back()->get_rendered_width());
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
                                                         css().get_font_metrics(), css().get_text_align()));

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
            litehtml::size sz;
            item->get_el()->src_el()->get_content_size(sz, self_size.render_width);
            item->get_el()->pos() = sz;
            item->set_rendered_min_width(sz.width);
        }
    }

    bool add_box = true;

    if(!m_line_boxes.empty())
    {
        if(m_line_boxes.back()->can_hold(item, src_el()->css().get_white_space()))
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

        pixel_t content_height = m_line_boxes.back()->bottom();

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

        if(add != 0_px)
        {
            for(auto& box : m_line_boxes)
            {
                box->y_shift(add);
            }
        }
    }
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
        bl = height() - margin_bottom();
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
        bl = height();
    }
    return bl;
}
