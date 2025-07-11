/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.google.android.material.appbar.AppBarLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Custom AppBarLayout.OnOffsetChangedListener that coordinates sophisticated
 * collapsing header animations.
 * Handles avatar scaling/translation and dual text view transitions based on
 * scroll progress.
 */
public class CollapsingHeaderOffsetListener implements AppBarLayout.OnOffsetChangedListener {

    // Animation helpers
    private AvatarAnimationHelper avatarAnimationHelper;
    private TextTransitionHelper textTransitionHelper;
    private HeaderButtonsAnimationHelper buttonAnimationHelper;
    private HeaderScrollResponder scrollResponder;

    // Status bar color callback for collapse animation
    private StatusBarColorCallback statusBarColorCallback;

    // Views to animate
    private View avatarView;
    private View expandedHeaderContainer;
    private View collapsedTitleContainer;
    private View nameView;
    private View statusView;
    private View headerButtonsContainer;

    // Layout parameters
    private int totalScrollRange = 0;
    private float previousProgress = -1f;
    private boolean isInitialized = false;

    // Material Design animation thresholds for conditional smooth transitions
    private static final float AVATAR_TRANSITION_THRESHOLD = 0.75f; // Avatar snaps to collapsed state
    private static final float TEXT_TRANSITION_THRESHOLD = 0.65f; // Text snaps to toolbar position
    private static final float BUTTON_COLLAPSE_START = 0.3f; // Buttons start collapsing
    private static final float BUTTON_COLLAPSE_COMPLETE = 0.7f; // Buttons completely hidden

    // Legacy thresholds for reference
    private static final float AVATAR_ANIMATION_START = 0.1f; // Start avatar animation early
    private static final float TEXT_FADE_START = 0.0f; // Start text animation immediately
    private static final float COLLAPSED_TEXT_SHOW = 0.8f; // Show collapsed text late (but we don't use this)
    private static final float HEADER_BUTTONS_FADE = 0.4f; // Fade buttons mid-way

    // 60fps performance optimization - Material Design standard
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 16; // 16ms = ~60fps

    // Material Design interpolators for smooth animations - using Telegram's proven
    // patterns
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(2.0f);
    private final AccelerateDecelerateInterpolator smoothInterpolator = new AccelerateDecelerateInterpolator();
    // Telegram's premium interpolators for ultra-smooth animations
    private static final CubicBezierInterpolator EASE_OUT_QUINT = CubicBezierInterpolator.EASE_OUT_QUINT;
    private static final CubicBezierInterpolator EASE_OUT = CubicBezierInterpolator.EASE_OUT;

    /**
     * Interface for status bar color callbacks during collapse animation
     */
    public interface StatusBarColorCallback {
        void onCollapseProgressChanged(float progress);
    }

    public CollapsingHeaderOffsetListener() {
        // Constructor
    }

    /**
     * Set animation helpers for coordinated animations
     */
    public void setAnimationHelpers(AvatarAnimationHelper avatarHelper, TextTransitionHelper textHelper,
            HeaderButtonsAnimationHelper buttonHelper) {
        this.avatarAnimationHelper = avatarHelper;
        this.textTransitionHelper = textHelper;
        this.buttonAnimationHelper = buttonHelper;
    }

    /**
     * Set scroll responder for smooth animation coordination
     */
    public void setScrollResponder(HeaderScrollResponder scrollResponder) {
        this.scrollResponder = scrollResponder;
    }

    /**
     * Set status bar color callback for dynamic color changes during collapse
     */
    public void setStatusBarColorCallback(StatusBarColorCallback callback) {
        this.statusBarColorCallback = callback;
    }

    /**
     * Set views that will be animated during collapse/expand
     */
    public void setAnimatedViews(View avatarView, View expandedContainer, View collapsedContainer,
            View buttonsContainer) {
        this.avatarView = avatarView;
        this.expandedHeaderContainer = expandedContainer;
        this.collapsedTitleContainer = collapsedContainer;
        this.headerButtonsContainer = buttonsContainer;
    }

    /**
     * Set individual text views for accurate positioning
     */
    public void setTextViews(View nameView, View statusView) {
        this.nameView = nameView;
        this.statusView = statusView;
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        // Performance optimization - limit update frequency for 60fps
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTime = currentTime;

        // Initialize if needed
        if (!isInitialized) {
            initializeScrollRange(appBarLayout);
        }

        // Calculate scroll progress using Material Design standard calculation
        float scrollProgress = calculateMaterialScrollProgress(verticalOffset);

        // Only update if progress has changed significantly to avoid unnecessary
        // redraws
        if (Math.abs(scrollProgress - previousProgress) < 0.001f) {
            return;
        }
        previousProgress = scrollProgress;

        // Update all animations with smooth Material Design timing
        updateAnimationsWithMaterialTiming(scrollProgress, verticalOffset, appBarLayout);
    }

