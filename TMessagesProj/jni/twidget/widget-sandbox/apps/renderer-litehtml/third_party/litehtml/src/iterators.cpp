#include "iterators.h"
#include "render_item.h"

litehtml::elements_iterator::elements_iterator(bool return_parents, iterator_selector* go_inside,
                                               iterator_selector* select) :
    m_go_inside(go_inside),
    m_select(select),
    m_return_parent(return_parents)
{
}

bool litehtml::elements_iterator::go_inside(const std::shared_ptr<render_item>& el)
{
    return /*!el->children().empty() &&*/ m_go_inside && m_go_inside->select(el);
}

void litehtml::elements_iterator::process(
    const std::shared_ptr<render_item>&                                           container,
    const std::function<void(std::shared_ptr<render_item>&, iterator_item_type)>& func)
{
    for(auto& el : container->children())
    {
        if(go_inside(el))
        {
            if(m_return_parent)
            {
                // call function for parent
                func(el, iterator_item_type_start_parent);
            }
            // go inside element and process its items
            process(el, func);
            if(m_return_parent)
            {
                // call function for parent
                func(el, iterator_item_type_end_parent);
            }
        } else
        {
            // call function for element
            if(!m_select || m_select->select(el))
            {
                func(el, iterator_item_type_child);
            }
        }
    }
}

//////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////

bool litehtml::go_inside_inline::select(const std::shared_ptr<render_item>& el)
{
    return el->src_el()->css().get_display() == display_inline && el->src_el()->css().get_float() == float_none;
}

bool litehtml::inline_selector::select(const std::shared_ptr<render_item>& el)
{
    return el->src_el()->css().get_display() == display_inline_text ||
           el->src_el()->css().get_display() == display_inline_table ||
           el->src_el()->css().get_display() == display_inline_block ||
           el->src_el()->css().get_display() == display_inline_flex || el->src_el()->css().get_float() != float_none;
}

bool litehtml::go_inside_table::select(const std::shared_ptr<render_item>& el)
{
    return el->src_el()->css().get_display() == display_table_row_group ||
           el->src_el()->css().get_display() == display_table_header_group ||
           el->src_el()->css().get_display() == display_table_footer_group;
}

bool litehtml::table_rows_selector::select(const std::shared_ptr<render_item>& el)
{
    return el->src_el()->css().get_display() == display_table_row;
}

bool litehtml::table_cells_selector::select(const std::shared_ptr<render_item>& el)
{
    return el->src_el()->css().get_display() == display_table_cell;
}
