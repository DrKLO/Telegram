#include "el_style.h"
#include "document.h"

litehtml::el_style::el_style(const std::shared_ptr<document>& doc) :
    element(doc)
{
}

void litehtml::el_style::parse_attributes()
{
    std::string text;

    for(auto& el : m_children)
    {
        el->get_text(text);
    }
    get_document()->add_stylesheet(text.c_str(), nullptr, get_attr("media"));
}

bool litehtml::el_style::appendChild(const ptr& el)
{
    if(el && el->is_text())
    {
        m_children.push_back(el);
        return true;
    }
    return false;
}

void litehtml::el_style::compute_styles(bool /* recursive */)
{
    css_w().set_display(display_none);
}

litehtml::string_id litehtml::el_style::tag() const
{
    return _style_;
}

const char* litehtml::el_style::get_tagName() const
{
    return "style";
}
