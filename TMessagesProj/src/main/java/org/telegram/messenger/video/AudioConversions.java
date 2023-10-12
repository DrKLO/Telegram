package org.telegram.messenger.video;

public class AudioConversions {

    private static final int BYTES_PER_SAMPLE_PER_CHANNEL = 2; // Assuming 16bit audio, so 2
    private static final long MICROSECONDS_PER_SECOND = 1000000L;
    private static final int BYTES_PER_SHORT = 2;

    @SuppressWarnings("WeakerAccess")
    public static long bytesToUs(
            int bytes /* bytes */,
            int sampleRate /* samples/sec */,
            int channels /* channel */
    ) {
        int byteRatePerChannel = sampleRate * BYTES_PER_SAMPLE_PER_CHANNEL; // bytes/sec/channel
        int byteRate = byteRatePerChannel * channels; // bytes/sec
        return MICROSECONDS_PER_SECOND * bytes / byteRate; // usec
    }

    @SuppressWarnings("WeakerAccess")
    public static int usToBytes(long us, int sampleRate, int channels) {
        int byteRatePerChannel = sampleRate * BYTES_PER_SAMPLE_PER_CHANNEL;
        int byteRate = byteRatePerChannel * channels;
        return (int) Math.ceil((double) us * byteRate / MICROSECONDS_PER_SECOND);
    }

    public static long shortsToUs(int shorts, int sampleRate, int channels) {
        return bytesToUs(shorts * BYTES_PER_SHORT, sampleRate, channels);
    }

    public static int usToShorts(long us, int sampleRate, int channels) {
        return usToBytes(us, sampleRate, channels) / BYTES_PER_SHORT;
    }
}
