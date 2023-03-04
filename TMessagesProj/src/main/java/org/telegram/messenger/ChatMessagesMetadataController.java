package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

public class ChatMessagesMetadataController {

    final ChatActivity chatActivity;
    private ArrayList<MessageObject> reactionsToCheck = new ArrayList<>(10);
    private ArrayList<MessageObject> extendedMediaToCheck = new ArrayList<>(10);

    ArrayList<Integer> reactionsRequests = new ArrayList<>();
    ArrayList<Integer> extendedMediaRequests = new ArrayList<>();


    public ChatMessagesMetadataController(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
    }

    public void checkMessages(ChatActivity.ChatActivityAdapter chatAdapter, int maxAdapterPosition, int minAdapterPosition, long currentTime) {
        ArrayList<MessageObject> messages = chatActivity.messages;
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
            for (int i = from; i < to; i++) {
                MessageObject messageObject = messages.get(i);
                if (chatActivity.getThreadMessage() != messageObject && messageObject.getId() > 0 && messageObject.messageOwner.action == null && (currentTime - messageObject.reactionsLastCheckTime) > 15000L) {
                    messageObject.reactionsLastCheckTime = currentTime;
                    reactionsToCheck.add(messageObject);
                }
                if (chatActivity.getThreadMessage() != messageObject && messageObject.getId() > 0 && messageObject.hasExtendedMediaPreview() && (currentTime - messageObject.extendedMediaLastCheckTime) > 30000L) {
                    messageObject.extendedMediaLastCheckTime = currentTime;
                    extendedMediaToCheck.add(messageObject);
                }
            }
            loadReactionsForMessages(chatActivity.getDialogId(), reactionsToCheck);
            loadExtendedMediaForMessages(chatActivity.getDialogId(), extendedMediaToCheck);
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
            chatActivity.getConnectionsManager().cancelRequest(reactionsRequests.remove(0), false);
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
        if (extendedMediaRequests.size() > 5) {
            chatActivity.getConnectionsManager().cancelRequest(extendedMediaRequests.remove(0), false);
        }
    }

    public void onFragmentDestroy() {
        for (int i = 0; i < reactionsRequests.size(); i++) {
            chatActivity.getConnectionsManager().cancelRequest(reactionsRequests.remove(i), false);
        }
        reactionsRequests.clear();
        for (int i = 0; i < extendedMediaRequests.size(); i++) {
            chatActivity.getConnectionsManager().cancelRequest(extendedMediaRequests.remove(i), false);
        }
        extendedMediaRequests.clear();
    }
}
