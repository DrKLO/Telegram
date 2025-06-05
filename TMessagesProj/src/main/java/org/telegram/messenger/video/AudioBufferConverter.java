package org.telegram.messenger.video;

import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.messenger.video.remix.AudioRemixer;
import org.telegram.messenger.video.remix.DefaultAudioRemixer;
import org.telegram.messenger.video.resample.AudioResampler;
import org.telegram.messenger.video.resample.DefaultAudioResampler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioBufferConverter {
    private static final String TAG = AudioBufferConverter.class.getSimpleName();
    private static final int BYTES_PER_SHORT = 2;

    private final AudioRemixer mRemixer;
    private final AudioResampler mResampler;

    public AudioBufferConverter() {
        // Create remixer and resampler.
        mRemixer = new DefaultAudioRemixer();
        mResampler = new DefaultAudioResampler();
    }

    public int calculateRequiredOutputSize(int inputSize, int inputSampleRate, int inputChannelCount,
                                           int outputSampleRate, int outputChannelCount){
        checkChannels(inputChannelCount, outputChannelCount);

        int requiredOutputSize = inputSize;

        // Ask remixer how much space it needs for the given input
        requiredOutputSize = mRemixer.getRemixedSize(requiredOutputSize, inputChannelCount, outputChannelCount);

        // After remixing we'll resample.
        // Resampling will change the input size based on the sample rate ratio.
        requiredOutputSize = (int) Math.ceil((double) requiredOutputSize *
                outputSampleRate / (double)inputSampleRate);
        return requiredOutputSize;
    }

    public ShortBuffer convert(@NonNull ShortBuffer inputBuffer, int inputSampleRate, int inputChannelCount,
                        int outputSampleRate, int outputChannelCount) {

        checkChannels(inputChannelCount, outputChannelCount);

        final int inputSize = inputBuffer.remaining();

        // Do the remixing.
        int remixSize = mRemixer.getRemixedSize(inputSize, inputChannelCount, outputChannelCount);
        ShortBuffer remixedBuffer = createBuffer(remixSize);
        mRemixer.remix(inputBuffer, inputChannelCount, remixedBuffer, outputChannelCount);
        remixedBuffer.rewind();

        // Do the resampling.
        int resampleSize = (int) Math.ceil((double) remixSize * outputSampleRate / (double)inputSampleRate);
        // We add some extra values to avoid BufferOverflowException.
        // Problem may occur for calculation.
        // To be safe we add 10 but 1 is enough may be. Not sure.
        resampleSize += 10;

        ShortBuffer outputBuffer = createBuffer(resampleSize);
        mResampler.resample(remixedBuffer, inputSampleRate, outputBuffer, outputSampleRate, outputChannelCount);
        outputBuffer.limit(outputBuffer.position());
        outputBuffer.rewind();

        return outputBuffer;
    }

    private void checkChannels(int inputChannelCount, int outputChannelCount){
        if (inputChannelCount == 6 && (outputChannelCount == 1 || outputChannelCount == 2)) {
            return;
        }
        if (inputChannelCount != 1 && inputChannelCount != 2) {
            throw new UnsupportedOperationException("Input channel count (" + inputChannelCount + ") not supported.");
        }
        if (outputChannelCount != 1 && outputChannelCount != 2) {
            throw new UnsupportedOperationException("Output channel count (" + outputChannelCount + ") not supported.");
        }
    }

    private ShortBuffer createBuffer(int capacity) {
        ShortBuffer buffer = ByteBuffer.allocateDirect(capacity * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        buffer.clear();
        buffer.limit(capacity);
        return buffer;
    }
}
