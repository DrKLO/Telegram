package org.telegram.tgnet;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsManager {

    public final static int ConnectionTypeGeneric = 1;
    public final static int ConnectionTypeDownload = 2;
    public final static int ConnectionTypeUpload = 4;
    public final static int ConnectionTypePush = 8;
    public final static int ConnectionTypeDownload2 = ConnectionTypeDownload | (1 << 16);
    public final static int ConnectionTypeUpload2 = ConnectionTypeUpload | (1 << 16);

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
    public final static int ConnectionStateUpdating = 4;

    public final static int DEFAULT_DATACENTER_ID = Integer.MAX_VALUE;

    private long lastPauseTime = System.currentTimeMillis();
    private boolean appPaused = true;
    private int lastClassGuid = 1;
    private boolean isUpdating;
    private int connectionState = native_getConnectionState();
    private AtomicInteger lastRequestToken = new AtomicInteger(1);
    private PowerManager.WakeLock wakeLock;
    private int appResumeCount;

    private static volatile ConnectionsManager Instance = null;

    public static ConnectionsManager getInstance() {
        ConnectionsManager localInstance = Instance;
        if (localInstance == null) {
            synchronized (ConnectionsManager.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ConnectionsManager();
                }
            }
        }
        return localInstance;
    }

    public ConnectionsManager() {
        try {
            PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lock");
            wakeLock.setReferenceCounted(false);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public long getCurrentTimeMillis() {
        return native_getCurrentTimeMillis();
    }

    public int getCurrentTime() {
        return native_getCurrentTime();
    }

    public int getTimeDifference() {
        return native_getTimeDifference();
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock) {
        return sendRequest(object, completionBlock, null, 0);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock, int flags) {
        return sendRequest(object, completionBlock, null, flags, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock, int flags, int connetionType) {
        return sendRequest(object, completionBlock, null, flags, DEFAULT_DATACENTER_ID, connetionType, true);
    }

    public int sendRequest(TLObject object, RequestDelegate completionBlock, QuickAckDelegate quickAckBlock, int flags) {
        return sendRequest(object, completionBlock, quickAckBlock, flags, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
    }

    public int sendRequest(final TLObject object, final RequestDelegate onComplete, final QuickAckDelegate onQuickAck, final int flags, final int datacenterId, final int connetionType, final boolean immediate) {
        final int requestToken = lastRequestToken.getAndIncrement();
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileLog.d("send request " + object + " with token = " + requestToken);
                try {
                    NativeByteBuffer buffer = new NativeByteBuffer(object.getObjectSize());
                    object.serializeToStream(buffer);
                    object.freeResources();

                    native_sendRequest(buffer.address, new RequestDelegateInternal() {
                        @Override
                        public void run(int response, int errorCode, String errorText, int networkType) {
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
                                    FileLog.e(object + " got error " + error.code + " " + error.text);
                                }
                                if (resp != null) {
                                    resp.networkType = networkType;
                                }
                                FileLog.d("java received " + resp + " error = " + error);
                                final TLObject finalResponse = resp;
                                final TLRPC.TL_error finalError = error;
                                Utilities.stageQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        onComplete.run(finalResponse, finalError);
                                        if (finalResponse != null) {
                                            finalResponse.freeResources();
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }, onQuickAck, flags, datacenterId, connetionType, immediate, requestToken);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        return requestToken;
    }

    public void cancelRequest(int token, boolean notifyServer) {
        native_cancelRequest(token, notifyServer);
    }

    public void cleanup() {
        native_cleanUp();
    }

    public void cancelRequestsForGuid(int guid) {
        native_cancelRequestsForGuid(guid);
    }

    public void bindRequestToGuid(int requestToken, int guid) {
        native_bindRequestToGuid(requestToken, guid);
    }

    public void applyDatacenterAddress(int datacenterId, String ipAddress, int port) {
        native_applyDatacenterAddress(datacenterId, ipAddress, port);
    }

    public int getConnectionState() {
        if (connectionState == ConnectionStateConnected && isUpdating) {
            return ConnectionStateUpdating;
        }
        return connectionState;
    }

    public void setUserId(int id) {
        native_setUserId(id);
    }

    private void checkConnection() {
        native_setUseIpv6(useIpv6Address());
        native_setNetworkAvailable(isNetworkOnline(), getCurrentNetworkType());
    }

    public void setPushConnectionEnabled(boolean value) {
        native_setPushConnectionEnabled(value);
    }

    public void init(int version, int layer, int apiId, String deviceModel, String systemVersion, String appVersion, String langCode, String configPath, String logPath, int userId, boolean enablePushConnection) {
        native_init(version, layer, apiId, deviceModel, systemVersion, appVersion, langCode, configPath, logPath, userId, enablePushConnection, isNetworkOnline(), getCurrentNetworkType());
        checkConnection();
        BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkConnection();
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);
    }

    public void switchBackend() {
        native_switchBackend();
    }

    public void resumeNetworkMaybe() {
        native_resumeNetwork(true);
    }

    public void updateDcSettings() {
        native_updateDcSettings();
    }

    public long getPauseTime() {
        return lastPauseTime;
    }

    public void setAppPaused(final boolean value, final boolean byScreenState) {
        if (!byScreenState) {
            appPaused = value;
            FileLog.d("app paused = " + value);
            if (value) {
                appResumeCount--;
            } else {
                appResumeCount++;
            }
            FileLog.d("app resume count " + appResumeCount);
            if (appResumeCount < 0) {
                appResumeCount = 0;
            }
        }
        if (appResumeCount == 0) {
            if (lastPauseTime == 0) {
                lastPauseTime = System.currentTimeMillis();
            }
            native_pauseNetwork();
        } else {
            if (appPaused) {
                return;
            }
            FileLog.e("reset app pause time");
            if (lastPauseTime != 0 && System.currentTimeMillis() - lastPauseTime > 5000) {
                ContactsController.getInstance().checkContacts();
            }
            lastPauseTime = 0;
            native_resumeNetwork(false);
        }
    }

    public static void onUnparsedMessageReceived(int address) {
        try {
            NativeByteBuffer buff = NativeByteBuffer.wrap(address);
            buff.reused = true;
            final TLObject message = TLClassStore.Instance().TLdeserialize(buff, buff.readInt32(true), true);
            if (message instanceof TLRPC.Updates) {
                FileLog.d("java received " + message);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (getInstance().wakeLock.isHeld()) {
                            FileLog.d("release wakelock");
                            getInstance().wakeLock.release();
                        }
                    }
                });
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        MessagesController.getInstance().processUpdates((TLRPC.Updates) message, false);
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onUpdate() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().updateTimerProc();
            }
        });
    }

    public static void onSessionCreated() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().getDifference();
            }
        });
    }

    public static void onConnectionStateChanged(final int state) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                getInstance().connectionState = state;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState);
            }
        });
    }

    public static void onLogout() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (UserConfig.getClientUserId() != 0) {
                    UserConfig.clearConfig();
                    MessagesController.getInstance().performLogout(false);
                }
            }
        });
    }

    public static int getCurrentNetworkType() {
        if (isConnectedOrConnectingToWiFi()) {
            return StatsController.TYPE_WIFI;
        } else if (isRoaming()) {
            return StatsController.TYPE_ROAMING;
        } else {
            return StatsController.TYPE_MOBILE;
        }
    }

    public static void onBytesSent(int amount, int networkType) {
        try {
            StatsController.getInstance().incrementSentBytesCount(networkType, StatsController.TYPE_TOTAL, amount);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onBytesReceived(int amount, int networkType) {
        try {
            StatsController.getInstance().incrementReceivedBytesCount(networkType, StatsController.TYPE_TOTAL, amount);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onUpdateConfig(int address) {
        try {
            NativeByteBuffer buff = NativeByteBuffer.wrap(address);
            buff.reused = true;
            final TLRPC.TL_config message = TLRPC.TL_config.TLdeserialize(buff, buff.readInt32(true), true);
            if (message != null) {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        MessagesController.getInstance().updateConfig(message);
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onInternalPushReceived() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!getInstance().wakeLock.isHeld()) {
                        getInstance().wakeLock.acquire(10000);
                        FileLog.d("acquire wakelock");
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public static native void native_switchBackend();
    public static native void native_pauseNetwork();
    public static native void native_setUseIpv6(boolean value);
    public static native void native_updateDcSettings();
    public static native void native_setNetworkAvailable(boolean value, int networkType);
    public static native void native_resumeNetwork(boolean partial);
    public static native long native_getCurrentTimeMillis();
    public static native int native_getCurrentTime();
    public static native int native_getTimeDifference();
    public static native void native_sendRequest(int object, RequestDelegateInternal onComplete, QuickAckDelegate onQuickAck, int flags, int datacenterId, int connetionType, boolean immediate, int requestToken);
    public static native void native_cancelRequest(int token, boolean notifyServer);
    public static native void native_cleanUp();
    public static native void native_cancelRequestsForGuid(int guid);
    public static native void native_bindRequestToGuid(int requestToken, int guid);
    public static native void native_applyDatacenterAddress(int datacenterId, String ipAddress, int port);
    public static native int native_getConnectionState();
    public static native void native_setUserId(int id);
    public static native void native_init(int version, int layer, int apiId, String deviceModel, String systemVersion, String appVersion, String langCode, String configPath, String logPath, int userId, boolean enablePushConnection, boolean hasNetwork, int networkType);
    public static native void native_setJava(boolean useJavaByteBuffers);
    public static native void native_setPushConnectionEnabled(boolean value);

    public int generateClassGuid() {
        return lastClassGuid++;
    }

    public static boolean isRoaming() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
            if (netInfo != null) {
                return netInfo.isRoaming();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedOrConnectingToWiFi() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo.State state = netInfo.getState();
            if (netInfo != null && (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING || state == NetworkInfo.State.SUSPENDED)) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedToWiFi() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public void applyCountryPortNumber(String number) {

    }

    public void setIsUpdating(final boolean value) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (isUpdating == value) {
                    return;
                }
                isUpdating = value;
                if (connectionState == ConnectionStateConnected) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState);
                }
            }
        });
    }

    @SuppressLint("NewApi")
    protected static boolean useIpv6Address() {
        if (Build.VERSION.SDK_INT < 19) {
            return false;
        }
        if (BuildVars.DEBUG_VERSION) {
            try {
                NetworkInterface networkInterface;
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    networkInterface = networkInterfaces.nextElement();
                    if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.getInterfaceAddresses().isEmpty()) {
                        continue;
                    }
                    FileLog.e("valid interface: " + networkInterface);
                    List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                    for (int a = 0; a < interfaceAddresses.size(); a++) {
                        InterfaceAddress address = interfaceAddresses.get(a);
                        InetAddress inetAddress = address.getAddress();
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.e("address: " + inetAddress.getHostAddress());
                        }
                        if (inetAddress.isLinkLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isMulticastAddress()) {
                            continue;
                        }
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.e("address is good");
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
                        }
                    }
                }
            }
            if (!hasIpv4 && hasIpv6) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        return false;
    }

    public static boolean isNetworkOnline() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
            if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                return true;
            }

            netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }
}
