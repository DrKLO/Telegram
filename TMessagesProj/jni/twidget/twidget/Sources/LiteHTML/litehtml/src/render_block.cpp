#include "render_block.h"

#include "document.h"
#include "document_container.h"
#include "formatting_context.h"
#include "html_tag.h"
#include "render_block_context.h"
#include "render_inline_context.h"
#include "types.h"
#include <algorithm>
#include <climits>

litehtml::rendered_width litehtml::render_item_block::place_float(const std::shared_ptr<render_item>& el, pixel_t top,
                                                                  const containing_block_context& self_size,
                                                                  formatting_context*             fmt_ctx)
{
    pixel_t line_top  = fmt_ctx->get_cleared_top(el, top);
    pixel_t line_left = 0_px;

    auto nv = el->render(line_left, line_top, self_size.new_width(self_size.render_width), fmt_ctx);
    if(nv.natural_width < el->width() && el->src_el()->css().get_width().is_predefined())
    {
        el->render(line_left, line_top, self_size.new_width(nv.natural_width), fmt_ctx);
    }

    formatting_context::new_position new_pos;
    formatting_context::el_position  el_pos;
    el_pos.el_pos           = el->pos();
    el_pos.el_pos          += el->get_margins();
    el_pos.el_pos          += el->get_paddings();
    el_pos.el_pos          += el->get_borders();
    el_pos.container_width  = self_size.render_width;
    if(el->src_el()->css().get_float() == float_left)
    {
        new_pos = fmt_ctx->place_to_left(el_pos);
    } else if(el->src_el()->css().get_float() == float_right)
    {
        el->pos().x     = self_size.render_width.value - el_pos.el_pos.width + el->content_offset_left();
        el_pos.el_pos.x = self_size.render_width.value - el_pos.el_pos.width;
        new_pos         = fmt_ctx->place_to_right(el_pos);
    }
    nv.min_width = nv.natural_width;
    if(new_pos.found)
    {
        el->pos().x = new_pos.left + el->content_offset_left();
        el->pos().y = new_pos.top + el->content_offset_top();
    }
    nv.natural_width = self_size.render_width.value - new_pos.width + nv.natural_width;
    fmt_ctx->add_float(el, nv.min_width, self_size.context_idx);
    fix_line_width(el->src_el()->css().get_float(), self_size, fmt_ctx);

    return nv;
}

std::shared_ptr<litehtml::render_item> litehtml::render_item_block::init()
{
    std::shared_ptr<render_item> ret;

    // Initialize indexes for list items
    if(src_el()->css().get_display() == display_list_item &&
       src_el()->css().get_list_style_type() >= list_style_type_armenian)
    {
        if(auto p = src_el()->parent())
        {
            int val = atoi(p->get_attr("start", "1"));
            for(const auto& child : p->children())
            {
                if(child == src_el())
                {
                    src_el()->set_attr("list_index", std::to_string(val).c_str());
                    break;
                }
                if(child->css().get_display() == display_list_item)
                {
                    val++;
                }
            }
        }
    }
    // Split inline blocks with box blocks inside
    auto iter = m_children.begin();
    while(iter != m_children.end())
    {
        const auto& el = *iter;
        if(el->src_el()->css().get_display() == display_inline && !el->children().empty())
        {
            auto split_el = el->split_inlines();
            if(std::get<0>(split_el))
            {
                iter = m_children.erase(iter);
                iter = m_children.insert(iter, std::get<2>(split_el));
                iter = m_children.insert(iter, std::get<1>(split_el));
                iter = m_children.insert(iter, std::get<0>(split_el));

                std::get<0>(split_el)->parent(shared_from_this());
                std::get<1>(split_el)->parent(shared_from_this());
                std::get<2>(split_el)->parent(shared_from_this());
                continue;
            }
        }
        ++iter;
    }

    bool has_block_level = false;
    bool has_inlines     = false;
    for(const auto& el : m_children)
    {
        if(!el->src_el()->is_float())
        {
            if(el->src_el()->is_block_box())
            {
                has_block_level = true;
            } else if(el->src_el()->is_inline())
            {
                has_inlines = true;
            }
        }
        if(has_block_level && has_inlines)
        {
            break;
        }
    }
    if(has_block_level)
    {
        ret = std::make_shared<render_item_block_context>(src_el());
        ret->parent(parent());

        auto                 doc = src_el()->get_document();
        decltype(m_children) new_children;
        decltype(m_children) inlines;
        bool                 not_ws_added = false;
        for(const auto& el : m_children)
        {
            if(el->src_el()->is_inline())
            {
                inlines.push_back(el);
                if(!el->src_el()->is_white_space())
                {
                    not_ws_added = true;
                }
            } else
            {
                if(not_ws_added)
                {
                    auto anon_el = std::make_shared<html_tag>(src_el());
                    auto anon_ri = std::make_shared<render_item_block>(anon_el);
                    for(const auto& inl : inlines)
                    {
                        anon_ri->add_child(inl);
                    }

                    not_ws_added = false;
                    new_children.push_back(anon_ri);
                    anon_ri->parent(ret);
                }
                new_children.push_back(el);
                el->parent(ret);
                inlines.clear();
            }
        }
        if(!inlines.empty() && not_ws_added)
        {
            auto anon_el = std::make_shared<html_tag>(src_el());
            auto anon_ri = std::make_shared<render_item_block>(anon_el);
            for(const auto& inl : inlines)
            {
                anon_ri->add_child(inl);
            }

            new_children.push_back(anon_ri);
            anon_ri->parent(ret);
        }
        ret->children() = new_children;
    }

    if(!ret)
    {
        ret = std::make_shared<render_item_inline_context>(src_el());
        ret->parent(parent());
        ret->children() = children();
        for(const auto& el : ret->children())
        {
            el->parent(ret);
        }
    }

    ret->src_el()->add_render(ret);

    for(auto& el : ret->children())
    {
        el = el->init();
    }

    return ret;
}

