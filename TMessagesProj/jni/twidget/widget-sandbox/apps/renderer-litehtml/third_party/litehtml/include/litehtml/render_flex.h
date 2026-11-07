#ifndef LITEHTML_RENDER_FLEX_H
#define LITEHTML_RENDER_FLEX_H

#include "render_block.h"
#include "flex_item.h"
#include "flex_line.h"

namespace litehtml
{
    class render_item_flex : public render_item_block
    {
        std::list<flex_line> m_lines;

        std::list<flex_line> get_lines(const containing_block_context& self_size, formatting_context* fmt_ctx,
                                       bool is_row_direction, pixel_t container_main_size, bool single_line);
        rendered_width _render_content(pixel_t x, pixel_t y, bool second_pass,
                                       const containing_block_context& self_size, formatting_context* fmt_ctx) override;

      public:
        explicit render_item_flex(std::shared_ptr<element> src_el) :
            render_item_block(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_flex>(src_el());
        }
        std::shared_ptr<render_item> init() override;

        pixel_t get_first_baseline() override;
        pixel_t get_last_baseline() override;
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_FLEX_H
