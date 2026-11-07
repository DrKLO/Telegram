#include <algorithm>
#include <cstdint>

#include "html.h"
#include "document.h"
#include "document_container.h"
#include "html_microsyntaxes.h"
#include "html_tag.h"
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

    void html_tag::remove_attr(const char* _name)
    {
        if(!_name)
        {
            return;
        }
        std::string name = lowcase(_name);
        m_attrs.erase(name);
        if(name == "class")
        {
            m_str_classes.clear();
            m_classes.clear();
        } else if(name == "id")
        {
            m_id = empty_id;
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

    void litehtml::html_tag::apply_stylesheet(const litehtml::css& stylesheet)
    {
        for(const auto& sel : stylesheet.selectors())
        {
            // optimization
            {
                const auto& r = sel->m_right;
                if(r.m_tag != star_id && r.m_tag != m_tag)
                {
                    continue;
                }

                if(!r.m_attrs.empty())
                {
                    const auto& attr = r.m_attrs[0];
                    if(attr.type == select_class && !(attr.name in m_classes))
                    {
                        continue;
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

        for(auto& el : m_children)
        {
            if(el->css().get_display() != display_inline_text)
            {
                el->apply_stylesheet(stylesheet);
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
            if(m_css.get_overflow() > overflow_visible)
            {
                position border_box  = pos;
                border_box          += ri->get_paddings();
                border_box          += ri->get_borders();

                border_radiuses bdr_radius =
                    m_css.get_borders().radius.calc_percents(border_box.width, border_box.height);

                bdr_radius -= ri->get_borders();
                bdr_radius -= ri->get_paddings();

                get_document()->container()->set_clip(pos, bdr_radius);
            }

            draw_list_marker(hdc, pos, ri);

            if(m_css.get_overflow() > overflow_visible)
            {
                get_document()->container()->del_clip();
            }
        }
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

    void litehtml::html_tag::compute_styles(bool recursive)
    {
        const char*   style = get_attr("style");
        document::ptr doc   = get_document();

        if(style)
        {
            m_style.add(style, "", doc->container());
        }

        m_style.subst_vars(this);

        m_css.compute(this, doc);

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
            if(!get_document()->match_lang(sel.value))
            {
                return select_no_match;
            }
            break;
        case _checked_:
            // Form state: matches either the dynamic pseudo-class (set_pseudo_class)
            // or the standard HTML checked attribute.
            if(!(sel.name in m_pseudo_classes) && !get_attr("checked"))
            {
                return select_no_match;
            }
            break;
        case _disabled_:
            if(!(sel.name in m_pseudo_classes) && !get_attr("disabled"))
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
        return el_parent->find_ancestor(selector, apply_pseudo, is_pseudo);
    }

    void litehtml::html_tag::parse_attributes()
    {
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

    void litehtml::html_tag::draw_background(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                                             const std::shared_ptr<render_item>& ri)
    {
        auto inline_processed = ri->for_inline_boxes([&](const position& box_, bool first, bool last) {
            const background* bg = get_background();

            position content_box;
            position box  = box_;
            box.x        += x;
            box.y        += y;

            if(box.does_intersect(clip))
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

                        bg->draw_layer(hdc, i, layer, get_document()->container());
                    }
                }
                if(bdr.is_visible())
                {
                    borders b = bdr;
                    b.radius  = bdr.radius.calc_percents(box.width, box.height);
                    box.round();
                    get_document()->container()->draw_borders(hdc, b, box, false);
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

            if(border_box.does_intersect(clip) || is_root())
            {
                auto v_offset  = ri->get_draw_vertical_offset();
                pos.y         += v_offset;
                pos.height    -= v_offset;

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

                        bg->draw_layer(hdc, i, layer, get_document()->container());
                    }
                }

                borders bdr = m_css.get_borders();
                if(bdr.is_visible())
                {
                    border_box.round();
                    bdr.radius = m_css.get_borders().radius.calc_percents(border_box.width, border_box.height);
                    get_document()->container()->draw_borders(hdc, bdr, border_box, is_root());
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

    void litehtml::html_tag::refresh_styles()
    {
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
