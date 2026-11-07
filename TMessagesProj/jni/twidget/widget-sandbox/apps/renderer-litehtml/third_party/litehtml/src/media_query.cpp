#include "html.h"
#include "media_query.h"
#include "css_parser.h"
#include <cassert>
#include "document.h"
#include "document_container.h"

namespace litehtml
{

    bool eval_op(float x, short op, float value)
    {
        const float epsilon = 0.00001f;
        if(std::abs(x - value) < epsilon)
        {
            if(is_one_of(op, '=', u'⩾', u'⩽'))
            {
                return true;
            }
            if(op == u'≠')
            {
                return false;
            }
        }

        switch(op)
        {
        case '<':
            return pixel_t(x) < pixel_t(value);
        case u'⩽':
            return pixel_t(x) <= pixel_t(value);
        case '>':
            return pixel_t(x) > pixel_t(value);
        case u'⩾':
            return pixel_t(x) >= pixel_t(value);
        case '=':
            return pixel_t(x) == pixel_t(value);
        case u'≠':
            return pixel_t(x) != pixel_t(value);
        default:
            return false;
        }
    }

    bool media_feature::compare(float x) const
    {
        if(!op2)
        {
            return eval_op(x, op, value);
        }
        return eval_op(value, op, x) && eval_op(x, op2, value2);
    }

    bool media_feature::check(const media_features& feat) const
    {
        switch(_id(name))
        {
        case _width_:
            return compare(feat.width.value());
        case _height_:
            return compare(feat.height.value());
        case _device_width_:
            return compare(feat.device_width.value());
        case _device_height_:
            return compare(feat.device_height.value());
        case _orientation_:
            return compare(feat.height >= feat.width ? _portrait_ : _landscape_);
        case _aspect_ratio_:
            // The standard calls 1/0 "a degenerate ratio" https://drafts.csswg.org/css-values-4/#ratio-value,
            // but it doesn't specify exactly how it behaves in this context:
            // https://drafts.csswg.org/mediaqueries-5/#aspect-ratio. Chrome/Firefox work as expected with 1/0, for
            // example when both width and height are nonzero (aspect-ratio < 1/0) evaluates to true. But they behave
            // the same for 0/0, which is unexpected (0/0 is NaN, so any comparisons should evaluate to false). 0/1 is
            // also degenerate according to the standard.
            return feat.height != 0_px ? compare(static_cast<float>(feat.width) / feat.height.value()) : false;
        case _device_aspect_ratio_:
            return feat.device_height != 0_px
                       ? compare(static_cast<float>(feat.device_width) / feat.device_height.value())
                       : false;
        case _color_:
            return compare(feat.color);
        case _color_index_:
            return compare(feat.color_index);
        case _monochrome_:
            return compare(feat.monochrome);
        case _resolution_:
            return compare(feat.resolution.value());
        default:
            assert(0); // must never happen, unknown media features are handled in parse_media_feature
            return false;
        }
    }

    trilean media_condition::check(const media_features& features) const
    {
        if(op == _not_)
        {
            return not m_conditions[0].check(features);
        }

        if(op == _and_)
        {
            // https://drafts.csswg.org/mediaqueries/#evaluating
            // The result is true if the <media-in-parens> child term and all of the <media-in-parens>
            // children of the <media-and> child terms are true, false if at least one of these
            // <media-in-parens> terms are false, and unknown otherwise.
            trilean result = True;
            for(const auto& condition : m_conditions)
            {
                result = result && condition.check(features);
                if(result == False)
                {
                    return result; // no need to check further
                }
            }
            return result;
        }
        if(op == _or_)
        {
            // The result is false if the <media-in-parens> child term and all of the <media-in-parens>
            // children of the <media-or> child terms are false, true if at least one of these
            // <media-in-parens> terms are true, and unknown otherwise.
            trilean result = False;
            for(const auto& condition : m_conditions)
            {
                result = result || condition.check(features);
                if(result == True)
                {
                    return result; // no need to check further
                }
            }
            return result;
        }
        return False;
    }

    trilean media_in_parens::check(const media_features& features) const
    {
        if(is<media_condition>())
        {
            return get<media_condition>().check(features);
        }
        if(is<media_feature>())
        {
            return static_cast<trilean>(get<media_feature>().check(features));
        }
        // <general-enclosed>  https://drafts.csswg.org/mediaqueries/#evaluating
        return Unknown;
    }

