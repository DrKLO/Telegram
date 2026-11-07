#ifndef LITEHTML_FLEX_LINE_H
#define LITEHTML_FLEX_LINE_H

#include "formatting_context.h"

namespace litehtml
{
    class flex_item;

    class flex_line
    {
      public:
        std::list<std::shared_ptr<flex_item>> items;
        pixel_t                               cross_start = 0; // for row direction: top. for column direction: left
        pixel_t  main_size  = 0; // sum of all items main size, initially the sum of hypothetical main sizes
        pixel_t  cross_size = 0; // sum of all items cross size
        pixel_t  base_size  = 0;
        int      num_auto_margin_main_start = 0; // number of items with auto margin left/top
        int      num_auto_margin_main_end   = 0; // number of items with auto margin right/bottom
        baseline first_baseline;
        baseline last_baseline;
        bool     reverse_main;
        bool     reverse_cross;

        flex_line(bool _reverse_main, bool _reverse_cross) :
            reverse_main(_reverse_main),
            reverse_cross(_reverse_cross)
        {
        }

        void    init(pixel_t container_main_size, bool fit_container, bool is_row_direction,
                     const litehtml::containing_block_context& self_size, litehtml::formatting_context* fmt_ctx);
        bool    distribute_main_auto_margins(pixel_t free_main_size);
        pixel_t calculate_items_position(pixel_t container_main_size, flex_justify_content justify_content,
                                         bool is_row_direction, const containing_block_context& self_size,
                                         formatting_context* fmt_ctx);

      protected:
        void distribute_free_space(pixel_t container_main_size);
        void distribute_free_space_grow(pixel_t container_main_size);
        void distribute_free_space_shrink(pixel_t container_main_size);
        bool fix_min_max_violations();
    };
} // namespace litehtml

#endif // LITEHTML_FLEX_LINE_H
