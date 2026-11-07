#include "table.h"
#include "element.h"
#include "render_item.h"
#include "types.h"

void litehtml::table_grid::add_cell(const std::shared_ptr<render_item>& el)
{
    table_cell cell;
    cell.el      = el;
    cell.colspan = atoi(el->src_el()->get_attr("colspan", "1"));
    cell.rowspan = atoi(el->src_el()->get_attr("rowspan", "1"));
    cell.borders = el->get_borders();

    while(is_rowspanned(static_cast<int>(m_cells.size()) - 1, static_cast<int>(m_cells.back().size())))
    {
        m_cells.back().emplace_back();
    }

    m_cells.back().push_back(cell);
    for(int i = 1; i < cell.colspan; i++)
    {
        table_cell empty_cell;
        m_cells.back().push_back(empty_cell);
    }
}

void litehtml::table_grid::begin_row(const std::shared_ptr<render_item>& row)
{
    std::vector<table_cell> r;
    m_cells.push_back(r);

    m_rows.emplace_back(0, row);
}

bool litehtml::table_grid::is_rowspanned(int r, int c)
{
    for(int row = r - 1; row >= 0; row--)
    {
        if(c < static_cast<int>(m_cells[row].size()))
        {
            if(m_cells[row][c].rowspan > 1)
            {
                if(m_cells[row][c].rowspan >= r - row + 1)
                {
                    return true;
                }
            }
        }
    }
    return false;
}

void litehtml::table_grid::finish()
{
    m_rows_count = static_cast<int>(m_cells.size());
    m_cols_count = 0;
    for(auto& cell : m_cells)
    {
        m_cols_count = std::max(m_cols_count, static_cast<int>(cell.size()));
    }
    for(auto& cell : m_cells)
    {
        for(int j = static_cast<int>(cell.size()); j < m_cols_count; j++)
        {
            table_cell empty_cell;
            cell.push_back(empty_cell);
        }
    }

    m_columns.clear();
    for(int i = 0; i < m_cols_count; i++)
    {
        m_columns.emplace_back(0, 0);
    }

    for(int col = 0; col < m_cols_count; col++)
    {
        for(int row = 0; row < m_rows_count; row++)
        {
            if(cell(col, row)->el)
            {
                // find minimum left border width
                if(m_columns[col].border_left != 0_px)
                {
                    m_columns[col].border_left = std::min(m_columns[col].border_left, cell(col, row)->borders.left);
                } else
                {
                    m_columns[col].border_left = cell(col, row)->borders.left;
                }
                // find minimum right border width
                if(m_columns[col].border_right != 0_px)
                {
                    m_columns[col].border_right = std::min(m_columns[col].border_right, cell(col, row)->borders.right);
                } else
                {
                    m_columns[col].border_right = cell(col, row)->borders.right;
                }
                // find minimum top border width
                if(m_rows[row].border_top != 0_px)
                {
                    m_rows[row].border_top = std::min(m_rows[row].border_top, cell(col, row)->borders.top);
                } else
                {
                    m_rows[row].border_top = cell(col, row)->borders.top;
                }
                // find minimum bottom border width
                if(m_rows[row].border_bottom != 0_px)
                {
                    m_rows[row].border_bottom = std::min(m_rows[row].border_bottom, cell(col, row)->borders.bottom);
                } else
                {
                    m_rows[row].border_bottom = cell(col, row)->borders.bottom;
                }
            }

            if(cell(col, row)->el && cell(col, row)->colspan <= 1)
            {
                if(!cell(col, row)->el->src_el()->css().get_width().is_predefined() &&
                   m_columns[col].css_width.is_predefined())
                {
                    m_columns[col].css_width = cell(col, row)->el->src_el()->css().get_width();
                }
            }
        }
    }

    for(int col = 0; col < m_cols_count; col++)
    {
        for(int row = 0; row < m_rows_count; row++)
        {
            if(cell(col, row)->el && cell(col, row)->colspan == 1)
            {
                cell(col, row)->el->src_el()->css_w().set_width(m_columns[col].css_width);
            }
        }
    }
}

litehtml::table_cell* litehtml::table_grid::cell(int t_col, int t_row)
{
    if(t_col >= 0 && t_col < m_cols_count && t_row >= 0 && t_row < m_rows_count)
    {
        return &m_cells[t_row][t_col];
    }
    return nullptr;
}

