/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.query;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

@SuppressWarnings("unchecked")
public class MessagesSearchQuery {

    private static int reqId;
    private static int lastReqId;
    private static boolean messagesSearchEndReached[] = new boolean[] {false, false};
    private static ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private static String lastSearchQuery;
    private static int lastReturnedNum;

    private static int getMask() {
        int mask = 0;
        if (lastReturnedNum < searchResultMessages.size() - 1 || !messagesSearchEndReached[0] || !messagesSearchEndReached[1]) {
            mask |= 1;
        }
        if (lastReturnedNum > 0) {
            mask |= 2;
        }
        return mask;
    }

    public static void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, int direction) {
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        int max_id = 0;
        long queryWithDialog = dialog_id;
        if (query == null || query.length() == 0) {
            if (direction == 1) {
                lastReturnedNum++;
                if (lastReturnedNum < searchResultMessages.size()) {
                    MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId());
                    return;
                } else {
                    if (messagesSearchEndReached[0] && mergeDialogId == 0 || messagesSearchEndReached[1]) {
                        lastReturnedNum--;
                        return;
                    }
                    if (searchResultMessages.isEmpty()) {
                        return;
                    }
                    query = lastSearchQuery;
                    MessageObject messageObject = searchResultMessages.get(searchResultMessages.size() - 1);
                    if (messageObject.getDialogId() == dialog_id && !messagesSearchEndReached[0]) {
                        max_id = messageObject.getId();
                        queryWithDialog = dialog_id;
                    } else {
                        if (messageObject.getDialogId() == mergeDialogId) {
                            max_id = messageObject.getId();
                        }
                        queryWithDialog = mergeDialogId;
                        messagesSearchEndReached[1] = false;
                    }
                }
            } else if (direction == 2) {
                lastReturnedNum--;
                if (lastReturnedNum < 0) {
                    lastReturnedNum = 0;
                    return;
                }
                if (lastReturnedNum >= searchResultMessages.size()) {
                    lastReturnedNum = searchResultMessages.size() - 1;
                }
                MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId());
                return;
            } else {
                return;
            }
        }
        if (messagesSearchEndReached[0] && !messagesSearchEndReached[1] && mergeDialogId != 0) {
            queryWithDialog = mergeDialogId;
        }
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 21;
        int lower_part = (int) queryWithDialog;
        req.peer = MessagesController.getInputPeer(lower_part);
        if (req.peer == null) {
            return;
        }
        req.q = query;
        req.max_id = max_id;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        lastSearchQuery = query;
        final long queryWithDialogFinal = queryWithDialog;
        reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        reqId = 0;
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                if (req.max_id == 0 && queryWithDialogFinal == dialog_id) {
                                    lastReturnedNum = 0;
                                    searchResultMessages.clear();
                                }
                                boolean added = false;
                                for (int a = 0; a < Math.min(res.messages.size(), 20); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    added = true;
                                    searchResultMessages.add(new MessageObject(message, null, false));
                                }
                                messagesSearchEndReached[queryWithDialogFinal == dialog_id ? 0 : 1] = res.messages.size() != 21;
                                if (mergeDialogId == 0) {
                                    messagesSearchEndReached[1] = messagesSearchEndReached[0];
                                }
                                if (searchResultMessages.isEmpty()) {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, getMask(), (long) 0);
                                } else {
                                    if (added) {
                                        if (lastReturnedNum >= searchResultMessages.size()) {
                                            lastReturnedNum = searchResultMessages.size() - 1;
                                        }
                                        MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId());
                                    }
                                }
                                if (queryWithDialogFinal == dialog_id && messagesSearchEndReached[0] && mergeDialogId != 0) {
                                    messagesSearchEndReached[1] = false;
                                    searchMessagesInChat(lastSearchQuery, dialog_id, mergeDialogId, guid, 0);
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }
}
