/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class GroupCreateUserCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private CheckBox2 checkBox;
    private AvatarDrawable avatarDrawable;
    private Object currentObject;
    private CharSequence currentName;
    private CharSequence currentStatus;

    private int checkBoxType;

    private int currentAccount = UserConfig.selectedAccount;

    private String lastName;
    private int lastStatus;
    private TLRPC.FileLocation lastAvatar;

    private boolean drawDivider;
    private int padding;

    private ValueAnimator animator;
    private boolean isChecked;
    private float checkProgress;
    private Paint paint;

    private boolean forceDarkTheme;

    private boolean showSelfAsSaved;

    public GroupCreateUserCell(Context context, int checkBoxType, int pad, boolean selfAsSaved) {
        this(context, checkBoxType, pad, selfAsSaved, false);
    }

    public GroupCreateUserCell(Context context, int checkBoxType, int pad, boolean selfAsSaved, boolean forCall) {
        super(context);
        this.checkBoxType = checkBoxType;
        forceDarkTheme = forCall;

        drawDivider = false;
        padding = pad;
        showSelfAsSaved = selfAsSaved;
        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : (13 + padding), 6, LocaleController.isRTL ? (13 + padding) : 0, 0));

        nameTextView = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value, boolean force) {
                value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false);
                return super.setText(value, force);
            }
        };
        NotificationCenter.listenEmojiLoading(nameTextView);
        nameTextView.setTextColor(Theme.getColor(forceDarkTheme ? Theme.key_voipgroup_nameText : Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 28 : 72) + padding, 10, (LocaleController.isRTL ? 72 : 28) + padding, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 28 : 72) + padding, 32, (LocaleController.isRTL ? 72 : 28) + padding, 0));

        if (checkBoxType == 1) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40 + padding, 33, LocaleController.isRTL ? 39 + padding : 0, 0));
        } else if (checkBoxType == 2) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
        }

        setWillNotDraw(false);
    }

    public void setObject(TLObject object, CharSequence name, CharSequence status, boolean drawDivider) {
        setObject(object, name, status);
        this.drawDivider = drawDivider;
    }

    public void setObject(Object object, CharSequence name, CharSequence status) {
        currentObject = object;
        currentStatus = status;
        currentName = name;
        drawDivider = false;
        update(0);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        } else if (checkBoxType == 2) {
            if (isChecked == checked) {
                return;
            }
            isChecked = checked;
            if (animator != null) {
                animator.cancel();
            }
            if (animated) {
                animator = ValueAnimator.ofFloat(0.0f, 1.0f);
                animator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    float scale = isChecked ? 1.0f - 0.18f * v : 0.82f + 0.18f * v;
                    avatarImageView.setScaleX(scale);
                    avatarImageView.setScaleY(scale);
                    checkProgress = isChecked ? v : 1.0f - v;
                    invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animator = null;
                    }
                });
                animator.setDuration(180);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animator.start();
            } else {
                avatarImageView.setScaleX(isChecked ? 0.82f : 1.0f);
                avatarImageView.setScaleY(isChecked ? 0.82f : 1.0f);
                checkProgress = isChecked ? 1.0f : 0.0f;
            }
            invalidate();
        }
    }

    public void setCheckBoxEnabled(boolean enabled) {
        if (checkBox != null) {
            checkBox.setEnabled(enabled);
        }
    }

    public boolean isChecked() {
        if (checkBox != null) {
            return checkBox.isChecked();
        }
        return isChecked;
    }

    public Object getObject() {
        return currentObject;
    }

    public void setDrawDivider(boolean value) {
        drawDivider = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(currentObject instanceof String ? 50 : 58), MeasureSpec.EXACTLY));
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update(int mask) {
        if (currentObject == null) {
            return;
        }
        TLRPC.FileLocation photo = null;
        String newName = null;

        TLRPC.Chat currentChat = null;
        if (currentObject instanceof String) {
            ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(15);
            avatarImageView.getLayoutParams().width = avatarImageView.getLayoutParams().height = AndroidUtilities.dp(38);
            if (checkBox != null) {
                ((LayoutParams) checkBox.getLayoutParams()).topMargin = AndroidUtilities.dp(25);
                if (LocaleController.isRTL) {
                    ((LayoutParams) checkBox.getLayoutParams()).rightMargin = AndroidUtilities.dp(31);
                } else {
                    ((LayoutParams) checkBox.getLayoutParams()).leftMargin = AndroidUtilities.dp(32);
                }
            }

            String str = (String) currentObject;
            switch (str) {
                case "contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS);
                    break;
                case "non_contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS);
                    break;
                case "groups":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
                    break;
                case "channels":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS);
                    break;
                case "bots":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_BOTS);
                    break;
                case "muted":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_MUTED);
                    break;
                case "read":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_READ);
                    break;
                case "archived":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED);
                    break;
            }
            lastName = null;
            nameTextView.setText(currentName, true);
            statusTextView.setText(null);
            avatarImageView.setImage(null, "50_50", avatarDrawable);
        } else {
            if (currentStatus != null && TextUtils.isEmpty(currentStatus)) {
                ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
            } else {
                ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(10);
            }
            avatarImageView.getLayoutParams().width = avatarImageView.getLayoutParams().height = AndroidUtilities.dp(46);
            if (checkBox != null) {
                ((LayoutParams) checkBox.getLayoutParams()).topMargin = AndroidUtilities.dp(29) + padding;
                if (LocaleController.isRTL) {
                    ((LayoutParams) checkBox.getLayoutParams()).rightMargin = AndroidUtilities.dp(39) + padding;
                } else {
                    ((LayoutParams) checkBox.getLayoutParams()).leftMargin = AndroidUtilities.dp(45) + padding;
                }
            }

            if (currentObject instanceof TLRPC.User) {
                TLRPC.User currentUser = (TLRPC.User) currentObject;
                if (showSelfAsSaved && UserObject.isUserSelf(currentUser)) {
                    nameTextView.setText(LocaleController.getString("SavedMessages", R.string.SavedMessages), true);
                    statusTextView.setText(null);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser);
                    ((LayoutParams) nameTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(19);
                    return;
                }
                if (currentUser.photo != null) {
                    photo = currentUser.photo.photo_small;
                }
                if (mask != 0) {
                    boolean continueUpdate = false;
                    if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                        if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                            continueUpdate = true;
                        }
                    }
                    if (currentUser != null && currentStatus == null && !continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                        int newStatus = 0;
                        if (currentUser.status != null) {
                            newStatus = currentUser.status.expires;
                        }
                        if (newStatus != lastStatus) {
                            continueUpdate = true;
                        }
                    }
                    if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                        newName = UserObject.getUserName(currentUser);
                        if (!newName.equals(lastName)) {
                            continueUpdate = true;
                        }
                    }
                    if (!continueUpdate) {
                        return;
                    }
                }
                avatarDrawable.setInfo(currentUser);
                lastStatus = currentUser.status != null ? currentUser.status.expires : 0;

                if (currentName != null) {
                    lastName = null;
                    nameTextView.setText(currentName, true);
                } else {
                    lastName = newName == null ? UserObject.getUserName(currentUser) : newName;
                    nameTextView.setText(lastName);
                }

                if (currentStatus == null) {
                    if (currentUser.bot) {
                        statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                        statusTextView.setTextColor(Theme.getColor(forceDarkTheme ? Theme.key_voipgroup_lastSeenText : Theme.key_windowBackgroundWhiteGrayText));
                        statusTextView.setText(LocaleController.getString("Bot", R.string.Bot));
                    } else {
                        if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id)) {
                            statusTextView.setTag(Theme.key_windowBackgroundWhiteBlueText);
                            statusTextView.setTextColor(Theme.getColor(forceDarkTheme ? Theme.key_voipgroup_listeningText : Theme.key_windowBackgroundWhiteBlueText));
                            statusTextView.setText(LocaleController.getString("Online", R.string.Online));
                        } else {
                            statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                            statusTextView.setTextColor(Theme.getColor(forceDarkTheme ? Theme.key_voipgroup_lastSeenText : Theme.key_windowBackgroundWhiteGrayText));
                            statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser));
                        }
                    }
                }

                avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
            } else {
                currentChat = (TLRPC.Chat) currentObject;
                if (currentChat.photo != null) {
                    photo = currentChat.photo.photo_small;
                }
                if (mask != 0) {
                    boolean continueUpdate = false;
                    if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                        if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                            continueUpdate = true;
                        }
                    }
                    if (!continueUpdate && currentName == null && lastName != null && (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                        newName = currentChat.title;
                        if (!newName.equals(lastName)) {
                            continueUpdate = true;
                        }
                    }
                    if (!continueUpdate) {
                        return;
                    }
                }

                avatarDrawable.setInfo(currentChat);

                if (currentName != null) {
                    lastName = null;
                    nameTextView.setText(currentName, true);
                } else {
                    lastName = newName == null ? currentChat.title : newName;
                    nameTextView.setText(lastName);
                }

                if (currentStatus == null) {
                    statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
                    statusTextView.setTextColor(Theme.getColor(forceDarkTheme ? Theme.key_voipgroup_lastSeenText : Theme.key_windowBackgroundWhiteGrayText));
                    if (currentChat.participants_count != 0) {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            statusTextView.setText(LocaleController.formatPluralString("Subscribers", currentChat.participants_count));
                        } else {
                            statusTextView.setText(LocaleController.formatPluralString("Members", currentChat.participants_count));
                        }
                    } else if (currentChat.has_geo) {
                        statusTextView.setText(LocaleController.getString("MegaLocation", R.string.MegaLocation));
                    } else if (!ChatObject.isPublic(currentChat)) {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            statusTextView.setText(LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate));
                        } else {
                            statusTextView.setText(LocaleController.getString("MegaPrivate", R.string.MegaPrivate));
                        }
                    } else {
                        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                            statusTextView.setText(LocaleController.getString("ChannelPublic", R.string.ChannelPublic));
                        } else {
                            statusTextView.setText(LocaleController.getString("MegaPublic", R.string.MegaPublic));
                        }
                    }
                }

                avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
            }
        }


        avatarImageView.setRoundRadius(currentChat != null && currentChat.forum ? AndroidUtilities.dp(14) : AndroidUtilities.dp(24));
        if (currentStatus != null) {
            statusTextView.setText(currentStatus, true);
            statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
            statusTextView.setTextColor(Theme.getColor(forceDarkTheme ? Theme.key_voipgroup_lastSeenText : Theme.key_windowBackgroundWhiteGrayText));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (checkBoxType == 2 && (isChecked || checkProgress > 0.0f)) {
            paint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
            float cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2;
            float cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2;
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(18) + AndroidUtilities.dp(4) * checkProgress, paint);
        }
        if (drawDivider) {
            int start = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 72 + padding);
            int end = getMeasuredWidth() - AndroidUtilities.dp(!LocaleController.isRTL ? 0 : 72 + padding);
            if (forceDarkTheme) {
                Theme.dividerExtraPaint.setColor(Theme.getColor(Theme.key_voipgroup_actionBar));
                canvas.drawRect(start, getMeasuredHeight() - 1, end, getMeasuredHeight(), Theme.dividerExtraPaint);
            } else {
                canvas.drawRect(start, getMeasuredHeight() - 1, end, getMeasuredHeight(), Theme.dividerPaint);
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (isChecked()) {
            info.setCheckable(true);
            info.setChecked(true);
        }
    }

    public SimpleTextView getStatusTextView() {
        return statusTextView;
    }
}
