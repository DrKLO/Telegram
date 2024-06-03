package org.telegram.ui.Business;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class QuickRepliesController {

    public static final String GREETING = "hello";
    public static final String AWAY = "away";
    public static boolean isSpecial(String name) {
        return GREETING.equalsIgnoreCase(name) || AWAY.equalsIgnoreCase(name);
    }

    private static volatile QuickRepliesController[] Instance = new QuickRepliesController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }
    public static QuickRepliesController getInstance(int num) {
        QuickRepliesController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new QuickRepliesController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;
    private QuickRepliesController(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public class QuickReply {
        public int id;
        public String name;
        public int order;
        public int topMessageId;
        public MessageObject topMessage;
        public int messagesCount;
        public boolean local;
        public HashSet<Integer> localIds = new HashSet<>();
        public int getTopMessageId() {
            if (topMessage != null) return topMessage.getId();
            return topMessageId;
        }
        public int getMessagesCount() {
            if (local) return localIds.size();
            return messagesCount;
        }
        public boolean isSpecial() {
            return QuickRepliesController.isSpecial(name);
        }
    }

    public boolean canAddNew() {
        boolean containsGreeting = false;
        boolean containsAway = false;
        for (int i = 0; i < replies.size(); ++i) {
            containsGreeting = containsGreeting || GREETING.equalsIgnoreCase(replies.get(i).name);
            containsAway = containsAway || AWAY.equalsIgnoreCase(replies.get(i).name);
            if (containsGreeting && containsAway) break;
        }
        final int currentCount = replies.size() + (containsGreeting ? 0 : 1) + (containsAway ? 0 : 1);
        return currentCount < MessagesController.getInstance(currentAccount).quickRepliesLimit;
    }

    public final ArrayList<QuickReply> replies = new ArrayList<>();
    public final ArrayList<QuickReply> localReplies = new ArrayList<>();

    private ArrayList<QuickReply> filtered = new ArrayList<>();
    public ArrayList<QuickReply> getFilteredReplies() {
        filtered.clear();
        for (int i = 0; i < replies.size(); ++i) {
            if (!replies.get(i).isSpecial()) {
                filtered.add(replies.get(i));
            }
        }
        return filtered;
    }

    private boolean loading;
    private boolean loaded;
    public void load() {
//        if (!UserConfig.getInstance(currentAccount).isPremium()) return;
        load(true, null);
    }
    private void load(boolean cache) {
        load(cache, null);
    }
    private void load(boolean cache, Runnable whenLoaded) {
        if (loading || loaded) return;
        loading = true;
        if (cache) {
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            storage.getStorageQueue().postRunnable(() -> {
                final ArrayList<QuickReply> result = new ArrayList<>();
                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<TLRPC.Chat> chats = new ArrayList<>();

                SQLiteCursor cursor = null;
                try {
                    SQLiteDatabase db = storage.getDatabase();
                    cursor = db.queryFinalized("SELECT topic_id, name, order_value, count FROM business_replies ORDER BY order_value ASC");
                    while (cursor.next()) {
                        QuickReply reply = new QuickReply();
                        reply.id = cursor.intValue(0);
                        reply.name = cursor.stringValue(1);
                        reply.order = cursor.intValue(2);
                        reply.messagesCount = cursor.intValue(3);
                        result.add(reply);
                    }
                    cursor.dispose();

                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();

                    for (int i = 0; i < result.size(); ++i) {
                        QuickReply reply = result.get(i);
                        cursor = db.queryFinalized("SELECT data, send_state, mid, date, topic_id, ttl FROM quick_replies_messages WHERE topic_id = ? ORDER BY mid ASC", reply.id);
                        if (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.send_state = cursor.intValue(1);
                                message.readAttachPath(data, selfId);
                                data.reuse();
                                message.id = cursor.intValue(2);
                                message.date = cursor.intValue(3);
                                message.flags |= 1073741824;
                                message.quick_reply_shortcut_id = cursor.intValue(4);
                                message.ttl = cursor.intValue(5);
                                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                                reply.topMessage = new MessageObject(currentAccount, message, false, true);
                                reply.topMessageId = message.id;
                                reply.topMessage.generateThumbs(false);
                                reply.topMessage.applyQuickReply(reply.name, reply.id);
                            }
                        }
                        cursor.dispose();
                    }

                    if (!chatsToLoad.isEmpty()) {
                        storage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    if (!usersToLoad.isEmpty()) {
                        storage.getUsersInternal(usersToLoad, users);
                    }

                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    MessagesController.getInstance(currentAccount).putUsers(users, true);
                    MessagesController.getInstance(currentAccount).putChats(chats, true);
                    replies.clear();
                    replies.addAll(result);
                    if (whenLoaded != null) {
                        whenLoaded.run();
                    } else {
                        load(false);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                });
            });
        } else {
            TLRPC.TL_messages_getQuickReplies req = new TLRPC.TL_messages_getQuickReplies();
            req.hash = 0;
            for (int i = 0; i < replies.size(); ++i) {
                QuickReply reply = replies.get(i);

                req.hash = MediaDataController.calcHash(req.hash, reply.id);
                req.hash = MediaDataController.calcHash(req.hash, reply.name == null ? 0 : Long.parseUnsignedLong(Utilities.MD5(reply.name).substring(0, 16), 16));
                req.hash = MediaDataController.calcHash(req.hash, reply.topMessage == null ? 0 : reply.topMessage.getId());
                if (reply.topMessage != null && reply.topMessage.messageOwner != null && (reply.topMessage.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0) {
                    req.hash = MediaDataController.calcHash(req.hash, reply.topMessage.messageOwner.edit_date);
                } else {
                    req.hash = MediaDataController.calcHash(req.hash, 0);
                }
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, ((res, err) -> AndroidUtilities.runOnUIThread(() -> {
                ArrayList<QuickReply> result = null;
                if (res instanceof TLRPC.TL_messages_quickReplies) {
                    TLRPC.TL_messages_quickReplies quickReplies = (TLRPC.TL_messages_quickReplies) res;
                    MessagesController.getInstance(currentAccount).putUsers(quickReplies.users, false);
                    MessagesController.getInstance(currentAccount).putChats(quickReplies.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(quickReplies.users, quickReplies.chats, true, true);

                    result = new ArrayList<>();
                    for (int i = 0; i < quickReplies.quick_replies.size(); ++i) {
                        TLRPC.TL_quickReply tlreply = quickReplies.quick_replies.get(i);
                        QuickReply quickReply = new QuickReply();
                        quickReply.id = tlreply.shortcut_id;
                        quickReply.name = tlreply.shortcut;
                        quickReply.messagesCount = tlreply.count;
                        quickReply.topMessageId = tlreply.top_message;
                        quickReply.order = i;

                        TLRPC.Message message = null;
                        for (int j = 0; j < quickReplies.messages.size(); ++j) {
                            TLRPC.Message m = quickReplies.messages.get(j);
                            if (m.id == tlreply.top_message) {
                                message = m;
                                break;
                            }
                        }

                        if (message != null) {
                            quickReply.topMessage = new MessageObject(currentAccount, message, false, true);
                            quickReply.topMessage.generateThumbs(false);
                            quickReply.topMessage.applyQuickReply(tlreply.shortcut, tlreply.shortcut_id);
                        }

                        result.add(quickReply);
                    }
                } else if (res instanceof TLRPC.TL_messages_quickRepliesNotModified) {

                }
                loading = false;
                if (result != null) {
                    replies.clear();
                    replies.addAll(result);
                }
                loaded = true;
                saveToCache();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
            })));
        }
    }

    private void ensureLoaded(Runnable done) {
        if (loaded) {
            done.run();
        } else {
            load(true, done);
        }
    }

    private void saveToCache() {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase db = storage.getDatabase();
                db.executeFast("DELETE FROM business_replies").stepThis().dispose();
                state = db.executeFast("REPLACE INTO business_replies VALUES(?, ?, ?, ?)");
                for (int i = 0; i < replies.size(); ++i) {
                    QuickReply quickReply = replies.get(i);
                    state.requery();
                    state.bindInteger(1, quickReply.id);
                    state.bindString(2, quickReply.name);
                    state.bindInteger(3, quickReply.order);
                    state.bindInteger(4, quickReply.messagesCount);
                    state.step();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    private void updateOrder() {
        for (int i = 0; i < replies.size(); ++i) {
            replies.get(i).order = i;
        }
    }

    private void addReply(QuickReply reply) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase db = storage.getDatabase();
                state = db.executeFast("REPLACE INTO business_replies VALUES(?, ?, ?, ?);");
                state.requery();
                state.bindInteger(1, reply.id);
                state.bindString(2, reply.name);
                state.bindInteger(3, reply.order);
                state.bindInteger(4, reply.messagesCount);
                state.step();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
    }

    public QuickReply findReply(long topicId) {
        for (QuickReply reply : replies) {
            if (reply.id == topicId) {
                return reply;
            }
        }
        return null;
    }

    public QuickReply findReply(String name) {
        for (QuickReply reply : replies) {
            if (TextUtils.equals(name, reply.name)) {
                return reply;
            }
        }
        return null;
    }

    public QuickReply findLocalReply(long topicId) {
        for (QuickReply reply : localReplies) {
            if (reply.id == topicId) {
                return reply;
            }
        }
        return null;
    }

    public QuickReply findLocalReply(String name) {
        for (QuickReply reply : localReplies) {
            if (TextUtils.equals(name, reply.name)) {
                return reply;
            }
        }
        return null;
    }

    public boolean isNameBusy(String name, int exceptId) {
        QuickReply reply = findReply(name);
        return reply != null && reply.id != exceptId;
    }

    public long getTopicId(String quick_shortcut) {
        QuickReply reply = findReply(quick_shortcut);
        if (reply != null) return reply.id;
        return 0;
    }

    public void removeReply(long topicId) {
        QuickReply reply = null;
        for (int i = 0; i < replies.size(); ++i) {
            if (replies.get(i).id == topicId) {
                reply = replies.remove(i);
                break;
            }
        }
        if (reply == null) {
            return;
        }
        deleteLocalReply(reply.name);

        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase db = storage.getDatabase();
                db.executeFast("DELETE FROM business_replies WHERE topic_id = " + topicId).stepThis().dispose();
                db.executeFast("DELETE FROM quick_replies_messages WHERE topic_id = " + topicId).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
    }

    public void reorder() {
        ArrayList<Integer> oldOrder = new ArrayList<>();
        for (int i = 0; i < replies.size(); ++i) {
            oldOrder.add(replies.get(i).id);
        }
        Collections.sort(replies, (a, b) -> a.order - b.order);
        boolean orderUpdated = false;
        for (int i = 0; i < replies.size(); ++i) {
            if (replies.get(i).id != oldOrder.get(i)) {
                orderUpdated = true;
                break;
            }
        }
        if (orderUpdated) {
            TLRPC.TL_messages_reorderQuickReplies req = new TLRPC.TL_messages_reorderQuickReplies();
            for (int i = 0; i < replies.size(); ++i) {
                req.order.add(replies.get(i).id);
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {

            }));
            saveToCache();
        }
    }

    public void renameReply(int topicId, String name) {
        QuickReply reply = findReply(topicId);
        if (reply == null) return;
        final String oldName = reply.name;
        reply.name = name;
        TLRPC.TL_messages_editQuickReplyShortcut req = new TLRPC.TL_messages_editQuickReplyShortcut();
        req.shortcut_id = topicId;
        req.shortcut = name;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {

        }));
        saveToCache();

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
    }

    public void deleteReplies(ArrayList<Integer> ids) {
        for (int i = 0; i < ids.size(); ++i) {
            if (findReply(ids.get(i)) == null) {
                ids.remove(i);
                i--;
            }
        }
        if (ids.isEmpty()) return;
        final ArrayList<Integer> finalIds = ids;
        for (int i = 0; i < ids.size(); ++i) {
            QuickReply reply = findReply(ids.get(i));
            replies.remove(reply);
            deleteLocalReply(reply.name);

            TLRPC.TL_messages_deleteQuickReplyShortcut req = new TLRPC.TL_messages_deleteQuickReplyShortcut();
            req.shortcut_id = reply.id;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {

            }));

            if (GREETING.equals(reply.name)) {
                ConnectionsManager.getInstance(currentAccount).sendRequest(new TLRPC.TL_account_updateBusinessGreetingMessage(), null);
                TLRPC.UserFull userInfo = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
                if (userInfo != null) {
                    userInfo.flags2 &=~ 4;
                    userInfo.business_greeting_message = null;
                    MessagesStorage.getInstance(currentAccount).updateUserInfo(userInfo, true);
                }
            } else if (AWAY.equals(reply.name)) {
                ConnectionsManager.getInstance(currentAccount).sendRequest(new TLRPC.TL_account_updateBusinessAwayMessage(), null);
                TLRPC.UserFull userInfo = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
                if (userInfo != null) {
                    userInfo.flags2 &=~ 8;
                    userInfo.business_away_message = null;
                    MessagesStorage.getInstance(currentAccount).updateUserInfo(userInfo, true);
                }
            }
        }
        saveToCache();
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            try {
                storage.getDatabase().executeFast(String.format("DELETE FROM quick_replies_messages WHERE topic_id IN (%s)", TextUtils.join(", ", finalIds))).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
    }

    private void updateTopMessage(QuickReply reply) {
        if (reply == null) return;
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();

                MessageObject messageObject = null;
                cursor = storage.getDatabase().queryFinalized("SELECT data, send_state, mid, date, topic_id, ttl FROM quick_replies_messages WHERE topic_id = ? ORDER BY mid ASC", reply.id);
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.send_state = cursor.intValue(1);
                        message.readAttachPath(data, selfId);
                        data.reuse();
                        message.id = cursor.intValue(2);
                        message.date = cursor.intValue(3);
                        message.flags |= 1073741824;
                        message.quick_reply_shortcut_id = cursor.intValue(4);
                        message.ttl = cursor.intValue(5);
                        MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                        messageObject = new MessageObject(currentAccount, message, false, true);
                    }
                }
                cursor.dispose();

                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                if (!chatsToLoad.isEmpty()) {
                    storage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (!usersToLoad.isEmpty()) {
                    storage.getUsersInternal(usersToLoad, users);
                }
                final MessageObject finalMessageObject = messageObject;
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController.getInstance(currentAccount).putUsers(users, true);
                    MessagesController.getInstance(currentAccount).putChats(chats, true);
                    reply.topMessage = finalMessageObject;
                    if (reply.topMessage != null) {
                        reply.topMessage.applyQuickReply(reply.name, reply.id);
                    }
                    saveToCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                });
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
        // TODO
    }

    public boolean processUpdate(TLRPC.Update update, String quick_reply_shortcut, int quick_reply_shortcut_id) {
        if (update instanceof TLRPC.TL_updateQuickReplyMessage) {
            TLRPC.Message message = ((TLRPC.TL_updateQuickReplyMessage) update).message;
            ensureLoaded(() -> {
                if ((message.flags & 1073741824) != 0) {
                    QuickReply reply = findReply(message.quick_reply_shortcut_id);
                    if (reply == null) {
                        QuickReply newReply = new QuickReply();
                        newReply.id = message.quick_reply_shortcut_id;
                        newReply.topMessageId = message.id;
                        newReply.topMessage = new MessageObject(currentAccount, message, false, true);
                        newReply.topMessage.generateThumbs(false);
                        if (quick_reply_shortcut != null) {
                            newReply.name = quick_reply_shortcut;
                            deleteLocalReply(newReply.name);
                        }
                        newReply.topMessage.applyQuickReply(quick_reply_shortcut, quick_reply_shortcut_id);
                        newReply.messagesCount = 1;
                        replies.add(newReply);
                        updateOrder();
                        addReply(newReply);
                    } else if (reply.topMessageId == message.id) {
                        reply.topMessageId = message.id;
                        reply.topMessage = new MessageObject(currentAccount, message, false, true);
                        reply.topMessage.generateThumbs(false);
                        saveToCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                    } else if ((message.flags & TLRPC.MESSAGE_FLAG_EDITED) == 0) {
                        reply.messagesCount++;
                        saveToCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                    }
                }

                if (quick_reply_shortcut == null && quick_reply_shortcut_id == 0) {
                    ArrayList<TLRPC.Message> array = new ArrayList<>();
                    array.add(message);
                    MessagesStorage.getInstance(currentAccount).putMessages(array, true, true, false, DownloadController.getInstance(currentAccount).getAutodownloadMask(), ChatActivity.MODE_QUICK_REPLIES, message.quick_reply_shortcut_id);
                    final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();

                    ArrayList<MessageObject> msgObjs = new ArrayList<>();
                    msgObjs.add(new MessageObject(currentAccount, message, true, true));
                    MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(selfId, msgObjs, ChatActivity.MODE_QUICK_REPLIES);
                }
            });
            return true;
        } else if (update instanceof TLRPC.TL_updateQuickReplies) {
            ensureLoaded(() -> {
                ArrayList<TLRPC.TL_quickReply> quick_replies = ((TLRPC.TL_updateQuickReplies) update).quick_replies;
                ArrayList<QuickReply> oldReplies = new ArrayList<>(replies);
                replies.clear();
                for (int i = 0; i < quick_replies.size(); ++i) {
                    TLRPC.TL_quickReply tlreply = quick_replies.get(i);
                    QuickReply quickReply = null;
                    for (int j = 0; j < oldReplies.size(); ++j) {
                        if (oldReplies.get(j).id == tlreply.shortcut_id) {
                            quickReply = oldReplies.get(j);
                            break;
                        }
                    }
                    if (quickReply == null) {
                        quickReply = new QuickReply();
                    }
                    quickReply.id = tlreply.shortcut_id;
                    quickReply.name = tlreply.shortcut;
                    quickReply.messagesCount = tlreply.count;
                    quickReply.order = i;
                    quickReply.topMessageId = tlreply.top_message;
                    if (quickReply.topMessage != null && quickReply.topMessage.getId() != tlreply.top_message) {
                        quickReply.topMessage = null;
                    }
                    replies.add(quickReply);
                    deleteLocalReply(quickReply.name);
                }
                saveToCache();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
            });
            return true;
        } else if (update instanceof TLRPC.TL_updateNewQuickReply) {
            ensureLoaded(() -> {
                TLRPC.TL_quickReply tlreply = ((TLRPC.TL_updateNewQuickReply) update).quick_reply;
                QuickRepliesController.QuickReply reply = findReply(tlreply.shortcut_id);
                if (reply != null) {
                    reply.name = tlreply.shortcut;
                    reply.messagesCount = tlreply.count;
                    reply.topMessageId = tlreply.top_message;
                    if (reply.topMessage != null && reply.topMessage.getId() != tlreply.top_message) {
                        reply.topMessage = null;
                        updateTopMessage(reply);
                        return;
                    }
                } else {
                    QuickReply quickReply = new QuickReply();
                    quickReply.id = tlreply.shortcut_id;
                    quickReply.name = tlreply.shortcut;
                    quickReply.messagesCount = tlreply.count;
                    quickReply.topMessageId = tlreply.top_message;
                    updateOrder();
                    replies.add(quickReply);
                    deleteLocalReply(quickReply.name);
                }
                saveToCache();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
            });
            return true;
        } else if (update instanceof TLRPC.TL_updateDeleteQuickReply) {
            ensureLoaded(() -> {
                int id = ((TLRPC.TL_updateDeleteQuickReply) update).shortcut_id;
                QuickReply reply = findReply(id);
                if (reply != null) {
                    replies.remove(reply);
                    deleteLocalReply(reply.name);
                    final int topicId = reply.id;
                    MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
                    storage.getStorageQueue().postRunnable(() -> {
                        try {
                            SQLiteDatabase db = storage.getDatabase();
                            db.executeFast("DELETE FROM business_replies WHERE topic_id = " + topicId).stepThis().dispose();
                            db.executeFast("DELETE FROM quick_replies_messages WHERE topic_id = " + topicId).stepThis().dispose();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                    saveToCache();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                }
            });
            return true;
        } else if (update instanceof TLRPC.TL_updateDeleteQuickReplyMessages) {
            ensureLoaded(() -> {
                TLRPC.TL_updateDeleteQuickReplyMessages upd = (TLRPC.TL_updateDeleteQuickReplyMessages) update;
                int id = upd.shortcut_id;
                QuickReply quickReply = findReply(id);
                if (quickReply != null) {
                    quickReply.messagesCount -= upd.messages.size();
                    if (quickReply.messagesCount <= 0) {
                        replies.remove(quickReply);
                    }
                    if (upd.messages.contains(quickReply.getTopMessageId()) || quickReply.topMessage == null) {
                        quickReply.topMessage = null;
                        updateTopMessage(quickReply);
                    } else {
                        saveToCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                    }
                }
            });
            return true;
        }
        return false;
    }

    public void checkLocalMessages(ArrayList<MessageObject> messages) {
        for (MessageObject message : messages) {
            if (!message.isSending()) continue;
            QuickReply reply = findReply(message.getQuickReplyId());
            if (reply != null) continue;
            if (message.getQuickReplyName() == null) continue;
            reply = findReply(message.getQuickReplyName());
            if (reply != null) continue;

            reply = findLocalReply(message.getQuickReplyName());
            if (reply == null) {
                reply = new QuickReply();
                reply.local = true;
                reply.name = message.getQuickReplyName();
                reply.id = -1;
                reply.topMessage = message;
                reply.topMessageId = message.getId();
                localReplies.add(reply);
            }
            reply.localIds.add(message.getId());

            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
            });
        }
    }

    public void deleteLocalReply(String name) {
        QuickReply reply = findLocalReply(name);
        if (reply != null) {
            localReplies.remove(reply);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
        }
    }

    public void deleteLocalMessages(ArrayList<Integer> messages) {
        for (int id : messages) {
            deleteLocalMessage(id);
        }
    }

    public void deleteLocalMessage(int messageId) {
        for (int i = 0; i < localReplies.size(); ++i) {
            QuickReply reply = localReplies.get(i);
            if (reply.localIds.contains(messageId)) {
                reply.localIds.remove((Integer) messageId);
                if (reply.getMessagesCount() <= 0) {
                    localReplies.remove(reply);
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                break;
            }
        }
    }

    public boolean hasReplies() {
        return !replies.isEmpty();
    }

    public void sendQuickReplyTo(long dialogId, QuickRepliesController.QuickReply reply) {
        if (reply == null) return;

        TLRPC.TL_messages_sendQuickReplyMessages req = new TLRPC.TL_messages_sendQuickReplyMessages();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        if (req.peer == null) return;
        req.shortcut_id = reply.id;

        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<Integer> ids = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized("SELECT id FROM quick_replies_messages WHERE topic_id = ?", reply.id);
                while (cursor.next()) {
                    ids.add(cursor.intValue(0));
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (ids.isEmpty() || ids.size() < reply.getMessagesCount()) {
                    TLRPC.TL_messages_getQuickReplyMessages req2 = new TLRPC.TL_messages_getQuickReplyMessages();
                    req2.shortcut_id = reply.id;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TLRPC.TL_messages_messages) {
                            ArrayList<TLRPC.Message> messages = ((TLRPC.TL_messages_messages) res).messages;
                            ids.clear();
                            for (TLRPC.Message m : messages) {
                                ids.add(m.id);
                            }

                            req.id = ids;
                            for (int i = 0; i < ids.size(); ++i) {
                                req.random_id.add(Utilities.random.nextLong());
                            }
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, null);
                        } else {
                            FileLog.e("received " + res + " " + err + " on getQuickReplyMessages when trying to send quick reply");
                        }
                    }));
                } else {
                    req.id = ids;
                    for (int i = 0; i < ids.size(); ++i) {
                        req.random_id.add(Utilities.random.nextLong());
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                }
            });
        });
    }

}
