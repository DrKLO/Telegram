#include "html.h"
#include "css_length.h"

#include <algorithm>
#include <cmath>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace litehtml
{
    namespace
    {
        struct calc_node
        {
            enum kind_t : uint8_t
            {
                leaf,
                sum,     // args, each with its sign in `signs`
                scaled,  // args[0] times value
                min,
                max,
                clamp
            } kind = leaf;
            float             value = 0;  // leaf: the number; scaled: the factor
            css_units         unit  = css_units_none;
            std::vector<calc_node> args;
            std::vector<int8_t>    signs;
        };

        // Expressions are few (a widget's stylesheets, after unit conversion) and live as long as the process.
        constexpr size_t      MAX_EXPRESSIONS = 4096;
        std::mutex&           calc_mutex() { static std::mutex& m = *new std::mutex; return m; }
        std::vector<calc_node>& calc_table() { static auto& t = *new std::vector<calc_node>; return t; }
        std::unordered_map<std::string, int>& calc_index() { static auto& i = *new std::unordered_map<std::string, int>; return i; }

        void key_of(const calc_node& node, std::string& out)
        {
            out += std::to_string(node.kind) + ":" + std::to_string(node.value) + ":" + std::to_string(node.unit) + "(";
            for(size_t i = 0; i < node.args.size(); i++)
            {
                if(!node.signs.empty())
                    out += node.signs[i] < 0 ? "-" : "+";
                key_of(node.args[i], out);
                out += ",";
            }
            out += ")";
        }

        int intern(calc_node node)
        {
            std::string key;
            key_of(node, key);
            std::lock_guard<std::mutex> lock(calc_mutex());
            auto& index = calc_index();
            if(auto it = index.find(key); it != index.end())
                return it->second;
            auto& table = calc_table();
            if(table.size() >= MAX_EXPRESSIONS)
                return -1;
            table.push_back(std::move(node));
            index.emplace(std::move(key), int(table.size() - 1));
            return int(table.size() - 1);
        }

        void skip_whitespace(const css_token_vector& tokens, size_t& i)
        {
            while(i < tokens.size() && tokens[i].type == WHITESPACE)
                i++;
        }

        bool parse_sum(const css_token_vector& tokens, size_t& i, calc_node& out, int depth);

        bool parse_list(const css_token_vector& tokens, std::vector<calc_node>& out, int depth)
        {
            size_t i = 0;
            while(true)
            {
                calc_node arg;
                if(!parse_sum(tokens, i, arg, depth))
                    return false;
                out.push_back(std::move(arg));
                skip_whitespace(tokens, i);
                if(i == tokens.size())
                    return out.size() <= 32;
                if(tokens[i].ch != ',')
                    return false;
                i++;
            }
        }

        bool parse_atom(const css_token_vector& tokens, size_t& i, calc_node& out, int depth)
        {
            skip_whitespace(tokens, i);
            if(i >= tokens.size() || depth > 16)
                return false;
            const css_token& tok = tokens[i++];
            if(tok.type == NUMBER || tok.type == PERCENTAGE)
            {
                out.value = tok.n.number;
                out.unit  = tok.type == NUMBER ? css_units_none : css_units_percentage;
                return true;
            }
            if(tok.type == DIMENSION)
            {
                auto unit = css_values(css_units_strings).value_index(lowcase(tok.unit()));
                if(!unit || is_one_of(*unit, css_units_none, css_units_percentage, css_units_calc))
                    return false;
                out.value = tok.n.number;
                out.unit  = static_cast<css_units>(*unit);
                return true;
            }
            const std::string name = tok.type == CV_FUNCTION ? lowcase(tok.name()) : std::string();
            if(tok.type == ROUND_BLOCK || name == "calc")
            {
                size_t inner = 0;
                if(!parse_sum(tok.value, inner, out, depth + 1))
                    return false;
                skip_whitespace(tok.value, inner);
                return inner == tok.value.size();
            }
            if(is_one_of(name, "min", "max", "clamp"))
            {
                out.kind = name == "min" ? calc_node::min : name == "max" ? calc_node::max : calc_node::clamp;
                return parse_list(tok.value, out.args, depth + 1) && (out.kind != calc_node::clamp || out.args.size() == 3);
            }
            return false;
        }

        // A plain number, unitless and constant: the only thing a product may scale by.
        bool is_number(const calc_node& node)
        {
            return node.kind == calc_node::leaf && node.unit == css_units_none;
        }

        bool parse_product(const css_token_vector& tokens, size_t& i, calc_node& out, int depth)
        {
            if(!parse_atom(tokens, i, out, depth))
                return false;
            while(true)
            {
                size_t probe = i;
                skip_whitespace(tokens, probe);
                if(probe >= tokens.size() || (tokens[probe].ch != '*' && tokens[probe].ch != '/'))
                    return true;
                const bool divide = tokens[probe].ch == '/';
                i = probe + 1;
                calc_node right;
                if(!parse_atom(tokens, i, right, depth))
                    return false;
                if(divide || is_number(right))
                {
                    if(!is_number(right) || (divide && right.value == 0))
                        return false;
                    float factor = divide ? 1 / right.value : right.value;
                    if(is_number(out))
                    {
                        out.value *= factor;
                        continue;
                    }
                    calc_node scaled;
                    scaled.kind  = calc_node::scaled;
                    scaled.value = factor;
                    scaled.args.push_back(std::move(out));
                    out = std::move(scaled);
                } else if(is_number(out))
                {
                    calc_node scaled;
                    scaled.kind  = calc_node::scaled;
                    scaled.value = out.value;
                    scaled.args.push_back(std::move(right));
                    out = std::move(scaled);
                } else
                {
                    return false;
                }
            }
        }

        bool parse_sum(const css_token_vector& tokens, size_t& i, calc_node& out, int depth)
        {
            calc_node first;
            if(!parse_product(tokens, i, first, depth))
                return false;
            calc_node sum;
            sum.kind = calc_node::sum;
            sum.args.push_back(std::move(first));
            sum.signs.push_back(1);
            while(true)
            {
                size_t probe = i;
                skip_whitespace(tokens, probe);
                if(probe >= tokens.size() || (tokens[probe].ch != '+' && tokens[probe].ch != '-'))
                    break;
                i = probe + 1;
                calc_node term;
                if(!parse_product(tokens, i, term, depth))
                    return false;
                sum.args.push_back(std::move(term));
                sum.signs.push_back(tokens[probe].ch == '-' ? -1 : 1);
            }
            out = sum.args.size() == 1 ? std::move(sum.args.front()) : std::move(sum);
            return true;
        }

        float evaluate(const calc_node& node, float base, const calc_convert_fn& convert)
        {
            switch(node.kind)
            {
            case calc_node::leaf:
                if(node.unit == css_units_percentage)
                    return base * node.value / 100;
                return convert && node.unit != css_units_none && node.unit != css_units_px ? convert(node.value, node.unit) : node.value;
            case calc_node::sum:
                {
                    float total = 0;
                    for(size_t i = 0; i < node.args.size(); i++)
                        total += node.signs[i] * evaluate(node.args[i], base, convert);
                    return total;
                }
            case calc_node::scaled:
                return node.value * evaluate(node.args[0], base, convert);
            case calc_node::clamp:
                {
                    float low = evaluate(node.args[0], base, convert);
                    return std::max(low, std::min(evaluate(node.args[1], base, convert), evaluate(node.args[2], base, convert)));
                }
            default:
                {
                    float value = evaluate(node.args[0], base, convert);
                    for(size_t i = 1; i < node.args.size(); i++)
                    {
                        float arg = evaluate(node.args[i], base, convert);
                        value     = node.kind == calc_node::min ? std::min(value, arg) : std::max(value, arg);
                    }
                    return value;
                }
            }
        }

        bool has_percent(const calc_node& node)
        {
            if(node.kind == calc_node::leaf)
                return node.unit == css_units_percentage;
            return std::any_of(node.args.begin(), node.args.end(), has_percent);
        }

        void convert_leaves(calc_node& node, const calc_convert_fn& convert)
        {
            if(node.kind == calc_node::leaf)
            {
                if(node.unit != css_units_percentage && node.unit != css_units_none && node.unit != css_units_px)
                {
                    node.value = convert(node.value, node.unit);
                    node.unit  = css_units_px;
                }
                return;
            }
            for(auto& arg : node.args)
                convert_leaves(arg, convert);
        }
    } // namespace

    pixel_t calc_evaluate(int index, pixel_t percent_base, const calc_convert_fn& convert)
    {
        calc_node node;
        {
            std::lock_guard<std::mutex> lock(calc_mutex());
            if(index < 0 || size_t(index) >= calc_table().size())
                return 0_px;
            node = calc_table()[index];
        }
        float value = evaluate(node, percent_base.value(), convert);
        return pixel_t(std::isfinite(value) ? value : 0.f);
    }

    int calc_resolve_units(int index, const calc_convert_fn& convert, float& px)
    {
        calc_node node;
        {
            std::lock_guard<std::mutex> lock(calc_mutex());
            if(index < 0 || size_t(index) >= calc_table().size())
            {
                px = 0;
                return -1;
            }
            node = calc_table()[index];
        }
        convert_leaves(node, convert);
        if(!has_percent(node))
        {
            px = evaluate(node, 0, nullptr);
            if(!std::isfinite(px))
                px = 0;
            return -1;
        }
        return intern(std::move(node));
    }

    bool css_length::from_token(const css_token& token, int options, css_values keywords)
    {
        if((options & f_positive) && is_one_of(token.type, NUMBER, DIMENSION, PERCENTAGE) && token.n.number < 0)
        {
            return false;
        }

        // Math the stylesheet could not fold (style.cpp, fold_math) mixes units: it waits for layout.
        if(token.type == CV_FUNCTION && (options & f_length) && (options & f_percentage) &&
           is_one_of(lowcase(token.name()), "calc", "min", "max", "clamp"))
        {
            calc_node node;
            size_t    i = 0;
            css_token_vector single = {token};
            if(!parse_atom(single, i, node, 0) || is_number(node))
            {
                return false;
            }
            int index = intern(std::move(node));
            if(index < 0)
            {
                return false;
            }
            set_calc(index);
            return true;
        }

        if(token.type == IDENT)
        {
            auto idx = keywords.value_index(lowcase(token.name()));
            if(!idx.has_value())
            {
                return false;
            }
            m_predef        = idx.value();
            m_is_predefined = true;
            return true;
        }
        if(token.type == DIMENSION)
        {
            if(!(options & f_length))
            {
                return false;
            }

            auto idx = css_values(css_units_strings).value_index(lowcase(token.unit()));
            // note: 1none and 1\% are invalid
            if(!idx.has_value() || idx.value() == css_units_none || idx.value() == css_units_percentage)
            {
                return false;
            }

            m_value         = token.n.number;
            m_units         = static_cast<css_units>(idx.value());
            m_is_predefined = false;
            return true;
        }
        if(token.type == PERCENTAGE)
        {
            if(!(options & f_percentage))
            {
                return false;
            }

            m_value         = token.n.number;
            m_units         = css_units_percentage;
            m_is_predefined = false;
            return true;
        }
        if(token.type == NUMBER)
        {
            // if token is a nonzero number and neither f_number nor f_integer is specified in the options
            if(!(options & (f_number | f_integer)) && pixel_t(token.n.number) != 0_px)
            {
                return false;
            }
            // if token is a zero number and neither of f_number, f_integer or f_length are specified in the options
            if(!(options & (f_number | f_integer | f_length)) && pixel_t(token.n.number) == 0_px)
            {
                return false;
            }
            if((options & f_integer) && token.n.number_type != css_number_integer)
            {
                return false;
            }

            m_value         = token.n.number;
            m_units         = css_units_none;
            m_is_predefined = false;
            return true;
        }
        return false;
    }

    std::string css_length::to_string() const
    {
        if(m_is_predefined)
        {
            return "def(" + std::to_string(m_predef) + ")";
        }
        std::string res  = std::to_string(m_value) + "{";
        res             += css_values(css_units_strings).value_by_index(m_units);
        res             += "}";
        return res;
    }

    css_length css_length::predef_value(int val)
    {
        css_length len;
        len.predef(val);
        return len;
    }

} // namespace litehtml
