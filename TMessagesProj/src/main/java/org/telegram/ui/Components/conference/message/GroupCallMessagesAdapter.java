package org.telegram.ui.Components.conference.message;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.voip.GroupCallMessage;
import org.telegram.messenger.voip.GroupCallMessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

public class GroupCallMessagesAdapter extends RecyclerView.Adapter<GroupCallMessageCell.VH> implements GroupCallMessagesController.CallMessageListener {

    @NonNull
    @Override
    public GroupCallMessageCell.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        GroupCallMessageCell cell = new GroupCallMessageCell(parent.getContext());
        cell.setPadding(dp(22), 0, dp(22), 0);
        return new GroupCallMessageCell.VH(cell);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupCallMessageCell.VH holder, int position) {
        if (messages == null || messages.size() <= position) {
            return;
        }

        final GroupCallMessage message = messages.get(position);

        final GroupCallMessageCell cell = (GroupCallMessageCell) holder.itemView;
        cell.set(message);
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    @Nullable
    public GroupCallMessage getMessage(int position) {
        if (messages == null) {
            return null;
        }

        if (position >= 0 && position < messages.size()) {
            return messages.get(position);
        }

        return null;
    }

    @Nullable
    private List<GroupCallMessage> messages;

    @Override
    public void onNewGroupCallMessage(long callId, GroupCallMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }

        messages.add(0, message);
        notifyItemInserted(0);
    }

    @Override
    public void onPopGroupCallMessage() {
        if (messages != null && !messages.isEmpty()) {
            final int index = messages.size() - 1;
            messages.remove(index);
            notifyItemRemoved(index);
        }
    }



    private boolean isAttachedToRecyclerView;

    private int currentAccount = -1;
    private TLRPC.InputGroupCall inputGroupCall;


    @SuppressLint("NotifyDataSetChanged")
    public void attach() {
        isAttachedToRecyclerView = true;

        if (currentAccount != -1 && inputGroupCall != null) {
            messages = GroupCallMessagesController.getInstance(currentAccount).getCallMessages(inputGroupCall.id);
            notifyDataSetChanged();
            GroupCallMessagesController.getInstance(currentAccount)
                .subscribeToCallMessages(inputGroupCall.id, this);
        }
    }

    public void detach() {
        isAttachedToRecyclerView = false;

        if (currentAccount != -1 && inputGroupCall != null) {
            GroupCallMessagesController.getInstance(currentAccount)
                    .unsubscribeFromCallMessages(inputGroupCall.id, this);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setGroupCall(int currentAccount, TLRPC.InputGroupCall call) {
        if (isAttachedToRecyclerView && this.currentAccount != -1 && inputGroupCall != null) {
            GroupCallMessagesController.getInstance(this.currentAccount)
                .unsubscribeFromCallMessages(this.inputGroupCall.id, this);
        }

        this.currentAccount = currentAccount;
        this.inputGroupCall = call;

        if (isAttachedToRecyclerView) {
            messages = GroupCallMessagesController.getInstance(currentAccount).getCallMessages(inputGroupCall.id);
            notifyDataSetChanged();
            GroupCallMessagesController.getInstance(currentAccount)
                .subscribeToCallMessages(inputGroupCall.id, this);
        }
    }
}
