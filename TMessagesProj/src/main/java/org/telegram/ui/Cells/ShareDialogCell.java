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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class ShareDialogCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private CheckBox2 checkBox;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private TLRPC.User user;

    private float onlineProgress;
    private long lastUpdateTime;

    private int currentAccount = UserConfig.selectedAccount;

    public ShareDialogCell(Context context) {
        super(context);

        setWillNotDraw(false);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(28));
        addView(imageView, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(2);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(2);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 66, 6, 0));

        checkBox = new CheckBox2(context, 21);
        checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(4);
        checkBox.setProgressDelegate(progress -> {
            float scale = 1.0f - (1.0f - 0.857f) * checkBox.getProgress();
            imageView.setScaleX(scale);
            imageView.setScaleY(scale);
        });
        addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 19, 42, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(103), MeasureSpec.EXACTLY));
    }

    public void setDialog(int uid, boolean checked, CharSequence name) {
        if (uid > 0) {
            user = MessagesController.getInstance(currentAccount).getUser(uid);
            avatarDrawable.setInfo(user);
            if (UserObject.isUserSelf(user)) {
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
                imageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);
            }
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
            imageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable, chat);
        }
        checkBox.setChecked(checked, false);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView) {
            if (user != null && !MessagesController.isSupportUser(user)) {
                long newTime = SystemClock.uptimeMillis();
                long dt = newTime - lastUpdateTime;
                if (dt > 17) {
                    dt = 17;
                }
                lastUpdateTime = newTime;

                boolean isOnline = !user.self && !user.bot && (user.status != null && user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id));
                if (isOnline || onlineProgress != 0) {
                    int top = imageView.getBottom() - AndroidUtilities.dp(6);
                    int left = imageView.getRight() - AndroidUtilities.dp(10);
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    canvas.drawCircle(left, top, AndroidUtilities.dp(7) * onlineProgress, Theme.dialogs_onlineCirclePaint);
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
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
        Theme.checkboxSquare_checkPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
        Theme.checkboxSquare_checkPaint.setAlpha((int) (checkBox.getProgress() * 255));
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(28), Theme.checkboxSquare_checkPaint);
    }
}
