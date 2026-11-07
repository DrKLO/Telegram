#include "html.h"
#include "document.h"
#include <string_view>
#include <unordered_set>
#include "document_container.h"
#include "css_parser.h"
#include "el_anchor.h"
#include "el_base.h"
#include "el_body.h"
#include "el_break.h"
#include "el_cdata.h"
#include "el_comment.h"
#include "el_div.h"
#include "el_font.h"
#include "el_form.h"
#include "el_svg.h"
#include "el_image.h"
#include <unordered_set>
#include <map>
#include "el_link.h"
#include "el_para.h"
#include "el_script.h"
#include "el_space.h"
#include "el_style.h"
#include "el_table.h"
#include "el_td.h"
#include "el_text.h"
#include "el_title.h"
#include "el_tr.h"
#include "gumbo.h"
#include "html_tag.h"
#include "render_block.h"
#include "render_item.h"
#include "render_inline_context.h"
#include "render_table.h"
#include "stylesheet.h"
#include "types.h"

#include <cstdlib>
#include <cmath>
#include <iterator>
#include <mutex>
#include <typeinfo>
#include <string>
#include <unordered_map>

namespace litehtml
{

    document::document(document_container* container)
    {
        m_container = container;
    }

    document::~document()
    {
        if(m_gumbo)
        {
            gumbo_destroy_output(&kGumboDefaultOptions, m_gumbo);
            m_gumbo = nullptr;
        }
        m_over_element = m_active_element = nullptr;
        if(m_container)
        {
            for(auto& font : m_fonts)
            {
                m_container->delete_font(font.second.font);
            }
        }
    }

    document::ptr document::createFromString(const estring& str, document_container* container,
                                             const std::string& master_styles, const std::string& user_styles)
    {
        // Create litehtml::document
        document::ptr doc = std::make_shared<document>(container);

        // Parse document into GumboOutput
        GumboOutput* output = doc->parse_html(str);

        // mode must be set before doc->create_node because it is used in html_tag::set_attr
        switch(output->document->v.document.doc_type_quirks_mode)
        {
        case GUMBO_DOCTYPE_NO_QUIRKS:
            doc->m_mode = no_quirks_mode;
            break;
        case GUMBO_DOCTYPE_QUIRKS:
            doc->m_mode = quirks_mode;
            break;
        case GUMBO_DOCTYPE_LIMITED_QUIRKS:
            doc->m_mode = limited_quirks_mode;
            break;
        }

        // Create litehtml::elements.
        elements_list root_elements;
        doc->create_node(output->root, root_elements, true, true);
        element::ptr root;
        if(!root_elements.empty())
        {
            root = root_elements.back();
        }

        // Destroy GumboOutput
        gumbo_destroy_output(&kGumboDefaultOptions, output);

        doc->finalize_from_external_root(root, master_styles, user_styles);

        return doc;
    }

    document::ptr document::createUpdatableFromString(const std::string& html, document_container* container,
                                                      const std::string& master_styles)
    {
        document::ptr doc = std::make_shared<document>(container);
        doc->m_text        = html;
        GumboOutput* output = gumbo_parse_with_options(&kGumboDefaultOptions, doc->m_text.data(), doc->m_text.size());
        switch(output->document->v.document.doc_type_quirks_mode)
        {
        case GUMBO_DOCTYPE_NO_QUIRKS:
            doc->m_mode = no_quirks_mode;
            break;
        case GUMBO_DOCTYPE_QUIRKS:
            doc->m_mode = quirks_mode;
            break;
        case GUMBO_DOCTYPE_LIMITED_QUIRKS:
            doc->m_mode = limited_quirks_mode;
            break;
        }
        doc->m_track_nodes = true;
        elements_list root_elements;
        doc->create_node(output->root, root_elements, true, true);
        doc->m_track_nodes = false;
        element::ptr root;
        if(!root_elements.empty())
        {
            root = root_elements.back();
        }
        doc->m_gumbo         = output;
        doc->m_master_styles = master_styles;
        doc->finalize_from_external_root(root, master_styles, {});
        doc->build_template();
        return doc;
    }

    namespace
    {
        bool same_text(const char* a, const char* b)
        {
            return (a ? std::string_view(a) : std::string_view()) == (b ? std::string_view(b) : std::string_view());
        }

        const GumboVector* node_children(const GumboNode* node)
        {
            if(node->type == GUMBO_NODE_DOCUMENT)
                return &node->v.document.children;
            if(node->type == GUMBO_NODE_ELEMENT || node->type == GUMBO_NODE_TEMPLATE)
                return &node->v.element.children;
            return nullptr;
        }

        bool is_element_node(const GumboNode* node)
        {
            return node->type == GUMBO_NODE_ELEMENT || node->type == GUMBO_NODE_TEMPLATE;
        }

        std::string_view element_name(const GumboNode* node)
        {
            const char* known = gumbo_normalized_tagname(node->v.element.tag);
            if(known[0])
                return known;
            GumboStringPiece piece = node->v.element.original_tag;
            if(piece.data && piece.length >= 2 && piece.data[0] == '<' && piece.data[piece.length - 1] == '>')
                gumbo_tag_from_original_text(&piece);
            return piece.data ? std::string_view(piece.data, piece.length) : std::string_view();
        }

        bool same_element_kind(const GumboNode* a, const GumboNode* b)
        {
            if(a->type != b->type || !is_element_node(a))
                return false;
            const GumboElement& ea = a->v.element;
            const GumboElement& eb = b->v.element;
            if(ea.tag != eb.tag || ea.tag_namespace != eb.tag_namespace)
                return false;
            return ea.tag != GUMBO_TAG_UNKNOWN || element_name(a) == element_name(b);
        }

        std::string_view node_key(const GumboNode* node)
        {
            if(!is_element_node(node))
                return {};
            const GumboVector& attrs = node->v.element.attributes;
            if(attrs.length)
            {
                auto* first = static_cast<const GumboAttribute*>(attrs.data[0]);
                if(first->name && !strcmp(first->name, "data-lh-id"))
                    return first->value ? std::string_view(first->value) : std::string_view();
            }
            const GumboAttribute* attr = gumbo_get_attribute(&attrs, "data-lh-id");
            return attr && attr->value ? std::string_view(attr->value) : std::string_view();
        }

        bool raw_text_element(const GumboNode* node)
        {
            if(!is_element_node(node))
                return false;
            const GumboTag tag = node->v.element.tag;
            return tag == GUMBO_TAG_STYLE || tag == GUMBO_TAG_SCRIPT || tag == GUMBO_TAG_TITLE || tag == GUMBO_TAG_TEXTAREA ||
                   tag == GUMBO_TAG_NOSCRIPT || tag == GUMBO_TAG_XMP || tag == GUMBO_TAG_IFRAME || tag == GUMBO_TAG_NOEMBED ||
                   tag == GUMBO_TAG_NOFRAMES || tag == GUMBO_TAG_PLAINTEXT;
        }

        bool has_document_level_element(const GumboNode* node)
        {
            if(is_element_node(node))
            {
                switch(node->v.element.tag)
                {
                case GUMBO_TAG_HTML:
                case GUMBO_TAG_HEAD:
                case GUMBO_TAG_BODY:
                case GUMBO_TAG_STYLE:
                case GUMBO_TAG_LINK:
                case GUMBO_TAG_BASE:
                case GUMBO_TAG_META:
                case GUMBO_TAG_TITLE:
                case GUMBO_TAG_SCRIPT:
                case GUMBO_TAG_FRAMESET:
                case GUMBO_TAG_SELECT:
                case GUMBO_TAG_OPTION:
                case GUMBO_TAG_OPTGROUP:
                case GUMBO_TAG_DATALIST:
                    return true;
                default:
                    break;
                }
            }
            if(const GumboVector* children = node_children(node))
            {
                for(unsigned int i = 0; i < children->length; i++)
                {
                    if(has_document_level_element(static_cast<const GumboNode*>(children->data[i])))
                        return true;
                }
            }
            return false;
        }

        bool mentions_counters(const std::string& text)
        {
            for(size_t i = 0; i + 7 <= text.size(); i++)
            {
                if((text[i] | 0x20) != 'c' || t_strncasecmp(text.data() + i, "counter", 7) != 0)
                    continue;
                const size_t next = i + 7;
                if(next < text.size() && (text[next] == '-' || text[next] == '(' || ((text[next] | 0x20) == 's')))
                    return true;
            }
            return false;
        }

        void count_elements(const GumboNode* node, int depth, int& elements, int& deepest)
        {
            if(is_element_node(node))
            {
                elements++;
                deepest = std::max(deepest, depth);
                depth++;
            }
            if(const GumboVector* children = node_children(node))
            {
                for(unsigned int i = 0; i < children->length; i++)
                    count_elements(static_cast<const GumboNode*>(children->data[i]), depth, elements, deepest);
            }
        }

        std::string_view quoted_value(const GumboAttribute* attr, char close)
        {
            const GumboStringPiece& v = attr->original_value;
            if(!v.data || v.length < 2 || v.data[0] != close || v.data[v.length - 1] != close)
                return {};
            return std::string_view(v.data + 1, v.length - 2);
        }

        struct selector_names
        {
            std::unordered_set<string_id>& classes;
            std::unordered_set<string_id>& ids;
            std::unordered_set<string_id>& attrs;
        };

        void collect_ancestor_names(const css_selector& selector, bool self, selector_names& names)
        {
            for(const css_selector* sel = &selector; sel; sel = sel->m_left.get())
            {
                const bool ancestor = !self || sel != &selector;
                for(const auto& attr : sel->m_right.m_attrs)
                {
                    if(ancestor)
                    {
                        if(attr.type == select_class)
                            names.classes.insert(attr.name);
                        else if(attr.type == select_id)
                            names.ids.insert(attr.name);
                        else if(attr.type == select_attr)
                            names.attrs.insert(attr.name);
                        else if(attr.type == select_pseudo_class && attr.name == _checked_)
                        {
                            names.attrs.insert(_id("checked"));
                            names.attrs.insert(_id("selected"));
                        } else if(attr.type == select_pseudo_class && (attr.name == _disabled_ || attr.name == _enabled_))
                            names.attrs.insert(_id("disabled"));
                    }
                    for(const auto& inner : attr.selector_list)
                    {
                        if(inner)
                            collect_ancestor_names(*inner, !ancestor, names);
                    }
                }
            }
        }

        bool restylable_in_place(const element& el)
        {
            const std::type_info& type = typeid(el);
            return type == typeid(html_tag) || type == typeid(el_div) || type == typeid(el_para) || type == typeid(el_td) ||
                   type == typeid(el_tr) || type == typeid(el_body);
        }

        void scan_selector(const css_selector& selector, bool& attributes, bool& positions)
        {
            for(const css_selector* sel = &selector; sel; sel = sel->m_left.get())
            {
                if(sel->m_left && (sel->m_combinator == combinator_adjacent_sibling || sel->m_combinator == combinator_general_sibling))
                {
                    attributes = true;
                    positions  = true;
                }
                for(const auto& attr : sel->m_right.m_attrs)
                {
                    if(attr.type == select_pseudo_class)
                    {
                        switch(attr.name)
                        {
                        case _only_child_:
                        case _only_of_type_:
                        case _first_child_:
                        case _first_of_type_:
                        case _last_child_:
                        case _last_of_type_:
                        case _nth_child_:
                        case _nth_of_type_:
                        case _nth_last_child_:
                        case _nth_last_of_type_:
                            positions = true;
                            if(!attr.selector_list.empty())
                                attributes = true;
                            break;
                        default:
                            break;
                        }
                    }
                    for(const auto& inner : attr.selector_list)
                    {
                        if(inner)
                            scan_selector(*inner, attributes, positions);
                    }
                }
            }
        }
    }

    // The document as literal text with holes at its style attribute values and text nodes. A frame that
    // differs only inside the holes is matched without parsing: a quoted attribute value cannot contain its
    // quote and text cannot contain '<', so each hole ends at the first delimiter that starts the next literal.
    static bool svg_geometry_attribute(const char* name)
    {
        static const std::unordered_set<std::string>& names = *new std::unordered_set<std::string>{
            "points", "d", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "dx", "dy", "transform",
            "fill", "stroke", "stroke-width", "stroke-dasharray", "stroke-dashoffset", "opacity", "fill-opacity",
            "stroke-opacity", "offset", "stop-color", "stop-opacity", "text-anchor", "width", "height", "display", "visibility",
        };
        return names.count(name) != 0;
    }

    static std::vector<std::pair<std::string, std::string>> split_style_declarations(const std::string& text)
    {
        std::vector<std::pair<std::string, std::string>> out;
        size_t start = 0;
        int    depth = 0;
        char   quote = 0;
        for(size_t i = 0; i <= text.size(); i++)
        {
            const char c = i < text.size() ? text[i] : ';';
            if(quote)
            {
                if(c == quote)
                    quote = 0;
                continue;
            }
            if(c == '"' || c == '\'')
                quote = c;
            else if(c == '(')
                depth++;
            else if(c == ')')
                depth--;
            else if(c == ';' && depth <= 0)
            {
                std::string decl = text.substr(start, i - start);
                start = i + 1;
                size_t colon = decl.find(':');
                if(colon == std::string::npos)
                {
                    if(decl.find_first_not_of(" \t\n") != std::string::npos)
                        out.emplace_back(decl, std::string());
                    continue;
                }
                size_t a = decl.find_first_not_of(" \t\n"), b = decl.find_last_not_of(" \t\n", colon - 1);
                std::string name = a == std::string::npos || a >= colon ? std::string() : decl.substr(a, b - a + 1);
                if(name.compare(0, 2, "--") != 0)
                    for(char& ch : name)
                        if(ch >= 'A' && ch <= 'Z')
                            ch = static_cast<char>(ch - 'A' + 'a');
                out.emplace_back(name, decl.substr(colon + 1));
            }
        }
        return out;
    }

    // A style hole may be patched in place unless the edit touches custom properties, var() or counters,
    // whose effects reach other elements.
    static bool paint_only_property(const std::string& name)
    {
        static const std::unordered_set<std::string>& paint = *new std::unordered_set<std::string>{
            "color", "background", "background-color", "background-image", "background-position", "background-repeat",
            "opacity", "transform", "transform-origin", "box-shadow", "text-shadow", "filter", "outline", "outline-color",
            "outline-width", "outline-style", "outline-offset", "visibility", "fill", "fill-opacity", "stroke", "stroke-opacity",
            "stroke-width", "stroke-dasharray", "stroke-dashoffset", "stop-color", "stop-opacity", "border-color",
            "border-top-color", "border-right-color", "border-bottom-color", "border-left-color", "text-decoration-color",
            "caret-color", "cursor", "-lh-anim"};
        return paint.count(name) != 0;
    }

    static bool style_change_is_background_color(const std::string& before, const std::string& after)
    {
        if(getenv("LH_NO_BACKGROUND_PATCH") || before.find_first_of("\\{}") != std::string::npos ||
           after.find_first_of("\\{}") != std::string::npos || before.find("/*") != std::string::npos || after.find("/*") != std::string::npos ||
           before.find("counter") != std::string::npos || after.find("counter") != std::string::npos)
            return false;
        auto a = split_style_declarations(before), b = split_style_declarations(after);
        auto remove_background = [](auto& declarations) {
            declarations.erase(std::remove_if(declarations.begin(), declarations.end(), [](const auto& entry) {
                return entry.first == "background-color" || entry.first == "-lh-anim";
            }), declarations.end());
        };
        remove_background(a);
        remove_background(b);
        return a == b;
    }

