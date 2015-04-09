/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.android.query;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLClassStore;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ReplyMessageQuery {

    public static void loadReplyMessagesForMessages(final ArrayList<MessageObject> messages, final long dialog_id) {
        final ArrayList<Integer> replyMessages = new ArrayList<>();
        final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners = new HashMap<>();
        for (MessageObject messageObject : messages) {
            if (messageObject.getId() > 0 && messageObject.isReply() && messageObject.replyMessageObject == null) {
                Integer id = messageObject.messageOwner.reply_to_msg_id;
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

        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList<TLRPC.Message> result = new ArrayList<>();
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    ArrayList<Integer> loadedUsers = new ArrayList<>();
                    ArrayList<Integer> fromUser = new ArrayList<>();

                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages WHERE mid IN(%s)", TextUtils.join(",", replyMessages)));
                    while (cursor.next()) {
                        ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                        if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                            TLRPC.Message message = (TLRPC.Message) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = dialog_id;
                            fromUser.add(message.from_id);
                            if (message.action != null && message.action.user_id != 0) {
                                fromUser.add(message.action.user_id);
                            }
                            if (message.media != null && message.media.user_id != 0) {
                                fromUser.add(message.media.user_id);
                            }
                            if (message.media != null && message.media.audio != null && message.media.audio.user_id != 0) {
                                fromUser.add(message.media.audio.user_id);
                            }
                            if (message.fwd_from_id != 0) {
                                fromUser.add(message.fwd_from_id);
                            }
                            result.add(message);
                            replyMessages.remove((Integer) message.id);
                        }
                        MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                    }
                    cursor.dispose();

                    StringBuilder usersToLoad = new StringBuilder();
                    for (int uid : fromUser) {
                        if (!loadedUsers.contains(uid)) {
                            if (usersToLoad.length() != 0) {
                                usersToLoad.append(",");
                            }
                            usersToLoad.append(uid);
                            loadedUsers.add(uid);
                        }
                    }
                    if (usersToLoad.length() != 0) {
                        MessagesStorage.getInstance().getUsersInternal(usersToLoad.toString(), users);
                    }
                    broadcastReplyMessages(result, replyMessageOwners, users, dialog_id);

                    if (!replyMessages.isEmpty()) {
                        TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                        req.id = replyMessages;
                        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                            @Override
                            public void run(TLObject response, TLRPC.TL_error error) {
                                if (error == null) {
                                    TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                    ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                    broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, dialog_id);
                                    MessagesStorage.getInstance().putUsersAndChats(messagesRes.users, null, true, true);
                                    saveReplyMessages(replyMessageOwners, messagesRes.messages);
                                }
                            }
                        });
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
                            ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                            for (MessageObject messageObject : messageObjects) {
                                state.requery();
                                state.bindByteBuffer(1, data.buffer);
                                state.bindInteger(2, messageObject.getId());
                                state.step();
                            }
                            MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
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

    private static void broadcastReplyMessages(final ArrayList<TLRPC.Message> result, final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners, ArrayList<TLRPC.User> users, final long dialog_id) {
        final HashMap<Integer, TLRPC.User> usersHashMap = new HashMap<>();
        for (TLRPC.User user : users) {
            usersHashMap.put(user.id, user);
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                boolean changed = false;
                for (TLRPC.Message message : result) {
                    ArrayList<MessageObject> arrayList = replyMessageOwners.get(message.id);
                    if (arrayList != null) {
                        MessageObject messageObject = new MessageObject(message, usersHashMap, false);
                        for (MessageObject m : arrayList) {
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
