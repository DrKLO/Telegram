// Who gets a press over a widget: the widget or the bubble (docs/sdk-v1.md §5.5,
// docs/decisions.md "Gestures: touch-action is the claim" and "Vertical drags are
// the chat's"). A line-for-line twin of src/shared/gesture-policy.ts (see the
// comments there), GesturePolicy.swift and gesture_policy.cpp. Taps and double taps
// over the widget are always the widget's (a double tap with `dblclick`; the
// reaction stays with the bubble outside the widget), and a vertical drag is always
// the chat's: the list scrolls over every widget, whatever its touch-action says
// (v0.1.3). For the rest the widget claims per element with CSS `touch-action`,
// read by the renderer at pointerdown (WidgetHost.onGesture): `auto` leaves the
// press to the bubble (a hold opens the message menu, a horizontal swipe is reply
// or back); `pan-y` gives the widget horizontal drags; `none` gives it horizontal
// drags and holds; `pan-x` claims nothing more than `auto`. Touch-action on a
// container covers its children, as in CSS. The first move past the slop locks the
// axis for the whole press. The leading EDGE_STRIP_PX of the widget never take a
// claim (the system back gesture).
//
// Public API: a client's own recognizers over the widget feed a GestureArbiter the
// press and the claim and act on its verdicts (the demo's BubbleGestureLayout).
// Pure and side-effect free; checked against tests/fixtures/gesture-policy.json
// (copied to src/androidTest/assets by `pnpm build:android`), so a rule change
// goes to the four copies and the fixture together.

package org.telegram.widgetsandbox

import kotlin.math.abs
import kotlin.math.hypot

/**
 * On every primary pointerdown: what of this press the widget claims with CSS `touch-action`
 * (WidgetHost.onGesture, GesturePolicy.claimOf): `none` claims horizontal drags and holds, `pan-y`
 * horizontal drags, `auto` and `pan-x` nothing. Taps and double taps over the widget are always its own,
 * vertical drags always the chat's. A client's recognizers feed it to a GestureArbiter with the press.
 */
data class WidgetGestureClaim(
    /** Horizontal drags (no swipe-to-reply or back). */
    val x: Boolean,
    /**
     * Always false since v0.1.3: vertical drags are the chat's (the list scrolls), and
     * the arbiter ignores it. Kept so that code written for v0.1.2 still builds.
     */
    val y: Boolean,
    /** Holds, and a right click with a mouse: no message menu; the widget gets `contextmenu`. */
    val hold: Boolean = false,
)

/** `MOUSE` has no holds, no swipes and no edge strip; `TOUCH` and `PEN` have all of them. `value`: the name in gesture-policy.ts. */
enum class PointerKind(val value: String) { TOUCH("touch"), PEN("pen"), MOUSE("mouse") }

/**
 * What a press is so far: `PRESS` (undecided), `TAP`, `DOUBLE_TAP` (the second tap), `HOLD`,
 * `DRAG_X` / `DRAG_Y` (past the slop, by the dominant axis), `MENU_CLICK` (the secondary mouse button).
 */
enum class Gesture(val value: String) {
    PRESS("press"), TAP("tap"), DOUBLE_TAP("double-tap"), HOLD("hold"), DRAG_X("drag-x"), DRAG_Y("drag-y"), MENU_CLICK("menu-click"),
}

/** What the bubble does with a press it takes (a double tap over the widget never is the bubble's: no reaction). */
enum class BubbleAction(val value: String) { MENU("menu"), SWIPE("swipe"), SCROLL("scroll"), BACK("back") }

/** Who has the press: nobody yet, the widget or the bubble. */
enum class GestureOwner(val value: String) { PENDING("pending"), WIDGET("widget"), BUBBLE("bubble") }

/** The event a widget gets for a double tap (always) or a hold it claims; the renderer delivers it itself. */
enum class GestureEvent(val value: String) { DBLCLICK("dblclick"), CONTEXTMENU("contextmenu") }

data class GestureVerdict(
    val owner: GestureOwner,
    val gesture: Gesture,
    /**
     * Once, in the verdict where the bubble takes a press the widget has had since its
     * pointerdown: end it for the widget now (ACTION_CANCEL to the renderer's view:
     * `pointercancel` and nothing more of this pointer, no pointerup, no click).
     */
    val cancel: Boolean,
    /** Owner `BUBBLE`: what the bubble does with the press. */
    val action: BubbleAction? = null,
    /** Once, when the widget wins a double tap or a hold: the renderer gives the widget this event after the press's own. */
    val event: GestureEvent? = null,
    /** The press started in the leading edge strip, where the widget's claim does not apply. */
    val edge: Boolean = false,
)

