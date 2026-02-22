package org.telegram.messenger.utils;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

import java.util.Locale;

/**
 * RefreshRateController

 * Purpose:
 *  - Track real FPS using FrameMetrics
 *  - Switch display refresh rate to ~60 Hz if FPS is stably below 60
 *  - Restore max refresh rate if FPS is stably high again
 *  - Apply hysteresis and a minimum switch interval

 * Usage:
 *   private RefreshRateController rr;
 *   onCreate(): rr = new RefreshRateController(this);
 *   onResume(): rr.start();
 *   onPause(): rr.stop();
 */

@RequiresApi(api = Build.VERSION_CODES.N)
public final class RefreshRateController {

    private static final String TAG = "RefreshRateController";

    // --- Tuning parameters ---

    /** How long FPS must stay consistently low/high to trigger a switch */
    private static final long STABLE_WINDOW_MS = 1800; // ~1.8 seconds

    /** Minimum interval between refresh rate switches */
    private static final long MIN_SWITCH_INTERVAL_MS = 3000;

    /**
     * Hysteresis thresholds:
     *  - DOWN_FPS: average FPS must be <= this value to switch down to 60 Hz
     *  - UP_FPS:   average FPS must be >= this value to switch back to max Hz
     */
    private static final float DOWN_FPS = 55.0f;
    private static final float UP_FPS   = 58.5f;

    /** Ring buffer size (covers ~2–3 seconds of frames) */
    private static final int RING_SIZE = 240;

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // FrameMetrics listener (API 24+)
    private Window.OnFrameMetricsAvailableListener listener;

    // Ring buffer storing frame durations (nanoseconds)
    private final long[] frameNs = new long[RING_SIZE];
    private int ringCount = 0;
    private int ringPos = 0;
    private long ringSumNs = 0;

    // Stability tracking timestamps
    private long belowSinceMs = -1;
    private long aboveSinceMs = -1;

    // Refresh rate switching control
    private long lastSwitchMs = 0;
    private int currentPreferredModeId = 0;

    // Cached display modes
    private Display.Mode mode60;
    private Display.Mode modeMax;

    public RefreshRateController(@NonNull Activity activity) {
        this.activity = activity;
    }

    @MainThread
    public void start() {
        if (resolveModes()) {
            installListener();
        }
    }

    @MainThread
    public void stop() {
        removeListener();
        resetStats();
    }

    // --- Internal logic ---

    private void resetStats() {
        ringCount = 0;
        ringPos = 0;
        ringSumNs = 0;
        belowSinceMs = -1;
        aboveSinceMs = -1;
    }

    private void installListener() {
        final Window window = activity.getWindow();
        if (listener != null) return;

        listener = (w, fm, dropCountSinceLastInvocation) -> {
            // TOTAL_DURATION: full frame time from start of processing to buffer swap
            final long totalNs = fm.getMetric(FrameMetrics.TOTAL_DURATION);
            if (totalNs <= 0) return;

            pushFrame(totalNs);
            maybeSwitch();
        };

        // Use main looper so window attribute updates are safe
        window.addOnFrameMetricsAvailableListener(listener, mainHandler);
    }

    private void removeListener() {
        final Window window = activity.getWindow();
        if (listener != null) {
            window.removeOnFrameMetricsAvailableListener(listener);
            listener = null;
        }
    }

    private void pushFrame(long totalNs) {
        // Update ring buffer with running sum
        if (ringCount < RING_SIZE) {
            ringCount++;
        } else {
            ringSumNs -= frameNs[ringPos];
        }
        frameNs[ringPos] = totalNs;
        ringSumNs += totalNs;
        ringPos++;
        if (ringPos == RING_SIZE) ringPos = 0;
    }

    private float getAvgFps() {
        if (ringCount == 0) return 0f;
        final double avgFrameNs = (double) ringSumNs / (double) ringCount;
        if (avgFrameNs <= 0.0) return 0f;
        return (float) (1_000_000_000.0 / avgFrameNs);
    }

