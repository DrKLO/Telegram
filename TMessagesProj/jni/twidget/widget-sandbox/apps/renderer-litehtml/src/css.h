// Small CSS helpers shared by the shadow tree, the animation engine and the canvas. None of them
// is a CSS parser: they read the narrow, already sanitised text the validator forwards.
#pragma once

#include "display_list.h"

#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace twlite::css {

std::string trim(std::string_view s);
std::string lower(std::string s);
bool iequals(std::string_view a, std::string_view b);
bool starts_with(std::string_view s, std::string_view prefix);
bool ends_with(std::string_view s, std::string_view suffix);
/// A finite double from the whole of `s`, or nullopt.
std::optional<double> number(std::string_view s);

/// Splits at `separator` outside parentheses and quotes (`a, rgb(1,2,3)` is two parts).
std::vector<std::string> split_top_level(std::string_view text, char separator);

struct Declaration {
    std::string name, value;
    bool important = false;
};
/// An inline `style` text as declarations; invalid names are dropped, a later duplicate wins.
std::vector<Declaration> parse_declarations(std::string_view text);

/// What may be written into the HTML the renderer generates. The validator already guarantees
/// these; a name holding `;`, `"` or `>` would otherwise break out of its attribute or declaration.
bool is_tag_name(std::string_view name);
bool is_attribute_name(std::string_view name);
bool is_property_name(std::string_view name);

/// Replaces every `light-dark(a, b)` with the side the theme picks: litehtml knows no such function.
std::string resolve_light_dark(std::string_view css, bool dark);

/// `1.5s`, `200ms` or a `calc()` of times, in seconds.
std::optional<double> parse_time(std::string_view text);

/// A CSS colour: hex, rgb[a](), hsl[a](), a named colour or `transparent`.
bool parse_color(std::string_view text, Color& out);
/// Rewrites every colour in `text` as `rgba(r, g, b, a)`, so two values interpolate number by number.
std::string normalize_colors(std::string_view text);

/// Shortest decimal form: integers without a point, at most `digits` fractional digits.
std::string format_number(double value, int digits = 4);

/// The `font` shorthand as the canvas and `measureText` read it.
struct Font {
    std::string family = "sans-serif";
    float size = 10;
    int weight = 400;
    bool italic = false;
};
Font parse_font(std::string_view font);

/// A `transform` list as one matrix for a box of `w` × `h` (percentages), about the box's top left.
/// Only 2D functions in px, % and angle units; nullopt for anything else (3D, em, var(), calc()).
std::optional<Affine> parse_transform(std::string_view text, float w, float h);
/// The individual `translate`, `rotate` and `scale` properties, likewise.
std::optional<Affine> parse_translate(std::string_view text, float w, float h);
std::optional<Affine> parse_rotate(std::string_view text);
std::optional<Affine> parse_scale(std::string_view text);
/// `transform-origin` within a `w` × `h` box; nullopt when it is not 2D px, % or keywords.
std::optional<std::pair<float, float>> parse_origin(std::string_view text, float w, float h);
/// `opacity`: a number or a percentage, clamped to 0..1.
std::optional<float> parse_opacity(std::string_view text);

} // namespace twlite::css