void litehtml::table_grid::distribute_max_width(pixel_t width, int start, int end)
{
    table_column_accessor_max_width selector;
    distribute_width(width, start, end, &selector);
}

void litehtml::table_grid::distribute_min_width(pixel_t width, int start, int end)
{
    table_column_accessor_min_width selector;
    distribute_width(width, start, end, &selector);
}

void litehtml::table_grid::distribute_width(pixel_t width, int start, int end, table_column_accessor* acc)
{
    if(start < 0 || start >= m_cols_count || end < 0 || end >= m_cols_count)
    {
        return;
    }

    pixel_t cols_width;
    for(int col = start; col <= end; col++)
    {
        cols_width += m_columns[col].max_width;
    }

    pixel_t add = width / pixel_t(end - start + 1);
    pixel_t added_width;
    for(int col = start; col <= end; col++)
    {
        if(cols_width != 0_px)
        {
            add = width * (m_columns[col].max_width / cols_width);
        }
        added_width              += add;
        acc->get(m_columns[col]) += add;
    }
    if(added_width < width)
    {
        acc->get(m_columns[start]) += width - added_width;
    }
}

void litehtml::table_grid::distribute_width(pixel_t width, int start, int end)
{
    if(start < 0 || start >= m_cols_count || end < 0 || end >= m_cols_count)
    {
        return;
    }

    std::vector<table_column*> distribute_columns;

    for(int step = 0; step < 3; step++)
    {
        distribute_columns.clear();

        switch(step)
        {
        case 0:
            {
                // distribute between the columns with width == auto
                for(int col = start; col <= end; col++)
                {
                    if(m_columns[col].css_width.is_predefined())
                    {
                        distribute_columns.push_back(&m_columns[col]);
                    }
                }
            }
            break;
        case 1:
            {
                // distribute between the columns with percents
                for(int col = start; col <= end; col++)
                {
                    if(!m_columns[col].css_width.is_predefined() &&
                       m_columns[col].css_width.units() == css_units_percentage)
                    {
                        distribute_columns.push_back(&m_columns[col]);
                    }
                }
            }
            break;
        case 2:
            {
                // well distribute between all columns
                for(int col = start; col <= end; col++)
                {
                    distribute_columns.push_back(&m_columns[col]);
                }
            }
            break;
        }

        pixel_t added_width;

        if(!distribute_columns.empty() || step == 2)
        {
            pixel_t cols_width;
            for(const auto& column : distribute_columns)
            {
                cols_width += column->max_width - column->min_width;
            }

            if(cols_width != 0_px)
            {
                pixel_t add = width / pixel_t(static_cast<int>(distribute_columns.size()));
                for(const auto& column : distribute_columns)
                {
                    add = width * ((column->max_width - column->min_width) / cols_width);
                    if(column->width + add >= column->min_width)
                    {
                        column->width += add;
                        added_width   += add;
                    } else
                    {
                        added_width   += (column->width - column->min_width) * (add / std::abs(add.value()));
                        column->width  = column->min_width;
                    }
                }
                if(added_width < width && step)
                {
                    distribute_columns.front()->width += width - added_width;
                    added_width                        = width;
                }
            } else
            {
                const pixel_t share       = width / pixel_t(static_cast<int>(distribute_columns.size()));
                pixel_t       distributed = 0_px;
                for(const auto& column : distribute_columns)
                {
                    column->width += share;
                    distributed   += share;
                }
                distribute_columns.back()->width += width - distributed;
                added_width                       = width;
            }
        }

        if(added_width == width)
        {
            break;
        }
        width -= added_width;
    }
}

