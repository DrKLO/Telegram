package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/**
 * Asks once per account whether the user wants to join the Kite news channel.
 * Joining happens only after the user taps "Subscribe".
 */
public final class KiteChannelPrompt {

    public static final String CHANNEL_USERNAME = "usekite";

    private static final String PREF_SHOWN = "kite_channel_prompt_shown";
    private static final long SHOW_DELAY_MS = 5000;

    private KiteChannelPrompt() {
    }

    public static void maybeShow(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        if (!UserConfig.getInstance(account).isClientActivated() || wasShown(account)) {
            return;
        }
        // Let the permission dialogs that DialogsActivity shows first go away.
        AndroidUtilities.runOnUIThread(() -> show(fragment), SHOW_DELAY_MS);
    }

    private static boolean wasShown(int account) {
        return MessagesController.getMainSettings(account).getBoolean(PREF_SHOWN, false);
    }

    private static void show(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        if (fragment.getParentActivity() == null || fragment.getVisibleDialog() != null
                || LaunchActivity.getSafeLastFragment() != fragment || wasShown(account)) {
            return;
        }
        final SharedPreferences prefs = MessagesController.getMainSettings(account);
        prefs.edit().putBoolean(PREF_SHOWN, true).apply();

        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle(getString(R.string.KiteChannelPromptTitle))
            .setMessage(getString(R.string.KiteChannelPromptText))
            .setPositiveButton(getString(R.string.KiteChannelPromptSubscribe), (dialog, which) -> join(fragment, account))
            .setNegativeButton(getString(R.string.KiteChannelPromptNotNow), null)
            .create());
    }

    private static void join(BaseFragment fragment, int account) {
        final MessagesController controller = MessagesController.getInstance(account);
        controller.getUserNameResolver().resolve(CHANNEL_USERNAME, peerId -> {
            if (peerId == null || peerId == Long.MAX_VALUE || peerId >= 0) {
                showResult(fragment, R.raw.ic_ban, getString(R.string.KiteChannelPromptFailed));
                return;
            }
            final TLRPC.Chat chat = controller.getChat(-peerId);
            if (chat == null) {
                showResult(fragment, R.raw.ic_ban, getString(R.string.KiteChannelPromptFailed));
                return;
            }
            if (!ChatObject.isNotInChat(chat)) {
                showResult(fragment, R.raw.contact_check, getString(R.string.KiteChannelPromptDone));
                return;
            }
            controller.addUserToChat(chat.id, UserConfig.getInstance(account).getCurrentUser(), 0, null, fragment,
                () -> showResult(fragment, R.raw.contact_check, getString(R.string.KiteChannelPromptDone)));
        });
    }

    private static void showResult(BaseFragment fragment, int iconRawId, String text) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(iconRawId, text).show();
    }
}
