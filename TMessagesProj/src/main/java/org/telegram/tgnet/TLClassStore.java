/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.tgnet;

import android.util.SparseArray;

import org.telegram.messenger.FileLog;

public class TLClassStore {
    private interface TLObjectFactory {
        TLObject create();
    }

    private final SparseArray<TLObjectFactory> classStore;

    public TLClassStore() {
        classStore = new SparseArray<>();

        classStore.put(TLRPC.TL_error.constructor, TLRPC.TL_error::new);
        classStore.put(TLRPC.TL_decryptedMessageService.constructor, TLRPC.TL_decryptedMessageService::new);
        classStore.put(TLRPC.TL_decryptedMessage.constructor, TLRPC.TL_decryptedMessage::new);
        classStore.put(TLRPC.TL_decryptedMessageLayer.constructor, TLRPC.TL_decryptedMessageLayer::new);
        classStore.put(TLRPC.TL_decryptedMessage_layer17.constructor, TLRPC.TL_decryptedMessage::new);
        classStore.put(TLRPC.TL_decryptedMessage_layer45.constructor, TLRPC.TL_decryptedMessage_layer45::new);
        classStore.put(TLRPC.TL_decryptedMessageService_layer8.constructor, TLRPC.TL_decryptedMessageService_layer8::new);
        classStore.put(TLRPC.TL_decryptedMessage_layer8.constructor, TLRPC.TL_decryptedMessage_layer8::new);
        classStore.put(TLRPC.TL_message_secret.constructor, TLRPC.TL_message_secret::new);
        classStore.put(TLRPC.TL_message_secret_layer72.constructor, TLRPC.TL_message_secret_layer72::new);
        classStore.put(TLRPC.TL_message_secret_old.constructor, TLRPC.TL_message_secret_old::new);
        classStore.put(TLRPC.TL_messageEncryptedAction.constructor, TLRPC.TL_messageEncryptedAction::new);
        classStore.put(TLRPC.TL_null.constructor, TLRPC.TL_null::new);
    }

    static TLClassStore store = null;

    public static TLClassStore Instance() {
        if (store == null) {
            store = new TLClassStore();
        }
        return store;
    }

    public TLObject TLdeserialize(NativeByteBuffer stream, int constructor, boolean exception) {
        TLObjectFactory factory = classStore.get(constructor);
        if (factory != null) {
            TLObject response = factory.create();
            response.readParams(stream, exception);
            return response;
        }
        return null;
    }
}
