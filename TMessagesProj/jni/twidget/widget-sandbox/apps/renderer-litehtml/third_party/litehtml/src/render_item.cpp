#include "render_item.h"
#include "document.h"
#include "document_container.h"
#include "types.h"
#include <algorithm>
#include <typeinfo>

litehtml::render_item::render_item(std::shared_ptr<element> _src_el) :
    m_element(std::move(_src_el))
{
    document::ptr doc = src_el()->get_document();
    auto          fm  = css().get_font_metrics();

    m_margins.left   = doc->to_pixels(src_el()->css().get_margins().left, fm, 0_px);
    m_margins.right  = doc->to_pixels(src_el()->css().get_margins().right, fm, 0_px);
    m_margins.top    = doc->to_pixels(src_el()->css().get_margins().top, fm, 0_px);
    m_margins.bottom = doc->to_pixels(src_el()->css().get_margins().bottom, fm, 0_px);

    m_padding.left   = doc->to_pixels(src_el()->css().get_padding().left, fm, 0_px);
    m_padding.right  = doc->to_pixels(src_el()->css().get_padding().right, fm, 0_px);
    m_padding.top    = doc->to_pixels(src_el()->css().get_padding().top, fm, 0_px);
    m_padding.bottom = doc->to_pixels(src_el()->css().get_padding().bottom, fm, 0_px);

    m_borders.left   = doc->to_pixels(src_el()->css().get_borders().left.width, fm, 0_px);
    m_borders.right  = doc->to_pixels(src_el()->css().get_borders().right.width, fm, 0_px);
    m_borders.top    = doc->to_pixels(src_el()->css().get_borders().top.width, fm, 0_px);
    m_borders.bottom = doc->to_pixels(src_el()->css().get_borders().bottom.width, fm, 0_px);
}

litehtml::rendered_width litehtml::render_item::render(pixel_t x, pixel_t y,
                                                       const containing_block_context& containing_block_size,
                                                       formatting_context* fmt_ctx, bool second_pass)
{
    calc_outlines(containing_block_size.width);

    m_pos.clear();
    m_pos.move_to(x, y);

    pixel_t content_left = content_offset_left();
    pixel_t content_top  = content_offset_top();

    m_pos.x += content_left;
    m_pos.y += content_top;

    if(src_el()->is_block_formatting_context() || (fmt_ctx == nullptr))
    {
        formatting_context fmt;
        auto               ret = _render(x, y, containing_block_size, &fmt, second_pass);
        fmt.apply_relative_shift(containing_block_size);
        return ret;
    }

    fmt_ctx->push_position(x + content_left, y + content_top);
    auto ret = _render(x, y, containing_block_size, fmt_ctx, second_pass);
    fmt_ctx->pop_position(x + content_left, y + content_top);
    return ret;
}

void litehtml::render_item::calc_outlines(pixel_t parent_width)
{
    m_padding.left  = m_element->css().get_padding().left.calc_percent(parent_width);
    m_padding.right = m_element->css().get_padding().right.calc_percent(parent_width);

    m_borders.left  = m_element->css().get_borders().left.width.calc_percent(parent_width);
    m_borders.right = m_element->css().get_borders().right.width.calc_percent(parent_width);

    m_margins.left  = m_element->css().get_margins().left.calc_percent(parent_width);
    m_margins.right = m_element->css().get_margins().right.calc_percent(parent_width);

    m_margins.top    = m_element->css().get_margins().top.calc_percent(parent_width);
    m_margins.bottom = m_element->css().get_margins().bottom.calc_percent(parent_width);

    m_padding.top    = m_element->css().get_padding().top.calc_percent(parent_width);
    m_padding.bottom = m_element->css().get_padding().bottom.calc_percent(parent_width);
}

litehtml::pixel_t litehtml::render_item::calc_auto_margins(pixel_t parent_width)
{
    if((src_el()->css().get_display() == display_block || src_el()->css().get_display() == display_table) &&
       src_el()->css().get_position() != element_position_absolute && src_el()->css().get_float() == float_none)
    {
        if(src_el()->css().get_margins().left.is_predefined() && src_el()->css().get_margins().right.is_predefined())
        {
            pixel_t el_width = m_pos.width + m_borders.left + m_borders.right + m_padding.left + m_padding.right;
            if(el_width <= parent_width)
            {
                m_margins.left  = (parent_width - el_width) / 2_px;
                m_margins.right = (parent_width - el_width) - m_margins.left;
            } else
            {
                m_margins.left  = 0;
                m_margins.right = 0;
            }
            return m_margins.left;
        }
        if(src_el()->css().get_margins().left.is_predefined() && !src_el()->css().get_margins().right.is_predefined())
        {
            pixel_t el_width =
                m_pos.width + m_borders.left + m_borders.right + m_padding.left + m_padding.right + m_margins.right;
            m_margins.left = parent_width - el_width;
            m_margins.left = std::max<pixel_t>(m_margins.left, 0_px);
            return m_margins.left;
        }
        if(!src_el()->css().get_margins().left.is_predefined() && src_el()->css().get_margins().right.is_predefined())
        {
            pixel_t el_width =
                m_pos.width + m_borders.left + m_borders.right + m_padding.left + m_padding.right + m_margins.left;
            m_margins.right = parent_width - el_width;
            m_margins.right = std::max<pixel_t>(m_margins.right, 0_px);
        }
    }
    return 0_px;
}

void litehtml::render_item::apply_relative_shift(const containing_block_context& containing_block_size)
{
    if(src_el()->css().get_position() == element_position_relative)
    {
        css_offsets offsets = src_el()->css().get_offsets();
        if(!offsets.left.is_predefined())
        {
            m_pos.x += offsets.left.calc_percent(containing_block_size.width);
        } else if(!offsets.right.is_predefined())
        {
            m_pos.x -= offsets.right.calc_percent(containing_block_size.width);
        }
        if(!offsets.top.is_predefined())
        {
            m_pos.y += offsets.top.calc_percent(containing_block_size.height);
        } else if(!offsets.bottom.is_predefined())
        {
            m_pos.y -= offsets.bottom.calc_percent(containing_block_size.height);
        }
    }
}

std::tuple<std::shared_ptr<litehtml::render_item>, std::shared_ptr<litehtml::render_item>,
           std::shared_ptr<litehtml::render_item>>
