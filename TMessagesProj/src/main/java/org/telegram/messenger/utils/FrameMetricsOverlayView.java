// FrameMetricsOverlayView.java
package org.telegram.messenger.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(Build.VERSION_CODES.N)
public final class FrameMetricsOverlayView extends View {

    // =======================
    //  FrameMetrics registry
    // =======================

    private enum Metric {
        UNKNOWN_DELAY_DURATION(FrameMetrics.UNKNOWN_DELAY_DURATION, "unknown delay", true),
        INPUT_HANDLING_DURATION(FrameMetrics.INPUT_HANDLING_DURATION, "input", true),
        ANIMATION_DURATION(FrameMetrics.ANIMATION_DURATION, "animation", true),
        LAYOUT_MEASURE_DURATION(FrameMetrics.LAYOUT_MEASURE_DURATION, "layout", true),
        DRAW_DURATION(FrameMetrics.DRAW_DURATION, "draw", true),
        SYNC_DURATION(FrameMetrics.SYNC_DURATION, "sync", true),
        COMMAND_ISSUE_DURATION(FrameMetrics.COMMAND_ISSUE_DURATION, "cmd issue", true),
        SWAP_BUFFERS_DURATION(FrameMetrics.SWAP_BUFFERS_DURATION, "swap buffers", true),
        GPU_DURATION(FrameMetrics.GPU_DURATION, "gpu", true, Build.VERSION_CODES.S),
        TOTAL_DURATION(FrameMetrics.TOTAL_DURATION, "total", true),

        // FIRST_DRAW_FRAME(FrameMetrics.FIRST_DRAW_FRAME, "first draw", false),
        // INTENDED_VSYNC_TIMESTAMP(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, "intended vsync", false),
        // VSYNC_TIMESTAMP(FrameMetrics.VSYNC_TIMESTAMP, "vsync", false),
        // DEADLINE(FrameMetrics.DEADLINE, "deadline", false)
        ;

        final int key;
        final String label;
        final boolean isDuration;
        final int minApi;

        // runtime
        long last = Long.MIN_VALUE;
        double avgMs = 0;

        Metric(int key, String label, boolean isDuration) {
            this(key, label, isDuration, Build.VERSION_CODES.N);
        }

        Metric(int key, String label, boolean isDuration, int minApi) {
            this.key = key;
            this.label = label;
            this.isDuration = isDuration;
            this.minApi = minApi;
        }

        boolean isAvailable() {
            return Build.VERSION.SDK_INT >= minApi;
        }
    }

    // =======================
    //  Public API
    // =======================

    public static FrameMetricsOverlayView attachToActivityCorner(
            @NonNull Activity activity,
            int cornerGravity,
            int marginDp
    ) {
        FrameMetricsOverlayView v = new FrameMetricsOverlayView(activity);
        v.attachInternal(activity, cornerGravity, marginDp);
        return v;
    }

    public void detach() {
        stop();
        if (wm != null && attachedToWindowManager.getAndSet(false)) {
            try { wm.removeViewImmediate(this); } catch (Throwable ignored) {}
        }
        wm = null;
        lp = null;
        hostWindow = null;
    }

    // =======================
    //  Internals
    // =======================

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private Window hostWindow;

    private HandlerThread metricsThread;
    private Handler metricsHandler;
    private Window.OnFrameMetricsAvailableListener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean attachedToWindowManager = new AtomicBoolean(false);

    private static final long MS_IN_NS = 1_000_000L;
    private static final double EWMA_ALPHA = 0.05;

    // redraw
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable redraw = new Runnable() {
        @Override public void run() {
            if (!running.get()) return;
            invalidate();
            uiHandler.postDelayed(this, 300);
        }
    };

    public FrameMetricsOverlayView(Context context) {
        super(context.getApplicationContext());
        bgPaint.setColor(0xB0000000);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(dp(context, 10));
        textPaint.setFakeBoldText(true);
        textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));

        setWillNotDraw(false);
    }

    @SuppressLint("RtlHardcoded")
    private void attachInternal(Activity activity, int gravity, int marginDp) {
        wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        hostWindow = activity.getWindow();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        lp.gravity = gravity;
        int m = dp(activity, marginDp);
        lp.x = m;
        lp.y = m;
        lp.width = dp(activity, 260);

        wm.addView(this, lp);
        attachedToWindowManager.set(true);

        start();
    }

    private void start() {
        if (running.getAndSet(true)) return;

        metricsThread = new HandlerThread("FrameMetrics");
        metricsThread.start();
        metricsHandler = new Handler(metricsThread.getLooper());

        listener = (window, fm, dropCount) -> {
            for (Metric m : Metric.values()) {
                if (!m.isAvailable()) {
                    m.last = Long.MIN_VALUE;
                    continue;
                }
                long v = fm.getMetric(m.key);
                m.last = v;

                if (m.isDuration && v >= 0) {
                    double ms = v / (double) MS_IN_NS;
                    m.avgMs = m.avgMs == 0
                            ? ms
                            : m.avgMs + EWMA_ALPHA * (ms - m.avgMs);
                }
            }
        };

        hostWindow.addOnFrameMetricsAvailableListener(listener, metricsHandler);
        uiHandler.post(redraw);
    }

    private void stop() {
        running.set(false);
        uiHandler.removeCallbacks(redraw);
        if (hostWindow != null && listener != null) {
            hostWindow.removeOnFrameMetricsAvailableListener(listener);
        }
        if (metricsThread != null) {
            metricsThread.quitSafely();
        }
    }

    // =======================
    //  Drawing
    // =======================

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = dp(getContext(), 8);
        float line = dp(getContext(), 14);

        int lines = 0;
        for (Metric ignored : Metric.values()) lines++;

        float w = getWidth() > 0 ? getWidth() : dp(getContext(), 260);
        float h = pad * 2 + line * lines;

        canvas.drawRoundRect(0, 0, w, h, dp(getContext(), 10), dp(getContext(), 10), bgPaint);

        float x = pad;
        float y = pad + line;

        for (Metric m : Metric.values()) {
            String text;
            if (!m.isAvailable() || m.last < 0) {
                text = String.format(Locale.US, "%-16s : n/a", m.label);
            } else if (m.isDuration) {
                text = String.format(
                        Locale.US,
                        "%-16s : %5.2f / %5.2f ms",
                        m.label,
                        m.last / (double) MS_IN_NS,
                        m.avgMs
                );
            } else {
                text = String.format(
                        Locale.US,
                        "%-16s : %d",
                        m.label,
                        m.last
                );
            }
            canvas.drawText(text, x, y, textPaint);
            y += line;
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = dp(getContext(), 260);
        int height = dp(getContext(), 8) * 2 + dp(getContext(), 14) * Metric.values().length;
        setMeasuredDimension(width, height);
    }

    private static int dp(Context c, int dp) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return Math.round(dp * dm.density);
    }
}
