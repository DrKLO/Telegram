#ifndef LITEHTML_SCROLL_VIEW_H
#define LITEHTML_SCROLL_VIEW_H

#include "types.h"

namespace litehtml
{
    class scroll_view : std::enable_shared_from_this<scroll_view>
    {
      protected:
        position m_viewport;    // Viewport position and size
        size     m_scroll_size; // Size of the scrollable area, not the viewport
        pixel_t  m_left;        // Horizontal scroll position
        pixel_t  m_top;         // Vertical scroll position

      public:
        scroll_view(const position& viewport, const size& scroll_size) :
            m_viewport(viewport),
            m_scroll_size(scroll_size)
        {
        }

        pixel_t get_left() const
        {
            return m_left;
        }
        pixel_t get_top() const
        {
            return m_top;
        }

        pixel_t get_max_h_scroll() const
        {
            return std::max(0_px, m_scroll_size.width - m_viewport.width);
        }
        pixel_t get_max_v_scroll() const
        {
            return std::max(0_px, m_scroll_size.height - m_viewport.height);
        }

        void set(const position& viewport, const size& scroll_size)
        {
            m_viewport    = viewport;
            m_scroll_size = scroll_size;
            m_left        = std::clamp(m_left, 0_px, get_max_h_scroll());
            m_top         = std::clamp(m_top, 0_px, get_max_v_scroll());
        }

        const position& get_viewport() const
        {
            return m_viewport;
        }

        bool is_h_scrollable(const pixel_t dx) const
        {
            if(m_scroll_size.width == 0_px)
            {
                return false;
            }
            if(m_left != std::clamp(m_left + dx, 0_px, get_max_h_scroll()))
            {
                return true;
            }
            return false;
        }

        pixel_t h_scroll(const pixel_t dx)
        {
            if(m_scroll_size.width == 0_px)
            {
                return 0_px;
            }

            const pixel_t new_left = std::clamp(m_left + dx, 0_px, get_max_h_scroll());
            const pixel_t ret      = new_left - m_left;
            m_left                 = new_left;
            return ret;
        }

        bool is_v_scrollable(const pixel_t dy) const
        {
            if(m_scroll_size.height == 0_px)
            {
                return false;
            }
            if(m_top != std::clamp(m_top + dy, 0_px, get_max_v_scroll()))
            {
                return true;
            }
            return false;
        }

        pixel_t v_scroll(const pixel_t dy)
        {
            if(m_scroll_size.height == 0_px)
            {
                return 0_px;
            }

            const pixel_t new_top = std::clamp(m_top + dy, 0_px, get_max_v_scroll());
            const pixel_t ret     = new_top - m_top;
            m_top                 = new_top;
            return ret;
        }

        bool is_point_inside(const pixel_t x, const pixel_t y) const
        {
            return m_viewport.is_point_inside(x, y);
        }
    };

} // namespace litehtml

#endif // LITEHTML_SCROLL_VIEW_H
