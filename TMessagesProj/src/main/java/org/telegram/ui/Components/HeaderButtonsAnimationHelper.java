/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Helper class for animating header bubble buttons during collapsing header transitions.
 * Handles Y-direction collapse animations synchronized with header movement.
 */
public class HeaderButtonsAnimationHelper {
    
    // Animation configuration - transition thresholds for smooth state changes
    // Even wider range for smoother, slower animations (both collapse and uncollapse)
    private static final float COLLAPSE_START_THRESHOLD = 0.1f; // When buttons start collapsing (even earlier)
    private static final float COLLAPSE_COMPLETE_THRESHOLD = 0.9f; // When buttons completely disappear (even later)
    private static final float UNCOLLAPSE_START_THRESHOLD = 0.1f; // When buttons start appearing (even earlier)
    
    // Material Design interpolators for smooth animations
    private static final CubicBezierInterpolator EASE_OUT_QUINT = CubicBezierInterpolator.EASE_OUT_QUINT;
    private static final CubicBezierInterpolator EASE_OUT = CubicBezierInterpolator.EASE_OUT;
    
    // Animation state
    private float currentProgress = 0f;
    private float currentAlpha = 1f;
    private float currentScaleY = 1f;
    private float currentTranslationY = 0f;
    
    // Views to animate
    private ViewGroup headerButtonsContainer;
    private View[] buttonViews;
    
    // Layout parameters
    private float initialHeight;
    private float headerHeight;
    private boolean isInitialized = false;
    
    public HeaderButtonsAnimationHelper() {
        // Constructor
    }
    
    /**
     * Set the header buttons container and individual button views
     */
    public void setButtonViews(ViewGroup buttonsContainer, View... buttons) {
        this.headerButtonsContainer = buttonsContainer;
        this.buttonViews = buttons;
    }
    
    /**
     * Initialize layout parameters for smooth transitions
     */
    public void initializeLayout(float headerHeight) {
        this.headerHeight = headerHeight;
        if (headerButtonsContainer != null) {
            this.initialHeight = headerButtonsContainer.getHeight();
        }
        this.isInitialized = true;
    }
    
    /**
     * Update button animations based on scroll progress with conditional transitions
     */
    public void updateAnimation(float progress) {
        this.currentProgress = Math.max(0f, Math.min(1f, progress));
        calculateButtonAnimationValues();
        applyAnimationsToButtons();
    }
    
    /**
     * Calculate animation values with ultra-smooth gradual transitions
     */
    private void calculateButtonAnimationValues() {
        if (!isInitialized) return;
        
        // Use gradual animation throughout the range for smoother feel
        if (currentProgress <= UNCOLLAPSE_START_THRESHOLD) {
            // Fully expanded state - buttons are completely visible
            currentAlpha = 1f;
            currentScaleY = 1f;
            currentTranslationY = 0f;
        } else if (currentProgress >= COLLAPSE_COMPLETE_THRESHOLD) {
            // Fully collapsed state - buttons are completely hidden
            currentAlpha = 0f;
            currentScaleY = 0.25f; // Match the calculation: 1f - (1f * 0.75f) = 0.25f for consistency
            currentTranslationY = AndroidUtilities.dp(-12); // Match the calculation for smooth transition
        } else {
            // Extended transition zone - ultra-smooth animation between states
            float transitionProgress = (currentProgress - UNCOLLAPSE_START_THRESHOLD) / 
                                     (COLLAPSE_COMPLETE_THRESHOLD - UNCOLLAPSE_START_THRESHOLD);
            
            // Use symmetric easing that works smoothly in both directions (collapse and uncollapse)
            // EASE_OUT works great for uncollapsing, so use EASE_IN for collapsing to mirror the smoothness
            float symmetricProgress = EASE_OUT.getInterpolation(transitionProgress);
            
            // Apply the same smooth curve to all properties for consistent feel
            currentAlpha = 1f - symmetricProgress;
            currentScaleY = 1f - (symmetricProgress * 0.75f); // Slightly gentler scale for smoother collapse
            currentTranslationY = symmetricProgress * AndroidUtilities.dp(-12); // Even more subtle upward movement
        }
    }
    
