#ifndef LITEHTML_CSS_POSITION_H
#define LITEHTML_CSS_POSITION_H

#include "css_length.h"

namespace litehtml
{
    struct css_position
    {
        css_length x;
        css_length y;
        css_length width;
        css_length height;
    };

    struct css_size
    {
        css_length width;
        css_length height;

        css_size() = default;
        css_size(css_length width, css_length height) :
            width(width),
            height(height)
        {
        }
    };

    using size_vector = std::vector<css_size>;
} // namespace litehtml

#endif // LITEHTML_CSS_POSITION_H
