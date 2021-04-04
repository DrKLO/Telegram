package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class BayerTextureSprite2D extends Sprite2D {

    private final BayerTextureSprite2DShader shader;

    private static final float QUADV[] =
            {-1, 1, 0, 0,
                    -1, -1, 0, 1,
                    1, 1, 1, 0,
                    -1, -1, 0, 1,
                    1, -1, 1, 1,
                    1, 1, 1, 0};
    private final FloatBuffer triangleVertices;

    public BayerTextureSprite2D(BayerTextureSprite2DShader shader) {
        this.shader = shader;
        triangleVertices =
                ByteBuffer.allocateDirect(QUADV.length * 4).order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        triangleVertices.put(QUADV).position(0);
    }

    public void draw(Texture texture, Texture bayerTexture) {
        GLES20.glUseProgram(shader.programId);
        GLES20.glUniform1i(shader.aTextureHandle, 0);
        GLES20.glUniform1i(shader.aBayerTextureHandle, 1);
        texture.use(0);
        bayerTexture.use(1);
        triangleVertices.position(0);
        GLES20.glVertexAttribPointer(
                shader.aPositionHandle, 2, GLES20.GL_FLOAT, false, 16, triangleVertices
        );
        GLES20.glEnableVertexAttribArray(shader.aPositionHandle);
        triangleVertices.position(2);
        GLES20.glVertexAttribPointer(
                shader.aTexturePositionHandle, 2, GLES20.GL_FLOAT, false, 16, triangleVertices
        );
        GLES20.glEnableVertexAttribArray(shader.aTexturePositionHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(shader.aPositionHandle);
        GLES20.glDisableVertexAttribArray(shader.aTexturePositionHandle);
    }
}