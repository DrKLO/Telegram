#ifndef LITEHTML_CSS_VALUES_H
#define LITEHTML_CSS_VALUES_H

#include <array>
#include <cstdint>
#include <optional>
#include <string_view>

namespace litehtml
{
    template <std::size_t N> constexpr auto split_css_values(std::string_view str)
    {
        std::array<std::string_view, N> result{};
        std::size_t                     start = 0;
        std::size_t                     index = 0;

        for(std::size_t i = 0; i <= str.size(); ++i)
        {
            if(i == str.size() || str[i] == ';')
            {
                result[index++] = str.substr(start, i - start);
                start           = i + 1;
            }
        }
        return result;
    }

    struct css_values
    {
        const std::string_view* data = nullptr;
        std::size_t             size = 0;

        template <std::size_t N>
        css_values(const std::array<std::string_view, N>& data) :
            data(data.data()),
            size(data.size())
        {
        }

        css_values() = default;

        std::optional<int> value_index(const std::string_view& str) const
        {
            for(std::size_t i = 0; i < size; i++)
            {
                if(data[i] == str)
                {
                    return static_cast<int>(i);
                }
            }
            return std::nullopt;
        }

        bool has(const std::string_view& str) const
        {
            return value_index(str).has_value();
        }

        std::string_view value_by_index(std::size_t index) const
        {
            if(index < size)
            {
                return data[index];
            }
            return "";
        }
    };

    // ==========================================================
    // CSS Property: display
    // ==========================================================
    inline constexpr auto style_display_strings = split_css_values<18>(
        "none;block;inline;inline-block;inline-table;list-item;table;table-caption;table-cell;table-column;"
        "table-column-group;table-footer-group;table-header-group;table-row;table-row-group;inline-text;flex;"
        "inline-flex");

    enum style_display
    {
        display_none,
        display_block,
        display_inline,
        display_inline_block,
        display_inline_table,
        display_list_item,
        display_table,
        display_table_caption,
        display_table_cell,
        display_table_column,
        display_table_column_group,
        display_table_footer_group,
        display_table_header_group,
        display_table_row,
        display_table_row_group,
        display_inline_text,
        display_flex,
        display_inline_flex,
    };

    // ==========================================================
    // CSS Property: font-size
    // ==========================================================
    inline constexpr auto font_size_strings =
        split_css_values<9>("xx-small;x-small;small;medium;large;x-large;xx-large;smaller;larger");

    enum font_size
    {
        font_size_xx_small,
        font_size_x_small,
        font_size_small,
        font_size_medium,
        font_size_large,
        font_size_x_large,
        font_size_xx_large,
        font_size_smaller,
        font_size_larger,
    };

    // ==========================================================
    // CSS Property: line-height
    // ==========================================================
    inline constexpr auto line_height_strings = split_css_values<1>("normal");

    enum line_height
    {
        line_height_normal
    };

    // ==========================================================
    // CSS Property: font-style
    // ==========================================================
    inline constexpr auto font_style_strings = split_css_values<2>("normal;italic");

    enum font_style
    {
        font_style_normal,
        font_style_italic
    };

    // ==========================================================
    // CSS Property: font-system-family-name
    // ==========================================================
    inline constexpr auto font_system_family_name_strings =
        split_css_values<6>("caption;icon;menu;message-box;small-caption;status-bar");

    // ==========================================================
    // CSS Property: font-variant
    // ==========================================================
    inline constexpr auto font_variant_strings = split_css_values<2>("normal;small-caps");

    enum font_variant
    {
        font_variant_normal,
        font_variant_small_caps
    };

    // ==========================================================
    // CSS Property: font-weight
    // ==========================================================
    inline constexpr auto font_weight_strings = split_css_values<4>("normal;bold;bolder;lighter");

    enum font_weight
    {
        font_weight_normal,
        font_weight_bold,
        font_weight_bolder,
        font_weight_lighter,
    };

    // ==========================================================
    // CSS Property: list-style-type
    // ==========================================================
    inline constexpr auto list_style_type_strings = split_css_values<22>(
        "none;circle;disc;square;armenian;cjk-ideographic;decimal;decimal-leading-zero;georgian;hebrew;hiragana;"
        "hiragana-iroha;katakana;katakana-iroha;lower-alpha;lower-greek;lower-latin;lower-roman;upper-alpha;"
        "upper-latin;upper-roman");

