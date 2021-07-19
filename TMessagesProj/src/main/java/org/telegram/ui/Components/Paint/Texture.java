package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import org.telegram.ui.Components.Size;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Texture {

    private Bitmap bitmap;
    private int texture;

    public Texture(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void cleanResources(boolean recycleBitmap) {
        if (texture == 0) {
            return;
        }

        int[] textures = new int[]{texture};
        GLES20.glDeleteTextures(1, textures, 0);
        texture = 0;

        if (recycleBitmap) {
            bitmap.recycle();
        }
    }

    private boolean isPOT(int x) {
        return (x & (x - 1)) == 0;
    }

    public int texture() {
        if (texture != 0) {
            return texture;
        }

        if (bitmap.isRecycled()) {
            return 0;
        }

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        texture = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i += 1) {
            int argb = pixels[i];
            pixels[i] = argb & 0xff00ff00 | ((argb & 0xff) << 16) | ((argb >> 16) & 0xff);
        }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, IntBuffer.wrap(pixels));

        int px = bitmap.getPixel(0, 0);

        ByteBuffer buffer = ByteBuffer.allocateDirect(4); //fix for android 9.0
        buffer.putInt(px).position(0);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        Utils.HasGLError();

        return texture;
    }

    public static int generateTexture(Size size) {
        int texture;
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        texture = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        int width = (int) size.width;
        int height = (int) size.height;
        int format = GLES20.GL_RGBA;
        int type = GLES20.GL_UNSIGNED_BYTE;

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height, 0, format, type, null);

        return texture;
    }
}
