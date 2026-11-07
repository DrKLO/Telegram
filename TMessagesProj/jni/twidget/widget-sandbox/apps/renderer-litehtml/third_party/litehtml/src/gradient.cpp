#include "html.h"
#include "gradient.h"
#include "css_parser.h"

namespace litehtml
{

    bool parse_linear_gradient_direction(const css_token_vector& tokens, int& index, float& angle, int& side);
    bool parse_linear_gradient_direction_and_interpolation(const css_token_vector& tokens, gradient& gradient);
    bool parse_color_interpolation_method(const css_token_vector& tokens, int& index, color_space_t& color_space,
                                          hue_interpolation_t& hue_interpolation);
    bool parse_gradient_position(const css_token_vector& tokens, int& index, gradient& gradient);
    bool parse_radial_gradient_shape_size_position_interpolation(const css_token_vector& tokens, gradient& result);
    bool parse_conic_gradient_angle_position_interpolation(const css_token_vector& tokens, gradient& gradient);
    template <class T>
    bool parse_color_stop_list(const std::vector<css_token_vector>& list, gradient& grad,
                               document_container* container);

    ////////////////////////////////////////////////////////////////////////////////////////////
    // These combinators are currently used only in one place because the code is usually shorter without them.

    using parse_fn = std::function<bool(const css_token_vector& tokens, int& index)>;

    // a?
    parse_fn opt(const parse_fn& a)
    {
        return [=](auto&... x) {
            a(x...);
            return true;
        };
    }

    // a b
    parse_fn seq(const parse_fn& a, const parse_fn& b)
    {
        return [=](auto& t, auto& i) {
            auto save   = i;
            bool result = a(t, i) && b(t, i);
            if(!result)
            {
                i = save; // backtrack
            }
            return result;
        };
    }

    // Not overloading operator|| because it is easier to get a bug: a || b || c does the wrong thing,
    // see the note at https://www.w3.org/TR/css-values-4/#component-combinators.
    // a || b
    parse_fn oror(const parse_fn& a, const parse_fn& b)
    {
        return [=](auto&... x) {
            if(a(x...))
            {
                b(x...);
                return true;
            }
            if(b(x...))
            {
                a(x...);
                return true;
            }
            return false;
        };
    }

    parse_fn operator""_x(const char* str, size_t len)
    {
        return [=](const css_token_vector& tokens, int& index) {
            if(at(tokens, index).ident() == std::string(str, len))
            {
                index++;
                return true;
            }
            return false;
        };
    }

