/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef CONNECTIONSMANAGER_H
#define CONNECTIONSMANAGER_H

#include <pthread.h>
#include <queue>
#include <functional>
#include <sys/epoll.h>
#include <map>
#include <atomic>
#include <bits/unique_ptr.h>
#include "Defines.h"

#ifdef ANDROID
#include <jni.h>
#endif

class NativeByteBuffer;
class Connection;
class Datacenter;
class Request;
class DatacenterHandshake;
class TLObject;
class ConnectionSocket;
class TL_auth_exportedAuthorization;
class ByteArray;
class TL_config;
class EventObject;
class Config;
class ProxyCheckInfo;

class ConnectionsManager {

public:
    ConnectionsManager(int32_t instance);
    ~ConnectionsManager();

    static ConnectionsManager &getInstance(int32_t instanceNum);
    int64_t getCurrentTimeMillis();
    int64_t getCurrentTimeMonotonicMillis();
    int32_t getCurrentTime();
    bool isTestBackend();
    int32_t getTimeDifference();
    int32_t sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate);
    int32_t sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate, int32_t requestToken);
    void cancelRequest(int32_t token, bool notifyServer);
    void cleanUp(bool resetKeys);
    void cancelRequestsForGuid(int32_t guid);
    void bindRequestToGuid(int32_t requestToken, int32_t guid);
    void applyDatacenterAddress(uint32_t datacenterId, std::string ipAddress, uint32_t port);
    void setDelegate(ConnectiosManagerDelegate *connectiosManagerDelegate);
    ConnectionState getConnectionState();
    void setUserId(int32_t userId);
    void switchBackend();
    void resumeNetwork(bool partial);
    void pauseNetwork();
    void setNetworkAvailable(bool value, int32_t type, bool slow);
    void setUseIpv6(bool value);
    void init(uint32_t version, int32_t layer, int32_t apiId, std::string deviceModel, std::string systemVersion, std::string appVersion, std::string langCode, std::string systemLangCode, std::string configPath, std::string logPath, int32_t userId, bool isPaused, bool enablePushConnection, bool hasNetwork, int32_t networkType);
    void setProxySettings(std::string address, uint16_t port, std::string username, std::string password, std::string secret);
    void setLangCode(std::string langCode);
    void updateDcSettings(uint32_t datacenterId, bool workaround);
    void setPushConnectionEnabled(bool value);
    void applyDnsConfig(NativeByteBuffer *buffer, std::string phone);
    void setMtProtoVersion(int version);
    int32_t getMtProtoVersion();
    int64_t checkProxy(std::string address, uint16_t port, std::string username, std::string password, std::string secret, onRequestTimeFunc requestTimeFunc, jobject ptr1);

#ifdef ANDROID
    void sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, onWriteToSocketFunc onWriteToSocket, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate, int32_t requestToken, jobject ptr1, jobject ptr2, jobject ptr3);
    static void useJavaVM(JavaVM *vm, bool useJavaByteBuffers);
#endif

