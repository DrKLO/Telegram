#include "el_font.h"
#include "document.h"

litehtml::el_font::el_font(const std::shared_ptr<document>& doc) :
    html_tag(doc)
{
}

void litehtml::el_font::parse_attributes()
{
    const char* str = get_attr("color");
    if(str)
    {
        m_style.add_property(_color_, str, "", false, get_document()->container());
    }

    str = get_attr("face");
    if(str)
    {
        m_style.add_property(_font_family_, str);
    }

    str = get_attr("size");
    if(str)
    {
        int sz = atoi(str);
        if(*str == '+' || *str == '-')
        {
            sz = 3 + sz; // relative size
        }

        if(sz <= 1)
        {
            m_style.add_property(_font_size_, "x-small");
        } else if(sz >= 6)
        {
            m_style.add_property(_font_size_, "xx-large");
        } else
        {
            switch(sz)
            {
            case 2:
                m_style.add_property(_font_size_, "small");
                break;
            case 3:
                m_style.add_property(_font_size_, "medium");
                break;
            case 4:
                m_style.add_property(_font_size_, "large");
                break;
            case 5:
                m_style.add_property(_font_size_, "x-large");
                break;
            }
        }
    }

    html_tag::parse_attributes();
}
