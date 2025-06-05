package org.telegram.messenger.video.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * The simplest {@link AudioRemixer} that does nothing.
 */
public class PassThroughAudioRemixer implements AudioRemixer {

    @Override
    public void remix(@NonNull ShortBuffer inputBuffer, int inputChannelCount, @NonNull ShortBuffer outputBuffer, int outputChannelCount) {
        outputBuffer.put(inputBuffer);
    }

    @Override
    public int getRemixedSize(int inputSize, int inputChannelCount, int outputChannelCount) {
        return inputSize;
    }
}
