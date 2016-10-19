/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <stdlib.h>
#include <algorithm>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/bn.h>
#include <openssl/pem.h>
#include <openssl/aes.h>
#include <memory.h>
#include "Datacenter.h"
#include "Connection.h"
#include "MTProtoScheme.h"
#include "ApiScheme.h"
#include "FileLog.h"
#include "NativeByteBuffer.h"
#include "ByteArray.h"
#include "BuffersStorage.h"
#include "ConnectionsManager.h"
#include "Config.h"

static std::vector<std::string> serverPublicKeys;
static std::vector<uint64_t> serverPublicKeysFingerprints;
static BN_CTX *bnContext;

Datacenter::Datacenter(uint32_t id) {
    datacenterId = id;
    for (uint32_t a = 0; a < DOWNLOAD_CONNECTIONS_COUNT; a++) {
        downloadConnections[a] = nullptr;
    }
}

Datacenter::Datacenter(NativeByteBuffer *data) {
    for (uint32_t a = 0; a < DOWNLOAD_CONNECTIONS_COUNT; a++) {
        downloadConnections[a] = nullptr; 
    }
    uint32_t currentVersion = data->readUint32(nullptr);
    if (currentVersion >= 2 && currentVersion <= 5) {
        datacenterId = data->readUint32(nullptr);
        if (currentVersion >= 3) {
            lastInitVersion = data->readUint32(nullptr);
        }
        uint32_t len = data->readUint32(nullptr);
        for (uint32_t a = 0; a < len; a++) {
            std::string address = data->readString(nullptr);
            addressesIpv4.push_back(address);
            ports[address] = data->readUint32(nullptr);
        }
        if (currentVersion >= 5) {
            len = data->readUint32(nullptr);
            for (uint32_t a = 0; a < len; a++) {
                std::string address = data->readString(nullptr);
                addressesIpv6.push_back(address);
                ports[address] = data->readUint32(nullptr);
            }
            len = data->readUint32(nullptr);
            for (uint32_t a = 0; a < len; a++) {
                std::string address = data->readString(nullptr);
                addressesIpv4Download.push_back(address);
                ports[address] = data->readUint32(nullptr);
            }
            len = data->readUint32(nullptr);
            for (uint32_t a = 0; a < len; a++) {
                std::string address = data->readString(nullptr);
                addressesIpv6Download.push_back(address);
                ports[address] = data->readUint32(nullptr);
            }
        }
        len = data->readUint32(nullptr);
        if (len != 0) {
            authKey = data->readBytes(len, nullptr);
        }
        if (currentVersion >= 4) {
            authKeyId = data->readInt64(nullptr);
        } else {
            len = data->readUint32(nullptr);
            if (len != 0) {
                authKeyId = data->readInt64(nullptr);
            }
        }
        authorized = data->readInt32(nullptr) != 0;
        len = data->readUint32(nullptr);
        for (uint32_t a = 0; a < len; a++) {
            TL_future_salt *salt = new TL_future_salt();
            salt->valid_since = data->readInt32(nullptr);
            salt->valid_until = data->readInt32(nullptr);
            salt->salt = data->readInt64(nullptr);
            serverSalts.push_back(std::unique_ptr<TL_future_salt>(salt));
        }
    }

    if (config == nullptr) {
        config = new Config("dc" + to_string_int32(datacenterId) + "conf.dat");
    }
    NativeByteBuffer *buffer = config->readConfig();
    if (buffer != nullptr) {
        uint32_t version = buffer->readUint32(nullptr);
        if (version <= paramsConfigVersion) {
            currentPortNumIpv4 = buffer->readUint32(nullptr);
            currentAddressNumIpv4 = buffer->readUint32(nullptr);
            currentPortNumIpv6 = buffer->readUint32(nullptr);
            currentAddressNumIpv6 = buffer->readUint32(nullptr);
            currentPortNumIpv4Download = buffer->readUint32(nullptr);
            currentAddressNumIpv4Download = buffer->readUint32(nullptr);
            currentPortNumIpv6Download = buffer->readUint32(nullptr);
            currentAddressNumIpv6Download = buffer->readUint32(nullptr);
        }
        buffer->reuse();
    } else {
        currentPortNumIpv4 = 0;
        currentAddressNumIpv4 = 0;
        currentPortNumIpv6 = 0;
        currentAddressNumIpv6 = 0;
        currentPortNumIpv4Download = 0;
        currentAddressNumIpv4Download = 0;
        currentPortNumIpv6Download = 0;
        currentAddressNumIpv6Download = 0;
    }
}

void Datacenter::switchTo443Port() {
    for (uint32_t a = 0; a < addressesIpv4.size(); a++) {
        if (ports[addressesIpv4[a]] == 443) {
            currentAddressNumIpv4 = a;
            currentPortNumIpv4 = 0;
            break;
        }
    }
    for (uint32_t a = 0; a < addressesIpv6.size(); a++) {
        if (ports[addressesIpv6[a]] == 443) {
            currentAddressNumIpv6 = a;
            currentPortNumIpv6 = 0;
            break;
        }
    }
    for (uint32_t a = 0; a < addressesIpv4Download.size(); a++) {
        if (ports[addressesIpv4Download[a]] == 443) {
            currentAddressNumIpv4Download = a;
            currentPortNumIpv4Download = 0;
            break;
        }
    }
    for (uint32_t a = 0; a < addressesIpv6Download.size(); a++) {
        if (ports[addressesIpv6Download[a]] == 443) {
            currentAddressNumIpv6Download = a;
            currentPortNumIpv6Download = 0;
            break;
        }
    }
}

std::string Datacenter::getCurrentAddress(uint32_t flags) {
    uint32_t currentAddressNum;
    std::vector<std::string> *addresses;
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            currentAddressNum = currentAddressNumIpv6Download;
            addresses = &addressesIpv6Download;
        } else {
            currentAddressNum = currentAddressNumIpv4Download;
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & 1) != 0) {
            currentAddressNum = currentAddressNumIpv6;
            addresses = &addressesIpv6;
        } else {
            currentAddressNum = currentAddressNumIpv4;
            addresses = &addressesIpv4;
        }
    }
    if (addresses->empty()) {
        return std::string("");
    }
    if (currentAddressNum >= addresses->size()) {
        currentAddressNum = 0;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                currentAddressNumIpv6Download = currentAddressNum;
            } else {
                currentAddressNumIpv4Download = currentAddressNum;
            }
        } else {
            if ((flags & 1) != 0) {
                currentAddressNumIpv6 = currentAddressNum;
            } else {
                currentAddressNumIpv4 = currentAddressNum;
            }
        }
    }
    return (*addresses)[currentAddressNum];
}

int32_t Datacenter::getCurrentPort(uint32_t flags) {
    if (ports.empty()) {
        return overridePort == -1 ? 443 : overridePort;
    }

    const int32_t *portsArray = overridePort == 8888 ? defaultPorts8888 : defaultPorts;

    uint32_t currentPortNum;
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            currentPortNum = currentPortNumIpv6Download;
        } else {
            currentPortNum = currentPortNumIpv4Download;
        }
    } else {
        if ((flags & 1) != 0) {
            currentPortNum = currentPortNumIpv6;
        } else {
            currentPortNum = currentPortNumIpv4;
        }
    }

    if (currentPortNum >= 11) {
        currentPortNum = 0;
        if ((flags & 2) != 0) {
            if ((flags & 1) != 0) {
                currentPortNumIpv6Download = currentPortNum;
            } else {
                currentPortNumIpv4Download = currentPortNum;
            }
        } else {
            if ((flags & 1) != 0) {
                currentPortNumIpv6 = currentPortNum;
            } else {
                currentPortNumIpv4 = currentPortNum;
            }
        }
    }
    int32_t port = portsArray[currentPortNum];
    if (port == -1) {
        if (overridePort != -1) {
            return overridePort;
        }
        std::string address = getCurrentAddress(flags);
        return ports[address];
    }
    return port;
}

