package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

public class TransformableTintTextureSprite2DShader extends Shader {

    private static final String VERTEX_SHADER_CODE =
            "" +
                    "attribute vec4 aVertex;" +
                    "attribute vec2 aTextureLocation;" +
                    "varying vec2 vTextureLocation;" +
                    "uniform mat4 uTransformMatrix;" +
                    "void main() {" +
                    "   vTextureLocation = aTextureLocation.xy;" +
                    "   gl_Position = uTransformMatrix * aVertex;" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "" +
                    "precision mediump float;" +
                    "uniform sampler2D t_texture1;" +
                    "varying vec2 vTextureLocation;" +
                    "uniform vec3 uTintColor;" +
                    "void main() {" +
                    "   vec4 textureColor = texture2D(t_texture1, vTextureLocation);" +
                    "   gl_FragColor = textureColor * vec4(uTintColor.rgb, 1.);" +
                    "}";

    public final int aPositionHandle;
    public final int aTextureHandle;
    public final int uTintColorHandle;
    public final int uTransformMatrixHandle;

    public TransformableTintTextureSprite2DShader(int programId) {
        super(programId);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "aVertex");
        aTextureHandle = GLES20.glGetAttribLocation(programId, "aTextureLocation");
        uTintColorHandle = GLES20.glGetUniformLocation(programId, "uTintColor");
        uTransformMatrixHandle = GLES20.glGetUniformLocation(programId, "uTransformMatrix");
    }

    public static TransformableTintTextureSprite2DShader newInstance() {
        return new TransformableTintTextureSprite2DShader(compile(
                VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE
        ));
    }
}
