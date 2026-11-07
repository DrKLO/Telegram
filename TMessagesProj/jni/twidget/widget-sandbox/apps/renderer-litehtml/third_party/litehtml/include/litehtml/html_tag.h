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
        using ptr = std::shared_ptr<html_tag>;

      protected:
        string_id              m_tag = empty_id;
        string_id              m_id  = empty_id;
        string_vector          m_str_classes;
        std::vector<string_id> m_classes;
        style                  m_style;
        string_map             m_attrs;
        std::vector<string_id> m_pseudo_classes;

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
        void        remove_attr(const char* name) override;
        const char* get_attr(const char* name, const char* def = nullptr) const override;
        void        apply_stylesheet(const litehtml::css& stylesheet) override;
        void        refresh_styles() override;

        bool is_white_space() const override;
        bool is_body() const override;
        bool is_break() const override;

        bool on_mouse_over() override;
        bool on_mouse_leave() override;
        bool on_lbutton_down() override;
        bool on_lbutton_up(bool is_click) override;
        void on_click() override;
        bool set_pseudo_class(string_id cls, bool add) override;
        bool set_class(const char* pclass, bool add) override;
        bool is_replaced() const override;
        void compute_styles(bool recursive = true) override;
        void draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                  const std::shared_ptr<render_item>& ri) override;
        void draw_background(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                             const std::shared_ptr<render_item>& ri) override;

        template <class Type>
        const Type& get_property(string_id name, bool inherited, const Type& default_value,
                                 uint_ptr css_properties_member_offset) const;
        bool        get_custom_property(string_id name, css_token_vector& result) const;

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
        const property_value& value = m_style.get_property(name);

        if(value.is<Type>())
        {
            return value.get<Type>();
        }
        if(inherited || value.is<inherit>())
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
