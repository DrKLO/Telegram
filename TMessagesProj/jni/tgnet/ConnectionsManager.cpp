/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <stdlib.h>
#include <sys/eventfd.h>
#include <unistd.h>
#include <chrono>
#include <algorithm>
#include <fcntl.h>
#include <memory.h>
#include <openssl/rand.h>
#include <zlib.h>
#include <string>
#include <inttypes.h>
#include "ConnectionsManager.h"
#include "FileLog.h"
#include "EventObject.h"
#include "MTProtoScheme.h"
#include "ApiScheme.h"
#include "NativeByteBuffer.h"
#include "Connection.h"
#include "Datacenter.h"
#include "Request.h"
#include "BuffersStorage.h"
#include "ByteArray.h"
#include "Config.h"
#include "ProxyCheckInfo.h"

#ifdef ANDROID
#include <jni.h>
JavaVM *javaVm = nullptr;
JNIEnv *jniEnv[MAX_ACCOUNT_COUNT];
jclass jclass_ByteBuffer = nullptr;
jmethodID jclass_ByteBuffer_allocateDirect = 0;
#endif

static bool done = false;

ConnectionsManager::ConnectionsManager(int32_t instance) {
    instanceNum = instance;
    if ((epolFd = epoll_create(128)) == -1) {
        DEBUG_E("unable to create epoll instance");
        exit(1);
    }
    int flags;
    if ((flags = fcntl(epolFd, F_GETFD, NULL)) < 0) {
        DEBUG_W("fcntl(%d, F_GETFD)", epolFd);
    }
    if (!(flags & FD_CLOEXEC)) {
        if (fcntl(epolFd, F_SETFD, flags | FD_CLOEXEC) == -1) {
            DEBUG_W("fcntl(%d, F_SETFD)", epolFd);
        }
    }

    if ((epollEvents = new epoll_event[128]) == nullptr) {
        DEBUG_E("unable to allocate epoll events");
        exit(1);
    }

    eventFd = eventfd(0, EFD_NONBLOCK);
    if (eventFd != -1) {
        struct epoll_event event = {0};
        event.data.ptr = new EventObject(&eventFd, EventObjectTypeEvent);
        event.events = EPOLLIN | EPOLLET;
        if (epoll_ctl(epolFd, EPOLL_CTL_ADD, eventFd, &event) == -1) {
            eventFd = -1;
            FileLog::e("unable to add eventfd");
        }
    }

    if (eventFd == -1) {
        pipeFd = new int[2];
        if (pipe(pipeFd) != 0) {
            DEBUG_E("unable to create pipe");
            exit(1);
        }
        flags = fcntl(pipeFd[0], F_GETFL);
        if (flags == -1) {
            DEBUG_E("fcntl get pipefds[0] failed");
            exit(1);
        }
        if (fcntl(pipeFd[0], F_SETFL, flags | O_NONBLOCK) == -1) {
            DEBUG_E("fcntl set pipefds[0] failed");
            exit(1);
        }

        flags = fcntl(pipeFd[1], F_GETFL);
        if (flags == -1) {
            DEBUG_E("fcntl get pipefds[1] failed");
            exit(1);
        }
        if (fcntl(pipeFd[1], F_SETFL, flags | O_NONBLOCK) == -1) {
            DEBUG_E("fcntl set pipefds[1] failed");
            exit(1);
        }

        EventObject *eventObject = new EventObject(pipeFd, EventObjectTypePipe);

        epoll_event eventMask = {};
        eventMask.events = EPOLLIN;
        eventMask.data.ptr = eventObject;
        if (epoll_ctl(epolFd, EPOLL_CTL_ADD, pipeFd[0], &eventMask) != 0) {
            DEBUG_E("can't add pipe to epoll");
            exit(1);
        }
    }

    sizeCalculator = new NativeByteBuffer(true);
    networkBuffer = new NativeByteBuffer((uint32_t) READ_BUFFER_SIZE);
    if (networkBuffer == nullptr) {
        DEBUG_E("unable to allocate read buffer");
        exit(1);
    }

    pthread_mutex_init(&mutex, NULL);
}

ConnectionsManager::~ConnectionsManager() {
    if (epolFd != 0) {
        close(epolFd);
        epolFd = 0;
    }
    pthread_mutex_destroy(&mutex);
}

ConnectionsManager& ConnectionsManager::getInstance(int32_t instanceNum) {
    switch (instanceNum) {
        case 0:
            static ConnectionsManager instance0(0);
            return instance0;
        case 1:
            static ConnectionsManager instance1(1);
            return instance1;
        case 2:
        default:
            static ConnectionsManager instance2(2);
            return instance2;
    }
}

int ConnectionsManager::callEvents(int64_t now) {
    if (!events.empty()) {
        for (std::list<EventObject *>::iterator iter = events.begin(); iter != events.end();) {
            EventObject *eventObject = (*iter);
            if (eventObject->time <= now) {
                iter = events.erase(iter);
                eventObject->onEvent(0);
            } else {
                int diff = (int) (eventObject->time - now);
                return diff > 1000 || diff < 0 ? 1000 : diff;
            }
        }
    }
    if (!networkPaused) {
        return 1000;
    }
    int32_t timeToPushPing = (int32_t) ((sendingPushPing ? 30000 : 60000 * 3) - llabs(now - lastPushPingTime));
    if (timeToPushPing <= 0) {
        return 1000;
    }
    DEBUG_D("schedule next epoll wakeup in %d ms", timeToPushPing);
    return timeToPushPing;
}

void ConnectionsManager::checkPendingTasks() {
    while (true) {
        std::function<void()> task;
        pthread_mutex_lock(&mutex);
        if (pendingTasks.empty()) {
            pthread_mutex_unlock(&mutex);
            return;
        }
        task = pendingTasks.front();
        pendingTasks.pop();
        pthread_mutex_unlock(&mutex);
        task();
    }
}

void ConnectionsManager::select() {
    checkPendingTasks();
    int eventsCount = epoll_wait(epolFd, epollEvents, 128, callEvents(getCurrentTimeMonotonicMillis()));
    checkPendingTasks();
    int64_t now = getCurrentTimeMonotonicMillis();
    callEvents(now);
    for (int32_t a = 0; a < eventsCount; a++) {
        EventObject *eventObject = (EventObject *) epollEvents[a].data.ptr;
        eventObject->onEvent(epollEvents[a].events);
    }
    size_t count = activeConnections.size();
    for (uint32_t a = 0; a < count; a++) {
        activeConnections[a]->checkTimeout(now);
    }

    Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
    if (pushConnectionEnabled) {
        if ((sendingPushPing && llabs(now - lastPushPingTime) >= 30000) || llabs(now - lastPushPingTime) >= 60000 * 3 + 10000) {
            lastPushPingTime = 0;
            sendingPushPing = false;
            if (datacenter != nullptr) {
                Connection *connection = datacenter->getPushConnection(false);
                if (connection != nullptr) {
                    connection->suspendConnection();
                }
            }
            DEBUG_D("push ping timeout");
        }
        if (llabs(now - lastPushPingTime) >= 60000 * 3) {
            DEBUG_D("time for push ping");
            lastPushPingTime = now;
            if (datacenter != nullptr) {
                sendPing(datacenter, true);
            }
        }
    }

    if (lastPauseTime != 0 && llabs(now - lastPauseTime) >= nextSleepTimeout) {
        bool dontSleep = !requestingSaltsForDc.empty();
        if (!dontSleep) {
            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if (request->connectionType & ConnectionTypeDownload || request->connectionType & ConnectionTypeUpload) {
                    dontSleep = true;
                    break;
                }
            }
        }
        if (!dontSleep) {
            for (requestsIter iter = requestsQueue.begin(); iter != requestsQueue.end(); iter++) {
                Request *request = iter->get();
                if (request->connectionType & ConnectionTypeDownload || request->connectionType & ConnectionTypeUpload) {
                    dontSleep = true;
                    break;
                }
            }
        }
        if (!dontSleep) {
            if (!networkPaused) {
                DEBUG_D("pausing network and timers by sleep time = %d", nextSleepTimeout);
                for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
                    iter->second->suspendConnections();
                }
            }
            networkPaused = true;
            return;
        } else {
            lastPauseTime = now;
            DEBUG_D("don't sleep because of salt, upload or download request");
        }
    }
    if (networkPaused) {
        networkPaused = false;
        DEBUG_D("resume network and timers");
    }

    if (delegate != nullptr) {
        delegate->onUpdate(instanceNum);
    }
    if (datacenter != nullptr) {
        if (datacenter->hasAuthKey(ConnectionTypeGeneric, 1)) {
            if (llabs(now - lastPingTime) >= 19000) {
                lastPingTime = now;
                sendPing(datacenter, false);
            }
            if (abs((int32_t) (now / 1000) - lastDcUpdateTime) >= DC_UPDATE_TIME) {
                updateDcSettings(0, false);
            }
            processRequestQueue(0, 0);
        } else if (!datacenter->isHandshakingAny()) {
            datacenter->beginHandshake(HandshakeTypeAll, true);
        }
    }
}

void ConnectionsManager::scheduleTask(std::function<void()> task) {
    pthread_mutex_lock(&mutex);
    pendingTasks.push(task);
    pthread_mutex_unlock(&mutex);
    wakeup();
}

void ConnectionsManager::scheduleEvent(EventObject *eventObject, uint32_t time) {
    eventObject->time = getCurrentTimeMonotonicMillis() + time;
    std::list<EventObject *>::iterator iter;
    for (iter = events.begin(); iter != events.end(); iter++) {
        if ((*iter)->time > eventObject->time) {
            break;
        }
    }
    events.insert(iter, eventObject);
}

void ConnectionsManager::removeEvent(EventObject *eventObject) {
    for (std::list<EventObject *>::iterator iter = events.begin(); iter != events.end(); iter++) {
        if (*iter == eventObject) {
            events.erase(iter);
            break;
        }
    }
}

void ConnectionsManager::wakeup() {
    if (pipeFd == nullptr) {
        eventfd_write(eventFd, 1);
    } else {
        char ch = 'x';
        write(pipeFd[1], &ch, 1);
    }
}

void *ConnectionsManager::ThreadProc(void *data) {
    DEBUG_D("network thread started");
    ConnectionsManager *networkManager = (ConnectionsManager *) (data);
#ifdef ANDROID
    javaVm->AttachCurrentThread(&jniEnv[networkManager->instanceNum], NULL);
#endif
    if (networkManager->currentUserId != 0 && networkManager->pushConnectionEnabled) {
        Datacenter *datacenter = networkManager->getDatacenterWithId(networkManager->currentDatacenterId);
        if (datacenter != nullptr) {
            datacenter->createPushConnection()->setSessionId(networkManager->pushSessionId);
            networkManager->sendPing(datacenter, true);
        }
    }
    do {
        networkManager->select();
    } while (!done);
    return nullptr;
}

void ConnectionsManager::loadConfig() {
    if (config == nullptr) {
        config = new Config(instanceNum, "tgnet.dat");
    }
    NativeByteBuffer *buffer = config->readConfig();
    if (buffer != nullptr) {
        uint32_t version = buffer->readUint32(nullptr);
        DEBUG_D("config version = %u", version);
        if (version <= configVersion) {
            testBackend = buffer->readBool(nullptr);
            if (version >= 3) {
                clientBlocked = buffer->readBool(nullptr);
            }
            if (buffer->readBool(nullptr)) {
                currentDatacenterId = buffer->readUint32(nullptr);
                timeDifference = buffer->readInt32(nullptr);
                lastDcUpdateTime = buffer->readInt32(nullptr);
                pushSessionId = buffer->readInt64(nullptr);
                if (version >= 2) {
                    registeredForInternalPush = buffer->readBool(nullptr);
                }

                DEBUG_D("current dc id = %u, time difference = %d, registered for push = %d", currentDatacenterId, timeDifference, (int32_t) registeredForInternalPush);

                uint32_t count = buffer->readUint32(nullptr);
                for (uint32_t a = 0; a < count; a++) {
                    sessionsToDestroy.push_back(buffer->readInt64(nullptr));
                }

                count = buffer->readUint32(nullptr);
                for (uint32_t a = 0; a < count; a++) {
                    Datacenter *datacenter = new Datacenter(instanceNum, buffer);
                    datacenters[datacenter->getDatacenterId()] = datacenter;
                    DEBUG_D("datacenter(%p) %u loaded (hasAuthKey = %d)", datacenter, datacenter->getDatacenterId(), (int) datacenter->hasPermanentAuthKey());
                }
            }
        }
        buffer->reuse();
    }

    if (currentDatacenterId != 0 && currentUserId) {
        Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
        if (datacenter == nullptr || !datacenter->hasPermanentAuthKey()) {
            if (datacenter != nullptr) {
                DEBUG_D("reset authorization because of dc %d", currentDatacenterId);
            }
            currentDatacenterId = 0;
            datacenters.clear();
            scheduleTask([&] {
                if (delegate != nullptr) {
                    delegate->onLogout(instanceNum);
                }
            });
        }
    }

    initDatacenters();

    if ((datacenters.size() != 0 && currentDatacenterId == 0) || pushSessionId == 0) {
        if (pushSessionId == 0) {
            RAND_bytes((uint8_t *) &pushSessionId, 8);
        }
        if (currentDatacenterId == 0) {
            currentDatacenterId = 2;
        }
        saveConfig();
    }
    movingToDatacenterId = DEFAULT_DATACENTER_ID;
}

void ConnectionsManager::saveConfigInternal(NativeByteBuffer *buffer) {
    buffer->writeInt32(configVersion);
    buffer->writeBool(testBackend);
    buffer->writeBool(clientBlocked);
    Datacenter *currentDatacenter = getDatacenterWithId(currentDatacenterId);
    buffer->writeBool(currentDatacenter != nullptr);
    if (currentDatacenter != nullptr) {
        buffer->writeInt32(currentDatacenterId);
        buffer->writeInt32(timeDifference);
        buffer->writeInt32(lastDcUpdateTime);
        buffer->writeInt64(pushSessionId);
        buffer->writeBool(registeredForInternalPush);

        std::vector<int64_t> sessions;
        currentDatacenter->getSessions(sessions);

        uint32_t count = (uint32_t) sessions.size();
        buffer->writeInt32(count);
        for (uint32_t a = 0; a < count; a++) {
            buffer->writeInt64(sessions[a]);
        }
        count = (uint32_t) datacenters.size();
        buffer->writeInt32(count);
        for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
            iter->second->serializeToStream(buffer);
        }
    }
}

void ConnectionsManager::saveConfig() {
    if (config == nullptr) {
        config = new Config(instanceNum, "tgnet.dat");
    }
    sizeCalculator->clearCapacity();
    saveConfigInternal(sizeCalculator);
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(sizeCalculator->capacity());
    saveConfigInternal(buffer);
    config->writeConfig(buffer);
    buffer->reuse();
}

