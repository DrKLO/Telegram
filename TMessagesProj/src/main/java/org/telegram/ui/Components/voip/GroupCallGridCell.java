package org.telegram.ui.Components.voip;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.GroupCallTabletGridAdapter;

public class GroupCallGridCell extends FrameLayout {

    public final static int CELL_HEIGHT = 165;
    public int spanCount;
    public int position;
    public GroupCallTabletGridAdapter gridAdapter;

    GroupCallMiniTextureView renderer;

    ChatObject.VideoParticipant participant;
    public boolean attached;
    private final boolean isTabletGrid;

    public GroupCallGridCell(@NonNull Context context, boolean isTabletGrid) {
        super(context);
        this.isTabletGrid = isTabletGrid;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isTabletGrid) {
            float totalSpans = 6;
            float w = ((View) getParent()).getMeasuredWidth() / totalSpans * spanCount;
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(gridAdapter.getItemHeight(position), MeasureSpec.EXACTLY));
        } else {
            float spanCount = GroupCallActivity.isLandscapeMode ? 3f : 2f;
            float parentWidth;
            float h;
            if (getParent() != null) {
                parentWidth = ((View) getParent()).getMeasuredWidth();
            } else {
                parentWidth = MeasureSpec.getSize(widthMeasureSpec);
            }
            if (GroupCallActivity.isTabletMode) {
                h = parentWidth / 2f;
            } else {
                h = parentWidth / spanCount;
            }

            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (h + AndroidUtilities.dp(4)), MeasureSpec.EXACTLY));
        }
    }

    public void setData(AccountInstance accountInstance, ChatObject.VideoParticipant participant, ChatObject.Call call, long selfPeerId) {
        this.participant = participant;
    }

    public ChatObject.VideoParticipant getParticipant() {
        return participant;
    }

    public void setRenderer(GroupCallMiniTextureView renderer) {
        this.renderer = renderer;
    }

    public GroupCallMiniTextureView getRenderer() {
        return renderer;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }

    public float getItemHeight() {
        if (gridAdapter != null) {
            return gridAdapter.getItemHeight(position);
        } else {
            return getMeasuredHeight();
        }
    }
}
