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
        public RedactedTransaction(String id, int date) {
            this.id = id;
            this.date = date;
            peer = new TL_stars.TL_starsTransactionPeerUnsupported();
            amount = TL_stars.StarsAmount.ofStars(0);
        }
    }

    public static final class Receipt {
        public final String id = "preview-" + UUID.randomUUID();
        public final int date = (int) (System.currentTimeMillis() / 1000L);
        public final long sender = UserObject.ANONYMOUS;

        public TL_stars.StarsTransaction asTransaction() { return new RedactedTransaction(id, date); }
    }

    public static Receipt sendAnonymousLiveReaction(long stars) {
        if (stars <= 0) throw new IllegalArgumentException("A reaction must contain Stars");
        return new Receipt();
    }

    public static Receipt sendChannelReaction(int account, long dialogId, int messageId, long stars) {
        if (!isChannelPrivate(account, dialogId)) throw new IllegalStateException("Private-channel mock policy required");
        Receipt receipt = sendAnonymousLiveReaction(stars);
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
            || !state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()).isEmpty()) return true;
        for (java.util.Map.Entry<String, ?> entry : state(account).getAll().entrySet()) {
            if (entry.getKey().startsWith("channel_") && Boolean.TRUE.equals(entry.getValue())) return true;
        }
        return false;
    }

    public static boolean isAnonymised(int account, TL_stars.StarsTransaction transaction) {
        return transaction instanceof RedactedTransaction
            || state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()).contains(transaction.id)
            || transaction.date <= state(account).getLong("redacted_through", -1);
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
        state(account).edit().putLong("redacted_through", Math.max(previous, throughDate)).commit();
        changed(account);
    }

    public static boolean shouldRedact(int account, TL_stars.StarsTransaction transaction) {
        if (transaction.amount instanceof TL_stars.TL_starsTonAmount) return false;
        if (isAnonymised(account, transaction)) return true;
        long dialogId = transaction.peer == null ? 0 : DialogObject.getPeerDialogId(transaction.peer.peer);
        return isChannelPrivate(account, dialogId);
    }

    public static TL_stars.StarsTransaction project(int account, TL_stars.StarsTransaction transaction) {
        if (!shouldRedact(account, transaction)) return transaction;
        if (!isAnonymised(account, transaction)) {
            Set<String> ids = new HashSet<>(state(account).getStringSet("redacted_ids", java.util.Collections.emptySet()));
            ids.add(transaction.id);
            state(account).edit().putStringSet("redacted_ids", ids).commit();
        }
        return new RedactedTransaction(transaction.id, transaction.date);
    }
}
