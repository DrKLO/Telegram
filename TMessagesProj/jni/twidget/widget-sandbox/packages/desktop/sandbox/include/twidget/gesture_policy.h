// Who gets a press over a widget: the widget or the bubble (docs/sdk-v1.md §5.5,
// docs/decisions.md "Gestures: touch-action is the claim" and "Vertical drags are
// the chat's"). The C++ twin of packages/core/src/shared/gesture-policy.ts, next to
// GesturePolicy.swift and GesturePolicy.kt. Taps and double taps over the widget are
// always the widget's (the reaction stays with the bubble outside the widget), and a
// vertical drag is always the chat's: the list scrolls over every widget, whatever
// its touch-action says (v0.1.3). For the rest the widget claims per element with CSS
// `touch-action`, read by the renderer at pointerdown on the target and its
// ancestors: `auto` leaves the press to the bubble (a hold opens the message menu, a
// horizontal swipe is reply or back); `pan-y` gives the widget horizontal drags;
// `none` gives it horizontal drags and holds; `pan-x` claims nothing more than
// `auto`. Touch-action on a container covers its children, as in CSS. The first move
// past the slop locks the axis for the whole press. The leading EDGE_STRIP_PX of the
// widget never take a claim (the system back gesture).
//
// Public API: the client's own recognizers over the widget feed a GestureArbiter
// the press (widget-local CSS px, ms on one clock) with the claim `WidgetHost::onGesture`
// reports for it, and act on its verdicts: on `cancel` take the press from the
// widget (its renderer gets a cancel: `pointercancel`, nothing more of this
// pointer), then do the verdict's `action`. Pure and side-effect free; checked
// against tests/fixtures/gesture-policy.json (copied from packages/core/tests/fixtures
// by `pnpm build:desktop`), so a rule change goes to the four copies and the
// fixture together.
#pragma once

#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace twidget {

/// What the widget takes of a press beyond its taps and double taps, from `touch-action` (and a learned `preventDefault` on pointerdown).
struct WidgetGestureClaim {
    /// Horizontal drags (no swipe-to-reply or back).
    bool x = false;
    /// Always false since v0.1.3: vertical drags are the chat's (the list scrolls), and the
    /// arbiter ignores it. Kept so that code written for v0.1.2 still builds.
    bool y = false;
    /// Holds, and a right click with a mouse: no message menu; the widget gets `contextmenu`.
    bool hold = false;

    constexpr bool operator==(const WidgetGestureClaim& o) const { return x == o.x && y == o.y && hold == o.hold; }
    constexpr bool operator!=(const WidgetGestureClaim& o) const { return !(*this == o); }
};

/// `Mouse` has no holds, no swipes and no edge strip; `Touch` and `Pen` have all of them.
enum class PointerKind { Touch, Pen, Mouse };

/// Who has the press: still undecided, the widget's, or the bubble's.
enum class GestureOwner { Pending, Widget, Bubble };

/**
 * What a press is so far: `Press` (undecided), `Tap`, `DoubleTap` (the second tap),
 * `Hold`, `DragX` / `DragY` (past the slop, by the dominant axis), `MenuClick`
 * (the secondary mouse button).
 */
enum class Gesture { Press, Tap, DoubleTap, Hold, DragX, DragY, MenuClick };

/// What the bubble does with a press it takes.
enum class BubbleAction { Menu, Swipe, Scroll, Back };

/// The event the widget's renderer delivers after the press's own events when the widget wins a double tap or a hold.
enum class GestureEvent { DblClick, ContextMenu };

struct GestureVerdict {
    GestureOwner owner = GestureOwner::Pending;
    Gesture gesture = Gesture::Press;
    /**
     * Once, in the verdict where the bubble takes a press the widget has had since its
     * pointerdown: end it for the widget now, `pointercancel` and nothing more of this
     * pointer (no pointerup, no click).
     */
    bool cancel = false;
    /// Owner `Bubble`: what the bubble does with the press.
    std::optional<BubbleAction> action;
    /// Once, when the widget wins a double tap or a hold: deliver this event after the press's own events.
    std::optional<GestureEvent> event;
    /// The press started in the leading edge strip, where the widget's claim does not apply.
    bool edge = false;

    constexpr bool operator==(const GestureVerdict& o) const {
        return owner == o.owner && gesture == o.gesture && cancel == o.cancel && action == o.action && event == o.event && edge == o.edge;
    }
    constexpr bool operator!=(const GestureVerdict& o) const { return !(*this == o); }
};

