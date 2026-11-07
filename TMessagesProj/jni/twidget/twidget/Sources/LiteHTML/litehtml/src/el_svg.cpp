#include <unordered_set>
#include <atomic>
#include <cstring>
#include <cmath>
#include <cctype>
#include <functional>
#include "html.h"
#include "el_svg.h"
#include "render_item.h"
#include "render_image.h"
#include "document_container.h"
#include "css_parser.h"
#include <cmath>
#include <cstdlib>
#include <cstring>

namespace litehtml
{
    static std::atomic<bool> svg_group_cache_enabled{getenv("LH_NO_SVG_GROUP_CACHE") == nullptr};

    void svg_set_group_cache_enabled(bool enabled)
    {
        svg_group_cache_enabled.store(enabled, std::memory_order_relaxed);
    }

    bool has_var(const css_token_vector& tokens);
    void subst_vars_(string_id name, css_token_vector& tokens, const html_tag* el);

    namespace
    {
        const float pi = 3.14159265f;

        void skip_separators(const char*& s)
        {
            while(*s && (std::isspace(static_cast<unsigned char>(*s)) || *s == ','))
            {
                s++;
            }
        }

        bool next_number(const char*& s, float& value)
        {
            skip_separators(s);
            if(!*s)
            {
                return false;
            }
            const char* start = s;
            const char* p     = s;
            if(*p == '+' || *p == '-')
            {
                p++;
            }
            bool digits = false;
            while(std::isdigit(static_cast<unsigned char>(*p)))
            {
                p++;
                digits = true;
            }
            if(*p == '.')
            {
                p++;
                while(std::isdigit(static_cast<unsigned char>(*p)))
                {
                    p++;
                    digits = true;
                }
            }
            if(!digits)
            {
                return false;
            }
            if(*p == 'e' || *p == 'E')
            {
                const char* e = p + 1;
                if(*e == '+' || *e == '-')
                {
                    e++;
                }
                if(std::isdigit(static_cast<unsigned char>(*e)))
                {
                    p = e;
                    while(std::isdigit(static_cast<unsigned char>(*p)))
                    {
                        p++;
                    }
                }
            }
            value = std::strtof(std::string(start, p).c_str(), nullptr);
            s     = p;
            return true;
        }

        bool next_flag(const char*& s, int& flag)
        {
            skip_separators(s);
            if(*s == '0' || *s == '1')
            {
                flag = *s - '0';
                s++;
                return true;
            }
            return false;
        }

        std::vector<float> numbers(const char* s)
        {
            std::vector<float> out;
            float              v;
            while(s && next_number(s, v))
            {
                out.push_back(v);
                while(*s && std::isalpha(static_cast<unsigned char>(*s)))
                {
                    s++;
                }
                if(*s == '%')
                {
                    s++;
                }
            }
            return out;
        }

        float attr_length(const element::ptr& el, const char* name, float base, float def)
        {
            const char* value = el->get_attr(name);
            if(!value || !*value)
            {
                return def;
            }
            const char* p = value;
            float       v = 0;
            if(!next_number(p, v))
            {
                return def;
            }
            return *p == '%' ? v * base / 100 : v;
        }

        class path_builder
        {
          public:
            vector_path& path;
            float        cx = 0, cy = 0, sx = 0, sy = 0, qx = 0, qy = 0, kx = 0, ky = 0;
            char         last = 0;

            explicit path_builder(vector_path& p) :
                path(p)
            {
            }

            void add(vector_path::op_t op, std::initializer_list<float> pts)
            {
                path.ops.push_back(op);
                path.points.insert(path.points.end(), pts);
            }

            void move(float x, float y)
            {
                add(vector_path::move, {x, y});
                cx = sx = x;
                cy = sy = y;
            }

            void line(float x, float y)
            {
                add(vector_path::line, {x, y});
                cx = x;
                cy = y;
            }

            void cubic(float x1, float y1, float x2, float y2, float x, float y)
            {
                add(vector_path::cubic, {x1, y1, x2, y2, x, y});
                kx = x2;
                ky = y2;
                cx = x;
                cy = y;
            }

            void quad(float x1, float y1, float x, float y)
            {
                add(vector_path::quad, {x1, y1, x, y});
                qx = x1;
                qy = y1;
                cx = x;
                cy = y;
            }

            void close()
            {
                add(vector_path::close, {});
                cx = sx;
                cy = sy;
            }

            void arc(float rx, float ry, float rotation, int large, int sweep, float x2, float y2)
            {
                float x1 = cx, y1 = cy;
                if(rx == 0 || ry == 0 || (x1 == x2 && y1 == y2))
                {
                    line(x2, y2);
                    return;
                }
                rx          = std::fabs(rx);
                ry          = std::fabs(ry);
                float phi   = rotation * pi / 180;
                float cphi  = std::cos(phi), sphi = std::sin(phi);
                float dx2   = (x1 - x2) / 2, dy2 = (y1 - y2) / 2;
                float x1p   = cphi * dx2 + sphi * dy2;
                float y1p   = -sphi * dx2 + cphi * dy2;
                float lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
                if(lambda > 1)
                {
                    rx *= std::sqrt(lambda);
                    ry *= std::sqrt(lambda);
                }
                float num  = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
                float den  = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
                float coef = den > 0 ? std::sqrt(std::max(0.f, num / den)) : 0;
                if(large == sweep)
                {
                    coef = -coef;
                }
                float cxp    = coef * rx * y1p / ry;
                float cyp    = -coef * ry * x1p / rx;
                float ccx    = cphi * cxp - sphi * cyp + (x1 + x2) / 2;
                float ccy    = sphi * cxp + cphi * cyp + (y1 + y2) / 2;
                auto  angle  = [](float ux, float uy, float vx, float vy) {
                    return std::atan2(ux * vy - uy * vx, ux * vx + uy * vy);
                };
                float theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
                float dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
                if(!sweep && dtheta > 0)
                {
                    dtheta -= 2 * pi;
                } else if(sweep && dtheta < 0)
                {
                    dtheta += 2 * pi;
                }
                int   segments = std::max(1, static_cast<int>(std::ceil(std::fabs(dtheta) / (pi / 2) - 0.001f)));
                float delta    = dtheta / static_cast<float>(segments);
                float t        = 4.f / 3.f * std::tan(delta / 4);
                auto  point    = [&](float a, float& px, float& py) {
                    px = ccx + rx * std::cos(a) * cphi - ry * std::sin(a) * sphi;
                    py = ccy + rx * std::cos(a) * sphi + ry * std::sin(a) * cphi;
                };
                auto derivative = [&](float a, float& dx, float& dy) {
                    dx = -rx * std::sin(a) * cphi - ry * std::cos(a) * sphi;
                    dy = -rx * std::sin(a) * sphi + ry * std::cos(a) * cphi;
                };
                for(int i = 0; i < segments; i++)
                {
                    float a1 = theta1 + delta * static_cast<float>(i), a2 = a1 + delta;
                    float p1x, p1y, p2x, p2y, d1x, d1y, d2x, d2y;
                    point(a1, p1x, p1y);
                    point(a2, p2x, p2y);
                    derivative(a1, d1x, d1y);
                    derivative(a2, d2x, d2y);
                    if(i == segments - 1)
                    {
                        p2x = x2;
                        p2y = y2;
                    }
                    cubic(p1x + t * d1x, p1y + t * d1y, p2x - t * d2x, p2y - t * d2y, p2x, p2y);
                }
            }

