/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <stdlib.h>
#include <algorithm>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/bn.h>
#include <openssl/pem.h>
#include <openssl/aes.h>
#include <memory.h>
#include <inttypes.h>
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
#include "Handshake.h"

thread_local static SHA256_CTX sha256Ctx;

Datacenter::Datacenter(int32_t instance, uint32_t id) {
    instanceNum = instance;
    datacenterId = id;
    for (auto & a : uploadConnection) {
        a = nullptr;
    }
    for (auto & a : downloadConnection) {
        a = nullptr;
    }
    for (auto & a : proxyConnection) {
        a = nullptr;
    }
}

Datacenter::Datacenter(int32_t instance, NativeByteBuffer *data) {
    instanceNum = instance;
    for (auto & a : uploadConnection) {
        a = nullptr;
    }
    for (auto & a : downloadConnection) {
        a = nullptr;
    }
    for (auto & a : proxyConnection) {
        a = nullptr;
    }
    uint32_t currentVersion = data->readUint32(nullptr);
    if (currentVersion >= 2 && currentVersion <= configVersion) {
        datacenterId = data->readUint32(nullptr);
        if (currentVersion >= 3) {
            lastInitVersion = data->readUint32(nullptr);
        }
        if (currentVersion >= 10) {
            lastInitMediaVersion = data->readUint32(nullptr);
        }
        int count = currentVersion >= 5 ? 4 : 1;
        for (int b = 0; b < count; b++) {
            std::vector<TcpAddress> *array;
            switch (b) {
                case 0:
                    array = &addressesIpv4;
                    break;
                case 1:
                    array = &addressesIpv6;
                    break;
                case 2:
                    array = &addressesIpv4Download;
                    break;
                case 3:
                    array = &addressesIpv6Download;
                    break;
                default:
                    array = nullptr;
                    break;
            }
            if (array == nullptr) {
                continue;
            }
            uint32_t len = data->readUint32(nullptr);
            for (uint32_t a = 0; a < len; a++) {
                std::string address = data->readString(nullptr);
                uint32_t port = data->readUint32(nullptr);
                int32_t flags;
                std::string secret;
                if (currentVersion >= 7) {
                    flags = data->readInt32(nullptr);
                } else {
                    flags = 0;
                }
                if (currentVersion >= 11) {
                    secret = data->readString(nullptr);
                } else if (currentVersion >= 9) {
                    secret = data->readString(nullptr);
                    if (!secret.empty()) {
                        size_t size = secret.size() / 2;
                        char *result = new char[size];
                        for (int32_t i = 0; i < size; i++) {
                            result[i] = (char) (char2int(secret[i * 2]) * 16 + char2int(secret[i * 2 + 1]));
                        }
                        secret = std::string(result, size);
                        delete[] result;
                    }
                }
                (*array).push_back(TcpAddress(address, port, flags, secret));
            }
        }
        if (currentVersion >= 6) {
            isCdnDatacenter = data->readBool(nullptr);
        }
        uint32_t len = data->readUint32(nullptr);
        if (len != 0) {
            authKeyPerm = data->readBytes(len, nullptr);
        }
        if (currentVersion >= 4) {
            authKeyPermId = data->readInt64(nullptr);
        } else {
            len = data->readUint32(nullptr);
            if (len != 0) {
                authKeyPermId = data->readInt64(nullptr);
            }
        }
        if (currentVersion >= 8) {
            len = data->readUint32(nullptr);
            if (len != 0) {
                authKeyTemp = data->readBytes(len, nullptr);
            }
            authKeyTempId = data->readInt64(nullptr);
        }
        if (currentVersion >= 12) {
            len = data->readUint32(nullptr);
            if (len != 0) {
                authKeyMediaTemp = data->readBytes(len, nullptr);
            }
            authKeyMediaTempId = data->readInt64(nullptr);
        }
        authorized = data->readInt32(nullptr) != 0;
        len = data->readUint32(nullptr);
        for (uint32_t a = 0; a < len; a++) {
            auto salt = new TL_future_salt();
            salt->valid_since = data->readInt32(nullptr);
            salt->valid_until = data->readInt32(nullptr);
            salt->salt = data->readInt64(nullptr);
            serverSalts.push_back(std::unique_ptr<TL_future_salt>(salt));
        }
        if (currentVersion >= 13) {
            len = data->readUint32(nullptr);
            for (uint32_t a = 0; a < len; a++) {
                auto salt = new TL_future_salt();
                salt->valid_since = data->readInt32(nullptr);
                salt->valid_until = data->readInt32(nullptr);
                salt->salt = data->readInt64(nullptr);
                mediaServerSalts.push_back(std::unique_ptr<TL_future_salt>(salt));
            }
        }
    }

    if (config == nullptr) {
        config = new Config(instanceNum, "dc" + to_string_int32(datacenterId) + "conf.dat");
    }
    NativeByteBuffer *buffer = config->readConfig();
    if (buffer != nullptr) {
        uint32_t version = buffer->readUint32(nullptr);
        if (version >= 1) {
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

TcpAddress *Datacenter::getCurrentAddress(uint32_t flags) {
    uint32_t currentAddressNum;
    std::vector<TcpAddress> *addresses;
    if (flags == 0 && (authKeyPerm == nullptr || PFS_ENABLED && authKeyTemp == nullptr) && !addressesIpv4Temp.empty()) {
        flags = TcpAddressFlagTemp;
    }
    if ((flags & TcpAddressFlagTemp) != 0) {
        currentAddressNum = currentAddressNumIpv4Temp;
        addresses = &addressesIpv4Temp;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentAddressNum = currentAddressNumIpv6Download;
            addresses = &addressesIpv6Download;
        } else {
            currentAddressNum = currentAddressNumIpv4Download;
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentAddressNum = currentAddressNumIpv6;
            addresses = &addressesIpv6;
        } else {
            currentAddressNum = currentAddressNumIpv4;
            addresses = &addressesIpv4;
        }
    }
    if (addresses->empty()) {
        return nullptr;
    }
    if ((flags & TcpAddressFlagStatic) != 0) {
        for (auto & addresse : *addresses) {
            if ((addresse.flags & TcpAddressFlagStatic) != 0) {
                return &addresse;
            }
        }
    }
    if (currentAddressNum >= addresses->size()) {
        currentAddressNum = 0;
        if ((flags & TcpAddressFlagTemp) != 0) {
            currentAddressNumIpv4Temp = currentAddressNum;
        } else if ((flags & TcpAddressFlagDownload) != 0) {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentAddressNumIpv6Download = currentAddressNum;
            } else {
                currentAddressNumIpv4Download = currentAddressNum;
            }
        } else {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentAddressNumIpv6 = currentAddressNum;
            } else {
                currentAddressNumIpv4 = currentAddressNum;
            }
        }
    }
    return &(*addresses)[currentAddressNum];
}

