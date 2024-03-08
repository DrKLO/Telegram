/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <stdlib.h>
#include <algorithm>
#include <memory>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/bn.h>
#include <openssl/pem.h>
#include <openssl/aes.h>
#include <memory.h>
#include "Handshake.h"
#include "FileLog.h"
#include "Datacenter.h"
#include "ConnectionsManager.h"
#include "MTProtoScheme.h"
#include "ApiScheme.h"
#include "BuffersStorage.h"
#include "NativeByteBuffer.h"
#include "Config.h"
#include "Connection.h"

thread_local static std::vector<std::string> serverPublicKeys;
thread_local static std::vector<uint64_t> serverPublicKeysFingerprints;
thread_local static std::map<int32_t, std::string> cdnPublicKeys;
thread_local static std::map<int32_t, uint64_t> cdnPublicKeysFingerprints;
thread_local static std::vector<Datacenter *> cdnWaitingDatacenters;
thread_local static bool loadingCdnKeys = false;
thread_local static BN_CTX *bnContext = nullptr;
thread_local static Config *cdnConfig = nullptr;

Handshake::Handshake(Datacenter *datacenter, HandshakeType type, HandshakeDelegate *handshakeDelegate) {
    currentDatacenter = datacenter;
    handshakeType = type;
    delegate = handshakeDelegate;
}

Handshake::~Handshake() {
    cleanupHandshake();
}

void Handshake::beginHandshake(bool reconnect) {
    if (LOGS_ENABLED) DEBUG_D("account%u dc%u handshake: begin, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
    cleanupHandshake();
    Connection *connection = getConnection();
    handshakeState = 1;

    if (reconnect) {
        connection->suspendConnection();
        connection->connect();
    }

    auto request = new TL_req_pq_multi();
    request->nonce = std::make_unique<ByteArray>(16);
    RAND_bytes(request->nonce->bytes, 16);
    authNonce = new ByteArray(request->nonce.get());
    sendRequestData(request, true);
}

void Handshake::cleanupHandshake() {
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
    if (authKeyTempPending != nullptr) {
        delete authKeyTempPending;
        authKeyTempPending = nullptr;
    }
    if (authKeyPendingMessageId != 0 || authKeyPendingRequestId != 0) {
        ConnectionsManager::getInstance(currentDatacenter->instanceNum).cancelRequestInternal(authKeyPendingRequestId, authKeyPendingMessageId, false, false, nullptr);
        authKeyPendingMessageId = 0;
        authKeyPendingRequestId = 0;
    }
    authKeyTempPendingId = 0;
}

inline Connection *Handshake::getConnection() {
    return handshakeType == HandshakeTypeMediaTemp ? currentDatacenter->createGenericMediaConnection() : currentDatacenter->createGenericConnection();
}

void Handshake::sendRequestData(TLObject *object, bool important) {
    uint32_t messageLength = object->getObjectSize();
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(20 + messageLength);
    buffer->writeInt64(0);
    buffer->writeInt64(ConnectionsManager::getInstance(currentDatacenter->instanceNum).generateMessageId());
    buffer->writeInt32(messageLength);
    object->serializeToStream(buffer);
    getConnection()->sendData(buffer, false, false);
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
        if (LOGS_ENABLED) DEBUG_FATAL("factorization failed for %" PRIu64, what);
        p = 0;
        q = 0;
        return false;
    }
}

inline bool check_prime(BIGNUM *p) {
    int result = 0;
    if (!BN_primality_test(&result, p, 64, bnContext, 0, NULL)) {
        if (LOGS_ENABLED) DEBUG_FATAL("OpenSSL error at BN_primality_test");
        return false;
    }
    return result != 0;
}