    enum list_style_type
    {
        list_style_type_none,
        list_style_type_circle,
        list_style_type_disc,
        list_style_type_square,
        list_style_type_armenian,
        list_style_type_cjk_ideographic,
        list_style_type_decimal,
        list_style_type_decimal_leading_zero,
        list_style_type_georgian,
        list_style_type_hebrew,
        list_style_type_hiragana,
        list_style_type_hiragana_iroha,
        list_style_type_katakana,
        list_style_type_katakana_iroha,
        list_style_type_lower_alpha,
        list_style_type_lower_greek,
        list_style_type_lower_latin,
        list_style_type_lower_roman,
        list_style_type_upper_alpha,
        list_style_type_upper_latin,
        list_style_type_upper_roman,
    };

    // ==========================================================
    // CSS Property: list-style-position
    // ==========================================================
    inline constexpr auto list_style_position_strings = split_css_values<2>("inside;outside");

    enum list_style_position
    {
        list_style_position_inside,
        list_style_position_outside
    };

    // ==========================================================
    // CSS Property: font-variant
    // ==========================================================
    inline constexpr auto vertical_align_strings =
        split_css_values<8>("baseline;sub;super;top;text-top;middle;bottom;text-bottom");

    enum vertical_align
    {
        va_baseline,
        va_sub,
        va_super,
        va_top,
        va_text_top,
        va_middle,
        va_bottom,
        va_text_bottom
    };

    // ==========================================================
    // CSS Property: border-width
    // ==========================================================
    inline constexpr auto border_width_strings = split_css_values<3>("thin;medium;thick");

    enum border_width
    {
        border_width_thin,
        border_width_medium,
        border_width_thick
    };

    constexpr float                border_width_thin_value   = 1;
    constexpr float                border_width_medium_value = 3;
    constexpr float                border_width_thick_value  = 5;
    constexpr std::array<float, 3> border_width_values       = {border_width_thin_value, border_width_medium_value,
                                                                border_width_thick_value};

    // ==========================================================
    // CSS Property: border-style
    // ==========================================================
    inline constexpr auto border_style_strings =
        split_css_values<10>("none;hidden;dotted;dashed;solid;double;groove;ridge;inset;outset");

    enum border_style
    {
        border_style_none,
        border_style_hidden,
        border_style_dotted,
        border_style_dashed,
        border_style_solid,
        border_style_double,
        border_style_groove,
        border_style_ridge,
        border_style_inset,
        border_style_outset
    };

    // ==========================================================
    // CSS Property: float
    // ==========================================================
    inline constexpr auto element_float_strings = split_css_values<3>("none;left;right");

    enum element_float
    {
        float_none,
        float_left,
        float_right
    };

    // ==========================================================
    // CSS Property: clear
    // ==========================================================
    inline constexpr auto element_clear_strings = split_css_values<4>("none;left;right;both");

    enum element_clear
    {
        clear_none,
        clear_left,
        clear_right,
        clear_both
    };

    // ==========================================================
    // CSS units
    // ==========================================================
    inline constexpr auto css_units_strings =
        split_css_values<17>("none;%;in;cm;mm;em;ex;pt;pc;px;vw;vh;vmin;vmax;rem;ch");

    enum css_units : uint8_t // see css_length
    {
        css_units_none,
        css_units_percentage,
        css_units_in,
        css_units_cm,
        css_units_mm,
        css_units_em,
        css_units_ex,
        css_units_pt,
        css_units_pc,
        css_units_px,
        css_units_vw,
        css_units_vh,
        css_units_vmin,
        css_units_vmax,
        css_units_rem,
        css_units_ch,
    };

    // ==========================================================
    // CSS Property: background-attachment
    // ==========================================================
    inline constexpr auto background_attachment_strings = split_css_values<2>("scroll;fixed");

    enum background_attachment
    {
        background_attachment_scroll,
        background_attachment_fixed
    };

