package org.telegram.messenger.voip;

public final class NativeTgVoipInstance implements TgVoip.Instance {

    private final String persistentStateFilePath;

    private TgVoip.OnStateUpdatedListener onStateUpdatedListener;
    private TgVoip.OnSignalBarsUpdatedListener onSignalBarsUpdatedListener;

    private long nativeInstanceId;

    public NativeTgVoipInstance(String persistentStateFilePath) {
        this.persistentStateFilePath = persistentStateFilePath;
    }

    public void setNativeInstanceId(long nativeInstanceId) {
        this.nativeInstanceId = nativeInstanceId;
    }

    @Override
    public native void setNetworkType(int networkType);

    @Override
    public native void setMuteMicrophone(boolean muteMicrophone);

    @Override
    public native void setAudioOutputGainControlEnabled(boolean enabled);

    @Override
    public native void setEchoCancellationStrength(int strength);

    @Override
    public native String getLastError();

    @Override
    public native String getDebugInfo();

    @Override
    public native long getPreferredRelayId();

    @Override
    public native TgVoip.TrafficStats getTrafficStats();

    @Override
    public native byte[] getPersistentState();

    @Override
    public native TgVoip.FinalState stop();

    @Override
    public void sendGroupCallKey(byte[] groupCallEncryptionKey) {
    }

    @Override
    public void requestCallUpgrade() {
    }

    @Override
    public int getPeerCapabilities() {
        return 0;
    }

    @Override
    public void setOnStateUpdatedListener(TgVoip.OnStateUpdatedListener listener) {
        onStateUpdatedListener = listener;
    }

    @Override
    public void setOnSignalBarsUpdatedListener(TgVoip.OnSignalBarsUpdatedListener listener) {
        onSignalBarsUpdatedListener = listener;
    }

    private void onStateUpdated(int state) {
        if (onStateUpdatedListener != null) {
            onStateUpdatedListener.onStateUpdated(state);
        }
    }

    private void onSignalBarsUpdated(int signalBars) {
        if (onSignalBarsUpdatedListener != null) {
            onSignalBarsUpdatedListener.onSignalBarsUpdated(signalBars);
        }
    }
}
