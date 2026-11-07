#include "html.h"
#include "web_color.h"

#include <cmath>
#include "css_parser.h"
#include "os_types.h"
#include "document_container.h"

namespace litehtml
{

    const web_color web_color::transparent   = web_color(0, 0, 0, 0);
    const web_color web_color::black         = web_color(0, 0, 0, 255);
    const web_color web_color::white         = web_color(255, 255, 255, 255);
    const web_color web_color::current_color = web_color(true);

    gradient gradient::transparent;

    struct def_color
    {
        const char* name;
        const char* rgb;
    };

    def_color g_def_colors[] = {
        {"transparent",          "rgba(0, 0, 0, 0)"},
        {"AliceBlue",            "#F0F8FF"         },
        {"AntiqueWhite",         "#FAEBD7"         },
        {"Aqua",                 "#00FFFF"         },
        {"Aquamarine",           "#7FFFD4"         },
        {"Azure",                "#F0FFFF"         },
        {"Beige",                "#F5F5DC"         },
        {"Bisque",               "#FFE4C4"         },
        {"Black",                "#000000"         },
        {"BlanchedAlmond",       "#FFEBCD"         },
        {"Blue",                 "#0000FF"         },
        {"BlueViolet",           "#8A2BE2"         },
        {"Brown",                "#A52A2A"         },
        {"BurlyWood",            "#DEB887"         },
        {"CadetBlue",            "#5F9EA0"         },
        {"Chartreuse",           "#7FFF00"         },
        {"Chocolate",            "#D2691E"         },
        {"Coral",                "#FF7F50"         },
        {"CornflowerBlue",       "#6495ED"         },
        {"Cornsilk",             "#FFF8DC"         },
        {"Crimson",              "#DC143C"         },
        {"Cyan",                 "#00FFFF"         },
        {"DarkBlue",             "#00008B"         },
        {"DarkCyan",             "#008B8B"         },
        {"DarkGoldenRod",        "#B8860B"         },
        {"DarkGray",             "#A9A9A9"         },
        {"DarkGrey",             "#A9A9A9"         },
        {"DarkGreen",            "#006400"         },
        {"DarkKhaki",            "#BDB76B"         },
        {"DarkMagenta",          "#8B008B"         },
        {"DarkOliveGreen",       "#556B2F"         },
        {"Darkorange",           "#FF8C00"         },
        {"DarkOrchid",           "#9932CC"         },
        {"DarkRed",              "#8B0000"         },
        {"DarkSalmon",           "#E9967A"         },
        {"DarkSeaGreen",         "#8FBC8F"         },
        {"DarkSlateBlue",        "#483D8B"         },
        {"DarkSlateGray",        "#2F4F4F"         },
        {"DarkSlateGrey",        "#2F4F4F"         },
        {"DarkTurquoise",        "#00CED1"         },
        {"DarkViolet",           "#9400D3"         },
        {"DeepPink",             "#FF1493"         },
        {"DeepSkyBlue",          "#00BFFF"         },
        {"DimGray",              "#696969"         },
        {"DimGrey",              "#696969"         },
        {"DodgerBlue",           "#1E90FF"         },
        {"FireBrick",            "#B22222"         },
        {"FloralWhite",          "#FFFAF0"         },
        {"ForestGreen",          "#228B22"         },
        {"Fuchsia",              "#FF00FF"         },
        {"Gainsboro",            "#DCDCDC"         },
        {"GhostWhite",           "#F8F8FF"         },
        {"Gold",                 "#FFD700"         },
        {"GoldenRod",            "#DAA520"         },
        {"Gray",                 "#808080"         },
        {"Grey",                 "#808080"         },
        {"Green",                "#008000"         },
        {"GreenYellow",          "#ADFF2F"         },
        {"HoneyDew",             "#F0FFF0"         },
        {"HotPink",              "#FF69B4"         },
        {"Ivory",                "#FFFFF0"         },
        {"Khaki",                "#F0E68C"         },
        {"Lavender",             "#E6E6FA"         },
        {"LavenderBlush",        "#FFF0F5"         },
        {"LawnGreen",            "#7CFC00"         },
        {"LemonChiffon",         "#FFFACD"         },
        {"LightBlue",            "#ADD8E6"         },
        {"LightCoral",           "#F08080"         },
        {"LightCyan",            "#E0FFFF"         },
        {"LightGoldenRodYellow", "#FAFAD2"         },
        {"LightGray",            "#D3D3D3"         },
        {"LightGrey",            "#D3D3D3"         },
        {"LightGreen",           "#90EE90"         },
        {"LightPink",            "#FFB6C1"         },
        {"LightSalmon",          "#FFA07A"         },
        {"LightSeaGreen",        "#20B2AA"         },
        {"LightSkyBlue",         "#87CEFA"         },
        {"LightSlateGray",       "#778899"         },
        {"LightSlateGrey",       "#778899"         },
        {"LightSteelBlue",       "#B0C4DE"         },
        {"LightYellow",          "#FFFFE0"         },
        {"Lime",                 "#00FF00"         },
        {"LimeGreen",            "#32CD32"         },
        {"Linen",                "#FAF0E6"         },
        {"Magenta",              "#FF00FF"         },
        {"Maroon",               "#800000"         },
        {"MediumAquaMarine",     "#66CDAA"         },
        {"MediumBlue",           "#0000CD"         },
        {"MediumOrchid",         "#BA55D3"         },
        {"MediumPurple",         "#9370D8"         },
        {"MediumSeaGreen",       "#3CB371"         },
        {"MediumSlateBlue",      "#7B68EE"         },
        {"MediumSpringGreen",    "#00FA9A"         },
        {"MediumTurquoise",      "#48D1CC"         },
        {"MediumVioletRed",      "#C71585"         },
        {"MidnightBlue",         "#191970"         },
        {"MintCream",            "#F5FFFA"         },
        {"MistyRose",            "#FFE4E1"         },
        {"Moccasin",             "#FFE4B5"         },
        {"NavajoWhite",          "#FFDEAD"         },
        {"Navy",                 "#000080"         },
        {"OldLace",              "#FDF5E6"         },
        {"Olive",                "#808000"         },
        {"OliveDrab",            "#6B8E23"         },
        {"Orange",               "#FFA500"         },
        {"OrangeRed",            "#FF4500"         },
        {"Orchid",               "#DA70D6"         },
        {"PaleGoldenRod",        "#EEE8AA"         },
        {"PaleGreen",            "#98FB98"         },
        {"PaleTurquoise",        "#AFEEEE"         },
        {"PaleVioletRed",        "#D87093"         },
        {"PapayaWhip",           "#FFEFD5"         },
        {"PeachPuff",            "#FFDAB9"         },
        {"Peru",                 "#CD853F"         },
        {"Pink",                 "#FFC0CB"         },
        {"Plum",                 "#DDA0DD"         },
        {"PowderBlue",           "#B0E0E6"         },
        {"Purple",               "#800080"         },
        {"Red",                  "#FF0000"         },
        {"RosyBrown",            "#BC8F8F"         },
        {"RoyalBlue",            "#4169E1"         },
        {"SaddleBrown",          "#8B4513"         },
        {"Salmon",               "#FA8072"         },
        {"SandyBrown",           "#F4A460"         },
        {"SeaGreen",             "#2E8B57"         },
        {"SeaShell",             "#FFF5EE"         },
        {"Sienna",               "#A0522D"         },
        {"Silver",               "#C0C0C0"         },
        {"SkyBlue",              "#87CEEB"         },
        {"SlateBlue",            "#6A5ACD"         },
        {"SlateGray",            "#708090"         },
        {"SlateGrey",            "#708090"         },
        {"Snow",                 "#FFFAFA"         },
        {"SpringGreen",          "#00FF7F"         },
        {"SteelBlue",            "#4682B4"         },
        {"Tan",                  "#D2B48C"         },
        {"Teal",                 "#008080"         },
        {"Thistle",              "#D8BFD8"         },
        {"Tomato",               "#FF6347"         },
        {"Turquoise",            "#40E0D0"         },
        {"Violet",               "#EE82EE"         },
        {"Wheat",                "#F5DEB3"         },
        {"White",                "#FFFFFF"         },
        {"WhiteSmoke",           "#F5F5F5"         },
        {"Yellow",               "#FFFF00"         },
        {"YellowGreen",          "#9ACD32"         },
    };

