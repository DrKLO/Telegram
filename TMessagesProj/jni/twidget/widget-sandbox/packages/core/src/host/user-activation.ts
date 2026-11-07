// User activation for privileged bridge calls (docs/protocol.md, "User
// activation"). The link calls (`PRIVILEGED_METHODS` in sdk-policy.ts) need a real user gesture: the
// main-thread host numbers gestures from trusted renderer events (the DOM
// event's isTrusted), hands the number to the worker with the event, and
// accepts a privileged bridge message only when it carries a gesture the host
// issued, has not consumed yet and issued recently. Nothing the worker says
// creates a gesture. Twin of the Swift/C++/Kotlin glue (WidgetHost.gesture(for:),
// GestureCounter in widget_host.h and in UserActivation.kt), which also counts
// only events the renderer marks `trusted`.

/** Pointer presses only: widgets get no keyboard (docs/decisions.md, "Profile v1"), so no key is a gesture. */
const TYPES = new Set(['click', 'dblclick', 'mousedown', 'mouseup', 'pointerdown', 'pointerup']);
/** A privileged call must reach the host this soon after its gesture was issued. */
export const GESTURE_TTL_MS = 5000;

type GestureEvent = { type?: unknown; detail?: unknown };

/** A click made by a pointer has a click count; a click a key press activates has `detail` 0. */
const pointerClick = (ev: GestureEvent) => Number(ev.detail) > 0;

/** A pointer press or release, or a click with a click count. Keys never count. */
export function isUserActivation(ev: GestureEvent): boolean {
  const type = String(ev.type ?? '');
  if (!TYPES.has(type)) return false;
  return type === 'click' || type === 'dblclick' ? pointerClick(ev) : true;
}

/**
 * One physical pointer press (pointerdown … click / dblclick) is one gesture.
 * A click without a click count (`detail` 0: what Enter or Space on a focused
 * control produces) is a gesture only as the end of a press already open.
 */
export class GestureCounter {
  private n = 0;
  private pressOpen = false;
  private lastType = '';

  /** The gesture number for this event, 0 when it is not a user activation. */
  gestureFor(ev: GestureEvent): number {
    const type = String(ev.type ?? '');
    if (!TYPES.has(type)) return 0;
    let continues = false;
    if (type === 'mousedown') continues = this.lastType === 'pointerdown';
    else if (type === 'pointerup' || type === 'mouseup') continues = this.pressOpen;
    else if (type === 'click') continues = this.pressOpen;
    else if (type === 'dblclick') continues = this.lastType === 'click';
    if (!continues && !isUserActivation(ev)) {
      // A keyboard click ends any chain: a dblclick after it is not the pointer's.
      this.pressOpen = false;
      this.lastType = '';
      return 0;
    }
    if (!continues) this.n++;
    this.pressOpen = type !== 'click' && type !== 'dblclick';
    this.lastType = type;
    return this.n;
  }

  /**
   * A primary press began: the renderer's `gesture` claim, sent for every one, whether the widget listens or not, and
   * before the press's own events. What follows is a new gesture, not the end of an open one: a finger's pointerdown is
   * not `trusted` (it may become the chat's scroll), so without this its release continued a press that had ended
   * without a click (a drag), whose gesture was spent.
   */
  pressBegan() {
    this.pressOpen = false;
    this.lastType = '';
  }
}

/** Host side: gestures handed to the worker, and the last one a privileged call consumed. */
export class HostActivationGate {
  private issued = new Map<number, number>();
  private consumed = 0;

  issue(gesture: number, now: number) {
    this.issued.set(gesture, now);
    if (this.issued.size > 64) for (const k of [...this.issued.keys()].slice(0, this.issued.size - 64)) this.issued.delete(k);
  }

  /** True once per issued gesture, while the widget is visible and the gesture is recent. */
  accept(gesture: unknown, now: number, visible: boolean): boolean {
    if (!this.available(gesture, now, visible)) return false;
    this.consumed = gesture as number;
    return true;
  }

  /** What `accept` would say, without consuming the gesture (a request consumes it only when it goes out as a press's). */
  available(gesture: unknown, now: number, visible: boolean): boolean {
    const n = typeof gesture === 'number' && Number.isInteger(gesture) ? gesture : 0;
    const at = this.issued.get(n);
    return visible && n > this.consumed && at !== undefined && now - at <= GESTURE_TTL_MS;
  }
}
