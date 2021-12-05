/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLClassStore;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class SecretChatHelper extends BaseController {

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

    public static int CURRENT_SECRET_CHAT_LAYER = 101;

    private ArrayList<Integer> sendingNotifyLayer = new ArrayList<>();
    private SparseArray<ArrayList<TL_decryptedMessageHolder>> secretHolesQueue = new SparseArray<>();
    private SparseArray<ArrayList<TLRPC.Update>> pendingSecretMessages = new SparseArray<>();
    private SparseArray<SparseIntArray> requestedHoles = new SparseArray<>();
    private SparseArray<TLRPC.EncryptedChat> acceptingChats = new SparseArray<>();
    public ArrayList<TLRPC.Update> delayedEncryptedChatUpdates = new ArrayList<>();
    private ArrayList<Long> pendingEncMessagesToDelete = new ArrayList<>();
    private boolean startingSecretChat = false;

    private static volatile SecretChatHelper[] Instance = new SecretChatHelper[UserConfig.MAX_ACCOUNT_COUNT];

    public static SecretChatHelper getInstance(int num) {
        SecretChatHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (SecretChatHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new SecretChatHelper(num);
                }
            }
        }
        return localInstance;
    }

    public SecretChatHelper(int instance) {
        super(instance);
    }

    public void cleanup() {
        sendingNotifyLayer.clear();
        acceptingChats.clear();
        secretHolesQueue.clear();
        pendingSecretMessages.clear();
        requestedHoles.clear();
        delayedEncryptedChatUpdates.clear();
        pendingEncMessagesToDelete.clear();

        startingSecretChat = false;
    }

    protected void processPendingEncMessages() {
        if (!pendingEncMessagesToDelete.isEmpty()) {
            ArrayList<Long> pendingEncMessagesToDeleteCopy = new ArrayList<>(pendingEncMessagesToDelete);
            AndroidUtilities.runOnUIThread(() -> {
                for (int a = 0; a < pendingEncMessagesToDeleteCopy.size(); a++) {
                    MessageObject messageObject = getMessagesController().dialogMessagesByRandomIds.get(pendingEncMessagesToDeleteCopy.get(a));
                    if (messageObject != null) {
                        messageObject.deleted = true;
                    }
                }
            });
            ArrayList<Long> arr = new ArrayList<>(pendingEncMessagesToDelete);
            getMessagesStorage().markMessagesAsDeletedByRandoms(arr);
            pendingEncMessagesToDelete.clear();
        }
    }

    private TLRPC.TL_messageService createServiceSecretMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.DecryptedMessageAction decryptedMessage) {
        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = decryptedMessage;
        newMsg.local_id = newMsg.id = getUserConfig().getNewMessageId();
        newMsg.from_id = new TLRPC.TL_peerUser();
        newMsg.from_id.user_id = getUserConfig().getClientUserId();
        newMsg.unread = true;
        newMsg.out = true;
        newMsg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMsg.dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
        newMsg.peer_id = new TLRPC.TL_peerUser();
        newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        if (encryptedChat.participant_id == getUserConfig().getClientUserId()) {
            newMsg.peer_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.peer_id.user_id = encryptedChat.participant_id;
        }
        if (decryptedMessage instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || decryptedMessage instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
            newMsg.date = getConnectionsManager().getCurrentTime();
        } else {
            newMsg.date = 0;
        }
        newMsg.random_id = getSendMessagesHelper().getNextRandomId();
        getUserConfig().saveConfig(false);

        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(newMsg);
        getMessagesStorage().putMessages(arr, false, true, true, 0, false);

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

    protected void processUpdateEncryption(TLRPC.TL_updateEncryption update, ConcurrentHashMap<Long, TLRPC.User> usersDict) {
        TLRPC.EncryptedChat newChat = update.chat;
        long dialog_id = DialogObject.makeEncryptedDialogId(newChat.id);
        TLRPC.EncryptedChat existingChat = getMessagesController().getEncryptedChatDB(newChat.id, false);

        if (newChat instanceof TLRPC.TL_encryptedChatRequested && existingChat == null) {
            long userId = newChat.participant_id;
            if (userId == getUserConfig().getClientUserId()) {
                userId = newChat.admin_id;
            }
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) {
                user = usersDict.get(userId);
            }
            newChat.user_id = userId;
            TLRPC.Dialog dialog = new TLRPC.TL_dialog();
            dialog.id = dialog_id;
            dialog.folder_id = newChat.folder_id;
            dialog.unread_count = 0;
            dialog.top_message = 0;
            dialog.last_message_date = update.date;
            getMessagesController().putEncryptedChat(newChat, false);
            AndroidUtilities.runOnUIThread(() -> {
                if (dialog.folder_id == 1) {
                    SharedPreferences.Editor editor = MessagesController.getNotificationsSettings(currentAccount).edit();
                    editor.putBoolean("dialog_bar_archived" + dialog_id, true);
                    editor.commit();
                }
                getMessagesController().dialogs_dict.put(dialog.id, dialog);
                getMessagesController().allDialogs.add(dialog);
                getMessagesController().sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            });
            getMessagesStorage().putEncryptedChat(newChat, user, dialog);
            acceptSecretChat(newChat);
        } else if (newChat instanceof TLRPC.TL_encryptedChat) {
            if (existingChat instanceof TLRPC.TL_encryptedChatWaiting && (existingChat.auth_key == null || existingChat.auth_key.length == 1)) {
                newChat.a_or_b = existingChat.a_or_b;
                newChat.user_id = existingChat.user_id;
                processAcceptedSecretChat(newChat);
            } else if (existingChat == null && startingSecretChat) {
                delayedEncryptedChatUpdates.add(update);
            }
        } else {
            TLRPC.EncryptedChat exist = existingChat;
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
            AndroidUtilities.runOnUIThread(() -> {
                if (exist != null) {
                    getMessagesController().putEncryptedChat(newChat, false);
                }
                getMessagesStorage().updateEncryptedChat(newChat);
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
            });
        }
        if (newChat instanceof TLRPC.TL_encryptedChatDiscarded && newChat.history_deleted) {
            AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteDialog(dialog_id, 0));
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

    public void sendNotifyLayerMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
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

    public void sendRequestKeyMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
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

    public void sendAcceptKeyMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
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

    public void sendCommitKeyMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
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

    public void sendAbortKeyMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage, long excange_id) {
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

    public void sendNoopMessage(TLRPC.EncryptedChat encryptedChat, TLRPC.Message resendMessage) {
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

    public void sendResendMessage(TLRPC.EncryptedChat encryptedChat, int start, int end, TLRPC.Message resendMessage) {
        if (!(encryptedChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        SparseIntArray array = requestedHoles.get(encryptedChat.id);
        if (array != null && array.indexOfKey(start) >= 0) {
            return;
        }
        if (array == null) {
            array = new SparseIntArray();
            requestedHoles.put(encryptedChat.id, array);
        }
        array.put(start, end);

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        TLRPC.Message message;

        if (resendMessage != null) {
            message = resendMessage;
            reqSend.action = message.action.encryptedAction;
        } else {
            reqSend.action = new TLRPC.TL_decryptedMessageActionResend();
            reqSend.action.start_seq_no = start;
            reqSend.action.end_seq_no = end;
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

            MessageObject newMsgObj = new MessageObject(currentAccount, message, false, false);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsgObj.wasJustSent = true;
            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            getMessagesController().updateInterfaceWithMessages(message.dialog_id, objArr, false);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
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

            MessageObject newMsgObj = new MessageObject(currentAccount, message, false, false);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsgObj.wasJustSent = true;
            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            getMessagesController().updateInterfaceWithMessages(message.dialog_id, objArr, false);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
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
                File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName + ".jpg");
                File cacheFile2 = FileLoader.getPathToAttach(size);
                cacheFile.renameTo(cacheFile2);
                ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, ImageLocation.getForPhoto(size, newMsg.media.photo), true);
                ArrayList<TLRPC.Message> arr = new ArrayList<>();
                arr.add(newMsg);
                getMessagesStorage().putMessages(arr, false, true, false, 0, false);

                //getMessagesStorage().putSentFile(originalPath, newMsg.media.photo, 3);
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
                newMsg.media.document.thumbs = document.thumbs;
                newMsg.media.document.dc_id = file.dc_id;
                if (newMsg.media.document.thumbs.isEmpty()) {
                    TLRPC.PhotoSize thumb = new TLRPC.TL_photoSizeEmpty();
                    thumb.type = "s";
                    newMsg.media.document.thumbs.add(thumb);
                }

                if (newMsg.attachPath != null && newMsg.attachPath.startsWith(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath())) {
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
                getMessagesStorage().putMessages(arr, false, true, false, 0, false);
            }
        }
    }

    public static boolean isSecretVisibleMessage(TLRPC.Message message) {
        return message.action instanceof TLRPC.TL_messageEncryptedAction && (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL);
    }

    public static boolean isSecretInvisibleMessage(TLRPC.Message message) {
        return message.action instanceof TLRPC.TL_messageEncryptedAction && !(message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL);
    }

    protected void performSendEncryptedRequest(TLRPC.TL_messages_sendEncryptedMultiMedia req, SendMessagesHelper.DelayedMessage message) {
        for (int a = 0; a < req.files.size(); a++) {
            performSendEncryptedRequest(req.messages.get(a), message.messages.get(a), message.encryptedChat, req.files.get(a), message.originalPaths.get(a), message.messageObjects.get(a));
        }
    }

    protected void performSendEncryptedRequest(TLRPC.DecryptedMessage req, TLRPC.Message newMsgObj, TLRPC.EncryptedChat chat, TLRPC.InputEncryptedFile encryptedFile, String originalPath, MessageObject newMsg) {
        if (req == null || chat.auth_key == null || chat instanceof TLRPC.TL_encryptedChatRequested || chat instanceof TLRPC.TL_encryptedChatWaiting) {
            return;
        }
        getSendMessagesHelper().putToSendingMessages(newMsgObj, false);
        Utilities.stageQueue.postRunnable(() -> {
            try {
                TLObject toEncryptObject;

                TLRPC.TL_decryptedMessageLayer layer = new TLRPC.TL_decryptedMessageLayer();
                int myLayer = Math.max(46, AndroidUtilities.getMyLayerVersion(chat.layer));
                layer.layer = Math.min(myLayer, Math.max(46, AndroidUtilities.getPeerLayerVersion(chat.layer)));
                layer.message = req;
                layer.random_bytes = new byte[15];
                Utilities.random.nextBytes(layer.random_bytes);
                toEncryptObject = layer;

                if (chat.seq_in == 0 && chat.seq_out == 0) {
                    if (chat.admin_id == getUserConfig().getClientUserId()) {
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

                    if (chat.key_create_date == 0) {
                        chat.key_create_date = getConnectionsManager().getCurrentTime();
                    }
                    chat.key_use_count_out++;
                    if ((chat.key_use_count_out >= 100 || chat.key_create_date < getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 7) && chat.exchange_id == 0 && chat.future_key_fingerprint == 0) {
                        requestNewSecretChatKey(chat);
                    }

                    getMessagesStorage().updateEncryptedChatSeq(chat, false);
                    newMsgObj.seq_in = layer.in_seq_no;
                    newMsgObj.seq_out = layer.out_seq_no;
                    getMessagesStorage().setMessageSeq(newMsgObj.id, newMsgObj.seq_in, newMsgObj.seq_out);
                } else {
                    layer.in_seq_no = newMsgObj.seq_in;
                    layer.out_seq_no = newMsgObj.seq_out;
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(req + " send message with in_seq = " + layer.in_seq_no + " out_seq = " + layer.out_seq_no);
                }

                int len = toEncryptObject.getObjectSize();
                NativeByteBuffer toEncrypt = new NativeByteBuffer(4 + len);
                toEncrypt.writeInt32(len);
                toEncryptObject.serializeToStream(toEncrypt);

                len = toEncrypt.length();
                int extraLen = len % 16 != 0 ? 16 - len % 16 : 0;
                extraLen += (2 + Utilities.random.nextInt(3)) * 16;

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
                boolean incoming = chat.admin_id != getUserConfig().getClientUserId();

                messageKeyFull = Utilities.computeSHA256(chat.auth_key, 88 + (incoming ? 8 : 0), 32, dataForEncryption.buffer, 0, dataForEncryption.buffer.limit());
                System.arraycopy(messageKeyFull, 8, messageKey, 0, 16);

                toEncrypt.reuse();

                MessageKeyData keyData = MessageKeyData.generateMessageKeyData(chat.auth_key, messageKey, incoming, 2);

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
                        req2.silent = newMsgObj.silent;
                        req2.data = data;
                        req2.random_id = req.random_id;
                        req2.peer = new TLRPC.TL_inputEncryptedChat();
                        req2.peer.chat_id = chat.id;
                        req2.peer.access_hash = chat.access_hash;
                        reqToSend = req2;
                    }
                } else {
                    TLRPC.TL_messages_sendEncryptedFile req2 = new TLRPC.TL_messages_sendEncryptedFile();
                    req2.silent = newMsgObj.silent;
                    req2.data = data;
                    req2.random_id = req.random_id;
                    req2.peer = new TLRPC.TL_inputEncryptedChat();
                    req2.peer.chat_id = chat.id;
                    req2.peer.access_hash = chat.access_hash;
                    req2.file = encryptedFile;
                    reqToSend = req2;
                }
                getConnectionsManager().sendRequest(reqToSend, (response, error) -> {
                    if (error == null) {
                        if (req.action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                            TLRPC.EncryptedChat currentChat = getMessagesController().getEncryptedChat(chat.id);
                            if (currentChat == null) {
                                currentChat = chat;
                            }

                            if (currentChat.key_hash == null) {
                                currentChat.key_hash = AndroidUtilities.calcAuthKeyHash(currentChat.auth_key);
                            }

                            if (currentChat.key_hash.length == 16) {
                                try {
                                    byte[] sha256 = Utilities.computeSHA256(chat.auth_key, 0, chat.auth_key.length);
                                    byte[] key_hash = new byte[36];
                                    System.arraycopy(chat.key_hash, 0, key_hash, 0, 16);
                                    System.arraycopy(sha256, 0, key_hash, 16, 20);
                                    currentChat.key_hash = key_hash;
                                    getMessagesStorage().updateEncryptedChat(currentChat);
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                }
                            }

                            sendingNotifyLayer.remove((Integer) currentChat.id);
                            currentChat.layer = AndroidUtilities.setMyLayerVersion(currentChat.layer, CURRENT_SECRET_CHAT_LAYER);
                            getMessagesStorage().updateEncryptedChatLayer(currentChat);
                        }
                    }
                    if (error == null) {
                        String attachPath = newMsgObj.attachPath;
                        TLRPC.messages_SentEncryptedMessage res = (TLRPC.messages_SentEncryptedMessage) response;
                        if (isSecretVisibleMessage(newMsgObj)) {
                            newMsgObj.date = res.date;
                        }
                        int existFlags;
                        if (newMsg != null && res.file instanceof TLRPC.TL_encryptedFile) {
                            updateMediaPaths(newMsg, res.file, req, originalPath);
                            existFlags = newMsg.getMediaExistanceFlags();
                        } else {
                            existFlags = 0;
                        }
                        getMessagesStorage().getStorageQueue().postRunnable(() -> {
                            if (isSecretInvisibleMessage(newMsgObj)) {
                                res.date = 0;
                            }
                            getMessagesStorage().updateMessageStateAndId(newMsgObj.random_id, 0, newMsgObj.id, newMsgObj.id, res.date, false, 0);
                            AndroidUtilities.runOnUIThread(() -> {
                                newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, newMsgObj.id, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, false);
                                getSendMessagesHelper().processSentMessage(newMsgObj.id);
                                if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj)) {
                                    getSendMessagesHelper().stopVideoService(attachPath);
                                }
                                getSendMessagesHelper().removeFromSendingMessages(newMsgObj.id, false);
                            });
                        });
                    } else {
                        getMessagesStorage().markMessageAsSendError(newMsgObj, false);
                        AndroidUtilities.runOnUIThread(() -> {
                            newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                            getNotificationCenter().postNotificationName(NotificationCenter.messageSendError, newMsgObj.id);
                            getSendMessagesHelper().processSentMessage(newMsgObj.id);
                            if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj)) {
                                getSendMessagesHelper().stopVideoService(newMsgObj.attachPath);
                            }
                            getSendMessagesHelper().removeFromSendingMessages(newMsgObj.id, false);
                        });
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void applyPeerLayer(TLRPC.EncryptedChat chat, int newPeerLayer) {
        int currentPeerLayer = AndroidUtilities.getPeerLayerVersion(chat.layer);
        if (newPeerLayer <= currentPeerLayer) {
            return;
        }
        if (chat.key_hash.length == 16) {
            try {
                byte[] sha256 = Utilities.computeSHA256(chat.auth_key, 0, chat.auth_key.length);
                byte[] key_hash = new byte[36];
                System.arraycopy(chat.key_hash, 0, key_hash, 0, 16);
                System.arraycopy(sha256, 0, key_hash, 16, 20);
                chat.key_hash = key_hash;
                getMessagesStorage().updateEncryptedChat(chat);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        chat.layer = AndroidUtilities.setPeerLayerVersion(chat.layer, newPeerLayer);
        getMessagesStorage().updateEncryptedChatLayer(chat);
        if (currentPeerLayer < CURRENT_SECRET_CHAT_LAYER) {
            sendNotifyLayerMessage(chat, null);
        }
        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatUpdated, chat));
    }

    public TLRPC.Message processDecryptedObject(TLRPC.EncryptedChat chat, TLRPC.EncryptedFile file, int date, TLObject object, boolean new_key_used) {
        if (object != null) {
            long from_id = chat.admin_id;
            if (from_id == getUserConfig().getClientUserId()) {
                from_id = chat.participant_id;
            }

            if (chat.exchange_id == 0 && chat.future_key_fingerprint == 0 && chat.key_use_count_in >= 120) {
                requestNewSecretChatKey(chat);
            }

            if (chat.exchange_id == 0 && chat.future_key_fingerprint != 0 && !new_key_used) {
                chat.future_auth_key = new byte[256];
                chat.future_key_fingerprint = 0;
                getMessagesStorage().updateEncryptedChat(chat);
            } else if (chat.exchange_id != 0 && new_key_used) {
                chat.key_fingerprint = chat.future_key_fingerprint;
                chat.auth_key = chat.future_auth_key;
                chat.key_create_date = getConnectionsManager().getCurrentTime();
                chat.future_auth_key = new byte[256];
                chat.future_key_fingerprint = 0;
                chat.key_use_count_in = 0;
                chat.key_use_count_out = 0;
                chat.exchange_id = 0;

                getMessagesStorage().updateEncryptedChat(chat);
            }

            if (object instanceof TLRPC.TL_decryptedMessage) {
                TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage) object;
                TLRPC.TL_message newMessage;
                newMessage = new TLRPC.TL_message_secret();
                newMessage.ttl = decryptedMessage.ttl;
                newMessage.entities = decryptedMessage.entities;
                newMessage.message = decryptedMessage.message;
                newMessage.date = date;
                newMessage.local_id = newMessage.id = getUserConfig().getNewMessageId();
                newMessage.silent = decryptedMessage.silent;
                getUserConfig().saveConfig(false);
                newMessage.from_id = new TLRPC.TL_peerUser();
                newMessage.from_id.user_id = from_id;
                newMessage.peer_id = new TLRPC.TL_peerUser();
                newMessage.peer_id.user_id = getUserConfig().getClientUserId();
                newMessage.random_id = decryptedMessage.random_id;
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
                newMessage.dialog_id = DialogObject.makeEncryptedDialogId(chat.id);
                if (decryptedMessage.reply_to_random_id != 0) {
                    newMessage.reply_to = new TLRPC.TL_messageReplyHeader();
                    newMessage.reply_to.reply_to_random_id = decryptedMessage.reply_to_random_id;
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
                    newMessage.media.vcard = "";
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
                    if (TextUtils.isEmpty(newMessage.message)) {
                        newMessage.message = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    }
                    newMessage.media.photo = new TLRPC.TL_photo();
                    newMessage.media.photo.file_reference = new byte[0];
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
                        newMessage.media.flags |= 4;
                    }

                    TLRPC.TL_photoSize big = new TLRPC.TL_photoSize_layer127();
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
                    if (TextUtils.isEmpty(newMessage.message)) {
                        newMessage.message = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    }
                    newMessage.media.document.date = date;
                    newMessage.media.document.size = file.size;
                    newMessage.media.document.id = file.id;
                    newMessage.media.document.access_hash = file.access_hash;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "video/mp4";
                    }
                    byte[] thumb = ((TLRPC.TL_decryptedMessageMediaVideo) decryptedMessage.media).thumb;
                    TLRPC.PhotoSize photoSize;
                    if (thumb != null && thumb.length != 0 && thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        photoSize = new TLRPC.TL_photoCachedSize();
                        photoSize.bytes = thumb;
                        photoSize.w = decryptedMessage.media.thumb_w;
                        photoSize.h = decryptedMessage.media.thumb_h;
                        photoSize.type = "s";
                        photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                    } else {
                        photoSize = new TLRPC.TL_photoSizeEmpty();
                        photoSize.type = "s";
                    }
                    newMessage.media.document.thumbs.add(photoSize);
                    newMessage.media.document.flags |= 1;
                    TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                    attributeVideo.w = decryptedMessage.media.w;
                    attributeVideo.h = decryptedMessage.media.h;
                    attributeVideo.duration = decryptedMessage.media.duration;
                    attributeVideo.supports_streaming = false;
                    newMessage.media.document.attributes.add(attributeVideo);
                    if (newMessage.ttl != 0) {
                        newMessage.media.ttl_seconds = newMessage.ttl;
                        newMessage.media.flags |= 4;
                    }
                    if (newMessage.ttl != 0) {
                        newMessage.ttl = Math.max(decryptedMessage.media.duration + 1, newMessage.ttl);
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument) {
                    if (decryptedMessage.media.key == null || decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv == null || decryptedMessage.media.iv.length != 32) {
                        return null;
                    }
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.flags |= 3;
                    if (TextUtils.isEmpty(newMessage.message)) {
                        newMessage.message = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    }
                    newMessage.media.document = new TLRPC.TL_documentEncrypted();
                    newMessage.media.document.id = file.id;
                    newMessage.media.document.access_hash = file.access_hash;
                    newMessage.media.document.date = date;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaDocument_layer8) {
                        TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                        fileName.file_name = decryptedMessage.media.file_name;
                        newMessage.media.document.attributes.add(fileName);
                    } else {
                        newMessage.media.document.attributes = decryptedMessage.media.attributes;
                    }
                    if (newMessage.ttl > 0) {
                        for (int a = 0, N = newMessage.media.document.attributes.size(); a < N; a++) {
                            TLRPC.DocumentAttribute attribute = newMessage.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeAudio || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                newMessage.ttl = Math.max(attribute.duration + 1, newMessage.ttl);
                                break;
                            }
                        }
                        newMessage.ttl = Math.max(decryptedMessage.media.duration + 1, newMessage.ttl);
                    }
                    newMessage.media.document.size = decryptedMessage.media.size != 0 ? Math.min(decryptedMessage.media.size, file.size) : file.size;
                    newMessage.media.document.key = decryptedMessage.media.key;
                    newMessage.media.document.iv = decryptedMessage.media.iv;
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "";
                    } else if ("application/x-tgsticker".equals(newMessage.media.document.mime_type) || "application/x-tgsdice".equals(newMessage.media.document.mime_type)) {
                        newMessage.media.document.mime_type = "application/x-bad_tgsticker";
                    }
                    byte[] thumb = ((TLRPC.TL_decryptedMessageMediaDocument) decryptedMessage.media).thumb;
                    TLRPC.PhotoSize photoSize;
                    if (thumb != null && thumb.length != 0 && thumb.length <= 6000 && decryptedMessage.media.thumb_w <= 100 && decryptedMessage.media.thumb_h <= 100) {
                        photoSize = new TLRPC.TL_photoCachedSize();
                        photoSize.bytes = thumb;
                        photoSize.w = decryptedMessage.media.thumb_w;
                        photoSize.h = decryptedMessage.media.thumb_h;
                        photoSize.type = "s";
                        photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                    } else {
                        photoSize = new TLRPC.TL_photoSizeEmpty();
                        photoSize.type = "s";
                    }
                    newMessage.media.document.thumbs.add(photoSize);
                    newMessage.media.document.flags |= 1;
                    newMessage.media.document.dc_id = file.dc_id;
                    if (MessageObject.isVoiceMessage(newMessage) || MessageObject.isRoundVideoMessage(newMessage)) {
                        newMessage.media_unread = true;
                    }
                } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaExternalDocument) {
                    newMessage.media = new TLRPC.TL_messageMediaDocument();
                    newMessage.media.flags |= 3;
                    newMessage.message = "";
                    newMessage.media.document = new TLRPC.TL_document();
                    newMessage.media.document.id = decryptedMessage.media.id;
                    newMessage.media.document.access_hash = decryptedMessage.media.access_hash;
                    newMessage.media.document.file_reference = new byte[0];
                    newMessage.media.document.date = decryptedMessage.media.date;
                    newMessage.media.document.attributes = decryptedMessage.media.attributes;
                    newMessage.media.document.mime_type = decryptedMessage.media.mime_type;
                    newMessage.media.document.dc_id = decryptedMessage.media.dc_id;
                    newMessage.media.document.size = decryptedMessage.media.size;
                    newMessage.media.document.thumbs.add(((TLRPC.TL_decryptedMessageMediaExternalDocument) decryptedMessage.media).thumb);
                    newMessage.media.document.flags |= 1;
                    if (newMessage.media.document.mime_type == null) {
                        newMessage.media.document.mime_type = "";
                    }
                    if (MessageObject.isAnimatedStickerMessage(newMessage)) {
                        newMessage.stickerVerified = 0;
                        getMediaDataController().verifyAnimatedStickerMessage(newMessage, true);
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
                    if (TextUtils.isEmpty(newMessage.message)) {
                        newMessage.message = decryptedMessage.media.caption != null ? decryptedMessage.media.caption : "";
                    }
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
                    if (newMessage.media.document.thumbs.isEmpty()) {
                        TLRPC.PhotoSize thumb = new TLRPC.TL_photoSizeEmpty();
                        thumb.type = "s";
                        newMessage.media.document.thumbs.add(thumb);
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
                if (newMessage.ttl != 0 && newMessage.media.ttl_seconds == 0) {
                    newMessage.media.ttl_seconds = newMessage.ttl;
                    newMessage.media.flags |= 4;
                }
                if (newMessage.message != null) {
                    newMessage.message = newMessage.message.replace('\u202E', ' ');
                }
                return newMessage;
            } else if (object instanceof TLRPC.TL_decryptedMessageService) {
                TLRPC.TL_decryptedMessageService serviceMessage = (TLRPC.TL_decryptedMessageService) object;
                if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL || serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        newMessage.action = new TLRPC.TL_messageEncryptedAction();
                        if (serviceMessage.action.ttl_seconds < 0 || serviceMessage.action.ttl_seconds > 60 * 60 * 24 * 365) {
                            serviceMessage.action.ttl_seconds = 60 * 60 * 24 * 365;
                        }
                        chat.ttl = serviceMessage.action.ttl_seconds;
                        newMessage.action.encryptedAction = serviceMessage.action;
                        getMessagesStorage().updateEncryptedChatTTL(chat);
                    } else {
                        newMessage.action = new TLRPC.TL_messageEncryptedAction();
                        newMessage.action.encryptedAction = serviceMessage.action;
                    }
                    newMessage.local_id = newMessage.id = getUserConfig().getNewMessageId();
                    getUserConfig().saveConfig(false);
                    newMessage.unread = true;
                    newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    newMessage.date = date;
                    newMessage.from_id = new TLRPC.TL_peerUser();
                    newMessage.from_id.user_id = from_id;
                    newMessage.peer_id = new TLRPC.TL_peerUser();
                    newMessage.peer_id.user_id = getUserConfig().getClientUserId();
                    newMessage.dialog_id = DialogObject.makeEncryptedDialogId(chat.id);
                    return newMessage;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
                    long did = DialogObject.makeEncryptedDialogId(chat.id);
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(did);
                        if (dialog != null) {
                            dialog.unread_count = 0;
                            getMessagesController().dialogMessage.remove(dialog.id);
                        }
                        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                            getNotificationsController().processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
                            LongSparseIntArray dialogsToUpdate = new LongSparseIntArray(1);
                            dialogsToUpdate.put(did, 0);
                            getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
                        }));
                        getMessagesStorage().deleteDialog(did, 1);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        getNotificationCenter().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false, null);
                    });
                    return null;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
                    if (!serviceMessage.action.random_ids.isEmpty()) {
                        pendingEncMessagesToDelete.addAll(serviceMessage.action.random_ids);
                    }
                    return null;
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionReadMessages) {
                    if (!serviceMessage.action.random_ids.isEmpty()) {
                        int time = getConnectionsManager().getCurrentTime();
                        getMessagesStorage().createTaskForSecretChat(chat.id, time, time, 1, serviceMessage.action.random_ids);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
                    applyPeerLayer(chat, serviceMessage.action.layer);
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
                    if (chat.exchange_id != 0) {
                        if (chat.exchange_id > serviceMessage.action.exchange_id) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("we already have request key with higher exchange_id");
                            }
                            return null;
                        } else {
                            sendAbortKeyMessage(chat, null, chat.exchange_id); //TODO don't send?
                        }
                    }

                    byte[] salt = new byte[256];
                    Utilities.random.nextBytes(salt);
                    BigInteger p = new BigInteger(1, getMessagesStorage().getSecretPBytes());
                    BigInteger g_b = BigInteger.valueOf(getMessagesStorage().getSecretG());
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
                            correctedAuth[a] = 0;
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

                    getMessagesStorage().updateEncryptedChat(chat);

                    sendAcceptKeyMessage(chat, null);
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionAcceptKey) {
                    if (chat.exchange_id == serviceMessage.action.exchange_id) {

                        BigInteger p = new BigInteger(1, getMessagesStorage().getSecretPBytes());
                        BigInteger i_authKey = new BigInteger(1, serviceMessage.action.g_b);

                        if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
                            chat.future_auth_key = new byte[256];
                            chat.future_key_fingerprint = 0;
                            chat.exchange_id = 0;
                            getMessagesStorage().updateEncryptedChat(chat);

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
                                correctedAuth[a] = 0;
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
                            getMessagesStorage().updateEncryptedChat(chat);
                            sendCommitKeyMessage(chat, null);
                        } else {
                            chat.future_auth_key = new byte[256];
                            chat.future_key_fingerprint = 0;
                            chat.exchange_id = 0;
                            getMessagesStorage().updateEncryptedChat(chat);
                            sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                        }
                    } else {
                        chat.future_auth_key = new byte[256];
                        chat.future_key_fingerprint = 0;
                        chat.exchange_id = 0;
                        getMessagesStorage().updateEncryptedChat(chat);
                        sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionCommitKey) {
                    if (chat.exchange_id == serviceMessage.action.exchange_id && chat.future_key_fingerprint == serviceMessage.action.key_fingerprint) {
                        long old_fingerprint = chat.key_fingerprint;
                        byte[] old_key = chat.auth_key;
                        chat.key_fingerprint = chat.future_key_fingerprint;
                        chat.auth_key = chat.future_auth_key;
                        chat.key_create_date = getConnectionsManager().getCurrentTime();
                        chat.future_auth_key = old_key;
                        chat.future_key_fingerprint = old_fingerprint;
                        chat.key_use_count_in = 0;
                        chat.key_use_count_out = 0;
                        chat.exchange_id = 0;

                        getMessagesStorage().updateEncryptedChat(chat);

                        sendNoopMessage(chat, null);
                    } else {
                        chat.future_auth_key = new byte[256];
                        chat.future_key_fingerprint = 0;
                        chat.exchange_id = 0;
                        getMessagesStorage().updateEncryptedChat(chat);
                        sendAbortKeyMessage(chat, null, serviceMessage.action.exchange_id);
                    }
                } else if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionAbortKey) {
                    if (chat.exchange_id == serviceMessage.action.exchange_id) {
                        chat.future_auth_key = new byte[256];
                        chat.future_key_fingerprint = 0;
                        chat.exchange_id = 0;
                        getMessagesStorage().updateEncryptedChat(chat);
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
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("unknown message " + object);
                }
            }
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("unknown TLObject");
            }
        }
        return null;
    }

    private TLRPC.Message createDeleteMessage(int mid, int seq_out, int seq_in, long random_id, TLRPC.EncryptedChat encryptedChat) {
        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();
        newMsg.action = new TLRPC.TL_messageEncryptedAction();
        newMsg.action.encryptedAction = new TLRPC.TL_decryptedMessageActionDeleteMessages();
        newMsg.action.encryptedAction.random_ids.add(random_id);
        newMsg.local_id = newMsg.id = mid;
        newMsg.from_id = new TLRPC.TL_peerUser();
        newMsg.from_id.user_id = getUserConfig().getClientUserId();
        newMsg.unread = true;
        newMsg.out = true;
        newMsg.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        newMsg.dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
        newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
        newMsg.seq_in = seq_in;
        newMsg.seq_out = seq_out;
        newMsg.peer_id = new TLRPC.TL_peerUser();
        if (encryptedChat.participant_id == getUserConfig().getClientUserId()) {
            newMsg.peer_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.peer_id.user_id = encryptedChat.participant_id;
        }
        newMsg.date = 0;
        newMsg.random_id = random_id;
        return newMsg;
    }

    private void resendMessages(int startSeq, int endSeq, TLRPC.EncryptedChat encryptedChat) {
        if (encryptedChat == null || endSeq - startSeq < 0) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                int sSeq = startSeq;
                if (encryptedChat.admin_id == getUserConfig().getClientUserId() && sSeq % 2 == 0) {
                    sSeq++;
                }

                SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT uid FROM requested_holes WHERE uid = %d AND ((seq_out_start >= %d AND %d <= seq_out_end) OR (seq_out_start >= %d AND %d <= seq_out_end))", encryptedChat.id, sSeq, sSeq, endSeq, endSeq));
                boolean exists = cursor.next();
                cursor.dispose();
                if (exists) {
                    return;
                }

                long dialog_id = DialogObject.makeEncryptedDialogId(encryptedChat.id);
                SparseArray<TLRPC.Message> messagesToResend = new SparseArray<>();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                for (int a = sSeq; a <= endSeq; a += 2) {
                    messagesToResend.put(a, null);
                }
                cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, r.random_id, s.seq_in, s.seq_out, m.ttl, s.mid FROM messages_seq as s LEFT JOIN randoms_v2 as r ON r.mid = s.mid LEFT JOIN messages_v2 as m ON m.mid = s.mid WHERE m.uid = %d AND m.out = 1 AND s.seq_out >= %d AND s.seq_out <= %d ORDER BY seq_out ASC", dialog_id, sSeq, endSeq));
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
                        message.readAttachPath(data, getUserConfig().clientUserId);
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
                if (messagesToResend.size() != 0) {
                    for (int a = 0; a < messagesToResend.size(); a++) {
                        int seq = messagesToResend.keyAt(a);
                        messages.add(createDeleteMessage(getUserConfig().getNewMessageId(), seq, seq + 1, Utilities.random.nextLong(), encryptedChat));
                    }
                    getUserConfig().saveConfig(false);
                }
                Collections.sort(messages, (lhs, rhs) -> AndroidUtilities.compare(lhs.seq_out, rhs.seq_out));
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                encryptedChats.add(encryptedChat);

                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0; a < messages.size(); a++) {
                        TLRPC.Message message = messages.get(a);
                        MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                        messageObject.resendAsIs = true;
                        getSendMessagesHelper().retrySendMessage(messageObject, true);
                    }
                });

                getSendMessagesHelper().processUnsentMessages(messages, null, new ArrayList<>(), new ArrayList<>(), encryptedChats);
                getMessagesStorage().getDatabase().executeFast(String.format(Locale.US, "REPLACE INTO requested_holes VALUES(%d, %d, %d)", encryptedChat.id, sSeq, endSeq)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void checkSecretHoles(TLRPC.EncryptedChat chat, ArrayList<TLRPC.Message> messages) {
        ArrayList<TL_decryptedMessageHolder> holes = secretHolesQueue.get(chat.id);
        if (holes == null) {
            return;
        }
        Collections.sort(holes, (lhs, rhs) -> {
            if (lhs.layer.out_seq_no > rhs.layer.out_seq_no) {
                return 1;
            } else if (lhs.layer.out_seq_no < rhs.layer.out_seq_no) {
                return -1;
            }
            return 0;
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
            getMessagesStorage().updateEncryptedChatSeq(chat, true);
        }
    }

    private boolean decryptWithMtProtoVersion(NativeByteBuffer is, byte[] keyToDecrypt, byte[] messageKey, int version, boolean incoming, boolean encryptOnError) {
        if (version == 1) {
            incoming = false;
        }
        MessageKeyData keyData = MessageKeyData.generateMessageKeyData(keyToDecrypt, messageKey, incoming, version);
        Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, false, false, 24, is.limit() - 24);

        int error = 0;

        int len = is.readInt32(false);
        byte[] messageKeyFull;
        if (version == 2) {
            messageKeyFull = Utilities.computeSHA256(keyToDecrypt, 88 + (incoming ? 8 : 0), 32, is.buffer, 24, is.buffer.limit());
            if (!Utilities.arraysEquals(messageKey, 0, messageKeyFull, 8)) {
                if (encryptOnError) {
                    Utilities.aesIgeEncryption(is.buffer, keyData.aesKey, keyData.aesIv, true, false, 24, is.limit() - 24);
                    is.position(24);
                }
                error |= 1;
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
                error |= 1;
            }
        }
        if (len <= 0) {
            error |= 1;
        }
        if (len > is.limit() - 28) {
            error |= 1;
        }
        int padding = is.limit() - 28 - len;
        if (version == 2) {
            if (padding < 12) {
                error |= 1;
            }
            if (padding > 1024) {
                error |= 1;
            }
        } else {
            if (padding > 15) {
                error |= 1;
            }
        }

        return error == 0;
    }

    protected ArrayList<TLRPC.Message> decryptMessage(TLRPC.EncryptedMessage message) {
        TLRPC.EncryptedChat chat = getMessagesController().getEncryptedChatDB(message.chat_id, true);
        if (chat == null || chat instanceof TLRPC.TL_encryptedChatDiscarded) {
            return null;
        }

        try {
            if (chat instanceof TLRPC.TL_encryptedChatWaiting) {
                ArrayList<TLRPC.Update> updates = pendingSecretMessages.get(chat.id);
                if (updates == null) {
                    updates = new ArrayList<>();
                    pendingSecretMessages.put(chat.id, updates);
                }
                TLRPC.TL_updateNewEncryptedMessage updateNewEncryptedMessage = new TLRPC.TL_updateNewEncryptedMessage();
                updateNewEncryptedMessage.message = message;
                updates.add(updateNewEncryptedMessage);
                return null;
            }
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
            int mtprotoVersion = 2;
            int decryptedWithVersion = 2;

            if (keyToDecrypt != null) {
                byte[] messageKey = is.readData(16, false);

                boolean incoming = chat.admin_id == getUserConfig().getClientUserId();
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
                        if (!decryptWithMtProtoVersion(is, keyToDecrypt, messageKey, 2, incoming, true)) {
                            return null;
                        }
                    }
                }

                TLObject object = TLClassStore.Instance().TLdeserialize(is, is.readInt32(false), false);

                is.reuse();
                if (!new_key_used) {
                    chat.key_use_count_in++;
                }
                if (object instanceof TLRPC.TL_decryptedMessageLayer) {
                    TLRPC.TL_decryptedMessageLayer layer = (TLRPC.TL_decryptedMessageLayer) object;
                    if (chat.seq_in == 0 && chat.seq_out == 0) {
                        if (chat.admin_id == getUserConfig().getClientUserId()) {
                            chat.seq_out = 1;
                            chat.seq_in = -2;
                        } else {
                            chat.seq_in = -1;
                        }
                    }
                    if (layer.random_bytes.length < 15) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("got random bytes less than needed");
                        }
                        return null;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("current chat in_seq = " + chat.seq_in + " out_seq = " + chat.seq_out);
                        FileLog.d("got message with in_seq = " + layer.in_seq_no + " out_seq = " + layer.out_seq_no);
                    }
                    if (layer.out_seq_no <= chat.seq_in) {
                        return null;
                    }
                    if (decryptedWithVersion == 1 && chat.mtproto_seq != 0 && layer.out_seq_no >= chat.mtproto_seq) {
                        return null;
                    }
                    if (chat.seq_in != layer.out_seq_no - 2) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("got hole");
                        }
                        sendResendMessage(chat, chat.seq_in + 2, layer.out_seq_no - 2, null);
                        ArrayList<TL_decryptedMessageHolder> arr = secretHolesQueue.get(chat.id);
                        if (arr == null) {
                            arr = new ArrayList<>();
                            secretHolesQueue.put(chat.id, arr);
                        }
                        if (arr.size() >= 4) {
                            secretHolesQueue.remove(chat.id);
                            TLRPC.TL_encryptedChatDiscarded newChat = new TLRPC.TL_encryptedChatDiscarded();
                            newChat.id = chat.id;
                            newChat.user_id = chat.user_id;
                            newChat.auth_key = chat.auth_key;
                            newChat.key_create_date = chat.key_create_date;
                            newChat.key_use_count_in = chat.key_use_count_in;
                            newChat.key_use_count_out = chat.key_use_count_out;
                            newChat.seq_in = chat.seq_in;
                            newChat.seq_out = chat.seq_out;
                            AndroidUtilities.runOnUIThread(() -> {
                                getMessagesController().putEncryptedChat(newChat, false);
                                getMessagesStorage().updateEncryptedChat(newChat);
                                getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
                            });
                            declineSecretChat(chat.id, false);
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
                    getMessagesStorage().updateEncryptedChatSeq(chat, true);
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
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e(String.format("fingerprint mismatch %x", fingerprint));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return null;
    }

    public void requestNewSecretChatKey(TLRPC.EncryptedChat encryptedChat) {
        byte[] salt = new byte[256];
        Utilities.random.nextBytes(salt);

        BigInteger i_g_a = BigInteger.valueOf(getMessagesStorage().getSecretG());
        i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, getMessagesStorage().getSecretPBytes()));
        byte[] g_a = i_g_a.toByteArray();
        if (g_a.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(g_a, 1, correctedAuth, 0, 256);
            g_a = correctedAuth;
        }

        encryptedChat.exchange_id = getSendMessagesHelper().getNextRandomId();
        encryptedChat.a_or_b = salt;
        encryptedChat.g_a = g_a;

        getMessagesStorage().updateEncryptedChat(encryptedChat);

        sendRequestKeyMessage(encryptedChat, null);
    }

    public void processAcceptedSecretChat(TLRPC.EncryptedChat encryptedChat) {
        BigInteger p = new BigInteger(1, getMessagesStorage().getSecretPBytes());
        BigInteger i_authKey = new BigInteger(1, encryptedChat.g_a_or_b);

        if (!Utilities.isGoodGaAndGb(i_authKey, p)) {
            declineSecretChat(encryptedChat.id, false);
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
                correctedAuth[a] = 0;
            }
            authKey = correctedAuth;
        }
        byte[] authKeyHash = Utilities.computeSHA1(authKey);
        byte[] authKeyId = new byte[8];
        System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
        long fingerprint = Utilities.bytesToLong(authKeyId);
        if (encryptedChat.key_fingerprint == fingerprint) {
            encryptedChat.auth_key = authKey;
            encryptedChat.key_create_date = getConnectionsManager().getCurrentTime();
            encryptedChat.seq_in = -2;
            encryptedChat.seq_out = 1;
            getMessagesStorage().updateEncryptedChat(encryptedChat);
            getMessagesController().putEncryptedChat(encryptedChat, false);
            ArrayList<TLRPC.Update> pendingUpdates = pendingSecretMessages.get(encryptedChat.id);
            if (pendingUpdates != null) {
                getMessagesController().processUpdateArray(pendingUpdates, null, null, false, 0);
                pendingSecretMessages.remove(encryptedChat.id);
            }
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatUpdated, encryptedChat);
                sendNotifyLayerMessage(encryptedChat, null);
            });
        } else {
            TLRPC.TL_encryptedChatDiscarded newChat = new TLRPC.TL_encryptedChatDiscarded();
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
            getMessagesStorage().updateEncryptedChat(newChat);
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putEncryptedChat(newChat, false);
                getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
            });
            declineSecretChat(encryptedChat.id, false);
        }
    }

    public void declineSecretChat(int chat_id, boolean revoke) {
        declineSecretChat(chat_id, revoke, 0);
    }

    public void declineSecretChat(int chat_id, boolean revoke, long taskId) {
        final long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(4 + 4 + 4);
                data.writeInt32(100);
                data.writeInt32(chat_id);
                data.writeBool(revoke);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        TLRPC.TL_messages_discardEncryption req = new TLRPC.TL_messages_discardEncryption();
        req.chat_id = chat_id;
        req.delete_history = revoke;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
        });
    }

    public void acceptSecretChat(TLRPC.EncryptedChat encryptedChat) {
        if (acceptingChats.get(encryptedChat.id) != null) {
            return;
        }
        acceptingChats.put(encryptedChat.id, encryptedChat);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = getMessagesStorage().getLastSecretVersion();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
                if (response instanceof TLRPC.TL_messages_dhConfig) {
                    if (!Utilities.isGoodPrime(res.p, res.g)) {
                        acceptingChats.remove(encryptedChat.id);
                        declineSecretChat(encryptedChat.id, false);
                        return;
                    }

                    getMessagesStorage().setSecretPBytes(res.p);
                    getMessagesStorage().setSecretG(res.g);
                    getMessagesStorage().setLastSecretVersion(res.version);
                    getMessagesStorage().saveSecretParams(getMessagesStorage().getLastSecretVersion(), getMessagesStorage().getSecretG(), getMessagesStorage().getSecretPBytes());
                }
                byte[] salt = new byte[256];
                for (int a = 0; a < 256; a++) {
                    salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
                }
                encryptedChat.a_or_b = salt;
                encryptedChat.seq_in = -1;
                encryptedChat.seq_out = 0;
                BigInteger p = new BigInteger(1, getMessagesStorage().getSecretPBytes());
                BigInteger g_b = BigInteger.valueOf(getMessagesStorage().getSecretG());
                g_b = g_b.modPow(new BigInteger(1, salt), p);
                BigInteger g_a = new BigInteger(1, encryptedChat.g_a);

                if (!Utilities.isGoodGaAndGb(g_a, p)) {
                    acceptingChats.remove(encryptedChat.id);
                    declineSecretChat(encryptedChat.id, false);
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
                        correctedAuth[a] = 0;
                    }
                    authKey = correctedAuth;
                }
                byte[] authKeyHash = Utilities.computeSHA1(authKey);
                byte[] authKeyId = new byte[8];
                System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
                encryptedChat.auth_key = authKey;
                encryptedChat.key_create_date = getConnectionsManager().getCurrentTime();

                TLRPC.TL_messages_acceptEncryption req2 = new TLRPC.TL_messages_acceptEncryption();
                req2.g_b = g_b_bytes;
                req2.peer = new TLRPC.TL_inputEncryptedChat();
                req2.peer.chat_id = encryptedChat.id;
                req2.peer.access_hash = encryptedChat.access_hash;
                req2.key_fingerprint = Utilities.bytesToLong(authKeyId);
                getConnectionsManager().sendRequest(req2, (response1, error1) -> {
                    acceptingChats.remove(encryptedChat.id);
                    if (error1 == null) {
                        TLRPC.EncryptedChat newChat = (TLRPC.EncryptedChat) response1;
                        newChat.auth_key = encryptedChat.auth_key;
                        newChat.user_id = encryptedChat.user_id;
                        newChat.seq_in = encryptedChat.seq_in;
                        newChat.seq_out = encryptedChat.seq_out;
                        newChat.key_create_date = encryptedChat.key_create_date;
                        newChat.key_use_count_in = encryptedChat.key_use_count_in;
                        newChat.key_use_count_out = encryptedChat.key_use_count_out;
                        getMessagesStorage().updateEncryptedChat(newChat);
                        getMessagesController().putEncryptedChat(newChat, false);
                        AndroidUtilities.runOnUIThread(() -> {
                            getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatUpdated, newChat);
                            sendNotifyLayerMessage(newChat, null);
                        });
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            } else {
                acceptingChats.remove(encryptedChat.id);
            }
        });
    }

    public void startSecretChat(Context context, TLRPC.User user) {
        if (user == null || context == null) {
            return;
        }
        startingSecretChat = true;
        AlertDialog progressDialog = new AlertDialog(context, 3);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = getMessagesStorage().getLastSecretVersion();
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig) response;
                if (response instanceof TLRPC.TL_messages_dhConfig) {
                    if (!Utilities.isGoodPrime(res.p, res.g)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                if (!((Activity) context).isFinishing()) {
                                    progressDialog.dismiss();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                        return;
                    }
                    getMessagesStorage().setSecretPBytes(res.p);
                    getMessagesStorage().setSecretG(res.g);
                    getMessagesStorage().setLastSecretVersion(res.version);
                    getMessagesStorage().saveSecretParams(getMessagesStorage().getLastSecretVersion(), getMessagesStorage().getSecretG(), getMessagesStorage().getSecretPBytes());
                }
                byte[] salt = new byte[256];
                for (int a = 0; a < 256; a++) {
                    salt[a] = (byte) ((byte) (Utilities.random.nextDouble() * 256) ^ res.random[a]);
                }

                BigInteger i_g_a = BigInteger.valueOf(getMessagesStorage().getSecretG());
                i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, getMessagesStorage().getSecretPBytes()));
                byte[] g_a = i_g_a.toByteArray();
                if (g_a.length > 256) {
                    byte[] correctedAuth = new byte[256];
                    System.arraycopy(g_a, 1, correctedAuth, 0, 256);
                    g_a = correctedAuth;
                }

                TLRPC.TL_messages_requestEncryption req2 = new TLRPC.TL_messages_requestEncryption();
                req2.g_a = g_a;
                req2.user_id = getMessagesController().getInputUser(user);
                req2.random_id = Utilities.random.nextInt();
                getConnectionsManager().sendRequest(req2, (response1, error1) -> {
                    if (error1 == null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            startingSecretChat = false;
                            if (!((Activity) context).isFinishing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) response1;
                            chat.user_id = chat.participant_id;
                            chat.seq_in = -2;
                            chat.seq_out = 1;
                            chat.a_or_b = salt;
                            getMessagesController().putEncryptedChat(chat, false);
                            TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                            dialog.id = DialogObject.makeEncryptedDialogId(chat.id);
                            dialog.unread_count = 0;
                            dialog.top_message = 0;
                            dialog.last_message_date = getConnectionsManager().getCurrentTime();
                            getMessagesController().dialogs_dict.put(dialog.id, dialog);
                            getMessagesController().allDialogs.add(dialog);
                            getMessagesController().sortDialogs(null);
                            getMessagesStorage().putEncryptedChat(chat, user, dialog);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                            getNotificationCenter().postNotificationName(NotificationCenter.encryptedChatCreated, chat);
                            Utilities.stageQueue.postRunnable(() -> {
                                if (!delayedEncryptedChatUpdates.isEmpty()) {
                                    getMessagesController().processUpdateArray(delayedEncryptedChatUpdates, null, null, false, 0);
                                    delayedEncryptedChatUpdates.clear();
                                }
                            });
                        });
                    } else {
                        delayedEncryptedChatUpdates.clear();
                        AndroidUtilities.runOnUIThread(() -> {
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
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            } else {
                delayedEncryptedChatUpdates.clear();
                AndroidUtilities.runOnUIThread(() -> {
                    startingSecretChat = false;
                    if (!((Activity) context).isFinishing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        progressDialog.setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(reqId, true));
        try {
            progressDialog.show();
        } catch (Exception e) {
            //don't promt
        }
    }
}
