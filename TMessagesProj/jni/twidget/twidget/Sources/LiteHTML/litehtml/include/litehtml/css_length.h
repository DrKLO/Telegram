#ifndef LITEHTML_CSS_LENGTH_H
#define LITEHTML_CSS_LENGTH_H

#include <functional>
#include <string>
#include "css_tokenizer.h"
#include "css_values.h"
#include "pixel_type.h"

namespace litehtml
{
    // from_token options (flags)
    enum
    {
        f_length            = 1,                       // <length> (includes unitless zero)
        f_percentage        = 2,                       // <percentage>
        f_length_percentage = f_length | f_percentage, // <length-percentage>
        f_number            = 4,                       // <number>
        f_integer           = 8,                       // <integer>
        f_positive          = 16                       // ⩾0 (non-negative)
    };

    // <length> | <percentage> | <number> | auto | none | normal
    // https://developer.mozilla.org/en-US/docs/Web/CSS/length
    class css_length
    {
        union {
            float m_value = 0;
            int   m_predef;
        };
        css_units m_units         = css_units_none;
        bool      m_is_predefined = false;

      public:
        css_length() = default;
        css_length(float val, css_units units = css_units_px);
        css_length& operator=(float val);

        bool              is_predefined() const;
        void              predef(int val);
        int               predef() const;
        static css_length predef_value(int val = 0);
        void              set_value(float val, css_units units);
        void              set_calc(int index);
        int               calc_index() const { return static_cast<int>(m_value); }
        float             val() const;
        css_units         units() const;
        pixel_t           calc_percent(pixel_t width) const;
        bool              from_token(const css_token& token, int options, css_values predefined_keywords = {});
        std::string       to_string() const;
    };

    using length_vector = std::vector<css_length>;

    // Math that only layout can finish, such as calc(50% - 10px), is kept as an expression shared by every
    // length that has it. `convert` turns a leaf of another unit than % into px; null takes it as px.
    using calc_convert_fn = std::function<float(float value, css_units unit)>;
    pixel_t calc_evaluate(int index, pixel_t percent_base, const calc_convert_fn& convert = nullptr);
    /// The expression with every leaf but % in px; -1 when it has no % left, and `px` is then its value.
    int calc_resolve_units(int index, const calc_convert_fn& convert, float& px);

    inline css_length::css_length(float val, css_units units) :
        m_value(val),
        m_units(units)
    {
    }

    inline css_length& css_length::operator=(float val)
    {
        m_value         = val;
        m_units         = css_units_px;
        m_is_predefined = false;
        return *this;
    }

    inline bool css_length::is_predefined() const
    {
        return m_is_predefined;
    }

    inline void css_length::predef(int val)
    {
        m_predef        = val;
        m_is_predefined = true;
    }

    inline int css_length::predef() const
    {
        if(m_is_predefined)
        {
            return m_predef;
        }
        return 0;
    }

    inline void css_length::set_value(float val, css_units units)
    {
        m_value         = val;
        m_is_predefined = false;
        m_units         = units;
    }

    inline void css_length::set_calc(int index)
    {
        m_value         = static_cast<float>(index);
        m_is_predefined = false;
        m_units         = css_units_calc;
    }

    inline float css_length::val() const
    {
        if(!m_is_predefined)
        {
            // A calc length means something only against a size: without one its % parts count as zero.
            return m_units == css_units_calc ? calc_evaluate(static_cast<int>(m_value), 0_px).value() : m_value;
        }
        return 0;
    }

    inline css_units css_length::units() const
    {
        return m_units;
    }

    inline pixel_t css_length::calc_percent(pixel_t width) const
    {
        if(!is_predefined())
        {
            if(units() == css_units_percentage)
            {
                return width * m_value / 100.0_px;
            }
            if(units() == css_units_calc)
            {
                return calc_evaluate(static_cast<int>(m_value), width);
            }
            return static_cast<pixel_t>(val());
        }
        return 0_px;
    }
} // namespace litehtml

#endif // LITEHTML_CSS_LENGTH_H