void Datacenter::addAddressAndPort(std::string address, uint32_t port, uint32_t flags) {
    std::vector<std::string> *addresses;
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            addresses = &addressesIpv6Download;
        } else {
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & 1) != 0) {
            addresses = &addressesIpv6;
        } else {
            addresses = &addressesIpv4;
        }
    }
    if (std::find(addresses->begin(), addresses->end(), address) != addresses->end()) {
        return;
    }
    addresses->push_back(address);
    ports[address] = port;
}

void Datacenter::nextAddressOrPort(uint32_t flags) {
    uint32_t currentPortNum;
    uint32_t currentAddressNum;
    std::vector<std::string> *addresses;
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            currentPortNum = currentPortNumIpv6Download;
            currentAddressNum = currentAddressNumIpv6Download;
            addresses = &addressesIpv6Download;
        } else {
            currentPortNum = currentPortNumIpv4Download;
            currentAddressNum = currentAddressNumIpv4Download;
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & 1) != 0) {
            currentPortNum = currentPortNumIpv6;
            currentAddressNum = currentAddressNumIpv6;
            addresses = &addressesIpv6;
        } else {
            currentPortNum = currentPortNumIpv4;
            currentAddressNum = currentAddressNumIpv4;
            addresses = &addressesIpv4;
        }
    }
    if (currentPortNum + 1 < 11) {
        currentPortNum++;
    } else {
        if (currentAddressNum + 1 < addresses->size()) {
            currentAddressNum++;
        } else {
            currentAddressNum = 0;
        }
        currentPortNum = 0;
    }
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            currentPortNumIpv6Download = currentPortNum;
            currentAddressNumIpv6Download = currentAddressNum;
        } else {
            currentPortNumIpv4Download = currentPortNum;
            currentAddressNumIpv4Download = currentAddressNum;
        }
    } else {
        if ((flags & 1) != 0) {
            currentPortNumIpv6 = currentPortNum;
            currentAddressNumIpv6 = currentAddressNum;
        } else {
            currentPortNumIpv4 = currentPortNum;
            currentAddressNumIpv4 = currentAddressNum;
        }
    }
}

void Datacenter::storeCurrentAddressAndPortNum() {
    if (config == nullptr) {
        config = new Config("dc" + to_string_int32(datacenterId) + "conf.dat");
    }
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(128);
    buffer->writeInt32(paramsConfigVersion);
    buffer->writeInt32(currentPortNumIpv4);
    buffer->writeInt32(currentAddressNumIpv4);
    buffer->writeInt32(currentPortNumIpv6);
    buffer->writeInt32(currentAddressNumIpv6);
    buffer->writeInt32(currentPortNumIpv4Download);
    buffer->writeInt32(currentAddressNumIpv4Download);
    buffer->writeInt32(currentPortNumIpv6Download);
    buffer->writeInt32(currentAddressNumIpv6Download);
    config->writeConfig(buffer);
    buffer->reuse();
}

void Datacenter::replaceAddressesAndPorts(std::vector<std::string> &newAddresses, std::map<std::string, uint32_t> &newPorts, uint32_t flags) {
    std::vector<std::string> *addresses;
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            addresses = &addressesIpv6Download;
        } else {
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & 1) != 0) {
            addresses = &addressesIpv6;
        } else {
            addresses = &addressesIpv4;
        }
    }
    size_t size = addresses->size();
    for (uint32_t a = 0; a < size; a++) {
        std::map<std::string, uint32_t>::iterator iter = ports.find((*addresses)[a]);
        if (iter != ports.end()) {
            ports.erase(iter);
        }
    }
    if ((flags & 2) != 0) {
        if ((flags & 1) != 0) {
            addressesIpv6Download = newAddresses;
        } else {
            addressesIpv4Download = newAddresses;
        }
    } else {
        if ((flags & 1) != 0) {
            addressesIpv6 = newAddresses;
        } else {
            addressesIpv4 = newAddresses;
        }
    }
    ports.insert(newPorts.begin(), newPorts.end());
}

void Datacenter::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(configVersion);
    stream->writeInt32(datacenterId);
    stream->writeInt32(lastInitVersion);
    size_t size;
    stream->writeInt32((int32_t) (size = addressesIpv4.size()));
    for (uint32_t a = 0; a < size; a++) {
        stream->writeString(addressesIpv4[a]);
        stream->writeInt32(ports[addressesIpv4[a]]);
    }
    stream->writeInt32((int32_t) (size = addressesIpv6.size()));
    for (uint32_t a = 0; a < size; a++) {
        stream->writeString(addressesIpv6[a]);
        stream->writeInt32(ports[addressesIpv6[a]]);
    }
    stream->writeInt32((int32_t) (size = addressesIpv4Download.size()));
    for (uint32_t a = 0; a < size; a++) {
        stream->writeString(addressesIpv4Download[a]);
        stream->writeInt32(ports[addressesIpv4Download[a]]);
    }
    stream->writeInt32((int32_t) (size = addressesIpv6Download.size()));
    for (uint32_t a = 0; a < size; a++) {
        stream->writeString(addressesIpv6Download[a]);
        stream->writeInt32(ports[addressesIpv6Download[a]]);
    }
    if (authKey != nullptr) {
        stream->writeInt32(authKey->length);
        stream->writeBytes(authKey);
    } else {
        stream->writeInt32(0);
    }
    stream->writeInt64(authKeyId);
    stream->writeInt32(authorized ? 1 : 0);
    stream->writeInt32((int32_t) (size = serverSalts.size()));
    for (uint32_t a = 0; a < size; a++) {
        stream->writeInt32(serverSalts[a]->valid_since);
        stream->writeInt32(serverSalts[a]->valid_until);
        stream->writeInt64(serverSalts[a]->salt);
    }
}

void Datacenter::clear() {
    authKey = nullptr;
    authKeyId = 0;
    authorized = false;
    serverSalts.clear();
}

void Datacenter::clearServerSalts() {
    serverSalts.clear();
}

int64_t Datacenter::getServerSalt() {
    int32_t date = ConnectionsManager::getInstance().getCurrentTime();

    bool cleanupNeeded = false;

    int64_t result = 0;
    int32_t maxRemainingInterval = 0;

    size_t size = serverSalts.size();
    for (uint32_t a = 0; a < size; a++) {
        TL_future_salt *salt = serverSalts[a].get();
        if (salt->valid_until < date) {
            cleanupNeeded = true;
        } else if (salt->valid_since <= date && salt->valid_until > date) {
            if (maxRemainingInterval == 0 || abs(salt->valid_until - date) > maxRemainingInterval) {
                maxRemainingInterval = abs(salt->valid_until - date);
                result = salt->salt;
            }
        }
    }

    if (cleanupNeeded) {
        size = serverSalts.size();
        for (uint32_t i = 0; i < size; i++) {
            if (serverSalts[i]->valid_until < date) {
                serverSalts.erase(serverSalts.begin() + i);
                size--;
                i--;
            }
        }
    }

    if (result == 0) {
        DEBUG_D("dc%u valid salt not found", datacenterId);
    }

    return result;
}

