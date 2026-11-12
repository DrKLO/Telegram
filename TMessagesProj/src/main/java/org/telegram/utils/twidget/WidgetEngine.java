package org.telegram.utils.twidget;

import android.content.Context;

import java.nio.charset.StandardCharsets;

/**
 * Where a session's native engine (WidgetHost: the widget's JavaScript, the validator and TwidgetCore) runs: in the
 * isolated process (WidgetRemoteEngine, the default) or in this one (WidgetSandboxNative). The session talks to either
 * the same way; frames come back to its WidgetSandboxBackend, which paints them here in both cases.
 */
abstract class WidgetEngine {
    /** Debug: sessions made from now on run in the isolated process (true) or in this one. */
    static volatile boolean isolated = true;

    // Debug switches for sessions made from now on (WidgetSandboxNative#conformance, #attackScan, #limits): the
    // isolated engine takes them with each session (WidgetRemoteEngine), the in-process one at its create. Only the
    // in-process engine loads libtwidget here.
    static volatile boolean conformance, attackScan;
    static volatile byte[] limits;

    static WidgetEngine current() {
        return isolated ? WidgetRemoteEngine.INSTANCE : LOCAL;
    }

    static void conformance(boolean enabled) {
        conformance = enabled;
    }

    static void attackScan(boolean enabled) {
        attackScan = enabled;
    }

    static void limits(byte[] json) {
        limits = json;
    }

    /** A new session on the calling background thread; its id for the other calls. */
    abstract long create(Context context, String html, String theme, float width, float density, int cpuMultiplier,
            String locale, String storage, String sdk, String stoppedLabel, boolean visible, WidgetSandboxBackend backend)
            throws Exception;

    abstract void destroy(long session);

    abstract void visible(long session, boolean visible);

    abstract void theme(long session, byte[] theme);

    abstract void width(long session, float width);

    abstract void data(long session, byte[] data);

    abstract void safeArea(long session, byte[] area);

    abstract void answerRequest(long session, long request, int failure, byte[] result);

    abstract void repaint(long session);

    abstract void tick(long session, float timeMs, boolean frame, boolean animate, boolean paint);

    abstract void touch(long session, int action, float x, float y, int pointer, boolean primary, boolean mouse,
            int modifiers, int button);

    abstract void hover(long session, int action, float x, float y, int modifiers);

    /**
     * In this process: libtwidget loaded into the client, for private builds' comparisons only
     * (WidgetConformanceActivity --ez inProcess true).
     */
    static final WidgetEngine LOCAL = new WidgetEngine() {
        @Override
        long create(Context context, String html, String theme, float width, float density, int cpuMultiplier,
                String locale, String storage, String sdk, String stoppedLabel, boolean visible,
                WidgetSandboxBackend backend) throws Exception {
            // The first touch of WidgetSandboxNative loads libtwidget into this process: private builds' comparisons only.
            if (!WidgetSandboxNative.available()) throw new IllegalStateException("libtwidget is missing");
            WidgetSandboxNative.conformance(conformance);
            WidgetSandboxNative.attackScan(attackScan);
            WidgetSandboxNative.limits(limits);
            String directory = WidgetSandboxResources.prepare(context).getAbsolutePath();
            return WidgetSandboxNative.create(
                    utf8(directory), utf8(html), utf8(theme), width, density, cpuMultiplier, utf8(locale), utf8(storage),
                    utf8(sdk), utf8(stoppedLabel), visible, backend);
        }

        @Override
        void destroy(long session) {
            WidgetSandboxNative.destroy(session);
        }

        @Override
        void visible(long session, boolean visible) {
            WidgetSandboxNative.visible(session, visible);
        }

        @Override
        void theme(long session, byte[] theme) {
            WidgetSandboxNative.theme(session, theme);
        }

        @Override
        void width(long session, float width) {
            WidgetSandboxNative.width(session, width);
        }

        @Override
        void data(long session, byte[] data) {
            WidgetSandboxNative.data(session, data);
        }

        @Override
        void safeArea(long session, byte[] area) {
            WidgetSandboxNative.safeArea(session, area);
        }

        @Override
        void answerRequest(long session, long request, int failure, byte[] result) {
            WidgetSandboxNative.answerRequest(session, request, failure, result);
        }

        @Override
        void repaint(long session) {
            WidgetSandboxNative.repaint(session);
        }

        @Override
        void tick(long session, float timeMs, boolean frame, boolean animate, boolean paint) {
            WidgetSandboxNative.tick(session, timeMs, frame, animate, paint);
        }

        @Override
        void touch(long session, int action, float x, float y, int pointer, boolean primary, boolean mouse,
                int modifiers, int button) {
            WidgetSandboxNative.touch(session, action, x, y, pointer, primary, mouse, modifiers, button);
        }

        @Override
        void hover(long session, int action, float x, float y, int modifiers) {
            WidgetSandboxNative.hover(session, action, x, y, modifiers);
        }
    };

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