private:
    static void *ThreadProc(void *data);

    void initDatacenters();
    void loadConfig();
    void saveConfig();
    void saveConfigInternal(NativeByteBuffer *buffer);
    void select();
    void wakeup();
    void processServerResponse(TLObject *message, int64_t messageId, int32_t messageSeqNo, int64_t messageSalt, Connection *connection, int64_t innerMsgId, int64_t containerMessageId);
    void sendPing(Datacenter *datacenter, bool usePushConnection);
    void sendMessagesToConnection(std::vector<std::unique_ptr<NetworkMessage>> &messages, Connection *connection, bool reportAck);
    void sendMessagesToConnectionWithConfirmation(std::vector<std::unique_ptr<NetworkMessage>> &messages, Connection *connection, bool reportAck);
    void requestSaltsForDatacenter(Datacenter *datacenter);
    void clearRequestsForDatacenter(Datacenter *datacenter, HandshakeType type);
    void registerForInternalPushUpdates();
    void processRequestQueue(uint32_t connectionType, uint32_t datacenterId);
    void moveToDatacenter(uint32_t datacenterId);
    void authorizeOnMovingDatacenter();
    void authorizedOnMovingDatacenter();
    Datacenter *getDatacenterWithId(uint32_t datacenterId);
    std::unique_ptr<TLObject> wrapInLayer(TLObject *object, Datacenter *datacenter, Request *baseRequest);
    void removeRequestFromGuid(int32_t requestToken);
    bool cancelRequestInternal(int32_t token, int64_t messageId, bool notifyServer, bool removeFromClass);
    int callEvents(int64_t now);
    int32_t sendRequestInternal(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate);

    void checkPendingTasks();
    void scheduleTask(std::function<void()> task);
    void scheduleEvent(EventObject *eventObject, uint32_t time);
    void removeEvent(EventObject *eventObject);
    void onConnectionClosed(Connection *connection, int reason);
    void onConnectionConnected(Connection *connection);
    void onConnectionQuickAckReceived(Connection *connection, int32_t ack);
    void onConnectionDataReceived(Connection *connection, NativeByteBuffer *data, uint32_t length);
    void attachConnection(ConnectionSocket *connection);
    void detachConnection(ConnectionSocket *connection);
    TLObject *TLdeserialize(TLObject *request, uint32_t bytes, NativeByteBuffer *data);
    TLObject *getRequestWithMessageId(int64_t messageId);
    void onDatacenterHandshakeComplete(Datacenter *datacenter, HandshakeType type, int32_t timeDiff);
    void onDatacenterExportAuthorizationComplete(Datacenter *datacenter);
    int64_t generateMessageId();
    bool isIpv6Enabled();
    bool isNetworkAvailable();

    void checkProxyInternal(ProxyCheckInfo *proxyCheckInfo);

    int32_t instanceNum = 0;
    uint32_t configVersion = 3;
    Config *config = nullptr;

    std::list<EventObject *> events;

    std::map<uint32_t, Datacenter *> datacenters;
    std::map<int32_t, std::vector<std::int32_t>> quickAckIdToRequestIds;
    int32_t pingTime;
    bool testBackend = false;
    bool clientBlocked = true;
    std::atomic<uint32_t> lastRequestToken{50000000};
    uint32_t currentDatacenterId = 0;
    uint32_t movingToDatacenterId = DEFAULT_DATACENTER_ID;
    int64_t pushSessionId = 0;
    int32_t currentPingTime = 0;
    bool registeringForPush = false;
    int64_t lastPushPingTime = 0;
    bool sendingPushPing = false;
    bool updatingDcSettings = false;
    bool updatingDcSettingsWorkaround = false;
    int32_t disconnectTimeoutAmount = 0;
    int32_t requestingSecondAddress = 0;
    int32_t updatingDcStartTime = 0;
    int32_t lastDcUpdateTime = 0;
    int64_t lastPingTime = getCurrentTimeMonotonicMillis();
    bool networkPaused = false;
    int32_t nextSleepTimeout = CONNECTION_BACKGROUND_KEEP_TIME;
    int64_t lastPauseTime = 0;
    ConnectionState connectionState = ConnectionStateConnecting;
    std::unique_ptr<ByteArray> movingAuthorization;
    std::vector<int64_t> sessionsToDestroy;
    int32_t lastDestroySessionRequestTime;
    std::map<int32_t, std::vector<int32_t>> requestsByGuids;
    std::map<int32_t, int32_t> guidsByRequests;
    std::map<int64_t, int64_t> resendRequests;
    Datacenter *deserializingDatacenter;

    std::string proxyUser = "";
    std::string proxyPassword = "";
    std::string proxyAddress = "";
    std::string proxySecret = "";
    uint16_t proxyPort = 1080;
    int32_t lastPingProxyId = 2000000;
    std::vector<std::unique_ptr<ProxyCheckInfo>> proxyCheckQueue;
    std::vector<std::unique_ptr<ProxyCheckInfo>> proxyActiveChecks;

    pthread_t networkThread;
    pthread_mutex_t mutex;
    std::queue<std::function<void()>> pendingTasks;
    struct epoll_event *epollEvents;
    timespec timeSpec;
    timespec timeSpecMonotonic;
    int32_t timeDifference = 0;
    int64_t lastOutgoingMessageId = 0;
    bool networkAvailable = true;
    bool networkSlow = false;
    bool ipv6Enabled = false;
    std::vector<ConnectionSocket *> activeConnections;
    int epolFd;
    int eventFd;
    int *pipeFd;
    NativeByteBuffer *networkBuffer;

    requestsList requestsQueue;
    requestsList runningRequests;
    std::vector<uint32_t> requestingSaltsForDc;
    int32_t lastPingId = 0;

    int32_t currentNetworkType = NETWORK_TYPE_WIFI;
    uint32_t currentVersion = 1;
    int32_t currentLayer = 34;
    int32_t currentApiId = 6;
    std::string currentDeviceModel;
    std::string currentSystemVersion;
    std::string currentAppVersion;
    std::string currentLangCode;
    std::string currentSystemLangCode;
    std::string currentConfigPath;
    std::string currentLogPath;
    int32_t currentUserId = 0;
    bool registeredForInternalPush = false;
    bool pushConnectionEnabled = true;
    int32_t mtProtoVersion = 2;

    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> genericMessagesToDatacenters;
    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> genericMediaMessagesToDatacenters;
    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> tempMessagesToDatacenters;
    std::vector<uint32_t> unknownDatacenterIds;
    std::vector<std::pair<Datacenter *, ConnectionType>> neededDatacenters;
    std::map<uint32_t, uint32_t> downloadRunningRequestCount;
    std::vector<Datacenter *> unauthorizedDatacenters;
    NativeByteBuffer *sizeCalculator;

    ConnectiosManagerDelegate *delegate;

    friend class ConnectionSocket;
    friend class ConnectionSession;
    friend class Connection;
    friend class Timer;
    friend class Datacenter;
    friend class TL_message;
    friend class TL_rpc_result;
    friend class Config;
    friend class FileLoadOperation;
    friend class FileLog;
    friend class Handshake;
};

#ifdef ANDROID
extern JavaVM *javaVm;
extern JNIEnv *jniEnv[MAX_ACCOUNT_COUNT];
extern jclass jclass_ByteBuffer;
extern jmethodID jclass_ByteBuffer_allocateDirect;
#endif

#endif
