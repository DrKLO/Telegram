/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.query;

import android.text.Spannable;
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
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanUserMention;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class MessagesQuery {

    private static Comparator<TLRPC.MessageEntity> entityComparator = new Comparator<TLRPC.MessageEntity>() {
        @Override
        public int compare(TLRPC.MessageEntity entity1, TLRPC.MessageEntity entity2) {
            if (entity1.offset > entity2.offset) {
                return 1;
            } else if (entity1.offset < entity2.offset) {
                return -1;
            }
            return 0;
        }
    };

    public static MessageObject loadPinnedMessage(final int channelId, final int mid, boolean useQueue) {
        if (useQueue) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    loadPinnedMessageInternal(channelId, mid, false);
                }
            });
        } else {
            return loadPinnedMessageInternal(channelId, mid, true);
        }
        return null;
    }

    private static MessageObject loadPinnedMessageInternal(final int channelId, final int mid, boolean returnValue) {
        try {
            long messageId = ((long) mid) | ((long) channelId) << 32;

            TLRPC.Message result = null;
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<Integer> usersToLoad = new ArrayList<>();
            ArrayList<Integer> chatsToLoad = new ArrayList<>();

            SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages WHERE mid = %d", messageId));
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    result = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (result.action instanceof TLRPC.TL_messageActionHistoryClear) {
                        result = null;
                    } else {
                        result.id = cursor.intValue(1);
                        result.date = cursor.intValue(2);
                        result.dialog_id = -channelId;
                        MessagesStorage.addUsersAndChatsFromMessage(result, usersToLoad, chatsToLoad);
                    }
                }
            }
            cursor.dispose();

            if (result == null) {
                cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM chat_pinned WHERE uid = %d", channelId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        result = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (result.id != mid || result.action instanceof TLRPC.TL_messageActionHistoryClear) {
                            result = null;
                        } else {
                            result.dialog_id = -channelId;
                            MessagesStorage.addUsersAndChatsFromMessage(result, usersToLoad, chatsToLoad);
                        }
                    }
                }
                cursor.dispose();
            }

            if (result == null) {
                final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                req.channel = MessagesController.getInputChannel(channelId);
                req.id.add(mid);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        boolean ok = false;
                        if (error == null) {
                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                            removeEmptyMessages(messagesRes.messages);
                            if (!messagesRes.messages.isEmpty()) {
                                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                broadcastPinnedMessage(messagesRes.messages.get(0), messagesRes.users, messagesRes.chats, false, false);
                                MessagesStorage.getInstance().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                savePinnedMessage(messagesRes.messages.get(0));
                                ok = true;
                            }
                        }
                        if (!ok) {
                            MessagesStorage.getInstance().updateChannelPinnedMessage(channelId, 0);
                        }
                    }
                });
            } else {
                if (returnValue) {
                    return broadcastPinnedMessage(result, users, chats, true, returnValue);
                } else {
                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    broadcastPinnedMessage(result, users, chats, true, false);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static void savePinnedMessage(final TLRPC.Message result) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().beginTransaction();
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO chat_pinned VALUES(?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(result.getObjectSize());
                    result.serializeToStream(data);
                    state.requery();
                    state.bindInteger(1, result.to_id.channel_id);
                    state.bindInteger(2, result.id);
                    state.bindByteBuffer(3, data);
                    state.step();
                    data.reuse();
                    state.dispose();
                    MessagesStorage.getInstance().getDatabase().commitTransaction();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private static MessageObject broadcastPinnedMessage(final TLRPC.Message result, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final boolean isCache, boolean returnValue) {
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
        if (returnValue) {
            return new MessageObject(result, usersDict, chatsDict, false);
        } else {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    MessagesController.getInstance().putUsers(users, isCache);
                    MessagesController.getInstance().putChats(chats, isCache);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didLoadedPinnedMessage, new MessageObject(result, usersDict, chatsDict, false));
                }
            });
        }
        return null;
    }

    private static void removeEmptyMessages(ArrayList<TLRPC.Message> messages) {
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message == null || message instanceof TLRPC.TL_messageEmpty || message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                messages.remove(a);
                a--;
            }
        }
    }

    public static void loadReplyMessagesForMessages(final ArrayList<MessageObject> messages, final long dialogId) {
        if ((int) dialogId == 0) {
            final ArrayList<Long> replyMessages = new ArrayList<>();
            final HashMap<Long, ArrayList<MessageObject>> replyMessageRandomOwners = new HashMap<>();
            final StringBuilder stringBuilder = new StringBuilder();
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
                if (messageObject.isReply() && messageObject.replyMessageObject == null) {
                    Long id = messageObject.messageOwner.reply_to_random_id;
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append(',');
                    }
                    stringBuilder.append(id);
                    ArrayList<MessageObject> messageObjects = replyMessageRandomOwners.get(id);
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                        replyMessageRandomOwners.put(id, messageObjects);
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
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, m.date, r.random_id FROM randoms as r INNER JOIN messages as m ON r.mid = m.mid WHERE r.random_id IN(%s)", TextUtils.join(",", replyMessages)));
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = dialogId;

                                ArrayList<MessageObject> arrayList = replyMessageRandomOwners.remove(cursor.longValue(3));
                                if (arrayList != null) {
                                    MessageObject messageObject = new MessageObject(message, null, null, false);
                                    for (int b = 0; b < arrayList.size(); b++) {
                                        MessageObject object = arrayList.get(b);
                                        object.replyMessageObject = messageObject;
                                        object.messageOwner.reply_to_msg_id = messageObject.getId();
                                        if (object.isMegagroup()) {
                                            object.replyMessageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                }
                            }
                        }
                        cursor.dispose();
                        if (!replyMessageRandomOwners.isEmpty()) {
                            for (HashMap.Entry<Long, ArrayList<MessageObject>> entry : replyMessageRandomOwners.entrySet()) {
                                ArrayList<MessageObject> arrayList = entry.getValue();
                                for (int a = 0; a < arrayList.size(); a++) {
                                    arrayList.get(a).messageOwner.reply_to_random_id = 0;
                                }
                            }
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.didLoadedReplyMessages, dialogId);
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        } else {
            final ArrayList<Integer> replyMessages = new ArrayList<>();
            final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners = new HashMap<>();
            final StringBuilder stringBuilder = new StringBuilder();
            int channelId = 0;
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
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
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = dialogId;
                                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                                result.add(message);
                                replyMessages.remove((Integer) message.id);
                            }
                        }
                        cursor.dispose();

                        if (!usersToLoad.isEmpty()) {
                            MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        }
                        if (!chatsToLoad.isEmpty()) {
                            MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        }
                        broadcastReplyMessages(result, replyMessageOwners, users, chats, dialogId, true);

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
                                            removeEmptyMessages(messagesRes.messages);
                                            ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                            broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
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
                                            removeEmptyMessages(messagesRes.messages);
                                            ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                            broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
                                            MessagesStorage.getInstance().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                            saveReplyMessages(replyMessageOwners, messagesRes.messages);
                                        }
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }
    }

    private static void saveReplyMessages(final HashMap<Integer, ArrayList<MessageObject>> replyMessageOwners, final ArrayList<TLRPC.Message> result) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().beginTransaction();
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("UPDATE messages SET replydata = ? WHERE mid = ?");
                    for (int a = 0; a < result.size(); a++) {
                        TLRPC.Message message = result.get(a);
                        ArrayList<MessageObject> messageObjects = replyMessageOwners.get(message.id);
                        if (messageObjects != null) {
                            NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                            for (int b = 0; b < messageObjects.size(); b++) {
                                MessageObject messageObject = messageObjects.get(b);
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
                    FileLog.e(e);
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
                            if (m.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                                m.generatePinMessageText(null, null);
                            } else if (m.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                                m.generateGameMessageText(null);
                            } else if (m.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                                m.generatePaymentSentMessageText(null);
                            }
                            if (m.isMegagroup()) {
                                m.replyMessageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                            }
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

    public static void sortEntities(ArrayList<TLRPC.MessageEntity> entities) {
        Collections.sort(entities, entityComparator);
    }

    private static boolean checkInclusion(int index, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset <= index && entity.offset + entity.length > index) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIntersection(int start, int end, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > start && entity.offset + entity.length <= end) {
                return true;
            }
        }
        return false;
    }

    private static void removeOffsetAfter(int start, int countToRemove, ArrayList<TLRPC.MessageEntity> entities) {
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > start) {
                entity.offset -= countToRemove;
            }
        }
    }

    public static ArrayList<TLRPC.MessageEntity> getEntities(CharSequence[] message) {
        if (message == null || message[0] == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> entities = null;
        int index;
        int start = -1;
        int lastIndex = 0;
        boolean isPre = false;
        final String mono = "`";
        final String pre = "```";
        final String bold = "**";
        final String italic = "__";
        while ((index = TextUtils.indexOf(message[0], !isPre ? mono : pre, lastIndex)) != -1) {
            if (start == -1) {
                isPre = message[0].length() - index > 2 && message[0].charAt(index + 1) == '`' && message[0].charAt(index + 2) == '`';
                start = index;
                lastIndex = index + (isPre ? 3 : 1);
            } else {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int a = index + (isPre ? 3 : 1); a < message[0].length(); a++) {
                    if (message[0].charAt(a) == '`') {
                        index++;
                    } else {
                        break;
                    }
                }
                lastIndex = index + (isPre ? 3 : 1);
                if (isPre) {
                    int firstChar = start > 0 ? message[0].charAt(start - 1) : 0;
                    boolean replacedFirst = firstChar == ' ' || firstChar == '\n';
                    CharSequence startMessage = TextUtils.substring(message[0], 0, start - (replacedFirst ? 1 : 0));
                    CharSequence content = TextUtils.substring(message[0], start + 3, index);
                    firstChar = index + 3 < message[0].length() ? message[0].charAt(index + 3) : 0;
                    CharSequence endMessage = TextUtils.substring(message[0], index + 3 + (firstChar == ' ' || firstChar == '\n' ? 1 : 0), message[0].length());
                    if (startMessage.length() != 0) {
                        startMessage = TextUtils.concat(startMessage, "\n");
                    } else {
                        replacedFirst = true;
                    }
                    if (endMessage.length() != 0) {
                        endMessage = TextUtils.concat("\n", endMessage);
                    }
                    if (!TextUtils.isEmpty(content)) {
                        message[0] = TextUtils.concat(startMessage, content, endMessage);
                        TLRPC.TL_messageEntityPre entity = new TLRPC.TL_messageEntityPre();
                        entity.offset = start + (replacedFirst ? 0 : 1);
                        entity.length = index - start - 3 + (replacedFirst ? 0 : 1);
                        entity.language = "";
                        entities.add(entity);
                        lastIndex -= 6;
                    }
                } else {
                    if (start + 1 != index) {
                        message[0] = TextUtils.concat(TextUtils.substring(message[0], 0, start), TextUtils.substring(message[0], start + 1, index), TextUtils.substring(message[0], index + 1, message[0].length()));
                        TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
                        entity.offset = start;
                        entity.length = index - start - 1;
                        entities.add(entity);
                        lastIndex -= 2;
                    }
                }
                start = -1;
                isPre = false;
            }
        }
        if (start != -1 && isPre) {
            message[0] = TextUtils.concat(TextUtils.substring(message[0], 0, start), TextUtils.substring(message[0], start + 2, message[0].length()));
            if (entities == null) {
                entities = new ArrayList<>();
            }
            TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
            entity.offset = start;
            entity.length = 1;
            entities.add(entity);
        }

        if (message[0] instanceof Spannable) {
            Spannable spannable = (Spannable) message[0];
            TypefaceSpan spans[] = spannable.getSpans(0, message[0].length(), TypefaceSpan.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    TypefaceSpan span = spans[a];
                    int spanStart = spannable.getSpanStart(span);
                    int spanEnd = spannable.getSpanEnd(span);
                    if (checkInclusion(spanStart, entities) || checkInclusion(spanEnd, entities) || checkIntersection(spanStart, spanEnd, entities)) {
                        continue;
                    }
                    if (entities == null) {
                        entities = new ArrayList<>();
                    }
                    TLRPC.MessageEntity entity;
                    if (span.isBold()) {
                        entity = new TLRPC.TL_messageEntityBold();
                    } else {
                        entity = new TLRPC.TL_messageEntityItalic();
                    }
                    entity.offset = spanStart;
                    entity.length = spanEnd - spanStart;
                    entities.add(entity);
                }
            }

            URLSpanUserMention spansMentions[] = spannable.getSpans(0, message[0].length(), URLSpanUserMention.class);
            if (spansMentions != null && spansMentions.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansMentions.length; b++) {
                    TLRPC.TL_inputMessageEntityMentionName entity = new TLRPC.TL_inputMessageEntityMentionName();
                    entity.user_id = MessagesController.getInputUser(Utilities.parseInt(spansMentions[b].getURL()));
                    if (entity.user_id != null) {
                        entity.offset = spannable.getSpanStart(spansMentions[b]);
                        entity.length = Math.min(spannable.getSpanEnd(spansMentions[b]), message[0].length()) - entity.offset;
                        if (message[0].charAt(entity.offset + entity.length - 1) == ' ') {
                            entity.length--;
                        }
                        entities.add(entity);
                    }
                }
            }
        }

        for (int c = 0; c < 2; c++) {
            lastIndex = 0;
            start = -1;
            String checkString = c == 0 ? bold : italic;
            char checkChar = c == 0 ? '*' : '_';
            while ((index = TextUtils.indexOf(message[0], checkString, lastIndex)) != -1) {
                if (start == -1) {
                    char prevChar = index == 0 ? ' ' : message[0].charAt(index - 1);
                    if (!checkInclusion(index, entities) && (prevChar == ' ' || prevChar == '\n')) {
                        start = index;
                    }
                    lastIndex = index + 2;
                } else {
                    for (int a = index + 2; a < message[0].length(); a++) {
                        if (message[0].charAt(a) == checkChar) {
                            index++;
                        } else {
                            break;
                        }
                    }
                    lastIndex = index + 2;
                    if (checkInclusion(index, entities) || checkIntersection(start, index, entities)) {
                        start = -1;
                        continue;
                    }
                    if (start + 2 != index) {
                        if (entities == null) {
                            entities = new ArrayList<>();
                        }
                        message[0] = TextUtils.concat(TextUtils.substring(message[0], 0, start), TextUtils.substring(message[0], start + 2, index), TextUtils.substring(message[0], index + 2, message[0].length()));
                        TLRPC.MessageEntity entity;
                        if (c == 0) {
                            entity = new TLRPC.TL_messageEntityBold();
                        } else {
                            entity = new TLRPC.TL_messageEntityItalic();
                        }
                        entity.offset = start;
                        entity.length = index - start - 2;
                        removeOffsetAfter(entity.offset + entity.length, 4, entities);
                        entities.add(entity);
                        lastIndex -= 4;
                    }
                    start = -1;
                }
            }
        }

        return entities;
    }
}
