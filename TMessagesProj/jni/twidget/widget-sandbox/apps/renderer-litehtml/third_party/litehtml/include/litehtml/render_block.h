#ifndef LITEHTML_RENDER_BLOCK_H
#define LITEHTML_RENDER_BLOCK_H

#include "render_item.h"

namespace litehtml
{
    class render_item_block : public render_item
    {
      protected:
        /**
         * Render block content.
         *
         * @param x horizontal position of the content
         * @param y vertical position of the content
         * @param second_pass true is this is the second pass.
         * @param self_size defines calculated size of block
         * @param fmt_ctx formatting context
         * @return rendered_width value with calculated widths
         */
        virtual rendered_width _render_content([[maybe_unused]] pixel_t x, [[maybe_unused]] pixel_t y,
                                               [[maybe_unused]] bool                            second_pass,
                                               [[maybe_unused]] const containing_block_context& self_size,
                                               [[maybe_unused]] formatting_context*             fmt_ctx)
        {
            return {0_px, 0_px};
        }
        rendered_width _render(pixel_t x, pixel_t y, const containing_block_context& containing_block_size,
                               formatting_context* fmt_ctx, bool second_pass) override;
        rendered_width place_float(const std::shared_ptr<render_item>& el, pixel_t top,
                                   const containing_block_context& self_size, formatting_context* fmt_ctx);
        virtual void   fix_line_width([[maybe_unused]] element_float                   flt,
                                      [[maybe_unused]] const containing_block_context& containing_block_size,
                                      [[maybe_unused]] formatting_context*             fmt_ctx)
        {
        }

      public:
        explicit render_item_block(std::shared_ptr<element> src_el) :
            render_item(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_block>(src_el());
        }
        std::shared_ptr<render_item> init() override;
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_BLOCK_H
