#include <cstring>
#include <cmath>
#include "html.h"
#include "css_effects.h"
#include "style.h"
#include "web_color.h"
#include "css_parser.h"
#include "document_container.h"

namespace litehtml
{
    bool parse_clip_path(const css_token_vector& tokens, clip_path_shape& out)
    {
        out = clip_path_shape();
        if(tokens.size() != 1 || tokens[0].type != CV_FUNCTION) return false;
        const auto& fn = tokens[0];
        clip_path_shape shape;
        if(lowcase(fn.name()) == "polygon") shape.kind = clip_path_shape::polygon;
        else if(lowcase(fn.name()) == "inset") shape.kind = clip_path_shape::inset;
        else if(lowcase(fn.name()) == "circle") shape.kind = clip_path_shape::circle;
        else if(lowcase(fn.name()) == "ellipse") shape.kind = clip_path_shape::ellipse;
        else return false;
        size_t at = 0;
        const auto& args = fn.value;
        if(shape.kind == clip_path_shape::polygon)
        {
            if(!args.empty() && (args[0].ident() == "evenodd" || args[0].ident() == "nonzero"))
            {
                shape.evenodd = args[0].ident() == "evenodd";
                if(args.size() < 2 || args[1].ch != ',') return false;
                at = 2;
            }
            while(at < args.size())
            {
                for(int axis = 0; axis < 2; axis++)
                {
                    css_length len;
                    if(at >= args.size() || !len.from_token(args[at++], f_length | f_percentage)) return false;
                    shape.values.push_back(len);
                }
                if(at < args.size() && (args[at++].ch != ',' || at == args.size())) return false;
            }
            if(shape.values.size() < 6) return false;
        } else if(shape.kind == clip_path_shape::inset)
        {
            while(at < args.size() && args[at].ident() != "round")
            {
                css_length len;
                if(shape.values.size() == 4 || !len.from_token(args[at++], f_length | f_percentage)) return false;
                shape.values.push_back(len);
            }
            if(shape.values.empty()) return false;
            auto v = shape.values;
            shape.values = {v[0], v.size() > 1 ? v[1] : v[0], v.size() > 2 ? v[2] : v[0], v.size() > 3 ? v[3] : v.size() > 1 ? v[1] : v[0]};
            if(at < args.size())
            {
                at++;
                css_length radius;
                if(at + 1 != args.size() || !radius.from_token(args[at], f_length | f_percentage)) return false;
                if(radius.val() < 0) return false;
                shape.values.push_back(radius);
            }
        } else
        {
            const size_t radii = shape.kind == clip_path_shape::circle ? 1 : 2;
            for(size_t i = 0; i < radii; i++)
            {
                css_length len;
                if(at >= args.size() || !len.from_token(args[at++], f_length | f_percentage)) return false;
                if(len.val() < 0) return false;
                shape.values.push_back(len);
            }
            if(at == args.size())
            {
                shape.values.push_back(css_length(50, css_units_percentage));
                shape.values.push_back(css_length(50, css_units_percentage));
            } else
            {
                if(args[at++].ident() != "at" || at + 2 != args.size()) return false;
                for(int i = 0; i < 2; i++)
                {
                    css_length len;
                    const auto& tok = args[at++];
                    if(tok.ident() == "center") len = css_length(50, css_units_percentage);
                    else if(tok.ident() == (i == 0 ? "left" : "top")) len = 0;
                    else if(tok.ident() == (i == 0 ? "right" : "bottom")) len = css_length(100, css_units_percentage);
                    else if(!len.from_token(tok, f_length | f_percentage)) return false;
                    shape.values.push_back(len);
                }
            }
        }
        out = std::move(shape);
        return true;
    }

