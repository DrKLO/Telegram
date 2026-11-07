#include "html.h"
#include "css_selector.h"
#include "css_parser.h"
#include "internal.h"
#include "document.h"
#include <set>

namespace litehtml
{

    void css_selector::calc_specificity()
    {
        if(m_right.m_tag != star_id)
        {
            m_specificity.d = 1;
        }
        for(const auto& attr : m_right.m_attrs)
        {
            // :where() and its arguments add nothing  https://www.w3.org/TR/selectors-4/#zero-matches
            if(attr.type == select_pseudo_class && attr.name == _where_)
            {
                continue;
            }
            if(attr.type == select_id)
            {
                m_specificity.b++;
            } else
            {
                m_specificity.c++;
            }
        }
        if(m_left)
        {
            m_left->calc_specificity();
            m_specificity += m_left->m_specificity;
        }
    }

    void css_selector::add_media_to_doc(document* doc) const
    {
        if(m_media_query && doc)
        {
            doc->add_media_list(m_media_query);
        }
    }

    // https://www.w3.org/TR/selectors-4/#type-nmsp
    // <ns-prefix> = [ <ident-token> | '*' ]? '|'     https://www.w3.org/TR/selectors-4/#typedef-ns-prefix
    std::string parse_ns_prefix(const css_token_vector& tokens, int& index)
    {
        const auto& a = at(tokens, index);
        const auto& b = at(tokens, index + 1);

        if(a.ch == '|')
        {
            index++;
            return "";
        }

        if((a.type == IDENT || a.ch == '*') && b.ch == '|')
        {
            index += 2;
            return a.type == IDENT ? a.name() : "*";
        }

        return "";
    }

    struct wq_name
    {
        std::string prefix;
        std::string name;
    };

    // <wq-name> = <ns-prefix>? <ident-token>
    // Whitespace is forbidden between any of the components of a <wq-name>.
    wq_name parse_wq_name(const css_token_vector& tokens, int& index)
    {
        int         start  = index;
        std::string prefix = parse_ns_prefix(tokens, index);

        auto tok = at(tokens, index);
        if(tok.type == IDENT)
        {
            index++;
            return {prefix, tok.name()};
        }
        // restore index to before <ns-prefix> if failed to parse <ident-token>
        index = start;

        // handle situation when a name is erroneously parsed as prefix, eg. [x|=a]
        tok = at(tokens, index);
        if(tok.type == IDENT)
        {
            index++;
            return {"", tok.name()};
        }

        return {};
    }

    // https://www.w3.org/TR/selectors-4/#typedef-type-selector
    // <type-selector> = <wq-name> | <ns-prefix>? '*'
    // <wq-name> = <ns-prefix>? <ident-token>
    // So:
    // <type-selector> = <ns-prefix>? [ <ident-token> | '*' ]
    wq_name parse_type_selector(const css_token_vector& tokens, int& index)
    {
        int         start  = index;
        std::string prefix = parse_ns_prefix(tokens, index);

        const auto& tok = at(tokens, index);
        if(tok.type == IDENT || tok.ch == '*')
        {
            index++;
            std::string name = tok.type == IDENT ? tok.name() : "*";
            // type selector is always ASCII-case-insensitive for HTML documents, regardless of document mode (quirks/no
            // quirks)
            return {lowcase(prefix), lowcase(name)};
        }
        // restore index to before <ns-prefix> if failed to parse <ident-token> or '*'
        index = start;
        return {};
    }

    // <attr-matcher> = [ '~' | '|' | '^' | '$' | '*' ]? '='
    bool parse_attr_matcher(const css_token_vector& tokens, int& index, attr_matcher& matcher)
    {
        const auto& a = at(tokens, index);
        const auto& b = at(tokens, index + 1);

        if(a.ch == '=')
        {
            index++;
            matcher = attribute_equals;
            return true;
        }

        if(!is_one_of(a.ch, '~', '|', '^', '$', '*') || b.ch != '=')
        {
            return false;
        }

        index   += 2;
        matcher  = static_cast<attr_matcher>(a.ch);
        return true;
    }

