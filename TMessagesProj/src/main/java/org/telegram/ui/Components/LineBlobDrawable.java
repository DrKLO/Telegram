package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import org.telegram.messenger.AndroidUtilities;

import java.util.Random;

public class LineBlobDrawable {

    public float minRadius;
    public float maxRadius;

    public Path path = new Path();
    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] radius;
    private float[] radiusNext;
    private float[] progress;
    private float[] speed;

    final Random random = new Random();

    private final float N;

    public LineBlobDrawable(int n) {
        N = n;
        radius = new float[n + 1];

        radiusNext = new float[n + 1];
        progress = new float[n + 1];
        speed = new float[n + 1];

        for (int i = 0; i <= N; i++) {
            generateBlob(radius, i);
            generateBlob(radiusNext, i);
            progress[i] = 0;
        }
    }

    private void generateBlob(float[] radius, int i) {
        float radDif = maxRadius - minRadius;
        radius[i] = minRadius + Math.abs(((random.nextInt() % 100f) / 100f)) * radDif;
        speed[i] = (float) (0.017 + 0.003 * (Math.abs(random.nextInt() % 100f) / 100f));
    }

    public void update(float amplitude, float speedScale) {
        for (int i = 0; i <= N; i++) {
            progress[i] += (speed[i] * BlobDrawable.MIN_SPEED) + amplitude * speed[i] * BlobDrawable.MAX_SPEED * speedScale;
            if (progress[i] >= 1f) {
                progress[i] = 0;
                radius[i] = radiusNext[i];
                generateBlob(radiusNext, i);
            }
        }
    }

    public void draw(float left, float top, float right, float bottom, Canvas canvas, Paint paint, float  pinnedTop, float progressToPinned) {
        path.reset();

        path.moveTo(right, bottom);
        path.lineTo(left, bottom);

        for (int i = 0; i <= N; i++) {
            if (i == 0) {
                float progress = this.progress[i];
                float r1 = radius[i] * (1f - progress) + radiusNext[i] * progress;
                float y = (top - r1) * progressToPinned + pinnedTop * (1f - progressToPinned);
                path.lineTo(left, y);
            } else {
                float progress = this.progress[i - 1];
                float r1 = radius[i - 1] * (1f - progress) + radiusNext[i - 1] * progress;
                float progressNext = this.progress[i];
                float r2 = radius[i] * (1f - progressNext) + radiusNext[i] * progressNext;
                float x1 = (right - left) / N * (i - 1);
                float x2 = (right - left) / N * i;
                float cx = x1 + (x2 - x1) / 2;

                float y1 = (top - r1) * progressToPinned + pinnedTop * (1f - progressToPinned);
                float y2 = (top - r2) * progressToPinned + pinnedTop * (1f - progressToPinned);
                path.cubicTo(
                        cx, y1,
                        cx, y2,
                        x2, y2
                );
                if (i == N) {
                    path.lineTo(right, bottom);
                }
            }
        }

        canvas.drawPath(path, paint);
    }

    public void generateBlob() {
        for (int i = 0; i < N; i++) {
            generateBlob(radius, i);
            generateBlob(radiusNext, i);
            progress[i] = 0;
        }
    }
}