namespace gesture {

/// Movement (CSS px, from the pointerdown) below which a press is still a tap or a hold.
inline constexpr double TOUCH_SLOP_PX = 10;
/// A touch held this long within the slop is a hold: the bubble's message menu, or the widget's on `none`.
inline constexpr double HOLD_MS = 500;
/// A second tap whose pointerdown comes at most this long after the first tap's pointerup is a double tap.
inline constexpr double DOUBLE_TAP_MS = 300;
/// ... and lands at most this far (CSS px) from the first tap's pointerdown.
inline constexpr double DOUBLE_TAP_SLOP_PX = 40;
/// Presses that start this close (CSS px) to the widget's leading edge take no claim: the system back gesture starts there.
inline constexpr double EDGE_STRIP_PX = 20;

inline constexpr WidgetGestureClaim NO_CLAIM{};

/**
 * The claim of a press from the computed `touch-action` of the target and of each
 * ancestor up to the root (the widget's `<html>`) included, target first, and whether the widget
 * prevented pointerdown on the target before. `none` anywhere claims horizontal
 * drags and holds; a value without a horizontal pan (`pan-x`, `pan-left`,
 * `pan-right`) claims `x`; `auto` and `manipulation` claim nothing, and so does a
 * value with a horizontal pan (`pan-x`: vertical drags cannot be claimed). A
 * prevented pointerdown claims `x`, never holds: only `touch-action: none` does.
 * `y` is always false. Values are trimmed of ASCII
 * whitespace and lowercased A–Z only (computed `touch-action` is ASCII keywords).
 */
WidgetGestureClaim claimOf(const std::vector<std::string>& touchActions, bool prevented = false);

/// The TS names: "touch" | "pen" | "mouse", "pending" | "widget" | "bubble", "press" | "tap" | "double-tap" | "hold" |
/// "drag-x" | "drag-y" | "menu-click", "menu" | "swipe" | "scroll" | "back", "dblclick" | "contextmenu".
std::string_view name(PointerKind pointer);
std::string_view name(GestureOwner owner);
std::string_view name(Gesture gesture);
std::string_view name(BubbleAction action);
std::string_view name(GestureEvent event);

} // namespace gesture

/**
 * One pointer's presses over one widget, in order. `x` is measured from the
 * widget's leading edge (the left one in a left-to-right UI, the right one in a
 * right-to-left UI: pass `width - x` there), `y` from its top, CSS px; `t` in ms on
 * one clock. Calls with a `t` past the hold deadline decide the hold first, so a
 * late `tick` changes nothing. Not thread-safe: one recognizer, one thread.
 */
class GestureArbiter {
public:
    /// A primary pointerdown. `button` 2 with a mouse is the secondary click. A press still open is replaced.
    GestureVerdict down(double t, double x, double y, const WidgetGestureClaim& claim, PointerKind pointer = PointerKind::Touch, int button = 0);
    /// A move of the press's pointer. nullopt when no press is open.
    std::optional<GestureVerdict> move(double t, double x, double y);
    /// The hold timer: call it at `holdDeadline()`. nullopt when no press is open.
    std::optional<GestureVerdict> tick(double t);
    /// The pointerup. A press still undecided is a tap, or the second tap of a double tap. nullopt when no press is open.
    std::optional<GestureVerdict> up(double t, double x, double y);
    /// The platform cancelled the press (a scroll, the client's own recognizer): it is over, and no double tap continues it.
    void cancel();
    /// When `tick` may turn the open press into a hold, or nullopt.
    std::optional<double> holdDeadline() const;

private:
    struct Press {
        double t0, x0, y0;
        WidgetGestureClaim claim;
        PointerKind pointer;
        bool edge;
        bool second;
        /// The first move past the slop has happened: the press's axis is decided.
        bool locked = false;
        GestureOwner owner = GestureOwner::Pending;
        Gesture gesture = Gesture::Press;
        std::optional<BubbleAction> action;
    };
    struct Tap {
        double t, x, y;
    };

    static std::optional<GestureVerdict> holdFirst(Press& p, double t);
    static GestureVerdict decide(Press& p, GestureOwner owner, Gesture gesture, std::optional<BubbleAction> action = std::nullopt,
                                 std::optional<GestureEvent> event = std::nullopt);
    static GestureVerdict verdict(const Press& p);

    std::optional<Press> press_;
    std::optional<Tap> lastTap_;
};

} // namespace twidget
