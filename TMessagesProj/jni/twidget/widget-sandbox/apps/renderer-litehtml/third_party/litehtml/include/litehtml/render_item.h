#ifndef LITEHTML_RENDER_ITEM_H
#define LITEHTML_RENDER_ITEM_H

#include <memory>
#include <list>
#include <tuple>
#include "html.h"
#include "types.h"
#include "line_box.h"
#include "table.h"
#include "formatting_context.h"
#include "element.h"
#include "scroll_view.h"

namespace litehtml
{
    class element;

    class render_item : public std::enable_shared_from_this<render_item>
    {
      protected:
        std::shared_ptr<element>                  m_element;
        std::weak_ptr<render_item>                m_parent;
        std::list<std::shared_ptr<render_item>>   m_children;
        margins                                   m_margins;
        margins                                   m_padding;
        margins                                   m_borders;
        position                                  m_pos;
        bool                                      m_skip = false;
        std::vector<std::shared_ptr<render_item>> m_positioned;
        std::shared_ptr<scroll_view>              m_scroll_view;

        containing_block_context calculate_containing_block_context(const containing_block_context& cb_context);
        void                     calc_cb_length(const css_length& len, pixel_t percent_base,
                                                containing_block_context::typed_pixel& out_value) const;
        virtual rendered_width   _render(pixel_t /*x*/, pixel_t /*y*/,
                                         const containing_block_context& /*containing_block_size*/,
                                         formatting_context* /*fmt_ctx*/, bool /*second_pass = false*/)
        {
            return {0_px, 0_px};
        }

      public:
        explicit render_item(std::shared_ptr<element> src_el);

        virtual ~render_item() = default;

        pixel_t get_scroll_left() const
        {
            return m_scroll_view ? m_scroll_view->get_left() : 0_px;
        }

        pixel_t get_scroll_top() const
        {
            return m_scroll_view ? m_scroll_view->get_top() : 0_px;
        }

        void scroll_box(position& box) const
        {
            if(m_scroll_view)
            {
                box.x -= m_scroll_view->get_left();
                box.y -= m_scroll_view->get_top();
            }
        }

        pixel_t h_scroll(pixel_t dx)
        {
            return m_scroll_view ? m_scroll_view->h_scroll(dx) : 0_px;
        }

        pixel_t v_scroll(pixel_t dy)
        {
            return m_scroll_view ? m_scroll_view->v_scroll(dy) : 0_px;
        }

        bool is_h_scrollable(const pixel_t dx) const
        {
            return m_scroll_view ? m_scroll_view->is_h_scrollable(dx) : false;
        }

        bool is_v_scrollable(const pixel_t dy) const
        {
            return m_scroll_view ? m_scroll_view->is_v_scrollable(dy) : false;
        }

        std::list<std::shared_ptr<render_item>>& children()
        {
            return m_children;
        }

        // Access to the m_pos
        position& pos()
        {
            return m_pos;
        }

        // Calculates the position of the element in the document
        // This position is relative to the x and y arguments
        // Scroll shifts are applied to the position
        position calc_placement(int x = 0, int y = 0) const
        {
            position pos  = m_pos;
            pos.x        += pixel_t(x) - get_scroll_left();
            pos.y        += pixel_t(y) - get_scroll_top();
            return pos;
        }

        bool skip() const
        {
            return m_skip;
        }

        void skip(bool val)
        {
            m_skip = val;
        }

        pixel_t right() const
        {
            return left() + width();
        }

        pixel_t left() const
        {
            return m_pos.left() - m_margins.left - m_padding.left - m_borders.left;
        }

        pixel_t top() const
        {
            return m_pos.top() - m_margins.top - m_padding.top - m_borders.top;
        }

        pixel_t bottom() const
        {
            return top() + height();
        }

        pixel_t height() const
        {
            return m_pos.height + m_margins.height() + m_padding.height() + m_borders.height();
        }

        pixel_t width() const
        {
            return m_pos.width + m_margins.width() + m_padding.width() + m_borders.width();
        }

        pixel_t padding_top() const
        {
            return m_padding.top;
        }

        pixel_t padding_bottom() const
        {
            return m_padding.bottom;
        }

        pixel_t padding_left() const
        {
            return m_padding.left;
        }

        pixel_t padding_right() const
        {
            return m_padding.right;
        }

        pixel_t border_top() const
        {
            return m_borders.top;
        }

        pixel_t border_bottom() const
        {
            return m_borders.bottom;
        }

