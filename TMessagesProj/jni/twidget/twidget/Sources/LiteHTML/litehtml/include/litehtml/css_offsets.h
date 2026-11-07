#ifndef LITEHTML_CSS_OFFSETS_H
#define LITEHTML_CSS_OFFSETS_H

#include "css_length.h"

namespace litehtml
{
    struct css_offsets
    {
        css_length left;
        css_length top;
        css_length right;
        css_length bottom;

        std::string to_string() const
        {
            return "left: " + left.to_string() + ", top: " + top.to_string() + ", right: " + right.to_string() +
                   ", bottom: " + bottom.to_string();
        }
    };
} // namespace litehtml

#endif // LITEHTML_CSS_OFFSETS_H