    vector_path clip_path_shape::resolve(const position& box) const
    {
        vector_path path;
        path.evenodd = evenodd;
        auto add = [&](vector_path::op_t op, std::initializer_list<float> points) {
            path.ops.push_back(op);
            path.points.insert(path.points.end(), points);
        };
        const float x = box.x, y = box.y, w = box.width, h = box.height;
        if(kind == polygon)
        {
            for(size_t i = 0; i + 1 < values.size(); i += 2)
                add(i == 0 ? vector_path::move : vector_path::line,
                    {x + float(values[i].calc_percent(box.width)), y + float(values[i + 1].calc_percent(box.height))});
        } else if(kind != none)
        {
            float l, t, r, b, rx, ry;
            if(kind == inset)
            {
                l = x + float(values[3].calc_percent(box.width));
                t = y + float(values[0].calc_percent(box.height));
                r = x + w - float(values[1].calc_percent(box.width));
                b = y + h - float(values[2].calc_percent(box.height));
                if(r < l) l = r = (l + r) / 2;
                if(b < t) t = b = (t + b) / 2;
                rx = values.size() > 4 ? float(values[4].calc_percent(box.width)) : 0;
                ry = values.size() > 4 ? float(values[4].calc_percent(box.height)) : 0;
                rx = std::min(rx, (r - l) / 2);
                ry = std::min(ry, (b - t) / 2);
            } else
            {
                size_t radii = kind == circle ? 1 : 2;
                float cx = x + float(values[radii].calc_percent(box.width));
                float cy = y + float(values[radii + 1].calc_percent(box.height));
                rx = float(values[0].calc_percent(kind == circle ? pixel_t(std::hypot(w, h) / std::sqrt(2.f)) : box.width));
                ry = kind == circle ? rx : float(values[1].calc_percent(box.height));
                l = cx - rx; r = cx + rx; t = cy - ry; b = cy + ry;
            }
            const float k = 0.5522847498f;
            add(vector_path::move, {l + rx, t});
            add(vector_path::line, {r - rx, t});
            add(vector_path::cubic, {r - rx + rx * k, t, r, t + ry - ry * k, r, t + ry});
            add(vector_path::line, {r, b - ry});
            add(vector_path::cubic, {r, b - ry + ry * k, r - rx + rx * k, b, r - rx, b});
            add(vector_path::line, {l + rx, b});
            add(vector_path::cubic, {l + rx - rx * k, b, l, b - ry + ry * k, l, b - ry});
            add(vector_path::line, {l, t + ry});
            add(vector_path::cubic, {l, t + ry - ry * k, l + rx - rx * k, t, l + rx, t});
        }
        if(!path.ops.empty()) add(vector_path::close, {});
        return path;
    }

    static bool parse_shadow_length(const css_token& tok, css_length& len)
    {
        return len.from_token(tok, f_length);
    }

    bool parse_box_shadow(const css_token_vector& tokens, box_shadow_vector& shadows, document_container* container)
    {
        shadows.clear();
        if(tokens.size() == 1 && tokens[0].ident() == "none")
        {
            return true;
        }
        box_shadow shadow;
        int        lengths   = 0;
        bool       has_color = false;
        auto       flush     = [&]() -> bool {
            if(lengths < 2)
            {
                return false;
            }
            shadows.push_back(shadow);
            shadow    = box_shadow();
            lengths   = 0;
            has_color = false;
            return true;
        };
        for(const auto& tok : tokens)
        {
            if(tok.ch == ',')
            {
                if(!flush())
                {
                    shadows.clear();
                    return false;
                }
                continue;
            }
            if(tok.ident() == "inset")
            {
                shadow.inset = true;
                continue;
            }
            css_length len;
            if(lengths < 4 && parse_shadow_length(tok, len))
            {
                css_length* slots[4] = {&shadow.x, &shadow.y, &shadow.blur, &shadow.spread};
                *slots[lengths++]    = len;
                continue;
            }
            web_color color;
            if(!has_color && parse_color(tok, color, container))
            {
                shadow.color = color;
                has_color    = true;
                continue;
            }
            shadows.clear();
            return false;
        }
        if(!flush())
        {
            shadows.clear();
            return false;
        }
        return true;
    }

    static bool function_args(const css_token& tok, std::vector<css_token>& args)
    {
        args.clear();
        for(const auto& arg : tok.value)
        {
            if(arg.ch != ',')
            {
                args.push_back(arg);
            }
        }
        return !args.empty();
    }

    static bool parse_number(const css_token& tok, float& value)
    {
        if(tok.type == NUMBER)
        {
            value = tok.n.number;
            return true;
        }
        if(tok.type == PERCENTAGE)
        {
            value = tok.n.number / 100;
            return true;
        }
        return false;
    }

    static bool parse_radians(const css_token& tok, float& value)
    {
        float degrees = 0;
        if(!parse_angle(tok, degrees))
        {
            return false;
        }
        value = degrees * 3.14159265f / 180;
        return true;
    }

    struct calc_value
    {
        double      number = 0;
        std::string unit;
    };

    static bool calc_sum(const css_token_vector& tokens, size_t& index, calc_value& out, int depth);

