package org.telegram.utils.twidget;

/**
 * widget-sandbox's gesture policy (docs/sdk-v1.md §5.5): who gets a press over a widget, the widget or the bubble.
 * The client's recognizers over one widget feed it the press with the widget's claim and act on its verdicts.
 * The rules are widget-sandbox's own C++ GestureArbiter (twidget/gesture_policy.h, checked against the shared
 * fixture on every platform), reached through the bridge, so the chat follows the same policy as the sandbox
 * and the renderer that sends the widget its dblclick and contextmenu. UI thread.
 *
 * <p>Coordinates are CSS px from the widget's leading edge (the right one in a right-to-left UI) and its top;
 * times are ms on one clock. A verdict is an int: {@link #NONE} for no open press, else its fields through
 * {@link #owner}, {@link #gesture}, {@link #cancel}, {@link #action}, {@link #edge}.
 */
public final class WidgetGestureArbiter {

    public static final int NONE = -1;
    public static final int OWNER_PENDING = 0, OWNER_WIDGET = 1, OWNER_BUBBLE = 2;
    public static final int PRESS = 0, TAP = 1, DOUBLE_TAP = 2, HOLD = 3, DRAG_X = 4, DRAG_Y = 5, MENU_CLICK = 6;
    public static final int ACTION_NONE = 0, ACTION_MENU = 1, ACTION_SWIPE = 2, ACTION_SCROLL = 3, ACTION_BACK = 4;

    private long handle;

    private long handle() {
        if (handle == 0) handle = WidgetSandboxNative.arbiterCreate();
        return handle;
    }

    /** A primary press starts; a press still open is replaced. */
    public int down(double t, double x, double y, boolean claimX, boolean claimY, boolean hold) {
        return down(t, x, y, claimX, claimY, hold, false, 0);
    }

    /** A press of a mouse (`button` 2: the secondary) or a finger. */
    public int down(double t, double x, double y, boolean claimX, boolean claimY, boolean hold, boolean mouse, int button) {
        return WidgetSandboxNative.arbiterDown(handle(), t, x, y, claimX, claimY, hold, mouse, button);
    }

    public int move(double t, double x, double y) {
        return handle == 0 ? NONE : WidgetSandboxNative.arbiterMove(handle, t, x, y);
    }

    /** The hold timer, at {@link #holdDeadline}. */
    public int tick(double t) {
        return handle == 0 ? NONE : WidgetSandboxNative.arbiterTick(handle, t);
    }

    public int up(double t, double x, double y) {
        return handle == 0 ? NONE : WidgetSandboxNative.arbiterUp(handle, t, x, y);
    }

    /** The platform took the press: it is over, and no double tap continues it. */
    public void cancel() {
        if (handle != 0) WidgetSandboxNative.arbiterCancel(handle);
    }

    /** When {@link #tick} may make the open press a hold, ms; negative for none. */
    public double holdDeadline() {
        return handle == 0 ? -1 : WidgetSandboxNative.arbiterHoldDeadline(handle);
    }

    /** Frees the native arbiter; the next press makes a new one (and no double tap spans the two). */
    public void release() {
        if (handle == 0) return;
        WidgetSandboxNative.arbiterDestroy(handle);
        handle = 0;
    }

    public static int owner(int verdict) {
        return verdict & 3;
    }

    public static int gesture(int verdict) {
        return (verdict >> 2) & 7;
    }

    /** The bubble takes a press the widget has had since its pointerdown: end it for the widget now. */
    public static boolean cancel(int verdict) {
        return (verdict & (1 << 5)) != 0;
    }

    /** What the bubble does with a press it takes, ACTION_*. */
    public static int action(int verdict) {
        return (verdict >> 6) & 7;
    }

    /** The press started in the leading edge strip, where the widget's claim does not apply. */
    public static boolean edge(int verdict) {
        return (verdict & (1 << 11)) != 0;
    }
}
