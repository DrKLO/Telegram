/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.tgnet;

import org.telegram.messenger.FileLog;

import java.util.HashMap;

public class TLClassStore {
    private HashMap<Integer, Class> classStore;

    public TLClassStore() {
        classStore = new HashMap<>();

        classStore.put(TLRPC.TL_error.constructor, TLRPC.TL_error.class);
        classStore.put(TLRPC.TL_decryptedMessageService.constructor, TLRPC.TL_decryptedMessageService.class);
        classStore.put(TLRPC.TL_decryptedMessage.constructor, TLRPC.TL_decryptedMessage.class);
        classStore.put(TLRPC.TL_config.constructor, TLRPC.TL_config.class);
        classStore.put(TLRPC.TL_decryptedMessageLayer.constructor, TLRPC.TL_decryptedMessageLayer.class);
        classStore.put(TLRPC.TL_decryptedMessageService_old.constructor, TLRPC.TL_decryptedMessageService_old.class);
        classStore.put(TLRPC.TL_decryptedMessage_old.constructor, TLRPC.TL_decryptedMessage_old.class);
        classStore.put(TLRPC.TL_message_secret.constructor, TLRPC.TL_message_secret.class);
        classStore.put(TLRPC.TL_messageEncryptedAction.constructor, TLRPC.TL_messageEncryptedAction.class);
        classStore.put(TLRPC.TL_decryptedMessageHolder.constructor, TLRPC.TL_decryptedMessageHolder.class);
        classStore.put(TLRPC.TL_null.constructor, TLRPC.TL_null.class);

        classStore.put(TLRPC.TL_updateShortChatMessage.constructor, TLRPC.TL_updateShortChatMessage.class);
        classStore.put(TLRPC.TL_updates.constructor, TLRPC.TL_updates.class);
        classStore.put(TLRPC.TL_updateShortMessage.constructor, TLRPC.TL_updateShortMessage.class);
        classStore.put(TLRPC.TL_updateShort.constructor, TLRPC.TL_updateShort.class);
        classStore.put(TLRPC.TL_updatesCombined.constructor, TLRPC.TL_updatesCombined.class);
        classStore.put(TLRPC.TL_updateShortSentMessage.constructor, TLRPC.TL_updateShortSentMessage.class);
        classStore.put(TLRPC.TL_updatesTooLong.constructor, TLRPC.TL_updatesTooLong.class);

        classStore.put(TLRPC.TL_video.constructor, TLRPC.TL_video.class);
        classStore.put(TLRPC.TL_videoEmpty.constructor, TLRPC.TL_videoEmpty.class);
        classStore.put(TLRPC.TL_video_old2.constructor, TLRPC.TL_video_old2.class);
        classStore.put(TLRPC.TL_video_old.constructor, TLRPC.TL_video_old.class);
        classStore.put(TLRPC.TL_videoEncrypted.constructor, TLRPC.TL_videoEncrypted.class);
        classStore.put(TLRPC.TL_video_old3.constructor, TLRPC.TL_video_old3.class);

        classStore.put(TLRPC.TL_audio.constructor, TLRPC.TL_audio.class);
        classStore.put(TLRPC.TL_audioEncrypted.constructor, TLRPC.TL_audioEncrypted.class);
        classStore.put(TLRPC.TL_audioEmpty.constructor, TLRPC.TL_audioEmpty.class);
        classStore.put(TLRPC.TL_audio_old.constructor, TLRPC.TL_audio_old.class);
        classStore.put(TLRPC.TL_audio_old2.constructor, TLRPC.TL_audio_old2.class);

        classStore.put(TLRPC.TL_document.constructor, TLRPC.TL_document.class);
        classStore.put(TLRPC.TL_documentEmpty.constructor, TLRPC.TL_documentEmpty.class);
        classStore.put(TLRPC.TL_documentEncrypted_old.constructor, TLRPC.TL_documentEncrypted_old.class);
        classStore.put(TLRPC.TL_documentEncrypted.constructor, TLRPC.TL_documentEncrypted.class);
        classStore.put(TLRPC.TL_document_old.constructor, TLRPC.TL_document_old.class);

        classStore.put(TLRPC.TL_photo.constructor, TLRPC.TL_photo.class);
        classStore.put(TLRPC.TL_photoEmpty.constructor, TLRPC.TL_photoEmpty.class);
        classStore.put(TLRPC.TL_photoSize.constructor, TLRPC.TL_photoSize.class);
        classStore.put(TLRPC.TL_photoSizeEmpty.constructor, TLRPC.TL_photoSizeEmpty.class);
        classStore.put(TLRPC.TL_photoCachedSize.constructor, TLRPC.TL_photoCachedSize.class);
        classStore.put(TLRPC.TL_photo_old.constructor, TLRPC.TL_photo_old.class);
        classStore.put(TLRPC.TL_photo_old2.constructor, TLRPC.TL_photo_old2.class);
    }

    static TLClassStore store = null;

    public static TLClassStore Instance() {
        if (store == null) {
            store = new TLClassStore();
        }
        return store;
    }

    public TLObject TLdeserialize(NativeByteBuffer stream, int constructor, boolean exception) {
        Class objClass = classStore.get(constructor);
        if (objClass != null) {
            TLObject response;
            try {
                response = (TLObject) objClass.newInstance();
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
                return null;
            }
            response.readParams(stream, exception);
            return response;
        }
        return null;
    }
}