        pixel_t border_left() const
        {
            return m_borders.left;
        }

        pixel_t border_right() const
        {
            return m_borders.right;
        }

        pixel_t margin_top() const
        {
            return m_margins.top;
        }

        pixel_t margin_bottom() const
        {
            return m_margins.bottom;
        }

        pixel_t margin_left() const
        {
            return m_margins.left;
        }

        pixel_t margin_right() const
        {
            return m_margins.right;
        }

        std::shared_ptr<render_item> parent() const
        {
            return m_parent.lock();
        }

        margins& get_margins()
        {
            return m_margins;
        }

        margins& get_paddings()
        {
            return m_padding;
        }

        void set_paddings(const margins& val)
        {
            m_padding = val;
        }

        margins& get_borders()
        {
            return m_borders;
        }

        /**
         * Top offset to the element content. Includes paddings, margins and borders.
         */
        pixel_t content_offset_top() const
        {
            return m_margins.top + m_padding.top + m_borders.top;
        }

        /**
         * Bottom offset to the element content. Includes paddings, margins and borders.
         */
        pixel_t content_offset_bottom() const
        {
            return m_margins.bottom + m_padding.bottom + m_borders.bottom;
        }

        /**
         * Left offset to the element content. Includes paddings, margins and borders.
         */
        pixel_t content_offset_left() const
        {
            return m_margins.left + m_padding.left + m_borders.left;
        }

        /**
         * Right offset to the element content. Includes paddings, margins and borders.
         */
        pixel_t content_offset_right() const
        {
            return m_margins.right + m_padding.right + m_borders.right;
        }

        /**
         * Sum of left and right offsets to the element content. Includes paddings, margins and borders.
         */
        pixel_t content_offset_width() const
        {
            return content_offset_left() + content_offset_right();
        }

        /**
         * Sum of top and bottom offsets to the element content. Includes paddings, margins and borders.
         */
        pixel_t content_offset_height() const
        {
            return content_offset_top() + content_offset_bottom();
        }

        pixel_t render_offset_left() const
        {
            if(css().get_box_sizing() == box_sizing_content_box)
            {
                return m_margins.left + m_borders.left + m_padding.left;
            }
            return m_margins.left;
        }

        pixel_t render_offset_right() const
        {
            if(css().get_box_sizing() == box_sizing_content_box)
            {
                return m_margins.right + m_borders.right + m_padding.right;
            }
            return m_margins.right;
        }

        pixel_t render_offset_width() const
        {
            return render_offset_left() + render_offset_right();
        }

        pixel_t render_offset_top() const
        {
            if(css().get_box_sizing() == box_sizing_content_box)
            {
                return m_margins.top + m_borders.top + m_padding.top;
            }
            return m_margins.top;
        }

        pixel_t render_offset_bottom() const
        {
            if(css().get_box_sizing() == box_sizing_content_box)
            {
                return m_margins.bottom + m_borders.bottom + m_padding.bottom;
            }
            return m_margins.bottom;
        }

        pixel_t render_offset_height() const
        {
            return render_offset_top() + render_offset_bottom();
        }

        pixel_t box_sizing_left() const
        {
            if(css().get_box_sizing() == box_sizing_border_box)
            {
                return m_padding.left + m_borders.left;
            }
            return 0_px;
        }

        pixel_t box_sizing_right() const
        {
            if(css().get_box_sizing() == box_sizing_border_box)
            {
                return m_padding.right + m_borders.right;
            }
            return 0_px;
        }

        pixel_t box_sizing_width() const
        {
            return box_sizing_left() + box_sizing_right();
        }

        pixel_t box_sizing_top() const
        {
            if(css().get_box_sizing() == box_sizing_border_box)
            {
                return m_padding.top + m_borders.top;
            }
            return 0_px;
        }

        pixel_t box_sizing_bottom() const
        {
            if(css().get_box_sizing() == box_sizing_border_box)
            {
                return m_padding.bottom + m_borders.bottom;
            }
            return 0_px;
        }

        pixel_t box_sizing_height() const
        {
            return box_sizing_top() + box_sizing_bottom();
        }

        void parent(const std::shared_ptr<render_item>& par)
        {
            m_parent = par;
        }

        const std::shared_ptr<element>& src_el() const
        {
            return m_element;
        }

        const css_properties& css() const
        {
            return m_element->css();
        }

        void add_child(const std::shared_ptr<render_item>& ri)
        {
            m_children.push_back(ri);
            ri->parent(shared_from_this());
        }

