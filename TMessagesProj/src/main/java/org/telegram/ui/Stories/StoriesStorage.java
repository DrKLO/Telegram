package org.telegram.ui.Stories;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StoriesStorage {

    private static final int EXPIRE_AFTER = 60 * 60 * 24;//one day
    int currentAccount;
    MessagesStorage storage;

    public StoriesStorage(int currentAccount) {
        this.currentAccount = currentAccount;
        storage = MessagesStorage.getInstance(currentAccount);

    }

    public void getAllStories(Consumer<TLRPC.TL_stories_allStories> consumer) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            SQLiteCursor cursor = null;
            ArrayList<TLRPC.PeerStories> userStoriesArray = new ArrayList<>();
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            boolean failed = false;
            try {
                cursor = database.queryFinalized("SELECT dialog_id, max_read FROM stories_counter");
                LongSparseIntArray dialogsCounter = new LongSparseIntArray();

                while (cursor.next()) {
                    long dialogId = cursor.longValue(0);
                    int maxReadId = cursor.intValue(1);
                    dialogsCounter.put(dialogId, maxReadId);
                    if (dialogId > 0) {
                        usersToLoad.add(dialogId);
                    } else {
                        chatsToLoad.add(dialogId);
                    }
                }
                cursor.dispose();
                cursor = null;

                for (int i = 0; i < dialogsCounter.size(); i++) {
                    long dialogId = dialogsCounter.keyAt(i);
                    int maxReadId = dialogsCounter.valueAt(i);
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, custom_params FROM stories WHERE dialog_id = %d", dialogId));
                    ArrayList<TLRPC.StoryItem> storyItems = new ArrayList<>();
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        NativeByteBuffer customData = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.StoryItem storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                            storyItem.dialogId = dialogId;
                            StoryCustomParamsHelper.readLocalParams(storyItem, customData);
                            storyItems.add(storyItem);
                            data.reuse();
                        }
                        if (customData != null) {
                            customData.reuse();
                        }
                    }
                    cursor.dispose();
                    cursor = null;
                    TLRPC.PeerStories userStories;
                    userStories = new TLRPC.TL_peerStories();
                    userStories.stories = storyItems;
                    userStories.max_read_id = maxReadId;
                    userStories.peer = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                    userStoriesArray.add(userStories);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                failed = true;
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            if (failed) {
                AndroidUtilities.runOnUIThread(() -> consumer.accept(null));
                return;
            }
            TLRPC.TL_stories_allStories storiesResponse = new TLRPC.TL_stories_allStories();
            storiesResponse.peer_stories = userStoriesArray;
            storiesResponse.users = storage.getUsers(usersToLoad);
            storiesResponse.chats = storage.getChats(chatsToLoad);
            for (int i = 0; i < storiesResponse.peer_stories.size(); i++) {
                TLRPC.PeerStories userStories = storiesResponse.peer_stories.get(i);
                long dialogId = DialogObject.getPeerDialogId(userStories.peer);
                checkExpiredStories(dialogId, userStories.stories);
                if (userStories.stories.isEmpty()) {
                    storiesResponse.peer_stories.remove(i);
                    i--;
                }

                Collections.sort(userStories.stories, StoriesController.storiesComparator);
            }
            Collections.sort(storiesResponse.peer_stories, Comparator.comparingInt(o -> -o.stories.get(o.stories.size() - 1).date));

            AndroidUtilities.runOnUIThread(() -> consumer.accept(storiesResponse));
        });
    }

    private void checkExpiredStories(long dialogId, ArrayList<TLRPC.StoryItem> stories) {
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        SQLiteDatabase database = storage.getDatabase();
        ArrayList<Integer> storiesToDeleteIds = null;
        ArrayList<TLRPC.StoryItem> storiesToDelete = null;
        for (int i = 0; i < stories.size(); i++) {
            TLRPC.StoryItem storyItem = stories.get(i);
            if (currentTime > stories.get(i).expire_date) {
                if (storiesToDeleteIds == null) {
                    storiesToDeleteIds = new ArrayList<>();
                    storiesToDelete = new ArrayList<>();
                }
                storiesToDeleteIds.add(storyItem.id);
                storiesToDelete.add(storyItem);
                stories.remove(i);
                i--;
            }
        }

        if (storiesToDelete != null) {
            String ids = TextUtils.join(", ", storiesToDeleteIds);
            try {
                database.executeFast(String.format(Locale.US, "DELETE FROM stories WHERE dialog_id = %d AND story_id IN (%s)", dialogId, ids)).stepThis().dispose();
            } catch (SQLiteException e) {
                FileLog.e(e);
            }
            ArrayList<TLRPC.StoryItem> finalStoriesToDelete = storiesToDelete;
        }

    }

    public void putStoriesInternal(long dialogId, TLRPC.PeerStories userStories) {
        SQLiteDatabase database = storage.getDatabase();
        try {
            if (userStories != null) {
                ArrayList<TLRPC.StoryItem> storyItems = userStories.stories;
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO stories VALUES(?, ?, ?, ?)");
                for (int i = 0; i < storyItems.size(); i++) {
                    state.requery();
                    TLRPC.StoryItem storyItem = storyItems.get(i);
                    if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
                        FileLog.e("try write deleted story");
                        continue;
                    }
                    state.bindLong(1, dialogId);
                    state.bindLong(2, storyItem.id);

                    NativeByteBuffer data = new NativeByteBuffer(storyItem.getObjectSize());
                    storyItem.serializeToStream(data);
                    state.bindByteBuffer(3, data);
                    NativeByteBuffer nativeByteBuffer = StoryCustomParamsHelper.writeLocalParams(storyItem);
                    if (nativeByteBuffer != null) {
                        state.bindByteBuffer(4, nativeByteBuffer);
                    } else {
                        state.bindNull(4);
                    }
                    if (nativeByteBuffer != null) {
                        nativeByteBuffer.reuse();
                    }
                    state.step();
                    data.reuse();
                }
                state.dispose();
                database.executeFast(String.format(Locale.US, "REPLACE INTO stories_counter VALUES(%d, %d, %d)", dialogId, 0, userStories.max_read_id)).stepThis().dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putStoryInternal(long dialogId, TLRPC.StoryItem storyItem) {
        SQLiteDatabase database = storage.getDatabase();
        try {
            SQLitePreparedStatement state = database.executeFast("REPLACE INTO stories VALUES(?, ?, ?, ?)");
            if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
                FileLog.e("putStoryInternal: try write deleted story");
                return;
            }
            state.bindLong(1, dialogId);
            state.bindLong(2, storyItem.id);

            NativeByteBuffer data = new NativeByteBuffer(storyItem.getObjectSize());
            storyItem.serializeToStream(data);
            state.bindByteBuffer(3, data);
            NativeByteBuffer nativeByteBuffer = StoryCustomParamsHelper.writeLocalParams(storyItem);
            if (nativeByteBuffer != null) {
                state.bindByteBuffer(4, nativeByteBuffer);
            } else {
                state.bindNull(4);
            }
            if (nativeByteBuffer != null) {
                nativeByteBuffer.reuse();
            }
            state.step();
            data.reuse();
            state.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void saveAllStories(ArrayList<TLRPC.PeerStories> user_stories, boolean isNext, boolean hidden, Runnable callback) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            for (int i = 0; i < user_stories.size(); i++) {
                TLRPC.PeerStories stories = user_stories.get(i);
                fillSkippedStories(DialogObject.getPeerDialogId(stories.peer), stories);
            }
            if (!isNext) {
                try {
                    SQLiteCursor cursor = database.queryFinalized("SELECT DISTINCT dialog_id FROM stories");

                    ArrayList<Long> dialogsToDelete = new ArrayList<>();
                    while (cursor.next()) {
                        long dialogId = cursor.longValue(0);
                        if (dialogId > 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                            if (user == null) {
                                user = MessagesStorage.getInstance(currentAccount).getUser(dialogId);
                            }
                            if (user == null || user.stories_hidden == hidden && !dialogsToDelete.contains(dialogId)) {
                                dialogsToDelete.add(dialogId);
                            }
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                            if (chat == null) {
                                chat = MessagesStorage.getInstance(currentAccount).getChat(-dialogId);
                            }
                            if (chat == null || chat.stories_hidden == hidden && !dialogsToDelete.contains(dialogId)) {
                                dialogsToDelete.add(dialogId);
                            }
                        }
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("StoriesStorage delete dialogs " + TextUtils.join(",", dialogsToDelete));
                    }
                    database.executeFast(String.format(Locale.US, "DELETE FROM stories WHERE dialog_id IN(%s)", TextUtils.join(",", dialogsToDelete))).stepThis().dispose();
                } catch (Throwable e) {
                    storage.checkSQLException(e);
                }
            }
            for (int i = 0; i < user_stories.size(); i++) {
                TLRPC.PeerStories stories = user_stories.get(i);
                putStoriesInternal(DialogObject.getPeerDialogId(stories.peer), stories);
            }
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback);
            }
        });
    }

    private void fillSkippedStories(long user_id, TLRPC.PeerStories userStories) {
        try {
            if (userStories != null) {
                ArrayList<TLRPC.StoryItem> storyItems = userStories.stories;
                for (int i = 0; i < storyItems.size(); i++) {
                    TLRPC.StoryItem storyItem = storyItems.get(i);
                    if (storyItem instanceof TLRPC.TL_storyItemSkipped) {
                        TLRPC.StoryItem storyInDatabase = getStoryInternal(user_id, storyItems.get(i).id);
                        if (storyInDatabase instanceof TLRPC.TL_storyItem) {
                            storyItems.set(i, storyInDatabase);
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private TLRPC.StoryItem getStoryInternal(long user_id, int storyId) {
        SQLiteDatabase database = storage.getDatabase();
        SQLiteCursor cursor = null;
        TLRPC.StoryItem storyItem = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, custom_params FROM stories WHERE dialog_id = %d AND story_id = %d", user_id, storyId));

            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                NativeByteBuffer customData = cursor.byteBufferValue(1);
                if (data != null) {
                    storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                    storyItem.dialogId = user_id;
                    data.reuse();
                }
                if (storyItem != null) {
                    StoryCustomParamsHelper.readLocalParams(storyItem, customData);
                }
                if (customData != null) {
                    customData.reuse();
                }
            }
            cursor.dispose();
        } catch (SQLiteException e) {
            FileLog.e(e);
        }
        return storyItem;
    }


    public void getStories(long dialogId, Consumer<TLRPC.PeerStories> consumer) {
        storage.getStorageQueue().postRunnable(() -> {
            TLRPC.PeerStories finalUserStories = getStoriesInternal(dialogId);
            AndroidUtilities.runOnUIThread(() -> consumer.accept(finalUserStories));
        });
    }

    private TLRPC.PeerStories getStoriesInternal(long dialogId) {
        SQLiteDatabase database = storage.getDatabase();
        SQLiteCursor cursor = null;
        TLRPC.PeerStories userStories = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT count, max_read FROM stories_counter WHERE dialog_id = %d", dialogId));
            int count = 0;
            int maxReadId = 0;
            while (cursor.next()) {
                count = cursor.intValue(1);
                maxReadId = cursor.intValue(2);
            }
            cursor.dispose();
            cursor = null;

            cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, custom_params FROM stories WHERE dialog_id = %d", dialogId));
            ArrayList<TLRPC.StoryItem> storyItems = new ArrayList<>();
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                NativeByteBuffer customData = cursor.byteBufferValue(1);
                if (data != null) {
                    TLRPC.StoryItem storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                    StoryCustomParamsHelper.readLocalParams(storyItem, customData);
                    storyItems.add(storyItem);
                    data.reuse();
                }
                if (customData != null) {
                    customData.reuse();
                }
            }
            cursor.dispose();
            cursor = null;
            userStories = new TLRPC.TL_peerStories();
            userStories.max_read_id = maxReadId;
            userStories.stories = storyItems;
            userStories.peer = MessagesController.getInstance(currentAccount).getPeer(dialogId);

        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return userStories;
    }

    public void updateStoryItem(long dialogId, TLRPC.StoryItem storyItem) {
        if (dialogId == 0) {
            return;
        }
        storage.getStorageQueue().postRunnable(() -> {
            updateStoryItemInternal(dialogId, storyItem);
        });
    }

    private void updateStoryItemInternal(long dialogId, TLRPC.StoryItem storyItem) {
        if (dialogId == 0 || storyItem == null) {
            return;
        }
        if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
            FileLog.e("StoriesStorage: try write deleted story");
        }
        if (StoriesUtilities.isExpired(currentAccount, storyItem)) {
            FileLog.e("StoriesStorage: try write expired story");
        }
        SQLiteDatabase database = storage.getDatabase();
        SQLitePreparedStatement state;
        try {
            state = database.executeFast("REPLACE INTO stories VALUES(?, ?, ?, ?)");
            state.requery();

            state.bindLong(1, dialogId);
            state.bindLong(2, storyItem.id);

            NativeByteBuffer data = new NativeByteBuffer(storyItem.getObjectSize());
            storyItem.serializeToStream(data);
            state.bindByteBuffer(3, data);
            NativeByteBuffer nativeByteBuffer = StoryCustomParamsHelper.writeLocalParams(storyItem);
            if (nativeByteBuffer != null) {
                state.bindByteBuffer(4, nativeByteBuffer);
            } else {
                state.bindNull(4);
            }
            if (nativeByteBuffer != null) {
                nativeByteBuffer.reuse();
            }
            state.step();
            data.reuse();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void updateMaxReadId(long dialogId, int max_read_id) {
        if (dialogId > 0) {
            TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            if (userFull != null && userFull.stories != null) {
                userFull.stories.max_read_id = max_read_id;
                storage.updateUserInfo(userFull, false);
            }
        } else {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            if (chatFull != null && chatFull.stories != null) {
                chatFull.stories.max_read_id = max_read_id;
                storage.updateChatInfo(chatFull, false);
            }
        }
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            try {
                database.executeFast(String.format(Locale.US, "REPLACE INTO stories_counter VALUES(%d, 0, %d)", dialogId, max_read_id)).stepThis().dispose();
            } catch (Throwable e) {
                storage.checkSQLException(e);
            }
        });
    }

    public void processUpdate(TLRPC.TL_updateStory updateStory) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            SQLiteCursor cursor = null;
            try {
                long dialogId = DialogObject.getPeerDialogId(updateStory.peer);
                int count = 0;
                int storyId = updateStory.story.id;
                boolean storyExist = false;
                if (updateStory.story instanceof TLRPC.TL_storyItemDeleted) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, custom_params FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyId));
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        NativeByteBuffer customData = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.StoryItem storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                            StoryCustomParamsHelper.readLocalParams(storyItem, customData);
                            data.reuse();
                        }
                        if (customData != null) {
                            customData.reuse();
                        }
                        storyExist = true;
                    }
                    cursor.dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyId)).stepThis().dispose();
                    if (storyExist) {
                        count--;
                    }
                } else if (updateStory.story instanceof TLRPC.TL_storyItem) {
                    updateStoryItemInternal(dialogId, updateStory.story);
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT story_id FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyId));
                    if (cursor.next()) {
                        storyExist = true;
                    }
                    cursor.dispose();
                    if (!storyExist) {
                        count++;
                    }
                }
                cursor = database.queryFinalized("SELECT count, max_read FROM stories_counter WHERE dialog_id = " + dialogId);
                int totalCount = 0;
                if (cursor.next()) {
                    totalCount = cursor.intValue(1);
                }
                cursor.dispose();
                cursor = null;
                database.executeFast(String.format(Locale.US, "UPDATE stories_counter SET count = %d WHERE dialog_id = %d",
                        totalCount + count,
                        dialogId)
                ).stepThis().dispose();
            } catch (Throwable e) {
                storage.checkSQLException(e);
            }
        });
    }

    public void updateStories(TLRPC.PeerStories currentStories) {
        storage.getStorageQueue().postRunnable(() -> {
            for (int i = 0; i < currentStories.stories.size(); i++) {
                updateStoryItemInternal(DialogObject.getPeerDialogId(currentStories.peer), currentStories.stories.get(i));
            }
        });
    }

    public void deleteStory(long dialogId, int storyId) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            try {
                database.executeFast(String.format(Locale.US, "DELETE FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyId)).stepThis().dispose();
            } catch (Throwable e) {
                storage.checkSQLException(e);
            }
        });
    }

    public void deleteStories(long dialogId, ArrayList<Integer> storyIds) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            try {
                String ids = TextUtils.join(", ", storyIds);
                database.executeFast(String.format(Locale.US, "DELETE FROM stories WHERE dialog_id = %d AND story_id IN (%s)", dialogId, ids)).stepThis().dispose();
            } catch (Throwable e) {
                storage.checkSQLException(e);
            }
        });
    }

    //storage queue
    public void fillMessagesWithStories(LongSparseArray<ArrayList<MessageObject>> messagesWithUnknownStories, Runnable runnable, int classGuid) {
        if (runnable == null) {
            return;
        }
        if (messagesWithUnknownStories == null) {
            runnable.run();
            return;
        }
        ArrayList<MessageObject> updatedMessages = new ArrayList<>();
        for (int i = 0; i < messagesWithUnknownStories.size(); i++) {
            long dialogId = messagesWithUnknownStories.keyAt(i);
            ArrayList<MessageObject> messageObjects = messagesWithUnknownStories.valueAt(i);
            for (int j = 0; j < messageObjects.size(); j++) {
                MessageObject messageObject = messageObjects.get(j);
                int storyId = getStoryId(messageObject);
                TLRPC.StoryItem storyItem = getStoryInternal(dialogId, storyId);
                if (storyItem != null && !(storyItem instanceof TLRPC.TL_storyItemSkipped)) {
                    applyStory(currentAccount, dialogId, messageObject, storyItem);

                    updatedMessages.add(messageObject);
                    messageObjects.remove(j);
                    j--;
                    if (messageObjects.isEmpty()) {
                        messagesWithUnknownStories.removeAt(i);
                        i--;
                    }
                }
            }
        }

        updateMessagesWithStories(updatedMessages);

        if (!messagesWithUnknownStories.isEmpty()) {
            int requestsCount[] = new int[]{messagesWithUnknownStories.size()};
            for (int i = 0; i < messagesWithUnknownStories.size(); i++) {
                long dialogId = messagesWithUnknownStories.keyAt(i);
                ArrayList<MessageObject> messageObjects = messagesWithUnknownStories.valueAt(i);
                TLRPC.TL_stories_getStoriesByID request = new TLRPC.TL_stories_getStoriesByID();
                request.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                for (int j = 0; j < messageObjects.size(); j++) {
                    request.id.add(getStoryId(messageObjects.get(j)));
                }
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                    if (response != null) {
                        TLRPC.TL_stories_stories stories = (TLRPC.TL_stories_stories) response;
                        for (int j = 0; j < messageObjects.size(); j++) {
                            MessageObject messageObject = messageObjects.get(j);
                            boolean found = false;
                            for (int k = 0; k < stories.stories.size(); k++) {
                                if (stories.stories.get(k).id == getStoryId(messageObject)) {
                                    applyStory(currentAccount, dialogId, messageObject, stories.stories.get(k));
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                TLRPC.TL_storyItemDeleted storyItemDeleted = new TLRPC.TL_storyItemDeleted();
                                storyItemDeleted.id = getStoryId(messageObject);
                                applyStory(currentAccount, dialogId, messageObject, storyItemDeleted);
                            }
                            storage.getStorageQueue().postRunnable(() -> {
                                updateMessagesWithStories(messageObjects);
                            });
                        }
                    }
                    requestsCount[0]--;
                    if (requestsCount[0] == 0) {
                        runnable.run();
                    }
                });
                if (classGuid != 0) {
                    ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
                }
            }
        } else {
            runnable.run();
        }
    }

    public static void applyStory(int currentAccount, long dialogId, MessageObject messageObject, TLRPC.StoryItem storyItem) {
        if (messageObject.messageOwner.reply_to instanceof TLRPC.TL_messageReplyStoryHeader && messageObject.messageOwner.reply_to.story_id == storyItem.id) {
            messageObject.messageOwner.replyStory = checkExpiredStateLocal(currentAccount, dialogId, storyItem);
        }
        if (messageObject.type == MessageObject.TYPE_STORY || messageObject.type == MessageObject.TYPE_STORY_MENTION) {
            MessageMediaStoryFull mediaStoryFull = new MessageMediaStoryFull();
            mediaStoryFull.user_id = DialogObject.getPeerDialogId(messageObject.messageOwner.media.peer);
            mediaStoryFull.peer = messageObject.messageOwner.media.peer;
            mediaStoryFull.id = messageObject.messageOwner.media.id;
            mediaStoryFull.storyItem = checkExpiredStateLocal(currentAccount, dialogId, storyItem);
            mediaStoryFull.via_mention = messageObject.messageOwner.media.via_mention;
            messageObject.messageOwner.media = mediaStoryFull;
        }
        if (
            messageObject.messageOwner.media != null &&
            messageObject.messageOwner.media.webpage != null &&
            messageObject.messageOwner.media.webpage.attributes != null
        ) {
            for (int i = 0; i < messageObject.messageOwner.media.webpage.attributes.size(); ++i) {
                TLRPC.WebPageAttribute attr = messageObject.messageOwner.media.webpage.attributes.get(i);
                if (attr instanceof TLRPC.TL_webPageAttributeStory && ((TLRPC.TL_webPageAttributeStory) attr).id == storyItem.id) {
                    attr.flags |= 1;
                    ((TLRPC.TL_webPageAttributeStory) attr).storyItem = checkExpiredStateLocal(currentAccount, dialogId, storyItem);
                }
            }
        }
    }

    private static int getStoryId(MessageObject messageObject) {
        if (messageObject.type == MessageObject.TYPE_STORY || messageObject.type == MessageObject.TYPE_STORY_MENTION) {
            return messageObject.messageOwner.media.id;
        } else if (
            messageObject.messageOwner.media != null &&
            messageObject.messageOwner.media.webpage != null &&
            messageObject.messageOwner.media.webpage.attributes != null
        ) {
            for (int i = 0; i < messageObject.messageOwner.media.webpage.attributes.size(); ++i) {
                TLRPC.WebPageAttribute attr = messageObject.messageOwner.media.webpage.attributes.get(i);
                if (attr instanceof TLRPC.TL_webPageAttributeStory) {
                    return ((TLRPC.TL_webPageAttributeStory) attr).id;
                }
            }
        }
        return messageObject.messageOwner.reply_to.story_id;
    }

    public void updateMessagesWithStories(List<MessageObject> updatedMessages) {
        try {
            SQLiteDatabase database = storage.getDatabase();
            if (!updatedMessages.isEmpty()) {
                SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET replydata = ? WHERE mid = ? AND uid = ?");
                SQLitePreparedStatement topicState = database.executeFast("UPDATE messages_topics SET replydata = ? WHERE mid = ? AND uid = ?");

                SQLitePreparedStatement state2 = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                SQLitePreparedStatement topicState2 = database.executeFast("UPDATE messages_topics SET data = ? WHERE mid = ? AND uid = ?");
                for (int i = 0; i < updatedMessages.size(); i++) {
                    MessageObject messageObject = updatedMessages.get(i);
                    for (int k = 0; k < 2; k++) {
                        if (messageObject.messageOwner.replyStory != null) {
                            SQLitePreparedStatement localState = k == 0 ? state : topicState;
                            if (localState == null) {
                                continue;
                            }
                            NativeByteBuffer data = new NativeByteBuffer(messageObject.messageOwner.replyStory.getObjectSize());
                            messageObject.messageOwner.replyStory.serializeToStream(data);
                            localState.requery();
                            localState.bindByteBuffer(1, data);
                            localState.bindInteger(2, messageObject.getId());
                            localState.bindLong(3, messageObject.getDialogId());
                            localState.step();
                        } else {
                            SQLitePreparedStatement localState = k == 0 ? state2 : topicState2;
                            if (localState == null) {
                                continue;
                            }
                            NativeByteBuffer data = new NativeByteBuffer(messageObject.messageOwner.getObjectSize());
                            messageObject.messageOwner.serializeToStream(data);
                            localState.requery();
                            localState.bindByteBuffer(1, data);
                            localState.bindInteger(2, messageObject.getId());
                            localState.bindLong(3, messageObject.getDialogId());
                            localState.step();
                        }
                    }
                }

                state.dispose();
                topicState.dispose();
                state2.dispose();
                topicState2.dispose();
            }
        } catch (Throwable e) {
            storage.checkSQLException(e);
        }
    }

    public static TLRPC.StoryItem checkExpiredStateLocal(int currentAccount, long dialogId, TLRPC.StoryItem storyItem) {
        if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
            return storyItem;
        }
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        boolean expired = false;
        if (storyItem.expire_date > 0) {
            expired = currentTime > storyItem.expire_date;
        } else {
            expired = currentTime - storyItem.date > EXPIRE_AFTER;
        }
        if (!storyItem.pinned && expired && dialogId != 0 && dialogId != UserConfig.getInstance(currentAccount).clientUserId) {
            TLRPC.TL_storyItemDeleted storyItemDeleted = new TLRPC.TL_storyItemDeleted();
            storyItemDeleted.id = storyItem.id;
            return storyItemDeleted;
        }
        return storyItem;
    }

    public void getMaxReadIds(Consumer<LongSparseIntArray> consumer) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            SQLiteCursor cursor = null;
            LongSparseIntArray longSparseIntArray = new LongSparseIntArray();
            try {
                cursor = database.queryFinalized("SELECT dialog_id, max_read FROM stories_counter");
                while (cursor.next()) {
                    long dialogId = cursor.longValue(0);
                    int maxReadId = cursor.intValue(1);
                    longSparseIntArray.put(dialogId, maxReadId);
                }
            } catch (Exception e) {
                storage.checkSQLException(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                consumer.accept(longSparseIntArray);
            });
        });
    }

    public void putPeerStories(TLRPC.PeerStories userStories) {
        storage.getStorageQueue().postRunnable(() -> {
            putStoriesInternal(DialogObject.getPeerDialogId(userStories.peer), userStories);
        });
    }

    public void deleteAllUserStories(long dialogId) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            try {
                database.executeFast(String.format(Locale.US, "DELETE FROM stories WHERE dialog_id = %d", dialogId)).stepThis().dispose();
            } catch (Throwable e) {
                storage.checkSQLException(e);
            }
        });
    }
}