    // https://www.w3.org/TR/selectors-4/#typedef-attribute-selector
    // <attribute-selector> = '[' <wq-name> ']' |
    //                        '[' <wq-name> <attr-matcher> [ <string-token> | <ident-token> ] <attr-modifier>? ']'
    // <attr-matcher> = [ '~' | '|' | '^' | '$' | '*' ]? '='
    // <attr-modifier> = i | s
    css_attribute_selector parse_attribute_selector(const css_token& block)
    {
        css_attribute_selector selector;

        const css_token_vector& tokens = block.value;
        int                     index  = 0;

        // <wq-name>
        skip_whitespace(tokens, index);
        wq_name wq_name = parse_wq_name(tokens, index);
        if(wq_name.name == "")
        {
            return {};
        }

        // attribute name in attribute selector is ASCII case-insensitive for HTML documents, regardless of document
        // mode (quirks/no quirks)
        auto prefix = lowcase(wq_name.prefix);
        auto name   = lowcase(wq_name.name);

        skip_whitespace(tokens, index);
        if(index == static_cast<int>(tokens.size())) // [name]
        {
            selector.type    = select_attr;
            selector.prefix  = _id(prefix);
            selector.name    = _id(name);
            selector.matcher = attribute_exists;
            return selector;
        }

        // <attr-matcher>
        skip_whitespace(tokens, index);
        attr_matcher matcher;
        if(!parse_attr_matcher(tokens, index, matcher))
        {
            return {};
        }

        // <string-token> | <ident-token>
        skip_whitespace(tokens, index);
        const css_token& value = at(tokens, index);
        if(value.type != STRING && value.type != IDENT)
        {
            return {};
        }
        index++;

        // <attr-modifier>?
        skip_whitespace(tokens, index);
        char             modifier = 0;
        const css_token& tok      = at(tokens, index);
        if(tok.type == IDENT)
        {
            if(tok.ident() == "s")
            {
                modifier = 's';
            } else if(tok.ident() == "i")
            {
                modifier = 'i';
            } else
            {
                return {}; // junk at the end of attribute selector
            }
            index++;
        }

        skip_whitespace(tokens, index);
        if(index != static_cast<int>(tokens.size()))
        {
            return {}; // junk at the end of attribute selector
        }

        // https://html.spec.whatwg.org/multipage/semantics-other.html#case-sensitivity-of-selectors
        // Attribute selectors on an HTML element in an HTML document must treat the values
        // of attributes with the following names as ASCII case-insensitive (unless s modifier is specified):
        static string_vector special_attributes = {
            "accept",     "accept-charset", "align",     "alink",    "axis",     "bgcolor", "charset",
            "checked",    "clear",          "codetype",  "color",    "compact",  "declare", "defer",
            "dir",        "direction",      "disabled",  "enctype",  "face",     "frame",   "hreflang",
            "http-equiv", "lang",           "language",  "link",     "media",    "method",  "multiple",
            "nohref",     "noresize",       "noshade",   "nowrap",   "readonly", "rel",     "rev",
            "rules",      "scope",          "scrolling", "selected", "shape",    "target",  "text",
            "type",       "valign",         "valuetype", "vlink",
        };

        selector.type           = select_attr;
        selector.prefix         = _id(prefix);
        selector.name           = _id(name);
        selector.matcher        = matcher;
        selector.caseless_match = modifier == 'i' || (!modifier && name in special_attributes);
        selector.value          = selector.caseless_match ? lowcase(value.str()) : value.str();
        return selector;
    }

    struct an_b
    {
        int  a, b;
        bool valid;
        an_b() :
            a(),
            b(),
            valid(false)
        {
        }
        an_b(int a, int b) :
            a(a),
            b(b),
            valid(true)
        {
        }
    };

    // NOTE: "+ 5" is not valid, and strtol correctly fails to parse it
    bool to_int(const std::string& s, int& number)
    {
        if(s == "")
        {
            return false;
        }

        const char* ptr = s.c_str();
        char*       end;
        int         n = strtol(ptr, &end, 10);

        if(end != ptr + s.size())
        {
            return false;
        }

        number = n;
        return true;
    }

