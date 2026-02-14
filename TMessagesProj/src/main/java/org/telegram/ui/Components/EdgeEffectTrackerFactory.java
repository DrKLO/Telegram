package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public final class EdgeEffectTrackerFactory extends RecyclerView.EdgeEffectFactory {
    private final TrackingEdgeEffect[] edgeEffects = new TrackingEdgeEffect[4];
    private final ArrayList<OnEdgeEffectListener> listeners = new ArrayList<>();

    public void addEdgeEffectListener(OnEdgeEffectListener listener) {
        listeners.add(listener);
    }

    public void removeEdgeEffectListener(OnEdgeEffectListener listener) {
        listeners.remove(listener);
    }

    public boolean hasVisibleEdges() {
        for (TrackingEdgeEffect edgeEffect : edgeEffects) {
            if (edgeEffect != null && edgeEffect.isVisible()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(
            RecyclerView view,
            @RecyclerView.EdgeEffectFactory.EdgeDirection int direction
    ) {
        final TrackingEdgeEffect edgeEffect = new TrackingEdgeEffect(view.getContext(), direction, this::onEdgeEffectVisibilityChange);
        edgeEffects[direction] = edgeEffect;
        return edgeEffect;
    }

    private void onEdgeEffectVisibilityChange(int direction, boolean isVisible) {
        for (OnEdgeEffectListener listener : listeners) {
            listener.onEdgeEffectVisibilityChange(direction, isVisible);
        }
    }

    public interface OnEdgeEffectListener {
        void onEdgeEffectVisibilityChange(@EdgeDirection int direction, boolean isVisible);
    }

    private static final class TrackingEdgeEffect extends EdgeEffect {

        private final @RecyclerView.EdgeEffectFactory.EdgeDirection int direction;
        private final OnEdgeEffectListener listener;

        TrackingEdgeEffect(
                Context context,
                @RecyclerView.EdgeEffectFactory.EdgeDirection int direction,
                OnEdgeEffectListener listener
        ) {
            super(context);
            this.direction = direction;
            this.listener = listener;
        }

        public boolean isVisible() {
            return !isFinished() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getDistance() != 0f);
        }

        private boolean lastVisibility;
        private void checkEdgeVisibility() {
            final boolean isVisible = isVisible();
            if (lastVisibility != isVisible) {
                lastVisibility = isVisible;
                if (listener != null) {
                    listener.onEdgeEffectVisibilityChange(direction, isVisible);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // AndroidUtilities.printStackTrace("onEdgeEffectVisibilityChange " + direction + " " + isVisible + " " + getDistance());
                    }
                }
            }
        }

        @Override
        public void setSize(int width, int height) {
            super.setSize(width, height);
            checkEdgeVisibility();
        }

        @Override
        public void finish() {
            super.finish();
            checkEdgeVisibility();
        }

        @Override
        public void onPull(float deltaDistance) {
            super.onPull(deltaDistance);
            checkEdgeVisibility();
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            super.onPull(deltaDistance, displacement);
            checkEdgeVisibility();
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            final float result = super.onPullDistance(deltaDistance, displacement);
            checkEdgeVisibility();
            return result;
        }

        @Override
        public void onRelease() {
            super.onRelease();
            checkEdgeVisibility();
        }

        @Override
        public void onAbsorb(int velocity) {
            super.onAbsorb(velocity);
            checkEdgeVisibility();
        }

        @Override
        public boolean draw(Canvas canvas) {
            final boolean result = super.draw(canvas);
            checkEdgeVisibility();
            return result;
        }
    }
}
