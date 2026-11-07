#ifndef LITEHTML_RENDER_BLOCK_CONTEXT_H
#define LITEHTML_RENDER_BLOCK_CONTEXT_H

#include "render_block.h"

namespace litehtml
{
    /**
     * In a block formatting context, boxes are laid out one after the other, vertically, beginning at the top of a
     * containing block.
     * https://www.w3.org/TR/CSS22/visuren.html#block-formatting
     */
    class render_item_block_context : public render_item_block
    {
      protected:
        rendered_width _render_content(pixel_t x, pixel_t y, bool second_pass,
                                       const containing_block_context& self_size, formatting_context* fmt_ctx) override;

      public:
        explicit render_item_block_context(std::shared_ptr<element> src_el) :
            render_item_block(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_block_context>(src_el());
        }
        pixel_t get_first_baseline() override;
        pixel_t get_last_baseline() override;
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_BLOCK_CONTEXT_H
