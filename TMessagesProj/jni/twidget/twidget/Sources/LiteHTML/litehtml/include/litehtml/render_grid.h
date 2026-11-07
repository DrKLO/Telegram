#ifndef LITEHTML_RENDER_GRID_H
#define LITEHTML_RENDER_GRID_H

#include "render_flex.h"

namespace litehtml
{
    class render_item_grid : public render_item_flex
    {
        rendered_width _render_content(pixel_t x, pixel_t y, bool second_pass,
                                       const containing_block_context& self_size, formatting_context* fmt_ctx) override;

      public:
        explicit render_item_grid(std::shared_ptr<element> src_el) :
            render_item_flex(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_grid>(src_el());
        }

        pixel_t get_first_baseline() override
        {
            return render_item_block::get_first_baseline();
        }
        pixel_t get_last_baseline() override
        {
            return render_item_block::get_last_baseline();
        }
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_GRID_H
