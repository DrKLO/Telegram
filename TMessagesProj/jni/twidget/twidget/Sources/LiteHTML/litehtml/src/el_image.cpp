#include "html.h"
#include "el_image.h"
#include "render_image.h"
#include "document_container.h"

#include <algorithm>

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

void litehtml::el_image::update_src()
{
    m_src    = get_attr("src", "");
    m_loaded = false;
}

void litehtml::el_image::parse_attributes()
{
    m_src    = get_attr("src", "");
    m_loaded = false;

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
            layer.origin_box  = fitted(pos);
            layer.border_box  = pos;
            layer.border_box += ri->get_paddings();
            layer.border_box += ri->get_borders();
            layer.repeat      = background_repeat_no_repeat;
            layer.border_radius =
                css().get_borders().radius.calc_percents(layer.border_box.width, layer.border_box.height);
            doc_ptr()->container()->draw_image(hdc, layer, m_src, {});
        }
    }
}

litehtml::position litehtml::el_image::fitted(const position& box)
{
    std::string fit = raw_declaration("object-fit");
    trim(fit);
    lcase(fit);
    std::string aspect;
    if(fit.empty() || fit == "fill")
    {
        aspect = get_document()->container()->get_image_aspect(m_src.c_str());
        if(aspect.empty())
            return box;
        fit = aspect.find("slice") != std::string::npos ? "cover" : "contain";
    }
    size natural;
    get_document()->container()->get_image_size(m_src.c_str(), nullptr, natural);
    if(natural.width <= 0_px || natural.height <= 0_px)
        return box;
    const float sx = (box.width / natural.width).value(), sy = (box.height / natural.height).value();
    float       scale = 1;
    if(fit == "contain")
        scale = std::min(sx, sy);
    else if(fit == "cover")
        scale = std::max(sx, sy);
    else if(fit == "scale-down")
        scale = std::min(1.f, std::min(sx, sy));
    const float w = natural.width.value() * scale, h = natural.height.value() * scale;
    // object-position: keywords, percentages of the room left, or lengths from the box's corner.
    std::string where = raw_declaration("object-position");
    if(!aspect.empty())
        where = std::string(aspect.find("xmin") != std::string::npos   ? "left"
                            : aspect.find("xmax") != std::string::npos ? "right"
                                                                       : "center") +
                (aspect.find("ymin") != std::string::npos   ? " top"
                 : aspect.find("ymax") != std::string::npos ? " bottom"
                                                            : " center");
    lcase(where);
    string_vector parts;
    split_string(where, parts, " ");
    parts.erase(std::remove_if(parts.begin(), parts.end(), [](const std::string& s) { return s.empty(); }), parts.end());
    if(parts.size() == 1)
        parts.push_back(is_one_of(parts[0], "top", "bottom") ? parts[0] : "center");
    if(parts.size() >= 2 && is_one_of(parts[0], "top", "bottom"))
        std::swap(parts[0], parts[1]);
    auto offset = [](const std::string& value, float room) {
        if(value == "left" || value == "top")
            return 0.f;
        if(value == "right" || value == "bottom")
            return room;
        if(value.empty() || value == "center")
            return room / 2;
        float v = std::strtof(value.c_str(), nullptr);
        return value.back() == '%' ? room * v / 100 : v;
    };
    const float dx = offset(parts.size() >= 2 ? parts[0] : "center", box.width.value() - w);
    const float dy = offset(parts.size() >= 2 ? parts[1] : "center", box.height.value() - h);
    return position(box.x + pixel_t(dx), box.y + pixel_t(dy), pixel_t(w), pixel_t(h));
}

void litehtml::el_image::compute_styles(bool recursive)
{
    html_tag::compute_styles(recursive);

    if(!m_src.empty() && !m_loaded)
    {
        m_loaded = true;
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
