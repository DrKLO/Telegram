package org.telegram.ui.community;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;

public class CommunityArrowDrawable extends Drawable {
    private final Drawable arrowDrawable;
    private int lastColor;
    private boolean drawCircle;

    public CommunityArrowDrawable() {
        this.arrowDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.settings_arrow).mutate();
    }

    public CommunityArrowDrawable withCircle() {
        drawCircle = true;
        return this;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float cx = getBounds().exactCenterX();
        final float cy = getBounds().exactCenterY();

        final int bgColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int iconColor = Theme.getColor(Theme.key_windowBackgroundWhite);

        if (lastColor != iconColor) {
            lastColor = iconColor;
            arrowDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        }


        canvas.drawCircle(cx, cy, dp(46 / 6f), Theme.fillingPaint(ColorUtils.setAlphaComponent(iconColor, alpha)));

        canvas.drawCircle(cx, cy, dp(40 / 6f), Theme.fillingPaint(ColorUtils.setAlphaComponent(bgColor, alpha)));
        DrawableUtils.setBounds(arrowDrawable, cx, cy, Gravity.CENTER);
        canvas.translate(0, dp(0.66f));
        canvas.save();
        canvas.rotate(90, cx, cy);
        DrawableUtils.drawWithScale(canvas, arrowDrawable, 0.8f);
        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(40 / 3f);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(40 / 3f);
    }

    private int alpha = 255;

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        arrowDrawable.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
