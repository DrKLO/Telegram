package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class BlurSprite2D extends Sprite2D {

    private final BlurSprite2DShader shader;

    private static final float[] QUADV =
            {-1, 1, 0, 0,
                    -1, -1, 0, 1,
                    1, 1, 1, 0,
                    -1, -1, 0, 1,
                    1, -1, 1, 1,
                    1, 1, 1, 0};
    private final FloatBuffer triangleVertices;

    public BlurSprite2D(BlurSprite2DShader shader) {
        this.shader = shader;
        triangleVertices =
                ByteBuffer.allocateDirect(QUADV.length * 4).order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        triangleVertices.put(QUADV).position(0);
    }

    public void draw(Texture texture, float blurRadius, int viewportSize) {
        GLES20.glUseProgram(shader.programId);
        GLES20.glUniform1f(shader.radiusHandle, blurRadius);
        GLES20.glUniform1f(shader.resolutionHandle, viewportSize);
        texture.use(0);
        triangleVertices.position(0);
        GLES20.glVertexAttribPointer(
                shader.aPositionHandle, 2, GLES20.GL_FLOAT, false, 16, triangleVertices
        );
        GLES20.glEnableVertexAttribArray(shader.aPositionHandle);
        triangleVertices.position(2);
        GLES20.glVertexAttribPointer(
                shader.aTextureHandle, 2, GLES20.GL_FLOAT, false, 16, triangleVertices
        );
        GLES20.glEnableVertexAttribArray(shader.aTextureHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(shader.aPositionHandle);
        GLES20.glDisableVertexAttribArray(shader.aTextureHandle);
    }
}