    private void initializeScrollRange(AppBarLayout appBarLayout) {
        totalScrollRange = appBarLayout.getTotalScrollRange();

        // Wait for layout to be ready before calculating positions
        appBarLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                appBarLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Initialize animation helpers with layout parameters after layout is ready
                if (avatarAnimationHelper != null && avatarView != null) {
                    initializeAvatarAnimation();
                }

                if (textTransitionHelper != null) {
                    initializeTextAnimation();
                }
            }
        });

        isInitialized = true;
    }

    private void initializeAvatarAnimation() {
        // Avatar should move only UPWARD and very slowly
        // Remove leftward movement and reduce upward movement for slower animation

        float leftMovement = 0f; // No leftward movement
        float upwardMovement = AndroidUtilities.dp(-50); // Even smaller upward movement for very slow animation

        float avatarSize = AndroidUtilities.dp(84); // Standard avatar size
        float toolbarHeight = AndroidUtilities.dp(56); // Standard toolbar height

        // Initialize with simple relative movements - no complex calculations
        avatarAnimationHelper.initializeLayout(
                0f, 0f, // Initial position (relative)
                leftMovement, upwardMovement, // Target movement (only UP, no left)
                avatarSize, toolbarHeight);
    }

    private void initializeTextAnimation() {
        // Text should move to the exact position next to the back button in the action
        // bar
        // Calculate proper position for text to end up next to back button

        // Move text to the left to position next to back button (back button is
        // typically at left edge)
        float leftMovement = AndroidUtilities.dp(-280); // Move further left to reach back button area
        float upwardMovement = AndroidUtilities.dp(-200); // Move up to action bar level

        // Initialize with proper positioning for action bar placement
        textTransitionHelper.initializeLayout(
                0f, 0f, 0f, AndroidUtilities.dp(20), // Initial positions (relative)
                leftMovement, upwardMovement, // Name movement (left and up to action bar)
                leftMovement, upwardMovement - AndroidUtilities.dp(15) // Status movement (left and up, slightly higher)
        );
    }

    private float calculateMaterialScrollProgress(int verticalOffset) {
        if (totalScrollRange == 0) {
            return 0f;
        }

        // Material Design standard: Convert negative offset to positive progress
        // (0.0-1.0)
        // verticalOffset is negative when scrolling up, so we use Math.abs
        float progress = Math.abs((float) verticalOffset) / (float) totalScrollRange;

        // Ensure progress stays within bounds and apply smooth curve
        progress = Math.max(0f, Math.min(1f, progress));

        // Apply Material Design easing for smoother visual progression
        // This creates a more natural feeling scroll animation
        return applyMaterialEasing(progress);
    }

    private float applyMaterialEasing(float progress) {
        // Use Telegram's premium CubicBezierInterpolator for ultra-smooth animations
        // EASE_OUT_QUINT provides the smoothest deceleration used throughout Telegram
        return EASE_OUT_QUINT.getInterpolation(progress);
    }

    private void updateAnimationsWithMaterialTiming(float scrollProgress, int verticalOffset,
            AppBarLayout appBarLayout) {
        // Update scroll responder for coordinated animations with smooth timing
        if (scrollResponder != null) {
            scrollResponder.onScrollChanged(scrollProgress);
        }

        // Update avatar animation with conditional transitions
        if (avatarAnimationHelper != null && avatarView != null) {
            updateAvatarAnimationWithConditionalTransitions(scrollProgress);
        }

        // Update text transition with conditional state switching
        if (textTransitionHelper != null) {
            updateTextAnimationWithConditionalTransitions(scrollProgress);
        }

        // Update header buttons animation with smooth Y-direction collapse
        if (buttonAnimationHelper != null && headerButtonsContainer != null) {
            updateButtonAnimationsWithTiming(scrollProgress);
        }

        // Update header container animations with proper timing
        updateHeaderContainerAnimationsWithTiming(scrollProgress);

        // Update additional view animations
        updateAdditionalAnimationsWithTiming(scrollProgress);

        // Update status bar color for collapse animation
        if (statusBarColorCallback != null) {
            statusBarColorCallback.onCollapseProgressChanged(scrollProgress);
        }
    }

    private void updateAvatarAnimationWithConditionalTransitions(float scrollProgress) {
        // Make avatar animate only when collapsing header is near it
        // Synchronize with text animation timing

        if (scrollProgress <= 0.7f) {
            // Avatar stays in original position until collapsing header is very near
            avatarAnimationHelper.updateAnimation(0f);
        } else if (scrollProgress >= 0.98f) {
            // Collapsed state - avatar is in toolbar position
            avatarAnimationHelper.updateAnimation(1f);
        } else {
            // Ultra-slow animation zone (70% to 98% - synchronized with text)
            float transitionProgress = (scrollProgress - 0.7f) / 0.28f;
            // Apply very slow EASE_OUT for ultra-slow avatar motion
            float smoothProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(transitionProgress);
            avatarAnimationHelper.updateAnimation(smoothProgress);
        }

        avatarAnimationHelper.applyAnimationToView(avatarView);
    }

    private void updateTextAnimationWithConditionalTransitions(float scrollProgress) {
        // Use gradual transition for text translation movement
        // This allows users to see the "left upward motion" as requested

        if (textTransitionHelper != null) {
            // Pass the actual scroll progress for gradual translation animation
            textTransitionHelper.updateAnimation(scrollProgress);
        }
    }

    private void updateButtonAnimationsWithTiming(float scrollProgress) {
        // Update header buttons with Y-direction collapse animation synchronized with
        // header
        if (buttonAnimationHelper != null) {
            buttonAnimationHelper.updateAnimation(scrollProgress);
        }
    }

    private void updateHeaderContainerAnimationsWithTiming(float scrollProgress) {
        // Fade out expanded header container with smooth timing
        if (expandedHeaderContainer != null) {
            float expandedAlpha = 1f - applyMaterialEasing(scrollProgress);
            expandedHeaderContainer.setAlpha(Math.max(0f, expandedAlpha));

            // Apply subtle scale animation with smooth easing
            float scaleProgress = applyMaterialEasing(scrollProgress);
            float scale = 1f - (scaleProgress * 0.05f); // Slight scale down
            expandedHeaderContainer.setScaleX(scale);
            expandedHeaderContainer.setScaleY(scale);
        }

        // DO NOT fade in collapsed title container - let text translate smoothly
        // instead
        // The text will be handled by the TextTransitionHelper for smooth translation
        if (collapsedTitleContainer != null) {
            // Keep collapsed container fully transparent since we're using smooth text
            // translation
            collapsedTitleContainer.setAlpha(0f);
        }
    }

    private void updateAdditionalAnimationsWithTiming(float scrollProgress) {
        // Handle header buttons fade out with smooth Material Design timing
        if (scrollProgress >= HEADER_BUTTONS_FADE) {
            // Apply smooth easing to button fade animation
            float buttonFadeProgress = (scrollProgress - HEADER_BUTTONS_FADE) / (1f - HEADER_BUTTONS_FADE);
            float easedProgress = applyMaterialEasing(buttonFadeProgress);
            // Button fade implementation would go here based on specific layout
            // requirements
        }

        // Add any additional animations with Material Design timing
        // For example: background color transitions, elevation changes, etc.
    }

    /**
     * Get current scroll progress for external use
     */
    public float getCurrentScrollProgress() {
        return previousProgress;
    }

    /**
     * Check if header is in collapsed state
     */
    public boolean isCollapsed() {
        return previousProgress >= 0.9f;
    }

    /**
     * Check if header is in expanded state
     */
    public boolean isExpanded() {
        return previousProgress <= 0.1f;
    }

    /**
     * Reset all animations to initial state
     */
    public void resetAnimations() {
        previousProgress = 0f;

        if (avatarAnimationHelper != null) {
            avatarAnimationHelper.reset();
        }

        if (textTransitionHelper != null) {
            textTransitionHelper.reset();
        }

        if (buttonAnimationHelper != null) {
            buttonAnimationHelper.reset();
        }

        if (scrollResponder != null) {
            scrollResponder.resetAnimation();
        }

        // Reset view states
        if (avatarView != null) {
            avatarView.setScaleX(1f);
            avatarView.setScaleY(1f);
            avatarView.setTranslationX(0f);
            avatarView.setTranslationY(0f);
            avatarView.setAlpha(1f);
        }

        if (expandedHeaderContainer != null) {
            expandedHeaderContainer.setAlpha(1f);
            expandedHeaderContainer.setScaleX(1f);
            expandedHeaderContainer.setScaleY(1f);
        }

        if (collapsedTitleContainer != null) {
            collapsedTitleContainer.setAlpha(0f);
        }
    }

    /**
     * Enable or disable animations for performance
     */
    public void setAnimationsEnabled(boolean enabled) {
        if (!enabled) {
            // Disable animations and reset to neutral state
            resetAnimations();
        }
    }

    /**
     * Set animation quality for performance optimization
     */
    public void setAnimationQuality(float quality) {
        // quality: 0.0 = lowest, 1.0 = highest
        // Could be used to reduce animation complexity on low-end devices
        quality = Math.max(0f, Math.min(1f, quality));

        // Adjust update interval based on quality
        // Lower quality = less frequent updates
    }
}