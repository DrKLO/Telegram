/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Helper class for complex avatar animations during collapsing header
 * transitions.
 * Handles avatar scaling, translation, and transformation to black circle.
 */
public class AvatarAnimationHelper {

    // Animation configuration
    private static final float SCALE_THRESHOLD = 0.7f; // When avatar starts scaling
    private static final float DISAPPEAR_THRESHOLD = 0.95f; // When avatar disappears
    private static final float MIN_SCALE = 0.15f; // Minimum avatar scale
    private static final float BLACK_CIRCLE_THRESHOLD = 0.85f; // When avatar becomes black circle

    // Material Design animation interpolators for smooth transitions
    // Use Telegram's premium CubicBezierInterpolator for ultra-smooth animations
    private static final CubicBezierInterpolator EASE_OUT_QUINT = CubicBezierInterpolator.EASE_OUT_QUINT;
    private static final CubicBezierInterpolator EASE_OUT = CubicBezierInterpolator.EASE_OUT;

    // Animation state
    private float currentProgress = 0f;
    private float currentScale = 1f;
    private float currentTranslationX = 0f;
    private float currentTranslationY = 0f;
    private float currentAlpha = 1f;
    private boolean isBlackCircle = false;

    // Layout parameters
    private float initialX, initialY;
    private float targetX, targetY; // Position next to back button
    private float avatarSize;
    private float toolbarHeight;

    // Paint for black circle effect
    private Paint blackCirclePaint;
    private Path circlePath;
    private RectF circleRect;

    public AvatarAnimationHelper() {
        initializePaint();
    }

    private void initializePaint() {
        blackCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackCirclePaint.setColor(0xFF000000);
        blackCirclePaint.setStyle(Paint.Style.FILL);

        circlePath = new Path();
        circleRect = new RectF();
    }

    /**
     * Initialize layout parameters for animation calculations
     */
    public void initializeLayout(float initialX, float initialY, float targetX, float targetY,
            float avatarSize, float toolbarHeight) {
        this.initialX = initialX;
        this.initialY = initialY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.avatarSize = avatarSize;
        this.toolbarHeight = toolbarHeight;
    }

    /**
     * Update animation state based on scroll progress (0.0 = expanded, 1.0 =
     * collapsed)
     */
    public void updateAnimation(float progress) {
        this.currentProgress = Math.max(0f, Math.min(1f, progress));
        calculateAnimationValues();
    }

    private void calculateAnimationValues() {
        // Use smooth interpolation for ultra-smooth avatar transitions
        // Consistent with Material Design principles and Telegram's smooth animations

        if (currentProgress <= 0.1f) {
            // Start state - avatar in original position, full size, normal appearance
            currentScale = 1f;
            currentTranslationX = 0f;
            currentTranslationY = 0f;
            currentAlpha = 1f;
            isBlackCircle = false;
        } else if (currentProgress >= 0.9f) {
            // End state - avatar moved to toolbar, small black circle, then disappear
            currentScale = MIN_SCALE;
            currentTranslationX = targetX;
            currentTranslationY = targetY;
            currentAlpha = 0.3f; // Partially visible black circle
            isBlackCircle = true;
        } else {
            // Smooth interpolation zone (10% to 90% - matching text animation range)
            float transitionProgress = (currentProgress - 0.1f) / 0.8f; // 80% range for smooth movement

            // Apply ultra-smooth EASE_OUT_QUINT for silky smooth avatar motion
            float smoothProgress = EASE_OUT_QUINT.getInterpolation(transitionProgress);

            // Smooth scale transition
            currentScale = 1f - (smoothProgress * (1f - MIN_SCALE));

            // Smooth translation with perfect easing
            currentTranslationX = smoothProgress * targetX;
            currentTranslationY = smoothProgress * targetY;

            // Smooth alpha transition - start fading at 70% progress
            if (currentProgress >= 0.7f) {
                float fadeProgress = (currentProgress - 0.7f) / 0.2f; // 20% fade range
                currentAlpha = 1f - (EASE_OUT.getInterpolation(fadeProgress) * 0.7f); // Fade to 30%
                isBlackCircle = true;
            } else {
                currentAlpha = 1f;
                isBlackCircle = false;
            }
        }
    }

    /**
     * Apply calculated animations to the avatar view
     */
    public void applyAnimationToView(View avatarView) {
        if (avatarView == null)
            return;

        avatarView.setScaleX(currentScale);
        avatarView.setScaleY(currentScale);
        avatarView.setTranslationX(currentTranslationX);
        avatarView.setTranslationY(currentTranslationY);
        avatarView.setAlpha(currentAlpha);

        // Trigger redraw for black circle effect
        if (isBlackCircle) {
            avatarView.invalidate();
        }
    }

    /**
     * Draw black circle overlay when avatar is transforming
     */
    public void drawBlackCircleOverlay(Canvas canvas, View avatarView) {
        if (!isBlackCircle || avatarView == null)
            return;

        float centerX = avatarView.getX() + avatarView.getWidth() / 2f;
        float centerY = avatarView.getY() + avatarView.getHeight() / 2f;
        float radius = (avatarView.getWidth() / 2f) * currentScale;

        // Draw black circle with smooth edges
        circleRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        circlePath.reset();
        circlePath.addOval(circleRect, Path.Direction.CW);

        blackCirclePaint.setAlpha((int) (255 * currentAlpha));
        canvas.drawPath(circlePath, blackCirclePaint);
    }

    /**
     * Get current animation progress for synchronizing with other animations
     */
    public float getAnimationProgress() {
        return currentProgress;
    }

    /**
     * Get current scale value
     */
    public float getCurrentScale() {
        return currentScale;
    }

    /**
     * Get current translation X
     */
    public float getCurrentTranslationX() {
        return currentTranslationX;
    }

    /**
     * Get current translation Y
     */
    public float getCurrentTranslationY() {
        return currentTranslationY;
    }

    /**
     * Get current alpha value
     */
    public float getCurrentAlpha() {
        return currentAlpha;
    }

    /**
     * Check if avatar is currently in black circle state
     */
    public boolean isInBlackCircleState() {
        return isBlackCircle;
    }

    /**
     * Check if avatar should be visible
     */
    public boolean shouldDrawAvatar() {
        return currentAlpha > 0f && currentProgress < DISAPPEAR_THRESHOLD;
    }

    /**
     * Reset animation to initial state
     */
    public void reset() {
        currentProgress = 0f;
        currentScale = 1f;
        currentTranslationX = 0f;
        currentTranslationY = 0f;
        currentAlpha = 1f;
        isBlackCircle = false;
    }

    /**
     * Create smooth transition animator for specific progress change
     */
    public ValueAnimator createProgressAnimator(float fromProgress, float toProgress, long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(fromProgress, toProgress);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            updateAnimation(progress);
        });
        return animator;
    }
}