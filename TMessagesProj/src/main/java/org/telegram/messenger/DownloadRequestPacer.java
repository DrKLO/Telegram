package org.telegram.messenger;

/** Per-file dispatch spacing used by TDLib's FileDownloader/DelayDispatcher. */
final class DownloadRequestPacer {
    private double intervalMs = 50;
    private long nextRequestAt;

    long remainingDelay(long now) {
        return Math.max(0, nextRequestAt - now);
    }

    void onRequestSent(long now) {
        // Round up because DispatchQueue schedules in whole milliseconds.
        nextRequestAt = now + (long) Math.ceil(intervalMs);
        intervalMs = Math.max(3, intervalMs * 0.8);
    }
}
