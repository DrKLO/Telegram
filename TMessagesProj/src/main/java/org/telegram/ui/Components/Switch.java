/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Keep;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class Switch extends View {

    private RectF rectF;

    private float progress;
    private ObjectAnimator checkAnimator;
    private ObjectAnimator iconAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private Paint paint;
    private Paint paint2;

    private int drawIconType;
    private float iconProgress = 1.0f;

    private OnCheckedChangeListener onCheckedChangeListener;

    private String trackColorKey = Theme.key_switch2Track;
    private String trackCheckedColorKey = Theme.key_switch2TrackChecked;
    private String thumbColorKey = Theme.key_windowBackgroundWhite;
    private String thumbCheckedColorKey = Theme.key_windowBackgroundWhite;

    private Drawable iconDrawable;
    private int lastIconColor;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(Switch view, boolean isChecked);
    }

    public Switch(Context context) {
        super(context);
        rectF = new RectF();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeCap(Paint.Cap.ROUND);
        paint2.setStrokeWidth(AndroidUtilities.dp(2));
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    @Keep
    public void setIconProgress(float value) {
        if (iconProgress == value) {
            return;
        }
        iconProgress = value;
        invalidate();
    }

    @Keep
    public float getIconProgress() {
        return iconProgress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
    }

    private void cancelIconAnimator() {
        if (iconAnimator != null) {
            iconAnimator.cancel();
            iconAnimator = null;
        }
    }

    public void setDrawIconType(int type) {
        drawIconType = type;
    }

    public void setColors(String track, String trackChecked, String thumb, String thumbChecked) {
        trackColorKey = track;
        trackCheckedColorKey = trackChecked;
        thumbColorKey = thumb;
        thumbCheckedColorKey = thumbChecked;
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(250);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimator = null;
            }
        });
        checkAnimator.start();
    }

    private void animateIcon(boolean newCheckedState) {
        iconAnimator = ObjectAnimator.ofFloat(this, "iconProgress", newCheckedState ? 1 : 0);
        iconAnimator.setDuration(250);
        iconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                iconAnimator = null;
            }
        });
        iconAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked, drawIconType, animated);
    }

    public void setChecked(boolean checked, int iconType, boolean animated) {
        if (checked != isChecked) {
            isChecked = checked;
            if (attachedToWindow && animated) {
                animateToCheckedState(checked);
            } else {
                cancelCheckAnimator();
                setProgress(checked ? 1.0f : 0.0f);
            }
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
        if (drawIconType != iconType) {
            drawIconType = iconType;
            if (attachedToWindow && animated) {
                animateIcon(iconType == 0);
            } else {
                cancelIconAnimator();
                setIconProgress(iconType == 0 ? 1.0f : 0.0f);
            }
        }
    }

    public void setIcon(int icon) {
        if (icon != 0) {
            iconDrawable = getResources().getDrawable(icon).mutate();
            if (iconDrawable != null) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = Theme.getColor(isChecked ? trackCheckedColorKey : trackColorKey), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            iconDrawable = null;
        }
    }

    public boolean hasIcon() {
        return iconDrawable != null;
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }

        int width = AndroidUtilities.dp(31);
        int thumb = AndroidUtilities.dp(20);
        int x = (getMeasuredWidth() - width) / 2;
        int y = (getMeasuredHeight() - AndroidUtilities.dp(14)) / 2;
        int tx = x + AndroidUtilities.dp(7) + (int) (AndroidUtilities.dp(17) * progress);
        int ty = getMeasuredHeight() / 2;

        int color1 = Theme.getColor(trackColorKey);
        int color2 = Theme.getColor(trackCheckedColorKey);
        if (iconDrawable != null && lastIconColor != (isChecked ? color2 : color1)) {
            iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = (isChecked ? color2 : color1), PorterDuff.Mode.MULTIPLY));
        }

        int r1 = Color.red(color1);
        int r2 = Color.red(color2);
        int g1 = Color.green(color1);
        int g2 = Color.green(color2);
        int b1 = Color.blue(color1);
        int b2 = Color.blue(color2);
        int a1 = Color.alpha(color1);
        int a2 = Color.alpha(color2);

        int red = (int) (r1 + (r2 - r1) * progress);
        int green = (int) (g1 + (g2 - g1) * progress);
        int blue = (int) (b1 + (b2 - b1) * progress);
        int alpha = (int) (a1 + (a2 - a1) * progress);
        int color = ((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
        paint.setColor(color);
        paint2.setColor(color);

        rectF.set(x, y, x + width, y + AndroidUtilities.dp(14));
        canvas.drawRoundRect(rectF, AndroidUtilities.dp(7), AndroidUtilities.dp(7), paint);
        canvas.drawCircle(tx, ty, AndroidUtilities.dp(10), paint);

        color1 = Theme.getColor(thumbColorKey);
        color2 = Theme.getColor(thumbCheckedColorKey);
        r1 = Color.red(color1);
        r2 = Color.red(color2);
        g1 = Color.green(color1);
        g2 = Color.green(color2);
        b1 = Color.blue(color1);
        b2 = Color.blue(color2);
        a1 = Color.alpha(color1);
        a2 = Color.alpha(color2);

        red = (int) (r1 + (r2 - r1) * progress);
        green = (int) (g1 + (g2 - g1) * progress);
        blue = (int) (b1 + (b2 - b1) * progress);
        alpha = (int) (a1 + (a2 - a1) * progress);
        paint.setColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

        canvas.drawCircle(tx, ty, AndroidUtilities.dp(8), paint);

        if (iconDrawable != null) {
            iconDrawable.setBounds(tx - iconDrawable.getIntrinsicWidth() / 2, ty - iconDrawable.getIntrinsicHeight() / 2, tx + iconDrawable.getIntrinsicWidth() / 2, ty + iconDrawable.getIntrinsicHeight() / 2);
            iconDrawable.draw(canvas);
        } else if (drawIconType == 1) {
            tx -= AndroidUtilities.dp(10.8f) - AndroidUtilities.dp(1.3f) * progress;
            ty -= AndroidUtilities.dp(8.5f) - AndroidUtilities.dp(0.5f) * progress;
            int startX2 = (int) AndroidUtilities.dpf2(4.6f) + tx;
            int startY2 = (int) (AndroidUtilities.dpf2(9.5f) + ty);
            int endX2 = startX2 + AndroidUtilities.dp(2);
            int endY2 = startY2 + AndroidUtilities.dp(2);

            int startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
            int startY = (int) AndroidUtilities.dpf2(5.4f) + ty;
            int endX = startX + AndroidUtilities.dp(7);
            int endY = startY + AndroidUtilities.dp(7);

            startX = (int) (startX + (startX2 - startX) * progress);
            startY = (int) (startY + (startY2 - startY) * progress);
            endX = (int) (endX + (endX2 - endX) * progress);
            endY = (int) (endY + (endY2 - endY) * progress);
            canvas.drawLine(startX, startY, endX, endY, paint2);

            startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
            startY = (int) AndroidUtilities.dpf2(12.5f) + ty;
            endX = startX + AndroidUtilities.dp(7);
            endY = startY - AndroidUtilities.dp(7);
            canvas.drawLine(startX, startY, endX, endY, paint2);
        } else if (drawIconType == 2 || iconAnimator != null) {
            paint2.setAlpha((int) (255 * (1.0f - iconProgress)));
            canvas.drawLine(tx, ty, tx, ty - AndroidUtilities.dp(5), paint2);
            canvas.save();
            canvas.rotate(-90 * iconProgress, tx, ty);
            canvas.drawLine(tx, ty, tx + AndroidUtilities.dp(4), ty, paint2);
            canvas.restore();
        }
    }
}
