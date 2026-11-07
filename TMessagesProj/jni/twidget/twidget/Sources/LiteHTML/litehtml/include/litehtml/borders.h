#ifndef LITEHTML_BORDERS_H
#define LITEHTML_BORDERS_H

#include <algorithm>
#include <string>

#include "css_length.h"
#include "types.h"
#include "web_color.h"

namespace litehtml
{
    struct css_border
    {
        css_length   width;
        border_style style = border_style_none;
        web_color    color;

        css_border()                                 = default;
        css_border(const css_border& val)            = default;
        css_border& operator=(const css_border& val) = default;

        std::string to_string() const;
    };

    struct border
    {
        pixel_t      width = 0_px;
        border_style style = border_style_none;
        web_color    color;

        border()                  = default;
        border(const border& val) = default;
        border(const css_border& val) :
            width(static_cast<pixel_t>(val.width.val())),
            style(val.style),
            color(val.color)
        {
        }
        border& operator=(const border& val) = default;
        border& operator=(const css_border& val)
        {
            width = static_cast<pixel_t>(val.width.val());
            style = val.style;
            color = val.color;
            return *this;
        }
    };

    struct border_radiuses
    {
        pixel_t top_left_x;
        pixel_t top_left_y;

        pixel_t top_right_x;
        pixel_t top_right_y;

        pixel_t bottom_right_x;
        pixel_t bottom_right_y;

        pixel_t bottom_left_x;
        pixel_t bottom_left_y;

        void operator+=(const margins& mg)
        {
            top_left_x     += mg.left;
            top_left_y     += mg.top;
            top_right_x    += mg.right;
            top_right_y    += mg.top;
            bottom_right_x += mg.right;
            bottom_right_y += mg.bottom;
            bottom_left_x  += mg.left;
            bottom_left_y  += mg.bottom;
            fix_values();
        }

        void operator-=(const margins& mg)
        {
            top_left_x     -= mg.left;
            top_left_y     -= mg.top;
            top_right_x    -= mg.right;
            top_right_y    -= mg.top;
            bottom_right_x -= mg.right;
            bottom_right_y -= mg.bottom;
            bottom_left_x  -= mg.left;
            bottom_left_y  -= mg.bottom;
            fix_values();
        }
        void fix_values()
        {
            top_left_x     = std::max<pixel_t>(top_left_x, 0_px);
            top_left_y     = std::max<pixel_t>(top_left_y, 0_px);
            top_right_x    = std::max<pixel_t>(top_right_x, 0_px);
            top_right_y    = std::max<pixel_t>(top_right_y, 0_px);
            bottom_right_x = std::max<pixel_t>(bottom_right_x, 0_px);
            bottom_right_y = std::max<pixel_t>(bottom_right_y, 0_px);
            bottom_left_x  = std::max<pixel_t>(bottom_left_x, 0_px);
            bottom_left_y  = std::max<pixel_t>(bottom_left_y, 0_px);
        }
        void fix_values(pixel_t width, pixel_t height)
        {
            fix_values();
            pixel_t half_width  = width / 2.0_px;
            pixel_t half_height = height / 2.0_px;
            auto    fix_one     = [&](pixel_t& radii_x, pixel_t& radii_y) {
                pixel_t factor  = std::min(half_width / radii_x, half_height / radii_y);
                radii_x        *= factor;
                radii_y        *= factor;
            };

            if(top_left_x > half_width || top_left_y > half_height)
            {
                fix_one(top_left_x, top_left_y);
            }
            if(top_right_x > half_width || top_right_y > half_height)
            {
                fix_one(top_right_x, top_right_y);
            }
            if(bottom_right_x > half_width || bottom_right_y > half_height)
            {
                fix_one(bottom_right_x, bottom_right_y);
            }
            if(bottom_left_x > half_width || bottom_left_y > half_height)
            {
                fix_one(bottom_left_x, bottom_left_y);
            }
        }
    };

    struct css_border_radius
    {
        css_length top_left_x;
        css_length top_left_y;

        css_length top_right_x;
        css_length top_right_y;

        css_length bottom_right_x;
        css_length bottom_right_y;

        css_length bottom_left_x;
        css_length bottom_left_y;

        border_radiuses calc_percents(pixel_t width, pixel_t height) const
        {
            border_radiuses ret;
            ret.bottom_left_x  = bottom_left_x.calc_percent(width);
            ret.bottom_left_y  = bottom_left_y.calc_percent(height);
            ret.top_left_x     = top_left_x.calc_percent(width);
            ret.top_left_y     = top_left_y.calc_percent(height);
            ret.top_right_x    = top_right_x.calc_percent(width);
            ret.top_right_y    = top_right_y.calc_percent(height);
            ret.bottom_right_x = bottom_right_x.calc_percent(width);
            ret.bottom_right_y = bottom_right_y.calc_percent(height);
            ret.fix_values(width, height);
            return ret;
        }
    };

    struct css_borders
    {
        css_border        left;
        css_border        top;
        css_border        right;
        css_border        bottom;
        css_border_radius radius;

        css_borders() = default;

        bool is_visible() const
        {
            return pixel_t(left.width.val()) != 0_px || pixel_t(right.width.val()) != 0_px ||
                   pixel_t(top.width.val()) != 0_px || pixel_t(bottom.width.val()) != 0_px;
        }

        std::string to_string() const
        {
            return "left: " + left.to_string() + ", top: " + top.to_string() + ", right: " + top.to_string() +
                   ", bottom: " + bottom.to_string();
        }
    };

    struct borders
    {
        border          left;
        border          top;
        border          right;
        border          bottom;
        border_radiuses radius;

        borders()                   = default;
        borders(const borders& val) = default;

        borders(const css_borders& val) :
            left(val.left),
            top(val.top),
            right(val.right),
            bottom(val.bottom)
        {
        }

        bool is_visible() const
        {
            return left.width != 0_px || right.width != 0_px || top.width != 0_px || bottom.width != 0_px;
        }

        borders& operator=(const borders& val) = default;

        borders& operator=(const css_borders& val)
        {
            left   = val.left;
            right  = val.right;
            top    = val.top;
            bottom = val.bottom;
            return *this;
        }
    };
} // namespace litehtml

#endif // LITEHTML_BORDERS_H
