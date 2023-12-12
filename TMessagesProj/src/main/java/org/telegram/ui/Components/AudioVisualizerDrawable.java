package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

public class AudioVisualizerDrawable {

    private final CircleBezierDrawable[] drawables;

    private final int[] tmpWaveform = new int[3];
    private final float[] animateTo = new float[8];
    private final float[] current = new float[8];
    private final float[] dt = new float[8];

    private float idleScale;
    private boolean idleScaleInc;

    private final Paint p1;

    private View parentView;
    private final Random random = new Random();

    public float IDLE_RADIUS = AndroidUtilities.dp(6) * 0.33f;
    public float WAVE_RADIUS = AndroidUtilities.dp(12) * 0.36f;
    public float ANIMATION_DURATION = 120;
    public int ALPHA = 61;


    public AudioVisualizerDrawable() {
        drawables = new CircleBezierDrawable[2];
        for (int i = 0; i < 2; i++) {
            CircleBezierDrawable drawable = drawables[i] = new CircleBezierDrawable(6);
            drawable.idleStateDiff = 0;
            drawable.radius = AndroidUtilities.dp(24);
            drawable.radiusDiff = 0;
            drawable.randomK = 1f;
        }
        p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    final int MAX_SAMPLE_SUM = 6;
    private float[] lastAmplitude = new float[MAX_SAMPLE_SUM];
    private int lastAmplitudeCount;
    private int lastAmplitudePointer;


    public void setWaveform(boolean playing, boolean animate, float[] waveform) {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            return;
        }
        if (!playing && !animate) {
            for (int i = 0; i < 8; i++) {
                animateTo[i] = current[i] = 0;
            }
            return;
        }

        boolean idleState = waveform != null && waveform[6] == 0;
        float amplitude = waveform == null ? 0 : waveform[6];

        if (waveform != null && amplitude > 0.4) {
            lastAmplitude[lastAmplitudePointer] = amplitude;
            lastAmplitudePointer++;
            if (lastAmplitudePointer > MAX_SAMPLE_SUM - 1) {
                lastAmplitudePointer = 0;
            }
            lastAmplitudeCount++;
        } else {
            lastAmplitudeCount = 0;
        }

        if (idleState) {
            for (int i = 0; i < 6; i++) {
                waveform[i] = ((random.nextInt() % 500) / 1000f);
            }
        }
        float duration = idleState ? ANIMATION_DURATION * 2 : ANIMATION_DURATION;
        if (lastAmplitudeCount > MAX_SAMPLE_SUM) {
            float a = 0;
            for (int i = 0; i < MAX_SAMPLE_SUM; i++) {
                a += lastAmplitude[i];
            }
            a /= (float) MAX_SAMPLE_SUM;
            if (a > 0.52f) {
                duration -= ANIMATION_DURATION * (a - 0.40f);
            }
        }
        for (int i = 0; i < 7; i++) {
            if (waveform == null) {
                animateTo[i] = 0;
            } else {
                animateTo[i] = waveform[i];
            }
            if (parentView == null) {
                current[i] = animateTo[i];
            } else if (i == 6) {
                dt[i] = (animateTo[i] - current[i]) / (ANIMATION_DURATION + 80);
            } else {
                dt[i] = (animateTo[i] - current[i]) / duration;
            }
        }

        animateTo[7] = playing ? 1f : 0f;
        dt[7] = (animateTo[7] - current[7]) / 120;
    }

    float rotation;

    public void draw(Canvas canvas, float cx, float cy, int color, float alpha, Theme.ResourcesProvider resourcesProvider) {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            return;
        }
        p1.setColor(color);
        p1.setAlpha((int) (ALPHA * alpha));
        this.draw(canvas, cx, cy);
    }

    public void draw(Canvas canvas, float cx, float cy, boolean outOwner, float alpha, Theme.ResourcesProvider resourcesProvider) {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            return;
        }
        if (outOwner) {
            p1.setColor(Theme.getColor(Theme.key_chat_outLoader, resourcesProvider));
            p1.setAlpha((int) (ALPHA * alpha));
        } else {
            p1.setColor(Theme.getColor(Theme.key_chat_inLoader, resourcesProvider));
            p1.setAlpha((int) (ALPHA * alpha));
        }
        this.draw(canvas, cx, cy);
    }

    public void draw(Canvas canvas, float cx, float cy, boolean outOwner, Theme.ResourcesProvider resourcesProvider) {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            return;
        }
        if (outOwner) {
            p1.setColor(Theme.getColor(Theme.key_chat_outLoader, resourcesProvider));
            p1.setAlpha(ALPHA);
        } else {
            p1.setColor(Theme.getColor(Theme.key_chat_inLoader, resourcesProvider));
            p1.setAlpha(ALPHA);
        }
        this.draw(canvas, cx, cy);
    }

    public void draw(Canvas canvas, float cx, float cy) {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            if (animateTo[i] != current[i]) {
                current[i] += dt[i] * 16;
                if ((dt[i] > 0 && current[i] > animateTo[i]) || (dt[i] < 0 && current[i] < animateTo[i])) {
                    current[i] = animateTo[i];
                }
                parentView.invalidate();
            }
        }

        if (idleScaleInc) {
            idleScale += 0.02f;
            if (idleScale > 1f) {
                idleScaleInc = false;
                idleScale = 1f;
            }
        } else {
            idleScale -= 0.02f;
            if (idleScale < 0) {
                idleScaleInc = true;
                idleScale = 0;
            }
        }

        float enterProgress = current[7];
        float radiusProgress = current[6] * current[0];

        if (enterProgress == 0 && radiusProgress == 0) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            tmpWaveform[i] = (int) (current[i] * WAVE_RADIUS);
        }
        drawables[0].setAdditionals(tmpWaveform);

        for (int i = 0; i < 3; i++) {
            tmpWaveform[i] = (int) (current[i + 3] * WAVE_RADIUS);
        }
        drawables[1].setAdditionals(tmpWaveform);
        float radius = AndroidUtilities.dp(22) +
                AndroidUtilities.dp(4) * radiusProgress +
                IDLE_RADIUS * enterProgress;

        if (radius > AndroidUtilities.dp(26)) {
            radius = AndroidUtilities.dp(26);
        }
        drawables[0].radius = drawables[1].radius = radius;

        canvas.save();
        rotation += 0.6;
        canvas.rotate(rotation, cx, cy);
        canvas.save();
        float s = 1f + 0.04f * idleScale;
        canvas.scale(s, s, cx, cy);
        drawables[0].draw(cx, cy, canvas, p1);
        canvas.restore();

        canvas.rotate(60, cx, cy);
        s = 1f + 0.04f * (1f - idleScale);
        canvas.scale(s, s, cx, cy);
        drawables[1].draw(cx, cy, canvas, p1);
        canvas.restore();
    }

    public void setParentView(View parentView) {
        this.parentView = parentView;
    }

    public View getParentView() {
        return parentView;
    }

    public void setColor(int color) {
        p1.setColor(color);
    }
}
