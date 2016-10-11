package org.telegram.tgnet;

import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NativeByteBuffer extends AbstractSerializedData {

    protected int address;
    public ByteBuffer buffer;
    private boolean justCalc;
    private int len;
    public boolean reused = true;

    private static final ThreadLocal<NativeByteBuffer> addressWrapper = new ThreadLocal<NativeByteBuffer>() {
        @Override
        protected NativeByteBuffer initialValue() {
            return new NativeByteBuffer(0, true);
        }
    };

    public static NativeByteBuffer wrap(int address) {
        NativeByteBuffer result = addressWrapper.get();
        if (address != 0) {
            if (!result.reused) {
                FileLog.e("tmessages", "forgot to reuse?");
            }
            result.address = address;
            result.reused = false;
            result.buffer = native_getJavaByteBuffer(address);
            result.buffer.limit(native_limit(address));
            int position = native_position(address);
            if (position <= result.buffer.limit()) {
                result.buffer.position(position);
            }
            result.buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return result;
    }

    private NativeByteBuffer(int address, boolean wrap) {

    }

    public NativeByteBuffer(int size) throws Exception {
        if (size >= 0) {
            address = native_getFreeBuffer(size);
            if (address != 0) {
                buffer = native_getJavaByteBuffer(address);
                buffer.position(0);
                buffer.limit(size);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
        } else {
            throw new Exception("invalid NativeByteBuffer size");
        }
    }

    public NativeByteBuffer(boolean calculate) {
        justCalc = calculate;
    }

    public int position() {
        return buffer.position();
    }

    public void position(int position) {
        buffer.position(position);
    }

    public int capacity() {
        return buffer.capacity();
    }

    public int limit() {
        return buffer.limit();
    }

    public void limit(int limit) {
        buffer.limit(limit);
    }

    public void put(ByteBuffer buff) {
        buffer.put(buff);
    }

    public void rewind() {
        if (justCalc) {
            len = 0;
        } else {
            buffer.rewind();
        }
    }

    public void compact() {
        buffer.compact();
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public void writeInt32(int x) {
        try {
            if (!justCalc) {
                buffer.putInt(x);
            } else {
                len += 4;
            }
        } catch(Exception e) {
            FileLog.e("tmessages", "write int32 error");
        }
    }

    public void writeInt64(long x) {
        try {
            if (!justCalc) {
                buffer.putLong(x);
            } else {
                len += 8;
            }
        } catch(Exception e) {
            FileLog.e("tmessages", "write int64 error");
        }
    }

    public void writeBool(boolean value) {
        if (!justCalc) {
            if (value) {
                writeInt32(0x997275b5);
            } else {
                writeInt32(0xbc799737);
            }
        } else {
            len += 4;
        }
    }

    public void writeBytes(byte[] b) {
        try {
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += b.length;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write raw error");
        }
    }

    public void writeBytes(byte[] b, int offset, int count) {
        try {
            if (!justCalc) {
                buffer.put(b, offset, count);
            } else {
                len += count;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write raw error");
        }
    }

    public void writeByte(int i) {
        writeByte((byte) i);
    }

    public void writeByte(byte b) {
        try {
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += 1;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write byte error");
        }
    }

    public void writeString(String s) {
        try {
            writeByteArray(s.getBytes("UTF-8"));
        } catch(Exception e) {
            FileLog.e("tmessages", "write string error");
        }
    }

    public void writeByteArray(byte[] b, int offset, int count) {
        try {
            if(count <= 253) {
                if (!justCalc) {
                    buffer.put((byte)count);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    buffer.put((byte)254);
                    buffer.put((byte)count);
                    buffer.put((byte)(count >> 8));
                    buffer.put((byte)(count >> 16));
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                buffer.put(b, offset, count);
            } else {
                len += count;
            }
            int i = count <= 253 ? 1 : 4;
            while ((count + i) % 4 != 0) {
                if (!justCalc) {
                    buffer.put((byte)0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write byte array error");
        }
    }

    public void writeByteArray(byte[] b) {
        try {
            if (b.length <= 253) {
                if (!justCalc) {
                    buffer.put((byte) b.length);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    buffer.put((byte) 254);
                    buffer.put((byte) b.length);
                    buffer.put((byte) (b.length >> 8));
                    buffer.put((byte) (b.length >> 16));
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += b.length;
            }
            int i = b.length <= 253 ? 1 : 4;
            while((b.length + i) % 4 != 0) {
                if (!justCalc) {
                    buffer.put((byte) 0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write byte array error");
        }
    }

    public void writeDouble(double d) {
        try {
            writeInt64(Double.doubleToRawLongBits(d));
        } catch(Exception e) {
            FileLog.e("tmessages", "write double error");
        }
    }

    public void writeByteBuffer(NativeByteBuffer b) {
        try {
            int l = b.limit();
            if (l <= 253) {
                if (!justCalc) {
                    buffer.put((byte) l);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    buffer.put((byte) 254);
                    buffer.put((byte) l);
                    buffer.put((byte) (l >> 8));
                    buffer.put((byte) (l >> 16));
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                b.rewind();
                buffer.put(b.buffer);
            } else {
                len += l;
            }
            int i = l <= 253 ? 1 : 4;
            while((l + i) % 4 != 0) {
                if (!justCalc) {
                    buffer.put((byte) 0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void writeBytes(NativeByteBuffer b) {
        if (justCalc) {
            len += b.limit();
        } else {
            b.rewind();
            buffer.put(b.buffer);
        }
    }

    public int getIntFromByte(byte b) {
        return b >= 0 ? b : ((int)b) + 256;
    }

    public int length() {
        if (!justCalc) {
            return buffer.position();
        }
        return len;
    }

    public void skip(int count) {
        if (count == 0) {
            return;
        }
        if (!justCalc) {
            buffer.position(buffer.position() + count);
        } else {
            len += count;
        }
    }

    public int getPosition() {
        return buffer.position();
    }

    public int readInt32(boolean exception) {
        try {
            return buffer.getInt();
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read int32 error", e);
            } else {
                FileLog.e("tmessages", "read int32 error");
            }
        }
        return 0;
    }

    public boolean readBool(boolean exception) {
        int consructor = readInt32(exception);
        if (consructor == 0x997275b5) {
            return true;
        } else if (consructor == 0xbc799737) {
            return false;
        }
        if (exception) {
            throw new RuntimeException("Not bool value!");
        } else {
            FileLog.e("tmessages", "Not bool value!");
        }
        return false;
    }

    public long readInt64(boolean exception) {
        try {
            return buffer.getLong();
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read int64 error", e);
            } else {
                FileLog.e("tmessages", "read int64 error");
            }
        }
        return 0;
    }

    public void readBytes(byte[] b, boolean exception) {
        try {
            buffer.get(b);
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read raw error", e);
            } else {
                FileLog.e("tmessages", "read raw error");
            }
        }
    }

    public byte[] readData(int count, boolean exception) {
        byte[] arr = new byte[count];
        readBytes(arr, exception);
        return arr;
    }

    public String readString(boolean exception) {
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if(l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            buffer.get(b);
            int i = sl;
            while((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return new String(b, "UTF-8");
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read string error", e);
            } else {
                FileLog.e("tmessages", "read string error");
            }
        }
        return "";
    }

    public byte[] readByteArray(boolean exception) {
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if (l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            buffer.get(b);
            int i = sl;
            while((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return b;
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read byte array error", e);
            } else {
                FileLog.e("tmessages", "read byte array error");
            }
        }
        return new byte[0];
    }

    public NativeByteBuffer readByteBuffer(boolean exception) {
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if (l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            NativeByteBuffer b = new NativeByteBuffer(l);
            int old = buffer.limit();
            buffer.limit(buffer.position() + l);
            b.buffer.put(buffer);
            buffer.limit(old);
            b.buffer.position(0);
            int i = sl;
            while((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return b;
        } catch (Exception e) {
            if (exception) {
                throw new RuntimeException("read byte array error", e);
            } else {
                FileLog.e("tmessages", "read byte array error");
            }
        }
        return null;
    }

    public double readDouble(boolean exception) {
        try {
            return Double.longBitsToDouble(readInt64(exception));
        } catch(Exception e) {
            if (exception) {
                throw new RuntimeException("read double error", e);
            } else {
                FileLog.e("tmessages", "read double error");
            }
        }
        return 0;
    }

    public void reuse() {
        if (address != 0) {
            reused = true;
            native_reuse(address);
        }
    }

    public static native int native_getFreeBuffer(int length);
    public static native ByteBuffer native_getJavaByteBuffer(int address);
    public static native int native_limit(int address);
    public static native int native_position(int address);
    public static native void native_reuse(int address);
}
