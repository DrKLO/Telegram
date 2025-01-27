/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.tgnet;

public class TLObject {

    public int networkType;

    public boolean disableFree = false;
    private static final ThreadLocal<NativeByteBuffer> sizeCalculator = new ThreadLocal<NativeByteBuffer>() {
        @Override
        protected NativeByteBuffer initialValue() {
            return new NativeByteBuffer(true);
        }
    };

    public TLObject() {}

    public void readParams(InputSerializedData stream, boolean exception) {

    }

    public void serializeToStream(OutputSerializedData stream) {

    }

    public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
        return null;
    }

    public void freeResources() {

    }

    public int getObjectSize() {
        NativeByteBuffer byteBuffer = sizeCalculator.get();
        byteBuffer.rewind();
        serializeToStream(sizeCalculator.get());
        return byteBuffer.length();
    }
}
