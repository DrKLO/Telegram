#include "html.h"
#include "css_length.h"

namespace litehtml
{

    bool css_length::from_token(const css_token& token, int options, css_values keywords)
    {
        if((options & f_positive) && is_one_of(token.type, NUMBER, DIMENSION, PERCENTAGE) && token.n.number < 0)
        {
            return false;
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
