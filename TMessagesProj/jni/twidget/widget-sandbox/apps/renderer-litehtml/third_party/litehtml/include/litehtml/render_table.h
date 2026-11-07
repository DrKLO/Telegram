#ifndef LITEHTML_RENDER_TABLE_H
#define LITEHTML_RENDER_TABLE_H

#include "render_item.h"

namespace litehtml
{
    class render_item_table : public render_item
    {
      protected:
        // data for table rendering
        std::unique_ptr<table_grid> m_grid;
        pixel_t                     m_border_spacing_x;
        pixel_t                     m_border_spacing_y;

        rendered_width _render(pixel_t x, pixel_t y, const containing_block_context& containing_block_size,
                               formatting_context* fmt_ctx, bool second_pass) override;

      public:
        explicit render_item_table(std::shared_ptr<element> src_el);

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_table>(src_el());
        }
        void    draw_children(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, draw_flag flag,
                              int zindex) override;
        pixel_t get_draw_vertical_offset() override;
        std::shared_ptr<render_item> init() override;
    };

    class render_item_table_part : public render_item
    {
      public:
        explicit render_item_table_part(std::shared_ptr<element> src_el) :
            render_item(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_table_part>(src_el());
        }
    };

    class render_item_table_row : public render_item
    {
      public:
        explicit render_item_table_row(std::shared_ptr<element> src_el) :
            render_item(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_table_row>(src_el());
        }
        bool for_inline_boxes([[maybe_unused]] const std::function<bool(const position& box, bool first, bool last)>&
                                  process) const override;
        bool is_point_inside(pixel_t x, pixel_t y) const override;
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_TABLE_H
