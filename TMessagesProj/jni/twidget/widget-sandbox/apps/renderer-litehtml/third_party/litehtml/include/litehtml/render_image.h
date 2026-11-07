#ifndef LITEHTML_RENDER_IMAGE_H
#define LITEHTML_RENDER_IMAGE_H

#include "render_item.h"

namespace litehtml
{
    class render_item_image : public render_item
    {
      protected:
        pixel_t        calc_max_height(pixel_t image_height, pixel_t containing_block_height);
        rendered_width _render(pixel_t x, pixel_t y, const containing_block_context& containing_block_size,
                               formatting_context* fmt_ctx, bool second_pass) override;

      public:
        explicit render_item_image(std::shared_ptr<element> src_el) :
            render_item(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_image>(src_el());
        }
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_IMAGE_H