int32_t Datacenter::getCurrentPort(uint32_t flags) {
    uint32_t currentAddressNum;
    uint32_t currentPortNum;
    std::vector<TcpAddress> *addresses;
    if (flags == 0 && (authKeyPerm == nullptr || PFS_ENABLED && authKeyTemp == nullptr) && !addressesIpv4Temp.empty()) {
        flags = TcpAddressFlagTemp;
    }
    if ((flags & TcpAddressFlagTemp) != 0) {
        currentAddressNum = currentAddressNumIpv4Temp;
        currentPortNum = currentPortNumIpv4Temp;
        addresses = &addressesIpv4Temp;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentAddressNum = currentAddressNumIpv6Download;
            currentPortNum = currentPortNumIpv6Download;
            addresses = &addressesIpv6Download;
        } else {
            currentAddressNum = currentAddressNumIpv4Download;
            currentPortNum = currentPortNumIpv4Download;
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentAddressNum = currentAddressNumIpv6;
            currentPortNum = currentPortNumIpv6;
            addresses = &addressesIpv6;
        } else {
            currentAddressNum = currentAddressNumIpv4;
            currentPortNum = currentPortNumIpv4;
            addresses = &addressesIpv4;
        }
    }
    if (addresses->empty()) {
        return 443;
    }

    if ((flags & TcpAddressFlagStatic) != 0) {
        uint32_t num = 0;
        for (auto & addresse : *addresses) {
            if ((addresse.flags & TcpAddressFlagStatic) != 0) {
                currentAddressNum = num;
                break;
            }
            num++;
        }
    }
    if (currentAddressNum >= addresses->size()) {
        currentAddressNum = 0;
        if ((flags & TcpAddressFlagTemp) != 0) {
            currentAddressNumIpv4Temp = currentAddressNum;
        } else if ((flags & TcpAddressFlagDownload) != 0) {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentAddressNumIpv6Download = currentAddressNum;
            } else {
                currentAddressNumIpv4Download = currentAddressNum;
            }
        } else {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentAddressNumIpv6 = currentAddressNum;
            } else {
                currentAddressNumIpv4 = currentAddressNum;
            }
        }
    }
    if (currentPortNum >= 4) {
        currentPortNum = 0;
        if ((flags & TcpAddressFlagTemp) != 0) {
            currentPortNumIpv4Temp = currentAddressNum;
        } else if ((flags & TcpAddressFlagDownload) != 0) {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentPortNumIpv6Download = currentPortNum;
            } else {
                currentPortNumIpv4Download = currentPortNum;
            }
        } else {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentPortNumIpv6 = currentPortNum;
            } else {
                currentPortNumIpv4 = currentPortNum;
            }
        }
    }
    TcpAddress *address = &((*addresses) [currentAddressNum]);
    int32_t port;
    if (!address->secret.empty()) {
        port = -1;
    } else {
        port = defaultPorts[currentPortNum];
    }
    if (port == -1) {
        return address->port;
    }
    return port;
}

void Datacenter::addAddressAndPort(std::string address, uint32_t port, uint32_t flags, std::string secret) {
    std::vector<TcpAddress> *addresses;
    if ((flags & TcpAddressFlagTemp) != 0) {
        addresses = &addressesIpv4Temp;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            addresses = &addressesIpv6Download;
        } else {
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            addresses = &addressesIpv6;
        } else {
            addresses = &addressesIpv4;
        }
    }
    for (auto & addresse : *addresses) {
        if (addresse.address == address && addresse.port == port) {
            return;
        }
    }
    addresses->push_back(TcpAddress(address, port, flags, secret));
}

void Datacenter::nextAddressOrPort(uint32_t flags) {
    uint32_t currentPortNum;
    uint32_t currentAddressNum;
    std::vector<TcpAddress> *addresses;
    if (flags == 0 && (authKeyPerm == nullptr || PFS_ENABLED && authKeyTemp == nullptr) && !addressesIpv4Temp.empty()) {
        flags = TcpAddressFlagTemp;
    }
    if ((flags & TcpAddressFlagTemp) != 0) {
        currentPortNum = currentPortNumIpv4Temp;
        currentAddressNum = currentAddressNumIpv4Temp;
        addresses = &addressesIpv4Temp;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentPortNum = currentPortNumIpv6Download;
            currentAddressNum = currentAddressNumIpv6Download;
            addresses = &addressesIpv6Download;
        } else {
            currentPortNum = currentPortNumIpv4Download;
            currentAddressNum = currentAddressNumIpv4Download;
            addresses = &addressesIpv4Download;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentPortNum = currentPortNumIpv6;
            currentAddressNum = currentAddressNumIpv6;
            addresses = &addressesIpv6;
        } else {
            currentPortNum = currentPortNumIpv4;
            currentAddressNum = currentAddressNumIpv4;
            addresses = &addressesIpv4;
        }
    }

    bool tryNextPort = true;
    if ((flags & TcpAddressFlagStatic) == 0 && currentAddressNum < addresses->size()) {
        TcpAddress *currentAddress = &((*addresses)[currentAddressNum]);
        tryNextPort = (currentAddress->flags & TcpAddressFlagStatic) == 0;
    }
    if (tryNextPort && currentPortNum + 1 < 4) {
        currentPortNum++;
    } else {
        if (currentAddressNum + 1 < addresses->size()) {
            currentAddressNum++;
        } else {
            repeatCheckingAddresses = true;
            currentAddressNum = 0;
        }
        currentPortNum = 0;
    }
    if ((flags & TcpAddressFlagTemp) != 0) {
        currentPortNumIpv4Temp = currentPortNum;
        currentAddressNumIpv4Temp = currentAddressNum;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentPortNumIpv6Download = currentPortNum;
            currentAddressNumIpv6Download = currentAddressNum;
        } else {
            currentPortNumIpv4Download = currentPortNum;
            currentAddressNumIpv4Download = currentAddressNum;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentPortNumIpv6 = currentPortNum;
            currentAddressNumIpv6 = currentAddressNum;
        } else {
            currentPortNumIpv4 = currentPortNum;
            currentAddressNumIpv4 = currentAddressNum;
        }
    }
}

