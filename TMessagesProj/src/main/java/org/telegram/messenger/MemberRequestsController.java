package org.telegram.messenger;

import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;

import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;

public class MemberRequestsController extends BaseController {

    private static final MemberRequestsController[] instances = new MemberRequestsController[UserConfig.MAX_ACCOUNT_COUNT];

    public static MemberRequestsController getInstance(int accountNum) {
        MemberRequestsController local = instances[accountNum];
        if (local == null) {
            synchronized (MemberRequestsController.class) {
                local = instances[accountNum];
                if (local == null) {
                    local = new MemberRequestsController(accountNum);
                    instances[accountNum] = local;
                }
            }
        }
        return local;
    }

    private final LongSparseArray<TLRPC.TL_messages_chatInviteImporters> firstImportersCache = new LongSparseArray<>();

    public MemberRequestsController(int accountNum) {
        super(accountNum);
    }

    @Nullable
    public TLRPC.TL_messages_chatInviteImporters getCachedImporters(long chatId) {
        return firstImportersCache.get(chatId);
    }

    public int getImporters(final long chatId, final String query, TLRPC.TL_chatInviteImporter lastImporter, LongSparseArray<TLRPC.User> users, RequestDelegate onComplete) {
        boolean isEmptyQuery = TextUtils.isEmpty(query);
        TLRPC.TL_messages_getChatInviteImporters req = new TLRPC.TL_messages_getChatInviteImporters();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
        req.requested = true;
        req.limit = 30;
        if (!isEmptyQuery) {
            req.q = query;
            req.flags |= 4;
        }
        if (lastImporter == null) {
            req.offset_user = new TLRPC.TL_inputUserEmpty();
        } else {
            req.offset_user = getMessagesController().getInputUser(users.get(lastImporter.user_id));
            req.offset_date = lastImporter.date;
        }
        return getConnectionsManager().sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_messages_chatInviteImporters importers = (TLRPC.TL_messages_chatInviteImporters) response;
                    if (lastImporter == null && isEmptyQuery)
                        firstImportersCache.put(chatId, importers);
                }
                onComplete.run(response, error);
            });
        });
    }

    public void onPendingRequestsUpdated(TLRPC.TL_updatePendingJoinRequests update) {
        long peerId = MessageObject.getPeerId(update.peer);
        firstImportersCache.put(-peerId, null);
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-peerId);
        if (chatFull != null) {
            chatFull.requests_pending = update.requests_pending;
            chatFull.recent_requesters = update.recent_requesters;
            chatFull.flags |= 131072;
            getMessagesStorage().updateChatInfo(chatFull, false);
            getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
        }
    }
}
