/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef DATACENTER_H
#define DATACENTER_H

#include <stdint.h>
#include <vector>
#include <map>
#include "Defines.h"

class TL_future_salt;
class Connection;
class NativeByteBuffer;
class TL_future_salt;
class TL_help_configSimple;
class ByteArray;
class TLObject;
class Config;
class Handshake;

class Datacenter : public HandshakeDelegate {

public:
    Datacenter(int32_t instance, uint32_t id);
    Datacenter(int32_t instance, NativeByteBuffer *data);
    uint32_t getDatacenterId();
    TcpAddress *getCurrentAddress(uint32_t flags);
    int32_t getCurrentPort(uint32_t flags);
    void addAddressAndPort(std::string address, uint32_t port, uint32_t flags, std::string secret);
    void nextAddressOrPort(uint32_t flags);
    bool isCustomPort(uint32_t flags);
    void storeCurrentAddressAndPortNum();
    void replaceAddresses(std::vector<TcpAddress> &newAddresses, uint32_t flags);
    void serializeToStream(NativeByteBuffer *stream);
    void clearAuthKey(HandshakeType type);
    void clearServerSalts();
    int64_t getServerSalt();
    void mergeServerSalts(std::vector<std::unique_ptr<TL_future_salt>> &salts);
    void addServerSalt(std::unique_ptr<TL_future_salt> &serverSalt);
    bool containsServerSalt(int64_t value);
    void suspendConnections(bool suspendPush);
    void getSessions(std::vector<int64_t> &sessions);
    void recreateSessions(HandshakeType type);
    void resetAddressAndPortNum();
    bool isHandshakingAny();
    bool isHandshaking(bool media);
    bool isHandshaking(HandshakeType type);
    bool hasAuthKey(ConnectionType connectionTyoe, int32_t allowPendingKey);
    bool hasPermanentAuthKey();
    int64_t getPermanentAuthKeyId();
    bool isExportingAuthorization();
    bool hasMediaAddress();
    void resetInitVersion();

    Connection *getDownloadConnection(uint8_t num, bool create);
    Connection *getProxyConnection(uint8_t num, bool create, bool connect);
    Connection *getUploadConnection(uint8_t num, bool create);
    Connection *getGenericConnection(bool create, int32_t allowPendingKey);
    Connection *getGenericMediaConnection(bool create, int32_t allowPendingKey);
    Connection *getPushConnection(bool create);
    Connection *getTempConnection(bool create);
    Connection *getConnectionByType(uint32_t connectionType, bool create, int32_t allowPendingKey);

    static void aesIgeEncryption(uint8_t *buffer, uint8_t *key, uint8_t *iv, bool encrypt, bool changeIv, uint32_t length);

private:
    void onHandshakeConnectionClosed(Connection *connection);
    void onHandshakeConnectionConnected(Connection *connection);
    void onHandshakeComplete(Handshake *handshake, int64_t keyId, ByteArray *authKey, int32_t timeDifference);
    void processHandshakeResponse(bool media, TLObject *message, int64_t messageId);
    NativeByteBuffer *createRequestsData(std::vector<std::unique_ptr<NetworkMessage>> &requests, int32_t *quickAckId, Connection *connection, bool pfsInit);
    bool decryptServerResponse(int64_t keyId, uint8_t *key, uint8_t *data, uint32_t length, Connection *connection);
    TLObject *getCurrentHandshakeRequest(bool media);
    ByteArray *getAuthKey(ConnectionType connectionType, bool perm, int64_t *authKeyId, int32_t allowPendingKey);

    const int32_t *defaultPorts = new int32_t[4] {-1, 443, 5222, -1};

    int32_t instanceNum;
    uint32_t datacenterId;
    Connection *genericConnection = nullptr;
    Connection *genericMediaConnection = nullptr;
    Connection *tempConnection = nullptr;
    Connection *proxyConnection[PROXY_CONNECTIONS_COUNT];
    Connection *downloadConnection[DOWNLOAD_CONNECTIONS_COUNT];
    Connection *uploadConnection[UPLOAD_CONNECTIONS_COUNT];
    Connection *pushConnection = nullptr;

    uint32_t lastInitVersion = 0;
    uint32_t lastInitMediaVersion = 0;
    bool authorized = false;

    std::vector<TcpAddress> addressesIpv4;
    std::vector<TcpAddress> addressesIpv6;
    std::vector<TcpAddress> addressesIpv4Download;
    std::vector<TcpAddress> addressesIpv6Download;
    std::vector<TcpAddress> addressesIpv4Temp;
    std::vector<std::unique_ptr<TL_future_salt>> serverSalts;
    uint32_t currentPortNumIpv4 = 0;
    uint32_t currentAddressNumIpv4 = 0;
    uint32_t currentPortNumIpv4Temp = 0;
    uint32_t currentAddressNumIpv4Temp = 0;
    uint32_t currentPortNumIpv6 = 0;
    uint32_t currentAddressNumIpv6 = 0;
    uint32_t currentPortNumIpv4Download = 0;
    uint32_t currentAddressNumIpv4Download = 0;
    uint32_t currentPortNumIpv6Download = 0;
    uint32_t currentAddressNumIpv6Download = 0;
    ByteArray *authKeyPerm = nullptr;
    int64_t authKeyPermId = 0;
    ByteArray *authKeyTemp = nullptr;
    int64_t authKeyTempId = 0;
    ByteArray *authKeyMediaTemp = nullptr;
    int64_t authKeyMediaTempId = 0;
    Config *config = nullptr;
    bool isCdnDatacenter = false;

    std::vector<std::unique_ptr<Handshake>> handshakes;

    const uint32_t configVersion = 11;
    const uint32_t paramsConfigVersion = 1;

    Connection *createProxyConnection(uint8_t num);
    Connection *createDownloadConnection(uint8_t num);
    Connection *createUploadConnection(uint8_t num);
    Connection *createGenericConnection();
    Connection *createGenericMediaConnection();
    Connection *createTempConnection();
    Connection *createPushConnection();
    Connection *createConnectionByType(uint32_t connectionType);

    void beginHandshake(HandshakeType handshakeType, bool reconnect);

    bool exportingAuthorization = false;
    void exportAuthorization();

    static TL_help_configSimple *decodeSimpleConfig(NativeByteBuffer *buffer);

    friend class ConnectionsManager;
    friend class Connection;
    friend class Handshake;
    friend class Request;
};

#endif
