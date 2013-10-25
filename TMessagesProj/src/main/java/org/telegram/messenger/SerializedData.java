/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SerializedData {
    protected boolean isOut = true;
    private ByteArrayOutputStream outbuf;
    private DataOutputStream out;
    private ByteArrayInputStream inbuf;
    private DataInputStream in;

    public SerializedData() {
        outbuf = new ByteArrayOutputStream();
        out = new DataOutputStream(outbuf);
    }

    public SerializedData(byte[] data){
        isOut = false;
        inbuf = new ByteArrayInputStream(data);
        in = new DataInputStream(inbuf);
    }

    public SerializedData(File file) throws IOException {
        FileInputStream is = new FileInputStream(file);
        byte[] data = new byte[(int)file.length()];
        new DataInputStream(is).readFully(data);
        is.close();

        isOut = false;
        inbuf = new ByteArrayInputStream(data);
        in = new DataInputStream(inbuf);
    }

    public void writeInt32(int x){
        writeInt32(x, out);
    }

    protected void writeInt32(int x, DataOutputStream out){
        try {
            for(int i = 0; i < 4; i++){
                out.write(x >> (i * 8));
            }
        } catch(IOException gfdsgd) {
            Log.e("tmessages", "write int32 error");
        }
    }

    public void writeInt64(long i) {
        writeInt64(i, out);
    }

    protected void writeInt64(long x, DataOutputStream out){
        try {
            for(int i = 0; i < 8; i++){
                out.write((int)(x >> (i * 8)));
            }
        } catch(IOException gfdsgd) {
            Log.e("tmessages", "write int64 error");
        }
    }

    public boolean readBool() {
        int consructor = readInt32();
        if (consructor == 0x997275b5) {
            return true;
        } else if (consructor == 0xbc799737) {
            return false;
        }
        Log.e("tmessages", "Not bool value!");
        return false;
    }

    public void writeBool(boolean value) {
        if (value) {
            writeInt32(0x997275b5);
        } else {
            writeInt32(0xbc799737);
        }
    }

    public int readInt32(){
        try {
            int i = 0;
            for(int j = 0; j < 4; j++){
                i |= (in.read() << (j * 8));
            }
            return i;
        } catch(IOException x) {
            Log.e("tmessages", "read int32 error");
        }
        return 0;
    }

    public long readInt64(){
        try {
            long i = 0;
            for(int j = 0; j < 8; j++){
                i |= ((long)in.read() << (j * 8));
            }
            return i;
        } catch(IOException x) {
            Log.e("tmessages", "read int64 error");
        }
        return 0;
    }

    public void writeRaw(byte[] b){
        try {
            out.write(b);
        } catch(Exception x) {
            Log.e("tmessages", "write raw error");
        }
    }

    public void writeRaw(byte[] b, int offset, int count) {
        try {
            out.write(b, offset, count);
        } catch(Exception x) {
            Log.e("tmessages", "write raw error");
        }
    }

    public void writeByte(int i) {
        try {
            out.writeByte((byte)i);
        } catch (Exception e) {
            Log.e("tmessages", "write byte error");
        }
    }

    public void writeByte(byte b) {
        try {
            out.writeByte(b);
        } catch (Exception e) {
            Log.e("tmessages", "write byte error");
        }
    }

    public void readRaw(byte[] b){
        try {
            in.read(b);
        } catch(Exception x) {
            Log.e("tmessages", "read raw error");
        }
    }

    public byte[] readData(int count) {
        byte[] arr = new byte[count];
        readRaw(arr);
        return arr;
    }

    public String readString(){
        try {
            int sl = 1;
            int l = in.read();
            if(l >= 254){
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
        } catch(Exception x) {
            Log.e("tmessages", "read string error");
        }
        return null;
    }

    public byte[] readByteArray() {
        try {
            int sl = 1;
            int l = in.read();
            if (l >= 254){
                l = in.read() | (in.read() << 8) | (in.read() << 16);
                sl = 4;
            }
            byte[] b = new byte[l];
            in.read(b);
            int i = sl;
            while((l + i) % 4 != 0){
                in.read();
                i++;
            }
            return b;
        } catch(Exception x) {
            Log.e("tmessages", "read byte array error");
        }
        return null;
    }

    public void writeByteArray(byte[] b){
        try {
            if (b.length <= 253){
                out.write(b.length);
            } else {
                out.write(254);
                out.write(b.length);
                out.write(b.length >> 8);
                out.write(b.length >> 16);
            }
            out.write(b);
            int i = b.length <= 253 ? 1 : 4;
            while((b.length + i) % 4 != 0){
                out.write(0);
                i++;
            }
        }catch(Exception x) {
            Log.e("tmessages", "write byte array error");
        }
    }

    public void writeString(String s){
        try {
            writeByteArray(s.getBytes("UTF-8"));
        } catch(Exception x) {
            Log.e("tmessages", "write string error");
        }
    }

    public void writeByteArray(byte[] b, int offset, int count) {
        try {
            if(count <= 253){
                out.write(count);
            } else {
                out.write(254);
                out.write(count);
                out.write(count >> 8);
                out.write(count >> 16);
            }
            out.write(b, offset, count);
            int i = count <= 253 ? 1 : 4;
            while ((count + i) % 4 != 0){
                out.write(0);
                i++;
            }
        } catch(Exception x) {
            Log.e("tmessages", "write byte array error");
        }
    }

    public double readDouble(){
        try {
            return Double.longBitsToDouble(readInt64());
        } catch(Exception x) {
            Log.e("tmessages", "read double error");
        }
        return 0;
    }

    public void writeDouble(double d){
        try {
            writeInt64(Double.doubleToRawLongBits(d));
        } catch(Exception x) {
            Log.e("tmessages", "write double error");
        }
    }

    public int length() {
        return isOut ? outbuf.size() : inbuf.available();
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
