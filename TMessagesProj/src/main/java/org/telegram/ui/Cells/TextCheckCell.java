/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.Locale;

public class TextCheckCell extends FrameLayout {
    private boolean isAnimatingToThumbInsteadOfTouch;

    public int itemId;

    private TextView textView;
    private TextView valueTextView;
    private Switch checkBox;
    private boolean needDivider;
    private boolean isMultiline;
    private int height = 50;
    private int animatedColorBackground;
    private float animationProgress;
    private Paint animationPaint;
    private float lastTouchX;
    private ObjectAnimator animator;
    private boolean drawCheckRipple;
    private int padding;
    private Theme.ResourcesProvider resourcesProvider;
    ImageView imageView;
    private boolean isRTL;

    public static final Property<TextCheckCell, Float> ANIMATION_PROGRESS = new AnimationProperties.FloatProperty<TextCheckCell>("animationProgress") {
        @Override
        public void setValue(TextCheckCell object, float value) {
            object.setAnimationProgress(value);
            object.invalidate();
        }

        @Override
        public Float get(TextCheckCell object) {
            return object.animationProgress;
        }
    };

    public TextCheckCell(Context context) {
        this(context, 21);
    }

    public TextCheckCell(Context context, int padding) {
        this(context, padding, false, null);
    }