inline NativeByteBuffer *decompressGZip(NativeByteBuffer *data) {
    int retCode;
    z_stream stream;

    memset(&stream, 0, sizeof(z_stream));
    stream.avail_in = data->limit();
    stream.next_in = data->bytes();

    retCode = inflateInit2(&stream, 15 + 32);
    if (retCode != Z_OK) {
        DEBUG_E("can't decompress data");
        exit(1);
    }
    NativeByteBuffer *result = BuffersStorage::getInstance().getFreeBuffer(data->limit() * 4);
    stream.avail_out = result->capacity();
    stream.next_out = result->bytes();
    while (1) {
        retCode = inflate(&stream, Z_NO_FLUSH);
        if (retCode == Z_STREAM_END) {
            break;
        }
        if (retCode == Z_OK) {
            NativeByteBuffer *newResult = BuffersStorage::getInstance().getFreeBuffer(result->capacity() * 2);
            memcpy(newResult->bytes(), result->bytes(), result->capacity());
            stream.avail_out = newResult->capacity() - result->capacity();
            stream.next_out = newResult->bytes() + result->capacity();
            result->reuse();
            result = newResult;
        } else {
            DEBUG_E("can't decompress data");
            exit(1);
        }
    }
    result->limit((uint32_t) stream.total_out);
    inflateEnd(&stream);
    return result;
}

inline NativeByteBuffer *compressGZip(NativeByteBuffer *buffer) {
    if (buffer == nullptr || buffer->limit() == 0) {
        return nullptr;
    }
    z_stream stream;
    int retCode;

    memset(&stream, 0, sizeof(z_stream));
    stream.avail_in = buffer->limit();
    stream.next_in = buffer->bytes();

    retCode = deflateInit2(&stream, Z_BEST_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY);
    if (retCode != Z_OK) {
        DEBUG_E("%s: deflateInit2() failed with error %i", __PRETTY_FUNCTION__, retCode);
        return nullptr;
    }

    NativeByteBuffer *result = BuffersStorage::getInstance().getFreeBuffer(buffer->limit());
    stream.avail_out = result->limit();
    stream.next_out = result->bytes();
    retCode = deflate(&stream, Z_FINISH);
    if ((retCode != Z_OK) && (retCode != Z_STREAM_END)) {
        DEBUG_E("%s: deflate() failed with error %i", __PRETTY_FUNCTION__, retCode);
        deflateEnd(&stream);
        result->reuse();
        return nullptr;
    }
    if (retCode != Z_STREAM_END || stream.total_out >= buffer->limit() - 4) {
        deflateEnd(&stream);
        result->reuse();
        return nullptr;
    }
    result->limit((uint32_t) stream.total_out);
    deflateEnd(&stream);
    return result;
}

int64_t ConnectionsManager::getCurrentTimeMillis() {
    clock_gettime(CLOCK_REALTIME, &timeSpec);
    return (int64_t) timeSpec.tv_sec * 1000 + (int64_t) timeSpec.tv_nsec / 1000000;
}

int64_t ConnectionsManager::getCurrentTimeMonotonicMillis() {
    clock_gettime(CLOCK_MONOTONIC, &timeSpecMonotonic);
    return (int64_t) timeSpecMonotonic.tv_sec * 1000 + (int64_t) timeSpecMonotonic.tv_nsec / 1000000;
}

int32_t ConnectionsManager::getCurrentTime() {
    return (int32_t) (getCurrentTimeMillis() / 1000) + timeDifference;
}

bool ConnectionsManager::isTestBackend() {
    return testBackend;
}

int32_t ConnectionsManager::getTimeDifference() {
    return timeDifference;
}

int64_t ConnectionsManager::generateMessageId() {
    int64_t messageId = (int64_t) ((((double) getCurrentTimeMillis() + ((double) timeDifference) * 1000) * 4294967296.0) / 1000.0);
    if (messageId <= lastOutgoingMessageId) {
        messageId = lastOutgoingMessageId + 1;
    }
    while (messageId % 4 != 0) {
        messageId++;
    }
    lastOutgoingMessageId = messageId;
    return messageId;
}

bool ConnectionsManager::isNetworkAvailable() {
    return networkAvailable;
}

void ConnectionsManager::cleanUp(bool resetKeys) {
    scheduleTask([&, resetKeys] {
        for (requestsIter iter = requestsQueue.begin(); iter != requestsQueue.end();) {
            Request *request = iter->get();
            if (request->requestFlags & RequestFlagWithoutLogin) {
                iter++;
                continue;
            }
            if (request->onCompleteRequestCallback != nullptr) {
                TL_error *error = new TL_error();
                error->code = -1000;
                error->text = "";
                request->onComplete(nullptr, error, 0);
                delete error;
            }
            iter = requestsQueue.erase(iter);
        }
        for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end();) {
            Request *request = iter->get();
            if (request->requestFlags & RequestFlagWithoutLogin) {
                iter++;
                continue;
            }
            if (request->onCompleteRequestCallback != nullptr) {
                TL_error *error = new TL_error();
                error->code = -1000;
                error->text = "";
                request->onComplete(nullptr, error, 0);
                delete error;
            }
            iter = runningRequests.erase(iter);
        }
        quickAckIdToRequestIds.clear();

        for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
            if (resetKeys) {
                iter->second->clearAuthKey(HandshakeTypeAll);
            }
            iter->second->recreateSessions(HandshakeTypeAll);
            iter->second->authorized = false;
        }
        sessionsToDestroy.clear();
        currentUserId = 0;
        registeredForInternalPush = false;
        saveConfig();
    });
}

