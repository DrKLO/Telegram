package org.telegram.ui.Components;

// TODO agolokoz: add left and right bounds
public class AnimationsInterpolator extends CubicBezierInterpolator {

    private static final float FACTOR = 0.95f;

    public AnimationsInterpolator() {
        this(FACTOR, 0f);
    }

    public AnimationsInterpolator(float xStart, float xEnd) {
        super(xStart * FACTOR, 0f, xEnd * FACTOR, 1f);
    }

    public void setStartX(float x) {
        start.x = x * FACTOR;
    }

    public void setEndX(float x) {
        end.x = x * FACTOR;
    }

    public float getStartX() {
        return start.x;
    }

    public float getEndX() {
        return end.x;
    }
}
