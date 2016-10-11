/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class HintDialogCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();

    private static Drawable countDrawable;
    private static Drawable countDrawableGrey;
    private static TextPaint countPaint;

    private int lastUnreadCount;
    private int countWidth;
    private StaticLayout countLayout;

    private long dialog_id;

    public HintDialogCell(Context context) {
        super(context);
        setBackgroundResource(R.drawable.list_selector);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(27));
        addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xff212121);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(2);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(2);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 64, 6, 0));

        if (countDrawable == null) {
            countDrawable = getResources().getDrawable(R.drawable.dialogs_badge);
            countDrawableGrey = getResources().getDrawable(R.drawable.dialogs_badge2);

            countPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            countPaint.setColor(0xffffffff);
            countPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        }
        countPaint.setTextSize(AndroidUtilities.dp(13));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getBackground().setHotspot(event.getX(), event.getY());
            }
        }
        return super.onTouchEvent(event);
    }

    public void checkUnreadCounter(int mask) {
        if (mask != 0 && (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) == 0 && (mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) == 0) {
            return;
        }
        TLRPC.TL_dialog dialog = MessagesController.getInstance().dialogs_dict.get(dialog_id);
        if (dialog != null && dialog.unread_count != 0) {
            if (lastUnreadCount != dialog.unread_count) {
                lastUnreadCount = dialog.unread_count;
                String countString = String.format("%d", dialog.unread_count);
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(countPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, countPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                if (mask != 0) {
                    invalidate();
                }
            }
        } else if (countLayout != null) {
            if (mask != 0) {
                invalidate();
            }
            lastUnreadCount = 0;
            countLayout = null;
        }
    }

    public void setDialog(int uid, boolean counter, CharSequence name) {
        dialog_id = uid;
        TLRPC.FileLocation photo = null;
        if (uid > 0) {
            TLRPC.User user = MessagesController.getInstance().getUser(uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (user != null) {
                nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(user);
            if (user != null && user.photo != null) {
                photo = user.photo.photo_small;
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (chat != null) {
                nameTextView.setText(chat.title);
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(chat);
            if (chat != null && chat.photo != null) {
                photo = chat.photo.photo_small;
            }
        }
        imageView.setImage(photo, "50_50", avatarDrawable);
        if (counter) {
            checkUnreadCounter(0);
        } else {
            countLayout = null;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView) {
            if (countLayout != null) {
                int top = AndroidUtilities.dp(6);
                int left = AndroidUtilities.dp(54);
                int x = left - AndroidUtilities.dp(5.5f);
                if (MessagesController.getInstance().isDialogMuted(dialog_id)) {
                    countDrawableGrey.setBounds(x, top, x + countWidth + AndroidUtilities.dp(11), top + countDrawableGrey.getIntrinsicHeight());
                    countDrawableGrey.draw(canvas);
                } else {
                    countDrawable.setBounds(x, top, x + countWidth + AndroidUtilities.dp(11), top + countDrawable.getIntrinsicHeight());
                    countDrawable.draw(canvas);
                }
                canvas.save();
                canvas.translate(left, top + AndroidUtilities.dp(4));
                countLayout.draw(canvas);
                canvas.restore();
            }
        }
        return result;
    }
}
