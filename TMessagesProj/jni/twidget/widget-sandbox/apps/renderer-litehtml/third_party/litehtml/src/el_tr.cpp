#include "el_tr.h"
#include "document.h"

litehtml::el_tr::el_tr(const std::shared_ptr<document>& doc) :
    html_tag(doc)
{
}

void litehtml::el_tr::parse_attributes()
{
    // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-tr-height
    const char* str = get_attr("height");
    if(str)
    {
        map_to_dimension_property(_height_, str);
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
    str = get_attr("bgcolor");
    if(str)
    {
        m_style.add_property(_background_color_, str, "", false, get_document()->container());
    }
    html_tag::parse_attributes();
}
