package org.telegram.ui.AnimatedBg;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.CharBuffer;

public class Texture {

    /**
     * 8x8 Bayer ordered dithering pattern.
     * Each input pixel is scaled to the 0..63 range before looking in this table to determine the action.
     */
    private static final char BAYER_KERNEL[] = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21};
    private static final char BAYER_KERNEL_SIZE = 8;

    public final int textureId;
    public final int width;
    public final int height;

    public Texture(int textureId, int width, int height) {
        this.textureId = textureId;
        this.width = width;
        this.height = height;
    }

    public void use(int textureSlot) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureSlot);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
    }

    public void delete() {
        GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
    }

    public static Texture load(Bitmap bitmap) {
        int[] handle = new int[1];
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glGenTextures(1, handle, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle[0]);
        int textureId = handle[0];

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        );

        int format = GLUtils.getInternalFormat(bitmap);
        int type = GLES20.GL_UNSIGNED_BYTE;
        try {
            type = GLUtils.getType(bitmap);
        } catch (IllegalArgumentException e) {
            Log.e("OpenGL", "bitmap illegal type");
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, format, bitmap, type, 0);

        return new Texture(textureId, bitmap.getWidth(), bitmap.getHeight());
    }

    public static Texture createBayerCorrectionTexture() {
        int[] handle = new int[1];
        GLES20.glGenTextures(1, handle, 0);
        Texture bayerTexture = new Texture(handle[0], BAYER_KERNEL_SIZE, BAYER_KERNEL_SIZE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bayerTexture.textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, BAYER_KERNEL_SIZE, BAYER_KERNEL_SIZE, 0, GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE, CharBuffer.wrap(BAYER_KERNEL)
        );
        return bayerTexture;
    }
}