bool Datacenter::isCustomPort(uint32_t flags) {
    uint32_t currentPortNum;
    if (flags == 0 && (authKeyPerm == nullptr || PFS_ENABLED && authKeyTemp == nullptr) && !addressesIpv4Temp.empty()) {
        flags = TcpAddressFlagTemp;
    }
    if ((flags & TcpAddressFlagTemp) != 0) {
        currentPortNum = currentPortNumIpv4Temp;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentPortNum = currentPortNumIpv6Download;
        } else {
            currentPortNum = currentPortNumIpv4Download;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            currentPortNum = currentPortNumIpv6;
        } else {
            currentPortNum = currentPortNumIpv4;
        }
    }
    return defaultPorts[currentPortNum] != -1;
}

void Datacenter::storeCurrentAddressAndPortNum() {
    if (config == nullptr) {
        config = new Config(instanceNum, "dc" + to_string_int32(datacenterId) + "conf.dat");
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

void Datacenter::resetAddressAndPortNum() {
    currentPortNumIpv4 = 0;
    currentAddressNumIpv4 = 0;
    currentPortNumIpv6 = 0;
    currentAddressNumIpv6 = 0;
    currentPortNumIpv4Download = 0;
    currentAddressNumIpv4Download = 0;
    currentPortNumIpv6Download = 0;
    currentAddressNumIpv6Download = 0;
    storeCurrentAddressAndPortNum();
}

void Datacenter::replaceAddresses(std::vector<TcpAddress> &newAddresses, uint32_t flags) {
    isCdnDatacenter = (flags & 8) != 0;
    TcpAddress *currentTcpAddress = getCurrentAddress(flags);
    std::string currentAddress = currentTcpAddress != nullptr ? currentTcpAddress->address : "";
    if ((flags & TcpAddressFlagTemp) != 0) {
        addressesIpv4Temp = newAddresses;
    } else if ((flags & TcpAddressFlagDownload) != 0) {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            addressesIpv6Download = newAddresses;
        } else {
            addressesIpv4Download = newAddresses;
        }
    } else {
        if ((flags & TcpAddressFlagIpv6) != 0) {
            addressesIpv6 = newAddresses;
        } else {
            addressesIpv4 = newAddresses;
        }
    }
    TcpAddress *newTcpAddress = getCurrentAddress(flags);
    std::string newAddress = newTcpAddress != nullptr ? newTcpAddress->address : "";
    if (currentAddress.compare(newAddress)) {
        if ((flags & TcpAddressFlagTemp) != 0) {
            currentPortNumIpv4Temp = 0;
        } else if ((flags & TcpAddressFlagDownload) != 0) {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentPortNumIpv6Download = 0;
            } else {
                currentPortNumIpv4Download = 0;
            }
        } else {
            if ((flags & TcpAddressFlagIpv6) != 0) {
                currentPortNumIpv6 = 0;
            } else {
                currentPortNumIpv4 = 0;
            }
        }
    }
}

void Datacenter::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(configVersion);
    stream->writeInt32(datacenterId);
    stream->writeInt32(lastInitVersion);
    stream->writeInt32(lastInitMediaVersion);
    size_t size;
    for (int b = 0; b < 4; b++) {
        std::vector<TcpAddress> *array;
        switch (b) {
            case 0:
                array = &addressesIpv4;
                break;
            case 1:
                array = &addressesIpv6;
                break;
            case 2:
                array = &addressesIpv4Download;
                break;
            case 3:
                array = &addressesIpv6Download;
                break;
            default:
                array = nullptr;
                break;
        }
        if (array == nullptr) {
            continue;
        }
        stream->writeInt32((int32_t) (size = array->size()));
        for (uint32_t a = 0; a < size; a++) {
            stream->writeString((*array)[a].address);
            stream->writeInt32((*array)[a].port);
            stream->writeInt32((*array)[a].flags);
            stream->writeString((*array)[a].secret);
        }
    }
    stream->writeBool(isCdnDatacenter);
    if (authKeyPerm != nullptr) {
        stream->writeInt32(authKeyPerm->length);
        stream->writeBytes(authKeyPerm);
    } else {
        stream->writeInt32(0);
    }
    stream->writeInt64(authKeyPermId);
    if (authKeyTemp != nullptr) {
        stream->writeInt32(authKeyTemp->length);
        stream->writeBytes(authKeyTemp);
    } else {
        stream->writeInt32(0);
    }
    stream->writeInt64(authKeyTempId);
    if (authKeyMediaTemp != nullptr) {
        stream->writeInt32(authKeyMediaTemp->length);
        stream->writeBytes(authKeyMediaTemp);
    } else {
        stream->writeInt32(0);
    }
    stream->writeInt64(authKeyMediaTempId);
    stream->writeInt32(authorized ? 1 : 0);

    size = 0;
    for (uint32_t a = 0; a < serverSalts.size(); a++) {
        if (serverSalts[a] != nullptr) size++;
    }
    stream->writeInt32((int32_t) size);
    for (uint32_t a = 0; a < serverSalts.size(); a++) {
        if (serverSalts[a] == nullptr) continue;
        stream->writeInt32(serverSalts[a]->valid_since);
        stream->writeInt32(serverSalts[a]->valid_until);
        stream->writeInt64(serverSalts[a]->salt);
    }

    size = 0;
    for (uint32_t a = 0; a < mediaServerSalts.size(); a++) {
        if (mediaServerSalts[a] != nullptr) size++;
    }
    stream->writeInt32((int32_t) size);
    for (uint32_t a = 0; a < mediaServerSalts.size(); a++) {
        if (mediaServerSalts[a] == nullptr) continue;
        stream->writeInt32(mediaServerSalts[a]->valid_since);
        stream->writeInt32(mediaServerSalts[a]->valid_until);
        stream->writeInt64(mediaServerSalts[a]->salt);
    }
}

void Datacenter::clearAuthKey(HandshakeType type) {
    if (type == HandshakeTypeAll || isCdnDatacenter) {
        if (authKeyPerm != nullptr) {
            delete authKeyPerm;
            authKeyPerm = nullptr;
            if (LOGS_ENABLED) DEBUG_D("dc%d account%u clear authKeyPerm", datacenterId, instanceNum);
        }
        authKeyPermId = 0;
        serverSalts.clear();
    }
    if (type == HandshakeTypeMediaTemp || type == HandshakeTypeAll) {
        if (authKeyMediaTemp != nullptr) {
            delete authKeyMediaTemp;
            authKeyMediaTemp = nullptr;
            if (LOGS_ENABLED) DEBUG_D("dc%d account%u clear authKeyMediaTemp", datacenterId, instanceNum);
        }
        authKeyMediaTempId = 0;
        lastInitMediaVersion = 0;
        mediaServerSalts.clear();
    }
    if (type == HandshakeTypeTemp || type == HandshakeTypeAll) {
        if (authKeyTemp != nullptr) {
            delete authKeyTemp;
            authKeyTemp = nullptr;
            if (LOGS_ENABLED) DEBUG_D("dc%d account%u clear authKeyTemp", datacenterId, instanceNum);
        }
        authKeyTempId = 0;
        lastInitVersion = 0;
    }
    handshakes.clear();
}

