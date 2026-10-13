package org.telegram.ui.multiaccount;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AvatarDrawable;

/**
 * Regular dialog row that can be rebound to any account and marks the owning account with a small
 * avatar badge on the bottom-left corner of the chat avatar (the online dot uses the right side).
 */
public class UnifiedDialogCell extends DialogCell {

    private final ImageReceiver accountImage = new ImageReceiver(this);
    private final AvatarDrawable accountAvatarDrawable = new AvatarDrawable();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean showAccountBadge;
    private int badgeAccount = -1;

    public UnifiedDialogCell(Context context, int account, Theme.ResourcesProvider resourcesProvider) {
        super(null, context, false, false, account, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        accountImage.setRoundRadius(dp(9));
        accountImage.setAllowLoadingOnAttachedOnly(true);
        accountAvatarDrawable.setTextSize(dp(9));
    }

    /** Binds the row to {@code account}; must precede {@code setDialog}. */
    public void bindAccount(int account, boolean showBadge) {
        setCurrentAccount(account);
        showAccountBadge = showBadge;
        if (showBadge && badgeAccount != account) {
            badgeAccount = account;
            TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
            accountAvatarDrawable.setInfo(account, self);
            accountImage.setCurrentAccount(account);
            accountImage.setForUserOrChat(self, accountAvatarDrawable);
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        accountImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        accountImage.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!showAccountBadge || avatarImage.getImageWidth() <= 0) {
            return;
        }
        float size = dp(18);
        float left = avatarImage.getImageX() - dp(2);
        float top = avatarImage.getImageY2() - size + dp(2);
        ringPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f + dp(1.5f), ringPaint);
        accountImage.setImageCoords(left, top, size, size);
        accountImage.draw(canvas);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (showAccountBadge && badgeAccount >= 0) {
            CharSequence text = info.getText();
            String label = LocaleController.formatString(R.string.MultiAccountAccountLabel, MultiAccountUi.getAccountName(badgeAccount));
            info.setText(text == null ? label : text + ", " + label);
        }
    }
}
