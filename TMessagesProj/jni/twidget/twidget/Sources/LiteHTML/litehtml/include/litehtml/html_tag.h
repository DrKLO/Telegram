#ifndef LITEHTML_HTML_TAG_H
#define LITEHTML_HTML_TAG_H

#include "element.h"
#include "background.h"
#include "css_selector.h"
#include "line_box.h"
#include "style.h"
#include "stylesheet.h"
#include "table.h"

namespace litehtml
{

    class html_tag : public element
    {
        friend class elements_iterator;
        friend class el_table;
        friend class table_grid;
        friend class line_box;

      public:
        uint64_t paint_signature() const override;
        using ptr = std::shared_ptr<html_tag>;

      protected:
        string_id              m_tag = empty_id;
        string_id              m_id  = empty_id;
        string_vector          m_str_classes;
        std::vector<string_id> m_classes;
        style                  m_style;
        style                  m_style_base;
        bool                   m_has_style_base = false;
        bool m_anonymous_style = false;
        string_map             m_attrs;
        std::vector<string_id> m_pseudo_classes;

        struct match_memo
        {
            uint64_t                       epoch = 0;
            string_map                     attrs;
            std::vector<css_selector::ptr> rules[3];
        };
        std::vector<match_memo> m_match_memos;

        struct flip_state
        {
            // Move-only (it holds unique_ptrs), and declared so: a vector moves an element only when it
            // cannot copy it or the move cannot throw, and MSVC's std::map move may throw.
            flip_state()                        = default;
            flip_state(flip_state&&)            = default;
            flip_state& operator=(flip_state&&) = default;

            std::string                                 value;
            uint64_t                                    epoch     = 0;
            int                                         parent_id = -1;
            string_map                                  others;
            string_vector                               str_classes;
            std::vector<string_id>                      classes;
            style                                       st;
            style                                       st_base;
            bool                                        has_base = false;
            used_selector::vector                       used;
            css_properties                              css;
            std::vector<css_properties>                 kids;
            std::map<std::string, int>                  regions;
        };
        std::vector<flip_state> m_flips;

        void select_all(const css_selector& selector, elements_list& res) override;

      public:
        explicit html_tag(const std::shared_ptr<document>& doc);
        // constructor for anonymous wrapper boxes
        explicit html_tag(const std::shared_ptr<element>& parent, const std::string& style = "display: block");

        bool                          appendChild(const element::ptr& el) override;
        bool                          removeChild(const element::ptr& el) override;
        void                          clearRecursive() override;
        string_id                     tag() const override;
        string_id                     id() const override;
        const char*                   get_tagName() const override;
        void                          set_tagName(const char* tag) override;
        void                          set_data(const char* data) override;
        const std::vector<string_id>& classes() const
        {
            return m_classes;
        }
        const string_vector& str_classes() const
        {
            return m_str_classes;
        }

        void        set_attr(const char* name, const char* val) override;
        const char* get_attr(const char* name, const char* def = nullptr) const override;
        void        apply_stylesheet(const litehtml::css& stylesheet) override;
        void        apply_matching(const litehtml::css* sheets[3], uint64_t epoch);
        bool        flip_class(const std::string& from, const std::string& to, uint64_t epoch, int* region);
        void        note_flip(const std::string& from, const std::string& to, int region);
        void        forget_flips() { m_flips.clear(); }
        void        apply_selector(const std::shared_ptr<css_selector>& sel);
        void        refresh_styles() override;
        // Puts back the style the element had before its inline `style` attribute was applied, so a changed
        // attribute can be applied by compute_styles exactly like on a freshly built element.
        void        restore_base_style();
        const string_map& attributes() const
        {
            return m_attrs;
        }
        // Replaces every attribute, as if the element had been created with `attrs`.
        void reset_attributes(const string_map& attrs);
        // Drops everything stylesheets and attributes contributed, including ::before and ::after, so the
        // element can be matched again exactly like a freshly built one.
        void forget_matched_styles();

        bool is_white_space() const override;
        bool is_body() const override;
        bool is_break() const override;

        bool on_mouse_over() override;
        bool on_mouse_leave() override;
        bool on_lbutton_down() override;
        bool on_lbutton_up(bool is_click) override;
        void on_click() override;
        bool set_pseudo_class(string_id cls, bool add) override;
        bool has_pseudo_class(string_id cls) const override
        {
            return std::find(m_pseudo_classes.begin(), m_pseudo_classes.end(), cls) != m_pseudo_classes.end();
        }
        bool set_class(const char* pclass, bool add) override;
        bool is_replaced() const override;
        void compute_styles(bool recursive = true) override;
        bool background_color_is_local() const;
        void compute_background_style();
        bool patch_inline_properties(const std::vector<string_id>& names, const std::string& patch);
        bool can_restyle_width_shallow() const;
        void draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                  const std::shared_ptr<render_item>& ri) override;
        void draw_background(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                             const std::shared_ptr<render_item>& ri) override;

