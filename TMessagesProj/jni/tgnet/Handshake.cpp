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
    if (LOGS_ENABLED) DEBUG_D("dc%u handshake: begin, type = %d", currentDatacenter->datacenterId, handshakeType);
    cleanupHandshake();
    Connection *connection = getConnection();
    handshakeState = 1;

    if (reconnect) {
        connection->suspendConnection();
        connection->connect();
    }

#ifdef USE_OLD_KEYS
    TL_req_pq *request = new TL_req_pq();
    request->nonce = std::unique_ptr<ByteArray>(new ByteArray(16));
    RAND_bytes(request->nonce->bytes, 16);
    authNonce = new ByteArray(request->nonce.get());
    sendRequestData(request, true);
#else
    TL_req_pq_multi *request = new TL_req_pq_multi();
    request->nonce = std::unique_ptr<ByteArray>(new ByteArray(16));
    RAND_bytes(request->nonce->bytes, 16);
    authNonce = new ByteArray(request->nonce.get());
    sendRequestData(request, true);
#endif
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
        ConnectionsManager::getInstance(currentDatacenter->instanceNum).cancelRequestInternal(authKeyPendingRequestId, authKeyPendingMessageId, false, false);
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
        if (LOGS_ENABLED) DEBUG_E("factorization failed for %" PRIu64, what);
        p = 0;
        q = 0;
        return false;
    }
}

