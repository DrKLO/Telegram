package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

import java.util.ArrayList;

public class TextRadioCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private RadioButton radioButton;
    private boolean needDivider;
    private boolean isMultiline;
    private int height = 50;
    private int animatedColorBackground;
    private float animationProgress;
    private Paint animationPaint;
    private float lastTouchX;
    private ObjectAnimator animator;
    private boolean drawCheckRipple;
    private boolean isRTL;
    private int padding;

    public static final Property<TextRadioCell, Float> ANIMATION_PROGRESS = new AnimationProperties.FloatProperty<TextRadioCell>("animationProgress") {
        @Override
        public void setValue(TextRadioCell object, float value) {
            object.setAnimationProgress(value);
            object.invalidate();
        }

        @Override
        public Float get(TextRadioCell object) {
            return object.animationProgress;
        }
    };

    public TextRadioCell(Context context) {
        this(context, 21);
    }

    public TextRadioCell(Context context, int padding) {
        this(context, padding, false);
    }

    public TextRadioCell(Context context, int padding, boolean dialog) {
        super(context);

        this.padding = padding;

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? padding : 64, 0, LocaleController.isRTL ? 64 : padding, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogIcon : Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? padding : 64, 36, LocaleController.isRTL ? 64 : padding, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
//        radioButton.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        radioButton.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
        addView(radioButton, LayoutHelper.createFrame(20, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));

        isRTL = LocaleController.isRTL;

        setClipChildren(false);
    }

    public void updateRTL() {
        if (isRTL == LocaleController.isRTL) {
            return;
        }
        isRTL = LocaleController.isRTL;
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams textViewLayout = (FrameLayout.LayoutParams) textView.getLayoutParams();
        textViewLayout.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        textViewLayout.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? padding : 64);
        textViewLayout.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 64 : padding);
        textView.setLayoutParams(textViewLayout);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        FrameLayout.LayoutParams valueTextViewLayout = (FrameLayout.LayoutParams) valueTextView.getLayoutParams();
        valueTextViewLayout.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        valueTextViewLayout.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? padding : 64);
        valueTextViewLayout.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 64 : padding);
        valueTextView.setLayoutParams(valueTextViewLayout);
        FrameLayout.LayoutParams radioButtonLayout = (FrameLayout.LayoutParams) radioButton.getLayoutParams();
        radioButtonLayout.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL;
        radioButton.setLayoutParams(radioButtonLayout);
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

    public void setTextAndCheck(String text, boolean checked, boolean divider) {
        textView.setText(text);
        isMultiline = false;
        radioButton.setChecked(checked, false);
        needDivider = divider;
        valueTextView.setVisibility(GONE);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.topMargin = 0;
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void setColors(String key, String switchKey, String switchKeyChecked, String switchThumb, String switchThumbChecked) {
        textView.setTextColor(Theme.getColor(key));
//        radioButton.setColors(switchKey, switchKeyChecked, switchThumb, switchThumbChecked);
        textView.setTag(key);
    }

    public void setTypeface(Typeface typeface) {
        textView.setTypeface(typeface);
    }

    public void setHeight(int value) {
        height = value;
    }

//    public void setDrawCheckRipple(boolean value) {
//        drawCheckRipple = value;
//    }

    @Override
    public void setPressed(boolean pressed) {
//        if (drawCheckRipple) {
//            checkBox.setDrawRipple(pressed);
//        }
        super.setPressed(pressed);
    }

    public void setTextAndValueAndCheck(String text, String value, boolean checked, boolean multiline, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        radioButton.setChecked(checked, false);
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
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(radioButton, "alpha", value ? 1.0f : 0.5f));
            if (valueTextView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueTextView, "alpha", value ? 1.0f : 0.5f));
            }
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            radioButton.setAlpha(value ? 1.0f : 0.5f);
            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.setAlpha(value ? 1.0f : 0.5f);
            }
        }
    }

    public void setChecked(boolean checked) {
        radioButton.setChecked(checked, true);
    }

    public boolean isChecked() {
        return radioButton.isChecked();
    }

    @Override
    public void setBackgroundColor(int color) {
        clearAnimation();
        animatedColorBackground = 0;
        super.setBackgroundColor(color);
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
//        radioButton.setOverrideColor(checked ? 1 : 2);
        animatedColorBackground = color;
        animationPaint.setColor(animatedColorBackground);
        animationProgress = 0.0f;
        animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 0.0f, 1.0f);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setBackgroundColor(animatedColorBackground);
                animatedColorBackground = 0;
                invalidate();
            }
        });
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animator.setDuration(240).start();
    }

    private void setAnimationProgress(float value) {
        animationProgress = value;
        float rad = Math.max(lastTouchX, getMeasuredWidth() - lastTouchX) + AndroidUtilities.dp(40);
        float cx = lastTouchX;
        int cy = getMeasuredHeight() / 2;
        float animatedRad = rad * animationProgress;
//        radioButton.setOverrideColorProgress(cx, cy, animatedRad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (animatedColorBackground != 0) {
            float rad = Math.max(lastTouchX, getMeasuredWidth() - lastTouchX) + AndroidUtilities.dp(40);
            float cx = lastTouchX;
            int cy = getMeasuredHeight() / 2;
            float animatedRad = rad * animationProgress;
            canvas.drawCircle(cx, cy, animatedRad, animationPaint);
        }
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(64) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(radioButton.isChecked());
        info.setContentDescription(radioButton.isChecked() ? LocaleController.getString("NotificationsOn", R.string.NotificationsOn) : LocaleController.getString("NotificationsOff", R.string.NotificationsOff));
        StringBuilder sb = new StringBuilder();
        sb.append(textView.getText());
        if (!TextUtils.isEmpty(valueTextView.getText())) {
            sb.append("\n");
            sb.append(valueTextView.getText());
        }
        info.setContentDescription(sb);
        info.setClassName("android.widget.RadioButton");
    }
}