litehtml::render_item::split_inlines()
{
    std::tuple<std::shared_ptr<litehtml::render_item>, std::shared_ptr<litehtml::render_item>,
               std::shared_ptr<litehtml::render_item>>
        ret;
    for(const auto& child : m_children)
    {
        if(child->src_el()->is_block_box() && child->src_el()->css().get_float() == float_none)
        {
            std::get<0>(ret) = clone();
            std::get<1>(ret) = child;
            std::get<2>(ret) = clone();

            std::get<1>(ret)->parent(std::get<0>(ret));
            std::get<2>(ret)->parent(std::get<0>(ret));

            bool found = false;
            for(const auto& ch : m_children)
            {
                if(ch == child)
                {
                    found = true;
                    continue;
                }
                if(!found)
                {
                    std::get<0>(ret)->add_child(ch);
                } else
                {
                    std::get<2>(ret)->add_child(ch);
                }
            }
            break;
        }
        if(!child->children().empty())
        {
            auto child_split = child->split_inlines();
            if(std::get<0>(child_split))
            {
                std::get<0>(ret) = clone();
                std::get<1>(ret) = std::get<1>(child_split);
                std::get<2>(ret) = clone();

                std::get<2>(ret)->parent(std::get<0>(ret));

                bool found = false;
                for(const auto& ch : m_children)
                {
                    if(ch == child)
                    {
                        found = true;
                        continue;
                    }
                    if(!found)
                    {
                        std::get<0>(ret)->add_child(ch);
                    } else
                    {
                        std::get<2>(ret)->add_child(ch);
                    }
                }
                std::get<0>(ret)->add_child(std::get<0>(child_split));
                std::get<2>(ret)->add_child(std::get<2>(child_split));
                break;
            }
        }
    }
    return ret;
}

bool litehtml::render_item::fetch_positioned()
{
    bool ret = false;

    m_positioned.clear();

    for(auto& el : m_children)
    {
        litehtml::element_position el_pos = el->src_el()->css().get_position();
        if(el_pos != element_position_static)
        {
            add_positioned(el);
        }
        if(!ret && (el_pos == element_position_absolute || el_pos == element_position_fixed))
        {
            ret = true;
        }
        if(el->fetch_positioned())
        {
            ret = true;
        }
    }
    return ret;
}