void Datacenter::clearServerSalts(bool media) {
    std::vector<std::unique_ptr<TL_future_salt>> &salts = media ? mediaServerSalts : serverSalts;
    salts.clear();
}

int64_t Datacenter::getServerSalt(bool media) {
    int32_t date = ConnectionsManager::getInstance(instanceNum).getCurrentTime();

    bool cleanupNeeded = false;

    int64_t result = 0;
    int32_t maxRemainingInterval = 0;

    std::vector<std::unique_ptr<TL_future_salt>> &salts = media ? mediaServerSalts : serverSalts;

    size_t size = salts.size();
    for (uint32_t a = 0; a < size; a++) {
        TL_future_salt *salt = salts[a].get();
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
        size = salts.size();
        for (uint32_t i = 0; i < size; i++) {
            if (salts[i]->valid_until < date) {
                salts.erase(salts.begin() + i);
                size--;
                i--;
            }
        }
    }

    if (result == 0) {
        if (LOGS_ENABLED) DEBUG_D("dc%u valid salt not found", datacenterId);
    }

    return result;
}

void Datacenter::mergeServerSalts(TL_future_salts *futureSalts, bool media) {
    if (futureSalts->salts.empty()) {
        return;
    }
    std::vector<std::unique_ptr<TL_future_salt>> &salts = media ? mediaServerSalts : serverSalts;

    int32_t date = ConnectionsManager::getInstance(instanceNum).getCurrentTime();
    std::vector<int64_t> existingSalts;
    existingSalts.reserve(salts.size());
    size_t size = salts.size();
    for (uint32_t a = 0; a < size; a++) {
        existingSalts.push_back(salts[a]->salt);
    }
    bool added = false;
    size = futureSalts->salts.size();
    for (uint32_t a = 0; a < size; a++) {
        int64_t value = futureSalts->salts[a]->salt;
        if (std::find(existingSalts.begin(), existingSalts.end(), value) == existingSalts.end() && futureSalts->salts[a]->valid_until > date) {
            salts.push_back(std::unique_ptr<TL_future_salt>(std::move(futureSalts->salts[a])));
            added = true;
        }
    }
    if (added) {
        std::sort(salts.begin(), salts.end(), [](const std::unique_ptr<TL_future_salt> &x, const std::unique_ptr<TL_future_salt> &y) { return x->valid_since < y->valid_since; });
    }
}

void Datacenter::addServerSalt(std::unique_ptr<TL_future_salt> &serverSalt, bool media) {
    std::vector<std::unique_ptr<TL_future_salt>> &salts = media ? mediaServerSalts : serverSalts;

    size_t size = salts.size();
    for (uint32_t a = 0; a < size; a++) {
        if (salts[a]->salt == serverSalt->salt) {
            return;
        }
    }
    salts.push_back(std::move(serverSalt));
    std::sort(salts.begin(), salts.end(), [](const std::unique_ptr<TL_future_salt> &x, const std::unique_ptr<TL_future_salt> &y) { return x->valid_since < y->valid_since; });
}

bool Datacenter::containsServerSalt(int64_t value, bool media) {
    std::vector<std::unique_ptr<TL_future_salt>> &salts = media ? mediaServerSalts : serverSalts;

    size_t size = salts.size();
    for (uint32_t a = 0; a < size; a++) {
        if (salts[a]->salt == value) {
            return true;
        }
    }
    return false;
}

void Datacenter::suspendConnections(bool suspendPush) {
    if (genericConnection != nullptr) {
        genericConnection->suspendConnection();
    }
    if (suspendPush && pushConnection != nullptr) {
        pushConnection->suspendConnection();
    }
    if (genericMediaConnection != nullptr) {
        genericMediaConnection->suspendConnection();
    }
    if (tempConnection != nullptr) {
        tempConnection->suspendConnection();
    }
    for (auto & a : uploadConnection) {
        if (a != nullptr) {
            a->suspendConnection();
        }
    }
    for (auto & a : downloadConnection) {
        if (a != nullptr) {
            a->suspendConnection();
        }
    }
}

void Datacenter::getSessions(std::vector<int64_t> &sessions) {
    if (genericConnection != nullptr) {
        sessions.push_back(genericConnection->getSessionId());
    }
    if (genericMediaConnection != nullptr) {
        sessions.push_back(genericMediaConnection->getSessionId());
    }
    if (tempConnection != nullptr) {
        sessions.push_back(tempConnection->getSessionId());
    }
    for (auto & a : uploadConnection) {
        if (a != nullptr) {
            sessions.push_back(a->getSessionId());
        }
    }
    for (auto & a : downloadConnection) {
        if (a != nullptr) {
            sessions.push_back(a->getSessionId());
        }
    }
    for (auto & a : proxyConnection) {
        if (a != nullptr) {
            sessions.push_back(a->getSessionId());
        }
    }
}

void Datacenter::recreateSessions(HandshakeType type) {
    if (type == HandshakeTypeAll || type == HandshakeTypeTemp || type == HandshakeTypePerm) {
        if (genericConnection != nullptr) {
            genericConnection->recreateSession();
        }
        if (tempConnection != nullptr) {
            tempConnection->recreateSession();
        }
        for (auto & a : uploadConnection) {
            if (a != nullptr) {
                a->recreateSession();
            }
        }
        for (auto & a : proxyConnection) {
            if (a != nullptr) {
                a->recreateSession();
            }
        }
    }
    if (type == HandshakeTypeAll || type == HandshakeTypeMediaTemp || type == HandshakeTypePerm) {
        for (auto & a : downloadConnection) {
            if (a != nullptr) {
                a->recreateSession();
            }
        }
        if (genericMediaConnection != nullptr) {
            genericMediaConnection->recreateSession();
        }
    }
}

Connection *Datacenter::createProxyConnection(uint8_t num) {
    if (proxyConnection[num] == nullptr) {
        proxyConnection[num] = new Connection(this, ConnectionTypeProxy, num);
    }
    return proxyConnection[num];
}

Connection *Datacenter::createDownloadConnection(uint8_t num) {
    if (downloadConnection[num] == nullptr) {
        downloadConnection[num] = new Connection(this, ConnectionTypeDownload, num);
    }
    return downloadConnection[num];
}

