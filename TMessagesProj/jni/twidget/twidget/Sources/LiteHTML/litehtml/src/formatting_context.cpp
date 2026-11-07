#include "render_item.h"
#include "types.h"
#include <optional>
#include "formatting_context.h"

void litehtml::formatting_context::add_float(const std::shared_ptr<render_item>& el, pixel_t min_width, int context)
{
    floated_box fb;
    fb.pos.x        = el->left() + m_current_left;
    fb.pos.y        = el->top() + m_current_top;
    fb.pos.width    = el->width();
    fb.pos.height   = el->height();
    fb.float_side   = el->src_el()->css().get_float();
    fb.clear_floats = el->src_el()->css().get_clear();
    fb.el           = el;
    fb.context      = context;
    fb.min_width    = min_width;

    if(fb.float_side == float_left)
    {
        if(m_floats_left.empty())
        {
            m_floats_left.push_back(fb);
        } else
        {
            bool inserted = false;
            for(auto i = m_floats_left.begin(); i != m_floats_left.end(); i++)
            {
                if(fb.pos.right() > i->pos.right())
                {
                    m_floats_left.insert(i, std::move(fb));
                    fb       = {};
                    inserted = true;
                    break;
                }
            }
            if(!inserted)
            {
                m_floats_left.push_back(std::move(fb));
            }
        }
        m_cache_line_left.invalidate();
    } else if(fb.float_side == float_right)
    {
        if(m_floats_right.empty())
        {
            m_floats_right.push_back(std::move(fb));
        } else
        {
            bool inserted = false;
            for(auto i = m_floats_right.begin(); i != m_floats_right.end(); i++)
            {
                if(fb.pos.left() < i->pos.left())
                {
                    m_floats_right.insert(i, std::move(fb));
                    fb       = {};
                    inserted = true;
                    break;
                }
            }
            if(!inserted)
            {
                m_floats_right.push_back(fb);
            }
        }
        m_cache_line_right.invalidate();
    }
}

litehtml::pixel_t litehtml::formatting_context::get_floats_height(element_float el_float) const
{
    pixel_t h = m_current_top;

    for(const auto& fb : m_floats_left)
    {
        bool process = false;
        switch(el_float)
        {
        case float_none:
            process = true;
            break;
        case float_left:
            if(fb.clear_floats == clear_left || fb.clear_floats == clear_both)
            {
                process = true;
            }
            break;
        case float_right:
            if(fb.clear_floats == clear_right || fb.clear_floats == clear_both)
            {
                process = true;
            }
            break;
        }
        if(process)
        {
            if(el_float == float_none)
            {
                h = std::max(h, fb.pos.bottom());
            } else
            {
                h = std::max(h, fb.pos.top());
            }
        }
    }

    for(const auto& fb : m_floats_right)
    {
        bool process = false;
        switch(el_float)
        {
        case float_none:
            process = true;
            break;
        case float_left:
            if(fb.clear_floats == clear_left || fb.clear_floats == clear_both)
            {
                process = true;
            }
            break;
        case float_right:
            if(fb.clear_floats == clear_right || fb.clear_floats == clear_both)
            {
                process = true;
            }
            break;
        }
        if(process)
        {
            if(el_float == float_none)
            {
                h = std::max(h, fb.pos.bottom());
            } else
            {
                h = std::max(h, fb.pos.top());
            }
        }
    }

    return h - m_current_top;
}

litehtml::pixel_t litehtml::formatting_context::get_left_floats_height() const
{
    pixel_t h = 0_px;
    if(!m_floats_left.empty())
    {
        for(const auto& fb : m_floats_left)
        {
            h = std::max(h, fb.pos.bottom());
        }
    }
    return h - m_current_top;
}

litehtml::pixel_t litehtml::formatting_context::get_right_floats_height() const
{
    pixel_t h = 0_px;
    if(!m_floats_right.empty())
    {
        for(const auto& fb : m_floats_right)
        {
            h = std::max(h, fb.pos.bottom());
        }
    }
    return h - m_current_top;
}

