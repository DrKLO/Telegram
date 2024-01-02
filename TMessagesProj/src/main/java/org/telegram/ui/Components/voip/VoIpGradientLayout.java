package org.telegram.ui.Components.voip;

import static org.telegram.ui.Components.voip.VoIPBackgroundProvider.REVEAL_SCALE_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.Components.MotionBackgroundDrawable;

@SuppressLint("ViewConstructor")
public class VoIpGradientLayout extends FrameLayout {

    public enum GradientState {
        CALLING,
        CONNECTED,
        BAD_CONNECTION
    }

    private final MotionBackgroundDrawable bgBlueViolet;
    private final MotionBackgroundDrawable bgBlueGreen;
    private final MotionBackgroundDrawable bgGreen;
    private final MotionBackgroundDrawable bgOrangeRed;

    private final MotionBackgroundDrawable bgBlueVioletDark;
    private final MotionBackgroundDrawable bgBlueGreenDark;
    private final MotionBackgroundDrawable bgGreenDark;
    private final MotionBackgroundDrawable bgOrangeRedDark;

    private final MotionBackgroundDrawable bgBlueVioletLight;
    private final MotionBackgroundDrawable bgBlueGreenLight;
    private final MotionBackgroundDrawable bgGreenLight;
    private final MotionBackgroundDrawable bgOrangeRedLight;
    private final MotionBackgroundDrawable bgGreenLightReveal;
    private final MotionBackgroundDrawable bgGreenDarkReveal;

    private int alphaBlueViolet = 0;
    private int alphaBlueGreen = 0;
    private int alphaGreen = 0;
    private int alphaOrangeRed = 0;
    private float clipRadius = 0f;
    private boolean showClip = false;
    private final Path clipPath = new Path();
    private int clipCx = 0;
    private int clipCy = 0;
    private ValueAnimator callingAnimator;
    private ValueAnimator badConnectionAnimator;
    private AnimatorSet connectedAnimatorSet;
    private final AnimatorSet defaultAnimatorSet;
    private GradientState state;
    private boolean isPaused = false;
    public volatile boolean lockDrawing = false;
    private final VoIPBackgroundProvider backgroundProvider;
    private boolean allowAnimations;

    public VoIpGradientLayout(@NonNull Context context, VoIPBackgroundProvider backgroundProvider) {
        super(context);
        this.backgroundProvider = backgroundProvider;
        allowAnimations = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);
        bgBlueViolet = new MotionBackgroundDrawable(0xFFB456D8, 0xFF8148EC, 0xFF20A4D7, 0xFF3F8BEA, 0, false, true);
        bgBlueGreen = new MotionBackgroundDrawable(0xFF4576E9, 0xFF3B7AF1, 0xFF08B0A3, 0xFF17AAE4, 0, false, true);
        bgGreen = new MotionBackgroundDrawable(0xFF07A9AC, 0xFF07BA63, 0xFFA9CC66, 0xFF5AB147, 0, false, true);
        bgOrangeRed = new MotionBackgroundDrawable(0xFFE86958, 0xFFE7618F, 0xFFDB904C, 0xFFDE7238, 0, false, true);

        bgBlueVioletDark = new MotionBackgroundDrawable(0xFFA736D0, 0xFF6A2BDD, 0xFF0F95C9, 0xFF287AE1, 0, false, true);
        bgBlueGreenDark = new MotionBackgroundDrawable(0xFF2D60D6, 0xFF2C6ADF, 0xFF009595, 0xFF0291C9, 0, false, true);
        bgGreenDark = new MotionBackgroundDrawable(0xFF008B8E, 0xFF01934C, 0xFF8FBD37, 0xFF319D27, 0, false, true);
        bgOrangeRedDark = new MotionBackgroundDrawable(0xFFE23F29, 0xFFE6306F, 0xFFC77616, 0xFFD75A16, 0, false, true);

