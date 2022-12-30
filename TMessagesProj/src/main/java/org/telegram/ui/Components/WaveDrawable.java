package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

public class WaveDrawable {

    public final static float MAX_AMPLITUDE = 1800f;

    private final static float ROTATION_SPEED = 0.36f * 0.1f;
    public final static float SINE_WAVE_SPEED = 0.81f;
    public final static float SMALL_WAVE_RADIUS = 0.55f;
    public final static float SMALL_WAVE_SCALE = 0.40f;
    public final static float SMALL_WAVE_SCALE_SPEED = 0.60f;
    public final static float FLING_DISTANCE = 0.50f;
    private final static float WAVE_ANGLE = 0.03f;
    private final static float RANDOM_RADIUS_SIZE = 0.3f;

    private final static float ANIMATION_SPEED_CIRCLE = 0.45f;
    public final static float CIRCLE_ALPHA_1 = 0.30f;
    public final static float CIRCLE_ALPHA_2 = 0.15f;

    private final static float IDLE_ROTATION_SPEED = 0.2f;
    private final static float IDLE_WAVE_ANGLE = 0.5f;
    private final static float IDLE_SCALE_SPEED = 0.3f;
    private final static float IDLE_RADIUS = 0.56f;
    private final static float IDLE_ROTATE_DIF = 0.1f * IDLE_ROTATION_SPEED;

    private final static float ANIMATION_SPEED_WAVE_HUGE = 0.65f;
    private final static float ANIMATION_SPEED_WAVE_SMALL = 0.45f;
    private final static float animationSpeed = 1f - ANIMATION_SPEED_WAVE_HUGE;
    private final static float animationSpeedTiny = 1f - ANIMATION_SPEED_WAVE_SMALL;
    public final static float animationSpeedCircle = 1f - ANIMATION_SPEED_CIRCLE;

    private Paint paintRecordWaveBig = new Paint();
    private Paint paintRecordWaveTin = new Paint();

    public float fling;
    private float animateToAmplitude;
    private float amplitude;
    private float slowAmplitude;
    private float animateAmplitudeDiff;
    private float animateAmplitudeSlowDiff;
    float lastRadius;
    float radiusDiff;
    float waveDif;
    double waveAngle;
    private boolean incRandomAdditionals;

    public float rotation;
    float idleRotation;

    private float circleRadius;

    private Interpolator linearInterpolator = new LinearInterpolator();

    public float amplitudeWaveDif;
    private final CircleBezierDrawable circleBezierDrawable;
    public float amplitudeRadius;
    private float idleRadius = 0;
    private float idleRadiusK = 0.15f * IDLE_WAVE_ANGLE;
    private boolean expandIdleRadius;
    private boolean expandScale;

    private boolean isBig;

    private boolean isIdle = true;
    private float scaleIdleDif;
    private float scaleDif;
    public float scaleSpeed = 0.00008f;
    public float scaleSpeedIdle = 0.0002f * IDLE_SCALE_SPEED;
    public float maxScale;

    private float flingRadius;
    private Animator flingAnimator;

    private ValueAnimator animator;

    float randomAdditions = AndroidUtilities.dp(8) * RANDOM_RADIUS_SIZE;

    private final ValueAnimator.AnimatorUpdateListener flingUpdateListener = animation -> flingRadius = (float) animation.getAnimatedValue();
    private float idleGlobalRadius = AndroidUtilities.dp(10f) * IDLE_RADIUS;

    private float sineAngleMax;

    private WaveDrawable tinyWaveDrawable;

    private long lastUpdateTime;
    private View parentView;

    public WaveDrawable(View parent, int n, float rotateDif, float radius, WaveDrawable tinyDrawable) {
        parentView = parent;
        circleBezierDrawable = new CircleBezierDrawable(n);
        amplitudeRadius = radius;
        isBig = tinyDrawable != null;
        tinyWaveDrawable = tinyDrawable;
        expandIdleRadius = isBig;
        radiusDiff = AndroidUtilities.dp(34) * 0.0012f;

        if (Build.VERSION.SDK_INT >= 26) {
            paintRecordWaveBig.setAntiAlias(true);
            paintRecordWaveTin.setAntiAlias(true);
        }
    }

