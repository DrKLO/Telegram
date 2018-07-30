/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class FireworksEffect {

    private Paint particlePaint;

    private long lastAnimationTime;

    final float angleDiff = (float) (Math.PI / 180 * 60);

    private class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float velocity;
        float alpha;
        float lifeTime;
        float currentTime;
        float scale;
        int color;
        int type;

        public void draw(Canvas canvas) {
            switch (type) {
                case 0: {
                    particlePaint.setColor(color);
                    particlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f) * scale);
                    particlePaint.setAlpha((int) (255 * alpha));
                    canvas.drawPoint(x, y, particlePaint);
                    break;
                }
                case 1:
                    break;
            }
        }
    }

    private ArrayList<Particle> particles = new ArrayList<>();
    private ArrayList<Particle> freeParticles = new ArrayList<>();

    public FireworksEffect() {
        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        particlePaint.setColor(Theme.getColor(Theme.key_actionBarDefaultTitle) & 0xffe6e6e6);
        particlePaint.setStrokeCap(Paint.Cap.ROUND);
        particlePaint.setStyle(Paint.Style.STROKE);

        for (int a = 0; a < 20; a++) {
            freeParticles.add(new Particle());
        }
    }

    private void updateParticles(long dt) {
        int count = particles.size();
        for (int a = 0; a < count; a++) {
            Particle particle = particles.get(a);
            if (particle.currentTime >= particle.lifeTime) {
                if (freeParticles.size() < 40) {
                    freeParticles.add(particle);
                }
                particles.remove(a);
                a--;
                count--;
                continue;
            }
            particle.alpha = 1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(particle.currentTime / particle.lifeTime);
            particle.x += particle.vx * particle.velocity * dt / 500.0f;
            particle.y += particle.vy * particle.velocity * dt / 500.0f;
            particle.vy += dt / 100.0f;
            particle.currentTime += dt;
        }
    }

    public void onDraw(View parent, Canvas canvas) {
        if (parent == null || canvas == null) {
            return;
        }

        int count = particles.size();
        for (int a = 0; a < count; a++) {
            Particle particle = particles.get(a);
            particle.draw(canvas);
        }

        if (Utilities.random.nextBoolean() && particles.size() + 8 < 150) {
            int statusBarHeight = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            float cx = Utilities.random.nextFloat() * parent.getMeasuredWidth();
            float cy = statusBarHeight + Utilities.random.nextFloat() * (parent.getMeasuredHeight() - AndroidUtilities.dp(20) - statusBarHeight);
            int color;

            switch (Utilities.random.nextInt(4)) {
                case 0: {
                    color = 0xff342eda;
                    break;
                }
                case 1: {
                    color = 0xfff32015;
                    break;
                }
                case 2: {
                    color = 0xfffcd753;
                    break;
                }
                case 3: {
                    color = 0xff19c43a;
                    break;
                }
                case 4:
                default: {
                    color = 0xffffe988;
                    break;
                }
            }
            for (int a = 0; a < 8; a++) {
                int angle = Utilities.random.nextInt(270) - 225;
                float vx = (float) Math.cos(Math.PI / 180.0 * angle);
                float vy = (float) Math.sin(Math.PI / 180.0 * angle);

                Particle newParticle;
                if (!freeParticles.isEmpty()) {
                    newParticle = freeParticles.get(0);
                    freeParticles.remove(0);
                } else {
                    newParticle = new Particle();
                }
                newParticle.x = cx;
                newParticle.y = cy;

                newParticle.vx = vx * 1.5f;
                newParticle.vy = vy;

                newParticle.color = color;
                newParticle.alpha = 1.0f;
                newParticle.currentTime = 0;

                newParticle.scale = Math.max(1.0f, Utilities.random.nextFloat() * 1.5f);
                newParticle.type = 0;//Utilities.random.nextInt(2);

                newParticle.lifeTime = 1000 + Utilities.random.nextInt(1000);
                newParticle.velocity = 20.0f + Utilities.random.nextFloat() * 4.0f;
                particles.add(newParticle);
            }
        }

        long newTime = System.currentTimeMillis();
        long dt = Math.min(17, newTime - lastAnimationTime);
        updateParticles(dt);
        lastAnimationTime = newTime;
        parent.invalidate();
    }
}
