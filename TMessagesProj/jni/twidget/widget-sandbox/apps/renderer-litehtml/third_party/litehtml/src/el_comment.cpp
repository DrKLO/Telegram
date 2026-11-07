#include "el_comment.h"

litehtml::el_comment::el_comment(const std::shared_ptr<document>& doc) :
    element(doc)
{
    // m_skip = true;
}

bool litehtml::el_comment::is_comment() const
{
    return true;
}

void litehtml::el_comment::get_text(std::string& text) const
{
    text += m_text;
}

void litehtml::el_comment::set_data(const char* data)
{
    if(data)
    {
        m_text += data;
    }
}