    // https://www.w3.org/TR/css-syntax-3/#anb-syntax
    // I don't use the formal grammar because it creates a lot of unnecessary complexity.
    // Deviations from the standard:
    // * escapes are not allowed
    // * comments are allowed inside numbers and identifiers: ev/**/en
    an_b parse_an_b(std::string s)
    {
        lcase(trim(s));
        if(s == "even")
        {
            return {2, 0};
        }
        if(s == "odd")
        {
            return {2, 1};
        }

        int a, b;

        int i = static_cast<int>(s.find('n'));
        if(i == -1)
        {
            if(!to_int(s, b))
            {
                return {};
            }
            return {0, b};
        }

        auto str_a = s.substr(0, i);
        auto str_b = s.substr(i + 1);

        if(is_one_of(str_a, "", "+", "-"))
        {
            a = str_a == "-" ? -1 : 1;
        } else
        {
            if(!to_int(str_a, a))
            {
                return {};
            }
        }

        trim(str_b); // spaces after n are allowed: 2n + 3
        if(str_b != "")
        {
            if(str_b[0] == '+' || str_b[0] == '-')
            {
                while(is_whitespace(str_b[1]))
                {
                    str_b.erase(1, 1); // spaces after sign are allowed
                }
            }

            if(!to_int(str_b, b))
            {
                return {};
            }
        } else
        {
            b = 0;
        }

        return {a, b};
    }

    int find_of_keyword(const css_token_vector& tokens)
    {
        for(int i = 0; i < static_cast<int>(tokens.size()); i++)
        {
            if(tokens[i].ident() == "of")
            {
                return i;
            }
        }
        return -1;
    }

    // :nth-child(An+B [of S]?)      https://www.w3.org/TR/selectors-4/#the-nth-child-pseudo
    // :nth-last-child(An+B [of S]?)
    //    where S is a forgiving <complex-selector-list>
    //
    // :nth-of-type(An+B)            https://www.w3.org/TR/selectors-4/#the-nth-of-type-pseudo
    // :nth-last-of-type(An+B)
    //
    css_attribute_selector parse_nth_child(const css_token& token, bool of_keyword, document_mode mode)
    {
        css_attribute_selector selector(select_pseudo_class, lowcase(token.name()));

        const auto& tokens = token.value;

        // find "of" keyword
        int i = of_keyword ? find_of_keyword(tokens) : -1;
        if(i >= 0)
        {
            const auto& selector_tokens = slice(tokens, i + 1);

            // The standard doesn't specify if pseudo-elements are allowed in this selector list.
            // But specifying them will make selector match nothing anyway because
            // "The structural pseudo-classes only apply to elements in the document tree;
            // they must never match pseudo-elements." https://www.w3.org/TR/selectors-4/#structural-pseudos
            // So I parse as if they were not allowed.
            selector.selector_list =
                parse_selector_list(selector_tokens, forgiving_mode + forbid_pseudo_elements, mode);
            // NOTE: selector_list may be empty, this does not invalidate the selector.

            // Chrome/Firefox behavior differs from the standard: they treat S as unforgiving and allow pseudo-elements.
            // NOTE: :is(), which also accepts <forgiving-selector-list>, is handled correctly by Chrome/Firefox.
            // Use this code instead of above to match Chrome/Firefox behavior:
            // selector.selector_list = parse_selector_list(selector_tokens, strict_mode);
            // if (selector.selector_list.empty()) return {};
        }

        // get string representation of everything between "nth-child(" and "of" or ")", except for comments
        std::string str = get_repr(tokens, 0, i); // Note: i == -1 works as expected

        an_b x = parse_an_b(str);
        if(!x.valid)
        {
            return {};
        }

        selector.a = x.a;
        selector.b = x.b;

        return selector;
    }

    css_attribute_selector parse_function_pseudo_class(const css_token& token, document_mode mode)
    {
        std::string name = lowcase(token.name());
        if(name == "nth-child" || name == "nth-last-child")
        {
            return parse_nth_child(token, true, mode);
        }
        if(name == "nth-of-type" || name == "nth-last-of-type")
        {
            return parse_nth_child(token, false, mode);
        }
        // https://www.w3.org/TR/selectors-4/#matches
        // https://www.w3.org/TR/selectors-4/#zero-matches
        // :is() and :where() differ only in specificity, see css_selector::calc_specificity.
        if(name == "is" || name == "where")
        {
            css_attribute_selector selector(select_pseudo_class, name);
            // "taking a <forgiving-selector-list> as its sole argument"
            // "Pseudo-elements... are not valid within :is()."
            selector.selector_list = parse_selector_list(token.value, forgiving_mode + forbid_pseudo_elements, mode);
            return selector;
        }
        if(name == "not") // https://www.w3.org/TR/selectors-4/#negation
        {
            css_attribute_selector selector(select_pseudo_class, name);
            // "taking a selector list as an argument"
            // "Pseudo-elements... are not valid within :not()."
            selector.selector_list = parse_selector_list(token.value, strict_mode + forbid_pseudo_elements, mode);
            if(selector.selector_list.empty())
            {
                return {};
            }
            return selector;
        }
        if(name == "lang") // https://www.w3.org/TR/selectors-4/#the-lang-pseudo
        {
            css_attribute_selector selector(select_pseudo_class, name);
            selector.value = get_repr(token.value);
            return selector;
        }
        return {};
    }

