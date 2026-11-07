#include "types.h"
#include "render_grid.h"
#include "html_tag.h"
#include <algorithm>

namespace
{
    using namespace litehtml;

    struct grid_track
    {
        grid_track_size size;
        pixel_t         base      = 0_px;
        pixel_t         limit     = 0_px;
        bool            infinite  = true;
        bool            repeated  = false;
        bool            occupied  = false;
        bool            collapsed = false;
        pixel_t         content     = 0_px;
        pixel_t         min_content = 0_px;
    };

    struct grid_cell
    {
        std::shared_ptr<render_item> el;
        int                          col      = -1;
        int                          row      = -1;
        int                          col_span = 1;
        int                          row_span = 1;
        pixel_t                      min_content;
        pixel_t                      max_content;
        bool                         auto_min = true; // min-width: auto
        pixel_t                      width;
        pixel_t                      height;
    };

    pixel_t resolve_gap(const css_length& gap, pixel_t base)
    {
        if(gap.units() == css_units_percentage)
        {
            return base > 0_px ? gap.calc_percent(base) : 0_px;
        }
        return pixel_t(gap.val());
    }

    pixel_t definite_size(const grid_track_size& size, pixel_t available)
    {
        if(size.max_type == grid_track_size::length)
        {
            return size.max_len.calc_percent(available);
        }
        if(size.min_type == grid_track_size::length)
        {
            return size.min_len.calc_percent(available);
        }
        return 0_px;
    }

    std::vector<grid_track> make_tracks(const grid_track_list& list, pixel_t available, pixel_t gap)
    {
        std::vector<grid_track> tracks;
        auto                    add = [&](const grid_track_size& size, bool repeated) {
            grid_track track;
            track.size     = size;
            track.repeated = repeated;
            tracks.push_back(track);
        };
        int count = 0;
        if(list.repeat_at >= 0 && !list.repeat.empty())
        {
            pixel_t fixed = 0_px;
            for(const auto& size : list.tracks)
            {
                fixed += definite_size(size, available);
            }
            pixel_t step = 0_px;
            for(const auto& size : list.repeat)
            {
                step += definite_size(size, available) + gap;
            }
            int     fixed_count = static_cast<int>(list.tracks.size());
            pixel_t room        = available - fixed - (fixed_count > 0 ? gap * pixel_t(fixed_count) : 0_px) + gap;
            if(step > 0_px && room > 0_px)
            {
                count = static_cast<int>(std::floor((room / step).value()));
            }
            count = std::max(1, std::min(count, 1000));
        }
        for(size_t i = 0; i <= list.tracks.size(); i++)
        {
            if(static_cast<int>(i) == list.repeat_at)
            {
                for(int n = 0; n < count; n++)
                {
                    for(const auto& size : list.repeat)
                    {
                        add(size, true);
                    }
                }
            }
            if(i < list.tracks.size())
            {
                add(list.tracks[i], false);
            }
        }
        return tracks;
    }

    int resolve_line(int line, int explicit_count)
    {
        if(line > 0)
        {
            return line - 1;
        }
        if(line < 0)
        {
            return std::max(0, explicit_count + 1 + line);
        }
        return -1;
    }

    void resolve_placement(const grid_placement& placement, int explicit_count, int& start, int& span)
    {
        int s = resolve_line(placement.start, explicit_count);
        int e = resolve_line(placement.end, explicit_count);
        span  = std::max(1, placement.span);
        if(s >= 0 && e >= 0)
        {
            if(e < s)
            {
                std::swap(s, e);
            }
            span  = std::max(1, e - s);
            start = s;
        } else if(s >= 0)
        {
            start = s;
        } else if(e >= 0)
        {
            start = std::max(0, e - span);
        } else
        {
            start = -1;
        }
    }

    class occupancy
    {
        std::vector<std::vector<bool>> m_rows;
        int                            m_cols;

      public:
        explicit occupancy(int cols) :
            m_cols(cols)
        {
        }

        int rows() const { return static_cast<int>(m_rows.size()); }

