#include "el_image.h"
#include "render_image.h"
#include "document_container.h"

litehtml::el_image::el_image(const document::ptr& doc) :
    html_tag(doc)
{
    m_css.set_display(display_inline_block);
}

void litehtml::el_image::get_content_size(size& sz, pixel_t /*max_width*/)
{
    get_document()->container()->get_image_size(m_src.c_str(), nullptr, sz);
}

bool litehtml::el_image::is_replaced() const
{
    return true;
}

void litehtml::el_image::parse_attributes()
{
    m_src = get_attr("src", "");

    // https://html.spec.whatwg.org/multipage/rendering.html#attributes-for-embedded-content-and-images:the-img-element-5
    const char* str = get_attr("width");
    if(str)
    {
        map_to_dimension_property(_width_, str);
    }

    str = get_attr("height");
    if(str)
    {
        map_to_dimension_property(_height_, str);
    }
}

void litehtml::el_image::draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                              const std::shared_ptr<render_item>& ri)
{
    html_tag::draw(hdc, x, y, clip, ri);
    position pos  = ri->pos();
    pos.x        += x;
    pos.y        += y;
    pos.round();

    // draw image as background
    if(pos.does_intersect(clip))
    {
        if(pos.width > 0_px && pos.height > 0_px)
        {
            background_layer layer;
            layer.clip_box    = pos;
            layer.origin_box  = pos;
            layer.border_box  = pos;
            layer.border_box += ri->get_paddings();
            layer.border_box += ri->get_borders();
            layer.repeat      = background_repeat_no_repeat;
            layer.border_radius =
                css().get_borders().radius.calc_percents(layer.border_box.width, layer.border_box.height);
            get_document()->container()->draw_image(hdc, layer, m_src, {});
        }
    }
}

void litehtml::el_image::compute_styles(bool recursive)
{
    html_tag::compute_styles(recursive);

    if(!m_src.empty())
    {
        if(!css().get_height().is_predefined() && !css().get_width().is_predefined())
        {
            get_document()->container()->load_image(m_src.c_str(), nullptr, true);
        } else
        {
            get_document()->container()->load_image(m_src.c_str(), nullptr, false);
        }
    }
}

std::string litehtml::el_image::dump_get_name()
{
    return "img src=\"" + m_src + "\"";
}

std::shared_ptr<litehtml::render_item> litehtml::el_image::create_render_item(
    const std::shared_ptr<render_item>& parent_ri)
{
    auto ret = std::make_shared<render_item_image>(shared_from_this());
    ret->parent(parent_ri);
    return ret;
}