    // ==========================================================
    // CSS Property: background-repeat
    // ==========================================================
    inline constexpr auto background_repeat_strings = split_css_values<4>("repeat;repeat-x;repeat-y;no-repeat");

    enum background_repeat
    {
        background_repeat_repeat,
        background_repeat_repeat_x,
        background_repeat_repeat_y,
        background_repeat_no_repeat
    };

    // ==========================================================
    // CSS Property: background-attachment
    // https://drafts.csswg.org/css-box-4/#typedef-visual-box
    // ==========================================================
    inline constexpr auto background_box_strings = split_css_values<3>("border-box;padding-box;content-box");

    enum background_box
    {
        background_box_border,
        background_box_padding,
        background_box_content
    };

    // ==========================================================
    // CSS Property: background-position
    // ==========================================================
    inline constexpr auto background_position_strings = split_css_values<5>("left;right;top;bottom;center");
    inline constexpr std::array<float, 5> background_position_percentages = {0, 100, 0, 100, 50};

    enum background_position
    {
        background_position_left,
        background_position_right,
        background_position_top,
        background_position_bottom,
        background_position_center,
    };

    // ==========================================================
    // CSS Property: position
    // ==========================================================
    inline constexpr auto element_position_strings = split_css_values<4>("static;relative;absolute;fixed");

    enum element_position
    {
        element_position_static,
        element_position_relative,
        element_position_absolute,
        element_position_fixed,
    };

    // ==========================================================
    // CSS Property: align
    // ==========================================================
    inline constexpr auto text_align_strings = split_css_values<4>("left;right;center;justify");

    enum text_align
    {
        text_align_left,
        text_align_right,
        text_align_center,
        text_align_justify
    };

    // ==========================================================
    // CSS Property: text-transform
    // ==========================================================
    inline constexpr auto text_transform_strings = split_css_values<4>("none;capitalize;uppercase;lowercase");

    enum text_transform
    {
        text_transform_none,
        text_transform_capitalize,
        text_transform_uppercase,
        text_transform_lowercase
    };

    // ==========================================================
    // CSS Property: white-space
    // ==========================================================
    inline constexpr auto white_space_strings = split_css_values<5>("normal;nowrap;pre;pre-line;pre-wrap");

    enum white_space
    {
        white_space_normal,
        white_space_nowrap,
        white_space_pre,
        white_space_pre_line,
        white_space_pre_wrap
    };

    // ==========================================================
    // CSS Property: overflow
    // ==========================================================
    inline constexpr auto overflow_strings = split_css_values<6>("visible;hidden;scroll;auto;no-display;no-content");

    enum overflow
    {
        overflow_visible,
        overflow_hidden,
        overflow_scroll,
        overflow_auto,
        overflow_no_display,
        overflow_no_content
    };

    // ==========================================================
    // CSS Property: background-size
    // ==========================================================
    inline constexpr auto background_size_strings = split_css_values<3>("auto;cover;contain");

    enum background_size
    {
        background_size_auto, // must be first, see parse_bg_size
        background_size_cover,
        background_size_contain,
    };

    // ==========================================================
    // CSS Property: visibility
    // ==========================================================
    inline constexpr auto visibility_strings = split_css_values<3>("visible;hidden;collapse");

    enum visibility
    {
        visibility_visible,
        visibility_hidden,
        visibility_collapse,
    };

    // ==========================================================
    // CSS Property: border-collapse
    // ==========================================================
    inline constexpr auto border_collapse_strings = split_css_values<2>("collapse;separate");

    enum border_collapse
    {
        border_collapse_collapse,
        border_collapse_separate,
    };

    // ==========================================================
    // CSS Property: content
    // ==========================================================
    inline constexpr auto content_property_strings =
        split_css_values<6>("none;normal;open-quote;close-quote;no-open-quote;no-close-quote");

    enum content_property
    {
        content_property_none,
        content_property_normal,
        content_property_open_quote,
        content_property_close_quote,
        content_property_no_open_quote,
        content_property_no_close_quote,
    };

    // ==========================================================
    // CSS Property: appearance
    // ==========================================================
    inline constexpr auto appearance_strings = split_css_values<17>(
        "none;auto;menulist-button;textfield;button;checkbox;listbox;menulist;meter;progress-bar;push-button;radio;"
        "searchfield;slider-horizontal;square-button;textarea");

