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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;

public class CheckBoxUserCell extends FrameLayout {

    private TextView textView;
    private BackupImageView imageView;
    private CheckBoxSquare checkBox;
    private AvatarDrawable avatarDrawable;
    private boolean needDivider;

    private TLRPC.User currentUser;

    public CheckBoxUserCell(Context context, boolean alert) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(alert ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 17 : 94), 0, (LocaleController.isRTL ? 94 : 17), 0));

        avatarDrawable = new AvatarDrawable();
        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(36));
        addView(imageView, LayoutHelper.createFrame(36, 36, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 48, 6, 48, 0));

        checkBox = new CheckBoxSquare(context, alert);
        addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 17), 15, (LocaleController.isRTL ? 17 : 0), 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public TLRPC.User getCurrentUser() {
        return currentUser;
    }

    public void setUser(TLRPC.User user, boolean checked, boolean divider) {
        currentUser = user;
        textView.setText(ContactsController.formatName(user.first_name, user.last_name));
        checkBox.setChecked(checked, false);
        TLRPC.FileLocation photo = null;
        avatarDrawable.setInfo(user);
        if (user != null && user.photo != null) {
            photo = user.photo.photo_small;
        }
        imageView.setImage(photo, "50_50", avatarDrawable);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public TextView getTextView() {
        return textView;
    }

    public CheckBoxSquare getCheckBox() {
        return checkBox;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(getPaddingLeft(), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }
}