void litehtml::render_item::render_positioned(render_type rt)
{
    position view_port;
    src_el()->get_document()->container()->get_viewport(view_port);

    bool process = false;
    for(auto& el : m_positioned)
    {
        element_position el_position = el->src_el()->css().get_position();

        process = false;
        if(el->src_el()->css().get_display() != display_none)
        {
            if(el_position == element_position_absolute)
            {
                if(rt != render_fixed_only)
                {
                    process = true;
                }
            } else if(el_position == element_position_fixed)
            {
                if(rt != render_no_fixed)
                {
                    process = true;
                }
            }
        }

        if(process)
        {
            containing_block_context containing_block_size;
            if(el_position == element_position_fixed || (is_root() && !src_el()->is_positioned()))
            {
                containing_block_size.height = view_port.height;
                containing_block_size.width  = view_port.width;
            } else
            {
                containing_block_size.height = m_pos.height + m_padding.height();
                containing_block_size.width  = m_pos.width + m_padding.width();
            }

            css_length css_left   = el->src_el()->css().get_offsets().left;
            css_length css_right  = el->src_el()->css().get_offsets().right;
            css_length css_top    = el->src_el()->css().get_offsets().top;
            css_length css_bottom = el->src_el()->css().get_offsets().bottom;

            bool need_render = false;

            css_length el_width  = el->src_el()->css().get_width();
            css_length el_height = el->src_el()->css().get_height();

            // The height is a css height, so it is specified in the box sizing of the element and
            // the paddings and the borders are added to it only for a content-box element.
            auto fix_height_min_max = [&](pixel_t height) {
                auto max_height = el->css().get_max_height();
                auto min_height = el->css().get_min_height();
                if(!max_height.is_predefined())
                {
                    pixel_t max_height_value = max_height.calc_percent(containing_block_size.height);
                    height                   = std::min(height, max_height_value);
                }
                if(!min_height.is_predefined())
                {
                    pixel_t min_height_value = min_height.calc_percent(containing_block_size.height);
                    height                   = std::max(height, min_height_value);
                }
                height += el->content_offset_height() - el->box_sizing_height();
                return height;
            };

            // see fix_height_min_max
            auto fix_width_min_max = [&](pixel_t width) {
                auto max_width = el->css().get_max_width();
                auto min_width = el->css().get_min_width();
                if(!max_width.is_predefined())
                {
                    pixel_t max_width_value = max_width.calc_percent(containing_block_size.width);
                    width                   = std::min(width, max_width_value);
                }
                if(!min_width.is_predefined())
                {
                    pixel_t min_width_value = min_width.calc_percent(containing_block_size.width);
                    width                   = std::max(width, min_width_value);
                }
                width += el->content_offset_width() - el->box_sizing_width();
                return width;
            };

            pixel_t bottom = 0_px;
            pixel_t top    = 0_px;
            pixel_t height = 0_px;

            auto [el_static_offset_x, el_static_offset_y] = element_static_offset(el);

            pixel_t el_static_x = el->m_pos.x + el_static_offset_x;
            pixel_t el_static_y = el->m_pos.y + el_static_offset_y;
            // Calculate vertical position
            // https://www.w3.org/TR/CSS22/visudet.html#abs-non-replaced-height
            // 10.6.4 Absolutely positioned, non-replaced elements
            if(css_top.is_predefined() && !css_bottom.is_predefined() && el_height.is_predefined())
            {
                // 1. 'top' and 'height' are 'auto' and 'bottom' is not 'auto', then the height is based on the
                // content per 10.6.7, set 'auto' values for 'margin-top' and 'margin-bottom' to 0, and solve for 'top'
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                height = el->height();
                bottom = css_bottom.calc_percent(containing_block_size.height);
                top    = containing_block_size.height.value - height - bottom;
            } else if(css_top.is_predefined() && css_bottom.is_predefined() && !el_height.is_predefined())
            {
                // 2. 'top' and 'bottom' are 'auto' and 'height' is not 'auto', then set 'top' to the static position,
                // set 'auto' values for 'margin-top' and 'margin-bottom' to 0, and solve for 'bottom'
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                top    = el_static_y - el->content_offset_top();
                height = fix_height_min_max(el_height.calc_percent(containing_block_size.height));
            } else if(!css_top.is_predefined() && css_bottom.is_predefined() && el_height.is_predefined())
            {
                // 3. 'height' and 'bottom' are 'auto' and 'top' is not 'auto', then the height is based on the
                // content per 10.6.7, set 'auto' values for 'margin-top' and 'margin-bottom' to 0,
                // and solve for 'bottom'
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                height = el->height();
                top    = css_top.calc_percent(containing_block_size.height);
            } else if(css_top.is_predefined() && !css_bottom.is_predefined() && !el_height.is_predefined())
            {
                // 4. 'top' is 'auto', 'height' and 'bottom' are not 'auto', then set 'auto' values for 'margin-top'
                // and 'margin-bottom' to 0, and solve for 'top'
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                height = fix_height_min_max(el_height.calc_percent(containing_block_size.height));
                bottom = css_bottom.calc_percent(containing_block_size.height);
                top    = containing_block_size.height.value - height - bottom;
            } else if(!css_top.is_predefined() && !css_bottom.is_predefined() && el_height.is_predefined())
            {
                // 5. 'height' is 'auto', 'top' and 'bottom' are not 'auto', then 'auto' values for 'margin-top' and
                // 'margin-bottom' are set to 0 and solve for 'height'
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                bottom = css_bottom.calc_percent(containing_block_size.height);
                top    = css_top.calc_percent(containing_block_size.height);
                if(el->src_el()->is_replaced())
                {
                    height = el->height() - el->content_offset_height();
                    pixel_t reminded =
                        (containing_block_size.height.value - top - bottom) - height - el->content_offset_height();
                    if(reminded > 0_px)
                    {
                        int divider = 0;
                        if(el->css().get_margins().top.is_predefined())
                        {
                            divider++;
                        }
                        if(el->css().get_margins().bottom.is_predefined())
                        {
                            divider++;
                        }
                        if(divider != 0)
                        {
                            if(el->css().get_margins().top.is_predefined())
                            {
                                el->m_margins.top = reminded / pixel_t(divider);
                            }
                            if(el->css().get_margins().bottom.is_predefined())
                            {
                                el->m_margins.bottom = reminded / pixel_t(divider);
                            }
                        }
                    }
                    height += el->content_offset_height();
                } else
                {
                    height = containing_block_size.height.value - top - bottom;
                }
                if(!el->css().get_max_height().is_predefined())
                {
                    pixel_t max_height = el->css().get_max_height().calc_percent(containing_block_size.height);
                    if(height - el->content_offset_height() > max_height)
                    {
                        pixel_t reminded = height - el->content_offset_height() - max_height;
                        height           = max_height;
                        int divider      = 0;
                        if(el->css().get_margins().top.is_predefined())
                        {
                            divider++;
                        }
                        if(el->css().get_margins().bottom.is_predefined())
                        {
                            divider++;
                        }
                        if(divider != 0)
                        {
                            if(el->css().get_margins().top.is_predefined())
                            {
                                el->m_margins.top = reminded / pixel_t(divider);
                            }
                            if(el->css().get_margins().bottom.is_predefined())
                            {
                                el->m_margins.bottom = reminded / pixel_t(divider);
                            }
                        }
                        height += el->content_offset_height();
                    }
                }
            } else if(!css_top.is_predefined() && css_bottom.is_predefined() && !el_height.is_predefined())
            {
                // 6. 'bottom' is 'auto', 'top' and 'height' are not 'auto', then set 'auto' values for 'margin-top'
                // and 'margin-bottom' to 0 and solve for 'bottom'
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                height = fix_height_min_max(el_height.calc_percent(containing_block_size.height));
                top    = css_top.calc_percent(containing_block_size.height);
            } else if(css_top.is_predefined() && css_bottom.is_predefined() && el_height.is_predefined())
            {
                // If all three of 'top', 'height', and 'bottom' are auto, set 'top' to the static position and
                // apply rule number three.
                if(el->css().get_margins().top.is_predefined())
                {
                    el->m_margins.top = 0;
                }
                if(el->css().get_margins().bottom.is_predefined())
                {
                    el->m_margins.bottom = 0;
                }
                height = el->height();
                top    = el_static_y - el->content_offset_top();
            } else
            {
                // If none of the three are 'auto':
                height           = fix_height_min_max(el_height.calc_percent(containing_block_size.height));
                top              = css_top.calc_percent(containing_block_size.height);
                bottom           = css_bottom.calc_percent(containing_block_size.height);
                pixel_t remained = containing_block_size.height.value - height - top - bottom;

                if(el->css().get_margins().top.is_predefined() && el->css().get_margins().bottom.is_predefined())
                {
                    // If both 'margin-top' and 'margin-bottom' are 'auto', solve the equation under the extra
                    // constraint that the two margins get equal values.
                    el->m_margins.top = el->m_margins.bottom  = remained / 2_px;
                    height                                   += el->m_margins.top + el->m_margins.bottom;
                } else
                {
                    // If one of 'margin-top' or 'margin-bottom' is 'auto', solve the equation for that value.
                    if(el->css().get_margins().top.is_predefined())
                    {
                        el->m_margins.top  = remained;
                        height            += el->m_margins.top;
                    }
                    if(el->css().get_margins().bottom.is_predefined())
                    {
                        el->m_margins.bottom  = remained;
                        height               += el->m_margins.bottom;
                    }
                }
            }
            el->m_pos.y = top + el->content_offset_top();
            if(el->m_pos.height != height - el->content_offset_height())
            {
                el->m_pos.height = height - el->content_offset_height();
                need_render      = true;
            }

            // Calculate horizontal position
            pixel_t right = 0_px;
            pixel_t left  = 0_px;
            pixel_t width = 0_px;
            // https://www.w3.org/TR/CSS22/visudet.html#abs-non-replaced-width
            // 10.3.7 Absolutely positioned, non-replaced elements
            if(css_left.is_predefined() && !css_right.is_predefined() && el_width.is_predefined())
            {
                // 1. 'left' and 'width' are 'auto' and 'right' is not 'auto', then the width is shrink-to-fit.
                // Then solve for 'left'
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                width = el->width();
                right = css_right.calc_percent(containing_block_size.width);
                left  = containing_block_size.width.value - width - right;
            } else if(css_left.is_predefined() && css_right.is_predefined() && !el_width.is_predefined())
            {
                // 2. 'left' and 'right' are 'auto' and 'width' is not 'auto', then if the 'direction' property of
                // the element establishing the static-position containing block is 'ltr' set 'left' to the
                // static position, otherwise set 'right' to the static position. Then solve for 'left'
                // (if 'direction is 'rtl') or 'right' (if 'direction' is 'ltr').
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                left  = el_static_x - el->content_offset_left();
                width = fix_width_min_max(el_width.calc_percent(containing_block_size.width));
            } else if(!css_left.is_predefined() && css_right.is_predefined() && el_width.is_predefined())
            {
                // 3. 'width' and 'right' are 'auto' and 'left' is not 'auto', then the width is shrink-to-fit .
                // Then solve for 'right'
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                width = el->width();
                left  = css_left.calc_percent(containing_block_size.width);
            } else if(css_left.is_predefined() && !css_right.is_predefined() && !el_width.is_predefined())
            {
                // 4. 'left' is 'auto', 'width' and 'right' are not 'auto', then solve for 'left'
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                right = css_right.calc_percent(containing_block_size.width);
                width = fix_width_min_max(el_width.calc_percent(containing_block_size.width));
                left  = containing_block_size.width.value - right - width;
            } else if(!css_left.is_predefined() && !css_right.is_predefined() && el_width.is_predefined())
            {
                // 5. 'width' is 'auto', 'left' and 'right' are not 'auto', then solve for 'width'
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                left  = css_left.calc_percent(containing_block_size.width);
                right = css_right.calc_percent(containing_block_size.width);
                if(el->src_el()->is_replaced())
                {
                    width = el->width() - el->content_offset_width();
                    pixel_t reminded =
                        (containing_block_size.width.value - left - right) - width - el->content_offset_width();
                    if(reminded != 0_px)
                    {
                        int divider = 0;
                        if(el->css().get_margins().left.is_predefined())
                        {
                            divider++;
                        }
                        if(el->css().get_margins().right.is_predefined())
                        {
                            divider++;
                        }
                        if(divider != 0)
                        {
                            if(el->css().get_margins().left.is_predefined())
                            {
                                el->m_margins.left = reminded / pixel_t(divider);
                            }
                            if(el->css().get_margins().right.is_predefined())
                            {
                                el->m_margins.right = reminded / pixel_t(divider);
                            }
                        }
                    }
                    width += el->content_offset_width();
                } else
                {
                    width = containing_block_size.width.value - left - right;
                }
                if(!el->css().get_max_width().is_predefined())
                {
                    pixel_t max_width = el->css().get_max_width().calc_percent(containing_block_size.height);
                    if(width - el->content_offset_width() > max_width)
                    {
                        pixel_t reminded = width - el->content_offset_width() - max_width;
                        width            = max_width;
                        int divider      = 0;
                        if(el->css().get_margins().left.is_predefined())
                        {
                            divider++;
                        }
                        if(el->css().get_margins().right.is_predefined())
                        {
                            divider++;
                        }
                        if(divider != 0)
                        {
                            if(el->css().get_margins().left.is_predefined())
                            {
                                el->m_margins.left = reminded / pixel_t(divider);
                            }
                            if(el->css().get_margins().right.is_predefined())
                            {
                                el->m_margins.right = reminded / pixel_t(divider);
                            }
                        }
                        width += el->content_offset_width();
                    }
                }
            } else if(!css_left.is_predefined() && css_right.is_predefined() && !el_width.is_predefined())
            {
                // 6. 'right' is 'auto', 'left' and 'width' are not 'auto', then solve for 'right'
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                left  = css_left.calc_percent(containing_block_size.width);
                width = fix_width_min_max(el_width.calc_percent(containing_block_size.width));
            } else if(css_left.is_predefined() && css_right.is_predefined() && el_width.is_predefined())
            {
                // If all three of 'left', 'width', and 'right' are 'auto': First set any 'auto' values for
                // 'margin-left' and 'margin-right' to 0. Then, if the 'direction' property of the element
                // establishing the static-position containing block is 'ltr' set 'left' to the static position
                // and apply rule number three below; otherwise, set 'right' to the static position and apply
                // rule number one below.
                if(el->css().get_margins().left.is_predefined())
                {
                    el->m_margins.left = 0;
                }
                if(el->css().get_margins().right.is_predefined())
                {
                    el->m_margins.right = 0;
                }
                width = el->width();
                left  = el_static_x - el->content_offset_left();
            } else
            {
                // If none of the three is 'auto':
                width            = fix_width_min_max(el_width.calc_percent(containing_block_size.width));
                left             = css_left.calc_percent(containing_block_size.width);
                right            = css_right.calc_percent(containing_block_size.width);
                pixel_t remained = containing_block_size.width.value - width - left - right;

                if(el->css().get_margins().left.is_predefined() && el->css().get_margins().right.is_predefined())
                {
                    // If both 'margin-left' and 'margin-right' are 'auto', solve the equation under the extra
                    // constraint that the two margins get equal values, unless this would make them negative,
                    // in which case when direction of the containing block is 'ltr' ('rtl'), set 'margin-left'
                    // ('margin-right') to zero and solve for 'margin-right' ('margin-left').
                    el->m_margins.left = el->m_margins.right = remained / 2_px;
                    if(el->m_margins.left < 0_px)
                    {
                        el->m_margins.left  = 0;
                        el->m_margins.right = remained;
                    }
                    width += el->m_margins.left + el->m_margins.right;
                } else
                {
                    // If one of 'margin-left' or 'margin-right' is 'auto', solve the equation
                    // for that value. If the values are over-constrained, ignore the value for 'left'
                    // (in case the 'direction' property of the containing block is 'rtl') or 'right' (in case
                    // 'direction' is 'ltr') and solve for that value.
                    if(el->css().get_margins().left.is_predefined())
                    {
                        el->m_margins.left  = remained;
                        width              += el->m_margins.left;
                    }
                    if(el->css().get_margins().right.is_predefined())
                    {
                        el->m_margins.right  = remained;
                        width               += el->m_margins.right;
                    }
                }
            }
            el->m_pos.x = left + el->content_offset_left();
            if(el->m_pos.width != width - el->content_offset_width())
            {
                el->m_pos.width = width - el->content_offset_width();
                need_render     = true;
            }

            if(el_position != element_position_fixed)
            {
                el->m_pos.x -= el_static_offset_x;
                el->m_pos.y -= el_static_offset_y;
            }

            if(need_render)
            {
                position pos = el->m_pos;
                el->render(el->left(), el->top(), containing_block_size.new_width(el->width()), nullptr, true);
                el->m_pos = pos;
            }

            if(el_position == element_position_fixed)
            {
                position fixed_pos = el->pos();
                el->get_redraw_box(fixed_pos);
                src_el()->get_document()->add_fixed_box(fixed_pos);
            }
        }

        el->render_positioned();
    }

    if(!m_positioned.empty())
    {
        std::stable_sort(m_positioned.begin(), m_positioned.end(),
                         [](const std::shared_ptr<render_item>& Left, const std::shared_ptr<render_item>& Right) {
                             return (Left->src_el()->css().get_z_index() < Right->src_el()->css().get_z_index());
                         });
    }
}

