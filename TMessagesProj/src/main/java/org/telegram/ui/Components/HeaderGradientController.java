/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class HeaderGradientController {
    
    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint gradientPaint;
    private final Paint overlayPaint;
    private final Matrix gradientMatrix;
    
    // Gradient properties
    private LinearGradient baseGradient;
    private RadialGradient accentGradient;
    private int[] gradientColors;
    private int[] accentColors;
    private float[] gradientPositions;
    
    // Animation state
    private float animationTime = 0f;
    private float scrollProgress = 0f;
    private boolean isDarkTheme = false;
    
    // Bounds
    private int boundsWidth = 0;
    private int boundsHeight = 0;
    
    // Theme colors
    private int baseColor;
    private int accentColor;
    private int overlayColor;
    
    public HeaderGradientController(Context context, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
        
        // Initialize paint objects
        gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setDither(true);
        
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setDither(true);
        
        gradientMatrix = new Matrix();
        
        // Initialize gradient positions
        gradientPositions = new float[]{0f, 0.3f, 0.7f, 1f};
        
        updateTheme();
    }
    
    public void updateTheme() {
        isDarkTheme = Theme.isCurrentThemeDark();
        
        // Get theme colors
        baseColor = getThemedColor(Theme.key_profile_headerAnimationGradientStart);
        accentColor = getThemedColor(Theme.key_profile_headerAnimationGradientEnd);
        overlayColor = getThemedColor(Theme.key_profile_headerAnimationOverlay);
        
        // Create color arrays for gradients
        if (isDarkTheme) {
            gradientColors = new int[]{
                Color.argb(180, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.argb(80, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            };
            
            int accentThemeColor = getThemedColor(Theme.key_profile_headerAnimationAccent);
            accentColors = new int[]{
                Color.argb(60, Color.red(accentThemeColor), Color.green(accentThemeColor), Color.blue(accentThemeColor)),
                Color.argb(20, Color.red(accentThemeColor), Color.green(accentThemeColor), Color.blue(accentThemeColor)),
                Color.argb(0, Color.red(accentThemeColor), Color.green(accentThemeColor), Color.blue(accentThemeColor))
            };
        } else {
            gradientColors = new int[]{
                Color.argb(220, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                Color.argb(160, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.argb(100, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),
                Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            };
            
            int accentThemeColor = getThemedColor(Theme.key_profile_headerAnimationAccent);
            accentColors = new int[]{
                Color.argb(80, Color.red(accentThemeColor), Color.green(accentThemeColor), Color.blue(accentThemeColor)),
                Color.argb(40, Color.red(accentThemeColor), Color.green(accentThemeColor), Color.blue(accentThemeColor)),
                Color.argb(0, Color.red(accentThemeColor), Color.green(accentThemeColor), Color.blue(accentThemeColor))
            };
        }
        
        // Recreate gradients if bounds are set
        if (boundsWidth > 0 && boundsHeight > 0) {
            createGradients();
        }
    }
    
    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
    
    public void setBounds(int left, int top, int right, int bottom) {
        this.boundsWidth = right - left;
        this.boundsHeight = bottom - top;
        
        if (boundsWidth > 0 && boundsHeight > 0) {
            createGradients();
        }
    }
    
    private void createGradients() {
        // Validate arrays before creating gradients
        if (gradientColors == null || gradientPositions == null || 
            gradientColors.length != gradientPositions.length) {
            return;
        }
        
        // Create base gradient (vertical)
        baseGradient = new LinearGradient(
            0, 0, 0, boundsHeight,
            gradientColors,
            gradientPositions,
            Shader.TileMode.CLAMP
        );
        
        // Create accent gradient (radial, centered at top)
        if (accentColors != null && accentColors.length > 0) {
            float centerX = boundsWidth / 2f;
            float centerY = boundsHeight * 0.3f;
            float radius = Math.max(boundsWidth, boundsHeight) * 0.8f;
            
            float[] accentPositions;
            if (accentColors.length == 2) {
                // Low performance mode - use 2 colors
                accentPositions = new float[]{0f, 1f};
            } else {
                // Normal mode - use 3 colors
                accentPositions = new float[]{0f, 0.5f, 1f};
            }
            
            accentGradient = new RadialGradient(
                centerX, centerY, radius,
                accentColors,
                accentPositions,
                Shader.TileMode.CLAMP
            );
        }
    }
    
    public void update(float deltaTime, float scrollProgress) {
        this.animationTime += deltaTime;
        this.scrollProgress = scrollProgress;
        
        // Update gradient transformations based on scroll and animation
        updateGradientTransformation();
    }
    
    private void updateGradientTransformation() {
        if (baseGradient == null || accentGradient == null) return;
        
        // Reset matrix
        gradientMatrix.reset();
        
        // Apply scroll-based transformation
        float scrollOffset = scrollProgress * boundsHeight * 0.1f;
        
        // Apply subtle animation movement
        float animationOffset = (float) (Math.sin(animationTime * 0.5f) * AndroidUtilities.dp(10));
        
        // Apply transformations
        gradientMatrix.postTranslate(animationOffset, scrollOffset);
        
        // Apply matrix to gradients
        baseGradient.setLocalMatrix(gradientMatrix);
        accentGradient.setLocalMatrix(gradientMatrix);
    }
    
    public void draw(Canvas canvas, float scrollProgress) {
        if (boundsWidth <= 0 || boundsHeight <= 0) return;
        
        // Update scroll progress
        this.scrollProgress = scrollProgress;
        
        // Calculate alpha based on scroll progress
        float baseAlpha = 0.8f + (scrollProgress * 0.2f);
        float accentAlpha = 0.3f + (scrollProgress * 0.4f);
        
        // Draw base gradient
        if (baseGradient != null) {
            gradientPaint.setShader(baseGradient);
            gradientPaint.setAlpha((int) (255 * baseAlpha));
            canvas.drawRect(0, 0, boundsWidth, boundsHeight, gradientPaint);
        }
        
        // Draw accent gradient
        if (accentGradient != null) {
            gradientPaint.setShader(accentGradient);
            gradientPaint.setAlpha((int) (255 * accentAlpha));
            canvas.drawRect(0, 0, boundsWidth, boundsHeight, gradientPaint);
        }
        
        // Draw subtle overlay for better text readability
        drawOverlay(canvas, scrollProgress);
    }
    
    private void drawOverlay(Canvas canvas, float scrollProgress) {
        // Create a subtle overlay that becomes more visible when scrolling
        float overlayAlpha = scrollProgress * 0.1f;
        
        if (overlayAlpha > 0) {
            overlayPaint.setColor(overlayColor);
            overlayPaint.setAlpha((int) (255 * overlayAlpha));
            canvas.drawRect(0, 0, boundsWidth, boundsHeight, overlayPaint);
        }
    }
    
    // Animation effects for special events
    public void pulseEffect() {
        // Create a pulse effect by temporarily modifying the gradient
        // This could be triggered when gifts are received or profile is viewed
        // Implementation depends on specific requirements
    }
    
    public void setIntensity(float intensity) {
        // Adjust gradient intensity based on external factors
        // This could be used to make the gradient more or less prominent
        intensity = Math.max(0f, Math.min(1f, intensity));
        
        // Update gradient colors based on intensity
        if (gradientColors != null) {
            for (int i = 0; i < gradientColors.length; i++) {
                int color = gradientColors[i];
                int alpha = (int) (Color.alpha(color) * intensity);
                gradientColors[i] = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
            }
            
            // Recreate gradients with new colors
            if (boundsWidth > 0 && boundsHeight > 0) {
                createGradients();
            }
        }
    }
    
    public void setCustomColors(int primaryColor, int secondaryColor) {
        // Allow custom colors for special profile types (Premium, Business, etc.)
        this.baseColor = primaryColor;
        this.accentColor = secondaryColor;
        updateTheme();
    }
    
    // Performance optimization methods
    public void setLowPerformanceMode(boolean enabled) {
        if (enabled) {
            // Simplify gradients for better performance
            gradientPositions = new float[]{0f, 1f};
            if (gradientColors != null && gradientColors.length > 1) {
                gradientColors = new int[]{gradientColors[0], gradientColors[gradientColors.length - 1]};
            }
            if (accentColors != null && accentColors.length > 1) {
                accentColors = new int[]{accentColors[0], accentColors[accentColors.length - 1]};
            }
        } else {
            // Restore full gradient quality
            gradientPositions = new float[]{0f, 0.3f, 0.7f, 1f};
            updateTheme();
        }
        
        if (boundsWidth > 0 && boundsHeight > 0) {
            createGradients();
        }
    }
}