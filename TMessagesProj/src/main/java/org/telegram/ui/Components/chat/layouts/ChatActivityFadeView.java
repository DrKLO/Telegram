package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;

public class ChatActivityFadeView extends View {
    private BlurredBackgroundWithFadeDrawable fadeDrawableTop;
    private BlurredBackgroundWithFadeDrawable fadeDrawableBottom;
    private int fadeZoneTop, fadeZoneBottom;

    public ChatActivityFadeView(Context context) {
        super(context);
    }

    public void setup(BlurredBackgroundDrawableViewFactory factory) {
        fadeDrawableTop = new BlurredBackgroundWithFadeDrawable(factory.create(this));
        fadeDrawableTop.setFadeHeight(-dp(30), true);

        fadeDrawableBottom = new BlurredBackgroundWithFadeDrawable(factory.create(this));
        fadeDrawableBottom.setFadeHeight(dp(30), true);
    }

    public void setFadeHeightTop(int height) {
        fadeDrawableTop.setFadeHeight(-height, true);
    }

    public void setFadeHeightBottom(int height) {
        fadeDrawableBottom.setFadeHeight(height, true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkBounds();
    }

    public void setFadeZoneTop(int height) {
        if (fadeZoneTop != height) {
            fadeZoneTop = height;
            checkBounds();
            invalidate();
        }
    }

    public void setFadeZoneBottom(int height) {
        if (fadeZoneBottom != height) {
            fadeZoneBottom = height;
            checkBounds();
            invalidate();
        }
    }

    private void checkBounds() {
        fadeDrawableTop.setBounds(0, 0, getMeasuredWidth(), fadeZoneTop);
        fadeDrawableBottom.setBounds(0, getMeasuredHeight() - fadeZoneBottom, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        fadeDrawableTop.draw(canvas);
        fadeDrawableBottom.draw(canvas);
    }
}

