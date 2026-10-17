package org.telegram.ui.Stars;

import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

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
        getTextView().setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        setTopPadding(8);
        setBottomPadding(12);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        update();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (!StarsPrivacy.isEnabled(account)) {
            info.setClickable(true);
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,
                LocaleController.getString(R.string.StarsPrivacyMakePrivate)));
        }
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK && !StarsPrivacy.isEnabled(account)) {
            StarsPrivacy.setEnabled(account, true);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
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
