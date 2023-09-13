package org.telegram.messenger.video.audio_input;

import org.telegram.messenger.video.AudioConversions;

public class BlankAudioInput extends AudioInput {

    public final long durationUs;
    private int requiredShortsForDuration;
    private int remainingShorts;

    public BlankAudioInput(long durationUs){
        this.durationUs = durationUs;
    }

    @Override
    public long getStartTimeUs() {
        return 0;
    }

    @Override
    public long getEndTimeUs() {
        return durationUs;
    }

    @Override
    public long getDurationUs() {
        return durationUs;
    }

    @Override
    public int getSampleRate() {
        return -1;
    }

    @Override
    public int getBitrate() {
        return -1;
    }

    @Override
    public int getChannelCount() {
        return -1;
    }

    @Override
    public void setStartTimeUs(long timeUs) { }

    @Override
    public void setEndTimeUs(long timeUs) { }

    @Override
    public boolean hasRemaining() {
        return remainingShorts > 0;
    }

    @Override
    public void start(int outputSampleRate, int outputChannelCount) {
        requiredShortsForDuration = AudioConversions.usToShorts(
                durationUs, outputSampleRate, outputChannelCount);
        remainingShorts = requiredShortsForDuration;
    }

    @Override
    public short getNext() {
        if(!hasRemaining()) throw new RuntimeException("Audio input has no remaining value.");

        remainingShorts--;
        if(isLoopingEnabled() && remainingShorts == 0){
            remainingShorts = requiredShortsForDuration;
        }
        return 0;
    }

    @Override
    public void release() {
        remainingShorts = 0;
    }
}
