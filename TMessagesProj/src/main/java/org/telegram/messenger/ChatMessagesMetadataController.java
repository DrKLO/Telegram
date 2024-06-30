package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Stories.StoriesStorage;

import java.util.ArrayList;

public class ChatMessagesMetadataController {

    final ChatActivity chatActivity;
    private ArrayList<MessageObject> reactionsToCheck = new ArrayList<>(10);
    private ArrayList<MessageObject> extendedMediaToCheck = new ArrayList<>(10);
    private ArrayList<MessageObject> storiesToCheck = new ArrayList<>(10);

    ArrayList<Integer> reactionsRequests = new ArrayList<>();
    ArrayList<Integer> extendedMediaRequests = new ArrayList<>();


    public ChatMessagesMetadataController(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
    }

    public void checkMessages(ChatActivity.ChatActivityAdapter chatAdapter, int maxAdapterPosition, int minAdapterPosition, long currentTime) {
        ArrayList<MessageObject> messages = chatAdapter.getMessages();
        if (!chatActivity.isInScheduleMode() && maxAdapterPosition >= 0 && minAdapterPosition >= 0) {
            int from = minAdapterPosition - chatAdapter.messagesStartRow - 10;
            int to = maxAdapterPosition - chatAdapter.messagesStartRow + 10;
            if (from < 0) {
                from = 0;
            }
            if (to > messages.size()) {
                to = messages.size();
            }
            reactionsToCheck.clear();
            extendedMediaToCheck.clear();
            storiesToCheck.clear();
            for (int i = from; i < to; i++) {
                MessageObject messageObject = messages.get(i);
                if (chatActivity.getThreadMessage() != messageObject && messageObject.getId() > 0 && messageObject.messageOwner.action == null && (currentTime - messageObject.reactionsLastCheckTime) > 15000L) {
                    messageObject.reactionsLastCheckTime = currentTime;
                    reactionsToCheck.add(messageObject);
                }
                if (chatActivity.getThreadMessage() != messageObject && messageObject.getId() > 0 && (messageObject.hasExtendedMediaPreview() || messageObject.hasPaidMediaPreview()) && (currentTime - messageObject.extendedMediaLastCheckTime) > 30000L) {
                    messageObject.extendedMediaLastCheckTime = currentTime;
                    extendedMediaToCheck.add(messageObject);
                }
                if (messageObject.type == MessageObject.TYPE_STORY || messageObject.type == MessageObject.TYPE_STORY_MENTION || messageObject.messageOwner.replyStory != null) {
                    TL_stories.StoryItem storyItem = messageObject.type == MessageObject.TYPE_STORY || messageObject.type == MessageObject.TYPE_STORY_MENTION ? messageObject.messageOwner.media.storyItem : messageObject.messageOwner.replyStory;
                    if (storyItem == null || storyItem instanceof TL_stories.TL_storyItemDeleted) {
                        continue;
                    }
                    if (currentTime - storyItem.lastUpdateTime > 1000 * 5 * 60) {
                        storyItem.lastUpdateTime = currentTime;
                        storiesToCheck.add(messageObject);
                    }
                }
            }
            loadReactionsForMessages(chatActivity.getDialogId(), reactionsToCheck);
            loadExtendedMediaForMessages(chatActivity.getDialogId(), extendedMediaToCheck);
            loadStoriesForMessages(chatActivity.getDialogId(), storiesToCheck);
        }
    }

