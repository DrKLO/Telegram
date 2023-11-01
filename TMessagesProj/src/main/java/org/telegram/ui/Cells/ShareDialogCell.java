/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class ShareDialogCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private SimpleTextView topicTextView;
    private CheckBox2 checkBox;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private TLRPC.User user;
    private int currentType;

    private float onlineProgress;
    private long lastUpdateTime;
    private long currentDialog;

    private boolean topicWasVisible;

    private int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    public static final int TYPE_SHARE = 0;
    public static final int TYPE_CALL = 1;
    public static final int TYPE_CREATE = 2;

    public ShareDialogCell(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);
        currentType = type;

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(28));
        if (type == TYPE_CREATE) {
            addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));
        } else {
            addView(imageView, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));
        }

        nameTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), AndroidUtilities.dp(10), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(nameTextView);
        nameTextView.setTextColor(getThemedColor(type == TYPE_CALL ? Theme.key_voipgroup_nameText : Theme.key_dialogTextBlack));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(2);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(2);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, currentType == TYPE_CREATE ? 58 : 66, 6, 0));

        topicTextView = new SimpleTextView(context);
        topicTextView.setTextColor(getThemedColor(type == TYPE_CALL ? Theme.key_voipgroup_nameText : Theme.key_dialogTextBlack));
        topicTextView.setTextSize(12);
        topicTextView.setMaxLines(2);
        topicTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        topicTextView.setAlignment(Layout.Alignment.ALIGN_CENTER);
        addView(topicTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, currentType == TYPE_CREATE ? 58 : 66, 6, 0));

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(Theme.key_dialogRoundCheckBox, type == TYPE_CALL ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(4);
        checkBox.setProgressDelegate(progress -> {
            float scale = 1.0f - (1.0f - 0.857f) * checkBox.getProgress();
            imageView.setScaleX(scale);
            imageView.setScaleY(scale);
            invalidate();
        });
        addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 19, currentType == TYPE_CREATE ? -40 : 42, 0, 0));

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(currentType == TYPE_CREATE ? 95 : 103), MeasureSpec.EXACTLY));
    }

    public void setDialog(long uid, boolean checked, CharSequence name) {
        if (DialogObject.isUserDialog(uid)) {
            user = MessagesController.getInstance(currentAccount).getUser(uid);
            avatarDrawable.setInfo(user);
            if (currentType != TYPE_CREATE && UserObject.isReplyUser(user)) {
                nameTextView.setText(LocaleController.getString("RepliesTitle", R.string.RepliesTitle));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageView.setImage(null, null, avatarDrawable, user);
            } else if (currentType != TYPE_CREATE && UserObject.isUserSelf(user)) {
                nameTextView.setText(LocaleController.getString("SavedMessages", R.string.SavedMessages));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                imageView.setImage(null, null, avatarDrawable, user);
            } else {
                if (name != null) {
                    nameTextView.setText(name);
                } else if (user != null) {
                    nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
                } else {
                    nameTextView.setText("");
                }
                imageView.setForUserOrChat(user, avatarDrawable);
            }
            imageView.setRoundRadius(AndroidUtilities.dp(28));
        } else {
            user = null;
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (chat != null) {
                nameTextView.setText(chat.title);
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(chat);
            imageView.setForUserOrChat(chat, avatarDrawable);
            imageView.setRoundRadius(chat != null && chat.forum ? AndroidUtilities.dp(16) : AndroidUtilities.dp(28));
        }
        currentDialog = uid;
        checkBox.setChecked(checked, false);
    }

    public long getCurrentDialog() {
        return currentDialog;
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
        if (!checked) {
            setTopic(null, true);
        }
    }

    public void setTopic(TLRPC.TL_forumTopic topic, boolean animate) {
        boolean wasVisible = topicWasVisible;
        boolean visible = topic != null;
        if (wasVisible != visible || !animate) {
            SpringAnimation prevSpring = (SpringAnimation) topicTextView.getTag(R.id.spring_tag);
            if (prevSpring != null) {
                prevSpring.cancel();
            }

            if (visible) {
                topicTextView.setText(ForumUtilities.getTopicSpannedName(topic, topicTextView.getTextPaint(), false));
                topicTextView.requestLayout();
            }
            if (animate) {
                SpringAnimation springAnimation = new SpringAnimation(new FloatValueHolder(visible ? 0f : 1000f))
                        .setSpring(new SpringForce(visible ? 1000f : 0f)
                                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addUpdateListener((animation, value, velocity) -> {
                            value /= 1000f;

                            topicTextView.setAlpha(value);
                            nameTextView.setAlpha(1f - value);

                            topicTextView.setTranslationX((1f - value) * -AndroidUtilities.dp(10));
                            nameTextView.setTranslationX(value * AndroidUtilities.dp(10));
                        })
                        .addEndListener((animation, canceled, value, velocity) -> {
                            topicTextView.setTag(R.id.spring_tag, null);
                        });
                topicTextView.setTag(R.id.spring_tag, springAnimation);
                springAnimation.start();
            } else {
                if (visible) {
                    topicTextView.setAlpha(1f);
                    nameTextView.setAlpha(0f);
                    topicTextView.setTranslationX(0);
                    nameTextView.setTranslationX(AndroidUtilities.dp(10));
                } else {
                    topicTextView.setAlpha(0f);
                    nameTextView.setAlpha(1f);
                    topicTextView.setTranslationX(-AndroidUtilities.dp(10));
                    nameTextView.setTranslationX(0);
                }
            }

            topicWasVisible = visible;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView && currentType != TYPE_CREATE) {
            if (user != null && !MessagesController.isSupportUser(user)) {
                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastUpdateTime;
                if (dt > 17) {
                    dt = 17;
                }
                lastUpdateTime = newTime;

                boolean isOnline = !user.self && !user.bot && (user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id));
                if (isOnline || onlineProgress != 0) {
                    int top = imageView.getBottom() - AndroidUtilities.dp(6);
                    int left = imageView.getRight() - AndroidUtilities.dp(10);
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(currentType == TYPE_CALL ? Theme.key_voipgroup_inviteMembersBackground : Theme.key_windowBackgroundWhite));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(7) * onlineProgress, Theme.dialogs_onlineCirclePaint);
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_chats_onlineCircle));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(5) * onlineProgress, Theme.dialogs_onlineCirclePaint);
                    if (isOnline) {
                        if (onlineProgress < 1.0f) {
                            onlineProgress += dt / 150.0f;
                            if (onlineProgress > 1.0f) {
                                onlineProgress = 1.0f;
                            }
                            imageView.invalidate();
                            invalidate();
                        }
                    } else {
                        if (onlineProgress > 0.0f) {
                            onlineProgress -= dt / 150.0f;
                            if (onlineProgress < 0.0f) {
                                onlineProgress = 0.0f;
                            }
                            imageView.invalidate();
                            invalidate();
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2;
        int cy = imageView.getTop() + imageView.getMeasuredHeight() / 2;
        Theme.checkboxSquare_checkPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBox));
        Theme.checkboxSquare_checkPaint.setAlpha((int) (checkBox.getProgress() * 255));
        int radius = AndroidUtilities.dp(currentType == TYPE_CREATE ? 24 : 28);
        AndroidUtilities.rectTmp.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, imageView.getRoundRadius()[0], imageView.getRoundRadius()[0], Theme.checkboxSquare_checkPaint);
        super.onDraw(canvas);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (checkBox.isChecked()) {
            info.setSelected(true);
        }
    }
}
