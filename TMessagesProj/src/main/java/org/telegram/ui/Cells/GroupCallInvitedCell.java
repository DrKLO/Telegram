package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressWarnings("FieldCanBeLocal")
public class GroupCallInvitedCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private ImageView muteButton;

    private AvatarDrawable avatarDrawable;

    private TLRPC.User currentUser;

    private Paint dividerPaint;

    private int grayIconColor = Theme.key_voipgroup_mutedIcon;

    private boolean needDivider;

    public GroupCallInvitedCell(Context context) {
        super(context);

        dividerPaint = new Paint();
        dividerPaint.setColor(Theme.getColor(Theme.key_voipgroup_actionBar));

        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 11, 6, LocaleController.isRTL ? 11 : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(16);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 10, LocaleController.isRTL ? 67 : 54, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(15);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        statusTextView.setTextColor(Theme.getColor(grayIconColor));
        statusTextView.setText(LocaleController.getString("Invited", R.string.Invited));
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 54 : 67, 32, LocaleController.isRTL ? 67 : 54, 0));

        muteButton = new ImageView(context);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        muteButton.setImageResource(R.drawable.msg_invited);
        muteButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        muteButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        muteButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(grayIconColor), PorterDuff.Mode.MULTIPLY));
        addView(muteButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 6, 0, 6, 0));

        setWillNotDraw(false);

        setFocusable(true);
    }

    public CharSequence getName() {
        return nameTextView.getText();
    }

    public void setData(int account, Long uid) {
        currentUser = MessagesController.getInstance(account).getUser(uid);
        avatarDrawable.setInfo(currentUser);

        String lastName = UserObject.getUserName(currentUser);
        nameTextView.setText(lastName);

        avatarImageView.getImageReceiver().setCurrentAccount(account);
        avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
    }

    public void setDrawDivider(boolean draw) {
        needDivider = draw;
        invalidate();
    }

    public void setGrayIconColor(int key, int value) {
        grayIconColor = key;
        muteButton.setColorFilter(new PorterDuffColorFilter(value, PorterDuff.Mode.MULTIPLY));
        statusTextView.setTextColor(value);
        Theme.setSelectorDrawableColor(muteButton.getDrawable(), value & 0x24ffffff, true);
    }

    public TLRPC.User getUser() {
        return currentUser;
    }

    public boolean hasAvatarSet() {
        return avatarImageView.getImageReceiver().hasNotThumb();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(68) : 0), getMeasuredHeight() - 1, dividerPaint);
        }
        super.dispatchDraw(canvas);
    }
}