    // simple = non-functional (without parentheses)
    bool is_supported_simple_pseudo_class(const std::string& name)
    {
        static std::set<std::string> supported_simple_pseudo_classes = {
            // Location Pseudo-classes  https://www.w3.org/TR/selectors-4/#location
            "any-link",
            "link",
            "visited",
            "local-link",
            "target",
            "target-within",
            "scope",
            // User Action Pseudo-classes  https://www.w3.org/TR/selectors-4/#useraction-pseudos
            "hover",
            "active",
            "focus",
            "focus-visible",
            "focus-within",
            // Form state pseudo-classes: matched against the checked/disabled
            // ATTRIBUTES kept in sync by the embedding application (litehtml
            // has no UI state of its own).
            "checked",
            "disabled",
            // Tree-Structural pseudo-classes  https://www.w3.org/TR/selectors-4/#structural-pseudos
            "root",
            "empty",
            "first-child",
            "last-child",
            "only-child",
            "first-of-type",
            "last-of-type",
            "only-of-type",
            // Shadow-tree pseudo-class  https://www.w3.org/TR/css-scoping-1/#host-selector
            // Shadow trees are not implemented, so :host never matches, which is what a browser does
            // for :host outside a shadow tree. Listed here so that a rule such as `:root, :host { ... }`
            // keeps its other selectors instead of being dropped entirely.
            "host",
        };
        return supported_simple_pseudo_classes.count(lowcase(name)) == 1;
    }

    // https://www.w3.org/TR/selectors-4/#typedef-pseudo-class-selector
    // <pseudo-class-selector> = ':' <ident-token> |
    //                           ':' <function-token> <any-value> ')'
    //                           where <ident-token> is not before, after, first-line or first-letter
    css_attribute_selector parse_pseudo_class(const css_token_vector& tokens, int& index, document_mode mode)
    {
        const auto& a = at(tokens, index);
        const auto& b = at(tokens, index + 1);

        if(a.ch != ':')
        {
            return {};
        }

        if(b.type == IDENT)
        {
            // unsupported pseudo-classes must be treated as invalid:  https://www.w3.org/TR/selectors-4/#w3c-partial
            if(!is_supported_simple_pseudo_class(b.ident()))
            {
                return {};
            }

            index += 2;
            return {select_pseudo_class, b.ident()};
        }
        if(b.type == CV_FUNCTION)
        {
            css_attribute_selector sel = parse_function_pseudo_class(b, mode);
            if(sel)
            {
                index += 2;
            }
            return sel;
        }
        return {};
    }

    // https://www.w3.org/TR/selectors-4/#typedef-subclass-selector
    // <subclass-selector> = <id-selector> | <class-selector> | <attribute-selector> | <pseudo-class-selector>
    // <id-selector> = <hash-token> with hash_type == ID
    // <class-selector> = '.' <ident-token>
    css_attribute_selector parse_subclass_selector(const css_token_vector& tokens, int& index, document_mode mode)
    {
        css_attribute_selector selector;

        const auto& tok0 = at(tokens, index);
        const auto& tok1 = at(tokens, index + 1);

        switch(tok0.type)
        {
        case HASH:
            if(tok0.hash_type == css_hash_id)
            {
                index++;
                selector.type    = select_id;
                std::string name = tok0.name();
                // ids are matched ASCII case-insensitively in quirks mode
                if(mode == quirks_mode)
                {
                    lcase(name);
                }
                selector.name = _id(name);
                return selector;
            }
            return {};

        case '.':
            if(tok1.type == IDENT)
            {
                index            += 2;
                selector.type     = select_class;
                std::string name  = tok1.name();
                // class names are matched ASCII case-insensitively in quirks mode
                if(mode == quirks_mode)
                {
                    lcase(name);
                }
                selector.name = _id(name);
                return selector;
            }
            return {};

        case SQUARE_BLOCK:
            selector = parse_attribute_selector(tok0);
            if(selector)
            {
                index++;
            }
            return selector;

        default:
            return parse_pseudo_class(tokens, index, mode);
        }
    }

