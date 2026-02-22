package org.telegram.messenger.utils;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsCompat;

public class GradientProtectionDrawable extends GradientDrawable {
    private final Interpolator mInterpolator;
    private final int[] mColors;
    private int mColor;

    public GradientProtectionDrawable(
            @WindowInsetsCompat.Side.InsetsSide int side,
            @ColorInt int color) {
        this(side, color, DEFAULT_INTERPOLATOR, 8);
    }

    public GradientProtectionDrawable(
        @WindowInsetsCompat.Side.InsetsSide int side,
        @ColorInt int color,
        Interpolator interpolator,
        int n
    ) {
        super();
        mInterpolator = interpolator;
        mColors = new int[n];
        setSide(side);
        setColor(color);
    }

    public void setSide(@WindowInsetsCompat.Side.InsetsSide int side) {
        switch (side) {
            case WindowInsetsCompat.Side.LEFT:
                setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                break;
            case WindowInsetsCompat.Side.TOP:
                setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                break;
            case WindowInsetsCompat.Side.RIGHT:
                setOrientation(GradientDrawable.Orientation.RIGHT_LEFT);
                break;
            case WindowInsetsCompat.Side.BOTTOM:
                setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
                break;
        }
    }

    public void setColor(@ColorInt int color) {
        if (mColor == color) {
            return;
        }

        mColor = color;
        fillColors(mInterpolator, mColor, mColors);
        setColors(mColors);
    }


    /* source: androidx.core.view.insets.GradientProtection */

    public static final Interpolator DEFAULT_INTERPOLATOR = new PathInterpolator(0.42f, 0f, 0.58f, 1f);

    public static void fillColors(Interpolator interpolator, int color, int[] colors) {
        final int steps = colors.length - 1;
        final int a = Color.alpha(color);
        for (int i = steps; i >= 0; i--) {
            final float alpha = interpolator.getInterpolation((steps - i)  / (float) steps);
            colors[i] = ColorUtils.setAlphaComponent(color, (int) (alpha * a));
        }
    }
}
