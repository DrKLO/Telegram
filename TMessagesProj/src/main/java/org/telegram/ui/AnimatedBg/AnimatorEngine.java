package org.telegram.ui.AnimatedBg;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Build;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import org.telegram.messenger.MessagesController;

public class AnimatorEngine {

    private static final PointF[] BG_CONTROL_POINTS = {
            new PointF(0.3f, 0.2f),
            new PointF(0.23f, 0.5f),
            new PointF(0.18f, 0.82f),
            new PointF(0.5f, 0.86f),
            new PointF(0.7f, 0.8f),
            new PointF(0.76f, 0.5f),
            new PointF(0.81f, 0.18f),
            new PointF(0.5f, 0.12f)};

    private static final PointF[] CONTROL_POINTS = {
            new PointF(0.36f, 0.24f),
            new PointF(0.26f, 0.58f),
            new PointF(0.18f, 0.9f),
            new PointF(0.42f, 0.82f),
            new PointF(0.64f, 0.74f),
            new PointF(0.73f, 0.4f),
            new PointF(0.81f, 0.08f),
            new PointF(0.6f, 0.16f)};

    private static final float[][] BG_CONTROL_POINT_SIZES = {
            {0.4f, 0.4f},
            {0.3f, 0.4f},
            {0.4f, 0.4f},
            {0.8f, 0.2f},
            {0.4f, 0.4f},
            {0.3f, 0.4f},
            {0.4f, 0.4f},
            {0.8f, 0.2f}
    };

    private static final float[][] CONTROL_POINT_SIZES = {
            {0.5f, 0.3f},
            {0.4f, 0.2f},
            {0.4f, 0.3f},
            {0.6f, 0.2f},
            {0.5f, 0.3f},
            {0.4f, 0.2f},
            {0.4f, 0.3f},
            {0.6f, 0.2f}
    };

    public final PointInfo[] points = {
            new PointInfo(Color.RED),
            new PointInfo(Color.GREEN),
            new PointInfo(Color.BLUE),
            new PointInfo(Color.YELLOW)
    };

    //between 0 and 8
    private float headPosition = 0;
    private float gravityOffset;
    private float headPositionWithGravityOffset = 0;

    private ValueAnimator currentAnimator;
    private FinishMoveAnimationListener finishMoveAnimationListener;
    public Interpolator interpolator = new CustomPathInterpolator(0, 1000, 1000, 33, 100);

    private HeadPositionChangeListener listener;

    public AnimatorEngine(HeadPositionChangeListener listener) {
        this.listener = listener;
        updatePoints();
    }

    public void setHeadPosition(float headPosition) {
        this.headPosition = (headPosition + 8) % 8;
        updateHeadPositionWithGravityOffset();
    }

    /**
     *
     * @param gravityOffset [0..1]
     */
    public void setGravityOffset(float gravityOffset) {
        this.gravityOffset = gravityOffset * 4f;
        updateHeadPositionWithGravityOffset();
    }

    private void updateHeadPositionWithGravityOffset() {
        headPositionWithGravityOffset = (headPosition + gravityOffset + 8) % 8;
        updatePoints();
        onHeadPositionChanged();
    }

    public String getColorsHash() {
        return points[0].color + "_" + points[1].color + "_" + points[2].color + "_" + points[3].color;
    }

    private void updatePoints() {
        int headIndex = controlPointIndex((int) headPositionWithGravityOffset);
        float progress = headPositionWithGravityOffset - (int) headPositionWithGravityOffset;
        for (int i = 0; i < points.length; i++) {
            int controlPointIndex = controlPointIndex(headIndex + 2 * i);
            int nextControlPointIndex = controlPointIndex(controlPointIndex + 1);
            PointF controlPoint = CONTROL_POINTS[controlPointIndex];
            PointF nextControlPoint = CONTROL_POINTS[nextControlPointIndex];
            PointF bgControlPoint = BG_CONTROL_POINTS[controlPointIndex];
            PointF nextBgControlPoint = BG_CONTROL_POINTS[nextControlPointIndex];
            points[i].position.set(
                    controlPoint.x + (nextControlPoint.x - controlPoint.x) * progress,
                    controlPoint.y + (nextControlPoint.y - controlPoint.y) * progress
            );
            points[i].bgPosition.set(
                    bgControlPoint.x + (nextBgControlPoint.x - bgControlPoint.x) * progress,
                    bgControlPoint.y + (nextBgControlPoint.y - bgControlPoint.y) * progress
            );
            float[] currentSize = CONTROL_POINT_SIZES[controlPointIndex];
            float[] nextSize = CONTROL_POINT_SIZES[nextControlPointIndex];
            points[i].rHorizontal = currentSize[0] + (nextSize[0] - currentSize[0]) * progress;
            points[i].rVertical = currentSize[1] + (nextSize[1] - currentSize[1]) * progress;
            float[] currentBorderSize = BG_CONTROL_POINT_SIZES[controlPointIndex];
            float[] nextBorderSize = BG_CONTROL_POINT_SIZES[nextControlPointIndex];
            points[i].rBgHorizontal =
                    currentBorderSize[0] + (nextBorderSize[0] - currentBorderSize[0]) * progress;
            points[i].rBgVertical =
                    currentBorderSize[1] + (nextBorderSize[1] - currentBorderSize[1]) * progress;
        }
    }

