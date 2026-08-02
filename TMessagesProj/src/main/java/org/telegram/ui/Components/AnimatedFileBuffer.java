package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Shader;

import java.util.Arrays;

public class AnimatedFileBuffer {
    private final BitmapShader[] shader = new BitmapShader[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    public final Bitmap bitmap;
    public final int width;
    public final int height;
    public int time;
    public boolean opaque;

    private AnimatedFileBuffer(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.width = bitmap.getWidth();
        this.height = bitmap.getHeight();
    }

    public BitmapShader getShader(int index) {
        if (shader[index] == null) {
            shader[index] = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        return shader[index];
    }

    public static AnimatedFileBuffer of(int width, int height) {
        return new AnimatedFileBuffer(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
    }

    public static AnimatedFileBuffer of(Bitmap bitmap) {
        return new AnimatedFileBuffer(bitmap);
    }

    public void recycle() {
        bitmap.recycle();
        Arrays.fill(shader, null);
    }
}