void ConnectionsManager::onConnectionClosed(Connection *connection, int reason) {
    Datacenter *datacenter = connection->getDatacenter();
    if ((connection->getConnectionType() == ConnectionTypeGeneric || connection->getConnectionType() == ConnectionTypeGenericMedia) && datacenter->isHandshakingAny()) {
        datacenter->onHandshakeConnectionClosed(connection);
    }
    if (connection->getConnectionType() == ConnectionTypeGeneric) {
        if (datacenter->getDatacenterId() == currentDatacenterId) {
            if (!connection->isSuspended() && proxyAddress.empty()) {
                if (reason == 2) {
                    disconnectTimeoutAmount += connection->getTimeout();
                } else {
                    disconnectTimeoutAmount += 4;
                }
                DEBUG_D("increase disconnect timeout %d", disconnectTimeoutAmount);
                int32_t maxTimeout;
                if (clientBlocked) {
                    maxTimeout = 5;
                } else {
                    maxTimeout = 20;
                }
                if (disconnectTimeoutAmount >= maxTimeout) {
                    if (!connection->hasUsefullData()) {
                        DEBUG_D("start requesting new address and port due to timeout reach");
                        requestingSecondAddress = 0;
                        delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
                    } else {
                        DEBUG_D("connection has usefull data, don't request anything");
                    }
                    disconnectTimeoutAmount = 0;
                }
            }

            if (networkAvailable) {
                if (proxyAddress.empty()) {
                    if (connectionState != ConnectionStateConnecting) {
                        connectionState = ConnectionStateConnecting;
                        if (delegate != nullptr) {
                            delegate->onConnectionStateChanged(connectionState, instanceNum);
                        }
                    }
                } else {
                    if (connectionState != ConnectionStateConnectingViaProxy) {
                        connectionState = ConnectionStateConnectingViaProxy;
                        if (delegate != nullptr) {
                            delegate->onConnectionStateChanged(connectionState, instanceNum);
                        }
                    }
                }
            } else {
                if (connectionState != ConnectionStateWaitingForNetwork) {
                    connectionState = ConnectionStateWaitingForNetwork;
                    if (delegate != nullptr) {
                        delegate->onConnectionStateChanged(connectionState, instanceNum);
                    }
                }
            }
        }
    } else if (connection->getConnectionType() == ConnectionTypePush) {
        DEBUG_D("connection(%p) push connection closed", connection);
        sendingPushPing = false;
        lastPushPingTime = getCurrentTimeMonotonicMillis() - 60000 * 3 + 4000;
    } else if (connection->getConnectionType() == ConnectionTypeProxy) {
        scheduleTask([&, connection] {
            for (std::vector<std::unique_ptr<ProxyCheckInfo>>::iterator iter = proxyActiveChecks.begin(); iter != proxyActiveChecks.end(); iter++) {
                ProxyCheckInfo *proxyCheckInfo = iter->get();
                if (proxyCheckInfo->connectionNum == connection->getConnectionNum()) {
                    bool found = false;
                    for (requestsIter iter2 = runningRequests.begin(); iter2 != runningRequests.end(); iter2++) {
                        Request *request = iter2->get();
                        if (connection->getConnectionToken() == request->connectionToken && request->requestToken == proxyCheckInfo->requestToken && (request->connectionType & 0x0000ffff) == ConnectionTypeProxy) {
                            request->completed = true;
                            runningRequests.erase(iter2);
                            proxyCheckInfo->onRequestTime(-1);
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        proxyActiveChecks.erase(iter);
                        if (!proxyCheckQueue.empty()) {
                            proxyCheckInfo = proxyCheckQueue[0].release();
                            proxyCheckQueue.erase(proxyCheckQueue.begin());
                            checkProxyInternal(proxyCheckInfo);
                        }
                    }
                    break;
                }
            }
        });
    }
}

void ConnectionsManager::onConnectionConnected(Connection *connection) {
    Datacenter *datacenter = connection->getDatacenter();
    if ((connection->getConnectionType() == ConnectionTypeGeneric || connection->getConnectionType() == ConnectionTypeGenericMedia) && datacenter->isHandshakingAny()) {
        datacenter->onHandshakeConnectionConnected(connection);
        return;
    }

    if (datacenter->hasAuthKey(connection->getConnectionType(), 1)) {
        if (connection->getConnectionType() == ConnectionTypePush) {
            sendingPushPing = false;
            lastPushPingTime = getCurrentTimeMonotonicMillis();
            sendPing(datacenter, true);
        } else {
            if (networkPaused && lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
            }
            processRequestQueue(connection->getConnectionType(), datacenter->getDatacenterId());
        }
    }
}

void ConnectionsManager::onConnectionQuickAckReceived(Connection *connection, int32_t ack) {
    std::map<int32_t, std::vector<int32_t>>::iterator iter = quickAckIdToRequestIds.find(ack);
    if (iter == quickAckIdToRequestIds.end()) {
        return;
    }
    for (requestsIter iter2 = runningRequests.begin(); iter2 != runningRequests.end(); iter2++) {
        Request *request = iter2->get();
        if (std::find(iter->second.begin(), iter->second.end(), request->requestToken) != iter->second.end()) {
            request->onQuickAck();
        }
    }
    quickAckIdToRequestIds.erase(iter);
}

void ConnectionsManager::onConnectionDataReceived(Connection *connection, NativeByteBuffer *data, uint32_t length) {
    bool error = false;
    if (length <= 24 + 32) {
        int32_t code = data->readInt32(&error);
        if (code == 0) {
            DEBUG_D("mtproto noop");
        } else if (code == -1) {
            int32_t ackId = data->readInt32(&error);
            if (!error) {
                onConnectionQuickAckReceived(connection, ackId & (~(1 << 31)));
            }
        } else {
            Datacenter *datacenter = connection->getDatacenter();
            DEBUG_W("mtproto error = %d", code);
            if (code == -444 && connection->getConnectionType() == ConnectionTypeGeneric && !proxyAddress.empty() && !proxySecret.empty()) {
                if (delegate != nullptr) {
                    delegate->onProxyError(instanceNum);
                }
            } else if (code == -404 && (datacenter->isCdnDatacenter || PFS_ENABLED)) {
                if (!datacenter->isHandshaking(connection->isMediaConnection)) {
                    datacenter->clearAuthKey(connection->isMediaConnection ? HandshakeTypeMediaTemp : HandshakeTypeTemp);
                    datacenter->beginHandshake(connection->isMediaConnection ? HandshakeTypeMediaTemp : HandshakeTypeTemp, true);
                    DEBUG_D("connection(%p, account%u, dc%u, type %d) reset auth key due to -404 error", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType());
                }
            } else {
                connection->reconnect();
            }
        }
        return;
    }
    uint32_t mark = data->position();

    int64_t keyId = data->readInt64(&error);

    if (error) {
        connection->reconnect();
        return;
    }

    Datacenter *datacenter = connection->getDatacenter();

    if (connectionState != ConnectionStateConnected && connection->getConnectionType() == ConnectionTypeGeneric && datacenter->getDatacenterId() == currentDatacenterId) {
        connectionState = ConnectionStateConnected;
        if (delegate != nullptr) {
            delegate->onConnectionStateChanged(connectionState, instanceNum);
        }
    }

    if (keyId == 0) {
        int64_t messageId = data->readInt64(&error);
        if (error) {
            connection->reconnect();
            return;
        }

        if (connection->isMessageIdProcessed(messageId)) {
            return;
        }

        uint32_t messageLength = data->readUint32(&error);
        if (error) {
            connection->reconnect();
            return;
        }

        if (!connection->allowsCustomPadding()) {
            if (messageLength != data->remaining()) {
                DEBUG_E("connection(%p) received incorrect message length", connection);
                connection->reconnect();
                return;
            }
        }

        TLObject *request;
        if (datacenter->isHandshaking(connection->isMediaConnection)) {
            request = datacenter->getCurrentHandshakeRequest(connection->isMediaConnection);
        } else {
            request = getRequestWithMessageId(messageId);
        }

        deserializingDatacenter = datacenter;
        TLObject *object = TLdeserialize(request, messageLength, data);

        if (object != nullptr) {
            if (datacenter->isHandshaking(connection->isMediaConnection)) {
                datacenter->processHandshakeResponse(connection->isMediaConnection, object, messageId);
            } else {
                processServerResponse(object, messageId, 0, 0, connection, 0, 0);
                connection->addProcessedMessageId(messageId);
            }
            connection->setHasUsefullData();
            delete object;
        }
    } else {
        if (connection->allowsCustomPadding()) {
            uint32_t padding = (length - 24) % 16;
            if (padding != 0) {
                length -= padding;
            }
        }
        if (length < 24 + 32 || !connection->allowsCustomPadding() && (length - 24) % 16 != 0 || !datacenter->decryptServerResponse(keyId, data->bytes() + mark + 8, data->bytes() + mark + 24, length - 24, connection)) {
            DEBUG_E("connection(%p) unable to decrypt server response", connection);
            connection->reconnect();
            return;
        }
        data->position(mark + 24);

        int64_t messageServerSalt = data->readInt64(&error);
        int64_t messageSessionId = data->readInt64(&error);

        if (messageSessionId != connection->getSessionId()) {
            DEBUG_E("connection(%p) received invalid message session id (0x%" PRIx64 " instead of 0x%" PRIx64 ")", connection, (uint64_t) messageSessionId, (uint64_t) connection->getSessionId());
            return;
        }

        bool doNotProcess = false;

        int64_t messageId = data->readInt64(&error);
        int32_t messageSeqNo = data->readInt32(&error);
        uint32_t messageLength = data->readUint32(&error);

        if (connection->isMessageIdProcessed(messageId)) {
            doNotProcess = true;
        }

        if (messageSeqNo % 2 != 0) {
            connection->addMessageToConfirm(messageId);
        }

        if (!doNotProcess) {
            deserializingDatacenter = datacenter;
            TLObject *object = TLdeserialize(nullptr, messageLength, data);
            if (object != nullptr) {
                connection->setHasUsefullData();
                DEBUG_D("connection(%p, account%u, dc%u, type %d) received object %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
                processServerResponse(object, messageId, messageSeqNo, messageServerSalt, connection, 0, 0);
                connection->addProcessedMessageId(messageId);
                delete object;
                if (connection->getConnectionType() == ConnectionTypePush) {
                    std::vector<std::unique_ptr<NetworkMessage>> messages;
                    sendMessagesToConnectionWithConfirmation(messages, connection, false);
                }
            } else {
                if (delegate != nullptr) {
                    delegate->onUnparsedMessageReceived(0, data, connection->getConnectionType(), instanceNum);
                }
            }
        } else {
            std::vector<std::unique_ptr<NetworkMessage>> messages;
            sendMessagesToConnectionWithConfirmation(messages, connection, false);
        }
    }
}

TLObject *ConnectionsManager::getRequestWithMessageId(int64_t messageId) {
    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
        Request *request = iter->get();
        if (request->messageId == messageId) {
            return request->rawRequest;
        }
    }
    return nullptr;
}

TLObject *ConnectionsManager::TLdeserialize(TLObject *request, uint32_t bytes, NativeByteBuffer *data) {
    bool error = false;
    uint32_t position = data->position();
    uint32_t constructor = data->readUint32(&error);
    if (error) {
        data->position(position);
        return nullptr;
    }

    TLObject *object = TLClassStore::TLdeserialize(data, bytes, constructor, instanceNum, error);

    if (error) {
        if (object != nullptr) {
            delete object;
        }
        data->position(position);
        return nullptr;
    }

    if (object == nullptr) {
        if (request != nullptr) {
            TL_api_request *apiRequest = dynamic_cast<TL_api_request *>(request);
            if (apiRequest != nullptr) {
                object = apiRequest->deserializeResponse(data, bytes, error);
                DEBUG_D("api request constructor 0x%x, don't parse", constructor);
            } else {
                object = request->deserializeResponse(data, constructor, instanceNum, error);
                if (object != nullptr && error) {
                    delete object;
                    object = nullptr;
                }
            }
        } else {
            DEBUG_D("not found request to parse constructor 0x%x", constructor);
        }
    }
    if (object == nullptr) {
        data->position(position);
    }
    return object;
}

void ConnectionsManager::processServerResponse(TLObject *message, int64_t messageId, int32_t messageSeqNo, int64_t messageSalt, Connection *connection, int64_t innerMsgId, int64_t containerMessageId) {
    const std::type_info &typeInfo = typeid(*message);

    Datacenter *datacenter = connection->getDatacenter();

    if (typeInfo == typeid(TL_new_session_created)) {
        TL_new_session_created *response = (TL_new_session_created *) message;

        if (!connection->isSessionProcessed(response->unique_id)) {
            DEBUG_D("connection(%p, account%u, dc%u, type %d) new session created (first message id: 0x%" PRIx64 ", server salt: 0x%" PRIx64 ", unique id: 0x%" PRIx64 ")", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), (uint64_t) response->first_msg_id, (uint64_t) response->server_salt, (uint64_t) response->unique_id);

            std::unique_ptr<TL_future_salt> salt = std::unique_ptr<TL_future_salt>(new TL_future_salt());
            salt->valid_until = salt->valid_since = getCurrentTime();
            salt->valid_until += 30 * 60;
            salt->salt = response->server_salt;
            datacenter->addServerSalt(salt);

            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
                if (request->messageId < response->first_msg_id && request->connectionType & connection->getConnectionType() && requestDatacenter != nullptr && requestDatacenter->getDatacenterId() == datacenter->getDatacenterId()) {
                    DEBUG_D("clear request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
                    request->clear(true);
                }
            }

            saveConfig();

            if (datacenter->getDatacenterId() == currentDatacenterId && currentUserId) {
                if (connection->getConnectionType() == ConnectionTypePush) {
                    registerForInternalPushUpdates();
                } else if (connection->getConnectionType() == ConnectionTypeGeneric) {
                    if (delegate != nullptr) {
                        delegate->onSessionCreated(instanceNum);
                    }
                }
            }
            connection->addProcessedSession(response->unique_id);
        }
    } else if (typeInfo == typeid(TL_msg_container)) {
        TL_msg_container *response = (TL_msg_container *) message;
        size_t count = response->messages.size();
        for (uint32_t a = 0; a < count; a++) {
            TL_message *innerMessage = response->messages[a].get();
            int64_t innerMessageId = innerMessage->msg_id;
            if (innerMessage->seqno % 2 != 0) {
                connection->addMessageToConfirm(innerMessageId);
            }
            if (connection->isMessageIdProcessed(innerMessageId)) {
                continue;
            }
            if (innerMessage->unparsedBody != nullptr) {
                if (delegate != nullptr) {
                    delegate->onUnparsedMessageReceived(0, innerMessage->unparsedBody.get(), connection->getConnectionType(), instanceNum);
                }
            } else {
                processServerResponse(innerMessage->body.get(), 0, innerMessage->seqno, messageSalt, connection, innerMessageId, messageId);
            }
            connection->addProcessedMessageId(innerMessageId);
        }
    } else if (typeInfo == typeid(TL_pong)) {
        if (connection->getConnectionType() == ConnectionTypePush) {
            if (!registeredForInternalPush) {
                registerForInternalPushUpdates();
            }
            DEBUG_D("connection(%p, account%u, dc%u, type %d) received push ping", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType());
            sendingPushPing = false;
        } else {
            TL_pong *response = (TL_pong *) message;
            if (response->ping_id >= 2000000) {
                for (std::vector<std::unique_ptr<ProxyCheckInfo>>::iterator iter = proxyActiveChecks.begin(); iter != proxyActiveChecks.end(); iter++) {
                    ProxyCheckInfo *proxyCheckInfo = iter->get();
                    if (proxyCheckInfo->pingId == response->ping_id) {
                        for (requestsIter iter2 = runningRequests.begin(); iter2 != runningRequests.end(); iter2++) {
                            Request *request = iter2->get();
                            if (request->requestToken == proxyCheckInfo->requestToken) {
                                int64_t ping = llabs(getCurrentTimeMonotonicMillis() - request->startTimeMillis);
                                DEBUG_D("got ping response for request %p, %" PRId64, request->rawRequest, ping);
                                request->completed = true;
                                proxyCheckInfo->onRequestTime(ping);
                                runningRequests.erase(iter2);
                                break;
                            }
                        }
                        proxyActiveChecks.erase(iter);

                        if (!proxyCheckQueue.empty()) {
                            proxyCheckInfo = proxyCheckQueue[0].release();
                            proxyCheckQueue.erase(proxyCheckQueue.begin());
                            checkProxyInternal(proxyCheckInfo);
                        }
                        break;
                    }
                }
            } else if (response->ping_id == lastPingId) {
                int32_t diff = (int32_t) (getCurrentTimeMonotonicMillis() / 1000) - pingTime;

                if (abs(diff) < 10) {
                    currentPingTime = (diff + currentPingTime) / 2;
                    if (messageId != 0) {
                        int64_t timeMessage = (int64_t) (messageId / 4294967296.0 * 1000);
                        timeDifference = (int32_t) ((timeMessage - getCurrentTimeMillis()) / 1000 - currentPingTime / 2);
                    }
                }
            }
        }
    } else if (typeInfo == typeid(TL_future_salts)) {
        TL_future_salts *response = (TL_future_salts *) message;
        int64_t requestMid = response->req_msg_id;
        for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
            Request *request = iter->get();
            if (request->respondsToMessageId(requestMid)) {
                request->onComplete(response, nullptr, connection->currentNetworkType);
                request->completed = true;
                runningRequests.erase(iter);
                break;
            }
        }
    } else if (dynamic_cast<DestroySessionRes *>(message)) {
        DestroySessionRes *response = (DestroySessionRes *) message;
        DEBUG_D("destroyed session 0x%" PRIx64 " (%s)", (uint64_t) response->session_id, typeInfo == typeid(TL_destroy_session_ok) ? "ok" : "not found");
    } else if (typeInfo == typeid(TL_rpc_result)) {
        TL_rpc_result *response = (TL_rpc_result *) message;
        int64_t resultMid = response->req_msg_id;

        bool hasResult = response->result.get() != nullptr;
        bool ignoreResult = false;
        if (hasResult) {
            TLObject *object = response->result.get();
            DEBUG_D("connection(%p, account%u, dc%u, type %d) received rpc_result with %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
        }
        RpcError *error = hasResult ? dynamic_cast<RpcError *>(response->result.get()) : nullptr;
        if (error != nullptr) {
            DEBUG_E("connection(%p, account%u, dc%u, type %d) rpc error %d: %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), error->error_code, error->error_message.c_str());
            if (error->error_code == 303) {
                uint32_t migrateToDatacenterId = DEFAULT_DATACENTER_ID;

                static std::vector<std::string> migrateErrors = {"NETWORK_MIGRATE_", "PHONE_MIGRATE_", "USER_MIGRATE_"};

                size_t count = migrateErrors.size();
                for (uint32_t a = 0; a < count; a++) {
                    std::string &possibleError = migrateErrors[a];
                    if (error->error_message.find(possibleError) != std::string::npos) {
                        std::string num = error->error_message.substr(possibleError.size(), error->error_message.size() - possibleError.size());
                        uint32_t val = (uint32_t) atoi(num.c_str());
                        migrateToDatacenterId = val;
                    }
                }

                if (migrateToDatacenterId != DEFAULT_DATACENTER_ID) {
                    ignoreResult = true;
                    moveToDatacenter(migrateToDatacenterId);
                }
            }
        }

        uint32_t retryRequestsFromDatacenter = DEFAULT_DATACENTER_ID - 1;
        uint32_t retryRequestsConnections = 0;

        if (!ignoreResult) {
            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if (request->respondsToMessageId(resultMid)) {
                    DEBUG_D("got response for request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
                    bool discardResponse = false;
                    bool isError = false;
                    bool allowInitConnection = true;

                    if (request->onCompleteRequestCallback != nullptr) {
                        TL_error *implicitError = nullptr;
                        NativeByteBuffer *unpacked_data = nullptr;
                        TLObject *result = response->result.get();
                        if (typeid(*result) == typeid(TL_gzip_packed)) {
                            TL_gzip_packed *innerResponse = (TL_gzip_packed *) result;
                            unpacked_data = decompressGZip(innerResponse->packed_data.get());
                            TLObject *object = TLdeserialize(request->rawRequest, unpacked_data->limit(), unpacked_data);
                            if (object != nullptr) {
                                response->result = std::unique_ptr<TLObject>(object);
                            } else {
                                response->result = std::unique_ptr<TLObject>(nullptr);
                            }
                        }

                        hasResult = response->result.get() != nullptr;
                        error = hasResult ? dynamic_cast<RpcError *>(response->result.get()) : nullptr;
                        TL_error *error2 = hasResult ? dynamic_cast<TL_error *>(response->result.get()) : nullptr;
                        if (error != nullptr) {
                            allowInitConnection = false;
                            static std::string authRestart = "AUTH_RESTART";
                            static std::string authKeyPermEmpty = "AUTH_KEY_PERM_EMPTY";
                            bool processEvenFailed = error->error_code == 500 && error->error_message.find(authRestart) != std::string::npos;
                            DEBUG_E("request %p rpc error %d: %s", request, error->error_code, error->error_message.c_str());

                            if (error->error_code == 401 && error->error_message.find(authKeyPermEmpty) != std::string::npos) {
                                discardResponse = true;
                                request->minStartTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000 + 1);
                                request->startTime = 0;

                                if (!datacenter->isHandshaking(connection->isMediaConnection)) {
                                    datacenter->clearAuthKey(connection->isMediaConnection ? HandshakeTypeMediaTemp : HandshakeTypeTemp);
                                    saveConfig();
                                    datacenter->beginHandshake(connection->isMediaConnection ? HandshakeTypeMediaTemp : HandshakeTypeTemp, false);
                                }
                            } else if ((request->requestFlags & RequestFlagFailOnServerErrors) == 0 || processEvenFailed) {
                                if (error->error_code == 500 || error->error_code < 0) {
                                    discardResponse = true;
                                    request->minStartTime = request->startTime + (request->serverFailureCount > 10 ? 10 : request->serverFailureCount);
                                    request->serverFailureCount++;
                                } else if (error->error_code == 420) {
                                    int32_t waitTime = 2;
                                    static std::string floodWait = "FLOOD_WAIT_";
                                    if (error->error_message.find(floodWait) != std::string::npos) {
                                        std::string num = error->error_message.substr(floodWait.size(), error->error_message.size() - floodWait.size());
                                        waitTime = atoi(num.c_str());
                                        if (waitTime <= 0) {
                                            waitTime = 2;
                                        }
                                    }

                                    discardResponse = true;
                                    request->failedByFloodWait = waitTime;
                                    request->startTime = 0;
                                    request->startTimeMillis = 0;
                                    request->minStartTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000 + waitTime);
                                } else if (error->error_code == 400) {
                                    static std::string waitFailed = "MSG_WAIT_FAILED";
                                    if (error->error_message.find(waitFailed) != std::string::npos) {
                                        discardResponse = true;
                                        request->minStartTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000 + 1);
                                        request->startTime = 0;
                                        request->startTimeMillis = 0;
                                    }
                                }
                            }
                            if (!discardResponse) {
                                implicitError = new TL_error();
                                implicitError->code = error->error_code;
                                implicitError->text = error->error_message;
                            }
                        } else if (error2 == nullptr) {
                            if (request->rawRequest == nullptr || response->result == nullptr) {
                                allowInitConnection = false;
                                DEBUG_E("rawRequest is null");
                                implicitError = new TL_error();
                                implicitError->code = -1000;
                                implicitError->text = "";
                            }
                        }

                        if (!discardResponse) {
                            if (implicitError != nullptr || error2 != nullptr) {
                                isError = true;
                                request->onComplete(nullptr, implicitError != nullptr ? implicitError : error2, connection->currentNetworkType);
                                if (error2 != nullptr) {
                                    delete error2;
                                }
                            } else {
                                request->onComplete(response->result.get(), nullptr, connection->currentNetworkType);
                            }
                        }

                        if (implicitError != nullptr) {
                            if (implicitError->code == 401) {
                                allowInitConnection = false;
                                isError = true;
                                static std::string sessionPasswordNeeded = "SESSION_PASSWORD_NEEDED";

                                if (implicitError->text.find(sessionPasswordNeeded) != std::string::npos) {
                                    //ignore this error
                                } else if (datacenter->getDatacenterId() == currentDatacenterId || datacenter->getDatacenterId() == movingToDatacenterId) {
                                    if (request->connectionType & ConnectionTypeGeneric && currentUserId) {
                                        currentUserId = 0;
                                        if (delegate != nullptr) {
                                            delegate->onLogout(instanceNum);
                                        }
                                        cleanUp(false);
                                    }
                                } else {
                                    datacenter->authorized = false;
                                    saveConfig();
                                    discardResponse = true;
                                    if (request->connectionType & ConnectionTypeDownload || request->connectionType & ConnectionTypeUpload) {
                                        retryRequestsFromDatacenter = datacenter->datacenterId;
                                        retryRequestsConnections = request->connectionType;
                                    }
                                }
                            } else if (currentUserId == 0 && implicitError->code == 406) {
                                static std::string authKeyDuplicated = "AUTH_KEY_DUPLICATED";
                                if (implicitError->text.find(authKeyDuplicated) != std::string::npos) {
                                    cleanUp(true);
                                }
                            }
                        }

                        if (unpacked_data != nullptr) {
                            unpacked_data->reuse();
                        }
                        if (implicitError != nullptr) {
                            delete implicitError;
                        }
                    }

                    if (!discardResponse) {
                        if (allowInitConnection && !isError) {
                            bool save = false;
                            if (request->isInitRequest && datacenter->lastInitVersion != currentVersion) {
                                datacenter->lastInitVersion = currentVersion;
                                save = true;
                            } else if (request->isInitMediaRequest && datacenter->lastInitMediaVersion != currentVersion) {
                                datacenter->lastInitMediaVersion = currentVersion;
                                save = true;
                            }
                            if (save) {
                                saveConfig();
                                DEBUG_D("dc%d init connection completed", datacenter->getDatacenterId());
                            }
                        }
                        request->completed = true;
                        removeRequestFromGuid(request->requestToken);
                        runningRequests.erase(iter);
                    } else {
                        request->messageId = 0;
                        request->messageSeqNo = 0;
                        request->connectionToken = 0;
                    }
                    break;
                }
            }
        }

        if (retryRequestsFromDatacenter != DEFAULT_DATACENTER_ID - 1) {
            processRequestQueue(retryRequestsConnections, retryRequestsFromDatacenter);
        } else {
            processRequestQueue(0, 0);
        }
    } else if (typeInfo == typeid(TL_msgs_ack)) {

    } else if (typeInfo == typeid(TL_bad_msg_notification)) {
        TL_bad_msg_notification *result = (TL_bad_msg_notification *) message;
        DEBUG_E("bad message notification %d for messageId 0x%" PRIx64 ", seqno %d", result->error_code, result->bad_msg_id, result->bad_msg_seqno);
        switch (result->error_code) {
            case 16:
            case 17:
            case 19:
            case 32:
            case 33:
            case 64: {
                int64_t realId = messageId != 0 ? messageId : containerMessageId;
                if (realId == 0) {
                    realId = innerMsgId;
                }

                if (realId != 0) {
                    int64_t time = (int64_t) (messageId / 4294967296.0 * 1000);
                    int64_t currentTime = getCurrentTimeMillis();
                    timeDifference = (int32_t) ((time - currentTime) / 1000 - currentPingTime / 2);
                }

                datacenter->recreateSessions(HandshakeTypeAll);
                saveConfig();

                lastOutgoingMessageId = 0;
                clearRequestsForDatacenter(datacenter, HandshakeTypeAll);
                break;
            }
            case 20: {
                for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                    Request *request = iter->get();
                    if (request->respondsToMessageId(result->bad_msg_id)) {
                        if (request->completed) {
                            break;
                        }
                        connection->addMessageToConfirm(result->bad_msg_id);
                        request->clear(true);
                        break;
                    }
                }
            }
            default:
                break;
        }
    } else if (typeInfo == typeid(TL_bad_server_salt)) {
        TL_bad_server_salt *response = (TL_bad_server_salt *) message;
        if (messageId != 0) {
            int64_t time = (int64_t) (messageId / 4294967296.0 * 1000);
            int64_t currentTime = getCurrentTimeMillis();
            timeDifference = (int32_t) ((time - currentTime) / 1000 - currentPingTime / 2);
            lastOutgoingMessageId = messageId > (lastOutgoingMessageId ? messageId : lastOutgoingMessageId);
        }
        int64_t resultMid = response->bad_msg_id;
        if (resultMid != 0) {
            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if ((request->connectionType & ConnectionTypeDownload) == 0) {
                    continue;
                }
                Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
                if (requestDatacenter != nullptr && requestDatacenter->getDatacenterId() == datacenter->getDatacenterId()) {
                    request->retryCount = 0;
                    request->failedBySalt = true;
                }
            }
        }

        datacenter->clearServerSalts();

        std::unique_ptr<TL_future_salt> salt = std::unique_ptr<TL_future_salt>(new TL_future_salt());
        salt->valid_until = salt->valid_since = getCurrentTime();
        salt->valid_until += 30 * 60;
        salt->salt = messageSalt;
        datacenter->addServerSalt(salt);
        saveConfig();

        requestSaltsForDatacenter(datacenter);
        if (datacenter->hasAuthKey(ConnectionTypeGeneric, 1)) {
            processRequestQueue(AllConnectionTypes, datacenter->getDatacenterId());
        }
    } else if (typeInfo == typeid(MsgsStateInfo)) {
        MsgsStateInfo *response = (MsgsStateInfo *) message;
        DEBUG_D("connection(%p, account%u, dc%u, type %d) got %s for messageId 0x%" PRIx64, connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeInfo.name(), response->req_msg_id);

        std::map<int64_t, int64_t>::iterator mIter = resendRequests.find(response->req_msg_id);
        if (mIter != resendRequests.end()) {
            DEBUG_D("found resend for messageId 0x%" PRIx64, mIter->second);
            connection->addMessageToConfirm(mIter->second);
            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if (request->respondsToMessageId(mIter->second)) {
                    if (request->completed) {
                        break;
                    }
                    request->clear(true);
                    break;
                }
            }
            resendRequests.erase(mIter);
        }
    } else if (dynamic_cast<MsgDetailedInfo *>(message)) {
        MsgDetailedInfo *response = (MsgDetailedInfo *) message;

        bool requestResend = false;
        bool confirm = true;

        DEBUG_D("connection(%p, account%u, dc%u, type %d) got %s for messageId 0x%" PRIx64, connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeInfo.name(), response->msg_id);
        if (typeInfo == typeid(TL_msg_detailed_info)) {
            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if (request->respondsToMessageId(response->msg_id)) {
                    if (request->completed) {
                        break;
                    }
                    DEBUG_D("got TL_msg_detailed_info for rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
                    int32_t currentTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000);
                    if (request->lastResendTime == 0 || abs(currentTime - request->lastResendTime) >= 60) {
                        request->lastResendTime = currentTime;
                        requestResend = true;
                    } else {
                        confirm = false;
                    }
                    break;
                }
            }
        } else {
            if (!connection->isMessageIdProcessed(messageId)) {
                requestResend = true;
            }
        }

        if (requestResend) {
            TL_msg_resend_req *request = new TL_msg_resend_req();
            request->msg_ids.push_back(response->answer_msg_id);
            NetworkMessage *networkMessage = new NetworkMessage();
            networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
            networkMessage->message->msg_id = generateMessageId();
            networkMessage->message->bytes = request->getObjectSize();
            networkMessage->message->body = std::unique_ptr<TLObject>(request);
            networkMessage->message->seqno = connection->generateMessageSeqNo(false);
            resendRequests[networkMessage->message->msg_id] = response->answer_msg_id;

            std::vector<std::unique_ptr<NetworkMessage>> array;
            array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));

            sendMessagesToConnection(array, connection, false);
        } else if (confirm) {
            connection->addMessageToConfirm(response->answer_msg_id);
        }
    } else if (typeInfo == typeid(TL_gzip_packed)) {
        TL_gzip_packed *response = (TL_gzip_packed *) message;
        NativeByteBuffer *data = decompressGZip(response->packed_data.get());
        TLObject *object = TLdeserialize(getRequestWithMessageId(messageId), data->limit(), data);
        if (object != nullptr) {
            DEBUG_D("connection(%p, account%u, dc%u, type %d) received object %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
            processServerResponse(object, messageId, messageSeqNo, messageSalt, connection, innerMsgId, containerMessageId);
            delete object;
        } else {
            if (delegate != nullptr) {
                delegate->onUnparsedMessageReceived(messageId, data, connection->getConnectionType(), instanceNum);
            }
        }
        data->reuse();
    } else if (typeInfo == typeid(TL_updatesTooLong)) {
        if (connection->connectionType == ConnectionTypePush) {
            if (networkPaused) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
                DEBUG_D("received internal push: wakeup network in background");
            } else if (lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
                DEBUG_D("received internal push: reset sleep timeout");
            } else {
                DEBUG_D("received internal push");
            }
            if (delegate != nullptr) {
                delegate->onInternalPushReceived(instanceNum);
            }
        } else {
            if (delegate != nullptr) {
                NativeByteBuffer *data = BuffersStorage::getInstance().getFreeBuffer(message->getObjectSize());
                message->serializeToStream(data);
                data->position(0);
                delegate->onUnparsedMessageReceived(0, data, connection->getConnectionType(), instanceNum);
                data->reuse();
            }
        }
    }
}

