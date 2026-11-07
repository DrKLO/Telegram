#include "html_microsyntaxes.h"
#include "html.h"

namespace litehtml
{

    // https://html.spec.whatwg.org/multipage/common-microsyntaxes.html#rules-for-parsing-integers
    bool html_parse_integer(const std::string& str, int& val)
    {
        const char* ptr = str.c_str();
        char*       end;
        // AFAICT strtol does exactly what's required by the standard
        int n = strtol(ptr, &end, 10);
        if(end == ptr)
        {
            return false;
        }
        val = n;
        return true;
    }

    // https://html.spec.whatwg.org/multipage/common-microsyntaxes.html#rules-for-parsing-non-negative-integers
    bool html_parse_non_negative_integer(const std::string& str, int& val)
    {
        int n = 0;
        if(!html_parse_integer(str, n) || n < 0)
        {
            return false;
        }
        val = n;
        return true;
    }

    // https://html.spec.whatwg.org/multipage/common-microsyntaxes.html#rules-for-parsing-dimension-values
    bool html_parse_dimension_value(const std::string& str, float& result, html_dimension_type& type)
    {
        // 1. Let input be the string being parsed.
        // 2. Let position be a position variable for input, initially pointing at the start of input.
        auto position = str.c_str();
        // 3. Skip ASCII whitespace within input given position.
        while(is_whitespace(*position))
        {
            position++;
        }
        // 4. If position is past the end of input or the code point at position within input is not an ASCII digit,
        // then return failure.
        if(!is_digit(*position))
        {
            return false;
        }
        // 5. Collect a sequence of code points that are ASCII digits from input given position, and interpret the
        // resulting sequence as a base-ten integer. Let value be that number.
        char* end;
        auto  value = static_cast<float>(strtol(position, &end, 10));
        position    = end;
        // 6. If position is past the end of input, then return value as a length.
        if(!*position)
        {
            result = value;
            type   = html_length;
            return true;
        }
        // 7. If the code point at position within input is U+002E (.), then:
        if(*position == '.')
        {
            // 1. Advance position by 1.
            position++;
            // 2. If position is past the end of input or the code point at position within input is not an ASCII digit,
            // then return the current dimension value with value, input, and position.
            if(!is_digit(*position))
            {
                result = value;
                type   = *position == '%' ? html_percentage : html_length;
                return true;
            }
            // 3. Let divisor have the value 1.
            float divisor = 1;
            // 4. While true:
            while(true)
            {
                // 1. Multiply divisor by ten.
                divisor *= 10;
                // 2. Add the value of the code point at position within input, interpreted as a base-ten digit (0..9)
                // and divided by divisor, to value.
                value += digit_value(*position) / divisor;
                // 3. Advance position by 1.
                position++;
                // 4. If position is past the end of input, then return value as a length.
                if(!*position)
                {
                    result = value;
                    type   = html_length;
                    ;
                    return true;
                }
                // 5. If the code point at position within input is not an ASCII digit, then break.
                if(!is_digit(*position))
                {
                    break;
                }
            }
        }
        // 8. Return the current dimension value with value, input, and position.
        result = value;
        type   = *position == '%' ? html_percentage : html_length;
        return true;
    }

    // https://html.spec.whatwg.org/multipage/common-microsyntaxes.html#rules-for-parsing-non-zero-dimension-values
    bool html_parse_nonzero_dimension_value(const std::string& str, float& val, html_dimension_type& type)
    {
        float               x;
        html_dimension_type t;
        if(!html_parse_dimension_value(str, x, t) || pixel_t(x) == 0_px)
        {
            return false;
        }
        val  = x;
        type = t;
        return true;
    }

} // namespace litehtml
