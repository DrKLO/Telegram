#include "html.h"
#include "stylesheet.h"
#include "css_parser.h"
#include "document.h"
#include "document_container.h"

namespace litehtml
{

    // ( <declaration> )  https://drafts.csswg.org/css-conditional-3/#typedef-supports-decl
    static bool eval_supports_declaration(const css_token_vector& tokens, document_container* container)
    {
        // a <supports-decl> holds exactly one declaration
        for(const auto& token : tokens)
        {
            if(token.ch == ';')
            {
                return false;
            }
        }
        // the declaration is supported if litehtml was able to parse it
        style st;
        st.add(tokens, "", container);
        return !st.empty();
    }

    // These return false when the syntax is invalid, which is not the same as a condition that is
    // false: an @supports rule with an invalid condition is invalid and its block is never applied.
    static bool parse_supports_condition(const css_token_vector& tokens, int& index, bool& result,
                                         document_container* container);

    // <supports-in-parens> = ( <supports-condition> ) | <supports-feature> | <general-enclosed>
    static bool parse_supports_in_parens(const css_token& token, bool& result, document_container* container)
    {
        // <general-enclosed> = <function-token> <any-value>? )
        // Any such function is unsupported, so it evaluates to false.
        if(token.type == CV_FUNCTION)
        {
            result = false;
            return true;
        }
        // everything but a block is not a <supports-in-parens> at all
        if(token.type != ROUND_BLOCK)
        {
            return false;
        }

        // ( <supports-condition> )
        int  index = 0;
        bool value = false;
        if(parse_supports_condition(token.value, index, value, container))
        {
            skip_whitespace(token.value, index);
            if(index == static_cast<int>(token.value.size()))
            {
                result = value;
                return true;
            }
        }
        // ( <declaration> ), or <general-enclosed> = ( <any-value>? ) which evaluates to false
        result = eval_supports_declaration(token.value, container);
        return true;
    }

    // https://drafts.csswg.org/css-conditional-3/#typedef-supports-condition
    // <supports-condition> = not <supports-in-parens>
    //                      | <supports-in-parens> [ and <supports-in-parens> ]*
    //                      | <supports-in-parens> [ or <supports-in-parens> ]*
    static bool parse_supports_condition(const css_token_vector& tokens, int& index, bool& result,
                                         document_container* container)
    {
        auto parse_operand = [&](bool& value) {
            skip_whitespace(tokens, index);
            if(!parse_supports_in_parens(at(tokens, index), value, container))
            {
                return false;
            }
            index++;
            return true;
        };
        auto next_ident = [&]() {
            skip_whitespace(tokens, index);
            return lowcase(at(tokens, index).ident());
        };

        if(next_ident() == "not")
        {
            index++;
            if(!parse_operand(result))
            {
                return false;
            }
            result = !result;
            return true;
        }

        if(!parse_operand(result))
        {
            return false;
        }

        std::string op = next_ident();
        if(op != "and" && op != "or")
        {
            return true; // a single <supports-in-parens>, the caller checks what follows it
        }
        // mixing `and` and `or` without parentheses is invalid, so the operator cannot change
        while(next_ident() == op)
        {
            index++;
            bool operand = false;
            if(!parse_operand(operand))
            {
                return false;
            }
            result = op == "and" ? result && operand : result || operand;
        }
        return true;
    }

    static bool eval_supports_condition(const css_token_vector& tokens, document_container* container)
    {
        int  index  = 0;
        bool result = false;
        if(!parse_supports_condition(tokens, index, result, container))
        {
            return false;
        }
        skip_whitespace(tokens, index);
        return index == static_cast<int>(tokens.size()) && result;
    }