void ConnectionsManager::sendPing(Datacenter *datacenter, bool usePushConnection) {
    if (usePushConnection && (currentUserId == 0 || !usePushConnection)) {
        return;
    }
    Connection *connection = nullptr;
    if (usePushConnection) {
        connection = datacenter->getPushConnection(true);
    } else {
        connection = datacenter->getGenericConnection(true, 0);
    }
    if (connection == nullptr || (!usePushConnection && connection->getConnectionToken() == 0)) {
        return;
    }
    TL_ping_delay_disconnect *request = new TL_ping_delay_disconnect();
    request->ping_id = ++lastPingId;
    if (usePushConnection) {
        request->disconnect_delay = 60 * 7;
    } else {
        request->disconnect_delay = 35;
        pingTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000);
    }

    NetworkMessage *networkMessage = new NetworkMessage();
    networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
    networkMessage->message->msg_id = generateMessageId();
    networkMessage->message->bytes = request->getObjectSize();
    networkMessage->message->body = std::unique_ptr<TLObject>(request);
    networkMessage->message->seqno = connection->generateMessageSeqNo(false);

    std::vector<std::unique_ptr<NetworkMessage>> array;
    array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    NativeByteBuffer *transportData = datacenter->createRequestsData(array, nullptr, connection, false);
    if (usePushConnection) {
        DEBUG_D("dc%d send ping to push connection", datacenter->getDatacenterId());
        sendingPushPing = true;
    }
    connection->sendData(transportData, false, true);
}

bool ConnectionsManager::isIpv6Enabled() {
    return ipv6Enabled;
}

void ConnectionsManager::initDatacenters() {
    Datacenter *datacenter;
    if (!testBackend) {
        if (datacenters.find(1) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 1);
            datacenter->addAddressAndPort("149.154.175.50", 443, 0, "");
            datacenter->addAddressAndPort("2001:b28:f23d:f001:0000:0000:0000:000a", 443, 1, "");
            datacenters[1] = datacenter;
        }

        if (datacenters.find(2) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 2);
            datacenter->addAddressAndPort("149.154.167.51", 443, 0, "");
            datacenter->addAddressAndPort("2001:67c:4e8:f002:0000:0000:0000:000a", 443, 1, "");
            datacenters[2] = datacenter;
        }

        if (datacenters.find(3) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 3);
            datacenter->addAddressAndPort("149.154.175.100", 443, 0, "");
            datacenter->addAddressAndPort("2001:b28:f23d:f003:0000:0000:0000:000a", 443, 1, "");
            datacenters[3] = datacenter;
        }

        if (datacenters.find(4) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 4);
            datacenter->addAddressAndPort("149.154.167.91", 443, 0, "");
            datacenter->addAddressAndPort("2001:67c:4e8:f004:0000:0000:0000:000a", 443, 1, "");
            datacenters[4] = datacenter;
        }

        if (datacenters.find(5) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 5);
            datacenter->addAddressAndPort("149.154.171.5", 443, 0, "");
            datacenter->addAddressAndPort("2001:b28:f23f:f005:0000:0000:0000:000a", 443, 1, "");
            datacenters[5] = datacenter;
        }
    } else {
        if (datacenters.find(1) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 1);
            datacenter->addAddressAndPort("149.154.175.40", 443, 0, "");
            datacenter->addAddressAndPort("2001:b28:f23d:f001:0000:0000:0000:000e", 443, 1, "");
            datacenters[1] = datacenter;
        }

        if (datacenters.find(2) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 2);
            datacenter->addAddressAndPort("149.154.167.40", 443, 0, "");
            datacenter->addAddressAndPort("2001:67c:4e8:f002:0000:0000:0000:000e", 443, 1, "");
            datacenters[2] = datacenter;
        }

        if (datacenters.find(3) == datacenters.end()) {
            datacenter = new Datacenter(instanceNum, 3);
            datacenter->addAddressAndPort("149.154.175.117", 443, 0, "");
            datacenter->addAddressAndPort("2001:b28:f23d:f003:0000:0000:0000:000e", 443, 1, "");
            datacenters[3] = datacenter;
        }
    }
}

void ConnectionsManager::attachConnection(ConnectionSocket *connection) {
    if (std::find(activeConnections.begin(), activeConnections.end(), connection) != activeConnections.end()) {
        return;
    }
    activeConnections.push_back(connection);
}

void ConnectionsManager::detachConnection(ConnectionSocket *connection) {
    std::vector<ConnectionSocket *>::iterator iter = std::find(activeConnections.begin(), activeConnections.end(), connection);
    if (iter != activeConnections.end()) {
        activeConnections.erase(iter);
    }
}

int32_t ConnectionsManager::sendRequestInternal(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate) {
    if (!currentUserId && !(flags & RequestFlagWithoutLogin)) {
        DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        return 0;
    }
    Request *request = new Request(instanceNum, lastRequestToken++, connetionType, flags, datacenterId, onComplete, onQuickAck, nullptr);
    request->rawRequest = object;
    request->rpcRequest = wrapInLayer(object, getDatacenterWithId(datacenterId), request);
    requestsQueue.push_back(std::unique_ptr<Request>(request));
    if (immediate) {
        processRequestQueue(0, 0);
    }
    return request->requestToken;
}

int32_t ConnectionsManager::sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate) {
    int32_t requestToken = lastRequestToken++;
    return sendRequest(object, onComplete, onQuickAck, flags, datacenterId, connetionType, immediate, requestToken);
}

int32_t ConnectionsManager::sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate, int32_t requestToken) {
    if (!currentUserId && !(flags & RequestFlagWithoutLogin)) {
        DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        return 0;
    }
    if (requestToken == 0) {
        requestToken = lastRequestToken++;
    }
    scheduleTask([&, requestToken, object, onComplete, onQuickAck, flags, datacenterId, connetionType, immediate] {
        Request *request = new Request(instanceNum, requestToken, connetionType, flags, datacenterId, onComplete, onQuickAck, nullptr);
        request->rawRequest = object;
        request->rpcRequest = wrapInLayer(object, getDatacenterWithId(datacenterId), request);
        requestsQueue.push_back(std::unique_ptr<Request>(request));
        if (immediate) {
            processRequestQueue(0, 0);
        }
    });
    return requestToken;
}