    trilean media_query::check(const media_features& features) const
    {
        trilean result;
        // https://drafts.csswg.org/mediaqueries/#media-types
        // User agents must recognize the following media types as valid, but must make them match nothing.
        if(m_media_type >= media_type_first_deprecated)
        {
            result = False;
        } else if(m_media_type == media_type_unknown)
        {
            result = False;
        } else if(m_media_type == media_type_all)
        {
            result = True;
        } else
        {
            result = static_cast<trilean>(m_media_type == features.type);
        }

        if(result == True)
        {
            for(const auto& condition : m_conditions)
            {
                result = result && condition.check(features);
                if(result == False)
                {
                    break; // no need to check further
                }
            }
        }

        if(m_not)
        {
            result = not result;
        }

        return result;
    }

    // https://drafts.csswg.org/mediaqueries/#mq-list
    bool media_query_list::check(const media_features& features) const
    {
        if(empty())
        {
            return true; // An empty media query list evaluates to true.
        }

        trilean result = False;
        for(const auto& query : m_queries)
        {
            result = result || query.check(features);
            if(result == True)
            {
                break; // no need to check further
            }
        }

        // https://drafts.csswg.org/mediaqueries/#evaluating
        // If the result ... is used in any context that expects a two-valued boolean, “unknown” must be converted to
        // “false”.
        return result == True;
    }

    // nested @media rules: https://drafts.csswg.org/css-conditional-3/#processing
    // all of them must be true for style rules to apply
    bool media_query_list_list::apply_media_features(const media_features& features)
    {
        bool apply = true;

        for(const auto& mq_list : m_media_query_lists)
        {
            if(!mq_list.check(features))
            {
                apply = false;
                break;
            }
        }

        bool ret  = (apply != m_is_used);
        m_is_used = apply;
        return ret;
    }

    bool parse_media_query(const css_token_vector& tokens, media_query& mquery, const std::shared_ptr<document>& doc);

    // https://drafts.csswg.org/mediaqueries-5/#typedef-media-query-list
    media_query_list parse_media_query_list(const css_token_vector& _tokens, const std::shared_ptr<document>& doc)
    {
        auto keep_whitespace = [](const auto& left_token, const auto& right_token) {
            return is_one_of(left_token.ch, '<', '>') && right_token.ch == '=';
        };
        css_token_vector tokens = normalize(_tokens, f_componentize | f_remove_whitespace, keep_whitespace);
        // this needs special treatment because empty media query list evaluates to true
        if(tokens.empty())
        {
            return {};
        }

        media_query_list result;
        auto             list_of_lists = parse_comma_separated_list(tokens);
        for(const auto& list : list_of_lists)
        {
            media_query query;
            parse_media_query(list, query, doc);
            // Note: appending even if media query failed to parse, as per standard.
            result.m_queries.push_back(query);
        }
        return result;
    }

    media_query_list parse_media_query_list(const std::string& str, const std::shared_ptr<document>& doc)
    {
        auto tokens = normalize(str);
        return parse_media_query_list(tokens, doc);
    }

    bool parse_media_condition(const css_token_vector& tokens, int& index, bool or_allowed, media_condition& condition,
                               const std::shared_ptr<document>& doc);

    // <media-query> = <media-condition> | [ not | only ]? <media-type> [ and <media-condition-without-or> ]?
    bool parse_media_query(const css_token_vector& tokens, media_query& mquery, const std::shared_ptr<document>& doc)
    {
        if(tokens.empty())
        {
            return false;
        }
        int  index = 0;
        auto end   = [&]() { return index == static_cast<int>(tokens.size()); };

        media_condition condition;
        if(parse_media_condition(tokens, index, true, condition, doc) && end())
        {
            mquery.m_not        = false;
            mquery.m_media_type = media_type_all;
            mquery.m_conditions = {condition};
            return true;
        }

        std::string ident = tokens[0].ident();
        bool        _not  = false;
        if(ident == "not")
        {
            index++, _not = true;
        } else if(ident == "only")
        {
            index++; // ignored  https://drafts.csswg.org/mediaqueries-5/#mq-only
        }

        // <media-type> = <ident> except only, not, and, or, and layer
        ident = at(tokens, index).ident();
        if(is_one_of(ident, "", "only", "not", "and", "or", "layer"))
        {
            return false;
        }
        auto idx        = css_values(media_type_strings).value_index(ident);
        int  media_type = idx.has_value() ? static_cast<int>(*idx) + 1 : media_type_unknown;
        index++;

        if(at(tokens, index).ident() == "and")
        {
            index++;
            if(!parse_media_condition(tokens, index, false, condition, doc) || !end())
            {
                return false;
            }
            mquery.m_conditions = {condition};
        }
        if(!end())
        {
            return false;
        }
        mquery.m_not        = _not;
        mquery.m_media_type = static_cast<litehtml::media_type>(media_type);
        return true;
    }

