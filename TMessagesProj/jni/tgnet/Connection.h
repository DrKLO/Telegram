/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
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
class ByteArray;

class Connection : public ConnectionSession, public ConnectionSocket {

public:

    Connection(Datacenter *datacenter, ConnectionType type, int8_t num);
    ~Connection();

    void connect();
    void suspendConnection();
    void suspendConnection(bool idle);
    void sendData(NativeByteBuffer *buffer, bool reportAck, bool encrypted);
    bool hasUsefullData();
    void setHasUsefullData();
    bool allowsCustomPadding();
    uint32_t getConnectionToken();
    ConnectionType getConnectionType();
    int8_t getConnectionNum();
    Datacenter *getDatacenter();
    bool isSuspended();
    static bool isMediaConnectionType(ConnectionType type);

protected:

    void onReceivedData(NativeByteBuffer *buffer) override;
    void onDisconnected(int32_t reason, int32_t error) override;
    void onConnected() override;
    bool hasPendingRequests() override;
    void reconnect();

private:

    enum TcpConnectionState {
        TcpConnectionStageIdle,
        TcpConnectionStageConnecting,
        TcpConnectionStageReconnecting,
        TcpConnectionStageConnected,
        TcpConnectionStageSuspended
    };

    enum ProtocolType {
        ProtocolTypeEF,
        ProtocolTypeEE,
        ProtocolTypeDD,
        ProtocolTypeTLS
    };

    inline void encryptKeyWithSecret(uint8_t *array, uint8_t secretType);
    inline std::string *getCurrentSecret(uint8_t secretType);
    void onDisconnectedInternal(int32_t reason, int32_t error);

    ProtocolType currentProtocolType = ProtocolTypeEE;

    TcpConnectionState connectionState = TcpConnectionStageIdle;
    uint32_t connectionToken = 0;
    std::string hostAddress;
    std::string secret;
    uint16_t hostPort;
    uint16_t failedConnectionCount;
    Datacenter *currentDatacenter;
    uint32_t currentAddressFlags;
    ConnectionType connectionType;
    int8_t connectionNum;
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
    bool isMediaConnection = false;
    bool waitForReconnectTimer = false;
    bool connectionInProcess = false;
    uint32_t lastReconnectTimeout = 100;
    int64_t usefullDataReceiveTime;
    uint32_t currentTimeout = 4;
    uint32_t receivedDataAmount = 0;
    uint32_t generation = 0;

    uint8_t temp[64];

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