        template <class Type>
        const Type& get_property(string_id name, bool inherited, const Type& default_value,
                                 uint_ptr css_properties_member_offset) const;
        std::string raw_declaration(const std::string& name) const;
        /// What the UA stylesheet alone gives this element for `name` (`revert`); invalid when nothing.
        const property_value& ua_property(string_id name) const;
        bool        get_custom_property(string_id name, css_token_vector& result) const;
        bool has_own_property(string_id name) const
        {
            const property_value& value = m_style.get_property(name);
            return !value.is<invalid>();
        }
        const css_token_vector* get_token_property(string_id name) const
        {
            const property_value& value = m_style.get_property(name);
            return value.is<css_token_vector>() ? &value.get<css_token_vector>() : nullptr;
        }

        elements_list& children();

        int select(const css_selector::vector& selector_list, bool apply_pseudo = true) override;
        int select(const std::string& selector) override;
        int select(const css_selector& selector, bool apply_pseudo = true) override;
        int select(const css_element_selector& selector, bool apply_pseudo = true) override;
        int select_pseudoclass(const css_attribute_selector& sel);
        int select_attribute(const css_attribute_selector& sel) const;

        elements_list select_all(const std::string& selector) override;
        elements_list select_all(const css_selector& selector) override;

        element::ptr select_one(const std::string& selector) override;
        element::ptr select_one(const css_selector& selector) override;

        element::ptr find_ancestor(const css_selector& selector, bool apply_pseudo = true,
                                   bool* is_pseudo = nullptr) override;
        element::ptr find_adjacent_sibling(const element::ptr& el, const css_selector& selector,
                                           bool apply_pseudo = true, bool* is_pseudo = nullptr) override;
        element::ptr find_sibling(const element::ptr& el, const css_selector& selector, bool apply_pseudo = true,
                                  bool* is_pseudo = nullptr) override;
        void         get_text(std::string& text) const override;
        void         parse_attributes() override;

        void get_content_size(size& sz, pixel_t max_width) override;
        void add_style(const style& style) override;

        bool              is_nth_child(const element::ptr& el, int num, int off, bool of_type,
                                       const css_selector::vector& selector_list) const override;
        bool              is_nth_last_child(const element::ptr& el, int num, int off, bool of_type,
                                            const css_selector::vector& selector_list) const override;
        bool              is_only_child(const element::ptr& el, bool of_type) const override;
        const background* get_background(bool own_only = false) override;

        std::string dump_get_name() override;

      protected:
        void         draw_list_marker(uint_ptr hdc, const position& pos, const std::shared_ptr<render_item>& ri);
        std::string  get_list_marker_text(int index);
        element::ptr get_element_before(const style& style, bool create);
        element::ptr get_element_after(const style& style, bool create);

        void map_to_pixel_length_property(string_id prop_name, const std::string& attr_value);
        void map_to_pixel_length_property_with_default_value(string_id prop_name, const std::string& attr_value,
                                                             int default_value);
        void map_to_dimension_property(string_id prop_name, const std::string& attr_value);
        void map_to_dimension_property_ignoring_zero(string_id prop_name, const std::string& attr_value);

      private:
        void handle_counter_properties();
    };

    /************************************************************************/
    /*                        Inline Functions                              */
    /************************************************************************/

    inline elements_list& html_tag::children()
    {
        return m_children;
    }

    template <class Type>
    const Type& html_tag::get_property(string_id name, bool inherited, const Type& default_value,
                                       uint_ptr css_properties_member_offset) const
    {
        const property_value& declared = m_style.get_property(name);
        const property_value& value =
            declared.is<css_wide_keyword>() && declared.get<css_wide_keyword>().kind == css_wide_keyword::revert
                ? ua_property(name)
                : declared;

        if(value.is<Type>())
        {
            return value.get<Type>();
        }
        // initial, and unset on a property that does not inherit (a revert the UA sheet has no value for is unset)
        if(declared.is<css_wide_keyword>() && !value.is<inherit>() &&
           (!inherited || declared.get<css_wide_keyword>().kind == css_wide_keyword::initial))
        {
            return default_value;
        }
        if(inherited || value.is<inherit>() || declared.is<css_wide_keyword>())
        {
            if(auto _parent = parent())
            {
                // NOLINTBEGIN(cppcoreguidelines-pro-type-reinterpret-cast)
                return *reinterpret_cast<const Type*>(reinterpret_cast<const byte*>(&_parent->css()) +
                                                      css_properties_member_offset);
                // NOLINTEND(cppcoreguidelines-pro-type-reinterpret-cast)
            }
            return default_value;
        }
        return default_value;
    }

} // namespace litehtml

#endif // LITEHTML_HTML_TAG_H
