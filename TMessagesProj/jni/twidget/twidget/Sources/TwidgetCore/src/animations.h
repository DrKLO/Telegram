// CSS animations and transitions, and the effects of Web Animations (`Element.animate()`), sampled
// into per-element style overrides. The renderer reads each element's declared animation and
// transition properties out of the document, hands them to the Animator after every build, and
// writes what `sample` returns back as inline declarations. Nothing here touches litehtml.
// Ported from TwidgetRenderer's WidgetAnimations.swift and WidgetWebAnimations.swift. Times are seconds on
// the renderer's clock unless a name says milliseconds.
#pragma once

#include "paint_policy.h"
#include "twidget/json.h"

#include <cstdint>
#include <functional>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace twlite {

/// (property, value) in the order they apply; a later entry of the same property wins.
using StyleValues = std::vector<std::pair<std::string, std::string>>;
/// Animated declarations per widget node id.
using StyleOverrides = std::map<int64_t, StyleValues>;
/// An element's declared values of the watched properties, as written.
using Declared = std::unordered_map<std::string, std::string>;

/// An easing function. `css` follows CSS animations and transitions (clamped to [0, 1] input);
/// `web` is the Web Animations form with the before flag and extrapolated cubic-béziers.
struct Timing {
    enum Kind { Linear, Bezier, Steps, Points } kind = Linear;
    enum Jump { JumpEnd, JumpStart, JumpBoth, JumpNone } jump = JumpEnd;
    double x1 = 0, y1 = 0, x2 = 1, y2 = 1;
    int steps = 1;
    std::vector<std::pair<double, double>> points;  // linear(): (input, output), inputs non-decreasing
    std::string text = "linear";

    double css(double x) const;
    double web(double x, bool before) const;

    static std::optional<Timing> parse(std::string_view text);
    static std::optional<Timing> parse_uncached(std::string_view text);
    static Timing ease();
};

struct Keyframe {
    double offset = 0;
    StyleValues declarations;
    std::optional<Timing> timing;
};
using KeyframeRules = std::unordered_map<std::string, std::vector<Keyframe>>;
/// Collects every `@keyframes` (and `@-webkit-keyframes`) block of a style sheet.
void parse_keyframes(std::string_view css, KeyframeRules& rules);

struct AnimationSpec {
    std::string name = "none";
    double duration = 0;
    std::string timing_text = "ease";
    double delay = 0;
    double iterations = 1;
    std::string direction = "normal";
    std::string fill = "none";
    bool paused = false;

    Timing timing() const;
    bool operator==(const AnimationSpec&) const = default;
    static std::vector<AnimationSpec> list(const Declared& declarations);
};

struct TransitionSpec {
    std::string property;
    double duration = 0;
    Timing timing = Timing::ease();
    double delay = 0;
    static std::vector<TransitionSpec> list(const Declared& declarations);
};

/// Interpolates two CSS values number by number when they have the same shape (colours normalised,
/// `none` transforms expanded to identities, shorter transform lists padded); otherwise flips at 0.5.
std::string interpolate(const std::string& a, const std::string& b, double t);

/// One `Element.animate()` effect as the sandbox reports it. Times in milliseconds; `start_ms` is
/// already on the renderer's clock.
struct WebAnimation {
    struct Frame {
        double offset = 0;
        Timing easing;
        std::string composite;  // "" = the effect's
        StyleValues values;
    };
    int64_t aid = 0;
    int64_t target = 0;
    std::vector<Frame> frames;
    double duration = 0, delay = 0, end_delay = 0, iterations = 1, iteration_start = 0;
    std::string direction = "normal", fill = "none";
    Timing easing;
    std::string composite = "replace", iteration_composite = "replace";
    std::optional<double> start_ms, hold_ms;
    double rate = 1;
    std::vector<std::string> properties;
    /// What it adds to its box while it runs (paint_policy.h, rule 5).
    std::optional<paint::BoxPaint> heavy;

    bool running() const { return !hold_ms && start_ms && rate != 0; }
    /// Validated like twidget's WebAnimation.parse; nullopt drops the record.
    static std::optional<WebAnimation> parse(const twidget::Json& record, double clock_offset_ms);
};

struct AnimationEvent {
    std::string type;  // animationstart, animationiteration, animationend, transitionend
    int64_t target = 0;
    std::string name;  // animationName, or the transition's propertyName
    double elapsed = 0;
};

class Animator {
public:
    /// Declared properties the renderer reads per element; transitions are watched on these.
    static const std::vector<std::string>& watched();
    /// An element is read only when it declares one of these (or is a Web Animations target).
    static const std::vector<std::string>& entry_properties();

    void set_style_sheets(const std::vector<std::string>& texts);
    /// The declared values of every element after a build; starts, restarts and stops animations
    /// and starts transitions where a watched value changed.
    void update(const std::unordered_map<int64_t, Declared>& declarations, double now);
    /// Style overrides at `now`, and the animation events crossed since the last sample.
    StyleOverrides sample(double now);
    /// Something will change on the next frame.
    bool active(double now) const;
    /// The same, leaving out the elements in `skip`.
    bool active(double now, const std::unordered_set<int64_t>& skip) const;
    std::vector<AnimationEvent> take_events() { return std::move(events_); }

    // What a compositor needs to play an element's animations without the core (compositor.h).

