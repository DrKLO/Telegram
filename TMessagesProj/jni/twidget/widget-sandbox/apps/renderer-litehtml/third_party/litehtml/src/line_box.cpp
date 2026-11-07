#include "html.h"
#include "line_box.h"
#include "element.h"
#include "render_item.h"
#include "types.h"
#include <algorithm>

//////////////////////////////////////////////////////////////////////////////////////////

litehtml::line_box_item::~line_box_item() = default;

void litehtml::line_box_item::place_to(pixel_t x, pixel_t y)
{
    m_element->pos().x = x + m_element->content_offset_left();
    m_element->pos().y = y + m_element->content_offset_top();
}

litehtml::position& litehtml::line_box_item::pos()
{
    return m_element->pos();
}

litehtml::pixel_t litehtml::line_box_item::width() const
{
    return m_element->width();
}

litehtml::pixel_t litehtml::line_box_item::top() const
{
    return m_element->top();
}

litehtml::pixel_t litehtml::line_box_item::bottom() const
{
    return m_element->bottom();
}

litehtml::pixel_t litehtml::line_box_item::right() const
{
    return m_element->right();
}

litehtml::pixel_t litehtml::line_box_item::left() const
{
    return m_element->left();
}

litehtml::pixel_t litehtml::line_box_item::height() const
{
    return m_element->height();
}

void litehtml::line_box_item::y_shift(pixel_t shift)
{
    m_element->y_shift(shift);
}

//////////////////////////////////////////////////////////////////////////////////////////

litehtml::lbi_start::lbi_start(const std::shared_ptr<render_item>& element) :
    line_box_item(element)
{
    m_pos.height = m_element->src_el()->css().get_font_metrics().height;
    m_pos.width  = m_element->content_offset_left();
}

litehtml::lbi_start::~lbi_start() = default;

void litehtml::lbi_start::place_to(pixel_t x, pixel_t y)
{
    m_pos.x = x + m_element->content_offset_left();
    m_pos.y = y;
}

litehtml::pixel_t litehtml::lbi_start::width() const
{
    return m_pos.width;
}

litehtml::pixel_t litehtml::lbi_start::top() const
{
    return m_pos.y;
}

litehtml::pixel_t litehtml::lbi_start::bottom() const
{
    return m_pos.y + m_pos.height;
}

litehtml::pixel_t litehtml::lbi_start::right() const
{
    return m_pos.x;
}

litehtml::pixel_t litehtml::lbi_start::left() const
{
    return m_pos.x - m_element->content_offset_left();
}

litehtml::pixel_t litehtml::lbi_start::height() const
{
    return m_pos.height;
}

//////////////////////////////////////////////////////////////////////////////////////////

litehtml::lbi_end::lbi_end(const std::shared_ptr<render_item>& element) :
    lbi_start(element)
{
    m_pos.height = m_element->src_el()->css().get_font_metrics().height;
    m_pos.width  = m_element->content_offset_right();
}

litehtml::lbi_end::~lbi_end() = default;

void litehtml::lbi_end::place_to(pixel_t x, pixel_t y)
{
    m_pos.x = x;
    m_pos.y = y;
}

litehtml::pixel_t litehtml::lbi_end::right() const
{
    return m_pos.x + m_pos.width;
}

litehtml::pixel_t litehtml::lbi_end::left() const
{
    return m_pos.x;
}

//////////////////////////////////////////////////////////////////////////////////////////

litehtml::lbi_continue::lbi_continue(const std::shared_ptr<render_item>& element) :
    lbi_start(element)
{
    m_pos.height = m_element->src_el()->css().get_font_metrics().height;
    m_pos.width  = 0;
}

litehtml::lbi_continue::~lbi_continue() = default;

void litehtml::lbi_continue::place_to(pixel_t x, pixel_t y)
{
    m_pos.x = x;
    m_pos.y = y;
}

litehtml::pixel_t litehtml::lbi_continue::right() const
{
    return m_pos.x;
}

litehtml::pixel_t litehtml::lbi_continue::left() const
{
    return m_pos.x;
}

litehtml::pixel_t litehtml::lbi_continue::width() const
{
    return 0_px;
}

//////////////////////////////////////////////////////////////////////////////////////////

