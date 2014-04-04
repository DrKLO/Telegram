package org.telegram.objects;

import org.telegram.messenger.TLObject;

import java.util.HashMap;
import java.util.Map;

public class AttachmentObjectWrapper {
    private static Map<Class<?>, AttachmentObjectFactory<? extends AttachmentObject>> mapping = new HashMap<Class<?>, AttachmentObjectFactory<? extends AttachmentObject>>(20);

    public <T> void addMapping(Class<T> clazz, AttachmentObjectFactory<? extends AttachmentObject> factoryMapping) {
        mapping.put(clazz, factoryMapping);
    }

    public static <T extends TLObject> AttachmentObject<T> wrap(Class<?> clazz, T rawObject) {
        if (!mapping.containsKey(clazz)) {
            throw new IllegalStateException("Factory for the class " + clazz + " not found");
        }
        return mapping.get(clazz).create(rawObject);
    }

    interface AttachmentObjectFactory<T extends AttachmentObject> {
        <T> T create(TLObject rawObject);
    }
}
