package org.telegram.ui.Stories;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stories;

import java.util.ArrayList;

public class ViewsForPeerStoriesRequester {

    final StoriesController storiesController;
    final int currentAccount;
    final long dialogId;

    int currentReqId;
    boolean isRunning;

    final Runnable scheduleRequestRunnable = () -> step();

    public ViewsForPeerStoriesRequester(StoriesController storiesController, long dialogId, int currentAccount) {
        this.currentAccount = currentAccount;
        this.storiesController = storiesController;
        this.dialogId = dialogId;
    }

    public void start(boolean start) {
        if (isRunning == start) {
            return;
        }
        if (start) {
            isRunning = true;
            step();
        } else {
            isRunning = false;
            AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnable);
            ConnectionsManager.getInstance(currentAccount).cancelRequest(currentReqId, false);
            currentReqId = 0;
        }
    }

    protected void getStoryIds(ArrayList<Integer> ids) {
        TL_stories.PeerStories stories = storiesController.getStories(dialogId);
        if (stories != null && stories.stories != null) {
            for (int i = 0; i < stories.stories.size(); i++) {
                ids.add(stories.stories.get(i).id);
            }
        }
    }

    protected boolean updateStories(ArrayList<Integer> reqIds, TL_stories.TL_stories_storyViews storyViews) {
        if (storyViews == null || storyViews.views == null) {
            return false;
        }
        TL_stories.PeerStories currentStories = storiesController.getStories(dialogId);
        if (currentStories == null || currentStories.stories.isEmpty()) {
            return false;
        }
        for (int i = 0; i < storyViews.views.size(); i++) {
            for (int j = 0; j < currentStories.stories.size(); j++) {
                if (currentStories.stories.get(j).id == reqIds.get(i)) {
                    currentStories.stories.get(j).views = storyViews.views.get(i);
                }
            }
        }
        storiesController.storiesStorage.updateStories(currentStories);
        return true;
    }

    private static final long interval = 10_000;
    private static long lastRequestTime;

    private void step() {
        if (!isRunning) {
            return;
        }
        long wait = interval - (System.currentTimeMillis() - lastRequestTime);
        if (wait > 0) {
            AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnable);
            AndroidUtilities.runOnUIThread(scheduleRequestRunnable, wait);
        } else {
            if (!requestInternal()) {
                currentReqId = 0;
                isRunning = false;
            }
        }
    }

    private boolean requestInternal() {
        if (currentReqId != 0) {
            return false;
        }
        TL_stories.TL_stories_getStoriesViews req = new TL_stories.TL_stories_getStoriesViews();
        getStoryIds(req.id);
        if (req.id.isEmpty()) {
            return false;
        }
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

        currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            lastRequestTime = System.currentTimeMillis();

            if (response != null) {
                TL_stories.TL_stories_storyViews storyViews = (TL_stories.TL_stories_storyViews) response;
                MessagesController.getInstance(currentAccount).putUsers(storyViews.users, false);

                if (!updateStories(req.id, storyViews)) {
                    currentReqId = 0;
                    isRunning = false;
                    return;
                }

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesUpdated);
            }
            currentReqId = 0;
            if (isRunning) {
                AndroidUtilities.cancelRunOnUIThread(scheduleRequestRunnable);
                AndroidUtilities.runOnUIThread(scheduleRequestRunnable, interval);
            }
        }));

        return true;
    }
}
