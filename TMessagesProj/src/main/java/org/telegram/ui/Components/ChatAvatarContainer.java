/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
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
import org.telegram.ui.ProfileActivity;

public class ChatAvatarContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView avatarImageView;
    private SimpleTextView titleTextView;
    private SimpleTextView subtitleTextView;
    private ImageView timeItem;
    private TimerDrawable timerDrawable;
    private ChatActivity parentFragment;
    private StatusDrawable[] statusDrawables = new StatusDrawable[6];
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private int currentAccount = UserConfig.selectedAccount;
    private boolean occupyStatusBar = true;
    private int leftPadding = AndroidUtilities.dp(8);
    StatusDrawable currentTypingDrawable;

    private AnimatorSet titleAnimation;

    private boolean[] isOnline = new boolean[1];

    private boolean secretChatTimer;

    private int onlineCount = -1;
    private int currentConnectionState;
    private CharSequence lastSubtitle;
    private String lastSubtitleColorKey;

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private Theme.ResourcesProvider resourcesProvider;

    public ChatAvatarContainer(Context context, ChatActivity chatActivity, boolean needTime) {
        this(context, chatActivity, needTime, null);
    }
    
    public ChatAvatarContainer(Context context, ChatActivity chatActivity, boolean needTime, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        parentFragment = chatActivity;

        final boolean avatarClickable = parentFragment != null && parentFragment.getChatMode() == 0 && !UserObject.isReplyUser(parentFragment.getCurrentUser());
        avatarImageView = new BackupImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (avatarClickable && getImageReceiver().hasNotThumb()) {
                    info.setText(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString("Open", R.string.Open)));
                    }
                } else {
                    info.setVisibleToUser(false);
                }
            }
        };
        if (parentFragment != null) {
            sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(chatActivity);
            if (parentFragment.isThreadChat() || parentFragment.getChatMode() == 2) {
                avatarImageView.setVisibility(GONE);
            }
        }
        avatarImageView.setContentDescription(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));
        avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        addView(avatarImageView);
        if (avatarClickable) {
            avatarImageView.setOnClickListener(v -> openProfile(true));
        }

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTextSize(18);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        addView(titleTextView);

        subtitleTextView = new SimpleTextView(context);
        subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextView.setTextSize(14);
        subtitleTextView.setGravity(Gravity.LEFT);
        addView(subtitleTextView);

        if (parentFragment != null) {
            timeItem = new ImageView(context);
            timeItem.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            timeItem.setScaleType(ImageView.ScaleType.CENTER);
            timeItem.setAlpha(0.0f);
            timeItem.setScaleY(0.0f);
            timeItem.setScaleX(0.0f);
            timeItem.setVisibility(GONE);
            timeItem.setImageDrawable(timerDrawable = new TimerDrawable(context));
            addView(timeItem);
            secretChatTimer = needTime;

            timeItem.setOnClickListener(v -> {
                if (secretChatTimer) {
                    parentFragment.showDialog(AlertsCreator.createTTLAlert(getContext(), parentFragment.getCurrentEncryptedChat(), resourcesProvider).create());
                } else {
                    openSetTimer();
                }
            });
            if (secretChatTimer) {
                timeItem.setContentDescription(LocaleController.getString("SetTimer", R.string.SetTimer));
            } else {
                timeItem.setContentDescription(LocaleController.getString("AccAutoDeleteTimer", R.string.AccAutoDeleteTimer));
            }
        }

        if (parentFragment != null && parentFragment.getChatMode() == 0) {
            if (!parentFragment.isThreadChat() && !UserObject.isReplyUser(parentFragment.getCurrentUser())) {
                setOnClickListener(v -> openProfile(false));
            }

            TLRPC.Chat chat = parentFragment.getCurrentChat();
            statusDrawables[0] = new TypingDotsDrawable(true);
            statusDrawables[1] = new RecordStatusDrawable(true);
            statusDrawables[2] = new SendingFileDrawable(true);
            statusDrawables[3] = new PlayingGameDrawable(false, resourcesProvider);
            statusDrawables[4] = new RoundStatusDrawable(true);
            statusDrawables[5] = new ChoosingStickerStatusDrawable(true);
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].setIsChat(chat != null);
            }
        }
    }

    public boolean openSetTimer() {
        if (parentFragment.getParentActivity() == null) {
            return false;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (chat != null && !ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES)) {
            if (timeItem.getTag() != null) {
                parentFragment.showTimerHint();
            }
            return false;
        }
        ClearHistoryAlert alert = new ClearHistoryAlert(parentFragment.getParentActivity(), parentFragment.getCurrentUser(), parentFragment.getCurrentChat(), false, null);
        alert.setDelegate(new ClearHistoryAlert.ClearHistoryAlertDelegate() {
            @Override
            public void onAutoDeleteHistory(int ttl, int action) {
                parentFragment.getMessagesController().setDialogHistoryTTL(parentFragment.getDialogId(), ttl);
                TLRPC.ChatFull chatInfo = parentFragment.getCurrentChatInfo();
                TLRPC.UserFull userInfo = parentFragment.getCurrentUserInfo();
                if (userInfo != null || chatInfo != null) {
                    parentFragment.getUndoView().showWithAction(parentFragment.getDialogId(), action, parentFragment.getCurrentUser(), userInfo != null ? userInfo.ttl_period : chatInfo.ttl_period, null, null);
                }
            }
        });
        parentFragment.showDialog(alert);
        return true;
    }

    private void openProfile(boolean byAvatar) {
        if (byAvatar && (AndroidUtilities.isTablet() || AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y || !avatarImageView.getImageReceiver().hasNotThumb())) {
            byAvatar = false;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        ImageReceiver imageReceiver = avatarImageView.getImageReceiver();
        String key = imageReceiver.getImageKey();
        ImageLoader imageLoader = ImageLoader.getInstance();
        if (key != null && !imageLoader.isInMemCache(key, false)) {
            Drawable drawable = imageReceiver.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                imageLoader.putImageToCache((BitmapDrawable) drawable, key, false);
            }
        }

        if (user != null) {
            Bundle args = new Bundle();
            if (UserObject.isUserSelf(user)) {
                args.putLong("dialog_id", parentFragment.getDialogId());
                int[] media = new int[MediaDataController.MEDIA_TYPES_COUNT];
                System.arraycopy(sharedMediaPreloader.getLastMediaCount(), 0, media, 0, media.length);
                MediaActivity fragment = new MediaActivity(args, sharedMediaPreloader);
                fragment.setChatInfo(parentFragment.getCurrentChatInfo());
                parentFragment.presentFragment(fragment);
            } else {
                args.putLong("user_id", user.id);
                args.putBoolean("reportSpam", parentFragment.hasReportSpam());
                if (timeItem != null) {
                    args.putLong("dialog_id", parentFragment.getDialogId());
                }
                args.putInt("actionBarColor", getThemedColor(Theme.key_actionBarDefault));
                ProfileActivity fragment = new ProfileActivity(args, sharedMediaPreloader);
                fragment.setUserInfo(parentFragment.getCurrentUserInfo());
                fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
                parentFragment.presentFragment(fragment);
            }
        } else if (chat != null) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            ProfileActivity fragment = new ProfileActivity(args, sharedMediaPreloader);
            fragment.setChatInfo(parentFragment.getCurrentChatInfo());
            fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
            parentFragment.presentFragment(fragment);
        }
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
    }

    public void setTitleColors(int title, int subtitle) {
        titleTextView.setTextColor(title);
        subtitleTextView.setTextColor(subtitle);
        subtitleTextView.setTag(subtitle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int availableWidth = width - AndroidUtilities.dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
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
        avatarImageView.layout(leftPadding, viewTop, leftPadding + AndroidUtilities.dp(42), viewTop + AndroidUtilities.dp(42));
        int l = leftPadding + (avatarImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp( 54) : 0);
        if (subtitleTextView.getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(1.3f), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f));
        } else {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(11), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(11));
        }
        if (timeItem != null) {
            timeItem.layout(leftPadding + AndroidUtilities.dp(16), viewTop + AndroidUtilities.dp(15), leftPadding + AndroidUtilities.dp(16 + 34), viewTop + AndroidUtilities.dp(15 + 34));
        }
        subtitleTextView.layout(l, viewTop + AndroidUtilities.dp(24), l + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
    }

    public void setLeftPadding(int value) {
        leftPadding = value;
    }

    public void showTimeItem(boolean animated) {
        if (timeItem == null || timeItem.getTag() != null) {
            return;
        }
        timeItem.clearAnimation();
        timeItem.setVisibility(VISIBLE);
        timeItem.setTag(1);
        if (animated) {
            timeItem.animate().setDuration(180).alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setListener(null).start();
        } else {
            timeItem.setAlpha(1.0f);
            timeItem.setScaleY(1.0f);
            timeItem.setScaleX(1.0f);
        }
    }

    public void hideTimeItem(boolean animated) {
        if (timeItem == null || timeItem.getTag() == null) {
            return;
        }
        timeItem.clearAnimation();
        timeItem.setTag(null);
        if (animated) {
            timeItem.animate().setDuration(180).alpha(0.0f).scaleX(0.0f).scaleY(0.0f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    timeItem.setVisibility(GONE);
                    super.onAnimationEnd(animation);
                }
            }).start();
        } else {
            timeItem.setVisibility(GONE);
            timeItem.setAlpha(0.0f);
            timeItem.setScaleY(0.0f);
            timeItem.setScaleX(0.0f);
        }
    }

    public void setTime(int value) {
        if (timerDrawable == null) {
            return;
        }
        if (value == 0 && !secretChatTimer) {
            return;
        }
        timerDrawable.setTime(value);
    }

    public void setTitleIcons(Drawable leftIcon, Drawable rightIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        if (!(titleTextView.getRightDrawable() instanceof ScamDrawable)) {
            titleTextView.setRightDrawable(rightIcon);
        }
    }

    public void setTitle(CharSequence value) {
        setTitle(value, false, false);
    }

    public void setTitle(CharSequence value, boolean scam, boolean fake) {
        titleTextView.setText(value);
        if (scam || fake) {
            if (!(titleTextView.getRightDrawable() instanceof ScamDrawable)) {
                ScamDrawable drawable = new ScamDrawable(11, scam ? 0 : 1);
                drawable.setColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                titleTextView.setRightDrawable(drawable);
            }
        } else if (titleTextView.getRightDrawable() instanceof ScamDrawable) {
            titleTextView.setRightDrawable(null);
        }
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

    public void onDestroy() {
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.onDestroy(parentFragment);
        }
    }

    private void setTypingAnimation(boolean start) {
        if (start) {
            try {
                int type = MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.getDialogId(), parentFragment.getThreadId());
                if (type == 5) {
                    subtitleTextView.replaceTextWithDrawable(statusDrawables[type], "**oo**");
                    statusDrawables[type].setColor(getThemedColor(Theme.key_chat_status));
                    subtitleTextView.setLeftDrawable(null);
                } else {
                    subtitleTextView.replaceTextWithDrawable(null, null);
                    statusDrawables[type].setColor(getThemedColor(Theme.key_chat_status));
                    subtitleTextView.setLeftDrawable(statusDrawables[type]);
                }
                currentTypingDrawable = statusDrawables[type];
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
            currentTypingDrawable = null;
            subtitleTextView.setLeftDrawable(null);
            subtitleTextView.replaceTextWithDrawable(null, null);
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].stop();
            }
        }
    }

    public void updateSubtitle() {
        updateSubtitle(false);
    }

    public void updateSubtitle(boolean animated) {
        if (parentFragment == null) {
            return;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        if (UserObject.isUserSelf(user) || UserObject.isReplyUser(user) || parentFragment.getChatMode() != 0) {
            if (subtitleTextView.getVisibility() != GONE) {
                subtitleTextView.setVisibility(GONE);
            }
            return;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        CharSequence printString = MessagesController.getInstance(currentAccount).getPrintingString(parentFragment.getDialogId(), parentFragment.getThreadId(), false);
        if (printString != null) {
            printString = TextUtils.replace(printString, new String[]{"..."}, new String[]{""});
        }
        CharSequence newSubtitle;
        boolean useOnlineColor = false;
        if (printString == null || printString.length() == 0 || ChatObject.isChannel(chat) && !chat.megagroup) {
            if (parentFragment.isThreadChat()) {
                if (titleTextView.getTag() != null) {
                    return;
                }
                titleTextView.setTag(1);
                if (titleAnimation != null) {
                    titleAnimation.cancel();
                    titleAnimation = null;
                }
                if (animated) {
                    titleAnimation = new AnimatorSet();
                    titleAnimation.playTogether(
                            ObjectAnimator.ofFloat(titleTextView, View.TRANSLATION_Y, AndroidUtilities.dp(9.7f)),
                            ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, 0.0f));
                    titleAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationCancel(Animator animation) {
                            titleAnimation = null;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (titleAnimation == animation) {
                                subtitleTextView.setVisibility(INVISIBLE);
                                titleAnimation = null;
                            }
                        }
                    });
                    titleAnimation.setDuration(180);
                    titleAnimation.start();
                } else {
                    titleTextView.setTranslationY(AndroidUtilities.dp(9.7f));
                    subtitleTextView.setAlpha(0.0f);
                    subtitleTextView.setVisibility(INVISIBLE);
                }
                return;
            }
            setTypingAnimation(false);
            if (chat != null) {
                TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
                if (ChatObject.isChannel(chat)) {
                    if (info != null && info.participants_count != 0) {
                        if (chat.megagroup) {
                            if (onlineCount > 1) {
                                newSubtitle = String.format("%s, %s", LocaleController.formatPluralString("Members", info.participants_count), LocaleController.formatPluralString("OnlineCount", Math.min(onlineCount, info.participants_count)));
                            } else {
                                newSubtitle = LocaleController.formatPluralString("Members", info.participants_count);
                            }
                        } else {
                            int[] result = new int[1];
                            String shortNumber = LocaleController.formatShortNumber(info.participants_count, result);
                            if (chat.megagroup) {
                                newSubtitle = LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            } else {
                                newSubtitle = LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber);
                            }
                        }
                    } else {
                        if (chat.megagroup) {
                            if (info == null) {
                                newSubtitle = LocaleController.getString("Loading", R.string.Loading).toLowerCase();
                            } else {
                                if (chat.has_geo) {
                                    newSubtitle = LocaleController.getString("MegaLocation", R.string.MegaLocation).toLowerCase();
                                } else if (!TextUtils.isEmpty(chat.username)) {
                                    newSubtitle = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                                } else {
                                    newSubtitle = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                                }
                            }
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
                if (UserObject.isReplyUser(user)) {
                    newStatus = "";
                } else if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    newStatus = LocaleController.getString("ChatYourSelf", R.string.ChatYourSelf);
                } else if (user.id == 333000 || user.id == 777000 || user.id == 42777) {
                    newStatus = LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
                } else if (MessagesController.isSupportUser(user)) {
                    newStatus = LocaleController.getString("SupportStatus", R.string.SupportStatus);
                } else if (user.bot) {
                    newStatus = LocaleController.getString("Bot", R.string.Bot);
                } else {
                    isOnline[0] = false;
                    newStatus = LocaleController.formatUserStatus(currentAccount, user, isOnline);
                    useOnlineColor = isOnline[0];
                }
                newSubtitle = newStatus;
            } else {
                newSubtitle = "";
            }
        } else {
            if (parentFragment.isThreadChat()) {
                if (titleTextView.getTag() != null) {
                    titleTextView.setTag(null);
                    subtitleTextView.setVisibility(VISIBLE);
                    if (titleAnimation != null) {
                        titleAnimation.cancel();
                        titleAnimation = null;
                    }
                    if (animated) {
                        titleAnimation = new AnimatorSet();
                        titleAnimation.playTogether(
                                ObjectAnimator.ofFloat(titleTextView, View.TRANSLATION_Y, 0),
                                ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, 1.0f));
                        titleAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                titleAnimation = null;
                            }
                        });
                        titleAnimation.setDuration(180);
                        titleAnimation.start();
                    } else {
                        titleTextView.setTranslationY(0.0f);
                        subtitleTextView.setAlpha(1.0f);
                    }
                }
            }
            newSubtitle = printString;
            if (MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.getDialogId(), parentFragment.getThreadId()) == 5) {
                newSubtitle = Emoji.replaceEmoji(newSubtitle, subtitleTextView.getTextPaint().getFontMetricsInt(), AndroidUtilities.dp(15), false);
            }
            useOnlineColor = true;
            setTypingAnimation(true);
        }
        lastSubtitleColorKey = useOnlineColor ? Theme.key_chat_status : Theme.key_actionBarDefaultSubtitle;
        if (lastSubtitle == null) {
            subtitleTextView.setText(newSubtitle);
            subtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
            subtitleTextView.setTag(lastSubtitleColorKey);
        } else {
            lastSubtitle = newSubtitle;
        }
    }

    public void setChatAvatar(TLRPC.Chat chat) {
        avatarDrawable.setInfo(chat);
        if (avatarImageView != null) {
            avatarImageView.setForUserOrChat(chat, avatarDrawable);
        }
    }

    public void setUserAvatar(TLRPC.User user) {
        setUserAvatar(user, false);
    }

    public void setUserAvatar(TLRPC.User user, boolean showSelf) {
        avatarDrawable.setInfo(user);
        if (UserObject.isReplyUser(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
            avatarDrawable.setSmallSize(true);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else if (UserObject.isUserSelf(user) && !showSelf) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            avatarDrawable.setSmallSize(true);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else {
            avatarDrawable.setSmallSize(false);
            if (avatarImageView != null) {
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        }
    }

    public void checkAndUpdateAvatar() {
        if (parentFragment == null) {
            return;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (user != null) {
            avatarDrawable.setInfo(user);
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setSmallSize(true);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else if (UserObject.isUserSelf(user)) {
                avatarDrawable.setSmallSize(true);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else {
                avatarDrawable.setSmallSize(false);
                if (avatarImageView != null) {
                    avatarImageView.setForUserOrChat(user, avatarDrawable);
                }
            }
        } else if (chat != null) {
            avatarDrawable.setInfo(chat);
            if (avatarImageView != null) {
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
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
        } else if (info instanceof TLRPC.TL_channelFull && info.participants_count > 200) {
            onlineCount = info.online_count;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
            currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            updateCurrentConnectionState();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdateConnectionState) {
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
                if (lastSubtitleColorKey != null) {
                    subtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                    subtitleTextView.setTag(lastSubtitleColorKey);
                }
            }
        } else {
            if (lastSubtitle == null) {
                lastSubtitle = subtitleTextView.getText();
            }
            subtitleTextView.setText(title);
            subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (info.isClickable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString("OpenProfile", R.string.OpenProfile)));
        }
    }

    public SharedMediaLayout.SharedMediaPreloader getSharedMediaPreloader() {
        return sharedMediaPreloader;
    }

    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public void updateColors() {
        if (currentTypingDrawable != null) {
            currentTypingDrawable.setColor(getThemedColor(Theme.key_chat_status));
        }
    }
}
