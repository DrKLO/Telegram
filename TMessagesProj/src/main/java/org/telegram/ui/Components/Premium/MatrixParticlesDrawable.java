package org.telegram.ui.Components.Premium;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;


public class MatrixParticlesDrawable {

    RectF excludeRect = new RectF();
    Bitmap[] bitmaps = new Bitmap[16];

    int size;
    Rect drawingRect = new Rect();
    ArrayList<Particle>[] particles;
    MatrixTextParticle[][] matrixTextParticles;
    Paint paint = new Paint();

    void init() {
        size = AndroidUtilities.dp(16);
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
        textPaint.setTextSize(size);
        textPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_premiumStartSmallStarsColor2), 30));
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 16; i++) {
            char c = (char) (i < 10 ? ('0' + i) : ('A' + (i - 10)));
            bitmaps[i] = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmaps[i]);
            canvas.drawText(Character.toString(c), size >> 1, size, textPaint);
        }
    }

    void onDraw(Canvas canvas) {
        int nx = drawingRect.width() / size;
        int ny = drawingRect.height() / size;
        if (nx == 0 || ny == 0) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (particles == null || particles.length != nx + 1) {
            particles = new ArrayList[nx + 1];
            for (int x = 0; x <= nx; x++) {
                particles[x] = new ArrayList<>();
                Particle particle = new Particle();
                particle.init(ny, currentTime);
                particles[x].add(particle);
            }
        }

        if (matrixTextParticles == null || matrixTextParticles.length != nx + 1 || matrixTextParticles[0].length != ny + 1) {
            matrixTextParticles = new MatrixTextParticle[nx + 1][];
            for (int x = 0; x <= nx; x++) {
                matrixTextParticles[x] = new MatrixTextParticle[ny + 1];
                for (int y = 0; y <= ny; y++) {
                    matrixTextParticles[x][y] = new MatrixTextParticle();
                    matrixTextParticles[x][y].init(currentTime);
                }
            }
        }


        for (int x = 0; x <= nx; x++) {
            ArrayList<Particle> list = particles[x];
            for (int i = 0; i < list.size(); i++) {
                Particle particle = list.get(i);
                if (currentTime - particle.time > 50) {
                    particle.y++;
                    particle.time = currentTime;
                    if (particle.y - particle.len >= ny) {
                        if (list.size() == 1) {
                            particle.reset(currentTime);
                        } else {
                            list.remove(particle);
                            i--;
                        }
                    }
                    if (particle.y > particle.len && i == list.size() - 1 && Math.abs(Utilities.fastRandom.nextInt(4)) == 0) {
                        Particle newParticle = new Particle();
                        newParticle.reset(currentTime);
                        list.add(newParticle);
                    }
                }
                int n = Math.min(particle.y, ny + 1);
                for (int y = Math.max(0, particle.y - particle.len); y < n; y++) {
                    float finalX = size * x;
                    float finalY = size * y;
                    if (!excludeRect.contains(finalX, finalY)) {
                        float alpha = Utilities.clamp(0.2f + 0.8f * (1f - (particle.y - y) / (float) (particle.len - 1)), 1f, 0f);
                        matrixTextParticles[x][y].draw(canvas, finalX, finalY, currentTime, alpha);
                    }
                }
            }
//            for (int y = 0; y <= ny; y++) {
//                matrixTextParticles[x][y].draw(canvas, size * x, size * y, currentTime);
//            }
        }
    }

    private class Particle {
        int y;
        int len = 5;
        long time;

        public void init(int ny, long currentTime) {
            y = Math.abs(Utilities.fastRandom.nextInt() % ny);
            time = currentTime;
            len = 4 + Math.abs(Utilities.fastRandom.nextInt() % 6);
        }

        public void reset(long currentTime) {
            y = 0;
            time = currentTime;
            len = 4 + Math.abs(Utilities.fastRandom.nextInt() % 6);
        }
    }

    private class MatrixTextParticle {
        int index;
        int nextIndex;
        long lastUpdateTime;
        long nextUpdateTime;

        public void init(long time) {
            index = Math.abs(Utilities.fastRandom.nextInt() % 16);
            nextIndex = Math.abs(Utilities.fastRandom.nextInt() % 16);
            lastUpdateTime = time;
            nextUpdateTime = time + Math.abs(Utilities.fastRandom.nextInt() % 300) + 150;
        }

        public void draw(Canvas canvas, float x, float y, long currentTime, float alpha) {
            if (nextUpdateTime - currentTime < 150) {
                float p = Utilities.clamp(1f - (nextUpdateTime - currentTime) / 150f, 1f, 0);
                paint.setAlpha((int) ((1f - p) * alpha * 255));
                canvas.drawBitmap(bitmaps[index], x, y, paint);
                paint.setAlpha((int) (p * alpha * 255));
                canvas.drawBitmap(bitmaps[nextIndex], x, y, paint);
                paint.setAlpha(255);
                if (p >= 1) {
                    index = nextIndex;
                    lastUpdateTime = currentTime;
                    nextIndex = Math.abs(Utilities.fastRandom.nextInt() % 16);
                    nextUpdateTime = currentTime + Math.abs(Utilities.fastRandom.nextInt() % 300) + 150;
                }
            } else {
                paint.setAlpha((int) (alpha * 255));
                canvas.drawBitmap(bitmaps[index], x, y, paint);
            }

        }
    }
}
