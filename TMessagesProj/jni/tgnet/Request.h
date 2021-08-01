/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef REQUEST_H
#define REQUEST_H

#include <stdint.h>
#include <vector>
#include "Defines.h"

#ifdef ANDROID
#include <jni.h>
#endif

class TLObject;
class TL_error;
class Datacenter;

class Request {

public:
    Request(int32_t instance, int32_t token, ConnectionType type, uint32_t flags, uint32_t datacenter, onCompleteFunc completeFunc, onQuickAckFunc quickAckFunc, onWriteToSocketFunc writeToSocketFunc);
    ~Request();

    int64_t messageId = 0;
    int32_t messageSeqNo = 0;
    uint32_t datacenterId = 0;
    uint32_t connectionToken = 0;
    int32_t requestToken = 0;
    uint32_t retryCount = 0;
    bool failedBySalt = false;
    int32_t failedByFloodWait = 0;
    ConnectionType connectionType;
    uint32_t requestFlags;
    bool completed = false;
    bool cancelled = false;
    bool isInitRequest = false;
    bool isInitMediaRequest = false;
    uint8_t dataType = 0;
    int32_t serializedLength = 0;
    int32_t startTime = 0;
    int64_t startTimeMillis = 0;
    int32_t minStartTime = 0;
    int32_t lastResendTime = 0;
    bool isResending = false;
    int32_t instanceNum = 0;
    uint32_t serverFailureCount = 0;
    TLObject *rawRequest;
    std::unique_ptr<TLObject> rpcRequest;
    onCompleteFunc onCompleteRequestCallback;
    onQuickAckFunc onQuickAckCallback;
    onWriteToSocketFunc onWriteToSocketCallback;

    void addRespondMessageId(int64_t id);
    bool respondsToMessageId(int64_t id);
    void clear(bool time);
    void onComplete(TLObject *result, TL_error *error, int32_t networkType, int64_t responseTime);
    void onQuickAck();
    void onWriteToSocket();
    bool isMediaRequest();
    bool hasInitFlag();
    bool needInitRequest(Datacenter *datacenter, uint32_t currentVersion);
    TLObject *getRpcRequest();

#ifdef ANDROID
    jobject ptr1 = nullptr;
    jobject ptr2 = nullptr;
    jobject ptr3 = nullptr;
#endif

private:
    std::vector<int64_t> respondsToMessageIds;
};

#endif
