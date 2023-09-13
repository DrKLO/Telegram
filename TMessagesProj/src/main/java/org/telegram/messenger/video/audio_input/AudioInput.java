package org.telegram.messenger.video.audio_input;

public abstract class AudioInput {
    private boolean loopingEnabled;
    private float volume = 1f;

    public boolean isLoopingEnabled() {
        return loopingEnabled;
    }

    public float getVolume() {
        return volume;
    }

    public void setLoopingEnabled(boolean loopingEnabled) {
        this.loopingEnabled = loopingEnabled;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(volume, 1f));
    }

    public abstract long getStartTimeUs();

    public abstract long getEndTimeUs();

    public abstract long getDurationUs();

    public abstract int getSampleRate();

    public abstract int getBitrate();

    public abstract int getChannelCount();

    public abstract boolean hasRemaining();

    public abstract void setStartTimeUs(long timeUs);

    public abstract void setEndTimeUs(long timeUs);

    public abstract void start(int outputSampleRate, int outputChannelCount);

    public abstract short getNext();

    public abstract void release();

}
