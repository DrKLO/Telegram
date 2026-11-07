#ifndef LITEHTML_TABLE_H
#define LITEHTML_TABLE_H

#include <vector>
#include <memory>
#include "css_length.h"
#include "types.h"

namespace litehtml
{
    class render_item;

    struct table_row
    {
        using vector = std::vector<table_row>;

        pixel_t                      height;
        pixel_t                      border_top;
        pixel_t                      border_bottom;
        std::shared_ptr<render_item> el_row;
        pixel_t                      top;
        pixel_t                      bottom;
        css_length                   css_height;
        pixel_t                      min_height;

        table_row()
        {
            min_height    = 0;
            top           = 0;
            bottom        = 0;
            border_bottom = 0;
            border_top    = 0;
            height        = 0;
            el_row        = nullptr;
            css_height.predef(0);
        }

        table_row(pixel_t h, const std::shared_ptr<render_item>& row);

        table_row(const table_row& val)
        {
            min_height    = val.min_height;
            top           = val.top;
            bottom        = val.bottom;
            border_bottom = val.border_bottom;
            border_top    = val.border_top;
            height        = val.height;
            css_height    = val.css_height;
            el_row        = val.el_row;
        }

        table_row(table_row&& val) noexcept
        {
            min_height    = val.min_height;
            top           = val.top;
            bottom        = val.bottom;
            border_bottom = val.border_bottom;
            border_top    = val.border_top;
            height        = val.height;
            css_height    = val.css_height;
            el_row        = std::move(val.el_row);
        }
    };

    struct table_column
    {
        using vector = std::vector<table_column>;

        pixel_t    min_width;
        pixel_t    max_width;
        pixel_t    width;
        css_length css_width;
        pixel_t    border_left;
        pixel_t    border_right;
        pixel_t    left;
        pixel_t    right;

        table_column()
        {
            left         = 0;
            right        = 0;
            border_left  = 0;
            border_right = 0;
            min_width    = 0;
            max_width    = 0;
            width        = 0;
            css_width.predef(0);
        }

        table_column(pixel_t min_w, pixel_t max_w)
        {
            left         = 0;
            right        = 0;
            border_left  = 0;
            border_right = 0;
            max_width    = max_w;
            min_width    = min_w;
            width        = 0;
            css_width.predef(0);
        }

        table_column(const table_column& val)
        {
            left         = val.left;
            right        = val.right;
            border_left  = val.border_left;
            border_right = val.border_right;
            max_width    = val.max_width;
            min_width    = val.min_width;
            width        = val.width;
            css_width    = val.css_width;
        }
    };

    class table_column_accessor
    {
      public:
        virtual pixel_t& get(table_column& col) = 0;

      protected:
        ~table_column_accessor() = default;
    };

    class table_column_accessor_max_width final : public table_column_accessor
    {
      public:
        pixel_t& get(table_column& col) override;
    };

    class table_column_accessor_min_width final : public table_column_accessor
    {
      public:
        pixel_t& get(table_column& col) override;
    };

    class table_column_accessor_width final : public table_column_accessor
    {
      public:
        pixel_t& get(table_column& col) override;
    };

    struct table_cell
    {
        std::shared_ptr<render_item> el;
        int                          colspan;
        int                          rowspan;
        pixel_t                      min_width;
        pixel_t                      min_height;
        pixel_t                      max_width;
        pixel_t                      max_height;
        pixel_t                      width;
        pixel_t                      height;
        margins                      borders;

        table_cell()
        {
            min_width  = 0;
            min_height = 0;
            max_width  = 0;
            max_height = 0;
            width      = 0;
            height     = 0;
            colspan    = 1;
            rowspan    = 1;
            el         = nullptr;
        }

        table_cell(const table_cell& val)
        {
            el         = val.el;
            colspan    = val.colspan;
            rowspan    = val.rowspan;
            width      = val.width;
            height     = val.height;
            min_width  = val.min_width;
            min_height = val.min_height;
            max_width  = val.max_width;
            max_height = val.max_height;
            borders    = val.borders;
        }

        table_cell(table_cell&& val) noexcept
        {
            el         = std::move(val.el);
            colspan    = val.colspan;
            rowspan    = val.rowspan;
            width      = val.width;
            height     = val.height;
            min_width  = val.min_width;
            min_height = val.min_height;
            max_width  = val.max_width;
            max_height = val.max_height;
            borders    = val.borders;
        }
    };

    class table_grid
    {
      public:
        using rows = std::vector<std::vector<table_cell>>;

      private:
        int                  m_rows_count = 0;
        int                  m_cols_count = 0;
        rows                 m_cells;
        table_column::vector m_columns;
        table_row::vector    m_rows;
        pixel_t              m_top_captions_height    = 0_px;
        pixel_t              m_bottom_captions_height = 0_px;

        std::vector<std::shared_ptr<render_item>> m_captions;

      public:
        void          clear();
        void          begin_row(const std::shared_ptr<render_item>& row);
        void          add_cell(const std::shared_ptr<render_item>& el);
        bool          is_rowspanned(int r, int c);
        void          finish();
        table_cell*   cell(int t_col, int t_row);
        table_column& column(int c)
        {
            return m_columns[c];
        }
        table_row& row(int r)
        {
            return m_rows[r];
        }
        std::vector<std::shared_ptr<render_item>>& captions()
        {
            return m_captions;
        }

        int rows_count() const
        {
            return m_rows_count;
        }
        int cols_count() const
        {
            return m_cols_count;
        }

        void top_captions_height(pixel_t height)
        {
            m_top_captions_height = height;
        }
        pixel_t top_captions_height() const
        {
            return m_top_captions_height;
        }
        void bottom_captions_height(pixel_t height)
        {
            m_bottom_captions_height = height;
        }
        pixel_t bottom_captions_height() const
        {
            return m_bottom_captions_height;
        }

        void    distribute_max_width(pixel_t width, int start, int end);
        void    distribute_min_width(pixel_t width, int start, int end);
        void    distribute_width(pixel_t width, int start, int end);
        void    distribute_width(pixel_t width, int start, int end, table_column_accessor* acc);
        pixel_t calc_table_width(pixel_t block_width, bool is_auto, pixel_t& min_table_width, pixel_t& max_table_width);
        void    calc_horizontal_positions(const margins& table_borders, border_collapse bc, pixel_t bdr_space_x);
        void    calc_vertical_positions(const margins& table_borders, border_collapse bc, pixel_t bdr_space_y);
        void    calc_rows_height(pixel_t blockHeight, pixel_t borderSpacingY);
    };
} // namespace litehtml

#endif // LITEHTML_TABLE_H