void litehtml::render_item::add_positioned(const std::shared_ptr<litehtml::render_item>& el)
{
    if(src_el()->css().get_position() != element_position_static || is_root())
    {
        m_positioned.push_back(el);
    } else
    {
        auto el_parent = parent();
        if(el_parent)
        {
            el_parent->add_positioned(el);
        }
    }
}

void litehtml::render_item::get_redraw_box(litehtml::position& pos, pixel_t x /*= 0*/, pixel_t y /*= 0*/)
{
    if(is_visible())
    {
        pixel_t p_left   = std::min(pos.left(), x + m_pos.left() - m_padding.left - m_borders.left);
        pixel_t p_right  = std::max(pos.right(), x + m_pos.right() + m_padding.left + m_borders.left);
        pixel_t p_top    = std::min(pos.top(), y + m_pos.top() - m_padding.top - m_borders.top);
        pixel_t p_bottom = std::max(pos.bottom(), y + m_pos.bottom() + m_padding.bottom + m_borders.bottom);

        pos.x      = p_left;
        pos.y      = p_top;
        pos.width  = p_right - p_left;
        pos.height = p_bottom - p_top;

        if(src_el()->css().get_overflow() == overflow_visible)
        {
            for(auto& el : m_children)
            {
                if(el->src_el()->css().get_position() != element_position_fixed)
                {
                    el->get_redraw_box(pos, x + m_pos.x, y + m_pos.y);
                }
            }
        }
    }
}

