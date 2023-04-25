package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GenericProvider;
import org.telegram.ui.ActionBar.Theme;

public class CheckBox2 extends View {

    private CheckBoxBase checkBoxBase;
    Drawable iconDrawable;
    int currentIcon;

    public CheckBox2(Context context, int sz) {
        this(context, sz, null);
    }

    public CheckBox2(Context context, int sz, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        checkBoxBase = new CheckBoxBase(this, sz, resourcesProvider);
    }

    public void setCirclePaintProvider(GenericProvider<Void, Paint> circlePaintProvider) {
        checkBoxBase.setCirclePaintProvider(circlePaintProvider);
    }

    public void setProgressDelegate(CheckBoxBase.ProgressDelegate delegate) {
        checkBoxBase.setProgressDelegate(delegate);
    }

    public void setChecked(int num, boolean checked, boolean animated) {
        checkBoxBase.setChecked(num, checked, animated);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBoxBase.setChecked(checked, animated);
    }

    public CheckBoxBase getCheckBoxBase() {
        return checkBoxBase;
    }

    public void setNum(int num) {
        checkBoxBase.setNum(num);
    }

    public boolean isChecked() {
        return checkBoxBase.isChecked();
    }

    public void setColor(int background, int background2, int check) {
        checkBoxBase.setColor(background, background2, check);
    }

    @Override
    public void setEnabled(boolean enabled) {
        checkBoxBase.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    public void setDrawUnchecked(boolean value) {
        checkBoxBase.setDrawUnchecked(value);
    }

    public void setDrawBackgroundAsArc(int type) {
        checkBoxBase.setBackgroundType(type);
    }

    public float getProgress() {
        return checkBoxBase.getProgress();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkBoxBase.onAttachedToWindow();
    }

    public void setDuration(long duration) {
        checkBoxBase.animationDuration = duration;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        checkBoxBase.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkBoxBase.setBounds(0, 0, right - left, bottom - top);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (iconDrawable != null) {
            int cx = getMeasuredWidth() >> 1;
            int cy = getMeasuredHeight() >> 1;
            iconDrawable.setBounds(cx - iconDrawable.getIntrinsicWidth() / 2, cy - iconDrawable.getIntrinsicHeight() / 2, cx + iconDrawable.getIntrinsicWidth() / 2, cy + iconDrawable.getIntrinsicHeight() / 2);
            iconDrawable.draw(canvas);
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1.2f));
            paint.setColor(Theme.getColor(Theme.key_switch2Track));
            canvas.drawCircle(cx, cy, cx - AndroidUtilities.dp(1.5f), paint);
        } else {
            checkBoxBase.draw(canvas);
        }
    }

    public void setForbidden(boolean forbidden) {
        checkBoxBase.setForbidden(forbidden);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(isChecked());
    }

    public void setIcon(int icon) {
        if (icon != currentIcon) {
            currentIcon = icon;
            if (icon == 0) {
                iconDrawable = null;
            } else {
                iconDrawable = ContextCompat.getDrawable(getContext(), icon).mutate();
                iconDrawable.setColorFilter(Theme.getColor(Theme.key_switch2Track), PorterDuff.Mode.MULTIPLY);
            }
        }
    }

    public boolean hasIcon() {
        return iconDrawable != null;
    }
}
