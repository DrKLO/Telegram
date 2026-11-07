#include "el_script.h"
#include "document.h"

litehtml::el_script::el_script(const std::shared_ptr<document>& doc) :
    element(doc)
{
}

void litehtml::el_script::parse_attributes()
{
    // TODO: pass script text to document container
}

bool litehtml::el_script::appendChild(const ptr& el)
{
    el->get_text(m_text);
    return true;
}

litehtml::string_id litehtml::el_script::tag() const
{
    return _script_;
}

const char* litehtml::el_script::get_tagName() const
{
    return "script";
}
