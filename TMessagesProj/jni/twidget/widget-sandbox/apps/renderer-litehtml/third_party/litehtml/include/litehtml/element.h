#ifndef LITEHTML_ELEMENT_H
#define LITEHTML_ELEMENT_H

#include "types.h"
#include "css_properties.h"
#include "stylesheet.h"
#include <functional>
#include <list>
#include <memory>
#include <tuple>

namespace litehtml
{
    class line_box;
    class dumper;
    class render_item;

    class element : public std::enable_shared_from_this<element>
    {
        friend class line_box;
        friend class html_tag;
        friend class el_table;
        friend class document;

      public:
        using ptr       = std::shared_ptr<element>;
        using const_ptr = std::shared_ptr<const element>;
        using weak_ptr  = std::weak_ptr<element>;

      protected:
        std::weak_ptr<element>                m_parent;
        std::weak_ptr<document>               m_doc;
        elements_list                         m_children;
        css_properties                        m_css;
        std::list<std::weak_ptr<render_item>> m_renders;
        used_selector::vector                 m_used_styles;

        virtual void select_all(const css_selector& selector, elements_list& res);
        element::ptr _add_before_after(int type, const style& style);

      private:
        std::map<string_id, int> m_counter_values;

      public:
        explicit element(const std::shared_ptr<document>& doc);
        virtual ~element() = default;

        const css_properties& css() const;
        css_properties&       css_w();

        bool     in_normal_flow() const;
        bool     is_inline() const;     // returns true if element is inline
        bool     is_inline_box() const; // returns true if element is inline box (inline-table, inline-box, inline-flex)
        bool     is_block_box() const;
        position get_placement() const;
        bool     is_positioned() const;
        bool     is_float() const;
        bool     is_block_formatting_context() const;

        pixel_t v_scroll(pixel_t dy) const;
        pixel_t h_scroll(pixel_t dx) const;

        bool         is_root() const;
        element::ptr parent() const;
        void         parent(const element::ptr& par);
        // returns true for elements inside a table (but outside cells) that don't participate in table rendering
        bool is_table_skip() const;

        std::shared_ptr<document>                  get_document() const;
        const std::list<std::shared_ptr<element>>& children() const;

        std::shared_ptr<render_item> get_render_item();

        virtual elements_list select_all(const std::string& selector);
        virtual elements_list select_all(const css_selector& selector);

        virtual element::ptr select_one(const std::string& selector);
        virtual element::ptr select_one(const css_selector& selector);

        virtual bool appendChild(const ptr& el);
        virtual bool removeChild(const ptr& el);
        virtual void clearRecursive();

        virtual string_id   id() const;
        virtual string_id   tag() const;
        virtual const char* get_tagName() const;
        virtual void        set_tagName(const char* tag);
        virtual void        set_data(const char* data);

        virtual void        set_attr(const char* name, const char* val);
        virtual void        remove_attr(const char* name);
        virtual const char* get_attr(const char* name, const char* def = nullptr) const;
        virtual void        apply_stylesheet(const litehtml::css& stylesheet);
        virtual void        refresh_styles();
        virtual bool        is_white_space() const;
        virtual bool        is_space() const;
        virtual bool        is_comment() const;
        virtual bool        is_body() const;
        virtual bool        is_break() const;
        virtual bool        is_text() const;

        virtual bool on_mouse_over();
        virtual bool on_mouse_leave();
        virtual bool on_lbutton_down();
        virtual bool on_lbutton_up(bool is_click = true);
        virtual void on_click();
        virtual bool set_pseudo_class(string_id cls, bool add);
        virtual bool set_class(const char* pclass, bool add);
        virtual bool is_replaced() const;
        virtual void compute_styles(bool recursive = true);
        virtual void draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                          const std::shared_ptr<render_item>& ri);
        virtual void draw_background(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                     const std::shared_ptr<render_item>& ri);

