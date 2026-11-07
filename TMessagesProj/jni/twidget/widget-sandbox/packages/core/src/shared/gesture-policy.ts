// Who gets a press over a widget: the widget or the bubble (docs/sdk-v1.md §5.5,
// docs/decisions.md "Gestures: touch-action is the claim" and "Vertical drags are
// the chat's"). Taps and double taps over the widget are always the widget's (a
// double tap with `dblclick`; the reaction stays with the bubble outside the
// widget), and a vertical drag is always the chat's: the list scrolls over every
// widget, whatever its touch-action says (v0.1.3). For the rest the widget claims
// per element with CSS `touch-action`, read at pointerdown on the target and its
// ancestors: `auto` leaves the press to the bubble (a hold opens the message menu,
// a horizontal swipe is reply or back); `pan-y` gives the widget horizontal drags;
// `none` gives it horizontal drags and holds; `pan-x` claims nothing more than
// `auto`. Touch-action on a container covers its children, as in CSS. The first
// move past the slop locks the axis for the whole press. The leading EDGE_STRIP_PX
// of the widget never take a claim (the system back gesture).
//
// Pure and side-effect free: the renderer core (its dblclick / contextmenu and the
// web hosts' bubble), the Node harness and every client's own recognizers feed a
// GestureArbiter the press and act on its verdicts. Twins: GesturePolicy.swift,
// GesturePolicy.kt, gesture_policy.cpp; all four are checked against
// tests/fixtures/gesture-policy.json, so a rule change goes to the four copies and
// the fixture together.

/** Movement (CSS px, from the pointerdown) below which a press is still a tap or a hold. */
export const TOUCH_SLOP_PX = 10;
/** A touch held this long within the slop is a hold: the bubble's message menu, or the widget's on `none`. */
export const HOLD_MS = 500;
/** A second tap whose pointerdown comes at most this long after the first tap's pointerup is a double tap. */
export const DOUBLE_TAP_MS = 300;
/** ... and lands at most this far (CSS px) from the first tap's pointerdown. */
export const DOUBLE_TAP_SLOP_PX = 40;
/** Presses that start this close (CSS px) to the widget's leading edge take no claim: the system back gesture starts there. */
export const EDGE_STRIP_PX = 20;

/** What the widget takes of a press beyond its taps and double taps, from `touch-action` (and a learned `preventDefault` on pointerdown). */
export interface GestureClaim {
  /** Horizontal drags (no swipe-to-reply or back). */
  x: boolean;
  /**
   * Always false since v0.1.3: vertical drags are the chat's (the list scrolls), and
   * the arbiter ignores it. Kept so that code written for v0.1.2 still builds.
   */
  y: boolean;
  /** Holds, and a right click with a mouse: no message menu; the widget gets `contextmenu`. */
  hold: boolean;
}

export const NO_CLAIM: GestureClaim = Object.freeze({ x: false, y: false, hold: false });

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
export function claimOf(touchActions: readonly string[], prevented = false): GestureClaim {
  let x = prevented;
  for (const raw of touchActions) {
    const ta = raw.trim().toLowerCase();
    if (ta === '' || ta === 'auto' || ta === 'manipulation') continue;
    if (ta === 'none') return { x: true, y: false, hold: true };
    if (!/\bpan-(x|left|right)\b/.test(ta)) x = true;
  }
  return { x, y: false, hold: false };
}

/** `mouse` has no holds, no swipes and no edge strip; `touch` and `pen` have all of them. */
export type PointerKind = 'touch' | 'pen' | 'mouse';

/**
 * What a press is so far: `press` (undecided), `tap`, `double-tap` (the second tap),
 * `hold`, `drag-x` / `drag-y` (past the slop, by the dominant axis), `menu-click`
 * (the secondary mouse button).
 */
export type Gesture = 'press' | 'tap' | 'double-tap' | 'hold' | 'drag-x' | 'drag-y' | 'menu-click';

/** What the bubble does with a press it takes (a double tap over the widget never is the bubble's: no reaction). */
export type BubbleAction = 'menu' | 'swipe' | 'scroll' | 'back';

export interface GestureVerdict {
  owner: 'pending' | 'widget' | 'bubble';
  gesture: Gesture;
  /**
   * Once, in the verdict where the bubble takes a press the widget has had since its
   * pointerdown: end it for the widget now, `pointercancel` and nothing more of this
   * pointer (no pointerup, no click).
   */
  cancel: boolean;
  /** Owner `bubble`: what the bubble does with the press. */
  action?: BubbleAction;
  /** Once, at a double tap and when the widget wins a hold: deliver this event after the press's own events. */
  event?: 'dblclick' | 'contextmenu';
  /** The press started in the leading edge strip, where the widget's claim does not apply. */
  edge?: true;
}

interface Press {
  t0: number;
  x0: number;
  y0: number;
  claim: GestureClaim;
  pointer: PointerKind;
  edge: boolean;
  second: boolean;
  /** The first move past the slop has happened: the press's axis is decided. */
  locked: boolean;
  owner: GestureVerdict['owner'];
  gesture: Gesture;
  action?: BubbleAction;
}

