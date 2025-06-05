/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef DEFINES_H
#define DEFINES_H

#include <functional>
#include <list>
#include <limits.h>
#include <sstream>
#include <inttypes.h>
#include "ByteArray.h"

#define USE_DEBUG_SESSION false
#define READ_BUFFER_SIZE 1024 * 1024 * 2
//#define DEBUG_VERSION
#define PFS_ENABLED 1
#define DEFAULT_DATACENTER_ID INT_MAX
#define DC_UPDATE_TIME 60 * 60
#define TEMP_AUTH_KEY_EXPIRE_TIME 24 * 60 * 60
#define PROXY_CONNECTIONS_COUNT 4
#define DOWNLOAD_CONNECTIONS_COUNT 2
#define UPLOAD_CONNECTIONS_COUNT 4
#define CONNECTION_BACKGROUND_KEEP_TIME 10000
#define MAX_ACCOUNT_COUNT 5
#define USE_DELEGATE_HOST_RESOLVE

#define USE_IPV4_ONLY 0
#define USE_IPV6_ONLY 1
#define USE_IPV4_IPV6_RANDOM 2

#define NETWORK_TYPE_MOBILE 0
#define NETWORK_TYPE_WIFI 1
#define NETWORK_TYPE_ROAMING 2

class TLObject;
class TL_error;
class Request;
class TL_message;
class TL_config;
class NativeByteBuffer;
class Handshake;
class ConnectionSocket;

typedef std::function<void(TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime, int64_t msgId, int32_t dcId)> onCompleteFunc;
typedef std::function<void()> onQuickAckFunc;
typedef std::function<void()> onWriteToSocketFunc;
typedef std::function<void()> onRequestClearFunc;
typedef std::function<void()> onRequestCancelDoneFunc;
typedef std::function<void(int64_t messageId)> fillParamsFunc;
typedef std::function<void(int64_t requestTime)> onRequestTimeFunc;
typedef std::list<std::unique_ptr<Request>> requestsList;
typedef requestsList::iterator requestsIter;

typedef struct NetworkMessage {
    std::unique_ptr<TL_message> message;
    bool invokeAfter = false;
    bool needQuickAck = false;
    bool forceContainer = false;
    int32_t requestId;
} NetworkMessage;

enum ConnectionType {
    ConnectionTypeGeneric = 1,
    ConnectionTypeDownload = 2,
    ConnectionTypeUpload = 4,
    ConnectionTypePush = 8,
    ConnectionTypeTemp = 16,
    ConnectionTypeProxy = 32,
    ConnectionTypeGenericMedia = 64
};

enum TcpAddressFlag {
    TcpAddressFlagIpv6 = 1,
    TcpAddressFlagDownload = 2,
    TcpAddressFlagO = 4,
    TcpAddressFlagCdn = 8,
    TcpAddressFlagStatic = 16,
    TcpAddressFlagTemp = 2048
};

enum ConnectionState {
    ConnectionStateConnecting = 1,
    ConnectionStateWaitingForNetwork = 2,
    ConnectionStateConnected = 3,
    ConnectionStateConnectingViaProxy = 4
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

enum HandshakeType {
    HandshakeTypePerm,
    HandshakeTypeTemp,
    HandshakeTypeMediaTemp,
    HandshakeTypeCurrent,
    HandshakeTypeAll
};

class TcpAddress {

public:
    std::string address;
    int32_t flags;
    int32_t port;
    std::string secret;

    TcpAddress(std::string addr, int32_t p, int32_t f, std::string s) {
        address = addr;
        port = p;
        flags = f;
        secret = s;
    }
};

typedef std::function<void(std::string path)> onFinishedFunc;
typedef std::function<void(FileLoadFailReason reason)> onFailedFunc;
typedef std::function<void(float progress)> onProgressChangedFunc;

typedef struct ConnectiosManagerDelegate {
    virtual void onUpdate(int32_t instanceNum) = 0;
    virtual void onSessionCreated(int32_t instanceNum) = 0;
    virtual void onConnectionStateChanged(ConnectionState state, int32_t instanceNum) = 0;
    virtual void onUnparsedMessageReceived(int64_t reqMessageId, NativeByteBuffer *buffer, ConnectionType connectionType, int32_t instanceNum) = 0;
    virtual void onLogout(int32_t instanceNum) = 0;
    virtual void onUpdateConfig(TL_config *config, int32_t instanceNum) = 0;
    virtual void onInternalPushReceived(int32_t instanceNum) = 0;
    virtual void onBytesSent(int32_t amount, int32_t networkType, int32_t instanceNum) = 0;
    virtual void onBytesReceived(int32_t amount, int32_t networkType, int32_t instanceNum) = 0;
    virtual void onRequestNewServerIpAndPort(int32_t second, int32_t instanceNum) = 0;
    virtual void onProxyError(int32_t instanceNum) = 0;
    virtual void getHostByName(std::string domain, int32_t instanceNum, ConnectionSocket *socket) = 0;
    virtual int32_t getInitFlags(int32_t instanceNum) = 0;
    virtual void onPremiumFloodWait(int32_t instanceNum, int32_t requestToken, bool isUpload) = 0;
    virtual void onIntegrityCheckClassic(int32_t instanceNum, int32_t requestToken, std::string project, std::string nonce) = 0;
    virtual void onCaptchaCheck(int32_t instanceNum, int32_t requestToken, std::string action, std::string key_id) = 0;
} ConnectiosManagerDelegate;

typedef struct HandshakeDelegate {
    virtual void onHandshakeComplete(Handshake *handshake, int64_t keyId, ByteArray *authKey, int32_t timeDifference) = 0;
} HandshakeDelegate;

#define AllConnectionTypes ConnectionTypeGeneric | ConnectionTypeDownload | ConnectionTypeUpload

enum RequestFlag {
    RequestFlagEnableUnauthorized = 1,
    RequestFlagFailOnServerErrors = 2,
    RequestFlagCanCompress = 4,
    RequestFlagWithoutLogin = 8,
    RequestFlagTryDifferentDc = 16,
    RequestFlagForceDownload = 32,
    RequestFlagInvokeAfter = 64,
    RequestFlagNeedQuickAck = 128,
    RequestFlagUseUnboundKey = 256,
    RequestFlagResendAfter = 512,
    RequestFlagIgnoreFloodWait = 1024,
    RequestFlagListenAfterCancel = 2048,
    RequestFlagIsCancel = 32768,
    RequestFlagFailOnServerErrorsExceptFloodWait = 65536
};

inline std::string to_string_int32(int32_t value) {
    char buf[30];
    int len = sprintf(buf, "%d", value);
    return std::string(buf, (uint32_t) len);
}

inline std::string to_string_uint64(uint64_t value) {
    char buf[30];
    int len = sprintf(buf, "%" PRIu64, value);
    return std::string(buf, (uint32_t) len);
}

inline int32_t char2int(char input) {
    if (input >= '0' && input <= '9') {
        return input - '0';
    } else if (input >= 'A' && input <= 'F') {
        return (char) (input - 'A' + 10);
    } else if (input >= 'a' && input <= 'f') {
        return (char) (input - 'a' + 10);
    }
    return 0;
}

#endif