    // <hex-color>  https://drafts.csswg.org/css-color-4/#typedef-hex-color
    bool parse_hash_color(const css_token& tok, web_color& color)
    {
        if(tok.type != HASH)
        {
            return false;
        }

        std::string s   = tok.str();
        int         len = static_cast<int>(s.size());
        if(!is_one_of(len, 3, 4, 6, 8))
        {
            return false;
        }
        for(auto ch : s)
        {
            if(!is_hex_digit(ch))
            {
                return false;
            }
        }

        std::string r, g, b, a = "ff";
        if(len == 3 || len == 4)
        {
            r = {s[0], s[0]};
            g = {s[1], s[1]};
            b = {s[2], s[2]};
            if(len == 4)
            {
                a = {s[3], s[3]};
            }
        } else // 6 or 8
        {
            r = {s[0], s[1]};
            g = {s[2], s[3]};
            b = {s[4], s[5]};
            if(len == 8)
            {
                a = {s[6], s[7]};
            }
        }
        auto read_two_hex_digits = [](auto str) { return byte(16 * digit_value(str[0]) + digit_value(str[1])); };

        color =
            web_color(read_two_hex_digits(r), read_two_hex_digits(g), read_two_hex_digits(b), read_two_hex_digits(a));
        return true;
    }

    float clamp(float x, float min, float max)
    {
        if(x < min)
        {
            return min;
        }
        if(x > max)
        {
            return max;
        }
        return x;
    }

