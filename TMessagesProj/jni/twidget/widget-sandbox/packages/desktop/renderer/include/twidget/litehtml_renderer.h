// litehtml renderer prototype: the Renderer contract of WidgetHost on the C++
// core in renderer-litehtml/ (repo root, shared with the iOS/macOS, Android and
// web demos) through its C ABI (include/twidget_litehtml.h — the same shape as
// the Blitz one, so this file is BlitzRenderer with `twl_*` for `twb_*`). The
// validated DOM is kept as a shadow tree on the C++ side and re-parsed by
// litehtml on every batch (docs/litehtml-renderer.md); fonts and pixels come
// from the CoreText/CoreGraphics backend, which is why -DTW_LITEHTML=ON is
// macOS-only today — a tdesktop host would paint the core's display list with
// QPainter instead. No WebView, no script, no network. Dev tooling, not a
// product path; same owner contract as twidget/blitz_renderer.h: rasters out
// (`paint()`, `onPaint`), pointers in (`touch()`), one mutex around the core.
#pragma once

#include "twidget/renderer.h"

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

struct twl_renderer;

namespace twidget {

class LitehtmlRenderer final : public Renderer {
public:
    /// One raster of the document: `width × height` physical px, premultiplied RGBA8, row-major.
    struct Frame {
        uint32_t width = 0, height = 0;
        std::vector<uint8_t> rgba;
        double paintMs = 0;
        bool empty() const { return rgba.empty(); }
    };

    /// Plug into WidgetHost: `WidgetHost(..., LitehtmlRenderer::factory(dpr))`.
    static RendererFactory factory(double dpr = 1);

    /// `width` in CSS px, `dpr` = device pixel ratio, `theme` "light" | "dark".
    LitehtmlRenderer(const std::string& theme, double width, double dpr);
    ~LitehtmlRenderer() override;

    /// False when the core could not create the document (`load()` reports it as a diag).
    bool ok() const { return handle_ != nullptr; }

    // Renderer (host thread)
    void load() override;
    void send(const std::string& json) override;
    void setTheme(const std::string& theme) override;
    void setWidth(double width) override;
    void destroy() override;

    /// A pointer in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. Any thread.
    void touch(int action, double x, double y, int pointer = 0, bool primary = true);
    /// Down + up at one point (a tap).
    void click(double x, double y) { touch(0, x, y); touch(2, x, y); }

    /// Paints the document now. Empty on failure.
    Frame paint();
    /// The frames the clock paints after `requestFrame` / document changes (the equivalent of the view's refresh).
    std::function<void(const Frame&)> onPaint;
    int paints() const { return paints_; }
    double lastPaintMs() const { return lastPaintMs_; }
    /// Creating the empty document.
    double initMs() const { return initMs_; }
    double dpr() const { return dpr_; }
    /// The last rebuild as JSON (`serializeMs`, `parseLayoutMs`, `passes`, `htmlBytes`, `builds`, `displayOps`).
    std::string stats();
    /// The HTML litehtml last parsed (diagnostics).
    std::string html();

private:
    void dispatch(char* messages);
    void schedulePaint();
    void requestFrame();
    void clockLoop();
    static double nowMs();

    twl_renderer* handle_ = nullptr;
    double dpr_;
    double initMs_ = 0;
    std::mutex mutex_;  // every twl_* call

    // The clock: one tick per display refresh at most, like CADisplayLink / Choreographer in the demos.
    std::mutex clockMutex_;
    std::condition_variable clockCv_;
    std::thread clock_;
    bool frameRequested_ = false;
    bool paintScheduled_ = false;
    bool stopClock_ = false;
    bool frozen_ = false;
    bool destroyed_ = false;
    std::atomic<int> paints_{0};
    std::atomic<double> lastPaintMs_{0};
};

} // namespace twidget
