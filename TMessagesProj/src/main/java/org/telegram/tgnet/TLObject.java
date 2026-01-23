/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.tgnet;

import me.vkryl.core.BitwiseUtils;

public class TLObject {

    public static final int FLAG_0  = 1;
    public static final int FLAG_1  = 1 << 1;
    public static final int FLAG_2  = 1 << 2;
    public static final int FLAG_3  = 1 << 3;
    public static final int FLAG_4  = 1 << 4;
    public static final int FLAG_5  = 1 << 5;
    public static final int FLAG_6  = 1 << 6;
    public static final int FLAG_7  = 1 << 7;
    public static final int FLAG_8  = 1 << 8;
    public static final int FLAG_9  = 1 << 9;
    public static final int FLAG_10 = 1 << 10;
    public static final int FLAG_11 = 1 << 11;
    public static final int FLAG_12 = 1 << 12;
    public static final int FLAG_13 = 1 << 13;
    public static final int FLAG_14 = 1 << 14;
    public static final int FLAG_15 = 1 << 15;
    public static final int FLAG_16 = 1 << 16;
    public static final int FLAG_17 = 1 << 17;
    public static final int FLAG_18 = 1 << 18;
    public static final int FLAG_19 = 1 << 19;
    public static final int FLAG_20 = 1 << 20;
    public static final int FLAG_21 = 1 << 21;
    public static final int FLAG_22 = 1 << 22;
    public static final int FLAG_23 = 1 << 23;
    public static final int FLAG_24 = 1 << 24;
    public static final int FLAG_25 = 1 << 25;
    public static final int FLAG_26 = 1 << 26;
    public static final int FLAG_27 = 1 << 27;
    public static final int FLAG_28 = 1 << 28;
    public static final int FLAG_29 = 1 << 29;
    public static final int FLAG_30 = 1 << 30;
    public static final int FLAG_31 = 1 << 31;

    public static int setFlag(int flags, int flag, boolean value) {
        return BitwiseUtils.setFlag(flags, flag, value);
    }

    public static boolean hasFlag(int flags, int flag) {
        return BitwiseUtils.hasFlag(flags, flag);
    }



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

    protected static <T extends TLObject> T TLdeserialize(
            Class<T> tClass,
            T object,
            InputSerializedData stream,
            int constructor,
            boolean exception
    ) {
        if (object == null) {
            TLParseException.doThrowOrLog(stream, tClass.getName(), constructor, exception);
            return null;
        }

        object.readParams(stream, exception);
        return object;
    }
}
