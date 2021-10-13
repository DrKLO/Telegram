/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <cassert>
#include <cstdlib>
#include <sys/eventfd.h>
#include <unistd.h>
#include <chrono>
#include <algorithm>
#include <fcntl.h>
#include <memory.h>
#include <openssl/rand.h>
#include <zlib.h>
#include <memory>
#include <string>
#include <cinttypes>
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
jmethodID jclass_ByteBuffer_allocateDirect = nullptr;
#endif

static bool done = false;

ConnectionsManager::ConnectionsManager(int32_t instance) {
    instanceNum = instance;
    if ((epolFd = epoll_create(128)) == -1) {
        if (LOGS_ENABLED) DEBUG_E("unable to create epoll instance");
        exit(1);
    }
    int flags;
    if ((flags = fcntl(epolFd, F_GETFD, NULL)) < 0) {
        if (LOGS_ENABLED) DEBUG_W("fcntl(%d, F_GETFD)", epolFd);
    }
    if (!(flags & FD_CLOEXEC)) {
        if (fcntl(epolFd, F_SETFD, flags | FD_CLOEXEC) == -1) {
            if (LOGS_ENABLED) DEBUG_W("fcntl(%d, F_SETFD)", epolFd);
        }
    }

    if ((epollEvents = new epoll_event[128]) == nullptr) {
        if (LOGS_ENABLED) DEBUG_E("unable to allocate epoll events");
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
            if (LOGS_ENABLED) DEBUG_E("unable to create pipe");
            exit(1);
        }
        flags = fcntl(pipeFd[0], F_GETFL);
        if (flags == -1) {
            if (LOGS_ENABLED) DEBUG_E("fcntl get pipefds[0] failed");
            exit(1);
        }
        if (fcntl(pipeFd[0], F_SETFL, flags | O_NONBLOCK) == -1) {
            if (LOGS_ENABLED) DEBUG_E("fcntl set pipefds[0] failed");
            exit(1);
        }

        flags = fcntl(pipeFd[1], F_GETFL);
        if (flags == -1) {
            if (LOGS_ENABLED) DEBUG_E("fcntl get pipefds[1] failed");
            exit(1);
        }
        if (fcntl(pipeFd[1], F_SETFL, flags | O_NONBLOCK) == -1) {
            if (LOGS_ENABLED) DEBUG_E("fcntl set pipefds[1] failed");
            exit(1);
        }

        auto eventObject = new EventObject(pipeFd, EventObjectTypePipe);

        epoll_event eventMask = {};
        eventMask.events = EPOLLIN;
        eventMask.data.ptr = eventObject;
        if (epoll_ctl(epolFd, EPOLL_CTL_ADD, pipeFd[0], &eventMask) != 0) {
            if (LOGS_ENABLED) DEBUG_E("can't add pipe to epoll");
            exit(1);
        }
    }

    sizeCalculator = new NativeByteBuffer(true);
    networkBuffer = new NativeByteBuffer((uint32_t) READ_BUFFER_SIZE);
    if (networkBuffer == nullptr) {
        if (LOGS_ENABLED) DEBUG_E("unable to allocate read buffer");
        exit(1);
    }

    pthread_mutex_init(&mutex, nullptr);
}

