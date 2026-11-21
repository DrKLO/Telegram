package org.telegram.messenger;

import android.os.SystemClock;

import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

// Download speed in bits per second, measured from how fast consecutive chunks of one file arrive.
// Until a fresh measurement exists, falls back to the player's estimate, which starts from the network type.
public class DownloadSpeedEstimator {

    private static final long MAX_AGE_MS = 60_000;

    private static double bitrate;
    private static long updatedAt;

    public static synchronized void onSample(long bits, long durationMs) {
        if (bits <= 0 || durationMs <= 0) {
            return;
        }
        double sample = bits * 1000.0 / durationMs;
        long now = SystemClock.elapsedRealtime();
        bitrate = updatedAt == 0 || now - updatedAt > MAX_AGE_MS ? sample : bitrate * 0.7 + sample * 0.3;
        updatedAt = now;
    }

    // Fast enough that preloading ahead and waiting for cancelled responses cost nothing noticeable.
    public static boolean isFast() {
        return getBitrate() >= 3_000_000;
    }

    public static synchronized long getBitrate() {
        if (updatedAt != 0 && SystemClock.elapsedRealtime() - updatedAt <= MAX_AGE_MS) {
            return (long) bitrate;
        }
        return DefaultBandwidthMeter.getSingletonInstance(ApplicationLoader.applicationContext).getBitrateEstimate();
    }
}
