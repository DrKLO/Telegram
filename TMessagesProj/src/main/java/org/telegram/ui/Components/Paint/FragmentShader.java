package org.telegram.ui.Components.Paint;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class FragmentShader extends Shader {

    private static final String VERTEX_SHADER = "" +
            "attribute vec4 a_position;\n" +
            "void main() {\n" +
            "    gl_Position = a_position;\n" +
            "}";

    private static final float[] VERTICES = new float[]{ -1.0f, 1.0f,  -1.0f, -1.0f,  1.0f, -1.0f,  1.0f, 1.0f };
    private static final short[] ORDER = new short[]{ 0, 1, 2, 0, 2, 3 };

    private final FloatBuffer verticesBuffer = ByteBuffer.allocateDirect(VERTICES.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();

    private final ShortBuffer orderBuffer = ByteBuffer.allocateDirect(ORDER.length * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();

    private final int locAttrPosition;

    public FragmentShader(String fragmentShader) {
        super(VERTEX_SHADER, fragmentShader, new String[]{}, new String[]{});

        verticesBuffer.put(VERTICES);
        verticesBuffer.position(0);

        orderBuffer.put(ORDER);
        orderBuffer.position(0);

        locAttrPosition = GLES20.glGetAttribLocation(program, "a_position");
    }

    public void draw() {
        GLES20.glUseProgram(program);
        GLES20.glVertexAttribPointer(locAttrPosition, 2, GLES20.GL_FLOAT, false, 2 * 4, verticesBuffer);
        GLES20.glEnableVertexAttribArray(locAttrPosition);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, orderBuffer);
    }
}
