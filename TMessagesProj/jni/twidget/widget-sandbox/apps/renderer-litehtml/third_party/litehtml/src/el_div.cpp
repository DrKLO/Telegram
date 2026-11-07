#include "el_div.h"
#include "document.h"

litehtml::el_div::el_div(const document::ptr& doc) :
    html_tag(doc)
{
}

void litehtml::el_div::parse_attributes()
{
    const char* str = get_attr("align");
    if(str)
    {
        m_style.add_property(_text_align_, str);
    }
    html_tag::parse_attributes();
}
