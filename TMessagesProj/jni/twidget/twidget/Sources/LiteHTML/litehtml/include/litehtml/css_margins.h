#ifndef LITEHTML_CSS_MARGINS_H
#define LITEHTML_CSS_MARGINS_H

#include "css_length.h"

namespace litehtml
{
    struct css_margins
    {
        css_length left;
        css_length right;
        css_length top;
        css_length bottom;

        std::string to_string() const
        {
            return "left: " + left.to_string() + ", right: " + right.to_string() + ", top: " + top.to_string() +
                   ", bottom: " + bottom.to_string();
        }
    };
} // namespace litehtml

#endif // LITEHTML_CSS_MARGINS_H
