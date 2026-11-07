#ifndef LITEHTML_HTML_H
#define LITEHTML_HTML_H

#include "media_query.h"
#include <algorithm>
#include <cstdlib>
#include <cstring>

namespace litehtml
{
    const std::string      whitespace = " \n\r\t\f";
    std::string&           trim(std::string& s, const std::string& chars_to_trim = whitespace);
    std::string            trim(const std::string& s, const std::string& chars_to_trim = whitespace);
    std::string&           lcase(std::string& s);
    std::string::size_type find_close_bracket(const std::string& s, std::string::size_type off, char open_b = '(',
                                              char close_b = ')');
    void split_string(const std::string& str, std::vector<std::string>& tokens, const std::string& delims = whitespace,
                      const std::string& delims_preserve = "", const std::string& quote = "\"");
    std::vector<std::string> split_string(const std::string& str, const std::string& delims = whitespace,
                                          const std::string& delims_preserve = "", const std::string& quote = "\"");
    void        join_string(std::string& str, const std::vector<std::string>& tokens, const std::string& delims);
    double      t_strtod(const char* string, char** endPtr = nullptr);
    std::string get_escaped_string(const std::string& in_str);

    template <typename T, typename... Opts> inline bool is_one_of(const T& val, Opts... opts)
    {
        return (... || (val == opts));
    }
    template <class T> const T& at(const std::vector<T>& vec, int index /*may be negative*/)
    {
        static T invalid_item; // T's default constructor must create invalid item
        if(index < 0)
        {
            index += static_cast<int>(vec.size());
        }
        return index >= 0 && index < static_cast<int>(vec.size()) ? vec[index] : invalid_item;
    }
    template <class Map, class Key> auto at(const Map& map, const Key& key)
    {
        static typename Map::mapped_type invalid_value; // mapped_type's default constructor must create invalid item
        auto                             it = map.find(key);
        return it != map.end() ? it->second : invalid_value;
    }
    template <typename T> std::vector<T> slice(const std::vector<T>& vec, int index, int count = -1)
    {
        if(count == -1)
        {
            count = static_cast<int>(vec.size()) - index;
        }
        return {vec.begin() + index, vec.begin() + index + count};
    }
    template <class C> // C == vector or string
    void remove(C& vec, int index /*may be negative*/, int count = 1)
    {
        if(index < 0)
        {
            index += static_cast<int>(vec.size());
        }

        if(index < 0 || index >= static_cast<int>(vec.size()))
        {
            return;
        }

        count = std::min(count, static_cast<int>(vec.size()) - index);
        if(count <= 0)
        {
            return;
        }

        vec.erase(vec.begin() + index, vec.begin() + index + count);
    }
    template <class T> void insert(std::vector<T>& vec, int index, const std::vector<T>& x)
    {
        vec.insert(vec.begin() + index, x.begin(), x.end());
    }
    template <class T> std::vector<T>& operator+=(std::vector<T>& vec, const std::vector<T>& x)
    {
        vec.insert(vec.end(), x.begin(), x.end());
        return vec;
    }
    template <class C, class T> bool contains(const C& coll, const T& item)
    {
        return std::find(coll.begin(), coll.end(), item) != coll.end();
    }
    inline bool contains(const std::string& str, const std::string& substr)
    {
        return str.find(substr) != std::string::npos;
    }
    template <class C> void sort(C& coll)
    {
        std::sort(coll.begin(), coll.end());
    }

    int         t_strcasecmp(const char* s1, const char* s2);
    int         t_strncasecmp(const char* s1, const char* s2, size_t n);
    inline bool equal_i(const std::string& s1, const std::string& s2)
    {
        if(s1.size() != s2.size())
        {
            return false;
        }
        return t_strncasecmp(s1.c_str(), s2.c_str(), s1.size()) == 0;
    }
    inline bool match(const std::string& str, int index /*may be negative*/, const std::string& substr)
    {
        if(index < 0)
        {
            index += static_cast<int>(str.size());
        }
        if(index < 0)
        {
            return false;
        }
        return str.substr(index, substr.size()) == substr;
    }
    inline bool match_i(const std::string& str, int index /*may be negative*/, const std::string& substr)
    {
        if(index < 0)
        {
            index += static_cast<int>(str.size());
        }
        if(index < 0)
        {
            return false;
        }
        return equal_i(str.substr(index, substr.size()), substr);
    }

    bool is_number(const std::string& string, bool allow_dot = true);

    // https://infra.spec.whatwg.org/#ascii-whitespace
    inline bool is_whitespace(int c)
    {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    inline int t_isalpha(int c)
    {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
    const auto is_letter = t_isalpha;

    inline int t_tolower(int c)
    {
        return (c >= 'A' && c <= 'Z' ? c + 'a' - 'A' : c);
    }
    // https://infra.spec.whatwg.org/#ascii-lowercase
    inline int lowcase(int c)
    {
        return t_tolower(c);
    }
    inline std::string lowcase(std::string str)
    {
        for(char& c : str)
        {
            c = static_cast<char>(t_tolower(c));
        }
        return str;
    }

    inline int t_isdigit(int c)
    {
        return (c >= '0' && c <= '9');
    }
    const auto is_digit = t_isdigit;

    inline bool is_hex_digit(int ch)
    {
        return is_digit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    inline int digit_value(int ch)
    {
        return is_digit(ch) ? ch - '0' : lowcase(ch) - 'a' + 10;
    }

    inline bool is_surrogate(int ch)
    {
        return ch >= 0xD800 && ch < 0xE000;
    }

    inline int round_f(float val)
    {
        int int_val = static_cast<int>(val);
        if(val - static_cast<float>(int_val) >= 0.5f)
        {
            int_val++;
        }
        return int_val;
    }

    inline int round_d(double val)
    {
        int int_val = static_cast<int>(val);
        if(val - int_val >= 0.5)
        {
            int_val++;
        }
        return int_val;
    }

    inline float t_strtof(const std::string& str, char** endPtr = nullptr)
    {
        return static_cast<float>(t_strtod(str.c_str(), endPtr));
    }

    inline pixel_t baseline_align(pixel_t line_height, pixel_t line_base_line, pixel_t height, pixel_t baseline)
    {
        return (line_height - line_base_line) - (height - baseline);
    }
} // namespace litehtml

#endif // LITEHTML_HTML_H
