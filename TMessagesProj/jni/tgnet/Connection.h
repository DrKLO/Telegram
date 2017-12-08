/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef CONNECTION_H
#define CONNECTION_H

#include <pthread.h>
#include <vector>
#include <string>
#include <openssl/aes.h>
#include "ConnectionSession.h"
#include "ConnectionSocket.h"
#include "Defines.h"

class Datacenter;
class Timer;
class ByteStream;

class Connection : public ConnectionSession, public ConnectionSocket {

public:

    Connection(Datacenter *datacenter, ConnectionType type);
    ~Connection();

    void connect();
    void suspendConnection();
    void suspendConnection(bool idle);
    void sendData(NativeByteBuffer *buffer, bool reportAck);
    bool hasUsefullData();
    void setHasUsefullData();
    uint32_t getConnectionToken();
    ConnectionType getConnectionType();
    Datacenter *getDatacenter();

protected:

    void onReceivedData(NativeByteBuffer *buffer) override;
    void onDisconnected(int reason) override;
    void onConnected() override;
    void reconnect();

private:

    enum TcpConnectionState {
        TcpConnectionStageIdle,
        TcpConnectionStageConnecting,
        TcpConnectionStageReconnecting,
        TcpConnectionStageConnected,
        TcpConnectionStageSuspended
    };

    TcpConnectionState connectionState = TcpConnectionStageIdle;
    uint32_t connectionToken = 0;
    std::string hostAddress;
    uint16_t hostPort;
    uint16_t failedConnectionCount;
    Datacenter *currentDatacenter;
    uint32_t currentAddressFlags;
    ConnectionType connectionType;
    bool firstPacketSent = false;
    NativeByteBuffer *restOfTheData = nullptr;
    uint32_t lastPacketLength = 0;
    bool hasSomeDataSinceLastConnect = false;
    bool isTryingNextPort = false;
    bool wasConnected = false;
    uint32_t willRetryConnectCount = 5;
    Timer *reconnectTimer;
    bool usefullData = false;
    bool forceNextPort = false;
    
    AES_KEY encryptKey;
    uint8_t encryptIv[16];
    uint32_t encryptNum;
    uint8_t encryptCount[16];
    
    AES_KEY decryptKey;
    uint8_t decryptIv[16];
    uint32_t decryptNum;
    uint8_t decryptCount[16];

    friend class ConnectionsManager;
};

#endif
