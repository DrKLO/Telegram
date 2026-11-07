#include "el_table.h"
#include "document.h"

namespace litehtml
{

    el_table::el_table(const std::shared_ptr<document>& doc) :
        html_tag(doc)
    {
    }

    bool el_table::appendChild(const element::ptr& el)
    {
        if(!el)
        {
            return false;
        }
        if(el->tag() == _tbody_ || el->tag() == _thead_ || el->tag() == _tfoot_ || el->tag() == _caption_)
        {
            return html_tag::appendChild(el);
        }
        return false;
    }

    void el_table::parse_attributes()
    {
        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-table-width
        const char* str = get_attr("width");
        if(str)
        {
            map_to_dimension_property_ignoring_zero(_width_, str);
        }

        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-table-height
        str = get_attr("height");
        if(str)
        {
            map_to_dimension_property(_height_, str);
        }

        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-table-cellspacing
        str = get_attr("cellspacing");
        if(str)
        {
            map_to_pixel_length_property(_border_spacing_, str);
        }

        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-table-border
        str = get_attr("border");
        if(str)
        {
            map_to_pixel_length_property_with_default_value(_border_width_, str, 1);
        }

        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-background
        str = get_attr("bgcolor");
        if(str)
        {
            m_style.add_property(_background_color_, str, "", false, get_document()->container());
        }

        html_tag::parse_attributes();
    }

} // namespace litehtml
