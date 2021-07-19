package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Random;

public class CircleBezierDrawable {

    private Path path = new Path();
    private float[] pointStart = new float[4];
    private float[] pointEnd = new float[4];
    private Matrix m = new Matrix();
    private final float L;
    private final int N;

    float globalRotate = 0f;
    public float idleStateDiff = 0f;
    public float radius;
    public float radiusDiff;
    public float cubicBezierK = 1f;

    final Random random = new Random();

    float[] randomAdditionals;

    public float randomK;

    public CircleBezierDrawable(int n) {
        N = n;
        L = (float) ((4.0 / 3.0) * Math.tan(Math.PI / (2 * N)));
        randomAdditionals = new float[n];
        calculateRandomAdditionals();
    }

    public void calculateRandomAdditionals() {
        for (int i = 0; i < N; i++) {
            randomAdditionals[i] = random.nextInt() % 100 / 100f;
        }
    }

    public void setAdditionals(int[] additionals) {
        for (int i = 0; i < N; i += 2) {
            randomAdditionals[i] = additionals[i / 2];
            randomAdditionals[i + 1] = 0;
        }
    }

    public void draw(float cX, float cY, Canvas canvas, Paint paint) {
        float r1 = radius - idleStateDiff / 2f - radiusDiff / 2f;
        float r2 = radius + radiusDiff / 2 + idleStateDiff / 2f;

        float l = L * Math.max(r1, r2) * cubicBezierK;

        path.reset();
        for (int i = 0; i < N; i++) {
            m.reset();
            m.setRotate(360f / N * i, cX, cY);
            float r = (i % 2 == 0 ? r1 : r2) + randomK * randomAdditionals[i];

            pointStart[0] = cX;
            pointStart[1] = cY - r;
            pointStart[2] = cX + l + randomK * randomAdditionals[i] * L;
            pointStart[3] = cY - r;

            m.mapPoints(pointStart);

            int j = i + 1;
            if (j >= N) j = 0;

            r = (j % 2 == 0 ? r1 : r2) + randomK * randomAdditionals[j];


            pointEnd[0] = cX;
            pointEnd[1] = cY - r;
            pointEnd[2] = cX - l + randomK * randomAdditionals[j] * L;
            pointEnd[3] = cY - r;


            m.reset();
            m.setRotate(360f / N * j, cX, cY);

            m.mapPoints(pointEnd);

            if (i == 0) {
                path.moveTo(pointStart[0], pointStart[1]);
            }

            path.cubicTo(
                    pointStart[2], pointStart[3],
                    pointEnd[2], pointEnd[3],
                    pointEnd[0], pointEnd[1]
            );
        }

        canvas.save();
        canvas.rotate(globalRotate, cX, cY);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    public void setRandomAdditions(float randomK) {
        this.randomK = randomK;
    }
}