inline bool isGoodPrime(BIGNUM *p, uint32_t g) {
    if (g < 2 || g > 7 || BN_num_bits(p) != 2048) {
        return false;
    }

    BIGNUM *t = BN_new();
    BIGNUM *dh_g = BN_new();

    if (!BN_set_word(dh_g, 4 * g)) {
        if (LOGS_ENABLED) DEBUG_FATAL("OpenSSL error at BN_set_word(dh_g, 4 * g)");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }
    if (!BN_mod(t, p, dh_g, bnContext)) {
        if (LOGS_ENABLED) DEBUG_FATAL("OpenSSL error at BN_mod");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }
    uint64_t x = BN_get_word(t);
    if (x >= 4 * g) {
        if (LOGS_ENABLED) DEBUG_FATAL("OpenSSL error at BN_get_word");
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
        OPENSSL_free(prime);
        BN_free(t);
        return true;
    }
    OPENSSL_free(prime);

    if (!result || !check_prime(p)) {
        BN_free(t);
        return false;
    }

    BIGNUM *b = BN_new();
    if (!BN_set_word(b, 2)) {
        if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_set_word(b, 2)");
        BN_free(b);
        BN_free(t);
        return false;
    }
    if (!BN_div(t, 0, p, b, bnContext)) {
        if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_div");
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

void Handshake::cleanupServerKeys() {
    serverPublicKeys.clear();
    serverPublicKeysFingerprints.clear();
}

void Handshake::processHandshakeResponse(TLObject *message, int64_t messageId) {
    if (handshakeState == 0) {
        return;
    }
    const std::type_info &typeInfo = typeid(*message);
    if (typeInfo == typeid(TL_resPQ)) {
        processHandshakeResponse_resPQ(message, messageId);
    } else if (dynamic_cast<Server_DH_Params *>(message)) {
        processHandshakeResponse_serverDHParams(message, messageId);
    } else if (dynamic_cast<Set_client_DH_params_answer *>(message)) {
        processHandshakeResponse_serverDHParamsAnswer(message, messageId);
    }
}

void Handshake::processHandshakeResponse_resPQ(TLObject *message, int64_t messageId) {
    if (handshakeState != 1) {
        sendAckRequest(messageId);
        return;
    }

    handshakeState = 2;
    auto result = (TL_resPQ *) message;
    if (authNonce->isEqualTo(result->nonce.get())) {
        std::string key = "";
        int64_t keyFingerprint = 0;

        size_t count1 = result->server_public_key_fingerprints.size();
        if (currentDatacenter->isCdnDatacenter) {
            auto iter = cdnPublicKeysFingerprints.find(currentDatacenter->datacenterId);
            if (iter != cdnPublicKeysFingerprints.end()) {
                for (uint32_t a = 0; a < count1; a++) {
                    if ((uint64_t) result->server_public_key_fingerprints[a] == iter->second) {
                        keyFingerprint = iter->second;
                        key = cdnPublicKeys[currentDatacenter->datacenterId];
                    }
                }
            }
        } else {
            if (serverPublicKeys.empty()) {
                if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                    serverPublicKeys.emplace_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                                  "MIIBCgKCAQEAyMEdY1aR+sCR3ZSJrtztKTKqigvO/vBfqACJLZtS7QMgCGXJ6XIR\n"
                                                  "yy7mx66W0/sOFa7/1mAZtEoIokDP3ShoqF4fVNb6XeqgQfaUHd8wJpDWHcR2OFwv\n"
                                                  "plUUI1PLTktZ9uW2WE23b+ixNwJjJGwBDJPQEQFBE+vfmH0JP503wr5INS1poWg/\n"
                                                  "j25sIWeYPHYeOrFp/eXaqhISP6G+q2IeTaWTXpwZj4LzXq5YOpk4bYEQ6mvRq7D1\n"
                                                  "aHWfYmlEGepfaYR8Q0YqvvhYtMte3ITnuSJs171+GDqpdKcSwHnd6FudwGO4pcCO\n"
                                                  "j4WcDuXc2CTHgH8gFTNhp/Y8/SpDOhvn9QIDAQAB\n"
                                                  "-----END RSA PUBLIC KEY-----");
                    serverPublicKeysFingerprints.push_back(0xb25898df208d2603);
                } else {
                    serverPublicKeys.emplace_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                                  "MIIBCgKCAQEA6LszBcC1LGzyr992NzE0ieY+BSaOW622Aa9Bd4ZHLl+TuFQ4lo4g\n"
                                                  "5nKaMBwK/BIb9xUfg0Q29/2mgIR6Zr9krM7HjuIcCzFvDtr+L0GQjae9H0pRB2OO\n"
                                                  "62cECs5HKhT5DZ98K33vmWiLowc621dQuwKWSQKjWf50XYFw42h21P2KXUGyp2y/\n"
                                                  "+aEyZ+uVgLLQbRA1dEjSDZ2iGRy12Mk5gpYc397aYp438fsJoHIgJ2lgMv5h7WY9\n"
                                                  "t6N/byY9Nw9p21Og3AoXSL2q/2IJ1WRUhebgAdGVMlV1fkuOQoEzR7EdpqtQD9Cs\n"
                                                  "5+bfo3Nhmcyvk5ftB0WkJ9z6bNZ7yxrP8wIDAQAB\n"
                                                  "-----END RSA PUBLIC KEY-----");
                    serverPublicKeysFingerprints.push_back(0xd09d1d85de64fd85);
                }
            }

            size_t count2 = serverPublicKeysFingerprints.size();
            for (uint32_t a = 0; a < count1; a++) {
                for (uint32_t b = 0; b < count2; b++) {
                    if ((uint64_t) result->server_public_key_fingerprints[a] == serverPublicKeysFingerprints[b]) {
                        keyFingerprint = result->server_public_key_fingerprints[a];
                        key = serverPublicKeys[b];
                        break;
                    }
                }
                if (keyFingerprint != 0) {
                    break;
                }
            }
        }

        if (keyFingerprint == 0) {
            if (currentDatacenter->isCdnDatacenter) {
                if (LOGS_ENABLED) DEBUG_D("account%u dc%u handshake: can't find valid cdn server public key, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
                loadCdnConfig(currentDatacenter);
            } else {
                if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: can't find valid server public key, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
            }
            return;
        }

        authServerNonce = new ByteArray(result->server_nonce.get());

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

        auto request = new TL_req_DH_params();
        request->nonce = std::make_unique<ByteArray>(new ByteArray(authNonce));
        request->server_nonce = std::make_unique<ByteArray>(new ByteArray(authServerNonce));
        request->p = std::make_unique<ByteArray>(new ByteArray(4));
        request->p->bytes[3] = (uint8_t) p;
        request->p->bytes[2] = (uint8_t) (p >> 8);
        request->p->bytes[1] = (uint8_t) (p >> 16);
        request->p->bytes[0] = (uint8_t) (p >> 24);
        request->q = std::make_unique<ByteArray>(new ByteArray(4));
        request->q->bytes[3] = (uint8_t) q;
        request->q->bytes[2] = (uint8_t) (q >> 8);
        request->q->bytes[1] = (uint8_t) (q >> 16);
        request->q->bytes[0] = (uint8_t) (q >> 24);
        request->public_key_fingerprint = keyFingerprint;

        TLObject *innerData;
        if (handshakeType == HandshakeTypePerm) {
            auto tl_p_q_inner_data = new TL_p_q_inner_data_dc();
            tl_p_q_inner_data->nonce = std::make_unique<ByteArray>(authNonce);
            tl_p_q_inner_data->server_nonce = std::make_unique<ByteArray>(authServerNonce);
            tl_p_q_inner_data->pq = std::make_unique<ByteArray>(new ByteArray(result->pq.get()));
            tl_p_q_inner_data->p = std::make_unique<ByteArray>(new ByteArray(request->p.get()));
            tl_p_q_inner_data->q = std::make_unique<ByteArray>(new ByteArray(request->q.get()));
            tl_p_q_inner_data->new_nonce = std::make_unique<ByteArray>(new ByteArray(32));
            if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                tl_p_q_inner_data->dc = 10000 + currentDatacenter->datacenterId;
            } else {
                tl_p_q_inner_data->dc = currentDatacenter->datacenterId;
            }
            RAND_bytes(tl_p_q_inner_data->new_nonce->bytes, 32);
            authNewNonce = new ByteArray(tl_p_q_inner_data->new_nonce.get());
            innerData = tl_p_q_inner_data;
        } else {
            auto tl_p_q_inner_data_temp = new TL_p_q_inner_data_temp_dc();
            tl_p_q_inner_data_temp->nonce = std::make_unique<ByteArray>(new ByteArray(authNonce));
            tl_p_q_inner_data_temp->server_nonce = std::make_unique<ByteArray>(new ByteArray(authServerNonce));
            tl_p_q_inner_data_temp->pq = std::make_unique<ByteArray>(new ByteArray(result->pq.get()));
            tl_p_q_inner_data_temp->p = std::make_unique<ByteArray>(new ByteArray(request->p.get()));
            tl_p_q_inner_data_temp->q = std::make_unique<ByteArray>(new ByteArray(request->q.get()));
            tl_p_q_inner_data_temp->new_nonce = std::make_unique<ByteArray>(new ByteArray(32));
            if (handshakeType == HandshakeTypeMediaTemp) {
                if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                    tl_p_q_inner_data_temp->dc = -(10000 + currentDatacenter->datacenterId);
                } else {
                    tl_p_q_inner_data_temp->dc = -currentDatacenter->datacenterId;
                }
            } else {
                if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                    tl_p_q_inner_data_temp->dc = 10000 + currentDatacenter->datacenterId;
                } else {
                    tl_p_q_inner_data_temp->dc = currentDatacenter->datacenterId;
                }
            }
            tl_p_q_inner_data_temp->expires_in = TEMP_AUTH_KEY_EXPIRE_TIME;
            RAND_bytes(tl_p_q_inner_data_temp->new_nonce->bytes, 32);
            authNewNonce = new ByteArray(tl_p_q_inner_data_temp->new_nonce.get());
            innerData = tl_p_q_inner_data_temp;
        }

        uint32_t innerDataSize = innerData->getObjectSize();
        if (innerDataSize > 144) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: inner data too large %d, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, innerDataSize, handshakeType);
            delete innerData;
            beginHandshake(false);
            return;
        }
        uint32_t keySize = 32;
        uint32_t ivSize = 32;
        uint32_t paddedDataSize = 192;
        uint32_t encryptedDataSize = keySize + paddedDataSize + SHA256_DIGEST_LENGTH;
        uint32_t additionalSize = innerDataSize < paddedDataSize ? paddedDataSize - innerDataSize : 0;
        NativeByteBuffer *innerDataBuffer = BuffersStorage::getInstance().getFreeBuffer(encryptedDataSize + paddedDataSize + ivSize + SHA256_DIGEST_LENGTH + 256);

        innerDataBuffer->position(encryptedDataSize);
        innerData->serializeToStream(innerDataBuffer);
        delete innerData;

        BIO *keyBio = BIO_new(BIO_s_mem());
        BIO_write(keyBio, key.c_str(), (int) key.length());
        RSA *rsaKey = PEM_read_bio_RSAPublicKey(keyBio, nullptr, nullptr, nullptr);
        BIO_free(keyBio);

        while (true) {
            RAND_bytes(innerDataBuffer->bytes() + encryptedDataSize + innerDataSize, additionalSize);
            for (uint32_t i = 0; i < paddedDataSize; i++) {
                innerDataBuffer->bytes()[keySize + i] = innerDataBuffer->bytes()[encryptedDataSize + paddedDataSize - i - 1];
            }

            RAND_bytes(innerDataBuffer->bytes(), keySize);
            SHA256_CTX sha256Ctx;
            SHA256_Init(&sha256Ctx);
            SHA256_Update(&sha256Ctx, innerDataBuffer->bytes(), keySize);
            SHA256_Update(&sha256Ctx, innerDataBuffer->bytes() + encryptedDataSize, paddedDataSize);
            SHA256_Final(innerDataBuffer->bytes() + keySize + paddedDataSize, &sha256Ctx);

            memset(innerDataBuffer->bytes() + encryptedDataSize + paddedDataSize, 0, ivSize);
            Datacenter::aesIgeEncryption(innerDataBuffer->bytes() + keySize, innerDataBuffer->bytes(), innerDataBuffer->bytes() + encryptedDataSize + paddedDataSize, true, true, paddedDataSize + SHA256_DIGEST_LENGTH);

            SHA256_Init(&sha256Ctx);
            SHA256_Update(&sha256Ctx, innerDataBuffer->bytes() + keySize, paddedDataSize + SHA256_DIGEST_LENGTH);
            SHA256_Final(innerDataBuffer->bytes() + encryptedDataSize + paddedDataSize + ivSize, &sha256Ctx);

            for (uint32_t i = 0; i < keySize; i++) {
                innerDataBuffer->bytes()[i] ^= innerDataBuffer->bytes()[encryptedDataSize + paddedDataSize + ivSize + i];
            }

            bool ok = false;
            uint32_t offset = encryptedDataSize + paddedDataSize + ivSize + SHA256_DIGEST_LENGTH;
            size_t resLen = BN_bn2bin(rsaKey->n, innerDataBuffer->bytes() + offset);
            const auto shift = (256 - resLen);

            for (auto i = 0; i != 256; ++i) {
                const auto a = innerDataBuffer->bytes()[i];
                const auto b = (i < shift) ? 0 : innerDataBuffer->bytes()[offset + i - shift];
                if (a > b) {
                    break;
                } else if (a < b) {
                    ok = true;
                    break;
                }
            }
            if (ok) {
                break;
            }
        }

        if (bnContext == nullptr) {
            bnContext = BN_CTX_new();
        }
        BIGNUM *a = BN_bin2bn(innerDataBuffer->bytes(), encryptedDataSize, nullptr);
        BIGNUM *r = BN_new();
        BN_mod_exp(r, a, rsaKey->e, rsaKey->n, bnContext);
        uint32_t size = BN_num_bytes(r);
        auto rsaEncryptedData = new ByteArray(size >= 256 ? size : 256);
        BN_bn2bin(r, rsaEncryptedData->bytes + (size < 256 ? (256 - size) : 0));
        if (256 - size > 0) {
            memset(rsaEncryptedData->bytes, 0, 256 - size);
        }
        BN_free(a);
        BN_free(r);
        RSA_free(rsaKey);
        innerDataBuffer->reuse();

        request->encrypted_data = std::unique_ptr<ByteArray>(rsaEncryptedData);

        sendAckRequest(messageId);
        sendRequestData(request, true);
    } else {
        if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid client nonce, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
        beginHandshake(false);
    }
}

