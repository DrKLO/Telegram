#include "types.h"
#include "render_flex.h"
#include "html_tag.h"

litehtml::rendered_width litehtml::render_item_flex::_render_content(pixel_t x, pixel_t y, bool /*second_pass*/,
                                                                     const containing_block_context& self_size,
                                                                     formatting_context*             fmt_ctx)
{
    bool    is_row_direction    = true;
    bool    reverse             = false;
    pixel_t container_main_size = self_size.render_width;

    switch(css().get_flex_direction())
    {
    case flex_direction_column:
        is_row_direction = false;
        reverse          = false;
        break;
    case flex_direction_column_reverse:
        is_row_direction = false;
        reverse          = true;
        break;
    case flex_direction_row:
        is_row_direction = true;
        reverse          = false;
        break;
    case flex_direction_row_reverse:
        is_row_direction = true;
        reverse          = true;
        break;
    }

    bool single_line   = css().get_flex_wrap() == flex_wrap_nowrap;
    bool fit_container = false;

    if(!is_row_direction)
    {
        if(self_size.height.type != containing_block_context::cbc_value_type_auto)
        {
            container_main_size = self_size.height.value - box_sizing_height();
        } else
        {
            // Direction columns, height is auto - always in single line
            container_main_size = 0;
            single_line         = true;
            fit_container       = true;
        }
        if(self_size.min_height.type != containing_block_context::cbc_value_type_auto &&
           self_size.min_height.value > container_main_size)
        {
            container_main_size = self_size.min_height;
        }
        if(self_size.max_height.type != containing_block_context::cbc_value_type_auto &&
           self_size.max_height.value > container_main_size)
        {
            container_main_size = self_size.max_height;
            single_line         = false;
        }
    }

    /////////////////////////////////////////////////////////////////
    /// Split flex items to lines
    /////////////////////////////////////////////////////////////////
    m_lines = get_lines(self_size, fmt_ctx, is_row_direction, container_main_size, single_line);

    pixel_t sum_cross_size = 0_px;
    pixel_t sum_main_size  = 0_px;
    pixel_t ret_width      = 0_px;

    /////////////////////////////////////////////////////////////////
    /// Resolving Flexible Lengths
    /// REF: https://www.w3.org/TR/css-flexbox-1/#resolve-flexible-lengths
    /////////////////////////////////////////////////////////////////
    for(auto& ln : m_lines)
    {
        if(is_row_direction)
        {
            ret_width += ln.base_size;
        }
        ln.init(container_main_size, fit_container, is_row_direction, self_size, fmt_ctx);
        sum_cross_size += ln.cross_size;
        sum_main_size   = std::max(sum_main_size, ln.main_size);
        if(reverse)
        {
            ln.items.reverse();
        }
    }

    pixel_t free_cross_size = 0_px;
    bool    is_wrap_reverse = css().get_flex_wrap() == flex_wrap_wrap_reverse;
    if(container_main_size == 0_px)
    {
        container_main_size = sum_main_size;
    }

    /////////////////////////////////////////////////////////////////
    /// Calculate free cross size
    /////////////////////////////////////////////////////////////////
    if(is_row_direction)
    {
        if(self_size.height.type != containing_block_context::cbc_value_type_auto)
        {
            pixel_t height  = self_size.height.value - box_sizing_height();
            free_cross_size = height - sum_cross_size;
        }
    } else
    {
        free_cross_size = self_size.render_width.value - sum_cross_size;
        ret_width       = sum_cross_size;
    }

    /////////////////////////////////////////////////////////////////
    /// Fix align-content property
    /////////////////////////////////////////////////////////////////
    flex_align_content align_content = css().get_flex_align_content();
    if(align_content == flex_align_content_space_between)
    {
        // If the leftover free-space is negative or there is only a single flex line in the flex
        // container, this value is identical to flex-start.
        if(m_lines.size() == 1 || free_cross_size < 0_px)
        {
            align_content = flex_align_content_flex_start;
        }
    }
    if(align_content == flex_align_content_space_around)
    {
        // If the leftover free-space is negative or there is only a single flex line in the flex
        // container, this value is identical to flex-start.
        if(m_lines.size() == 1 || free_cross_size < 0_px)
        {
            align_content = flex_align_content_center;
        }
    }

    /////////////////////////////////////////////////////////////////
    /// Distribute free cross size for align-content: stretch
    /////////////////////////////////////////////////////////////////
    if(css().get_flex_align_content() == flex_align_content_stretch && free_cross_size > 0_px)
    {
        pixel_t add = free_cross_size / pixel_t(static_cast<int>(m_lines.size()));
        if(add > 0_px)
        {
            for(auto& ln : m_lines)
            {
                ln.cross_size   += add;
                free_cross_size -= add;
            }
        }
        if(!m_lines.empty())
        {
            while(free_cross_size > 0_px)
            {
                pixel_t distributeStep = 1_px;
                for(auto& ln : m_lines)
                {
                    ln.cross_size   += distributeStep;
                    free_cross_size -= distributeStep;
                }
            }
        }
    }

    /// Reverse lines for flex-wrap: wrap-reverse
    if(css().get_flex_wrap() == flex_wrap_wrap_reverse)
    {
        m_lines.reverse();
    }

    /////////////////////////////////////////////////////////////////
    /// Align flex lines
    /////////////////////////////////////////////////////////////////
    pixel_t line_pos        = 0_px;
    pixel_t add_before_line = 0_px;
    pixel_t add_after_line  = 0_px;
    switch(align_content)
    {
    case flex_align_content_flex_start:
        if(is_wrap_reverse)
        {
            line_pos = free_cross_size;
        }
        break;
    case flex_align_content_flex_end:
        if(!is_wrap_reverse)
        {
            line_pos = free_cross_size;
        }
        break;
    case flex_align_content_end:
        line_pos = free_cross_size;
        break;
    case flex_align_content_center:
        line_pos = free_cross_size / 2_px;
        break;
    case flex_align_content_space_between:
        add_after_line = free_cross_size / pixel_t(static_cast<int>(m_lines.size() - 1));
        break;
    case flex_align_content_space_around:
        add_before_line = add_after_line = free_cross_size / pixel_t(static_cast<int>(m_lines.size() * 2));
        break;
    default:
        if(is_wrap_reverse)
        {
            line_pos = free_cross_size;
        }
        break;
    }
    for(auto& ln : m_lines)
    {
        line_pos       += add_before_line;
        ln.cross_start  = line_pos;
        line_pos       += ln.cross_size + add_after_line;
    }

    /// Fix justify-content property
    flex_justify_content justify_content = css().get_flex_justify_content();
    if((justify_content == flex_justify_content_right || justify_content == flex_justify_content_left) &&
       !is_row_direction)
    {
        justify_content = flex_justify_content_start;
    }

    /////////////////////////////////////////////////////////////////
    /// Align flex items in flex lines
    /////////////////////////////////////////////////////////////////
    for(auto& ln : m_lines)
    {
        pixel_t height =
            ln.calculate_items_position(container_main_size, justify_content, is_row_direction, self_size, fmt_ctx);
        m_pos.height = std::max(m_pos.height, height);
    }

    // calculate the final position
    m_pos.move_to(x, y);
    m_pos.x += content_offset_left();
    m_pos.y += content_offset_top();

    return {ret_width, ret_width};
}

