package org.telegram.messenger.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import androidx.collection.ArrayMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class TgVoip {

    private static final List<String> AVAILABLE_VERSIONS = Arrays.asList("2.4.4", "2.7");
    private static final List<Integer> AVAILABLE_VERSIONS_IDS = Arrays.asList(1, 3);

    private static final int LIB_REVISION = 1;

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

    private static Map<String, Delegate> delegateByVersionArray = new ArrayMap<>(AVAILABLE_VERSIONS.size());
    private static Delegate delegate;

    private static ServerConfig globalServerConfig = new ServerConfig(new JSONObject());
    private static int bufferSize;

    private TgVoip() {
    }

    public static List<String> getAvailableVersions() {
        return AVAILABLE_VERSIONS;
    }

    @SuppressLint("DefaultLocale")
    public static void setNativeVersion(Context context, String version) {
        Delegate delegate = delegateByVersionArray.get(version);
        if (delegate == null) {
            final int versionIndex = AVAILABLE_VERSIONS.indexOf(version);
            if (versionIndex == -1) {
                throw new IllegalArgumentException(String.format("tgvoip version %s is not available (available versions = %s)", version, TextUtils.join(", ", AVAILABLE_VERSIONS)));
            }
            final File dexDir = context.getDir("dex", Context.MODE_PRIVATE);
            final File tgVoipDexFile = new File(dexDir, "libtgvoip.dex");
            if (!checkDexFile(tgVoipDexFile)) {
                tgVoipDexFile.delete();
                BufferedInputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    inputStream = new BufferedInputStream(context.getAssets().open("libtgvoip.dex"));
                    outputStream = new BufferedOutputStream(new FileOutputStream(tgVoipDexFile));
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = inputStream.read(buf, 0, buf.length)) >= 0) {
                        outputStream.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("failed to copy libtgvoip.dex", e);
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    } catch (IOException e) {
                        FileLog.e(e);
                    }
                }
                if (!checkDexFile(tgVoipDexFile)) {
                    throw new IllegalStateException("incorrect libtgvoip.dex checksum after copying from assets");
                }
            }
            final File cacheDir = new File(dexDir, "tgvoip_cache" + version);
            if (cacheDir.exists()) {
                if (!cacheDir.isDirectory()) {
                    cacheDir.delete();
                    cacheDir.mkdirs();
                }
            } else {
                cacheDir.mkdirs();
            }
            ClassLoader classLoader = new DexClassLoader(tgVoipDexFile.getAbsolutePath(), cacheDir.getAbsolutePath(),
                     context.getApplicationInfo().nativeLibraryDir, context.getClassLoader());
            try {
                classLoader.loadClass("org.telegram.messenger.voip.TgVoipNativeLoader")
                        .getDeclaredMethod("initNativeLib", Context.class, int.class)
                        .invoke(null, context, AVAILABLE_VERSIONS_IDS.get(versionIndex));
            } catch (Exception e) {
                throw new IllegalStateException("failed to load tgvoip native library version " + version, e);
            }
            try {
                delegate = (Delegate) classLoader.loadClass("org.telegram.messenger.voip.NativeTgVoipDelegate").newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("failed to create new instance of tgvoip delegate", e);
            }
            delegateByVersionArray.put(version, delegate);
        }
        if (TgVoip.delegate != delegate) {
            TgVoip.delegate = delegate;
            delegate.setGlobalServerConfig(globalServerConfig.jsonObject.toString());
            delegate.setBufferSize(bufferSize);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("tgvoip version changed to " + version);
            }
        }
    }

    private static boolean checkDexFile(File dexFile) {
        if (dexFile != null && dexFile.exists()) {
            try {
                final MessageDigest digest = MessageDigest.getInstance("SHA1");
                final InputStream fileInputStream = new FileInputStream(dexFile);
                final byte[] buffer = new byte[4096];
                int len;
                while ((len = fileInputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, len);
                }
                final String checksum = new String(Base64.encode(digest.digest(), Base64.DEFAULT)).trim();
                return TgVoipDex.getChecksum().equals(checksum);
            } catch (Exception e) {
                FileLog.e(e);
                return false;
            }
        } else {
            return false;
        }
    }

    public static ServerConfig getGlobalServerConfig() {
        return globalServerConfig;
    }

    public static void setGlobalServerConfig(String serverConfigJson) {
        try {
            globalServerConfig = new ServerConfig(new JSONObject(serverConfigJson));
            if (delegate != null) {
                delegate.setGlobalServerConfig(serverConfigJson);
            }
        } catch (JSONException e) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("failed to parse tgvoip server config", e);
            }
        }
    }

    public static Instance makeInstance(Config config, String persistentStateFilePath, Endpoint[] endpoints, Proxy proxy, int networkType, EncryptionKey encryptionKey) {
        checkHasDelegate();
        return delegate.makeInstance(config, persistentStateFilePath, endpoints, proxy, networkType, encryptionKey);
    }

    public static void setBufferSize(int size) {
        bufferSize = size;
        if (delegate != null) {
            delegate.setBufferSize(size);
        }
    }

    public static int getConnectionMaxLayer() {
        return 92;
    }

    public static String getVersion() {
        return delegate != null ? delegate.getVersion() : null;
    }

    private static void checkHasDelegate() {
        if (delegate == null) {
            throw new IllegalStateException("tgvoip version is not set");
        }
    }

    public interface Delegate {

        Instance makeInstance(Config config, String persistentStateFilePath, Endpoint[] endpoints, Proxy proxy, int networkType, EncryptionKey encryptionKey);

        void setGlobalServerConfig(String serverConfigJson);

        void setBufferSize(int size);

        int getConnectionMaxLayer();

        String getVersion();

    }

    public interface Instance {

        void setNetworkType(int networkType);

        void setMuteMicrophone(boolean muteMicrophone);

        void setAudioOutputGainControlEnabled(boolean enabled);

        void setEchoCancellationStrength(int strength);

        String getLastError();

        String getDebugInfo();

        long getPreferredRelayId();

        TrafficStats getTrafficStats();

        byte[] getPersistentState();

        FinalState stop();

        void sendGroupCallKey(byte[] groupCallEncryptionKey);

        void requestCallUpgrade();

        int getPeerCapabilities();

        void setOnStateUpdatedListener(OnStateUpdatedListener listener);

        void setOnSignalBarsUpdatedListener(OnSignalBarsUpdatedListener listener);

    }

    public interface OnStateUpdatedListener {
        void onStateUpdated(int state);
    }

    public interface OnSignalBarsUpdatedListener {
        void onSignalBarsUpdated(int signalBars);
    }

    //region Entities
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
        public final int maxApiLayer;

        public Config(double initializationTimeout, double receiveTimeout, int dataSaving, boolean enableP2p, boolean enableAec, boolean enableNs, boolean enableAgc, boolean enableCallUpgrade, String logPath, int maxApiLayer) {
            this.initializationTimeout = initializationTimeout;
            this.receiveTimeout = receiveTimeout;
            this.dataSaving = dataSaving;
            this.enableP2p = enableP2p;
            this.enableAec = enableAec;
            this.enableNs = enableNs;
            this.enableAgc = enableAgc;
            this.enableCallUpgrade = enableCallUpgrade;
            this.logPath = logPath;
            this.maxApiLayer = maxApiLayer;
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
                    ", maxApiLayer=" + maxApiLayer +
                    '}';
        }
    }

    public static final class Endpoint {

        public final long id;
        public final String ipv4;
        public final String ipv6;
        public final int port;
        public final int type;
        public final byte[] peerTag;

        public Endpoint(long id, String ipv4, String ipv6, int port, int type, byte[] peerTag) {
            this.id = id;
            this.ipv4 = ipv4;
            this.ipv6 = ipv6;
            this.port = port;
            this.type = type;
            this.peerTag = peerTag;
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
        public final String debugLog;
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

    public static final class ServerConfig {

        public final boolean useSystemNs;
        public final boolean useSystemAec;
        public final double hangupUiTimeout;

        private final JSONObject jsonObject;

        private ServerConfig(JSONObject jsonObject) {
            this.jsonObject = jsonObject;
            this.useSystemNs = jsonObject.optBoolean("use_system_ns", true);
            this.useSystemAec = jsonObject.optBoolean("use_system_aec", true);
            this.hangupUiTimeout = jsonObject.optDouble("hangup_ui_timeout", 5);
        }

        public String getString(String key) {
            return getString(key, "");
        }

        public String getString(String key, String fallback) {
            return jsonObject.optString(key, fallback);
        }
    }
    //endregion
}
