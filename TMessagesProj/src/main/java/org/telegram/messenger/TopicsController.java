package org.telegram.messenger;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Forum.ForumUtilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class TopicsController extends BaseController {

    public static final int TOPIC_FLAG_TITLE = 1;
    public static final int TOPIC_FLAG_ICON = 2;
    public static final int TOPIC_FLAG_PIN = 4;
    public static final int TOPIC_FLAG_CLOSE = 8;
    public static final int TOPIC_FLAG_TOTAL_MESSAGES_COUNT = 16;
    public static final int TOPIC_FLAG_HIDE = 32;

    private static final int MAX_PRELOAD_COUNT = 20;

    public static final int LOAD_TYPE_PRELOAD = 0;
    public static final int LOAD_TYPE_LOAD_NEXT = 1;
    public static final int LOAD_TYPE_LOAD_UNKNOWN = 2;

    LongSparseArray<ArrayList<TLRPC.TL_forumTopic>> topicsByChatId = new LongSparseArray<>();
    LongSparseArray<LongSparseArray<TLRPC.TL_forumTopic>> topicsMapByChatId = new LongSparseArray<>();
    LongSparseIntArray topicsIsLoading = new LongSparseIntArray();
    LongSparseIntArray endIsReached = new LongSparseIntArray();
    LongSparseArray<TLRPC.TL_forumTopic> topicsByTopMsgId = new LongSparseArray<>();

    LongSparseIntArray currentOpenTopicsCounter = new LongSparseIntArray();
    LongSparseIntArray openedTopicsBuChatId = new LongSparseIntArray();

    public TopicsController(int num) {
        super(num);
    }

    public void preloadTopics(long chatId) {
        loadTopics(chatId, true, LOAD_TYPE_PRELOAD);
    }

    public void loadTopics(long chatId) {
        loadTopics(chatId, false, LOAD_TYPE_LOAD_NEXT);
    }

    public void loadTopics(long chatId, boolean fromCache, int loadType) {
        if (topicsIsLoading.get(chatId, 0) != 0) {
            return;
        }

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load topics " + chatId + " fromCache=" + fromCache + " loadType=" + loadType);
        }
        topicsIsLoading.put(chatId, 1);

        if (fromCache) {
            getMessagesStorage().loadTopics(-chatId, topics -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("loaded from cache " + chatId + " topics_count=" + (topics == null ? 0 : topics.size()));
                    }

                    topicsIsLoading.put(chatId, 0);
                    processTopics(chatId, topics, null, fromCache, loadType, -1);
                    sortTopics(chatId);
                });
            });
            return;
        }

        TLRPC.TL_channels_getForumTopics getForumTopics = new TLRPC.TL_channels_getForumTopics();
        getForumTopics.channel = getMessagesController().getInputChannel(chatId);
        if (loadType == LOAD_TYPE_PRELOAD) {
            getForumTopics.limit = MAX_PRELOAD_COUNT;
        } else if (loadType == LOAD_TYPE_LOAD_NEXT) {
            getForumTopics.limit = 100;
            TopicsLoadOffset loadOffsets = getLoadOffset(chatId);
            getForumTopics.offset_date = loadOffsets.lastMessageDate;
            getForumTopics.offset_id = loadOffsets.lastMessageId;
            getForumTopics.offset_topic = loadOffsets.lastTopicId;

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("offset_date=" + loadOffsets.lastMessageDate + " offset_id=" + loadOffsets.lastMessageId + " offset_topic=" + loadOffsets.lastTopicId);
            }
        }

        getConnectionsManager().sendRequest(getForumTopics, (response, error) -> {
            if (response != null) {
                SparseArray<TLRPC.Message> messagesMap = new SparseArray<>();
                TLRPC.TL_messages_forumTopics topics = (TLRPC.TL_messages_forumTopics) response;
                for (int i = 0; i < topics.messages.size(); i++) {
                    messagesMap.put(topics.messages.get(i).id, topics.messages.get(i));
                }
                AndroidUtilities.runOnUIThread(() -> {
                    getMessagesStorage().putUsersAndChats(((TLRPC.TL_messages_forumTopics) response).users, ((TLRPC.TL_messages_forumTopics) response).chats, true, true);
                    getMessagesController().putUsers(((TLRPC.TL_messages_forumTopics) response).users, false);
                    getMessagesController().putChats(((TLRPC.TL_messages_forumTopics) response).chats, false);

                    topicsIsLoading.put(chatId, 0);
                    processTopics(chatId, topics.topics, messagesMap, false, loadType, ((TLRPC.TL_messages_forumTopics) response).count);
                    getMessagesStorage().putMessages(topics.messages, false, true, false, 0, false, 0, 0);
                    sortTopics(chatId);
                    getMessagesStorage().saveTopics(-chatId, topicsByChatId.get(chatId), true, true);

                    if (!topics.topics.isEmpty() && loadType == LOAD_TYPE_LOAD_NEXT) {
                        TLRPC.TL_forumTopic lastTopic = topics.topics.get(topics.topics.size() - 1);
                        TLRPC.Message lastTopicMessage = messagesMap.get(lastTopic.top_message);
                        saveLoadOffset(chatId, lastTopic.top_message, lastTopicMessage == null ? 0 : lastTopicMessage.date, lastTopic.id);
                    } else if (getTopics(chatId) == null || getTopics(chatId).size() < topics.count) {
                        clearLoadingOffset(chatId);
                        loadTopics(chatId);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    topicsIsLoading.put(chatId, 0);
                    getNotificationCenter().postNotificationName(NotificationCenter.topicsDidLoaded, chatId, false);
                });
            }

        });
    }

    public void processTopics(long chatId, ArrayList<TLRPC.TL_forumTopic> newTopics, SparseArray<TLRPC.Message> messagesMap, boolean fromCache, int loadType, int totalCount) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processTopics=" + "new_topics_size=" + (newTopics == null ? 0 : newTopics.size()) + " fromCache=" + fromCache + " load_type=" + loadType + " totalCount=" + totalCount);
        }
        ArrayList<TLRPC.TL_forumTopic> topics = topicsByChatId.get(chatId);
        ArrayList<TLRPC.TL_forumTopic> topicsToReload = null;
        ArrayList<Integer> deletedTopics = null;
        LongSparseArray<TLRPC.TL_forumTopic> topicsMap = topicsMapByChatId.get(chatId);

        if (topics == null) {
            topics = new ArrayList<>();
            topicsByChatId.put(chatId, topics);
        }
        if (topicsMap == null) {
            topicsMap = new LongSparseArray<>();
            topicsMapByChatId.put(chatId, topicsMap);
        }

        boolean changed = false;
        if (newTopics != null) {
            for (int i = 0; i < newTopics.size(); i++) {
                TLRPC.TL_forumTopic newTopic = newTopics.get(i);
                if (newTopic instanceof TLRPC.TL_forumTopicDeleted) {
                    if (deletedTopics == null) {
                        deletedTopics = new ArrayList<>();
                    }
                    deletedTopics.add(newTopic.id);
                    continue;
                }
                if (!topicsMap.containsKey(newTopic.id)) {
                    if (messagesMap != null) {
                        newTopic.topMessage = messagesMap.get(newTopic.top_message);
                        newTopic.topicStartMessage = messagesMap.get(newTopic.id);
                    }
                    if (newTopic.topMessage == null && !newTopic.isShort) {
                        if (topicsToReload == null) {
                            topicsToReload = new ArrayList<>();
                        }
                        topicsToReload.add(newTopic);
                    }
                    if (newTopic.topicStartMessage == null) {
                        newTopic.topicStartMessage = new TLRPC.TL_message();
                        newTopic.topicStartMessage.message = "";
                        newTopic.topicStartMessage.id = newTopic.id;
                        newTopic.topicStartMessage.peer_id = getMessagesController().getPeer(-chatId);
                        newTopic.topicStartMessage.action = new TLRPC.TL_messageActionTopicCreate();
                        newTopic.topicStartMessage.action.title = newTopic.title;
                    }
                    topics.add(newTopic);
                    topicsMap.put(newTopic.id, newTopic);
                    topicsByTopMsgId.put(messageHash(newTopic.top_message, chatId), newTopic);
                    changed = true;
                }
            }
        }

        int pinnedTopics = 0;
        for (int i = 0; i < topics.size(); ++i) {
            TLRPC.TL_forumTopic topic = topics.get(i);
            if (topic != null && topic.pinned) {
                int newPinnedOrder = pinnedTopics++;
                if (topic.pinnedOrder != newPinnedOrder) {
                    topic.pinnedOrder = newPinnedOrder;
                    changed = true;
                }
            }
        }

        if (deletedTopics != null && loadType == LOAD_TYPE_LOAD_UNKNOWN) {
           for (int i = 0; i < deletedTopics.size(); i++) {
               for (int j = 0; j < topics.size(); j++) {
                   if (topics.get(j).id == deletedTopics.get(i)) {
                       topics.remove(j);
                       break;
                   }
               }
           }
           getMessagesStorage().removeTopics(chatId, deletedTopics);
        }
        if (topicsToReload != null && loadType != LOAD_TYPE_LOAD_UNKNOWN) {
            reloadTopics(chatId, topicsToReload, null);
        } else if (((loadType == LOAD_TYPE_PRELOAD && !fromCache) || loadType == LOAD_TYPE_LOAD_NEXT) && topics.size() >= totalCount && totalCount >= 0) {
            endIsReached.put(chatId, 1);
            getUserConfig().getPreferences().edit().putBoolean("topics_end_reached_" + chatId, true).apply();
            changed = true;
        }

        if (changed) {
            sortTopics(chatId);
        }

        getNotificationCenter().postNotificationName(NotificationCenter.topicsDidLoaded, chatId, true);

        if ((loadType == LOAD_TYPE_PRELOAD || (loadType == LOAD_TYPE_PRELOAD && !fromCache)) && fromCache && topicsByChatId.get(chatId).isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> {
                loadTopics(chatId, false, LOAD_TYPE_PRELOAD);
            });
        }
    }

    private long messageHash(int messageId, long chatId) {
        return chatId + ((long) messageId << 12);
    }


    public ArrayList<TLRPC.TL_forumTopic> getTopics(long chatId) {
        return topicsByChatId.get(chatId);
    }

    private void sortTopics(long chatId) {
        sortTopics(chatId, true);
    }

    public void sortTopics(long chatId, boolean notify) {
        ArrayList<TLRPC.TL_forumTopic> topics = topicsByChatId.get(chatId);
        if (topics != null) {
            if (openedTopicsBuChatId.get(chatId, 0) > 0) {
                Collections.sort(topics, (a, b) -> {
                    if (a.hidden != b.hidden) {
                        return a.hidden ? -1 : 1;
                    }
                    if (a.pinned != b.pinned) {
                        return a.pinned ? -1 : 1;
                    }
                    if (a.pinned && b.pinned) {
                        return a.pinnedOrder - b.pinnedOrder;
                    }
                    return (b.topMessage != null ? b.topMessage.date : 0) - (a.topMessage != null ? a.topMessage.date : 0);
                });
            }
            if (notify) {
                getNotificationCenter().postNotificationName(NotificationCenter.topicsDidLoaded, chatId, true);
            }
        }
    }

    public void updateTopicsWithDeletedMessages(long dialogId, ArrayList<Integer> messages) {
        if (dialogId > 0) {
            return;
        }
        long chatId = -dialogId;
        AndroidUtilities.runOnUIThread(() -> {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                ArrayList<TLRPC.TL_forumTopic> topicsToUpdate = null;
                try {
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT topic_id, top_message FROM topics WHERE did = %d AND top_message IN (%s)", dialogId, TextUtils.join(",", messages)));
                    while (cursor.next()) {
                        if (topicsToUpdate == null) {
                            topicsToUpdate = new ArrayList<>();
                        }

                        TLRPC.TL_forumTopic topic = new TLRPC.TL_forumTopic();
                        topic.id = cursor.intValue(0);
                        topic.top_message = cursor.intValue(1);
                        topic.from_id = getMessagesController().getPeer(getUserConfig().clientUserId);
                        topic.notify_settings = new TLRPC.TL_peerNotifySettings();
                        topicsToUpdate.add(topic);
                    }
                    cursor.dispose();

                    if (topicsToUpdate != null) {
                        for (int i = 0; i < topicsToUpdate.size(); i++) {
                            TLRPC.TL_forumTopic topic = topicsToUpdate.get(i);
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT mid, data FROM messages_topics WHERE uid = %d AND topic_id = %d ORDER BY mid DESC LIMIT 1", dialogId, topic.id));

                            if (cursor.next()) {
                                NativeByteBuffer data = cursor.byteBufferValue(1);
                                if (data != null) {
                                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    message.readAttachPath(data, getUserConfig().clientUserId);
                                    data.reuse();
                                    topicsByTopMsgId.remove(messageHash(topic.top_message, chatId));
                                    topic.top_message = message.id;
                                    topic.topMessage = message;
                                    topic.groupedMessages = null;
                                    topicsByTopMsgId.put(messageHash(topic.top_message, chatId), topic);
                                }
                            }
                            cursor.dispose();
                        }

                        for (int i = 0; i < topicsToUpdate.size(); i++) {
                            getMessagesStorage().getDatabase().executeFast(String.format(Locale.US, "UPDATE topics SET top_message = %d WHERE did = %d AND topic_id = %d", topicsToUpdate.get(i).top_message, dialogId, topicsToUpdate.get(i).id)).stepThis().dispose();
                        }
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
                ArrayList<TLRPC.TL_forumTopic> finalTopicsToUpdate = topicsToUpdate;
                getMessagesStorage().loadGroupedMessagesForTopics(dialogId, finalTopicsToUpdate);
                if (finalTopicsToUpdate != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        boolean changed = false;
                        ArrayList<TLRPC.TL_forumTopic> topicsToReload = null;

                        for (int i = 0; i < finalTopicsToUpdate.size(); i++) {
                            TLRPC.TL_forumTopic fromUpdate = finalTopicsToUpdate.get(i);
                            LongSparseArray<TLRPC.TL_forumTopic> map = topicsMapByChatId.get(chatId);
                            TLRPC.TL_forumTopic toUpdate;
                            if (map != null) {
                                toUpdate = map.get(fromUpdate.id);
                                if (toUpdate != null && fromUpdate.top_message != -1 && fromUpdate.topMessage != null) {
                                    topicsByTopMsgId.remove(messageHash(toUpdate.top_message, chatId));
                                    toUpdate.top_message = fromUpdate.topMessage.id;
                                    toUpdate.topMessage = fromUpdate.topMessage;
                                    toUpdate.groupedMessages = fromUpdate.groupedMessages;

                                    topicsByTopMsgId.put(messageHash(toUpdate.top_message, chatId), toUpdate);
                                    changed = true;
                                } else if (fromUpdate.top_message == -1 || fromUpdate.topMessage == null) {
                                    if (topicsToReload == null) {
                                        topicsToReload = new ArrayList<>();
                                    }
                                    topicsToReload.add(fromUpdate);
                                }
                            }
                        }
                        if (changed) {
                            sortTopics(chatId);
                        }
                        if (topicsToReload != null) {
                            reloadTopics(chatId, topicsToReload, null);
                        }
                    });
                }
            });
        });
    }

    public void reloadTopics(long chatId, ArrayList<TLRPC.TL_forumTopic> topicsToReload, Runnable callback) {
        TLRPC.TL_channels_getForumTopicsByID req = new TLRPC.TL_channels_getForumTopicsByID();
        for (int i = 0; i < topicsToReload.size(); i++) {
            req.topics.add(topicsToReload.get(i).id);
        }
        req.channel = getMessagesController().getInputChannel(chatId);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                SparseArray<TLRPC.Message> messagesMap = new SparseArray<>();
                TLRPC.TL_messages_forumTopics topics = (TLRPC.TL_messages_forumTopics) response;
                for (int i = 0; i < topics.messages.size(); i++) {
                    messagesMap.put(topics.messages.get(i).id, topics.messages.get(i));
                }
                AndroidUtilities.runOnUIThread(() -> {
                    getMessagesController().putUsers(((TLRPC.TL_messages_forumTopics) response).users, false);
                    getMessagesController().putChats(((TLRPC.TL_messages_forumTopics) response).chats, false);

                    processTopics(chatId, topics.topics, messagesMap, false, LOAD_TYPE_LOAD_UNKNOWN, -1);
                    getMessagesStorage().putMessages(topics.messages, false, true, false, 0, false, 0, 0);
                    getMessagesStorage().saveTopics(-chatId, topicsByChatId.get(chatId), true, true);
                    if (callback != null) {
                        callback.run();
                    }
                });
            }
        }));
    }

    public void updateMaxReadId(long chatId, long topicId, int readMaxId, int unreadCount, int mentionsUnread) {
        TLRPC.TL_forumTopic topic = findTopic(chatId, topicId);
        if (topic != null) {
            topic.read_inbox_max_id = readMaxId;
            topic.unread_count = unreadCount;
            if (mentionsUnread >= 0) {
                topic.unread_mentions_count = mentionsUnread;
            }
            sortTopics(chatId);
        }
    }

    public TLRPC.TL_forumTopic findTopic(long chatId, long topicId) {
        LongSparseArray<TLRPC.TL_forumTopic> topicsMap = topicsMapByChatId.get(chatId);
        if (topicsMap != null) {
            return topicsMap.get(topicId);
        }
        return null;
    }

    public String getTopicName(TLRPC.Chat chat, MessageObject message) {
        if (message.messageOwner.reply_to == null) {
            return null;
        }
        int topicId = message.messageOwner.reply_to.reply_to_top_id;
        if (topicId == 0) {
            topicId = message.messageOwner.reply_to.reply_to_msg_id;
        }
        if (topicId != 0) {
            TLRPC.TL_forumTopic topic = findTopic(chat.id, topicId);
            if (topic != null) {
                return topic.title;
            }
        }
        return "";
    }

    public CharSequence getTopicIconName(TLRPC.Chat chat, MessageObject message, TextPaint paint) {
        return getTopicIconName(chat, message, paint, null);
    }

    public CharSequence getTopicIconName(TLRPC.Chat chat, MessageObject message, TextPaint paint, Drawable[] drawableToSet) {
        if (message.messageOwner.reply_to == null) {
            return null;
        }
        int topicId = message.messageOwner.reply_to.reply_to_top_id;
        if (topicId == 0) {
            topicId = message.messageOwner.reply_to.reply_to_msg_id;
        }
        if (topicId != 0) {
            TLRPC.TL_forumTopic topic = findTopic(chat.id, topicId);
            if (topic != null) {
                return ForumUtilities.getTopicSpannedName(topic, paint, drawableToSet, false);
            }
        }
        return null;
    }

    private final static int[] countsTmp = new int[4];

    public int[] getForumUnreadCount(long chatId) {
        ArrayList<TLRPC.TL_forumTopic> topics = topicsByChatId.get(chatId);
        Arrays.fill(countsTmp, 0);
        if (topics != null) {
            for (int i = 0; i < topics.size(); i++) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                countsTmp[0] += topic.unread_count > 0 ? 1 : 0;
                countsTmp[1] += topic.unread_mentions_count > 0 ? 1 : 0;
                countsTmp[2] += topic.unread_reactions_count > 0 ? 1 : 0;
                if (!getMessagesController().isDialogMuted(-chatId, topic.id)) {
                    countsTmp[3] += topic.unread_count;
                }

            }
        }
        return countsTmp;
    }

    public void onTopicCreated(long dialogId, TLRPC.TL_forumTopic forumTopic, boolean saveInDatabase) {
        LongSparseArray<TLRPC.TL_forumTopic> map = topicsMapByChatId.get(-dialogId);
        if (findTopic(-dialogId, forumTopic.id) != null) {
            return;
        }
        if (map == null) {
            map = new LongSparseArray<>();
            topicsMapByChatId.put(-dialogId, map);
        }
        ArrayList<TLRPC.TL_forumTopic> list = topicsByChatId.get(-dialogId);
        if (list == null) {
            list = new ArrayList<>();
            topicsByChatId.put(-dialogId, list);
        }
        map.put(forumTopic.id, forumTopic);
        list.add(forumTopic);
        if (saveInDatabase) {
            getMessagesStorage().saveTopics(dialogId, Collections.singletonList(forumTopic), false, true);
        }
        sortTopics(-dialogId, true);
    }

    public void onTopicEdited(long dialogId, TLRPC.TL_forumTopic forumTopic) {
        getMessagesStorage().updateTopicData(dialogId, forumTopic, TOPIC_FLAG_ICON + TOPIC_FLAG_TITLE + TOPIC_FLAG_HIDE);
        sortTopics(-dialogId);
    }

    public void deleteTopics(long chatId, ArrayList<Integer> topicIds) {
        ArrayList<TLRPC.TL_forumTopic> topicsArray = topicsByChatId.get(chatId);
        LongSparseArray<TLRPC.TL_forumTopic> topicsMap = topicsMapByChatId.get(chatId);
        if (topicsMap != null && topicsArray != null) {
            for (int i = 0; i < topicIds.size(); i++) {
                int topicId = topicIds.get(i);
                TLRPC.TL_forumTopic topic = topicsMap.get(topicId);
                topicsMap.remove(topicId);
                if (topic != null) {
                    topicsByTopMsgId.remove(messageHash(topic.top_message, chatId));
                    topicsArray.remove(topic);
                }
            }
            sortTopics(chatId);
        }
        for (int i = 0; i < topicIds.size(); i++) {
            deleteTopic(chatId, topicIds.get(i), 0);
        }
    }

    private void deleteTopic(long chatId, int topicId, int offset) {
        TLRPC.TL_channels_deleteTopicHistory deleteTopicHistory = new TLRPC.TL_channels_deleteTopicHistory();
        deleteTopicHistory.channel = getMessagesController().getInputChannel(chatId);
        deleteTopicHistory.top_msg_id = topicId;
        if (offset == 0) {
            getMessagesStorage().removeTopic(-chatId, topicId);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(deleteTopicHistory, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                    getMessagesController().processNewChannelDifferenceParams(res.pts, res.pts_count, chatId);
                    if (res.offset > 0) {
                        deleteTopic(chatId, topicId, res.offset);
                    }
                }

            }
        });
    }

    public void toggleCloseTopic(long chatId, int topicId, boolean close) {
        TLRPC.TL_channels_editForumTopic req = new TLRPC.TL_channels_editForumTopic();
        req.channel = getMessagesController().getInputChannel(chatId);
        req.topic_id = topicId;
        req.flags |= 4;
        req.closed = close;

        LongSparseArray<TLRPC.TL_forumTopic> topicsMap = topicsMapByChatId.get(chatId);
        if (topicsMap != null) {
            TLRPC.TL_forumTopic topic = topicsMap.get(topicId);
            if (topic != null) {
                topic.closed = close;
                getMessagesStorage().updateTopicData(-chatId, topic, TOPIC_FLAG_CLOSE);
            }
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {

                }
            }
        });
    }

    public ArrayList<Integer> getCurrentPinnedOrder(long chatId) {
        ArrayList<TLRPC.TL_forumTopic> topics = getTopics(chatId);
        ArrayList<Integer> newOrder = new ArrayList<>();
        if (topics != null) {
            for (int i = 0; i < topics.size(); ++i) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                if (topic == null) {
                    continue;
                }
                if (topic.pinned) {
                    newOrder.add(topic.id);
                }
            }
        }
        return newOrder;
    }

    public void applyPinnedOrder(long chatId, ArrayList<Integer> order) {
        applyPinnedOrder(chatId, order, true);
    }

    public void applyPinnedOrder(long chatId, ArrayList<Integer> order, boolean notify) {
        if (order == null) {
            return;
        }

        ArrayList<TLRPC.TL_forumTopic> topics = getTopics(chatId);
        boolean updated = false;
        if (topics != null) {
            for (int i = 0; i < topics.size(); ++i) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                if (topic == null) {
                    continue;
                }
                int newPinnedOrder = order.indexOf(topic.id);
                boolean newPinned = newPinnedOrder >= 0;
                if (topic.pinned != newPinned || newPinned && topic.pinnedOrder != newPinnedOrder) {
                    updated = true;
                    topic.pinned = newPinned;
                    topic.pinnedOrder = newPinnedOrder;
                    getMessagesStorage().updateTopicData(chatId, topic, TopicsController.TOPIC_FLAG_PIN);
                }
            }
        } else {
            updated = true;
        }

        if (notify && updated) {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_SELECT_DIALOG);
            });
        }
    }

    public void toggleShowTopic(long chatId, int topicId, boolean show) {
        TLRPC.TL_channels_editForumTopic req = new TLRPC.TL_channels_editForumTopic();
        req.channel = getMessagesController().getInputChannel(chatId);
        req.topic_id = topicId;
        req.flags = 8;
        boolean wasHidden = show;
        req.hidden = !show;

        TLRPC.TL_forumTopic topic = findTopic(chatId, topicId);
        if (topic != null) {
            wasHidden = topic.hidden;
            topic.hidden = req.hidden;
            if (topic.hidden) {
                topic.closed = true;
//                topic.pinned = true;
//                ArrayList<Integer> order = getCurrentPinnedOrder(chatId);
//                order.remove((Integer) topicId);
//                order.add(0, topicId);
//                applyPinnedOrder(chatId, order);
            }
            updateTopicInUi(-chatId, topic, TOPIC_FLAG_PIN | TOPIC_FLAG_HIDE | TOPIC_FLAG_CLOSE);
            getMessagesStorage().updateTopicData(-chatId, topic, TOPIC_FLAG_PIN | TOPIC_FLAG_HIDE | TOPIC_FLAG_CLOSE);
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            if (err != null) {

            }
        });
    }

    public void toggleViewForumAsMessages(long channelId, boolean enabled) {
        TLRPC.TL_channels_toggleViewForumAsMessages request = new TLRPC.TL_channels_toggleViewForumAsMessages();
        request.channel_id = getMessagesController().getInputChannel(channelId);
        request.enabled = enabled;
        getConnectionsManager().sendRequest(request, (res, err) -> {
            if (res != null) {
                getMessagesController().processUpdates((TLRPC.Updates) res, false);
            }
        });
    }

    public void pinTopic(long chatId, int topicId, boolean pin, BaseFragment fragment) {
        TLRPC.TL_channels_updatePinnedForumTopic req = new TLRPC.TL_channels_updatePinnedForumTopic();
        req.channel = getMessagesController().getInputChannel(chatId);
        req.topic_id = topicId;
        req.pinned = pin;

        ArrayList<Integer> prevOrder = getCurrentPinnedOrder(chatId);
        ArrayList<Integer> newOrder = new ArrayList<>(prevOrder);
        newOrder.remove((Integer) topicId);
        if (pin) {
            newOrder.add(0, topicId);
        }
        applyPinnedOrder(chatId, newOrder);

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null) {
                if ("PINNED_TOO_MUCH".equals(error.text)) {
                    if (fragment == null) {
                        return;
                    }
                    applyPinnedOrder(chatId, prevOrder);
                    AndroidUtilities.runOnUIThread(() -> {
                        fragment.showDialog(
                            new AlertDialog.Builder(fragment.getContext())
                                .setTitle(LocaleController.getString("LimitReached", R.string.LimitReached))
                                .setMessage(LocaleController.formatString("LimitReachedPinnedTopics", R.string.LimitReachedPinnedTopics, MessagesController.getInstance(currentAccount).topicsPinnedLimit))
                                .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                                .create()
                        );
                    });
                } else if ("PINNED_TOPIC_NOT_MODIFIED".equals(error.text)) {
                    reloadTopics(chatId, false);
                }
            }
        });
    }

    public void reorderPinnedTopics(long chatId, ArrayList<Integer> topics) {
        TLRPC.TL_channels_reorderPinnedForumTopics req = new TLRPC.TL_channels_reorderPinnedForumTopics();
        req.channel = getMessagesController().getInputChannel(chatId);
        if (topics != null) {
            req.order.addAll(topics);
        }
        req.force = true;
        applyPinnedOrder(chatId, topics, false);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
    }

    public void updateMentionsUnread(long dialogId, long topicId, int topicMentionsCount) {
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.TL_forumTopic topic = findTopic(-dialogId, topicId);
            if (topic != null) {
                topic.unread_mentions_count = topicMentionsCount;
                sortTopics(-dialogId, true);
            }
        });
    }

    public int updateReactionsUnread(long dialogId, long topicId, int count, boolean increment) {
        TLRPC.TL_forumTopic topic = findTopic(-dialogId, topicId);
        int totalCount = -1;
        if (topic != null) {
            if (increment) {
                topic.unread_reactions_count += count;
                if (topic.unread_reactions_count < 0) {
                    topic.unread_reactions_count = 0;
                }
            } else {
                topic.unread_reactions_count = count;
            }
            totalCount = topic.unread_reactions_count;
            sortTopics(-dialogId, true);
        }
        return totalCount;
    }

    public void markAllReactionsAsRead(long chatId, long topicId) {
        TLRPC.TL_forumTopic topic = findTopic(chatId, topicId);
        if (topic != null && topic.unread_reactions_count > 0) {
            topic.unread_reactions_count = 0;
            sortTopics(chatId);
        }
    }

    LongSparseArray<TopicsLoadOffset> offsets = new LongSparseArray<>();

    public TopicsLoadOffset getLoadOffset(long chatId) {
        TopicsLoadOffset offset = offsets.get(chatId);
        if (offset != null) {
            return offset;
        }
        return new TopicsLoadOffset();
//        SharedPreferences sharedPreferences = getUserConfig().getPreferences();
//        TopicsLoadOffset topicsLoadOffset = new TopicsLoadOffset();
//        topicsLoadOffset.lastMessageId = sharedPreferences.getInt("topics_load_offset_message_id_" + chatId, 0);
//        topicsLoadOffset.lastMessageDate = sharedPreferences.getInt("topics_load_offset_date_" + chatId, 0);
//        topicsLoadOffset.lastMessageId = sharedPreferences.getInt("topics_load_offset_topic_id_" + chatId, 0);
//        return topicsLoadOffset;
    }

    public void saveLoadOffset(long chatId, int lastMessageId, int lastMessageDate, int lastTopicId) {
        TopicsLoadOffset offset = new TopicsLoadOffset();
        offset.lastMessageId = lastMessageId;
        offset.lastMessageDate = lastMessageDate;
        offset.lastTopicId = lastTopicId;
        offsets.put(chatId, offset);
//        SharedPreferences.Editor editor = getUserConfig().getPreferences().edit();
//        editor.putInt("topics_load_offset_message_id_" + chatId, lastMessageId);
//        editor.putInt("topics_load_offset_date_" + chatId, lastMessageDate);
//        editor.putInt("topics_load_offset_topic_id_" + chatId, lastTopicId);
//        editor.apply();
    }

    public void clearLoadingOffset(long chatId) {
        offsets.remove(chatId);
//        SharedPreferences.Editor editor = getUserConfig().getPreferences().edit();
//        editor.remove("topics_load_offset_message_id_" + chatId);
//        editor.remove("topics_load_offset_date_" + chatId);
//        editor.remove("topics_load_offset_topic_id_" + chatId);
//        editor.apply();
    }

    public boolean endIsReached(long chatId) {
        return endIsReached.get(chatId, 0) == 1;
    }

    public void processUpdate(List<TopicUpdate> topicUpdates) {
        AndroidUtilities.runOnUIThread(() -> {
            HashSet<Long> changedDialogs = new HashSet<>();
            LongSparseArray<ArrayList<TLRPC.TL_forumTopic>> topicsToReload = null;
            for (int i = 0; i < topicUpdates.size(); i++) {
                TopicUpdate update = topicUpdates.get(i);
                if (update.reloadTopic) {
                    if (topicsToReload == null) {
                        topicsToReload = new LongSparseArray<>();
                    }
                    ArrayList<TLRPC.TL_forumTopic> arrayList = topicsToReload.get(update.dialogId);
                    if (arrayList == null) {
                        arrayList = new ArrayList<>();
                        topicsToReload.put(update.dialogId, arrayList);
                    }
                    TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();
                    forumTopic.id = (int) update.topicId;
                    arrayList.add(forumTopic);
                } else {
                    TLRPC.TL_forumTopic topic = findTopic(-update.dialogId, update.topicId);
                    if (topic != null) {
                        if (update.onlyCounters) {
                            if (update.unreadCount >= 0) {
                                topic.unread_count = update.unreadCount;
                            }
                            if (update.unreadMentions >= 0) {
                                topic.unread_mentions_count = update.unreadMentions;
                            }
                        } else {
                            topicsByTopMsgId.remove(messageHash(topic.top_message, -update.dialogId));
                            topic.topMessage = update.topMessage;
                            topic.groupedMessages = update.groupedMessages;
                            topic.top_message = update.topMessageId;
                            topic.unread_count = update.unreadCount;
                            topic.unread_mentions_count = update.unreadMentions;
                            topicsByTopMsgId.put(messageHash(topic.top_message, -update.dialogId), topic);
                        }
                        if (update.totalMessagesCount > 0) {
                            topic.totalMessagesCount = update.totalMessagesCount;
                        }
                        changedDialogs.add(-update.dialogId);
                    }
                }
            }
            for (Long changedDialog : changedDialogs) {
                sortTopics(changedDialog, true);
            }

            if (topicsToReload != null) {
                for (int i = 0; i < topicsToReload.size(); i++) {
                    long dialogId = topicsToReload.keyAt(i);
                    ArrayList<TLRPC.TL_forumTopic> topics = topicsToReload.valueAt(i);
                    reloadTopics(-dialogId, topics, null);
                }
            }

        });
    }

    public boolean isLoading(long chatId) {
        return topicsIsLoading.get(chatId, 0) == 1 && (topicsByChatId.get(chatId) == null || topicsByChatId.get(chatId).isEmpty());
    }

    public void onTopicsDeletedServerSide(ArrayList<MessagesStorage.TopicKey> topicsToDelete) {
        AndroidUtilities.runOnUIThread(() -> {
            HashSet<Long> changedChatId = new HashSet<>();
            for (int i = 0; i < topicsToDelete.size(); i++) {
                MessagesStorage.TopicKey topicKey = topicsToDelete.get(i);
                long chatId = -topicKey.dialogId;
                LongSparseArray<TLRPC.TL_forumTopic> topicsMap = topicsMapByChatId.get(chatId);
                if (topicsMap != null) {
                    topicsMap.remove(topicKey.topicId);
                }
                ArrayList<TLRPC.TL_forumTopic> topics = topicsByChatId.get(chatId);
                if (topics != null) {
                    for (int k = 0; k < topics.size(); k++) {
                        if (topics.get(k).id == topicKey.topicId) {
                            topics.remove(k);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogDeleted, -chatId, topicKey.topicId);
                            changedChatId.add(chatId);
                            break;
                        }
                    }
                }

            }
            for (Long chatId : changedChatId) {
                sortTopics(chatId, true);
            }
        });
    }

    public void reloadTopics(long chatId) {
        reloadTopics(chatId, true);
    }

    public void reloadTopics(long chatId, boolean fromCache) {
        AndroidUtilities.runOnUIThread(() -> {
            getUserConfig().getPreferences().edit().remove("topics_end_reached_" + chatId).apply();
            topicsByChatId.remove(chatId);
            topicsMapByChatId.remove(chatId);
            endIsReached.delete(chatId);
            clearLoadingOffset(chatId);

            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null && chat.forum) {
                loadTopics(chatId, fromCache, LOAD_TYPE_PRELOAD);
            }
            sortTopics(chatId);
        });
    }

    public void databaseCleared() {
        AndroidUtilities.runOnUIThread(() -> {
            topicsByChatId.clear();
            topicsMapByChatId.clear();
            endIsReached.clear();

            SharedPreferences.Editor editor = getUserConfig().getPreferences().edit();
            for (String key : getUserConfig().getPreferences().getAll().keySet()) {
                if (key.startsWith("topics_load_offset_message_id_")) {
                    editor.remove(key);
                }
                if (key.startsWith("topics_load_offset_date_")) {
                    editor.remove(key);
                }
                if (key.startsWith("topics_load_offset_topic_id_")) {
                    editor.remove(key);
                }
                if (key.startsWith("topics_end_reached_")) {
                    editor.remove(key);
                }
            }
            editor.apply();
        });
    }

    public void updateReadOutbox(HashMap<MessagesStorage.TopicKey, Integer> topicsReadOutbox) {
        AndroidUtilities.runOnUIThread(() -> {
            HashSet<Long> updatedChats = new HashSet<>();
            for (MessagesStorage.TopicKey topicKey : topicsReadOutbox.keySet()) {
                int value = topicsReadOutbox.get(topicKey);
                TLRPC.TL_forumTopic topic = findTopic(-topicKey.dialogId, topicKey.topicId);
                if (topic != null) {
                    topic.read_outbox_max_id = Math.max(topic.read_outbox_max_id, value);
                    updatedChats.add(-topicKey.dialogId);
                    if (topic.topMessage != null && topic.read_outbox_max_id >= topic.topMessage.id) {
                        topic.topMessage.unread = false;
                    }
                }
            }
            //TODO topics
            // optimize move to mask update
            for (Long chatId : updatedChats) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.topicsDidLoaded, chatId, true);
            }
        });
    }

    public void updateTopicInUi(long dialogId, TLRPC.TL_forumTopic forumTopic, int flags) {
        TLRPC.TL_forumTopic topic = findTopic(-dialogId, forumTopic.id);
        if (topic != null) {
            if ((flags & TOPIC_FLAG_TITLE) != 0) {
                topic.title = forumTopic.title;
            }
            if ((flags & TOPIC_FLAG_ICON) != 0) {
                topic.icon_emoji_id = forumTopic.icon_emoji_id;
            }
            if ((flags & TOPIC_FLAG_CLOSE) != 0) {
                topic.closed = forumTopic.closed;
            }
            if ((flags & TOPIC_FLAG_PIN) != 0) {
                topic.pinned = forumTopic.pinned;
            }
            if ((flags & TOPIC_FLAG_HIDE) != 0) {
                topic.hidden = forumTopic.hidden;
            }
            sortTopics(-dialogId);
        }
    }

    public void processEditedMessages(LongSparseArray<ArrayList<MessageObject>> editingMessagesFinal) {
        HashSet<Long> changedChatId = new HashSet<>();
        for (int i = 0; i < editingMessagesFinal.size(); i++) {
            ArrayList<MessageObject> messageObjects = editingMessagesFinal.valueAt(i);
            for (int j = 0; j < messageObjects.size(); j++) {
                TLRPC.TL_forumTopic topic = topicsByTopMsgId.get(messageHash(messageObjects.get(j).getId(), -messageObjects.get(j).getDialogId()));
                if (topic != null) {
                    topic.topMessage = messageObjects.get(j).messageOwner;
                    changedChatId.add(-messageObjects.get(j).getDialogId());
                }
            }
        }
        for (Long chatId : changedChatId) {
            sortTopics(chatId, true);
        }
    }

    public void processEditedMessage(TLRPC.Message newMsg) {
        TLRPC.TL_forumTopic topic = topicsByTopMsgId.get(messageHash(newMsg.id, -newMsg.dialog_id));
        if (topic != null) {
            topic.topMessage = newMsg;
            sortTopics(-newMsg.dialog_id, true);
        }
    }

    public void loadTopic(long chatId, long topicId, Runnable runnable) {
        getMessagesStorage().loadTopics(-chatId, topics -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("loaded from cache " + chatId + " topics_count=" + (topics == null ? 0 : topics.size()));
                }

                processTopics(chatId, topics, null, true, LOAD_TYPE_PRELOAD, -1);
                sortTopics(chatId);
                if (findTopic(chatId, topicId) != null) {
                    runnable.run();
                } else {
                    ArrayList<TLRPC.TL_forumTopic> topicToReload = new ArrayList<>();
                    TLRPC.TL_forumTopic topic = new TLRPC.TL_forumTopic();
                    topic.id = (int) topicId;
                    reloadTopics(chatId, topicToReload, runnable);
                }
            });
        });
    }

    private class TopicsLoadOffset {
        int lastMessageId;
        int lastMessageDate;
        int lastTopicId;
    }

    public static class TopicUpdate {
        public int totalMessagesCount = -1;
        long dialogId;
        long topicId;
        int unreadMentions;
        int unreadCount;
        int topMessageId;
        TLRPC.Message topMessage;
        ArrayList<MessageObject> groupedMessages;
        boolean reloadTopic;
        boolean onlyCounters;
    }


    public void onTopicFragmentResume(long chatId) {
        int v = openedTopicsBuChatId.get(chatId, 0);
        openedTopicsBuChatId.put(chatId, v + 1);
        sortTopics(chatId);
    }

    public void onTopicFragmentPause(long chatId) {
        int v = openedTopicsBuChatId.get(chatId, 0);
        v--;
        if (v < 0) {
            v = 0;
        }
        openedTopicsBuChatId.put(chatId, v);
    }

    public void getTopicRepliesCount(long dialogId, long topicId) {
        TLRPC.TL_forumTopic topic = findTopic(-dialogId, topicId);
        if (topic != null) {
            if (topic.totalMessagesCount == 0) {
                TLRPC.TL_messages_getReplies req = new TLRPC.TL_messages_getReplies();
                req.peer = getMessagesController().getInputPeer(dialogId);
                req.msg_id = (int) topicId;
                req.limit = 1;
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response != null) {
                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                        topic.totalMessagesCount = messages.count;
                        getMessagesStorage().updateTopicData(dialogId, topic, TOPIC_FLAG_TOTAL_MESSAGES_COUNT);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.topicsDidLoaded, -dialogId, true);
                    }
                }));
            }
        }
    }
}
