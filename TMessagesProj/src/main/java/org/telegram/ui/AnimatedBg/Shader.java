package org.telegram.ui.AnimatedBg;

import android.opengl.GLES20;
import android.util.Log;

public class Shader {

    public final int programId;

    public Shader(int programId) {
        this.programId = programId;
    }

    public void use() {
        GLES20.glUseProgram(programId);
    }

    public void delete() {
        GLES20.glDeleteProgram(programId);
    }

    public static int loadShader(int shaderType, String shaderSrc) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, shaderSrc);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(
                        "Error",
                        "Could not compile shader name:" + shaderSrc + " =>" + shaderType + ":"
                );
                Log.e("Error", GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        } else {
            throw new IllegalStateException("Can't create program");
        }
        return shader;
    }

    public static int compile(String vertexShader, String fragmentShader) {
        int vertexShaderHandle = loadShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShader
        );
        int fragmentShaderHandle = loadShader(
                GLES20.GL_FRAGMENT_SHADER, fragmentShader
        );

        // create empty OpenGL ES Program
        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShaderHandle);
        GLES20.glAttachShader(programId, fragmentShaderHandle);
        GLES20.glLinkProgram(programId);
        return programId;
    }
}