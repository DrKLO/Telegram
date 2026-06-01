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
import org.telegram.ui.Components.TypingDotsDrawable;

@SuppressLint("ViewConstructor")
public class SendButtonBlockedByTypingView extends View {
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
        return super.verifyDrawable(who) || who == typingDotsDrawable;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        DrawableUtils.setBounds(typingDotsDrawable, w / 2f, h / 2f, Gravity.CENTER);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider));
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(19), paint);

        DrawableUtils.drawWithScale(canvas, typingDotsDrawable, 1.35f);
        invalidate();
    }
}
