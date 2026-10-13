package org.telegram.ui.Wallet;

/** Associates a latched EGL buffer with the front-content revision it contains. */
final class WalletCardFrameHistory {
    private final long[] timestamps = new long[64];
    private final long[] revisions = new long[64];
    private int next;
    private int count;

    synchronized void clear() {
        next = count = 0;
    }

    synchronized void record(long timestamp, long revision) {
        timestamps[next] = timestamp;
        revisions[next] = revision;
        next = (next + 1) % timestamps.length;
        count = Math.min(count + 1, timestamps.length);
    }

    synchronized long revisionAt(long timestamp) {
        for (int i = 0; i < count; i++) {
            int index = (next - 1 - i + timestamps.length) % timestamps.length;
            if (timestamps[index] == timestamp) return revisions[index];
        }
        // Never acknowledge newer content just because GL has already drawn it.
        return 0;
    }
}
