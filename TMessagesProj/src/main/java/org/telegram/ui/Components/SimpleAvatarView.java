package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;

public class SimpleAvatarView extends View {
    public final static int SELECT_ANIMATION_DURATION = 200;

    private ImageReceiver avatarImage = new ImageReceiver(this);
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float selectProgress;
    private boolean isAvatarHidden;
    private ValueAnimator animator;

    public SimpleAvatarView(Context context) {
        super(context);
    }

    public SimpleAvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleAvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    {
        avatarImage.setRoundRadius(AndroidUtilities.dp(28));
        selectPaint.setStrokeWidth(AndroidUtilities.dp(2));
        selectPaint.setStyle(Paint.Style.STROKE);
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        float scale = 0.9f + selectProgress * 0.1f;
        canvas.scale(scale, scale);
        selectPaint.setColor(Theme.getColor(Theme.key_dialogTextBlue));
        selectPaint.setAlpha((int) (Color.alpha(selectPaint.getColor()) * selectProgress));
        float stroke = selectPaint.getStrokeWidth();
        AndroidUtilities.rectTmp.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
        canvas.drawArc(AndroidUtilities.rectTmp, -90, selectProgress * 360, false, selectPaint);
        canvas.restore();

        if (!isAvatarHidden) {
            float pad = selectPaint.getStrokeWidth() * 2.5f * selectProgress;
            avatarImage.setImageCoords(pad, pad, getWidth() - pad * 2, getHeight() - pad * 2);
            avatarImage.draw(canvas);
        }
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
     * @return If avatar is currently selected
     */
    public boolean isSelected() {
        return selectProgress == 1;
    }

    /**
     * Sets avatar selected value
     * @param s If avatar is selected
     * @param animate If we should animate status change
     */
    public void setSelected(boolean s, boolean animate) {
        if (animator != null) {
            animator.cancel();
        }
        if (animate) {
            float to = s ? 1 : 0;
            ValueAnimator anim = ValueAnimator.ofFloat(selectProgress, to).setDuration(SELECT_ANIMATION_DURATION);
            anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
            anim.addUpdateListener(animation -> {
                selectProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animator == animation) {
                        animator = null;
                    }
                }
            });
            anim.start();
            animator = anim;
        } else {
            selectProgress = s ? 1 : 0;
            invalidate();
        }
    }

    /**
     * Sets avatar hidden
     * @param h If we should hide avatar from view
     */
    public void setHideAvatar(boolean h) {
        isAvatarHidden = h;
        invalidate();
    }
}
