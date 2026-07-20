package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;

public class ChatActivityFadeView extends View implements Theme.Colorable {
    private BlurredBackgroundWithFadeDrawable fadeDrawableTop;
    private BlurredBackgroundWithFadeDrawable fadeDrawableBottom;
    private int fadeZoneTop, fadeZoneBottom;

    public ChatActivityFadeView(Context context) {
        super(context);
    }


    private BlurredBackgroundSourceColor sourceColor;
    private BlurredBackgroundDrawableViewFactory factory;
    private int colorKey;

    public void setupColorKey(int colorKey) {
        this.colorKey = colorKey;
        if (sourceColor == null) {
            sourceColor = new BlurredBackgroundSourceColor();
            sourceColor.setColor(Theme.getColor(colorKey));
            factory = new BlurredBackgroundDrawableViewFactory(sourceColor);
            setup(factory);
        }
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

    public void setFadeHeightTop(int height, boolean opacity) {
        fadeDrawableTop.setFadeHeight(-height, opacity);
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

    @Override
    public void updateColors() {
        if (sourceColor != null && colorKey != -1) {
            sourceColor.setColor(Theme.getColor(colorKey));
            invalidate();
        }
    }
}