#ifdef ANDROID
void ConnectionsManager::sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, onWriteToSocketFunc onWriteToSocket, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate, int32_t requestToken, jobject ptr1, jobject ptr2, jobject ptr3) {
    if (!currentUserId && !(flags & RequestFlagWithoutLogin)) {
        DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        JNIEnv *env = 0;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		    DEBUG_E("can't get jnienv");
            exit(1);
	    }
        if (ptr1 != nullptr) {
            env->DeleteGlobalRef(ptr1);
            ptr1 = nullptr;
        }
        if (ptr2 != nullptr) {
            env->DeleteGlobalRef(ptr2);
            ptr2 = nullptr;
        }
        if (ptr3 != nullptr) {
            env->DeleteGlobalRef(ptr3);
            ptr3 = nullptr;
        }
        return;
    }
    scheduleTask([&, requestToken, object, onComplete, onQuickAck, onWriteToSocket, flags, datacenterId, connetionType, immediate, ptr1, ptr2, ptr3] {
        DEBUG_D("send request %p - %s", object, typeid(*object).name());
        Request *request = new Request(instanceNum, requestToken, connetionType, flags, datacenterId, onComplete, onQuickAck, onWriteToSocket);
        request->rawRequest = object;
        request->ptr1 = ptr1;
        request->ptr2 = ptr2;
        request->ptr3 = ptr3;
        request->rpcRequest = wrapInLayer(object, getDatacenterWithId(datacenterId), request);
        DEBUG_D("send request wrapped %p - %s", request->rpcRequest.get(), typeid(*(request->rpcRequest.get())).name());
        requestsQueue.push_back(std::unique_ptr<Request>(request));
        if (immediate) {
            processRequestQueue(0, 0);
        }
    });
}
#endif

void ConnectionsManager::cancelRequestsForGuid(int32_t guid) {
    scheduleTask([&, guid] {
        std::map<int32_t, std::vector<int32_t>>::iterator iter = requestsByGuids.find(guid);
        if (iter != requestsByGuids.end()) {
            std::vector<int32_t> &requests = iter->second;
            size_t count = requests.size();
            for (uint32_t a = 0; a < count; a++) {
                cancelRequestInternal(requests[a], 0, true, false);
                std::map<int32_t, int32_t>::iterator iter2 = guidsByRequests.find(requests[a]);
                if (iter2 != guidsByRequests.end()) {
                    guidsByRequests.erase(iter2);
                }
            }
            requestsByGuids.erase(iter);
        }
    });
}

void ConnectionsManager::bindRequestToGuid(int32_t requestToken, int32_t guid) {
    scheduleTask([&, requestToken, guid] {
        std::map<int32_t, std::vector<int32_t>>::iterator iter = requestsByGuids.find(guid);
        if (iter != requestsByGuids.end()) {
            iter->second.push_back(requestToken);
        } else {
            std::vector<int32_t> array;
            array.push_back(requestToken);
            requestsByGuids[guid] = array;
        }
        guidsByRequests[requestToken] = guid;
    });
}

void ConnectionsManager::setUserId(int32_t userId) {
    scheduleTask([&, userId] {
        int32_t oldUserId = currentUserId;
        currentUserId = userId;
        if (oldUserId == userId && userId != 0) {
            registerForInternalPushUpdates();
        }
        if (currentUserId != userId && userId != 0) {
            updateDcSettings(0, false);
        }
        if (currentUserId != 0 && pushConnectionEnabled) {
            Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
            if (datacenter != nullptr) {
                datacenter->createPushConnection()->setSessionId(pushSessionId);
                sendPing(datacenter, true);
            }
        }
    });
}

void ConnectionsManager::switchBackend() {
    scheduleTask([&] {
        currentDatacenterId = 1;
        testBackend = !testBackend;
        datacenters.clear();
        initDatacenters();
        saveConfig();
        exit(1);
    });
}

void ConnectionsManager::removeRequestFromGuid(int32_t requestToken) {
    std::map<int32_t, int32_t>::iterator iter2 = guidsByRequests.find(requestToken);
    if (iter2 != guidsByRequests.end()) {
        std::map<int32_t, std::vector<int32_t>>::iterator iter = requestsByGuids.find(iter2->first);
        if (iter != requestsByGuids.end()) {
            std::vector<int32_t>::iterator iter3 = std::find(iter->second.begin(), iter->second.end(), iter->first);
            if (iter3 != iter->second.end()) {
                iter->second.erase(iter3);
                if (iter->second.empty()) {
                    requestsByGuids.erase(iter);
                }
            }
        }
        guidsByRequests.erase(iter2);
    }
}

bool ConnectionsManager::cancelRequestInternal(int32_t token, int64_t messageId, bool notifyServer, bool removeFromClass) {
    for (requestsIter iter = requestsQueue.begin(); iter != requestsQueue.end(); iter++) {
        Request *request = iter->get();
        if (token != 0 && request->requestToken == token || messageId != 0 && request->respondsToMessageId(messageId)) {
            request->cancelled = true;
            DEBUG_D("cancelled queued rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
            requestsQueue.erase(iter);
            if (removeFromClass) {
                removeRequestFromGuid(token);
            }
            return true;
        }
    }

    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
        Request *request = iter->get();
        if (token != 0 && request->requestToken == token || messageId != 0 && request->respondsToMessageId(messageId)) {
            if (notifyServer) {
                TL_rpc_drop_answer *dropAnswer = new TL_rpc_drop_answer();
                dropAnswer->req_msg_id = request->messageId;
                sendRequest(dropAnswer, nullptr, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin | RequestFlagFailOnServerErrors, request->datacenterId, request->connectionType, true);
            }
            request->cancelled = true;
            DEBUG_D("cancelled running rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
            runningRequests.erase(iter);
            if (removeFromClass) {
                removeRequestFromGuid(token);
            }
            return true;
        }
    }
    return false;
}

void ConnectionsManager::cancelRequest(int32_t token, bool notifyServer) {
    if (token == 0) {
        return;
    }
    scheduleTask([&, token, notifyServer] {
        cancelRequestInternal(token, 0, notifyServer, true);
    });
}

void ConnectionsManager::onDatacenterHandshakeComplete(Datacenter *datacenter, HandshakeType type, int32_t timeDiff) {
    saveConfig();
    uint32_t datacenterId = datacenter->getDatacenterId();
    if (datacenterId == currentDatacenterId || datacenterId == movingToDatacenterId) {
        timeDifference = timeDiff;
        datacenter->recreateSessions(type);
        clearRequestsForDatacenter(datacenter, type);
    }
    processRequestQueue(AllConnectionTypes, datacenterId);
    if (type == HandshakeTypeTemp && !proxyCheckQueue.empty()) {
        ProxyCheckInfo *proxyCheckInfo = proxyCheckQueue[0].release();
        proxyCheckQueue.erase(proxyCheckQueue.begin());
        checkProxyInternal(proxyCheckInfo);
    }
}

void ConnectionsManager::onDatacenterExportAuthorizationComplete(Datacenter *datacenter) {
    saveConfig();
    scheduleTask([&, datacenter] {
        processRequestQueue(AllConnectionTypes, datacenter->getDatacenterId());
    });
}

void ConnectionsManager::sendMessagesToConnection(std::vector<std::unique_ptr<NetworkMessage>> &messages, Connection *connection, bool reportAck) {
    if (messages.empty() || connection == nullptr) {
        return;
    }

    std::vector<std::unique_ptr<NetworkMessage>> currentMessages;
    Datacenter *datacenter = connection->getDatacenter();

    uint32_t currentSize = 0;
    size_t count = messages.size();
    for (uint32_t a = 0; a < count; a++) {
        NetworkMessage *networkMessage = messages[a].get();
        currentMessages.push_back(std::move(messages[a]));
        currentSize += networkMessage->message->bytes;

        if (currentSize >= 3 * 1024 || a == count - 1) {
            int32_t quickAckId = 0;
            NativeByteBuffer *transportData = datacenter->createRequestsData(currentMessages, reportAck ? &quickAckId : nullptr, connection, false);

            if (transportData != nullptr) {
                if (reportAck && quickAckId != 0) {
                    std::vector<int32_t> requestIds;

                    size_t count2 = currentMessages.size();
                    for (uint32_t b = 0; b < count2; b++) {
                        NetworkMessage *message = currentMessages[b].get();
                        if (message->requestId != 0) {
                            requestIds.push_back(message->requestId);
                        }
                    }

                    if (!requestIds.empty()) {
                        std::map<int32_t, std::vector<int32_t>>::iterator iter = quickAckIdToRequestIds.find(quickAckId);
                        if (iter == quickAckIdToRequestIds.end()) {
                            quickAckIdToRequestIds[quickAckId] = requestIds;
                        } else {
                            iter->second.insert(iter->second.end(), requestIds.begin(), requestIds.end());
                        }
                    }
                }

                connection->sendData(transportData, reportAck, true);
            } else {
                DEBUG_E("connection(%p) connection data is empty", connection);
            }

            currentSize = 0;
            currentMessages.clear();
        }
    }
}

void ConnectionsManager::sendMessagesToConnectionWithConfirmation(std::vector<std::unique_ptr<NetworkMessage>> &messages, Connection *connection, bool reportAck) {
    NetworkMessage *networkMessage = connection->generateConfirmationRequest();
    if (networkMessage != nullptr) {
        messages.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    }
    sendMessagesToConnection(messages, connection, reportAck);
}

void ConnectionsManager::requestSaltsForDatacenter(Datacenter *datacenter) {
    if (std::find(requestingSaltsForDc.begin(), requestingSaltsForDc.end(), datacenter->getDatacenterId()) != requestingSaltsForDc.end()) {
        return;
    }
    requestingSaltsForDc.push_back(datacenter->getDatacenterId());
    TL_get_future_salts *request = new TL_get_future_salts();
    request->num = 32;
    sendRequest(request, [&, datacenter](TLObject *response, TL_error *error, int32_t networkType) {
        std::vector<uint32_t>::iterator iter = std::find(requestingSaltsForDc.begin(), requestingSaltsForDc.end(), datacenter->getDatacenterId());
        if (iter != requestingSaltsForDc.end()) {
            requestingSaltsForDc.erase(iter);
        }
        if (error == nullptr) {
            TL_future_salts *res = (TL_future_salts *) response;
            datacenter->mergeServerSalts(res->salts);
            saveConfig();
        }
    }, nullptr, RequestFlagWithoutLogin | RequestFlagEnableUnauthorized | RequestFlagUseUnboundKey, datacenter->getDatacenterId(), ConnectionTypeGeneric, true);
}

void ConnectionsManager::clearRequestsForDatacenter(Datacenter *datacenter, HandshakeType type) {
    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
        Request *request = iter->get();
        Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
        if (requestDatacenter->getDatacenterId() != datacenter->getDatacenterId()) {
            continue;
        }
        if (type == HandshakeTypePerm || type == HandshakeTypeAll || type == HandshakeTypeMediaTemp && request->isMediaRequest() || type == HandshakeTypeTemp && !request->isMediaRequest()) {
            request->clear(true);
        }
    }
}

void ConnectionsManager::registerForInternalPushUpdates() {
    if (registeringForPush || !currentUserId) {
        return;
    }
    registeredForInternalPush = false;
    registeringForPush = true;
    TL_account_registerDevice *request = new TL_account_registerDevice();
    request->token_type = 7;
    request->token = to_string_uint64((uint64_t) pushSessionId);

    sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType) {
        if (error == nullptr) {
            registeredForInternalPush = true;
            DEBUG_D("registered for internal push");
        } else {
            registeredForInternalPush = false;
            DEBUG_E("unable to registering for internal push");
        }
        saveConfig();
        registeringForPush = false;
    }, nullptr, 0, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
}


inline void addMessageToDatacenter(uint32_t datacenterId, NetworkMessage *networkMessage, std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> &messagesToDatacenters) {
    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter = messagesToDatacenters.find(datacenterId);
    if (iter == messagesToDatacenters.end()) {
        std::vector<std::unique_ptr<NetworkMessage>> &array = messagesToDatacenters[datacenterId] = std::vector<std::unique_ptr<NetworkMessage>>();
        array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    } else {
        iter->second.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    }
}