    // [ <number> | <percentage> | none ]{3}  [ / [<alpha-value> | none] ]?
    bool parse_modern_syntax(const css_token_vector& tokens, bool is_hsl, css_length& x, css_length& y, css_length& z,
                             css_length& a)
    {
        auto           n        = tokens.size();
        constexpr auto none_val = split_css_values<1>("none");
        if(n != 3 && n != 5)
        {
            return false;
        }
        if(is_hsl)
        {
            // [<hue> | none] [<number> | <percentage> | none]{2} [ / [<alpha-value> | none] ]?
            // <hue> = <number> | <angle>
            if(!x.from_token(tokens[0], f_number, none_val))
            {
                float hue;
                if(!parse_angle(tokens[0], hue))
                {
                    return false;
                }
                x.set_value(hue, css_units_none);
            }
        } else if(!x.from_token(tokens[0], f_number | f_percentage, none_val))
        {
            return false;
        }
        if(!y.from_token(tokens[1], f_number | f_percentage, none_val))
        {
            return false;
        }
        if(!z.from_token(tokens[2], f_number | f_percentage, none_val))
        {
            return false;
        }
        if(n == 5)
        {
            if(tokens[3].ch != '/')
            {
                return false;
            }
            // <alpha-value> = <number> | <percentage>
            if(!a.from_token(tokens[4], f_number | f_percentage, none_val))
            {
                return false;
            }
        }
        // convert nones to zeros
        // https://drafts.csswg.org/css-color-4/#missing
        // For all other purposes, a missing component behaves as a zero value...
        for(auto t : {&x, &y, &z, &a})
        {
            if(t->is_predefined())
            {
                t->set_value(0, css_units_none);
            }
        }
        return true;
    }

