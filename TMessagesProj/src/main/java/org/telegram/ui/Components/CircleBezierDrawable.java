package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

public class CircleBezierDrawable {

    private Path path = new Path();
    private float[] pointStart = new float[4];
    private float[] pointEnd = new float[4];
    private Matrix m = new Matrix();
    private final float L;
    private final int N;

    float globalRotate = 0f;
    public float idleStateDiff = 0f;
    float radius;
    float radiusDiff;
    float cubicBezierK = 1f;

    public CircleBezierDrawable(int n) {
        N = n;
        L = (float) ((4.0 / 3.0) * Math.tan(Math.PI / (2 * N)));
    }

    protected void draw(float cX, float cY, Canvas canvas, Paint paint) {
        float r1 = radius - idleStateDiff / 2f - radiusDiff / 2f;
        float r2 = radius + radiusDiff / 2 + idleStateDiff / 2f;

        float l = L * Math.max(r1, r2) * cubicBezierK;

        path.reset();
        for (int i = 0; i < N; i++) {
            m.reset();
            m.setRotate(360f / N * i, cX, cY);
            float r = (i % 2 == 0 ? r1 : r2);

            pointStart[0] = cX;
            pointStart[1] = cY - r;
            pointStart[2] = cX + l;
            pointStart[3] = cY - r;

            m.mapPoints(pointStart);

            int j = i + 1;
            if (j >= N) j = 0;

            r = (j % 2 == 0 ? r1 : r2);

            pointEnd[0] = cX;
            pointEnd[1] = cY - r;
            pointEnd[2] = cX - l;
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
}
