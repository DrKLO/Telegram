#include "html.h"
#include "types.h"

namespace litehtml
{

    std::string& trim(std::string& s, const std::string& chars_to_trim)
    {
        std::string::size_type pos = s.find_first_not_of(chars_to_trim);
        if(pos != std::string::npos)
        {
            s.erase(s.begin(), s.begin() + pos);
        } else
        {
            s = "";
            return s;
        }
        pos = s.find_last_not_of(chars_to_trim);
        if(pos != std::string::npos)
        {
            s.erase(s.begin() + pos + 1, s.end());
        }
        return s;
    }

    std::string trim(const std::string& s, const std::string& chars_to_trim)
    {
        std::string str = s;
        trim(str, chars_to_trim);
        return str;
    }

    std::string& lcase(std::string& s)
    {
        for(char& i : s)
        {
            i = static_cast<char>(t_tolower(i));
        }
        return s;
    }

    std::string::size_type find_close_bracket(const std::string& s, std::string::size_type off, char open_b,
                                              char close_b)
    {
        int cnt = 0;
        for(std::string::size_type i = off; i < s.length(); i++)
        {
            if(s[i] == open_b)
            {
                cnt++;
            } else if(s[i] == close_b)
            {
                cnt--;
                if(!cnt)
                {
                    return i;
                }
            }
        }
        return std::string::npos;
    }

    litehtml::string_vector split_string(const std::string& str, const std::string& delims,
                                         const std::string& delims_preserve, const std::string& quote)
    {
        litehtml::string_vector result;
        split_string(str, result, delims, delims_preserve, quote);
        return result;
    }

    void split_string(const std::string& str, litehtml::string_vector& tokens, const std::string& delims,
                      const std::string& delims_preserve, const std::string& quote)
    {
        if(str.empty() || (delims.empty() && delims_preserve.empty()))
        {
            return;
        }

        std::string all_delims = delims + delims_preserve + quote;

        std::string::size_type token_start = 0;
        std::string::size_type token_end   = str.find_first_of(all_delims, token_start);
        std::string::size_type token_len;
        std::string            token;
        while(true)
        {
            while(token_end != std::string::npos && quote.find_first_of(str[token_end]) != std::string::npos)
            {
                if(str[token_end] == '(')
                {
                    token_end = find_close_bracket(str, token_end, '(', ')');
                } else if(str[token_end] == '[')
                {
                    token_end = find_close_bracket(str, token_end, '[', ']');
                } else if(str[token_end] == '{')
                {
                    token_end = find_close_bracket(str, token_end, '{', '}');
                } else
                {
                    token_end = str.find_first_of(str[token_end], token_end + 1);
                }
                if(token_end != std::string::npos)
                {
                    token_end = str.find_first_of(all_delims, token_end + 1);
                }
            }

            if(token_end == std::string::npos)
            {
                token_len = std::string::npos;
            } else
            {
                token_len = token_end - token_start;
            }

            token = str.substr(token_start, token_len);
            if(!token.empty())
            {
                tokens.push_back(token);
            }
            if(token_end != std::string::npos && !delims_preserve.empty() &&
               delims_preserve.find_first_of(str[token_end]) != std::string::npos)
            {
                tokens.push_back(str.substr(token_end, 1));
            }

            token_start = token_end;
            if(token_start == std::string::npos)
            {
                break;
            }
            token_start++;
            if(token_start == str.length())
            {
                break;
            }
            token_end = str.find_first_of(all_delims, token_start);
        }
    }

    void join_string(std::string& str, const litehtml::string_vector& tokens, const std::string& delims)
    {
        str = "";
        for(size_t i = 0; i < tokens.size(); i++)
        {
            if(i != 0)
            {
                str += delims;
            }
            str += tokens[i];
        }
    }

    int t_strcasecmp(const char* s1, const char* s2)
    {
        int i, d, c;

        for(i = 0;; i++)
        {
            c = t_tolower(static_cast<unsigned char>(s1[i]));
            d = c - t_tolower(static_cast<unsigned char>(s2[i]));
            if(d < 0)
            {
                return -1;
            }
            if(d > 0)
            {
                return 1;
            }
            if(c == 0)
            {
                return 0;
            }
        }
    }

    int t_strncasecmp(const char* s1, const char* s2, size_t n)
    {
        int i, d, c;

        for(i = 0; i < static_cast<int>(n); i++)
        {
            c = t_tolower(static_cast<unsigned char>(s1[i]));
            d = c - t_tolower(static_cast<unsigned char>(s2[i]));
            if(d < 0)
            {
                return -1;
            }
            if(d > 0)
            {
                return 1;
            }
            if(c == 0)
            {
                return 0;
            }
        }

        return 0;
    }

    std::string get_escaped_string(const std::string& in_str)
    {
        std::string ret;
        for(auto ch : in_str)
        {
            switch(ch)
            {
            case '\'':
                ret += "\\'";
                break;

            case '\"':
                ret += "\\\"";
                break;

            case '\?':
                ret += "\\?";
                break;

            case '\\':
                ret += "\\\\";
                break;

            case '\a':
                ret += "\\a";
                break;

            case '\b':
                ret += "\\b";
                break;

            case '\f':
                ret += "\\f";
                break;

            case '\n':
                ret += "\\n";
                break;

            case '\r':
                ret += "\\r";
                break;

            case '\t':
                ret += "\\t";
                break;

            case '\v':
                ret += "\\v";
                break;

            default:
                ret += ch;
            }
        }
        return ret;
    }

    bool is_number(const std::string& string, const bool allow_dot)
    {
        for(auto ch : string)
        {
            if(!t_isdigit(ch) && (!allow_dot || ch != '.'))
            {
                return false;
            }
        }
        return true;
    }

} // namespace litehtml