litehtml::pixel_t litehtml::formatting_context::get_line_left(pixel_t y)
{
    y += m_current_top;

    if(m_cache_line_left.is_valid && m_cache_line_left.hash == y)
    {
        if(m_cache_line_left.val - m_current_left < 0_px)
        {
            return 0_px;
        }
        return m_cache_line_left.val - m_current_left;
    }

    pixel_t w = 0_px;
    for(const auto& fb : m_floats_left)
    {
        if(y >= fb.pos.top() && y < fb.pos.bottom())
        {
            w = std::max(w, fb.pos.right());
            if(w < fb.pos.right())
            {
                break;
            }
        }
    }
    m_cache_line_left.set_value(y, w);
    w -= m_current_left;
    if(w < 0_px)
    {
        return 0_px;
    }
    return w;
}

litehtml::pixel_t litehtml::formatting_context::get_line_right(pixel_t y, pixel_t def_right)
{
    y         += m_current_top;
    def_right += m_current_left;
    if(m_cache_line_right.is_valid && m_cache_line_right.hash == y)
    {
        if(m_cache_line_right.is_default)
        {
            return def_right - m_current_left;
        }
        pixel_t w = std::min(m_cache_line_right.val, def_right) - m_current_left;
        if(w < 0_px)
        {
            return 0_px;
        }
        return w;
    }

    pixel_t w                     = def_right;
    m_cache_line_right.is_default = true;
    for(const auto& fb : m_floats_right)
    {
        if(y >= fb.pos.top() && y < fb.pos.bottom())
        {
            w                             = std::min(w, fb.pos.left());
            m_cache_line_right.is_default = false;
            if(w > fb.pos.left())
            {
                break;
            }
        }
    }
    m_cache_line_right.set_value(y, w);
    w -= m_current_left;
    if(w < 0_px)
    {
        return 0_px;
    }
    return w;
}

void litehtml::formatting_context::clear_floats(int context)
{
    auto iter = m_floats_left.begin();
    while(iter != m_floats_left.end())
    {
        if(iter->context >= context)
        {
            iter = m_floats_left.erase(iter);
            m_cache_line_left.invalidate();
        } else
        {
            iter++;
        }
    }

    iter = m_floats_right.begin();
    while(iter != m_floats_right.end())
    {
        if(iter->context >= context)
        {
            iter = m_floats_right.erase(iter);
            m_cache_line_right.invalidate();
        } else
        {
            iter++;
        }
    }
}

litehtml::pixel_t litehtml::formatting_context::get_cleared_top(const std::shared_ptr<render_item>& el,
                                                                pixel_t                             line_top) const
{
    switch(el->src_el()->css().get_clear())
    {
    case clear_left:
        {
            pixel_t fh = get_left_floats_height();
            if(fh != 0_px && fh > line_top)
            {
                line_top = fh;
            }
        }
        break;
    case clear_right:
        {
            pixel_t fh = get_right_floats_height();
            if(fh != 0_px && fh > line_top)
            {
                line_top = fh;
            }
        }
        break;
    case clear_both:
        {
            pixel_t fh = get_floats_height(float_none);
            if(fh != 0_px && fh > line_top)
            {
                line_top = fh;
            }
        }
        break;
    default:
        if(el->src_el()->css().get_float() != float_none)
        {
            pixel_t fh = get_floats_height(el->src_el()->css().get_float());
            if(fh != 0_px && fh > line_top)
            {
                line_top = fh;
            }
        }
        break;
    }
    return line_top;
}

