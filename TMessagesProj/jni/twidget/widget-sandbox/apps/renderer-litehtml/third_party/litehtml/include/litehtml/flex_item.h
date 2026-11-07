#ifndef LITEHTML_FLEX_ITEM_H
#define LITEHTML_FLEX_ITEM_H

#include "formatting_context.h"
#include "render_item.h"

namespace litehtml
{
    class flex_line;

    enum flex_clamp_state
    {
        flex_clamp_state_unclamped,
        flex_clamp_state_inflexible,
        flex_clamp_state_min_violation,
        flex_clamp_state_max_violation
    };

    /**
     * Base class for flex item
     */
    class flex_item
    {
      public:
        std::shared_ptr<render_item> el;

        // All sizes should be interpreted as outer/margin-box sizes.
        pixel_t            base_size = 0_px;
        pixel_t            min_size  = 0_px;
        def_value<pixel_t> max_size  = 0_px;
        pixel_t main_size = 0_px; // Holds the outer hypothetical main size before distribute_free_space, and the used
                                  // outer main size after.

        int     grow                      = 0;
        int     shrink                    = 0;
        pixel_t scaled_flex_shrink_factor = 0;

        bool             frozen      = false;
        flex_clamp_state clamp_state = flex_clamp_state_unclamped;

        int order     = 0;
        int src_order = 0;

        def_value<pixel_t> auto_margin_main_start  = 0_px;
        def_value<pixel_t> auto_margin_main_end    = 0_px;
        bool               auto_margin_cross_start = false;
        bool               auto_margin_cross_end   = false;

        flex_align_items align = flex_align_items_auto;

        explicit flex_item(std::shared_ptr<render_item>& _el) :
            el(_el)
        {
        }

        virtual ~flex_item() = default;

        bool operator<(const flex_item& b) const
        {
            if(order < b.order)
            {
                return true;
            }
            if(order == b.order)
            {
                return src_order < b.src_order;
            }
            return false;
        }
        void            init(const litehtml::containing_block_context& self_size, litehtml::formatting_context* fmt_ctx,
                             flex_align_items align_items);
        virtual void    apply_main_auto_margins()                    = 0;
        virtual bool    apply_cross_auto_margins(pixel_t cross_size) = 0;
        virtual void    set_main_position(pixel_t pos)               = 0;
        virtual void    set_cross_position(pixel_t pos)              = 0;
        virtual pixel_t get_el_main_size()                           = 0;
        virtual pixel_t get_el_cross_size()                          = 0;

        void    place(flex_line& ln, pixel_t main_pos, const containing_block_context& self_size,
                      formatting_context* fmt_ctx);
        pixel_t get_last_baseline(baseline::_baseline_type type) const;
        pixel_t get_first_baseline(baseline::_baseline_type type) const;

      protected:
        virtual void direction_specific_init(const litehtml::containing_block_context& self_size,
                                             litehtml::formatting_context*             fmt_ctx) = 0;
        virtual void align_stretch(flex_line& ln, const containing_block_context& self_size,
                                   formatting_context* fmt_ctx)                                 = 0;
        virtual void align_baseline(flex_line& ln, const containing_block_context& self_size,
                                    formatting_context* fmt_ctx)                                = 0;
    };

    /**
     * Flex item with "flex-direction: row" or " flex-direction: row-reverse"
     */
    class flex_item_row_direction : public flex_item
    {
      public:
        explicit flex_item_row_direction(std::shared_ptr<render_item>& _el) :
            flex_item(_el)
        {
        }

        void    apply_main_auto_margins() override;
        bool    apply_cross_auto_margins(pixel_t cross_size) override;
        void    set_main_position(pixel_t pos) override;
        void    set_cross_position(pixel_t pos) override;
        pixel_t get_el_main_size() override;
        pixel_t get_el_cross_size() override;

      protected:
        void direction_specific_init(const litehtml::containing_block_context& self_size,
                                     litehtml::formatting_context*             fmt_ctx) override;
        void align_stretch(flex_line& ln, const containing_block_context& self_size,
                           formatting_context* fmt_ctx) override;
        void align_baseline(flex_line& ln, const containing_block_context& self_size,
                            formatting_context* fmt_ctx) override;
    };

    /**
     * Flex item with "flex-direction: column" or " flex-direction: column-reverse"
     */
    class flex_item_column_direction : public flex_item
    {
      public:
        explicit flex_item_column_direction(std::shared_ptr<render_item>& _el) :
            flex_item(_el)
        {
        }

        void    apply_main_auto_margins() override;
        bool    apply_cross_auto_margins(pixel_t cross_size) override;
        void    set_main_position(pixel_t pos) override;
        void    set_cross_position(pixel_t pos) override;
        pixel_t get_el_main_size() override;
        pixel_t get_el_cross_size() override;

      protected:
        void direction_specific_init(const litehtml::containing_block_context& self_size,
                                     litehtml::formatting_context*             fmt_ctx) override;
        void align_stretch(flex_line& ln, const containing_block_context& self_size,
                           formatting_context* fmt_ctx) override;
        void align_baseline(flex_line& ln, const containing_block_context& self_size,
                            formatting_context* fmt_ctx) override;
    };
} // namespace litehtml

#endif // LITEHTML_FLEX_ITEM_H
