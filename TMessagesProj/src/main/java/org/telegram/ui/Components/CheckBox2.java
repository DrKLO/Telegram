package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

public class CheckBox2 extends View {

    private CheckBoxBase checkBoxBase;

    public CheckBox2(Context context, int sz) {
        super(context);

        checkBoxBase = new CheckBoxBase(this, sz);
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

    public void setNum(int num) {
        checkBoxBase.setNum(num);
    }

    public boolean isChecked() {
        return checkBoxBase.isChecked();
    }

    public void setColor(String background, String background2, String check) {
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
        checkBoxBase.setDrawBackgroundAsArc(type);
    }

    public float getProgress() {
        return checkBoxBase.getProgress();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkBoxBase.onAttachedToWindow();
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
        checkBoxBase.draw(canvas);
    }
}
