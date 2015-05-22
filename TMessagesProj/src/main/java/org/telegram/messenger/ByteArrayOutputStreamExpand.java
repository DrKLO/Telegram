/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import java.io.OutputStream;

public class ByteArrayOutputStreamExpand extends OutputStream {

    protected byte[] buf;
    protected int count;

    public ByteArrayOutputStreamExpand() {
        buf = new byte[32];
    }

    public ByteArrayOutputStreamExpand(int size) {
        if (size >= 0) {
            buf = new byte[size];
        } else {
            throw new IllegalArgumentException("size < 0");
        }
    }

    private void expand(int i) {
        if (count + i <= buf.length) {
            return;
        }

        byte[] newbuf = new byte[count + i];
        System.arraycopy(buf, 0, newbuf, 0, count);
        buf = newbuf;
    }

    public synchronized void reset() {
        count = 0;
    }

    public int size() {
        return count;
    }

    public byte[] toByteArray() {
        return buf;
    }

    @Override
    public String toString() {
        return new String(buf, 0, count);
    }

    @Override
    public void write(byte[] buffer, int offset, int len) {
        checkOffsetAndCount(buffer.length, offset, len);
        if (len == 0) {
            return;
        }
        expand(len);
        System.arraycopy(buffer, offset, buf, this.count, len);
        this.count += len;
    }

    @Override
    public void write(int oneByte) {
        if (count == buf.length) {
            expand(1);
        }
        buf[count++] = (byte) oneByte;
    }

    public void checkOffsetAndCount(int arrayLength, int offset, int count) {
        if ((offset | count) < 0 || offset > arrayLength || arrayLength - offset < count) {
            throw new ArrayIndexOutOfBoundsException("length=" + arrayLength + "; regionStart=" + offset + "; regionLength=" + count);
        }
    }
}
