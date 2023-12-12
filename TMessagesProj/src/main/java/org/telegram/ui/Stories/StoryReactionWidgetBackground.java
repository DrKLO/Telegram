package org.telegram.ui.Stories;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class StoryReactionWidgetBackground extends Drawable {

    private final int STYLE_FILLED = 0;
    private final int STYLE_TRANSCLUENT = 1;
    int style;
    private final View parent;
    Paint shadowPaint;
    Paint backgroundPaint;
    int alpha = 255;

    float[] points = new float[3 * 5];
    AnimatedFloat progressToMirrored;
    private boolean mirror;
    private Paint xRefPaint;
    Path path = new Path();


    public StoryReactionWidgetBackground(View parent) {
        this.parent = parent;
        progressToMirrored = new AnimatedFloat(parent, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x5f000000);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.WHITE);
    }

    public void updateShadowLayer(float scale) {
        shadowPaint.setShadowLayer(AndroidUtilities.dp(2) / scale, 0, AndroidUtilities.dpf2(0.7f) / scale, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.18f)));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        points[0] = getBounds().centerX();
        points[1] = getBounds().centerY();
        points[2] = getBounds().height() / 2f;

        points[3] = getBounds().left + getBounds().width() * 1.027f;
        points[4] = getBounds().top + getBounds().height() * 0.956f;
        points[5] = getBounds().height() * 0.055f;

        points[6] = getBounds().left + getBounds().width() * 0.843f;
        points[7] = getBounds().top + getBounds().height() * 0.812f;
        points[8] = getBounds().height() * 0.132f;

        //mirrored
        points[9] = getBounds().left + getBounds().width() * (1f - 1.027f);
        points[10] = getBounds().top + getBounds().height() * 0.956f;
        points[11] = getBounds().height() * 0.055f;

        points[12] = getBounds().left + getBounds().width() * (1f - 0.843f);
        points[13] = getBounds().top + getBounds().height() * 0.812f;
        points[14] = getBounds().height() * 0.132f;

        float mirrorProgress = progressToMirrored.set(mirror ? 1f : 0);
        if (style == STYLE_FILLED) {
            backgroundPaint.setColor(Color.WHITE);
        } else if (style == STYLE_TRANSCLUENT) {
            if (xRefPaint == null) {
                xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                xRefPaint.setColor(0xff000000);
                xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                xRefPaint.setStrokeWidth(AndroidUtilities.dp(3));
            }
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 127));
        }
        if (alpha != 255 || style == STYLE_TRANSCLUENT) {
            canvas.saveLayerAlpha(getBounds().left - getBounds().width() * 0.2f, getBounds().top, getBounds().right + getBounds().width() * 0.2f, getBounds().bottom + getBounds().height() * 0.2f, alpha, Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }
        path.rewind();
        for (int k = 0; k < 2; k++) {
            if (style == STYLE_TRANSCLUENT && k == 0) {
                continue;
            }
            Paint paint = k == 0 ? shadowPaint : backgroundPaint;
            int shadowOffset = k == 0 ? 1 : 0;
            for (int i = 0; i < 5; i++) {
                if (i == 1 || i == 2) {
                    if (mirrorProgress == 1f) {
                        continue;
                    }
                    path.addCircle(points[i * 3], points[i * 3 + 1], points[i * 3 + 2] * (1f - mirrorProgress) - shadowOffset, Path.Direction.CW);
                    // drawCircle(canvas, , paint);
                } else if (i == 3 || i == 4) {
                    if (mirrorProgress == 0) {
                        continue;
                    }
                    path.addCircle(points[i * 3], points[i * 3 + 1], points[i * 3 + 2] * mirrorProgress - shadowOffset, Path.Direction.CW);
                    //drawCircle(canvas, points[i * 3], points[i * 3 + 1], points[i * 3 + 2] * mirrorProgress - shadowOffset, paint);
                } else {
                    path.addCircle(points[i * 3], points[i * 3 + 1], points[i * 3 + 2] - shadowOffset, Path.Direction.CW);
                 //   drawCircle(canvas, points[i * 3], points[i * 3 + 1], points[i * 3 + 2] - shadowOffset, paint);
                }
            }
            canvas.drawPath(path, paint);
        }
        canvas.restore();
    }

    private void drawCircle(Canvas canvas, float cx, float cy, float r, Paint paint) {
        if (style == STYLE_TRANSCLUENT) {
            canvas.drawCircle(cx, cy, r, xRefPaint);
        }
        canvas.drawCircle(cx, cy, r, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    public void setMirror(boolean mirror, boolean animate) {
        this.mirror = mirror;
        if (!animate) {
            progressToMirrored.set(mirror ? 1f : 0, true);
        } else {
            parent.invalidate();
        }
    }

    public void nextStyle() {
        style++;
        if (style >= 2) {
            style = 0;
        }
    }

    public boolean isDarkStyle() {
        return style == STYLE_TRANSCLUENT;
    }
}
