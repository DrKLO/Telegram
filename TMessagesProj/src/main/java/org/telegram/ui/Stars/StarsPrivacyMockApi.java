package org.telegram.ui.Stars;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.tl.TL_stars;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Local simulation of proposed Telegram server capabilities. Never sends a payment.
 * Persisted policies/tombstones simulate server state across restarts; they do not erase
 * Telegram's real ledger, backups or other clients. See docs/STARS_PRIVACY.md.
 */
public final class StarsPrivacyMockApi {
    public static final long HIGHLIGHT_MS = 5 * 60_000L;
    private static final HashMap<String, Long> highlights = new HashMap<>();
    private static final HashMap<String, Integer> percentiles = new HashMap<>();

    private StarsPrivacyMockApi() {}

    private static SharedPreferences state(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("stars_privacy_mock_" + account, Context.MODE_PRIVATE);
    }

    private static void changed(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.starsPrivacyChanged);
    }

    public static boolean isChannelPrivate(int account, long dialogId) {
        return dialogId < 0 && state(account).getBoolean("channel_" + dialogId, false);
    }

    public static void setChannelPrivate(int account, long dialogId, boolean enabled) {
        if (dialogId >= 0) throw new IllegalArgumentException("A channel dialog ID must be negative");
        if (isChannelPrivate(account, dialogId) && !enabled) {
            SharedPreferences prefs = state(account);
            Set<String> publicIds = new HashSet<>(prefs.getStringSet("new_public_receipts", java.util.Collections.emptySet()));
            publicIds.removeIf(id -> prefs.getLong("receipt_channel_" + id, 0) == dialogId);
            SharedPreferences.Editor edit = prefs.edit().putLong("channel_cutoff_" + dialogId,
                Math.max(prefs.getLong("channel_cutoff_" + dialogId, -1), now(account)))
                .putStringSet("new_public_receipts", publicIds);
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith("public_post_" + dialogId + ":")) edit.remove(key);
            }
            edit.commit();
            highlights.keySet().removeIf(key -> key.startsWith(account + ":" + dialogId + ":"));
            percentiles.keySet().removeIf(key -> key.startsWith(account + ":" + dialogId + ":"));
        }
        state(account).edit().putBoolean("channel_" + dialogId, enabled).apply();
        changed(account);
    }

    public static boolean showsPercentile(int account, long dialogId) {
        return isChannelPrivate(account, dialogId) && state(account).getBoolean("percentile_" + dialogId, true);
    }

    public static void setShowsPercentile(int account, long dialogId, boolean enabled) {
        state(account).edit().putBoolean("percentile_" + dialogId, enabled).apply();
        changed(account);
    }

    /** Local-only subtype, never serialized to Telegram. Contains no hidden original record. */
    public static final class RedactedTransaction extends TL_stars.TL_starsTransaction {
        public RedactedTransaction(String id, int date, TL_stars.StarsAmount amount) {
            this.id = id;
            this.date = date;
            peer = new TL_stars.TL_starsTransactionPeerUnsupported();
            // Keep accounting information, without retaining the original record or amount object.
            this.amount = TL_stars.StarsAmount.ofStars(amount.amount);
            this.amount.nanos = amount.nanos;
        }
    }

    public static final class Receipt {
        public final String id = "preview-" + UUID.randomUUID();
        public final int date = (int) (System.currentTimeMillis() / 1000L);
        public final long sender = UserObject.ANONYMOUS;
        private final long stars;

        private Receipt(long stars) { this.stars = stars; }

        public TL_stars.StarsTransaction asTransaction() {
            return new RedactedTransaction(id, date, TL_stars.StarsAmount.ofStars(-stars));
        }
    }

    public static Receipt sendAnonymousLiveReaction(long stars) {
        if (stars <= 0) throw new IllegalArgumentException("A reaction must contain Stars");
        return new Receipt(stars);
    }

    public static Receipt sendChannelReaction(int account, long dialogId, int messageId, long stars) {
        if (!isChannelPrivate(account, dialogId)) throw new IllegalStateException("Private-channel mock policy required");
        Receipt receipt = sendAnonymousLiveReaction(stars);
        protectPost(account, dialogId, messageId);
        String key = key(account, dialogId, messageId);
        long deadline = SystemClock.elapsedRealtime() + HIGHLIGHT_MS;
        highlights.put(key, deadline);
        // Fixture distribution represents server aggregates, never reconstructed from top_reactors.
        percentiles.put(key, percentileBucket(stars, new long[] {5, 10, 15, 20, 25, 30, 40, 50, 75, 100}));
        AndroidUtilities.runOnUIThread(() -> {
            if (highlights.get(key) != null && highlights.get(key) == deadline) {
                highlights.remove(key);
                percentiles.remove(key);
                changed(account);
            }
        }, HIGHLIGHT_MS);
        changed(account);
        return receipt;
    }

    private static String key(int account, long dialogId, int messageId) {
        return account + ":" + dialogId + ":" + messageId;
    }

    private static long now(int account) {
        return org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime();
    }

    /** The simulation has no restore operation for previously private activity. */
    public static void sealPrivateActivity(int account) {
        SharedPreferences.Editor edit = state(account).edit()
            .putLong("private_through", Math.max(state(account).getLong("private_through", -1), now(account)))
            .remove("new_public_receipts");
        for (String key : state(account).getAll().keySet()) {
            if (key.startsWith("public_post_")) edit.remove(key);
        }
        edit.commit();
        highlights.keySet().removeIf(key -> key.startsWith(account + ":"));
        percentiles.keySet().removeIf(key -> key.startsWith(account + ":"));
    }

    public static void protectPost(int account, long dialogId, int messageId) {
        String key = "private_post_" + dialogId + ":" + messageId;
        if (!state(account).getBoolean(key, false)) state(account).edit().putBoolean(key, true).commit();
    }

    public static boolean isProtectedPost(int account, long dialogId, int messageId) {
        return state(account).getBoolean("private_post_" + dialogId + ":" + messageId, false);
    }

    public static long publicContribution(int account, long dialogId, int messageId) {
        return state(account).getLong("public_post_" + dialogId + ":" + messageId, 0);
    }

    /** Models per-contribution privacy, which the current per-post API cannot express. */
    public static TL_stars.StarsTransaction sendPublicReaction(int account, long dialogId, int messageId, long stars) {
        if (stars <= 0 || StarsPrivacy.isPrivate(account, dialogId)) throw new IllegalStateException("Public reaction unavailable");
        TL_stars.TL_starsTransaction receipt = new TL_stars.TL_starsTransaction();
        receipt.id = "preview-" + UUID.randomUUID();
        receipt.date = (int) now(account);
        receipt.amount = TL_stars.StarsAmount.ofStars(-stars);
        receipt.peer = new TL_stars.TL_starsTransactionPeer();
        receipt.peer.peer = new org.telegram.tgnet.TLRPC.TL_peerChannel();
        receipt.peer.peer.channel_id = -dialogId;
        receipt.reaction = true;
        receipt.msg_id = messageId;
        Set<String> ids = new HashSet<>(state(account).getStringSet("new_public_receipts", java.util.Collections.emptySet()));
        ids.add(receipt.id);
        state(account).edit().putStringSet("new_public_receipts", ids)
            .putLong("receipt_channel_" + receipt.id, dialogId)
            .putLong("public_post_" + dialogId + ":" + messageId, publicContribution(account, dialogId, messageId) + stars).commit();
        changed(account);
        return receipt;
    }

    public static boolean isHighlightActive(long now, long deadline) {
        return deadline > 0 && now < deadline && deadline - now <= HIGHLIGHT_MS;
    }

    public static boolean showHighlight(int account, long dialogId, int messageId) {
        Long deadline = highlights.get(key(account, dialogId, messageId));
        return deadline != null && isHighlightActive(SystemClock.elapsedRealtime(), deadline);
    }

    /** Coarse donor-only bucket; ties share rank. 0 means insufficient cohort, so show nothing. */
    public static int percentileBucket(long contribution, long[] cohort) {
        if (contribution <= 0 || cohort == null || cohort.length < 10) return 0;
        int greater = 0;
        for (long amount : cohort) if (amount > contribution) greater++;
        int percentile = (int) Math.ceil(100.0 * (greater + 1) / (cohort.length + 1));
        return percentile <= 10 ? 10 : percentile <= 25 ? 25 : percentile <= 50 ? 50 : 100;
    }

    public static int donorPercentile(int account, long dialogId, int messageId) {
        if (!showsPercentile(account, dialogId) || !showHighlight(account, dialogId, messageId)) return 0;
        Integer value = percentiles.get(key(account, dialogId, messageId));
        return value == null ? 0 : value;
    }

    public static boolean hasPrivateHistory(int account) {
        if (state(account).getLong("redacted_through", -1) >= 0
            || state(account).getLong("private_through", -1) >= 0
            || !state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()).isEmpty()) return true;
        for (java.util.Map.Entry<String, ?> entry : state(account).getAll().entrySet()) {
            if (entry.getKey().startsWith("channel_") && Boolean.TRUE.equals(entry.getValue())) return true;
            if (entry.getKey().startsWith("channel_cutoff_") && entry.getValue() instanceof Long && (Long) entry.getValue() >= 0) return true;
        }
        return false;
    }

    public static boolean isAnonymised(int account, TL_stars.StarsTransaction transaction) {
        return transaction instanceof RedactedTransaction
            || state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()).contains(transaction.id)
            || !state(account).getStringSet("new_public_receipts", java.util.Collections.emptySet()).contains(transaction.id)
                && (transaction.date <= state(account).getLong("redacted_through", -1)
                    || transaction.date <= state(account).getLong("private_through", -1));
    }

    /** No restore endpoint. Re-fetching the same ID is still redacted in this mock. */
    public static void anonymise(int account, String id) {
        Set<String> ids = new HashSet<>(state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()));
        ids.add(id);
        state(account).edit().putStringSet("redacted_ids", ids).commit();
        changed(account);
    }

    /** Covers unloaded pages as well as currently visible rows. Does not affect future receipts. */
    public static void anonymiseHistory(int account, long throughDate) {
        long previous = state(account).getLong("redacted_through", -1);
        state(account).edit().putLong("redacted_through", Math.max(previous, throughDate)).remove("new_public_receipts").commit();
        changed(account);
    }

    public static boolean shouldRedact(int account, TL_stars.StarsTransaction transaction) {
        if (transaction.amount instanceof TL_stars.TL_starsTonAmount) return false;
        if (isAnonymised(account, transaction)) return true;
        long dialogId = transaction.peer == null ? 0 : DialogObject.getPeerDialogId(transaction.peer.peer);
        return isChannelPrivate(account, dialogId)
            || !state(account).getStringSet("new_public_receipts", java.util.Collections.emptySet()).contains(transaction.id)
                && (transaction.date <= state(account).getLong("channel_cutoff_" + dialogId, -1)
                    || transaction.reaction && isProtectedPost(account, dialogId, transaction.msg_id));
    }

    public static TL_stars.StarsTransaction project(int account, TL_stars.StarsTransaction transaction) {
        return project(account, transaction, true);
    }

    public static TL_stars.StarsTransaction project(int account, TL_stars.StarsTransaction transaction, boolean personal) {
        if (transaction.amount instanceof TL_stars.TL_starsTonAmount) return transaction;
        if (!shouldRedact(account, transaction) && !(personal && StarsPrivacy.isEnabled(account))) return transaction;
        if (!isAnonymised(account, transaction)) {
            Set<String> ids = new HashSet<>(state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()));
            ids.add(transaction.id);
            state(account).edit().putStringSet("redacted_ids", ids).commit();
        }
        return new RedactedTransaction(transaction.id, transaction.date, transaction.amount);
    }
}
