package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;

public class SenderSelectView extends View {
    private ImageReceiver avatarImage = new ImageReceiver(this);
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private MenuDrawable menuDrawable = new MenuDrawable() {
        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            invalidate();
        }
    };
    private Drawable selectorDrawable;
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF mTempRect = new RectF();

    public SenderSelectView(Context context) {
        super(context);
    }

    public SenderSelectView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SenderSelectView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    {
        avatarImage.setRoundRadius(AndroidUtilities.dp(28));
        menuDrawable.setMiniIcon(true);
        menuDrawable.setRotateToBack(false);
        menuDrawable.setRotation(0f, false);
        menuDrawable.setRoundCap();
        menuDrawable.setCallback(this);
        updateColors();
    }

    /**
     * Updates theme colors
     */
    private void updateColors() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
        int textColor = Theme.getColor(Theme.key_chat_messagePanelVoicePressed);
        menuDrawable.setBackColor(textColor);
        menuDrawable.setIconColor(textColor);
        selectorDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), Color.TRANSPARENT, Theme.getColor(Theme.key_windowBackgroundWhite));
        selectorDrawable.setCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        avatarImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        avatarImage.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        avatarImage.setImageCoords(0, 0, getWidth(), getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        avatarImage.draw(canvas);

        int alpha = (int) (menuDrawable.getCurrentRotation() * 0xFF);
        backgroundPaint.setAlpha(alpha);
        mTempRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(mTempRect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backgroundPaint);

        canvas.save();
        canvas.translate(AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        menuDrawable.setAlpha(alpha);
        menuDrawable.setBounds(0, 0, getWidth(), getHeight());
        menuDrawable.draw(canvas);
        canvas.restore();

        selectorDrawable.setBounds(0, 0, getWidth(), getHeight());
        selectorDrawable.draw(canvas);
    }

    /**
     * Sets new User or Chat to be bound as the avatar
     * @param obj User or chat
     */
    public void setAvatar(TLObject obj) {
        avatarDrawable.setInfo(obj);
        avatarImage.setForUserOrChat(obj, avatarDrawable);
    }

    /**
     * Sets new animation progress
     * @param progress New progress
     */
    public void setProgress(float progress) {
        setProgress(progress, true);
    }

    /**
     * Sets new animation progress
     * @param progress New progress
     * @param animate If we should animate
     */
    public void setProgress(float progress, boolean animate) {
        menuDrawable.setRotation(progress, animate);
    }

    /**
     * @return Current animation progress
     */
    public float getProgress() {
        return menuDrawable.getCurrentRotation();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || selectorDrawable == who;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        selectorDrawable.setState(getDrawableState());
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        selectorDrawable.jumpToCurrentState();
    }
}