inline bool check_prime(BIGNUM *p) {
    int result = 0;
    if (!BN_primality_test(&result, p, BN_prime_checks, bnContext, 0, NULL)) {
        if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_primality_test");
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
        if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_set_word(dh_g, 4 * g)");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }
    if (!BN_mod(t, p, dh_g, bnContext)) {
        if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_mod");
        BN_free(t);
        BN_free(dh_g);
        return false;
    }
    uint64_t x = BN_get_word(t);
    if (x >= 4 * g) {
        if (LOGS_ENABLED) DEBUG_E("OpenSSL error at BN_get_word");
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

void Handshake::processHandshakeResponse(TLObject *message, int64_t messageId) {
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
            std::string key;
            int64_t keyFingerprint = 0;

            size_t count1 = result->server_public_key_fingerprints.size();
            if (currentDatacenter->isCdnDatacenter) {
                std::map<int32_t, uint64_t>::iterator iter = cdnPublicKeysFingerprints.find(currentDatacenter->datacenterId);
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
#ifdef USE_OLD_KEYS
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
#endif

                    serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                                       "MIIBCgKCAQEAruw2yP/BCcsJliRoW5eBVBVle9dtjJw+OYED160Wybum9SXtBBLX\n"
                                                       "riwt4rROd9csv0t0OHCaTmRqBcQ0J8fxhN6/cpR1GWgOZRUAiQxoMnlt0R93LCX/\n"
                                                       "j1dnVa/gVbCjdSxpbrfY2g2L4frzjJvdl84Kd9ORYjDEAyFnEA7dD556OptgLQQ2\n"
                                                       "e2iVNq8NZLYTzLp5YpOdO1doK+ttrltggTCy5SrKeLoCPPbOgGsdxJxyz5KKcZnS\n"
                                                       "Lj16yE5HvJQn0CNpRdENvRUXe6tBP78O39oJ8BTHp9oIjd6XWXAsp2CvK45Ol8wF\n"
                                                       "XGF710w9lwCGNbmNxNYhtIkdqfsEcwR5JwIDAQAB\n"
                                                       "-----END RSA PUBLIC KEY-----");
                    serverPublicKeysFingerprints.push_back(0xbc35f3509f7b7a5LL);

                    serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                                       "MIIBCgKCAQEAvfLHfYH2r9R70w8prHblWt/nDkh+XkgpflqQVcnAfSuTtO05lNPs\n"
                                                       "pQmL8Y2XjVT4t8cT6xAkdgfmmvnvRPOOKPi0OfJXoRVylFzAQG/j83u5K3kRLbae\n"
                                                       "7fLccVhKZhY46lvsueI1hQdLgNV9n1cQ3TDS2pQOCtovG4eDl9wacrXOJTG2990V\n"
                                                       "jgnIKNA0UMoP+KF03qzryqIt3oTvZq03DyWdGK+AZjgBLaDKSnC6qD2cFY81UryR\n"
                                                       "WOab8zKkWAnhw2kFpcqhI0jdV5QaSCExvnsjVaX0Y1N0870931/5Jb9ICe4nweZ9\n"
                                                       "kSDF/gip3kWLG0o8XQpChDfyvsqB9OLV/wIDAQAB\n"
                                                       "-----END RSA PUBLIC KEY-----");
                    serverPublicKeysFingerprints.push_back(0x15ae5fa8b5529542LL);

                    serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                                       "MIIBCgKCAQEAs/ditzm+mPND6xkhzwFIz6J/968CtkcSE/7Z2qAJiXbmZ3UDJPGr\n"
                                                       "zqTDHkO30R8VeRM/Kz2f4nR05GIFiITl4bEjvpy7xqRDspJcCFIOcyXm8abVDhF+\n"
                                                       "th6knSU0yLtNKuQVP6voMrnt9MV1X92LGZQLgdHZbPQz0Z5qIpaKhdyA8DEvWWvS\n"
                                                       "Uwwc+yi1/gGaybwlzZwqXYoPOhwMebzKUk0xW14htcJrRrq+PXXQbRzTMynseCoP\n"
                                                       "Ioke0dtCodbA3qQxQovE16q9zz4Otv2k4j63cz53J+mhkVWAeWxVGI0lltJmWtEY\n"
                                                       "K6er8VqqWot3nqmWMXogrgRLggv/NbbooQIDAQAB\n"
                                                       "-----END RSA PUBLIC KEY-----");
                    serverPublicKeysFingerprints.push_back(0xaeae98e13cd7f94fLL);

                    serverPublicKeys.push_back("-----BEGIN RSA PUBLIC KEY-----\n"
                                                       "MIIBCgKCAQEAvmpxVY7ld/8DAjz6F6q05shjg8/4p6047bn6/m8yPy1RBsvIyvuD\n"
                                                       "uGnP/RzPEhzXQ9UJ5Ynmh2XJZgHoE9xbnfxL5BXHplJhMtADXKM9bWB11PU1Eioc\n"
                                                       "3+AXBB8QiNFBn2XI5UkO5hPhbb9mJpjA9Uhw8EdfqJP8QetVsI/xrCEbwEXe0xvi\n"
                                                       "fRLJbY08/Gp66KpQvy7g8w7VB8wlgePexW3pT13Ap6vuC+mQuJPyiHvSxjEKHgqe\n"
                                                       "Pji9NP3tJUFQjcECqcm0yV7/2d0t/pbCm+ZH1sadZspQCEPPrtbkQBlvHb4OLiIW\n"
                                                       "PGHKSMeRFvp3IWcmdJqXahxLCUS1Eh6MAQIDAQAB\n"
                                                       "-----END RSA PUBLIC KEY-----");
                    serverPublicKeysFingerprints.push_back(0x5a181b2235057d98LL);
                }

                size_t count2 = serverPublicKeysFingerprints.size();
                for (uint32_t a = 0; a < count1; a++) {
                    for (uint32_t b = 0; b < count2; b++) {
                        if ((uint64_t) result->server_public_key_fingerprints[a] == serverPublicKeysFingerprints[b]) {
                            keyFingerprint = result->server_public_key_fingerprints[a];
                            key = serverPublicKeys[a];
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
                    if (LOGS_ENABLED) DEBUG_D("dc%u handshake: can't find valid cdn server public key, type = %d", currentDatacenter->datacenterId, handshakeType);
                    loadCdnConfig(currentDatacenter);
                } else {
                    if (LOGS_ENABLED) DEBUG_E("dc%u handshake: can't find valid server public key, type = %d", currentDatacenter->datacenterId, handshakeType);
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
            request->public_key_fingerprint = keyFingerprint;

            TLObject *innerData;
            if (handshakeType == HandshakeTypePerm) {
                TL_p_q_inner_data_dc *tl_p_q_inner_data = new TL_p_q_inner_data_dc();
                tl_p_q_inner_data->nonce = std::unique_ptr<ByteArray>(new ByteArray(authNonce));
                tl_p_q_inner_data->server_nonce = std::unique_ptr<ByteArray>(new ByteArray(authServerNonce));
                tl_p_q_inner_data->pq = std::unique_ptr<ByteArray>(new ByteArray(result->pq.get()));
                tl_p_q_inner_data->p = std::unique_ptr<ByteArray>(new ByteArray(request->p.get()));
                tl_p_q_inner_data->q = std::unique_ptr<ByteArray>(new ByteArray(request->q.get()));
                tl_p_q_inner_data->new_nonce = std::unique_ptr<ByteArray>(new ByteArray(32));
                if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                    tl_p_q_inner_data->dc = 10000 + currentDatacenter->datacenterId;
                } else {
                    tl_p_q_inner_data->dc = currentDatacenter->datacenterId;
                }
                RAND_bytes(tl_p_q_inner_data->new_nonce->bytes, 32);
                authNewNonce = new ByteArray(tl_p_q_inner_data->new_nonce.get());
                innerData = tl_p_q_inner_data;
            } else {
                TL_p_q_inner_data_temp_dc *tl_p_q_inner_data_temp = new TL_p_q_inner_data_temp_dc();
                tl_p_q_inner_data_temp->nonce = std::unique_ptr<ByteArray>(new ByteArray(authNonce));
                tl_p_q_inner_data_temp->server_nonce = std::unique_ptr<ByteArray>(new ByteArray(authServerNonce));
                tl_p_q_inner_data_temp->pq = std::unique_ptr<ByteArray>(new ByteArray(result->pq.get()));
                tl_p_q_inner_data_temp->p = std::unique_ptr<ByteArray>(new ByteArray(request->p.get()));
                tl_p_q_inner_data_temp->q = std::unique_ptr<ByteArray>(new ByteArray(request->q.get()));
                tl_p_q_inner_data_temp->new_nonce = std::unique_ptr<ByteArray>(new ByteArray(32));
                /*if (handshakeType == HandshakeTypeMediaTemp) {
                    if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                        tl_p_q_inner_data_temp->dc = -(10000 + currentDatacenter->datacenterId);
                    } else {
                        tl_p_q_inner_data_temp->dc = -currentDatacenter->datacenterId;
                    }
                } else {*/
                    if (ConnectionsManager::getInstance(currentDatacenter->instanceNum).testBackend) {
                        tl_p_q_inner_data_temp->dc = 10000 + currentDatacenter->datacenterId;
                    } else {
                        tl_p_q_inner_data_temp->dc = currentDatacenter->datacenterId;
                    }
                //}
                tl_p_q_inner_data_temp->expires_in = TEMP_AUTH_KEY_EXPIRE_TIME;
                RAND_bytes(tl_p_q_inner_data_temp->new_nonce->bytes, 32);
                authNewNonce = new ByteArray(tl_p_q_inner_data_temp->new_nonce.get());
                innerData = tl_p_q_inner_data_temp;
            }

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
                memset(rsaEncryptedData->bytes + resLen, 0, 256 - resLen);
            }
            BN_free(a);
            BN_free(r);
            RSA_free(rsaKey);
            innerDataBuffer->reuse();

            request->encrypted_data = std::unique_ptr<ByteArray>(rsaEncryptedData);

            sendAckRequest(messageId);
            sendRequestData(request, true);
        } else {
            if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid client nonce, type = %d", currentDatacenter->datacenterId, handshakeType);
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
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: can't decode DH params, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
                return;
            }

            bool error = false;
            NativeByteBuffer *answerWithHash = new NativeByteBuffer(result->encrypted_answer->bytes + SHA_DIGEST_LENGTH, result->encrypted_answer->length - SHA_DIGEST_LENGTH);
            uint32_t constructor = answerWithHash->readUint32(&error);
            TL_server_DH_inner_data *dhInnerData = TL_server_DH_inner_data::TLdeserialize(answerWithHash, constructor, currentDatacenter->instanceNum, error);
            delete answerWithHash;

            if (error) {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: can't parse decoded DH params, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
                return;
            }

            if (!authNonce->isEqualTo(dhInnerData->nonce.get())) {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH nonce, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
                return;
            }

            if (!authServerNonce->isEqualTo(dhInnerData->server_nonce.get())) {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH server nonce, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
                return;
            }

            BIGNUM *p = BN_bin2bn(dhInnerData->dh_prime->bytes, dhInnerData->dh_prime->length, NULL);
            if (p == nullptr) {
                if (LOGS_ENABLED) DEBUG_E("can't allocate BIGNUM p");
                exit(1);
            }
            if (!isGoodPrime(p, dhInnerData->g)) {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: bad prime, type = %d", currentDatacenter->datacenterId, handshakeType);
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
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: bad prime and g_a, type = %d", currentDatacenter->datacenterId, handshakeType);
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
            if (LOGS_ENABLED) DEBUG_E("dc%u handshake: can't set DH params, type = %d", currentDatacenter->datacenterId, handshakeType);
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
            if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH answer nonce, type = %d", currentDatacenter->datacenterId, handshakeType);
            beginHandshake(false);
            return;
        }
        if (!authServerNonce->isEqualTo(result->server_nonce.get())) {
            if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH answer server nonce, type = %d", currentDatacenter->datacenterId, handshakeType);
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
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH answer nonce hash 1, type = %d", currentDatacenter->datacenterId, handshakeType);
                authKeyAuxHashBuffer->reuse();
                beginHandshake(false);
            } else {
                if (LOGS_ENABLED) DEBUG_D("dc%u handshake: completed, time difference = %d, type = %d", currentDatacenter->datacenterId, timeDifference, handshakeType);
                authKeyAuxHashBuffer->position(authNewNonce->length + 1 + 12);
                authKeyTempPendingId = authKeyAuxHashBuffer->readInt64(nullptr);
                authKeyAuxHashBuffer->reuse();

                if (handshakeRequest != nullptr) {
                    delete handshakeRequest;
                    handshakeRequest = nullptr;
                }

                std::unique_ptr<TL_future_salt> salt = std::unique_ptr<TL_future_salt>(handshakeServerSalt);
                currentDatacenter->addServerSalt(salt);
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
                        networkMessage->message = std::unique_ptr<TL_message>(new TL_message());
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

                    authKeyPendingRequestId = ConnectionsManager::getInstance(currentDatacenter->instanceNum).sendRequest(request, [&](TLObject *response, TL_error *error, int32_t networkType) {
                        authKeyPendingMessageId = 0;
                        authKeyPendingRequestId = 0;
                        if (response != nullptr && typeid(*response) == typeid(TL_boolTrue)) {
                            if (LOGS_ENABLED) DEBUG_D("dc%u handshake: bind completed", currentDatacenter->datacenterId);
                            ConnectionsManager::getInstance(currentDatacenter->instanceNum).scheduleTask([&] {
                                ByteArray *authKey = authKeyTempPending;
                                authKeyTempPending = nullptr;
                                delegate->onHandshakeComplete(this, authKeyTempPendingId, authKey, timeDifference);
                            });
                        } else {
                            ConnectionsManager::getInstance(currentDatacenter->instanceNum).scheduleTask([&] {
                                beginHandshake(true);
                            });
                        }
                    }, nullptr, RequestFlagWithoutLogin | RequestFlagEnableUnauthorized | RequestFlagUseUnboundKey, currentDatacenter->datacenterId, connection->getConnectionType(), true, 0);
                }
            }
        } else if (typeInfo == typeid(TL_dh_gen_retry)) {
            authKeyAuxHashBuffer->writeByte(2);
            SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

            if (memcmp(result->new_nonce_hash2->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH answer nonce hash 2, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
            } else {
                if (LOGS_ENABLED) DEBUG_D("dc%u handshake: retry DH, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
            }
            authKeyAuxHashBuffer->reuse();
        } else if (typeInfo == typeid(TL_dh_gen_fail)) {
            authKeyAuxHashBuffer->writeByte(3);
            SHA1(authKeyAuxHashBuffer->bytes(), authKeyAuxHashLength - 12, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength);

            if (memcmp(result->new_nonce_hash3->bytes, authKeyAuxHashBuffer->bytes() + authKeyAuxHashLength + SHA_DIGEST_LENGTH - 16, 16)) {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: invalid DH answer nonce hash 3, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
            } else {
                if (LOGS_ENABLED) DEBUG_E("dc%u handshake: server declined DH params, type = %d", currentDatacenter->datacenterId, handshakeType);
                beginHandshake(false);
            }
            authKeyAuxHashBuffer->reuse();
        }
    }
}

