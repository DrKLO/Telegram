#include "flex_line.h"
#include "flex_item.h"
#include "types.h"

void litehtml::flex_line::distribute_free_space(pixel_t container_main_size)
{
    // 1 Determine the used flex factor. Sum the outer hypothetical main sizes of all items on the line.
    // If the sum is less than the flex container’s inner main size, use the flex grow factor for the
    // rest of this algorithm; otherwise, use the flex shrink factor.

    if(main_size < container_main_size)
    {
        distribute_free_space_grow(container_main_size);
    } else
    {
        distribute_free_space_shrink(container_main_size);
    }
}

void litehtml::flex_line::distribute_free_space_grow(pixel_t container_main_size)
{
    pixel_t initial_free_space = container_main_size;

    bool all_inflexible = true;

    for(auto& item : items)
    {
        // 2. Size inflexible items. Freeze, setting its target main size to its hypothetical main size
        // any item that has a flex factor of zero
        // if using the flex grow factor: any item that has a flex base size greater than its hypothetical main size

        // 3. Calculate initial free space. Sum the outer sizes of all items on the line, and subtract this
        // from the flex container’s inner main size. For frozen items, use their outer target main size; for
        // other items, use their outer flex base size.

        if(item->grow == 0 || item->base_size > item->main_size)
        {
            item->frozen        = true;
            item->clamp_state   = flex_clamp_state_inflexible;
            initial_free_space -= item->main_size;
        } else
        {
            initial_free_space -= item->base_size;
            all_inflexible      = false;
        }
    }

    // 4. Loop:

    // 4.a Check for flexible items. If all the flex items on the line are frozen, free space has been
    // distributed; exit this loop.

    if(all_inflexible)
    {
        return;
    }

    while(true)
    {
        // 4.b Calculate the remaining free space as for initial free space, above. If the sum of the
        // unfrozen flex items’ flex factors is less than one, multiply the initial free space by this sum.
        // If the magnitude of this value is less than the magnitude of the remaining free space, use
        // this as the remaining free space.

        int     sum_flex_grow_factor = 0;
        pixel_t remaining_free_space = container_main_size;

        for(auto& item : items)
        {
            if(item->frozen)
            {
                remaining_free_space -= item->main_size;
            } else
            {
                remaining_free_space -= item->base_size;
                sum_flex_grow_factor += item->grow;
            }
        }

        if(sum_flex_grow_factor < 1000)
        {
            pixel_t adjusted_free_space = initial_free_space * pixel_t(sum_flex_grow_factor) / 1000_px;
            if(adjusted_free_space < remaining_free_space)
            {
                remaining_free_space = adjusted_free_space;
            }
        }

        // 4.c Distribute free space proportional to the flex factors.

        // If the remaining free space is zero
        // Do nothing.

        if(remaining_free_space != 0_px)
        {
            for(auto& item : items)
            {
                if(!item->frozen)
                {
                    // If using the flex grow factor
                    // Find the ratio of the item’s flex grow factor to the sum of the flex grow factors of all
                    // unfrozen items on the line. Set the item’s target main size to its flex base size plus a
                    // fraction of the remaining free space proportional to the ratio.

                    item->main_size =
                        item->base_size + remaining_free_space * pixel_t(item->grow) / pixel_t(sum_flex_grow_factor);
                }
            }
        }

        if(fix_min_max_violations())
        {
            break;
        }
    }
}

