#ifndef LITEHTML_EL_SVG_H
#define LITEHTML_EL_SVG_H

#include "html_tag.h"
#include "document.h"

namespace litehtml
{
    bool  svg_geometry(const element::ptr& el, vector_path& path);
    float svg_path_length(const vector_path& path, float at, float* x, float* y);
    /// A piece of an SVG <text>: its own characters, or a <tspan>'s, styled by `style`.
    struct svg_text_run
    {
        element::ptr style;
        std::string  text;
        position     box;  // user space
    };
    /// What an SVG <text> draws, run by run, and where all of it goes, in its user space.
    bool  svg_text_runs(const element::ptr& el, document_container* container, std::vector<svg_text_run>& runs, position& box);
    bool  svg_text_box(const element::ptr& el, document_container* container, position& box);
    /// An SVG element's user space to document coordinates: its transforms, its parents', and the view box.
    bool  svg_ctm(const element::ptr& el, transform_matrix& ctm);
    bool  svg_paint_color(const element::ptr& el, bool fill, web_color& out, document_container* container);
    void svg_set_group_cache_enabled(bool enabled);

    class el_svg : public html_tag
    {
        friend bool svg_geometry(const element::ptr& el, vector_path& path);
        bool view_box(float box[4]) const;
        uint64_t m_last_paint_key = 0;
        int      m_stable_draws   = 0;
        uint64_t m_content_key    = 0;
        uint32_t m_content_generation = 0;
        bool     m_content_cacheable  = false;
        bool     m_group_cache_eligible = false;
        bool     cacheable_paint_key(uint64_t& key) const;
        bool     cached_paint_key(uint64_t& key);

      public:
        explicit el_svg(const document::ptr& doc);

        bool is_replaced() const override;
        bool has_external_intrinsic_size() const override;
        void parse_attributes() override;
        void get_content_size(size& sz, pixel_t max_width) override;
        void draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                  const std::shared_ptr<render_item>& ri) override;
        bool paint_shapes(uint_ptr hdc, const position& pos);
        element::ptr element_at(const position& pos, pixel_t x, pixel_t y);
        void view_group(const position& pos, float view[4], paint_group& group) const;

        std::shared_ptr<render_item> create_render_item(const std::shared_ptr<render_item>& parent_ri) override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_SVG_H