            void ellipse(float ex, float ey, float rx, float ry)
            {
                const float k = 0.5522847498f;
                move(ex + rx, ey);
                cubic(ex + rx, ey + ry * k, ex + rx * k, ey + ry, ex, ey + ry);
                cubic(ex - rx * k, ey + ry, ex - rx, ey + ry * k, ex - rx, ey);
                cubic(ex - rx, ey - ry * k, ex - rx * k, ey - ry, ex, ey - ry);
                cubic(ex + rx * k, ey - ry, ex + rx, ey - ry * k, ex + rx, ey);
                close();
            }

            void rect(float x, float y, float w, float h, float rx, float ry)
            {
                rx = std::min(rx, w / 2);
                ry = std::min(ry, h / 2);
                if(rx <= 0 || ry <= 0)
                {
                    move(x, y);
                    line(x + w, y);
                    line(x + w, y + h);
                    line(x, y + h);
                    close();
                    return;
                }
                const float k = 0.5522847498f;
                move(x + rx, y);
                line(x + w - rx, y);
                cubic(x + w - rx + rx * k, y, x + w, y + ry - ry * k, x + w, y + ry);
                line(x + w, y + h - ry);
                cubic(x + w, y + h - ry + ry * k, x + w - rx + rx * k, y + h, x + w - rx, y + h);
                line(x + rx, y + h);
                cubic(x + rx - rx * k, y + h, x, y + h - ry + ry * k, x, y + h - ry);
                line(x, y + ry);
                cubic(x, y + ry - ry * k, x + rx - rx * k, y, x + rx, y);
                close();
            }

            void parse(const char* d)
            {
                char        cmd = 0;
                const char* s   = d;
                while(s && *s)
                {
                    skip_separators(s);
                    if(!*s)
                    {
                        break;
                    }
                    if(std::isalpha(static_cast<unsigned char>(*s)))
                    {
                        cmd = *s++;
                    } else if(!cmd)
                    {
                        return;
                    }
                    bool  rel = std::islower(static_cast<unsigned char>(cmd)) != 0;
                    float ox = rel ? cx : 0, oy = rel ? cy : 0;
                    float v[7];
                    auto  read = [&](int n) {
                        for(int i = 0; i < n; i++)
                        {
                            if(!next_number(s, v[i]))
                            {
                                return false;
                            }
                        }
                        return true;
                    };
                    char upper = static_cast<char>(std::toupper(static_cast<unsigned char>(cmd)));
                    switch(upper)
                    {
                    case 'M':
                        if(!read(2))
                        {
                            return;
                        }
                        move(ox + v[0], oy + v[1]);
                        cmd = rel ? 'l' : 'L';
                        break;
                    case 'L':
                        if(!read(2))
                        {
                            return;
                        }
                        line(ox + v[0], oy + v[1]);
                        break;
                    case 'H':
                        if(!read(1))
                        {
                            return;
                        }
                        line(ox + v[0], cy);
                        break;
                    case 'V':
                        if(!read(1))
                        {
                            return;
                        }
                        line(cx, oy + v[0]);
                        break;
                    case 'C':
                        if(!read(6))
                        {
                            return;
                        }
                        cubic(ox + v[0], oy + v[1], ox + v[2], oy + v[3], ox + v[4], oy + v[5]);
                        break;
                    case 'S':
                        {
                            if(!read(4))
                            {
                                return;
                            }
                            bool  smooth = last == 'C' || last == 'S';
                            float x1     = smooth ? 2 * cx - kx : cx;
                            float y1     = smooth ? 2 * cy - ky : cy;
                            cubic(x1, y1, ox + v[0], oy + v[1], ox + v[2], oy + v[3]);
                        }
                        break;
                    case 'Q':
                        if(!read(4))
                        {
                            return;
                        }
                        quad(ox + v[0], oy + v[1], ox + v[2], oy + v[3]);
                        break;
                    case 'T':
                        {
                            if(!read(2))
                            {
                                return;
                            }
                            bool  smooth = last == 'Q' || last == 'T';
                            float x1     = smooth ? 2 * cx - qx : cx;
                            float y1     = smooth ? 2 * cy - qy : cy;
                            quad(x1, y1, ox + v[0], oy + v[1]);
                        }
                        break;
                    case 'A':
                        {
                            int large = 0, sweep = 0;
                            if(!next_number(s, v[0]) || !next_number(s, v[1]) || !next_number(s, v[2]) ||
                               !next_flag(s, large) || !next_flag(s, sweep) || !next_number(s, v[3]) ||
                               !next_number(s, v[4]))
                            {
                                return;
                            }
                            arc(v[0], v[1], v[2], large, sweep, ox + v[3], oy + v[4]);
                        }
                        break;
                    case 'Z':
                        close();
                        break;
                    default:
                        return;
                    }
                    last = upper;
                }
            }
        };

        static litehtml::transform_matrix svg_css_transform(const element::ptr& el, const litehtml::position& border_box)
        {
            using namespace litehtml;
            const auto& el_css = el->css();
            const auto tag = std::dynamic_pointer_cast<html_tag>(el);
            const bool has_origin = tag && tag->has_own_property(_transform_origin_);
            // transform-box: fill-box: the shape's own box in place of the view box, and its corner the default origin.
            position box = border_box;
            bool     fill_box = false;
            vector_path geometry;
            if(el_css.get_transform_fill_box() && svg_geometry(el, geometry) && geometry.points.size() >= 2)
            {
                float x0 = geometry.points[0], y0 = geometry.points[1], x1 = x0, y1 = y0;
                for(size_t i = 0; i + 1 < geometry.points.size(); i += 2)
                {
                    x0 = std::min(x0, geometry.points[i]);
                    x1 = std::max(x1, geometry.points[i]);
                    y0 = std::min(y0, geometry.points[i + 1]);
                    y1 = std::max(y1, geometry.points[i + 1]);
                }
                box      = position(pixel_t(x0), pixel_t(y0), pixel_t(x1 - x0), pixel_t(y1 - y0));
                fill_box = true;
            }
            pixel_t ox = has_origin ? box.x + el_css.get_transform_origin_x().calc_percent(box.width) : fill_box ? box.x : 0_px;
            pixel_t oy = has_origin ? box.y + el_css.get_transform_origin_y().calc_percent(box.height) : fill_box ? box.y : 0_px;
            transform_matrix m{1, 0, 0, 1, -ox.value(), -oy.value()};
            const auto&      functions = el_css.get_transform();
            for(auto it = functions.rbegin(); it != functions.rend(); ++it)
            {
                transform_matrix f;
                switch(it->kind)
                {
                case transform_function::translate:
                    f.tx = it->x.calc_percent(box.width).value();
                    f.ty = it->y.calc_percent(box.height).value();
                    break;
                case transform_function::scale:
                    f.a = it->v[0];
                    f.d = it->v[1];
                    break;
                case transform_function::rotate:
                    f.a = std::cos(it->v[0]);
                    f.b = std::sin(it->v[0]);
                    f.c = -f.b;
                    f.d = f.a;
                    break;
                case transform_function::skew:
                    f.b = std::tan(it->v[1]);
                    f.c = std::tan(it->v[0]);
                    break;
                case transform_function::matrix:
                    f = {it->v[0], it->v[1], it->v[2], it->v[3], it->v[4], it->v[5]};
                    break;
                case transform_function::rotate_x:
                    f.d = std::cos(it->v[0]);
                    break;
                case transform_function::rotate_y:
                    f.a = std::cos(it->v[0]);
                    break;
                }
                m = m.then(f);
            }
            return m.then(transform_matrix{1, 0, 0, 1, ox.value(), oy.value()});
        }

