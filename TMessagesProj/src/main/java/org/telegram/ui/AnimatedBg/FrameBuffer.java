package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

public class FrameBuffer {

    private final int bufferId;
    private final int systemBufferId;
    public final Texture bufferTexture;

    public FrameBuffer(int width, int height) {
        int[] handle = new int[1];
        GLES20.glGetIntegerv(
                GLES20.GL_FRAMEBUFFER_BINDING, handle, 0
        );
        systemBufferId = handle[0];

        GLES20.glGenFramebuffers(1, handle, 0);
        bufferId = handle[0];

        GLES20.glGenTextures(1, handle, 0);
        bufferTexture = new Texture(handle[0], width, height);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bufferTexture.textureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,
                0, GLES20.GL_RGB, width, height,
                0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null
        );

        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        );
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        );
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bufferId);

        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                bufferTexture.textureId,
                0
        );
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, systemBufferId);
    }

    /**
     * Prepare framebuffer to draw to it
     */
    public void activate() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bufferId);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bufferTexture.textureId);
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST
        );
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST
        );
    }

    /**
     * Flush buffer to screen
     */
    public void flush() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, systemBufferId);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bufferTexture.textureId);
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        );
        GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        );
    }
}