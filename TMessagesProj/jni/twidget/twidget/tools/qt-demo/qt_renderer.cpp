#include "qt_renderer.h"

#include "twidget/json.h"

#include <QTransform>

#include <cmath>

using twidget::Json;

namespace {
constexpr double kTickSeconds = 1.0 / 60;
}

double QtRenderer::nowMs() {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

QtRenderer::QtRenderer(const std::string& theme, double width, double dpr, std::function<void(std::shared_ptr<QtFrame>)> onFrame)
    : dpr_(dpr), onFrame_(std::move(onFrame)) {
    core_ = std::make_unique<twlite::Renderer>(width, dpr, theme == "dark", backend_);
    core_->set_compositing(true);
    clock_ = std::thread([this] { clockLoop(); });
}

QtRenderer::~QtRenderer() { destroy(); }

void QtRenderer::load() {
    if (onMessage) onMessage("{\"type\":\"ready\"}");
    schedulePaint();
}

void QtRenderer::send(const std::string& json) {
    if (json.rfind("{\"type\":\"requestFrame\"", 0) == 0) {
        requestFrame();
        return;
    }
    if (json.rfind("{\"type\":\"frozen\"", 0) == 0) {
        std::lock_guard l(clockMutex_);
        frozen_ = true;
    }

    auto msg = Json::parse(json);
    if (!msg) {
        if (onDiag) onDiag("renderer-violation", "bad message");
        return;
    }
    run([&](std::vector<Json>& out) {
        try {
            out = core_->receive(*msg);
        } catch (const std::exception& e) {
            if (onDiag) onDiag("renderer", std::string("receive: ") + e.what());
        }
        lastBuildMs_ = core_->stats().parse_ms;
    });
}

template <class Call> void QtRenderer::run(Call call) {
    std::vector<Json> out;
    bool dirty, ticking;
    double wake;
    bool editorChanged = false;
    std::optional<twlite::Renderer::EditorSpec> editor;
    {
        std::lock_guard l(mutex_);
        if (destroyed_) return;
        call(out);
        dirty = core_->dirty();
        ticking = core_->wants_tick();
        wake = core_->next_tick_in();
        const twlite::Renderer::EditorSpec* spec = core_->editor();
        uint64_t generation = spec ? spec->generation : 0;
        if (generation != editorGeneration_) {
            editorGeneration_ = generation;
            editorChanged = true;
            if (spec) editor = *spec;
        }
    }
    if (editorChanged && onEditor) onEditor(std::move(editor));
    dispatch(std::move(out));
    setWake(wake);
    setTicking(ticking);
    if (dirty) schedulePaint();
}

void QtRenderer::setWake(double seconds) {
    {
        std::lock_guard l(clockMutex_);
        if (!std::isfinite(seconds)) {
            wakeAt_.reset();
            return;
        }
        // At least a frame away: a wake-up that is already due waits for the next tick anyway.
        wakeAt_ = std::chrono::steady_clock::now() +
                  std::chrono::duration_cast<std::chrono::steady_clock::duration>(std::chrono::duration<double>(std::max(seconds, kTickSeconds)));
    }
    clockCv_.notify_all();
}

void QtRenderer::setTheme(const std::string& theme) {
    run([&](std::vector<Json>& out) { core_->set_theme(theme == "dark", out); });
}

void QtRenderer::setWidth(double width) {
    run([&](std::vector<Json>& out) { core_->set_width(width, out); });
}

void QtRenderer::touch(int action, double x, double y, int pointer, bool primary, bool mouse, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->touch(action, x, y, pointer, primary, mouse, modifiers); });
}

void QtRenderer::hover(int action, double x, double y, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->hover(action, x, y, modifiers); });
}

void QtRenderer::wheel(double x, double y, double dx, double dy, int mode, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->wheel(x, y, dx, dy, mode, modifiers); });
}

void QtRenderer::key(const std::string& type, const std::string& key, const std::string& code, bool repeat, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->key(type, key, code, repeat, modifiers); });
}

void QtRenderer::setVisible(bool visible) {
    if (!visible) {
        std::lock_guard l(mutex_);
        if (!destroyed_) {
            core_->trim();
            backend_.trim();
        }
    }
    {
        std::lock_guard l(clockMutex_);
        visible_ = visible;
    }
    clockCv_.notify_all();
}

void QtRenderer::edit(uint64_t generation, const std::string& text) {
    run([&](std::vector<Json>& out) { out = core_->edit(generation, text); });
}

void QtRenderer::endEditing() {
    run([&](std::vector<Json>& out) { out = core_->end_editing(); });
}

void QtRenderer::setTicking(bool ticking) {
    {
        std::lock_guard l(clockMutex_);
        if (ticking_ == ticking) return;
        ticking_ = ticking;
    }
    if (ticking) clockCv_.notify_all();
}

void QtRenderer::destroy() {
    {
        std::lock_guard l(clockMutex_);
        if (stopClock_) return;
        stopClock_ = true;
    }
    clockCv_.notify_all();
    if (clock_.joinable()) clock_.join();

    std::lock_guard l(mutex_);
    destroyed_ = true;
    core_.reset();
}

