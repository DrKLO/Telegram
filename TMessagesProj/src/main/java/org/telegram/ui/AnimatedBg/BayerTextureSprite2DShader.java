package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

public class BayerTextureSprite2DShader extends Shader {

    private static final String VERTEX_SHADER_CODE =
            "" +
                    "attribute vec4 a_vertex;" +
                    "attribute vec2 a_texcoord;" +
                    "varying vec2 v_texcoord;" +
                    "void main() {" +
                    "   v_texcoord = a_texcoord.xy;" +
                    "   gl_Position = a_vertex;" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "" +
                    "precision mediump float;" +
                    "uniform sampler2D u_texture0;" +
                    "uniform sampler2D u_texture1;" +
                    "varying vec2 v_texcoord;" +
                    "void main() {" +
                    "   vec4 color = texture2D(u_texture0, v_texcoord);" +
                    "   gl_FragColor = color + vec4(texture2D(u_texture1, gl_FragCoord.xy / 8.0).r / 32.0 - (1.0 / 128.0));" +
                    "}";

    public final int aPositionHandle;
    public final int aTexturePositionHandle;
    public final int aTextureHandle;
    public final int aBayerTextureHandle;


    public BayerTextureSprite2DShader(int programId) {
        super(programId);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "a_vertex");
        aTexturePositionHandle = GLES20.glGetAttribLocation(programId, "a_texcoord");
        aTextureHandle = GLES20.glGetUniformLocation(programId, "u_texture0");
        aBayerTextureHandle = GLES20.glGetUniformLocation(programId, "u_texture1");
    }

    public static BayerTextureSprite2DShader newInstance() {

        return new BayerTextureSprite2DShader(compile(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE));
    }
}