void ConnectionsManager::processRequestQueue(uint32_t connectionTypes, uint32_t dc) {
    genericMessagesToDatacenters.clear();
    genericMediaMessagesToDatacenters.clear();
    tempMessagesToDatacenters.clear();
    unknownDatacenterIds.clear();
    neededDatacenters.clear();
    unauthorizedDatacenters.clear();
    downloadRunningRequestCount.clear();

    int64_t currentTimeMillis = getCurrentTimeMonotonicMillis();
    int32_t currentTime = (int32_t) (currentTimeMillis / 1000);
    uint32_t genericRunningRequestCount = 0;
    uint32_t uploadRunningRequestCount = 0;

    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end();) {
        Request *request = iter->get();
        const std::type_info &typeInfo = typeid(*request->rawRequest);

        uint32_t datacenterId = request->datacenterId;
        if (datacenterId == DEFAULT_DATACENTER_ID) {
            if (movingToDatacenterId != DEFAULT_DATACENTER_ID) {
                iter++;
                continue;
            }
            datacenterId = currentDatacenterId;
        }

        switch (request->connectionType & 0x0000ffff) {
            case ConnectionTypeGeneric:
                genericRunningRequestCount++;
                break;
            case ConnectionTypeDownload: {
                uint32_t currentCount;
                std::map<uint32_t, uint32_t>::iterator dcIter = downloadRunningRequestCount.find(datacenterId);
                if (dcIter != downloadRunningRequestCount.end()) {
                    currentCount = dcIter->second;
                } else {
                    currentCount = 0;
                }
                downloadRunningRequestCount[datacenterId] = currentCount + 1;
                break;
            }
            case ConnectionTypeUpload:
                uploadRunningRequestCount++;
                break;
            default:
                break;
        }

        if (request->requestFlags & RequestFlagTryDifferentDc) {
            int32_t requestStartTime = request->startTime;
            int32_t timeout = 30;
            if (updatingDcSettings && dynamic_cast<TL_help_getConfig *>(request->rawRequest)) {
                requestStartTime = updatingDcStartTime;
                updatingDcStartTime = currentTime;
                timeout = 60;
            }
            if (request->startTime != 0 && abs(currentTime - requestStartTime) >= timeout) {
                DEBUG_D("move %s to requestsQueue", typeid(*request->rawRequest).name());
                requestsQueue.push_back(std::move(*iter));
                iter = runningRequests.erase(iter);
                continue;
            }
        }
        int32_t canUseUnboundKey = 0;
        if ((request->requestFlags & RequestFlagUseUnboundKey) != 0) {
            canUseUnboundKey |= 1;
        }

        Datacenter *requestDatacenter = getDatacenterWithId(datacenterId);
        if (requestDatacenter == nullptr) {
            if (std::find(unknownDatacenterIds.begin(), unknownDatacenterIds.end(), datacenterId) == unknownDatacenterIds.end()) {
                unknownDatacenterIds.push_back(datacenterId);
            }
            iter++;
            continue;
        } else {
            if (requestDatacenter->isCdnDatacenter) {
                request->requestFlags |= RequestFlagEnableUnauthorized;
            }
            if (request->needInitRequest(requestDatacenter, currentVersion) && !request->hasInitFlag() && request->rawRequest->isNeedLayer()) {
                DEBUG_D("move %p - %s to requestsQueue because of initConnection", request->rawRequest, typeid(*request->rawRequest).name());
                requestsQueue.push_back(std::move(*iter));
                iter = runningRequests.erase(iter);
                continue;
            }

            if (!requestDatacenter->hasAuthKey(request->connectionType, canUseUnboundKey)) {
                std::pair<Datacenter *, ConnectionType> pair = std::make_pair(requestDatacenter, request->connectionType);
                if (std::find(neededDatacenters.begin(), neededDatacenters.end(), pair) == neededDatacenters.end()) {
                    neededDatacenters.push_back(pair);
                }
                iter++;
                continue;
            } else if (!(request->requestFlags & RequestFlagEnableUnauthorized) && !requestDatacenter->authorized && request->datacenterId != DEFAULT_DATACENTER_ID && request->datacenterId != currentDatacenterId) {
                if (std::find(unauthorizedDatacenters.begin(), unauthorizedDatacenters.end(), requestDatacenter) == unauthorizedDatacenters.end()) {
                    unauthorizedDatacenters.push_back(requestDatacenter);
                }
                iter++;
                continue;
            }
        }

        Connection *connection = requestDatacenter->getConnectionByType(request->connectionType, true, canUseUnboundKey);
        int32_t maxTimeout = request->connectionType & ConnectionTypeGeneric ? 8 : 30;
        if (!networkAvailable || connection->getConnectionToken() == 0) {
            iter++;
            continue;
        }

        uint32_t requestConnectionType = request->connectionType & 0x0000ffff;

        bool forceThisRequest = (connectionTypes & requestConnectionType) && requestDatacenter->getDatacenterId() == dc;

        if (typeInfo == typeid(TL_get_future_salts) || typeInfo == typeid(TL_destroy_session)) {
            if (request->messageId != 0) {
                request->addRespondMessageId(request->messageId);
            }
            request->clear(false);
            forceThisRequest = false;
        }

        if (forceThisRequest || (abs(currentTime - request->startTime) > maxTimeout &&
                                 (currentTime >= request->minStartTime ||
                                  (request->failedByFloodWait != 0 && (request->minStartTime - currentTime) > request->failedByFloodWait) ||
                                  (request->failedByFloodWait == 0 && abs(currentTime - request->minStartTime) >= 60))
                                 )
            ) {
            if (!forceThisRequest && request->connectionToken > 0) {
                if ((request->connectionType & ConnectionTypeGeneric || request->connectionType & ConnectionTypeTemp) && request->connectionToken == connection->getConnectionToken()) {
                    DEBUG_D("request token is valid, not retrying %s (%p)", typeInfo.name(), request->rawRequest);
                    iter++;
                    continue;
                } else {
                    if (connection->getConnectionToken() != 0 && request->connectionToken == connection->getConnectionToken()) {
                        DEBUG_D("request download token is valid, not retrying %s (%p)", typeInfo.name(), request->rawRequest);
                        iter++;
                        continue;
                    }
                }
            }

            if (request->connectionToken != 0 && request->connectionToken != connection->getConnectionToken()) {
                request->lastResendTime = 0;
            }

            request->retryCount++;

            if (!request->failedBySalt) {
                if (request->connectionType & ConnectionTypeDownload) {
                    uint32_t retryMax = 10;
                    if (!(request->requestFlags & RequestFlagForceDownload)) {
                        if (request->failedByFloodWait) {
                            retryMax = 1;
                        } else {
                            retryMax = 6;
                        }
                    }
                    if (request->retryCount >= retryMax) {
                        DEBUG_E("timed out %s", typeInfo.name());
                        TL_error *error = new TL_error();
                        error->code = -123;
                        error->text = "RETRY_LIMIT";
                        request->onComplete(nullptr, error, connection->currentNetworkType);
                        delete error;
                        iter = runningRequests.erase(iter);
                        continue;
                    }
                }
            } else {
                request->failedBySalt = false;
            }

            if (request->messageSeqNo == 0) {
                request->messageSeqNo = connection->generateMessageSeqNo((request->connectionType & ConnectionTypeProxy) == 0);
                request->messageId = generateMessageId();
                if (request->rawRequest->initFunc != nullptr) {
                    request->rawRequest->initFunc(request->messageId);
                }
            }
            request->startTime = currentTime;
            request->startTimeMillis = currentTimeMillis;

            NetworkMessage *networkMessage = new NetworkMessage();
            networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
            networkMessage->message->msg_id = request->messageId;
            networkMessage->message->bytes = request->serializedLength;
            networkMessage->message->outgoingBody = request->getRpcRequest();
            networkMessage->message->seqno = request->messageSeqNo;
            networkMessage->requestId = request->requestToken;
            networkMessage->invokeAfter = (request->requestFlags & RequestFlagInvokeAfter) != 0;
            networkMessage->needQuickAck = (request->requestFlags & RequestFlagNeedQuickAck) != 0;

            request->connectionToken = connection->getConnectionToken();
            switch (requestConnectionType) {
                case ConnectionTypeGeneric:
                    addMessageToDatacenter(requestDatacenter->getDatacenterId(), networkMessage, genericMessagesToDatacenters);
                    break;
                case ConnectionTypeGenericMedia:
                    addMessageToDatacenter(requestDatacenter->getDatacenterId(), networkMessage, genericMediaMessagesToDatacenters);
                    break;
                case ConnectionTypeTemp:
                    addMessageToDatacenter(requestDatacenter->getDatacenterId(), networkMessage, tempMessagesToDatacenters);
                    break;
                case ConnectionTypeProxy: {
                    std::vector<std::unique_ptr<NetworkMessage>> array;
                    array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
                    sendMessagesToConnection(array, connection, false);
                    break;
                }
                case ConnectionTypeDownload:
                case ConnectionTypeUpload: {
                    std::vector<std::unique_ptr<NetworkMessage>> array;
                    array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
                    sendMessagesToConnectionWithConfirmation(array, connection, false);
                    request->onWriteToSocket();
                    break;
                }
                default:
                    delete networkMessage;
            }
        }
        iter++;
    }

    Connection *genericConnection = nullptr;
    Datacenter *defaultDatacenter = getDatacenterWithId(currentDatacenterId);
    if (defaultDatacenter != nullptr) {
        genericConnection = defaultDatacenter->getGenericConnection(true, 0);
        if (genericConnection != nullptr && !sessionsToDestroy.empty() && genericConnection->getConnectionToken() != 0) {
            std::vector<int64_t>::iterator iter = sessionsToDestroy.begin();

            sessionsToDestroy.erase(iter);

            if (abs(currentTime - lastDestroySessionRequestTime) > 2) {
                lastDestroySessionRequestTime = currentTime;
                TL_destroy_session *request = new TL_destroy_session();
                request->session_id = *iter;

                NetworkMessage *networkMessage = new NetworkMessage();
                networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
                networkMessage->message->msg_id = generateMessageId();
                networkMessage->message->bytes = request->getObjectSize();
                networkMessage->message->body = std::unique_ptr<TLObject>(request);
                networkMessage->message->seqno = genericConnection->generateMessageSeqNo(false);
                addMessageToDatacenter(defaultDatacenter->getDatacenterId(), networkMessage, genericMessagesToDatacenters);
            }
        }
    }

    for (requestsIter iter = requestsQueue.begin(); iter != requestsQueue.end();) {
        Request *request = iter->get();
        if (request->cancelled) {
            iter = requestsQueue.erase(iter);
            continue;
        }

        uint32_t datacenterId = request->datacenterId;
        if (datacenterId == DEFAULT_DATACENTER_ID) {
            if (movingToDatacenterId != DEFAULT_DATACENTER_ID) {
                iter++;
                continue;
            }
            datacenterId = currentDatacenterId;
        }

        int32_t canUseUnboundKey = 0;
        if ((request->requestFlags & RequestFlagUseUnboundKey) != 0) {
            canUseUnboundKey |= 1;
        }

        if (request->requestFlags & RequestFlagTryDifferentDc) {
            int32_t requestStartTime = request->startTime;
            int32_t timeout = 30;
            if (updatingDcSettings && dynamic_cast<TL_help_getConfig *>(request->rawRequest)) {
                requestStartTime = updatingDcStartTime;
                timeout = 60;
            } else {
                request->startTime = 0;
                request->startTimeMillis = 0;
            }
            if (requestStartTime != 0 && abs(currentTime - requestStartTime) >= timeout) {
                std::vector<uint32_t> allDc;
                for (std::map<uint32_t, Datacenter *>::iterator iter2 = datacenters.begin(); iter2 != datacenters.end(); iter2++) {
                    if (iter2->first == datacenterId || iter2->second->isCdnDatacenter) {
                        continue;
                    }
                    allDc.push_back(iter2->first);
                }
                uint8_t index;
                RAND_bytes(&index, 1);
                datacenterId = allDc[index % allDc.size()];
                if (dynamic_cast<TL_help_getConfig *>(request->rawRequest)) {
                    updatingDcStartTime = currentTime;
                    request->datacenterId = datacenterId;
                } else {
                    currentDatacenterId = datacenterId;
                }
            }
        }

        Datacenter *requestDatacenter = getDatacenterWithId(datacenterId);
        if (requestDatacenter == nullptr) {
            if (std::find(unknownDatacenterIds.begin(), unknownDatacenterIds.end(), datacenterId) == unknownDatacenterIds.end()) {
                unknownDatacenterIds.push_back(datacenterId);
            }
            iter++;
            continue;
        } else {
            if (request->needInitRequest(requestDatacenter, currentVersion) && !request->hasInitFlag()) {
                request->rpcRequest.release();
                request->rpcRequest = wrapInLayer(request->rawRequest, requestDatacenter, request);
            }

            if (!requestDatacenter->hasAuthKey(request->connectionType, canUseUnboundKey)) {
                std::pair<Datacenter *, ConnectionType> pair = std::make_pair(requestDatacenter, request->connectionType);
                if (std::find(neededDatacenters.begin(), neededDatacenters.end(), pair) == neededDatacenters.end()) {
                    neededDatacenters.push_back(pair);
                }
                iter++;
                continue;
            } else if (!(request->requestFlags & RequestFlagEnableUnauthorized) && !requestDatacenter->authorized && request->datacenterId != DEFAULT_DATACENTER_ID && request->datacenterId != currentDatacenterId) {
                if (std::find(unauthorizedDatacenters.begin(), unauthorizedDatacenters.end(), requestDatacenter) == unauthorizedDatacenters.end()) {
                    unauthorizedDatacenters.push_back(requestDatacenter);
                }
                iter++;
                continue;
            }
        }

        Connection *connection = requestDatacenter->getConnectionByType(request->connectionType, true, canUseUnboundKey);

        if (request->connectionType & ConnectionTypeGeneric && connection->getConnectionToken() == 0) {
            iter++;
            continue;
        }

        switch (request->connectionType & 0x0000ffff) {
            case ConnectionTypeGeneric:
            case ConnectionTypeGenericMedia:
                if (!canUseUnboundKey && genericRunningRequestCount >= 60) {
                    iter++;
                    continue;
                }
                genericRunningRequestCount++;
                break;
            case ConnectionTypeDownload: {
                uint32_t currentCount;
                std::map<uint32_t, uint32_t>::iterator dcIter = downloadRunningRequestCount.find(datacenterId);
                if (dcIter != downloadRunningRequestCount.end()) {
                    currentCount = dcIter->second;
                } else {
                    currentCount = 0;
                }
                if (!networkAvailable || currentCount >= 6) {
                    iter++;
                    continue;
                }
                downloadRunningRequestCount[datacenterId] = currentCount + 1;
                break;
            }
            case ConnectionTypeProxy:
            case ConnectionTypeTemp:
                if (!networkAvailable) {
                    iter++;
                    continue;
                }
                break;
            case ConnectionTypeUpload:
                if (!networkAvailable || uploadRunningRequestCount >= 10) {
                    iter++;
                    continue;
                }
                uploadRunningRequestCount++;
                break;
            default:
                break;
        }

        request->messageId = generateMessageId();
        if (request->rawRequest->initFunc != nullptr) {
            request->rawRequest->initFunc(request->messageId);
        }

        uint32_t requestLength = request->rpcRequest->getObjectSize();
        if (request->requestFlags & RequestFlagCanCompress) {
            request->requestFlags &= ~RequestFlagCanCompress;
            NativeByteBuffer *original = BuffersStorage::getInstance().getFreeBuffer(requestLength);
            request->rpcRequest->serializeToStream(original);
            NativeByteBuffer *buffer = compressGZip(original);
            if (buffer != nullptr) {
                TL_gzip_packed *packed = new TL_gzip_packed();
                packed->originalRequest = std::move(request->rpcRequest);
                packed->packed_data_to_send = buffer;
                request->rpcRequest = std::unique_ptr<TLObject>(packed);
                requestLength = packed->getObjectSize();
            }
            original->reuse();
        }

        request->serializedLength = requestLength;
        request->messageSeqNo = connection->generateMessageSeqNo((request->connectionType & ConnectionTypeProxy) == 0);
        request->startTime = currentTime;
        request->startTimeMillis = currentTimeMillis;
        request->connectionToken = connection->getConnectionToken();

        NetworkMessage *networkMessage = new NetworkMessage();
        networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
        networkMessage->message->msg_id = request->messageId;
        networkMessage->message->bytes = request->serializedLength;
        networkMessage->message->outgoingBody = request->getRpcRequest();
        networkMessage->message->seqno = request->messageSeqNo;
        networkMessage->requestId = request->requestToken;
        networkMessage->invokeAfter = (request->requestFlags & RequestFlagInvokeAfter) != 0;
        networkMessage->needQuickAck = (request->requestFlags & RequestFlagNeedQuickAck) != 0;

        runningRequests.push_back(std::move(*iter));

        switch (request->connectionType & 0x0000ffff) {
            case ConnectionTypeGeneric:
                addMessageToDatacenter(requestDatacenter->getDatacenterId(), networkMessage, genericMessagesToDatacenters);
                break;
            case ConnectionTypeGenericMedia:
                addMessageToDatacenter(requestDatacenter->getDatacenterId(), networkMessage, genericMediaMessagesToDatacenters);
                break;
            case ConnectionTypeTemp:
                addMessageToDatacenter(requestDatacenter->getDatacenterId(), networkMessage, tempMessagesToDatacenters);
                break;
            case ConnectionTypeProxy: {
                std::vector<std::unique_ptr<NetworkMessage>> array;
                array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
                sendMessagesToConnection(array, connection, false);
                break;
            }
            case ConnectionTypeDownload:
            case ConnectionTypeUpload: {
                std::vector<std::unique_ptr<NetworkMessage>> array;
                array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
                sendMessagesToConnectionWithConfirmation(array, connection, false);
                break;
            }
            default:
                delete networkMessage;
        }

        iter = requestsQueue.erase(iter);
    }

    for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
        Datacenter *datacenter = iter->second;
        std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter2 = genericMessagesToDatacenters.find(datacenter->getDatacenterId());
        if (iter2 == genericMessagesToDatacenters.end()) {
            Connection *connection = datacenter->getGenericConnection(false, 1);
            if (connection != nullptr && connection->getConnectionToken() != 0 && connection->hasMessagesToConfirm()) {
                genericMessagesToDatacenters[datacenter->getDatacenterId()] = std::vector<std::unique_ptr<NetworkMessage>>();
            }
        }

        iter2 = genericMediaMessagesToDatacenters.find(datacenter->getDatacenterId());
        if (iter2 == genericMediaMessagesToDatacenters.end()) {
            Connection *connection = datacenter->getGenericMediaConnection(false, 1);
            if (connection != nullptr && connection->getConnectionToken() != 0 && connection->hasMessagesToConfirm()) {
                genericMediaMessagesToDatacenters[datacenter->getDatacenterId()] = std::vector<std::unique_ptr<NetworkMessage>>();
            }
        }

        iter2 = tempMessagesToDatacenters.find(datacenter->getDatacenterId());
        if (iter2 == tempMessagesToDatacenters.end()) {
            Connection *connection = datacenter->getTempConnection(false);
            if (connection != nullptr && connection->getConnectionToken() != 0 && connection->hasMessagesToConfirm()) {
                tempMessagesToDatacenters[datacenter->getDatacenterId()] = std::vector<std::unique_ptr<NetworkMessage>>();
            }
        }
    }

    for (std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter = genericMessagesToDatacenters.begin(); iter != genericMessagesToDatacenters.end(); iter++) {
        Datacenter *datacenter = getDatacenterWithId(iter->first);
        if (datacenter != nullptr) {
            bool scannedPreviousRequests = false;
            int64_t lastSentMessageRpcId = 0;
            bool needQuickAck = false;
            std::vector<std::unique_ptr<NetworkMessage>> &array = iter->second;
            size_t count = array.size();
            for (uint32_t b = 0; b < count; b++) {
                NetworkMessage *networkMessage = array[b].get();
                if (networkMessage->needQuickAck) {
                    needQuickAck = true;
                }
                if (networkMessage->invokeAfter) {
                    if (!scannedPreviousRequests) {
                        scannedPreviousRequests = true;

                        std::vector<int64_t> currentRequests;
                        for (uint32_t a = 0; a < count; a++) {
                            NetworkMessage *currentNetworkMessage = array[a].get();
                            TL_message *currentMessage = currentNetworkMessage->message.get();
                            if (currentNetworkMessage->invokeAfter) {
                                currentRequests.push_back(currentMessage->msg_id);
                            }
                        }

                        int64_t maxRequestId = 0;
                        for (requestsIter iter2 = runningRequests.begin(); iter2 != runningRequests.end(); iter2++) {
                            Request *request = iter2->get();
                            if (request->requestFlags & RequestFlagInvokeAfter) {
                                if (request->messageId > maxRequestId && std::find(currentRequests.begin(), currentRequests.end(), request->messageId) == currentRequests.end()) {
                                    maxRequestId = request->messageId;
                                }
                            }
                        }

                        lastSentMessageRpcId = maxRequestId;
                    }

                    TL_message *message = networkMessage->message.get();

                    if (lastSentMessageRpcId != 0 && lastSentMessageRpcId != message->msg_id) {
                        TL_invokeAfterMsg *request = new TL_invokeAfterMsg();
                        request->msg_id = lastSentMessageRpcId;
                        if (message->outgoingBody != nullptr) {
                            DEBUG_D("wrap outgoingBody(%p, %s) to TL_invokeAfterMsg", message->outgoingBody, typeid(*message->outgoingBody).name());
                            request->outgoingQuery = message->outgoingBody;
                            message->outgoingBody = nullptr;
                        } else {
                            DEBUG_D("wrap body(%p, %s) to TL_invokeAfterMsg", message->body.get(), typeid(*(message->body.get())).name());
                            request->query = std::move(message->body);
                        }
                        message->body = std::unique_ptr<TLObject>(request);
                        message->bytes += 4 + 8;
                    }

                    lastSentMessageRpcId = message->msg_id;
                }
            }

            sendMessagesToConnectionWithConfirmation(array, datacenter->getGenericConnection(true, 1), needQuickAck);
        }
    }

    for (std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter = tempMessagesToDatacenters.begin(); iter != tempMessagesToDatacenters.end(); iter++) {
        Datacenter *datacenter = getDatacenterWithId(iter->first);
        if (datacenter != nullptr) {
            std::vector<std::unique_ptr<NetworkMessage>> &array = iter->second;
            sendMessagesToConnectionWithConfirmation(array, datacenter->getTempConnection(true), false);
        }
    }

    for (std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter = genericMediaMessagesToDatacenters.begin(); iter != genericMediaMessagesToDatacenters.end(); iter++) {
        Datacenter *datacenter = getDatacenterWithId(iter->first);
        if (datacenter != nullptr) {
            std::vector<std::unique_ptr<NetworkMessage>> &array = iter->second;
            sendMessagesToConnectionWithConfirmation(array, datacenter->getGenericMediaConnection(true, 1), false);
        }
    }

    if (connectionTypes == ConnectionTypeGeneric && dc == currentDatacenterId) {
        std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter2 = genericMessagesToDatacenters.find(currentDatacenterId);
        if (iter2 == genericMessagesToDatacenters.end()) {
            sendPing(getDatacenterWithId(currentDatacenterId), false);
        }
    }

    if (!unknownDatacenterIds.empty()) {
        updateDcSettings(0, false);
    }

    size_t count = neededDatacenters.size();
    for (uint32_t a = 0; a < count; a++) {
        Datacenter *datacenter = neededDatacenters[a].first;
        bool media = Connection::isMediaConnectionType(neededDatacenters[a].second) && datacenter->hasMediaAddress();
        if (datacenter->getDatacenterId() != movingToDatacenterId && !datacenter->isHandshaking(media) && !datacenter->hasAuthKey(neededDatacenters[a].second, 1)) {
            datacenter->beginHandshake(media ? HandshakeTypeMediaTemp : HandshakeTypeTemp, true);
        }
    }

    if (currentUserId) {
        count = unauthorizedDatacenters.size();
        for (uint32_t a = 0; a < count; a++) {
            Datacenter *datacenter = unauthorizedDatacenters[a];
            uint32_t id = datacenter->getDatacenterId();
            if (id != currentDatacenterId && id != movingToDatacenterId && !datacenter->isExportingAuthorization()) {
                datacenter->exportAuthorization();
            }
        }
    }
}

