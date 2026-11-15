package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;

/**
 * Effects for the enlarged selection oval of the main tab bar:
 * blue body, four-side edge reflections, green/blue light dispersion rim and a moving prism beam.
 * Call draw() from MainTabsLayout.dispatchDraw() while the oval is pressed.
 */
public final class GlassPressEffects {

    public static final int BLUE = 0xFF0A84FF;

    private static final int REFLECT_TOP = 0xFFFFFFFF;
    private static final int REFLECT_BOTTOM = 0xFF28DCC8;
    private static final int REFLECT_LEFT = 0xFF5AE682;
    private static final int REFLECT_RIGHT = 0xFF3C96FF;

    private static final int[] DISPERSION = {
            0xFF3DDC84, 0xFF2DE0C4, 0xFF4DE1FF, 0xFF3A8BFF, 0xFF4DE1FF, 0xFF2DE0C4, 0xFF3DDC84
    };
    private static final int[] PRISM = {
            0x00000000, 0xFF3CDC82, 0xFF78FFAA, 0xFF3CF0D2, 0xFF3CC8FF, 0xFF3C82FF, 0x00000000
    };
    private static final float[] PRISM_POS = {0f, 0.2f, 0.35f, 0.5f, 0.65f, 0.8f, 1f};

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint prismPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rimRect = new RectF();
    private final Matrix matrix = new Matrix();
    private final SweepGradient sweep = new SweepGradient(0, 0, DISPERSION, null);

    public GlassPressEffects() {
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setShader(sweep);
    }

    /**
     * @param rect   the enlarged oval bounds
     * @param factor 0..1 press progress
     * @param strength 0..1 overall intensity of reflections and dispersion
     */
    public void draw(Canvas canvas, RectF rect, float factor, float strength) {
        if (factor <= 0.01f) {
            return;
        }
        final float h = rect.height();
        final float radius = h / 2f;

        bodyPaint.setColor(BLUE);
        bodyPaint.setAlpha((int) (255 * 0.16f * factor));
        canvas.drawRoundRect(rect, radius, radius, bodyPaint);

        canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        final float edgeV = h * 0.34f;
        final float edgeH = Math.min(rect.width() * 0.24f, h * 0.55f);
        final float edgeAlpha = factor * strength;
        drawEdge(canvas, rect, rect.left, rect.top, rect.left, rect.top + edgeV, REFLECT_TOP, 0.85f * edgeAlpha);
        drawEdge(canvas, rect, rect.left, rect.bottom, rect.left, rect.bottom - edgeV, REFLECT_BOTTOM, 0.65f * edgeAlpha);
        drawEdge(canvas, rect, rect.left, rect.top, rect.left + edgeH, rect.top, REFLECT_LEFT, 0.6f * edgeAlpha);
        drawEdge(canvas, rect, rect.right, rect.top, rect.right - edgeH, rect.top, REFLECT_RIGHT, 0.6f * edgeAlpha);

        final float t = (SystemClock.uptimeMillis() % 2800L) / 2800f;
        final float p = t < 0.5f ? t * 2f : (1f - t) * 2f;
        final float cx = rect.left + (p * 1.6f - 0.3f) * rect.width();
        final float half = rect.width() * 0.18f;
        prismPaint.setShader(new LinearGradient(cx - half, rect.top, cx + half, rect.bottom, PRISM, PRISM_POS, Shader.TileMode.CLAMP));
        prismPaint.setAlpha((int) (255 * 0.8f * factor * strength));
        canvas.drawRect(rect, prismPaint);
        canvas.restore();

        final float angle = (SystemClock.uptimeMillis() % 3500L) / 3500f * 360f;
        matrix.setRotate(angle);
        matrix.postTranslate(rect.centerX(), rect.centerY());
        sweep.setLocalMatrix(matrix);
        final float stroke = dp(2.5f);
        rimPaint.setStrokeWidth(stroke);
        rimPaint.setAlpha((int) (255 * 0.9f * factor * strength));
        rimRect.set(rect);
        rimRect.inset(stroke / 2f, stroke / 2f);
        canvas.drawRoundRect(rimRect, radius - stroke / 2f, radius - stroke / 2f, rimPaint);
    }

    private void drawEdge(Canvas canvas, RectF rect, float x0, float y0, float x1, float y1, int color, float alpha) {
        final int from = Color.argb((int) (255 * Math.min(1f, Math.max(0f, alpha))), Color.red(color), Color.green(color), Color.blue(color));
        final int to = from & 0x00FFFFFF;
        edgePaint.setShader(new LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP));
        canvas.drawRect(rect, edgePaint);
    }
}
