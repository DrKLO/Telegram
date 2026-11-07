#include "html.h"
#include "css_tokenizer.h"
#include "utf8_strings.h"

namespace litehtml
{

    void css_parse_error(const std::string& /*msg*/)
    {
        // printf("%s\n", msg.c_str());
    }

    std::string css_token::ident() const
    {
        if(type != IDENT)
        {
            return "";
        }
        return name().substr(0, 2) == "--" ? name() : lowcase(name());
    }

    char mirror(char c)
    {
        if(c == '{')
        {
            return '}';
        }
        if(c == '[')
        {
            return ']';
        }
        if(c == '(')
        {
            return ')';
        }
        return c;
    }

    std::string css_token::get_repr(bool insert_spaces) const
    {
        if(!is_component_value())
        {
            return repr;
        }

        using litehtml::get_repr;
        if(type == CV_FUNCTION)
        {
            return name() + '(' + get_repr(value, 0, -1, insert_spaces) + ')';
        }

        char opening_bracket = static_cast<char>(-type - 100);
        char closing_bracket = mirror(opening_bracket);
        return opening_bracket + get_repr(value, 0, -1, insert_spaces) + closing_bracket;
    }

    // concatenate string representations of tokens
    std::string get_repr(const css_token_vector& tokens, int index, int count, bool insert_spaces)
    {
        if(count == -1)
        {
            count = static_cast<int>(tokens.size()) - index;
        }
        std::string str;
        std::string space = insert_spaces ? " " : "";
        for(int i = index; i < index + count; i++)
        {
            str += tokens[i].get_repr(insert_spaces) + space;
        }
        if(insert_spaces)
        {
            remove(str, -1);
        }
        return str;
    }

    // https://www.w3.org/TR/css-syntax-3/#whitespace
    bool css_tokenizer::is_whitespace(int ch)
    {
        // NOTE: \r and \f are converted to \n in filter_code_points
        return ch == '\n' || ch == '\t' || ch == ' ';
    }

    // https://www.w3.org/TR/css-syntax-3/#non-printable-code-point
    bool css_tokenizer::is_non_printable_code_point(int ch)
    {
        return (ch >= 0 && ch <= 8) || ch == 0xB || (ch >= 0xE && ch <= 0x1F) || ch == 0x7F;
    }

    // https://www.w3.org/TR/css-syntax-3/#ident-start-code-point
    bool css_tokenizer::is_ident_start_code_point(int ch)
    {
        return is_letter(ch) || ch >= 0x80 || ch == '_';
    }

    // https://www.w3.org/TR/css-syntax-3/#ident-code-point
    bool css_tokenizer::is_ident_code_point(int ch)
    {
        return is_ident_start_code_point(ch) || is_digit(ch) || ch == '-';
    }

    // Consume the next input code point. Return the current input code point.
    // When we know that next input char is ASCII and not NUL, we can just write str[index++] instead.
    int css_tokenizer::consume_char()
    {
        // NOTE: if str[index] == 0 index is not incremented
        return current_char = read_utf8_char(str, index);
    }

    // https://www.w3.org/TR/css-syntax-3/#reconsume-the-current-input-code-point
    // "reconsume" is not a good name - it should be called unconsume (the char will actually be reconsumed later when
    // consume_char is called). When we know that current input char is ASCII and index != 0, we can just write index--
    // instead.
    void css_tokenizer::unconsume_char()
    {
        // see comment for current_char
        if(current_char == 0)
        {
            return;
        }

        // NOTE: if index == 0 index is not decremented
        prev_utf8_char(str, index);
    }

    int css_tokenizer::peek_char()
    {
        int i = index;
        return read_utf8_char(str, i);
    }

