package org.telegram.ui.Stories;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.collection.LongSparseArray;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

//TODO stories
//support deleting story files
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
            ArrayList<TLRPC.TL_userStories> userStoriesArray = new ArrayList<>();
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
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
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, local_path, local_thumb_path FROM stories WHERE dialog_id = %d", dialogId));
                    ArrayList<TLRPC.StoryItem> storyItems = new ArrayList<>();
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        String path = cursor.stringValue(1);
                        String firstFramePath = cursor.stringValue(2);
                        if (data != null) {
                            TLRPC.StoryItem storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                            storyItem.attachPath = path;
                            storyItem.firstFramePath = firstFramePath;
                            storyItems.add(storyItem);
                            data.reuse();
                        }
                    }
                    cursor.dispose();
                    cursor = null;
                    TLRPC.TL_userStories userStories;
                    userStories = new TLRPC.TL_userStories();
                    userStories.stories = storyItems;
                    userStories.max_read_id = maxReadId;
                    userStories.user_id = dialogId;
                    userStoriesArray.add(userStories);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            TLRPC.TL_stories_allStories storiesResponse = new TLRPC.TL_stories_allStories();
            storiesResponse.user_stories = userStoriesArray;
            storiesResponse.users = storage.getUsers(usersToLoad);
            for (int i = 0; i < storiesResponse.user_stories.size(); i++) {
                TLRPC.TL_userStories userStories = storiesResponse.user_stories.get(i);
                checkExpiredStories(userStories.user_id, userStories.stories);
                if (userStories.stories.isEmpty()) {
                    storiesResponse.user_stories.remove(i);
                    i--;
                }
                Collections.sort(userStories.stories, StoriesController.storiesComparator);
            }
            Collections.sort(storiesResponse.user_stories, Comparator.comparingInt(o -> -o.stories.get(o.stories.size() - 1).date));

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
            if (currentTime - stories.get(i).date > EXPIRE_AFTER) {
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
                e.printStackTrace();
            }
            ArrayList<TLRPC.StoryItem> finalStoriesToDelete = storiesToDelete;
        }

    }

    public void putStoriesInternal(long dialogId, TLRPC.TL_userStories userStories) {
        SQLiteDatabase database = storage.getDatabase();
        try {
            if (userStories != null) {
                ArrayList<TLRPC.StoryItem> storyItems = userStories.stories;
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO stories VALUES(?, ?, ?, ?, ?)");
                for (int i = 0; i < storyItems.size(); i++) {
                    state.requery();
                    TLRPC.StoryItem storyItem = storyItems.get(i);
                    if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT local_path, local_thumb_path FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyItem.id));
                        if (cursor.next()) {
                            storyItem.attachPath = cursor.stringValue(1);
                            storyItem.firstFramePath = cursor.stringValue(2);
                        }
                        cursor.dispose();
                    }
                    if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
                        FileLog.e("try write deleted story");
                        continue;
                    }
                    state.bindLong(1, dialogId);
                    state.bindLong(2, storyItem.id);

                    NativeByteBuffer data = new NativeByteBuffer(storyItem.getObjectSize());
                    storyItem.serializeToStream(data);
                    state.bindByteBuffer(3, data);
                    if (storyItem.attachPath == null) {
                        state.bindNull(4);
                    } else {
                        state.bindString(4, storyItem.attachPath);
                    }
                    if (storyItem.firstFramePath == null) {
                        state.bindNull(5);
                    } else {
                        state.bindString(5, storyItem.firstFramePath);
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
            SQLitePreparedStatement state = database.executeFast("REPLACE INTO stories VALUES(?, ?, ?, ?, ?)");
            if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT local_path, local_thumb_path FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyItem.id));
                if (cursor.next()) {
                    storyItem.attachPath = cursor.stringValue(1);
                    storyItem.firstFramePath = cursor.stringValue(2);
                }
                cursor.dispose();
            }
            if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
                FileLog.e("putStoryInternal: try write deleted story");
                return;
            }
            state.bindLong(1, dialogId);
            state.bindLong(2, storyItem.id);

            NativeByteBuffer data = new NativeByteBuffer(storyItem.getObjectSize());
            storyItem.serializeToStream(data);
            state.bindByteBuffer(3, data);
            if (storyItem.attachPath == null) {
                state.bindNull(4);
            } else {
                state.bindString(4, storyItem.attachPath);
            }
            if (storyItem.firstFramePath == null) {
                state.bindNull(5);
            } else {
                state.bindString(5, storyItem.firstFramePath);
            }
            state.step();
            data.reuse();
            state.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void saveAllStories(ArrayList<TLRPC.TL_userStories> user_stories, boolean isNext, boolean hidden, Runnable callback) {
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            for (int i = 0; i < user_stories.size(); i++) {
                TLRPC.TL_userStories stories = user_stories.get(i);
                fillSkippedStories(stories.user_id, stories);
            }
            if (!isNext) {
                try {
                    SQLiteCursor cursor = database.queryFinalized("SELECT dialog_id FROM stories");

                    ArrayList<Long> dialogsToDelete = new ArrayList<>();
                    while (cursor.next()) {
                        long dialogId = cursor.longValue(1);
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        if (user == null) {
                            user = MessagesStorage.getInstance(currentAccount).getUser(dialogId);
                        }
                        if (user == null || user.stories_hidden == hidden && !dialogsToDelete.contains(dialogId)) {
                            dialogsToDelete.add(dialogId);
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
                TLRPC.TL_userStories stories = user_stories.get(i);
                putStoriesInternal(stories.user_id, stories);
            }
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback);
            }
        });
    }

    private void fillSkippedStories(long user_id, TLRPC.TL_userStories userStories) {
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
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, local_path, local_thumb_path FROM stories WHERE dialog_id = %d AND story_id = %d", user_id, storyId));

            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                String path = cursor.stringValue(1);
                String thumbPath = cursor.stringValue(2);
                if (data != null) {
                    storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                    storyItem.dialogId = user_id;
                    storyItem.attachPath = path;
                    storyItem.firstFramePath = thumbPath;
                    data.reuse();
                }
            }
            cursor.dispose();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        return storyItem;
    }


    public void getStories(long dialogId, Consumer<TLRPC.TL_userStories> consumer) {
        storage.getStorageQueue().postRunnable(() -> {
            TLRPC.TL_userStories finalUserStories = getStoriesInternal(dialogId);
            AndroidUtilities.runOnUIThread(() -> consumer.accept(finalUserStories));
        });
    }

    private TLRPC.TL_userStories getStoriesInternal(long dialogId) {
        SQLiteDatabase database = storage.getDatabase();
        SQLiteCursor cursor = null;
        TLRPC.TL_userStories userStories = null;
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

            cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, local_path, local_thumb_path FROM stories WHERE dialog_id = %d", dialogId));
            ArrayList<TLRPC.StoryItem> storyItems = new ArrayList<>();
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                String path = cursor.stringValue(1);
                String thumbPath = cursor.stringValue(2);
                if (data != null) {
                    TLRPC.StoryItem storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                    storyItem.attachPath = path;
                    storyItem.firstFramePath = thumbPath;
                    storyItems.add(storyItem);
                }
                data.reuse();
            }
            cursor.dispose();
            cursor = null;
            userStories = new TLRPC.TL_userStories();
            userStories.max_read_id = maxReadId;
            userStories.stories = storyItems;
            userStories.user_id = dialogId;

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
        storage.getStorageQueue().postRunnable(() -> {
            updateStoryItemInternal(dialogId, storyItem);
        });
    }

    private void updateStoryItemInternal(long dialogId, TLRPC.StoryItem storyItem) {
        if (storyItem instanceof TLRPC.TL_storyItemDeleted) {
            FileLog.e("StoriesStorage: try write deleted story");
        }
        if (StoriesUtilities.isExpired(currentAccount, storyItem)) {
            FileLog.e("StoriesStorage: try write expired story");
        }
        SQLiteDatabase database = storage.getDatabase();
        SQLitePreparedStatement state;
        try {
            String attachPath = storyItem.attachPath;
            String thumbPath = storyItem.firstFramePath;
            if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT local_path, local_thumb_path FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyItem.id));
                if (cursor.next()) {
                    attachPath = cursor.stringValue(1);
                    thumbPath = cursor.stringValue(2);
                }
                cursor.dispose();
            }
            state = database.executeFast("REPLACE INTO stories VALUES(?, ?, ?, ?, ?)");
            state.requery();

            state.bindLong(1, dialogId);
            state.bindLong(2, storyItem.id);

            NativeByteBuffer data = new NativeByteBuffer(storyItem.getObjectSize());
            storyItem.serializeToStream(data);
            state.bindByteBuffer(3, data);
            if (attachPath == null) {
                state.bindNull(4);
            } else {
                state.bindString(4, attachPath);
            }
            if (thumbPath == null) {
                state.bindNull(5);
            } else {
                state.bindString(5, thumbPath);
            }
            state.step();
            data.reuse();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void updateMaxReadId(long dialogId, int max_read_id) {
        TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
        if (userFull != null && userFull.stories != null) {
            userFull.stories.max_read_id = max_read_id;
            storage.updateUserInfo(userFull, false);
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
                long dialogId = updateStory.user_id;
                int count = 0;
                int storyId = updateStory.story.id;
                boolean storyExist = false;
                if (updateStory.story instanceof TLRPC.TL_storyItemDeleted) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, local_path, local_thumb_path FROM stories WHERE dialog_id = %d AND story_id = %d", dialogId, storyId));
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        String path = cursor.stringValue(1);
                        String thumbPath = cursor.stringValue(2);
                        if (data != null) {
                            TLRPC.StoryItem storyItem = TLRPC.StoryItem.TLdeserialize(data, data.readInt32(true), true);
                            storyItem.attachPath = path;
                            storyItem.firstFramePath = thumbPath;
                            data.reuse();
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

    public void updateStories(TLRPC.TL_userStories currentStories) {
        storage.getStorageQueue().postRunnable(() -> {
            for (int i = 0; i < currentStories.stories.size(); i++) {
                updateStoryItemInternal(currentStories.user_id, currentStories.stories.get(i));
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
    public void fillMessagesWithStories(LongSparseArray<ArrayList<MessageObject>> messagesWithUnknownStories, Runnable runnable) {
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
                request.user_id = MessagesController.getInstance(currentAccount).getInputUser(dialogId);
                for (int j = 0; j < messageObjects.size(); j++) {
                    request.id.add(getStoryId(messageObjects.get(j)));
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
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
            mediaStoryFull.user_id = messageObject.messageOwner.media.user_id;
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

    public void putUserStories(TLRPC.TL_userStories userStories) {
        storage.getStorageQueue().postRunnable(() -> {
            putStoriesInternal(userStories.user_id, userStories);
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
