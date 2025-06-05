package org.telegram.messenger.video.remix;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ShortBuffer;

/**
 * Remixes audio data. See {@link DownMixAudioRemixer},
 * {@link UpMixAudioRemixer} or {@link PassThroughAudioRemixer}
 * for concrete implementations.
 */
public interface AudioRemixer {

    /**
     * Remixes input audio from input buffer into the output buffer.
     * The output buffer is guaranteed to have a {@link Buffer#remaining()} size that is
     * consistent with {@link #getRemixedSize(int,int,int)}.
     *
     * @param inputBuffer the input buffer
     * @param outputBuffer the output buffer
     */
    void remix(@NonNull final ShortBuffer inputBuffer, int inputChannelCount,
               @NonNull final ShortBuffer outputBuffer, int outputChannelCount);

    /**
     * Returns the output size (in shorts) needed to process an input buffer of the
     * given size (in shorts).
     * @param inputSize input size in shorts
     * @return output size in shorts
     */
    int getRemixedSize(int inputSize, int inputChannelCount, int outputChannelCount);

    AudioRemixer DOWNMIX = new DownMixAudioRemixer();

    AudioRemixer UPMIX = new UpMixAudioRemixer();

    AudioRemixer PASSTHROUGH = new PassThroughAudioRemixer();

    AudioRemixer SURROUND = new SurroundAudioRemixer();
}
