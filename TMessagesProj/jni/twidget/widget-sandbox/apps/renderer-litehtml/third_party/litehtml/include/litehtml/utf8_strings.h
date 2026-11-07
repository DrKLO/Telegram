#ifndef LITEHTML_UTF8_STRINGS_H
#define LITEHTML_UTF8_STRINGS_H

#include <string>

namespace litehtml
{
    // converts UTF-32 ch to UTF-8 and appends it to str
    void     append_char(std::string& str, char32_t ch);
    char32_t read_utf8_char(const std::string& str, int& index);
    void     prev_utf8_char(const std::string& str, int& index);

    class utf8_to_utf32
    {
        std::u32string m_str;

      public:
        utf8_to_utf32(const std::string& val);
        operator const char32_t*() const
        {
            return m_str.c_str();
        }
    };

    class utf32_to_utf8
    {
        std::string m_str;

      public:
        utf32_to_utf8(const std::u32string& val);
        operator const char*() const
        {
            return m_str.c_str();
        }
        const char* c_str() const
        {
            return m_str.c_str();
        }
    };

#define litehtml_from_utf32(str) litehtml::utf32_to_utf8(str)
#define litehtml_to_utf32(str)   litehtml::utf8_to_utf32(str)
} // namespace litehtml

#endif // LITEHTML_UTF8_STRINGS_H
