package org.telegram.tgnet;

public abstract class AbstractSerializedData {

    public abstract void writeInt32(int x);

    public abstract void writeInt64(long x);

    public abstract void writeBool(boolean value);

    public abstract void writeBytes(byte[] b);

    public abstract void writeBytes(byte[] b, int offset, int count);

    public abstract void writeByte(int i);

    public abstract void writeByte(byte b);

    public abstract void writeString(String s);

    public abstract void writeByteArray(byte[] b, int offset, int count);

    public abstract void writeByteArray(byte[] b);

    public abstract void writeDouble(double d);

    public abstract void writeByteBuffer(NativeByteBuffer buffer);

    public abstract int readInt32(boolean exception);

    public abstract boolean readBool(boolean exception);

    public abstract long readInt64(boolean exception);

    public abstract void readBytes(byte[] b, boolean exception);

    public abstract byte[] readData(int count, boolean exception);

    public abstract String readString(boolean exception);

    public abstract byte[] readByteArray(boolean exception);

    public abstract NativeByteBuffer readByteBuffer(boolean exception);

    public abstract double readDouble(boolean exception);

    public abstract int length();

    public abstract void skip(int count);

    public abstract int getPosition();
}
