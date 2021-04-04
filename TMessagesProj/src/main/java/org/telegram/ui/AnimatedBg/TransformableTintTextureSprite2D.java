package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class TransformableTintTextureSprite2D extends Sprite2D {

    private final TransformableTintTextureSprite2DShader shader;

    private static final float[] QUADV =
            {-1, 1, 0, 0,
                    -1, -1, 0, 1,
                    1, 1, 1, 0,
                    -1, -1, 0, 1,
                    1, -1, 1, 1,
                    1, 1, 1, 0};
    private final FloatBuffer triangleVertices;

    public TransformableTintTextureSprite2D(TransformableTintTextureSprite2DShader shader) {
        this.shader = shader;
        triangleVertices =
                ByteBuffer.allocateDirect(QUADV.length * 4).order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        triangleVertices.put(QUADV).position(0);
    }

    public void draw(Texture texture, float[] transformMatrix, float[] color) {
        GLES20.glUseProgram(shader.programId);
        texture.use(0);

        GLES20.glUniform3fv(shader.uTintColorHandle, 1, color, 0);
        // Attach values
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

        GLES20.glUniformMatrix4fv(shader.uTransformMatrixHandle, 1, false, transformMatrix, 0);

        // Draw
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisable(GLES20.GL_BLEND);

        GLES20.glDisableVertexAttribArray(shader.aPositionHandle);
        GLES20.glDisableVertexAttribArray(shader.aTextureHandle);
    }
}