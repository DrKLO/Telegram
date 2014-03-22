/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Base64;

import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionsManager implements Action.ActionDelegate, TcpConnection.TcpConnectionDelegate {
    private HashMap<Integer, Datacenter> datacenters = new HashMap<Integer, Datacenter>();
    private HashMap<Long, ArrayList<Long>> processedMessageIdsSet = new HashMap<Long, ArrayList<Long>>();
    private HashMap<Long, Integer> nextSeqNoInSession = new HashMap<Long, Integer>();
    private ArrayList<Long> sessionsToDestroy = new ArrayList<Long>();
    private ArrayList<Long> destroyingSessions = new ArrayList<Long>();
    private HashMap<Integer, ArrayList<Long>> quickAckIdToRequestIds = new HashMap<Integer, ArrayList<Long>>();
    private HashMap<Long, ArrayList<Long>> messagesIdsForConfirmation = new HashMap<Long, ArrayList<Long>>();
    private HashMap<Long, ArrayList<Long>> processedSessionChanges = new HashMap<Long, ArrayList<Long>>();
    private HashMap<Long, Integer> pingIdToDate = new HashMap<Long, Integer>();
    private ConcurrentHashMap<Integer, ArrayList<Long>> requestsByGuids = new ConcurrentHashMap<Integer, ArrayList<Long>>(100, 1.0f, 2);
    private ConcurrentHashMap<Long, Integer> requestsByClass = new ConcurrentHashMap<Long, Integer>(100, 1.0f, 2);
    public volatile int connectionState = 2;

    private ArrayList<RPCRequest> requestQueue = new ArrayList<RPCRequest>();
    private ArrayList<RPCRequest> runningRequests = new ArrayList<RPCRequest>();
    private ArrayList<Action> actionQueue = new ArrayList<Action>();

    private TLRPC.TL_auth_exportedAuthorization movingAuthorization;
    public static final int DEFAULT_DATACENTER_ID = Integer.MAX_VALUE;
    public static final int DC_UPDATE_TIME = 60 * 60;
    public int currentDatacenterId;
    public int movingToDatacenterId;
    private long lastOutgoingMessageId = 0;
    private int useDifferentBackend = 0;
    private final int SESSION_VERSION = 2;
    public int timeDifference = 0;
    public int currentPingTime;
    private int lastDestroySessionRequestTime;
    public static final boolean isDebugSession = false;
    private boolean updatingDcSettings = false;
    private int updatingDcStartTime = 0;
    private int lastDcUpdateTime = 0;
    private int currentAppVersion = 0;

    private boolean paused = false;
    private Runnable stageRunnable;
    private Runnable pingRunnable;
    private long lastPingTime = System.currentTimeMillis();
    private int nextWakeUpTimeout = 60000;
    private int nextSleepTimeout = 60000;

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
        currentAppVersion = ApplicationLoader.getAppVersion();
        lastOutgoingMessageId = 0;
        movingToDatacenterId = DEFAULT_DATACENTER_ID;
        loadSession();

        if (!isNetworkOnline()) {
            connectionState = 1;
        }

        Timer serviceTimer = new Timer();
        serviceTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        long currentTime = System.currentTimeMillis();
                        if (ApplicationLoader.lastPauseTime != 0 && ApplicationLoader.lastPauseTime < currentTime - nextSleepTimeout) {
                            boolean dontSleep = false;
                            for (RPCRequest request : runningRequests) {
                                if (request.retryCount < 10 && (request.runningStartTime + 60 > (int)(currentTime / 1000)) && ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0 || (request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0)) {
                                    dontSleep = true;
                                    break;
                                }
                            }
                            if (!dontSleep) {
                                for (RPCRequest request : requestQueue) {
                                    if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0 || (request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                                        dontSleep = true;
                                        break;
                                    }
                                }
                            }
                            if (!dontSleep) {
                                if (!paused) {
                                    FileLog.e("tmessages", "pausing network and timers by sleep time = " + nextSleepTimeout);
                                    for (Datacenter datacenter : datacenters.values()) {
                                        if (datacenter.connection != null) {
                                            datacenter.connection.suspendConnection(true);
                                        }
                                        if (datacenter.uploadConnection != null) {
                                            datacenter.uploadConnection.suspendConnection(true);
                                        }
                                        if (datacenter.downloadConnection != null) {
                                            datacenter.downloadConnection.suspendConnection(true);
                                        }
                                    }
                                }
                                try {
                                    paused = true;
                                    if (ApplicationLoader.lastPauseTime < currentTime - nextSleepTimeout - nextWakeUpTimeout) {
                                        ApplicationLoader.lastPauseTime = currentTime;
                                        nextSleepTimeout = 30000;
                                        FileLog.e("tmessages", "wakeup network in background by wakeup time = " + nextWakeUpTimeout);
                                        if (nextWakeUpTimeout < 30 * 60 * 1000) {
                                            nextWakeUpTimeout *= 2;
                                        }
                                    } else {
                                        Thread.sleep(500);
                                        return;
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            } else {
                                ApplicationLoader.lastPauseTime += 30 * 1000;
                                FileLog.e("tmessages", "don't sleep 30 seconds because of upload or download request");
                            }
                        }
                        if (paused) {
                            paused = false;
                            FileLog.e("tmessages", "resume network and timers");
                        }

                        if (datacenters != null) {
                            MessagesController.getInstance().updateTimerProc();
                            if (datacenterWithId(currentDatacenterId).authKey != null) {
                                if (lastPingTime < System.currentTimeMillis() - 19000) {
                                    lastPingTime = System.currentTimeMillis();
                                    generatePing();
                                }
                                if (!updatingDcSettings && lastDcUpdateTime < (int)(System.currentTimeMillis() / 1000) - DC_UPDATE_TIME) {
                                    updateDcSettings(0);
                                }
                                processRequestQueue(0, 0);
                            }
                        }
                    }
                });
            }
        }, 1000, 1000);
    }

    public void resumeNetworkMaybe() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (paused) {
                    ApplicationLoader.lastPauseTime = System.currentTimeMillis();
                    nextWakeUpTimeout = 60000;
                    nextSleepTimeout = 30000;
                    FileLog.e("tmessages", "wakeup network in background by recieved push");
                } else if (ApplicationLoader.lastPauseTime != 0) {
                    ApplicationLoader.lastPauseTime = System.currentTimeMillis();
                    FileLog.e("tmessages", "reset sleep timeout by recieved push");
                }
            }
        });
    }

    public void applicationMovedToForeground() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (paused) {
                    nextSleepTimeout = 60000;
                    nextWakeUpTimeout = 60000;
                    FileLog.e("tmessages", "reset timers by application moved to foreground");
                }
            }
        });
    }

    //================================================================================
    // Config and session manage
    //================================================================================

    public Datacenter datacenterWithId(int datacenterId) {
        if (datacenterId == DEFAULT_DATACENTER_ID) {
            return datacenters.get(currentDatacenterId);
        }
        return datacenters.get(datacenterId);
    }

    void setTimeDifference(int diff) {
        boolean store = Math.abs(diff - timeDifference) > 25;
        timeDifference = diff;
        if (store) {
            saveSession();
        }
    }

    void loadSession() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                File configFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "config.dat");
                if (configFile.exists()) {
                    try {
                        SerializedData data = new SerializedData(configFile);
                        int datacenterSetId = data.readInt32();
                        int version = data.readInt32();

                        if (datacenterSetId == useDifferentBackend && version == SESSION_VERSION) {
                            sessionsToDestroy.clear();
                            int count = data.readInt32();
                            for (int a = 0; a < count; a++) {
                                sessionsToDestroy.add(data.readInt64());
                            }
                            timeDifference = data.readInt32();
                            count = data.readInt32();
                            for (int a = 0; a < count; a++) {
                                Datacenter datacenter = new Datacenter(data, 0);
                                datacenters.put(datacenter.datacenterId, datacenter);
                            }
                            currentDatacenterId = data.readInt32();
                        } else {
                            UserConfig.clearConfig();
                        }
                    } catch (Exception e) {
                        UserConfig.clearConfig();
                    }
                } else {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
                    int datacenterSetId = preferences.getInt("datacenterSetId", 0);
                    if (datacenterSetId == useDifferentBackend) {
                        currentDatacenterId = preferences.getInt("currentDatacenterId", 0);
                        timeDifference = preferences.getInt("timeDifference", 0);
                        lastDcUpdateTime = preferences.getInt("lastDcUpdateTime", 0);
                        try {
                            sessionsToDestroy.clear();
                            String sessionsString = preferences.getString("sessionsToDestroy", null);
                            if (sessionsString != null) {
                                byte[] sessionsBytes = Base64.decode(sessionsString, Base64.DEFAULT);
                                if (sessionsBytes != null) {
                                    SerializedData data = new SerializedData(sessionsBytes);
                                    int count = data.readInt32();
                                    for (int a = 0; a < count; a++) {
                                        sessionsToDestroy.add(data.readInt64());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }

                        try {
                            String datacentersString = preferences.getString("datacenters", null);
                            if (datacentersString != null) {
                                byte[] datacentersBytes = Base64.decode(datacentersString, Base64.DEFAULT);
                                if (datacentersBytes != null) {
                                    SerializedData data = new SerializedData(datacentersBytes);
                                    int count = data.readInt32();
                                    for (int a = 0; a < count; a++) {
                                        Datacenter datacenter = new Datacenter(data, 1);
                                        datacenters.put(datacenter.datacenterId, datacenter);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }

                if (currentDatacenterId != 0 && UserConfig.clientActivated) {
                    Datacenter datacenter = datacenterWithId(currentDatacenterId);
                    if (datacenter.authKey == null) {
                        currentDatacenterId = 0;
                        datacenters.clear();
                        UserConfig.clearConfig();
                    }
                }

                if (datacenters.size() == 0) {
                    if (useDifferentBackend == 0) {
                        Datacenter datacenter = new Datacenter();
                        datacenter.datacenterId = 1;
                        datacenter.addAddressAndPort("173.240.5.1", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);

                        datacenter = new Datacenter();
                        datacenter.datacenterId = 2;
                        datacenter.addAddressAndPort("109.239.131.193", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);

                        datacenter = new Datacenter();
                        datacenter.datacenterId = 3;
                        datacenter.addAddressAndPort("174.140.142.6", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);

                        datacenter = new Datacenter();
                        datacenter.datacenterId = 4;
                        datacenter.addAddressAndPort("31.210.235.12", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);

                        datacenter = new Datacenter();
                        datacenter.datacenterId = 5;
                        datacenter.addAddressAndPort("116.51.22.2", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);
                    } else {
                        Datacenter datacenter = new Datacenter();
                        datacenter.datacenterId = 1;
                        datacenter.addAddressAndPort("173.240.5.253", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);

                        datacenter = new Datacenter();
                        datacenter.datacenterId = 2;
                        datacenter.addAddressAndPort("109.239.131.195", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);

                        datacenter = new Datacenter();
                        datacenter.datacenterId = 3;
                        datacenter.addAddressAndPort("174.140.142.5", 443);
                        datacenters.put(datacenter.datacenterId, datacenter);
                    }
                } else if (datacenters.size() == 1) {
                    Datacenter datacenter = new Datacenter();
                    datacenter.datacenterId = 2;
                    datacenter.addAddressAndPort("109.239.131.193", 443);
                    datacenters.put(datacenter.datacenterId, datacenter);

                    datacenter = new Datacenter();
                    datacenter.datacenterId = 3;
                    datacenter.addAddressAndPort("174.140.142.6", 443);
                    datacenters.put(datacenter.datacenterId, datacenter);

                    datacenter = new Datacenter();
                    datacenter.datacenterId = 4;
                    datacenter.addAddressAndPort("31.210.235.12", 443);
                    datacenters.put(datacenter.datacenterId, datacenter);

                    datacenter = new Datacenter();
                    datacenter.datacenterId = 5;
                    datacenter.addAddressAndPort("116.51.22.2", 443);
                    datacenters.put(datacenter.datacenterId, datacenter);
                }

                for (Datacenter datacenter : datacenters.values()) {
                    datacenter.authSessionId = getNewSessionId();
                }

                if (datacenters.size() != 0 && currentDatacenterId == 0) {
                    currentDatacenterId = 1;
                    saveSession();
                }
                movingToDatacenterId = DEFAULT_DATACENTER_ID;
            }
        });
    }

    void saveSession() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("datacenterSetId", useDifferentBackend);
                    Datacenter currentDatacenter = datacenterWithId(currentDatacenterId);
                    if (currentDatacenter != null) {
                        editor.putInt("currentDatacenterId", currentDatacenterId);
                        editor.putInt("timeDifference", timeDifference);
                        editor.putInt("lastDcUpdateTime", lastDcUpdateTime);

                        ArrayList<Long> sessions = new ArrayList<Long>();
                        if (currentDatacenter.authSessionId != 0) {
                            sessions.add(currentDatacenter.authSessionId);
                        }
                        if (currentDatacenter.authDownloadSessionId != 0) {
                            sessions.add(currentDatacenter.authDownloadSessionId);
                        }
                        if (currentDatacenter.authUploadSessionId != 0) {
                            sessions.add(currentDatacenter.authUploadSessionId);
                        }

                        if (!sessions.isEmpty()) {
                            SerializedData data = new SerializedData(sessions.size() * 8 + 4);
                            data.writeInt32(sessions.size());
                            for (long session : sessions) {
                                data.writeInt64(session);
                            }
                            editor.putString("sessionsToDestroy", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                        } else {
                            editor.remove("sessionsToDestroy");
                        }

                        if (!datacenters.isEmpty()) {
                            SerializedData data = new SerializedData();
                            data.writeInt32(datacenters.size());
                            for (Datacenter datacenter : datacenters.values()) {
                                datacenter.SerializeToStream(data);
                            }
                            editor.putString("datacenters", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                        } else {
                            editor.remove("datacenters");
                        }
                    } else {
                        editor.remove("datacenters");
                        editor.remove("sessionsToDestroy");
                        editor.remove("currentDatacenterId");
                        editor.remove("timeDifference");
                    }
                    editor.commit();
                    File configFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "config.dat");
                    if (configFile.exists()) {
                        configFile.delete();
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    void clearRequestsForRequestClass(int requestClass, Datacenter datacenter) {
        for (RPCRequest request : runningRequests) {
            Datacenter dcenter = datacenterWithId(request.runningDatacenterId);
            if ((request.flags & requestClass) != 0 && dcenter != null && dcenter.datacenterId == datacenter.datacenterId) {
                request.runningMessageId = 0;
                request.runningMessageSeqNo = 0;
                request.runningStartTime = 0;
                request.runningMinStartTime = 0;
                request.transportChannelToken = 0;
            }
        }
    }

    public void cleanUp() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                Datacenter datacenter = datacenterWithId(currentDatacenterId);
                recreateSession(datacenter.authSessionId, datacenter);
            }
        });
    }

    void recreateSession(long sessionId, Datacenter datacenter) {
        messagesIdsForConfirmation.remove(sessionId);
        processedMessageIdsSet.remove(sessionId);
        nextSeqNoInSession.remove(sessionId);
        processedSessionChanges.remove(sessionId);
        pingIdToDate.remove(sessionId);

        if (sessionId == datacenter.authSessionId) {
            clearRequestsForRequestClass(RPCRequest.RPCRequestClassGeneric, datacenter);
            FileLog.d("tmessages", "***** Recreate generic session");
            datacenter.authSessionId = getNewSessionId();
        }
    }

    long getNewSessionId() {
        long newSessionId = MessagesController.random.nextLong();
        return isDebugSession ? (0xabcd000000000000L | (newSessionId & 0x0000ffffffffffffL)) : newSessionId;
    }

    long generateMessageId() {
        long messageId = (long)((((double)System.currentTimeMillis() + ((double)timeDifference) * 1000) * 4294967296.0) / 1000.0);
        if (messageId <= lastOutgoingMessageId) {
            messageId = lastOutgoingMessageId + 1;
        }
        while (messageId % 4 != 0) {
            messageId++;
        }
        lastOutgoingMessageId = messageId;
        return messageId;
    }

    long getTimeFromMsgId(long messageId) {
        return (long)(messageId / 4294967296.0 * 1000);
    }

    int generateMessageSeqNo(long session, boolean increment) {
        int value = 0;
        if (nextSeqNoInSession.containsKey(session)) {
            value = nextSeqNoInSession.get(session);
        }
        if (increment) {
            nextSeqNoInSession.put(session, value + 1);
        }
        return value * 2 + (increment ? 1 : 0);
    }

    boolean isMessageIdProcessed(long sessionId, long messageId) {
        ArrayList<Long> set = processedMessageIdsSet.get(sessionId);
        return set != null && set.contains(messageId);
    }

    void addProcessedMessageId(long sessionId, long messageId) {
        ArrayList<Long> set = processedMessageIdsSet.get(sessionId);
        if (set != null) {
            final int eraseLimit = 1000;
            final int eraseThreshold = 224;

            if (set.size() > eraseLimit + eraseThreshold) {
                for (int a = 0; a < Math.min(set.size(), eraseThreshold + 1); a++) {
                    set.remove(0);
                }
            }
            set.add(messageId);
        } else {
            ArrayList<Long> sessionMap = new ArrayList<Long>();
            sessionMap.add(messageId);
            processedMessageIdsSet.put(sessionId, sessionMap);
        }
    }

    //================================================================================
    // Requests manage
    //================================================================================
    int lastClassGuid = 1;
    public int generateClassGuid() {
        int guid = lastClassGuid++;
        ArrayList<Long> requests = new ArrayList<Long>();
        requestsByGuids.put(guid, requests);
        return guid;
    }

    public void cancelRpcsForClassGuid(int guid) {
        ArrayList<Long> requests = requestsByGuids.get(guid);
        if (requests != null) {
            for (Long request : requests) {
                cancelRpc(request, true);
            }
            requestsByGuids.remove(guid);
        }
    }

    public void bindRequestToGuid(final Long request, final int guid) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Long> requests = requestsByGuids.get(guid);
                if (requests != null) {
                    requests.add(request);
                    requestsByClass.put(request, guid);
                }
            }
        });
    }

    public void removeRequestInClass(final Long request) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                Integer guid = requestsByClass.get(request);
                if (guid != null) {
                    ArrayList<Long> requests = requestsByGuids.get(guid);
                    if (requests != null) {
                        requests.remove(request);
                    }
                }
            }
        });
    }

    public void applyDcPushUpdate(final int dc, final String ip_address, final int port) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                Datacenter exist = datacenterWithId(dc);
                if (exist != null) {
                    ArrayList<String> addresses = new ArrayList<String>();
                    HashMap<String, Integer> ports = new HashMap<String, Integer>();
                    addresses.add(ip_address);
                    ports.put(ip_address, port);
                    exist.replaceAddressesAndPorts(addresses, ports);
                    if (exist.connection != null) {
                        exist.connection.suspendConnection(true);
                    }
                    if (exist.uploadConnection != null) {
                        exist.uploadConnection.suspendConnection(true);
                    }
                    if (exist.downloadConnection != null) {
                        exist.downloadConnection.suspendConnection(true);
                    }
                    if (dc == 1) {
                        updateDcSettings(1);
                    }
                }
            }
        });
    }

    public void applyCountryPortNumber(final String phone) {
        if (phone == null || phone.length() == 0) {
            return;
        }
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (phone.startsWith("968")) {
                    for (HashMap.Entry<Integer, Datacenter> entry : datacenters.entrySet()) {
                        Datacenter datacenter = entry.getValue();
                        datacenter.overridePort = 14;
                        if (datacenter.connection != null) {
                            datacenter.connection.suspendConnection(true);
                        }
                        if (datacenter.uploadConnection != null) {
                            datacenter.uploadConnection.suspendConnection(true);
                        }
                        if (datacenter.downloadConnection != null) {
                            datacenter.downloadConnection.suspendConnection(true);
                        }
                    }
                } else {
                    for (HashMap.Entry<Integer, Datacenter> entry : datacenters.entrySet()) {
                        Datacenter datacenter = entry.getValue();
                        datacenter.overridePort = -1;
                    }
                }
            }
        });
    }

    public void updateDcSettings(int dcNum) {
        if (updatingDcSettings) {
            return;
        }
        updatingDcStartTime = (int)(System.currentTimeMillis() / 1000);
        updatingDcSettings = true;
        TLRPC.TL_help_getConfig getConfig = new TLRPC.TL_help_getConfig();

        ConnectionsManager.getInstance().performRpc(getConfig, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (!updatingDcSettings) {
                    return;
                }
                if (error == null) {
                    lastDcUpdateTime = (int)(System.currentTimeMillis() / 1000);
                    TLRPC.TL_config config = (TLRPC.TL_config)response;
                    ArrayList<Datacenter> datacentersArr = new ArrayList<Datacenter>();
                    HashMap<Integer, Datacenter> datacenterMap = new HashMap<Integer, Datacenter>();
                    for (TLRPC.TL_dcOption datacenterDesc : config.dc_options) {
                        Datacenter existing = datacenterMap.get(datacenterDesc.id);
                        if (existing == null) {
                            existing = new Datacenter();
                            existing.datacenterId = datacenterDesc.id;
                            existing.authSessionId = MessagesController.random.nextLong();
                            datacentersArr.add(existing);
                            datacenterMap.put(existing.datacenterId, existing);
                        }
                        existing.addAddressAndPort(datacenterDesc.ip_address, datacenterDesc.port);
                    }

                    if (!datacentersArr.isEmpty()) {
                        for (Datacenter datacenter : datacentersArr) {
                            Datacenter exist = datacenterWithId(datacenter.datacenterId);
                            if (exist == null) {
                                datacenters.put(datacenter.datacenterId, datacenter);
                            } else {
                                exist.replaceAddressesAndPorts(datacenter.addresses, datacenter.ports);
                            }
                            if (datacenter.datacenterId == movingToDatacenterId) {
                                movingToDatacenterId = DEFAULT_DATACENTER_ID;
                                moveToDatacenter(datacenter.datacenterId);
                            }
                        }
                        saveSession();

                        processRequestQueue(RPCRequest.RPCRequestClassTransportMask, 0);
                    }
                }
                updatingDcSettings = false;
            }
        }, null, true, RPCRequest.RPCRequestClassEnableUnauthorized | RPCRequest.RPCRequestClassGeneric, dcNum == 0 ? currentDatacenterId : dcNum);
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCProgressDelegate progressBlock, boolean requiresCompletion, int requestClass) {
        return performRpc(rpc, completionBlock, progressBlock, requiresCompletion, requestClass, DEFAULT_DATACENTER_ID);
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCProgressDelegate progressBlock, boolean requiresCompletion, int requestClass, int datacenterId) {
        return performRpc(rpc, completionBlock, progressBlock, null, requiresCompletion, requestClass, datacenterId);
    }

    TLObject wrapInLayer(TLObject object, int datacenterId, RPCRequest request) {
        if (object.layer() > 0) {
            Datacenter datacenter = datacenterWithId(datacenterId);
            if (datacenter == null || datacenter.lastInitVersion != currentAppVersion) {
                request.initRequest = true;
                TLRPC.initConnection invoke = new TLRPC.initConnection();
                invoke.query = object;
                invoke.api_id = BuildVars.APP_ID;
                try {
                    invoke.lang_code = Locale.getDefault().getCountry();
                    invoke.device_model = Build.MANUFACTURER + Build.MODEL;
                    if (invoke.device_model == null) {
                        invoke.device_model = "Android unknown";
                    }
                    PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                    invoke.app_version = pInfo.versionName;
                    if (invoke.app_version == null) {
                        invoke.app_version = "App version unknown";
                    }
                    invoke.system_version = "SDK " + Build.VERSION.SDK_INT;
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    invoke.lang_code = "en";
                    invoke.device_model = "Android unknown";
                    invoke.app_version = "App version unknown";
                    invoke.system_version = "SDK " + Build.VERSION.SDK_INT;
                }
                if (invoke.lang_code == null || invoke.lang_code.length() == 0) {
                    invoke.lang_code = "en";
                }
                if (invoke.device_model == null || invoke.device_model.length() == 0) {
                    invoke.device_model = "Android unknown";
                }
                if (invoke.app_version == null || invoke.app_version.length() == 0) {
                    invoke.app_version = "App version unknown";
                }
                if (invoke.system_version == null || invoke.system_version.length() == 0) {
                    invoke.system_version = "SDK Unknown";
                }
                object = invoke;
            }
            TLRPC.invokeWithLayer12 invoke = new TLRPC.invokeWithLayer12();
            invoke.query = object;
            FileLog.d("wrap in layer", "" + object);
            return invoke;
        }
        return object;
    }

    public static volatile long nextCallToken = 0;
    long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCProgressDelegate progressBlock, final RPCRequest.RPCQuickAckDelegate quickAckBlock, final boolean requiresCompletion, final int requestClass, final int datacenterId) {

        final long requestToken = nextCallToken++;

        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                RPCRequest request = new RPCRequest();
                request.token = requestToken;
                request.flags = requestClass;

                request.runningDatacenterId = datacenterId;

                request.rawRequest = rpc;
                request.rpcRequest = wrapInLayer(rpc, datacenterId, request);
                request.completionBlock = completionBlock;
                request.progressBlock = progressBlock;
                request.quickAckBlock = quickAckBlock;
                request.requiresCompletion = requiresCompletion;

                requestQueue.add(request);

                if (paused && ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0 || (request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0)) {
                    ApplicationLoader.lastPauseTime = System.currentTimeMillis();
                    nextSleepTimeout = 30000;
                    FileLog.e("tmessages", "wakeup by download or upload request");
                }

                processRequestQueue(0, 0);
            }
        });

        return requestToken;
    }

    public void cancelRpc(final long token, final boolean notifyServer) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                boolean found = false;

                for (int i = 0; i < requestQueue.size(); i++) {
                    RPCRequest request = requestQueue.get(i);
                    if (request.token == token) {
                        found = true;
                        request.cancelled = true;
                        FileLog.d("tmessages", "===== Cancelled queued rpc request " + request.rawRequest);
                        requestQueue.remove(i);
                        break;
                    }
                }

                for (int i = 0; i < runningRequests.size(); i++) {
                    RPCRequest request = runningRequests.get(i);
                    if (request.token == token) {
                        found = true;

                        FileLog.d("tmessages", "===== Cancelled running rpc request " + request.rawRequest);

                        if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                            if (notifyServer) {
                                TLRPC.TL_rpc_drop_answer dropAnswer = new TLRPC.TL_rpc_drop_answer();
                                dropAnswer.req_msg_id = request.runningMessageId;
                                performRpc(dropAnswer, null, null, false, request.flags);
                            }
                        }

                        request.cancelled = true;
                        runningRequests.remove(i);
                        break;
                    }
                }
                if (!found) {
                    FileLog.d("tmessages", "***** Warning: cancelling unknown request");
                }
            }
        });
    }

    public static boolean isNetworkOnline() {
        boolean status = false;
        try {
            ConnectivityManager cm = (ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getNetworkInfo(0);
            if (netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED) {
                status = true;
            } else {
                netInfo = cm.getNetworkInfo(1);
                if(netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED) {
                    status = true;
                }
            }
        } catch(Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }
        return status;
    }

    public int getCurrentTime() {
        return (int)(System.currentTimeMillis() / 1000) + timeDifference;
    }

    private void processRequestQueue(int requestClass, int _datacenterId) {
        final HashMap<Integer, Integer> activeTransportTokens = new HashMap<Integer, Integer>();
        final ArrayList<Integer> transportsToResume = new ArrayList<Integer>();

        final HashMap<Integer, Integer> activeDownloadTransportTokens = new HashMap<Integer, Integer>();
        final ArrayList<Integer> downloadTransportsToResume = new ArrayList<Integer>();

        final HashMap<Integer, Integer> activeUploadTransportTokens = new HashMap<Integer, Integer>();
        final ArrayList<Integer> uploadTransportsToResume = new ArrayList<Integer>();

        for (Datacenter datacenter : datacenters.values()) {
            if (datacenter.connection != null) {
                int channelToken = datacenter.connection.channelToken;
                if (channelToken != 0) {
                    activeTransportTokens.put(datacenter.datacenterId, channelToken);
                }
            }
            if (datacenter.downloadConnection != null) {
                int channelToken = datacenter.downloadConnection.channelToken;
                if (channelToken != 0) {
                    activeDownloadTransportTokens.put(datacenter.datacenterId, channelToken);
                }
            }
            if (datacenter.uploadConnection != null) {
                int channelToken = datacenter.uploadConnection.channelToken;
                if (channelToken != 0) {
                    activeUploadTransportTokens.put(datacenter.datacenterId, channelToken);
                }
            }
        }
        for (RPCRequest request : runningRequests) {
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeTransportTokens.containsKey(requestDatacenter.datacenterId) && !transportsToResume.contains(requestDatacenter.datacenterId)) {
                    transportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeDownloadTransportTokens.containsKey(requestDatacenter.datacenterId) && !downloadTransportsToResume.contains(requestDatacenter.datacenterId)) {
                    downloadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeUploadTransportTokens.containsKey(requestDatacenter.datacenterId) && !uploadTransportsToResume.contains(requestDatacenter.datacenterId)) {
                    uploadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            }
        }
        for (RPCRequest request : requestQueue) {
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeTransportTokens.containsKey(requestDatacenter.datacenterId) && !transportsToResume.contains(requestDatacenter.datacenterId)) {
                    transportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeDownloadTransportTokens.containsKey(requestDatacenter.datacenterId) && !downloadTransportsToResume.contains(requestDatacenter.datacenterId)) {
                    downloadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeUploadTransportTokens.containsKey(requestDatacenter.datacenterId) && !uploadTransportsToResume.contains(requestDatacenter.datacenterId)) {
                    uploadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            }
        }

        boolean haveNetwork = true;//activeTransportTokens.size() != 0 || isNetworkOnline();

        if (!activeTransportTokens.containsKey(currentDatacenterId) && !transportsToResume.contains(currentDatacenterId)) {
            transportsToResume.add(currentDatacenterId);
        }

        for (int it : transportsToResume) {
            Datacenter datacenter = datacenterWithId(it);
            if (datacenter.authKey != null) {
                if (datacenter.connection == null) {
                    datacenter.connection = new TcpConnection(datacenter.datacenterId);
                    datacenter.connection.delegate = this;
                    datacenter.connection.transportRequestClass = RPCRequest.RPCRequestClassGeneric;
                }
                datacenter.connection.connect();
            }
        }
        for (int it : downloadTransportsToResume) {
            Datacenter datacenter = datacenterWithId(it);
            if (datacenter.authKey != null) {
                if (datacenter.downloadConnection == null) {
                    datacenter.downloadConnection = new TcpConnection(datacenter.datacenterId);
                    datacenter.downloadConnection.delegate = this;
                    datacenter.downloadConnection.transportRequestClass = RPCRequest.RPCRequestClassDownloadMedia;
                    datacenter.authDownloadSessionId = getNewSessionId();
                }
                datacenter.downloadConnection.connect();
            }
        }
        for (int it : uploadTransportsToResume) {
            Datacenter datacenter = datacenterWithId(it);
            if (datacenter.authKey != null) {
                if (datacenter.uploadConnection == null) {
                    datacenter.uploadConnection = new TcpConnection(datacenter.datacenterId);
                    datacenter.uploadConnection.delegate = this;
                    datacenter.uploadConnection.transportRequestClass = RPCRequest.RPCRequestClassUploadMedia;
                    datacenter.authUploadSessionId = getNewSessionId();
                }
                datacenter.uploadConnection.connect();
            }
        }

        final HashMap<Integer, ArrayList<NetworkMessage>> genericMessagesToDatacenters = new HashMap<Integer, ArrayList<NetworkMessage>>();

        final ArrayList<Integer> unknownDatacenterIds = new ArrayList<Integer>();
        final ArrayList<Integer> neededDatacenterIds = new ArrayList<Integer>();
        final ArrayList<Integer> unauthorizedDatacenterIds = new ArrayList<Integer>();

        int currentTime = (int)(System.currentTimeMillis() / 1000);
        for (int i = 0; i < runningRequests.size(); i++) {
            RPCRequest request = runningRequests.get(i);

            if (updatingDcSettings && datacenters.size() > 1 && request.rawRequest instanceof TLRPC.TL_help_getConfig) {
                if (updatingDcStartTime < currentTime - 60) {
                    FileLog.e("tmessages", "move TL_help_getConfig to requestQueue");
                    requestQueue.add(request);
                    runningRequests.remove(i);
                    i--;
                    continue;
                }
            }

            int datacenterId = request.runningDatacenterId;
            if (datacenterId == DEFAULT_DATACENTER_ID) {
                if (movingToDatacenterId != DEFAULT_DATACENTER_ID) {
                    continue;
                }
                datacenterId = currentDatacenterId;
            }

            Datacenter requestDatacenter = datacenterWithId(datacenterId);
            if (!request.initRequest && requestDatacenter.lastInitVersion != currentAppVersion) {
                request.rpcRequest = wrapInLayer(request.rawRequest, requestDatacenter.datacenterId, request);
                SerializedData os = new SerializedData(true);
                request.rpcRequest.serializeToStream(os);
                request.serializedLength = os.length();
            }

            if (requestDatacenter == null) {
                if (!unknownDatacenterIds.contains(datacenterId)) {
                    unknownDatacenterIds.add(datacenterId);
                }
                continue;
            } else if (requestDatacenter.authKey == null) {
                if (!neededDatacenterIds.contains(datacenterId)) {
                    neededDatacenterIds.add(datacenterId);
                }
                continue;
            } else if (!requestDatacenter.authorized && request.runningDatacenterId != DEFAULT_DATACENTER_ID && request.runningDatacenterId != currentDatacenterId && (request.flags & RPCRequest.RPCRequestClassEnableUnauthorized) == 0) {
                if (!unauthorizedDatacenterIds.contains(datacenterId)) {
                    unauthorizedDatacenterIds.add(datacenterId);
                }
                continue;
            }

            Integer tokenIt = activeTransportTokens.get(requestDatacenter.datacenterId);
            int datacenterTransportToken = tokenIt != null ? tokenIt : 0;

            Integer uploadTokenIt = activeUploadTransportTokens.get(requestDatacenter.datacenterId);
            int datacenterUploadTransportToken = uploadTokenIt != null ? uploadTokenIt : 0;

            Integer downloadTokenIt = activeDownloadTransportTokens.get(requestDatacenter.datacenterId);
            int datacenterDownloadTransportToken = downloadTokenIt != null ? downloadTokenIt : 0;

            double maxTimeout = 8.0;

            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                if (datacenterTransportToken == 0) {
                    continue;
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                if (!haveNetwork) {
                    FileLog.d("tmessages", "Don't have any network connection, skipping download request");
                    continue;
                }
                if (datacenterDownloadTransportToken == 0) {
                    continue;
                }
                maxTimeout = 40.0;
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                if (!haveNetwork) {
                    FileLog.d("tmessages", "Don't have any network connection, skipping upload request");
                    continue;
                }
                if (datacenterUploadTransportToken == 0) {
                    continue;
                }
                maxTimeout = 30.0;
            }

            long sessionId = 0;
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                sessionId = requestDatacenter.authSessionId;
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                sessionId = requestDatacenter.authDownloadSessionId;
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0 ) {
                sessionId = requestDatacenter.authUploadSessionId;
            }

            boolean forceThisRequest = (request.flags & requestClass) != 0 && (_datacenterId == Integer.MIN_VALUE || requestDatacenter.datacenterId == _datacenterId);

            if (request.rawRequest instanceof TLRPC.TL_get_future_salts || request.rawRequest instanceof TLRPC.TL_destroy_session) {
                if (request.runningMessageId != 0) {
                    request.addRespondMessageId(request.runningMessageId);
                }
                request.runningMessageId = 0;
                request.runningMessageSeqNo = 0;
                request.transportChannelToken = 0;
                forceThisRequest = false;
            }

            if (((Math.abs(currentTime - request.runningStartTime) > maxTimeout) && (currentTime > request.runningMinStartTime || Math.abs(currentTime - request.runningMinStartTime) > 60.0)) || forceThisRequest) {
                if (!forceThisRequest && request.transportChannelToken > 0) {
                    if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0 && datacenterTransportToken == request.transportChannelToken) {
                        FileLog.d("tmessages", "Request token is valid, not retrying " + request.rawRequest);
                        continue;
                    } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                        if (datacenterDownloadTransportToken != 0 && request.transportChannelToken == datacenterDownloadTransportToken) {
                            FileLog.d("tmessages", "Request download token is valid, not retrying " + request.rawRequest);
                            continue;
                        }
                    } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                        if (datacenterUploadTransportToken != 0 && request.transportChannelToken == datacenterUploadTransportToken) {
                            FileLog.d("tmessages", "Request upload token is valid, not retrying " + request.rawRequest);
                            continue;
                        }
                    }
                }

                request.retryCount++;
                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = new TLRPC.TL_protoMessage();

                if (request.runningMessageSeqNo == 0) {
                    request.runningMessageSeqNo = generateMessageSeqNo(sessionId, true);
                    request.runningMessageId = generateMessageId();
                }
                networkMessage.protoMessage.msg_id = request.runningMessageId;
                networkMessage.protoMessage.seqno = request.runningMessageSeqNo;
                networkMessage.protoMessage.bytes = request.serializedLength;
                networkMessage.protoMessage.body = request.rpcRequest;
                networkMessage.rawRequest = request.rawRequest;
                networkMessage.requestId = request.token;

                request.runningStartTime = currentTime;

                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    request.transportChannelToken = datacenterTransportToken;
                    addMessageToDatacenter(genericMessagesToDatacenters, requestDatacenter.datacenterId, networkMessage);
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    request.transportChannelToken = datacenterDownloadTransportToken;
                    ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, sessionId, requestDatacenter.downloadConnection, false, false);
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    request.transportChannelToken = datacenterUploadTransportToken;
                    ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, sessionId, requestDatacenter.uploadConnection, false, false);
                }
            }
        }

        boolean updatingState = MessagesController.getInstance().updatingState;

        if (activeTransportTokens.get(currentDatacenterId) != null) {
            if (!updatingState) {
                Datacenter currentDatacenter = datacenterWithId(currentDatacenterId);

                for (Long it : sessionsToDestroy) {
                    if (destroyingSessions.contains(it)) {
                        continue;
                    }
                    if (System.currentTimeMillis() / 1000 - lastDestroySessionRequestTime > 2.0) {
                        lastDestroySessionRequestTime = (int)(System.currentTimeMillis() / 1000);
                        TLRPC.TL_destroy_session destroySession = new TLRPC.TL_destroy_session();
                        destroySession.session_id = it;
                        destroyingSessions.add(it);

                        NetworkMessage networkMessage = new NetworkMessage();
                        networkMessage.protoMessage = wrapMessage(destroySession, currentDatacenter.authSessionId, false);
                        if (networkMessage.protoMessage != null) {
                            addMessageToDatacenter(genericMessagesToDatacenters, currentDatacenter.datacenterId, networkMessage);
                        }
                    }
                }
            }
        }

        int genericRunningRequestCount = 0;
        int uploadRunningRequestCount = 0;
        int downloadRunningRequestCount = 0;

        for (RPCRequest request : runningRequests) {
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                genericRunningRequestCount++;
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                uploadRunningRequestCount++;
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                downloadRunningRequestCount++;
            }
        }

        for (int i = 0; i < requestQueue.size(); i++) {
            RPCRequest request = requestQueue.get(i);
            if (request.cancelled) {
                requestQueue.remove(i);
                i--;
                continue;
            }

            if (updatingDcSettings && datacenters.size() > 1 && request.rawRequest instanceof TLRPC.TL_help_getConfig) {
                if (updatingDcStartTime < currentTime - 60) {
                    updatingDcStartTime = currentTime;
                    ArrayList<Datacenter> allDc = new ArrayList<Datacenter>(datacenters.values());
                    for (int a = 0; a < allDc.size(); a++) {
                        Datacenter dc = allDc.get(a);
                        if (dc.datacenterId == request.runningDatacenterId) {
                            allDc.remove(a);
                            break;
                        }
                    }
                    Datacenter newDc = allDc.get(Math.abs(MessagesController.random.nextInt()) % allDc.size());
                    request.runningDatacenterId = newDc.datacenterId;
                }
            }

            int datacenterId = request.runningDatacenterId;
            if (datacenterId == DEFAULT_DATACENTER_ID) {
                if (movingToDatacenterId != DEFAULT_DATACENTER_ID && (request.flags & RPCRequest.RPCRequestClassEnableUnauthorized) == 0) {
                    continue;
                }
                datacenterId = currentDatacenterId;
            }

            Datacenter requestDatacenter = datacenterWithId(datacenterId);
            if (!request.initRequest && requestDatacenter.lastInitVersion != currentAppVersion) {
                request.rpcRequest = wrapInLayer(request.rawRequest, requestDatacenter.datacenterId, request);
            }

            if (requestDatacenter == null) {
                unknownDatacenterIds.add(datacenterId);
                continue;
            } else if (requestDatacenter.authKey == null) {
                neededDatacenterIds.add(datacenterId);
                continue;
            } else if (!requestDatacenter.authorized && request.runningDatacenterId != DEFAULT_DATACENTER_ID && request.runningDatacenterId != currentDatacenterId && (request.flags & RPCRequest.RPCRequestClassEnableUnauthorized) == 0) {
                unauthorizedDatacenterIds.add(datacenterId);
                continue;
            }

            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0 && activeTransportTokens.get(requestDatacenter.datacenterId) == null) {
                continue;
            }

            if (updatingState && (request.rawRequest instanceof TLRPC.TL_account_updateStatus || request.rawRequest instanceof TLRPC.TL_account_registerDevice)) {
                continue;
            }

            if (request.requiresCompletion) {
                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    if (genericRunningRequestCount >= 60)
                        continue;

                    genericRunningRequestCount++;

                    Integer tokenIt = activeTransportTokens.get(requestDatacenter.datacenterId);
                    request.transportChannelToken = tokenIt != null ? tokenIt : 0;
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    if (!haveNetwork) {
                        FileLog.d("tmessages", "Don't have any network connection, skipping upload request");
                        continue;
                    }

                    if (uploadRunningRequestCount >= 5) {
                        continue;
                    }

                    Integer uploadTokenIt = activeUploadTransportTokens.get(requestDatacenter.datacenterId);
                    request.transportChannelToken = uploadTokenIt != null ? uploadTokenIt : 0;

                    uploadRunningRequestCount++;
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    if (!haveNetwork) {
                        FileLog.d("tmessages", "Don't have any network connection, skipping download request");
                        continue;
                    }

                    if (downloadRunningRequestCount >= 5) {
                        continue;
                    }

                    Integer downloadTokenIt = activeDownloadTransportTokens.get(requestDatacenter.datacenterId);
                    request.transportChannelToken = downloadTokenIt != null ? downloadTokenIt : 0;

                    downloadRunningRequestCount++;
                }
            }

            long messageId = generateMessageId();

            boolean canCompress = (request.flags & RPCRequest.RPCRequestClassCanCompress) != 0;

            SerializedData os = new SerializedData(!canCompress);
            request.rpcRequest.serializeToStream(os);
            int requestLength = os.length();

            if (requestLength != 0) {
                long sessionId = 0;
                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    sessionId = requestDatacenter.authSessionId;
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    sessionId = requestDatacenter.authDownloadSessionId;
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    sessionId = requestDatacenter.authUploadSessionId;
                }

                if (canCompress) {
                    try {
                        byte[] data = Utilities.compress(os.toByteArray());
                        if (data.length < requestLength) {
                            TLRPC.TL_gzip_packed packed = new TLRPC.TL_gzip_packed();
                            packed.packed_data = data;
                            request.rpcRequest = packed;
                            os = new SerializedData(true);
                            packed.serializeToStream(os);
                            requestLength = os.length();
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = new TLRPC.TL_protoMessage();
                networkMessage.protoMessage.msg_id = messageId;
                networkMessage.protoMessage.seqno = generateMessageSeqNo(sessionId, true);
                networkMessage.protoMessage.bytes = requestLength;
                networkMessage.protoMessage.body = request.rpcRequest;
                networkMessage.rawRequest = request.rawRequest;
                networkMessage.requestId = request.token;

                request.runningMessageId = messageId;
                request.runningMessageSeqNo = networkMessage.protoMessage.seqno;
                request.serializedLength = requestLength;
                request.runningStartTime = (int)(System.currentTimeMillis() / 1000);
                if (request.requiresCompletion) {
                    runningRequests.add(request);
                }

                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    addMessageToDatacenter(genericMessagesToDatacenters, requestDatacenter.datacenterId, networkMessage);
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, sessionId, requestDatacenter.downloadConnection, false, false);
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, sessionId, requestDatacenter.uploadConnection, false, false);
                } else {
                    FileLog.e("tmessages", "***** Error: request " + request.rawRequest + " has undefined session");
                }
            } else {
                FileLog.e("tmessages", "***** Couldn't serialize " + request.rawRequest);
            }

            requestQueue.remove(i);
            i--;
        }

        for (Datacenter datacenter : datacenters.values()) {
            if (genericMessagesToDatacenters.get(datacenter.datacenterId) == null && datacenter.connection != null && datacenter.connection.channelToken != 0) {
                ArrayList<Long> arr = messagesIdsForConfirmation.get(datacenter.authSessionId);
                if (arr != null && arr.size() != 0) {
                    genericMessagesToDatacenters.put(datacenter.datacenterId, new ArrayList<NetworkMessage>());
                }
            }
        }

        for (int iter : genericMessagesToDatacenters.keySet()) {
            Datacenter datacenter = datacenterWithId(iter);
            if (datacenter != null) {
                boolean scannedPreviousRequests = false;
                long lastSendMessageRpcId = 0;

                boolean hasSendMessage = false;
                ArrayList<NetworkMessage> arr = genericMessagesToDatacenters.get(iter);
                for (NetworkMessage networkMessage : arr) {
                    TLRPC.TL_protoMessage message = networkMessage.protoMessage;

                    Object rawRequest = networkMessage.rawRequest;

                    if (rawRequest != null && (rawRequest instanceof TLRPC.TL_messages_sendMessage ||
                            rawRequest instanceof TLRPC.TL_messages_sendMedia ||
                            rawRequest instanceof TLRPC.TL_messages_forwardMessages ||
                            rawRequest instanceof TLRPC.TL_messages_sendEncrypted)) {

                        if (rawRequest instanceof TLRPC.TL_messages_sendMessage) {
                            hasSendMessage = true;
                        }

                        if (!scannedPreviousRequests) {
                            scannedPreviousRequests = true;

                            ArrayList<Long> currentRequests = new ArrayList<Long>();
                            for (NetworkMessage currentNetworkMessage : arr) {
                                TLRPC.TL_protoMessage currentMessage = currentNetworkMessage.protoMessage;

                                Object currentRawRequest = currentNetworkMessage.rawRequest;

                                if (currentRawRequest instanceof TLRPC.TL_messages_sendMessage ||
                                        currentRawRequest instanceof TLRPC.TL_messages_sendMedia ||
                                        currentRawRequest instanceof TLRPC.TL_messages_forwardMessages ||
                                        currentRawRequest instanceof TLRPC.TL_messages_sendEncrypted) {
                                    currentRequests.add(currentMessage.msg_id);
                                }
                            }

                            long maxRequestId = 0;
                            for (RPCRequest request : runningRequests) {
                                if (request.rawRequest instanceof TLRPC.TL_messages_sendMessage ||
                                        request.rawRequest instanceof TLRPC.TL_messages_sendMedia ||
                                        request.rawRequest instanceof TLRPC.TL_messages_forwardMessages ||
                                        request.rawRequest instanceof TLRPC.TL_messages_sendEncrypted) {
                                    if (!currentRequests.contains(request.runningMessageId)) {
                                        maxRequestId = Math.max(maxRequestId, request.runningMessageId);
                                    }
                                }
                            }

                            lastSendMessageRpcId = maxRequestId;
                        }

                        if (lastSendMessageRpcId != 0 && lastSendMessageRpcId != message.msg_id) {
                            TLRPC.TL_invokeAfterMsg invokeAfterMsg = new TLRPC.TL_invokeAfterMsg();
                            invokeAfterMsg.msg_id = lastSendMessageRpcId;
                            invokeAfterMsg.query = message.body;

                            message.body = invokeAfterMsg;
                            message.bytes = message.bytes + 4 + 8;
                        }

                        lastSendMessageRpcId = message.msg_id;
                    }
                }

                if (datacenter.connection == null) {
                    datacenter.connection = new TcpConnection(datacenter.datacenterId);
                    datacenter.connection.delegate = this;
                    datacenter.connection.transportRequestClass = RPCRequest.RPCRequestClassGeneric;
                }

                proceedToSendingMessages(arr, datacenter.authSessionId, datacenter.connection, hasSendMessage, arr.size() != 0);
            }
        }

        if ((requestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (_datacenterId == Integer.MIN_VALUE) {
                for (Datacenter datacenter : datacenters.values()) {
                    ArrayList<NetworkMessage> messagesIt = genericMessagesToDatacenters.get(datacenter.datacenterId);
                    if (messagesIt == null || messagesIt.size() == 0) {
                        generatePing(datacenter);
                    }
                }
            } else {
                ArrayList<NetworkMessage> messagesIt = genericMessagesToDatacenters.get(_datacenterId);
                if (messagesIt == null || messagesIt.size() == 0) {
                    generatePing();
                }
            }
        }

        if (!unknownDatacenterIds.isEmpty() && !updatingDcSettings) {
            updateDcSettings(0);
        }

        for (int num : neededDatacenterIds) {
            if (num != movingToDatacenterId) {
                boolean notFound = true;
                for (Action actor : actionQueue) {
                    if (actor instanceof HandshakeAction) {
                        HandshakeAction eactor = (HandshakeAction)actor;
                        if (eactor.datacenter.datacenterId == num) {
                            notFound = false;
                            break;
                        }
                    }
                }
                if (notFound) {
                    HandshakeAction actor = new HandshakeAction(datacenterWithId(num));
                    actor.delegate = this;
                    dequeueActor(actor, true);
                }
            }
        }

        for (int num : unauthorizedDatacenterIds) {
            if (num != currentDatacenterId && num != movingToDatacenterId && UserConfig.clientUserId != 0) {
                boolean notFound = true;
                for (Action actor : actionQueue) {
                    if (actor instanceof ExportAuthorizationAction) {
                        ExportAuthorizationAction eactor = (ExportAuthorizationAction)actor;
                        if (eactor.datacenter.datacenterId == num) {
                            notFound = false;
                            break;
                        }
                    }
                }
                if (notFound) {
                    ExportAuthorizationAction actor = new ExportAuthorizationAction(datacenterWithId(num));
                    actor.delegate = this;
                    dequeueActor(actor, true);
                }
            }
        }
    }

    void addMessageToDatacenter(HashMap<Integer, ArrayList<NetworkMessage>> pMap, int datacenterId, NetworkMessage message) {
        ArrayList<NetworkMessage> arr = pMap.get(datacenterId);
        if (arr == null) {
            arr = new ArrayList<NetworkMessage>();
            pMap.put(datacenterId, arr);
        }
        arr.add(message);
    }

    TLRPC.TL_protoMessage wrapMessage(TLObject message, long sessionId, boolean meaningful) {
        SerializedData os = new SerializedData(true);
        message.serializeToStream(os);

        if (os.length() != 0) {
            TLRPC.TL_protoMessage protoMessage = new TLRPC.TL_protoMessage();
            protoMessage.msg_id = generateMessageId();
            protoMessage.bytes = os.length();
            protoMessage.body = message;
            protoMessage.seqno = generateMessageSeqNo(sessionId, meaningful);
            return protoMessage;
        } else {
            FileLog.e("tmessages", "***** Couldn't serialize " + message);
            return null;
        }
    }

    void proceedToSendingMessages(ArrayList<NetworkMessage> messageList, long sessionId, TcpConnection connection, boolean reportAck, boolean requestShortTimeout) {
        if (sessionId == 0) {
            return;
        }

        ArrayList<NetworkMessage> messages = new ArrayList<NetworkMessage>();
        if(messageList != null) {
            messages.addAll(messageList);
        }

        final ArrayList<Long> arr = messagesIdsForConfirmation.get(sessionId);
        if (arr != null && arr.size() != 0) {
            TLRPC.TL_msgs_ack msgAck = new TLRPC.TL_msgs_ack();
            msgAck.msg_ids = new ArrayList<Long>();
            msgAck.msg_ids.addAll(arr);

            SerializedData os = new SerializedData(true);
            msgAck.serializeToStream(os);

            if (os.length() != 0) {
                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = new TLRPC.TL_protoMessage();

                networkMessage.protoMessage.msg_id = generateMessageId();
                networkMessage.protoMessage.seqno = generateMessageSeqNo(sessionId, false);

                networkMessage.protoMessage.bytes = os.length();
                networkMessage.protoMessage.body = msgAck;

                messages.add(networkMessage);
            } else {
                FileLog.e("tmessages", "***** Couldn't serialize ");
            }

            arr.clear();
        }

        sendMessagesToTransport(messages, connection, sessionId, reportAck, requestShortTimeout);
    }

    void sendMessagesToTransport(ArrayList<NetworkMessage> messagesToSend, TcpConnection connection, long sessionId, boolean reportAck, boolean requestShortTimeout) {
        if (messagesToSend.size() == 0) {
            return;
        }

        if (connection == null) {
            FileLog.e("tmessages", String.format("***** Transport for session 0x%x not found", sessionId));
            return;
        }

        ArrayList<NetworkMessage> currentMessages = new ArrayList<NetworkMessage>();

        int currentSize = 0;
        for (int a = 0; a < messagesToSend.size(); a++) {
            NetworkMessage networkMessage = messagesToSend.get(a);
            currentMessages.add(networkMessage);

            TLRPC.TL_protoMessage protoMessage = networkMessage.protoMessage;

            currentSize += protoMessage.bytes;

            if (currentSize >= 3 * 1024 || a == messagesToSend.size() - 1) {
                ArrayList<Integer> quickAckId = new ArrayList<Integer>();
                byte[] transportData = createConnectionData(currentMessages, sessionId, quickAckId, connection);

                if (transportData != null) {
                    if (reportAck && quickAckId.size() != 0) {
                        ArrayList<Long> requestIds = new ArrayList<Long>();

                        for (NetworkMessage message : messagesToSend) {
                            if (message.requestId != 0) {
                                requestIds.add(message.requestId);
                            }
                        }

                        if (requestIds.size() != 0) {
                            int ack = quickAckId.get(0);
                            ArrayList<Long> arr = quickAckIdToRequestIds.get(ack);
                            if (arr == null) {
                                arr = new ArrayList<Long>();
                                quickAckIdToRequestIds.put(ack, arr);
                            }
                            arr.addAll(requestIds);
                        }
                    }

                    connection.sendData(transportData, reportAck, requestShortTimeout);
                } else {
                    FileLog.e("tmessages", "***** Transport data is nil");
                }

                currentSize = 0;
                currentMessages.clear();
            }
        }
    }

    @SuppressWarnings("unused")
    byte[] createConnectionData(ArrayList<NetworkMessage> messages, long sessionId, ArrayList<Integer> quickAckId, TcpConnection connection) {
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());
        if (datacenter.authKey == null) {
            return null;
        }

        long messageId;
        TLObject messageBody;
        int messageSeqNo;

        if (messages.size() == 1) {
            NetworkMessage networkMessage = messages.get(0);
            TLRPC.TL_protoMessage message = networkMessage.protoMessage;

            if (BuildVars.DEBUG_VERSION) {
                if (message.body instanceof TLRPC.invokeWithLayer12) {
                    FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer12)message.body).query);
                } else if (message.body instanceof TLRPC.initConnection) {
                    TLRPC.initConnection r = (TLRPC.initConnection)message.body;
                    if (r.query instanceof TLRPC.invokeWithLayer12) {
                        FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer12)r.query).query);
                    } else {
                        FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + r.query);
                    }
                } else {
                    FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + message.body);
                }
            }

            long msg_time = getTimeFromMsgId(message.msg_id);
            long currentTime = System.currentTimeMillis() + ((long)timeDifference) * 1000;

            if (msg_time < currentTime - 30000 || msg_time > currentTime + 25000) {
                FileLog.d("tmessages", "wrap in messages continaer");
                TLRPC.TL_msg_container messageContainer = new TLRPC.TL_msg_container();
                messageContainer.messages = new ArrayList<TLRPC.TL_protoMessage>();
                messageContainer.messages.add(message);

                messageId = generateMessageId();
                messageBody = messageContainer;
                messageSeqNo = generateMessageSeqNo(sessionId, false);
            } else {
                messageId = message.msg_id;
                messageBody = message.body;
                messageSeqNo = message.seqno;
            }
        } else {
            TLRPC.TL_msg_container messageContainer = new TLRPC.TL_msg_container();

            ArrayList<TLRPC.TL_protoMessage> containerMessages = new ArrayList<TLRPC.TL_protoMessage>(messages.size());

            for (NetworkMessage networkMessage : messages) {
                TLRPC.TL_protoMessage message = networkMessage.protoMessage;
                containerMessages.add(message);
                if (BuildVars.DEBUG_VERSION) {
                    if (message.body instanceof TLRPC.invokeWithLayer12) {
                        FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer12)message.body).query);
                    } else if (message.body instanceof TLRPC.initConnection) {
                        TLRPC.initConnection r = (TLRPC.initConnection)message.body;
                        if (r.query instanceof TLRPC.invokeWithLayer12) {
                            FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer12)r.query).query);
                        } else {
                            FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + r.query);
                        }
                    } else {
                        FileLog.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + message.body);
                    }
                }
            }

            messageContainer.messages = containerMessages;

            messageId = generateMessageId();
            messageBody = messageContainer;
            messageSeqNo = generateMessageSeqNo(sessionId, false);
        }

        SerializedData innerMessageOs = new SerializedData();
        messageBody.serializeToStream(innerMessageOs);
        byte[] messageData = innerMessageOs.toByteArray();

        SerializedData innerOs = new SerializedData(8 + 8 + 8 + 4 + 4 + messageData.length);
        long serverSalt = datacenter.selectServerSalt(getCurrentTime());
        if (serverSalt == 0) {
            innerOs.writeInt64(0);
        } else {
            innerOs.writeInt64(serverSalt);
        }
        innerOs.writeInt64(sessionId);
        innerOs.writeInt64(messageId);
        innerOs.writeInt32(messageSeqNo);
        innerOs.writeInt32(messageData.length);
        innerOs.writeRaw(messageData);
        byte[] innerData = innerOs.toByteArray();

        byte[] messageKeyFull = Utilities.computeSHA1(innerData);
        byte[] messageKey = new byte[16];
        System.arraycopy(messageKeyFull, messageKeyFull.length - 16, messageKey, 0, 16);

        if (quickAckId != null) {
            SerializedData data = new SerializedData(messageKeyFull);
            quickAckId.add(data.readInt32() & 0x7fffffff);
        }

        MessageKeyData keyData = Utilities.generateMessageKeyData(datacenter.authKey, messageKey, false);

        SerializedData dataForEncryption = new SerializedData(innerData.length + (innerData.length % 16));
        dataForEncryption.writeRaw(innerData);
        byte[] b = new byte[1];
        while (dataForEncryption.length() % 16 != 0) {
            MessagesController.random.nextBytes(b);
            dataForEncryption.writeByte(b[0]);
        }

        byte[] encryptedData = Utilities.aesIgeEncryption(dataForEncryption.toByteArray(), keyData.aesKey, keyData.aesIv, true, false, 0);

        try {
            SerializedData data = new SerializedData(8 + messageKey.length + encryptedData.length);
            data.writeInt64(datacenter.authKeyId);
            data.writeRaw(messageKey);
            data.writeRaw(encryptedData);

            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            innerData = null;
            messageData = null;
            System.gc();
            SerializedData data = new SerializedData();
            data.writeInt64(datacenter.authKeyId);
            data.writeRaw(messageKey);
            data.writeRaw(encryptedData);

            return data.toByteArray();
        }
    }

    void refillSaltSet(final Datacenter datacenter) {
        for (RPCRequest request : requestQueue) {
            if (request.rawRequest instanceof TLRPC.TL_get_future_salts) {
                return;
            }
        }

        for (RPCRequest request : runningRequests) {
            if (request.rawRequest instanceof TLRPC.TL_get_future_salts) {
                return;
            }
        }

        TLRPC.TL_get_future_salts getFutureSalts = new TLRPC.TL_get_future_salts();
        getFutureSalts.num = 64;

        performRpc(getFutureSalts, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                TLRPC.TL_futuresalts res = (TLRPC.TL_futuresalts)response;
                if (error == null) {
                    int currentTime = getCurrentTime();
                    datacenter.mergeServerSalts(currentTime, res.salts);
                    saveSession();
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric, datacenter.datacenterId);
    }

    void messagesConfirmed(final long requestMsgId) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (RPCRequest request : runningRequests) {
                    if (requestMsgId == request.runningMessageId) {
                        request.confirmed = true;
                    }
                }
            }
        });
    }

    void rpcCompleted(final long requestMsgId) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < runningRequests.size(); i++) {
                    RPCRequest request = runningRequests.get(i);
                    removeRequestInClass(request.token);
                    if (request.respondsToMessageId(requestMsgId)) {
                        runningRequests.remove(i);
                        i--;
                    }
                }
            }
        });
    }

    void processMessage(TLObject message, long messageId, int messageSeqNo, long messageSalt, TcpConnection connection, long sessionId, long innerMsgId, long containerMessageId) {
        if (message == null) {
            FileLog.e("tmessages", "message is null");
            return;
        }
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());

        if (message instanceof TLRPC.TL_new_session_created) {
            TLRPC.TL_new_session_created newSession = (TLRPC.TL_new_session_created)message;
            ArrayList<Long> arr = processedSessionChanges.get(sessionId);
            if (arr == null) {
                arr = new ArrayList<Long>();
                processedSessionChanges.put(sessionId, arr);
            }
            if (!arr.contains(newSession.unique_id)) {
                FileLog.d("tmessages", "New session:");
                FileLog.d("tmessages", String.format("    first message id: %d", newSession.first_msg_id));
                FileLog.d("tmessages", String.format("    server salt: %d", newSession.server_salt));
                FileLog.d("tmessages", String.format("    unique id: %d", newSession.unique_id));

                long serverSalt = newSession.server_salt;

                ServerSalt serverSaltDesc = new ServerSalt();
                serverSaltDesc.validSince = getCurrentTime();
                serverSaltDesc.validUntil = getCurrentTime() + 30 * 60;
                serverSaltDesc.value = serverSalt;
                datacenter.addServerSalt(serverSaltDesc);

                for (RPCRequest request : runningRequests) {
                    Datacenter dcenter = datacenterWithId(request.runningDatacenterId);
                    if (request.runningMessageId < newSession.first_msg_id && (request.flags & connection.transportRequestClass) != 0 && dcenter != null && dcenter.datacenterId == datacenter.datacenterId) {
                        request.runningMessageId = 0;
                        request.runningMessageSeqNo = 0;
                        request.runningStartTime = 0;
                        request.runningMinStartTime = 0;
                        request.transportChannelToken = 0;
                    }
                }

                saveSession();

                if (sessionId == datacenter.authSessionId && datacenter.datacenterId == currentDatacenterId && UserConfig.clientActivated) {
                    MessagesController.getInstance().getDifference();
                }
                arr.add(newSession.unique_id);
            }
        } else if (message instanceof TLRPC.TL_msg_container) {
            /*if (messageId != 0) {
                long time = getTimeFromMsgId(messageId);
                long currentTime = System.currentTimeMillis();
                timeDifference = (int)((time - currentTime) / 1000 - currentPingTime / 2.0);
            }*/

            TLRPC.TL_msg_container messageContainer = (TLRPC.TL_msg_container)message;
            for (TLRPC.TL_protoMessage innerMessage : messageContainer.messages) {
                long innerMessageId = innerMessage.msg_id;
                if (innerMessage.seqno % 2 != 0) {
                    ArrayList<Long> set = messagesIdsForConfirmation.get(sessionId);
                    if (set == null) {
                        set = new ArrayList<Long>();
                        messagesIdsForConfirmation.put(sessionId, set);
                    }
                    set.add(innerMessageId);
                }
                if (isMessageIdProcessed(sessionId, innerMessageId)) {
                    continue;
                }
                processMessage(innerMessage.body, 0, innerMessage.seqno, messageSalt, connection, sessionId, innerMessageId, messageId);
                addProcessedMessageId(sessionId, innerMessageId);
            }
        } else if (message instanceof TLRPC.TL_pong) {
            TLRPC.TL_pong pong = (TLRPC.TL_pong)message;
            long pingId = pong.ping_id;

            ArrayList<Long> itemsToDelete = new ArrayList<Long>();
            for (Long pid : pingIdToDate.keySet()) {
                if (pid == pingId) {
                    int time = pingIdToDate.get(pid);
                    int pingTime = (int)(System.currentTimeMillis() / 1000) - time;

                    if (Math.abs(pingTime) < 10) {
                        currentPingTime = (pingTime + currentPingTime) / 2;

                        if (messageId != 0) {
                            long timeMessage = getTimeFromMsgId(messageId);
                            long currentTime = System.currentTimeMillis();
                            timeDifference = (int)((timeMessage - currentTime) / 1000 - currentPingTime / 2.0);
                        }
                    }
                    itemsToDelete.add(pid);
                } else if (pid < pingId) {
                    itemsToDelete.add(pid);
                }
            }
            for (Long pid : itemsToDelete) {
                pingIdToDate.remove(pid);
            }
        } else if (message instanceof TLRPC.TL_futuresalts) {
            TLRPC.TL_futuresalts futureSalts = (TLRPC.TL_futuresalts)message;
            long requestMid = futureSalts.req_msg_id;
            for (RPCRequest request : runningRequests) {
                if (request.respondsToMessageId(requestMid)) {
                    if (request.completionBlock != null) {
                        request.completionBlock.run(futureSalts, null);
                    }

                    futureSalts.freeResources();

                    messagesConfirmed(requestMid);
                    rpcCompleted(requestMid);

                    break;
                }
            }
        } else if (message instanceof TLRPC.DestroySessionRes) {
            TLRPC.DestroySessionRes res = (TLRPC.DestroySessionRes)message;
            ArrayList<Long> lst = new ArrayList<Long>();
            lst.addAll(sessionsToDestroy);
            destroyingSessions.remove(res.session_id);
            for (long session : lst) {
                if (session == res.session_id) {
                    sessionsToDestroy.remove(session);
                    FileLog.d("tmessages", String.format("Destroyed session %d (%s)", res.session_id, res instanceof TLRPC.TL_destroy_session_ok ? "ok" : "not found"));
                    break;
                }
            }
        } else if (message instanceof TLRPC.TL_rpc_result) {
            TLRPC.TL_rpc_result resultContainer = (TLRPC.TL_rpc_result)message;
            long resultMid = resultContainer.req_msg_id;

            boolean ignoreResult = false;
            FileLog.d("tmessages", "object in rpc_result is " + resultContainer.result);
            if (resultContainer.result instanceof TLRPC.RpcError) {
                String errorMessage = ((TLRPC.RpcError)resultContainer.result).error_message;
                FileLog.e("tmessages", String.format("***** RPC error %d: %s", ((TLRPC.RpcError)resultContainer.result).error_code, errorMessage));

                int migrateToDatacenterId = DEFAULT_DATACENTER_ID;

                if (((TLRPC.RpcError)resultContainer.result).error_code == 303) {
                    ArrayList<String> migrateErrors = new ArrayList<String>();
                    migrateErrors.add("NETWORK_MIGRATE_");
                    migrateErrors.add("PHONE_MIGRATE_");
                    migrateErrors.add("USER_MIGRATE_");
                    for (String possibleError : migrateErrors) {
                        if (errorMessage.contains(possibleError)) {
                            String errorMsg = errorMessage.replace(possibleError, "");

                            Pattern pattern = Pattern.compile("[0-9]+");
                            Matcher matcher = pattern.matcher(errorMsg);
                            if (matcher.find()) {
                                errorMsg = matcher.group(0);
                            }

                            Integer val;
                            try {
                                val = Integer.parseInt(errorMsg);
                            } catch (Exception e) {
                                val = null;
                            }

                            if (val != null) {
                                migrateToDatacenterId = val;
                            } else {
                                migrateToDatacenterId = DEFAULT_DATACENTER_ID;
                            }
                        }
                    }
                }

                if (migrateToDatacenterId != DEFAULT_DATACENTER_ID) {
                    ignoreResult = true;
                    moveToDatacenter(migrateToDatacenterId);
                }
            }

            int retryRequestsFromDatacenter = -1;
            int retryRequestsClass = 0;

            if (!ignoreResult) {
                boolean found = false;

                for (RPCRequest request : runningRequests) {
                    if (request.respondsToMessageId(resultMid)) {
                        found = true;

                        boolean discardResponse = false;
                        boolean isError = false;
                        if (request.completionBlock != null) {
                            TLRPC.TL_error implicitError = null;
                            if (resultContainer.result instanceof TLRPC.TL_gzip_packed) {
                                TLRPC.TL_gzip_packed packet = (TLRPC.TL_gzip_packed)resultContainer.result;
                                TLObject uncomressed = Utilities.decompress(packet.packed_data, request.rawRequest);
                                if (uncomressed == null) {
                                    System.gc();
                                    uncomressed = Utilities.decompress(packet.packed_data, request.rawRequest);
                                }
                                if (uncomressed == null) {
                                    throw new RuntimeException("failed to decomress responce for " + request.rawRequest);
                                }
                                resultContainer.result = uncomressed;
                            }
                            if (resultContainer.result instanceof TLRPC.RpcError) {
                                String errorMessage = ((TLRPC.RpcError) resultContainer.result).error_message;
                                FileLog.e("tmessages", String.format("***** RPC error %d: %s", ((TLRPC.RpcError) resultContainer.result).error_code, errorMessage));

                                int errorCode = ((TLRPC.RpcError) resultContainer.result).error_code;

                                if (errorCode == 500 || errorCode < 0) {
                                    if ((request.flags & RPCRequest.RPCRequestClassFailOnServerErrors) != 0) {
                                        if (request.serverFailureCount < 1) {
                                            discardResponse = true;
                                            request.runningMinStartTime = request.runningStartTime + 1;
                                            request.serverFailureCount++;
                                        }
                                    } else {
                                        discardResponse = true;
                                        request.runningMinStartTime = request.runningStartTime + 1;
                                        request.confirmed = false;
                                    }
                                } else if (errorCode == 420) {
                                    if ((request.flags & RPCRequest.RPCRequestClassFailOnServerErrors) == 0) {
                                        double waitTime = 2.0;

                                        if (errorMessage.contains("FLOOD_WAIT_")) {
                                            String errorMsg = errorMessage.replace("FLOOD_WAIT_", "");

                                            Pattern pattern = Pattern.compile("[0-9]+");
                                            Matcher matcher = pattern.matcher(errorMsg);
                                            if (matcher.find()) {
                                                errorMsg = matcher.group(0);
                                            }

                                            Integer val;
                                            try {
                                                val = Integer.parseInt(errorMsg);
                                            } catch (Exception e) {
                                                val = null;
                                            }
                                            if (val != null) {
                                                waitTime = val;
                                            }
                                        }

                                        waitTime = Math.min(30, waitTime);

                                        discardResponse = true;
                                        request.runningMinStartTime = (int)(System.currentTimeMillis() / 1000 + waitTime);
                                        request.confirmed = false;
                                    }
                                }

                                implicitError = new TLRPC.TL_error();
                                implicitError.code = ((TLRPC.RpcError)resultContainer.result).error_code;
                                implicitError.text = ((TLRPC.RpcError)resultContainer.result).error_message;
                            } else if (!(resultContainer.result instanceof TLRPC.TL_error)) {
                                if (request.rawRequest == null || !request.rawRequest.responseClass().isAssignableFrom(resultContainer.result.getClass())) {
                                    if (request.rawRequest == null) {
                                        FileLog.e("tmessages", "rawRequest is null");
                                    } else {
                                        FileLog.e("tmessages", "***** RPC error: invalid response class " + resultContainer.result + " (" + request.rawRequest.responseClass() + " expected)");
                                    }
                                    implicitError = new TLRPC.TL_error();
                                    implicitError.code = -1000;
                                }
                            }

                            if (!discardResponse) {
                                if (implicitError != null || resultContainer.result instanceof TLRPC.TL_error) {
                                    isError = true;
                                    request.completionBlock.run(null, implicitError != null ? implicitError : (TLRPC.TL_error) resultContainer.result);
                                } else {
                                    request.completionBlock.run(resultContainer.result, null);
                                }
                            }

                            if (implicitError != null && implicitError.code == 401) {
                                isError = true;
                                if (datacenter.datacenterId == currentDatacenterId || datacenter.datacenterId == movingToDatacenterId) {
                                    if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                                        if (UserConfig.clientActivated) {
                                            UserConfig.clearConfig();
                                            Utilities.RunOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    NotificationCenter.getInstance().postNotificationName(1234);
                                                }
                                            });
                                        }
                                    }
                                } else {
                                    datacenter.authorized = false;
                                    saveSession();
                                    discardResponse = true;
                                    if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0 || (request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                                        retryRequestsFromDatacenter = datacenter.datacenterId;
                                        retryRequestsClass = request.flags;
                                    }
                                }
                            }
                        }

                        if (!discardResponse) {
                            if (request.initRequest && !isError) {
                                if (datacenter.lastInitVersion != currentAppVersion) {
                                    datacenter.lastInitVersion = currentAppVersion;
                                    saveSession();
                                    FileLog.e("tmessages", "init connection completed");
                                } else {
                                    FileLog.e("tmessages", "rpc is init, but init connection already completed");
                                }
                            }
                            rpcCompleted(resultMid);
                        } else {
                            request.runningMessageId = 0;
                            request.runningMessageSeqNo = 0;
                            request.transportChannelToken = 0;
                        }
                        break;
                    }
                }

                resultContainer.freeResources();

                if (!found) {
                    FileLog.d("tmessages", "Response received, but request wasn't found.");
                    rpcCompleted(resultMid);
                }

                messagesConfirmed(resultMid);
            }

            if (retryRequestsFromDatacenter >= 0) {
                processRequestQueue(retryRequestsClass, retryRequestsFromDatacenter);
            } else {
                processRequestQueue(0, 0);
            }
        } else if (message instanceof TLRPC.TL_msgs_ack) {

        } else if (message instanceof TLRPC.TL_ping) {

        } else if (message instanceof TLRPC.TL_bad_msg_notification) {
            TLRPC.TL_bad_msg_notification badMsgNotification = (TLRPC.TL_bad_msg_notification)message;

            FileLog.e("tmessages", String.format("***** Bad message: %d", badMsgNotification.error_code));

            if (badMsgNotification.error_code == 16 || badMsgNotification.error_code == 17 || badMsgNotification.error_code == 19 || badMsgNotification.error_code == 32 || badMsgNotification.error_code == 33 || badMsgNotification.error_code == 64) {
                long realId = messageId != 0 ? messageId : containerMessageId;
                if (realId == 0) {
                    realId = innerMsgId;
                }

                if (realId != 0) {
                    long time = getTimeFromMsgId(messageId);
                    long currentTime = System.currentTimeMillis();
                    timeDifference = (int)((time - currentTime) / 1000 - currentPingTime / 2.0);
                }

                recreateSession(datacenter.authSessionId, datacenter);
                saveSession();

                lastOutgoingMessageId = 0;
                clearRequestsForRequestClass(connection.transportRequestClass, datacenter);
            }
        } else if (message instanceof TLRPC.TL_bad_server_salt) {
            if (messageId != 0) {
                long time = getTimeFromMsgId(messageId);
                long currentTime = System.currentTimeMillis();
                timeDifference = (int)((time - currentTime) / 1000 - currentPingTime / 2.0);

                lastOutgoingMessageId = Math.max(messageId, lastOutgoingMessageId);
            }

            datacenter.clearServerSalts();

            ServerSalt serverSaltDesc = new ServerSalt();
            serverSaltDesc.validSince = getCurrentTime();
            serverSaltDesc.validUntil = getCurrentTime() + 30 * 60;
            serverSaltDesc.value = messageSalt;

            datacenter.addServerSalt(serverSaltDesc);
            saveSession();

            refillSaltSet(datacenter);
            if (datacenter.authKey != null) {
                processRequestQueue(RPCRequest.RPCRequestClassTransportMask, datacenter.datacenterId);
            }
        } else if (message instanceof TLRPC.MsgDetailedInfo) {
            TLRPC.MsgDetailedInfo detailedInfo = (TLRPC.MsgDetailedInfo)message;

            boolean requestResend = false;

            if (detailedInfo instanceof TLRPC.TL_msg_detailed_info) {
                long requestMid = ((TLRPC.TL_msg_detailed_info)detailedInfo).msg_id;
                for (RPCRequest request : runningRequests) {
                    if (request.respondsToMessageId(requestMid)) {
                        requestResend = true;
                        break;
                    }
                }
            } else {
                if (!isMessageIdProcessed(sessionId, messageId)) {
                    requestResend = true;
                }
            }

            if (requestResend) {
                TLRPC.TL_msg_resend_req resendReq = new TLRPC.TL_msg_resend_req();
                resendReq.msg_ids.add(detailedInfo.answer_msg_id);

                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = wrapMessage(resendReq, sessionId, false);

                ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                arr.add(networkMessage);
                sendMessagesToTransport(arr, connection, sessionId, false, true);
            } else {
                ArrayList<Long> set = messagesIdsForConfirmation.get(sessionId);
                if (set == null) {
                    set = new ArrayList<Long>();
                    messagesIdsForConfirmation.put(sessionId, set);
                }
                set.add(detailedInfo.answer_msg_id);
            }
        } else if (message instanceof TLRPC.TL_gzip_packed) {
            TLRPC.TL_gzip_packed packet = (TLRPC.TL_gzip_packed)message;
            TLObject result = Utilities.decompress(packet.packed_data, getRequestWithMessageId(messageId));
            processMessage(result, messageId, messageSeqNo, messageSalt, connection, sessionId, innerMsgId, containerMessageId);
        } else if (message instanceof TLRPC.Updates) {
            MessagesController.getInstance().processUpdates((TLRPC.Updates)message, false);
        } else {
            FileLog.e("tmessages", "***** Error: unknown message class " + message);
        }
    }

    void generatePing() {
        for (Datacenter datacenter : datacenters.values()) {
            if (datacenter.datacenterId == currentDatacenterId) {
                generatePing(datacenter);
            }
        }
    }

    static long nextPingId = 0;
    byte[] generatePingData(Datacenter datacenter, boolean recordTime) {
        long sessionId = datacenter.authSessionId;
        if (sessionId == 0) {
            return null;
        }

        TLRPC.TL_ping_delay_disconnect ping = new TLRPC.TL_ping_delay_disconnect();
        ping.ping_id = nextPingId++;
        ping.disconnect_delay = 35;

        if (recordTime && sessionId == datacenter.authSessionId) {
            pingIdToDate.put(ping.ping_id, (int)(System.currentTimeMillis() / 1000));
        }

        NetworkMessage networkMessage = new NetworkMessage();
        networkMessage.protoMessage = wrapMessage(ping, sessionId, false);

        ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
        arr.add(networkMessage);
        return createConnectionData(arr, sessionId, null, datacenter.connection);
    }

    void generatePing(Datacenter datacenter) {
        if (datacenter.connection == null || datacenter.connection.channelToken == 0) {
            return;
        }

        byte[] transportData = generatePingData(datacenter, true);
        if (transportData != null) {
            datacenter.connection.sendData(transportData, false, true);
        }
    }

    public long needsToDecodeMessageIdFromPartialData(TcpConnection connection, byte[] data) {
        if (data == null) {
            return -1;
        }

        Datacenter datacenter = datacenters.get(connection.getDatacenterId());
        SerializedData is = new SerializedData(data);

        byte[] keyId = is.readData(8);
        SerializedData keyIdData = new SerializedData(keyId);
        long key = keyIdData.readInt64();
        if (key == 0) {
            return -1;
        } else {
            if (datacenter.authKeyId == 0 || key != datacenter.authKeyId) {
                FileLog.e("tmessages", "Error: invalid auth key id " + connection);
                return -1;
            }

            byte[] messageKey = is.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(datacenter.authKey, messageKey, true);

            byte[] messageData = is.readData(data.length - 24);
            messageData = Utilities.aesIgeEncryption(messageData, keyData.aesKey, keyData.aesIv, false, false, 0);

            if (messageData == null) {
                return -1;
            }

            SerializedData messageIs = new SerializedData(messageData);
            long messageServerSalt = messageIs.readInt64();
            long messageSessionId = messageIs.readInt64();

            if (messageSessionId != datacenter.authSessionId && messageSessionId != datacenter.authDownloadSessionId && messageSessionId != datacenter.authUploadSessionId) {
                FileLog.e("tmessages", String.format("***** Error: invalid message session ID (%d instead of %d)", messageSessionId, datacenter.authSessionId));
                finishUpdatingState(connection);
                return -1;
            }

            long messageId = messageIs.readInt64();
            int messageSeqNo = messageIs.readInt32();
            int messageLength = messageIs.readInt32();

            boolean[] stop = new boolean[1];
            long[] reqMsgId = new long[1];
            stop[0] = false;
            reqMsgId[0] = 0;

            while (!stop[0] && reqMsgId[0] == 0) {
                int signature = messageIs.readInt32(stop);
                if (stop[0]) {
                    break;
                }
                findReqMsgId(messageIs, signature, reqMsgId, stop);
            }

            return reqMsgId[0];
        }
    }

    private void findReqMsgId(SerializedData is, int signature, long[] reqMsgId, boolean[] failed) {
        if (signature == 0x73f1f8dc) {
            if (is.length() < 4) {
                failed[0] = true;
                return;
            }
            int count = is.readInt32(failed);
            if (failed[0]) {
                return;
            }

            for (int i = 0; i < count; i++) {
                is.readInt64(failed);
                if (failed[0]) {
                    return;
                }
                is.readInt32(failed);
                if (failed[0]) {
                    return;
                }
                is.readInt32(failed);
                if (failed[0]) {
                    return;
                }

                int innerSignature = is.readInt32(failed);
                if (failed[0]) {
                    return;
                }

                findReqMsgId(is, innerSignature, reqMsgId, failed);
                if (failed[0] || reqMsgId[0] != 0) {
                    return;
                }
            }
        } else if (signature == 0xf35c6d01) {
            long value = is.readInt64(failed);
            if (failed[0]) {
                return;
            }
            reqMsgId[0] = value;
        } else if (signature == 0x62d6b459) {
            is.readInt32(failed);
            if (failed[0]) {
                return;
            }

            int count = is.readInt32(failed);
            if (failed[0]) {
                return;
            }

            for (int i = 0; i < count; i++) {
                is.readInt32(failed);
                if (failed[0]) {
                    return;
                }
            }
        } else if (signature == 0x347773c5) {
            is.readInt64(failed);
            if (failed[0]) {
                return;
            }
            is.readInt64(failed);
        }
    }

    //================================================================================
    // TCPConnection delegate
    //================================================================================

    @Override
    public void tcpConnectionProgressChanged(TcpConnection connection, long messageId, int currentSize, int length) {
        for (RPCRequest request : runningRequests) {
            if (request.respondsToMessageId(messageId)) {
                if (request.progressBlock != null) {
                    request.progressBlock.progress(length, currentSize);
                }
                break;
            }
        }
    }

    @Override
    public void tcpConnectionClosed(TcpConnection connection) {
        if (connection.getDatacenterId() == currentDatacenterId && (connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (isNetworkOnline()) {
                connectionState = 2;
            } else {
                connectionState = 1;
            }
            if (BuildVars.DEBUG_VERSION) {
                try {
                    ConnectivityManager cm = (ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo[] networkInfos = cm.getAllNetworkInfo();
                    for (int a = 0; a < 2; a++) {
                        if (a >= networkInfos.length) {
                            break;
                        }
                        NetworkInfo info = networkInfos[a];
                        FileLog.e("tmessages", "Network: " + info.getTypeName() + " status: " + info.getState() + " info: " + info.getExtraInfo() + " object: " + info.getDetailedState() + " other: " + info);
                    }
                    if (networkInfos.length == 0) {
                        FileLog.e("tmessages", "no network available");
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", "NETWORK STATE GET ERROR");
                }
            }
            final int stateCopy = connectionState;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                }
            });
        }
    }

    @Override
    public void tcpConnectionConnected(TcpConnection connection) {
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());
        if (datacenter.authKey != null) {
            processRequestQueue(connection.transportRequestClass, connection.getDatacenterId());
        }
    }

    @Override
    public void tcpConnectionQuiackAckReceived(TcpConnection connection, int ack) {
        ArrayList<Long> arr = quickAckIdToRequestIds.get(ack);
        if (arr != null) {
            for (RPCRequest request : runningRequests) {
                if (arr.contains(request.token)) {
                    if (request.quickAckBlock != null) {
                        request.quickAckBlock.quickAck();
                    }
                }
            }
            quickAckIdToRequestIds.remove(ack);
        }
    }

    private void finishUpdatingState(TcpConnection connection) {
        if (connection.getDatacenterId() == currentDatacenterId && (connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (ConnectionsManager.getInstance().connectionState == 3 && !MessagesController.getInstance().gettingDifference && !MessagesController.getInstance().gettingDifferenceAgain) {
                ConnectionsManager.getInstance().connectionState = 0;
                final int stateCopy = ConnectionsManager.getInstance().connectionState;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                    }
                });
            }
        }
    }

    @Override
    public void tcpConnectionReceivedData(TcpConnection connection, ByteBufferDesc data, int length) {
        if (connection.getDatacenterId() == currentDatacenterId && (connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (connectionState == 1 || connectionState == 2) {
                connectionState = 3;
                final int stateCopy = connectionState;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(703, stateCopy);
                    }
                });
            }
        }
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());

        long keyId = data.readInt64();
        if (keyId == 0) {
            long messageId = data.readInt64();
            if (isMessageIdProcessed(0, messageId)) {
                finishUpdatingState(connection);
                return;
            }

            int messageLength = data.readInt32();
            int constructor = data.readInt32();

            TLObject object = TLClassStore.Instance().TLdeserialize(data, constructor, getRequestWithMessageId(messageId));

            processMessage(object, messageId, 0, 0, connection, 0, 0, 0);

            if (object != null) {
                addProcessedMessageId(0, messageId);
            }
        } else {
            if (datacenter.authKeyId == 0 || keyId != datacenter.authKeyId) {
                FileLog.e("tmessages", "Error: invalid auth key id " + connection);
                connection.suspendConnection(true);
                connection.connect();
                return;
            }

            byte[] messageKey = data.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(datacenter.authKey, messageKey, true);
            data.compact();
            data.limit(data.position());
            data.position(0);

            Utilities.aesIgeEncryption2(data.buffer, keyData.aesKey, keyData.aesIv, false, false, length - 24);
//            if (messageData == null) {
//                FileLog.e("tmessages", "Error: can't decrypt message data " + connection);
//                connection.suspendConnection(true);
//                connection.connect();
//                return;
//            }

            long messageServerSalt = data.readInt64();
            long messageSessionId = data.readInt64();

            if (messageSessionId != datacenter.authSessionId && messageSessionId != datacenter.authDownloadSessionId && messageSessionId != datacenter.authUploadSessionId) {
                FileLog.e("tmessages", String.format("***** Error: invalid message session ID (%d instead of %d)", messageSessionId, datacenter.authSessionId));
                finishUpdatingState(connection);
                return;
            }

            boolean doNotProcess = false;

            long messageId = data.readInt64();
            int messageSeqNo = data.readInt32();
            int messageLength = data.readInt32();

            if (isMessageIdProcessed(messageSessionId, messageId)) {
                doNotProcess = true;
            }

            if (messageSeqNo % 2 != 0) {
                ArrayList<Long> set = messagesIdsForConfirmation.get(messageSessionId);
                if (set == null) {
                    set = new ArrayList<Long>();
                    messagesIdsForConfirmation.put(messageSessionId, set);
                }
                set.add(messageId);
            }

            byte[] realMessageKeyFull = Utilities.computeSHA1(data.buffer, 0, Math.min(messageLength + 32, data.limit()));
            if (realMessageKeyFull == null) {
                return;
            }
            byte[] realMessageKey = new byte[16];
            System.arraycopy(realMessageKeyFull, realMessageKeyFull.length - 16, realMessageKey, 0, 16);

            if (!Arrays.equals(messageKey, realMessageKey)) {
                FileLog.e("tmessages", "***** Error: invalid message key");
                connection.suspendConnection(true);
                connection.connect();
                return;
            }

            if (!doNotProcess) {
                int constructor = data.readInt32();
                TLObject message = TLClassStore.Instance().TLdeserialize(data, constructor, getRequestWithMessageId(messageId));

                if (message == null) {
                    FileLog.e("tmessages", "***** Error parsing message: " + constructor);
                } else {
                    processMessage(message, messageId, messageSeqNo, messageServerSalt, connection, messageSessionId, 0, 0);

                    addProcessedMessageId(messageSessionId, messageId);
                }
            } else {
                proceedToSendingMessages(null, messageSessionId, connection, false, false);
            }
            finishUpdatingState(connection);
        }
    }

    public TLObject getRequestWithMessageId(long msgId) {
        for (RPCRequest request : runningRequests) {
            if (msgId == request.runningMessageId) {
                return request.rawRequest;
            }
        }
        return null;
    }

    //================================================================================
    // Move to datacenter manage
    //================================================================================

    void moveToDatacenter(final int datacenterId) {
        if (movingToDatacenterId == datacenterId) {
            return;
        }
        movingToDatacenterId = datacenterId;

        Datacenter currentDatacenter = datacenterWithId(currentDatacenterId);
        clearRequestsForRequestClass(RPCRequest.RPCRequestClassGeneric, currentDatacenter);
        clearRequestsForRequestClass(RPCRequest.RPCRequestClassDownloadMedia, currentDatacenter);
        clearRequestsForRequestClass(RPCRequest.RPCRequestClassUploadMedia, currentDatacenter);

        if (UserConfig.clientUserId != 0) {
            TLRPC.TL_auth_exportAuthorization exportAuthorization = new TLRPC.TL_auth_exportAuthorization();
            exportAuthorization.dc_id = datacenterId;

            performRpc(exportAuthorization, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        movingAuthorization = (TLRPC.TL_auth_exportedAuthorization)response;
                        authorizeOnMovingDatacenter();
                    } else {
                        Utilities.globalQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                moveToDatacenter(datacenterId);
                            }
                        }, 1000);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric, currentDatacenterId);
        } else {
            authorizeOnMovingDatacenter();
        }
    }

    void authorizeOnMovingDatacenter() {
        Datacenter datacenter = datacenterWithId(movingToDatacenterId);
        if (datacenter == null) {
            if (!updatingDcSettings) {
                updateDcSettings(0);
            }
            return;
        }

        recreateSession(datacenter.authSessionId, datacenter);

        if (datacenter.authKey == null) {
            datacenter.clearServerSalts();
            HandshakeAction actor = new HandshakeAction(datacenter);
            actor.delegate = this;
            dequeueActor(actor, true);
        }

        if (movingAuthorization != null) {
            TLRPC.TL_auth_importAuthorization importAuthorization = new TLRPC.TL_auth_importAuthorization();
            importAuthorization.id = UserConfig.clientUserId;
            importAuthorization.bytes = movingAuthorization.bytes;
            performRpc(importAuthorization, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    movingAuthorization = null;
                    if (error == null) {
                        authorizedOnMovingDatacenter();
                    } else {
                        moveToDatacenter(movingToDatacenterId);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric, datacenter.datacenterId);
        } else {
            authorizedOnMovingDatacenter();
        }
    }

    void authorizedOnMovingDatacenter() {
        Datacenter datacenter = datacenterWithId(currentDatacenterId);
        if (datacenter != null && datacenter.connection != null) {
            datacenter.connection.suspendConnection(true);
        }
        movingAuthorization = null;
        currentDatacenterId = movingToDatacenterId;
        movingToDatacenterId = DEFAULT_DATACENTER_ID;
        saveSession();
        processRequestQueue(0, 0);
    }

    //================================================================================
    // Actors manage
    //================================================================================

    public void dequeueActor(final Action actor, final boolean execute) {
        if (actionQueue.size() == 0 || execute) {
            actor.execute(null);
        }
        actionQueue.add(actor);
    }

    public void cancelActor(final Action actor) {
        if (actor != null) {
            actionQueue.remove(actor);
        }
    }

    @Override
    public void ActionDidFinishExecution(final Action action, HashMap<String, Object> params) {
        if (action instanceof HandshakeAction) {
            HandshakeAction eactor = (HandshakeAction)action;
            eactor.datacenter.connection.delegate = this;
            saveSession();

            if (eactor.datacenter.datacenterId == currentDatacenterId || eactor.datacenter.datacenterId == movingToDatacenterId) {
                timeDifference = (Integer)params.get("timeDifference");

                recreateSession(eactor.datacenter.authSessionId, eactor.datacenter);
            }
            processRequestQueue(RPCRequest.RPCRequestClassTransportMask, eactor.datacenter.datacenterId);
        } else if (action instanceof ExportAuthorizationAction) {
            ExportAuthorizationAction eactor = (ExportAuthorizationAction)action;

            Datacenter datacenter = eactor.datacenter;
            datacenter.authorized = true;
            saveSession();
            processRequestQueue(RPCRequest.RPCRequestClassTransportMask, datacenter.datacenterId);
        }
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                actionQueue.remove(action);
                action.delegate = null;
            }
        });
    }

    @Override
    public void ActionDidFailExecution(final Action action) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                actionQueue.remove(action);
                action.delegate = null;
            }
        });
    }
}
