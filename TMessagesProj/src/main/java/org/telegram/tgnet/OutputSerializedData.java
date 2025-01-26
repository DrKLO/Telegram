package org.telegram.tgnet;

public interface OutputSerializedData {

    void writeInt32(int x);
    void writeInt64(long x);
    void writeBool(boolean value);
    void writeBytes(byte[] b);
    void writeBytes(byte[] b, int offset, int count);
    void writeByte(int i);
    void writeByte(byte b);
    void writeString(String s);
    void writeByteArray(byte[] b, int offset, int count);
    void writeByteArray(byte[] b);
    void writeFloat(float f);
    void writeDouble(double d);
    void writeByteBuffer(NativeByteBuffer buffer);

    void skip(int count);
    int getPosition();

}
