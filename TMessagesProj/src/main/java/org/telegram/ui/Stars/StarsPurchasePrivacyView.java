package org.telegram.ui.Stars;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

/** A short, actionable notice shared by both native Stars purchase sheets. */
public final class StarsPurchasePrivacyView extends TextInfoPrivacyCell implements NotificationCenter.NotificationCenterDelegate {
    private final int account;

    public StarsPurchasePrivacyView(Context context, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.account = account;
        setTag("stars_purchase_privacy");
        setTopPadding(8);
        setBottomPadding(12);
        update();
    }

    private void update() {
        setText(StarsPrivacy.isEnabled(account)
            ? LocaleController.getString(R.string.StarsPurchasePrivacyOn)
            : AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.StarsPurchasePrivacyOff),
                () -> StarsPrivacy.setEnabled(account, true)));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.starsPrivacyChanged);
        update();
    }

    @Override protected void onDetachedFromWindow() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.starsPrivacyChanged);
        super.onDetachedFromWindow();
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        update();
    }
}
