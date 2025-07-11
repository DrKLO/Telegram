/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Helper class for smooth text transitions during collapsing header animations.
 * Manages dual text views (expanded and collapsed) with synchronized fade
 * transitions.
 */
public class TextTransitionHelper {

    // Animation configuration
    private static final float FADE_START_THRESHOLD = 0.3f; // When expanded text starts fading
    private static final float FADE_END_THRESHOLD = 0.7f; // When collapsed text appears
    private static final float TRANSLATION_THRESHOLD = 0.8f; // When text translation completes

    // Material Design animation interpolators for smooth text transitions
    private final AccelerateInterpolator fadeOutInterpolator = new AccelerateInterpolator(2f);
    private final DecelerateInterpolator fadeInInterpolator = new DecelerateInterpolator(2f);
    private final DecelerateInterpolator translationInterpolator = new DecelerateInterpolator(2.0f); // Smoother
                                                                                                     // translation

    // Text views
    private TextView expandedNameView;
    private TextView expandedStatusView;
    private TextView collapsedNameView;
    private TextView collapsedStatusView;

    // Animation state
    private float currentProgress = 0f;
    private float expandedAlpha = 1f;
    private float collapsedAlpha = 1f; // always visible for now
    private float nameTranslationX = 0f;
    private float nameTranslationY = 0f;
    private float statusTranslationX = 0f;
    private float statusTranslationY = 0f;
    private float nameScale = 1f;
    private float statusScale = 1f;

    // Layout parameters
    private float initialNameX, initialNameY;
    private float initialStatusX, initialStatusY;
    private float targetNameX, targetNameY;
    private float targetStatusX, targetStatusY;

    public TextTransitionHelper() {
        // Constructor
    }

    /**
     * Set the text views for animation
     */
    public void setTextViews(TextView expandedName, TextView expandedStatus,
            TextView collapsedName, TextView collapsedStatus) {
        this.expandedNameView = expandedName;
        this.expandedStatusView = expandedStatus;
        this.collapsedNameView = collapsedName;
        this.collapsedStatusView = collapsedStatus;

        // Initialize collapsed views as not invisible
        if (collapsedNameView != null) {
            collapsedNameView.setAlpha(1f);
        }
        if (collapsedStatusView != null) {
            collapsedStatusView.setAlpha(1f);
        }
    }

    /**
     * Initialize layout parameters for smooth transitions
     */
    public void initializeLayout(float initialNameX, float initialNameY, float initialStatusX, float initialStatusY,
            float targetNameX, float targetNameY, float targetStatusX, float targetStatusY) {
        this.initialNameX = initialNameX;
        this.initialNameY = initialNameY;
        this.initialStatusX = initialStatusX;
        this.initialStatusY = initialStatusY;
        this.targetNameX = targetNameX;
        this.targetNameY = targetNameY;
        this.targetStatusX = targetStatusX;
        this.targetStatusY = targetStatusY;
    }

    /**
     * Update text animations based on scroll progress (0.0 = expanded, 1.0 =
     * collapsed)
     */
    public void updateAnimation(float progress) {
        this.currentProgress = Math.max(0f, Math.min(1f, progress));
        calculateTextAnimationValues();
        applyAnimationsToViews();
    }

    /**
     * Apply Material Design easing curve for smooth animations
     */
    private float applyMaterialEasing(float progress) {
        // Use Telegram's ultra-smooth CubicBezierInterpolator for perfect text
        // transitions
        // EASE_OUT provides smooth deceleration without stuttering
        return CubicBezierInterpolator.EASE_OUT.getInterpolation(progress);
    }

    private void calculateTextAnimationValues() {
        // Implement actual translation movement instead of fade effects
        // Text should visually move from center to toolbar position with "left upward
        // motion"

        // Keep text visible throughout animation - no fading
        expandedAlpha = 1f; // Always visible during translation
        collapsedAlpha = 0f; // Keep collapsed hidden, use translated expanded text

        // Apply the same smooth technique as button animations for consistent feel
        // Extended range for slower, more visible left upward motion
        if (currentProgress <= 0.1f) {
            // Still in center position - no translation yet (same as button start
            // threshold)
            nameTranslationX = 0f;
            nameTranslationY = 0f;
            statusTranslationX = 0f;
            statusTranslationY = 0f;
            nameScale = 1f;
            statusScale = 1f;
        } else if (currentProgress >= 0.9f) {
            // Fully translated to toolbar position - text should be visible next to back
            // button
            nameTranslationX = targetNameX;
            nameTranslationY = targetNameY;
            statusTranslationX = targetStatusX;
            statusTranslationY = targetStatusY;
            nameScale = 0.92f; // Match button scaling for consistency
            statusScale = 0.92f;
        } else {
            // Extended gradual translation zone (10% to 90% - matching button animation
            // range)
            float transitionProgress = (currentProgress - 0.1f) / 0.8f; // 80% range for smooth movement

            // Use exact same interpolation as avatar animation for perfect consistency
            float symmetricProgress = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(transitionProgress);

            // Apply EXACT same smooth left upward motion for BOTH username and status
            nameTranslationX = symmetricProgress * targetNameX;
            nameTranslationY = symmetricProgress * targetNameY;

            // Apply EXACT same technique to status - no stagger, same smooth movement
            statusTranslationX = symmetricProgress * targetStatusX;
            statusTranslationY = symmetricProgress * targetStatusY;

            // Apply EXACT same gentle scaling to both - no differences
            nameScale = 1f - (symmetricProgress * 0.08f); // Same gentle scaling as individual buttons
            statusScale = 1f - (symmetricProgress * 0.08f); // Identical to name scaling
        }
    }

