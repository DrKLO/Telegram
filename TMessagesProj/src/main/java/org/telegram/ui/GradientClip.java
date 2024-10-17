package org.telegram.ui;


import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

public class GradientClip {

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    private static final boolean DEBUG = false;

    private final Paint[] paint = new Paint[4];
    private LinearGradient gradient;
    private final Matrix matrix = new Matrix();

    public void draw(Canvas canvas, RectF rect, boolean top, float alpha) {
        draw(canvas, rect, top ? TOP : BOTTOM, alpha);
    }

    public void draw(Canvas canvas, RectF rect, int dir, float alpha) {
        if (alpha <= 0) return;

        if (gradient == null) {
            gradient = new LinearGradient(0, 0, 0, 16, new int[] { 0xFFFF0000, 0x00FF0000 }, new float[] {0, 1}, Shader.TileMode.CLAMP);
        }
        if (paint[dir] == null) {
            paint[dir] = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (!DEBUG) paint[dir].setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        paint[dir].setShader(gradient);

        matrix.reset();
        if (dir == LEFT) {
            matrix.postScale(1f, rect.width() / 16f);
            matrix.postRotate(-90);
            matrix.postTranslate(rect.left, rect.top);
        } else if (dir == TOP) {
            matrix.postScale(1f, rect.height() / 16f);
            matrix.postTranslate(rect.left, rect.top);
        } else if (dir == RIGHT) {
            matrix.postScale(1f, rect.width() / 16f);
            matrix.postRotate(90);
            matrix.postTranslate(rect.right, rect.top);
        } else if (dir == BOTTOM) {
            matrix.postScale(1f, rect.height() / 16f);
            matrix.postScale(1f, -1f);
            matrix.postTranslate(rect.left, rect.bottom);
        }
        gradient.setLocalMatrix(matrix);
        paint[dir].setAlpha((int) (0xFF * alpha));
        canvas.drawRect(rect, paint[dir]);
    }

    public Paint getPaint(int dir, float alpha) {
        if (paint[dir] == null) {
            paint[dir] = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint[dir].setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        paint[dir].setShader(null);
        paint[dir].setColor(0xFFFF0000);
        paint[dir].setAlpha((int) (0xFF * alpha));
        return paint[dir];
    }

    public void clipOut(Canvas canvas, RectF rect, float alpha) {
        final int dir = 0;
        if (paint[dir] == null) {
            paint[dir] = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (!DEBUG) paint[dir].setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        }
        paint[dir].setShader(gradient);
        paint[dir].setAlpha((int) (0xFF * alpha));
        canvas.drawRect(rect, paint[dir]);
    }

}
