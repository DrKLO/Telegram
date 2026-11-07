#ifndef LITEHTML_GRADIENT_H
#define LITEHTML_GRADIENT_H

#include "css_length.h"
#include "string_id.h"
#include "web_color.h"

namespace litehtml
{
    enum gradient_side
    {
        gradient_side_none     = 0,
        gradient_side_left     = 0x01,
        gradient_side_right    = 0x02,
        gradient_side_top      = 0x04,
        gradient_side_bottom   = 0x08,
        gradient_side_x_center = 0x10,
        gradient_side_y_center = 0x20,
        gradient_side_x_length = 0x40,
        gradient_side_y_length = 0x80,
    };
    static_assert(gradient_side_left == (1u << background_position_left),
                  "parse_bg_position is also used to parse radial gradient position");

    enum radial_shape_t
    {
        radial_shape_none,
        radial_shape_circle,
        radial_shape_ellipse,
    };

    class gradient
    {
      public:
        // https://drafts.csswg.org/css-images-4/#typedef-color-stop-list
        // color_stop represents <linear-color-stop> | <linear-color-hint> | <angular-color-stop> | <angular-color-hint>
        // <linear-color-stop>  = <color> <length-percentage>{1,2}?
        // <linear-color-hint>  = <length-percentage>
        // <angular-color-stop> = <color> <angle-percentage>{1,2}?
        // <angular-color-hint> = <angle-percentage>
        // If two lengths/angles are specified they create two color-stops with the same color.
        class color_stop
        {
          public:
            bool                      is_color_hint = false;
            web_color                 color;
            std::optional<css_length> length;
            std::optional<float>      angle;

            color_stop() = default;
            color_stop(web_color color) :
                color(color)
            {
            }
            color_stop(web_color color, css_length length) :
                color(color),
                length(length)
            {
            }
            color_stop(web_color color, float angle) :
                color(color),
                angle(angle)
            {
            }
            color_stop(css_length length) :
                is_color_hint(true),
                length(length)
            {
            }
            color_stop(float angle) :
                is_color_hint(true),
                angle(angle)
            {
            }
        };

        string_id m_type;
        // linear gradient:       m_side default is gradient_side_none (use angle)
        // radial,conic gradient: m_side default is gradient_side_x_center | gradient_side_y_center (see
        // parse_gradient)
        uint32_t m_side = gradient_side_none;
        // linear gradient angle, used when m_side == gradient_side_none
        float                   angle = 180.0f;
        std::vector<color_stop> m_colors;
        // radial,conic position (together with m_side)
        css_length position_x;
        // radial,conic position (together with m_side)
        css_length position_y;
        // actual default depends on the number of radii provided, see
        // parse_radial_gradient_shape_size_position_interpolation
        radial_shape_t radial_shape = radial_shape_ellipse;
        // <radial-size>  https://drafts.csswg.org/css-images-3/#valdef-radial-gradient-radial-size
        // if radius is specified radial_extent will be set to none, see parse_radial_size
        radial_extent_t radial_extent = radial_extent_farthest_corner;
        css_length      radial_radius_x;
        css_length      radial_radius_y;
        float           conic_from_angle = 0;
        // If the host syntax does not define what color space interpolation should take place in, it defaults to
        // Oklab.
        color_space_t color_space = color_space_oklab;
        // Unless otherwise specified, if no specific hue interpolation algorithm is selected by the host syntax,
        // the default is shorter.
        hue_interpolation_t hue_interpolation = hue_interpolation_shorter;

        explicit gradient(string_id type = empty_id) :
            m_type(type)
        {
            position_x.predef(0);
            position_y.predef(0);

            radial_radius_x.predef(0);
            radial_radius_y.predef(0);
        }

        bool is_empty() const
        {
            return m_type == empty_id || m_colors.empty();
        }

        bool is_linear() const
        {
            return m_type == _linear_gradient_ || m_type == _repeating_linear_gradient_;
        }
        bool is_radial() const
        {
            return m_type == _radial_gradient_ || m_type == _repeating_radial_gradient_;
        }
        bool is_conic() const
        {
            return m_type == _conic_gradient_ || m_type == _repeating_conic_gradient_;
        }

        static gradient transparent;
    };

    class image
    {
      public:
        enum type
        {
            type_none,
            type_url,
            type_gradient,
        };
        type        type = type_none;
        std::string url;
        gradient    m_gradient;

        bool is_empty() const
        {
            switch(type)
            {
            case type_none:
                return true;
            case type_url:
                return url.empty();
            case type_gradient:
                return m_gradient.is_empty();
            }
            return true;
        }
    };

    bool parse_gradient(const css_token& token, gradient& gradient, document_container* container);
} // namespace litehtml

#endif // LITEHTML_GRADIENT_H