        transform_matrix parse_svg_transform(const char* s)
        {
            transform_matrix m;
            if(!s)
            {
                return m;
            }
            std::vector<transform_matrix> list;
            while(*s)
            {
                skip_separators(s);
                const char* name = s;
                while(*s && std::isalpha(static_cast<unsigned char>(*s)))
                {
                    s++;
                }
                std::string fn(name, s);
                while(*s && *s != '(')
                {
                    s++;
                }
                if(!*s)
                {
                    break;
                }
                const char* close = std::strchr(s, ')');
                if(!close)
                {
                    break;
                }
                std::vector<float> args = numbers(std::string(s + 1, close).c_str());
                s                       = close + 1;
                transform_matrix f;
                fn = lowcase(fn);
                if(fn == "matrix" && args.size() == 6)
                {
                    f = {args[0], args[1], args[2], args[3], args[4], args[5]};
                } else if(fn == "translate" && !args.empty())
                {
                    f.tx = args[0];
                    f.ty = args.size() > 1 ? args[1] : 0;
                } else if(fn == "scale" && !args.empty())
                {
                    f.a = args[0];
                    f.d = args.size() > 1 ? args[1] : args[0];
                } else if(fn == "rotate" && !args.empty())
                {
                    float a = args[0] * pi / 180;
                    transform_matrix r{std::cos(a), std::sin(a), -std::sin(a), std::cos(a), 0, 0};
                    if(args.size() == 3)
                    {
                        f = transform_matrix{1, 0, 0, 1, -args[1], -args[2]}.then(r).then(
                            transform_matrix{1, 0, 0, 1, args[1], args[2]});
                    } else
                    {
                        f = r;
                    }
                } else if(fn == "skewx" && !args.empty())
                {
                    f.c = std::tan(args[0] * pi / 180);
                } else if(fn == "skewy" && !args.empty())
                {
                    f.b = std::tan(args[0] * pi / 180);
                }
                list.push_back(f);
            }
            for(auto it = list.rbegin(); it != list.rend(); ++it)
            {
                m = m.then(*it);
            }
            return m;
        }

        thread_local std::vector<std::pair<const element*, element::ptr>> use_hosts;

        struct use_host_scope
        {
            use_host_scope(const element::ptr& target, const element::ptr& use) { use_hosts.emplace_back(target.get(), use); }
            ~use_host_scope() { use_hosts.pop_back(); }
        };

        element::ptr svg_parent(const element::ptr& el)
        {
            for(auto it = use_hosts.rbegin(); it != use_hosts.rend(); ++it)
            {
                if(it->first == el.get())
                {
                    return it->second;
                }
            }
            return el->parent();
        }

        css_token_vector svg_property(const element::ptr& el, string_id name, bool inherited)
        {
            for(element::ptr cur = el; cur; cur = svg_parent(cur))
            {
                if(const auto* tag = dynamic_cast<const html_tag*>(cur.get()))
                {
                    if(const auto* tokens = tag->get_token_property(name))
                    {
                        return *tokens;
                    }
                    if(const char* attr = tag->get_attr(_s(name).c_str()))
                    {
                        css_token_vector tokens = normalize(attr, f_componentize | f_remove_whitespace);
                        if(has_var(tokens))
                        {
                            subst_vars_(name, tokens, tag);
                            if(has_var(tokens))
                            {
                                return {};
                            }
                        }
                        if(has_foldable_math(tokens))
                        {
                            fold_math(tokens, tag->get_document()->container());
                        }
                        return tokens;
                    }
                }
                if(!inherited)
                {
                    break;
                }
            }
            return {};
        }

        bool svg_paint(const element::ptr& el, string_id name, web_color def, bool def_on, web_color& out,
                       document_container* container, bool* gradient = nullptr)
        {
            if(gradient) *gradient = false;
            css_token_vector tokens = svg_property(el, name, true);
            if(tokens.empty())
            {
                out = def;
                return def_on;
            }
            for(const auto& tok : tokens)
            {
                std::string ident = tok.ident();
                if(ident == "none")
                {
                    return false;
                }
                if(ident == "currentcolor")
                {
                    out = el->css().get_color();
                    return true;
                }
                if(tok.type == URL || (tok.type == CV_FUNCTION && lowcase(tok.name()) == "url"))
                {
                    if(gradient) *gradient = true;
                    continue;
                }
                web_color color;
                if(tok.type != URL && tok.type != CV_FUNCTION && parse_color(tok, color, container))
                {
                    out = color;
                    return true;
                }
                if(tok.type == CV_FUNCTION && lowcase(tok.name()) != "url" && parse_color(tok, color, container))
                {
                    out = color;
                    return true;
                }
            }
            return false;
        }

        float svg_number(const element::ptr& el, string_id name, float def, bool inherited = true)
        {
            css_token_vector tokens = svg_property(el, name, inherited);
            if(tokens.empty())
            {
                return def;
            }
            const auto& tok = tokens[0];
            if(tok.type == NUMBER || tok.type == DIMENSION)
            {
                return tok.n.number;
            }
            if(tok.type == PERCENTAGE)
            {
                return tok.n.number / 100;
            }
            return def;
        }

        std::string svg_ident(const element::ptr& el, string_id name)
        {
            css_token_vector tokens = svg_property(el, name, true);
            return tokens.empty() ? std::string() : tokens[0].ident();
        }

        web_color with_opacity(web_color color, float opacity)
        {
            opacity     = std::max(0.f, std::min(1.f, opacity));
            color.alpha = static_cast<byte>(std::lround(color.alpha * opacity));
            return color;
        }

        std::string svg_url_id(const css_token_vector& tokens)
        {
            for(const auto& tok : tokens)
            {
                std::string url;
                if(tok.type == URL)
                    url = tok.str();
                else if(tok.type == CV_FUNCTION && lowcase(tok.name()) == "url" && !tok.value.empty())
                    url = tok.value[0].str();
                if(url.size() > 1 && url[0] == '#')
                    return url.substr(1);
            }
            return {};
        }

        float gradient_coordinate(const element::ptr& el, const char* name, float def)
        {
            const char* value = el->get_attr(name);
            if(!value || !*value)
                return def;
            const char* p = value;
            float       v = 0;
            if(!next_number(p, v))
                return def;
            return *p == '%' ? v / 100 : v;
        }

