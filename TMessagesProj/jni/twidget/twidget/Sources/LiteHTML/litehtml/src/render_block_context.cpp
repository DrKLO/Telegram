#include "render_block_context.h"

#include "document.h"
#include "types.h"
#include <algorithm>

litehtml::rendered_width litehtml::render_item_block_context::_render_content(pixel_t /*x*/, pixel_t /*y*/,
                                                                              bool second_pass,
                                                                              const containing_block_context& self_size,
                                                                              formatting_context*             fmt_ctx)
{
    m_va_applied = 0_px;
    std::shared_ptr<render_item> last_margin_el;

    rendered_width ret_width;
    pixel_t        child_top   = 0_px;
    pixel_t        last_margin = 0_px;
    bool           is_first    = true;
    for(const auto& el : m_children)
    {
        // we don't need to process absolute and fixed positioned element on the second pass
        if(second_pass)
        {
            auto el_position = el->src_el()->css().get_position();
            if((el_position == element_position_absolute || el_position == element_position_fixed))
            {
                continue;
            }
        }

        if(el->src_el()->css().get_float() != float_none)
        {
            auto rw = place_float(el, child_top, self_size, fmt_ctx);
            ret_width.merge(rw);
        } else if(el->src_el()->css().get_display() != display_none)
        {
            if(el->src_el()->css().get_position() == element_position_absolute ||
               el->src_el()->css().get_position() == element_position_fixed)
            {
                el->render_out_of_flow(child_top, self_size, fmt_ctx);
            } else
            {
                child_top           = fmt_ctx->get_cleared_top(el, child_top);
                pixel_t child_x     = 0_px;
                pixel_t child_width = self_size.render_width;
                pixel_t top_margin  = m_margins.top;

                el->calc_outlines(self_size.width);

                const pixel_t base_child_top = child_top;
                const pixel_t base_top_margin = top_margin;
                auto collapse = [&](pixel_t margin) {
                    child_top  = base_child_top;
                    top_margin = base_top_margin;
                    if(is_first && collapse_top_margin())
                    {
                        if(margin > 0_px)
                        {
                            child_top -= margin;
                            if(margin > get_margins().top)
                            {
                                top_margin = margin;
                            }
                        }
                    } else if(margin > 0_px)
                    {
                        child_top -= std::min(last_margin, margin);
                    }
                };
                const pixel_t css_top = el->get_margins().top;
                collapse(css_top);

                auto rw = el->render(child_x, child_top, self_size.new_width(child_width), fmt_ctx);
                if(el->get_margins().top != css_top)
                {
                    const pixel_t before = child_top;
                    collapse(el->get_margins().top);
                    if(child_top != before)
                    {
                        rw = el->render(child_x, child_top, self_size.new_width(child_width), fmt_ctx);
                    }
                }
                // Render table with "width: auto" into returned width
                if(el->css().get_display() == display_table && rw.natural_width < child_width &&
                   el->css().get_width().is_predefined())
                {
                    el->render(child_x, child_top, self_size.new_width(rw.natural_width), fmt_ctx);
                }

                // Check if we need to move block to the next line
                if(el->src_el()->is_replaced() || el->src_el()->is_block_formatting_context() ||
                   el->src_el()->css().get_display() == display_table)
                {
                    formatting_context::el_position el_pos;
                    el_pos.el_pos           = el->pos();
                    el_pos.el_pos          += el->get_margins();
                    el_pos.el_pos          += el->get_paddings();
                    el_pos.el_pos          += el->get_borders();
                    el_pos.el_pos.width     = rw.min_width;
                    el_pos.container_width  = self_size.render_width;
                    el_pos.el_margins       = el->get_margins();
                    auto new_pos            = fmt_ctx->place_to_left(el_pos);
                    if(new_pos.found)
                    {
                        child_top   = new_pos.top;
                        el->pos().x = new_pos.left + el->content_offset_left();
                        el->pos().y = child_top + el->content_offset_top();
                        //  Rollback top margin collapse if the block was moved to the new line
                        if(is_first && new_pos.new_line && collapse_top_margin())
                        {
                            top_margin = m_margins.top;
                        }
                    }
                    if(new_pos.width != self_size.render_width && el->css().get_width().is_predefined())
                    {
                        rw = el->render(new_pos.left, new_pos.top, self_size.new_width(new_pos.width), fmt_ctx);
                    }
                    rw.natural_width = self_size.render_width.value - new_pos.width + rw.natural_width;
                }

                pixel_t auto_margin = el->calc_auto_margins(child_width);
                if(auto_margin != 0_px)
                {
                    el->pos().x += auto_margin;
                } else if(css().get_direction() == direction_rtl && !el->css().get_margins().left.is_predefined() &&
                          !el->css().get_margins().right.is_predefined())
                {
                    // Right to left, a block narrower than its container keeps its right margin and sits on the right.
                    el->pos().x += pixel_t(self_size.render_width) - el->right();
                }
                ret_width.merge(rw);
                m_margins.top   = top_margin;
                child_top      += el->height();
                last_margin     = el->get_margins().bottom;
                last_margin_el  = el;
                is_first        = false;

                if(el->src_el()->css().get_position() == element_position_relative)
                {
                    el->apply_relative_shift(self_size);
                }
            }
        }
    }

    if(self_size.height.type != containing_block_context::cbc_value_type_auto && self_size.height.value > 0_px)
    {
        m_pos.height = self_size.height;
    } else
    {
        m_pos.height = child_top;
        if(collapse_bottom_margin())
        {
            m_pos.height     -= last_margin;
            m_margins.bottom  = std::max(m_margins.bottom, last_margin);
            if(last_margin_el)
            {
                last_margin_el->get_margins().bottom = 0_px;
            }
        }
    }

    return ret_width;
}

