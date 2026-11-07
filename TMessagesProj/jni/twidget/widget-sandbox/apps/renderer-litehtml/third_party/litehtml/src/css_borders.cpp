#include "borders.h"

std::string litehtml::css_border::to_string() const
{
    std::string ret  = width.to_string() + "/";
    ret             += css_values(border_style_strings).value_by_index(style);
    ret             += "/" + color.to_string();
    return ret;
}