litehtml::formatting_context::new_position litehtml::formatting_context::place_to_left(const el_position& el_pos) const
{
    position pos_el   = el_pos.el_pos;
    pos_el.x         += m_current_left + el_pos.el_margins.left;
    pos_el.y         += m_current_top + el_pos.el_margins.top;
    pos_el.width     -= el_pos.el_margins.left + el_pos.el_margins.right;
    auto max_right    = el_pos.container_width + m_current_left;
    bool was_changed  = false;
    bool next_line    = false;
    bool left_side    = false; // true if floating block at the left side
    bool right_side   = false; // true if floating block at the right side

    while(true)
    {
        std::optional<position> max_left_pos;
        bool                    found    = false;
        pixel_t                 max_left = m_current_left;
        left_side                        = false;
        // check intersection with left floats
        for(const auto& fb : m_floats_left)
        {
            if(fb.pos.height == 0_px)
            {
                continue;
            }
            if(fb.pos.on_same_line(pos_el, true))
            {
                left_side = true;
                max_left  = std::max(max_left, fb.pos.right());
                if(pos_el.x < fb.pos.right())
                {
                    pos_el.x     = fb.pos.right();
                    max_left_pos = fb.pos;
                    found        = true;
                    was_changed  = true;
                }
            }
        }
        if(pos_el.right() > max_right && found)
        {
            // move to next line
            next_line = true;
            pos_el.x  = m_current_left + el_pos.el_margins.left;
            pos_el.y  = max_left_pos->bottom();
        } else
        {
            found             = false;
            pixel_t min_right = max_right;
            right_side        = false;
            // check intersection with right floats
            for(const auto& fb : m_floats_right)
            {
                if(fb.pos.height == 0_px)
                {
                    continue;
                }
                // calculate minimum right position
                if(fb.pos.on_same_line(pos_el, true))
                {
                    right_side = true;
                    min_right  = std::min(min_right, fb.pos.left());
                }
                // if element intersects float box move it to the next line
                if(fb.pos.does_intersect(&pos_el, true))
                {
                    right_side = false;
                    pos_el.x   = m_current_left + el_pos.el_margins.left;
                    pos_el.y =
                        max_left_pos.has_value() ? std::min(max_left_pos->bottom(), fb.pos.bottom()) : fb.pos.bottom();
                    found       = true;
                    next_line   = true;
                    was_changed = true;
                    break;
                }
            }
            if(!found)
            {
                // position found
                new_position pos;
                pos.found    = was_changed;
                pos.new_line = next_line;
                pos.top      = pos_el.y - m_current_top - el_pos.el_margins.top;
                pos.left     = pos_el.x - m_current_left - el_pos.el_margins.left;
                pos.width    = min_right - max_left;

                if(left_side)
                {
                    pos.width += el_pos.el_margins.left;
                }
                if(right_side)
                {
                    pos.width += el_pos.el_margins.right;
                }

                return pos;
            }
        }
    }
    // position not found
    new_position pos;
    pos.found = false;
    return pos;
}

