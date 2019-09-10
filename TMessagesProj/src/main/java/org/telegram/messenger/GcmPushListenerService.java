/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class GcmPushListenerService extends FirebaseMessagingService {

    public static final int NOTIFICATION_ID = 1;
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String from = message.getFrom();
        final Map data = message.getData();
        final long time = message.getSentTime();
        final long receiveTime = SystemClock.uptimeMillis();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("GCM received data: " + data + " from: " + from);
        }
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.stageQueue.postRunnable(() -> {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("GCM START PROCESSING");
                }
                int currentAccount = -1;
                String loc_key = null;
                String jsonString = null;
                try {
                    Object value = data.get("p");
                    if (!(value instanceof String)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("GCM DECRYPT ERROR 1");
                        }
                        onDecryptError();
                        return;
                    }
                    byte[] bytes = Base64.decode((String) value, Base64.URL_SAFE);
                    NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
                    buffer.writeBytes(bytes);
                    buffer.position(0);

                    if (SharedConfig.pushAuthKeyId == null) {
                        SharedConfig.pushAuthKeyId = new byte[8];
                        byte[] authKeyHash = Utilities.computeSHA1(SharedConfig.pushAuthKey);
                        System.arraycopy(authKeyHash, authKeyHash.length - 8, SharedConfig.pushAuthKeyId, 0, 8);
                    }
                    byte[] inAuthKeyId = new byte[8];
                    buffer.readBytes(inAuthKeyId, true);
                    if (!Arrays.equals(SharedConfig.pushAuthKeyId, inAuthKeyId)) {
                        onDecryptError();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d(String.format(Locale.US, "GCM DECRYPT ERROR 2 k1=%s k2=%s, key=%s", Utilities.bytesToHex(SharedConfig.pushAuthKeyId), Utilities.bytesToHex(inAuthKeyId), Utilities.bytesToHex(SharedConfig.pushAuthKey)));
                        }
                        return;
                    }

                    byte[] messageKey = new byte[16];
                    buffer.readBytes(messageKey, true);

                    MessageKeyData messageKeyData = MessageKeyData.generateMessageKeyData(SharedConfig.pushAuthKey, messageKey, true, 2);
                    Utilities.aesIgeEncryption(buffer.buffer, messageKeyData.aesKey, messageKeyData.aesIv, false, false, 24, bytes.length - 24);

                    byte[] messageKeyFull = Utilities.computeSHA256(SharedConfig.pushAuthKey, 88 + 8, 32, buffer.buffer, 24, buffer.buffer.limit());
                    if (!Utilities.arraysEquals(messageKey, 0, messageKeyFull, 8)) {
                        onDecryptError();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d(String.format("GCM DECRYPT ERROR 3, key = %s", Utilities.bytesToHex(SharedConfig.pushAuthKey)));
                        }
                        return;
                    }

                    int len = buffer.readInt32(true);
                    byte[] strBytes = new byte[len];
                    buffer.readBytes(strBytes, true);
                    jsonString = new String(strBytes);
                    JSONObject json = new JSONObject(jsonString);

                    if (json.has("loc_key")) {
                        loc_key = json.getString("loc_key");
                    } else {
                        loc_key = "";
                    }

                    JSONObject custom;
                    Object object = json.get("custom");
                    if (object instanceof JSONObject) {
                        custom = json.getJSONObject("custom");
                    } else {
                        custom = new JSONObject();
                    }

                    Object userIdObject;
                    if (json.has("user_id")) {
                        userIdObject = json.get("user_id");
                    } else {
                        userIdObject = null;
                    }
                    int accountUserId;
                    if (userIdObject == null) {
                        accountUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                    } else {
                        if (userIdObject instanceof Integer) {
                            accountUserId = (Integer) userIdObject;
                        } else if (userIdObject instanceof String) {
                            accountUserId = Utilities.parseInt((String) userIdObject);
                        } else {
                            accountUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                        }
                    }
                    int account = UserConfig.selectedAccount;
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        if (UserConfig.getInstance(a).getClientUserId() == accountUserId) {
                            account = a;
                            break;
                        }
                    }
                    final int accountFinal = currentAccount = account;
                    if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("GCM ACCOUNT NOT ACTIVATED");
                        }
                        countDownLatch.countDown();
                        return;
                    }
                    Object obj = data.get("google.sent_time");
                    switch (loc_key) {
                        case "DC_UPDATE": {
                            int dc = custom.getInt("dc");
                            String addr = custom.getString("addr");
                            String[] parts = addr.split(":");
                            if (parts.length != 2) {
                                countDownLatch.countDown();
                                return;
                            }
                            String ip = parts[0];
                            int port = Integer.parseInt(parts[1]);
                            ConnectionsManager.getInstance(currentAccount).applyDatacenterAddress(dc, ip, port);
                            ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe();
                            countDownLatch.countDown();
                            return;
                        }
                        case "MESSAGE_ANNOUNCEMENT": {
                            TLRPC.TL_updateServiceNotification update = new TLRPC.TL_updateServiceNotification();
                            update.popup = false;
                            update.flags = 2;
                            update.inbox_date = (int) (time / 1000);
                            update.message = json.getString("message");
                            update.type = "announcement";
                            update.media = new TLRPC.TL_messageMediaEmpty();
                            final TLRPC.TL_updates updates = new TLRPC.TL_updates();
                            updates.updates.add(update);
                            Utilities.stageQueue.postRunnable(() -> MessagesController.getInstance(accountFinal).processUpdates(updates, false));
                            ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe();
                            countDownLatch.countDown();
                            return;
                        }
                        case "SESSION_REVOKE": {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (UserConfig.getInstance(accountFinal).getClientUserId() != 0) {
                                    UserConfig.getInstance(accountFinal).clearConfig();
                                    MessagesController.getInstance(accountFinal).performLogout(0);
                                }
                            });
                            return;
                        }
                    }

                    int channel_id;
                    int chat_id;
                    int user_id;
                    long dialog_id = 0;
                    boolean scheduled;
                    if (custom.has("channel_id")) {
                        channel_id = custom.getInt("channel_id");
                        dialog_id = -channel_id;
                    } else {
                        channel_id = 0;
                    }
                    if (custom.has("from_id")) {
                        user_id = custom.getInt("from_id");
                        dialog_id = user_id;
                    } else {
                        user_id = 0;
                    }
                    if (custom.has("chat_id")) {
                        chat_id = custom.getInt("chat_id");
                        dialog_id = -chat_id;
                    } else {
                        chat_id = 0;
                    }
                    if (custom.has("encryption_id")) {
                        dialog_id = ((long) custom.getInt("encryption_id")) << 32;
                    }
                    if (custom.has("schedule")) {
                        scheduled = custom.getInt("schedule") == 1;
                    } else {
                        scheduled = false;
                    }
                    if (dialog_id == 0 && "ENCRYPTED_MESSAGE".equals(loc_key)) {
                        dialog_id = -(1L << 32);
                    }
                    if (dialog_id != 0) {
                        if ("READ_HISTORY".equals(loc_key)) {
                            int max_id = custom.getInt("max_id");
                            final ArrayList<TLRPC.Update> updates = new ArrayList<>();
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("GCM received read notification max_id = " + max_id + " for dialogId = " + dialog_id);
                            }
                            if (channel_id != 0) {
                                TLRPC.TL_updateReadChannelInbox update = new TLRPC.TL_updateReadChannelInbox();
                                update.channel_id = channel_id;
                                update.max_id = max_id;
                                updates.add(update);
                            } else {
                                TLRPC.TL_updateReadHistoryInbox update = new TLRPC.TL_updateReadHistoryInbox();
                                if (user_id != 0) {
                                    update.peer = new TLRPC.TL_peerUser();
                                    update.peer.user_id = user_id;
                                } else {
                                    update.peer = new TLRPC.TL_peerChat();
                                    update.peer.chat_id = chat_id;
                                }
                                update.max_id = max_id;
                                updates.add(update);
                            }
                            MessagesController.getInstance(accountFinal).processUpdateArray(updates, null, null, false, 0);
                            countDownLatch.countDown();
                        } else if ("MESSAGE_DELETED".equals(loc_key)) {
                            String messages = custom.getString("messages");
                            String[] messagesArgs = messages.split(",");
                            SparseArray<ArrayList<Integer>> deletedMessages = new SparseArray<>();
                            ArrayList<Integer> ids = new ArrayList<>();
                            for (int a = 0; a < messagesArgs.length; a++) {
                                ids.add(Utilities.parseInt(messagesArgs[a]));
                            }
                            deletedMessages.put(channel_id, ids);
                            NotificationsController.getInstance(currentAccount).removeDeletedMessagesFromNotifications(deletedMessages);

                            MessagesController.getInstance(currentAccount).deleteMessagesByPush(dialog_id, ids, channel_id);
                        } else if (!TextUtils.isEmpty(loc_key)) {
                            int msg_id;
                            if (custom.has("msg_id")) {
                                msg_id = custom.getInt("msg_id");
                            } else {
                                msg_id = 0;
                            }

                            long random_id;
                            if (custom.has("random_id")) {
                                random_id = Utilities.parseLong(custom.getString("random_id"));
                            } else {
                                random_id = 0;
                            }

                            boolean processNotification = false;
                            if (msg_id != 0) {
                                Integer currentReadValue = MessagesController.getInstance(currentAccount).dialogs_read_inbox_max.get(dialog_id);
                                if (currentReadValue == null) {
                                    currentReadValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
                                    MessagesController.getInstance(accountFinal).dialogs_read_inbox_max.put(dialog_id, currentReadValue);
                                }
                                if (msg_id > currentReadValue) {
                                    processNotification = true;
                                }
                            } else if (random_id != 0) {
                                if (!MessagesStorage.getInstance(account).checkMessageByRandomId(random_id)) {
                                    processNotification = true;
                                }
                            }
                            if (processNotification) {
                                int chat_from_id;

                                if (custom.has("chat_from_id")) {
                                    chat_from_id = custom.getInt("chat_from_id");
                                } else {
                                    chat_from_id = 0;
                                }
                                boolean mention = custom.has("mention") && custom.getInt("mention") != 0;
                                boolean silent = custom.has("silent") && custom.getInt("silent") != 0;

                                String[] args;
                                if (json.has("loc_args")) {
                                    JSONArray loc_args = json.getJSONArray("loc_args");
                                    args = new String[loc_args.length()];
                                    for (int a = 0; a < args.length; a++) {
                                        args[a] = loc_args.getString(a);
                                    }
                                } else {
                                    args = null;
                                }
                                String messageText = null;
                                String message1 = null;
                                String name = args[0];
                                String userName = null;
                                boolean localMessage = false;
                                boolean supergroup = false;
                                boolean pinned = false;
                                boolean channel = false;
                                boolean edited = custom.has("edit_date");
                                if (loc_key.startsWith("CHAT_")) {
                                    supergroup = channel_id != 0;
                                    userName = name;
                                    name = args[1];
                                } else if (loc_key.startsWith("PINNED_")) {
                                    supergroup = chat_from_id != 0;
                                    pinned = true;
                                } else if (loc_key.startsWith("CHANNEL_")) {
                                    channel = true;
                                }

                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("GCM received message notification " + loc_key + " for dialogId = " + dialog_id + " mid = " + msg_id);
                                }
                                switch (loc_key) {
                                    case "MESSAGE_TEXT":
                                    case "CHANNEL_MESSAGE_TEXT": {
                                        messageText = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, args[0], args[1]);
                                        message1 = args[1];
                                        break;
                                    }
                                    case "MESSAGE_NOTEXT": {
                                        messageText = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, args[0]);
                                        message1 = LocaleController.getString("Message", R.string.Message);
                                        break;
                                    }
                                    case "MESSAGE_PHOTO": {
                                        messageText = LocaleController.formatString("NotificationMessagePhoto", R.string.NotificationMessagePhoto, args[0]);
                                        message1 = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
                                        break;
                                    }
                                    case "MESSAGE_PHOTO_SECRET": {
                                        messageText = LocaleController.formatString("NotificationMessageSDPhoto", R.string.NotificationMessageSDPhoto, args[0]);
                                        message1 = LocaleController.getString("AttachDestructingPhoto", R.string.AttachDestructingPhoto);
                                        break;
                                    }
                                    case "MESSAGE_VIDEO": {
                                        messageText = LocaleController.formatString("NotificationMessageVideo", R.string.NotificationMessageVideo, args[0]);
                                        message1 = LocaleController.getString("AttachVideo", R.string.AttachVideo);
                                        break;
                                    }
                                    case "MESSAGE_VIDEO_SECRET": {
                                        messageText = LocaleController.formatString("NotificationMessageSDVideo", R.string.NotificationMessageSDVideo, args[0]);
                                        message1 = LocaleController.getString("AttachDestructingVideo", R.string.AttachDestructingVideo);
                                        break;
                                    }
                                    case "MESSAGE_SCREENSHOT": {
                                        messageText = LocaleController.getString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot).replace("un1", args[0]);
                                        break;
                                    }
                                    case "MESSAGE_ROUND": {
                                        messageText = LocaleController.formatString("NotificationMessageRound", R.string.NotificationMessageRound, args[0]);
                                        message1 = LocaleController.getString("AttachRound", R.string.AttachRound);
                                        break;
                                    }
                                    case "MESSAGE_DOC": {
                                        messageText = LocaleController.formatString("NotificationMessageDocument", R.string.NotificationMessageDocument, args[0]);
                                        message1 = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                                        break;
                                    }
                                    case "MESSAGE_STICKER": {
                                        if (args.length > 1 && !TextUtils.isEmpty(args[1])) {
                                            messageText = LocaleController.formatString("NotificationMessageStickerEmoji", R.string.NotificationMessageStickerEmoji, args[0], args[1]);
                                            message1 = args[1] + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationMessageSticker", R.string.NotificationMessageSticker, args[0]);
                                            message1 = LocaleController.getString("AttachSticker", R.string.AttachSticker);
                                        }
                                        break;
                                    }
                                    case "MESSAGE_AUDIO": {
                                        messageText = LocaleController.formatString("NotificationMessageAudio", R.string.NotificationMessageAudio, args[0]);
                                        message1 = LocaleController.getString("AttachAudio", R.string.AttachAudio);
                                        break;
                                    }
                                    case "MESSAGE_CONTACT": {
                                        messageText = LocaleController.formatString("NotificationMessageContact2", R.string.NotificationMessageContact2, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachContact", R.string.AttachContact);
                                        break;
                                    }
                                    case "MESSAGE_POLL": {
                                        messageText = LocaleController.formatString("NotificationMessagePoll2", R.string.NotificationMessagePoll2, args[0], args[1]);
                                        message1 = LocaleController.getString("Poll", R.string.Poll);
                                        break;
                                    }
                                    case "MESSAGE_GEO": {
                                        messageText = LocaleController.formatString("NotificationMessageMap", R.string.NotificationMessageMap, args[0]);
                                        message1 = LocaleController.getString("AttachLocation", R.string.AttachLocation);
                                        break;
                                    }
                                    case "MESSAGE_GEOLIVE": {
                                        messageText = LocaleController.formatString("NotificationMessageLiveLocation", R.string.NotificationMessageLiveLocation, args[0]);
                                        message1 = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                                        break;
                                    }
                                    case "MESSAGE_GIF": {
                                        messageText = LocaleController.formatString("NotificationMessageGif", R.string.NotificationMessageGif, args[0]);
                                        message1 = LocaleController.getString("AttachGif", R.string.AttachGif);
                                        break;
                                    }
                                    case "MESSAGE_GAME": {
                                        messageText = LocaleController.formatString("NotificationMessageGame", R.string.NotificationMessageGame, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachGame", R.string.AttachGame);
                                        break;
                                    }
                                    case "MESSAGE_GAME_SCORE":
                                    case "CHANNEL_MESSAGE_GAME_SCORE":{
                                        messageText = LocaleController.formatString("NotificationMessageGameScored", R.string.NotificationMessageGameScored, args[0], args[1], args[2]);
                                        break;
                                    }
                                    case "MESSAGE_INVOICE": {
                                        messageText = LocaleController.formatString("NotificationMessageInvoice", R.string.NotificationMessageInvoice, args[0], args[1]);
                                        message1 = LocaleController.getString("PaymentInvoice", R.string.PaymentInvoice);
                                        break;
                                    }
                                    case "MESSAGE_FWDS": {
                                        messageText = LocaleController.formatString("NotificationMessageForwardFew", R.string.NotificationMessageForwardFew, args[0], LocaleController.formatPluralString("messages", Utilities.parseInt(args[1])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "MESSAGE_PHOTOS": {
                                        messageText = LocaleController.formatString("NotificationMessageFew", R.string.NotificationMessageFew, args[0], LocaleController.formatPluralString("Photos", Utilities.parseInt(args[1])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "MESSAGE_VIDEOS": {
                                        messageText = LocaleController.formatString("NotificationMessageFew", R.string.NotificationMessageFew, args[0], LocaleController.formatPluralString("Videos", Utilities.parseInt(args[1])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "MESSAGES": {
                                        messageText = LocaleController.formatString("NotificationMessageAlbum", R.string.NotificationMessageAlbum, args[0]);
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_NOTEXT": {
                                        messageText = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, args[0]);
                                        message1 = LocaleController.getString("Message", R.string.Message);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_PHOTO": {
                                        messageText = LocaleController.formatString("ChannelMessagePhoto", R.string.ChannelMessagePhoto, args[0]);
                                        message1 = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_VIDEO": {
                                        messageText = LocaleController.formatString("ChannelMessageVideo", R.string.ChannelMessageVideo, args[0]);
                                        message1 = LocaleController.getString("AttachVideo", R.string.AttachVideo);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_ROUND": {
                                        messageText = LocaleController.formatString("ChannelMessageRound", R.string.ChannelMessageRound, args[0]);
                                        message1 = LocaleController.getString("AttachRound", R.string.AttachRound);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_DOC": {
                                        messageText = LocaleController.formatString("ChannelMessageDocument", R.string.ChannelMessageDocument, args[0]);
                                        message1 = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_STICKER": {
                                        if (args.length > 1 && !TextUtils.isEmpty(args[1])) {
                                            messageText = LocaleController.formatString("ChannelMessageStickerEmoji", R.string.ChannelMessageStickerEmoji, args[0], args[1]);
                                            message1 = args[1] + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker);
                                        } else {
                                            messageText = LocaleController.formatString("ChannelMessageSticker", R.string.ChannelMessageSticker, args[0]);
                                            message1 = LocaleController.getString("AttachSticker", R.string.AttachSticker);
                                        }
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_AUDIO": {
                                        messageText = LocaleController.formatString("ChannelMessageAudio", R.string.ChannelMessageAudio, args[0]);
                                        message1 = LocaleController.getString("AttachAudio", R.string.AttachAudio);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_CONTACT": {
                                        messageText = LocaleController.formatString("ChannelMessageContact2", R.string.ChannelMessageContact2, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachContact", R.string.AttachContact);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_POLL": {
                                        messageText = LocaleController.formatString("ChannelMessagePoll2", R.string.ChannelMessagePoll2, args[0], args[1]);
                                        message1 = LocaleController.getString("Poll", R.string.Poll);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_GEO": {
                                        messageText = LocaleController.formatString("ChannelMessageMap", R.string.ChannelMessageMap, args[0]);
                                        message1 = LocaleController.getString("AttachLocation", R.string.AttachLocation);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_GEOLIVE": {
                                        messageText = LocaleController.formatString("ChannelMessageLiveLocation", R.string.ChannelMessageLiveLocation, args[0]);
                                        message1 = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_GIF": {
                                        messageText = LocaleController.formatString("ChannelMessageGIF", R.string.ChannelMessageGIF, args[0]);
                                        message1 = LocaleController.getString("AttachGif", R.string.AttachGif);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_GAME": {
                                        messageText = LocaleController.formatString("NotificationMessageGame", R.string.NotificationMessageGame, args[0]);
                                        message1 = LocaleController.getString("AttachGame", R.string.AttachGame);
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_FWDS": {
                                        messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("ForwardedMessageCount", Utilities.parseInt(args[1])).toLowerCase());
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_PHOTOS": {
                                        messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("Photos", Utilities.parseInt(args[1])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHANNEL_MESSAGE_VIDEOS": {
                                        messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("Videos", Utilities.parseInt(args[1])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHANNEL_MESSAGES": {
                                        messageText = LocaleController.formatString("ChannelMessageAlbum", R.string.ChannelMessageAlbum, args[0]);
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHAT_MESSAGE_TEXT": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, args[0], args[1], args[2]);
                                        message1 = args[2];
                                        break;
                                    }
                                    case "CHAT_MESSAGE_NOTEXT": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, args[0], args[1]);
                                        message1 = LocaleController.getString("Message", R.string.Message);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_PHOTO": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupPhoto", R.string.NotificationMessageGroupPhoto, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_VIDEO": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupVideo", R.string.NotificationMessageGroupVideo, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachVideo", R.string.AttachVideo);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_ROUND": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupRound", R.string.NotificationMessageGroupRound, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachRound", R.string.AttachRound);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_DOC": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupDocument", R.string.NotificationMessageGroupDocument, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_STICKER": {
                                        if (args.length > 2 && !TextUtils.isEmpty(args[2])) {
                                            messageText = LocaleController.formatString("NotificationMessageGroupStickerEmoji", R.string.NotificationMessageGroupStickerEmoji, args[0], args[1], args[2]);
                                            message1 = args[2] + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationMessageGroupSticker", R.string.NotificationMessageGroupSticker, args[0], args[1]);
                                            message1 = args[1] + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker);
                                        }
                                        break;
                                    }
                                    case "CHAT_MESSAGE_AUDIO": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupAudio", R.string.NotificationMessageGroupAudio, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachAudio", R.string.AttachAudio);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_CONTACT": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupContact2", R.string.NotificationMessageGroupContact2, args[0], args[1], args[2]);
                                        message1 = LocaleController.getString("AttachContact", R.string.AttachContact);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_POLL": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupPoll2", R.string.NotificationMessageGroupPoll2, args[0], args[1], args[2]);
                                        message1 = LocaleController.getString("Poll", R.string.Poll);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_GEO": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachLocation", R.string.AttachLocation);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_GEOLIVE": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupLiveLocation", R.string.NotificationMessageGroupLiveLocation, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_GIF": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupGif", R.string.NotificationMessageGroupGif, args[0], args[1]);
                                        message1 = LocaleController.getString("AttachGif", R.string.AttachGif);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_GAME": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupGame", R.string.NotificationMessageGroupGame, args[0], args[1], args[2]);
                                        message1 = LocaleController.getString("AttachGame", R.string.AttachGame);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_GAME_SCORE": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupGameScored", R.string.NotificationMessageGroupGameScored, args[0], args[1], args[2], args[3]);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_INVOICE": {
                                        messageText = LocaleController.formatString("NotificationMessageGroupInvoice", R.string.NotificationMessageGroupInvoice, args[0], args[1], args[2]);
                                        message1 = LocaleController.getString("PaymentInvoice", R.string.PaymentInvoice);
                                        break;
                                    }
                                    case "CHAT_CREATED":
                                    case "CHAT_ADD_YOU": {
                                        messageText = LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_TITLE_EDITED": {
                                        messageText = LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_PHOTO_EDITED": {
                                        messageText = LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_ADD_MEMBER": {
                                        messageText = LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, args[0], args[1], args[2]);
                                        break;
                                    }
                                    case "CHAT_DELETE_MEMBER": {
                                        messageText = LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_DELETE_YOU": {
                                        messageText = LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_LEFT": {
                                        messageText = LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_RETURNED": {
                                        messageText = LocaleController.formatString("NotificationGroupAddSelf", R.string.NotificationGroupAddSelf, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_JOINED": {
                                        messageText = LocaleController.formatString("NotificationGroupAddSelfMega", R.string.NotificationGroupAddSelfMega, args[0], args[1]);
                                        break;
                                    }
                                    case "CHAT_MESSAGE_FWDS": {
                                        messageText = LocaleController.formatString("NotificationGroupForwardedFew", R.string.NotificationGroupForwardedFew, args[0], args[1], LocaleController.formatPluralString("messages", Utilities.parseInt(args[2])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHAT_MESSAGE_PHOTOS": {
                                        messageText = LocaleController.formatString("NotificationGroupFew", R.string.NotificationGroupFew, args[0], args[1], LocaleController.formatPluralString("Photos", Utilities.parseInt(args[2])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHAT_MESSAGE_VIDEOS": {
                                        messageText = LocaleController.formatString("NotificationGroupFew", R.string.NotificationGroupFew, args[0], args[1], LocaleController.formatPluralString("Videos", Utilities.parseInt(args[2])));
                                        localMessage = true;
                                        break;
                                    }
                                    case "CHAT_MESSAGES": {
                                        messageText = LocaleController.formatString("NotificationGroupAlbum", R.string.NotificationGroupAlbum, args[0], args[1]);
                                        localMessage = true;
                                        break;
                                    }
                                    case "PINNED_TEXT": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, args[0], args[1], args[2]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, args[0], args[1]);
                                        }
                                        break;
                                    }
                                    case "PINNED_NOTEXT": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_PHOTO": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedPhoto", R.string.NotificationActionPinnedPhoto, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedPhotoChannel", R.string.NotificationActionPinnedPhotoChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_VIDEO": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedVideo", R.string.NotificationActionPinnedVideo, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedVideoChannel", R.string.NotificationActionPinnedVideoChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_ROUND": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedRound", R.string.NotificationActionPinnedRound, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedRoundChannel", R.string.NotificationActionPinnedRoundChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_DOC": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedFile", R.string.NotificationActionPinnedFile, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedFileChannel", R.string.NotificationActionPinnedFileChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_STICKER": {
                                        if (chat_from_id != 0) {
                                            if (args.length > 2 && !TextUtils.isEmpty(args[2])) {
                                                messageText = LocaleController.formatString("NotificationActionPinnedStickerEmoji", R.string.NotificationActionPinnedStickerEmoji, args[0], args[1], args[2]);
                                            } else {
                                                messageText = LocaleController.formatString("NotificationActionPinnedSticker", R.string.NotificationActionPinnedSticker, args[0], args[1]);
                                            }
                                        } else {
                                            if (args.length > 1 && !TextUtils.isEmpty(args[1])) {
                                                messageText = LocaleController.formatString("NotificationActionPinnedStickerEmojiChannel", R.string.NotificationActionPinnedStickerEmojiChannel, args[0], args[1]);
                                            } else {
                                                messageText = LocaleController.formatString("NotificationActionPinnedStickerChannel", R.string.NotificationActionPinnedStickerChannel, args[0]);
                                            }
                                        }
                                        break;
                                    }
                                    case "PINNED_AUDIO": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedVoice", R.string.NotificationActionPinnedVoice, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedVoiceChannel", R.string.NotificationActionPinnedVoiceChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_CONTACT": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedContact2", R.string.NotificationActionPinnedContact2, args[0], args[1], args[2]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedContactChannel2", R.string.NotificationActionPinnedContactChannel2, args[0], args[1]);
                                        }
                                        break;
                                    }
                                    case "PINNED_POLL": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedPoll2", R.string.NotificationActionPinnedPoll2, args[0], args[1], args[2]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedPollChannel2", R.string.NotificationActionPinnedPollChannel2, args[0], args[1]);
                                        }
                                        break;
                                    }
                                    case "PINNED_GEO": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGeo", R.string.NotificationActionPinnedGeo, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGeoChannel", R.string.NotificationActionPinnedGeoChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_GEOLIVE": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGeoLive", R.string.NotificationActionPinnedGeoLive, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGeoLiveChannel", R.string.NotificationActionPinnedGeoLiveChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_GAME": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGame", R.string.NotificationActionPinnedGame, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGameChannel", R.string.NotificationActionPinnedGameChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_GAME_SCORE": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGameScore", R.string.NotificationActionPinnedGameScore, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGameScoreChannel", R.string.NotificationActionPinnedGameScoreChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_INVOICE": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedInvoice", R.string.NotificationActionPinnedInvoice, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedInvoiceChannel", R.string.NotificationActionPinnedInvoiceChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "PINNED_GIF": {
                                        if (chat_from_id != 0) {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGif", R.string.NotificationActionPinnedGif, args[0], args[1]);
                                        } else {
                                            messageText = LocaleController.formatString("NotificationActionPinnedGifChannel", R.string.NotificationActionPinnedGifChannel, args[0]);
                                        }
                                        break;
                                    }
                                    case "ENCRYPTED_MESSAGE": {
                                        messageText = LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
                                        name = LocaleController.getString("SecretChatName", R.string.SecretChatName);
                                        localMessage = true;
                                        break;
                                    }
                                    case "CONTACT_JOINED":
                                    case "AUTH_UNKNOWN":
                                    case "AUTH_REGION":
                                    case "LOCKED_MESSAGE":
                                    case "ENCRYPTION_REQUEST":
                                    case "ENCRYPTION_ACCEPT":
                                    case "PHONE_CALL_REQUEST":
                                    case "MESSAGE_MUTED":
                                    case "PHONE_CALL_MISSED": {
                                        //ignored
                                        break;
                                    }
                                    default: {
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.w("unhandled loc_key = " + loc_key);
                                        }
                                        break;
                                    }
                                }
                                if (messageText != null) {
                                    TLRPC.TL_message messageOwner = new TLRPC.TL_message();
                                    messageOwner.id = msg_id;
                                    messageOwner.random_id = random_id;
                                    messageOwner.message = message1 != null ? message1 : messageText;
                                    messageOwner.date = (int) (time / 1000);
                                    if (pinned) {
                                        messageOwner.action = new TLRPC.TL_messageActionPinMessage();
                                    }
                                    if (supergroup) {
                                        messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                    }
                                    messageOwner.dialog_id = dialog_id;
                                    if (channel_id != 0) {
                                        messageOwner.to_id = new TLRPC.TL_peerChannel();
                                        messageOwner.to_id.channel_id = channel_id;
                                    } else if (chat_id != 0) {
                                        messageOwner.to_id = new TLRPC.TL_peerChat();
                                        messageOwner.to_id.chat_id = chat_id;
                                    } else {
                                        messageOwner.to_id = new TLRPC.TL_peerUser();
                                        messageOwner.to_id.user_id = user_id;
                                    }
                                    messageOwner.flags |= 256;
                                    messageOwner.from_id = chat_from_id;
                                    messageOwner.mentioned = mention || pinned;
                                    messageOwner.silent = silent;
                                    messageOwner.from_scheduled = scheduled;

                                    MessageObject messageObject = new MessageObject(currentAccount, messageOwner, messageText, name, userName, localMessage, channel, edited);
                                    ArrayList<MessageObject> arrayList = new ArrayList<>();
                                    arrayList.add(messageObject);//TODO
                                    NotificationsController.getInstance(currentAccount).processNewMessages(arrayList, true, true, countDownLatch);
                                } else {
                                    countDownLatch.countDown();
                                }
                            } else {
                                countDownLatch.countDown();
                            }
                        }
                    }

                    ConnectionsManager.onInternalPushReceived(currentAccount);
                    ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe();
                } catch (Throwable e) {
                    if (currentAccount != -1) {
                        ConnectionsManager.onInternalPushReceived(currentAccount);
                        ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe();
                        countDownLatch.countDown();
                    } else {
                        onDecryptError();
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("error in loc_key = " + loc_key + " json " + jsonString);
                    }
                    FileLog.e(e);
                }
            });
        });
        try {
            countDownLatch.await();
        } catch (Throwable ignore) {

        }
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("finished GCM service, time = " + (SystemClock.uptimeMillis() - receiveTime));
        }
    }

    private void onDecryptError() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                ConnectionsManager.onInternalPushReceived(a);
                ConnectionsManager.getInstance(a).resumeNetworkMaybe();
            }
        }
        countDownLatch.countDown();
    }

    @Override
    public void onNewToken(String token) {
        AndroidUtilities.runOnUIThread(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Refreshed token: " + token);
            }
            ApplicationLoader.postInitApplication();
            sendRegistrationToServer(token);
        });
    }

    public static void sendRegistrationToServer(final String token) {
        Utilities.stageQueue.postRunnable(() -> {
            ConnectionsManager.setRegId(token, SharedConfig.pushStringStatus);
            if (token == null) {
                return;
            }
            SharedConfig.pushString = token;
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = UserConfig.getInstance(a);
                userConfig.registeredForPush = false;
                userConfig.saveConfig(false);
                if (userConfig.getClientUserId() != 0) {
                    final int currentAccount = a;
                    AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(currentAccount).registerForPush(token));
                }
            }
        });
    }
}
