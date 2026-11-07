// Heavy boxes (widget-sandbox v0.6.0: packages/core/src/shared/paint-policy.ts, docs/decisions.md "Heavy boxes"): the
// paint limit every renderer enforces, since only the renderer knows the cascade. A box is heavy when its computed
// style has a blurred box-shadow layer, a filter, a backdrop-filter or opacity below 1 (a disabled or pressed <button>'s
// own dimming aside), or while an animation of one of them runs. At most twidget::kMaxHeavyBoxes at once, of which
// twidget::kMaxBackdropBoxes with backdrop-filter; past either the renderer reports `limit` and the host stops the widget.
//
// The rules here take CSS text, as packages/core/tests/fixtures/paint-policy.json writes it; the renderer judges
// litehtml's computed values by the same rules (Renderer::count_paint). The value counts, not the picture:
// `opacity: 0.999` and `filter: brightness(1)` are heavy.
#pragma once

#include "twidget/renderer.h"

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace twlite::paint {

/// What one box adds: to the heavy count, and to the backdrop-filter count.
struct BoxPaint {
    bool heavy = false;
    bool backdrop = false;
    bool operator==(const BoxPaint&) const = default;
};

/// A box's computed values as CSS text; an empty value is the initial one.
struct Style {
    std::string_view display, content, box_shadow, filter, backdrop_filter, opacity;
    /// A <button> with the `disabled` attribute, or held down (:active): its opacity is its own dimming. Ignored for a
    /// pseudo-element, a box of its own.
    enum class Button { None, Disabled, Pressed } button = Button::None;
};

/// Rule 1: a layer of `box-shadow` has a blur radius (its third length) above 0; one of unknown size (calc()) counts.
bool box_shadow_blurred(std::string_view value);
/// The layers without blur, "none" when nothing is left: what a stopped widget keeps.
std::string box_shadow_without_blur(std::string_view value);
/// `display: none` on an element: nothing under it builds a box either (`contents` hides nothing).
bool hides_subtree(std::string_view display);
/// The box an element (or with `pseudo` its ::before / ::after) builds and what it adds; nullopt when it builds none.
/// Rules 1 to 4; a running animation (rule 5) is AnimationPaint's.
std::optional<BoxPaint> box_paint(const Style& style, bool pseudo);

/// Rule 5: what a running animation adds to its box, from its keyframes fed in order. It counts when it changes
/// filter, backdrop-filter or opacity, or box-shadow through a blurred layer, a value that is no string (unknown), or
/// a missing first or last keyframe (the box's own value, unknown to the rule). Shadows without blur do not count.
class AnimationPaint {
public:
    /// The next keyframe; its offset (the computed one when implicit), nullopt when it has none.
    void keyframe(std::optional<double> offset) { at_ = offset; }
    /// One member of it, by CSS or camelCase name, vendor prefix allowed; `value` nullopt when it is no string.
    /// `offset`, `computedOffset`, `easing` and `composite` are no properties.
    void property(std::string_view name, std::optional<std::string_view> value);
    std::optional<BoxPaint> result() const;

private:
    std::optional<double> at_;
    bool heavy_ = false, backdrop_ = false, shadow_ = false, shadow_blurred_ = false, shadow_first_ = false, shadow_last_ = false;
};

struct Verdict {
    twidget::WidgetPaintLimit code = twidget::WidgetPaintLimit::Paint;
    int count = 0;
    bool operator==(const Verdict&) const = default;
};

/// The ledger of heavy boxes by any key the renderer gives a box (an element and each of its pseudo-elements a key
/// of their own). It keeps both counts current as boxes change and animations start and end, and gives the verdict
/// before a frame is drawn; the first one past a limit sticks.
template <class Key, class Hash = std::hash<Key>>
class PaintCounter {
public:
    int heavy() const { return heavy_; }
    int backdrop() const { return backdrop_; }
    /// The most there were at once.
    int peak_heavy() const { return peak_heavy_; }
    int peak_backdrop() const { return peak_backdrop_; }

    /// The box's computed values, or nullopt: it builds no box now. Its animations stay.
    void set_box(const Key& key, std::optional<BoxPaint> paint) {
        auto it = boxes_.find(key);
        if (it != boxes_.end()) it->second.seen = scan_;
        if (!paint && it == boxes_.end()) return;
        update(key, [&](Box& b) { b.paint = paint; });
    }
    /// The box is not rendered (display: none on it or an ancestor) or its node left: its animations go with it.
    void remove_box(const Key& key) {
        if (boxes_.count(key)) update(key, [](Box& b) {
            b.paint.reset();
            b.anims.clear();
        });
    }
    /// An animation of rule 5 started on the box; `id` tells it from the box's others.
    void start_animation(const Key& key, std::string_view id, BoxPaint paint) {
        update(key, [&](Box& b) {
            for (Anim& a : b.anims) {
                if (a.id != id) continue;
                a.n++;
                a.backdrop = a.backdrop || paint.backdrop;
                return;
            }
            b.anims.push_back({std::string(id), 1, paint.backdrop});
        });
    }
    /// That animation ended or was cancelled; one that never started changes nothing.
    void end_animation(const Key& key, std::string_view id) {
        if (!animating(key, id)) return;
        update(key, [&](Box& b) {
            for (auto a = b.anims.begin(); a != b.anims.end(); ++a) {
                if (a->id != id) continue;
                if (--a->n <= 0) b.anims.erase(a);
                return;
            }
        });
    }
    bool has_box(const Key& key) const {
        auto it = boxes_.find(key);
        return it != boxes_.end() && it->second.paint.has_value();
    }
    bool animating(const Key& key, std::string_view id) const {
        auto it = boxes_.find(key);
        if (it == boxes_.end()) return false;
        for (const Anim& a : it->second.anims)
            if (a.id == id) return true;
        return false;
    }
    /// Null while both counts are within their limits; the first time one is past, the heavy count (checked first) or
    /// the backdrop count, and the same verdict from then on.
    std::optional<Verdict> check() {
        if (stopped_) return stopped_;
        if (heavy_ > twidget::kMaxHeavyBoxes) stopped_ = Verdict{twidget::WidgetPaintLimit::Paint, heavy_};
        else if (backdrop_ > twidget::kMaxBackdropBoxes) stopped_ = Verdict{twidget::WidgetPaintLimit::Backdrop, backdrop_};
        return stopped_;
    }

    /// A count of every box: set_box for each one the renderer builds between the two; a box it did not set is gone,
    /// as remove_box. True when one took running animations with it.
    void begin_scan() { scan_++; }
    bool end_scan() {
        bool anims = false;
        for (auto it = boxes_.begin(); it != boxes_.end();) {
            if (it->second.seen == scan_) {
                ++it;
                continue;
            }
            auto [heavy, backdrop] = contribution(it->second);
            heavy_ -= heavy;
            backdrop_ -= backdrop;
            anims = anims || !it->second.anims.empty();
            it = boxes_.erase(it);
        }
        return anims;
    }

private:
    struct Anim {
        std::string id;
        int n = 0;
        bool backdrop = false;
    };
    struct Box {
        std::optional<BoxPaint> paint;
        std::vector<Anim> anims;
        uint32_t seen = 0;
    };

    static BoxPaint contribution(const Box& b) {
        if (!b.paint) return {};
        BoxPaint out = *b.paint;
        for (const Anim& a : b.anims) {
            out.heavy = true;
            out.backdrop = out.backdrop || a.backdrop;
        }
        return out;
    }
    template <class Change> void update(const Key& key, Change change) {
        auto it = boxes_.find(key);
        BoxPaint before = it == boxes_.end() ? BoxPaint{} : contribution(it->second);
        if (it == boxes_.end()) {
            it = boxes_.emplace(key, Box{}).first;
            it->second.seen = scan_;
        }
        change(it->second);
        BoxPaint after = contribution(it->second);
        heavy_ += int(after.heavy) - int(before.heavy);
        backdrop_ += int(after.backdrop) - int(before.backdrop);
        if (!it->second.paint && it->second.anims.empty()) boxes_.erase(it);
        if (heavy_ > peak_heavy_) peak_heavy_ = heavy_;
        if (backdrop_ > peak_backdrop_) peak_backdrop_ = backdrop_;
    }

    std::unordered_map<Key, Box, Hash> boxes_;
    int heavy_ = 0, backdrop_ = 0, peak_heavy_ = 0, peak_backdrop_ = 0;
    uint32_t scan_ = 0;
    std::optional<Verdict> stopped_;
};

} // namespace twlite::paint
