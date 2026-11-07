#include "el_para.h"
#include "document.h"

litehtml::el_para::el_para(const std::shared_ptr<document>& doc) :
    html_tag(doc)
{
}

void litehtml::el_para::parse_attributes()
{
    const char* str = get_attr("align");
    if(str)
    {
        m_style.add_property(_text_align_, str);
    }

    html_tag::parse_attributes();
}
