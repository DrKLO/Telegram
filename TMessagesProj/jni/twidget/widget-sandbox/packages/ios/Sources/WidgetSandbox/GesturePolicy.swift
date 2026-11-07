// Who gets a press over a widget, the widget or the bubble: a line-for-line
// mirror of src/shared/gesture-policy.ts (see the comments there; docs/sdk-v1.md
// §5.5, docs/decisions.md "Gestures: touch-action is the claim" and "Vertical drags
// are the chat's"). Taps and double taps over the widget are always the widget's
// (a double tap with `dblclick`; the reaction stays with the bubble outside the
// widget), and a vertical drag is always the chat's: the list scrolls over every
// widget, whatever its touch-action says (v0.1.3). For the rest the widget claims
// per element with CSS `touch-action`, read by the renderer at pointerdown on the
// target and its ancestors and reported as `WidgetHost.onGestureClaim`: `auto`
// leaves the press to the bubble (a hold opens the message menu, a horizontal
// swipe is reply or back); `pan-y` gives the widget horizontal drags; `none` gives
// it horizontal drags and holds; `pan-x` claims nothing more than `auto`.
// Touch-action on a container covers its children, as in CSS. The first move past
// the slop locks the axis for the whole press, a hold the widget won included (a
// vertical one still goes to the chat). The leading `edgeStripPx` of the widget
// never take a claim (the system back gesture).
//
// A client's own recognizers over the widget feed a GestureArbiter the press
// and act on its verdicts: with `cancel`, take the touch from the renderer view
// (the widget gets pointercancel) and do what `action` says.
//
// Pure and side-effect free; checked against tests/fixtures/gesture-policy.json
// (copied to Tests/WidgetSandboxTests/Fixtures by `pnpm build:ios`), shared with
// gesture-policy.ts, GesturePolicy.kt and gesture_policy.cpp: a rule change goes
// to the four copies and the fixture together.

import Foundation

public enum GesturePolicy {
    /// Movement (points, from the pointerdown) below which a press is still a tap or a hold.
    public static let touchSlopPx = 10.0
    /// A touch held this long within the slop is a hold: the bubble's message menu, or the widget's on `none`.
    public static let holdMs = 500.0
    /// A second tap whose pointerdown comes at most this long after the first tap's pointerup is a double tap.
    public static let doubleTapMs = 300.0
    /// ... and lands at most this far (points) from the first tap's pointerdown.
    public static let doubleTapSlopPx = 40.0
    /// Presses that start this close (points) to the widget's leading edge take no claim: the system back gesture starts there.
    public static let edgeStripPx = 20.0

    /// Claims nothing: the bubble keeps every gesture of the press.
    public static let noClaim = WidgetGestureClaim(x: false, y: false)

    /// `mouse` has no holds, no swipes and no edge strip; `touch` and `pen` have all of them.
    public enum PointerKind: String {
        case touch, pen, mouse
    }

    /// The claim of a press from the computed `touch-action` of the target and of each ancestor up to the root (the
    /// widget's `<html>`) included, target first, and whether the widget prevented pointerdown on the target before.
    /// `none` anywhere claims horizontal drags and holds; a value without a horizontal pan (`pan-x`, `pan-left`,
    /// `pan-right`) claims `x`; `auto` and `manipulation` claim nothing, and so does a value with a horizontal pan
    /// (`pan-x`: vertical drags cannot be claimed). A prevented pointerdown claims `x`, never holds: only
    /// `touch-action: none` does. `y` is always false. Taps and double taps are always the widget's: no claim needed.
    /// The renderer computes this itself; clients get the result as the claim.
    public static func claimOf(_ touchActions: [String], prevented: Bool = false) -> WidgetGestureClaim {
        var x = prevented
        for raw in touchActions {
            let ta = asciiLower(raw.trimmingCharacters(in: jsWhitespace))
            let value = String(decoding: ta, as: UTF16.self)
            if value.isEmpty || value == "auto" || value == "manipulation" { continue }
            if value == "none" { return WidgetGestureClaim(x: true, y: false, hold: true) }
            if !containsWord(ta, horizontalPans) { x = true }
        }
        return WidgetGestureClaim(x: x, y: false)
    }

