/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
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
#include "ConnectionsManager.h"
#include "FileLog.h"
#include "EventObject.h"
#include "MTProtoScheme.h"
#include "NativeByteBuffer.h"
#include "Connection.h"
#include "Datacenter.h"
#include "Request.h"
#include "BuffersStorage.h"
#include "ByteArray.h"
#include "Config.h"

#ifdef ANDROID
#include <jni.h>
JavaVM *javaVm = nullptr;
JNIEnv *jniEnv = nullptr;
jclass jclass_ByteBuffer = nullptr;
jmethodID jclass_ByteBuffer_allocateDirect = 0;
#endif

static bool done = false;

ConnectionsManager::ConnectionsManager() {
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

    EventObject *eventObject = new EventObject(pipeFd, EventObjectPipe);

    epoll_event eventMask = {};
    eventMask.events = EPOLLIN;
    eventMask.data.ptr = eventObject;
    if (epoll_ctl(epolFd, EPOLL_CTL_ADD, pipeFd[0], &eventMask) != 0) {
        DEBUG_E("can't add pipe to epoll");
        exit(1);
    }

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

ConnectionsManager& ConnectionsManager::getInstance() {
    static ConnectionsManager instance;
    return instance;
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
                return diff > 1000 ? 1000 : diff;
            }
        }
    }
    if (!networkPaused) {
        return 1000;
    }
    int32_t timeToPushPing = (sendingPushPing ? 30000 : 60000 * 3) - abs(now - lastPushPingTime);
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
    int eventsCount = epoll_wait(epolFd, epollEvents, 128, callEvents(getCurrentTimeMillis()));
    checkPendingTasks();
    int64_t now = getCurrentTimeMillis();
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
        if ((sendingPushPing && abs(now - lastPushPingTime) >= 30000) || abs(now - lastPushPingTime) >= 60000 * 3 + 10000) {
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
        if (abs(now - lastPushPingTime) >= 60000 * 3) {
            DEBUG_D("time for push ping");
            lastPushPingTime = now;
            if (datacenter != nullptr) {
                sendPing(datacenter, true);
            }
        }
    }

    if (lastPauseTime != 0 && abs(now - lastPauseTime) >= nextSleepTimeout) {
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
        delegate->onUpdate();
    }
    if (datacenter != nullptr) {
        if (datacenter->hasAuthKey()) {
            if (abs(now - lastPingTime) >= 19000) {
                lastPingTime = now;
                sendPing(datacenter, false);
            }
            if (abs((int32_t) (now / 1000) - lastDcUpdateTime) >= DC_UPDATE_TIME) {
                updateDcSettings(0);
            }
            processRequestQueue(0, 0);
        } else if (!datacenter->isHandshaking()) {
            datacenter->beginHandshake(true);
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
    eventObject->time = getCurrentTimeMillis() + time;
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
    char ch = 'x';
    write(pipeFd[1], &ch, 1);
}

void *ConnectionsManager::ThreadProc(void *data) {
    DEBUG_D("network thread started");
#ifdef ANDROID
    javaVm->AttachCurrentThread(&jniEnv, NULL);
#endif
    ConnectionsManager *networkManager = (ConnectionsManager *) (data);
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
        config = new Config("tgnet.dat");
    }
    NativeByteBuffer *buffer = config->readConfig();
    if (buffer != nullptr) {
        uint32_t version = buffer->readUint32(nullptr);
        DEBUG_D("config version = %u", version);
        if (version <= configVersion) {
            testBackend = buffer->readBool(nullptr);
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
                    Datacenter *datacenter = new Datacenter(buffer);
                    datacenters[datacenter->getDatacenterId()] = datacenter;
                    DEBUG_D("datacenter(%p) %u loaded (hasAuthKey = %d)", datacenter, datacenter->getDatacenterId(), (int) datacenter->hasAuthKey());
                }
            }
        }
        buffer->reuse();
    }

    if (currentDatacenterId != 0 && currentUserId) {
        Datacenter *datacenter = getDatacenterWithId(currentDatacenterId);
        if (datacenter == nullptr || !datacenter->hasAuthKey()) {
            if (datacenter != nullptr) {
                DEBUG_D("reset authorization because of dc %p", datacenter);
            }
            currentDatacenterId = 0;
            datacenters.clear();
            scheduleTask([&] {
                if (delegate != nullptr) {
                    delegate->onLogout();
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

void ConnectionsManager::saveConfig() {
    if (config == nullptr) {
        config = new Config("tgnet.dat");
    }
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(32 * 1024);
    buffer->writeInt32(configVersion);
    buffer->writeBool(testBackend);
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

int32_t ConnectionsManager::getCurrentTime() {
    return (int32_t) (getCurrentTimeMillis() / 1000) + timeDifference;
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

void ConnectionsManager::cleanUp() {
    scheduleTask([&] {
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
                request->onComplete(nullptr, error);
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
                request->onComplete(nullptr, error);
                delete error;
            }
            iter = runningRequests.erase(iter);
        }
        quickAckIdToRequestIds.clear();

        for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
            iter->second->recreateSessions();
            iter->second->authorized = false;
        }
        sessionsToDestroy.clear();
        currentUserId = 0;
        registeredForInternalPush = false;
        saveConfig();
    });
}

void ConnectionsManager::onConnectionClosed(Connection *connection) {
    Datacenter *datacenter = connection->getDatacenter();
    if (connection->getConnectionType() == ConnectionTypeGeneric) {
        if (datacenter->isHandshaking()) {
            datacenter->onHandshakeConnectionClosed(connection);
        }
        if (datacenter->getDatacenterId() == currentDatacenterId) {
            if (networkAvailable) {
                if (connectionState != ConnectionStateConnecting) {
                    connectionState = ConnectionStateConnecting;
                    if (delegate != nullptr) {
                        delegate->onConnectionStateChanged(connectionState);
                    }
                }
            } else {
                if (connectionState != ConnectionStateWaitingForNetwork) {
                    connectionState = ConnectionStateWaitingForNetwork;
                    if (delegate != nullptr) {
                        delegate->onConnectionStateChanged(connectionState);
                    }
                }
            }
        }
    } else if (connection->getConnectionType() == ConnectionTypePush) {
        DEBUG_D("connection(%p) push connection closed", connection);
        sendingPushPing = false;
        lastPushPingTime = getCurrentTimeMillis() - 60000 * 3 + 4000;
    }
}

void ConnectionsManager::onConnectionConnected(Connection *connection) {
    Datacenter *datacenter = connection->getDatacenter();
    if (connection->getConnectionType() == ConnectionTypeGeneric) {
        if (datacenter->isHandshaking()) {
            datacenter->onHandshakeConnectionConnected(connection);
            return;
        }
    }

    if (datacenter->hasAuthKey()) {
        if (connection->getConnectionType() == ConnectionTypePush) {
            sendingPushPing = false;
            lastPushPingTime = getCurrentTimeMillis();
            sendPing(datacenter, true);
        } else {
            if (networkPaused && lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMillis();
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
    if (length == 4) {
        int32_t code = data->readInt32(&error);
        DEBUG_E("mtproto error = %d", code);
        connection->reconnect();
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
            delegate->onConnectionStateChanged(connectionState);
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

        if (messageLength != data->remaining()) {
            DEBUG_E("connection(%p) received incorrect message length", connection);
            connection->reconnect();
            return;
        }
        TLObject *request;
        if (datacenter->isHandshaking()) {
            request = datacenter->getCurrentHandshakeRequest();
        } else {
            request = getRequestWithMessageId(messageId);
        }

        TLObject *object = TLdeserialize(request, messageLength, data);

        if (object != nullptr) {
            if (datacenter->isHandshaking()) {
                datacenter->processHandshakeResponse(object, messageId);
            } else {
                processServerResponse(object, messageId, 0, 0, connection, 0, 0);
                connection->addProcessedMessageId(messageId);
            }
            delete object;
        }
    } else {
        if (length < 24 + 32 || !datacenter->decryptServerResponse(keyId, data->bytes() + mark + 8, data->bytes() + mark + 24, length - 24)) {
            DEBUG_E("connection(%p) unable to decrypt server response", connection);
            datacenter->switchTo443Port();
            connection->suspendConnection();
            connection->connect();
            return;
        }
        data->position(mark + 24);

        int64_t messageServerSalt = data->readInt64(&error);
        int64_t messageSessionId = data->readInt64(&error);

        if (messageSessionId != connection->getSissionId()) {
            DEBUG_E("connection(%p) received invalid message session id (0x%llx instead of 0x%llx)", connection, (uint64_t) messageSessionId, (uint64_t) connection->getSissionId());
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
            TLObject *object = TLdeserialize(nullptr, messageLength, data);
            if (object != nullptr) {
                DEBUG_D("connection(%p, dc%u, type %d) received object %s", connection, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
                processServerResponse(object, messageId, messageSeqNo, messageServerSalt, connection, 0, 0);
                connection->addProcessedMessageId(messageId);
                delete object;
                if (connection->getConnectionType() == ConnectionTypePush) {
                    std::vector<std::unique_ptr<NetworkMessage>> messages;
                    sendMessagesToConnectionWithConfirmation(messages, connection, false);
                }
            } else {
                if (delegate != nullptr) {
                    delegate->onUnparsedMessageReceived(0, data, connection->getConnectionType());
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

    TLObject *object = TLClassStore::TLdeserialize(data, bytes, constructor, error);

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
                object = request->deserializeResponse(data, constructor, error);
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
            DEBUG_D("connection(%p, dc%u, type %d) new session created (first message id: 0x%llx, server salt: 0x%llx, unique id: 0x%llx)", connection, datacenter->getDatacenterId(), connection->getConnectionType(), (uint64_t) response->first_msg_id, (uint64_t) response->server_salt, (uint64_t) response->unique_id);

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
                        delegate->onSessionCreated();
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
                    delegate->onUnparsedMessageReceived(0, innerMessage->unparsedBody.get(), connection->getConnectionType());
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
            DEBUG_D("connection(%p, dc%u, type %d) received push ping", connection, datacenter->getDatacenterId(), connection->getConnectionType());
            sendingPushPing = false;
        } else {
            TL_pong *response = (TL_pong *) message;
            if (response->ping_id == lastPingId) {
                int64_t currentTime = getCurrentTimeMillis();
                int32_t diff = (int32_t) (currentTime / 1000) - pingTime;

                if (abs(diff) < 10) {
                    currentPingTime = (diff + currentPingTime) / 2;
                    if (messageId != 0) {
                        int64_t timeMessage = (int64_t) (messageId / 4294967296.0 * 1000);
                        timeDifference = (int32_t) ((timeMessage - currentTime) / 1000 - currentPingTime / 2);
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
                request->onComplete(response, nullptr);
                request->completed = true;
                runningRequests.erase(iter);
                break;
            }
        }
    } else if (dynamic_cast<DestroySessionRes *>(message)) {
        DestroySessionRes *response = (DestroySessionRes *) message;
        DEBUG_D("destroyed session 0x%llx (%s)", (uint64_t) response->session_id, typeInfo == typeid(TL_destroy_session_ok) ? "ok" : "not found");
    } else if (typeInfo == typeid(TL_rpc_result)) {
        TL_rpc_result *response = (TL_rpc_result *) message;
        int64_t resultMid = response->req_msg_id;

        bool hasResult = response->result.get() != nullptr;
        bool ignoreResult = false;
        if (hasResult) {
            TLObject *object = response->result.get();
            DEBUG_D("connection(%p, dc%u, type %d) received rpc_result with %s", connection, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
        }
        RpcError *error = hasResult ? dynamic_cast<RpcError *>(response->result.get()) : nullptr;
        if (error != nullptr) {
            DEBUG_E("connection(%p, dc%u, type %d) rpc error %d: %s", connection, datacenter->getDatacenterId(), connection->getConnectionType(), error->error_code, error->error_message.c_str());
            if (error->error_code == 303) {
                uint32_t migrateToDatacenterId = DEFAULT_DATACENTER_ID;
                
                static std::vector<std::string> migrateErrors;
                if (migrateErrors.empty()) {
                    migrateErrors.push_back("NETWORK_MIGRATE_");
                    migrateErrors.push_back("PHONE_MIGRATE_");
                    migrateErrors.push_back("USER_MIGRATE_");
                }

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
                            DEBUG_E("request %p rpc error %d: %s", request, error->error_code, error->error_message.c_str());

                            if ((request->requestFlags & RequestFlagFailOnServerErrors) == 0) {
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
                                    request->minStartTime = (int32_t) (getCurrentTimeMillis() / 1000 + waitTime);
                                } else if (error->error_code == 400) {
                                    static std::string waitFailed = "MSG_WAIT_FAILED";
                                    if (error->error_message.find(waitFailed) != std::string::npos) {
                                        discardResponse = true;
                                        request->minStartTime = (int32_t) (getCurrentTimeMillis() / 1000 + 1);
                                        request->startTime = 0;
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
                                request->onComplete(nullptr, implicitError != nullptr ? implicitError : error2);
                                if (error2 != nullptr) {
                                    delete error2;
                                }
                            } else {
                                request->onComplete(response->result.get(), nullptr);
                            }
                        }

                        if (implicitError != nullptr && implicitError->code == 401) {
                            allowInitConnection = false;
                            isError = true;
                            static std::string sessionPasswordNeeded = "SESSION_PASSWORD_NEEDED";
                            if (implicitError->text.find(sessionPasswordNeeded) != std::string::npos) {
                                //ignore this error
                            } else if (datacenter->getDatacenterId() == currentDatacenterId || datacenter->getDatacenterId() == movingToDatacenterId) {
                                if (request->connectionType & ConnectionTypeGeneric && currentUserId) {
                                    currentUserId = 0;
                                    if (delegate != nullptr) {
                                        delegate->onLogout();
                                    }
                                    cleanUp();
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
                        }

                        if (unpacked_data != nullptr) {
                            unpacked_data->reuse();
                        }
                        if (implicitError != nullptr) {
                            delete implicitError;
                        }
                    }

                    if (!discardResponse) {
                        if (allowInitConnection && request->isInitRequest && !isError) {
                            if (datacenter->lastInitVersion != currentVersion) {
                                datacenter->lastInitVersion = currentVersion;
                                saveConfig();
                                DEBUG_D("dc%d init connection completed", datacenter->getDatacenterId());
                            } else {
                                DEBUG_D("dc%d rpc is init, but init connection already completed", datacenter->getDatacenterId());
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
        DEBUG_E("bad message: %d", result->error_code);
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

                datacenter->recreateSessions();
                saveConfig();

                lastOutgoingMessageId = 0;
                clearRequestsForDatacenter(datacenter);
                break;
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
        if (datacenter->hasAuthKey()) {
            processRequestQueue(AllConnectionTypes, datacenter->getDatacenterId());
        }
    } else if (dynamic_cast<MsgDetailedInfo *>(message)) {
        MsgDetailedInfo *response = (MsgDetailedInfo *) message;

        bool requestResend = false;
        bool confirm = true;

        if (typeInfo == typeid(TL_msg_detailed_info)) {
            for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
                Request *request = iter->get();
                if (request->respondsToMessageId(response->msg_id)) {
                    if (request->completed) {
                        break;
                    }
                    int32_t currentTime = (int32_t) (getCurrentTimeMillis() / 1000);
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
            DEBUG_D("connection(%p, dc%u, type %d) received object %s", connection, datacenter->getDatacenterId(), connection->getConnectionType(), typeid(*object).name());
            processServerResponse(object, messageId, messageSeqNo, messageSalt, connection, innerMsgId, containerMessageId);
            delete object;
        } else {
            if (delegate != nullptr) {
                delegate->onUnparsedMessageReceived(messageId, data, connection->getConnectionType());
            }
        }
        data->reuse();
    } else if (typeInfo == typeid(TL_updatesTooLong)) {
        if (connection->connectionType == ConnectionTypePush) {
            if (networkPaused) {
                lastPauseTime = getCurrentTimeMillis();
                DEBUG_D("received internal push: wakeup network in background");
            } else if (lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMillis();
                DEBUG_D("received internal push: reset sleep timeout");
            } else {
                DEBUG_D("received internal push");
            }
            if (delegate != nullptr) {
                delegate->onInternalPushReceived();
            }
        } else {
            if (delegate != nullptr) {
                NativeByteBuffer *data = BuffersStorage::getInstance().getFreeBuffer(message->getObjectSize());
                message->serializeToStream(data);
                data->position(0);
                delegate->onUnparsedMessageReceived(0, data, connection->getConnectionType());
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
        connection = datacenter->getGenericConnection(true);
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
        pingTime = (int32_t) (getCurrentTimeMillis() / 1000);
    }

    NetworkMessage *networkMessage = new NetworkMessage();
    networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
    networkMessage->message->msg_id = generateMessageId();
    networkMessage->message->bytes = request->getObjectSize();
    networkMessage->message->body = std::unique_ptr<TLObject>(request);
    networkMessage->message->seqno = connection->generateMessageSeqNo(false);

    std::vector<std::unique_ptr<NetworkMessage>> array;
    array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    NativeByteBuffer *transportData = datacenter->createRequestsData(array, nullptr, connection);
    if (usePushConnection) {
        DEBUG_D("dc%d send ping to push connection", datacenter->getDatacenterId());
        sendingPushPing = true;
    }
    connection->sendData(transportData, false);
}

bool ConnectionsManager::isIpv6Enabled() {
    return ipv6Enabled;
}

void ConnectionsManager::initDatacenters() {
    Datacenter *datacenter;
    if (!testBackend) {
        if (datacenters.find(1) == datacenters.end()) {
            datacenter = new Datacenter(1);
            datacenter->addAddressAndPort("149.154.175.50", 443, 0);
            datacenter->addAddressAndPort("2001:b28:f23d:f001:0000:0000:0000:000a", 443, 1);
            datacenters[1] = datacenter;
        }

        if (datacenters.find(2) == datacenters.end()) {
            datacenter = new Datacenter(2);
            datacenter->addAddressAndPort("149.154.167.51", 443, 0);
            datacenter->addAddressAndPort("2001:67c:4e8:f002:0000:0000:0000:000a", 443, 1);
            datacenters[2] = datacenter;
        }

        if (datacenters.find(3) == datacenters.end()) {
            datacenter = new Datacenter(3);
            datacenter->addAddressAndPort("149.154.175.100", 443, 0);
            datacenter->addAddressAndPort("2001:b28:f23d:f003:0000:0000:0000:000a", 443, 1);
            datacenters[3] = datacenter;
        }

        if (datacenters.find(4) == datacenters.end()) {
            datacenter = new Datacenter(4);
            datacenter->addAddressAndPort("149.154.167.91", 443, 0);
            datacenter->addAddressAndPort("2001:67c:4e8:f004:0000:0000:0000:000a", 443, 1);
            datacenters[4] = datacenter;
        }

        if (datacenters.find(5) == datacenters.end()) {
            datacenter = new Datacenter(5);
            datacenter->addAddressAndPort("149.154.171.5", 443, 0);
            datacenter->addAddressAndPort("2001:b28:f23f:f005:0000:0000:0000:000a", 443, 1);
            datacenters[5] = datacenter;
        }
    } else {
        if (datacenters.find(1) == datacenters.end()) {
            datacenter = new Datacenter(1);
            datacenter->addAddressAndPort("149.154.175.10", 443, 0);
            datacenter->addAddressAndPort("2001:b28:f23d:f001:0000:0000:0000:000e", 443, 1);
            datacenters[1] = datacenter;
        }

        if (datacenters.find(2) == datacenters.end()) {
            datacenter = new Datacenter(2);
            datacenter->addAddressAndPort("149.154.167.40", 443, 0);
            datacenter->addAddressAndPort("2001:67c:4e8:f002:0000:0000:0000:000e", 443, 1);
            datacenters[2] = datacenter;
        }

        if (datacenters.find(3) == datacenters.end()) {
            datacenter = new Datacenter(3);
            datacenter->addAddressAndPort("149.154.175.117", 443, 0);
            datacenter->addAddressAndPort("2001:b28:f23d:f003:0000:0000:0000:000e", 443, 1);
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

int32_t ConnectionsManager::sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate) {
    int32_t requestToken = lastRequestToken++;
    sendRequest(object, onComplete, onQuickAck, flags, datacenterId, connetionType, immediate, requestToken);
    return requestToken;
}

void ConnectionsManager::sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate, int32_t requestToken) {
    if (!currentUserId && !(flags & RequestFlagWithoutLogin)) {
        DEBUG_D("can't do request without login %s", typeid(*object).name());
        delete object;
        return;
    }
    scheduleTask([&, requestToken, object, onComplete, onQuickAck, flags, datacenterId, connetionType, immediate] {
        Request *request = new Request(requestToken, connetionType, flags, datacenterId, onComplete, onQuickAck);
        request->rawRequest = object;
        request->rpcRequest = wrapInLayer(object, getDatacenterWithId(datacenterId), request);
        requestsQueue.push_back(std::unique_ptr<Request>(request));
        if (immediate) {
            processRequestQueue(0, 0);
        }
    });
}

#ifdef ANDROID
void ConnectionsManager::sendRequest(TLObject *object, onCompleteFunc onComplete, onQuickAckFunc onQuickAck, uint32_t flags, uint32_t datacenterId, ConnectionType connetionType, bool immediate, int32_t requestToken, jobject ptr1, jobject ptr2) {
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
        return;
    }
    scheduleTask([&, requestToken, object, onComplete, onQuickAck, flags, datacenterId, connetionType, immediate, ptr1, ptr2] {
        DEBUG_D("send request %p - %s", object, typeid(*object).name());
        Request *request = new Request(requestToken, connetionType, flags, datacenterId, onComplete, onQuickAck);
        request->rawRequest = object;
        request->ptr1 = ptr1;
        request->ptr2 = ptr2;
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
                cancelRequestInternal(requests[a], true, false);
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
            updateDcSettings(0);
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

void ConnectionsManager::cancelRequestInternal(int32_t token, bool notifyServer, bool removeFromClass) {
    for (requestsIter iter = requestsQueue.begin(); iter != requestsQueue.end(); iter++) {
        Request *request = iter->get();
        if (request->requestToken == token) {
            request->cancelled = true;
            DEBUG_D("cancelled queued rpc request %p - %s", request->rawRequest, typeid(*request->rawRequest).name());
            requestsQueue.erase(iter);
            if (removeFromClass) {
                removeRequestFromGuid(token);
            }
            return;
        }
    }

    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
        Request *request = iter->get();
        if (request->requestToken == token) {
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
            return;
        }
    }
}

void ConnectionsManager::cancelRequest(int32_t token, bool notifyServer) {
    if (token == 0) {
        return;
    }
    scheduleTask([&, token, notifyServer] {
        cancelRequestInternal(token, notifyServer, true);
    });
}

void ConnectionsManager::onDatacenterHandshakeComplete(Datacenter *datacenter, int32_t timeDiff) {
    saveConfig();
    uint32_t datacenterId = datacenter->getDatacenterId();
    if (datacenterId == currentDatacenterId || datacenterId == movingToDatacenterId) {
        timeDifference = timeDiff;
        datacenter->recreateSessions();
        clearRequestsForDatacenter(datacenter);
    }
    processRequestQueue(AllConnectionTypes, datacenterId);
}

void ConnectionsManager::onDatacenterExportAuthorizationComplete(Datacenter *datacenter) {
    saveConfig();
    processRequestQueue(AllConnectionTypes, datacenter->getDatacenterId());
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
            NativeByteBuffer *transportData = datacenter->createRequestsData(currentMessages, reportAck ? &quickAckId : nullptr, connection);

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

                connection->sendData(transportData, reportAck);
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
    sendRequest(request, [&, datacenter](TLObject *response, TL_error *error) {
        std::vector<uint32_t>::iterator iter = std::find(requestingSaltsForDc.begin(), requestingSaltsForDc.end(), datacenter->getDatacenterId());
        if (iter != requestingSaltsForDc.end()) {
            requestingSaltsForDc.erase(iter);
        }
        if (error == nullptr) {
            TL_future_salts *res = (TL_future_salts *) response;
            datacenter->mergeServerSalts(res->salts);
            saveConfig();
        }
    }, nullptr, RequestFlagWithoutLogin | RequestFlagEnableUnauthorized, datacenter->getDatacenterId(), ConnectionTypeGeneric, true);
}

void ConnectionsManager::clearRequestsForDatacenter(Datacenter *datacenter) {
    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end(); iter++) {
        Request *request = iter->get();
        Datacenter *requestDatacenter = getDatacenterWithId(request->datacenterId);
        if (requestDatacenter->getDatacenterId() == datacenter->getDatacenterId()) {
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
    request->token = to_string_uint64(pushSessionId);
    request->app_sandbox = false;

    request->app_version = currentAppVersion;
    request->device_model = currentDeviceModel;
    request->lang_code = currentLangCode;
    request->system_version = currentSystemVersion;
    if (request->lang_code.empty()) {
        request->lang_code = "en";
    }
    if (request->device_model.empty()) {
        request->device_model = "device model unknown";
    }
    if (request->app_version.empty()) {
        request->app_version = "app version unknown";
    }
    if (request->system_version.empty()) {
        request->system_version = "system version unknown";
    }

    sendRequest(request, [&](TLObject *response, TL_error *error) {
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


inline void addMessageToDatacenter(uint32_t datacenterId, NetworkMessage *networkMessage, std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> &genericMessagesToDatacenters) {
    std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter = genericMessagesToDatacenters.find(datacenterId);
    if (iter == genericMessagesToDatacenters.end()) {
        std::vector<std::unique_ptr<NetworkMessage>> &array = genericMessagesToDatacenters[datacenterId] = std::vector<std::unique_ptr<NetworkMessage>>();
        array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    } else {
        iter->second.push_back(std::unique_ptr<NetworkMessage>(networkMessage));
    }
}

void ConnectionsManager::processRequestQueue(uint32_t connectionTypes, uint32_t dc) {
    static std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>> genericMessagesToDatacenters;
    static std::vector<uint32_t> unknownDatacenterIds;
    static std::vector<Datacenter *> neededDatacenters;
    static std::vector<Datacenter *> unauthorizedDatacenters;

    genericMessagesToDatacenters.clear();
    unknownDatacenterIds.clear();
    neededDatacenters.clear();
    unauthorizedDatacenters.clear();

    Connection *genericConnection = nullptr;
    Datacenter *defaultDatacenter = getDatacenterWithId(currentDatacenterId);
    if (defaultDatacenter != nullptr) {
        genericConnection = defaultDatacenter->getGenericConnection(true);
    }

    int32_t currentTime = (int32_t) (getCurrentTimeMillis() / 1000);
    uint32_t genericRunningRequestCount = 0;
    uint32_t uploadRunningRequestCount = 0;
    uint32_t downloadRunningRequestCount = 0;

    for (requestsIter iter = runningRequests.begin(); iter != runningRequests.end();) {
        Request *request = iter->get();
        const std::type_info &typeInfo = typeid(*request->rawRequest);

        switch (request->connectionType & 0x0000ffff) {
            case ConnectionTypeGeneric:
                genericRunningRequestCount++;
                break;
            case ConnectionTypeDownload:
                downloadRunningRequestCount++;
                break;
            case ConnectionTypeUpload:
                uploadRunningRequestCount++;
                break;
            default:
                break;
        }

        uint32_t datacenterId = request->datacenterId;
        if (datacenterId == DEFAULT_DATACENTER_ID) {
            if (movingToDatacenterId != DEFAULT_DATACENTER_ID) {
                iter++;
                continue;
            }
            datacenterId = currentDatacenterId;
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

        Datacenter *requestDatacenter = getDatacenterWithId(datacenterId);
        if (requestDatacenter->lastInitVersion != currentVersion && !request->isInitRequest) {
            request->rpcRequest.release();
            request->rpcRequest = wrapInLayer(request->rawRequest, requestDatacenter, request);
            request->serializedLength = request->getRpcRequest()->getObjectSize();
        }

        if (requestDatacenter == nullptr) {
            if (std::find(unknownDatacenterIds.begin(), unknownDatacenterIds.end(), datacenterId) == unknownDatacenterIds.end()) {
                unknownDatacenterIds.push_back(datacenterId);
            }
            iter++;
            continue;
        } else if (!requestDatacenter->hasAuthKey()) {
            if (std::find(neededDatacenters.begin(), neededDatacenters.end(), requestDatacenter) == neededDatacenters.end()) {
                neededDatacenters.push_back(requestDatacenter);
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

        Connection *connection = requestDatacenter->getConnectionByType(request->connectionType, true);
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
                if (request->connectionType & ConnectionTypeGeneric && request->connectionToken == connection->getConnectionToken()) {
                    DEBUG_D("request token is valid, not retrying %s", typeInfo.name());
                    iter++;
                    continue;
                } else {
                    if (connection->getConnectionToken() != 0 && request->connectionToken == connection->getConnectionToken()) {
                        DEBUG_D("request download token is valid, not retrying %s", typeInfo.name());
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
                        request->onComplete(nullptr, error);
                        delete error;
                        iter = runningRequests.erase(iter);
                        continue;
                    }
                }
            } else {
                request->failedBySalt = false;
            }

            if (request->messageSeqNo == 0) {
                request->messageSeqNo = connection->generateMessageSeqNo(true);
                request->messageId = generateMessageId();
            }
            request->startTime = currentTime;

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
        }
        iter++;
    }

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

        if (request->requestFlags & RequestFlagTryDifferentDc) {
            int32_t requestStartTime = request->startTime;
            int32_t timeout = 30;
            if (updatingDcSettings && dynamic_cast<TL_help_getConfig *>(request->rawRequest)) {
                requestStartTime = updatingDcStartTime;
                timeout = 60;
            } else {
                request->startTime = 0;
            }
            if (requestStartTime != 0 && abs(currentTime - requestStartTime) >= timeout) {
                std::vector<uint32_t> allDc;
                for (std::map<uint32_t, Datacenter *>::iterator iter2 = datacenters.begin(); iter2 != datacenters.end(); iter2++) {
                    if (iter2->first == datacenterId) {
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
        if (requestDatacenter->lastInitVersion != currentVersion && !request->isInitRequest) {
            request->rpcRequest.release();
            request->rpcRequest = wrapInLayer(request->rawRequest, requestDatacenter, request);
        }

        if (requestDatacenter == nullptr) {
            if (std::find(unknownDatacenterIds.begin(), unknownDatacenterIds.end(), datacenterId) == unknownDatacenterIds.end()) {
                unknownDatacenterIds.push_back(datacenterId);
            }
            iter++;
            continue;
        } else if (!requestDatacenter->hasAuthKey()) {
            if (std::find(neededDatacenters.begin(), neededDatacenters.end(), requestDatacenter) == neededDatacenters.end()) {
                neededDatacenters.push_back(requestDatacenter);
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

        Connection *connection = requestDatacenter->getConnectionByType(request->connectionType, true);

        if (request->connectionType & ConnectionTypeGeneric && connection->getConnectionToken() == 0) {
            iter++;
            continue;
        }

        switch (request->connectionType & 0x0000ffff) {
            case ConnectionTypeGeneric:
                if (genericRunningRequestCount >= 60) {
                    iter++;
                    continue;
                }
                genericRunningRequestCount++;
                break;
            case ConnectionTypeDownload:
                if (!networkAvailable || downloadRunningRequestCount >= 5) {
                    iter++;
                    continue;
                }
                downloadRunningRequestCount++;
                break;
            case ConnectionTypeUpload:
                if (!networkAvailable || uploadRunningRequestCount >= 5) {
                    iter++;
                    continue;
                }
                uploadRunningRequestCount++;
                break;
            default:
                break;
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
        request->messageId = generateMessageId();
        request->serializedLength = requestLength;
        request->messageSeqNo = connection->generateMessageSeqNo(true);
        request->startTime = (int32_t) (getCurrentTimeMillis() / 1000);
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
            Connection *connection = datacenter->getGenericConnection(false);
            if (connection != nullptr && connection->getConnectionToken() != 0 && connection->hasMessagesToConfirm()) {
                genericMessagesToDatacenters[datacenter->getDatacenterId()] = std::vector<std::unique_ptr<NetworkMessage>>();
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

            sendMessagesToConnectionWithConfirmation(array, datacenter->getGenericConnection(true), needQuickAck);
        }
    }

    if (connectionTypes == ConnectionTypeGeneric && dc == currentDatacenterId) {
        std::map<uint32_t, std::vector<std::unique_ptr<NetworkMessage>>>::iterator iter2 = genericMessagesToDatacenters.find(currentDatacenterId);
        if (iter2 == genericMessagesToDatacenters.end()) {
            sendPing(getDatacenterWithId(currentDatacenterId), false);
        }
    }

    if (!unknownDatacenterIds.empty()) {
        updateDcSettings(0);
    }

    size_t count = neededDatacenters.size();
    for (uint32_t a = 0; a < count; a++) {
        Datacenter *datacenter = neededDatacenters[a];
        if (datacenter->getDatacenterId() != movingToDatacenterId && !datacenter->isHandshaking() && !datacenter->hasAuthKey()) {
            datacenter->beginHandshake(true);
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
        if (datacenter == nullptr || datacenter->lastInitVersion != currentVersion) {
            if (datacenter->getDatacenterId() == currentDatacenterId) {
                registerForInternalPushUpdates();
            }
            baseRequest->isInitRequest = true;
            initConnection *request = new initConnection();
            request->query = std::unique_ptr<TLObject>(object);
            request->api_id = currentApiId;
            request->app_version = currentAppVersion;
            request->device_model = currentDeviceModel;
            request->lang_code = currentLangCode;
            request->system_version = currentSystemVersion;
            if (request->lang_code.empty()) {
                request->lang_code = "en";
            }
            if (request->device_model.empty()) {
                request->device_model = "device model unknown";
            }
            if (request->app_version.empty()) {
                request->app_version = "app version unknown";
            }
            if (request->system_version.empty()) {
                request->system_version = "system version unknown";
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

void ConnectionsManager::updateDcSettings(uint32_t dcNum) {
    if (updatingDcSettings) {
        return;
    }
    updatingDcStartTime = (int32_t) (getCurrentTimeMillis() / 1000);
    updatingDcSettings = true;
    TL_help_getConfig *request = new TL_help_getConfig();

    sendRequest(request, [&](TLObject *response, TL_error *error) {
        if (!updatingDcSettings) {
            return;
        }

        if (response != nullptr) {
            TL_config *config = (TL_config *) response;
            int32_t updateIn = config->expires - getCurrentTime();
            if (updateIn <= 0) {
                updateIn = 120;
            }
            lastDcUpdateTime = (int32_t) (getCurrentTimeMillis() / 1000) - DC_UPDATE_TIME + updateIn;

            struct DatacenterInfo {
                std::vector<std::string> addressesIpv4;
                std::vector<std::string> addressesIpv6;
                std::vector<std::string> addressesIpv4Download;
                std::vector<std::string> addressesIpv6Download;
                std::map<std::string, uint32_t> ports;

                void addAddressAndPort(std::string address, uint32_t port, uint32_t flags) {
                    std::vector<std::string> *addresses;
                    if ((flags & 2) != 0) {
                        if ((flags & 1) != 0) {
                            addresses = &addressesIpv6Download;
                        } else {
                            addresses = &addressesIpv4Download;
                        }
                    } else {
                        if ((flags & 1) != 0) {
                            addresses = &addressesIpv6;
                        } else {
                            addresses = &addressesIpv4;
                        }
                    }
                    if (std::find(addresses->begin(), addresses->end(), address) != addresses->end()) {
                        return;
                    }
                    addresses->push_back(address);
                    ports[address] = port;
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
                info->addAddressAndPort(dcOption->ip_address, (uint32_t) dcOption->port, (uint32_t) dcOption->flags);
            }

            if (!map.empty()) {
                for (std::map<uint32_t, std::unique_ptr<DatacenterInfo>>::iterator iter = map.begin(); iter != map.end(); iter++) {
                    Datacenter *datacenter = getDatacenterWithId(iter->first);
                    DatacenterInfo *info = iter->second.get();
                    if (datacenter == nullptr) {
                        datacenter = new Datacenter(iter->first);
                        datacenters[iter->first] = datacenter;
                    }
                    datacenter->replaceAddressesAndPorts(info->addressesIpv4, info->ports, 0);
                    datacenter->replaceAddressesAndPorts(info->addressesIpv6, info->ports, 1);
                    datacenter->replaceAddressesAndPorts(info->addressesIpv4Download, info->ports, 2);
                    datacenter->replaceAddressesAndPorts(info->addressesIpv6Download, info->ports, 3);
                    if (iter->first == movingToDatacenterId) {
                        movingToDatacenterId = DEFAULT_DATACENTER_ID;
                        moveToDatacenter(iter->first);
                    }
                }
                saveConfig();
                processRequestQueue(AllConnectionTypes, 0);
            }
            if (delegate != nullptr) {
                delegate->onUpdateConfig(config);
            }
        }
        updatingDcSettings = false;
    }, nullptr, RequestFlagEnableUnauthorized  | RequestFlagWithoutLogin | RequestFlagTryDifferentDc, dcNum == 0 ? currentDatacenterId : dcNum, ConnectionTypeGeneric, true);
}

void ConnectionsManager::moveToDatacenter(uint32_t datacenterId) {
    if (movingToDatacenterId == datacenterId) {
        return;
    }
    movingToDatacenterId = datacenterId;

    Datacenter *currentDatacenter = getDatacenterWithId(currentDatacenterId);
    clearRequestsForDatacenter(currentDatacenter);

    if (currentUserId) {
        TL_auth_exportAuthorization *request = new TL_auth_exportAuthorization();
        request->dc_id = datacenterId;
        sendRequest(request, [&, datacenterId](TLObject *response, TL_error *error) {
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
        updateDcSettings(0);
        return;
    }
    datacenter->recreateSessions();
    clearRequestsForDatacenter(datacenter);

    if (!datacenter->hasAuthKey() && !datacenter->isHandshaking()) {
        datacenter->clearServerSalts();
        datacenter->beginHandshake(true);
    }

    if (movingAuthorization != nullptr) {
        TL_auth_importAuthorization *request = new TL_auth_importAuthorization();
        request->id = currentUserId;
        request->bytes = std::move(movingAuthorization);
        sendRequest(request, [&](TLObject *response, TL_error *error) {
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
    processRequestQueue(0, 0);
}

void ConnectionsManager::applyDatacenterAddress(uint32_t datacenterId, std::string ipAddress, uint32_t port) {
    scheduleTask([&, datacenterId, ipAddress, port] {
        Datacenter *datacenter = getDatacenterWithId(datacenterId);
        if (datacenter != nullptr) {
            std::vector<std::string> addresses;
            std::map<std::string, uint32_t> ports;
            addresses.push_back(ipAddress);
            ports[ipAddress] = port;
            datacenter->replaceAddressesAndPorts(addresses, ports, 0);
            datacenter->suspendConnections();
            updateDcSettings(datacenterId);
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

void ConnectionsManager::init(uint32_t version, int32_t layer, int32_t apiId, std::string deviceModel, std::string systemVersion, std::string appVersion, std::string langCode, std::string configPath, std::string logPath, int32_t userId, bool isPaused, bool enablePushConnection) {
    currentVersion = version;
    currentLayer = layer;
    currentApiId = apiId;
    currentConfigPath = configPath;
    currentDeviceModel = deviceModel;
    currentSystemVersion = systemVersion;
    currentAppVersion = appVersion;
    currentLangCode = langCode;
    currentUserId = userId;
    currentLogPath = logPath;
    pushConnectionEnabled = enablePushConnection;
    if (isPaused) {
        lastPauseTime = getCurrentTimeMillis();
    }

    if (!currentConfigPath.empty() && currentConfigPath.find_last_of('/') != currentConfigPath.size() - 1) {
        currentConfigPath += "/";
    }
    
    if (!logPath.empty()) {
        FileLog::init(logPath);
    }

    loadConfig();

    pthread_create(&networkThread, NULL, (ConnectionsManager::ThreadProc), this);
}

void ConnectionsManager::resumeNetwork(bool partial) {
    scheduleTask([&, partial] {
        if (partial) {
            if (networkPaused) {
                lastPauseTime = getCurrentTimeMillis();
                networkPaused = false;
                DEBUG_D("wakeup network in background");
            } else if (lastPauseTime != 0) {
                lastPauseTime = getCurrentTimeMillis();
                networkPaused = false;
                DEBUG_D("reset sleep timeout");
            }
        } else {
            lastPauseTime = 0;
            networkPaused = false;
        }
    });
}

void ConnectionsManager::pauseNetwork() {
    if (lastPauseTime != 0) {
        return;
    }
    lastPauseTime = getCurrentTimeMillis();
}

void ConnectionsManager::setNetworkAvailable(bool value) {
    scheduleTask([&, value] {
        networkAvailable = value;
        if (!networkAvailable) {
            connectionState = ConnectionStateWaitingForNetwork;
        } else {
            for (std::map<uint32_t, Datacenter *>::iterator iter = datacenters.begin(); iter != datacenters.end(); iter++) {
                if (iter->second->isHandshaking()) {
                    iter->second->createGenericConnection()->connect();
                }
            }
        }
        if (delegate != nullptr) {
            delegate->onConnectionStateChanged(connectionState);
        }
    });
}

void ConnectionsManager::setUseIpv6(bool value) {
    scheduleTask([&, value] {
        ipv6Enabled = value;
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

/*
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
*/