    static bool style_change_property_patch(const std::string& before, const std::string& after, document::property_patch& patch)
    {
        static const bool disabled = getenv("LH_NO_PROPERTY_PATCH") != nullptr;
        if(disabled)
            return false;
        for(const std::string* text : {&before, &after})
        {
            if(text->find_first_of("\\{}!\"'") != std::string::npos || text->find("/*") != std::string::npos ||
               text->find("var(") != std::string::npos || text->find("--") != std::string::npos ||
               text->find("counter") != std::string::npos || text->find("-lh-") != std::string::npos ||
               text->find("url(") != std::string::npos)
                return false;
        }
        auto a = split_style_declarations(before), b = split_style_declarations(after);
        std::map<std::string, std::string> left, right;
        auto collect = [](const std::vector<std::pair<std::string, std::string>>& list, std::map<std::string, std::string>& out) {
            for(const auto& d : list)
            {
                if(d.first.empty() || d.first == "all" || d.first == "background" ||
                   d.first.find_first_not_of("abcdefghijklmnopqrstuvwxyz-") != std::string::npos ||
                   !out.emplace(d.first, d.second).second)
                    return false;
            }
            return true;
        };
        if(!collect(a, left) || !collect(b, right))
            return false;
        for(const auto& d : left)
            if(!right.count(d.first))
                return false;
        for(const auto& d : b)
        {
            auto it = left.find(d.first);
            if(it != left.end() && it->second == d.second)
                continue;
            string_id id;
            if(d.first == "transform")
                id = _transform_;
            else if(d.first == "opacity")
                id = _opacity_;
            else if(d.first == "background-color")
                id = _background_color_;
            else
                return false;
            if(id != _background_color_)
                patch.box = false;
            patch.names.push_back(id);
            patch.text += d.first;
            patch.text += ':';
            patch.text += d.second;
            patch.text += ';';
        }
        return !patch.names.empty();
    }

    static bool style_change_is_width_only(const std::string& before, const std::string& after)
    {
        std::map<std::string, std::string> declarations[2];
        std::vector<std::pair<std::string, std::string>> unchanged[2];
        size_t index = 0;
        for(const std::string* text : {&before, &after})
        {
            const std::string lower = lowcase(*text);
            if(text->find_first_of("\\{}!\"'") != std::string::npos || text->find("/*") != std::string::npos ||
               lower.find("var(") != std::string::npos || lower.find("--") != std::string::npos ||
               lower.find("counter") != std::string::npos || lower.find("-lh-") != std::string::npos ||
               lower.find("url(") != std::string::npos)
                return false;
            auto& values = declarations[index++];
            for(const auto& entry : split_style_declarations(*text))
            {
                if(entry.first.empty() || entry.first == "all" ||
                   entry.first.find_first_not_of("abcdefghijklmnopqrstuvwxyz-") != std::string::npos ||
                   !values.emplace(entry.first, entry.second).second)
                    return false;
                if(entry.first != "width")
                    unchanged[index - 1].push_back(entry);
            }
            auto width = values.find("width");
            if(width == values.end())
                return false;
            const css_token_vector tokens = normalize(width->second, f_componentize);
            css_length length;
            if(tokens.size() != 1 || !length.from_token(tokens.front(), f_length_percentage | f_positive) ||
               length.is_predefined() || !std::isfinite(length.val()) ||
               (length.units() != css_units_px && length.units() != css_units_percentage))
                return false;
            values.erase(width);
        }
        return unchanged[0] == unchanged[1];
    }

    static bool shallow_width_owner(const element::ptr& owner)
    {
        const std::type_info& type = typeid(*owner);
        if(type != typeid(html_tag) && type != typeid(el_div) && type != typeid(el_para) && type != typeid(el_body))
            return false;
        switch(owner->css().get_display())
        {
        case display_block:
        case display_inline_block:
        case display_flex:
        case display_inline_flex:
        case display_grid:
        case display_inline_grid:
            return std::static_pointer_cast<html_tag>(owner)->can_restyle_width_shallow();
        default:
            return false;
        }
    }

    static bool has_generated_children(const element::ptr& owner)
    {
        for(const auto& child : owner->children())
        {
            if(child->tag() == __tag_before_ || child->tag() == __tag_after_)
                return true;
        }
        return false;
    }

    static bool style_change_is_paint_only(const std::string& before, const std::string& after)
    {
        auto a = split_style_declarations(before), b = split_style_declarations(after);
        std::map<std::string, std::string> left, right;
        for(const auto& d : a)
            left[d.first] = d.second;
        for(const auto& d : b)
            right[d.first] = d.second;
        for(const auto& d : left)
        {
            auto it = right.find(d.first);
            if((it == right.end() || it->second != d.second) && !paint_only_property(d.first))
                return false;
        }
        for(const auto& d : right)
        {
            if(!left.count(d.first) && !paint_only_property(d.first))
                return false;
        }
        return true;
    }

    // The edit only repaints inside the element's own border box (svg = false), or inside the outermost <svg>
    // around it (svg = true); nothing it changes is inherited or paints outside those boxes.
    static int style_change_region(const std::string& before, const std::string& after)
    {
        static const std::unordered_set<std::string>& box = *new std::unordered_set<std::string>{
            "background", "background-color", "background-image", "background-position", "background-repeat",
            "border-color", "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
            "caret-color", "cursor", "-lh-anim"};
        static const std::unordered_set<std::string>& svg = *new std::unordered_set<std::string>{
            "fill", "fill-opacity", "stroke", "stroke-opacity", "stroke-width", "stroke-dasharray", "stroke-dashoffset",
            "stop-color", "stop-opacity"};
        auto a = split_style_declarations(before), b = split_style_declarations(after);
        std::map<std::string, std::string> left, right;
        for(const auto& d : a)
            left[d.first] = d.second;
        for(const auto& d : b)
            right[d.first] = d.second;
        int  region = 1;
        auto note   = [&](const std::string& name) {
            if(box.count(name))
                return;
            if(name == "color" || name == "text-decoration-color")
            {
                if(region == 1)
                    region = 3;
                return;
            }
            region = svg.count(name) && region > 0 ? 2 : 0;
        };
        for(const auto& d : left)
        {
            auto it = right.find(d.first);
            if(it == right.end() || it->second != d.second)
                note(d.first);
        }
        for(const auto& d : right)
        {
            if(!left.count(d.first))
                note(d.first);
        }
        return region;
    }

    static int declared_region(const std::string& name)
    {
        static const std::unordered_set<std::string>& box = *new std::unordered_set<std::string>{
            "border-color", "border-top-color", "border-right-color", "border-bottom-color", "border-left-color", "caret-color", "cursor"};
        if(name.compare(0, 10, "background") == 0 || box.count(name))
            return 1;
        if(name == "color" || name == "text-decoration-color")
            return 3;
        return 0;
    }

    static bool push_text_owner_region(const element::ptr& owner, std::vector<element::ptr>& regions)
    {
        static const bool disabled = getenv("LH_NO_COLOR_REGIONS") != nullptr;
        if(disabled || !owner || owner->children().empty() || owner->css().get_display() == display_list_item)
            return false;
        for(const auto& child : owner->children())
        {
            if(!child->is_text())
                return false;
        }
        regions.push_back(owner);
        for(const auto& child : owner->children())
        {
            if(typeid(*child) == typeid(el_text))
                regions.push_back(child);
        }
        return true;
    }

    static bool text_regions_disabled()
    {
        static const bool disabled = getenv("LH_NO_TEXT_REGIONS") != nullptr;
        return disabled;
    }

    static element::ptr outermost_svg(const element::ptr& el)
    {
        element::ptr found;
        if(!el)
            return found;
        if(auto doc = el->get_document(); doc && !doc->has_svg())
            return found;
        for(auto p = el; p; p = p->parent())
        {
            if(std::dynamic_pointer_cast<el_svg>(p))
                found = p;
        }
        return found;
    }

    static bool style_change_is_local(const std::string& before, const std::string& after)
    {
        if(before.find("counter") != std::string::npos || after.find("counter") != std::string::npos)
            return false;
        auto a = split_style_declarations(before), b = split_style_declarations(after);
        auto sensitive = [](const std::pair<std::string, std::string>& d) {
            return d.first.compare(0, 2, "--") == 0;
        };
        size_t i = 0;
        while(i < a.size() && i < b.size() && a[i] == b[i])
            i++;
        size_t ea = a.size(), eb = b.size();
        while(ea > i && eb > i && a[ea - 1] == b[eb - 1])
        {
            ea--;
            eb--;
        }
        for(size_t k = i; k < ea; k++)
            if(sensitive(a[k]))
                return false;
        for(size_t k = i; k < eb; k++)
            if(sensitive(b[k]))
                return false;
        return true;
    }

    void document::build_template()
    {
        m_template_ok = false;
        m_literals.clear();
        m_holes.clear();
        m_style_holes.clear();
        m_owner_holes.clear();
        m_node_holes.clear();
        if(!m_gumbo)
            return;
        const char* base = m_text.data();
        const size_t size = m_text.size();
        struct span
        {
            size_t   start, end;
            html_hole hole;
        };
        std::vector<span> spans;
        bool              ok = true;
        std::unordered_set<std::string> selected;
        for(const char* p = static_cast<const char*>(memchr(base, '[', size)); p; p = static_cast<const char*>(memchr(p + 1, '[', base + size - p - 1)))
        {
            const char* q = p + 1;
            while(q < base + size && (isalnum(static_cast<unsigned char>(*q)) || *q == '-' || *q == '_' || *q == ':'))
                q++;
            if(q > p + 1)
                selected.emplace(p + 1, q - p - 1);
            if(q >= base + size - 1)
                break;
        }
        std::function<void(const GumboNode*, bool)> walk = [&](const GumboNode* node, bool raw) {
            if(!ok)
                return;
            switch(node->type)
            {
            case GUMBO_NODE_DOCUMENT:
                for(unsigned int i = 0; i < node->v.document.children.length; i++)
                    walk(static_cast<const GumboNode*>(node->v.document.children.data[i]), false);
                break;
            case GUMBO_NODE_ELEMENT:
            case GUMBO_NODE_TEMPLATE:
            {
                const GumboElement& e = node->v.element;
                if(m_node_elements.find(node) == m_node_elements.end())
                    break;
                for(unsigned int i = 0; i < e.attributes.length; i++)
                {
                    auto*      attr = static_cast<const GumboAttribute*>(e.attributes.data[i]);
                    const bool src  = (e.tag == GUMBO_TAG_IMG || e.tag == GUMBO_TAG_CANVAS) && strcmp(attr->name, "src") == 0;
                    const bool geometry = !src && e.tag_namespace == GUMBO_NAMESPACE_SVG && e.tag != GUMBO_TAG_SVG &&
                                          svg_geometry_attribute(attr->name) && !selected.count(attr->name);
                    const bool cls = !src && !geometry && e.tag_namespace == GUMBO_NAMESPACE_HTML && strcmp(attr->name, "class") == 0;
                    if(!src && !geometry && !cls && strcmp(attr->name, "style") != 0)
                        continue;
                    const GumboStringPiece& v = attr->original_value;
                    auto it = m_node_elements.find(node);
                    if(!v.data || v.length < 2 || (v.data[0] != '"' && v.data[0] != '\'') || v.data < base ||
                       v.data + v.length > base + size || it == m_node_elements.end() || it->second.size() != 1)
                    {
                        if(cls)
                            continue;
                        ok = false;
                        return;
                    }
                    span s;
                    s.start       = (size_t)(v.data - base) + 1;
                    s.end         = (size_t)(v.data - base) + v.length - 1;
                    s.hole.close  = v.data[0];
                    s.hole.src    = src;
                    s.hole.cls    = cls;
                    if(geometry)
                        s.hole.attr = attr->name;
                    s.hole.owner  = it->second.front();
                    s.hole.node   = node;
                    s.hole.name   = attr->name;
                    s.hole.value  = std::string(base + s.start, s.end - s.start);
                    spans.push_back(std::move(s));
                }
                const char* content = e.original_tag.data ? e.original_tag.data + e.original_tag.length : nullptr;
                const bool textarea_newline = e.tag == GUMBO_TAG_TEXTAREA && content && content < base + size &&
                                              (*content == '\n' || *content == '\r');
                const bool child_raw = e.tag == GUMBO_TAG_STYLE || e.tag == GUMBO_TAG_SCRIPT || e.tag == GUMBO_TAG_TITLE ||
                                       textarea_newline || e.tag == GUMBO_TAG_NOSCRIPT;
                for(unsigned int i = 0; i < e.children.length; i++)
                    walk(static_cast<const GumboNode*>(e.children.data[i]), child_raw);
                break;
            }
            case GUMBO_NODE_TEXT:
            {
                if(raw)
                    break;
                const GumboStringPiece& t  = node->v.text.original_text;
                auto                    it = m_node_elements.find(node);
                if(!t.data || t.length == 0 || t.data < base || t.data + t.length > base + size ||
                   it == m_node_elements.end() || it->second.empty())
                {
                    ok = false;
                    return;
                }
                span s;
                s.start                = (size_t)(t.data - base);
                s.end                  = s.start + t.length;
                s.hole.text            = true;
                s.hole.node            = node;
                s.hole.text_elements   = it->second;
                s.hole.value           = std::string(base + s.start, t.length);
                if(s.end >= size || base[s.end] != '<')
                {
                    ok = false;
                    return;
                }
                spans.push_back(std::move(s));
                break;
            }
            default:
                break;
            }
        };
        walk(m_gumbo->document, false);
        if(!ok)
            return;
        std::sort(spans.begin(), spans.end(), [](const span& a, const span& b) { return a.start < b.start; });
        size_t pos = 0;
        for(auto& s : spans)
        {
            if(s.start < pos)
                return;
            m_literals.emplace_back(base + pos, s.start - pos);
            m_holes.push_back(std::move(s.hole));
            pos = s.end;
        }
        m_literals.emplace_back(base + pos, size - pos);
        for(size_t i = 0; i < m_holes.size(); i++)
            m_node_holes[m_holes[i].node].push_back(i);
        m_template_ok = true;
    }

    namespace
    {
        bool table_part(style_display display)
        {
            switch(display)
            {
            case display_table_cell:
            case display_table_row:
            case display_table_column:
            case display_table_column_group:
            case display_table_row_group:
            case display_table_header_group:
            case display_table_footer_group:
            case display_table_caption:
                return true;
            default:
                return false;
            }
        }

        bool tree_neutral_change(const std::string& before, const std::string& after, bool inline_box, bool table_cached)
        {
            static const std::unordered_set<std::string>& neutral = *new std::unordered_set<std::string>{
                "width", "height", "min-width", "min-height", "max-width", "max-height", "left", "top", "right", "bottom",
                "margin", "margin-left", "margin-right", "margin-top", "margin-bottom", "padding", "padding-left",
                "padding-right", "padding-top", "padding-bottom", "border-radius", "border-top-left-radius",
                "border-top-right-radius", "border-bottom-left-radius", "border-bottom-right-radius", "border-color",
                "border-top-color", "border-right-color", "border-bottom-color", "border-left-color", "color", "background",
                "background-color", "background-image", "background-position", "background-size", "opacity", "transform",
                "transform-origin", "box-shadow", "text-shadow", "outline-color", "filter", "fill", "stroke", "stroke-width",
                "stroke-dasharray", "stroke-dashoffset", "fill-opacity", "stroke-opacity", "z-index", "text-align",
                "vertical-align", "letter-spacing", "flex-grow", "flex-shrink", "flex-basis", "gap", "row-gap", "column-gap",
                "justify-content", "align-items", "align-self"};
            auto parse = [](const std::string& text, std::map<std::string, std::string>& out) {
                if(text.find_first_of("\"'\\{}") != std::string::npos || text.find("/*") != std::string::npos ||
                   text.find("url(") != std::string::npos)
                    return false;
                size_t start = 0;
                while(start <= text.size())
                {
                    size_t end   = text.find(';', start);
                    std::string part = text.substr(start, end == std::string::npos ? std::string::npos : end - start);
                    size_t colon = part.find(':');
                    if(colon != std::string::npos)
                    {
                        std::string name = part.substr(0, colon);
                        name.erase(0, name.find_first_not_of(" \t\n"));
                        name.erase(name.find_last_not_of(" \t\n") + 1);
                        for(char& c : name)
                            c = char(tolower(static_cast<unsigned char>(c)));
                        out[name] = part.substr(colon + 1);
                    } else if(part.find_first_not_of(" \t\n") != std::string::npos)
                        return false;
                    if(end == std::string::npos)
                        break;
                    start = end + 1;
                }
                return true;
            };
            std::map<std::string, std::string> a, b;
            if(!parse(before, a) || !parse(after, b))
                return false;
            auto allowed = [&](const std::string& name) {
                if(inline_box && (name.compare(0, 6, "margin") == 0 || name.compare(0, 7, "padding") == 0 || name == "left" ||
                                  name == "top" || name == "right" || name == "bottom"))
                    return false;
                if(table_cached && (name == "width" || name == "height" || name.compare(0, 4, "min-") == 0 ||
                                    name.compare(0, 4, "max-") == 0 || name.compare(0, 7, "padding") == 0 ||
                                    name.compare(0, 6, "margin") == 0))
                    return false;
                return neutral.count(name) != 0;
            };
            for(const auto& kv : a)
            {
                auto it = b.find(kv.first);
                if((it == b.end() || it->second != kv.second) && !allowed(kv.first))
                    return false;
            }
            for(const auto& kv : b)
            {
                if(!a.count(kv.first) && !allowed(kv.first))
                    return false;
            }
            return true;
        }
    }

