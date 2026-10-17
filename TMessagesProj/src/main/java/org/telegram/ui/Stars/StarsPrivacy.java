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
        // Persist the boundary before publishing the new default. Unloaded history pages
        // must be protected too, not only the rows that happened to be on screen.
        if (isEnabled(account) && !enabled) StarsPrivacyMockApi.sealPrivateActivity(account);
        MessagesController.getMainSettings(account).edit().putBoolean(KEY, enabled).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.starsPrivacyChanged);
    }

    public static boolean isPrivate(int account, long dialogId) {
        return isEnabled(account) || StarsPrivacyMockApi.isChannelPrivate(account, dialogId);
    }

    public static boolean usesMockSend(int account, org.telegram.messenger.MessageObject message) {
        if (message == null) return false;
        observeReaction(account, message);
        StarsController.MessageId id = StarsController.MessageId.from(message);
        return StarsPrivacyMockApi.isChannelPrivate(account, id.did)
            || !isEnabled(account) && StarsPrivacyMockApi.isProtectedPost(account, id.did, id.mid);
    }

    public static long resolvePeer(int account, long dialogId, long requestedPeer) {
        return isPrivate(account, dialogId) ? UserObject.ANONYMOUS : requestedPeer;
    }

    public static long resolvePeer(int account, long requestedPeer) {
        return isEnabled(account) ? UserObject.ANONYMOUS : requestedPeer;
    }

    public static boolean showSelectedState(int account, org.telegram.messenger.MessageObject message, TLRPC.ReactionCount reaction) {
        if (!(reaction.reaction instanceof TLRPC.TL_reactionPaid)) return reaction.chosen;
        if (message == null) return showSelectedState(account, reaction);
        StarsController.MessageId id = StarsController.MessageId.from(message);
        observeReaction(account, message);
        if (isEnabled(account) || !reaction.chosen) return false;
        if (StarsPrivacyMockApi.isChannelPrivate(account, id.did)) return StarsPrivacyMockApi.showHighlight(account, id.did, id.mid);
        return !StarsPrivacyMockApi.isProtectedPost(account, id.did, id.mid)
            || StarsPrivacyMockApi.publicContribution(account, id.did, id.mid) > 0;
    }

    /** Capture privacy before rebinding views or changing a send-as identity. */
    public static void observeReaction(int account, org.telegram.messenger.MessageObject message) {
        if (message == null || !message.isPaidReactionChosen()) return;
        StarsController.MessageId id = StarsController.MessageId.from(message);
        Long peer = message.getMyPaidReactionPeer();
        if (isPrivate(account, id.did) || peer != null && peer == UserObject.ANONYMOUS) {
            StarsPrivacyMockApi.protectPost(account, id.did, id.mid);
        }
    }

    public static boolean showSelectedState(int account, TLRPC.ReactionCount reaction) {
        return reaction.chosen && (!(reaction.reaction instanceof TLRPC.TL_reactionPaid) || !isEnabled(account));
    }
}