void litehtml::flex_line::distribute_free_space_shrink(pixel_t container_main_size)
{
    pixel_t initial_free_space = container_main_size;

    bool all_inflexible = true;

    for(auto& item : items)
    {
        // 2. Size inflexible items. Freeze, setting its target main size to its hypothetical main size
        // any item that has a flex factor of zero
        // if using the flex shrink factor: any item that has a flex base size smaller than its hypothetical main size

        // 3. Calculate initial free space. Sum the outer sizes of all items on the line, and subtract this
        // from the flex container’s inner main size. For frozen items, use their outer target main size; for
        // other items, use their outer flex base size.

        if(item->shrink == 0 || item->base_size < item->main_size)
        {
            item->frozen        = true;
            item->clamp_state   = flex_clamp_state_inflexible;
            initial_free_space -= item->main_size;
        } else
        {
            initial_free_space -= item->base_size;
            all_inflexible      = false;
        }
    }

    // 4. Loop:

    // 4.a Check for flexible items. If all the flex items on the line are frozen, free space has been
    // distributed; exit this loop.

    if(all_inflexible)
    {
        return;
    }

    while(true)
    {
        // 4.b Calculate the remaining free space as for initial free space, above. If the sum of the
        // unfrozen flex items’ flex factors is less than one, multiply the initial free space by this sum.
        // If the magnitude of this value is less than the magnitude of the remaining free space, use
        // this as the remaining free space.

        int     sum_flex_shrink_factor        = 0;
        pixel_t sum_scaled_flex_shrink_factor = 0_px;
        pixel_t remaining_free_space          = container_main_size;

        for(auto& item : items)
        {
            if(item->frozen)
            {
                remaining_free_space -= item->main_size;
            } else
            {
                remaining_free_space          -= item->base_size;
                sum_flex_shrink_factor        += item->shrink;
                sum_scaled_flex_shrink_factor += item->scaled_flex_shrink_factor;
            }
        }

        if(sum_flex_shrink_factor < 1000)
        {
            pixel_t adjusted_free_space = initial_free_space * pixel_t(sum_flex_shrink_factor) / 1000_px;
            if(adjusted_free_space > remaining_free_space)
            {
                remaining_free_space = adjusted_free_space;
            }
        }

        // 4.c Distribute free space proportional to the flex factors.

        // If the remaining free space is zero
        // Do nothing.

        if(remaining_free_space != 0_px)
        {
            for(auto& item : items)
            {
                if(!item->frozen)
                {
                    // If using the flex shrink factor
                    // For every unfrozen item on the line, multiply its flex shrink factor by its inner flex base
                    // size, and note this as its scaled flex shrink factor. Find the ratio of the item’s scaled
                    // flex shrink factor to the sum of the scaled flex shrink factors of all unfrozen items on
                    // the line. Set the item’s target main size to its flex base size minus a fraction of the
                    // absolute value of the remaining free space proportional to the ratio.

                    item->main_size = item->base_size + remaining_free_space * item->scaled_flex_shrink_factor /
                                                            sum_scaled_flex_shrink_factor;
                }
            }
        }

        if(fix_min_max_violations())
        {
            break;
        }
    }
}

bool litehtml::flex_line::fix_min_max_violations()
{
    // 4.d  Fix min/max violations. Clamp each non-frozen item’s target main size by its used min
    // and max main sizes and floor its content-box size at zero. If the item’s target main size was
    // made smaller by this, it’s a max violation. If the item’s target main size was made larger
    // by this, it’s a min violation.

    pixel_t total_violation = 0_px;

    for(auto& item : items)
    {
        if(!item->frozen)
        {
            if(item->main_size < item->min_size)
            {
                total_violation   += item->min_size - item->main_size;
                item->main_size    = item->min_size;
                item->clamp_state  = flex_clamp_state_min_violation;
            } else if(!item->max_size.is_default() && item->main_size > item->max_size)
            {
                total_violation   += pixel_t(item->max_size) - item->main_size;
                item->main_size    = item->max_size;
                item->clamp_state  = flex_clamp_state_max_violation;
            }
        }
    }

    // 4.e Freeze over-flexed items. The total violation is the sum of the adjustments from the
    // previous step ∑(clamped size - unclamped size). If the total violation is:
    // Zero: Freeze all items.
    // Positive: Freeze all the items with min violations.
    // Negative: Freeze all the items with max violations.

    if(total_violation == 0_px)
    {
        return true;
    }

    bool all_frozen = true;

    flex_clamp_state state_to_freeze =
        total_violation > 0_px ? flex_clamp_state_min_violation : flex_clamp_state_max_violation;

    for(auto& item : items)
    {
        if(!item->frozen)
        {
            if(item->clamp_state == state_to_freeze)
            {
                item->frozen = true;
            } else
            {
                all_frozen        = false;
                item->clamp_state = flex_clamp_state_unclamped;
            }
        }
    }

    return all_frozen;
}

bool litehtml::flex_line::distribute_main_auto_margins(pixel_t free_main_size)
{
    if(free_main_size > 0_px && (num_auto_margin_main_start || num_auto_margin_main_end))
    {
        pixel_t add = free_main_size / static_cast<int>(items.size() * 2);
        for(auto& item : items)
        {
            if(!item->auto_margin_main_start.is_default())
            {
                item->auto_margin_main_start  = add;
                item->main_size              += add;
                main_size                    += add;
                free_main_size               -= add;
            }
            if(!item->auto_margin_main_end.is_default())
            {
                item->auto_margin_main_end  = add;
                item->main_size            += add;
                main_size                  += add;
                free_main_size             -= add;
            }
        }

        pixel_t ditribute_step = 1_px;
        while(free_main_size > 0_px)
        {
            for(auto& item : items)
            {
                if(!item->auto_margin_main_start.is_default())
                {
                    item->auto_margin_main_start  = pixel_t(item->auto_margin_main_start) + ditribute_step;
                    free_main_size               -= ditribute_step;
                    if(free_main_size < ditribute_step)
                    {
                        break;
                    }
                }
                if(!item->auto_margin_main_end.is_default())
                {
                    item->auto_margin_main_end  = pixel_t(item->auto_margin_main_end) + ditribute_step;
                    free_main_size             -= ditribute_step;
                    if(free_main_size < ditribute_step)
                    {
                        break;
                    }
                }
            }
        }
        return true;
    }
    return false;
}

