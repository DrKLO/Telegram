#ifndef LITEHTML_STRING_ID_H
#define LITEHTML_STRING_ID_H

#include <string>

namespace litehtml
{

#define STRING_ID(...)                                                                                                 \
    enum string_id                                                                                                     \
    {                                                                                                                  \
        __VA_ARGS__                                                                                                    \
    };                                                                                                                 \
    const auto initial_string_ids = #__VA_ARGS__;

    STRING_ID(

        // HTML tags
        _a_, _abbr_, _acronym_, _address_, _applet_, _area_, _article_, _aside_, _audio_, _b_, _base_, _basefont_,
        _bdi_, _bdo_, _big_, _blockquote_, _body_, _br_, _button_, _canvas_, _caption_, _center_, _cite_, _code_, _col_,
        _colgroup_, _data_, _datalist_, _dd_, _del_, _details_, _dfn_, _dialog_, _dir_, _div_, _dl_, _dt_, _em_,
        _embed_, _fieldset_, _figcaption_, _figure_, _footer_, _form_, _frame_, _frameset_, _h1_, _h2_, _h3_, _h4_,
        _h5_, _h6_, _head_, _header_, _hr_, _html_, _i_, _iframe_, _img_, _input_, _ins_, _kbd_, _label_, _legend_,
        _li_, _link_, _main_, _map_, _mark_, _meta_, _meter_, _nav_, _noframes_, _noscript_, _object_, _ol_, _optgroup_,
        _option_, _output_, _p_, _param_, _picture_, _pre_, _progress_, _q_, _rp_, _rt_, _ruby_, _s_, _samp_, _script_,
        _section_, _select_, _small_, _source_, _span_, _strike_, _strong_, _style_, _sub_, _summary_, _sup_, _svg_,
        _table_, _tbody_, _td_, _template_, _textarea_, _tfoot_, _th_, _thead_, _time_, _title_, _tr_, _track_, _tt_,
        _u_, _ul_, _var_, _video_, _wbr_,

        // litehtml internal tags
        __tag_before_, // note: real tag cannot start with '-'
        __tag_after_,

        // CSS pseudo-elements
        _before_, _after_,

        // CSS pseudo-classes
        _root_, _only_child_, _only_of_type_, _first_child_, _first_of_type_, _last_child_, _last_of_type_, _nth_child_,
        _nth_of_type_, _nth_last_child_, _nth_last_of_type_, _is_, _where_, _not_, _lang_,

        _active_, _hover_, _checked_, _disabled_,

        // CSS property names
        // Side properties must go in this order: top, right, bottom, left (clockwise starting from the top).
        // Corner properties must go in this order: top-left, top-right, bottom-right, bottom-left (clockwise starting
        // from the top-left). This is used in style::add_four_properties() for margin-*, padding-*,
        // border-*-{width,style,color} and border-*-*-radius{,-x,-y}.
        _background_, _background_color_, _background_image_, _background_image_baseurl_, _background_repeat_,
        _background_origin_, _background_clip_, _background_attachment_, _background_size_, _background_position_,
        _background_position_x_, _background_position_y_, _background_gradient_,

        _border_, _border_width_, _border_style_, _border_color_,

        _border_spacing_, __litehtml_border_spacing_x_, __litehtml_border_spacing_y_,

        _border_top_, _border_right_, _border_bottom_, _border_left_,

        _border_top_style_, _border_right_style_, _border_bottom_style_, _border_left_style_,

        _border_top_width_, _border_right_width_, _border_bottom_width_, _border_left_width_,

        _border_top_color_, _border_right_color_, _border_bottom_color_, _border_left_color_,

        _border_radius_, _border_radius_x_, _border_radius_y_,

        _border_top_left_radius_, _border_top_right_radius_, _border_bottom_right_radius_, _border_bottom_left_radius_,

        _border_top_left_radius_x_, _border_top_right_radius_x_, _border_bottom_right_radius_x_,
        _border_bottom_left_radius_x_,

        _border_top_left_radius_y_, _border_top_right_radius_y_, _border_bottom_right_radius_y_,
        _border_bottom_left_radius_y_,

        _list_style_, _list_style_type_, _list_style_position_, _list_style_image_, _list_style_image_baseurl_,

        _margin_, _margin_top_, _margin_right_, _margin_bottom_, _margin_left_,

        _padding_, _padding_top_, _padding_right_, _padding_bottom_, _padding_left_,

        _font_, _font_family_, _font_style_, _font_variant_, _font_weight_, _font_size_, _line_height_,
        _text_decoration_, _text_decoration_style_, _text_decoration_line_, _text_decoration_color_,
        _text_decoration_thickness_, _text_emphasis_, _text_emphasis_style_, _text_emphasis_color_,
        _text_emphasis_position_,

        _white_space_, _text_align_, _vertical_align_, _color_, _width_, _height_, _min_width_, _min_height_,
        _max_width_, _max_height_, _position_, _overflow_, _display_, _visibility_, _appearance_, _box_sizing_,
        _z_index_, _float_, _clear_, _text_indent_, _top_, _right_, _bottom_, _left_, _cursor_, _content_,
        _border_collapse_, _text_transform_,

        _flex_, _flex_flow_, _flex_direction_, _flex_wrap_, _justify_content_, _align_items_, _align_content_,
        _align_self_, _flex_grow_, _flex_shrink_, _flex_basis_,

        _caption_side_, _order_,

        _counter_reset_, _counter_increment_,

        // some CSS dimensions
        _deg_, _grad_, _rad_, _turn_,

        // some CSS property values
        _initial_, _auto_, _none_, _linear_gradient_, _repeating_linear_gradient_, _radial_gradient_,
        _repeating_radial_gradient_, _conic_gradient_, _repeating_conic_gradient_,

        // at-rules and their components
        _charset_, _layer_, _supports_, _import_, _media_, _and_, _or_, _boolean_, _plain_, _range_, _discrete_,
        _integer_, _length_, _resolution_, _ratio_, _keyword_, _orientation_, _portrait_, _landscape_, _device_width_,
        _device_height_, _aspect_ratio_, _device_aspect_ratio_, _color_index_, _monochrome_, )
#undef STRING_ID
    extern const string_id empty_id; // _id("")
    extern const string_id star_id;  // _id("*")

    string_id          _id(const std::string& str);
    const std::string& _s(string_id id);

} // namespace litehtml

#endif // LITEHTML_STRING_ID_H
