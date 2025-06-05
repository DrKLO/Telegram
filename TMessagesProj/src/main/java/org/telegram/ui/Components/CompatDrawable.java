package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CompatDrawable extends Drawable {
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CompatDrawable(View view) {
        if (view != null) {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    onAttachedToWindow();
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    onDetachedToWindow();
                }
            });
            if (view.isAttachedToWindow()) {
                view.post(this::onAttachedToWindow);
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {

    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    public void onAttachedToWindow() {}
    public void onDetachedToWindow() {}
}