        bool fits(int row, int col, int row_span, int col_span) const
        {
            if(col < 0 || col + col_span > m_cols)
            {
                return false;
            }
            for(int r = row; r < row + row_span; r++)
            {
                if(r >= rows())
                {
                    continue;
                }
                for(int c = col; c < col + col_span; c++)
                {
                    if(m_rows[r][c])
                    {
                        return false;
                    }
                }
            }
            return true;
        }

        void mark(int row, int col, int row_span, int col_span)
        {
            while(rows() < row + row_span)
            {
                m_rows.emplace_back(m_cols, false);
            }
            for(int r = row; r < row + row_span; r++)
            {
                for(int c = col; c < col + col_span; c++)
                {
                    m_rows[r][c] = true;
                }
            }
        }
    };

    void place_cells(std::vector<grid_cell>& cells, int cols)
    {
        occupancy grid(cols);
        for(auto& cell : cells)
        {
            if(cell.row >= 0 && cell.col >= 0)
            {
                grid.mark(cell.row, cell.col, cell.row_span, cell.col_span);
            }
        }
        for(auto& cell : cells)
        {
            if(cell.row >= 0 && cell.col < 0)
            {
                int col = 0;
                while(col + cell.col_span <= cols && !grid.fits(cell.row, col, cell.row_span, cell.col_span))
                {
                    col++;
                }
                cell.col = col + cell.col_span <= cols ? col : 0;
                grid.mark(cell.row, cell.col, cell.row_span, cell.col_span);
            }
        }
        int cursor_row = 0;
        int cursor_col = 0;
        for(auto& cell : cells)
        {
            if(cell.row >= 0)
            {
                continue;
            }
            if(cell.col >= 0)
            {
                if(cell.col < cursor_col)
                {
                    cursor_row++;
                }
                while(!grid.fits(cursor_row, cell.col, cell.row_span, cell.col_span))
                {
                    cursor_row++;
                }
                cell.row   = cursor_row;
                cursor_col = cell.col + cell.col_span;
            } else
            {
                while(true)
                {
                    if(cursor_col + cell.col_span > cols)
                    {
                        cursor_col = 0;
                        cursor_row++;
                    }
                    if(grid.fits(cursor_row, cursor_col, cell.row_span, cell.col_span))
                    {
                        break;
                    }
                    cursor_col++;
                }
                cell.row    = cursor_row;
                cell.col    = cursor_col;
                cursor_col += cell.col_span;
            }
            grid.mark(cell.row, cell.col, cell.row_span, cell.col_span);
        }
    }

