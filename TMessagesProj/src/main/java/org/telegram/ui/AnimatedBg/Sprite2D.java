package org.telegram.ui.AnimatedBg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class Sprite2D {

    protected final FloatBuffer vertexBuffer;
    protected final ShortBuffer drawListBuffer;

    static final int COORDS_PER_VERTEX = 3;
    private static final float[] SQUARE_COORDS = {
            -1f, 1f, 0.0f,   // top left
            -1f, -1f, 0.0f,   // bottom left
            1f, -1f, 0.0f,   // bottom right
            1f, 1f, 0.0f}; // top right

    protected static final short[] DRAW_ORDER = {0, 1, 2, 0, 2, 3}; // order to draw vertices

    protected final int vertexCount = SQUARE_COORDS.length / COORDS_PER_VERTEX;
    protected final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    public Sprite2D() {
        ByteBuffer bb = ByteBuffer.allocateDirect(SQUARE_COORDS.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(SQUARE_COORDS);
        vertexBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(DRAW_ORDER.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(DRAW_ORDER);
        drawListBuffer.position(0);
    }
}
