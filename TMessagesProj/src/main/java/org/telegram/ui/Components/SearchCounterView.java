package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

public class SearchCounterView extends View {

    private final static int ANIMATION_TYPE_REPLACE = 2;

    int animationType = -1;

    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    RectF rectF = new RectF();

    int currentCount;
    private boolean countAnimationIncrement;
    private ValueAnimator countAnimator;
    private float countChangeProgress = 1f;
    private StaticLayout countLayout;
    private StaticLayout countOldLayout;
    private StaticLayout countAnimationStableLayout;
    private StaticLayout countAnimationStableLayout2;
    private StaticLayout countAnimationInLayout;

    private int countWidthOld;
    private int countWidth;

    private int textColor;
    private int textColorKey = Theme.key_chat_searchPanelText;

    int lastH;
    int gravity = Gravity.CENTER;
    float countLeft;
    float x;

    public float horizontalPadding;

    String currentString;
    private final Theme.ResourcesProvider resourcesProvider;

    public SearchCounterView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(AndroidUtilities.dp(15));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() != lastH) {
            int count = currentCount;
            String str = currentString;
            currentString = null;
            setCount(str, count, false);
            lastH = getMeasuredHeight();
        }
    }

    float dx = 0;

    public void setCount(String newStr, int count, boolean animated) {
        if (currentString != null && currentString.equals(newStr)) {
            return;
        }
        if (countAnimator != null) {
            countAnimator.cancel();
        }
        if (currentCount == 0 || count <= 0 || newStr == null || LocaleController.isRTL || TextUtils.isEmpty(newStr)) {
            animated = false;
        }
        
        if (animated && newStr != null && !newStr.contains("**")) {
            animated = false;
        }

        if (!animated) {
            if (newStr != null) {
                newStr = newStr.replaceAll("\\*\\*", "");
            }
            currentCount = count;
            if (newStr == null) {
                countWidth = 0;
                countLayout = null;
            } else {
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(newStr)));
                countLayout = new StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            }
            invalidate();
        }

        dx = 0;
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
                    invalidate();
                }
            });

            animationType = ANIMATION_TYPE_REPLACE;
            countAnimator.setDuration(200);
            countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);

            if (countLayout != null) {
                String oldStr = currentString;

                int countStartIndex = newStr.indexOf("**");
                if (countStartIndex >= 0) {
                    newStr = newStr.replaceAll("\\*\\*", "");
                } else {
                    countStartIndex = 0;
                }

                SpannableStringBuilder oldSpannableStr = new SpannableStringBuilder(oldStr);
                SpannableStringBuilder newSpannableStr = new SpannableStringBuilder(newStr);
                SpannableStringBuilder stableStr = new SpannableStringBuilder(newStr);

                boolean replaceAllDigits = Integer.toString(currentCount).length() != Integer.toString(count).length();
                boolean newEndReached = false;
                boolean oldEndReached = false;
                int n = Math.min(oldStr.length(), newStr.length());
                int cutIndexNew = 0;
                int cutIndexOld = 0;
                if (countStartIndex > 0) {
                    oldSpannableStr.setSpan(new EmptyStubSpan(), 0, Math.min(oldSpannableStr.length(), countStartIndex), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                    newSpannableStr.setSpan(new EmptyStubSpan(), 0, Math.min(newSpannableStr.length(), countStartIndex), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                    stableStr.setSpan(new EmptyStubSpan(), 0, Math.min(stableStr.length(), countStartIndex), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                for (int i = countStartIndex; i < n; i++) {
                    if (!newEndReached && !oldEndReached) {
                        if (replaceAllDigits) {
                            stableStr.setSpan(new EmptyStubSpan(), i, i + 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else if (oldStr.charAt(i) == newStr.charAt(i)) {
                            oldSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                            newSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            stableStr.setSpan(new EmptyStubSpan(), i, i + 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    if (!Character.isDigit(newStr.charAt(i))) {
                        newSpannableStr.setSpan(new EmptyStubSpan(), i, newStr.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                        newEndReached = true;
                        cutIndexNew = i;
                    }

                    if (!Character.isDigit(oldStr.charAt(i))) {
                        oldSpannableStr.setSpan(new EmptyStubSpan(), i, oldStr.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                        oldEndReached = true;
                        cutIndexOld = i;
                    }
                }

                int countOldWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(oldStr)));
                int countNewWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(newStr)));
                countOldLayout = new StaticLayout(oldSpannableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                countAnimationStableLayout = new StaticLayout(stableStr, textPaint, countNewWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                countAnimationInLayout = new StaticLayout(newSpannableStr, textPaint, countNewWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

                if (countStartIndex > 0) {
                    SpannableStringBuilder stableString2 = new SpannableStringBuilder(newStr);
                    stableString2.setSpan(new EmptyStubSpan(), countStartIndex, newStr.length(), 0);
                    countAnimationStableLayout2 = new StaticLayout(stableString2, textPaint, countNewWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                } else {
                    countAnimationStableLayout2 = null;
                }

                dx = countOldLayout.getPrimaryHorizontal(cutIndexOld) - countAnimationStableLayout.getPrimaryHorizontal(cutIndexNew);
            }
            countWidthOld = countWidth;
            countAnimationIncrement = count < currentCount;
            countAnimator.start();
        }
        if (count > 0) {
            countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(newStr)));
            countLayout = new StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        }

        currentCount = count;
        invalidate();
        currentString = newStr;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int textColor = Theme.getColor(textColorKey, resourcesProvider);

        if (this.textColor != textColor) {
            this.textColor = textColor;
            textPaint.setColor(textColor);
        }

        if (countChangeProgress != 1f) {

            float countTop = (getMeasuredHeight() - AndroidUtilities.dp(23)) / 2f;
            float countWidth;
            if (this.countWidth == this.countWidthOld) {
                countWidth = this.countWidth;
            } else {
                countWidth = this.countWidth * countChangeProgress + this.countWidthOld * (1f - countChangeProgress);
            }
            updateX(countWidth);

            rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));

            boolean increment = countAnimationIncrement;
            if (countAnimationInLayout != null) {
                canvas.save();
                canvas.translate(countLeft, countTop + AndroidUtilities.dp(2) + (increment ? AndroidUtilities.dp(13) : -AndroidUtilities.dp(13)) * (1f - countChangeProgress));
                textPaint.setAlpha((int) (255 * countChangeProgress));
                countAnimationInLayout.draw(canvas);
                canvas.restore();
            } else if (countLayout != null) {
                canvas.save();
                canvas.translate(countLeft, countTop + AndroidUtilities.dp(2) + (increment ? AndroidUtilities.dp(13) : -AndroidUtilities.dp(13)) * (1f - countChangeProgress));
                textPaint.setAlpha((int) (255 * countChangeProgress));
                countLayout.draw(canvas);
                canvas.restore();
            }

            if (countOldLayout != null) {
                canvas.save();
                canvas.translate(countLeft, countTop + AndroidUtilities.dp(2) + (increment ? -AndroidUtilities.dp(13) : AndroidUtilities.dp(13)) * (countChangeProgress));
                textPaint.setAlpha((int) (255 * (1f - countChangeProgress)));
                countOldLayout.draw(canvas);
                canvas.restore();
            }

            if (countAnimationStableLayout != null) {
                canvas.save();
                canvas.translate(countLeft + dx * (1f - countChangeProgress), countTop + AndroidUtilities.dp(2));
                textPaint.setAlpha(255);
                countAnimationStableLayout.draw(canvas);
                canvas.restore();
            }

            if (countAnimationStableLayout2 != null) {
                canvas.save();
                canvas.translate(countLeft, countTop + AndroidUtilities.dp(2));
                textPaint.setAlpha(255);
                countAnimationStableLayout2.draw(canvas);
                canvas.restore();
            }
            textPaint.setAlpha(255);
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
        if (countLayout != null) {
            canvas.save();
            canvas.translate(countLeft, countTop + AndroidUtilities.dp(2));
            countLayout.draw(canvas);
            canvas.restore();
        }
    }

    public void setGravity(int gravity) {
        this.gravity = gravity;
    }

}
