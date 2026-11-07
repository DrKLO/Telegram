#include "render_block_context.h"

#include "document.h"
#include "types.h"
#include <algorithm>

litehtml::rendered_width litehtml::render_item_block_context::_render_content(pixel_t /*x*/, pixel_t /*y*/,
                                                                              bool second_pass,
                                                                              const containing_block_context& self_size,
                                                                              formatting_context*             fmt_ctx)
{
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
                pixel_t min_rendered_width = el->render(0_px, child_top, self_size, fmt_ctx).natural_width;
                if(min_rendered_width < el->width() && el->src_el()->css().get_width().is_predefined())
                {
                    el->render(0_px, child_top, self_size.new_width(min_rendered_width), fmt_ctx);
                }
            } else
            {
                child_top           = fmt_ctx->get_cleared_top(el, child_top);
                pixel_t child_x     = 0_px;
                pixel_t child_width = self_size.render_width;
                pixel_t top_margin  = m_margins.top;

                el->calc_outlines(self_size.width);

                // Collapse top margin
                if(is_first && collapse_top_margin())
                {
                    if(el->get_margins().top > 0_px)
                    {
                        child_top -= el->get_margins().top;
                        if(el->get_margins().top > get_margins().top)
                        {
                            top_margin = el->get_margins().top;
                        }
                    }
                } else
                {
                    if(el->get_margins().top > 0_px)
                    {
                        if(last_margin > el->get_margins().top)
                        {
                            child_top -= el->get_margins().top;
                        } else
                        {
                            child_top -= last_margin;
                        }
                    }
                }

                auto rw = el->render(child_x, child_top, self_size.new_width(child_width), fmt_ctx);
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

litehtml::pixel_t litehtml::render_item_block_context::get_first_baseline()
{
    if(m_children.empty())
    {
        return height() - margin_bottom();
    }
    const auto& item = m_children.front();
    return content_offset_top() + item->top() + item->get_first_baseline();
}

litehtml::pixel_t litehtml::render_item_block_context::get_last_baseline()
{
    if(m_children.empty())
    {
        return height() - margin_bottom();
    }
    const auto& item = m_children.back();
    return content_offset_top() + item->top() + item->get_last_baseline();
}
