package org.telegram.ui.Components;


import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class UnreadCounterTextView extends View {

    private int currentCounter;
    private String currentCounterString;
    private int textWidth;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private int circleWidth;
    private int rippleColor;

    private Drawable icon;
    private StaticLayout textLayout;
    private Drawable iconOut;
    private StaticLayout textLayoutOut;
    private int layoutTextWidth;
    private TextPaint layoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Drawable selectableBackground;

    ValueAnimator replaceAnimator;
    float replaceProgress = 1f;
    boolean animatedFromBottom;
    int textColor;
    int panelBackgroundColor;
    int counterColor;
    CharSequence lastText;

    int textColorKey = Theme.key_chat_fieldOverlayText;

    public UnreadCounterTextView(Context context) {
        super(context);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setTypeface(AndroidUtilities.bold());

        layoutPaint.setTextSize(AndroidUtilities.dp(15));
        layoutPaint.setTypeface(AndroidUtilities.bold());
    }

    public void setText(CharSequence text, boolean animatedFromBottom) {
        if (lastText == text) {
            return;
        }
        lastText = text;
        this.animatedFromBottom = animatedFromBottom;
        textLayoutOut = textLayout;
        iconOut = icon;
        layoutPaint.setTypeface(AndroidUtilities.bold());
        layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
        icon = null;
        textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        setContentDescription(text);
        invalidate();

        if (textLayoutOut != null || iconOut != null) {
            if (replaceAnimator != null) {
                replaceAnimator.cancel();
            }
            replaceProgress = 0;
            replaceAnimator = ValueAnimator.ofFloat(0,1f);
            replaceAnimator.addUpdateListener(animation -> {
                replaceProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            replaceAnimator.setDuration(150);
            replaceAnimator.start();
        }
    }

    public void setText(CharSequence text) {
        layoutPaint.setTypeface(AndroidUtilities.bold());
        layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
        icon = null;
        textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        setContentDescription(text);
        invalidate();
    }

    public void setTextInfo(CharSequence text) {
        layoutPaint.setTypeface(null);
        layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
        icon = null;
        textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth + 1, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        setContentDescription(text);
        invalidate();
    }

    public void setTextInfo(Drawable icon, CharSequence text) {
        layoutPaint.setTypeface(null);
        layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
        this.icon = icon;
        textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth + 1, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        setContentDescription(text);
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (selectableBackground != null) {
            selectableBackground.setState(getDrawableState());
        }
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        if (selectableBackground != null) {
            return selectableBackground == drawable || super.verifyDrawable(drawable);
        }
        return super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (selectableBackground != null) {
            selectableBackground.jumpToCurrentState();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (textLayout != null) {
                int lineWidth = (int) Math.ceil(textLayout.getLineWidth(0));
                int contentWidth;
                if (getMeasuredWidth() == ((View)getParent()).getMeasuredWidth()) {
                    contentWidth = getMeasuredWidth() - AndroidUtilities.dp(96);
                } else {
                    if (isTouchFullWidth())  {
                        contentWidth = getMeasuredWidth();
                    } else {
                        contentWidth = lineWidth + (circleWidth > 0 ? circleWidth + AndroidUtilities.dp(8) : 0);
                        contentWidth += AndroidUtilities.dp(48);
                    }
                }
                int x = (getMeasuredWidth() - contentWidth) / 2;
                rect.set(
                        x, getMeasuredHeight() / 2f - contentWidth / 2f,
                        x + contentWidth, getMeasuredHeight() / 2f + contentWidth / 2f
                );
                if (!rect.contains(event.getX(), event.getY())) {
                    setPressed(false);
                    return false;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    protected Theme.ResourcesProvider getResourceProvider() {
        return null;
    }

    protected boolean isTouchFullWidth() {
        return false;
    }

    protected void updateCounter() {
    }

    protected float getTopOffset() {
        return 0;
    }

    public void setCounter(int newCount) {
        if (currentCounter != newCount) {
            currentCounter = newCount;
            if (currentCounter == 0) {
                currentCounterString = null;
                circleWidth = 0;
            } else {
                currentCounterString = AndroidUtilities.formatWholeNumber(currentCounter, 0);
                textWidth = (int) Math.ceil(textPaint.measureText(currentCounterString));
                int newWidth = Math.max(AndroidUtilities.dp(20), AndroidUtilities.dp(12) + textWidth);
                if (circleWidth != newWidth) {
                    circleWidth = newWidth;
                }
            }
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = textLayout;
        int color = Theme.getColor(isEnabled() ? textColorKey : Theme.key_windowBackgroundWhiteGrayText, getResourceProvider());
        if (textColor != color) {
            layoutPaint.setColor(textColor = color);
        }
        color = Theme.getColor(Theme.key_chat_messagePanelBackground, getResourceProvider());
        if (panelBackgroundColor != color) {
            textPaint.setColor(panelBackgroundColor = color);
        }
        color = Theme.getColor(Theme.key_chat_goDownButtonCounterBackground, getResourceProvider());
        if (counterColor != color) {
            paint.setColor(counterColor = color);
        }

        if (getParent() != null) {
            int contentWidth = getMeasuredWidth();
            int x = (getMeasuredWidth() - contentWidth) / 2;
            if (rippleColor != Theme.getColor(textColorKey, getResourceProvider()) || selectableBackground == null) {
                selectableBackground = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(60), 0, ColorUtils.setAlphaComponent(rippleColor = Theme.getColor(textColorKey, getResourceProvider()), 26));
                selectableBackground.setCallback(this);
            }
            int start = (getLeft() + x) <= 0 ? x - AndroidUtilities.dp(20) : x;
            int end = x + contentWidth > ((View) getParent()).getMeasuredWidth() ? x + contentWidth + AndroidUtilities.dp(20) : x + contentWidth;
            selectableBackground.setBounds(
                    start, getMeasuredHeight() / 2 - contentWidth / 2,
                    end, getMeasuredHeight() / 2 + contentWidth / 2
            );
            selectableBackground.draw(canvas);
        }
        if (textLayout != null) {
            canvas.save();
            if (replaceProgress != 1f && textLayoutOut != null) {
                int oldAlpha = layoutPaint.getAlpha();

                canvas.save();
                canvas.translate((getMeasuredWidth() - textLayoutOut.getWidth()) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2 + getTopOffset());
                canvas.translate(+(iconOut != null ? iconOut.getIntrinsicWidth() / 2 + AndroidUtilities.dp(3) : 0), (animatedFromBottom ? -1f : 1f) * AndroidUtilities.dp(18) * replaceProgress);
                if (iconOut != null) {
                    iconOut.setBounds(
                            -iconOut.getIntrinsicWidth() - AndroidUtilities.dp(6),
                            (textLayout.getHeight() - iconOut.getIntrinsicHeight()) / 2 + AndroidUtilities.dp(1),
                            -AndroidUtilities.dp(6),
                            (textLayout.getHeight() + iconOut.getIntrinsicHeight()) / 2 + AndroidUtilities.dp(1)
                    );
                    iconOut.setAlpha((int) (oldAlpha * (1f - replaceProgress)));
                    iconOut.draw(canvas);
                }
                layoutPaint.setAlpha((int) (oldAlpha * (1f - replaceProgress)));
                textLayoutOut.draw(canvas);
                canvas.restore();

                canvas.save();
                canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2 + getTopOffset());
                canvas.translate(+(icon != null ? icon.getIntrinsicWidth() / 2 + AndroidUtilities.dp(3) : 0), (animatedFromBottom ? 1f : -1f) * AndroidUtilities.dp(18) * (1f - replaceProgress));
                if (icon != null) {
                    icon.setBounds(
                            -icon.getIntrinsicWidth() - AndroidUtilities.dp(6),
                            (textLayout.getHeight() - icon.getIntrinsicHeight()) / 2 + AndroidUtilities.dp(1),
                            -AndroidUtilities.dp(6),
                            (textLayout.getHeight() + icon.getIntrinsicHeight()) / 2 + AndroidUtilities.dp(1)
                    );
                    icon.setAlpha((int) (oldAlpha * (replaceProgress)));
                    icon.draw(canvas);
                }
                layoutPaint.setAlpha((int) (oldAlpha * (replaceProgress)));
                textLayout.draw(canvas);
                canvas.restore();

                layoutPaint.setAlpha(oldAlpha);
            } else {
                canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2 - circleWidth / 2 + (icon != null ? icon.getIntrinsicWidth() / 2 + AndroidUtilities.dp(3) : 0), (getMeasuredHeight() - textLayout.getHeight()) / 2 + getTopOffset());
                if (icon != null) {
                    icon.setBounds(
                            -icon.getIntrinsicWidth()-AndroidUtilities.dp(6),
                            (textLayout.getHeight() - icon.getIntrinsicHeight()) / 2 + AndroidUtilities.dp(1),
                            -AndroidUtilities.dp(6),
                            (textLayout.getHeight() + icon.getIntrinsicHeight()) / 2 + AndroidUtilities.dp(1)
                    );
                    icon.setAlpha(255);
                    icon.draw(canvas);
                }
                textLayout.draw(canvas);
            }

            canvas.restore();
        }

        if (currentCounterString != null) {
            if (layout != null) {
                int lineWidth = (int) Math.ceil(layout.getLineWidth(0));
                int x = (getMeasuredWidth() - lineWidth) / 2 + lineWidth - circleWidth / 2 + AndroidUtilities.dp(6);
                rect.set(x, getMeasuredHeight() / 2 - AndroidUtilities.dp(10), x + circleWidth, getMeasuredHeight() / 2 + AndroidUtilities.dp(10));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
                canvas.drawText(currentCounterString, rect.centerX() - textWidth / 2.0f, rect.top + AndroidUtilities.dp(14.5f), textPaint);
            }
        }
    }

    public void setTextColorKey(int textColorKey) {
        this.textColorKey = textColorKey;
        invalidate();
    }
}