/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <algorithm>
#include "Request.h"
#include "TLObject.h"
#include "MTProtoScheme.h"
#include "ConnectionsManager.h"

Request::Request(int32_t token, ConnectionType type, uint32_t flags, uint32_t datacenter, onCompleteFunc completeFunc, onQuickAckFunc quickAckFunc, onWriteToSocketFunc writeToSocketFunc) {
    requestToken = token;
    connectionType = type;
    requestFlags = flags;
    datacenterId = datacenter;
    onCompleteRequestCallback = completeFunc;
    onQuickAckCallback = quickAckFunc;
    onWriteToSocketCallback = writeToSocketFunc;
    dataType = (uint8_t) (requestFlags >> 24);
}

Request::~Request() {
#ifdef ANDROID
    if (ptr1 != nullptr) {
        jniEnv->DeleteGlobalRef(ptr1);
        ptr1 = nullptr;
    }
    if (ptr2 != nullptr) {
        jniEnv->DeleteGlobalRef(ptr2);
        ptr2 = nullptr;
    }
    if (ptr3 != nullptr) {
        jniEnv->DeleteGlobalRef(ptr3);
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

void Request::onComplete(TLObject *result, TL_error *error, int32_t networkType) {
    if (onCompleteRequestCallback != nullptr && (result != nullptr || error != nullptr)) {
        onCompleteRequestCallback(result, error, networkType);
    }
}

void Request::onWriteToSocket() {
    if (onWriteToSocketCallback != nullptr) {
        onWriteToSocketCallback();
    }
}

void Request::onQuickAck() {
    if (onQuickAckCallback != nullptr) {
        onQuickAckCallback();
    }
}

TLObject *Request::getRpcRequest() {
    return rpcRequest.get();
}