        bool is_root() const
        {
            return m_parent.expired();
        }

        bool collapse_top_margin() const
        {
            return m_borders.top == 0_px && m_padding.top == 0_px && m_element->in_normal_flow() &&
                   m_element->css().get_float() == float_none && m_margins.top >= 0_px && !is_flex_item() &&
                   !is_root() && !is_one_of(css().get_overflow(), overflow_hidden, overflow_scroll, overflow_auto);
        }

        bool collapse_bottom_margin() const
        {
            return m_borders.bottom == 0_px && m_padding.bottom == 0_px && m_element->in_normal_flow() &&
                   m_element->css().get_float() == float_none && m_margins.bottom >= 0_px && !is_root() &&
                   !is_one_of(css().get_overflow(), overflow_hidden, overflow_scroll, overflow_auto);
        }

        bool is_visible() const
        {
            return !m_skip && src_el()->css().get_display() != display_none &&
                   src_el()->css().get_visibility() == visibility_visible;
        }

        bool is_flex_item() const
        {
            auto par = parent();
            return par && (par->css().get_display() == display_inline_flex || par->css().get_display() == display_flex);
        }

        rendered_width render(pixel_t x, pixel_t y, const containing_block_context& containing_block_size,
                              formatting_context* fmt_ctx, bool second_pass = false);
        void           apply_relative_shift(const containing_block_context& containing_block_size);
        void           calc_outlines(pixel_t parent_width);
        pixel_t        calc_auto_margins(pixel_t parent_width); // returns left margin

        virtual std::shared_ptr<render_item> init();
        virtual void                         apply_vertical_align() {}
        /**
         * Get first baseline position. Default position is element bottom without bottom margin.
         * @returns offset of the first baseline from element top
         */
        virtual pixel_t get_first_baseline()
        {
            return height() - margin_bottom();
        }
        /**
         * Get the last baseline position.  The default position is element bottom without bottom margin.
         * @returns offset of the last baseline from element top
         */
        virtual pixel_t get_last_baseline()
        {
            return height() - margin_bottom();
        }

        virtual std::shared_ptr<render_item> clone()
        {
            return std::make_shared<render_item>(src_el());
        }
        std::tuple<std::shared_ptr<litehtml::render_item>, std::shared_ptr<litehtml::render_item>,
                   std::shared_ptr<litehtml::render_item>>
             split_inlines();
        bool fetch_positioned();
        void render_positioned(render_type rt = render_all);
        // returns element offset related to the containing block
        std::tuple<pixel_t, pixel_t> element_static_offset(const std::shared_ptr<litehtml::render_item>& el);

        void add_positioned(const std::shared_ptr<litehtml::render_item>& el);
        void get_redraw_box(litehtml::position& pos, pixel_t x = 0_px, pixel_t y = 0_px);
        void calc_document_size(litehtml::size& sz, pixel_t x = 0_px, pixel_t y = 0_px);

        /**
         * @brief Call func for all inline boxes
         * @param process function to call for boxes. "first "argument is true for the first box, "last" argument is
         * true for the last box. Function should return true to continue.
         * @return returns true if supported, else returns false
         */
        virtual bool for_inline_boxes(
            [[maybe_unused]] const std::function<bool(const position& box, bool first, bool last)>& process) const
        {
            return false;
        }

        virtual void set_inline_boxes(position::vector& /*boxes*/) {};
        virtual void add_inline_box(const position& /*box*/) {};
        virtual void clear_inline_boxes() {};
        void draw_stacking_context(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, bool with_positioned);
        virtual void    draw_children(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, draw_flag flag,
                                      int zindex);
        virtual pixel_t get_draw_vertical_offset()
        {
            return 0_px;
        }
        virtual std::shared_ptr<element> get_child_by_point(
            pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y, draw_flag flag, int zindex,
            const std::function<bool(const std::shared_ptr<render_item>&)>& check);
        std::shared_ptr<element> get_element_by_point(
            pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
            const std::function<bool(const std::shared_ptr<render_item>&)>& check);
        virtual bool is_point_inside(pixel_t x, pixel_t y) const;
        void         dump(litehtml::dumper& cout);
        position     get_placement() const;
        virtual void y_shift(pixel_t delta);
        /**
         * Returns the boxes of rendering element. All coordinates are absolute
         *
         * @param redraw_box function to be called on every rendering box
         * @return
         */
        void get_rendering_boxes(const std::function<void(const position&)>& redraw_box) const;
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_ITEM_H
