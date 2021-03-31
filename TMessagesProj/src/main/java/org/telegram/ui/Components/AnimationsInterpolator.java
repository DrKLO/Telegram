package org.telegram.ui.Components;

// TODO agolokoz: add left and right bounds
public class AnimationsInterpolator extends CubicBezierInterpolator {

    private static final float FACTOR = 0.95f;

    public AnimationsInterpolator() {
        super(FACTOR, 0f, 0f, FACTOR);
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
