/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.telegram.TL.TLClassStore;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsManager implements Action.ActionDelegate, TcpConnection.TcpConnectionDelegate {

    public static boolean DEBUG_VERSION = false;

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
    public int currentDatacenterId;
    public int movingToDatacenterId;
    private long lastOutgoingMessageId = 0;
    private int useDifferentBackend = 0;
    private final int SESSION_VERSION = 2;
    public int timeDifference = 0;
    public int currentPingTime;
    private int lastDestroySessionRequestTime;

    public static ConnectionsManager Instance = new ConnectionsManager();

    private boolean paused = false;
    private Runnable stageRunnable;
    private Runnable pingRunnable;
    private long lastPingTime = System.currentTimeMillis();

    public ConnectionsManager() {
        lastOutgoingMessageId = 0;
        movingToDatacenterId = DEFAULT_DATACENTER_ID;
        loadSession();

        Timer serviceTimer = new Timer();
        serviceTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        if (ApplicationLoader.lastPauseTime != 0 && ApplicationLoader.lastPauseTime < System.currentTimeMillis() - 60000) {
                            if (!paused) {
                                if (DEBUG_VERSION) {
                                    Log.e("tmessages", "pausing network and timers");
                                }
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
                                Thread.sleep(500);
                                paused = true;
                                return;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (paused) {
                            paused = false;
                            if (DEBUG_VERSION) {
                                Log.e("tmessages", "resume network and timers");
                            }
                        }

                        if (datacenters != null) {
                            MessagesController.Instance.updateTimerProc();
                            if (datacenterWithId(currentDatacenterId).authKey != null) {
                                if (lastPingTime < System.currentTimeMillis() - 30000) {
                                    lastPingTime = System.currentTimeMillis();
                                    generatePing();
                                }
                                processRequestQueue(0, 0);
                            }
                        }
                    }
                });
            }
        }, 1000, 1000);
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
        //_timeOffsetFromUTC = _timeDifference + (int)[[NSTimeZone localTimeZone] secondsFromGMT];
        if (store) {
            saveSession();
        }
    }

    void loadSession() {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                File configFile = new File(Utilities.applicationContext.getFilesDir(), "config.dat");
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
                                Datacenter datacenter = new Datacenter(data);
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
                    UserConfig.clearConfig();
                }

                if (datacenters.size() == 0) {
//                    Datacenter datacenter = new Datacenter();
//                    datacenter.datacenterId = 1;
//                    datacenter.address = "173.240.5.253";
//                    datacenter.port = 443;
//                    datacenters.put(datacenter.datacenterId, datacenter);

                    Datacenter datacenter = new Datacenter();
                    datacenter.datacenterId = 1;
                    datacenter.address = "173.240.5.1";
                    datacenter.port = 443;
                    datacenters.put(datacenter.datacenterId, datacenter);
                }

                for (Datacenter datacenter : datacenters.values()) {
                    datacenter.authSessionId = (long)(MessagesController.random.nextDouble() * Long.MAX_VALUE);
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
                SerializedData data = new SerializedData();

                data.writeInt32(useDifferentBackend);
                data.writeInt32(SESSION_VERSION);
                Datacenter currentDatacenter = datacenterWithId(currentDatacenterId);
                if (currentDatacenter != null) {
                    ArrayList<Long> sessions = new ArrayList<Long>();
                    if (currentDatacenter.authSessionId != 0) {
                        sessions.add(currentDatacenter.authSessionId);
                    }
                    data.writeInt32(sessions.size());
                    for (long session : sessions) {
                        data.writeInt64(session);
                    }
                    data.writeInt32(timeDifference);
                    data.writeInt32(datacenters.size());
                    for (Datacenter datacenter : datacenters.values()) {
                        datacenter.SerializeToStream(data);
                    }
                    data.writeInt32(currentDatacenterId);
                } else {
                    data.writeInt32(0);
                }
                try {
                    File configFile = new File(Utilities.applicationContext.getFilesDir(), "config.dat");
                    if (!configFile.exists()) {
                        configFile.createNewFile();
                    }
                    FileOutputStream stream = new FileOutputStream(configFile);
                    stream.write(data.toByteArray());
                    stream.close();
                } catch (Exception e) {
                    e.printStackTrace();
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
            if (DEBUG_VERSION) {
                Log.d("tmessages", "***** Recreate generic session");
            }
            datacenter.authSessionId = (long)(MessagesController.random.nextDouble() * Long.MAX_VALUE);
        }
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

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCProgressDelegate progressBlock, boolean requiresCompletion, int requestClass) {
        return performRpc(rpc, completionBlock, progressBlock, requiresCompletion, requestClass, DEFAULT_DATACENTER_ID);
    }

    public long performRpc(final TLObject rpc, final RPCRequest.RPCRequestDelegate completionBlock, final RPCRequest.RPCProgressDelegate progressBlock, boolean requiresCompletion, int requestClass, int datacenterId) {
        return performRpc(rpc, completionBlock, progressBlock, null, requiresCompletion, requestClass, datacenterId);
    }

    TLObject wrapInLayer(TLObject object) {
        if (object.layer() > 0) {
            TLRPC.invokeWithLayer8 invoke = new TLRPC.invokeWithLayer8();
            invoke.query = object;
            if (DEBUG_VERSION) {
                Log.d("wrap in layer", "" + object);
            }
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
                request.rpcRequest = wrapInLayer(rpc);
                request.completionBlock = completionBlock;
                request.progressBlock = progressBlock;
                request.quickAckBlock = quickAckBlock;
                request.requiresCompletion = requiresCompletion;

                requestQueue.add(request);

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
                        if (DEBUG_VERSION) {
                            Log.d("tmessages", "===== Cancelled queued rpc request " + request.rawRequest);
                        }
                        requestQueue.remove(i);
                        break;
                    }
                }

                for (int i = 0; i < runningRequests.size(); i++) {
                    RPCRequest request = runningRequests.get(i);
                    if (request.token == token) {
                        found = true;

                        if (DEBUG_VERSION) {
                            Log.d("tmessages", "===== Cancelled running rpc request " + request.rawRequest);
                        }

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
                    if (DEBUG_VERSION) {
                        Log.d("tmessages", "***** Warning: cancelling unknown request");
                    }
                }
            }
        });
    }

    public static boolean isNetworkOnline() {
        boolean status = false;
        try {
            ConnectivityManager cm = (ConnectivityManager)Utilities.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
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
            e.printStackTrace();
            return false;
        }
        return status;
    }

    public int getCurrentTime() {
        return (int)(System.currentTimeMillis() / 1000) + timeDifference;
    }

    public void processRequestQueue(int requestClass, int _datacenterId) {
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
                if (requestDatacenter != null && !activeTransportTokens.containsKey(requestDatacenter.datacenterId)) {
                    transportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeDownloadTransportTokens.containsKey(requestDatacenter.datacenterId)) {
                    downloadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeUploadTransportTokens.containsKey(requestDatacenter.datacenterId)) {
                    uploadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            }
        }
        for (RPCRequest request : requestQueue) {
            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeTransportTokens.containsKey(requestDatacenter.datacenterId)) {
                    transportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeDownloadTransportTokens.containsKey(requestDatacenter.datacenterId)) {
                    downloadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                Datacenter requestDatacenter = datacenterWithId(request.runningDatacenterId);
                if (requestDatacenter != null && !activeUploadTransportTokens.containsKey(requestDatacenter.datacenterId)) {
                    uploadTransportsToResume.add(requestDatacenter.datacenterId);
                }
            }
        }

        boolean haveNetwork = true;//activeTransportTokens.size() != 0 || isNetworkOnline();

        if (!activeTransportTokens.containsKey(currentDatacenterId)) {
            transportsToResume.add(currentDatacenterId);
        }

        for (int it : transportsToResume) {
            Datacenter datacenter = datacenterWithId(it);
            if (datacenter.authKey != null) {
                if (datacenter.connection == null) {
                    datacenter.connection = new TcpConnection(datacenter.address, datacenter.port);
                    datacenter.connection.delegate = this;
                    datacenter.connection.transportRequestClass = RPCRequest.RPCRequestClassGeneric;
                    datacenter.connection.datacenterId = datacenter.datacenterId;
                }
                datacenter.connection.connect();
                /*if (it == currentDatacenterId) {
                 boolean isConnecting = datacenter.connection.channelToken != 0;
                 if (isConnecting) {
                 _isWaitingForFirstData = true;
                 }

                 if (_isConnecting != isConnecting)
                 {
                 _isConnecting = isConnecting;
                 [self dispatchConnectingState];
                 }
                 }*/
            }
        }
        for (int it : downloadTransportsToResume) {
            Datacenter datacenter = datacenterWithId(it);
            if (datacenter.authKey != null) {
                if (datacenter.downloadConnection == null) {
                    datacenter.downloadConnection = new TcpConnection(datacenter.address, datacenter.port);
                    datacenter.downloadConnection.delegate = this;
                    datacenter.downloadConnection.transportRequestClass = RPCRequest.RPCRequestClassDownloadMedia;
                    datacenter.downloadConnection.datacenterId = datacenter.datacenterId;
                    datacenter.authDownloadSessionId = (long)(MessagesController.random.nextDouble() * Long.MAX_VALUE);
                }
                datacenter.downloadConnection.connect();
            }
        }
        for (int it : uploadTransportsToResume) {
            Datacenter datacenter = datacenterWithId(it);
            if (datacenter.authKey != null) {
                if (datacenter.uploadConnection == null) {
                    datacenter.uploadConnection = new TcpConnection(datacenter.address, datacenter.port);
                    datacenter.uploadConnection.delegate = this;
                    datacenter.uploadConnection.transportRequestClass = RPCRequest.RPCRequestClassUploadMedia;
                    datacenter.uploadConnection.datacenterId = datacenter.datacenterId;
                    datacenter.authUploadSessionId = (long)(MessagesController.random.nextDouble() * Long.MAX_VALUE);
                }
                datacenter.uploadConnection.connect();
            }
        }

        final HashMap<Integer, ArrayList<NetworkMessage>> genericMessagesToDatacenters = new HashMap<Integer, ArrayList<NetworkMessage>>();

        final ArrayList<Integer> unknownDatacenterIds = new ArrayList<Integer>();
        final ArrayList<Integer> neededDatacenterIds = new ArrayList<Integer>();
        final ArrayList<Integer> unauthorizedDatacenterIds = new ArrayList<Integer>();

        int currentTime = (int)(System.currentTimeMillis() / 1000);
        for (RPCRequest request : runningRequests) {
            int datacenterId = request.runningDatacenterId;
            if (datacenterId == DEFAULT_DATACENTER_ID) {
                if (movingToDatacenterId != DEFAULT_DATACENTER_ID) {
                    continue;
                }
                datacenterId = currentDatacenterId;
            }

            Datacenter requestDatacenter = datacenterWithId(datacenterId);
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

            double maxTimeout = 8.0;

            if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                if (datacenterTransportToken == 0) {
                    continue;
                }
            } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                if (!haveNetwork) {
                    if (DEBUG_VERSION) {
                        Log.d("tmessages", "Don't have any network connection, skipping download request");
                    }
                    continue;
                }
                maxTimeout = 40.0;
            } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                if (!haveNetwork) {
                    if (DEBUG_VERSION) {
                        Log.d("tmessages", "Don't have any network connection, skipping upload request");
                    }
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
                        if (DEBUG_VERSION) {
                            Log.d("tmessages", "Request token is valid, not retrying " + request.rawRequest);
                        }
                        continue;
                    } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                        int downloadToken = requestDatacenter.downloadConnection.channelToken;
                        if (downloadToken != 0 && request.transportChannelToken == downloadToken) {
                            if (DEBUG_VERSION) {
                                Log.d("tmessages", "Request download token is valid, not retrying " + request.rawRequest);
                            }
                            continue;
                        }
                    } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                        int uploadToken = requestDatacenter.uploadConnection.channelToken;
                        if (uploadToken != 0 && request.transportChannelToken == uploadToken) {
                            if (DEBUG_VERSION) {
                                Log.d("tmessages", "Request upload token is valid, not retrying " + request.rawRequest);
                            }
                            continue;
                        }
                    }
                }

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
                    ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, sessionId, requestDatacenter.downloadConnection, false, false);
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    ArrayList<NetworkMessage> arr = new ArrayList<NetworkMessage>();
                    arr.add(networkMessage);
                    proceedToSendingMessages(arr, sessionId, requestDatacenter.uploadConnection, false, false);
                }
            }
        }

        boolean updatingState = MessagesController.Instance.updatingState;

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

            int datacenterId = request.runningDatacenterId;
            if (datacenterId == DEFAULT_DATACENTER_ID) {
                if (movingToDatacenterId != DEFAULT_DATACENTER_ID && (request.flags & RPCRequest.RPCRequestClassEnableUnauthorized) == 0) {
                    continue;
                }
                datacenterId = currentDatacenterId;
            }

            Datacenter requestDatacenter = datacenterWithId(datacenterId);
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
                    if (uploadRunningRequestCount >= 20)
                        continue;

                    if (!haveNetwork) {
                        if (DEBUG_VERSION) {
                            Log.d("tmessages", "Don't have any network connection, skipping upload request");
                        }
                        continue;
                    }

                    if (uploadRunningRequestCount >= 5) {
                        continue;
                    }

                    uploadRunningRequestCount++;
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    if (!haveNetwork) {
                        if (DEBUG_VERSION) {
                            Log.d("tmessages", "Don't have any network connection, skipping download request");
                        }
                        continue;
                    }

                    if (downloadRunningRequestCount >= 5) {
                        continue;
                    }

                    downloadRunningRequestCount++;
                }
            }

            long messageId = generateMessageId();

            SerializedData os = new SerializedData();
            request.rpcRequest.serializeToStream(os);

            if (os.length() != 0) {
                long sessionId = 0;
                if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                    sessionId = requestDatacenter.authSessionId;
                } else if ((request.flags & RPCRequest.RPCRequestClassDownloadMedia) != 0) {
                    sessionId = requestDatacenter.authDownloadSessionId;
                } else if ((request.flags & RPCRequest.RPCRequestClassUploadMedia) != 0) {
                    sessionId = requestDatacenter.authUploadSessionId;
                }

                NetworkMessage networkMessage = new NetworkMessage();
                networkMessage.protoMessage = new TLRPC.TL_protoMessage();
                networkMessage.protoMessage.msg_id = messageId;
                networkMessage.protoMessage.seqno = generateMessageSeqNo(sessionId, true);
                networkMessage.protoMessage.bytes = os.length();
                networkMessage.protoMessage.body = request.rpcRequest;
                networkMessage.rawRequest = request.rawRequest;
                networkMessage.requestId = request.token;

                request.runningMessageId = messageId;
                request.runningMessageSeqNo = networkMessage.protoMessage.seqno;
                request.serializedLength = os.length();
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
                    if (DEBUG_VERSION) {
                        Log.e("tmessages", "***** Error: request " + request.rawRequest + " has undefined session");
                    }
                }
            } else {
                if (DEBUG_VERSION) {
                    Log.e("tmessages", "***** Couldn't serialize " + request.rawRequest);
                }
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
                    datacenter.connection = new TcpConnection(datacenter.address, datacenter.port);
                    datacenter.connection.delegate = this;
                    datacenter.connection.transportRequestClass = RPCRequest.RPCRequestClassGeneric;
                    datacenter.connection.datacenterId = datacenter.datacenterId;
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

        for (int num : unknownDatacenterIds) {
            boolean notFound = true;
            for (Action actor : actionQueue) {
                if (actor instanceof UpdateDatacenterListAction) {
                    UpdateDatacenterListAction eactor = (UpdateDatacenterListAction)actor;
                    if (eactor.datacenterId == num) {
                        notFound = false;
                        break;
                    }
                }
            }
            if (notFound) {
                UpdateDatacenterListAction actor = new UpdateDatacenterListAction(num);
                actor.delegate = this;
                dequeueActor(actor, true);
            }
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
            if (num != currentDatacenterId && num != movingToDatacenterId && UserConfig.clientUserId != 0/* && unavailableDatacenterIds.get(num) == null*/) {
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
        SerializedData os = new SerializedData();
        message.serializeToStream(os);

        if (os.length() != 0) {
            TLRPC.TL_protoMessage protoMessage = new TLRPC.TL_protoMessage();
            protoMessage.msg_id = generateMessageId();
            protoMessage.bytes = os.length();
            protoMessage.body = message;
            protoMessage.seqno = generateMessageSeqNo(sessionId, meaningful);
            return protoMessage;
        } else {
            if (DEBUG_VERSION) {
                Log.e("tmessages", "***** Couldn't serialize " + message);
            }
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

            SerializedData os = new SerializedData();
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
                if (DEBUG_VERSION) {
                    Log.e("tmessages", "***** Couldn't serialize ");
                }
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
            if (DEBUG_VERSION) {
                Log.e("tmessages", String.format("***** Transport for session 0x%x not found", sessionId));
            }
            return;
        }

        ArrayList<NetworkMessage> currentMessages = new ArrayList<NetworkMessage>();

        int currentSize = 0;
        for (int a = 0; a < messagesToSend.size(); a++) {
            NetworkMessage networkMessage = messagesToSend.get(a);
            currentMessages.add(networkMessage);

            TLRPC.TL_protoMessage protoMessage = networkMessage.protoMessage;

            currentSize += protoMessage.bytes;

            // || currentMessages.size() == 5
            if (currentSize >= 3 * 1024 || a == messagesToSend.size() - 1) {
                ArrayList<Integer> quickAckId = new ArrayList<Integer>();
                byte[] transportData = createConnectionData(currentMessages, sessionId, quickAckId, connection);

                if (transportData != null) {
                    if (reportAck && quickAckId.size() != 0)
                    {
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
                    if (DEBUG_VERSION) {
                        Log.e("tmessages", "***** Transport data is nil");
                    }
                }

                currentSize = 0;
                currentMessages.clear();
            }
        }
    }

    byte[] createConnectionData(ArrayList<NetworkMessage> messages, long sessionId, ArrayList<Integer> quickAckId, TcpConnection connection) {
        Datacenter datacenter = datacenterWithId(connection.datacenterId);
        if (datacenter.authKey == null) {
            return null;
        }

        long messageId;
        TLObject messageBody;
        int messageSeqNo;

        if (messages.size() == 1) {
            NetworkMessage networkMessage = messages.get(0);
            TLRPC.TL_protoMessage message = networkMessage.protoMessage;

            if (DEBUG_VERSION) {
                Log.d("tmessages", sessionId + ":Send message " + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + message.body);
            }

            long msg_time = getTimeFromMsgId(message.msg_id);
            long currentTime = System.currentTimeMillis() + ((long)timeDifference) * 1000;

            if (msg_time < currentTime - 30000 || msg_time > currentTime + 25000) {
                if (DEBUG_VERSION) {
                    Log.d("tmessages", "wrap in messages continaer");
                }
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
                if (DEBUG_VERSION) {
                    Log.d("tmessages", sessionId + ":DC" + datacenter.datacenterId + "> Send message (" + message.seqno + ", " + message.msg_id + "): " + message.body);
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

        SerializedData innerOs = new SerializedData();
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

        SerializedData dataForEncryption = new SerializedData();
        dataForEncryption.writeRaw(innerData);
        while (dataForEncryption.length() % 16 != 0) {
            dataForEncryption.writeByte(0);
        }

        byte[] encryptedData = Utilities.aesIgeEncryption(dataForEncryption.toByteArray(), keyData.aesKey, keyData.aesIv, true, false);

        SerializedData data = new SerializedData();
        data.writeRaw(datacenter.authKeyId);
        data.writeRaw(messageKey);
        data.writeRaw(encryptedData);

        return data.toByteArray();
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
            if (DEBUG_VERSION) {
                Log.e("tmessages", "message is null");
            }
            return;
        }
        Datacenter datacenter = datacenterWithId(connection.datacenterId);

        if (message instanceof TLRPC.TL_new_session_created) {
            TLRPC.TL_new_session_created newSession = (TLRPC.TL_new_session_created)message;
            ArrayList<Long> arr = processedSessionChanges.get(sessionId);
            if (arr == null) {
                arr = new ArrayList<Long>();
                processedSessionChanges.put(sessionId, arr);
            }
            if (!arr.contains(newSession.unique_id)) {
                if (DEBUG_VERSION) {
                    Log.d("tmessages", "New session:");
                    Log.d("tmessages", String.format("    first message id: %d", newSession.first_msg_id));
                    Log.d("tmessages", String.format("    server salt: %d", newSession.server_salt));
                    Log.d("tmessages", String.format("    unique id: %d", newSession.unique_id));
                }

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

                //if (sessionId == datacenter.authSessionId && [datacenter.datacenterId isEqualToValue:currentDatacenterId])
                //    [TGTelegraphInstance stateUpdateRequired];
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
                    if (DEBUG_VERSION) {
                        Log.d("tmessages", String.format("Destroyed session %d (%s)", res.session_id, res instanceof TLRPC.TL_destroy_session_ok ? "ok" : "not found"));
                    }
                    break;
                }
            }
        } else if (message instanceof TLRPC.TL_rpc_result) {
            TLRPC.TL_rpc_result resultContainer = (TLRPC.TL_rpc_result)message;
            long resultMid = resultContainer.req_msg_id;

            boolean ignoreResult = false;
            if (DEBUG_VERSION) {
                Log.d("tmessages", "object in rpc_result is " + resultContainer.result);
            }
            if (resultContainer.result instanceof TLRPC.RpcError) {
                String errorMessage = ((TLRPC.RpcError)resultContainer.result).error_message;
                if (DEBUG_VERSION) {
                    Log.e("tmessages", String.format("***** RPC error %d: %s", ((TLRPC.RpcError)resultContainer.result).error_code, errorMessage));
                }

                int migrateToDatacenterId = DEFAULT_DATACENTER_ID;

                if (((TLRPC.RpcError)resultContainer.result).error_code == 303) {
                    ArrayList<String> migrateErrors = new ArrayList<String>();
                    migrateErrors.add("NETWORK_MIGRATE_");
                    migrateErrors.add("PHONE_MIGRATE_");
                    migrateErrors.add("USER_MIGRATE_");
                    for (String possibleError : migrateErrors) {
                        if (errorMessage.contains(possibleError)) {
                            String errorMsg = errorMessage.replace(possibleError, "");
                            Scanner scanner = new Scanner(errorMsg);
                            scanner.useDelimiter("");

                            Integer val;
                            try {
                                val = scanner.nextInt();
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
                        if (request.completionBlock != null) {
                            TLRPC.TL_error implicitError = null;
                            if (resultContainer.result instanceof TLRPC.TL_gzip_packed) {
                                TLRPC.TL_gzip_packed packet = (TLRPC.TL_gzip_packed)resultContainer.result;
                                TLObject uncomressed = Utilities.decompress(packet.packed_data, request.rawRequest);
                                if (uncomressed == null) {
                                    uncomressed = Utilities.decompress(packet.packed_data, request.rawRequest);
                                }
                                if (uncomressed == null) {
                                    throw new RuntimeException("failed to decomress responce for " + request.rawRequest);
                                }
                                resultContainer.result = uncomressed;
                            }
                            if (resultContainer.result instanceof TLRPC.RpcError) {
                                String errorMessage = ((TLRPC.RpcError) resultContainer.result).error_message;
                                if (DEBUG_VERSION) {
                                    Log.e("tmessages", String.format("***** RPC error %d: %s", ((TLRPC.RpcError) resultContainer.result).error_code, errorMessage));
                                }

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
                                            Scanner scanner = new Scanner(errorMsg);
                                            scanner.useDelimiter("");
                                            Integer val;
                                            try {
                                                val = scanner.nextInt();
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
                                    if (DEBUG_VERSION) {
                                        if (request.rawRequest == null) {
                                            Log.e("tmessages", "rawRequest is null");
                                        } else {
                                            Log.e("tmessages", "***** RPC error: invalid response class " + resultContainer.result + " (" + request.rawRequest.responseClass() + " expected)");
                                        }
                                    }
                                    implicitError = new TLRPC.TL_error();
                                    implicitError.code = -1000;
                                }
                            }

                            if (!discardResponse) {
                                if (implicitError != null || resultContainer.result instanceof TLRPC.TL_error) {
                                    request.completionBlock.run(null, implicitError != null ? implicitError : (TLRPC.TL_error) resultContainer.result);
                                } else {
                                    request.completionBlock.run(resultContainer.result, null);
                                }
                            }

                            if (implicitError != null && implicitError.code == 401) {
                                if (datacenter.datacenterId == currentDatacenterId || datacenter.datacenterId == movingToDatacenterId) {
                                    if ((request.flags & RPCRequest.RPCRequestClassGeneric) != 0) {
                                        Utilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                NotificationCenter.Instance.postNotificationName(1234);
                                                UserConfig.clearConfig();
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
                            rpcCompleted(resultMid);
                        } else {
                            request.runningMessageId = 0;
                            request.runningMessageSeqNo = 0;
                            request.transportChannelToken = 0;
                        }
                        break;
                    }
                }

                if (!found) {
                    if (DEBUG_VERSION) {
                        Log.d("tmessages", "Response received, but request wasn't found.");
                    }
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

            if (DEBUG_VERSION) {
                Log.e("tmessages", String.format("***** Bad message: %d", badMsgNotification.error_code));
            }
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
            MessagesController.Instance.processUpdates((TLRPC.Updates)message);
        } else {
            if (DEBUG_VERSION) {
                Log.e("tmessages", "***** Error: unknown message class " + message);
            }
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

        TLRPC.TL_ping ping = new TLRPC.TL_ping();
        ping.ping_id = nextPingId++;

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

    //================================================================================
    // TCPConnection delegate
    //================================================================================

    @Override
    public void tcpConnectionClosed(TcpConnection connection) {
        if (connection.datacenterId == currentDatacenterId && (connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (isNetworkOnline()) {
                connectionState = 2;
            } else {
                connectionState = 1;
            }
            final int stateCopy = connectionState;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.Instance.postNotificationName(703, stateCopy);
                }
            });
        }
    }

    @Override
    public void tcpConnectionConnected(TcpConnection connection) {
        /*int requestClass = [transport transportRequestClass];
        if ([transport datacenter].datacenterId == _currentDatacenterId && requestClass & TGRequestClassGeneric)
        {
            if (_isConnecting)
            {
                _isConnecting = false;
                _isWaitingForFirstData = true;
                [self dispatchConnectingState];
            }

            if (!_isReady)
                return;
        }*/

        Datacenter datacenter = datacenterWithId(connection.datacenterId);
        if (datacenter.authKey != null) {
            processRequestQueue(connection.transportRequestClass, connection.datacenterId);
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
        if (connection.datacenterId == currentDatacenterId && (connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (ConnectionsManager.Instance.connectionState == 3 && !MessagesController.Instance.gettingDifference) {
                ConnectionsManager.Instance.connectionState = 0;
                final int stateCopy = ConnectionsManager.Instance.connectionState;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.Instance.postNotificationName(703, stateCopy);
                    }
                });
            }
        }
    }

    @Override
    public void tcpConnectionReceivedData(TcpConnection connection, byte[] data) {
        if (connection.datacenterId == currentDatacenterId && (connection.transportRequestClass & RPCRequest.RPCRequestClassGeneric) != 0) {
            if (connectionState == 1 || connectionState == 2) {
                connectionState = 3;
                final int stateCopy = connectionState;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.Instance.postNotificationName(703, stateCopy);
                    }
                });
            }
        }
        Datacenter datacenter = datacenterWithId(connection.datacenterId);

        SerializedData is = new SerializedData(data);

        byte[] keyId = is.readData(8);
        SerializedData keyIdData = new SerializedData(keyId);
        if (keyIdData.readInt64() == 0) {
            long messageId = is.readInt64();
            if (isMessageIdProcessed(0, messageId)) {
                finishUpdatingState(connection);
                return;
            }

            int messageLength = is.readInt32();

            int constructor = is.readInt32();

            TLObject object = TLClassStore.Instance().TLdeserialize(is, constructor, getRequestWithMessageId(messageId));

            processMessage(object, messageId, 0, 0, connection, 0, 0, 0);

            if (object != null) {
                addProcessedMessageId(0, messageId);
            }
        } else {
            if (datacenter.authKeyId == null || !Arrays.equals(keyId, datacenter.authKeyId)) {
                if (DEBUG_VERSION) {
                    Log.e("tmessages", "Error: invalid auth key id " + connection);
                }
                return;
            }

            byte[] messageKey = is.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(datacenter.authKey, messageKey, true);

            byte[] messageData = is.readData(data.length - 24);
            messageData = Utilities.aesIgeEncryption(messageData, keyData.aesKey, keyData.aesIv, false, false);

            SerializedData messageIs = new SerializedData(messageData);
            long messageServerSalt = messageIs.readInt64();
            long messageSessionId = messageIs.readInt64();

            if (messageSessionId != datacenter.authSessionId && messageSessionId != datacenter.authDownloadSessionId && messageSessionId != datacenter.authUploadSessionId) {
                if (DEBUG_VERSION) {
                    Log.e("tmessages", String.format("***** Error: invalid message session ID (%d instead of %d)", messageSessionId, datacenter.authSessionId));
                }
                finishUpdatingState(connection);
                return;
            }

            boolean doNotProcess = false;

            long messageId = messageIs.readInt64();
            int messageSeqNo = messageIs.readInt32();

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

            if (!doNotProcess) {
                int messageLength = messageIs.readInt32();

                int constructor = messageIs.readInt32();
                TLObject message = TLClassStore.Instance().TLdeserialize(messageIs, constructor, getRequestWithMessageId(messageId));

                if (message == null) {
                    if (DEBUG_VERSION) {
                        Log.e("tmessages", "***** Error parsing message: " + constructor);
                    }
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
            boolean notFound = true;
            for (Action actor : actionQueue) {
                if (actor instanceof UpdateDatacenterListAction) {
                    UpdateDatacenterListAction eactor = (UpdateDatacenterListAction)actor;
                    if (eactor.datacenterId == movingToDatacenterId) {
                        notFound = false;
                        break;
                    }
                }
            }
            if (notFound) {
                UpdateDatacenterListAction actor = new UpdateDatacenterListAction(movingToDatacenterId);
                actor.delegate = this;
                dequeueActor(actor, true);
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
        } else if (action instanceof UpdateDatacenterListAction) {
            UpdateDatacenterListAction eactor = (UpdateDatacenterListAction)action;

            @SuppressWarnings("unchecked")
            ArrayList<Datacenter> arr = (ArrayList<Datacenter>)params.get("datacenters");
            if (arr.size() != 0) {
                for (Datacenter datacenter : arr) {
                    Datacenter exist = datacenterWithId(datacenter.datacenterId);
                    if (exist == null) {
                        datacenters.put(datacenter.datacenterId, datacenter);
                    } else {
                        exist.address = datacenter.address;
                        exist.port = datacenter.port;
                        if (exist.port == 25) {
                            exist.port = 443;
                        }
                    }
                    if (datacenter.datacenterId == movingToDatacenterId) {
                        movingToDatacenterId = DEFAULT_DATACENTER_ID;
                        moveToDatacenter(datacenter.datacenterId);
                    }
                }
                saveSession();

                processRequestQueue(RPCRequest.RPCRequestClassTransportMask, eactor.datacenterId);
            }
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