void Handshake::sendAckRequest(int64_t messageId) {
    TL_msgs_ack *msgsAck = new TL_msgs_ack();
    msgsAck->msg_ids.push_back(messageId);
    sendRequestData(msgsAck, false);
}

TLObject *Handshake::getCurrentHandshakeRequest() {
    return handshakeRequest;
}

void Handshake::saveCdnConfigInternal(NativeByteBuffer *buffer) {
    buffer->writeInt32(1);
    buffer->writeInt32((int32_t) cdnPublicKeys.size());
    for (std::map<int32_t, std::string>::iterator iter = cdnPublicKeys.begin(); iter != cdnPublicKeys.end(); iter++) {
        buffer->writeInt32(iter->first);
        buffer->writeString(iter->second);
        buffer->writeInt64(cdnPublicKeysFingerprints[iter->first]);
    }
}

void Handshake::saveCdnConfig(Datacenter *datacenter) {
    if (cdnConfig == nullptr) {
        cdnConfig = new Config(datacenter->instanceNum, "cdnkeys.dat");
    }
    thread_local static NativeByteBuffer *sizeCalculator = new NativeByteBuffer(true);
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
    if (cdnPublicKeysFingerprints.empty()) {
        if (cdnConfig == nullptr) {
            cdnConfig = new Config(datacenter->instanceNum, "cdnkeys.dat");
        }
        NativeByteBuffer *buffer = cdnConfig->readConfig();
        if (buffer != nullptr) {
            uint32_t version = buffer->readUint32(nullptr);
            if (version >= 1) {
                size_t count = buffer->readUint32(nullptr);
                for (uint32_t a = 0; a < count; a++) {
                    int dcId = buffer->readInt32(nullptr);
                    cdnPublicKeys[dcId] = buffer->readString(nullptr);
                    cdnPublicKeysFingerprints[dcId] = buffer->readUint64(nullptr);
                }
            }
            buffer->reuse();
            if (!cdnPublicKeysFingerprints.empty()) {
                size_t count = cdnWaitingDatacenters.size();
                for (uint32_t a = 0; a < count; a++) {
                    cdnWaitingDatacenters[a]->beginHandshake(HandshakeTypeCurrent, false);
                }
                cdnWaitingDatacenters.clear();
                return;
            }
        }
    }
    loadingCdnKeys = true;
    TL_help_getCdnConfig *request = new TL_help_getCdnConfig();

    ConnectionsManager::getInstance(datacenter->instanceNum).sendRequest(request, [&, datacenter](TLObject *response, TL_error *error, int32_t networkType) {
        if (response != nullptr) {
            TL_cdnConfig *config = (TL_cdnConfig *) response;
            size_t count = config->public_keys.size();
            BIO *keyBio = BIO_new(BIO_s_mem());
            NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(1024);
            thread_local static uint8_t sha1Buffer[20];
            for (uint32_t a = 0; a < count; a++) {
                TL_cdnPublicKey *publicKey = config->public_keys[a].get();
                cdnPublicKeys[publicKey->dc_id] = publicKey->public_key;

                BIO_write(keyBio, publicKey->public_key.c_str(), (int) publicKey->public_key.length());
                RSA *rsaKey = PEM_read_bio_RSAPublicKey(keyBio, NULL, NULL, NULL);

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
            for (uint32_t a = 0; a < count; a++) {
                cdnWaitingDatacenters[a]->beginHandshake(HandshakeTypeCurrent, false);
            }
            cdnWaitingDatacenters.clear();
            saveCdnConfig(datacenter);
        }
        loadingCdnKeys = false;
    }, nullptr, RequestFlagEnableUnauthorized | RequestFlagWithoutLogin, DEFAULT_DATACENTER_ID, ConnectionTypeGeneric, true);
}

HandshakeType Handshake::getType() {
    return handshakeType;
}

ByteArray *Handshake::getAuthKeyTempPending() {
    return authKeyTempPending;
}

int64_t Handshake::getAuthKeyTempPendingId() {
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
