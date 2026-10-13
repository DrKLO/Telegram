package org.telegram.messenger.utils;

import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;

import android.util.SparseArray;

import androidx.annotation.Nullable;

import org.telegram.messenger.BuildConfig;

import java.util.LinkedHashSet;
import java.util.Set;

import me.vkryl.core.reference.ReferenceList;

/**
 * A thin wrapper around Android {@link Choreographer} that delivers animation
 * callbacks at a stable ~60 fps regardless of the display refresh rate.
 *
 * <p>Callbacks with the same fps share a single schedule — they always fire
 * on the same tick, minimising the number of screen invalidations.
 *
 * <p>Must be used on the main thread only.
 */
public final class Choreographer60FpsContent implements Choreographer.FrameCallback {

    // ── Target frame rate ─────────────────────────────────────────────────────

    /** Desired animation rate, fps. */
    private static final int  TARGET_FPS        = 60;

    /** Duration of one target frame in nanoseconds (~16.67 ms). */
    private static final long FRAME_INTERVAL_NS = 1_000_000_000L / TARGET_FPS;

    /** Fixed schedules indexed by fps - 1. */
    private static final long[] FRAME_MASKS = buildFrameMasks();

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static Choreographer60FpsContent sInstance;

    public static Choreographer60FpsContent getInstance() {
        checkMainThread();
        if (sInstance == null) {
            sInstance = new Choreographer60FpsContent();
        }
        return sInstance;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Choreographer mChoreographer = Choreographer.getInstance();

    /** One-shot callbacks — fired once on the next frame, then cleared. */
    private final Set<FrameCallback> mOneShot = new LinkedHashSet<>();

    /**
     * Persistent callback groups keyed by fps.
     * All callbacks in the same group share one fixed schedule and always fire together,
     * so N animations at the same fps produce exactly one invalidate wave per period.
     */
    private final SparseArray<CallbackGroup> mGroups = new SparseArray<>();

    private final ReferenceList<Drawable> mDrawablesToInvalidate      = new ReferenceList<>();
    private final ReferenceList<Drawable> mDrawablesToInvalidate30fps = new ReferenceList<>();
    private final ReferenceList<View>     mViewsToInvalidate          = new ReferenceList<>();

    /** Nanoseconds accumulated since the last dispatched 60fps frame. */
    private long mAccumulatedNs;

    /** Timestamp of the previous VSYNC event; 0 means "not started yet". */
    private long mLastVsyncNs;

    /** Current tick in the repeating 60-tick cycle; also used for legacy 30fps drawables. */
    private int mCounter;

    // ── Public interface ──────────────────────────────────────────────────────

    /** Callback interface, mirrors {@link Choreographer.FrameCallback}. */
    public interface FrameCallback {
        /**
         * Called at the requested fps.
         *
         * @param frameTimeNanos frame start time in {@link System#nanoTime()} domain, ns
         */
        void doFrame(long frameTimeNanos);
    }

    /**
     * Schedules a one-shot callback for the next ~60 fps frame.
     * Removed automatically after it fires.
     */
    public void post(FrameCallback callback) {
        checkMainThread();
        mOneShot.add(callback);
    }

    public void postInvalidateDrawable(Drawable drawable) {
        checkMainThread();
        mDrawablesToInvalidate.add(drawable);
    }

    public void postInvalidateDrawable30fps(Drawable drawable) {
        checkMainThread();
        mDrawablesToInvalidate30fps.add(drawable);
    }

    public void postInvalidateView(View view) {
        checkMainThread();
        mViewsToInvalidate.add(view);
    }

    /**
     * Subscribes a persistent callback at ~60 fps.
     */
    public void addFrameCallback(FrameCallback callback) {
        addFrameCallback(callback, TARGET_FPS);
    }

    /**
     * Subscribes a persistent Runnable callback at ~60 fps.
     */
    public void addFrameCallback(Runnable callback) {
        addFrameCallback(callback, TARGET_FPS);
    }

    public void addFrameCallbackOnce(Runnable callback, int fps) {
        checkMainThread();
        if (callback == null) {
            return;
        }
        fps = Math.max(1, Math.min(fps, TARGET_FPS));
        removeFrameCallbackOnce(callback); // remove from any existing group first
        CallbackGroup group = getOrCreateGroup(fps);
        if (group.runnableCallbacksOnce == null) {
            group.runnableCallbacksOnce = new ReferenceList<>();
        }
        group.runnableCallbacksOnce.add(callback);
    }


    /**
     * Subscribes a persistent Runnable callback at the given fps.
     *
     * @param callback the {@link Runnable} to run on each tick
     * @param fps      desired rate in frames per second; clamped to [1, TARGET_FPS]
     */
    public void addFrameCallback(Runnable callback, int fps) {
        checkMainThread();
        if (callback == null) {
            return;
        }
        fps = Math.max(1, Math.min(fps, TARGET_FPS));
        removeFrameCallback(callback); // remove from any existing group first
        getOrCreateGroup(fps).runnableCallbacks.add(callback);
    }

    /**
     * Subscribes a persistent callback at the given fps.
     *
     * <p>Callbacks sharing the same fps value share a single schedule and
     * are guaranteed to fire on the same tick — this minimises screen invalidations
     * when multiple animations run at the same rate.
     *
     * @param callback the {@link FrameCallback} to register
     * @param fps      desired rate in frames per second; clamped to [1, TARGET_FPS]
     */
    public void addFrameCallback(FrameCallback callback, int fps) {
        checkMainThread();
        fps = Math.max(1, Math.min(fps, TARGET_FPS));
        removeFrameCallback(callback); // remove from any existing group first
        getOrCreateGroup(fps).callbacks.add(callback);
    }

    /**
     * Unsubscribes a persistent Runnable callback from whichever group it belongs to.
     * Empty groups are removed automatically.
     */
    public void removeFrameCallback(Runnable callback) {
        checkMainThread();
        if (callback == null) {
            return;
        }
        for (int i = 0; i < mGroups.size(); i++) {
            CallbackGroup group = mGroups.valueAt(i);
            if (group.runnableCallbacks.remove(callback)) {
                return;
            }
        }
    }

    public void removeFrameCallbackOnce(Runnable callback) {
        checkMainThread();
        if (callback == null) {
            return;
        }
        for (int i = 0; i < mGroups.size(); i++) {
            CallbackGroup group = mGroups.valueAt(i);
            if (group.runnableCallbacksOnce != null && group.runnableCallbacksOnce.remove(callback)) {
                return;
            }
        }
    }

    /**
     * Unsubscribes a persistent callback from whichever group it belongs to.
     * Empty groups are removed automatically.
     */
    public void removeFrameCallback(FrameCallback callback) {
        checkMainThread();
        if (callback == null) {
            return;
        }
        for (int i = 0; i < mGroups.size(); i++) {
            CallbackGroup group = mGroups.valueAt(i);
            if (group.callbacks.remove(callback)) {
                return;
            }
        }
    }

    // ── Private implementation ────────────────────────────────────────────────

    private Choreographer60FpsContent() {
        mChoreographer.postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (mLastVsyncNs == 0) {
            mLastVsyncNs = frameTimeNanos;
        } else {
            mAccumulatedNs += frameTimeNanos - mLastVsyncNs;
            mLastVsyncNs    = frameTimeNanos;

            if (mAccumulatedNs >= FRAME_INTERVAL_NS) {
                mAccumulatedNs %= FRAME_INTERVAL_NS;
                dispatchFrame(frameTimeNanos);
            }
        }

        mChoreographer.postFrameCallback(this);
    }

    private void dispatchFrame(long frameTimeNanos) {
        // All groups use a fixed schedule on the same repeating 60-tick cycle.
        final long tickMask = 1L << mCounter;
        for (int i = 0; i < mGroups.size(); i++) {
            CallbackGroup group = mGroups.valueAt(i);
            final boolean fire = (group.frameMask & tickMask) != 0;
            if (fire) {
                if (group.runnableCallbacksOnce != null) {
                    ReferenceList<Runnable> referenceList = group.runnableCallbacksOnce;
                    group.runnableCallbacksOnce = null;
                    for (Runnable runnable : referenceList) {
                        runnable.run();
                    }
                }

                for (FrameCallback cb : group.callbacks) {
                    cb.doFrame(frameTimeNanos);
                }
                for (Runnable runnable : group.runnableCallbacks) {
                    runnable.run();
                }
            }
        }

        // One-shot callbacks.
        for (FrameCallback cb : mOneShot) {
            cb.doFrame(frameTimeNanos);
        }

        // View / drawable invalidations.
        for (View view : mViewsToInvalidate) {
            view.invalidate();
        }
        for (Drawable drawable : mDrawablesToInvalidate) {
            drawable.invalidateSelf();
        }
        mViewsToInvalidate.clear();
        mDrawablesToInvalidate.clear();
        mOneShot.clear();

        // Legacy 30fps drawables.
        if (mCounter % 2 == 0) {
            for (Drawable drawable : mDrawablesToInvalidate30fps) {
                drawable.invalidateSelf();
            }
            mDrawablesToInvalidate30fps.clear();
        }

        mCounter++;
        if (mCounter == TARGET_FPS) {
            mCounter = 0;
        }
    }

    private CallbackGroup getOrCreateGroup(int fps) {
        fps = Math.max(1, Math.min(fps, TARGET_FPS));
        CallbackGroup group = mGroups.get(fps);
        if (group == null) {
            group = new CallbackGroup(FRAME_MASKS[fps - 1]);
            mGroups.put(fps, group);
        }
        return group;
    }

    private static long[] buildFrameMasks() {
        long[] masks = new long[TARGET_FPS];
        for (int index = 0; index < masks.length; index++) {
            masks[index] = buildFrameMask(index + 1);
        }
        return masks;
    }

    /**
     * Keeps the nearest regular divisor-of-60 schedule and adds/removes leap frames.
     * Ties select the lower base fps. Schedules depend only on fps, never on subscribers.
     */
    private static long buildFrameMask(int fps) {
        int baseFps = 1;
        for (int candidate = 2; candidate <= TARGET_FPS; candidate++) {
            if (TARGET_FPS % candidate == 0
                    && Math.abs(candidate - fps) < Math.abs(baseFps - fps)) {
                baseFps = candidate;
            }
        }

        int stride = TARGET_FPS / baseFps;
        long mask = 0;
        for (int tick = 0; tick < TARGET_FPS; tick += stride) {
            mask |= 1L << tick;
        }

        int correction = fps - baseFps;
        int leapFrames = Math.abs(correction);
        for (int i = 0; i < leapFrames; i++) {
            // Centre each correction in its part of the cycle.
            int targetTick = (2 * i + 1) * TARGET_FPS / (2 * leapFrames);
            for (int distance = 0; distance < TARGET_FPS; distance++) {
                boolean selected = false;
                for (int direction = 0; direction < 2; direction++) {
                    int tick = (targetTick + (direction == 0 ? distance : -distance)
                            + TARGET_FPS) % TARGET_FPS;
                    long bit = 1L << tick;
                    if (correction > 0) {
                        // Even-stride grids share odd leap slots, including 30 and 15 fps.
                        if ((mask & bit) != 0 || (stride % 2 == 0 && tick % 2 == 0)) {
                            continue;
                        }
                        mask |= bit;
                    } else {
                        if ((mask & bit) == 0) {
                            continue;
                        }
                        mask &= ~bit;
                    }
                    selected = true;
                    break;
                }
                if (selected) {
                    break;
                }
            }
        }
        return mask;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /** Fixed frame schedule shared by all callbacks at the same fps. */
    private static final class CallbackGroup {
        final long frameMask;

        final ReferenceList<FrameCallback> callbacks = new ReferenceList<>();
        final ReferenceList<Runnable> runnableCallbacks = new ReferenceList<>();
        @Nullable
        ReferenceList<Runnable> runnableCallbacksOnce;

        CallbackGroup(long frameMask) {
            this.frameMask = frameMask;
        }
    }

    private static void checkMainThread() {
        if (BuildConfig.DEBUG_PRIVATE_VERSION || BuildConfig.DEBUG_VERSION) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new IllegalStateException("Choreographer60FpsContent must be used on the main thread");
            }
        }
    }
}