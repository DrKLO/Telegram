package org.telegram.ui.Stories.recorder;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

@Keep
public class FfmpegAudioWaveformLoader {

    @Keep
    private volatile boolean running = true;

    private native void init(String path, int count);

    public FfmpegAudioWaveformLoader(String path, int count, Utilities.Callback2<short[], Integer> onChunkReceived) {
        this.onChunkReceived = onChunkReceived;
        Utilities.phoneBookQueue.postRunnable(() -> {
            init(path, count);
        });
    }

    private Utilities.Callback2<short[], Integer> onChunkReceived;
    @Keep
    private void receiveChunk(short[] data, int len) {
        AndroidUtilities.runOnUIThread(() -> {
            onChunkReceived.run(data, len);
        });
    }

    public void destroy() {
        Utilities.phoneBookQueue.postRunnable(() -> {
            running = false;
        });
    }
}
