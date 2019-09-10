package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class ThemePreviewMessagesCell extends LinearLayout {

    private Drawable backgroundDrawable;
    private Drawable oldBackgroundDrawable;
    private ChatMessageCell[] cells = new ChatMessageCell[2];
    private Drawable shadowDrawable;
    private ActionBarLayout parentLayout;

    public ThemePreviewMessagesCell(Context context, ActionBarLayout layout, int type) {
        super(context);

        parentLayout = layout;

        setWillNotDraw(false);
        setOrientation(LinearLayout.VERTICAL);
        setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));

        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);

        int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;
        TLRPC.Message message = new TLRPC.TL_message();
        if (type == 0) {
            message.message = LocaleController.getString("FontSizePreviewReply", R.string.FontSizePreviewReply);
        } else {
            message.message = LocaleController.getString("NewThemePreviewReply", R.string.NewThemePreviewReply);
        }
        message.date = date + 60;
        message.dialog_id = 1;
        message.flags = 259;
        message.from_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        message.id = 1;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = true;
        message.to_id = new TLRPC.TL_peerUser();
        message.to_id.user_id = 0;
        MessageObject replyMessageObject = new MessageObject(UserConfig.selectedAccount, message, true);

        message = new TLRPC.TL_message();
        if (type == 0) {
            message.message = LocaleController.getString("FontSizePreviewLine2", R.string.FontSizePreviewLine2);
        } else {
            message.message = LocaleController.getString("NewThemePreviewLine2", R.string.NewThemePreviewLine2);
        }
        message.date = date + 960;
        message.dialog_id = 1;
        message.flags = 259;
        message.from_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        message.id = 1;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = true;
        message.to_id = new TLRPC.TL_peerUser();
        message.to_id.user_id = 0;
        MessageObject message1 = new MessageObject(UserConfig.selectedAccount, message, true);
        message1.resetLayout();
        message1.eventId = 1;

        message = new TLRPC.TL_message();
        if (type == 0) {
            message.message = LocaleController.getString("FontSizePreviewLine1", R.string.FontSizePreviewLine1);
        } else {
            message.message = LocaleController.getString("NewThemePreviewLine1", R.string.NewThemePreviewLine1);
        }
        message.date = date + 60;
        message.dialog_id = 1;
        message.flags = 257 + 8;
        message.from_id = 0;
        message.id = 1;
        message.reply_to_msg_id = 5;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = false;
        message.to_id = new TLRPC.TL_peerUser();
        message.to_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        MessageObject message2 = new MessageObject(UserConfig.selectedAccount, message, true);
        if (type == 0) {
            message2.customReplyName = LocaleController.getString("FontSizePreviewName", R.string.FontSizePreviewName);
        } else {
            message2.customReplyName = LocaleController.getString("NewThemePreviewName", R.string.NewThemePreviewName);
        }
        message2.eventId = 1;
        message2.resetLayout();
        message2.replyMessageObject = replyMessageObject;

        for (int a = 0; a < cells.length; a++) {
            cells[a] = new ChatMessageCell(context);
            cells[a].setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

            });
            cells[a].isChat = false;
            cells[a].setFullyDraw(true);
            cells[a].setMessageObject(a == 0 ? message2 : message1, null, false, false);
            addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    public ChatMessageCell[] getCells() {
        return cells;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < cells.length; a++) {
            cells[a].invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
        if (newDrawable != backgroundDrawable && newDrawable != null) {
            if (Theme.isAnimatingColor()) {
                oldBackgroundDrawable = backgroundDrawable;
            }
            backgroundDrawable = newDrawable;
        }
        float themeAnimationValue = parentLayout.getThemeAnimationValue();
        for (int a = 0; a < 2; a++) {
            Drawable drawable = a == 0 ? oldBackgroundDrawable : backgroundDrawable;
            if (drawable == null) {
                continue;
            }
            if (a == 1 && oldBackgroundDrawable != null && parentLayout != null) {
                drawable.setAlpha((int) (255 * themeAnimationValue));
            } else {
                drawable.setAlpha(255);
            }
            if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable) {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                drawable.draw(canvas);
            } else if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                    drawable.draw(canvas);
                    canvas.restore();
                } else {
                    int viewHeight = getMeasuredHeight();
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) drawable.getIntrinsicHeight();
                    float scale = scaleX < scaleY ? scaleY : scaleX;
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (viewHeight - height) / 2;
                    canvas.save();
                    canvas.clipRect(0, 0, width, getMeasuredHeight());
                    drawable.setBounds(x, y, x + width, y + height);
                    drawable.draw(canvas);
                    canvas.restore();
                }
            }
            if (a == 0 && oldBackgroundDrawable != null && themeAnimationValue >= 1.0f) {
                oldBackgroundDrawable = null;
            }
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
