package org.telegram.ui.Components.blur3;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

public class StrokeDrawable extends Drawable {

    private BlurredBackgroundColorProvider colorProvider;
    protected int strokeColorTop, strokeColorBottom;

    private float alpha = 1.0f;
    private final RectF rect = new RectF();
    private int padding;

    private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void setBackgroundColor(int color) {
        paintFill.setColor(color);
        invalidateSelf();
    }

    public void setPadding(int padding) {
        this.padding = padding;
    }

    public void setColorProvider(BlurredBackgroundColorProvider colorProvider) {
        this.colorProvider = colorProvider;

        paintStrokeTop.setStyle(Paint.Style.STROKE);
        paintStrokeBottom.setStyle(Paint.Style.STROKE);

        updateColors();
    }

    public void updateColors() {
        if (colorProvider == null) return;

        strokeColorTop = Theme.multAlpha(colorProvider.getStrokeColorTop(), alpha);
        strokeColorBottom = Theme.multAlpha(colorProvider.getStrokeColorBottom(), alpha);

        paintStrokeTop.setColor(strokeColorTop);
        paintStrokeTop.setStrokeWidth(dpf2(1));
        paintStrokeBottom.setColor(strokeColorBottom);
        paintStrokeBottom.setStrokeWidth(dpf2(2 / 3f));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float cx = getBounds().centerX();
        final float cy = getBounds().centerY();
        final float radius = Math.min(getBounds().width(), getBounds().height()) / 2.0f - padding;
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawCircle(cx, cy, radius, paintFill);
        if (strokeColorTop != 0) {
            BlurredBackgroundDrawable.drawStroke(canvas, rect, radius, dpf2(1), true, paintStrokeTop);
        }
        if (strokeColorBottom != 0) {
            BlurredBackgroundDrawable.drawStroke(canvas, rect, radius, dpf2(2 / 3f), false, paintStrokeBottom);
        }
    }

    @Override
    public void setAlpha(int i) {
        alpha = i / 255.0f;
        updateColors();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
