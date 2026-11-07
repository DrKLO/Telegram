#ifndef LITEHTML_DOCUMENT_H
#define LITEHTML_DOCUMENT_H

#include "stylesheet.h"
#include "css_properties.h"
#include "encodings.h"
#include "font_description.h"
#include "master_css.h"
#include "types.h"

#include <atomic>
#include <cstdint>
#include <functional>
#include <unordered_map>
#include <map>
#include <unordered_set>
#include <vector>

using GumboOutput = struct GumboInternalOutput;

namespace litehtml
{
    struct css_text
    {
        using vector = std::vector<css_text>;

        std::string text;
        std::string baseurl;
        std::string media;

        css_text() = default;

        css_text(const char* txt, const char* url, const char* media_str) :
            text(txt ? txt : ""),
            baseurl(url ? url : ""),
            media(media_str ? media_str : "")
        {
        }
    };

    class dumper
    {
      public:
        virtual ~dumper()                                                        = default;
        virtual void begin_node(const std::string& descr)                        = 0;
        virtual void end_node()                                                  = 0;
        virtual void begin_attrs_group(const std::string& descr)                 = 0;
        virtual void end_attrs_group()                                           = 0;
        virtual void add_attr(const std::string& name, const std::string& value) = 0;
    };

    class html_tag;
    class render_item;

    class document : public std::enable_shared_from_this<document>
    {
      public:
        bool take_table_cells(int count);
        std::unordered_set<const element*> skip_paint;
        std::unordered_set<const element*> skip_background_images;
        const element* bare_effects = nullptr;
        bool bare_shadows = false;
        bool has_backdrop = false;
        std::unordered_set<const element*> skip_scroll_content;
        const element* unclipped_scroll = nullptr;
        bool m_paint_only_patch = false;
        bool m_patch_confined   = false;
        std::vector<std::shared_ptr<element>> m_patch_regions;
        uint64_t m_style_epoch = 0;
        // Counts style computations and structure updates, so a caller can tell nothing it derived from computed
        // styles can have changed since it last looked.
        uint64_t style_epoch() const { return m_style_epoch; }
        void     note_style_change() { m_style_epoch++; }
        uint64_t m_layout_epoch       = 1;
        static std::atomic<uint64_t> s_layout_hits;
        static std::atomic<uint64_t> s_layout_misses;
        static std::atomic<uint64_t> s_layout_mismatches;
        position m_layout_viewport;
        std::vector<render_item*> m_layout_marked;
        std::vector<render_item*> m_relayout_roots;
        struct layout_box
        {
            const render_item* item;
            position           box;
        };
        std::vector<layout_box>                      m_layout_before;
        bool                                         m_layout_before_valid = false;
        bool                                         m_layout_after_render = false;
        std::unordered_set<const element*>           m_layout_origins;
        std::vector<std::shared_ptr<element>>        m_restyle_regions;
        bool                                         m_restyle_confined = true;
        void     snapshot_layout();
        void     begin_layout_patch();
        bool     layout_dirty_regions(std::vector<position>& out);
        void     note_layout_origin(const element* el) { m_layout_origins.insert(el); }
        bool     layout_cache_enabled() const;
        bool     layout_cache_check() const;
        uint64_t layout_epoch() const { return m_layout_epoch; }
        void     invalidate_layout_cache() { m_layout_epoch++; }
        void     note_layout_hit() { s_layout_hits.fetch_add(1, std::memory_order_relaxed); }
        void     note_layout_miss() { s_layout_misses.fetch_add(1, std::memory_order_relaxed); }
        void     note_layout_check_mismatch() { s_layout_mismatches.fetch_add(1, std::memory_order_relaxed); }
        void     note_layout_dirty(render_item* item) { m_layout_marked.push_back(item); }
        void     note_relayout_root(render_item* item) { m_relayout_roots.push_back(item); }
        uint64_t m_render_tree_serial = 0;
        uint64_t m_positioned_epoch   = UINT64_MAX;
        uint64_t m_positioned_tree    = UINT64_MAX;
        bool     m_has_positioned     = false;
        using ptr      = std::shared_ptr<document>;
        using weak_ptr = std::weak_ptr<document>;

      private:
        std::shared_ptr<element>                m_root;
        std::shared_ptr<render_item>            m_root_render;
        document_container*                     m_container;
        fonts_map                               m_fonts;
        css_text::vector                        m_css;
        litehtml::css                           m_styles;
        litehtml::web_color                     m_def_color;
        litehtml::css                           m_master_css;

      public:
        const litehtml::css& master_css() const { return m_master_css; }

