package org.telegram.ui.Components.spoilers;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Process;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;

public class SpoilerEffectBitmapFactory {

    private static SpoilerEffectBitmapFactory factory;

    public static SpoilerEffectBitmapFactory getInstance() {
        if (factory == null) {
            factory = new SpoilerEffectBitmapFactory();
        }
        return factory;
    }

    final DispatchQueue dispatchQueue = new DispatchQueue("SpoilerEffectBitmapFactory", true, 3 * Process.THREAD_PRIORITY_LESS_FAVORABLE);
    private Bitmap shaderBitmap;
    Bitmap bufferBitmap;
    Bitmap backgroundBitmap;
    Canvas shaderCanvas;
    Paint shaderPaint;
    long lastUpdateTime;
    ArrayList<SpoilerEffect> shaderSpoilerEffects;
    boolean isRunning;
    Matrix localMatrix = new Matrix();
    int size;

    private SpoilerEffectBitmapFactory() {
        int maxSize = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? AndroidUtilities.dp(150) : AndroidUtilities.dp(100);
        size = (int) Math.min(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f, maxSize);
        if (size < AndroidUtilities.dp(80)) {
            size = AndroidUtilities.dp(80);
        }
    }


    Paint getPaint() {
        if (shaderBitmap == null) {
            shaderBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            shaderCanvas = new Canvas(shaderBitmap);
            shaderPaint = new Paint();
            shaderSpoilerEffects = new ArrayList<>(10 * 10);
            shaderPaint.setShader(new BitmapShader(shaderBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            int step = (int) (size / 10f);
            int particleCount = (int) (60 * (size / (float) AndroidUtilities.dp(200)));
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    SpoilerEffect shaderSpoilerEffect = new SpoilerEffect();
                    shaderSpoilerEffect.setSize(size);
                    shaderSpoilerEffect.setBounds(step * i, step * j - AndroidUtilities.dp(5), step * i + step +  AndroidUtilities.dp(3), step * j + step + AndroidUtilities.dp(5));
                    shaderSpoilerEffect.drawPoints = true;
                    shaderSpoilerEffect.setMaxParticlesCount(Math.min(SpoilerEffect.MAX_PARTICLES_PER_ENTITY * 5, particleCount));
                    shaderSpoilerEffect.setColor(Color.WHITE);
                    shaderSpoilerEffects.add(shaderSpoilerEffect);
                }
            }

            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    shaderSpoilerEffects.get(j + i * 10).draw(shaderCanvas);
                }
            }
            shaderPaint.setShader(new BitmapShader(shaderBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            lastUpdateTime = System.currentTimeMillis();
        }

        return shaderPaint;
    }

    public void checkUpdate() {
        long time = System.currentTimeMillis();
        if (time - lastUpdateTime > 32 && !isRunning) {
            lastUpdateTime = System.currentTimeMillis();
            isRunning = true;
            Bitmap bufferBitmapFinall = bufferBitmap;
            dispatchQueue.postRunnable(() -> {
                Bitmap bitmap = bufferBitmapFinall;
                if (bitmap == null) {
                    bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
                }
                if (backgroundBitmap == null) {
                    backgroundBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
                } else {
                    backgroundBitmap.eraseColor(Color.TRANSPARENT);
                }
                Canvas shaderCanvas = new Canvas(bitmap);
                Canvas backgroundCanvas = new Canvas(backgroundBitmap);
                for (int i = 0; i < 10; i++) {
                    for (int j = 0; j < 10; j++) {
                        shaderSpoilerEffects.get(j + i * 10).draw(backgroundCanvas);
                    }
                }
                bitmap.eraseColor(Color.TRANSPARENT);
                shaderCanvas.drawBitmap(backgroundBitmap, 0, 0, null);
                Bitmap finalBitmap = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    bufferBitmap = shaderBitmap;
                    shaderBitmap = finalBitmap;
                    shaderPaint.setShader(new BitmapShader(shaderBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                    isRunning = false;
                });
            });
        }

    }


}
