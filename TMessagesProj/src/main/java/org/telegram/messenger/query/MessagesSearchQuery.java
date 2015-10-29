/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
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

public class MessagesSearchQuery {

    private static int reqId;
    private static int lastReqId;
    private static boolean messagesSearchEndReached;
    private static ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private static String lastSearchQuery;
    private static int lastReturnedNum;

    private static int getMask() {
        int mask = 0;
        if (lastReturnedNum < searchResultMessages.size() - 1) {
            mask |= 1;
        }
        if (lastReturnedNum > 0) {
            mask |= 2;
        }
        return mask;
    }

    public static void searchMessagesInChat(String query, long dialog_id, final int guid, int direction) {
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        int max_id = 0;
        if (query == null || query.length() == 0) {
            if (direction == 1) {
                lastReturnedNum++;
                if (lastReturnedNum < searchResultMessages.size()) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, searchResultMessages.get(lastReturnedNum).getId(), getMask());
                    return;
                } else {
                    if (messagesSearchEndReached) {
                        lastReturnedNum--;
                        return;
                    }
                    query = lastSearchQuery;
                    max_id = searchResultMessages.get(searchResultMessages.size() - 1).getId();
                }
            } else if (direction == 2) {
                lastReturnedNum--;
                if (lastReturnedNum < 0) {
                    lastReturnedNum = 0;
                    return;
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, searchResultMessages.get(lastReturnedNum).getId(), getMask());
                return;
            } else {
                return;
            }
        }
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 21;
        int lower_part = (int) dialog_id;
        req.peer = MessagesController.getInputPeer(lower_part);
        if (req.peer == null) {
            return;
        }
        req.q = query;
        req.max_id = max_id;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        lastSearchQuery = query;
        reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                if (req.max_id == 0) {
                                    lastReturnedNum = 0;
                                    searchResultMessages.clear();
                                }
                                boolean added = false;
                                for (int a = 0; a < Math.min(res.messages.size(), 20); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    added = true;
                                    searchResultMessages.add(new MessageObject(message, null, false));
                                }
                                messagesSearchEndReached = res.messages.size() != 21;
                                if (searchResultMessages.isEmpty()) {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, getMask());
                                } else {
                                    if (added) {
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, searchResultMessages.get(lastReturnedNum).getId(), getMask());
                                    }
                                }
                            }
                        }
                        reqId = 0;
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }
}