        bool svg_gradient_paint(const element::ptr& el, string_id name, const vector_path& path, svg_gradient& out,
                                float opacity, document_container* container)
        {
            std::string id = svg_url_id(svg_property(el, name, true));
            if(id.empty() || id.size() > 128)
                return false;
            for(char ch : id)
            {
                if(!std::isalnum(static_cast<unsigned char>(ch)) && ch != '-' && ch != '_')
                    return false;
            }
            auto doc = el->get_document();
            if(!doc || !doc->root())
                return false;
            element::ptr target = doc->root()->select_one("#" + id);
            if(!target)
                return false;
            std::string tag = lowcase(target->get_tagName());
            bool        linear = tag == "lineargradient";
            if(!linear && tag != "radialgradient")
                return false;
            float minx = 0, miny = 0, maxx = 0, maxy = 0;
            for(size_t i = 0; i + 1 < path.points.size(); i += 2)
            {
                float x = path.points[i], y = path.points[i + 1];
                if(i == 0)
                {
                    minx = maxx = x;
                    miny = maxy = y;
                } else
                {
                    minx = std::min(minx, x);
                    maxx = std::max(maxx, x);
                    miny = std::min(miny, y);
                    maxy = std::max(maxy, y);
                }
            }
            const char* units = target->get_attr("gradientunits");
            bool        user  = units && std::string(units) == "userSpaceOnUse";
            float       w = maxx - minx, h = maxy - miny;
            auto        mapx = [&](float v) { return user ? v : minx + v * w; };
            auto        mapy = [&](float v) { return user ? v : miny + v * h; };
            svg_gradient g;
            if(linear)
            {
                g.type = 1;
                g.x1   = mapx(gradient_coordinate(target, "x1", 0));
                g.y1   = mapy(gradient_coordinate(target, "y1", 0));
                g.x2   = mapx(gradient_coordinate(target, "x2", 1));
                g.y2   = mapy(gradient_coordinate(target, "y2", 0));
            } else
            {
                g.type   = 2;
                float cx = gradient_coordinate(target, "cx", 0.5f), cy = gradient_coordinate(target, "cy", 0.5f);
                g.x1     = mapx(cx);
                g.y1     = mapy(cy);
                g.fx     = mapx(gradient_coordinate(target, "fx", cx));
                g.fy     = mapy(gradient_coordinate(target, "fy", cy));
                float r  = gradient_coordinate(target, "r", 0.5f);
                g.r      = user ? r : r * std::sqrt((w * w + h * h) / 2);
            }
            for(const auto& child : target->children())
            {
                if(lowcase(child->get_tagName()) != "stop")
                    continue;
                float     offset = std::max(0.f, std::min(1.f, gradient_coordinate(child, "offset", 0)));
                web_color color  = web_color::black;
                css_token_vector tokens = svg_property(child, _id("stop-color"), false);
                web_color parsed;
                for(const auto& tok : tokens)
                {
                    if(parse_color(tok, parsed, container))
                    {
                        color = parsed;
                        break;
                    }
                }
                color = with_opacity(color, svg_number(child, _id("stop-opacity"), 1, false) * opacity);
                if(!g.stops.empty())
                    offset = std::max(offset, g.stops.back().first);
                g.stops.emplace_back(offset, color);
            }
            if(g.stops.empty())
                return false;
            out = std::move(g);
            return true;
        }

        bool svg_group_key(const element::ptr& root, uint64_t& key)
        {
            uint64_t hash = 0x67726f7570ULL;
            int count = 0;
            auto include = [&](const element::ptr& el) {
                for(const char* attribute : {"fill", "stroke", "style", "mask", "clip-path", "filter", "marker-start", "marker-mid", "marker-end"})
                {
                    if(const char* value = el->get_attr(attribute); value && lowcase(value).find("url(") != std::string::npos)
                        return false;
                }
                if(const auto tag = dynamic_cast<const html_tag*>(el.get()))
                {
                    for(const auto name : {_fill_, _stroke_})
                    {
                        if(const auto tokens = tag->get_token_property(name); tokens && (has_var(*tokens) || !svg_url_id(*tokens).empty()))
                            return false;
                    }
                }
                for(const char* attribute : {"fill", "stroke"})
                    if(const char* value = el->get_attr(attribute); value && lowcase(value).find("var(") != std::string::npos)
                        return false;
                hash = (hash ^ el->paint_signature()) * 1099511628211ULL;
                return true;
            };
            std::function<bool(const element::ptr&)> visit = [&](const element::ptr& el) {
                if(++count > 4000 || !include(el))
                    return false;
                const std::string name = lowcase(el->get_tagName() ? el->get_tagName() : "");
                if(name == "use" || name == "image" || name == "foreignobject" || name == "canvas" || name == "img")
                    return false;
                for(const auto& child : el->children())
                    if(!visit(child)) return false;
                return true;
            };
            if(!visit(root) || count < 8)
                return false;
            for(auto parent = root->parent(); parent; parent = parent->parent())
                if(!include(parent)) return false;
            key = hash;
            return true;
        }

        class svg_painter
        {
            document_container* m_container;
            uint_ptr            m_hdc;
            float               m_view[4];
            bool                m_hit_mode = false;
            float               m_px       = 0;
            float               m_py       = 0;
            transform_matrix    m_matrix;
            element::ptr        m_hit;
            bool                m_cache_groups = false;
            position            m_cache_bounds;

            bool hits(float x0, float y0, float x1, float y1, float slop) const
            {
                float min_x = 1e30f, min_y = 1e30f, max_x = -1e30f, max_y = -1e30f;
                for(float x : {x0, x1})
                {
                    for(float y : {y0, y1})
                    {
                        const float tx = m_matrix.a * x + m_matrix.c * y + m_matrix.tx;
                        const float ty = m_matrix.b * x + m_matrix.d * y + m_matrix.ty;
                        min_x          = std::min(min_x, tx);
                        min_y          = std::min(min_y, ty);
                        max_x          = std::max(max_x, tx);
                        max_y          = std::max(max_y, ty);
                    }
                }
                return m_px >= min_x - slop && m_px <= max_x + slop && m_py >= min_y - slop && m_py <= max_y + slop;
            }

            void hit_path(const element::ptr& el, const vector_path& path)
            {
                if(path.points.size() < 2)
                {
                    return;
                }
                float min_x = path.points[0], min_y = path.points[1], max_x = min_x, max_y = min_y;
                for(size_t i = 0; i + 1 < path.points.size(); i += 2)
                {
                    min_x = std::min(min_x, path.points[i]);
                    max_x = std::max(max_x, path.points[i]);
                    min_y = std::min(min_y, path.points[i + 1]);
                    max_y = std::max(max_y, path.points[i + 1]);
                }
                const float scale = std::sqrt(std::abs(m_matrix.a * m_matrix.d - m_matrix.b * m_matrix.c));
                const float slop  = std::max(0.f, svg_number(el, _stroke_width_, 1)) * scale / 2;
                if(hits(min_x, min_y, max_x, max_y, slop))
                {
                    m_hit = el;
                }
            }

            void paint(const element::ptr& el, vector_path& path)
            {
                if(path.ops.empty())
                {
                    return;
                }
                if(m_hit_mode)
                {
                    hit_path(el, path);
                    return;
                }
                path.fill   = svg_paint(el, _fill_, web_color::black, true, path.fill_color, m_container);
                bool stroke_gradient = false;
                path.stroke = svg_paint(el, _stroke_, web_color::black, false, path.stroke_color, m_container, &stroke_gradient);
                path.fill_color   = with_opacity(path.fill_color, svg_number(el, _fill_opacity_, 1));
                if(!path.fill && svg_gradient_paint(el, _fill_, path, path.fill_gradient, svg_number(el, _fill_opacity_, 1), m_container))
                {
                    path.fill       = true;
                    path.fill_color = path.fill_gradient.stops.back().second;
                }
                path.stroke_color = with_opacity(path.stroke_color, svg_number(el, _stroke_opacity_, 1));
                if(!path.stroke && stroke_gradient && svg_gradient_paint(el, _stroke_, path, path.stroke_gradient, svg_number(el, _stroke_opacity_, 1), m_container))
                {
                    path.stroke       = true;
                    path.stroke_color = path.stroke_gradient.stops.back().second;
                }
                path.evenodd      = svg_ident(el, _fill_rule_) == "evenodd";
                path.stroke_width = std::max(0.f, svg_number(el, _stroke_width_, 1));
                std::string cap   = svg_ident(el, _stroke_linecap_);
                path.line_cap     = cap == "round" ? 1 : cap == "square" ? 2 : 0;
                std::string join  = svg_ident(el, _stroke_linejoin_);
                path.line_join    = join == "round" ? 1 : join == "bevel" ? 2 : 0;
                path.miter_limit  = svg_number(el, _stroke_miterlimit_, 4);
                path.dash_offset  = svg_number(el, _stroke_dashoffset_, 0);
                css_token_vector dashes = svg_property(el, _stroke_dasharray_, true);
                for(const auto& tok : dashes)
                {
                    if(tok.type == NUMBER || tok.type == DIMENSION)
                    {
                        path.dashes.push_back(std::max(0.f, tok.n.number));
                    }
                }
                if(path.dashes.size() % 2 == 1)
                {
                    auto copy = path.dashes;
                    path.dashes.insert(path.dashes.end(), copy.begin(), copy.end());
                }
                if(path.stroke_width == 0)
                {
                    path.stroke = false;
                }
                if(path.fill || path.stroke)
                {
                    m_container->draw_path(m_hdc, path);
                }
            }