litehtml::formatting_context::new_position litehtml::formatting_context::place_to_right(const el_position& el_pos) const
{
    position pos_el   = el_pos.el_pos;
    pos_el.x         += m_current_left + el_pos.el_margins.left;
    pos_el.y         += m_current_top + el_pos.el_margins.top;
    pos_el.width     -= el_pos.el_margins.left + el_pos.el_margins.right;
    auto max_right    = el_pos.container_width + m_current_left;
    bool was_changed  = false;
    bool next_line    = false;
    bool left_side    = false; // true if floating block at the left side
    bool right_side   = false; // true if floating block at the right side

    while(true)
    {
        std::optional<position> min_right_pos;
        bool                    found     = false;
        pixel_t                 min_right = max_right;
        right_side                        = false;
        // check intersection with right floats
        for(const auto& fb : m_floats_right)
        {
            if(fb.pos.height == 0_px)
            {
                continue;
            }
            // if element intersects float box move it to the left of float box
            if(fb.pos.on_same_line(pos_el, true))
            {
                right_side = true;
                min_right  = std::min(min_right, fb.pos.left());
                if(pos_el.right() > fb.pos.left())
                {
                    pos_el.x      = fb.pos.left() - pos_el.width;
                    min_right_pos = fb.pos;
                    found         = true;
                    was_changed   = true;
                }
            }
        }
        if(pos_el.left() < m_current_left && found)
        {
            // move to next line
            next_line = true;
            pos_el.x  = max_right - pos_el.width - el_pos.el_margins.left;
            pos_el.y  = min_right_pos->bottom();
        } else
        {
            found            = false;
            pixel_t max_left = m_current_left;
            left_side        = false;
            // check intersection with left floats
            for(const auto& fb : m_floats_left)
            {
                if(fb.pos.height == 0_px)
                {
                    continue;
                }
                // calculate maximum left position
                if(fb.pos.on_same_line(pos_el, true))
                {
                    left_side = true;
                    max_left  = std::max(max_left, fb.pos.right());
                }
                // if element intersects float box move it to the next line
                if(fb.pos.does_intersect(&pos_el, true))
                {
                    left_side   = false;
                    pos_el.x    = max_right - pos_el.width - el_pos.el_margins.left;
                    pos_el.y    = min_right_pos.has_value() ? std::min(min_right_pos->bottom(), fb.pos.bottom())
                                                            : fb.pos.bottom();
                    found       = true;
                    next_line   = true;
                    was_changed = true;
                    break;
                }
            }
            if(!found)
            {
                // position found
                new_position pos;
                pos.found    = was_changed;
                pos.new_line = next_line;
                pos.top      = pos_el.y - m_current_top - el_pos.el_margins.top;
                pos.left     = pos_el.x - m_current_left - el_pos.el_margins.left;
                pos.width    = min_right - max_left;

                if(left_side)
                {
                    pos.width += el_pos.el_margins.left;
                }
                if(right_side)
                {
                    pos.width += el_pos.el_margins.right;
                }

                return pos;
            }
        }
    }
    // position not found
    new_position pos;
    pos.found = false;
    return pos;
}

void litehtml::formatting_context::update_floats(pixel_t dy, const std::shared_ptr<render_item>& parent)
{
    bool reset_cache = false;
    for(auto fb = m_floats_left.rbegin(); fb != m_floats_left.rend(); fb++)
    {
        if(fb->el->src_el()->is_ancestor(parent->src_el()))
        {
            reset_cache  = true;
            fb->pos.y   += dy;
        }
    }
    if(reset_cache)
    {
        m_cache_line_left.invalidate();
    }
    reset_cache = false;
    for(auto fb = m_floats_right.rbegin(); fb != m_floats_right.rend(); fb++)
    {
        if(fb->el->src_el()->is_ancestor(parent->src_el()))
        {
            reset_cache  = true;
            fb->pos.y   += dy;
        }
    }
    if(reset_cache)
    {
        m_cache_line_right.invalidate();
    }
}

void litehtml::formatting_context::apply_relative_shift(const containing_block_context& containing_block_size)
{
    for(const auto& fb : m_floats_left)
    {
        fb.el->apply_relative_shift(containing_block_size);
    }
}

litehtml::pixel_t litehtml::formatting_context::find_min_left(pixel_t y, int context_idx)
{
    y                += m_current_top;
    pixel_t min_left  = m_current_left;
    for(const auto& fb : m_floats_left)
    {
        if(y >= fb.pos.top() && y < fb.pos.bottom() && fb.context == context_idx)
        {
            min_left += fb.min_width;
        }
    }
    if(min_left < m_current_left)
    {
        return 0_px;
    }
    return min_left - m_current_left;
}

litehtml::pixel_t litehtml::formatting_context::find_min_right(pixel_t y, pixel_t right, int context_idx)
{
    y                 += m_current_top;
    pixel_t min_right  = right + m_current_left;
    for(const auto& fb : m_floats_right)
    {
        if(y >= fb.pos.top() && y < fb.pos.bottom() && fb.context == context_idx)
        {
            min_right -= fb.min_width;
        }
    }
    if(min_right < m_current_left)
    {
        return 0_px;
    }
    return min_right - m_current_left;
}