static bool is_in_flow(const std::shared_ptr<litehtml::render_item>& item)
{
    const auto& css = item->src_el()->css();
    return css.get_display() != litehtml::display_none && css.get_float() == litehtml::float_none &&
           css.get_position() != litehtml::element_position_absolute &&
           css.get_position() != litehtml::element_position_fixed;
}

litehtml::pixel_t litehtml::render_item_block_context::get_first_baseline()
{
    for(const auto& item : m_children)
    {
        if(is_in_flow(item))
        {
            return content_offset_top() + item->top() + item->get_first_baseline();
        }
    }
    return height() - margin_bottom();
}

litehtml::pixel_t litehtml::render_item_block_context::get_last_baseline()
{
    for(auto it = m_children.rbegin(); it != m_children.rend(); ++it)
    {
        if(is_in_flow(*it))
        {
            return content_offset_top() + (*it)->top() + (*it)->get_last_baseline();
        }
    }
    return height() - margin_bottom();
}

void litehtml::render_item_block_context::apply_vertical_align()
{
    const vertical_align va = src_el()->css().get_vertical_align();
    bool                 any = false;
    pixel_t              content_bottom = 0_px;
    for(const auto& el : m_children)
    {
        if(el->src_el()->css().get_display() == display_none ||
           el->src_el()->css().get_position() == element_position_absolute ||
           el->src_el()->css().get_position() == element_position_fixed)
        {
            continue;
        }
        content_bottom = any ? std::max(content_bottom, el->bottom()) : el->bottom();
        any            = true;
    }
    if(!any)
    {
        return;
    }
    content_bottom -= m_va_applied;
    pixel_t add = 0_px;
    if(m_pos.height > content_bottom)
    {
        if(va == va_middle)
        {
            add = (m_pos.height - content_bottom) / 2_px;
        } else if(va == va_bottom)
        {
            add = m_pos.height - content_bottom;
        }
    }
    const pixel_t shift = add - m_va_applied;
    if(shift != 0_px)
    {
        for(const auto& el : m_children)
        {
            if(el->src_el()->css().get_position() != element_position_absolute &&
               el->src_el()->css().get_position() != element_position_fixed)
            {
                el->pos().y += shift;
            }
        }
    }
    m_va_applied = add;
}
