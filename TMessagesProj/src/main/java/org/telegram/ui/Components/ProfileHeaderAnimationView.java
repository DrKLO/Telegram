/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class ProfileHeaderAnimationView extends View {

    private HeaderGiftParticleSystem particleSystem;
    private HeaderGradientController gradientController;
    private HeaderScrollResponder scrollResponder;
    
    private Paint backgroundPaint;
    private boolean isAnimating = false;
    private float animationProgress = 0f;
    private float scrollProgress = 0f;
    
    // Animation state
    private ValueAnimator mainAnimator;
    private long lastFrameTime;
    private boolean isInitialized = false;
    
    // Theme support
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean isDarkTheme = false;
    
    // Performance optimization
    private final int performanceClass;
    private boolean isLowPerformance = false;
    
    public ProfileHeaderAnimationView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.performanceClass = SharedConfig.getDevicePerformanceClass();
        this.isLowPerformance = performanceClass == SharedConfig.PERFORMANCE_CLASS_LOW;
        
        initializeComponents();
        initializePaint();
    }
    
    private void initializeComponents() {
        // Initialize particle system with performance considerations
        particleSystem = new HeaderGiftParticleSystem(getContext(), resourcesProvider);
        particleSystem.setPerformanceClass(performanceClass);
        
        // Initialize gradient controller
        gradientController = new HeaderGradientController(getContext(), resourcesProvider);
        
        // Initialize scroll responder
        scrollResponder = new HeaderScrollResponder();
        scrollResponder.setAnimationView(this);
        
        isInitialized = true;
    }
    
    private void initializePaint() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setDither(true);
        updateTheme();
    }
    
    public void updateTheme() {
        if (gradientController != null) {
            gradientController.updateTheme();
        }
        if (particleSystem != null) {
            particleSystem.updateTheme();
        }
        invalidate();
    }
    
    public void setScrollProgress(float progress) {
        this.scrollProgress = Math.max(0f, Math.min(1f, progress));
        
        if (scrollResponder != null) {
            scrollResponder.onScrollChanged(scrollProgress);
        }
        
        // Update particle system based on scroll
        if (particleSystem != null) {
            particleSystem.setScrollProgress(scrollProgress);
        }
        
        // Update animation intensity based on scroll
        updateAnimationIntensity();
        
        if (isInitialized) {
            invalidate();
        }
    }
    
    private void updateAnimationIntensity() {
        if (particleSystem != null) {
            // Increase particle generation when scrolling up (revealing header)
            float intensity = scrollProgress * 0.3f + 0.1f; // Base intensity + scroll-based boost
            particleSystem.setIntensity(intensity);
        }
    }
    
    public void startAnimation() {
        if (isAnimating || !isInitialized) return;
        
        isAnimating = true;
        lastFrameTime = System.currentTimeMillis();
        
        // Start main animation loop
        if (mainAnimator != null) {
            mainAnimator.cancel();
        }
        
        mainAnimator = ValueAnimator.ofFloat(0f, 1f);
        mainAnimator.setDuration(Long.MAX_VALUE); // Infinite duration
        mainAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        mainAnimator.addUpdateListener(animation -> {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastFrameTime) / 1000f;
            lastFrameTime = currentTime;
            
            updateAnimation(deltaTime);
            invalidate();
        });
        mainAnimator.start();
        
        // Start particle system
        if (particleSystem != null) {
            particleSystem.startAnimation();
        }
    }
    
    public void stopAnimation() {
        if (!isAnimating) return;
        
        isAnimating = false;
        
        if (mainAnimator != null) {
            mainAnimator.cancel();
            mainAnimator = null;
        }
        
        if (particleSystem != null) {
            particleSystem.stopAnimation();
        }
    }
    
    private void updateAnimation(float deltaTime) {
        if (!isAnimating) return;
        
        // Update animation progress
        animationProgress += deltaTime * 0.5f; // Adjust speed as needed
        if (animationProgress > 1f) {
            animationProgress = 0f; // Loop
        }
        
        // Update particle system
        if (particleSystem != null) {
            particleSystem.update(deltaTime);
        }
        
        // Update gradient controller
        if (gradientController != null) {
            gradientController.update(deltaTime, scrollProgress);
        }
        
        // Check performance periodically (every 2 seconds of animation time)
        if (animationProgress % 2f < deltaTime) {
            checkAndAdjustPerformance();
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        
        // Update component dimensions
        if (particleSystem != null) {
            particleSystem.setBounds(0, 0, width, height);
        }
        
        if (gradientController != null) {
            gradientController.setBounds(0, 0, width, height);
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (!isInitialized) return;
        
        int width = getWidth();
        int height = getHeight();
        
        if (width <= 0 || height <= 0) return;
        
        // Draw gradient background
        if (gradientController != null) {
            gradientController.draw(canvas, scrollProgress);
        }
        
        // Draw particle system
        if (particleSystem != null && isAnimating) {
            particleSystem.draw(canvas);
        }
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Set performance mode based on device class
        setPerformanceOptimization(performanceClass == SharedConfig.PERFORMANCE_CLASS_LOW);
        startAnimation();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }
    
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }
    
    // Public API for integration with ProfileActivity
    public void onScrollChanged(float scrollOffset, float maxScrollOffset) {
        float progress = maxScrollOffset > 0 ? scrollOffset / maxScrollOffset : 0f;
        setScrollProgress(progress);
    }
    
    public void setGiftsVisible(boolean visible) {
        if (particleSystem != null) {
            particleSystem.setGiftsVisible(visible);
        }
    }
    
    public void triggerGiftAnimation() {
        if (particleSystem != null) {
            particleSystem.triggerGiftBurst();
        }
    }
    
    public void setAnimationEnabled(boolean enabled) {
        if (enabled) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }
    
    // Performance monitoring and optimization
    public float getAverageFrameTime() {
        if (particleSystem != null) {
            return particleSystem.getAverageFrameTime();
        }
        return 0f;
    }
    
    public void setPerformanceOptimization(boolean enabled) {
        if (particleSystem != null) {
            particleSystem.setPerformanceOptimization(enabled);
        }
        if (gradientController != null) {
            gradientController.setLowPerformanceMode(enabled);
        }
        if (scrollResponder != null) {
            scrollResponder.setLowPerformanceMode(enabled);
        }
    }
    
    // Auto performance adjustment based on frame rate
    private void checkAndAdjustPerformance() {
        float avgFrameTime = getAverageFrameTime();
        if (avgFrameTime > 0) {
            // If frame time exceeds 20ms (50 FPS), enable performance optimization
            boolean shouldOptimize = avgFrameTime > 20f;
            
            if (shouldOptimize != isLowPerformance) {
                setPerformanceOptimization(shouldOptimize);
                isLowPerformance = shouldOptimize;
            }
        }
    }
}