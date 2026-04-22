package org.telegram.ui.Components.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

public class ChatActivityTopFadeView extends View {
    public ChatActivityTopFadeView(Context context) {
        super(context);
    }

    private Drawable drawable;
    private int fadeHeight;

    public void setFadeDrawable(Drawable drawable) {
        this.drawable = drawable;
        checkBounds();
        invalidate();
    }

    public void setFadeHeight(int fadeHeight) {
        if (this.fadeHeight != fadeHeight) {
            this.fadeHeight = fadeHeight;
            checkBounds();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkBounds();
    }

    private void checkBounds() {
        if (drawable != null) {
            drawable.setBounds(0, 0, getMeasuredWidth(), fadeHeight);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (drawable != null) {
            drawable.draw(canvas);
        }
    }
}
