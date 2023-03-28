package org.telegram.messenger.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class ImmutableByteArrayOutputStream extends OutputStream {

    public byte buf[];

    protected int count;

    public ImmutableByteArrayOutputStream() {
        this(32);
    }

    public ImmutableByteArrayOutputStream(int size) {
        buf = new byte[size];
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - buf.length > 0) {
            grow(minCapacity);
        }
    }

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private void grow(int minCapacity) {
        int oldCapacity = buf.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        buf = Arrays.copyOf(buf, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    public synchronized void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    public void writeInt(int value) {
        ensureCapacity(count + 4);
        buf[count] = (byte) (value >>> 24);
        buf[count + 1] = (byte) (value >>> 16);
        buf[count + 2] = (byte) (value >>> 8);
        buf[count + 3] = (byte) (value);
        count += 4;
    }

    public void writeLong(long value) {
        ensureCapacity(count + 8);
        buf[count] = (byte) (value >>> 56);
        buf[count + 1] = (byte) (value >>> 48);
        buf[count + 2] = (byte) (value >>> 40);
        buf[count + 3] = (byte) (value >>> 32);
        buf[count + 4] = (byte) (value >>> 24);
        buf[count + 5] = (byte) (value >>> 16);
        buf[count + 6] = (byte) (value >>> 8);
        buf[count + 7] = (byte) (value);
        count += 8;
    }

    public synchronized void write(byte b[], int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public synchronized void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    public synchronized void reset() {
        count = 0;
    }

}
