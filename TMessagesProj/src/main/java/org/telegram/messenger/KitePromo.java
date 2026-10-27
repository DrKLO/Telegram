package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * Keeps the Kite news channel in the first row of the chat list.
 * When the server has no promo dialog of its own, the channel is shown as the
 * promo dialog (with a Join button while the user is not a member), and after
 * joining it stays on top of the main chat list.
 */
public final class KitePromo {

    public static final String CHANNEL_USERNAME = "usekite";

    private static final String PREF_CHANNEL_DIALOG_ID = "kite_channel_dialog_id";

    // Remembered across restarts: the promo dialog is restored from the cache before
    // the channel is resolved again, and it must still be recognised as the Kite channel.
    private static volatile long channelDialogId = MessagesController.getGlobalMainSettings().getLong(PREF_CHANNEL_DIALOG_ID, 0);

    private static final boolean[] refreshed = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    /** Promo data built locally for the Kite channel, not received from the server. */
    static final class Data extends TLRPC.TL_help_promoData {
    }

    private KitePromo() {
    }

    /** The Kite channel's dialog id, 0 until it has been resolved once. */
    public static long getChannelDialogId() {
        return channelDialogId;
    }

    public static boolean isKiteDialog(long dialogId) {
        return dialogId != 0 && dialogId == channelDialogId;
    }

    /**
     * Re-checks the promo dialog once per app start, so the Kite channel comes back to the
     * first row even when an earlier check is still cached as "nothing to show".
     */
    public static void refreshOnce(int account) {
        if (refreshed[account]) {
            return;
        }
        refreshed[account] = true;
        MessagesController.getInstance(account).checkPromoInfo(true);
    }

    /**
     * Resolves the Kite channel and builds promo data for it.
     * Calls back with null if the channel can not be resolved.
     */
    static void load(int account, int expires, Utilities.Callback<TLObject> callback) {
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = CHANNEL_USERNAME;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                callback.run(null);
                return;
            }
            final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            if (!(resolved.peer instanceof TLRPC.TL_peerChannel)) {
                callback.run(null);
                return;
            }
            channelDialogId = -resolved.peer.channel_id;
            MessagesController.getGlobalMainSettings().edit().putLong(PREF_CHANNEL_DIALOG_ID, channelDialogId).apply();
            final Data promo = new Data();
            promo.peer = resolved.peer;
            promo.chats = resolved.chats;
            promo.users = resolved.users;
            promo.expires = expires;
            callback.run(promo);
        });
    }
}
