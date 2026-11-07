#include "utf8_strings.h"
#include "types.h"

namespace litehtml
{

    // consume one utf-8 char and increment index accordingly
    // if str[index] == 0 index is not incremented
    char32_t read_utf8_char(const std::string& str, int& index)
    {
        auto getb = [&]() -> byte {
            if(!str[index])
            {
                return 0;
            }
            return str[index++];
        };

        byte b1 = getb();

        // Determine whether we are dealing
        // with a one-, two-, three-, or four-
        // byte sequence.
        if((b1 & 0x80) == 0)
        {
            // 1-byte sequence: 000000000xxxxxxx = 0xxxxxxx
            return b1;
        }
        if((b1 & 0xe0) == 0xc0)
        {
            // 2-byte sequence: 00000yyyyyxxxxxx = 110yyyyy 10xxxxxx
            char32_t r  = (b1 & 0x1f) << 6;
            r          |= getb() & 0x3f;
            return r;
        }
        if((b1 & 0xf0) == 0xe0)
        {
            // 3-byte sequence: zzzzyyyyyyxxxxxx = 1110zzzz 10yyyyyy 10xxxxxx
            char32_t r  = (b1 & 0x0f) << 12;
            r          |= (getb() & 0x3f) << 6;
            r          |= getb() & 0x3f;
            return r;
        }
        if((b1 & 0xf8) == 0xf0)
        {
            // 4-byte sequence: uuuzzzzzzyyyyyyxxxxxx = 11110uuu 10zzzzzz 10yyyyyy 10xxxxxx
            byte b2 = getb() & 0x3f;
            byte b3 = getb() & 0x3f;
            byte b4 = getb() & 0x3f;
            return ((b1 & 7) << 18) | (b2 << 12) | (b3 << 6) | b4;
        }

        return 0xFFFD;
    }

    // No error handling, str must be valid UTF-8 (it is ensured by document::parse_html and
    // css_parser::parse_stylesheet). Currently used only in css parser, where actual char value is not needed, so it
    // returns void.
    void prev_utf8_char(const std::string& str, int& index)
    {
        while(index && (static_cast<byte>(str[--index]) >> 6) == 0b10)
            ; // skip continuation bytes
    }

    void append_char(std::string& str, char32_t code)
    {
        if(code <= 0x7F)
        {
            str += static_cast<char>(code);
        } else if(code <= 0x7FF)
        {
            str += static_cast<char>((code >> 6) + 192);
            str += (code & 63) + 128;
        } else if(0xd800 <= code && code <= 0xdfff)
        {
            // error: unexpected surrogate (code is UTF-32, not UTF-16)
        } else if(code <= 0xFFFF)
        {
            str += static_cast<char>((code >> 12) + 224);
            str += ((code >> 6) & 63) + 128;
            str += (code & 63) + 128;
        } else if(code <= 0x10FFFF)
        {
            str += static_cast<char>((code >> 18) + 240);
            str += ((code >> 12) & 63) + 128;
            str += ((code >> 6) & 63) + 128;
            str += (code & 63) + 128;
        }
    }

    utf8_to_utf32::utf8_to_utf32(const std::string& val)
    {
        int index = 0;
        while(char32_t ch = read_utf8_char(val, index))
        {
            m_str += ch;
        }
    }

    utf32_to_utf8::utf32_to_utf8(const std::u32string& val)
    {
        for(auto ch : val)
        {
            append_char(m_str, ch);
        }
    }

} // namespace litehtml