litehtml::pixel_t litehtml::table_grid::calc_table_width(pixel_t block_width, bool is_auto, pixel_t& min_table_width,
                                                         pixel_t& max_table_width)
{
    // pixel_t table_width = 0;

    min_table_width = 0; // MIN
    max_table_width = 0; // MAX

    pixel_t cur_width = 0_px;
    pixel_t max_w     = 0_px;
    pixel_t min_w     = 0_px;

    for(int col = 0; col < m_cols_count; col++)
    {
        min_table_width += m_columns[col].min_width;
        max_table_width += m_columns[col].max_width;

        if(!m_columns[col].css_width.is_predefined())
        {
            m_columns[col].width = m_columns[col].css_width.calc_percent(block_width);
            m_columns[col].width = std::max(m_columns[col].width, m_columns[col].min_width);
        } else
        {
            m_columns[col].width  = m_columns[col].min_width;
            max_w                += m_columns[col].max_width;
            min_w                += m_columns[col].min_width;
        }

        cur_width += m_columns[col].width;
    }

    if(cur_width == block_width)
    {
        return cur_width;
    }

    if(cur_width < block_width)
    {
        if(cur_width - min_w + max_w <= block_width)
        {
            cur_width = 0;
            for(int col = 0; col < m_cols_count; col++)
            {
                if(m_columns[col].css_width.is_predefined())
                {
                    m_columns[col].width = m_columns[col].max_width;
                }
                cur_width += m_columns[col].width;
            }
            if(cur_width == block_width || is_auto)
            {
                return cur_width;
            }
        }
        distribute_width(block_width - cur_width, 0, m_cols_count - 1);
        cur_width = 0;
        for(int col = 0; col < m_cols_count; col++)
        {
            cur_width += m_columns[col].width;
        }
    } else
    {
        pixel_t fixed_width = 0_px;
        float   percent     = 0;
        for(int col = 0; col < m_cols_count; col++)
        {
            if(!m_columns[col].css_width.is_predefined() && m_columns[col].css_width.units() == css_units_percentage)
            {
                percent += m_columns[col].css_width.val();
            } else
            {
                fixed_width += m_columns[col].width;
            }
        }
        auto scale = static_cast<float>(100.0 / percent);
        if(pixel_t(scale) != 1_px)
        {
            cur_width = 0;
            for(int col = 0; col < m_cols_count; col++)
            {
                if(!m_columns[col].css_width.is_predefined() &&
                   m_columns[col].css_width.units() == css_units_percentage)
                {
                    css_length w;
                    w.set_value(m_columns[col].css_width.val() * scale, css_units_percentage);
                    m_columns[col].width = w.calc_percent(block_width - fixed_width);
                    if(m_columns[col].width < m_columns[col].min_width)
                    {
                        m_columns[col].width = m_columns[col].min_width;
                    }
                }
                cur_width += m_columns[col].width;
            }
        }
        // If the table is still too wide shrink columns with % widths
        if(cur_width > block_width)
        {
            while(true)
            {
                bool shrunk = false;
                for(int col = 0; col < m_cols_count; col++)
                {
                    if(!m_columns[col].css_width.is_predefined() &&
                       m_columns[col].css_width.units() == css_units_percentage)
                    {
                        if(m_columns[col].width > m_columns[col].min_width)
                        {
                            m_columns[col].width--;
                            cur_width--;
                            shrunk = true;
                            if(cur_width <= block_width)
                            {
                                break;
                            }
                        }
                    }
                }
                if(cur_width <= block_width || !shrunk)
                {
                    break;
                }
            }
        }
    }
    return cur_width;
}

void litehtml::table_grid::clear()
{
    m_rows_count = 0;
    m_cols_count = 0;
    m_cells.clear();
    m_columns.clear();
    m_rows.clear();
}

void litehtml::table_grid::calc_horizontal_positions(const margins& table_borders, border_collapse bc,
                                                     pixel_t bdr_space_x)
{
    if(bc == border_collapse_separate)
    {
        pixel_t left = bdr_space_x;
        for(int i = 0; i < m_cols_count; i++)
        {
            m_columns[i].left  = left;
            m_columns[i].right = m_columns[i].left + m_columns[i].width;
            left               = m_columns[i].right + bdr_space_x;
        }
    } else
    {
        pixel_t left = 0;
        if(m_cols_count)
        {
            left -= std::min(table_borders.left, m_columns[0].border_left);
        }
        for(int i = 0; i < m_cols_count; i++)
        {
            if(i > 0)
            {
                left -= std::min(m_columns[i - 1].border_right, m_columns[i].border_left);
            }

            m_columns[i].left  = left;
            m_columns[i].right = m_columns[i].left + m_columns[i].width;
            left               = m_columns[i].right;
        }
    }
}

