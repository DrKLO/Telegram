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
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CounterView;
import org.telegram.ui.Components.LayoutHelper;

public class HintDialogCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private RectF rect = new RectF();

    private int lastUnreadCount;
    private TLRPC.User currentUser;

    private long dialog_id;
    private int currentAccount = UserConfig.selectedAccount;
    float showOnlineProgress;
    boolean wasDraw;

    CounterView counterView;

    public HintDialogCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(27));
        addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(1);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(1);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 64, 6, 0));

        counterView = new CounterView(context);
        addView(counterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, Gravity.TOP,0 ,4,0,0));
        counterView.setColors(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter);
        counterView.setGravity(Gravity.RIGHT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY));
        counterView.horizontalPadding = AndroidUtilities.dp(13);
    }

    public void update(int mask) {
        if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
            if (currentUser != null) {
                currentUser = MessagesController.getInstance(currentAccount).getUser(currentUser.id);
                imageView.invalidate();
                invalidate();
            }
        }
        if (mask != 0 && (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) == 0 && (mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) == 0) {
            return;
        }
        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialog_id);
        if (dialog != null && dialog.unread_count != 0) {
            if (lastUnreadCount != dialog.unread_count) {
                lastUnreadCount = dialog.unread_count;
                counterView.setCount(lastUnreadCount, wasDraw);
            }
        } else {
            lastUnreadCount = 0;
            counterView.setCount(0, wasDraw);
        }
    }

    public void update() {
        int uid = (int) dialog_id;
        TLRPC.FileLocation photo = null;
        if (uid > 0) {
            currentUser = MessagesController.getInstance(currentAccount).getUser(uid);
            avatarDrawable.setInfo(currentUser);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
            avatarDrawable.setInfo(chat);
            currentUser = null;
        }
    }

    public void setDialog(int uid, boolean counter, CharSequence name) {
        if (dialog_id != uid) {
            wasDraw = false;
            invalidate();
        }
        dialog_id = uid;
        if (uid > 0) {
            currentUser = MessagesController.getInstance(currentAccount).getUser(uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (currentUser != null) {
                nameTextView.setText(UserObject.getFirstName(currentUser));
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(currentUser);
            imageView.setImage(ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_STRIPPED), "50_50", avatarDrawable, currentUser);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (chat != null) {
                nameTextView.setText(chat.title);
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(chat);
            currentUser = null;
            imageView.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_STRIPPED), "50_50", avatarDrawable, chat);
        }
        if (counter) {
            update(0);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView) {
            boolean showOnline = currentUser != null && !currentUser.bot && (currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id));
            if (!wasDraw) {
                showOnlineProgress = showOnline ? 1f : 0f;
            }
            if (showOnline && showOnlineProgress != 1f) {
                showOnlineProgress += 16f / 150;
                if (showOnlineProgress > 1) {
                    showOnlineProgress = 1f;
                }
                invalidate();
            } else if (!showOnline && showOnlineProgress != 0) {
                showOnlineProgress -= 16f / 150;
                if (showOnlineProgress < 0) {
                    showOnlineProgress = 0;
                }
                invalidate();
            }
            if (showOnlineProgress != 0) {
                int top = AndroidUtilities.dp(53);
                int left = AndroidUtilities.dp(59);
                canvas.save();
                canvas.scale(showOnlineProgress, showOnlineProgress, left, top);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawCircle(left, top, AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(left, top, AndroidUtilities.dp(5), Theme.dialogs_onlineCirclePaint);
                canvas.restore();
            }
            wasDraw = true;
        }
        return result;
    }
}
