package org.telegram.utils.twidget;

import org.telegram.utils.twidget.IWidgetEngineCallback;

/**
 * The widget engine in its isolated process (WidgetEngineService), as WidgetSandboxNative is in-process: one session per
 * widget, ids from create. Everything after create is oneway and runs in order, as the native calls only enqueue work.
 */
interface IWidgetEngine {
    long create(in byte[] html, in byte[] theme, float width, float density, int cpuMultiplier, in byte[] locale,
            in byte[] storage, in byte[] sdk, in byte[] stoppedLabel, boolean visible, IWidgetEngineCallback callback);

    oneway void destroy(long session);
    oneway void visible(long session, boolean visible);
    oneway void theme(long session, in byte[] theme);
    oneway void width(long session, float width);
    oneway void data(long session, in byte[] data);
    oneway void safeArea(long session, in byte[] area);
    oneway void answerRequest(long session, long request, int failure, in byte[] result);
    oneway void repaint(long session);
    oneway void tick(long session, float timeMs, boolean frame, boolean animate, boolean paint);
    oneway void touch(long session, int action, float x, float y, int pointer, boolean primary, boolean mouse,
            int modifiers, int button);
    oneway void hover(long session, int action, float x, float y, int modifiers);

    /** The bundles read and registered now, so that the first session does not wait for them (WidgetRemoteEngine#prewarm). */
    oneway void warmup();

    /** Debug switches for sessions made from now on (WidgetSandboxNative#conformance, #attackScan, #limits). */
    void debug(boolean conformance, boolean attackScan, in byte[] limits);
}
