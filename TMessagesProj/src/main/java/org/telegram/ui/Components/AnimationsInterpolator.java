package org.telegram.ui.Components;

public class AnimationsInterpolator extends CubicBezierInterpolator {

    private static final float boundFactor = 0.95f;

    private float leftBoundProgress;
    private float rightBoundProgress;

    public AnimationsInterpolator() {
        this(boundFactor, 0f, 0f, 1f);
    }

    public AnimationsInterpolator(float xStart, float xEnd, float leftBoundProgress, float rightBoundProgress) {
        super(xStart * boundFactor, 0f, xEnd * boundFactor, 1f);
        this.leftBoundProgress = leftBoundProgress;
        this.rightBoundProgress = rightBoundProgress;
    }

    @Override
    public float getInterpolation(float time) {
        if (time < leftBoundProgress) {
            return 0f;
        } else if (time > rightBoundProgress) {
            return 1f;
        } else {
            float boundedTime = (time - leftBoundProgress) / (rightBoundProgress - leftBoundProgress);
            return super.getInterpolation(boundedTime);
        }
    }

    public void setStartX(float x) {
        start.x = x * boundFactor;
    }

    public void setEndX(float x) {
        end.x = x * boundFactor;
    }

    public void setLeftBoundProgress(float leftBoundProgress) {
        this.leftBoundProgress = leftBoundProgress;
    }

    public void setRightBoundProgress(float rightBoundProgress) {
        this.rightBoundProgress = rightBoundProgress;
    }

    public float getStartX() {
        return start.x;
    }

    public float getEndX() {
        return end.x;
    }
}
