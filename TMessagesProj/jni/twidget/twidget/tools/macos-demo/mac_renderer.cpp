#include "mac_renderer.h"

#include "twidget/json.h"

#include <cmath>
#include <vector>

using twidget::Json;

namespace {
constexpr double kTickSeconds = 1.0 / 60;
}

CGAffineTransform layer_transform(const twlite::LayerKey& key, const twlite::Rect& bounds) {
    // A layer point (u, v), v up from the bottom, is the document point (x + u, y + h - v); the key
    // then moves that to where it shows.
    twlite::Affine local{1, 0, 0, -1, bounds.x, bounds.y + bounds.h};
    twlite::Affine m = key.matrix * local;
    return CGAffineTransformMake(m.a, m.b, m.c, m.d, m.e, m.f);
}

CGPathRef clip_path(const twlite::Rect& r, const twlite::Radii& q) {
    // CSS: when adjacent radii exceed a side, all of them shrink by the same factor.
    float f = 1;
    auto limit = [&](float side, float a, float b) { if (a + b > side && a + b > 0) f = std::min(f, side / (a + b)); };
    limit(r.w, q.tlx, q.trx);
    limit(r.w, q.blx, q.brx);
    limit(r.h, q.tly, q.bly);
    limit(r.h, q.try_, q.bry);
    float tlx = q.tlx * f, tly = q.tly * f, trx = q.trx * f, try_ = q.try_ * f;
    float brx = q.brx * f, bry = q.bry * f, blx = q.blx * f, bly = q.bly * f;
    float x0 = r.x, y0 = r.y, x1 = r.x + r.w, y1 = r.y + r.h;

    CGMutablePathRef path = CGPathCreateMutable();
    constexpr float k = 0.5522847498f;
    // A quarter ellipse from (sx, sy) to (ex, ey) around the corner (cx, cy).
    auto corner = [&](float sx, float sy, float cx, float cy, float ex, float ey) {
        if (sx == ex && sy == ey) return;
        CGPathAddCurveToPoint(path, nullptr, sx + (cx - sx) * k, sy + (cy - sy) * k, ex + (cx - ex) * k, ey + (cy - ey) * k, ex, ey);
    };
    CGPathMoveToPoint(path, nullptr, x0 + tlx, y0);
    CGPathAddLineToPoint(path, nullptr, x1 - trx, y0);
    corner(x1 - trx, y0, x1, y0, x1, y0 + try_);
    CGPathAddLineToPoint(path, nullptr, x1, y1 - bry);
    corner(x1, y1 - bry, x1, y1, x1 - brx, y1);
    CGPathAddLineToPoint(path, nullptr, x0 + blx, y1);
    corner(x0 + blx, y1, x0, y1, x0, y1 - bly);
    CGPathAddLineToPoint(path, nullptr, x0, y0 + tly);
    corner(x0, y0 + tly, x0, y0, x0 + tlx, y0);
    CGPathCloseSubpath(path);
    return path;
}

double MacRenderer::nowMs() {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

MacRenderer::MacRenderer(const std::string& theme, double width, double dpr, std::function<void(std::shared_ptr<Frame>)> onFrame)
    : dpr_(dpr), onFrame_(std::move(onFrame)) {
    core_ = std::make_unique<twlite::Renderer>(width, dpr, theme == "dark", backend_);
    // Transform and opacity animations go to Core Animation instead of being painted every frame.
    core_->set_compositing(true);
    clock_ = std::thread([this] { clockLoop(); });
}

MacRenderer::~MacRenderer() { destroy(); }

void MacRenderer::load() {
    if (onMessage) onMessage("{\"type\":\"ready\"}");
    schedulePaint();
}

void MacRenderer::send(const std::string& json) {
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

template <class Call> void MacRenderer::run(Call call) {
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

void MacRenderer::edit(uint64_t generation, const std::string& text) {
    run([&](std::vector<Json>& out) { out = core_->edit(generation, text); });
}

void MacRenderer::endEditing() {
    run([&](std::vector<Json>& out) { out = core_->end_editing(); });
}

void MacRenderer::setWake(double seconds) {
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

void MacRenderer::setTheme(const std::string& theme) {
    run([&](std::vector<Json>& out) { core_->set_theme(theme == "dark", out); });
}

void MacRenderer::setWidth(double width) {
    run([&](std::vector<Json>& out) { core_->set_width(width, out); });
}

void MacRenderer::touch(int action, double x, double y, int pointer, bool primary, bool mouse, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->touch(action, x, y, pointer, primary, mouse, modifiers); });
}

void MacRenderer::hover(int action, double x, double y, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->hover(action, x, y, modifiers); });
}

void MacRenderer::wheel(double x, double y, double dx, double dy, int mode, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->wheel(x, y, dx, dy, mode, modifiers); });
}

void MacRenderer::key(const std::string& type, const std::string& key, const std::string& code, bool repeat, int modifiers) {
    run([&](std::vector<Json>& out) { out = core_->key(type, key, code, repeat, modifiers); });
}

