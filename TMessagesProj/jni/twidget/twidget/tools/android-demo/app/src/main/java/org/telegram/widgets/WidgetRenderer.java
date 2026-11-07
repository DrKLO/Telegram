package org.telegram.widgets;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.widgetsandbox.litehtml.LitehtmlBackend;
import org.telegram.widgetsandbox.litehtml.LitehtmlNative;

import java.util.ArrayList;

/**
 * TwidgetCore as the widget's renderer: what WidgetHost sends lands here through the bridge and runs on the main
 * thread, where the core and its Java backend live; what the core answers goes back to the host. Paints once per
 * vsync at most into WidgetView, which also plays the compositor's layers and covers an edited text control with
 * an EditText. The Android twin of the Qt demo's QtRenderer.
 */
public final class WidgetRenderer {

    private final float density;
    private final LitehtmlBackend backend = new LitehtmlBackend();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WidgetView view;
    private long handle;
    private long host;
    private boolean destroyed;
    private boolean frozen;
    private boolean visible = true;

    private boolean paintScheduled;
    private boolean frameRequested;
    private boolean ticking;
    private String lastEditor = "null";
    private long compositionVersion = -1;
    private final ArrayList<WidgetView.Layer> layers = new ArrayList<>();

    /** Paint statistics for the cell's status line. */
    int paints;
    double lastPaintMs;

    private final Choreographer.FrameCallback paintFrame = frameTimeNanos -> {
        paintScheduled = false;
        paint();
    };