Datacenter *ConnectionsManager::getDatacenterWithId(uint32_t datacenterId) {
    if (datacenterId == DEFAULT_DATACENTER_ID) {
        return datacenters[currentDatacenterId];
    }
    std::map<uint32_t, Datacenter *>::iterator iter = datacenters.find(datacenterId);
    return iter != datacenters.end() ? iter->second : nullptr;
}

std::unique_ptr<TLObject> ConnectionsManager::wrapInLayer(TLObject *object, Datacenter *datacenter, Request *baseRequest) {
    if (object->isNeedLayer()) {
        bool media = PFS_ENABLED && datacenter != nullptr && baseRequest->isMediaRequest() && datacenter->hasMediaAddress();
        if (datacenter == nullptr || baseRequest->needInitRequest(datacenter, currentVersion)) {
            if (datacenter != nullptr && datacenter->getDatacenterId() == currentDatacenterId) {
                registerForInternalPushUpdates();
            }
            if (media) {
                baseRequest->isInitMediaRequest = true;
            } else {
                baseRequest->isInitRequest = true;
            }
            initConnection *request = new initConnection();
            if (delegate != nullptr) {
                request->flags = delegate->getInitFlags(instanceNum);
            } else {
                request->flags = 0;
            }
            request->query = std::unique_ptr<TLObject>(object);
            request->api_id = currentApiId;
            request->app_version = currentAppVersion;
            request->lang_code = currentLangCode;
            request->system_lang_code = currentLangCode;
            request->lang_pack = "android";
            request->system_lang_code = currentSystemLangCode;
            if (!proxyAddress.empty() && !proxySecret.empty()) {
                request->flags |= 1;
                request->proxy = std::unique_ptr<TL_inputClientProxy>(new TL_inputClientProxy());
                request->proxy->address = proxyAddress;
                request->proxy->port = proxyPort;
            }

            if (datacenter == nullptr || datacenter->isCdnDatacenter) {
                request->device_model = "n/a";
                request->system_version = "n/a";
            } else {
                request->device_model = currentDeviceModel;
                request->system_version = currentSystemVersion;
            }
            if (request->lang_code.empty()) {
                request->lang_code = "en";
            }
            if (request->device_model.empty()) {
                request->device_model = "n/a";
            }
            if (request->app_version.empty()) {
                request->app_version = "n/a";
            }
            if (request->system_version.empty()) {
                request->system_version = "n/a";
            }
            invokeWithLayer *request2 = new invokeWithLayer();
            request2->layer = currentLayer;
            request2->query = std::unique_ptr<TLObject>(request);
            DEBUG_D("wrap in layer %s", typeid(*object).name());
            return std::unique_ptr<TLObject>(request2);
        }
    }
    return std::unique_ptr<TLObject>(object);
}

inline std::string hexStr(unsigned char *data, uint32_t len) {
    constexpr char hexmap[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    std::string s(len * 2, ' ');
    for (uint32_t i = 0; i < len; ++i) {
        s[2 * i] = hexmap[(data[i] & 0xF0) >> 4];
        s[2 * i + 1] = hexmap[data[i] & 0x0F];
    }
    return s;
}

void ConnectionsManager::updateDcSettings(uint32_t dcNum, bool workaround) {
    if (workaround) {
        if (updatingDcSettingsWorkaround) {
            return;
        }
        updatingDcSettingsWorkaround = true;
    } else {
        if (updatingDcSettings) {
            return;
        }
        updatingDcSettings = true;
        updatingDcStartTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000);
    }

    TL_help_getConfig *request = new TL_help_getConfig();
    sendRequest(request, [&, workaround](TLObject *response, TL_error *error, int32_t networkType) {
        if (!workaround && !updatingDcSettings || workaround && !updatingDcSettingsWorkaround) {
            return;
        }

        if (response != nullptr) {
            TL_config *config = (TL_config *) response;
            clientBlocked = (config->flags & 256) != 0;
            if (!workaround) {
                int32_t updateIn = config->expires - getCurrentTime();
                if (updateIn <= 0) {
                    updateIn = 120;
                }
                lastDcUpdateTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000) - DC_UPDATE_TIME + updateIn;
            }

            struct DatacenterInfo {
                std::vector<TcpAddress> addressesIpv4;
                std::vector<TcpAddress> addressesIpv6;
                std::vector<TcpAddress> addressesIpv4Download;
                std::vector<TcpAddress> addressesIpv6Download;
                bool isCdn = false;

                void addAddressAndPort(TL_dcOption *dcOption) {
                    std::vector<TcpAddress> *addresses;
                    if (!isCdn) {
                        isCdn = dcOption->cdn;
                    }
                    if (dcOption->media_only) {
                        if (dcOption->ipv6) {
                            addresses = &addressesIpv6Download;
                        } else {
                            addresses = &addressesIpv4Download;
                        }
                    } else {
                        if (dcOption->ipv6) {
                            addresses = &addressesIpv6;
                        } else {
                            addresses = &addressesIpv4;
                        }
                    }
                    for (std::vector<TcpAddress>::iterator iter = addresses->begin(); iter != addresses->end(); iter++) {
                        if (iter->address == dcOption->ip_address && iter->port == dcOption->port) {
                            return;
                        }
                    }
                    std::string secret;
                    if (dcOption->secret != nullptr) {
                        secret = hexStr(dcOption->secret->bytes, dcOption->secret->length);
                    }
                    DEBUG_D("getConfig add %s:%d to dc%d, flags %d, has secret = %d[%d]", dcOption->ip_address.c_str(), dcOption->port, dcOption->id, dcOption->flags, dcOption->secret != nullptr ? 1 : 0, dcOption->secret != nullptr ? dcOption->secret->length : 0);
                    addresses->push_back(TcpAddress(dcOption->ip_address, dcOption->port, dcOption->flags, secret));
                }
            };

            std::map<uint32_t, std::unique_ptr<DatacenterInfo>> map;
            size_t count = config->dc_options.size();
            for (uint32_t a = 0; a < count; a++) {
                TL_dcOption *dcOption = config->dc_options[a].get();
                std::map<uint32_t, std::unique_ptr<DatacenterInfo>>::iterator iter = map.find((uint32_t) dcOption->id);
                DatacenterInfo *info;
                if (iter == map.end()) {
                    map[dcOption->id] = std::unique_ptr<DatacenterInfo>(info = new DatacenterInfo);
                } else {
                    info = iter->second.get();
                }
                info->addAddressAndPort(dcOption);
            }

            if (!map.empty()) {
                for (std::map<uint32_t, std::unique_ptr<DatacenterInfo>>::iterator iter = map.begin(); iter != map.end(); iter++) {
                    Datacenter *datacenter = getDatacenterWithId(iter->first);
                    DatacenterInfo *info = iter->second.get();
                    if (datacenter == nullptr) {
                        datacenter = new Datacenter(instanceNum, iter->first);
                        datacenters[iter->first] = datacenter;
                    }
                    datacenter->replaceAddresses(info->addressesIpv4, info->isCdn ? 8 : 0);
                    datacenter->replaceAddresses(info->addressesIpv6, info->isCdn ? 9 : 1);
                    datacenter->replaceAddresses(info->addressesIpv4Download, info->isCdn ? 10 : 2);
                    datacenter->replaceAddresses(info->addressesIpv6Download, info->isCdn ? 11 : 3);
                    if (iter->first == movingToDatacenterId) {
                        movingToDatacenterId = DEFAULT_DATACENTER_ID;
                        moveToDatacenter(iter->first);
                    }
                }
                saveConfig();
                scheduleTask([&] {
                    processRequestQueue(AllConnectionTypes, 0);
                });
            }
            if (delegate != nullptr) {
                delegate->onUpdateConfig(config, instanceNum);
            }
        }
        if (workaround) {
            updatingDcSettingsWorkaround = false;
        } else {
            updatingDcSettings = false;
        }
    }, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin | RequestFlagUseUnboundKey | (workaround ? 0 : RequestFlagTryDifferentDc), dcNum == 0 ? currentDatacenterId : dcNum, workaround ? ConnectionTypeTemp : ConnectionTypeGeneric, true);
}