    void size_columns(std::vector<grid_track>& tracks, const std::vector<grid_cell>& cells, pixel_t available,
                      pixel_t gap, bool stretch_auto)
    {
        for(auto& track : tracks)
        {
            track.base     = 0_px;
            track.limit    = 0_px;
            track.infinite = true;
            if(track.size.min_type == grid_track_size::length)
            {
                track.base = track.size.min_len.calc_percent(available);
            }
            if(track.size.max_type == grid_track_size::length)
            {
                track.limit    = std::max(track.base, track.size.max_len.calc_percent(available));
                track.infinite = false;
            }
        }
        for(const auto& cell : cells)
        {
            if(cell.col_span != 1)
            {
                continue;
            }
            auto& track       = tracks[cell.col];
            track.content     = std::max(track.content, cell.max_content);
            track.min_content = std::max(track.min_content, cell.min_content);
            switch(track.size.min_type)
            {
            case grid_track_size::automatic:
            case grid_track_size::min_content:
                track.base = std::max(track.base, cell.min_content);
                break;
            case grid_track_size::max_content:
                track.base = std::max(track.base, cell.max_content);
                break;
            default:
                break;
            }
            if(track.size.max_type == grid_track_size::automatic || track.size.max_type == grid_track_size::max_content)
            {
                track.limit = std::max(track.limit, cell.max_content);
            } else if(track.size.max_type == grid_track_size::min_content)
            {
                track.limit = std::max(track.limit, cell.min_content);
            }
        }
        for(const auto& cell : cells)
        {
            if(cell.col_span == 1)
            {
                continue;
            }
            // Across a flexible track an item's automatic minimum is zero (css-grid 6.6), and a minimum of its own
            // grows the flexible tracks only (css-grid 12.5).
            bool spans_flexible = false;
            for(int c = cell.col; c < cell.col + cell.col_span; c++)
            {
                spans_flexible = spans_flexible || (tracks[c].size.max_type == grid_track_size::fraction && !tracks[c].collapsed);
            }
            if(spans_flexible && cell.auto_min)
            {
                continue;
            }
            auto grows = [&](const grid_track& track) {
                return track.size.min_type != grid_track_size::length && !track.collapsed &&
                       (!spans_flexible || track.size.max_type == grid_track_size::fraction);
            };
            pixel_t spanned   = gap * pixel_t(cell.col_span - 1);
            int     intrinsic = 0;
            for(int c = cell.col; c < cell.col + cell.col_span; c++)
            {
                spanned += tracks[c].base;
                if(grows(tracks[c]))
                {
                    intrinsic++;
                }
            }
            if(intrinsic > 0 && cell.min_content > spanned)
            {
                pixel_t share = (cell.min_content - spanned) / pixel_t(intrinsic);
                for(int c = cell.col; c < cell.col + cell.col_span; c++)
                {
                    if(grows(tracks[c]))
                    {
                        tracks[c].base += share;
                    }
                }
            }
        }
        for(auto& track : tracks)
        {
            if(track.collapsed)
            {
                track.base  = 0_px;
                track.limit = 0_px;
                continue;
            }
            if(track.size.max_type != grid_track_size::length && track.size.max_type != grid_track_size::fraction)
            {
                track.infinite = false;
            }
            track.limit = std::max(track.limit, track.base);
        }

        int visible = 0;
        for(const auto& track : tracks)
        {
            if(!track.collapsed)
            {
                visible++;
            }
        }
        pixel_t gaps = visible > 1 ? gap * pixel_t(visible - 1) : 0_px;
        auto    used = [&]() {
            pixel_t sum = gaps;
            for(const auto& track : tracks)
            {
                sum += track.base;
            }
            return sum;
        };

        for(int guard = 0; guard < 8; guard++)
        {
            pixel_t free = available - used();
            if(free <= 0.5_px)
            {
                break;
            }
            int growing = 0;
            for(const auto& track : tracks)
            {
                if(!track.collapsed && track.size.max_type != grid_track_size::fraction && track.base < track.limit)
                {
                    growing++;
                }
            }
            if(growing == 0)
            {
                break;
            }
            pixel_t share = free / pixel_t(growing);
            for(auto& track : tracks)
            {
                if(!track.collapsed && track.size.max_type != grid_track_size::fraction && track.base < track.limit)
                {
                    track.base = std::min(track.limit, track.base + share);
                }
            }
        }

        std::vector<grid_track*> flexible;
        for(auto& track : tracks)
        {
            if(!track.collapsed && track.size.max_type == grid_track_size::fraction)
            {
                flexible.push_back(&track);
            }
        }
        if(!flexible.empty())
        {
            std::vector<bool> frozen(flexible.size(), false);
            pixel_t           fr_size = 0_px;
            for(int guard = 0; guard < 16; guard++)
            {
                pixel_t rest   = available - gaps;
                float   sum_fr = 0;
                for(const auto& track : tracks)
                {
                    if(track.collapsed || track.size.max_type == grid_track_size::fraction)
                    {
                        continue;
                    }
                    rest -= track.base;
                }
                for(size_t i = 0; i < flexible.size(); i++)
                {
                    if(frozen[i])
                    {
                        rest -= flexible[i]->base;
                    } else
                    {
                        sum_fr += flexible[i]->size.fr;
                    }
                }
                fr_size = sum_fr > 1 ? rest / pixel_t(sum_fr) : rest;
                bool changed = false;
                for(size_t i = 0; i < flexible.size(); i++)
                {
                    if(!frozen[i] && fr_size * pixel_t(flexible[i]->size.fr) < flexible[i]->base)
                    {
                        frozen[i] = true;
                        changed   = true;
                    }
                }
                if(!changed)
                {
                    break;
                }
            }
            for(size_t i = 0; i < flexible.size(); i++)
            {
                if(!frozen[i])
                {
                    flexible[i]->base = std::max(flexible[i]->base, fr_size * pixel_t(flexible[i]->size.fr));
                }
            }
        } else if(stretch_auto)
        {
            pixel_t free = available - used();
            int     autos = 0;
            for(const auto& track : tracks)
            {
                if(!track.collapsed && track.size.max_type == grid_track_size::automatic)
                {
                    autos++;
                }
            }
            if(free > 0_px && autos > 0)
            {
                pixel_t share = free / pixel_t(autos);
                for(auto& track : tracks)
                {
                    if(!track.collapsed && track.size.max_type == grid_track_size::automatic)
                    {
                        track.base += share;
                    }
                }
            }
        }
    }

