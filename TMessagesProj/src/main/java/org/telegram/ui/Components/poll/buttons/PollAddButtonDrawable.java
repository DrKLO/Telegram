package org.telegram.ui.Components.poll.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.poll.PollAttachedMedia;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("UseCompatLoadingForDrawables")
public class PollAddButtonDrawable extends PollButtonDrawableBase implements FactorAnimator.Target {
    public static final int MOTION_NONE = -1;
    public static final int MOTION_ALL = 0;

    private final BoolAnimator animatorIsEnabled = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    private final int[] pressedState = new int[] { android.R.attr.state_enabled, android.R.attr.state_pressed };
    private final Drawable addDrawable;
    private final TextPaint addAnOptionTextPaint;
    private StaticLayout addAnOptionText;
    private int addAnOptionLastWidth;
    private int textLastColor;

    public PollAddButtonDrawable(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(resourcesProvider);

        this.addDrawable = context.getResources().getDrawable(R.drawable.outline_poll_add_24).mutate();
        this.addAnOptionTextPaint = new TextPaint(Theme.chat_audioPerformerPaint);

        setSelectorsColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        checkIconsAlpha();
        checkTextAlpha();
    }

    public void setIsEditEnabled(boolean isEditEnabled, boolean animated) {
        animatorIsEnabled.setValue(isEditEnabled, animated);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        getSelectorDrawable().draw(canvas);

        final float isActiveFactor = animatorIsEnabled.getFloatValue();
        DrawableUtils.drawWithScale(canvas, addDrawable, 1f - isActiveFactor);

        if (addAnOptionText != null) {
            canvas.save();
            canvas.translate(bounds.left + dp(44), bounds.top + dp(13.66f));
            addAnOptionText.draw(canvas);
            canvas.restore();
        }
    }



    public void setTextColor(int color) {
        if (textLastColor != color) {
            textLastColor = color;
            addAnOptionTextPaint.setColor(color);

            final ColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            addDrawable.setColorFilter(filter);
            checkTextAlpha();
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);

        final float cy = bounds.exactCenterY();
        final float cxl = bounds.left + dp(22.33f);
        final float cxr = bounds.right - dp(27f);
        final int ss = dp(44);

        DrawableUtils.setBounds(addDrawable, cxl, cy, Gravity.CENTER);

        final int width = bounds.width() - dp(56);
        if (addAnOptionText == null || addAnOptionLastWidth != width) {
            addAnOptionLastWidth = width;
            addAnOptionText = new StaticLayout(getString(R.string.PollAddAnOption), addAnOptionTextPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
    }


    private void checkTextAlpha() {
        final float isActiveFactor = animatorIsEnabled.getFloatValue();
        final int alpha = getAlpha();
        addAnOptionTextPaint.setAlpha((int)(alpha * (1f - isActiveFactor)));
    }

    private void checkIconsAlpha() {
        final float isActiveFactor = animatorIsEnabled.getFloatValue();
        final int alpha = getAlpha();

        addDrawable.setAlpha((int)(alpha * (1f - isActiveFactor)));
    }

    @Override
    protected void onAlphaChanged(int alpha) {
        super.onAlphaChanged(alpha);
        checkIconsAlpha();
        checkTextAlpha();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        checkIconsAlpha();
        checkTextAlpha();
        invalidateSelf();
    }

    public int checkMotionPressed(int x, int y) {
        final Rect bounds = getBounds();

        if (bounds.contains(x, y)) {
            getSelectorDrawable().setHotspot(x, y);
            getSelectorDrawable().setState(pressedState);
            return MOTION_ALL;
        }
        return MOTION_NONE;
    }
}
