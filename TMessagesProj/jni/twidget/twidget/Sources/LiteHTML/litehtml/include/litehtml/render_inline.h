#ifndef LITEHTML_RENDER_INLINE_H
#define LITEHTML_RENDER_INLINE_H

#include "render_item.h"
#include "types.h"

namespace litehtml
{
    class render_item_inline : public render_item
    {
      protected:
        position::vector m_boxes;

      public:
        explicit render_item_inline(std::shared_ptr<element> src_el) :
            render_item(std::move(src_el))
        {
        }

        bool for_inline_boxes([[maybe_unused]] const std::function<bool(const position& box, bool first, bool last)>&
                                  process) const override
        {
            for(auto box = m_boxes.begin(); box != m_boxes.end(); ++box)
            {
                if(!process(*box, box == m_boxes.begin(), box == m_boxes.end() - 1))
                {
                    break;
                }
            }
            return true;
        }

        void set_inline_boxes(position::vector& boxes) override
        {
            m_boxes = boxes;
        }
        void add_inline_box(const position& box) override
        {
            m_boxes.emplace_back(box);
        };
        void clear_inline_boxes() override
        {
            m_boxes.clear();
        }
        pixel_t get_first_baseline() override
        {
            return src_el()->css().get_font_metrics().height - src_el()->css().get_font_metrics().base_line();
        }
        pixel_t get_last_baseline() override
        {
            return src_el()->css().get_font_metrics().height - src_el()->css().get_font_metrics().base_line();
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_inline>(src_el());
        }
        void y_shift(pixel_t shift) override
        {
            if(css().get_display() == display_inline_text)
            {
                render_item::y_shift(shift);
            }
            for(auto& box : m_boxes)
            {
                box.y += shift;
            }
        }
        bool is_point_inside(pixel_t x, pixel_t y) const override
        {
            for(auto& box : m_boxes)
            {
                if(box.is_point_inside(x, y))
                {
                    return true;
                }
            }
            return false;
        }
    };

    class render_text : public render_item_inline
    {
      public:
        std::vector<std::shared_ptr<render_text>> fragments;
        std::string fragment_text;
        bool is_fragment = false;
        // text-overflow: ellipsis: how many bytes of the text are drawn; -1 all of them.
        int cut = -1;

        explicit render_text(std::shared_ptr<element> src_el) :
            render_item_inline(std::move(src_el))
        {
        }

        void update_fragment_bounds()
        {
            if(fragments.empty())
                return;
            m_pos = fragments.front()->pos();
            for(const auto& fragment : fragments)
            {
                const position& box = fragment->pos();
                const pixel_t right = std::max(m_pos.right(), box.right());
                const pixel_t bottom = std::max(m_pos.bottom(), box.bottom());
                m_pos.x = std::min(m_pos.x, box.x);
                m_pos.y = std::min(m_pos.y, box.y);
                m_pos.width = right - m_pos.x;
                m_pos.height = bottom - m_pos.y;
            }
        }

        void y_shift(pixel_t shift) override
        {
            render_item_inline::y_shift(shift);
            for(const auto& fragment : fragments)
                fragment->y_shift(shift);
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_text>(src_el());
        }

        bool for_inline_boxes([[maybe_unused]] const std::function<bool(const position& box, bool first, bool last)>&
                                  process) const override
        {
            return false;
        }
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_INLINE_H
