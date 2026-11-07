#include "compositor.h"

#include <algorithm>
#include <cmath>
#include <string>

namespace twlite {

namespace {

constexpr float GEOMETRY_TOLERANCE = 0.05f;
constexpr float OPACITY_TOLERANCE = 0.002f;
constexpr int INITIAL_SPANS = 8;
// A discontinuity (steps(), a flip at 0.5) never meets the tolerance: it stops splitting here and
// is played as a ramp this short.
constexpr int MAX_DEPTH = 9;
constexpr size_t MAX_KEYS = 1024;

struct Digest {
    uint64_t h = 1469598103934665603ull;
    void bytes(const void* p, size_t n) {
        const auto* b = static_cast<const unsigned char*>(p);
        for (size_t i = 0; i < n; i++) h = (h ^ b[i]) * 1099511628211ull;
    }
    template <typename T> void value(const T& v) { bytes(&v, sizeof v); }
    void text(const std::string& s) {
        value(s.size());
        bytes(s.data(), s.size());
    }
    void stops(const std::vector<Stop>& list) {
        value(list.size());
        for (const Stop& s : list) {
            value(s.offset);
            value(s.color);
        }
    }
};

} // namespace

std::vector<LayerKey> resume_track(const LayerTrack& track, double elapsed, double& duration) {
    const auto& keys = track.keys;
    duration = track.duration;
    if (keys.size() < 2 || elapsed <= 0 || track.duration <= 0) return keys;
    double p = track.repeat ? std::fmod(elapsed, track.duration) / track.duration : elapsed / track.duration;
    if (p >= 1) {
        LayerKey last = keys.back();
        last.t = 0;
        return {last};
    }
    auto at = [&](double t) {
        size_t i = 0;
        while (i + 2 < keys.size() && keys[i + 1].t < t) i++;
        const LayerKey &a = keys[i], &b = keys[i + 1];
        float u = b.t > a.t ? float(std::clamp((t - a.t) / (b.t - a.t), 0.0, 1.0)) : 0.0f;
        auto lerp = [u](float x, float y) { return x + (y - x) * u; };
        LayerKey k;
        k.matrix = Affine{lerp(a.matrix.a, b.matrix.a), lerp(a.matrix.b, b.matrix.b), lerp(a.matrix.c, b.matrix.c),
                          lerp(a.matrix.d, b.matrix.d), lerp(a.matrix.e, b.matrix.e), lerp(a.matrix.f, b.matrix.f)};
        k.opacity = lerp(a.opacity, b.opacity);
        return k;
    };
    LayerKey now = at(p);
    now.t = 0;
    std::vector<LayerKey> out{now};
    if (!track.repeat) {
        for (const LayerKey& k : keys) {
            if (k.t <= p) continue;
            LayerKey moved = k;
            moved.t = float((k.t - p) / (1 - p));
            out.push_back(moved);
        }
        duration = track.duration * (1 - p);
        return out;
    }
    // A loop's last key is its first again: the keys after the phase, then those before it.
    for (const LayerKey& k : keys) {
        if (k.t <= p) continue;
        LayerKey moved = k;
        moved.t = float(k.t - p);
        out.push_back(moved);
    }
    for (const LayerKey& k : keys) {
        if (k.t <= 0 || k.t >= p) continue;
        LayerKey moved = k;
        moved.t = float(k.t + 1 - p);
        out.push_back(moved);
    }
    LayerKey end = now;
    end.t = 1;
    out.push_back(end);
    return out;
}

uint64_t paint_digest(const DisplayList& list) {
    Digest d;
    for (const Command& c : list) {
        d.value(c.kind);
        d.value(c.rect);
        d.value(c.radii);
        d.value(c.clip);
        d.value(c.has_clip);
        d.value(c.color);
        d.value(c.width);
        d.value(c.font);
        d.text(c.text);
        d.value(c.x);
        d.value(c.y);
        d.value(c.image);
        d.value(c.canvas);
        d.value(c.ax);
        d.value(c.ay);
        d.value(c.bx);
        d.value(c.by);
        d.stops(c.stops);
        d.value(c.opacity);
        d.value(c.matrix);
        d.value(c.hidden);
        d.value(c.inset);
        if (const Path* p = c.path.get()) {
            d.bytes(p->ops.data(), p->ops.size() * sizeof(Path::Op));
            d.bytes(p->points.data(), p->points.size() * sizeof(float));
            d.value(p->fill);
            d.value(p->evenodd);
            d.value(p->fill_color);
            d.value(p->gradient);
            float g[7] = {p->x1, p->y1, p->x2, p->y2, p->r, p->fx, p->fy};
            d.value(g);
            d.stops(p->stops);
            d.value(p->stroke);
            d.value(p->stroke_color);
            d.value(p->stroke_width);
            d.value(p->line_cap);
            d.value(p->line_join);
            d.value(p->miter_limit);
            d.bytes(p->dashes.data(), p->dashes.size() * sizeof(float));
            d.value(p->dash_offset);
        }
    }
    return d.h;
}

bool bake_track(const std::function<bool(double t, LayerKey& key)>& sample, const Rect& box, std::vector<LayerKey>& keys) {
    const float corners[4][2] = {{box.x, box.y}, {box.x + box.w, box.y}, {box.x, box.y + box.h}, {box.x + box.w, box.y + box.h}};
    auto straight = [&](const LayerKey& a, const LayerKey& b, const LayerKey& mid) {
        float worst = 0;
        for (const auto& p : corners) {
            float ax = a.matrix.apply_x(p[0], p[1]), ay = a.matrix.apply_y(p[0], p[1]);
            float bx = b.matrix.apply_x(p[0], p[1]), by = b.matrix.apply_y(p[0], p[1]);
            float mx = mid.matrix.apply_x(p[0], p[1]), my = mid.matrix.apply_y(p[0], p[1]);
            worst = std::max(worst, std::hypot(mx - (ax + bx) / 2, my - (ay + by) / 2));
        }
        return worst <= GEOMETRY_TOLERANCE && std::fabs(mid.opacity - (a.opacity + b.opacity) / 2) <= OPACITY_TOLERANCE;
    };

    keys.clear();
    bool failed = false;
    std::function<void(double, const LayerKey&, double, const LayerKey&, int)> split = [&](double t0, const LayerKey& k0, double t1, const LayerKey& k1, int depth) {
        if (failed) return;
        double tm = (t0 + t1) / 2;
        LayerKey km;
        if (!sample(tm, km)) {
            failed = true;
            return;
        }
        km.t = float(tm);
        if (depth >= MAX_DEPTH || straight(k0, k1, km)) {
            keys.push_back(k1);
            failed = keys.size() > MAX_KEYS;
            return;
        }
        split(t0, k0, tm, km, depth + 1);
        split(tm, km, t1, k1, depth + 1);
    };

    LayerKey previous;
    if (!sample(0, previous)) return false;
    previous.t = 0;
    keys.push_back(previous);
    for (int i = 1; i <= INITIAL_SPANS && !failed; i++) {
        double t0 = double(i - 1) / INITIAL_SPANS, t1 = double(i) / INITIAL_SPANS;
        LayerKey next;
        if (!sample(t1, next)) return false;
        next.t = float(t1);
        split(t0, previous, t1, next, 0);
        previous = next;
    }
    if (failed) return false;

    // A run of equal keys needs only its ends.
    std::vector<LayerKey> kept;
    kept.reserve(keys.size());
    for (size_t i = 0; i < keys.size(); i++) {
        size_t n = kept.size();
        bool same = n >= 2 && kept[n - 1].matrix == kept[n - 2].matrix && kept[n - 1].opacity == kept[n - 2].opacity &&
                    keys[i].matrix == kept[n - 1].matrix && keys[i].opacity == kept[n - 1].opacity;
        if (same && i + 1 < keys.size()) kept[n - 1].t = keys[i].t;
        else kept.push_back(keys[i]);
    }
    keys = std::move(kept);
    return true;
}

} // namespace twlite
