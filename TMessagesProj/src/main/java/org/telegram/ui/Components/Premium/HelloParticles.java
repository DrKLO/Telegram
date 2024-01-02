package org.telegram.ui.Components.Premium;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.HashMap;

public class HelloParticles {

    private static final String[] hellos = new String[] {
        "Hello", "Привіт", "Привет", "Bonjour", "Hola", "Ciao", "Olá", "여보세요", "你好", "Salve",
        "Sveiki", "Halo", "გამარჯობა", "Hallå", "Salam", "Tere", "Dia dhuit", "こんにちは", "Сайн уу",
        "Bongu", "Ahoj", "γεια", "Zdravo", "नमस्ते", "Habari", "Hallo", "ជំរាបសួរ", "مرحبًا", "ನಮಸ್ಕಾರ",
        "Салам", "Silav li wir", "سڵاو", "Kif inti", "Talofa", "Thobela", "हॅलो", "ሰላም", "Здраво",
        "ഹലോ", "ہیلو", "ꯍꯦꯜꯂꯣ", "Alô", "வணக்கம்", "Mhoro", "Moni", "Alo", "สวัสดี", "Salom", "Բարեւ"
    };

    public static class Drawable {

        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private float bitmapScale = 1;
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

        public RectF rect = new RectF();
        public RectF screenRect = new RectF();
        public boolean paused;
        private Paint paint = new Paint();

        ArrayList<Drawable.Particle> particles = new ArrayList<>();
        public float speedScale = 1f;

        public final int count;
        public boolean useGradient;
        public int size1 = 14, size2 = 12, size3 = 10;
        public long minLifeTime = 2000;
        private int lastColor;
        private final float dt = 1000 / AndroidUtilities.screenRefreshRate;

        public Drawable(int count) {
            this.count = count;
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textPaint.setColor(Color.WHITE);
            switch (SharedConfig.getDevicePerformanceClass()) {
                case SharedConfig.PERFORMANCE_CLASS_LOW:
                    bitmapScale = .25f;
                    break;
                case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                    bitmapScale = .5f;
                    break;
                case SharedConfig.PERFORMANCE_CLASS_HIGH:
                default:
                    bitmapScale = .75f;
                    break;
            }
            textPaint.setTextSize(AndroidUtilities.dp(24 * bitmapScale));
            paint.setColor(Color.WHITE);
        }

        public void init() {
            if (particles.isEmpty()) {
                for (int i = 0; i < count; i++) {
                    particles.add(new Drawable.Particle());
                }
            }
        }

        public void resetPositions() {
            long time = System.currentTimeMillis();
            for (int i = 0; i < particles.size(); i++) {
                particles.get(i).genPosition(time, i, true);
            }
        }

        public void onDraw(Canvas canvas) {
            long time = System.currentTimeMillis();
            for (int i = 0; i < particles.size(); i++) {
                Drawable.Particle particle = particles.get(i);
                if (paused) {
                    particle.draw(canvas, i, pausedTime);
                } else {
                    particle.draw(canvas, i, time);
                }
                if (particle.inProgress >= 1) {
                    particle.genPosition(time, i, false);
                }
            }
        }

        public void recycle() {
            for (Bitmap bitmap : bitmaps.values()) {
                bitmap.recycle();
            }
            bitmaps.clear();
        }

        long pausedTime;

        private class Particle {
            private boolean set;
            private float x, y;
            private float vecX, vecY;
            private int alpha;
            private StaticLayout staticLayout;
            private Bitmap bitmap;
            private int l, w, h;
            private long duration;
            private float scale;
            float inProgress;

            public void draw(Canvas canvas, int index,  long time) {
                if (!paused) {
                    if (inProgress != 1f) {
                        inProgress += dt / duration;
                        if (inProgress > 1f) {
                            inProgress = 1f;
                        }
                    }
                }


                if (bitmap != null) {
                    canvas.save();
                    float t = 1f - 4f * (float) Math.pow(inProgress - .5f, 2f);
                    float s = scale / bitmapScale * (.7f + .4f * t);
                    canvas.translate(x - w / 2f, y - h / 2f);
                    canvas.scale(s, s, w / 2f, h / 2f);
                    paint.setAlpha((int) (alpha * t));
                    canvas.drawBitmap(bitmap, 0, 0, paint);
                    canvas.restore();
                }
            }

            public void genPosition(long time, int index, boolean reset) {
                duration = 2250 + Math.abs(Utilities.fastRandom.nextLong() % 2250);
                scale = .6f + .45f * Math.abs(Utilities.fastRandom.nextFloat());

                String string = hellos[Math.abs(Utilities.fastRandom.nextInt() % hellos.length)];
                if (string.length() > 7) {
                    scale *= .6f;
                } else if (string.length() > 5) {
                    scale *= .75f;
                }
                staticLayout = new StaticLayout(string, textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
                if (staticLayout.getLineCount() <= 0) {
                    l = w = h = 0;
                } else {
                    l = (int) staticLayout.getLineLeft(0);
                    w = (int) staticLayout.getLineWidth(0);
                    h = staticLayout.getHeight();
                }
                bitmap = bitmaps.get(string);
                if (bitmap == null) {
                    bitmap = Bitmap.createBitmap(Math.max(1, w - Math.max(0, l)), Math.max(1, h), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.translate(-l, 0);
                    staticLayout.draw(canvas);
                    bitmaps.put(string, bitmap);
                }

                float bestDistance = 0;
                float minX = rect.left + w / 4f, maxX = rect.right - w / 4f;
                if (index % 2 == 0) {
                    maxX = rect.centerX() - w / 2f;
                } else {
                    minX = rect.centerX() + w / 2f;
                }
                float bestX = minX + Math.abs(Utilities.fastRandom.nextInt() % (maxX - minX));
                float bestY = rect.top + Math.abs(Utilities.fastRandom.nextInt() % rect.height());
                for (int k = 0; k < 10; k++) {
                    float randX = minX + Math.abs(Utilities.fastRandom.nextInt() % (maxX - minX));
                    float randY = rect.top + Math.abs(Utilities.fastRandom.nextInt() % rect.height());
                    float minDistance = Integer.MAX_VALUE;
                    for (int j = 0; j < particles.size(); j++) {
                        Particle p = particles.get(j);
                        if (!p.set) {
                            continue;
                        }
                        float rx = Math.min(Math.abs(p.x + p.w * (scale / bitmapScale) * 1.1f - randX), Math.abs(p.x - randX));
                        float ry = p.y - randY;
                        float distance = rx * rx + ry * ry;
                        if (distance < minDistance) {
                            minDistance = distance;
                        }
                    }
                    if (minDistance > bestDistance) {
                        bestDistance = minDistance;
                        bestX = randX;
                        bestY = randY;
                    }
                }
                x = bestX;
                y = bestY;

                double a = Math.atan2(x - rect.centerX(), y - rect.centerY());
                vecX = (float) Math.sin(a);
                vecY = (float) Math.cos(a);
                alpha = (int) (255 * ((50 + Utilities.fastRandom.nextInt(50)) / 100f));

                inProgress = reset ? Math.abs((Utilities.fastRandom.nextFloat() % 1f) * .9f) : 0;
                set = true;
            }
        }
    }
}
