#include <unordered_map>
#include <algorithm>
#include <cstdint>

#include "html.h"
#include "document.h"
#include "document_container.h"
#include "html_microsyntaxes.h"
#include "html_tag.h"
#include "css_parser.h"
#include <set>
#include "internal.h"
#include "iterators.h"
#include "line_box.h"
#include "num_cvt.h"
#include "render_item.h"
#include "stylesheet.h"
#include "table.h"

namespace litehtml
{

    litehtml::html_tag::html_tag(const std::shared_ptr<document>& doc) :
        element(doc)
    {
    }

    litehtml::html_tag::html_tag(const element::ptr& parent, const std::string& style) :
        element(parent->get_document())
    {
        litehtml::style st;
        st.add(style);
        add_style(st);
        m_anonymous_style = true;
        this->parent(parent);
        compute_styles();
    }

    bool litehtml::html_tag::appendChild(const element::ptr& el)
    {
        if(el)
        {
            el->parent(shared_from_this());
            m_children.push_back(el);
            return true;
        }
        return false;
    }

    bool litehtml::html_tag::removeChild(const element::ptr& el)
    {
        if(el && el->parent() == shared_from_this())
        {
            el->parent(nullptr);
            m_children.erase(std::remove(m_children.begin(), m_children.end(), el), m_children.end());
            return true;
        }
        return false;
    }

    void litehtml::html_tag::clearRecursive()
    {
        for(auto& el : m_children)
        {
            el->clearRecursive();
            el->parent(nullptr);
        }
        m_children.clear();
    }

    string_id html_tag::id() const
    {
        return m_id;
    }

    string_id html_tag::tag() const
    {
        return m_tag;
    }

    const char* html_tag::get_tagName() const
    {
        return _s(m_tag).c_str();
    }

    void html_tag::set_tagName(const char* tag)
    {
        m_tag = _id(lowcase(tag));
    }

    void html_tag::set_attr(const char* _name, const char* _val)
    {
        if(_name && _val)
        {
            // attribute names in attribute selector are matched ASCII case-insensitively regardless of document mode
            std::string name = lowcase(_name);
            // m_attrs has all attribute values, including class and id, in their original case
            // because in attribute selector values are matched case-sensitively even in quirks mode
            m_attrs[name] = _val;

            if(name == "class")
            {
                std::string val = _val;
                // class names in class selector (.xxx) are matched ASCII case-insensitively in quirks mode
                if(get_document()->mode() == quirks_mode)
                {
                    lcase(val);
                }
                m_str_classes = split_string(val, whitespace, "", "");
                m_classes.clear();
                for(const auto& cls : m_str_classes)
                {
                    m_classes.push_back(_id(cls));
                }
            } else if(name == "id")
            {
                std::string val = _val;
                // ids in id selector (#xxx) are matched ASCII case-insensitively in quirks mode
                if(get_document()->mode() == quirks_mode)
                {
                    lcase(val);
                }
                m_id = _id(val);
            }
        }
    }

    const char* html_tag::get_attr(const char* name, const char* def) const
    {
        auto attr = m_attrs.find(name);
        if(attr != m_attrs.end())
        {
            return attr->second.c_str();
        }
        return def;
    }

    litehtml::elements_list litehtml::html_tag::select_all(const std::string& selector)
    {
        css_selector sel;
        sel.parse(selector, get_document()->mode());

        return select_all(sel);
    }

    litehtml::elements_list litehtml::html_tag::select_all(const css_selector& selector)
    {
        litehtml::elements_list res;
        select_all(selector, res);
        return res;
    }

    void litehtml::html_tag::select_all(const css_selector& selector, elements_list& res)
    {
        if(select(selector))
        {
            res.push_back(shared_from_this());
        }

        for(auto& el : m_children)
        {
            el->select_all(selector, res);
        }
    }

    litehtml::element::ptr litehtml::html_tag::select_one(const std::string& selector)
    {
        css_selector sel;
        sel.parse(selector, get_document()->mode());

        return select_one(sel);
    }

    litehtml::element::ptr litehtml::html_tag::select_one(const css_selector& selector)
    {
        if(select(selector))
        {
            return shared_from_this();
        }

        for(auto& el : m_children)
        {
            element::ptr res = el->select_one(selector);
            if(res)
            {
                return res;
            }
        }
        return nullptr;
    }

    namespace
    {
        constexpr int64_t        kSelectorStepBudget = 4000000;
        thread_local int         g_apply_depth       = 0;
        thread_local int64_t     g_selector_steps    = 0;

        thread_local uint64_t    g_apply_pass        = 0;

        bool prune_enabled()
        {
            static const bool enabled = getenv("LH_NO_DESCENDANT_PRUNE") == nullptr;
            return enabled;
        }

        struct sibling_index
        {
            uint64_t                                         pass  = 0;
            size_t                                           count = 0;
            std::vector<element*>                            kids;
            std::unordered_map<const element*, size_t>       index;
        };

        thread_local std::unordered_map<const element*, sibling_index> g_sibling_indexes;

        struct apply_scope
        {
            apply_scope()
            {
                if(g_apply_depth++ == 0)
                {
                    g_selector_steps = 0;
                    g_apply_pass++;
                    g_sibling_indexes.clear();
                }
            }
            ~apply_scope() { g_apply_depth--; }
        };
    }

    void litehtml::html_tag::apply_stylesheet(const litehtml::css& stylesheet)
    {
        apply_scope scope;
        for(const auto& sel : stylesheet.selectors())
            apply_selector(sel);

        for(auto& el : m_children)
        {
            if(el->css().get_display() != display_inline_text)
            {
                el->apply_stylesheet(stylesheet);
            }
        }
    }

    bool litehtml::html_tag::flip_class(const std::string& from, const std::string& to, uint64_t epoch, int* region)
    {
        static const bool disabled = getenv("LH_NO_CLASS_FLIP") != nullptr;
        if(disabled)
            return false;
        for(const auto& child : m_children)
        {
            if(child->tag() == __tag_before_ || child->tag() == __tag_after_ || !child->children().empty())
                return false;
        }
        const int parent_id = parent() ? parent()->css().get_shared_id() : 0;
        auto same_others = [this](const string_map& others) {
            auto a = others.begin();
            for(auto b = m_attrs.begin(); b != m_attrs.end(); ++b)
            {
                if(b->first == "class")
                    continue;
                if(a == others.end() || a->first != b->first || a->second != b->second)
                    return false;
                ++a;
            }
            return a == others.end();
        };
        flip_state* saved  = nullptr;
        flip_state* target = nullptr;
        for(auto& state : m_flips)
        {
            if(state.value == from)
                saved = &state;
        }
        if(!saved)
        {
            if(m_flips.size() >= 3)
                m_flips.erase(m_flips.begin());
            m_flips.emplace_back();
            saved        = &m_flips.back();
            saved->value = from;
        }
        if(saved->epoch != 0 && saved->epoch != epoch)
            saved->regions.clear();
        saved->epoch     = epoch;
        saved->parent_id = parent_id;
        if(!same_others(saved->others))
        {
            saved->others = m_attrs;
            saved->others.erase("class");
        }
        for(auto& state : m_flips)
        {
            if(state.value != to || state.epoch != epoch || state.parent_id != parent_id || state.kids.size() != m_children.size() ||
               !state.regions.count(from) || !same_others(state.others))
                continue;
            target = &state;
        }
        auto store = [this](flip_state& state, bool move) {
            if(move)
            {
                std::swap(state.str_classes, m_str_classes);
                std::swap(state.classes, m_classes);
                std::swap(state.st, m_style);
                std::swap(state.st_base, m_style_base);
                std::swap(state.used, m_used_styles);
                std::swap(state.css, m_css);
                state.has_base = m_has_style_base;
                state.kids.resize(m_children.size());
                size_t i = 0;
                for(const auto& child : m_children)
                    std::swap(state.kids[i++], child->css_w());
                return;
            }
            state.str_classes = m_str_classes;
            state.classes     = m_classes;
            state.st          = m_style;
            state.st_base     = m_style_base;
            state.has_base    = m_has_style_base;
            state.used.clear();
            for(const auto& us : m_used_styles)
            {
                auto copy    = std::make_unique<used_selector>(us->m_selector, us->m_used);
                state.used.push_back(std::move(copy));
            }
            state.css = m_css;
            state.kids.clear();
            for(const auto& child : m_children)
                state.kids.push_back(child->css());
        };
        if(!target)
        {
            store(*saved, false);
            return false;
        }
        *region = target->regions[from];
        saved->str_classes = std::move(m_str_classes);
        m_str_classes      = std::move(target->str_classes);
        saved->classes     = std::move(m_classes);
        m_classes          = std::move(target->classes);
        saved->st          = std::move(m_style);
        m_style            = std::move(target->st);
        saved->st_base     = std::move(m_style_base);
        m_style_base       = std::move(target->st_base);
        saved->used        = std::move(m_used_styles);
        m_used_styles      = std::move(target->used);
        saved->css         = std::move(m_css);
        m_css              = std::move(target->css);
        saved->has_base    = m_has_style_base;
        m_has_style_base   = target->has_base;
        std::swap(saved->kids, target->kids);
        size_t i = 0;
        for(const auto& child : m_children)
            std::swap(saved->kids[i++], child->css_w());
        target->kids.clear();
        m_attrs["class"] = to;
        return true;
    }

