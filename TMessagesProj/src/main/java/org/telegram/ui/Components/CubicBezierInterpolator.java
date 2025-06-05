package org.telegram.ui.Components;

import android.graphics.PointF;
import android.os.Build;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.core.graphics.PathParser;

public class CubicBezierInterpolator implements Interpolator {

    public static final CubicBezierInterpolator DEFAULT = new CubicBezierInterpolator(0.25, 0.1, 0.25, 1);
    public static final CubicBezierInterpolator EASE_OUT = new CubicBezierInterpolator(0, 0, .58, 1);
    public static final CubicBezierInterpolator EASE_OUT_QUINT = new CubicBezierInterpolator(.23, 1, .32, 1);
    public static final CubicBezierInterpolator EASE_IN = new CubicBezierInterpolator(.42, 0, 1, 1);
    public static final CubicBezierInterpolator EASE_BOTH = new CubicBezierInterpolator(.42, 0, .58, 1);
    public static final CubicBezierInterpolator EASE_OUT_BACK = new CubicBezierInterpolator(.34, 1.56, .64, 1);

    public static final Interpolator Emphasized = Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(PathParser.createPathFromPathData("M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1")) : new LinearInterpolator();
    public static final Interpolator EmphasizedDecelerate = Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(0.05f, 0.7f, 0.1f, 1f) : new LinearInterpolator();
    public static final Interpolator EmphasizedAccelerate = Build.VERSION.SDK_INT >= 21 ? new PathInterpolator(0.3f, 0f, 0.8f, 0.15f) : new LinearInterpolator();

    protected PointF start;
    protected PointF end;
    protected PointF a = new PointF();
    protected PointF b = new PointF();
    protected PointF c = new PointF();

    public CubicBezierInterpolator(PointF start, PointF end) throws IllegalArgumentException {
        if (start.x < 0 || start.x > 1) {
            throw new IllegalArgumentException("startX value must be in the range [0, 1]");
        }
        if (end.x < 0 || end.x > 1) {
            throw new IllegalArgumentException("endX value must be in the range [0, 1]");
        }
        this.start = start;
        this.end = end;
    }

    public CubicBezierInterpolator(float startX, float startY, float endX, float endY) {
        this(new PointF(startX, startY), new PointF(endX, endY));
    }

    public CubicBezierInterpolator(double startX, double startY, double endX, double endY) {
        this((float) startX, (float) startY, (float) endX, (float) endY);
    }

    @Override
    public float getInterpolation(float time) {
        return getBezierCoordinateY(getXForTime(time));
    }

    protected float getBezierCoordinateY(float time) {
        c.y = 3 * start.y;
        b.y = 3 * (end.y - start.y) - c.y;
        a.y = 1 - c.y - b.y;
        return time * (c.y + time * (b.y + time * a.y));
    }

    protected float getXForTime(float time) {
        float x = time;
        float z;
        for (int i = 1; i < 14; i++) {
            z = getBezierCoordinateX(x) - time;
            if (Math.abs(z) < 1e-3) {
                break;
            }
            x -= z / getXDerivate(x);
        }
        return x;
    }

    private float getXDerivate(float t) {
        return c.x + t * (2 * b.x + 3 * a.x * t);
    }

    private float getBezierCoordinateX(float time) {
        c.x = 3 * start.x;
        b.x = 3 * (end.x - start.x) - c.x;
        a.x = 1 - c.x - b.x;
        return time * (c.x + time * (b.x + time * a.x));
    }
}