    bool parse_media_in_parens(const css_token& token, media_in_parens& media_in_parens,
                               const std::shared_ptr<document>& doc);

    // <media-condition>            = <media-not> | <media-in-parens> [ <media-and>* | <media-or>* ]
    // <media-condition-without-or> = <media-not> | <media-in-parens> <media-and>*
    // <media-not> = not <media-in-parens>
    bool parse_media_condition(const css_token_vector& tokens, int& index, bool _or_allowed, media_condition& condition,
                               const std::shared_ptr<document>& doc)
    {
        media_in_parens media_in_parens;
        if(at(tokens, index).ident() == "not")
        {
            if(!parse_media_in_parens(at(tokens, index + 1), media_in_parens, doc))
            {
                return false;
            }
            condition.op            = _not_;
            condition.m_conditions  = {media_in_parens};
            index                  += 2;
            return true;
        }

        if(!parse_media_in_parens(at(tokens, index), media_in_parens, doc))
        {
            return false;
        }
        condition.m_conditions = {media_in_parens};
        index++;

        bool or_allowed  = _or_allowed;
        bool and_allowed = true;
        while(true)
        {
            std::string ident = at(tokens, index).ident();
            if(ident == "and" && and_allowed)
            {
                condition.op = _and_, or_allowed = false;
            } else if(ident == "or" && or_allowed)
            {
                condition.op = _or_, and_allowed = false;
            } else
            {
                return true;
            }
            index++;

            if(!parse_media_in_parens(at(tokens, index), media_in_parens, doc))
            {
                return false;
            }
            condition.m_conditions.push_back(media_in_parens);
            index++;
        }
    }

    bool parse_media_feature(const css_token& token, media_feature& media_feature,
                             const std::shared_ptr<document>& doc);

    // https://drafts.csswg.org/mediaqueries-5/#typedef-media-in-parens
    // <media-in-parens> = ( <media-condition> ) | <media-feature> | <general-enclosed>
    // <general-enclosed> = [ <function-token> <any-value>? ) ] | [ ( <any-value>? ) ]
    bool parse_media_in_parens(const css_token& token, media_in_parens& media_in_parens,
                               const std::shared_ptr<document>& doc)
    {
        if(token.type == CV_FUNCTION)
        {
            if(!token.value.empty() && !is_any_value(token.value))
            {
                return false;
            }
            media_in_parens = unknown();
            return true;
        }

        if(token.type != ROUND_BLOCK)
        {
            return false;
        }
        const css_token_vector& tokens = token.value;

        int             index = 0;
        media_condition condition;
        media_feature   media_feature;
        if(parse_media_condition(tokens, index, true, condition, doc) && index == static_cast<int>(tokens.size()))
        {
            media_in_parens = condition;
        } else if(parse_media_feature(token, media_feature, doc))
        {
            media_in_parens = media_feature;
        } else if(!tokens.empty() && !is_any_value(tokens))
        {
            return false;
        } else
        {
            media_in_parens = unknown();
        }
        return true;
    }

    bool parse_mf_value(const css_token_vector& tokens, int& index, css_token val[2]);
    bool parse_mf_range(const css_token_vector& tokens, media_feature& media_feature,
                        const std::shared_ptr<document>& doc);

    // https://drafts.csswg.org/mediaqueries/#mq-ranges
    // Every media feature defines its “type” as either “range” or “discrete” in its definition table.
    // The only significant difference between the two types is that “range” media features can be evaluated in a range
    // context and accept “min-” and “max-” prefixes on their name.