void Handshake::processHandshakeResponse_serverDHParams(TLObject *message, int64_t messageId) {
    const std::type_info &typeInfo = typeid(*message);
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
        Datacenter::aesIgeEncryption(result->encrypted_answer->bytes, tmpAesKeyAndIv->bytes(), tmpAesKeyAndIv->bytes() + 32, false, false, result->encrypted_answer->length);

        bool hashVerified = false;
        for (uint32_t i = 0; i < 16; i++) {
            SHA1(result->encrypted_answer->bytes + SHA_DIGEST_LENGTH, result->encrypted_answer->length - i - SHA_DIGEST_LENGTH, tmpAesKeyAndIv->bytes() + 64);
            if (!memcmp(tmpAesKeyAndIv->bytes() + 64, result->encrypted_answer->bytes, SHA_DIGEST_LENGTH)) {
                hashVerified = true;
                break;
            }
        }

        if (!hashVerified) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: can't decode DH params, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            return;
        }

        bool error = false;
        NativeByteBuffer *answerWithHash = new NativeByteBuffer(result->encrypted_answer->bytes + SHA_DIGEST_LENGTH, result->encrypted_answer->length - SHA_DIGEST_LENGTH);
        uint32_t constructor = answerWithHash->readUint32(&error);
        TL_server_DH_inner_data *dhInnerData = TL_server_DH_inner_data::TLdeserialize(answerWithHash, constructor, currentDatacenter->instanceNum, error);
        delete answerWithHash;

        if (error) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: can't parse decoded DH params, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            return;
        }

        if (!authNonce->isEqualTo(dhInnerData->nonce.get())) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH nonce, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            return;
        }

        if (!authServerNonce->isEqualTo(dhInnerData->server_nonce.get())) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH server nonce, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            return;
        }

        BIGNUM *p = BN_bin2bn(dhInnerData->dh_prime->bytes, dhInnerData->dh_prime->length, NULL);
        if (p == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("can't allocate BIGNUM p");
            exit(1);
        }
        if (!isGoodPrime(p, dhInnerData->g)) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: bad prime, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            BN_free(p);
            return;
        }

        BIGNUM *g_a = BN_new();
        if (g_a == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("can't allocate BIGNUM g_a");
            exit(1);
        }
        BN_bin2bn(dhInnerData->g_a->bytes, dhInnerData->g_a->length, g_a);
        if (!isGoodGaAndGb(g_a, p)) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: bad prime and g_a, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            BN_free(p);
            BN_free(g_a);
            return;
        }

        BIGNUM *g = BN_new();
        if (g == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("can't allocate BIGNUM g");
            exit(1);
        }
        if (!BN_set_word(g, dhInnerData->g)) {
            if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_set_word(g_b, dhInnerData->g)");
            beginHandshake(false);
            BN_free(g);
            BN_free(g_a);
            BN_free(p);
            return;
        }
        thread_local static uint8_t bytes[256];
        RAND_bytes(bytes, 256);
        BIGNUM *b = BN_bin2bn(bytes, 256, NULL);
        if (b == nullptr) {
            if (LOGS_ENABLED) DEBUG_E("can't allocate BIGNUM b");
            exit(1);
        }

        BIGNUM *g_b = BN_new();
        if (!BN_mod_exp(g_b, g, b, p, bnContext)) {
            if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_mod_exp(g_b, g, b, p, bnContext)");
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
        Datacenter::aesIgeEncryption(clientInnerDataBuffer->bytes(), tmpAesKeyAndIv->bytes(), tmpAesKeyAndIv->bytes() + 32, true, false, clientInnerDataBuffer->limit());
        setClientDhParams->encrypted_data = std::unique_ptr<ByteArray>(new ByteArray(clientInnerDataBuffer->bytes(), clientInnerDataBuffer->limit()));
        clientInnerDataBuffer->reuse();
        tmpAesKeyAndIv->reuse();

        sendAckRequest(messageId);
        sendRequestData(setClientDhParams, true);

        int32_t currentTime = (int32_t) (ConnectionsManager::getInstance(currentDatacenter->instanceNum).getCurrentTimeMillis() / 1000);
        timeDifference = dhInnerData->server_time - currentTime;

        handshakeServerSalt = new TL_future_salt();
        handshakeServerSalt->valid_since = currentTime + timeDifference - 5;
        handshakeServerSalt->valid_until = handshakeServerSalt->valid_since + 30 * 60;
        for (int32_t a = 7; a >= 0; a--) {
            handshakeServerSalt->salt <<= 8;
            handshakeServerSalt->salt |= (authNewNonce->bytes[a] ^ authServerNonce->bytes[a]);
        }
    } else {
        if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: can't set DH params, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
        beginHandshake(false);
    }
}

