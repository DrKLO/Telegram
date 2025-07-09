package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;

import javax.annotation.Nullable;


public class ActionBarButton extends View {
    private int buttonId = 0;
    private Drawable icon;
    private CharSequence text;
    private TextPaint textPaint;
    private Rect textBounds = new Rect();
    private Paint backgroundPaint;
    private float cornerRadius;
    private int iconSize;
    private OnClickListener onClickListener;

    private Paint ripplePaint;
    private float rippleX, rippleY;
    private float rippleRadius;
    private float maxRippleRadius;
    private boolean isRippling = false;
    private ValueAnimator rippleAnimator;
    private Path clipPath = new Path();
    private RectF clipRectF = new RectF();

    private int padding = dp(8);

    private float animationProgress = 1f;
    private int originalTextAlpha;

    public ActionBarButton(Context context, @Nullable CharSequence text, Drawable icon, int backgroundColor, int contentColor, int buttonId) {
        super(context);
        init(text, icon, backgroundColor, contentColor, buttonId);
    }

    private void init(CharSequence text, Drawable icon, int backgroundColor, int contentColor, int buttonId) {
        this.buttonId = buttonId;
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        originalTextAlpha = textPaint.getAlpha();
        textPaint.setTypeface(AndroidUtilities.bold());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint.setAlpha(80);
        ripplePaint.setStyle(Paint.Style.FILL);

        setIconSize(dp(24));
        setCornerRadius(dp(12));
        if (text != null) {
            setTextSize(12);
            setTextColor(contentColor);
            setText(text);
        }
        setIcon(icon, contentColor);
        setBackgroundColor(backgroundColor);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = 0;
        int desiredHeight = padding * 2;

        boolean hasIcon = icon != null;
        boolean hasText = text != null;

        if (hasIcon) {
            desiredHeight += iconSize;
            desiredWidth = iconSize;
        }

        if (hasText) {
            textPaint.getTextBounds(String.valueOf(text), 0, text.length(), textBounds);
            desiredHeight += textBounds.height();
            desiredWidth = Math.max(desiredWidth, textBounds.width());
        }

        if (hasIcon && hasText) {
            desiredHeight += padding;
        }

        desiredWidth += padding * 2;

        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int save = canvas.save();
        clipRectF.set(0, 0, getWidth(), getHeight());
        clipPath.rewind();
        clipPath.addRoundRect(clipRectF, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawPaint(backgroundPaint);
        boolean hasIcon = icon != null;
        boolean hasText = !TextUtils.isEmpty(text);

        int availableWidth = getWidth() - padding * 2;
        int availableHeight = getHeight() - padding * 2;

        int totalHeight = 0;
        int textHeight = 0;
        if (hasIcon) {
            totalHeight += iconSize;
        }
        if (hasText) {
            textPaint.getTextBounds(String.valueOf(text), 0, text.length(), textBounds);
            textHeight = textBounds.height();
            totalHeight += textHeight;
        }
        if (hasIcon && hasText) {
            totalHeight += padding;
        }

        int startY = padding + (availableHeight - totalHeight) / 2;
        int y = startY;
        int centerX = padding + availableWidth / 2;

        if (hasIcon) {
            icon.setAlpha((int) (255 * animationProgress));
            icon.setBounds(centerX - iconSize / 2, y, centerX + iconSize / 2, y + iconSize);

            canvas.save();
            canvas.scale(animationProgress, animationProgress, icon.getBounds().centerX(), icon.getBounds().centerY());
            icon.draw(canvas);
            canvas.restore();

            y += iconSize + (hasText ? padding : 0);
        }

        if (hasText) {
            textPaint.setAlpha((int) (originalTextAlpha * animationProgress));

            float textCenterX = centerX;
            float textCenterY = y + (float) textHeight / 2;

            canvas.save();
            canvas.scale(animationProgress, animationProgress, textCenterX, textCenterY);
            canvas.drawText(String.valueOf(text),
                    centerX - (float) textBounds.width() / 2,
                    y + textHeight,
                    textPaint);
            canvas.restore();
        }

        if (isRippling) {
            canvas.drawCircle(rippleX, rippleY, rippleRadius, ripplePaint);
        }

        canvas.restoreToCount(save);
    }

    public void setAnimationProgress(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (this.animationProgress == progress) {
            return;
        }
        this.animationProgress = progress;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                rippleX = event.getX();
                rippleY = event.getY();
                startRipple();
                return true;
            case MotionEvent.ACTION_UP:
                if (onClickListener != null && isInside(event)) {
                    onClickListener.onClick(this);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                stopRipple();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void startRipple() {
        isRippling = true;

        float maxX = Math.max(rippleX, getWidth() - rippleX);
        float maxY = Math.max(rippleY, getHeight() - rippleY);
        maxRippleRadius = (float) Math.sqrt(maxX * maxX + maxY * maxY);

        rippleAnimator = ValueAnimator.ofFloat(0, maxRippleRadius);
        rippleAnimator.setDuration(300);
        rippleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stopRipple();
            }
        });
        rippleAnimator.addUpdateListener(animation -> {
            rippleRadius = (float) animation.getAnimatedValue();
            invalidate();
        });
        rippleAnimator.start();
    }

    private void stopRipple() {
        if (rippleAnimator != null && rippleAnimator.isRunning()) {
            rippleAnimator.cancel();
        }
        isRippling = false;
        invalidate();
    }

    private boolean isInside(MotionEvent e) {
        return e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= getHeight();
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
        requestLayout();
        invalidate();
    }

    public void setIcon(Drawable icon, int color) {
        this.icon = icon;
        this.icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        requestLayout();
        invalidate();
    }

    public void setText(CharSequence text) {
        this.text = text;
        requestLayout();
    }

    public void setTextSize(int sizeInDp) {
        int newSize = dp(sizeInDp);
        if (newSize == textPaint.getTextSize()) {
            return;
        }
        textPaint.setTextSize(newSize);
        requestLayout();
        invalidate();
    }

    public CharSequence getText() {
        if (text == null) {
            return "";
        }
        return text;
    }

    public void setTextColor(int color) {
        this.textPaint.setColor(color);
        invalidate();
    }

    public void setBackgroundColor(int color) {
        this.backgroundPaint.setColor(color);
        this.backgroundPaint.setAlpha(204);
        invalidate();
    }

    public void setRippleColor(int color) {
        this.ripplePaint.setColor(color);
        invalidate();
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = cornerRadius;
        requestLayout();
    }

    public void setIconSize(int size) {
        this.iconSize = size;
        requestLayout();
        invalidate();
    }

    public int getId() {
        return buttonId;
    }

    public void setOnActionClickListener(OnClickListener listener) {
        this.onClickListener = listener;
    }
}