    void litehtml::html_tag::note_flip(const std::string& from, const std::string& to, int region)
    {
        bool known = false;
        for(auto& state : m_flips)
        {
            if(state.value == to)
            {
                state.regions[from] = region;
                known               = true;
            }
        }
        if(!known)
        {
            if(m_flips.size() >= 3)
                m_flips.erase(m_flips.begin());
            m_flips.emplace_back();
            m_flips.back().value         = to;
            m_flips.back().regions[from] = region;
        }
        for(auto& state : m_flips)
        {
            if(state.value == from)
                state.regions[to] = region;
        }
    }

    void litehtml::html_tag::apply_matching(const litehtml::css* sheets[3], uint64_t epoch)
    {
        static const bool disabled = getenv("LH_NO_MATCH_MEMO") != nullptr;
        if(!disabled)
        {
            for(const auto& memo : m_match_memos)
            {
                if(memo.epoch != epoch || memo.attrs != m_attrs)
                    continue;
                for(int k = 0; k < 3; k++)
                {
                    if(k == 1)
                        parse_attributes();
                    for(const auto& sel : memo.rules[k])
                        apply_selector(sel);
                }
                return;
            }
        }
        size_t       bounds[4] = {0, 0, 0, 0};
        const size_t start     = m_used_styles.size();
        for(int k = 0; k < 3; k++)
        {
            if(k == 1)
                parse_attributes();
            bounds[k] = m_used_styles.size();
            apply_stylesheet(*sheets[k]);
        }
        bounds[3] = m_used_styles.size();
        if(disabled || start != 0 || !m_children.empty())
            return;
        match_memo memo;
        memo.epoch = epoch;
        memo.attrs = m_attrs;
        for(int k = 0; k < 3; k++)
        {
            for(size_t i = bounds[k]; i < bounds[k + 1]; i++)
                memo.rules[k].push_back(m_used_styles[i]->m_selector);
        }
        if(m_match_memos.size() >= 2)
            m_match_memos.erase(m_match_memos.begin());
        m_match_memos.push_back(std::move(memo));
    }

    void litehtml::html_tag::apply_selector(const css_selector::ptr& sel)
    {
        {
            {
                const auto& r = sel->m_right;
                if(r.m_tag != star_id && r.m_tag != m_tag)
                {
                    return;
                }

                if(!r.m_attrs.empty())
                {
                    const auto& attr = r.m_attrs[0];
                    if(attr.type == select_class && !(attr.name in m_classes))
                    {
                        return;
                    }
                }
            }

            uint32_t apply = select(*sel, false);

            if(apply != select_no_match)
            {
                used_selector::ptr us = std::make_unique<used_selector>(sel, false);

                if(sel->is_media_valid())
                {
                    auto apply_before_after = [&]() {
                        const auto& content_property = sel->m_style->get_property(_content_);
                        bool        content_none =
                            content_property.is<std::string>() && content_property.get<std::string>() == "none";
                        bool create =
                            !content_none && (sel->m_right.m_attrs.size() > 1 || sel->m_right.m_tag != star_id);

                        element::ptr el;
                        if(apply & select_match_with_after)
                        {
                            el = get_element_after(*sel->m_style, create);
                        } else if(apply & select_match_with_before)
                        {
                            el = get_element_before(*sel->m_style, create);
                        } else
                        {
                            return;
                        }
                        if(el)
                        {
                            if(!content_none)
                            {
                                el->add_style(*sel->m_style);
                            } else
                            {
                                el->parent()->removeChild(el);
                            }
                        } else
                        {
                            if(!content_none)
                            {
                                add_style(*sel->m_style);
                            }
                        }
                        us->m_used = true;
                    };

                    if(apply & select_match_pseudo_class)
                    {
                        if(select(*sel, true))
                        {
                            if((apply & (select_match_with_after | select_match_with_before)))
                            {
                                apply_before_after();
                            } else
                            {
                                add_style(*sel->m_style);
                                us->m_used = true;
                            }
                        }
                    } else if((apply & (select_match_with_after | select_match_with_before)))
                    {
                        apply_before_after();
                    } else
                    {
                        add_style(*sel->m_style);
                        us->m_used = true;
                    }
                }
                m_used_styles.push_back(std::move(us));
            }
        }
    }

    void litehtml::html_tag::get_content_size(size& sz, pixel_t max_width)
    {
        sz.height = 0;
        if(m_css.get_display() == display_block)
        {
            sz.width = max_width;
        } else
        {
            sz.width = 0;
        }
    }

