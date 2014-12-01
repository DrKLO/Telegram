/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.telegram.messenger.BuffersStorage;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageKeyData;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLClassStore;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class SecretChatHelper {

    public static final int CURRENT_SECRET_CHAT_LAYER = 20;

    private ArrayList<Integer> sendingNotifyLayer = new ArrayList<Integer>();
    private HashMap<Integer, ArrayList<TLRPC.TL_decryptedMessageHolder>> secretHolesQueue = new HashMap<Integer, ArrayList<TLRPC.TL_decryptedMessageHolder>>();
    private HashMap<Integer, TLRPC.EncryptedChat> acceptingChats = new HashMap<Integer, TLRPC.EncryptedChat>();
    public ArrayList<TLRPC.Update> delayedEncryptedChatUpdates = new ArrayList<TLRPC.Update>();
    private ArrayList<Long> pendingEncMessagesToDelete = new ArrayList<Long>();
    private boolean startingSecretChat = false;

    private static volatile SecretChatHelper Instance = null;
    public static SecretChatHelper getInstance() {
        SecretChatHelper localInstance = Instance;
        if (localInstance == null) {
            synchronized (SecretChatHelper.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new SecretChatHelper();
                }
            }
        }
        return localInstance;
    }

    public void cleanUp() {
        sendingNotifyLayer.clear();
        acceptingChats.clear();
        secretHolesQueue.clear();
        delayedEncryptedChatUpdates.clear();
        pendingEncMessagesToDelete.clear();

        startingSecretChat = false;
    }

    protected void processPendingEncMessages() {
        if (!pendingEncMessagesToDelete.isEmpty()) {
            ArrayList<Long> arr = new ArrayList<Long>(pendingEncMessagesToDelete);
            MessagesStorage.getInstance().markMessagesAsDeletedByRandoms(arr);
            pendingEncMessagesToDelete.clear();
        }
    }

    private TLRPC.TL_messageService createServiceSecretMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.DecryptedMessageAction decryptedMessage) {
        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = decryptedMessage;
        newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
        newMsg.from_id = UserConfig.getClientUserId();
        newMsg.flags = TLRPC.MESSAGE_FLAG_UNREAD | TLRPC.MESSAGE_FLAG_OUT;
        newMsg.dialog_id = ((long)encryptedChat.id) << 32;
        newMsg.to_id = new TLRPC.TL_peerUser();
        newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
            newMsg.to_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.to_id.user_id = encryptedChat.participant_id;
        }
        if (decryptedMessage instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || decryptedMessage instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
            newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
        } else {
            newMsg.date = 0;
        }
        newMsg.random_id = SendMessagesHelper.getInstance().getNextRandomId();
        UserConfig.saveConfig(false);

        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.getInstance().putMessages(arr, false, true, true, 0);

        return newMsg;
    }

    public void sendMessagesReadMessage(TLRPC.EncryptedChat encryptedChat, ArrayList<Long> random_ids, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionReadMessages();
            reqSend.action.random_ids = random_ids;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    protected void processUpdateEncryption(TLRPC.TL_updateEncryption update, ConcurrentHashMap<Integer, TLRPC.User> usersDict) {
        final TLRPC.EncryptedChat newChat = update.chat;
        long dialog_id = ((long)newChat.id) << 32;
        TLRPC.EncryptedChat existingChat = MessagesController.getInstance().getEncryptedChatDB(newChat.id);

        if (newChat instanceof TLRPC.TL_encryptedChatRequested && existingChat == null) {
            int user_id = newChat.participant_id;
            if (user_id == UserConfig.getClientUserId()) {
                user_id = newChat.admin_id;
            }
            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            if (user == null) {
                user = usersDict.get(user_id);
            }
            newChat.user_id = user_id;
            final TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
            dialog.id = dialog_id;
            dialog.unread_count = 0;
            dialog.top_message = 0;
            dialog.last_message_date = update.date;

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    MessagesController.getInstance().dialogs_dict.put(dialog.id, dialog);
                    MessagesController.getInstance().dialogs.add(dialog);
                    MessagesController.getInstance().putEncryptedChat(newChat, false);
                    Collections.sort(MessagesController.getInstance().dialogs, new Comparator<TLRPC.TL_dialog>() {
                        @Override
                        public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                            if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                return 0;
                            } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                return 1;
                            } else {
                                return -1;
                            }
                        }
                    });
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                }
            });
            MessagesStorage.getInstance().putEncryptedChat(newChat, user, dialog);
            SecretChatHelper.getInstance().acceptSecretChat(newChat);
        } else if (newChat instanceof TLRPC.TL_encryptedChat) {
            if (existingChat != null && existingChat instanceof TLRPC.TL_encryptedChatWaiting && (existingChat.auth_key == null || existingChat.auth_key.length == 1)) {
                newChat.a_or_b = existingChat.a_or_b;
                newChat.user_id = existingChat.user_id;
                SecretChatHelper.getInstance().processAcceptedSecretChat(newChat);
            } else if (existingChat == null && startingSecretChat) {
                delayedEncryptedChatUpdates.add(update);
            }
        } else {
            final TLRPC.EncryptedChat exist = existingChat;
            if (exist != null) {
                newChat.user_id = exist.user_id;
                newChat.auth_key = exist.auth_key;
                newChat.key_create_date = exist.key_create_date;
                newChat.key_use_count_in = exist.key_use_count_in;
                newChat.key_use_count_out = exist.key_use_count_out;
                newChat.ttl = exist.ttl;
                newChat.seq_in = exist.seq_in;
                newChat.seq_out = exist.seq_out;
            }
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (exist != null) {
                        MessagesController.getInstance().putEncryptedChat(newChat, false);
                    }
                    MessagesStorage.getInstance().updateEncryptedChat(newChat);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
                }
            });
        }
    }

    public void sendMessagesDeleteMessage(TLRPC.EncryptedChat encryptedChat, ArrayList<Long> random_ids, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionDeleteMessages();
            reqSend.action.random_ids = random_ids;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendClearHistoryMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionFlushHistory();
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendNotifyLayerMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        if (sendingNotifyLayer.contains(encryptedChat.id)) {
            return;
        }
        sendingNotifyLayer.add(encryptedChat.id);
        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionNotifyLayer();
            reqSend.action.layer = CURRENT_SECRET_CHAT_LAYER;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendRequestKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionRequestKey();
            reqSend.action.exchange_id = encryptedChat.exchange_id;
            reqSend.action.g_a = encryptedChat.g_a;

            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendAcceptKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionAcceptKey();
            reqSend.action.exchange_id = encryptedChat.exchange_id;
            reqSend.action.key_fingerprint = encryptedChat.future_key_fingerprint;
            reqSend.action.g_b = encryptedChat.g_a_or_b;

            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendCommitKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionCommitKey();
            reqSend.action.exchange_id = encryptedChat.exchange_id;
            reqSend.action.key_fingerprint = encryptedChat.future_key_fingerprint;

            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendAbortKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage, long excange_id) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionAbortKey();
            reqSend.action.exchange_id = excange_id;

            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendNoopMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionNoop();
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendTTLMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionSetMessageTTL();
            reqSend.action.ttl_seconds = encryptedChat.ttl;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);

            MessageObject newMsgObj = new MessageObject(message, null);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
            objArr.add(newMsgObj);
            MessagesController.getInstance().updateInterfaceWithMessages(message.dialog_id, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    public void sendScreenshotMessage(TLRPC.EncryptedChat encryptedChat, ArrayList<Long> random_ids, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = null;
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessageService();
        } else {
            reqSend = new TLRPC.TL_decryptedMessageService_old();
            reqSend.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
            Utilities.random.nextBytes(reqSend.random_bytes);
        }

        TLRPC.Message message = null;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionScreenshotMessages();
            reqSend.action.random_ids = random_ids;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);

            MessageObject newMsgObj = new MessageObject(message, null);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
            objArr.add(newMsgObj);
            MessagesController.getInstance().updateInterfaceWithMessages(message.dialog_id, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null);
    }

    private void processSentMessage(TLRPC.Message newMsg, TLRPC.EncryptedFile file, TLRPC.DecryptedMessage decryptedMessage, String originalPath) {
        if (file != null) {
            if (newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
                TLRPC.PhotoSize size = newMsg.media.photo.sizes.get(newMsg.media.photo.sizes.size() - 1);
                String fileName = size.location.volume_id + "_" + size.location.local_id;
                size.location = new TLRPC.TL_fileEncryptedLocation();
                size.location.key = decryptedMessage.media.key;
                size.location.iv = decryptedMessage.media.iv;
                size.location.dc_id = file.dc_id;
                size.location.volume_id = file.id;
                size.location.secret = file.access_hash;
                size.location.local_id = file.key_fingerprint;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                File cacheFile2 = FileLoader.getPathToAttach(size);
                cacheFile.renameTo(cacheFile2);
                ImageLoader.getInstance().replaceImageInCache(fileName, fileName2);
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);

                MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.photo, 3);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaVideo && newMsg.media.video != null) {
                TLRPC.Video video = newMsg.media.video;
                newMsg.media.video = new TLRPC.TL_videoEncrypted();
                newMsg.media.video.duration = video.duration;
                newMsg.media.video.thumb = video.thumb;
                newMsg.media.video.dc_id = file.dc_id;
                newMsg.media.video.w = video.w;
                newMsg.media.video.h = video.h;
                newMsg.media.video.date = video.date;
                newMsg.media.video.caption = "";
                newMsg.media.video.user_id = video.user_id;
                newMsg.media.video.size = file.size;
                newMsg.media.video.id = file.id;
                newMsg.media.video.access_hash = file.access_hash;
                newMsg.media.video.key = decryptedMessage.media.key;
                newMsg.media.video.iv = decryptedMessage.media.iv;
                newMsg.media.video.mime_type = video.mime_type;

                if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                    File cacheFile = new File(newMsg.attachPath);
                    File cacheFile2 = FileLoader.getPathToAttach(newMsg.media.video);
                    if (cacheFile.renameTo(cacheFile2)) {
                        newMsg.attachPath = "";
                    }
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);

                MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.video, 5);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaDocument && newMsg.media.document != null) {
                TLRPC.Document document = newMsg.media.document;
                newMsg.media.document = new TLRPC.TL_documentEncrypted();
                newMsg.media.document.id = file.id;
                newMsg.media.document.access_hash = file.access_hash;
                newMsg.media.document.user_id = document.user_id;
                newMsg.media.document.date = document.date;
                newMsg.media.document.file_name = document.file_name;
                newMsg.media.document.mime_type = document.mime_type;
                newMsg.media.document.size = file.size;
                newMsg.media.document.key = decryptedMessage.media.key;
                newMsg.media.document.iv = decryptedMessage.media.iv;
                newMsg.media.document.thumb = document.thumb;
                newMsg.media.document.dc_id = file.dc_id;

                if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                    File cacheFile = new File(newMsg.attachPath);
                    File cacheFile2 = FileLoader.getPathToAttach(newMsg.media.document);
                    if (cacheFile.renameTo(cacheFile2)) {
                        newMsg.attachPath = "";
                    }
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);

                MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.document, 4);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaAudio && newMsg.media.audio != null) {
                TLRPC.Audio audio = newMsg.media.audio;
                newMsg.media.audio = new TLRPC.TL_audioEncrypted();
                newMsg.media.audio.id = file.id;
                newMsg.media.audio.access_hash = file.access_hash;
                newMsg.media.audio.user_id = audio.user_id;
                newMsg.media.audio.date = audio.date;
                newMsg.media.audio.duration = audio.duration;
                newMsg.media.audio.size = file.size;
                newMsg.media.audio.dc_id = file.dc_id;
                newMsg.media.audio.key = decryptedMessage.media.key;
                newMsg.media.audio.iv = decryptedMessage.media.iv;
                newMsg.media.audio.mime_type = audio.mime_type;

                String fileName = audio.dc_id + "_" + audio.id + ".ogg";
                String fileName2 = newMsg.media.audio.dc_id + "_" + newMsg.media.audio.id + ".ogg";
                if (!fileName.equals(fileName2)) {
                    File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                    File cacheFile2 = FileLoader.getPathToAttach(newMsg.media.audio);
                    if (cacheFile.renameTo(cacheFile2)) {
                        newMsg.attachPath = "";
                    }
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
            }
        }
    }

    public static boolean isSecretVisibleMessage(TLRPC.Message message) {
        return message.action instanceof TLRPC.TL_messageEncryptedAction && (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL);
    }

    public static boolean isSecretInvisibleMessage(TLRPC.Message message) {
        return message.action instanceof TLRPC.TL_messageEncryptedAction && !(message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL);
    }

    protected void performSendEncryptedRequest(final TLRPC.DecryptedMessage req, final TLRPC.Message newMsgObj, final TLRPC.EncryptedChat chat, final TLRPC.InputEncryptedFile encryptedFile, final String originalPath) {
        if (req == null || chat.auth_key == null || chat instanceof TLRPC.TL_encryptedChatRequested || chat instanceof TLRPC.TL_encryptedChatWaiting) {
            return;
        }
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                TLObject toEncryptObject = null;
                if (AndroidUtilities.getPeerLayerVersion(chat.layer) >= 17) {
                    TLRPC.TL_decryptedMessageLayer layer = new TLRPC.TL_decryptedMessageLayer();
                    layer.layer = AndroidUtilities.getPeerLayerVersion(chat.layer);
                    layer.message = req;
                    layer.random_bytes = new byte[Math.max(1, (int) Math.ceil(Utilities.random.nextDouble() * 16))];
                    Utilities.random.nextBytes(layer.random_bytes);
                    toEncryptObject = layer;

                    if (chat.seq_in == 0 && chat.seq_out == 0) {
                        if (chat.admin_id == UserConfig.getClientUserId()) {
                            chat.seq_out = 1;
                        } else {
                            chat.seq_in = 1;
                        }
                    }

                    if (newMsgObj.seq_in == 0 && newMsgObj.seq_out == 0) {
                        layer.in_seq_no = chat.seq_in;
                        layer.out_seq_no = chat.seq_out;
                        chat.seq_out += 2;
                        if (AndroidUtilities.getPeerLayerVersion(chat.layer) >= 20) {
                            if (chat.key_create_date == 0) {
                                chat.key_create_date = ConnectionsManager.getInstance().getCurrentTime();
                            }
                            chat.key_use_count_out++;
                            if ((chat.key_use_count_out >= 100 || chat.key_create_date < ConnectionsManager.getInstance().getCurrentTime() - 60 * 60 * 24 * 7) && chat.exchange_id == 0) {
                                requestNewSecretChatKey(chat);
                            }
                        }
                        MessagesStorage.getInstance().updateEncryptedChatSeq(chat);
                        if (newMsgObj != null) {
                            newMsgObj.seq_in = layer.in_seq_no;
                            newMsgObj.seq_out = layer.out_seq_no;
                            MessagesStorage.getInstance().setMessageSeq(newMsgObj.id, newMsgObj.seq_in, newMsgObj.seq_out);
                        }
                    } else {
                        layer.in_seq_no = newMsgObj.seq_in;
                        layer.out_seq_no = newMsgObj.seq_out;
                    }
                } else {
                    toEncryptObject = req;
                }

                int len = toEncryptObject.getObjectSize();
                ByteBufferDesc toEncrypt = BuffersStorage.getInstance().getFreeBuffer(4 + len);
                toEncrypt.writeInt32(len);
                toEncryptObject.serializeToStream(toEncrypt);

                byte[] messageKeyFull = Utilities.computeSHA1(toEncrypt.buffer);
                byte[] messageKey = new byte[16];
                System.arraycopy(messageKeyFull, messageKeyFull.length - 16, messageKey, 0, 16);

                MessageKeyData keyData = Utilities.generateMessageKeyData(chat.auth_key, messageKey, false);

                len = toEncrypt.length();
                int extraLen = len % 16 != 0 ? 16 - len % 16 : 0;
                ByteBufferDesc dataForEncryption = BuffersStorage.getInstance().getFreeBuffer(len + extraLen);
                toEncrypt.position(0);
                dataForEncryption.writeRaw(toEncrypt);
                if (extraLen != 0) {
                    byte[] b = new byte[extraLen];
                    Utilities.random.nextBytes(b);
                    dataForEncryption.writeRaw(b);
                }
                BuffersStorage.getInstance().reuseFreeBuffer(toEncrypt);

                Utilities.aesIgeEncryption(dataForEncryption.buffer, keyData.aesKey, keyData.aesIv, true, false, 0, dataForEncryption.limit());

                ByteBufferDesc data = BuffersStorage.getInstance().getFreeBuffer(8 + messageKey.length + dataForEncryption.length());
                dataForEncryption.position(0);
                data.writeInt64(chat.key_fingerprint);
                data.writeRaw(messageKey);
                data.writeRaw(dataForEncryption);
                BuffersStorage.getInstance().reuseFreeBuffer(dataForEncryption);
                data.position(0);

                TLObject reqToSend = null;

                if (encryptedFile == null) {
                    if (req instanceof TLRPC.TL_decryptedMessageService) {
                        TLRPC.TL_messages_sendEncryptedService req2 = new TLRPC.TL_messages_sendEncryptedService();
                        req2.data = data;
                        req2.random_id = req.random_id;
                        req2.peer = new TLRPC.TL_inputEncryptedChat();
                        req2.peer.chat_id = chat.id;
                        req2.peer.access_hash = chat.access_hash;
                        reqToSend = req2;
                    } else {
                        TLRPC.TL_messages_sendEncrypted req2 = new TLRPC.TL_messages_sendEncrypted();
                        req2.data = data;
                        req2.random_id = req.random_id;
                        req2.peer = new TLRPC.TL_inputEncryptedChat();
                        req2.peer.chat_id = chat.id;
                        req2.peer.access_hash = chat.access_hash;
                        reqToSend = req2;
                    }
                } else {
                    TLRPC.TL_messages_sendEncryptedFile req2 = new TLRPC.TL_messages_sendEncryptedFile();
                    req2.data = data;
                    req2.random_id = req.random_id;
                    req2.peer = new TLRPC.TL_inputEncryptedChat();
                    req2.peer.chat_id = chat.id;
                    req2.peer.access_hash = chat.access_hash;
                    req2.file = encryptedFile;
                    reqToSend = req2;
                }
                ConnectionsManager.getInstance().performRpc(reqToSend, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            if (req.action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                                TLRPC.EncryptedChat currentChat = MessagesController.getInstance().getEncryptedChat(chat.id);
                                sendingNotifyLayer.remove((Integer)currentChat.id);
                                currentChat.layer = AndroidUtilities.setMyLayerVersion(currentChat.layer, CURRENT_SECRET_CHAT_LAYER);
                                MessagesStorage.getInstance().updateEncryptedChatLayer(currentChat);
                            }
                        }
                        if (newMsgObj != null) {
                            if (error == null) {
                                final String attachPath = newMsgObj.attachPath;
                                final TLRPC.messages_SentEncryptedMessage res = (TLRPC.messages_SentEncryptedMessage) response;
                                if (isSecretVisibleMessage(newMsgObj)) {
                                    newMsgObj.date = res.date;
                                }
                                if (res.file instanceof TLRPC.TL_encryptedFile) {
                                    processSentMessage(newMsgObj, res.file, req, originalPath);
                                }
                                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isSecretInvisibleMessage(newMsgObj)) {
                                            res.date = 0;
                                        }
                                        MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.random_id, newMsgObj.id, newMsgObj.id, res.date, false);
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, newMsgObj.id, newMsgObj.id, newMsgObj);
                                                SendMessagesHelper.getInstance().processSentMessage(newMsgObj.id);
                                                if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                                    SendMessagesHelper.getInstance().stopVideoService(attachPath);
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                MessagesStorage.getInstance().markMessageAsSendError(newMsgObj.id);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                                        SendMessagesHelper.getInstance().processSentMessage(newMsgObj.id);
                                        if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                            SendMessagesHelper.getInstance().stopVideoService(newMsgObj.attachPath);
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }

    public TLRPC.Message processDecryptedObject(final TLRPC.EncryptedChat chat, final TLRPC.EncryptedFile file, int date, long random_id, TLObject object, boolean new_key_used) {
        if (object != null) {
            int from_id = chat.admin_id;
            if (from_id == UserConfig.getClientUserId()) {
                from_id = chat.participant_id;
            }

            if (AndroidUtilities.getPeerLayerVersion(chat.layer) >= 20 && chat.exchange_id == 0 && chat.future_key_fingerprint == 0 && chat.key_use_count_in >= 120) {
                requestNewSecretChatKey(chat);
            }

            if (chat.exchange_id == 0 && chat.future_key_fingerprint != 0 && !new_key_used) {
                chat.future_auth_key = new byte[256];
                chat.future_key_fingerprint = 0;
                MessagesStorage.getInstance().updateEncryptedChat(chat);
            } else if (chat.exchange_id != 0 && new_key_used) {
                chat.key_fingerprint = chat.future_key_fingerprint;
                chat.auth_key = chat.future_auth_key;
                chat.key_create_date = ConnectionsManager.getInstance().getCurrentTime();
                chat.future_auth_key = new byte[256];
                chat.future_key_fingerprint = 0;
                chat.key_use_count_in = 0;
                chat.key_use_count_out = 0;
                chat.exchange_id = 0;

                MessagesStorage.getInstance().updateEncryptedChat(chat);
            }

            if (object instanceof TLRPC.TL_decryptedMessage) {
                TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage)object;
                TLRPC.TL_message newMessage = null;
                if (AndroidUtilities.getPeerLayerVersion(chat.layer) >= 17) {
                    newMessage = new TLRPC.TL_message_secret();
                    newMessage.ttl = decryptedMessage.ttl;
                } else {
                    newMessage = new TLRPC.TL_message();
                    newMessage.ttl = chat.ttl;
                }
                newMessage.message = decryptedMessage.message;
                newMessage.date = date;
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.from_id = from_id;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.random_id = random_id;
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                newMessage.dialog_id = ((long)chat.id) << 32;
                if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaEmpty) {
                    newMessage.media = new TLRPC.TL_messageMediaEmpty();
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaContact) {
                    newMessage.media = new TLRPC.TL_messageMediaContact();
                    newMessage.media.last_name = decryptedMessage.media.last_name;
                    newMessage.media.first_name = decryptedMessage.media.first_name;
                    newMessage.media.phone_number = decryptedMessage.media.phone_number;
                    newMessage.media.user_id = decryptedMessage.media.user_id;
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaGeoPoint) {
                    newMessage.media = new TLRPC.TL_messageMediaGeo();
                    newMessage.media.geo = new TLRPC.TL_geoPoint();
                    newMessage.media.geo.lat = decryptedMessage.media.lat;
                    newMessage.media.geo._long = decryptedMessage.media._long;
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaPhoto) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaPhoto();
                    newMessage.media.photo = new TLRPC.TL_photo();
                    newMessage.media.photo.user_id = newMessage.from_id;
                    newMessage.media.photo.date = newMessage.date;
                    newMessage.media.photo.caption = "";
                    newMessage.media.photo.geo = new TLRPC.TL_geoPointEmpty();
                    if (decryptedMessage.media.thumb.length != 0 && decryptedMessage.media.thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        TLRPC.TL_photoCachedSize small = new TLRPC.TL_photoCachedSize();
                        small.w = decryptedMessage.media.thumb_w;
                        small.h = decryptedMessage.media.thumb_h;
                        small.bytes = decryptedMessage.media.thumb;
                        small.type = "s";
                        small.location = new TLRPC.TL_fileLocationUnavailable();
                        newMessage.media.photo.sizes.add(small);
                    }

                    TLRPC.TL_photoSize big = new TLRPC.TL_photoSize();
                    big.w = decryptedMessage.media.w;
                    big.h = decryptedMessage.media.h;
                    big.type = "x";
                    big.size = file.size;
                    big.location = new TLRPC.TL_fileEncryptedLocation();
                    big.location.key = decryptedMessage.media.key;
                    big.location.iv = decryptedMessage.media.iv;
                    big.location.dc_id = file.dc_id;
                    big.location.volume_id = file.id;
                    big.location.secret = file.access_hash;
                    big.location.local_id = file.key_fingerprint;
                    newMessage.media.photo.sizes.add(big);
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaVideo) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaVideo();
                    newMessage.media.video = new TLRPC.TL_videoEncrypted();
                    if (decryptedMessage.media.thumb.length != 0 && decryptedMessage.media.thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        newMessage.media.video.thumb = new TLRPC.TL_photoCachedSize();
                        newMessage.media.video.thumb.bytes = decryptedMessage.media.thumb;
                        newMessage.media.video.thumb.w = decryptedMessage.media.thumb_w;
                        newMessage.media.video.thumb.h = decryptedMessage.media.thumb_h;
                        newMessage.media.video.thumb.type = "s";
                        newMessage.media.video.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                    } else {
                        newMessage.media.video.thumb = new TLRPC.TL_photoSizeEmpty();
                        newMessage.media.video.thumb.type = "s";
                    }
                    newMessage.media.video.duration = decryptedMessage.media.duration;
                    newMessage.media.video.dc_id = file.dc_id;
                    newMessage.media.video.w = decryptedMessage.media.w;
                    newMessage.media.video.h = decryptedMessage.media.h;
                    newMessage.media.video.date = date;
                    newMessage.media.video.caption = "";
                    newMessage.media.video.user_id = from_id;
                    newMessage.media.video.size = file.size;
                    newMessage.media.video.id = file.id;
                    newMessage.media.video.access_hash = file.access_hash;
                    newMessage.media.video.key = decryptedMessage.media.key;
                    newMessage.media.video.iv = decryptedMessage.media.iv;
                    newMessage.media.video.mime_type = decryptedMessage.media.mime_type;
                    if (newMessage.ttl != 0) {
                        newMessage.ttl = Math.max(newMessage.media.video.duration + 1, newMessage.ttl);
                    }
                    if (newMessage.media.video.mime_type == null) {
                        newMessage.media.video.mime_type = "video/mp4";
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.document = new TLRPC.TL_documentEncrypted();
                    newMessage.media.document.id = file.id;
                    newMessage.media.document.access_hash = file.access_hash;
                    newMessage.media.document.user_id = decryptedMessage.media.user_id;
                    newMessage.media.document.date = date;
                    newMessage.media.document.file_name = decryptedMessage.media.file_name;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    newMessage.media.document.size = file.size;
                    newMessage.media.document.key = decryptedMessage.media.key;
                    newMessage.media.document.iv = decryptedMessage.media.iv;
                    if (decryptedMessage.media.thumb.length != 0 && decryptedMessage.media.thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        newMessage.media.document.thumb = new TLRPC.TL_photoCachedSize();
                        newMessage.media.document.thumb.bytes = decryptedMessage.media.thumb;
                        newMessage.media.document.thumb.w = decryptedMessage.media.thumb_w;
                        newMessage.media.document.thumb.h = decryptedMessage.media.thumb_h;
                        newMessage.media.document.thumb.type = "s";
                        newMessage.media.document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                    } else {
                        newMessage.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                        newMessage.media.document.thumb.type = "s";
                    }
                    newMessage.media.document.dc_id = file.dc_id;
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaAudio) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaAudio();
                    newMessage.media.audio = new TLRPC.TL_audioEncrypted();
                    newMessage.media.audio.id = file.id;
                    newMessage.media.audio.access_hash = file.access_hash;
                    newMessage.media.audio.user_id = from_id;
                    newMessage.media.audio.date = date;
                    newMessage.media.audio.size = file.size;
                    newMessage.media.audio.key = decryptedMessage.media.key;
                    newMessage.media.audio.iv = decryptedMessage.media.iv;
                    newMessage.media.audio.dc_id = file.dc_id;
                    newMessage.media.audio.duration = decryptedMessage.media.duration;
                    newMessage.media.audio.mime_type = decryptedMessage.media.mime_type;
                    if (newMessage.ttl != 0) {
                        newMessage.ttl = Math.max(newMessage.media.audio.duration + 1, newMessage.ttl);
                    }
                    if (newMessage.media.audio.mime_type == null) {
                        newMessage.media.audio.mime_type = "audio/ogg";
                    }
                } else {
                    return null;
                }
                return newMessage;
            } else if (object instanceof TLRPC.TL_decryptedMessageService) {
                final TLRPC.TL_decryptedMessageService serviceMessage = (TLRPC.TL_decryptedMessageService)object;
                if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL || serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        newMessage.action = new TLRPC.TL_messageEncryptedAction();
                        if (serviceMessage.action.ttl_seconds < 0 || serviceMessage.action.ttl_seconds > 60 * 60 * 24 * 365) {
                            serviceMessage.action.ttl_seconds = 60 * 60 * 24 * 365;
                        }
                        chat.ttl = serviceMessage.action.ttl_seconds;
                        newMessage.action.encryptedAction = serviceMessage.action;
                        MessagesStorage.getInstance().updateEncryptedChatTTL(chat);
                    } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                        newMessage.action = new TLRPC.TL_messageEncryptedAction();
                        newMessage.action.encryptedAction = serviceMessage.action;
                    }
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                    newMessage.date = date;
                    newMessage.from_id = from_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getClientUserId();
                    newMessage.dialog_id = ((long)chat.id) << 32;
                    return newMessage;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                    final long did = ((long)chat.id) << 32;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(did);
                            if (dialog != null) {
                                dialog.unread_count = 0;
                                MessagesController.getInstance().dialogMessage.remove(dialog.top_message);
                            }
                            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            NotificationsController.getInstance().processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
                                            HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
                                            dialogsToUpdate.put(did, 0);
                                            NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                                        }
                                    });
                                }
                            });
                            MessagesStorage.getInstance().deleteDialog(did, true);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    });
                    return null;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
                    if (!serviceMessage.action.random_ids.isEmpty()) {
                        pendingEncMessagesToDelete.addAll(serviceMessage.action.random_ids);
                    }
                    return null;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionReadMessages) {
                    if (!serviceMessage.action.random_ids.isEmpty()) {
                        MessagesStorage.getInstance().createTaskForSecretChat(chat.id, ConnectionsManager.getInstance().getCurrentTime(), ConnectionsManager.getInstance().getCurrentTime(), 1, serviceMessage.action.random_ids);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                    int currentPeerLayer = AndroidUtilities.getPeerLayerVersion(chat.layer);
                    chat.layer = 0;
                    chat.layer = AndroidUtilities.setPeerLayerVersion(chat.layer, serviceMessage.action.layer);
                    MessagesStorage.getInstance().updateEncryptedChatLayer(chat);
                    if (currentPeerLayer < CURRENT_SECRET_CHAT_LAYER) {
                        sendNotifyLayerMessage(chat, null);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
                    if (chat.exchange_id != 0) {
                        if (chat.exchange_id > serviceMessage.action.exchange_id) {
                            FileLog.e("tmessages", "we already have request key with higher exchange_id");
                            return null;
                        } else {
                            sendAbortKeyMessage(chat, null, chat.exchange_id);
                        }
                    }

                    byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte) (Utilities.random.nextDouble() * 256);
                    }

                    BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
                    BigInteger g_b = BigInteger.valueOf(MessagesStorage.secretG);
                    g_b = g_b.modPow(new BigInteger(1, salt), p);
                    BigInteger g_a = new BigInteger(1, serviceMessage.action.g_a);

                    if (!Utilities.isGoodGaAndGb(g_a, p)) {
                        sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                        return null;
                    }

                    byte[] g_b_bytes = g_b.toByteArray();
                    if (g_b_bytes.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_b_bytes, 1, correctedAuth, 0, 256);
                        g_b_bytes = correctedAuth;
                    }

                    g_a = g_a.modPow(new BigInteger(1, salt), p);

                    byte[] authKey = g_a.toByteArray();
                    if (authKey.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
                        authKey = correctedAuth;
                    } else if (authKey.length < 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
                        for (int a = 0; a < 256 - authKey.length; a++) {
                            authKey[a] = 0;
                        }
                        authKey = correctedAuth;
                    }
                    byte[] authKeyHash = Utilities.computeSHA1(authKey);
                    byte[] authKeyId = new byte[8];
                    System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);

                    chat.exchange_id = serviceMessage.action.exchange_id;
                    chat.future_auth_key = authKey;
                    chat.future_key_fingerprint = Utilities.bytesToLong(authKeyId);
                    chat.g_a_or_b = g_b_bytes;

                    MessagesStorage.getInstance().updateEncryptedChat(chat);

                    sendAcceptKeyMessage(chat, null);
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionAcceptKey) {
                    if (chat.exchange_id == serviceMessage.action.exchange_id) {

                        BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
                        BigInteger i_authKey = new BigInteger(1, serviceMessage.action.g_b);

                        if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
                            chat.future_auth_key = new byte[256];
                            chat.future_key_fingerprint = 0;
                            chat.exchange_id = 0;
                            MessagesStorage.getInstance().updateEncryptedChat(chat);

                            sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                            return null;
                        }

                        i_authKey = i_authKey.modPow(new BigInteger(1, chat.a_or_b), p);

                        byte[] authKey = i_authKey.toByteArray();
                        if (authKey.length > 256) {
                            byte[] correctedAuth = new byte[256];
                            System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
                            authKey = correctedAuth;
                        } else if (authKey.length < 256) {
                            byte[] correctedAuth = new byte[256];
                            System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
                            for (int a = 0; a < 256 - authKey.length; a++) {
                                authKey[a] = 0;
                            }
                            authKey = correctedAuth;
                        }
                        byte[] authKeyHash = Utilities.computeSHA1(authKey);
                        byte[] authKeyId = new byte[8];
                        System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
                        long fingerprint = Utilities.bytesToLong(authKeyId);
                        if (serviceMessage.action.key_fingerprint == fingerprint) {
                            chat.future_auth_key = authKey;
                            chat.future_key_fingerprint = fingerprint;

                            MessagesStorage.getInstance().updateEncryptedChat(chat);
                        } else {
                            chat.future_auth_key = new byte[256];
                            chat.future_key_fingerprint = 0;
                            chat.exchange_id = 0;
                            MessagesStorage.getInstance().updateEncryptedChat(chat);
                            sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                        }

                        sendCommitKeyMessage(chat, null);
                    } else {
                        chat.future_auth_key = new byte[256];
                        chat.future_key_fingerprint = 0;
                        chat.exchange_id = 0;
                        MessagesStorage.getInstance().updateEncryptedChat(chat);
                        sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionCommitKey) {
                    if (chat.exchange_id == serviceMessage.action.exchange_id && chat.future_key_fingerprint == serviceMessage.action.key_fingerprint) {
                        long old_fingerpring = chat.key_fingerprint;
                        byte[] old_key = chat.auth_key;
                        chat.key_fingerprint = chat.future_key_fingerprint;
                        chat.auth_key = chat.future_auth_key;
                        chat.key_create_date = ConnectionsManager.getInstance().getCurrentTime();
                        chat.future_auth_key = old_key;
                        chat.future_key_fingerprint = old_fingerpring;
                        chat.key_use_count_in = 0;
                        chat.key_use_count_out = 0;
                        chat.exchange_id = 0;

                        MessagesStorage.getInstance().updateEncryptedChat(chat);

                        sendNoopMessage(chat, null);
                    } else {
                        chat.future_auth_key = new byte[256];
                        chat.future_key_fingerprint = 0;
                        chat.exchange_id = 0;
                        MessagesStorage.getInstance().updateEncryptedChat(chat);
                        sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionAbortKey) {
                    if (chat.exchange_id == serviceMessage.action.exchange_id) {
                        chat.future_auth_key = new byte[256];
                        chat.future_key_fingerprint = 0;
                        chat.exchange_id = 0;
                        MessagesStorage.getInstance().updateEncryptedChat(chat);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionNoop) {
                    //do nothing
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionResend) {

                } else {
                    return null;
                }
            } else {
                FileLog.e("tmessages", "unknown message " + object);
            }
        } else {
            FileLog.e("tmessages", "unknown TLObject");
        }
        return null;
    }

    public void checkSecretHoles(TLRPC.EncryptedChat chat, ArrayList<TLRPC.Message> messages) {
        ArrayList<TLRPC.TL_decryptedMessageHolder> holes = secretHolesQueue.get(chat.id);
        if (holes == null) {
            return;
        }
        Collections.sort(holes, new Comparator<TLRPC.TL_decryptedMessageHolder>() {
            @Override
            public int compare(TLRPC.TL_decryptedMessageHolder lhs, TLRPC.TL_decryptedMessageHolder rhs) {
                if (lhs.layer.out_seq_no > rhs.layer.out_seq_no) {
                    return 1;
                } else if (lhs.layer.out_seq_no < rhs.layer.out_seq_no) {
                    return -1;
                }
                return 0;
            }
        });

        boolean update = false;
        for (int a = 0; a < holes.size(); a++) {
            TLRPC.TL_decryptedMessageHolder holder = holes.get(a);
            if (holder.layer.out_seq_no == chat.seq_in || chat.seq_in == holder.layer.out_seq_no - 2) {
                chat.seq_in = holder.layer.out_seq_no;
                holes.remove(a);
                a--;
                update = true;

                TLRPC.Message message = processDecryptedObject(chat, holder.file, holder.date, holder.random_id, holder.layer.message, holder.new_key_used);
                if (message != null) {
                    messages.add(message);
                }
            } else {
                break;
            }
        }
        if (holes.isEmpty()) {
            secretHolesQueue.remove(chat.id);
        }
        if (update) {
            MessagesStorage.getInstance().updateEncryptedChatSeq(chat);
        }
    }

    protected ArrayList<TLRPC.Message> decryptMessage(TLRPC.EncryptedMessage message) {
        final TLRPC.EncryptedChat chat = MessagesController.getInstance().getEncryptedChatDB(message.chat_id);
        if (chat == null || chat instanceof TLRPC.TL_encryptedChatDiscarded) {
            return null;
        }
        ByteBufferDesc is = BuffersStorage.getInstance().getFreeBuffer(message.bytes.length);
        is.writeRaw(message.bytes);
        is.position(0);
        long fingerprint = is.readInt64();
        byte[] keyToDecrypt = null;
        boolean new_key_used = false;
        if (chat.key_fingerprint == fingerprint) {
            keyToDecrypt = chat.auth_key;
        } else if (chat.future_key_fingerprint != 0 && chat.future_key_fingerprint == fingerprint) {
            keyToDecrypt = chat.future_auth_key;
            new_key_used = true;
        }

        if (keyToDecrypt != null) {
            byte[] messageKey = is.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(keyToDecrypt, messageKey, false);

            Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, false, false, 24, is.limit() - 24);

            int len = is.readInt32();
            TLObject object = TLClassStore.Instance().TLdeserialize(is, is.readInt32());
            BuffersStorage.getInstance().reuseFreeBuffer(is);
            if (!new_key_used && AndroidUtilities.getPeerLayerVersion(chat.layer) >= 20) {
                chat.key_use_count_in++;
            }
            if (object instanceof TLRPC.TL_decryptedMessageLayer) {
                final TLRPC.TL_decryptedMessageLayer layer = (TLRPC.TL_decryptedMessageLayer)object;
                if (chat.seq_in == 0 && chat.seq_out == 0) {
                    if (chat.admin_id == UserConfig.getClientUserId()) {
                        chat.seq_out = 1;
                    } else {
                        chat.seq_in = 1;
                    }
                }
                if (layer.out_seq_no < chat.seq_in) {
                    return null;
                }
                if (chat.seq_in != layer.out_seq_no && chat.seq_in != layer.out_seq_no - 2) {
                    ArrayList<TLRPC.TL_decryptedMessageHolder> arr = secretHolesQueue.get(chat.id);
                    if (arr == null) {
                        arr = new ArrayList<TLRPC.TL_decryptedMessageHolder>();
                        secretHolesQueue.put(chat.id, arr);
                    }
                    if (arr.size() >= 10) {
                        secretHolesQueue.remove(chat.id);
                        final TLRPC.TL_encryptedChatDiscarded newChat = new TLRPC.TL_encryptedChatDiscarded();
                        newChat.id = chat.id;
                        newChat.user_id = chat.user_id;
                        newChat.auth_key = chat.auth_key;
                        newChat.key_create_date = chat.key_create_date;
                        newChat.key_use_count_in = chat.key_use_count_in;
                        newChat.key_use_count_out = chat.key_use_count_out;
                        newChat.seq_in = chat.seq_in;
                        newChat.seq_out = chat.seq_out;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                MessagesController.getInstance().putEncryptedChat(newChat, false);
                                MessagesStorage.getInstance().updateEncryptedChat(newChat);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
                            }
                        });
                        declineSecretChat(chat.id);
                        return null;
                    }

                    TLRPC.TL_decryptedMessageHolder holder = new TLRPC.TL_decryptedMessageHolder();
                    holder.layer = layer;
                    holder.file = message.file;
                    holder.random_id = message.random_id;
                    holder.date = message.date;
                    holder.new_key_used = new_key_used;
                    arr.add(holder);
                    return null;
                }
                chat.seq_in = layer.out_seq_no;
                MessagesStorage.getInstance().updateEncryptedChatSeq(chat);
                object = layer.message;
            }
            ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
            TLRPC.Message decryptedMessage = processDecryptedObject(chat, message.file, message.date, message.random_id, object, new_key_used);
            if (decryptedMessage != null) {
                messages.add(decryptedMessage);
            }
            checkSecretHoles(chat, messages);
            return messages;
        } else {
            BuffersStorage.getInstance().reuseFreeBuffer(is);
            FileLog.e("tmessages", "fingerprint mismatch " + fingerprint);
        }
        return null;
    }

    public void requestNewSecretChatKey(final TLRPC.EncryptedChat encryptedChat) {
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 20) {
            return;
        }
        final byte[] salt = new byte[256];
        for (int a = 0; a < 256; a++) {
            salt[a] = (byte) (Utilities.random.nextDouble() * 256);
        }

        BigInteger i_g_a = BigInteger.valueOf(MessagesStorage.secretG);
        i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));
        byte[] g_a = i_g_a.toByteArray();
        if (g_a.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(g_a, 1, correctedAuth, 0, 256);
            g_a = correctedAuth;
        }

        encryptedChat.exchange_id = SendMessagesHelper.getInstance().getNextRandomId();
        encryptedChat.a_or_b = salt;
        encryptedChat.g_a = g_a;

        MessagesStorage.getInstance().updateEncryptedChat(encryptedChat);

        sendRequestKeyMessage(encryptedChat, null);
    }

    public void processAcceptedSecretChat(final TLRPC.EncryptedChat encryptedChat) {
        BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
        BigInteger i_authKey = new BigInteger(1, encryptedChat.g_a_or_b);

        if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
            declineSecretChat(encryptedChat.id);
            return;
        }

        i_authKey = i_authKey.modPow(new BigInteger(1, encryptedChat.a_or_b), p);

        byte[] authKey = i_authKey.toByteArray();
        if (authKey.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
            authKey = correctedAuth;
        } else if (authKey.length < 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
            for (int a = 0; a < 256 - authKey.length; a++) {
                authKey[a] = 0;
            }
            authKey = correctedAuth;
        }
        byte[] authKeyHash = Utilities.computeSHA1(authKey);
        byte[] authKeyId = new byte[8];
        System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
        long fingerprint = Utilities.bytesToLong(authKeyId);
        if (encryptedChat.key_fingerprint == fingerprint) {
            encryptedChat.auth_key = authKey;
            encryptedChat.key_create_date = ConnectionsManager.getInstance().getCurrentTime();
            encryptedChat.seq_in = 0;
            encryptedChat.seq_out = 1;
            MessagesStorage.getInstance().updateEncryptedChat(encryptedChat);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    MessagesController.getInstance().putEncryptedChat(encryptedChat, false);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatUpdated, encryptedChat);
                    sendNotifyLayerMessage(encryptedChat, null);
                }
            });
        } else {
            final TLRPC.TL_encryptedChatDiscarded newChat = new TLRPC.TL_encryptedChatDiscarded();
            newChat.id = encryptedChat.id;
            newChat.user_id = encryptedChat.user_id;
            newChat.auth_key = encryptedChat.auth_key;
            newChat.key_create_date = encryptedChat.key_create_date;
            newChat.key_use_count_in = encryptedChat.key_use_count_in;
            newChat.key_use_count_out = encryptedChat.key_use_count_out;
            newChat.seq_in = encryptedChat.seq_in;
            newChat.seq_out = encryptedChat.seq_out;
            MessagesStorage.getInstance().updateEncryptedChat(newChat);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    MessagesController.getInstance().putEncryptedChat(newChat, false);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
                }
            });
            declineSecretChat(encryptedChat.id);
        }
    }

    public void declineSecretChat(int chat_id) {
        TLRPC.TL_messages_discardEncryption req = new TLRPC.TL_messages_discardEncryption();
        req.chat_id = chat_id;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void acceptSecretChat(final TLRPC.EncryptedChat encryptedChat) {
        if (acceptingChats.get(encryptedChat.id) != null) {
            return;
        }
        acceptingChats.put(encryptedChat.id, encryptedChat);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
                    if (response instanceof TLRPC.TL_messages_dhConfig) {
                        if (!Utilities.isGoodPrime(res.p, res.g)) {
                            acceptingChats.remove(encryptedChat.id);
                            declineSecretChat(encryptedChat.id);
                            return;
                        }

                        MessagesStorage.secretPBytes = res.p;
                        MessagesStorage.secretG = res.g;
                        MessagesStorage.lastSecretVersion = res.version;
                        MessagesStorage.getInstance().saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
                    }
                    byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
                    }
                    encryptedChat.a_or_b = salt;
                    encryptedChat.seq_in = 1;
                    encryptedChat.seq_out = 0;
                    BigInteger p = new BigInteger(1, MessagesStorage.secretPBytes);
                    BigInteger g_b = BigInteger.valueOf(MessagesStorage.secretG);
                    g_b = g_b.modPow(new BigInteger(1, salt), p);
                    BigInteger g_a = new BigInteger(1, encryptedChat.g_a);

                    if (!Utilities.isGoodGaAndGb(g_a, p)) {
                        acceptingChats.remove(encryptedChat.id);
                        declineSecretChat(encryptedChat.id);
                        return;
                    }

                    byte[] g_b_bytes = g_b.toByteArray();
                    if (g_b_bytes.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_b_bytes, 1, correctedAuth, 0, 256);
                        g_b_bytes = correctedAuth;
                    }

                    g_a = g_a.modPow(new BigInteger(1, salt), p);

                    byte[] authKey = g_a.toByteArray();
                    if (authKey.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
                        authKey = correctedAuth;
                    } else if (authKey.length < 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
                        for (int a = 0; a < 256 - authKey.length; a++) {
                            authKey[a] = 0;
                        }
                        authKey = correctedAuth;
                    }
                    byte[] authKeyHash = Utilities.computeSHA1(authKey);
                    byte[] authKeyId = new byte[8];
                    System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
                    encryptedChat.auth_key = authKey;
                    encryptedChat.key_create_date = ConnectionsManager.getInstance().getCurrentTime();

                    TLRPC.TL_messages_acceptEncryption req2 = new TLRPC.TL_messages_acceptEncryption();
                    req2.g_b = g_b_bytes;
                    req2.peer = new TLRPC.TL_inputEncryptedChat();
                    req2.peer.chat_id = encryptedChat.id;
                    req2.peer.access_hash = encryptedChat.access_hash;
                    req2.key_fingerprint = Utilities.bytesToLong(authKeyId);
                    ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            acceptingChats.remove(encryptedChat.id);
                            if (error == null) {
                                final TLRPC.EncryptedChat newChat = (TLRPC.EncryptedChat) response;
                                newChat.auth_key = encryptedChat.auth_key;
                                newChat.user_id = encryptedChat.user_id;
                                newChat.seq_in = encryptedChat.seq_in;
                                newChat.seq_out = encryptedChat.seq_out;
                                newChat.key_create_date = encryptedChat.key_create_date;
                                newChat.key_use_count_in = encryptedChat.key_use_count_in;
                                newChat.key_use_count_out = encryptedChat.key_use_count_out;
                                MessagesStorage.getInstance().updateEncryptedChat(newChat);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MessagesController.getInstance().putEncryptedChat(newChat, false);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
                                        sendNotifyLayerMessage(newChat, null);
                                    }
                                });
                            }
                        }
                    });
                } else {
                    acceptingChats.remove(encryptedChat.id);
                }
            }
        });
    }

    public void startSecretChat(final Context context, final TLRPC.User user) {
        if (user == null) {
            return;
        }
        startingSecretChat = true;
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        final long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
                    if (response instanceof TLRPC.TL_messages_dhConfig) {
                        if (!Utilities.isGoodPrime(res.p, res.g)) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (!((Activity) context).isFinishing()) {
                                            progressDialog.dismiss();
                                        }
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            });
                            return;
                        }
                        MessagesStorage.secretPBytes = res.p;
                        MessagesStorage.secretG = res.g;
                        MessagesStorage.lastSecretVersion = res.version;
                        MessagesStorage.getInstance().saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
                    }
                    final byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
                    }

                    BigInteger i_g_a = BigInteger.valueOf(MessagesStorage.secretG);
                    i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));
                    byte[] g_a = i_g_a.toByteArray();
                    if (g_a.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_a, 1, correctedAuth, 0, 256);
                        g_a = correctedAuth;
                    }

                    TLRPC.TL_messages_requestEncryption req2 = new TLRPC.TL_messages_requestEncryption();
                    req2.g_a = g_a;
                    req2.user_id = MessagesController.getInputUser(user);
                    req2.random_id = Utilities.random.nextInt();
                    ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(final TLObject response, TLRPC.TL_error error) {
                            if (error == null) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        startingSecretChat = false;
                                        if (!((Activity) context).isFinishing()) {
                                            try {
                                                progressDialog.dismiss();
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
                                            }
                                        }
                                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) response;
                                        chat.user_id = chat.participant_id;
                                        chat.seq_in = 0;
                                        chat.seq_out = 1;
                                        chat.a_or_b = salt;
                                        MessagesController.getInstance().putEncryptedChat(chat, false);
                                        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                                        dialog.id = ((long) chat.id) << 32;
                                        dialog.unread_count = 0;
                                        dialog.top_message = 0;
                                        dialog.last_message_date = ConnectionsManager.getInstance().getCurrentTime();
                                        MessagesController.getInstance().dialogs_dict.put(dialog.id, dialog);
                                        MessagesController.getInstance().dialogs.add(dialog);
                                        Collections.sort(MessagesController.getInstance().dialogs, new Comparator<TLRPC.TL_dialog>() {
                                            @Override
                                            public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                                    return 0;
                                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                                    return 1;
                                                } else {
                                                    return -1;
                                                }
                                            }
                                        });
                                        MessagesStorage.getInstance().putEncryptedChat(chat, user, dialog);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatCreated, chat);
                                        Utilities.stageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!delayedEncryptedChatUpdates.isEmpty()) {
                                                    MessagesController.getInstance().processUpdateArray(delayedEncryptedChatUpdates, null, null);
                                                    delayedEncryptedChatUpdates.clear();
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                delayedEncryptedChatUpdates.clear();
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!((Activity) context).isFinishing()) {
                                            startingSecretChat = false;
                                            try {
                                                progressDialog.dismiss();
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
                                            }
                                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            builder.setMessage(LocaleController.getString("CreateEncryptedChatError", R.string.CreateEncryptedChatError));
                                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                            builder.show().setCanceledOnTouchOutside(true);
                                        }
                                    }
                                });
                            }
                        }
                    }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                } else {
                    delayedEncryptedChatUpdates.clear();
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            startingSecretChat = false;
                            if (!((Activity) context).isFinishing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    });
                }
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
        progressDialog.show();
    }
}
