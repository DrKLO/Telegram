package org.telegram.ui.Components.poll.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RadialProgress;

import me.vkryl.android.animator.BoolAnimator;

public class PollInstantButtonDrawable extends PollButtonDrawableBase {
    private final AnimatedTextView.AnimatedTextDrawable buttonTextAnimatedDrawable;
    private final RadialProgress radialProgress;
    private final BoolAnimator animatorProgressVisible;

    public PollInstantButtonDrawable(View parent, Theme.ResourcesProvider resourcesProvider) {
        super(resourcesProvider);

        radialProgress = new RadialProgress(parent);
        radialProgress.setBackground(null, true, false);
        radialProgress.setRotationTime(650);
        radialProgress.setProgress(0.69f, false);
        radialProgress.setStrokeWidth(dp(1.5f));

        animatorProgressVisible = new BoolAnimator(parent, CubicBezierInterpolator.EASE_OUT_QUINT, 260);
        buttonTextAnimatedDrawable = new AnimatedTextView.AnimatedTextDrawable(true, false, false);
        buttonTextAnimatedDrawable.setTypeface(AndroidUtilities.bold());
        buttonTextAnimatedDrawable.setTextSize(dp(13));
        buttonTextAnimatedDrawable.setGravity(Gravity.CENTER);

        setSelectorsColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
    }

    public void setButtonText(CharSequence text, boolean animated) {
        buttonTextAnimatedDrawable.setText(text, animated);
    }

    public void setButtonTextColor(int color) {
        buttonTextAnimatedDrawable.setTextColor(color);
        radialProgress.setProgressColor(color);
    }

    private float offsetY;

    public void setTextOffsetY(float offset) {
        if (this.offsetY != offset) {
            this.offsetY = offset;
            checkBounds(getBounds());
        }
    }

    public void setLoading(boolean loading, boolean animated) {
        if (animatorProgressVisible.getValue() != loading) {
            animatorProgressVisible.setValue(loading, animated);
        }
    }

    public float getProgressFactor() {
        return animatorProgressVisible.getFloatValue();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float loadingFactor = animatorProgressVisible.getFloatValue();
        if (loadingFactor < 1) {
            DrawableUtils.drawWithScale(canvas, buttonTextAnimatedDrawable, 1f - loadingFactor);
        }
        if (loadingFactor > 0) {
            final float cx = getBounds().exactCenterX();
            final float cy = getBounds().exactCenterY();
            canvas.save();
            canvas.scale(loadingFactor, loadingFactor, cx, cy);
            radialProgress.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        checkBounds(getBounds());

        final int r = dp(11);
        final int cx = bounds.centerX();
        final int cy = bounds.centerY();
        radialProgress.setProgressRect(cx - r, cy - r, cx + r, cy + r);
    }

    private void checkBounds(@NonNull Rect bounds) {
        int o = (int) offsetY;
        buttonTextAnimatedDrawable.setBounds(bounds.left, bounds.top + o, bounds.right, bounds.bottom + o);
    }

    @Override
    public void setupCallbacks(Callback callback) {
        super.setupCallbacks(callback);
        buttonTextAnimatedDrawable.setCallback(callback);
    }

    @Override
    protected void onAlphaChanged(int alpha) {
        super.onAlphaChanged(alpha);
        buttonTextAnimatedDrawable.setAlpha(alpha);
    }

    @Override
    public boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == buttonTextAnimatedDrawable;
    }
}