Connection *Datacenter::createUploadConnection(uint8_t num) {
    if (uploadConnection[num] == nullptr) {
        uploadConnection[num] = new Connection(this, ConnectionTypeUpload, num);
    }
    return uploadConnection[num];
}

Connection *Datacenter::createGenericConnection() {
    if (genericConnection == nullptr) {
        genericConnection = new Connection(this, ConnectionTypeGeneric, 0);
    }
    return genericConnection;
}

Connection *Datacenter::createGenericMediaConnection() {
    if (genericMediaConnection == nullptr) {
        genericMediaConnection = new Connection(this, ConnectionTypeGenericMedia, 0);
    }
    return genericMediaConnection;
}

Connection *Datacenter::createPushConnection() {
    if (pushConnection == nullptr) {
        pushConnection = new Connection(this, ConnectionTypePush, 0);
    }
    return pushConnection;
}

Connection *Datacenter::createTempConnection() {
    if (tempConnection == nullptr) {
        tempConnection = new Connection(this, ConnectionTypeTemp, 0);
    }
    return tempConnection;
}

uint32_t Datacenter::getDatacenterId() {
    return datacenterId;
}

bool Datacenter::isHandshakingAny() {
    return !handshakes.empty();
}

bool Datacenter::isHandshaking(bool media) {
    if (handshakes.empty()) {
        return false;
    }
    if (media && (isCdnDatacenter || !PFS_ENABLED)) {
        media = false;
    }
    for (auto & iter : handshakes) {
        Handshake *handshake = iter.get();
        if (handshake->getType() == HandshakeTypePerm || (media && handshake->getType() == HandshakeTypeMediaTemp) || (!media && handshake->getType() != HandshakeTypeMediaTemp)) {
            return true;
        }
    }
    return false;
}

bool Datacenter::isHandshaking(HandshakeType type) {
    if (handshakes.empty()) {
        return false;
    }
    for (auto & iter : handshakes) {
        Handshake *handshake = iter.get();
        if (handshake->getType() == type) {
            return true;
        }
    }
    return false;
}

void Datacenter::beginHandshake(HandshakeType handshakeType, bool reconnect) {
    if (handshakeType == HandshakeTypeCurrent) {
        for (auto & iter : handshakes) {
            Handshake *handshake = iter.get();
            handshake->beginHandshake(reconnect);
        }
    } else {
        if (authKeyPerm == nullptr) {
            if (!isHandshaking(HandshakeTypePerm)) {
                auto handshake = new Handshake(this, HandshakeTypePerm, this);
                handshakes.push_back(std::unique_ptr<Handshake>(handshake));
                handshake->beginHandshake(reconnect);
            }
        } else if (PFS_ENABLED) {
            if (handshakeType == HandshakeTypeAll || handshakeType == HandshakeTypeTemp) {
                if (!isHandshaking(HandshakeTypeTemp)) {
                    auto handshake = new Handshake(this, HandshakeTypeTemp, this);
                    handshakes.push_back(std::unique_ptr<Handshake>(handshake));
                    handshake->beginHandshake(reconnect);
                }
            }
            if ((handshakeType == HandshakeTypeAll || handshakeType == HandshakeTypeMediaTemp) && hasMediaAddress()) {
                if (!isHandshaking(HandshakeTypeMediaTemp)) {
                    auto handshake = new Handshake(this, HandshakeTypeMediaTemp, this);
                    handshakes.push_back(std::unique_ptr<Handshake>(handshake));
                    handshake->beginHandshake(reconnect);
                }
            }
        }
    }
}

void Datacenter::onHandshakeConnectionClosed(Connection *connection) {
    if (handshakes.empty()) {
        return;
    }
    bool media = connection->getConnectionType() == ConnectionTypeGenericMedia;
    for (auto & iter : handshakes) {
        Handshake *handshake = iter.get();
        if ((media && handshake->getType() == HandshakeTypeMediaTemp) || (!media && handshake->getType() != HandshakeTypeMediaTemp)) {
            handshake->onHandshakeConnectionClosed();
        }
    }
}

