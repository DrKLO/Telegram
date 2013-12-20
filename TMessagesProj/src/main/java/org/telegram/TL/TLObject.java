/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.TL;

import org.telegram.messenger.SerializedData;

public class TLObject {
    public TLObject () {

    }

    public void readParams(SerializedData stream) {

    }

    public byte[] serialize () {
        return null;
    }

    public void serializeToStream(SerializedData stream) {

    }

    public Class<? extends TLObject> responseClass () {
        return this.getClass();
    }

    public int layer () {
       return 8;
    }

    public void parseVector(TLRPC.Vector vector, SerializedData data) {

    }
}
