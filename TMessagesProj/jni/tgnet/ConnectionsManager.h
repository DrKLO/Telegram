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
#include <unordered_set>
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
    int32_t getCurrentPingTime();
    uint32_t getCurrentDatacenterId();
    bool isTestBackend();
    int32_t getTimeDifference();
    int32_t sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, onRequestClearFunc onClear, uint32_t flags, uint32_t datacenterId, ConnectionType connectionType, bool immediate);
    int32_t sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, onRequestClearFunc onClear, uint32_t flags, uint32_t datacenterId, ConnectionType connectionType, bool immediate, int32_t requestToken);
    void cancelRequest(int32_t token, bool notifyServer, onRequestCancelDoneFunc onCancelled);
    void cleanUp(bool resetKeys, int32_t datacenterId);
    void cancelRequestsForGuid(int32_t guid);
    void bindRequestToGuid(int32_t requestToken, int32_t guid);
    void applyDatacenterAddress(uint32_t datacenterId, std::string ipAddress, uint32_t port);
    void setDelegate(ConnectiosManagerDelegate *connectiosManagerDelegate);
    ConnectionState getConnectionState();
    void setUserId(int64_t userId);
    void setUserPremium(bool premium);
    void switchBackend(bool restart);
    void resumeNetwork(bool partial);
    void pauseNetwork();
    void setNetworkAvailable(bool value, int32_t type, bool slow);
    void setIpStrategy(uint8_t value);
    void init(uint32_t version, int32_t layer, int32_t apiId, std::string deviceModel, std::string systemVersion, std::string appVersion, std::string langCode, std::string systemLangCode, std::string configPath, std::string logPath, std::string regId, std::string cFingerprint, std::string installerId, std::string packageId, int32_t timezoneOffset, int64_t userId, bool userPremium, bool isPaused, bool enablePushConnection, bool hasNetwork, int32_t networkType, int32_t performanceClass);
    void setProxySettings(std::string address, uint16_t port, std::string username, std::string password, std::string secret);
    void setLangCode(std::string langCode);
    void setRegId(std::string regId);
    void setSystemLangCode(std::string langCode);
    void updateDcSettings(uint32_t datacenterId, bool workaround, bool ifLoadingTryAgain);
    void setPushConnectionEnabled(bool value);
    void applyDnsConfig(NativeByteBuffer *buffer, std::string phone, int32_t date);
    int64_t checkProxy(std::string address, uint16_t port, std::string username, std::string password, std::string secret, onRequestTimeFunc requestTimeFunc, jobject ptr1);

#ifdef ANDROID
    void sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, onWriteToSocketFunc onWriteToSocket, onRequestClearFunc onClear, uint32_t flags, uint32_t datacenterId, ConnectionType connectionType, bool immediate, int32_t requestToken);
    static void useJavaVM(JavaVM *vm, bool useJavaByteBuffers);
