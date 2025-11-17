package org.telegram.messenger.utils;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FrameMetricsTracker {
    private static final int DEFAULT_TOP_N = 5;

    private final int topN;
    private final Map<Integer, String> METRIC_NAMES = new HashMap<>();

    private long windowStartNs = 0;
    private int framesInWindow = 0;

    // Суммы по метрикам за текущую секунду
    private final Map<Integer, Long> sums = new HashMap<>();

    // Кадры текущего окна (для топ-N)
    private final List<Sample> samples = new ArrayList<>();

    public FrameMetricsTracker() {
        this(DEFAULT_TOP_N);
    }

    public FrameMetricsTracker(int topN) {
        this.topN = Math.max(1, topN);
        initMetricNames();
    }

    private void initMetricNames() {
        METRIC_NAMES.put(FrameMetrics.TOTAL_DURATION, "TOTAL");
        METRIC_NAMES.put(FrameMetrics.INTENDED_VSYNC_TIMESTAMP, "INTENDED_VSYNC");
        METRIC_NAMES.put(FrameMetrics.VSYNC_TIMESTAMP, "VSYNC_TIMESTAMP");
        METRIC_NAMES.put(FrameMetrics.INPUT_HANDLING_DURATION, "INPUT");
        METRIC_NAMES.put(FrameMetrics.ANIMATION_DURATION, "ANIMATION");
        METRIC_NAMES.put(FrameMetrics.LAYOUT_MEASURE_DURATION, "LAYOUT_MEASURE");
        METRIC_NAMES.put(FrameMetrics.DRAW_DURATION, "DRAW");
        METRIC_NAMES.put(FrameMetrics.SYNC_DURATION, "SYNC");
        METRIC_NAMES.put(FrameMetrics.COMMAND_ISSUE_DURATION, "COMMAND_ISSUE"); // “зелёный” столбец HWUI близок к этому
        METRIC_NAMES.put(FrameMetrics.SWAP_BUFFERS_DURATION, "SWAP_BUFFERS");
        METRIC_NAMES.put(FrameMetrics.GPU_DURATION, "GPU"); // “оранжевый” столбец HWUI

        // Появилась на API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                int UNKNOWN_DELAY_DURATION =
                        FrameMetrics.class.getField("UNKNOWN_DELAY_DURATION").getInt(null);
                METRIC_NAMES.put(UNKNOWN_DELAY_DURATION, "UNKNOWN_DELAY");
            } catch (Throwable ignored) {
            }
        }
    }

    public void start(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            System.out.println("FrameMetrics requires API 24+");
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        activity.getWindow().addOnFrameMetricsAvailableListener(
                new Window.OnFrameMetricsAvailableListener() {
                    @Override
                    public void onFrameMetricsAvailable(Window window, FrameMetrics fm, int dropped) {
                        long now = System.nanoTime();
                        if (windowStartNs == 0) windowStartNs = now;

                        // Соберём значения всех нам известных метрик
                        Map<Integer, Long> vals = new HashMap<>();
                        for (Map.Entry<Integer, String> e : METRIC_NAMES.entrySet()) {
                            long v = safeGet(fm, e.getKey());
                            if (v > 0) vals.put(e.getKey(), v);
                        }

                        // Нужные сокращения
                        long total = vals.getOrDefault(FrameMetrics.TOTAL_DURATION, 0L);
                        long gpu   = vals.getOrDefault(FrameMetrics.GPU_DURATION, 0L);
                        long cmd   = vals.getOrDefault(FrameMetrics.COMMAND_ISSUE_DURATION, 0L);
                        long draw  = vals.getOrDefault(FrameMetrics.DRAW_DURATION, 0L);
                        long sync  = vals.getOrDefault(FrameMetrics.SYNC_DURATION, 0L);
                        long swap  = vals.getOrDefault(FrameMetrics.SWAP_BUFFERS_DURATION, 0L);

                        // Накопим суммы
                        for (Map.Entry<Integer, Long> e : vals.entrySet()) {
                            sums.merge(e.getKey(), e.getValue(), Long::sum);
                        }
                        framesInWindow++;

                        // Сохраним сэмпл для топ-N (минимальный набор + вся карта)
                        samples.add(new Sample(total, gpu, cmd, draw, sync, swap, vals));

                        // Раз в 1 секунду — печать и сброс
                        long elapsedNs = now - windowStartNs;
                        if (elapsedNs >= 1_000_000_000L) {
                            printAveragesAndTop(elapsedNs);
                            resetWindow(now);
                        }
                    }
                },
                handler
        );
    }

    private static long safeGet(FrameMetrics fm, int metric) {
        try {
            long v = fm.getMetric(metric);
            return v < 0 ? 0 : v;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private void printAveragesAndTop(long elapsedNs) {
        if (framesInWindow == 0) return;

        double secs = elapsedNs / 1_000_000_000.0;
        double fps = framesInWindow / secs;

        System.out.printf(Locale.US,
                "WTF ------ FrameMetrics (%.2fs) | frames=%d | FPS=%.1f ------%n",
                secs, framesInWindow, fps);

        // Усреднённые значения по всем доступным метрикам
        for (Map.Entry<Integer, String> e : METRIC_NAMES.entrySet()) {
            long sum = sums.getOrDefault(e.getKey(), 0L);
            if (sum <= 0) continue;
            double avgMs = (sum / (double) framesInWindow) / 1_000_000.0;
            System.out.printf(Locale.US, "WTF %-18s : %7.3f ms%n", e.getValue(), avgMs);
        }

        // Топ-N тяжёлых кадров по TOTAL_DURATION
        samples.sort(Comparator.comparingLong((Sample s) -> s.total).reversed());
        int limit = Math.min(topN, samples.size());
        if (limit > 0) {
            System.out.println("Top " + limit + " heaviest frames (by TOTAL):");
            for (int i = 0; i < limit; i++) {
                Sample s = samples.get(i);
                System.out.printf(Locale.US,
                        "WTF #%d  TOTAL=%7.3f ms | GPU=%7.3f | CMD=%7.3f | DRAW=%7.3f | SYNC=%7.3f | SWAP=%7.3f%n",
                        i + 1,
                        nsToMs(s.total), nsToMs(s.gpu), nsToMs(s.cmd),
                        nsToMs(s.draw), nsToMs(s.sync), nsToMs(s.swap));
            }
        }

        System.out.println("---------------------------------------------------");
    }

    private void resetWindow(long now) {
        windowStartNs = now;
        framesInWindow = 0;
        sums.clear();
        samples.clear();
    }

    private static double nsToMs(long ns) {
        return ns / 1_000_000.0;
    }

    private static class Sample {
        final long total, gpu, cmd, draw, sync, swap;
        final Map<Integer, Long> all; // на будущее, если захочешь печатать больше

        Sample(long total, long gpu, long cmd, long draw, long sync, long swap, Map<Integer, Long> all) {
            this.total = total;
            this.gpu = gpu;
            this.cmd = cmd;
            this.draw = draw;
            this.sync = sync;
            this.swap = swap;
            this.all = all;
        }
    }
}