void Datacenter::onHandshakeConnectionConnected(Connection *connection) {
    if (handshakes.empty()) {
        return;
    }
    bool media = connection->getConnectionType() == ConnectionTypeGenericMedia;
    for (auto & iter : handshakes) {
        Handshake *handshake = iter.get();
        if ((media && handshake->getType() == HandshakeTypeMediaTemp) || (!media && handshake->getType() != HandshakeTypeMediaTemp)) {
            handshake->onHandshakeConnectionConnected();
        }
    }
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

void Datacenter::processHandshakeResponse(bool media, TLObject *message, int64_t messageId) {
    if (handshakes.empty()) {
        return;
    }
    for (auto & iter : handshakes) {
        Handshake *handshake = iter.get();
        if ((media && handshake->getType() == HandshakeTypeMediaTemp) || (!media && handshake->getType() != HandshakeTypeMediaTemp)) {
            handshake->processHandshakeResponse(message, messageId);
        }
    }
}

TLObject *Datacenter::getCurrentHandshakeRequest(bool media) {
    if (handshakes.empty()) {
        return nullptr;
    }
    for (auto & iter : handshakes) {
        Handshake *handshake = iter.get();
        if ((media && handshake->getType() == HandshakeTypeMediaTemp) || (!media && handshake->getType() != HandshakeTypeMediaTemp)) {
            return handshake->getCurrentHandshakeRequest();
        }
    }
    return nullptr;
}

inline void generateMessageKey(int32_t instanceNum, uint8_t *authKey, uint8_t *messageKey, uint8_t *result, bool incoming, int mtProtoVersion) {
    uint32_t x = incoming ? 8 : 0;
    thread_local static uint8_t sha[68];
    switch (mtProtoVersion) {
        case 2:
            SHA256_Init(&sha256Ctx);
            SHA256_Update(&sha256Ctx, messageKey, 16);
            SHA256_Update(&sha256Ctx, authKey + x, 36);
            SHA256_Final(sha, &sha256Ctx);

            SHA256_Init(&sha256Ctx);
            SHA256_Update(&sha256Ctx, authKey + 40 + x, 36);
            SHA256_Update(&sha256Ctx, messageKey, 16);
            SHA256_Final(sha + 32, &sha256Ctx);

            memcpy(result, sha, 8);
            memcpy(result + 8, sha + 32 + 8, 16);
            memcpy(result + 8 + 16, sha + 24, 8);

            memcpy(result + 32, sha + 32, 8);
            memcpy(result + 32 + 8, sha + 8, 16);
            memcpy(result + 32 + 8 + 16, sha + 32 + 24, 8);
            break;
        default:
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
            break;
    }
}

ByteArray *Datacenter::getAuthKey(ConnectionType connectionType, bool perm, int64_t *authKeyId, int32_t allowPendingKey) {
    bool usePermKey = isCdnDatacenter || perm || !PFS_ENABLED;
    if (usePermKey) {
        if (authKeyId != nullptr) {
            *authKeyId = authKeyPermId;
        }
        return authKeyPerm;
    } else {
        bool media = Connection::isMediaConnectionType(connectionType) && hasMediaAddress();
        ByteArray *authKeyPending = nullptr;
        int64_t authKeyPendingId = 0;
        for (auto & iter : handshakes) {
            Handshake *handshake = iter.get();
            if ((media && handshake->getType() == HandshakeTypeMediaTemp) || (!media && handshake->getType() == HandshakeTypeTemp)) {
                authKeyPending = handshake->getPendingAuthKey();
                authKeyPendingId = handshake->getPendingAuthKeyId();
                break;
            }
        }
        if ((allowPendingKey & 1) != 0 && authKeyPending != nullptr) {
            if (authKeyId != nullptr) {
                *authKeyId = authKeyPendingId;
            }
            return authKeyPending;
        } else if (media) {
            if (authKeyId != nullptr) {
                *authKeyId = authKeyMediaTempId;
            }
            return authKeyMediaTemp;
        } else {
            if (authKeyId != nullptr) {
                *authKeyId = authKeyTempId;
            }
            return authKeyTemp;
        }
    }
}

NativeByteBuffer *Datacenter::createRequestsData(std::vector<std::unique_ptr<NetworkMessage>> &requests, int32_t *quickAckId, Connection *connection, bool pfsInit) {
    int64_t authKeyId;
    ByteArray *authKey = getAuthKey(connection->getConnectionType(), pfsInit, &authKeyId, 1);
    if (authKey == nullptr) {
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
        if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) send message (session: 0x%" PRIx64 ", seqno: %d, messageid: 0x%" PRIx64 "): %s(%p)", connection, instanceNum, datacenterId, connection->getConnectionType(), (uint64_t) connection->getSessionId(), networkMessage->message->seqno, (uint64_t) networkMessage->message->msg_id, typeid(*messageBody).name(), messageBody);

        auto messageTime = (int64_t) (networkMessage->message->msg_id / 4294967296.0 * 1000);
        int64_t currentTime = ConnectionsManager::getInstance(instanceNum).getCurrentTimeMillis() + (int64_t) ConnectionsManager::getInstance(instanceNum).getTimeDifference() * 1000;

        if (!pfsInit && (networkMessage->forceContainer || messageTime < currentTime - 30000 || messageTime > currentTime + 25000)) {
            if (LOGS_ENABLED) DEBUG_D("wrap message in container");
            auto messageContainer = new TL_msg_container();
            messageContainer->messages.push_back(std::move(networkMessage->message));

            messageId = ConnectionsManager::getInstance(instanceNum).generateMessageId();
            messageBody = messageContainer;
            messageSeqNo = connection->generateMessageSeqNo(false);
            freeMessageBody = true;
        } else {
            messageId = networkMessage->message->msg_id;
            messageSeqNo = networkMessage->message->seqno;
        }
    } else {
        if (LOGS_ENABLED) DEBUG_D("start write messages to container");
        auto messageContainer = new TL_msg_container();
        size_t count = requests.size();
        for (uint32_t a = 0; a < count; a++) {
            NetworkMessage *networkMessage = requests[a].get();
            if (networkMessage->message->outgoingBody != nullptr) {
                messageBody = networkMessage->message->outgoingBody;
            } else {
                messageBody = networkMessage->message->body.get();
            }
            if (LOGS_ENABLED) DEBUG_D("connection(%p, account%u, dc%u, type %d) send message (session: 0x%" PRIx64 ", seqno: %d, messageid: 0x%" PRIx64 "): %s(%p)", connection, instanceNum, datacenterId, connection->getConnectionType(), (uint64_t) connection->getSessionId(), networkMessage->message->seqno, (uint64_t) networkMessage->message->msg_id, typeid(*messageBody).name(), messageBody);
            messageContainer->messages.push_back(std::unique_ptr<TL_message>(std::move(networkMessage->message)));
        }
        messageId = ConnectionsManager::getInstance(instanceNum).generateMessageId();
        messageBody = messageContainer;
        freeMessageBody = true;
        messageSeqNo = connection->generateMessageSeqNo(false);
    }

    int32_t mtProtoVersion;
    if (pfsInit) {
        mtProtoVersion = 1;
    } else {
        mtProtoVersion = 2;
    }
    uint32_t messageSize = messageBody->getObjectSize();
    uint32_t additionalSize = (32 + messageSize) % 16;
    if (additionalSize != 0) {
        additionalSize = 16 - additionalSize;
    }
    if (mtProtoVersion == 2) {
        uint8_t index;
        RAND_bytes(&index, 1);
        additionalSize += (2 + (index % 14)) * 16;
    }
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(24 + 32 + messageSize + additionalSize);
    buffer->writeInt64(authKeyId);
    buffer->position(24);

    if (pfsInit) {
        int64_t value;
        RAND_bytes((uint8_t *) &value, 8);
        buffer->writeInt64(value);
        RAND_bytes((uint8_t *) &value, 8);
        buffer->writeInt64(value);
    } else {
        buffer->writeInt64(getServerSalt(Connection::isMediaConnectionType(connection->getConnectionType())));
        buffer->writeInt64(connection->getSessionId());
    }
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
    thread_local static uint8_t messageKey[96];
    switch (mtProtoVersion) {
        case 2: {
            SHA256_Init(&sha256Ctx);
            SHA256_Update(&sha256Ctx, authKey->bytes + 88, 32);
            SHA256_Update(&sha256Ctx, buffer->bytes() + 24, 32 + messageSize + additionalSize);
            SHA256_Final(messageKey, &sha256Ctx);
            if (quickAckId != nullptr) {
                *quickAckId = (((messageKey[0] & 0xff)) |
                               ((messageKey[1] & 0xff) << 8) |
                               ((messageKey[2] & 0xff) << 16) |
                               ((messageKey[3] & 0xff) << 24)) & 0x7fffffff;
            }
            break;
        }
        default: {
            SHA1(buffer->bytes() + 24, 32 + messageSize, messageKey + 4);
            if (quickAckId != nullptr) {
                *quickAckId = (((messageKey[4] & 0xff)) |
                               ((messageKey[5] & 0xff) << 8) |
                               ((messageKey[6] & 0xff) << 16) |
                               ((messageKey[7] & 0xff) << 24)) & 0x7fffffff;
            }
            break;
        }
    }
    memcpy(buffer->bytes() + 8, messageKey + 8, 16);

    generateMessageKey(instanceNum, authKey->bytes, messageKey + 8, messageKey + 32, false, mtProtoVersion);
    aesIgeEncryption(buffer->bytes() + 24, messageKey + 32, messageKey + 64, true, false, buffer->limit() - 24);

    return buffer;
}

