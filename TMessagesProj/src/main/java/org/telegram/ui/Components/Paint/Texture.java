package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import org.telegram.ui.Components.Size;

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

        boolean mipMappable = isPOT(bitmap.getWidth()) && isPOT(bitmap.getHeight());
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, mipMappable ? GLES20.GL_LINEAR_MIPMAP_LINEAR : GLES20.GL_LINEAR);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        if (mipMappable) {
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }

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
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        int width = (int) size.width;
        int height = (int) size.height;
        int format = GLES20.GL_RGBA;
        int type = GLES20.GL_UNSIGNED_BYTE;

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height, 0, format, type, null);

        return texture;
    }
}
