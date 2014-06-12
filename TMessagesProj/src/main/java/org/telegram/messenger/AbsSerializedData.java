/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

public abstract class AbsSerializedData {
    public abstract void writeInt32(int x);
    public abstract void writeInt64(long x);
    public abstract void writeBool(boolean value);
    public abstract void writeRaw(byte[] b);
    public abstract void writeRaw(byte[] b, int offset, int count);
    public abstract void writeByte(int i);
    public abstract void writeByte(byte b);
    public abstract void writeString(String s);
    public abstract void writeByteArray(byte[] b, int offset, int count);
    public abstract void writeByteArray(byte[] b);
    public abstract void writeDouble(double d);
    public abstract int readInt32();
    public abstract int readInt32(boolean[] error);
    public abstract boolean readBool();
    public abstract long readInt64();
    public abstract long readInt64(boolean[] error);
    public abstract void readRaw(byte[] b);
    public abstract byte[] readData(int count);
    public abstract String readString();
    public abstract byte[] readByteArray();
    public abstract ByteBufferDesc readByteBuffer();
    public abstract void writeByteBuffer(ByteBufferDesc buffer);
    public abstract double readDouble();
    public abstract int length();
}