    enum appearance
    {
        appearance_none,
        appearance_auto,
        appearance_menulist_button,
        appearance_textfield,
        appearance_button,
        appearance_checkbox,
        appearance_listbox,
        appearance_menulist,
        appearance_meter,
        appearance_progress_bar,
        appearance_push_button,
        appearance_radio,
        appearance_searchfield,
        appearance_slider_horizontal,
        appearance_square_button,
        appearance_textarea,
    };

    // ==========================================================
    // CSS Property: box-sizing
    // ==========================================================
    inline constexpr auto box_sizing_strings = split_css_values<2>("content-box;border-box");

    enum box_sizing
    {
        box_sizing_content_box,
        box_sizing_border_box,
    };

    // ==========================================================
    // CSS Property: media-type
    // ==========================================================
    // https://drafts.csswg.org/mediaqueries/#media-types
    // User agents must recognize the following media types as valid, but must make them match nothing.
#define deprecated_media_type_strings "tty;tv;projection;handheld;braille;embossed;aural;speech"
    inline constexpr auto media_type_strings = split_css_values<11>("all;print;screen;" deprecated_media_type_strings);

    enum media_type
    {
        media_type_unknown,
        media_type_all,
        media_type_print,
        media_type_screen,
        media_type_first_deprecated
    };

    // ==========================================================
    // CSS Property: flex-direction
    // ==========================================================
    inline constexpr auto flex_direction_strings = split_css_values<4>("row;row-reverse;column;column-reverse");

    enum flex_direction
    {
        flex_direction_row,
        flex_direction_row_reverse,
        flex_direction_column,
        flex_direction_column_reverse
    };

    // ==========================================================
    // CSS Property: flex-wrap
    // ==========================================================
    inline constexpr auto flex_wrap_strings = split_css_values<3>("nowrap;wrap;wrap-reverse");

    enum flex_wrap
    {
        flex_wrap_nowrap,
        flex_wrap_wrap,
        flex_wrap_wrap_reverse
    };

    // ==========================================================
    // CSS Property: flex-justify-content
    // ==========================================================
    inline constexpr auto flex_justify_content_strings = split_css_values<12>(
        "normal;flex-start;flex-end;center;space-between;space-around;start;end;left;right;space-evenly;stretch");

    enum flex_justify_content
    {
        flex_justify_content_normal,
        flex_justify_content_flex_start,
        flex_justify_content_flex_end,
        flex_justify_content_center,
        flex_justify_content_space_between,
        flex_justify_content_space_around,
        flex_justify_content_start,
        flex_justify_content_end,
        flex_justify_content_left,
        flex_justify_content_right,
        flex_justify_content_space_evenly,
        flex_justify_content_stretch,
    };

#define self_position_vals "center;start;end;self-start;self-end;flex-start;flex-end"
    inline constexpr auto self_position_strings = split_css_values<7>(self_position_vals);
    inline constexpr auto flex_align_items_strings =
        split_css_values<11>("auto;normal;stretch;baseline;" self_position_vals);

    enum flex_align_items
    {
        flex_align_items_auto, // used for align-self property only
        flex_align_items_normal,
        flex_align_items_stretch,
        flex_align_items_baseline,

        flex_align_items_center,
        flex_align_items_start,
        flex_align_items_end,
        flex_align_items_self_start,
        flex_align_items_self_end,
        flex_align_items_flex_start,
        flex_align_items_flex_end,

        flex_align_items_first  = 0x100,
        flex_align_items_last   = 0x200,
        flex_align_items_unsafe = 0x400,
        flex_align_items_safe   = 0x800,
    };

    // ==========================================================
    // CSS Property: flex-align-content
    // ==========================================================
    inline constexpr auto flex_align_content_strings =
        split_css_values<8>("flex-start;start;flex-end;end;center;space-between;space-around;stretch");

    enum flex_align_content
    {
        flex_align_content_flex_start,
        flex_align_content_start,
        flex_align_content_flex_end,
        flex_align_content_end,
        flex_align_content_center,
        flex_align_content_space_between,
        flex_align_content_space_around,
        flex_align_content_stretch
    };

