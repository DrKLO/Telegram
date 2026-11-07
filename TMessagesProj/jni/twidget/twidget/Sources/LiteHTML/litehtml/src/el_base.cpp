#include "el_base.h"
#include "document.h"
#include "document_container.h"

litehtml::el_base::el_base(const std::shared_ptr<document>& doc) :
    html_tag(doc)
{
}

void litehtml::el_base::parse_attributes()
{
    get_document()->container()->set_base_url(get_attr("href"));
}