    // simple = non-functional (without parentheses)
    // Only ::before and ::after generate a box, see html_tag::select. The rest are parsed and never
    // match, so that a rule like `.btn, ::file-selector-button { ... }` keeps its other selectors
    // instead of being dropped because of one selector the engine cannot render.
    bool is_supported_simple_pseudo_element(const std::string& name)
    {
        return is_one_of(
            lowcase(name),
            // Typographic Pseudo-elements  https://www.w3.org/TR/css-pseudo-4/#typographic-pseudos
            "first-line", "first-letter",
            // Highlight Pseudo-elements  https://www.w3.org/TR/css-pseudo-4/#highlight-pseudos
            "selection",
            // Tree-Abiding Pseudo-elements  https://www.w3.org/TR/css-pseudo-4/#treelike
            "before", "after", "marker", "placeholder", "backdrop",
            // https://html.spec.whatwg.org/multipage/rendering.html#the-file-selector-button-pseudo-element
            "file-selector-button");
    }

    css_attribute_selector parse_pseudo_element(const css_token_vector& tokens, int& index)
    {
        const auto& a = at(tokens, index);
        const auto& b = at(tokens, index + 1);
        const auto& c = at(tokens, index + 2);

        if(a.ch != ':')
        {
            return {};
        }
        if(b.ch != ':' && b.type != IDENT)
        {
            return {};
        }

        if(b.type == IDENT) // legacy syntax with one ':'  https://www.w3.org/TR/selectors-4/#single-colon-pseudos
        {
            // only these four pseudo-elements may be written with a single colon
            if(!is_one_of(b.ident(), "before", "after", "first-line", "first-letter"))
            {
                return {};
            }

            index += 2;
            return {select_pseudo_element, b.ident()};
        }

        if(c.type == IDENT) // normal syntax with '::'
        {
            if(!is_supported_simple_pseudo_element(c.ident()))
            {
                return {};
            }

            index += 3;
            return {select_pseudo_element, c.ident()};
        }

        return {};
    }

    // https://www.w3.org/TR/selectors-4/#typedef-compound-selector
    // <compound-selector> = [ <type-selector>? <subclass-selector>*
    //                         [ <pseudo-element-selector> <pseudo-class-selector>* ]* ]!
    // NOTE: This grammar allows pseudo-classes to go before #id and .class and [attr].
    // Whitespace is forbidden:
    // * Between any of the top-level components of a <compound-selector>
    css_element_selector::ptr parse_compound_selector(const css_token_vector& tokens, int& index, document_mode mode)
    {
        auto selector = std::make_shared<css_element_selector>();

        // <type-selector>?
        wq_name wq_name    = parse_type_selector(tokens, index);
        selector->m_prefix = _id(wq_name.prefix);
        selector->m_tag    = _id(wq_name.name);

        // <subclass-selector>*
        while(css_attribute_selector sel = parse_subclass_selector(tokens, index, mode))
        {
            selector->m_attrs.push_back(sel);
        }

        // [ <pseudo-element-selector> <pseudo-class-selector>* ]*
        while(true)
        {
            auto sel = parse_pseudo_element(tokens, index);
            if(!sel)
            {
                break;
            }
            selector->m_attrs.push_back(sel);

            while((sel = parse_pseudo_class(tokens, index, mode)))
            {
                selector->m_attrs.push_back(sel);
            }
        }

        // [..]!  "must produce at least one value"  https://www.w3.org/TR/css-values-4/#mult-req
        if(selector->m_tag == empty_id && selector->m_attrs.empty())
        {
            return nullptr;
        }

        if(selector->m_tag == empty_id)
        {
            selector->m_tag = star_id;
        }

        return selector;
    }

