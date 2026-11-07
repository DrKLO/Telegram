#ifndef LITEHTML_FORMATTING_CONTEXT_H
#define LITEHTML_FORMATTING_CONTEXT_H

#include "types.h"
#include <list>

namespace litehtml
{
    class formatting_context
    {
      public:
        struct new_position
        {
            bool    found    = false; // true if position found else use the suplied position
            bool    new_line = false; // true if element was moved to new line
            pixel_t top;              // element top position
            pixel_t left;             // element left position
            pixel_t width;            // maximum width available for element
        };

        struct el_position
        {
            margins  el_margins;      // element margins
            position el_pos;          // element position including margins
            pixel_t  container_width; // maximum width on containing block
        };

      private:
        std::list<floated_box> m_floats_left;
        std::list<floated_box> m_floats_right;
        pixel_pixel_cache      m_cache_line_left;
        pixel_pixel_cache      m_cache_line_right;
        pixel_t                m_current_top;
        pixel_t                m_current_left;

      public:
        formatting_context() :
            m_current_top(0),
            m_current_left(0)
        {
        }

        void push_position(pixel_t x, pixel_t y)
        {
            m_current_left += x;
            m_current_top  += y;
        }
        void pop_position(pixel_t x, pixel_t y)
        {
            m_current_left -= x;
            m_current_top  -= y;
        }

        void         add_float(const std::shared_ptr<render_item>& el, pixel_t min_width, int context);
        void         clear_floats(int context);
        new_position place_to_left(const el_position& el_pos) const;
        new_position place_to_right(const el_position& el_pos) const;
        pixel_t      get_floats_height(element_float el_float = float_none) const;
        pixel_t      get_left_floats_height() const;
        pixel_t      get_right_floats_height() const;
        pixel_t      get_line_left(pixel_t y);
        void         get_line_left_right(pixel_t y, pixel_t def_right, pixel_t& ln_left, pixel_t& ln_right)
        {
            ln_left  = get_line_left(y);
            ln_right = get_line_right(y, def_right);
        }
        pixel_t get_line_right(pixel_t y, pixel_t def_right);
        pixel_t get_cleared_top(const std::shared_ptr<render_item>& el, pixel_t line_top) const;
        void    update_floats(pixel_t dy, const std::shared_ptr<render_item>& parent);
        void    apply_relative_shift(const containing_block_context& containing_block_size);
        pixel_t find_min_left(pixel_t y, int context_idx);
        pixel_t find_min_right(pixel_t y, pixel_t right, int context_idx);
    };
} // namespace litehtml

#endif // LITEHTML_FORMATTING_CONTEXT_H