    // 1: patched (or nothing changed), 0: the frame does not fit the template, -1: fits but needs a rebuild.
    int document::update_from_template(const std::string& html, bool* changed)
    {
        invalidate_paint();
        std::vector<std::pair<size_t, std::string>> changes;
        size_t                                      pos = 0;
        for(size_t i = 0; i < m_holes.size(); i++)
        {
            const std::string& literal = m_literals[i];
            if(html.compare(pos, literal.size(), literal) != 0)
                return 0;
            pos += literal.size();
            size_t end = html.find(m_holes[i].text ? '<' : m_holes[i].close, pos);
            if(end == std::string::npos)
                return 0;
            if(html.compare(pos, end - pos, m_holes[i].value) != 0)
                changes.emplace_back(i, html.substr(pos, end - pos));
            pos = end;
        }
        if(html.size() - pos != m_literals.back().size() || html.compare(pos, std::string::npos, m_literals.back()) != 0)
            return 0;
        return apply_template_values(changes, changed);
    }

    bool document::update_styles(const std::vector<std::pair<std::shared_ptr<element>, std::string>>& styles, bool* changed)
    {
        invalidate_paint();
        m_paint_only_patch = false;
        m_patch_confined   = false;
        m_patch_regions.clear();
        if(changed)
            *changed = false;
        if(!m_root || !m_finalized || !m_template_ok || getenv("LH_NO_TEMPLATE"))
            return false;
        if(m_style_holes.empty())
        {
            for(size_t i = 0; i < m_holes.size(); i++)
            {
                const html_hole& hole = m_holes[i];
                if(!hole.text && !hole.src && !hole.cls && hole.attr.empty() && hole.owner)
                    m_style_holes.emplace(hole.owner.get(), i);
            }
        }
        std::vector<std::pair<size_t, std::string>> changes;
        changes.reserve(styles.size());
        for(const auto& entry : styles)
        {
            auto it = entry.first ? m_style_holes.find(entry.first.get()) : m_style_holes.end();
            if(it == m_style_holes.end())
                return false;
            const html_hole& hole = m_holes[it->second];
            if(entry.second.find(hole.close) != std::string::npos || entry.second.find('<') != std::string::npos)
                return false;
            if(entry.second == hole.value)
                continue;
            for(const auto& change : changes)
            {
                if(change.first == it->second)
                    return false;
            }
            changes.emplace_back(it->second, entry.second);
        }
        std::sort(changes.begin(), changes.end(), [](const auto& a, const auto& b) { return a.first < b.first; });
        return apply_template_values(changes, changed) == 1;
    }

    void document::begin_layout_patch()
    {
        m_layout_origins.clear();
        m_restyle_regions.clear();
        m_restyle_confined    = true;
        m_layout_before_valid = false;
        m_layout_after_render = false;
        if(m_root_render && layout_cache_enabled())
            snapshot_layout();
    }

    bool document::update_holes(const std::vector<hole_change>& changes, bool* changed)
    {
        invalidate_paint();
        m_paint_only_patch = false;
        m_patch_confined   = false;
        m_patch_regions.clear();
        if(changed)
            *changed = false;
        if(!m_root || !m_finalized || !m_template_ok || getenv("LH_NO_TEMPLATE"))
            return false;
        if(m_owner_holes.empty())
        {
            for(size_t i = 0; i < m_holes.size(); i++)
            {
                const html_hole& hole = m_holes[i];
                const element*   owner = nullptr;
                if(!hole.text)
                    owner = hole.owner.get();
                else if(!hole.text_elements.empty() && hole.text_elements.front()->parent())
                    owner = hole.text_elements.front()->parent().get();
                if(owner)
                    m_owner_holes[owner].push_back(i);
            }
        }
        std::vector<std::pair<size_t, std::string>> list;
        std::vector<size_t>                         seen;
        list.reserve(changes.size());
        seen.reserve(changes.size());
        for(const auto& change : changes)
        {
            auto it = change.owner ? m_owner_holes.find(change.owner.get()) : m_owner_holes.end();
            if(it == m_owner_holes.end())
                return false;
            size_t found = m_holes.size();
            for(size_t i : it->second)
            {
                const html_hole& hole = m_holes[i];
                if(change.text ? !hole.text : (hole.text || hole.name != change.name))
                    continue;
                if(found != m_holes.size())
                    return false;
                found = i;
            }
            if(found == m_holes.size())
                return false;
            const html_hole& hole = m_holes[found];
            if(change.value.find('<') != std::string::npos || (!hole.text && change.value.find(hole.close) != std::string::npos))
                return false;
            if(hole.text && (hole.text_elements.empty() || hole.text_elements.front()->parent().get() != change.owner.get()))
                return false;
            if(std::find(seen.begin(), seen.end(), found) != seen.end())
                return false;
            seen.push_back(found);
            if(change.value != hole.value)
                list.emplace_back(found, change.value);
        }
        std::sort(list.begin(), list.end(), [](const auto& a, const auto& b) { return a.first < b.first; });
        return apply_template_values(list, changed) == 1;
    }

    bool document::template_html(std::string& out) const
    {
        if(!m_template_ok || m_literals.size() != m_holes.size() + 1)
            return false;
        size_t size = 0;
        for(const auto& literal : m_literals)
            size += literal.size();
        for(const auto& hole : m_holes)
            size += hole.value.size();
        out.clear();
        out.reserve(size);
        for(size_t i = 0; i < m_holes.size(); i++)
        {
            out += m_literals[i];
            out += m_holes[i].value;
        }
        out += m_literals.back();
        return true;
    }

    bool document::class_change_allowed(const html_hole& hole, const std::string& value, string_map& attrs)
    {
        static const bool disabled = getenv("LH_NO_CLASS_HOLES") != nullptr;
        if(disabled || !hole.owner || !hole.owner->parent() || (selector_scope() & 5) != 0)
            return false;
        for(auto p = hole.owner->parent(); p; p = p->parent())
        {
            if(p->tag() == _select_)
                return false;
        }
        auto tag = std::dynamic_pointer_cast<html_tag>(hole.owner);
        if(!tag)
            return false;
        attrs          = tag->attributes();
        attrs["class"] = value;
        return retag_allowed(hole.owner, hole.owner->get_tagName(), false, attrs, false);
    }

    int document::apply_template_values(std::vector<std::pair<size_t, std::string>>& changes, bool* changed)
    {
        begin_layout_patch();
        invalidate_paint();
        std::vector<size_t>                             dirty;
        std::vector<bool> background_changes(changes.size(), false);
        std::vector<bool> width_changes(changes.size(), false);
        std::vector<property_patch> property_patches(changes.size());
        std::vector<std::pair<size_t, string_map>>      classes;
        for(const auto& change : changes)
        {
            const size_t       i = change.first;
            const std::string& v = change.second;
            if(i >= m_holes.size() || v == m_holes[i].value)
                continue;
            if(v.find_first_of("&\r") != std::string::npos || v.find('\0') != std::string::npos)
                return -1;
            if(m_holes[i].cls)
            {
                string_map attrs;
                if(!class_change_allowed(m_holes[i], v, attrs))
                    return -1;
                classes.emplace_back(&change - changes.data(), std::move(attrs));
                continue;
            }
            if(m_holes[i].text)
            {
                if(v.find_first_not_of(" \t\n\f") == std::string::npos)
                    return -1;
            } else if(!m_holes[i].src && m_holes[i].attr.empty())
            {
                if(style_attributes_selectable())
                    return template_requires_fresh_document;
                property_patch& patch = property_patches[&change - changes.data()];
                if(!style_change_property_patch(m_holes[i].value, v, patch))
                {
                    patch.names.clear();
                    width_changes[&change - changes.data()] = style_change_is_width_only(m_holes[i].value, v);
                    if(width_changes[&change - changes.data()] && has_generated_children(m_holes[i].owner))
                        return template_requires_fresh_document;
                    const bool background = style_change_is_background_color(m_holes[i].value, v);
                    background_changes[&change - changes.data()] = background;
                    if(!background && !style_change_is_local(m_holes[i].value, v))
                        return -1;
                }
            }
            if(m_holes[i].text && !m_holes[i].text_elements.front()->parent())
                return -1;
            if(m_holes[i].text && v[0] == '\n')
            {
                const char* tag = m_holes[i].text_elements.front()->parent()->get_tagName();
                if(tag && (!strcmp(tag, "pre") || !strcmp(tag, "listing") || !strcmp(tag, "textarea")))
                    return -1;
            }
            dirty.push_back(&change - changes.data());
        }
        if(dirty.empty() && classes.empty())
        {
            m_paint_only_patch = true;
            m_patch_confined   = true;
            m_patch_regions.clear();
            return 1;
        }
        if(changed)
            *changed = true;

        std::vector<element::ptr> restyle;
        std::unordered_set<const element*> background_only;
        std::unordered_map<const element*, const property_patch*> patched;
        std::unordered_set<const element*> shallow_width;
        bool allow_shallow_width = classes.empty() && !m_has_svg && m_tabular_elements.empty() &&
                                   !getenv("LH_NO_SHALLOW_WIDTH_PATCH");
        std::vector<element::ptr> new_text;
        std::vector<element::ptr> retext;
        std::vector<std::string>  words;
        bool                      keep_tree  = !getenv("LH_NO_KEEP_TREE");
        bool                      paint_only = !getenv("LH_NO_PAINT_ONLY");
        m_patch_regions.clear();
        m_patch_confined = !getenv("LH_NO_PATCH_REGIONS");
        for(const auto& entry : classes)
        {
            html_hole& hole   = m_holes[changes[entry.first].first];
            int        region = 0;
            auto       tag    = std::static_pointer_cast<html_tag>(hole.owner);
            const bool fast   = tag->flip_class(hole.value, changes[entry.first].second, m_match_epoch, &region);
            if(!fast)
            {
                if(!apply_retag(hole.owner, entry.second, true, &region))
                {
                    keep_tree  = false;
                    paint_only = false;
                    tag->forget_flips();
                } else
                {
                    tag->note_flip(hole.value, changes[entry.first].second, region);
                }
            }
            if(region == 1)
                m_restyle_regions.push_back(hole.owner);
            else if(region != 3 || outermost_svg(hole.owner) || !push_text_owner_region(hole.owner, m_restyle_regions))
                m_restyle_confined = false;
            if(m_patch_confined && region == 1)
                m_patch_regions.push_back(hole.owner);
            else if(!m_patch_confined || region != 3 || outermost_svg(hole.owner) || !push_text_owner_region(hole.owner, m_patch_regions))
                m_patch_confined = false;
            hole.value       = changes[entry.first].second;
            if(!fast)
                restyle.push_back(hole.owner);
        }
        static const bool                               resize_only = getenv("LH_NO_SAME_SIZE_TEXT") != nullptr;
        std::vector<std::pair<element::ptr, size>>      measured;
        for(size_t k : dirty)
        {
            const std::string& value   = changes[k].second;
            html_hole&         hole    = m_holes[changes[k].first];
            const bool         inplace = hole.text && retext_in_place(hole.text_elements, value.c_str(), words);
            const bool         background = background_changes[k];
            const property_patch* patch   = property_patches[k].names.empty() ? nullptr : &property_patches[k];
            if(hole.text ? !inplace : hole.src || !hole.attr.empty() || (!width_changes[k] && !patch && !background))
                allow_shallow_width = false;
            if(allow_shallow_width && width_changes[k] && shallow_width_owner(hole.owner))
                shallow_width.insert(hole.owner.get());
            if(!hole.text && !hole.src)
            {
                const int kind = hole.attr.empty() ? (background ? 1 : patch ? (patch->box ? 1 : 0) : style_change_region(hole.value, value)) : 2;
                if(kind == 1)
                    m_restyle_regions.push_back(hole.owner);
                else if(kind == 2 && outermost_svg(hole.owner))
                    m_restyle_regions.push_back(outermost_svg(hole.owner));
                else if(kind != 3 || outermost_svg(hole.owner) || !push_text_owner_region(hole.owner, m_restyle_regions))
                    m_restyle_confined = false;
            }
            if(m_patch_confined)
            {
                element::ptr region;
                bool         words_region = false;
                bool         continue_region = false;
                if(!hole.text && !hole.src)
                {
                    const int kind = hole.attr.empty() ? (background ? 1 : patch ? (patch->box ? 1 : 0) : style_change_region(hole.value, value)) : 2;
                    if(kind == 1)
                        region = hole.owner;
                    else if(kind == 2)
                        region = outermost_svg(hole.owner);
                    else if(kind == 3 && !outermost_svg(hole.owner) && push_text_owner_region(hole.owner, m_patch_regions))
                        continue_region = true;
                } else if(inplace && !text_regions_disabled())
                {
                    region       = outermost_svg(hole.text_elements.front());
                    words_region = !region;
                }
                if(continue_region)
                {
                } else if(words_region)
                {
                    for(const auto& el : hole.text_elements)
                    {
                        if(typeid(*el) == typeid(el_text))
                            m_patch_regions.push_back(el);
                    }
                } else if(region)
                    m_patch_regions.push_back(region);
                else
                    m_patch_confined = false;
            }
            if(paint_only)
            {
                if(hole.src || (hole.text && (!inplace || resize_only)))
                {
                    paint_only = false;
                } else if(!hole.text && !hole.attr.empty())
                {
                    bool inside_svg = false;
                    for(auto p = hole.owner->parent(); p; p = p->parent())
                    {
                        if(std::dynamic_pointer_cast<el_svg>(p))
                        {
                            inside_svg = true;
                            break;
                        }
                    }
                    paint_only = inside_svg;
                } else if(!hole.text)
                {
                    paint_only = background || patch || style_change_is_paint_only(hole.value, value);
                }
            }
            if(inplace)
            {
                hole.value = value;
                for(size_t w = 0; w < words.size(); w++)
                {
                    size before;
                    hole.text_elements[w]->get_content_size(before, 0_px);
                    measured.emplace_back(hole.text_elements[w], before);
                    std::static_pointer_cast<el_text>(hole.text_elements[w])->set_text(words[w]);
                    retext.push_back(hole.text_elements[w]);
                }
                continue;
            }
            if(keep_tree && (hole.src ||
                             (!hole.text && hole.attr.empty() && !background && !patch && !tree_neutral_change(hole.value, value, hole.owner->css().get_display() == display_inline,
                                                                         table_part(hole.owner->css().get_display())))))
                keep_tree = false;
            if(background)
            {
                auto tag = dynamic_cast<html_tag*>(hole.owner.get());
                if(tag && (typeid(*tag) == typeid(html_tag) || typeid(*tag) == typeid(el_div) || typeid(*tag) == typeid(el_td)) &&
                   tag->background_color_is_local())
                    background_only.insert(hole.owner.get());
            }
            hole.value      = value;
            if(hole.src)
            {
                string_map  attrs = {{"src", hole.value}};
                std::string tag   = hole.owner->get_tagName() ? hole.owner->get_tagName() : "img";
                const bool  keep  = m_container->sanitize_element(tag, false, attrs) && attrs.count("src");
                hole.owner->set_attr("src", keep ? attrs["src"].c_str() : "");
                if(auto image = std::dynamic_pointer_cast<el_image>(hole.owner))
                    image->update_src();
                restyle.push_back(hole.owner);
                continue;
            }
            if(!hole.attr.empty())
            {
                string_map  attrs = {{hole.attr, hole.value}};
                std::string tag   = hole.owner->get_tagName();
                const bool  keep  = m_container->sanitize_element(tag, true, attrs) && attrs.count(hole.attr);
                hole.owner->set_attr(hole.attr.c_str(), keep ? attrs[hole.attr].c_str() : "");
                restyle.push_back(hole.owner);
                continue;
            }
            if(!hole.text)
            {
                hole.owner->set_attr("style", hole.value.c_str());
                restyle.push_back(hole.owner);
                if(patch && !outermost_svg(hole.owner))
                    patched[hole.owner.get()] = patch;
                continue;
            }
            elements_list created;
            m_container->split_text(
                hole.value.c_str(),
                [this, &created](const char* text) { created.push_back(std::make_shared<el_text>(text, shared_from_this())); },
                [this, &created](const char* text) { created.push_back(std::make_shared<el_space>(text, shared_from_this())); });
            hole.text_elements.front()->parent()->replace_children(hole.text_elements, created);
            if(keep_tree && !replace_text_renderers(hole.text_elements, created))
                keep_tree = false;
            hole.text_elements.assign(created.begin(), created.end());
            new_text.insert(new_text.end(), created.begin(), created.end());
        }
        std::vector<std::pair<bool, bool>> groups;
        if(paint_only)
        {
            groups.reserve(restyle.size());
            for(const auto& el : restyle)
                groups.emplace_back(el->css().is_paint_group(), el->css().get_transform().empty());
        }
        for(const auto& entry : classes)
        {
            background_only.erase(m_holes[changes[entry.first].first].owner.get());
            patched.erase(m_holes[changes[entry.first].first].owner.get());
        }
        std::unordered_set<const element*> seen;
        for(const auto& el : restyle)
            if(!seen.insert(el.get()).second)
            {
                background_only.erase(el.get());
                patched.erase(el.get());
                shallow_width.erase(el.get());
            }
        for(const auto* list : {&restyle, &new_text, &retext})
            for(const auto& el : *list)
                for(auto parent = el->parent(); parent; parent = parent->parent())
                {
                    background_only.erase(parent.get());
                    patched.erase(parent.get());
                }
        if(!allow_shallow_width || !keep_tree || !new_text.empty())
            shallow_width.clear();
        restyle_after_patch(restyle, new_text, keep_tree, retext, background_only, patched, shallow_width);
        for(size_t k = 0; k < measured.size() && paint_only; k++)
        {
            size after;
            measured[k].first->get_content_size(after, 0_px);
            if(after.width != measured[k].second.width || after.height != measured[k].second.height)
                paint_only = false;
        }
        if(paint_only)
        {
            for(size_t k = 0; k < restyle.size() && paint_only; k++)
            {
                if(restyle[k]->css().is_paint_group() != groups[k].first || restyle[k]->css().get_transform().empty() != groups[k].second)
                    paint_only = false;
            }
        }
        m_paint_only_patch = paint_only && keep_tree;
        if(keep_tree && !m_paint_only_patch)
            forget_positions();
        if(!m_paint_only_patch)
        {
            m_patch_confined = false;
            m_patch_regions.clear();
        }
        return 1;
    }

