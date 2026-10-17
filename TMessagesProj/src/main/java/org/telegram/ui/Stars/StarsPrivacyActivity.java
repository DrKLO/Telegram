package org.telegram.ui.Stars;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** Personal defaults and irreversible history actions, using Telegram settings cells. */
public class StarsPrivacyActivity extends StarsPrivacySettingsActivity {
    public StarsPrivacyActivity(int account) {
        currentAccount = account;
    }

    @Override public View createView(Context context) {
        createSettingsView(context, R.string.StarsPrivacyTitle, false);
        addHeader(R.string.StarsPrivacyReactionsHeader);
        TextCheckCell toggle = addToggle("stars_privacy_toggle", R.string.StarsPrivacySwitch,
            StarsPrivacy.isEnabled(currentAccount));
        toggle.setOnClickListener(v -> {
            boolean enabled = !StarsPrivacy.isEnabled(currentAccount);
            setVisibleDialog(StarsPrivacyConfirmation.request(context, currentAccount, enabled, getResourceProvider(), () -> {
                toggle.setChecked(enabled);
                showFeedback(R.raw.passcode_lock_close,
                    enabled ? R.string.StarsPrivacyEnabledNotice : R.string.StarsPrivacyDisabledNotice);
            }));
        });
        addInfo(LocaleController.getString(R.string.StarsPrivacyDescription) + "\n\n"
            + LocaleController.getString(R.string.StarsPrivacyScope));

        addHeader(R.string.StarsPrivacyHistoryHeader);
        TextSettingsCell history = new TextSettingsCell(context, getResourceProvider());
        history.setTag("stars_history_anonymise");
        history.setTextAndValue(LocaleController.getString(R.string.StarsHistoryAnonymiseAction),
            LocaleController.getString(R.string.StarsPrivacyPreviewLabel), false);
        history.setOnClickListener(v -> setVisibleDialog(StarsHistoryPrivacy.confirm(context, currentAccount, null,
            getResourceProvider(), () -> showFeedback(R.raw.contact_check, R.string.StarsHistoryAnonymisedNotice))));
        content.addView(history, LayoutHelper.createLinear(-1, -2));
        addInfo(LocaleController.getString(R.string.StarsHistoryAnonymiseInfo));
        updateColors();
        return fragmentView;
    }
}
