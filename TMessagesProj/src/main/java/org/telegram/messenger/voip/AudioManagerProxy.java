package org.telegram.messenger.voip;

import static android.content.Context.AUDIO_SERVICE;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.DispatchQueue;

public class AudioManagerProxy {
    private DispatchQueue dispatchQueue = new DispatchQueue("audioManagerProxy");

    private AudioManager am;
    private boolean isSpeakerphoneOnInitialized;
    private boolean isSpeakerphoneOn;

    private boolean isBluetoothScoOnInitialized;
    private boolean isBluetoothScoOn;

    private boolean isWiredHeadsetOnInitialized;
    private boolean isWiredHeadsetOn;

    public AudioManagerProxy(Context context) {
        am = (AudioManager) context.getSystemService(AUDIO_SERVICE);
    }

    public void setSpeakerphoneOn(boolean on) {
        isSpeakerphoneOn = on;
        isSpeakerphoneOnInitialized = true;
        dispatchQueue.postRunnable(() -> am.setSpeakerphoneOn(on));
    }

    public boolean isSpeakerphoneOn() {
        if (!isSpeakerphoneOnInitialized) {
            isSpeakerphoneOn = am.isSpeakerphoneOn();
            isSpeakerphoneOnInitialized = true;
        }
        return isSpeakerphoneOn;
    }

    public void setBluetoothScoOn(boolean on) {
        isBluetoothScoOn = on;
        isBluetoothScoOnInitialized = true;
        dispatchQueue.postRunnable(() -> am.setBluetoothScoOn(on));
    }

    public boolean isBluetoothScoOn() {
        if (!isBluetoothScoOnInitialized) {
            isBluetoothScoOn = am.isBluetoothScoOn();
            isBluetoothScoOnInitialized = true;
        }
        return isBluetoothScoOn;
    }

    public void stopBluetoothSco() {
        am.stopBluetoothSco();
    }

    public void startBluetoothSco() {
        am.startBluetoothSco();
    }

    public boolean isWiredHeadsetOn() {
        if (!isWiredHeadsetOnInitialized) {
            isWiredHeadsetOn = am.isBluetoothScoOn();
            isWiredHeadsetOnInitialized = true;
        }
        return isWiredHeadsetOn;
    }

    public void abandonAudioFocus(AudioManager.OnAudioFocusChangeListener l) {
        am.abandonAudioFocus(l);
    }

    public void unregisterMediaButtonEventReceiver(ComponentName eventReceiver) {
        am.unregisterMediaButtonEventReceiver(eventReceiver);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void unregisterAudioDeviceCallback(AudioDeviceCallback callback) {
        am.unregisterAudioDeviceCallback(callback);
    }

    public void setMode(int mode) {
        am.setMode(mode);
    }

    public void requestAudioFocus(AudioManager.OnAudioFocusChangeListener l, int streamType, int durationHint) {
        am.requestAudioFocus(l, streamType, durationHint);
    }

    public AudioManager getAudioManager() {
        return am;
    }

    public int getRingerMode() {
        return am.getRingerMode();
    }

    public String getProperty(String key) {
        return am.getProperty(key);
    }

    public boolean isBluetoothScoAvailableOffCall() {
        return am.isBluetoothScoAvailableOffCall();
    }

    public boolean isBluetoothA2dpOn() {
        return am.isBluetoothA2dpOn();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void registerAudioDeviceCallback(AudioDeviceCallback callback, Handler handler) {
        am.registerAudioDeviceCallback(callback, handler);
    }

    public void registerMediaButtonEventReceiver(ComponentName componentName) {
        am.registerMediaButtonEventReceiver(componentName);
    }
}