object GesturePolicy {
    /** Movement (CSS px, from the pointerdown) below which a press is still a tap or a hold. */
    const val TOUCH_SLOP_PX = 10.0
    /** A touch held this long within the slop is a hold: the bubble's message menu, or the widget's on `none`. */
    const val HOLD_MS = 500.0
    /** A second tap whose pointerdown comes at most this long after the first tap's pointerup is a double tap. */
    const val DOUBLE_TAP_MS = 300.0
    /** ... and lands at most this far (CSS px) from the first tap's pointerdown. */
    const val DOUBLE_TAP_SLOP_PX = 40.0
    /** Presses that start this close (CSS px) to the widget's leading edge take no claim: the system back gesture starts there. */
    const val EDGE_STRIP_PX = 20.0

    val NO_CLAIM = WidgetGestureClaim(x = false, y = false)

    private val HORIZONTAL_PANS = listOf("pan-x", "pan-left", "pan-right")

    /**
     * The claim of a press from the computed `touch-action` of the target and of each
     * ancestor up to the root (the widget's `<html>`) included, target first, and whether the widget
     * prevented pointerdown on the target before. `none` anywhere claims horizontal
     * drags and holds; a value without a horizontal pan (`pan-x`, `pan-left`,
     * `pan-right`) claims `x`; `auto` and `manipulation` claim nothing, and so does a
     * value with a horizontal pan (`pan-x`: vertical drags cannot be claimed). A
     * prevented pointerdown claims `x`, never holds: only `touch-action: none` does.
     * `y` is always false.
     */
    fun claimOf(touchActions: List<String>, prevented: Boolean = false): WidgetGestureClaim {
        var x = prevented
        for (raw in touchActions) {
            val ta = asciiLower(raw.trim { isJsSpace(it) })
            if (ta == "" || ta == "auto" || ta == "manipulation") continue
            if (ta == "none") return WidgetGestureClaim(x = true, y = false, hold = true)
            if (!hasWord(ta, HORIZONTAL_PANS)) x = true
        }
        return WidgetGestureClaim(x, y = false)
    }

    /** What String.prototype.trim removes: ECMAScript's WhiteSpace and LineTerminator. */
    private fun isJsSpace(c: Char) = c == ' ' || c in '\t'..'\r' || c == ' ' || c == ' ' || c in ' '..' ' ||
        c == ' ' || c == ' ' || c == ' ' || c == ' ' || c == '　' || c == '﻿'

    /** Lowercases A–Z only (the values are ASCII keywords). */
    private fun asciiLower(s: String): String {
        val b = StringBuilder(s.length)
        for (ch in s) b.append(if (ch in 'A'..'Z') ch + 32 else ch)
        return b.toString()
    }

    private fun isWordChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

    /** One of `words` between JavaScript `\b` boundaries (ASCII word characters), as `/\bpan-(x|left|right)\b/`. */
    private fun hasWord(s: String, words: List<String>): Boolean {
        for (w in words) {
            var i = s.indexOf(w)
            while (i >= 0) {
                val end = i + w.length
                if ((i == 0 || !isWordChar(s[i - 1])) && (end == s.length || !isWordChar(s[end]))) return true
                i = s.indexOf(w, i + 1)
            }
        }
        return false
    }
}

/**
 * One pointer's presses over one widget, in order. `x` is measured from the
 * widget's leading edge (the left one in a left-to-right UI, the right one in a
 * right-to-left UI: pass `width - x` there), `y` from its top, CSS px (= dp); `t`
 * in ms on one clock (MotionEvent.eventTime). Calls with a `t` past the hold
 * deadline decide the hold first, so a late `tick` changes nothing. Not
 * thread-safe: one thread (the client's main thread).
 */
class GestureArbiter {
    private class Press(val t0: Double, val x0: Double, val y0: Double, val claim: WidgetGestureClaim, val pointer: PointerKind, val edge: Boolean, val second: Boolean) {
        /** The press has left the slop once: its axis is decided. */
        var locked = false
        var owner = GestureOwner.PENDING
        var gesture = Gesture.PRESS
        var action: BubbleAction? = null
    }

    private class Tap(val t: Double, val x: Double, val y: Double)

    private var press: Press? = null
    private var lastTap: Tap? = null