    bool document::take_table_cells(int count)
    {
        if(m_table_cells + count > max_table_cells)
        {
            if(!m_table_limited)
            {
                m_table_limited = true;
                m_container->sanitizer_limit("table");
            }
            return false;
        }
        m_table_cells += count;
        return true;
    }

    void document::forget_positions()
    {
        if(!m_root_render)
            return;
        if(layout_cache_enabled())
        {
            for(render_item* item : m_layout_marked)
                item->pos() = position();
            m_layout_marked.clear();
            return;
        }
        std::vector<render_item*> pending{m_root_render.get()};
        while(!pending.empty())
        {
            render_item* item = pending.back();
            pending.pop_back();
            item->pos() = position();
            for(const auto& child : item->children())
                pending.push_back(child.get());
        }
    }

    bool document::retext_in_place(const std::vector<element::ptr>& current, const char* text,
                                   std::vector<std::string>& words) const
    {
        static const bool disabled = getenv("LH_NO_INPLACE_TEXT") != nullptr;
        if(disabled || current.empty() || !text)
            return false;
        const element::ptr parent = current.front()->parent();
        if(!parent)
            return false;
        std::vector<std::pair<bool, std::string>> parts;
        m_container->split_text(
            text, [&parts](const char* word) { parts.emplace_back(false, word); },
            [&parts](const char* space) { parts.emplace_back(true, space); });
        if(parts.size() != current.size())
            return false;
        words.clear();
        words.reserve(parts.size());
        for(size_t i = 0; i < parts.size(); i++)
        {
            const element& el    = *current[i];
            const bool     space = typeid(el) == typeid(el_space);
            if((!space && typeid(el) != typeid(el_text)) || space != parts[i].first || current[i]->parent() != parent)
                return false;
            if(space)
            {
                std::string before;
                el.get_text(before);
                if(before != parts[i].second)
                    return false;
            }
            words.push_back(std::move(parts[i].second));
        }
        return true;
    }

    bool document::replace_text_renderers(const std::vector<element::ptr>& current,
                                          const elements_list& replacement)
    {
        if(current.empty() || replacement.empty())
            return false;
        auto owner = current.front()->parent();
        if(!owner || owner->children().size() != replacement.size() ||
           !std::equal(owner->children().begin(), owner->children().end(), replacement.begin()))
            return false;
        std::shared_ptr<render_item> target;
        owner->run_on_renderers([&](const std::shared_ptr<render_item>& item) {
            if(!std::dynamic_pointer_cast<render_item_inline_context>(item) || item->children().size() != current.size())
                return true;
            auto root = item;
            while(auto parent = root->parent())
                root = parent;
            if(root != m_root_render)
                return true;
            auto old = current.begin();
            for(const auto& child : item->children())
            {
                if(child->src_el() != *old++)
                    return true;
            }
            target = item;
            return false;
        });
        if(!target)
            return false;
        for(const auto& child : target->children())
            m_layout_marked.erase(std::remove(m_layout_marked.begin(), m_layout_marked.end(), child.get()), m_layout_marked.end());
        target->children().clear();
        for(const auto& child : replacement)
            target->add_child(child->create_render_item(target)->init());
        target->mark_layout_dirty();
        m_render_tree_serial++;
        return true;
    }

    void document::restyle_after_patch(const std::vector<element::ptr>& restyle, const std::vector<element::ptr>& new_text,
                                       bool keep_tree, const std::vector<element::ptr>& retext,
                                       const std::unordered_set<const element*>& background_only,
                                       const std::unordered_map<const element*, const property_patch*>& patched,
                                       const std::unordered_set<const element*>& shallow_width)
    {
        invalidate_paint();
        m_share_cache.clear();
        std::unordered_set<const element*> pending;
        for(const auto& el : restyle)
            pending.insert(el.get());
        for(const auto& el : new_text)
            pending.insert(el.get());
        for(const auto& el : retext)
            pending.insert(el.get());
        auto covered = [&pending, &shallow_width](const element::ptr& el) {
            for(auto p = el->parent(); p; p = p->parent())
            {
                if(pending.count(p.get()) && !shallow_width.count(p.get()))
                    return true;
            }
            return false;
        };
        std::unordered_map<const element*, const property_patch*> fast;
        for(const auto& el : restyle)
        {
            auto it = patched.find(el.get());
            if(it != patched.end() && !covered(el))
                fast[el.get()] = it->second;
            else if(auto tag = std::dynamic_pointer_cast<html_tag>(el))
                tag->restore_base_style();
        }
        if(!keep_tree || !new_text.empty())
            m_layout_before_valid = false;
        const bool positioned_fresh = keep_tree && new_text.empty() && m_positioned_epoch == m_style_epoch &&
                                      m_positioned_tree == m_render_tree_serial;
        std::vector<std::pair<const element*, element_position>> positions;
        auto collect_positions = [&positions, &shallow_width](const element::ptr& root) {
            if(shallow_width.count(root.get()))
            {
                positions.emplace_back(root.get(), root->css().get_position());
                return;
            }
            std::vector<const element*> stack{root.get()};
            while(!stack.empty())
            {
                const element* el = stack.back();
                stack.pop_back();
                positions.emplace_back(el, el->css().get_position());
                for(const auto& child : el->children())
                    stack.push_back(child.get());
            }
        };
        std::vector<element::ptr> roots;
        for(const auto& list : {&restyle, &new_text, &retext})
        {
            for(const auto& el : *list)
            {
                if(!covered(el))
                    roots.push_back(el);
            }
        }
        if(positioned_fresh)
        {
            for(const auto& el : roots)
                collect_positions(el);
        }
        for(const auto& el : roots)
        {
            auto patch = fast.find(el.get());
            if(patch != fast.end())
            {
                auto tag = std::static_pointer_cast<html_tag>(el);
                if(!tag->patch_inline_properties(patch->second->names, patch->second->text))
                {
                    tag->restore_base_style();
                    tag->compute_styles(true);
                }
            } else if(background_only.count(el.get()))
                std::static_pointer_cast<html_tag>(el)->compute_background_style();
            else
                el->compute_styles(!shallow_width.count(el.get()));
        }
        if(positioned_fresh)
        {
            bool same = true;
            for(const auto& entry : positions)
            {
                if(entry.first->css().get_position() != entry.second)
                {
                    same = false;
                    break;
                }
            }
            if(same)
                m_positioned_epoch = m_style_epoch;
        }

        if(keep_tree && m_root_render)
            return;
        m_tabular_elements.clear();
        m_table_cells = 0;
        m_root_render = m_root->create_render_item(nullptr);
        m_render_tree_serial++;
        m_layout_marked.clear();
        m_relayout_roots.clear();
        m_layout_before_valid = false;
        fix_tables_layout();
        if(m_root_render)
            m_root_render = m_root_render->init();
    }

    bool document::update_from_string(const std::string& html, bool* changed)
    {
        invalidate_paint();
        m_paint_only_patch = false;
        m_patch_confined   = false;
        m_patch_regions.clear();
        if(changed)
            *changed = false;
        if(!m_gumbo || !m_root || !m_finalized)
            return false;
        if(m_template_ok && !getenv("LH_NO_TEMPLATE"))
        {
            const int result = update_from_template(html, changed);
            if(result == 1)
                return true;
            if(result == template_requires_fresh_document)
                return false;
        }
        return update_structure(html, changed);
    }

    int document::selector_scope()
    {
        if(m_selector_scope >= 0)
            return m_selector_scope;
        bool           attributes = false;
        bool           positions  = false;
        selector_names names{m_left_classes, m_left_ids, m_left_attrs};
        for(const css* sheet : {&m_master_css, &m_styles, &m_user_css})
        {
            for(const auto& sel : sheet->selectors())
            {
                if(!sel)
                    continue;
                scan_selector(*sel, attributes, positions);
                collect_ancestor_names(*sel, !(sel->m_style && sel->m_style->declares_custom_property()), names);
            }
        }
        bool counters = mentions_counters(m_master_styles);
        for(const auto& sheet : m_css)
            counters = counters || mentions_counters(sheet.text);
        m_selector_scope = (attributes ? 1 : 0) | (positions ? 2 : 0) | (counters ? 4 : 0);
        return m_selector_scope;
    }

    bool document::retag_allowed(const element::ptr& el, std::string name, bool svg, string_map& attrs, bool blocked)
    {
        auto tag = std::dynamic_pointer_cast<html_tag>(el);
        if(!tag || blocked || !restylable_in_place(*el))
            return false;
        if(name.empty() || lowcase(name) != el->get_tagName() || !m_container->sanitize_element(name, svg, attrs) ||
           lowcase(name) != el->get_tagName())
            return false;
        auto unsafe_style = [](std::string_view value) {
            return value.find("counter") != std::string_view::npos || value.find("var(") != std::string_view::npos ||
                   value.find("--") != std::string_view::npos;
        };
        const string_map& before    = tag->attributes();
        auto              was       = before.find("style");
        auto              now       = attrs.find("style");
        const std::string old_style = was == before.end() ? std::string() : was->second;
        const std::string new_style = now == attrs.end() ? std::string() : now->second;
        if(old_style != new_style && (unsafe_style(old_style) || unsafe_style(new_style)))
            return false;
        auto normalized = [this](const std::string* value) {
            std::string text = value ? *value : std::string();
            if(m_mode == quirks_mode)
                lcase(text);
            return text;
        };
        auto reaches_others = [&](const std::string& attr, const std::string* old, const std::string* next) {
            if(m_left_attrs.count(_id(attr)))
                return true;
            if(attr == "id")
                return m_left_ids.count(_id(normalized(old))) != 0 || m_left_ids.count(_id(normalized(next))) != 0;
            if(attr != "class")
                return false;
            std::unordered_set<string_id> a, c;
            for(const auto& cls : split_string(normalized(old), whitespace, "", ""))
                a.insert(_id(cls));
            for(const auto& cls : split_string(normalized(next), whitespace, "", ""))
                c.insert(_id(cls));
            for(string_id cls : a)
            {
                if(!c.count(cls) && m_left_classes.count(cls))
                    return true;
            }
            for(string_id cls : c)
            {
                if(!a.count(cls) && m_left_classes.count(cls))
                    return true;
            }
            return false;
        };
        for(const auto& entry : before)
        {
            auto it = attrs.find(entry.first);
            if((it == attrs.end() || it->second != entry.second) &&
               reaches_others(entry.first, &entry.second, it == attrs.end() ? nullptr : &it->second))
                return false;
        }
        for(const auto& entry : attrs)
        {
            if(!before.count(entry.first) && reaches_others(entry.first, nullptr, &entry.second))
                return false;
        }
        return true;
    }

    bool document::apply_retag(const element::ptr& el, const string_map& attrs, bool check, int* region)
    {
        if(region)
            *region = 0;
        auto has_pseudo = [&el]() {
            for(const auto& child : el->m_children)
                if(child->tag() == __tag_before_ || child->tag() == __tag_after_)
                    return true;
            return false;
        };
        auto used_rules = [&el]() {
            std::vector<std::pair<const css_selector*, bool>> rules;
            rules.reserve(el->m_used_styles.size());
            for(const auto& used : el->m_used_styles)
                rules.emplace_back(used->m_selector.get(), used->m_used);
            std::sort(rules.begin(), rules.end());
            return rules;
        };
        auto                                              tag     = std::static_pointer_cast<html_tag>(el);
        bool                                              neutral = check && !has_pseudo();
        std::vector<std::pair<const css_selector*, bool>> rules_before;
        string_map                                        attrs_before;
        if(neutral)
        {
            rules_before = used_rules();
            attrs_before = tag->attributes();
        }
        tag->reset_attributes(attrs);
        tag->forget_matched_styles();
        elements_list children;
        children.swap(tag->m_children);
        const css* sheets[3] = {&m_master_css, &m_styles, &m_user_css};
        tag->apply_matching(sheets, m_match_epoch);
        elements_list pseudo;
        pseudo.swap(tag->m_children);
        for(const auto& child : pseudo)
        {
            if(child->tag() == __tag_before_)
                children.push_front(child);
            else
                children.push_back(child);
        }
        tag->m_children.swap(children);
        if(!neutral || has_pseudo())
            return false;
        attrs_before.erase("class");
        string_map attrs_after = tag->attributes();
        attrs_after.erase("class");
        if(attrs_before != attrs_after)
            return false;
        const auto                                        rules_after = used_rules();
        std::vector<std::pair<const css_selector*, bool>> changed;
        std::set_symmetric_difference(rules_before.begin(), rules_before.end(), rules_after.begin(), rules_after.end(),
                                      std::back_inserter(changed));
        int kind = 1;
        for(const auto& rule : changed)
        {
            if(!rule.first->m_style || !rule.first->m_style->only_declares([&kind](string_id name) {
                   const std::string& text     = _s(name);
                   const std::string  property = text.compare(0, 8, "-lh-raw-") == 0 ? text.substr(8) : text;
                   const int          declared = declared_region(property);
                   kind                        = declared == 0 ? 0 : (kind == 0 ? 0 : std::max(kind, declared));
                   return paint_only_property(property);
               }))
                return false;
        }
        if(region)
            *region = kind;
        return true;
    }

