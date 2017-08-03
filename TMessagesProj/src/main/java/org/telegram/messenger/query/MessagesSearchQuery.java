/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.query;

import android.text.TextUtils;

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
    private static int mergeReqId;
    private static long lastMergeDialogId;
    private static int lastReqId;
    private static int messagesSearchCount[] = new int[] {0, 0};
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

    public static void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, final int direction) {
        searchMessagesInChat(query, dialog_id, mergeDialogId, guid, direction, false);
    }

    private static void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, final int direction, final boolean internal) {
        int max_id = 0;
        long queryWithDialog = dialog_id;
        boolean firstQuery = !internal;
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        if (mergeReqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(mergeReqId, true);
            mergeReqId = 0;
        }
        if (TextUtils.isEmpty(query)) {
            if (searchResultMessages.isEmpty()) {
                return;
            }
            if (direction == 1) {
                lastReturnedNum++;
                if (lastReturnedNum < searchResultMessages.size()) {
                    MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                    return;
                } else {
                    if (messagesSearchEndReached[0] && mergeDialogId == 0 && messagesSearchEndReached[1]) {
                        lastReturnedNum--;
                        return;
                    }
                    firstQuery = false;
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
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                return;
            } else {
                return;
            }
        } else if (firstQuery) {
            messagesSearchEndReached[0] = messagesSearchEndReached[1] = false;
            messagesSearchCount[0] = messagesSearchCount[1] = 0;
            searchResultMessages.clear();
        }
        if (messagesSearchEndReached[0] && !messagesSearchEndReached[1] && mergeDialogId != 0) {
            queryWithDialog = mergeDialogId;
        }
        if (queryWithDialog == dialog_id && firstQuery) {
            if (mergeDialogId != 0) {
                TLRPC.InputPeer inputPeer = MessagesController.getInputPeer((int) mergeDialogId);
                if (inputPeer == null) {
                    return;
                }
                final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.peer = inputPeer;
                lastMergeDialogId = mergeDialogId;
                req.limit = 1;
                req.q = query;
                req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                mergeReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (lastMergeDialogId == mergeDialogId) {
                                    mergeReqId = 0;
                                    if (response != null) {
                                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                        messagesSearchEndReached[1] = res.messages.isEmpty();
                                        messagesSearchCount[1] = res instanceof TLRPC.TL_messages_messagesSlice ? res.count : res.messages.size();
                                        searchMessagesInChat(req.q, dialog_id, mergeDialogId, guid, direction, true);
                                    }
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
                return;
            } else {
                lastMergeDialogId = 0;
                messagesSearchEndReached[1] = true;
                messagesSearchCount[1] = 0;
            }
        }
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInputPeer((int) queryWithDialog);
        if (req.peer == null) {
            return;
        }
        req.limit = 21;
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
                        if (currentReqId == lastReqId) {
                            reqId = 0;
                            if (response != null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                if (req.max_id == 0 && queryWithDialogFinal == dialog_id) {
                                    lastReturnedNum = 0;
                                    searchResultMessages.clear();
                                    messagesSearchCount[0] = 0;
                                }
                                boolean added = false;
                                for (int a = 0; a < Math.min(res.messages.size(), 20); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    added = true;
                                    searchResultMessages.add(new MessageObject(message, null, false));
                                }
                                messagesSearchEndReached[queryWithDialogFinal == dialog_id ? 0 : 1] = res.messages.size() != 21;
                                messagesSearchCount[queryWithDialogFinal == dialog_id ? 0 : 1] = res instanceof TLRPC.TL_messages_messagesSlice ? res.count : res.messages.size();
                                if (searchResultMessages.isEmpty()) {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, getMask(), (long) 0, 0, 0);
                                } else {
                                    if (added) {
                                        if (lastReturnedNum >= searchResultMessages.size()) {
                                            lastReturnedNum = searchResultMessages.size() - 1;
                                        }
                                        MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                                    }
                                }
                                if (queryWithDialogFinal == dialog_id && messagesSearchEndReached[0] && mergeDialogId != 0 && !messagesSearchEndReached[1]) {
                                    searchMessagesInChat(lastSearchQuery, dialog_id, mergeDialogId, guid, 0, true);
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static String getLastSearchQuery() {
        return lastSearchQuery;
    }
}
