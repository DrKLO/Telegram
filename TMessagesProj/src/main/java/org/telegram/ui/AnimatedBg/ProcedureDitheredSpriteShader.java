package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

public class ProcedureDitheredSpriteShader extends Shader {

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
                    "precision highp float;" +
                    "const highp float NOISE_GRANULARITY = .5/255.0;" +
                    "uniform sampler2D u_texture0;" +
                    "uniform vec2 u_screen_size;" +
                    "varying vec2 v_texcoord;" +
                    "highp float random(vec2 coords) {" +
                    "   return fract(sin(dot(coords.xy, vec2(12.9898,78.233))) * 43758.5453);" +
                    "}" +
                    "void main() {" +
                    "   vec4 color = texture2D(u_texture0, v_texcoord);" +
                    "   highp vec2 coordinates = gl_FragCoord.xy / u_screen_size;" +
                    "   vec3 colorR = color.rgb + mix(-NOISE_GRANULARITY, NOISE_GRANULARITY, random(coordinates));" +
                    "   gl_FragColor = vec4(colorR, 1.0);" +
                    "}";

    public final int aPositionHandle;
    public final int aTexturePositionHandle;
    public final int aTextureHandle;
    public final int aScreenSize;


    public ProcedureDitheredSpriteShader(int programId) {
        super(programId);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "a_vertex");
        aTexturePositionHandle = GLES20.glGetAttribLocation(programId, "a_texcoord");
        aTextureHandle = GLES20.glGetUniformLocation(programId, "u_texture0");
        aScreenSize = GLES20.glGetUniformLocation(programId, "u_screen_size");
    }

    public static ProcedureDitheredSpriteShader newInstance() {

        return new ProcedureDitheredSpriteShader(compile(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE));
    }
}