    void distribute_content(pixel_t free, flex_justify_content justify, int count, pixel_t& start, pixel_t& between)
    {
        start   = 0_px;
        between = 0_px;
        if(free <= 0_px || count <= 0)
        {
            return;
        }
        switch(justify)
        {
        case flex_justify_content_center:
            start = free / 2_px;
            break;
        case flex_justify_content_end:
        case flex_justify_content_flex_end:
        case flex_justify_content_right:
            start = free;
            break;
        case flex_justify_content_space_between:
            between = count > 1 ? free / pixel_t(count - 1) : 0_px;
            break;
        case flex_justify_content_space_around:
            between = free / pixel_t(count);
            start   = between / 2_px;
            break;
        case flex_justify_content_space_evenly:
            between = free / pixel_t(count + 1);
            start   = between;
            break;
        default:
            break;
        }
    }

    bool is_stretch(int align)
    {
        align &= 0xFF;
        return align == flex_align_items_normal || align == flex_align_items_stretch ||
               align == flex_align_items_auto;
    }

    pixel_t align_offset(int align, pixel_t free)
    {
        switch(align & 0xFF)
        {
        case flex_align_items_center:
            return free / 2_px;
        case flex_align_items_end:
        case flex_align_items_flex_end:
        case flex_align_items_self_end:
            return free;
        default:
            return 0_px;
        }
    }
} // namespace

