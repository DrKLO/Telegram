#include "el_break.h"

litehtml::el_break::el_break(const std::shared_ptr<litehtml::document>& doc) :
    html_tag(doc)
{
}

bool litehtml::el_break::is_break() const
{
    return true;
}
