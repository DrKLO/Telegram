package org.telegram.ui.Stories;

import android.view.View;

import com.google.android.exoplayer2.util.Log;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.LongSparseLongArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.Paint.Path;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class UserListPoller {

    private static UserListPoller[] istances = new UserListPoller[UserConfig.MAX_ACCOUNT_COUNT];

    final int currentAccount;

    private UserListPoller(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public static UserListPoller getInstance(int account) {
        if (istances[account] == null) {
            istances[account] = new UserListPoller(account);
        }
        return istances[account];
    }

    LongSparseLongArray userPollLastTime = new LongSparseLongArray();
    ArrayList<Long> dialogIds = new ArrayList<>();
    ArrayList<Long> collectedDialogIds = new ArrayList<>();

    ArrayList<Integer> runningRequests = new ArrayList<>();

    Runnable requestCollectedRunnables = new Runnable() {
        @Override
        public void run() {
            if (!collectedDialogIds.isEmpty()) {
                ArrayList<Long> dialogsFinal = new ArrayList<>(collectedDialogIds);
                collectedDialogIds.clear();
                TLRPC.TL_users_getStoriesMaxIDs request = new TLRPC.TL_users_getStoriesMaxIDs();
                for (int i = 0; i < dialogsFinal.size(); i++) {
                    request.id.add(MessagesController.getInstance(currentAccount).getInputUser(dialogsFinal.get(i)));
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response != null) {
                        TLRPC.Vector vector = (TLRPC.Vector) response;
                        ArrayList<TLRPC.User> usersToUpdate = new ArrayList<>();
                        ArrayList<TLRPC.Chat> chatsToUpdate = new ArrayList<>();
                        for (int i = 0; i < vector.objects.size(); i++) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogsFinal.get(i));
                            if (user == null) {
                                continue;
                            }
                            user.stories_max_id = (int) vector.objects.get(i);
                            if (user.stories_max_id != 0) {
                                user.flags2 |= 32;
                            } else {
                                user.flags2 &= ~32;
                            }
                            usersToUpdate.add(user);
                        }
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(usersToUpdate, null, true, true);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                    }
                }));
            }
        }
    };

    public void checkList(RecyclerListView recyclerListView) {
        long currentTime = System.currentTimeMillis();
        dialogIds.clear();
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);
            long dialogId = 0;
            if (child instanceof DialogCell) {
                dialogId = ((DialogCell) child).getDialogId();
            } else if (child instanceof UserCell) {
                dialogId = ((UserCell) child).getDialogId();
            }

            if (dialogId > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (user != null && !user.bot && !user.self && !user.contact && user.status != null && !(user.status instanceof TLRPC.TL_userStatusEmpty)) {
                    long lastPollTime = userPollLastTime.get(dialogId, 0);
                    if (currentTime - lastPollTime > 60 * 60 * 1000) {
                        userPollLastTime.put(dialogId, currentTime);
                        dialogIds.add(dialogId);
                    }
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (ChatObject.isChannel(chat)) {
                    long lastPollTime = userPollLastTime.get(dialogId, 0);
                    if (currentTime - lastPollTime > 60 * 60 * 1000) {
                        userPollLastTime.put(dialogId, currentTime);
                        dialogIds.add(dialogId);
                    }
                }
            }
        }
        if (!dialogIds.isEmpty()) {
            collectedDialogIds.addAll(dialogIds);
            AndroidUtilities.cancelRunOnUIThread(requestCollectedRunnables);
            AndroidUtilities.runOnUIThread(requestCollectedRunnables, 300);
        }
    }
}
