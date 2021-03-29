package org.telegram.ui.Animations;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.GLTextureView;

// TODO agolokoz: fix bug with animations in viewpager
public class GradientBackgroundView extends GLTextureView {

    private final GradientGLDrawer drawer = new GradientGLDrawer(getContext());
    private final float[] animationStartPoints = new float[AnimationsController.backgroundPointsCount * 2];
    private final float[] currentPoints = new float[AnimationsController.backgroundPointsCount * 2];

    @Nullable
    private ValueAnimator animator;
    @Nullable
    private AnimationSettings settings;
    @Nullable
    private TimeInterpolator interpolator;
    private int currentPointsPosition;

    public GradientBackgroundView(@NonNull Context context) {
        super(context);
        setDrawer(drawer);
        setPointsState(currentPointsPosition);
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureAvailable(surface, width, height);
        setColors(AnimationsController.getBackgroundColorsCopy());
    }

    public void setColors(int[] colors) {
        for (int i = 0; i != colors.length; ++i) {
            drawer.setColor(i, colors[i]);
        }
    }

    public void setSettings(@Nullable AnimationSettings settings) {
        this.settings = settings;
        interpolator = null;
    }

    public void animateBackground() {
        int nextPosition = (currentPointsPosition + 1) % AnimationsController.backgroundPositionsCount;
        startAnimation(nextPosition);
        currentPointsPosition = nextPosition;
    }

    private void startAnimation(int nextPointsPosition) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            for (int i = 0; i < AnimationsController.backgroundPointsCount; ++i) {
                float xPrev = animationStartPoints[i * 2];
                float yPrev = animationStartPoints[i * 2 + 1];
                float xNext = AnimationsController.getBackgroundPointX(nextPointsPosition, i);
                float yNext = AnimationsController.getBackgroundPointY(nextPointsPosition, i);
                float xCurr = xPrev + (xNext - xPrev) * progress;
                float yCurr = yPrev + (yNext - yPrev) * progress;
                setPointPosition(i, xCurr, yCurr);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean isCancelled = false;
            @Override
            public void onAnimationStart(Animator animation) {
                isCancelled = false;
            }
            @Override
            public void onAnimationCancel(Animator animation) {
                isCancelled = true;
                System.arraycopy(currentPoints, 0, animationStartPoints, 0, currentPoints.length);
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isCancelled) {
                    return;
                }
                for (int i = 0; i < AnimationsController.backgroundPointsCount; ++i) {
                    float xNext = AnimationsController.getBackgroundPointX(nextPointsPosition, i);
                    float yNext = AnimationsController.getBackgroundPointY(nextPointsPosition, i);
                    setPointPosition(i, xNext, yNext);
                    setAnimationStartPoint(i, xNext, yNext);
                }
            }
        });
        animator.setDuration(settings == null ? 500 : settings.maxDuration);
        if (interpolator != null) {
            animator.setInterpolator(interpolator);
        }
        animator.start();
    }

    private void setPointsState(int position) {
        for (int i = 0; i < AnimationsController.backgroundPointsCount; ++i) {
            float x = AnimationsController.getBackgroundPointX(position, i);
            float y = AnimationsController.getBackgroundPointY(position, i);
            setPointPosition(i, x, y);
            setAnimationStartPoint(i, x, y);
        }
    }

    private void setPointPosition(int pointIdx, float x, float y) {
        drawer.setPosition(pointIdx, x, y);
        currentPoints[pointIdx * 2] = x;
        currentPoints[pointIdx * 2 + 1] = y;
    }

    private void setAnimationStartPoint(int pointIdx, float x, float y) {
        animationStartPoints[pointIdx * 2] = x;
        animationStartPoints[pointIdx * 2 + 1] = y;
    }
}