    // ==========================================================
    // CSS Property: flex-basis
    // ==========================================================
    inline constexpr auto flex_basis_strings = split_css_values<5>("auto;content;fit-content;min-content;max-content");

    enum flex_basis
    {
        flex_basis_auto,
        flex_basis_content,
        flex_basis_fit_content,
        flex_basis_min_content,
        flex_basis_max_content,
    };

    // ==========================================================
    // CSS Property: caption-side
    // ==========================================================
    inline constexpr auto caption_side_strings = split_css_values<2>("top;bottom");

    enum caption_side
    {
        caption_side_top,
        caption_side_bottom
    };

    // ==========================================================
    // CSS Property: text-decoration-line
    // ==========================================================
    inline constexpr auto style_text_decoration_line_strings =
        split_css_values<4>("none;underline;overline;line-through");

    enum text_decoration_line
    {
        text_decoration_line_none         = 0x00,
        text_decoration_line_underline    = 0x01,
        text_decoration_line_overline     = 0x02,
        text_decoration_line_line_through = 0x04,
    };

    // ==========================================================
    // CSS Property: text-decoration-style
    // ==========================================================
    inline constexpr auto style_text_decoration_style_strings = split_css_values<5>("solid;double;dotted;dashed;wavy");

    enum text_decoration_style
    {
        text_decoration_style_solid,
        text_decoration_style_double,
        text_decoration_style_dotted,
        text_decoration_style_dashed,
        text_decoration_style_wavy,
        text_decoration_style_max,
    };

    // ==========================================================
    // CSS Property: text-decoration-thickness
    // ==========================================================
    inline constexpr auto style_text_decoration_thickness_strings = split_css_values<2>("auto;from-font");

    enum text_decoration_thickness
    {
        text_decoration_thickness_auto,
        text_decoration_thickness_from_font,
    };

    // ==========================================================
    // CSS Property: text-emphasis-position
    // ==========================================================
    inline constexpr auto style_text_emphasis_position_strings = split_css_values<4>("over;under;left;right");

    enum text_emphasis_position
    {
        text_emphasis_position_over  = 0x00,
        text_emphasis_position_under = 0x01,
        text_emphasis_position_left  = 0x02,
        text_emphasis_position_right = 0x04,
    };

    // ==========================================================
    // CSS Property: radial-extent
    // ==========================================================
    inline constexpr auto radial_extent_strings =
        split_css_values<4>("closest-corner;closest-side;farthest-corner;farthest-side");
    enum radial_extent_t
    {
        radial_extent_none,
        radial_extent_closest_corner,
        radial_extent_closest_side,
        radial_extent_farthest_corner,
        radial_extent_farthest_side,
    };

    // ==========================================================
    // CSS Property: color-space
    // ==========================================================
    inline constexpr auto color_space_strings = split_css_values<15>(
        "srgb;srgb-linear;display-p3;a98-rgb;prophoto-rgb;rec2020;lab;oklab;xyz;xyz-d50;xyz-d65;hsl;hwb;lch;oklch");

    enum color_space_t
    {
        color_space_none,

        // rectangular-color-space
        color_space_srgb,
        color_space_srgb_linear,
        color_space_display_p3,
        color_space_a98_rgb,
        color_space_prophoto_rgb,
        color_space_rec2020,
        color_space_lab,
        color_space_oklab,
        color_space_xyz,
        color_space_xyz_d50,
        color_space_xyz_d65,

        // polar-color-space
        color_space_hsl,
        color_space_polar_start = color_space_hsl,
        color_space_hwb,
        color_space_lch,
        color_space_oklch,
    };

    // ==========================================================
    // CSS Property: hue-interpolation
    // ==========================================================
    inline constexpr auto hue_interpolation_strings = split_css_values<4>("shorter;longer;increasing;decreasing");

    enum hue_interpolation_t
    {
        hue_interpolation_none,
        hue_interpolation_shorter,
        hue_interpolation_longer,
        hue_interpolation_increasing,
        hue_interpolation_decreasing
    };

} // namespace litehtml

#endif // LITEHTML_CSS_VALUES_H
