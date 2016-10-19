/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef DEFINES_H
#define DEFINES_H

#include <functional>
#include <list>
#include <limits.h>
#include <bits/unique_ptr.h>
#include <sstream>

#define USE_DEBUG_SESSION false
#define READ_BUFFER_SIZE 1024 * 128
//#define DEBUG_VERSION
#define DEFAULT_DATACENTER_ID INT_MAX
#define DC_UPDATE_TIME 60 * 60
#define DOWNLOAD_CONNECTIONS_COUNT 2
#define CONNECTION_BACKGROUND_KEEP_TIME 10000

#define DOWNLOAD_CHUNK_SIZE 1024 * 32
#define DOWNLOAD_CHUNK_BIG_SIZE 1024 * 128
#define DOWNLOAD_MAX_REQUESTS 4
#define DOWNLOAD_MAX_BIG_REQUESTS 4
#define DOWNLOAD_BIG_FILE_MIN_SIZE 1024 * 1024

class TLObject;
class TL_error;
class Request;
class TL_message;
class TL_config;
class NativeByteBuffer;
class FileLoadOperation;

typedef std::function<void(TLObject *response, TL_error *error)> onCompleteFunc;
typedef std::function<void()> onQuickAckFunc;
typedef std::list<std::unique_ptr<Request>> requestsList;
typedef requestsList::iterator requestsIter;

typedef struct NetworkMessage {
    std::unique_ptr<TL_message> message;
    bool invokeAfter = false;
    bool needQuickAck = false;
    int32_t requestId;
} NetworkMessage;

enum ConnectionType {
    ConnectionTypeGeneric = 1,
    ConnectionTypeDownload = 2,
    ConnectionTypeUpload = 4,
    ConnectionTypePush = 8,
};

enum ConnectionState {
    ConnectionStateConnecting = 1,
    ConnectionStateWaitingForNetwork = 2,
    ConnectionStateConnected = 3
};

enum EventObjectType {
    EventObjectTypeConnection,
    EventObjectTypeTimer,
    EventObjectTypePipe,
    EventObjectTypeEvent
};

enum FileLoadState {
    FileLoadStateIdle,
    FileLoadStateDownloading,
    FileLoadStateFailed,
    FileLoadStateFinished
};

enum FileLoadFailReason {
    FileLoadFailReasonError,
    FileLoadFailReasonCanceled,
    FileLoadFailReasonRetryLimit
};

typedef std::function<void(std::string path)> onFinishedFunc;
typedef std::function<void(FileLoadFailReason reason)> onFailedFunc;
typedef std::function<void(float progress)> onProgressChangedFunc;

typedef struct ConnectiosManagerDelegate {
    virtual void onUpdate() = 0;
    virtual void onSessionCreated() = 0;
    virtual void onConnectionStateChanged(ConnectionState state) = 0;
    virtual void onUnparsedMessageReceived(int64_t reqMessageId, NativeByteBuffer *buffer, ConnectionType connectionType) = 0;
    virtual void onLogout() = 0;
    virtual void onUpdateConfig(TL_config *config) = 0;
    virtual void onInternalPushReceived() = 0;
} ConnectiosManagerDelegate;

#define AllConnectionTypes ConnectionTypeGeneric | ConnectionTypeDownload | ConnectionTypeUpload

enum RequestFlag {
    RequestFlagEnableUnauthorized = 1,
    RequestFlagFailOnServerErrors = 2,
    RequestFlagCanCompress = 4,
    RequestFlagWithoutLogin = 8,
    RequestFlagTryDifferentDc = 16,
    RequestFlagForceDownload = 32,
    RequestFlagInvokeAfter = 64,
    RequestFlagNeedQuickAck = 128
};

inline std::string to_string_int32(int32_t value) {
    char buf[30];
    int len = sprintf(buf, "%d", value);
    return std::string(buf, len);
}

inline std::string to_string_uint64(uint64_t value) {
    char buf[30];
    int len = sprintf(buf, "%llu", value);
    return std::string(buf, len);
}

#endif