    private void loadStoriesForMessages(long dialogId, ArrayList<MessageObject> visibleObjects) {
        if (visibleObjects.isEmpty()) {
            return;
        }
        for (int i = 0; i < visibleObjects.size(); i++) {
            TL_stories.TL_stories_getStoriesByID req = new TL_stories.TL_stories_getStoriesByID();
            MessageObject messageObject = visibleObjects.get(i);
            TL_stories.StoryItem storyItem = new TL_stories.TL_storyItem();
            if (messageObject.type == MessageObject.TYPE_STORY || messageObject.type == MessageObject.TYPE_STORY_MENTION) {
                storyItem = messageObject.messageOwner.media.storyItem;
                storyItem.dialogId = messageObject.messageOwner.media.user_id;
            } else if (messageObject.messageOwner.reply_to != null) {
                storyItem = messageObject.messageOwner.replyStory;
                storyItem.dialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.reply_to.peer);
            } else {
                continue;
            }
            long storyDialogId = storyItem.dialogId;
            req.peer = chatActivity.getMessagesController().getInputPeer(storyDialogId);
            req.id.add(storyItem.id);
            int storyId = storyItem.id;
            int reqId = chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> {
                TL_stories.StoryItem newStoryItem = null;
                if (response != null) {
                    TL_stories.TL_stories_stories stories = (TL_stories.TL_stories_stories) response;
                    if (stories.stories.size() > 0) {
                        newStoryItem = stories.stories.get(0);
                    }
                    if (newStoryItem == null) {
                        newStoryItem = new TL_stories.TL_storyItemDeleted();
                    }
                    newStoryItem.lastUpdateTime = System.currentTimeMillis();
                    newStoryItem.id = storyId;
                    TL_stories.StoryItem finalNewStoryItem = newStoryItem;
                    AndroidUtilities.runOnUIThread(() -> {
                        boolean wasExpired = messageObject.isExpiredStory();
                        StoriesStorage.applyStory(chatActivity.getCurrentAccount(), storyDialogId, messageObject, finalNewStoryItem);
                        ArrayList<MessageObject> messageObjects = new ArrayList<>();
                        messageObject.forceUpdate = true;
                        messageObjects.add(messageObject);
                        chatActivity.getMessagesStorage().getStorageQueue().postRunnable(() -> {
                            chatActivity.getMessagesController().getStoriesController().getStoriesStorage().updateMessagesWithStories(messageObjects);
                        });
                        if (!wasExpired && messageObject.isExpiredStory() && messageObject.type == MessageObject.TYPE_STORY_MENTION) {
                            chatActivity.updateMessages(messageObjects, true);
                        } else {
                            chatActivity.updateMessages(messageObjects, false);
                        }
                    });
                }
            });
            extendedMediaRequests.add(reqId);
        }
        if (extendedMediaRequests.size() > 10) {
            chatActivity.getConnectionsManager().cancelRequest(extendedMediaRequests.remove(0), false);
        }
    }

    public void loadReactionsForMessages(long dialogId, ArrayList<MessageObject> visibleObjects) {
        if (visibleObjects.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_getMessagesReactions req = new TLRPC.TL_messages_getMessagesReactions();
        req.peer = chatActivity.getMessagesController().getInputPeer(dialogId);
        for (int i = 0; i < visibleObjects.size(); i++) {
            MessageObject messageObject = visibleObjects.get(i);
            req.id.add(messageObject.getId());
        }
        int reqId = chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.Updates updates = (TLRPC.Updates) response;
                for (int i = 0; i < updates.updates.size(); i++) {
                    if (updates.updates.get(i) instanceof TLRPC.TL_updateMessageReactions) {
                        ((TLRPC.TL_updateMessageReactions) updates.updates.get(i)).updateUnreadState = false;
                    }
                }
                chatActivity.getMessagesController().processUpdates(updates, false);
            }
        });
        reactionsRequests.add(reqId);
        if (reactionsRequests.size() > 5) {
            chatActivity.getConnectionsManager().cancelRequest(reactionsRequests.remove(0), true);
        }
    }

    public void loadExtendedMediaForMessages(long dialogId, ArrayList<MessageObject> visibleObjects) {
        if (visibleObjects.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_getExtendedMedia req = new TLRPC.TL_messages_getExtendedMedia();
        req.peer = chatActivity.getMessagesController().getInputPeer(dialogId);
        for (int i = 0; i < visibleObjects.size(); i++) {
            MessageObject messageObject = visibleObjects.get(i);
            req.id.add(messageObject.getId());
        }
        int reqId = chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                chatActivity.getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
        });
        extendedMediaRequests.add(reqId);
        if (extendedMediaRequests.size() > 10) {
            chatActivity.getConnectionsManager().cancelRequest(extendedMediaRequests.remove(0), false);
        }
    }

    public void onFragmentDestroy() {
        for (int i = 0; i < reactionsRequests.size(); i++) {
            chatActivity.getConnectionsManager().cancelRequest(reactionsRequests.get(i), false);
        }
        reactionsRequests.clear();
        for (int i = 0; i < extendedMediaRequests.size(); i++) {
            chatActivity.getConnectionsManager().cancelRequest(extendedMediaRequests.get(i), false);
        }
        extendedMediaRequests.clear();
    }
}
