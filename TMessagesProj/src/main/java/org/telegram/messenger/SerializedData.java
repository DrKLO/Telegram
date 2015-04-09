/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class SerializedData extends AbsSerializedData {
    protected boolean isOut = true;
    private ByteArrayOutputStream outbuf;
    private DataOutputStream out;
    private ByteArrayInputStream inbuf;
    private DataInputStream in;
    private boolean justCalc = false;
    private int len;

    public SerializedData() {
        outbuf = new ByteArrayOutputStream();
        out = new DataOutputStream(outbuf);
    }

    public SerializedData(boolean calculate) {
        if (!calculate) {
            outbuf = new ByteArrayOutputStream();
            out = new DataOutputStream(outbuf);
        }
        justCalc = calculate;
        len = 0;
    }

    public SerializedData(int size) {
        outbuf = new ByteArrayOutputStream(size);
        out = new DataOutputStream(outbuf);
    }

    public SerializedData(byte[] data) {
        isOut = false;
        inbuf = new ByteArrayInputStream(data);
        in = new DataInputStream(inbuf);
    }

    public void cleanup() {
        try {
            if (inbuf != null) {
                inbuf.close();
                inbuf = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            if (in != null) {
                in.close();
                in = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            if (outbuf != null) {
                outbuf.close();
                outbuf = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public SerializedData(File file) throws Exception {
        FileInputStream is = new FileInputStream(file);
        byte[] data = new byte[(int)file.length()];
        new DataInputStream(is).readFully(data);
        is.close();

        isOut = false;
        inbuf = new ByteArrayInputStream(data);
        in = new DataInputStream(inbuf);
    }

    public void writeInt32(int x) {
        if (!justCalc) {
            writeInt32(x, out);
        } else {
            len += 4;
        }
    }

    private void writeInt32(int x, DataOutputStream out) {
        try {
            for(int i = 0; i < 4; i++) {
                out.write(x >> (i * 8));
            }
        } catch(Exception e) {
            FileLog.e("tmessages", "write int32 error");
        }
    }

    public void writeInt64(long i) {
        if (!justCalc) {
            writeInt64(i, out);
        } else {
            len += 8;
        }
    }

    private void writeInt64(long x, DataOutputStream out) {
        try {
            for(int i = 0; i < 8; i++) {
                out.write((int)(x >> (i * 8)));
            }
        } catch(Exception e) {
            FileLog.e("tmessages", "write int64 error");
        }
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

    public void writeByteBuffer(ByteBufferDesc buffer) {
        if (!justCalc) {
            //TODO ?
        } else {
            int l = buffer.limit();
            if (l <= 253) {
                len += 1;
            } else {
                len += 4;
            }
            len += l;
            int i = l <= 253 ? 1 : 4;
            while((l + i) % 4 != 0) {
                len += 1;
                i++;
            }
        }
    }

    public int readInt32() {
        return readInt32(null);
    }

    public int readInt32(boolean[] error) {
        try {
            int i = 0;
            for(int j = 0; j < 4; j++) {
                i |= (in.read() << (j * 8));
            }
            if (error != null) {
                error[0] = false;
            }
            return i;
        } catch(Exception x) {
            if (error != null) {
                error[0] = true;
            }
            FileLog.e("tmessages", "read int32 error");
        }
        return 0;
    }

    public long readInt64() {
        return readInt64(null);
    }

    public long readInt64(boolean[] error) {
        try {
            long i = 0;
            for(int j = 0; j < 8; j++) {
                i |= ((long)in.read() << (j * 8));
            }
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

    public void writeRaw(byte[] b) {
        try {
            if (!justCalc) {
                out.write(b);
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
                out.write(b, offset, count);
            } else {
                len += count;
            }
        } catch (Exception x) {
            FileLog.e("tmessages", "write raw error");
        }
    }

    public void writeByte(int i) {
        try {
            if (!justCalc) {
                out.writeByte((byte)i);
            } else {
                len += 1;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write byte error");
        }
    }

    public void writeByte(byte b) {
        try {
            if (!justCalc) {
                out.writeByte(b);
            } else {
                len += 1;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", "write byte error");
        }
    }

    public void readRaw(byte[] b) {
        try {
            in.read(b);
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
            int l = in.read();
            if(l >= 254) {
                l = in.read() | (in.read() << 8) | (in.read() << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            in.read(b);
            int i=sl;
            while((l + i) % 4 != 0) {
                in.read();
                i++;
            }
            return new String(b, "UTF-8");
        } catch (Exception x) {
            FileLog.e("tmessages", "read string error");
        }
        return null;
    }

    public byte[] readByteArray() {
        try {
            int sl = 1;
            int l = in.read();
            if (l >= 254) {
                l = in.read() | (in.read() << 8) | (in.read() << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            in.read(b);
            int i = sl;
            while((l + i) % 4 != 0) {
                in.read();
                i++;
            }
            return b;
        } catch (Exception x) {
            FileLog.e("tmessages", "read byte array error");
        }
        return null;
    }

    public ByteBufferDesc readByteBuffer() {
        throw new RuntimeException("SerializedData don't support readByteBuffer");
    }

    public void writeByteArray(byte[] b) {
        try {
            if (b.length <= 253) {
                if (!justCalc) {
                    out.write(b.length);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    out.write(254);
                    out.write(b.length);
                    out.write(b.length >> 8);
                    out.write(b.length >> 16);
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                out.write(b);
            } else {
                len += b.length;
            }
            int i = b.length <= 253 ? 1 : 4;
            while((b.length + i) % 4 != 0) {
                if (!justCalc) {
                    out.write(0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception x) {
            FileLog.e("tmessages", "write byte array error");
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
                    out.write(count);
                } else {
                    len += 1;
                }
            } else {
                if (!justCalc) {
                    out.write(254);
                    out.write(count);
                    out.write(count >> 8);
                    out.write(count >> 16);
                } else {
                    len += 4;
                }
            }
            if (!justCalc) {
                out.write(b, offset, count);
            } else {
                len += count;
            }
            int i = count <= 253 ? 1 : 4;
            while ((count + i) % 4 != 0) {
                if (!justCalc) {
                    out.write(0);
                } else {
                    len += 1;
                }
                i++;
            }
        } catch (Exception x) {
            FileLog.e("tmessages", "write byte array error");
        }
    }

    public double readDouble() {
        try {
            return Double.longBitsToDouble(readInt64());
        } catch(Exception x) {
            FileLog.e("tmessages", "read double error");
        }
        return 0;
    }

    public void writeDouble(double d) {
        try {
            writeInt64(Double.doubleToRawLongBits(d));
        } catch(Exception x) {
            FileLog.e("tmessages", "write double error");
        }
    }

    public int length() {
        if (!justCalc) {
            return isOut ? outbuf.size() : inbuf.available();
        }
        return len;
    }

    protected void set(byte[] newData) {
        isOut = false;
        inbuf = new ByteArrayInputStream(newData);
        in = new DataInputStream(inbuf);
    }

    public byte[] toByteArray() {
        return outbuf.toByteArray();
    }
}
