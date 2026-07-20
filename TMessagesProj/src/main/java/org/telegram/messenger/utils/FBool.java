package org.telegram.messenger.utils;

import androidx.annotation.FloatRange;
import androidx.core.math.MathUtils;

public class FBool {
    private FBool() {

    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float not(@FloatRange(from = 0.0, to = 1.0) float a) {
        return 1f - clamp(a);
    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float and(@FloatRange(from = 0.0, to = 1.0) float a, @FloatRange(from = 0.0, to = 1.0) float b) {
        return Math.min(clamp(a), clamp(b));
    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float or(@FloatRange(from = 0.0, to = 1.0) float a, @FloatRange(from = 0.0, to = 1.0) float b) {
        return not(and(not(a), not(b)));
    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float nand(@FloatRange(from = 0.0, to = 1.0) float a, @FloatRange(from = 0.0, to = 1.0) float b) {
        return not(and(a, b));
    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float nor(@FloatRange(from = 0.0, to = 1.0) float a, @FloatRange(from = 0.0, to = 1.0) float b) {
        return not(or(a, b));
    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float xor(@FloatRange(from = 0.0, to = 1.0) float a, @FloatRange(from = 0.0, to = 1.0) float b) {
        return or(and(a, not(b)), and(not(a), b));
    }

    @FloatRange(from = 0.0, to = 1.0)
    public static float xnor(@FloatRange(from = 0.0, to = 1.0) float a, @FloatRange(from = 0.0, to = 1.0) float b) {
        return not(xor(a, b));
    }

    private static float clamp(float v) {
        return MathUtils.clamp(v, 0, 1);
    }
}
