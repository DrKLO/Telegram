package org.telegram.utils.twidget;

/**
 * What the client process runs of the widget engine's native code: widget-sandbox's gesture policy and twidget's CPU
 * filter pass, compiled into the client library (jni/twidget/twidget-client.cpp), so this process never loads
 * libtwidget.so, the engine (QuickJS, the validator, the renderer) that only the isolated WidgetEngineService maps.
 * Neither piece reads widget code. Resolved by name; the client library is loaded at startup (NativeLoader).
 */
final class WidgetClientNative {

    // CSS filters ("fl" of a FilterPush: display_list.h Filter [kind, amount, x, y, ARGB] as parallel arrays) over an
    // ARGB_8888 bitmap, in place; `scale` px per CSS px.
    static native void filterPixels(
            android.graphics.Bitmap bitmap, int[] kind, float[] amount, float[] x, float[] y, int[] color, float scale);

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

    private WidgetClientNative() {}
}
