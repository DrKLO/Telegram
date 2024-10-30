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
#include "FileLog.h"

Request::Request(int32_t instance, int32_t token, ConnectionType type, uint32_t flags, uint32_t datacenter, onCompleteFunc completeFunc, onQuickAckFunc quickAckFunc, onWriteToSocketFunc writeToSocketFunc, onRequestClearFunc onClearFunc) {
    requestToken = token;
    connectionType = type;
    requestFlags = flags;
    datacenterId = datacenter;
    onCompleteRequestCallback = completeFunc;
    onQuickAckCallback = quickAckFunc;
    onWriteToSocketCallback = writeToSocketFunc;
    onRequestClearCallback = onClearFunc;
    dataType = (uint8_t) (requestFlags >> 24);
    instanceNum = instance;
}

Request::~Request() {
    if (!completedSent && !disableClearCallback && onRequestClearCallback != nullptr) {
        onRequestClearCallback();
    }
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

void Request::onComplete(TLObject *result, TL_error *error, int32_t networkType, int64_t responseTime, int64_t requestMsgId, int32_t dcId) {
    if (onCompleteRequestCallback != nullptr && (result != nullptr || error != nullptr)) {
        completedSent = true;
        onCompleteRequestCallback(result, error, networkType, responseTime, requestMsgId, dcId);
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

bool Request::isCancelRequest() {
    return (requestFlags & RequestFlagIsCancel) != 0;
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
