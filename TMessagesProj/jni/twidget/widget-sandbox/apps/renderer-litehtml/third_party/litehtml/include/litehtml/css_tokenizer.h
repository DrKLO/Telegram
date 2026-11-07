#ifndef LITEHTML_CSS_TOKENIZER_H
#define LITEHTML_CSS_TOKENIZER_H

#include <cstdio>
#include <string>
#include <utility>
#include <vector>

namespace litehtml
{

    // https://www.w3.org/TR/css-syntax-3/#tokenization
    // :;,()[]{} token or delim token: type == this char
    // EOF token: type == EOF (-1)
    // type may be 0 to indicate an error, see at()
    enum css_token_type
    {
        WHITESPACE = ' ',

        // Giving EOF and some chars explicit names to facilitate debugging and to get rid of warning C4063: case '41'
        // is not a valid value for switch of enum 'litehtml::css_token_type'
        _EOF          = EOF,
        LEFT_BRACE    = '{',
        RIGHT_BRACE   = '}',
        LEFT_BRACKET  = '[',
        RIGHT_BRACKET = ']',
        LEFT_PAREN    = '(',
        RIGHT_PAREN   = ')',
        COLON         = ':',
        SEMICOLON     = ';',
        COMMA         = ',',
        BANG          = '!',
        DOT           = '.',
        AMPERSAND     = '&',

        IDENT = -20, // do not collide with any unicode chars
        FUNCTION,    // calc(
        AT_KEYWORD,  // @media
        HASH,        // #foo
        STRING,      // "xxx" or 'xxx'
        BAD_STRING,
        URL, // url(x.com)  - but not url("x.com"), which is function + string + ')'
        BAD_URL,
        NUMBER,     // 25
        PERCENTAGE, // 25%
        DIMENSION,  // 25px
        CDO,        // <!--
        CDC,        // -->

        // https://www.w3.org/tr/css-syntax-3/#component-value
        CV_FUNCTION = -100,
        // simple block:
        CURLY_BLOCK  = -100 - '{',
        ROUND_BLOCK  = -100 - '(',
        SQUARE_BLOCK = -100 - '['
    };

    enum css_number_type
    {
        css_number_integer,
        css_number_number
    };

    enum css_hash_type
    {
        css_hash_unrestricted,
        css_hash_id
    };

    // css_token: CSS token or component value ("fat" token)
    // Tokens exist in uncomponentized form only a short time after tokenization, most of the time they are "fat".
    // All functions in css_parser work regardless of whether tokens are fat or not, as per standard.
    // All functions outside of css_parser that parse media queries, selectors, property values assume tokens are
    // componentized.
    class css_token
    {
        std::string m_str;

      public:
        css_token(css_token_type type = css_token_type(), float number = 0,
                  css_number_type number_type = css_number_integer, std::string str = "") :
            m_str(std::move(str)),
            type(type),
            n{number, number_type}
        {
        }

        css_token(css_token_type type, const std::string& str) :
            m_str(str),
            type(type),
            n()
        {
        }

        css_token(const css_token& token) :
            m_str(token.str()),
            type(token.type),
            repr(token.repr)
        {
            switch(type)
            {
            case HASH:
                hash_type = token.hash_type;
                break;

            case NUMBER:
            case PERCENTAGE:
            case DIMENSION:
                n = token.n;
                break;

            case CV_FUNCTION:
            case CURLY_BLOCK:
            case ROUND_BLOCK:
            case SQUARE_BLOCK:
                value = std::vector<css_token>(token.value);
                break;

            default:;
            }
        }

        css_token& operator=(const css_token& token) = default;

        bool is_component_value() const
        {
            return type <= CV_FUNCTION;
        }

        std::string ident() const;
        std::string get_repr(bool insert_spaces = false) const;

        union {
            css_token_type type;
            int            ch; // used for <delim-token> or :;,()[]{}
        };

        // STRING, URL
        std::string& str()
        {
            return m_str;
        }
        const std::string& str() const
        {
            return m_str;
        }
        // HASH, IDENT, AT_KEYWORD, FUNCTION, CV_FUNCTION
        std::string& name()
        {
            return m_str;
        }
        const std::string& name() const
        {
            return m_str;
        }
        // DIMENSION
        std::string& unit()
        {
            return m_str;
        }
        const std::string& unit() const
        {
            return m_str;
        }

        struct number
        {
            float           number;      // NUMBER, PERCENTAGE, DIMENSION
            css_number_type number_type; // NUMBER, DIMENSION
        };
        union {
            css_hash_type hash_type; // HASH
            number        n;
        };
        std::vector<css_token> value; // CV_FUNCTION, XXX_BLOCK

        std::string repr; // https://www.w3.org/TR/css-syntax-3/#representation
    };

    using css_token_vector = std::vector<css_token>;
    std::string get_repr(const css_token_vector& tokens, int index = 0, int count = -1, bool insert_spaces = false);

    class css_tokenizer
    {
      public:
        css_tokenizer(const std::string& input) :
            str(input)
        {
        }

        css_token_vector tokenize();

      private:
        // Input stream. Valid UTF-8; no NUL bytes. https://www.w3.org/TR/css-syntax-3/#input-stream
        std::string str;

        // Index of the next input char.  https://www.w3.org/TR/css-syntax-3/#next-input-code-point
        int index = 0;

        // https://www.w3.org/TR/css-syntax-3/#current-input-code-point
        // This is needed to handle the situation when unconsume_char is called when index == str.size().
        // We need to distinguish between the situation when we just read the last char and
        // the situation when we already have been at the end and just read NUL.
        // If we don't do this tokenizer will loop forever on input "a".
        int current_char = 0;

      private:
        static bool is_whitespace(int ch);
        static bool is_non_printable_code_point(int ch);
        static bool is_ident_start_code_point(int ch);
        static bool is_ident_code_point(int ch);

        struct three_chars
        {
            int _1, _2, _3;
        };

        int         consume_char();
        void        unconsume_char();
        int         peek_char();
        three_chars peek_chars();

        void      consume_comments();
        int       consume_escaped_code_point();
        css_token consume_string_token(int ending_code_point);

        static bool would_start_ident_sequence(three_chars chars);
        std::string consume_ident_sequence();

        static bool   would_start_a_number(int x, int y, int z);
        static double convert_string_to_number(const std::string& str);
        double        consume_number(css_number_type& number_type);
        css_token     consume_numeric_token();

        void      consume_remnants_of_bad_url();
        css_token consume_url_token();

        css_token consume_ident_like_token();
        css_token consume_token();
    };

    void                    css_parse_error(const std::string& msg);
    inline css_token_vector tokenize(const std::string& str)
    {
        return css_tokenizer(str).tokenize();
    }

} // namespace litehtml

#endif // LITEHTML_CSS_TOKENIZER_H
