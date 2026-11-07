#include <cmath>

#include "background.h"
#include "document.h"
#include "document_container.h"
#include "render_item.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace litehtml
{

    bool litehtml::background::get_layer(int idx, position pos, const element* el,
                                         const std::shared_ptr<render_item>& ri, background_layer& layer) const
    {
        if(idx < 0 || idx >= get_layers_number())
        {
            return false;
        }

        position content_box  = pos;
        position padding_box  = pos;
        padding_box          += ri->get_paddings();
        position border_box   = padding_box;
        border_box           += ri->get_borders();

        layer.border_radius = el->css().get_borders().radius.calc_percents(border_box.width, border_box.height);
        layer.border_box    = border_box;
        layer.is_root       = el->is_root();

        int        clip = 0;
        css_size   size;
        css_length position_x;
        css_length position_y;

        if(idx == static_cast<int>(m_image.size()))
        {
            if(m_image.empty())
            {
                clip = !m_clip.empty() ? m_clip.front() : background_box_border;
            } else
            {
                clip = m_clip.empty() ? background_box_border : m_clip[(idx - 1) % m_clip.size()];
            }
        } else
        {
            layer.attachment      = m_attachment.empty()
                                        ? background_attachment_scroll
                                        : static_cast<background_attachment>(m_attachment[idx % m_attachment.size()]);
            layer.repeat          = m_repeat.empty() ? background_repeat_repeat
                                                     : static_cast<background_repeat>(m_repeat[idx % m_repeat.size()]);
            clip                  = m_clip.empty() ? background_box_border : m_clip[idx % m_clip.size()];
            int            origin = m_origin.empty() ? background_box_padding : m_origin[idx % m_origin.size()];
            const css_size auto_auto(css_length::predef_value(background_size_auto),
                                     css_length::predef_value(background_size_auto));
            size = m_size.empty() ? auto_auto : m_size[idx % m_size.size()];
            position_x =
                m_position_x.empty() ? css_length(0, css_units_percentage) : m_position_x[idx % m_position_x.size()];
            position_y =
                m_position_y.empty() ? css_length(0, css_units_percentage) : m_position_y[idx % m_position_y.size()];

            switch(origin)
            {
            case background_box_border:
                layer.origin_box = border_box;
                break;
            case background_box_content:
                layer.origin_box = content_box;
                break;
            default:
                layer.origin_box = padding_box;
                break;
            }
        }

        switch(clip)
        {
        case background_box_padding:
            layer.clip_box = padding_box;
            break;
        case background_box_content:
            layer.clip_box = content_box;
            break;
        default:
            layer.clip_box = border_box;
            break;
        }

        litehtml::size bg_size(layer.origin_box.width, layer.origin_box.height);

        if(get_layer_type(idx) == type_image)
        {
            auto image_layer = get_image_layer(idx);
            if(image_layer)
            {
                litehtml::size img_size;
                el->get_document()->container()->get_image_size(image_layer->url.c_str(), image_layer->base_url.c_str(),
                                                                img_size);
                if(img_size.width != 0_px && img_size.height != 0_px)
                {
                    litehtml::size img_new_sz    = img_size;
                    pixel_t        img_ar_width  = img_size.width / img_size.height;
                    pixel_t        img_ar_height = img_size.height / img_size.width;

                    if(size.width.is_predefined())
                    {
                        switch(size.width.predef())
                        {
                        case background_size_contain:
                            if((layer.origin_box.width * img_ar_height) <= layer.origin_box.height)
                            {
                                img_new_sz.width  = layer.origin_box.width;
                                img_new_sz.height = layer.origin_box.width * img_ar_height;
                            } else
                            {
                                img_new_sz.height = layer.origin_box.height;
                                img_new_sz.width  = layer.origin_box.height * img_ar_width;
                            }
                            break;
                        case background_size_cover:
                            if((layer.origin_box.width * img_ar_height) >= layer.origin_box.height)
                            {
                                img_new_sz.width  = layer.origin_box.width;
                                img_new_sz.height = layer.origin_box.width * img_ar_height;
                            } else
                            {
                                img_new_sz.height = layer.origin_box.height;
                                img_new_sz.width  = layer.origin_box.height * img_ar_width;
                            }
                            break;
                        case background_size_auto:
                            if(!size.height.is_predefined())
                            {
                                img_new_sz.height = size.height.calc_percent(layer.origin_box.height);
                                img_new_sz.width  = img_new_sz.height * img_ar_width;
                            }
                            break;
                        }
                    } else
                    {
                        img_new_sz.width = size.width.calc_percent(layer.origin_box.width);
                        if(size.height.is_predefined())
                        {
                            img_new_sz.height = img_new_sz.width * img_ar_height;
                        } else
                        {
                            img_new_sz.height = size.height.calc_percent(layer.origin_box.height);
                        }
                    }
                    bg_size = img_new_sz;
                }
            }
        } else
        {
            if(!size.width.is_predefined())
            {
                bg_size.width = size.width.calc_percent(layer.origin_box.width);
            }
            if(!size.height.is_predefined())
            {
                bg_size.height = size.height.calc_percent(layer.origin_box.height);
            }
        }

        position new_origin_box;
        new_origin_box.width  = bg_size.width;
        new_origin_box.height = bg_size.height;
        new_origin_box.x      = layer.origin_box.x + position_x.calc_percent(layer.origin_box.width - bg_size.width);
        new_origin_box.y      = layer.origin_box.y + position_y.calc_percent(layer.origin_box.height - bg_size.height);
        layer.origin_box      = new_origin_box;

        return true;
    }

    std::unique_ptr<litehtml::background_layer::image> litehtml::background::get_image_layer(int idx) const
    {
        if(idx >= 0 && idx < static_cast<int>(m_image.size()))
        {
            if(m_image[idx].type == image::type_url)
            {
                auto ret      = std::make_unique<background_layer::image>();
                ret->url      = m_image[idx].url;
                ret->base_url = m_baseurl;
                return ret;
            }
        }
        return {};
    }

    std::unique_ptr<litehtml::background_layer::color> litehtml::background::get_color_layer(int idx) const
    {
        if(idx == static_cast<int>(m_image.size()))
        {
            auto ret   = std::make_unique<background_layer::color>();
            ret->color = m_color;
            return ret;
        }
        return {};
    }

    // Compute the endpoints so that a gradient of the given angle covers a box of
    // the given size.
    // https://source.chromium.org/chromium/chromium/src/+/main:third_party/blink/renderer/core/css/css_gradient_value.cc;drc=7061f1585ab97cc3358d1e0fc9e950e5a107a7f9;l=1070
    static void EndPointsFromAngle(float angle_deg, const litehtml::size& size, litehtml::pointF& first_point,
                                   litehtml::pointF& second_point)
    {
        angle_deg = fmodf(angle_deg, 360);
        if(angle_deg < 0)
        {
            angle_deg += 360;
        }

        if(pixel_t(angle_deg) == 0_px)
        {
            first_point.set(0, static_cast<float>(size.height));
            second_point.set(0, 0);
            return;
        }

        if(pixel_t(angle_deg) == 90_px)
        {
            first_point.set(0, 0);
            second_point.set(static_cast<float>(size.width), 0);
            return;
        }

        if(pixel_t(angle_deg) == 180_px)
        {
            first_point.set(0, 0);
            second_point.set(0, static_cast<float>(size.height));
            return;
        }

        if(pixel_t(angle_deg) == 270_px)
        {
            first_point.set(static_cast<float>(size.width), 0);
            second_point.set(0, 0);
            return;
        }

        // angleDeg is a "bearing angle" (0deg = N, 90deg = E),
        // but tan expects 0deg = E, 90deg = N.
        auto slope = static_cast<float>(tan((90.0 - angle_deg) * M_PI / 180.0));

        // We find the endpoint by computing the intersection of the line formed by
        // the slope, and a line perpendicular to it that intersects the corner.
        float perpendicular_slope = -1 / slope;

        // Compute start corner relative to center, in Cartesian space (+y = up).
        float            half_height = static_cast<float>(size.height) / 2.0f;
        float            half_width  = static_cast<float>(size.width) / 2.0f;
        litehtml::pointF end_corner;
        if(angle_deg < 90)
        {
            end_corner.set(half_width, half_height);
        } else if(angle_deg < 180)
        {
            end_corner.set(half_width, -half_height);
        } else if(angle_deg < 270)
        {
            end_corner.set(-half_width, -half_height);
        } else
        {
            end_corner.set(-half_width, half_height);
        }

        // Compute c (of y = mx + c) using the corner point.
        float c     = end_corner.y - perpendicular_slope * end_corner.x;
        float end_x = c / (slope - perpendicular_slope);
        float end_y = perpendicular_slope * end_x + c;

        // We computed the end point, so set the second point, taking into account the
        // moved origin and the fact that we're in drawing space (+y = down).
        second_point.set(half_width + end_x, half_height - end_y);
        // Reflect around the center for the start point.
        first_point.set(half_width - end_x, half_height + end_y);
    }

    static float distance(const litehtml::pointF& p1, const litehtml::pointF& p2)
    {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        return static_cast<float>(sqrt(dx * dx + dy * dy));
    }

    std::unique_ptr<litehtml::background_layer::linear_gradient> litehtml::background::get_linear_gradient_layer(
        int idx, const background_layer& layer) const
    {
        if(idx < 0 || idx >= static_cast<int>(m_image.size()))
        {
            return {};
        }
        if(m_image[idx].type != image::type_gradient)
        {
            return {};
        }
        if(m_image[idx].m_gradient.m_type != _linear_gradient_ &&
           m_image[idx].m_gradient.m_type != _repeating_linear_gradient_)
        {
            return {};
        }

        auto  ret = std::make_unique<background_layer::linear_gradient>();
        float angle;
        if(m_image[idx].m_gradient.m_side == 0)
        {
            angle = m_image[idx].m_gradient.angle;
        } else
        {
            auto rise = static_cast<float>(layer.origin_box.width);
            auto run  = static_cast<float>(layer.origin_box.height);
            if(m_image[idx].m_gradient.m_side & gradient_side_left)
            {
                run *= -1;
            }
            if(m_image[idx].m_gradient.m_side & gradient_side_bottom)
            {
                rise *= -1;
            }
            angle = static_cast<float>(90 - atan2(rise, run) * 180 / M_PI);
        }
        EndPointsFromAngle(angle,
                           {static_cast<float>(layer.origin_box.width), static_cast<float>(layer.origin_box.height)},
                           ret->start, ret->end);
        ret->start.x += static_cast<float>(layer.origin_box.x);
        ret->start.y += static_cast<float>(layer.origin_box.y);
        ret->end.x   += static_cast<float>(layer.origin_box.x);
        ret->end.y   += static_cast<float>(layer.origin_box.y);

        auto line_len = distance(ret->start, ret->end);

        if(!ret->prepare_color_points(line_len, m_image[idx].m_gradient.m_type, m_image[idx].m_gradient.m_colors))
        {
            return {};
        }

        return ret;
    }

    static inline litehtml::pointF calc_ellipse_radius(const litehtml::pointF& offset, float aspect_ratio)
    {
        // If the aspectRatio is 0 or infinite, the ellipse is completely flat.
        // (If it is NaN, the ellipse is 0x0, and should be handled as zero width.)
        if(!std::isfinite(aspect_ratio) || pixel_t(aspect_ratio) == 0_px)
        {
            return {0, 0};
        }

        // x^2/a^2 + y^2/b^2 = 1
        // a/b = aspectRatio, b = a/aspectRatio
        // a = sqrt(x^2 + y^2/(1/aspect_ratio^2))
        float a = sqrtf(offset.x * offset.x + offset.y * offset.y * aspect_ratio * aspect_ratio);
        return {a, a / aspect_ratio};
    }

    static inline litehtml::pointF find_corner(const litehtml::pointF& center, const litehtml::position& box,
                                               bool farthest)
    {
        struct descr
        {
            float distance;
            float x;
            float y;

            descr(float _distance, float _x, float _y) :
                distance(_distance),
                x(_x),
                y(_y)
            {
            }
        };

        litehtml::pointF ret;

        // Default is left-top corner
        ret.x     = static_cast<float>(box.left());
        ret.y     = static_cast<float>(box.top());
        auto dist = distance(center, {static_cast<float>(box.left()), static_cast<float>(box.top())});

        // Check right-top corner
        auto next_dist = distance(center, {static_cast<float>(box.right()), static_cast<float>(box.top())});
        if((farthest && next_dist > dist) || (!farthest && next_dist < dist))
        {
            ret.x = static_cast<float>(box.right());
            ret.y = static_cast<float>(box.top());
            dist  = next_dist;
        }

        // Check right-bottom corner
        next_dist = distance(center, {static_cast<float>(box.right()), static_cast<float>(box.bottom())});
        if((farthest && next_dist > dist) || (!farthest && next_dist < dist))
        {
            ret.x = static_cast<float>(box.right());
            ret.y = static_cast<float>(box.bottom());
            dist  = next_dist;
        }

        // Check left-bottom corner
        next_dist = distance(center, {static_cast<float>(box.left()), static_cast<float>(box.bottom())});
        if((farthest && next_dist > dist) || (!farthest && next_dist < dist))
        {
            ret.x = static_cast<float>(box.left());
            ret.y = static_cast<float>(box.bottom());
        }

        ret.x -= center.x;
        ret.y -= center.y;

        return ret;
    }

    std::unique_ptr<litehtml::background_layer::radial_gradient> litehtml::background::get_radial_gradient_layer(
        int idx, const background_layer& layer) const
    {
        if(idx < 0 || idx >= static_cast<int>(m_image.size()))
        {
            return {};
        }
        if(m_image[idx].type != image::type_gradient)
        {
            return {};
        }
        if(m_image[idx].m_gradient.m_type != _radial_gradient_ &&
           m_image[idx].m_gradient.m_type != _repeating_radial_gradient_)
        {
            return {};
        }

        auto ret = std::make_unique<background_layer::radial_gradient>();

        ret->position.x = static_cast<float>(layer.origin_box.x) + static_cast<float>(layer.origin_box.width) / 2.0f;
        ret->position.y = static_cast<float>(layer.origin_box.y) + static_cast<float>(layer.origin_box.height) / 2.0f;

        if(m_image[idx].m_gradient.m_side & gradient_side_left)
        {
            ret->position.x = static_cast<float>(layer.origin_box.left());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_right)
        {
            ret->position.x = static_cast<float>(layer.origin_box.right());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_x_center)
        {
            ret->position.x =
                static_cast<float>(layer.origin_box.left()) + static_cast<float>(layer.origin_box.width) / 2.0f;
        } else if(m_image[idx].m_gradient.m_side & gradient_side_x_length)
        {
            ret->position.x =
                static_cast<float>(layer.origin_box.left()) +
                static_cast<float>(m_image[idx].m_gradient.position_x.calc_percent(layer.origin_box.width));
        }

        if(m_image[idx].m_gradient.m_side & gradient_side_top)
        {
            ret->position.y = static_cast<float>(layer.origin_box.top());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_bottom)
        {
            ret->position.y = static_cast<float>(layer.origin_box.bottom());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_y_center)
        {
            ret->position.y =
                static_cast<float>(layer.origin_box.top()) + static_cast<float>(layer.origin_box.height) / 2.0f;
        } else if(m_image[idx].m_gradient.m_side & gradient_side_y_length)
        {
            ret->position.y =
                static_cast<float>(layer.origin_box.top()) +
                static_cast<float>(m_image[idx].m_gradient.position_y.calc_percent(layer.origin_box.height));
        }

        if(m_image[idx].m_gradient.radial_extent)
        {
            switch(m_image[idx].m_gradient.radial_extent)
            {

            case radial_extent_closest_corner:
                {
                    if(m_image[idx].m_gradient.radial_shape == radial_shape_circle)
                    {
                        float corner1 = distance(ret->position, {static_cast<float>(layer.origin_box.left()),
                                                                 static_cast<float>(layer.origin_box.top())});
                        float corner2 = distance(ret->position, {static_cast<float>(layer.origin_box.right()),
                                                                 static_cast<float>(layer.origin_box.top())});
                        float corner3 = distance(ret->position, {static_cast<float>(layer.origin_box.left()),
                                                                 static_cast<float>(layer.origin_box.bottom())});
                        float corner4 = distance(ret->position, {static_cast<float>(layer.origin_box.right()),
                                                                 static_cast<float>(layer.origin_box.bottom())});
                        ret->radius.x = ret->radius.y = std::min({corner1, corner2, corner3, corner4});
                    } else
                    {
                        // Aspect ratio is the same as for radial_extent_closest_side
                        float aspect_ration =
                            std::min(std::abs(ret->position.x - static_cast<float>(layer.origin_box.left())),
                                     std::abs(ret->position.x - static_cast<float>(layer.origin_box.right()))) /
                            std::min(std::abs(ret->position.y - static_cast<float>(layer.origin_box.top())),
                                     std::abs(ret->position.y - static_cast<float>(layer.origin_box.bottom())));

                        auto corner   = find_corner(ret->position, layer.origin_box, false);
                        auto radius   = calc_ellipse_radius(corner, aspect_ration);
                        ret->radius.x = radius.x;
                        ret->radius.y = radius.y;
                    }
                }
                break;
            case radial_extent_closest_side:
                if(m_image[idx].m_gradient.radial_shape == radial_shape_circle)
                {
                    ret->radius.x = ret->radius.y = std::min({
                        std::abs(ret->position.x - static_cast<float>(layer.origin_box.left())),
                        std::abs(ret->position.x - static_cast<float>(layer.origin_box.right())),
                        std::abs(ret->position.y - static_cast<float>(layer.origin_box.top())),
                        std::abs(ret->position.y - static_cast<float>(layer.origin_box.bottom())),
                    });
                } else
                {
                    ret->radius.x = std::min(std::abs(ret->position.x - static_cast<float>(layer.origin_box.left())),
                                             std::abs(ret->position.x - static_cast<float>(layer.origin_box.right())));
                    ret->radius.y = std::min(std::abs(ret->position.y - static_cast<float>(layer.origin_box.top())),
                                             std::abs(ret->position.y - static_cast<float>(layer.origin_box.bottom())));
                }
                break;
            case radial_extent_farthest_corner:
                {
                    if(m_image[idx].m_gradient.radial_shape == radial_shape_circle)
                    {
                        float corner1 = distance(ret->position, {static_cast<float>(layer.origin_box.left()),
                                                                 static_cast<float>(layer.origin_box.top())});
                        float corner2 = distance(ret->position, {static_cast<float>(layer.origin_box.right()),
                                                                 static_cast<float>(layer.origin_box.top())});
                        float corner3 = distance(ret->position, {static_cast<float>(layer.origin_box.left()),
                                                                 static_cast<float>(layer.origin_box.bottom())});
                        float corner4 = distance(ret->position, {static_cast<float>(layer.origin_box.right()),
                                                                 static_cast<float>(layer.origin_box.bottom())});
                        ret->radius.x = ret->radius.y = std::max({corner1, corner2, corner3, corner4});
                    } else
                    {
                        // Aspect ratio is the same as for radial_extent_farthest_side
                        float aspect_ration =
                            std::max(std::abs(ret->position.x - static_cast<float>(layer.origin_box.left())),
                                     std::abs(ret->position.x - static_cast<float>(layer.origin_box.right()))) /
                            std::max(std::abs(ret->position.y - static_cast<float>(layer.origin_box.top())),
                                     std::abs(ret->position.y - static_cast<float>(layer.origin_box.bottom())));

                        auto corner   = find_corner(ret->position, layer.origin_box, true);
                        auto radius   = calc_ellipse_radius(corner, aspect_ration);
                        ret->radius.x = radius.x;
                        ret->radius.y = radius.y;
                    }
                }
                break;
            case radial_extent_farthest_side:
                if(m_image[idx].m_gradient.radial_shape == radial_shape_circle)
                {
                    ret->radius.x = ret->radius.y = std::max({
                        std::abs(ret->position.x - static_cast<float>(layer.origin_box.left())),
                        std::abs(ret->position.x - static_cast<float>(layer.origin_box.right())),
                        std::abs(ret->position.y - static_cast<float>(layer.origin_box.top())),
                        std::abs(ret->position.y - static_cast<float>(layer.origin_box.bottom())),
                    });
                } else
                {
                    ret->radius.x = std::max(std::abs(ret->position.x - static_cast<float>(layer.origin_box.left())),
                                             std::abs(ret->position.x - static_cast<float>(layer.origin_box.right())));
                    ret->radius.y = std::max(std::abs(ret->position.y - static_cast<float>(layer.origin_box.top())),
                                             std::abs(ret->position.y - static_cast<float>(layer.origin_box.bottom())));
                }
                break;
            default:
                break;
            }
        }
        if(!m_image[idx].m_gradient.radial_radius_x.is_predefined())
        {
            ret->radius.x =
                static_cast<float>(m_image[idx].m_gradient.radial_radius_x.calc_percent(layer.origin_box.width));
        }
        if(!m_image[idx].m_gradient.radial_radius_y.is_predefined())
        {
            ret->radius.y =
                static_cast<float>(m_image[idx].m_gradient.radial_radius_y.calc_percent(layer.origin_box.height));
        }

        if(ret->prepare_color_points(ret->radius.x, m_image[idx].m_gradient.m_type, m_image[idx].m_gradient.m_colors))
        {
            return ret;
        }

        return {};
    }

    std::unique_ptr<litehtml::background_layer::conic_gradient> litehtml::background::get_conic_gradient_layer(
        int idx, const background_layer& layer) const
    {
        if(idx < 0 || idx >= static_cast<int>(m_image.size()))
        {
            return {};
        }
        if(m_image[idx].type != image::type_gradient)
        {
            return {};
        }
        if(m_image[idx].m_gradient.m_type != _conic_gradient_ &&
           m_image[idx].m_gradient.m_type != _repeating_conic_gradient_)
        {
            return {};
        }

        auto ret = std::make_unique<background_layer::conic_gradient>();

        ret->position.x = static_cast<float>(layer.origin_box.x) + static_cast<float>(layer.origin_box.width) / 2.0f;
        ret->position.y = static_cast<float>(layer.origin_box.y) + static_cast<float>(layer.origin_box.height) / 2.0f;

        if(m_image[idx].m_gradient.m_side & gradient_side_left)
        {
            ret->position.x = static_cast<float>(layer.origin_box.left());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_right)
        {
            ret->position.x = static_cast<float>(layer.origin_box.right());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_x_center)
        {
            ret->position.x =
                static_cast<float>(layer.origin_box.left()) + static_cast<float>(layer.origin_box.width) / 2.0f;
        } else if(m_image[idx].m_gradient.m_side & gradient_side_x_length)
        {
            ret->position.x =
                static_cast<float>(layer.origin_box.left()) +
                static_cast<float>(m_image[idx].m_gradient.position_x.calc_percent(layer.origin_box.width));
        }

        if(m_image[idx].m_gradient.m_side & gradient_side_top)
        {
            ret->position.y = static_cast<float>(layer.origin_box.top());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_bottom)
        {
            ret->position.y = static_cast<float>(layer.origin_box.bottom());
        } else if(m_image[idx].m_gradient.m_side & gradient_side_y_center)
        {
            ret->position.y =
                static_cast<float>(layer.origin_box.top()) + static_cast<float>(layer.origin_box.height) / 2.0f;
        } else if(m_image[idx].m_gradient.m_side & gradient_side_y_length)
        {
            ret->position.y =
                static_cast<float>(layer.origin_box.top()) +
                static_cast<float>(m_image[idx].m_gradient.position_y.calc_percent(layer.origin_box.height));
        }

        ret->angle             = m_image[idx].m_gradient.conic_from_angle;
        ret->color_space       = m_image[idx].m_gradient.color_space;
        ret->hue_interpolation = m_image[idx].m_gradient.hue_interpolation;

        float corner1 = distance(
            ret->position, {static_cast<float>(layer.origin_box.left()), static_cast<float>(layer.origin_box.top())});
        float corner2 = distance(
            ret->position, {static_cast<float>(layer.origin_box.right()), static_cast<float>(layer.origin_box.top())});
        float corner3 = distance(ret->position, {static_cast<float>(layer.origin_box.left()),
                                                 static_cast<float>(layer.origin_box.bottom())});
        float corner4 = distance(ret->position, {static_cast<float>(layer.origin_box.right()),
                                                 static_cast<float>(layer.origin_box.bottom())});
        ret->radius   = std::max({corner1, corner2, corner3, corner4});

        if(ret->prepare_color_points(0, m_image[idx].m_gradient.m_type, m_image[idx].m_gradient.m_colors))
        {
            return ret;
        }

        return {};
    }

    litehtml::background::layer_type litehtml::background::get_layer_type(int idx) const
    {
        if(idx >= 0 && idx < static_cast<int>(m_image.size()))
        {
            switch(m_image[idx].type)
            {

            case image::type_url:
                return type_image;
            case image::type_gradient:
                switch(m_image[idx].m_gradient.m_type)
                {
                case _linear_gradient_:
                case _repeating_linear_gradient_:
                    return type_linear_gradient;
                case _radial_gradient_:
                case _repeating_radial_gradient_:
                    return type_radial_gradient;
                case _conic_gradient_:
                case _repeating_conic_gradient_:
                    return type_conic_gradient;
                default:
                    break;
                }
                break;
            default:
                break;
            }
        } else if(idx == static_cast<int>(m_image.size()))
        {
            return type_color;
        }
        return type_none;
    }

    void litehtml::background::draw_layer(uint_ptr hdc, int idx, const background_layer& layer,
                                          document_container* container) const
    {
        switch(get_layer_type(idx))
        {
        case background::type_color:
            {
                auto color_layer = get_color_layer(idx);
                if(color_layer)
                {
                    container->draw_solid_fill(hdc, layer, color_layer->color);
                }
            }
            break;
        case background::type_image:
            if(layer.origin_box.width != 0_px && layer.origin_box.height != 0_px)
            {
                auto image_layer = get_image_layer(idx);
                if(image_layer)
                {
                    container->draw_image(hdc, layer, image_layer->url, image_layer->base_url);
                }
            }
            break;
        case background::type_linear_gradient:
            if(layer.origin_box.width != 0_px && layer.origin_box.height != 0_px)
            {
                auto gradient_layer = get_linear_gradient_layer(idx, layer);
                if(gradient_layer)
                {
                    container->draw_linear_gradient(hdc, layer, *gradient_layer);
                }
            }
            break;
        case background::type_radial_gradient:
            if(layer.origin_box.width != 0_px && layer.origin_box.height != 0_px)
            {
                auto gradient_layer = get_radial_gradient_layer(idx, layer);
                if(gradient_layer)
                {
                    container->draw_radial_gradient(hdc, layer, *gradient_layer);
                }
            }
            break;
        case background::type_conic_gradient:
            if(layer.origin_box.width != 0_px && layer.origin_box.height != 0_px)
            {
                auto gradient_layer = get_conic_gradient_layer(idx, layer);
                if(gradient_layer)
                {
                    container->draw_conic_gradient(hdc, layer, *gradient_layer);
                }
            }
            break;
        default:
            break;
        }
    }

    static void repeat_color_points(std::vector<litehtml::background_layer::color_point>& color_points)
    {
        auto old_points = color_points;
        if(color_points.back().offset < 1)
        {
            float gd_size = color_points.back().offset - old_points.front().offset;
            auto  iter    = old_points.begin();
            while(color_points.back().offset < 1)
            {
                color_points.emplace_back(iter->offset + gd_size, iter->color);
                std::advance(iter, 1);
                if(iter == old_points.end())
                {
                    iter    = old_points.begin();
                    gd_size = color_points.back().offset - old_points.front().offset;
                }
            }
        }
        if(color_points.front().offset > 0)
        {
            float gd_size = color_points.front().offset;
            auto  iter    = old_points.rbegin();
            while(color_points.front().offset > 0)
            {
                color_points.emplace(color_points.begin(), gd_size - (old_points.back().offset - iter->offset),
                                     iter->color);
                std::advance(iter, 1);
                if(iter == old_points.rend())
                {
                    iter    = old_points.rbegin();
                    gd_size = color_points.front().offset;
                }
            }
        }
    }

    void litehtml::background_layer::gradient_base::color_points_transparent_fix()
    {
        for(int i = 0; i < static_cast<int>(color_points.size()); i++)
        {
            if(color_points[i].color.alpha == 0)
            {
                if(i == 0)
                {
                    if(i + 1 < static_cast<int>(color_points.size()))
                    {
                        color_points[i].color       = color_points[i + 1].color;
                        color_points[i].color.alpha = 0;
                    }
                } else if(i + 1 == static_cast<int>(color_points.size()))
                {
                    if(i - 1 >= 0)
                    {
                        color_points[i].color       = color_points[i - 1].color;
                        color_points[i].color.alpha = 0;
                    }
                } else
                {
                    color_points[i].color       = color_points[i + 1].color;
                    color_points[i].color.alpha = 0;
                    background_layer::color_point cpt;
                    cpt.color       = color_points[i - 1].color;
                    cpt.color.alpha = 0;
                    cpt.offset      = color_points[i].offset;
                    color_points.emplace(std::next(color_points.begin(), i), cpt);
                    i++;
                }
            }
        }
    }

    // normalize length into value between 0 and 1
    float normalize_length(css_length length, float line_len)
    {
        if(length.units() == css_units_percentage)
        {
            return length.val() / 100.0f;
        }
        if(pixel_t(line_len) != 0_px)
        {
            return length.val() / line_len;
        }
        return length.val();
    }

    bool litehtml::background_layer::gradient_base::prepare_color_points(
        float line_len, string_id g_type, const std::vector<gradient::color_stop>& colors)
    {
        bool repeating = false;
        if(g_type == _linear_gradient_ || g_type == _radial_gradient_ || g_type == _conic_gradient_)
        {
            repeating = false;
        } else if(g_type == _repeating_linear_gradient_ || g_type == _repeating_radial_gradient_ ||
                  g_type == _repeating_conic_gradient_)
        {
            repeating = true;
        }
        int  none_units      = 0;
        bool has_transparent = false;
        for(const auto& item : colors)
        {
            if(item.is_color_hint)
            {
                if(!color_points.empty())
                {
                    color_points.back().hint =
                        item.length ? normalize_length(*item.length, line_len) : *item.angle / 360;
                }
                continue;
            }
            if(item.color.alpha == 0)
            {
                has_transparent = true;
            }
            if(item.length)
            {
                color_points.emplace_back(normalize_length(*item.length, line_len), item.color);
            } else if(item.angle)
            {
                color_points.emplace_back(*item.angle / 360, item.color);
            } else
            {
                if(!color_points.empty())
                {
                    none_units++;
                }
                color_points.emplace_back(0.0f, item.color);
            }
        }
        if(color_points.empty())
        {
            return false;
        }

        if(!repeating)
        {
            // Add color point with offset 0 if not exists
            if(pixel_t(color_points[0].offset) != 0_px)
            {
                color_points.emplace(color_points.begin(), 0.0f, color_points[0].color);
            }
            // Add color point with offset 1.0 if not exists
            if(pixel_t(color_points.back().offset) < 1_px)
            {
                if(pixel_t(color_points.back().offset) == 0_px)
                {
                    color_points.back().offset = 1;
                    none_units--;
                } else
                {
                    color_points.emplace_back(1.0f, color_points.back().color);
                }
            }
        } else
        {
            // Add color point with offset 1.0 if not exists
            if(pixel_t(color_points.back().offset) == 0_px)
            {
                color_points.back().offset = 1;
                none_units--;
            }
        }

        if(none_units > 0)
        {
            size_t i = 1;
            while(i < color_points.size())
            {
                if(pixel_t(color_points[i].offset) != 0_px)
                {
                    i++;
                    continue;
                }
                // Find next defined offset
                size_t j = i + 1;
                while(pixel_t(color_points[j].offset) == 0_px)
                {
                    j++;
                }
                size_t num    = j - i;
                float  sum    = color_points[i - 1].offset + color_points[j].offset;
                float  offset = sum / static_cast<float>(num + 1);
                while(i < j)
                {
                    color_points[i].offset = color_points[i - 1].offset + offset;
                    i++;
                }
            }
        }

        // process transparent
        if(has_transparent)
        {
            color_points_transparent_fix();
        }

        if(repeating)
        {
            repeat_color_points(color_points);
        }

        return true;
    }

} // namespace litehtml
