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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HeaderGiftParticleSystem {
    
    private static final int MAX_PARTICLES = 20;
    private static final int MAX_PARTICLES_LOW_PERFORMANCE = 10;
    private static final float PARTICLE_SPAWN_RATE = 0.5f; // particles per second
    private static final float PARTICLE_LIFETIME = 8f; // seconds
    private static final float PARTICLE_SPEED = 30f; // dp per second
    private static final float PARTICLE_SIZE = 24f; // dp
    
    // Gift emoji types based on the mockup
    private static final String[] GIFT_EMOJIS = {
        "🎁", "🎀", "🎊", "🎉", "💝", "🌟", "✨", "🎈", "🎂", "🍰", "🎆", "🎇"
    };
    
    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final List<GiftParticle> particles;
    private final Random random;
    private final Paint emojiPaint;
    private final DecelerateInterpolator decelerateInterpolator;
    private final LinearInterpolator linearInterpolator;
    
    // Animation state
    private float spawnTimer = 0f;
    private float intensity = 0.2f;
    private boolean isAnimating = false;
    private boolean giftsVisible = true;
    
    // Bounds
    private int boundsWidth = 0;
    private int boundsHeight = 0;
    
    // Performance optimization
    private int performanceClass = SharedConfig.PERFORMANCE_CLASS_AVERAGE;
    private int maxParticles = MAX_PARTICLES;
    private boolean performanceOptimization = false;
    private float averageFrameTime = 0f;
    private long frameTimeSum = 0;
    private int frameCount = 0;
    
    public HeaderGiftParticleSystem(Context context, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
        this.particles = new ArrayList<>();
        this.random = new Random();
        this.decelerateInterpolator = new DecelerateInterpolator(2.0f);
        this.linearInterpolator = new LinearInterpolator();
        this.emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        initializePaint();
        setPerformanceClass(SharedConfig.getDevicePerformanceClass());
    }
    
    private void initializePaint() {
        emojiPaint.setTextAlign(Paint.Align.CENTER);
        emojiPaint.setTextSize(AndroidUtilities.dp(PARTICLE_SIZE));
        emojiPaint.setAlpha(255);
    }
    
    public void setPerformanceClass(int performanceClass) {
        this.performanceClass = performanceClass;
        
        switch (performanceClass) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                maxParticles = MAX_PARTICLES_LOW_PERFORMANCE;
                break;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                maxParticles = (int) (MAX_PARTICLES * 0.8f);
                break;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
            default:
                maxParticles = MAX_PARTICLES;
                break;
        }
        
        // Remove excess particles if needed
        while (particles.size() > maxParticles) {
            particles.remove(particles.size() - 1);
        }
    }
    
    public void setBounds(int left, int top, int right, int bottom) {
        this.boundsWidth = right - left;
        this.boundsHeight = bottom - top;
    }
    
    public void setIntensity(float intensity) {
        this.intensity = Math.max(0f, Math.min(1f, intensity));
    }
    
    public void setScrollProgress(float scrollProgress) {
        // Reduce particle visibility and move them closer to avatar as user scrolls
        for (GiftParticle particle : particles) {
            // Move particles closer to avatar center
            float targetRadius = AndroidUtilities.dp(40) * (1.0f - scrollProgress * 0.7f);
            particle.orbitRadius = targetRadius + AndroidUtilities.dp(20);
            
            // Fade out particles as user scrolls
            float baseAlpha = 1.0f - scrollProgress;
            particle.alpha = Math.max(0f, baseAlpha);
        }
    }
    
    public void setGiftsVisible(boolean visible) {
        this.giftsVisible = visible;
    }
    
    public void startAnimation() {
        isAnimating = true;
        spawnTimer = 0f;
    }
    
    public void stopAnimation() {
        isAnimating = false;
    }
    
    public void update(float deltaTime) {
        if (!isAnimating || !giftsVisible) return;
        
        long frameStartTime = System.currentTimeMillis();
        
        // Update spawn timer
        spawnTimer += deltaTime;
        
        // Spawn new particles
        float spawnRate = PARTICLE_SPAWN_RATE * intensity;
        if (spawnRate > 0 && spawnTimer >= 1f / spawnRate) {
            spawnParticle();
            spawnTimer = 0f;
        }
        
        // Update existing particles
        for (int i = particles.size() - 1; i >= 0; i--) {
            GiftParticle particle = particles.get(i);
            particle.update(deltaTime);
            
            // Remove dead particles
            if (particle.isDead()) {
                particles.remove(i);
            }
        }
        
        // Performance monitoring
        updatePerformanceMetrics(System.currentTimeMillis() - frameStartTime);
    }
    
    private void spawnParticle() {
        if (particles.size() >= maxParticles || boundsWidth <= 0 || boundsHeight <= 0) {
            return;
        }
        
        GiftParticle particle = new GiftParticle();
        
        // Calculate avatar center position (centered in header)
        float avatarCenterX = boundsWidth / 2f; // Center horizontally
        float avatarCenterY = AndroidUtilities.dp(21); // Half of avatar height
        
        // Random angle around the avatar
        float angle = random.nextFloat() * 360f;
        float radius = AndroidUtilities.dp(60 + random.nextFloat() * 40); // Distance from avatar
        
        // Position particle in circle around avatar
        particle.x = avatarCenterX + (float) (Math.cos(Math.toRadians(angle)) * radius);
        particle.y = avatarCenterY + (float) (Math.sin(Math.toRadians(angle)) * radius);
        
        // Circular motion parameters
        particle.orbitCenterX = avatarCenterX;
        particle.orbitCenterY = avatarCenterY;
        particle.orbitRadius = radius;
        particle.orbitAngle = angle;
        particle.orbitSpeed = 10f + random.nextFloat() * 20f; // degrees per second
        
        // Gentle floating motion
        particle.velocityX = (random.nextFloat() - 0.5f) * AndroidUtilities.dp(5);
        particle.velocityY = (random.nextFloat() - 0.5f) * AndroidUtilities.dp(5);
        
        // Random rotation
        particle.rotation = random.nextFloat() * 360f;
        particle.rotationSpeed = (random.nextFloat() - 0.5f) * 30f; // Slower rotation
        
        // Random emoji
        particle.emoji = GIFT_EMOJIS[random.nextInt(GIFT_EMOJIS.length)];
        
        // Random scale variation
        particle.scale = 0.6f + random.nextFloat() * 0.4f;
        
        // Lifetime
        particle.maxLifetime = PARTICLE_LIFETIME + random.nextFloat() * 4f;
        
        particles.add(particle);
    }
    
    public void triggerGiftBurst() {
        if (!isAnimating || !giftsVisible) return;
        
        // Spawn multiple particles at once for burst effect
        int burstCount = Math.min(5, maxParticles - particles.size());
        for (int i = 0; i < burstCount; i++) {
            spawnParticle();
        }
    }
    
    public void draw(Canvas canvas) {
        if (!isAnimating || !giftsVisible || particles.isEmpty()) return;
        
        for (GiftParticle particle : particles) {
            particle.draw(canvas, emojiPaint);
        }
    }
    
    public void updateTheme() {
        // Update paint colors based on theme if needed
        // The emoji paint doesn't need theme updates, but we keep this for consistency
    }
    
    public void setPerformanceOptimization(boolean enabled) {
        this.performanceOptimization = enabled;
        
        if (enabled) {
            // Reduce particle count for better performance
            maxParticles = Math.max(5, maxParticles / 2);
            while (particles.size() > maxParticles) {
                particles.remove(particles.size() - 1);
            }
        } else {
            // Restore normal particle count
            setPerformanceClass(performanceClass);
        }
    }
    
    private void updatePerformanceMetrics(long frameTime) {
        frameTimeSum += frameTime;
        frameCount++;
        
        if (frameCount >= 60) { // Update every 60 frames
            averageFrameTime = frameTimeSum / (float) frameCount;
            frameTimeSum = 0;
            frameCount = 0;
            
            // Auto-optimize performance if needed
            if (performanceOptimization && averageFrameTime > 16.7f) { // 60 FPS threshold
                setPerformanceOptimization(true);
            }
        }
    }
    
    public float getAverageFrameTime() {
        return averageFrameTime;
    }
    
    private static class GiftParticle {
        float x, y;
        float velocityX, velocityY;
        float rotation, rotationSpeed;
        float scale = 1f;
        float alpha = 1f;
        float lifetime = 0f;
        float maxLifetime = PARTICLE_LIFETIME;
        String emoji;
        
        // Orbital motion properties
        float orbitCenterX, orbitCenterY;
        float orbitRadius;
        float orbitAngle;
        float orbitSpeed;
        
        private final RectF tempRect = new RectF();
        private final Path tempPath = new Path();
        
        void update(float deltaTime) {
            lifetime += deltaTime;
            
            // Update orbital motion
            orbitAngle += orbitSpeed * deltaTime;
            if (orbitAngle > 360f) orbitAngle -= 360f;
            
            // Calculate new position based on orbit
            x = orbitCenterX + (float) (Math.cos(Math.toRadians(orbitAngle)) * orbitRadius);
            y = orbitCenterY + (float) (Math.sin(Math.toRadians(orbitAngle)) * orbitRadius);
            
            // Add gentle floating motion
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;
            
            // Update rotation
            rotation += rotationSpeed * deltaTime;
            
            // Update alpha based on lifetime
            float lifeProgress = lifetime / maxLifetime;
            if (lifeProgress > 0.8f) {
                // Fade out in the last 20% of lifetime
                alpha = 1f - ((lifeProgress - 0.8f) / 0.2f);
            } else {
                alpha = 1f;
            }
            
            // Add subtle breathing effect to floating motion
            float breatheEffect = (float) (Math.sin(lifetime * 3f) * AndroidUtilities.dp(2));
            velocityX += breatheEffect * deltaTime * 0.1f;
            velocityY += breatheEffect * deltaTime * 0.1f;
        }
        
        void draw(Canvas canvas, Paint paint) {
            if (alpha <= 0f) return;
            
            paint.setAlpha((int) (255 * alpha));
            
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rotation);
            canvas.scale(scale, scale);
            
            // Draw the emoji
            float textSize = paint.getTextSize();
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            float textHeight = fontMetrics.descent - fontMetrics.ascent;
            float textOffset = (textHeight / 2) - fontMetrics.descent;
            
            canvas.drawText(emoji, 0, textOffset, paint);
            
            canvas.restore();
        }
        
        boolean isDead() {
            return lifetime >= maxLifetime || alpha <= 0f;
        }
    }
}