    public void setValue(float value) {
        animateToAmplitude = value;

        if (isBig) {
            if (animateToAmplitude > amplitude) {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100f + 300f * animationSpeed);
            } else {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500f * animationSpeed);
            }
            animateAmplitudeSlowDiff = (animateToAmplitude - slowAmplitude) / (100f + 500 * animationSpeed);
        } else {
            if (animateToAmplitude > amplitude) {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100f + 400f * animationSpeedTiny);
            } else {
                animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100f + 500f * animationSpeedTiny);
            }
            animateAmplitudeSlowDiff = (animateToAmplitude - slowAmplitude) / (100f + 500 * animationSpeedTiny);
        }

        boolean idle = value < 0.1f;
        if (isIdle != idle && idle && isBig) {
            float bRotation = rotation;
            int k = 60;
            float animateToBRotation = Math.round(rotation / k) * k + k / 2;
            float tRotation = tinyWaveDrawable.rotation;
            float animateToTRotation = Math.round(tRotation / k) * k;

            float bWaveDif = waveDif;
            float tWaveDif = tinyWaveDrawable.waveDif;
            animator = ValueAnimator.ofFloat(1f, 0f);
            animator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                rotation = animateToBRotation + (bRotation - animateToBRotation) * v;
                tinyWaveDrawable.rotation = animateToTRotation + (tRotation - animateToTRotation) * v;
                waveDif = 1f + (bWaveDif - 1f) * v;
                tinyWaveDrawable.waveDif = 1 + (tWaveDif - 1f) * v;

                waveAngle = (float) Math.acos(waveDif);
                tinyWaveDrawable.waveAngle = (float) Math.acos(-tinyWaveDrawable.waveDif);
            });
            animator.setDuration(1200);
            animator.start();
        }

        isIdle = idle;

        if (!isIdle && animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void startFling(float delta) {
        if (SharedConfig.getLiteMode().enabled()) {
            return;
        }
        if (flingAnimator != null) {
            flingAnimator.cancel();
        }
        float fling = this.fling * 2;
        float flingDistance = delta * amplitudeRadius * (isBig ? 8 : 20) * 16 * fling;
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(flingRadius, flingDistance);
        valueAnimator.addUpdateListener(flingUpdateListener);

        valueAnimator.setDuration((long) ((isBig ? 200 : 350) * fling));
        valueAnimator.setInterpolator(linearInterpolator);
        ValueAnimator valueAnimator1 = ValueAnimator.ofFloat(flingDistance, 0);
        valueAnimator1.addUpdateListener(flingUpdateListener);

        valueAnimator1.setInterpolator(linearInterpolator);
        valueAnimator1.setDuration((long) ((isBig ? 220 : 380) * fling));

        AnimatorSet animatorSet = new AnimatorSet();
        flingAnimator = animatorSet;
        animatorSet.playSequentially(valueAnimator, valueAnimator1);
        animatorSet.start();

    }

    boolean wasFling;

    public void tick(float circleRadius) {
        if (SharedConfig.getLiteMode().enabled()) {
            return;
        }
        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 20) {
            dt = 17;
        }

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

            if (Math.abs(amplitude - animateToAmplitude) * amplitudeRadius < AndroidUtilities.dp(4)) {
                if (!wasFling) {
                    startFling(animateAmplitudeDiff);
                    wasFling = true;
                }
            } else {
                wasFling = false;
            }
        }

        if (animateToAmplitude != slowAmplitude) {
            slowAmplitude += animateAmplitudeSlowDiff * dt;
            if (Math.abs(slowAmplitude - amplitude) > 0.2f) {
                slowAmplitude = amplitude + (slowAmplitude > amplitude ?
                        0.2f : -0.2f);
            }
            if (animateAmplitudeSlowDiff > 0) {
                if (slowAmplitude > animateToAmplitude) {
                    slowAmplitude = animateToAmplitude;
                }
            } else {
                if (slowAmplitude < animateToAmplitude) {
                    slowAmplitude = animateToAmplitude;
                }
            }
        }


        idleRadius = circleRadius * idleRadiusK;
        if (expandIdleRadius) {
            scaleIdleDif += scaleSpeedIdle * dt;
            if (scaleIdleDif >= 0.05f) {
                scaleIdleDif = 0.05f;
                expandIdleRadius = false;
            }
        } else {
            scaleIdleDif -= scaleSpeedIdle * dt;
            if (scaleIdleDif < 0f) {
                scaleIdleDif = 0f;
                expandIdleRadius = true;
            }
        }

        if (maxScale > 0) {
            if (expandScale) {
                scaleDif += scaleSpeed * dt;
                if (scaleDif >= maxScale) {
                    scaleDif = maxScale;
                    expandScale = false;
                }
            } else {
                scaleDif -= scaleSpeed * dt;
                if (scaleDif < 0f) {
                    scaleDif = 0f;
                    expandScale = true;
                }
            }
        }


        if (sineAngleMax > animateToAmplitude) {
            sineAngleMax -= 0.25f;
            if (sineAngleMax < animateToAmplitude) {
                sineAngleMax = animateToAmplitude;
            }
        } else if (sineAngleMax < animateToAmplitude) {
            sineAngleMax += 0.25f;
            if (sineAngleMax > animateToAmplitude) {
                sineAngleMax = animateToAmplitude;
            }
        }

        if (!isIdle) {
            rotation += (ROTATION_SPEED * 0.5f + ROTATION_SPEED * 4f * (amplitude > 0.5f ? 1 : amplitude / 0.5f)) * dt;
            if (rotation > 360) rotation %= 360;
        } else {
            idleRotation += IDLE_ROTATE_DIF * dt;
            if (idleRotation > 360) idleRotation %= 360;
        }

        if (lastRadius < circleRadius) {
            lastRadius = circleRadius;
        } else {
            lastRadius -= radiusDiff * dt;
            if (lastRadius < circleRadius) {
                lastRadius = circleRadius;
            }
        }

        lastRadius = circleRadius;

        if (!isIdle) {
            waveAngle += (amplitudeWaveDif * sineAngleMax) * dt;
            if (isBig) {
                waveDif = (float) Math.cos(waveAngle);
            } else {
                waveDif = -(float) Math.cos(waveAngle);
            }

            if (waveDif > 0f && incRandomAdditionals) {
                circleBezierDrawable.calculateRandomAdditionals();
                incRandomAdditionals = false;
            } else if (waveDif < 0f && !incRandomAdditionals) {
                circleBezierDrawable.calculateRandomAdditionals();
                incRandomAdditionals = true;
            }
        }

        parentView.invalidate();
    }

    public void draw(float cx, float cy, float scale, Canvas canvas) {
        if (SharedConfig.getLiteMode().enabled()) {
            return;
        }
        float waveAmplitude = amplitude < 0.3f ? amplitude / 0.3f : 1f;
        float radiusDiff = AndroidUtilities.dp(10) + AndroidUtilities.dp(50) * WAVE_ANGLE * animateToAmplitude;

        circleBezierDrawable.idleStateDiff = idleRadius * (1f - waveAmplitude);

        float kDiff = 0.35f * waveAmplitude * waveDif;
        circleBezierDrawable.radiusDiff = radiusDiff * kDiff;
        circleBezierDrawable.cubicBezierK = 1f + Math.abs(kDiff) * waveAmplitude + (1f - waveAmplitude) * idleRadiusK;


        circleBezierDrawable.radius = (lastRadius + amplitudeRadius * amplitude) + idleGlobalRadius + (flingRadius * waveAmplitude);

        if (circleBezierDrawable.radius + circleBezierDrawable.radiusDiff < circleRadius) {
            circleBezierDrawable.radiusDiff = circleRadius - circleBezierDrawable.radius;
        }

        if (isBig) {
            circleBezierDrawable.globalRotate = rotation + idleRotation;
        } else {
            circleBezierDrawable.globalRotate = -rotation + idleRotation;
        }

        canvas.save();
        float s = scale + scaleIdleDif * (1f - waveAmplitude) + scaleDif * waveAmplitude;
        canvas.scale(s, s, cx, cy);
        circleBezierDrawable.setRandomAdditions(waveAmplitude * waveDif * randomAdditions);

        circleBezierDrawable.draw(cx, cy, canvas, isBig ? paintRecordWaveBig : paintRecordWaveTin);
        canvas.restore();
    }

    public void setCircleRadius(float radius) {
        circleRadius = radius;
    }

    public void setColor(int color, int alpha) {
        paintRecordWaveBig.setColor(color);
        paintRecordWaveTin.setColor(color);
        paintRecordWaveBig.setAlpha(alpha);
        paintRecordWaveTin.setAlpha(alpha);
    }
}
