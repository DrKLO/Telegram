package org.telegram.utils.twidget;

/** Android binding to the upstream C++ WidgetHost; inputs only enqueue worker commands. */
final class WidgetSandboxNative {
    // Loaded by the client's NativeLoader. Registered by its existing JNI_OnLoad.

    static native long create(
            byte[] directory,
            byte[] html,
            byte[] theme,
            float width,
            float density,
            int cpuMultiplier,
            byte[] locale,
            byte[] storage,
            byte[] sdk,
            byte[] stoppedLabel,
            boolean visible,
            WidgetSandboxBackend backend);

    static native void destroy(long session);

    static native void visible(long session, boolean visible);

    // A WidgetSandboxTheme JSON: the colour scheme, the palette and the button tokens.
    static native void theme(long session, byte[] theme);

    static native void width(long session, float width);

    // The widget's full state, {"chatData","publicData"} (null for none): updateMessageWidgetData, a polled public_data.
    static native void data(long session, byte[] data);

    // What the client draws over the widget, {"top","right","bottom","left","cornerLeft","cornerRight"} in CSS px
    // (docs/sdk-v1.md §2.17): only when the bubble's layout changes it, never per frame.
    static native void safeArea(long session, byte[] area);

    // The answer to the widget's request or wallet call `request` (signal 12, 19): `failure` 0 answered (`result`, null
    // for none), 1 timeout, 2 rate limited, 3 failed; a wallet call's also 4 rejected, 5 bad request (docs/sdk-v1.md
    // §5.11, §5.13; WidgetSandboxSession#REQUEST_ANSWERED and the rest).
    static native void answerRequest(long session, long request, int failure, byte[] result);

    // Re-record the existing layout when Android has released a display list.
    static native void repaint(long session);

    // `frame` delivers requestAnimationFrame, `animate` advances CSS/Web Animations, `paint` records.
    static native void tick(
            long session, float timeMs, boolean frame, boolean animate, boolean paint);

    // A pointer in CSS px. A mouse (`mouse`) sends no touch events; `modifiers` bit0 alt, bit1 ctrl, bit2 meta,
    // bit3 shift; `button` 2 is the secondary one (docs/sdk-v1.md §5.5).
    static native void touch(
            long session, int action, float x, float y, int pointer, boolean primary, boolean mouse, int modifiers,
            int button);

    // A mouse over the widget with no button down (0) or leaving it (1): :hover, the over/out events, the cursor.
    static native void hover(long session, int action, float x, float y, int modifiers);

    // CSS filters ("fl" of a FilterPush, as JSON) over an ARGB_8888 bitmap, in place; `scale` px per CSS px.
    static native void filterPixels(android.graphics.Bitmap bitmap, byte[] filters, float scale);

    // Debug: every session reports its widget's `data-conformance` attribute (signal 9) while on.
    static native void conformance(boolean enabled);

    // Debug: limits every new session takes instead of the defaults (the stress kit's passes), JSON as UTF-8; null: the defaults.
    static native void limits(byte[] json);

    // Debug: every session made while on reports a URL that could reach the network (signal 10).
    static native void attackScan(boolean enabled);

    // widget-sandbox's GestureArbiter (twidget/gesture_policy.h), one per WidgetGestureArbiter; UI thread.
    static native long arbiterCreate();

    static native void arbiterDestroy(long arbiter);

    static native int arbiterDown(
            long arbiter, double t, double x, double y, boolean claimX, boolean claimY, boolean hold, boolean mouse, int button);

    static native int arbiterMove(long arbiter, double t, double x, double y);

    static native int arbiterTick(long arbiter, double t);

    static native int arbiterUp(long arbiter, double t, double x, double y);

    static native void arbiterCancel(long arbiter);

    static native double arbiterHoldDeadline(long arbiter);

    private WidgetSandboxNative() {}
}