      private:
        litehtml::css                           m_user_css;
        litehtml::size                          m_size;
        position::vector                        m_fixed_boxes;
        std::shared_ptr<element>                m_over_element;
        std::shared_ptr<element>                m_active_element;
        std::list<std::shared_ptr<render_item>> m_tabular_elements;
        media_query_list_list::vector           m_media_lists;
        media_features                          m_media;
        std::string                             m_lang;
        std::string                             m_culture;
        std::string                             m_text;
        document_mode                           m_mode      = no_quirks_mode;
        bool                                    m_finalized = false;
        std::map<std::string, css_properties>   m_share_cache;
        int                                     m_next_share_id = 0;
        GumboOutput*                            m_gumbo = nullptr;
        bool                                    m_track_nodes = false;
        int                                     m_build_depth    = 0;
        int                                     m_build_elements = 0;
        bool                                    m_build_limited  = false;
        int                                     m_selector_scope = -1;
        int                                     m_ids_selectable = -1;
        int                                     m_style_attributes_selectable = -1;
        std::unordered_set<string_id>           m_left_classes;
        std::unordered_set<string_id>           m_left_ids;
        std::unordered_set<string_id>           m_left_attrs;
        static constexpr int                    max_build_depth    = 256;
        static constexpr int                    max_build_elements = 20000;
        static constexpr int                    max_table_cells    = 200000;
        int                                     m_table_cells      = 0;
        bool                                    m_table_limited    = false;
        std::unordered_map<const void*, std::vector<std::shared_ptr<element>>> m_node_elements;
        std::string                             m_master_styles;
        mutable uint32_t                        m_paint_generation = 1;
        uint64_t                                m_match_epoch      = 1;
        bool                                    m_has_svg          = false;
        bool                                    m_cull_paint       = false;
        struct html_hole
        {
            bool                                  text  = false;
            bool                                  src   = false;
            bool                                  cls   = false;
            const void*                           node  = nullptr;
            std::string                           name;
            std::string                           attr;
            char                                  close = 0;
            std::string                           value;
            std::shared_ptr<element>              owner;
            std::vector<std::shared_ptr<element>> text_elements;
        };
        std::vector<std::string>                m_literals;
        std::vector<html_hole>                  m_holes;
        bool                                    m_template_ok = false;
        std::unordered_map<const element*, size_t> m_style_holes;
        std::unordered_map<const element*, std::vector<size_t>> m_owner_holes;
        std::unordered_map<const void*, std::vector<size_t>> m_node_holes;

      public:
        bool style_sharing_enabled() const;
        bool ids_selectable();
        int  next_share_id()
        {
            return ++m_next_share_id;
        }
        const css_properties* share_lookup(const std::string& key) const
        {
            auto it = m_share_cache.find(key);
            return it == m_share_cache.end() ? nullptr : &it->second;
        }
        void share_store(const std::string& key, const css_properties& props)
        {
            m_share_cache[key] = props;
        }

        document(document_container* objContainer);
        virtual ~document();

        document_container* container() const
        {
            return m_container;
        }
        document_mode mode() const
        {
            return m_mode;
        }
        uint_ptr  get_font(const font_description& descr, font_metrics* fm);
        pixel_t   render(pixel_t max_width, render_type rt = render_all);
        void      draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, bool cull = false);
        uint32_t  paint_generation() const { return m_paint_generation; }
        void      note_svg() { m_has_svg = true; }
        bool      has_svg() const { return m_has_svg; }
        void      invalidate_paint() const { m_paint_generation = m_paint_generation + 1 == 0 ? 1 : m_paint_generation + 1; }
        bool      culls_paint() const { return m_cull_paint; }
        void      set_culls_paint(bool cull) { m_cull_paint = cull; }
        web_color get_def_color() const
        {
            return m_def_color;
        }
        void                         cvt_units(css_length& val, const font_metrics& metrics, pixel_t size) const;
        pixel_t                      to_pixels(const css_length& val, const font_metrics& metrics, pixel_t size) const;
        pixel_t                      width() const;
        pixel_t                      height() const;
        pixel_t                      content_width() const;
        pixel_t                      content_height() const;
        void                         add_stylesheet(const char* str, const char* baseurl, const char* media);
        bool                         on_mouse_over(pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
                                                   const std::function<void(const position&)>& redraw_box);
        std::vector<scroll_values>   on_scroll(pixel_t dx, pixel_t dy, pixel_t x, pixel_t y, pixel_t client_x,
                                               pixel_t client_y) const;
        bool                         on_lbutton_down(pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
                                                     const std::function<void(const position&)>& redraw_box);
        bool                         on_lbutton_up(pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
                                                   const std::function<void(const position&)>& redraw_box);
        bool                         on_button_cancel(const std::function<void(const position&)>& redraw_box);
        bool                         on_mouse_leave(const std::function<void(const position&)>& redraw_box);
        std::shared_ptr<element>     create_element(const char* tag_name, const string_map& attributes);
        std::shared_ptr<element>     root();
        std::shared_ptr<render_item> root_render();
        void                         get_fixed_boxes(position::vector& fixed_boxes);
        void                         add_fixed_box(const position& pos);
        void                         add_media_list(const media_query_list_list::ptr& list);
        bool                         media_changed();
        bool                         lang_changed();
        bool                         match_lang(const std::string& lang);
        void                         add_tabular(const std::shared_ptr<render_item>& el);
        std::shared_ptr<const element> get_over_element() const
        {
            return m_over_element;
        }

        void append_children_from_string(element& parent, const char* str, bool replace_existing);
        void dump(dumper& cout);

        // see doc/document_createFromString.txt
        static document::ptr createFromString(const estring& str, document_container* container,
                                              const std::string& master_styles = litehtml::master_css,
                                              const std::string& user_styles   = {});

