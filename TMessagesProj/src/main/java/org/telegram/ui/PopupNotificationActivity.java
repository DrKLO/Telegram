/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayingGameDrawable;
import org.telegram.ui.Components.PopupAudioView;
import org.telegram.ui.Components.RecordStatusDrawable;
import org.telegram.ui.Components.RoundStatusDrawable;
import org.telegram.ui.Components.SendingFileDrawable;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.StatusDrawable;
import org.telegram.ui.Components.TypingDotsDrawable;

import java.io.File;
import java.util.ArrayList;

public class PopupNotificationActivity extends Activity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBar actionBar;
    private ChatActivityEnterView chatActivityEnterView;
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView onlineTextView;
    private FrameLayout avatarContainer;
    private TextView countText;
    private ViewGroup messageContainer;
    private ViewGroup centerView;
    private ViewGroup leftView;
    private ViewGroup rightView;

    private ViewGroup centerButtonsView;
    private ViewGroup leftButtonsView;
    private ViewGroup rightButtonsView;

    private RelativeLayout popupContainer;
    private ArrayList<ViewGroup> textViews = new ArrayList<>();
    private ArrayList<ViewGroup> imageViews = new ArrayList<>();
    private ArrayList<ViewGroup> audioViews = new ArrayList<>();
    private VelocityTracker velocityTracker = null;
    private StatusDrawable[] statusDrawables = new StatusDrawable[5];

    private final static int id_chat_compose_panel = 1000;

    private int classGuid;
    private int lastResumedAccount = -1;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private boolean finished = false;
    private CharSequence lastPrintString;
    private MessageObject currentMessageObject = null;
    private MessageObject[] setMessageObjects = new MessageObject[3];
    private int currentMessageNum = 0;
    private PowerManager.WakeLock wakeLock = null;
    private boolean animationInProgress = false;
    private long animationStartTime = 0;
    private float moveStartX = -1;
    private boolean startedMoving = false;
    private Runnable onAnimationEndRunnable = null;
    private boolean isReply;
    private ArrayList<MessageObject> popupMessages = new ArrayList<>();

    private class FrameLayoutTouch extends FrameLayout {
        public FrameLayoutTouch(Context context) {
            super(context);
        }

        public FrameLayoutTouch(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public FrameLayoutTouch(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return checkTransitionAnimation() || ((PopupNotificationActivity) getContext()).onTouchEventMy(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            return checkTransitionAnimation() || ((PopupNotificationActivity) getContext()).onTouchEventMy(ev);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            ((PopupNotificationActivity) getContext()).onTouchEventMy(null);
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);

        AndroidUtilities.fillStatusBarHeight(this, false);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.contactsDidLoad);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        classGuid = ConnectionsManager.generateClassGuid();

        statusDrawables[0] = new TypingDotsDrawable(false);
        statusDrawables[1] = new RecordStatusDrawable(false);
        statusDrawables[2] = new SendingFileDrawable(false);
        statusDrawables[3] = new PlayingGameDrawable(false, null);
        statusDrawables[4] = new RoundStatusDrawable(false);

        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthMode = MeasureSpec.getMode(widthMeasureSpec);
                int heightMode = MeasureSpec.getMode(heightMeasureSpec);
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                int keyboardSize = measureKeyboardHeight();

                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    heightSize -= chatActivityEnterView.getEmojiPadding();
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    if (chatActivityEnterView.isPopupView(child)) {
                        child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                    } else if (chatActivityEnterView.isRecordCircle(child)) {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    } else {
                        child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize + AndroidUtilities.dp(2)), MeasureSpec.EXACTLY));
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = measureKeyboardHeight() <= AndroidUtilities.dp(20) ? chatActivityEnterView.getEmojiPadding() : 0;

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    int width = child.getMeasuredWidth();
                    int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }
                    if (chatActivityEnterView.isPopupView(child)) {
                        childTop = paddingBottom != 0 ? getMeasuredHeight() - paddingBottom : getMeasuredHeight();
                    } else if (chatActivityEnterView.isRecordCircle(child)) {
                        childTop = popupContainer.getTop() + popupContainer.getMeasuredHeight() - child.getMeasuredHeight() - lp.bottomMargin;
                        childLeft = popupContainer.getLeft() + popupContainer.getMeasuredWidth() - child.getMeasuredWidth() - lp.rightMargin;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }
        };
        setContentView(contentView);
        contentView.setBackgroundColor(0x99000000);

        RelativeLayout relativeLayout = new RelativeLayout(this);
        contentView.addView(relativeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        popupContainer = new RelativeLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int w = chatActivityEnterView.getMeasuredWidth();
                int h = chatActivityEnterView.getMeasuredHeight();
                for (int a = 0, count; a < getChildCount(); a++) {
                    View v = getChildAt(a);
                    if (v.getTag() instanceof String) {
                        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h - AndroidUtilities.dp(3), MeasureSpec.EXACTLY));
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                for (int a = 0, count; a < getChildCount(); a++) {
                    View v = getChildAt(a);
                    if (v.getTag() instanceof String) {
                        v.layout(v.getLeft(), chatActivityEnterView.getTop() + AndroidUtilities.dp(3), v.getRight(), chatActivityEnterView.getBottom());
                    }
                }
            }
        };
        popupContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        relativeLayout.addView(popupContainer, LayoutHelper.createRelative(LayoutHelper.MATCH_PARENT, 240, 12, 0, 12, 0, RelativeLayout.CENTER_IN_PARENT));

        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        chatActivityEnterView = new ChatActivityEnterView(this, contentView, null, false);
        chatActivityEnterView.setId(id_chat_compose_panel);
        popupContainer.addView(chatActivityEnterView, LayoutHelper.createRelative(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, RelativeLayout.ALIGN_PARENT_BOTTOM));
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message, boolean notify, int scheduleDate) {
                if (currentMessageObject == null) {
                    return;
                }
                if (currentMessageNum >= 0 && currentMessageNum < popupMessages.size()) {
                    popupMessages.remove(currentMessageNum);
                }
                MessagesController.getInstance(currentMessageObject.currentAccount).markDialogAsRead(currentMessageObject.getDialogId(), currentMessageObject.getId(), Math.max(0, currentMessageObject.getId()), currentMessageObject.messageOwner.date, true, 0, 0, true, 0);
                currentMessageObject = null;
                getNewMessage();
            }

            @Override
            public void onTextChanged(CharSequence text, boolean big, boolean fromDraft) {

            }

            @Override
            public void onTextSelectionChanged(int start, int end) {

            }

            @Override
            public void onTextSpansChanged(CharSequence text) {

            }

            @Override
            public void onStickersExpandedChange() {

            }

            @Override
            public void onSwitchRecordMode(boolean video) {

            }

            @Override
            public void onPreAudioVideoRecord() {

            }

            @Override
            public void onMessageEditEnd(boolean loading) {

            }

            @Override
            public void needSendTyping() {
                if (currentMessageObject != null) {
                    MessagesController.getInstance(currentMessageObject.currentAccount).sendTyping(currentMessageObject.getDialogId(), 0, 0, classGuid);
                }
            }

            @Override
            public void onAttachButtonHidden() {

            }

            @Override
            public void onAttachButtonShow() {

            }

            @Override
            public void onWindowSizeChanged(int size) {

            }

            @Override
            public void onStickersTab(boolean opened) {

            }

            @Override
            public void didPressAttachButton() {

            }

            @Override
            public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int ttl, long effectId) {

            }

            @Override
            public void toggleVideoRecordingPause() {

            }

            @Override
            public void needStartRecordAudio(int state) {

            }

            @Override
            public void needChangeVideoPreviewState(int state, float seekProgress) {

            }

            @Override
            public void needShowMediaBanHint() {

            }

            @Override
            public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {

            }

            @Override
            public void onSendLongClick() {

            }

            @Override
            public void onAudioVideoInterfaceUpdated() {

            }
        });

        messageContainer = new FrameLayoutTouch(this);
        popupContainer.addView(messageContainer, 0);

        actionBar = new ActionBar(this);
        actionBar.setOccupyStatusBar(false);
        actionBar.setBackButtonImage(R.drawable.ic_close_white);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        popupContainer.addView(actionBar);
        ViewGroup.LayoutParams layoutParams = actionBar.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem view = menu.addItemWithWidth(2, 0, AndroidUtilities.dp(56));
        countText = new TextView(this);
        countText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        countText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        countText.setGravity(Gravity.CENTER);
        view.addView(countText, LayoutHelper.createFrame(56, LayoutHelper.MATCH_PARENT));

        avatarContainer = new FrameLayout(this);
        avatarContainer.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        actionBar.addView(avatarContainer);
        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        layoutParams2.height = LayoutHelper.MATCH_PARENT;
        layoutParams2.width = LayoutHelper.WRAP_CONTENT;
        layoutParams2.rightMargin = AndroidUtilities.dp(48);
        layoutParams2.leftMargin = AndroidUtilities.dp(60);
        layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
        avatarContainer.setLayoutParams(layoutParams2);

        avatarImageView = new BackupImageView(this);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
        avatarContainer.addView(avatarImageView);
        layoutParams2 = (FrameLayout.LayoutParams) avatarImageView.getLayoutParams();
        layoutParams2.width = AndroidUtilities.dp(42);
        layoutParams2.height = AndroidUtilities.dp(42);
        layoutParams2.topMargin = AndroidUtilities.dp(3);
        avatarImageView.setLayoutParams(layoutParams2);

        nameTextView = new TextView(this);
        nameTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setTypeface(AndroidUtilities.bold());
        avatarContainer.addView(nameTextView);
        layoutParams2 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        layoutParams2.width = LayoutHelper.WRAP_CONTENT;
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;
        layoutParams2.leftMargin = AndroidUtilities.dp(54);
        layoutParams2.bottomMargin = AndroidUtilities.dp(22);
        layoutParams2.gravity = Gravity.BOTTOM;
        nameTextView.setLayoutParams(layoutParams2);

        onlineTextView = new TextView(this);
        onlineTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity(Gravity.LEFT);
        avatarContainer.addView(onlineTextView);
        layoutParams2 = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
        layoutParams2.width = LayoutHelper.WRAP_CONTENT;
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;
        layoutParams2.leftMargin = AndroidUtilities.dp(54);
        layoutParams2.bottomMargin = AndroidUtilities.dp(4);
        layoutParams2.gravity = Gravity.BOTTOM;
        onlineTextView.setLayoutParams(layoutParams2);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    onFinish();
                    finish();
                } else if (id == 1) {
                    openCurrentMessage();
                } else if (id == 2) {
                    switchToNextMessage();
                }
            }
        });

        PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "screen");
        wakeLock.setReferenceCounted(false);

        handleIntent(getIntent());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidUtilities.checkDisplaySize(this, newConfig);
        AndroidUtilities.setPreferredMaxRefreshRate(getWindow());
        fixLayout();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString(R.string.AppName));
            builder.setMessage(LocaleController.getString(R.string.PermissionNoAudioWithHint));
            builder.setNegativeButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialog, which) -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.show();
        }
    }

    private void switchToNextMessage() {
        if (popupMessages.size() > 1) {
            if (currentMessageNum < popupMessages.size() - 1) {
                currentMessageNum++;
            } else {
                currentMessageNum = 0;
            }
            currentMessageObject = popupMessages.get(currentMessageNum);
            updateInterfaceForCurrentMessage(2);
            countText.setText(String.format("%d/%d", currentMessageNum + 1, popupMessages.size()));
        }
    }

    private void switchToPreviousMessage() {
        if (popupMessages.size() > 1) {
            if (currentMessageNum > 0) {
                currentMessageNum--;
            } else {
                currentMessageNum = popupMessages.size() - 1;
            }
            currentMessageObject = popupMessages.get(currentMessageNum);
            updateInterfaceForCurrentMessage(1);
            countText.setText(String.format("%d/%d", currentMessageNum + 1, popupMessages.size()));
        }
    }

    public boolean checkTransitionAnimation() {
        if (animationInProgress && animationStartTime < System.currentTimeMillis() - 400) {
            animationInProgress = false;
            if (onAnimationEndRunnable != null) {
                onAnimationEndRunnable.run();
                onAnimationEndRunnable = null;
            }
        }
        return animationInProgress;
    }

    public boolean onTouchEventMy(MotionEvent motionEvent) {
        if (checkTransitionAnimation()) {
            return false;
        }
        if (motionEvent != null && motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            moveStartX = motionEvent.getX();
        } else if (motionEvent != null && motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            float x = motionEvent.getX();
            int diff = (int) (x - moveStartX);
            if (moveStartX != -1 && !startedMoving) {
                if (Math.abs(diff) > AndroidUtilities.dp(10)) {
                    startedMoving = true;
                    moveStartX = x;
                    AndroidUtilities.lockOrientation(this);
                    diff = 0;
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    } else {
                        velocityTracker.clear();
                    }
                }
            }
            if (startedMoving) {
                if (leftView == null && diff > 0) {
                    diff = 0;
                }
                if (rightView == null && diff < 0) {
                    diff = 0;
                }
                if (velocityTracker != null) {
                    velocityTracker.addMovement(motionEvent);
                }
                applyViewsLayoutParams(diff);
            }
        } else if (motionEvent == null || motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            if (motionEvent != null && startedMoving) {
                int diff = (int) (motionEvent.getX() - moveStartX);
                int width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
                float moveDiff = 0;
                int forceMove = 0;
                View otherView = null;
                View otherButtonsView = null;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    if (velocityTracker.getXVelocity() >= 3500) {
                        forceMove = 1;
                    } else if (velocityTracker.getXVelocity() <= -3500) {
                        forceMove = 2;
                    }
                }
                if ((forceMove == 1 || diff > width / 3) && leftView != null) {
                    moveDiff = width - centerView.getTranslationX();
                    otherView = leftView;
                    otherButtonsView = leftButtonsView;
                    onAnimationEndRunnable = () -> {
                        animationInProgress = false;
                        switchToPreviousMessage();
                        AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                    };
                } else if ((forceMove == 2 || diff < -width / 3) && rightView != null) {
                    moveDiff = -width - centerView.getTranslationX();
                    otherView = rightView;
                    otherButtonsView = rightButtonsView;
                    onAnimationEndRunnable = () -> {
                        animationInProgress = false;
                        switchToNextMessage();
                        AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                    };
                } else if (centerView.getTranslationX() != 0) {
                    moveDiff = -centerView.getTranslationX();
                    otherView = diff > 0 ? leftView : rightView;
                    otherButtonsView = diff > 0 ? leftButtonsView : rightButtonsView;
                    onAnimationEndRunnable = () -> {
                        animationInProgress = false;
                        applyViewsLayoutParams(0);
                        AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                    };
                }
                if (moveDiff != 0) {
                    int time = (int) (Math.abs(moveDiff / (float) width) * 200);
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(centerView, "translationX", centerView.getTranslationX() + moveDiff));
                    if (centerButtonsView != null) {
                        animators.add(ObjectAnimator.ofFloat(centerButtonsView, "translationX", centerButtonsView.getTranslationX() + moveDiff));
                    }
                    if (otherView != null) {
                        animators.add(ObjectAnimator.ofFloat(otherView, "translationX", otherView.getTranslationX() + moveDiff));
                    }
                    if (otherButtonsView != null) {
                        animators.add(ObjectAnimator.ofFloat(otherButtonsView, "translationX", otherButtonsView.getTranslationX() + moveDiff));
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(animators);
                    animatorSet.setDuration(time);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (onAnimationEndRunnable != null) {
                                onAnimationEndRunnable.run();
                                onAnimationEndRunnable = null;
                            }
                        }
                    });
                    animatorSet.start();
                    animationInProgress = true;
                    animationStartTime = System.currentTimeMillis();
                }
            } else {
                applyViewsLayoutParams(0);
            }
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            startedMoving = false;
            moveStartX = -1;
        }
        return startedMoving;
    }

    private void applyViewsLayoutParams(int xOffset) {
        FrameLayout.LayoutParams layoutParams;
        RelativeLayout.LayoutParams rLayoutParams;
        int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
        if (leftView != null) {
            layoutParams = (FrameLayout.LayoutParams) leftView.getLayoutParams();
            if (layoutParams.width != widht) {
                layoutParams.width = widht;
                leftView.setLayoutParams(layoutParams);
            }
            leftView.setTranslationX(-widht + xOffset);
        }
        if (leftButtonsView != null) {
            leftButtonsView.setTranslationX(-widht + xOffset);
        }
        if (centerView != null) {
            layoutParams = (FrameLayout.LayoutParams) centerView.getLayoutParams();
            if (layoutParams.width != widht) {
                layoutParams.width = widht;
                centerView.setLayoutParams(layoutParams);
            }
            centerView.setTranslationX(xOffset);
        }
        if (centerButtonsView != null) {
            centerButtonsView.setTranslationX(xOffset);
        }
        if (rightView != null) {
            layoutParams = (FrameLayout.LayoutParams) rightView.getLayoutParams();
            if (layoutParams.width != widht) {
                layoutParams.width = widht;
                rightView.setLayoutParams(layoutParams);
            }
            rightView.setTranslationX(widht + xOffset);
        }
        if (rightButtonsView != null) {
            rightButtonsView.setTranslationX(widht + xOffset);
        }
        messageContainer.invalidate();
    }

    private LinearLayout getButtonsViewForMessage(int num, boolean applyOffset) {
        if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
            return null;
        }
        if (num == -1) {
            num = popupMessages.size() - 1;
        } else if (num == popupMessages.size()) {
            num = 0;
        }
        LinearLayout view = null;
        final MessageObject messageObject = popupMessages.get(num);
        int buttonsCount = 0;

        TLRPC.ReplyMarkup markup = messageObject.messageOwner.reply_markup;

        if (messageObject.getDialogId() == 777000 && markup != null) {
            ArrayList<TLRPC.TL_keyboardButtonRow> rows = markup.rows;
            for (int a = 0, size = rows.size(); a < size; a++) {
                TLRPC.TL_keyboardButtonRow row = rows.get(a);
                for (int b = 0, size2 = row.buttons.size(); b < size2; b++) {
                    TLRPC.KeyboardButton button = row.buttons.get(b);
                    if (button instanceof TLRPC.TL_keyboardButtonCallback) {
                        buttonsCount++;
                    }
                }
            }
        }

        final int account = messageObject.currentAccount;
        if (buttonsCount > 0) {
            ArrayList<TLRPC.TL_keyboardButtonRow> rows = markup.rows;
            for (int a = 0, size = rows.size(); a < size; a++) {
                TLRPC.TL_keyboardButtonRow row = rows.get(a);
                for (int b = 0, size2 = row.buttons.size(); b < size2; b++) {
                    TLRPC.KeyboardButton button = row.buttons.get(b);
                    if (button instanceof TLRPC.TL_keyboardButtonCallback) {
                        if (view == null) {
                            view = new LinearLayout(this);
                            view.setOrientation(LinearLayout.HORIZONTAL);
                            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                            view.setWeightSum(100);
                            view.setTag("b");
                            view.setOnTouchListener((v, event) -> true);
                        }

                        TextView textView = new TextView(this);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        textView.setTypeface(AndroidUtilities.bold());
                        textView.setText(button.text.toUpperCase());
                        textView.setTag(button);
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        view.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 100.0f / buttonsCount));
                        textView.setOnClickListener(v -> {
                            TLRPC.KeyboardButton button1 = (TLRPC.KeyboardButton) v.getTag();
                            if (button1 != null) {
                                SendMessagesHelper.getInstance(account).sendNotificationCallback(messageObject.getDialogId(), messageObject.getId(), button1.data);
                            }
                        });
                    }
                }
            }
        }

        if (view != null) {
            int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

            if (applyOffset) {
                if (num == currentMessageNum) {
                    view.setTranslationX(0);
                } else if (num == currentMessageNum - 1) {
                    view.setTranslationX(-widht);
                } else if (num == currentMessageNum + 1) {
                    view.setTranslationX(widht);
                }
            }
            popupContainer.addView(view, layoutParams);
        }

        return view;
    }

    private ViewGroup getViewForMessage(int num, boolean applyOffset) {
        if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
            return null;
        }
        if (num == -1) {
            num = popupMessages.size() - 1;
        } else if (num == popupMessages.size()) {
            num = 0;
        }
        ViewGroup view;
        MessageObject messageObject = popupMessages.get(num);
        if ((messageObject.type == MessageObject.TYPE_PHOTO || messageObject.type == MessageObject.TYPE_GEO) && !messageObject.isSecretMedia()) {
            if (imageViews.size() > 0) {
                view = imageViews.get(0);
                imageViews.remove(0);
            } else {
                view = new FrameLayout(this);

                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                frameLayout.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                view.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                BackupImageView backupImageView = new BackupImageView(this);
                backupImageView.setTag(311);
                frameLayout.addView(backupImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                TextView textView = new TextView(this);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setGravity(Gravity.CENTER);
                textView.setTag(312);
                frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                view.setTag(2);

                view.setOnClickListener(v -> openCurrentMessage());
            }

            TextView messageText = view.findViewWithTag(312);
            BackupImageView imageView = view.findViewWithTag(311);
            imageView.setAspectFit(true);

            if (messageObject.type == MessageObject.TYPE_PHOTO) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
                boolean photoSet = false;
                if (currentPhotoObject != null) {
                    boolean photoExist = true;
                    if (messageObject.type == MessageObject.TYPE_PHOTO) {
                        File cacheFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner);
                        if (!cacheFile.exists()) {
                            photoExist = false;
                        }
                    }
                    if (!messageObject.needDrawBluredPreview()) {
                        if (photoExist || DownloadController.getInstance(messageObject.currentAccount).canDownloadMedia(messageObject)) {
                            imageView.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "100_100", ImageLocation.getForObject(thumb, messageObject.photoThumbsObject), "100_100_b", currentPhotoObject.size, messageObject);
                            photoSet = true;
                        } else {
                            if (thumb != null) {
                                imageView.setImage(ImageLocation.getForObject(thumb, messageObject.photoThumbsObject), "100_100_b", null, null, messageObject);
                                photoSet = true;
                            }
                        }
                    }
                }
                if (!photoSet) {
                    imageView.setVisibility(View.GONE);
                    messageText.setVisibility(View.VISIBLE);
                    messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SharedConfig.fontSize);
                    messageText.setText(messageObject.messageText);
                } else {
                    imageView.setVisibility(View.VISIBLE);
                    messageText.setVisibility(View.GONE);
                }
            } else if (messageObject.type == MessageObject.TYPE_GEO) {
                messageText.setVisibility(View.GONE);
                messageText.setText(messageObject.messageText);
                imageView.setVisibility(View.VISIBLE);
                TLRPC.GeoPoint geoPoint = messageObject.messageOwner.media.geo;
                double lat = geoPoint.lat;
                double lon = geoPoint._long;

                if (MessagesController.getInstance(messageObject.currentAccount).mapProvider == 2) {
                    imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithGeoPoint(geoPoint, 100, 100, 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)))), null, null, null, messageObject);
                } else {
                    String currentUrl = AndroidUtilities.formapMapUrl(messageObject.currentAccount, lat, lon, 100, 100, true, 15, -1);
                    imageView.setImage(currentUrl, null, null);
                }
            }
        } else if (messageObject.type == MessageObject.TYPE_VOICE) {
            PopupAudioView cell;
            if (audioViews.size() > 0) {
                view = audioViews.get(0);
                audioViews.remove(0);
                cell = view.findViewWithTag(300);
            } else {
                view = new FrameLayout(this);

                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                frameLayout.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                view.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                FrameLayout frameLayout1 = new FrameLayout(this);
                frameLayout.addView(frameLayout1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 0));
                cell = new PopupAudioView(this);
                cell.setTag(300);
                frameLayout1.addView(cell);

                view.setTag(3);

                view.setOnClickListener(v -> openCurrentMessage());
            }

            cell.setMessageObject(messageObject);
            if (DownloadController.getInstance(messageObject.currentAccount).canDownloadMedia(messageObject)) {
                cell.downloadAudioIfNeed();
            }
        } else {
            if (textViews.size() > 0) {
                view = textViews.get(0);
                textViews.remove(0);
            } else {
                view = new FrameLayout(this);

                ScrollView scrollView = new ScrollView(this);
                scrollView.setFillViewport(true);
                view.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                LinearLayout linearLayout = new LinearLayout(this);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
                linearLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                linearLayout.setOnClickListener(v -> openCurrentMessage());

                TextView textView = new TextView(this);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTag(301);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setGravity(Gravity.CENTER);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                view.setTag(1);
            }
            TextView messageText = view.findViewWithTag(301);
            messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SharedConfig.fontSize);
            messageText.setText(messageObject.messageText);
        }
        if (view.getParent() == null) {
            messageContainer.addView(view);
        }
        view.setVisibility(View.VISIBLE);

        if (applyOffset) {
            int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.width = widht;
            if (num == currentMessageNum) {
                view.setTranslationX(0);
            } else if (num == currentMessageNum - 1) {
                view.setTranslationX(-widht);
            } else if (num == currentMessageNum + 1) {
                view.setTranslationX(widht);
            }
            view.setLayoutParams(layoutParams);
            view.invalidate();
        }

        return view;
    }

    private void reuseButtonsView(ViewGroup view) {
        if (view == null) {
            return;
        }
        popupContainer.removeView(view);
    }

    private void reuseView(ViewGroup view) {
        if (view == null) {
            return;
        }
        int tag = (Integer) view.getTag();
        view.setVisibility(View.GONE);
        if (tag == 1) {
            textViews.add(view);
        } else if (tag == 2) {
            imageViews.add(view);
        } else if (tag == 3) {
            audioViews.add(view);
        }
    }

    private void prepareLayouts(int move) {
        int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
        if (move == 0) {
            reuseView(centerView);
            reuseView(leftView);
            reuseView(rightView);
            reuseButtonsView(centerButtonsView);
            reuseButtonsView(leftButtonsView);
            reuseButtonsView(rightButtonsView);
            for (int a = currentMessageNum - 1; a < currentMessageNum + 2; a++) {
                if (a == currentMessageNum - 1) {
                    leftView = getViewForMessage(a, true);
                    leftButtonsView = getButtonsViewForMessage(a, true);
                } else if (a == currentMessageNum) {
                    centerView = getViewForMessage(a, true);
                    centerButtonsView = getButtonsViewForMessage(a, true);
                } else if (a == currentMessageNum + 1) {
                    rightView = getViewForMessage(a, true);
                    rightButtonsView = getButtonsViewForMessage(a, true);
                }
            }
        } else if (move == 1) {
            reuseView(rightView);
            reuseButtonsView(rightButtonsView);
            rightView = centerView;
            centerView = leftView;
            leftView = getViewForMessage(currentMessageNum - 1, true);

            rightButtonsView = centerButtonsView;
            centerButtonsView = leftButtonsView;
            leftButtonsView = getButtonsViewForMessage(currentMessageNum - 1, true);
        } else if (move == 2) {
            reuseView(leftView);
            reuseButtonsView(leftButtonsView);
            leftView = centerView;
            centerView = rightView;
            rightView = getViewForMessage(currentMessageNum + 1, true);

            leftButtonsView = centerButtonsView;
            centerButtonsView = rightButtonsView;
            rightButtonsView = getButtonsViewForMessage(currentMessageNum + 1, true);
        } else if (move == 3) {
            if (rightView != null) {
                float offset = rightView.getTranslationX();
                reuseView(rightView);
                if ((rightView = getViewForMessage(currentMessageNum + 1, false)) != null) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) rightView.getLayoutParams();
                    layoutParams.width = widht;
                    rightView.setLayoutParams(layoutParams);
                    rightView.setTranslationX(offset);
                    rightView.invalidate();
                }
            }
            if (rightButtonsView != null) {
                float offset = rightButtonsView.getTranslationX();
                reuseButtonsView(rightButtonsView);
                if ((rightButtonsView = getButtonsViewForMessage(currentMessageNum + 1, false)) != null) {
                    rightButtonsView.setTranslationX(offset);
                }
            }
        } else if (move == 4) {
            if (leftView != null) {
                float offset = leftView.getTranslationX();
                reuseView(leftView);
                if ((leftView = getViewForMessage(0, false)) != null) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) leftView.getLayoutParams();
                    layoutParams.width = widht;
                    leftView.setLayoutParams(layoutParams);
                    leftView.setTranslationX(offset);
                    leftView.invalidate();
                }
            }
            if (leftButtonsView != null) {
                float offset = leftButtonsView.getTranslationX();
                reuseButtonsView(leftButtonsView);
                if ((leftButtonsView = getButtonsViewForMessage(0, false)) != null) {
                    leftButtonsView.setTranslationX(offset);
                }
            }
        }
        for (int a = 0; a < 3; a++) {
            int num = currentMessageNum - 1 + a;
            MessageObject messageObject;
            if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
                messageObject = null;
            } else {
                if (num == -1) {
                    num = popupMessages.size() - 1;
                } else if (num == popupMessages.size()) {
                    num = 0;
                }
                messageObject = popupMessages.get(num);
            }
            setMessageObjects[a] = messageObject;
        }
    }

    private void fixLayout() {
        if (avatarContainer != null) {
            avatarContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (avatarContainer != null) {
                        avatarContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    int padding = (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(48)) / 2;
                    avatarContainer.setPadding(avatarContainer.getPaddingLeft(), padding, avatarContainer.getPaddingRight(), padding);
                    return true;
                }
            });
        }
        if (messageContainer != null) {
            messageContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    messageContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (!checkTransitionAnimation() && !startedMoving) {
                        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) messageContainer.getLayoutParams();
                        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();
                        layoutParams.bottomMargin = AndroidUtilities.dp(48);
                        layoutParams.width = ViewGroup.MarginLayoutParams.MATCH_PARENT;
                        layoutParams.height = ViewGroup.MarginLayoutParams.MATCH_PARENT;
                        messageContainer.setLayoutParams(layoutParams);
                        applyViewsLayoutParams(0);
                    }
                    return true;
                }
            });
        }
    }

    private void handleIntent(Intent intent) {
        isReply = intent != null && intent.getBooleanExtra("force", false);
        popupMessages.clear();
        if (isReply) {
            int account = intent != null ? intent.getIntExtra("currentAccount", UserConfig.selectedAccount) : UserConfig.selectedAccount;
            if (!UserConfig.isValidAccount(account)) {
                return;
            }
            popupMessages.addAll(NotificationsController.getInstance(account).popupReplyMessages);
        } else {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    popupMessages.addAll(NotificationsController.getInstance(a).popupMessages);
                }
            }
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode() || !ApplicationLoader.isScreenOn) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        if (currentMessageObject == null) {
            currentMessageNum = 0;
        }
        getNewMessage();
    }

    private void getNewMessage() {
        if (popupMessages.isEmpty()) {
            onFinish();
            finish();
            return;
        }

        boolean found = false;
        if ((currentMessageNum != 0 || chatActivityEnterView.hasText() || startedMoving) && currentMessageObject != null) {
            for (int a = 0, size = popupMessages.size(); a < size; a++) {
                MessageObject messageObject = popupMessages.get(a);
                if (messageObject.currentAccount == currentMessageObject.currentAccount && messageObject.getDialogId() == currentMessageObject.getDialogId() && messageObject.getId() == currentMessageObject.getId()) {
                    currentMessageNum = a;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            currentMessageNum = 0;
            currentMessageObject = popupMessages.get(0);
            updateInterfaceForCurrentMessage(0);
        } else if (startedMoving) {
            if (currentMessageNum == popupMessages.size() - 1) {
                prepareLayouts(3);
            } else if (currentMessageNum == 1) {
                prepareLayouts(4);
            }
        }
        countText.setText(String.format("%d/%d", currentMessageNum + 1, popupMessages.size()));
    }

    private void openCurrentMessage() {
        if (currentMessageObject == null) {
            return;
        }
        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        long dialogId = currentMessageObject.getDialogId();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            intent.putExtra("encId", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            intent.putExtra("userId", dialogId);
        } else if (DialogObject.isChatDialog(dialogId)) {
            intent.putExtra("chatId", -dialogId);
        }
        intent.putExtra("currentAccount", currentMessageObject.currentAccount);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        onFinish();
        finish();
    }

    private void updateInterfaceForCurrentMessage(int move) {
        if (actionBar == null) {
            return;
        }
        if (lastResumedAccount != currentMessageObject.currentAccount) {
            if (lastResumedAccount >= 0) {
                ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(true, false);
            }
            lastResumedAccount = currentMessageObject.currentAccount;
            ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(false, false);
        }
        currentChat = null;
        currentUser = null;
        long dialogId = currentMessageObject.getDialogId();
        chatActivityEnterView.setDialogId(dialogId, currentMessageObject.currentAccount);
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentMessageObject.currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            currentUser = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(encryptedChat.user_id);
        } else if (DialogObject.isUserDialog(dialogId)) {
            currentUser = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(dialogId);
        } else if (DialogObject.isChatDialog(dialogId)) {
            currentChat = MessagesController.getInstance(currentMessageObject.currentAccount).getChat(-dialogId);
            if (currentMessageObject.isFromUser()) {
                currentUser = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(currentMessageObject.messageOwner.from_id.user_id);
            }
        }

        if (currentChat != null) {
            nameTextView.setText(currentChat.title);
            if (currentUser != null) {
                onlineTextView.setText(UserObject.getUserName(currentUser));
            } else {
                onlineTextView.setText(null);
            }
            nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            nameTextView.setCompoundDrawablePadding(0);
        } else if (currentUser != null) {
            nameTextView.setText(UserObject.getUserName(currentUser));
            if (DialogObject.isEncryptedDialog(dialogId)) {
                nameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_white, 0, 0, 0);
                nameTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
            } else {
                nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                nameTextView.setCompoundDrawablePadding(0);
            }
        }

        prepareLayouts(move);
        updateSubtitle();
        checkAndUpdateAvatar();
        applyViewsLayoutParams(0);
    }

    private void updateSubtitle() {
        if (actionBar == null || currentMessageObject == null) {
            return;
        }
        if (currentChat != null || currentUser == null) {
            return;
        }
        if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance(currentMessageObject.currentAccount).contactsDict.get(currentUser.id) == null && (ContactsController.getInstance(currentMessageObject.currentAccount).contactsDict.size() != 0 || !ContactsController.getInstance(currentMessageObject.currentAccount).isLoadingContacts())) {
            if (currentUser.phone != null && currentUser.phone.length() != 0) {
                nameTextView.setText(PhoneFormat.getInstance().format("+" + currentUser.phone));
            } else {
                nameTextView.setText(UserObject.getUserName(currentUser));
            }
        } else {
            nameTextView.setText(UserObject.getUserName(currentUser));
        }
        if (currentUser != null && currentUser.id == UserObject.VERIFY) {
            onlineTextView.setText(LocaleController.getString(R.string.VerifyCodesNotifications));
        } else if (currentUser != null && currentUser.id == 777000) {
            onlineTextView.setText(LocaleController.getString(R.string.ServiceNotifications));
        } else {
            CharSequence printString = MessagesController.getInstance(currentMessageObject.currentAccount).getPrintingString(currentMessageObject.getDialogId(), 0, false);
            if (printString == null || printString.length() == 0) {
                lastPrintString = null;
                setTypingAnimation(false);
                TLRPC.User user = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(currentUser.id);
                if (user != null) {
                    currentUser = user;
                }
                onlineTextView.setText(LocaleController.formatUserStatus(currentMessageObject.currentAccount, currentUser));
            } else {
                lastPrintString = printString;
                onlineTextView.setText(printString);
                setTypingAnimation(true);
            }
        }
    }

    private void checkAndUpdateAvatar() {
        if (currentMessageObject == null) {
            return;
        }
        if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance(currentMessageObject.currentAccount).getChat(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (avatarImageView != null) {
                AvatarDrawable avatarDrawable = new AvatarDrawable(currentChat);
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
        } else if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (avatarImageView != null) {
                AvatarDrawable avatarDrawable = new AvatarDrawable(currentUser);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        }
    }

    private void setTypingAnimation(boolean start) {
        if (actionBar == null) {
            return;
        }
        if (start) {
            try {
                Integer type = MessagesController.getInstance(currentMessageObject.currentAccount).getPrintingStringType(currentMessageObject.getDialogId(), 0);
                onlineTextView.setCompoundDrawablesWithIntrinsicBounds(statusDrawables[type], null, null, null);
                onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
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
            onlineTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            onlineTextView.setCompoundDrawablePadding(0);
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].stop();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (chatActivityEnterView.isPopupShowing()) {
            chatActivityEnterView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MediaController.getInstance().setFeedbackView(chatActivityEnterView, true);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setFieldFocused(true);
        }
        fixLayout();
        checkAndUpdateAvatar();
        wakeLock.acquire(7000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.hidePopup(false);
            chatActivityEnterView.setFieldFocused(false);
        }
        if (lastResumedAccount >= 0) {
            ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(true, false);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            if (account == lastResumedAccount) {
                onFinish();
                finish();
            }
        } else if (id == NotificationCenter.pushMessagesUpdated) {
            if (!isReply) {
                popupMessages.clear();
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        popupMessages.addAll(NotificationsController.getInstance(a).popupMessages);
                    }
                }
                getNewMessage();
                if (!popupMessages.isEmpty()) {
                    for (int a = 0; a < 3; a++) {
                        int num = currentMessageNum - 1 + a;
                        MessageObject messageObject;
                        if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
                            messageObject = null;
                        } else {
                            if (num == -1) {
                                num = popupMessages.size() - 1;
                            } else if (num == popupMessages.size()) {
                                num = 0;
                            }
                            messageObject = popupMessages.get(num);
                        }
                        if (setMessageObjects[a] != messageObject) {
                            updateInterfaceForCurrentMessage(0);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (currentMessageObject == null || account != lastResumedAccount) {
                return;
            }
            int updateMask = (Integer) args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0) {
                updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0) {
                checkAndUpdateAvatar();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance(currentMessageObject.currentAccount).getPrintingString(currentMessageObject.getDialogId(), 0, false);
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle();
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset) {
            Integer mid = (Integer) args[0];
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer) view.getTag() == 3) {
                        PopupAudioView cell = view.findViewWithTag(300);
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.currentAccount == account && messageObject.getId() == mid) {
                            cell.updateButtonState();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer) view.getTag() == 3) {
                        PopupAudioView cell = view.findViewWithTag(300);
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.currentAccount == account && messageObject.getId() == mid) {
                            cell.updateProgress();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer) view.getTag() == 1) {
                        TextView textView = view.findViewWithTag(301);
                        if (textView != null) {
                            textView.invalidate();
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.contactsDidLoad) {
            if (account == lastResumedAccount) {
                updateSubtitle();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onFinish();
        MediaController.getInstance().setFeedbackView(chatActivityEnterView, false);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (avatarImageView != null) {
            avatarImageView.setImageDrawable(null);
        }
    }

    protected void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        if (isReply) {
            popupMessages.clear();
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.contactsDidLoad);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
