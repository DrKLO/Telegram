package org.telegram.ui.Stars;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

/** Personal, account-scoped client preference. This does not delete server records. */
public final class StarsPrivacy {
    private static final String KEY = "privateStarReactions";

    private StarsPrivacy() {}

    public static boolean isEnabled(int account) {
        return MessagesController.getMainSettings(account).getBoolean(KEY, true);
    }

    public static void setEnabled(int account, boolean enabled) {
        MessagesController.getMainSettings(account).edit().putBoolean(KEY, enabled).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.starsPrivacyChanged);
    }

    public static boolean isPrivate(int account, long dialogId) {
        return isEnabled(account) || StarsPrivacyMockApi.isChannelPrivate(account, dialogId);
    }

    public static long resolvePeer(int account, long dialogId, long requestedPeer) {
        return isPrivate(account, dialogId) ? UserObject.ANONYMOUS : requestedPeer;
    }

    public static long resolvePeer(int account, long requestedPeer) {
        return isEnabled(account) ? UserObject.ANONYMOUS : requestedPeer;
    }

    public static boolean showSelectedState(int account, org.telegram.messenger.MessageObject message, TLRPC.ReactionCount reaction) {
        if (!showSelectedState(account, reaction)) return false;
        if (!(reaction.reaction instanceof TLRPC.TL_reactionPaid) || message == null) return reaction.chosen;
        StarsController.MessageId id = StarsController.MessageId.from(message);
        return !StarsPrivacyMockApi.isChannelPrivate(account, id.did) || StarsPrivacyMockApi.showHighlight(account, id.did, id.mid);
    }

    public static boolean showSelectedState(int account, TLRPC.ReactionCount reaction) {
        return reaction.chosen && (!(reaction.reaction instanceof TLRPC.TL_reactionPaid) || !isEnabled(account));
    }
}
