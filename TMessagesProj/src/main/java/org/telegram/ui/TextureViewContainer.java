package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.ImageReceiver;

public class TextureViewContainer extends FrameLayout {

    ImageReceiver imageReceiver = new ImageReceiver(this);
    boolean firstFrameRendered;
    TextureView textureView;

    public TextureViewContainer(@NonNull Context context) {
        super(context);
        textureView = new TextureView(context);
        addView(textureView);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!firstFrameRendered) {
            imageReceiver.setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight());
            imageReceiver.draw(canvas);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
    }
}
