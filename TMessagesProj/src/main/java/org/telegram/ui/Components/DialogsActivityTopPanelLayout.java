package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import me.vkryl.android.animator.ListAnimator;

public class DialogsActivityTopPanelLayout extends AnimatedLinearLayout {
    private final GradientDrawable fadeDrawable;

    public DialogsActivityTopPanelLayout(@NonNull Context context) {
        super(context);

        setOrientation(LinearLayout.VERTICAL);
        fadeDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, null);

        updateColors();
    }

    BlurredBackgroundDrawable backgroundDrawable;

    public void setBlurredBackground(BlurredBackgroundDrawable background) {
        backgroundDrawable = background;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkBoundsAndClipping();
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkBoundsAndClipping();
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev)
            || ev.getAction() == MotionEvent.ACTION_DOWN && backgroundDrawable != null && backgroundDrawable.getBounds().contains((int) ev.getX(), (int) ev.getY());
    }

    private final Path clipPath = new Path();
    private final RectF clipRectF = new RectF();

    private void checkBoundsAndClipping() {
        final float bgHeight = getMetadata().getTotalHeight();
        final float bgAlpha = getMetadata().getTotalVisibility();

        clipRectF.set(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getPaddingTop() + bgHeight);

        final float r = Math.min(dp(24), Math.min(clipRectF.width(), clipRectF.height()) / 2f);
        clipPath.rewind();
        clipPath.addRoundRect(clipRectF, r, r, Path.Direction.CW);

        fadeDrawable.setBounds(0, 0, getMeasuredWidth(), Math.min(dp(30), (int) bgHeight + getPaddingTop()));
        fadeDrawable.setAlpha((int) (bgAlpha * 255));

        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha((int) (bgAlpha * 255));
            backgroundDrawable.setBounds(dp(4), 0, getMeasuredWidth() - dp(4), getPaddingTop() + getPaddingBottom() + (int) bgHeight);
            backgroundDrawable.setRadius(Math.min(dp(24), bgHeight / 2));
        }
    }

    public void updateColors() {
        if (backgroundDrawable != null) {
            backgroundDrawable.updateColors();
        }

        final int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        final int alpha = Color.alpha(color);
        fadeDrawable.setColors(new int[]{
            ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xC0 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0x90 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0)
        });

        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        fadeDrawable.draw(canvas);
        if (backgroundDrawable != null) {
            backgroundDrawable.draw(canvas);
        }

        canvas.save();
        canvas.clipPath(clipPath);
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<?> entry = getEntry(a);
            final float top = getPaddingTop() + entry.getRectF().top;

            final float position = entry.getPosition();
            final float alpha = entry.getVisibility() * Math.min(1, position);

            Theme.dividerPaint.setAlpha((int) (255 * alpha));
            final float offsetL = getPaddingLeft() + dp(16) * (1f - alpha);
            final float offsetR = getPaddingRight() + dp(16) * (1f - alpha);
            canvas.drawLine(offsetL, top, getWidth() - offsetR, top, Theme.dividerPaint);
        }

        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
