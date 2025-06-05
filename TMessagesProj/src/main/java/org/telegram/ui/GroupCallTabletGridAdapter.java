package org.telegram.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.voip.GroupCallGridCell;
import org.telegram.ui.Components.voip.GroupCallMiniTextureView;
import org.telegram.ui.Components.voip.GroupCallRenderersContainer;

import java.util.ArrayList;

public class GroupCallTabletGridAdapter extends RecyclerListView.SelectionAdapter {
    private ChatObject.Call groupCall;
    private final int currentAccount;

    private final ArrayList<ChatObject.VideoParticipant> videoParticipants = new ArrayList<>();

    private ArrayList<GroupCallMiniTextureView> attachedRenderers;
    private GroupCallRenderersContainer renderersContainer;
    private final GroupCallActivity activity;
    private boolean visible = false;

    public GroupCallTabletGridAdapter(ChatObject.Call groupCall, int currentAccount, GroupCallActivity activity) {
        this.groupCall = groupCall;
        this.currentAccount = currentAccount;
        this.activity = activity;
    }

    public void setRenderersPool(ArrayList<GroupCallMiniTextureView> attachedRenderers, GroupCallRenderersContainer renderersContainer) {
        this.attachedRenderers = attachedRenderers;
        this.renderersContainer = renderersContainer;
    }

    public void setGroupCall(ChatObject.Call groupCall) {
        this.groupCall = groupCall;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecyclerListView.Holder(new GroupCallGridCell(parent.getContext(), true) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (visible && getParticipant() != null) {
                    attachRenderer(this, true);
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                attachRenderer(this, false);
            }
        });
    }

    private void attachRenderer(GroupCallGridCell cell, boolean attach) {
        if (attach && cell.getRenderer() == null) {
            cell.setRenderer(GroupCallMiniTextureView.getOrCreate(attachedRenderers, renderersContainer, null, null, cell, cell.getParticipant(), groupCall, activity));
        } else if (!attach) {
            if (cell.getRenderer() != null) {
                cell.getRenderer().setTabletGridView(null);
                cell.setRenderer(null);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupCallGridCell cell = (GroupCallGridCell) holder.itemView;

        ChatObject.VideoParticipant oldVideoParticipant = cell.getParticipant();
        ChatObject.VideoParticipant videoParticipant;
        TLRPC.GroupCallParticipant participant;
        videoParticipant = videoParticipants.get(position);
        participant = videoParticipants.get(position).participant;
        cell.spanCount = getSpanCount(position);
        cell.position = position;
        cell.gridAdapter = this;

        if (cell.getMeasuredHeight() != getItemHeight(position)) {
            cell.requestLayout();
        }

        cell.setData(AccountInstance.getInstance(currentAccount), videoParticipant, groupCall, MessageObject.getPeerId(groupCall.selfPeer));

        if (oldVideoParticipant != null && !oldVideoParticipant.equals(videoParticipant) && cell.attached && cell.getRenderer() != null) {
            attachRenderer(cell, false);
            attachRenderer(cell, true);
        } else if (cell.getRenderer() != null) {
            cell.getRenderer().updateAttachState(true);
        }
    }

    @Override
    public int getItemCount() {
        return videoParticipants.size();
    }

    public void setVisibility(RecyclerListView listView, boolean visibility, boolean updateAttach) {
        visible = visibility;
        if (updateAttach) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (view instanceof GroupCallGridCell) {
                    GroupCallGridCell cell = (GroupCallGridCell) view;
                    if (cell.getParticipant() != null) {
                        attachRenderer(cell, visibility);
                    }
                }
            }
        }
    }

    public void scrollToPeerId(long peerId, RecyclerListView fullscreenUsersListView) {
//        for (int i = 0; i < participants.size(); i++) {
//            if (peerId == MessageObject.getPeerId(participants.get(i).peer)) {
//                ((LinearLayoutManager) fullscreenUsersListView.getLayoutManager()).scrollToPositionWithOffset(i, AndroidUtilities.dp(13));
//                break;
//            }
//        }
    }

    public void update(boolean animated, RecyclerListView listView) {
        if (groupCall == null) {
            return;
        }
        if (animated) {
            ArrayList<ChatObject.VideoParticipant> oldVideoParticipants = new ArrayList<>();

            oldVideoParticipants.addAll(videoParticipants);
            videoParticipants.clear();
            videoParticipants.addAll(groupCall.visibleVideoParticipants);

            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldVideoParticipants.size();
                }

                @Override
                public int getNewListSize() {
                    return videoParticipants.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    if (oldItemPosition < oldVideoParticipants.size() && newItemPosition < videoParticipants.size()) {
                        return oldVideoParticipants.get(oldItemPosition).equals(videoParticipants.get(newItemPosition));
                    }
                    return false;
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return true;
                }
            }).dispatchUpdatesTo(this);
            AndroidUtilities.updateVisibleRows(listView);
        } else {
            videoParticipants.clear();
            videoParticipants.addAll(groupCall.visibleVideoParticipants);
            notifyDataSetChanged();
        }
    }

    public int getSpanCount(int position) {
        int itemsCount = getItemCount();
        if (itemsCount <= 1) {
            return 6;
        } else if (itemsCount == 2) {
            return 6;
        } else if (itemsCount == 3) {
            if (position == 0 || position == 1) {
                return 3;
            }
            return 6;
        }

        return 3;
    }

    public int getItemHeight(int position) {
        View parentView = activity.tabletVideoGridView;
        int itemsCount = getItemCount();
        if (itemsCount <= 1) {
            return parentView.getMeasuredHeight();
        } else if (itemsCount <= 4) {
            return parentView.getMeasuredHeight() / 2;
        } else {
            return (int) (parentView.getMeasuredHeight() / 2.5f);
        }
    }
}