        // Like createFromString, but keeps the parse tree so update_from_string can patch the document.
        static document::ptr createUpdatableFromString(const std::string& html, document_container* container,
                                                       const std::string& master_styles);

        // Patches an updatable document to the new HTML in place when only inline `style` attribute values
        // and text change and the element structure is the same. Restyles the changed subtrees and rebuilds
        // the render tree. Returns false and leaves the document unchanged otherwise; the caller then builds
        // a new document. *changed tells whether anything differed.
        bool last_patch_paint_only() const { return m_paint_only_patch; }
        // After a paint-only patch: the elements whose border boxes hold every pixel the patch can change, or
        // false when some change may paint elsewhere.
        bool last_patch_regions(std::vector<std::shared_ptr<element>>& out) const
        {
            if(!m_paint_only_patch || !m_patch_confined)
                return false;
            out = m_patch_regions;
            return true;
        }
        bool update_from_string(const std::string& html, bool* changed);
        // Patches the inline `style` values of the given elements without an HTML string, through the same
        // checks and restyle as update_from_string. Fails, leaving the document unchanged, when an element has
        // no style attribute in the source or a value could not have come from serialized HTML.
        bool update_styles(const std::vector<std::pair<std::shared_ptr<element>, std::string>>& styles, bool* changed);
        struct property_patch
        {
            std::vector<string_id> names;
            std::string            text;
            bool                   box = true;
        };
        struct hole_change
        {
            std::shared_ptr<element> owner;
            std::string              name;
            bool                     text = false;
            std::string              value;
        };
        // Patches attribute values (`name`) and the text of elements whose only content is one text run (`text`)
        // without an HTML string, through the same checks and restyle as update_from_string. Each value must be the
        // exact text serialized HTML would carry there. Fails, leaving the document unchanged, when a change has no
        // matching hole in the source.
        bool update_holes(const std::vector<hole_change>& changes, bool* changed);
        // The HTML the document currently stands for: the parsed source with every patch applied.
        bool template_html(std::string& out) const;

        // The mode must be set before any element is created, because it is used in html_tag::set_attr.
        void set_document_mode(document_mode mode);
        // Finish a document whose element tree was built by the caller with create_element() and
        // element::appendChild() instead of being parsed from an HTML string. Runs the same
        // finalization sequence createFromString() runs after parsing. A document is finalized
        // once, when its tree is complete; any further call does nothing.
        void finalize_from_external_root(const std::shared_ptr<element>& root,
                                         const std::string&              master_styles = litehtml::master_css,
                                         const std::string&              user_styles   = {});

      private:
        uint_ptr add_font(const font_description& descr, font_metrics* fm);
        void     build_template();
        int      update_from_template(const std::string& html, bool* changed);
        int      apply_template_values(std::vector<std::pair<size_t, std::string>>& changes, bool* changed);
        static constexpr int template_requires_fresh_document = -2;
        bool     update_structure(const std::string& html, bool* changed);
        int      selector_scope();
        bool     style_attributes_selectable();
        void     restyle_after_patch(const std::vector<std::shared_ptr<element>>& restyle,
                                     const std::vector<std::shared_ptr<element>>& new_text, bool keep_tree = false,
                                     const std::vector<std::shared_ptr<element>>& retext = {},
                                     const std::unordered_set<const element*>& background_only = {},
                                     const std::unordered_map<const element*, const property_patch*>& patched = {},
                                     const std::unordered_set<const element*>& shallow_width = {});
        bool     retag_allowed(const std::shared_ptr<element>& el, std::string name, bool svg, string_map& attrs,
                               bool blocked);
        bool     apply_retag(const std::shared_ptr<element>& el, const string_map& attrs, bool check, int* region = nullptr);
        void     forget_positions();
        bool     class_change_allowed(const html_hole& hole, const std::string& value, string_map& attrs);
        bool     retext_in_place(const std::vector<std::shared_ptr<element>>& current, const char* text,
                                 std::vector<std::string>& words) const;
        bool     replace_text_renderers(const std::vector<std::shared_ptr<element>>& current,
                                        const elements_list& replacement);

        GumboOutput* parse_html(estring str);
        void         create_node(void* gnode, elements_list& elements, bool parseTextNode, bool process_root);
        bool         update_media_lists(const media_features& features);
        void         fix_tables_layout();
        void fix_table_children(const std::shared_ptr<render_item>& el_ptr, style_display disp, const char* disp_str);
        void fix_table_parent(const std::shared_ptr<render_item>& el_ptr, style_display disp, const char* disp_str);
    };

    inline std::shared_ptr<element> document::root()
    {
        return m_root;
    }

    inline std::shared_ptr<render_item> document::root_render()
    {
        return m_root_render;
    }

    inline void document::add_tabular(const std::shared_ptr<render_item>& el)
    {
        m_tabular_elements.push_back(el);
    }

    inline bool document::match_lang(const std::string& lang)
    {
        return lang == m_lang || lang == m_culture;
    }
} // namespace litehtml

#endif // LITEHTML_DOCUMENT_H
