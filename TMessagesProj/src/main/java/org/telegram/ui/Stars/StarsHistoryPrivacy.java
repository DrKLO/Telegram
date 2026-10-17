package org.telegram.ui.Stars;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

/** Confirmation shared by settings, receipt sheets and transaction long-press. */
public final class StarsHistoryPrivacy {
    private StarsHistoryPrivacy() {}

    public static AlertDialog confirm(Context context, int account, TL_stars.StarsTransaction transaction, Theme.ResourcesProvider resourcesProvider, Runnable done) {
        AlertDialog dialog = new AlertDialog.Builder(context, resourcesProvider)
            .setTitle(LocaleController.getString(transaction == null ? R.string.StarsHistoryAnonymise : R.string.StarsReceiptAnonymise))
            .setMessage(LocaleController.getString(R.string.StarsHistoryAnonymiseConfirm))
            .setPositiveButton(LocaleController.getString(R.string.StarsAnonymise), (d, which) -> {
                if (transaction == null) StarsPrivacyMockApi.anonymiseHistory(account,
                    org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime());
                else StarsPrivacyMockApi.anonymise(account, transaction.id);
                if (done != null) done.run();
            })
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create();
        dialog.show();
        return dialog;
    }
}