ConnectionsManager::~ConnectionsManager() {
    if (epolFd != 0) {
        close(epolFd);
        epolFd = 0;
    }
    if (pipeFd != nullptr) {
        delete[] pipeFd;
        pipeFd = nullptr;
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
        for (auto iter = events.begin(); iter != events.end();) {
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
    auto timeToPushPing = (int32_t) ((sendingPushPing ? 30000 : nextPingTimeOffset) - llabs(now - lastPushPingTime));
    if (timeToPushPing <= 0) {
        return 1000;
    }
    return timeToPushPing;
}

void ConnectionsManager::checkPendingTasks() {
    int32_t count = INT_MAX;
    while (true) {
        std::function<void()> task;
        pthread_mutex_lock(&mutex);
        if (pendingTasks.empty() || count <= 0) {
            pthread_mutex_unlock(&mutex);
            return;
        }
        if (count == INT_MAX) {
            count = (int32_t) pendingTasks.size();
        } else {
            count--;
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
        auto eventObject = (EventObject *) epollEvents[a].data.ptr;
        eventObject->onEvent(epollEvents[a].events);
    }
    activeConnectionsCopy.resize(activeConnections.size());
    std::copy(std::begin(activeConnections), std::end(activeConnections), std::begin(activeConnectionsCopy));
    for (auto connection : activeConnectionsCopy) {
        connection->checkTimeout(now);
    }

    Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
    if (pushConnectionEnabled) {
        if ((sendingPushPing && llabs(now - lastPushPingTime) >= 30000) || llabs(now - lastPushPingTime) >= nextPingTimeOffset + 10000) {
            lastPushPingTime = 0;
            sendingPushPing = false;
            if (datacenter != nullptr) {
                Connection *connection = datacenter->getPushConnection(false);
                if (connection != nullptr) {
                    connection->suspendConnection();
                }
            }
            if (LOGS_ENABLED) DEBUG_D("push ping timeout");
        }
        if (llabs(now - lastPushPingTime) >= nextPingTimeOffset) {
            if (LOGS_ENABLED) DEBUG_D("time for push ping");
            lastPushPingTime = now;
            uint8_t offset;
            RAND_bytes(&offset, 1);
            nextPingTimeOffset = 60000 * 3 + (offset % 40) - 20;
            if (datacenter != nullptr) {
                sendPing(datacenter, true);
            }
        }
    }

    if (lastPauseTime != 0 && llabs(now - lastPauseTime) >= nextSleepTimeout) {
        bool dontSleep = !requestingSaltsForDc.empty();
        if (!dontSleep) {
            for (auto & runningRequest : runningRequests) {
                Request *request = runningRequest.get();
                if (request->connectionType & ConnectionTypeDownload || request->connectionType & ConnectionTypeUpload) {
                    dontSleep = true;
                    break;
                }
            }
        }
        if (!dontSleep) {
            for (auto & iter : requestsQueue) {
                Request *request = iter.get();
                if (request->connectionType & ConnectionTypeDownload || request->connectionType & ConnectionTypeUpload) {
                    dontSleep = true;
                    break;
                }
            }
        }
        if (!dontSleep) {
            if (!networkPaused) {
                if (LOGS_ENABLED) DEBUG_D("pausing network and timers by sleep time = %d", nextSleepTimeout);
                for (auto & dc : datacenters) {
                    dc.second->suspendConnections(false);
                }
            }
            networkPaused = true;
            return;
        } else {
            lastPauseTime = now;
            if (LOGS_ENABLED) DEBUG_D("don't sleep because of salt, upload or download request");
        }
    }
    if (networkPaused) {
        networkPaused = false;
        for (auto & dc : datacenters) {
            if (dc.second->isHandshaking(false)) {
                dc.second->createGenericConnection()->connect();
            } else if (dc.second->isHandshaking(true)) {
                dc.second->createGenericMediaConnection()->connect();
            }
        }
        if (LOGS_ENABLED) DEBUG_D("resume network and timers");
    }

    if (delegate != nullptr) {
        delegate->onUpdate(instanceNum);
    }
    if (datacenter != nullptr) {
        if (datacenter->hasAuthKey(ConnectionTypeGeneric, 1)) {
            if (llabs(now - lastPingTime) >= (testBackend ? 2000 : 19000)) {
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
    for (auto iter = events.begin(); iter != events.end(); iter++) {
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
    if (LOGS_ENABLED) DEBUG_D("network thread started");
    auto networkManager = (ConnectionsManager *) (data);
#ifdef ANDROID
    javaVm->AttachCurrentThread(&jniEnv[networkManager->instanceNum], nullptr);
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
        if (LOGS_ENABLED) DEBUG_D("config version = %u", version);
        if (version <= configVersion) {
            testBackend = buffer->readBool(nullptr);
            if (version >= 3) {
                clientBlocked = buffer->readBool(nullptr);
            }
            if (version >= 4) {
                lastInitSystemLangcode = buffer->readString(nullptr);
            }
            if (buffer->readBool(nullptr)) {
                currentDatacenterId = buffer->readUint32(nullptr);
                timeDifference = buffer->readInt32(nullptr);
                lastDcUpdateTime = buffer->readInt32(nullptr);
                pushSessionId = buffer->readInt64(nullptr);
                if (version >= 2) {
                    registeredForInternalPush = buffer->readBool(nullptr);
                }
                if (version >= 5) {
                    int32_t lastServerTime = buffer->readInt32(nullptr);
                    int32_t currentTime = getCurrentTime();
                    if (currentTime > timeDifference && currentTime < lastServerTime) {
                        timeDifference += (lastServerTime - currentTime);
                    }
                }

                if (LOGS_ENABLED) DEBUG_D("current dc id = %u, time difference = %d, registered for push = %d", currentDatacenterId, timeDifference, (int32_t) registeredForInternalPush);

                uint32_t count = buffer->readUint32(nullptr);
                for (uint32_t a = 0; a < count; a++) {
                    sessionsToDestroy.push_back(buffer->readInt64(nullptr));
                }

                count = buffer->readUint32(nullptr);
                for (uint32_t a = 0; a < count; a++) {
                    auto datacenter = new Datacenter(instanceNum, buffer);
                    datacenters[datacenter->getDatacenterId()] = datacenter;
                    if (LOGS_ENABLED) DEBUG_D("datacenter(%p) %u loaded (hasAuthKey = %d, 0x%" PRIx64 ")", datacenter, datacenter->getDatacenterId(), (int) datacenter->hasPermanentAuthKey(), datacenter->getPermanentAuthKeyId());
                }
            }
        }
        buffer->reuse();
    }

    if (currentDatacenterId != 0 && currentUserId) {
        Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
        if (datacenter == nullptr || !datacenter->hasPermanentAuthKey()) {
            if (datacenter != nullptr) {
                if (LOGS_ENABLED) DEBUG_D("reset authorization because of dc %d", currentDatacenterId);
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

    if ((!datacenters.empty() && currentDatacenterId == 0) || pushSessionId == 0) {
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
    buffer->writeString(lastInitSystemLangcode);
    Datacenter *currentDatacenter = getDatacenterWithId(currentDatacenterId);
    buffer->writeBool(currentDatacenter != nullptr);
    if (currentDatacenter != nullptr) {
        buffer->writeInt32(currentDatacenterId);
        buffer->writeInt32(timeDifference);
        buffer->writeInt32(lastDcUpdateTime);
        buffer->writeInt64(pushSessionId);
        buffer->writeBool(registeredForInternalPush);
        buffer->writeInt32(getCurrentTime());

        std::vector<int64_t> sessions;
        currentDatacenter->getSessions(sessions);

        auto count = (uint32_t) sessions.size();
        buffer->writeInt32(count);
        for (uint32_t a = 0; a < count; a++) {
            buffer->writeInt64(sessions[a]);
        }
        count = (uint32_t) datacenters.size();
        buffer->writeInt32(count);
        for (auto & datacenter : datacenters) {
            datacenter.second->serializeToStream(buffer);
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
        if (LOGS_ENABLED) DEBUG_E("can't decompress data");
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
            if (LOGS_ENABLED) DEBUG_E("can't decompress data");
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
        if (LOGS_ENABLED) DEBUG_E("%s: deflateInit2() failed with error %i", __PRETTY_FUNCTION__, retCode);
        return nullptr;
    }

    NativeByteBuffer *result = BuffersStorage::getInstance().getFreeBuffer(buffer->limit());
    stream.avail_out = result->limit();
    stream.next_out = result->bytes();
    retCode = deflate(&stream, Z_FINISH);
    if ((retCode != Z_OK) && (retCode != Z_STREAM_END)) {
        if (LOGS_ENABLED) DEBUG_E("%s: deflate() failed with error %i", __PRETTY_FUNCTION__, retCode);
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
    clock_gettime(CLOCK_BOOTTIME, &timeSpecMonotonic);
    return (int64_t) timeSpecMonotonic.tv_sec * 1000 + (int64_t) timeSpecMonotonic.tv_nsec / 1000000;
}

int32_t ConnectionsManager::getCurrentTime() {
    return (int32_t) (getCurrentTimeMillis() / 1000) + timeDifference;
}

uint32_t ConnectionsManager::getCurrentDatacenterId() {
    Datacenter *datacenter = getDatacenterWithId(DEFAULT_DATACENTER_ID);
    return datacenter != nullptr ? datacenter->getDatacenterId() : INT_MAX;
}

bool ConnectionsManager::isTestBackend() {
    return testBackend;
}

int32_t ConnectionsManager::getTimeDifference() {
    return timeDifference;
}

int64_t ConnectionsManager::generateMessageId() {
    auto messageId = (int64_t) ((((double) getCurrentTimeMillis() + ((double) timeDifference) * 1000) * 4294967296.0) / 1000.0);
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

void ConnectionsManager::cleanUp(bool resetKeys, int32_t datacenterId) {
    scheduleTask([&, resetKeys, datacenterId] {
        for (auto iter = requestsQueue.begin(); iter != requestsQueue.end();) {
            Request *request = iter->get();
            if (datacenterId != -1) {
                Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
                if (requestDatacenter != nullptr && requestDatacenter->getDatacenterId() != datacenterId) {
                    iter++;
                    continue;
                }
            }
            if (request->requestFlags & RequestFlagWithoutLogin) {
                iter++;
                continue;
            }
            if (request->onCompleteRequestCallback != nullptr) {
                auto error = new TL_error();
                error->code = -1000;
                error->text = "";
                request->onComplete(nullptr, error, 0, 0);
                delete error;
            }
            iter = requestsQueue.erase(iter);
        }
        for (auto iter = runningRequests.begin(); iter != runningRequests.end();) {
            Request *request = iter->get();
            if (datacenterId != -1) {
                Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
                if (requestDatacenter != nullptr && requestDatacenter->getDatacenterId() != datacenterId) {
                    iter++;
                    continue;
                }
            }
            if (request->requestFlags & RequestFlagWithoutLogin) {
                iter++;
                continue;
            }
            if (request->onCompleteRequestCallback != nullptr) {
                auto error = new TL_error();
                error->code = -1000;
                error->text = "";
                request->onComplete(nullptr, error, 0, 0);
                delete error;
            }
            iter = runningRequests.erase(iter);
        }
        quickAckIdToRequestIds.clear();

        for (auto & datacenter : datacenters) {
            if (datacenterId != -1 && datacenter.second->getDatacenterId() != datacenterId) {
                continue;
            }
            if (resetKeys) {
                datacenter.second->clearAuthKey(HandshakeTypeAll);
            }
            datacenter.second->recreateSessions(HandshakeTypeAll);
            datacenter.second->authorized = false;
        }
        if (datacenterId == -1) {
            sessionsToDestroy.clear();
            currentUserId = 0;
            registeredForInternalPush = false;
        }
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
            sendingPing = false;
            if (!connection->isSuspended() && (proxyAddress.empty() || connection->hasTlsHashMismatch())) {
                if (reason == 2) {
                    disconnectTimeoutAmount += connection->getTimeout();
                } else {
                    disconnectTimeoutAmount += 4;
                }
                if (LOGS_ENABLED) DEBUG_D("increase disconnect timeout %d", disconnectTimeoutAmount);
                int32_t maxTimeout;
                if (clientBlocked) {
                    maxTimeout = 5;
                } else {
                    maxTimeout = 20;
                }
                if (disconnectTimeoutAmount >= maxTimeout) {
                    if (!connection->hasUsefullData()) {
                        if (LOGS_ENABLED) DEBUG_D("start requesting new address and port due to timeout reach");
                        requestingSecondAddressByTlsHashMismatch = connection->hasTlsHashMismatch();
                        if (requestingSecondAddressByTlsHashMismatch) {
                            requestingSecondAddress = 1;
                        } else {
                            requestingSecondAddress = 0;
                        }
                        delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
                    } else {
                        if (LOGS_ENABLED) DEBUG_D("connection has usefull data, don't request anything");
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
        if (LOGS_ENABLED) DEBUG_D("connection(%p) push connection closed", connection);
        sendingPushPing = false;
        lastPushPingTime = getCurrentTimeMonotonicMillis() - nextPingTimeOffset + 4000;
    } else if (connection->getConnectionType() == ConnectionTypeProxy) {
        scheduleTask([&, connection] {
            for (auto iter = proxyActiveChecks.begin(); iter != proxyActiveChecks.end(); iter++) {
                ProxyCheckInfo *proxyCheckInfo = iter->get();
                if (proxyCheckInfo->connectionNum == connection->getConnectionNum()) {
                    bool found = false;
                    for (auto iter2 = runningRequests.begin(); iter2 != runningRequests.end(); iter2++) {
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
    ConnectionType connectionType = connection->getConnectionType();
    if ((connectionType == ConnectionTypeGeneric || connectionType == ConnectionTypeGenericMedia) && datacenter->isHandshakingAny()) {
        datacenter->onHandshakeConnectionConnected(connection);
        return;
    }

    if (datacenter->hasAuthKey(connectionType, 1)) {
        if (connectionType == ConnectionTypePush) {
            sendingPushPing = false;
            lastPushPingTime = getCurrentTimeMonotonicMillis();
            sendPing(datacenter, true);
        } else {
            if (connectionType == ConnectionTypeGeneric && datacenter->getDatacenterId() == currentDatacenterId) {
                sendingPing = false;
            }
            if (networkPaused && lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
            }
            processRequestQueue(connection->getConnectionType(), datacenter->getDatacenterId());
        }
    }
}

void ConnectionsManager::onConnectionQuickAckReceived(Connection *connection, int32_t ack) {
    auto iter = quickAckIdToRequestIds.find(ack);
    if (iter == quickAckIdToRequestIds.end()) {
        return;
    }
    for (auto & runningRequest : runningRequests) {
        Request *request = runningRequest.get();
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
            if (LOGS_ENABLED) DEBUG_D("mtproto noop");
        } else if (code == -1) {
            int32_t ackId = data->readInt32(&error);
            if (!error) {
                onConnectionQuickAckReceived(connection, ackId & (~(1 << 31)));
            }
        } else {
            Datacenter *datacenter = connection->getDatacenter();
            if (LOGS_ENABLED) DEBUG_W("mtproto error = %d", code);
            if (code == -444 && connection->getConnectionType() == ConnectionTypeGeneric && !proxyAddress.empty() && !proxySecret.empty()) {
                if (delegate != nullptr) {
                    delegate->onProxyError(instanceNum);
                }
            } else if (code == -404 && (datacenter->isCdnDatacenter || PFS_ENABLED)) {
                if (!datacenter->isHandshaking(connection->isMediaConnection)) {
                    datacenter->clearAuthKey(connection->isMediaConnection ? HandshakeTypeMediaTemp : HandshakeTypeTemp);
                    datacenter->beginHandshake(connection->isMediaConnection ? HandshakeTypeMediaTemp : HandshakeTypeTemp, true);
                    if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) reset auth key due to -404 error", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType());
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
                if (LOGS_ENABLED) DEBUG_E("connection(%p) received incorrect message length", connection);
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
            lastProtocolUsefullData = true;
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
        if (length < 24 + 32 || (!connection->allowsCustomPadding() && (length - 24) % 16 != 0) || !datacenter->decryptServerResponse(keyId, data->bytes() + mark + 8, data->bytes() + mark + 24, length - 24, connection)) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p) unable to decrypt server response", connection);
            connection->reconnect();
            return;
        }
        data->position(mark + 24);

        int64_t messageServerSalt = data->readInt64(&error);
        int64_t messageSessionId = data->readInt64(&error);

        if (messageSessionId != connection->getSessionId()) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p) received invalid message session id (0x%" PRIx64 " instead of 0x%" PRIx64 ")", connection, (uint64_t) messageSessionId, (uint64_t) connection->getSessionId());
            return;
        }

        int64_t messageId = data->readInt64(&error);
        int32_t messageSeqNo = data->readInt32(&error);
        uint32_t messageLength = data->readUint32(&error);

        int32_t processedStatus = connection->isMessageIdProcessed(messageId);

        if (messageSeqNo % 2 != 0) {
            connection->addMessageToConfirm(messageId);
        }

        TLObject *object = nullptr;

        if (processedStatus != 1) {
            deserializingDatacenter = datacenter;
            object = TLdeserialize(nullptr, messageLength, data);
            if (processedStatus == 2) {
                if (object == nullptr) {
                    connection->recreateSession();
                    connection->reconnect();
                    return;
                } else {
                    delete object;
                    object = nullptr;
                }
            }
        }
        if (!processedStatus) {
            if (object != nullptr) {
                lastProtocolUsefullData = true;
                connection->setHasUsefullData();
                if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) received object %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
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

bool ConnectionsManager::hasPendingRequestsForConnection(Connection *connection) {
    ConnectionType type = connection->getConnectionType();
    if (type == ConnectionTypeGeneric || type == ConnectionTypeTemp || type == ConnectionTypeGenericMedia) {
        Datacenter *datacenter = connection->getDatacenter();
        int8_t num = connection->getConnectionNum();
        uint32_t token = connection->getConnectionToken();
        if (type == ConnectionTypeGeneric) {
            if (sendingPing && type == ConnectionTypeGeneric && datacenter->getDatacenterId() == currentDatacenterId) {
                return true;
            } else if (datacenter->isHandshaking(false)) {
                return true;
            }
        } else if (type == ConnectionTypeGenericMedia) {
            if (datacenter->isHandshaking(true)) {
                return true;
            }
        }
        for (auto & runningRequest : runningRequests) {
            Request *request = runningRequest.get();
            auto connectionNum = (uint8_t) (request->connectionType >> 16);
            auto connectionType = (ConnectionType) (request->connectionType & 0x0000ffff);
            if ((connectionType == type && connectionNum == num) || request->connectionToken == token) {
                return true;
            }
        }
        return false;
    }
    return true;
}

TLObject *ConnectionsManager::getRequestWithMessageId(int64_t messageId) {
    for (auto & runningRequest : runningRequests) {
        Request *request = runningRequest.get();
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
        delete object;
        data->position(position);
        return nullptr;
    }

    if (object == nullptr) {
        if (request != nullptr) {
            auto apiRequest = dynamic_cast<TL_api_request *>(request);
            if (apiRequest != nullptr) {
                object = apiRequest->deserializeResponse(data, bytes, instanceNum, error);
                if (LOGS_ENABLED) DEBUG_D("api request constructor 0x%x, don't parse", constructor);
            } else {
                object = request->deserializeResponse(data, constructor, instanceNum, error);
                if (object != nullptr && error) {
                    delete object;
                    object = nullptr;
                }
            }
        } else {
            if (LOGS_ENABLED) DEBUG_D("not found request to parse constructor 0x%x", constructor);
        }
    }
    if (object == nullptr) {
        data->position(position);
    }
    return object;
}

void ConnectionsManager::processServerResponse(TLObject *message, int64_t messageId, int32_t messageSeqNo, int64_t messageSalt, Connection *connection, int64_t innerMsgId, int64_t containerMessageId) {
    const std::type_info &typeInfo = typeid(*message);

    if (LOGS_ENABLED) DEBUG_D("process server response %p - %s", message, typeInfo.name());
    auto timeMessage = (int64_t) ((messageId != 0 ? messageId : innerMsgId) / 4294967296.0 * 1000);

    Datacenter *datacenter = connection->getDatacenter();

    if (typeInfo == typeid(TL_new_session_created)) {
        auto response = (TL_new_session_created *) message;

        if (!connection->isSessionProcessed(response->unique_id)) {
            if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) new session created (first message id: 0x%" PRIx64 ", server salt: 0x%" PRIx64 ", unique id: 0x%" PRIx64 ")", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), (uint64_t) response->first_msg_id, (uint64_t) response->server_salt, (uint64_t) response->unique_id);

            std::unique_ptr<TL_future_salt> salt = std::make_unique<TL_future_salt>();
            salt->valid_until = salt->valid_since = getCurrentTime();
            salt->valid_until += 30 * 60;
            salt->salt = response->server_salt;
            datacenter->addServerSalt(salt, Connection::isMediaConnectionType(connection->getConnectionType()));

            for (auto & runningRequest : runningRequests) {
                Request *request = runningRequest.get();
                Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
                if (request->messageId < response->first_msg_id && request->connectionType & connection->getConnectionType() && requestDatacenter != nullptr && requestDatacenter->getDatacenterId() == datacenter->getDatacenterId()) {
                    if (LOGS_ENABLED) DEBUG_D("clear request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
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
        auto response = (TL_msg_container *) message;
        size_t count = response->messages.size();
        if (LOGS_ENABLED) DEBUG_D("received container with %d items", (int32_t) count);
        for (uint32_t a = 0; a < count; a++) {
            TL_message *innerMessage = response->messages[a].get();
            int64_t innerMessageId = innerMessage->msg_id;
            if (innerMessage->seqno % 2 != 0) {
                connection->addMessageToConfirm(innerMessageId);
            }
            int32_t processedStatus = connection->isMessageIdProcessed(innerMessageId);
            if (processedStatus == 2) {
                if (innerMessage->unparsedBody != nullptr) {
                    connection->recreateSession();
                    connection->reconnect();
                    return;
                }
                processedStatus = 0;
            }
            if (processedStatus) {
                if (LOGS_ENABLED) DEBUG_D("inner message %d id 0x%" PRIx64 " already processed", a, innerMessageId);
                continue;
            }
            if (innerMessage->unparsedBody != nullptr) {
                if (LOGS_ENABLED) DEBUG_D("inner message %d id 0x%" PRIx64 " is unparsed", a, innerMessageId);
                if (delegate != nullptr) {
                    delegate->onUnparsedMessageReceived(0, innerMessage->unparsedBody.get(), connection->getConnectionType(), instanceNum);
                }
            } else {
                if (LOGS_ENABLED) DEBUG_D("inner message %d id 0x%" PRIx64 " process", a, innerMessageId);
                processServerResponse(innerMessage->body.get(), 0, innerMessage->seqno, messageSalt, connection, innerMessageId, messageId);
            }
            connection->addProcessedMessageId(innerMessageId);
        }
    } else if (typeInfo == typeid(TL_pong)) {
        if (connection->getConnectionType() == ConnectionTypePush) {
            if (!registeredForInternalPush) {
                registerForInternalPushUpdates();
            }
            if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) received push ping", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType());
            sendingPushPing = false;
        } else {
            auto response = (TL_pong *) message;
            if (response->ping_id >= 2000000) {
                for (auto iter = proxyActiveChecks.begin(); iter != proxyActiveChecks.end(); iter++) {
                    ProxyCheckInfo *proxyCheckInfo = iter->get();
                    if (proxyCheckInfo->pingId == response->ping_id) {
                        for (auto iter2 = runningRequests.begin(); iter2 != runningRequests.end(); iter2++) {
                            Request *request = iter2->get();
                            if (request->requestToken == proxyCheckInfo->requestToken) {
                                int64_t ping = llabs(getCurrentTimeMonotonicMillis() - request->startTimeMillis);
                                if (LOGS_ENABLED) DEBUG_D("got ping response for request %p, %" PRId64, request->rawRequest, ping);
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
                            scheduleCheckProxyInternal(proxyCheckInfo);
                        }
                        break;
                    }
                }
            } else if (response->ping_id == lastPingId) {
                int32_t diff = (int32_t) (getCurrentTimeMonotonicMillis() / 1000) - pingTime;

                if (abs(diff) < 10) {
                    currentPingTime = (diff + currentPingTime) / 2;
                    if (messageId != 0) {
                        timeDifference = (int32_t) ((timeMessage - getCurrentTimeMillis()) / 1000 - currentPingTime / 2);
                    }
                }
                sendingPing = false;
            }
        }
    } else if (typeInfo == typeid(TL_future_salts)) {
        auto response = (TL_future_salts *) message;
        int64_t requestMid = response->req_msg_id;
        for (auto iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
            Request *request = iter->get();
            if (request->respondsToMessageId(requestMid)) {
                request->onComplete(response, nullptr, connection->currentNetworkType, timeMessage);
                request->completed = true;
                runningRequests.erase(iter);
                break;
            }
        }
    } else if (dynamic_cast<DestroySessionRes *>(message)) {
        auto response = (DestroySessionRes *) message;
        if (LOGS_ENABLED) DEBUG_D("destroyed session 0x%" PRIx64 " (%s)", (uint64_t) response->session_id, typeInfo == typeid(TL_destroy_session_ok) ? "ok" : "not found");
    } else if (typeInfo == typeid(TL_rpc_result)) {
        auto response = (TL_rpc_result *) message;
        int64_t resultMid = response->req_msg_id;
        if (resultMid == lastInvokeAfterMessageId) {
            lastInvokeAfterMessageId = 0;
        }

        bool hasResult = response->result != nullptr;
        bool ignoreResult = false;
        if (hasResult) {
            TLObject *object = response->result.get();
            if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) received rpc_result with %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
        }
        RpcError *error = hasResult ? dynamic_cast<RpcError *>(response->result.get()) : nullptr;
        if (error != nullptr) {
            if (LOGS_ENABLED) DEBUG_E("connection(%p, account%u, dc%u, type %d) rpc error %d: %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), error->error_code, error->error_message.c_str());
            if (error->error_code == 303) {
                uint32_t migrateToDatacenterId = DEFAULT_DATACENTER_ID;

                static std::vector<std::string> migrateErrors = {"NETWORK_MIGRATE_", "PHONE_MIGRATE_", "USER_MIGRATE_"};

                size_t count = migrateErrors.size();
                for (uint32_t a = 0; a < count; a++) {
                    std::string &possibleError = migrateErrors[a];
                    if (error->error_message.find(possibleError) != std::string::npos) {
                        std::string num = error->error_message.substr(possibleError.size(), error->error_message.size() - possibleError.size());
                        auto val = (uint32_t) atoi(num.c_str());
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
            for (auto iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if (!request->respondsToMessageId(resultMid)) {
                    continue;
                }
                if (LOGS_ENABLED) DEBUG_D("got response for request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
                bool discardResponse = false;
                bool isError = false;
                bool allowInitConnection = true;

                if (request->onCompleteRequestCallback != nullptr) {
                    TL_error *implicitError = nullptr;
                    NativeByteBuffer *unpacked_data = nullptr;
                    TLObject *result = response->result.get();
                    if (typeid(*result) == typeid(TL_gzip_packed)) {
                        auto innerResponse = (TL_gzip_packed *) result;
                        unpacked_data = decompressGZip(innerResponse->packed_data.get());
                        TLObject *object = TLdeserialize(request->rawRequest, unpacked_data->limit(), unpacked_data);
                        if (object != nullptr) {
                            response->result = std::unique_ptr<TLObject>(object);
                        } else {
                            response->result = std::unique_ptr<TLObject>(nullptr);
                        }
                    }

                    hasResult = response->result != nullptr;
                    error = hasResult ? dynamic_cast<RpcError *>(response->result.get()) : nullptr;
                    TL_error *error2 = hasResult ? dynamic_cast<TL_error *>(response->result.get()) : nullptr;
                    if (error != nullptr) {
                        allowInitConnection = false;
                        static std::string authRestart = "AUTH_RESTART";
                        static std::string authKeyPermEmpty = "AUTH_KEY_PERM_EMPTY";
                        static std::string workerBusy = "WORKER_BUSY_TOO_LONG_RETRY";
                        bool processEvenFailed = error->error_code == 500 && error->error_message.find(authRestart) != std::string::npos;
                        bool isWorkerBusy = error->error_code == 500 && error->error_message.find(workerBusy) != std::string::npos;
                        if (LOGS_ENABLED) DEBUG_E("request %p rpc error %d: %s", request, error->error_code, error->error_message.c_str());

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
                                static std::string waitFailed = "MSG_WAIT_FAILED";
                                static std::string waitTimeout = "MSG_WAIT_TIMEOUT";
                                if (error->error_message.find(waitFailed) != std::string::npos) {
                                    request->startTime = 0;
                                    request->startTimeMillis = 0;
                                    request->requestFlags |= RequestFlagResendAfter;
                                } else {
                                    if (isWorkerBusy) {
                                        request->minStartTime = 0;
                                    } else {
                                        request->minStartTime = request->startTime + (request->serverFailureCount > 10 ? 10 : request->serverFailureCount);
                                    }
                                    request->serverFailureCount++;
                                }
                                discardResponse = true;
                            } else if (error->error_code == 420) {
                                int32_t waitTime = 2;
                                static std::string floodWait = "FLOOD_WAIT_";
                                static std::string slowmodeWait = "SLOWMODE_WAIT_";
                                discardResponse = true;
                                if (error->error_message.find(floodWait) != std::string::npos) {
                                    std::string num = error->error_message.substr(floodWait.size(), error->error_message.size() - floodWait.size());
                                    waitTime = atoi(num.c_str());
                                    if (waitTime <= 0) {
                                        waitTime = 2;
                                    }
                                } else if (error->error_message.find(slowmodeWait) != std::string::npos) {
                                    std::string num = error->error_message.substr(slowmodeWait.size(), error->error_message.size() - slowmodeWait.size());
                                    waitTime = atoi(num.c_str());
                                    if (waitTime <= 0) {
                                        waitTime = 2;
                                    }
                                    discardResponse = false;
                                }
                                request->failedByFloodWait = waitTime;
                                request->startTime = 0;
                                request->startTimeMillis = 0;
                                request->minStartTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000 + waitTime);
                            } else if (error->error_code == 400) {
                                static std::string waitFailed = "MSG_WAIT_FAILED";
                                static std::string bindFailed = "ENCRYPTED_MESSAGE_INVALID";
                                static std::string waitTimeout = "MSG_WAIT_TIMEOUT";
                                if (error->error_message.find(waitTimeout) != std::string::npos || error->error_message.find(waitFailed) != std::string::npos) {
                                    discardResponse = true;
                                    request->startTime = 0;
                                    request->startTimeMillis = 0;
                                    request->requestFlags |= RequestFlagResendAfter;
                                } else if (error->error_message.find(bindFailed) != std::string::npos && typeid(*request->rawRequest) == typeid(TL_auth_bindTempAuthKey)) {
                                    int datacenterId;
                                    if (delegate != nullptr && getDatacenterWithId(DEFAULT_DATACENTER_ID) == datacenter) {
                                        delegate->onLogout(instanceNum);
                                        datacenterId = -1;
                                    } else {
                                        datacenterId = datacenter->getDatacenterId();
                                    }
                                    cleanUp(true, datacenterId);
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
                            if (LOGS_ENABLED) DEBUG_E("rawRequest is null");
                            implicitError = new TL_error();
                            implicitError->code = -1000;
                            implicitError->text = "";
                        }
                    }

                    if (!discardResponse) {
                        if (implicitError != nullptr || error2 != nullptr) {
                            isError = true;
                            request->onComplete(nullptr, implicitError != nullptr ? implicitError : error2, connection->currentNetworkType, timeMessage);
                            delete error2;
                        } else {
                            request->onComplete(response->result.get(), nullptr, connection->currentNetworkType, timeMessage);
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
                                    cleanUp(false, -1);
                                }
                            } else {
                                datacenter->authorized = false;
                                saveConfig();
                                discardResponse = true;
                                if (request->connectionType & ConnectionTypeDownload || request->connectionType & ConnectionTypeUpload) {
                                    retryRequestsFromDatacenter = datacenter->getDatacenterId();
                                    retryRequestsConnections = request->connectionType;
                                }
                            }
                        } else if (currentUserId == 0 && implicitError->code == 406) {
                            static std::string authKeyDuplicated = "AUTH_KEY_DUPLICATED";
                            if (implicitError->text.find(authKeyDuplicated) != std::string::npos) {
                                cleanUp(true, datacenter->getDatacenterId());
                            }
                        }
                    }

                    if (unpacked_data != nullptr) {
                        unpacked_data->reuse();
                    }
                    delete implicitError;
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
                            if (LOGS_ENABLED) DEBUG_D("dc%d init connection completed", datacenter->getDatacenterId());
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

        if (retryRequestsFromDatacenter != DEFAULT_DATACENTER_ID - 1) {
            processRequestQueue(retryRequestsConnections, retryRequestsFromDatacenter);
        } else {
            processRequestQueue(0, 0);
        }
    } else if (typeInfo == typeid(TL_msgs_ack)) {

    } else if (typeInfo == typeid(TL_bad_msg_notification)) {
        auto result = (TL_bad_msg_notification *) message;
        if (LOGS_ENABLED) DEBUG_E("bad message notification %d for messageId 0x%" PRIx64 ", seqno %d", result->error_code, result->bad_msg_id, result->bad_msg_seqno);
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
                    auto time = (int64_t) (messageId / 4294967296.0 * 1000);
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
                for (auto & runningRequest : runningRequests) {
                    Request *request = runningRequest.get();
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
        bool media = Connection::isMediaConnectionType(connection->getConnectionType());
        requestSaltsForDatacenter(datacenter, media, connection->getConnectionType() == ConnectionTypeTemp);
        if (messageId != 0) {
            auto time = (int64_t) (messageId / 4294967296.0 * 1000);
            int64_t currentTime = getCurrentTimeMillis();
            timeDifference = (int32_t) ((time - currentTime) / 1000 - currentPingTime / 2);
            lastOutgoingMessageId = (messageId > lastOutgoingMessageId ? messageId : lastOutgoingMessageId);
        }
        if ((connection->getConnectionType() & ConnectionTypeDownload) == 0 || !datacenter->containsServerSalt(messageSalt, media)) {
            auto response = (TL_bad_server_salt *) message;
            int64_t resultMid = response->bad_msg_id;
            if (resultMid != 0) {
                bool beginHandshake = false;
                for (auto & runningRequest : runningRequests) {
                    Request *request = runningRequest.get();
                    if (!beginHandshake && request->datacenterId == datacenter->getDatacenterId() && typeid(*request->rawRequest) == typeid(TL_auth_bindTempAuthKey) && request->respondsToMessageId(response->bad_msg_id)) {
                        beginHandshake = true;
                    }
                    if ((request->connectionType & ConnectionTypeDownload) == 0) {
                        continue;
                    }
                    Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
                    if (requestDatacenter != nullptr && requestDatacenter->getDatacenterId() == datacenter->getDatacenterId()) {
                        request->retryCount = 0;
                        request->failedBySalt = true;
                    }
                }
                if (beginHandshake) {
                    datacenter->beginHandshake(HandshakeTypeCurrent, false);
                }
            }

            datacenter->clearServerSalts(media);

            std::unique_ptr<TL_future_salt> salt = std::make_unique<TL_future_salt>();
            salt->valid_until = salt->valid_since = getCurrentTime();
            salt->valid_until += 30 * 60;
            salt->salt = messageSalt;
            datacenter->addServerSalt(salt, media);
            saveConfig();

            if (datacenter->hasAuthKey(ConnectionTypeGeneric, 1)) {
                processRequestQueue(AllConnectionTypes, datacenter->getDatacenterId());
            }
        }
    } else if (typeInfo == typeid(MsgsStateInfo)) {
        auto response = (MsgsStateInfo *) message;
        if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) got %s for messageId 0x%" PRIx64, connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeInfo.name(), response->req_msg_id);

        auto mIter = resendRequests.find(response->req_msg_id);
        if (mIter != resendRequests.end()) {
            if (LOGS_ENABLED) DEBUG_D("found resend for messageId 0x%" PRIx64, mIter->second);
            connection->addMessageToConfirm(mIter->second);
            for (auto & runningRequest : runningRequests) {
                Request *request = runningRequest.get();
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
        auto response = (MsgDetailedInfo *) message;

        bool requestResend = false;
        bool confirm = true;

        if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) got %s for messageId 0x%" PRIx64, connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeInfo.name(), response->msg_id);
        if (typeInfo == typeid(TL_msg_detailed_info)) {
            for (auto & runningRequest : runningRequests) {
                Request *request = runningRequest.get();
                if (request->respondsToMessageId(response->msg_id)) {
                    if (request->completed) {
                        break;
                    }
                    if (LOGS_ENABLED) DEBUG_D("got TL_msg_detailed_info for rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
                    auto currentTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000);
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
            auto request = new TL_msg_resend_req();
            request->msg_ids.push_back(response->answer_msg_id);
            auto networkMessage = new NetworkMessage();
            networkMessage->message = std::make_unique<TL_message>();
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
        auto response = (TL_gzip_packed *) message;
        NativeByteBuffer *data = decompressGZip(response->packed_data.get());
        TLObject *object = TLdeserialize(getRequestWithMessageId(messageId), data->limit(), data);
        if (object != nullptr) {
            if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) received object %s", connection, instanceNum, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
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
                if (LOGS_ENABLED) DEBUG_D("received internal push: wakeup network in background");
            } else if (lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMonotonicMillis();
                if (LOGS_ENABLED) DEBUG_D("received internal push: reset sleep timeout");
            } else {
                if (LOGS_ENABLED) DEBUG_D("received internal push");
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
    auto request = new TL_ping_delay_disconnect();
    request->ping_id = ++lastPingId;
    if (usePushConnection) {
        request->disconnect_delay = 60 * 7;
    } else {
        request->disconnect_delay = testBackend ? 10 : 35;
        pingTime = (int32_t) (getCurrentTimeMonotonicMillis() / 1000);
    }

    auto networkMessage = new NetworkMessage();
    networkMessage->message = std::make_unique<TL_message>();
    networkMessage->message->msg_id = generateMessageId();
    networkMessage->message->bytes = request->getObjectSize();
    networkMessage->message->body = std::unique_ptr<TLObject>(request);
    networkMessage->message->seqno = connection->generateMessageSeqNo(false);

    std::vector<std::unique_ptr<NetworkMessage>> array;
    array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    NativeByteBuffer *transportData = datacenter->createRequestsData(array, nullptr, connection, false);
    if (usePushConnection) {
        if (LOGS_ENABLED) DEBUG_D("dc%d send ping to push connection", datacenter->getDatacenterId());
        sendingPushPing = true;
    } else {
        sendingPing = true;
    }
    connection->sendData(transportData, false, true);
}

uint8_t ConnectionsManager::getIpStratagy() {
    return ipStrategy;
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
            datacenter->addAddressAndPort("95.161.76.100", 443, 0, "");
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
    auto iter = std::find(activeConnections.begin(), activeConnections.end(), connection);
    if (iter != activeConnections.end()) {
        activeConnections.erase(iter);
    }
}

int32_t ConnectionsManager::sendRequestInternal(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate) {
    if (!currentUserId && !(flags & RequestFlagWithoutLogin)) {
        if (LOGS_ENABLED) DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        return 0;
    }
    auto request = new Request(instanceNum, lastRequestToken++, connetionType, flags, datacenterId, onComplete, onQuickAck, nullptr);
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
        if (LOGS_ENABLED) DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        return 0;
    }
    if (requestToken == 0) {
        requestToken = lastRequestToken++;
    }
    scheduleTask([&, requestToken, object, onComplete, onQuickAck, flags, datacenterId, connetionType, immediate] {
        auto request = new Request(instanceNum, requestToken, connetionType, flags, datacenterId, onComplete, onQuickAck, nullptr);
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
        if (LOGS_ENABLED) DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        JNIEnv *env = 0;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            if (LOGS_ENABLED) DEBUG_E("can't get jnienv");
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
        if (LOGS_ENABLED) DEBUG_D("send request %p - %s", object, typeid(*object).name());
        auto request = new Request(instanceNum, requestToken, connetionType, flags, datacenterId, onComplete, onQuickAck, onWriteToSocket);
        request->rawRequest = object;
        request->ptr1 = ptr1;
        request->ptr2 = ptr2;
        request->ptr3 = ptr3;
        request->rpcRequest = wrapInLayer(object, getDatacenterWithId(datacenterId), request);
        if (LOGS_ENABLED) DEBUG_D("send request wrapped %p - %s", request->rpcRequest.get(), typeid(*(request->rpcRequest.get())).name());
        requestsQueue.push_back(std::unique_ptr<Request>(request));
        if (immediate) {
            processRequestQueue(0, 0);
        }
    });
}
#endif

void ConnectionsManager::cancelRequestsForGuid(int32_t guid) {
    scheduleTask([&, guid] {
        auto iter = requestsByGuids.find(guid);
        if (iter != requestsByGuids.end()) {
            std::vector<int32_t> &requests = iter->second;
            size_t count = requests.size();
            for (uint32_t a = 0; a < count; a++) {
                cancelRequestInternal(requests[a], 0, true, false);
                auto iter2 = guidsByRequests.find(requests[a]);
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
        auto iter = requestsByGuids.find(guid);
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

void ConnectionsManager::setUserId(int64_t userId) {
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

void ConnectionsManager::switchBackend(bool restart) {
    scheduleTask([&, restart] {
        currentDatacenterId = 1;
        testBackend = !testBackend;
        datacenters.clear();
        initDatacenters();
        saveConfig();
        if (restart) {
            exit(1);
        }
    });
}

void ConnectionsManager::removeRequestFromGuid(int32_t requestToken) {
    auto iter2 = guidsByRequests.find(requestToken);
    if (iter2 != guidsByRequests.end()) {
        auto iter = requestsByGuids.find(iter2->first);
        if (iter != requestsByGuids.end()) {
            auto iter3 = std::find(iter->second.begin(), iter->second.end(), iter->first);
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
    for (auto iter = requestsQueue.begin(); iter != requestsQueue.end(); iter++) {
        Request *request = iter->get();
        if ((token != 0 && request->requestToken == token) || (messageId != 0 && request->respondsToMessageId(messageId))) {
            request->cancelled = true;
            if (LOGS_ENABLED) DEBUG_D("cancelled queued rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
            requestsQueue.erase(iter);
            if (removeFromClass) {
                removeRequestFromGuid(token);
            }
            return true;
        }
    }

    for (auto iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
        Request *request = iter->get();
        if ((token != 0 && request->requestToken == token) || (messageId != 0 && request->respondsToMessageId(messageId))) {
            if (notifyServer) {
                auto dropAnswer = new TL_rpc_drop_answer();
                dropAnswer->req_msg_id = request->messageId;
                sendRequest(dropAnswer, nullptr, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin | RequestFlagFailOnServerErrors, request->datacenterId, request->connectionType, true);
            }
            request->cancelled = true;
            if (LOGS_ENABLED) DEBUG_D("cancelled running rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
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
    if (datacenterId == currentDatacenterId || datacenterId == movingToDatacenterId || updatingDcSettingsWorkaround || updatingDcSettings) {
        timeDifference = timeDiff;
        datacenter->recreateSessions(type);
        clearRequestsForDatacenter(datacenter, type);
    }
    processRequestQueue(AllConnectionTypes, datacenterId);
    if (type == HandshakeTypeTemp && !proxyCheckQueue.empty()) {
        ProxyCheckInfo *proxyCheckInfo = proxyCheckQueue[0].release();
        proxyCheckQueue.erase(proxyCheckQueue.begin());
        scheduleCheckProxyInternal(proxyCheckInfo);
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
                        auto iter = quickAckIdToRequestIds.find(quickAckId);
                        if (iter == quickAckIdToRequestIds.end()) {
                            quickAckIdToRequestIds[quickAckId] = requestIds;
                        } else {
                            iter->second.insert(iter->second.end(), requestIds.begin(), requestIds.end());
                        }
                    }
                }

                connection->sendData(transportData, reportAck, true);
            } else {
                if (LOGS_ENABLED) DEBUG_E("connection(%p) connection data is empty", connection);
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

void ConnectionsManager::requestSaltsForDatacenter(Datacenter *datacenter, bool media, bool useTempConnection) {
    uint32_t id = datacenter->getDatacenterId();
    if (useTempConnection) {
        id |= 0x80000000;
    }
    if (media) {
        id |= 0x40000000;
    }
    if (std::find(requestingSaltsForDc.begin(), requestingSaltsForDc.end(), id) != requestingSaltsForDc.end()) {
        return;
    }
    ConnectionType connectionType;
    if (media) {
        connectionType = ConnectionTypeGenericMedia;
    } else if (useTempConnection) {
        connectionType = ConnectionTypeTemp;
    } else {
        connectionType = ConnectionTypeGeneric;
    }
    requestingSaltsForDc.push_back(id);
    auto request = new TL_get_future_salts();
    request->num = 32;
    sendRequest(request, [&, datacenter, id, media](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime) {
        auto iter = std::find(requestingSaltsForDc.begin(), requestingSaltsForDc.end(), id);
        if (iter != requestingSaltsForDc.end()) {
            requestingSaltsForDc.erase(iter);
        }
        if (response != nullptr) {
            datacenter->mergeServerSalts((TL_future_salts *) response, media);
            saveConfig();
        }
    }, nullptr, RequestFlagWithoutLogin | RequestFlagEnableUnauthorized | RequestFlagUseUnboundKey, datacenter->getDatacenterId(), connectionType, true);
}

void ConnectionsManager::clearRequestsForDatacenter(Datacenter *datacenter, HandshakeType type) {
    for (auto & runningRequest : runningRequests) {
        Request *request = runningRequest.get();
        Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
        if (requestDatacenter->getDatacenterId() != datacenter->getDatacenterId()) {
            continue;
        }
        if (type == HandshakeTypePerm || type == HandshakeTypeAll || (type == HandshakeTypeMediaTemp && request->isMediaRequest()) || (type == HandshakeTypeTemp && !request->isMediaRequest())) {
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
    auto request = new TL_account_registerDevice();
    request->token_type = 7;
    request->token = to_string_uint64((uint64_t) pushSessionId);

    sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime) {
        if (error == nullptr) {
            registeredForInternalPush = true;
            if (LOGS_ENABLED) DEBUG_D("registered for internal push");
        } else {
            registeredForInternalPush = false;
            if (LOGS_ENABLED) DEBUG_E("unable to registering for internal push");
        }
        saveConfig();
        registeringForPush = false;
    }, nullptr, 0, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
}


inline void addMessageToDatacenter(uint32_t datacenterId, NetworkMessage *networkMessage, std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> &messagesToDatacenters) {
    auto iter = messagesToDatacenters.find(datacenterId);
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
    auto currentTime = (int32_t) (currentTimeMillis / 1000);
    uint32_t genericRunningRequestCount = 0;
    uint32_t uploadRunningRequestCount = 0;
    bool hasInvokeAfterMessage = false;
    bool hasInvokeWaitMessage = false;

    for (auto iter = runningRequests.begin(); iter != runningRequests.end();) {
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
        if ((request->requestFlags & RequestFlagResendAfter) != 0) {
            hasInvokeWaitMessage = true;
            if (hasInvokeAfterMessage) {
                iter++;
                continue;
            }
        }
        if (!hasInvokeAfterMessage && (request->requestFlags & RequestFlagInvokeAfter) != 0) {
            hasInvokeAfterMessage = true;
        }

        switch (request->connectionType & 0x0000ffff) {
            case ConnectionTypeGeneric:
                genericRunningRequestCount++;
                break;
            case ConnectionTypeDownload: {
                uint32_t currentCount;
                auto dcIter = downloadRunningRequestCount.find(datacenterId);
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
                if (LOGS_ENABLED) DEBUG_D("move %s to requestsQueue", typeid(*request->rawRequest).name());
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
                if (LOGS_ENABLED) DEBUG_D("move %p - %s to requestsQueue because of initConnection", request->rawRequest, typeid(*request->rawRequest).name());
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

        if (typeInfo == typeid(TL_get_future_salts)) {
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
                    if (LOGS_ENABLED) DEBUG_D("request token is valid, not retrying %s (%p)", typeInfo.name(), request->rawRequest);
                    iter++;
                    continue;
                } else {
                    if (connection->getConnectionToken() != 0 && request->connectionToken == connection->getConnectionToken()) {
                        if (LOGS_ENABLED) DEBUG_D("request download token is valid, not retrying %s (%p)", typeInfo.name(), request->rawRequest);
                        iter++;
                        continue;
                    }
                }
            }

            if (request->connectionToken != 0 && request->connectionToken != connection->getConnectionToken()) {
                request->lastResendTime = 0;
                request->isResending = true;
            }

            request->retryCount++;

            if (!request->failedBySalt) {
                if (request->connectionType & ConnectionTypeDownload) {
                    uint32_t retryMax = 10;
                    if (!(request->requestFlags & RequestFlagForceDownload)) {
                        if (request->failedByFloodWait) {
                            retryMax = 2;
                        } else {
                            retryMax = 6;
                        }
                    }
                    if (request->retryCount >= retryMax) {
                        if (LOGS_ENABLED) DEBUG_E("timed out %s", typeInfo.name());
                        auto error = new TL_error();
                        error->code = -123;
                        error->text = "RETRY_LIMIT";
                        request->onComplete(nullptr, error, connection->currentNetworkType, 0);
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

            auto networkMessage = new NetworkMessage();
            networkMessage->forceContainer = request->isResending;
            networkMessage->message = std::make_unique<TL_message>();
            networkMessage->message->msg_id = request->messageId;
            networkMessage->message->bytes = request->serializedLength;
            networkMessage->message->outgoingBody = request->getRpcRequest();
            networkMessage->message->seqno = request->messageSeqNo;
            networkMessage->requestId = request->requestToken;
            networkMessage->invokeAfter = (request->requestFlags & RequestFlagInvokeAfter) != 0 && (request->requestFlags & RequestFlagResendAfter) == 0;
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
            auto iter = sessionsToDestroy.begin();

            if (abs(currentTime - lastDestroySessionRequestTime) > 2) {
                lastDestroySessionRequestTime = currentTime;
                auto request = new TL_destroy_session();
                request->session_id = *iter;

                auto networkMessage = new NetworkMessage();
                networkMessage->message = std::make_unique<TL_message>();
                networkMessage->message->msg_id = generateMessageId();
                networkMessage->message->bytes = request->getObjectSize();
                networkMessage->message->body = std::unique_ptr<TLObject>(request);
                networkMessage->message->seqno = genericConnection->generateMessageSeqNo(false);
                addMessageToDatacenter(defaultDatacenter->getDatacenterId(), networkMessage, genericMessagesToDatacenters);
            }
            sessionsToDestroy.erase(iter);
        }
    }

    for (auto iter = requestsQueue.begin(); iter != requestsQueue.end();) {
        Request *request = iter->get();
        if (request->cancelled) {
            iter = requestsQueue.erase(iter);
            continue;
        }
        if (hasInvokeWaitMessage && (request->requestFlags & RequestFlagInvokeAfter) != 0 && (request->requestFlags & RequestFlagResendAfter) == 0) {
            request->requestFlags |= RequestFlagResendAfter;
        }
        if (hasInvokeAfterMessage && (request->requestFlags & RequestFlagResendAfter) != 0) {
            iter++;
            continue;
        }
        if (!hasInvokeAfterMessage && (request->requestFlags & RequestFlagInvokeAfter) != 0) {
            hasInvokeAfterMessage = true;
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
                for (auto & datacenter : datacenters) {
                    if (datacenter.first == datacenterId || datacenter.second->isCdnDatacenter) {
                        continue;
                    }
                    allDc.push_back(datacenter.first);
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
                auto dcIter = downloadRunningRequestCount.find(datacenterId);
                if (dcIter != downloadRunningRequestCount.end()) {
                    currentCount = dcIter->second;
                } else {
                    currentCount = 0;
                }
                if (!networkAvailable || currentCount >= 16) {
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
        if (LOGS_ENABLED) DEBUG_D("messageId for token = %d, 0x%" PRIx64, request->requestToken, request->messageId);

        uint32_t requestLength = request->rpcRequest->getObjectSize();
        if (request->requestFlags & RequestFlagCanCompress) {
            request->requestFlags &= ~RequestFlagCanCompress;
            NativeByteBuffer *original = BuffersStorage::getInstance().getFreeBuffer(requestLength);
            request->rpcRequest->serializeToStream(original);
            NativeByteBuffer *buffer = compressGZip(original);
            if (buffer != nullptr) {
                auto packed = new TL_gzip_packed();
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

        auto networkMessage = new NetworkMessage();
        networkMessage->message = std::make_unique<TL_message>();
        networkMessage->forceContainer = request->isResending;
        networkMessage->message->msg_id = request->messageId;
        networkMessage->message->bytes = request->serializedLength;
        networkMessage->message->outgoingBody = request->getRpcRequest();
        networkMessage->message->seqno = request->messageSeqNo;
        networkMessage->requestId = request->requestToken;
        networkMessage->invokeAfter = (request->requestFlags & RequestFlagInvokeAfter) != 0 && (request->requestFlags & RequestFlagResendAfter) == 0;
        networkMessage->needQuickAck = (request->requestFlags & RequestFlagNeedQuickAck) != 0;

        if (!hasPendingRequestsForConnection(connection)) {
            connection->resetLastEventTime();
        }
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

    for (auto & iter : datacenters) {
        Datacenter *datacenter = iter.second;
        auto iter2 = genericMessagesToDatacenters.find(datacenter->getDatacenterId());
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

    for (auto & genericMessagesToDatacenter : genericMessagesToDatacenters) {
        Datacenter *datacenter = getDatacenterWithId(genericMessagesToDatacenter.first);
        if (datacenter != nullptr) {
            bool scannedPreviousRequests = false;
            bool needQuickAck = false;
            int64_t lastSentMessageRpcId = 0;
            std::vector<std::unique_ptr<NetworkMessage>> &array = genericMessagesToDatacenter.second;
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
                            if (currentNetworkMessage->invokeAfter) {
                                currentRequests.push_back(currentNetworkMessage->message->msg_id);
                            }
                        }

                        int64_t maxRequestId = 0;
                        if (lastInvokeAfterMessageId != 0) {
                            auto timeMessage = (int64_t) (lastInvokeAfterMessageId / 4294967296.0);
                            if (getCurrentTime() - timeMessage <= 5) {
                                maxRequestId = lastInvokeAfterMessageId;
                            }
                        }
                        for (auto & runningRequest : runningRequests) {
                            Request *request = runningRequest.get();
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
                        auto request = new TL_invokeAfterMsg();
                        request->msg_id = lastSentMessageRpcId;
                        if (message->outgoingBody != nullptr) {
                            if (LOGS_ENABLED) DEBUG_D("wrap outgoingBody(%p, %s) to TL_invokeAfterMsg, token = %d, after 0x%" PRIx64, message->outgoingBody, typeid(*message->outgoingBody).name(), networkMessage->requestId, request->msg_id);
                            request->outgoingQuery = message->outgoingBody;
                            message->outgoingBody = nullptr;
                        } else {
                            if (LOGS_ENABLED) DEBUG_D("wrap body(%p, %s) to TL_invokeAfterMsg, token = %d, after 0x%" PRIx64, message->body.get(), typeid(*(message->body.get())).name(), networkMessage->requestId, request->msg_id);
                            request->query = std::move(message->body);
                        }
                        message->body = std::unique_ptr<TLObject>(request);
                        message->bytes += 4 + 8;
                    }

                    lastSentMessageRpcId = message->msg_id;
                    lastInvokeAfterMessageId = message->msg_id;
                }
            }

            sendMessagesToConnectionWithConfirmation(array, datacenter->getGenericConnection(true, 1), needQuickAck);
        }
    }

    for (auto & tempMessagesToDatacenter : tempMessagesToDatacenters) {
        Datacenter *datacenter = getDatacenterWithId(tempMessagesToDatacenter.first);
        if (datacenter != nullptr) {
            std::vector<std::unique_ptr<NetworkMessage>> &array = tempMessagesToDatacenter.second;
            sendMessagesToConnectionWithConfirmation(array, datacenter->getTempConnection(true), false);
        }
    }

    for (auto & genericMediaMessagesToDatacenter : genericMediaMessagesToDatacenters) {
        Datacenter *datacenter = getDatacenterWithId(genericMediaMessagesToDatacenter.first);
        if (datacenter != nullptr) {
            std::vector<std::unique_ptr<NetworkMessage>> &array = genericMediaMessagesToDatacenter.second;
            sendMessagesToConnectionWithConfirmation(array, datacenter->getGenericMediaConnection(true, 1), false);
        }
    }

    if (connectionTypes == ConnectionTypeGeneric && dc == currentDatacenterId) {
        auto iter2 = genericMessagesToDatacenters.find(currentDatacenterId);
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
    auto iter = datacenters.find(datacenterId);
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
            auto request = new initConnection();
            if (delegate != nullptr) {
                request->flags = delegate->getInitFlags(instanceNum);
            } else {
                request->flags = 0;
            }
            request->query = std::unique_ptr<TLObject>(object);
            request->api_id = currentApiId;
            request->app_version = currentAppVersion;
            request->lang_code = currentLangCode;
            request->lang_pack = "android";
            request->system_lang_code = currentSystemLangCode;


            auto jsonObject = new TL_jsonObject();
            request->params = std::unique_ptr<JSONValue>(jsonObject);

            if (!currentRegId.empty()) {
                auto objectValue = new TL_jsonObjectValue();
                jsonObject->value.push_back(std::unique_ptr<TL_jsonObjectValue>(objectValue));

                auto jsonString = new TL_jsonString();
                jsonString->value = currentRegId;
                objectValue->key = "device_token";
                objectValue->value = std::unique_ptr<JSONValue>(jsonString);
            }
            if (!certFingerprint.empty()) {
                auto objectValue = new TL_jsonObjectValue();
                jsonObject->value.push_back(std::unique_ptr<TL_jsonObjectValue>(objectValue));

                auto jsonString = new TL_jsonString();
                jsonString->value = certFingerprint;
                objectValue->key = "data";
                objectValue->value = std::unique_ptr<JSONValue>(jsonString);
            }

            auto objectValue = new TL_jsonObjectValue();
            jsonObject->value.push_back(std::unique_ptr<TL_jsonObjectValue>(objectValue));
            auto jsonString = new TL_jsonString();
            jsonString->value = installer;
            objectValue->key = "installer";
            objectValue->value = std::unique_ptr<JSONValue>(jsonString);

            objectValue = new TL_jsonObjectValue();
            jsonObject->value.push_back(std::unique_ptr<TL_jsonObjectValue>(objectValue));
            jsonString = new TL_jsonString();
            jsonString->value = package;
            objectValue->key = "package_id";
            objectValue->value = std::unique_ptr<JSONValue>(jsonString);

            objectValue = new TL_jsonObjectValue();
            jsonObject->value.push_back(std::unique_ptr<TL_jsonObjectValue>(objectValue));

            auto jsonNumber = new TL_jsonNumber();
            jsonNumber->value = currentDeviceTimezone;
            objectValue->key = "tz_offset";
            objectValue->value = std::unique_ptr<JSONValue>(jsonNumber);

            request->flags |= 2;

            if (!proxyAddress.empty() && !proxySecret.empty()) {
                request->flags |= 1;
                request->proxy = std::make_unique<TL_inputClientProxy>();
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
            auto request2 = new invokeWithLayer();
            request2->layer = currentLayer;
            request2->query = std::unique_ptr<TLObject>(request);
            if (LOGS_ENABLED) DEBUG_D("wrap in layer %s, flags = %d", typeid(*object).name(), request->flags);
            return std::unique_ptr<TLObject>(request2);
        }
    }
    return std::unique_ptr<TLObject>(object);
}

static const char *const url_symbols64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
static unsigned char url_char_to_value[256];
static void init_base64url_table() {
    static bool is_inited = []() {
        std::fill(std::begin(url_char_to_value), std::end(url_char_to_value), static_cast<unsigned char>(64));
        for (unsigned char i = 0; i < 64; i++) {
            url_char_to_value[static_cast<size_t>(url_symbols64[i])] = i;
        }
        return true;
    }();
    assert(is_inited);
}

std::string base64UrlDecode(std::string base64) {
    init_base64url_table();

    size_t padding_length = 0;
    while (!base64.empty() && base64.back() == '=') {
        base64.pop_back();
        padding_length++;
    }
    if (padding_length >= 3 || (padding_length > 0 && ((base64.size() + padding_length) & 3) != 0)) {
        return "";
    }

    if ((base64.size() & 3) == 1) {
        return "";
    }

    std::string output;
    output.reserve(((base64.size() + 3) >> 2) * 3);
    for (size_t i = 0; i < base64.size();) {
        size_t left = std::min(base64.size() - i, static_cast<size_t>(4));
        int c = 0;
        for (size_t t = 0; t < left; t++) {
            auto value = url_char_to_value[base64.c_str()[i++]];
            if (value == 64) {
                return "";
            }
            c |= value << ((3 - t) * 6);
        }
        output += static_cast<char>(static_cast<unsigned char>(c >> 16));
        if (left == 2) {
            if ((c & ((1 << 16) - 1)) != 0) {
                return "";
            }
        } else {
            output += static_cast<char>(static_cast<unsigned char>(c >> 8));
            if (left == 3) {
                if ((c & ((1 << 8) - 1)) != 0) {
                    return "";
                }
            } else {
                output += static_cast<char>(static_cast<unsigned char>(c));
            }
        }
    }
    return output;
}

inline std::string decodeSecret(std::string secret) {
    bool allHex = true;
    for (char i : secret) {
        if (!((i >= '0' && i <= '9') || (i >= 'a' && i <= 'f') || (i >= 'A' && i <= 'F'))) {
            allHex = false;
            break;
        }
    }
    if (allHex) {
        size_t size = secret.size() / 2;
        char *result = new char[size];
        for (int32_t i = 0; i < size; i++) {
            result[i] = (char) (char2int(secret[i * 2]) * 16 + char2int(secret[i * 2 + 1]));
        }
        secret = std::string(result, size);
        delete[] result;
        return secret;
    }
    return base64UrlDecode(secret);
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

    auto request = new TL_help_getConfig();
    sendRequest(request, [&, workaround](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime) {
        if ((!workaround && !updatingDcSettings) || (workaround && !updatingDcSettingsWorkaround)) {
            return;
        }

        if (response != nullptr) {
            auto config = (TL_config *) response;
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
                    for (auto & addresse : *addresses) {
                        if (addresse.address == dcOption->ip_address && addresse.port == dcOption->port) {
                            return;
                        }
                    }
                    std::string secret;
                    if (dcOption->secret != nullptr) {
                        secret = std::string((const char *) dcOption->secret->bytes, dcOption->secret->length);
                    }
                    if (LOGS_ENABLED) DEBUG_D("getConfig add %s:%d to dc%d, flags %d, has secret = %d[%d]", dcOption->ip_address.c_str(), dcOption->port, dcOption->id, dcOption->flags, dcOption->secret != nullptr ? 1 : 0, dcOption->secret != nullptr ? dcOption->secret->length : 0);
                    addresses->push_back(TcpAddress(dcOption->ip_address, dcOption->port, dcOption->flags, secret));
                }
            };

            std::map<uint32_t, std::unique_ptr<DatacenterInfo>> map;
            size_t count = config->dc_options.size();
            for (uint32_t a = 0; a < count; a++) {
                TL_dcOption *dcOption = config->dc_options[a].get();
                auto iter = map.find((uint32_t) dcOption->id);
                DatacenterInfo *info;
                if (iter == map.end()) {
                    map[dcOption->id] = std::unique_ptr<DatacenterInfo>(info = new DatacenterInfo);
                } else {
                    info = iter->second.get();
                }
                info->addAddressAndPort(dcOption);
            }

            if (!map.empty()) {
                for (auto & iter : map) {
                    Datacenter *datacenter = getDatacenterWithId(iter.first);
                    DatacenterInfo *info = iter.second.get();
                    if (datacenter == nullptr) {
                        datacenter = new Datacenter(instanceNum, iter.first);
                        datacenters[iter.first] = datacenter;
                    }
                    datacenter->replaceAddresses(info->addressesIpv4, info->isCdn ? 8 : 0);
                    datacenter->replaceAddresses(info->addressesIpv6, info->isCdn ? 9 : 1);
                    datacenter->replaceAddresses(info->addressesIpv4Download, info->isCdn ? 10 : 2);
                    datacenter->replaceAddresses(info->addressesIpv6Download, info->isCdn ? 11 : 3);
                    if (iter.first == movingToDatacenterId) {
                        movingToDatacenterId = DEFAULT_DATACENTER_ID;
                        moveToDatacenter(iter.first);
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
        auto request = new TL_auth_exportAuthorization();
        request->dc_id = datacenterId;
        sendRequest(request, [&, datacenterId](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime) {
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
        datacenter->clearServerSalts(false);
        datacenter->clearServerSalts(true);
        datacenter->beginHandshake(HandshakeTypeAll, true);
    }

    if (movingAuthorization != nullptr) {
        auto request = new TL_auth_importAuthorization();
        request->id = currentUserId;
        request->bytes = std::move(movingAuthorization);
        sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime) {
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
            addresses.emplace_back(ipAddress, port, 0, "");
            datacenter->suspendConnections(true);
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
        if (prefix.empty()) {
            found = true;
        } else if (prefix[0] == '+' && phone.find(prefix.substr(1)) == 0) {
            found = true;
        } else if (prefix[0] == '-' && phone.find(prefix.substr(1)) == 0) {
            return false;
        }
    }
    return found;
}

void ConnectionsManager::applyDnsConfig(NativeByteBuffer *buffer, std::string phone, int32_t date) {
    scheduleTask([&, buffer, phone, date] {
        int32_t realDate = date;
        if (LOGS_ENABLED) DEBUG_D("trying to decrypt config %d", requestingSecondAddress);
        TL_help_configSimple *config = Datacenter::decodeSimpleConfig(buffer);
        if (config != nullptr && realDate == 0) {
            realDate = config->date;
        }
        int currentDate = getCurrentTime();
        if (config != nullptr && config->date <= currentDate && currentDate <= config->expires) {
            if (realDate > 0 && requestingSecondAddressByTlsHashMismatch) {
                timeDifference += (realDate - currentDate);
                requestingSecondAddressByTlsHashMismatch = false;
            }
            for (auto & iter : config->rules) {
                TL_accessPointRule *rule = iter.get();
                if (!checkPhoneByPrefixesRules(phone, rule->phone_prefix_rules)) {
                    continue;
                }
                Datacenter *datacenter = getDatacenterWithId(rule->dc_id);
                if (datacenter != nullptr) {
                    std::vector<TcpAddress> addresses;
                    for (auto iter2 = rule->ips.begin(); iter2 != rule->ips.end(); iter2++) {
                        IpPort *port = iter2->get();
                        const std::type_info &typeInfo = typeid(*port);
                        if (typeInfo == typeid(TL_ipPort)) {
                            auto ipPort = (TL_ipPort *) port;
                            addresses.emplace_back(ipPort->ipv4, ipPort->port, 0, "");
                            if (LOGS_ENABLED) DEBUG_D("got address %s and port %d for dc%d", ipPort->ipv4.c_str(), ipPort->port, rule->dc_id);
                        } else if (typeInfo == typeid(TL_ipPortSecret)) {
                            auto ipPort = (TL_ipPortSecret *) port;
                            addresses.emplace_back(ipPort->ipv4, ipPort->port, 0, std::string((const char *) ipPort->secret->bytes, ipPort->secret->length));
                            if (LOGS_ENABLED) DEBUG_D("got address %s and port %d for dc%d with secret", ipPort->ipv4.c_str(), ipPort->port, rule->dc_id);
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
                    if (LOGS_ENABLED) DEBUG_D("config datacenter %d not found", rule->dc_id);
                }
            }
            delete config;
        } else {
            if (config == nullptr) {
                if (LOGS_ENABLED) DEBUG_D("can't decrypt dns config");
            } else {
                delete config;
                if (LOGS_ENABLED) DEBUG_D("dns config not valid due to date or expire");
            }
            if (requestingSecondAddress == 2) {
                requestingSecondAddress = 3;
                delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
            } else if (requestingSecondAddress == 1) {
                requestingSecondAddress = 2;
                delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
            } else if (requestingSecondAddress == 0) {
                requestingSecondAddress = 1;
                delegate->onRequestNewServerIpAndPort(requestingSecondAddress, instanceNum);
            } else {
                requestingSecondAddress = 0;
            }
        }
        buffer->reuse();
    });
}

void ConnectionsManager::init(uint32_t version, int32_t layer, int32_t apiId, std::string deviceModel, std::string systemVersion, std::string appVersion, std::string langCode, std::string systemLangCode, std::string configPath, std::string logPath, std::string regId, std::string cFingerpting, std::string installerId, std::string packageId, int32_t timezoneOffset, int64_t userId, bool isPaused, bool enablePushConnection, bool hasNetwork, int32_t networkType) {
    currentVersion = version;
    currentLayer = layer;
    currentApiId = apiId;
    currentConfigPath = configPath;
    currentDeviceModel = deviceModel;
    currentSystemVersion = systemVersion;
    currentAppVersion = appVersion;
    currentLangCode = langCode;
    currentRegId = regId;
    certFingerprint = cFingerpting;
    installer = installerId;
    package = packageId;
    currentDeviceTimezone = timezoneOffset;
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
        LOGS_ENABLED = true;
        FileLog::getInstance().init(logPath);
    }

    loadConfig();

    bool needLoadConfig = false;
    if (systemLangCode.compare(lastInitSystemLangcode) != 0) {
        lastInitSystemLangcode = systemLangCode;
        for (auto & datacenter : datacenters) {
            datacenter.second->resetInitVersion();
        }
        needLoadConfig = true;
        saveConfig();
    }
    if (!needLoadConfig && currentUserId != 0) {
        Datacenter *datacenter = getDatacenterWithId(DEFAULT_DATACENTER_ID);
        if (datacenter != nullptr && datacenter->lastInitVersion != currentVersion) {
            needLoadConfig = true;
        }
    }

    pthread_create(&networkThread, nullptr, (ConnectionsManager::ThreadProc), this);

    if (needLoadConfig) {
        updateDcSettings(0, false);
    }
}

void ConnectionsManager::setProxySettings(std::string address, uint16_t port, std::string username, std::string password, std::string secret) {
    scheduleTask([&, address, port, username, password, secret] {
        std::string newSecret = decodeSecret(secret);
        bool secretChanged = proxySecret != newSecret;
        bool reconnect = proxyAddress != address || proxyPort != port || username != proxyUser || proxyPassword != password || secretChanged;
        proxyAddress = address;
        proxyPort = port;
        proxyUser = username;
        proxyPassword = password;
        proxySecret = std::move(newSecret);
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
            for (auto & datacenter : datacenters) {
                datacenter.second->suspendConnections(true);
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
        if (currentLangCode == langCode) {
            return;
        }
        currentLangCode = langCode;
        for (auto & datacenter : datacenters) {
            datacenter.second->resetInitVersion();
        }
        saveConfig();
    });
}

void ConnectionsManager::setRegId(std::string regId) {
    scheduleTask([&, regId] {
        if (currentRegId == regId) {
            return;
        }
        currentRegId = regId;
        for (auto & datacenter : datacenters) {
            datacenter.second->resetInitVersion();
        }
        updateDcSettings(0, false);
        saveConfig();
    });
}

void ConnectionsManager::setSystemLangCode(std::string langCode) {
    scheduleTask([&, langCode] {
        if (currentSystemLangCode == langCode) {
            return;
        }
        lastInitSystemLangcode = currentSystemLangCode = langCode;
        for (auto & datacenter : datacenters) {
            datacenter.second->resetInitVersion();
        }
        saveConfig();
        updateDcSettings(0, false);
    });
}

void ConnectionsManager::resumeNetwork(bool partial) {
    scheduleTask([&, partial] {
        if (lastMonotonicPauseTime != 0) {
            int64_t diff = (getCurrentTimeMonotonicMillis() - lastMonotonicPauseTime) / 1000;
            int64_t systemDiff = getCurrentTime() - lastSystemPauseTime;
            if (systemDiff < 0 || abs(systemDiff - diff) > 2) {
                timeDifference -= (systemDiff - diff);
            }
        }
        if (partial) {
            if (networkPaused) {
                lastMonotonicPauseTime = lastPauseTime = getCurrentTimeMonotonicMillis();
                lastSystemPauseTime = getCurrentTime();
                networkPaused = false;
                if (LOGS_ENABLED) DEBUG_D("wakeup network in background account%u", instanceNum);
            } else if (lastPauseTime != 0) {
                lastMonotonicPauseTime = lastPauseTime = getCurrentTimeMonotonicMillis();
                lastSystemPauseTime = getCurrentTime();
                networkPaused = false;
                if (LOGS_ENABLED) DEBUG_D("reset sleep timeout account%u", instanceNum);
            }
        } else {
            lastPauseTime = 0;
            lastMonotonicPauseTime = 0;
            lastSystemPauseTime = 0;
            networkPaused = false;
            if (LOGS_ENABLED) DEBUG_D("wakeup network account%u", instanceNum);
        }
        if (!networkPaused) {
            for (auto & datacenter : datacenters) {
                if (datacenter.second->isHandshaking(false)) {
                    datacenter.second->createGenericConnection()->connect();
                } else if (datacenter.second->isHandshaking(true)) {
                    datacenter.second->createGenericMediaConnection()->connect();
                }
            }
        }
    });
}

void ConnectionsManager::pauseNetwork() {
    if (lastPauseTime != 0) {
        return;
    }
    lastMonotonicPauseTime = lastPauseTime = getCurrentTimeMonotonicMillis();
    lastSystemPauseTime = getCurrentTime();
    saveConfig();
}

void ConnectionsManager::setNetworkAvailable(bool value, int32_t type, bool slow) {
    scheduleTask([&, value, type, slow] {
        networkAvailable = value;
        currentNetworkType = type;
        networkSlow = slow;
        if (!networkAvailable) {
            connectionState = ConnectionStateWaitingForNetwork;
        } else {
            for (auto & datacenter : datacenters) {
                if (datacenter.second->isHandshaking(false)) {
                    datacenter.second->createGenericConnection()->connect();
                } else if (datacenter.second->isHandshaking(true)) {
                    datacenter.second->createGenericMediaConnection()->connect();
                }
            }
        }
        if (delegate != nullptr) {
            delegate->onConnectionStateChanged(connectionState, instanceNum);
        }
    });
}

void ConnectionsManager::setIpStrategy(uint8_t value) {
    scheduleTask([&, value] {
        ipStrategy = value;
    });
}

int64_t ConnectionsManager::checkProxy(std::string address, uint16_t port, std::string username, std::string password, std::string secret, onRequestTimeFunc requestTimeFunc, jobject ptr1) {
    auto proxyCheckInfo = new ProxyCheckInfo();
    proxyCheckInfo->address = address;
    proxyCheckInfo->port = port;
    proxyCheckInfo->username = username;
    proxyCheckInfo->password = password;
    proxyCheckInfo->secret = decodeSecret(secret);
    proxyCheckInfo->onRequestTime = requestTimeFunc;
    proxyCheckInfo->pingId = ++lastPingProxyId;
    proxyCheckInfo->instanceNum = instanceNum;
    proxyCheckInfo->ptr1 = ptr1;

    scheduleCheckProxyInternal(proxyCheckInfo);

    return proxyCheckInfo->pingId;
}

void ConnectionsManager::scheduleCheckProxyInternal(ProxyCheckInfo *proxyCheckInfo) {
    scheduleTask([&, proxyCheckInfo] {
        checkProxyInternal(proxyCheckInfo);
    });
}

void ConnectionsManager::checkProxyInternal(ProxyCheckInfo *proxyCheckInfo) {
    int32_t freeConnectionNum = -1;
    if (proxyActiveChecks.size() != PROXY_CONNECTIONS_COUNT) {
        for (int32_t a = 0; a < PROXY_CONNECTIONS_COUNT; a++) {
            bool found = false;
            for (auto & proxyActiveCheck : proxyActiveChecks) {
                if (proxyActiveCheck.get()->connectionNum == a) {
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
        auto connectionType = (ConnectionType) (ConnectionTypeProxy | (freeConnectionNum << 16));
        Datacenter *datacenter = getDatacenterWithId(DEFAULT_DATACENTER_ID);
        Connection *connection = datacenter->getProxyConnection((uint8_t) freeConnectionNum, true, false);
        if (connection != nullptr) {
            connection->setOverrideProxy(proxyCheckInfo->address, proxyCheckInfo->port, proxyCheckInfo->username, proxyCheckInfo->password, proxyCheckInfo->secret);
            connection->suspendConnection();
            proxyCheckInfo->connectionNum = freeConnectionNum;
            auto request = new TL_ping();
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
}

#ifdef ANDROID
void ConnectionsManager::useJavaVM(JavaVM *vm, bool useJavaByteBuffers) {
    javaVm = vm;
    if (useJavaByteBuffers) {
        JNIEnv *env = nullptr;
        if (javaVm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            if (LOGS_ENABLED) DEBUG_E("can't get jnienv");
            exit(1);
        }
        jclass_ByteBuffer = (jclass) env->NewGlobalRef(env->FindClass("java/nio/ByteBuffer"));
        if (jclass_ByteBuffer == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("can't find java ByteBuffer class");
            exit(1);
        }
        jclass_ByteBuffer_allocateDirect = env->GetStaticMethodID(jclass_ByteBuffer, "allocateDirect", "(I)Ljava/nio/ByteBuffer;");
        if (jclass_ByteBuffer_allocateDirect == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("can't find java ByteBuffer allocateDirect");
            exit(1);
        }
        if (LOGS_ENABLED) DEBUG_D("using java ByteBuffer");
    }
}
#endif
