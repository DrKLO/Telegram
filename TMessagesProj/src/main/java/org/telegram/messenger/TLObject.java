/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

public class TLObject {

    public boolean disableFree = false;

    public TLObject() {

    }

    public void readParams(AbsSerializedData stream, boolean exception) {

    }

    public void serializeToStream(AbsSerializedData stream) {

    }

    public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
        return null;
    }

    public int layer() {
        return 11;
    }

    public void freeResources() {

    }

    public int getObjectSize() {
        ByteBufferDesc bufferDesc = new ByteBufferDesc(true);
        serializeToStream(bufferDesc);
        return bufferDesc.length();
    }
}
