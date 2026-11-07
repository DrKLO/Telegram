#ifndef LITEHTML_BACKGROUND_H
#define LITEHTML_BACKGROUND_H

#include "types.h"
#include "borders.h"
#include "css_length.h"
#include "css_position.h"
#include "gradient.h"
#include "web_color.h"

namespace litehtml
{
    class background_layer
    {
      public:
        // border_box defines draw boundary. Everything must be drawn inside this rectangle only.
        position border_box;
        // border_radius defines radius of the border_box.
        border_radiuses border_radius;
        // clip_box defines clipping rectangle. Works like border_box. Container must set additional clipping.
        position clip_box;
        // origin_box defines origin rectangle.
        position              origin_box;
        background_attachment attachment = background_attachment_scroll;
        background_repeat     repeat     = background_repeat_repeat;
        // is_root is true for root element. Container can use this flag to apply background to the top window.
        bool is_root = false;

        background_layer() = default;

        class image
        {
          public:
            std::string url;
            std::string base_url;
        };

        struct color_point
        {
            float                offset;
            web_color            color;
            std::optional<float> hint;
            color_point() :
                offset(0.0)
            {
            }
            color_point(float _offset, web_color _color) :
                offset(_offset),
                color(_color)
            {
            }
        };

        class color
        {
          public:
            web_color color;
        };

        class gradient_base
        {
          public:
            std::vector<color_point> color_points;
            color_space_t            color_space       = color_space_none;
            hue_interpolation_t      hue_interpolation = hue_interpolation_none;

            void color_points_transparent_fix();
            bool prepare_color_points(float len, string_id grad_type, const std::vector<gradient::color_stop>& colors);
        };

        class linear_gradient : public gradient_base
        {
          public:
            pointF start;
            pointF end;
        };

        class radial_gradient : public gradient_base
        {
          public:
            pointF position;
            pointF radius;
        };

        class conic_gradient : public gradient_base
        {
          public:
            pointF position; // position is the center of the conic gradient
            float  angle =
                0; // angle is the angle of the gradient in degrees, starting from 0 at the top and going clockwise
            float radius = 0; // radius is the distance from the center to the farthest corner of the background box
        };
    };

    class background
    {
      public:
        enum layer_type
        {
            type_none,
            type_color,
            type_image,
            type_linear_gradient,
            type_radial_gradient,
            type_conic_gradient,
        };

        std::vector<image> m_image;
        std::string        m_baseurl;
        web_color          m_color;
        int_vector         m_attachment;
        length_vector      m_position_x;
        length_vector      m_position_y;
        size_vector        m_size;
        int_vector         m_repeat;
        int_vector         m_clip;
        int_vector         m_origin;

        bool is_empty() const
        {
            if(m_color.alpha != 0)
            {
                return false;
            }
            if(m_image.empty())
            {
                return true;
            }
            for(const auto& img : m_image)
            {
                if(!img.is_empty())
                {
                    return false;
                }
            }
            return true;
        }
        int get_layers_number() const
        {
            if(m_color != web_color::transparent)
            {
                return static_cast<int>(m_image.size()) + 1;
            }
            return static_cast<int>(m_image.size());
        }
        bool       get_layer(int idx, position pos, const element* el, const std::shared_ptr<render_item>& ri,
                             background_layer& layer) const;
        layer_type get_layer_type(int idx) const;
        std::unique_ptr<background_layer::image>           get_image_layer(int idx) const;
        std::unique_ptr<background_layer::color>           get_color_layer(int idx) const;
        std::unique_ptr<background_layer::linear_gradient> get_linear_gradient_layer(
            int idx, const background_layer& layer) const;
        std::unique_ptr<background_layer::radial_gradient> get_radial_gradient_layer(
            int idx, const background_layer& layer) const;
        std::unique_ptr<background_layer::conic_gradient> get_conic_gradient_layer(int                     idx,
                                                                                   const background_layer& layer) const;
        void draw_layer(uint_ptr hdc, int idx, const background_layer& layer, document_container* container) const;
    };
} // namespace litehtml

#endif // LITEHTML_BACKGROUND_H