void MacRenderer::setVisible(bool visible) {
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

void MacRenderer::setTicking(bool ticking) {
    {
        std::lock_guard l(clockMutex_);
        if (ticking_ == ticking) return;
        ticking_ = ticking;
    }
    if (ticking) clockCv_.notify_all();
}

void MacRenderer::destroy() {
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

void MacRenderer::dispatch(std::vector<Json> messages) {
    if (!onMessage) return;
    for (const Json& msg : messages) {
        if (msg["type"].asString() == "violation" && onDiag) onDiag("renderer-violation", msg["detail"].asString());
        onMessage(msg.dump());
    }
}

Image MacRenderer::image_of(const std::vector<uint8_t>& pixels, size_t w, size_t h) {
    if (w == 0 || h == 0) return {};
    CFDataRef data = CFDataCreate(kCFAllocatorDefault, pixels.data(), CFIndex(pixels.size()));
    CGDataProviderRef provider = CGDataProviderCreateWithCFData(data);
    CGColorSpaceRef rgb = CGColorSpaceCreateDeviceRGB();
    CGImageRef image = CGImageCreate(w, h, 8, 32, w * 4, rgb, kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big, provider, nullptr, false,
                                     kCGRenderingIntentDefault);
    CGColorSpaceRelease(rgb);
    CGDataProviderRelease(provider);
    CFRelease(data);
    return Image(image);
}

Image MacRenderer::raster(const twlite::DisplayList& list, const twlite::Rect& area, double scale) {
    size_t w = size_t(std::ceil(area.w * scale)), h = size_t(std::ceil(area.h * scale));
    if (w == 0 || h == 0 || w * h > (size_t(1) << 26)) return {};
    std::vector<uint8_t> pixels(w * h * 4);
    if (area.x == 0 && area.y == 0) {
        backend_.paint(list, pixels.data(), uint32_t(w), uint32_t(h), scale);
    } else {
        // The painter draws from the document's origin: a group moves `area` there.
        twlite::DisplayList moved;
        moved.reserve(list.size() + 2);
        twlite::Command push;
        push.kind = twlite::Cmd::GroupPush;
        push.matrix[4] = -area.x;
        push.matrix[5] = -area.y;
        moved.push_back(push);
        moved.insert(moved.end(), list.begin(), list.end());
        twlite::Command pop;
        pop.kind = twlite::Cmd::GroupPop;
        moved.push_back(pop);
        backend_.paint(moved, pixels.data(), uint32_t(w), uint32_t(h), scale);
    }
    return image_of(pixels, w, h);
}

void MacRenderer::paint() {
    auto frame = std::make_shared<Frame>();
    {
        std::lock_guard l(mutex_);
        if (destroyed_) return;
        if (core_->px_width() == 0 || core_->px_height() == 0) return;
        double t0 = nowMs();
        twlite::DisplayList list = core_->paint();
        frame->width = core_->width();
        frame->height = core_->viewport_height();
        frame->dpr = dpr_;
        // Only what changed is painted again, over the last frame's pixels.
        size_t w = size_t(core_->px_width()), h = size_t(core_->px_height());
        std::vector<twlite::Rect> damage;
        bool partial = core_->damage(damage) && pixels_.size() == w * h * 4 && pixelsWidth_ == w;
        if (!partial) {
            pixels_.assign(w * h * 4, 0);
            pixelsWidth_ = w;
        }
        if (!partial || !damage.empty()) backend_.paint(list, pixels_.data(), uint32_t(w), uint32_t(h), dpr_, partial ? &damage : nullptr);
        frame->document = image_of(pixels_, w, h);

        // The layers go with the document they were cut from: the view shows both in one transaction.
        const twlite::Composition& composition = core_->composition();
        if (composition.version != compositionVersion_) {
            compositionVersion_ = composition.version;
            std::vector<LayerFrame> layers;
            std::map<int64_t, std::pair<uint64_t, uint64_t>> shown;
            for (const twlite::Layer& source : composition.layers) {
                LayerFrame layer;
                layer.id = source.id;
                layer.bounds = source.bounds;
                layer.scale = float(dpr_) * source.raster_scale;
                layer.has_clip = source.has_clip;
                layer.clip = source.clip;
                layer.clip_radii = source.clip_radii;
                auto before = shown_.find(source.id);
                if (before == shown_.end() || before->second.first != source.content_version) layer.content = raster(source.content, source.bounds, layer.scale);
                layer.track_changed = before == shown_.end() || before->second.second != source.track_version;
                layer.track = source.track;
                layer.parent = source.parent;
                shown[source.id] = {source.content_version, source.track_version};
                layers.push_back(std::move(layer));
            }
            shown_ = std::move(shown);
            layerCount_ = int(layers.size());
            frame->layers = std::move(layers);
        }
        lastPaintMs_ = nowMs() - t0;
    }
    paints_++;
    if (onFrame_) onFrame_(std::move(frame));
}

void MacRenderer::requestFrame() {
    {
        std::lock_guard l(clockMutex_);
        if (frozen_ || frameRequested_) return;
        frameRequested_ = true;
    }
    clockCv_.notify_all();
}

void MacRenderer::schedulePaint() {
    {
        std::lock_guard l(clockMutex_);
        if (paintScheduled_ || stopClock_) return;
        paintScheduled_ = true;
    }
    clockCv_.notify_all();
}

void MacRenderer::clockLoop() {
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