void Datacenter::mergeServerSalts(std::vector<std::unique_ptr<TL_future_salt>> &salts) {
    if (salts.empty()) {
        return;
    }
    int32_t date = ConnectionsManager::getInstance().getCurrentTime();
    std::vector<int64_t> existingSalts(serverSalts.size());
    size_t size = serverSalts.size();
    for (uint32_t a = 0; a < size; a++) {
        existingSalts.push_back(serverSalts[a]->salt);
    }
    bool added = false;
    size = salts.size();
    for (uint32_t a = 0; a < size; a++) {
        int64_t value = salts[a]->salt;
        if (std::find(existingSalts.begin(), existingSalts.end(), value) == existingSalts.end() && salts[a]->valid_until > date) {
            serverSalts.push_back(std::unique_ptr<TL_future_salt>(std::move(salts[a])));
            added = true;
        }
    }
    if (added) {
        std::sort(serverSalts.begin(), serverSalts.end(), [](const std::unique_ptr<TL_future_salt> &x, const std::unique_ptr<TL_future_salt> &y) { return x->valid_since < y->valid_since; });
    }
}

void Datacenter::addServerSalt(std::unique_ptr<TL_future_salt> &serverSalt) {
    size_t size = serverSalts.size();
    for (uint32_t a = 0; a < size; a++) {
        if (serverSalts[a]->salt == serverSalt->salt) {
            return;
        }
    }
    serverSalts.push_back(std::move(serverSalt));
    std::sort(serverSalts.begin(), serverSalts.end(), [](const std::unique_ptr<TL_future_salt> &x, const std::unique_ptr<TL_future_salt> &y) { return x->valid_since < y->valid_since; });
}

bool Datacenter::containsServerSalt(int64_t value) {
    size_t size = serverSalts.size();
    for (uint32_t a = 0; a < size; a++) {
        if (serverSalts[a]->salt == value) {
            return true;
        }
    }
    return false;
}

void Datacenter::suspendConnections() {
    if (genericConnection != nullptr) {
        genericConnection->suspendConnection();
    }
    if (uploadConnection != nullptr) {
        uploadConnection->suspendConnection();
    }
    for (uint32_t a = 0; a < DOWNLOAD_CONNECTIONS_COUNT; a++) {
        if (downloadConnections[a] != nullptr) {
            downloadConnections[a]->suspendConnection();
        }
    }
}

void Datacenter::getSessions(std::vector<int64_t> &sessions) {
    if (genericConnection != nullptr) {
        sessions.push_back(genericConnection->getSissionId());
    }
    if (uploadConnection != nullptr) {
        sessions.push_back(uploadConnection->getSissionId());
    }
    for (uint32_t a = 0; a < DOWNLOAD_CONNECTIONS_COUNT; a++) {
        if (downloadConnections[a] != nullptr) {
            sessions.push_back(downloadConnections[a]->getSissionId());
        }
    }
}

void Datacenter::recreateSessions() {
    if (genericConnection != nullptr) {
        genericConnection->recreateSession();
    }
    if (uploadConnection != nullptr) {
        uploadConnection->recreateSession();
    }
    for (uint32_t a = 0; a < DOWNLOAD_CONNECTIONS_COUNT; a++) {
        if (downloadConnections[a] != nullptr) {
            downloadConnections[a]->recreateSession();
        }
    }
}

Connection *Datacenter::createDownloadConnection(uint32_t num) {
    if (downloadConnections[num] == nullptr) {
        downloadConnections[num] = new Connection(this, ConnectionTypeDownload);
    }
    return downloadConnections[num];
}

Connection *Datacenter::createUploadConnection() {
    if (uploadConnection == nullptr) {
        uploadConnection = new Connection(this, ConnectionTypeUpload);
    }
    return uploadConnection;
}

Connection *Datacenter::createGenericConnection() {
    if (genericConnection == nullptr) {
        genericConnection = new Connection(this, ConnectionTypeGeneric);
    }
    return genericConnection;
}

Connection *Datacenter::createPushConnection() {
    if (pushConnection == nullptr) {
        pushConnection = new Connection(this, ConnectionTypePush);
    }
    return pushConnection;
}

uint32_t Datacenter::getDatacenterId() {
    return datacenterId;
}

bool Datacenter::isHandshaking() {
    return handshakeState != 0;
}

void Datacenter::beginHandshake(bool reconnect) {
    DEBUG_D("dc%u handshake: begin", datacenterId);
    cleanupHandshake();
    createGenericConnection()->recreateSession();
    handshakeState = 1;

    if (reconnect) {
        createGenericConnection()->suspendConnection();
        createGenericConnection()->connect();
    }

    TL_req_pq *request = new TL_req_pq();
    request->nonce = std::unique_ptr<ByteArray>(new ByteArray(16));
    RAND_bytes(request->nonce->bytes, 16);
    authNonce = new ByteArray(request->nonce.get());
    sendRequestData(request, true);
}

void Datacenter::cleanupHandshake() {
    handshakeState = 0;
    if (handshakeRequest != nullptr) {
        delete handshakeRequest;
        handshakeRequest = nullptr;
    }
    if (handshakeServerSalt != nullptr) {
        delete handshakeServerSalt;
        handshakeServerSalt = nullptr;
    }
    if (authNonce != nullptr) {
        delete authNonce;
        authNonce = nullptr;
    }
    if (authServerNonce != nullptr) {
        delete authServerNonce;
        authServerNonce = nullptr;
    }
    if (authNewNonce != nullptr) {
        delete authNewNonce;
        authNewNonce = nullptr;
    }
    if (handshakeAuthKey != nullptr) {
        delete handshakeAuthKey;
        handshakeAuthKey = nullptr;
    }
}

void Datacenter::sendRequestData(TLObject *object, bool important) {
    uint32_t messageLength = object->getObjectSize();
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(20 + messageLength);
    buffer->writeInt64(0);
    buffer->writeInt64(ConnectionsManager::getInstance().generateMessageId());
    buffer->writeInt32(messageLength);
    object->serializeToStream(buffer);
    createGenericConnection()->sendData(buffer, false);
    if (important) {
        if (handshakeRequest != object) {
            if (handshakeRequest != nullptr) {
                delete handshakeRequest;
            }
            handshakeRequest = object;
        }
    } else {
        delete object;
    }
}

void Datacenter::onHandshakeConnectionClosed(Connection *connection) {
    if (handshakeState == 0) {
        return;
    }
    needResendData = true;
}

void Datacenter::onHandshakeConnectionConnected(Connection *connection) {
    if (handshakeState == 0 || !needResendData) {
        return;
    }
    beginHandshake(false);
}

inline uint64_t gcd(uint64_t a, uint64_t b) {
    while (a != 0 && b != 0) {
        while ((b & 1) == 0) {
            b >>= 1;
        }
        while ((a & 1) == 0) {
            a >>= 1;
        }
        if (a > b) {
            a -= b;
        } else {
            b -= a;
        }
    }
    return b == 0 ? a : b;
}

inline bool factorizeValue(uint64_t what, uint32_t &p, uint32_t &q) {
    int32_t it = 0, i, j;
    uint64_t g = 0;
    for (i = 0; i < 3 || it < 1000; i++) {
        uint64_t t = ((lrand48() & 15) + 17) % what;
        uint64_t x = (long long) lrand48() % (what - 1) + 1, y = x;
        int32_t lim = 1 << (i + 18);
        for (j = 1; j < lim; j++) {
            ++it;
            uint64_t a = x, b = x, c = t;
            while (b) {
                if (b & 1) {
                    c += a;
                    if (c >= what) {
                        c -= what;
                    }
                }
                a += a;
                if (a >= what) {
                    a -= what;
                }
                b >>= 1;
            }
            x = c;
            uint64_t z = x < y ? what + x - y : x - y;
            g = gcd(z, what);
            if (g != 1) {
                break;
            }
            if (!(j & (j - 1))) {
                y = x;
            }
        }
        if (g > 1 && g < what) {
            break;
        }
    }

    if (g > 1 && g < what) {
        p = (uint32_t) g;
        q = (uint32_t) (what / g);
        if (p > q) {
            uint32_t tmp = p;
            p = q;
            q = tmp;
        }
        return true;
    } else {
        DEBUG_E("factorization failed for %llu", what);
        p = 0;
        q = 0;
        return false;
    }
}

