#ifndef LITEHTML_LINE_BOX_H
#define LITEHTML_LINE_BOX_H

#include <memory>
#include "css_properties.h"
#include "types.h"

namespace litehtml
{
    class render_item;

    struct line_context
    {
        pixel_t calculatedTop;
        pixel_t top;
        pixel_t left;
        pixel_t right;

        pixel_t width() const
        {
            return right - left;
        }
        void fix_top()
        {
            calculatedTop = top;
        }
        line_context() :
            calculatedTop(0),
            top(0),
            left(0),
            right(0)
        {
        }
    };

    class line_box_item
    {
      public:
        enum element_type
        {
            type_text_part,
            type_inline_start,
            type_inline_continue,
            type_inline_end
        };

      protected:
        std::shared_ptr<render_item> m_element;
        pixel_t                      m_rendered_min_width;
        pixel_t                      m_items_top;
        pixel_t                      m_items_bottom;

      public:
        explicit line_box_item(const std::shared_ptr<render_item>& element) :
            m_element(element)
        {
        }
        line_box_item(const line_box_item& el) = default;
        line_box_item(line_box_item&&)         = default;
        virtual ~line_box_item();

        virtual pixel_t                     height() const;
        const std::shared_ptr<render_item>& get_el() const
        {
            return m_element;
        }
        virtual position&    pos();
        virtual void         place_to(pixel_t x, pixel_t y);
        virtual pixel_t      width() const;
        virtual pixel_t      top() const;
        virtual pixel_t      bottom() const;
        virtual pixel_t      right() const;
        virtual pixel_t      left() const;
        virtual element_type get_type() const
        {
            return type_text_part;
        }
        virtual pixel_t get_rendered_min_width() const
        {
            return m_rendered_min_width;
        }
        virtual void set_rendered_min_width(pixel_t min_width)
        {
            m_rendered_min_width = min_width;
        }
        virtual void y_shift(pixel_t shift);

        void reset_items_height()
        {
            m_items_top = m_items_bottom = 0_px;
        }
        void add_item_height(pixel_t item_top, pixel_t item_bottom)
        {
            m_items_top    = std::min(m_items_top, item_top);
            m_items_bottom = std::max(m_items_bottom, item_bottom);
        }
        pixel_t get_items_top() const
        {
            return m_items_top;
        }
        pixel_t get_items_bottom() const
        {
            return m_items_bottom;
        }
    };

    class lbi_start : public line_box_item
    {
      protected:
        position m_pos;

      public:
        explicit lbi_start(const std::shared_ptr<render_item>& element);
        ~lbi_start() override;

        void      place_to(pixel_t x, pixel_t y) override;
        pixel_t   height() const override;
        pixel_t   width() const override;
        position& pos() override
        {
            return m_pos;
        }
        pixel_t      top() const override;
        pixel_t      bottom() const override;
        pixel_t      right() const override;
        pixel_t      left() const override;
        element_type get_type() const override
        {
            return type_inline_start;
        }
        pixel_t get_rendered_min_width() const override
        {
            return width();
        }
    };

    class lbi_end : public lbi_start
    {
      public:
        explicit lbi_end(const std::shared_ptr<render_item>& element);
        ~lbi_end() override;

        void         place_to(pixel_t x, pixel_t y) override;
        pixel_t      right() const override;
        pixel_t      left() const override;
        element_type get_type() const override
        {
            return type_inline_end;
        }
        void y_shift(pixel_t) override {}
    };

    class lbi_continue : public lbi_start
    {
      public:
        explicit lbi_continue(const std::shared_ptr<render_item>& element);
        ~lbi_continue() override;

        void         place_to(pixel_t x, pixel_t y) override;
        pixel_t      right() const override;
        pixel_t      left() const override;
        pixel_t      width() const override;
        element_type get_type() const override
        {
            return type_inline_continue;
        }
    };

    class line_box
    {
        struct va_context
        {
            pixel_t        line_height;
            pixel_t        baseline;
            font_metrics   fm;
            line_box_item* start_lbi = nullptr;
        };

        pixel_t           m_top;
        pixel_t           m_left;
        pixel_t           m_right;
        pixel_t           m_height;
        pixel_t           m_width;
        css_line_height_t m_default_line_height;
        font_metrics      m_font_metrics;
        pixel_t           m_baseline;
        text_align        m_text_align;
        rendered_width    m_rendered_width;

        std::list<std::unique_ptr<line_box_item>> m_items;

      public:
        line_box(pixel_t top, pixel_t left, pixel_t right, const css_line_height_t& line_height, const font_metrics& fm,
                 text_align align) :
            m_top(top),
            m_left(left),
            m_right(right),
            m_height(0),
            m_width(0),
            m_default_line_height(line_height),
            m_font_metrics(fm),
            m_baseline(0),
            m_text_align(align)
        {
        }

        pixel_t bottom() const
        {
            return m_top + height();
        }
        pixel_t top() const
        {
            return m_top;
        }
        pixel_t right() const
        {
            return m_left + width();
        }
        pixel_t left() const
        {
            return m_left;
        }
        pixel_t height() const
        {
            return m_height;
        }
        pixel_t width() const
        {
            return m_width;
        }
        pixel_t line_right() const
        {
            return m_right;
        }
        rendered_width get_rendered_width() const
        {
            return m_rendered_width;
        }

        void    add_item(std::unique_ptr<line_box_item> item);
        bool    can_hold(const std::unique_ptr<line_box_item>& item, white_space ws) const;
        bool    is_empty() const;
        pixel_t baseline() const;
        pixel_t top_margin() const;
        pixel_t bottom_margin() const;
        void    y_shift(pixel_t shift);

        std::list<std::unique_ptr<line_box_item>>  finish(bool                            last_box,
                                                          const containing_block_context& containing_block_size);
        std::list<std::unique_ptr<line_box_item>>  new_width(pixel_t left, pixel_t right);
        std::shared_ptr<render_item>               get_last_text_part() const;
        std::shared_ptr<render_item>               get_first_text_part() const;
        std::list<std::unique_ptr<line_box_item>>& items()
        {
            return m_items;
        }

      private:
        bool           have_last_space() const;
        bool           is_break_only() const;
        static pixel_t calc_va_baseline(const va_context& current, vertical_align va, const font_metrics& new_font,
                                        pixel_t top, pixel_t bottom);
    };
} // namespace litehtml

#endif // LITEHTML_LINE_BOX_H
