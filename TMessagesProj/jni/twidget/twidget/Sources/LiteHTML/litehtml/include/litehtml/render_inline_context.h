#ifndef LITEHTML_RENDER_INLINE_CONTEXT_H
#define LITEHTML_RENDER_INLINE_CONTEXT_H

#include "render_block.h"
#include "render_inline.h"

namespace litehtml
{
    /**
     * An inline formatting context is established by a block container box that contains no block-level boxes.
     * https://www.w3.org/TR/CSS22/visuren.html#inline-formatting
     */
    class render_item_inline_context : public render_item_block
    {
        pixel_t m_trailing = 0_px;
        std::vector<std::shared_ptr<render_text>> m_fragmented_texts;

      protected:
        void shift_content(pixel_t delta) override
        {
            for(auto& box : m_line_boxes)
            {
                box->y_shift(delta);
            }
            for(const auto& text : m_fragmented_texts)
                text->update_fragment_bounds();
        }

        /**
         *	Structure contains elements with display: inline
         *	members:
         *	- element: 		render_item with display: inline
         *	- boxes: 		rectangles represented inline element content. There are can be many boxes if content
         *			 is split into some lines
         *	- start_box:	the start position of currently calculated box
         */
        struct inlines_item
        {
            std::shared_ptr<render_item> element;
            position::vector             boxes;
            position                     start_box;

            explicit inlines_item(const std::shared_ptr<render_item>& el) :
                element(el)
            {
            }
        };

      protected:
        std::vector<std::unique_ptr<litehtml::line_box>> m_line_boxes;
        rendered_width                                   m_rendered_width;
        pixel_t                                          m_va_applied = 0_px;
        std::vector<position>                            m_ellipses;  // text-overflow: ellipsis, in the content box
        bool                                             m_cut = false;  // its texts may be cut

        /// text-overflow: ellipsis: cuts the lines too long for the box and places their "…".
        void    apply_ellipsis(pixel_t room);

        rendered_width _render_content(pixel_t x, pixel_t y, bool second_pass,
                                       const containing_block_context& self_size, formatting_context* fmt_ctx) override;
        void           fix_line_width(element_float flt, const containing_block_context& self_size,
                                      formatting_context* fmt_ctx) override;

        std::list<std::unique_ptr<line_box_item>> finish_last_box(bool                            end_of_render,
                                                                  const containing_block_context& self_size);
        void    place_inline(std::unique_ptr<line_box_item> item, const containing_block_context& self_size,
                             formatting_context* fmt_ctx);
        pixel_t new_box(const std::unique_ptr<line_box_item>& el, const containing_block_context& self_size,
                        formatting_context* fmt_ctx);
        void    apply_vertical_align() override;

      public:
        explicit render_item_inline_context(std::shared_ptr<element> src_el) :
            render_item_block(std::move(src_el))
        {
        }

        std::shared_ptr<render_item> clone() override
        {
            return std::make_shared<render_item_inline_context>(src_el());
        }

        void    draw_children(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, draw_flag flag, int zindex) override;
        pixel_t get_first_baseline() override;
        pixel_t get_last_baseline() override;
        pixel_t control_baseline_inset() const;
        size text_content_size() const;
        rendered_width measure(pixel_t x, pixel_t y, const containing_block_context& cb,
            formatting_context* fmt_ctx, bool second_pass = false, pixel_t* measured_height = nullptr) override;
    };
} // namespace litehtml

#endif // LITEHTML_RENDER_INLINE_CONTEXT_H
