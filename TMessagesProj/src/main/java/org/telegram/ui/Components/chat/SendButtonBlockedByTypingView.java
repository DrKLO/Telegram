package org.telegram.ui.Components.chat;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.TypingDotsDrawable;

import me.vkryl.android.animator.BoolAnimator;

@SuppressLint("ViewConstructor")
public class SendButtonBlockedByTypingView extends View {
    private final BoolAnimator animatorStopAllowed = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L);


    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Theme.ResourcesProvider resourcesProvider;
    private final TypingDotsDrawable typingDotsDrawable;

    public SendButtonBlockedByTypingView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        typingDotsDrawable = new TypingDotsDrawable(true);
        typingDotsDrawable.setCallback(this);
        typingDotsDrawable.setColor(0xFFFFFFFF);
        typingDotsDrawable.setIgnoreAnimationLocks();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        typingDotsDrawable.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        typingDotsDrawable.stop();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) ||
            who == typingDotsDrawable && !animatorStopAllowed.getValue();
    }

    public void setStopAllowed(boolean allowed, boolean animated) {
        animatorStopAllowed.setValue(allowed, animated);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        DrawableUtils.setBounds(typingDotsDrawable, w / 2f, h / 2f, Gravity.CENTER);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float cx = getWidth() / 2f;
        final float cy = getHeight() / 2f;

        super.onDraw(canvas);
        paint.setColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider));
        canvas.drawCircle(cx, cy, dp(19), paint);

        final float factorStop = animatorStopAllowed.getFloatValue();
        final float factorDots = 1f - factorStop;
        if (factorDots > 0) {
            DrawableUtils.drawWithScale(canvas, typingDotsDrawable, 1.35f * factorDots);
            invalidate();
        }
        if (factorStop > 0) {
            final float s = dp(6.666f) * factorStop;
            final float r = dp(2.666f) * factorStop;
            canvas.drawRoundRect(cx - s, cy - s, cx + s, cy + s, r, r, Theme.fillingPaint(0xFFFFFFFF));
        }
    }
}
