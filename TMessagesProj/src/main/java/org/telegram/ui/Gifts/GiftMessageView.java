package org.telegram.ui.Gifts;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.tgnet.TLObject;

public class GiftMessageView extends View {

    private final GiftMessageDrawable drawable = new GiftMessageDrawable();

    public GiftMessageView(Context context) {
        super(context);
        drawable.setParentView(this);
    }

    public void setMessage(CharSequence text) {
        drawable.setMessage(text);
        requestLayout();
    }

    public void setUser(TLObject user) {
        drawable.setUser(user);
        invalidate();
    }

    public GiftMessageDrawable getDrawable() {
        return drawable;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int maxWidth = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        drawable.measure(maxWidth);
        setMeasuredDimension(
                drawable.getMinimumWidth() + getPaddingLeft() + getPaddingRight(),
                drawable.getMinimumHeight() + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        drawable.setBounds(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        drawable.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        drawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        drawable.detach();
    }

    public TextPaint getTextPaint() {
        return drawable.getTextPaint();
    }
}