    void litehtml::html_tag::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                  const std::shared_ptr<render_item>& ri)
    {
        position pos  = ri->pos();
        pos.x        += x;
        pos.y        += y;

        draw_background(hdc, x, y, clip, ri);

        if(m_css.get_display() == display_list_item &&
           (m_css.get_list_style_type() != list_style_type_none || m_css.get_list_style_image() != ""))
        {
            if(m_css.clips_contents())
            {
                position border_box  = pos;
                border_box          += ri->get_paddings();
                border_box          += ri->get_borders();

                border_radiuses bdr_radius =
                    m_css.get_borders().radius.calc_percents(border_box.width, border_box.height);

                bdr_radius -= ri->get_borders();
                bdr_radius -= ri->get_paddings();

                doc_ptr()->container()->set_clip(pos, bdr_radius);
            }

            draw_list_marker(hdc, pos, ri);

            if(m_css.clips_contents())
            {
                doc_ptr()->container()->del_clip();
            }
        }
    }

    std::string html_tag::raw_declaration(const std::string& name) const
    {
        string_id raw = raw_declaration_id(_id(name));
        if(raw == empty_id)
            return {};
        const property_value& value = m_style.get_property(raw);
        if(!value.is<css_token_vector>())
            return {};
        return get_repr(value.get<css_token_vector>(), 0, -1, true);
    }

    const property_value& html_tag::ua_property(string_id name) const
    {
        static const property_value& none = *new property_value();
        auto doc = get_document();
        if(!doc)
            return none;
        // The sheet is sorted by specificity and order, so the last match wins, unless an earlier one is important.
        const property_value* found = &none;
        for(const auto& sel : doc->master_css().selectors())
        {
            if(sel->m_layered)
                continue;
            const property_value& value = sel->m_style->get_property(name);
            if(value.is<invalid>() || (found->m_important && !value.m_important) || !sel->is_media_valid())
                continue;
            int match = const_cast<html_tag*>(this)->select(*sel, true);
            if(!(match & select_match) || (match & (select_match_with_before | select_match_with_after)))
                continue;
            found = &value;
        }
        return *found;
    }

    bool html_tag::get_custom_property(string_id name, css_token_vector& result) const
    {
        const property_value& value = m_style.get_property(name);

        if(value.is<css_token_vector>())
        {
            result = value.get<css_token_vector>();
            return true;
        }
        if(auto _parent = dynamic_cast<html_tag*>(parent().get()))
        {
            return _parent->get_custom_property(name, result);
        }
        return false;
    }

    uint64_t litehtml::html_tag::paint_signature() const
    {
        uint64_t h = element::paint_signature();
        auto mix = [&h](const std::string& value) {
            for(unsigned char c : value)
            {
                h ^= c;
                h *= 1099511628211ULL;
            }
            h ^= 0xff;
            h *= 1099511628211ULL;
        };
        mix(std::to_string(static_cast<int>(m_tag)));
        for(const auto& attr : m_attrs)
        {
            mix(attr.first);
            mix(attr.second);
        }
        return h;
    }

    bool html_tag::background_color_is_local() const
    {
        if(m_anonymous_style)
            return false;
        for(const auto& child : m_children)
        {
            if(auto tag = std::dynamic_pointer_cast<html_tag>(child))
            {
                if(tag->m_style.get_property(_background_color_).is<inherit>())
                    return false;
            }
        }
        return true;
    }

    void html_tag::compute_background_style()
    {
        auto doc = get_document();
        doc->note_style_change();
        const char* style = get_attr("style");
        if(style)
        {
            if(!m_has_style_base)
            {
                m_style_base = m_style;
                m_has_style_base = true;
            }
            m_style.add(style, "", doc->container());
        }
        m_style.subst_vars(this);
        m_css.compute_background_color(this);
        m_css.set_shared_id(doc->next_share_id());
    }

    bool html_tag::patch_inline_properties(const std::vector<string_id>& names, const std::string& patch)
    {
        if(m_anonymous_style || !m_has_style_base || m_style.has_var_property() || m_style_base.has_var_property())
            return false;
        for(const auto& child : m_children)
        {
            if(auto tag = dynamic_cast<const html_tag*>(child.get()))
            {
                for(string_id name : names)
                    if(tag->m_style.get_property(name).is<inherit>())
                        return false;
            }
        }
        auto  doc = get_document();
        style            scratch;
        css_token_vector fast;
        if(names.size() == 1 && names[0] == _transform_ && patch.size() > 11 &&
           patch.compare(0, 10, "transform:") == 0 && patch.back() == ';' &&
           fast_transform_tokens(patch.substr(10, patch.size() - 11), fast))
        {
            if(!doc->container() || doc->container()->sanitize_declaration("transform", fast))
                scratch.add_property(_transform_, fast, "", false, doc->container());
        } else
            scratch.add(normalize(patch, f_componentize), "", doc->container());
        std::vector<string_id> ids;
        for(string_id name : names)
        {
            ids.push_back(name);
            if(string_id raw = raw_declaration_id(name); raw != empty_id)
                ids.push_back(raw);
        }
        if(scratch.has_var_property() ||
           !scratch.only_declares([&ids](string_id id) { return std::find(ids.begin(), ids.end(), id) != ids.end(); }))
            return false;
        const bool group     = m_css.is_paint_group();
        const bool transform = m_css.get_transform().empty();
        doc->note_style_change();
        m_style.replace_properties(ids, m_style_base, scratch);
        for(string_id name : names)
        {
            if(name == _transform_)
                m_css.compute_transform(this, doc);
            else if(name == _opacity_)
                m_css.compute_opacity(this);
            else
                m_css.compute_background_color(this);
        }
        if(m_css.is_paint_group() != group || m_css.get_transform().empty() != transform)
        {
            restore_base_style();
            compute_styles(true);
            return true;
        }
        m_css.set_shared_id(doc->next_share_id());
        return true;
    }

    bool html_tag::can_restyle_width_shallow() const
    {
        if(m_anonymous_style || !m_has_style_base || !m_pseudo_classes.empty() ||
           m_style.has_var_property() || m_style_base.has_var_property() ||
           m_style.declares_custom_property() || m_style_base.declares_custom_property())
            return false;
        const property_value& width = m_style.get_property(_width_);
        if(!width.is<css_length>() || width.get<css_length>().is_predefined())
            return false;
        for(const auto& child : m_children)
        {
            if(child->tag() == __tag_before_ || child->tag() == __tag_after_)
                return false;
            if(auto tag = dynamic_cast<const html_tag*>(child.get()))
            {
                if(tag->m_anonymous_style || tag->m_style.get_property(_width_).is<inherit>() ||
                   tag->m_style.has_var_property() || tag->m_style_base.has_var_property())
                    return false;
            }
        }
        return true;
    }

    void litehtml::html_tag::compute_styles(bool recursive)
    {
        document::ptr doc = get_document();
        doc->note_style_change();
        mark_layout_dirty();

        bool        eligible = false;
        bool        shared   = false;
        std::string shareKey;

        if(doc->style_sharing_enabled() && !m_anonymous_style && m_pseudo_classes.empty() && get_attr("style") == nullptr &&
           !m_style.has_var_property() && (m_id == empty_id || !doc->ids_selectable()))
        {
            eligible = true;
            for(const auto& attr : m_attrs)
            {
                const std::string& name = attr.first;
                if(name != "class" && name.rfind("data-", 0) != 0 && (name != "id" || doc->ids_selectable()))
                {
                    eligible = false;
                    break;
                }
            }
        }

        if(eligible)
        {
            int parentId = 0;
            if(const auto& par = parent())
            {
                parentId = par->css().get_shared_id();
            }
            shareKey = std::to_string(parentId) + '#' + std::to_string(static_cast<int>(m_tag));
            for(const auto& us : m_used_styles)
            {
                shareKey += us->m_used ? '|' : '!';
                shareKey += std::to_string(reinterpret_cast<uintptr_t>(us->m_selector.get()));
            }
            if(const css_properties* hit = doc->share_lookup(shareKey))
            {
                m_css  = *hit;
                shared = true;
            }
        }

        if(!shared)
        {
            const char* style = get_attr("style");
            if(style)
            {
                if(!m_has_style_base)
                {
                    m_style_base     = m_style;
                    m_has_style_base = true;
                }
                m_style.add(style, "", doc->container());
            }
            m_style.subst_vars(this);
            m_css.compute(this, doc);
            m_css.set_shared_id(doc->next_share_id());
            if(eligible)
            {
                doc->share_store(shareKey, m_css);
            }
        }

        if(recursive)
        {
            for(const auto& el : m_children)
            {
                el->compute_styles();
            }
        }
    }

    bool litehtml::html_tag::is_white_space() const
    {
        return false;
    }

    int html_tag::select(const css_selector::vector& selector_list, bool apply_pseudo)
    {
        for(const auto& sel : selector_list)
        {
            if(int result = select(*sel, apply_pseudo))
            {
                return result;
            }
        }
        return select_no_match;
    }

    int litehtml::html_tag::select(const std::string& selector)
    {
        css_selector sel;
        sel.parse(selector, get_document()->mode());
        return select(sel, true);
    }

    int litehtml::html_tag::select(const css_selector& selector, bool apply_pseudo)
    {
        if(g_apply_depth > 0 && ++g_selector_steps > kSelectorStepBudget)
        {
            return select_no_match;
        }
        int right_res = select(selector.m_right, apply_pseudo);
        if(right_res == select_no_match)
        {
            return select_no_match;
        }
        element::ptr el_parent = parent();
        if(selector.m_left)
        {
            if(!el_parent)
            {
                return select_no_match;
            }
            switch(selector.m_combinator)
            {
            case combinator_descendant:
                {
                    bool         is_pseudo = false;
                    element::ptr res       = find_ancestor(*selector.m_left, apply_pseudo, &is_pseudo);
                    if(!res)
                    {
                        return select_no_match;
                    }
                    if(is_pseudo)
                    {
                        right_res |= select_match_pseudo_class;
                    }
                }
                break;
            case combinator_child:
                {
                    int res = el_parent->select(*selector.m_left, apply_pseudo);
                    if(res == select_no_match)
                    {
                        return select_no_match;
                    }
                    if(right_res != select_match_pseudo_class)
                    {
                        right_res |= res;
                    }
                }
                break;
            case combinator_adjacent_sibling:
                {
                    bool         is_pseudo = false;
                    element::ptr res       = el_parent->find_adjacent_sibling(shared_from_this(), *selector.m_left,
                                                                              apply_pseudo, &is_pseudo);
                    if(!res)
                    {
                        return select_no_match;
                    }
                    if(is_pseudo)
                    {
                        right_res |= select_match_pseudo_class;
                    }
                }
                break;
            case combinator_general_sibling:
                {
                    bool         is_pseudo = false;
                    element::ptr res =
                        el_parent->find_sibling(shared_from_this(), *selector.m_left, apply_pseudo, &is_pseudo);
                    if(!res)
                    {
                        return select_no_match;
                    }
                    if(is_pseudo)
                    {
                        right_res |= select_match_pseudo_class;
                    }
                }
                break;
            default:
                right_res = select_no_match;
            }
        }
        return right_res;
    }

    int litehtml::html_tag::select(const css_element_selector& selector, bool apply_pseudo)
    {
        if(selector.m_tag != star_id && selector.m_tag != m_tag)
        {
            return select_no_match;
        }

        int res = select_match;

        for(const auto& attr : selector.m_attrs)
        {
            switch(attr.type)
            {
            case select_class:
                if(!(attr.name in m_classes))
                {
                    return select_no_match;
                }
                break;
            case select_id:
                if(attr.name != m_id)
                {
                    return select_no_match;
                }
                break;
            case select_pseudo_element:
                if(attr.name == _after_)
                {
                    if(selector.m_attrs.size() == 1 && selector.m_tag == star_id && m_tag != __tag_after_)
                    {
                        return select_no_match;
                    }
                    res |= select_match_with_after;
                } else if(attr.name == _before_)
                {
                    if(selector.m_attrs.size() == 1 && selector.m_tag == star_id && m_tag != __tag_before_)
                    {
                        return select_no_match;
                    }
                    res |= select_match_with_before;
                } else
                {
                    return select_no_match;
                }
                break;
            case select_pseudo_class:
                if(apply_pseudo)
                {
                    if(select_pseudoclass(attr) == select_no_match)
                    {
                        return select_no_match;
                    }
                } else
                {
                    res |= select_match_pseudo_class;
                }
                break;
            default:
                if(select_attribute(attr) == select_no_match)
                {
                    return select_no_match;
                }
            }
        }
        return res;
    }

    int html_tag::select_pseudoclass(const css_attribute_selector& sel)
    {
        element::ptr el_parent = parent();

        switch(sel.name)
        {
        case _only_child_:
            if(!el_parent || !el_parent->is_only_child(shared_from_this(), false))
            {
                return select_no_match;
            }
            break;
        case _only_of_type_:
            if(!el_parent || !el_parent->is_only_child(shared_from_this(), true))
            {
                return select_no_match;
            }
            break;
        case _first_child_:
            if(!el_parent || !el_parent->is_nth_child(shared_from_this(), 0, 1, false))
            {
                return select_no_match;
            }
            break;
        case _first_of_type_:
            if(!el_parent || !el_parent->is_nth_child(shared_from_this(), 0, 1, true))
            {
                return select_no_match;
            }
            break;
        case _last_child_:
            if(!el_parent || !el_parent->is_nth_last_child(shared_from_this(), 0, 1, false))
            {
                return select_no_match;
            }
            break;
        case _last_of_type_:
            if(!el_parent || !el_parent->is_nth_last_child(shared_from_this(), 0, 1, true))
            {
                return select_no_match;
            }
            break;
        case _nth_child_:
        case _nth_of_type_:
        case _nth_last_child_:
        case _nth_last_of_type_:
            {
                if(!el_parent)
                {
                    return select_no_match;
                }

                int num = sel.a;
                int off = sel.b;
                if(!num && !off)
                {
                    return select_no_match;
                }

                switch(sel.name)
                {
                case _nth_child_:
                    if(!el_parent->is_nth_child(shared_from_this(), num, off, false, sel.selector_list))
                    {
                        return select_no_match;
                    }
                    break;
                case _nth_of_type_:
                    if(!el_parent->is_nth_child(shared_from_this(), num, off, true))
                    {
                        return select_no_match;
                    }
                    break;
                case _nth_last_child_:
                    if(!el_parent->is_nth_last_child(shared_from_this(), num, off, false, sel.selector_list))
                    {
                        return select_no_match;
                    }
                    break;
                case _nth_last_of_type_:
                    if(!el_parent->is_nth_last_child(shared_from_this(), num, off, true))
                    {
                        return select_no_match;
                    }
                    break;
                default:
                    break;
                }
            }
            break;
        case _is_:
        case _where_: // :where() matches like :is(), it only differs in specificity
            if(!select(sel.selector_list, true))
            {
                return select_no_match;
            }
            break;
        case _not_:
            if(select(sel.selector_list, true))
            {
                return select_no_match;
            }
            break;
        case _lang_:
            {
                // The nearest lang attribute decides, as a language range: "ru" matches "ru" and "ru-RU".
                const char* lang = nullptr;
                for(element::ptr el = shared_from_this(); el && !lang; el = el->parent())
                    lang = el->get_attr("lang");
                if(!lang)
                {
                    if(!get_document()->match_lang(sel.value))
                        return select_no_match;
                    break;
                }
                const std::string tag = lowcase(lang), range = lowcase(sel.value);
                if(range.empty() || (tag != range && tag.compare(0, range.size() + 1, range + "-") != 0))
                    return select_no_match;
            }
            break;
        case _empty_:
            // Whitespace is content, as in browsers; comments and generated ::before/::after are not.
            for(const auto& child : m_children)
            {
                if(!child->is_comment() && child->tag() != __tag_before_ && child->tag() != __tag_after_)
                    return select_no_match;
            }
            break;
        case _checked_:
            if(!(m_tag == _input_ && get_attr("checked")) && !(m_tag == _option_ && get_attr("selected")))
            {
                return select_no_match;
            }
            break;
        case _disabled_:
        case _enabled_:
            if(!is_one_of(m_tag, _input_, _button_, _select_, _textarea_, _option_, _optgroup_, _fieldset_) ||
               (get_attr("disabled") != nullptr) != (sel.name == _disabled_))
            {
                return select_no_match;
            }
            break;
        default:
            if(!(sel.name in m_pseudo_classes))
            {
                return select_no_match;
            }
            break;
        }
        return select_match;
    }

    // https://www.w3.org/TR/selectors-4/#attribute-selectors
    int html_tag::select_attribute(const css_attribute_selector& sel) const
    {
        const char* sz_attr_value = get_attr(_s(sel.name).c_str());

        if(!sz_attr_value)
        {
            return select_no_match;
        }

        std::string attr_value = sel.caseless_match ? lowcase(sz_attr_value) : sz_attr_value;

        switch(sel.matcher)
        {
        case attribute_exists:
            return select_match;

        case attribute_equals:
            if(attr_value == sel.value)
            {
                return select_match;
            }
            break;

        case attribute_contains_string: // *=
            if(sel.value != "" && contains(attr_value, sel.value))
            {
                return select_match;
            }
            break;

        // Attribute value is a whitespace-separated list of words, one of which is exactly sel.value
        case attribute_contains_word: // ~=
            if(sel.value != "" && contains(split_string(attr_value), sel.value))
            {
                return select_match;
            }
            break;

        case attribute_starts_with_string: // ^=
            if(sel.value != "" && match(attr_value, 0, sel.value))
            {
                return select_match;
            }
            break;

        // Attribute value is either equals sel.value or begins with sel.value immediately followed by "-".
        case attribute_starts_with_string_hyphen: // |=
            // Note: no special treatment for sel.value == ""
            if(attr_value == sel.value || match(attr_value, 0, sel.value + '-'))
            {
                return select_match;
            }
            break;

        case attribute_ends_with_string: // $=
            if(sel.value != "" && match(attr_value, -static_cast<int>(sel.value.size()), sel.value))
            {
                return select_match;
            }
            break;
        }
        return select_no_match;
    }

    element::ptr html_tag::find_ancestor(const css_selector& selector, bool apply_pseudo, bool* is_pseudo)
    {
        element::ptr el_parent = parent();
        if(!el_parent)
        {
            return nullptr;
        }
        int res = el_parent->select(selector, apply_pseudo);
        if(res != select_no_match)
        {
            if(is_pseudo)
            {
                if(res & select_match_pseudo_class)
                {
                    *is_pseudo = true;
                } else
                {
                    *is_pseudo = false;
                }
            }
            return el_parent;
        }
        if(prune_enabled() && selector.m_left && selector.m_combinator == combinator_descendant &&
           el_parent->select(selector.m_right, apply_pseudo) != select_no_match)
        {
            return nullptr;
        }
        return el_parent->find_ancestor(selector, apply_pseudo, is_pseudo);
    }

    void litehtml::html_tag::parse_attributes()
    {
        static const std::set<std::string>& svg_text_tags = *new std::set<std::string>{"svg", "g", "text", "tspan", "textpath", "a"};
        bool                               svg_context   = false;
        for(element::ptr cur = parent(); cur && !svg_context; cur = cur->parent())
            svg_context = lowcase(cur->get_tagName()) == "svg";
        std::string tag = lowcase(_s(m_tag));
        if((tag == "svg" || svg_context) && svg_text_tags.count(tag))
        {
            for(const char* name : {"font-family", "font-weight", "font-style", "font-size"})
            {
                const char* value = get_attr(name);
                if(!value || !*value)
                    continue;
                std::string text = value;
                if(std::string(name) == "font-size" && text.find_first_not_of("0123456789.") == std::string::npos)
                    text += "px";
                m_style.add(std::string(name) + ":" + text, "", nullptr);
            }
        }
        for(auto& el : m_children)
        {
            el->parse_attributes();
        }
    }

    void litehtml::html_tag::get_text(std::string& text) const
    {
        for(auto& el : m_children)
        {
            el->get_text(text);
        }
    }

    bool litehtml::html_tag::is_body() const
    {
        return false;
    }

    void litehtml::html_tag::set_data(const char* /*data*/) {}

    bool litehtml::html_tag::on_mouse_over()
    {
        bool ret = false;

        element::ptr el = shared_from_this();
        while(el)
        {
            if(el->set_pseudo_class(_hover_, true))
            {
                ret = true;
            }
            el = el->parent();
        }

        return ret;
    }

    bool litehtml::html_tag::on_mouse_leave()
    {
        bool ret = false;

        element::ptr el = shared_from_this();
        while(el)
        {
            if(el->set_pseudo_class(_hover_, false))
            {
                ret = true;
            }
            if(el->set_pseudo_class(_active_, false))
            {
                ret = true;
            }
            el = el->parent();
        }

        return ret;
    }

    bool litehtml::html_tag::on_lbutton_down()
    {
        bool ret = false;

        element::ptr el = shared_from_this();
        while(el)
        {
            if(el->set_pseudo_class(_active_, true))
            {
                ret = true;
            }
            el = el->parent();
        }

        return ret;
    }

    bool litehtml::html_tag::on_lbutton_up(bool is_click)
    {
        bool ret = false;

        element::ptr el = shared_from_this();
        while(el)
        {
            if(el->set_pseudo_class(_active_, false))
            {
                ret = true;
            }
            el = el->parent();
        }

        if(is_click)
        {
            on_click();
        }

        return ret;
    }

    void litehtml::html_tag::on_click()
    {
        if(!is_root())
        {
            if(!get_document()->container()->on_element_click(shared_from_this()))
            {
                element::ptr el_parent = parent();
                if(el_parent)
                {
                    el_parent->on_click();
                }
            }
        }
    }

    bool litehtml::html_tag::is_break() const
    {
        return false;
    }

    static shadow_paint shadow_paint_for(const box_shadow& shadow)
    {
        shadow_paint paint;
        paint.x      = pixel_t(shadow.x.val());
        paint.y      = pixel_t(shadow.y.val());
        paint.blur   = pixel_t(std::max(0.f, shadow.blur.val()));
        paint.spread = pixel_t(shadow.spread.val());
        paint.color  = shadow.color;
        paint.inset  = shadow.inset;
        return paint;
    }

    void litehtml::html_tag::draw_background(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                             const std::shared_ptr<render_item>& ri)
    {
        const auto& skipped_images = get_document()->skip_background_images;
        const bool  skip_images    = !skipped_images.empty() && skipped_images.count(this);
        const css_border& outline_style = m_css.get_outline();
        if(!get_background() && !m_css.get_borders().is_visible() && m_css.get_box_shadows().empty() &&
           !(outline_style.width.val() > 0 && outline_style.style != border_style_none))
        {
            return;
        }
        pixel_t inline_reach = 0;
        for(const auto& shadow : m_css.get_box_shadows())
        {
            if(!shadow.inset)
            {
                inline_reach = std::max(inline_reach, pixel_t(std::fabs(shadow.blur.val()) * 2 + std::max(0.f, shadow.spread.val()) +
                                                              std::max(std::fabs(shadow.x.val()), std::fabs(shadow.y.val()))));
            }
        }
        auto inline_processed = ri->for_inline_boxes([&](const position& box_, bool first, bool last) {
            const background* bg = get_background();

            position content_box;
            position box  = box_;
            box.x        += x;
            box.y        += y;
            position reach_box  = box;
            reach_box.x        -= inline_reach;
            reach_box.y        -= inline_reach;
            reach_box.width    += inline_reach * 2;
            reach_box.height   += inline_reach * 2;

            if(reach_box.does_intersect(clip))
            {
                content_box  = box;
                content_box -= ri->get_borders();
                content_box -= ri->get_paddings();

                css_borders bdr;

                // set left borders radius for the first box
                if(first)
                {
                    bdr.radius.bottom_left_x = m_css.get_borders().radius.bottom_left_x;
                    bdr.radius.bottom_left_y = m_css.get_borders().radius.bottom_left_y;
                    bdr.radius.top_left_x    = m_css.get_borders().radius.top_left_x;
                    bdr.radius.top_left_y    = m_css.get_borders().radius.top_left_y;
                }

                // set right borders radius for the last box
                if(last)
                {
                    bdr.radius.bottom_right_x = m_css.get_borders().radius.bottom_right_x;
                    bdr.radius.bottom_right_y = m_css.get_borders().radius.bottom_right_y;
                    bdr.radius.top_right_x    = m_css.get_borders().radius.top_right_x;
                    bdr.radius.top_right_y    = m_css.get_borders().radius.top_right_y;
                }

                bdr.top    = m_css.get_borders().top;
                bdr.bottom = m_css.get_borders().bottom;
                if(first)
                {
                    bdr.left = m_css.get_borders().left;
                }
                if(last)
                {
                    bdr.right = m_css.get_borders().right;
                }

                const auto& inline_shadows = m_css.get_box_shadows();
                position    shadow_box     = box;
                shadow_box.round();
                const border_radiuses shadow_radius = bdr.radius.calc_percents(box.width, box.height);
                for(auto it = inline_shadows.rbegin(); it != inline_shadows.rend(); ++it)
                {
                    if(!it->inset)
                    {
                        doc_ptr()->container()->draw_box_shadow(hdc, shadow_box, shadow_radius, shadow_paint_for(*it));
                    }
                }
                if(bg)
                {
                    int num_layers = bg->get_layers_number();
                    for(int i = num_layers - 1; i >= 0; i--)
                    {
                        background_layer layer;

                        if(!bg->get_layer(i, content_box, this, ri, layer))
                        {
                            continue;
                        }

                        layer.border_radius = bdr.radius.calc_percents(box.width, box.height);

                        layer.border_box.round();
                        layer.clip_box.round();
                        layer.origin_box.round();

                        if(skip_images && bg->get_layer_type(i) != background::type_color)
                        {
                            continue;
                        }
                        bg->draw_layer(hdc, i, layer, doc_ptr()->container());
                    }
                }
                for(auto it = inline_shadows.rbegin(); it != inline_shadows.rend(); ++it)
                {
                    if(it->inset)
                    {
                        doc_ptr()->container()->draw_box_shadow(hdc, shadow_box, shadow_radius, shadow_paint_for(*it));
                    }
                }
                if(bdr.is_visible())
                {
                    borders b = bdr;
                    b.radius  = bdr.radius.calc_percents(box.width, box.height);
                    box.round();
                    doc_ptr()->container()->draw_borders(hdc, b, box, false);
                }
            }
            return true;
        });

        if(!inline_processed)
        {
            position pos  = ri->pos();
            pos.x        += x;
            pos.y        += y;

            position border_box  = pos;
            border_box          += ri->get_paddings();
            border_box          += ri->get_borders();

            position ink = border_box;
            for(const auto& shadow : m_css.get_box_shadows())
            {
                if(!shadow.inset)
                {
                    pixel_t reach = pixel_t(std::fabs(shadow.blur.val()) + std::max(0.f, shadow.spread.val())) +
                                    pixel_t(std::max(std::fabs(shadow.x.val()), std::fabs(shadow.y.val())));
                    ink += margins{reach, reach, reach, reach};
                }
            }
            pixel_t outline_reach = pixel_t(m_css.get_outline().width.val()) + std::max(0_px, m_css.get_outline_offset());
            if(outline_reach > 0_px)
            {
                ink += margins{outline_reach, outline_reach, outline_reach, outline_reach};
            }

            if(ink.does_intersect(clip) || is_root())
            {
                auto v_offset  = ri->get_draw_vertical_offset();
                pos.y         += v_offset;
                pos.height    -= v_offset;

                position shadow_box = border_box;
                shadow_box.round();
                border_radiuses shadow_radius =
                    m_css.get_borders().radius.calc_percents(shadow_box.width, shadow_box.height);
                const auto& shadows = m_css.get_box_shadows();
                const bool bare = doc_ptr()->bare_shadows && doc_ptr()->bare_effects == this;
                for(auto it = shadows.rbegin(); it != shadows.rend(); ++it)
                {
                    if(!it->inset && !bare)
                    {
                        doc_ptr()->container()->draw_box_shadow(hdc, shadow_box, shadow_radius,
                                                                     shadow_paint_for(*it));
                    }
                }

                const background* bg = get_background();
                if(bg)
                {
                    int num_layers = bg->get_layers_number();
                    for(int i = num_layers - 1; i >= 0; i--)
                    {
                        background_layer layer;

                        if(!bg->get_layer(i, pos, this, ri, layer))
                        {
                            continue;
                        }

                        if(is_root() && (clip != nullptr))
                        {
                            layer.clip_box   = *clip;
                            layer.border_box = *clip;
                        }

                        layer.border_box.round();
                        layer.clip_box.round();
                        layer.origin_box.round();

                        if(skip_images && bg->get_layer_type(i) != background::type_color)
                        {
                            continue;
                        }
                        bg->draw_layer(hdc, i, layer, doc_ptr()->container());
                    }
                }

                if(!shadows.empty())
                {
                    position padding_box  = shadow_box;
                    padding_box          -= ri->get_borders();
                    border_radiuses inner = shadow_radius;
                    inner                -= ri->get_borders();
                    for(auto it = shadows.rbegin(); it != shadows.rend(); ++it)
                    {
                        if(it->inset)
                        {
                            doc_ptr()->container()->draw_box_shadow(hdc, padding_box, inner, shadow_paint_for(*it));
                        }
                    }
                }

                const bool collapsed_table = (m_css.get_display() == display_table || m_css.get_display() == display_inline_table) &&
                                             m_css.get_border_collapse() == border_collapse_collapse;
                if(!ri->defer_borders && !collapsed_table)
                {
                    ri->draw_borders(hdc, border_box);
                }

                const css_border& outline = m_css.get_outline();
                if(outline.width.val() > 0 && outline.style != border_style_none)
                {
                    pixel_t  grow        = m_css.get_outline_offset() + pixel_t(outline.width.val());
                    position outline_box = shadow_box;
                    outline_box += margins{grow, grow, grow, grow};
                    borders ob;
                    ob.left = ob.right = ob.top = ob.bottom = border(outline);
                    ob.radius = shadow_radius;
                    if(ob.radius.top_left_x > 0_px || ob.radius.top_right_x > 0_px || ob.radius.bottom_left_x > 0_px ||
                       ob.radius.bottom_right_x > 0_px)
                    {
                        ob.radius += margins{grow, grow, grow, grow};
                    }
                    doc_ptr()->container()->draw_borders(hdc, ob, outline_box, false);
                }
            }
        }
    }

    bool litehtml::html_tag::set_pseudo_class(string_id cls, bool add)
    {
        bool ret = false;
        if(add)
        {
            if(std::find(m_pseudo_classes.begin(), m_pseudo_classes.end(), cls) == m_pseudo_classes.end())
            {
                m_pseudo_classes.push_back(cls);
                ret = true;
            }
        } else
        {
            auto pi = std::find(m_pseudo_classes.begin(), m_pseudo_classes.end(), cls);
            if(pi != m_pseudo_classes.end())
            {
                m_pseudo_classes.erase(pi);
                ret = true;
            }
        }
        return ret;
    }

    bool litehtml::html_tag::set_class(const char* pclass, bool add)
    {
        string_vector classes;
        bool          changed = false;

        split_string(pclass, classes, " ");

        if(add)
        {
            for(auto& _class : classes)
            {
                if(std::find(m_str_classes.begin(), m_str_classes.end(), _class) == m_str_classes.end())
                {
                    m_str_classes.push_back(std::move(_class));
                    changed = true;
                }
            }
        } else
        {
            for(const auto& _class : classes)
            {
                auto end = std::remove(m_str_classes.begin(), m_str_classes.end(), _class);

                if(end != m_str_classes.end())
                {
                    m_str_classes.erase(end, m_str_classes.end());
                    changed = true;
                }
            }
        }

        if(changed)
        {
            std::string class_string;
            join_string(class_string, m_str_classes, " ");
            set_attr("class", class_string.c_str());

            return true;
        }
        return false;
    }

    bool litehtml::html_tag::is_replaced() const
    {
        return false;
    }

    void litehtml::html_tag::draw_list_marker(uint_ptr hdc, const position& pos, const std::shared_ptr<render_item>& ri)
    {
        list_marker lm;

        size img_size;
        if(css().get_list_style_image() != "")
        {
            lm.image   = css().get_list_style_image();
            lm.baseurl = css().get_list_style_image_baseurl().c_str();
            get_document()->container()->get_image_size(lm.image.c_str(), lm.baseurl, img_size);
        } else
        {
            lm.baseurl = nullptr;
        }

        pixel_t ln_height = css().line_height().computed_value;
        pixel_t sz_font   = css().get_font_size();
        lm.pos.x          = pos.x;
        lm.pos.width      = sz_font / 3_px;
        lm.color          = css().get_color();
        lm.marker_type    = css().get_list_style_type();
        lm.font           = css().get_font();

        if(css().get_list_style_type() >= list_style_type_armenian)
        {
            auto li_baseline = pos.y + ri->get_first_baseline() - ri->content_offset_top();
            lm.pos.y         = li_baseline - css().get_font_metrics().ascent;
            lm.pos.height    = css().get_font_metrics().height;

            lm.index = atoi(get_attr("list_index", "0"));
        } else
        {
            lm.pos.height = sz_font / 3_px;
            lm.pos.y      = pos.y + ln_height / 2_px - lm.pos.height / 2_px;
            lm.index      = -1;
        }

        if(img_size.width != 0_px && img_size.height != 0_px)
        {
            if(lm.pos.y + img_size.height > pos.y + pos.height)
            {
                lm.pos.y = pos.y + pos.height - img_size.height;
            }
            if(img_size.width > lm.pos.width)
            {
                lm.pos.x -= img_size.width - lm.pos.width;
            }

            lm.pos.width  = img_size.width;
            lm.pos.height = img_size.height;
        }

        if(m_css.get_list_style_position() == list_style_position_outside)
        {
            if(m_css.get_list_style_type() >= list_style_type_armenian)
            {
                if(lm.font)
                {
                    auto tw_space = get_document()->container()->text_width(" ", lm.font);
                    lm.pos.x      = pos.x - tw_space * 2_px;
                    lm.pos.width  = tw_space;
                } else
                {
                    lm.pos.width = 0;
                }
            } else
            {
                lm.pos.x -= sz_font;
            }
        }

        if(m_css.get_list_style_type() >= list_style_type_armenian)
        {
            auto marker_text = get_list_marker_text(lm.index);
            if(marker_text.empty())
            {
                get_document()->container()->draw_list_marker(hdc, lm);
            } else
            {
                if(lm.font)
                {
                    marker_text   += ".";
                    auto tw        = get_document()->container()->text_width(marker_text.c_str(), lm.font);
                    auto text_pos  = lm.pos;
                    text_pos.move_to(text_pos.right() - tw, text_pos.y);
                    text_pos.width = tw;
                    text_pos.round();
                    get_document()->container()->draw_text(hdc, marker_text.c_str(), lm.font, lm.color, text_pos);
                }
            }
        } else
        {
            get_document()->container()->draw_list_marker(hdc, lm);
        }
    }

    std::string litehtml::html_tag::get_list_marker_text(int index)
    {
        switch(m_css.get_list_style_type())
        {
        case litehtml::list_style_type_decimal:
            return std::to_string(index);
        case litehtml::list_style_type_decimal_leading_zero:
            {
                auto txt = std::to_string(index);
                if(txt.length() == 1)
                {
                    txt = "0" + txt;
                }
                return txt;
            }
        case litehtml::list_style_type_lower_latin:
        case litehtml::list_style_type_lower_alpha:
            return num_cvt::to_latin_lower(index);
        case litehtml::list_style_type_lower_greek:
            return num_cvt::to_greek_lower(index);
        case litehtml::list_style_type_upper_alpha:
        case litehtml::list_style_type_upper_latin:
            return num_cvt::to_latin_upper(index);
        case litehtml::list_style_type_lower_roman:
            return num_cvt::to_roman_lower(index);
        case litehtml::list_style_type_upper_roman:
            return num_cvt::to_roman_upper(index);
        default:
            return "";
            //	case litehtml::list_style_type_armenian:
            //	case litehtml::list_style_type_georgian:
            //	case litehtml::list_style_type_hebrew:
            //	case litehtml::list_style_type_hiragana:
            //	case litehtml::list_style_type_hiragana_iroha:
            //	case litehtml::list_style_type_katakana:
            //	case litehtml::list_style_type_katakana_iroha:
            //  case litehtml::list_style_type_none:
            //  case litehtml::list_style_type_circle:
            //  case litehtml::list_style_type_disc:
            //  case litehtml::list_style_type_square:
            //  case litehtml::list_style_type_cjk_ideographic:
            //      break;
        }
    }

    bool html_tag::is_nth_child(const element::ptr& el, int num, int off, bool of_type,
                                const css_selector::vector& selector_list) const
    {
        int idx = 1;
        for(const auto& child : m_children)
        {
            if(child->css().get_display() != display_inline_text)
            {
                if((!of_type && selector_list.empty()) || (of_type && child->tag() == el->tag()) ||
                   child->select(selector_list))
                {
                    if(el == child)
                    {
                        if(num != 0)
                        {
                            if((idx - off) * num >= 0 && (idx - off) % num == 0)
                            {
                                return true;
                            }

                        } else if(idx == off)
                        {
                            return true;
                        }
                        return false;
                    }
                    idx++;
                }
                if(el == child)
                {
                    break;
                }
            }
        }
        return false;
    }

    bool html_tag::is_nth_last_child(const element::ptr& el, int num, int off, bool of_type,
                                     const css_selector::vector& selector_list) const
    {
        int idx = 1;
        for(auto child = m_children.rbegin(); child != m_children.rend(); child++)
        {
            if((*child)->css().get_display() != display_inline_text)
            {
                if((!of_type && selector_list.empty()) || (of_type && (*child)->tag() == el->tag()) ||
                   (*child)->select(selector_list))
                {
                    if(el == (*child))
                    {
                        if(num != 0)
                        {
                            if((idx - off) * num >= 0 && (idx - off) % num == 0)
                            {
                                return true;
                            }

                        } else if(idx == off)
                        {
                            return true;
                        }
                        return false;
                    }
                    idx++;
                }
                if(el == (*child))
                {
                    break;
                }
            }
        }
        return false;
    }

    litehtml::element::ptr litehtml::html_tag::find_adjacent_sibling(const element::ptr& el,
                                                                     const css_selector& selector,
                                                                     bool                apply_pseudo /*= true*/,
                                                                     bool*               is_pseudo /*= 0*/)
    {
        static const bool indexed = getenv("LH_NO_SIBLING_INDEX") == nullptr;
        if(indexed && g_apply_depth > 0 && m_children.size() > 16)
        {
            auto& entry = g_sibling_indexes[this];
            if(entry.pass != g_apply_pass || entry.count != m_children.size())
            {
                entry.pass  = g_apply_pass;
                entry.count = m_children.size();
                entry.kids.clear();
                entry.index.clear();
                for(auto& e : m_children)
                {
                    if(e->css().get_display() != display_inline_text)
                    {
                        entry.index[e.get()] = entry.kids.size();
                        entry.kids.push_back(e.get());
                    }
                }
            }
            auto it = entry.index.find(el.get());
            if(it == entry.index.end() || it->second == 0)
                return nullptr;
            element::ptr prev = entry.kids[it->second - 1]->shared_from_this();
            int          res  = prev->select(selector, apply_pseudo);
            if(res == select_no_match)
                return nullptr;
            if(is_pseudo)
                *is_pseudo = (res & select_match_pseudo_class) != 0;
            return prev;
        }
        element::ptr ret;
        for(auto& e : m_children)
        {
            if(e->css().get_display() != display_inline_text)
            {
                if(e == el)
                {
                    if(ret)
                    {
                        int res = ret->select(selector, apply_pseudo);
                        if(res != select_no_match)
                        {
                            if(is_pseudo)
                            {
                                if(res & select_match_pseudo_class)
                                {
                                    *is_pseudo = true;
                                } else
                                {
                                    *is_pseudo = false;
                                }
                            }
                            return ret;
                        }
                    }
                    return nullptr;
                }
                ret = e;
            }
        }
        return nullptr;
    }

    litehtml::element::ptr litehtml::html_tag::find_sibling(const element::ptr& el, const css_selector& selector,
                                                            bool apply_pseudo /*= true*/, bool* is_pseudo /*= 0*/)
    {
        element::ptr ret = nullptr;
        for(auto& e : m_children)
        {
            if(e->css().get_display() != display_inline_text)
            {
                if(e == el)
                {
                    return ret;
                }
                if(!ret)
                {
                    int res = e->select(selector, apply_pseudo);
                    if(res == select_no_match && selector.m_left &&
                       (selector.m_combinator == combinator_descendant || selector.m_combinator == combinator_child) &&
                       prune_enabled() && e->select(selector.m_right, apply_pseudo) != select_no_match)
                    {
                        return nullptr;
                    }
                    if(res != select_no_match)
                    {
                        if(is_pseudo)
                        {
                            if(res & select_match_pseudo_class)
                            {
                                *is_pseudo = true;
                            } else
                            {
                                *is_pseudo = false;
                            }
                        }
                        ret = e;
                    }
                }
            }
        }
        return nullptr;
    }

    bool litehtml::html_tag::is_only_child(const element::ptr& el, bool of_type) const
    {
        int child_count = 0;
        for(const auto& child : m_children)
        {
            if(child->css().get_display() != display_inline_text)
            {
                if(!of_type || (of_type && el->tag() == child->tag()))
                {
                    child_count++;
                }
                if(child_count > 1)
                {
                    break;
                }
            }
        }
        return child_count <= 1;
    }

    litehtml::element::ptr litehtml::html_tag::get_element_before(const style& style, bool create)
    {
        if(!m_children.empty())
        {
            if(m_children.front()->tag() == __tag_before_)
            {
                return m_children.front();
            }
        }
        if(create)
        {
            return add_pseudo_before(style);
        }
        return nullptr;
    }

    litehtml::element::ptr litehtml::html_tag::get_element_after(const style& style, bool create)
    {
        if(!m_children.empty())
        {
            if(m_children.back()->tag() == __tag_after_)
            {
                return m_children.back();
            }
        }
        if(create)
        {
            return add_pseudo_after(style);
        }
        return nullptr;
    }

    void litehtml::html_tag::handle_counter_properties()
    {
        const auto& reset_property = m_style.get_property(_counter_reset_);
        if(reset_property.is<string_vector>())
        {
            auto reset_function = [&](const string_id& name_id, const int value) { reset_counter(name_id, value); };
            parse_counter_tokens(reset_property.get<string_vector>(), 0, reset_function);
            return;
        }

        const auto& inc_property = m_style.get_property(_counter_increment_);
        if(inc_property.is<string_vector>())
        {
            auto inc_function = [&](const string_id& name_id, const int value) { increment_counter(name_id, value); };
            parse_counter_tokens(inc_property.get<string_vector>(), 1, inc_function);
            return;
        }
    }

    void litehtml::html_tag::add_style(const style& style)
    {
        m_style.combine(style);
        handle_counter_properties();
    }

    void litehtml::html_tag::restore_base_style()
    {
        if(m_has_style_base)
        {
            m_style          = m_style_base;
            m_has_style_base = false;
        }
    }

    void litehtml::html_tag::reset_attributes(const string_map& attrs)
    {
        m_attrs.clear();
        m_str_classes.clear();
        m_classes.clear();
        m_id = empty_id;
        for(const auto& attr : attrs)
        {
            set_attr(attr.first.c_str(), attr.second.c_str());
        }
    }

    void litehtml::html_tag::forget_matched_styles()
    {
        for(auto it = m_children.begin(); it != m_children.end();)
        {
            if((*it)->tag() == __tag_before_ || (*it)->tag() == __tag_after_)
            {
                (*it)->parent(nullptr);
                it = m_children.erase(it);
            } else
            {
                ++it;
            }
        }
        m_style.clear();
        m_style_base     = style();
        m_has_style_base = false;
        m_used_styles.clear();
    }

    void litehtml::html_tag::refresh_styles()
    {
        m_has_style_base = false;
        for(auto& el : m_children)
        {
            if(el->css().get_display() != display_inline_text)
            {
                el->refresh_styles();
            }
        }

        m_style.clear();

        for(auto& usel : m_used_styles)
        {
            usel->m_used = false;

            if(usel->m_selector->is_media_valid())
            {
                int apply = select(*usel->m_selector, false);

                if(apply != select_no_match)
                {
                    if(apply & select_match_pseudo_class)
                    {
                        if(select(*usel->m_selector, true))
                        {
                            if(apply & select_match_with_after)
                            {
                                element::ptr el = get_element_after(*usel->m_selector->m_style, false);
                                if(el)
                                {
                                    el->add_style(*usel->m_selector->m_style);
                                }
                            } else if(apply & select_match_with_before)
                            {
                                element::ptr el = get_element_before(*usel->m_selector->m_style, false);
                                if(el)
                                {
                                    el->add_style(*usel->m_selector->m_style);
                                }
                            } else
                            {
                                add_style(*usel->m_selector->m_style);
                                usel->m_used = true;
                            }
                        }
                    } else if(apply & select_match_with_after)
                    {
                        element::ptr el = get_element_after(*usel->m_selector->m_style, false);
                        if(el)
                        {
                            el->add_style(*usel->m_selector->m_style);
                        }
                    } else if(apply & select_match_with_before)
                    {
                        element::ptr el = get_element_before(*usel->m_selector->m_style, false);
                        if(el)
                        {
                            el->add_style(*usel->m_selector->m_style);
                        }
                    } else
                    {
                        add_style(*usel->m_selector->m_style);
                        usel->m_used = true;
                    }
                }
            }
        }
    }

    const litehtml::background* litehtml::html_tag::get_background(bool own_only)
    {
        if(own_only)
        {
            // return own background with check for empty one
            if(m_css.get_bg().is_empty())
            {
                return nullptr;
            }
            return &m_css.get_bg();
        }

        if(m_css.get_bg().is_empty())
        {
            // if this is root element (<html>) try to get background from body
            if(is_root())
            {
                for(const auto& el : m_children)
                {
                    if(el->is_body())
                    {
                        // return own body background
                        return el->get_background(true);
                    }
                }
            }
            return nullptr;
        }

        if(is_body())
        {
            element::ptr el_parent = parent();
            if(el_parent)
            {
                if(!el_parent->get_background(true))
                {
                    // parent of body will draw background for body
                    return nullptr;
                }
            }
        }

        return &m_css.get_bg();
    }

    std::string html_tag::dump_get_name()
    {
        if(m_tag == empty_id)
        {
            return "anon [html_tag]";
        }
        return _s(m_tag) + " [html_tag]";
    }

    // https://html.spec.whatwg.org/multipage/rendering.html#maps-to-the-pixel-length-property
    void html_tag::map_to_pixel_length_property(string_id prop_name, const std::string& attr_value)
    {
        int n;
        if(html_parse_non_negative_integer(attr_value, n))
        {
            css_token tok(DIMENSION, static_cast<float>(n), css_number_integer, "px");
            m_style.add_property(prop_name, {tok});
        }
    }

    // https://html.spec.whatwg.org/multipage/rendering.html#tables-2:attr-table-border
    void html_tag::map_to_pixel_length_property_with_default_value(string_id prop_name, const std::string& attr_value,
                                                                   int default_value)
    {
        int n = default_value;
        html_parse_non_negative_integer(attr_value, n);
        css_token tok(DIMENSION, static_cast<float>(n), css_number_integer, "px");
        m_style.add_property(prop_name, {tok});
    }

    // https://html.spec.whatwg.org/multipage/rendering.html#maps-to-the-dimension-property
    void html_tag::map_to_dimension_property(string_id prop_name, const std::string& attr_value)
    {
        float               x    = 0;
        html_dimension_type type = html_length;
        if(!html_parse_dimension_value(attr_value, x, type))
        {
            return;
        }

        css_token tok;
        if(type == html_length)
        {
            tok = {DIMENSION, x, css_number_number, "px"};
        } else
        {
            tok = {PERCENTAGE, x, css_number_number};
        }

        m_style.add_property(prop_name, {tok});
    }

    // https://html.spec.whatwg.org/multipage/rendering.html#maps-to-the-dimension-property-(ignoring-zero)
    void html_tag::map_to_dimension_property_ignoring_zero(string_id prop_name, const std::string& attr_value)
    {
        float               x    = 0;
        html_dimension_type type = html_length;
        if(!html_parse_nonzero_dimension_value(attr_value, x, type))
        {
            return;
        }

        css_token tok;
        if(type == html_length)
        {
            tok = {DIMENSION, x, css_number_number, "px"};
        } else
        {
            tok = {PERCENTAGE, x, css_number_number};
        }

        m_style.add_property(prop_name, {tok});
    }

} // namespace litehtml