    bool end(const css_token_vector& tokens, int index)
    {
        return index == static_cast<int>(tokens.size());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    // https://drafts.csswg.org/css-images-4/#gradients
    //
    // <gradient> =
    //   <linear-gradient()> | <repeating-linear-gradient()> |
    //   <radial-gradient()> | <repeating-radial-gradient()> |
    //   <conic-gradient()>  | <repeating-conic-gradient()>
    //
    bool parse_gradient(const css_token& token, gradient& result, document_container* container)
    {
        if(token.type != CV_FUNCTION)
        {
            return false;
        }

        auto type = _id(lowcase(token.name()));

        if(!is_one_of(type, _linear_gradient_, _repeating_linear_gradient_, _radial_gradient_,
                      _repeating_radial_gradient_, _conic_gradient_, _repeating_conic_gradient_))
        {
            return false;
        }

        gradient grad(type);

        if(!grad.is_linear())
        {
            // radial and conic position defaults to 'center'
            // https://drafts.csswg.org/css-images-3/#valdef-radial-gradient-position
            // https://drafts.csswg.org/css-images-4/#valdef-conic-gradient-position
            grad.m_side = gradient_side_x_center | gradient_side_y_center;
        }

        auto list = parse_comma_separated_list(token.value);
        if(list.empty())
        {
            return false;
        }

        bool ok;

        if(grad.is_linear())
        {
            ok = parse_linear_gradient_direction_and_interpolation(list[0], grad);
        } else if(grad.is_radial())
        {
            ok = parse_radial_gradient_shape_size_position_interpolation(list[0], grad);
        } else
        {
            ok = parse_conic_gradient_angle_position_interpolation(list[0], grad);
        }

        if(ok)
        {
            remove(list, 0);
        }

        if(grad.is_conic())
        {
            ok = parse_color_stop_list<float>(list, grad, container);
        } else
        {
            ok = parse_color_stop_list<css_length>(list, grad, container);
        }

        if(!ok)
        {
            return false;
        }

        result = grad;
        return true;
    }

    // parse <length-percentage> or <angle-percentage>
    bool parse_lenang(const css_token& tok, css_length& length)
    {
        return parse_length(tok, length, f_length_percentage);
    }
    bool parse_lenang(const css_token& tok, float& angle)
    {
        return parse_angle(tok, angle, true);
    }

    // <color-hint> = <length-percentage> | <angle-percentage>
    template <class T> // T == css_length or float
    bool parse_color_hint(const css_token_vector& tokens, std::vector<gradient::color_stop>& color_stops)
    {
        T lenang;
        if(tokens.size() == 1 && parse_lenang(tokens[0], lenang))
        {
            color_stops.push_back(lenang);
            return true;
        }
        return false;
    }

    // <linear-color-stop> = <color> <length-percentage>{1,2}?
    // <angular-color-stop> = <color> <angle-percentage>{1,2}?
    template <class T> // T == css_length or float
    bool parse_color_stop(const css_token_vector& tokens, std::vector<gradient::color_stop>& color_stops,
                          document_container* container)
    {
        if(tokens.empty() || tokens.size() > 3)
        {
            return false;
        }

        web_color color;
        if(!parse_color(tokens[0], color, container))
        {
            return false;
        }

        if(tokens.size() == 1) // <color>
        {
            color_stops.emplace_back(color);
            return true;
        }
        if(tokens.size() == 2) // <color> <length-angle-percentage>
        {
            T lenang;
            if(parse_lenang(tokens[1], lenang))
            {
                color_stops.emplace_back(color, lenang);
                return true;
            }
        } else if(tokens.size() == 3) // <color> <length-angle-percentage> <length-angle-percentage>
        {
            T lenang1, lenang2;
            if(parse_lenang(tokens[1], lenang1) && parse_lenang(tokens[2], lenang2))
            {
                color_stops.emplace_back(color, lenang1);
                color_stops.emplace_back(color, lenang2);
                return true;
            }
        }
        return false;
    }

    // <color-stop-list> = <color-stop> , [ <color-hint>? , <color-stop> ]#
    template <class T> // T == css_length or float
    bool parse_color_stop_list(const std::vector<css_token_vector>& list, gradient& grad, document_container* container)
    {
        if(list.size() < 2) // at least two color-stops must be present
        {
            return false;
        }

        if(!parse_color_stop<T>(list[0], grad.m_colors, container))
        {
            return false;
        }

        // [ <color-hint>? , <color-stop> ]#
        for(size_t i = 1; i < list.size(); i++)
        {
            if(parse_color_hint<T>(list[i], grad.m_colors))
            {
                i++;
                if(i == list.size())
                {
                    return false; // color-hint not followed by color-stop
                }
            }
            if(!parse_color_stop<T>(list[i], grad.m_colors, container))
            {
                return false;
            }
        }
        return true;
    }

    // https://drafts.csswg.org/css-images-4/#linear-gradients
    // [ <angle> | to <side-or-corner> ] || <color-interpolation-method>
    bool parse_linear_gradient_direction_and_interpolation(const css_token_vector& tokens, gradient& gradient)
    {
        float angle             = 180;
        int   side              = gradient_side_none;
        auto  color_space       = color_space_oklab;
        auto  hue_interpolation = hue_interpolation_shorter;

        int index = 0;
        if(parse_linear_gradient_direction(tokens, index, angle, side))
        {
            parse_color_interpolation_method(tokens, index, color_space, hue_interpolation);
        } else if(parse_color_interpolation_method(tokens, index, color_space, hue_interpolation))
        {
            parse_linear_gradient_direction(tokens, index, angle, side);
        } else
        {
            return false;
        }

        if(index != static_cast<int>(tokens.size()))
        {
            return false;
        }

        gradient.angle             = angle;
        gradient.m_side            = side;
        gradient.color_space       = color_space;
        gradient.hue_interpolation = hue_interpolation;
        return true;
    }

    // https://drafts.csswg.org/css-images-4/#linear-gradients
    // <angle> | to <side-or-corner>
    // <side-or-corner> = [left | right] || [top | bottom]
    bool parse_linear_gradient_direction(const css_token_vector& tokens, int& index, float& angle, int& side)
    {
        if(parse_angle(at(tokens, index), angle))
        {
            index++;
            return true;
        }

        if(at(tokens, index).ident() != "to")
        {
            return false;
        }

        std::string a = at(tokens, index + 1).ident();
        std::string b = at(tokens, index + 2).ident();

        if(is_one_of(a, "left", "right", "top", "bottom"))
        {
            if(!is_one_of(b, "left", "right", "top", "bottom"))
            {
                switch(_id(a))
                {
                case _top_:
                    angle = 0;
                    break;
                case _bottom_:
                    angle = 180;
                    break;
                case _left_:
                    angle = 270;
                    break;
                case _right_:
                    angle = 90;
                    break;
                default:
                    return false;
                }
                index += 2;
                return true;
            }
            // fix order
            if(is_one_of(a, "top", "bottom"))
            {
                swap(a, b);
            }

            // check order
            if(!is_one_of(a, "left", "right") || !is_one_of(b, "top", "bottom"))
            {
                return false;
            }

            side   = a == "left" ? gradient_side_left : gradient_side_right;
            side  |= b == "top" ? gradient_side_top : gradient_side_bottom;
            index += 3;
            return true;
        }
        return false;
    }

    // https://drafts.csswg.org/css-images-4/#typedef-conic-gradient-syntax
    // [ from <angle> ]? [ at <position> ]?
    bool parse_conic_angle_position(const css_token_vector& tokens, int& index, gradient& gradient)
    {
        if(at(tokens, index).ident() == "from" && parse_angle(at(tokens, index + 1), gradient.conic_from_angle))
        {
            index += 2;
        }

        int i = index;
        if(at(tokens, i).ident() == "at" && parse_gradient_position(tokens, ++i, gradient))
        {
            index = i;
        }

        return true;
    }
    // [ [ from <angle> ]? [ at <position> ]? ] || <color-interpolation-method>
    bool parse_conic_gradient_angle_position_interpolation(const css_token_vector& tokens, gradient& gradient)
    {
        if(tokens.empty())
        {
            return false;
        }

        auto color_space       = color_space_oklab;
        auto hue_interpolation = hue_interpolation_shorter;

        int index = 0;
        // checking color interpolation first because parse_conic_angle_position always succeeds
        if(parse_color_interpolation_method(tokens, index, color_space, hue_interpolation))
        {
            parse_conic_angle_position(tokens, index, gradient);
        } else if(parse_conic_angle_position(tokens, index, gradient))
        {
            parse_color_interpolation_method(tokens, index, color_space, hue_interpolation);
        } else
        {
            return false;
        }

        if(index != static_cast<int>(tokens.size()))
        {
            return false;
        }

        gradient.color_space       = color_space;
        gradient.hue_interpolation = hue_interpolation;
        return true;
    }

    const float pi = 3.14159265f;

    // https://drafts.csswg.org/css-values-4/#angles
    bool parse_angle(const css_token& tok, float& angle, bool percents_allowed)
    {
        // The unit identifier may be omitted if the <angle> is zero.
        // https://drafts.csswg.org/css-images-3/#linear-gradient-syntax
        if(tok.type == NUMBER && pixel_t(tok.n.number) == 0_px)
        {
            angle = 0;
            return true;
        }

        // <angle-percentage> in conic gradient
        if(tok.type == PERCENTAGE && percents_allowed)
        {
            angle = tok.n.number * 360 / 100;
            return true;
        }

        if(tok.type == DIMENSION)
        {
            switch(_id(lowcase(tok.unit())))
            {
            case _deg_:
                angle = tok.n.number;
                break;
            case _grad_:
                angle = (tok.n.number / 400) * 360;
                break;
            case _rad_:
                angle = (tok.n.number / (2 * pi)) * 360;
                break;
            case _turn_:
                angle = tok.n.number * 360;
                break;
            default:
                return false;
            }
            return true;
        }

        return false;
    }

    // https://www.w3.org/TR/css-color-4/#color-interpolation-method
    // <rectangular-color-space> = srgb | srgb-linear | display-p3 | a98-rgb | prophoto-rgb | rec2020 | lab | oklab |
    // xyz | xyz-d50 | xyz-d65 <polar-color-space> = hsl | hwb | lch | oklch <hue-interpolation-method> = [ shorter |
    // longer | increasing | decreasing ] hue <color-interpolation-method> = in [ <rectangular-color-space> |
    // <polar-color-space> <hue-interpolation-method>? ]
    bool parse_color_interpolation_method(const css_token_vector& tokens, int& index, color_space_t& color_space,
                                          hue_interpolation_t& hue_interpolation)
    {
        if(at(tokens, index).ident() == "in" &&
           parse_keyword(at(tokens, index + 1), color_space, color_space_strings, 1))
        {
            index += 2;
        } else
        {
            return false;
        }

        if(color_space >= color_space_polar_start &&
           at(tokens, index + 1).ident() ==
               "hue" && // must be checked before parse_keyword, otherwise hue_interpolation may be assigned a value
                        // when there is no "hue" keyword
           parse_keyword(at(tokens, index), hue_interpolation, hue_interpolation_strings, 1))
        {
            index += 2;
        }
        return true;
    }

    // https://www.w3.org/TR/css-images-3/#typedef-radial-size
    // <radial-size> = <radial-extent> | <length [0,∞]> | <length-percentage [0,∞]>{2}
    // <radial-extent> = closest-corner | closest-side | farthest-corner | farthest-side
    // Permitted values also depend on <radial-shape>, see parse_radial_gradient_shape_size_position_interpolation.
    // TODO: <radial-size> syntax was extended in https://drafts.csswg.org/css-images-4/#radial-size
    bool parse_radial_size(const css_token_vector& tokens, int& index, gradient& gradient)
    {
        auto& tok0 = at(tokens, index);
        auto& tok1 = at(tokens, index + 1);

        if(parse_keyword(tok0, gradient.radial_extent, radial_extent_strings, 1))
        {
            index++;
            return true;
        }

        css_length length[2];
        if(length[0].from_token(tok0, f_length_percentage | f_positive) &&
           length[1].from_token(tok1, f_length_percentage | f_positive))
        {
            gradient.radial_extent    = radial_extent_none;
            gradient.radial_radius_x  = length[0];
            gradient.radial_radius_y  = length[1];
            index                    += 2;
            return true;
        }

        if(length[0].from_token(tok0, f_length | f_positive))
        {
            gradient.radial_extent   = radial_extent_none;
            gradient.radial_radius_x = length[0];
            index++;
            return true;
        }

        return false;
    }

    bool parse_gradient_position(const css_token_vector& tokens, int& index, gradient& gradient)
    {
        css_length x, y;
        if(!parse_bg_position(tokens, index, x, y, false))
        {
            return false;
        }

        gradient.m_side = 0;
        if(x.is_predefined())
        {
            if(x.predef() == background_position_center)
            {
                gradient.m_side |= gradient_side_x_center;
            } else
            {
                gradient.m_side |= 1 << x.predef();
            }
        } else
        {
            gradient.m_side     |= gradient_side_x_length;
            gradient.position_x  = x;
        }

        if(y.is_predefined())
        {
            if(y.predef() == background_position_center)
            {
                gradient.m_side |= gradient_side_y_center;
            } else
            {
                gradient.m_side |= 1 << y.predef();
            }
        } else
        {
            gradient.m_side     |= gradient_side_y_length;
            gradient.position_y  = y;
        }
        return true;
    }

    // https://drafts.csswg.org/css-images-4/#radial-gradients
    // [ [ <radial-shape> || <radial-size> ]? [ at <position> ]? ] || <color-interpolation-method>
    bool parse_radial_gradient_shape_size_position_interpolation(const css_token_vector& tokens, gradient& result)
    {
        // this check is needed because parse may succeed without consuming any input
        if(tokens.empty())
        {
            return false;
        }

        auto shape        = radial_shape_none;
        auto radial_shape = [&](const css_token_vector& tokens, int& index) {
            constexpr auto vals = split_css_values<2>("circle;ellipse");
            if(!parse_keyword(at(tokens, index), shape, vals, 1))
            {
                return false;
            }
            index++;
            return true;
        };

        using namespace std::placeholders;
        gradient grad;
        // sets grad.radial_extent or grad.radial_radius_{x,y}
        parse_fn radial_size = std::bind(parse_radial_size, _1, _2, std::ref(grad));
        // sets grad.m_side and grad.radial_position_{x,y}
        parse_fn radial_position = std::bind(parse_gradient_position, _1, _2, std::ref(grad));

        auto color_space                = color_space_oklab;
        auto hue_interpolation          = hue_interpolation_shorter;
        auto color_interpolation_method = [&](const css_token_vector& tokens, int& index) {
            return parse_color_interpolation_method(tokens, index, color_space, hue_interpolation);
        };

        /////////////////////////////////////////////////////////////////////////////////////////

        auto parse = oror(color_interpolation_method, // first trying this because seq(opt,opt) always succeeds
                          seq(opt(oror(radial_shape, radial_size)), opt(seq("at"_x, radial_position))));

        int  index = 0;
        bool ok    = parse(tokens, index) && end(tokens, index);
        if(!ok)
        {
            return false;
        }

        /////////////////////////////////////////////////////////////////////////////////////////

        // If <radial-shape> is specified as circle or is omitted, the <radial-size> may be given explicitly as <length
        // [0,∞]>
        if(shape == radial_shape_ellipse &&
           // radius_x is specified, but radius_y is not
           !grad.radial_radius_x.is_predefined() && grad.radial_radius_y.is_predefined())
        {
            return false;
        }

        // If <radial-shape> is specified as ellipse or is omitted, <radial-size> may instead be given explicitly as
        // <length-percentage [0,∞]>{2}
        if(shape == radial_shape_circle &&
           // both radius_x and radius_y are specified
           !grad.radial_radius_y.is_predefined())
        {
            return false;
        }

        // If <radial-shape> is omitted, the ending shape defaults to a circle if the <radial-size> is a single
        // <length>, and to an ellipse otherwise.
        if(shape == radial_shape_none)
        {
            if(!grad.radial_radius_x.is_predefined() && grad.radial_radius_y.is_predefined())
            {
                shape = radial_shape_circle;
            } else
            {
                shape = radial_shape_ellipse;
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////

        result.radial_shape = shape;

        result.radial_extent   = grad.radial_extent;
        result.radial_radius_x = grad.radial_radius_x;
        result.radial_radius_y = grad.radial_radius_y;

        result.m_side     = grad.m_side;
        result.position_x = grad.position_x;
        result.position_y = grad.position_y;

        result.color_space       = color_space;
        result.hue_interpolation = hue_interpolation;

        return true;
    }

} // namespace litehtml