void QtRenderer::dispatch(std::vector<Json> messages) {
    if (!onMessage) return;
    for (const Json& msg : messages) {
        if (msg["type"].asString() == "violation" && onDiag) onDiag("renderer-violation", msg["detail"].asString());
        onMessage(msg.dump());
    }
}

void QtRenderer::paint() {
    auto frame = std::make_shared<QtFrame>();
    {
        std::lock_guard l(mutex_);
        if (destroyed_) return;
        uint32_t w = core_->px_width(), h = core_->px_height();
        if (w == 0 || h == 0) return;
        double t0 = nowMs();
        twlite::DisplayList list = core_->paint();
        // Only what changed is painted again, over the last frame.
        std::vector<twlite::Rect> damage;
        bool partial = core_->damage(damage) && last_.width() == int(w) && last_.height() == int(h);
        if (!partial) last_ = twlite::QtBackend::make_image(int(w), int(h));
        if (!partial || !damage.empty()) backend_.paint(list, last_, QTransform::fromScale(dpr_, dpr_), partial ? &damage : nullptr);
        frame->document = last_;
        // Set after painting: QPainter would otherwise scale by it a second time.
        frame->document.setDevicePixelRatio(dpr_);

        // The layers go with the document they were cut from; their images only when their content changed.
        const twlite::Composition& composition = core_->composition();
        if (composition.version != compositionVersion_) {
            compositionVersion_ = composition.version;
            std::vector<QtLayer> layers;
            std::map<int64_t, std::pair<uint64_t, QImage>> images;
            for (const twlite::Layer& source : composition.layers) {
                QtLayer layer;
                layer.id = source.id;
                layer.bounds = source.bounds;
                layer.has_clip = source.has_clip;
                layer.clip = source.clip;
                layer.clip_radii = source.clip_radii;
                layer.track = source.track;
                layer.parent = source.parent;
                auto old = layerImages_.find(source.id);
                if (old != layerImages_.end() && old->second.first == source.content_version) {
                    layer.image = old->second.second;
                } else {
                    double scale = dpr_ * source.raster_scale;
                    layer.image = twlite::QtBackend::make_image(int(std::ceil(source.bounds.w * scale)), int(std::ceil(source.bounds.h * scale)));
                    backend_.paint(source.content, layer.image, QTransform::fromTranslate(-source.bounds.x, -source.bounds.y) * QTransform::fromScale(scale, scale));
                    layer.image.setDevicePixelRatio(scale);
                }
                images[source.id] = {source.content_version, layer.image};
                layers.push_back(std::move(layer));
            }
            layerImages_ = std::move(images);
            frame->layers = std::move(layers);
        } else if (partial && damage.empty()) {
            return;
        }
        lastPaintMs_ = nowMs() - t0;
    }
    paints_++;
    if (onFrame_) onFrame_(std::move(frame));
}

void QtRenderer::requestFrame() {
    {
        std::lock_guard l(clockMutex_);
        if (frozen_ || frameRequested_) return;
        frameRequested_ = true;
    }
    clockCv_.notify_all();
}

void QtRenderer::schedulePaint() {
    {
        std::lock_guard l(clockMutex_);
        if (paintScheduled_ || stopClock_) return;
        paintScheduled_ = true;
    }
    clockCv_.notify_all();
}

void QtRenderer::clockLoop() {
    for (;;) {
        bool frame, paint, tick;
        {
            std::unique_lock l(clockMutex_);
            auto ready = [&] { return stopClock_ || frameRequested_ || paintScheduled_ || (ticking_ && visible_); };
            bool woken = false;
            while (!ready()) {
                if (wakeAt_ && visible_) {
                    auto at = *wakeAt_;
                    if (!clockCv_.wait_until(l, at, ready) && wakeAt_ == at) {
                        woken = true;
                        wakeAt_.reset();
                        break;
                    }
                } else {
                    clockCv_.wait(l);
                }
            }
            if (stopClock_) return;

            // One tick: like rAF before the browser's paint, the callback first, the raster after.
            clockCv_.wait_for(l, std::chrono::duration<double>(kTickSeconds), [&] { return stopClock_; });
            if (stopClock_) return;

            frame = frameRequested_;
            paint = paintScheduled_;
            tick = (ticking_ && visible_) || woken;
            frameRequested_ = false;
            paintScheduled_ = false;
        }

        if (frame && onMessage) onMessage("{\"type\":\"frame\",\"time\":" + Json::number(nowMs()) + "}");
        if (tick) {
            // Animations advance and coalesced moves go out once per frame, before the paint.
            std::vector<Json> out;
            bool dirty = false, ticking = false;
            double wake;
            {
                std::lock_guard l(mutex_);
                if (destroyed_) return;
                out = core_->tick();
                dirty = core_->dirty();
                ticking = core_->wants_tick();
                wake = core_->next_tick_in();
            }
            dispatch(std::move(out));
            setWake(wake);
            {
                std::lock_guard l(clockMutex_);
                ticking_ = ticking;
            }
            paint = paint || dirty;
        }
        if (paint) this->paint();
    }
}