/**
 * One pointer's presses over one widget, in order. `x` is measured from the
 * widget's leading edge (the left one in a left-to-right UI, the right one in a
 * right-to-left UI: pass `width - x` there), `y` from its top, CSS px; `t` in ms on
 * one clock. Calls with a `t` past the hold deadline decide the hold first, so a
 * late `tick` changes nothing.
 */
export class GestureArbiter {
  private press: Press | null = null;
  private lastTap: { t: number; x: number; y: number } | null = null;

  /** A primary pointerdown. `button` 2 with a mouse is the secondary click. A press still open is replaced. */
  down(t: number, x: number, y: number, claim: GestureClaim, pointer: PointerKind = 'touch', button = 0): GestureVerdict {
    const edge = pointer !== 'mouse' && x < EDGE_STRIP_PX;
    const last = this.lastTap;
    this.lastTap = null;
    const second = last !== null && t - last.t <= DOUBLE_TAP_MS && Math.hypot(x - last.x, y - last.y) <= DOUBLE_TAP_SLOP_PX;
    const p: Press = { t0: t, x0: x, y0: y, claim: edge ? NO_CLAIM : claim, pointer, edge, second, locked: false, owner: 'pending', gesture: 'press' };
    this.press = p;
    if (pointer === 'mouse' && button === 2) {
      return p.claim.hold ? this.decide(p, 'widget', 'menu-click', undefined, 'contextmenu') : this.decide(p, 'bubble', 'menu-click', 'menu');
    }
    return this.verdict(p);
  }

  /** A move of the press's pointer. Null when no press is open. */
  move(t: number, x: number, y: number): GestureVerdict | null {
    const p = this.press;
    if (!p) return null;
    const held = this.holdFirst(p, t);
    if (held) return held;
    const dx = x - p.x0, dy = y - p.y0;
    if (p.locked || Math.hypot(dx, dy) <= TOUCH_SLOP_PX) return this.verdict(p);
    p.locked = true;
    const horizontal = Math.abs(dx) > Math.abs(dy);
    // A hold the widget won still has its axis decided by the first move past the slop: a vertical one is the chat's.
    if (p.owner === 'widget' && p.gesture === 'hold') return horizontal ? this.verdict(p) : this.decide(p, 'bubble', 'drag-y', 'scroll');
    if (p.owner !== 'pending') return this.verdict(p);
    const gesture: Gesture = horizontal ? 'drag-x' : 'drag-y';
    // A mouse drag never pans the chat; a touch drag is the widget's only when it is horizontal and claimed:
    // a vertical one always scrolls the chat, whatever the claim says.
    if (p.pointer === 'mouse' || (horizontal && p.claim.x)) return this.decide(p, 'widget', gesture);
    return this.decide(p, 'bubble', gesture, horizontal ? (p.edge ? 'back' : 'swipe') : 'scroll');
  }

  /** The hold timer: call it at `holdDeadline()`. Null when no press is open. */
  tick(t: number): GestureVerdict | null {
    const p = this.press;
    if (!p) return null;
    return this.holdFirst(p, t) ?? this.verdict(p);
  }

  /** The pointerup. A press still undecided is a tap, or the second tap of a double tap. Null when no press is open. */
  up(t: number, x: number, y: number): GestureVerdict | null {
    const p = this.press;
    if (!p) return null;
    const v = this.move(t, x, y)!;
    this.press = null;
    if (p.owner !== 'pending') return v.cancel || v.event ? v : this.verdict(p);
    // A double tap over the widget is the widget's on every element, whatever touch-action says.
    if (p.second) return this.decide(p, 'widget', 'double-tap', undefined, 'dblclick');
    this.lastTap = { t, x: p.x0, y: p.y0 };
    return this.decide(p, 'widget', 'tap');
  }

  /** The platform cancelled the press (a scroll, the client's own recognizer): it is over, and no double tap continues it. */
  cancel(): void {
    this.press = null;
    this.lastTap = null;
  }

  /** When `tick` may turn the open press into a hold, or null. */
  holdDeadline(): number | null {
    const p = this.press;
    return p && p.owner === 'pending' && p.pointer !== 'mouse' ? p.t0 + HOLD_MS : null;
  }

  private holdFirst(p: Press, t: number): GestureVerdict | null {
    if (p.owner !== 'pending' || p.pointer === 'mouse' || t - p.t0 < HOLD_MS) return null;
    return p.claim.hold ? this.decide(p, 'widget', 'hold', undefined, 'contextmenu') : this.decide(p, 'bubble', 'hold', 'menu');
  }

  private decide(p: Press, owner: 'widget' | 'bubble', gesture: Gesture, action?: BubbleAction, event?: 'dblclick' | 'contextmenu'): GestureVerdict {
    p.owner = owner;
    p.gesture = gesture;
    p.action = action;
    const v = this.verdict(p);
    v.cancel = owner === 'bubble';
    if (event) v.event = event;
    return v;
  }

  private verdict(p: Press): GestureVerdict {
    const v: GestureVerdict = { owner: p.owner, gesture: p.gesture, cancel: false };
    if (p.action) v.action = p.action;
    if (p.edge) v.edge = true;
    return v;
  }
}
