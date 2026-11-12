package org.telegram.utils.twidget;

import android.os.SystemClock;

import java.nio.charset.StandardCharsets;

/**
 * The client's own check of what one session's engine asks for. The engine (WidgetHost's policy in widget-sandbox)
 * already lets through only what a tap, the server's flags and its limits allow; since it runs the widget's code in
 * the isolated process, a widget that escaped its sandbox would own that policy too. So the client keeps the parts
 * that reach the user or the bot by itself, each never stricter than the engine's (docs/sdk-v1.md §5):
 * <ul>
 * <li>links and copyText: one per press the client delivered to the visible widget, within {@link #PRESS_MS};
 * <li>requests: with `can_query`, one in flight; as a press's (consuming it) or, with `background_query` and no wallet,
 * at most one per {@link #BACKGROUND_MS}; wallet calls with `context.wallet`, as a press's or within {@link #PRESS_MS}
 * of the answer to a request with the wallet card that a press sent (the engine's grant);
 * <li>haptics: shown, at most {@link #HAPTICS_PER_SECOND}; the engine's sizes for links, texts and request data.
 * </ul>
 * Thread-safe: presses come from UI, the engine's calls from its threads.
 */
final class WidgetGate {
    // The engine's wallet grant (WALLET_GRANT_MS) is the longest a press may unlock anything.
    static final long PRESS_MS = 10_000;
    // The engine's own interval is 5 s, but a held request goes out when the widget is shown, right after another.
    static final long BACKGROUND_MS = 1_000;
    static final int HAPTICS_PER_SECOND = 10;
    // widget-sandbox's sdk_policy.h: MAX_LINK_LENGTH, MAX_COPY_TEXT_LENGTH (UTF-16), MAX_REQUEST_DATA_BYTES (UTF-8);
    // a storage commit is at most MAX_STORAGE_COMMIT_BYTES, a wallet call far below the cap here.
    static final int MAX_LINK = 256, MAX_COPY_TEXT = 256, MAX_REQUEST_DATA = 64, MAX_STORAGE_COMMIT = 2 * 12 * 4096 + 64,
            MAX_WALLET_CALL = 256 * 1024;
    private static final String[] TELEGRAM_HOSTS = {"t.me", "telegram.me", "telegram.dog"};

    private final boolean canQuery, backgroundQuery, wallet;
    private int presses, usedPress;
    private long lastPress = Long.MIN_VALUE / 2, lastBackground = Long.MIN_VALUE / 2, grantAt = Long.MIN_VALUE / 2;
    private boolean requestInFlight, granting;
    private final long[] haptics = new long[HAPTICS_PER_SECOND];
    private int nextHaptic;

    /** `context`: the session's context as the client made it (WidgetMedia#sdk), not what the engine says. */
    WidgetGate(org.json.JSONObject context) {
        boolean preview = context != null && context.optBoolean("preview");
        canQuery = context != null && !preview && context.optBoolean("canQuery");
        backgroundQuery = canQuery && context.optBoolean("backgroundQuery");
        wallet = canQuery && context.has("wallet") && !context.isNull("wallet");
    }

    /** UI: a press went to the widget while it was shown. */
    synchronized void press() {
        presses++;
        lastPress = SystemClock.elapsedRealtime();
    }

    private boolean pressAvailable(long now) {
        return usedPress < presses && now - lastPress <= PRESS_MS;
    }

    /** openLink, openTelegramLink, copyText: consumes the press. */
    synchronized boolean privileged(boolean visible) {
        long now = SystemClock.elapsedRealtime();
        if (!visible || !pressAvailable(now)) return false;
        usedPress = presses;
        return true;
    }

    /** A request to the bot (`wallet`: with the user's wallet card); true lets it out until {@link #answered}. */
    synchronized boolean request(String data, boolean withWallet, boolean visible) {
        if (!canQuery || requestInFlight || data == null || data.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_DATA
                || withWallet && !wallet) return false;
        long now = SystemClock.elapsedRealtime();
        if (visible && pressAvailable(now)) {
            usedPress = presses;
            granting = withWallet;
        } else {
            if (!backgroundQuery || withWallet || now - lastBackground < BACKGROUND_MS) return false;
            lastBackground = now;
        }
        requestInFlight = true;
        return true;
    }

    /** A wallet call: a press's, or within the grant of an answered request with the wallet card. */
    synchronized boolean walletCall(String call, boolean visible) {
        if (!wallet || requestInFlight || call == null || call.length() > MAX_WALLET_CALL || !visible) return false;
        long now = SystemClock.elapsedRealtime();
        if (pressAvailable(now)) usedPress = presses;
        else if (now - grantAt > PRESS_MS) return false;
        requestInFlight = true;
        return true;
    }

    /** The client answered the request or wallet call in flight (`answered`: with a result, not a failure). */
    synchronized void answered(boolean answered) {
        if (granting && answered) grantAt = SystemClock.elapsedRealtime();
        requestInFlight = granting = false;
    }

    synchronized boolean haptic(boolean visible) {
        long now = SystemClock.elapsedRealtime();
        if (!visible || now - haptics[nextHaptic] < 1000) return false;
        haptics[nextHaptic] = now;
        nextHaptic = (nextHaptic + 1) % haptics.length;
        return true;
    }

    static boolean copyText(String text) {
        return text != null && text.length() <= MAX_COPY_TEXT;
    }

    static boolean storageCommit(String commit) {
        return commit != null && commit.length() <= MAX_STORAGE_COMMIT;
    }

    /**
     * A link as the engine's checkLink lets it out: https, no user info, a lowercase ASCII host of valid labels, and
     * a Telegram host exactly for openTelegramLink.
     */
    static boolean link(String url, boolean telegram) {
        if (url == null || url.length() > MAX_LINK || url.length() < 9 || !url.regionMatches(true, 0, "https://", 0, 8)) return false;
        int end = 8;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        String authority = url.substring(8, end);
        if (authority.indexOf('@') >= 0) return false;
        int colon = authority.indexOf(':');
        String host = colon < 0 ? authority : authority.substring(0, colon);
        if (host.isEmpty() || host.length() > 253) return false;
        for (String label : host.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') return false;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-')) return false;
            }
        }
        boolean telegramHost = false;
        for (String t : TELEGRAM_HOSTS) telegramHost |= host.equals(t) || host.endsWith("." + t);
        return telegramHost == telegram;
    }
}
