package org.telegram.ui.Components.Paint;

import android.graphics.Color;
import android.opengl.GLES20;
import android.util.Log;

import org.telegram.messenger.FileLog;

import java.util.HashMap;
import java.util.Map;

public class Shader {

    protected int program;
    private int vertexShader;
    private int fragmentShader;

    protected Map<String, Integer> uniformsMap = new HashMap<>();

    public Shader(String vertexShader, String fragmentShader, String attributes[], String uniforms[]) {
        this.program = GLES20.glCreateProgram();

        CompilationResult vResult = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        if (vResult.status == GLES20.GL_FALSE) {
            FileLog.e("Vertex shader compilation failed");
            destroyShader(vResult.shader, 0, program);
            return;
        }

        CompilationResult fResult = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        if (fResult.status == GLES20.GL_FALSE) {
            FileLog.e("Fragment shader compilation failed");
            destroyShader(vResult.shader, fResult.shader, program);
            return;
        }

        GLES20.glAttachShader(program, vResult.shader);
        GLES20.glAttachShader(program, fResult.shader);

        for (int i = 0; i < attributes.length; i++) {
            GLES20.glBindAttribLocation(program, i, attributes[i]);
        }

        if (linkProgram(program) == GLES20.GL_FALSE) {
            destroyShader(vResult.shader, fResult.shader, program);
            return;
        }

        for (String uniform : uniforms) {
            uniformsMap.put(uniform, GLES20.glGetUniformLocation(program, uniform));
        }

        if (vResult.shader != 0) {
            GLES20.glDeleteShader(vResult.shader);
        }

        if (fResult.shader != 0) {
            GLES20.glDeleteShader(fResult.shader);
        }
    }

    public void cleanResources() {
        if (program != 0) {
            GLES20.glDeleteProgram(vertexShader);
            program = 0;
        }
    }

    private class CompilationResult {
        int shader;
        int status;

        CompilationResult(int shader, int status) {
            this.shader = shader;
            this.status = status;
        }
    }

    public int getUniform(String key) {
        return uniformsMap.get(key);
    }

    private CompilationResult compileShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == GLES20.GL_FALSE) {
            FileLog.e(GLES20.glGetShaderInfoLog(shader));
        }

        return new CompilationResult(shader, compileStatus[0]);
    }

    private int linkProgram(int program) {
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GLES20.GL_FALSE) {
            FileLog.e(GLES20.glGetProgramInfoLog(program));
        }

        return linkStatus[0];
    }

    private void destroyShader(int vertexShader, int fragmentShader, int program) {
        if (vertexShader != 0) {
            GLES20.glDeleteShader(vertexShader);
        }

        if (fragmentShader != 0) {
            GLES20.glDeleteShader(fragmentShader);
        }

        if (program != 0) {
            GLES20.glDeleteProgram(vertexShader);
        }
    }

    public static void SetColorUniform(int location, int color) {
        float r = Color.red(color) / 255.0f;
        float g = Color.green(color) / 255.0f;
        float b = Color.blue(color) / 255.0f;
        float a = Color.alpha(color) / 255.0f;

        GLES20.glUniform4f(location, r, g, b, a);
    }
}