    /// What `String.prototype.trim` removes: Unicode spaces and line breaks and U+FEFF, not U+0085.
    private static let jsWhitespace = CharacterSet.whitespacesAndNewlines.subtracting(CharacterSet(charactersIn: "\u{85}")).union(CharacterSet(charactersIn: "\u{FEFF}"))
    private static let horizontalPans = ["pan-x", "pan-left", "pan-right"].map { Array($0.utf16) }

    /// Lowercases A–Z only (the computed value is ASCII; a Unicode mapping must not make a keyword out of something else).
    private static func asciiLower(_ s: String) -> [UInt16] {
        s.utf16.map { $0 >= 65 && $0 <= 90 ? $0 + 32 : $0 }
    }

    private static func isWordChar(_ c: UInt16) -> Bool {
        (c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || c == 95
    }

    /// Whether `s` holds one of `words` with no word character (`[A-Za-z0-9_]`) on either side: the TS `/\bpan-(x|left|right)\b/`.
    private static func containsWord(_ s: [UInt16], _ words: [[UInt16]]) -> Bool {
        for w in words where s.count >= w.count {
            for i in 0...(s.count - w.count) where s[i..<i + w.count].elementsEqual(w) {
                let end = i + w.count
                if (i == 0 || !isWordChar(s[i - 1])) && (end == s.count || !isWordChar(s[end])) { return true }
            }
        }
        return false
    }
}

/// What the arbiter makes of a press so far, and who owns it.
public struct GestureVerdict: Equatable {
    public enum Owner: String {
        case pending, widget, bubble
    }

    /// What a press is so far: `press` (undecided), `tap`, `doubleTap` (the second tap), `hold`, `dragX` / `dragY`
    /// (past the slop, by the dominant axis), `menuClick` (the secondary mouse button). Raw values are the TS names.
    public enum Gesture: String {
        case press, tap, doubleTap = "double-tap", hold, dragX = "drag-x", dragY = "drag-y", menuClick = "menu-click"
    }

    /// What the bubble does with a press it takes (a double tap over the widget never is the bubble's: no reaction).
    public enum BubbleAction: String {
        case menu, swipe, scroll, back
    }

    /// The event the widget gets for a double tap or a hold it wins (the renderer sends it itself).
    public enum Event: String {
        case dblclick, contextmenu
    }

    public var owner: Owner
    public var gesture: Gesture
    /// Once, in the verdict where the bubble takes a press the widget has had since its pointerdown: end it for the
    /// widget now (`pointercancel`, nothing more of this pointer: no pointerup, no click).
    public var cancel: Bool
    /// Owner `bubble`: what the bubble does with the press.
    public var action: BubbleAction?
    /// Once, when the widget wins a double tap or a hold: the renderer delivers this event after the press's own events.
    public var event: Event?
    /// The press started in the leading edge strip, where the widget's claim does not apply.
    public var edge: Bool

    public init(owner: Owner, gesture: Gesture, cancel: Bool = false, action: BubbleAction? = nil, event: Event? = nil, edge: Bool = false) {
        self.owner = owner
        self.gesture = gesture
        self.cancel = cancel
        self.action = action
        self.event = event
        self.edge = edge
    }
}

/// One pointer's presses over one widget, in order. `x` is measured from the widget's leading edge (the left one
/// in a left-to-right UI, the right one in a right-to-left UI: pass `width - x` there), `y` from its top, points;
/// `t` in ms on one clock. Calls with a `t` past the hold deadline decide the hold first, so a late `tick` changes
/// nothing.
public struct GestureArbiter {
    private struct Press {
        var t0: Double
        var x0: Double
        var y0: Double
        var claim: WidgetGestureClaim
        var pointer: GesturePolicy.PointerKind
        var edge: Bool
        var second: Bool
        /// The first move past the slop has happened: the press's axis is decided.
        var locked = false
        var owner = GestureVerdict.Owner.pending
        var gesture = GestureVerdict.Gesture.press
        var action: GestureVerdict.BubbleAction?
    }

    private var press: Press?
    private var lastTap: (t: Double, x: Double, y: Double)?

    public init() {}

