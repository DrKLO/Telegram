package org.telegram.tgnet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.EmuDetector;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.KeepAliveJob;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsManager extends BaseController {

    public final static int ConnectionTypeGeneric = 1;
    public final static int ConnectionTypeDownload = 2;
    public final static int ConnectionTypeUpload = 4;
    public final static int ConnectionTypePush = 8;
    public final static int ConnectionTypeDownload2 = ConnectionTypeDownload | (1 << 16);

    public final static int FileTypePhoto = 0x01000000;
    public final static int FileTypeVideo = 0x02000000;
    public final static int FileTypeAudio = 0x03000000;
    public final static int FileTypeFile = 0x04000000;

    public final static int RequestFlagEnableUnauthorized = 1;
    public final static int RequestFlagFailOnServerErrors = 2;
    public final static int RequestFlagCanCompress = 4;
    public final static int RequestFlagWithoutLogin = 8;
    public final static int RequestFlagTryDifferentDc = 16;
    public final static int RequestFlagForceDownload = 32;
    public final static int RequestFlagInvokeAfter = 64;
    public final static int RequestFlagNeedQuickAck = 128;

    public final static int ConnectionStateConnecting = 1;
    public final static int ConnectionStateWaitingForNetwork = 2;
    public final static int ConnectionStateConnected = 3;
    public final static int ConnectionStateConnectingToProxy = 4;
    public final static int ConnectionStateUpdating = 5;

    public final static byte USE_IPV4_ONLY = 0;
    public final static byte USE_IPV6_ONLY = 1;
    public final static byte USE_IPV4_IPV6_RANDOM = 2;

    private static long lastDnsRequestTime;

    public final static int DEFAULT_DATACENTER_ID = Integer.MAX_VALUE;

    private long lastPauseTime = System.currentTimeMillis();
    private boolean appPaused = true;
    private boolean isUpdating;
    private int connectionState;
    private AtomicInteger lastRequestToken = new AtomicInteger(1);
    private int appResumeCount;

    private static AsyncTask currentTask;

    private static HashMap<String, ResolveHostByNameTask> resolvingHostnameTasks = new HashMap<>();

    public static final Executor DNS_THREAD_POOL_EXECUTOR;
    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<>(128);
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "DnsAsyncTask #" + mCount.getAndIncrement());
        }
    };

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        DNS_THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    private static class ResolvedDomain {

        public ArrayList<String> addresses;
        long ttl;

        public ResolvedDomain(ArrayList<String> a, long t) {
            addresses = a;
            ttl = t;
        }

        public String getAddress() {
            return addresses.get(Utilities.random.nextInt(addresses.size()));
        }
    }

    private static HashMap<String, ResolvedDomain> dnsCache = new HashMap<>();

    private static int lastClassGuid = 1;
    
    private static volatile ConnectionsManager[] Instance = new ConnectionsManager[UserConfig.MAX_ACCOUNT_COUNT];
    public static ConnectionsManager getInstance(int num) {
        ConnectionsManager localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ConnectionsManager.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ConnectionsManager(num);
                }
            }
        }
        return localInstance;
    }

    public ConnectionsManager(int instance) {
        super(instance);
        connectionState = native_getConnectionState(currentAccount);
        String deviceModel;
        String systemLangCode;
        String langCode;
        String appVersion;
        String systemVersion;
        File config = ApplicationLoader.getFilesDirFixed();
        if (instance != 0) {
            config = new File(config, "account" + instance);
            config.mkdirs();
        }
        String configPath = config.toString();
        boolean enablePushConnection = isPushConnectionEnabled();
        try {
            systemLangCode = LocaleController.getSystemLocaleStringIso639().toLowerCase();
            langCode = LocaleController.getLocaleStringIso639().toLowerCase();
            deviceModel = Build.MANUFACTURER + Build.MODEL;
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            appVersion = pInfo.versionName + " (" + pInfo.versionCode + ")";
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                appVersion += " pbeta";
            } else if (BuildVars.DEBUG_VERSION) {
                appVersion += " beta";
            }
            systemVersion = "SDK " + Build.VERSION.SDK_INT;
        } catch (Exception e) {
            systemLangCode = "en";
            langCode = "";
            deviceModel = "Android unknown";
            appVersion = "App version unknown";
            systemVersion = "SDK " + Build.VERSION.SDK_INT;
        }
        if (systemLangCode.trim().length() == 0) {
            systemLangCode = "en";
        }
        if (deviceModel.trim().length() == 0) {
            deviceModel = "Android unknown";
        }
        if (appVersion.trim().length() == 0) {
            appVersion = "App version unknown";
        }
        if (systemVersion.trim().length() == 0) {
            systemVersion = "SDK Unknown";
        }
        getUserConfig().loadConfig();
        String pushString = getRegId();
        String fingerprint = AndroidUtilities.getCertificateSHA256Fingerprint();

        int timezoneOffset = (TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings()) / 1000;

        init(BuildVars.BUILD_VERSION, TLRPC.LAYER, BuildVars.APP_ID, deviceModel, systemVersion, appVersion, langCode, systemLangCode, configPath, FileLog.getNetworkLogPath(), pushString, fingerprint, timezoneOffset, getUserConfig().getClientUserId(), enablePushConnection);
    }

    private String getRegId() {
        String pushString = SharedConfig.pushString;
        if (TextUtils.isEmpty(pushString) && !TextUtils.isEmpty(SharedConfig.pushStringStatus)) {
            pushString = SharedConfig.pushStringStatus;
        }
        if (TextUtils.isEmpty(pushString)) {
            pushString = SharedConfig.pushStringStatus = "__FIREBASE_GENERATING_SINCE_" + getCurrentTime() + "__";
        }
        return pushString;
    }

    public boolean isPushConnectionEnabled() {
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (preferences.contains("pushConnection")) {
            return preferences.getBoolean("pushConnection", true);
        } else {
            return MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("backgroundConnection", false);
        }
    }

    public long getCurrentTimeMillis() {
        return native_getCurrentTimeMillis(currentAccount);
    }

    public int getCurrentTime() {
        return native_getCurrentTime(currentAccount);
    }

    public int getCurrentDatacenterId() {
        return native_getCurrentDatacenterId(currentAccount);
    }

    public int getTimeDifference() {
        return native_getTimeDifference(currentAccount);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock) {
        return sendRequest(object, completionBlock, null, 0);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock, int flags) {
        return sendRequest(object, completionBlock, null, null, null, flags, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock, int flags, int connetionType) {
        return sendRequest(object, completionBlock, null, null, null, flags, DEFAULT_DATACENTER_ID, connetionType, true);
    }

    public int sendRequest(TLObject object, RequestDelegateTimestamp completionBlock, int flags, int connetionType, int datacenterId) {
        return sendRequest(object, null, completionBlock, null, null, flags, datacenterId, connetionType, true);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock, QuickAckDelegate quickAckBlock, int flags) {
        return sendRequest(object, completionBlock, null, quickAckBlock, null, flags, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, final QuickAckDelegate onQuickAck, final WriteToSocketDelegate onWriteToSocket, final int flags, final int datacenterId, final int connetionType, final boolean immediate) {
        return sendRequest(object, onComplete, null, onQuickAck, onWriteToSocket, flags, datacenterId, connetionType, immediate);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, final RequestDelegateTimestamp onCompleteTimestamp, final QuickAckDelegate onQuickAck, final WriteToSocketDelegate onWriteToSocket, final int flags, final int datacenterId, final int connetionType, final boolean immediate) {
        final int requestToken = lastRequestToken.getAndIncrement();
        Utilities.stageQueue.postRunnable(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("send request " + object + " with token = " + requestToken);
            }
            try {
                NativeByteBuffer buffer = new NativeByteBuffer(object.getObjectSize());
                object.serializeToStream(buffer);
                object.freeResources();

                native_sendRequest(currentAccount, buffer.address, (response, errorCode, errorText, networkType, timestamp) -> {
                    try {
                        TLObject resp = null;
                        TLRPC.TL_error error = null;
                        if (response != 0) {
                            NativeByteBuffer buff = NativeByteBuffer.wrap(response);
                            buff.reused = true;
                            resp = object.deserializeResponse(buff, buff.readInt32(true), true);
                        } else if (errorText != null) {
                            error = new TLRPC.TL_error();
                            error.code = errorCode;
                            error.text = errorText;
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.e(object + " got error " + error.code + " " + error.text);
                            }
                        }
                        if (resp != null) {
                            resp.networkType = networkType;
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("java received " + resp + " error = " + error);
                        }
                        final TLObject finalResponse = resp;
                        final TLRPC.TL_error finalError = error;
                        Utilities.stageQueue.postRunnable(() -> {
                            if (onComplete != null) {
                                onComplete.run(finalResponse, finalError);
                            } else if (onCompleteTimestamp != null) {
                                onCompleteTimestamp.run(finalResponse, finalError, timestamp);
                            }
                            if (finalResponse != null) {
                                finalResponse.freeResources();
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }, onQuickAck, onWriteToSocket, flags, datacenterId, connetionType, immediate, requestToken);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        return requestToken;
    }

    public void cancelRequest(int token, boolean notifyServer) {
        native_cancelRequest(currentAccount, token, notifyServer);
    }

    public void cleanup(boolean resetKeys) {
        native_cleanUp(currentAccount, resetKeys);
    }

    public void cancelRequestsForGuid(int guid) {
        native_cancelRequestsForGuid(currentAccount, guid);
    }

    public void bindRequestToGuid(int requestToken, int guid) {
        native_bindRequestToGuid(currentAccount, requestToken, guid);
    }

    public void applyDatacenterAddress(int datacenterId, String ipAddress, int port) {
        native_applyDatacenterAddress(currentAccount, datacenterId, ipAddress, port);
    }

    public int getConnectionState() {
        if (connectionState == ConnectionStateConnected && isUpdating) {
            return ConnectionStateUpdating;
        }
        return connectionState;
    }

    public void setUserId(long id) {
        native_setUserId(currentAccount, id);
    }

    public void checkConnection() {
        native_setIpStrategy(currentAccount, getIpStrategy());
        native_setNetworkAvailable(currentAccount, ApplicationLoader.isNetworkOnline(), ApplicationLoader.getCurrentNetworkType(), ApplicationLoader.isConnectionSlow());
    }

    public void setPushConnectionEnabled(boolean value) {
        native_setPushConnectionEnabled(currentAccount, value);
    }

    public void init(int version, int layer, int apiId, String deviceModel, String systemVersion, String appVersion, String langCode, String systemLangCode, String configPath, String logPath, String regId, String cFingerprint, int timezoneOffset, long userId, boolean enablePushConnection) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String proxyAddress = preferences.getString("proxy_ip", "");
        String proxyUsername = preferences.getString("proxy_user", "");
        String proxyPassword = preferences.getString("proxy_pass", "");
        String proxySecret = preferences.getString("proxy_secret", "");
        int proxyPort = preferences.getInt("proxy_port", 1080);

        if (preferences.getBoolean("proxy_enabled", false) && !TextUtils.isEmpty(proxyAddress)) {
            native_setProxySettings(currentAccount, proxyAddress, proxyPort, proxyUsername, proxyPassword, proxySecret);
        }
        String installer = "";
        try {
            installer = ApplicationLoader.applicationContext.getPackageManager().getInstallerPackageName(ApplicationLoader.applicationContext.getPackageName());
        } catch (Throwable ignore) {

        }
        if (installer == null) {
            installer = "";
        }
        String packageId = "";
        try {
            packageId = ApplicationLoader.applicationContext.getPackageName();
        } catch (Throwable ignore) {

        }
        if (packageId == null) {
            packageId = "";
        }

        native_init(currentAccount, version, layer, apiId, deviceModel, systemVersion, appVersion, langCode, systemLangCode, configPath, logPath, regId, cFingerprint, installer, packageId, timezoneOffset, userId, enablePushConnection, ApplicationLoader.isNetworkOnline(), ApplicationLoader.getCurrentNetworkType());
        checkConnection();
    }

    public static void setLangCode(String langCode) {
        langCode = langCode.replace('_', '-').toLowerCase();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            native_setLangCode(a, langCode);
        }
    }

    public static void setRegId(String regId, String status) {
        String pushString = regId;
        if (TextUtils.isEmpty(pushString) && !TextUtils.isEmpty(status)) {
            pushString = status;
        }
        if (TextUtils.isEmpty(pushString)) {
            pushString = SharedConfig.pushStringStatus = "__FIREBASE_GENERATING_SINCE_" + getInstance(0).getCurrentTime() + "__";
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            native_setRegId(a, pushString);
        }
    }

    public static void setSystemLangCode(String langCode) {
        langCode = langCode.replace('_', '-').toLowerCase();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            native_setSystemLangCode(a, langCode);
        }
    }

    public void switchBackend(boolean restart) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().remove("language_showed2").commit();
        native_switchBackend(currentAccount, restart);
    }

    public boolean isTestBackend() {
        return native_isTestBackend(currentAccount) != 0;
    }

    public void resumeNetworkMaybe() {
        native_resumeNetwork(currentAccount, true);
    }

    public void updateDcSettings() {
        native_updateDcSettings(currentAccount);
    }

    public long getPauseTime() {
        return lastPauseTime;
    }

    public long checkProxy(String address, int port, String username, String password, String secret, RequestTimeDelegate requestTimeDelegate) {
        if (TextUtils.isEmpty(address)) {
            return 0;
        }
        if (address == null) {
            address = "";
        }
        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
        if (secret == null) {
            secret = "";
        }
        return native_checkProxy(currentAccount, address, port, username, password, secret, requestTimeDelegate);
    }

    public void setAppPaused(final boolean value, final boolean byScreenState) {
        if (!byScreenState) {
            appPaused = value;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("app paused = " + value);
            }
            if (value) {
                appResumeCount--;
            } else {
                appResumeCount++;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("app resume count " + appResumeCount);
            }
            if (appResumeCount < 0) {
                appResumeCount = 0;
            }
        }
        if (appResumeCount == 0) {
            if (lastPauseTime == 0) {
                lastPauseTime = System.currentTimeMillis();
            }
            native_pauseNetwork(currentAccount);
        } else {
            if (appPaused) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("reset app pause time");
            }
            if (lastPauseTime != 0 && System.currentTimeMillis() - lastPauseTime > 5000) {
                getContactsController().checkContacts();
            }
            lastPauseTime = 0;
            native_resumeNetwork(currentAccount, false);
        }
    }

    public static void onUnparsedMessageReceived(long address, final int currentAccount) {
        try {
            NativeByteBuffer buff = NativeByteBuffer.wrap(address);
            buff.reused = true;
            int constructor = buff.readInt32(true);
            final TLObject message = TLClassStore.Instance().TLdeserialize(buff, constructor, true);
            if (message instanceof TLRPC.Updates) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("java received " + message);
                }
                KeepAliveJob.finishJob();
                Utilities.stageQueue.postRunnable(() -> AccountInstance.getInstance(currentAccount).getMessagesController().processUpdates((TLRPC.Updates) message, false));
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(String.format("java received unknown constructor 0x%x", constructor));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onUpdate(final int currentAccount) {
        Utilities.stageQueue.postRunnable(() -> AccountInstance.getInstance(currentAccount).getMessagesController().updateTimerProc());
    }

    public static void onSessionCreated(final int currentAccount) {
        Utilities.stageQueue.postRunnable(() -> AccountInstance.getInstance(currentAccount).getMessagesController().getDifference());
    }

    public static void onConnectionStateChanged(final int state, final int currentAccount) {
        AndroidUtilities.runOnUIThread(() -> {
            getInstance(currentAccount).connectionState = state;
            AccountInstance.getInstance(currentAccount).getNotificationCenter().postNotificationName(NotificationCenter.didUpdateConnectionState);
        });
    }

    public static void onLogout(final int currentAccount) {
        AndroidUtilities.runOnUIThread(() -> {
            AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
            if (accountInstance.getUserConfig().getClientUserId() != 0) {
                accountInstance.getUserConfig().clearConfig();
                accountInstance.getMessagesController().performLogout(0);
            }
        });
    }

    public static int getInitFlags() {
        int flags = 0;
        EmuDetector detector = EmuDetector.with(ApplicationLoader.applicationContext);
        if (detector.detect()) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("detected emu");
            }
            flags |= 1024;
        }
        return flags;
    }

    public static void onBytesSent(int amount, int networkType, final int currentAccount) {
        try {
            AccountInstance.getInstance(currentAccount).getStatsController().incrementSentBytesCount(networkType, StatsController.TYPE_TOTAL, amount);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onRequestNewServerIpAndPort(final int second, final int currentAccount) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean networkOnline = ApplicationLoader.isNetworkOnline();
            Utilities.stageQueue.postRunnable(() -> {
                if (currentTask != null || second == 0 && Math.abs(lastDnsRequestTime - System.currentTimeMillis()) < 10000 || !networkOnline) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("don't start task, current task = " + currentTask + " next task = " + second + " time diff = " + Math.abs(lastDnsRequestTime - System.currentTimeMillis()) + " network = " + ApplicationLoader.isNetworkOnline());
                    }
                    return;
                }
                lastDnsRequestTime = System.currentTimeMillis();
                if (second == 3) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start mozilla txt task");
                    }
                    MozillaDnsLoadTask task = new MozillaDnsLoadTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                } else if (second == 2) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start google txt task");
                    }
                    GoogleDnsLoadTask task = new GoogleDnsLoadTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                } else if (second == 1) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start dns txt task");
                    }
                    DnsTxtLoadTask task = new DnsTxtLoadTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start firebase task");
                    }
                    FirebaseTask task = new FirebaseTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                }
            });
        });
    }

    public static void onProxyError() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needShowAlert, 3));
    }

    public static void getHostByName(String hostName, long address) {
        AndroidUtilities.runOnUIThread(() -> {
            ResolvedDomain resolvedDomain = dnsCache.get(hostName);
            if (resolvedDomain != null && SystemClock.elapsedRealtime() - resolvedDomain.ttl < 5 * 60 * 1000) {
                native_onHostNameResolved(hostName, address, resolvedDomain.getAddress());
            } else {
                ResolveHostByNameTask task = resolvingHostnameTasks.get(hostName);
                if (task == null) {
                    task = new ResolveHostByNameTask(hostName);
                    try {
                        task.executeOnExecutor(DNS_THREAD_POOL_EXECUTOR, null, null, null);
                    } catch (Throwable e) {
                        FileLog.e(e);
                        native_onHostNameResolved(hostName, address, "");
                        return;
                    }
                    resolvingHostnameTasks.put(hostName, task);
                }
                task.addAddress(address);
            }
        });
    }

    public static void onBytesReceived(int amount, int networkType, final int currentAccount) {
        try {
            StatsController.getInstance(currentAccount).incrementReceivedBytesCount(networkType, StatsController.TYPE_TOTAL, amount);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onUpdateConfig(long address, final int currentAccount) {
        try {
            NativeByteBuffer buff = NativeByteBuffer.wrap(address);
            buff.reused = true;
            final TLRPC.TL_config message = TLRPC.TL_config.TLdeserialize(buff, buff.readInt32(true), true);
            if (message != null) {
                Utilities.stageQueue.postRunnable(() -> AccountInstance.getInstance(currentAccount).getMessagesController().updateConfig(message));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onInternalPushReceived(final int currentAccount) {
        KeepAliveJob.startJob();
    }

    public static void setProxySettings(boolean enabled, String address, int port, String username, String password, String secret) {
        if (address == null) {
            address = "";
        }
        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
        if (secret == null) {
            secret = "";
        }

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (enabled && !TextUtils.isEmpty(address)) {
                native_setProxySettings(a, address, port, username, password, secret);
            } else {
                native_setProxySettings(a, "", 1080, "", "", "");
            }
            AccountInstance accountInstance = AccountInstance.getInstance(a);
            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }
    }

    public static native void native_switchBackend(int currentAccount, boolean restart);
    public static native int native_isTestBackend(int currentAccount);
    public static native void native_pauseNetwork(int currentAccount);
    public static native void native_setIpStrategy(int currentAccount, byte value);
    public static native void native_updateDcSettings(int currentAccount);
    public static native void native_setNetworkAvailable(int currentAccount, boolean value, int networkType, boolean slow);
    public static native void native_resumeNetwork(int currentAccount, boolean partial);
    public static native long native_getCurrentTimeMillis(int currentAccount);
    public static native int native_getCurrentTime(int currentAccount);
    public static native int native_getCurrentDatacenterId(int currentAccount);
    public static native int native_getTimeDifference(int currentAccount);
    public static native void native_sendRequest(int currentAccount, long object, RequestDelegateInternal onComplete, QuickAckDelegate onQuickAck, WriteToSocketDelegate onWriteToSocket, int flags, int datacenterId, int connetionType, boolean immediate, int requestToken);
    public static native void native_cancelRequest(int currentAccount, int token, boolean notifyServer);
    public static native void native_cleanUp(int currentAccount, boolean resetKeys);
    public static native void native_cancelRequestsForGuid(int currentAccount, int guid);
    public static native void native_bindRequestToGuid(int currentAccount, int requestToken, int guid);
    public static native void native_applyDatacenterAddress(int currentAccount, int datacenterId, String ipAddress, int port);
    public static native int native_getConnectionState(int currentAccount);
    public static native void native_setUserId(int currentAccount, long id);
    public static native void native_init(int currentAccount, int version, int layer, int apiId, String deviceModel, String systemVersion, String appVersion, String langCode, String systemLangCode, String configPath, String logPath, String regId, String cFingerprint, String installer, String packageId, int timezoneOffset, long userId, boolean enablePushConnection, boolean hasNetwork, int networkType);
    public static native void native_setProxySettings(int currentAccount, String address, int port, String username, String password, String secret);
    public static native void native_setLangCode(int currentAccount, String langCode);
    public static native void native_setRegId(int currentAccount, String regId);
    public static native void native_setSystemLangCode(int currentAccount, String langCode);
    public static native void native_seSystemLangCode(int currentAccount, String langCode);
    public static native void native_setJava(boolean useJavaByteBuffers);
    public static native void native_setPushConnectionEnabled(int currentAccount, boolean value);
    public static native void native_applyDnsConfig(int currentAccount, long address, String phone, int date);
    public static native long native_checkProxy(int currentAccount, String address, int port, String username, String password, String secret, RequestTimeDelegate requestTimeDelegate);
    public static native void native_onHostNameResolved(String host, long address, String ip);

    public static int generateClassGuid() {
        return lastClassGuid++;
    }

    public void setIsUpdating(final boolean value) {
        AndroidUtilities.runOnUIThread(() -> {
            if (isUpdating == value) {
                return;
            }
            isUpdating = value;
            if (connectionState == ConnectionStateConnected) {
                AccountInstance.getInstance(currentAccount).getNotificationCenter().postNotificationName(NotificationCenter.didUpdateConnectionState);
            }
        });
    }

    @SuppressLint("NewApi")
    protected static byte getIpStrategy() {
        if (Build.VERSION.SDK_INT < 19) {
            return USE_IPV4_ONLY;
        }
        if (BuildVars.LOGS_ENABLED) {
            try {
                NetworkInterface networkInterface;
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    networkInterface = networkInterfaces.nextElement();
                    if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.getInterfaceAddresses().isEmpty()) {
                        continue;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("valid interface: " + networkInterface);
                    }
                    List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                    for (int a = 0; a < interfaceAddresses.size(); a++) {
                        InterfaceAddress address = interfaceAddresses.get(a);
                        InetAddress inetAddress = address.getAddress();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("address: " + inetAddress.getHostAddress());
                        }
                        if (inetAddress.isLinkLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isMulticastAddress()) {
                            continue;
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("address is good");
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        try {
            NetworkInterface networkInterface;
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            boolean hasIpv4 = false;
            boolean hasIpv6 = false;
            boolean hasStrangeIpv4 = false;
            while (networkInterfaces.hasMoreElements()) {
                networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                for (int a = 0; a < interfaceAddresses.size(); a++) {
                    InterfaceAddress address = interfaceAddresses.get(a);
                    InetAddress inetAddress = address.getAddress();
                    if (inetAddress.isLinkLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isMulticastAddress()) {
                        continue;
                    }
                    if (inetAddress instanceof Inet6Address) {
                        hasIpv6 = true;
                    } else if (inetAddress instanceof Inet4Address) {
                        String addrr = inetAddress.getHostAddress();
                        if (!addrr.startsWith("192.0.0.")) {
                            hasIpv4 = true;
                        } else {
                            hasStrangeIpv4 = true;
                        }
                    }
                }
            }
            if (hasIpv6) {
                if (hasStrangeIpv4) {
                    return USE_IPV4_IPV6_RANDOM;
                }
                if (!hasIpv4) {
                    return USE_IPV6_ONLY;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        return USE_IPV4_ONLY;
    }

    private static class ResolveHostByNameTask extends AsyncTask<Void, Void, ResolvedDomain> {

        private ArrayList<Long> addresses = new ArrayList<>();
        private String currentHostName;

        public ResolveHostByNameTask(String hostName) {
            super();
            currentHostName = hostName;
        }

        public void addAddress(long address) {
            if (addresses.contains(address)) {
                return;
            }
            addresses.add(address);
        }

        protected ResolvedDomain doInBackground(Void... voids) {
            ByteArrayOutputStream outbuf = null;
            InputStream httpConnectionStream = null;
            boolean done = false;
            try {
                URL downloadUrl = new URL("https://www.google.com/resolve?name=" + currentHostName + "&type=A");
                URLConnection httpConnection = downloadUrl.openConnection();
                httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                httpConnection.addRequestProperty("Host", "dns.google.com");
                httpConnection.setConnectTimeout(1000);
                httpConnection.setReadTimeout(2000);
                httpConnection.connect();
                httpConnectionStream = httpConnection.getInputStream();

                outbuf = new ByteArrayOutputStream();

                byte[] data = new byte[1024 * 32];
                while (true) {
                    int read = httpConnectionStream.read(data);
                    if (read > 0) {
                        outbuf.write(data, 0, read);
                    } else if (read == -1) {
                        break;
                    } else {
                        break;
                    }
                }

                JSONObject jsonObject = new JSONObject(new String(outbuf.toByteArray()));
                if (jsonObject.has("Answer")) {
                    JSONArray array = jsonObject.getJSONArray("Answer");
                    int len = array.length();
                    if (len > 0) {
                        ArrayList<String> addresses = new ArrayList<>(len);
                        for (int a = 0; a < len; a++) {
                            addresses.add(array.getJSONObject(a).getString("data"));
                        }
                        return new ResolvedDomain(addresses, SystemClock.elapsedRealtime());
                    }
                }
                done = true;
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    if (httpConnectionStream != null) {
                        httpConnectionStream.close();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    if (outbuf != null) {
                        outbuf.close();
                    }
                } catch (Exception ignore) {

                }
            }
            if (!done) {
                try {
                    InetAddress address = InetAddress.getByName(currentHostName);
                    ArrayList<String> addresses = new ArrayList<>(1);
                    addresses.add(address.getHostAddress());
                    return new ResolvedDomain(addresses, SystemClock.elapsedRealtime());
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final ResolvedDomain result) {
            if (result != null) {
                dnsCache.put(currentHostName, result);
                for (int a = 0, N = addresses.size(); a < N; a++) {
                    native_onHostNameResolved(currentHostName, addresses.get(a), result.getAddress());
                }
            } else {
                for (int a = 0, N = addresses.size(); a < N; a++) {
                    native_onHostNameResolved(currentHostName, addresses.get(a), "");
                }
            }
            resolvingHostnameTasks.remove(currentHostName);
        }
    }

    private static class DnsTxtLoadTask extends AsyncTask<Void, Void, NativeByteBuffer> {

        private int currentAccount;
        private int responseDate;

        public DnsTxtLoadTask(int instance) {
            super();
            currentAccount = instance;
        }

        protected NativeByteBuffer doInBackground(Void... voids) {
            ByteArrayOutputStream outbuf = null;
            InputStream httpConnectionStream = null;
            for (int i = 0; i < 3; i++) {
                try {
                    String googleDomain;
                    if (i == 0) {
                        googleDomain = "www.google.com";
                    } else if (i == 1) {
                        googleDomain = "www.google.ru";
                    } else {
                        googleDomain = "google.com";
                    }
                    String domain = native_isTestBackend(currentAccount) != 0 ? "tapv3.stel.com" : AccountInstance.getInstance(currentAccount).getMessagesController().dcDomainName;
                    int len = Utilities.random.nextInt(116) + 13;
                    final String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

                    StringBuilder padding = new StringBuilder(len);
                    for (int a = 0; a < len; a++) {
                        padding.append(characters.charAt(Utilities.random.nextInt(characters.length())));
                    }
                    URL downloadUrl = new URL("https://" + googleDomain + "/resolve?name=" + domain + "&type=ANY&random_padding=" + padding);
                    URLConnection httpConnection = downloadUrl.openConnection();
                    httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                    httpConnection.addRequestProperty("Host", "dns.google.com");
                    httpConnection.setConnectTimeout(5000);
                    httpConnection.setReadTimeout(5000);
                    httpConnection.connect();
                    httpConnectionStream = httpConnection.getInputStream();
                    responseDate = (int) (httpConnection.getDate() / 1000);

                    outbuf = new ByteArrayOutputStream();

                    byte[] data = new byte[1024 * 32];
                    while (true) {
                        if (isCancelled()) {
                            break;
                        }
                        int read = httpConnectionStream.read(data);
                        if (read > 0) {
                            outbuf.write(data, 0, read);
                        } else if (read == -1) {
                            break;
                        } else {
                            break;
                        }
                    }

                    JSONObject jsonObject = new JSONObject(new String(outbuf.toByteArray()));
                    JSONArray array = jsonObject.getJSONArray("Answer");
                    len = array.length();
                    ArrayList<String> arrayList = new ArrayList<>(len);
                    for (int a = 0; a < len; a++) {
                        JSONObject object = array.getJSONObject(a);
                        int type = object.getInt("type");
                        if (type != 16) {
                            continue;
                        }
                        arrayList.add(object.getString("data"));
                    }
                    Collections.sort(arrayList, (o1, o2) -> {
                        int l1 = o1.length();
                        int l2 = o2.length();
                        if (l1 > l2) {
                            return -1;
                        } else if (l1 < l2) {
                            return 1;
                        }
                        return 0;
                    });
                    StringBuilder builder = new StringBuilder();
                    for (int a = 0; a < arrayList.size(); a++) {
                        builder.append(arrayList.get(a).replace("\"", ""));
                    }
                    byte[] bytes = Base64.decode(builder.toString(), Base64.DEFAULT);
                    NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
                    buffer.writeBytes(bytes);
                    return buffer;
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    try {
                        if (httpConnectionStream != null) {
                            httpConnectionStream.close();
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    try {
                        if (outbuf != null) {
                            outbuf.close();
                        }
                    } catch (Exception ignore) {

                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final NativeByteBuffer result) {
            Utilities.stageQueue.postRunnable(() -> {
                currentTask = null;
                if (result != null) {
                    native_applyDnsConfig(currentAccount, result.address, AccountInstance.getInstance(currentAccount).getUserConfig().getClientPhone(), responseDate);
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("failed to get dns txt result");
                        FileLog.d("start google task");
                    }
                    GoogleDnsLoadTask task = new GoogleDnsLoadTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                }
            });
        }
    }

    private static class GoogleDnsLoadTask extends AsyncTask<Void, Void, NativeByteBuffer> {

        private int currentAccount;
        private int responseDate;

        public GoogleDnsLoadTask(int instance) {
            super();
            currentAccount = instance;
        }

        protected NativeByteBuffer doInBackground(Void... voids) {
            ByteArrayOutputStream outbuf = null;
            InputStream httpConnectionStream = null;
            try {
                String domain = native_isTestBackend(currentAccount) != 0 ? "tapv3.stel.com" : AccountInstance.getInstance(currentAccount).getMessagesController().dcDomainName;
                int len = Utilities.random.nextInt(116) + 13;
                final String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

                StringBuilder padding = new StringBuilder(len);
                for (int a = 0; a < len; a++) {
                    padding.append(characters.charAt(Utilities.random.nextInt(characters.length())));
                }
                URL downloadUrl = new URL("https://dns.google.com/resolve?name=" + domain + "&type=ANY&random_padding=" + padding);
                URLConnection httpConnection = downloadUrl.openConnection();
                httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                httpConnection.connect();
                httpConnectionStream = httpConnection.getInputStream();
                responseDate = (int) (httpConnection.getDate() / 1000);

                outbuf = new ByteArrayOutputStream();

                byte[] data = new byte[1024 * 32];
                while (true) {
                    if (isCancelled()) {
                        break;
                    }
                    int read = httpConnectionStream.read(data);
                    if (read > 0) {
                        outbuf.write(data, 0, read);
                    } else if (read == -1) {
                        break;
                    } else {
                        break;
                    }
                }

                JSONObject jsonObject = new JSONObject(new String(outbuf.toByteArray()));
                JSONArray array = jsonObject.getJSONArray("Answer");
                len = array.length();
                ArrayList<String> arrayList = new ArrayList<>(len);
                for (int a = 0; a < len; a++) {
                    JSONObject object = array.getJSONObject(a);
                    int type = object.getInt("type");
                    if (type != 16) {
                        continue;
                    }
                    arrayList.add(object.getString("data"));
                }
                Collections.sort(arrayList, (o1, o2) -> {
                    int l1 = o1.length();
                    int l2 = o2.length();
                    if (l1 > l2) {
                        return -1;
                    } else if (l1 < l2) {
                        return 1;
                    }
                    return 0;
                });
                StringBuilder builder = new StringBuilder();
                for (int a = 0; a < arrayList.size(); a++) {
                    builder.append(arrayList.get(a).replace("\"", ""));
                }
                byte[] bytes = Base64.decode(builder.toString(), Base64.DEFAULT);
                NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
                buffer.writeBytes(bytes);
                return buffer;
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    if (httpConnectionStream != null) {
                        httpConnectionStream.close();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    if (outbuf != null) {
                        outbuf.close();
                    }
                } catch (Exception ignore) {

                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final NativeByteBuffer result) {
            Utilities.stageQueue.postRunnable(() -> {
                currentTask = null;
                if (result != null) {
                    native_applyDnsConfig(currentAccount, result.address, AccountInstance.getInstance(currentAccount).getUserConfig().getClientPhone(), responseDate);
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("failed to get google result");
                        FileLog.d("start mozilla task");
                    }
                    MozillaDnsLoadTask task = new MozillaDnsLoadTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                }
            });
        }
    }

    private static class MozillaDnsLoadTask extends AsyncTask<Void, Void, NativeByteBuffer> {

        private int currentAccount;
        private int responseDate;

        public MozillaDnsLoadTask(int instance) {
            super();
            currentAccount = instance;
        }

        protected NativeByteBuffer doInBackground(Void... voids) {
            ByteArrayOutputStream outbuf = null;
            InputStream httpConnectionStream = null;
            try {
                String domain = native_isTestBackend(currentAccount) != 0 ? "tapv3.stel.com" : AccountInstance.getInstance(currentAccount).getMessagesController().dcDomainName;
                int len = Utilities.random.nextInt(116) + 13;
                final String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

                StringBuilder padding = new StringBuilder(len);
                for (int a = 0; a < len; a++) {
                    padding.append(characters.charAt(Utilities.random.nextInt(characters.length())));
                }
                URL downloadUrl = new URL("https://mozilla.cloudflare-dns.com/dns-query?name=" + domain + "&type=TXT&random_padding=" + padding);
                URLConnection httpConnection = downloadUrl.openConnection();
                httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1");
                httpConnection.addRequestProperty("accept", "application/dns-json");
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                httpConnection.connect();
                httpConnectionStream = httpConnection.getInputStream();
                responseDate = (int) (httpConnection.getDate() / 1000);

                outbuf = new ByteArrayOutputStream();

                byte[] data = new byte[1024 * 32];
                while (true) {
                    if (isCancelled()) {
                        break;
                    }
                    int read = httpConnectionStream.read(data);
                    if (read > 0) {
                        outbuf.write(data, 0, read);
                    } else if (read == -1) {
                        break;
                    } else {
                        break;
                    }
                }

                JSONObject jsonObject = new JSONObject(new String(outbuf.toByteArray()));
                JSONArray array = jsonObject.getJSONArray("Answer");
                len = array.length();
                ArrayList<String> arrayList = new ArrayList<>(len);
                for (int a = 0; a < len; a++) {
                    JSONObject object = array.getJSONObject(a);
                    int type = object.getInt("type");
                    if (type != 16) {
                        continue;
                    }
                    arrayList.add(object.getString("data"));
                }
                Collections.sort(arrayList, (o1, o2) -> {
                    int l1 = o1.length();
                    int l2 = o2.length();
                    if (l1 > l2) {
                        return -1;
                    } else if (l1 < l2) {
                        return 1;
                    }
                    return 0;
                });
                StringBuilder builder = new StringBuilder();
                for (int a = 0; a < arrayList.size(); a++) {
                    builder.append(arrayList.get(a).replace("\"", ""));
                }
                byte[] bytes = Base64.decode(builder.toString(), Base64.DEFAULT);
                NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
                buffer.writeBytes(bytes);
                return buffer;
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    if (httpConnectionStream != null) {
                        httpConnectionStream.close();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    if (outbuf != null) {
                        outbuf.close();
                    }
                } catch (Exception ignore) {

                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final NativeByteBuffer result) {
            Utilities.stageQueue.postRunnable(() -> {
                currentTask = null;
                if (result != null) {
                    native_applyDnsConfig(currentAccount, result.address, AccountInstance.getInstance(currentAccount).getUserConfig().getClientPhone(), responseDate);
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("failed to get mozilla txt result");
                    }
                }
            });
        }
    }

    private static class FirebaseTask extends AsyncTask<Void, Void, NativeByteBuffer> {

        private int currentAccount;
        private FirebaseRemoteConfig firebaseRemoteConfig;

        public FirebaseTask(int instance) {
            super();
            currentAccount = instance;
        }

        protected NativeByteBuffer doInBackground(Void... voids) {
            try {
                if (native_isTestBackend(currentAccount) != 0) {
                    throw new Exception("test backend");
                }
                firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
                String currentValue = firebaseRemoteConfig.getString("ipconfigv3");
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("current firebase value = " + currentValue);
                }

                firebaseRemoteConfig.fetch(0).addOnCompleteListener(finishedTask -> {
                    final boolean success = finishedTask.isSuccessful();
                    Utilities.stageQueue.postRunnable(() -> {
                        if (success) {
                            firebaseRemoteConfig.activate().addOnCompleteListener(finishedTask2 -> {
                                currentTask = null;
                                String config = firebaseRemoteConfig.getString("ipconfigv3");
                                if (!TextUtils.isEmpty(config)) {
                                    byte[] bytes = Base64.decode(config, Base64.DEFAULT);
                                    try {
                                        NativeByteBuffer buffer = new NativeByteBuffer(bytes.length);
                                        buffer.writeBytes(bytes);
                                        int date = (int) (firebaseRemoteConfig.getInfo().getFetchTimeMillis() / 1000);
                                        native_applyDnsConfig(currentAccount, buffer.address, AccountInstance.getInstance(currentAccount).getUserConfig().getClientPhone(), date);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                } else {
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("failed to get firebase result");
                                        FileLog.d("start dns txt task");
                                    }
                                    DnsTxtLoadTask task = new DnsTxtLoadTask(currentAccount);
                                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                                    currentTask = task;
                                }
                            });
                        }
                    });
                });
            } catch (Throwable e) {
                Utilities.stageQueue.postRunnable(() -> {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("failed to get firebase result");
                        FileLog.d("start dns txt task");
                    }
                    DnsTxtLoadTask task = new DnsTxtLoadTask(currentAccount);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
                    currentTask = task;
                });
                FileLog.e(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(NativeByteBuffer result) {

        }
    }
}
