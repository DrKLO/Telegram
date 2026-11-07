#include "render_item.h"
#include "render_inline_context.h"
#include "document.h"
#include "document_container.h"
#include "types.h"
#include <algorithm>
#include <functional>
#include <set>
#include <typeinfo>

thread_local std::vector<const litehtml::render_item*>* litehtml::g_paint_trace = nullptr;

void litehtml::render_item::draw_borders(uint_ptr hdc, position border_box) const
{
    borders bdr = src_el()->css().get_borders();
    if(collapse_skip & 1) bdr.left.style = border_style_none;
    if(collapse_skip & 2) bdr.top.style = border_style_none;
    if(collapse_skip & 4) bdr.right.style = border_style_none;
    if(collapse_skip & 8) bdr.bottom.style = border_style_none;
    if(bdr.is_visible())
    {
        border_box.round();
        bdr.radius = src_el()->css().get_borders().radius.calc_percents(border_box.width, border_box.height);
        src_el()->doc_ptr()->container()->draw_borders(hdc, bdr, border_box, src_el()->is_root());
    }
}

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

bool litehtml::render_item::same_layout_input(const layout_entry& entry, const containing_block_context& cb, bool second_pass)
{
    auto same = [](const containing_block_context::typed_pixel& a, const containing_block_context::typed_pixel& b) {
        return a.type == b.type && a.value == b.value;
    };
    const containing_block_context& c = entry.cb;
    return entry.valid && entry.second_pass == second_pass && same(c.width, cb.width) &&
           same(c.render_width, cb.render_width) && same(c.min_width, cb.min_width) && same(c.max_width, cb.max_width) &&
           same(c.height, cb.height) && same(c.min_height, cb.min_height) && same(c.max_height, cb.max_height) &&
           c.context_idx == cb.context_idx && c.size_mode == cb.size_mode;
}

bool litehtml::render_item::same_resolved_layout_input(const layout_entry& entry,
                                                       const containing_block_context& cb, bool second_pass)
{
    if(!entry.valid || entry.second_pass != second_pass ||
       entry.cb.size_mode != containing_block_context::size_mode_normal ||
       cb.size_mode != containing_block_context::size_mode_normal ||
       entry.cb.context_idx != cb.context_idx)
        return false;
    const auto& style = css();
    auto fixed = [](const css_length& value) {
        return !value.is_predefined() && value.units() == css_units_px && std::isfinite(value.val());
    };
    if(style.get_display() != display_flex || style.get_position() != element_position_static ||
       style.get_float() != float_none || !src_el()->is_block_formatting_context() ||
       !fixed(style.get_width()) || !fixed(style.get_height()) ||
       style.get_width().val() <= 0 || style.get_height().val() <= 0)
        return false;
    const auto& margin = style.get_margins();
    const auto& padding = style.get_padding();
    const auto& border = style.get_borders();
    for(const auto* value : {&margin.left, &margin.right, &margin.top, &margin.bottom,
                             &padding.left, &padding.right, &padding.top, &padding.bottom,
                             &border.left.width, &border.right.width, &border.top.width, &border.bottom.width})
        if(!fixed(*value))
            return false;
    for(const auto* value : {&style.get_min_width(), &style.get_max_width(),
                             &style.get_min_height(), &style.get_max_height()})
        if(!value->is_predefined())
            return false;
    if(getenv("LH_NO_FIXED_LAYOUT_REUSE"))
        return false;
    for(const auto* context : {&entry.cb, &cb})
        for(const auto* value : {&context->width, &context->render_width, &context->height})
            if(!std::isfinite(value->value.value()))
                return false;
    if(m_resolved_layout_safe < 0)
    {
        m_resolved_layout_safe = 1;
        std::vector<render_item*> pending{this};
        size_t count = 0;
        while(!pending.empty())
        {
            render_item* item = pending.back();
            pending.pop_back();
            if(++count > 256 || item->css().get_position() != element_position_static ||
               item->css().get_float() != float_none || item->src_el()->has_external_intrinsic_size())
            {
                m_resolved_layout_safe = 0;
                break;
            }
            for(const auto& child : item->children())
            {
                if(pending.size() + count >= 256)
                {
                    m_resolved_layout_safe = 0;
                    break;
                }
                pending.push_back(child.get());
            }
            if(!m_resolved_layout_safe)
                break;
        }
    }
    if(!m_resolved_layout_safe)
        return false;
    auto before = calculate_containing_block_context(entry.cb);
    auto after = calculate_containing_block_context(cb);
    auto normalize = [](containing_block_context& context) {
        for(auto* value : {&context.min_width, &context.max_width, &context.min_height, &context.max_height})
            if(value->type == containing_block_context::cbc_value_type_none)
                value->value = 0_px;
    };
    normalize(before);
    normalize(after);
    return same_layout_input({before, {}, second_pass, true, 0_px}, after, second_pass);
}

bool litehtml::render_item::layout_cacheable(formatting_context* fmt_ctx) const
{
    const document* doc = src_el()->doc_ptr();
    if(!doc || !doc->layout_cache_enabled() || m_out_of_flow)
        return false;
    const element_position position = css().get_position();
    if(position == element_position_absolute || position == element_position_fixed)
        return false;
    return fmt_ctx == nullptr || src_el()->is_block_formatting_context();
}

