// FrameMetricsOverlayView.java
package org.telegram.messenger.utils;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

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
        textPaint.setTextSize(dp(10));
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
        int m = dp(marginDp);
        lp.x = m;
        lp.y = m;
        lp.width = dp(260);

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
        float pad = dp(8);
        float line = dp(14);

        int lines = 0;
        for (Metric ignored : Metric.values()) lines++;
        lines += 6;

        float w;
        w = getWidth() > 0 ? getWidth() : dp(260);
        float h = pad * 2 + line * lines;

        canvas.drawRoundRect(0, 0, w, h, dp(10), dp(10), bgPaint);

        float x = pad;
        float y = pad + line;

        long totalUi = 0;
        long totalRt = 0;
        long totalGpu = 0;
        long totalOther = 0;
        long totalFrame = 0;

        double totalUiAvg = 0;
        double totalRtAvg = 0;
        double totalGpuAvg = 0;
        double totalOtherAvg = 0;
        double totalFrameAvg = 0;

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
                switch (m) {
                    case INPUT_HANDLING_DURATION:
                    case ANIMATION_DURATION:
                    case LAYOUT_MEASURE_DURATION:
                    case DRAW_DURATION:
                        totalUi += m.last;
                        totalUiAvg += m.avgMs;
                        break;
                    case SYNC_DURATION:
                        totalUi += m.last;
                        totalUiAvg += m.avgMs;
                        totalRt += m.last;
                        totalRtAvg += m.avgMs;
                        break;
                    case COMMAND_ISSUE_DURATION:
                        totalRt += m.last;
                        totalRtAvg += m.avgMs;
                        break;
                    case GPU_DURATION:
                        totalGpu += m.last;
                        totalGpuAvg += m.avgMs;
                        break;
                    case UNKNOWN_DELAY_DURATION:
                    case SWAP_BUFFERS_DURATION:
                        totalOther += m.last;
                        totalOtherAvg += m.avgMs;
                        break;
                }
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
        totalFrame = Math.max(totalUi, Math.max(totalRt, totalGpu));
        totalFrameAvg = Math.max(totalUiAvg, Math.max(totalRtAvg, totalGpuAvg));

        y += line;
        canvas.drawText(String.format(Locale.US, "%-16s : %5.2f / %5.2f ms",
            "ui", totalUi / (double) MS_IN_NS, totalUiAvg), x, y, textPaint);
        y += line;
        canvas.drawText(String.format(Locale.US, "%-16s : %5.2f / %5.2f ms",
            "rt", totalRt / (double) MS_IN_NS, totalRtAvg), x, y, textPaint);
        y += line;
        canvas.drawText(String.format(Locale.US, "%-16s : %5.2f / %5.2f ms",
            "gpu", totalGpu / (double) MS_IN_NS, totalGpuAvg), x, y, textPaint);
        y += line;
        canvas.drawText(String.format(Locale.US, "%-16s : %5.2f / %5.2f ms",
            "other", totalOther / (double) MS_IN_NS, totalOtherAvg), x, y, textPaint);
        y += line;
        canvas.drawText(String.format(Locale.US, "%-16s : %5.2f / %5.2f ms",
            "frame", totalFrame / (double) MS_IN_NS, totalFrameAvg), x, y, textPaint);

        /*y += line;
        {
            double a = (totalFrame / (double) MS_IN_NS);
            double b = totalOtherAvg;
            if (a > 1 && b > 1) {
                canvas.drawText(String.format(Locale.US, "%-16s : %5.2f / %5.2f ms",
                        "fps", 1000.0 / a, 1000.0 / b), x, y, textPaint);
            }
        }*/

    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = dp(260);
        int height = dp(8) * 2 + dp(14) * (Metric.values().length + 6);
        setMeasuredDimension(width, height);
    }

}
