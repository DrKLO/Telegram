/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import androidx.core.widget.NestedScrollView;

public class JoinGroupAlert extends BottomSheet {

    public static final int ORIGINATION_OTHER = -1;
    public static final int ORIGINATION_SPONSORED_CHAT = 0;

    private final String hash;
    private final BaseFragment fragment;
    private TLRPC.ChatInvite chatInvite;
    private TLRPC.Chat currentChat;
    private TextView requestTextView;
    private RadialProgressView requestProgressView;

    public JoinGroupAlert(final Context context, TLObject obj, String group, BaseFragment parentFragment, Theme.ResourcesProvider resourcesProvider) {
        this(context, obj, group, parentFragment, resourcesProvider, ORIGINATION_OTHER);
    }

    public JoinGroupAlert(final Context context, TLObject obj, String group, BaseFragment parentFragment, Theme.ResourcesProvider resourcesProvider, int origination) {
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
        frameLayout.addView(closeView, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.END, 6, 8, 8, 0));

        String title = null, about = null;
        AvatarDrawable avatarDrawable;
        boolean verified = false;
        boolean scam = false;
        boolean fake = false;
        int participants_count = 0;

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(45));
        linearLayout.addView(avatarImageView, LayoutHelper.createLinear(90, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 27, 0, 0));

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
            verified = chatInvite.verified;
            fake = chatInvite.fake;
            scam = chatInvite.scam;
        } else if (currentChat != null) {
            avatarDrawable = new AvatarDrawable(currentChat);
            title = currentChat.title;
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(currentChat.id);
            about = chatFull != null ? chatFull.about : null;
            participants_count = Math.max(currentChat.participants_count, chatFull != null ? chatFull.participants_count : 0);
            avatarImageView.setForUserOrChat(currentChat, avatarDrawable, currentChat);
            verified = currentChat.verified;
            fake = currentChat.fake;
            scam = currentChat.scam;
        }

        SimpleTextView simpleTextView = new SimpleTextView(context);
        simpleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        simpleTextView.setTextSize(20);
        simpleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        simpleTextView.setText(title);
        simpleTextView.setGravity(Gravity.CENTER);
        linearLayout.addView(simpleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 10, 10, participants_count > 0 ? 0 : 20));

        if (scam || fake) {
            simpleTextView.setRightDrawable(getScamDrawable(scam ? 0 : 1));
        } else if (verified) {
            simpleTextView.setRightDrawable(getVerifiedCrossfadeDrawable());
        }

        final boolean isChannel = chatInvite != null && (chatInvite.channel && !chatInvite.megagroup || ChatObject.isChannelAndNotMegaGroup(chatInvite.chat)) || ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        boolean hasAbout = !TextUtils.isEmpty(about);

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);

        if (chatInvite != null && origination == ORIGINATION_SPONSORED_CHAT) {
            textView.setText(LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase());
        } else {
            textView.setText(isChannel
                    ? LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase()
                    : LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase()
            );
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 0, 10, hasAbout ? 0 : 20));

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
            requestTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
            requestTextView.setEllipsize(TextUtils.TruncateAt.END);
            requestTextView.setGravity(Gravity.CENTER);
            requestTextView.setSingleLine(true);
            requestTextView.setText(isChannel ? LocaleController.getString("RequestToJoinChannel", R.string.RequestToJoinChannel) : LocaleController.getString("RequestToJoinGroup", R.string.RequestToJoinGroup));
            requestTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            requestTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            requestTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
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
            requestFrameLayout.addView(requestTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 14, 0, 14, 0));

            TextView descriptionTextView = new TextView(getContext());
            descriptionTextView.setGravity(Gravity.CENTER);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descriptionTextView.setText(isChannel ? LocaleController.getString("RequestToJoinChannelDescription", R.string.RequestToJoinChannelDescription) : LocaleController.getString("RequestToJoinGroupDescription", R.string.RequestToJoinGroupDescription));
            descriptionTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 17, 24, 15));
        } else if (chatInvite != null) {
            if (!chatInvite.participants.isEmpty()) {
                int participantsCount = chatInvite.participants.size();
                int visibleAvatarsCount = Math.min(participantsCount, 3);
                float factor = 0.65f;
                int avatarSize = 38;
                AvatarsImageView avatarsImageView = new AvatarsImageView(context, false);
                avatarsImageView.setAvatarsTextSize(AndroidUtilities.dp(20));
                avatarsImageView.setSize(AndroidUtilities.dp(avatarSize));
                avatarsImageView.setCount(visibleAvatarsCount);
                avatarsImageView.setStepFactor(factor);
                for (int i = 0; i < visibleAvatarsCount; i++) {
                    avatarsImageView.setObject(i, UserConfig.selectedAccount, chatInvite.participants.get(i));
                }
                avatarsImageView.commitTransition(false);
                int avatarContainerWidth = (int) (avatarSize + (visibleAvatarsCount - 1) * (avatarSize * factor + 1));
                linearLayout.addView(avatarsImageView, LayoutHelper.createLinear(avatarContainerWidth, 44, Gravity.CENTER, 0, 2, 0, 4));

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                textView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
                textView.setGravity(Gravity.CENTER);

                String txt;
                if (visibleAvatarsCount == 1) {
                    txt = ellipsize(textView, chatInvite, 0).toString();
                } else if (visibleAvatarsCount == 2) {
                    txt = LocaleController.formatString(
                            "RequestToJoinMembersTwo", R.string.RequestToJoinMembersTwo,
                            ellipsize(textView, chatInvite, 0),
                            ellipsize(textView, chatInvite, 1)
                    );
                } else if (participants_count == 3) {
                    txt = LocaleController.formatString(
                            "RequestToJoinMembersThree", R.string.RequestToJoinMembersThree,
                            ellipsize(textView, chatInvite, 0),
                            ellipsize(textView, chatInvite, 1),
                            ellipsize(textView, chatInvite, 2)
                    );
                } else {
                    int count = Math.max(participants_count - visibleAvatarsCount, 2);
                    txt = String.format(
                            LocaleController.getPluralString("RequestToJoinMembersAll", count),
                            ellipsize(textView, chatInvite, 0),
                            ellipsize(textView, chatInvite, 1),
                            count
                    );
                }

                textView.setText(txt);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 0, 10, 24));
            }

            boolean isJoinToChannel = chatInvite.channel && !chatInvite.megagroup || ChatObject.isChannel(chatInvite.chat) && !chatInvite.chat.megagroup;
            TextView joinTextView = new TextView(getContext());
            joinTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
            joinTextView.setEllipsize(TextUtils.TruncateAt.END);
            joinTextView.setGravity(Gravity.CENTER);
            joinTextView.setSingleLine(true);
            joinTextView.setText(isJoinToChannel ? LocaleController.getString("ProfileJoinChannel", R.string.ProfileJoinChannel) : LocaleController.getString("ProfileJoinGroup", R.string.ProfileJoinGroup));
            joinTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            joinTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            joinTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            linearLayout.addView(joinTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 14, 0, 14, 14));
            joinTextView.setOnClickListener(v -> {
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
                                openChat(chat.id);
                            }
                        } else {
                            if ("USER_ALREADY_PARTICIPANT".equals(error.text) && origination == ORIGINATION_SPONSORED_CHAT && chatInvite != null && chatInvite.chat != null) {
                                openChat(chatInvite.chat.id);
                            } else {
                                AlertsCreator.processError(currentAccount, error, fragment, req);
                            }
                        }
                    });
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            });
        }
    }

    private Drawable getVerifiedCrossfadeDrawable() {
        Drawable verifiedDrawable = Theme.dialogs_verifiedDrawable;
        Drawable verifiedCheckDrawable = Theme.dialogs_verifiedCheckDrawable;
        return new CombinedDrawable(verifiedDrawable, verifiedCheckDrawable);
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

    private CharSequence ellipsize(TextView textView, TLRPC.ChatInvite chatInvite, int pos) {
        String firstName = chatInvite.participants.get(pos).first_name;
        if (firstName == null) {
            firstName = "";
        }
        return TextUtils.ellipsize(firstName.trim(), textView.getPaint(), AndroidUtilities.dp(120), TextUtils.TruncateAt.END);
    }

    private Drawable getScamDrawable(int type) {
//        ScamDrawable scamDrawable = new ScamDrawable(11, type);
//        scamDrawable.setColor(getThemedColor(Theme.key_avatar_subtitleInProfileBlue));
        return type == 0 ? Theme.dialogs_scamDrawable : Theme.dialogs_fakeDrawable;
    }

    private void openChat(long chatId) {
        Bundle args = new Bundle();
        args.putLong("chat_id", chatId);
        if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, fragment)) {
            ChatActivity chatActivity = new ChatActivity(args);
            fragment.presentFragment(chatActivity, fragment instanceof ChatActivity);
        }
    }
}
