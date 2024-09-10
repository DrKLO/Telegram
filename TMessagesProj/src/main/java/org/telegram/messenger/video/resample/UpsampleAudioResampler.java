package org.telegram.messenger.video.resample;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * An {@link AudioResampler} that upsamples from a lower sample rate to a higher sample rate.
 */
public class UpsampleAudioResampler implements AudioResampler {

    private static float ratio(int remaining, int all) {
        return (float) remaining / all;
    }

    @Override
    public void resample(@NonNull ShortBuffer inputBuffer, int inputSampleRate, @NonNull ShortBuffer outputBuffer, int outputSampleRate, int channels) {
        if (inputSampleRate > outputSampleRate) {
            throw new IllegalArgumentException("Illegal use of UpsampleAudioResampler");
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Illegal use of UpsampleAudioResampler. Channels:" + channels);
        }

        final int inputSamples = inputBuffer.remaining() / channels;
        final int outputSamples = (int) Math.ceil(inputSamples * ((double) outputSampleRate / inputSampleRate));

        final int fakeSamples = outputSamples - inputSamples;
        int remainingInputSamples = inputSamples;
        int remainingFakeSamples = fakeSamples;
        float remainingInputSamplesRatio = ratio(remainingInputSamples, inputSamples);
        float remainingFakeSamplesRatio = ratio(remainingFakeSamples, fakeSamples);
        while (remainingInputSamples > 0 && remainingFakeSamples > 0) {
            // Will this be an input sample or a fake sample?
            // Choose the one with the bigger ratio.
            if (remainingInputSamplesRatio >= remainingFakeSamplesRatio) {
                outputBuffer.put(inputBuffer.get());
                if (channels == 2) outputBuffer.put(inputBuffer.get());
                remainingInputSamples--;
                remainingInputSamplesRatio = ratio(remainingInputSamples, inputSamples);
            } else {
                outputBuffer.put(fakeSample(outputBuffer, inputBuffer, 1, channels));
                if (channels == 2) outputBuffer.put(fakeSample(outputBuffer, inputBuffer, 2, channels));
                remainingFakeSamples--;
                remainingFakeSamplesRatio = ratio(remainingFakeSamples, fakeSamples);
            }
        }
    }

    /**
     * We have different options here.
     * 1. Return a 0 sample.
     * 2. Return a noise sample.
     * 3. Return the previous sample for this channel.
     * 4. Return an interpolated value between previous and next sample for this channel.
     *
     * None of this provides a real quality improvement, since the fundamental issue is that we
     * can not achieve a higher quality by simply inserting fake samples each X input samples.
     * A real upsampling algorithm should do something more intensive like interpolating between
     * all values, not just the spare one.
     *
     * However this is probably beyond the scope of this library.
     *
     * @param output output buffer
     * @param input output buffer
     * @param channel current channel
     * @param channels number of channels
     * @return a fake sample
     */
    @SuppressWarnings("unused")
    private static short fakeSample(@NonNull ShortBuffer output, @NonNull ShortBuffer input, int channel, int channels) {
        return output.get(output.position() - channels);
    }
}
