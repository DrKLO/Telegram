/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesController;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessageObject;
import org.telegram.android.PhotoObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.Components.PopupAudioView;
import org.telegram.ui.Components.TypingDotsDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

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
    private ArrayList<ViewGroup> textViews = new ArrayList<ViewGroup>();
    private ArrayList<ViewGroup> imageViews = new ArrayList<ViewGroup>();
    private ArrayList<ViewGroup> audioViews = new ArrayList<ViewGroup>();
    private VelocityTracker velocityTracker = null;
    private TypingDotsDrawable typingDotsDrawable;

    private int classGuid;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private boolean finished = false;
    private CharSequence lastPrintString;
    private MessageObject currentMessageObject = null;
    private int currentMessageNum = 0;
    private PowerManager.WakeLock wakeLock = null;
    private boolean animationInProgress = false;
    private long animationStartTime = 0;
    private float moveStartX = -1;
    private boolean startedMoving = false;
    private Runnable onAnimationEndRunnable = null;

    private class FrameLayoutTouch extends FrameLayoutFixed {
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
            ((PopupNotificationActivity)getContext()).onTouchEventMy(null);
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    public class FrameLayoutAnimationListener extends FrameLayoutFixed {
        public FrameLayoutAnimationListener(Context context) {
            super(context);
        }

        public FrameLayoutAnimationListener(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public FrameLayoutAnimationListener(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected void onAnimationEnd() {
            super.onAnimationEnd();
            if (onAnimationEndRunnable != null) {
                onAnimationEndRunnable.run();
                onAnimationEndRunnable = null;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);

        typingDotsDrawable = new TypingDotsDrawable();

        chatActivityEnterView = new ChatActivityEnterView();
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend() {
                if (currentMessageObject == null) {
                    return;
                }
                if (currentMessageNum >= 0 && currentMessageNum < NotificationsController.getInstance().popupMessages.size()) {
                    NotificationsController.getInstance().popupMessages.remove(currentMessageNum);
                }
                MessagesController.getInstance().markDialogAsRead(currentMessageObject.getDialogId(), currentMessageObject.messageOwner.id, Math.max(0, currentMessageObject.messageOwner.id), 0, currentMessageObject.messageOwner.date, true, true);
                currentMessageObject = null;
                getNewMessage();
            }

            @Override
            public void needSendTyping() {
                if (currentMessageObject != null) {
                    MessagesController.getInstance().sendTyping(currentMessageObject.getDialogId(), classGuid);
                }
            }

            @Override
            public void onAttachButtonHidden() {

            }

            @Override
            public void onAttachButtonShow() {

            }
        });

        setContentView(R.layout.popup_notification_layout);
        RelativeLayout popupContainer = (RelativeLayout) findViewById(R.id.popup_container);
        messageContainer = new FrameLayoutTouch(this);
        popupContainer.addView(messageContainer, 0);

        actionBar = new ActionBar(this);
        actionBar.setOccupyStatusBar(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundResource(R.color.header);
        actionBar.setItemsBackground(R.drawable.bar_selector);
        popupContainer.addView(actionBar);
        ViewGroup.LayoutParams layoutParams = actionBar.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);

        ActionBarMenu menu = actionBar.createMenu();
        View view = menu.addItemResource(2, R.layout.popup_count_layout);
        countText = (TextView) view.findViewById(R.id.count_text);

        avatarContainer = new FrameLayoutFixed(this);
        avatarContainer.setBackgroundResource(R.drawable.bar_selector);
        avatarContainer.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        actionBar.addView(avatarContainer);
        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        layoutParams2.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.rightMargin = AndroidUtilities.dp(48);
        layoutParams2.leftMargin = AndroidUtilities.dp(60);
        layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
        avatarContainer.setLayoutParams(layoutParams2);

        avatarImageView = new BackupImageView(this);
        avatarImageView.imageReceiver.setRoundRadius(AndroidUtilities.dp(21));
        avatarImageView.processDetach = false;
        avatarContainer.addView(avatarImageView);
        layoutParams2 = (FrameLayout.LayoutParams) avatarImageView.getLayoutParams();
        layoutParams2.width = AndroidUtilities.dp(42);
        layoutParams2.height = AndroidUtilities.dp(42);
        layoutParams2.topMargin = AndroidUtilities.dp(3);
        avatarImageView.setLayoutParams(layoutParams2);

        nameTextView = new TextView(this);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        avatarContainer.addView(nameTextView);
        layoutParams2 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.leftMargin = AndroidUtilities.dp(54);
        layoutParams2.bottomMargin = AndroidUtilities.dp(22);
        layoutParams2.gravity = Gravity.BOTTOM;
        nameTextView.setLayoutParams(layoutParams2);

        onlineTextView = new TextView(this);
        onlineTextView.setTextColor(0xffd7e8f7);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity(Gravity.LEFT);
        avatarContainer.addView(onlineTextView);
        layoutParams2 = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
        layoutParams2.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams2.height = FrameLayout.LayoutParams.WRAP_CONTENT;
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

        chatActivityEnterView.setContainerView(this, findViewById(R.id.chat_layout));

        PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "screen");
        wakeLock.setReferenceCounted(false);

        handleIntent(getIntent());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidUtilities.checkDisplaySize();
        fixLayout();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void switchToNextMessage() {
        if (NotificationsController.getInstance().popupMessages.size() > 1) {
            if (currentMessageNum < NotificationsController.getInstance().popupMessages.size() - 1) {
                currentMessageNum++;
            } else {
                currentMessageNum = 0;
            }
            currentMessageObject = NotificationsController.getInstance().popupMessages.get(currentMessageNum);
            updateInterfaceForCurrentMessage(2);
            countText.setText(String.format("%d/%d", currentMessageNum + 1, NotificationsController.getInstance().popupMessages.size()));
        }
    }

    private void switchToPreviousMessage() {
        if (NotificationsController.getInstance().popupMessages.size() > 1) {
            if (currentMessageNum > 0) {
                currentMessageNum--;
            } else {
                currentMessageNum = NotificationsController.getInstance().popupMessages.size() - 1;
            }
            currentMessageObject = NotificationsController.getInstance().popupMessages.get(currentMessageNum);
            updateInterfaceForCurrentMessage(1);
            countText.setText(String.format("%d/%d", currentMessageNum + 1, NotificationsController.getInstance().popupMessages.size()));
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
            int diff = (int)(x - moveStartX);
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
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) centerView.getLayoutParams();
                int diff = (int)(motionEvent.getX() - moveStartX);
                int width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
                int moveDiff = 0;
                int forceMove = 0;
                View otherView = null;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    if (velocityTracker.getXVelocity() >= 3500) {
                        forceMove = 1;
                    } else if (velocityTracker.getXVelocity() <= -3500) {
                        forceMove = 2;
                    }
                }
                if ((forceMove == 1 || diff > width / 3) && leftView != null) {
                    moveDiff = width - layoutParams.leftMargin;
                    otherView = leftView;
                    onAnimationEndRunnable = new Runnable() {
                        @Override
                        public void run() {
                            animationInProgress = false;
                            switchToPreviousMessage();
                            AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                        }
                    };
                } else if ((forceMove == 2 || diff < -width / 3) && rightView != null) {
                    moveDiff = -width - layoutParams.leftMargin;
                    otherView = rightView;
                    onAnimationEndRunnable = new Runnable() {
                        @Override
                        public void run() {
                            animationInProgress = false;
                            switchToNextMessage();
                            AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                        }
                    };
                } else if (layoutParams.leftMargin != 0) {
                    moveDiff = -layoutParams.leftMargin;
                    otherView = diff > 0 ? leftView : rightView;
                    onAnimationEndRunnable = new Runnable() {
                        @Override
                        public void run() {
                            animationInProgress = false;
                            applyViewsLayoutParams(0);
                            AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                        }
                    };
                }
                if (moveDiff != 0) {
                    int time = (int)(Math.abs((float)moveDiff / (float)width) * 200);
                    TranslateAnimation animation = new TranslateAnimation(0, moveDiff, 0, 0);
                    animation.setDuration(time);
                    centerView.startAnimation(animation);
                    if (otherView != null) {
                        animation = new TranslateAnimation(0, moveDiff, 0, 0);
                        animation.setDuration(time);
                        otherView.startAnimation(animation);
                    }
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
        FrameLayout.LayoutParams layoutParams = null;
        int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
        if (leftView != null) {
            layoutParams = (FrameLayout.LayoutParams) leftView.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.width = widht;
            layoutParams.leftMargin = -widht + xOffset;
            leftView.setLayoutParams(layoutParams);
        }
        if (centerView != null) {
            layoutParams = (FrameLayout.LayoutParams) centerView.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.width = widht;
            layoutParams.leftMargin = xOffset;
            centerView.setLayoutParams(layoutParams);
        }
        if (rightView != null) {
            layoutParams = (FrameLayout.LayoutParams) rightView.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.width = widht;
            layoutParams.leftMargin = widht + xOffset;
            rightView.setLayoutParams(layoutParams);
        }
        messageContainer.invalidate();
    }

    private ViewGroup getViewForMessage(int num, boolean applyOffset) {
        if (NotificationsController.getInstance().popupMessages.size() == 1 && (num < 0 || num >= NotificationsController.getInstance().popupMessages.size())) {
            return null;
        }
        if (num == -1) {
            num = NotificationsController.getInstance().popupMessages.size() - 1;
        } else if (num == NotificationsController.getInstance().popupMessages.size()) {
            num = 0;
        }
        ViewGroup view = null;
        MessageObject messageObject = NotificationsController.getInstance().popupMessages.get(num);
        if (messageObject.type == 1 || messageObject.type == 4) {
            if (imageViews.size() > 0) {
                view = imageViews.get(0);
                imageViews.remove(0);
            } else {
                view = new FrameLayoutAnimationListener(this);
                view.addView(getLayoutInflater().inflate(R.layout.popup_image_layout, null));
                view.setTag(2);

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openCurrentMessage();
                    }
                });
            }

            TextView messageText = (TextView)view.findViewById(R.id.message_text);
            BackupImageView imageView = (BackupImageView) view.findViewById(R.id.message_image);
            imageView.imageReceiver.setAspectFit(true);

            if (messageObject.type == 1) {
                PhotoObject currentPhotoObject = PhotoObject.getClosestImageWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                boolean photoSet = false;
                if (currentPhotoObject != null) {
                    boolean photoExist = true;
                    if (messageObject.type == 1) {
                        File cacheFile = FileLoader.getPathToMessage(messageObject.messageOwner);
                        if (!cacheFile.exists()) {
                            photoExist = false;
                        }
                    }
                    if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO)) {
                        imageView.setImage(currentPhotoObject.photoOwner.location, "100_100", messageObject.imagePreview, currentPhotoObject.photoOwner.size);
                        photoSet = true;
                    } else {
                        if (messageObject.imagePreview != null) {
                            imageView.setImageBitmap(messageObject.imagePreview);
                            photoSet = true;
                        }
                    }
                }
                if (!photoSet) {
                    imageView.setVisibility(View.GONE);
                    messageText.setVisibility(View.VISIBLE);
                    messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, MessagesController.getInstance().fontSize);
                    messageText.setText(messageObject.messageText);
                } else {
                    imageView.setVisibility(View.VISIBLE);
                    messageText.setVisibility(View.GONE);
                }
            } else if (messageObject.type == 4) {
                messageText.setVisibility(View.GONE);
                messageText.setText(messageObject.messageText);
                imageView.setVisibility(View.VISIBLE);
                double lat = messageObject.messageOwner.media.geo.lat;
                double lon = messageObject.messageOwner.media.geo._long;
                String currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=13&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int)Math.ceil(AndroidUtilities.density)), lat, lon);
                imageView.setImage(currentUrl, null, null);
            }
        } else if (messageObject.type == 2) {
            PopupAudioView cell = null;
            if (audioViews.size() > 0) {
                view = audioViews.get(0);
                audioViews.remove(0);
                cell = (PopupAudioView)view.findViewWithTag(300);
            } else {
                view = new FrameLayoutAnimationListener(this);
                view.addView(getLayoutInflater().inflate(R.layout.popup_audio_layout, null));
                view.setTag(3);

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openCurrentMessage();
                    }
                });

                ViewGroup audioContainer = (ViewGroup)view.findViewById(R.id.audio_container);
                cell = new PopupAudioView(this);
                cell.setTag(300);
                audioContainer.addView(cell);
            }

            cell.setMessageObject(messageObject);
            if (MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_AUDIO)) {
                cell.downloadAudioIfNeed();
            }
        } else {
            if (textViews.size() > 0) {
                view = textViews.get(0);
                textViews.remove(0);
            } else {
                view = new FrameLayoutAnimationListener(this);
                view.addView(getLayoutInflater().inflate(R.layout.popup_text_layout, null));
                view.setTag(1);

                View textContainer = view.findViewById(R.id.text_container);
                textContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openCurrentMessage();
                    }
                });
            }
            TextView messageText = (TextView)view.findViewById(R.id.message_text);
            messageText.setTag(301);
            messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, MessagesController.getInstance().fontSize);
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
                layoutParams.leftMargin = 0;
            } else if (num == currentMessageNum - 1) {
                layoutParams.leftMargin = -widht;
            } else if (num == currentMessageNum + 1) {
                layoutParams.leftMargin = widht;
            }
            view.setLayoutParams(layoutParams);
            view.invalidate();
        }

        return view;
    }

    private void reuseView(ViewGroup view) {
        if (view == null) {
            return;
        }
        int tag = (Integer)view.getTag();
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
        if (move == 0) {
            reuseView(centerView);
            reuseView(leftView);
            reuseView(rightView);
            for (int a = currentMessageNum - 1; a < currentMessageNum + 2; a++) {
                if (a == currentMessageNum - 1) {
                    leftView = getViewForMessage(a, true);
                } else if (a == currentMessageNum) {
                    centerView = getViewForMessage(a, true);
                } else if (a == currentMessageNum + 1) {
                    rightView = getViewForMessage(a, true);
                }
            }
        } else if (move == 1) {
            reuseView(rightView);
            rightView = centerView;
            centerView = leftView;
            leftView = getViewForMessage(currentMessageNum - 1, true);
        } else if (move == 2) {
            reuseView(leftView);
            leftView = centerView;
            centerView = rightView;
            rightView = getViewForMessage(currentMessageNum + 1, true);
        } else if (move == 3) {
            if (rightView != null) {
                int offset = ((FrameLayout.LayoutParams) rightView.getLayoutParams()).leftMargin;
                reuseView(rightView);
                rightView = getViewForMessage(currentMessageNum + 1, false);
                int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) rightView.getLayoutParams();
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                layoutParams.width = widht;
                layoutParams.leftMargin = offset;
                rightView.setLayoutParams(layoutParams);
                rightView.invalidate();
            }
        } else if (move == 4) {
            if (leftView != null) {
                int offset = ((FrameLayout.LayoutParams) leftView.getLayoutParams()).leftMargin;
                reuseView(leftView);
                leftView = getViewForMessage(0, false);
                int widht = AndroidUtilities.displaySize.x - AndroidUtilities.dp(24);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) leftView.getLayoutParams();
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                layoutParams.width = widht;
                layoutParams.leftMargin = offset;
                leftView.setLayoutParams(layoutParams);
                leftView.invalidate();
            }
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
                    int padding = (AndroidUtilities.getCurrentActionBarHeight() - AndroidUtilities.dp(48)) / 2;
                    avatarContainer.setPadding(avatarContainer.getPaddingLeft(), padding, avatarContainer.getPaddingRight(), padding);
                    return false;
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
                        layoutParams.topMargin = AndroidUtilities.getCurrentActionBarHeight();
                        layoutParams.bottomMargin = AndroidUtilities.dp(48);
                        layoutParams.width = ViewGroup.MarginLayoutParams.MATCH_PARENT;
                        layoutParams.height = ViewGroup.MarginLayoutParams.MATCH_PARENT;
                        messageContainer.setLayoutParams(layoutParams);
                        applyViewsLayoutParams(0);
                    }
                    return false;
                }
            });
        }
    }

    private void handleIntent(Intent intent) {
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
        if (NotificationsController.getInstance().popupMessages.isEmpty()) {
            onFinish();
            finish();
            return;
        }

        boolean found = false;
        if ((currentMessageNum != 0 || chatActivityEnterView.hasText() || startedMoving) && currentMessageObject != null) {
            for (int a = 0; a < NotificationsController.getInstance().popupMessages.size(); a++) {
                if (NotificationsController.getInstance().popupMessages.get(a).messageOwner.id == currentMessageObject.messageOwner.id) {
                    currentMessageNum = a;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            currentMessageNum = 0;
            currentMessageObject = NotificationsController.getInstance().popupMessages.get(0);
            updateInterfaceForCurrentMessage(0);
        } else if (startedMoving) {
            if (currentMessageNum == NotificationsController.getInstance().popupMessages.size() - 1) {
                prepareLayouts(3);
            } else if (currentMessageNum == 1) {
                prepareLayouts(4);
            }
        }
        countText.setText(String.format("%d/%d", currentMessageNum + 1, NotificationsController.getInstance().popupMessages.size()));
    }

    private void openCurrentMessage() {
        if (currentMessageObject == null) {
            return;
        }
        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        long dialog_id = currentMessageObject.getDialogId();
        if ((int)dialog_id != 0) {
            int lower_id = (int)dialog_id;
            if (lower_id < 0) {
                intent.putExtra("chatId", -lower_id);
            } else {
                intent.putExtra("userId", lower_id);
            }
        } else {
            intent.putExtra("encId", (int)(dialog_id >> 32));
        }
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(0x00008000);
        startActivity(intent);
        onFinish();
        finish();
    }

    private void updateInterfaceForCurrentMessage(int move) {
        if (actionBar == null) {
            return;
        }
        currentChat = null;
        currentUser = null;
        long dialog_id = currentMessageObject.getDialogId();
        chatActivityEnterView.setDialogId(dialog_id);
        if ((int)dialog_id != 0) {
            int lower_id = (int)dialog_id;
            if (lower_id > 0) {
                currentUser = MessagesController.getInstance().getUser(lower_id);
            } else {
                currentChat = MessagesController.getInstance().getChat(-lower_id);
                currentUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
            }
        } else {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat((int)(dialog_id >> 32));
            currentUser = MessagesController.getInstance().getUser(encryptedChat.user_id);
        }

        if (currentChat != null && currentUser != null) {
            nameTextView.setText(currentChat.title);
            onlineTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            nameTextView.setCompoundDrawablePadding(0);
        } else if (currentUser != null) {
            nameTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            if ((int)dialog_id == 0) {
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
        if (actionBar == null) {
            return;
        }
        if (currentChat != null || currentUser == null) {
            return;
        }
        if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
            if (currentUser.phone != null && currentUser.phone.length() != 0) {
                nameTextView.setText(PhoneFormat.getInstance().format("+" + currentUser.phone));
            } else {
                nameTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            }
        } else {
            nameTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
        }
        CharSequence printString = MessagesController.getInstance().printingStrings.get(currentMessageObject.getDialogId());
        if (printString == null || printString.length() == 0) {
            lastPrintString = null;
            setTypingAnimation(false);
            TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
            if (user != null) {
                currentUser = user;
            }
            onlineTextView.setText(LocaleController.formatUserStatus(currentUser));
        } else {
            lastPrintString = printString;
            onlineTextView.setText(printString);
            setTypingAnimation(true);
        }
    }

    private void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        AvatarDrawable avatarDrawable = null;
        if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
            avatarDrawable = new AvatarDrawable(currentChat);
        } else if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            }
            avatarDrawable = new AvatarDrawable(currentUser);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
        }
    }

    private void setTypingAnimation(boolean start) {
        if (actionBar == null) {
            return;
        }
        if (start) {
            try {
                onlineTextView.setCompoundDrawablesWithIntrinsicBounds(typingDotsDrawable, null, null, null);
                onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                typingDotsDrawable.start();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            onlineTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            onlineTextView.setCompoundDrawablePadding(0);
            typingDotsDrawable.stop();
        }
    }

    @Override
    public void onBackPressed() {
        if (chatActivityEnterView.isEmojiPopupShowing()) {
            chatActivityEnterView.hideEmojiPopup();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setFieldFocused(true);
        }
        ConnectionsManager.getInstance().setAppPaused(false, false);
        fixLayout();
        wakeLock.acquire(7000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.hideEmojiPopup();
            chatActivityEnterView.setFieldFocused(false);
        }
        ConnectionsManager.getInstance().setAppPaused(true, false);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            onFinish();
            finish();
        } else if (id == NotificationCenter.pushMessagesUpdated) {
            getNewMessage();
        } else if (id == NotificationCenter.updateInterfaces) {
            if (currentMessageObject == null) {
                return;
            }
            int updateMask = (Integer)args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0) {
                updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0) {
                checkAndUpdateAvatar();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance().printingStrings.get(currentMessageObject.getDialogId());
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle();
                }
            }
        } else if (id == NotificationCenter.audioDidReset) {
            Integer mid = (Integer)args[0];
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer)view.getTag() == 3) {
                        PopupAudioView cell = (PopupAudioView)view.findViewWithTag(300);
                        if (cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == mid) {
                            cell.updateButtonState();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.audioProgressDidChanged) {
            Integer mid = (Integer)args[0];
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer)view.getTag() == 3) {
                        PopupAudioView cell = (PopupAudioView)view.findViewWithTag(300);
                        if (cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == mid) {
                            cell.updateProgress();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer)view.getTag() == 1) {
                        TextView textView = (TextView)view.findViewWithTag(301);
                        if (textView != null) {
                            textView.invalidate();
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.contactsDidLoaded) {
            updateSubtitle();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onFinish();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    protected void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