inline bool check_prime(BIGNUM *p) {
    int result = 0;
    if (!BN_primality_test(&result, p, BN_prime_checks, bnContext, 0, NULL)) {
        DEBUG_E("OpenSSL error at BN_primality_test");
        return false;
    }
    return result != 0;
}

inline bool isGoodPrime(BIGNUM *p, uint32_t g) {
    //TODO check against known good primes
    if (g < 2 || g > 7 || BN_num_bits(p) != 2048) {
        return false;
    }

    BIGNUM *t = BN_new();
    BIGNUM *dh_g = BN_new();

    if (!BN_set_word(dh_g, 4 * g)) {
        DEBUG_E("OpenSSL error at BN_set_word(dh_g, 4 * g)");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }
    if (!BN_mod(t, p, dh_g, bnContext)) {
        DEBUG_E("OpenSSL error at BN_mod");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }
    uint64_t x = BN_get_word(t);
    if (x >= 4 * g) {
        DEBUG_E("OpenSSL error at BN_get_word");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }

    BN_free(dh_g);

    bool result = true;
    switch (g) {
        case 2:
            if (x != 7) {
                result = false;
            }
            break;
        case 3:
            if (x % 3 != 2) {
                result = false;
            }
            break;
        case 5:
            if (x % 5 != 1 && x % 5 != 4) {
                result = false;
            }
            break;
        case 6:
            if (x != 19 && x != 23) {
                result = false;
            }
            break;
        case 7:
            if (x % 7 != 3 && x % 7 != 5 && x % 7 != 6) {
                result = false;
            }
            break;
        default:
            break;
    }

    char *prime = BN_bn2hex(p);
    static const char *goodPrime = "c71caeb9c6b1c9048e6c522f70f13f73980d40238e3e21c14934d037563d930f48198a0aa7c14058229493d22530f4dbfa336f6e0ac925139543aed44cce7c3720fd51f69458705ac68cd4fe6b6b13abdc9746512969328454f18faf8c595f642477fe96bb2a941d5bcd1d4ac8cc49880708fa9b378e3c4f3a9060bee67cf9a4a4a695811051907e162753b56b0f6b410dba74d8a84b2a14b3144e0ef1284754fd17ed950d5965b4b9dd46582db1178d169c6bc465b0d6ff9ca3928fef5b9ae4e418fc15e83ebea0f87fa9ff5eed70050ded2849f47bf959d956850ce929851f0d8115f635b105ee2e4e15d04b2454bf6f4fadf034b10403119cd8e3b92fcc5b";
    if (!strcasecmp(prime, goodPrime)) {
        delete [] prime;
        BN_free(t);
        return true;
    }
    delete [] prime;

    if (!result || !check_prime(p)) {
        BN_free(t);
        return false;
    }

    BIGNUM *b = BN_new();
    if (!BN_set_word(b, 2)) {
        DEBUG_E("OpenSSL error at BN_set_word(b, 2)");
        BN_free(b);
        BN_free(t);
        return false;
    }
    if (!BN_div(t, 0, p, b, bnContext)) {
        DEBUG_E("OpenSSL error at BN_div");
        BN_free(b);
        BN_free(t);
        return false;
    }
    if (!check_prime(t)) {
        result = false;
    }
    BN_free(b);
    BN_free(t);
    return result;
}

inline bool isGoodGaAndGb(BIGNUM *g_a, BIGNUM *p) {
    if (BN_num_bytes(g_a) > 256 || BN_num_bits(g_a) < 2048 - 64 || BN_cmp(p, g_a) <= 0) {
        return false;
    }
    BIGNUM *dif = BN_new();
    BN_sub(dif, p, g_a);
    if (BN_num_bits(dif) < 2048 - 64) {
        BN_free(dif);
        return false;
    }
    BN_free(dif);
    return true;
}

void Datacenter::aesIgeEncryption(uint8_t *buffer, uint8_t *key, uint8_t *iv, bool encrypt, bool changeIv, uint32_t length) {
    uint8_t *ivBytes = iv;
    if (!changeIv) {
        ivBytes = new uint8_t[32];
        memcpy(ivBytes, iv, 32);
    }
    AES_KEY akey;
    if (!encrypt) {
        AES_set_decrypt_key(key, 32 * 8, &akey);
        AES_ige_encrypt(buffer, buffer, length, &akey, ivBytes, AES_DECRYPT);
    } else {
        AES_set_encrypt_key(key, 32 * 8, &akey);
        AES_ige_encrypt(buffer, buffer, length, &akey, ivBytes, AES_ENCRYPT);
    }
    if (!changeIv) {
        delete [] ivBytes;
    }
}

int32_t Datacenter::selectPublicKey(std::vector<int64_t> &fingerprints) {
    if (serverPublicKeys.empty()) {
        serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                           "MIIBCgKCAQEAwVACPi9w23mF3tBkdZz+zwrzKOaaQdr01vAbU4E1pvkfj4sqDsm6\n"
                                           "lyDONS789sVoD/xCS9Y0hkkC3gtL1tSfTlgCMOOul9lcixlEKzwKENj1Yz/s7daS\n"
                                           "an9tqw3bfUV/nqgbhGX81v/+7RFAEd+RwFnK7a+XYl9sluzHRyVVaTTveB2GazTw\n"
                                           "Efzk2DWgkBluml8OREmvfraX3bkHZJTKX4EQSjBbbdJ2ZXIsRrYOXfaA+xayEGB+\n"
                                           "8hdlLmAjbCVfaigxX0CDqWeR1yFL9kwd9P0NsZRPsmoqVwMbMu7mStFai6aIhc3n\n"
                                           "Slv8kg9qv1m6XHVQY3PnEw+QQtqSIXklHwIDAQAB\n"
                                           "-----END RSA PUBLIC KEY-----");
        serverPublicKeysFingerprints.push_back(0xc3b42b026ce86b21LL);

        serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                           "MIIBCgKCAQEAxq7aeLAqJR20tkQQMfRn+ocfrtMlJsQ2Uksfs7Xcoo77jAid0bRt\n"
                                           "ksiVmT2HEIJUlRxfABoPBV8wY9zRTUMaMA654pUX41mhyVN+XoerGxFvrs9dF1Ru\n"
                                           "vCHbI02dM2ppPvyytvvMoefRoL5BTcpAihFgm5xCaakgsJ/tH5oVl74CdhQw8J5L\n"
                                           "xI/K++KJBUyZ26Uba1632cOiq05JBUW0Z2vWIOk4BLysk7+U9z+SxynKiZR3/xdi\n"
                                           "XvFKk01R3BHV+GUKM2RYazpS/P8v7eyKhAbKxOdRcFpHLlVwfjyM1VlDQrEZxsMp\n"
                                           "NTLYXb6Sce1Uov0YtNx5wEowlREH1WOTlwIDAQAB\n"
                                           "-----END RSA PUBLIC KEY-----");
        serverPublicKeysFingerprints.push_back(0x9a996a1db11c729bLL);

        serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                           "MIIBCgKCAQEAsQZnSWVZNfClk29RcDTJQ76n8zZaiTGuUsi8sUhW8AS4PSbPKDm+\n"
                                           "DyJgdHDWdIF3HBzl7DHeFrILuqTs0vfS7Pa2NW8nUBwiaYQmPtwEa4n7bTmBVGsB\n"
                                           "1700/tz8wQWOLUlL2nMv+BPlDhxq4kmJCyJfgrIrHlX8sGPcPA4Y6Rwo0MSqYn3s\n"
                                           "g1Pu5gOKlaT9HKmE6wn5Sut6IiBjWozrRQ6n5h2RXNtO7O2qCDqjgB2vBxhV7B+z\n"
                                           "hRbLbCmW0tYMDsvPpX5M8fsO05svN+lKtCAuz1leFns8piZpptpSCFn7bWxiA9/f\n"
                                           "x5x17D7pfah3Sy2pA+NDXyzSlGcKdaUmwQIDAQAB\n"
                                           "-----END RSA PUBLIC KEY-----");
        serverPublicKeysFingerprints.push_back(0xb05b2a6f70cdea78LL);

        serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                           "MIIBCgKCAQEAwqjFW0pi4reKGbkc9pK83Eunwj/k0G8ZTioMMPbZmW99GivMibwa\n"
                                           "xDM9RDWabEMyUtGoQC2ZcDeLWRK3W8jMP6dnEKAlvLkDLfC4fXYHzFO5KHEqF06i\n"
                                           "qAqBdmI1iBGdQv/OQCBcbXIWCGDY2AsiqLhlGQfPOI7/vvKc188rTriocgUtoTUc\n"
                                           "/n/sIUzkgwTqRyvWYynWARWzQg0I9olLBBC2q5RQJJlnYXZwyTL3y9tdb7zOHkks\n"
                                           "WV9IMQmZmyZh/N7sMbGWQpt4NMchGpPGeJ2e5gHBjDnlIf2p1yZOYeUYrdbwcS0t\n"
                                           "UiggS4UeE8TzIuXFQxw7fzEIlmhIaq3FnwIDAQAB\n"
                                           "-----END RSA PUBLIC KEY-----");
        serverPublicKeysFingerprints.push_back(0x71e025b6c76033e3LL);
    }

    size_t count1 = fingerprints.size();
    size_t count2 = serverPublicKeysFingerprints.size();
    for (uint32_t a = 0; a < count1; a++) {
        for (uint32_t b = 0; b < count2; b++) {
            if ((uint64_t) fingerprints[a] == serverPublicKeysFingerprints[b]) {
                return b;
            }
        }
    }
    return -1;
}