            void draw_text(const element::ptr& el)
            {
                position                  box;
                std::vector<svg_text_run> runs;
                if(!svg_text_runs(el, m_container, runs, box))
                {
                    return;
                }
                for(const auto& run : runs)
                {
                    const position& pos = run.box;
                    if(m_hit_mode)
                    {
                        if(hits(pos.left().value(), pos.top().value(), pos.right().value(), pos.bottom().value(), 0))
                        {
                            m_hit = el;
                        }
                        continue;
                    }
                    web_color color;
                    if(!svg_paint(run.style, _fill_, web_color::black, true, color, m_container))
                    {
                        continue;
                    }
                    color = with_opacity(color, svg_number(run.style, _fill_opacity_, 1));
                    m_container->draw_text(m_hdc, run.text.c_str(), run.style->css().get_font(), color, pos);
                }
            }

          public:
            svg_painter(document_container* container, uint_ptr hdc, const float view[4], const position* cache_bounds = nullptr) :
                m_container(container),
                m_hdc(hdc)
            {
                std::copy(view, view + 4, m_view);
                if(cache_bounds)
                {
                    m_cache_groups = svg_group_cache_enabled.load(std::memory_order_relaxed);
                    m_cache_bounds = *cache_bounds;
                }
            }

            svg_painter(document_container* container, const float view[4], const transform_matrix& matrix, float x, float y) :
                m_container(container),
                m_hdc(0),
                m_hit_mode(true),
                m_px(x),
                m_py(y),
                m_matrix(matrix)
            {
                std::copy(view, view + 4, m_view);
            }

            element::ptr hit() const
            {
                return m_hit;
            }

            static int attribute_visibility(const element::ptr& el)
            {
                const char* value = el->get_attr("visibility");
                if(!value)
                {
                    return -1;
                }
                std::string v = lowcase(value);
                trim(v);
                return v == "hidden" || v == "collapse" ? 1 : v == "visible" ? 0 : -1;
            }

            static bool attribute_hides(const element::ptr& el)
            {
                const char* value = el->get_attr("display");
                if(!value)
                {
                    return false;
                }
                std::string v = lowcase(value);
                trim(v);
                return v == "none";
            }

            void draw_children(const element::ptr& el, int inherited = -1, int depth = 0)
            {
                for(const auto& child : el->children())
                {
                    draw_child(child, inherited, depth);
                }
            }

            // <use>: the element it names, moved to x, y; a <symbol> has its view box fitted into width × height.
            void draw_use(const element::ptr& use, int visibility, int depth)
            {
                const char* href = use->get_attr("href");
                if(!href)
                    href = use->get_attr("xlink:href");
                if(!href || href[0] != '#' || !href[1] || depth > 8)
                    return;
                element::ptr target;
                if(auto root = use->get_document()->root())
                    target = root->select_one(std::string("#") + (href + 1));
                if(!target)
                    return;
                for(element::ptr p = use; p; p = p->parent())
                {
                    if(p == target)
                        return;
                }
                transform_matrix m{1, 0, 0, 1, attr_length(use, "x", m_view[2], 0), attr_length(use, "y", m_view[3], 0)};
                const bool symbol = lowcase(target->get_tagName() ? target->get_tagName() : "") == "symbol";
                std::vector<float> box = numbers(target->get_attr("viewbox"));
                if(symbol && box.size() == 4 && box[2] > 0 && box[3] > 0)
                {
                    const float w = attr_length(use, "width", m_view[2], m_view[2]), h = attr_length(use, "height", m_view[3], m_view[3]);
                    const float s = std::min(w / box[2], h / box[3]);
                    m = transform_matrix{s, 0, 0, s, -box[0] * s + (w - box[2] * s) / 2, -box[1] * s + (h - box[3] * s) / 2}.then(m);
                }
                const transform_matrix saved = m_matrix;
                if(m_hit_mode)
                {
                    m_matrix = m.then(m_matrix);
                } else
                {
                    paint_group group;
                    group.matrix = m;
                    group.bounds = position(-100000, -100000, 200000, 200000);
                    m_container->begin_group(m_hdc, group);
                }
                {
                    use_host_scope host(target, use);
                    if(symbol)
                        draw_children(target, visibility, depth + 1);
                    else
                        draw_child(target, visibility, depth + 1);
                }
                if(!m_hit_mode)
                    m_container->end_group(m_hdc);
                m_matrix = saved;
            }

            void draw_child(const element::ptr& child, int inherited, int depth)
            {
                const float diag = std::sqrt((m_view[2] * m_view[2] + m_view[3] * m_view[3]) / 2);
                {
                    if(child->is_text() || child->css().get_display() == display_none || attribute_hides(child))
                    {
                        return;
                    }
                    int visibility = attribute_visibility(child);
                    if(visibility < 0)
                    {
                        visibility = inherited;
                    }
                    const bool hidden = visibility >= 0 ? visibility == 1 : child->css().get_visibility() != visibility_visible;
                    std::string name = lowcase(child->get_tagName() ? child->get_tagName() : "");
                    if(is_one_of(name, "defs", "style", "title", "desc", "lineargradient", "radialgradient",
                                 "clippath", "mask", "pattern", "symbol", "marker", "filter", "script", "metadata"))
                    {
                        return;
                    }
                    float opacity = child->css().get_opacity();
                    if(const char* attr = child->get_attr("opacity"))
                    {
                        opacity *= std::max(0.f, std::min(1.f, std::strtof(attr, nullptr)));
                    }
                    paint_group group;
                    const auto tag = std::dynamic_pointer_cast<html_tag>(child);
                    group.matrix = tag && tag->has_own_property(_transform_)
                        ? svg_css_transform(child, position(pixel_t(m_view[0]), pixel_t(m_view[1]), pixel_t(m_view[2]), pixel_t(m_view[3])))
                        : parse_svg_transform(child->get_attr("transform"));
                    group.opacity = opacity;
                    group.bounds  = position(-100000, -100000, 200000, 200000);
                    bool grouped  = opacity < 1 || !group.matrix.is_identity();
                    const transform_matrix saved = m_matrix;
                    if(m_hit_mode)
                    {
                        m_matrix = group.matrix.then(m_matrix);
                        grouped  = false;
                    }
                    if(grouped)
                    {
                        m_container->begin_group(m_hdc, group);
                    }
                    vector_path  path;
                    path_builder builder(path);
                    if(name == "g" || name == "a" || name == "svg" || name == "switch")
                    {
                        bool cached = false;
                        uint64_t key = 0;
                        if(m_cache_groups && depth == 0 && name == "g" && !grouped && svg_group_key(child, key))
                        {
                            for(float value : {m_view[0], m_view[1], m_view[2], m_view[3], m_cache_bounds.x.value(), m_cache_bounds.y.value(), m_cache_bounds.width.value(), m_cache_bounds.height.value()})
                            {
                                uint32_t bits;
                                memcpy(&bits, &value, sizeof(bits));
                                key = (key ^ bits) * 1099511628211ULL;
                            }
                            cached = m_container->draw_cached(m_hdc, m_cache_bounds, key, [&](uint_ptr target) {
                                svg_painter(m_container, target, m_view).draw_children(child, visibility, depth + 1);
                            });
                        }
                        if(!cached) draw_children(child, visibility, depth + 1);
                    } else if(hidden)
                    {
                    } else if(name == "circle")
                    {
                        float r = attr_length(child, "r", diag, 0);
                        if(r > 0)
                        {
                            builder.ellipse(attr_length(child, "cx", m_view[2], 0), attr_length(child, "cy", m_view[3], 0), r, r);
                        }
                        paint(child, path);
                    } else if(name == "ellipse")
                    {
                        float rx = attr_length(child, "rx", m_view[2], 0), ry = attr_length(child, "ry", m_view[3], 0);
                        if(rx > 0 && ry > 0)
                        {
                            builder.ellipse(attr_length(child, "cx", m_view[2], 0), attr_length(child, "cy", m_view[3], 0), rx, ry);
                        }
                        paint(child, path);
                    } else if(name == "rect")
                    {
                        float w  = attr_length(child, "width", m_view[2], 0);
                        float h  = attr_length(child, "height", m_view[3], 0);
                        float rx = attr_length(child, "rx", m_view[2], -1);
                        float ry = attr_length(child, "ry", m_view[3], -1);
                        if(rx < 0)
                        {
                            rx = ry < 0 ? 0 : ry;
                        }
                        if(ry < 0)
                        {
                            ry = rx;
                        }
                        if(w > 0 && h > 0)
                        {
                            builder.rect(attr_length(child, "x", m_view[2], 0), attr_length(child, "y", m_view[3], 0), w, h, rx, ry);
                        }
                        paint(child, path);
                    } else if(name == "line")
                    {
                        builder.move(attr_length(child, "x1", m_view[2], 0), attr_length(child, "y1", m_view[3], 0));
                        builder.line(attr_length(child, "x2", m_view[2], 0), attr_length(child, "y2", m_view[3], 0));
                        paint(child, path);
                    } else if(name == "polyline" || name == "polygon")
                    {
                        std::vector<float> pts = numbers(child->get_attr("points", ""));
                        for(size_t i = 0; i + 1 < pts.size(); i += 2)
                        {
                            if(i == 0)
                            {
                                builder.move(pts[i], pts[i + 1]);
                            } else
                            {
                                builder.line(pts[i], pts[i + 1]);
                            }
                        }
                        if(name == "polygon" && pts.size() >= 4)
                        {
                            builder.close();
                        }
                        paint(child, path);
                    } else if(name == "path")
                    {
                        builder.parse(child->get_attr("d", ""));
                        paint(child, path);
                    } else if(name == "text")
                    {
                        draw_text(child);
                    } else if(name == "use")
                    {
                        draw_use(child, visibility, depth);
                    }
                    if(grouped)
                    {
                        m_container->end_group(m_hdc);
                    }
                    m_matrix = saved;
                }
            }
        };
    } // namespace