    private void maybeSwitch() {
        // Require a small warm-up period
        if (ringCount < 30) return;

        final long nowMs = android.os.SystemClock.uptimeMillis();
        final float avgFps = getAvgFps();

        final boolean canSwitchNow =
                (nowMs - lastSwitchMs) >= MIN_SWITCH_INTERVAL_MS;

        // If currently preferring high refresh rate and FPS is stably low → switch to 60 Hz
        final boolean preferHigh = !isPrefer60();
        if (preferHigh) {
            if (avgFps <= DOWN_FPS) {
                if (belowSinceMs < 0) belowSinceMs = nowMs;
                if ((nowMs - belowSinceMs) >= STABLE_WINDOW_MS && canSwitchNow) {
                    setPreferredMode(mode60);
                    lastSwitchMs = nowMs;
                    belowSinceMs = -1;
                    aboveSinceMs = -1;
                    logDecision("DOWN", avgFps);
                }
            } else {
                belowSinceMs = -1; // FPS is unstable again
            }
            return;
        }

        // If currently preferring 60 Hz and FPS is stably high → restore max refresh rate
        if (avgFps >= UP_FPS) {
            if (aboveSinceMs < 0) aboveSinceMs = nowMs;
            if ((nowMs - aboveSinceMs) >= STABLE_WINDOW_MS && canSwitchNow) {
                setPreferredMode(modeMax);
                lastSwitchMs = nowMs;
                belowSinceMs = -1;
                aboveSinceMs = -1;
                logDecision("UP", avgFps);
            }
        } else {
            aboveSinceMs = -1;
        }
    }

    private boolean isPrefer60() {
        if (mode60 == null) return false;
        return currentPreferredModeId == mode60.getModeId();
    }

    private void logDecision(String direction, float fps) {
        if (!BuildConfig.DEBUG) return;
        Log.d(TAG, String.format(Locale.US,
                "%s switch, avgFps=%.2f, preferModeId=%d",
                direction, fps, currentPreferredModeId));
    }

    private boolean resolveModes() {

        final Display display = getDisplayCompat(activity);
        if (display == null) return false;

        final Display.Mode[] modes = display.getSupportedModes();
        if (modes == null || modes.length == 0) return false;

        Display.Mode best60 = null;
        Display.Mode bestMax = null;

        for (Display.Mode m : modes) {
            if (bestMax == null || m.getRefreshRate() > bestMax.getRefreshRate()) {
                bestMax = m;
            }

            // Look for a refresh rate close to 60 Hz (often 59.94)
            final float rr = m.getRefreshRate();
            if (rr >= 58.0f && rr <= 62.5f) {
                if (best60 == null) {
                    best60 = m;
                } else {
                    // Prefer the mode closest to 60 Hz; if equal, pick higher resolution
                    float d1 = Math.abs(best60.getRefreshRate() - 60f);
                    float d2 = Math.abs(rr - 60f);
                    if (d2 < d1) {
                        best60 = m;
                    } else if (d2 == d1) {
                        int a1 = best60.getPhysicalWidth() * best60.getPhysicalHeight();
                        int a2 = m.getPhysicalWidth() * m.getPhysicalHeight();
                        if (a2 > a1) best60 = m;
                    }
                }
            }
        }

        mode60 = best60;
        modeMax = bestMax;

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "mode60=" + modeToString(mode60)
                    + ", modeMax=" + modeToString(modeMax));
        }

        return mode60 != modeMax;
    }

    private void setPreferredMode(@Nullable Display.Mode mode) {
        if (mode == null) return;

        final Window window = activity.getWindow();
        //final WindowManager wm = window.getWindowManager();
        //if (wm == null) return;

        AndroidUtilities.setPreferredMaxRefreshRate(window, mode.getRefreshRate());

        //final WindowManager.LayoutParams lp = window.getAttributes();

        // Do nothing if the mode is already set
        //if (lp.preferredDisplayModeId == mode.getModeId()) {
        //    currentPreferredModeId = mode.getModeId();
        //    return;
        //}

        //lp.preferredRefreshRate = mode.getRefreshRate();
        //lp.preferredDisplayModeId = mode.getModeId();
        //try {
        //    window.setAttributes(lp);
        //    wm.updateViewLayout(window.getDecorView(), lp);
        //} catch (Exception e) {
        //    FileLog.e(e);
        //}
        currentPreferredModeId = mode.getModeId();
    }

    @Nullable
    private static Display getDisplayCompat(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return activity.getDisplay();
        } else {
            // Deprecated but still required for older APIs
            return activity.getWindowManager().getDefaultDisplay();
        }
    }

    private static String modeToString(@Nullable Display.Mode m) {
        if (m == null) return "null";
        return m.getPhysicalWidth() + "x" + m.getPhysicalHeight()
                + " @" + m.getRefreshRate()
                + " (id=" + m.getModeId() + ")";
    }
}
