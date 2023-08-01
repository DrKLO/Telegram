package org.telegram.ui.Stories;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

//TODO stories
public class ViewsForSelfStoriesRequester {

    StoriesController storiesController;
    int currentAccount;

    int currentReqId;
    boolean isRunning;
    long time;

    final Runnable scheduleRequestRunnuble = () -> requestInternal();

    public ViewsForSelfStoriesRequester(StoriesController storiesController, int currentAccount) {
        this.currentAccount = currentAccount;
        this.storiesController = storiesController;
    }

    public void start(boolean start) {
        if (isRunning != start) {
            if (requestInternal()) {
                isRunning = start;
            }
        } else {
            isRunning = false;
            AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnuble);
            ConnectionsManager.getInstance(currentAccount).cancelRequest(currentReqId, false);
            currentReqId = 0;
        }
    }

    private boolean requestInternal() {
        TLRPC.TL_userStories stories = storiesController.getStories(UserConfig.getInstance(currentAccount).getClientUserId());
        if (stories == null || stories.stories.isEmpty() || currentReqId != 0) {
            return false;
        }
        TLRPC.TL_stories_getStoriesViews req = new TLRPC.TL_stories_getStoriesViews();
        for (int i = 0; i < stories.stories.size(); i++) {
            req.id.add(stories.stories.get(i).id);
        }


        currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.TL_userStories currentStories = storiesController.getStories(UserConfig.getInstance(currentAccount).getClientUserId());
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
                AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnuble);
                AndroidUtilities.runOnUIThread(scheduleRequestRunnuble, 10_000);
            }
        }));
        return true;
    }
}