    // https://drafts.csswg.org/mediaqueries/#mq-min-max
    // “Discrete” type properties do not accept “min-” or “max-” prefixes. Adding such a prefix to a “discrete” type
    // media feature simply results in an unknown feature name. For example, (min-grid: 1) is invalid, because grid is a
    // “discrete” media feature, and so doesn’t accept the prefixes. (Even though the grid media feature appears to be
    // numeric, as it accepts the values 0 and 1.)

    struct mf_info
    {
        string_id              type       = empty_id; // range, discrete
        string_id              value_type = empty_id; // length, ratio, resolution, integer, keyword
        std::vector<string_id> keywords =
            {}; // default value is specified here to get rid of gcc warning "missing initializer for member"

        operator bool() const
        {
            return type != empty_id;
        }
    };

    std::map<std::string, mf_info> supported_media_features = {
        ////////////////////////////////////////////////
        // 4. Viewport/Page Dimensions Media Features
        ////////////////////////////////////////////////

        // https://drafts.csswg.org/mediaqueries/#width
        // For continuous media, this is the width of the viewport.
        {"width",               {_range_, _length_}                               },
        {"height",              {_range_, _length_}                               },

        // https://drafts.csswg.org/mediaqueries/#aspect-ratio
        // width/height
        {"aspect-ratio",        {_range_, _ratio_}                                },

        // https://drafts.csswg.org/mediaqueries/#orientation
        {"orientation",         {_discrete_, _keyword_, {_portrait_, _landscape_}}},

        ////////////////////////////////////////////////
        // 5. Display Quality Media Features
        ////////////////////////////////////////////////

        // https://drafts.csswg.org/mediaqueries/#resolution
        // https://developer.mozilla.org/en-US/docs/Web/CSS/@media/resolution
        {"resolution",          {_range_, _resolution_}                           },

        ////////////////////////////////////////////////
        // 6. Color Media Features
        ////////////////////////////////////////////////

        // https://drafts.csswg.org/mediaqueries/#color
        {"color",               {_range_, _integer_}                              },

        // https://drafts.csswg.org/mediaqueries/#color-index
        {"color-index",         {_range_, _integer_}                              },

        // https://drafts.csswg.org/mediaqueries/#monochrome
        {"monochrome",          {_range_, _integer_}                              },

        ////////////////////////////////////////////////
        // Deprecated Media Features
        ////////////////////////////////////////////////

        // https://drafts.csswg.org/mediaqueries/#device-width
        {"device-width",        {_range_, _length_}                               },
        {"device-height",       {_range_, _length_}                               },

        // https://drafts.csswg.org/mediaqueries/#device-aspect-ratio
        {"device-aspect-ratio", {_range_, _ratio_}                                },
    };

    bool convert_units(const mf_info& mfi, css_token val[2], const std::shared_ptr<document>& doc)
    {
        switch(mfi.value_type)
        {
        case _integer_:
            // nothing to convert, just verify
            return val[0].type == NUMBER && val[0].n.number_type == css_number_integer && val[1].type == 0;

        case _length_:
            {
                if(val[1].type != 0)
                {
                    return false;
                }
                css_length length;
                if(!length.from_token(val[0], f_length))
                {
                    return false;
                }
                font_metrics fm;
                fm.x_height = fm.font_size = doc->container()->get_default_font_size();
                doc->cvt_units(length, fm, 0_px);
                val[0].n.number = length.val();
                return true;
            }

        case _resolution_: // https://drafts.csswg.org/css-values-4/#resolution
            if(val[1].type != 0)
            {
                return false;
            }
            if(val[0].type == DIMENSION)
            {
                std::string    unit          = lowcase(val[0].unit());
                constexpr auto allowed_units = split_css_values<4>("dpi;dpcm;dppx;x"); // x == dppx
                // The allowed range of <resolution> values always excludes negative values
                if(css_values(allowed_units).has(unit) || val[0].n.number < 0)
                {
                    return false;
                }
                // dppx is the canonical unit, but we convert to dpi instead to match
                // document_container::get_media_features "Note that due to the 1:96 fixed ratio of CSS in to CSS px,
                // 1dppx is equivalent to 96dpi."
                if(unit == "dppx" || unit == "x")
                {
                    val[0].n.number *= 96;
                } else if(unit == "dpcm")
                {
                    val[0].n.number *= 2.54f; // 1in = 2.54cm
                }
                return true;
            }
            // https://drafts.csswg.org/mediaqueries/#resolution
            if(val[0].ident() == "infinite")
            {
                val[0] = css_token(NUMBER, INFINITY, css_number_number);
                return true;
            }
            // Note: <resolution> doesn't allow unitless zero
            return false;

        case _ratio_: // https://drafts.csswg.org/css-values-4/#ratio  <ratio> = <number [0,∞]> [ / <number [0,∞]> ]?
            if(val[0].type == NUMBER && val[0].n.number >= 0 &&
               ((val[1].type == NUMBER && val[1].n.number >= 0) || val[1].type == 0))
            {
                if(val[1].type == NUMBER)
                {
                    val[0].n.number /= val[1].n.number; // Note: val[1].n.number may be 0, so result may be inf
                }
                return true;
            }
            return false;

        case _keyword_:
            {
                if(val[1].type != 0)
                {
                    return false;
                }
                string_id ident = _id(val[0].ident());
                if(!contains(mfi.keywords, ident))
                {
                    return false;
                }
                val[0] = css_token(NUMBER, static_cast<float>(ident));
                return true;
            }

        default:
            return false;
        }
    }