    /** A primary pointerdown. `button` 2 with a mouse is the secondary click. A press still open is replaced. */
    fun down(t: Double, x: Double, y: Double, claim: WidgetGestureClaim, pointer: PointerKind = PointerKind.TOUCH, button: Int = 0): GestureVerdict {
        val edge = pointer != PointerKind.MOUSE && x < GesturePolicy.EDGE_STRIP_PX
        val last = lastTap
        lastTap = null
        val second = last != null && t - last.t <= GesturePolicy.DOUBLE_TAP_MS && hypot(x - last.x, y - last.y) <= GesturePolicy.DOUBLE_TAP_SLOP_PX
        val p = Press(t, x, y, if (edge) GesturePolicy.NO_CLAIM else claim, pointer, edge, second)
        press = p
        if (pointer == PointerKind.MOUSE && button == 2) {
            return if (p.claim.hold) decide(p, GestureOwner.WIDGET, Gesture.MENU_CLICK, null, GestureEvent.CONTEXTMENU)
            else decide(p, GestureOwner.BUBBLE, Gesture.MENU_CLICK, BubbleAction.MENU)
        }
        return verdict(p)
    }

    /** A move of the press's pointer. Null when no press is open. */
    fun move(t: Double, x: Double, y: Double): GestureVerdict? {
        val p = press ?: return null
        holdFirst(p, t)?.let { return it }
        val dx = x - p.x0
        val dy = y - p.y0
        if (p.locked || hypot(dx, dy) <= GesturePolicy.TOUCH_SLOP_PX) return verdict(p)
        p.locked = true
        val horizontal = abs(dx) > abs(dy)
        // A hold the widget won still has its axis decided by the first move past the slop: a vertical one is the chat's.
        if (p.owner == GestureOwner.WIDGET && p.gesture == Gesture.HOLD) {
            return if (horizontal) verdict(p) else decide(p, GestureOwner.BUBBLE, Gesture.DRAG_Y, BubbleAction.SCROLL)
        }
        if (p.owner != GestureOwner.PENDING) return verdict(p)
        val gesture = if (horizontal) Gesture.DRAG_X else Gesture.DRAG_Y
        // A mouse drag never pans the chat; a touch drag is the widget's only when it is horizontal and claimed:
        // a vertical one always scrolls the chat, whatever the claim says.
        if (p.pointer == PointerKind.MOUSE || (horizontal && p.claim.x)) return decide(p, GestureOwner.WIDGET, gesture)
        return decide(p, GestureOwner.BUBBLE, gesture, if (horizontal) (if (p.edge) BubbleAction.BACK else BubbleAction.SWIPE) else BubbleAction.SCROLL)
    }

    /** The hold timer: call it at `holdDeadline()`. Null when no press is open. */
    fun tick(t: Double): GestureVerdict? {
        val p = press ?: return null
        return holdFirst(p, t) ?: verdict(p)
    }

    /** The pointerup. A press still undecided is a tap, or the second tap of a double tap. Null when no press is open. */
    fun up(t: Double, x: Double, y: Double): GestureVerdict? {
        val p = press ?: return null
        val v = move(t, x, y)!!
        press = null
        if (p.owner != GestureOwner.PENDING) return if (v.cancel || v.event != null) v else verdict(p)
        // A double tap over the widget is the widget's on every element, whatever touch-action says.
        if (p.second) return decide(p, GestureOwner.WIDGET, Gesture.DOUBLE_TAP, null, GestureEvent.DBLCLICK)
        lastTap = Tap(t, p.x0, p.y0)
        return decide(p, GestureOwner.WIDGET, Gesture.TAP)
    }

    /** The platform cancelled the press (a scroll, the client's own recognizer): it is over, and no double tap continues it. */
    fun cancel() {
        press = null
        lastTap = null
    }

    /** When `tick` may turn the open press into a hold, or null. */
    fun holdDeadline(): Double? {
        val p = press ?: return null
        return if (p.owner == GestureOwner.PENDING && p.pointer != PointerKind.MOUSE) p.t0 + GesturePolicy.HOLD_MS else null
    }

    private fun holdFirst(p: Press, t: Double): GestureVerdict? {
        if (p.owner != GestureOwner.PENDING || p.pointer == PointerKind.MOUSE || t - p.t0 < GesturePolicy.HOLD_MS) return null
        return if (p.claim.hold) decide(p, GestureOwner.WIDGET, Gesture.HOLD, null, GestureEvent.CONTEXTMENU)
        else decide(p, GestureOwner.BUBBLE, Gesture.HOLD, BubbleAction.MENU)
    }

    private fun decide(p: Press, owner: GestureOwner, gesture: Gesture, action: BubbleAction? = null, event: GestureEvent? = null): GestureVerdict {
        p.owner = owner
        p.gesture = gesture
        p.action = action
        return verdict(p).copy(cancel = owner == GestureOwner.BUBBLE, event = event)
    }

    private fun verdict(p: Press) = GestureVerdict(p.owner, p.gesture, cancel = false, action = p.action, edge = p.edge)
}
