// Small CSS helpers shared by the shadow tree, the animation engine and the canvas. None of them
// is a CSS parser: they read the narrow, already sanitised text the validator forwards.
#pragma once

#include "display_list.h"
#include "twidget/json.h"

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

/// A character that continues a CSS identifier.
bool ident_char(char c);
/// Past what a text rewrite must not look into when it starts at `i`: a string, a comment, an escape or an
/// unquoted url(…). `i` itself when none starts there.
size_t skip_opaque(std::string_view css, size_t i);

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
/// Only 2D functions in px, % and angle units, and calc() of px and %; nullopt for anything else (3D, em, var()).
std::optional<Affine> parse_transform(std::string_view text, float w, float h);
/// The individual `translate`, `rotate` and `scale` properties, likewise.
std::optional<Affine> parse_translate(std::string_view text, float w, float h);
std::optional<Affine> parse_rotate(std::string_view text);
std::optional<Affine> parse_scale(std::string_view text);
/// `transform-origin` within a `w` × `h` box; nullopt when it is not 2D px, % or keywords.
std::optional<std::pair<float, float>> parse_origin(std::string_view text, float w, float h);
/// `opacity`: a number or a percentage, clamped to 0..1.
std::optional<float> parse_opacity(std::string_view text);

/// The custom properties the host's `theme` message sets on the widget's root (widget-sandbox
/// docs/sdk-v1.md §2.3, §2.4): the palette completed from the scheme's defaults, `--tg-color-scheme`,
/// each button token whose value passes its grammar, and the text tokens completed from the reference set.
/// Twin of `themeVariables` in sdk-policy.ts.
std::vector<std::pair<std::string, std::string>> theme_variables(const twidget::Json& theme);
/// The `--tg-content-safe-area-*` properties of the host's `safeArea` message (docs/sdk-v1.md §2.17): all six, each
/// inset 0–64 and corner 0–200 px at two decimals, anything else 0px. Twin of `contentSafeAreaVariables` in sdk-policy.ts.
std::vector<std::pair<std::string, std::string>> content_safe_area_variables(const twidget::Json& inset);
/// A name `theme_variables` or `content_safe_area_variables` may set: the widget's own style ops cannot set it on the root.
bool is_theme_variable(std::string_view name);

/// `css` with every `var(--name[, fallback])` replaced by the value of `--name` in `vars`, else by its
/// fallback; a var() with neither stays.
std::string substitute_variables(std::string_view css, const std::vector<std::pair<std::string, std::string>>& vars);
/// Every `:where(X)` as `X`, for the profile's UA layer in litehtml's master CSS: litehtml sorts the master
/// CSS by specificity, so its own `body {margin: 8px}` would beat a later zero-specificity rule, while no
/// author rule can lose to the master CSS anyway.
std::string unwrap_where(std::string_view css);

} // namespace twlite::css
