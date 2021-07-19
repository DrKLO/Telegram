/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <algorithm>
#include "Request.h"
#include "TLObject.h"
#include "MTProtoScheme.h"
#include "ConnectionsManager.h"
#include "Datacenter.h"
#include "Connection.h"

Request::Request(int32_t instance, int32_t token, ConnectionType type, uint32_t flags, uint32_t datacenter, onCompleteFunc completeFunc, onQuickAckFunc quickAckFunc, onWriteToSocketFunc writeToSocketFunc) {
    requestToken = token;
    connectionType = type;
    requestFlags = flags;
    datacenterId = datacenter;
    onCompleteRequestCallback = completeFunc;
    onQuickAckCallback = quickAckFunc;
    onWriteToSocketCallback = writeToSocketFunc;
    dataType = (uint8_t) (requestFlags >> 24);
    instanceNum = instance;
}

Request::~Request() {
#ifdef ANDROID
    if (ptr1 != nullptr) {
        jniEnv[instanceNum]->DeleteGlobalRef(ptr1);
        ptr1 = nullptr;
    }
    if (ptr2 != nullptr) {
        jniEnv[instanceNum]->DeleteGlobalRef(ptr2);
        ptr2 = nullptr;
    }
    if (ptr3 != nullptr) {
        jniEnv[instanceNum]->DeleteGlobalRef(ptr3);
        ptr3 = nullptr;
    }
#endif
}

void Request::addRespondMessageId(int64_t id) {
    respondsToMessageIds.push_back(messageId);
}

bool Request::respondsToMessageId(int64_t id) {
    return messageId == id || std::find(respondsToMessageIds.begin(), respondsToMessageIds.end(), id) != respondsToMessageIds.end();
}

void Request::clear(bool time) {
    messageId = 0;
    messageSeqNo = 0;
    connectionToken = 0;
    if (time) {
        startTime = 0;
        minStartTime = 0;
    }
}

void Request::onComplete(TLObject *result, TL_error *error, int32_t networkType, int64_t responseTime) {
    if (onCompleteRequestCallback != nullptr && (result != nullptr || error != nullptr)) {
        onCompleteRequestCallback(result, error, networkType, responseTime);
    }
}

void Request::onWriteToSocket() {
    if (onWriteToSocketCallback != nullptr) {
        onWriteToSocketCallback();
    }
}

bool Request::hasInitFlag() {
    return isInitRequest || isInitMediaRequest;
}

bool Request::isMediaRequest() {
    return Connection::isMediaConnectionType(connectionType);
}

bool Request::needInitRequest(Datacenter *datacenter, uint32_t currentVersion) {
    bool media = PFS_ENABLED && datacenter != nullptr && isMediaRequest() && datacenter->hasMediaAddress();
    return !media && datacenter->lastInitVersion != currentVersion || media && datacenter->lastInitMediaVersion != currentVersion;
}

void Request::onQuickAck() {
    if (onQuickAckCallback != nullptr) {
        onQuickAckCallback();
    }
}

TLObject *Request::getRpcRequest() {
    return rpcRequest.get();
}