    bool svg_paint_color(const element::ptr& el, bool fill, web_color& out, document_container* container)
    {
        return svg_paint(el, fill ? _fill_ : _stroke_, web_color::black, fill, out, container);
    }

    bool svg_geometry(const element::ptr& el, vector_path& path)
    {
        if(!el || !el->get_tagName())
        {
            return false;
        }
        const std::string name = lowcase(el->get_tagName());
        float             vw = 0, vh = 0;
        for(element::ptr p = el->parent(); p; p = p->parent())
        {
            if(const auto* svg = dynamic_cast<const el_svg*>(p.get()))
            {
                float box[4];
                if(svg->view_box(box))
                {
                    vw = box[2];
                    vh = box[3];
                } else
                {
                    vw = numbers(svg->get_attr("width")).empty() ? 0 : numbers(svg->get_attr("width"))[0];
                    vh = numbers(svg->get_attr("height")).empty() ? 0 : numbers(svg->get_attr("height"))[0];
                }
                break;
            }
        }
        const float  diag = std::sqrt((vw * vw + vh * vh) / 2);
        path_builder builder(path);
        if(name == "circle")
        {
            float r = attr_length(el, "r", diag, 0);
            if(r > 0)
                builder.ellipse(attr_length(el, "cx", vw, 0), attr_length(el, "cy", vh, 0), r, r);
        } else if(name == "ellipse")
        {
            float rx = attr_length(el, "rx", vw, 0), ry = attr_length(el, "ry", vh, 0);
            if(rx > 0 && ry > 0)
                builder.ellipse(attr_length(el, "cx", vw, 0), attr_length(el, "cy", vh, 0), rx, ry);
        } else if(name == "rect")
        {
            float w = attr_length(el, "width", vw, 0), h = attr_length(el, "height", vh, 0);
            float rx = attr_length(el, "rx", vw, -1), ry = attr_length(el, "ry", vh, -1);
            if(rx < 0)
                rx = ry < 0 ? 0 : ry;
            if(ry < 0)
                ry = rx;
            if(w > 0 && h > 0)
                builder.rect(attr_length(el, "x", vw, 0), attr_length(el, "y", vh, 0), w, h, rx, ry);
        } else if(name == "line")
        {
            builder.move(attr_length(el, "x1", vw, 0), attr_length(el, "y1", vh, 0));
            builder.line(attr_length(el, "x2", vw, 0), attr_length(el, "y2", vh, 0));
        } else if(name == "polyline" || name == "polygon")
        {
            std::vector<float> pts = numbers(el->get_attr("points", ""));
            for(size_t i = 0; i + 1 < pts.size(); i += 2)
            {
                if(i == 0)
                    builder.move(pts[i], pts[i + 1]);
                else
                    builder.line(pts[i], pts[i + 1]);
            }
            if(name == "polygon" && pts.size() >= 4)
                builder.close();
        } else if(name == "path")
        {
            builder.parse(el->get_attr("d", ""));
        } else
        {
            return false;
        }
        return !path.ops.empty();
    }

    bool svg_text_runs(const element::ptr& el, document_container* container, std::vector<svg_text_run>& runs, position& box)
    {
        struct pending
        {
            element::ptr style;
            std::string  text;
            const char*  x  = nullptr;
            const char*  y  = nullptr;
            const char*  dx = nullptr;
            const char*  dy = nullptr;
        };
        std::vector<pending> parts;
        bool                 space = false, any = false;
        // The text's own characters and each <tspan>'s, whitespace collapsed across them as one text.
        std::function<void(const element::ptr&, const element::ptr&)> collect = [&](const element::ptr& node, const element::ptr& style) {
            for(const auto& child : node->children())
            {
                if(child->is_text())
                {
                    std::string text;
                    child->get_text(text);
                    if(parts.empty() || parts.back().style != style)
                        parts.push_back({style, {}});
                    for(char ch : text)
                    {
                        if(std::isspace(static_cast<unsigned char>(ch)))
                        {
                            space = any;
                            continue;
                        }
                        if(space)
                            parts.back().text += ' ';
                        space = false;
                        any   = true;
                        parts.back().text += ch;
                    }
                } else if(lowcase(child->get_tagName() ? child->get_tagName() : "") == "tspan" && child->css().get_display() != display_none)
                {
                    parts.push_back({child, {}, child->get_attr("x"), child->get_attr("y"), child->get_attr("dx"), child->get_attr("dy")});
                    collect(child, child);
                }
            }
        };
        parts.push_back({el, {}, el->get_attr("x"), el->get_attr("y"), el->get_attr("dx"), el->get_attr("dy")});
        collect(el, el);
        auto first = [](const char* value) {
            std::vector<float> v = numbers(value);
            return v.empty() ? 0.f : v[0];
        };
        auto shift = [](const char* value, float em) -> float {
            if(!value || !*value)
                return 0;
            const char* p = value;
            float       v = 0;
            if(!next_number(p, v))
                return 0;
            return (p[0] == 'e' && p[1] == 'm') ? v * em : v;
        };
        runs.clear();
        float x = 0, y = 0, start = 0;
        for(const auto& part : parts)
        {
            const float em = part.style->css().get_font_size().value();
            if(part.x)
                x = first(part.x);
            if(part.y)
                y = first(part.y);
            x += shift(part.dx, em);
            y += shift(part.dy, em);
            if(&part == &parts.front())
                start = x;
            uint_ptr font = part.style->css().get_font();
            if(part.text.empty() || !font)
                continue;
            const auto& fm    = part.style->css().get_font_metrics();
            pixel_t     width = container->text_width(part.text.c_str(), font);
            runs.push_back({part.style, part.text, position(pixel_t(x), pixel_t(y) - fm.ascent, width, fm.height)});
            x += width.value();
        }
        if(runs.empty())
        {
            return false;
        }
        std::string anchor = svg_ident(el, _text_anchor_);
        const float total  = x - start;
        const float offset = anchor == "middle" ? -total / 2 : anchor == "end" ? -total : 0;
        float       x0 = INFINITY, y0 = INFINITY, x1 = -INFINITY, y1 = -INFINITY;
        for(auto& run : runs)
        {
            run.box.x += pixel_t(offset);
            x0 = std::min(x0, run.box.x.value());
            y0 = std::min(y0, run.box.y.value());
            x1 = std::max(x1, run.box.right().value());
            y1 = std::max(y1, run.box.bottom().value());
        }
        box = position(pixel_t(x0), pixel_t(y0), pixel_t(x1 - x0), pixel_t(y1 - y0));
        return true;
    }

