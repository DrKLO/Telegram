package org.telegram.messenger.voip;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.webrtc.ContextUtils;
import org.webrtc.VideoSink;

import java.nio.ByteBuffer;
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
    private VideoSourcesCallback unknownParticipantsCallback;
    private RequestBroadcastPartCallback requestBroadcastPartCallback;
    private RequestBroadcastPartCallback cancelRequestBroadcastPartCallback;
    private float[] temp = new float[1];

    private boolean isGroup;

    public static class SsrcGroup {
        public String semantics;
        public int[] ssrcs;
    }

    public interface PayloadCallback {
        void run(int ssrc, String value);
    }

    public interface AudioLevelsCallback {
        void run(int[] uids, float[] levels, boolean[] voice);
    }

    public interface VideoSourcesCallback {
        void run(long taskPtr, int[] ssrcs);
    }

    public interface RequestBroadcastPartCallback {
        void run(long timestamp, long duration, int videoChannel, int quality);
    }

    public static NativeInstance make(String version, Instance.Config config, String path, Instance.Endpoint[] endpoints, Instance.Proxy proxy, int networkType, Instance.EncryptionKey encryptionKey, VideoSink remoteSink, long videoCapturer, AudioLevelsCallback audioLevelsCallback) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("create new tgvoip instance, version " + version);
        }
        NativeInstance instance = new NativeInstance();
        instance.persistentStateFilePath = path;
        instance.audioLevelsCallback = audioLevelsCallback;
        float aspectRatio = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        instance.nativePtr = makeNativeInstance(version, instance, config, path, endpoints, proxy, networkType, encryptionKey, remoteSink, videoCapturer, aspectRatio);
        return instance;
    }

    public static NativeInstance makeGroup(String logPath, long videoCapturer, boolean screencast, boolean noiseSupression, PayloadCallback payloadCallback, AudioLevelsCallback audioLevelsCallback, VideoSourcesCallback unknownParticipantsCallback, RequestBroadcastPartCallback requestBroadcastPartCallback, RequestBroadcastPartCallback cancelRequestBroadcastPartCallback) {
        ContextUtils.initialize(ApplicationLoader.applicationContext);
        NativeInstance instance = new NativeInstance();
        instance.payloadCallback = payloadCallback;
        instance.audioLevelsCallback = audioLevelsCallback;
        instance.unknownParticipantsCallback = unknownParticipantsCallback;
        instance.requestBroadcastPartCallback = requestBroadcastPartCallback;
        instance.cancelRequestBroadcastPartCallback = cancelRequestBroadcastPartCallback;
        instance.isGroup = true;
        instance.nativePtr = makeGroupNativeInstance(instance, logPath, SharedConfig.disableVoiceAudioEffects, videoCapturer, screencast, noiseSupression);
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

    private void onStateUpdated(int state/*, boolean */) {
        if (onStateUpdatedListener != null) {
            onStateUpdatedListener.onStateUpdated(state, false);
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
    private void onNetworkStateUpdated(boolean connected, boolean inTransition) {
        if (onStateUpdatedListener != null) {
            AndroidUtilities.runOnUIThread(() -> onStateUpdatedListener.onStateUpdated(connected ? 1 : 0, inTransition));
        }
    }

    private void onAudioLevelsUpdated(int[] uids, float[] levels, boolean[] voice) {
        if (isGroup && uids != null && uids.length == 0) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> audioLevelsCallback.run(uids, levels, voice));
    }

    private void onParticipantDescriptionsRequired(long taskPtr, int[] ssrcs) {
        if (unknownParticipantsCallback == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> unknownParticipantsCallback.run(taskPtr, ssrcs));
    }

    private void onEmitJoinPayload(String json, int ssrc) {
        try {
            AndroidUtilities.runOnUIThread(() -> payloadCallback.run(ssrc, json));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void onRequestBroadcastPart(long timestamp, long duration, int videoChannel, int quality) {
        requestBroadcastPartCallback.run(timestamp, duration, videoChannel, quality);
    }

    private void onCancelRequestBroadcastPart(long timestamp, int videoChannel, int quality) {
        cancelRequestBroadcastPartCallback.run(timestamp, 0, 0, 0);
    }

    public native void setJoinResponsePayload(String payload);
    public native void prepareForStream();
    public native void resetGroupInstance(boolean set, boolean disconnect);

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

    private static native long makeGroupNativeInstance(NativeInstance instance, String persistentStateFilePath, boolean highQuality, long videoCapturer, boolean screencast, boolean noiseSupression);
    private static native long makeNativeInstance(String version, NativeInstance instance, Instance.Config config, String persistentStateFilePath, Instance.Endpoint[] endpoints, Instance.Proxy proxy, int networkType, Instance.EncryptionKey encryptionKey, VideoSink remoteSink, long videoCapturer, float aspectRatio);
    public static native long createVideoCapturer(VideoSink localSink, int type);
    public static native void setVideoStateCapturer(long videoCapturer, int videoState);
    public static native void switchCameraCapturer(long videoCapturer, boolean front);
    public static native void destroyVideoCapturer(long videoCapturer);

    public native void onMediaDescriptionAvailable(long taskPtr, int[] ssrcs);
    public native void setNoiseSuppressionEnabled(boolean value);
    public native void activateVideoCapturer(long videoCapturer);
    public native void clearVideoCapturer();
    public native long addIncomingVideoOutput(int quality, String endpointId, SsrcGroup[] ssrcGroups, VideoSink remoteSink);
    public native void removeIncomingVideoOutput(long nativeRemoteSink);
    public native void setVideoEndpointQuality(String endpointId, int quality);
    public native void setGlobalServerConfig(String serverConfigJson);
    public native void setBufferSize(int size);
    public native String getVersion();
    public native void setNetworkType(int networkType);
    public native void setMuteMicrophone(boolean muteMicrophone);
    public native void setVolume(int ssrc, double volume);
    public native void setAudioOutputGainControlEnabled(boolean enabled);
    public native void setEchoCancellationStrength(int strength);
    public native String getLastError();
    public native String getDebugInfo();
    public native long getPreferredRelayId();
    public native Instance.TrafficStats getTrafficStats();
    public native byte[] getPersistentState();
    private native void stopNative();
    private native void stopGroupNative();
    public native void setupOutgoingVideo(VideoSink localSink, int type);
    public native void setupOutgoingVideoCreated(long videoCapturer);
    public native void switchCamera(boolean front);
    public native void setVideoState(int videoState);
    public native void onSignalingDataReceive(byte[] data);
    public native void onStreamPartAvailable(long ts, ByteBuffer buffer, int size, long timestamp, int videoChannel, int quality);
    public native boolean hasVideoCapturer();
}