    bool document::update_structure(const std::string& html, bool* changed)
    {
        m_match_epoch++;
        begin_layout_patch();
        invalidate_paint();
        note_style_change();
        if(!m_gumbo || !m_root || !m_finalized || m_build_limited || getenv("LH_NO_STRUCTURE"))
            return false;
        const int scope = selector_scope();
        if((scope & 4) || mentions_counters(html))
            return false;
        const bool attribute_scope = (scope & 1) != 0;
        const bool position_scope  = (scope & 2) != 0;

        std::string  text   = html;
        GumboOutput* output = gumbo_parse_with_options(&kGumboDefaultOptions, text.data(), text.size());
        if(!output)
            return false;
        auto fail = [&]() {
            gumbo_destroy_output(&kGumboDefaultOptions, output);
            return false;
        };
        if(output->document->v.document.doc_type_quirks_mode != m_gumbo->document->v.document.doc_type_quirks_mode)
            return fail();
        int total = 0, deepest = 0;
        count_elements(output->document, 0, total, deepest);
        if(total > max_build_elements || deepest >= max_build_depth)
            return fail();

        auto holes_of = [&](const GumboNode* node) -> const std::vector<size_t>* {
            if(!m_template_ok)
                return nullptr;
            auto it = m_node_holes.find(node);
            return it == m_node_holes.end() ? nullptr : &it->second;
        };
        auto hole_in = [&](const std::vector<size_t>* holes, const char* name) -> const html_hole* {
            if(!holes)
                return nullptr;
            for(size_t index : *holes)
            {
                const html_hole& hole = m_holes[index];
                if(name ? (!hole.text && hole.name == name) : hole.text)
                    return &hole;
            }
            return nullptr;
        };
        auto mapped_element = [&](const GumboNode* node) -> const element::ptr* {
            auto it = m_node_elements.find(node);
            return it == m_node_elements.end() || it->second.size() != 1 ? nullptr : &it->second.front();
        };
        auto current_text = [&](const GumboNode* node) -> std::vector<element::ptr> {
            if(const html_hole* hole = hole_in(holes_of(node), nullptr))
                return hole->text_elements;
            auto it = m_node_elements.find(node);
            return it == m_node_elements.end() ? std::vector<element::ptr>() : it->second;
        };
        auto same_text_node = [&](const GumboNode* a, const GumboNode* b) {
            if(const html_hole* hole = hole_in(holes_of(a), nullptr))
            {
                const GumboStringPiece& t = b->v.text.original_text;
                return t.data && std::string_view(t.data, t.length) == hole->value;
            }
            return same_text(a->v.text.text, b->v.text.text);
        };
        std::function<bool(const GumboNode*, const GumboNode*)> same_subtree = [&](const GumboNode* a, const GumboNode* b) {
            if(a->type != b->type)
                return false;
            if(is_element_node(a))
            {
                if(!same_element_kind(a, b))
                    return false;
                const GumboVector& x = a->v.element.attributes;
                const GumboVector& y = b->v.element.attributes;
                if(x.length != y.length)
                    return false;
                for(unsigned int i = 0; i < x.length; i++)
                {
                    auto* p = static_cast<const GumboAttribute*>(x.data[i]);
                    auto* q = static_cast<const GumboAttribute*>(y.data[i]);
                    if(!same_text(p->name, q->name) || !same_text(p->value, q->value))
                        return false;
                }
            } else if(a->type != GUMBO_NODE_DOCUMENT)
            {
                return same_text(a->v.text.text, b->v.text.text);
            }
            const GumboVector* ca = node_children(a);
            const GumboVector* cb = node_children(b);
            if(ca->length != cb->length)
                return false;
            for(unsigned int i = 0; i < ca->length; i++)
            {
                if(!same_subtree(static_cast<const GumboNode*>(ca->data[i]), static_cast<const GumboNode*>(cb->data[i])))
                    return false;
            }
            return true;
        };

        struct rebuild
        {
            element::ptr                           parent;
            const GumboNode*                       node;
            std::vector<std::vector<element::ptr>> keep;
            std::vector<char>                      create;
        };
        std::vector<std::pair<element::ptr, std::string>>                    styles;
        std::vector<std::pair<element::ptr, string_map>>                     retags;
        std::vector<rebuild>                                                 rebuilds;
        std::vector<std::pair<const GumboNode*, element::ptr>>               kept;
        std::vector<std::pair<const GumboNode*, std::vector<element::ptr>>> kept_text;
        std::vector<std::pair<std::vector<element::ptr>, std::vector<std::string>>> retexts;
        kept.reserve(m_node_elements.size());
        enum
        {
            diff_kept,
            diff_replace,
            diff_failed
        };
        auto unsafe_style = [](std::string_view value) {
            return value.find("counter") != std::string_view::npos || value.find("var(") != std::string_view::npos ||
                   value.find("--") != std::string_view::npos;
        };
        auto retag = [&](const GumboNode* b, const element::ptr& el, bool in_select) {
            string_map attrs;
            for(unsigned int i = 0; i < b->v.element.attributes.length; i++)
            {
                auto* attr        = static_cast<const GumboAttribute*>(b->v.element.attributes.data[i]);
                attrs[attr->name] = attr->value;
            }
            if(!retag_allowed(el, std::string(element_name(b)), b->v.element.tag_namespace == GUMBO_NAMESPACE_SVG, attrs,
                              in_select || attribute_scope))
                return false;
            retags.emplace_back(el, std::move(attrs));
            return true;
        };
        std::function<bool(const GumboNode*, const GumboNode*, const element::ptr&, bool)> diff_children;
        auto diff_element = [&](const GumboNode* a, const GumboNode* b, const element::ptr& el, bool in_select) {
            const GumboVector&           x       = a->v.element.attributes;
            const GumboVector&           y       = b->v.element.attributes;
            const std::vector<size_t>*   holes   = holes_of(a);
            const GumboAttribute*        style   = nullptr;
            bool                         renamed = x.length != y.length;
            for(unsigned int i = 0; i < x.length && !renamed; i++)
            {
                auto* p = static_cast<const GumboAttribute*>(x.data[i]);
                auto* q = static_cast<const GumboAttribute*>(y.data[i]);
                if(!same_text(p->name, q->name))
                {
                    renamed = true;
                    break;
                }
                const html_hole* hole   = hole_in(holes, p->name);
                std::string_view before = hole ? std::string_view(hole->value) : std::string_view(p->value);
                bool             same   = false;
                if(hole)
                {
                    const GumboStringPiece& v = q->original_value;
                    same = v.data && v.length >= 2 && v.data[0] == hole->close && v.data[v.length - 1] == hole->close &&
                           std::string_view(v.data + 1, v.length - 2) == before;
                } else
                {
                    same = before == std::string_view(q->value);
                }
                if(same)
                    continue;
                if(strcmp(p->name, "style") != 0)
                {
                    renamed = true;
                    break;
                }
                if(unsafe_style(before) || unsafe_style(q->value))
                    return diff_replace;
                if(style_attributes_selectable() ||
                   (has_generated_children(el) && style_change_is_width_only(std::string(before), q->value)))
                    return diff_failed;
                style = q;
            }
            if(raw_text_element(a))
            {
                if(renamed || style || !same_subtree(a, b))
                    return diff_replace;
                kept.emplace_back(b, el);
                return diff_kept;
            }
            if(renamed)
            {
                style = nullptr;
                if(!retag(b, el, in_select))
                    return diff_replace;
            }
            if(!diff_children(a, b, el, in_select || a->v.element.tag == GUMBO_TAG_SELECT))
                return diff_failed;
            if(style)
                styles.emplace_back(el, style->value);
            kept.emplace_back(b, el);
            return diff_kept;
        };
        diff_children = [&](const GumboNode* a, const GumboNode* b, const element::ptr& parent, bool in_select) {
            const GumboVector* ca = node_children(a);
            const GumboVector* cb = node_children(b);
            bool               aligned = ca->length == cb->length;
            for(unsigned int i = 0; aligned && i < ca->length; i++)
            {
                auto* x = static_cast<const GumboNode*>(ca->data[i]);
                auto* y = static_cast<const GumboNode*>(cb->data[i]);
                if(x->type != y->type || (is_element_node(x) && (!same_element_kind(x, y) || node_key(x) != node_key(y))))
                    aligned = false;
            }
            const size_t styles_mark    = styles.size();
            const size_t retags_mark    = retags.size();
            const size_t rebuilds_mark  = rebuilds.size();
            const size_t kept_mark      = kept.size();
            const size_t kept_text_mark = kept_text.size();
            const size_t retexts_mark   = retexts.size();
            rebuild      plan;
            if(aligned)
            {
                bool replaced = false;
                for(unsigned int i = 0; i < cb->length; i++)
                {
                    auto* x      = static_cast<const GumboNode*>(ca->data[i]);
                    auto* y      = static_cast<const GumboNode*>(cb->data[i]);
                    bool  create = false;
                    if(is_element_node(x))
                    {
                        const element::ptr* el = mapped_element(x);
                        if(!el)
                        {
                            create = !same_subtree(x, y);
                            replaced = replaced || create;
                        } else
                        {
                            const int result = diff_element(x, y, *el, in_select);
                            if(result == diff_failed)
                                return false;
                            create   = result == diff_replace;
                            replaced = replaced || create;
                        }
                    } else if(x->type == GUMBO_NODE_TEXT)
                    {
                        create = !same_text_node(x, y);
                        std::vector<std::string> words;
                        if(create && !in_select && retext_in_place(current_text(x), y->v.text.text, words))
                        {
                            create = false;
                            retexts.emplace_back(current_text(x), std::move(words));
                        }
                        if(!create)
                            kept_text.emplace_back(y, current_text(x));
                    } else
                    {
                        create = !same_text(x->v.text.text, y->v.text.text);
                    }
                    if(create && plan.create.empty())
                        plan.create.assign(cb->length, 0);
                    if(create)
                        plan.create[i] = 1;
                }
                if(plan.create.empty())
                    return true;
                plan.keep.resize(cb->length);
                const bool everything = replaced && attribute_scope;
                if(everything)
                {
                    styles.resize(styles_mark);
                    retags.resize(retags_mark);
                    rebuilds.resize(rebuilds_mark);
                    kept.resize(kept_mark);
                    kept_text.resize(kept_text_mark);
                    retexts.resize(retexts_mark);
                }
                for(unsigned int i = 0; i < cb->length; i++)
                {
                    auto* x = static_cast<const GumboNode*>(ca->data[i]);
                    if(everything || (x->type != GUMBO_NODE_TEXT && !is_element_node(x)))
                        plan.create[i] = 1;
                    if(plan.create[i])
                        continue;
                    if(x->type == GUMBO_NODE_TEXT)
                        plan.keep[i] = current_text(x);
                    else if(const element::ptr* el = mapped_element(x))
                        plan.keep[i] = {*el};
                }
            } else
            {
                plan.keep.resize(cb->length);
                plan.create.assign(cb->length, 1);
                std::unordered_map<std::string_view, const GumboNode*> keyed;
                std::unordered_set<std::string_view>                   duplicates;
                std::unordered_set<std::string_view>                   seen;
                if(!position_scope)
                {
                    for(unsigned int i = 0; i < ca->length; i++)
                    {
                        std::string_view key = node_key(static_cast<const GumboNode*>(ca->data[i]));
                        if(!key.empty() && !keyed.emplace(key, static_cast<const GumboNode*>(ca->data[i])).second)
                            duplicates.insert(key);
                    }
                    for(unsigned int i = 0; i < cb->length; i++)
                    {
                        std::string_view key = node_key(static_cast<const GumboNode*>(cb->data[i]));
                        if(!key.empty() && !seen.insert(key).second)
                            duplicates.insert(key);
                    }
                }
                for(unsigned int i = 0; i < cb->length && !position_scope; i++)
                {
                    auto*            y   = static_cast<const GumboNode*>(cb->data[i]);
                    std::string_view key = node_key(y);
                    if(key.empty() || duplicates.count(key))
                        continue;
                    auto match = keyed.find(key);
                    if(match == keyed.end() || !same_element_kind(match->second, y))
                        continue;
                    const element::ptr* el = mapped_element(match->second);
                    if(!el)
                        continue;
                    const int result = diff_element(match->second, y, *el, in_select);
                    if(result == diff_failed)
                        return false;
                    if(result == diff_kept)
                    {
                        plan.create[i] = 0;
                        plan.keep[i]   = {*el};
                    }
                }
            }
            if(!parent || in_select || (is_element_node(a) && (a->v.element.tag == GUMBO_TAG_HTML || a->v.element.tag == GUMBO_TAG_HEAD)))
                return false;
            std::unordered_set<const element*> reused;
            for(unsigned int i = 0; i < cb->length; i++)
            {
                if(plan.create[i] && has_document_level_element(static_cast<const GumboNode*>(cb->data[i])))
                    return false;
                for(const auto& el : plan.keep[i])
                    reused.insert(el.get());
            }
            for(unsigned int i = 0; i < ca->length; i++)
            {
                auto* x = static_cast<const GumboNode*>(ca->data[i]);
                if(!is_element_node(x))
                    continue;
                const element::ptr* el = mapped_element(x);
                if((!el || !reused.count(el->get())) && has_document_level_element(x))
                    return false;
            }
            plan.parent = parent;
            plan.node   = b;
            rebuilds.push_back(std::move(plan));
            return true;
        };
        if(!diff_children(m_gumbo->document, output->document, nullptr, false))
            return fail();

        auto chain = [](element::ptr el) {
            std::vector<element::ptr> out;
            for(; el; el = el->parent())
                out.push_back(el);
            return out;
        };
        auto nearest_attached = [this](const std::vector<element::ptr>& path) -> element::ptr {
            for(const auto& candidate : path)
            {
                element::ptr el = candidate;
                while(el && el != m_root)
                    el = el->parent();
                if(el == m_root)
                    return candidate;
            }
            return nullptr;
        };
        const std::vector<element::ptr> over_chain   = chain(m_over_element);
        const std::vector<element::ptr> active_chain = chain(m_active_element);

        std::unordered_map<const void*, std::vector<element::ptr>> remapped;
        remapped.reserve(kept.size() + kept_text.size());
        for(auto& entry : kept)
            remapped[entry.first] = {std::move(entry.second)};
        for(auto& entry : kept_text)
            remapped[entry.first] = std::move(entry.second);
        m_node_elements.swap(remapped);

        std::vector<element::ptr> restyle;
        for(const auto& patch : styles)
        {
            patch.first->set_attr("style", patch.second.c_str());
            restyle.push_back(patch.first);
        }
        std::vector<element::ptr> retext;
        for(auto& entry : retexts)
        {
            for(size_t w = 0; w < entry.second.size(); w++)
            {
                std::static_pointer_cast<el_text>(entry.first[w])->set_text(entry.second[w]);
                retext.push_back(entry.first[w]);
            }
        }
        static const bool no_keep_tree = getenv("LH_NO_KEEP_TREE") != nullptr;
        bool              keep_tree    = !no_keep_tree && rebuilds.empty() && styles.empty();
        std::vector<element::ptr> fresh;
        std::vector<element::ptr> created;
        m_track_nodes    = true;
        m_build_elements = 0;
        for(auto& plan : rebuilds)
        {
            const element::ptr& parent = plan.parent;
            int                 depth  = 0;
            for(auto p = parent->parent(); p; p = p->parent())
                depth++;
            m_build_depth = depth + 1;
            element::ptr before, after;
            if(!parent->m_children.empty() && parent->m_children.front()->tag() == __tag_before_)
                before = parent->m_children.front();
            if(!parent->m_children.empty() && parent->m_children.back()->tag() == __tag_after_)
                after = parent->m_children.back();
            std::unordered_set<const element*> reused;
            elements_list                      children;
            if(before)
                children.push_back(before);
            const GumboVector* nodes = node_children(plan.node);
            for(unsigned int i = 0; i < nodes->length; i++)
            {
                if(!plan.create[i])
                {
                    for(const auto& el : plan.keep[i])
                    {
                        reused.insert(el.get());
                        children.push_back(el);
                    }
                    continue;
                }
                elements_list made;
                create_node(static_cast<GumboNode*>(nodes->data[i]), made, true, true);
                for(const auto& el : made)
                {
                    children.push_back(el);
                    if(std::dynamic_pointer_cast<html_tag>(el))
                        fresh.push_back(el);
                    created.push_back(el);
                }
            }
            if(after)
                children.push_back(after);
            for(const auto& old : parent->m_children)
            {
                if(old != before && old != after && !reused.count(old.get()))
                    old->parent(nullptr);
            }
            for(const auto& el : children)
                el->parent(parent);
            parent->m_children.swap(children);
        }
        m_track_nodes = false;
        m_build_depth = 0;

        for(auto& entry : retags)
        {
            keep_tree = apply_retag(entry.first, entry.second, keep_tree) && keep_tree;
            restyle.push_back(entry.first);
        }

        const bool any = !styles.empty() || !rebuilds.empty() || !retags.empty() || !retexts.empty();
        if(any)
        {
            for(const auto& el : fresh)
                el->apply_stylesheet(m_master_css);
            for(const auto& el : fresh)
                el->parse_attributes();
            for(const auto& el : fresh)
                el->apply_stylesheet(m_styles);
            for(const auto& el : fresh)
                el->apply_stylesheet(m_user_css);
            restyle_after_patch(restyle, created, keep_tree, retext);
            if(keep_tree)
                forget_positions();
            m_over_element   = nearest_attached(over_chain);
            m_active_element = nearest_attached(active_chain);
        }

        gumbo_destroy_output(&kGumboDefaultOptions, m_gumbo);
        m_gumbo = output;
        m_text.swap(text);
        build_template();
        if(changed)
            *changed = any;
        return true;
    }

