package org.telegram.messenger.video.audio_input;

import android.util.Log;

import org.telegram.messenger.video.AudioBufferConverter;
import org.telegram.messenger.video.AudioConversions;
import org.telegram.messenger.video.AudioDecoder;

import java.io.IOException;
import java.nio.ShortBuffer;


public class GeneralAudioInput extends AudioInput {

    private final AudioDecoder decoder;
    private AudioBufferConverter audioBufferConverter;

    private long startOffsetUs;
    private int requiredShortsForStartOffset;
    private int startOffsetShortsCounter;

    private int outputSampleRate;
    private int outputChannelCount;

    private ShortBuffer buffer;
    private boolean hasRemaining;

    public GeneralAudioInput(String sourcePath) throws IOException {
        decoder = new AudioDecoder(sourcePath);
        init();
    }

    public GeneralAudioInput(String sourcePath, int audioIndex) throws IOException {
        decoder = new AudioDecoder(sourcePath, audioIndex);
        init();
    }

    private void init() {
        audioBufferConverter = new AudioBufferConverter();
    }

    @Override
    public void setLoopingEnabled(boolean loopingEnabled) {
        super.setLoopingEnabled(loopingEnabled);
        decoder.setLoopingEnabled(loopingEnabled);
    }

    public long getStartOffsetUs() {
        return startOffsetUs;
    }

    @Override
    public long getStartTimeUs() {
        return decoder.getStartTimeUs();
    }

    @Override
    public long getEndTimeUs() {
        return decoder.getEndTimeUs();
    }

    @Override
    public long getDurationUs() {
        return getEndTimeUs() - getStartTimeUs() + getStartOffsetUs();
    }

    @Override
    public int getSampleRate() {
        return decoder.getSampleRate();
    }

    @Override
    public int getBitrate() {
        return decoder.getBitrateRate();
    }

    @Override
    public int getChannelCount() {
        return decoder.getChannelCount();
    }

    public void setStartOffsetUs(long startOffsetUs) {
        this.startOffsetUs = startOffsetUs < 0 ? 0 : startOffsetUs;
    }

    @Override
    public void setStartTimeUs(long timeUs) {
        decoder.setStartTimeUs(timeUs);
    }

    @Override
    public void setEndTimeUs(long timeUs) {
        decoder.setEndTimeUs(timeUs);
    }

    @Override
    public boolean hasRemaining() {
        return hasRemaining;
    }

    @Override
    public void start(int outputSampleRate, int outputChannelCount) {
        this.outputSampleRate = outputSampleRate;
        this.outputChannelCount = outputChannelCount;
        hasRemaining = true;
        decoder.start();

        requiredShortsForStartOffset = AudioConversions.usToShorts(getStartOffsetUs(), this.outputSampleRate, this.outputChannelCount);
        startOffsetShortsCounter = 0;
    }

    @Override
    public short getNext() {
        if (!hasRemaining()) throw new RuntimeException("Audio input has no remaining value.");

        if (startOffsetShortsCounter < requiredShortsForStartOffset) {
            startOffsetShortsCounter++;
            return 0;
        }

        decode();
        short value = 0;
        if (buffer != null && buffer.remaining() > 0) {
            value = buffer.get();
        }
        decode();

        if (buffer == null || buffer.remaining() < 1) {
            hasRemaining = false;
        }

        return value;
    }

    private void decode() {
        if (buffer == null || buffer.remaining() <= 0) {
            AudioDecoder.DecodedBufferData audioData = decoder.decode();
            if (audioData.index >= 0) {
                final ShortBuffer inputBuffer = audioData.byteBuffer.asShortBuffer();
                buffer = audioBufferConverter.convert(
                    inputBuffer,
                    decoder.getSampleRate(), decoder.getChannelCount(),
                    outputSampleRate, outputChannelCount
                );
                decoder.releaseOutputBuffer(audioData.index);
            } else {
                buffer = null;
            }
        }
    }

    @Override
    public void release() {
        buffer = null;
        hasRemaining = false;
        decoder.stop();
        decoder.release();
    }
}
