package org.telegram.objects;

import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;

import java.lang.reflect.Field;

public abstract class ExtendedAttachmentObject<T extends TLObject> extends AttachmentObject<T> {
    private long id;
    private long access_hash;
    private int dc_id;
    private byte[] key;
    private byte[] iv;

    protected ExtendedAttachmentObject(Class<?> clazz, T rawObject) {
        super(clazz, rawObject);
        try {
            Field id = clazz.getField("id");
            this.id = id.getLong(rawObject);
            Field access_hash = clazz.getField("access_hash");
            this.access_hash = access_hash.getLong(rawObject);
            Field dc_id = clazz.getField("dc_id");
            this.dc_id = dc_id.getInt(rawObject);
            Field key = clazz.getField("key");
            this.key = (byte[]) key.get(rawObject);
            Field iv = clazz.getField("iv");
            this.iv = (byte[]) iv.get(rawObject);
        } catch(NoSuchFieldException e) {
            throw new IllegalStateException(e);
        } catch(IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isEncrypted() {
        return false;
    }

    public TLRPC.InputFileLocation getFileLocation() {
        TLRPC.InputFileLocation fileLocation = getAttachmentFileLocation();
        fileLocation.id = id;
        fileLocation.access_hash = access_hash;
        return fileLocation;
    }

    public abstract TLRPC.InputFileLocation getAttachmentFileLocation();

    @Override
    public String getAttachmentFileName() {
        return dc_id + "_" + id + getAttachmentFileExtension();
    }

    public long getId() {
        return id;
    }

    public long getAccess_hash() {
        return access_hash;
    }

    public int getDc_id() {
        return dc_id;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getIv() {
        return iv;
    }
}