    // <combinator> = '>' | '+' | '~' | [ '|' '|' ]
    // <whitespace> combinator is also handled here
    // parse_combinator consumes all leading and trailing whitespace
    // column combinator || is at-risk https://www.w3.org/TR/selectors-4/  and not implemented in Chrome/Firefox
    // https://caniuse.com/mdn-css_selectors_column
    int parse_combinator(const css_token_vector& tokens, int& index)
    {
        bool ws = skip_whitespace(tokens, index);

        const css_token& tok = at(tokens, index);
        if(is_one_of(tok.ch, '>', '+', '~'))
        //	if (tok.ch in ${'>', '+', '~'})
        {
            index++;
            skip_whitespace(tokens, index);
            return tok.ch;
        }

        return ws ? ' ' : 0;
    }

    bool has_pseudo_element(const css_element_selector& selector)
    {
        for(const auto& attr : selector.m_attrs)
        {
            if(attr.type == select_pseudo_element)
            {
                return true;
            }
        }
        return false;
    }

    css_selector::ptr parse_complex_selector(const css_token_vector& tokens, document_mode mode)
    {
        int index = 0;
        skip_whitespace(tokens, index);
        auto sel = parse_compound_selector(tokens, index, mode);
        if(!sel)
        {
            return nullptr;
        }

        auto selector     = std::make_shared<css_selector>();
        selector->m_right = *sel;

        // NOTE: all the whitespace is handled by parse_combinator, that's why skip_whitespace is never called in the
        // loop NOTE: parse_complex_selector is different from most other parse_xxx functions in that it's required
        //       to parse all input tokens, it doesn't just parse as much as possible.
        while(true)
        {
            int combinator = parse_combinator(tokens, index);
            if(index == static_cast<int>(tokens.size()))
            {
                // combinator == 0 means index already was at the end before the call to parse_combinator
                return !combinator || combinator == ' ' ? selector : nullptr;
            }
            if(!combinator) // not the end and combinator failed to parse
            {
                return nullptr;
            }

            // "Only the last compound selector of a complex selector may contain a pseudo-element"
            // https://www.w3.org/TR/selectors-4/#typedef-complex-selector
            if(has_pseudo_element(selector->m_right))
            {
                return nullptr;
            }

            // otherwise: index is not at the end, combinator is good and tokens[index] is not whitespace
            // it means if parse_compound_selector fails it's an error
            sel = parse_compound_selector(tokens, index, mode);
            if(!sel)
            {
                return nullptr;
            }

            auto new_selector          = std::make_shared<css_selector>();
            new_selector->m_left       = selector;
            new_selector->m_right      = *sel;
            new_selector->m_combinator = static_cast<css_combinator>(combinator);
            selector                   = new_selector;
        }
    }

    // Return true if `selector` has (in any of its css_element_selector's) a css_attribute_selector
    // of type `type` and name `name`. name == "" matches any name.
    bool has_selector(const css_selector& selector, attr_select_type type, const std::string& name = "")
    {
        for(const auto& sel : selector.m_right.m_attrs)
        {
            if(sel.type == type && (name == "" || equal_i(_s(sel.name), name)))
            {
                return true;
            }
        }

        if(selector.m_left)
        {
            return has_selector(*selector.m_left, type, name);
        }

        return false;
    }

    // https://www.w3.org/TR/css-syntax-3/#parse-comma-list
    // https://www.w3.org/TR/selectors-4/#selector-list
    // https://www.w3.org/TR/selectors-4/#forgiving-selector
    // Parse comma-separated list of complex selectors.
    css_selector::vector parse_selector_list(const css_token_vector& tokens, int options, document_mode mode)
    {
        // NOTE: this is unnecessary: "If input contains only <whitespace-token>s, return an empty list."

        std::vector<css_token_vector> list_of_lists = parse_comma_separated_list(tokens);
        css_selector::vector          result;

        for(const auto& list : list_of_lists)
        {
            css_selector::ptr selector = parse_complex_selector(list, mode);

            // if selector is failed to parse or not allowed by the options
            if(!selector || ((options & forbid_pseudo_elements) && has_selector(*selector, select_pseudo_element)))
            {
                // in forgiving mode, ignore the bad selector
                if(options & forgiving_mode)
                {
                    continue;
                }

                // in strict mode, entire selector-list fails to parse because of one bad selector
                return {};
            }

            result.push_back(selector);
        }

        return result;
    }

    bool css_selector::parse(const std::string& text, document_mode mode)
    {
        auto tokens = normalize(text, f_componentize);
        auto ptr    = parse_complex_selector(tokens, mode);
        if(!ptr)
        {
            return false;
        }
        *this = *ptr;
        return true;
    }

} // namespace litehtml