bool litehtml::render_item::layout_reusable() const
{
    const document* doc = src_el()->doc_ptr();
    return !m_layout_dirty && doc && m_layout_epoch == doc->layout_epoch();
}

bool litehtml::render_item::relayout_boundary() const
{
    const document* doc = src_el()->doc_ptr();
    if(!doc || !doc->layout_cache_enabled() || !css().contains(contain_size) || m_out_of_flow ||
       !src_el()->is_block_formatting_context() || src_el()->is_replaced())
        return false;
    const element_position position = css().get_position();
    if(position == element_position_absolute || position == element_position_fixed)
        return false;
    if(m_layout_dirty)
        return m_relayout_pending;
    return m_last_layout.valid && m_layout_epoch == doc->layout_epoch();
}

void litehtml::render_item::relayout()
{
    m_relayout_pending = false;
    if(!m_layout_dirty || !m_last_layout.valid)
        return;
    const position placed  = m_pos;
    const margins  margin  = m_margins;
    const margins  padding = m_padding;
    const margins  border  = m_borders;
    render(m_last_x, m_last_y, m_last_layout.cb, nullptr, m_last_layout.second_pass);
    m_pos     = placed;
    m_margins = margin;
    m_padding = padding;
    m_borders = border;
}

void litehtml::render_item::mark_layout_dirty()
{
    document* doc = src_el()->doc_ptr();
    for(render_item* item = this; item;)
    {
        if(item != this && item->relayout_boundary())
        {
            if(!item->m_relayout_pending && doc)
                doc->note_relayout_root(item);
            item->m_relayout_pending   = true;
            item->m_layout_dirty       = true;
            item->m_measured[0].valid = false;
            item->m_measured[1].valid = false;
            return;
        }
        if(!item->m_layout_dirty && doc)
            doc->note_layout_dirty(item);
        item->m_layout_dirty      = true;
        item->m_last_layout.valid = false;
        item->m_measured[0].valid = false;
        item->m_measured[1].valid = false;
        auto parent = item->m_parent.lock();
        item        = parent.get();
    }
}

litehtml::rendered_width litehtml::render_item::render(pixel_t x, pixel_t y,
                                                       const containing_block_context& containing_block_size,
                                                       formatting_context* fmt_ctx, bool second_pass)
{
    const bool cacheable = layout_cacheable(fmt_ctx);
    document*  doc       = src_el()->doc_ptr();
    if(doc && m_layout_epoch != doc->layout_epoch())
    {
        m_measured[0].valid = false;
        m_measured[1].valid = false;
    }
    if(cacheable && layout_reusable() &&
       (same_layout_input(m_last_layout, containing_block_size, second_pass) ||
        same_resolved_layout_input(m_last_layout, containing_block_size, second_pass)))
    {
        m_pos     = m_last_pos;
        m_margins = m_last_margins;
        m_padding = m_last_padding;
        m_borders = m_last_borders;
        if(x != m_last_x || y != m_last_y)
        {
            m_pos.x = x + content_offset_left();
            m_pos.y = y + content_offset_top();
        }
        doc->note_layout_hit();
        if(!doc->layout_cache_check())
            return m_last_layout.ret;
        const position       cached_pos = m_pos;
        const rendered_width cached     = m_last_layout.ret;
        m_layout_dirty                  = true;
        rendered_width fresh            = render(x, y, containing_block_size, fmt_ctx, second_pass);
        if(fresh.natural_width != cached.natural_width || fresh.min_width != cached.min_width || !(m_pos == cached_pos))
            doc->note_layout_check_mismatch();
        return fresh;
    }
    if(cacheable && doc)
        doc->note_layout_miss();

    m_resolved_layout_safe = -1;
    calc_outlines(containing_block_size.width);

    m_pos.clear();
    m_pos.move_to(x, y);

    pixel_t content_left = content_offset_left();
    pixel_t content_top  = content_offset_top();

    m_pos.x += content_left;
    m_pos.y += content_top;

    rendered_width ret;
    if(src_el()->is_block_formatting_context() || (fmt_ctx == nullptr))
    {
        formatting_context fmt;
        ret = _render(x, y, containing_block_size, &fmt, second_pass);
        fmt.apply_relative_shift(containing_block_size);
    } else
    {
        fmt_ctx->push_position(x + content_left, y + content_top);
        ret = _render(x, y, containing_block_size, fmt_ctx, second_pass);
        fmt_ctx->pop_position(x + content_left, y + content_top);
    }
    m_layout_dirty = false;
    if(cacheable && doc)
    {
        m_layout_epoch = doc->layout_epoch();
        m_last_layout  = {containing_block_size, ret, second_pass, true, height()};
        m_last_pos     = m_pos;
        m_last_x       = x;
        m_last_y       = y;
        m_last_margins = m_margins;
        m_last_padding = m_padding;
        m_last_borders = m_borders;
    } else
        m_last_layout.valid = false;
    return ret;
}