    bool svg_text_box(const element::ptr& el, document_container* container, position& box)
    {
        std::vector<svg_text_run> runs;
        return svg_text_runs(el, container, runs, box);
    }

    bool svg_ctm(const element::ptr& el, transform_matrix& ctm)
    {
        std::vector<element::ptr> chain;
        const el_svg*             svg = nullptr;
        for(element::ptr p = el; p && !svg; p = p->parent())
        {
            svg = dynamic_cast<const el_svg*>(p.get());
            if(!svg)
                chain.push_back(p);
        }
        if(!svg || chain.empty())
        {
            return false;
        }
        paint_group group;
        float       view[4];
        svg->view_group(svg->get_placement(), view, group);
        // As the painter composes them: an element's own transform first, then each parent's, then the view's.
        const position   view_box = position(pixel_t(view[0]), pixel_t(view[1]), pixel_t(view[2]), pixel_t(view[3]));
        transform_matrix m;
        for(const auto& p : chain)
        {
            const auto tag = std::dynamic_pointer_cast<html_tag>(p);
            m = m.then(tag && tag->has_own_property(_transform_) ? svg_css_transform(p, view_box) : parse_svg_transform(p->get_attr("transform")));
        }
        ctm = m.then(group.matrix);
        return true;
    }

    float svg_path_length(const vector_path& path, float at, float* outx, float* outy)
    {
        float  total = 0, cx = 0, cy = 0, sx = 0, sy = 0;
        bool   found = false;
        size_t k     = 0;
        auto   seg   = [&](float x0, float y0, float x1, float y1) {
            float d = std::hypot(x1 - x0, y1 - y0);
            if(!found && at >= 0 && total + d >= at)
            {
                float t = d > 0 ? (at - total) / d : 0;
                if(outx)
                    *outx = x0 + (x1 - x0) * t;
                if(outy)
                    *outy = y0 + (y1 - y0) * t;
                found = true;
            }
            total += d;
        };
        for(auto op : path.ops)
        {
            switch(op)
            {
            case vector_path::move:
                cx = sx = path.points[k];
                cy = sy = path.points[k + 1];
                k += 2;
                if(!found && at >= 0 && total >= at)
                {
                    if(outx)
                        *outx = cx;
                    if(outy)
                        *outy = cy;
                    found = true;
                }
                break;
            case vector_path::line:
                seg(cx, cy, path.points[k], path.points[k + 1]);
                cx = path.points[k];
                cy = path.points[k + 1];
                k += 2;
                break;
            case vector_path::quad:
            case vector_path::cubic:
                {
                    const bool cubic = op == vector_path::cubic;
                    const float* p   = &path.points[k];
                    float        px = cx, py = cy;
                    const int    steps = 64;
                    for(int i = 1; i <= steps; i++)
                    {
                        float t = float(i) / steps, u = 1 - t, x, y;
                        if(cubic)
                        {
                            x = u * u * u * cx + 3 * u * u * t * p[0] + 3 * u * t * t * p[2] + t * t * t * p[4];
                            y = u * u * u * cy + 3 * u * u * t * p[1] + 3 * u * t * t * p[3] + t * t * t * p[5];
                        } else
                        {
                            x = u * u * cx + 2 * u * t * p[0] + t * t * p[2];
                            y = u * u * cy + 2 * u * t * p[1] + t * t * p[3];
                        }
                        seg(px, py, x, y);
                        px = x;
                        py = y;
                    }
                    cx = cubic ? p[4] : p[2];
                    cy = cubic ? p[5] : p[3];
                    k += cubic ? 6 : 4;
                }
                break;
            case vector_path::close:
                seg(cx, cy, sx, sy);
                cx = sx;
                cy = sy;
                break;
            }
        }
        if(!found && at >= 0)
        {
            if(outx)
                *outx = cx;
            if(outy)
                *outy = cy;
        }
        return total;
    }

    el_svg::el_svg(const document::ptr& doc) :
        html_tag(doc)
    {
        if(doc)
            doc->note_svg();
        m_css.set_display(display_inline_block);
    }

    bool el_svg::is_replaced() const
    {
        return true;
    }

    bool el_svg::has_external_intrinsic_size() const
    {
        return false;
    }

    bool el_svg::view_box(float box[4]) const
    {
        std::vector<float> values = numbers(get_attr("viewbox"));
        if(values.size() != 4 || values[2] <= 0 || values[3] <= 0)
        {
            return false;
        }
        std::copy(values.begin(), values.end(), box);
        return true;
    }

    void el_svg::parse_attributes()
    {
        html_tag::parse_attributes();
        for(const char* name : {"width", "height"})
        {
            const char* value = get_attr(name);
            if(value && *value)
            {
                map_to_dimension_property(std::string(name) == "width" ? _width_ : _height_, value);
            }
        }
    }

    void el_svg::get_content_size(size& sz, pixel_t max_width)
    {
        float              box[4];
        bool               has_box = view_box(box);
        std::vector<float> w       = numbers(get_attr("width"));
        std::vector<float> h       = numbers(get_attr("height"));
        if(has_box)
        {
            float ratio = box[3] / box[2];
            float width = !w.empty() ? w[0] : (!h.empty() ? h[0] / ratio : max_width.value());
            sz.width    = pixel_t(width);
            sz.height   = pixel_t(!h.empty() ? h[0] : width * ratio);
        } else
        {
            sz.width  = pixel_t(!w.empty() ? w[0] : 300);
            sz.height = pixel_t(!h.empty() ? h[0] : 150);
        }
    }

