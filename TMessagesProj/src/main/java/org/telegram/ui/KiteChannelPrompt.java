package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Dialog;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.KiteStats;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/**
 * Asks on every app start whether the user wants to join the Kite news channel,
 * until they are a member. Joining happens only after the user taps "Subscribe".
 */
public final class KiteChannelPrompt {

    public static final String CHANNEL_USERNAME = "usekite";

    private static final long SHOW_DELAY_MS = 5000;

    // Accounts already asked since the app process started.
    private static final boolean[] askedThisLaunch = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private KiteChannelPrompt() {
    }

    public static void maybeShow(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        if (!UserConfig.getInstance(account).isClientActivated() || askedThisLaunch[account]) {
            return;
        }
        // Let the permission dialogs that DialogsActivity shows first go away.
        AndroidUtilities.runOnUIThread(() -> showIfNotMember(fragment), SHOW_DELAY_MS);
    }

    private static void showIfNotMember(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        if (askedThisLaunch[account]) {
            return;
        }
        MessagesController.getInstance(account).getUserNameResolver().resolve(CHANNEL_USERNAME, peerId -> {
            if (peerId != null && peerId != Long.MAX_VALUE && peerId < 0) {
                final TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-peerId);
                if (chat != null && !ChatObject.isNotInChat(chat)) {
                    return;
                }
            }
            show(fragment);
        });
    }

    private static final int MAX_SHOW_ATTEMPTS = 12;

    private static void show(BaseFragment fragment) {
        show(fragment, 0);
    }

    private static void show(BaseFragment fragment, int attempt) {
        final int account = fragment.getCurrentAccount();
        if (askedThisLaunch[account] || attempt >= MAX_SHOW_ATTEMPTS) {
            return;
        }
        final BaseFragment host = chatListHost(fragment);
        Dialog shown = null;
        if (host != null && host.getVisibleDialog() == null) {
            shown = host.showDialog(new AlertDialog.Builder(host.getParentActivity())
                .setTitle(getString(R.string.KiteChannelPromptTitle))
                .setMessage(getString(R.string.KiteChannelPromptText))
                .setPositiveButton(getString(R.string.KiteChannelPromptSubscribe), (dialog, which) -> join(host, account))
                .setNegativeButton(getString(R.string.KiteChannelPromptNotNow), null)
                .create());
        }
        if (shown != null) {
            askedThisLaunch[account] = true;
        } else {
            // Another dialog, a transition or another screen is in the way: try again later.
            AndroidUtilities.runOnUIThread(() -> show(fragment, attempt + 1), SHOW_DELAY_MS);
        }
    }

    /**
     * The fragment that should host Kite dialogs while the chat list is on screen.
     * Since 12.x the chat list lives inside MainTabsActivity, which is then the top fragment.
     */
    static BaseFragment chatListHost(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return null;
        }
        final BaseFragment top = LaunchActivity.getSafeLastFragment();
        if (top == fragment || top instanceof MainTabsActivity) {
            return top.getParentActivity() != null ? top : null;
        }
        return null;
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
            controller.addUserToChat(chat.id, UserConfig.getInstance(account).getCurrentUser(), 0, null, fragment, () -> {
                showResult(fragment, R.raw.contact_check, getString(R.string.KiteChannelPromptDone));
                KiteStats.reportJoin();
            });
        });
    }

    private static void showResult(BaseFragment fragment, int iconRawId, String text) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(iconRawId, text).show();
    }
}