    bool document::style_attributes_selectable()
    {
        if(m_style_attributes_selectable >= 0)
            return m_style_attributes_selectable != 0;
        std::function<bool(const css_selector&)> reads_style = [&](const css_selector& selector) {
            for(const css_selector* sel = &selector; sel; sel = sel->m_left.get())
            {
                for(const auto& attr : sel->m_right.m_attrs)
                {
                    if(attr.type == select_attr && t_strcasecmp(_s(attr.name).c_str(), "style") == 0)
                        return true;
                    for(const auto& inner : attr.selector_list)
                    {
                        if(inner && reads_style(*inner))
                            return true;
                    }
                }
            }
            return false;
        };
        for(const css* sheet : {&m_master_css, &m_styles, &m_user_css})
        {
            for(const auto& selector : sheet->selectors())
            {
                if(selector && reads_style(*selector))
                {
                    m_style_attributes_selectable = 1;
                    return true;
                }
            }
        }
        m_style_attributes_selectable = 0;
        return false;
    }

    bool document::ids_selectable()
    {
        if(m_ids_selectable >= 0)
            return m_ids_selectable != 0;
        static const bool disabled = getenv("LH_NO_ID_SHARE") != nullptr;
        std::function<bool(const css_selector&)> sees_ids = [&](const css_selector& selector) {
            for(const css_selector* sel = &selector; sel; sel = sel->m_left.get())
            {
                for(const auto& attr : sel->m_right.m_attrs)
                {
                    if(attr.type == select_id || (attr.type == select_attr && t_strcasecmp(_s(attr.name).c_str(), "id") == 0))
                        return true;
                    for(const auto& inner : attr.selector_list)
                    {
                        if(inner && sees_ids(*inner))
                            return true;
                    }
                }
            }
            return false;
        };
        bool seen = disabled;
        for(const css* sheet : {&m_master_css, &m_styles, &m_user_css})
        {
            for(const auto& sel : sheet->selectors())
            {
                if(seen)
                    break;
                seen = sel && sees_ids(*sel);
            }
        }
        m_ids_selectable = seen ? 1 : 0;
        return seen;
    }

    bool document::style_sharing_enabled() const
    {
        static const bool enabled = getenv("LH_NO_STYLE_SHARING") == nullptr;
        return enabled;
    }

    void document::set_document_mode(document_mode mode)
    {
        m_mode = mode;
    }

    void document::finalize_from_external_root(const std::shared_ptr<element>& root, const std::string& base_master_styles,
                                               const std::string& user_styles)
    {
        invalidate_paint();
        // Finalization accumulates state that is never rolled back: the parsed stylesheets, the media
        // lists, the tabular elements, the render tree, and the used selectors of every element. A
        // second run would duplicate all of it, so a document is finalized once, exactly like a parsed
        // one. Build a new document to rebuild.
        if(m_finalized)
        {
            return;
        }
        m_finalized = true;

        m_root = root;

        const std::string master_styles =
            m_mode == quirks_mode && base_master_styles != ""
                ? base_master_styles + "\ntable { font-size: medium; font-weight: normal; font-style: normal; }\n"
                : base_master_styles;
        if(master_styles != "")
        {
            static std::mutex&                           master_cache_mutex = *new std::mutex;
            static std::unordered_map<std::string, css>& master_cache = *new std::unordered_map<std::string, css>;
            const bool                                  cacheable =
                master_styles.find("@media") == std::string::npos && master_styles.find("@import") == std::string::npos;
            bool hit = false;
            if(cacheable)
            {
                std::lock_guard<std::mutex> lock(master_cache_mutex);
                auto                        it = master_cache.find(master_styles);
                if(it != master_cache.end())
                {
                    m_master_css = it->second;
                    hit          = true;
                }
            }
            if(!hit)
            {
                container()->trusted_styles(true);
                m_master_css.parse_css_stylesheet(master_styles, "", shared_from_this());
                container()->trusted_styles(false);
                m_master_css.sort_selectors();
                if(cacheable)
                {
                    std::lock_guard<std::mutex> lock(master_cache_mutex);
                    if(master_cache.size() > 64)
                        master_cache.clear();
                    master_cache.emplace(master_styles, m_master_css);
                }
            }
        }
        if(user_styles != "")
        {
            container()->trusted_styles(true);
            m_user_css.parse_css_stylesheet(user_styles, "", shared_from_this());
            container()->trusted_styles(false);
            m_user_css.sort_selectors();
        }

        // Let's process created elements tree
        if(m_root)
        {
            container()->get_media_features(m_media);

            m_root->set_pseudo_class(_root_, true);

            // apply master CSS
            m_root->apply_stylesheet(m_master_css);

            // parse elements attributes
            m_root->parse_attributes();

            // Parsed and sorted document stylesheets are immutable, so documents built from the same
            // <style> text share one parse. Media and imports register per-document state while parsing,
            // so sheets that use them are parsed every time.
            static std::mutex&                           styles_cache_mutex = *new std::mutex;
            static std::unordered_map<std::string, css>& styles_cache = *new std::unordered_map<std::string, css>;
            std::string                                 styles_key;
            bool                                        styles_cacheable = !m_css.empty();
            for(const auto& css : m_css)
            {
                // Viewport lengths are folded to px while parsing, so such a sheet holds for one viewport.
                if(css.media != "" || css.text.find("@media") != std::string::npos || css.text.find("@import") != std::string::npos || has_viewport_units(css.text))
                {
                    styles_cacheable = false;
                    break;
                }
            }
            bool styles_hit = false;
            if(styles_cacheable)
            {
                styles_key.push_back(static_cast<char>('0' + static_cast<int>(m_mode)));
                for(const auto& css : m_css)
                {
                    styles_key.append(css.baseurl);
                    styles_key.push_back('\x1e');
                    styles_key.append(css.text);
                    styles_key.push_back('\x1f');
                }
                std::lock_guard<std::mutex> lock(styles_cache_mutex);
                auto                        it = styles_cache.find(styles_key);
                if(it != styles_cache.end())
                {
                    m_styles   = it->second;
                    styles_hit = true;
                }
            }
            if(!styles_hit)
            {
                // parse style sheets linked in document
                for(const auto& css : m_css)
                {
                    media_query_list_list::ptr media;
                    if(css.media != "")
                    {
                        auto mq_list = parse_media_query_list(css.media, shared_from_this());
                        media        = std::make_shared<media_query_list_list>();
                        media->add(mq_list);
                    }
                    m_styles.parse_css_stylesheet(css.text, css.baseurl, shared_from_this(), media);
                }
                // Sort css selectors using CSS rules.
                m_styles.sort_selectors();
                if(styles_cacheable)
                {
                    std::lock_guard<std::mutex> lock(styles_cache_mutex);
                    if(styles_cache.size() > 128)
                        styles_cache.clear();
                    styles_cache.emplace(std::move(styles_key), m_styles);
                }
            }

            // Apply media features.
            update_media_lists(m_media);

            // Apply parsed styles.
            m_root->apply_stylesheet(m_styles);

            // Apply user styles if any
            m_root->apply_stylesheet(m_user_css);

            // Initialize element::m_css
            m_root->compute_styles();

            // Create rendering tree
            m_table_cells = 0;
            m_root_render = m_root->create_render_item(nullptr);
            m_render_tree_serial++;
            m_layout_marked.clear();
            m_relayout_roots.clear();
            m_layout_before_valid = false;

            // Now the m_tabular_elements is filled with tabular elements.
            // We have to check the tabular elements for missing table elements
            // and create the anonymous boxes in visual table layout
            fix_tables_layout();

            // Finally initialize elements
            // init() returns pointer to the render_init element because it can change its type
            if(m_root_render)
            {
                m_root_render = m_root_render->init();
            }
        }
    }

    // https://html.spec.whatwg.org/multipage/parsing.html#change-the-encoding
    encoding adjust_meta_encoding(encoding meta_encoding, encoding current_encoding)
    {
        // 1.
        if(current_encoding == encoding::utf_16le || current_encoding == encoding::utf_16be)
        {
            return current_encoding;
        }

        // 2.
        if(meta_encoding == encoding::utf_16le || meta_encoding == encoding::utf_16be)
        {
            return encoding::utf_8;
        }

        // 3.
        if(meta_encoding == encoding::x_user_defined)
        {
            return encoding::windows_1252;
        }

        // 4,5,6.
        return meta_encoding;
    }

    // https://html.spec.whatwg.org/multipage/parsing.html#parsing-main-inhead:change-the-encoding
    encoding get_meta_encoding(GumboNode* root)
    {
        // find <head>
        GumboNode* head = nullptr;
        for(size_t i = 0; i < root->v.element.children.length; i++)
        {
            auto* node = static_cast<GumboNode*>(root->v.element.children.data[i]);
            if(node->type == GUMBO_NODE_ELEMENT && node->v.element.tag == GUMBO_TAG_HEAD)
            {
                head = node;
                break;
            }
        }
        if(!head)
        {
            return encoding::null;
        }

        // go through <meta> tags in <head>
        for(size_t i = 0; i < head->v.element.children.length; i++)
        {
            auto* node = static_cast<GumboNode*>(head->v.element.children.data[i]);
            if(node->type != GUMBO_NODE_ELEMENT || node->v.element.tag != GUMBO_TAG_META)
            {
                continue;
            }

            auto* charset    = gumbo_get_attribute(&node->v.element.attributes, "charset");
            auto* http_equiv = gumbo_get_attribute(&node->v.element.attributes, "http-equiv");
            auto* content    = gumbo_get_attribute(&node->v.element.attributes, "content");
            // 1. If the element has a charset attribute...
            if(charset)
            {
                auto encoding = get_encoding(charset->value);
                if(encoding != encoding::null)
                {
                    return encoding;
                }
            }
            // 2. Otherwise, if the element has an http-equiv attribute...
            else if(http_equiv && t_strcasecmp(http_equiv->value, "content-type") == 0 && content)
            {
                auto encoding = extract_encoding_from_meta_element(content->value);
                if(encoding != encoding::null)
                {
                    return encoding;
                }
            }
        }

        return encoding::null;
    }

    // substitute for gumbo_parse that handles encodings
    GumboOutput* document::parse_html(estring str)
    {
        // https://html.spec.whatwg.org/multipage/parsing.html#the-input-byte-stream
        encoding_sniffing_algorithm(str);
        // cannot store output in local variable because gumbo keeps pointers into it,
        // which will be accessed later in gumbo_tag_from_original_text
        if(str.encoding == encoding::utf_8)
        {
            m_text = str;
        } else
        {
            decode(str, str.encoding, m_text);
        }

        // Gumbo does not support callbacks on node creation, so we cannot change encoding while parsing.
        // Instead, we parse entire file and then handle <meta> tags.

        // Using gumbo_parse_with_options to pass string length (m_text may contain NUL chars).
        GumboOutput* output = gumbo_parse_with_options(&kGumboDefaultOptions, m_text.data(), m_text.size());

        if(str.confidence == confidence::certain)
        {
            return output;
        }

        // Otherwise: confidence is tentative.

        // If valid HTML encoding is specified in <meta> tag...
        encoding meta_encoding = get_meta_encoding(output->root);
        if(meta_encoding != encoding::null)
        {
            // ...and it is different from currently used encoding...
            encoding new_encoding = adjust_meta_encoding(meta_encoding, str.encoding);
            if(new_encoding != str.encoding)
            {
                // ...reparse with the new encoding.
                gumbo_destroy_output(&kGumboDefaultOptions, output);
                m_text.clear();

                if(new_encoding == encoding::utf_8)
                {
                    m_text = str;
                } else
                {
                    decode(str, new_encoding, m_text);
                }
                output = gumbo_parse_with_options(&kGumboDefaultOptions, m_text.data(), m_text.size());
            }
        }

        return output;
    }

