package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;

import java.util.Random;

public class BlobDrawable {

    public static float MAX_SPEED = 8.2f;
    public static float MIN_SPEED = 0.8f;
    public static float AMPLITUDE_SPEED = 0.33f;

    public static float SCALE_BIG = 0.807f;
    public static float SCALE_SMALL = 0.704f;

    public static float SCALE_BIG_MIN = 0.878f;
    public static float SCALE_SMALL_MIN = 0.926f;

    public static float FORM_BIG_MAX = 0.6f;
    public static float FORM_SMALL_MAX = 0.6f;

    public static float GLOBAL_SCALE = 1f;

    public static float FORM_BUTTON_MAX = 0f;

    public static float GRADIENT_SPEED_MIN = 0.5f;
    public static float GRADIENT_SPEED_MAX = 0.01f;

    public static float LIGHT_GRADIENT_SIZE = 0.5f;

    public float minRadius;
    public float maxRadius;

    private Path path = new Path();
    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] radius;
    private float[] angle;
    private float[] radiusNext;
    private float[] angleNext;
    private float[] progress;
    private float[] speed;


    private float[] pointStart = new float[4];
    private float[] pointEnd = new float[4];

    final Random random = new Random();

    private final float N;
    private final float L;
    public float cubicBezierK = 1f;

    private final Matrix m = new Matrix();

    private final int liteFlag;

    public BlobDrawable(int n) {
        this(n, LiteMode.FLAG_CALLS_ANIMATIONS);
    }

    public BlobDrawable(int n, int liteFlag) {
        N = n;
        L = (float) ((4.0 / 3.0) * Math.tan(Math.PI / (2 * N)));
        radius = new float[n];
        angle = new float[n];

        radiusNext = new float[n];
        angleNext = new float[n];
        progress = new float[n];
        speed = new float[n];

        for (int i = 0; i < N; i++) {
            generateBlob(radius, angle, i);
            generateBlob(radiusNext, angleNext, i);
            progress[i] = 0;
        }

        this.liteFlag = liteFlag;
    }

    private void generateBlob(float[] radius, float[] angle, int i) {
        float angleDif = 360f / N * 0.05f;
        float radDif = maxRadius - minRadius;
        radius[i] = minRadius + Math.abs(((random.nextInt() % 100f) / 100f)) * radDif;
        angle[i] = 360f / N * i + ((random.nextInt() % 100f) / 100f) * angleDif;
        speed[i] = (float) (0.017 + 0.003 * (Math.abs(random.nextInt() % 100f) / 100f));
    }

    public void update(float amplitude, float speedScale) {
        if (!LiteMode.isEnabled(liteFlag)) {
            return;
        }
        for (int i = 0; i < N; i++) {
            progress[i] += (speed[i] * MIN_SPEED) + amplitude * speed[i] * MAX_SPEED * speedScale;
            if (progress[i] >= 1f) {
                progress[i] = 0;
                radius[i] = radiusNext[i];
                angle[i] = angleNext[i];
                generateBlob(radiusNext, angleNext, i);
            }
        }
    }

    public void draw(float cX, float cY, Canvas canvas, Paint paint) {
        if (!LiteMode.isEnabled(liteFlag)) {
            return;
        }
        path.reset();

        for (int i = 0; i < N; i++) {
            float progress = this.progress[i];
            int nextIndex = i + 1 < N ? i + 1 : 0;
            float progressNext = this.progress[nextIndex];
            float r1 = radius[i] * (1f - progress) + radiusNext[i] * progress;
            float r2 = radius[nextIndex] * (1f - progressNext) + radiusNext[nextIndex] * progressNext;
            float angle1 = angle[i] * (1f - progress) + angleNext[i] * progress;
            float angle2 = angle[nextIndex] * (1f - progressNext) + angleNext[nextIndex] * progressNext;

            float l = L * (Math.min(r1, r2) + (Math.max(r1, r2) - Math.min(r1, r2)) / 2f) * cubicBezierK;
            m.reset();
            m.setRotate(angle1, cX, cY);

            pointStart[0] = cX;
            pointStart[1] = cY - r1;
            pointStart[2] = cX + l;
            pointStart[3] = cY - r1;

            m.mapPoints(pointStart);

            pointEnd[0] = cX;
            pointEnd[1] = cY - r2;
            pointEnd[2] = cX - l;
            pointEnd[3] = cY - r2;

            m.reset();
            m.setRotate(angle2, cX, cY);

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
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    public void generateBlob() {
        for (int i = 0; i < N; i++) {
            generateBlob(radius, angle, i);
            generateBlob(radiusNext, angleNext, i);
            progress[i] = 0;
        }
    }


    private float animateToAmplitude;
    public float amplitude;
    private float animateAmplitudeDiff;

    private final static float ANIMATION_SPEED_WAVE_HUGE = 0.65f;
    private final static float ANIMATION_SPEED_WAVE_SMALL = 0.45f;
    private final static float animationSpeed = 1f - ANIMATION_SPEED_WAVE_HUGE;
    private final static float animationSpeedTiny = 1f - ANIMATION_SPEED_WAVE_SMALL;

    public void setValue(float value, boolean isBig) {
        animateToAmplitude = value;
        if (!LiteMode.isEnabled(liteFlag)) {
            return;
        }
        if (isBig) {
            if (animateToAmplitude > amplitude) {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100f + 300f * animationSpeed);
            } else {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500f * animationSpeed);
            }
        } else {
            if (animateToAmplitude > amplitude) {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100f + 400f * animationSpeedTiny);
            } else {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100f + 500f * animationSpeedTiny);
            }
        }
    }

    public void updateAmplitude(long dt) {
        if (animateToAmplitude != amplitude) {
            amplitude += animateAmplitudeDiff * dt;
            if (animateAmplitudeDiff > 0) {
                if (amplitude > animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            } else {
                if (amplitude < animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            }
        }
    }
}
