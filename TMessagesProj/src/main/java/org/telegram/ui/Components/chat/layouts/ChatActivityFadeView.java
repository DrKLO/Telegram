package org.Tajgram.ui.Components.chat.layouts;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.Tajgram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.Tajgram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.Tajgram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

public class ChatActivityFadeView extends View {
    private BlurredBackgroundWithFadeDrawable fadeDrawableTop;
    private BlurredBackgroundWithFadeDrawable fadeDrawableBottom;
    private int fadeZoneTop, fadeZoneBottom;

    public ChatActivityFadeView(Context context) {
        super(context);
    }

    public void setup(BlurredBackgroundDrawableViewFactory factory) {
        setup(factory, null);
    }

    public void setup(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        fadeDrawableTop = new BlurredBackgroundWithFadeDrawable(factory.create(this).setColorProvider(colorProvider));
        fadeDrawableTop.setFadeHeight(-dp(30), true);

        fadeDrawableBottom = new BlurredBackgroundWithFadeDrawable(factory.create(this).setColorProvider(colorProvider));
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

    public void setFadeTopAlpha(int alpha) {
        if (fadeDrawableTop.getAlpha() != alpha) {
            fadeDrawableTop.setAlpha(alpha);
            invalidate();
        }
    }
    
    private void checkBounds() {
        fadeDrawableTop.setBounds(0, 0, getMeasuredWidth(), fadeZoneTop);
        fadeDrawableBottom.setBounds(0, getMeasuredHeight() - fadeZoneBottom, getMeasuredWidth(), getMeasuredHeight());
    }

    public void setIgnoreFastWay(boolean ignoreFastWay) {
        fadeDrawableTop.setIgnoreFastWay(ignoreFastWay);
        fadeDrawableBottom.setIgnoreFastWay(ignoreFastWay);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        fadeDrawableTop.draw(canvas);
        fadeDrawableBottom.draw(canvas);
    }
}