void ConnectionsManager::moveToDatacenter(uint32_t datacenterId) {
    if (movingToDatacenterId == datacenterId) {
        return;
    }
    movingToDatacenterId = datacenterId;

    Datacenter *currentDatacenter = getDatacenterWithId(currentDatacenterId);
    clearRequestsForDatacenter(currentDatacenter, HandshakeTypeAll);

    if (currentUserId) {
        TL_auth_exportAuthorization *request = new TL_auth_exportAuthorization();
        request->dc_id = datacenterId;
        sendRequest(request, [&, datacenterId](TLObject *response, TL_error *error, int32_t networkType) {
            if (error == nullptr) {
                movingAuthorization = std::move(((TL_auth_exportedAuthorization *) response)->bytes);
                authorizeOnMovingDatacenter();
            } else {
                moveToDatacenter(datacenterId);
            }
        }, nullptr, RequestFlagWithoutLogin, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
    } else {
        authorizeOnMovingDatacenter();
    }
}

void ConnectionsManager::authorizeOnMovingDatacenter() {
    Datacenter *datacenter = getDatacenterWithId(movingToDatacenterId);
    if (datacenter == nullptr) {
        updateDcSettings(0, false);
        return;
    }
    datacenter->recreateSessions(HandshakeTypeAll);
    clearRequestsForDatacenter(datacenter, HandshakeTypeAll);

    if (!datacenter->hasAuthKey(ConnectionTypeGeneric, 0) && !datacenter->isHandshakingAny()) {
        datacenter->clearServerSalts();
        datacenter->beginHandshake(HandshakeTypeAll, true);
    }

    if (movingAuthorization != nullptr) {
        TL_auth_importAuthorization *request = new TL_auth_importAuthorization();
        request->id = currentUserId;
        request->bytes = std::move(movingAuthorization);
        sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType) {
            if (error == nullptr) {
                authorizedOnMovingDatacenter();
            } else {
                moveToDatacenter(movingToDatacenterId);
            }
        }, nullptr, RequestFlagWithoutLogin, datacenter->getDatacenterId(), ConnectionTypeGeneric, true);
    } else {
        authorizedOnMovingDatacenter();
    }
}

void ConnectionsManager::authorizedOnMovingDatacenter() {
    movingAuthorization.reset();
    currentDatacenterId = movingToDatacenterId;
    movingToDatacenterId = DEFAULT_DATACENTER_ID;
    saveConfig();
    scheduleTask([&] {
        processRequestQueue(0, 0);
    });
}

void ConnectionsManager::applyDatacenterAddress(uint32_t datacenterId, std::string ipAddress, uint32_t port) {
    scheduleTask([&, datacenterId, ipAddress, port] {
        Datacenter *datacenter = getDatacenterWithId(datacenterId);
        if (datacenter != nullptr) {
            std::vector<TcpAddress> addresses;
            addresses.push_back(TcpAddress(ipAddress, port, 0, ""));
            datacenter->suspendConnections();
            datacenter->replaceAddresses(addresses, 0);
            datacenter->resetAddressAndPortNum();
            saveConfig();
            if (datacenter->isHandshakingAny()) {
                datacenter->beginHandshake(HandshakeTypeCurrent, true);
            }
            updateDcSettings(datacenterId, false);
        }
    });
}

ConnectionState ConnectionsManager::getConnectionState() {
    return connectionState;
}

void ConnectionsManager::setDelegate(ConnectiosManagerDelegate *connectiosManagerDelegate) {
    delegate = connectiosManagerDelegate;
}

void ConnectionsManager::setPushConnectionEnabled(bool value) {
    pushConnectionEnabled = value;
    Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
    if (datacenter != nullptr) {
        if (!pushConnectionEnabled) {
            Connection *connection = datacenter->getPushConnection(false);
            if (connection != nullptr) {
                connection->suspendConnection();
            }
        } else {
            datacenter->createPushConnection()->setSessionId(pushSessionId);
            sendPing(datacenter, true);
        }
    }
}

inline bool checkPhoneByPrefixesRules(std::string phone, std::string rules) {
    if (rules.empty() || phone.empty()) {
        return true;
    }
    bool found = false;

    std::stringstream ss(rules);
    std::string prefix;
    while (std::getline(ss, prefix, ',')) {
        if (prefix == "") {
            found = true;
        } else if (prefix[0] == '+' && phone.find(prefix.substr(1)) == 0) {
            found = true;
        } else if (prefix[0] == '-' && phone.find(prefix.substr(1)) == 0) {
            return false;
        }
    }
    return found;
}

void ConnectionsManager::applyDnsConfig(NativeByteBuffer *buffer, std::string phone) {
    scheduleTask([&, buffer, phone] {
        DEBUG_D("trying to decrypt config %d", requestingSecondAddress);
        TL_help_configSimple *config = Datacenter::decodeSimpleConfig(buffer);
        int currentDate = getCurrentTime();
        if (config != nullptr && config->date <= currentDate && currentDate <= config->expires) {
            for (std::vector<std::unique_ptr<TL_accessPointRule>>::iterator iter = config->rules.begin(); iter != config->rules.end(); iter++) {
                TL_accessPointRule *rule = iter->get();
                if (!checkPhoneByPrefixesRules(phone, rule->phone_prefix_rules)) {
                    continue;
                }
                Datacenter *datacenter = getDatacenterWithId(rule->dc_id);
                if (datacenter != nullptr) {
                    std::vector<TcpAddress> addresses;
                    for (std::vector<std::unique_ptr<IpPort>>::iterator iter2 = rule->ips.begin(); iter2 != rule->ips.end(); iter2++) {
                        IpPort *port = iter2->get();
                        const std::type_info &typeInfo = typeid(*port);
                        if (typeInfo == typeid(TL_ipPort)) {
                            TL_ipPort *ipPort = (TL_ipPort *) port;
                            addresses.push_back(TcpAddress(ipPort->ipv4, ipPort->port, 0, ""));
                            DEBUG_D("got address %s and port %d for dc%d", ipPort->ipv4.c_str(), ipPort->port, rule->dc_id);
                        } else if (typeInfo == typeid(TL_ipPortSecret)) {
                            TL_ipPortSecret *ipPort = (TL_ipPortSecret *) port;
                            addresses.push_back(TcpAddress(ipPort->ipv4, ipPort->port, 0, hexStr(ipPort->secret->bytes, ipPort->secret->length)));
                            DEBUG_D("got address %s and port %d for dc%d with secret", ipPort->ipv4.c_str(), ipPort->port, rule->dc_id);
                        }
                    }
                    if (!addresses.empty()) {
                        datacenter->replaceAddresses(addresses, TcpAddressFlagTemp);
                        Connection *connection = datacenter->getTempConnection(false);
                        if (connection != nullptr) {
                            connection->suspendConnection();
                        }
                        if (datacenter->isHandshakingAny()) {
                            datacenter->beginHandshake(HandshakeTypeCurrent, true);
                        }
                        updateDcSettings(rule->dc_id, true);
                    }
                } else {
                    DEBUG_D("config datacenter %d not found", rule->dc_id);
                }
            }
            delete config;
        } else {
            if (config == nullptr) {
                DEBUG_D("can't decrypt dns config");
            } else {
                delete config;
                DEBUG_D("dns config not valid due to date or expire");
            }
            if (requestingSecondAddress == 0) {
                requestingSecondAddress = 1;
                delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
            } else if (requestingSecondAddress == 1) {
                requestingSecondAddress = 2;
                delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
            } else {
                requestingSecondAddress = 0;
            }
        }
        buffer->reuse();
    });
}

void ConnectionsManager::init(uint32_t version, int32_t layer, int32_t apiId, std::string deviceModel, std::string systemVersion, std::string appVersion, std::string langCode, std::string systemLangCode, std::string configPath, std::string logPath, int32_t userId, bool isPaused, bool enablePushConnection, bool hasNetwork, int32_t networkType) {
    currentVersion = version;
    currentLayer = layer;
    currentApiId = apiId;
    currentConfigPath = configPath;
    currentDeviceModel = deviceModel;
    currentSystemVersion = systemVersion;
    currentAppVersion = appVersion;
    currentLangCode = langCode;
    currentSystemLangCode = systemLangCode;
    currentUserId = userId;
    currentLogPath = logPath;
    pushConnectionEnabled = enablePushConnection;
    currentNetworkType = networkType;
    networkAvailable = hasNetwork;
    if (isPaused) {
        lastPauseTime = getCurrentTimeMonotonicMillis();
    }

    if (!currentConfigPath.empty() && currentConfigPath.find_last_of('/') != currentConfigPath.size() - 1) {
        currentConfigPath += "/";
    }

    if (!logPath.empty()) {
        FileLog::getInstance().init(logPath);
    }

    loadConfig();

    pthread_create(&networkThread, NULL, (ConnectionsManager::ThreadProc), this);
}

void ConnectionsManager::setProxySettings(std::string address, uint16_t port, std::string username, std::string password, std::string secret) {
    scheduleTask([&, address, port, username, password, secret] {
        bool secretChanged = proxySecret != secret;
        bool reconnect = proxyAddress != address || proxyPort != port || username != proxyUser || proxyPassword != password || secretChanged;
        proxyAddress = address;
        proxyPort = port;
        proxyUser = username;
        proxyPassword = password;
        proxySecret = secret;
        if (!proxyAddress.empty() && connectionState == ConnectionStateConnecting) {
            connectionState = ConnectionStateConnectingViaProxy;
            if (delegate != nullptr) {
                delegate->onConnectionStateChanged(connectionState, instanceNum);
            }
        } else if (proxyAddress.empty() && connectionState == ConnectionStateConnectingViaProxy) {
            connectionState = ConnectionStateConnecting;
            if (delegate != nullptr) {
                delegate->onConnectionStateChanged(connectionState, instanceNum);
            }
        }
        if (secretChanged) {
            Datacenter *datacenter = getDatacenterWithId(DEFAULT_DATACENTER_ID);
            if (datacenter != nullptr) {
                datacenter->resetInitVersion();
            }
        }
        if (reconnect) {
            for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
                iter->second->suspendConnections();
            }
            Datacenter *datacenter = getDatacenterWithId(DEFAULT_DATACENTER_ID);
            if (datacenter != nullptr && datacenter->isHandshakingAny()) {
                datacenter->beginHandshake(HandshakeTypeCurrent, true);
            }
            processRequestQueue(0, 0);
        }
    });
}

void ConnectionsManager::setLangCode(std::string langCode) {
    scheduleTask([&, langCode] {
        if (currentLangCode.compare(langCode) == 0) {
            return;
        }
        currentLangCode = langCode;
        for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
            iter->second->resetInitVersion();
        }
        saveConfig();
    });
}

void ConnectionsManager::resumeNetwork(bool partial) {
    scheduleTask([&, partial] {
        if (partial) {
            if (networkPaused) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
                networkPaused = false;
                DEBUG_D("wakeup network in background");
            } else if (lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
                networkPaused = false;
                DEBUG_D("reset sleep timeout");
            }
        } else {
            DEBUG_D("wakeup network");
            lastPauseTime = 0;
            networkPaused = false;
        }
    });
}

void ConnectionsManager::pauseNetwork() {
    if (lastPauseTime != 0) {
        return;
    }
    lastPauseTime = getCurrentTimeMonotonicMillis();
}

void ConnectionsManager::setNetworkAvailable(bool value, int32_t type, bool slow) {
    scheduleTask([&, value, type] {
        networkAvailable = value;
        currentNetworkType = type;
        networkSlow = slow;
        if (!networkAvailable) {
            connectionState = ConnectionStateWaitingForNetwork;
        } else {
            for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
                if (iter->second->isHandshaking(false)) {
                    iter->second->createGenericConnection()->connect();
                } else if (iter->second->isHandshaking(true)) {
                    iter->second->createGenericMediaConnection()->connect();
                }
            }
        }
        if (delegate != nullptr) {
            delegate->onConnectionStateChanged(connectionState, instanceNum);
        }
    });
}

void ConnectionsManager::setUseIpv6(bool value) {
    scheduleTask([&, value] {
        ipv6Enabled = value;
    });
}

void ConnectionsManager::setMtProtoVersion(int version) {
    mtProtoVersion = version;
}

int32_t ConnectionsManager::getMtProtoVersion() {
    return mtProtoVersion;
}

int64_t ConnectionsManager::checkProxy(std::string address, uint16_t port, std::string username, std::string password, std::string secret, onRequestTimeFunc requestTimeFunc, jobject ptr1) {
    ProxyCheckInfo *proxyCheckInfo = new ProxyCheckInfo();
    proxyCheckInfo->address = address;
    proxyCheckInfo->port = port;
    proxyCheckInfo->username = username;
    proxyCheckInfo->password = password;
    proxyCheckInfo->secret = secret;
    proxyCheckInfo->onRequestTime = requestTimeFunc;
    proxyCheckInfo->pingId = ++lastPingProxyId;
    proxyCheckInfo->instanceNum = instanceNum;
    proxyCheckInfo->ptr1 = ptr1;

    checkProxyInternal(proxyCheckInfo);

    return proxyCheckInfo->pingId;
}

void ConnectionsManager::checkProxyInternal(ProxyCheckInfo *proxyCheckInfo) {
    scheduleTask([&, proxyCheckInfo] {
        int32_t freeConnectionNum = -1;
        if (proxyActiveChecks.size() != PROXY_CONNECTIONS_COUNT) {
            for (int32_t a = 0; a < PROXY_CONNECTIONS_COUNT; a++) {
                bool found = false;
                for (std::vector<std::unique_ptr<ProxyCheckInfo>>::iterator iter = proxyActiveChecks.begin(); iter != proxyActiveChecks.end(); iter++) {
                    if (iter->get()->connectionNum == a) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    freeConnectionNum = a;
                    break;
                }
            }
        }
        if (freeConnectionNum == -1) {
            proxyCheckQueue.push_back(std::unique_ptr<ProxyCheckInfo>(proxyCheckInfo));
        } else {
            ConnectionType connectionType = (ConnectionType) (ConnectionTypeProxy | (freeConnectionNum << 16));
            Datacenter *datacenter = getDatacenterWithId(DEFAULT_DATACENTER_ID);
            Connection *connection = datacenter->getConnectionByType(connectionType, true, 1);
            if (connection != nullptr) {
                connection->setOverrideProxy(proxyCheckInfo->address, proxyCheckInfo->port, proxyCheckInfo->username, proxyCheckInfo->password, proxyCheckInfo->secret);
                connection->suspendConnection();
                proxyCheckInfo->connectionNum = freeConnectionNum;
                TL_ping *request = new TL_ping();
                request->ping_id = proxyCheckInfo->pingId;
                proxyCheckInfo->requestToken = sendRequest(request, nullptr, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin, DEFAULT_DATACENTER_ID, connectionType, true, 0);
                proxyActiveChecks.push_back(std::unique_ptr<ProxyCheckInfo>(proxyCheckInfo));
            } else if (PFS_ENABLED) {
                if (datacenter->isHandshaking(false)) {
                    datacenter->beginHandshake(HandshakeTypeTemp, false);
                }
                proxyCheckQueue.push_back(std::unique_ptr<ProxyCheckInfo>(proxyCheckInfo));
            }
        }
    });
}

#ifdef ANDROID
void ConnectionsManager::useJavaVM(JavaVM *vm, bool useJavaByteBuffers) {
    javaVm = vm;
    if (useJavaByteBuffers) {
        JNIEnv *env = 0;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            DEBUG_E("can't get jnienv");
            exit(1);
        }
        jclass_ByteBuffer = (jclass) env->NewGlobalRef(env->FindClass("java/nio/ByteBuffer"));
        if (jclass_ByteBuffer == 0) {
            DEBUG_E("can't find java ByteBuffer class");
            exit(1);
        }
        jclass_ByteBuffer_allocateDirect = env->GetStaticMethodID(jclass_ByteBuffer, "allocateDirect", "(I)Ljava/nio/ByteBuffer;");
        if (jclass_ByteBuffer_allocateDirect == 0) {
            DEBUG_E("can't find java ByteBuffer allocateDirect");
            exit(1);
        }
        DEBUG_D("using java ByteBuffer");
    }
}
#endif