    static bool calc_atom(const css_token_vector& tokens, size_t& index, calc_value& out, int depth)
    {
        while(index < tokens.size() && tokens[index].type == WHITESPACE)
            index++;
        if(index >= tokens.size() || depth > 16)
            return false;
        const css_token& tok = tokens[index++];
        if(tok.type == NUMBER)
        {
            out = {tok.n.number, ""};
            return true;
        }
        if(tok.type == PERCENTAGE)
        {
            out = {tok.n.number, "%"};
            return true;
        }
        if(tok.type == DIMENSION)
        {
            out = {tok.n.number, lowcase(tok.unit())};
            return true;
        }
        if(tok.type == CV_FUNCTION && is_one_of(lowcase(tok.name()), "min", "max", "clamp"))
        {
            const std::string name = lowcase(tok.name());
            std::vector<calc_value> args;
            size_t inner = 0;
            while(true)
            {
                calc_value arg;
                if(!calc_sum(tok.value, inner, arg, depth + 1))
                    return false;
                args.push_back(arg);
                while(inner < tok.value.size() && tok.value[inner].type == WHITESPACE)
                    inner++;
                if(inner == tok.value.size())
                    break;
                if(tok.value[inner].ch != ',')
                    return false;
                inner++;
            }
            if(args.size() > 32 || (name == "clamp" && args.size() != 3))
                return false;
            std::string unit;
            for(const auto& arg : args)
                if(!arg.unit.empty())
                    unit = arg.unit;
            for(const auto& arg : args)
                if(arg.unit != unit && !(arg.unit.empty() && arg.number == 0))
                    return false;
            double value = args[0].number;
            if(name == "clamp")
                value = std::max(args[0].number, std::min(args[1].number, args[2].number));
            else
                for(const auto& arg : args)
                    value = name == "min" ? std::min(value, arg.number) : std::max(value, arg.number);
            out = {value, unit};
            return std::isfinite(value);
        }
        if(tok.type == ROUND_BLOCK || (tok.type == CV_FUNCTION && lowcase(tok.name()) == "calc"))
        {
            size_t inner = 0;
            if(!calc_sum(tok.value, inner, out, depth + 1))
                return false;
            while(inner < tok.value.size() && tok.value[inner].type == WHITESPACE)
                inner++;
            return inner == tok.value.size();
        }
        return false;
    }

    static int calc_operator(const css_token_vector& tokens, size_t& index, const char* allowed)
    {
        size_t probe = index;
        while(probe < tokens.size() && tokens[probe].type == WHITESPACE)
            probe++;
        if(probe < tokens.size() && tokens[probe].ch > 0 && tokens[probe].ch < 128 && strchr(allowed, tokens[probe].ch))
        {
            index = probe + 1;
            return tokens[probe].ch;
        }
        return 0;
    }

    static bool calc_product(const css_token_vector& tokens, size_t& index, calc_value& out, int depth)
    {
        if(!calc_atom(tokens, index, out, depth))
            return false;
        while(int op = calc_operator(tokens, index, "*/"))
        {
            calc_value right;
            if(!calc_atom(tokens, index, right, depth))
                return false;
            if(op == '*')
            {
                if(!out.unit.empty() && !right.unit.empty())
                    return false;
                out = {out.number * right.number, out.unit.empty() ? right.unit : out.unit};
            } else
            {
                if(!right.unit.empty() || right.number == 0)
                    return false;
                out.number /= right.number;
            }
        }
        return std::isfinite(out.number);
    }

    static bool calc_sum(const css_token_vector& tokens, size_t& index, calc_value& out, int depth)
    {
        if(!calc_product(tokens, index, out, depth))
            return false;
        while(int op = calc_operator(tokens, index, "+-"))
        {
            calc_value right;
            if(!calc_product(tokens, index, right, depth))
                return false;
            if(out.unit != right.unit && out.number != 0 && right.number != 0)
                return false;
            std::string unit = out.unit.empty() ? right.unit : out.unit;
            out = {op == '+' ? out.number + right.number : out.number - right.number, unit};
        }
        return std::isfinite(out.number);
    }

    static bool fold_calc(css_token& tok)
    {
        if(tok.type != CV_FUNCTION || lowcase(tok.name()) != "calc")
            return true;
        calc_value value;
        size_t     index = 0;
        if(!calc_sum(tok.value, index, value, 0))
            return false;
        while(index < tok.value.size() && tok.value[index].type == WHITESPACE)
            index++;
        if(index != tok.value.size())
            return false;
        if(value.unit.empty())
            tok = css_token(NUMBER, float(value.number), css_number_number);
        else if(value.unit == "%")
            tok = css_token(PERCENTAGE, float(value.number), css_number_number);
        else
            tok = css_token(DIMENSION, float(value.number), css_number_number, value.unit);
        return true;
    }

    static bool fold_env(css_token& tok)
    {
        if(tok.value.empty() || tok.value[0].type != IDENT)
            return false;
        const std::string name = lowcase(tok.value[0].name());
        if(tok.value.size() > 2 && tok.value[1].ch == ',')
        {
            css_token_vector fallback(tok.value.begin() + 2, tok.value.end());
            if(fallback.size() != 1)
                return false;
            tok = fallback[0];
            return true;
        }
        if(tok.value.size() == 1 && (name.rfind("safe-area-inset-", 0) == 0 || name.rfind("keyboard-inset-", 0) == 0 ||
                                     name.rfind("safe-area-max-inset-", 0) == 0))
        {
            tok = css_token(DIMENSION, 0, css_number_integer, "px");
            return true;
        }
        return false;
    }

