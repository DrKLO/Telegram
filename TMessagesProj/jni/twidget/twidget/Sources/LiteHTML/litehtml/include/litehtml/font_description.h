#ifndef LITEHTML_FONT_DESCRIPTION_H
#define LITEHTML_FONT_DESCRIPTION_H

#include "css_length.h"
#include "web_color.h"
#include <string>
#include <vector>

namespace litehtml
{
    struct font_description
    {
        std::string family;                    // Font Family
        pixel_t     size;                      // Font size
        font_style  style = font_style_normal; // Font stype, see the enum font_style
        int         weight;                    // Font weight.
        int         decoration_line =
            text_decoration_line_none;   // Decoration line. A bitset of flags of the enum text_decoration_line
        css_length decoration_thickness; // Decoration line thickness in pixels. See predefined values in
                                         // enumtext_decoration_thickness
        text_decoration_style decoration_style =
            text_decoration_style_solid; // Decoration line style. See enum text_decoration_style
        web_color   decoration_color = web_color::current_color;     // Decoration line color
        std::string emphasis_style;                                  // Text emphasis style
        web_color   emphasis_color    = web_color::current_color;    // Text emphasis color
        int         emphasis_position = text_emphasis_position_over; // Text emphasis position
        float       letter_spacing    = 0;
        float       word_spacing      = 0;
        std::string features;
        int         smoothing         = 0;
        float       shadow_x          = 0;
        float       shadow_y          = 0;
        float       shadow_blur       = 0;
        web_color   shadow_color      = web_color::transparent;
        struct text_shadow
        {
            float     x = 0, y = 0, blur = 0;
            web_color color;
        };
        std::vector<text_shadow> extra_shadows;

        std::string hash() const
        {
            std::string out;
            out += family;
            out += ":sz=" + std::to_string(size.value());
            out += ":st=" + std::to_string(static_cast<int>(style));
            out += ":w=" + std::to_string(weight);
            out += ":dl=" + std::to_string(decoration_line);
            out += ":dt=" + decoration_thickness.to_string();
            out += ":ds=" + std::to_string(static_cast<int>(decoration_style));
            out += ":dc=" + decoration_color.to_string();
            out += ":ephs=" + emphasis_style;
            out += ":ephc=" + emphasis_color.to_string();
            out += ":ephp=" + std::to_string(emphasis_position);
            if(letter_spacing != 0)
            {
                out += ":ls=" + std::to_string(letter_spacing);
            }
            if(word_spacing != 0)
            {
                out += ":wsp=" + std::to_string(word_spacing);
            }
            if(!features.empty())
            {
                out += ":ff=" + features;
            }
            if(smoothing != 0)
            {
                out += ":fs=" + std::to_string(smoothing);
            }
            if(shadow_color.alpha != 0)
            {
                out += ":ts=" + std::to_string(shadow_x) + "," + std::to_string(shadow_y) + "," +
                       std::to_string(shadow_blur) + "," + shadow_color.to_string();
            }
            for(const auto& extra : extra_shadows)
            {
                out += ";" + std::to_string(extra.x) + "," + std::to_string(extra.y) + "," + std::to_string(extra.blur) + "," + extra.color.to_string();
            }

            return out;
        }
    };
} // namespace litehtml

#endif // LITEHTML_FONT_DESCRIPTION_H
