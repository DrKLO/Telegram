package org.telegram.messenger.voip;

import com.google.android.exoplayer2.util.Util;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.webrtc.ContextUtils;
import org.webrtc.VideoSink;

import java.util.Arrays;
import java.util.List;

public final class Instance {

    public static final List<String> AVAILABLE_VERSIONS = Arrays.asList("2.4.4", "2.7.7", "5.0.0", "6.0.0", "7.0.0", "8.0.0", "9.0.0", "10.0.0", "11.0.0");

    public static final int AUDIO_STATE_MUTED = 0;
    public static final int AUDIO_STATE_ACTIVE = 1;

    public static final int VIDEO_STATE_INACTIVE = 0;
    public static final int VIDEO_STATE_PAUSED = 1;
    public static final int VIDEO_STATE_ACTIVE = 2;

    //region Constants
    public static final int NET_TYPE_UNKNOWN = 0;
    public static final int NET_TYPE_GPRS = 1;
    public static final int NET_TYPE_EDGE = 2;
    public static final int NET_TYPE_3G = 3;
    public static final int NET_TYPE_HSPA = 4;
    public static final int NET_TYPE_LTE = 5;
    public static final int NET_TYPE_WIFI = 6;
    public static final int NET_TYPE_ETHERNET = 7;
    public static final int NET_TYPE_OTHER_HIGH_SPEED = 8;
    public static final int NET_TYPE_OTHER_LOW_SPEED = 9;
    public static final int NET_TYPE_DIALUP = 10;
    public static final int NET_TYPE_OTHER_MOBILE = 11;
    public static final int ENDPOINT_TYPE_INET = 0;
    public static final int ENDPOINT_TYPE_LAN = 1;
    public static final int ENDPOINT_TYPE_UDP_RELAY = 2;
    public static final int ENDPOINT_TYPE_TCP_RELAY = 3;
    public static final int STATE_WAIT_INIT = 1;
    public static final int STATE_WAIT_INIT_ACK = 2;
    public static final int STATE_ESTABLISHED = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_RECONNECTING = 5;
    public static final int DATA_SAVING_NEVER = 0;
    public static final int DATA_SAVING_MOBILE = 1;
    public static final int DATA_SAVING_ALWAYS = 2;
    public static final int DATA_SAVING_ROAMING = 3;
    public static final int PEER_CAP_GROUP_CALLS = 1;

    // Java-side Errors
    public static final String ERROR_CONNECTION_SERVICE = "ERROR_CONNECTION_SERVICE";
    public static final String ERROR_INSECURE_UPGRADE = "ERROR_INSECURE_UPGRADE";
    public static final String ERROR_LOCALIZED = "ERROR_LOCALIZED";
    public static final String ERROR_PRIVACY = "ERROR_PRIVACY";
    public static final String ERROR_PEER_OUTDATED = "ERROR_PEER_OUTDATED";

    // Native-side Errors
    public static final String ERROR_UNKNOWN = "ERROR_UNKNOWN";
    public static final String ERROR_INCOMPATIBLE = "ERROR_INCOMPATIBLE";
    public static final String ERROR_TIMEOUT = "ERROR_TIMEOUT";
    public static final String ERROR_AUDIO_IO = "ERROR_AUDIO_IO";
    //endregion

    private static ServerConfig globalServerConfig = new ServerConfig(new JSONObject());
    private static int bufferSize;

    private static NativeInstance instance;

    private Instance() {
    }

    public static ServerConfig getGlobalServerConfig() {
        return globalServerConfig;
    }

