/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef HANDSHAKE_H
#define HANDSHAKE_H

#include <stdint.h>
#include "Defines.h"

class Datacenter;
class ByteArray;
class TLObject;
class TL_future_salt;
class Connection;

class Handshake {

public:

    Handshake(Datacenter *datacenter, HandshakeType type, HandshakeDelegate *handshakeDelegate);
    ~Handshake();
    void beginHandshake(bool reconnect);
    void cleanupHandshake();
    void processHandshakeResponse(TLObject *message, int64_t messageId);
    void processHandshakeResponse_resPQ(TLObject *message, int64_t messageId);
    void processHandshakeResponse_serverDHParams(TLObject *message, int64_t messageId);
    void processHandshakeResponse_serverDHParamsAnswer(TLObject *message, int64_t messageId);
    void onHandshakeConnectionConnected();
    void onHandshakeConnectionClosed();
    static void cleanupServerKeys();
    HandshakeType getType();
    ByteArray *getPendingAuthKey();
    int64_t getPendingAuthKeyId();
    TLObject *getCurrentHandshakeRequest();

private:

    Datacenter *currentDatacenter;
    HandshakeType handshakeType;
    HandshakeDelegate *delegate;

    uint8_t handshakeState = 0;
    TLObject *handshakeRequest = nullptr;
    ByteArray *authNonce = nullptr;
    ByteArray *authServerNonce = nullptr;
    ByteArray *authNewNonce = nullptr;
    ByteArray *handshakeAuthKey = nullptr;
    TL_future_salt *handshakeServerSalt = nullptr;
    int32_t timeDifference = 0;
    ByteArray *authKeyTempPending = nullptr;
    int64_t authKeyTempPendingId = 0;
    int32_t authKeyPendingRequestId = 0;
    int64_t authKeyPendingMessageId = 0;
    bool needResendData = false;

    void sendRequestData(TLObject *object, bool important);
    void sendAckRequest(int64_t messageId);

    static void saveCdnConfig(Datacenter *datacenter);
    static void saveCdnConfigInternal(NativeByteBuffer *buffer);
    static void loadCdnConfig(Datacenter *datacenter);

    inline Connection *getConnection();
};

#endif