litehtml::rendered_width litehtml::render_item_block::_render(pixel_t x, pixel_t y,
                                                              const containing_block_context& containing_block_size,
                                                              formatting_context* fmt_ctx, bool second_pass)
{
    containing_block_context self_size = calculate_containing_block_context(containing_block_size);
    if(css().get_aspect_ratio() > 0 && self_size.height.type == containing_block_context::cbc_value_type_auto &&
       (containing_block_size.size_mode & containing_block_context::size_mode_content) == 0)
    {
        // From the width it gets: max-width and min-width apply first.
        pixel_t ratio_width = self_size.render_width;
        if(self_size.max_width.type != containing_block_context::cbc_value_type_none)
        {
            ratio_width = std::min<pixel_t>(ratio_width, self_size.max_width);
        }
        if(self_size.min_width.type != containing_block_context::cbc_value_type_none)
        {
            ratio_width = std::max<pixel_t>(ratio_width, self_size.min_width);
        }
        pixel_t ratio_height =
            (ratio_width + box_sizing_width()) / pixel_t(css().get_aspect_ratio()) - box_sizing_height();
        if(self_size.min_height.type == containing_block_context::cbc_value_type_none ||
           self_size.min_height.value < ratio_height)
        {
            self_size.min_height = containing_block_context::typed_pixel(
                ratio_height, containing_block_context::cbc_value_type_absolute);
        }
    }

    //*****************************************
    // Render content
    //*****************************************
    auto [ret_width, ret_min_width] = _render_content(x, y, second_pass, self_size, fmt_ctx);
    //*****************************************
    const bool size_contained = css().contains(contain_size);
    if(size_contained || css().contains(contain_inline_size))
    {
        ret_width     = 0_px;
        ret_min_width = 0_px;
    }

    if(src_el()->css().get_display() == display_list_item)
    {
        if(m_pos.height == 0_px)
        {
            m_pos.height = css().line_height().computed_value;
        }
    }

    bool requires_rerender = false; // when true, the second pass for content rendering is required

    // Set block width
    if((containing_block_size.size_mode & containing_block_context::size_mode_content) == 0)
    {
        if(self_size.width.type == containing_block_context::cbc_value_type_absolute)
        {
            ret_width = m_pos.width = self_size.render_width;
        } else
        {
            m_pos.width = self_size.render_width;
            // width: min-content, max-content or fit-content
            const css_length& width = css().get_width();
            if(width.is_predefined() && width.predef() != 0 && !is_root())
            {
                pixel_t content = width.predef() == 1 ? ret_min_width
                                : width.predef() == 2 ? ret_width
                                                      : std::min<pixel_t>(ret_width, self_size.render_width);
                if(content != m_pos.width)
                {
                    m_pos.width       = content;
                    requires_rerender = true;
                }
            }
        }
    } else
    {
        m_pos.width = ret_width;
        if(self_size.width.type == containing_block_context::cbc_value_type_absolute && ret_width > self_size.width)
        {
            ret_width = self_size.width;
        }
    }

    // Fix width with max-width attribute
    if(self_size.max_width.type != containing_block_context::cbc_value_type_none)
    {
        if(m_pos.width > self_size.max_width)
        {
            m_pos.width       = self_size.max_width;
            requires_rerender = true;
        }
    }

    // Fix width with min-width attribute
    if(self_size.min_width.type != containing_block_context::cbc_value_type_none)
    {
        if(m_pos.width < self_size.min_width)
        {
            m_pos.width       = self_size.min_width;
            requires_rerender = true;
        }
    } else if(m_pos.width < 0_px)
    {
        m_pos.width = 0_px;
    }

    // re-render content with new width if required
    if(requires_rerender && !second_pass && !is_root())
    {
        if(src_el()->is_block_formatting_context())
        {
            fmt_ctx->clear_floats(-1);
        } else
        {
            fmt_ctx->clear_floats(self_size.context_idx);
        }

        _render_content(x, y, true, self_size.new_width(m_pos.width + (static_cast<pixel_t>(self_size.width) - self_size.render_width)), fmt_ctx);
    }

    pixel_t content_height = m_pos.height;
    if(size_contained)
        m_pos.height = 0_px;

    if(src_el()->tag() == _textarea_ && self_size.height.type == containing_block_context::cbc_value_type_auto && !size_contained)
    {
        const char* attribute = src_el()->get_attr("rows", "2");
        char* end = nullptr;
        long rows = std::strtol(attribute, &end, 10);
        if(end == attribute || rows <= 0 || rows > INT_MAX)
            rows = 2;
        m_pos.height = css().line_height().computed_value * pixel_t(static_cast<float>(rows));
    }

    // Set block height
    if(self_size.height.type != containing_block_context::cbc_value_type_auto &&
       ((containing_block_size.size_mode & containing_block_context::size_mode_content) == 0))
    {
        // TODO: Something wrong here
        // Percentage height from undefined containing block height is usually <= 0
        if(self_size.height.type == containing_block_context::cbc_value_type_percentage)
        {
            if(self_size.height.value > 0_px)
            {
                m_pos.height = self_size.height;
            }
        } else
        {
            m_pos.height = self_size.height;
        }
        m_pos.height -= box_sizing_height();
    } else if(src_el()->is_block_formatting_context() && !size_contained)
    {
        // add the floats' height to the block height
        pixel_t floats_height = fmt_ctx->get_floats_height();
        m_pos.height          = std::max(floats_height, m_pos.height);
    }
    if((containing_block_size.size_mode & containing_block_context::size_mode_content) != 0)
    {
        if(self_size.height.type == containing_block_context::cbc_value_type_absolute)
        {
            m_pos.height = std::min<pixel_t>(m_pos.height, self_size.height);
        }
    }

    // Fix height with min-height attribute
    if(self_size.min_height.type != containing_block_context::cbc_value_type_none)
    {
        m_pos.height = std::max<pixel_t>(m_pos.height, self_size.min_height);
    } else if(m_pos.height < 0_px)
    {
        m_pos.height = 0;
    }

    // Fix width with max-width attribute
    if(self_size.max_height.type != containing_block_context::cbc_value_type_none)
    {
        m_pos.height = std::min<pixel_t>(m_pos.height, self_size.max_height);
    }

    if(css().get_center_content() &&
       !is_one_of(css().get_display(), display_flex, display_inline_flex, display_grid, display_inline_grid) &&
       m_pos.height > content_height + 0.5_px)
    {
        shift_content((m_pos.height - content_height) / 2_px);
    }

    // calculate the final position
    m_pos.move_to(x, y);
    m_pos.x += content_offset_left();
    m_pos.y += content_offset_top();

    if(src_el()->css().get_display() == display_list_item)
    {
        std::string list_image = src_el()->css().get_list_style_image();
        if(!list_image.empty())
        {
            size        sz;
            std::string list_image_baseurl = src_el()->css().get_list_style_image_baseurl();
            src_el()->get_document()->container()->get_image_size(list_image.c_str(), list_image_baseurl.c_str(), sz);
            m_pos.height = std::max(m_pos.height, sz.height);
        }
    }

    rendered_width ret;
    ret.natural_width = ret_width + content_offset_width();
    if(!is_one_of(css().get_overflow(), overflow_visible, overflow_clip) && css().get_width().is_predefined() &&
       css().get_display() != display_table_cell)
    {
        ret.min_width = content_offset_left() + content_offset_right();
    } else
    {
        if(css().get_width().is_predefined())
        {
            ret.min_width = ret_min_width + content_offset_width();
        } else
        {
            ret.min_width = width();
        }
    }

    return ret;
}
