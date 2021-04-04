package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;

public class BlurSprite2DShader extends Shader {

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
                    "uniform sampler2D t_texture1;" +
                    "varying vec2 v_texcoord;" +
                    "uniform float resolution;" +
                    "uniform float radius;" +
                    "void main() {" +
                    "   vec4 sum = vec4(0.0);" +
                    "   vec2 tc = v_texcoord.xy;" +
                    "   float blur = radius / resolution;" +
                    "   float hstep = 1.0;" +
                    "   float vstep = 1.0;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x - 4.0*blur*hstep, tc.y - 4.0*blur*vstep)) * 0.0162162162;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x - 3.0*blur*hstep, tc.y - 3.0*blur*vstep)) * 0.0540540541;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x - 2.0*blur*hstep, tc.y - 2.0*blur*vstep)) * 0.1216216216;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x - 1.0*blur*hstep, tc.y - 1.0*blur*vstep)) * 0.1945945946;" +
                    "" +
                    "   sum += texture2D(t_texture1, vec2(tc.x, tc.y)) * 0.2270270270;" +
                    "" +
                    "   sum += texture2D(t_texture1, vec2(tc.x + 1.0*blur*hstep, tc.y + 1.0*blur*vstep)) * 0.1945945946;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x + 2.0*blur*hstep, tc.y + 2.0*blur*vstep)) * 0.1216216216;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x + 3.0*blur*hstep, tc.y + 3.0*blur*vstep)) * 0.0540540541;" +
                    "   sum += texture2D(t_texture1, vec2(tc.x + 4.0*blur*hstep, tc.y + 4.0*blur*vstep)) * 0.0162162162;" +
                    "   gl_FragColor = vec4(sum.rgb, 1.0);" +
                    "}";

    public final int aPositionHandle;
    public final int aTextureHandle;
    public final int radiusHandle;
    public final int resolutionHandle;

    public BlurSprite2DShader(int programId) {
        super(programId);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "a_vertex");
        aTextureHandle = GLES20.glGetAttribLocation(programId, "a_texcoord");
        radiusHandle = GLES20.glGetUniformLocation(programId, "radius");
        resolutionHandle = GLES20.glGetUniformLocation(programId, "resolution");
    }

    public static BlurSprite2DShader newInstance() {
        return new BlurSprite2DShader(compile(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE));
    }
}
