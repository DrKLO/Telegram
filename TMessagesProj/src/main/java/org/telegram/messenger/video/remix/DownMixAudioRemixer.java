package org.telegram.messenger.video.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * A {@link AudioRemixer} that downmixes stereo audio to mono.
 */
public class DownMixAudioRemixer implements AudioRemixer {

    private static final int SIGNED_SHORT_LIMIT = 32768;
    private static final int UNSIGNED_SHORT_MAX = 65535;

    @Override
    public void remix(@NonNull ShortBuffer inputBuffer, int inputChannelCount, @NonNull ShortBuffer outputBuffer, int outputChannelCount) {
        final int inRemaining = inputBuffer.remaining() / 2;
        final int outSpace = outputBuffer.remaining();

        final int samplesToBeProcessed = Math.min(inRemaining, outSpace);
        for (int i = 0; i < samplesToBeProcessed; ++i) {
            outputBuffer.put(mix(inputBuffer.get(), inputBuffer.get()));
        }
    }

    @Override
    public int getRemixedSize(int inputSize, int inputChannelCount, int outputChannelCount) {
        return inputSize / 2;
    }

    public static short mix(short input1, short input2) {
        // Down-mix stereo to mono
        // Viktor Toth's algorithm -
        // See: http://www.vttoth.com/CMS/index.php/technical-notes/68
        //      http://stackoverflow.com/a/25102339

        // Convert to unsigned
        final int a = input1 + SIGNED_SHORT_LIMIT;
        final int b = input2 + SIGNED_SHORT_LIMIT;
        int m;
        // Pick the equation
        if ((a < SIGNED_SHORT_LIMIT) || (b < SIGNED_SHORT_LIMIT)) {
            // Viktor's first equation when both sources are "quiet"
            // (i.e. less than middle of the dynamic range)
            m = a * b / SIGNED_SHORT_LIMIT;
        } else {
            // Viktor's second equation when one or both sources are loud
            m = 2 * (a + b) - (a * b) / SIGNED_SHORT_LIMIT - UNSIGNED_SHORT_MAX;
        }
        // Convert output back to signed short
        if (m == UNSIGNED_SHORT_MAX + 1) m = UNSIGNED_SHORT_MAX;
        return (short) (m - SIGNED_SHORT_LIMIT);
    }
}