void Handshake::processHandshakeResponse_serverDHParamsAnswer(TLObject *message, int64_t messageId) {
    if (handshakeState != 3) {
        sendAckRequest(messageId);
        return;
    }
    const std::type_info &typeInfo = typeid(*message);

    handshakeState = 4;

    Set_client_DH_params_answer *result = (Set_client_DH_params_answer *) message;

    if (!authNonce->isEqualTo(result->nonce.get())) {
        if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH answer nonce, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
        beginHandshake(false);
        return;
    }
    if (!authServerNonce->isEqualTo(result->server_nonce.get())) {
        if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH answer server nonce, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
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
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH answer nonce hash 1, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            authKeyAuxHashBuffer->reuse();
            beginHandshake(false);
        } else {
            if (LOGS_ENABLED) DEBUG_D("account%u dc%u handshake: completed, time difference = %d, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, timeDifference, handshakeType);
            authKeyAuxHashBuffer->position(authNewNonce->length + 1 + 12);
            authKeyTempPendingId = authKeyAuxHashBuffer->readInt64(nullptr);
            authKeyAuxHashBuffer->reuse();

            if (handshakeRequest != nullptr) {
                delete handshakeRequest;
                handshakeRequest = nullptr;
            }

            std::unique_ptr<TL_future_salt> salt = std::unique_ptr<TL_future_salt>(handshakeServerSalt);
            currentDatacenter->clearServerSalts(handshakeType == HandshakeTypeMediaTemp);
            currentDatacenter->addServerSalt(salt, handshakeType == HandshakeTypeMediaTemp);
            handshakeServerSalt = nullptr;

            if (handshakeType == HandshakeTypePerm) {
                ConnectionsManager::getInstance(currentDatacenter->instanceNum).scheduleTask([&] {
                    ByteArray *authKey = handshakeAuthKey;
                    handshakeAuthKey = nullptr;
                    delegate->onHandshakeComplete(this, authKeyTempPendingId, authKey, timeDifference);
                });
            } else {
                authKeyTempPending = handshakeAuthKey;
                handshakeAuthKey = nullptr;

                Connection *connection = getConnection();

                TL_auth_bindTempAuthKey *request = new TL_auth_bindTempAuthKey();
                request->initFunc = [&, request, connection](int64_t messageId) {
                    TL_bind_auth_key_inner *inner = new TL_bind_auth_key_inner();
                    inner->expires_at = ConnectionsManager::getInstance(currentDatacenter->instanceNum).getCurrentTime() + timeDifference + TEMP_AUTH_KEY_EXPIRE_TIME;
                    inner->perm_auth_key_id = currentDatacenter->authKeyPermId;
                    inner->temp_auth_key_id = authKeyTempPendingId;
                    RAND_bytes((uint8_t *) &inner->nonce, 8);
                    inner->temp_session_id = connection->getSessionId();

                    NetworkMessage *networkMessage = new NetworkMessage();
                    networkMessage->message = std::make_unique<TL_message>();
                    networkMessage->message->msg_id = authKeyPendingMessageId = messageId;
                    networkMessage->message->bytes = inner->getObjectSize();
                    networkMessage->message->body = std::unique_ptr<TLObject>(inner);
                    networkMessage->message->seqno = 0;

                    std::vector<std::unique_ptr<NetworkMessage>> array;
                    array.push_back(std::unique_ptr<NetworkMessage>(networkMessage));

                    request->perm_auth_key_id = inner->perm_auth_key_id;
                    request->nonce = inner->nonce;
                    request->expires_at = inner->expires_at;
                    request->encrypted_message = currentDatacenter->createRequestsData(array, nullptr, connection, true);
                };

                authKeyPendingRequestId = ConnectionsManager::getInstance(currentDatacenter->instanceNum).sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime, int64_t msgId) {
                    authKeyPendingMessageId = 0;
                    authKeyPendingRequestId = 0;
                    if (response != nullptr && typeid(*response) == typeid(TL_boolTrue)) {
                        if (LOGS_ENABLED) DEBUG_D("account%u dc%u handshake: bind completed", currentDatacenter->instanceNum, currentDatacenter->datacenterId);
                        ConnectionsManager::getInstance(currentDatacenter->instanceNum).scheduleTask([&] {
                            ByteArray *authKey = authKeyTempPending;
                            authKeyTempPending = nullptr;
                            delegate->onHandshakeComplete(this, authKeyTempPendingId, authKey, timeDifference);
                        });
                    } else if (error == nullptr || error->code != 400 || error->text.find("ENCRYPTED_MESSAGE_INVALID") == std::string::npos) {
                        ConnectionsManager::getInstance(currentDatacenter->instanceNum).scheduleTask([&] {
                            beginHandshake(true);
                        });
                    }
                }, nullptr, nullptr, RequestFlagWithoutLogin | RequestFlagEnableUnauthorized | RequestFlagUseUnboundKey, currentDatacenter->datacenterId, connection->getConnectionType(), true, 0);
            }
        }
    } else if (typeInfo == typeid(TL_dh_gen_retry)) {
        authKeyAuxHashBuffer->writeByte(2);
        SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

        if (memcmp(result->new_nonce_hash2->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH answer nonce hash 2, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
        } else {
            if (LOGS_ENABLED) DEBUG_D("account%u dc%u handshake: retry DH, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
        }
        authKeyAuxHashBuffer->reuse();
    } else if (typeInfo == typeid(TL_dh_gen_fail)) {
        authKeyAuxHashBuffer->writeByte(3);
        SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

        if (memcmp(result->new_nonce_hash3->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: invalid DH answer nonce hash 3, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
        } else {
            if (LOGS_ENABLED) DEBUG_E("account%u dc%u handshake: server declined DH params, type = %d", currentDatacenter->instanceNum, currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
        }
        authKeyAuxHashBuffer->reuse();
    }
}

void Handshake::sendAckRequest(int64_t messageId) {
    auto msgsAck = new TL_msgs_ack();
    msgsAck->msg_ids.push_back(messageId);
    sendRequestData(msgsAck, false);
}

TLObject *Handshake::getCurrentHandshakeRequest() {
    return handshakeRequest;
}

void Handshake::saveCdnConfigInternal(NativeByteBuffer *buffer) {
    buffer->writeInt32(1);
    buffer->writeInt32((int32_t) cdnPublicKeys.size());
    for (auto & cdnPublicKey : cdnPublicKeys) {
        buffer->writeInt32(cdnPublicKey.first);
        buffer->writeString(cdnPublicKey.second);
        buffer->writeInt64(cdnPublicKeysFingerprints[cdnPublicKey.first]);
    }
}

void Handshake::saveCdnConfig(Datacenter *datacenter) {
    if (cdnConfig == nullptr) {
        cdnConfig = new Config(datacenter->instanceNum, "cdnkeys.dat");
    }
    thread_local static auto sizeCalculator = new NativeByteBuffer(true);
    sizeCalculator->clearCapacity();
    saveCdnConfigInternal(sizeCalculator);
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(sizeCalculator->capacity());
    saveCdnConfigInternal(buffer);
    cdnConfig->writeConfig(buffer);
    buffer->reuse();
}

void Handshake::loadCdnConfig(Datacenter *datacenter) {
    if (std::find(cdnWaitingDatacenters.begin(), cdnWaitingDatacenters.end(), datacenter) != cdnWaitingDatacenters.end()) {
        return;
    }
    cdnWaitingDatacenters.push_back(datacenter);
    if (loadingCdnKeys) {
        return;
    }
    if (LOGS_ENABLED) DEBUG_D("account%u dc%u loadCdnConfig", datacenter->instanceNum, datacenter->datacenterId);
//    if (cdnPublicKeysFingerprints.empty()) {
//        if (cdnConfig == nullptr) {
//            cdnConfig = new Config(datacenter->instanceNum, "cdnkeys.dat");
//        }
//        NativeByteBuffer *buffer = cdnConfig->readConfig();
//        if (buffer != nullptr) {
//            uint32_t version = buffer->readUint32(nullptr);
//            if (version >= 1) {
//                size_t count = buffer->readUint32(nullptr);
//                for (uint32_t a = 0; a < count; a++) {
//                    int dcId = buffer->readInt32(nullptr);
//                    cdnPublicKeys[dcId] = buffer->readString(nullptr);
//                    cdnPublicKeysFingerprints[dcId] = buffer->readUint64(nullptr);
//                }
//            }
//            buffer->reuse();
//            if (!cdnPublicKeysFingerprints.empty()) {
//                size_t count = cdnWaitingDatacenters.size();
//                for (uint32_t a = 0; a < count; a++) {
//                    cdnWaitingDatacenters[a]->beginHandshake(HandshakeTypeCurrent, false);
//                }
//                cdnWaitingDatacenters.clear();
//                return;
//            }
//        }
//    }
    loadingCdnKeys = true;
    auto request = new TL_help_getCdnConfig();

    ConnectionsManager::getInstance(datacenter->instanceNum).sendRequest(request, [&, datacenter](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime, int64_t msgId) {
        if (response != nullptr) {
            auto config = (TL_cdnConfig *) response;
            size_t count = config->public_keys.size();
            BIO *keyBio = BIO_new(BIO_s_mem());
            NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(1024);
            thread_local static uint8_t sha1Buffer[20];
            for (uint32_t a = 0; a < count; a++) {
                TL_cdnPublicKey *publicKey = config->public_keys[a].get();
                cdnPublicKeys[publicKey->dc_id] = publicKey->public_key;

                BIO_write(keyBio, publicKey->public_key.c_str(), (int) publicKey->public_key.length());
                RSA *rsaKey = PEM_read_bio_RSAPublicKey(keyBio, nullptr, nullptr, nullptr);

                int nBytes = BN_num_bytes(rsaKey->n);
                int eBytes = BN_num_bytes(rsaKey->e);
                std::string nStr(nBytes, 0), eStr(eBytes, 0);
                BN_bn2bin(rsaKey->n, (uint8_t *)&nStr[0]);
                BN_bn2bin(rsaKey->e, (uint8_t *)&eStr[0]);
                buffer->writeString(nStr);
                buffer->writeString(eStr);
                SHA1(buffer->bytes(), buffer->position(), sha1Buffer);
                cdnPublicKeysFingerprints[publicKey->dc_id] = ((uint64_t) sha1Buffer[19]) << 56 |
                                                              ((uint64_t) sha1Buffer[18]) << 48 |
                                                              ((uint64_t) sha1Buffer[17]) << 40 |
                                                              ((uint64_t) sha1Buffer[16]) << 32 |
                                                              ((uint64_t) sha1Buffer[15]) << 24 |
                                                              ((uint64_t) sha1Buffer[14]) << 16 |
                                                              ((uint64_t) sha1Buffer[13]) << 8 |
                                                              ((uint64_t) sha1Buffer[12]);
                RSA_free(rsaKey);
                if (a != count - 1) {
                    buffer->position(0);
                    BIO_reset(keyBio);
                }
            }
            buffer->reuse();
            BIO_free(keyBio);
            count = cdnWaitingDatacenters.size();
            if (LOGS_ENABLED) DEBUG_D("account%u dc%u cdnConfig loaded begin handshake", datacenter->instanceNum, datacenter->datacenterId);
            for (uint32_t a = 0; a < count; a++) {
                cdnWaitingDatacenters[a]->beginHandshake(HandshakeTypeCurrent, false);
            }
            cdnWaitingDatacenters.clear();
            saveCdnConfig(datacenter);
        }
        loadingCdnKeys = false;
    }, nullptr, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
}

HandshakeType Handshake::getType() {
    return handshakeType;
}

ByteArray *Handshake::getPendingAuthKey() {
    return authKeyTempPending;
}

int64_t Handshake::getPendingAuthKeyId() {
    return authKeyTempPendingId;
}

void Handshake::onHandshakeConnectionClosed() {
    if (handshakeState == 0) {
        return;
    }
    needResendData = true;
}

void Handshake::onHandshakeConnectionConnected() {
    if (handshakeState == 0 || !needResendData) {
        return;
    }
    beginHandshake(false);
}