    // https://www.w3.org/TR/css-syntax-3/#parse-a-css-stylesheet
    template <class Input> // Input == string or css_token_vector
    void css::parse_css_stylesheet(const Input& input, const std::string& baseurl, const std::shared_ptr<document>& doc,
                                   const media_query_list_list::ptr& media, bool top_level)
    {
        if(doc && media)
        {
            doc->add_media_list(media);
        }

        // To parse a CSS stylesheet, first parse a stylesheet.
        auto rules          = css_parser::parse_stylesheet(input, top_level);
        bool import_allowed = top_level;

        // Interpret all of the resulting top-level qualified rules as style rules, defined below.
        // If any style rule is invalid, or any at-rule is not recognized or is invalid according
        // to its grammar or context, it's a parse error. Discard that rule.
        for(const auto& rule : rules)
        {
            if(rule->type == raw_rule::qualified)
            {
                if(parse_style_rule(rule, baseurl, doc, media))
                {
                    import_allowed = false;
                }
                continue;
            }

            // Otherwise: at-rule
            switch(_id(lowcase(rule->name)))
            {
            case _charset_: // ignored  https://www.w3.org/TR/css-syntax-3/#charset-rule
                break;

            case _import_:
                if(import_allowed)
                {
                    parse_import_rule(rule, baseurl, doc, media);
                } else
                {
                    css_parse_error("incorrect placement of @import rule");
                }
                break;

            // https://www.w3.org/TR/css-conditional-3/#at-media
            // @media <media-query-list> { <stylesheet> }
            case _media_:
                {
                    if(rule->block.type != CURLY_BLOCK)
                    {
                        break;
                    }
                    auto new_media = media;
                    auto mq_list   = parse_media_query_list(rule->prelude, doc);
                    // An empty media query list evaluates to true.
                    // https://drafts.csswg.org/mediaqueries-5/#example-6f06ee45
                    if(!mq_list.empty())
                    {
                        new_media = std::make_shared<media_query_list_list>(media ? *media : media_query_list_list());
                        new_media->add(mq_list);
                    }
                    parse_css_stylesheet(rule->block.value, baseurl, doc, new_media, false);
                    import_allowed = false;
                    break;
                }

            // https://drafts.csswg.org/css-conditional-3/#at-supports
            // @supports <supports-condition> { <stylesheet> }
            case _supports_:
                {
                    if(rule->block.type != CURLY_BLOCK)
                    {
                        break;
                    }
                    auto condition = normalize(rule->prelude, f_componentize);
                    if(eval_supports_condition(condition, doc->container()))
                    {
                        parse_css_stylesheet(rule->block.value, baseurl, doc, media, false);
                    }
                    import_allowed = false;
                    break;
                }

            // https://drafts.csswg.org/css-cascade-5/#at-layer
            // @layer <layer-name># ;
            // @layer <layer-name>? { <stylesheet> }
            case _layer_:
                {
                    // Cascade layers are not implemented: a layer statement only declares layer order, so it
                    // is ignored, and the rules of a layer block are used as if they were not layered.
                    if(rule->block.type == CURLY_BLOCK)
                    {
                        parse_css_stylesheet(rule->block.value, baseurl, doc, media, false);
                        import_allowed = false;
                    }
                    // a layer statement rule is allowed before @import and does not disallow it
                    break;
                }

            default:
                css_parse_error("unrecognized rule @" + rule->name);
            }
        }
    }

    // https://drafts.csswg.org/css-cascade-5/#at-import
    // `layer` and `supports` are not supported
    // @import [ <url> | <string> ] <media-query-list>?
    void css::parse_import_rule(const raw_rule::ptr& rule, const std::string& baseurl,
                                const std::shared_ptr<document>& doc, const media_query_list_list::ptr& media)
    {
        auto tokens = rule->prelude;
        int  index  = 0;
        skip_whitespace(tokens, index);
        auto        tok = at(tokens, index);
        std::string url;
        auto        parse_string = [](const css_token& tok, std::string& str) {
            if(tok.type != STRING)
            {
                return false;
            }
            str = tok.str();
            return true;
        };
        bool ok = parse_url(tok, url) || parse_string(tok, url);
        if(!ok)
        {
            css_parse_error("invalid @import rule");
            return;
        }
        document_container* container = doc->container();
        std::string         css_text;
        std::string         css_baseurl = baseurl;
        container->import_css(css_text, url, css_baseurl);

        auto new_media = media;
        tokens         = slice(tokens, index + 1);
        auto mq_list   = parse_media_query_list(tokens, doc);
        if(!mq_list.empty())
        {
            new_media = std::make_shared<media_query_list_list>(media ? *media : media_query_list_list());
            new_media->add(mq_list);
        }

        parse_css_stylesheet(css_text, css_baseurl, doc, new_media, true);
    }

    // https://www.w3.org/TR/css-syntax-3/#style-rules
    bool css::parse_style_rule(const raw_rule::ptr& rule, const std::string& baseurl,
                               const std::shared_ptr<document>& doc, const media_query_list_list::ptr& media)
    {
        // The prelude of the qualified rule is parsed as a <selector-list>. If this returns failure, the entire style
        // rule is invalid.
        auto list = parse_selector_list(rule->prelude, strict_mode, doc->mode());
        if(list.empty())
        {
            css_parse_error("invalid selector");
            return false;
        }

        style::ptr style = std::make_shared<litehtml::style>(); // style block
        // The content of the qualified rule's block is parsed as a style block's contents.
        style->add(rule->block.value, baseurl, doc->container());

        for(const auto& sel : list)
        {
            sel->m_style       = style;
            sel->m_media_query = media;
            sel->calc_specificity();
            add_selector(sel);
        }
        return true;
    }

    void css::sort_selectors()
    {
        std::sort(m_selectors.begin(), m_selectors.end(),
                  [](const css_selector::ptr& v1, const css_selector::ptr& v2) { return (*v1) < (*v2); });
    }

} // namespace litehtml
