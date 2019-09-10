/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.JoinSheetUserCell;
import org.telegram.ui.ChatActivity;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class JoinGroupAlert extends BottomSheet {

    private TLRPC.ChatInvite chatInvite;
    private String hash;
    private BaseFragment fragment;

    public JoinGroupAlert(final Context context, TLRPC.ChatInvite invite, String group, BaseFragment parentFragment) {
        super(context, false, 0);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        fragment = parentFragment;
        chatInvite = invite;
        hash = group;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClickable(true);
        setCustomView(linearLayout);

        String title;
        AvatarDrawable avatarDrawable;
        int participants_count;

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(35));
        linearLayout.addView(avatarImageView, LayoutHelper.createLinear(70, 70, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        if (invite.chat != null) {
            avatarDrawable = new AvatarDrawable(invite.chat);
            title = invite.chat.title;
            participants_count = invite.chat.participants_count;
            avatarImageView.setImage(ImageLocation.getForChat(invite.chat, false), "50_50", avatarDrawable, invite);
        } else {
            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(0, invite.title, null);
            title = invite.title;
            participants_count = invite.participants_count;
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(invite.photo.sizes, 50);
            avatarImageView.setImage(ImageLocation.getForPhoto(size, invite.photo), "50_50", avatarDrawable, invite);
        }

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setText(title);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 10, 10, participants_count > 0 ? 0 : 10));

        if (participants_count > 0) {
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setText(LocaleController.formatPluralString("Members", participants_count));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 4, 10, 10));
        }

        if (!invite.participants.isEmpty()) {
            RecyclerListView listView = new RecyclerListView(context);
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            listView.setNestedScrollingEnabled(false);
            listView.setClipToPadding(false);
            listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            listView.setHorizontalScrollBarEnabled(false);
            listView.setVerticalScrollBarEnabled(false);
            listView.setAdapter(new UsersAdapter(context));
            listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
            linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        }

        View shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        linearLayout.addView(shadow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight()));

        PickerBottomLayout pickerBottomLayout = new PickerBottomLayout(context, false);
        linearLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        pickerBottomLayout.cancelButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.cancelButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        pickerBottomLayout.cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        pickerBottomLayout.cancelButton.setOnClickListener(view -> dismiss());
        pickerBottomLayout.doneButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.doneButton.setVisibility(View.VISIBLE);
        pickerBottomLayout.doneButtonBadgeTextView.setVisibility(View.GONE);
        pickerBottomLayout.doneButtonTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        pickerBottomLayout.doneButtonTextView.setText(LocaleController.getString("JoinGroup", R.string.JoinGroup));
        pickerBottomLayout.doneButton.setOnClickListener(v -> {
            dismiss();
            final TLRPC.TL_messages_importChatInvite req = new TLRPC.TL_messages_importChatInvite();
            req.hash = hash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (fragment == null || fragment.getParentActivity() == null) {
                        return;
                    }
                    if (error == null) {
                        TLRPC.Updates updates = (TLRPC.Updates) response;
                        if (!updates.chats.isEmpty()) {
                            TLRPC.Chat chat = updates.chats.get(0);
                            chat.left = false;
                            chat.kicked = false;
                            MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
                            MessagesController.getInstance(currentAccount).putChats(updates.chats, false);
                            Bundle args = new Bundle();
                            args.putInt("chat_id", chat.id);
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, fragment)) {
                                ChatActivity chatActivity = new ChatActivity(args);
                                fragment.presentFragment(chatActivity, fragment instanceof ChatActivity);
                            }
                        }
                    } else {
                        AlertsCreator.processError(currentAccount, error, fragment, req);
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        });
    }

    private class UsersAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public UsersAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            int count = chatInvite.participants.size();
            int participants_count;
            if (chatInvite.chat != null) {
                participants_count = chatInvite.chat.participants_count;
            } else {
                participants_count = chatInvite.participants_count;
            }
            if (count != participants_count) {
                count++;
            }
            return count;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new JoinSheetUserCell(context);
            view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(100), AndroidUtilities.dp(90)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            JoinSheetUserCell cell = (JoinSheetUserCell) holder.itemView;
            if (position < chatInvite.participants.size()) {
                cell.setUser(chatInvite.participants.get(position));
            } else {
                int participants_count;
                if (chatInvite.chat != null) {
                    participants_count = chatInvite.chat.participants_count;
                } else {
                    participants_count = chatInvite.participants_count;
                }
                cell.setCount(participants_count - chatInvite.participants.size());
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }
}
