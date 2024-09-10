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
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.BusinessLinksController;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.StoriesUtilities;
import org.telegram.ui.TopicsFragment;

import java.util.concurrent.atomic.AtomicReference;

public class ChatAvatarContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public boolean allowDrawStories;
    private Integer storiesForceState;
    public BackupImageView avatarImageView;
    private SimpleTextView titleTextView;
    private AtomicReference<SimpleTextView> titleTextLargerCopyView = new AtomicReference<>();
    private SimpleTextView subtitleTextView;
    private AnimatedTextView animatedSubtitleTextView;
    private AtomicReference<SimpleTextView> subtitleTextLargerCopyView = new AtomicReference<>();
    private ImageView timeItem;
    private ImageView starBgItem, starFgItem;
    private TimerDrawable timerDrawable;
    private ChatActivity parentFragment;
    private StatusDrawable[] statusDrawables = new StatusDrawable[6];
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private int currentAccount = UserConfig.selectedAccount;
    private boolean occupyStatusBar = true;
    private int leftPadding = AndroidUtilities.dp(8);
    private int rightAvatarPadding = 0;
    StatusDrawable currentTypingDrawable;

    private int lastWidth = -1;
    private int largerWidth = -1;


    private AnimatorSet titleAnimation;

    private boolean[] isOnline = new boolean[1];
    public boolean[] statusMadeShorter = new boolean[1];

    private boolean secretChatTimer;

    private int onlineCount = -1;
    private int currentConnectionState;
    private CharSequence lastSubtitle;
    private int lastSubtitleColorKey = -1;
    private Integer overrideSubtitleColor;

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private Theme.ResourcesProvider resourcesProvider;

    public boolean allowShorterStatus = false;
    public boolean premiumIconHiddable = false;

    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatusDrawable;

    protected boolean useAnimatedSubtitle() {
        return false;
    }

    public void hideSubtitle() {
        if (getSubtitleTextView() != null) {
            getSubtitleTextView().setVisibility(View.GONE);
        }
    }

    public void setStoriesForceState(Integer storiesForceState) {
        this.storiesForceState = storiesForceState;
    }

    private class SimpleTextConnectedView extends SimpleTextView {

        private AtomicReference<SimpleTextView> reference;
        public SimpleTextConnectedView(Context context, AtomicReference<SimpleTextView> reference) {
            super(context);
            this.reference = reference;
        }

        @Override
        public void setTranslationY(float translationY) {
            if (reference != null) {
                SimpleTextView connected = reference.get();
                if (connected != null) {
                    connected.setTranslationY(translationY);
                }
            }
            super.setTranslationY(translationY);
        }

        @Override
        public boolean setText(CharSequence value) {
            if (reference != null) {
                SimpleTextView connected = reference.get();
                if (connected != null) {
                    connected.setText(value);
                }
            }
            return super.setText(value);
        }
    }

    public ChatAvatarContainer(Context context, BaseFragment baseFragment, boolean needTime) {
        this(context, baseFragment, needTime, null);
    }

    public ChatAvatarContainer(Context context, BaseFragment baseFragment, boolean needTime, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        if (baseFragment instanceof ChatActivity) {
            parentFragment = (ChatActivity) baseFragment;
        }

        final boolean avatarClickable = parentFragment != null && parentFragment.getChatMode() == 0 && !UserObject.isReplyUser(parentFragment.getCurrentUser());
        avatarImageView = new BackupImageView(context) {

            StoriesUtilities.AvatarStoryParams params = new StoriesUtilities.AvatarStoryParams(true) {
                @Override
                public void openStory(long dialogId, Runnable onDone) {
                    baseFragment.getOrCreateStoryViewer().open(getContext(), dialogId, (dialogId1, messageId, storyId, type, holder) -> {
                        holder.crossfadeToAvatarImage = holder.storyImage = imageReceiver;
                        holder.params = params;
                        holder.view = avatarImageView;
                        holder.alpha = avatarImageView.getAlpha();
                        holder.clipTop = 0;
                        holder.clipBottom = AndroidUtilities.displaySize.y;
                        holder.clipParent = (View) getParent();
                        return true;
                    });
                }
            };

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (avatarClickable && getImageReceiver().hasNotThumb()) {
                    info.setText(LocaleController.getString(R.string.AccDescrProfilePicture));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString(R.string.Open)));
                    }
                } else {
                    info.setVisibleToUser(false);
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (allowDrawStories && animatedEmojiDrawable == null) {
                    params.originalAvatarRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    params.drawSegments = true;
                    params.drawInside = true;
                    params.resourcesProvider = resourcesProvider;
                    if (storiesForceState != null) {
                        params.forceState = storiesForceState;
                    }

                    long dialogId = 0;
                    if (parentFragment != null) {
                        dialogId = parentFragment.getDialogId();
                    } else if (baseFragment instanceof TopicsFragment) {
                        dialogId = ((TopicsFragment) baseFragment).getDialogId();
                    }

                    StoriesUtilities.drawAvatarWithStory(dialogId, canvas, imageReceiver, params);
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (allowDrawStories) {
                    if (params.checkOnTouchEvent(event, this)) {
                        return true;
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        if (baseFragment instanceof ChatActivity || baseFragment instanceof TopicsFragment) {
            if (parentFragment == null || (parentFragment.getChatMode() != ChatActivity.MODE_QUICK_REPLIES && parentFragment.getChatMode() != ChatActivity.MODE_EDIT_BUSINESS_LINK)) {
                sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(baseFragment);
            }
            if (parentFragment != null && (parentFragment.isThreadChat() || parentFragment.getChatMode() == ChatActivity.MODE_PINNED || parentFragment.getChatMode() == ChatActivity.MODE_QUICK_REPLIES || parentFragment.getChatMode() == ChatActivity.MODE_EDIT_BUSINESS_LINK)) {
                avatarImageView.setVisibility(GONE);
            }
        }
        avatarImageView.setContentDescription(LocaleController.getString(R.string.AccDescrProfilePicture));
        avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        addView(avatarImageView);
        if (avatarClickable) {
            avatarImageView.setOnClickListener(v -> {
                if (!onAvatarClick()) {
                    openProfile(true);
                }
            });
        }

        titleTextView = new SimpleTextConnectedView(context, titleTextLargerCopyView);
        titleTextView.setEllipsizeByGradient(true);
        titleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTextSize(18);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        titleTextView.setCanHideRightDrawable(false);
        titleTextView.setRightDrawableOutside(true);
        titleTextView.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
        addView(titleTextView);

        if (useAnimatedSubtitle()) {
            animatedSubtitleTextView = new AnimatedTextView(context, true, true, true);
            animatedSubtitleTextView.setAnimationProperties(.3f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            animatedSubtitleTextView.setEllipsizeByGradient(true);
            animatedSubtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            animatedSubtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
            animatedSubtitleTextView.setTextSize(AndroidUtilities.dp(14));
            animatedSubtitleTextView.setGravity(Gravity.LEFT);
            animatedSubtitleTextView.setPadding(0, 0, AndroidUtilities.dp(10), 0);
            animatedSubtitleTextView.setTranslationY(-AndroidUtilities.dp(1));
            addView(animatedSubtitleTextView);
        } else {
            subtitleTextView = new SimpleTextConnectedView(context, subtitleTextLargerCopyView);
            subtitleTextView.setEllipsizeByGradient(true);
            subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
            subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
            subtitleTextView.setTextSize(14);
            subtitleTextView.setGravity(Gravity.LEFT);
            subtitleTextView.setPadding(0, 0, AndroidUtilities.dp(10), 0);
            addView(subtitleTextView);
        }

        if (parentFragment != null) {
            timeItem = new ImageView(context);
            timeItem.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            timeItem.setScaleType(ImageView.ScaleType.CENTER);
            timeItem.setAlpha(0.0f);
            timeItem.setScaleY(0.0f);
            timeItem.setScaleX(0.0f);
            timeItem.setVisibility(GONE);
            timeItem.setImageDrawable(timerDrawable = new TimerDrawable(context, resourcesProvider));
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
                timeItem.setContentDescription(LocaleController.getString(R.string.SetTimer));
            } else {
                timeItem.setContentDescription(LocaleController.getString(R.string.AccAutoDeleteTimer));
            }

            starBgItem = new ImageView(context);
            starBgItem.setImageResource(R.drawable.star_small_outline);
            starBgItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefault), PorterDuff.Mode.SRC_IN));
            starBgItem.setAlpha(0.0f);
            starBgItem.setScaleY(0.0f);
            starBgItem.setScaleX(0.0f);
            addView(starBgItem);

            starFgItem = new ImageView(context);
            starFgItem.setImageResource(R.drawable.star_small_inner);
            starFgItem.setAlpha(0.0f);
            starFgItem.setScaleY(0.0f);
            starFgItem.setScaleX(0.0f);
            addView(starFgItem);
        }

        if (parentFragment != null && (parentFragment.getChatMode() == 0 || parentFragment.getChatMode() == ChatActivity.MODE_SAVED)) {
            if ((!parentFragment.isThreadChat() || parentFragment.isTopic) && !UserObject.isReplyUser(parentFragment.getCurrentUser())) {
                setOnClickListener(v -> {
                    openProfile(false);
                });
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

        emojiStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(titleTextView, AndroidUtilities.dp(24));
    }

    public ButtonBounce bounce = new ButtonBounce(this);
    private Runnable onLongClick = () -> {
        pressed = false;
        bounce.setPressed(false);
        if (canSearch()) {
            openSearch();
        }
    };

    private boolean pressed;
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && canSearch()) {
            pressed = true;
            bounce.setPressed(true);
            AndroidUtilities.cancelRunOnUIThread(this.onLongClick);
            AndroidUtilities.runOnUIThread(this.onLongClick, ViewConfiguration.getLongPressTimeout());
            return true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                bounce.setPressed(false);
                pressed = false;
                if (isClickable()) {
                    openProfile(false);
                }
                AndroidUtilities.cancelRunOnUIThread(this.onLongClick);
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        final float s = bounce.getScale(.02f);
        canvas.scale(s, s, getWidth() / 2f, getHeight() / 2f);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public boolean ignoreTouches;
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ignoreTouches) return false;
        return super.dispatchTouchEvent(ev);
    }

    protected boolean canSearch() {
        return false;
    }

    protected void openSearch() {

    }

    protected boolean onAvatarClick() {
        return false;
    }

    public void setTitleExpand(boolean titleExpand) {
        int newRightPadding = titleExpand ? AndroidUtilities.dp(10) : 0;
        if (titleTextView.getPaddingRight() != newRightPadding) {
            titleTextView.setPadding(0, AndroidUtilities.dp(6), newRightPadding, AndroidUtilities.dp(12));
            requestLayout();
            invalidate();
        }
    }

    public void setOverrideSubtitleColor(Integer overrideSubtitleColor) {
        this.overrideSubtitleColor = overrideSubtitleColor;
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
        TLRPC.ChatFull chatInfo = parentFragment.getCurrentChatInfo();
        TLRPC.UserFull userInfo = parentFragment.getCurrentUserInfo();
        int ttl = 0;
        if (userInfo != null) {
            ttl = userInfo.ttl_period;
        } else if (chatInfo != null) {
            ttl = chatInfo.ttl_period;
        }

        ActionBarPopupWindow[] scrimPopupWindow = new ActionBarPopupWindow[1];
        AutoDeletePopupWrapper autoDeletePopupWrapper = new AutoDeletePopupWrapper(getContext(), null, new AutoDeletePopupWrapper.Callback() {
            @Override
            public void dismiss() {
                if (scrimPopupWindow[0] != null) {
                    scrimPopupWindow[0].dismiss();
                }
            }

            @Override
            public void setAutoDeleteHistory(int time, int action) {
                if (parentFragment == null) {
                    return;
                }
                parentFragment.getMessagesController().setDialogHistoryTTL(parentFragment.getDialogId(), time);
                TLRPC.ChatFull chatInfo = parentFragment.getCurrentChatInfo();
                TLRPC.UserFull userInfo = parentFragment.getCurrentUserInfo();
                if (userInfo != null || chatInfo != null) {
                    UndoView undoView = parentFragment.getUndoView();
                    if (undoView != null) {
                        undoView.showWithAction(parentFragment.getDialogId(), action, parentFragment.getCurrentUser(), userInfo != null ? userInfo.ttl_period : chatInfo.ttl_period, null, null);
                    }
                }

            }
        }, true, 0, resourcesProvider);
        autoDeletePopupWrapper.updateItems(ttl);

        scrimPopupWindow[0] = new ActionBarPopupWindow(autoDeletePopupWrapper.windowLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (parentFragment != null) {
                    parentFragment.dimBehindView(false);
                }
            }
        };
        scrimPopupWindow[0].setPauseNotifications(true);
        scrimPopupWindow[0].setDismissAnimationDuration(220);
        scrimPopupWindow[0].setOutsideTouchable(true);
        scrimPopupWindow[0].setClippingEnabled(true);
        scrimPopupWindow[0].setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow[0].setFocusable(true);
        autoDeletePopupWrapper.windowLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow[0].setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow[0].getContentView().setFocusableInTouchMode(true);
        scrimPopupWindow[0].showAtLocation(avatarImageView, 0, (int) (avatarImageView.getX() + getX()), (int) avatarImageView.getY());
        parentFragment.dimBehindView(true);
        return true;
    }

    public void openProfile(boolean byAvatar) {
        openProfile(byAvatar, true, false);
    }

    public void openProfile(boolean byAvatar, boolean fromChatAnimation, boolean removeLast) {
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
            if (drawable instanceof BitmapDrawable && !(drawable instanceof AnimatedFileDrawable)) {
                imageLoader.putImageToCache((BitmapDrawable) drawable, key, false);
            }
        }

        if (user != null) {
            Bundle args = new Bundle();
            if (UserObject.isUserSelf(user)) {
                if (!sharedMediaPreloader.hasSharedMedia()) {
                    return;
                }
                args.putLong("dialog_id", parentFragment.getDialogId());
                if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                    args.putLong("topic_id", parentFragment.getSavedDialogId());
                }
                MediaActivity fragment = new MediaActivity(args, sharedMediaPreloader);
                fragment.setChatInfo(parentFragment.getCurrentChatInfo());
                parentFragment.presentFragment(fragment, removeLast);
            } else {
                if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                    long dialogId = parentFragment.getSavedDialogId();
                    args.putBoolean("saved", true);
                    if (dialogId >= 0) {
                        args.putLong("user_id", dialogId);
                    } else {
                        args.putLong("chat_id", -dialogId);
                    }
                } else {
                    args.putLong("user_id", user.id);
                    if (timeItem != null) {
                        args.putLong("dialog_id", parentFragment.getDialogId());
                    }
                }
                args.putBoolean("reportSpam", parentFragment.hasReportSpam());
                args.putInt("actionBarColor", getThemedColor(Theme.key_actionBarDefault));
                ProfileActivity fragment = new ProfileActivity(args, sharedMediaPreloader);
                fragment.setUserInfo(parentFragment.getCurrentUserInfo(), parentFragment.profileChannelMessageFetcher, parentFragment.birthdayAssetsFetcher);
                if (fromChatAnimation) {
                    fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
                }
                parentFragment.presentFragment(fragment, removeLast);
            }
        } else if (chat != null) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                args.putLong("topic_id", parentFragment.getSavedDialogId());
            } else if (parentFragment.isTopic) {
                args.putLong("topic_id", parentFragment.getThreadMessage().getId());
            }
            ProfileActivity fragment = new ProfileActivity(args, sharedMediaPreloader);
            fragment.setChatInfo(parentFragment.getCurrentChatInfo());
            if (fromChatAnimation) {
                fragment.setPlayProfileAnimation(byAvatar ? 2 : 1);
            }
            parentFragment.presentFragment(fragment, removeLast);
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
        int width = MeasureSpec.getSize(widthMeasureSpec) + titleTextView.getPaddingRight();
        int availableWidth = width - AndroidUtilities.dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
        avatarImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24 + 8) + titleTextView.getPaddingRight(), MeasureSpec.AT_MOST));
        if (subtitleTextView != null) {
            subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        } else if (animatedSubtitleTextView != null) {
            animatedSubtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        }
        if (timeItem != null) {
            timeItem.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34), MeasureSpec.EXACTLY));
        }
        if (starBgItem != null) {
            starBgItem.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        }
        if (starFgItem != null) {
            starFgItem.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        if (lastWidth != -1 && lastWidth != width && lastWidth > width) {
            fadeOutToLessWidth(lastWidth);
        }
        SimpleTextView titleTextLargerCopyView = this.titleTextLargerCopyView.get();
        if (titleTextLargerCopyView != null) {
            int largerAvailableWidth = largerWidth - AndroidUtilities.dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
            titleTextLargerCopyView.measure(MeasureSpec.makeMeasureSpec(largerAvailableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));
        }
        lastWidth = width;
    }

    private void fadeOutToLessWidth(int largerWidth) {
        this.largerWidth = largerWidth;
        SimpleTextView titleTextLargerCopyView = this.titleTextLargerCopyView.get();
        if (titleTextLargerCopyView != null) {
            removeView(titleTextLargerCopyView);
        }
        titleTextLargerCopyView = new SimpleTextView(getContext());
        this.titleTextLargerCopyView.set(titleTextLargerCopyView);
        titleTextLargerCopyView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        titleTextLargerCopyView.setTextSize(18);
        titleTextLargerCopyView.setGravity(Gravity.LEFT);
        titleTextLargerCopyView.setTypeface(AndroidUtilities.bold());
        titleTextLargerCopyView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        titleTextLargerCopyView.setRightDrawable(titleTextView.getRightDrawable());
        titleTextLargerCopyView.setRightDrawable2(titleTextView.getRightDrawable2());
        titleTextLargerCopyView.setRightDrawableOutside(titleTextView.getRightDrawableOutside());
        titleTextLargerCopyView.setLeftDrawable(titleTextView.getLeftDrawable());
        titleTextLargerCopyView.setText(titleTextView.getText());
        titleTextLargerCopyView.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
            SimpleTextView titleTextLargerCopyView2 = this.titleTextLargerCopyView.get();
            if (titleTextLargerCopyView2 != null) {
                removeView(titleTextLargerCopyView2);
                this.titleTextLargerCopyView.set(null);
            }
        }).start();
        addView(titleTextLargerCopyView);

        SimpleTextView subtitleTextLargerCopyView = this.subtitleTextLargerCopyView.get();
        if (subtitleTextLargerCopyView != null) {
            removeView(subtitleTextLargerCopyView);
        }
        subtitleTextLargerCopyView = new SimpleTextView(getContext());
        this.subtitleTextLargerCopyView.set(subtitleTextLargerCopyView);
        subtitleTextLargerCopyView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        subtitleTextLargerCopyView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextLargerCopyView.setTextSize(14);
        subtitleTextLargerCopyView.setGravity(Gravity.LEFT);
        if (subtitleTextView != null) {
            subtitleTextLargerCopyView.setText(subtitleTextView.getText());
        } else if (animatedSubtitleTextView != null) {
            subtitleTextLargerCopyView.setText(animatedSubtitleTextView.getText());
        }
        subtitleTextLargerCopyView.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
            SimpleTextView subtitleTextLargerCopyView2 = this.subtitleTextLargerCopyView.get();
            if (subtitleTextLargerCopyView2 != null) {
                removeView(subtitleTextLargerCopyView2);
                this.subtitleTextLargerCopyView.set(null);
                if (!allowDrawStories) {
                    setClipChildren(true);
                }
            }
        }).start();
        addView(subtitleTextLargerCopyView);

        setClipChildren(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        int viewTop = (actionBarHeight - AndroidUtilities.dp(42)) / 2 + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
        avatarImageView.layout(leftPadding, viewTop + 1, leftPadding + AndroidUtilities.dp(42), viewTop + 1 + AndroidUtilities.dp(42));
        int l = leftPadding + (avatarImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp( 54) : 0) + rightAvatarPadding;
        SimpleTextView titleTextLargerCopyView = this.titleTextLargerCopyView.get();
        if (getSubtitleTextView().getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(1.3f) - titleTextView.getPaddingTop(), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f) - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom());
            if (titleTextLargerCopyView != null) {
                titleTextLargerCopyView.layout(l, viewTop + AndroidUtilities.dp(1.3f), l + titleTextLargerCopyView.getMeasuredWidth(), viewTop + titleTextLargerCopyView.getTextHeight() + AndroidUtilities.dp(1.3f));
            }
        } else {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(11) - titleTextView.getPaddingTop(), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(11) - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom());
            if (titleTextLargerCopyView != null) {
                titleTextLargerCopyView.layout(l, viewTop + AndroidUtilities.dp(11), l + titleTextLargerCopyView.getMeasuredWidth(), viewTop + titleTextLargerCopyView.getTextHeight() + AndroidUtilities.dp(11));
            }
        }
        if (timeItem != null) {
            timeItem.layout(leftPadding + AndroidUtilities.dp(16), viewTop + AndroidUtilities.dp(15), leftPadding + AndroidUtilities.dp(16 + 34), viewTop + AndroidUtilities.dp(15 + 34));
        }
        if (starBgItem != null) {
            starBgItem.layout(leftPadding + AndroidUtilities.dp(28), viewTop + AndroidUtilities.dp(24), leftPadding + AndroidUtilities.dp(28) + starBgItem.getMeasuredWidth(), viewTop + AndroidUtilities.dp(24) + starBgItem.getMeasuredHeight());
        }
        if (starFgItem != null) {
            starFgItem.layout(leftPadding + AndroidUtilities.dp(28), viewTop + AndroidUtilities.dp(24), leftPadding + AndroidUtilities.dp(28) + starFgItem.getMeasuredWidth(), viewTop + AndroidUtilities.dp(24) + starFgItem.getMeasuredHeight());
        }
        if (subtitleTextView != null) {
            subtitleTextView.layout(l, viewTop + AndroidUtilities.dp(24), l + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
        } else if (animatedSubtitleTextView != null) {
            animatedSubtitleTextView.layout(l, viewTop + AndroidUtilities.dp(24), l + animatedSubtitleTextView.getMeasuredWidth(), viewTop + animatedSubtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
        }
        SimpleTextView subtitleTextLargerCopyView = this.subtitleTextLargerCopyView.get();
        if (subtitleTextLargerCopyView != null) {
            subtitleTextLargerCopyView.layout(l, viewTop + AndroidUtilities.dp(24), l + subtitleTextLargerCopyView.getMeasuredWidth(), viewTop + subtitleTextLargerCopyView.getTextHeight() + AndroidUtilities.dp(24));
        }
    }

    public void setLeftPadding(int value) {
        leftPadding = value;
    }

    public void setRightAvatarPadding(int value) {
        rightAvatarPadding = value;
    }

    public void showTimeItem(boolean animated) {
        if (timeItem == null || timeItem.getTag() != null || avatarImageView.getVisibility() != View.VISIBLE) {
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

    public void setTime(int value, boolean animated) {
        if (timerDrawable == null) {
            return;
        }
        boolean show = !stars;
        if (value == 0 && !secretChatTimer) {
            show = false;
            return;
        }
        if (show) {
            showTimeItem(animated);
            timerDrawable.setTime(value);
        } else {
            hideTimeItem(animated);
        }
    }

    public boolean stars;
    public void setStars(boolean stars, boolean animated) {
        if (starBgItem == null || starFgItem == null) return;
        this.stars = stars;
        if (!animated) {
            starBgItem.setAlpha(stars ? 1f : 0f);
            starBgItem.setScaleX(stars ? 1.1f : 0f);
            starBgItem.setScaleY(stars ? 1.1f : 0f);
            starFgItem.setAlpha(stars ? 1f : 0f);
            starFgItem.setScaleX(stars ? 1f : 0f);
            starFgItem.setScaleY(stars ? 1f : 0f);
        } else {
            starBgItem.animate().alpha(stars ? 1f : 0f).scaleX(stars ? 1.1f : 0f).scaleY(stars ? 1.1f : 0f).start();
            starFgItem.animate().alpha(stars ? 1f : 0f).scaleX(stars ? 1f : 0f).scaleY(stars ? 1f : 0f).start();
        }
    }

    private boolean rightDrawableIsScamOrVerified = false;
    private String rightDrawableContentDescription = null;
    private String rightDrawable2ContentDescription = null;

    public void setTitleIcons(Drawable leftIcon, Drawable mutedIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        if (!rightDrawableIsScamOrVerified) {
            if (mutedIcon != null) {
                rightDrawable2ContentDescription = LocaleController.getString(R.string.NotificationsMuted);
            } else {
                rightDrawable2ContentDescription = null;
            }
            titleTextView.setRightDrawable2(mutedIcon);
        }
    }

    public void setTitle(CharSequence value) {
        setTitle(value, false, false, false, false, null, false);
    }

    public void setTitle(CharSequence value, boolean scam, boolean fake, boolean verified, boolean premium, TLRPC.EmojiStatus emojiStatus, boolean animated) {
        if (value != null) {
            value = Emoji.replaceEmoji(value, titleTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(24), false);
        }
        titleTextView.setText(value);
        if (scam || fake) {
            if (!(titleTextView.getRightDrawable() instanceof ScamDrawable)) {
                ScamDrawable drawable = new ScamDrawable(11, scam ? 0 : 1);
                drawable.setColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                titleTextView.setRightDrawable2(drawable);
//                titleTextView.setRightPadding(0);
                rightDrawable2ContentDescription = LocaleController.getString(R.string.ScamMessage);
                rightDrawableIsScamOrVerified = true;
            }
        } else if (verified) {
            Drawable verifiedBackground = getResources().getDrawable(R.drawable.verified_area).mutate();
            verifiedBackground.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
            Drawable verifiedCheck = getResources().getDrawable(R.drawable.verified_check).mutate();
            verifiedCheck.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_profile_verifiedCheck), PorterDuff.Mode.MULTIPLY));
            Drawable verifiedDrawable = new CombinedDrawable(verifiedBackground, verifiedCheck);
            titleTextView.setRightDrawable2(verifiedDrawable);
            rightDrawableIsScamOrVerified = true;
            rightDrawable2ContentDescription = LocaleController.getString(R.string.AccDescrVerified);
        } else if (titleTextView.getRightDrawable() instanceof ScamDrawable) {
            titleTextView.setRightDrawable2(null);
            rightDrawableIsScamOrVerified = false;
            rightDrawable2ContentDescription = null;
        }
        if (premium || DialogObject.getEmojiStatusDocumentId(emojiStatus) != 0) {
            if (titleTextView.getRightDrawable() instanceof AnimatedEmojiDrawable.WrapSizeDrawable &&
                ((AnimatedEmojiDrawable.WrapSizeDrawable) titleTextView.getRightDrawable()).getDrawable() instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) ((AnimatedEmojiDrawable.WrapSizeDrawable) titleTextView.getRightDrawable()).getDrawable()).removeView(titleTextView);
            }
            if (DialogObject.getEmojiStatusDocumentId(emojiStatus) != 0) {
                emojiStatusDrawable.set(DialogObject.getEmojiStatusDocumentId(emojiStatus), animated);
            } else if (premium) {
                Drawable drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_premium_liststar).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
                emojiStatusDrawable.set(drawable, animated);
            } else {
                emojiStatusDrawable.set((Drawable) null, animated);
            }
            emojiStatusDrawable.setColor(getThemedColor(Theme.key_profile_verifiedBackground));
            titleTextView.setRightDrawable(emojiStatusDrawable);
            rightDrawableIsScamOrVerified = false;
            rightDrawableContentDescription = LocaleController.getString(R.string.AccDescrPremium);
        } else {
            titleTextView.setRightDrawable(null);
            rightDrawableContentDescription = null;
        }
    }

    public void setSubtitle(CharSequence value) {
        if (lastSubtitle == null) {
            if (subtitleTextView != null) {
                subtitleTextView.setText(value);
            } else if (animatedSubtitleTextView != null) {
                animatedSubtitleTextView.setText(value);
            }
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

    public View getSubtitleTextView() {
        if (subtitleTextView != null) {
            return subtitleTextView;
        }
        if (animatedSubtitleTextView != null) {
            return animatedSubtitleTextView;
        }
        return null;
    }

    public TextPaint getSubtitlePaint() {
        return subtitleTextView != null ? subtitleTextView.getTextPaint() : animatedSubtitleTextView.getPaint();
    }

    public void onDestroy() {
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.onDestroy(parentFragment);
        }
    }

    private void setTypingAnimation(boolean start) {
        if (subtitleTextView == null) return;
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
        if (parentFragment.getChatMode() == ChatActivity.MODE_EDIT_BUSINESS_LINK) {
            setSubtitle(BusinessLinksController.stripHttps(parentFragment.businessLink.link));
            return;
        }
        TLRPC.User user = parentFragment.getCurrentUser();
        if ((UserObject.isUserSelf(user) || UserObject.isReplyUser(user) || parentFragment.getChatMode() != 0) && parentFragment.getChatMode() != ChatActivity.MODE_SAVED) {
            if (getSubtitleTextView().getVisibility() != GONE) {
                getSubtitleTextView().setVisibility(GONE);
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
            if (parentFragment.isThreadChat() && !parentFragment.isTopic) {
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
                            ObjectAnimator.ofFloat(getSubtitleTextView(), View.ALPHA, 0.0f));
                    titleAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationCancel(Animator animation) {
                            titleAnimation = null;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (titleAnimation == animation) {
                                getSubtitleTextView().setVisibility(INVISIBLE);
                                titleAnimation = null;
                            }
                        }
                    });
                    titleAnimation.setDuration(180);
                    titleAnimation.start();
                } else {
                    titleTextView.setTranslationY(AndroidUtilities.dp(9.7f));
                    getSubtitleTextView().setAlpha(0.0f);
                    getSubtitleTextView().setVisibility(INVISIBLE);
                }
                return;
            }
            setTypingAnimation(false);
            if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                int messagesCount = parentFragment.getMessagesController().getSavedMessagesController().getMessagesCount(parentFragment.getSavedDialogId());
                newSubtitle = LocaleController.formatPluralString("SavedMessagesCount", Math.max(1, messagesCount));
            } else if (parentFragment.isTopic && chat != null) {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, parentFragment.getTopicId());
                int count = 0;
                if (topic != null) {
                    count = topic.totalMessagesCount - 1;
                }
                if (count > 0) {
                    newSubtitle = LocaleController.formatPluralString("messages", count, count);
                } else {
                    newSubtitle = LocaleController.formatString(R.string.TopicProfileStatus, chat.title);
                }
            } else if (chat != null) {
                TLRPC.ChatFull info = parentFragment.getCurrentChatInfo();
                newSubtitle = getChatSubtitle(chat, info, onlineCount);
            } else if (user != null) {
                TLRPC.User newUser = MessagesController.getInstance(currentAccount).getUser(user.id);
                if (newUser != null) {
                    user = newUser;
                }
                String newStatus;
                if (UserObject.isReplyUser(user)) {
                    newStatus = "";
                } else if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    newStatus = LocaleController.getString(R.string.ChatYourSelf);
                } else if (user.id == 333000 || user.id == 777000 || user.id == 42777) {
                    newStatus = LocaleController.getString(R.string.ServiceNotifications);
                } else if (MessagesController.isSupportUser(user)) {
                    newStatus = LocaleController.getString(R.string.SupportStatus);
                } else if (user.bot && user.bot_active_users != 0) {
                    newStatus = LocaleController.formatPluralStringComma("BotUsers", user.bot_active_users, ',');
                } else if (user.bot) {
                    newStatus = LocaleController.getString(R.string.Bot);
                } else {
                    isOnline[0] = false;
                    newStatus = LocaleController.formatUserStatus(currentAccount, user, isOnline, allowShorterStatus ? statusMadeShorter : null);
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
                    getSubtitleTextView().setVisibility(VISIBLE);
                    if (titleAnimation != null) {
                        titleAnimation.cancel();
                        titleAnimation = null;
                    }
                    if (animated) {
                        titleAnimation = new AnimatorSet();
                        titleAnimation.playTogether(
                                ObjectAnimator.ofFloat(titleTextView, View.TRANSLATION_Y, 0),
                                ObjectAnimator.ofFloat(getSubtitleTextView(), View.ALPHA, 1.0f));
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
                        getSubtitleTextView().setAlpha(1.0f);
                    }
                }
            }
            newSubtitle = printString;
            if (MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.getDialogId(), parentFragment.getThreadId()) == 5) {
                newSubtitle = Emoji.replaceEmoji(newSubtitle, getSubtitlePaint().getFontMetricsInt(), AndroidUtilities.dp(15), false);
            }
            useOnlineColor = true;
            setTypingAnimation(true);
        }
        lastSubtitleColorKey = useOnlineColor ? Theme.key_chat_status : Theme.key_actionBarDefaultSubtitle;
        if (lastSubtitle == null) {
            if (subtitleTextView != null) {
                subtitleTextView.setText(newSubtitle);
                if (overrideSubtitleColor == null) {
                    subtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                    subtitleTextView.setTag(lastSubtitleColorKey);
                } else {
                    subtitleTextView.setTextColor(overrideSubtitleColor);
                }
            } else {
                animatedSubtitleTextView.setText(newSubtitle, animated);
                if (overrideSubtitleColor == null) {
                    animatedSubtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                    animatedSubtitleTextView.setTag(lastSubtitleColorKey);
                } else {
                    animatedSubtitleTextView.setTextColor(overrideSubtitleColor);
                }
            }
        } else {
            lastSubtitle = newSubtitle;
        }
    }

    public static CharSequence getChatSubtitle(TLRPC.Chat chat, TLRPC.ChatFull info, int onlineCount) {
        CharSequence newSubtitle = null;
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
                    boolean ignoreShort = AndroidUtilities.isAccessibilityScreenReaderEnabled();
                    String shortNumber = ignoreShort ? String.valueOf(result[0] = info.participants_count) : LocaleController.formatShortNumber(info.participants_count, result);
                    if (chat.megagroup) {
                        newSubtitle = LocaleController.formatPluralString("Members", result[0]).replace(String.format("%d", result[0]), shortNumber);
                    } else {
                        newSubtitle = LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format("%d", result[0]), shortNumber);
                    }
                }
            } else {
                if (chat.megagroup) {
                    if (info == null) {
                        newSubtitle = LocaleController.getString(R.string.Loading).toLowerCase();
                    } else {
                        if (chat.has_geo) {
                            newSubtitle = LocaleController.getString(R.string.MegaLocation).toLowerCase();
                        } else if (ChatObject.isPublic(chat)) {
                            newSubtitle = LocaleController.getString(R.string.MegaPublic).toLowerCase();
                        } else {
                            newSubtitle = LocaleController.getString(R.string.MegaPrivate).toLowerCase();
                        }
                    }
                } else {
                    if (ChatObject.isPublic(chat)) {
                        newSubtitle = LocaleController.getString(R.string.ChannelPublic).toLowerCase();
                    } else {
                        newSubtitle = LocaleController.getString(R.string.ChannelPrivate).toLowerCase();
                    }
                }
            }
        } else {
            if (ChatObject.isKickedFromChat(chat)) {
                newSubtitle = LocaleController.getString(R.string.YouWereKicked);
            } else if (ChatObject.isLeftFromChat(chat)) {
                newSubtitle = LocaleController.getString(R.string.YouLeft);
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
        return newSubtitle;
    }

    public int getLastSubtitleColorKey() {
        return lastSubtitleColorKey;
    }

    public void setChatAvatar(TLRPC.Chat chat) {
        avatarDrawable.setInfo(currentAccount, chat);
        if (avatarImageView != null) {
            avatarImageView.setForUserOrChat(chat, avatarDrawable);
            avatarImageView.setRoundRadius(ChatObject.isForum(chat) ? AndroidUtilities.dp(ChatObject.hasStories(chat) ? 11 : 16) : AndroidUtilities.dp(21));
        }
    }

    public void setUserAvatar(TLRPC.User user) {
        setUserAvatar(user, false);
    }

    public void setUserAvatar(TLRPC.User user, boolean showSelf) {
        avatarDrawable.setInfo(currentAccount, user);
        if (UserObject.isReplyUser(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
            avatarDrawable.setScaleSize(.8f);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else if (UserObject.isAnonymous(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
            avatarDrawable.setScaleSize(.8f);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else if (UserObject.isUserSelf(user) && !showSelf) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            avatarDrawable.setScaleSize(.8f);
            if (avatarImageView != null) {
                avatarImageView.setImage(null, null, avatarDrawable, user);
            }
        } else {
            avatarDrawable.setScaleSize(1f);
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
        if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
            long dialogId = parentFragment.getSavedDialogId();
            if (dialogId >= 0) {
                user = parentFragment.getMessagesController().getUser(dialogId);
                chat = null;
            } else {
                user = null;
                chat = parentFragment.getMessagesController().getChat(-dialogId);
            }
        }
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
            if (UserObject.isReplyUser(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else if (UserObject.isAnonymous(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else if (UserObject.isUserSelf(user) && parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_MY_NOTES);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else if (UserObject.isUserSelf(user)) {
                avatarDrawable.setScaleSize(.8f);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                if (avatarImageView != null) {
                    avatarImageView.setImage(null, null, avatarDrawable, user);
                }
            } else {
                avatarDrawable.setScaleSize(1f);
                if (avatarImageView != null) {
                    avatarImageView.imageReceiver.setForUserOrChat(user, avatarDrawable,  null, true, VectorAvatarThumbDrawable.TYPE_STATIC, false);
                }
            }
        } else if (chat != null) {
            avatarDrawable.setInfo(currentAccount, chat);
            if (avatarImageView != null) {
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
            avatarImageView.setRoundRadius(chat.forum ? AndroidUtilities.dp(ChatObject.hasStories(chat) ? 11 : 16) : AndroidUtilities.dp(21));
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
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
            if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
            }
            currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
            updateCurrentConnectionState();
        }
        if (emojiStatusDrawable != null) {
            emojiStatusDrawable.attach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (parentFragment != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
            if (parentFragment.getChatMode() == ChatActivity.MODE_SAVED) {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
            }
        }
        if (emojiStatusDrawable != null) {
            emojiStatusDrawable.detach();
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
        } else if (id == NotificationCenter.emojiLoaded) {
            if (titleTextView != null) {
                titleTextView.invalidate();
            }
            if (getSubtitleTextView() != null) {
                getSubtitleTextView().invalidate();
            }
            invalidate();
        } else if (id == NotificationCenter.savedMessagesDialogsUpdate) {
            updateSubtitle(true);
        }
    }

    private void updateCurrentConnectionState() {
        String title = null;
        if (currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            title = LocaleController.getString(R.string.WaitingForNetwork);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnecting) {
            title = LocaleController.getString(R.string.Connecting);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
            title = LocaleController.getString(R.string.Updating);
        } else if (currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy) {
            title = LocaleController.getString(R.string.ConnectingToProxy);
        }
        if (title == null) {
            if (lastSubtitle != null) {
                if (subtitleTextView != null) {
                    subtitleTextView.setText(lastSubtitle);
                    lastSubtitle = null;
                    if (overrideSubtitleColor != null) {
                        subtitleTextView.setTextColor(overrideSubtitleColor);
                    } else if (lastSubtitleColorKey >= 0) {
                        subtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                        subtitleTextView.setTag(lastSubtitleColorKey);
                    }
                } else if (animatedSubtitleTextView != null) {
                    animatedSubtitleTextView.setText(lastSubtitle, !LocaleController.isRTL);
                    lastSubtitle = null;
                    if (overrideSubtitleColor != null) {
                        animatedSubtitleTextView.setTextColor(overrideSubtitleColor);
                    } else if (lastSubtitleColorKey >= 0) {
                        animatedSubtitleTextView.setTextColor(getThemedColor(lastSubtitleColorKey));
                        animatedSubtitleTextView.setTag(lastSubtitleColorKey);
                    }
                }
            }
        } else {
            if (subtitleTextView != null) {
                if (lastSubtitle == null) {
                    lastSubtitle = subtitleTextView.getText();
                }
                subtitleTextView.setText(title);
                if (overrideSubtitleColor != null) {
                    subtitleTextView.setTextColor(overrideSubtitleColor);
                } else {
                    subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                    subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
                }
            } else if (animatedSubtitleTextView != null) {
                if (lastSubtitle == null) {
                    lastSubtitle = animatedSubtitleTextView.getText();
                }
                animatedSubtitleTextView.setText(title, !LocaleController.isRTL);
                if (overrideSubtitleColor != null) {
                    animatedSubtitleTextView.setTextColor(overrideSubtitleColor);
                } else {
                    animatedSubtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                    animatedSubtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
                }
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder sb = new StringBuilder();
        sb.append(titleTextView.getText());
        if (rightDrawableContentDescription != null) {
            sb.append(", ");
            sb.append(rightDrawableContentDescription);
        }
        if (rightDrawable2ContentDescription != null) {
            sb.append(", ");
            sb.append(rightDrawable2ContentDescription);
        }
        sb.append("\n");
        if (subtitleTextView != null) {
            sb.append(subtitleTextView.getText());
        } else if (animatedSubtitleTextView != null) {
            sb.append(animatedSubtitleTextView.getText());
        }
        info.setContentDescription(sb);
        if (info.isClickable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString(R.string.OpenProfile)));
        }
    }

    public SharedMediaLayout.SharedMediaPreloader getSharedMediaPreloader() {
        return sharedMediaPreloader;
    }

    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void updateColors() {
        if (currentTypingDrawable != null) {
            currentTypingDrawable.setColor(getThemedColor(Theme.key_chat_status));
        }
    }
}
