package org.telegram.messenger.video.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * A {@link AudioRemixer} that takes only first channels from 6-channel input
 */
public class SurroundAudioRemixer implements AudioRemixer {

    @Override
    public void remix(@NonNull ShortBuffer inputBuffer, int inputChannelCount, @NonNull ShortBuffer outputBuffer, int outputChannelCount) {
        if (outputChannelCount != 1 && outputChannelCount != 2) {
            throw new IllegalArgumentException("Output must be 2 or 1 channels");
        }

        // Calculate the number of frames to process
        final int inFramesRemaining = inputBuffer.remaining() / inputChannelCount;
        final int outFramesSpace = outputBuffer.remaining() / outputChannelCount;
        final int framesToProcess = Math.min(inFramesRemaining, outFramesSpace);

        // Process each frame, extracting the first two channels (Front Left and Front Right)
        for (int i = 0; i < framesToProcess; ++i) {
            // Read the first two samples of each 6-channel frame
            short frontLeft = inputBuffer.get();   // First channel (Front Left)
            short frontRight = inputBuffer.get();  // Second channel (Front Right)

            // Skip the remaining 4 channels (Center, LFE, Rear Left, Rear Right)
            inputBuffer.position(inputBuffer.position() + 4);

            // Write the two extracted channels to the output buffer
            if (outputChannelCount == 2) {
                outputBuffer.put(frontLeft);
                outputBuffer.put(frontRight);
            } else if (outputChannelCount == 1) {
                outputBuffer.put(DownMixAudioRemixer.mix(frontLeft, frontRight));
            }
        }
    }

    @Override
    public int getRemixedSize(int inputSize, int inputChannelCount, int outputChannelCount) {
        return inputSize / inputChannelCount * outputChannelCount;
    }
}