void litehtml::line_box::add_item(std::unique_ptr<line_box_item> item)
{
    item->get_el()->skip(false);
    bool add = true;
    switch(item->get_type())
    {
    case line_box_item::type_text_part:
        if(item->get_el()->src_el()->is_white_space())
        {
            add = !is_empty() && !have_last_space();
        }
        break;
    case line_box_item::type_inline_start:
    case line_box_item::type_inline_end:
    case line_box_item::type_inline_continue:
        add = true;
        break;
    }
    if(add)
    {
        item->place_to(m_left + m_width, m_top);
        m_width  += item->width();
        m_height  = std::max(m_height, item->get_el()->height());
        m_items.emplace_back(std::move(item));
    } else
    {
        item->get_el()->skip(true);
    }
}

litehtml::pixel_t litehtml::line_box::calc_va_baseline(const va_context& current, vertical_align va,
                                                       const font_metrics& new_font, pixel_t top, pixel_t bottom)
{
    switch(va)
    {
    case va_super:
        return current.baseline - current.fm.super_shift;
    case va_sub:
        return current.baseline + current.fm.sub_shift;
    case va_middle:
        return current.baseline - current.fm.x_height / 2_px;
    case va_text_top:
        return current.baseline - (current.fm.height - current.fm.base_line()) + new_font.height - new_font.base_line();
    case va_text_bottom:
        return current.baseline + current.fm.base_line() - new_font.base_line();
    case va_bottom:
        return bottom - new_font.base_line();
    case va_top:
        return top + new_font.height - new_font.base_line();
    default:
        return current.baseline;
    }
}