    void document::create_node(void* gnode, elements_list& elements, bool parseTextNode, bool process_root)
    {
        auto* node = static_cast<GumboNode*>(gnode);
        switch(node->type)
        {
        case GUMBO_NODE_ELEMENT:
            {
                if(process_root)
                {
                    string_map      attrs;
                    GumboAttribute* attr = nullptr;
                    for(unsigned int i = 0; i < node->v.element.attributes.length; i++)
                    {
                        attr              = static_cast<GumboAttribute*>(node->v.element.attributes.data[i]);
                        attrs[attr->name] = attr->value;
                    }

                    element::ptr ret;
                    const char*  tag = gumbo_normalized_tagname(node->v.element.tag);
                    std::string  name;
                    if(tag[0])
                    {
                        name = tag;
                    } else if(node->v.element.original_tag.data && node->v.element.original_tag.length)
                    {
                        gumbo_tag_from_original_text(&node->v.element.original_tag);
                        name.assign(node->v.element.original_tag.data, node->v.element.original_tag.length);
                    }
                    if(!name.empty())
                    {
                        if(m_build_depth >= max_build_depth || m_build_elements >= max_build_elements)
                        {
                            m_container->sanitizer_limit(m_build_depth >= max_build_depth ? "depth" : "elements");
                            m_build_limited = true;
                            break;
                        }
                        if(!m_container->sanitize_element(name, node->v.element.tag_namespace == GUMBO_NAMESPACE_SVG, attrs))
                        {
                            break;
                        }
                        ret = create_element(name.c_str(), attrs);
                        m_build_elements++;
                    }
                    if(!strcmp(tag, "script"))
                    {
                        parseTextNode = false;
                    }
                    if(ret)
                    {
                        if(m_track_nodes)
                            m_node_elements[node] = {ret};
                        elements_list child;
                        m_build_depth++;
                        for(unsigned int i = 0; i < node->v.element.children.length; i++)
                        {
                            child.clear();
                            create_node(static_cast<GumboNode*>(node->v.element.children.data[i]), child, parseTextNode,
                                        true);
                            std::for_each(child.begin(), child.end(),
                                          [&ret](element::ptr& el) { ret->appendChild(el); });
                        }
                        m_build_depth--;
                        elements.push_back(ret);
                    }
                } else
                {
                    for(unsigned int i = 0; i < node->v.element.children.length; i++)
                    {
                        create_node(static_cast<GumboNode*>(node->v.element.children.data[i]), elements, parseTextNode,
                                    true);
                    }
                }
            }
            break;
        case GUMBO_NODE_TEXT:
            {
                const size_t before = elements.size();
                if(!parseTextNode)
                {
                    elements.push_back(std::make_shared<el_text>(node->v.text.text, shared_from_this()));
                } else
                {
                    m_container->split_text(
                        node->v.text.text,
                        [this, &elements](const char* text) {
                            elements.push_back(std::make_shared<el_text>(text, shared_from_this()));
                        },
                        [this, &elements](const char* text) {
                            elements.push_back(std::make_shared<el_space>(text, shared_from_this()));
                        });
                }
                if(m_track_nodes)
                {
                    auto it = elements.begin();
                    std::advance(it, before);
                    m_node_elements[node] = std::vector<element::ptr>(it, elements.end());
                }
            }
            break;
        case GUMBO_NODE_CDATA:
            {
                element::ptr ret = std::make_shared<el_cdata>(shared_from_this());
                ret->set_data(node->v.text.text);
                elements.push_back(ret);
            }
            break;
        case GUMBO_NODE_COMMENT:
            {
                element::ptr ret = std::make_shared<el_comment>(shared_from_this());
                ret->set_data(node->v.text.text);
                elements.push_back(ret);
            }
            break;
        case GUMBO_NODE_WHITESPACE:
            {
                std::string str = node->v.text.text;
                for(size_t i = 0; i < str.length(); i++)
                {
                    elements.push_back(std::make_shared<el_space>(str.substr(i, 1).c_str(), shared_from_this()));
                }
            }
            break;
        default:
            break;
        }
    }

    element::ptr document::create_element(const char* tag_name, const string_map& attributes)
    {
        element::ptr  newTag;
        document::ptr this_doc = shared_from_this();
        if(m_container)
        {
            newTag = m_container->create_element(tag_name, attributes, this_doc);
        }
        if(!newTag)
        {
            if(!strcmp(tag_name, "br"))
            {
                newTag = std::make_shared<el_break>(this_doc);
            } else if(!strcmp(tag_name, "p"))
            {
                newTag = std::make_shared<el_para>(this_doc);
            } else if(!strcmp(tag_name, "img") || !strcmp(tag_name, "canvas"))
            {
                newTag = std::make_shared<el_image>(this_doc);
            } else if(!strcmp(tag_name, "table"))
            {
                newTag = std::make_shared<el_table>(this_doc);
            } else if(!strcmp(tag_name, "td") || !strcmp(tag_name, "th"))
            {
                newTag = std::make_shared<el_td>(this_doc);
            } else if(!strcmp(tag_name, "link"))
            {
                newTag = std::make_shared<el_link>(this_doc);
            } else if(!strcmp(tag_name, "title"))
            {
                newTag = std::make_shared<el_title>(this_doc);
            } else if(!strcmp(tag_name, "a"))
            {
                newTag = std::make_shared<el_anchor>(this_doc);
            } else if(!strcmp(tag_name, "tr"))
            {
                newTag = std::make_shared<el_tr>(this_doc);
            } else if(!strcmp(tag_name, "style"))
            {
                newTag = std::make_shared<el_style>(this_doc);
            } else if(!strcmp(tag_name, "base"))
            {
                newTag = std::make_shared<el_base>(this_doc);
            } else if(!strcmp(tag_name, "body"))
            {
                newTag = std::make_shared<el_body>(this_doc);
            } else if(!strcmp(tag_name, "div"))
            {
                newTag = std::make_shared<el_div>(this_doc);
            } else if(!strcmp(tag_name, "script"))
            {
                newTag = std::make_shared<el_script>(this_doc);
            } else if(!strcmp(tag_name, "font"))
            {
                newTag = std::make_shared<el_font>(this_doc);
            } else if(!strcmp(tag_name, "svg"))
            {
                newTag = std::make_shared<el_svg>(this_doc);
            } else if(!strcmp(tag_name, "input"))
            {
                newTag = std::make_shared<el_input>(this_doc);
            } else if(!strcmp(tag_name, "select"))
            {
                newTag = std::make_shared<el_select>(this_doc);
            } else if(!strcmp(tag_name, "button"))
            {
                newTag = std::make_shared<el_button>(this_doc);
            } else if(!strcmp(tag_name, "textarea"))
            {
                newTag = std::make_shared<el_textarea>(this_doc);
            } else if(!strcmp(tag_name, "fieldset"))
            {
                newTag = std::make_shared<el_fieldset>(this_doc);
            } else if(!strcmp(tag_name, "progress") || !strcmp(tag_name, "meter"))
            {
                newTag = std::make_shared<el_meter>(this_doc);
            } else
            {
                newTag = std::make_shared<html_tag>(this_doc);
            }
        }

        if(newTag)
        {
            newTag->set_tagName(tag_name);
            for(const auto& attribute : attributes)
            {
                newTag->set_attr(attribute.first.c_str(), attribute.second.c_str());
            }
        }

        return newTag;
    }

    uint_ptr document::add_font(const font_description& descr, font_metrics* fm)
    {
        uint_ptr ret = 0;

        std::string key = descr.hash();

        if(m_fonts.find(key) == m_fonts.end())
        {
            font_item fi = {0, {}};

            fi.font      = m_container->create_font(descr, this, &fi.metrics);
            m_fonts[key] = fi;
            ret          = fi.font;
            if(fm)
            {
                *fm = fi.metrics;
            }
        }
        return ret;
    }

    uint_ptr document::get_font(const font_description& descr, font_metrics* fm)
    {
        if(descr.size == 0_px)
        {
            return 0;
        }

        auto key = descr.hash();

        auto el = m_fonts.find(key);

        if(el != m_fonts.end())
        {
            if(fm)
            {
                *fm = el->second.metrics;
            }
            return el->second.font;
        }
        return add_font(descr, fm);
    }

    std::atomic<uint64_t> document::s_layout_hits{0};
    std::atomic<uint64_t> document::s_layout_misses{0};
    std::atomic<uint64_t> document::s_layout_mismatches{0};

    bool document::layout_cache_enabled() const
    {
        static const bool disabled = getenv("LH_NO_LAYOUT_CACHE") != nullptr;
        return !disabled;
    }

    bool document::layout_cache_check() const
    {
        static const bool check = getenv("LH_LAYOUT_CACHE_CHECK") != nullptr;
        return check;
    }

    namespace
    {
        template <class F> void walk_boxes(render_item* item, float x, float y, bool unsafe, F&& visit)
        {
            const css_properties& css = item->src_el()->css();
            unsafe = unsafe || css.get_position() == element_position_absolute || css.get_position() == element_position_fixed ||
                     css.is_paint_group();
            const position& pos = item->pos_const();
            visit(item, x, y, unsafe);
            unsafe = unsafe || !css.get_transform().empty() || is_one_of(css.get_overflow(), overflow_scroll, overflow_auto);
            const float cx = x + pos.x.value();
            const float cy = y + pos.y.value();
            for(const auto& child : item->children())
                walk_boxes(child.get(), cx, cy, unsafe, visit);
        }

        bool paints_inside_box(const render_item* item)
        {
            const element::ptr&   el  = item->src_el();
            const css_properties& css = el->css();
            if(el->is_text())
            {
                const element::ptr parent = el->parent();
                return parent && parent->css().get_text_shadows().empty();
            }
            if(css.get_display() == display_list_item || !css.get_box_shadows().empty() || !css.get_text_shadows().empty() ||
               (css.get_outline().style != border_style_none && css.get_outline().style != border_style_hidden))
                return false;
            if(el->is_inline() && !el->is_inline_box())
            {
                const background* bg = el->get_background();
                if((bg && !bg->is_empty()) || css.get_borders().is_visible())
                    return false;
            }
            return !std::dynamic_pointer_cast<el_svg>(el);
        }
    }

    void document::snapshot_layout()
    {
        m_layout_before.clear();
        m_layout_before.reserve(m_layout_before.capacity());
        walk_boxes(m_root_render.get(), 0, 0, false, [this](const render_item* item, float x, float y, bool) {
            position box  = item->pos_const();
            box.x        += pixel_t(x);
            box.y        += pixel_t(y);
            m_layout_before.push_back({item, box});
        });
        m_layout_before_valid = true;
    }

    bool document::layout_dirty_regions(std::vector<position>& out)
    {
        out.clear();
        if(!m_layout_before_valid || !m_layout_after_render || !m_root_render)
            return false;
        if(!m_restyle_confined)
            return false;
        std::unordered_set<const element*> origins;
        std::vector<const element*>        sources(m_layout_origins.begin(), m_layout_origins.end());
        for(const auto& region : m_restyle_regions)
            sources.push_back(region.get());
        for(const element* el : sources)
        {
            const element* owner = el;
            while(owner && !const_cast<element*>(owner)->get_render_item())
            {
                const element::ptr parent = owner->parent();
                owner                     = parent.get();
            }
            if(!owner)
                return false;
            origins.insert(owner);
        }
        size_t index = 0;
        bool   ok    = true;
        double area  = 0;
        auto   add   = [&](const render_item* item, position box) {
            box += item->get_paddings_const();
            box += item->get_borders_const();
            pixel_t slack = 1_px;
            if(item->src_el()->is_text())
                slack = std::max(4_px, pixel_t(std::ceil(float(box.height) / 2) + 1)) + 1_px;
            box.x      -= slack;
            box.y      -= slack;
            box.width  += slack * 2;
            box.height += slack * 2;
            if(box.width <= 0_px || box.height <= 0_px)
                return;
            area += double(float(box.width)) * double(float(box.height));
            out.push_back(box);
        };
        walk_boxes(m_root_render.get(), 0, 0, false, [&](const render_item* item, float x, float y, bool unsafe) {
            if(!ok)
                return;
            if(index >= m_layout_before.size() || m_layout_before[index].item != item)
            {
                ok = false;
                return;
            }
            const position& before = m_layout_before[index++].box;
            position        box    = item->pos_const();
            box.x                 += pixel_t(x);
            box.y                 += pixel_t(y);
            if(before == box && !origins.count(item->src_el().get()))
                return;
            if(unsafe || !paints_inside_box(item))
            {
                ok = false;
                return;
            }
            add(item, before);
            add(item, box);
            if(out.size() > 128)
                ok = false;
        });
        if(!ok || index != m_layout_before.size())
            return false;
        const position& root = m_root_render->pos_const();
        if(!(area <= 0.5 * std::max(1.0, double(float(root.width)) * double(float(root.height)))))
            return false;
        return true;
    }

    pixel_t document::render(pixel_t max_width, render_type rt)
    {
        invalidate_paint();
        pixel_t ret = 0_px;
        if(m_root && m_root_render)
        {
            position viewport;
            m_container->get_viewport(viewport);
            if(!(viewport == m_layout_viewport))
            {
                m_layout_viewport = viewport;
                invalidate_layout_cache();
            }
            m_layout_marked.clear();
            containing_block_context cb_context;
            cb_context.width       = max_width;
            cb_context.width.type  = containing_block_context::cbc_value_type_absolute;
            cb_context.height      = viewport.height;
            cb_context.height.type = containing_block_context::cbc_value_type_absolute;

            if(rt == render_fixed_only)
            {
                m_fixed_boxes.clear();
                m_root_render->render_positioned(rt);
            } else
            {
                static const bool refetch = getenv("LH_NO_POSITIONED_REUSE") != nullptr;
                if(refetch || m_positioned_epoch != m_style_epoch || m_positioned_tree != m_render_tree_serial)
                {
                    m_has_positioned   = m_root_render->fetch_positioned();
                    m_positioned_epoch = m_style_epoch;
                    m_positioned_tree  = m_render_tree_serial;
                }
                ret = m_root_render->render(0_px, 0_px, cb_context, nullptr).natural_width;
                for(render_item* item : m_relayout_roots)
                    item->relayout();
                m_relayout_roots.clear();
                m_layout_after_render = true;
                if(m_has_positioned)
                {
                    m_fixed_boxes.clear();
                    m_root_render->render_positioned(rt);
                }
                m_size.width  = 0;
                m_size.height = 0;
                m_root_render->calc_document_size(m_size);
            }
        }
        return ret;
    }

