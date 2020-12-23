package org.telegram.messenger.voip;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.webrtc.ContextUtils;
import org.webrtc.VideoSink;

import java.util.concurrent.CountDownLatch;

public class NativeInstance {

    private Instance.OnStateUpdatedListener onStateUpdatedListener;
    private Instance.OnSignalBarsUpdatedListener onSignalBarsUpdatedListener;
    private Instance.OnSignalingDataListener onSignalDataListener;
    private Instance.OnRemoteMediaStateUpdatedListener onRemoteMediaStateUpdatedListener;
    private long nativePtr;
    private String persistentStateFilePath;

    private PayloadCallback payloadCallback;
    private AudioLevelsCallback audioLevelsCallback;
    private float[] temp = new float[1];

    private boolean isGroup;

    public interface PayloadCallback {
        void run(int ssrc, String value);
    }

    public interface AudioLevelsCallback {
        void run(int[] uids, float[] levels, boolean[] voice);
    }

    public static NativeInstance make(String version, Instance.Config config, String path, Instance.Endpoint[] endpoints, Instance.Proxy proxy, int networkType, Instance.EncryptionKey encryptionKey, VideoSink remoteSink, long videoCapturer) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("create new tgvoip instance, version " + version);
        }
        NativeInstance instance = new NativeInstance();
        instance.persistentStateFilePath = path;
        float aspectRatio = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        instance.nativePtr = makeNativeInstance(version, instance, config, path, endpoints, proxy, networkType, encryptionKey, remoteSink, videoCapturer, aspectRatio);
        return instance;
    }

    public static NativeInstance makeGroup(PayloadCallback payloadCallback, AudioLevelsCallback audioLevelsCallback) {
        ContextUtils.initialize(ApplicationLoader.applicationContext);
        NativeInstance instance = new NativeInstance();
        instance.payloadCallback = payloadCallback;
        instance.audioLevelsCallback = audioLevelsCallback;
        instance.isGroup = true;
        instance.nativePtr = makeGroupNativeInstance(instance, SharedConfig.disableVoiceAudioEffects);
        return instance;
    }

    public int getPeerCapabilities() {
        return 0;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setOnStateUpdatedListener(Instance.OnStateUpdatedListener listener) {
        onStateUpdatedListener = listener;
    }

    public void setOnSignalBarsUpdatedListener(Instance.OnSignalBarsUpdatedListener listener) {
        onSignalBarsUpdatedListener = listener;
    }

    public void setOnSignalDataListener(Instance.OnSignalingDataListener listener) {
        onSignalDataListener = listener;
    }

    public void setOnRemoteMediaStateUpdatedListener(Instance.OnRemoteMediaStateUpdatedListener listener) {
        onRemoteMediaStateUpdatedListener = listener;
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

    private void onSignalingData(byte[] data) {
        if (onSignalDataListener != null) {
            onSignalDataListener.onSignalingData(data);
        }
    }

    private void onRemoteMediaStateUpdated(int audioState, int videoState) {
        if (onRemoteMediaStateUpdatedListener != null) {
            onRemoteMediaStateUpdatedListener.onMediaStateUpdated(audioState, videoState);
        }
    }

    //group calls
    private void onNetworkStateUpdated(boolean connected) {
        if (onStateUpdatedListener != null) {
            AndroidUtilities.runOnUIThread(() -> onStateUpdatedListener.onStateUpdated(connected ? 1 : 0));
        }
    }

    private void onAudioLevelsUpdated(int[] uids, float[] levels, boolean[] voice) {
        if (uids.length == 0) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> audioLevelsCallback.run(uids, levels, voice));
    }

    private void onEmitJoinPayload(String ufrag, String pwd, Instance.Fingerprint[] fingerprints, int ssrc) {
        try {
            JSONObject json = new JSONObject();
            json.put("ufrag", ufrag);
            json.put("pwd", pwd);
            JSONArray array = new JSONArray();
            for (int a = 0; a < fingerprints.length; a++) {
                JSONObject object = new JSONObject();
                object.put("hash", fingerprints[a].hash);
                object.put("fingerprint", fingerprints[a].fingerprint);
                object.put("setup", fingerprints[a].setup);
                array.put(object);
            }
            json.put("fingerprints", array);
            json.put("ssrc", ssrc);
            AndroidUtilities.runOnUIThread(() -> payloadCallback.run(ssrc, json.toString()));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public native void removeSsrcs(int[] ssrcs);
    public native void setJoinResponsePayload(String ufrag, String pwd, Instance.Fingerprint[] fingerprints, Instance.Candidate[] candidates);

    private Instance.FinalState finalState;
    private CountDownLatch stopBarrier;
    private void onStop(Instance.FinalState state) {
        finalState = state;
        if (stopBarrier != null) {
            stopBarrier.countDown();
        }
    }

    public Instance.FinalState stop() {
        stopBarrier = new CountDownLatch(1);
        stopNative();
        try {
            stopBarrier.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return finalState;
    }

    public void stopGroup() {
        stopGroupNative();
    }

    private static native long makeGroupNativeInstance(NativeInstance instance, boolean highQuality);
    private static native long makeNativeInstance(String version, NativeInstance instance, Instance.Config config, String persistentStateFilePath, Instance.Endpoint[] endpoints, Instance.Proxy proxy, int networkType, Instance.EncryptionKey encryptionKey, VideoSink remoteSink, long videoCapturer, float aspectRatio);
    public static native long createVideoCapturer(VideoSink localSink, boolean front);
    public static native void setVideoStateCapturer(long videoCapturer, int videoState);
    public static native void switchCameraCapturer(long videoCapturer, boolean front);
    public static native void destroyVideoCapturer(long videoCapturer);

    public native void setGlobalServerConfig(String serverConfigJson);
    public native void setBufferSize(int size);
    public native String getVersion();
    public native void setNetworkType(int networkType);
    public native void setMuteMicrophone(boolean muteMicrophone);
    public native void setAudioOutputGainControlEnabled(boolean enabled);
    public native void setEchoCancellationStrength(int strength);
    public native String getLastError();
    public native String getDebugInfo();
    public native long getPreferredRelayId();
    public native Instance.TrafficStats getTrafficStats();
    public native byte[] getPersistentState();
    private native void stopNative();
    private native void stopGroupNative();
    public native void setupOutgoingVideo(VideoSink localSink, boolean front);
    public native void switchCamera(boolean front);
    public native void setVideoState(int videoState);
    public native void onSignalingDataReceive(byte[] data);
}