std::list<std::unique_ptr<litehtml::line_box_item>> litehtml::line_box::finish(
    bool last_box, const containing_block_context& containing_block_size)
{
    std::list<std::unique_ptr<line_box_item>> ret_items;
    bool                                      finished_with_break = false;

    if(!last_box)
    {
        if(!is_empty())
        {
            finished_with_break = m_items.back()->get_el()->src_el()->is_break();
        }
        while(!m_items.empty())
        {
            if(m_items.back()->get_type() == line_box_item::type_text_part)
            {
                // remove trailing spaces
                if(m_items.back()->get_el()->src_el()->is_break() ||
                   m_items.back()->get_el()->src_el()->is_white_space())
                {
                    m_width -= m_items.back()->width();
                    m_items.back()->get_el()->skip(true);
                    m_items.pop_back();
                } else
                {
                    break;
                }
            } else if(m_items.back()->get_type() == line_box_item::type_inline_start)
            {
                // remove trailing empty inline_start markers
                // these markers will be added at the beginning of the next line box
                m_width -= m_items.back()->width();
                ret_items.emplace_back(std::move(m_items.back()));
                m_items.pop_back();
            } else
            {
                break;
            }
        }
    } else
    {
        // remove trailing spaces
        auto iter = m_items.rbegin();
        while(iter != m_items.rend())
        {
            if((*iter)->get_type() == line_box_item::type_text_part)
            {
                if((*iter)->get_el()->src_el()->is_white_space())
                {
                    (*iter)->get_el()->skip(true);
                    m_width -= (*iter)->width();
                    // Space can be between text and inline_end marker
                    // We have to shift all items on the right side
                    if(iter != m_items.rbegin())
                    {
                        auto r_iter = iter;
                        r_iter--;
                        while(true)
                        {
                            (*r_iter)->pos().x -= (*iter)->width();
                            if(r_iter == m_items.rbegin())
                            {
                                break;
                            }
                            r_iter--;
                        }
                    }
                    // erase white space element
                    iter = decltype(iter)(m_items.erase(std::next(iter).base()));
                } else
                {
                    break;
                }
            } else
            {
                iter++;
            }
        }
    }

    if(is_empty() || (!is_empty() && last_box && is_break_only()))
    {
        m_height   = m_default_line_height.computed_value;
        m_baseline = m_font_metrics.base_line();
        return ret_items;
    }

    pixel_t spacing_x = 0_px; // Number of pixels to distribute between elements
    pixel_t shift_x   = 0_px; // Shift elements by X to apply the text-align

    switch(m_text_align)
    {
    case text_align_right:
        if(m_width < (m_right - m_left))
        {
            shift_x = (m_right - m_left) - m_width;
        }
        break;
    case text_align_center:
        if(m_width < (m_right - m_left))
        {
            shift_x = ((m_right - m_left) - m_width) / 2_px;
        }
        break;
    case text_align_justify:
        if(m_width < (m_right - m_left) && !last_box && !finished_with_break)
        {
            shift_x   = 0_px;
            spacing_x = (m_right - m_left) - m_width;
        }
        break;
    default:
        shift_x = 0_px;
    }

    int   counter = 0;
    float offj    = static_cast<float>(spacing_x) / std::max(1.f, static_cast<float>(m_items.size()) - 1.f);
    float cixx    = 0.0f;

    std::optional<pixel_t> line_height;

    if(!m_default_line_height.css_value.is_predefined())
    {
        line_height = m_default_line_height.computed_value;
    }

    va_context            current_context;
    std::list<va_context> contexts;

    current_context.baseline    = 0;
    current_context.fm          = m_font_metrics;
    current_context.start_lbi   = nullptr;
    current_context.line_height = m_default_line_height.computed_value;

    m_rendered_width.reset();

    struct items_dimensions
    {
        pixel_t top        = 0_px;
        pixel_t bottom     = 0_px;
        int     count      = 0;
        pixel_t max_height = 0_px;

        void add_item(const line_box_item* item)
        {
            top        = std::min(top, item->top());
            bottom     = std::max(bottom, item->bottom());
            max_height = std::max(max_height, item->height());
            count++;
        }
        pixel_t height() const
        {
            return bottom - top;
        }
    };

    items_dimensions line_max_height;
    items_dimensions top_aligned_max_height;
    items_dimensions bottom_aligned_max_height;
    items_dimensions inline_boxes_dims;

    // First pass
    // 1. Apply text-align-justify
    // 1. Align all items by baseline
    // 2. top/button aligned items are aligned by baseline
    // 3. Calculate top and button of the linebox separately for items in baseline
    //    and for top and bottom aligned items
    for(const auto& lbi : m_items)
    {
        // Apply text-align-justify
        m_rendered_width.natural_width += lbi->get_rendered_min_width();
        m_rendered_width.min_width      = std::max(m_rendered_width.min_width, lbi->get_rendered_min_width());
        if(spacing_x != 0_px && counter)
        {
            cixx += offj;
            if((counter + 1) == static_cast<int>(m_items.size()))
            {
                cixx += 0.99f;
            }
            lbi->pos().x += pixel_t(cixx);
        }
        counter++;
        if((m_text_align == text_align_right || spacing_x != 0_px) && counter == static_cast<int>(m_items.size()))
        {
            // Forcible justify the last element to the right side for text align right and justify;
            lbi->pos().x = m_right - lbi->pos().width;
        } else if(shift_x != 0_px)
        {
            lbi->pos().x += shift_x;
        }

        // Calculate new baseline for inline start/continue
        // Inline start/continue elements are inline containers like <span>
        if(lbi->get_type() == line_box_item::type_inline_start ||
           lbi->get_type() == line_box_item::type_inline_continue)
        {
            contexts.push_back(current_context);
            if(is_one_of(lbi->get_el()->css().get_vertical_align(), va_top, va_bottom))
            {
                // top/bottom aligned inline boxes are aligned by baseline == 0
                current_context.baseline  = 0;
                current_context.start_lbi = lbi.get();
                current_context.start_lbi->reset_items_height();
            } else if(current_context.start_lbi)
            {
                current_context.baseline = calc_va_baseline(
                    current_context, lbi->get_el()->css().get_vertical_align(), lbi->get_el()->css().get_font_metrics(),
                    current_context.start_lbi->top(), current_context.start_lbi->bottom());
            } else
            {
                current_context.start_lbi = nullptr;
                current_context.baseline  = calc_va_baseline(current_context, lbi->get_el()->css().get_vertical_align(),
                                                             lbi->get_el()->css().get_font_metrics(),
                                                             line_max_height.top, line_max_height.bottom);
            }
            current_context.fm          = lbi->get_el()->css().get_font_metrics();
            current_context.line_height = lbi->get_el()->css().line_height().computed_value;
        }

        pixel_t bl                = current_context.baseline;
        pixel_t content_offset    = 0_px;
        bool    is_top_bottom_box = false;
        bool    ignore            = false;

        // Align element by baseline
        if(!is_one_of(lbi->get_el()->src_el()->css().get_display(), display_inline_text, display_inline))
        {
            // Apply margins, paddings and border for inline boxes
            content_offset = lbi->get_el()->content_offset_top();
            switch(lbi->get_el()->css().get_vertical_align())
            {
            case va_bottom:
            case va_top:
                // Align by base line 0 all inline boxes with top and bottom vertical align
                bl                = 0;
                is_top_bottom_box = true;
                break;

            case va_text_bottom:
                lbi->pos().y = bl + current_context.fm.base_line() - lbi->get_el()->height() + content_offset;
                ignore       = true;
                break;

            case va_text_top:
                lbi->pos().y = bl - current_context.fm.ascent + content_offset;
                ignore       = true;
                break;

            case va_middle:
                lbi->pos().y =
                    bl - current_context.fm.x_height / 2_px - lbi->get_el()->height() / 2_px + content_offset;
                ignore = true;
                break;

            default:
                bl = calc_va_baseline(current_context, lbi->get_el()->css().get_vertical_align(),
                                      lbi->get_el()->css().get_font_metrics(), line_max_height.top,
                                      line_max_height.bottom);
                break;
            }
        }
        if(!ignore)
        {
            lbi->pos().y = bl - lbi->get_el()->get_last_baseline() + content_offset;
        }

        if(is_top_bottom_box)
        {
            switch(lbi->get_el()->css().get_vertical_align())
            {
            case va_top:
                top_aligned_max_height.add_item(lbi.get());
                break;
            case va_bottom:
                bottom_aligned_max_height.add_item(lbi.get());
                break;
            default:
                break;
            }
        } else if(current_context.start_lbi)
        {
            current_context.start_lbi->add_item_height(lbi->top(), lbi->bottom());
            switch(current_context.start_lbi->get_el()->css().get_vertical_align())
            {
            case va_top:
                top_aligned_max_height.add_item(lbi.get());
                break;
            case va_bottom:
                bottom_aligned_max_height.add_item(lbi.get());
                break;
            default:
                break;
            }
        } else
        {
            if(!lbi->get_el()->src_el()->is_inline_box())
            {
                line_max_height.add_item(lbi.get());
            } else
            {
                inline_boxes_dims.add_item(lbi.get());
            }
        }

        if(!lbi->get_el()->src_el()->is_inline_box() && !lbi->get_el()->css().line_height().css_value.is_predefined())
        {
            if(line_height.has_value())
            {
                line_height = std::max(line_height.value(), lbi->get_el()->css().line_height().computed_value);
            } else
            {
                line_height = lbi->get_el()->css().line_height().computed_value;
            }
        }

        if(lbi->get_type() == line_box_item::type_inline_end)
        {
            if(!contexts.empty())
            {
                current_context = contexts.back();
                contexts.pop_back();
            }
        }
    }

    pixel_t top_shift = 0_px;
    if(line_height.has_value())
    {
        m_height = line_height.value();
        if(line_max_height.count != 0)
        {
            // We have inline items
            top_shift = std::abs(line_max_height.top.value());

            const pixel_t top_shift_correction = (line_height.value() - line_max_height.height()) / 2_px;
            // We have to calculate the baseline from the top of the line box due to possible round errors.
            // The top_shift_correction is actually text offset from the top of the line box.
            // The (top_shift_correction + line_max_height.height()): is the bottom of the text with shift
            // (text_bottom). The line_max_height.bottom is the text baseline here. So the formula is: baseline =
            // line_height - text_bottom + text_baseline The linebox baseline is the length from the bottom of the line
            // box to the text baseline.
            m_baseline =
                line_height.value() - (top_shift_correction + line_max_height.height()) + line_max_height.bottom;
            top_shift += top_shift_correction;
            if(inline_boxes_dims.count)
            {
                const pixel_t diff2(std::abs(inline_boxes_dims.top.value()) - std::abs(top_shift.value()));
                if(diff2 > 0_px)
                {
                    m_height   += diff2;
                    top_shift  += diff2;
                    m_baseline += diff2;
                }
                const pixel_t diff1 = inline_boxes_dims.bottom - (line_max_height.bottom + top_shift_correction);
                if(diff1 > 0_px)
                {
                    m_height += diff1;
                }
            }
        } else if(inline_boxes_dims.count != 0)
        {
            // We have inline boxes only
            m_height   = inline_boxes_dims.height();
            top_shift  = std::abs(inline_boxes_dims.top.value());
            m_baseline = inline_boxes_dims.bottom;
        } else
        {
            // We don't have inline items and inline boxes
            top_shift = 0_px;
        }

        const pixel_t top_down_height =
            std::max(top_aligned_max_height.max_height, bottom_aligned_max_height.max_height);
        if(top_down_height > m_height)
        {
            if(bottom_aligned_max_height.count)
            {
                top_shift += bottom_aligned_max_height.height() - m_height;
            }
            m_height = top_down_height;
        }
    } else
    {
        // Add inline boxes dimensions
        line_max_height.top    = std::min(line_max_height.top, inline_boxes_dims.top);
        line_max_height.bottom = std::max(line_max_height.bottom, inline_boxes_dims.bottom);

        // Height is maximum from inline elements height and top/bottom aligned elements height
        m_height =
            std::max({line_max_height.height(), top_aligned_max_height.height(), bottom_aligned_max_height.height()});

        top_shift  = -std::min(line_max_height.top, line_max_height.bottom - bottom_aligned_max_height.height());
        m_baseline = line_max_height.bottom;
    }

    struct inline_item_box
    {
        std::shared_ptr<render_item> element;
        position                     box;

        inline_item_box() = default;
        explicit inline_item_box(const std::shared_ptr<render_item>& el) :
            element(el)
        {
        }
    };

    std::list<inline_item_box> inlines;

    contexts.clear();

    current_context.baseline  = 0;
    current_context.fm        = m_font_metrics;
    current_context.start_lbi = nullptr;
    // int va_top_bottom = 0;

    // Second pass:
    // 1. Vertical align top/bottom
    // 2. Apply relative shift
    // 3. Calculate inline boxes
    for(const auto& lbi : m_items)
    {
        if(is_one_of(lbi->get_type(), line_box_item::type_inline_start, line_box_item::type_inline_continue))
        {
            contexts.push_back(current_context);
            current_context.fm = lbi->get_el()->css().get_font_metrics();

            if(lbi->get_el()->css().get_vertical_align() == va_top)
            {
                current_context.baseline  = m_top - lbi->get_items_top();
                current_context.start_lbi = lbi.get();
            } else if(lbi->get_el()->css().get_vertical_align() == va_bottom)
            {
                current_context.baseline  = m_top + m_height - lbi->get_items_bottom();
                current_context.start_lbi = lbi.get();
            }
        } else if(lbi->get_type() == line_box_item::type_inline_end)
        {
            if(!contexts.empty())
            {
                current_context = contexts.back();
                contexts.pop_back();
            }
        }

        if(current_context.start_lbi)
        {
            lbi->pos().y =
                current_context.baseline - lbi->get_el()->get_last_baseline() + lbi->get_el()->content_offset_top();
        } else if(is_one_of(lbi->get_el()->css().get_vertical_align(), va_top, va_bottom) &&
                  lbi->get_type() == line_box_item::type_text_part)
        {
            if(lbi->get_el()->css().get_vertical_align() == va_top)
            {
                lbi->pos().y = m_top + lbi->get_el()->content_offset_top();
            } else
            {
                lbi->pos().y = m_top + m_height - (lbi->bottom() - lbi->top()) + lbi->get_el()->content_offset_bottom();
            }
        } else
        {
            // move element to the correct position
            lbi->pos().y += m_top + top_shift;
        }

        lbi->get_el()->apply_relative_shift(containing_block_size);

        // Calculate and push inline box into the render item element
        if(lbi->get_type() == line_box_item::type_inline_start ||
           lbi->get_type() == line_box_item::type_inline_continue)
        {
            if(lbi->get_type() == line_box_item::type_inline_start)
            {
                lbi->get_el()->clear_inline_boxes();
            }
            inlines.emplace_back(lbi->get_el());
            inlines.back().box.x      = lbi->left();
            inlines.back().box.y      = lbi->top() - lbi->get_el()->content_offset_top();
            inlines.back().box.height = lbi->bottom() - lbi->top() + lbi->get_el()->content_offset_height();
        } else if(lbi->get_type() == line_box_item::type_inline_end)
        {
            if(!inlines.empty())
            {
                inlines.back().box.width = lbi->right() - inlines.back().box.x;
                inlines.back().element->add_inline_box(inlines.back().box);
                inlines.pop_back();
            }
        }
    }

    for(auto iter = inlines.rbegin(); iter != inlines.rend(); ++iter)
    {
        iter->box.width = m_items.back()->right() - iter->box.x;
        iter->element->add_inline_box(iter->box);

        ret_items.emplace_front(std::unique_ptr<line_box_item>(new lbi_continue(iter->element)));
    }

    return ret_items;
}

