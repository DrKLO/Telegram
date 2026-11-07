#ifndef LITEHTML_WEB_COLOR_H
#define LITEHTML_WEB_COLOR_H

#include "css_tokenizer.h"
#include "types.h"

namespace litehtml
{
    class document_container;

    struct web_color
    {
        byte red              = 0;
        byte green            = 0;
        byte blue             = 0;
        byte alpha            = 255;
        bool is_current_color = false;

        static const web_color transparent;
        static const web_color black;
        static const web_color white;
        static const web_color current_color;

        web_color() = default;
        web_color(byte r, byte g, byte b, byte a = 255) :
            red(r),
            green(g),
            blue(b),
            alpha(a)
        {
        }
        web_color(bool is_current_color) :
            is_current_color(is_current_color)
        {
        }

        bool operator==(web_color color) const
        {
            return red == color.red && green == color.green && blue == color.blue && alpha == color.alpha;
        }
        bool operator!=(web_color color) const
        {
            return !(*this == color);
        }

        web_color   darken(double fraction) const;
        std::string to_string() const;
    };

    bool parse_color(const css_token& token, web_color& color, document_container* container);
} // namespace litehtml

#endif // LITEHTML_WEB_COLOR_H
