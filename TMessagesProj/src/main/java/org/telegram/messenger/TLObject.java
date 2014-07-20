/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

public class TLObject {
    public boolean disableFree = false;

    public TLObject () {

    }

    public void readParams(AbsSerializedData stream) {

    }

    public byte[] serialize () {
        return null;
    }

    public void serializeToStream(AbsSerializedData stream) {

    }

    public Class<? extends TLObject> responseClass () {
        return this.getClass();
    }

    public int layer () {
       return 11;
    }

    public void parseVector(TLRPC.Vector vector, AbsSerializedData data) {

    }

    public void freeResources() {

    }

    public int getObjectSize() {
        ByteBufferDesc bufferDesc = new ByteBufferDesc(true);
        serializeToStream(bufferDesc);
        return bufferDesc.length();
    }
}
