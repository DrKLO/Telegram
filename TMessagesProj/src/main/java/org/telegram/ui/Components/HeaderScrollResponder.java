/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class HeaderScrollResponder {
    
    private ProfileHeaderAnimationView animationView;
    private ValueAnimator smoothAnimator;
    private DecelerateInterpolator decelerateInterpolator;
    private LinearInterpolator linearInterpolator;
    
    // Animation helpers for collapsing header
    private AvatarAnimationHelper avatarAnimationHelper;
    private TextTransitionHelper textTransitionHelper;
    private HeaderButtonsAnimationHelper buttonAnimationHelper;
    
    // Scroll state
    private float currentScrollProgress = 0f;
    private float targetScrollProgress = 0f;
    private float scrollVelocity = 0f;
    private long lastScrollTime = 0;
    private boolean isScrolling = false;
    
    // Animation parameters
    private static final float SMOOTH_FACTOR = 0.1f;
    private static final float VELOCITY_THRESHOLD = 0.01f;
    private static final long SCROLL_TIMEOUT = 150; // ms
    private static final float MAX_SCROLL_PROGRESS = 1f;
    
    // Intensity control
    private float baseIntensity = 0.2f;
    private float maxIntensity = 0.8f;
    private float intensityMultiplier = 1f;
    
    public HeaderScrollResponder() {
        decelerateInterpolator = new DecelerateInterpolator(2.0f);
        linearInterpolator = new LinearInterpolator();
        lastScrollTime = System.currentTimeMillis();
    }
    
    public void setAnimationView(ProfileHeaderAnimationView animationView) {
        this.animationView = animationView;
    }
    
    /**
     * Set animation helpers for collapsing header animations
     */
    public void setAnimationHelpers(AvatarAnimationHelper avatarHelper, TextTransitionHelper textHelper) {
        this.avatarAnimationHelper = avatarHelper;
        this.textTransitionHelper = textHelper;
    }
    
    /**
     * Set button animation helper
     */
    public void setButtonAnimationHelper(HeaderButtonsAnimationHelper buttonHelper) {
        this.buttonAnimationHelper = buttonHelper;
    }
    
    public void onScrollChanged(float scrollProgress) {
        long currentTime = System.currentTimeMillis();
        
        // Clamp scroll progress
        scrollProgress = Math.max(0f, Math.min(MAX_SCROLL_PROGRESS, scrollProgress));
        
        // Calculate velocity
        float deltaProgress = scrollProgress - targetScrollProgress;
        float deltaTime = (currentTime - lastScrollTime) / 1000f;
        
        if (deltaTime > 0) {
            scrollVelocity = deltaProgress / deltaTime;
        }
        
        targetScrollProgress = scrollProgress;
        lastScrollTime = currentTime;
        isScrolling = true;
        
        // Update animation immediately for responsive feel
        updateAnimation();
        
        // Start smooth animation to target
        startSmoothAnimation();
    }
    
    private void startSmoothAnimation() {
        if (smoothAnimator != null) {
            smoothAnimator.cancel();
        }
        
        smoothAnimator = ValueAnimator.ofFloat(currentScrollProgress, targetScrollProgress);
        smoothAnimator.setDuration(200);
        smoothAnimator.setInterpolator(decelerateInterpolator);
        smoothAnimator.addUpdateListener(animation -> {
            currentScrollProgress = (Float) animation.getAnimatedValue();
            updateAnimation();
        });
        smoothAnimator.start();
    }
    
    private void updateAnimation() {
        // Update collapsing header animations
        if (avatarAnimationHelper != null) {
            avatarAnimationHelper.updateAnimation(currentScrollProgress);
        }
        
        if (textTransitionHelper != null) {
            textTransitionHelper.updateAnimation(currentScrollProgress);
        }
        
        if (buttonAnimationHelper != null) {
            buttonAnimationHelper.updateAnimation(currentScrollProgress);
        }
        
        // Update particle animation view if present
        if (animationView != null) {
            // Calculate animation intensity based on scroll progress and velocity
            float intensity = calculateIntensity();
            animationView.setScrollProgress(currentScrollProgress);
        }
        
        // Check if we should trigger special effects
        checkForSpecialEffects();
        
        // Update scrolling state
        checkScrollingState();
    }
    
    private float calculateIntensity() {
        // Base intensity from scroll progress
        float scrollIntensity = baseIntensity + (currentScrollProgress * (maxIntensity - baseIntensity));
        
        // Add velocity-based intensity boost
        float velocityBoost = Math.min(Math.abs(scrollVelocity) * 0.5f, 0.3f);
        
        // Combine intensities
        float totalIntensity = scrollIntensity + velocityBoost;
        
        // Apply multiplier and clamp
        return Math.max(0f, Math.min(1f, totalIntensity * intensityMultiplier));
    }
    
    private void checkForSpecialEffects() {
        // Trigger gift burst on rapid scroll up
        if (scrollVelocity > 2f && currentScrollProgress > 0.5f) {
            if (animationView != null) {
                animationView.triggerGiftAnimation();
            }
        }
        
        // Trigger pulse effect on scroll direction change
        if (Math.abs(scrollVelocity) > VELOCITY_THRESHOLD) {
            float previousVelocity = scrollVelocity;
            if (previousVelocity > 0 && scrollVelocity < 0 || previousVelocity < 0 && scrollVelocity > 0) {
                // Direction changed - could trigger special effect
                triggerDirectionChangeEffect();
            }
        }
    }
    
    private void triggerDirectionChangeEffect() {
        // This could trigger a subtle animation effect when scroll direction changes
        // Implementation depends on specific design requirements
    }
    
    private void checkScrollingState() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastScrollTime > SCROLL_TIMEOUT) {
            if (isScrolling) {
                isScrolling = false;
                onScrollingStopped();
            }
        }
    }
    
    private void onScrollingStopped() {
        // Reset velocity
        scrollVelocity = 0f;
        
        // Smooth animation to settle state
        if (animationView != null) {
            // Could implement a settle animation here
        }
    }
    
    // Public API for external control
    public void setIntensityMultiplier(float multiplier) {
        this.intensityMultiplier = Math.max(0f, Math.min(2f, multiplier));
    }
    
    public void setBaseIntensity(float intensity) {
        this.baseIntensity = Math.max(0f, Math.min(1f, intensity));
    }
    
    public void setMaxIntensity(float intensity) {
        this.maxIntensity = Math.max(baseIntensity, Math.min(1f, intensity));
    }
    
    public void resetAnimation() {
        if (smoothAnimator != null) {
            smoothAnimator.cancel();
            smoothAnimator = null;
        }
        
        currentScrollProgress = 0f;
        targetScrollProgress = 0f;
        scrollVelocity = 0f;
        isScrolling = false;
        
        // Reset collapsing header animations
        if (avatarAnimationHelper != null) {
            avatarAnimationHelper.reset();
        }
        
        if (textTransitionHelper != null) {
            textTransitionHelper.reset();
        }
        
        if (buttonAnimationHelper != null) {
            buttonAnimationHelper.reset();
        }
        
        if (animationView != null) {
            animationView.setScrollProgress(0f);
        }
    }
    
    public void pauseAnimation() {
        if (smoothAnimator != null) {
            smoothAnimator.pause();
        }
    }
    
    public void resumeAnimation() {
        if (smoothAnimator != null) {
            smoothAnimator.resume();
        }
    }
    
    // Getters for debugging and monitoring
    public float getCurrentScrollProgress() {
        return currentScrollProgress;
    }
    
    public float getScrollVelocity() {
        return scrollVelocity;
    }
    
    public boolean isScrolling() {
        return isScrolling;
    }
    
    public float getCurrentIntensity() {
        return calculateIntensity();
    }
    
    // Special animation triggers
    public void triggerManualGiftBurst() {
        if (animationView != null) {
            animationView.triggerGiftAnimation();
        }
    }
    
    public void setAnimationEnabled(boolean enabled) {
        if (animationView != null) {
            animationView.setAnimationEnabled(enabled);
        }
    }
    
    // Performance optimization
    public void setLowPerformanceMode(boolean enabled) {
        if (enabled) {
            // Reduce animation smoothness for better performance
            maxIntensity = 0.5f;
            baseIntensity = 0.1f;
        } else {
            // Restore full animation quality
            maxIntensity = 0.8f;
            baseIntensity = 0.2f;
        }
    }
}