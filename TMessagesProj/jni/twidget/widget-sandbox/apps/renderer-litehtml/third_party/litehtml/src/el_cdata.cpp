#include "el_cdata.h"

litehtml::el_cdata::el_cdata(const std::shared_ptr<document>& doc) :
    element(doc)
{
    // m_skip = true;
}

void litehtml::el_cdata::get_text(std::string& text) const
{
    text += m_text;
}

void litehtml::el_cdata::set_data(const char* data)
{
    if(data)
    {
        m_text += data;
    }
}
