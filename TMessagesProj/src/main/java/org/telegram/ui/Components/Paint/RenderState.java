package org.telegram.ui.Components.Paint;

import android.graphics.PointF;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RenderState {
    private static final int DEFAULT_STATE_SIZE = 256;

    public float baseWeight;
    public float spacing;
    public float alpha;
    public float angle;
    public float scale;
    public float viewportScale;

    public double remainder;

    private int count;
    private int allocatedCount;
    private ByteBuffer buffer;

    public int getCount() {
        return count;
    }

    public void prepare() {
        count = 0;

        if (buffer != null) {
            return;
        }

        allocatedCount = DEFAULT_STATE_SIZE;
        buffer = ByteBuffer.allocateDirect(allocatedCount * 5 * 4);
        buffer.order(ByteOrder.nativeOrder());
        buffer.position(0);
    }

    public float read() {
        return buffer.getFloat();
    }

    public void setPosition(int position) {
        if (buffer == null || position < 0 || position >= allocatedCount) {
            return;
        }
        buffer.position(position * 5 * 4);
    }

    public void appendValuesCount(int count) {
        int newTotalCount = this.count + count;

        if (newTotalCount > allocatedCount || buffer == null) {
            resizeBuffer();
        }

        this.count = newTotalCount;
    }

    public void resizeBuffer() {
        if (buffer != null) {
            buffer = null;
        }

        allocatedCount = Math.max(allocatedCount * 2, DEFAULT_STATE_SIZE);

        buffer = ByteBuffer.allocateDirect(allocatedCount * 5 * 4);
        buffer.order(ByteOrder.nativeOrder());
        buffer.position(0);
    }

    public boolean addPoint(PointF point, float size, float angle, float alpha, int index) {
        if (index != -1 && index >= allocatedCount || buffer.position() == buffer.limit()) {
            resizeBuffer();
            return false;
        }

        if (index != -1) {
            buffer.position(index * 5 * 4);
        }
        buffer.putFloat(point.x);
        buffer.putFloat(point.y);
        buffer.putFloat(size);
        buffer.putFloat(angle);
        buffer.putFloat(alpha);

        return true;
    }

    public void reset() {
        count = 0;
        remainder = 0;
        if (buffer != null) {
            buffer.position(0);
        }
    }
}