    static bool mix_part(const css_token_vector& part, web_color& color, float& percent, document_container* container)
    {
        percent = -1;
        bool have_color = false;
        for(const auto& tok : part)
        {
            if(tok.type == PERCENTAGE && percent < 0)
                percent = tok.n.number;
            else if(!have_color && parse_color(tok, color, container) && !(color == web_color::current_color))
                have_color = true;
            else
                return false;
        }
        return have_color && (percent < 0 || (percent >= 0 && percent <= 100));
    }

    static bool fold_color_mix(css_token& tok, document_container* container)
    {
        auto parts = parse_comma_separated_list(tok.value);
        if(parts.size() != 3 || parts[0].size() != 2 || parts[0][0].ident() != "in" || parts[0][1].ident() != "srgb")
            return false;
        web_color a, b;
        float     pa, pb;
        if(!mix_part(parts[1], a, pa, container) || !mix_part(parts[2], b, pb, container))
            return false;
        if(pa < 0 && pb < 0)
            pa = pb = 50;
        else if(pa < 0)
            pa = 100 - pb;
        else if(pb < 0)
            pb = 100 - pa;
        const float sum = pa + pb;
        if(sum <= 0)
            return false;
        const float multiplier = sum < 100 ? sum / 100 : 1;
        const float wa = pa / sum, wb = pb / sum;
        const float aa = a.alpha / 255.f, ab = b.alpha / 255.f;
        const float alpha = aa * wa + ab * wb;
        auto channel = [&](float ca, float cb) {
            if(alpha <= 0)
                return 0.f;
            return std::round(std::min(255.f, std::max(0.f, (ca * aa * wa + cb * ab * wb) / alpha)));
        };
        char hex[10];
        snprintf(hex, sizeof(hex), "%02x%02x%02x%02x", int(channel(a.red, b.red)), int(channel(a.green, b.green)),
                 int(channel(a.blue, b.blue)), int(std::round(std::min(1.f, std::max(0.f, alpha * multiplier)) * 255)));
        css_token folded(HASH, std::string(hex));
        folded.hash_type = css_hash_unrestricted;
        tok = folded;
        return true;
    }

    static int viewport_axis(const css_token& token)
    {
        if(token.type != DIMENSION)
            return 0;
        const std::string unit = lowcase(token.unit());
        if(is_one_of(unit, "vw", "svw", "lvw", "dvw"))
            return 1;
        if(is_one_of(unit, "vh", "svh", "lvh", "dvh"))
            return 2;
        if(is_one_of(unit, "vmin", "svmin", "lvmin", "dvmin"))
            return 3;
        if(is_one_of(unit, "vmax", "svmax", "lvmax", "dvmax"))
            return 4;
        return 0;
    }

    bool fold_math(css_token_vector& tokens, document_container* container, int depth)
    {
        if(depth > 16)
            return false;
        bool changed = false;
        for(auto& tok : tokens)
        {
            if(int axis = viewport_axis(tok); axis && container)
            {
                media_features media;
                container->get_media_features(media);
                const float width = media.width.value(), height = media.height.value();
                const float extent = axis == 1 ? width : axis == 2 ? height : axis == 3 ? std::min(width, height) : std::max(width, height);
                tok = css_token(DIMENSION, extent * tok.n.number / 100, css_number_number, "px");
                changed = true;
            }
            if(!tok.is_component_value())
                continue;
            if(tok.type == CV_FUNCTION && lowcase(tok.name()) == "env" && fold_env(tok))
            {
                changed = true;
                if(!tok.is_component_value())
                    continue;
            }
            if(fold_math(tok.value, container, depth + 1))
                changed = true;
            if(tok.type != CV_FUNCTION)
                continue;
            const std::string name = lowcase(tok.name());
            if(is_one_of(name, "calc", "min", "max", "clamp"))
            {
                css_token_vector single = {tok};
                calc_value value;
                size_t index = 0;
                if(!calc_atom(single, index, value, 0))
                    continue;
                if(value.unit.empty())
                    tok = css_token(NUMBER, float(value.number), css_number_number);
                else if(value.unit == "%")
                    tok = css_token(PERCENTAGE, float(value.number), css_number_number);
                else
                    tok = css_token(DIMENSION, float(value.number), css_number_number, value.unit);
                changed = true;
            } else if(name == "color-mix" && fold_color_mix(tok, container))
            {
                changed = true;
            }
        }
        return changed;
    }

    bool has_foldable_math(const css_token_vector& tokens)
    {
        for(const auto& tok : tokens)
        {
            if(viewport_axis(tok))
                return true;
            if(tok.type == CV_FUNCTION && is_one_of(lowcase(tok.name()), "calc", "min", "max", "clamp", "env", "color-mix"))
                return true;
            if(tok.is_component_value() && has_foldable_math(tok.value))
                return true;
        }
        return false;
    }