void litehtml::flex_line::init(pixel_t container_main_size, bool fit_container, bool is_row_direction,
                               const litehtml::containing_block_context& self_size,
                               litehtml::formatting_context*             fmt_ctx)
{
    if(!fit_container)
    {
        distribute_free_space(container_main_size);
    }

    cross_size = 0;
    main_size  = 0;
    first_baseline.set(0_px, baseline::baseline_type_none);
    last_baseline.set(0_px, baseline::baseline_type_none);

    if(is_row_direction)
    {
        def_value<pixel_t> first_baseline_top    = 0_px;
        def_value<pixel_t> first_baseline_bottom = 0_px;
        def_value<pixel_t> last_baseline_top     = 0_px;
        def_value<pixel_t> last_baseline_bottom  = 0_px;
        pixel_t            non_baseline_height   = 0_px;

        // Calculate maximum cross size
        def_value<pixel_t> max_cross_size(0_px);
        if(self_size.height.type != containing_block_context::cbc_value_type_auto)
        {
            max_cross_size = self_size.height;
        }
        if(self_size.max_height.type != containing_block_context::cbc_value_type_none)
        {
            if(max_cross_size.is_default())
            {
                max_cross_size = self_size.max_height;
            } else
            {
                max_cross_size = std::max(pixel_t(max_cross_size), pixel_t(self_size.max_height));
            }
        }

        /// Render items into new size
        /// Find line cross_size
        /// Find line first/last baseline
        for(auto& item : items)
        {
            item->el->render(0_px, 0_px,
                             self_size.new_width(item->main_size - item->el->render_offset_width(),
                                                 containing_block_context::size_mode_exact_width),
                             fmt_ctx, false);

            if((item->align & 0xFF) == flex_align_items_baseline)
            {
                if(item->align & flex_align_items_last)
                {
                    last_baseline.type(reverse_cross ? baseline::baseline_type_top : baseline::baseline_type_bottom);

                    pixel_t top    = -item->el->get_last_baseline();
                    pixel_t bottom = top + item->el->height();

                    if(last_baseline_top.is_default())
                    {
                        last_baseline_top = top;
                    } else
                    {
                        last_baseline_top = std::min(pixel_t(last_baseline_top), top);
                    }

                    if(last_baseline_bottom.is_default())
                    {
                        last_baseline_bottom = bottom;
                    } else
                    {
                        last_baseline_bottom = std::max(pixel_t(last_baseline_bottom), bottom);
                    }
                } else
                {
                    first_baseline.type(reverse_cross ? baseline::baseline_type_bottom : baseline::baseline_type_top);
                    pixel_t top    = -item->el->get_first_baseline();
                    pixel_t bottom = top + item->el->height();

                    if(first_baseline_top.is_default())
                    {
                        first_baseline_top = top;
                    } else
                    {
                        first_baseline_top = std::min(pixel_t(first_baseline_top), top);
                    }

                    if(first_baseline_bottom.is_default())
                    {
                        first_baseline_bottom = bottom;
                    } else
                    {
                        first_baseline_bottom = std::max(pixel_t(first_baseline_bottom), bottom);
                    }
                }
            } else
            {
                non_baseline_height = std::max(non_baseline_height, item->el->height());
            }
            main_size += item->el->width();
        }

        cross_size = std::max(first_baseline_bottom.value() - first_baseline_top.value(),
                              last_baseline_bottom.value() - last_baseline_top.value());
        cross_size = std::max(cross_size, non_baseline_height);
        if(!max_cross_size.is_default() && cross_size > max_cross_size)
        {
            cross_size = max_cross_size;
        }

        first_baseline.calc(first_baseline_top, first_baseline_bottom);
        last_baseline.calc(last_baseline_top, last_baseline_bottom);
    } else
    {
        // Calculate maximum cross size
        def_value<pixel_t> max_cross_size(0_px);
        if(self_size.width.type != containing_block_context::cbc_value_type_auto)
        {
            max_cross_size = self_size.width;
        }
        if(self_size.max_width.type != containing_block_context::cbc_value_type_none)
        {
            if(max_cross_size.is_default())
            {
                max_cross_size = self_size.max_width;
            } else
            {
                max_cross_size = std::max(pixel_t(max_cross_size), pixel_t(self_size.max_width));
            }
        }

        for(auto& item : items)
        {
            pixel_t el_ret_width = item->el->render(0_px, 0_px, self_size, fmt_ctx, false).natural_width;
            item->el->render(0_px, 0_px,
                             self_size.new_width_height(el_ret_width - item->el->content_offset_width(),
                                                        item->main_size - item->el->content_offset_height(),
                                                        containing_block_context::size_mode_exact_width |
                                                            containing_block_context::size_mode_exact_height),
                             fmt_ctx, false);
            main_size  += item->el->height();
            cross_size  = std::max(cross_size, item->el->width());
        }
        if(!max_cross_size.is_default() && cross_size > max_cross_size)
        {
            cross_size = max_cross_size;
        }
    }
}