void Datacenter::processHandshakeResponse(TLObject *message, int64_t messageId) {
    if (handshakeState == 0) {
        return;
    }
    const std::type_info &typeInfo = typeid(*message);
    if (typeInfo == typeid(TL_resPQ)) {
        if (handshakeState != 1) {
            sendAckRequest(messageId);
            return;
        }

        handshakeState = 2;
        TL_resPQ *result = (TL_resPQ *) message;
        if (authNonce->isEqualTo(result->nonce.get())) {
            int32_t keyIndex = selectPublicKey(result->server_public_key_fingerprints);
            if (keyIndex < 0) {
                DEBUG_E("dc%u handshake: can't find valid server public key", datacenterId);
                beginHandshake(false);
                return;
            }

            authServerNonce = new ByteArray(result->server_nonce.get());

            //TODO run in different thread?
            uint64_t pq = ((uint64_t) (result->pq->bytes[0] & 0xff) << 56) |
                          ((uint64_t) (result->pq->bytes[1] & 0xff) << 48) |
                          ((uint64_t) (result->pq->bytes[2] & 0xff) << 40) |
                          ((uint64_t) (result->pq->bytes[3] & 0xff) << 32) |
                          ((uint64_t) (result->pq->bytes[4] & 0xff) << 24) |
                          ((uint64_t) (result->pq->bytes[5] & 0xff) << 16) |
                          ((uint64_t) (result->pq->bytes[6] & 0xff) << 8) |
                          ((uint64_t) (result->pq->bytes[7] & 0xff));
            uint32_t p, q;
            if (!factorizeValue(pq, p, q)) {
                beginHandshake(false);
                return;
            }

            TL_req_DH_params *request = new TL_req_DH_params();
            request->nonce = std::unique_ptr<ByteArray>(new ByteArray(authNonce));
            request->server_nonce = std::unique_ptr<ByteArray>(new ByteArray(authServerNonce));
            request->p = std::unique_ptr<ByteArray>(new ByteArray(4));
            request->p->bytes[3] = (uint8_t) p;
            request->p->bytes[2] = (uint8_t) (p >> 8);
            request->p->bytes[1] = (uint8_t) (p >> 16);
            request->p->bytes[0] = (uint8_t) (p >> 24);
            request->q = std::unique_ptr<ByteArray>(new ByteArray(4));
            request->q->bytes[3] = (uint8_t) q;
            request->q->bytes[2] = (uint8_t) (q >> 8);
            request->q->bytes[1] = (uint8_t) (q >> 16);
            request->q->bytes[0] = (uint8_t) (q >> 24);
            request->public_key_fingerprint = (int64_t) serverPublicKeysFingerprints[keyIndex];

            TL_p_q_inner_data *innerData = new TL_p_q_inner_data();
            innerData->nonce = std::unique_ptr<ByteArray>(new ByteArray(authNonce));
            innerData->server_nonce = std::unique_ptr<ByteArray>(new ByteArray(authServerNonce));
            innerData->pq = std::unique_ptr<ByteArray>(new ByteArray(result->pq.get()));
            innerData->p = std::unique_ptr<ByteArray>(new ByteArray(request->p.get()));
            innerData->q = std::unique_ptr<ByteArray>(new ByteArray(request->q.get()));
            innerData->new_nonce = std::unique_ptr<ByteArray>(new ByteArray(32));
            RAND_bytes(innerData->new_nonce->bytes, 32);
            authNewNonce = new ByteArray(innerData->new_nonce.get());

            uint32_t innerDataSize = innerData->getObjectSize();
            uint32_t additionalSize = innerDataSize + SHA_DIGEST_LENGTH < 255 ? 255 - (innerDataSize + SHA_DIGEST_LENGTH) : 0;
            NativeByteBuffer *innerDataBuffer = BuffersStorage::getInstance().getFreeBuffer(innerDataSize + additionalSize + SHA_DIGEST_LENGTH);
            innerDataBuffer->position(SHA_DIGEST_LENGTH);
            innerData->serializeToStream(innerDataBuffer);
            delete innerData;

            SHA1(innerDataBuffer->bytes() + SHA_DIGEST_LENGTH, innerDataSize, innerDataBuffer->bytes());
            if (additionalSize != 0) {
                RAND_bytes(innerDataBuffer->bytes() + SHA_DIGEST_LENGTH + innerDataSize, additionalSize);
            }

            std::string &key = serverPublicKeys[keyIndex];

            BIO *keyBio = BIO_new(BIO_s_mem());
            BIO_write(keyBio, key.c_str(), (int) key.length());
            RSA *rsaKey = PEM_read_bio_RSAPublicKey(keyBio, NULL, NULL, NULL);
            BIO_free(keyBio);
            if (bnContext == nullptr) {
                bnContext = BN_CTX_new();
            }
            BIGNUM *a = BN_bin2bn(innerDataBuffer->bytes(), innerDataBuffer->limit(), NULL);
            BIGNUM *r = BN_new();
            BN_mod_exp(r, a, rsaKey->e, rsaKey->n, bnContext);
            uint32_t size = BN_num_bytes(r);
            ByteArray *rsaEncryptedData = new ByteArray(size >= 256 ? size : 256);
            size_t resLen = BN_bn2bin(r, rsaEncryptedData->bytes);
            if (256 - resLen > 0) {
                memset(rsaEncryptedData + resLen, 0, 256 - resLen);
            }
            BN_free(a);
            BN_free(r);
            RSA_free(rsaKey);
            innerDataBuffer->reuse();

            request->encrypted_data = std::unique_ptr<ByteArray>(rsaEncryptedData);

            sendAckRequest(messageId);
            sendRequestData(request, true);
        } else {
            DEBUG_E("dc%u handshake: invalid client nonce", datacenterId);
            beginHandshake(false);
        }
    } else if (dynamic_cast<Server_DH_Params *>(message)) {
        if (typeInfo == typeid(TL_server_DH_params_ok)) {
            if (handshakeState != 2) {
                sendAckRequest(messageId);
                return;
            }

            handshakeState = 3;

            TL_server_DH_params_ok *result = (TL_server_DH_params_ok *) message;

            NativeByteBuffer *tmpAesKeyAndIv = BuffersStorage::getInstance().getFreeBuffer(84);

            NativeByteBuffer *newNonceAndServerNonce = BuffersStorage::getInstance().getFreeBuffer(authNewNonce->length + authServerNonce->length);
            newNonceAndServerNonce->writeBytes(authNewNonce);
            newNonceAndServerNonce->writeBytes(authServerNonce);
            SHA1(newNonceAndServerNonce->bytes(), newNonceAndServerNonce->limit(), tmpAesKeyAndIv->bytes());
            newNonceAndServerNonce->reuse();

            NativeByteBuffer *serverNonceAndNewNonce = BuffersStorage::getInstance().getFreeBuffer(authServerNonce->length + authNewNonce->length);
            serverNonceAndNewNonce->writeBytes(authServerNonce);
            serverNonceAndNewNonce->writeBytes(authNewNonce);
            SHA1(serverNonceAndNewNonce->bytes(), serverNonceAndNewNonce->limit(), tmpAesKeyAndIv->bytes() + 20);
            serverNonceAndNewNonce->reuse();

            NativeByteBuffer *newNonceAndNewNonce = BuffersStorage::getInstance().getFreeBuffer(authNewNonce->length + authNewNonce->length);
            newNonceAndNewNonce->writeBytes(authNewNonce);
            newNonceAndNewNonce->writeBytes(authNewNonce);
            SHA1(newNonceAndNewNonce->bytes(), newNonceAndNewNonce->limit(), tmpAesKeyAndIv->bytes() + 40);
            newNonceAndNewNonce->reuse();

            memcpy(tmpAesKeyAndIv->bytes() + 60, authNewNonce->bytes, 4);
            aesIgeEncryption(result->encrypted_answer->bytes, tmpAesKeyAndIv->bytes(), tmpAesKeyAndIv->bytes() + 32, false, false, result->encrypted_answer->length);

            bool hashVerified = false;
            for (uint32_t i = 0; i < 16; i++) {
                SHA1(result->encrypted_answer->bytes + SHA_DIGEST_LENGTH, result->encrypted_answer->length - i - SHA_DIGEST_LENGTH, tmpAesKeyAndIv->bytes() + 64);
                if (!memcmp(tmpAesKeyAndIv->bytes() + 64, result->encrypted_answer->bytes, SHA_DIGEST_LENGTH)) {
                    hashVerified = true;
                    break;
                }
            }

            if (!hashVerified) {
                DEBUG_E("dc%u handshake: can't decode DH params", datacenterId);
                beginHandshake(false);
                return;
            }

            bool error = false;
            NativeByteBuffer *answerWithHash = new NativeByteBuffer(result->encrypted_answer->bytes + SHA_DIGEST_LENGTH, result->encrypted_answer->length - SHA_DIGEST_LENGTH);
            uint32_t constructor = answerWithHash->readUint32(&error);
            TL_server_DH_inner_data *dhInnerData = TL_server_DH_inner_data::TLdeserialize(answerWithHash, constructor, error);
            delete answerWithHash;

            if (error) {
                DEBUG_E("dc%u handshake: can't parse decoded DH params", datacenterId);
                beginHandshake(false);
                return;
            }

            if (!authNonce->isEqualTo(dhInnerData->nonce.get())) {
                DEBUG_E("dc%u handshake: invalid DH nonce", datacenterId);
                beginHandshake(false);
                return;
            }

            if (!authServerNonce->isEqualTo(dhInnerData->server_nonce.get())) {
                DEBUG_E("dc%u handshake: invalid DH server nonce", datacenterId);
                beginHandshake(false);
                return;
            }

            BIGNUM *p = BN_bin2bn(dhInnerData->dh_prime->bytes, dhInnerData->dh_prime->length, NULL);
            if (p == nullptr) {
                DEBUG_E("can't allocate BIGNUM p");
                exit(1);
            }
            if (!isGoodPrime(p, dhInnerData->g)) {
                DEBUG_E("dc%u handshake: bad prime", datacenterId);
                beginHandshake(false);
                BN_free(p);
                return;
            }

            BIGNUM *g_a = BN_new();
            if (g_a == nullptr) {
                DEBUG_E("can't allocate BIGNUM g_a");
                exit(1);
            }
            BN_bin2bn(dhInnerData->g_a->bytes, dhInnerData->g_a->length, g_a);
            if (!isGoodGaAndGb(g_a, p)) {
                DEBUG_E("dc%u handshake: bad prime and g_a", datacenterId);
                beginHandshake(false);
                BN_free(p);
                BN_free(g_a);
                return;
            }

            BIGNUM *g = BN_new();
            if (g == nullptr) {
                DEBUG_E("can't allocate BIGNUM g");
                exit(1);
            }
            if (!BN_set_word(g, dhInnerData->g)) {
                DEBUG_E("OpenSSL error at BN_set_word(g_b, dhInnerData->g)");
                beginHandshake(false);
                BN_free(g);
                BN_free(g_a);
                BN_free(p);
                return;
            }
            static uint8_t bytes[256];
            RAND_bytes(bytes, 256);
            BIGNUM *b = BN_bin2bn(bytes, 256, NULL);
            if (b == nullptr) {
                DEBUG_E("can't allocate BIGNUM b");
                exit(1);
            }

            BIGNUM *g_b = BN_new();
            if (!BN_mod_exp(g_b, g, b, p, bnContext)) {
                DEBUG_E("OpenSSL error at BN_mod_exp(g_b, g, b, p, bnContext)");
                beginHandshake(false);
                BN_free(g);
                BN_free(g_a);
                BN_free(g_b);
                BN_free(b);
                BN_free(p);
                return;
            }

            TL_client_DH_inner_data *clientInnerData = new TL_client_DH_inner_data();
            clientInnerData->g_b = std::unique_ptr<ByteArray>(new ByteArray(BN_num_bytes(g_b)));
            BN_bn2bin(g_b, clientInnerData->g_b->bytes);
            clientInnerData->nonce = std::unique_ptr<ByteArray>(new ByteArray(authNonce));
            clientInnerData->server_nonce = std::unique_ptr<ByteArray>(new ByteArray(authServerNonce));
            clientInnerData->retry_id = 0;
            BN_free(g_b);
            BN_free(g);

            BIGNUM *authKeyNum = BN_new();
            BN_mod_exp(authKeyNum, g_a, b, p, bnContext);
            size_t l = BN_num_bytes(authKeyNum);
            handshakeAuthKey = new ByteArray(256);
            BN_bn2bin(authKeyNum, handshakeAuthKey->bytes);
            if (l < 256) {
                memmove(handshakeAuthKey->bytes + 256 - l, handshakeAuthKey->bytes, l);
                memset(handshakeAuthKey->bytes, 0, 256 - l);
            }

            BN_free(authKeyNum);
            BN_free(g_a);
            BN_free(b);
            BN_free(p);

            uint32_t clientInnerDataSize = clientInnerData->getObjectSize();
            uint32_t additionalSize = (clientInnerDataSize + SHA_DIGEST_LENGTH) % 16;
            if (additionalSize != 0) {
                additionalSize = 16 - additionalSize;
            }
            NativeByteBuffer *clientInnerDataBuffer = BuffersStorage::getInstance().getFreeBuffer(clientInnerDataSize + additionalSize + SHA_DIGEST_LENGTH);
            clientInnerDataBuffer->position(SHA_DIGEST_LENGTH);
            clientInnerData->serializeToStream(clientInnerDataBuffer);
            delete clientInnerData;

            SHA1(clientInnerDataBuffer->bytes() + SHA_DIGEST_LENGTH, clientInnerDataSize, clientInnerDataBuffer->bytes());

            if (additionalSize != 0) {
                RAND_bytes(clientInnerDataBuffer->bytes() + SHA_DIGEST_LENGTH + clientInnerDataSize, additionalSize);
            }

            TL_set_client_DH_params *setClientDhParams = new TL_set_client_DH_params();
            setClientDhParams->nonce = std::unique_ptr<ByteArray>(new ByteArray(authNonce));
            setClientDhParams->server_nonce = std::unique_ptr<ByteArray>(new ByteArray(authServerNonce));
            aesIgeEncryption(clientInnerDataBuffer->bytes(), tmpAesKeyAndIv->bytes(), tmpAesKeyAndIv->bytes() + 32, true, false, clientInnerDataBuffer->limit());
            setClientDhParams->encrypted_data = std::unique_ptr<ByteArray>(new ByteArray(clientInnerDataBuffer->bytes(), clientInnerDataBuffer->limit()));
            clientInnerDataBuffer->reuse();
            tmpAesKeyAndIv->reuse();

            sendAckRequest(messageId);
            sendRequestData(setClientDhParams, true);

            int32_t currentTime = (int32_t) (ConnectionsManager::getInstance().getCurrentTimeMillis() / 1000);
            timeDifference = dhInnerData->server_time - currentTime;

            handshakeServerSalt = new TL_future_salt();
            handshakeServerSalt->valid_since = currentTime + timeDifference - 5;
            handshakeServerSalt->valid_until = handshakeServerSalt->valid_since + 30 * 60;
            for (int32_t a = 7; a >= 0; a--) {
                handshakeServerSalt->salt <<= 8;
                handshakeServerSalt->salt |= (authNewNonce->bytes[a] ^ authServerNonce->bytes[a]);
            }
        } else {
            DEBUG_E("dc%u handshake: can't set DH params", datacenterId);
            beginHandshake(false);
        }
    } else if (dynamic_cast<Set_client_DH_params_answer *>(message)) {
        if (handshakeState != 3) {
            sendAckRequest(messageId);
            return;
        }

        handshakeState = 4;

        Set_client_DH_params_answer *result = (Set_client_DH_params_answer *) message;

        if (!authNonce->isEqualTo(result->nonce.get())) {
            DEBUG_E("dc%u handshake: invalid DH answer nonce", datacenterId);
            beginHandshake(false);
            return;
        }
        if (!authServerNonce->isEqualTo(result->server_nonce.get())) {
            DEBUG_E("dc%u handshake: invalid DH answer server nonce", datacenterId);
            beginHandshake(false);
            return;
        }

        sendAckRequest(messageId);

        uint32_t authKeyAuxHashLength = authNewNonce->length + SHA_DIGEST_LENGTH + 1;
        NativeByteBuffer *authKeyAuxHashBuffer = BuffersStorage::getInstance().getFreeBuffer(authKeyAuxHashLength + SHA_DIGEST_LENGTH);
        authKeyAuxHashBuffer->writeBytes(authNewNonce);
        SHA1(handshakeAuthKey->bytes, handshakeAuthKey->length, authKeyAuxHashBuffer->bytes() + authNewNonce->length + 1);

        if (typeInfo == typeid(TL_dh_gen_ok)) {
            authKeyAuxHashBuffer->writeByte(1);
            SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

            if (memcmp(result->new_nonce_hash1->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
                DEBUG_E("dc%u handshake: invalid DH answer nonce hash 1", datacenterId);
                authKeyAuxHashBuffer->reuse();
                beginHandshake(false);
            } else {
                DEBUG_D("dc%u handshake: completed, time difference = %d", datacenterId, timeDifference);
                authKey = handshakeAuthKey;
                handshakeAuthKey = nullptr;
                authKeyAuxHashBuffer->position(authNewNonce->length + 1 + 12);
                authKeyId = authKeyAuxHashBuffer->readInt64(nullptr);
                std::unique_ptr<TL_future_salt> salt = std::unique_ptr<TL_future_salt>(handshakeServerSalt);
                addServerSalt(salt);
                handshakeServerSalt = nullptr;
                ConnectionsManager::getInstance().onDatacenterHandshakeComplete(this, timeDifference);
                cleanupHandshake();
            }
            authKeyAuxHashBuffer->reuse();
        } else if (typeInfo == typeid(TL_dh_gen_retry)) {
            authKeyAuxHashBuffer->writeByte(2);
            SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

            if (memcmp(result->new_nonce_hash2->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
                DEBUG_E("dc%u handshake: invalid DH answer nonce hash 2", datacenterId);
                beginHandshake(false);
            } else {
                DEBUG_D("dc%u handshake: retry DH", datacenterId);
                beginHandshake(false);
            }
            authKeyAuxHashBuffer->reuse();
        } else if (typeInfo == typeid(TL_dh_gen_fail)) {
            authKeyAuxHashBuffer->writeByte(3);
            SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

            if (memcmp(result->new_nonce_hash3->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
                DEBUG_E("dc%u handshake: invalid DH answer nonce hash 3", datacenterId);
                beginHandshake(false);
            } else {
                DEBUG_E("dc%u handshake: server declined DH params", datacenterId);
                beginHandshake(false);
            }
            authKeyAuxHashBuffer->reuse();
        }
    }
}

TLObject *Datacenter::getCurrentHandshakeRequest() {
    return handshakeRequest;
}

void Datacenter::sendAckRequest(int64_t messageId) {
    TL_msgs_ack *msgsAck = new TL_msgs_ack();
    msgsAck->msg_ids.push_back(messageId);
    sendRequestData(msgsAck, false);
}

inline void generateMessageKey(uint8_t *authKey, uint8_t *messageKey, uint8_t *result, bool incoming) {
    uint32_t x = incoming ? 8 : 0;

    static uint8_t sha[68];

    memcpy(sha + 20, messageKey, 16);
    memcpy(sha + 20 + 16, authKey + x, 32);
    SHA1(sha + 20, 48, sha);
    memcpy(result, sha, 8);
    memcpy(result + 32, sha + 8, 12);

    memcpy(sha + 20, authKey + 32 + x, 16);
    memcpy(sha + 20 + 16, messageKey, 16);
    memcpy(sha + 20 + 16 + 16, authKey + 48 + x, 16);
    SHA1(sha + 20, 48, sha);
    memcpy(result + 8, sha + 8, 12);
    memcpy(result + 32 + 12, sha, 8);

    memcpy(sha + 20, authKey + 64 + x, 32);
    memcpy(sha + 20 + 32, messageKey, 16);
    SHA1(sha + 20, 48, sha);
    memcpy(result + 8 + 12, sha + 4, 12);
    memcpy(result + 32 + 12 + 8, sha + 16, 4);

    memcpy(sha + 20, messageKey, 16);
    memcpy(sha + 20 + 16, authKey + 96 + x, 32);
    SHA1(sha + 20, 48, sha);
    memcpy(result + 32 + 12 + 8 + 4, sha, 8);
}

NativeByteBuffer *Datacenter::createRequestsData(std::vector<std::unique_ptr<NetworkMessage>> &requests, int32_t *quickAckId, Connection *connection) {
    if (authKey == nullptr || connection == nullptr) {
        return nullptr;
    }

    int64_t messageId;
    TLObject *messageBody;
    bool freeMessageBody = false;
    int32_t messageSeqNo;

    if (requests.size() == 1) {
        NetworkMessage *networkMessage = requests[0].get();

        if (networkMessage->message->outgoingBody != nullptr) {
            messageBody = networkMessage->message->outgoingBody;
        } else {
            messageBody = networkMessage->message->body.get();
        }
        DEBUG_D("connection(%p, dc%u, type %d) send message (session: 0x%llx, seqno: %d, messageid: 0x%llx): %s(%p)", connection, datacenterId, connection->getConnectionType(), (uint64_t) connection->getSissionId(), networkMessage->message->seqno, (uint64_t) networkMessage->message->msg_id, typeid(*messageBody).name(), messageBody);

        int64_t messageTime = (int64_t) (networkMessage->message->msg_id / 4294967296.0 * 1000);
        int64_t currentTime = ConnectionsManager::getInstance().getCurrentTimeMillis() + (int64_t) ConnectionsManager::getInstance().getTimeDifference() * 1000;

        if (messageTime < currentTime - 30000 || messageTime > currentTime + 25000) {
            DEBUG_D("wrap message in container");
            TL_msg_container *messageContainer = new TL_msg_container();
            messageContainer->messages.push_back(std::move(networkMessage->message));

            messageId = ConnectionsManager::getInstance().generateMessageId();
            messageBody = messageContainer;
            messageSeqNo = connection->generateMessageSeqNo(false);
            freeMessageBody = true;
        } else {
            messageId = networkMessage->message->msg_id;
            messageSeqNo = networkMessage->message->seqno;
        }
    } else {
        DEBUG_D("start write messages to container");
        TL_msg_container *messageContainer = new TL_msg_container();
        size_t count = requests.size();
        for (uint32_t a = 0; a < count; a++) {
            NetworkMessage *networkMessage = requests[a].get();
            if (networkMessage->message->outgoingBody != nullptr) {
                messageBody = networkMessage->message->outgoingBody;
            } else {
                messageBody = networkMessage->message->body.get();
            }
            DEBUG_D("connection(%p, dc%u, type %d) send message (session: 0x%llx, seqno: %d, messageid: 0x%llx): %s(%p)", connection, datacenterId, connection->getConnectionType(), (uint64_t) connection->getSissionId(), networkMessage->message->seqno, (uint64_t) networkMessage->message->msg_id, typeid(*messageBody).name(), messageBody);
            messageContainer->messages.push_back(std::unique_ptr<TL_message>(std::move(networkMessage->message)));
        }
        messageId = ConnectionsManager::getInstance().generateMessageId();
        messageBody = messageContainer;
        freeMessageBody = true;
        messageSeqNo = connection->generateMessageSeqNo(false);
    }

    uint32_t messageSize = messageBody->getObjectSize();
    uint32_t additionalSize = (32 + messageSize) % 16;
    if (additionalSize != 0) {
        additionalSize = 16 - additionalSize;
    }
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(24 + 32 + messageSize + additionalSize);
    buffer->writeInt64(authKeyId);
    buffer->position(24);

    int64_t serverSalt = getServerSalt();
    buffer->writeInt64(serverSalt);
    buffer->writeInt64(connection->getSissionId());
    buffer->writeInt64(messageId);
    buffer->writeInt32(messageSeqNo);
    buffer->writeInt32(messageSize);
    messageBody->serializeToStream(buffer);
    if (freeMessageBody) {
        delete messageBody;
    }

    if (additionalSize != 0) {
        RAND_bytes(buffer->bytes() + 24 + 32 + messageSize, additionalSize);
    }
    static uint8_t messageKey[84];
    SHA1(buffer->bytes() + 24, 32 + messageSize, messageKey);
    memcpy(buffer->bytes() + 8, messageKey + 4, 16);

    if (quickAckId != nullptr) {
        *quickAckId = (((messageKey[0] & 0xff)) |
                       ((messageKey[1] & 0xff) << 8) |
                       ((messageKey[2] & 0xff) << 16) |
                       ((messageKey[3] & 0xff) << 24)) & 0x7fffffff;
    }

    generateMessageKey(authKey->bytes, messageKey + 4, messageKey + 20, false);
    aesIgeEncryption(buffer->bytes() + 24, messageKey + 20, messageKey + 52, true, false, buffer->limit() - 24);

    return buffer;
}

bool Datacenter::decryptServerResponse(int64_t keyId, uint8_t *key, uint8_t *data, uint32_t length) {
    if (authKeyId != keyId || length % 16 != 0) {
        return false;
    }
    static uint8_t messageKey[84];
    generateMessageKey(authKey->bytes, key, messageKey + 20, true);
    aesIgeEncryption(data, messageKey + 20, messageKey + 52, false, false, length);

    uint32_t messageLength;
    memcpy(&messageLength, data + 28, sizeof(uint32_t));
    if (messageLength > length - 32) {
        return false;
    }
    messageLength += 32;
    if (messageLength > length) {
        messageLength = length;
    }

    SHA1(data, messageLength, messageKey);

    return memcmp(messageKey + 4, key, 16) == 0;
}

bool Datacenter::hasAuthKey() {
    return authKey != nullptr;
}

Connection *Datacenter::createConnectionByType(uint32_t connectionType) {
    uint32_t connectionNum = connectionType >> 16;
    connectionType = connectionType & 0x0000ffff;
    switch (connectionType) {
        case ConnectionTypeGeneric:
            return createGenericConnection();
        case ConnectionTypeDownload:
            return createDownloadConnection(connectionNum);
        case ConnectionTypeUpload:
            return createUploadConnection();
        case ConnectionTypePush:
            return createPushConnection();
        default:
            return nullptr;
    }
}

Connection *Datacenter::getDownloadConnection(uint32_t num, bool create) {
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createDownloadConnection(num)->connect();
    }
    return downloadConnections[num];
}

Connection *Datacenter::getUploadConnection(bool create) {
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createUploadConnection()->connect();
    }
    return uploadConnection;
}

Connection *Datacenter::getGenericConnection(bool create) {
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createGenericConnection()->connect();
    }
    return genericConnection;
}