    /// Elements whose animations, transitions or Web Animations still change something at `now`:
    /// not ended, paused, held or finished.
    std::vector<int64_t> animated_ids(double now) const;
    /// Every property the animations of `id` write.
    std::vector<std::string> animated_properties(int64_t id) const;
    /// What `sample` would write for `id` at `t`, without events and without changing any state.
    StyleValues peek(int64_t id, double t) const;
    /// `property` of `id` without its animations: as declared, or the property's initial value.
    std::string base_value(int64_t id, const std::string& property) const { return underlying_value(id, property); }
    /// `id` declares an animation or a transition, whether or not one runs now.
    bool declares_motion(int64_t id) const;
    struct Plan {
        /// False while an animation still waits out its delay: nothing to hand over before `wake`.
        bool ready = true;
        /// The span to bake, on the renderer clock: up to the first end, or one loop when the
        /// animations repeat forever.
        double start = 0, duration = 0;
        bool repeat = false;
        /// When the core must sample `id` again: an end, a delay running out, or with `iterations`
        /// the next animationiteration.
        double wake = 0;
    };
    /// Nullopt when there is nothing to hand over, or when the timing cannot be baked: a reversed
    /// playback rate, two endless loops of different lengths, accumulating iterations.
    std::optional<Plan> plan(int64_t id, double now, bool iterations) const;
    /// Changes whenever anything that decides how `id` animates changes; equal values bake equal tracks.
    uint64_t signature(int64_t id) const;

    /// `{type:'anim', now, anims}`: Web Animations records, or script control of CSS animations
    /// and transitions (`css`). `clock_offset_ms` maps the sandbox clock onto the renderer's.
    void apply_web(const twidget::Json& message, double clock_offset_ms);
    /// Targets of Web Animations, whose underlying values the renderer must supply.
    std::vector<int64_t> web_targets() const;
    /// Properties the Web Animations on `id` animate.
    std::vector<std::string> web_properties(int64_t id) const;
    /// The `anims` query: running CSS animations and transitions of `ids`.
    twidget::Json css_animations(const std::vector<int64_t>& ids, double now) const;
    /// Drops Web Animations whose target left the document.
    void prune(const std::function<bool(int64_t)>& alive);

    /// Rule 5 of the heavy-box count (paint_policy.h): an animation that runs on element `id` and changes what makes a
    /// box heavy. `kind` 'a' is a CSS animation, from the end of its delay to its end, paused or not; 't' a transition
    /// likewise; 'w' a Web Animation while it runs, neither held nor finished. `name` the animation's or the
    /// transition's property, or the Web Animation's aid; it views the Animator's own strings until the next change.
    struct Motion {
        int64_t id = 0;
        char kind = 'a';
        std::string_view name;
        int64_t aid = 0;
        paint::BoxPaint paint;
    };
    /// The motions at `now` into `out` when it is given, and a hash of the set either way: equal sets hash equal.
    uint64_t heavy_motion(double now, std::vector<Motion>* out) const;

    /// The value an element has without animations, for Web Animations that add to it or leave a
    /// keyframe open. The renderer sets it before `sample`.
    std::unordered_map<int64_t, Declared> underlying;

private:
    struct Running {
        AnimationSpec spec;
        double start = 0;
        int iteration = 0;
        bool ended = false;
        // animation-play-state: paused stops the timeline where it is; running resumes it from there.
        std::optional<double> paused_at;
        double paused_duration = 0;
        double timeline_start() const { return start + paused_duration; }
        double local_time(double now) const { return paused_at.value_or(now) - timeline_start(); }
    };
    struct Transition {
        std::string from, to;
        double start = 0;
        TransitionSpec spec;
        std::optional<paint::BoxPaint> heavy;
    };
    struct Override {
        double origin = 0;
        std::optional<double> start, hold;
        double rate = 1;
        bool cancelled = false;
        double local(double now) const { return hold ? *hold : (now - (start ? *start : now)) * rate; }
    };

    std::string underlying_value(int64_t id, const std::string& property) const;
    /// `events` null: a peek, which reports nothing.
    std::optional<StyleValues> sample_animation(int64_t id, Running& item, const std::vector<Keyframe>& frames, double now, std::vector<AnimationEvent>* events) const;
    StyleValues values(const std::vector<Keyframe>& frames, const Declared& base, double progress, const AnimationSpec& spec) const;
    std::optional<std::string> transition_value(int64_t id, const std::string& property, double now) const;
    void prune_overrides();
    /// `only` non-zero: that target alone.
    void sample_web(StyleOverrides& out, double now, int64_t only = 0) const;
    const Override* override_of(int64_t id, const std::string& key) const;

    KeyframeRules keyframes_;
    /// Rule 5 for each @keyframes name, read once per set of style sheets.
    mutable std::unordered_map<std::string, std::optional<paint::BoxPaint>> keyframes_paint_;
    std::unordered_map<int64_t, std::vector<Running>> running_;
    std::unordered_map<int64_t, std::map<std::string, Transition>> transitions_;
    std::unordered_map<int64_t, Declared> bases_;
    std::unordered_map<int64_t, std::map<std::string, Override>> overrides_;
    std::map<int64_t, WebAnimation> web_;  // by aid; sampled in aid order, as twidget does
    std::vector<AnimationEvent> events_;
};

} // namespace twlite