void litehtml::render_item::calc_document_size(litehtml::size& sz, pixel_t x /*= 0*/, pixel_t y /*= 0*/)
{
    auto inline_processed = for_inline_boxes([&sz, &x, &y](const position& box, bool, bool) {
        sz.width  = std::max(sz.width, x + box.x + box.width);
        sz.height = std::max(sz.height, y + box.y + box.height);
        return true;
    });

    if(!inline_processed)
    {
        if(is_visible())
        {
            if(src_el()->css().get_position() != element_position_fixed)
            {
                sz.width  = std::max(sz.width, x + right());
                sz.height = std::max(sz.height, y + bottom());
            }

            if(is_one_of(src_el()->css().get_overflow(), overflow_scroll, overflow_auto))
            {
                size child_size;
                for(const auto& el : m_children)
                {
                    el->calc_document_size(child_size, 0_px, 0_px);
                }
                if(!m_scroll_view)
                {
                    m_scroll_view = std::make_shared<scroll_view>(m_pos, child_size);
                } else
                {
                    m_scroll_view->set(m_pos, child_size);
                }
            } else
            {
                // All children of tables and blocks with style other than "overflow: visible" are inside element.
                // We can skip calculating the size of children
                if(src_el()->css().get_overflow() == overflow_visible && src_el()->css().get_display() != display_table)
                {
                    for(const auto& el : m_children)
                    {
                        el->calc_document_size(sz, x + m_pos.x, y + m_pos.y);
                    }
                } else
                {
                    size child_size;
                    for(const auto& el : m_children)
                    {
                        el->calc_document_size(child_size, 0_px, 0_px);
                    }
                }
            }
        }
    }
}

void litehtml::render_item::draw_stacking_context(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                                  bool with_positioned)
{
    if(!is_visible())
    {
        return;
    }

    std::map<int, bool> z_indexes;
    if(with_positioned)
    {
        for(const auto& idx : m_positioned)
        {
            z_indexes[idx->src_el()->css().get_z_index()];
        }

        for(const auto& idx : z_indexes)
        {
            if(idx.first < 0)
            {
                draw_children(hdc, x, y, clip, draw_positioned, idx.first);
            }
        }
    }
    draw_children(hdc, x, y, clip, draw_block, 0);
    draw_children(hdc, x, y, clip, draw_floats, 0);
    draw_children(hdc, x, y, clip, draw_inlines, 0);
    if(with_positioned)
    {
        for(auto& z_index : z_indexes)
        {
            if(z_index.first == 0)
            {
                draw_children(hdc, x, y, clip, draw_positioned, z_index.first);
            }
        }

        for(auto& z_index : z_indexes)
        {
            if(z_index.first > 0)
            {
                draw_children(hdc, x, y, clip, draw_positioned, z_index.first);
            }
        }
    }
}

