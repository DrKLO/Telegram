package org.telegram.ui.Components.chat;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;

public class ChatActivityDraftMessageMeasureController {
    private RecyclerView recyclerView;
    private int messageIdToOverride;
    private long groupIdToOverride;
    private int previousMessageHeight;
    private boolean hasAdditionalHeight;

    public int getOverrideMeasureHeight(MessageObject messageObject, int measuredHeight) {
        if (!filter(messageObject)) {
            return measuredHeight;
        }
        
        final int availHeight = recyclerView.getHeight()
                - recyclerView.getPaddingTop()
                - recyclerView.getPaddingBottom()
                - previousMessageHeight;

        final int additionalHeight = Math.max(0, availHeight - measuredHeight);
        hasAdditionalHeight = additionalHeight > 0;
        if (messageIdToOverride > 0 && !hasAdditionalHeight) {
            setMessageIdToOverride(0, 0);
        }

        return measuredHeight + additionalHeight;
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    public void setPreviousMessageHeight(int previousMessageHeight) {
        this.previousMessageHeight = previousMessageHeight;
    }

    public boolean hasAdditionalHeight() {
        return hasAdditionalHeight;
    }

    public boolean onMessageIdChanged(int oldMessageId, int newMessageId, long groupId) {
        if (messageIdToOverride == oldMessageId) {
            return setMessageIdToOverride(newMessageId, groupId);
        }
        return false;
    }

    public boolean setMessageIdToOverride(int messageIdToOverride, long groupIdToOverride) {
        if (this.messageIdToOverride != messageIdToOverride || this.groupIdToOverride != groupIdToOverride) {
            this.messageIdToOverride = messageIdToOverride;
            this.groupIdToOverride = groupIdToOverride;
            if (messageIdToOverride == 0) {
                hasAdditionalHeight = false;
            }
            return true;
        }
        return false;
    }

    public void onScroll() {
        if (messageIdToOverride <= 0) {
            return;
        }

        boolean found = false;
        for (int a = 0, N = recyclerView.getChildCount(); a < N; a++) {
            if (filter(recyclerView.getChildAt(a))) {
                found = true;
                break;
            }
        }
        if (!found) {
            setMessageIdToOverride(0, 0);
        }
    }

    public void onRequestLayout() {
        if (messageIdToOverride == 0) {
            return;
        }

        for (int a = 0, N = recyclerView.getChildCount(); a < N; a++) {
            recyclerView.getChildAt(a).forceLayout();
        }
    }


    public boolean filter(View view) {
        if (view instanceof ChatMessageCell) {
            return filter(((ChatMessageCell) view).getMessageObject());
        } else if (view instanceof ChatActionCell) {
            return filter(((ChatActionCell) view).getMessageObject());
        }
        return false;
    }

    public boolean filter(MessageObject messageObject) {
        return messageObject != null && (messageObject.getId() == messageIdToOverride || groupIdToOverride != 0 && messageObject.getGroupId() == groupIdToOverride);
    }
}
