/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferDesc extends AbsSerializedData {
    public ByteBuffer buffer;
    private boolean justCalc = false;
    private int len = 0;

    public ByteBufferDesc(int size) {
        buffer = ByteBuffer.allocateDirect(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public ByteBufferDesc(boolean calculate) {
        justCalc = calculate;
    }

    public ByteBufferDesc(byte[] bytes) {
        buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
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
        buffer.rewind();
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

    public void writeRaw(byte[] b) {
        try {
            if (!justCalc) {
                buffer.put(b);
            } else {
                len += b.length;
            }
        } catch (Exception x) {
            FileLog.e("tmessages", "write raw error");
        }
    }

    public void writeRaw(byte[] b, int offset, int count) {
        try {
            if (!justCalc) {
                buffer.put(b, offset, count);
            } else {
                len += count;
            }
        } catch (Exception x) {
            FileLog.e("tmessages", "write raw error");
        }
    }

    public void writeByte(int i) {
        writeByte((byte)i);
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
        } catch(Exception x) {
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
        } catch (Exception x) {
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
        } catch (Exception x) {
            FileLog.e("tmessages", "write byte array error");
        }
    }

    public void writeDouble(double d) {
        try {
            writeInt64(Double.doubleToRawLongBits(d));
        } catch(Exception x) {
            FileLog.e("tmessages", "write double error");
        }
    }

    public void writeByteBuffer(ByteBufferDesc b) {
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

    public void writeRaw(ByteBufferDesc b) {
        if (justCalc) {
            len += b.limit();
        } else {
            b.rewind();
            buffer.put(b.buffer);
        }
    }

    public int readInt32() {
        return readInt32(null);
    }

    public int readInt32(boolean[] error) {
        try {
            int i = buffer.getInt();
            if (error != null) {
                error[0] = false;
            }
            return i;
        } catch (Exception x) {
            if (error != null) {
                error[0] = true;
            }
            FileLog.e("tmessages", "read int32 error");
        }
        return 0;
    }

    public boolean readBool() {
        int consructor = readInt32();
        if (consructor == 0x997275b5) {
            return true;
        } else if (consructor == 0xbc799737) {
            return false;
        }
        FileLog.e("tmessages", "Not bool value!");
        return false;
    }

    public long readInt64() {
        return readInt64(null);
    }

    public long readInt64(boolean[] error) {
        try {
            long i = buffer.getLong();
            if (error != null) {
                error[0] = false;
            }
            return i;
        } catch (Exception x) {
            if (error != null) {
                error[0] = true;
            }
            FileLog.e("tmessages", "read int64 error");
        }
        return 0;
    }

    public void readRaw(byte[] b) {
        try {
            buffer.get(b);
        } catch (Exception x) {
            FileLog.e("tmessages", "read raw error");
        }
    }

    public byte[] readData(int count) {
        byte[] arr = new byte[count];
        readRaw(arr);
        return arr;
    }

    public String readString() {
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
        } catch (Exception x) {
            FileLog.e("tmessages", "read string error");
        }
        return null;
    }

    public int getIntFromByte(byte b) {
        return b >= 0 ? b : ((int)b) + 256;
    }

    public byte[] readByteArray() {
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
        } catch (Exception x) {
            FileLog.e("tmessages", "read byte array error");
        }
        return null;
    }

    public ByteBufferDesc readByteBuffer() {
        try {
            int sl = 1;
            int l = getIntFromByte(buffer.get());
            if (l >= 254) {
                l = getIntFromByte(buffer.get()) | (getIntFromByte(buffer.get()) << 8) | (getIntFromByte(buffer.get()) << 16);
                sl = 4;
            }
            ByteBufferDesc b = BuffersStorage.getInstance().getFreeBuffer(l);
            if (b != null) {
                int old = buffer.limit();
                buffer.limit(buffer.position() + l);
                b.buffer.put(buffer);
                buffer.limit(old);
                b.buffer.position(0);
            }
            int i = sl;
            while((l + i) % 4 != 0) {
                buffer.get();
                i++;
            }
            return b;
        } catch (Exception x) {
            FileLog.e("tmessages", "read byte array error");
        }
        return null;
    }

    public double readDouble() {
        try {
            return Double.longBitsToDouble(readInt64());
        } catch(Exception x) {
            FileLog.e("tmessages", "read double error");
        }
        return 0;
    }

    public int length() {
        if (!justCalc) {
            return buffer.position();
        }
        return len;
    }
}
