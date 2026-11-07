#include "utf8_strings.h"
#include "document_container.h"

void litehtml::document_container::split_text(const char* text, const std::function<void(const char*)>& on_word,
                                              const std::function<void(const char*)>& on_space)
{
    std::u32string str;
    std::u32string str_in = static_cast<const char32_t*>(utf8_to_utf32(text));
    auto           letter = [](char32_t ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= 0xC0 && ch != 0xD7 && ch != 0xF7 && ch < 0x2000);
    };
    for(size_t i = 0; i < str_in.size(); i++)
    {
        const char32_t c = str_in[i];
        const char32_t prev = i > 0 ? str_in[i - 1] : 0;
        const char32_t next = i + 1 < str_in.size() ? str_in[i + 1] : 0;
        if(((c == '-' || c == 0x2010) && letter(prev) && letter(next)) || (c == '?' && next > ' ' && prev > ' '))
        {
            str += c;
            on_word(utf32_to_utf8(str));
            str.clear();
            continue;
        }
        if(c <= ' ' && (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f'))
        {
            if(!str.empty())
            {
                on_word(utf32_to_utf8(str));
                str.clear();
            }
            str += c;
            on_space(utf32_to_utf8(str));
            str.clear();
        }
        // CJK character range
        else if(c >= 0x4E00 && c <= 0x9FCC)
        {
            if(!str.empty())
            {
                on_word(utf32_to_utf8(str));
                str.clear();
            }
            str += c;
            on_word(utf32_to_utf8(str));
            str.clear();
        } else
        {
            str += c;
        }
    }
    if(!str.empty())
    {
        on_word(utf32_to_utf8(str));
    }
}