void litehtml::render_item::draw_children(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, draw_flag flag,
                                          int zindex)
{
    position pos  = m_pos;
    pos.x        += x - get_scroll_left();
    pos.y        += y - get_scroll_top();

    document::ptr doc = src_el()->get_document();

    if(src_el()->css().get_overflow() > overflow_visible)
    {
        // TODO: Process overflow for inline elements
        if(src_el()->css().get_display() != display_inline)
        {
            position clip_box    = m_pos;
            clip_box.x          += x;
            clip_box.y          += y;
            position border_box  = clip_box;
            border_box          += m_padding;
            border_box          += m_borders;

            border_radiuses bdr_radius =
                src_el()->css().get_borders().radius.calc_percents(border_box.width, border_box.height);

            bdr_radius -= m_borders;
            bdr_radius -= m_padding;

            doc->container()->set_clip(clip_box, bdr_radius);
        }
    }

    for(const auto& el : m_children)
    {
        if(el->is_visible())
        {
            bool process = true;
            switch(flag)
            {
            case draw_positioned:
                if(el->src_el()->is_positioned() && el->src_el()->css().get_z_index() == zindex)
                {
                    if(el->src_el()->css().get_position() == element_position_fixed)
                    {
                        // Fixed elements position is always relative to the (0,0)
                        el->src_el()->draw(hdc, 0_px, 0_px, clip, el);
                        el->draw_stacking_context(hdc, 0_px, 0_px, clip, true);
                    } else
                    {
                        el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                        el->draw_stacking_context(hdc, pos.x, pos.y, clip, true);
                    }
                    process = false;
                }
                break;
            case draw_block:
                if(!el->src_el()->is_inline() && el->src_el()->css().get_float() == float_none &&
                   !el->src_el()->is_positioned())
                {
                    el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                }
                break;
            case draw_floats:
                if(el->src_el()->css().get_float() != float_none && !el->src_el()->is_positioned())
                {
                    el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                    el->draw_stacking_context(hdc, pos.x, pos.y, clip, false);
                    process = false;
                }
                break;
            case draw_inlines:
                if(el->src_el()->is_inline() && el->src_el()->css().get_float() == float_none &&
                   !el->src_el()->is_positioned())
                {
                    el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                    if(el->src_el()->css().get_display() == display_inline_block ||
                       el->src_el()->css().get_display() == display_inline_flex)
                    {
                        el->draw_stacking_context(hdc, pos.x, pos.y, clip, false);
                        process = false;
                    }
                }
                break;
            default:
                break;
            }

            if(process)
            {
                if(flag == draw_positioned)
                {
                    if(!el->src_el()->is_positioned())
                    {
                        el->draw_children(hdc, pos.x, pos.y, clip, flag, zindex);
                    }
                } else
                {
                    if(el->src_el()->css().get_float() == float_none &&
                       el->src_el()->css().get_display() != display_inline_block && !el->src_el()->is_positioned())
                    {
                        el->draw_children(hdc, pos.x, pos.y, clip, flag, zindex);
                    }
                }
            }
        }
    }

    if(src_el()->css().get_overflow() > overflow_visible)
    {
        doc->container()->del_clip();
    }
}

std::shared_ptr<litehtml::element> litehtml::render_item::get_child_by_point(
    pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y, draw_flag flag, int zindex,
    const std::function<bool(const std::shared_ptr<render_item>&)>& check)
{
    if(src_el()->css().get_overflow() > overflow_visible)
    {
        if(!m_pos.is_point_inside(x, y))
        {
            return nullptr;
        }
    }

    element::ptr ret = nullptr;

    position el_pos = m_pos;
    el_pos.x        = x - el_pos.x + get_scroll_left();
    el_pos.y        = y - el_pos.y + get_scroll_top();

    for(auto i = m_children.crbegin(); i != m_children.crend() && !ret; std::advance(i, 1))
    {
        const auto& el = *i;

        if(el->is_visible() && el->src_el()->css().get_display() != display_inline_text)
        {
            bool process = true;
            switch(flag)
            {
            case draw_positioned:
                if(el->src_el()->is_positioned() && el->src_el()->css().get_z_index() == zindex)
                {
                    if(el->src_el()->css().get_position() == element_position_fixed)
                    {
                        ret = el->get_element_by_point(client_x, client_y, client_x, client_y, check);
                        if(!ret && el->is_point_inside(client_x, client_y))
                        {
                            if(!check || check(el))
                            {
                                ret = el->src_el();
                            }
                        }
                    } else
                    {
                        ret = el->get_element_by_point(el_pos.x, el_pos.y, client_x, client_y, check);
                        if(!ret && el->is_point_inside(el_pos.x, el_pos.y))
                        {
                            if(!check || check(el))
                            {
                                ret = el->src_el();
                            }
                        }
                    }
                    process = false;
                }
                break;
            case draw_block:
                if(!el->src_el()->is_inline() && el->src_el()->css().get_float() == float_none &&
                   !el->src_el()->is_positioned())
                {
                    ret = el->get_child_by_point(el_pos.x, el_pos.y, client_x, client_y, flag, zindex, check);

                    if(!ret && el->is_point_inside(el_pos.x, el_pos.y))
                    {
                        if(!check || check(el))
                        {
                            ret = el->src_el();
                        }
                    }
                    process = false;
                }
                break;
            case draw_floats:
                if(el->src_el()->css().get_float() != float_none && !el->src_el()->is_positioned())
                {
                    ret = el->get_element_by_point(el_pos.x, el_pos.y, client_x, client_y, check);

                    if(!ret && el->is_point_inside(el_pos.x, el_pos.y))
                    {
                        ret = el->src_el();
                    }
                    process = false;
                }
                break;
            case draw_inlines:
                if(el->src_el()->is_inline() && el->src_el()->css().get_float() == float_none &&
                   !el->src_el()->is_positioned())
                {
                    if(el->src_el()->css().get_display() == display_inline_block ||
                       el->src_el()->css().get_display() == display_inline_table ||
                       el->src_el()->css().get_display() == display_inline_flex)
                    {
                        ret     = el->get_element_by_point(el_pos.x, el_pos.y, client_x, client_y, check);
                        process = false;
                    }
                    if(!ret && el->is_point_inside(el_pos.x, el_pos.y))
                    {
                        if(!check || check(el))
                        {
                            ret = el->src_el();
                        }
                    }
                }
                break;
            default:
                break;
            }

            if(process && !el->src_el()->is_positioned())
            {
                if(flag == draw_positioned)
                {
                    element::ptr child =
                        el->get_child_by_point(el_pos.x, el_pos.y, client_x, client_y, flag, zindex, check);
                    if(child)
                    {
                        if(!check)
                        {
                            ret = child;
                        } else
                        {
                            child->run_on_renderers(
                                [&check, &ret, &child](const std::shared_ptr<render_item>& ri) -> bool {
                                    if(check(ri))
                                    {
                                        ret = child;
                                        return false;
                                    }
                                    return true;
                                });
                        }
                    }
                } else
                {
                    if(el->src_el()->css().get_float() == float_none &&
                       el->src_el()->css().get_display() != display_inline_block &&
                       el->src_el()->css().get_display() != display_inline_flex)
                    {
                        element::ptr child =
                            el->get_child_by_point(el_pos.x, el_pos.y, client_x, client_y, flag, zindex, check);
                        if(child)
                        {
                            if(!check)
                            {
                                ret = child;
                            } else
                            {
                                child->run_on_renderers(
                                    [&check, &ret, &child](const std::shared_ptr<render_item>& ri) -> bool {
                                        if(check(ri))
                                        {
                                            ret = child;
                                            return false;
                                        }
                                        return true;
                                    });
                            }
                        }
                    }
                }
            }
        }
    }

    return ret;
}

