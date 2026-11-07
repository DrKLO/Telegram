#include "twidget/litehtml_renderer.h"

#include "twidget/json.h"
#include "twidget_litehtml.h"

#include <chrono>
#include <cstring>

namespace twidget {

namespace {
constexpr double kTickSeconds = 1.0 / 60;
}

RendererFactory LitehtmlRenderer::factory(double dpr) {
    return [dpr](std::shared_ptr<SandboxResources>, const std::string& theme, double width) -> std::unique_ptr<Renderer> {
        return std::make_unique<LitehtmlRenderer>(theme, width, dpr);
    };
}

double LitehtmlRenderer::nowMs() {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

LitehtmlRenderer::LitehtmlRenderer(const std::string& theme, double width, double dpr) : dpr_(dpr) {
    double t0 = nowMs();
    handle_ = twl_create(width, dpr, theme.c_str());
    initMs_ = nowMs() - t0;
    clock_ = std::thread([this] { clockLoop(); });
}

LitehtmlRenderer::~LitehtmlRenderer() {
    destroy();
}

std::string LitehtmlRenderer::stats() {
    std::lock_guard<std::mutex> l(mutex_);
    if (!handle_ || destroyed_) return "{}";
    char* s = twl_stats(handle_);
    std::string out = s ? s : "{}";
    twl_string_free(s);
    return out;
}

std::string LitehtmlRenderer::html() {
    std::lock_guard<std::mutex> l(mutex_);
    if (!handle_ || destroyed_) return "";
    char* s = twl_html(handle_);
    std::string out = s ? s : "";
    twl_string_free(s);
    return out;
}

void LitehtmlRenderer::load() {
    if (!handle_) {
        if (onDiag) onDiag("renderer", "litehtml renderer failed to initialise");
        return;
    }
    if (onDiag) onDiag("litehtml-init", Json::number(initMs_) + " ms");
    if (onMessage) onMessage("{\"type\":\"ready\"}");
    schedulePaint();
}

void LitehtmlRenderer::send(const std::string& json) {
    if (!handle_) return;
    if (json.rfind("{\"type\":\"requestFrame\"", 0) == 0) {
        requestFrame();
        return;
    }
    if (json.rfind("{\"type\":\"frozen\"", 0) == 0) {
        std::lock_guard<std::mutex> l(clockMutex_);
        frozen_ = true;
    }
    char* out;
    bool dirty;
    {
        std::lock_guard<std::mutex> l(mutex_);
        if (destroyed_) return;
        out = twl_receive(handle_, json.c_str());
        dirty = twl_dirty(handle_);
    }
    dispatch(out);
    if (dirty) schedulePaint();
}

void LitehtmlRenderer::setTheme(const std::string& theme) {
    if (!handle_) return;
    char* out;
    bool dirty;
    {
        std::lock_guard<std::mutex> l(mutex_);
        if (destroyed_) return;
        out = twl_set_theme(handle_, theme.c_str());
        dirty = twl_dirty(handle_);
    }
    dispatch(out);
    if (dirty) schedulePaint();
}

void LitehtmlRenderer::setWidth(double width) {
    if (!handle_) return;
    char* out;
    bool dirty;
    {
        std::lock_guard<std::mutex> l(mutex_);
        if (destroyed_) return;
        out = twl_set_width(handle_, width);
        dirty = twl_dirty(handle_);
    }
    dispatch(out);
    if (dirty) schedulePaint();
}

void LitehtmlRenderer::touch(int action, double x, double y, int pointer, bool primary) {
    if (!handle_) return;
    char* out;
    bool dirty;
    {
        std::lock_guard<std::mutex> l(mutex_);
        if (destroyed_) return;
        out = twl_touch(handle_, action, x, y, pointer, primary);
        dirty = twl_dirty(handle_);
    }
    dispatch(out);
    if (dirty) schedulePaint();
}

void LitehtmlRenderer::destroy() {
    {
        std::lock_guard<std::mutex> l(clockMutex_);
        if (stopClock_) return;
        stopClock_ = true;
    }
    clockCv_.notify_all();
    if (clock_.joinable()) clock_.join();
    std::lock_guard<std::mutex> l(mutex_);
    destroyed_ = true;
    twl_destroy(handle_);
    handle_ = nullptr;
}

/// A JSON array of messages from the core; each one goes to the host separately.
void LitehtmlRenderer::dispatch(char* messages) {
    if (!messages) return;
    std::string text = messages;
    twl_string_free(messages);
    if (!onMessage) return;
    auto array = Json::parse(text);
    if (!array || !array->isArray()) return;
    for (const Json& msg : array->arr()) {
        if (msg["type"].asString() == "violation" && onDiag) onDiag("renderer-violation", msg["detail"].asString());
        onMessage(msg.dump());
    }
}

LitehtmlRenderer::Frame LitehtmlRenderer::paint() {
    Frame frame;
    std::lock_guard<std::mutex> l(mutex_);
    if (!handle_ || destroyed_) return frame;
    uint32_t w = 0, h = 0;
    twl_size(handle_, &w, &h);
    if (w == 0 || h == 0) return frame;
    frame.width = w;
    frame.height = h;
    frame.rgba.resize(size_t(w) * h * 4);
    double ms = twl_render(handle_, frame.rgba.data(), frame.rgba.size());
    if (ms < 0) {
        frame = Frame{};
        if (onDiag) onDiag("renderer", "litehtml paint failed (" + Json::number(ms) + ")");
        return frame;
    }
    frame.paintMs = ms;
    paints_++;
    lastPaintMs_ = ms;
    return frame;
}

// ───────────── frames and paints, coalesced into ticks ─────────────

void LitehtmlRenderer::requestFrame() {
    {
        std::lock_guard<std::mutex> l(clockMutex_);
        if (frozen_ || frameRequested_) return;
        frameRequested_ = true;
    }
    clockCv_.notify_all();
}

void LitehtmlRenderer::schedulePaint() {
    {
        std::lock_guard<std::mutex> l(clockMutex_);
        if (paintScheduled_ || stopClock_) return;
        paintScheduled_ = true;
    }
    clockCv_.notify_all();
}

void LitehtmlRenderer::clockLoop() {
    for (;;) {
        bool frame, paint;
        {
            std::unique_lock<std::mutex> l(clockMutex_);
            clockCv_.wait(l, [&] { return stopClock_ || frameRequested_ || paintScheduled_; });
            if (stopClock_) return;
            // One tick: like rAF before the browser's paint, the callback first, the raster after.
            clockCv_.wait_for(l, std::chrono::duration<double>(kTickSeconds), [&] { return stopClock_; });
            if (stopClock_) return;
            frame = frameRequested_;
            paint = paintScheduled_;
            frameRequested_ = false;
            paintScheduled_ = false;
        }
        if (frame && onMessage) onMessage("{\"type\":\"frame\",\"time\":" + Json::number(nowMs()) + "}");
        if (paint) {
            Frame f = this->paint();
            if (!f.empty() && onPaint) onPaint(f);
        }
    }
}

} // namespace twidget