        virtual void              get_text(std::string& text) const;
        virtual void              parse_attributes();
        virtual int               select(const css_selector::vector& selector_list, bool apply_pseudo = true);
        virtual int               select(const std::string& selector);
        virtual int               select(const css_selector& selector, bool apply_pseudo = true);
        virtual int               select(const css_element_selector& selector, bool apply_pseudo = true);
        virtual element::ptr      find_ancestor(const css_selector& selector, bool apply_pseudo = true,
                                                bool* is_pseudo = nullptr);
        virtual bool              is_ancestor(const ptr& el) const;
        virtual element::ptr      find_adjacent_sibling(const element::ptr& el, const css_selector& selector,
                                                        bool apply_pseudo = true, bool* is_pseudo = nullptr);
        virtual element::ptr      find_sibling(const element::ptr& el, const css_selector& selector,
                                               bool apply_pseudo = true, bool* is_pseudo = nullptr);
        virtual void              get_content_size(size& sz, pixel_t max_width);
        virtual bool              is_nth_child(const element::ptr& el, int num, int off, bool of_type,
                                               const css_selector::vector& selector_list = {}) const;
        virtual bool              is_nth_last_child(const element::ptr& el, int num, int off, bool of_type,
                                                    const css_selector::vector& selector_list = {}) const;
        virtual bool              is_only_child(const element::ptr& el, bool of_type) const;
        virtual void              add_style(const style& style);
        virtual const background* get_background(bool own_only = false);

        virtual std::string                                       dump_get_name();
        virtual std::vector<std::tuple<std::string, std::string>> dump_get_attrs();
        void                                                      dump(litehtml::dumper& cout);

        std::tuple<element::ptr, element::ptr, element::ptr> split_inlines();
        virtual std::shared_ptr<render_item> create_render_item(const std::shared_ptr<render_item>& parent_ri);

        bool requires_styles_update();
        void add_render(const std::shared_ptr<render_item>& ri);
        bool find_styles_changes(const std::function<void(const position&)>& redraw_box);

        element::ptr add_pseudo_before(const style& style)
        {
            return _add_before_after(0, style);
        }
        element::ptr add_pseudo_after(const style& style)
        {
            return _add_before_after(1, style);
        }

        std::string get_counter_value(const std::string& counter_name);
        std::string get_counters_value(const std::vector<std::string>& parameters);
        void        increment_counter(const string_id& counter_name_id, int increment = 1);
        void        reset_counter(const string_id& counter_name_id, int value = 0);

        void run_on_renderers(const std::function<bool(const std::shared_ptr<render_item>&)>& func);

      private:
        std::vector<element::ptr> get_siblings_before() const;
        bool find_counter(string_id counter_name_id, std::map<string_id, int>::iterator& map_iterator);
        void parse_counter_tokens(const std::vector<std::string>& tokens, int default_value,
                                  const std::function<void(string_id, int)>& handler) const;
    };

    //////////////////////////////////////////////////////////////////////////
    //							INLINE FUNCTIONS							//
    //////////////////////////////////////////////////////////////////////////

    inline bool litehtml::element::in_normal_flow() const
    {
        return css().get_position() != element_position_absolute && css().get_display() != display_none &&
               css().get_position() != element_position_fixed;
    }

    inline bool litehtml::element::is_root() const
    {
        return m_parent.expired();
    }

    inline element::ptr litehtml::element::parent() const
    {
        return m_parent.lock();
    }

    inline void litehtml::element::parent(const element::ptr& par)
    {
        m_parent = par;
    }

    inline bool litehtml::element::is_positioned() const
    {
        return (css().get_position() > element_position_static);
    }

    inline bool litehtml::element::is_float() const
    {
        return (css().get_float() != float_none);
    }

    inline std::shared_ptr<document> element::get_document() const
    {
        return m_doc.lock();
    }

    inline const css_properties& element::css() const
    {
        return m_css;
    }

    inline css_properties& element::css_w()
    {
        return m_css;
    }

    inline bool element::is_block_box() const
    {
        return css().get_display() == display_block || css().get_display() == display_flex ||
               css().get_display() == display_table || css().get_display() == display_list_item;
    }

    inline const std::list<std::shared_ptr<element>>& element::children() const
    {
        return m_children;
    }

    inline std::shared_ptr<render_item> element::get_render_item()
    {
        if(m_renders.empty())
        {
            return nullptr;
        }
        return m_renders.front().lock();
    }
} // namespace litehtml

#endif // LITEHTML_ELEMENT_H
