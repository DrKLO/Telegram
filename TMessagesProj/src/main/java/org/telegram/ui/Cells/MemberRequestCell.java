package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.LongSparseArray;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class MemberRequestCell extends FrameLayout {

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final BackupImageView avatarImageView = new BackupImageView(getContext());
    private final SimpleTextView nameTextView = new SimpleTextView(getContext());
    private final SimpleTextView statusTextView = new SimpleTextView(getContext());

    private TLRPC.TL_chatInviteImporter importer;
    private boolean isNeedDivider;

    public MemberRequestCell(@NonNull Context context, OnClickListener clickListener, boolean isChannel) {
        super(context);

        avatarImageView.setRoundRadius(AndroidUtilities.dp(23));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 12, 8, 12, 0));

        nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        nameTextView.setMaxLines(1);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(17);
        nameTextView.setTypeface(AndroidUtilities.bold());
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, LocaleController.isRTL ? 12 : 74, 12, LocaleController.isRTL ? 74 : 12, 0));

        statusTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        statusTextView.setMaxLines(1);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusTextView.setTextSize(14);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, LocaleController.isRTL ? 12 : 74, 36, LocaleController.isRTL ? 74 : 12, 0));

        int btnPadding = AndroidUtilities.dp(17);
        TextView addButton = new TextView(getContext());
        addButton.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
        addButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addButton.setMaxLines(1);
        addButton.setPadding(btnPadding, 0, btnPadding, 0);
        addButton.setText(isChannel ? LocaleController.getString(R.string.AddToChannel) : LocaleController.getString(R.string.AddToGroup));
        addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        addButton.setTextSize(14);
        addButton.setTypeface(AndroidUtilities.bold());
        addButton.setOnClickListener(v -> {
            if (clickListener != null && importer != null) {
                clickListener.onAddClicked(importer);
            }
        });
        addView(addButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 0 : 73, 62, LocaleController.isRTL ? 73 : 0, 0));

        float addButtonWidth = addButton.getPaint().measureText(addButton.getText().toString()) + btnPadding * 2;
        TextView dismissButton = new TextView(getContext());
        dismissButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector), 0xff000000));
        dismissButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        dismissButton.setMaxLines(1);
        dismissButton.setPadding(btnPadding, 0, btnPadding, 0);
        dismissButton.setText(LocaleController.getString(R.string.Dismiss));
        dismissButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        dismissButton.setTextSize(14);
        dismissButton.setTypeface(AndroidUtilities.bold());
        dismissButton.setOnClickListener(v -> {
            if (clickListener != null && importer != null) {
                clickListener.onDismissClicked(importer);
            }
        });
        FrameLayout.LayoutParams dismissLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(32), LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        dismissLayoutParams.topMargin = AndroidUtilities.dp(62);
        dismissLayoutParams.leftMargin = LocaleController.isRTL ? 0 : (int)(addButtonWidth + AndroidUtilities.dp(73 + 6));
        dismissLayoutParams.rightMargin = LocaleController.isRTL ? (int)(addButtonWidth + AndroidUtilities.dp(73 + 6)) : 0;
        addView(dismissButton, dismissLayoutParams);
    }

    public void setData(LongSparseArray<TLRPC.User> users, TLRPC.TL_chatInviteImporter importer, boolean isNeedDivider) {
        this.importer = importer;
        this.isNeedDivider = isNeedDivider;
        setWillNotDraw(!isNeedDivider);

        TLRPC.User user = users.get(importer.user_id);
        avatarDrawable.setInfo(user);
        avatarImageView.setForUserOrChat(user, avatarDrawable);
        nameTextView.setText(UserObject.getUserName(user));
        String dateText = LocaleController.formatDateAudio(importer.date, false);
        if (importer.via_chatlist) {
            statusTextView.setText(LocaleController.getString(R.string.JoinedViaFolder));
        } else if (importer.approved_by == 0) {
            statusTextView.setText(LocaleController.formatString("RequestedToJoinAt", R.string.RequestedToJoinAt, dateText));
        } else {
            TLRPC.User approvedByUser = users.get(importer.approved_by);
            if (approvedByUser != null) {
                statusTextView.setText(LocaleController.formatString("AddedBy", R.string.AddedBy, UserObject.getFirstName(approvedByUser), dateText));
            } else {
                statusTextView.setText("");
            }
        }
    }

    public TLRPC.TL_chatInviteImporter getImporter() {
        return importer;
    }

    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }

    public String getStatus() {
        return statusTextView.getText().toString();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(107), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isNeedDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(72), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(72) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }


    public interface OnClickListener {

        void onAddClicked(TLRPC.TL_chatInviteImporter importer);

        void onDismissClicked(TLRPC.TL_chatInviteImporter importer);
    }
}
