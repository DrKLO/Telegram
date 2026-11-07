// The renderer as WidgetHost sees it: the JSON message contract of
// docs/protocol.md (host → renderer: ops / query / listen / preventDefault /
// requestFrame / theme / safeArea / policy / frozen; renderer → host: ready /
// event / gesture / result / size / resize / frame / media / limit /
// violation) behind a C++ interface; a renderer reports the paint limit
// through `limitExceeded`. The
// desktop copy of Renderer.swift / Renderer.kt: on tdesktop the product
// renderer would be the Web App window; the Blitz prototype
// (twidget/blitz_renderer.h) plugs in through WidgetHost's factory.
//
// Threading: WidgetHost calls every member on its host thread (the stand-in
// for the UI thread). `onMessage` may be invoked from any thread — from inside
// `send` for a synchronous answer, or from a renderer's own clock for frames —
// and WidgetHost serialises it.
#pragma once

#include "twidget/resources.h"

#include <functional>
#include <memory>
#include <optional>
#include <string>

namespace twidget {

/// The paint limits a renderer enforces (packages/core/src/shared/paint-policy.ts): at most kMaxHeavyBoxes heavy boxes
/// at once (a blurred box-shadow, a filter, a backdrop-filter, opacity below 1, or an animation of one of them), of
/// which at most kMaxBackdropBoxes with backdrop-filter.
enum class WidgetPaintLimit {
    Paint,    ///< "paint-limit": more than kMaxHeavyBoxes heavy boxes; the count is the heavy boxes'
    Backdrop, ///< "backdrop-limit": more than kMaxBackdropBoxes boxes with backdrop-filter; the count is theirs
};
constexpr int kMaxHeavyBoxes = 16;
constexpr int kMaxBackdropBoxes = 2;

inline const char* paintLimitCode(WidgetPaintLimit code) { return code == WidgetPaintLimit::Backdrop ? "backdrop-limit" : "paint-limit"; }

inline std::optional<WidgetPaintLimit> paintLimitFromCode(const std::string& code) {
    if (code == "paint-limit") return WidgetPaintLimit::Paint;
    if (code == "backdrop-limit") return WidgetPaintLimit::Backdrop;
    return std::nullopt;
}

/// The reason the host stops the widget with, the same on every platform (`paintLimitMessage` in paint-policy.ts).
inline std::string paintLimitMessage(WidgetPaintLimit code, int count) {
    return code == WidgetPaintLimit::Backdrop
        ? "Paint limit exceeded: " + std::to_string(count) + " boxes with backdrop-filter (max " + std::to_string(kMaxBackdropBoxes) + ")"
        : "Paint limit exceeded: " + std::to_string(count) + " boxes with shadows, filters or transparency (max " + std::to_string(kMaxHeavyBoxes) + ")";
}

class Renderer {
public:
    virtual ~Renderer() = default;

    /// Renderer → host messages (JSON).
    std::function<void(const std::string& json)> onMessage;
    std::function<void(const std::string& kind, const std::string& detail)> onDiag;

    /// Starts the renderer; it announces itself with `{type:'ready'}`.
    virtual void load() = 0;
    /// Host → renderer message (JSON).
    virtual void send(const std::string& json) = 0;
    /// The chat theme changed (`prefers-color-scheme` inside the renderer).
    virtual void setTheme(const std::string& theme) = 0;
    /// Width of the bubble in CSS px changed.
    virtual void setWidth(double width) = 0;
    virtual void destroy() = 0;

    /// A renderer that counts heavy boxes (docs/decisions.md "Heavy boxes", the rule and the cases in
    /// packages/core/tests/fixtures/paint-policy.json) reports a passed limit here, with the count: before it draws that
    /// frame, and once. WidgetHost stops the widget as for memory or CPU (`WidgetDiagnostics::dead` =
    /// paintLimitMessage, the diagnostic "paint-limit" or "backdrop-limit"); the renderer draws the stopped widget
    /// without the effects (no blurred shadow, filter, backdrop-filter or transparency; animations stopped). Under
    /// "compat" the host ignores it.
    void limitExceeded(WidgetPaintLimit code, int count) {
        if (onMessage) onMessage(std::string("{\"type\":\"limit\",\"code\":\"") + paintLimitCode(code) + "\",\"count\":" + std::to_string(count < 0 ? 0 : count) + "}");
    }
};

/// Builds a renderer for one widget: the shared resources, the chat theme and the width in CSS px.
using RendererFactory = std::function<std::unique_ptr<Renderer>(std::shared_ptr<SandboxResources> resources, const std::string& theme, double width)>;

} // namespace twidget