bool Datacenter::decryptServerResponse(int64_t keyId, uint8_t *key, uint8_t *data, uint32_t length, Connection *connection) {
    int64_t authKeyId;
    ByteArray *authKey = getAuthKey(connection->getConnectionType(), false, &authKeyId, 1);
    if (authKey == nullptr) {
        return false;
    }
    bool error = authKeyId != keyId;
    thread_local static uint8_t messageKey[96];
    generateMessageKey(instanceNum, authKey->bytes, key, messageKey + 32, true, 2);
    aesIgeEncryption(data, messageKey + 32, messageKey + 64, false, false, length);

    uint32_t messageLength;
    memcpy(&messageLength, data + 28, sizeof(uint32_t));
    uint32_t paddingLength = length - (messageLength + 32);

    error |= (messageLength > length - 32);
    error |= (paddingLength < 12);
    error |= (paddingLength > 1024);

    SHA256_Init(&sha256Ctx);
    SHA256_Update(&sha256Ctx, authKey->bytes + 88 + 8, 32);
    SHA256_Update(&sha256Ctx, data, length);
    SHA256_Final(messageKey, &sha256Ctx);

    for (uint32_t i = 0; i < 16; i++) {
        error |= (messageKey[i + 8] != key[i]);
    }

    return !error;
}

bool Datacenter::hasPermanentAuthKey() {
    return authKeyPerm != nullptr;
}

int64_t Datacenter::getPermanentAuthKeyId() {
    return authKeyPermId;
}

bool Datacenter::hasAuthKey(ConnectionType connectionType, int32_t allowPendingKey) {
    return getAuthKey(connectionType, false, nullptr, allowPendingKey) != nullptr;
}

Connection *Datacenter::createConnectionByType(uint32_t connectionType) {
    uint8_t connectionNum = (uint8_t) (connectionType >> 16);
    connectionType = connectionType & 0x0000ffff;
    switch (connectionType) {
        case ConnectionTypeGeneric:
            return createGenericConnection();
        case ConnectionTypeGenericMedia:
            return createGenericMediaConnection();
        case ConnectionTypeDownload:
            return createDownloadConnection(connectionNum);
        case ConnectionTypeUpload:
            return createUploadConnection(connectionNum);
        case ConnectionTypePush:
            return createPushConnection();
        case ConnectionTypeTemp:
            return createTempConnection();
        case ConnectionTypeProxy:
            return createProxyConnection(connectionNum);
        default:
            return nullptr;
    }
}

Connection *Datacenter::getProxyConnection(uint8_t num, bool create, bool connect) {
    ByteArray *authKey = getAuthKey(ConnectionTypeProxy, false, nullptr, 1);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        Connection *connection = createProxyConnection(num);
        if (connect) {
            connection->connect();
        }
    }
    return proxyConnection[num];
}

Connection *Datacenter::getDownloadConnection(uint8_t num, bool create) {
    ByteArray *authKey = getAuthKey(ConnectionTypeDownload, false, nullptr, 0);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createDownloadConnection(num)->connect();
    }
    return downloadConnection[num];
}

Connection *Datacenter::getUploadConnection(uint8_t num, bool create) {
    ByteArray *authKey = getAuthKey(ConnectionTypeUpload, false, nullptr, 0);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createUploadConnection(num)->connect();
    }
    return uploadConnection[num];
}

Connection *Datacenter::getGenericConnection(bool create, int32_t allowPendingKey) {
    ByteArray *authKey = getAuthKey(ConnectionTypeGeneric, false, nullptr, allowPendingKey);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createGenericConnection()->connect();
    }
    return genericConnection;
}

Connection *Datacenter::getGenericMediaConnection(bool create, int32_t allowPendingKey) {
    ByteArray *authKey = getAuthKey(ConnectionTypeGenericMedia, false, nullptr, allowPendingKey);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createGenericMediaConnection()->connect();
    }
    return genericMediaConnection;
}

Connection *Datacenter::getPushConnection(bool create) {
    ByteArray *authKey = getAuthKey(ConnectionTypePush, false, nullptr, 0);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createPushConnection()->connect();
    }
    return pushConnection;
}

Connection *Datacenter::getTempConnection(bool create) {
    ByteArray *authKey = getAuthKey(ConnectionTypeTemp, false, nullptr, 1);
    if (authKey == nullptr) {
        return nullptr;
    }
    if (create) {
        createTempConnection()->connect();
    }
    return tempConnection;
}

Connection *Datacenter::getConnectionByType(uint32_t connectionType, bool create, int32_t allowPendingKey) {
    uint8_t connectionNum = (uint8_t) (connectionType >> 16);
    connectionType = connectionType & 0x0000ffff;
    switch (connectionType) {
        case ConnectionTypeGeneric:
            return getGenericConnection(create, allowPendingKey);
        case ConnectionTypeGenericMedia:
            return getGenericMediaConnection(create, allowPendingKey);
        case ConnectionTypeDownload:
            return getDownloadConnection(connectionNum, create);
        case ConnectionTypeUpload:
            return getUploadConnection(connectionNum, create);
        case ConnectionTypePush:
            return getPushConnection(create);
        case ConnectionTypeTemp:
            return getTempConnection(create);
        case ConnectionTypeProxy:
            return getProxyConnection(connectionNum, create, create);
        default:
            return nullptr;
    }
}

void Datacenter::onHandshakeComplete(Handshake *handshake, int64_t keyId, ByteArray *authKey, int32_t timeDifference) {
    HandshakeType type = handshake->getType();
    for (auto iter = handshakes.begin(); iter != handshakes.end(); iter++) {
        if (iter->get() == handshake) {
            handshakes.erase(iter);
            if (type == HandshakeTypePerm) {
                authKeyPermId = keyId;
                authKeyPerm = authKey;
                if (!isCdnDatacenter && PFS_ENABLED) {
                    beginHandshake(HandshakeTypeAll, false);
                }
            } else {
                if (type == HandshakeTypeTemp) {
                    authKeyTempId = keyId;
                    authKeyTemp = authKey;
                    lastInitVersion = 0;
                } else if (type == HandshakeTypeMediaTemp) {
                    authKeyMediaTempId = keyId;
                    authKeyMediaTemp = authKey;
                    lastInitMediaVersion = 0;
                }
            }
            ConnectionsManager::getInstance(instanceNum).onDatacenterHandshakeComplete(this, type, timeDifference);
            break;
        }
    }
}

