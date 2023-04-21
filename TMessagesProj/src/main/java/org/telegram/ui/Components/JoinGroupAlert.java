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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.JoinSheetUserCell;
import org.telegram.ui.ChatActivity;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class JoinGroupAlert extends BottomSheet {

    private TLRPC.ChatInvite chatInvite;
    private TLRPC.Chat currentChat;
    private String hash;
    private BaseFragment fragment;
    private TextView requestTextView;
    private RadialProgressView requestProgressView;

    public JoinGroupAlert(final Context context, TLObject obj, String group, BaseFragment parentFragment, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundWhite));

        fragment = parentFragment;
        if (obj instanceof TLRPC.ChatInvite) {
            chatInvite = (TLRPC.ChatInvite) obj;
        } else if (obj instanceof TLRPC.Chat) {
            currentChat = (TLRPC.Chat) obj;
        }
        hash = group;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClickable(true);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(linearLayout);

        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.addView(frameLayout);
        setCustomView(scrollView);

        ImageView closeView = new ImageView(context);
        closeView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        closeView.setColorFilter(getThemedColor(Theme.key_sheet_other));
        closeView.setImageResource(R.drawable.ic_layer_close);
        closeView.setOnClickListener((view) -> dismiss());
        int closeViewPadding = AndroidUtilities.dp(8);
        closeView.setPadding(closeViewPadding, closeViewPadding, closeViewPadding, closeViewPadding);
        frameLayout.addView(closeView, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.END, 6, 8, 6, 0));

        String title = null, about = null;
        AvatarDrawable avatarDrawable = null;
        int participants_count = 0;

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(35));
        linearLayout.addView(avatarImageView, LayoutHelper.createLinear(70, 70, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 29, 0, 0));

        if (chatInvite != null) {
            if (chatInvite.chat != null) {
                avatarDrawable = new AvatarDrawable(chatInvite.chat);
                title = chatInvite.chat.title;
                participants_count = chatInvite.chat.participants_count;
                avatarImageView.setForUserOrChat(chatInvite.chat, avatarDrawable, chatInvite);
            } else {
                avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(0, chatInvite.title, null);
                title = chatInvite.title;
                participants_count = chatInvite.participants_count;
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(chatInvite.photo.sizes, 50);
                avatarImageView.setImage(ImageLocation.getForPhoto(size, chatInvite.photo), "50_50", avatarDrawable, chatInvite);
            }
            about = chatInvite.about;
        } else if (currentChat != null) {
            avatarDrawable = new AvatarDrawable(currentChat);
            title = currentChat.title;
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(currentChat.id);
            about = chatFull != null ? chatFull.about : null;
            participants_count = Math.max(currentChat.participants_count, chatFull != null ? chatFull.participants_count : 0);
            avatarImageView.setForUserOrChat(currentChat, avatarDrawable, currentChat);
        }

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        textView.setText(title);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 9, 10, participants_count > 0 ? 0 : 20));

        final boolean isChannel = chatInvite != null && (chatInvite.channel && !chatInvite.megagroup || ChatObject.isChannelAndNotMegaGroup(chatInvite.chat)) || ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        boolean hasAbout = !TextUtils.isEmpty(about);
        if (participants_count > 0) {
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            if (isChannel) {
                textView.setText(LocaleController.formatPluralString("Subscribers", participants_count));
            } else {
                textView.setText(LocaleController.formatPluralString("Members", participants_count));
            }
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 3, 10, hasAbout ? 0 : 20));
        }

        if (hasAbout) {
            TextView aboutTextView = new TextView(context);
            aboutTextView.setGravity(Gravity.CENTER);
            aboutTextView.setText(about);
            aboutTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            aboutTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            linearLayout.addView(aboutTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 10, 24, 20));
        }

        if (chatInvite == null || chatInvite.request_needed) {
            FrameLayout requestFrameLayout = new FrameLayout(getContext());
            linearLayout.addView(requestFrameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            requestProgressView = new RadialProgressView(getContext(), resourcesProvider);
            requestProgressView.setProgressColor(getThemedColor(Theme.key_featuredStickers_addButton));
            requestProgressView.setSize(AndroidUtilities.dp(32));
            requestProgressView.setVisibility(View.INVISIBLE);
            requestFrameLayout.addView(requestProgressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

            requestTextView = new TextView(getContext());
            requestTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
            requestTextView.setEllipsize(TextUtils.TruncateAt.END);
            requestTextView.setGravity(Gravity.CENTER);
            requestTextView.setSingleLine(true);
            requestTextView.setText(isChannel ? LocaleController.getString("RequestToJoinChannel", R.string.RequestToJoinChannel) : LocaleController.getString("RequestToJoinGroup", R.string.RequestToJoinGroup));
            requestTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            requestTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            requestTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            requestTextView.setOnClickListener((view) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!isDismissed()) {
                        requestTextView.setVisibility(View.INVISIBLE);
                        requestProgressView.setVisibility(View.VISIBLE);
                    }
                }, 400);
                if (chatInvite == null && currentChat != null) {
                    MessagesController.getInstance(currentAccount).addUserToChat(
                        currentChat.id,
                        UserConfig.getInstance(currentAccount).getCurrentUser(),
                        0,
                        null,
                        null,
                        true,
                        this::dismiss,
                        err -> {
                            if (err != null && "INVITE_REQUEST_SENT".equals(err.text)) {
                                setOnDismissListener(di -> showBulletin(getContext(), fragment, isChannel));
                            }
                            dismiss();
                            return false;
                        }
                    );
                } else {
                    final TLRPC.TL_messages_importChatInvite request = new TLRPC.TL_messages_importChatInvite();
                    request.hash = hash;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (fragment == null || fragment.getParentActivity() == null) {
                                return;
                            }
                            if (error != null) {
                                if ("INVITE_REQUEST_SENT".equals(error.text)) {
                                    setOnDismissListener(di -> showBulletin(getContext(), fragment, isChannel));
                                } else {
                                    AlertsCreator.processError(currentAccount, error, fragment, request);
                                }
                            }
                            dismiss();
                        });
                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            });
            requestFrameLayout.addView(requestTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 0, 16, 0));

            TextView descriptionTextView = new TextView(getContext());
            descriptionTextView.setGravity(Gravity.CENTER);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descriptionTextView.setText(isChannel ? LocaleController.getString("RequestToJoinChannelDescription", R.string.RequestToJoinChannelDescription) : LocaleController.getString("RequestToJoinGroupDescription", R.string.RequestToJoinGroupDescription));
            descriptionTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 17, 24, 15));
        } else if (chatInvite != null) {
            if (!chatInvite.participants.isEmpty()) {
                RecyclerListView listView = new RecyclerListView(context);
                listView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
                listView.setNestedScrollingEnabled(false);
                listView.setClipToPadding(false);
                listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                listView.setHorizontalScrollBarEnabled(false);
                listView.setVerticalScrollBarEnabled(false);
                listView.setAdapter(new UsersAdapter(context));
                listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
                linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 7));
            }

            View shadow = new View(context);
            shadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
            linearLayout.addView(shadow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight()));

            PickerBottomLayout pickerBottomLayout = new PickerBottomLayout(context, false, resourcesProvider);
            linearLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
            pickerBottomLayout.cancelButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            pickerBottomLayout.cancelButton.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
            pickerBottomLayout.cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            pickerBottomLayout.cancelButton.setOnClickListener(view -> dismiss());
            pickerBottomLayout.doneButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            pickerBottomLayout.doneButton.setVisibility(View.VISIBLE);
            pickerBottomLayout.doneButtonBadgeTextView.setVisibility(View.GONE);
            pickerBottomLayout.doneButtonTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
            if (chatInvite.channel && !chatInvite.megagroup || ChatObject.isChannel(chatInvite.chat) && !chatInvite.chat.megagroup) {
                pickerBottomLayout.doneButtonTextView.setText(LocaleController.getString("ProfileJoinChannel", R.string.ProfileJoinChannel).toUpperCase());
            } else {
                pickerBottomLayout.doneButtonTextView.setText(LocaleController.getString("JoinGroup", R.string.JoinGroup));
            }
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
                                args.putLong("chat_id", chat.id);
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
    }

    public static void showBulletin(Context context, BaseFragment fragment, boolean isChannel) {
        if (context == null) {
            if (fragment != null) {
                context = fragment.getContext();
            }
            if (context == null) {
                return;
            }
            return;
        }
        Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(context, fragment.getResourceProvider());
        layout.imageView.setAnimation(R.raw.timer_3, 28, 28);
        layout.titleTextView.setText(LocaleController.getString("RequestToJoinSent", R.string.RequestToJoinSent));
        String subTitle = isChannel
                ? LocaleController.getString("RequestToJoinChannelSentDescription", R.string.RequestToJoinChannelSentDescription)
                : LocaleController.getString("RequestToJoinGroupSentDescription", R.string.RequestToJoinGroupSentDescription);
        layout.subtitleTextView.setText(subTitle);
        Bulletin.make(fragment, layout, Bulletin.DURATION_LONG).show();
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