    /// A primary pointerdown. `button` 2 with a mouse is the secondary click. A press still open is replaced.
    public mutating func down(at t: Double, x: Double, y: Double, claim: WidgetGestureClaim, pointer: GesturePolicy.PointerKind = .touch, button: Int = 0) -> GestureVerdict {
        let edge = pointer != .mouse && x < GesturePolicy.edgeStripPx
        let last = lastTap
        lastTap = nil
        let second = last.map { t - $0.t <= GesturePolicy.doubleTapMs && hypot(x - $0.x, y - $0.y) <= GesturePolicy.doubleTapSlopPx } ?? false
        var p = Press(t0: t, x0: x, y0: y, claim: edge ? GesturePolicy.noClaim : claim, pointer: pointer, edge: edge, second: second)
        defer { press = p }
        if pointer == .mouse && button == 2 {
            return p.claim.hold ? Self.decide(&p, .widget, .menuClick, event: .contextmenu) : Self.decide(&p, .bubble, .menuClick, action: .menu)
        }
        return Self.verdict(p)
    }

    /// A move of the press's pointer. Nil when no press is open.
    public mutating func move(at t: Double, x: Double, y: Double) -> GestureVerdict? {
        guard var p = press else { return nil }
        defer { press = p }
        if let held = Self.holdFirst(&p, t) { return held }
        let dx = x - p.x0, dy = y - p.y0
        if p.locked || hypot(dx, dy) <= GesturePolicy.touchSlopPx { return Self.verdict(p) }
        p.locked = true
        let horizontal = abs(dx) > abs(dy)
        // A hold the widget won still has its axis decided by the first move past the slop: a vertical one is the chat's.
        if p.owner == .widget && p.gesture == .hold { return horizontal ? Self.verdict(p) : Self.decide(&p, .bubble, .dragY, action: .scroll) }
        if p.owner != .pending { return Self.verdict(p) }
        let gesture: GestureVerdict.Gesture = horizontal ? .dragX : .dragY
        // A mouse drag never pans the chat; a touch drag is the widget's only when it is horizontal and claimed:
        // a vertical one always scrolls the chat, whatever the claim says (the arbiter never reads `y`).
        if p.pointer == .mouse || (horizontal && p.claim.x) { return Self.decide(&p, .widget, gesture) }
        return Self.decide(&p, .bubble, gesture, action: horizontal ? (p.edge ? .back : .swipe) : .scroll)
    }

    /// The hold timer: call it at `holdDeadline()`. Nil when no press is open.
    public mutating func tick(at t: Double) -> GestureVerdict? {
        guard var p = press else { return nil }
        defer { press = p }
        return Self.holdFirst(&p, t) ?? Self.verdict(p)
    }

    /// The pointerup. A press still undecided is a tap, or the second tap of a double tap. Nil when no press is open.
    public mutating func up(at t: Double, x: Double, y: Double) -> GestureVerdict? {
        guard let v = move(at: t, x: x, y: y), var p = press else { return nil }
        press = nil
        if p.owner != .pending { return v.cancel || v.event != nil ? v : Self.verdict(p) }
        // A double tap over the widget is the widget's on every element, whatever touch-action says.
        if p.second { return Self.decide(&p, .widget, .doubleTap, event: .dblclick) }
        lastTap = (t, p.x0, p.y0)
        return Self.decide(&p, .widget, .tap)
    }

    /// The platform cancelled the press (a scroll, the client's own recognizer): it is over, and no double tap continues it.
    public mutating func cancel() {
        press = nil
        lastTap = nil
    }

    /// When `tick` may turn the open press into a hold, or nil.
    public func holdDeadline() -> Double? {
        guard let p = press, p.owner == .pending, p.pointer != .mouse else { return nil }
        return p.t0 + GesturePolicy.holdMs
    }

    private static func holdFirst(_ p: inout Press, _ t: Double) -> GestureVerdict? {
        if p.owner != .pending || p.pointer == .mouse || t - p.t0 < GesturePolicy.holdMs { return nil }
        return p.claim.hold ? decide(&p, .widget, .hold, event: .contextmenu) : decide(&p, .bubble, .hold, action: .menu)
    }

    private static func decide(_ p: inout Press, _ owner: GestureVerdict.Owner, _ gesture: GestureVerdict.Gesture, action: GestureVerdict.BubbleAction? = nil, event: GestureVerdict.Event? = nil) -> GestureVerdict {
        p.owner = owner
        p.gesture = gesture
        p.action = action
        var v = verdict(p)
        v.cancel = owner == .bubble
        v.event = event
        return v
    }

    private static func verdict(_ p: Press) -> GestureVerdict {
        GestureVerdict(owner: p.owner, gesture: p.gesture, action: p.action, edge: p.edge)
    }
}
