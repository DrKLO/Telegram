package org.telegram.messenger.utils;

import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;

import android.util.SparseArray;

import org.telegram.messenger.BuildConfig;

import java.util.LinkedHashSet;
import java.util.Set;

import me.vkryl.core.reference.ReferenceList;

/**
 * A thin wrapper around Android {@link Choreographer} that delivers animation
 * callbacks at a stable ~60 fps regardless of the display refresh rate.
 *
 * <p>Callbacks with the same fps share a single accumulator — they always fire
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
     * Persistent callback groups keyed by interval in nanoseconds.
     * All callbacks in the same group share one accumulator and always fire together,
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

    /** Counts dispatched 60fps frames; used for legacy 30fps drawable support. */
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
     * <p>Callbacks sharing the same fps value share a single accumulator and
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
        // Dispatch grouped persistent callbacks.
        // Stride groups use mCounter % stride — zero per-group state, perfect sync.
        // Accumulator groups add FRAME_INTERVAL_NS each tick — supports any fps.
        for (int i = 0; i < mGroups.size(); i++) {
            CallbackGroup group = mGroups.valueAt(i);
            final boolean fire;
            if (group.stride > 0) {
                fire = mCounter % group.stride == 0;
            } else {
                group.accumulatedNs += FRAME_INTERVAL_NS;
                if (group.accumulatedNs >= group.intervalNs) {
                    group.accumulatedNs %= group.intervalNs;
                    fire = true;
                } else {
                    fire = false;
                }
            }
            if (fire) {
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
    }

    private CallbackGroup getOrCreateGroup(int fps) {
        CallbackGroup group = mGroups.get(fps);
        if (group == null) {
            long intervalNs = 1_000_000_000L / fps;
            // Use stride when fps divides TARGET_FPS evenly — perfect sync, no accumulator.
            int stride = (TARGET_FPS % fps == 0) ? TARGET_FPS / fps : 0;
            group = new CallbackGroup(intervalNs, stride);
            mGroups.put(fps, group);
        }
        return group;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * A group of callbacks sharing the same tick interval.
     *
     * <p>If {@code stride > 0} the group fires every {@code stride} ticks using
     * the global {@code mCounter} — perfectly synchronised with zero per-group state.
     * This works whenever {@code TARGET_FPS % fps == 0} (e.g. 60, 30, 20, 15, 12, 10).
     *
     * <p>Otherwise {@code accumulatedNs} is used — supports any fps (24, 25, …)
     * at the cost of minor phase drift between groups with different intervals.
     * All members of the same group still fire in unison.
     * <p>
     * CopyOnWriteArrayList allows safe removal during iteration (e.g. from doFrame).
     */
    private static final class CallbackGroup {
        final long intervalNs;
        /** > 0 when TARGET_FPS % fps == 0; uses mCounter % stride for dispatch. */
        final int  stride;
        /** Used only when stride == 0. */
        long accumulatedNs;

        final ReferenceList<FrameCallback> callbacks = new ReferenceList<>();
        final ReferenceList<Runnable> runnableCallbacks = new ReferenceList<>();

        CallbackGroup(long intervalNs, int stride) {
            this.intervalNs = intervalNs;
            this.stride     = stride;
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