    private final Choreographer.FrameCallback tickFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (handle == 0 || !ticking) {
                return;
            }
            forward(LitehtmlNative.tick(handle));
            after();
        }
    };

    private final Runnable wake = () -> {
        if (handle == 0) {
            return;
        }
        forward(LitehtmlNative.tick(handle));
        after();
    };

    public WidgetRenderer(Context context, String theme, double widthDp) {
        density = context.getResources().getDisplayMetrics().density;
        handle = LitehtmlNative.create(widthDp, density, theme, backend);
        if (handle != 0) {
            LitehtmlNative.setCompositing(handle, true);
        }
        view = new WidgetView(context, this, backend, density);
    }

    public WidgetView getView() {
        return view;
    }

    /** The host this renderer answers; set once WidgetHostNative.create returned it. */
    public void attach(long host) {
        this.host = host;
    }

    /** What WidgetHost calls, on its own thread: each call runs on the main thread, in order. */
    public WidgetHostNative.RendererBridge bridge() {
        return new WidgetHostNative.RendererBridge() {
            @Override
            public void load() {
                main.post(WidgetRenderer.this::load);
            }

            @Override
            public void send(String json) {
                main.post(() -> receive(json));
            }

            @Override
            public void setTheme(String theme) {
                main.post(() -> {
                    if (handle != 0) {
                        forward(LitehtmlNative.setTheme(handle, theme));
                        after();
                    }
                });
            }

            @Override
            public void setWidth(double widthDp) {
                main.post(() -> WidgetRenderer.this.setWidth(widthDp));
            }

            @Override
            public void destroy() {
                main.post(WidgetRenderer.this::release);
            }
        };
    }

    // ── host → renderer, on the main thread ──

    private void load() {
        if (destroyed) {
            return;
        }
        if (handle == 0) {
            diag("renderer", "TwidgetCore failed to start");
            return;
        }
        toHost("{\"type\":\"ready\"}");
        schedulePaint();
    }

    private void receive(String json) {
        if (destroyed || handle == 0) {
            return;
        }
        if (json.startsWith("{\"type\":\"requestFrame\"")) {
            requestFrame();
            return;
        }
        if (json.startsWith("{\"type\":\"frozen\"")) {
            frozen = true;
        }
        forward(LitehtmlNative.receive(handle, json));
        after();
    }

    void setWidth(double widthDp) {
        if (destroyed || handle == 0) {
            return;
        }
        forward(LitehtmlNative.setWidth(handle, widthDp));
        after();
    }

    // ── view → renderer, on the main thread ──

    /** action: 0 down, 1 move, 2 up, 3 cancel; CSS px. */
    void touch(int action, double x, double y, int pointer, boolean primary) {
        if (destroyed || handle == 0) {
            return;
        }
        forward(LitehtmlNative.touch(handle, action, x, y, pointer, primary));
        after();
    }

    void edit(long generation, String text) {
        if (destroyed || handle == 0) {
            return;
        }
        forward(LitehtmlNative.edit(handle, generation, text, "insertText"));
        after();
    }

    void endEditing() {
        if (destroyed || handle == 0) {
            return;
        }
        forward(LitehtmlNative.endEditing(handle));
        after();
    }

    /** Off screen nothing ticks and the caches go; back on screen the next paint is whole. */
    public void setVisible(boolean visible) {
        if (this.visible == visible || handle == 0) {
            return;
        }
        this.visible = visible;
        if (!visible) {
            stopTicking();
            LitehtmlNative.trim(handle);
        } else {
            after();
        }
    }

    // ── internals ──

    /** The renderer → host messages of one core call, a JSON array, one by one to the host. */
    private void forward(String arrayJson) {
        if (destroyed || host == 0) {
            return;
        }
        JSONArray messages;
        try {
            messages = new JSONArray(arrayJson);
        } catch (Exception e) {
            return;
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            if ("violation".equals(message.optString("type"))) {
                diag("renderer-violation", message.optString("detail"));
            }
            toHost(message.toString());
        }
    }

    private void toHost(String json) {
        if (host != 0) {
            WidgetHostNative.rendererMessage(host, json);
        }
    }

    private void diag(String kind, String detail) {
        if (host != 0) {
            WidgetHostNative.rendererDiag(host, kind, detail);
        }
    }

    /** After every core call: paint what changed, keep ticking as the core asks, follow the editor. */
    private void after() {
        if (destroyed || handle == 0) {
            return;
        }
        if (LitehtmlNative.dirty(handle)) {
            schedulePaint();
        }
        String editor = LitehtmlNative.editor(handle);
        if (!editor.equals(lastEditor)) {
            lastEditor = editor;
            JSONObject spec = null;
            try {
                spec = "null".equals(editor) ? null : new JSONObject(editor);
            } catch (Exception ignore) {
            }
            view.showEditor(spec);
        }
        main.removeCallbacks(wake);
        if (!visible || frozen) {
            stopTicking();
            return;
        }
        if (LitehtmlNative.wantsTick(handle)) {
            if (!ticking) {
                ticking = true;
            }
            Choreographer.getInstance().removeFrameCallback(tickFrame);
            Choreographer.getInstance().postFrameCallback(tickFrame);
        } else {
            stopTicking();
            double in = LitehtmlNative.nextTickIn(handle);
            if (!Double.isInfinite(in)) {
                main.postDelayed(wake, Math.max(16, (long) (in * 1000)));
            }
        }
    }

    private void stopTicking() {
        ticking = false;
        Choreographer.getInstance().removeFrameCallback(tickFrame);
    }

    private void requestFrame() {
        if (frozen || frameRequested) {
            return;
        }
        frameRequested = true;
        Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
            frameRequested = false;
            if (!destroyed) {
                toHost("{\"type\":\"frame\",\"time\":" + (frameTimeNanos / 1e6) + "}");
            }
        });
    }

    /** Display lists are recorded once per vsync at most; the view paints the changed areas into its frame. */
    private void schedulePaint() {
        if (paintScheduled || destroyed) {
            return;
        }
        paintScheduled = true;
        Choreographer.getInstance().postFrameCallback(paintFrame);
    }

    private void paint() {
        if (destroyed || handle == 0) {
            return;
        }
        long t0 = System.nanoTime();
        JSONArray list;
        JSONArray damage;
        try {
            list = new JSONArray(LitehtmlNative.displayList(handle));
            String damageJson = LitehtmlNative.damage(handle);
            damage = "null".equals(damageJson) ? null : new JSONArray(damageJson);
        } catch (Exception e) {
            return;
        }
        long size = LitehtmlNative.size(handle);
        view.paintDocument(list, damage, (int) (size >>> 32), (int) (size & 0xffffffffL));
        updateLayers();
        lastPaintMs = (System.nanoTime() - t0) / 1e6;
        paints++;
    }

    /** The compositor's layers, rastered again only where their content changed. */
    private void updateLayers() {
        JSONObject composition;
        try {
            composition = new JSONObject(LitehtmlNative.composition(handle));
        } catch (Exception e) {
            return;
        }
        long version = composition.optLong("v");
        if (version == compositionVersion) {
            return;
        }
        compositionVersion = version;
        JSONArray sources = composition.optJSONArray("l");
        ArrayList<WidgetView.Layer> next = new ArrayList<>();
        for (int i = 0; sources != null && i < sources.length(); i++) {
            JSONObject source = sources.optJSONObject(i);
            if (source == null) {
                continue;
            }
            long id = source.optLong("id");
            long contentVersion = source.optLong("cv");
            WidgetView.Layer old = null;
            for (WidgetView.Layer layer : layers) {
                if (layer.id == id && layer.contentVersion == contentVersion) {
                    old = layer;
                    break;
                }
            }
            JSONArray content = null;
            if (old == null) {
                try {
                    String json = LitehtmlNative.layerContent(handle, i);
                    content = json == null ? new JSONArray() : new JSONArray(json);
                } catch (Exception e) {
                    content = new JSONArray();
                }
            }
            next.add(view.makeLayer(source, old, content));
        }
        for (WidgetView.Layer layer : layers) {
            if (!next.contains(layer)) {
                layer.recycle();
            }
        }
        layers.clear();
        layers.addAll(next);
        view.setLayers(new ArrayList<>(layers));
    }

    /** Frees the core and the view's bitmaps; the host calls it through the bridge, the owner may too. Idempotent. */
    public void release() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        stopTicking();
        main.removeCallbacks(wake);
        Choreographer.getInstance().removeFrameCallback(paintFrame);
        view.showEditor(null);
        view.setLayers(new ArrayList<>());
        for (WidgetView.Layer layer : layers) {
            layer.recycle();
        }
        layers.clear();
        if (handle != 0) {
            LitehtmlNative.destroy(handle);
            handle = 0;
        }
        backend.release();
        view.release();
    }

    /** The owner let go of the host: no message goes to it after this. */
    public void detach() {
        host = 0;
    }
}