litehtml::rendered_width litehtml::render_item_grid::_render_content(pixel_t x, pixel_t y, bool /*second_pass*/,
                                                                     const containing_block_context& self_size,
                                                                     formatting_context*             fmt_ctx)
{
    pixel_t available  = std::max(0_px, pixel_t(self_size.render_width));
    pixel_t column_gap = resolve_gap(css().get_column_gap(), available);
    bool    definite_height =
        self_size.height.type == containing_block_context::cbc_value_type_absolute && self_size.height.value > 0_px;
    pixel_t container_height = definite_height ? pixel_t(self_size.height.value) - box_sizing_height() : 0_px;
    pixel_t row_gap          = resolve_gap(css().get_row_gap(), container_height);
    // A % height of an item resolves against this grid's height only when it has one: not an ancestor's.
    containing_block_context item_size = self_size;
    if(self_size.height.type == containing_block_context::cbc_value_type_auto)
    {
        item_size.height = containing_block_context::typed_pixel(0_px, containing_block_context::cbc_value_type_auto);
    }

    std::vector<grid_track> columns = make_tracks(css().get_grid_template_columns(), available, column_gap);
    int explicit_columns            = static_cast<int>(columns.size());
    int explicit_rows               = static_cast<int>(css().get_grid_template_rows().tracks.size());

    std::vector<grid_cell> cells;
    for(const auto& el : m_children)
    {
        auto position = el->src_el()->css().get_position();
        if(position == element_position_absolute || position == element_position_fixed)
        {
            // Out of flow, but render_positioned() takes the shrink-to-fit size from this pass, as the
            // block context does (render_block_context.cpp); left unrendered the box stays 0 x 0.
            if(el->src_el()->css().get_display() != display_none)
            {
                el->render_out_of_flow(0_px, self_size, fmt_ctx);
            }
            continue;
        }
        grid_cell cell;
        cell.el = el;
        resolve_placement(el->src_el()->css().get_grid_column(), explicit_columns, cell.col, cell.col_span);
        resolve_placement(el->src_el()->css().get_grid_row(), explicit_rows, cell.row, cell.row_span);
        cells.push_back(cell);
    }
    std::stable_sort(cells.begin(), cells.end(), [](const grid_cell& a, const grid_cell& b) {
        return a.el->src_el()->css().get_order() < b.el->src_el()->css().get_order();
    });

    int column_count = std::max(explicit_columns, 1);
    for(const auto& cell : cells)
    {
        column_count = std::max(column_count, (cell.col >= 0 ? cell.col : 0) + cell.col_span);
    }
    while(static_cast<int>(columns.size()) < column_count)
    {
        columns.emplace_back();
    }
    place_cells(cells, column_count);

    int row_count = explicit_rows;
    for(const auto& cell : cells)
    {
        row_count = std::max(row_count, cell.row + cell.row_span);
        for(int c = cell.col; c < cell.col + cell.col_span; c++)
        {
            columns[c].occupied = true;
        }
    }
    if(css().get_grid_template_columns().auto_fit)
    {
        for(auto& track : columns)
        {
            track.collapsed = track.repeated && !track.occupied;
        }
    }

    for(auto& cell : cells)
    {
        const auto& width = cell.el->src_el()->css().get_width();
        if(!width.is_predefined() && width.units() != css_units_percentage && width.units() != css_units_calc)
        {
            cell.el->render(0_px, 0_px, item_size.new_width(available), fmt_ctx);
            cell.max_content = cell.min_content = cell.el->width();
        } else
        {
            auto measured =
                cell.el->render(0_px, 0_px, item_size.new_width(available, containing_block_context::size_mode_content), fmt_ctx);
            cell.max_content = measured.natural_width;
            // A replaced element with a % width compresses to nothing (css-sizing 5.2.2): its % is of the area.
            cell.min_content = cell.el->src_el()->is_replaced() && !width.is_predefined() ? 0_px : measured.min_width;
        }
        const auto& minimum = cell.el->css().get_min_width();
        cell.auto_min = minimum.is_predefined();
        if(!minimum.is_predefined())
        {
            cell.min_content = std::max(0_px, minimum.calc_percent(available) - cell.el->box_sizing_width()) + cell.el->content_offset_width();
        }
    }

    flex_justify_content justify = css().get_flex_justify_content();
    bool stretch_auto = justify == flex_justify_content_normal || justify == flex_justify_content_stretch ||
                        justify == flex_justify_content_flex_start;
    size_columns(columns, cells, available, column_gap, stretch_auto);

    rendered_width ret;
    {
        int visible = 0;
        for(const auto& track : columns)
        {
            if(track.collapsed)
            {
                continue;
            }
            visible++;
            pixel_t fixed_min = track.size.min_type == grid_track_size::length ? track.size.min_len.calc_percent(available) : 0_px;
            pixel_t fixed_max = track.size.max_type == grid_track_size::length ? track.size.max_len.calc_percent(available) : 0_px;
            if(track.size.max_type == grid_track_size::length)
            {
                ret.natural_width += std::max(fixed_min, fixed_max);
            } else
            {
                ret.natural_width += std::max(fixed_min, track.content);
            }
            ret.min_width += std::max(fixed_min, track.min_content);
        }
        pixel_t gaps       = visible > 1 ? column_gap * pixel_t(visible - 1) : 0_px;
        ret.natural_width += gaps;
        ret.min_width     += gaps;
    }

    std::vector<pixel_t> column_x(columns.size() + 1, 0_px);
    {
        pixel_t used    = 0_px;
        int     visible = 0;
        for(const auto& track : columns)
        {
            if(!track.collapsed)
            {
                used += track.base;
                visible++;
            }
        }
        used += visible > 1 ? column_gap * pixel_t(visible - 1) : 0_px;
        pixel_t start, between;
        distribute_content(available - used, justify, visible, start, between);
        pixel_t pos   = start;
        bool    first = true;
        for(size_t c = 0; c < columns.size(); c++)
        {
            if(!columns[c].collapsed)
            {
                if(!first)
                {
                    pos += column_gap + between;
                }
                first = false;
            }
            column_x[c]  = pos;
            pos         += columns[c].base;
        }
        column_x[columns.size()] = pos;
    }
    auto area_width = [&](const grid_cell& cell) {
        return column_x[cell.col + cell.col_span - 1] + columns[cell.col + cell.col_span - 1].base - column_x[cell.col];
    };

    flex_align_items justify_items = css().get_justify_items();
    for(auto& cell : cells)
    {
        const auto& item_css = cell.el->src_el()->css();
        int         justify_self =
            item_css.get_justify_self() == flex_align_items_auto ? justify_items : item_css.get_justify_self();
        pixel_t area = area_width(cell);
        if(!item_css.get_width().is_predefined())
        {
            cell.el->render(0_px, 0_px, item_size.new_width(area), fmt_ctx);
        } else
        {
            pixel_t outer = is_stretch(justify_self) ? area : std::min(area, cell.max_content);
            cell.el->render(0_px, 0_px,
                            item_size.new_width(outer - cell.el->render_offset_width(),
                                                containing_block_context::size_mode_exact_width),
                            fmt_ctx);
        }
        cell.width  = cell.el->width();
        cell.height = cell.el->height();
    }

    const grid_track_list& template_rows = css().get_grid_template_rows();
    const grid_track_list& auto_rows     = css().get_grid_auto_rows();
    std::vector<grid_track> rows(row_count);
    for(int r = 0; r < row_count; r++)
    {
        if(r < explicit_rows)
        {
            rows[r].size = template_rows.tracks[r];
        } else if(!auto_rows.tracks.empty())
        {
            rows[r].size = auto_rows.tracks[(r - explicit_rows) % auto_rows.tracks.size()];
        }
        if(rows[r].size.min_type == grid_track_size::length)
        {
            rows[r].base = rows[r].size.min_len.calc_percent(container_height);
        }
        if(rows[r].size.max_type == grid_track_size::length)
        {
            rows[r].base = std::max(rows[r].base, rows[r].size.max_len.calc_percent(container_height));
        }
    }
    auto content_sized = [](const grid_track& track) {
        return track.size.max_type != grid_track_size::length;
    };
    for(const auto& cell : cells)
    {
        if(cell.row_span == 1 && content_sized(rows[cell.row]))
        {
            rows[cell.row].base = std::max(rows[cell.row].base, cell.height);
        }
    }
    for(const auto& cell : cells)
    {
        if(cell.row_span == 1)
        {
            continue;
        }
        pixel_t spanned = row_gap * pixel_t(cell.row_span - 1);
        int     flexible_rows = 0;
        for(int r = cell.row; r < cell.row + cell.row_span; r++)
        {
            spanned += rows[r].base;
            if(content_sized(rows[r]))
            {
                flexible_rows++;
            }
        }
        if(cell.height > spanned)
        {
            if(flexible_rows == 0)
            {
                rows[cell.row + cell.row_span - 1].base += cell.height - spanned;
            } else
            {
                pixel_t share = (cell.height - spanned) / pixel_t(flexible_rows);
                for(int r = cell.row; r < cell.row + cell.row_span; r++)
                {
                    if(content_sized(rows[r]))
                    {
                        rows[r].base += share;
                    }
                }
            }
        }
    }
    if(definite_height && row_count > 0)
    {
        // As the flexible columns: a share per fr, and a row whose content is more than its share keeps its content.
        std::vector<bool> frozen(rows.size(), false);
        pixel_t           fr_size = 0_px;
        for(int guard = 0; guard < 16; guard++)
        {
            pixel_t rest   = container_height - row_gap * pixel_t(row_count - 1);
            float   sum_fr = 0;
            for(size_t r = 0; r < rows.size(); r++)
            {
                if(rows[r].size.max_type != grid_track_size::fraction || frozen[r])
                {
                    rest -= rows[r].base;
                } else
                {
                    sum_fr += rows[r].size.fr;
                }
            }
            fr_size      = sum_fr > 1 ? rest / pixel_t(sum_fr) : rest;
            bool changed = false;
            for(size_t r = 0; r < rows.size(); r++)
            {
                if(rows[r].size.max_type == grid_track_size::fraction && !frozen[r] && fr_size * pixel_t(rows[r].size.fr) < rows[r].base)
                {
                    frozen[r] = true;
                    changed   = true;
                }
            }
            if(!changed)
            {
                break;
            }
        }
        for(size_t r = 0; r < rows.size(); r++)
        {
            if(rows[r].size.max_type == grid_track_size::fraction && !frozen[r])
            {
                rows[r].base = std::max(rows[r].base, fr_size * pixel_t(rows[r].size.fr));
            }
        }
    }

    pixel_t row_start   = 0_px;
    pixel_t row_between = 0_px;
    if(definite_height && row_count > 0)
    {
        pixel_t used = row_gap * pixel_t(row_count - 1);
        for(const auto& row : rows)
        {
            used += row.base;
        }
        pixel_t            free  = container_height - used;
        flex_align_content align = css().get_flex_align_content();
        if(free > 0_px && align == flex_align_content_stretch)
        {
            int stretchy = 0;
            for(const auto& row : rows)
            {
                if(row.size.max_type == grid_track_size::automatic)
                {
                    stretchy++;
                }
            }
            for(auto& row : rows)
            {
                if(stretchy > 0 && row.size.max_type == grid_track_size::automatic)
                {
                    row.base += free / pixel_t(stretchy);
                }
            }
        } else if(free > 0_px)
        {
            switch(align)
            {
            case flex_align_content_center:
                row_start = free / 2_px;
                break;
            case flex_align_content_end:
            case flex_align_content_flex_end:
                row_start = free;
                break;
            case flex_align_content_space_between:
                row_between = row_count > 1 ? free / pixel_t(row_count - 1) : 0_px;
                break;
            case flex_align_content_space_around:
                row_between = free / pixel_t(row_count);
                row_start   = row_between / 2_px;
                break;
            default:
                break;
            }
        }
    }

    std::vector<pixel_t> row_y(rows.size() + 1, 0_px);
    {
        pixel_t pos = row_start;
        for(size_t r = 0; r < rows.size(); r++)
        {
            row_y[r]  = pos;
            pos      += rows[r].base + row_gap + row_between;
        }
        row_y[rows.size()] = rows.empty() ? 0_px : pos - row_gap - row_between;
    }

    flex_align_items align_items = css().get_flex_align_items();
    for(auto& cell : cells)
    {
        const auto& item_css   = cell.el->src_el()->css();
        int         align_self = item_css.get_flex_align_self() == flex_align_items_auto ? align_items
                                                                                         : item_css.get_flex_align_self();
        int justify_self =
            item_css.get_justify_self() == flex_align_items_auto ? justify_items : item_css.get_justify_self();
        pixel_t area_w = area_width(cell);
        pixel_t area_h = row_y[cell.row + cell.row_span - 1] + rows[cell.row + cell.row_span - 1].base - row_y[cell.row];
        if(is_stretch(align_self) && item_css.get_height().is_predefined() && cell.height < area_h)
        {
            cell.el->render(0_px, 0_px,
                            self_size.new_width_height(cell.el->pos().width + cell.el->box_sizing_width(),
                                                       area_h - cell.el->content_offset_height() +
                                                           cell.el->box_sizing_height(),
                                                       containing_block_context::size_mode_exact_width |
                                                           containing_block_context::size_mode_exact_height),
                            fmt_ctx);
        }
        pixel_t offset_x = align_offset(justify_self, area_w - cell.el->width());
        pixel_t offset_y = is_stretch(align_self) ? 0_px : align_offset(align_self, area_h - cell.el->height());
        cell.el->pos().x = column_x[cell.col] + offset_x + cell.el->content_offset_left();
        cell.el->pos().y = row_y[cell.row] + offset_y + cell.el->content_offset_top();
    }

    m_pos.height = row_y[rows.size()];
    m_pos.move_to(x, y);
    m_pos.x += content_offset_left();
    m_pos.y += content_offset_top();

    return ret;
}