    // https://drafts.csswg.org/css-color-4/#rgb-functions
    // Values outside these ranges are not invalid, but are clamped to the ranges defined here at parsed-value time.
    byte calc_percent_and_clamp(const css_length& val, float max = 255)
    {
        float x = val.val();
        if(val.units() == css_units_percentage)
        {
            x = (x / 100) * max;
        }
        x = clamp(x, 0, max);
        return static_cast<byte>(std::round(pixel_t(max) == 1_px ? x * 255 : x));
    }

    // https://drafts.csswg.org/css-color-4/#rgb-functions
    bool parse_rgb_func(const css_token& tok, web_color& color)
    {
        if(tok.type != CV_FUNCTION || !is_one_of(lowcase(tok.name()), "rgb", "rgba"))
        {
            return false;
        }

        auto list = parse_comma_separated_list(tok.value);
        int  n    = static_cast<int>(list.size());
        if(!is_one_of(n, 1, 3, 4))
        {
            return false;
        }

        css_length r, g, b, a(1, css_units_none);
        // legacy syntax: <percentage>#{3} , <alpha-value>? | <number>#{3} , <alpha-value>?
        if(n != 1)
        {
            for(const auto& item : list)
            {
                if(item.size() != 1)
                {
                    return false;
                }
            }
            auto type = list[0][0].type;
            if(!is_one_of(type, PERCENTAGE, NUMBER))
            {
                return false;
            }
            int options = type == PERCENTAGE ? f_percentage : f_number;
            if(!r.from_token(list[0][0], options))
            {
                return false;
            }
            if(!g.from_token(list[1][0], options))
            {
                return false;
            }
            if(!b.from_token(list[2][0], options))
            {
                return false;
            }
            // <alpha-value> = <number> | <percentage>
            if(n == 4 && !a.from_token(list[3][0], f_number | f_percentage))
            {
                return false;
            }
        }
        // modern syntax:  [ <number> | <percentage> | none ]{3}  [ / [<alpha-value> | none] ]?
        else if(!parse_modern_syntax(tok.value, false, r, g, b, a))
        {
            return false;
        }

        color = web_color(calc_percent_and_clamp(r), calc_percent_and_clamp(g), calc_percent_and_clamp(b),
                          calc_percent_and_clamp(a, 1));
        return true;
    }

    // https://drafts.csswg.org/css-color-4/#hsl-to-rgb
    void hsl_to_rgb(float hue, float sat, float light, float& r, float& g, float& b)
    {
        hue = static_cast<float>(std::fmod(hue, 360.f));

        if(hue < 0)
        {
            hue += 360;
        }

        sat   /= 100;
        light /= 100;

        auto f = [=](float n) {
            auto  k = static_cast<float>(std::fmod(n + hue / 30, 12.f));
            float a = sat * std::min(light, 1 - light);
            return light - a * std::max(-1.f, std::min({k - 3, 9 - k, 1.f}));
        };

        r = f(0);
        g = f(8);
        b = f(4);
    }