std::list<litehtml::flex_line> litehtml::render_item_flex::get_lines(
    const litehtml::containing_block_context& self_size, litehtml::formatting_context* fmt_ctx, bool is_row_direction,
    pixel_t container_main_size, bool single_line)
{
    bool reverse_main;
    bool reverse_cross = css().get_flex_wrap() == flex_wrap_wrap_reverse;

    if(is_row_direction)
    {
        reverse_main = css().get_flex_direction() == flex_direction_row_reverse;
    } else
    {
        reverse_main = css().get_flex_direction() == flex_direction_column_reverse;
    }

    std::list<flex_line>                  lines;
    flex_line                             line(reverse_main, reverse_cross);
    std::list<std::shared_ptr<flex_item>> items;
    int                                   src_order     = 0;
    bool                                  sort_required = false;
    def_value<int>                        prev_order(0);

    for(auto& el : m_children)
    {
        std::shared_ptr<flex_item> item = nullptr;
        if(is_row_direction)
        {
            item = std::make_shared<flex_item_row_direction>(el);
        } else
        {
            item = std::make_shared<flex_item_column_direction>(el);
        }
        item->init(self_size, fmt_ctx, css().get_flex_align_items());
        item->src_order = src_order++;

        if(prev_order.is_default())
        {
            prev_order = item->order;
        } else if(!sort_required && item->order != prev_order)
        {
            sort_required = true;
        }

        items.emplace_back(item);
    }

    if(sort_required)
    {
        items.sort([](const std::shared_ptr<flex_item>& item1, const std::shared_ptr<flex_item>& item2) {
            if(item1->order < item2->order)
            {
                return true;
            }
            if(item1->order == item2->order)
            {
                return item1->src_order < item2->src_order;
            }
            return false;
        });
    }

    // Add flex items to lines
    for(auto& item : items)
    {
        if(!line.items.empty() && !single_line && line.main_size + item->main_size > container_main_size)
        {
            lines.emplace_back(line);
            line = flex_line(reverse_main, reverse_cross);
        }
        line.base_size += item->base_size;
        line.main_size += item->main_size;
        if(!item->auto_margin_main_start.is_default())
        {
            line.num_auto_margin_main_start++;
        }
        if(!item->auto_margin_main_end.is_default())
        {
            line.num_auto_margin_main_end++;
        }
        line.items.push_back(item);
    }
    // Add the last line to the lines list
    if(!line.items.empty())
    {
        lines.emplace_back(line);
    }
    return lines;
}

