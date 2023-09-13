package org.telegram.messenger.video.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * A {@link AudioRemixer} that upmixes mono audio to stereo.
 */
public class UpMixAudioRemixer implements AudioRemixer {

    @Override
    public void remix(@NonNull ShortBuffer inputBuffer, int inputChannelCount, @NonNull ShortBuffer outputBuffer, int outputChannelCount) {
        // Up-mix mono to stereo
        final int inRemaining = inputBuffer.remaining();
        final int outSpace = outputBuffer.remaining() / 2;

        final int samplesToBeProcessed = Math.min(inRemaining, outSpace);
        for (int i = 0; i < samplesToBeProcessed; ++i) {
            final short inSample = inputBuffer.get();
            outputBuffer.put(inSample);
            outputBuffer.put(inSample);
        }
    }

    @Override
    public int getRemixedSize(int inputSize, int inputChannelCount, int outputChannelCount) {
        return inputSize * 2;
    }
}
