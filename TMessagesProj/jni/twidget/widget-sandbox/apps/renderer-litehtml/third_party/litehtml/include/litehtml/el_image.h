#ifndef LITEHTML_EL_IMAGE_H
#define LITEHTML_EL_IMAGE_H

#include "html_tag.h"
#include "document.h"
#include "types.h"

namespace litehtml
{

    class el_image : public html_tag
    {
        std::string m_src;

      public:
        el_image(const document::ptr& doc);

        bool is_replaced() const override;
        void parse_attributes() override;
        void compute_styles(bool recursive = true) override;
        void draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                  const std::shared_ptr<render_item>& ri) override;
        void get_content_size(size& sz, pixel_t max_width) override;

        std::string dump_get_name() override;

        std::shared_ptr<render_item> create_render_item(const std::shared_ptr<render_item>& parent_ri) override;

      private:
        //		pixel_t calc_max_height(pixel_t image_height);
    };
} // namespace litehtml

#endif // LITEHTML_EL_IMAGE_H