litehtml::rendered_width litehtml::render_item::measure(pixel_t x, pixel_t y,
                                                        const containing_block_context& containing_block_size,
                                                        formatting_context* fmt_ctx, bool second_pass, pixel_t* measured_height)
{
    const bool cacheable = layout_cacheable(fmt_ctx);
    document*  doc       = src_el()->doc_ptr();
    if(cacheable && layout_reusable() && !doc->layout_cache_check())
    {
        if(same_layout_input(m_last_layout, containing_block_size, second_pass))
        {
            doc->note_layout_hit();
            if(measured_height)
                *measured_height = m_last_layout.outer_height;
            return m_last_layout.ret;
        }
        for(const auto& entry : m_measured)
        {
            if(same_layout_input(entry, containing_block_size, second_pass))
            {
                doc->note_layout_hit();
                if(measured_height)
                    *measured_height = entry.outer_height;
                return entry.ret;
            }
        }
    }
    rendered_width ret = render(x, y, containing_block_size, fmt_ctx, second_pass);
    if(measured_height)
        *measured_height = height();
    if(cacheable)
    {
        m_measured[m_measured_next] = {containing_block_size, ret, second_pass, true, height()};
        m_measured_next ^= 1;
    }
    return ret;
}

litehtml::pixel_t litehtml::render_item::measure_height(pixel_t x, pixel_t y,
                                                        const containing_block_context& containing_block_size,
                                                        formatting_context* fmt_ctx, bool second_pass)
{
    pixel_t measured_height = 0_px;
    measure(x, y, containing_block_size, fmt_ctx, second_pass, &measured_height);
    return measured_height;
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
    if(is_one_of(src_el()->css().get_display(), display_block, display_table, display_flex, display_grid, display_list_item) &&
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
    bool ret      = false;
    bool replaced = false;

    m_positioned.clear();

    for(auto& el : m_children)
    {
        replaced = replaced || el->src_el()->has_external_intrinsic_size();
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
        replaced = replaced || el->m_out_of_flow;
    }
    m_out_of_flow = ret || replaced || src_el()->has_external_intrinsic_size() ||
                    (css().get_display() == display_list_item && !css().get_list_style_image().empty());
    return ret;
}

void litehtml::render_item::render_out_of_flow(pixel_t y, const containing_block_context& self_size,
                                               formatting_context* fmt_ctx)
{
    rendered_width rw = render(0_px, y, self_size, fmt_ctx);
    if(!src_el()->css().get_width().is_predefined())
    {
        return;
    }
    if(rw.natural_width < width())
    {
        render(0_px, y, self_size.new_width(rw.natural_width), fmt_ctx);
    } else if(rw.min_width > width())
    {
        // A zero-width parent (a 'left: 0; right: 0' box before it is placed) still fits the words.
        render(0_px, y, self_size.new_width(rw.min_width), fmt_ctx);
    }
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

            if(is_one_of(el->css().get_display(), display_flex, display_inline_flex) &&
               (el_width.is_predefined() || el_height.is_predefined()))
            {
                pixel_t available_width = containing_block_size.width;
                if(!css_left.is_predefined())
                    available_width -= css_left.calc_percent(containing_block_size.width);
                if(!css_right.is_predefined())
                    available_width -= css_right.calc_percent(containing_block_size.width);
                available_width = std::max(0_px, available_width);
                auto measured = el->render(el->left(), el->top(),
                                           containing_block_size.new_width(available_width), nullptr);
                if(el_width.is_predefined() && (css_left.is_predefined() || css_right.is_predefined()))
                {
                    pixel_t fitted_width = std::min(std::max(measured.min_width, available_width), measured.natural_width);
                    if(fitted_width != el->width())
                        el->render(el->left(), el->top(), containing_block_size.new_width(fitted_width), nullptr);
                }
            }

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
                if(el->css().get_display() == display_table || el->src_el()->is_replaced())
                {
                    el->render(el->left(), el->top(), containing_block_size.new_width(el->width()), nullptr, true);
                } else
                {
                    el->render(el->left(), el->top(),
                               containing_block_size.new_width_height(
                                   pos.width + el->box_sizing_width(), pos.height + el->box_sizing_height(),
                                   containing_block_context::size_mode_exact_width |
                                       containing_block_context::size_mode_exact_height),
                               nullptr, true);
                }
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

    // A transformed or contained box holds the positioned elements inside it (add_positioned) but is
    // on no m_positioned list unless it is positioned itself, so nothing else would lay them out.
    std::function<void(render_item*)> holders = [&](render_item* item) {
        for(const auto& child : item->m_children)
        {
            if(child->src_el()->css().get_position() != element_position_static)
                continue;
            if(!child->m_positioned.empty())
                child->render_positioned(rt);
            else
                holders(child.get());
        }
    };
    holders(this);

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
    if(src_el()->css().get_position() != element_position_static || is_root() ||
       !src_el()->css().get_transform().empty() || src_el()->css().contains(contain_layout | contain_paint))
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

        if(!src_el()->css().clips_contents())
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

            if(is_one_of(src_el()->css().get_overflow(), overflow_scroll, overflow_auto, overflow_hidden))
            {
                size child_size;
                auto text = src_el()->tag() == _textarea_ ? dynamic_cast<render_item_inline_context*>(this) : nullptr;
                if(text)
                {
                    child_size = text->text_content_size();
                } else
                {
                    for(const auto& el : m_children)
                    {
                        el->calc_document_size(child_size, 0_px, 0_px);
                    }
                }
                position padding_box = m_pos;
                padding_box         += m_padding;
                size scroll_size(child_size.width + m_padding.left + m_padding.right, child_size.height + m_padding.top + m_padding.bottom);
                if(!m_scroll_view)
                {
                    m_scroll_view = std::make_shared<scroll_view>(padding_box, scroll_size);
                } else
                {
                    m_scroll_view->set(padding_box, scroll_size);
                }
            } else
            {
                // All children of tables and blocks with style other than "overflow: visible" are inside element.
                // We can skip calculating the size of children
                if(!src_el()->css().clips_contents() && src_el()->css().get_display() != display_table)
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

static litehtml::transform_matrix group_transform(const litehtml::css_properties& el_css, const litehtml::position& border_box)
{
    using namespace litehtml;
    pixel_t          ox = border_box.x + el_css.get_transform_origin_x().calc_percent(border_box.width);
    pixel_t          oy = border_box.y + el_css.get_transform_origin_y().calc_percent(border_box.height);
    transform_matrix m{1, 0, 0, 1, -ox.value(), -oy.value()};
    const auto&      functions = el_css.get_transform();
    for(auto it = functions.rbegin(); it != functions.rend(); ++it)
    {
        transform_matrix f;
        switch(it->kind)
        {
        case transform_function::translate:
            f.tx = it->x.calc_percent(border_box.width).value();
            f.ty = it->y.calc_percent(border_box.height).value();
            break;
        case transform_function::scale:
            f.a = it->v[0];
            f.d = it->v[1];
            break;
        case transform_function::rotate:
            f.a = std::cos(it->v[0]);
            f.b = std::sin(it->v[0]);
            f.c = -f.b;
            f.d = f.a;
            break;
        case transform_function::skew:
            f.b = std::tan(it->v[1]);
            f.c = std::tan(it->v[0]);
            break;
        case transform_function::matrix:
            f = {it->v[0], it->v[1], it->v[2], it->v[3], it->v[4], it->v[5]};
            break;
        case transform_function::rotate_x:
            f.d = std::cos(it->v[0]);
            break;
        case transform_function::rotate_y:
            f.a = std::cos(it->v[0]);
            break;
        }
        m = m.then(f);
    }
    return m.then(transform_matrix{1, 0, 0, 1, ox.value(), oy.value()});
}

litehtml::transform_matrix litehtml::render_item::get_visual_transform() const
{
    if(css().get_transform().empty() || (src_el()->is_inline() && !src_el()->is_inline_box() && !src_el()->is_replaced()))
        return {};
    position box = get_placement();
    box += m_padding;
    box += m_borders;
    return group_transform(css(), box);
}

static float shadow_reach(const litehtml::box_shadow_vector& shadows)
{
    float reach = 0;
    for(const auto& shadow : shadows)
    {
        if(!shadow.inset)
        {
            reach = std::max(reach, std::fabs(shadow.blur.val()) * 2 + std::fabs(shadow.spread.val()) +
                                        std::max(std::fabs(shadow.x.val()), std::fabs(shadow.y.val())));
        }
    }
    return reach;
}

void litehtml::render_item::add_self_ink(paint_ink& ink, float dx, float dy) const
{
    constexpr float slack = 2;
    const auto&     el_css = src_el()->css();
    position        box    = m_pos;
    box.x                 += dx;
    box.y                 += dy;
    if(src_el()->is_text())
    {
        float reach = std::max(4.f, box.height.value() / 2) + slack;
        if(auto parent_el = src_el()->parent())
        {
            reach += shadow_reach(parent_el->css().get_text_shadows());
        }
        ink.add(box, reach);
    }
    box         += m_padding;
    box         += m_borders;
    float reach  = shadow_reach(el_css.get_box_shadows());
    float stroke = el_css.get_outline().width.val() + std::max(0.f, el_css.get_outline_offset().value());
    if(stroke > 0)
    {
        reach += stroke;
    }
    ink.add(box, reach + slack);
    for_inline_boxes([&](const position& inline_box, bool, bool) {
        position shifted  = inline_box;
        shifted.x        += dx;
        shifted.y        += dy;
        ink.add(shifted, reach + slack);
        return true;
    });
}

void litehtml::render_item::add_children_ink(uint32_t generation, paint_ink& ink)
{
    const float dx = (m_pos.x - get_scroll_left()).value();
    const float dy = (m_pos.y - get_scroll_top()).value();
    for(const auto& el : m_children)
    {
        ink.add(el->ink(generation, true), dx, dy);
        if(ink.unbounded)
        {
            return;
        }
    }
}

const litehtml::paint_ink& litehtml::render_item::ink(uint32_t generation, bool drawn)
{
    if(m_ink_generation != generation)
    {
        m_ink_generation = generation;
        m_ink            = paint_ink();
        add_self_ink(m_ink, 0, 0);
        add_children_ink(generation, m_ink);
        const auto& el_css = src_el()->css();
        if(!el_css.get_filter().empty() && !m_ink.empty && !m_ink.unbounded)
        {
            const float reach = filter_reach(el_css.get_filter());
            m_ink.add(m_ink.l - reach, m_ink.t - reach, m_ink.r + reach, m_ink.b + reach);
        }
        if(el_css.get_position() == element_position_fixed || el_css.get_position() == element_position_sticky)
        {
            m_ink.unbounded = true;
        }
        if(!m_ink.unbounded && !m_ink.empty && el_css.get_display() == display_list_item &&
           (el_css.get_list_style_type() != list_style_type_none || !el_css.get_list_style_image().empty()))
        {
            if(!el_css.get_list_style_image().empty())
            {
                m_ink.unbounded = true;
            } else
            {
                const float font = std::max(el_css.get_font_size().value(), el_css.get_font_metrics().height.value());
                const float pad  = std::max(font, el_css.line_height().computed_value.value()) * 2 + 4;
                m_ink.add(-INFINITY, m_ink.t - pad, m_pos.x.value() + font + 4, m_ink.b + pad);
            }
        }
        m_drawn_ink = m_ink;
        if(!m_ink.unbounded && !m_ink.empty && !el_css.get_transform().empty())
        {
            position border_box  = m_pos;
            border_box          += m_padding;
            border_box          += m_borders;
            const auto  m        = group_transform(el_css, border_box);
            const float xs[2]    = {m_ink.l, m_ink.r};
            const float ys[2]    = {m_ink.t, m_ink.b};
            m_drawn_ink          = paint_ink();
            for(float x : xs)
            {
                for(float y : ys)
                {
                    const float px = m.a * x + m.c * y + m.tx;
                    const float py = m.b * x + m.d * y + m.ty;
                    if(!std::isfinite(px) || !std::isfinite(py))
                    {
                        m_drawn_ink.unbounded = true;
                    }
                    m_drawn_ink.add(px, py, px, py);
                }
            }
        }
    }
    return drawn ? m_drawn_ink : m_ink;
}

namespace
{
    // The z-indexes a paint group draws as its own stacking context: its positioned descendants, short of
    // those inside another positioned element or paint group, which draw their own. A group that is not
    // positioned keeps none of them in m_positioned (add_positioned files them with their containing block),
    // so they are found in the tree.
    void group_z_indexes(const std::shared_ptr<litehtml::render_item>& item, std::set<int>& z)
    {
        for(const auto& child : item->children())
        {
            if(child->src_el()->is_positioned())
                z.insert(child->src_el()->css().get_z_index());
            else if(!child->src_el()->css().is_paint_group())
                group_z_indexes(child, z);
        }
    }

    void draw_group_contents(const std::shared_ptr<litehtml::render_item>& el, litehtml::uint_ptr hdc, litehtml::pixel_t x,
                             litehtml::pixel_t y, const litehtml::position* clip)
    {
        if(!el->is_visible())
            return;
        std::set<int> z;
        group_z_indexes(el, z);
        for(int index : z)
            if(index < 0)
                el->draw_children(hdc, x, y, clip, litehtml::draw_positioned, index);
        el->draw_children(hdc, x, y, clip, litehtml::draw_block, 0);
        el->draw_children(hdc, x, y, clip, litehtml::draw_floats, 0);
        el->draw_children(hdc, x, y, clip, litehtml::draw_inlines, 0);
        for(int index : z)
            if(index >= 0)
                el->draw_children(hdc, x, y, clip, litehtml::draw_positioned, index);
    }
}

void litehtml::render_item::draw_group(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                       const std::shared_ptr<render_item>& el)
{
    const auto& el_css     = el->src_el()->css();
    const auto sticky = el->sticky_offset();
    x += sticky.x;
    y += sticky.y;
    position    border_box = el->pos();
    border_box.x          += x;
    border_box.y          += y;
    border_box            += el->get_paddings();
    border_box            += el->get_borders();

    paint_group group;
    group.opacity         = el_css.get_opacity();
    group.backface_hidden = el_css.get_backface_hidden();
    group.owner           = el->src_el().get();
    group.bounds          = border_box;
    el->get_redraw_box(group.bounds, x, y);
    if(group.opacity < 1)
    {
        const auto& groupInk = el->ink(src_el()->doc_ptr()->paint_generation(), false);
        if(!groupInk.empty && !groupInk.unbounded)
        {
            const float l = std::min(float(group.bounds.left()), groupInk.l + float(x));
            const float t = std::min(float(group.bounds.top()), groupInk.t + float(y));
            const float r = std::max(float(group.bounds.right()), groupInk.r + float(x));
            const float b = std::max(float(group.bounds.bottom()), groupInk.b + float(y));
            group.bounds       = position(pixel_t(l), pixel_t(t), pixel_t(r - l), pixel_t(b - t));
            group.exact_bounds = true;
        }
    }

    if(!el_css.get_transform().empty())
    {
        group.matrix = group_transform(el_css, border_box);
    }

    const auto& filter   = el_css.get_filter();
    const auto& backdrop = el_css.get_backdrop_filter();
    const bool  bare     = src_el()->doc_ptr()->bare_effects == el->src_el().get();
    if(!filter.empty() && !bare)
    {
        const float reach  = filter_reach(filter);
        group.bounds.x      -= reach;
        group.bounds.y      -= reach;
        group.bounds.width  += reach * 2;
        group.bounds.height += reach * 2;
    }

    auto container = src_el()->doc_ptr()->container();
    container->begin_group(hdc, group);
    const bool shaped = !el_css.get_clip_path().empty();
    if(shaped) container->set_clip_path(el_css.get_clip_path().resolve(border_box));
    const position* inner_clip = group.matrix.is_identity() ? clip : nullptr;
    position transformed_clip;
    static const bool transformed_culling = getenv("LH_NO_TRANSFORMED_CULLING") == nullptr;
    if(transformed_culling && clip && !inner_clip && src_el()->doc_ptr()->culls_paint() && filter.empty() && backdrop.empty())
    {
        const auto& m = group.matrix;
        const double determinant = double(m.a) * m.d - double(m.b) * m.c;
        if(std::isfinite(determinant) && std::abs(determinant) > 1e-8)
        {
            double left = INFINITY, top = INFINITY, right = -INFINITY, bottom = -INFINITY;
            for(double cx : {double(clip->left()) - 1, double(clip->right()) + 1})
            {
                for(double cy : {double(clip->top()) - 1, double(clip->bottom()) + 1})
                {
                    const double px = (m.d * (cx - m.tx) - m.c * (cy - m.ty)) / determinant;
                    const double py = (m.a * (cy - m.ty) - m.b * (cx - m.tx)) / determinant;
                    left = std::min(left, px);
                    top = std::min(top, py);
                    right = std::max(right, px);
                    bottom = std::max(bottom, py);
                }
            }
            if(std::isfinite(left) && std::isfinite(top) && std::isfinite(right) && std::isfinite(bottom) &&
               std::abs(left) < 1e6 && std::abs(top) < 1e6 && std::abs(right) < 1e6 && std::abs(bottom) < 1e6)
            {
                left = std::floor(left);
                top = std::floor(top);
                transformed_clip = position(float(left), float(top), float(std::ceil(right) - left), float(std::ceil(bottom) - top));
                inner_clip = &transformed_clip;
            }
        }
    }
    if(g_paint_trace) g_paint_trace->push_back(el.get());
    if(!backdrop.empty() && !bare)
    {
        filter_paint paint;
        paint.steps  = &backdrop;
        paint.box    = border_box;
        paint.bounds = border_box;
        paint.radius = el_css.get_borders().radius.calc_percents(border_box.width, border_box.height);
        paint.radius.fix_values(border_box.width, border_box.height);
        paint.owner  = el->src_el().get();
        container->draw_backdrop(hdc, paint);
    }
    if(!filter.empty() && !bare)
    {
        const auto& ink = el->ink(src_el()->doc_ptr()->paint_generation(), false);
        filter_paint paint;
        paint.steps  = &filter;
        paint.owner  = el->src_el().get();
        paint.box    = border_box;
        paint.bounds = border_box;
        if(!ink.empty && !ink.unbounded)
        {
            paint.bounds = position(pixel_t(ink.l) + x, pixel_t(ink.t) + y, pixel_t(ink.r - ink.l), pixel_t(ink.b - ink.t));
        }
        container->draw_filtered(hdc, paint, [&](uint_ptr target) {
            el->src_el()->draw(target, x, y, nullptr, el);
            draw_group_contents(el, target, x, y, nullptr);
        });
    } else
    {
        el->src_el()->draw(hdc, x, y, inner_clip, el);
        draw_group_contents(el, hdc, x, y, inner_clip);
    }
    if(shaped) container->del_clip();
    container->end_group(hdc);
}

void litehtml::render_item::draw_children(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, draw_flag flag,
                                          int zindex)
{
    document* doc = src_el()->doc_ptr();
    if(!doc->skip_scroll_content.empty() && doc->skip_scroll_content.count(src_el().get()))
    {
        return;
    }

    position pos  = m_pos;
    pos.x        += x - get_scroll_left();
    pos.y        += y - get_scroll_top();

    const bool clips = src_el()->css().clips_contents() && src_el().get() != doc->unclipped_scroll;
    if(clips)
    {
        // TODO: Process overflow for inline elements
        if(src_el()->css().get_display() != display_inline)
        {
            position clip_box    = m_pos;
            clip_box.x          += x;
            clip_box.y          += y;
            clip_box            += m_padding;
            position border_box  = clip_box;
            border_box          += m_borders;

            border_radiuses bdr_radius =
                src_el()->css().get_borders().radius.calc_percents(border_box.width, border_box.height);

            bdr_radius -= m_borders;

            doc->container()->set_clip(clip_box, bdr_radius);
        }
    }

    const bool     cull       = clip && doc->culls_paint();
    const uint32_t generation = doc->paint_generation();
    for(const auto& el : m_children)
    {
        if(!doc->skip_paint.empty() && doc->skip_paint.count(el->src_el().get()))
            continue;
        if(cull && el->ink(generation, true).misses(*clip, pos.x.value(), pos.y.value()))
            continue;
        if(el->is_visible())
        {
            if(el->src_el()->css().is_paint_group())
            {
                const auto& el_css = el->src_el()->css();
                bool        positioned = el->src_el()->is_positioned();
                bool        own_pass   = false;
                switch(flag)
                {
                case draw_positioned:
                    own_pass = positioned && el_css.get_z_index() == zindex;
                    break;
                case draw_block:
                    own_pass = !positioned && !el->src_el()->is_inline() && el_css.get_float() == float_none;
                    break;
                case draw_floats:
                    own_pass = !positioned && el_css.get_float() != float_none;
                    break;
                case draw_inlines:
                    own_pass = !positioned && el->src_el()->is_inline() && el_css.get_float() == float_none;
                    break;
                default:
                    break;
                }
                if(own_pass)
                {
                    bool fixed = el_css.get_position() == element_position_fixed;
                    const bool escape = fixed && !inside_paint_group(el);
                    if(escape)
                        doc->container()->suspend_clips();
                    draw_group(hdc, fixed ? 0_px : pos.x, fixed ? 0_px : pos.y, clip, el);
                    if(escape)
                        doc->container()->resume_clips();
                }
                // The group draws its positioned descendants itself, inside its opacity, filter and transform.
                continue;
            }
            bool process = true;
            switch(flag)
            {
            case draw_positioned:
                if(el->src_el()->is_positioned() && el->src_el()->css().get_z_index() == zindex)
                {
                    if(el->src_el()->css().get_position() == element_position_fixed)
                    {
                        // Fixed elements position is always relative to the (0,0)
                        if(g_paint_trace) g_paint_trace->push_back(el.get());
                        const bool escape = !inside_paint_group(el);
                        if(escape)
                            doc->container()->suspend_clips();
                        el->src_el()->draw(hdc, 0_px, 0_px, clip, el);
                        el->draw_stacking_context(hdc, 0_px, 0_px, clip, true);
                        if(escape)
                            doc->container()->resume_clips();
                    } else
                    {
                        if(g_paint_trace) g_paint_trace->push_back(el.get());
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
                    if(g_paint_trace) g_paint_trace->push_back(el.get());
                    el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                }
                break;
            case draw_floats:
                if(el->src_el()->css().get_float() != float_none && !el->src_el()->is_positioned())
                {
                    if(g_paint_trace) g_paint_trace->push_back(el.get());
                    el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                    el->draw_stacking_context(hdc, pos.x, pos.y, clip, false);
                    process = false;
                }
                break;
            case draw_inlines:
                if(el->src_el()->is_inline() && el->src_el()->css().get_float() == float_none &&
                   !el->src_el()->is_positioned())
                {
                    if(g_paint_trace) g_paint_trace->push_back(el.get());
                    el->src_el()->draw(hdc, pos.x, pos.y, clip, el);
                    if(el->src_el()->css().get_display() == display_inline_block ||
                       el->src_el()->css().get_display() == display_inline_flex ||
                       el->src_el()->css().get_display() == display_inline_grid)
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

    if(clips)
    {
        doc->container()->del_clip();
    }
}

bool litehtml::render_item::inside_paint_group(const std::shared_ptr<render_item>& el)
{
    for(auto p = el->parent(); p; p = p->parent())
    {
        if(p->src_el()->css().is_paint_group())
            return true;
    }
    return false;
}

std::shared_ptr<litehtml::element> litehtml::render_item::get_child_by_point(
    pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y, draw_flag flag, int zindex,
    const std::function<bool(const std::shared_ptr<render_item>&)>& check)
{
    if(!css().get_clip_path().empty())
    {
        position box = m_pos;
        box += m_padding;
        box += m_borders;
        if(!src_el()->doc_ptr()->container()->point_in_clip_path(css().get_clip_path().resolve(box), x, y)) return nullptr;
    }
    if(src_el()->css().clips_contents())
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
            position hit_pos = el_pos;
            pixel_t hit_client_x = client_x, hit_client_y = client_y;
            if(!el->css().get_transform().empty())
            {
                position box = el->m_pos;
                box += el->m_padding;
                box += el->m_borders;
                const auto matrix = group_transform(el->css(), box);
                const float determinant = matrix.a * matrix.d - matrix.b * matrix.c;
                if(std::fabs(determinant) < 1e-12f) continue;
                auto inverse = [&](pixel_t& px, pixel_t& py) {
                    const float dx = px.value() - matrix.tx, dy = py.value() - matrix.ty;
                    px = pixel_t((matrix.d * dx - matrix.c * dy) / determinant);
                    py = pixel_t((matrix.a * dy - matrix.b * dx) / determinant);
                };
                inverse(hit_pos.x, hit_pos.y);
                if(el->css().get_position() == element_position_fixed) inverse(hit_client_x, hit_client_y);
            }
            bool process = true;
            switch(flag)
            {
            case draw_positioned:
                if(el->src_el()->is_positioned() && el->src_el()->css().get_z_index() == zindex)
                {
                    if(el->src_el()->css().get_position() == element_position_fixed)
                    {
                        ret = el->get_element_by_point(hit_client_x, hit_client_y, hit_client_x, hit_client_y, check);
                        if(!ret && el->is_point_inside(hit_client_x, hit_client_y))
                        {
                            if(!check || check(el))
                            {
                                ret = el->src_el();
                            }
                        }
                    } else
                    {
                        ret = el->get_element_by_point(hit_pos.x, hit_pos.y, hit_client_x, hit_client_y, check);
                        if(!ret && el->is_point_inside(hit_pos.x, hit_pos.y))
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
                    ret = el->get_child_by_point(hit_pos.x, hit_pos.y, hit_client_x, hit_client_y, flag, zindex, check);

                    if(!ret && el->is_point_inside(hit_pos.x, hit_pos.y))
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
                    ret = el->get_element_by_point(hit_pos.x, hit_pos.y, hit_client_x, hit_client_y, check);

                    if(!ret && el->is_point_inside(hit_pos.x, hit_pos.y))
                    {
                        if(!check || check(el))
                        {
                            ret = el->src_el();
                        }
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
                       el->src_el()->css().get_display() == display_inline_flex ||
                       el->src_el()->css().get_display() == display_inline_grid)
                    {
                        ret     = el->get_element_by_point(hit_pos.x, hit_pos.y, hit_client_x, hit_client_y, check);
                        process = false;
                    }
                    if(!ret && el->is_point_inside(hit_pos.x, hit_pos.y))
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
                        el->get_child_by_point(hit_pos.x, hit_pos.y, hit_client_x, hit_client_y, flag, zindex, check);
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
                       el->src_el()->css().get_display() != display_inline_flex &&
                       el->src_el()->css().get_display() != display_inline_grid)
                    {
                        element::ptr child =
                            el->get_child_by_point(hit_pos.x, hit_pos.y, hit_client_x, hit_client_y, flag, zindex, check);
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

    const auto sticky = sticky_offset();
    x -= sticky.x;
    y -= sticky.y;
    if(!css().get_clip_path().empty())
    {
        position box = m_pos;
        box += m_padding;
        box += m_borders;
        if(!src_el()->doc_ptr()->container()->point_in_clip_path(css().get_clip_path().resolve(box), x, y)) return nullptr;
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
        if(is_point_inside(x + sticky.x, y + sticky.y))
        {
            if(!check || check(this->shared_from_this()))
            {
                ret = src_el();
            }
        }
    }

    return ret;
}

litehtml::position litehtml::render_item::sticky_offset() const
{
    position shift;
    if(css().get_position() != element_position_sticky) return shift;
    auto parent_item = parent();
    if(!parent_item) return shift;
    auto scroller = parent_item;
    pixel_t local_x = m_pos.x, local_y = m_pos.y;
    while(scroller && is_one_of(scroller->css().get_overflow(), overflow_visible, overflow_clip))
    {
        local_x += scroller->m_pos.x;
        local_y += scroller->m_pos.y;
        scroller = scroller->parent();
    }
    if(!scroller) return shift;
    position viewport = scroller->m_pos;
    viewport += scroller->m_padding;
    const auto& offsets = css().get_offsets();
    auto axis = [](pixel_t start, pixel_t size, pixel_t scroll, pixel_t viewport_start, pixel_t viewport_size,
                   pixel_t parent_start, pixel_t parent_size, const css_length& before, const css_length& after) {
        if(before.is_predefined() && after.is_predefined()) return 0_px;
        pixel_t delta = 0_px;
        if(!after.is_predefined())
            delta = std::min(delta, scroll + viewport_start + viewport_size - after.calc_percent(viewport_size) - start - size);
        if(!before.is_predefined())
            delta = std::max(delta, scroll + viewport_start + before.calc_percent(viewport_size) - start);
        return std::max(std::min(0_px, -parent_start), std::min(delta, std::max(0_px, parent_size - parent_start - size)));
    };
    const pixel_t left = m_pos.x - m_padding.left - m_borders.left;
    const pixel_t top = m_pos.y - m_padding.top - m_borders.top;
    pixel_t parent_width = parent_item->m_pos.width;
    pixel_t parent_height = parent_item->m_pos.height;
    if(const auto* view = parent_item->get_scroll_view())
    {
        parent_width = std::max(parent_width, view->get_scroll_size().width - parent_item->m_padding.width());
        parent_height = std::max(parent_height, view->get_scroll_size().height - parent_item->m_padding.height());
    }
    shift.x = axis(local_x - m_padding.left - m_borders.left, m_pos.width + m_padding.width() + m_borders.width(),
                   scroller->get_scroll_left(), -scroller->m_padding.left, viewport.width,
                   left, parent_width, offsets.left, offsets.right);
    shift.y = axis(local_y - m_padding.top - m_borders.top, m_pos.height + m_padding.height() + m_borders.height(),
                   scroller->get_scroll_top(), -scroller->m_padding.top, viewport.height,
                   top, parent_height, offsets.top, offsets.bottom);
    return shift;
}

bool litehtml::render_item::is_point_inside(pixel_t x, pixel_t y) const
{
    position pos = m_pos;
    const auto sticky = sticky_offset();
    x -= sticky.x;
    y -= sticky.y;
    pos += m_padding;
    pos += m_borders;
    if(!css().get_clip_path().empty() && !src_el()->doc_ptr()->container()->point_in_clip_path(css().get_clip_path().resolve(pos), x, y)) return false;
    return pos.is_point_inside(x, y);
}

void litehtml::render_item::get_rendering_boxes(const std::function<void(const position&)>& redraw_box) const
{
    const auto sticky = sticky_offset();
    pixel_t add_x = sticky.x;
    pixel_t add_y = sticky.y;

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
            const auto parent_sticky = cur_el->sticky_offset();
            add_x  += cur_el->m_pos.x - cur_el->get_scroll_left() + parent_sticky.x;
            add_y  += cur_el->m_pos.y - cur_el->get_scroll_top() + parent_sticky.y;
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
    const auto sticky = sticky_offset();
    pos.x += sticky.x;
    pos.y += sticky.y;
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
        // A calc length still has a % part after unit conversion: it depends on the base as one does.
        if(len.units() == litehtml::css_units_percentage || len.units() == litehtml::css_units_calc)
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
        if((cb_context.size_mode & containing_block_context::size_mode_resolved_width) != 0 ||
           (par && (par->css().get_display() == display_flex || par->css().get_display() == display_inline_flex) &&
           (par->css().get_flex_direction() == flex_direction_row ||
            par->css().get_flex_direction() == flex_direction_row_reverse)))
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
