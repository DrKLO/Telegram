package org.telegram.widgets;

/**
 * JNI surface of widget_host_jni.cpp: widget-sandbox's C++ WidgetHost (QuickJS, the trusted validator, the
 * sandbox engine) for one widget, over a renderer that lives in Java (WidgetRenderer). The host runs on threads of
 * its own; it calls the renderer and the listener there, and both hand what they get to the main thread.
 */
public final class WidgetHostNative {

    static {
        System.loadLibrary("twidget_litehtml");
    }

    private WidgetHostNative() {
    }

    /** What the host asks of the renderer, on the host's thread (twidget::Renderer in widget-sandbox). */
    public interface RendererBridge {
        /** Start; answer with `{"type":"ready"}` through rendererMessage. */
        void load();

        /** A host → renderer message (docs/protocol.md). */
        void send(String json);

        void setTheme(String theme);

        void setWidth(double widthDp);

        void destroy();
    }

    /** The host's diagnostics changed (JSON: height, loaded, dead, error, ops, jsMs, ...), on the host's thread. */
    public interface Listener {
        void onDiagnostics(String json);
    }

    /**
     * A host for `html` in a bubble `widthDp` wide; nothing runs until start(). `resourcesDir` holds widget-sandbox's
     * WidgetJS bundles (the app copies them out of its assets). 0 when the bundles fail to load.
     */
    public static native long create(String html, String theme, double widthDp, double dpr, String resourcesDir, RendererBridge renderer, Listener listener);

    public static native void start(long host);

    public static native void setTheme(long host, String theme);

    /** Off screen the widget pauses: no animation frames, timers at 1 Hz, `document.hidden`. */
    public static native void setVisible(long host, boolean visible);

    /** Stops the engine and the renderer and frees the host; the handle is gone after it. Not from the host's thread. */
    public static native void destroy(long host);

    /** A renderer → host message (docs/protocol.md), from any thread. */
    public static native void rendererMessage(long host, String json);

    /** A renderer diagnostic, from any thread. */
    public static native void rendererDiag(long host, String kind, String detail);
}
