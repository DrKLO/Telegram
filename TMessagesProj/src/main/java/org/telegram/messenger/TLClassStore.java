/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.HashMap;

public class TLClassStore {
    private HashMap<Integer, Class> classStore;

    public TLClassStore () {
        classStore = new HashMap<Integer, Class>();

        classStore.put(TLRPC.TL_chatPhotoEmpty.constructor, TLRPC.TL_chatPhotoEmpty.class);
        classStore.put(TLRPC.TL_chatPhoto.constructor, TLRPC.TL_chatPhoto.class);
        classStore.put(TLRPC.TL_futuresalts.constructor, TLRPC.TL_futuresalts.class);
        classStore.put(TLRPC.TL_bad_msg_notification.constructor, TLRPC.TL_bad_msg_notification.class);
        classStore.put(TLRPC.TL_bad_server_salt.constructor, TLRPC.TL_bad_server_salt.class);
        classStore.put(TLRPC.TL_error.constructor, TLRPC.TL_error.class);
        classStore.put(TLRPC.TL_messages_sentEncryptedMessage.constructor, TLRPC.TL_messages_sentEncryptedMessage.class);
        classStore.put(TLRPC.TL_messages_sentEncryptedFile.constructor, TLRPC.TL_messages_sentEncryptedFile.class);
        classStore.put(TLRPC.TL_auth_checkedPhone.constructor, TLRPC.TL_auth_checkedPhone.class);
        classStore.put(TLRPC.TL_msgs_ack.constructor, TLRPC.TL_msgs_ack.class);
        classStore.put(TLRPC.TL_messages_chatFull.constructor, TLRPC.TL_messages_chatFull.class);
        classStore.put(TLRPC.TL_rpc_result.constructor, TLRPC.TL_rpc_result.class);
        classStore.put(TLRPC.TL_contactStatus.constructor, TLRPC.TL_contactStatus.class);
        classStore.put(TLRPC.TL_auth_authorization.constructor, TLRPC.TL_auth_authorization.class);
        classStore.put(TLRPC.TL_messages_messages.constructor, TLRPC.TL_messages_messages.class);
        classStore.put(TLRPC.TL_messages_messagesSlice.constructor, TLRPC.TL_messages_messagesSlice.class);
        classStore.put(TLRPC.TL_rpc_answer_unknown.constructor, TLRPC.TL_rpc_answer_unknown.class);
        classStore.put(TLRPC.TL_rpc_answer_dropped.constructor, TLRPC.TL_rpc_answer_dropped.class);
        classStore.put(TLRPC.TL_rpc_answer_dropped_running.constructor, TLRPC.TL_rpc_answer_dropped_running.class);
        classStore.put(TLRPC.TL_contacts_link.constructor, TLRPC.TL_contacts_link.class);
        classStore.put(TLRPC.TL_peerUser.constructor, TLRPC.TL_peerUser.class);
        classStore.put(TLRPC.TL_peerChat.constructor, TLRPC.TL_peerChat.class);
        classStore.put(TLRPC.TL_encryptedFile.constructor, TLRPC.TL_encryptedFile.class);
        classStore.put(TLRPC.TL_encryptedFileEmpty.constructor, TLRPC.TL_encryptedFileEmpty.class);
        classStore.put(TLRPC.TL_destroy_session_ok.constructor, TLRPC.TL_destroy_session_ok.class);
        classStore.put(TLRPC.TL_destroy_session_none.constructor, TLRPC.TL_destroy_session_none.class);
        classStore.put(TLRPC.TL_updates_differenceEmpty.constructor, TLRPC.TL_updates_differenceEmpty.class);
        classStore.put(TLRPC.TL_updates_differenceSlice.constructor, TLRPC.TL_updates_differenceSlice.class);
        classStore.put(TLRPC.TL_updates_difference.constructor, TLRPC.TL_updates_difference.class);
        classStore.put(TLRPC.TL_geoPointEmpty.constructor, TLRPC.TL_geoPointEmpty.class);
        classStore.put(TLRPC.TL_geoPoint.constructor, TLRPC.TL_geoPoint.class);
        classStore.put(TLRPC.TL_help_appUpdate.constructor, TLRPC.TL_help_appUpdate.class);
        classStore.put(TLRPC.TL_help_noAppUpdate.constructor, TLRPC.TL_help_noAppUpdate.class);
        classStore.put(TLRPC.TL_messageForwarded.constructor, TLRPC.TL_messageForwarded.class);
        classStore.put(TLRPC.TL_messageEmpty.constructor, TLRPC.TL_messageEmpty.class);
        classStore.put(TLRPC.TL_message.constructor, TLRPC.TL_message.class);
        classStore.put(TLRPC.TL_messageService.constructor, TLRPC.TL_messageService.class);
        classStore.put(TLRPC.TL_inputPhoneContact.constructor, TLRPC.TL_inputPhoneContact.class);
        classStore.put(TLRPC.TL_invokeAfterMsg.constructor, TLRPC.TL_invokeAfterMsg.class);
        classStore.put(TLRPC.TL_messageMediaVideo.constructor, TLRPC.TL_messageMediaVideo.class);
        classStore.put(TLRPC.TL_messageMediaPhoto.constructor, TLRPC.TL_messageMediaPhoto.class);
        classStore.put(TLRPC.TL_messageMediaDocument.constructor, TLRPC.TL_messageMediaDocument.class);
        classStore.put(TLRPC.TL_messageMediaGeo.constructor, TLRPC.TL_messageMediaGeo.class);
        classStore.put(TLRPC.TL_messageMediaEmpty.constructor, TLRPC.TL_messageMediaEmpty.class);
        classStore.put(TLRPC.TL_messageMediaAudio.constructor, TLRPC.TL_messageMediaAudio.class);
        classStore.put(TLRPC.TL_messageMediaContact.constructor, TLRPC.TL_messageMediaContact.class);
        classStore.put(TLRPC.TL_messageMediaUnsupported.constructor, TLRPC.TL_messageMediaUnsupported.class);
        classStore.put(TLRPC.TL_auth_sentCode.constructor, TLRPC.TL_auth_sentCode.class);
        classStore.put(TLRPC.TL_peerNotifySettingsEmpty.constructor, TLRPC.TL_peerNotifySettingsEmpty.class);
        classStore.put(TLRPC.TL_peerNotifySettings.constructor, TLRPC.TL_peerNotifySettings.class);
        classStore.put(TLRPC.TL_msg_resend_req.constructor, TLRPC.TL_msg_resend_req.class);
        classStore.put(TLRPC.TL_http_wait.constructor, TLRPC.TL_http_wait.class);
        classStore.put(TLRPC.TL_contacts_blocked.constructor, TLRPC.TL_contacts_blocked.class);
        classStore.put(TLRPC.TL_contacts_blockedSlice.constructor, TLRPC.TL_contacts_blockedSlice.class);
        classStore.put(TLRPC.TL_inputGeoPoint.constructor, TLRPC.TL_inputGeoPoint.class);
        classStore.put(TLRPC.TL_inputGeoPointEmpty.constructor, TLRPC.TL_inputGeoPointEmpty.class);
        classStore.put(TLRPC.TL_help_inviteText.constructor, TLRPC.TL_help_inviteText.class);
        classStore.put(TLRPC.TL_messages_dhConfigNotModified.constructor, TLRPC.TL_messages_dhConfigNotModified.class);
        classStore.put(TLRPC.TL_messages_dhConfig.constructor, TLRPC.TL_messages_dhConfig.class);
        classStore.put(TLRPC.TL_audioEmpty.constructor, TLRPC.TL_audioEmpty.class);
        classStore.put(TLRPC.TL_audio.constructor, TLRPC.TL_audio.class);
        classStore.put(TLRPC.TL_destroy_sessions_res.constructor, TLRPC.TL_destroy_sessions_res.class);
        classStore.put(TLRPC.TL_contacts_contacts.constructor, TLRPC.TL_contacts_contacts.class);
        classStore.put(TLRPC.TL_contacts_contactsNotModified.constructor, TLRPC.TL_contacts_contactsNotModified.class);
        classStore.put(TLRPC.TL_photos_photos.constructor, TLRPC.TL_photos_photos.class);
        classStore.put(TLRPC.TL_photos_photosSlice.constructor, TLRPC.TL_photos_photosSlice.class);
        classStore.put(TLRPC.TL_chatFull.constructor, TLRPC.TL_chatFull.class);
        classStore.put(TLRPC.TL_msgs_all_info.constructor, TLRPC.TL_msgs_all_info.class);
        classStore.put(TLRPC.TL_inputPeerNotifySettings.constructor, TLRPC.TL_inputPeerNotifySettings.class);
        classStore.put(TLRPC.TL_null.constructor, TLRPC.TL_null.class);
        classStore.put(TLRPC.TL_inputUserSelf.constructor, TLRPC.TL_inputUserSelf.class);
        classStore.put(TLRPC.TL_inputUserForeign.constructor, TLRPC.TL_inputUserForeign.class);
        classStore.put(TLRPC.TL_inputUserEmpty.constructor, TLRPC.TL_inputUserEmpty.class);
        classStore.put(TLRPC.TL_inputUserContact.constructor, TLRPC.TL_inputUserContact.class);
        classStore.put(TLRPC.TL_p_q_inner_data.constructor, TLRPC.TL_p_q_inner_data.class);
        classStore.put(TLRPC.TL_msgs_state_req.constructor, TLRPC.TL_msgs_state_req.class);
        classStore.put(TLRPC.TL_boolTrue.constructor, TLRPC.TL_boolTrue.class);
        classStore.put(TLRPC.TL_boolFalse.constructor, TLRPC.TL_boolFalse.class);
        classStore.put(TLRPC.TL_auth_exportedAuthorization.constructor, TLRPC.TL_auth_exportedAuthorization.class);
        classStore.put(TLRPC.TL_messages_statedMessagesLinks.constructor, TLRPC.TL_messages_statedMessagesLinks.class);
        classStore.put(TLRPC.TL_messages_statedMessages.constructor, TLRPC.TL_messages_statedMessages.class);
        classStore.put(TLRPC.TL_inputNotifyChats.constructor, TLRPC.TL_inputNotifyChats.class);
        classStore.put(TLRPC.TL_inputNotifyPeer.constructor, TLRPC.TL_inputNotifyPeer.class);
        classStore.put(TLRPC.TL_inputNotifyUsers.constructor, TLRPC.TL_inputNotifyUsers.class);
        classStore.put(TLRPC.TL_inputNotifyGeoChatPeer.constructor, TLRPC.TL_inputNotifyGeoChatPeer.class);
        classStore.put(TLRPC.TL_inputNotifyAll.constructor, TLRPC.TL_inputNotifyAll.class);
        classStore.put(TLRPC.TL_inputAudioFileLocation.constructor, TLRPC.TL_inputAudioFileLocation.class);
        classStore.put(TLRPC.TL_inputEncryptedFileLocation.constructor, TLRPC.TL_inputEncryptedFileLocation.class);
        classStore.put(TLRPC.TL_inputVideoFileLocation.constructor, TLRPC.TL_inputVideoFileLocation.class);
        classStore.put(TLRPC.TL_inputDocumentFileLocation.constructor, TLRPC.TL_inputDocumentFileLocation.class);
        classStore.put(TLRPC.TL_inputFileLocation.constructor, TLRPC.TL_inputFileLocation.class);
        classStore.put(TLRPC.TL_photos_photo.constructor, TLRPC.TL_photos_photo.class);
        classStore.put(TLRPC.TL_userContact.constructor, TLRPC.TL_userContact.class);
        classStore.put(TLRPC.TL_userRequest.constructor, TLRPC.TL_userRequest.class);
        classStore.put(TLRPC.TL_userForeign.constructor, TLRPC.TL_userForeign.class);
        classStore.put(TLRPC.TL_userDeleted.constructor, TLRPC.TL_userDeleted.class);
        classStore.put(TLRPC.TL_userSelf.constructor, TLRPC.TL_userSelf.class);
        classStore.put(TLRPC.TL_userEmpty.constructor, TLRPC.TL_userEmpty.class);
        classStore.put(TLRPC.TL_geoChatMessage.constructor, TLRPC.TL_geoChatMessage.class);
        classStore.put(TLRPC.TL_geoChatMessageService.constructor, TLRPC.TL_geoChatMessageService.class);
        classStore.put(TLRPC.TL_geoChatMessageEmpty.constructor, TLRPC.TL_geoChatMessageEmpty.class);
        classStore.put(TLRPC.TL_pong.constructor, TLRPC.TL_pong.class);
        classStore.put(TLRPC.TL_messageActionChatEditPhoto.constructor, TLRPC.TL_messageActionChatEditPhoto.class);
        classStore.put(TLRPC.TL_messageActionChatDeleteUser.constructor, TLRPC.TL_messageActionChatDeleteUser.class);
        classStore.put(TLRPC.TL_messageActionChatDeletePhoto.constructor, TLRPC.TL_messageActionChatDeletePhoto.class);
        classStore.put(TLRPC.TL_messageActionChatAddUser.constructor, TLRPC.TL_messageActionChatAddUser.class);
        classStore.put(TLRPC.TL_messageActionChatCreate.constructor, TLRPC.TL_messageActionChatCreate.class);
        classStore.put(TLRPC.TL_messageActionEmpty.constructor, TLRPC.TL_messageActionEmpty.class);
        classStore.put(TLRPC.TL_messageActionChatEditTitle.constructor, TLRPC.TL_messageActionChatEditTitle.class);
        classStore.put(TLRPC.TL_messageActionGeoChatCreate.constructor, TLRPC.TL_messageActionGeoChatCreate.class);
        classStore.put(TLRPC.TL_messageActionGeoChatCheckin.constructor, TLRPC.TL_messageActionGeoChatCheckin.class);
        classStore.put(TLRPC.TL_contacts_foreignLinkMutual.constructor, TLRPC.TL_contacts_foreignLinkMutual.class);
        classStore.put(TLRPC.TL_contacts_foreignLinkUnknown.constructor, TLRPC.TL_contacts_foreignLinkUnknown.class);
        classStore.put(TLRPC.TL_contacts_foreignLinkRequested.constructor, TLRPC.TL_contacts_foreignLinkRequested.class);
        classStore.put(TLRPC.TL_dh_gen_retry.constructor, TLRPC.TL_dh_gen_retry.class);
        classStore.put(TLRPC.TL_dh_gen_fail.constructor, TLRPC.TL_dh_gen_fail.class);
        classStore.put(TLRPC.TL_dh_gen_ok.constructor, TLRPC.TL_dh_gen_ok.class);
        classStore.put(TLRPC.TL_peerNotifyEventsEmpty.constructor, TLRPC.TL_peerNotifyEventsEmpty.class);
        classStore.put(TLRPC.TL_peerNotifyEventsAll.constructor, TLRPC.TL_peerNotifyEventsAll.class);
        classStore.put(TLRPC.TL_chatLocated.constructor, TLRPC.TL_chatLocated.class);
        classStore.put(TLRPC.TL_decryptedMessageService.constructor, TLRPC.TL_decryptedMessageService.class);
        classStore.put(TLRPC.TL_decryptedMessage.constructor, TLRPC.TL_decryptedMessage.class);
        classStore.put(TLRPC.TL_inputPeerNotifyEventsAll.constructor, TLRPC.TL_inputPeerNotifyEventsAll.class);
        classStore.put(TLRPC.TL_inputPeerNotifyEventsEmpty.constructor, TLRPC.TL_inputPeerNotifyEventsEmpty.class);
        classStore.put(TLRPC.TL_client_DH_inner_data.constructor, TLRPC.TL_client_DH_inner_data.class);
        classStore.put(TLRPC.TL_video.constructor, TLRPC.TL_video.class);
        classStore.put(TLRPC.TL_videoEmpty.constructor, TLRPC.TL_videoEmpty.class);
        classStore.put(TLRPC.TL_contactBlocked.constructor, TLRPC.TL_contactBlocked.class);
        classStore.put(TLRPC.TL_inputDocumentEmpty.constructor, TLRPC.TL_inputDocumentEmpty.class);
        classStore.put(TLRPC.TL_inputDocument.constructor, TLRPC.TL_inputDocument.class);
        classStore.put(TLRPC.TL_inputAppEvent.constructor, TLRPC.TL_inputAppEvent.class);
        classStore.put(TLRPC.TL_messages_affectedHistory.constructor, TLRPC.TL_messages_affectedHistory.class);
        classStore.put(TLRPC.TL_documentEmpty.constructor, TLRPC.TL_documentEmpty.class);
        classStore.put(TLRPC.TL_document.constructor, TLRPC.TL_document.class);
        classStore.put(TLRPC.TL_inputMediaContact.constructor, TLRPC.TL_inputMediaContact.class);
        classStore.put(TLRPC.TL_inputMediaUploadedThumbDocument.constructor, TLRPC.TL_inputMediaUploadedThumbDocument.class);
        classStore.put(TLRPC.TL_inputMediaAudio.constructor, TLRPC.TL_inputMediaAudio.class);
        classStore.put(TLRPC.TL_inputMediaDocument.constructor, TLRPC.TL_inputMediaDocument.class);
        classStore.put(TLRPC.TL_inputMediaVideo.constructor, TLRPC.TL_inputMediaVideo.class);
        classStore.put(TLRPC.TL_inputMediaGeoPoint.constructor, TLRPC.TL_inputMediaGeoPoint.class);
        classStore.put(TLRPC.TL_inputMediaEmpty.constructor, TLRPC.TL_inputMediaEmpty.class);
        classStore.put(TLRPC.TL_inputMediaUploadedThumbVideo.constructor, TLRPC.TL_inputMediaUploadedThumbVideo.class);
        classStore.put(TLRPC.TL_inputMediaUploadedPhoto.constructor, TLRPC.TL_inputMediaUploadedPhoto.class);
        classStore.put(TLRPC.TL_inputMediaUploadedAudio.constructor, TLRPC.TL_inputMediaUploadedAudio.class);
        classStore.put(TLRPC.TL_inputMediaUploadedVideo.constructor, TLRPC.TL_inputMediaUploadedVideo.class);
        classStore.put(TLRPC.TL_inputMediaUploadedDocument.constructor, TLRPC.TL_inputMediaUploadedDocument.class);
        classStore.put(TLRPC.TL_inputMediaPhoto.constructor, TLRPC.TL_inputMediaPhoto.class);
        classStore.put(TLRPC.TL_geochats_messagesSlice.constructor, TLRPC.TL_geochats_messagesSlice.class);
        classStore.put(TLRPC.TL_geochats_messages.constructor, TLRPC.TL_geochats_messages.class);
        classStore.put(TLRPC.TL_messages_sentMessage.constructor, TLRPC.TL_messages_sentMessage.class);
        classStore.put(TLRPC.TL_messages_sentMessageLink.constructor, TLRPC.TL_messages_sentMessageLink.class);
        classStore.put(TLRPC.TL_encryptedMessageService.constructor, TLRPC.TL_encryptedMessageService.class);
        classStore.put(TLRPC.TL_encryptedMessage.constructor, TLRPC.TL_encryptedMessage.class);
        classStore.put(TLRPC.TL_contactSuggested.constructor, TLRPC.TL_contactSuggested.class);
        classStore.put(TLRPC.TL_server_DH_params_fail.constructor, TLRPC.TL_server_DH_params_fail.class);
        classStore.put(TLRPC.TL_server_DH_params_ok.constructor, TLRPC.TL_server_DH_params_ok.class);
        classStore.put(TLRPC.TL_userStatusEmpty.constructor, TLRPC.TL_userStatusEmpty.class);
        classStore.put(TLRPC.TL_userStatusOnline.constructor, TLRPC.TL_userStatusOnline.class);
        classStore.put(TLRPC.TL_userStatusOffline.constructor, TLRPC.TL_userStatusOffline.class);
        classStore.put(TLRPC.TL_msg_copy.constructor, TLRPC.TL_msg_copy.class);
        classStore.put(TLRPC.TL_contacts_importedContacts.constructor, TLRPC.TL_contacts_importedContacts.class);
        classStore.put(TLRPC.TL_futureSalt.constructor, TLRPC.TL_futureSalt.class);
        classStore.put(TLRPC.TL_updateEncryptedMessagesRead.constructor, TLRPC.TL_updateEncryptedMessagesRead.class);
        classStore.put(TLRPC.TL_updateContactLink.constructor, TLRPC.TL_updateContactLink.class);
        classStore.put(TLRPC.TL_updateReadMessages.constructor, TLRPC.TL_updateReadMessages.class);
        classStore.put(TLRPC.TL_updateChatParticipantDelete.constructor, TLRPC.TL_updateChatParticipantDelete.class);
        classStore.put(TLRPC.TL_updateRestoreMessages.constructor, TLRPC.TL_updateRestoreMessages.class);
        classStore.put(TLRPC.TL_updateUserTyping.constructor, TLRPC.TL_updateUserTyping.class);
        classStore.put(TLRPC.TL_updateChatUserTyping.constructor, TLRPC.TL_updateChatUserTyping.class);
        classStore.put(TLRPC.TL_updateUserName.constructor, TLRPC.TL_updateUserName.class);
        classStore.put(TLRPC.TL_updateNewEncryptedMessage.constructor, TLRPC.TL_updateNewEncryptedMessage.class);
        classStore.put(TLRPC.TL_updateNewMessage.constructor, TLRPC.TL_updateNewMessage.class);
        classStore.put(TLRPC.TL_updateMessageID.constructor, TLRPC.TL_updateMessageID.class);
        classStore.put(TLRPC.TL_updateDeleteMessages.constructor, TLRPC.TL_updateDeleteMessages.class);
        classStore.put(TLRPC.TL_updateEncryptedChatTyping.constructor, TLRPC.TL_updateEncryptedChatTyping.class);
        classStore.put(TLRPC.TL_updateDcOptions.constructor, TLRPC.TL_updateDcOptions.class);
        classStore.put(TLRPC.TL_updateChatParticipants.constructor, TLRPC.TL_updateChatParticipants.class);
        classStore.put(TLRPC.TL_updateEncryption.constructor, TLRPC.TL_updateEncryption.class);
        classStore.put(TLRPC.TL_updateActivation.constructor, TLRPC.TL_updateActivation.class);
        classStore.put(TLRPC.TL_updateNewAuthorization.constructor, TLRPC.TL_updateNewAuthorization.class);
        classStore.put(TLRPC.TL_updateNewGeoChatMessage.constructor, TLRPC.TL_updateNewGeoChatMessage.class);
        classStore.put(TLRPC.TL_updateUserPhoto.constructor, TLRPC.TL_updateUserPhoto.class);
        classStore.put(TLRPC.TL_updateContactRegistered.constructor, TLRPC.TL_updateContactRegistered.class);
        classStore.put(TLRPC.TL_updateChatParticipantAdd.constructor, TLRPC.TL_updateChatParticipantAdd.class);
        classStore.put(TLRPC.TL_updateUserStatus.constructor, TLRPC.TL_updateUserStatus.class);
        classStore.put(TLRPC.TL_contacts_suggested.constructor, TLRPC.TL_contacts_suggested.class);
        classStore.put(TLRPC.TL_rpc_error.constructor, TLRPC.TL_rpc_error.class);
        classStore.put(TLRPC.TL_rpc_req_error.constructor, TLRPC.TL_rpc_req_error.class);
        classStore.put(TLRPC.TL_inputEncryptedFile.constructor, TLRPC.TL_inputEncryptedFile.class);
        classStore.put(TLRPC.TL_inputEncryptedFileBigUploaded.constructor, TLRPC.TL_inputEncryptedFileBigUploaded.class);
        classStore.put(TLRPC.TL_inputEncryptedFileEmpty.constructor, TLRPC.TL_inputEncryptedFileEmpty.class);
        classStore.put(TLRPC.TL_inputEncryptedFileUploaded.constructor, TLRPC.TL_inputEncryptedFileUploaded.class);
        classStore.put(TLRPC.TL_decryptedMessageActionSetMessageTTL.constructor, TLRPC.TL_decryptedMessageActionSetMessageTTL.class);
        classStore.put(TLRPC.TL_contacts_myLinkRequested.constructor, TLRPC.TL_contacts_myLinkRequested.class);
        classStore.put(TLRPC.TL_contacts_myLinkContact.constructor, TLRPC.TL_contacts_myLinkContact.class);
        classStore.put(TLRPC.TL_contacts_myLinkEmpty.constructor, TLRPC.TL_contacts_myLinkEmpty.class);
        classStore.put(TLRPC.TL_server_DH_inner_data.constructor, TLRPC.TL_server_DH_inner_data.class);
        classStore.put(TLRPC.TL_new_session_created.constructor, TLRPC.TL_new_session_created.class);
        classStore.put(TLRPC.TL_userProfilePhotoEmpty.constructor, TLRPC.TL_userProfilePhotoEmpty.class);
        classStore.put(TLRPC.TL_userProfilePhoto.constructor, TLRPC.TL_userProfilePhoto.class);
        classStore.put(TLRPC.TL_photo.constructor, TLRPC.TL_photo.class);
        classStore.put(TLRPC.TL_photoEmpty.constructor, TLRPC.TL_photoEmpty.class);
        classStore.put(TLRPC.TL_encryptedChatWaiting.constructor, TLRPC.TL_encryptedChatWaiting.class);
        classStore.put(TLRPC.TL_encryptedChatEmpty.constructor, TLRPC.TL_encryptedChatEmpty.class);
        classStore.put(TLRPC.TL_encryptedChatDiscarded.constructor, TLRPC.TL_encryptedChatDiscarded.class);
        classStore.put(TLRPC.TL_encryptedChat.constructor, TLRPC.TL_encryptedChat.class);
        classStore.put(TLRPC.TL_encryptedChatRequested.constructor, TLRPC.TL_encryptedChatRequested.class);
        classStore.put(TLRPC.TL_geochats_statedMessage.constructor, TLRPC.TL_geochats_statedMessage.class);
        classStore.put(TLRPC.TL_contact.constructor, TLRPC.TL_contact.class);
        classStore.put(TLRPC.TL_config.constructor, TLRPC.TL_config.class);
        classStore.put(TLRPC.TL_inputAudio.constructor, TLRPC.TL_inputAudio.class);
        classStore.put(TLRPC.TL_inputAudioEmpty.constructor, TLRPC.TL_inputAudioEmpty.class);
        classStore.put(TLRPC.TL_messages_chats.constructor, TLRPC.TL_messages_chats.class);
        classStore.put(TLRPC.TL_contacts_found.constructor, TLRPC.TL_contacts_found.class);
        classStore.put(TLRPC.TL_chatParticipants.constructor, TLRPC.TL_chatParticipants.class);
        classStore.put(TLRPC.TL_chatParticipantsForbidden.constructor, TLRPC.TL_chatParticipantsForbidden.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaDocument.constructor, TLRPC.TL_decryptedMessageMediaDocument.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaGeoPoint.constructor, TLRPC.TL_decryptedMessageMediaGeoPoint.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaAudio.constructor, TLRPC.TL_decryptedMessageMediaAudio.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaVideo.constructor, TLRPC.TL_decryptedMessageMediaVideo.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaContact.constructor, TLRPC.TL_decryptedMessageMediaContact.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaEmpty.constructor, TLRPC.TL_decryptedMessageMediaEmpty.class);
        classStore.put(TLRPC.TL_decryptedMessageMediaPhoto.constructor, TLRPC.TL_decryptedMessageMediaPhoto.class);
        classStore.put(TLRPC.TL_chatParticipant.constructor, TLRPC.TL_chatParticipant.class);
        classStore.put(TLRPC.TL_chatForbidden.constructor, TLRPC.TL_chatForbidden.class);
        classStore.put(TLRPC.TL_geoChat.constructor, TLRPC.TL_geoChat.class);
        classStore.put(TLRPC.TL_chatEmpty.constructor, TLRPC.TL_chatEmpty.class);
        classStore.put(TLRPC.TL_chat.constructor, TLRPC.TL_chat.class);
        classStore.put(TLRPC.TL_storage_fileUnknown.constructor, TLRPC.TL_storage_fileUnknown.class);
        classStore.put(TLRPC.TL_storage_fileWebp.constructor, TLRPC.TL_storage_fileWebp.class);
        classStore.put(TLRPC.TL_storage_filePng.constructor, TLRPC.TL_storage_filePng.class);
        classStore.put(TLRPC.TL_storage_fileGif.constructor, TLRPC.TL_storage_fileGif.class);
        classStore.put(TLRPC.TL_storage_fileMov.constructor, TLRPC.TL_storage_fileMov.class);
        classStore.put(TLRPC.TL_storage_fileMp3.constructor, TLRPC.TL_storage_fileMp3.class);
        classStore.put(TLRPC.TL_storage_fileJpeg.constructor, TLRPC.TL_storage_fileJpeg.class);
        classStore.put(TLRPC.TL_storage_filePartial.constructor, TLRPC.TL_storage_filePartial.class);
        classStore.put(TLRPC.TL_storage_fileMp4.constructor, TLRPC.TL_storage_fileMp4.class);
        classStore.put(TLRPC.TL_inputMessagesFilterVideo.constructor, TLRPC.TL_inputMessagesFilterVideo.class);
        classStore.put(TLRPC.TL_inputMessagesFilterEmpty.constructor, TLRPC.TL_inputMessagesFilterEmpty.class);
        classStore.put(TLRPC.TL_inputMessagesFilterPhotos.constructor, TLRPC.TL_inputMessagesFilterPhotos.class);
        classStore.put(TLRPC.TL_inputMessagesFilterPhotoVideo.constructor, TLRPC.TL_inputMessagesFilterPhotoVideo.class);
        classStore.put(TLRPC.TL_msgs_state_info.constructor, TLRPC.TL_msgs_state_info.class);
        classStore.put(TLRPC.TL_upload_file.constructor, TLRPC.TL_upload_file.class);
        classStore.put(TLRPC.TL_dialog.constructor, TLRPC.TL_dialog.class);
        classStore.put(TLRPC.TL_fileLocation.constructor, TLRPC.TL_fileLocation.class);
        classStore.put(TLRPC.TL_fileLocationUnavailable.constructor, TLRPC.TL_fileLocationUnavailable.class);
        classStore.put(TLRPC.TL_messages_messageEmpty.constructor, TLRPC.TL_messages_messageEmpty.class);
        classStore.put(TLRPC.TL_messages_message.constructor, TLRPC.TL_messages_message.class);
        classStore.put(TLRPC.TL_geochats_located.constructor, TLRPC.TL_geochats_located.class);
        classStore.put(TLRPC.TL_inputGeoChat.constructor, TLRPC.TL_inputGeoChat.class);
        classStore.put(TLRPC.TL_protoMessage.constructor, TLRPC.TL_protoMessage.class);
        classStore.put(TLRPC.TL_photoSize.constructor, TLRPC.TL_photoSize.class);
        classStore.put(TLRPC.TL_photoSizeEmpty.constructor, TLRPC.TL_photoSizeEmpty.class);
        classStore.put(TLRPC.TL_photoCachedSize.constructor, TLRPC.TL_photoCachedSize.class);
        classStore.put(TLRPC.TL_contactFound.constructor, TLRPC.TL_contactFound.class);
        classStore.put(TLRPC.TL_inputFileBig.constructor, TLRPC.TL_inputFileBig.class);
        classStore.put(TLRPC.TL_inputFile.constructor, TLRPC.TL_inputFile.class);
        classStore.put(TLRPC.TL_messages_statedMessageLink.constructor, TLRPC.TL_messages_statedMessageLink.class);
        classStore.put(TLRPC.TL_messages_statedMessage.constructor, TLRPC.TL_messages_statedMessage.class);
        classStore.put(TLRPC.TL_userFull.constructor, TLRPC.TL_userFull.class);
        classStore.put(TLRPC.TL_updates_state.constructor, TLRPC.TL_updates_state.class);
        classStore.put(TLRPC.TL_resPQ.constructor, TLRPC.TL_resPQ.class);
        classStore.put(TLRPC.TL_updateShortChatMessage.constructor, TLRPC.TL_updateShortChatMessage.class);
        classStore.put(TLRPC.TL_updates.constructor, TLRPC.TL_updates.class);
        classStore.put(TLRPC.TL_updateShortMessage.constructor, TLRPC.TL_updateShortMessage.class);
        classStore.put(TLRPC.TL_updateShort.constructor, TLRPC.TL_updateShort.class);
        classStore.put(TLRPC.TL_updatesCombined.constructor, TLRPC.TL_updatesCombined.class);
        classStore.put(TLRPC.TL_updatesTooLong.constructor, TLRPC.TL_updatesTooLong.class);
        classStore.put(TLRPC.TL_messages_chat.constructor, TLRPC.TL_messages_chat.class);
        classStore.put(TLRPC.TL_wallPaper.constructor, TLRPC.TL_wallPaper.class);
        classStore.put(TLRPC.TL_wallPaperSolid.constructor, TLRPC.TL_wallPaperSolid.class);
        classStore.put(TLRPC.TL_msg_new_detailed_info.constructor, TLRPC.TL_msg_new_detailed_info.class);
        classStore.put(TLRPC.TL_msg_detailed_info.constructor, TLRPC.TL_msg_detailed_info.class);
        classStore.put(TLRPC.TL_inputEncryptedChat.constructor, TLRPC.TL_inputEncryptedChat.class);
        classStore.put(TLRPC.TL_inputChatPhoto.constructor, TLRPC.TL_inputChatPhoto.class);
        classStore.put(TLRPC.TL_inputChatPhotoEmpty.constructor, TLRPC.TL_inputChatPhotoEmpty.class);
        classStore.put(TLRPC.TL_inputChatUploadedPhoto.constructor, TLRPC.TL_inputChatUploadedPhoto.class);
        classStore.put(TLRPC.TL_inputVideoEmpty.constructor, TLRPC.TL_inputVideoEmpty.class);
        classStore.put(TLRPC.TL_inputVideo.constructor, TLRPC.TL_inputVideo.class);
        classStore.put(TLRPC.TL_nearestDc.constructor, TLRPC.TL_nearestDc.class);
        classStore.put(TLRPC.TL_inputPhotoEmpty.constructor, TLRPC.TL_inputPhotoEmpty.class);
        classStore.put(TLRPC.TL_inputPhoto.constructor, TLRPC.TL_inputPhoto.class);
        classStore.put(TLRPC.TL_importedContact.constructor, TLRPC.TL_importedContact.class);
        classStore.put(TLRPC.TL_inputPeerContact.constructor, TLRPC.TL_inputPeerContact.class);
        classStore.put(TLRPC.TL_inputPeerChat.constructor, TLRPC.TL_inputPeerChat.class);
        classStore.put(TLRPC.TL_inputPeerEmpty.constructor, TLRPC.TL_inputPeerEmpty.class);
        classStore.put(TLRPC.TL_inputPeerSelf.constructor, TLRPC.TL_inputPeerSelf.class);
        classStore.put(TLRPC.TL_inputPeerForeign.constructor, TLRPC.TL_inputPeerForeign.class);
        classStore.put(TLRPC.TL_dcOption.constructor, TLRPC.TL_dcOption.class);
        classStore.put(TLRPC.TL_decryptedMessageLayer.constructor, TLRPC.TL_decryptedMessageLayer.class);
        classStore.put(TLRPC.TL_inputPhotoCropAuto.constructor, TLRPC.TL_inputPhotoCropAuto.class);
        classStore.put(TLRPC.TL_inputPhotoCrop.constructor, TLRPC.TL_inputPhotoCrop.class);
        classStore.put(TLRPC.TL_messages_dialogs.constructor, TLRPC.TL_messages_dialogs.class);
        classStore.put(TLRPC.TL_messages_dialogsSlice.constructor, TLRPC.TL_messages_dialogsSlice.class);
        classStore.put(TLRPC.TL_req_pq.constructor, TLRPC.TL_req_pq.class);
        classStore.put(TLRPC.TL_req_DH_params.constructor, TLRPC.TL_req_DH_params.class);
        classStore.put(TLRPC.TL_set_client_DH_params.constructor, TLRPC.TL_set_client_DH_params.class);
        classStore.put(TLRPC.TL_ping.constructor, TLRPC.TL_ping.class);
        classStore.put(TLRPC.TL_ping_delay_disconnect.constructor, TLRPC.TL_ping_delay_disconnect.class);
        classStore.put(TLRPC.TL_destroy_session.constructor, TLRPC.TL_destroy_session.class);
        classStore.put(TLRPC.TL_destroy_sessions.constructor, TLRPC.TL_destroy_sessions.class);
        classStore.put(TLRPC.TL_get_future_salts.constructor, TLRPC.TL_get_future_salts.class);
        classStore.put(TLRPC.TL_rpc_drop_answer.constructor, TLRPC.TL_rpc_drop_answer.class);
        classStore.put(TLRPC.TL_auth_checkPhone.constructor, TLRPC.TL_auth_checkPhone.class);
        classStore.put(TLRPC.TL_auth_sendCode.constructor, TLRPC.TL_auth_sendCode.class);
        classStore.put(TLRPC.TL_auth_sendCall.constructor, TLRPC.TL_auth_sendCall.class);
        classStore.put(TLRPC.TL_auth_signUp.constructor, TLRPC.TL_auth_signUp.class);
        classStore.put(TLRPC.TL_auth_signIn.constructor, TLRPC.TL_auth_signIn.class);
        classStore.put(TLRPC.TL_auth_logOut.constructor, TLRPC.TL_auth_logOut.class);
        classStore.put(TLRPC.TL_auth_resetAuthorizations.constructor, TLRPC.TL_auth_resetAuthorizations.class);
        classStore.put(TLRPC.TL_auth_sendInvites.constructor, TLRPC.TL_auth_sendInvites.class);
        classStore.put(TLRPC.TL_auth_exportAuthorization.constructor, TLRPC.TL_auth_exportAuthorization.class);
        classStore.put(TLRPC.TL_auth_importAuthorization.constructor, TLRPC.TL_auth_importAuthorization.class);
        classStore.put(TLRPC.TL_account_registerDevice.constructor, TLRPC.TL_account_registerDevice.class);
        classStore.put(TLRPC.TL_account_unregisterDevice.constructor, TLRPC.TL_account_unregisterDevice.class);
        classStore.put(TLRPC.TL_account_updateNotifySettings.constructor, TLRPC.TL_account_updateNotifySettings.class);
        classStore.put(TLRPC.TL_account_getNotifySettings.constructor, TLRPC.TL_account_getNotifySettings.class);
        classStore.put(TLRPC.TL_account_resetNotifySettings.constructor, TLRPC.TL_account_resetNotifySettings.class);
        classStore.put(TLRPC.TL_account_updateProfile.constructor, TLRPC.TL_account_updateProfile.class);
        classStore.put(TLRPC.TL_account_updateStatus.constructor, TLRPC.TL_account_updateStatus.class);
        classStore.put(TLRPC.TL_account_getWallPapers.constructor, TLRPC.TL_account_getWallPapers.class);
        classStore.put(TLRPC.TL_users_getUsers.constructor, TLRPC.TL_users_getUsers.class);
        classStore.put(TLRPC.TL_users_getFullUser.constructor, TLRPC.TL_users_getFullUser.class);
        classStore.put(TLRPC.TL_contacts_getStatuses.constructor, TLRPC.TL_contacts_getStatuses.class);
        classStore.put(TLRPC.TL_contacts_getContacts.constructor, TLRPC.TL_contacts_getContacts.class);
        classStore.put(TLRPC.TL_contacts_importContacts.constructor, TLRPC.TL_contacts_importContacts.class);
        classStore.put(TLRPC.TL_contacts_search.constructor, TLRPC.TL_contacts_search.class);
        classStore.put(TLRPC.TL_contacts_getSuggested.constructor, TLRPC.TL_contacts_getSuggested.class);
        classStore.put(TLRPC.TL_contacts_deleteContact.constructor, TLRPC.TL_contacts_deleteContact.class);
        classStore.put(TLRPC.TL_contacts_deleteContacts.constructor, TLRPC.TL_contacts_deleteContacts.class);
        classStore.put(TLRPC.TL_contacts_block.constructor, TLRPC.TL_contacts_block.class);
        classStore.put(TLRPC.TL_contacts_unblock.constructor, TLRPC.TL_contacts_unblock.class);
        classStore.put(TLRPC.TL_contacts_getBlocked.constructor, TLRPC.TL_contacts_getBlocked.class);
        classStore.put(TLRPC.TL_messages_getMessages.constructor, TLRPC.TL_messages_getMessages.class);
        classStore.put(TLRPC.TL_messages_getDialogs.constructor, TLRPC.TL_messages_getDialogs.class);
        classStore.put(TLRPC.TL_messages_getHistory.constructor, TLRPC.TL_messages_getHistory.class);
        classStore.put(TLRPC.TL_messages_search.constructor, TLRPC.TL_messages_search.class);
        classStore.put(TLRPC.TL_messages_readHistory.constructor, TLRPC.TL_messages_readHistory.class);
        classStore.put(TLRPC.TL_messages_deleteHistory.constructor, TLRPC.TL_messages_deleteHistory.class);
        classStore.put(TLRPC.TL_messages_deleteMessages.constructor, TLRPC.TL_messages_deleteMessages.class);
        classStore.put(TLRPC.TL_messages_restoreMessages.constructor, TLRPC.TL_messages_restoreMessages.class);
        classStore.put(TLRPC.TL_messages_receivedMessages.constructor, TLRPC.TL_messages_receivedMessages.class);
        classStore.put(TLRPC.TL_messages_setTyping.constructor, TLRPC.TL_messages_setTyping.class);
        classStore.put(TLRPC.TL_messages_sendMessage.constructor, TLRPC.TL_messages_sendMessage.class);
        classStore.put(TLRPC.TL_messages_sendMedia.constructor, TLRPC.TL_messages_sendMedia.class);
        classStore.put(TLRPC.TL_messages_forwardMessages.constructor, TLRPC.TL_messages_forwardMessages.class);
        classStore.put(TLRPC.TL_messages_getChats.constructor, TLRPC.TL_messages_getChats.class);
        classStore.put(TLRPC.TL_messages_getFullChat.constructor, TLRPC.TL_messages_getFullChat.class);
        classStore.put(TLRPC.TL_messages_editChatTitle.constructor, TLRPC.TL_messages_editChatTitle.class);
        classStore.put(TLRPC.TL_messages_editChatPhoto.constructor, TLRPC.TL_messages_editChatPhoto.class);
        classStore.put(TLRPC.TL_messages_addChatUser.constructor, TLRPC.TL_messages_addChatUser.class);
        classStore.put(TLRPC.TL_messages_deleteChatUser.constructor, TLRPC.TL_messages_deleteChatUser.class);
        classStore.put(TLRPC.TL_messages_createChat.constructor, TLRPC.TL_messages_createChat.class);
        classStore.put(TLRPC.TL_updates_getState.constructor, TLRPC.TL_updates_getState.class);
        classStore.put(TLRPC.TL_updates_getDifference.constructor, TLRPC.TL_updates_getDifference.class);
        classStore.put(TLRPC.TL_photos_updateProfilePhoto.constructor, TLRPC.TL_photos_updateProfilePhoto.class);
        classStore.put(TLRPC.TL_photos_uploadProfilePhoto.constructor, TLRPC.TL_photos_uploadProfilePhoto.class);
        classStore.put(TLRPC.TL_upload_saveFilePart.constructor, TLRPC.TL_upload_saveFilePart.class);
        classStore.put(TLRPC.TL_upload_getFile.constructor, TLRPC.TL_upload_getFile.class);
        classStore.put(TLRPC.TL_help_getConfig.constructor, TLRPC.TL_help_getConfig.class);
        classStore.put(TLRPC.TL_help_getNearestDc.constructor, TLRPC.TL_help_getNearestDc.class);
        classStore.put(TLRPC.TL_help_getAppUpdate.constructor, TLRPC.TL_help_getAppUpdate.class);
        classStore.put(TLRPC.TL_help_saveAppLog.constructor, TLRPC.TL_help_saveAppLog.class);
        classStore.put(TLRPC.TL_help_getInviteText.constructor, TLRPC.TL_help_getInviteText.class);
        classStore.put(TLRPC.TL_photos_getUserPhotos.constructor, TLRPC.TL_photos_getUserPhotos.class);
        classStore.put(TLRPC.TL_messages_forwardMessage.constructor, TLRPC.TL_messages_forwardMessage.class);
        classStore.put(TLRPC.TL_messages_sendBroadcast.constructor, TLRPC.TL_messages_sendBroadcast.class);
        classStore.put(TLRPC.TL_geochats_getLocated.constructor, TLRPC.TL_geochats_getLocated.class);
        classStore.put(TLRPC.TL_geochats_getRecents.constructor, TLRPC.TL_geochats_getRecents.class);
        classStore.put(TLRPC.TL_geochats_checkin.constructor, TLRPC.TL_geochats_checkin.class);
        classStore.put(TLRPC.TL_geochats_getFullChat.constructor, TLRPC.TL_geochats_getFullChat.class);
        classStore.put(TLRPC.TL_geochats_editChatTitle.constructor, TLRPC.TL_geochats_editChatTitle.class);
        classStore.put(TLRPC.TL_geochats_editChatPhoto.constructor, TLRPC.TL_geochats_editChatPhoto.class);
        classStore.put(TLRPC.TL_geochats_search.constructor, TLRPC.TL_geochats_search.class);
        classStore.put(TLRPC.TL_geochats_getHistory.constructor, TLRPC.TL_geochats_getHistory.class);
        classStore.put(TLRPC.TL_geochats_setTyping.constructor, TLRPC.TL_geochats_setTyping.class);
        classStore.put(TLRPC.TL_geochats_sendMessage.constructor, TLRPC.TL_geochats_sendMessage.class);
        classStore.put(TLRPC.TL_geochats_sendMedia.constructor, TLRPC.TL_geochats_sendMedia.class);
        classStore.put(TLRPC.TL_geochats_createGeoChat.constructor, TLRPC.TL_geochats_createGeoChat.class);
        classStore.put(TLRPC.TL_messages_getDhConfig.constructor, TLRPC.TL_messages_getDhConfig.class);
        classStore.put(TLRPC.TL_messages_requestEncryption.constructor, TLRPC.TL_messages_requestEncryption.class);
        classStore.put(TLRPC.TL_messages_acceptEncryption.constructor, TLRPC.TL_messages_acceptEncryption.class);
        classStore.put(TLRPC.TL_messages_discardEncryption.constructor, TLRPC.TL_messages_discardEncryption.class);
        classStore.put(TLRPC.TL_messages_setEncryptedTyping.constructor, TLRPC.TL_messages_setEncryptedTyping.class);
        classStore.put(TLRPC.TL_messages_readEncryptedHistory.constructor, TLRPC.TL_messages_readEncryptedHistory.class);
        classStore.put(TLRPC.TL_messages_sendEncrypted.constructor, TLRPC.TL_messages_sendEncrypted.class);
        classStore.put(TLRPC.TL_messages_sendEncryptedFile.constructor, TLRPC.TL_messages_sendEncryptedFile.class);
        classStore.put(TLRPC.TL_messages_sendEncryptedService.constructor, TLRPC.TL_messages_sendEncryptedService.class);
        classStore.put(TLRPC.TL_messages_receivedQueue.constructor, TLRPC.TL_messages_receivedQueue.class);
        classStore.put(TLRPC.TL_upload_saveBigFilePart.constructor, TLRPC.TL_upload_saveBigFilePart.class);
        classStore.put(TLRPC.TL_help_support.constructor, TLRPC.TL_help_support.class);
        classStore.put(TLRPC.TL_help_getSupport.constructor, TLRPC.TL_help_getSupport.class);

        classStore.put(TLRPC.TL_msg_container.constructor, TLRPC.TL_msg_container.class);
        classStore.put(TLRPC.TL_fileEncryptedLocation.constructor, TLRPC.TL_fileEncryptedLocation.class);
        classStore.put(TLRPC.TL_messageActionTTLChange.constructor, TLRPC.TL_messageActionTTLChange.class);
        classStore.put(TLRPC.TL_videoEncrypted.constructor, TLRPC.TL_videoEncrypted.class);
        classStore.put(TLRPC.TL_documentEncrypted.constructor, TLRPC.TL_documentEncrypted.class);
        classStore.put(TLRPC.TL_audioEncrypted.constructor, TLRPC.TL_audioEncrypted.class);
        classStore.put(TLRPC.TL_gzip_packed.constructor, TLRPC.TL_gzip_packed.class);
        classStore.put(TLRPC.Vector.constructor, TLRPC.Vector.class);
        classStore.put(TLRPC.TL_userProfilePhotoOld.constructor, TLRPC.TL_userProfilePhotoOld.class);
        classStore.put(TLRPC.TL_messageActionUserUpdatedPhoto.constructor, TLRPC.TL_messageActionUserUpdatedPhoto.class);
        classStore.put(TLRPC.TL_messageActionUserJoined.constructor, TLRPC.TL_messageActionUserJoined.class);
        classStore.put(TLRPC.TL_messageActionLoginUnknownLocation.constructor, TLRPC.TL_messageActionLoginUnknownLocation.class);
        classStore.put(TLRPC.TL_encryptedChat_old.constructor, TLRPC.TL_encryptedChat_old.class);
        classStore.put(TLRPC.TL_encryptedChatRequested_old.constructor, TLRPC.TL_encryptedChatRequested_old.class);
    }