    void document::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip, bool cull)
    {
        if(m_root && m_root_render)
        {
            const bool saved = m_cull_paint;
            m_cull_paint     = cull && clip && !g_paint_trace;
            if(!m_root->css().get_clip_path().empty())
            {
                m_root_render->draw_group(hdc, x, y, clip, m_root_render);
            } else
            {
                m_root->draw(hdc, x, y, clip, m_root_render);
                m_root_render->draw_stacking_context(hdc, x, y, clip, true);
            }
            m_cull_paint = saved;
        }
    }

    pixel_t document::to_pixels(const css_length& val, const font_metrics& metrics, pixel_t size) const
    {
        if(val.is_predefined())
        {
            return 0_px;
        }
        pixel_t ret;
        switch(val.units())
        {
        case css_units_percentage:
            ret = val.calc_percent(size);
            break;
        case css_units_calc:
            ret = calc_evaluate(static_cast<int>(val.calc_index()), size, [&](float value, css_units unit) {
                return to_pixels(css_length(value, unit), metrics, size).value();
            });
            break;
        case css_units_em:
            ret = metrics.font_size * val.val();
            break;

        // https://drafts.csswg.org/css-values-4/#absolute-lengths
        case css_units_pt:
            ret = m_container->pt_to_px(val.val());
            break;
        case css_units_in:
            ret = m_container->pt_to_px(val.val() * 72); // 1in = 72pt
            break;
        case css_units_pc:
            ret = m_container->pt_to_px(val.val() * 12); // 1pc = (1/6)in = 12pt
            break;
        case css_units_cm:
            ret = m_container->pt_to_px(val.val() * 72 / 2.54f); // 1cm = (1/2.54)in = (72/2.54)pt
            break;
        case css_units_mm:
            ret = m_container->pt_to_px(val.val() * 72 / 2.54f / 10);
            break;

        case css_units_vw:
            ret = pixel_t(m_media.width.value() * val.val() / 100.0f);
            break;
        case css_units_vh:
            ret = pixel_t(m_media.height.value() * val.val() / 100.0f);
            break;
        case css_units_vmin:
            ret = pixel_t(std::min(m_media.height.value(), m_media.width.value()) * val.val() / 100.0f);
            break;
        case css_units_vmax:
            ret = pixel_t(std::max(m_media.height.value(), m_media.width.value()) * val.val() / 100.0f);
            break;
        case css_units_rem:
            ret = pixel_t(m_root->css().get_font_size().value() * val.val());
            break;
        case css_units_ex:
            ret = pixel_t(metrics.x_height.value() * val.val());
            break;
        case css_units_ch:
            ret = pixel_t(metrics.ch_width.value() * val.val());
            break;
        default:
            ret = pixel_t(val.val());
            break;
        }
        return ret;
    }

    void document::cvt_units(css_length& val, const font_metrics& metrics, pixel_t size) const
    {
        if(val.is_predefined())
        {
            return;
        }
        if(val.units() == css_units_calc)
        {
            // Every leaf but % in px; with no % left, the length is a plain one.
            float px    = 0;
            int   index = calc_resolve_units(val.calc_index(), [&](float value, css_units unit) {
                return to_pixels(css_length(value, unit), metrics, size).value();
            }, px);
            if(index < 0)
                val.set_value(px, css_units_px);
            else
                val.set_calc(index);
        } else if(val.units() != css_units_percentage)
        {
            val.set_value(static_cast<float>(to_pixels(val, metrics, size)), css_units_px);
        }
    }

    pixel_t document::width() const
    {
        return m_size.width;
    }

    pixel_t document::height() const
    {
        return m_size.height;
    }

    void document::add_stylesheet(const char* str, const char* baseurl, const char* media)
    {
        if(str && str[0])
        {
            m_css.emplace_back(str, baseurl, media);
        }
    }

    bool document::on_mouse_over(pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
                                 const std::function<void(const position&)>& redraw_box)
    {
        invalidate_paint();
        if(!m_root || !m_root_render)
        {
            return false;
        }

        element::ptr over_el = m_root_render->get_element_by_point(x, y, client_x, client_y, nullptr);

        bool state_was_changed = false;

        if(over_el != m_over_element)
        {
            if(m_over_element)
            {
                if(m_over_element->on_mouse_leave())
                {
                    m_container->on_mouse_event(m_over_element, mouse_event_leave);
                    state_was_changed = true;
                }
            }
            m_over_element = over_el;
        }

        std::string cursor;

        if(m_over_element)
        {
            if(m_over_element->on_mouse_over())
            {
                state_was_changed = true;
            }
            cursor = m_over_element->css().get_cursor();
        }

        m_container->set_cursor(cursor.c_str());

        if(state_was_changed)
        {
            m_container->on_mouse_event(m_over_element, mouse_event_enter);
            return m_root->find_styles_changes(redraw_box);
        }
        return false;
    }

    std::vector<scroll_values> document::on_scroll(pixel_t dx, pixel_t dy, pixel_t x, pixel_t y, pixel_t client_x,
                                                   pixel_t client_y) const
    {
        invalidate_paint();
        if(dy == 0_px && dx == 0_px)
        {
            return {};
        }

        element::ptr vscroll_el;
        element::ptr hscroll_el;

        if(dy != 0_px)
        {
            vscroll_el = m_root_render->get_element_by_point(
                x, y, client_x, client_y,
                [dy](const std::shared_ptr<render_item>& el) -> bool { return el->is_v_scrollable(dy); });
        }

        if(dx != 0_px)
        {
            hscroll_el = m_root_render->get_element_by_point(
                x, y, client_x, client_y,
                [dx](const std::shared_ptr<render_item>& el) -> bool { return el->is_h_scrollable(dx); });
        }

        if(!vscroll_el && !hscroll_el)
        {
            return {};
        }

        if(vscroll_el == hscroll_el)
        {
            scroll_values sv;
            sv.dx         = hscroll_el->h_scroll(dx);
            sv.dy         = vscroll_el->v_scroll(dy);
            sv.scroll_box = hscroll_el->get_placement();
            return {sv};
        }

        std::vector<scroll_values> ret;
        ret.reserve(2);

        if(vscroll_el)
        {
            scroll_values sv;
            sv.dy         = vscroll_el->v_scroll(dy);
            sv.scroll_box = vscroll_el->get_placement();
            ret.push_back(sv);
        }

        if(hscroll_el)
        {
            scroll_values sv;
            sv.dx         = hscroll_el->h_scroll(dx);
            sv.scroll_box = hscroll_el->get_placement();
            ret.push_back(sv);
        }

        return ret;
    }

    bool document::on_mouse_leave(const std::function<void(const position&)>& redraw_box)
    {
        invalidate_paint();
        if(!m_root || !m_root_render)
        {
            return false;
        }
        if(m_over_element)
        {
            auto el        = m_over_element;
            m_over_element = nullptr;
            if(el->on_mouse_leave())
            {
                m_container->on_mouse_event(el, mouse_event_leave);
                return m_root->find_styles_changes(redraw_box);
            }
        }
        return false;
    }

    bool document::on_lbutton_down(pixel_t x, pixel_t y, pixel_t client_x, pixel_t client_y,
                                   const std::function<void(const position&)>& redraw_box)
    {
        invalidate_paint();
        if(!m_root || !m_root_render)
        {
            return false;
        }

        element::ptr over_el = m_root_render->get_element_by_point(x, y, client_x, client_y, nullptr);
        m_active_element     = over_el;

        bool state_was_changed = false;

        if(over_el != m_over_element)
        {
            if(m_over_element)
            {
                if(m_over_element->on_mouse_leave())
                {
                    m_container->on_mouse_event(m_over_element, mouse_event_leave);
                    state_was_changed = true;
                }
            }
            m_over_element = over_el;
            if(m_over_element)
            {
                if(m_over_element->on_mouse_over())
                {
                    state_was_changed = true;
                }
            }
        }

        std::string cursor;

        if(m_over_element)
        {
            if(m_over_element->on_lbutton_down())
            {
                state_was_changed = true;
            }
            cursor = m_over_element->css().get_cursor();
        }

        m_container->set_cursor(cursor.c_str());

        if(state_was_changed)
        {
            m_container->on_mouse_event(m_over_element, mouse_event_enter);
            return m_root->find_styles_changes(redraw_box);
        }

        return false;
    }

    bool document::on_lbutton_up(pixel_t /*x*/, pixel_t /*y*/, pixel_t /*client_x*/, pixel_t /*client_y*/,
                                 const std::function<void(const position&)>& redraw_box)
    {
        invalidate_paint();
        if(!m_root || !m_root_render)
        {
            return false;
        }
        if(m_over_element)
        {
            if(m_over_element->on_lbutton_up(m_active_element == m_over_element))
            {
                return m_root->find_styles_changes(redraw_box);
            }
        }
        return false;
    }

    bool document::on_button_cancel(const std::function<void(const position&)>& redraw_box)
    {
        invalidate_paint();
        m_active_element = nullptr;
        return on_mouse_leave(redraw_box);
    }

    void document::get_fixed_boxes(position::vector& fixed_boxes)
    {
        fixed_boxes = m_fixed_boxes;
    }

    void document::add_fixed_box(const position& pos)
    {
        m_fixed_boxes.push_back(pos);
    }

    bool document::media_changed()
    {
        invalidate_paint();
        container()->get_media_features(m_media);
        if(update_media_lists(m_media))
        {
            m_root->refresh_styles();
            m_root->compute_styles();
            // The set of rendered elements can change across a media breakpoint
            // (e.g. display:none <-> block on responsive nav/hero blocks). The render
            // tree is built once in createFromString() from the computed display values,
            // so rebuild it here to add/remove render items for elements whose display
            // just changed; otherwise a newly-shown element keeps no render item and
            // never lays out (it collapses to zero).
            m_table_cells = 0;
            m_root_render = m_root->create_render_item(nullptr);
            m_render_tree_serial++;
            m_layout_marked.clear();
            m_relayout_roots.clear();
            m_layout_before_valid = false;
            if(m_root_render)
            {
                m_root_render = m_root_render->init();
            }
            return true;
        }
        return false;
    }

    bool document::lang_changed()
    {
        invalidate_paint();
        if(!m_media_lists.empty())
        {
            std::string culture;
            container()->get_language(m_lang, culture);
            if(!culture.empty())
            {
                m_culture = m_lang + '-' + culture;
            } else
            {
                m_culture.clear();
            }
            m_root->refresh_styles();
            m_root->compute_styles();
            return true;
        }
        return false;
    }

    // Apply media features (determine which selectors are active).
    bool document::update_media_lists(const media_features& features)
    {
        invalidate_paint();
        bool update_styles = false;
        for(auto& media_list : m_media_lists)
        {
            if(media_list->apply_media_features(features))
            {
                update_styles = true;
            }
        }
        return update_styles;
    }

    void document::add_media_list(const media_query_list_list::ptr& list)
    {
        if(list && !contains(m_media_lists, list))
        {
            m_media_lists.push_back(list);
        }
    }

    void document::fix_tables_layout()
    {
        invalidate_paint();
        const std::size_t limit = m_tabular_elements.size() * 8 + 64;
        std::size_t       processed = 0;
        for(const auto& el_ptr : m_tabular_elements)
        {
            if(++processed > limit)
            {
                break;
            }
            switch(el_ptr->src_el()->css().get_display())
            {
            case display_inline_table:
            case display_table:
                fix_table_children(el_ptr, display_table_row_group, "table-row-group");
                break;
            case display_table_footer_group:
            case display_table_row_group:
            case display_table_header_group:
                {
                    auto parent = el_ptr->parent();
                    if(parent)
                    {
                        if(parent->src_el()->css().get_display() != display_inline_table)
                        {
                            fix_table_parent(el_ptr, display_table, "table");
                        }
                    }
                    fix_table_children(el_ptr, display_table_row, "table-row");
                }
                break;
            case display_table_row:
                fix_table_parent(el_ptr, display_table_row_group, "table-row-group");
                fix_table_children(el_ptr, display_table_cell, "table-cell");
                break;
            case display_table_cell:
                fix_table_parent(el_ptr, display_table_row, "table-row");
                break;
            // TODO: make table layout fix for table-caption, table-column etc. elements
            case display_table_caption:
            case display_table_column:
            case display_table_column_group:
            default:
                break;
            }
        }
    }

    void document::fix_table_children(const std::shared_ptr<render_item>& el_ptr, style_display disp,
                                      const char* disp_str)
    {
        std::list<std::shared_ptr<render_item>> tmp;
        auto                                    first_iter = el_ptr->children().begin();
        auto                                    cur_iter   = el_ptr->children().begin();

        auto flush_elements = [&]() {
            element::ptr annon_tag = std::make_shared<html_tag>(el_ptr->src_el(), std::string("display:") + disp_str);
            std::shared_ptr<render_item> annon_ri;
            if(annon_tag->css().get_display() == display_table_cell)
            {
                annon_tag->set_tagName("table_cell");
                annon_ri = std::make_shared<render_item_block>(annon_tag);
            } else if(annon_tag->css().get_display() == display_table_row)
            {
                annon_ri = std::make_shared<render_item_table_row>(annon_tag);
            } else
            {
                annon_ri = std::make_shared<render_item_table_part>(annon_tag);
            }
            for(const auto& el : tmp)
            {
                annon_ri->add_child(el);
            }
            // add annon item as tabular for future processing
            add_tabular(annon_ri);
            annon_ri->parent(el_ptr);
            first_iter = el_ptr->children().insert(first_iter, annon_ri);
            cur_iter   = std::next(first_iter);
            while(cur_iter != el_ptr->children().end() && (*cur_iter)->parent() != el_ptr)
            {
                cur_iter = el_ptr->children().erase(cur_iter);
            }
            first_iter = cur_iter;
            tmp.clear();
        };

        while(cur_iter != el_ptr->children().end())
        {
            if((*cur_iter)->src_el()->css().get_display() != disp)
            {
                if(!(*cur_iter)->src_el()->is_table_skip() || ((*cur_iter)->src_el()->is_table_skip() && !tmp.empty()))
                {
                    if(disp != display_table_row_group ||
                       (*cur_iter)->src_el()->css().get_display() != display_table_caption)
                    {
                        if(tmp.empty())
                        {
                            first_iter = cur_iter;
                        }
                        tmp.push_back((*cur_iter));
                    }
                }
                cur_iter++;
            } else if(!tmp.empty())
            {
                flush_elements();
            } else
            {
                cur_iter++;
            }
        }
        if(!tmp.empty())
        {
            flush_elements();
        }
    }

    void document::fix_table_parent(const std::shared_ptr<render_item>& el_ptr, style_display disp,
                                    const char* disp_str)
    {
        auto parent = el_ptr->parent();

        if(parent->src_el()->css().get_display() != disp)
        {
            auto this_element = std::find_if(parent->children().begin(), parent->children().end(),
                                             [&](const std::shared_ptr<render_item>& el) { return el == el_ptr; });
            if(this_element != parent->children().end())
            {
                style_display el_disp = el_ptr->src_el()->css().get_display();
                auto          first   = this_element;
                auto          last    = this_element;
                auto          cur     = this_element;

                // find first element with same display
                while(true)
                {
                    if(cur == parent->children().begin())
                    {
                        break;
                    }
                    cur--;
                    if((*cur)->src_el()->is_table_skip() || (*cur)->src_el()->css().get_display() == el_disp)
                    {
                        first = cur;
                    } else
                    {
                        break;
                    }
                }

                // find last element with same display
                cur = this_element;
                while(true)
                {
                    cur++;
                    if(cur == parent->children().end())
                    {
                        break;
                    }

                    if((*cur)->src_el()->is_table_skip() || (*cur)->src_el()->css().get_display() == el_disp)
                    {
                        last = cur;
                    } else
                    {
                        break;
                    }
                }

                // extract elements with the same display and wrap them with anonymous object
                element::ptr annon_tag =
                    std::make_shared<html_tag>(parent->src_el(), std::string("display:") + disp_str);
                std::shared_ptr<render_item> annon_ri;
                if(annon_tag->css().get_display() == display_table ||
                   annon_tag->css().get_display() == display_inline_table)
                {
                    annon_ri = std::make_shared<render_item_table>(annon_tag);
                } else if(annon_tag->css().get_display() == display_table_row)
                {
                    annon_ri = std::make_shared<render_item_table_row>(annon_tag);
                } else
                {
                    annon_ri = std::make_shared<render_item_table_part>(annon_tag);
                }
                std::for_each(first, std::next(last, 1),
                              [&annon_ri](std::shared_ptr<render_item>& el) { annon_ri->add_child(el); });
                first = parent->children().erase(first, std::next(last));
                parent->children().insert(first, annon_ri);
                add_tabular(annon_ri);
                annon_ri->parent(parent);
            }
        }
    }

    void document::append_children_from_string(element& parent, const char* str, bool replace_existing)
    {
        invalidate_paint();
        // parent must belong to this document
        if(parent.get_document().get() != this)
        {
            return;
        }

        GumboOptions opts = kGumboDefaultOptions;
        // This is require to prevent creating html, head, body tags around the fragment
        // Although Gumbo always creates html tag anyway. We have to ignore it in create_node.
        opts.fragment_context = GUMBO_TAG_BODY;
        // parse document into GumboOutput
        GumboOutput* output = gumbo_parse_with_options(&opts, str, strlen(str));

        // Create litehtml::elements.
        elements_list child_elements;
        // Create elements excluding the root node
        create_node(output->root, child_elements, true, false);

        // Destroy GumboOutput
        gumbo_destroy_output(&kGumboDefaultOptions, output);

        auto parent_render = parent.get_render_item();

        if(replace_existing)
        {
            parent.clearRecursive();
            parent_render->children().clear();
        }

        // Let's process created elements tree
        for(const auto& child : child_elements)
        {
            // Add the child element to parent
            parent.appendChild(child);

            // apply master CSS
            child->apply_stylesheet(m_master_css);

            // parse elements attributes
            child->parse_attributes();

            // Apply parsed styles.
            child->apply_stylesheet(m_styles);

            // Apply user styles if any
            child->apply_stylesheet(m_user_css);

            // Initialize m_css
            child->compute_styles();

            // Finally initialize elements
            if(parent_render)
            {
                auto child_render = child->create_render_item(parent_render);
                if(child_render)
                {
                    child_render = child_render->init();
                    parent_render->add_child(child_render);
                }
            }
        }
        // Now the m_tabular_elements is filled with tabular elements.
        // We have to check the tabular elements for missing table elements
        // and create the anonymous boxes in visual table layout
        fix_tables_layout();
    }

    void document::dump(dumper& cout)
    {
        if(m_root_render)
        {
            m_root_render->dump(cout);
        }
    }

} // namespace litehtml
