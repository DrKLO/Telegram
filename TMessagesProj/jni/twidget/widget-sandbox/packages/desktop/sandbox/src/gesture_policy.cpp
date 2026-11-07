#include "twidget/gesture_policy.h"

#include <cmath>

namespace twidget {

namespace gesture {

namespace {

bool isSpace(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\v' || c == '\f' || c == '\r'; }
/// A regex word character (`\w` without the `u` flag): every other byte, a UTF-8 one included, is a boundary.
bool isWord(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'; }

std::string trimLower(std::string_view s) {
    size_t b = 0, e = s.size();
    while (b < e && isSpace(s[b])) b++;
    while (e > b && isSpace(s[e - 1])) e--;
    std::string out(s.substr(b, e - b));
    for (char& c : out) if (c >= 'A' && c <= 'Z') c = char(c + 32);
    return out;
}

/// `/\bpan-(a|b|c)\b/.test(ta)` for the three `words`.
bool hasPan(const std::string& ta, const std::string_view (&words)[3]) {
    for (size_t i = ta.find("pan-"); i != std::string::npos; i = ta.find("pan-", i + 1)) {
        if (i > 0 && isWord(ta[i - 1])) continue;
        std::string_view rest = std::string_view(ta).substr(i + 4);
        for (std::string_view w : words)
            if (rest.substr(0, w.size()) == w && (rest.size() == w.size() || !isWord(rest[w.size()]))) return true;
    }
    return false;
}

} // namespace

WidgetGestureClaim claimOf(const std::vector<std::string>& touchActions, bool prevented) {
    static constexpr std::string_view horizontal[3] = {"x", "left", "right"};
    bool x = prevented;
    for (const std::string& raw : touchActions) {
        std::string ta = trimLower(raw);
        if (ta.empty() || ta == "auto" || ta == "manipulation") continue;
        if (ta == "none") return {true, false, true};
        if (!hasPan(ta, horizontal)) x = true;
    }
    return {x, false, false};
}

std::string_view name(PointerKind pointer) {
    switch (pointer) {
    case PointerKind::Touch: return "touch";
    case PointerKind::Pen: return "pen";
    case PointerKind::Mouse: return "mouse";
    }
    return "";
}

std::string_view name(GestureOwner owner) {
    switch (owner) {
    case GestureOwner::Pending: return "pending";
    case GestureOwner::Widget: return "widget";
    case GestureOwner::Bubble: return "bubble";
    }
    return "";
}

std::string_view name(Gesture gesture) {
    switch (gesture) {
    case Gesture::Press: return "press";
    case Gesture::Tap: return "tap";
    case Gesture::DoubleTap: return "double-tap";
    case Gesture::Hold: return "hold";
    case Gesture::DragX: return "drag-x";
    case Gesture::DragY: return "drag-y";
    case Gesture::MenuClick: return "menu-click";
    }
    return "";
}

std::string_view name(BubbleAction action) {
    switch (action) {
    case BubbleAction::Menu: return "menu";
    case BubbleAction::Swipe: return "swipe";
    case BubbleAction::Scroll: return "scroll";
    case BubbleAction::Back: return "back";
    }
    return "";
}

std::string_view name(GestureEvent event) {
    switch (event) {
    case GestureEvent::DblClick: return "dblclick";
    case GestureEvent::ContextMenu: return "contextmenu";
    }
    return "";
}

} // namespace gesture

using namespace gesture;

GestureVerdict GestureArbiter::down(double t, double x, double y, const WidgetGestureClaim& claim, PointerKind pointer, int button) {
    bool edge = pointer != PointerKind::Mouse && x < EDGE_STRIP_PX;
    std::optional<Tap> last = lastTap_;
    lastTap_.reset();
    bool second = last && t - last->t <= DOUBLE_TAP_MS && std::hypot(x - last->x, y - last->y) <= DOUBLE_TAP_SLOP_PX;
    press_ = Press{t, x, y, edge ? NO_CLAIM : claim, pointer, edge, second, false, GestureOwner::Pending, Gesture::Press, std::nullopt};
    Press& p = *press_;
    if (pointer == PointerKind::Mouse && button == 2) {
        return p.claim.hold ? decide(p, GestureOwner::Widget, Gesture::MenuClick, std::nullopt, GestureEvent::ContextMenu)
                            : decide(p, GestureOwner::Bubble, Gesture::MenuClick, BubbleAction::Menu);
    }
    return verdict(p);
}

std::optional<GestureVerdict> GestureArbiter::move(double t, double x, double y) {
    if (!press_) return std::nullopt;
    Press& p = *press_;
    if (auto held = holdFirst(p, t)) return held;
    double dx = x - p.x0, dy = y - p.y0;
    if (p.locked || std::hypot(dx, dy) <= TOUCH_SLOP_PX) return verdict(p);
    p.locked = true;
    bool horizontal = std::fabs(dx) > std::fabs(dy);
    // A hold the widget won still has its axis decided by the first move past the slop: a vertical one is the chat's.
    if (p.owner == GestureOwner::Widget && p.gesture == Gesture::Hold)
        return horizontal ? verdict(p) : decide(p, GestureOwner::Bubble, Gesture::DragY, BubbleAction::Scroll);
    if (p.owner != GestureOwner::Pending) return verdict(p);
    Gesture gesture = horizontal ? Gesture::DragX : Gesture::DragY;
    // A mouse drag never pans the chat; a touch drag is the widget's only when it is horizontal and claimed:
    // a vertical one always scrolls the chat, whatever the claim says (`y` is never read).
    if (p.pointer == PointerKind::Mouse || (horizontal && p.claim.x)) return decide(p, GestureOwner::Widget, gesture);
    return decide(p, GestureOwner::Bubble, gesture, horizontal ? (p.edge ? BubbleAction::Back : BubbleAction::Swipe) : BubbleAction::Scroll);
}

std::optional<GestureVerdict> GestureArbiter::tick(double t) {
    if (!press_) return std::nullopt;
    if (auto held = holdFirst(*press_, t)) return held;
    return verdict(*press_);
}

std::optional<GestureVerdict> GestureArbiter::up(double t, double x, double y) {
    if (!press_) return std::nullopt;
    GestureVerdict v = *move(t, x, y);
    Press p = *press_;
    press_.reset();
    if (p.owner != GestureOwner::Pending) return v.cancel || v.event ? v : verdict(p);
    // A double tap over the widget is the widget's on every element, whatever touch-action says.
    if (p.second) return decide(p, GestureOwner::Widget, Gesture::DoubleTap, std::nullopt, GestureEvent::DblClick);
    lastTap_ = Tap{t, p.x0, p.y0};
    return decide(p, GestureOwner::Widget, Gesture::Tap);
}

void GestureArbiter::cancel() {
    press_.reset();
    lastTap_.reset();
}

std::optional<double> GestureArbiter::holdDeadline() const {
    if (press_ && press_->owner == GestureOwner::Pending && press_->pointer != PointerKind::Mouse) return press_->t0 + HOLD_MS;
    return std::nullopt;
}

std::optional<GestureVerdict> GestureArbiter::holdFirst(Press& p, double t) {
    if (p.owner != GestureOwner::Pending || p.pointer == PointerKind::Mouse || t - p.t0 < HOLD_MS) return std::nullopt;
    return p.claim.hold ? decide(p, GestureOwner::Widget, Gesture::Hold, std::nullopt, GestureEvent::ContextMenu)
                        : decide(p, GestureOwner::Bubble, Gesture::Hold, BubbleAction::Menu);
}

GestureVerdict GestureArbiter::decide(Press& p, GestureOwner owner, Gesture gesture, std::optional<BubbleAction> action, std::optional<GestureEvent> event) {
    p.owner = owner;
    p.gesture = gesture;
    p.action = action;
    GestureVerdict v = verdict(p);
    v.cancel = owner == GestureOwner::Bubble;
    v.event = event;
    return v;
}

GestureVerdict GestureArbiter::verdict(const Press& p) {
    GestureVerdict v;
    v.owner = p.owner;
    v.gesture = p.gesture;
    v.action = p.action;
    v.edge = p.edge;
    return v;
}

} // namespace twidget
