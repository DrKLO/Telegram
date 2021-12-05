package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class VoIPToggleButton extends FrameLayout {

    Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean drawBackground = true;
    private boolean animateBackground;
    Drawable[] icon = new Drawable[2];

    FrameLayout textLayoutContainer;
    TextView[] textView = new TextView[2];

    int backgroundColor;
    int animateToBackgroundColor;

    float replaceProgress;
    ValueAnimator replaceAnimator;

    int currentIconRes;
    int currentIconColor;
    int currentBackgroundColor;
    String currentText;
    public int animationDelay;

    private boolean iconChangeColor;
    private int replaceColorFrom;

    private Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float crossProgress;
    private boolean drawCross;

    private float crossOffset;

    Drawable rippleDrawable;

    private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean checkableForAccessibility;
    private boolean checkable;
    private boolean checked;
    private float checkedProgress;
    private int backgroundCheck1;
    private int backgroundCheck2;

    private float radius;
    private ValueAnimator checkAnimator;

    private RLottieImageView lottieImageView;

    public VoIPToggleButton(@NonNull Context context) {
        this(context, 52f);
    }
    public VoIPToggleButton(@NonNull Context context, float radius) {
        super(context);
        this.radius = radius;
        setWillNotDraw(false);

        textLayoutContainer = new FrameLayout(context);
        addView(textLayoutContainer);

        for (int i = 0; i < 2; i++) {
            TextView textView = new TextView(context);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTextColor(Color.WHITE);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            textLayoutContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, radius + 4, 0, 0));
            this.textView[i] = textView;
        }
        textView[1].setVisibility(View.GONE);


        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        xRefPaint.setStrokeWidth(AndroidUtilities.dp(3));

        crossPaint.setStrokeWidth(AndroidUtilities.dp(2));
        crossPaint.setStrokeCap(Paint.Cap.ROUND);

        bitmapPaint.setFilterBitmap(true);
    }

    public void setTextSize(int size) {
        for (int i = 0; i < 2; i++) {
            textView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        }
    }

    public void setDrawBackground(boolean value) {
        drawBackground = value;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        if (animateBackground && replaceProgress != 0) {
            circlePaint.setColor(ColorUtils.blendARGB(backgroundColor, animateToBackgroundColor, replaceProgress));
        } else {
            circlePaint.setColor(backgroundColor);
        }

        float cx = getWidth() / 2f;
        float cy = AndroidUtilities.dp(radius) / 2f;
        float radius = AndroidUtilities.dp(this.radius) / 2f;
        if (drawBackground) {
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(this.radius) / 2f, circlePaint);
        }
        if (rippleDrawable == null) {
            rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(this.radius), 0, Color.BLACK);
            rippleDrawable.setCallback(this);
        }
        rippleDrawable.setBounds((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius));
        rippleDrawable.draw(canvas);

        if (currentIconRes != 0) {
            if (drawCross || crossProgress != 0) {
                if (iconChangeColor) {
                    int color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress);
                    icon[0].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    crossPaint.setColor(color);
                }
                icon[0].setAlpha(255);

                if (replaceProgress != 0 && iconChangeColor) {
                    int color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress);
                    icon[0].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    crossPaint.setColor(color);
                }
                icon[0].setAlpha(255);

                if (drawCross && crossProgress < 1f) {
                    crossProgress += 0.08f;
                    if (crossProgress > 1f) {
                        crossProgress = 1f;
                    } else {
                        invalidate();
                    }
                } else if (!drawCross) {
                    crossProgress -= 0.08f;
                    if (crossProgress < 0) {
                        crossProgress = 0;
                    } else {
                        invalidate();
                    }
                }
                if (crossProgress > 0) {
                    int left = (int) (cx - icon[0].getIntrinsicWidth() / 2f);
                    int top = (int) (cy - icon[0].getIntrinsicHeight() / 2);

                    float startX = left + AndroidUtilities.dpf2(8) + crossOffset;
                    float startY = top + AndroidUtilities.dpf2(8);

                    float endX = startX - AndroidUtilities.dp(1) + AndroidUtilities.dp(17) * CubicBezierInterpolator.DEFAULT.getInterpolation(crossProgress);
                    float endY = startY + AndroidUtilities.dp(17) * CubicBezierInterpolator.DEFAULT.getInterpolation(crossProgress);

                    canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
                    icon[0].setBounds(
                            (int) (cx - icon[0].getIntrinsicWidth() / 2f), (int) (cy - icon[0].getIntrinsicHeight() / 2),
                            (int) (cx + icon[0].getIntrinsicWidth() / 2), (int) (cy + icon[0].getIntrinsicHeight() / 2)
                    );
                    icon[0].draw(canvas);

                    canvas.drawLine(startX, startY - AndroidUtilities.dp(2f), endX, endY - AndroidUtilities.dp(2f), xRefPaint);
                    canvas.drawLine(startX, startY, endX, endY, crossPaint);
                    canvas.restore();
                } else {
                    icon[0].setBounds(
                            (int) (cx - icon[0].getIntrinsicWidth() / 2f), (int) (cy - icon[0].getIntrinsicHeight() / 2),
                            (int) (cx + icon[0].getIntrinsicWidth() / 2), (int) (cy + icon[0].getIntrinsicHeight() / 2)
                    );
                    icon[0].draw(canvas);
                }
            } else {
                for (int i = 0; i < ((replaceProgress == 0 || iconChangeColor) ? 1 : 2); i++) {
                    if (icon[i] != null) {
                        canvas.save();
                        if (replaceProgress != 0 && !iconChangeColor && icon[0] != null && icon[1] != null) {
                            float p = i == 0 ? 1f - replaceProgress : replaceProgress;
                            canvas.scale(p, p, cx, cy);
                            icon[i].setAlpha((int) (255 * p));
                        } else {
                            if (iconChangeColor) {
                                int color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress);
                                icon[i].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                                crossPaint.setColor(color);
                            }
                            icon[i].setAlpha(255);
                        }
                        icon[i].setBounds(
                                (int) (cx - icon[i].getIntrinsicWidth() / 2f), (int) (cy - icon[i].getIntrinsicHeight() / 2),
                                (int) (cx + icon[i].getIntrinsicWidth() / 2), (int) (cy + icon[i].getIntrinsicHeight() / 2)
                        );
                        icon[i].draw(canvas);

                        canvas.restore();
                    }
                }
            }
        }
    }

    public void setBackgroundColor(int backgroundColor, int backgroundColorChecked) {
        backgroundCheck1 = backgroundColor;
        backgroundCheck2 = backgroundColorChecked;
        this.backgroundColor = ColorUtils.blendARGB(backgroundColor, backgroundColorChecked, checkedProgress);
        invalidate();
    }

    public void setData(int iconRes, int iconColor, int backgroundColor, String text, boolean cross, boolean animated) {
        setData(iconRes, iconColor, backgroundColor, 1.0f, true, text, cross, animated);
    }

    public void setEnabled(boolean enabled, boolean animated) {
        super.setEnabled(enabled);
        if (animated) {
            animate().alpha(enabled ? 1.0f : 0.5f).setDuration(180).start();
        } else {
            clearAnimation();
            setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    public void setData(int iconRes, int iconColor, int backgroundColor, float selectorAlpha, boolean recreateRipple, String text, boolean cross, boolean animated) {
        if (getVisibility() != View.VISIBLE) {
            animated = false;
            setVisibility(View.VISIBLE);
        }

        if (currentIconRes == iconRes && currentIconColor == iconColor && (checkable || currentBackgroundColor == backgroundColor) && (currentText != null && currentText.equals(text)) && cross == this.drawCross) {
            return;
        }

        if (rippleDrawable == null || recreateRipple) {
            if (Color.alpha(backgroundColor) == 255 && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.5) {
                rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.1f * selectorAlpha)));
                rippleDrawable.setCallback(this);
            } else {
                rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f * selectorAlpha)));
                rippleDrawable.setCallback(this);
            }
        }

        if (replaceAnimator != null) {
            replaceAnimator.cancel();
        }
        animateBackground = currentBackgroundColor != backgroundColor;

        iconChangeColor = currentIconRes == iconRes;
        if (iconChangeColor) {
            replaceColorFrom = currentIconColor;
        }
        currentIconRes = iconRes;
        currentIconColor = iconColor;
        currentBackgroundColor = backgroundColor;
        currentText = text;
        drawCross = cross;

        if (!animated) {
            if (iconRes != 0) {
                icon[0] = ContextCompat.getDrawable(getContext(), iconRes).mutate();
                icon[0].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
            crossPaint.setColor(iconColor);
            if (!checkable) {
                this.backgroundColor = backgroundColor;
            }
            textView[0].setText(text);
            crossProgress = drawCross ? 1f : 0;
            iconChangeColor = false;
            replaceProgress = 0f;
            invalidate();
        } else {
            if (!iconChangeColor && iconRes != 0) {
                icon[1] = ContextCompat.getDrawable(getContext(), iconRes).mutate();
                icon[1].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
            if (!checkable) {
                this.animateToBackgroundColor = backgroundColor;
            }

            boolean animateText = !textView[0].getText().toString().equals(text);

            if (!animateText) {
                textView[0].setText(text);
            } else {
                textView[1].setText(text);
                textView[1].setVisibility(View.VISIBLE);
                textView[1].setAlpha(0);
                textView[1].setScaleX(0);
                textView[1].setScaleY(0);
            }
            replaceAnimator = ValueAnimator.ofFloat(0, 1f);
            replaceAnimator.addUpdateListener(valueAnimator -> {
                replaceProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();

                if (animateText) {
                    textView[0].setAlpha(1f - replaceProgress);
                    textView[0].setScaleX(1f - replaceProgress);
                    textView[0].setScaleY(1f - replaceProgress);

                    textView[1].setAlpha(replaceProgress);
                    textView[1].setScaleX(replaceProgress);
                    textView[1].setScaleY(replaceProgress);
                }
            });
            replaceAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    replaceAnimator = null;
                    if (animateText) {
                        TextView tv = textView[0];
                        textView[0] = textView[1];
                        textView[1] = tv;
                        textView[1].setVisibility(View.GONE);
                    }

                    if (!iconChangeColor && icon[1] != null) {
                        icon[0] = icon[1];
                        icon[1] = null;
                    }
                    iconChangeColor = false;
                    if (!checkable) {
                        VoIPToggleButton.this.backgroundColor = animateToBackgroundColor;
                    }
                    replaceProgress = 0f;
                    invalidate();
                }
            });
            replaceAnimator.setDuration(150).start();
            invalidate();
        }
    }

    public void setCrossOffset(float crossOffset) {
        this.crossOffset = crossOffset;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (rippleDrawable != null) {
            rippleDrawable.setState(getDrawableState());
        }
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return rippleDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (rippleDrawable != null) {
            rippleDrawable.jumpToCurrentState();
        }
    }

    public void setCheckableForAccessibility(boolean checkableForAccessibility) {
        this.checkableForAccessibility = checkableForAccessibility;
    }

    //animate background if true
    public void setCheckable(boolean checkable) {
        this.checkable = checkable;
    }

    public void setChecked(boolean value, boolean animated) {
        if (checked == value) {
            return;
        }
        checked = value;
        if (checkable) {
            if (animated) {
                if (checkAnimator != null) {
                    checkAnimator.removeAllListeners();
                    checkAnimator.cancel();
                }
                checkAnimator = ValueAnimator.ofFloat(checkedProgress, checked ? 1f : 0);
                checkAnimator.addUpdateListener(valueAnimator -> {
                    checkedProgress = (float) valueAnimator.getAnimatedValue();
                    setBackgroundColor(backgroundCheck1, backgroundCheck2);
                });
                checkAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        checkedProgress = checked ? 1f : 0;
                        setBackgroundColor(backgroundCheck1, backgroundCheck2);
                    }
                });
                checkAnimator.setDuration(150);
                checkAnimator.start();
            } else {
                checkedProgress = checked ? 1f : 0;
                setBackgroundColor(backgroundCheck1, backgroundCheck2);
            }
        }
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(currentText);
        if (checkable || checkableForAccessibility) {
            info.setClassName(ToggleButton.class.getName());
            info.setCheckable(true);
            info.setChecked(checked);
        } else {
            info.setClassName(Button.class.getName());
        }
    }

    public void shakeView() {
        AndroidUtilities.shakeView(textView[0], 2, 0);
        AndroidUtilities.shakeView(textView[1], 2, 0);
    }

    public void showText(boolean show, boolean animated) {
        if (animated) {
            float a = show ? 1f : 0;
            if (textLayoutContainer.getAlpha() != a) {
                textLayoutContainer.animate().alpha(a).start();
            }
        } else {
            textLayoutContainer.animate().cancel();
            textLayoutContainer.setAlpha(show ? 1f : 0);
        }
    }
}