std::shared_ptr<litehtml::render_item> litehtml::render_item_flex::init()
{
    auto                 doc = src_el()->get_document();
    decltype(m_children) new_children;
    decltype(m_children) inlines;

    auto convert_inlines = [&]() {
        if(!inlines.empty())
        {
            // Find last not space
            auto not_space =
                std::find_if(inlines.rbegin(), inlines.rend(),
                             [&](const std::shared_ptr<render_item>& el) { return !el->src_el()->is_space(); });
            if(not_space != inlines.rend())
            {
                // Erase all spaces at the end
                inlines.erase((not_space.base()), inlines.end());
            }

            auto anon_el = std::make_shared<html_tag>(src_el());
            auto anon_ri = std::make_shared<render_item_block>(anon_el);
            for(const auto& inl : inlines)
            {
                anon_ri->add_child(inl);
            }
            anon_ri->parent(shared_from_this());

            new_children.push_back(anon_ri->init());
            inlines.clear();
        }
    };

    for(const auto& el : m_children)
    {
        if(el->src_el()->css().get_display() == display_inline_text)
        {
            if(!inlines.empty())
            {
                inlines.push_back(el);
            } else
            {
                if(!el->src_el()->is_white_space())
                {
                    inlines.push_back(el);
                }
            }
        } else
        {
            convert_inlines();
            if(el->src_el()->is_block_box())
            {
                // Add block boxes as is
                el->parent(shared_from_this());
                new_children.push_back(el->init());
            } else
            {
                // Wrap inlines with anonymous block box
                auto anon_el = std::make_shared<html_tag>(el->src_el());
                auto anon_ri = std::make_shared<render_item_block>(anon_el);
                anon_ri->add_child(el->init());
                anon_ri->parent(shared_from_this());
                new_children.push_back(anon_ri->init());
            }
        }
    }
    convert_inlines();
    children() = new_children;

    return shared_from_this();
}

litehtml::pixel_t litehtml::render_item_flex::get_first_baseline()
{
    if(css().get_flex_direction() == flex_direction_row || css().get_flex_direction() == flex_direction_row_reverse)
    {
        if(!m_lines.empty())
        {
            const auto& first_line = m_lines.front();
            if(first_line.first_baseline.type() != baseline::baseline_type_none)
            {
                return first_line.cross_start + first_line.first_baseline.get_offset_from_top(first_line.cross_size) +
                       content_offset_top();
            }
            if(first_line.last_baseline.type() != baseline::baseline_type_none)
            {
                return first_line.cross_start + first_line.last_baseline.get_offset_from_top(first_line.cross_size) +
                       content_offset_top();
            }
        }
    }
    if(!m_lines.empty())
    {
        if(!m_lines.front().items.empty())
        {
            return m_lines.front().items.front()->el->get_first_baseline() + content_offset_top();
        }
    }
    return height();
}

litehtml::pixel_t litehtml::render_item_flex::get_last_baseline()
{
    if(css().get_flex_direction() == flex_direction_row || css().get_flex_direction() == flex_direction_row_reverse)
    {
        if(!m_lines.empty())
        {
            const auto& first_line = m_lines.front();
            if(first_line.last_baseline.type() != baseline::baseline_type_none)
            {
                return first_line.cross_start + first_line.last_baseline.get_offset_from_top(first_line.cross_size) +
                       content_offset_top();
            }
            if(first_line.first_baseline.type() != baseline::baseline_type_none)
            {
                return first_line.cross_start + first_line.first_baseline.get_offset_from_top(first_line.cross_size) +
                       content_offset_top();
            }
        }
    }
    if(!m_lines.empty())
    {
        if(!m_lines.front().items.empty())
        {
            return m_lines.front().items.front()->el->get_last_baseline() + content_offset_top();
        }
    }
    return height();
}
