package org.telegram.tgnet;

public class TLEnum {
    public interface Constructor {
        int getConstructor();

        default void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(getConstructor());
        }
    }

    public static <E extends Enum<E> & Constructor> E TLdeserialize(Class<E> enumClass, int constructor, boolean exception) {
        final E result = fromConstructor(enumClass, constructor);

        if (result == null) {
            if (exception) {
                throw new RuntimeException(String.format("can't parse magic %x in %s", constructor, enumClass.getName()));
            }

            return null;
        }

        return result;
    }

    public static <E extends Enum<E> & Constructor> E fromConstructor(Class<E> enumClass, int constructor) {
        E[] enums = enumClass.getEnumConstants();
        if (enums == null) {
            return null;
        }

        for (E e : enums) {
            if (e.getConstructor() == constructor) {
                return e;
            }
        }
        return null;
    }
}
