#include "compositor.h"

#include <algorithm>
#include <cmath>
#include <string>
#include <unordered_map>

namespace twlite {

namespace {

constexpr float GEOMETRY_TOLERANCE = 0.05f;
constexpr float OPACITY_TOLERANCE = 0.002f;
constexpr int INITIAL_SPANS = 8;
// A discontinuity (steps(), a flip at 0.5) never meets the tolerance: it stops splitting here and
// is played as a ramp this short.
constexpr int MAX_DEPTH = 9;
constexpr size_t MAX_KEYS = 1024;
// More changed areas than this are merged into the one around them all.
constexpr size_t MAX_DAMAGE_RECTS = 32;

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

namespace {

void digest(Digest& d, const Command& c) {
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
    if (const Filters* f = c.filter.get()) {
        for (const Filter& step : *f) {
            d.value(step.kind);
            d.value(step.amount);
            d.value(step.x);
            d.value(step.y);
            d.value(step.color);
        }
    }
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
        d.value(p->stroke_gradient);
        d.value(p->sg);
        d.stops(p->stroke_stops);
    }
}

/// Where a drawing command can put pixels, in its own space; empty for the commands that only set
/// up clips and groups.
Rect ink_of(const Command& c) {
    auto grow = [](Rect r, float by) { return Rect{r.x - by, r.y - by, r.w + 2 * by, r.h + 2 * by}; };
    switch (c.kind) {
    case Cmd::FillRect:
    case Cmd::FillEllipse:
    case Cmd::Image:
    case Cmd::Linear:
    case Cmd::Radial:
    case Cmd::Conic:
        return grow(c.rect, 1);
    case Cmd::StrokeRect:
    case Cmd::StrokeEllipse:
        return grow(c.rect, c.width / 2 + 1);
    case Cmd::Text: {
        // Glyphs overhang their advance (italics, swashes): a quarter of the line on each side.
        float size = std::max(c.rect.h, 1.0f);
        return grow(c.rect, size / 4 + 1);
    }
    case Cmd::Shadow:
        return grow(c.rect, c.bx * 1.5f + std::fabs(c.by) + std::max(std::fabs(c.ax), std::fabs(c.ay)) + 2);
    case Cmd::FilterPush:
    case Cmd::Backdrop:
        return grow(c.rect, 1);
    case Cmd::Path: {
        const Path* p = c.path.get();
        if (!p || p->points.size() < 2) return {};
        float x0 = p->points[0], y0 = p->points[1], x1 = x0, y1 = y0;
        for (size_t i = 0; i + 1 < p->points.size(); i += 2) {
            x0 = std::min(x0, p->points[i]);
            x1 = std::max(x1, p->points[i]);
            y0 = std::min(y0, p->points[i + 1]);
            y1 = std::max(y1, p->points[i + 1]);
        }
        float reach = p->stroke ? p->stroke_width * std::max(p->miter_limit, 1.0f) / 2 : 0;
        return grow(Rect{x0, y0, x1 - x0, y1 - y0}, reach + 1);
    }
    default:
        return {};
    }
}

Rect transformed(const Rect& r, const Affine& m) {
    if (m == Affine{}) return r;
    float xs[4] = {r.x, r.x + r.w, r.x, r.x + r.w}, ys[4] = {r.y, r.y, r.y + r.h, r.y + r.h};
    float x0 = 1e30f, y0 = 1e30f, x1 = -1e30f, y1 = -1e30f;
    for (int i = 0; i < 4; i++) {
        float x = m.apply_x(xs[i], ys[i]), y = m.apply_y(xs[i], ys[i]);
        x0 = std::min(x0, x);
        y0 = std::min(y0, y);
        x1 = std::max(x1, x);
        y1 = std::max(y1, y);
    }
    return Rect{x0, y0, x1 - x0, y1 - y0};
}

} // namespace

uint64_t paint_digest(const DisplayList& list) {
    Digest d;
    for (const Command& c : list) digest(d, c);
    return d.h;
}

size_t drop_inert_clips(DisplayList& list) {
    // Per open clip: where it starts, its area (inset by its corners) in document coordinates, and
    // whether everything drawn inside has stayed within it so far.
    struct Open {
        size_t at;
        Rect area;
        bool keep;
    };
    struct Frame {
        Affine matrix;
        bool clip;  // a ClipPush (else a GroupPush)
    };
    std::vector<Frame> frames{{Affine{}, false}};
    std::vector<Open> open;
    std::vector<char> drop(list.size(), 0);
    auto axis_aligned = [](const Affine& m) { return m.b == 0 && m.c == 0 && m.a > 0 && m.d > 0; };
    auto inside = [](const Rect& r, const Rect& area) {
        return r.x >= area.x && r.y >= area.y && r.x + r.w <= area.x + area.w && r.y + r.h <= area.y + area.h;
    };
    for (size_t i = 0; i < list.size(); i++) {
        const Command& c = list[i];
        const Affine& m = frames.back().matrix;
        switch (c.kind) {
        case Cmd::ClipPush: {
            Open o{i, {}, false};
            // A clip path, or one under a rotation or skew, stays.
            if (!c.path && axis_aligned(m)) {
                const Radii& q = c.radii;
                float corner = std::max({q.tlx, q.tly, q.trx, q.try_, q.brx, q.bry, q.blx, q.bly});
                Rect area{c.rect.x + corner, c.rect.y + corner, c.rect.w - 2 * corner, c.rect.h - 2 * corner};
                o.area = transformed(area, m);
                o.keep = area.w > 0 && area.h > 0;
            }
            open.push_back(o);
            frames.push_back({m, true});
            continue;
        }
        case Cmd::ClipPop:
            if (frames.size() > 1 && frames.back().clip) {
                frames.pop_back();
                if (!open.empty()) {
                    if (open.back().keep) drop[open.back().at] = drop[i] = 1;
                    open.pop_back();
                }
            }
            continue;
        case Cmd::GroupPush: {
            Affine own{c.matrix[0], c.matrix[1], c.matrix[2], c.matrix[3], c.matrix[4], c.matrix[5]};
            frames.push_back({m * own, false});
            continue;
        }
        case Cmd::GroupPop:
            if (frames.size() > 1 && !frames.back().clip) frames.pop_back();
            continue;
        default:
            break;
        }
        if (open.empty()) continue;
        Rect ink = transformed(ink_of(c), m);
        if (ink.w <= 0 && ink.h <= 0) continue;
        // Within the innermost open clip is within every enclosing one that it is within.
        for (auto& o : open)
            if (o.keep && !inside(ink, o.area)) o.keep = false;
    }
    // One pass: an erase per dropped command makes many sibling clips quadratic.
    size_t kept = 0;
    for (size_t i = 0; i < list.size(); i++)
        if (!drop[i]) {
            if (kept != i) list[kept] = std::move(list[i]);
            kept++;
        }
    size_t dropped = list.size() - kept;
    list.erase(list.begin() + long(kept), list.end());
    return dropped / 2;
}

PaintSummary summarize(const DisplayList& list) {
    PaintSummary out;
    out.entries.reserve(list.size());
    // What a command draws depends on the clips and groups around it: its key carries them.
    struct Frame {
        uint64_t context;
        Affine matrix;
        bool hidden;
        Rect filtered;  // inside a filter: where its result paints, which a change inside may all change
    };
    std::vector<Frame> stack{{1469598103934665603ull, Affine{}, false, {}}};
    for (const Command& c : list) {
        Digest d;
        d.value(stack.back().context);
        digest(d, c);
        switch (c.kind) {
        case Cmd::ClipPush:
            stack.push_back({d.h, stack.back().matrix, stack.back().hidden, stack.back().filtered});
            continue;
        case Cmd::GroupPush: {
            Affine own{c.matrix[0], c.matrix[1], c.matrix[2], c.matrix[3], c.matrix[4], c.matrix[5]};
            stack.push_back({d.h, stack.back().matrix * own, stack.back().hidden || c.hidden || c.opacity <= 0, stack.back().filtered});
            continue;
        }
        case Cmd::FilterPush:
            stack.push_back({d.h, stack.back().matrix, stack.back().hidden, transformed(ink_of(c), stack.back().matrix)});
            continue;
        case Cmd::ClipPop:
        case Cmd::GroupPop:
        case Cmd::FilterPop:
            if (stack.size() > 1) stack.pop_back();
            continue;
        default:
            break;
        }
        PaintSummary::Entry e;
        e.key = d.h;
        e.canvas = c.canvas;
        e.backdrop = c.kind == Cmd::Backdrop;
        if (!stack.back().hidden) e.ink = stack.back().filtered.empty() ? transformed(ink_of(c), stack.back().matrix) : stack.back().filtered;
        out.entries.push_back(e);
    }
    return out;
}

bool damage_between(const PaintSummary& before, const PaintSummary& after, const std::function<bool(int canvas)>& canvas_changed, std::vector<Rect>& out) {
    out.clear();
    const auto& a = before.entries;
    const auto& b = after.entries;
    auto add = [&](const Rect& r) {
        if (r.w > 0 && r.h > 0) out.push_back(r);
    };
    // What both start and end with paints the same, unless a canvas behind it drew again.
    size_t head = 0;
    while (head < a.size() && head < b.size() && a[head].key == b[head].key) head++;
    size_t tail = 0;
    while (tail < a.size() - head && tail < b.size() - head && a[a.size() - 1 - tail].key == b[b.size() - 1 - tail].key) tail++;
    // The middle: commands matched by key, in order where they can be; the rest painted anew.
    std::unordered_map<uint64_t, std::vector<size_t>> waiting;
    for (size_t i = a.size() - tail; i-- > head;) waiting[a[i].key].push_back(i);
    std::vector<char> matched(a.size(), 0);
    std::vector<std::pair<size_t, size_t>> pairs;  // (after, before)
    for (size_t j = head; j < b.size() - tail; j++) {
        auto it = waiting.find(b[j].key);
        if (it == waiting.end() || it->second.empty()) {
            add(b[j].ink);
            continue;
        }
        size_t i = it->second.back();
        it->second.pop_back();
        matched[i] = 1;
        pairs.emplace_back(j, i);
    }
    for (size_t i = head; i < a.size() - tail; i++)
        if (!matched[i]) add(a[i].ink);
    // Matched commands whose paint order changed: those off the longest run kept in order.
    std::vector<size_t> run, at;  // run[k]: smallest `before` index ending a run of k + 1; at[k]: its pair
    std::vector<long> back(pairs.size(), -1);
    for (size_t p = 0; p < pairs.size(); p++) {
        size_t k = size_t(std::lower_bound(run.begin(), run.end(), pairs[p].second) - run.begin());
        if (k == run.size()) {
            run.push_back(pairs[p].second);
            at.push_back(p);
        } else {
            run[k] = pairs[p].second;
            at[k] = p;
        }
        back[p] = k > 0 ? long(at[k - 1]) : -1;
    }
    std::vector<char> kept(pairs.size(), 0);
    for (long p = at.empty() ? -1 : long(at.back()); p >= 0; p = back[size_t(p)]) kept[size_t(p)] = 1;
    for (size_t p = 0; p < pairs.size(); p++)
        if (!kept[p]) add(b[pairs[p].first].ink);
    for (const auto& e : b)
        if (e.canvas && canvas_changed && canvas_changed(e.canvas)) add(e.ink);
    // A backdrop filters what is under it: a change there changes all of it.
    auto touches = [](const Rect& a, const Rect& r) { return a.x < r.x + r.w && r.x < a.x + a.w && a.y < r.y + r.h && r.y < a.y + a.h; };
    for (const auto& e : b) {
        if (!e.backdrop) continue;
        for (size_t k = 0, n = out.size(); k < n; k++) {
            if (touches(e.ink, out[k])) {
                add(e.ink);
                break;
            }
        }
    }
    if (out.size() > MAX_DAMAGE_RECTS) {
        Rect u = out[0];
        for (const Rect& r : out) {
            float x0 = std::min(u.x, r.x), y0 = std::min(u.y, r.y);
            u = Rect{x0, y0, std::max(u.x + u.w, r.x + r.w) - x0, std::max(u.y + u.h, r.y + r.h) - y0};
        }
        out.assign(1, u);
    }
    return true;
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