std::shared_ptr<litehtml::element> litehtml::render_item::get_element_by_point(
    pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
    const std::function<bool(const std::shared_ptr<render_item>&)>& check)
{
    if(!is_visible())
    {
        return nullptr;
    }

    element::ptr ret;

    std::map<int, bool> z_indexes;

    for(const auto& i : m_positioned)
    {
        z_indexes[i->src_el()->css().get_z_index()];
    }

    for(auto iter = z_indexes.rbegin(); iter != z_indexes.rend(); ++iter)
    {
        if(iter->first > 0)
        {
            ret = get_child_by_point(x, y, client_x, client_y, draw_positioned, iter->first, check);
            if(ret)
            {
                return ret;
            }
        }
    }

    for(const auto& z_index : z_indexes)
    {
        if(z_index.first == 0)
        {
            ret = get_child_by_point(x, y, client_x, client_y, draw_positioned, z_index.first, check);
            if(ret)
            {
                return ret;
            }
        }
    }

    ret = get_child_by_point(x, y, client_x, client_y, draw_inlines, 0, check);
    if(ret)
    {
        return ret;
    }

    ret = get_child_by_point(x, y, client_x, client_y, draw_floats, 0, check);
    if(ret)
    {
        return ret;
    }

    ret = get_child_by_point(x, y, client_x, client_y, draw_block, 0, check);
    if(ret)
    {
        return ret;
    }

    for(auto iter = z_indexes.rbegin(); iter != z_indexes.rend(); ++iter)
    {
        if(iter->first < 0)
        {
            ret = get_child_by_point(x, y, client_x, client_y, draw_positioned, iter->first, check);
            if(ret)
            {
                return ret;
            }
        }
    }

    if(src_el()->css().get_position() == element_position_fixed)
    {
        if(is_point_inside(client_x, client_y))
        {
            if(!check || check(this->shared_from_this()))
            {
                ret = src_el();
            }
        }
    } else
    {
        if(is_point_inside(x, y))
        {
            if(!check || check(this->shared_from_this()))
            {
                ret = src_el();
            }
        }
    }

    return ret;
}

bool litehtml::render_item::is_point_inside(pixel_t x, pixel_t y) const
{
    position pos = m_pos;

    pos += m_padding;
    pos += m_borders;
    return pos.is_point_inside(x, y);
}

void litehtml::render_item::get_rendering_boxes(const std::function<void(const position&)>& redraw_box) const
{
    pixel_t add_x = 0_px;
    pixel_t add_y = 0_px;

    if(src_el()->css().get_position() != element_position_fixed)
    {
        auto cur_el = parent();

        while(cur_el)
        {
            if(cur_el->css().get_position() == element_position_fixed)
            {
                position view_port;
                src_el()->get_document()->container()->get_viewport(view_port);
                add_x += cur_el->m_pos.x + view_port.left() - cur_el->get_scroll_left();
                add_y += cur_el->m_pos.y + view_port.top() - cur_el->get_scroll_top();
                break;
            }
            add_x  += cur_el->m_pos.x - cur_el->get_scroll_left();
            add_y  += cur_el->m_pos.y - cur_el->get_scroll_top();
            cur_el  = cur_el->parent();
        }
    } else
    {
        position view_port;
        src_el()->get_document()->container()->get_viewport(view_port);
        add_x = view_port.left();
        add_y = view_port.top();
    }

    auto inline_processed = for_inline_boxes([&](const position& box, bool, bool) {
        position box_ = box;
        scroll_box(box_);
        box_.x += add_x;
        box_.y += add_y;
        redraw_box(box_);
        return true;
    });

    if(!inline_processed)
    {
        position pos  = m_pos;
        pos          += m_padding;
        pos          += m_borders;
        pos.x        += add_x;
        pos.y        += add_y;
        redraw_box(pos);
    }
}

void litehtml::render_item::dump(litehtml::dumper& cout)
{
    cout.begin_node(src_el()->dump_get_name() + "{" + std::string(typeid(*this).name()) + "}");

    auto attrs = src_el()->dump_get_attrs();
    if(!attrs.empty())
    {
        cout.begin_attrs_group("attributes");
        for(const auto& attr : attrs)
        {
            cout.add_attr(std::get<0>(attr), std::get<1>(attr));
        }
        cout.end_attrs_group();
    }

    if(!m_children.empty())
    {
        cout.begin_attrs_group("children");
        for(const auto& el : m_children)
        {
            el->dump(cout);
        }
        cout.end_attrs_group();
    }

    cout.end_node();
}

litehtml::position litehtml::render_item::get_placement() const
{
    litehtml::position pos = m_pos;
    if(css().get_position() == element_position_fixed)
    {
        position view_port;
        src_el()->get_document()->container()->get_viewport(view_port);
        pos.x += view_port.left();
        pos.y += view_port.top();
        return pos;
    }
    auto cur_el = parent();
    while(cur_el)
    {
        auto p  = cur_el->calc_placement();
        pos.x  += p.x;
        pos.y  += p.y;

        if(cur_el->css().get_position() == element_position_fixed)
        {
            position view_port;
            src_el()->get_document()->container()->get_viewport(view_port);
            pos.x += view_port.left();
            pos.y += view_port.top();
            return pos;
        }

        cur_el = cur_el->parent();
    }
    return pos;
}

std::shared_ptr<litehtml::render_item> litehtml::render_item::init()
{
    src_el()->add_render(shared_from_this());

    for(auto& el : children())
    {
        el = el->init();
    }

    return shared_from_this();
}

void litehtml::render_item::calc_cb_length(const css_length& len, pixel_t percent_base,
                                           containing_block_context::typed_pixel& out_value) const
{
    if(!len.is_predefined())
    {
        if(len.units() == litehtml::css_units_percentage)
        {
            out_value.value = len.calc_percent(percent_base);
            out_value.type  = litehtml::containing_block_context::cbc_value_type_percentage;
        } else
        {
            out_value.value = src_el()->get_document()->to_pixels(len, css().get_font_metrics(), 0_px);
            out_value.type  = containing_block_context::cbc_value_type_absolute;
        }
    }
}