#endif

    void reconnect(int32_t datacentrId, int32_t connectionType);
    void failNotRunningRequest(int32_t token);
    void receivedIntegrityCheckClassic(int32_t requestToken, std::string nonce, std::string token);

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
    void requestSaltsForDatacenter(Datacenter *datacenter, bool media, bool useTempConnection);
    void clearRequestsForDatacenter(Datacenter *datacenter, HandshakeType type);
    void registerForInternalPushUpdates();
    void processRequestQueue(uint32_t connectionType, uint32_t datacenterId);
    void moveToDatacenter(uint32_t datacenterId);
    void authorizeOnMovingDatacenter();
    void authorizedOnMovingDatacenter();
    Datacenter *getDatacenterWithId(uint32_t datacenterId);
    std::unique_ptr<TLObject> wrapInLayer(TLObject *object, Datacenter *datacenter, Request *baseRequest);
    void removeRequestFromGuid(int32_t requestToken);
    bool cancelRequestInternal(int32_t token, int64_t messageId, bool notifyServer, bool removeFromClass, onRequestCancelDoneFunc onCancelled);
    int callEvents(int64_t now);
    int32_t sendRequestInternal(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, onRequestClearFunc onClear, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate);

    void checkPendingTasks();
    void scheduleTask(std::function<void()> task);
    void scheduleEvent(EventObject *eventObject, uint32_t time);
    void removeEvent(EventObject *eventObject);
    void onConnectionClosed(Connection *connection, int reason);
    void onConnectionConnected(Connection *connection);
    void onConnectionQuickAckReceived(Connection *connection, int32_t ack);
    void onConnectionDataReceived(Connection *connection, NativeByteBuffer *data, uint32_t length);
    bool hasPendingRequestsForConnection(Connection *connection);
    void attachConnection(ConnectionSocket *connection);
    void detachConnection(ConnectionSocket *connection);
    TLObject *TLdeserialize(TLObject *request, uint32_t bytes, NativeByteBuffer *data);
    TLObject *getRequestWithMessageId(int64_t messageId);
    void onDatacenterHandshakeComplete(Datacenter *datacenter, HandshakeType type, int32_t timeDiff);
    void onDatacenterExportAuthorizationComplete(Datacenter *datacenter);
    int64_t generateMessageId();
    uint8_t getIpStratagy();
    bool isNetworkAvailable();

    void scheduleCheckProxyInternal(ProxyCheckInfo *proxyCheckInfo);
    void checkProxyInternal(ProxyCheckInfo *proxyCheckInfo);

    int32_t instanceNum = 0;
    uint32_t configVersion = 5;
    Config *config = nullptr;

    std::list<EventObject *> events;

    std::map<uint32_t, Datacenter *> datacenters;
    std::map<int32_t, std::vector<std::int32_t>> quickAckIdToRequestIds;
    int32_t pingTime;
    int64_t pingTimeMs;
    bool testBackend = false;
    bool clientBlocked = true;
    std::string lastInitSystemLangcode = "";
    std::atomic<uint32_t> lastRequestToken{50000000};
    uint32_t currentDatacenterId = 0;
    uint32_t movingToDatacenterId = DEFAULT_DATACENTER_ID;
    int64_t pushSessionId = 0;
    int32_t currentPingTime = 0;
    int32_t currentPingTimeLive = 0;
    bool registeringForPush = false;
    int64_t lastPushPingTime = 0;
    int32_t nextPingTimeOffset = 60000 * 3;
    int64_t sendingPushPingTime = 0;
    bool sendingPushPing = false;
    bool sendingPing = false;
    bool updatingDcSettings = false;
    bool updatingDcSettingsAgain = false;
    uint32_t updatingDcSettingsAgainDcNum = 0;
    bool updatingDcSettingsWorkaround = false;
    int32_t disconnectTimeoutAmount = 0;
    bool requestingSecondAddressByTlsHashMismatch = false;
    int32_t requestingSecondAddress = 0;
    int32_t updatingDcStartTime = 0;
    int32_t lastDcUpdateTime = 0;
    int64_t lastPingTime = getCurrentTimeMonotonicMillis();
    bool networkPaused = false;
    int32_t nextSleepTimeout = CONNECTION_BACKGROUND_KEEP_TIME;
    int64_t lastPauseTime = 0;
    int64_t lastMonotonicPauseTime = 0;
    int32_t lastSystemPauseTime = 0;
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
    uint8_t ipStrategy = USE_IPV4_ONLY;
    bool lastProtocolIsIpv6 = false;
    bool lastProtocolUsefullData = false;
    std::vector<ConnectionSocket *> activeConnections;
    std::vector<ConnectionSocket *> activeConnectionsCopy;
    int epolFd;
    int eventFd;
    int *pipeFd = nullptr;
    NativeByteBuffer *networkBuffer;

    requestsList waitingLoginRequests;
    requestsList requestsQueue;
    requestsList runningRequests;
    std::vector<uint32_t> requestingSaltsForDc;
    std::unordered_set<int32_t> tokensToBeCancelled;
    int32_t lastPingId = 0;
    int64_t lastInvokeAfterMessageId = 0;

    int32_t currentNetworkType = NETWORK_TYPE_WIFI;
    uint32_t currentVersion = 1;
    int32_t currentLayer = 34;
    int32_t currentApiId = 6;
    std::string currentDeviceModel;
    std::string currentSystemVersion;
    std::string currentAppVersion;
    std::string currentLangCode;
    std::string currentRegId;
    std::string certFingerprint;
    std::string installer;
    std::string package;
    int32_t currentDeviceTimezone = 0;
    std::string currentSystemLangCode;
    std::string currentConfigPath;
    std::string currentLogPath;
    int64_t currentUserId = 0;
    bool currentUserPremium = false;
    bool registeredForInternalPush = false;
    bool pushConnectionEnabled = true;
    int32_t currentPerformanceClass = -1;

    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> genericMessagesToDatacenters;
    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> genericMediaMessagesToDatacenters;
    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> tempMessagesToDatacenters;
    std::vector<uint32_t> unknownDatacenterIds;
    std::vector<std::pair<Datacenter *, ConnectionType>> neededDatacenters;
    std::map<uint32_t, uint32_t> downloadRunningRequestCount;
    std::map<uint32_t, uint32_t> downloadCancelRunningRequestCount;
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