    bool has_viewport_units(const css_token_vector& tokens)
    {
        for(const auto& tok : tokens)
        {
            if(viewport_axis(tok))
                return true;
            if(tok.is_component_value() && has_viewport_units(tok.value))
                return true;
        }
        return false;
    }

    bool has_viewport_units(const std::string& text)
    {
        const size_t count = text.size();
        auto word = [&](size_t at, const char* w) {
            for(size_t k = 0; w[k]; k++)
                if(at + k >= count || std::tolower(static_cast<unsigned char>(text[at + k])) != w[k])
                    return false;
            return true;
        };
        for(size_t i = 1; i + 1 < count; i++)
        {
            if(text[i] != 'v' && text[i] != 'V')
                continue;
            size_t     number = i - 1;
            const char prefix = static_cast<char>(std::tolower(static_cast<unsigned char>(text[number])));
            if((prefix == 's' || prefix == 'l' || prefix == 'd') && number > 0)
                number--;
            const unsigned char digit = static_cast<unsigned char>(text[number]);
            if(!std::isdigit(digit) && digit != '.')
                continue;
            size_t     end  = i + 1;
            const char unit = static_cast<char>(std::tolower(static_cast<unsigned char>(text[end])));
            if(unit == 'w' || unit == 'h')
                end += 1;
            else if(word(end, "min") || word(end, "max"))
                end += 3;
            else
                continue;
            if(end >= count || !std::isalpha(static_cast<unsigned char>(text[end])))
                return true;
        }
        return false;
    }

    float filter_reach(const filter_vector& filters)
    {
        float reach = 0;
        for(const auto& fn : filters)
        {
            if(fn.kind == filter_function::blur)
            {
                reach += std::ceil(std::fabs(fn.radius.val()) * 3);
            } else if(fn.kind == filter_function::drop_shadow)
            {
                reach += std::ceil(std::fabs(fn.radius.val()) * 3) + std::max(std::fabs(fn.x.val()), std::fabs(fn.y.val()));
            }
        }
        return reach;
    }

    bool parse_filter(const css_token_vector& tokens, filter_vector& functions, document_container* container)
    {
        functions.clear();
        if(tokens.size() == 1 && tokens[0].ident() == "none")
        {
            return true;
        }
        if(tokens.empty() || tokens.size() > 32)
        {
            return false;
        }
        std::vector<css_token> args;
        for(const auto& tok : tokens)
        {
            if(tok.type != CV_FUNCTION)
            {
                functions.clear();
                return false;
            }
            function_args(tok, args);
            for(auto& arg : args)
            {
                if(!fold_calc(arg))
                {
                    functions.clear();
                    return false;
                }
            }
            std::string     name = lowcase(tok.name());
            filter_function fn;
            bool            ok = args.size() <= 1;
            if(name == "blur")
            {
                fn.kind = filter_function::blur;
                if(!args.empty())
                {
                    ok = ok && fn.radius.from_token(args[0], f_length | f_positive);
                }
            } else if(name == "hue-rotate")
            {
                fn.kind = filter_function::hue_rotate;
                if(!args.empty())
                {
                    ok = ok && (args[0].type == NUMBER && args[0].n.number == 0 ? true : parse_angle(args[0], fn.amount));
                }
            } else if(name == "drop-shadow")
            {
                fn.kind     = filter_function::drop_shadow;
                int  count  = 0;
                bool colors = false;
                ok          = !args.empty() && args.size() <= 4;
                for(size_t i = 0; ok && i < args.size(); i++)
                {
                    css_length len;
                    if(count < 3 && len.from_token(args[i], f_length))
                    {
                        css_length* slots[3] = {&fn.x, &fn.y, &fn.radius};
                        *slots[count++]      = len;
                        continue;
                    }
                    web_color color;
                    if(!colors && parse_color(args[i], color, container))
                    {
                        fn.color = color;
                        colors   = true;
                        continue;
                    }
                    ok = false;
                }
                ok = ok && count >= 2 && fn.radius.val() >= 0;
            } else
            {
                static const std::pair<const char*, filter_function::kind_t> amounts[] = {
                    {"brightness", filter_function::brightness}, {"contrast", filter_function::contrast},
                    {"grayscale", filter_function::grayscale},   {"invert", filter_function::invert},
                    {"opacity", filter_function::opacity},       {"saturate", filter_function::saturate},
                    {"sepia", filter_function::sepia},
                };
                ok = false;
                for(const auto& entry : amounts)
                {
                    if(name == entry.first)
                    {
                        fn.kind   = entry.second;
                        fn.amount = 1;
                        ok        = args.size() <= 1 && (args.empty() || parse_number(args[0], fn.amount)) && fn.amount >= 0;
                        if(ok && (fn.kind == filter_function::grayscale || fn.kind == filter_function::invert ||
                                  fn.kind == filter_function::opacity || fn.kind == filter_function::sepia))
                        {
                            fn.amount = std::min(fn.amount, 1.f);
                        }
                        break;
                    }
                }
            }
            if(!ok || !std::isfinite(fn.amount))
            {
                functions.clear();
                return false;
            }
            functions.push_back(fn);
        }
        return true;
    }