std::shared_ptr<litehtml::render_item> litehtml::line_box::get_first_text_part() const
{
    for(const auto& item : m_items)
    {
        if(item->get_type() == line_box_item::type_text_part)
        {
            return item->get_el();
        }
    }
    return nullptr;
}

std::shared_ptr<litehtml::render_item> litehtml::line_box::get_last_text_part() const
{
    for(auto iter = m_items.rbegin(); iter != m_items.rend(); iter++)
    {
        if((*iter)->get_type() == line_box_item::type_text_part)
        {
            return (*iter)->get_el();
        }
    }
    return nullptr;
}

bool litehtml::line_box::can_hold(const std::unique_ptr<line_box_item>& item, white_space ws) const
{
    if(!item->get_el()->src_el()->is_inline())
    {
        return false;
    }

    if(item->get_type() == line_box_item::type_text_part)
    {
        // force new line on floats clearing
        if(item->get_el()->src_el()->is_break() && item->get_el()->css().get_clear() != clear_none)
        {
            return false;
        }

        auto last_el = get_last_text_part();

        // the first word is always can be hold
        if(!last_el)
        {
            return true;
        }

        // force new line if the last placed element was line break
        // Skip If the break item is float clearing
        if(last_el && last_el->src_el()->is_break() && last_el->css().get_clear() == clear_none)
        {
            return false;
        }

        // line break should stay in current line box
        if(item->get_el()->src_el()->is_break())
        {
            return true;
        }

        if(ws == white_space_nowrap || ws == white_space_pre ||
           (ws == white_space_pre_wrap && item->get_el()->src_el()->is_space()))
        {
            return true;
        }

        if(m_left + m_width + item->width() > m_right)
        {
            return false;
        }
    }

    return true;
}

