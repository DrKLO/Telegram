package org.telegram.ui.Stories;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class ViewsForPeerStoriesRequester {

    final StoriesController storiesController;
    final int currentAccount;
    final long dialogId;

    int currentReqId;
    boolean isRunning;

    final Runnable scheduleRequestRunnable = () -> requestInternal();

    public ViewsForPeerStoriesRequester(StoriesController storiesController, long dialogId, int currentAccount) {
        this.currentAccount = currentAccount;
        this.storiesController = storiesController;
        this.dialogId = dialogId;
    }

    public void start(boolean start) {
        if (isRunning != start) {
            if (requestInternal()) {
                isRunning = start;
            }
        } else {
            isRunning = false;
            AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnable);
            ConnectionsManager.getInstance(currentAccount).cancelRequest(currentReqId, false);
            currentReqId = 0;
        }
    }

    private boolean requestInternal() {
        TLRPC.PeerStories stories = storiesController.getStories(dialogId);
        if (stories == null || stories.stories.isEmpty() || currentReqId != 0) {
            return false;
        }
        TLRPC.TL_stories_getStoriesViews req = new TLRPC.TL_stories_getStoriesViews();
        for (int i = 0; i < stories.stories.size(); i++) {
            req.id.add(stories.stories.get(i).id);
        }
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

        currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.PeerStories currentStories = storiesController.getStories(dialogId);
                if (currentStories == null || currentStories.stories.isEmpty()) {
                    currentReqId = 0;
                    isRunning = false;
                    return;
                }
                TLRPC.TL_stories_storyViews storyViews = (TLRPC.TL_stories_storyViews) response;
                MessagesController.getInstance(currentAccount).putUsers(storyViews.users, false);

                for (int i = 0; i < storyViews.views.size(); i++) {
                    for (int j = 0; j < currentStories.stories.size(); j++) {
                        if (currentStories.stories.get(j).id == req.id.get(i)) {
                            currentStories.stories.get(j).views = storyViews.views.get(i);
                        }
                    }
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesUpdated);
                storiesController.storiesStorage.updateStories(currentStories);
            }
            currentReqId = 0;
            if (isRunning) {
                AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnable);
                AndroidUtilities.runOnUIThread(scheduleRequestRunnable, 10_000);
            }
        }));
        return true;
    }
}
