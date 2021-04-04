package org.telegram.ui.Animations;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;

import androidx.annotation.ColorInt;

import org.telegram.ui.Components.GLTextureView;
import org.telegram.ui.Components.Paint.FragmentShader;

public class GradientGLDrawer implements GLTextureView.Drawer {

    private static final int colorSize = 3;
    private static final int pointSize = 2;

    private final String fragmentShaderSource = "precision mediump float;uniform vec2 u_resolution;uniform vec3 u_color1;uniform vec3 u_color2;uniform vec3 u_color3;uniform vec3 u_color4;uniform vec2 u_point1;uniform vec2 u_point2;uniform vec2 u_point3;uniform vec2 u_point4;vec4 ones = vec4(1.0);float getWeight(vec2 uv, vec2 point) { float distance = max(length(uv - point), 0.01); return 1.0 / (distance * distance);}void main() { vec2 uv = gl_FragCoord.xy / u_resolution; vec4 weights = vec4(getWeight(uv, u_point1), getWeight(uv, u_point2), getWeight(uv, u_point3), getWeight(uv, u_point4)); float wSum = dot(weights, ones); vec3 wColors = (weights.x * u_color1 + weights.y * u_color2 + weights.z * u_color3 + weights.w * u_color4) / wSum; wColors = pow(wColors, vec3(0.85)); gl_FragColor = vec4(wColors.rgb, 1.0);}";;
    private FragmentShader shader;

    private final float[] colors = new float[colorSize * AnimationsController.backPointsCount];
    private final float[] points = new float[pointSize * AnimationsController.backPointsCount];
    private float width;
    private float height;

    private int locResolution = -1;
    private int locColor1 = -1;
    private int locColor2 = -1;
    private int locColor3 = -1;
    private int locColor4 = -1;
    private int locPoint1 = -1;
    private int locPoint2 = -1;
    private int locPoint3 = -1;
    private int locPoint4 = -1;

    public GradientGLDrawer(Context context) {
        for (int i = 0; i != 4; ++i) {
            int color = AnimationsController.getInstance().getBackgroundCurrentColor(i);
            float x = AnimationsController.getBackgroundPointX(0, i);
            float y = AnimationsController.getBackgroundPointY(0, i);
            setColorPoint(i, color, x, y);
        }
    }

    @Override
    public void init(int width, int height) {
        setSize(width, height);

        shader = new FragmentShader(fragmentShaderSource);
        int program = shader.getProgram();
        locResolution = GLES20.glGetUniformLocation(program, "u_resolution");

        locColor1 = GLES20.glGetUniformLocation(program, "u_color1");
        locColor2 = GLES20.glGetUniformLocation(program, "u_color2");
        locColor3 = GLES20.glGetUniformLocation(program, "u_color3");
        locColor4 = GLES20.glGetUniformLocation(program, "u_color4");

        locPoint1 = GLES20.glGetUniformLocation(program, "u_point1");
        locPoint2 = GLES20.glGetUniformLocation(program, "u_point2");
        locPoint3 = GLES20.glGetUniformLocation(program, "u_point3");
        locPoint4 = GLES20.glGetUniformLocation(program, "u_point4");
    }

    @Override
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void draw() {
        if (shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.getProgram());
        GLES20.glUniform2f(locResolution, width, height);

        GLES20.glUniform3fv(locColor1, 1, colors, 0);
        GLES20.glUniform3fv(locColor2, 1, colors, colorSize);
        GLES20.glUniform3fv(locColor3, 1, colors, colorSize * 2);
        GLES20.glUniform3fv(locColor4, 1, colors, colorSize * 3);

        GLES20.glUniform2fv(locPoint1, 1, points, 0);
        GLES20.glUniform2fv(locPoint2, 1, points, pointSize);
        GLES20.glUniform2fv(locPoint3, 1, points, pointSize * 2);
        GLES20.glUniform2fv(locPoint4, 1, points, pointSize * 3);

        shader.draw();
    }

    @Override
    public void release() {
        if (shader != null) {
            shader.cleanResources();
            shader = null;
        }
    }

    void setColor(int idx, @ColorInt int color) {
        colors[idx * 3] = Color.red(color) / 255f;
        colors[idx * 3 + 1] = Color.green(color) / 255f;
        colors[idx * 3 + 2] = Color.blue(color) / 255f;
    }

    /**
     * x from left to right
     * y from top to bottom
     */
    void setPosition(int idx, float x, float y) {
        points[idx * 2] = x;
        points[idx * 2 + 1] = 1f - y;
    }

    void setColorPoint(int idx, @ColorInt int color, float x, float y) {
        setColor(idx, color);
        setPosition(idx, x, y);
    }
}
