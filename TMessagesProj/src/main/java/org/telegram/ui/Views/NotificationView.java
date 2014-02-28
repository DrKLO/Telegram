/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.ui.ApplicationLoader;

import java.util.Timer;
import java.util.TimerTask;

public class NotificationView extends LinearLayout {
    public BackupImageView avatarImage;
    public TextView nameTextView;
    public TextView messageTextView;
    public ImageView closeButton;
    public FrameLayout textLayout;
    private WindowManager.LayoutParams notificationLayoutParams;
    private ViewGroup notificationParentView;
    private boolean onScreen;
    private Animation animShow;
    private Animation animHide;
    private Timer hideTimer;
    private int currentChatId = 0;
    private int currentUserId = 0;
    private int currentEncId = 0;
    private boolean isVisible;
    private final Integer timerSync = 1;

    public NotificationView(Context context) {
        super(context);
    }

    public NotificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        avatarImage = (BackupImageView)findViewById(R.id.avatar_image);
        nameTextView = (TextView)findViewById(R.id.name_text_view);
        messageTextView = (TextView)findViewById(R.id.message_text_view);
        closeButton = (ImageView)findViewById(R.id.close_button);
        textLayout = (FrameLayout)findViewById(R.id.text_layout);
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    synchronized (timerSync) {
                        if (hideTimer != null) {
                            hideTimer.cancel();
                            hideTimer = null;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                hide(true);
            }
        });

        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    synchronized (timerSync) {
                        if (hideTimer != null) {
                            hideTimer.cancel();
                            hideTimer = null;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                hide(true);

                if (currentChatId != 0) {
                    NotificationCenter.Instance.addToMemCache("push_chat_id", currentChatId);
                }
                if (currentUserId != 0) {
                    NotificationCenter.Instance.addToMemCache("push_user_id", currentUserId);
                }
                if (currentEncId != 0) {
                    NotificationCenter.Instance.addToMemCache("push_enc_id", currentEncId);
                }
                NotificationCenter.Instance.postNotificationName(658);
            }
        });

        notificationParentView = new FrameLayout(getContext());
        notificationParentView.addView(this);
        notificationParentView.setFocusable(false);
        setFocusable(false);
        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        notificationLayoutParams = new WindowManager.LayoutParams();
        notificationLayoutParams.height = 90;
        notificationLayoutParams.format = PixelFormat.TRANSLUCENT;
        notificationLayoutParams.width = WindowManager.LayoutParams.FILL_PARENT;
        notificationLayoutParams.gravity = Gravity.CLIP_HORIZONTAL | Gravity.TOP;
        notificationLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        notificationLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        isVisible = false;
        wm.addView(notificationParentView, notificationLayoutParams);
        notificationParentView.setVisibility(View.INVISIBLE);

        animHide = AnimationUtils.loadAnimation(ApplicationLoader.applicationContext, R.anim.slide_up);
        animHide.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {
                onScreen = false;
            }

            public void onAnimationRepeat(Animation animation) {

            }

            public void onAnimationEnd(Animation animation) {
                setVisibility(GONE);
                WindowManager wm = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                isVisible = false;
                notificationParentView.setVisibility(View.INVISIBLE);
            }
        });

        animShow = AnimationUtils.loadAnimation(ApplicationLoader.applicationContext, R.anim.slide_down);
        animShow.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {
                setVisibility(VISIBLE);
                onScreen = true;
            }

            public void onAnimationRepeat(Animation animation) {

            }

            public void onAnimationEnd(Animation animation) {

            }
        });
    }

    public void show(MessageObject object) {
        TLRPC.User user = MessagesController.Instance.users.get(object.messageOwner.from_id);
        TLRPC.Chat chat = null;
        long dialog_id = object.messageOwner.dialog_id;
        if (object.messageOwner.to_id.chat_id != 0) {
            chat = MessagesController.Instance.chats.get(object.messageOwner.to_id.chat_id);
            if (chat == null) {
                return;
            }
        }
        if (user == null) {
            return;
        }
        if (chat != null) {
            currentChatId = chat.id;
            currentUserId = 0;
            currentEncId = 0;
            nameTextView.setText(Utilities.formatName(user.first_name, user.last_name) + " @ " + chat.title);
        } else {
            int lower_id = (int)dialog_id;
            if (lower_id != 0 || dialog_id == 0) {
                currentUserId = user.id;
                currentEncId = 0;
            } else {
                currentUserId = 0;
                currentEncId = (int)(dialog_id >> 32);
            }
            currentChatId = 0;
            nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
        }
        nameTextView.setTextColor(Utilities.getColorForId(user.id));
        messageTextView.setText(object.messageText);
        TLRPC.FileLocation photo = null;
        if (user.photo != null) {
            photo = user.photo.photo_small;
        }
        avatarImage.setImage(photo, "50_50", Utilities.getUserAvatarForId(user.id));

        try {
            synchronized (timerSync) {
                if (hideTimer != null) {
                    hideTimer.cancel();
                    hideTimer = null;
                }
            }
            hideTimer = new Timer();
            hideTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            hide(true);
                        }
                    });
                    try {
                        synchronized (timerSync) {
                            if (hideTimer != null) {
                                hideTimer.cancel();
                                hideTimer = null;
                            }
                        }
                    } catch (Exception e) {
                FileLog.e("tmessages", e);
                    }
                }
            }, 3000);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        if (!onScreen) {
            WindowManager wm = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            isVisible = true;
            notificationParentView.setVisibility(View.VISIBLE);
            startAnimation(animShow);
        }
    }

    public void hide(boolean animation) {
        if (onScreen) {
            if (animation) {
                startAnimation(animHide);
            } else {
                try {
                    synchronized (timerSync) {
                        if (hideTimer != null) {
                            hideTimer.cancel();
                            hideTimer = null;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                onScreen = false;
                setVisibility(GONE);
                if (notificationParentView != null && notificationParentView.getParent() != null) {
                    WindowManager wm = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                    isVisible = false;
                    notificationParentView.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    public void destroy() {
        try {
            if (notificationParentView != null) {
                notificationParentView.removeView(this);
                try {
                    if (notificationParentView.getParent() != null) {
                        WindowManager wm = (WindowManager)ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeViewImmediate(notificationParentView);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            notificationParentView = null;
            notificationLayoutParams = null;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void applyOrientationPaddings(boolean isLandscape, int height) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)avatarImage.getLayoutParams();
        params.width = height;
        params.height = height;
        avatarImage.setLayoutParams(params);
        FrameLayout.LayoutParams params1 = (FrameLayout.LayoutParams)textLayout.getLayoutParams();
        if (isLandscape) {
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameTextView.setPadding(0, Utilities.dp(2), 0, 0);
            messageTextView.setPadding(0, Utilities.dp(18), 0, 0);
            if (Utilities.isRTL) {
                params1.setMargins(Utilities.dp(40), 0, height + Utilities.dp(6), 0);
            } else {
                params1.setMargins(height + Utilities.dp(6), 0, Utilities.dp(40), 0);
            }
        } else {
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            nameTextView.setPadding(0, Utilities.dp(4), 0, 0);
            messageTextView.setPadding(0, Utilities.dp(24), 0, 0);
            if (Utilities.isRTL) {
                params1.setMargins(Utilities.dp(40), 0, height + Utilities.dp(8), 0);
            } else {
                params1.setMargins(height + Utilities.dp(8), 0, Utilities.dp(40), 0);
            }
        }
        textLayout.setLayoutParams(params1);

        if (notificationParentView != null) {
            notificationLayoutParams.height = height;
            if (notificationParentView.getParent() != null) {
                WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                wm.updateViewLayout(notificationParentView, notificationLayoutParams);
            }
        }
    }
}