litehtml::containing_block_context litehtml::render_item::calculate_containing_block_context(
    const containing_block_context& cb_context)
{
    containing_block_context ret;
    ret.context_idx = cb_context.context_idx + 1;
    ret.width.value = ret.max_width.value = cb_context.width.value - content_offset_width();
    if(src_el()->css().get_position() != element_position_absolute &&
       src_el()->css().get_position() != element_position_fixed)
    {
        ret.height.value = cb_context.height.value - content_offset_height();
    }

    // Calculate width if css property is not auto
    // We have to use aut value for display_table_cell also.
    if(src_el()->css().get_display() != display_table_cell)
    {
        auto par = parent();
        if((cb_context.size_mode & containing_block_context::size_mode_exact_width) != 0)
        {
            ret.width.value = cb_context.width;
            ret.width.type  = containing_block_context::cbc_value_type_absolute;
        } else
        {
            const auto* width = &css().get_width();
            if(par && (par->css().get_display() == display_flex || par->css().get_display() == display_inline_flex))
            {
                if(!css().get_flex_basis().is_predefined() && css().get_flex_basis().val() >= 0)
                {
                    if(par->css().get_flex_direction() == flex_direction_row ||
                       par->css().get_flex_direction() == flex_direction_row_reverse)
                    {
                        ret.width.type  = containing_block_context::cbc_value_type_auto;
                        ret.width.value = 0;
                        width           = nullptr;
                    }
                }
            }
            if(width != nullptr)
            {
                calc_cb_length(*width, cb_context.width, ret.width);
            }
        }
        if((cb_context.size_mode & containing_block_context::size_mode_exact_height) != 0)
        {
            ret.height.value = cb_context.height;
            ret.height.type  = containing_block_context::cbc_value_type_absolute;
        } else
        {
            const auto* height = &css().get_height();
            if(par && (par->css().get_display() == display_flex || par->css().get_display() == display_inline_flex))
            {
                if(!css().get_flex_basis().is_predefined() && css().get_flex_basis().val() >= 0)
                {
                    if(par->css().get_flex_direction() == flex_direction_column ||
                       par->css().get_flex_direction() == flex_direction_column_reverse)
                    {
                        ret.height.type  = containing_block_context::cbc_value_type_auto;
                        ret.height.value = 0;
                        height           = nullptr;
                    }
                }
            }
            if(height != nullptr)
            {
                calc_cb_length(*height, cb_context.height, ret.height);
            }
        }
        if(ret.width.type != containing_block_context::cbc_value_type_auto &&
           (src_el()->css().get_display() == display_table || src_el()->is_root()))
        {
            ret.width.value -= content_offset_width();
        }
        if(ret.height.type != containing_block_context::cbc_value_type_auto &&
           (src_el()->css().get_display() == display_table || src_el()->is_root()))
        {
            ret.height.value -= content_offset_height();
        }
    }
    ret.render_width = ret.width;

    calc_cb_length(src_el()->css().get_min_width(), cb_context.width, ret.min_width);
    calc_cb_length(src_el()->css().get_max_width(), cb_context.width, ret.max_width);

    calc_cb_length(src_el()->css().get_min_height(), cb_context.height, ret.min_height);
    calc_cb_length(src_el()->css().get_max_height(), cb_context.height, ret.max_height);

    // Fix box sizing
    if(ret.width.type != containing_block_context::cbc_value_type_auto)
    {
        ret.render_width = ret.width.value - box_sizing_width();
    }
    if(ret.min_width.type != containing_block_context::cbc_value_type_none)
    {
        ret.min_width.value -= box_sizing_width();
    }
    if(ret.max_width.type != containing_block_context::cbc_value_type_none)
    {
        ret.max_width.value -= box_sizing_width();
    }
    if(ret.min_height.type != containing_block_context::cbc_value_type_none)
    {
        ret.min_height.value -= box_sizing_height();
    }
    if(ret.max_height.type != containing_block_context::cbc_value_type_none)
    {
        ret.max_height.value -= box_sizing_height();
    }

    // Issue #459: a flex item is rendered to its resolved main size via
    // size_mode_exact_width. For a row-direction flex container the flex algorithm
    // has already clamped the item's main size by its min/max-width, resolved
    // against the flex container's width (see
    // flex_item_row_direction::direction_specific_init). Re-applying a *percentage*
    // min/max-width here would resolve it against the item's own exact size
    // (cb_context.width == the assigned main size) instead of the container, and
    // because the item is rendered more than once (flex_line::init, then
    // align_stretch) the error compounds and the item collapses towards its content
    // width. So skip the width clamp on that exact-width pass.
    if((cb_context.size_mode & containing_block_context::size_mode_exact_width) != 0)
    {
        auto par = parent();
        if(par && (par->css().get_display() == display_flex || par->css().get_display() == display_inline_flex) &&
           (par->css().get_flex_direction() == flex_direction_row ||
            par->css().get_flex_direction() == flex_direction_row_reverse))
        {
            ret.min_width.type = containing_block_context::cbc_value_type_none;
            ret.max_width.type = containing_block_context::cbc_value_type_none;
        }
    }

    return ret;
}

std::tuple<litehtml::pixel_t, litehtml::pixel_t> litehtml::render_item::element_static_offset(
    const std::shared_ptr<litehtml::render_item>& el)
{
    pixel_t offset_x = 0_px;
    pixel_t offset_y = 0_px;
    auto    cur_el   = el->parent();
    auto    this_el  = el->css().get_position() != element_position_fixed ? shared_from_this()
                                                                          : src_el()->get_document()->root_render();
    while(cur_el && cur_el != this_el)
    {
        offset_x += cur_el->m_pos.x;
        offset_y += cur_el->m_pos.y;
        cur_el    = cur_el->parent();
    }

    if(el->css().get_position() == element_position_fixed || (is_root() && !src_el()->is_positioned()))
    {
        offset_x += this_el->m_pos.x;
        offset_y += this_el->m_pos.y;
    } else
    {
        offset_x += m_padding.left;
        offset_y += m_padding.top;
    }

    return {offset_x, offset_y};
}

void litehtml::render_item::y_shift(pixel_t delta)
{
    m_pos.y += delta;
}