    css_tokenizer::three_chars css_tokenizer::peek_chars()
    {
        three_chars chars = {};
        int         i     = index;

        chars._1 = read_utf8_char(str, i);
        chars._2 = read_utf8_char(str, i);
        chars._3 = read_utf8_char(str, i);
        return chars;
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-comments
    void css_tokenizer::consume_comments()
    {
        while(true)
        {
            if(str[index] == '/' && str[index + 1] == '*')
            {
                int i = static_cast<int>(str.find("*/", index + 2));

                if(i != -1)
                {
                    index = i + 2;
                } else
                {
                    index = static_cast<int>(str.size());
                    css_parse_error("eof in comment");
                    break;
                }
            } else
            {
                break;
            }
        }
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-escaped-code-point
    // It assumes that the U+005C (\) has already been consumed and that the next input code point
    // is not a newline (see https://www.w3.org/TR/css-syntax-3/#check-if-two-code-points-are-a-valid-escape).
    int css_tokenizer::consume_escaped_code_point()
    {
        // Consume the next input code point.
        int ch = consume_char();

        if(is_hex_digit(ch))
        {
            int number = digit_value(ch);
            // Consume as many hex digits as possible, but no more than 5.
            int max = 5;
            while(max-- > 0 && is_hex_digit(str[index]))
            {
                ch     = consume_char();
                number = number * 16 + digit_value(ch);
            }
            // If the next input code point is whitespace, consume it as well.
            if(is_whitespace(str[index]))
            {
                consume_char();
            }
            // If this number is zero, or is for a surrogate, or is greater than the maximum allowed code point
            if(number == 0 || is_surrogate(number) || number > 0x10FFFF)
            {
                return 0xFFFD;
            }
            // Otherwise, return the code point with that value.
            return number;
        }
        if(ch == 0) // EOF
        {
            // This is a parse error. Return U+FFFD.
            css_parse_error("eof in escaped codepoint");
            return 0xFFFD;
        }
        // anything else
        // Return the current input code point.
        return ch;
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-string-token
    css_token css_tokenizer::consume_string_token(int ending_code_point)
    {
        // Initially create a <string-token> with its value set to the empty string.
        css_token token(STRING);

        while(true)
        {
            // Repeatedly consume the next input code point from the stream:
            int ch = consume_char();
            switch(ch)
            {
            case 0: // EOF
                // This is a parse error. Return the <string-token>.
                css_parse_error("eof in string");
                return token;
            case '\n':
                // This is a parse error. Reconsume the current input code point, create a <bad-string-token>, and
                // return it.
                css_parse_error("newline in string");
                unconsume_char();
                return {BAD_STRING};
            case '\\':
                // If the next input code point is EOF, do nothing.
                if(str[index] == 0)
                {
                    break;
                }
                // Otherwise, if the next input code point is a newline, consume it.
                else if(str[index] == '\n')
                {
                    index++;
                }
                // Otherwise, (the stream starts with a valid escape) consume an escaped code point and
                // append the returned code point to the <string-token>’s value.
                else
                {
                    append_char(token.str(), consume_escaped_code_point());
                }
                break;
            default:
                if(ch == ending_code_point)
                {
                    return token;
                } else // anything else
                {
                    // Append the current input code point to the <string-token>’s value.
                    append_char(token.str(), ch);
                }
                break;
            }
        }
    }

    // https://www.w3.org/TR/css-syntax-3/#would-start-an-identifier
    bool css_tokenizer::would_start_ident_sequence(three_chars chars)
    {
        int c1 = chars._1;
        int c2 = chars._2;
        int c3 = chars._3;

        if(c1 == '-')
        {
            // If the second code point is an ident-start code point or a U+002D HYPHEN-MINUS, or
            // the second and third code points are a valid escape, return true. Otherwise, return false.
            return is_ident_start_code_point(c2) || c2 == '-' || (c2 == '\\' && c3 != '\n');
        }
        if(is_ident_start_code_point(c1))
        {
            return true;
        }
        if(c1 == '\\')
        {
            // If the first and second code points are a valid escape, return true. Otherwise, return false.
            return c2 != '\n';
        }
        return false;
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-name
    std::string css_tokenizer::consume_ident_sequence()
    {
        std::string result;

        while(true)
        {
            // Repeatedly consume the next input code point from the stream:
            int ch = consume_char();

            if(is_ident_code_point(ch))
            {
                append_char(result, ch); // Append the code point to result.
            }

            // else if the stream starts with a valid escape
            // NOTE: the wording is confusing because ch is not in the input stream anymore (it has been consumed)
            else if(ch == '\\' && str[index] != '\n')
            {
                // Consume an escaped code point. Append the returned code point to result.
                append_char(result, consume_escaped_code_point());
            }

            else
            {
                // Reconsume the current input code point. Return result.
                unconsume_char();
                return result;
            }
        }
    }

    // https://www.w3.org/TR/css-syntax-3/#starts-with-a-number
    bool css_tokenizer::would_start_a_number(int x, int y, int z)
    {
        if(x == '+' || x == '-')
        {
            // If the second code point is a digit, return true.
            if(is_digit(y))
            {
                return true;
            }
            // Otherwise, if the second code point is a U+002E (.) and the third code point is a digit, return true.
            if(y == '.' && is_digit(z))
            {
                return true;
            }
            // Otherwise, return false.
            return false;
        }
        if(x == '.')
        {
            // If the second code point is a digit, return true. Otherwise, return false.
            return is_digit(y);
        }
        return is_digit(x);
    }

    // https://www.w3.org/TR/css-syntax-3/#convert-string-to-number
    double css_tokenizer::convert_string_to_number(const std::string& str)
    {
        const char* p = str.c_str();

        // Divide the string into seven components, in order from left to right:

        // 1. A sign: a single U+002B (+) or U+002D (-), or the empty string.
        //    Let s be the number -1 if the sign is U+002D (-); otherwise, let s be the number 1.
        double s = 1;
        if(*p == '-')
        {
            s = -1, p++;
        } else if(*p == '+')
        {
            p++;
        }

        // 2. An integer part: zero or more digits. If there is at least one digit, let i be the number formed by
        //    interpreting the digits as a base-10 integer; otherwise, let i be the number 0.
        double i = 0;
        while(is_digit(*p))
        {
            i = i * 10 + digit_value(*p++);
        }

        // 3. A decimal point: a single U+002E (.), or the empty string.
        if(*p == '.')
        {
            p++;
        }

        // 4. A fractional part: zero or more digits. If there is at least one digit, let f be the number formed by
        //    interpreting the digits as a base-10 integer and d be the number of digits;
        //    otherwise, let f and d be the number 0.
        double f = 0;
        double d = 0;
        while(is_digit(*p))
        {
            f = f * 10 + digit_value(*p++), d++;
        }

        // 5. An exponent indicator: a single U+0045 (E) or U+0065 (e), or the empty string.
        if(*p == 'e' || *p == 'E')
        {
            p++;
        }

        // 6. An exponent sign: a single U+002B (+) or U+002D (-), or the empty string.
        //    Let t be the number -1 if the sign is U+002D (-); otherwise, let t be the number 1.
        double t = 1;
        if(*p == '-')
        {
            t = -1, p++;
        } else if(*p == '+')
        {
            p++;
        }

        // 7. An exponent: zero or more digits. If there is at least one digit, let e be the number formed by
        //    interpreting the digits as a base-10 integer; otherwise, let e be the number 0.
        double e = 0;
        while(is_digit(*p))
        {
            e = e * 10 + digit_value(*p++);
        }

        // Return the number s·(i + f·10ᐨᵈ)·10ᵗᵉ.
        return s * (i + f * pow(10, -d)) * pow(10, t * e);
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-number
    double css_tokenizer::consume_number(css_number_type& type)
    {
        // 1. Initially set type to "integer". Let repr be the empty string.
        type = css_number_integer;
        std::string repr;

        // 2. If the next input code point is U+002B (+) or U+002D (-), consume it and append it to repr.
        if(is_one_of(str[index], '+', '-'))
        {
            append_char(repr, str[index++]);
        }

        // 3. While the next input code point is a digit, consume it and append it to repr.
        while(is_digit(str[index]))
        {
            append_char(repr, str[index++]);
        }

        // 4. If the next 2 input code points are U+002E (.) followed by a digit, then:
        if(str[index] == '.' && is_digit(str[index + 1]))
        {
            // 1. Consume them.
            // 2. Append them to repr.
            append_char(repr, str[index++]);
            append_char(repr, str[index++]);
            // 3. Set type to "number".
            type = css_number_number;
            // 4. While the next input code point is a digit, consume it and append it to repr.
            while(is_digit(str[index]))
            {
                append_char(repr, str[index++]);
            }
        }

        // 5. If the next 2 or 3 input code points are U+0045 (E) or U+0065 (e),
        //    optionally followed by U+002D (-) or U+002B (+), followed by a digit, then:
        bool a = lowcase(str[index]) == 'e' && is_one_of(str[index + 1], '+', '-') && is_digit(str[index + 2]);
        bool b = lowcase(str[index]) == 'e' && is_digit(str[index + 1]);

        if(a || b)
        {
            // 1. Consume them.
            // 2. Append them to repr.
            append_char(repr, str[index++]);
            append_char(repr, str[index++]);
            if(a)
            {
                append_char(repr, str[index++]);
            }
            // 3. Set type to "number".
            type = css_number_number;
            // 4. While the next input code point is a digit, consume it and append it to repr.
            while(is_digit(str[index]))
            {
                append_char(repr, str[index++]);
            }
        }

        // 6. Convert repr to a number, and set the value to the returned value.
        double value = convert_string_to_number(repr);

        // 7. Return value and type.
        return value;
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-numeric-token
    css_token css_tokenizer::consume_numeric_token()
    {
        // Consume a number and let number be the result.
        css_number_type type;

        auto number = static_cast<float>(consume_number(type));

        // If the next 3 input code points would start an ident sequence, then:
        if(would_start_ident_sequence(peek_chars()))
        {
            // 1. Create a <dimension-token> with the same value and type flag as number, and
            //    a unit set initially to the empty string.
            css_token token(DIMENSION, number, type);

            // 2. Consume an ident sequence. Set the <dimension-token>’s unit to the returned value.
            token.unit() = consume_ident_sequence();

            // 3. Return the <dimension-token>.
            return token;
        }

        // Otherwise, if the next input code point is U+0025 (%), consume it.
        // Create a <percentage-token> with the same value as number, and return it.
        if(str[index] == '%')
        {
            index++;
            return {PERCENTAGE, number}; // NOTE: number_type is unused in <percentage-token>
        }

        // Otherwise, create a <number-token> with the same value and type flag as number, and return it.
        return {NUMBER, number, type};
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-remnants-of-bad-url
    void css_tokenizer::consume_remnants_of_bad_url()
    {
        while(true)
        {
            // Repeatedly consume the next input code point from the stream:
            int ch = consume_char();
            if(ch == ')' || ch == 0) // ')' or EOF
            {
                return;
            }
            // else if the input stream starts with a valid escape
            // NOTE: the wording is confusing because ch is not in the input stream anymore (it has been consumed)
            if(ch == '\\' && str[index] != '\n')
            {
                consume_escaped_code_point();
            }
            // anything else: Do nothing.
        }
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-url-token
    css_token css_tokenizer::consume_url_token()
    {
        // Initially create a <url-token> with its value set to the empty string.
        css_token token(URL);

        // Consume as much whitespace as possible.
        while(is_whitespace(str[index]))
        {
            index++;
        }

        while(true)
        {
            // Repeatedly consume the next input code point from the stream:
            int ch = consume_char();
            switch(ch)
            {
            case ')':
                // Return the <url-token>.
                return token;

            case 0: // EOF
                // This is a parse error. Return the <url-token>.
                css_parse_error("eof in unquoted url");
                return token;

            case '\n':
            case '\t':
            case ' ':
                // Consume as much whitespace as possible.
                while(is_whitespace(str[index]))
                {
                    index++;
                }
                // If the next input code point is U+0029 ()) or EOF, consume it and return the <url-token>
                // (if EOF was encountered, this is a parse error);
                if(str[index] == ')' || str[index] == 0)
                {
                    if(str[index] == 0)
                    {
                        css_parse_error("eof in unquoted url");
                    } else
                    {
                        index++; // consume ')'
                    }
                    return token;
                }
                // otherwise, consume the remnants of a bad url, create a <bad-url-token>, and return it.
                consume_remnants_of_bad_url();
                return {BAD_URL};

            case '"':
            case '\'':
            case '(':
            bad_url:
                // This is a parse error. Consume the remnants of a bad url, create a <bad-url-token>, and return it.
                css_parse_error("invalid char in unquoted url");
                consume_remnants_of_bad_url();
                return {BAD_URL};

            case '\\':
                // If the stream starts with a valid escape, consume an escaped code point and
                // append the returned code point to the <url-token>’s value.
                if(str[index] != '\n')
                {
                    append_char(token.str(), consume_escaped_code_point());
                }
                // Otherwise, this is a parse error. Consume the remnants of a bad url, create a <bad-url-token>, and
                // return it.
                else
                {
                    css_parse_error("escaped newline in unquoted url");
                    consume_remnants_of_bad_url();
                    return {BAD_URL};
                }
                break;

            default:
                if(is_non_printable_code_point(ch))
                {
                    goto bad_url;
                } else // anything else
                {
                    // Append the current input code point to the <url-token>’s value.
                    append_char(token.str(), ch);
                }
                break;
            }
        }
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-ident-like-token
    css_token css_tokenizer::consume_ident_like_token()
    {
        // Consume an ident sequence, and let string be the result.
        auto string = consume_ident_sequence();

        // If string’s value is an ASCII case-insensitive match for "url", and the next input code point is
        // U+0028 ((), consume it.
        if(lowcase(string) == "url" && str[index] == '(')
        {
            index++; // consume '('

            while(is_whitespace(str[index])) // not looking for 2 spaces, see next comment
            {
                index++;
            }

            if(is_one_of(str[index], '"', '\''))
            {
                // This is not exactly what standard says, but equivalent. The purpose is to preserve a whitespace
                // token.
                if(is_whitespace(str[index - 1]))
                {
                    index--;
                }
                return {FUNCTION, string};
            }
            // Otherwise, consume a url token, and return it.
            return consume_url_token();
        }

        // Otherwise, if the next input code point is U+0028 ((), consume it.
        // Create a <function-token> with its value set to string and return it.
        if(str[index] == '(')
        {
            index++;
            return {FUNCTION, string};
        }

        // Otherwise, create an <ident-token> with its value set to string and return it.
        return {IDENT, string};
    }

    // https://www.w3.org/TR/css-syntax-3/#consume-token
    css_token css_tokenizer::consume_token()
    {
        consume_comments();

        css_token token;
        int       start = index;

        // Consume the next input code point.
        int         ch   = consume_char();
        three_chars next = {};

        switch(ch)
        {
            // whitespace
        case '\n':
        case '\t':
        case ' ':
            // Consume as much whitespace as possible. Return a <whitespace-token>.
            while(is_whitespace(str[index]))
            {
                index++;
            }
            token.type = WHITESPACE;
            break;

        case '"':
        case '\'':
            token = consume_string_token(ch);
            break;

        case '#':
            // If the next input code point is an ident code point or the next two input code points are a valid escape,
            // then:
            if(is_ident_code_point(peek_char()) || (str[index] == '\\' && str[index + 1] != '\n'))
            {
                // 1. Create a <hash-token>.
                token.type = HASH;
                // 2. If the next 3 input code points would start an ident sequence, set the <hash-token>’s type flag to
                // "id".
                token.hash_type = would_start_ident_sequence(peek_chars()) ? css_hash_id : css_hash_unrestricted;
                // 3. Consume an ident sequence, and set the <hash-token>’s value to the returned string.
                token.name() = consume_ident_sequence();
                // 4. Return the <hash-token>.
            } else
            {
                // Otherwise, return a <delim-token> with its value set to the current input code point.
                token.ch = ch;
            }
            break;

        case '+':
        case '.':
            // If the input stream starts with a number, reconsume the current input code point, consume a numeric
            // token, and return it.
            next = peek_chars();
            if(would_start_a_number(ch, next._1, next._2))
            {
                unconsume_char();
                token = consume_numeric_token();
            } else
            {
                // Otherwise, return a <delim-token> with its value set to the current input code point.
                token.ch = ch;
            }
            break;

        case '-':
            // If the input stream starts with a number, reconsume the current input code point, consume a numeric
            // token, and return it.
            next = peek_chars();
            if(would_start_a_number(ch, next._1, next._2))
            {
                unconsume_char();
                token = consume_numeric_token();
            }
            // Otherwise, if the next 2 input code points are U+002D U+003E (->), consume them and return a <CDC-token>.
            else if(next._1 == '-' && next._2 == '>')
            {
                index      += 2;
                token.type  = CDC;
            }
            // Otherwise, if the input stream starts with an ident sequence, reconsume the current input code point,
            // consume an ident-like token, and return it.
            else if(would_start_ident_sequence({ch, next._1, next._2}))
            {
                unconsume_char();
                token = consume_ident_like_token();
            } else
            {
                // Otherwise, return a <delim-token> with its value set to the current input code point.
                token.ch = ch;
            }
            break;

        case '<':
            // If the next 3 input code points are !--, consume them and return a <CDO-token>.
            if(match(str, index, "!--"))
            {
                index      += 3;
                token.type  = CDO;
            } else
            {
                // Otherwise, return a <delim-token> with its value set to the current input code point.
                token.ch = ch;
            }
            break;

        case '@':
            // If the next 3 input code points would start an ident sequence, consume an ident sequence,
            // create an <at-keyword-token> with its value set to the returned value, and return it.
            if(would_start_ident_sequence(peek_chars()))
            {
                token.type   = AT_KEYWORD;
                token.name() = consume_ident_sequence();
            } else
            {
                // Otherwise, return a <delim-token> with its value set to the current input code point.
                token.ch = ch;
            }
            break;

        case '\\':
            // If the input stream starts with a valid escape, reconsume the current input code point,
            // consume an ident-like token, and return it.
            if(str[index] != '\n')
            {
                unconsume_char();
                token = consume_ident_like_token();
            } else
            {
                // Otherwise, this is a parse error. Return a <delim-token> with its value set to the current input code
                // point.
                css_parse_error("escaped newline outside a string");
                token.ch = ch;
            }
            break;

        case 0: // EOF
            token.type = _EOF;
            break;

        default:
            if(is_digit(ch))
            {
                // Reconsume the current input code point, consume a numeric token, and return it.
                unconsume_char();
                token = consume_numeric_token();
            } else if(is_ident_start_code_point(ch))
            {
                // Reconsume the current input code point, consume an ident-like token, and return it.
                unconsume_char();
                token = consume_ident_like_token();
            } else // anything else
            {
                // Return a <delim-token> with its value set to the current input code point.
                token.ch = ch; // NOTE: :;,()[]{} tokens are also handled here
            }
        }

        token.repr = str.substr(start, index - start);
        return token;
    }

    css_token_vector css_tokenizer::tokenize()
    {
        css_token_vector tokens;
        while(true)
        {
            css_token token = consume_token();
            if(token.type == EOF)
            {
                break;
            }
            tokens.push_back(token);
        }
        return tokens;
    }

} // namespace litehtml
