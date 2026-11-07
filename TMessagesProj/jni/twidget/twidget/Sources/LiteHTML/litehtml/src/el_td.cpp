#include "el_td.h"
#include "document.h"

namespace litehtml
{

    el_td::el_td(const std::shared_ptr<document>& doc) :
        html_tag(doc)
    {
    }

    void el_td::parse_attributes()
    {
        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-tdth-width
        const char* str = get_attr("width");
        if(str)
        {
            map_to_dimension_property_ignoring_zero(_width_, str);
        }

        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-tdth-height
        str = get_attr("height");
        if(str)
        {
            map_to_dimension_property_ignoring_zero(_height_, str);
        }

        // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-background
        str = get_attr("background");
        if(str)
        {
            std::string url  = "url('";
            url             += str;
            url             += "')";
            m_style.add_property(_background_image_, url);
        }

        str = get_attr("bgcolor");
        if(str)
        {
            m_style.add_property(_background_color_, str, "", false, get_document()->container());
        }

        str = get_attr("align");
        if(str)
        {
            m_style.add_property(_text_align_, str);
        }

        str = get_attr("valign");
        if(str)
        {
            m_style.add_property(_vertical_align_, str);
        }

        html_tag::parse_attributes();
    }

} // namespace litehtml
