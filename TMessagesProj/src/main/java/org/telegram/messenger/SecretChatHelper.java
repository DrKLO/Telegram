/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLClassStore;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class SecretChatHelper {

    public static class TL_decryptedMessageHolder extends TLObject {
        public static int constructor = 0x555555F9;

        public int date;
        public TLRPC.TL_decryptedMessageLayer layer;
        public TLRPC.EncryptedFile file;
        public boolean new_key_used;
        public int decryptedWithVersion;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            stream.readInt64(exception);
            date = stream.readInt32(exception);
            layer = TLRPC.TL_decryptedMessageLayer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (stream.readBool(exception)) {
                file = TLRPC.EncryptedFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            new_key_used = stream.readBool(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(0);
            stream.writeInt32(date);
            layer.serializeToStream(stream);
            stream.writeBool(file != null);
            if (file != null) {
                file.serializeToStream(stream);
            }
            stream.writeBool(new_key_used);
        }
    }

    public static final int CURRENT_SECRET_CHAT_LAYER = 73;

    private ArrayList<Integer> sendingNotifyLayer = new ArrayList<>();
    private HashMap<Integer, ArrayList<TL_decryptedMessageHolder>> secretHolesQueue = new HashMap<>();
    private HashMap<Integer, TLRPC.EncryptedChat> acceptingChats = new HashMap<>();
    public ArrayList<TLRPC.Update> delayedEncryptedChatUpdates = new ArrayList<>();
    private ArrayList<Long> pendingEncMessagesToDelete = new ArrayList<>();
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

    public void cleanup() {
        sendingNotifyLayer.clear();
        acceptingChats.clear();
        secretHolesQueue.clear();
        delayedEncryptedChatUpdates.clear();
        pendingEncMessagesToDelete.clear();

        startingSecretChat = false;
    }

    protected void processPendingEncMessages() {
        if (!pendingEncMessagesToDelete.isEmpty()) {
            final ArrayList<Long> pendingEncMessagesToDeleteCopy = new ArrayList<>(pendingEncMessagesToDelete);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    for (int a = 0; a < pendingEncMessagesToDeleteCopy.size(); a++) {
                        MessageObject messageObject = MessagesController.getInstance().dialogMessagesByRandomIds.get(pendingEncMessagesToDeleteCopy.get(a));
                        if (messageObject != null) {
                            messageObject.deleted = true;
                        }
                    }
                }
            });
            ArrayList<Long> arr = new ArrayList<>(pendingEncMessagesToDelete);
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
        newMsg.unread = true;
        newMsg.out = true;
        newMsg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMsg.dialog_id = ((long) encryptedChat.id) << 32;
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

        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(newMsg);
        MessagesStorage.getInstance().putMessages(arr, false, true, true, 0);

        return newMsg;
    }

    public void sendMessagesReadMessage(TLRPC.EncryptedChat encryptedChat, ArrayList<Long> random_ids, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionReadMessages();
            reqSend.action.random_ids = random_ids;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    protected void processUpdateEncryption(TLRPC.TL_updateEncryption update, ConcurrentHashMap<Integer, TLRPC.User> usersDict) {
        final TLRPC.EncryptedChat newChat = update.chat;
        long dialog_id = ((long) newChat.id) << 32;
        TLRPC.EncryptedChat existingChat = MessagesController.getInstance().getEncryptedChatDB(newChat.id, false);

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
            MessagesController.getInstance().putEncryptedChat(newChat, false);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    MessagesController.getInstance().dialogs_dict.put(dialog.id, dialog);
                    MessagesController.getInstance().dialogs.add(dialog);
                    MessagesController.getInstance().sortDialogs(null);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                }
            });
            MessagesStorage.getInstance().putEncryptedChat(newChat, user, dialog);
            SecretChatHelper.getInstance().acceptSecretChat(newChat);
        } else if (newChat instanceof TLRPC.TL_encryptedChat) {
            if (existingChat != null && existingChat instanceof TLRPC.TL_encryptedChatWaiting && (existingChat.auth_key == null || existingChat.auth_key.length == 1)) {
                newChat.a_or_b = existingChat.a_or_b;
                newChat.user_id = existingChat.user_id;
                processAcceptedSecretChat(newChat);
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
                newChat.admin_id = exist.admin_id;
                newChat.mtproto_seq = exist.mtproto_seq;
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
        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionDeleteMessages();
            reqSend.action.random_ids = random_ids;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendClearHistoryMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionFlushHistory();
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendNotifyLayerMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        if (sendingNotifyLayer.contains(encryptedChat.id)) {
            return;
        }
        sendingNotifyLayer.add(encryptedChat.id);
        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionNotifyLayer();
            reqSend.action.layer = CURRENT_SECRET_CHAT_LAYER;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendRequestKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

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

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendAcceptKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

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

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendCommitKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

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

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendAbortKeyMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage, long excange_id) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionAbortKey();
            reqSend.action.exchange_id = excange_id;

            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendNoopMessage(final TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionNoop();
            message = createServiceSecretMessage(encryptedChat, reqSend.action);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendTTLMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionSetMessageTTL();
            reqSend.action.ttl_seconds = encryptedChat.ttl;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);

            MessageObject newMsgObj = new MessageObject(message, null, false);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            MessagesController.getInstance().updateInterfaceWithMessages(message.dialog_id, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    public void sendScreenshotMessage(TLRPC.EncryptedChat encryptedChat, ArrayList<Long> random_ids, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();

        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionScreenshotMessages();
            reqSend.action.random_ids = random_ids;
            message = createServiceSecretMessage(encryptedChat, reqSend.action);

            MessageObject newMsgObj = new MessageObject(message, null, false);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            MessagesController.getInstance().updateInterfaceWithMessages(message.dialog_id, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        }
        reqSend.random_id = message.random_id;

        performSendEncryptedRequest(reqSend, message, encryptedChat, null, null, null);
    }

    private void updateMediaPaths(MessageObject newMsgObj, TLRPC.EncryptedFile file, TLRPC.DecryptedMessage decryptedMessage, String originalPath) {
        TLRPC.Message newMsg = newMsgObj.messageOwner;
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
                ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, size.location, true);
                ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);

                //MessagesStorage.getInstance().putSentFile(originalPath, newMsg.media.photo, 3);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaDocument && newMsg.media.document != null) {
                TLRPC.Document document = newMsg.media.document;
                newMsg.media.document = new TLRPC.TL_documentEncrypted();
                newMsg.media.document.id = file.id;
                newMsg.media.document.access_hash = file.access_hash;
                newMsg.media.document.date = document.date;
                newMsg.media.document.attributes = document.attributes;
                newMsg.media.document.mime_type = document.mime_type;
                newMsg.media.document.size = file.size;
                newMsg.media.document.key = decryptedMessage.media.key;
                newMsg.media.document.iv = decryptedMessage.media.iv;
                newMsg.media.document.thumb = document.thumb;
                newMsg.media.document.dc_id = file.dc_id;
                newMsg.media.document.caption = document.caption != null ? document.caption : "";

                if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
                    File cacheFile = new File(newMsg.attachPath);
                    File cacheFile2 = FileLoader.getPathToAttach(newMsg.media.document);
                    if (cacheFile.renameTo(cacheFile2)) {
                        newMsgObj.mediaExists = newMsgObj.attachPathExists;
                        newMsgObj.attachPathExists = false;
                        newMsg.attachPath = "";
                    }
                }

                ArrayList<TLRPC.Message> arr = new ArrayList<>();
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

    protected void performSendEncryptedRequest(final TLRPC.TL_messages_sendEncryptedMultiMedia req, final SendMessagesHelper.DelayedMessage message) {
        for (int a = 0; a < req.files.size(); a++) {
            performSendEncryptedRequest(req.messages.get(a), message.messages.get(a), message.encryptedChat, req.files.get(a), message.originalPaths.get(a), message.messageObjects.get(a));
        }
    }

    protected void performSendEncryptedRequest(final TLRPC.DecryptedMessage req, final TLRPC.Message newMsgObj, final TLRPC.EncryptedChat chat, final TLRPC.InputEncryptedFile encryptedFile, final String originalPath, final MessageObject newMsg) {
        if (req == null || chat.auth_key == null || chat instanceof TLRPC.TL_encryptedChatRequested || chat instanceof TLRPC.TL_encryptedChatWaiting) {
            return;
        }
        SendMessagesHelper.getInstance().putToSendingMessages(newMsgObj);
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    TLObject toEncryptObject;

                    TLRPC.TL_decryptedMessageLayer layer = new TLRPC.TL_decryptedMessageLayer();
                    int myLayer = Math.max(46, AndroidUtilities.getMyLayerVersion(chat.layer));
                    layer.layer = Math.min(myLayer, Math.max(46, AndroidUtilities.getPeerLayerVersion(chat.layer)));
                    layer.message = req;
                    layer.random_bytes = new byte[15];
                    Utilities.random.nextBytes(layer.random_bytes);
                    toEncryptObject = layer;

                    int mtprotoVersion = AndroidUtilities.getPeerLayerVersion(chat.layer) >= 73 ? 2 : 1;

                    if (chat.seq_in == 0 && chat.seq_out == 0) {
                        if (chat.admin_id == UserConfig.getClientUserId()) {
                            chat.seq_out = 1;
                            chat.seq_in = -2;
                        } else {
                            chat.seq_in = -1;
                        }
                    }

                    if (newMsgObj.seq_in == 0 && newMsgObj.seq_out == 0) {
                        layer.in_seq_no = chat.seq_in > 0 ? chat.seq_in : chat.seq_in + 2;
                        layer.out_seq_no = chat.seq_out;
                        chat.seq_out += 2;
                        if (AndroidUtilities.getPeerLayerVersion(chat.layer) >= 20) {
                            if (chat.key_create_date == 0) {
                                chat.key_create_date = ConnectionsManager.getInstance().getCurrentTime();
                            }
                            chat.key_use_count_out++;
                            if ((chat.key_use_count_out >= 100 || chat.key_create_date < ConnectionsManager.getInstance().getCurrentTime() - 60 * 60 * 24 * 7) && chat.exchange_id == 0 && chat.future_key_fingerprint == 0) {
                                requestNewSecretChatKey(chat);
                            }
                        }
                        MessagesStorage.getInstance().updateEncryptedChatSeq(chat, false);
                        if (newMsgObj != null) {
                            newMsgObj.seq_in = layer.in_seq_no;
                            newMsgObj.seq_out = layer.out_seq_no;
                            MessagesStorage.getInstance().setMessageSeq(newMsgObj.id, newMsgObj.seq_in, newMsgObj.seq_out);
                        }
                    } else {
                        layer.in_seq_no = newMsgObj.seq_in;
                        layer.out_seq_no = newMsgObj.seq_out;
                    }
                    FileLog.e(req + " send message with in_seq = " + layer.in_seq_no + " out_seq = " + layer.out_seq_no);

                    int len = toEncryptObject.getObjectSize();
                    NativeByteBuffer toEncrypt = new NativeByteBuffer(4 + len);
                    toEncrypt.writeInt32(len);
                    toEncryptObject.serializeToStream(toEncrypt);

                    len = toEncrypt.length();
                    int extraLen = len % 16 != 0 ? 16 - len % 16 : 0;
                    if (mtprotoVersion == 2) {
                        extraLen += (2 + Utilities.random.nextInt(3)) * 16;
                    }

                    NativeByteBuffer dataForEncryption = new NativeByteBuffer(len + extraLen);
                    toEncrypt.position(0);
                    dataForEncryption.writeBytes(toEncrypt);
                    if (extraLen != 0) {
                        byte[] b = new byte[extraLen];
                        Utilities.random.nextBytes(b);
                        dataForEncryption.writeBytes(b);
                    }

                    byte[] messageKey = new byte[16];
                    byte[] messageKeyFull;
                    boolean incoming = mtprotoVersion == 2 && chat.admin_id != UserConfig.getClientUserId();
                    if (mtprotoVersion == 2) {
                        messageKeyFull = Utilities.computeSHA256(chat.auth_key, 88 + (incoming ? 8 : 0), 32, dataForEncryption.buffer, 0, dataForEncryption.buffer.limit());
                        System.arraycopy(messageKeyFull, 8, messageKey, 0, 16);
                    } else {
                        messageKeyFull = Utilities.computeSHA1(toEncrypt.buffer);
                        System.arraycopy(messageKeyFull, messageKeyFull.length - 16, messageKey, 0, 16);
                    }
                    toEncrypt.reuse();

                    MessageKeyData keyData = MessageKeyData.generateMessageKeyData(chat.auth_key, messageKey, incoming, mtprotoVersion);

                    Utilities.aesIgeEncryption(dataForEncryption.buffer, keyData.aesKey, keyData.aesIv, true, false, 0, dataForEncryption.limit());

                    NativeByteBuffer data = new NativeByteBuffer(8 + messageKey.length + dataForEncryption.length());
                    dataForEncryption.position(0);
                    data.writeInt64(chat.key_fingerprint);
                    data.writeBytes(messageKey);
                    data.writeBytes(dataForEncryption);
                    dataForEncryption.reuse();
                    data.position(0);

                    TLObject reqToSend;

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
                    ConnectionsManager.getInstance().sendRequest(reqToSend, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            if (error == null) {
                                if (req.action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                                    TLRPC.EncryptedChat currentChat = MessagesController.getInstance().getEncryptedChat(chat.id);
                                    if (currentChat == null) {
                                        currentChat = chat;
                                    }

                                    if (currentChat.key_hash == null) {
                                        currentChat.key_hash = AndroidUtilities.calcAuthKeyHash(currentChat.auth_key);
                                    }

                                    if (AndroidUtilities.getPeerLayerVersion(currentChat.layer) >= 46 && currentChat.key_hash.length == 16) {
                                        try {
                                            byte[] sha256 = Utilities.computeSHA256(chat.auth_key, 0, chat.auth_key.length);
                                            byte[] key_hash = new byte[36];
                                            System.arraycopy(chat.key_hash, 0, key_hash, 0, 16);
                                            System.arraycopy(sha256, 0, key_hash, 16, 20);
                                            currentChat.key_hash = key_hash;
                                            MessagesStorage.getInstance().updateEncryptedChat(currentChat);
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                    }

                                    sendingNotifyLayer.remove((Integer) currentChat.id);
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
                                    if (newMsg != null && res.file instanceof TLRPC.TL_encryptedFile) {
                                        updateMediaPaths(newMsg, res.file, req, originalPath);
                                    }
                                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (isSecretInvisibleMessage(newMsgObj)) {
                                                res.date = 0;
                                            }
                                            MessagesStorage.getInstance().updateMessageStateAndId(newMsgObj.random_id, newMsgObj.id, newMsgObj.id, res.date, false, 0);
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, newMsgObj.id, newMsgObj.id, newMsgObj, newMsgObj.dialog_id);
                                                    SendMessagesHelper.getInstance().processSentMessage(newMsgObj.id);
                                                    if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj)) {
                                                        SendMessagesHelper.getInstance().stopVideoService(attachPath);
                                                    }
                                                    SendMessagesHelper.getInstance().removeFromSendingMessages(newMsgObj.id);
                                                }
                                            });
                                        }
                                    });
                                } else {
                                    MessagesStorage.getInstance().markMessageAsSendError(newMsgObj);
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                                            SendMessagesHelper.getInstance().processSentMessage(newMsgObj.id);
                                            if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj)) {
                                                SendMessagesHelper.getInstance().stopVideoService(newMsgObj.attachPath);
                                            }
                                            SendMessagesHelper.getInstance().removeFromSendingMessages(newMsgObj.id);
                                        }
                                    });
                                }
                            }
                        }
                    }, ConnectionsManager.RequestFlagInvokeAfter);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void applyPeerLayer(final TLRPC.EncryptedChat chat, int newPeerLayer) {
        int currentPeerLayer = AndroidUtilities.getPeerLayerVersion(chat.layer);
        if (newPeerLayer <= currentPeerLayer) {
            return;
        }
        if (chat.key_hash.length == 16 && currentPeerLayer >= 46) {
            try {
                byte[] sha256 = Utilities.computeSHA256(chat.auth_key, 0, chat.auth_key.length);
                byte[] key_hash = new byte[36];
                System.arraycopy(chat.key_hash, 0, key_hash, 0, 16);
                System.arraycopy(sha256, 0, key_hash, 16, 20);
                chat.key_hash = key_hash;
                MessagesStorage.getInstance().updateEncryptedChat(chat);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        chat.layer = AndroidUtilities.setPeerLayerVersion(chat.layer, newPeerLayer);
        MessagesStorage.getInstance().updateEncryptedChatLayer(chat);
        if (currentPeerLayer < CURRENT_SECRET_CHAT_LAYER) {
            sendNotifyLayerMessage(chat, null);
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatUpdated, chat);
            }
        });
    }

    public TLRPC.Message processDecryptedObject(final TLRPC.EncryptedChat chat, final TLRPC.EncryptedFile file, int date, TLObject object, boolean new_key_used) {
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
                TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage) object;
                TLRPC.TL_message newMessage;
                if (AndroidUtilities.getPeerLayerVersion(chat.layer) >= 17) {
                    newMessage = new TLRPC.TL_message_secret();
                    newMessage.ttl = decryptedMessage.ttl;
                    newMessage.entities = decryptedMessage.entities;
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
                newMessage.random_id = decryptedMessage.random_id;
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.unread = true;
                newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                if (decryptedMessage.via_bot_name != null && decryptedMessage.via_bot_name.length() > 0) {
                    newMessage.via_bot_name = decryptedMessage.via_bot_name;
                    newMessage.flags |= TLRPC.MESSAGE_FLAG_HAS_BOT_ID;
                }
                if (decryptedMessage.grouped_id != 0) {
                    newMessage.grouped_id = decryptedMessage.grouped_id;
                    newMessage.flags |= 131072;
                }
                newMessage.dialog_id = ((long) chat.id) << 32;
                if (decryptedMessage.reply_to_random_id != 0) {
                    newMessage.reply_to_random_id = decryptedMessage.reply_to_random_id;
                    newMessage.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                }
                if (decryptedMessage.media == null || decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaEmpty) {
                    newMessage.media = new TLRPC.TL_messageMediaEmpty();
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaWebPage) {
                    newMessage.media = new TLRPC.TL_messageMediaWebPage();
                    newMessage.media.webpage = new TLRPC.TL_webPageUrlPending();
                    newMessage.media.webpage.url = decryptedMessage.media.url;
                }  else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaContact) {
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
                    newMessage.media.flags |= 3;
                    newMessage.media.caption = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    newMessage.media.photo = new TLRPC.TL_photo();
                    newMessage.media.photo.date = newMessage.date;
                    byte[] thumb = ((TLRPC.TL_decryptedMessageMediaPhoto) decryptedMessage.media).thumb;
                    if (thumb != null && thumb.length != 0 && thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        TLRPC.TL_photoCachedSize small = new TLRPC.TL_photoCachedSize();
                        small.w = decryptedMessage.media.thumb_w;
                        small.h = decryptedMessage.media.thumb_h;
                        small.bytes = thumb;
                        small.type = "s";
                        small.location = new TLRPC.TL_fileLocationUnavailable();
                        newMessage.media.photo.sizes.add(small);
                    }
                    if (newMessage.ttl != 0) {
                        newMessage.media.ttl_seconds = newMessage.ttl;
                        newMessage.flags |= 4;
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
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.flags |= 3;
                    newMessage.media.document = new TLRPC.TL_documentEncrypted();
                    newMessage.media.document.key = decryptedMessage.media.key;
                    newMessage.media.document.iv = decryptedMessage.media.iv;
                    newMessage.media.document.dc_id = file.dc_id;
                    newMessage.media.caption = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    newMessage.media.document.date = date;
                    newMessage.media.document.size = file.size;
                    newMessage.media.document.id = file.id;
                    newMessage.media.document.access_hash = file.access_hash;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "video/mp4";
                    }
                    byte[] thumb = ((TLRPC.TL_decryptedMessageMediaVideo) decryptedMessage.media).thumb;
                    if (thumb != null && thumb.length != 0 && thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        newMessage.media.document.thumb = new TLRPC.TL_photoCachedSize();
                        newMessage.media.document.thumb.bytes = thumb;
                        newMessage.media.document.thumb.w = decryptedMessage.media.thumb_w;
                        newMessage.media.document.thumb.h = decryptedMessage.media.thumb_h;
                        newMessage.media.document.thumb.type = "s";
                        newMessage.media.document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                    } else {
                        newMessage.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                        newMessage.media.document.thumb.type = "s";
                    }

                    TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                    attributeVideo.w = decryptedMessage.media.w;
                    attributeVideo.h = decryptedMessage.media.h;
                    attributeVideo.duration = decryptedMessage.media.duration;
                    newMessage.media.document.attributes.add(attributeVideo);
                    if (newMessage.ttl != 0) {
                        newMessage.ttl = Math.max(decryptedMessage.media.duration + 2, newMessage.ttl);
                        newMessage.media.ttl_seconds = newMessage.ttl;
                        newMessage.flags |= 4;
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.flags |= 3;
                    newMessage.media.caption = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    newMessage.media.document = new TLRPC.TL_documentEncrypted();
                    newMessage.media.document.id = file.id;
                    newMessage.media.document.access_hash = file.access_hash;
                    newMessage.media.document.date = date;
                    if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument_layer8) {
                        TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                        fileName.file_name = decryptedMessage.media.file_name;
                        newMessage.media.document.attributes.add(fileName);
                    } else {
                        newMessage.media.document.attributes = decryptedMessage.media.attributes;
                    }
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    newMessage.media.document.size = decryptedMessage.media.size != 0 ? Math.min(decryptedMessage.media.size, file.size) : file.size;
                    newMessage.media.document.key = decryptedMessage.media.key;
                    newMessage.media.document.iv = decryptedMessage.media.iv;
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "";
                    }
                    byte[] thumb = ((TLRPC.TL_decryptedMessageMediaDocument) decryptedMessage.media).thumb;
                    if (thumb != null && thumb.length != 0 && thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        newMessage.media.document.thumb = new TLRPC.TL_photoCachedSize();
                        newMessage.media.document.thumb.bytes = thumb;
                        newMessage.media.document.thumb.w = decryptedMessage.media.thumb_w;
                        newMessage.media.document.thumb.h = decryptedMessage.media.thumb_h;
                        newMessage.media.document.thumb.type = "s";
                        newMessage.media.document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                    } else {
                        newMessage.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                        newMessage.media.document.thumb.type = "s";
                    }
                    newMessage.media.document.dc_id = file.dc_id;
                    if (MessageObject.isVoiceMessage(newMessage) || MessageObject.isRoundVideoMessage(newMessage)) {
                        newMessage.media_unread = true;
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaExternalDocument) {
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.flags |= 3;
                    newMessage.media.caption = "";
                    newMessage.media.document = new TLRPC.TL_document();
                    newMessage.media.document.id = decryptedMessage.media.id;
                    newMessage.media.document.access_hash = decryptedMessage.media.access_hash;
                    newMessage.media.document.date = decryptedMessage.media.date;
                    newMessage.media.document.attributes = decryptedMessage.media.attributes;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    newMessage.media.document.dc_id = decryptedMessage.media.dc_id;
                    newMessage.media.document.size = decryptedMessage.media.size;
                    newMessage.media.document.thumb = ((TLRPC.TL_decryptedMessageMediaExternalDocument) decryptedMessage.media).thumb;
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "";
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaAudio) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.flags |= 3;
                    newMessage.media.document = new TLRPC.TL_documentEncrypted();
                    newMessage.media.document.key = decryptedMessage.media.key;
                    newMessage.media.document.iv = decryptedMessage.media.iv;
                    newMessage.media.document.id = file.id;
                    newMessage.media.document.access_hash = file.access_hash;
                    newMessage.media.document.date = date;
                    newMessage.media.document.size = file.size;
                    newMessage.media.document.dc_id = file.dc_id;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    newMessage.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                    newMessage.media.document.thumb.type = "s";
                    newMessage.media.caption = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "audio/ogg";
                    }
                    TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                    attributeAudio.duration = decryptedMessage.media.duration;
                    attributeAudio.voice = true;
                    newMessage.media.document.attributes.add(attributeAudio);
                    if (newMessage.ttl != 0) {
                        newMessage.ttl = Math.max(decryptedMessage.media.duration + 1, newMessage.ttl);
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaVenue) {
                    newMessage.media = new TLRPC.TL_messageMediaVenue();
                    newMessage.media.geo = new TLRPC.TL_geoPoint();
                    newMessage.media.geo.lat = decryptedMessage.media.lat;
                    newMessage.media.geo._long = decryptedMessage.media._long;
                    newMessage.media.title = decryptedMessage.media.title;
                    newMessage.media.address = decryptedMessage.media.address;
                    newMessage.media.provider = decryptedMessage.media.provider;
                    newMessage.media.venue_id = decryptedMessage.media.venue_id;
                    newMessage.media.venue_type = "";
                } else {
                    return null;
                }
                return newMessage;
            } else if (object instanceof TLRPC.TL_decryptedMessageService) {
                final TLRPC.TL_decryptedMessageService serviceMessage = (TLRPC.TL_decryptedMessageService) object;
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
                    newMessage.unread = true;
                    newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    newMessage.date = date;
                    newMessage.from_id = from_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getClientUserId();
                    newMessage.dialog_id = ((long) chat.id) << 32;
                    return newMessage;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                    final long did = ((long) chat.id) << 32;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(did);
                            if (dialog != null) {
                                dialog.unread_count = 0;
                                MessagesController.getInstance().dialogMessage.remove(dialog.id);
                            }
                            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            NotificationsController.getInstance().processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
                                            HashMap<Long, Integer> dialogsToUpdate = new HashMap<>();
                                            dialogsToUpdate.put(did, 0);
                                            NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                                        }
                                    });
                                }
                            });
                            MessagesStorage.getInstance().deleteDialog(did, 1);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false);
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
                        int time = ConnectionsManager.getInstance().getCurrentTime();
                        MessagesStorage.getInstance().createTaskForSecretChat(chat.id, time, time, 1, serviceMessage.action.random_ids);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                    applyPeerLayer(chat, serviceMessage.action.layer);
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
                    if (chat.exchange_id != 0) {
                        if (chat.exchange_id > serviceMessage.action.exchange_id) {
                            FileLog.e("we already have request key with higher exchange_id");
                            return null;
                        } else {
                            sendAbortKeyMessage(chat, null, chat.exchange_id); //TODO don't send?
                        }
                    }

                    byte[] salt = new byte[256];
                    Utilities.random.nextBytes(salt);
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
                            sendCommitKeyMessage(chat, null);
                        } else {
                            chat.future_auth_key = new byte[256];
                            chat.future_key_fingerprint = 0;
                            chat.exchange_id = 0;
                            MessagesStorage.getInstance().updateEncryptedChat(chat);
                            sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                        }
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
                    if (serviceMessage.action.end_seq_no < chat.in_seq_no || serviceMessage.action.end_seq_no < serviceMessage.action.start_seq_no) {
                        return null;
                    }
                    if (serviceMessage.action.start_seq_no < chat.in_seq_no) {
                        serviceMessage.action.start_seq_no = chat.in_seq_no;
                    }
                    resendMessages(serviceMessage.action.start_seq_no, serviceMessage.action.end_seq_no, chat);
                } else {
                    return null;
                }
            } else {
                FileLog.e("unknown message " + object);
            }
        } else {
            FileLog.e("unknown TLObject");
        }
        return null;
    }

    private TLRPC.Message createDeleteMessage(int mid, int seq_out, int seq_in, long random_id, TLRPC.EncryptedChat encryptedChat) {
        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();
        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = new TLRPC.TL_decryptedMessageActionDeleteMessages();
        newMsg.action.encryptedAction.random_ids.add(random_id);
        newMsg.local_id = newMsg.id = mid;
        newMsg.from_id = UserConfig.getClientUserId();
        newMsg.unread = true;
        newMsg.out = true;
        newMsg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMsg.dialog_id = ((long) encryptedChat.id) << 32;
        newMsg.to_id = new TLRPC.TL_peerUser();
        newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        newMsg.seq_in = seq_in;
        newMsg.seq_out = seq_out;
        if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
            newMsg.to_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.to_id.user_id = encryptedChat.participant_id;
        }
        newMsg.date = 0;
        newMsg.random_id = random_id;
        return newMsg;
    }

    private void resendMessages(final int startSeq, final int endSeq, final TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat == null || endSeq - startSeq < 0) {
            return;
        }
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    int sSeq = startSeq;
                    if (encryptedChat.admin_id == UserConfig.getClientUserId() && sSeq % 2 == 0) {
                        sSeq++;
                    }

                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT uid FROM requested_holes WHERE uid = %d AND ((seq_out_start >= %d AND %d <= seq_out_end) OR (seq_out_start >= %d AND %d <= seq_out_end))", encryptedChat.id, sSeq, sSeq, endSeq, endSeq));
                    boolean exists = cursor.next();
                    cursor.dispose();
                    if (exists) {
                        return;
                    }

                    long dialog_id = ((long) encryptedChat.id) << 32;
                    HashMap<Integer, TLRPC.Message> messagesToResend = new HashMap<>();
                    final ArrayList<TLRPC.Message> messages = new ArrayList<>();
                    for (int a = sSeq; a < endSeq; a += 2) {
                        messagesToResend.put(a, null);
                    }
                    cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, r.random_id, s.seq_in, s.seq_out, m.ttl, s.mid FROM messages_seq as s LEFT JOIN randoms as r ON r.mid = s.mid LEFT JOIN messages as m ON m.mid = s.mid WHERE m.uid = %d AND m.out = 1 AND s.seq_out >= %d AND s.seq_out <= %d ORDER BY seq_out ASC", dialog_id, sSeq, endSeq));
                    while (cursor.next()) {
                        TLRPC.Message message;
                        long random_id = cursor.longValue(1);
                        if (random_id == 0) {
                            random_id = Utilities.random.nextLong();
                        }
                        int seq_in = cursor.intValue(2);
                        int seq_out = cursor.intValue(3);
                        int mid = cursor.intValue(5);

                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            message.random_id = random_id;
                            message.dialog_id = dialog_id;
                            message.seq_in = seq_in;
                            message.seq_out = seq_out;
                            message.ttl = cursor.intValue(4);
                        } else {
                            message = createDeleteMessage(mid, seq_out, seq_in, random_id, encryptedChat);
                        }
                        messages.add(message);
                        messagesToResend.remove(seq_out);
                    }
                    cursor.dispose();
                    if (!messagesToResend.isEmpty()) {
                        for (HashMap.Entry<Integer, TLRPC.Message> entry : messagesToResend.entrySet()) {
                            messages.add(createDeleteMessage(UserConfig.getNewMessageId(), entry.getKey(), 0, Utilities.random.nextLong(), encryptedChat));
                        }
                        UserConfig.saveConfig(false);
                    }
                    Collections.sort(messages, new Comparator<TLRPC.Message>() {
                        @Override
                        public int compare(TLRPC.Message lhs, TLRPC.Message rhs) {
                            return AndroidUtilities.compare(lhs.seq_out, rhs.seq_out);
                        }
                    });
                    ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                    encryptedChats.add(encryptedChat);

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            for (int a = 0; a < messages.size(); a++) {
                                TLRPC.Message message = messages.get(a);
                                MessageObject messageObject = new MessageObject(message, null, false);
                                messageObject.resendAsIs = true;
                                SendMessagesHelper.getInstance().retrySendMessage(messageObject, true);
                            }
                        }
                    });

                    SendMessagesHelper.getInstance().processUnsentMessages(messages, new ArrayList<TLRPC.User>(), new ArrayList<TLRPC.Chat>(), encryptedChats);
                    MessagesStorage.getInstance().getDatabase().executeFast(String.format(Locale.US, "REPLACE INTO requested_holes VALUES(%d, %d, %d)", encryptedChat.id, sSeq, endSeq)).stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void checkSecretHoles(TLRPC.EncryptedChat chat, ArrayList<TLRPC.Message> messages) {
        ArrayList<TL_decryptedMessageHolder> holes = secretHolesQueue.get(chat.id);
        if (holes == null) {
            return;
        }
        Collections.sort(holes, new Comparator<TL_decryptedMessageHolder>() {
            @Override
            public int compare(TL_decryptedMessageHolder lhs, TL_decryptedMessageHolder rhs) {
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
            TL_decryptedMessageHolder holder = holes.get(a);
            if (holder.layer.out_seq_no == chat.seq_in || chat.seq_in == holder.layer.out_seq_no - 2) {
                applyPeerLayer(chat, holder.layer.layer);
                chat.seq_in = holder.layer.out_seq_no;
                chat.in_seq_no = holder.layer.in_seq_no;
                holes.remove(a);
                a--;
                update = true;

                if (holder.decryptedWithVersion == 2) {
                    chat.mtproto_seq = Math.min(chat.mtproto_seq, chat.seq_in);
                }

                TLRPC.Message message = processDecryptedObject(chat, holder.file, holder.date, holder.layer.message, holder.new_key_used);
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
            MessagesStorage.getInstance().updateEncryptedChatSeq(chat, true);
        }
    }

    private boolean decryptWithMtProtoVersion(NativeByteBuffer is, byte[] keyToDecrypt, byte[] messageKey, int version, boolean incoming, boolean encryptOnError) {
        if (version == 1) {
            incoming = false;
        }
        MessageKeyData keyData = MessageKeyData.generateMessageKeyData(keyToDecrypt, messageKey, incoming, version);
        Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, false, false, 24, is.limit() - 24);

        int len = is.readInt32(false);
        byte[] messageKeyFull;
        if (version == 2) {
            messageKeyFull = Utilities.computeSHA256(keyToDecrypt, 88 + (incoming ? 8 : 0), 32, is.buffer, 24, is.buffer.limit());
            if (!Utilities.arraysEquals(messageKey, 0, messageKeyFull, 8)) {
                if (encryptOnError) {
                    Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, true, false, 24, is.limit() - 24);
                    is.position(24);
                }
                return false;
            }
        } else {
            int l = len + 28;
            if (l < is.buffer.limit() - 15 || l > is.buffer.limit()) {
                l = is.buffer.limit();
            }
            messageKeyFull = Utilities.computeSHA1(is.buffer, 24, l);
            if (!Utilities.arraysEquals(messageKey, 0, messageKeyFull, messageKeyFull.length - 16)) {
                if (encryptOnError) {
                    Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, true, false, 24, is.limit() - 24);
                    is.position(24);
                }
                return false;
            }
        }
        if (len <= 0 || len > is.limit() - 28) {
            return false;
        }
        int padding = is.limit() - 28 - len;
        if (version == 2 && (padding < 12 || padding > 1024) || version == 1 && padding > 15) {
            return false;
        }
        //
        return true;
    }

    protected ArrayList<TLRPC.Message> decryptMessage(TLRPC.EncryptedMessage message) {
        final TLRPC.EncryptedChat chat = MessagesController.getInstance().getEncryptedChatDB(message.chat_id, true);
        if (chat == null || chat instanceof TLRPC.TL_encryptedChatDiscarded) {
            return null;
        }

        try {
            NativeByteBuffer is = new NativeByteBuffer(message.bytes.length);
            is.writeBytes(message.bytes);
            is.position(0);
            long fingerprint = is.readInt64(false);
            byte[] keyToDecrypt = null;
            boolean new_key_used = false;
            if (chat.key_fingerprint == fingerprint) {
                keyToDecrypt = chat.auth_key;
            } else if (chat.future_key_fingerprint != 0 && chat.future_key_fingerprint == fingerprint) {
                keyToDecrypt = chat.future_auth_key;
                new_key_used = true;
            }
            int mtprotoVersion = AndroidUtilities.getPeerLayerVersion(chat.layer) >= 73 ? 2 : 1;
            int decryptedWithVersion = mtprotoVersion;

            if (keyToDecrypt != null) {
                byte[] messageKey = is.readData(16, false);

                boolean incoming = chat.admin_id == UserConfig.getClientUserId();
                boolean tryAnotherDecrypt = true;
                if (decryptedWithVersion == 2 && chat.mtproto_seq != 0) {
                    tryAnotherDecrypt = false;
                }

                if (!decryptWithMtProtoVersion(is, keyToDecrypt, messageKey, mtprotoVersion, incoming, tryAnotherDecrypt)) {
                    if (mtprotoVersion == 2) {
                        decryptedWithVersion = 1;
                        if (!tryAnotherDecrypt || !decryptWithMtProtoVersion(is, keyToDecrypt, messageKey, 1, incoming, false)) {
                            return null;
                        }
                    } else {
                        decryptedWithVersion = 2;
                        if (!decryptWithMtProtoVersion(is, keyToDecrypt, messageKey, 2, incoming, tryAnotherDecrypt)) {
                            return null;
                        }
                    }
                }

                TLObject object = TLClassStore.Instance().TLdeserialize(is, is.readInt32(false), false);

                is.reuse();
                if (!new_key_used && AndroidUtilities.getPeerLayerVersion(chat.layer) >= 20) {
                    chat.key_use_count_in++;
                }
                if (object instanceof TLRPC.TL_decryptedMessageLayer) {
                    final TLRPC.TL_decryptedMessageLayer layer = (TLRPC.TL_decryptedMessageLayer) object;
                    if (chat.seq_in == 0 && chat.seq_out == 0) {
                        if (chat.admin_id == UserConfig.getClientUserId()) {
                            chat.seq_out = 1;
                            chat.seq_in = -2;
                        } else {
                            chat.seq_in = -1;
                        }
                    }
                    if (layer.random_bytes.length < 15) {
                        FileLog.e("got random bytes less than needed");
                        return null;
                    }
                    FileLog.e("current chat in_seq = " + chat.seq_in + " out_seq = " + chat.seq_out);
                    FileLog.e("got message with in_seq = " + layer.in_seq_no + " out_seq = " + layer.out_seq_no);
                    if (layer.out_seq_no <= chat.seq_in) {
                        return null;
                    }
                    if (decryptedWithVersion == 1 && chat.mtproto_seq != 0 && layer.out_seq_no >= chat.mtproto_seq) {
                        return null;
                    }
                    if (chat.seq_in != layer.out_seq_no - 2) {
                        FileLog.e("got hole");
                        ArrayList<TL_decryptedMessageHolder> arr = secretHolesQueue.get(chat.id);
                        if (arr == null) {
                            arr = new ArrayList<>();
                            secretHolesQueue.put(chat.id, arr);
                        }
                        if (arr.size() >= 4) {
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

                        TL_decryptedMessageHolder holder = new TL_decryptedMessageHolder();
                        holder.layer = layer;
                        holder.file = message.file;
                        holder.date = message.date;
                        holder.new_key_used = new_key_used;
                        holder.decryptedWithVersion = decryptedWithVersion;
                        arr.add(holder);
                        return null;
                    }
                    if (decryptedWithVersion == 2) {
                        chat.mtproto_seq = Math.min(chat.mtproto_seq, chat.seq_in);
                    }
                    applyPeerLayer(chat, layer.layer);
                    chat.seq_in = layer.out_seq_no;
                    chat.in_seq_no = layer.in_seq_no;
                    MessagesStorage.getInstance().updateEncryptedChatSeq(chat, true);
                    object = layer.message;
                } else if (!(object instanceof TLRPC.TL_decryptedMessageService && ((TLRPC.TL_decryptedMessageService) object).action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer)) {
                    return null;
                }
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                TLRPC.Message decryptedMessage = processDecryptedObject(chat, message.file, message.date, object, new_key_used);
                if (decryptedMessage != null) {
                    messages.add(decryptedMessage);
                }
                checkSecretHoles(chat, messages);
                return messages;
            } else {
                is.reuse();
                FileLog.e(String.format("fingerprint mismatch %x", fingerprint));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return null;
    }

    public void requestNewSecretChatKey(final TLRPC.EncryptedChat encryptedChat) {
        if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 20) {
            return;
        }
        final byte[] salt = new byte[256];
        Utilities.random.nextBytes(salt);

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
            encryptedChat.seq_in = -2;
            encryptedChat.seq_out = 1;
            MessagesStorage.getInstance().updateEncryptedChat(encryptedChat);
            MessagesController.getInstance().putEncryptedChat(encryptedChat, false);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
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
            newChat.admin_id = encryptedChat.admin_id;
            newChat.mtproto_seq = encryptedChat.mtproto_seq;
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
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
                    encryptedChat.seq_in = -1;
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
                    ConnectionsManager.getInstance().sendRequest(req2, new RequestDelegate() {
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
                                MessagesController.getInstance().putEncryptedChat(newChat, false);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
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
        if (user == null || context == null) {
            return;
        }
        startingSecretChat = true;
        final AlertDialog progressDialog = new AlertDialog(context, 1);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        final int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
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
                                        FileLog.e(e);
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
                    ConnectionsManager.getInstance().sendRequest(req2, new RequestDelegate() {
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
                                                FileLog.e(e);
                                            }
                                        }
                                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) response;
                                        chat.user_id = chat.participant_id;
                                        chat.seq_in = -2;
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
                                        MessagesController.getInstance().sortDialogs(null);
                                        MessagesStorage.getInstance().putEncryptedChat(chat, user, dialog);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.encryptedChatCreated, chat);
                                        Utilities.stageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!delayedEncryptedChatUpdates.isEmpty()) {
                                                    MessagesController.getInstance().processUpdateArray(delayedEncryptedChatUpdates, null, null, false);
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
                                                FileLog.e(e);
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
                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
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
                                    FileLog.e(e);
                                }
                            }
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ConnectionsManager.getInstance().cancelRequest(reqId, true);
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        try {
            progressDialog.show();
        } catch (Exception e) {
            //don't promt
        }
    }
}
