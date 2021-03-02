package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CounterView extends View {

    private final static int ANIMATION_TYPE_IN = 0;
    private final static int ANIMATION_TYPE_OUT = 1;
    private final static int ANIMATION_TYPE_REPLACE = 2;

    int animationType = -1;

    Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    RectF rectF = new RectF();

    int currentCount;
    private boolean countAnimationIncrement;
    private ValueAnimator countAnimator;
    private float countChangeProgress = 1f;
    private StaticLayout countLayout;
    private StaticLayout countOldLayout;
    private StaticLayout countAnimationStableLayout;
    private StaticLayout countAnimationInLayout;

    private int countWidthOld;
    private int countWidth;

    private int circleColor;
    private int textColor;
    private String textColorKey = Theme.key_chat_goDownButtonCounter;
    private String circleColorKey = Theme.key_chat_goDownButtonCounterBackground;

    int lastH;
    int gravity = Gravity.CENTER;
    float countLeft;
    float x;

    private boolean reverseAnimation;
    public float horizontalPadding;


    public CounterView(Context context) {
        super(context);
        setVisibility(View.GONE);
        circlePaint.setColor(Color.BLACK);

        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setTextSize(AndroidUtilities.dp(13));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() != lastH) {
            int count = currentCount;
            currentCount = -1;
            setCount(count, animationType == ANIMATION_TYPE_IN);
            lastH = getMeasuredHeight();
        }
    }

    public void setCount(int count, boolean animated) {
        if (count == currentCount) {
            return;
        }
        if (countAnimator != null) {
            countAnimator.cancel();
        }
        if (count > 0) {
            setVisibility(View.VISIBLE);
        }
        if (!animated) {
            currentCount = count;
            if (count == 0) {
                setVisibility(View.GONE);
                return;
            }
            String newStr = String.valueOf(count);
            countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(newStr)));
            countLayout = new StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            invalidate();
        }
        String newStr = String.valueOf(count);

        if (animated) {
            if (countAnimator != null) {
                countAnimator.cancel();
            }
            countChangeProgress = 0f;
            countAnimator = ValueAnimator.ofFloat(0, 1f);
            countAnimator.addUpdateListener(valueAnimator -> {
                countChangeProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            countAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animationType = -1;
                    countChangeProgress = 1f;
                    countOldLayout = null;
                    countAnimationStableLayout = null;
                    countAnimationInLayout = null;
                    if (currentCount == 0) {
                        setVisibility(View.GONE);
                    }
                    invalidate();
                }
            });
            if (currentCount <= 0) {
                animationType = ANIMATION_TYPE_IN;
                countAnimator.setDuration(220);
                countAnimator.setInterpolator(new OvershootInterpolator());
            } else if (count == 0) {
                animationType = ANIMATION_TYPE_OUT;
                countAnimator.setDuration(150);
                countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            } else {
                animationType = ANIMATION_TYPE_REPLACE;
                countAnimator.setDuration(430);
                countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            }
            if (countLayout != null) {
                String oldStr = String.valueOf(currentCount);

                if (oldStr.length() == newStr.length()) {
                    SpannableStringBuilder oldSpannableStr = new SpannableStringBuilder(oldStr);
                    SpannableStringBuilder newSpannableStr = new SpannableStringBuilder(newStr);
                    SpannableStringBuilder stableStr = new SpannableStringBuilder(newStr);
                    for (int i = 0; i < oldStr.length(); i++) {
                        if (oldStr.charAt(i) == newStr.charAt(i)) {
                            oldSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                            newSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                        } else {
                            stableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                        }
                    }

                    int countOldWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(oldStr)));
                    countOldLayout = new StaticLayout(oldSpannableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    countAnimationStableLayout = new StaticLayout(stableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    countAnimationInLayout = new StaticLayout(newSpannableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                } else {
                    countOldLayout = countLayout;
                }
            }
            countWidthOld = countWidth;
            countAnimationIncrement = count > currentCount;
            countAnimator.start();
        }
        if (count > 0) {
            countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(newStr)));
            countLayout = new StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        }

        currentCount = count;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int textColor = Theme.getColor(textColorKey);
        int circleColor = Theme.getColor(circleColorKey);
        if (this.textColor != textColor) {
            this.textColor = textColor;
            textPaint.setColor(textColor);
        }
        if (this.circleColor != circleColor) {
            this.circleColor = circleColor;
            circlePaint.setColor(circleColor);
        }
        if (countChangeProgress != 1f) {
            if (animationType == ANIMATION_TYPE_IN || animationType == ANIMATION_TYPE_OUT) {
                updateX(countWidth);
                float cx = countLeft + countWidth / 2f;
                float cy = getMeasuredHeight() / 2f;
                canvas.save();
                float progress = animationType == ANIMATION_TYPE_IN ? countChangeProgress : (1f - countChangeProgress);
                canvas.scale(progress, progress, cx, cy);
                drawInternal(canvas);
                canvas.restore();
            } else {
                float progressHalf = countChangeProgress * 2;
                if (progressHalf > 1f) {
                    progressHalf = 1f;
                }

                float countTop = (getMeasuredHeight() - AndroidUtilities.dp(23)) / 2f;
                float countWidth;
                if (this.countWidth == this.countWidthOld) {
                    countWidth = this.countWidth;
                } else {
                    countWidth = this.countWidth * progressHalf + this.countWidthOld * (1f - progressHalf);
                }
                updateX(countWidth);

                float scale = 1f;
                if (countAnimationIncrement) {
                    if (countChangeProgress <= 0.5f) {
                        scale += 0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(countChangeProgress * 2);
                    } else {
                        scale += 0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation((1f - (countChangeProgress - 0.5f) * 2));
                    }
                }

                rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                canvas.save();
                canvas.scale(scale, scale, rectF.centerX(), rectF.centerY());
                canvas.drawRoundRect(rectF, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, circlePaint);
                canvas.clipRect(rectF);

                boolean increment = reverseAnimation != countAnimationIncrement;
                if (countAnimationInLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, countTop + AndroidUtilities.dp(4) + (increment ? AndroidUtilities.dp(13) : -AndroidUtilities.dp(13)) * (1f - progressHalf));
                    textPaint.setAlpha((int) (255 * progressHalf));
                    countAnimationInLayout.draw(canvas);
                    canvas.restore();
                } else if (countLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, countTop + AndroidUtilities.dp(4) + (increment ? AndroidUtilities.dp(13) : -AndroidUtilities.dp(13)) * (1f - progressHalf));
                    textPaint.setAlpha((int) (255 * progressHalf));
                    countLayout.draw(canvas);
                    canvas.restore();
                }

                if (countOldLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, countTop + AndroidUtilities.dp(4) + (increment ? -AndroidUtilities.dp(13) : AndroidUtilities.dp(13)) * (progressHalf));
                    textPaint.setAlpha((int) (255 * (1f - progressHalf)));
                    countOldLayout.draw(canvas);
                    canvas.restore();
                }

                if (countAnimationStableLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
                    textPaint.setAlpha(255);
                    countAnimationStableLayout.draw(canvas);
                    canvas.restore();
                }
                textPaint.setAlpha(255);
                canvas.restore();
            }
        } else {
            drawInternal(canvas);
        }
    }

    private void updateX(float countWidth) {
        if (gravity == Gravity.RIGHT) {
            countLeft = getMeasuredWidth() - AndroidUtilities.dp(5.5f);
            if (horizontalPadding != 0) {
                countLeft -= Math.max(horizontalPadding + countWidth / 2f, countWidth);
            } else {
                countLeft -= countWidth;
            }
        } else if (gravity == Gravity.LEFT) {
            countLeft = AndroidUtilities.dp(5.5f);
        } else {
            countLeft = (int) ((getMeasuredWidth() - countWidth) / 2f);
        }
        x = countLeft - AndroidUtilities.dp(5.5f);
    }

    private void drawInternal(Canvas canvas) {
        float countTop = (getMeasuredHeight() - AndroidUtilities.dp(23)) / 2f;
        updateX(countWidth);
        rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
        canvas.drawRoundRect(rectF, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, circlePaint);
        if (countLayout != null) {
            canvas.save();
            canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
            countLayout.draw(canvas);
            canvas.restore();
        }
    }

    public void setColors(String textKey, String circleKey){
        this.textColorKey = textKey;
        this.circleColorKey = circleKey;
    }

    public void setGravity(int gravity) {
        this.gravity = gravity;
    }

    public void setReverse(boolean b) {
        reverseAnimation = b;
    }
}
