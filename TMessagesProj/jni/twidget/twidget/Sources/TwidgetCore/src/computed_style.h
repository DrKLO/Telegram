// The `cs` query (getComputedStyle): what the fork computed for an element, written out as Chromium
// writes it, since widgets and widget-sandbox's conformance set compare against the browser's strings.
#pragma once

#include "twidget/json.h"

#include <litehtml.h>

#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace twlite {

/// What the fork does not compute and the renderer knows from its own tree.
struct ComputedExtras {
    /// The element's animated values (property, value), a later one winning: the fork keeps the
    /// declarations the widget wrote, which is what properties it does not compute are read from.
    const std::vector<std::pair<std::string, std::string>>* animated = nullptr;
};

/// Every property a widget may read, by its CSS name. `pseudo` is "::before" or "::after" (or the
/// one-colon forms), or empty for the element itself.
twidget::Json computed_style(const litehtml::element::ptr& el, std::string_view pseudo, const ComputedExtras& extras);

/// A custom property (`--name`) as the cascade left it, inherited ones included; "" when unset.
std::string computed_custom_property(const litehtml::element::ptr& el, const std::string& name);

/// The `touch-action` an element declares, from any stylesheet; "auto" when it declares none.
std::string computed_touch_action(const litehtml::element::ptr& el);

/// The `pointer-events` an element has, inherited from its ancestors; "auto" when none declares one.
std::string computed_pointer_events(const litehtml::element::ptr& el);

} // namespace twlite
