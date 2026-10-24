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

    private static volatile long channelDialogId;

    /** Promo data built locally for the Kite channel, not received from the server. */
    static final class Data extends TLRPC.TL_help_promoData {
    }

    private KitePromo() {
    }

    public static boolean isKiteDialog(long dialogId) {
        return dialogId != 0 && dialogId == channelDialogId;
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
            final Data promo = new Data();
            promo.peer = resolved.peer;
            promo.chats = resolved.chats;
            promo.users = resolved.users;
            promo.expires = expires;
            callback.run(promo);
        });
    }
}
