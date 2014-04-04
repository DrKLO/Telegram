package org.telegram.objects;

import org.telegram.messenger.TLObject;

import java.lang.reflect.Field;

public abstract class AttachmentObject<T extends TLObject> {
    protected final T rawObject;
    private int size;

    protected AttachmentObject(Class<?> clazz, T rawObject) {
        this.rawObject = rawObject;
        try {
            Field size = clazz.getField("size");
            this.size = size.getInt(rawObject);
        } catch(NoSuchFieldException e) {
            throw new IllegalStateException(e);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public int getSize() {
        return size;
    }

    public abstract String getAttachmentFileName();

    public abstract String getAttachmentFileExtension();
}