litehtml::pixel_t litehtml::flex_line::calculate_items_position(pixel_t                         container_main_size,
                                                                flex_justify_content            justify_content,
                                                                bool                            is_row_direction,
                                                                const containing_block_context& self_size,
                                                                formatting_context*             fmt_ctx)
{
    /// Distribute main axis free space for auto-margins
    pixel_t free_main_size = container_main_size - main_size;
    distribute_main_auto_margins(free_main_size);
    free_main_size = container_main_size - main_size;

    /// Fix justify-content property
    switch(justify_content)
    {
    case flex_justify_content_left:
    case flex_justify_content_right:
        if(!is_row_direction)
        {
            justify_content = flex_justify_content_start;
        }
        break;
    case flex_justify_content_space_between:
        // If the leftover free-space is negative or there is only a single flex item on the line, this
        // value is identical to flex-start.
        if(items.size() == 1 || free_main_size < 0_px)
        {
            justify_content = flex_justify_content_flex_start;
        }
        break;
    case flex_justify_content_space_around:
    case flex_justify_content_space_evenly:
        // If the leftover free-space is negative or there is only a single flex item on the line, this
        // value is identical to center
        if(items.size() == 1 || free_main_size < 0_px)
        {
            justify_content = flex_justify_content_center;
        }
        break;
    default:
        break;
    }

    /// Distribute free main size using justify-content property
    pixel_t main_pos        = 0_px;
    pixel_t add_before_item = 0_px;
    pixel_t add_after_item  = 0_px;
    pixel_t item_remainder  = 0_px;

    /// find initial main position and spaces between items
    switch(justify_content)
    {

    case flex_justify_content_right:
        main_pos = free_main_size;
        break;
    case flex_justify_content_left:
    case flex_justify_content_start:
        main_pos = 0_px;
        break;
    case flex_justify_content_end:
        main_pos = free_main_size;
        break;
    case flex_justify_content_flex_end:
        if(!reverse_main)
        {
            main_pos = free_main_size;
        }
        break;
    case flex_justify_content_center:
        main_pos = free_main_size / 2_px;
        break;
    case flex_justify_content_space_between:
        add_after_item = free_main_size / static_cast<int>(items.size() - 1);
        item_remainder = free_main_size - (add_after_item * static_cast<int>(items.size() - 1));
        break;
    case flex_justify_content_space_around:
        add_after_item = add_before_item = free_main_size / static_cast<int>(items.size() * 2);
        item_remainder                   = free_main_size - (add_after_item * static_cast<int>(items.size() * 2));
        break;
    case flex_justify_content_space_evenly:
        add_before_item = free_main_size / static_cast<int>(items.size() + 1);
        item_remainder  = free_main_size - add_before_item * static_cast<int>(items.size() + 1);
        break;
    default:
        if(reverse_main)
        {
            main_pos = free_main_size;
        }
        break;
    }

    /// Place all items in main and cross positions
    pixel_t height = 0_px;

    pixel_t distribute_step = 1_px;
    for(auto& item : items)
    {
        main_pos += add_before_item;
        if(add_before_item > 0_px && item_remainder > 0_px)
        {
            main_pos       += distribute_step;
            item_remainder -= distribute_step;
        }
        item->place(*this, main_pos, self_size, fmt_ctx);
        main_pos += item->get_el_main_size() + add_after_item;
        if(add_after_item > 0_px && item_remainder > 0_px)
        {
            main_pos       += distribute_step;
            item_remainder -= distribute_step;
        }
        height = std::max(height, item->el->bottom());
    }
    return height;
}
