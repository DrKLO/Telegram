package org.telegram.tgnet;

public interface InputSerializedData {
    TLDataSourceType getDataSourceType();

    boolean readBool(boolean exception);
    int readInt32(boolean exception);
    long readInt64(boolean exception);
    byte readByte(boolean exception);
    void readBytes(byte[] b, boolean exception);
    byte[] readData(int count, boolean exception);
    String readString(boolean exception);
    byte[] readByteArray(boolean exception);
    float readFloat(boolean exception);
    double readDouble(boolean exception);
    NativeByteBuffer readByteBuffer(boolean exception);

    int length();
    void skip(int count);
    int getPosition();
    int remaining();

}