        bgBlueVioletLight = new MotionBackgroundDrawable(0xFFD664FF, 0xFF9258FD, 0xFF2DC0F9, 0xFF57A1FF, 0, false, true);
        bgBlueGreenLight = new MotionBackgroundDrawable(0xFF558BFF, 0xFF5FABFF, 0xFF04DCCC, 0xFF28C2FF, 0, false, true);
        bgGreenLight = new MotionBackgroundDrawable(0xFF00D2D5, 0xFF09E279, 0xFFC7EF60, 0xFF6DD957, 0, false, true);
        bgOrangeRedLight = new MotionBackgroundDrawable(0xFFFF7866, 0xFFFF82A5, 0xFFFEB055, 0xFFFF8E51, 0, false, true);
        bgGreenLightReveal = new MotionBackgroundDrawable(0xFF00D2D5, 0xFF09E279, 0xFFC7EF60, 0xFF6DD957, 0, false, true);
        bgGreenDarkReveal = new MotionBackgroundDrawable(0xFF008B8E, 0xFF01934C, 0xFF8FBD37, 0xFF319D27, 0, false, true);

        bgBlueVioletDark.setBounds(0, 0, 80, 80);
        bgBlueGreenDark.setBounds(0, 0, 80, 80);
        bgGreenDark.setBounds(0, 0, 80, 80);
        bgOrangeRedDark.setBounds(0, 0, 80, 80);

