#ifndef LITEHTML_STYLE_H
#define LITEHTML_STYLE_H

#include "background.h"
#include "css_length.h"
#include "css_position.h"
#include "css_tokenizer.h"
#include "gradient.h"
#include "web_color.h"

namespace litehtml
{
    struct invalid
    {
    }; // indicates "not found" condition in style::get_property
    struct inherit
    {
    }; // "inherit" was specified as the value of this property

    struct property_value
        : variant<invalid, inherit, int, int_vector, css_length, length_vector, float, web_color, std::vector<image>,
                  std::string, std::vector<std::string>, size_vector, css_token_vector>
    {
        bool m_important = false;
        bool m_has_var   = false; // css_token_vector, parsing is delayed because of var()

        property_value() = default;
        template <class T>
        property_value(const T& val, bool important, bool has_var = false) :
            base(val),
            m_important(important),
            m_has_var(has_var)
        {
        }
    };

    class html_tag;
    using props_map = std::map<string_id, property_value>;

    // represents a style block, eg. "color: black; display: inline"
    class style
    {
      public:
        using ptr    = std::shared_ptr<style>;
        using vector = std::vector<style::ptr>;

      private:
        props_map                              m_properties;
        static std::map<string_id, css_values> m_valid_values;

      public:
        void add(const css_token_vector& tokens, const std::string& baseurl = "",
                 document_container* container = nullptr);
        void add(const std::string& txt, const std::string& baseurl = "", document_container* container = nullptr);

        void add_property(string_id name, const css_token_vector& tokens, const std::string& baseurl = "",
                          bool important = false, document_container* container = nullptr);
        void add_property(string_id name, const std::string& val, const std::string& baseurl = "",
                          bool important = false, document_container* container = nullptr);

        const property_value& get_property(string_id name) const;

        bool empty() const
        {
            return m_properties.empty();
        }

        void combine(const style& src);
        void clear()
        {
            m_properties.clear();
        }

        void subst_vars(const html_tag* el);

      private:
        void inherit_property(string_id name, bool important);

        void parse_background(const css_token_vector& tokens, const std::string& baseurl, bool important,
                              document_container* container);
        bool parse_bg_layer(const css_token_vector& tokens, document_container* container, background& bg,
                            bool final_layer);
        // parse the value of background-image property, which is comma-separated list of <bg-image>s
        void parse_background_image(const css_token_vector& tokens, const std::string& baseurl, bool important,
                                    document_container* container);

        // parse comma-separated list of keywords
        void parse_keyword_comma_list(string_id name, const css_token_vector& tokens, bool important);
        void parse_background_position(const css_token_vector& tokens, bool important);
        void parse_background_size(const css_token_vector& tokens, bool important);

        void parse_border(const css_token_vector& tokens, bool important, document_container* container);
        void parse_border_side(string_id name, const css_token_vector& tokens, bool important,
                               document_container* container);
        void parse_border_radius(const css_token_vector& tokens, bool important);

        bool parse_list_style_image(const css_token& tok, std::string& url);
        void parse_list_style(const css_token_vector& tokens, const std::string& baseurl, bool important);

        void parse_font(css_token_vector tokens, bool important);
        void parse_text_decoration(const css_token_vector& tokens, bool important, document_container* container);
        bool parse_text_decoration_color(const css_token& token, bool important, document_container* container);
        void parse_text_decoration_line(const css_token_vector& tokens, bool important);

        void parse_text_emphasis(const css_token_vector& tokens, bool important, document_container* container);
        bool parse_text_emphasis_color(const css_token& token, bool important, document_container* container);
        void parse_text_emphasis_position(const css_token_vector& tokens, bool important);

        void parse_flex_flow(const css_token_vector& tokens, bool important);
        void parse_flex(const css_token_vector& tokens, bool important);
        void parse_align_self(string_id name, const css_token_vector& tokens, bool important);

        void add_parsed_property(string_id name, const property_value& propval);
        void add_length_property(string_id name, const css_token& val, const css_values& keywords, int options,
                                 bool important);
        template <class T> void add_four_properties(string_id top_name, T val[4], int n, bool important);
        void                    remove_property(string_id name, bool important);
    };

    bool parse_url(const css_token& token, std::string& url);
    bool parse_length(const css_token& tok, css_length& length, int options, css_values keywords = {});
    bool parse_angle(const css_token& tok, float& angle, bool percents_allowed = false);
    bool parse_bg_position(const css_token_vector& tokens, int& index, css_length& x, css_length& y,
                           bool convert_keywords_to_percents);

    template <typename Enum>
    bool parse_keyword(const css_token& tok, Enum& val, css_values keywords, int first_keyword_value = 0)
    {
        auto idx = keywords.value_index(tok.ident());
        if(!idx.has_value())
        {
            return false;
        }
        val = static_cast<Enum>(first_keyword_value + *idx);
        return true;
    }

} // namespace litehtml

#endif // LITEHTML_STYLE_H
