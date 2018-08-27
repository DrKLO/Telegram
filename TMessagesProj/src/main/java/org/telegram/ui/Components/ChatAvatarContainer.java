/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.MediaActivity;
import org.telegram.ui.ProfileActivity;

public class ChatAvatarContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView avatarImageView;
    private SimpleTextView titleTextView;
    private SimpleTextView subtitleTextView;
    private ImageView timeItem;
    private TimerDrawable timerDrawable;
    private ChatActivity parentFragment;
    private StatusDrawable[] statusDrawables = new StatusDrawable[5];
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private int currentAccount = UserConfig.selectedAccount;
    private boolean occupyStatusBar = true;

    private int onlineCount = -1;
    private int currentConnectionState;
    private CharSequence lastSubtitle;

    public ChatAvatarContainer(Context context, ChatActivity chatActivity, boolean needTime) {
        super(context);
        parentFragment = chatActivity;

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        addView(avatarImageView);

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTextSize(18);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        addView(titleTextView);

        subtitleTextView = new SimpleTextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        subtitleTextView.setTextSize(14);
        subtitleTextView.setGravity(Gravity.LEFT);
        addView(subtitleTextView);

        if (needTime) {
            timeItem = new ImageView(context);
            timeItem.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            timeItem.setScaleType(ImageView.ScaleType.CENTER);
            timeItem.setImageDrawable(timerDrawable = new TimerDrawable(context));
            addView(timeItem);
            timeItem.setOnClickListener(v -> parentFragment.showDialog(AlertsCreator.createTTLAlert(getContext(), parentFragment.getCurrentEncryptedChat()).create()));
        }

        if (parentFragment != null) {
            setOnClickListener(v -> {
                TLRPC.User user = parentFragment.getCurrentUser();
                TLRPC.Chat chat = parentFragment.getCurrentChat();
                if (user != null) {
                    Bundle args = new Bundle();
                    if (UserObject.isUserSelf(user)) {
                        args.putLong("dialog_id", parentFragment.getDialogId());
                        MediaActivity fragment = new MediaActivity(args, new int[]{-1, -1, -1, -1, -1});
                        fragment.setChatInfo(parentFragment.getCurrentChatInfo());
                        parentFragment.presentFragment(fragment);
                    } else {
                        args.putInt("user_id", user.id);
                        if (timeItem != null) {
                            args.putLong("dialog_id", parentFragment.getDialogId());
                        }
                        ProfileActivity fragment = new ProfileActivity(args);
                        fragment.setPlayProfileAnimation(true);
                        parentFragment.presentFragment(fragment);
                    }
                } else if (chat != null) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    fragment.setChatInfo(parentFragment.getCurrentChatInfo());
                    fragment.setPlayProfileAnimation(true);
                    parentFragment.presentFragment(fragment);
                }
            });

            TLRPC.Chat chat = parentFragment.getCurrentChat();
            statusDrawables[0] = new TypingDotsDrawable();
            statusDrawables[1] = new RecordStatusDrawable();
            statusDrawables[2] = new SendingFileDrawable();
            statusDrawables[3] = new PlayingGameDrawable();
            statusDrawables[4] = new RoundStatusDrawable();
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].setIsChat(chat != null);
            }
        }
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
    }

    public void setTitleColors(int title, int subtitle) {
        titleTextView.setTextColor(title);
        subtitleTextView.setTextColor(title);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int availableWidth = width - AndroidUtilities.dp(54 + 16);
        avatarImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));
        subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        if (timeItem != null) {
            timeItem.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        int viewTop = (actionBarHeight - AndroidUtilities.dp(42)) / 2 + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
        avatarImageView.layout(AndroidUtilities.dp(8), viewTop, AndroidUtilities.dp(42 + 8), viewTop + AndroidUtilities.dp(42));
        if (subtitleTextView.getVisibility() == VISIBLE) {
            titleTextView.layout(AndroidUtilities.dp(8 + 54), viewTop + AndroidUtilities.dp(1.3f), AndroidUtilities.dp(8 + 54) + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f));
        } else {
            titleTextView.layout(AndroidUtilities.dp(8 + 54), viewTop + AndroidUtilities.dp(11), AndroidUtilities.dp(8 + 54) + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(11));
        }
        if (timeItem != null) {
            timeItem.layout(AndroidUtilities.dp(8 + 16), viewTop + AndroidUtilities.dp(15), AndroidUtilities.dp(8 + 16 + 34), viewTop + AndroidUtilities.dp(15 + 34));
        }
        subtitleTextView.layout(AndroidUtilities.dp(8 + 54), viewTop + AndroidUtilities.dp(24), AndroidUtilities.dp(8 + 54) + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
    }

    public void showTimeItem() {
        if (timeItem == null) {
            return;
        }
        timeItem.setVisibility(VISIBLE);
    }

    public void hideTimeItem() {
        if (timeItem == null) {
            return;
        }
        timeItem.setVisibility(GONE);
    }

    public void setTime(int value) {
        if (timerDrawable == null) {
            return;
        }
        timerDrawable.setTime(value);
    }

    public void setTitleIcons(int leftIcon, int rightIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        titleTextView.setRightDrawable(rightIcon);
    }

    public void setTitleIcons(Drawable leftIcon, Drawable rightIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        titleTextView.setRightDrawable(rightIcon);
    }

    public void setTitle(CharSequence value) {
        titleTextView.setText(value);
    }

    public void setSubtitle(CharSequence value) {
        if (lastSubtitle == null) {
            subtitleTextView.setText(value);
        } else {
            lastSubtitle = value;
        }
    }

    public ImageView getTimeItem() {
        return timeItem;
    }

    public SimpleTextView getTitleTextView() {
        return titleTextView;
    }

    public SimpleTextView getSubtitleTextView() {
        return subtitleTextView;
    }

    private void setTypingAnimation(boolean start) {
        if (start) {
            try {
                Integer type = MessagesController.getInstance(currentAccount).printingStringsTypes.get(parentFragment.getDialogId());
                subtitleTextView.setLeftDrawable(statusDrawables[type]);
                for (int a = 0; a < statusDrawables.length; a++) {
                    if (a == type) {
                        statusDrawables[a].start();
                    } else {
                        statusDrawables[a].stop();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            subtitleTextView.setLeftDrawable(null);
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].stop();
            }
        }
    }

    public void updateSubtitle() {
        if (parentFragment == null) {
            return;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        if (UserObject.isUserSelf(user)) {
            if (subtitleTextView.getVisibility() != GONE) {
                subtitleTextView.setVisibility(GONE);
            }
            return;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        CharSequence printString = MessagesController.getInstance(currentAccount).printingStrings.get(parentFragment.getDialogId());
        if (printString != null) {
            printString = TextUtils.replace(printString, new String[]{"..."}, new String[]{""});
        }
        CharSequence newSubtitle;
        if (printString == null || printString.length() == 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
            setTypingAnimation(false);
            if (chat != null) {
                TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
                if (ChatObject.isChannel(chat)) {
                    if (info != null && info.participants_count != 0) {
                        if (chat.megagroup && info.participants_count <= 200) {
                            if (onlineCount > 1 && info.participants_count != 0) {
                                newSubtitle = String.format("%s, %s", LocaleController.formatPluralString("Members", info.participants_count), LocaleController.formatPluralString("OnlineCount", onlineCount));
                            } else {
                                newSubtitle = LocaleController.formatPluralString("Members", info.participants_count);
                            }
                        } else {
                            int result[] = new int[1];
                            String shortNumber = LocaleController.formatShortNumber(info.participants_count, result);
                            if (chat.megagroup) {
                                newSubtitle = LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            } else {
                                newSubtitle = LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            }
                        }
                    } else {
                        if (chat.megagroup) {
                            newSubtitle = LocaleController.getString("Loading", R.string.Loading).toLowerCase();
                        } else {
                            if ((chat.flags & TLRPC.CHAT_FLAG_IS_PUBLIC) != 0) {
                                newSubtitle = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
                            } else {
                                newSubtitle = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
                            }
                        }
                    }
                } else {
                    if (ChatObject.isKickedFromChat(chat)) {
                        newSubtitle = LocaleController.getString("YouWereKicked", R.string.YouWereKicked);
                    } else if (ChatObject.isLeftFromChat(chat)) {
                        newSubtitle = LocaleController.getString("YouLeft", R.string.YouLeft);
                    } else {
                        int count = chat.participants_count;
                        if (info != null && info.participants != null) {
                            count = info.participants.participants.size();
                        }
                        if (onlineCount > 1 && count != 0) {
                            newSubtitle = String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("OnlineCount", onlineCount));
                        } else {
                            newSubtitle = LocaleController.formatPluralString("Members", count);
                        }
                    }
                }
            } else if (user != null) {
                TLRPC.User newUser = MessagesController.getInstance(currentAccount).getUser(user.id);
                if (newUser != null) {
                    user = newUser;
                }
                String newStatus;
                if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    newStatus = LocaleController.getString("ChatYourSelf", R.string.ChatYourSelf);
                } else if (user.id == 333000 || user.id == 777000) {
                    newStatus = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
                } else if (user.bot) {
                    newStatus = LocaleController.getString("Bot", R.string.Bot);
                } else {
                    newStatus = LocaleController.formatUserStatus(currentAccount, user);
                }
                newSubtitle = newStatus;
            } else {
                newSubtitle = "";
            }
        } else {
            newSubtitle = printString;
            setTypingAnimation(true);
        }
        if (lastSubtitle == null) {
            subtitleTextView.setText(newSubtitle);
        } else {
            lastSubtitle = newSubtitle;
        }
    }

    public void setChatAvatar(TLRPC.Chat chat) {
        TLRPC.FileLocation newPhoto = null;
        if (chat.photo != null) {
            newPhoto = chat.photo.photo_small;
        }
        avatarDrawable.setInfo(chat);
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
        }
    }

    public void setUserAvatar(TLRPC.User user) {
        TLRPC.FileLocation newPhoto = null;
        avatarDrawable.setInfo(user);
        if (UserObject.isUserSelf(user)) {
            avatarDrawable.setSavedMessages(2);
        } else if (user.photo != null) {
            newPhoto = user.photo.photo_small;
        }

        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
        }
    }

    public void checkAndUpdateAvatar() {
        if (parentFragment == null) {
            return;
        }
        TLRPC.FileLocation newPhoto = null;
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (user != null) {
            avatarDrawable.setInfo(user);
            if (UserObject.isUserSelf(user)) {
                avatarDrawable.setSavedMessages(2);
            } else if (user.photo != null) {
                newPhoto = user.photo.photo_small;
            }
        } else if (chat != null) {
            if (chat.photo != null) {
                newPhoto = chat.photo.photo_small;
            }
            avatarDrawable.setInfo(chat);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
        }
    }

    public void updateOnlineCount() {
        if (parentFragment == null) {
            return;
        }
        onlineCount = 0;
        TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
        if (info == null) {
            return;
        }
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (info instanceof TLRPC.TL_chatFull || info instanceof TLRPC.TL_channelFull && info.participants_count <= 200 && info.participants != null) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getInstance(currentAccount).getClientUserId()) && user.status.expires > 10000) {
                    onlineCount++;
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdatedConnectionState);
            currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            updateCurrentConnectionState();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdatedConnectionState);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdatedConnectionState) {
            int state = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                updateCurrentConnectionState();
            }
        }
    }

    private void updateCurrentConnectionState() {
        String title = null;
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            title = LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            title = LocaleController.getString("Connecting", R.string.Connecting);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            title = LocaleController.getString("Updating", R.string.Updating);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            title = LocaleController.getString("ConnectingToProxy", R.string.ConnectingToProxy);
        }
        if (title == null) {
            if (lastSubtitle != null) {
                subtitleTextView.setText(lastSubtitle);
                lastSubtitle = null;
            }
        } else {
            lastSubtitle = subtitleTextView.getText();
            subtitleTextView.setText(title);
        }
    }
}