bool litehtml::line_box::have_last_space() const
{
    auto last_el = get_last_text_part();
    if(last_el)
    {
        return last_el->src_el()->is_white_space() || last_el->src_el()->is_break();
    }
    return false;
}

bool litehtml::line_box::is_empty() const
{
    if(m_items.empty())
    {
        return true;
    }
    if(m_items.size() == 1 && m_items.front()->get_el()->src_el()->is_break() &&
       m_items.front()->get_el()->src_el()->css().get_clear() != clear_none)
    {
        return true;
    }
    for(const auto& el : m_items)
    {
        if(el->get_type() == line_box_item::type_text_part)
        {
            if(!el->get_el()->skip() || el->get_el()->src_el()->is_break())
            {
                return false;
            }
        }
    }
    return true;
}

litehtml::pixel_t litehtml::line_box::baseline() const
{
    return m_baseline;
}

litehtml::pixel_t litehtml::line_box::top_margin() const
{
    return 0_px;
}

litehtml::pixel_t litehtml::line_box::bottom_margin() const
{
    return 0_px;
}

void litehtml::line_box::y_shift(pixel_t shift)
{
    m_top += shift;
    for(auto& el : m_items)
    {
        el->y_shift(shift);
    }
}

bool litehtml::line_box::is_break_only() const
{
    if(m_items.empty())
    {
        return false;
    }

    bool break_found = false;

    for(auto iter = m_items.rbegin(); iter != m_items.rend(); iter++)
    {
        if((*iter)->get_type() == line_box_item::type_text_part)
        {
            if((*iter)->get_el()->src_el()->is_break())
            {
                break_found = true;
            } else if(!(*iter)->get_el()->skip())
            {
                return false;
            }
        }
    }
    return break_found;
}

std::list<std::unique_ptr<litehtml::line_box_item>> litehtml::line_box::new_width(pixel_t left, pixel_t right)
{
    std::list<std::unique_ptr<line_box_item>> ret_items;
    pixel_t                                   add = left - m_left;
    if(add != 0_px)
    {
        m_left            = left;
        m_right           = right;
        m_width           = 0_px;
        auto remove_begin = m_items.end();
        auto i            = m_items.begin();
        i++;
        while(i != m_items.end())
        {
            if(!(*i)->get_el()->skip())
            {
                if(m_left + m_width + (*i)->width() > m_right)
                {
                    remove_begin = i;
                    break;
                }
                (*i)->pos().x += add;
                m_width       += (*i)->get_el()->width();
            }
            i++;
        }
        if(remove_begin != m_items.end())
        {
            while(remove_begin != m_items.end())
            {
                ret_items.emplace_back(std::move(*remove_begin));
            }
            m_items.erase(remove_begin, m_items.end());
        }
    }
    return ret_items;
}
