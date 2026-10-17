package org.telegram.ui.Stars;

import android.content.Context;
import android.util.SparseArray;
import java.lang.ref.WeakReference;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

/** User-initiated changes share one native confirmation, including purchase accessibility actions. */
public final class StarsPrivacyConfirmation {
    private static final SparseArray<WeakReference<AlertDialog>> pending = new SparseArray<>();

    private StarsPrivacyConfirmation() {}

    public static AlertDialog request(Context context, int account, boolean enabled,
            Theme.ResourcesProvider resourcesProvider, Runnable done) {
        int key = account * 2;
        WeakReference<AlertDialog> reference = pending.get(key);
        AlertDialog existing = reference == null ? null : reference.get();
        if (!enabled) {
            if (existing != null) existing.dismiss();
            StarsPrivacy.setEnabled(account, false);
            if (done != null) done.run();
            return null;
        }
        return show(context, key, resourcesProvider, R.string.StarsPrivacyMakePrivate, R.string.StarsPrivacyEnableConfirm, () -> {
            StarsPrivacy.setEnabled(account, true);
            if (done != null) done.run();
        });
    }

    /** The editor callback changes its draft only; its native Save/Discard boundary remains intact. */
    public static AlertDialog channel(Context context, int account, Theme.ResourcesProvider resourcesProvider, Runnable accepted) {
        return show(context, account * 2 + 1, resourcesProvider, R.string.StarsChannelPrivate,
            R.string.StarsChannelEnableConfirm, accepted);
    }

    private static AlertDialog show(Context context, int key, Theme.ResourcesProvider resourcesProvider,
            int title, int message, Runnable accepted) {
        WeakReference<AlertDialog> reference = pending.get(key);
        AlertDialog existing = reference == null ? null : reference.get();
        if (existing != null && existing.isShowing()) return existing;
        AlertDialog dialog = new AlertDialog.Builder(context, resourcesProvider)
            .setTitle(LocaleController.getString(title))
            .setMessage(LocaleController.getString(message))
            .setPositiveButton(LocaleController.getString(R.string.StarsPrivacyConfirmAction), (d, which) -> accepted.run())
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create();
        pending.put(key, new WeakReference<>(dialog));
        dialog.setOnDismissListener(d -> {
            WeakReference<AlertDialog> current = pending.get(key);
            if (current != null && current.get() == dialog) pending.remove(key);
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.stars_privacy_confirm);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setId(R.id.stars_privacy_cancel);
        return dialog;
    }
}
