/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.GroupCreateCheckBox;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerUserCell extends FrameLayout {

    private TextView textView;
    private BackupImageView imageView;
    private AvatarDrawable avatarDrawable;
    private GroupCreateCheckBox checkBox;

    private int accountNumber;
    private RectF rect = new RectF();

    public DrawerUserCell(Context context) {
        super(context);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(18));
        addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 14, 6, 0, 0));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 72, 0, 60, 0));

        checkBox = new GroupCreateCheckBox(context);
        checkBox.setChecked(true, false);
        checkBox.setCheckScale(0.9f);
        checkBox.setInnerRadDiff(AndroidUtilities.dp(1.5f));
        checkBox.setColorKeysOverrides(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter, Theme.key_chats_menuBackground);
        addView(checkBox, LayoutHelper.createFrame(18, 18, Gravity.LEFT | Gravity.TOP, 37, 27, 0, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
    }

    public void setAccount(int account) {
        accountNumber = account;
        TLRPC.User user = UserConfig.getInstance(accountNumber).getCurrentUser();
        if (user == null) {
            return;
        }
        avatarDrawable.setInfo(user);
        textView.setText(ContactsController.formatName(user.first_name, user.last_name));
        TLRPC.FileLocation avatar;
        if (user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
            avatar = user.photo.photo_small;
        } else {
            avatar = null;
        }
        imageView.getImageReceiver().setCurrentAccount(account);
        imageView.setImage(avatar, "50_50", avatarDrawable);
        checkBox.setVisibility(account == UserConfig.selectedAccount ? VISIBLE : INVISIBLE);
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (UserConfig.getActivatedAccountsCount() <= 1 || !NotificationsController.getInstance(accountNumber).showBadgeNumber) {
            return;
        }
        int counter = NotificationsController.getInstance(accountNumber).getTotalUnreadCount();
        if (counter <= 0) {
            return;
        }

        String text = String.format("%d", counter);
        int countTop = AndroidUtilities.dp(12.5f);
        int textWidth = (int) Math.ceil(Theme.chat_livePaint.measureText(text));
        int countWidth = Math.max(AndroidUtilities.dp(12), textWidth);
        int countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(25);

        int x = countLeft - AndroidUtilities.dp(5.5f);
        rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
        canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_countPaint);

        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2 - AndroidUtilities.dp(0.5f), countTop + AndroidUtilities.dp(16), Theme.dialogs_countTextPaint);
    }
}
