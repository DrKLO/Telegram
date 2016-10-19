/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef DATACENTER_H
#define DATACENTER_H

#include <stdint.h>
#include <vector>
#include <map>
#include <bits/unique_ptr.h>
#include "Defines.h"

class TL_future_salt;
class Connection;
class NativeByteBuffer;
class TL_future_salt;
class ByteArray;
class TLObject;
class Config;

class Datacenter {

public:
    Datacenter(uint32_t id);
    Datacenter(NativeByteBuffer *data);
    void switchTo443Port();
    uint32_t getDatacenterId();
    std::string getCurrentAddress(uint32_t flags);
    int32_t getCurrentPort(uint32_t flags);
    void addAddressAndPort(std::string address, uint32_t port, uint32_t flags);
    void nextAddressOrPort(uint32_t flags);
    void storeCurrentAddressAndPortNum();
    void replaceAddressesAndPorts(std::vector<std::string> &newAddresses, std::map<std::string, uint32_t> &newPorts, uint32_t flags);
    void serializeToStream(NativeByteBuffer *stream);
    void clear();
    void clearServerSalts();
    int64_t getServerSalt();
    void mergeServerSalts(std::vector<std::unique_ptr<TL_future_salt>> &salts);
    void addServerSalt(std::unique_ptr<TL_future_salt> &serverSalt);
    bool containsServerSalt(int64_t value);
    void suspendConnections();
    void getSessions(std::vector<int64_t> &sessions);
    void recreateSessions();
    bool isHandshaking();
    bool hasAuthKey();
    bool isExportingAuthorization();

    Connection *getDownloadConnection(uint32_t num, bool create);
    Connection *getUploadConnection(bool create);
    Connection *getGenericConnection(bool create);
    Connection *getPushConnection(bool create);
    Connection *getConnectionByType(uint32_t connectionType, bool create);
    
    static void aesIgeEncryption(uint8_t *buffer, uint8_t *key, uint8_t *iv, bool encrypt, bool changeIv, uint32_t length);

private:
    void onHandshakeConnectionClosed(Connection *connection);
    void onHandshakeConnectionConnected(Connection *connection);
    void processHandshakeResponse(TLObject *message, int64_t messageId);
    NativeByteBuffer *createRequestsData(std::vector<std::unique_ptr<NetworkMessage>> &requests, int32_t *quickAckId, Connection *connection);
    bool decryptServerResponse(int64_t keyId, uint8_t *key, uint8_t *data, uint32_t length);
    TLObject *getCurrentHandshakeRequest();

    const int32_t *defaultPorts = new int32_t[11] {-1, 80, -1, 443, -1, 443, -1, 80, -1, 443, -1};
    const int32_t *defaultPorts8888 = new int32_t[11] {-1, 8888, -1, 443, -1, 8888,  -1, 80, -1, 8888, -1};

    uint32_t datacenterId;
    Connection *genericConnection = nullptr;
    Connection *downloadConnections[DOWNLOAD_CONNECTIONS_COUNT];
    Connection *uploadConnection = nullptr;
    Connection *pushConnection = nullptr;

    uint32_t lastInitVersion = 0;
    bool authorized = false;

    std::vector<std::string> addressesIpv4;
    std::vector<std::string> addressesIpv6;
    std::vector<std::string> addressesIpv4Download;
    std::vector<std::string> addressesIpv6Download;
    std::map<std::string, uint32_t> ports;
    std::vector<std::unique_ptr<TL_future_salt>> serverSalts;
    uint32_t currentPortNumIpv4 = 0;
    uint32_t currentAddressNumIpv4 = 0;
    uint32_t currentPortNumIpv6 = 0;
    uint32_t currentAddressNumIpv6 = 0;
    uint32_t currentPortNumIpv4Download = 0;
    uint32_t currentAddressNumIpv4Download = 0;
    uint32_t currentPortNumIpv6Download = 0;
    uint32_t currentAddressNumIpv6Download = 0;
    ByteArray *authKey = nullptr;
    int64_t authKeyId = 0;
    int32_t overridePort = -1;
    Config *config = nullptr;

    const uint32_t configVersion = 5;
    const uint32_t paramsConfigVersion = 1;

    Connection *createDownloadConnection(uint32_t num);
    Connection *createUploadConnection();
    Connection *createGenericConnection();
    Connection *createPushConnection();
    Connection *createConnectionByType(uint32_t connectionType);

    uint8_t handshakeState = 0;
    TLObject *handshakeRequest = nullptr;
    ByteArray *authNonce = nullptr;
    ByteArray *authServerNonce = nullptr;
    ByteArray *authNewNonce = nullptr;
    ByteArray *handshakeAuthKey = nullptr;
    TL_future_salt *handshakeServerSalt = nullptr;
    int32_t timeDifference = 0;
    bool needResendData = false;
    void beginHandshake(bool reconnect);
    void sendRequestData(TLObject *object, bool important);
    void cleanupHandshake();
    void sendAckRequest(int64_t messageId);
    int32_t selectPublicKey(std::vector<int64_t> &fingerprints);

    bool exportingAuthorization = false;
    void exportAuthorization();

    friend class ConnectionsManager;
};

#endif