    bool media_feature::verify_and_convert_units(string_id syntax, css_token val[2], css_token val2[2],
                                                 const std::shared_ptr<document>& doc)
    {
        // https://drafts.csswg.org/mediaqueries/#mq-boolean-context
        if(syntax == _boolean_) // (name)
        {
            // Attempting to evaluate a min/max prefixed media feature in a boolean context is invalid and a syntax
            // error.
            auto mf_info = at(supported_media_features, name);
            if(!mf_info)
            {
                return false;
            }
            value = mf_info.value_type == _keyword_ ? static_cast<float>(_none_) : 0;
            op    = u'≠';
            return true;
        }
        if(syntax == _plain_) // ({min-,max-,}name: value)
        {
            if(is_one_of(name.substr(0, 4), "min-", "max-"))
            {
                std::string real_name = name.substr(4);
                auto        mf_info   = at(supported_media_features, real_name);
                if(!mf_info || mf_info.type == _discrete_)
                {
                    return false;
                }
                if(!convert_units(mf_info, val, doc))
                {
                    return false;
                }
                value = val[0].n.number;
                op    = name.substr(0, 4) == "min-" ? u'⩾' : u'⩽';
                name  = real_name;
                return true;
            }
            auto mf_info = at(supported_media_features, name);
            if(!mf_info)
            {
                return false;
            }
            if(!convert_units(mf_info, val, doc))
            {
                return false;
            }
            value = val[0].n.number;
            op    = '=';
            return true;

        } // range syntax
        auto mf_info = at(supported_media_features, name);
        if(!mf_info || mf_info.type == _discrete_)
        {
            return false;
        }
        // if (val)
        {
            if(!convert_units(mf_info, val, doc))
            {
                return false;
            }
            value = val[0].n.number;
        }
        if(val2)
        {
            if(!convert_units(mf_info, val2, doc))
            {
                return false;
            }
            value2 = val2[0].n.number;
        }
        return true;
    }

    // <media-feature> = ( [ <mf-plain> | <mf-boolean> | <mf-range> ] )
    bool parse_media_feature(const css_token& token, media_feature& result, const std::shared_ptr<document>& doc)
    {
        if(token.type != ROUND_BLOCK || token.value.empty())
        {
            return false;
        }
        const css_token_vector& tokens = token.value;

        if(tokens.size() == 1)
        {
            media_feature mf = {tokens[0].ident()};
            if(!mf.verify_and_convert_units(_boolean_))
            {
                return false;
            }
            result = mf;
            return true;
        }

        if(tokens[0].type == IDENT && tokens[1].ch == ':')
        {
            css_token val[2];
            int       index = 2;
            if(!parse_mf_value(tokens, index, val) || index != static_cast<int>(tokens.size()))
            {
                return false;
            }

            media_feature mf = {tokens[0].ident()};
            if(!mf.verify_and_convert_units(_plain_, val, nullptr, doc))
            {
                return false;
            }
            result = mf;
            return true;
        }

        return parse_mf_range(tokens, result, doc);
    }

