// Blitz renderer prototype: the Renderer contract of WidgetHost on the Rust
// static library in renderer-blitz/ (repo root, shared with the iOS/macOS and
// Android demos) that lays out the validated DOM with blitz-dom and paints it
// on the CPU with vello_cpu into a premultiplied RGBA8 buffer. No WebView, no
// script, no network: the document only ever sees validated RenderOps and
// `data:` images. Dev tooling for docs/blitz-experiment.md, not a product
// path; the C++ twin of BlitzRenderer.swift / BlitzRenderer.kt, minus the view
// — the owner takes the frames (`paint()`, `onPaint`) and feeds pointers
// (`touch()`), which on tdesktop would be a QWidget's paintEvent and mouse
// events.
//
// Threading: the Rust document is not locked on its side, so every `twb_*`
// call goes through one mutex here; the host thread (messages), the clock
// thread (frames and paints) and the owner's thread (touches, paints) take
// turns. Messages to the host are dispatched outside the lock.
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

struct twb_renderer;

namespace twidget {

class BlitzRenderer final : public Renderer {
public:
    /// One raster of the document: `width × height` physical px, premultiplied RGBA8, row-major.
    struct Frame {
        uint32_t width = 0, height = 0;
        std::vector<uint8_t> rgba;
        double paintMs = 0;
        bool empty() const { return rgba.empty(); }
    };

    /// Plug into WidgetHost: `WidgetHost(..., BlitzRenderer::factory(dpr))`.
    static RendererFactory factory(double dpr = 1);

    /// `width` in CSS px, `dpr` = device pixel ratio, `theme` "light" | "dark".
    BlitzRenderer(const std::string& theme, double width, double dpr);
    ~BlitzRenderer() override;

    /// False when the Rust side could not create the document (`load()` reports it as a diag).
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
    /// Paint statistics (`blitz-paint` in the demos).
    int paints() const { return paints_; }
    double lastPaintMs() const { return lastPaintMs_; }
    /// Creating the document; the first one in the process also scans the system fonts.
    double initMs() const { return initMs_; }
    double dpr() const { return dpr_; }

    /// Diagnostics: the font collection (family count, emoji mapping).
    static std::string fontReport();

private:
    void dispatch(char* messages);
    void schedulePaint();
    void requestFrame();
    void clockLoop();
    static double nowMs();

    twb_renderer* handle_ = nullptr;
    double dpr_;
    double initMs_ = 0;
    std::mutex mutex_;  // every twb_* call

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
