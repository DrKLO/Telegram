/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import java.util.HashMap;

public class TLClassStore {
    private HashMap<Integer, Class> classStore;

    public TLClassStore() {
        classStore = new HashMap<>();

        classStore.put(TLRPC.TL_futuresalts.constructor, TLRPC.TL_futuresalts.class);
        classStore.put(TLRPC.TL_msg_new_detailed_info.constructor, TLRPC.TL_msg_new_detailed_info.class);
        classStore.put(TLRPC.TL_msg_detailed_info.constructor, TLRPC.TL_msg_detailed_info.class);
        classStore.put(TLRPC.TL_error.constructor, TLRPC.TL_error.class);
        classStore.put(TLRPC.TL_auth_authorization.constructor, TLRPC.TL_auth_authorization.class);
        classStore.put(TLRPC.TL_dh_gen_retry.constructor, TLRPC.TL_dh_gen_retry.class);
        classStore.put(TLRPC.TL_dh_gen_fail.constructor, TLRPC.TL_dh_gen_fail.class);
        classStore.put(TLRPC.TL_dh_gen_ok.constructor, TLRPC.TL_dh_gen_ok.class);
        classStore.put(TLRPC.TL_server_DH_inner_data.constructor, TLRPC.TL_server_DH_inner_data.class);
        classStore.put(TLRPC.TL_msgs_ack.constructor, TLRPC.TL_msgs_ack.class);
        classStore.put(TLRPC.TL_futureSalt.constructor, TLRPC.TL_futureSalt.class);
        classStore.put(TLRPC.TL_msg_resend_req.constructor, TLRPC.TL_msg_resend_req.class);
        classStore.put(TLRPC.TL_rpc_error.constructor, TLRPC.TL_rpc_error.class);
        classStore.put(TLRPC.TL_rpc_req_error.constructor, TLRPC.TL_rpc_req_error.class);
        classStore.put(TLRPC.TL_decryptedMessageService.constructor, TLRPC.TL_decryptedMessageService.class);
        classStore.put(TLRPC.TL_decryptedMessage.constructor, TLRPC.TL_decryptedMessage.class);
        classStore.put(TLRPC.TL_bad_msg_notification.constructor, TLRPC.TL_bad_msg_notification.class);
        classStore.put(TLRPC.TL_bad_server_salt.constructor, TLRPC.TL_bad_server_salt.class);
        classStore.put(TLRPC.TL_new_session_created.constructor, TLRPC.TL_new_session_created.class);
        classStore.put(TLRPC.TL_resPQ.constructor, TLRPC.TL_resPQ.class);
        classStore.put(TLRPC.TL_config.constructor, TLRPC.TL_config.class);
        classStore.put(TLRPC.TL_msg_copy.constructor, TLRPC.TL_msg_copy.class);
        classStore.put(TLRPC.TL_pong.constructor, TLRPC.TL_pong.class);
        classStore.put(TLRPC.TL_rpc_answer_unknown.constructor, TLRPC.TL_rpc_answer_unknown.class);
        classStore.put(TLRPC.TL_rpc_answer_dropped.constructor, TLRPC.TL_rpc_answer_dropped.class);
        classStore.put(TLRPC.TL_rpc_answer_dropped_running.constructor, TLRPC.TL_rpc_answer_dropped_running.class);
        classStore.put(TLRPC.TL_rpc_result.constructor, TLRPC.TL_rpc_result.class);
        classStore.put(TLRPC.TL_auth_exportedAuthorization.constructor, TLRPC.TL_auth_exportedAuthorization.class);
        classStore.put(TLRPC.TL_destroy_session_ok.constructor, TLRPC.TL_destroy_session_ok.class);
        classStore.put(TLRPC.TL_destroy_session_none.constructor, TLRPC.TL_destroy_session_none.class);
        classStore.put(TLRPC.TL_msgs_state_req.constructor, TLRPC.TL_msgs_state_req.class);
        classStore.put(TLRPC.TL_server_DH_params_fail.constructor, TLRPC.TL_server_DH_params_fail.class);
        classStore.put(TLRPC.TL_server_DH_params_ok.constructor, TLRPC.TL_server_DH_params_ok.class);
        classStore.put(TLRPC.TL_protoMessage.constructor, TLRPC.TL_protoMessage.class);
        classStore.put(TLRPC.TL_msgs_all_info.constructor, TLRPC.TL_msgs_all_info.class);
        classStore.put(TLRPC.TL_p_q_inner_data.constructor, TLRPC.TL_p_q_inner_data.class);
        classStore.put(TLRPC.TL_updateShortChatMessage.constructor, TLRPC.TL_updateShortChatMessage.class);
        classStore.put(TLRPC.TL_updates.constructor, TLRPC.TL_updates.class);
        classStore.put(TLRPC.TL_updateShortMessage.constructor, TLRPC.TL_updateShortMessage.class);
        classStore.put(TLRPC.TL_updateShort.constructor, TLRPC.TL_updateShort.class);
        classStore.put(TLRPC.TL_updatesCombined.constructor, TLRPC.TL_updatesCombined.class);
        classStore.put(TLRPC.TL_updatesTooLong.constructor, TLRPC.TL_updatesTooLong.class);
        classStore.put(TLRPC.TL_msgs_state_info.constructor, TLRPC.TL_msgs_state_info.class);
        classStore.put(TLRPC.TL_decryptedMessageLayer.constructor, TLRPC.TL_decryptedMessageLayer.class);
        classStore.put(TLRPC.TL_http_wait.constructor, TLRPC.TL_http_wait.class);
        classStore.put(TLRPC.TL_gzip_packed.constructor, TLRPC.TL_gzip_packed.class);
        classStore.put(TLRPC.TL_decryptedMessageService_old.constructor, TLRPC.TL_decryptedMessageService_old.class);
        classStore.put(TLRPC.TL_decryptedMessage_old.constructor, TLRPC.TL_decryptedMessage_old.class);
        classStore.put(TLRPC.TL_message_secret.constructor, TLRPC.TL_message_secret.class);
        classStore.put(TLRPC.TL_messageEncryptedAction.constructor, TLRPC.TL_messageEncryptedAction.class);
        classStore.put(TLRPC.TL_decryptedMessageHolder.constructor, TLRPC.TL_decryptedMessageHolder.class);
        classStore.put(TLRPC.TL_client_DH_inner_data.constructor, TLRPC.TL_client_DH_inner_data.class);
        classStore.put(TLRPC.TL_null.constructor, TLRPC.TL_null.class);
        classStore.put(TLRPC.TL_destroy_sessions_res.constructor, TLRPC.TL_destroy_sessions_res.class);
        classStore.put(TLRPC.TL_msg_container.constructor, TLRPC.TL_msg_container.class);


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

    public TLObject TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
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
