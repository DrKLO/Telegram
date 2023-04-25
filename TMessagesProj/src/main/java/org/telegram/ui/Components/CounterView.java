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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CounterView extends View {

    public CounterDrawable counterDrawable;
    private final Theme.ResourcesProvider resourcesProvider;

    public CounterView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setVisibility(View.GONE);
        counterDrawable = new CounterDrawable(this, true, resourcesProvider);
        counterDrawable.updateVisibility = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        counterDrawable.setSize(getMeasuredHeight(), getMeasuredWidth());
    }


    @Override
    protected void onDraw(Canvas canvas) {
        counterDrawable.draw(canvas);
    }


    public void setColors(int textKey, int circleKey) {
        counterDrawable.textColorKey = textKey;
        counterDrawable.circleColorKey = circleKey;
    }

    public void setGravity(int gravity) {
        counterDrawable.gravity = gravity;
    }

    public void setReverse(boolean b) {
        counterDrawable.reverseAnimation = b;
    }

    public void setCount(int count, boolean animated) {
        counterDrawable.setCount(count, animated);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public static class CounterDrawable {

        private final static int ANIMATION_TYPE_IN = 0;
        private final static int ANIMATION_TYPE_OUT = 1;
        private final static int ANIMATION_TYPE_REPLACE = 2;
        public boolean shortFormat;
        public float circleScale = 1f;

        int animationType = -1;

        public Paint circlePaint;
        public TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        public RectF rectF = new RectF();
        public boolean addServiceGradient;

        int currentCount;
        private boolean countAnimationIncrement;
        private ValueAnimator countAnimator;
        public float countChangeProgress = 1f;
        private StaticLayout countLayout;
        private StaticLayout countOldLayout;
        private StaticLayout countAnimationStableLayout;
        private StaticLayout countAnimationInLayout;

        private int countWidthOld;
        private int countWidth;

        private int circleColor;
        private int textColor;
        private int textColorKey = Theme.key_chat_goDownButtonCounter;
        private int circleColorKey = Theme.key_chat_goDownButtonCounterBackground;

        int lastH;
        int width;
        public int gravity = Gravity.CENTER;
        float countLeft;
        float x;
        public float radius = 11.5f;

        private boolean reverseAnimation;
        public float horizontalPadding;
        private boolean drawBackground = true;

        public boolean updateVisibility;

        private View parent;

        public final static int TYPE_DEFAULT = 0;
        public final static int TYPE_CHAT_PULLING_DOWN = 1;
        public final static int TYPE_CHAT_REACTIONS = 2;

        int type = TYPE_DEFAULT;
        private final Theme.ResourcesProvider resourcesProvider;

        public CounterDrawable(View parent, boolean drawBackground, Theme.ResourcesProvider resourcesProvider) {
            this.parent = parent;
            this.resourcesProvider = resourcesProvider;
            this.drawBackground = drawBackground;
            if (drawBackground) {
                circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                circlePaint.setColor(Color.BLACK);
            }
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textPaint.setTextSize(AndroidUtilities.dp(13));
        }

        public void setSize(int h, int w) {
            if (h != lastH) {
                int count = currentCount;
                currentCount = -1;
                setCount(count, animationType == ANIMATION_TYPE_IN);
                lastH = h;
            }
            width = w;
        }


        private void drawInternal(Canvas canvas) {
            float size = radius * 2;
            float countTop = (lastH - AndroidUtilities.dp(size)) / 2f;
            updateX(countWidth);
            rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(radius - 0.5f), countTop + AndroidUtilities.dp(size));
            if (circlePaint != null && drawBackground) {
                boolean needRestore = false;
                if (circleScale != 1f) {
                    canvas.save();
                    canvas.scale(circleScale, circleScale, rectF.centerX(), rectF.centerY());
                    needRestore = true;
                }
                canvas.drawRoundRect(rectF, radius * AndroidUtilities.density, radius * AndroidUtilities.density, circlePaint);
                if (addServiceGradient && Theme.hasGradientService()) {
                    canvas.drawRoundRect(rectF, radius * AndroidUtilities.density, radius * AndroidUtilities.density, Theme.chat_actionBackgroundGradientDarkenPaint);
                }
                if (needRestore) {
                    canvas.restore();
                }
            }
            if (countLayout != null) {
                canvas.save();
                canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
                countLayout.draw(canvas);
                canvas.restore();
            }
        }

        public void setCount(int count, boolean animated) {
            if (count == currentCount) {
                return;
            }
            if (countAnimator != null) {
                countAnimator.cancel();
            }
            if (count > 0 && updateVisibility && parent != null) {
                parent.setVisibility(View.VISIBLE);
            }
            if (Math.abs(count - currentCount) > 99) {
                animated = false;
            }
            if (!animated) {
                currentCount = count;
                if (count == 0) {
                    if (updateVisibility && parent != null) {
                        parent.setVisibility(View.GONE);
                    }
                    return;
                }
                String newStr = getStringOfCCount(count);
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(textPaint.measureText(newStr)));
                countLayout = new StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                if (parent != null) {
                    parent.invalidate();
                }
                return;
            }
            String newStr = getStringOfCCount(count);

            if (animated) {
                if (countAnimator != null) {
                    countAnimator.cancel();
                }
                countChangeProgress = 0f;
                countAnimator = ValueAnimator.ofFloat(0, 1f);
                countAnimator.addUpdateListener(valueAnimator -> {
                    countChangeProgress = (float) valueAnimator.getAnimatedValue();
                    if (parent != null) {
                        parent.invalidate();
                    }
                });
                countAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        countChangeProgress = 1f;
                        countOldLayout = null;
                        countAnimationStableLayout = null;
                        countAnimationInLayout = null;
                        if (parent != null) {
                            if (currentCount == 0 && updateVisibility) {
                                parent.setVisibility(View.GONE);
                            }
                            parent.invalidate();
                        }
                        animationType = -1;
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
                    String oldStr = getStringOfCCount(currentCount);

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
            if (parent != null) {
                parent.invalidate();
            }
        }

        private String getStringOfCCount(int count) {
            if (shortFormat) {
                return AndroidUtilities.formatWholeNumber(count, 0);
            }
            return String.valueOf(count);
        }

        public void draw(Canvas canvas) {
            if (type != TYPE_CHAT_PULLING_DOWN && type != TYPE_CHAT_REACTIONS) {
                int textColor = getThemedColor(textColorKey);
                int circleColor = getThemedColor(circleColorKey);
                if (this.textColor != textColor) {
                    this.textColor = textColor;
                    textPaint.setColor(textColor);
                }
                if (circlePaint != null && this.circleColor != circleColor) {
                    this.circleColor = circleColor;
                    circlePaint.setColor(circleColor);
                }
            }
            if (countChangeProgress != 1f) {
                if (animationType == ANIMATION_TYPE_IN || animationType == ANIMATION_TYPE_OUT) {
                    updateX(countWidth);
                    float cx = countLeft + countWidth / 2f;
                    float cy = lastH / 2f;
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

                    float countTop = (lastH - AndroidUtilities.dp(radius * 2)) / 2f;
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

                    rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(radius - 0.5f), countTop + AndroidUtilities.dp(radius * 2));
                    canvas.save();
                    canvas.scale(scale, scale, rectF.centerX(), rectF.centerY());
                    boolean needRestore = false;
                    if (circleScale != 1f) {
                        needRestore = true;
                        canvas.save();
                        canvas.scale(circleScale, circleScale, rectF.centerX(), rectF.centerY());
                    }
                    if (drawBackground && circlePaint != null) {
                        canvas.drawRoundRect(rectF, radius * AndroidUtilities.density, radius * AndroidUtilities.density, circlePaint);
                        if (addServiceGradient && Theme.hasGradientService()) {
                            canvas.drawRoundRect(rectF, radius * AndroidUtilities.density, radius * AndroidUtilities.density, Theme.chat_actionBackgroundGradientDarkenPaint);
                        }
                    }
                    if (needRestore) {
                        canvas.restore();
                    }
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

        public void updateBackgroundRect() {
            if (countChangeProgress != 1f) {
                if (animationType == ANIMATION_TYPE_IN || animationType == ANIMATION_TYPE_OUT) {
                    updateX(countWidth);
                    float countTop = (lastH - AndroidUtilities.dp(radius * 2)) / 2f;
                    rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                } else {
                    float progressHalf = countChangeProgress * 2;
                    if (progressHalf > 1f) {
                        progressHalf = 1f;
                    }
                    float countTop = (lastH - AndroidUtilities.dp(radius * 2)) / 2f;
                    float countWidth;
                    if (this.countWidth == this.countWidthOld) {
                        countWidth = this.countWidth;
                    } else {
                        countWidth = this.countWidth * progressHalf + this.countWidthOld * (1f - progressHalf);
                    }
                    updateX(countWidth);
                    rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
                }
            } else {
                updateX(countWidth);
                float countTop = (lastH - AndroidUtilities.dp(radius * 2)) / 2f;
                rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
            }
        }

        private void updateX(float countWidth) {
            float padding = drawBackground ? AndroidUtilities.dp(5.5f) : 0f;
            if (gravity == Gravity.RIGHT) {
                countLeft = width - padding;
                if (horizontalPadding != 0) {
                    countLeft -= Math.max(horizontalPadding + countWidth / 2f, countWidth);
                } else {
                    countLeft -= countWidth;
                }
            } else if (gravity == Gravity.LEFT) {
                countLeft = padding;
            } else {
                countLeft = (int) ((width - countWidth) / 2f);
            }
            x = countLeft - padding;
        }

        public float getCenterX() {
            updateX(countWidth);
            return countLeft + countWidth / 2f;
        }

        public void setType(int type) {
            this.type = type;
        }

        public void setParent(View parent) {
            this.parent = parent;
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        public int getWidth() {
            return currentCount == 0 ? 0 : (countWidth + AndroidUtilities.dp(radius - 0.5f));
        }
    }

    public float getEnterProgress() {
        if (counterDrawable.countChangeProgress != 1f && (counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_IN || counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_OUT)) {
            if (counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_IN) {
                return counterDrawable.countChangeProgress;
            } else {
                return 1f - counterDrawable.countChangeProgress;
            }
        } else {
            return counterDrawable.currentCount == 0 ? 0 : 1f;
        }
    }

    public boolean isInOutAnimation() {
        return counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_IN || counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_OUT;
    }

}
