package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

@SuppressLint("ViewConstructor")
public class BlurredLinearLayout extends LinearLayout {

    private final SizeNotifierFrameLayout sizeNotifierFrameLayout;
    protected Paint backgroundPaint;
    public int backgroundColor = Color.TRANSPARENT;
    public int backgroundPaddingBottom;
    public int backgroundPaddingTop;
    public boolean isTopView = true;
    public boolean drawBlur = true;

    public BlurredLinearLayout(@NonNull Context context, SizeNotifierFrameLayout sizeNotifierFrameLayout) {
        super(context);
        this.sizeNotifierFrameLayout = sizeNotifierFrameLayout;
    }

    private android.graphics.Rect blurBounds = new android.graphics.Rect();
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (SharedConfig.chatBlurEnabled() && sizeNotifierFrameLayout != null && drawBlur && backgroundColor != Color.TRANSPARENT) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint();
            }
            backgroundPaint.setColor(backgroundColor);
            blurBounds.set(0, backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight() - backgroundPaddingBottom);
            float y = 0;
            View view = this;
            while (view != sizeNotifierFrameLayout) {
                y += view.getY();
                view = (View) view.getParent();
            }
            sizeNotifierFrameLayout.drawBlurRect(canvas, y, blurBounds, backgroundPaint, isTopView);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        if (SharedConfig.chatBlurEnabled() && sizeNotifierFrameLayout != null) {
            backgroundColor = color;
        } else {
            super.setBackgroundColor(color);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        if (SharedConfig.chatBlurEnabled() && sizeNotifierFrameLayout != null) {
            sizeNotifierFrameLayout.blurBehindViews.add(this);
        }
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sizeNotifierFrameLayout != null) {
            sizeNotifierFrameLayout.blurBehindViews.remove(this);
        }
        super.onDetachedFromWindow();
    }
}