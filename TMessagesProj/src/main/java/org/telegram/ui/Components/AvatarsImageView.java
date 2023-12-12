package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.tgnet.TLObject;

public class AvatarsImageView extends View {

    public final AvatarsDrawable avatarsDrawable;

    public AvatarsImageView(@NonNull Context context, boolean inCall) {
        super(context);
        avatarsDrawable = new AvatarsDrawable(this, inCall);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        avatarsDrawable.width = getMeasuredWidth();
        avatarsDrawable.height = getMeasuredHeight();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarsDrawable.onAttachedToWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        avatarsDrawable.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarsDrawable.onDetachedFromWindow();
    }


    public void setStyle(int style) {
        avatarsDrawable.setStyle(style);
    }

    public void setDelegate(Runnable delegate) {
        avatarsDrawable.setDelegate(delegate);
    }

    public void setObject(int a, int currentAccount, TLObject object) {
        avatarsDrawable.setObject(a, currentAccount, object);
    }

    public void setAvatarsTextSize(int size) {
        avatarsDrawable.setAvatarsTextSize(size);
    }

    public void setSize(int size) {
        avatarsDrawable.setSize(size);
    }

    public void setStepFactor(float factor) {
        avatarsDrawable.setStepFactor(factor);
    }

    public void reset() {
        avatarsDrawable.reset();
    }

    public void setCount(int usersCount) {
        avatarsDrawable.setCount(usersCount);
    }

    public void commitTransition(boolean animated) {
        avatarsDrawable.commitTransition(animated);
    }

    public void updateAfterTransitionEnd() {
        avatarsDrawable.updateAfterTransitionEnd();
    }

    public void setCentered(boolean centered) {
        avatarsDrawable.setCentered(centered);
    }
}