    // <mf-value> = <number> | <dimension> | <ident> | <ratio>
    // <ratio> = <number [0,∞]> [ / <number [0,∞]> ]?
    bool parse_mf_value(const css_token_vector& tokens, int& index, css_token val[2])
    {
        const css_token& a = at(tokens, index);
        const css_token& b = at(tokens, index + 1);
        const css_token& c = at(tokens, index + 2);

        if(!is_one_of(a.type, NUMBER, DIMENSION, IDENT))
        {
            return false;
        }

        if(a.type == NUMBER && a.n.number >= 0 && b.ch == '/' && c.type == NUMBER && c.n.number >= 0)
        {
            val[0]  = a;
            val[1]  = c;
            index  += 3;
        } else
        {
            val[0] = a;
            index++;
        }
        return true;
    }

    short mirror(short op)
    {
        if(op == '<')
        {
            return '>';
        }
        if(op == '>')
        {
            return '<';
        }
        if(op == u'⩽')
        {
            return u'⩾';
        }
        if(op == u'⩾')
        {
            return u'⩽';
        }
        return op;
    }

    // <mf-range> = <mf-name> <mf-comparison> <mf-value>
    //            | <mf-value> <mf-comparison> <mf-name>
    //            | <mf-value> <mf-lt> <mf-name> <mf-lt> <mf-value>
    //            | <mf-value> <mf-gt> <mf-name> <mf-gt> <mf-value>
    // <mf-lt> = '<' '='?
    // <mf-gt> = '>' '='?
    // <mf-eq> = '='
    // <mf-comparison> = <mf-lt> | <mf-gt> | <mf-eq>
    bool parse_mf_range(const css_token_vector& tokens, media_feature& result, const std::shared_ptr<document>& doc)
    {
        if(tokens.size() < 3)
        {
            return false;
        }

        int         index;
        std::string name;
        auto        mf_name = [&]() {
            if(at(tokens, index).type != IDENT)
            {
                return false;
            }
            name = at(tokens, index).ident();
            // special case for (infinite = resolution)
            // resolution is the only range media feature that can accept a keyword
            if(name == "infinite")
            {
                return false;
            }
            index++;
            return true;
        };
        auto mf_value = [&](css_token _val[2]) { return parse_mf_value(tokens, index, _val); };
        auto mf_lt_gt = [&](char lg, short& _op) {
            const css_token& tok  = at(tokens, index);
            const css_token& tok1 = at(tokens, index + 1);

            if(tok.ch != lg)
            {
                return false;
            }

            if(tok1.ch == '=')
            {
                index += 2, _op = lg == '<' ? u'⩽' : u'⩾';
            } else
            {
                index++, _op = lg;
            }
            return true;
        };
        auto mf_lt         = [&](short& _op) { return mf_lt_gt('<', _op); };
        auto mf_gt         = [&](short& _op) { return mf_lt_gt('>', _op); };
        auto mf_comparison = [&](short& _op) {
            const css_token& tok = at(tokens, index);

            if(tok.ch == '=')
            {
                index++;
                _op = '=';
                return true;
            }

            return mf_lt(_op) || mf_gt(_op);
        };
        auto start = [&]() {
            index = 0;
            return true;
        };
        auto end = [&]() { return index == static_cast<int>(tokens.size()); };

        short     op;
        css_token val[2];
        // using lambda to avoid warning "assignment within conditional expression"
        auto reverse = [](short& _op) {
            _op = mirror(_op);
            return true;
        };
        if((start() && mf_name() && mf_comparison(op) && mf_value(val) && end()) ||
           (start() && mf_value(val) && mf_comparison(op) && mf_name() && end() && reverse(op)))
        {
            media_feature mf = {name};
            mf.op            = op;
            if(!mf.verify_and_convert_units(_range_, val, nullptr, doc))
            {
                return false;
            }
            result = mf;
            return true;
        }
        short     op2;
        css_token val2[2];
        if((start() && mf_value(val) && mf_lt(op) && mf_name() && mf_lt(op2) && mf_value(val2) && end()) ||
           (start() && mf_value(val) && mf_gt(op) && mf_name() && mf_gt(op2) && mf_value(val2) && end()))
        {
            media_feature mf = {name};
            mf.op            = op;
            mf.op2           = op2;
            if(!mf.verify_and_convert_units(_range_, val, val2, doc))
            {
                return false;
            }
            result = mf;
            return true;
        }
        return false;
    }

} // namespace litehtml
