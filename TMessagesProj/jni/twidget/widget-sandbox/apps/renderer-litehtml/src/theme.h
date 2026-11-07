// The theme variables the renderer sets on the widget's root (docs/sdk-v1.md §2.3, §2.4, §3.4): the C++ twin of
// `themeVariables` in packages/core/src/shared/sdk-policy.ts, which the WebView renderers run. The host sends
// `{type:'theme', theme:{colorScheme, params, button, text}}` as given; this keeps the names of the fixed list and
// the values that match their grammar, completes the palette and the text tokens from the defaults, and the
// renderer writes the result inline and !important on <html>, under the profile's UA layer (tw_ua_stylesheet.h).
#pragma once

#include "twidget/json.h"

#include <string>
#include <utility>
#include <vector>

namespace twlite::theme {

/// `--tg-theme-*` (15, always), `--tg-color-scheme`, the `--tg-button-*` the client passed, `--tg-text-*` (3, always).
std::vector<std::pair<std::string, std::string>> variables(const twidget::Json& theme);

/// `--tg-content-safe-area-inset-top|right|bottom|left` and `--tg-content-safe-area-corner-left|right` (6, always; the
/// twin of `contentSafeAreaVariables`, docs/sdk-v1.md §2.17): the host's `{type:'safeArea', inset:{top, right, bottom,
/// left, cornerLeft, cornerRight}}` in px, an inset in 0–64 and a corner in 0–200 with at most two decimals, else 0px.
std::vector<std::pair<std::string, std::string>> safe_area_variables(const twidget::Json& inset);

/// A name of the fixed list: the widget's own style ops on <html> cannot set it.
bool is_variable(const std::string& name);

/// `css` with every `var(--name[, fallback])` replaced by the value of `--name` in `vars`, else by its fallback
/// (a var() with neither stays as it is).
std::string substitute(const std::string& css, const std::vector<std::pair<std::string, std::string>>& vars);

/// The UA layer for litehtml's master CSS: every `:where(X)` becomes `X`. litehtml sorts the master CSS by specificity
/// and applies all of it before the author's, so its own `body {margin: 8px}` would beat a zero-specificity rule that
/// comes later, while no author rule can lose to the master CSS anyway. A `:where()` of a selector list followed by
/// more of its compound selector stays (it cannot be unwrapped); the template has none.
std::string unwrap_where(const std::string& css);

}  // namespace twlite::theme