void litehtml::table_grid::calc_vertical_positions(const margins& table_borders, border_collapse bc,
                                                   pixel_t bdr_space_y)
{
    if(bc == border_collapse_separate)
    {
        pixel_t top = bdr_space_y;
        for(int i = 0; i < m_rows_count; i++)
        {
            m_rows[i].top    = top;
            m_rows[i].bottom = m_rows[i].top + m_rows[i].height;
            top              = m_rows[i].bottom + bdr_space_y;
        }
    } else
    {
        pixel_t top = 0;
        if(m_rows_count)
        {
            top -= std::min(table_borders.top, m_rows[0].border_top);
        }
        for(int i = 0; i < m_rows_count; i++)
        {
            if(i > 0)
            {
                top -= std::min(m_rows[i - 1].border_bottom, m_rows[i].border_top);
            }

            m_rows[i].top    = top;
            m_rows[i].bottom = m_rows[i].top + m_rows[i].height;
            top              = m_rows[i].bottom;
        }
    }
}

void litehtml::table_grid::calc_rows_height(pixel_t blockHeight, pixel_t /*borderSpacingY*/)
{
    pixel_t min_table_height = 0;

    // compute vertical size inferred by cells
    for(auto& row : m_rows)
    {
        if(!row.css_height.is_predefined())
        {
            if(row.css_height.units() != css_units_percentage)
            {
                if(row.height < pixel_t(row.css_height.val()))
                {
                    row.height = pixel_t(row.css_height.val());
                }
            }
        }
        row.min_height    = row.height;
        min_table_height += row.height;
    }

    // min_table_height += borderSpacingY * ((int) m_rows.size() + 1);

    if(blockHeight > min_table_height)
    {
        pixel_t extra_height = blockHeight - min_table_height;
        int     auto_count   = 0; // number of rows with height=auto
        for(auto& row : m_rows)
        {
            if(!row.css_height.is_predefined() && row.css_height.units() == css_units_percentage)
            {
                row.height = row.css_height.calc_percent(blockHeight);
                if(row.height < row.min_height)
                {
                    row.height = row.min_height;
                }

                extra_height -= row.height - row.min_height;

                if(extra_height <= 0_px)
                {
                    break;
                }
            } else if(row.css_height.is_predefined())
            {
                auto_count++;
            }
        }
        if(extra_height > 0_px)
        {
            if(auto_count)
            {
                // distribute height to the rows with height=auto
                pixel_t extra_row_height = extra_height / pixel_t(auto_count);
                for(auto& row : m_rows)
                {
                    if(row.css_height.is_predefined())
                    {
                        row.height += extra_row_height;
                    }
                }
            } else
            {
                // We don't have rows with height=auto, so distribute height to all rows
                if(!m_rows.empty())
                {
                    pixel_t extra_row_height = extra_height / pixel_t(static_cast<int>(m_rows.size()));
                    for(auto& row : m_rows)
                    {
                        row.height += extra_row_height;
                    }
                }
            }
        } else if(extra_height < 0_px)
        {
            extra_height = -extra_height;
            for(auto row = m_rows.rbegin(); row < m_rows.rend() && extra_height > 0_px; row++)
            {
                if(row->height > row->min_height)
                {
                    if(row->height - extra_height >= row->min_height)
                    {
                        row->height  -= extra_height;
                        extra_height  = 0;
                    } else
                    {
                        extra_height -= row->height - row->min_height;
                        row->height   = row->min_height;
                    }
                }
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////

litehtml::pixel_t& litehtml::table_column_accessor_max_width::get(table_column& col)
{
    return col.max_width;
}

litehtml::pixel_t& litehtml::table_column_accessor_min_width::get(table_column& col)
{
    return col.min_width;
}

litehtml::pixel_t& litehtml::table_column_accessor_width::get(table_column& col)
{
    return col.width;
}

litehtml::table_row::table_row(pixel_t h, const std::shared_ptr<render_item>& row)
{
    min_height    = 0;
    height        = h;
    el_row        = row;
    border_bottom = 0;
    border_top    = 0;
    top           = 0;
    bottom        = 0;
    if(row)
    {
        css_height = row->src_el()->css().get_height();
    }
}