    public TextCheckCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 21, false, resourcesProvider);
    }

    public TextCheckCell(Context context, int padding, boolean dialog) {
        this(context, padding, dialog, null);
    }

    public TextCheckCell(Context context, int padding, boolean dialog, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        this.padding = padding;

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 70 : padding, 0, LocaleController.isRTL ? padding : 70, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogIcon : Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 70 : padding, 35, LocaleController.isRTL ? padding : 70, 0));

        checkBox = new Switch(context, resourcesProvider);
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(checkBox, LayoutHelper.createFrame(37, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));

        setClipChildren(false);

        isRTL = LocaleController.isRTL;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        checkBox.setEnabled(enabled);
    }

    public void setCheckBoxIcon(int icon) {
        checkBox.setIcon(icon);
    }

    public Switch getCheckBox() {
        return checkBox;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isMultiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(valueTextView.getVisibility() == VISIBLE ? 64 : height) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        lastTouchX = event.getX();
        return super.onTouchEvent(event);
    }

    public void setDivider(boolean divider) {
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndCheck(CharSequence text, boolean checked, boolean divider) {
        AvatarSpan.checkSpansParent(text, this);
        textView.setText(text);
        isMultiline = false;
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setChecked(checked, attached);
        needDivider = divider;
        valueTextView.setVisibility(GONE);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.topMargin = 0;
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void updateRTL() {
        if (isRTL == LocaleController.isRTL) {
            return;
        }
        isRTL = LocaleController.isRTL;

        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        removeView(textView);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 70 : padding, 0, LocaleController.isRTL ? padding : 70, 0));

        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        removeView(valueTextView);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 64 : padding, 36, LocaleController.isRTL ? padding : 64, 0));

        removeView(checkBox);
        addView(checkBox, LayoutHelper.createFrame(37, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
    }

    public void setColors(int key, int switchKey, int switchKeyChecked, int switchThumb, int switchThumbChecked) {
        textView.setTextColor(Theme.getColor(key, resourcesProvider));
        checkBox.setColors(switchKey, switchKeyChecked, switchThumb, switchThumbChecked);
        textView.setTag(key);
    }

    public void setTypeface(Typeface typeface) {
        textView.setTypeface(typeface);
    }

    public void setHeight(int value) {
        height = value;
    }

    public void setDrawCheckRipple(boolean value) {
        drawCheckRipple = value;
    }

    @Override
    public void setPressed(boolean pressed) {
        if (drawCheckRipple) {
            checkBox.setDrawRipple(pressed);
        }
        super.setPressed(pressed);
    }

    public void setTextAndValueAndCheck(String text, String value, boolean checked, boolean multiline, boolean divider) {
        AvatarSpan.checkSpansParent(text, this);
        textView.setText(text);
        valueTextView.setText(value);
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setChecked(checked, false);
        needDivider = divider;
        valueTextView.setVisibility(VISIBLE);
        isMultiline = multiline;
        if (multiline) {
            valueTextView.setLines(0);
            valueTextView.setMaxLines(0);
            valueTextView.setSingleLine(false);
            valueTextView.setEllipsize(null);
            valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(11));
        } else {
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
        }
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.topMargin = AndroidUtilities.dp(10);
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void setTextAndValue(String text, String value, boolean multiline, boolean divider) {
        AvatarSpan.checkSpansParent(text, this);
        textView.setText(text);
        valueTextView.setText(value);
        checkBox.setVisibility(View.GONE);
        needDivider = divider;
        valueTextView.setVisibility(VISIBLE);
        isMultiline = multiline;
        if (multiline) {
            valueTextView.setLines(0);
            valueTextView.setMaxLines(0);
            valueTextView.setSingleLine(false);
            valueTextView.setEllipsize(null);
            valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(11));
        } else {
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
        }
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.topMargin = AndroidUtilities.dp(10);
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        super.setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(checkBox, View.ALPHA, value ? 1.0f : 0.5f));
            if (valueTextView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueTextView, View.ALPHA, value ? 1.0f : 0.5f));
            }
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            checkBox.setAlpha(value ? 1.0f : 0.5f);
            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.setAlpha(value ? 1.0f : 0.5f);
            }
        }
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    @Override
    public void setBackgroundColor(int color) {
        if (animatedColorBackground != color) {
            clearAnimation();
            animatedColorBackground = 0;
            super.setBackgroundColor(color);
        }
    }

    public void setBackgroundColorAnimated(boolean checked, int color) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (animatedColorBackground != 0) {
            setBackgroundColor(animatedColorBackground);
        }
        if (animationPaint == null) {
            animationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        checkBox.setOverrideColor(checked ? 1 : 2);
        animatedColorBackground = color;
        animationPaint.setColor(animatedColorBackground);
        animationProgress = 0.0f;
        animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 0.0f, 1.0f);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatedColorBackground = 0;
                setBackgroundColor(color);
                invalidate();
            }
        });
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animator.setDuration(240).start();
    }

    private void setAnimationProgress(float value) {
        animationProgress = value;
        float tx = getLastTouchX();
        float rad = Math.max(tx, getMeasuredWidth() - tx) + AndroidUtilities.dp(40);
        float cx = tx;
        int cy = getMeasuredHeight() / 2;
        float animatedRad = rad * animationProgress;
        checkBox.setOverrideColorProgress(cx, cy, animatedRad);
    }

    public void setBackgroundColorAnimatedReverse(int color) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        int from = animatedColorBackground != 0 ? animatedColorBackground : getBackground() instanceof ColorDrawable ? ((ColorDrawable) getBackground()).getColor() : 0;
        if (animationPaint == null) animationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        animationPaint.setColor(from);

        setBackgroundColor(color);
        checkBox.setOverrideColor(1);
        animatedColorBackground = color;
        animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 1, 0).setDuration(240);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setBackgroundColor(color);
                animatedColorBackground = 0;
                invalidate();
            }
        });
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animator.start();
    }

    private float getLastTouchX() {
        return isAnimatingToThumbInsteadOfTouch ? (LocaleController.isRTL ? AndroidUtilities.dp(22) : getMeasuredWidth() - AndroidUtilities.dp(42)) : lastTouchX;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (animatedColorBackground != 0) {
            float tx = getLastTouchX();
            float rad = Math.max(tx, getMeasuredWidth() - tx) + AndroidUtilities.dp(40);
            float cx = tx;
            int cy = getMeasuredHeight() / 2;
            float animatedRad = rad * animationProgress;
            canvas.drawCircle(cx, cy, animatedRad, animationPaint);
        }
        if (needDivider) {
            Paint dividerPaint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : Theme.dividerPaint;
            if (dividerPaint != null) {
                if (imageView != null) {
                    canvas.drawLine(LocaleController.isRTL ? 0 : padding, getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? padding : 0), getMeasuredHeight() - 1, dividerPaint);
                } else {
                    canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, dividerPaint);
                }
            }
        }
    }

    public void setAnimatingToThumbInsteadOfTouch(boolean animatingToThumbInsteadOfTouch) {
        isAnimatingToThumbInsteadOfTouch = animatingToThumbInsteadOfTouch;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checkBox.isChecked());
        StringBuilder sb = new StringBuilder();
        sb.append(textView.getText());
        if (!TextUtils.isEmpty(valueTextView.getText())) {
            sb.append('\n');
            sb.append(valueTextView.getText());
        }
        info.setContentDescription(sb);
    }

    boolean attached;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }

    public void setColorfullIcon(int color, int resId) {
        if (imageView == null) {
            imageView = new RLottieImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(29, 29, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 19, 0, 19, 0));
            padding = AndroidUtilities.dp(65);
            ((MarginLayoutParams)textView.getLayoutParams()).leftMargin = LocaleController.isRTL ? 70 : padding;
            ((MarginLayoutParams)textView.getLayoutParams()).rightMargin = LocaleController.isRTL ? padding: 70;
        }
        imageView.setVisibility(VISIBLE);
        imageView.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        imageView.setImageResource(resId);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), color));
    }
}