    void el_svg::view_group(const position& pos, float view[4], paint_group& group) const
    {
        view[0] = 0;
        view[1] = 0;
        view[2] = pos.width.value();
        view[3] = pos.height.value();
        bool  has_box = view_box(view);
        float sx      = pos.width.value() / view[2];
        float sy      = pos.height.value() / view[3];
        float tx      = pos.x.value();
        float ty      = pos.y.value();
        std::string aspect = lowcase(get_attr("preserveaspectratio", ""));
        if(has_box && aspect.find("none") == std::string::npos)
        {
            bool  slice = aspect.find("slice") != std::string::npos;
            float s     = slice ? std::max(sx, sy) : std::min(sx, sy);
            float fx = 0.5f, fy = 0.5f;
            if(aspect.find("xmin") != std::string::npos)
            {
                fx = 0;
            } else if(aspect.find("xmax") != std::string::npos)
            {
                fx = 1;
            }
            if(aspect.find("ymin") != std::string::npos)
            {
                fy = 0;
            } else if(aspect.find("ymax") != std::string::npos)
            {
                fy = 1;
            }
            tx += (pos.width.value() - view[2] * s) * fx;
            ty += (pos.height.value() - view[3] * s) * fy;
            sx = sy = s;
        }
        group.matrix = {sx, 0, 0, sy, tx - view[0] * sx, ty - view[1] * sy};
        group.bounds = position(pixel_t(view[0]), pixel_t(view[1]), pixel_t(view[2]), pixel_t(view[3]));
    }

    element::ptr el_svg::element_at(const position& pos, pixel_t x, pixel_t y)
    {
        if(pos.width <= 0_px || pos.height <= 0_px || !pos.is_point_inside(x, y))
            return nullptr;
        paint_group group;
        float       view[4];
        view_group(pos, view, group);
        svg_painter painter(doc_ptr()->container(), view, group.matrix, x.value(), y.value());
        painter.draw_children(shared_from_this());
        return painter.hit();
    }

    bool el_svg::paint_shapes(uint_ptr hdc, const position& pos)
    {
        if(pos.width <= 0_px || pos.height <= 0_px)
            return false;
        paint_group group;
        float       view[4];
        view_group(pos, view, group);
        auto container = doc_ptr()->container();
        container->begin_group(hdc, group);
        svg_painter(container, hdc, view).draw_children(shared_from_this());
        container->end_group(hdc);
        return true;
    }

    void el_svg::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, const std::shared_ptr<render_item>& ri)
    {
        html_tag::draw(hdc, x, y, clip, ri);
        if(g_paint_trace)
        {
            return;
        }
        position pos  = ri->pos();
        pos.x        += x;
        pos.y        += y;
        if(pos.width <= 0_px || pos.height <= 0_px || !pos.does_intersect(clip))
        {
            return;
        }
        paint_group group;
        float       view[4];
        view_group(pos, view, group);
        const float     sx        = group.matrix.a;
        const float     sy        = group.matrix.d;
        const float     tx        = group.matrix.tx + view[0] * sx;
        const float     ty        = group.matrix.ty + view[1] * sy;
        auto            container = doc_ptr()->container();
        border_radiuses no_radius{};
        container->set_clip(pos, no_radius);
        const position cache_bounds(pixel_t((pos.x.value() - group.matrix.tx) / sx), pixel_t((pos.y.value() - group.matrix.ty) / sy),
                                    pixel_t(pos.width.value() / sx), pixel_t(pos.height.value() / sy));
        auto paint = [&](uint_ptr target) {
            container->begin_group(target, group);
            svg_painter(container, target, view, m_group_cache_eligible ? &cache_bounds : nullptr).draw_children(shared_from_this());
            container->end_group(target);
        };
        uint64_t key    = 0;
        bool     cached = false;
        if(cached_paint_key(key))
        {
            for(float v : {pos.width.value(), pos.height.value(), view[0], view[1], view[2], view[3], sx, sy, tx - std::floor(tx), ty - std::floor(ty)})
            {
                uint32_t bits;
                memcpy(&bits, &v, sizeof(bits));
                key = (key ^ bits) * 1099511628211ULL;
            }
            if(key == m_last_paint_key)
            {
                m_stable_draws++;
            } else
            {
                m_last_paint_key = key;
                m_stable_draws   = 0;
            }
            static const int stable_draws_needed = getenv("LH_SVG_LATE_CACHE") ? 2 : 0;
            if(m_stable_draws >= stable_draws_needed)
            {
                cached = container->draw_cached(hdc, pos, key, paint);
            }
        }
        if(!cached)
        {
            container->draw_isolated(hdc, pos, paint);
        }
        container->del_clip();
    }

    bool el_svg::cached_paint_key(uint64_t& key)
    {
        const uint32_t generation = doc_ptr()->paint_generation();
        if(m_content_generation != generation)
        {
            const bool previous_cacheable = m_content_cacheable;
            const uint64_t previous_key = m_content_key;
            m_content_generation = generation;
            m_content_cacheable  = cacheable_paint_key(m_content_key);
            m_group_cache_eligible = m_group_cache_eligible || (previous_cacheable && m_content_cacheable && previous_key != m_content_key);
        }
        key = m_content_key;
        return m_content_cacheable;
    }

    bool el_svg::cacheable_paint_key(uint64_t& key) const
    {
        uint64_t                        h = 1469598103934665603ULL;
        std::unordered_set<std::string> ids;
        std::vector<std::string>        references;
        int                             count = 0;
        bool                            ok    = true;
        auto collect_paint_references = [&](const element::ptr& el) {
            const auto* tag = dynamic_cast<const html_tag*>(el.get());
            if(!tag) return;
            for(const auto name : {_fill_, _stroke_})
            {
                std::string id;
                if(const auto* tokens = tag->get_token_property(name))
                    id = svg_url_id(*tokens);
                else if(const char* value = tag->get_attr(_s(name).c_str()); value && strchr(value, '('))
                    id = svg_url_id(svg_property(el, name, false));
                if(!id.empty()) references.push_back(std::move(id));
            }
        };
        std::function<void(const element::ptr&)> visit = [&](const element::ptr& el) {
            if(!ok)
                return;
            if(++count > 4000)
            {
                ok = false;
                return;
            }
            const char* tag = el->get_tagName();
            std::string name = tag ? lowcase(tag) : "";
            if(name == "use" || name == "image" || name == "foreignobject" || name == "canvas" || name == "img")
            {
                ok = false;
                return;
            }
            if(const char* id = el->get_attr("id"))
                ids.insert(id);
            collect_paint_references(el);
            for(const char* attribute : {"fill", "stroke", "style", "mask", "clip-path", "filter", "marker-start", "marker-mid", "marker-end"})
            {
                if(const char* value = el->get_attr(attribute))
                {
                    const char* at = strstr(value, "url(");
                    while(at)
                    {
                        const char* hash = strchr(at, '#');
                        const char* end  = strchr(at, ')');
                        if(!hash || !end || hash > end)
                            break;
                        std::string ref(hash + 1, end);
                        while(!ref.empty() && (ref.back() == '"' || ref.back() == '\'' || ref.back() == ' '))
                            ref.pop_back();
                        references.push_back(ref);
                        at = strstr(end, "url(");
                    }
                }
            }
            h = (h ^ el->paint_signature()) * 1099511628211ULL;
            for(const auto& child : el->children())
                visit(child);
        };
        visit(std::const_pointer_cast<element>(shared_from_this()));
        if(!ok)
            return false;
        for(auto p = parent(); p; p = p->parent())
        {
            collect_paint_references(p);
            h = (h ^ p->paint_signature()) * 1099511628211ULL;
        }
        for(const auto& ref : references)
        {
            if(!ids.count(ref))
                return false;
        }
        key = h;
        return true;
    }

    std::shared_ptr<render_item> el_svg::create_render_item(const std::shared_ptr<render_item>& parent_ri)
    {
        auto ret = std::make_shared<render_item_image>(shared_from_this());
        ret->parent(parent_ri);
        return ret;
    }
} // namespace litehtml