    static TLClassStore store = null;

    public static TLClassStore Instance() {
        if (store == null) {
            store = new TLClassStore();
        }
        return store;
    }

    public TLObject TLdeserialize(AbsSerializedData stream, int constructor) {
        try {
            return TLdeserialize(stream, constructor, null);
        } catch (Exception e) {
            return null;
        }
    }

    public TLObject TLdeserialize(AbsSerializedData stream, int constructor, TLObject request) {
        Class objClass = classStore.get(constructor);
        if (objClass != null) {
            try {
                TLObject response = (TLObject)objClass.newInstance();
                if (response instanceof TLRPC.Vector) {
                    if (request != null) {
                        request.parseVector((TLRPC.Vector)response, stream);
                    } else {
                        int size = stream.readInt32();
                        for (int a = 0; a < size; a++) {
                            ((TLRPC.Vector)response).objects.add(stream.readInt32());
                        }
                    }
                } else {
                    response.readParams(stream);
                }
                return response;
            } catch (IllegalAccessException e) {
                FileLog.e("tmessages", "can't create class");
                return null;
            } catch (InstantiationException e2) {
                FileLog.e("tmessages", "can't create class");
                return null;
            }
        } else {
            FileLog.e("tmessages", String.format("unknown class %x", constructor));
            return null;
            //throw new RuntimeException(String.format("unknown class %x", constructor));
        }
    }
}
