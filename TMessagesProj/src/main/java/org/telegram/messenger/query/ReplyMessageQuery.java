/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.query;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ReplyMessageQuery {

    public static void loadReplyMessagesForMessages(final ArrayList<MessageObject> messages, final long dialog_id) {
        final ArrayList<Integer> replyMessages = new ArrayList<>();
        final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners = new HashMap<>();
        final StringBuilder stringBuilder = new StringBuilder();
        int channelId = 0;
        for (MessageObject messageObject : messages) {
            if (messageObject.getId() > 0 && messageObject.isReply() && messageObject.replyMessageObject == null) {
                Integer id = messageObject.messageOwner.reply_to_msg_id;
                long messageId = id;
                if (messageObject.messageOwner.to_id.channel_id != 0) {
                    messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
                    channelId = messageObject.messageOwner.to_id.channel_id;
                }
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(',');
                }
                stringBuilder.append(messageId);
                ArrayList<MessageObject> messageObjects = replyMessageOwners.get(id);
                if (messageObjects == null) {
                    messageObjects = new ArrayList<>();
                    replyMessageOwners.put(id, messageObjects);
                }
                messageObjects.add(messageObject);
                if (!replyMessages.contains(id)) {
                    replyMessages.add(id);
                }
            }
        }
        if (replyMessages.isEmpty()) {
            return;
        }

        final int channelIdFinal = channelId;
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList<TLRPC.Message> result = new ArrayList<>();
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    ArrayList<Integer> usersToLoad = new ArrayList<>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<>();

                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages WHERE mid IN(%s)", stringBuilder.toString()));
                    while (cursor.next()) {
                        NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                        if (data != null && cursor.byteBufferValue(0, data) != 0) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = dialog_id;
                            MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                            result.add(message);
                            replyMessages.remove((Integer) message.id);
                        }
                        data.reuse();
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    broadcastReplyMessages(result, replyMessageOwners, users, chats, dialog_id, true);

                    if (!replyMessages.isEmpty()) {
                        if (channelIdFinal != 0) {
                            final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                            req.channel = MessagesController.getInputChannel(channelIdFinal);
                            req.id = replyMessages;
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                    if (error == null) {
                                        TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                        ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                        broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialog_id, false);
                                        MessagesStorage.getInstance().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                        saveReplyMessages(replyMessageOwners, messagesRes.messages);
                                    }
                                }
                            });
                        } else {
                            TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                            req.id = replyMessages;
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                    if (error == null) {
                                        TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                        ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                        broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialog_id, false);
                                        MessagesStorage.getInstance().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                        saveReplyMessages(replyMessageOwners, messagesRes.messages);
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private static void saveReplyMessages(final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners, final ArrayList<TLRPC.Message> result) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().beginTransaction();
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("UPDATE messages SET replydata = ? WHERE mid = ?");
                    for (TLRPC.Message message : result) {
                        ArrayList<MessageObject> messageObjects = replyMessageOwners.get(message.id);
                        if (messageObjects != null) {
                            NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                            for (MessageObject messageObject : messageObjects) {
                                state.requery();
                                long messageId = messageObject.getId();
                                if (messageObject.messageOwner.to_id.channel_id != 0) {
                                    messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
                                }
                                state.bindByteBuffer(1, data);
                                state.bindLong(2, messageId);
                                state.step();
                            }
                            data.reuse();
                        }
                    }
                    state.dispose();
                    MessagesStorage.getInstance().getDatabase().commitTransaction();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private static void broadcastReplyMessages(final ArrayList<TLRPC.Message> result, final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final long dialog_id, final boolean isCache) {
        final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();
        for (int a = 0; a < users.size(); a++) {
            TLRPC.User user = users.get(a);
            usersDict.put(user.id, user);
        }
        final HashMap<Integer, TLRPC.Chat> chatsDict = new HashMap<>();
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            chatsDict.put(chat.id, chat);
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().putUsers(users, isCache);
                MessagesController.getInstance().putChats(chats, isCache);
                boolean changed = false;
                for (int a = 0; a < result.size(); a++) {
                    TLRPC.Message message = result.get(a);
                    ArrayList<MessageObject> arrayList = replyMessageOwners.get(message.id);
                    if (arrayList != null) {
                        MessageObject messageObject = new MessageObject(message, usersDict, chatsDict, false);
                        for (int b = 0; b < arrayList.size(); b++) {
                            MessageObject m = arrayList.get(b);
                            m.replyMessageObject = messageObject;
                        }
                        changed = true;
                    }
                }
                if (changed) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didLoadedReplyMessages, dialog_id);
                }
            }
        });
    }
}
