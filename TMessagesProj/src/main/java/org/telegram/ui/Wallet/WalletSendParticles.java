package org.telegram.ui.Wallet;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import org.telegram.messenger.AndroidUtilities;
import java.util.ArrayList;
import java.util.Random;

final class WalletSendParticles {
    private static final int[] COLORS = {0xFF0A3CE0, 0xFF0057FF, 0xFF1E7BFF, 0xFF34A8FF, 0xFF63D2FF, 0xFF9BE9FF};
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private long lastFrame;

    private static class Spark {
        float x, y, vx, vy, size, life, drag, turn, spin, age;
        int color;
        float opacity = 1f;
    }

    WalletSendParticles() {
        star.moveTo(0, -1);
        star.quadTo(.12f, -.12f, 1, 0);
        star.quadTo(.12f, .12f, 0, 1);
        star.quadTo(-.12f, .12f, -1, 0);
        star.quadTo(-.12f, -.12f, 0, -1);
        star.close();
    }

    private float pick(float min, float max) { return min + random.nextFloat() * (max - min); }

    private Spark add(float x, float y, float angle, float speed, float size, float life, float drag) {
        if (sparks.isEmpty()) lastFrame = SystemClock.uptimeMillis();
        Spark s = new Spark();
        s.x = x; s.y = y;
        s.vx = (float) Math.cos(angle) * speed; s.vy = (float) Math.sin(angle) * speed;
        s.size = size; s.life = life; s.drag = drag;
        s.color = COLORS[random.nextInt(COLORS.length)];
        s.turn = pick(0, 90); s.spin = pick(-180, 180);
        sparks.add(s);
        return s;
    }

    void burst(float x, float y) {
        float d = AndroidUtilities.density;
        for (int i = 0; i < 28; i++) add(x, y, pick(0, (float) Math.PI * 2), pick(60, 300) * d,
                pick(2.5f, 6) * d, pick(.5f, .9f), 3);
    }

    void trail(float x, float y) {
        float d = AndroidUtilities.density;
        add(x, y, pick(0, (float) Math.PI * 2), pick(0, 3.5f * 8) * d,
                pick(1.5f, 3.5f) * d, pick(.35f, .6f), 2);
    }

    void celebrate(float x, float y) {
        float d = AndroidUtilities.density;
        for (int i = 0; i < 56; i++) {
            float angle, speed;
            if (i % 10 <= 6) {
                angle = (float) Math.PI + pick(-.3f, .3f);
                speed = pick(140, 560) * d;
            } else if (i % 10 <= 8) {
                angle = (float) Math.PI + (random.nextBoolean() ? 1 : -1) * pick(.5f, 1.4f);
                speed = pick(60, 240) * d;
            } else {
                angle = pick(0, (float) Math.PI * 2);
                speed = pick(40, 160) * d;
            }
            add(x, y, angle, speed, pick(2.2f, 6.5f) * d, pick(.8f, 1.4f), 2.4f);
        }
    }

    void sent(float x, float y) {
        clear();
        float d = AndroidUtilities.density;
        // Stagger matched left/right streams so the burst flows out of the gem.
        for (int i = 0; i < 48; i++) {
            for (int direction = -1; direction <= 1; direction += 2) {
                float angle = (direction < 0 ? (float) Math.PI : 0f) + pick(-.6f, .6f);
                Spark spark = add(x, y + pick(-3f, 3f) * d, angle, pick(150f, 310f) * d,
                        pick(1.3f, 3.2f) * d, pick(.65f, 1.05f), 1.8f);
                spark.color = 0xFF9BDEFF;
                spark.opacity = pick(.3f, 1f);
                spark.age = -i * .006f;
            }
        }
    }

    boolean isActive() { return !sparks.isEmpty(); }
    void clear() { sparks.clear(); }

    void draw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(.033f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.age += dt;
            if (s.age < 0f) continue;
            if (s.age >= s.life) { sparks.remove(i); continue; }
            float decay = (float) Math.exp(-s.drag * dt);
            s.vx *= decay; s.vy *= decay;
            s.x += s.vx * dt; s.y += s.vy * dt; s.turn += s.spin * dt;
            float t = s.age / s.life;
            float c = Math.max(0, Math.min(1, (t - .35f) / .65f));
            float alpha = s.opacity * Math.min(1, t * 8) * (1 - c * c * (3 - 2 * c));
            float r = s.size * (1 - .5f * t);
            int save = canvas.save();
            canvas.translate(s.x, s.y);
            canvas.rotate(s.turn);
            canvas.scale(r, r);
            paint.setColor(s.color);
            paint.setAlpha(Math.round(255 * .08f * alpha));
            canvas.drawCircle(0, 0, 1.4f, paint);
            paint.setAlpha(Math.round(255 * alpha));
            canvas.drawPath(star, paint);
            canvas.restoreToCount(save);
        }
    }
}