    private int controlPointIndex(int index) {
        return index % CONTROL_POINTS.length;
    }

    private void onHeadPositionChanged() {
        listener.onChanged(points);
    }

    public void animateToNext(MessagesController.AnimationConfig animationConfig) {
        animateToNext(animationConfig, null);
    }

    public void animateToNext(MessagesController.AnimationConfig animationConfig, FinishMoveAnimationListener listener) {
        if (currentAnimator != null && currentAnimator.isRunning()) {
            return;
        }
        finishMoveAnimationListener = listener;
        currentAnimator = ValueAnimator.ofFloat(headPosition, Math.round(headPosition + 1));
        interpolator = new CustomPathInterpolator(
                animationConfig.startTime,
                animationConfig.endTime,
                animationConfig.duration,
                animationConfig.startProgress,
                animationConfig.endProgress
        );
        currentAnimator.setInterpolator(interpolator);
        currentAnimator.setDuration(animationConfig.duration);
        currentAnimator.addUpdateListener(animation -> setHeadPosition((Float) animation
                .getAnimatedValue()));
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (finishMoveAnimationListener != null) {
                    finishMoveAnimationListener.onMoveFinish();
                    finishMoveAnimationListener = null;
                }
            }
        });
        currentAnimator.start();
    }

    public static class PointInfo {

        public final PointF position = new PointF();
        public final PointF bgPosition = new PointF();
        public float rHorizontal;
        public float rVertical;
        public float rBgHorizontal;
        public float rBgVertical;
        public int color;
        public float[] floatColor;

        public PointInfo(int color) {
            this.color = color;
        }

        public void setColor(int color) {
            this.color = color;
            this.floatColor = new float[3];
            this.floatColor[0] = Color.red(color) / 255f;
            this.floatColor[1] = Color.green(color) / 255f;
            this.floatColor[2] = Color.blue(color) / 255f;
        }

        public void setFloatColor(float[] floatColor) {
            this.floatColor = floatColor;
            this.color = rgb(floatColor[0], floatColor[1], floatColor[2]);
        }

        private static int rgb(float red, float green, float blue) {
            return 0xff000000 |
                    ((int) (red * 255.0f + 0.5f) << 16) |
                    ((int) (green * 255.0f + 0.5f) << 8) |
                    (int) (blue * 255.0f + 0.5f);
        }
    }

    public interface HeadPositionChangeListener {

        void onChanged(PointInfo[] points);
    }

    public static final class CustomPathInterpolator implements Interpolator {

        private final float pathInterpolatorStart;
        private final float pathInterpolatorEnd;

        private final Interpolator pathInterpolator;

        public CustomPathInterpolator(
                long startTime, long endTime, long totalLength, int startXPercent, int endXPercent
        ) {
            pathInterpolatorStart = (float) startTime / totalLength;
            pathInterpolatorEnd = (float) endTime / totalLength;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                pathInterpolator =
                        new PathInterpolator(startXPercent / 100f, 0f, 1 - endXPercent / 100f, 1f);
            } else {
                pathInterpolator = new AccelerateDecelerateInterpolator();
            }
        }

        @Override
        public float getInterpolation(float input) {
            if (input < pathInterpolatorStart) {
                return 0;
            } else if (input > pathInterpolatorEnd) {
                return 1;
            } else {
                float newIn =
                        (input - pathInterpolatorStart) / (pathInterpolatorEnd - pathInterpolatorStart);
                return pathInterpolator.getInterpolation(newIn);
            }
        }
    }

    public interface FinishMoveAnimationListener {

        void onMoveFinish();
    }
}