void Datacenter::exportAuthorization() {
    if (exportingAuthorization || isCdnDatacenter) {
        return;
    }
    exportingAuthorization = true;
    auto request = new TL_auth_exportAuthorization();
    request->dc_id = datacenterId;
    if (LOGS_ENABLED) DEBUG_D("dc%u begin export authorization", datacenterId);
    ConnectionsManager::getInstance(instanceNum).sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime, int64_t msgId, int32_t dcId) {
        if (error == nullptr) {
            auto res = (TL_auth_exportedAuthorization *) response;
            auto request2 = new TL_auth_importAuthorization();
            request2->bytes = std::move(res->bytes);
            request2->id = res->id;
            if (LOGS_ENABLED) DEBUG_D("dc%u begin import authorization", datacenterId);
            ConnectionsManager::getInstance(instanceNum).sendRequest(request2, [&](TLObject *response2, TL_error *error2, int32_t networkType, int64_t responseTime, int64_t msgId, int32_t dcId) {
                if (error2 == nullptr) {
                    authorized = true;
                    ConnectionsManager::getInstance(instanceNum).onDatacenterExportAuthorizationComplete(this);
                } else {
                    if (LOGS_ENABLED) DEBUG_D("dc%u failed import authorization", datacenterId);
                }
                exportingAuthorization = false;
            }, nullptr, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin, datacenterId, ConnectionTypeGeneric, true);
        } else {
            if (LOGS_ENABLED) DEBUG_D("dc%u failed export authorization", datacenterId);
            exportingAuthorization = false;
        }
    }, nullptr, nullptr, 0, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
}

bool Datacenter::isExportingAuthorization() {
    return exportingAuthorization;
}

bool Datacenter::hasMediaAddress() {
    std::vector<TcpAddress> *addresses;
    int strategy = ConnectionsManager::getInstance(instanceNum).getIpStratagy();
    if (strategy == USE_IPV6_ONLY) {
        addresses = &addressesIpv6Download;
    } else {
        addresses = &addressesIpv4Download;
    }
    return !addresses->empty();
}

void Datacenter::resetInitVersion() {
    lastInitVersion = 0;
    lastInitMediaVersion = 0;
}

bool Datacenter::isRepeatCheckingAddresses() {
    bool b = repeatCheckingAddresses;
    repeatCheckingAddresses = false;
    return b;
}

TL_help_configSimple *Datacenter::decodeSimpleConfig(NativeByteBuffer *buffer) {
    TL_help_configSimple *result = nullptr;

    if (buffer->limit() < 256) {
        return result;
    }

    static std::string public_key =
            "-----BEGIN RSA PUBLIC KEY-----\n"
                    "MIIBCgKCAQEAyr+18Rex2ohtVy8sroGPBwXD3DOoKCSpjDqYoXgCqB7ioln4eDCF\n"
                    "fOBUlfXUEvM/fnKCpF46VkAftlb4VuPDeQSS/ZxZYEGqHaywlroVnXHIjgqoxiAd\n"
                    "192xRGreuXIaUKmkwlM9JID9WS2jUsTpzQ91L8MEPLJ/4zrBwZua8W5fECwCCh2c\n"
                    "9G5IzzBm+otMS/YKwmR1olzRCyEkyAEjXWqBI9Ftv5eG8m0VkBzOG655WIYdyV0H\n"
                    "fDK/NWcvGqa0w/nriMD6mDjKOryamw0OP9QuYgMN0C9xMW9y8SmP4h92OAWodTYg\n"
                    "Y1hZCxdv6cs5UnW9+PWvS+WIbkh+GaWYxwIDAQAB\n"
                    "-----END RSA PUBLIC KEY-----";

    BIO *keyBio = BIO_new(BIO_s_mem());
    BIO_write(keyBio, public_key.c_str(), (int) public_key.length());

    RSA *rsaKey = PEM_read_bio_RSAPublicKey(keyBio, nullptr, nullptr, nullptr);
    if (rsaKey == nullptr) {
        if (rsaKey == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("Invalid rsa public key");
            return nullptr;
        }
    }

    BIGNUM x, y;
    uint8_t *bytes = buffer->bytes();
    BN_CTX *bnContext = BN_CTX_new();
    BN_init(&x);
    BN_init(&y);
    BN_bin2bn(bytes, 256, &x);

    const BIGNUM *n = NULL;
    const BIGNUM *e = NULL;
    RSA_get0_key(rsaKey, &n, &e, nullptr);

    if (BN_mod_exp(&y, &x, e, n, bnContext) == 1) {
        unsigned l = 256 - BN_num_bytes(&y);
        memset(bytes, 0, l);
        if (BN_bn2bin(&y, bytes + l) == 256 - l) {
            AES_KEY aeskey;
            unsigned char iv[16];
            memcpy(iv, bytes + 16, 16);
            AES_set_decrypt_key(bytes, 256, &aeskey);
            AES_cbc_encrypt(bytes + 32, bytes + 32, 256 - 32, &aeskey, iv, AES_DECRYPT);

            EVP_MD_CTX ctx;
            unsigned char sha256_out[32];
            unsigned olen = 0;
            EVP_MD_CTX_init(&ctx);
            EVP_DigestInit_ex(&ctx, EVP_sha256(), NULL);
            EVP_DigestUpdate(&ctx, bytes + 32, 256 - 32 - 16);
            EVP_DigestFinal_ex(&ctx, sha256_out, &olen);
            EVP_MD_CTX_cleanup(&ctx);
            if (olen == 32) {
                if (memcmp(bytes + 256 - 16, sha256_out, 16) == 0) {
                    unsigned data_len = *(unsigned *) (bytes + 32);
                    if (data_len && data_len <= 256 - 32 - 16 && !(data_len & 3)) {
                        buffer->position(32 + 4);
                        bool error = false;
                        result = TL_help_configSimple::TLdeserialize(buffer, buffer->readUint32(&error), 0, error);
                        if (error) {
                            if (result != nullptr) {
                                delete result;
                                result = nullptr;
                            }
                        }
                    } else {
                        if (LOGS_ENABLED) DEBUG_E("TL data length field invalid - %d", data_len);
                    }
                } else {
                    if (LOGS_ENABLED) DEBUG_E("RSA signature check FAILED (SHA256 mismatch)");
                }
            }
        }
    }
    BN_CTX_free(bnContext);
    BN_free(&x);
    BN_free(&y);
    RSA_free(rsaKey);
    BIO_free(keyBio);
    return result;
}