    // https://drafts.csswg.org/css-color-4/#the-hsl-notation
    bool parse_hsl_func(const css_token& tok, web_color& color)
    {
        if(tok.type != CV_FUNCTION || !is_one_of(lowcase(tok.name()), "hsl", "hsla"))
        {
            return false;
        }

        auto list = parse_comma_separated_list(tok.value);
        int  n    = static_cast<int>(list.size());
        if(!is_one_of(n, 1, 3, 4))
        {
            return false;
        }

        css_length h, s, l, a(1, css_units_none);
        // legacy syntax: <hue>, <percentage>, <percentage>, <alpha-value>?
        if(n != 1)
        {
            for(const auto& item : list)
            {
                if(item.size() != 1)
                {
                    return false;
                }
            }
            const auto& tok0 = list[0][0];
            float       hue;
            // <hue> = <number> | <angle>
            // number is interpreted as a number of degrees  https://drafts.csswg.org/css-color-4/#typedef-hue
            if(tok0.type == NUMBER)
            {
                hue = tok0.n.number;
            } else if(!parse_angle(tok0, hue))
            {
                return false;
            }
            h.set_value(hue, css_units_none);

            if(!s.from_token(list[1][0], f_percentage))
            {
                return false;
            }
            if(!l.from_token(list[2][0], f_percentage))
            {
                return false;
            }
            if(n == 4 && !a.from_token(list[3][0], f_number | f_percentage))
            {
                return false;
            }
        }
        // modern syntax:  [<hue> | none] [<percentage> | <number> | none]{2} [ / [<alpha-value> | none] ]?
        else if(!parse_modern_syntax(tok.value, true, h, s, l, a))
        {
            return false;
        }

        float hue = h.val();
        // no percent calculation needed for sat and lit because 0% ~ 0, 100% ~ 100
        float sat = s.val();
        float lit = l.val();
        // For historical reasons, if the saturation is less than 0% it is clamped to 0% at parsed-value time, before
        // being converted to an sRGB color.
        if(sat < 0)
        {
            sat = 0;
        }

        // Note: Chrome and Firefox treat invalid hsl values differently.
        //
        // Note: at this point, sat is not clamped at 100, and lit is not clamped at all. The standard
        // mentions only clamping sat at 0. As a result, returning rgb values may not be inside [0,1].
        float r, g, b;
        hsl_to_rgb(hue, sat, lit, r, g, b);

        r = clamp(r, 0, 1);
        g = clamp(g, 0, 1);
        b = clamp(b, 0, 1);

        color = web_color(static_cast<byte>(std::round(r * 255)), static_cast<byte>(std::round(g * 255)),
                          static_cast<byte>(std::round(b * 255)), calc_percent_and_clamp(a, 1));
        return true;
    }

    // https://drafts.csswg.org/css-color-5/#typedef-color-function
    bool parse_func_color(const css_token& tok, web_color& color)
    {
        return parse_rgb_func(tok, color) || parse_hsl_func(tok, color);
    }

    std::string resolve_name(const std::string& name, document_container* container)
    {
        for(auto clr : g_def_colors)
        {
            if(equal_i(name, clr.name))
            {
                return clr.rgb;
            }
        }

        if(container)
        {
            return container->resolve_color(name);
        }

        return "";
    }

    bool parse_name_color(const css_token& tok, web_color& color, document_container* container)
    {
        if(tok.type != IDENT)
        {
            return false;
        }
        if(tok.ident() == "currentcolor")
        {
            color = web_color::current_color;
            return true;
        }
        std::string str    = resolve_name(tok.name(), container);
        auto        tokens = normalize(str, f_componentize | f_remove_whitespace);
        if(tokens.size() != 1)
        {
            return false;
        }
        return parse_color(tokens[0], color, container);
    }

    // https://drafts.csswg.org/css-color-5/#typedef-color
    bool parse_color(const css_token& tok, web_color& color, document_container* container)
    {
        return parse_hash_color(tok, color) || parse_func_color(tok, color) || parse_name_color(tok, color, container);
    }

    web_color web_color::darken(double fraction) const
    {
        int v_red   = static_cast<int>(red);
        int v_blue  = static_cast<int>(blue);
        int v_green = static_cast<int>(green);
        v_red       = static_cast<int>(std::max(v_red - (v_red * fraction), 0.0));
        v_blue      = static_cast<int>(std::max(v_blue - (v_blue * fraction), 0.0));
        v_green     = static_cast<int>(std::max(v_green - (v_green * fraction), 0.0));
        return {static_cast<byte>(v_red), static_cast<byte>(v_green), static_cast<byte>(v_blue), alpha};
    }

    std::string web_color::to_string() const
    {
        char str[9];
        if(alpha)
        {
            t_snprintf(str, 9, "%02X%02X%02X%02X", red, green, blue, alpha);
        } else
        {
            t_snprintf(str, 9, "%02X%02X%02X", red, green, blue);
        }
        return str;
    }

} // namespace litehtml
