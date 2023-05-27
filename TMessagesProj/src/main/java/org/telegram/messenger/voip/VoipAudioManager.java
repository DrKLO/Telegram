package org.telegram.messenger.voip;

import static android.content.Context.AUDIO_SERVICE;

import android.media.AudioManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

public class VoipAudioManager {

    private Boolean isSpeakerphoneOn;

    private VoipAudioManager() {

    }

    private static final class InstanceHolder {
        static final VoipAudioManager instance = new VoipAudioManager();
    }

    public static VoipAudioManager get() {
        return InstanceHolder.instance;
    }

    /**
     * Sets the speakerphone on or off asynchronously.
     * On Samsung devices {@link AudioManager#setSpeakerphoneOn} and {@link AudioManager#isSpeakerphoneOn} take too much time.
     */
    public void setSpeakerphoneOn(boolean on) {
        isSpeakerphoneOn = on;
        final AudioManager audioManager = getAudioManager();
        Utilities.globalQueue.postRunnable(() -> {
            audioManager.setSpeakerphoneOn(on);
        });
    }

    /**
     * Checks whether the speakerphone is on or off.
     * {@link AudioManager#isSpeakerphoneOn} is fast if {@link AudioManager#setSpeakerphoneOn} has not been called before.
     */
    public boolean isSpeakerphoneOn() {
        if (isSpeakerphoneOn == null) {
            AudioManager audioManager = getAudioManager();
            return audioManager.isSpeakerphoneOn();
        }
        return isSpeakerphoneOn;
    }

    private AudioManager getAudioManager() {
        return (AudioManager) ApplicationLoader.applicationContext.getSystemService(AUDIO_SERVICE);
    }
}
