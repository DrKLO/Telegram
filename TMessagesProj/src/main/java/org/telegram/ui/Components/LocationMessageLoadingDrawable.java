package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LocationMessageLoadingDrawable extends Drawable {

    private float[] radii = new float[8];
    private float[] tempRadii = new float[8];
    private Path tempPath = new Path();
    private RectF tempRect = new RectF();
    private Paint paint = new Paint(); {
        paint.setColor(0xffff0000);
    }

    public void setRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        setRadii(radii, topLeft, topRight, bottomRight, bottomLeft);
    }

    private void setRadii(float[] arr, float topLeft, float topRight, float bottomRight, float bottomLeft) {
        arr[0] = arr[1] = topLeft;
        arr[2] = arr[3] = topRight;
        arr[4] = arr[5] = bottomRight;
        arr[6] = arr[7] = bottomLeft;
    }


    @Override
    public void draw(@NonNull Canvas canvas) {
        tempPath.rewind();
        tempRect.set(getBounds());
        tempPath.addRoundRect(tempRect, radii, Path.Direction.CW);
        canvas.drawPath(tempPath, paint);
    }

    @Override
    public void setAlpha(int i) {
        paint.setAlpha(i);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