        bgBlueVioletLight.setBounds(0, 0, 80, 80);
        bgBlueGreenLight.setBounds(0, 0, 80, 80);
        bgGreenLight.setBounds(0, 0, 80, 80);
        bgOrangeRedLight.setBounds(0, 0, 80, 80);

        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        defaultAnimatorSet = new AnimatorSet();
        ValueAnimator rotationAnimator = ValueAnimator.ofInt(0, 360);
        rotationAnimator.addUpdateListener(animation -> {
            backgroundProvider.setDegree((int) animation.getAnimatedValue());
            int degree = backgroundProvider.getDegree();
            if ((degree >= 0 && degree <= 2) || (degree >= 180 && degree <= 182)) {
                if (isPaused) {
                    defaultAnimatorSet.pause();
                    if (connectedAnimatorSet != null) {
                        connectedAnimatorSet.pause();
                    }
                }
            }
        });
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setRepeatMode(ValueAnimator.RESTART);
        defaultAnimatorSet.setInterpolator(new LinearInterpolator());
        defaultAnimatorSet.playTogether(rotationAnimator);
        defaultAnimatorSet.setDuration(12000);
        if (allowAnimations) {
            defaultAnimatorSet.start();
        }
        switchToCalling();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (defaultAnimatorSet != null) {
            defaultAnimatorSet.cancel();
        }
        if (connectedAnimatorSet != null) {
            connectedAnimatorSet.cancel();
        }
        if (callingAnimator != null) {
            callingAnimator.cancel();
        }
    }

    public void switchToCalling() {
        if (state == GradientState.CALLING) {
            return;
        }
        state = GradientState.CALLING;
        alphaBlueGreen = 255;
        callingAnimator = ValueAnimator.ofInt(255, 0, 255);
        callingAnimator.addUpdateListener(animation -> {
            alphaBlueViolet = (int) animation.getAnimatedValue();
            invalidate();
        });
        callingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        callingAnimator.setRepeatMode(ValueAnimator.RESTART);
        callingAnimator.setInterpolator(new LinearInterpolator());
        callingAnimator.setDuration(12000);
        if (allowAnimations) {
            callingAnimator.start();
        }
    }

    public boolean isConnectedCalled() {
        return state == GradientState.CONNECTED || state == GradientState.BAD_CONNECTION;
    }

    public void switchToCallConnected(int x, int y) {
        switchToCallConnected(x, y, true);
    }

    public void switchToCallConnected(int x, int y, boolean animated) {
        if (state == GradientState.CONNECTED || state == GradientState.BAD_CONNECTION) {
            return;
        }
        state = GradientState.CONNECTED;
        if (callingAnimator != null) {
            callingAnimator.removeAllUpdateListeners();
            callingAnimator.cancel();
            callingAnimator = null;
        }
        clipCx = x;
        clipCy = y;
        int w = AndroidUtilities.displaySize.x;
        int h = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight + AndroidUtilities.navigationBarHeight;
        double d1 = Math.sqrt((w - x) * (w - x) + (h - y) * (h - y));
        double d2 = Math.sqrt(x * x + (h - y) * (h - y));
        double d3 = Math.sqrt(x * x + y * y);
        double d4 = Math.sqrt((w - x) * (w - x) + y * y);
        double revealMaxRadius = Math.max(Math.max(Math.max(d1, d2), d3), d4);

        showClip = true;
        backgroundProvider.setReveal(true);
        ValueAnimator revealAnimator = ValueAnimator.ofFloat(0f, (float) revealMaxRadius);
        revealAnimator.addUpdateListener(animation -> {
            clipRadius = (float) animation.getAnimatedValue();
            invalidate();
            backgroundProvider.invalidateViews();
        });
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showClip = false;
                backgroundProvider.setReveal(false);
                if (allowAnimations) {
                    if (defaultAnimatorSet != null) {
                        defaultAnimatorSet.cancel();
                        defaultAnimatorSet.start();
                    }
                }
                switchToConnectedAnimator();
            }
        });
        revealAnimator.setDuration(animated ? 400 : 0);
        revealAnimator.start();
    }

    private void switchToConnectedAnimator() {
        if (connectedAnimatorSet != null) {
            return;
        }
        if (callingAnimator != null) {
            callingAnimator.removeAllUpdateListeners();
            callingAnimator.cancel();
            callingAnimator = null;
        }
        alphaGreen = 255;
        connectedAnimatorSet = new AnimatorSet();
        ValueAnimator blueGreenAnimator = ValueAnimator.ofInt(0, 255, 255, 255, 0);
        blueGreenAnimator.addUpdateListener(animation2 -> {
            alphaBlueGreen = (int) animation2.getAnimatedValue();
            invalidate();
        });
        blueGreenAnimator.setRepeatCount(ValueAnimator.INFINITE);
        blueGreenAnimator.setRepeatMode(ValueAnimator.RESTART);

        ValueAnimator blueVioletAnimator = ValueAnimator.ofInt(0, 0, 255, 0, 0);
        blueVioletAnimator.addUpdateListener(animation2 -> {
            alphaBlueViolet = (int) animation2.getAnimatedValue();
            invalidate();
        });
        blueVioletAnimator.setRepeatCount(ValueAnimator.INFINITE);
        blueVioletAnimator.setRepeatMode(ValueAnimator.RESTART);

        connectedAnimatorSet.playTogether(blueVioletAnimator, blueGreenAnimator);
        connectedAnimatorSet.setInterpolator(new LinearInterpolator());
        connectedAnimatorSet.setDuration(24000);
        if (allowAnimations) {
            connectedAnimatorSet.start();
        } else {
            alphaBlueGreen = 0;
            alphaBlueViolet = 0;
        }
        invalidate();
    }

    public void showToBadConnection() {
        if (state == GradientState.BAD_CONNECTION) {
            return;
        }
        state = GradientState.BAD_CONNECTION;
        badConnectionAnimator = ValueAnimator.ofInt(alphaOrangeRed, 255);
        badConnectionAnimator.addUpdateListener(animation -> {
            alphaOrangeRed = (int) animation.getAnimatedValue();
            invalidate();
            backgroundProvider.invalidateViews();
        });
        badConnectionAnimator.setDuration(500);
        badConnectionAnimator.start();
    }

    public void hideBadConnection() {
        if (state == GradientState.CONNECTED) {
            return;
        }
        state = GradientState.CONNECTED;
        switchToConnectedAnimator();
        if (badConnectionAnimator != null) {
            badConnectionAnimator.removeAllUpdateListeners();
            badConnectionAnimator.cancel();
        }
        badConnectionAnimator = ValueAnimator.ofInt(alphaOrangeRed, 0);
        badConnectionAnimator.addUpdateListener(animation -> {
            alphaOrangeRed = (int) animation.getAnimatedValue();
            invalidate();
            backgroundProvider.invalidateViews();
        });
        badConnectionAnimator.setDuration(500);
        badConnectionAnimator.start();
    }

    public void pause() {
        if (isPaused) {
            return;
        }
        isPaused = true;
    }

    public void resume() {
        if (!isPaused) {
            return;
        }
        isPaused = false;
        if (defaultAnimatorSet.isPaused()) {
            defaultAnimatorSet.resume();
        }
        if (connectedAnimatorSet != null && connectedAnimatorSet.isPaused()) {
            connectedAnimatorSet.resume();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        bgGreen.setBounds(0, 0, getWidth(), getHeight());
        bgOrangeRed.setBounds(0, 0, getWidth(), getHeight());
        bgBlueGreen.setBounds(0, 0, getWidth(), getHeight());
        bgBlueViolet.setBounds(0, 0, getWidth(), getHeight());
        bgGreenLightReveal.setBounds(0, 0, getWidth() / REVEAL_SCALE_FACTOR, getHeight() / REVEAL_SCALE_FACTOR);
        bgGreenDarkReveal.setBounds(0, 0, getWidth() / REVEAL_SCALE_FACTOR, getHeight() / REVEAL_SCALE_FACTOR);
        backgroundProvider.setTotalSize(getWidth(), getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (lockDrawing) {
            return;
        }
        float halfWidth = getWidth() / 2f;
        float halfHeight = getHeight() / 2f;
        canvas.save();
        canvas.scale(backgroundProvider.scale, backgroundProvider.scale, halfWidth, halfHeight);
        canvas.rotate(backgroundProvider.getDegree(), halfWidth, halfHeight);

        backgroundProvider.getLightCanvas().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        backgroundProvider.getDarkCanvas().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        if (alphaGreen != 0 && alphaOrangeRed != 255) {
            bgGreen.setAlpha(alphaGreen);
            bgGreenLight.setAlpha(alphaGreen);
            bgGreenDark.setAlpha(alphaGreen);

            bgGreen.draw(canvas);
            bgGreenLight.draw(backgroundProvider.getLightCanvas());
            bgGreenDark.draw(backgroundProvider.getDarkCanvas());
        }
        if (alphaBlueGreen != 0 && alphaOrangeRed != 255) {
            bgBlueGreen.setAlpha(alphaBlueGreen);
            bgBlueGreenDark.setAlpha(alphaBlueGreen);
            bgBlueGreenLight.setAlpha(alphaBlueGreen);

            bgBlueGreen.draw(canvas);
            bgBlueGreenDark.draw(backgroundProvider.getDarkCanvas());
            bgBlueGreenLight.draw(backgroundProvider.getLightCanvas());
        }
        if (alphaBlueViolet != 0 && alphaOrangeRed != 255) {
            bgBlueViolet.setAlpha(alphaBlueViolet);
            bgBlueVioletDark.setAlpha(alphaBlueViolet);
            bgBlueVioletLight.setAlpha(alphaBlueViolet);

            bgBlueViolet.draw(canvas);
            bgBlueVioletDark.draw(backgroundProvider.getDarkCanvas());
            bgBlueVioletLight.draw(backgroundProvider.getLightCanvas());
        }

        if (alphaOrangeRed != 0) {
            bgOrangeRed.setAlpha(alphaOrangeRed);
            bgOrangeRedDark.setAlpha(alphaOrangeRed);
            bgOrangeRedLight.setAlpha(alphaOrangeRed);

            bgOrangeRed.draw(canvas);
            bgOrangeRedDark.draw(backgroundProvider.getDarkCanvas());
            bgOrangeRedLight.draw(backgroundProvider.getLightCanvas());
        }
        canvas.restore();

        if (showClip) {
            clipPath.rewind();
            clipPath.addCircle(clipCx, clipCy, clipRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            canvas.scale(backgroundProvider.scale, backgroundProvider.scale, halfWidth, halfHeight);
            bgGreen.setAlpha(255);
            bgGreen.draw(canvas);

            clipPath.rewind();
            clipPath.addCircle(clipCx / (float) REVEAL_SCALE_FACTOR, clipCy / (float) REVEAL_SCALE_FACTOR, clipRadius / REVEAL_SCALE_FACTOR, Path.Direction.CW);
            backgroundProvider.getRevealCanvas().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            backgroundProvider.getRevealCanvas().save();
            backgroundProvider.getRevealCanvas().clipPath(clipPath);
            bgGreenLightReveal.setAlpha(255);
            bgGreenLightReveal.draw(backgroundProvider.getRevealCanvas());
            backgroundProvider.getRevealCanvas().restore();

            backgroundProvider.getRevealDrakCanvas().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            backgroundProvider.getRevealDrakCanvas().save();
            backgroundProvider.getRevealDrakCanvas().clipPath(clipPath);
            bgGreenDarkReveal.setAlpha(255);
            bgGreenDarkReveal.draw(backgroundProvider.getRevealDrakCanvas());
            backgroundProvider.getRevealDrakCanvas().restore();
        }
        super.onDraw(canvas);
    }
}