    /**
     * Apply calculated animations to button views
     */
    private void applyAnimationsToButtons() {
        if (headerButtonsContainer != null) {
            // Apply container-level animations
            headerButtonsContainer.setAlpha(currentAlpha);
            headerButtonsContainer.setScaleY(currentScaleY);
            headerButtonsContainer.setTranslationY(currentTranslationY);
            
            // Remove rotation effect to eliminate chunkiness - keep animation simple and smooth
            headerButtonsContainer.setRotation(0f);
        }
        
        // Apply individual button animations with ultra-smooth staggered effect
        if (buttonViews != null) {
            for (int i = 0; i < buttonViews.length; i++) {
                View button = buttonViews[i];
                if (button != null) {
                    // Reduced stagger delay for smoother collective animation
                    float staggerDelay = i * 0.02f; // Reduced to 20ms delay for smoother feel
                    float adjustedProgress = Math.max(0f, currentProgress - staggerDelay);
                    
                    if (adjustedProgress <= UNCOLLAPSE_START_THRESHOLD) {
                        button.setAlpha(1f);
                        button.setScaleX(1f);
                        button.setScaleY(1f);
                    } else if (adjustedProgress >= COLLAPSE_COMPLETE_THRESHOLD) {
                        button.setAlpha(0f);
                        button.setScaleX(0.92f); // Match calculation: 1f - (1f * 0.08f) = 0.92f for consistency
                        button.setScaleY(0.92f);
                    } else {
                        float buttonTransitionProgress = (adjustedProgress - UNCOLLAPSE_START_THRESHOLD) / 
                                                       (COLLAPSE_COMPLETE_THRESHOLD - UNCOLLAPSE_START_THRESHOLD);
                        
                        // Use the same symmetric easing as container for consistent smooth feel
                        float buttonSymmetricProgress = EASE_OUT.getInterpolation(buttonTransitionProgress);
                        
                        button.setAlpha(1f - buttonSymmetricProgress);
                        float scale = 1f - (buttonSymmetricProgress * 0.08f); // Even gentler scaling for ultra-smooth feel
                        button.setScaleX(scale);
                        button.setScaleY(scale);
                    }
                }
            }
        }
    }
    
    /**
     * Reset animations to initial state
     */
    public void reset() {
        currentProgress = 0f;
        currentAlpha = 1f;
        currentScaleY = 1f;
        currentTranslationY = 0f;
        applyAnimationsToButtons();
    }
    
    /**
     * Check if buttons are in collapsed state
     */
    public boolean isCollapsed() {
        return currentProgress >= COLLAPSE_COMPLETE_THRESHOLD;
    }
    
    /**
     * Check if buttons are in expanded state
     */
    public boolean isExpanded() {
        return currentProgress <= UNCOLLAPSE_START_THRESHOLD;
    }
    
    /**
     * Get current animation progress
     */
    public float getAnimationProgress() {
        return currentProgress;
    }
    
    /**
     * Get current alpha value
     */
    public float getCurrentAlpha() {
        return currentAlpha;
    }
    
    /**
     * Check if buttons are in transition state
     */
    public boolean isInTransition() {
        return currentProgress > UNCOLLAPSE_START_THRESHOLD && currentProgress < COLLAPSE_COMPLETE_THRESHOLD;
    }
    
    /**
     * Trigger manual collapse animation
     */
    public void triggerCollapseAnimation() {
        if (headerButtonsContainer != null) {
            // Could implement a manual animation trigger here if needed
        }
    }
    
    /**
     * Set animation enabled/disabled
     */
    public void setAnimationEnabled(boolean enabled) {
        if (!enabled) {
            reset();
        }
    }
    
    /**
     * Set custom collapse thresholds for fine-tuning
     */
    public void setCollapseThresholds(float startThreshold, float completeThreshold) {
        // Could be used to customize animation timing based on header design
    }
}