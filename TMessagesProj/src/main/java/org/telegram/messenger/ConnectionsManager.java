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
import android.os.PowerManager;
import android.util.Base64;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionsManager implements Action.ActionDelegate, TcpConnection.TcpConnectionDelegate {
    private HashMap<Integer, Datacenter> datacenters = new HashMap<>();

    private ArrayList<Long> sessionsToDestroy = new ArrayList<>();
    private ArrayList<Long> destroyingSessions = new ArrayList<>();
    private HashMap<Integer, ArrayList<Long>> quickAckIdToRequestIds = new HashMap<>();

    private HashMap<Long, Integer> pingIdToDate = new HashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Long>> requestsByGuids = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<Long, Integer> requestsByClass = new ConcurrentHashMap<>(100, 1.0f, 2);
    private volatile int connectionState = 2;

    private ArrayList<RPCRequest> requestQueue = new ArrayList<>();
    private ArrayList<RPCRequest> runningRequests = new ArrayList<>();
    private ArrayList<Action> actionQueue = new ArrayList<>();

    private ArrayList<Integer> unknownDatacenterIds = new ArrayList<>();
    private ArrayList<Integer> neededDatacenterIds = new ArrayList<>();
    private ArrayList<Integer> unauthorizedDatacenterIds = new ArrayList<>();
    private final HashMap<Integer, ArrayList<NetworkMessage>> genericMessagesToDatacenters = new HashMap<>();

    private TLRPC.TL_auth_exportedAuthorization movingAuthorization;
    public static final int DEFAULT_DATACENTER_ID = Integer.MAX_VALUE;
    private static final int DC_UPDATE_TIME = 60 * 60;
    protected int currentDatacenterId;
    protected int movingToDatacenterId;
    private long lastOutgoingMessageId = 0;
    private int isTestBackend = 0;
    private int timeDifference = 0;
    private int currentPingTime;
    private int lastDestroySessionRequestTime;
    private boolean updatingDcSettings = false;
    private int updatingDcStartTime = 0;
    private int lastDcUpdateTime = 0;
    private int currentAppVersion = 0;
    private long pushSessionId;
    private boolean registeringForPush = false;

    private boolean paused = false;
    private long lastPingTime = System.currentTimeMillis();
    private long lastPushPingTime = 0;
    private boolean pushMessagesReceived = true;
    private boolean sendingPushPing = false;
    private int nextSleepTimeout = 30000;
    private long nextPingId = 0;

    private long lastPauseTime = System.currentTimeMillis();
    private boolean appPaused = true;

    private volatile long nextCallToken = 1;

    private PowerManager.WakeLock wakeLock = null;

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

    private Runnable stageRunnable = new Runnable() {
        @Override
        public void run() {
            Utilities.stageQueue.handler.removeCallbacks(stageRunnable);
            if (datacenters != null) {
                Datacenter datacenter = datacenterWithId(currentDatacenterId);
                if (sendingPushPing && lastPushPingTime < System.currentTimeMillis() - 30000 || Math.abs(lastPushPingTime - System.currentTimeMillis()) > 60000 * 3 + 10000) {
                    lastPushPingTime = 0;
                    sendingPushPing = false;
                    if (datacenter != null && datacenter.pushConnection != null) {
                        datacenter.pushConnection.suspendConnection(true);
                    }
                    FileLog.e("tmessages", "push ping timeout");
                }
                if (lastPushPingTime < System.currentTimeMillis() - 60000 * 3) {
                    FileLog.e("tmessages", "time for push ping");
                    lastPushPingTime = System.currentTimeMillis();
                    if (datacenter != null) {
                        generatePing(datacenter, true);
                    }
                }
            }

            long currentTime = System.currentTimeMillis();
            if (lastPauseTime != 0 && lastPauseTime < currentTime - nextSleepTimeout) {
                boolean dontSleep = !pushMessagesReceived;
                if (!dontSleep) {
                    for (RPCRequest request : runningRequests) {
                        if (request.rawRequest instanceof TLRPC.TL_get_future_salts) {
                            dontSleep = true;
                        } else if (request.retryCount < 10 && (request.runningStartTime + 60 > (int) (currentTime / 1000)) && ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0 || (request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0)) {
                            dontSleep = true;
                            break;
                        }
                    }
                }
                if (!dontSleep) {
                    for (RPCRequest request : requestQueue) {
                        if (request.rawRequest instanceof TLRPC.TL_get_future_salts) {
                            dontSleep = true;
                        } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0 || (request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                            dontSleep = true;
                            break;
                        }
                    }
                }
                if (!dontSleep) {
                    if (!paused) {
                        FileLog.e("tmessages", "pausing network and timers by sleep time = " + nextSleepTimeout);
                        for (Datacenter datacenter : datacenters.values()) {
                            datacenter.suspendConnections();
                        }
                    }
                    try {
                        paused = true;
                        Utilities.stageQueue.postRunnable(stageRunnable, 1000);
                        return;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                } else {
                    lastPauseTime += 30 * 1000;
                    FileLog.e("tmessages", "don't sleep 30 seconds because of salt, upload or download request");
                }
            }
            if (paused) {
                paused = false;
                FileLog.e("tmessages", "resume network and timers");
            }

            if (datacenters != null) {
                MessagesController.getInstance().updateTimerProc();
                Datacenter datacenter = datacenterWithId(currentDatacenterId);
                if (datacenter != null) {
                    if (datacenter.authKey != null) {
                        if (lastPingTime < System.currentTimeMillis() - 19000) {
                            lastPingTime = System.currentTimeMillis();
                            generatePing();
                        }
                        if (!updatingDcSettings && lastDcUpdateTime < (int) (System.currentTimeMillis() / 1000) - DC_UPDATE_TIME) {
                            updateDcSettings(0);
                        }
                        processRequestQueue(0, 0);
                    } else {
                        boolean notFound = true;
                        for (Action actor : actionQueue) {
                            if (actor instanceof HandshakeAction) {
                                HandshakeAction eactor = (HandshakeAction)actor;
                                if (eactor.datacenter.datacenterId == datacenter.datacenterId) {
                                    notFound = false;
                                    break;
                                }
                            }
                        }
                        if (notFound) {
                            HandshakeAction actor = new HandshakeAction(datacenter);
                            actor.delegate = ConnectionsManager.this;
                            dequeueActor(actor, true);
                        }
                    }
                }
            }

            Utilities.stageQueue.postRunnable(stageRunnable, 1000);
        }
    };

    public ConnectionsManager() {
        currentAppVersion = ApplicationLoader.getAppVersion();
        lastOutgoingMessageId = 0;
        movingToDatacenterId = DEFAULT_DATACENTER_ID;
        loadSession();

        if (!isNetworkOnline()) {
            connectionState = 1;
        }

        Utilities.stageQueue.postRunnable(stageRunnable, 1000);

        try {
            PowerManager pm = (PowerManager)ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lock");
            wakeLock.setReferenceCounted(false);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public int getConnectionState() {
        return connectionState;
    }

    public void setConnectionState(int state) {
        connectionState = state;
    }

    private void resumeNetworkInternal() {
        if (paused) {
            lastPauseTime = System.currentTimeMillis();
            nextSleepTimeout = 30000;
            FileLog.e("tmessages", "wakeup network in background");
        } else if (lastPauseTime != 0) {
            lastPauseTime = System.currentTimeMillis();
            FileLog.e("tmessages", "reset sleep timeout");
        }
    }

    public void resumeNetworkMaybe() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                resumeNetworkInternal();
            }
        });
    }

    public void applicationMovedToForeground() {
        Utilities.stageQueue.postRunnable(stageRunnable);
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (paused) {
                    nextSleepTimeout = 30000;
                    FileLog.e("tmessages", "reset timers by application moved to foreground");
                }
            }
        });
    }

    public void setAppPaused(final boolean value, final boolean byScreenState) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!byScreenState) {
                    appPaused = value;
                    FileLog.e("tmessages", "app paused = " + value);
                }
                if (value) {
                    if (byScreenState) {
                        if (lastPauseTime == 0) {
                            lastPauseTime = System.currentTimeMillis();
                        }
                    } else {
                        lastPauseTime = System.currentTimeMillis();
                    }
                } else {
                    if (appPaused) {
                        return;
                    }
                    FileLog.e("tmessages", "reset app pause time");
                    if (lastPauseTime != 0 && System.currentTimeMillis() - lastPauseTime > 5000) {
                        ContactsController.getInstance().checkContacts();
                    }
                    lastPauseTime = 0;
                    ConnectionsManager.getInstance().applicationMovedToForeground();
                }
            }
        });
    }

    public long getPauseTime() {
        return lastPauseTime;
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

    public void switchBackend() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (isTestBackend == 0) {
                    isTestBackend = 1;
                } else {
                    isTestBackend = 0;
                }
                datacenters.clear();
                fillDatacenters();
                saveSession();
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        UserConfig.clearConfig();
                        System.exit(0);
                    }
                });
            }
        });
    }

    private void loadSession() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                File configFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "config.dat");
                if (configFile.exists()) {
                    try {
                        SerializedData data = new SerializedData(configFile);
                        isTestBackend = data.readInt32();
                        int version = data.readInt32();
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
                        data.cleanup();
                    } catch (Exception e) {
                        UserConfig.clearConfig();
                    }
                } else {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
                    isTestBackend = preferences.getInt("datacenterSetId", 0);
                    currentDatacenterId = preferences.getInt("currentDatacenterId", 0);
                    timeDifference = preferences.getInt("timeDifference", 0);
                    lastDcUpdateTime = preferences.getInt("lastDcUpdateTime", 0);
                    pushSessionId = preferences.getLong("pushSessionId", 0);

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
                                data.cleanup();
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
                                data.cleanup();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                if (currentDatacenterId != 0 && UserConfig.isClientActivated()) {
                    Datacenter datacenter = datacenterWithId(currentDatacenterId);
                    if (datacenter == null || datacenter.authKey == null) {
                        currentDatacenterId = 0;
                        datacenters.clear();
                        UserConfig.clearConfig();
                    }
                }

                fillDatacenters();

                if (datacenters.size() != 0 && currentDatacenterId == 0 || pushSessionId == 0) {
                    if (pushSessionId == 0) {
                        pushSessionId = Utilities.random.nextLong();
                    }
                    if (currentDatacenterId == 0) {
                        currentDatacenterId = 2;
                    }
                    saveSession();
                }
                movingToDatacenterId = DEFAULT_DATACENTER_ID;
            }
        });
    }

    private void fillDatacenters() {
        if (datacenters.size() == 0) {
            if (isTestBackend == 0) {
                Datacenter datacenter = new Datacenter();
                datacenter.datacenterId = 1;
                datacenter.addAddressAndPort("149.154.175.50", 443);
                datacenters.put(datacenter.datacenterId, datacenter);

                datacenter = new Datacenter();
                datacenter.datacenterId = 2;
                datacenter.addAddressAndPort("149.154.167.51", 443);
                datacenters.put(datacenter.datacenterId, datacenter);

                datacenter = new Datacenter();
                datacenter.datacenterId = 3;
                datacenter.addAddressAndPort("149.154.175.100", 443);
                datacenters.put(datacenter.datacenterId, datacenter);

                datacenter = new Datacenter();
                datacenter.datacenterId = 4;
                datacenter.addAddressAndPort("149.154.167.91", 443);
                datacenters.put(datacenter.datacenterId, datacenter);

                datacenter = new Datacenter();
                datacenter.datacenterId = 5;
                datacenter.addAddressAndPort("149.154.171.5", 443);
                datacenters.put(datacenter.datacenterId, datacenter);
            } else {
                Datacenter datacenter = new Datacenter();
                datacenter.datacenterId = 1;
                datacenter.addAddressAndPort("149.154.175.10", 443);
                datacenters.put(datacenter.datacenterId, datacenter);

                datacenter = new Datacenter();
                datacenter.datacenterId = 2;
                datacenter.addAddressAndPort("149.154.167.40", 443);
                datacenters.put(datacenter.datacenterId, datacenter);

                datacenter = new Datacenter();
                datacenter.datacenterId = 3;
                datacenter.addAddressAndPort("149.154.175.117", 443);
                datacenters.put(datacenter.datacenterId, datacenter);
            }
        } else if (datacenters.size() == 1) {
            Datacenter datacenter = new Datacenter();
            datacenter.datacenterId = 2;
            datacenter.addAddressAndPort("149.154.167.51", 443);
            datacenters.put(datacenter.datacenterId, datacenter);

            datacenter = new Datacenter();
            datacenter.datacenterId = 3;
            datacenter.addAddressAndPort("149.154.175.100", 443);
            datacenters.put(datacenter.datacenterId, datacenter);

            datacenter = new Datacenter();
            datacenter.datacenterId = 4;
            datacenter.addAddressAndPort("149.154.167.91", 443);
            datacenters.put(datacenter.datacenterId, datacenter);

            datacenter = new Datacenter();
            datacenter.datacenterId = 5;
            datacenter.addAddressAndPort("149.154.171.5", 443);
            datacenters.put(datacenter.datacenterId, datacenter);
        }
    }

    private void saveSession() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("datacenterSetId", isTestBackend);
                    Datacenter currentDatacenter = datacenterWithId(currentDatacenterId);
                    if (currentDatacenter != null) {
                        editor.putInt("currentDatacenterId", currentDatacenterId);
                        editor.putInt("timeDifference", timeDifference);
                        editor.putInt("lastDcUpdateTime", lastDcUpdateTime);
                        editor.putLong("pushSessionId", pushSessionId);

                        ArrayList<Long> sessions = new ArrayList<>();
                        currentDatacenter.getSessions(sessions);

                        if (!sessions.isEmpty()) {
                            SerializedData data = new SerializedData(sessions.size() * 8 + 4);
                            data.writeInt32(sessions.size());
                            for (long session : sessions) {
                                data.writeInt64(session);
                            }
                            editor.putString("sessionsToDestroy", Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                            data.cleanup();
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
                            data.cleanup();
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
                for (int a = 0; a < requestQueue.size(); a++) {
                    RPCRequest request = requestQueue.get(a);
                    if ((request.flags & RPCRequest.RPCRequestClassWithoutLogin) != 0) {
                        continue;
                    }
                    requestQueue.remove(a);
                    if (request.completionBlock != null) {
                        TLRPC.TL_error implicitError = new TLRPC.TL_error();
                        implicitError.code = -1000;
                        implicitError.text = "";
                        request.completionBlock.run(null, implicitError);
                    }
                    a--;
                }
                for (int a = 0; a < runningRequests.size(); a++) {
                    RPCRequest request = runningRequests.get(a);
                    if ((request.flags & RPCRequest.RPCRequestClassWithoutLogin) != 0) {
                        continue;
                    }
                    runningRequests.remove(a);
                    if (request.completionBlock != null) {
                        TLRPC.TL_error implicitError = new TLRPC.TL_error();
                        implicitError.code = -1000;
                        implicitError.text = "";
                        request.completionBlock.run(null, implicitError);
                    }
                    a--;
                }
                pingIdToDate.clear();
                quickAckIdToRequestIds.clear();

                for (Datacenter datacenter : datacenters.values()) {
                    datacenter.recreateSessions();
                    datacenter.authorized = false;
                }

                sessionsToDestroy.clear();
                saveSession();
            }
        });
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

    //================================================================================
    // Requests manage
    //================================================================================
    int lastClassGuid = 1;
    public int generateClassGuid() {
        int guid = lastClassGuid++;
        requestsByGuids.put(guid, new ArrayList<Long>());
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
        AndroidUtilities.runOnUIThread(new Runnable() {
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
        AndroidUtilities.runOnUIThread(new Runnable() {
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
                    ArrayList<String> addresses = new ArrayList<>();
                    HashMap<String, Integer> ports = new HashMap<>();
                    addresses.add(ip_address);
                    ports.put(ip_address, port);
                    exist.replaceAddressesAndPorts(addresses, ports);
                    exist.suspendConnections();
                    updateDcSettings(dc);
                }
            }
        });
    }

    public void initPushConnection() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                Datacenter datacenter = datacenterWithId(currentDatacenterId);
                if (datacenter != null) {
                    if (datacenter.pushConnection == null) {
                        datacenter.pushConnection = new TcpConnection(datacenter.datacenterId);
                        datacenter.pushConnection.setSessionId(pushSessionId);
                        datacenter.pushConnection.delegate = ConnectionsManager.this;
                        datacenter.pushConnection.transportRequestClass = RPCRequest.RPCRequestClassPush;
                        datacenter.pushConnection.connect();
                        generatePing(datacenter, true);
                    } else {
                        if (UserConfig.isClientActivated() && !UserConfig.registeredForInternalPush) {
                            registerForPush();
                        }
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
                        datacenter.overridePort = 8888;
                        datacenter.suspendConnections();
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
                    TLRPC.TL_config config = (TLRPC.TL_config)response;
                    int updateIn = config.expires - getCurrentTime();
                    if (updateIn <= 0) {
                        updateIn = 120;
                    }
                    lastDcUpdateTime = (int)(System.currentTimeMillis() / 1000) - DC_UPDATE_TIME + updateIn;
                    ArrayList<Datacenter> datacentersArr = new ArrayList<>();
                    HashMap<Integer, Datacenter> datacenterMap = new HashMap<>();
                    for (TLRPC.TL_dcOption datacenterDesc : config.dc_options) {
                        Datacenter existing = datacenterMap.get(datacenterDesc.id);
                        if (existing == null) {
                            existing = new Datacenter();
                            existing.datacenterId = datacenterDesc.id;
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
                    MessagesController.getInstance().updateConfig(config);
                }
                updatingDcSettings = false;
            }
        }, null, true, RPCRequest.RPCRequestClassEnableUnauthorized | RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassWithoutLogin | RPCRequest.RPCRequestClassTryDifferentDc, dcNum == 0 ? currentDatacenterId : dcNum);
    }

    private TLObject wrapInLayer(TLObject object, int datacenterId, RPCRequest request) {
        if (object.layer() > 0) {
            Datacenter datacenter = datacenterWithId(datacenterId);
            if (datacenter == null || datacenter.lastInitVersion != currentAppVersion) {
                registerForPush();
                request.initRequest = true;
                TLRPC.initConnection invoke = new TLRPC.initConnection();
                invoke.query = object;
                invoke.api_id = BuildVars.APP_ID;
                try {
                    invoke.lang_code = LocaleController.getLocaleString(Locale.getDefault());
                    invoke.device_model = Build.MANUFACTURER + Build.MODEL;
                    if (invoke.device_model == null) {
                        invoke.device_model = "Android unknown";
                    }
                    PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                    invoke.app_version = pInfo.versionName + " (" + pInfo.versionCode + ")";
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
                TLRPC.invokeWithLayer invoke2 = new TLRPC.invokeWithLayer();
                invoke2.query = invoke;
                FileLog.d("wrap in layer", "" + object);
                object = invoke2;
            }
        }
        return object;
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock) {
        return performRpc(rpc, completionBlock, null, true, RPCRequest.RPCRequestClassGeneric, DEFAULT_DATACENTER_ID);
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, boolean requiresCompletion, int requestClass) {
        return performRpc(rpc, completionBlock, null, requiresCompletion, requestClass, DEFAULT_DATACENTER_ID, true);
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCQuickAckDelegate quickAckBlock, final boolean requiresCompletion, final int requestClass, final int datacenterId) {
        return performRpc(rpc, completionBlock, quickAckBlock, requiresCompletion, requestClass, datacenterId, true);
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCQuickAckDelegate quickAckBlock, final boolean requiresCompletion, final int requestClass, final int datacenterId, final boolean runQueue) {
        if (rpc == null || !UserConfig.isClientActivated() && (requestClass & RPCRequest.RPCRequestClassWithoutLogin) == 0) {
            FileLog.e("tmessages", "can't do request without login " + rpc);
            return 0;
        }

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
                request.quickAckBlock = quickAckBlock;
                request.requiresCompletion = requiresCompletion;

                requestQueue.add(request);

                if (runQueue) {
                    processRequestQueue(0, 0);
                }
            }
        });

        return requestToken;
    }

    public void cancelRpc(final long token, final boolean notifyServer) {
        cancelRpc(token, notifyServer, false);
    }

    public void cancelRpc(final long token, final boolean notifyServer, final boolean ifNotSent) {
        if (token == 0) {
            return;
        }
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

                if (!ifNotSent) {
                    for (int i = 0; i < runningRequests.size(); i++) {
                        RPCRequest request = runningRequests.get(i);
                        if (request.token == token) {
                            found = true;

                            FileLog.d("tmessages", "===== Cancelled running rpc request " + request.rawRequest);

                            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                                if (notifyServer) {
                                    TLRPC.TL_rpc_drop_answer dropAnswer = new TLRPC.TL_rpc_drop_answer();
                                    dropAnswer.req_msg_id = request.runningMessageId;
                                    performRpc(dropAnswer, null, false, request.flags);
                                }
                            }

                            request.cancelled = true;
                            request.rawRequest.freeResources();
                            request.rpcRequest.freeResources();
                            runningRequests.remove(i);
                            break;
                        }
                    }
                    if (!found) {
                        FileLog.d("tmessages", "***** Warning: cancelling unknown request");
                    }
                }
            }
        });
    }

    public static boolean isNetworkOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                return true;
            }

            netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if(netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch(Exception e) {
            FileLog.e("tmessages", e);
            return true;
        }
        return false;
    }

    public static boolean isRoaming() {
        try {
            ConnectivityManager cm = (ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo != null) {
                return netInfo.isRoaming();
            }
        } catch(Exception e) {
            FileLog.e("tmessages", e);
        }
        return false;
    }

    public static boolean isConnectedToWiFi() {
        try {
            ConnectivityManager cm = (ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (netInfo != null && netInfo.getState() == NetworkInfo.State.CONNECTED) {
                return true;
            }
        } catch(Exception e) {
            FileLog.e("tmessages", e);
        }
        return false;
    }

    public int getCurrentTime() {
        return (int)(System.currentTimeMillis() / 1000) + timeDifference;
    }

    public int getTimeDifference() {
        return timeDifference;
    }

    private void processRequestQueue(int requestClass, int _datacenterId) {
        boolean haveNetwork = true;//isNetworkOnline();

        genericMessagesToDatacenters.clear();
        unknownDatacenterIds.clear();
        neededDatacenterIds.clear();
        unauthorizedDatacenterIds.clear();

        TcpConnection genericConnection = null;
        Datacenter defaultDatacenter = datacenterWithId(currentDatacenterId);
        if (defaultDatacenter != null) {
            genericConnection = defaultDatacenter.getGenericConnection(this);
        }

        int currentTime = (int)(System.currentTimeMillis() / 1000);
        for (int i = 0; i < runningRequests.size(); i++) {
            RPCRequest request = runningRequests.get(i);

            int datacenterId = request.runningDatacenterId;
            if (datacenterId == DEFAULT_DATACENTER_ID) {
                if (movingToDatacenterId != DEFAULT_DATACENTER_ID) {
                    continue;
                }
                datacenterId = currentDatacenterId;
            }

            if (datacenters.size() > 1 && (request.flags & RPCRequest.RPCRequestClassTryDifferentDc) != 0) {
                int requestStartTime = request.runningStartTime;
                int timeout = 30;
                if (updatingDcSettings && request.rawRequest instanceof TLRPC.TL_help_getConfig) {
                    requestStartTime = updatingDcStartTime;
                    timeout = 60;
                }
                if (requestStartTime != 0 && requestStartTime < currentTime - timeout) {
                    FileLog.e("tmessages", "move " + request.rawRequest + " to requestQueue");
                    requestQueue.add(request);
                    runningRequests.remove(i);
                    i--;
                    continue;
                }
            }

            Datacenter requestDatacenter = datacenterWithId(datacenterId);
            if (!request.initRequest && requestDatacenter.lastInitVersion != currentAppVersion) {
                request.rpcRequest = wrapInLayer(request.rawRequest, requestDatacenter.datacenterId, request);
                ByteBufferDesc os = new ByteBufferDesc(true);
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

            float maxTimeout = 8.0f;

            TcpConnection connection = null;
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                connection = requestDatacenter.getGenericConnection(this);
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                connection = requestDatacenter.getDownloadConnection(this);
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0 ) {
                connection = requestDatacenter.getUploadConnection(this);
            }

            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                if (connection.channelToken == 0) {
                    continue;
                }
            } else {
                if (!haveNetwork || connection.channelToken == 0) {
                    continue;
                }
                maxTimeout = 30.0f;
            }

            boolean forceThisRequest = (request.flags & requestClass) != 0 && requestDatacenter.datacenterId == _datacenterId;

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
                    if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0 && request.transportChannelToken == connection.channelToken) {
                        FileLog.d("tmessages", "Request token is valid, not retrying " + request.rawRequest);
                        continue;
                    } else {
                        if (connection.channelToken != 0 && request.transportChannelToken == connection.channelToken) {
                            FileLog.d("tmessages", "Request download token is valid, not retrying " + request.rawRequest);
                            continue;
                        }
                    }
                }

                if (request.transportChannelToken != 0 && request.transportChannelToken != connection.channelToken) {
                    request.lastResendTime = 0;
                }

                request.retryCount++;

                if (!request.salt && (request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    int retryMax = 10;
                    if ((request.flags & RPCRequest.RPCRequestClassForceDownload) == 0) {
                        if (request.wait) {
                            retryMax = 1;
                        } else {
                            retryMax = 6;
                        }
                    }
                    if (request.retryCount >= retryMax) {
                        FileLog.e("tmessages", "timed out " + request.rawRequest);
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.code = -123;
                        error.text = "RETRY_LIMIT";
                        if (request.completionBlock != null) {
                            request.completionBlock.run(null, error);
                        }
                        runningRequests.remove(i);
                        i--;
                        continue;
                    }
                }

                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = new TLRPC.TL_protoMessage();

                if (request.runningMessageSeqNo == 0) {
                    request.runningMessageSeqNo = connection.generateMessageSeqNo(true);
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
                    request.transportChannelToken = connection.channelToken;
                    addMessageToDatacenter(requestDatacenter.datacenterId, networkMessage);
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    request.transportChannelToken = connection.channelToken;
                    ArrayList<NetworkMessage> arr = new ArrayList<>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, connection, false);
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    request.transportChannelToken = connection.channelToken;
                    ArrayList<NetworkMessage> arr = new ArrayList<>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, connection, false);
                }
            }
        }

        if (genericConnection != null && genericConnection.channelToken != 0) {
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
                    networkMessage.protoMessage = wrapMessage(destroySession, currentDatacenter.connection, false);
                    if (networkMessage.protoMessage != null) {
                        addMessageToDatacenter(currentDatacenter.datacenterId, networkMessage);
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

            int datacenterId = request.runningDatacenterId;
            if (datacenterId == DEFAULT_DATACENTER_ID) {
                if (movingToDatacenterId != DEFAULT_DATACENTER_ID && (request.flags & RPCRequest.RPCRequestClassEnableUnauthorized) == 0) {
                    continue;
                }
                datacenterId = currentDatacenterId;
            }

            if (datacenters.size() > 1 && (request.flags & RPCRequest.RPCRequestClassTryDifferentDc) != 0) {
                int requestStartTime = request.runningStartTime;
                int timeout = 30;
                if (updatingDcSettings && request.rawRequest instanceof TLRPC.TL_help_getConfig) {
                    requestStartTime = updatingDcStartTime;
                    updatingDcStartTime = currentTime;
                    timeout = 60;
                } else {
                    request.runningStartTime = 0;
                }
                if (requestStartTime != 0 && requestStartTime < currentTime - timeout) {
                    ArrayList<Datacenter> allDc = new ArrayList<>(datacenters.values());
                    for (int a = 0; a < allDc.size(); a++) {
                        Datacenter dc = allDc.get(a);
                        if (dc.datacenterId == datacenterId) {
                            allDc.remove(a);
                            break;
                        }
                    }
                    Datacenter newDc = allDc.get(Math.abs(Utilities.random.nextInt() % allDc.size()));
                    datacenterId = newDc.datacenterId;
                    if (!(request.rawRequest instanceof TLRPC.TL_help_getConfig)) {
                        currentDatacenterId = datacenterId;
                    } else {
                        request.runningDatacenterId = datacenterId;
                    }
                }
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

            TcpConnection connection = null;
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                connection = requestDatacenter.getGenericConnection(this);
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                connection = requestDatacenter.getDownloadConnection(this);
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                connection = requestDatacenter.getUploadConnection(this);
            }

            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0 && connection.channelToken == 0) {
                continue;
            }

            if (request.requiresCompletion) {
                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    if (genericRunningRequestCount >= 60) {
                        continue;
                    }
                    genericRunningRequestCount++;
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    if (!haveNetwork || uploadRunningRequestCount >= 5) {
                        continue;
                    }
                    uploadRunningRequestCount++;
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    if (!haveNetwork || downloadRunningRequestCount >= 5) {
                        continue;
                    }
                    downloadRunningRequestCount++;
                }
            }

            long messageId = generateMessageId();

            boolean canCompress = (request.flags & RPCRequest.RPCRequestClassCanCompress) != 0;

            SerializedData os = new SerializedData(!canCompress);
            request.rpcRequest.serializeToStream(os);
            int requestLength = os.length();

            if (requestLength != 0) {
                if (canCompress) {
                    try {
                        byte[] data = Utilities.compress(os.toByteArray());
                        os.cleanup();
                        if (data.length < requestLength) {
                            TLRPC.TL_gzip_packed packed = new TLRPC.TL_gzip_packed();
                            packed.packed_data = data;
                            request.rpcRequest = packed;
                            os = new SerializedData(true);
                            packed.serializeToStream(os);
                            requestLength = os.length();
                            os.cleanup();
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = new TLRPC.TL_protoMessage();
                networkMessage.protoMessage.msg_id = messageId;
                networkMessage.protoMessage.seqno = connection.generateMessageSeqNo(true);
                networkMessage.protoMessage.bytes = requestLength;
                networkMessage.protoMessage.body = request.rpcRequest;
                networkMessage.rawRequest = request.rawRequest;
                networkMessage.requestId = request.token;

                request.runningMessageId = messageId;
                request.runningMessageSeqNo = networkMessage.protoMessage.seqno;
                request.serializedLength = requestLength;
                request.runningStartTime = (int)(System.currentTimeMillis() / 1000);
                request.transportChannelToken = connection.channelToken;
                if (request.requiresCompletion) {
                    runningRequests.add(request);
                }

                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    addMessageToDatacenter(requestDatacenter.datacenterId, networkMessage);
                } else {
                    ArrayList<NetworkMessage> arr = new ArrayList<>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, connection, false);
                }
            } else {
                FileLog.e("tmessages", "***** Couldn't serialize " + request.rawRequest);
            }

            requestQueue.remove(i);
            i--;
        }

        for (Datacenter datacenter : datacenters.values()) {
            if (genericMessagesToDatacenters.get(datacenter.datacenterId) == null && datacenter.connection != null && datacenter.connection.channelToken != 0 && datacenter.connection.hasMessagesToConfirm()) {
                genericMessagesToDatacenters.put(datacenter.datacenterId, new ArrayList<NetworkMessage>());
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
                            rawRequest instanceof TLRPC.TL_messages_forwardMessage ||
                            rawRequest instanceof TLRPC.TL_messages_sendEncrypted ||
                            rawRequest instanceof TLRPC.TL_messages_sendEncryptedFile ||
                            rawRequest instanceof TLRPC.TL_messages_sendEncryptedService)) {

                        if (rawRequest instanceof TLRPC.TL_messages_sendMessage) {
                            hasSendMessage = true;
                        }

                        if (!scannedPreviousRequests) {
                            scannedPreviousRequests = true;

                            ArrayList<Long> currentRequests = new ArrayList<>();
                            for (NetworkMessage currentNetworkMessage : arr) {
                                TLRPC.TL_protoMessage currentMessage = currentNetworkMessage.protoMessage;

                                Object currentRawRequest = currentNetworkMessage.rawRequest;

                                if (currentRawRequest instanceof TLRPC.TL_messages_sendMessage ||
                                        currentRawRequest instanceof TLRPC.TL_messages_sendMedia ||
                                        currentRawRequest instanceof TLRPC.TL_messages_forwardMessages ||
                                        currentRawRequest instanceof TLRPC.TL_messages_forwardMessage ||
                                        currentRawRequest instanceof TLRPC.TL_messages_sendEncrypted ||
                                        currentRawRequest instanceof TLRPC.TL_messages_sendEncryptedFile ||
                                        currentRawRequest instanceof TLRPC.TL_messages_sendEncryptedService) {
                                    currentRequests.add(currentMessage.msg_id);
                                }
                            }

                            long maxRequestId = 0;
                            for (RPCRequest request : runningRequests) {
                                if (request.rawRequest instanceof TLRPC.TL_messages_sendMessage ||
                                        request.rawRequest instanceof TLRPC.TL_messages_sendMedia ||
                                        request.rawRequest instanceof TLRPC.TL_messages_forwardMessages ||
                                        request.rawRequest instanceof TLRPC.TL_messages_forwardMessage ||
                                        request.rawRequest instanceof TLRPC.TL_messages_sendEncrypted ||
                                        request.rawRequest instanceof TLRPC.TL_messages_sendEncryptedFile ||
                                        request.rawRequest instanceof TLRPC.TL_messages_sendEncryptedService) {
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

                proceedToSendingMessages(arr, datacenter.getGenericConnection(this), hasSendMessage);
            }
        }

        if ((requestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            ArrayList<NetworkMessage> messagesIt = genericMessagesToDatacenters.get(_datacenterId);
            if (messagesIt == null || messagesIt.size() == 0) {
                generatePing();
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
            if (num != currentDatacenterId && num != movingToDatacenterId && UserConfig.isClientActivated()) {
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

    void addMessageToDatacenter(int datacenterId, NetworkMessage message) {
        ArrayList<NetworkMessage> arr = genericMessagesToDatacenters.get(datacenterId);
        if (arr == null) {
            arr = new ArrayList<>();
            genericMessagesToDatacenters.put(datacenterId, arr);
        }
        arr.add(message);
    }

    TLRPC.TL_protoMessage wrapMessage(TLObject message, TcpConnection connection, boolean meaningful) {
        ByteBufferDesc os = new ByteBufferDesc(true);
        message.serializeToStream(os);

        if (os.length() != 0) {
            TLRPC.TL_protoMessage protoMessage = new TLRPC.TL_protoMessage();
            protoMessage.msg_id = generateMessageId();
            protoMessage.bytes = os.length();
            protoMessage.body = message;
            protoMessage.seqno = connection.generateMessageSeqNo(meaningful);
            return protoMessage;
        } else {
            FileLog.e("tmessages", "***** Couldn't serialize " + message);
            return null;
        }
    }

    void proceedToSendingMessages(ArrayList<NetworkMessage> messageList, TcpConnection connection, boolean reportAck) {
        if (connection.getSissionId() == 0) {
            return;
        }

        ArrayList<NetworkMessage> messages = new ArrayList<>();
        if(messageList != null) {
            messages.addAll(messageList);
        }

        NetworkMessage message = connection.generateConfirmationRequest();
        if (message != null) {
            messages.add(message);
        }

        sendMessagesToTransport(messages, connection, reportAck);
    }

    void sendMessagesToTransport(ArrayList<NetworkMessage> messagesToSend, TcpConnection connection, boolean reportAck) {
        if (messagesToSend.size() == 0) {
            return;
        }

        if (connection == null) {
            return;
        }

        ArrayList<NetworkMessage> currentMessages = new ArrayList<>();

        int currentSize = 0;
        for (int a = 0; a < messagesToSend.size(); a++) {
            NetworkMessage networkMessage = messagesToSend.get(a);
            currentMessages.add(networkMessage);

            TLRPC.TL_protoMessage protoMessage = networkMessage.protoMessage;

            currentSize += protoMessage.bytes;

            if (currentSize >= 3 * 1024 || a == messagesToSend.size() - 1) {
                ArrayList<Integer> quickAckId = new ArrayList<>();
                ByteBufferDesc transportData = createConnectionData(currentMessages, quickAckId, connection);

                if (transportData != null) {
                    if (reportAck && quickAckId.size() != 0) {
                        ArrayList<Long> requestIds = new ArrayList<>();

                        for (NetworkMessage message : messagesToSend) {
                            if (message.requestId != 0) {
                                requestIds.add(message.requestId);
                            }
                        }

                        if (requestIds.size() != 0) {
                            int ack = quickAckId.get(0);
                            ArrayList<Long> arr = quickAckIdToRequestIds.get(ack);
                            if (arr == null) {
                                arr = new ArrayList<>();
                                quickAckIdToRequestIds.put(ack, arr);
                            }
                            arr.addAll(requestIds);
                        }
                    }

                    connection.sendData(transportData, true, reportAck);
                } else {
                    FileLog.e("tmessages", "***** Transport data is nil");
                }

                currentSize = 0;
                currentMessages.clear();
            }
        }
    }

    ByteBufferDesc createConnectionData(ArrayList<NetworkMessage> messages, ArrayList<Integer> quickAckId, TcpConnection connection) {
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
                if (message.body instanceof TLRPC.invokeWithLayer) {
                    FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer)message.body).query);
                } else if (message.body instanceof TLRPC.initConnection) {
                    TLRPC.initConnection r = (TLRPC.initConnection)message.body;
                    if (r.query instanceof TLRPC.invokeWithLayer) {
                        FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer)r.query).query);
                    } else {
                        FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + r.query);
                    }
                } else {
                    FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + message.body);
                }
            }

            long msg_time = getTimeFromMsgId(message.msg_id);
            long currentTime = System.currentTimeMillis() + ((long)timeDifference) * 1000;

            if (msg_time < currentTime - 30000 || msg_time > currentTime + 25000) {
                FileLog.d("tmessages", "wrap in messages continaer");
                TLRPC.TL_msg_container messageContainer = new TLRPC.TL_msg_container();
                messageContainer.messages = new ArrayList<>();
                messageContainer.messages.add(message);

                messageId = generateMessageId();
                messageBody = messageContainer;
                messageSeqNo = connection.generateMessageSeqNo(false);
            } else {
                messageId = message.msg_id;
                messageBody = message.body;
                messageSeqNo = message.seqno;
            }
        } else {
            TLRPC.TL_msg_container messageContainer = new TLRPC.TL_msg_container();

            ArrayList<TLRPC.TL_protoMessage> containerMessages = new ArrayList<>(messages.size());

            for (NetworkMessage networkMessage : messages) {
                TLRPC.TL_protoMessage message = networkMessage.protoMessage;
                containerMessages.add(message);
                if (BuildVars.DEBUG_VERSION) {
                    if (message.body instanceof TLRPC.invokeWithLayer) {
                        FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer)message.body).query);
                    } else if (message.body instanceof TLRPC.initConnection) {
                        TLRPC.initConnection r = (TLRPC.initConnection)message.body;
                        if (r.query instanceof TLRPC.invokeWithLayer) {
                            FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + ((TLRPC.invokeWithLayer)r.query).query);
                        } else {
                            FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + r.query);
                        }
                    } else {
                        FileLog.d("tmessages", connection.getSissionId() + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + message.body);
                    }
                }
            }

            messageContainer.messages = containerMessages;

            messageId = generateMessageId();
            messageBody = messageContainer;
            messageSeqNo = connection.generateMessageSeqNo(false);
        }

        ByteBufferDesc sizeBuffer = new ByteBufferDesc(true);
        messageBody.serializeToStream(sizeBuffer);

        ByteBufferDesc innerOs = BuffersStorage.getInstance().getFreeBuffer(8 + 8 + 8 + 4 + 4 + sizeBuffer.length());

        long serverSalt = datacenter.selectServerSalt(getCurrentTime());
        if (serverSalt == 0) {
            innerOs.writeInt64(0);
        } else {
            innerOs.writeInt64(serverSalt);
        }
        innerOs.writeInt64(connection.getSissionId());
        innerOs.writeInt64(messageId);
        innerOs.writeInt32(messageSeqNo);
        innerOs.writeInt32(sizeBuffer.length());
        messageBody.serializeToStream(innerOs);

        byte[] messageKeyFull = Utilities.computeSHA1(innerOs.buffer, 0, innerOs.limit());
        byte[] messageKey = new byte[16];
        System.arraycopy(messageKeyFull, messageKeyFull.length - 16, messageKey, 0, 16);

        if (quickAckId != null) {
            SerializedData data = new SerializedData(messageKeyFull);
            quickAckId.add(data.readInt32() & 0x7fffffff);
            data.cleanup();
        }

        MessageKeyData keyData = Utilities.generateMessageKeyData(datacenter.authKey, messageKey, false);

        int zeroCount = 0;
        if (innerOs.limit() % 16 != 0) {
            zeroCount = 16 - innerOs.limit() % 16;
        }

        ByteBufferDesc dataForEncryption = BuffersStorage.getInstance().getFreeBuffer(innerOs.limit() + zeroCount);
        dataForEncryption.writeRaw(innerOs);
        BuffersStorage.getInstance().reuseFreeBuffer(innerOs);

        if (zeroCount != 0) {
            byte[] b = new byte[zeroCount];
            Utilities.random.nextBytes(b);
            dataForEncryption.writeRaw(b);
        }

        Utilities.aesIgeEncryption(dataForEncryption.buffer, keyData.aesKey, keyData.aesIv, true, false, 0, dataForEncryption.limit());

        ByteBufferDesc data = BuffersStorage.getInstance().getFreeBuffer(8 + messageKey.length + dataForEncryption.limit());
        data.writeInt64(datacenter.authKeyId);
        data.writeRaw(messageKey);
        data.writeRaw(dataForEncryption);
        BuffersStorage.getInstance().reuseFreeBuffer(dataForEncryption);

        return data;
    }

    void refillSaltSet(final Datacenter datacenter) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (RPCRequest request : requestQueue) {
                    if (request.rawRequest instanceof TLRPC.TL_get_future_salts) {
                        Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                        if (requestDatacenter.datacenterId == datacenter.datacenterId) {
                            return;
                        }
                    }
                }

                for (RPCRequest request : runningRequests) {
                    if (request.rawRequest instanceof TLRPC.TL_get_future_salts) {
                        Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                        if (requestDatacenter.datacenterId == datacenter.datacenterId) {
                            return;
                        }
                    }
                }

                TLRPC.TL_get_future_salts getFutureSalts = new TLRPC.TL_get_future_salts();
                getFutureSalts.num = 32;

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
                }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassWithoutLogin, datacenter.datacenterId);
            }
        });
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

    private void rpcCompleted(final long requestMsgId) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < runningRequests.size(); i++) {
                    RPCRequest request = runningRequests.get(i);
                    removeRequestInClass(request.token);
                    if (request.respondsToMessageId(requestMsgId)) {
                        request.rawRequest.freeResources();
                        request.rpcRequest.freeResources();
                        runningRequests.remove(i);
                        i--;
                    }
                }
            }
        });
    }

    private void registerForPush() {
        if (registeringForPush || !UserConfig.isClientActivated()) {
            return;
        }
        UserConfig.registeredForInternalPush = false;
        UserConfig.saveConfig(false);
        registeringForPush = true;
        TLRPC.TL_account_registerDevice req = new TLRPC.TL_account_registerDevice();
        req.token_type = 7;
        req.token = "" + pushSessionId;
        req.app_sandbox = false;
        try {
            req.lang_code = LocaleController.getLocaleString(Locale.getDefault());
            req.device_model = Build.MANUFACTURER + Build.MODEL;
            if (req.device_model == null) {
                req.device_model = "Android unknown";
            }
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            req.app_version = pInfo.versionName + " (" + pInfo.versionCode + ")";
            if (req.app_version == null) {
                req.app_version = "App version unknown";
            }

        } catch (Exception e) {
            FileLog.e("tmessages", e);
            req.lang_code = "en";
            req.device_model = "Android unknown";
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            req.app_version = "App version unknown";
        }

        if (req.lang_code == null || req.lang_code.length() == 0) {
            req.lang_code = "en";
        }
        if (req.device_model == null || req.device_model.length() == 0) {
            req.device_model = "Android unknown";
        }
        if (req.app_version == null || req.app_version.length() == 0) {
            req.app_version = "App version unknown";
        }
        if (req.system_version == null || req.system_version.length() == 0) {
            req.system_version = "SDK Unknown";
        }

        if (req.app_version != null) {
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        UserConfig.registeredForInternalPush = true;
                        UserConfig.saveConfig(false);
                        saveSession();
                        FileLog.e("tmessages", "registered for internal push");
                    } else {
                        UserConfig.registeredForInternalPush = false;
                    }
                    registeringForPush = false;
                }
            }, true, RPCRequest.RPCRequestClassGeneric);
        }
    }

    void processMessage(TLObject message, long messageId, int messageSeqNo, long messageSalt, TcpConnection connection, long innerMsgId, long containerMessageId) {
        if (message == null) {
            FileLog.e("tmessages", "message is null");
            return;
        }
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());

        if (message instanceof TLRPC.TL_new_session_created) {
            TLRPC.TL_new_session_created newSession = (TLRPC.TL_new_session_created)message;

            if (!connection.isSessionProcessed(newSession.unique_id)) {
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

                if (datacenter.datacenterId == currentDatacenterId && UserConfig.isClientActivated()) {
                    if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
                        registerForPush();
                    } else if ((connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
                        MessagesController.getInstance().getDifference();
                    }
                }
                connection.addProcessedSession(newSession.unique_id);
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
                    connection.addMessageToConfirm(innerMessageId);
                }
                if (connection.isMessageIdProcessed(innerMessageId)) {
                    continue;
                }
                processMessage(innerMessage.body, 0, innerMessage.seqno, messageSalt, connection, innerMessageId, messageId);
                connection.addProcessedMessageId(innerMessageId);
            }
        } else if (message instanceof TLRPC.TL_pong) {
            if (UserConfig.isClientActivated() && !UserConfig.registeredForInternalPush && (connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
                registerForPush();
            }
            if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) == 0) {
                TLRPC.TL_pong pong = (TLRPC.TL_pong) message;
                long pingId = pong.ping_id;

                ArrayList<Long> itemsToDelete = new ArrayList<>();
                for (Long pid : pingIdToDate.keySet()) {
                    if (pid == pingId) {
                        int time = pingIdToDate.get(pid);
                        int pingTime = (int) (System.currentTimeMillis() / 1000) - time;

                        if (Math.abs(pingTime) < 10) {
                            currentPingTime = (pingTime + currentPingTime) / 2;

                            if (messageId != 0) {
                                long timeMessage = getTimeFromMsgId(messageId);
                                long currentTime = System.currentTimeMillis();
                                timeDifference = (int) ((timeMessage - currentTime) / 1000 - currentPingTime / 2.0);
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
            } else {
                FileLog.e("tmessages", "received push ping");
                sendingPushPing = false;
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
                    request.completed = true;
                    rpcCompleted(requestMid);

                    break;
                }
            }
        } else if (message instanceof TLRPC.DestroySessionRes) {
            TLRPC.DestroySessionRes res = (TLRPC.DestroySessionRes)message;
            ArrayList<Long> lst = new ArrayList<>();
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
                    ArrayList<String> migrateErrors = new ArrayList<>();
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
                                    if ((request.flags & RPCRequest.RPCRequestClassFailOnServerErrors) == 0) {
                                        discardResponse = true;
                                        int delay = Math.min(1, request.serverFailureCount * 2);
                                        request.runningMinStartTime = request.runningStartTime + delay;
                                        request.confirmed = false;
                                    }
                                    request.serverFailureCount++;
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
                                        request.wait = true;
                                        request.runningMinStartTime = (int)(System.currentTimeMillis() / 1000 + waitTime);
                                        request.confirmed = false;
                                    }
                                }

                                implicitError = new TLRPC.TL_error();
                                implicitError.code = ((TLRPC.RpcError)resultContainer.result).error_code;
                                implicitError.text = ((TLRPC.RpcError)resultContainer.result).error_message;
                            } else if (!(resultContainer.result instanceof TLRPC.TL_error)) {
                                if (request.rawRequest == null || resultContainer.result == null || !request.rawRequest.responseClass().isAssignableFrom(resultContainer.result.getClass())) {
                                    if (request.rawRequest == null) {
                                        FileLog.e("tmessages", "rawRequest is null");
                                    } else {
                                        FileLog.e("tmessages", "***** RPC error: invalid response class " + resultContainer.result + " (" + request.rawRequest.responseClass() + " expected)");
                                    }
                                    implicitError = new TLRPC.TL_error();
                                    implicitError.code = -1000;
                                    implicitError.text = "";
                                }
                            }

                            if (!discardResponse) {
                                if (implicitError != null || resultContainer.result instanceof TLRPC.TL_error) {
                                    isError = true;
                                    request.completionBlock.run(null, implicitError != null ? implicitError : (TLRPC.TL_error) resultContainer.result);
                                } else {
                                    if (resultContainer.result instanceof TLRPC.updates_Difference) {
                                        pushMessagesReceived = true;
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (wakeLock.isHeld()) {
                                                    FileLog.e("tmessages", "release wakelock");
                                                    wakeLock.release();
                                                }
                                            }
                                        });
                                    }
                                    request.completionBlock.run(resultContainer.result, null);
                                }
                            }

                            if (implicitError != null && implicitError.code == 401) {
                                isError = true;
                                if (implicitError.text != null && implicitError.text.contains("SESSION_PASSWORD_NEEDED")) {
                                    /*UserConfig.setWaitingForPasswordEnter(true); TODO
                                    UserConfig.saveConfig(false);
                                    if (UserConfig.isClientActivated()) {
                                        discardResponse = true;
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.needPasswordEnter);
                                            }
                                        });
                                    }*/
                                } else if (datacenter.datacenterId == currentDatacenterId || datacenter.datacenterId == movingToDatacenterId) {
                                    if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0 && UserConfig.isClientActivated()) {
                                        UserConfig.clearConfig();
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.appDidLogout);
                                            }
                                        });
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
                            request.completed = true;
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

                datacenter.recreateSessions();
                saveSession();

                lastOutgoingMessageId = 0;

                clearRequestsForRequestClass(RPCRequest.RPCRequestClassGeneric, datacenter);
                clearRequestsForRequestClass(RPCRequest.RPCRequestClassDownloadMedia, datacenter);
                clearRequestsForRequestClass(RPCRequest.RPCRequestClassUploadMedia, datacenter);
            }
        } else if (message instanceof TLRPC.TL_bad_server_salt) {
            if (messageId != 0) {
                long time = getTimeFromMsgId(messageId);
                long currentTime = System.currentTimeMillis();
                timeDifference = (int)((time - currentTime) / 1000 - currentPingTime / 2.0);

                lastOutgoingMessageId = Math.max(messageId, lastOutgoingMessageId);
            }
            long resultMid = ((TLRPC.TL_bad_server_salt) message).bad_msg_id;
            if (resultMid != 0) {
                for (RPCRequest request : runningRequests) {
                    if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) == 0) {
                        continue;
                    }
                    if (request.respondsToMessageId(resultMid)) {
                        request.retryCount = 0;
                        request.salt = true;
                        break;
                    }
                }
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
            boolean confirm = true;

            if (detailedInfo instanceof TLRPC.TL_msg_detailed_info) {
                for (RPCRequest request : runningRequests) {
                    if (request.respondsToMessageId(detailedInfo.msg_id)) {
                        if (request.completed) {
                            break;
                        }
                        if (request.lastResendTime == 0 || request.lastResendTime + 60 < (int)(System.currentTimeMillis() / 1000)) {
                            request.lastResendTime = (int)(System.currentTimeMillis() / 1000);
                            requestResend = true;
                        } else {
                            confirm = false;
                        }
                        break;
                    }
                }
            } else {
                if (!connection.isMessageIdProcessed(messageId)) {
                    requestResend = true;
                }
            }

            if (requestResend) {
                TLRPC.TL_msg_resend_req resendReq = new TLRPC.TL_msg_resend_req();
                resendReq.msg_ids.add(detailedInfo.answer_msg_id);

                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = wrapMessage(resendReq, connection, false);

                ArrayList<NetworkMessage> arr = new ArrayList<>();
                arr.add(networkMessage);
                sendMessagesToTransport(arr, connection, false);
            } else if (confirm) {
                connection.addMessageToConfirm(detailedInfo.answer_msg_id);
            }
        } else if (message instanceof TLRPC.TL_gzip_packed) {
            TLRPC.TL_gzip_packed packet = (TLRPC.TL_gzip_packed)message;
            TLObject result = Utilities.decompress(packet.packed_data, getRequestWithMessageId(messageId));
            processMessage(result, messageId, messageSeqNo, messageSalt, connection, innerMsgId, containerMessageId);
        } else if (message instanceof TLRPC.Updates) {
            if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
                FileLog.e("tmessages", "received internal push");
                if (paused) {
                    pushMessagesReceived = false;
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        FileLog.e("tmessages", "acquire wakelock");
                        wakeLock.acquire(20000);
                    }
                });
                resumeNetworkInternal();
            } else {
                pushMessagesReceived = true;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (wakeLock.isHeld()) {
                            FileLog.e("tmessages", "release wakelock");
                            wakeLock.release();
                        }
                    }
                });
                MessagesController.getInstance().processUpdates((TLRPC.Updates) message, false);
            }
        } else {
            FileLog.e("tmessages", "***** Error: unknown message class " + message);
        }
    }

    void generatePing() {
        Datacenter datacenter = datacenterWithId(currentDatacenterId);
        if (datacenter != null) {
            generatePing(datacenter, false);
        }
    }

    private ByteBufferDesc generatePingData(TcpConnection connection) {
        if (connection == null) {
            return null;
        }

        TLRPC.TL_ping_delay_disconnect ping = new TLRPC.TL_ping_delay_disconnect();
        ping.ping_id = nextPingId++;
        if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
            ping.disconnect_delay = 60 * 7;
        } else {
            ping.disconnect_delay = 35;
            pingIdToDate.put(ping.ping_id, (int) (System.currentTimeMillis() / 1000));
            if (pingIdToDate.size() > 20) {
                ArrayList<Long> itemsToDelete = new ArrayList<>();
                for (Long pid : pingIdToDate.keySet()) {
                    if (pid < nextPingId - 10) {
                        itemsToDelete.add(pid);
                    }
                }
                for (Long pid : itemsToDelete) {
                    pingIdToDate.remove(pid);
                }
            }
        }

        NetworkMessage networkMessage = new NetworkMessage();
        networkMessage.protoMessage = wrapMessage(ping, connection, false);

        ArrayList<NetworkMessage> arr = new ArrayList<>();
        arr.add(networkMessage);
        return createConnectionData(arr, null, connection);
    }

    void generatePing(Datacenter datacenter, boolean push) {
        TcpConnection connection = null;
        if (push) {
            connection = datacenter.pushConnection;
        } else {
            connection = datacenter.connection;
        }
        if (connection != null && (push || !push && connection.channelToken != 0)) {
            ByteBufferDesc transportData = generatePingData(connection);
            if (transportData != null) {
                if (push) {
                    FileLog.e("tmessages", "send push ping");
                    sendingPushPing = true;
                }
                connection.sendData(transportData, true, false);
            }
        }
    }

    //================================================================================
    // TCPConnection delegate
    //================================================================================

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
                    FileLog.e("tmessages", "NETWORK STATE GET ERROR", e);
                }
            }
            final int stateCopy = connectionState;
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
                }
            });
        } else if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
            FileLog.e("tmessages", "push connection closed");
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
                    FileLog.e("tmessages", "NETWORK STATE GET ERROR", e);
                }
            }
            sendingPushPing = false;
            lastPushPingTime = System.currentTimeMillis() - 60000 * 3 + 4000;
        }
    }

    @Override
    public void tcpConnectionConnected(TcpConnection connection) {
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());
        if (datacenter.authKey != null) {
            if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
                sendingPushPing = false;
                //lastPushPingTime = System.currentTimeMillis() - 60000 * 3 + 4000; //TODO check this
                //FileLog.e("tmessages", "schedule push ping in 4 seconds");
                lastPushPingTime = System.currentTimeMillis();
                generatePing(datacenter, true);
            } else {
                if (paused && lastPauseTime != 0) {
                    lastPauseTime = System.currentTimeMillis();
                    nextSleepTimeout = 30000;
                }
                processRequestQueue(connection.transportRequestClass, connection.getDatacenterId());
            }
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
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
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
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
                    }
                });
            }
        }
        if (length == 4) {
            int error = data.readInt32();
            FileLog.e("tmessages", "mtproto error = " + error);
            connection.suspendConnection(true);
            connection.connect();
            return;
        }
        Datacenter datacenter = datacenterWithId(connection.getDatacenterId());

        long keyId = data.readInt64();
        if (keyId == 0) {
            long messageId = data.readInt64();
            if (connection.isMessageIdProcessed(messageId)) {
                finishUpdatingState(connection);
                return;
            }

            int messageLength = data.readInt32();
            int constructor = data.readInt32();

            TLObject object = TLClassStore.Instance().TLdeserialize(data, constructor, getRequestWithMessageId(messageId));

            processMessage(object, messageId, 0, 0, connection, 0, 0);

            if (object != null) {
                connection.addProcessedMessageId(messageId);
            }
        } else {
            if (datacenter.authKeyId == 0 || keyId != datacenter.authKeyId) {
                FileLog.e("tmessages", "Error: invalid auth key id " + connection);
                datacenter.switchTo443Port();
                connection.suspendConnection(true);
                connection.connect();
                return;
            }

            byte[] messageKey = data.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(datacenter.authKey, messageKey, true);

            Utilities.aesIgeEncryption(data.buffer, keyData.aesKey, keyData.aesIv, false, false, data.position(), length - 24);

            long messageServerSalt = data.readInt64();
            long messageSessionId = data.readInt64();

            if (messageSessionId != connection.getSissionId()) {
                FileLog.e("tmessages", String.format("***** Error: invalid message session ID (%d instead of %d)", messageSessionId, connection.getSissionId()));
                finishUpdatingState(connection);
                return;
            }

            boolean doNotProcess = false;

            long messageId = data.readInt64();
            int messageSeqNo = data.readInt32();
            int messageLength = data.readInt32();

            if (connection.isMessageIdProcessed(messageId)) {
                doNotProcess = true;
            }

            if (messageSeqNo % 2 != 0) {
                connection.addMessageToConfirm(messageId);
            }

            byte[] realMessageKeyFull = Utilities.computeSHA1(data.buffer, 24, Math.min(messageLength + 32 + 24, data.limit()));
            if (realMessageKeyFull == null) {
                return;
            }

            if (!Utilities.arraysEquals(messageKey, 0, realMessageKeyFull, realMessageKeyFull.length - 16)) {
                FileLog.e("tmessages", "***** Error: invalid message key");
                datacenter.switchTo443Port();
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
                    FileLog.d("tmessages", "received object " + message);
                    processMessage(message, messageId, messageSeqNo, messageServerSalt, connection, 0, 0);
                    connection.addProcessedMessageId(messageId);

                    if ((connection.transportRequestClass & RPCRequest.RPCRequestClassPush) != 0) {
                        ArrayList<NetworkMessage> messages = new ArrayList<>();
                        NetworkMessage networkMessage = connection.generateConfirmationRequest();
                        if (networkMessage != null) {
                            messages.add(networkMessage);
                        }
                        sendMessagesToTransport(messages, connection, false);
                    }
                }
            } else {
                proceedToSendingMessages(null, connection, false);
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

        if (UserConfig.isClientActivated()) {
            TLRPC.TL_auth_exportAuthorization exportAuthorization = new TLRPC.TL_auth_exportAuthorization();
            exportAuthorization.dc_id = datacenterId;

            performRpc(exportAuthorization, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        movingAuthorization = (TLRPC.TL_auth_exportedAuthorization)response;
                        authorizeOnMovingDatacenter();
                    } else {
                        Utilities.stageQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                moveToDatacenter(datacenterId);
                            }
                        }, 1000);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassWithoutLogin, currentDatacenterId);
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

        datacenter.recreateSessions();

        clearRequestsForRequestClass(RPCRequest.RPCRequestClassGeneric, datacenter);
        clearRequestsForRequestClass(RPCRequest.RPCRequestClassDownloadMedia, datacenter);
        clearRequestsForRequestClass(RPCRequest.RPCRequestClassUploadMedia, datacenter);

        if (datacenter.authKey == null) {
            datacenter.clearServerSalts();
            HandshakeAction actor = new HandshakeAction(datacenter);
            actor.delegate = this;
            dequeueActor(actor, true);
        }

        if (movingAuthorization != null) {
            TLRPC.TL_auth_importAuthorization importAuthorization = new TLRPC.TL_auth_importAuthorization();
            importAuthorization.id = UserConfig.getClientUserId();
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
            }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassWithoutLogin, datacenter.datacenterId);
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

    @Override
    public void ActionDidFinishExecution(final Action action, HashMap<String, Object> params) {
        if (action instanceof HandshakeAction) {
            HandshakeAction eactor = (HandshakeAction)action;
            eactor.datacenter.connection.delegate = this;
            saveSession();

            if (eactor.datacenter.datacenterId == currentDatacenterId || eactor.datacenter.datacenterId == movingToDatacenterId) {
                timeDifference = (Integer)params.get("timeDifference");
                eactor.datacenter.recreateSessions();

                clearRequestsForRequestClass(RPCRequest.RPCRequestClassGeneric, eactor.datacenter);
                clearRequestsForRequestClass(RPCRequest.RPCRequestClassDownloadMedia, eactor.datacenter);
                clearRequestsForRequestClass(RPCRequest.RPCRequestClassUploadMedia, eactor.datacenter);
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
