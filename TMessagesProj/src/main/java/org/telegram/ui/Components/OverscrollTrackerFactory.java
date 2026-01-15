package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;

/**
 * {@code OverscrollTrackerFactory} is a {@link RecyclerView.EdgeEffectFactory} that exposes
 * overscroll lifecycle callbacks for a {@link RecyclerView} without using reflection.
 * <p>
 * {@link RecyclerView} does not provide a direct "is overscrolling" API. Internally, overscroll
 * is implemented via {@link EdgeEffect}. By supplying a custom {@link EdgeEffect} instance, we can
 * observe overscroll start, release, and end events in a stable way.
 *
 * <h3>What is considered "overscroll start"</h3>
 * An overscroll session starts on the first call to any of:
 * <ul>
 *     <li>{@link EdgeEffect#onPull(float)}</li>
 *     <li>{@link EdgeEffect#onPull(float, float)}</li>
 *     <li>{@link EdgeEffect#onAbsorb(int)}</li>
 * </ul>
 * Only one {@link Listener#onOverscrollStart(int)} callback is emitted per session.
 *
 * <h3>What is considered "overscroll end"</h3>
 * Overscroll is considered ended after:
 * <ol>
 *     <li>{@link EdgeEffect#onRelease()} has been called</li>
 *     <li>{@link EdgeEffect#isFinished()} returns {@code true}</li>
 * </ol>
 * This matches the visual end of the stretch/glow spring-back animation.
 *
 * <h3>Threading</h3>
 * All callbacks are invoked on the UI thread as part of input handling and view drawing.
 *
 * <h3>Typical use cases</h3>
 * <ul>
 *     <li>Triggering custom UI effects when the list overscrolls</li>
 *     <li>Synchronizing animations with overscroll start/release/end</li>
 *     <li>Collecting overscroll analytics/debug signals</li>
 * </ul>
 */
public final class OverscrollTrackerFactory extends RecyclerView.EdgeEffectFactory {

    /**
     * Listener receiving overscroll lifecycle callbacks.
     */
    public interface Listener {

        /**
         * Called once when an overscroll session starts for the given direction.
         *
         * @param direction One of:
         *                  {@link RecyclerView.EdgeEffectFactory#DIRECTION_TOP},
         *                  {@link RecyclerView.EdgeEffectFactory#DIRECTION_BOTTOM},
         *                  {@link RecyclerView.EdgeEffectFactory#DIRECTION_LEFT},
         *                  {@link RecyclerView.EdgeEffectFactory#DIRECTION_RIGHT}.
         */
        default void onOverscrollStart(@RecyclerView.EdgeEffectFactory.EdgeDirection int direction) {}

        /**
         * Called when the user releases an active overscroll (finger lifted).
         *
         * @param direction Overscroll direction.
         */
        default void onOverscrollRelease(@RecyclerView.EdgeEffectFactory.EdgeDirection int direction) {}

        /**
         * Called once when overscroll has fully ended and the effect animation has settled.
         *
         * @param direction Overscroll direction.
         */
        default void onOverscrollEnd(@RecyclerView.EdgeEffectFactory.EdgeDirection int direction) {}

        /**
         * Called on each pull that contributes to overscroll.
         *
         * @param direction Overscroll direction.
         * @param deltaDistance Normalized pull distance (as defined by {@link EdgeEffect}).
         */
        default void onOverscrollPull(@RecyclerView.EdgeEffectFactory.EdgeDirection int direction, float deltaDistance) {}

        /**
         * Called when a fling hits the edge and is absorbed into overscroll.
         *
         * @param direction Overscroll direction.
         * @param velocity Fling velocity in pixels per second.
         */
        default void onOverscrollAbsorb(@RecyclerView.EdgeEffectFactory.EdgeDirection int direction, int velocity) {}
    }

    private final Listener listener;

    /**
     * Creates a new {@code OverscrollTrackerFactory}.
     *
     * @param listener Listener that will receive overscroll callbacks. May be {@code null}.
     */
    public OverscrollTrackerFactory(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(
            RecyclerView view,
            @RecyclerView.EdgeEffectFactory.EdgeDirection int direction
    ) {
        return new TrackingEdgeEffect(view.getContext(), direction, listener);
    }

    /**
     * A custom {@link EdgeEffect} that tracks overscroll start/release/end transitions.
     * <p>
     * Note: This class relies on {@link RecyclerView} to perform invalidation while the
     * effect is animating in the normal on-screen pipeline.
     */
    private static final class TrackingEdgeEffect extends EdgeEffect {

        private final @RecyclerView.EdgeEffectFactory.EdgeDirection int direction;
        private final Listener listener;

        /** True while an overscroll session is active. */
        private boolean inOverscroll = false;

        TrackingEdgeEffect(
                Context context,
                @RecyclerView.EdgeEffectFactory.EdgeDirection int direction,
                Listener listener
        ) {
            super(context);
            this.direction = direction;
            this.listener = listener;
        }

        /** Ensures overscroll start is reported exactly once per session. */
        private void ensureStart() {
            if (!inOverscroll) {
                inOverscroll = true;
                AndroidUtilities.printStackTrace("Overscroll Start");
                if (listener != null) {
                    listener.onOverscrollStart(direction);
                }
            }
        }

        /**
         * Reports overscroll end when the effect has been released and fully finished.
         * Called from {@link #draw(Canvas)} which is driven by RecyclerView's draw loop.
         */
        private void maybeEnd() {
            if (inOverscroll && isFinished()) {
                inOverscroll = false;
                AndroidUtilities.printStackTrace("Overscroll End");
                if (listener != null) {
                    listener.onOverscrollEnd(direction);
                }
            }
        }

        @Override
        public void onPull(float deltaDistance) {
            ensureStart();
            if (listener != null) {
                listener.onOverscrollPull(direction, deltaDistance);
            }
            super.onPull(deltaDistance);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            ensureStart();
            if (listener != null) {
                listener.onOverscrollPull(direction, deltaDistance);
            }
            super.onPull(deltaDistance, displacement);
        }

        @Override
        public void onAbsorb(int velocity) {
            ensureStart();
            if (listener != null) {
                listener.onOverscrollAbsorb(direction, velocity);
            }
            super.onAbsorb(velocity);
        }

        @Override
        public void onRelease() {
            if (inOverscroll && listener != null) {
                listener.onOverscrollRelease(direction);
            }
            super.onRelease();

            maybeEnd();
        }

        @Override
        public boolean draw(Canvas canvas) {
            final boolean result = super.draw(canvas);
            maybeEnd();
            return result;
        }

        @Override
        public void finish() {
            super.finish();
            maybeEnd();
        }
    }
}
