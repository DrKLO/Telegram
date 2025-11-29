package org.telegram.tgnet;

import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class Vector<T extends TLObject> extends TLObject {
    public static final int constructor = 0x1cb5c415;

    private final TLDeserializer<T> itemDeserializer;
    public final ArrayList<T> objects = new ArrayList<>();

    public Vector(TLDeserializer<T> itemDeserializer) {
        this.itemDeserializer = itemDeserializer;
    }

    public static <T extends TLObject> Vector<T> TLDeserialize(
        InputSerializedData stream,
        int constructor,
        boolean exception,
        TLDeserializer<T> deserializer
    ) {
        if (constructor != Vector.constructor) {
            TLParseException.doThrowOrLog(stream, "Vector", constructor, exception);
            return null;
        }

        Vector<T> vector = new Vector<T>(deserializer);
        vector.readParams(stream, exception);
        return vector;
    }

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        final int size = stream.readInt32(exception);
        for (int i = 0; i < size; ++i) {
            objects.add(itemDeserializer.deserialize(stream, stream.readInt32(exception), exception));
        }
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        serialize(stream, objects);
    }

    @FunctionalInterface
    public interface TLDeserializer<T extends TLObject> {
        T deserialize(InputSerializedData stream, int constructor, boolean exception);
    }



    public static class Int extends TLObject {
        public int value;

        public Int(int x) {
            this.value = x;
        }

        public static Int TLDeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return new Int(constructor);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            value = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(value);
        }
    }

    public static Vector<Int> TLDeserializeInt(InputSerializedData stream, int constructor, boolean exception) {
        if (constructor != Vector.constructor) {
            TLParseException.doThrowOrLog(stream, "StarGift", constructor, exception);
            return null;
        }

        Vector<Int> vector = new Vector<Int>(Int::TLDeserialize);
        vector.readParams(stream, exception);
        return vector;
    }

    public ArrayList<Integer> toIntArray() {
        ArrayList<Integer> result = new ArrayList<>();
        for (T item : objects) {
            if (item instanceof Int) {
                result.add(((Int) item).value);
            }
        }
        return result;
    }


    public static class Long extends TLObject {
        public long value;

        public Long(int a, int b) {
            this.value = ((long) a << 32) | (b & 0xFFFFFFFFL);
        }

        public static Long TLDeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return new Long(constructor, stream.readInt32(exception));
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            value = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt64(value);
        }
    }

    public static Vector<Int> TLDeserializeLong(InputSerializedData stream, int constructor, boolean exception) {
        if (constructor != Vector.constructor) {
            TLParseException.doThrowOrLog(stream, "Vector", constructor, exception);
            return null;
        }

        Vector<Int> vector = new Vector<Int>(Int::TLDeserialize);
        vector.readParams(stream, exception);
        return vector;
    }

    public ArrayList<java.lang.Long> toLongArray() {
        ArrayList<java.lang.Long> result = new ArrayList<>();
        for (T item : objects) {
            if (item instanceof Vector.Long) {
                result.add(((Vector.Long) item).value);
            }
        }
        return result;
    }





    public static <T extends TLObject> void serialize(OutputSerializedData stream, final ArrayList<T> objects) {
        stream.writeInt32(constructor);
        stream.writeInt32(objects.size());
        for (int i = 0; i < objects.size(); ++i) {
            objects.get(i).serializeToStream(stream);
        }
    }
    public static <T> void serialize(OutputSerializedData stream, Utilities.Callback<T> write, final ArrayList<T> objects) {
        stream.writeInt32(constructor);
        stream.writeInt32(objects.size());
        for (int i = 0; i < objects.size(); ++i) {
            write.run(objects.get(i));
        }
    }
    public static void serializeInt(OutputSerializedData stream, final ArrayList<java.lang.Integer> objects) {
        serialize(stream, stream::writeInt32, objects);
    }
    public static void serializeLong(OutputSerializedData stream, final ArrayList<java.lang.Long> objects) {
        serialize(stream, stream::writeInt64, objects);
    }
    public static void serializeString(OutputSerializedData stream, final ArrayList<String> objects) {
        serialize(stream, stream::writeString, objects);
    }

    public static <T> ArrayList<T> deserialize(InputSerializedData stream, Utilities.CallbackReturn<Boolean, T> read, boolean exception) {
        final int magic = stream.readInt32(exception);
        if (magic != Vector.constructor) {
            TLParseException.doThrowOrLog(stream, "Vector", magic, exception);
            return new ArrayList<>();
        }
        final int size = stream.readInt32(exception);
        final ArrayList<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            result.add(read.run(exception));
        }
        return result;
    }
    public static ArrayList<java.lang.Integer> deserializeInt(InputSerializedData stream, boolean exception) {
        return deserialize(stream, stream::readInt32, exception);
    }
    public static ArrayList<java.lang.Long> deserializeLong(InputSerializedData stream, boolean exception) {
        return deserialize(stream, stream::readInt64, exception);
    }
    public static ArrayList<java.lang.String> deserializeString(InputSerializedData stream, boolean exception) {
        return deserialize(stream, stream::readString, exception);
    }
    public static ArrayList<byte[]> deserializeByteArray(InputSerializedData stream, boolean exception) {
        return deserialize(stream, stream::readByteArray, exception);
    }
    public static <T extends TLObject> ArrayList<T> deserialize(InputSerializedData stream, TLDeserializer<T> deserializer, boolean exception) {
        final int magic = stream.readInt32(exception);
        if (magic != Vector.constructor) {
            TLParseException.doThrowOrLog(stream, "Vector", magic, exception);
            return new ArrayList<>();
        }

        final int size = stream.readInt32(exception);
        final ArrayList<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            T o = deserializer.deserialize(stream, stream.readInt32(exception), exception);
            if (o != null) {
                result.add(o);
            }
        }

        return result;
    }
}
