package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class BadgeLevelDrawable extends Drawable implements Drawable.Callback {
    private final Context context;
    private final AnimatedTextView.AnimatedTextDrawable text;

    private Drawable inner, outer;
    private int innerColor;
    private int outerColor;
    private int textColor;

    private int lastLevelIndex;
    private int level;

    // private final Runnable update = this::debugUpdateStart;

    public BadgeLevelDrawable(Context context) {
        this.context = context;
        this.text = new AnimatedTextView.AnimatedTextDrawable();
        this.text.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
        this.text.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.text.setTextSize(dp(10));
        this.text.setGravity(Gravity.CENTER);
        this.text.setCallback(this);
        this.text.centerY = true;
        init();
    }

    public void setBadgeLevel(int level, boolean animated) {
        if (this.level != level || inner == null || outer == null) {
            text.setText(level >= 0 ? Integer.toString(level) : "!", animated);
            setLevelIndex(getIndexByLevel(this.level = level));
            invalidateSelf();
        }
    }

    public void setInnerColor(int innerColor) {
        if (this.innerColor != innerColor) {
            this.innerColor = innerColor;
            if (inner != null) {
                inner.setColorFilter(innerColor, PorterDuff.Mode.MULTIPLY);
                invalidateSelf();
            }
        }
    }

    public void setOuterColor(int outerColor) {
        if (this.outerColor != outerColor) {
            this.outerColor = outerColor;
            if (inner != null) {
                outer.setColorFilter(outerColor, PorterDuff.Mode.MULTIPLY);
                invalidateSelf();
            }
        }
    }

    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            this.textColor = textColor;
            this.text.setTextColor(textColor, false);
            invalidateSelf();
        }
    }

    private void setLevelIndex(int index) {
        if (this.lastLevelIndex != index || inner == null || outer == null) {
            inner = context.getResources().getDrawable(res[index * 2]).mutate();
            inner.setColorFilter(innerColor, PorterDuff.Mode.MULTIPLY);
            outer = context.getResources().getDrawable(res[index * 2 + 1]).mutate();
            outer.setColorFilter(outerColor, PorterDuff.Mode.MULTIPLY);
            lastLevelIndex = index;
            checkBounds();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (outer == null || inner == null) {
            return;
        }

        outer.draw(canvas);
        inner.draw(canvas);

        canvas.save();
        canvas.translate(getBounds().exactCenterX(), getBounds().exactCenterY());
        text.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        checkBounds();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getIntrinsicWidth() {
        return dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24);
    }

    @Override
    public void setAlpha(int alpha) {
        setInnerColor(ColorUtils.setAlphaComponent(innerColor, alpha));
        setOuterColor(ColorUtils.setAlphaComponent(outerColor, alpha));
        setTextColor(ColorUtils.setAlphaComponent(textColor, alpha));
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }

    public void debugUpdateStop() {
        //AndroidUtilities.cancelRunOnUIThread(update);
    }

    public void debugUpdateStart() {
        // setBadgeLevel((level + 1) % 100, true);
        // AndroidUtilities.runOnUIThread(update, 1000L);
    }

    private void checkBounds() {
        if (inner != null) {
            inner.setBounds(getBounds());
        }
        if (outer != null) {
            outer.setBounds(getBounds());
        }
    }

    private static @DrawableRes int[] res;
    private static void init() {
        if (res != null) {
            return;
        }
        res = new int[]{
            R.drawable.profile_level1_inner, R.drawable.profile_level1_outer,
            R.drawable.profile_level2_inner, R.drawable.profile_level2_outer,
            R.drawable.profile_level3_inner, R.drawable.profile_level3_outer,
            R.drawable.profile_level4_inner, R.drawable.profile_level4_outer,
            R.drawable.profile_level5_inner, R.drawable.profile_level5_outer,
            R.drawable.profile_level6_inner, R.drawable.profile_level6_outer,
            R.drawable.profile_level7_inner, R.drawable.profile_level7_outer,
            R.drawable.profile_level8_inner, R.drawable.profile_level8_outer,
            R.drawable.profile_level9_inner, R.drawable.profile_level9_outer,
            R.drawable.profile_level10_inner, R.drawable.profile_level10_outer,
            R.drawable.profile_level20_inner, R.drawable.profile_level20_outer,
            R.drawable.profile_level30_inner, R.drawable.profile_level30_outer,
            R.drawable.profile_level40_inner, R.drawable.profile_level40_outer,
            R.drawable.profile_level50_inner, R.drawable.profile_level50_outer,
            R.drawable.profile_level60_inner, R.drawable.profile_level60_outer,
            R.drawable.profile_level70_inner, R.drawable.profile_level70_outer,
            R.drawable.profile_level80_inner, R.drawable.profile_level80_outer,
            R.drawable.profile_level90_inner, R.drawable.profile_level90_outer,
            R.drawable.profile_level_minus_inner, R.drawable.profile_level_minus_outer
        };
    }

    private static int getIndexByLevel(int level) {
        if (level < 0) {
            return 18;
        }
        return MathUtils.clamp(level <= 10 ? (level - 1) : (8 + level / 10), 0, 17);
    }
}