    static bool fast_alpha(char c)
    {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static bool fast_digit(char c)
    {
        return c >= '0' && c <= '9';
    }

    static bool fast_one_of(const std::string& word, std::initializer_list<const char*> list)
    {
        std::string low = lowcase(word);
        for(const char* item : list)
        {
            if(low == item)
                return true;
        }
        return false;
    }

    bool fast_transform_tokens(const std::string& value, css_token_vector& tokens)
    {
        tokens.clear();
        const size_t size = value.size();
        if(size == 0 || size > 512)
            return false;
        auto ws = [](char c) { return c == ' ' || c == '\t' || c == '\n'; };
        for(char c : value)
        {
            if(!(fast_alpha(c) || fast_digit(c) || ws(c) || c == '.' || c == ',' || c == '+' || c == '-' || c == '(' ||
                 c == ')' || c == '%'))
                return false;
        }
        css_token_vector flat;
        size_t           i = 0;
        auto             whitespace = [&]() {
            while(i < size && ws(value[i]))
                i++;
        };
        auto word = [&]() {
            size_t start = i;
            while(i < size && fast_alpha(value[i]))
                i++;
            return value.substr(start, i - start);
        };
        auto ends_ident = [&]() {
            return i >= size || !(fast_alpha(value[i]) || fast_digit(value[i]) || value[i] == '-');
        };
        whitespace();
        if(i < size && fast_alpha(value[i]))
        {
            size_t      start = i;
            std::string name  = word();
            if(lowcase(name) == "none" && ends_ident() && (i >= size || value[i] != '('))
            {
                css_token tok(IDENT, name);
                tok.repr = value.substr(start, i - start);
                flat.push_back(std::move(tok));
                whitespace();
                if(i != size)
                    return false;
                tokens = std::move(flat);
                return true;
            }
            i = start;
        }
        int functions = 0;
        while(i < size)
        {
            std::string name  = word();
            if(name.empty() || i >= size || value[i] != '(' || ++functions > 32 ||
               !fast_one_of(name, {"translate", "translatex", "translatey", "rotate", "scale", "scalex", "scaley",
                                   "skewx", "skewy", "matrix"}))
                return false;
            i++;
            css_token fn(CV_FUNCTION, name);
            int args = 0;
            while(true)
            {
                whitespace();
                if(i >= size)
                    return false;
                char c = value[i];
                if(c == ')')
                {
                    flat.push_back(std::move(fn));
                    i++;
                    break;
                }
                if(c == ',')
                {
                    css_token tok(COMMA);
                    tok.repr = ",";
                    fn.value.push_back(std::move(tok));
                    i++;
                    continue;
                }
                size_t num = i;
                if(c == '+' || c == '-')
                    i++;
                size_t digits = i;
                while(i < size && fast_digit(value[i]))
                    i++;
                bool integral = i > digits;
                bool fraction = false;
                if(i + 1 < size && value[i] == '.' && fast_digit(value[i + 1]))
                {
                    i++;
                    while(i < size && fast_digit(value[i]))
                        i++;
                    fraction = true;
                }
                if(!integral && !fraction)
                    return false;
                if(i - num > 32)
                    return false;
                double number = css_tokenizer::convert_string_to_number(value.substr(num, i - num));
                if(!std::isfinite(number) || std::fabs(number) > 1e9)
                    return false;
                css_number_type type = fraction ? css_number_number : css_number_integer;
                if(i < size && value[i] == '%')
                {
                    i++;
                    css_token tok(PERCENTAGE, static_cast<float>(number));
                    tok.repr = value.substr(num, i - num);
                    fn.value.push_back(std::move(tok));
                } else if(i < size && fast_alpha(value[i]))
                {
                    std::string unit = word();
                    if(!ends_ident() || !fast_one_of(unit, {"px", "deg", "rad", "turn"}))
                        return false;
                    css_token tok(DIMENSION, static_cast<float>(number), type, unit);
                    tok.repr = value.substr(num, i - num);
                    fn.value.push_back(std::move(tok));
                } else
                {
                    css_token tok(NUMBER, static_cast<float>(number), type);
                    tok.repr = value.substr(num, i - num);
                    fn.value.push_back(std::move(tok));
                }
                if(++args > 6)
                    return false;
                if(i < size && !(ws(value[i]) || value[i] == ',' || value[i] == ')'))
                    return false;
            }
            if(args == 0)
                return false;
            whitespace();
        }
        if(functions == 0)
            return false;
        tokens = std::move(flat);
        return true;
    }

    bool parse_transform(const css_token_vector& tokens, transform_vector& functions)
    {
        functions.clear();
        if(tokens.size() == 1 && tokens[0].ident() == "none")
        {
            return true;
        }
        std::vector<css_token> args;
        for(const auto& tok : tokens)
        {
            if(tok.type != CV_FUNCTION || !function_args(tok, args))
            {
                functions.clear();
                return false;
            }
            // Math that mixes % and px does not fold: it stays a calc length, which layout resolves
            // against the box (css_length::from_token), and fails there if it is no length.
            for(auto& arg : args)
                fold_calc(arg);
            std::string        name = lowcase(tok.name());
            transform_function fn;
            bool               ok = true;
            if(name == "translate" || name == "translatex" || name == "translatey" || name == "translate3d")
            {
                fn.kind = transform_function::translate;
                if(name == "translatey")
                {
                    ok = fn.y.from_token(args[0], f_length_percentage);
                } else
                {
                    ok = fn.x.from_token(args[0], f_length_percentage);
                    if(ok && args.size() > 1 && name != "translatex")
                    {
                        ok = fn.y.from_token(args[1], f_length_percentage);
                    }
                }
            } else if(name == "scale" || name == "scalex" || name == "scaley" || name == "scale3d")
            {
                fn.kind = transform_function::scale;
                float sx = 1, sy = 1;
                if(name == "scaley")
                {
                    ok = parse_number(args[0], sy);
                } else
                {
                    ok = parse_number(args[0], sx);
                    sy = name == "scalex" ? 1 : sx;
                    if(ok && args.size() > 1 && name != "scalex")
                    {
                        ok = parse_number(args[1], sy);
                    }
                }
                fn.v[0] = sx;
                fn.v[1] = sy;
            } else if(name == "rotate" || name == "rotatez")
            {
                fn.kind = transform_function::rotate;
                ok      = parse_radians(args[0], fn.v[0]);
            } else if(name == "rotatex" || name == "rotatey")
            {
                fn.kind = name == "rotatex" ? transform_function::rotate_x : transform_function::rotate_y;
                ok      = parse_radians(args[0], fn.v[0]);
            } else if(name == "rotate3d")
            {
                float x = 0, y = 0, z = 0;
                ok = args.size() == 4 && parse_number(args[0], x) && parse_number(args[1], y) &&
                     parse_number(args[2], z) && parse_radians(args[3], fn.v[0]);
                if(ok && x == 0 && y == 0)
                {
                    fn.kind = transform_function::rotate;
                    fn.v[0] = z < 0 ? -fn.v[0] : fn.v[0];
                } else if(ok && y == 0 && z == 0)
                {
                    fn.kind = transform_function::rotate_x;
                } else if(ok && x == 0 && z == 0)
                {
                    fn.kind = transform_function::rotate_y;
                } else
                {
                    continue;
                }
            } else if(name == "skew" || name == "skewx" || name == "skewy")
            {
                fn.kind = transform_function::skew;
                if(name == "skewy")
                {
                    ok = parse_radians(args[0], fn.v[1]);
                } else
                {
                    ok = parse_radians(args[0], fn.v[0]);
                    if(ok && args.size() > 1 && name == "skew")
                    {
                        ok = parse_radians(args[1], fn.v[1]);
                    }
                }
            } else if(name == "matrix")
            {
                fn.kind = transform_function::matrix;
                ok      = args.size() == 6;
                for(size_t i = 0; ok && i < 6; i++)
                {
                    ok = args[i].type == NUMBER;
                    if(ok)
                    {
                        fn.v[i] = args[i].n.number;
                    }
                }
            } else if(name == "perspective" || name == "translatez")
            {
                continue;
            } else
            {
                ok = false;
            }
            if(!ok)
            {
                functions.clear();
                return false;
            }
            functions.push_back(fn);
        }
        return true;
    }

    bool parse_transform_origin(const css_token_vector& tokens, css_length& x, css_length& y)
    {
        x = css_length(50, css_units_percentage);
        y = css_length(50, css_units_percentage);
        int  index    = 0;
        bool x_set    = false;
        bool y_set    = false;
        for(const auto& tok : tokens)
        {
            if(index >= 2)
            {
                break;
            }
            std::string ident = tok.ident();
            css_length  len;
            if(ident == "left" || ident == "right")
            {
                x     = css_length(ident == "left" ? 0.f : 100.f, css_units_percentage);
                x_set = true;
            } else if(ident == "top" || ident == "bottom")
            {
                y     = css_length(ident == "top" ? 0.f : 100.f, css_units_percentage);
                y_set = true;
            } else if(ident == "center")
            {
            } else if(len.from_token(tok, f_length_percentage))
            {
                if(index == 0 && !x_set)
                {
                    x     = len;
                    x_set = true;
                } else
                {
                    y     = len;
                    y_set = true;
                }
            } else
            {
                return false;
            }
            index++;
        }
        return true;
    }

    static bool parse_track_breadth(const css_token& tok, grid_track_size::type_t& type, css_length& len, float& fr)
    {
        std::string ident = tok.ident();
        if(ident == "auto")
        {
            type = grid_track_size::automatic;
            return true;
        }
        if(ident == "min-content")
        {
            type = grid_track_size::min_content;
            return true;
        }
        if(ident == "max-content")
        {
            type = grid_track_size::max_content;
            return true;
        }
        if(tok.type == DIMENSION && lowcase(tok.unit()) == "fr" && tok.n.number >= 0)
        {
            type = grid_track_size::fraction;
            fr   = tok.n.number;
            return true;
        }
        if(len.from_token(tok, f_length_percentage | f_positive))
        {
            type = grid_track_size::length;
            return true;
        }
        return false;
    }

    static bool parse_track_size(const css_token& tok, grid_track_size& size)
    {
        if(tok.type == CV_FUNCTION && lowcase(tok.name()) == "minmax")
        {
            std::vector<css_token> args;
            if(!function_args(tok, args) || args.size() != 2)
            {
                return false;
            }
            float fr = 0;
            if(!parse_track_breadth(args[0], size.min_type, size.min_len, fr) ||
               size.min_type == grid_track_size::fraction)
            {
                return false;
            }
            return parse_track_breadth(args[1], size.max_type, size.max_len, size.fr);
        }
        if(tok.type == CV_FUNCTION && lowcase(tok.name()) == "fit-content")
        {
            std::vector<css_token> args;
            if(!function_args(tok, args) || args.size() != 1 || !size.max_len.from_token(args[0], f_length_percentage))
            {
                return false;
            }
            size.min_type = grid_track_size::automatic;
            size.max_type = grid_track_size::length;
            return true;
        }
        if(!parse_track_breadth(tok, size.max_type, size.max_len, size.fr))
        {
            return false;
        }
        if(size.max_type == grid_track_size::fraction)
        {
            size.min_type = grid_track_size::automatic;
        } else
        {
            size.min_type = size.max_type;
            size.min_len  = size.max_len;
        }
        return true;
    }

    bool parse_grid_track_list(const css_token_vector& tokens, grid_track_list& list)
    {
        list = grid_track_list();
        if(tokens.size() == 1 && tokens[0].ident() == "none")
        {
            return true;
        }
        for(const auto& tok : tokens)
        {
            if(tok.type == SQUARE_BLOCK)
            {
                continue;
            }
            if(tok.type == CV_FUNCTION && lowcase(tok.name()) == "repeat")
            {
                std::vector<css_token> args;
                if(!function_args(tok, args) || args.size() < 2)
                {
                    return false;
                }
                std::vector<grid_track_size> sizes;
                for(size_t i = 1; i < args.size(); i++)
                {
                    if(args[i].type == SQUARE_BLOCK)
                    {
                        continue;
                    }
                    grid_track_size size;
                    if(!parse_track_size(args[i], size))
                    {
                        return false;
                    }
                    sizes.push_back(size);
                }
                if(sizes.empty())
                {
                    return false;
                }
                std::string count = args[0].ident();
                if(count == "auto-fill" || count == "auto-fit")
                {
                    if(list.repeat_at >= 0)
                    {
                        return false;
                    }
                    list.repeat_at = static_cast<int>(list.tracks.size());
                    list.auto_fit  = count == "auto-fit";
                    list.repeat    = sizes;
                } else if(args[0].type == NUMBER && args[0].n.number_type == css_number_integer && args[0].n.number > 0)
                {
                    int n = std::min(static_cast<int>(args[0].n.number), 1000);
                    for(int i = 0; i < n; i++)
                    {
                        list.tracks.insert(list.tracks.end(), sizes.begin(), sizes.end());
                    }
                } else
                {
                    return false;
                }
                continue;
            }
            grid_track_size size;
            if(!parse_track_size(tok, size))
            {
                return false;
            }
            list.tracks.push_back(size);
        }
        return true;
    }

    bool parse_grid_placement(const css_token_vector& tokens, grid_placement& placement)
    {
        placement = grid_placement();
        int  part = 0;
        bool span = false;
        for(const auto& tok : tokens)
        {
            if(tok.ch == '/')
            {
                if(++part > 1)
                {
                    return false;
                }
                span = false;
                continue;
            }
            std::string ident = tok.ident();
            if(ident == "span")
            {
                span = true;
                continue;
            }
            if(ident == "auto")
            {
                continue;
            }
            if(tok.type != NUMBER || tok.n.number_type != css_number_integer || tok.n.number == 0)
            {
                return false;
            }
            int value = std::max(-1000, std::min(1000, static_cast<int>(tok.n.number)));
            if(span)
            {
                if(value < 0)
                {
                    return false;
                }
                placement.span = value;
            } else if(part == 0)
            {
                placement.start = value;
            } else
            {
                placement.end = value;
            }
        }
        return true;
    }
} // namespace litehtml