Connection *Datacenter::getPushConnection(bool create) {
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createPushConnection()->connect();
    }
    return pushConnection;
}

Connection *Datacenter::getConnectionByType(uint32_t connectionType, bool create) {
    uint32_t connectionNum = connectionType >> 16;
    connectionType = connectionType & 0x0000ffff;
    switch (connectionType) {
        case ConnectionTypeGeneric:
            return getGenericConnection(create);
        case ConnectionTypeDownload:
            return getDownloadConnection(connectionNum, create);
        case ConnectionTypeUpload:
            return getUploadConnection(create);
        case ConnectionTypePush:
            return getPushConnection(create);
        default:
            return nullptr;
    }
}

void Datacenter::exportAuthorization() {
    if (exportingAuthorization) {
        return;
    }
    exportingAuthorization = true;
    TL_auth_exportAuthorization *request = new TL_auth_exportAuthorization();
    request->dc_id = datacenterId;
    DEBUG_D("dc%u begin export authorization", datacenterId);
    ConnectionsManager::getInstance().sendRequest(request, [&](TLObject *response, TL_error *error) {
        if (error == nullptr) {
            TL_auth_exportedAuthorization *res = (TL_auth_exportedAuthorization *) response;
            TL_auth_importAuthorization *request2 = new TL_auth_importAuthorization();
            request2->bytes = std::move(res->bytes);
            request2->id = res->id;
            DEBUG_D("dc%u begin import authorization", datacenterId);
            ConnectionsManager::getInstance().sendRequest(request2, [&](TLObject *response2, TL_error *error2) {
                if (error2 == nullptr) {
                    authorized = true;
                    ConnectionsManager::getInstance().onDatacenterExportAuthorizationComplete(this);
                } else {
                    DEBUG_D("dc%u failed import authorization", datacenterId);
                }
                exportingAuthorization = false;
            }, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin, datacenterId, ConnectionTypeGeneric, true);
        } else {
            DEBUG_D("dc%u failed export authorization", datacenterId);
            exportingAuthorization = false;
        }
    }, nullptr, 0, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
}

bool Datacenter::isExportingAuthorization() {
    return exportingAuthorization;
}