    public static void setGlobalServerConfig(String serverConfigJson) {
        try {
            globalServerConfig = new ServerConfig(new JSONObject(serverConfigJson));
            if (instance != null) {
                instance.setGlobalServerConfig(serverConfigJson);
            }
        } catch (JSONException e) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("failed to parse tgvoip server config", e);
            }
        }
    }

    public static void destroyInstance() {
        instance = null;
    }

    public static NativeInstance makeInstance(String version, Config config, String persistentStateFilePath, Endpoint[] endpoints, Proxy proxy, int networkType, EncryptionKey encryptionKey, VideoSink remoteSink, long videoCapturer, NativeInstance.AudioLevelsCallback audioLevelsCallback) {
        if (!"2.4.4".equals(version)) {
            ContextUtils.initialize(ApplicationLoader.applicationContext);
        }
        instance = NativeInstance.make(version, config, persistentStateFilePath, endpoints, proxy, networkType, encryptionKey, remoteSink, videoCapturer, audioLevelsCallback);
        setGlobalServerConfig(globalServerConfig.jsonObject.toString());
        setBufferSize(bufferSize);
        return instance;
    }

    public static void setBufferSize(int size) {
        bufferSize = size;
        if (instance != null) {
            instance.setBufferSize(size);
        }
    }

    public static int getConnectionMaxLayer() {
        return 92;
    }

    public static String getVersion() {
        return instance != null ? instance.getVersion() : null;
    }

    private static void checkHasDelegate() {
        if (instance == null) {
            throw new IllegalStateException("tgvoip version is not set");
        }
    }

    public interface OnStateUpdatedListener {
        void onStateUpdated(int state, boolean inTransition);
    }

    public interface OnSignalBarsUpdatedListener {
        void onSignalBarsUpdated(int signalBars);
    }

    public interface OnSignalingDataListener {
        void onSignalingData(byte[] data);
    }

    public interface OnRemoteMediaStateUpdatedListener {
        void onMediaStateUpdated(int audioState, int videoState);
    }

    public static final class Config {

        public final double initializationTimeout;
        public final double receiveTimeout;
        public final int dataSaving;
        public final boolean enableP2p;
        public final boolean enableAec;
        public final boolean enableNs;
        public final boolean enableAgc;
        public final boolean enableCallUpgrade;
        public final String logPath;
        public final String statsLogPath;
        public final int maxApiLayer;
        public final boolean enableSm;

        public Config(double initializationTimeout, double receiveTimeout, int dataSaving, boolean enableP2p, boolean enableAec, boolean enableNs, boolean enableAgc, boolean enableCallUpgrade, boolean enableSm, String logPath, String statsLogPath, int maxApiLayer) {
            this.initializationTimeout = initializationTimeout;
            this.receiveTimeout = receiveTimeout;
            this.dataSaving = dataSaving;
            this.enableP2p = enableP2p;
            this.enableAec = enableAec;
            this.enableNs = enableNs;
            this.enableAgc = enableAgc;
            this.enableCallUpgrade = enableCallUpgrade;
            this.logPath = logPath;
            this.statsLogPath = statsLogPath;
            this.maxApiLayer = maxApiLayer;
            this.enableSm = enableSm;
        }

        @Override
        public String toString() {
            return "Config{" +
                    "initializationTimeout=" + initializationTimeout +
                    ", receiveTimeout=" + receiveTimeout +
                    ", dataSaving=" + dataSaving +
                    ", enableP2p=" + enableP2p +
                    ", enableAec=" + enableAec +
                    ", enableNs=" + enableNs +
                    ", enableAgc=" + enableAgc +
                    ", enableCallUpgrade=" + enableCallUpgrade +
                    ", logPath='" + logPath + '\'' +
                    ", statsLogPath='" + statsLogPath + '\'' +
                    ", maxApiLayer=" + maxApiLayer +
                    ", enableSm=" + enableSm +
                    '}';
        }
    }

    public static final class Endpoint {

        public final boolean isRtc;
        public final long id;
        public final String ipv4;
        public final String ipv6;
        public final int port;
        public final int type;
        public final byte[] peerTag;
        public final boolean turn;
        public final boolean stun;
        public final String username;
        public final String password;
        public final boolean tcp;
        public int reflectorId;

        public Endpoint(boolean isRtc, long id, String ipv4, String ipv6, int port, int type, byte[] peerTag, boolean turn, boolean stun, String username, String password, boolean tcp) {
            this.isRtc = isRtc;
            this.id = id;
            this.ipv4 = ipv4;
            this.ipv6 = ipv6;
            this.port = port;
            this.type = type;
            this.peerTag = peerTag;
            this.turn = turn;
            this.stun = stun;
            if (isRtc) {
                this.username = username;
                this.password = password;
            } else if (peerTag != null) {
                this.username = "reflector";
                this.password = Util.toHexString(peerTag);
            } else {
                this.username = null;
                this.password = null;
            }
            this.tcp = tcp;
        }

        @Override
        public String toString() {
            return "Endpoint{" +
                    "id=" + id +
                    ", ipv4='" + ipv4 + '\'' +
                    ", ipv6='" + ipv6 + '\'' +
                    ", port=" + port +
                    ", type=" + type +
                    ", peerTag=" + Arrays.toString(peerTag) +
                    ", turn=" + turn +
                    ", stun=" + stun +
                    ", username=" + username +
                    ", password=" + password +
                    ", tcp=" + tcp +
                    '}';
        }
    }

    public static final class Proxy {

        public final String host;
        public final int port;
        public final String login;
        public final String password;

        public Proxy(String host, int port, String login, String password) {
            this.host = host;
            this.port = port;
            this.login = login;
            this.password = password;
        }

        @Override
        public String toString() {
            return "Proxy{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    ", login='" + login + '\'' +
                    ", password='" + password + '\'' +
                    '}';
        }
    }

    public static final class EncryptionKey {

        public final byte[] value;
        public final boolean isOutgoing;

        public EncryptionKey(byte[] value, boolean isOutgoing) {
            this.value = value;
            this.isOutgoing = isOutgoing;
        }

        @Override
        public String toString() {
            return "EncryptionKey{" +
                    "value=" + Arrays.toString(value) +
                    ", isOutgoing=" + isOutgoing +
                    '}';
        }
    }

    public static final class FinalState {

        public final byte[] persistentState;
        public String debugLog;
        public final TrafficStats trafficStats;
        public final boolean isRatingSuggested;

        public FinalState(byte[] persistentState, String debugLog, TrafficStats trafficStats, boolean isRatingSuggested) {
            this.persistentState = persistentState;
            this.debugLog = debugLog;
            this.trafficStats = trafficStats;
            this.isRatingSuggested = isRatingSuggested;
        }

        @Override
        public String toString() {
            return "FinalState{" +
                    "persistentState=" + Arrays.toString(persistentState) +
                    ", debugLog='" + debugLog + '\'' +
                    ", trafficStats=" + trafficStats +
                    ", isRatingSuggested=" + isRatingSuggested +
                    '}';
        }
    }

    public static final class TrafficStats {

        public final long bytesSentWifi;
        public final long bytesReceivedWifi;
        public final long bytesSentMobile;
        public final long bytesReceivedMobile;

        public TrafficStats(long bytesSentWifi, long bytesReceivedWifi, long bytesSentMobile, long bytesReceivedMobile) {
            this.bytesSentWifi = bytesSentWifi;
            this.bytesReceivedWifi = bytesReceivedWifi;
            this.bytesSentMobile = bytesSentMobile;
            this.bytesReceivedMobile = bytesReceivedMobile;
        }

        @Override
        public String toString() {
            return "TrafficStats{" +
                    "bytesSentWifi=" + bytesSentWifi +
                    ", bytesReceivedWifi=" + bytesReceivedWifi +
                    ", bytesSentMobile=" + bytesSentMobile +
                    ", bytesReceivedMobile=" + bytesReceivedMobile +
                    '}';
        }
    }

    public static final class Fingerprint {

        public final String hash;
        public final String setup;
        public final String fingerprint;

        public Fingerprint(String hash, String setup, String fingerprint) {
            this.hash = hash;
            this.setup = setup;
            this.fingerprint = fingerprint;
        }

        @Override
        public String toString() {
            return "Fingerprint{" +
                    "hash=" + hash +
                    ", setup=" + setup +
                    ", fingerprint=" + fingerprint +
                    '}';
        }
    }

    public static final class Candidate {

        public final String port;
        public final String protocol;
        public final String network;
        public final String generation;
        public final String id;
        public final String component;
        public final String foundation;
        public final String priority;
        public final String ip;
        public final String type;
        public final String tcpType;
        public final String relAddr;
        public final String relPort;

        public Candidate(String port, String protocol, String network, String generation, String id, String component, String foundation, String priority, String ip, String type, String tcpType, String relAddr, String relPort) {
            this.port = port;
            this.protocol = protocol;
            this.network = network;
            this.generation = generation;
            this.id = id;
            this.component = component;
            this.foundation = foundation;
            this.priority = priority;
            this.ip = ip;
            this.type = type;
            this.tcpType = tcpType;
            this.relAddr = relAddr;
            this.relPort = relPort;
        }

        @Override
        public String toString() {
            return "Candidate{" +
                    "port=" + port +
                    ", protocol=" + protocol +
                    ", network=" + network +
                    ", generation=" + generation +
                    ", id=" + id +
                    ", component=" + component +
                    ", foundation=" + foundation +
                    ", priority=" + priority +
                    ", ip=" + ip +
                    ", type=" + type +
                    ", tcpType=" + tcpType +
                    ", relAddr=" + relAddr +
                    ", relPort=" + relPort +
                    '}';
        }
    }

    public static final class ServerConfig {

        public final boolean useSystemNs;
        public final boolean useSystemAec;
        public final boolean enableStunMarking;
        public final double hangupUiTimeout;

        public final boolean enable_vp8_encoder;
        public final boolean enable_vp8_decoder;
        public final boolean enable_vp9_encoder;
        public final boolean enable_vp9_decoder;
        public final boolean enable_h265_encoder;
        public final boolean enable_h265_decoder;
        public final boolean enable_h264_encoder;
        public final boolean enable_h264_decoder;

        private final JSONObject jsonObject;

        private ServerConfig(JSONObject jsonObject) {
            this.jsonObject = jsonObject;
            this.useSystemNs = jsonObject.optBoolean("use_system_ns", true);
            this.useSystemAec = jsonObject.optBoolean("use_system_aec", true);
            this.enableStunMarking = jsonObject.optBoolean("voip_enable_stun_marking", false);
            this.hangupUiTimeout = jsonObject.optDouble("hangup_ui_timeout", 5);

            this.enable_vp8_encoder = jsonObject.optBoolean("enable_vp8_encoder", true);
            this.enable_vp8_decoder = jsonObject.optBoolean("enable_vp8_decoder", true);
            this.enable_vp9_encoder = jsonObject.optBoolean("enable_vp9_encoder", true);
            this.enable_vp9_decoder = jsonObject.optBoolean("enable_vp9_decoder", true);
            this.enable_h265_encoder = jsonObject.optBoolean("enable_h265_encoder", true);
            this.enable_h265_decoder = jsonObject.optBoolean("enable_h265_decoder", true);
            this.enable_h264_encoder = jsonObject.optBoolean("enable_h264_encoder", true);
            this.enable_h264_decoder = jsonObject.optBoolean("enable_h264_decoder", true);
        }

        public String getString(String key) {
            return getString(key, "");
        }

        public String getString(String key, String fallback) {
            return jsonObject.optString(key, fallback);
        }
    }
}
