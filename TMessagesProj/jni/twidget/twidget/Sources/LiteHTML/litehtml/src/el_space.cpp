#include "html.h"
#include "document.h"
#include "el_space.h"

litehtml::el_space::el_space(const char* text, const std::shared_ptr<document>& doc) :
    el_text(text, doc)
{
}

bool litehtml::el_space::is_white_space() const
{
    white_space ws = css().get_white_space();
    return ws == white_space_normal || ws == white_space_nowrap || ws == white_space_pre_line;
}

bool litehtml::el_space::is_break() const
{
    white_space ws = css().get_white_space();
    if(ws == white_space_pre || ws == white_space_pre_line || ws == white_space_pre_wrap)
    {
        if(m_text == "\n")
        {
            return true;
        }
    }
    return false;
}

bool litehtml::el_space::is_space() const
{
    return true;
}

std::string litehtml::el_space::dump_get_name()
{
    return "space: \"" + get_escaped_string(m_text) + "\"";
}
