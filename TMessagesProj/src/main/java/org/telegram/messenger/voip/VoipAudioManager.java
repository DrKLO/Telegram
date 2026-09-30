package org.telegram.messenger.voip;

import static android.content.Context.AUDIO_SERVICE;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;


import org.telegram.messenger.AndroidUtilities;
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

    /**
     * On Android 12+ Bluetooth call routing goes through {@link AudioManager#setCommunicationDevice}.
     * LE Audio headsets exist only there: they never connect the HFP profile, so the SCO API cannot reach them.
     */
    public static boolean isBluetoothDevice(AudioDeviceInfo device) {
        if (device == null) {
            return false;
        }
        int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER);
    }

    public AudioDeviceInfo findBluetoothDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        for (AudioDeviceInfo device : getAudioManager().getAvailableCommunicationDevices()) {
            if (isBluetoothDevice(device)) {
                return device;
            }
        }
        return null;
    }

    public boolean isBluetoothOn() {
        AudioManager audioManager = getAudioManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return isBluetoothDevice(audioManager.getCommunicationDevice());
        }
        return audioManager.isBluetoothScoOn();
    }

    public void startBluetooth() {
        final AudioManager audioManager = getAudioManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo device = findBluetoothDevice();
            if (device == null) {
                return;
            }
            isSpeakerphoneOn = false;
            Utilities.globalQueue.postRunnable(() -> audioManager.setCommunicationDevice(device));
        } else {
            audioManager.startBluetoothSco();
        }
    }

    public void stopBluetooth() {
        final AudioManager audioManager = getAudioManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Utilities.globalQueue.postRunnable(() -> {
                if (isBluetoothDevice(audioManager.getCommunicationDevice())) {
                    audioManager.clearCommunicationDevice();
                }
            });
        } else {
            audioManager.stopBluetoothSco();
        }
    }

    public void setBluetoothOn(boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                startBluetooth();
            } else {
                stopBluetooth();
            }
        } else {
            getAudioManager().setBluetoothScoOn(on);
        }
    }

    public void isBluetoothAndSpeakerOnAsync(Utilities.Callback2<Boolean, Boolean> onDone) {
        Utilities.globalQueue.postRunnable(() -> {
            AudioManager audioManager = getAudioManager();
            boolean isBluetoothOn = isBluetoothOn();
            boolean isSpeakerphoneOn = audioManager.isSpeakerphoneOn();
            AndroidUtilities.runOnUIThread(() -> onDone.run(isBluetoothOn, isSpeakerphoneOn));
        });
    }

    private AudioManager getAudioManager() {
        return (AudioManager) ApplicationLoader.applicationContext.getSystemService(AUDIO_SERVICE);
    }
}