    private void applyAnimationsToViews() {
        // Apply EXACT same animations to expanded text views - ensure both behave
        // identically
        if (expandedNameView != null) {
            // ALWAYS fully visible - no alpha transitions ever
            expandedNameView.setAlpha(1f);
            expandedNameView.setTranslationX(nameTranslationX);
            expandedNameView.setTranslationY(nameTranslationY);
            expandedNameView.setScaleX(nameScale);
            expandedNameView.setScaleY(nameScale);

            // COMPLETELY disable ellipsize to prevent any text clipping/vanishing
            expandedNameView.setEllipsize(null);
            expandedNameView.setMaxLines(1);
            expandedNameView.setHorizontallyScrolling(true);
            // Ensure text stays visible by preventing any layout constraints
            expandedNameView.setSingleLine(false);
        }

        if (expandedStatusView != null) {
            // ALWAYS fully visible - no alpha transitions ever (identical to username)
            expandedStatusView.setAlpha(1f);
            expandedStatusView.setTranslationX(statusTranslationX);
            expandedStatusView.setTranslationY(statusTranslationY);
            expandedStatusView.setScaleX(statusScale);
            expandedStatusView.setScaleY(statusScale);

            // COMPLETELY disable ellipsize to prevent any text clipping/vanishing
            // (identical to username)
            expandedStatusView.setEllipsize(null);
            expandedStatusView.setMaxLines(1);
            expandedStatusView.setHorizontallyScrolling(true);
            // Ensure text stays visible by preventing any layout constraints
            expandedStatusView.setSingleLine(false);
        }

        // Keep collapsed text views hidden - we use translated expanded text instead
        if (collapsedNameView != null) {
            collapsedNameView.setAlpha(collapsedAlpha); // Always 0f - keep hidden
        }

        if (collapsedStatusView != null) {
            collapsedStatusView.setAlpha(collapsedAlpha); // Always 0f - keep hidden
        }
    }

    /**
     * Set text content for both expanded and collapsed views
     */
    public void setText(CharSequence name, CharSequence status) {
        if (expandedNameView != null) {
            expandedNameView.setText(name);
        }
        if (expandedStatusView != null) {
            expandedStatusView.setText(status);
        }
        if (collapsedNameView != null) {
            collapsedNameView.setText(name);
        }
        if (collapsedStatusView != null) {
            collapsedStatusView.setText(status);
        }
    }

    /**
     * Get current animation progress
     */
    public float getAnimationProgress() {
        return currentProgress;
    }

    /**
     * Get current expanded text alpha
     */
    public float getExpandedAlpha() {
        return expandedAlpha;
    }

    /**
     * Get current collapsed text alpha
     */
    public float getCollapsedAlpha() {
        return collapsedAlpha;
    }

    /**
     * Check if expanded text is visible
     */
    public boolean isExpandedTextVisible() {
        return expandedAlpha > 0f;
    }

    /**
     * Check if collapsed text is visible
     */
    public boolean isCollapsedTextVisible() {
        return collapsedAlpha > 0f;
    }

    /**
     * Reset animation to initial state
     */
    public void reset() {
        currentProgress = 0f;
        expandedAlpha = 1f;
        collapsedAlpha = 0f;
        nameTranslationX = 0f;
        nameTranslationY = 0f;
        statusTranslationX = 0f;
        statusTranslationY = 0f;
        nameScale = 1f;
        statusScale = 1f;
        applyAnimationsToViews();
    }

    /**
     * Create smooth transition animator for text animations
     */
    public ValueAnimator createTextAnimator(float fromProgress, float toProgress, long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(fromProgress, toProgress);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            updateAnimation(progress);
        });
        return animator;
    }

    /**
     * Apply custom text colors during animation
     */
    public void setTextColors(int expandedColor, int collapsedColor) {
        if (expandedNameView != null) {
            expandedNameView.setTextColor(expandedColor);
        }
        if (expandedStatusView != null) {
            expandedStatusView.setTextColor(expandedColor);
        }
        if (collapsedNameView != null) {
            collapsedNameView.setTextColor(collapsedColor);
        }
        if (collapsedStatusView != null) {
            collapsedStatusView.setTextColor(collapsedColor);
        }
    }

    /**
     * Handle text size changes during animation
     */
    public void setTextSizes(float expandedNameSize, float expandedStatusSize,
            float collapsedNameSize, float collapsedStatusSize) {
        if (expandedNameView != null) {
            expandedNameView.setTextSize(expandedNameSize);
        }
        if (expandedStatusView != null) {
            expandedStatusView.setTextSize(expandedStatusSize);
        }
        if (collapsedNameView != null) {
            collapsedNameView.setTextSize(collapsedNameSize);
        }
        if (collapsedStatusView != null) {
            collapsedStatusView.setTextSize(collapsedStatusSize);
        }
    }
}