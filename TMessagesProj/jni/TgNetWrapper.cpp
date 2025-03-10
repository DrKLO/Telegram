#include <jni.h>
#include "tgnet/ApiScheme.h"
#include "tgnet/BuffersStorage.h"
#include "tgnet/NativeByteBuffer.h"
#include "tgnet/ConnectionsManager.h"
#include "tgnet/MTProtoScheme.h"
#include "tgnet/ConnectionSocket.h"
#include "tgnet/FileLog.h"
#include "tgnet/Handshake.h"
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/bn.h>
#include <openssl/pem.h>
#include <openssl/aes.h>

JavaVM *java;

jclass jclass_RequestTimeDelegate;
jmethodID jclass_RequestTimeDelegate_run;

jclass jclass_ConnectionsManager;
jmethodID jclass_ConnectionsManager_onRequestClear;
jmethodID jclass_ConnectionsManager_onRequestComplete;
jmethodID jclass_ConnectionsManager_onRequestQuickAck;
jmethodID jclass_ConnectionsManager_onRequestWriteToSocket;
jmethodID jclass_ConnectionsManager_onUnparsedMessageReceived;
jmethodID jclass_ConnectionsManager_onUpdate;
jmethodID jclass_ConnectionsManager_onSessionCreated;
jmethodID jclass_ConnectionsManager_onLogout;
jmethodID jclass_ConnectionsManager_onConnectionStateChanged;
jmethodID jclass_ConnectionsManager_onInternalPushReceived;
jmethodID jclass_ConnectionsManager_onUpdateConfig;
jmethodID jclass_ConnectionsManager_onBytesSent;
jmethodID jclass_ConnectionsManager_onBytesReceived;
jmethodID jclass_ConnectionsManager_onRequestNewServerIpAndPort;
jmethodID jclass_ConnectionsManager_onProxyError;
jmethodID jclass_ConnectionsManager_getHostByName;
jmethodID jclass_ConnectionsManager_getInitFlags;
jmethodID jclass_ConnectionsManager_onPremiumFloodWait;
jmethodID jclass_ConnectionsManager_onIntegrityCheckClassic;
jmethodID jclass_ConnectionsManager_onCaptchaCheck;

bool check_utf8(const char *data, size_t len);

jlong getFreeBuffer(JNIEnv *env, jclass c, jint length) {
    return (jlong) (intptr_t) BuffersStorage::getInstance().getFreeBuffer((uint32_t) length);
}

jint limit(JNIEnv *env, jclass c, jlong address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) (intptr_t) address;
    return buffer->limit();
}

jint position(JNIEnv *env, jclass c, jlong address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) (intptr_t) address;
    return buffer->position();
}

void reuse(JNIEnv *env, jclass c, jlong address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) (intptr_t) address;
    buffer->reuse();
}

jobject getJavaByteBuffer(JNIEnv *env, jclass c, jlong address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) (intptr_t) address;
    if (buffer == nullptr) {
        return nullptr;
    }
    return buffer->getJavaByteBuffer();
}

static const char *NativeByteBufferClassPathName = "org/telegram/tgnet/NativeByteBuffer";
static JNINativeMethod NativeByteBufferMethods[] = {
        {"native_getFreeBuffer", "(I)J", (void *) getFreeBuffer},
        {"native_limit", "(J)I", (void *) limit},
        {"native_position", "(J)I", (void *) position},
        {"native_reuse", "(J)V", (void *) reuse},
        {"native_getJavaByteBuffer", "(J)Ljava/nio/ByteBuffer;", (void *) getJavaByteBuffer}
};

jlong getCurrentTimeMillis(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).getCurrentTimeMillis() + ((jlong) ConnectionsManager::getInstance(instanceNum).getTimeDifference()) * 1000;
}

jint getCurrentTime(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).getCurrentTime();
}

jint getCurrentPingTime(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).getCurrentPingTime();
}

jint getCurrentDatacenterId(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).getCurrentDatacenterId();
}

jint isTestBackend(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).isTestBackend() ? 1 : 0;
}

jint getTimeDifference(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).getTimeDifference();
}

void sendRequest(JNIEnv *env, jclass c, jint instanceNum, jlong object, jint flags, jint datacenterId, jint connectionType, jboolean immediate, jint token) {
    TL_api_request *request = new TL_api_request();
    request->request = (NativeByteBuffer *) (intptr_t) object;
    ConnectionsManager::getInstance(instanceNum).sendRequest(request, ([instanceNum, token](TLObject *response, TL_error *error, int32_t networkType, int64_t responseTime, int64_t msgId, int32_t dcId) {
        TL_api_response *resp = (TL_api_response *) response;
        jlong ptr = 0;
        jint errorCode = 0;
        jstring errorText = nullptr;
        if (resp != nullptr) {
            ptr = (jlong) resp->response.get();
        } else if (error != nullptr) {
            errorCode = error->code;
            const char *text = error->text.c_str();
            size_t size = error->text.size();
            if (check_utf8(text, size)) {
                errorText = jniEnv[instanceNum]->NewStringUTF(text);
            } else {
                errorText = jniEnv[instanceNum]->NewStringUTF("UTF-8 ERROR");
            }
        }
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestComplete, instanceNum, token, ptr, errorCode, errorText, networkType, responseTime, msgId, dcId);
        if (errorText != nullptr) {
            jniEnv[instanceNum]->DeleteLocalRef(errorText);
        }
    }), ([instanceNum, token] {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestQuickAck, instanceNum, token);
    }), ([instanceNum, token] {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestWriteToSocket, instanceNum, token);
    }), ([instanceNum, token] {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestClear, instanceNum, token, false);
    }), (uint32_t) flags, (uint32_t) datacenterId, (ConnectionType) connectionType, immediate, token);
}

void cancelRequest(JNIEnv *env, jclass c, jint instanceNum, jint token, jboolean notifyServer) {
    return ConnectionsManager::getInstance(instanceNum).cancelRequest(token, notifyServer, ([instanceNum, token]() -> void {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestClear, instanceNum, token, true);
    }));
}

void failNotRunningRequest(JNIEnv *env, jclass c, jint instanceNum, jint token) {
    return ConnectionsManager::getInstance(instanceNum).failNotRunningRequest(token);
}

void receivedIntegrityCheckClassic(JNIEnv *env, jclass c, jint instanceNum, jint requestToken, jstring nonce, jstring token) {
    const char* nonceStr = env->GetStringUTFChars(nonce, 0);
    const char* tokenStr = env->GetStringUTFChars(token, 0);
    std::string nonceString = nonceStr;
    std::string tokenString = tokenStr;
    ConnectionsManager::getInstance(instanceNum).receivedIntegrityCheckClassic(requestToken, nonceString, tokenString);
    if (nonceStr != nullptr) {
        env->ReleaseStringUTFChars(nonce, nonceStr);
    }
    if (tokenStr != nullptr) {
        env->ReleaseStringUTFChars(token, tokenStr);
    }
}

void receivedCaptchaResult(JNIEnv *env, jclass c, jint instanceNum, jintArray requestTokens, jstring token) {
    const char* tokenStr = env->GetStringUTFChars(token, 0);
    jsize requestTokensLength = env->GetArrayLength(requestTokens);
    jint *requestTokensJArr = env->GetIntArrayElements(requestTokens, NULL);
    int* requestTokensArr = new int[requestTokensLength];
    for (int i = 0; i < requestTokensLength; ++i) {
        requestTokensArr[i] = requestTokensJArr[i];
    }
    if (requestTokensJArr != nullptr) {
        env->ReleaseIntArrayElements(requestTokens, requestTokensJArr, 0);
    }
    std::string tokenString = tokenStr;
    ConnectionsManager::getInstance(instanceNum).receivedCaptchaResult(requestTokensLength, requestTokensArr, tokenString);
    if (tokenStr != nullptr) {
        env->ReleaseStringUTFChars(token, tokenStr);
    }
}

jboolean isGoodPrime(JNIEnv *env, jclass c, jbyteArray prime, jint g) {
    jsize length = env->GetArrayLength(prime);
    jbyte *bytes = env->GetByteArrayElements(prime, NULL);
    if (bytes == NULL) {
        DEBUG_E("isGoodPrime: failed to get byte array");
        return false;
    }
    unsigned char *unsignedBytes = (unsigned char *)bytes;
    BIGNUM *bn = BN_bin2bn(unsignedBytes, length, NULL);
    if (bn == NULL) {
        env->ReleaseByteArrayElements(prime, bytes, 0);
        DEBUG_E("isGoodPrime: failed to convert byte array into BIGNUM");
        return false;
    }
    bool result = Handshake::isGoodPrime(bn, g);
    BN_free(bn);
    env->ReleaseByteArrayElements(prime, bytes, 0);
    return result;
}

void cleanUp(JNIEnv *env, jclass c, jint instanceNum, jboolean resetKeys) {
    return ConnectionsManager::getInstance(instanceNum).cleanUp(resetKeys, -1);
}

void cancelRequestsForGuid(JNIEnv *env, jclass c, jint instanceNum, jint guid) {
    return ConnectionsManager::getInstance(instanceNum).cancelRequestsForGuid(guid);
}

void bindRequestToGuid(JNIEnv *env, jclass c, jint instanceNum, jint requestToken, jint guid) {
    return ConnectionsManager::getInstance(instanceNum).bindRequestToGuid(requestToken, guid);
}

void applyDatacenterAddress(JNIEnv *env, jclass c, jint instanceNum, jint datacenterId, jstring ipAddress, jint port) {
    const char *valueStr = env->GetStringUTFChars(ipAddress, 0);

    ConnectionsManager::getInstance(instanceNum).applyDatacenterAddress((uint32_t) datacenterId, std::string(valueStr), (uint32_t) port);

    if (valueStr != 0) {
        env->ReleaseStringUTFChars(ipAddress, valueStr);
    }
}

void setProxySettings(JNIEnv *env, jclass c, jint instanceNum, jstring address, jint port, jstring username, jstring password, jstring secret) {
    const char *addressStr = env->GetStringUTFChars(address, 0);
    const char *usernameStr = env->GetStringUTFChars(username, 0);
    const char *passwordStr = env->GetStringUTFChars(password, 0);
    const char *secretStr = env->GetStringUTFChars(secret, 0);

    ConnectionsManager::getInstance(instanceNum).setProxySettings(addressStr, (uint16_t) port, usernameStr, passwordStr, secretStr);

    if (addressStr != 0) {
        env->ReleaseStringUTFChars(address, addressStr);
    }
    if (usernameStr != 0) {
        env->ReleaseStringUTFChars(username, usernameStr);
    }
    if (passwordStr != 0) {
        env->ReleaseStringUTFChars(password, passwordStr);
    }
    if (secretStr != 0) {
        env->ReleaseStringUTFChars(secret, secretStr);
    }
}

jint getConnectionState(JNIEnv *env, jclass c, jint instanceNum) {
    return ConnectionsManager::getInstance(instanceNum).getConnectionState();
}

void setUserId(JNIEnv *env, jclass c, jint instanceNum, int64_t id) {
    ConnectionsManager::getInstance(instanceNum).setUserId(id);
}

void setUserPremium(JNIEnv *env, jclass c, jint instanceNum, bool premium) {
    ConnectionsManager::getInstance(instanceNum).setUserPremium(premium);
}

void switchBackend(JNIEnv *env, jclass c, jint instanceNum, jboolean restart) {
    ConnectionsManager::getInstance(instanceNum).switchBackend(restart);
}

void pauseNetwork(JNIEnv *env, jclass c, jint instanceNum) {
    ConnectionsManager::getInstance(instanceNum).pauseNetwork();
}

void resumeNetwork(JNIEnv *env, jclass c, jint instanceNum, jboolean partial) {
    ConnectionsManager::getInstance(instanceNum).resumeNetwork(partial);
}

void updateDcSettings(JNIEnv *env, jclass c, jint instanceNum) {
    ConnectionsManager::getInstance(instanceNum).updateDcSettings(0, false, false);
}

void setIpStrategy(JNIEnv *env, jclass c, jint instanceNum, jbyte value) {
    ConnectionsManager::getInstance(instanceNum).setIpStrategy((uint8_t) value);
}

void setNetworkAvailable(JNIEnv *env, jclass c, jint instanceNum, jboolean value, jint networkType, jboolean slow) {
    ConnectionsManager::getInstance(instanceNum).setNetworkAvailable(value, networkType, slow);
}

void setPushConnectionEnabled(JNIEnv *env, jclass c, jint instanceNum, jboolean value) {
    ConnectionsManager::getInstance(instanceNum).setPushConnectionEnabled(value);
}

void applyDnsConfig(JNIEnv *env, jclass c, jint instanceNum, jlong address, jstring phone, jint date) {
    const char *phoneStr = env->GetStringUTFChars(phone, 0);

    ConnectionsManager::getInstance(instanceNum).applyDnsConfig((NativeByteBuffer *) (intptr_t) address, phoneStr, date);
    if (phoneStr != 0) {
        env->ReleaseStringUTFChars(phone, phoneStr);
    }
}

jlong checkProxy(JNIEnv *env, jclass c, jint instanceNum, jstring address, jint port, jstring username, jstring password, jstring secret, jobject requestTimeFunc) {
    const char *addressStr = env->GetStringUTFChars(address, 0);
    const char *usernameStr = env->GetStringUTFChars(username, 0);
    const char *passwordStr = env->GetStringUTFChars(password, 0);
    const char *secretStr = env->GetStringUTFChars(secret, 0);

    if (requestTimeFunc != nullptr) {
        DEBUG_REF("sendRequest requestTimeFunc");
        requestTimeFunc = env->NewGlobalRef(requestTimeFunc);
    }

    jlong result = ConnectionsManager::getInstance(instanceNum).checkProxy(addressStr, (uint16_t) port, usernameStr, passwordStr, secretStr, [instanceNum, requestTimeFunc](int64_t time) {
        if (requestTimeFunc != nullptr) {
            jniEnv[instanceNum]->CallVoidMethod(requestTimeFunc, jclass_RequestTimeDelegate_run, time);
        }
    }, requestTimeFunc);

    if (addressStr != 0) {
        env->ReleaseStringUTFChars(address, addressStr);
    }
    if (usernameStr != 0) {
        env->ReleaseStringUTFChars(username, usernameStr);
    }
    if (passwordStr != 0) {
        env->ReleaseStringUTFChars(password, passwordStr);
    }
    if (secretStr != 0) {
        env->ReleaseStringUTFChars(secret, secretStr);
    }

    return result;
}

class Delegate : public ConnectiosManagerDelegate {
    
    void onUpdate(int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onUpdate, instanceNum);
    }
    
    void onSessionCreated(int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onSessionCreated, instanceNum);
    }
    
    void onConnectionStateChanged(ConnectionState state, int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onConnectionStateChanged, state, instanceNum);
    }
    
    void onUnparsedMessageReceived(int64_t reqMessageId, NativeByteBuffer *buffer, ConnectionType connectionType, int32_t instanceNum) {
        if (connectionType == ConnectionTypeGeneric) {
            jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onUnparsedMessageReceived, (jlong) (intptr_t) buffer, instanceNum, reqMessageId);
        }
    }
    
    void onLogout(int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onLogout, instanceNum);
    }
    
    void onUpdateConfig(TL_config *config, int32_t instanceNum) {
        NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(config->getObjectSize());
        config->serializeToStream(buffer);
        buffer->position(0);
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onUpdateConfig, (jlong) (intptr_t) buffer, instanceNum);
        buffer->reuse();
    }
    
    void onInternalPushReceived(int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onInternalPushReceived, instanceNum);
    }

    void onBytesReceived(int32_t amount, int32_t networkType, int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onBytesReceived, amount, networkType, instanceNum);
    }

    void onBytesSent(int32_t amount, int32_t networkType, int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onBytesSent, amount, networkType, instanceNum);
    }

    void onRequestNewServerIpAndPort(int32_t second, int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestNewServerIpAndPort, second, instanceNum);
    }

    void onProxyError(int32_t instanceNum) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onProxyError);
    }

    void getHostByName(std::string domain, int32_t instanceNum, ConnectionSocket *socket) {
        jstring domainName = jniEnv[instanceNum]->NewStringUTF(domain.c_str());
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_getHostByName, domainName, (jlong) (intptr_t) socket);
        jniEnv[instanceNum]->DeleteLocalRef(domainName);
    }

    int32_t getInitFlags(int32_t instanceNum) {
        return (int32_t) jniEnv[instanceNum]->CallStaticIntMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_getInitFlags);
    }

    void onPremiumFloodWait(int32_t instanceNum, int32_t requestToken, bool isUpload) {
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onPremiumFloodWait, instanceNum, requestToken, isUpload);
    }

    void onIntegrityCheckClassic(int32_t instanceNum, int32_t requestToken, std::string project, std::string nonce) {
        jstring projectStr = jniEnv[instanceNum]->NewStringUTF(project.c_str());
        jstring nonceStr = jniEnv[instanceNum]->NewStringUTF(nonce.c_str());
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onIntegrityCheckClassic, instanceNum, requestToken, projectStr, nonceStr);
        jniEnv[instanceNum]->DeleteLocalRef(projectStr);
        jniEnv[instanceNum]->DeleteLocalRef(nonceStr);
    }

    void onCaptchaCheck(int32_t instanceNum, int32_t requestToken, std::string action, std::string key_id) {
        jstring actionStr = jniEnv[instanceNum]->NewStringUTF(action.c_str());
        jstring keyIdStr = jniEnv[instanceNum]->NewStringUTF(key_id.c_str());
        jniEnv[instanceNum]->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onCaptchaCheck, instanceNum, requestToken, actionStr, keyIdStr);
        jniEnv[instanceNum]->DeleteLocalRef(actionStr);
        jniEnv[instanceNum]->DeleteLocalRef(keyIdStr);
    }

};

void onHostNameResolved(JNIEnv *env, jclass c, jstring host, jlong address, jstring ip) {
    const char *ipStr = env->GetStringUTFChars(ip, 0);
    const char *hostStr = env->GetStringUTFChars(host, 0);
    std::string i = std::string(ipStr);
    std::string h = std::string(hostStr);
    if (ipStr != 0) {
        env->ReleaseStringUTFChars(ip, ipStr);
    }
    if (hostStr != 0) {
        env->ReleaseStringUTFChars(host, hostStr);
    }
    ConnectionSocket *socket = (ConnectionSocket *) (intptr_t) address;
    socket->onHostNameResolved(h, i, false);
}

void discardConnection(JNIEnv *env, jclass c,  jint instanceNum, jint datacenerId, jint connectionType) {
    ConnectionsManager::getInstance(instanceNum).reconnect(datacenerId, connectionType);
}

void setLangCode(JNIEnv *env, jclass c, jint instanceNum, jstring langCode) {
    const char *langCodeStr = env->GetStringUTFChars(langCode, 0);

    ConnectionsManager::getInstance(instanceNum).setLangCode(std::string(langCodeStr));

    if (langCodeStr != 0) {
        env->ReleaseStringUTFChars(langCode, langCodeStr);
    }
}

void setRegId(JNIEnv *env, jclass c, jint instanceNum, jstring regId) {
    const char *regIdStr = env->GetStringUTFChars(regId, 0);

    ConnectionsManager::getInstance(instanceNum).setRegId(std::string(regIdStr));

    if (regIdStr != 0) {
        env->ReleaseStringUTFChars(regId, regIdStr);
    }
}

void setSystemLangCode(JNIEnv *env, jclass c, jint instanceNum, jstring langCode) {
    const char *langCodeStr = env->GetStringUTFChars(langCode, 0);

    ConnectionsManager::getInstance(instanceNum).setSystemLangCode(std::string(langCodeStr));

    if (langCodeStr != 0) {
        env->ReleaseStringUTFChars(langCode, langCodeStr);
    }
}

void init(JNIEnv *env, jclass c, jint instanceNum, jint version, jint layer, jint apiId, jstring deviceModel, jstring systemVersion, jstring appVersion, jstring langCode, jstring systemLangCode, jstring configPath, jstring logPath, jstring regId, jstring cFingerprint, jstring installerId, jstring packageId, jint timezoneOffset, jlong userId, jboolean userPremium, jboolean enablePushConnection, jboolean hasNetwork, jint networkType, jint performanceClass) {
    const char *deviceModelStr = env->GetStringUTFChars(deviceModel, 0);
    const char *systemVersionStr = env->GetStringUTFChars(systemVersion, 0);
    const char *appVersionStr = env->GetStringUTFChars(appVersion, 0);
    const char *langCodeStr = env->GetStringUTFChars(langCode, 0);
    const char *systemLangCodeStr = env->GetStringUTFChars(systemLangCode, 0);
    const char *configPathStr = env->GetStringUTFChars(configPath, 0);
    const char *logPathStr = env->GetStringUTFChars(logPath, 0);
    const char *regIdStr = env->GetStringUTFChars(regId, 0);
    const char *cFingerprintStr = env->GetStringUTFChars(cFingerprint, 0);
    const char *installerIdStr = env->GetStringUTFChars(installerId, 0);
    const char *packageIdStr = env->GetStringUTFChars(packageId, 0);

    ConnectionsManager::getInstance(instanceNum).init((uint32_t) version, layer, apiId, std::string(deviceModelStr), std::string(systemVersionStr), std::string(appVersionStr), std::string(langCodeStr), std::string(systemLangCodeStr), std::string(configPathStr), std::string(logPathStr), std::string(regIdStr), std::string(cFingerprintStr), std::string(installerIdStr), std::string(packageIdStr), timezoneOffset, userId, userPremium, true, enablePushConnection, hasNetwork, networkType, performanceClass);

    if (deviceModelStr != 0) {
        env->ReleaseStringUTFChars(deviceModel, deviceModelStr);
    }
    if (systemVersionStr != 0) {
        env->ReleaseStringUTFChars(systemVersion, systemVersionStr);
    }
    if (appVersionStr != 0) {
        env->ReleaseStringUTFChars(appVersion, appVersionStr);
    }
    if (langCodeStr != 0) {
        env->ReleaseStringUTFChars(langCode, langCodeStr);
    }
    if (systemLangCodeStr != 0) {
        env->ReleaseStringUTFChars(systemLangCode, systemLangCodeStr);
    }
    if (configPathStr != 0) {
        env->ReleaseStringUTFChars(configPath, configPathStr);
    }
    if (logPathStr != 0) {
        env->ReleaseStringUTFChars(logPath, logPathStr);
    }
    if (regIdStr != 0) {
        env->ReleaseStringUTFChars(regId, regIdStr);
    }
    if (cFingerprintStr != 0) {
        env->ReleaseStringUTFChars(cFingerprint, cFingerprintStr);
    }
    if (installerIdStr != 0) {
        env->ReleaseStringUTFChars(installerId, installerIdStr);
    }
    if (packageIdStr != 0) {
        env->ReleaseStringUTFChars(packageId, packageIdStr);
    }
}

void setJava(JNIEnv *env, jclass c, jboolean useJavaByteBuffers) {
    ConnectionsManager::useJavaVM(java, useJavaByteBuffers);
    for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
        ConnectionsManager::getInstance(a).setDelegate(new Delegate());
    }
}

static const char *ConnectionsManagerClassPathName = "org/telegram/tgnet/ConnectionsManager";
static JNINativeMethod ConnectionsManagerMethods[] = {
        {"native_getCurrentTimeMillis", "(I)J", (void *) getCurrentTimeMillis},
        {"native_getCurrentTime", "(I)I", (void *) getCurrentTime},
        {"native_getCurrentPingTime", "(I)I", (void *) getCurrentPingTime},
        {"native_getCurrentDatacenterId", "(I)I", (void *) getCurrentDatacenterId},
        {"native_isTestBackend", "(I)I", (void *) isTestBackend},
        {"native_getTimeDifference", "(I)I", (void *) getTimeDifference},
        {"native_sendRequest", "(IJIIIZI)V", (void *) sendRequest},
        {"native_cancelRequest", "(IIZ)V", (void *) cancelRequest},
        {"native_cleanUp", "(IZ)V", (void *) cleanUp},
        {"native_cancelRequestsForGuid", "(II)V", (void *) cancelRequestsForGuid},
        {"native_bindRequestToGuid", "(III)V", (void *) bindRequestToGuid},
        {"native_applyDatacenterAddress", "(IILjava/lang/String;I)V", (void *) applyDatacenterAddress},
        {"native_setProxySettings", "(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", (void *) setProxySettings},
        {"native_getConnectionState", "(I)I", (void *) getConnectionState},
        {"native_setUserId", "(IJ)V", (void *) setUserId},
        {"native_init", "(IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJZZZII)V", (void *) init},
        {"native_setLangCode", "(ILjava/lang/String;)V", (void *) setLangCode},
        {"native_setRegId", "(ILjava/lang/String;)V", (void *) setRegId},
        {"native_setSystemLangCode", "(ILjava/lang/String;)V", (void *) setSystemLangCode},
        {"native_switchBackend", "(IZ)V", (void *) switchBackend},
        {"native_pauseNetwork", "(I)V", (void *) pauseNetwork},
        {"native_resumeNetwork", "(IZ)V", (void *) resumeNetwork},
        {"native_updateDcSettings", "(I)V", (void *) updateDcSettings},
        {"native_setIpStrategy", "(IB)V", (void *) setIpStrategy},
        {"native_setNetworkAvailable", "(IZIZ)V", (void *) setNetworkAvailable},
        {"native_setPushConnectionEnabled", "(IZ)V", (void *) setPushConnectionEnabled},
        {"native_setJava", "(Z)V", (void *) setJava},
        {"native_applyDnsConfig", "(IJLjava/lang/String;I)V", (void *) applyDnsConfig},
        {"native_checkProxy", "(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/telegram/tgnet/RequestTimeDelegate;)J", (void *) checkProxy},
        {"native_onHostNameResolved", "(Ljava/lang/String;JLjava/lang/String;)V", (void *) onHostNameResolved},
        {"native_discardConnection", "(III)V", (void *) discardConnection},
        {"native_failNotRunningRequest", "(II)V", (void *) failNotRunningRequest},
        {"native_receivedIntegrityCheckClassic", "(IILjava/lang/String;Ljava/lang/String;)V", (void *) receivedIntegrityCheckClassic},
        {"native_receivedCaptchaResult", "(I[ILjava/lang/String;)V", (void *) receivedCaptchaResult},
        {"native_isGoodPrime", "([BI)Z", (void *) isGoodPrime},
};

inline int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods, int methodsCount) {
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, methodsCount) < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" int registerNativeTgNetFunctions(JavaVM *vm, JNIEnv *env) {
    java = vm;
    
    if (!registerNativeMethods(env, NativeByteBufferClassPathName, NativeByteBufferMethods, sizeof(NativeByteBufferMethods) / sizeof(NativeByteBufferMethods[0]))) {
        return JNI_FALSE;
    }
    
    if (!registerNativeMethods(env, ConnectionsManagerClassPathName, ConnectionsManagerMethods, sizeof(ConnectionsManagerMethods) / sizeof(ConnectionsManagerMethods[0]))) {
        return JNI_FALSE;
    }

    DEBUG_REF("RequestTimeDelegate class");
    jclass_RequestTimeDelegate = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/RequestTimeDelegate"));
    if (jclass_RequestTimeDelegate == 0) {
        return JNI_FALSE;
    }
    jclass_RequestTimeDelegate_run = env->GetMethodID(jclass_RequestTimeDelegate, "run", "(J)V");
    if (jclass_RequestTimeDelegate_run == 0) {
        return JNI_FALSE;
    }

    DEBUG_REF("ConnectionsManager class");
    jclass_ConnectionsManager = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/ConnectionsManager"));
    if (jclass_ConnectionsManager == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onRequestClear = env->GetStaticMethodID(jclass_ConnectionsManager, "onRequestClear", "(IIZ)V");
    if (jclass_ConnectionsManager_onRequestClear == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onRequestComplete = env->GetStaticMethodID(jclass_ConnectionsManager, "onRequestComplete", "(IIJILjava/lang/String;IJJI)V");
    if (jclass_ConnectionsManager_onRequestComplete == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onRequestWriteToSocket = env->GetStaticMethodID(jclass_ConnectionsManager, "onRequestWriteToSocket", "(II)V");
    if (jclass_ConnectionsManager_onRequestWriteToSocket == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onRequestQuickAck = env->GetStaticMethodID(jclass_ConnectionsManager, "onRequestQuickAck", "(II)V");
    if (jclass_ConnectionsManager_onRequestQuickAck == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onUnparsedMessageReceived = env->GetStaticMethodID(jclass_ConnectionsManager, "onUnparsedMessageReceived", "(JIJ)V");
    if (jclass_ConnectionsManager_onUnparsedMessageReceived == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onUpdate = env->GetStaticMethodID(jclass_ConnectionsManager, "onUpdate", "(I)V");
    if (jclass_ConnectionsManager_onUpdate == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onSessionCreated = env->GetStaticMethodID(jclass_ConnectionsManager, "onSessionCreated", "(I)V");
    if (jclass_ConnectionsManager_onSessionCreated == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onLogout = env->GetStaticMethodID(jclass_ConnectionsManager, "onLogout", "(I)V");
    if (jclass_ConnectionsManager_onLogout == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onConnectionStateChanged = env->GetStaticMethodID(jclass_ConnectionsManager, "onConnectionStateChanged", "(II)V");
    if (jclass_ConnectionsManager_onConnectionStateChanged == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onInternalPushReceived = env->GetStaticMethodID(jclass_ConnectionsManager, "onInternalPushReceived", "(I)V");
    if (jclass_ConnectionsManager_onInternalPushReceived == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onUpdateConfig = env->GetStaticMethodID(jclass_ConnectionsManager, "onUpdateConfig", "(JI)V");
    if (jclass_ConnectionsManager_onUpdateConfig == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onBytesSent = env->GetStaticMethodID(jclass_ConnectionsManager, "onBytesSent", "(III)V");
    if (jclass_ConnectionsManager_onBytesSent == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onBytesReceived = env->GetStaticMethodID(jclass_ConnectionsManager, "onBytesReceived", "(III)V");
    if (jclass_ConnectionsManager_onBytesReceived == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onRequestNewServerIpAndPort = env->GetStaticMethodID(jclass_ConnectionsManager, "onRequestNewServerIpAndPort", "(II)V");
    if (jclass_ConnectionsManager_onRequestNewServerIpAndPort == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onProxyError = env->GetStaticMethodID(jclass_ConnectionsManager, "onProxyError", "()V");
    if (jclass_ConnectionsManager_onProxyError == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_getHostByName = env->GetStaticMethodID(jclass_ConnectionsManager, "getHostByName", "(Ljava/lang/String;J)V");
    if (jclass_ConnectionsManager_getHostByName == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_getInitFlags = env->GetStaticMethodID(jclass_ConnectionsManager, "getInitFlags", "()I");
    if (jclass_ConnectionsManager_getInitFlags == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onPremiumFloodWait = env->GetStaticMethodID(jclass_ConnectionsManager, "onPremiumFloodWait", "(IIZ)V");
    if (jclass_ConnectionsManager_onPremiumFloodWait == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onIntegrityCheckClassic = env->GetStaticMethodID(jclass_ConnectionsManager, "onIntegrityCheckClassic", "(IILjava/lang/String;Ljava/lang/String;)V");
    if (jclass_ConnectionsManager_onIntegrityCheckClassic == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onCaptchaCheck = env->GetStaticMethodID(jclass_ConnectionsManager, "onCaptchaCheck", "(IILjava/lang/String;Ljava/lang/String;)V");
    if (jclass_ConnectionsManager_onCaptchaCheck == 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2018
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//

bool check_utf8(const char *data, size_t len) {
    const char *data_end = data + len;
    do {
        unsigned int a = (unsigned char) (*data++);
        if ((a & 0x80) == 0) {
            if (data == data_end + 1) {
                return true;
            }
            continue;
        }

#define ENSURE(condition) \
if (!(condition)) {       \
    return false;             \
}

        ENSURE((a & 0x40) != 0);

        unsigned int b = (unsigned char) (*data++);
        ENSURE((b & 0xc0) == 0x80);
        if ((a & 0x20) == 0) {
            ENSURE((a & 0x1e) > 0);
            continue;
        }

        unsigned int c = (unsigned char) (*data++);
        ENSURE((c & 0xc0) == 0x80);
        if ((a & 0x10) == 0) {
            int x = (((a & 0x0f) << 6) | (b & 0x20));
            ENSURE(x != 0 && x != 0x360);
            continue;
        }

        unsigned int d = (unsigned char) (*data++);
        ENSURE((d & 0xc0) == 0x80);
        if ((a & 0x08) == 0) {
            int t = (((a & 0x07) << 6) | (b & 0x30));
            ENSURE(0 < t && t < 0x110);
            continue;
        }

        return false;
#undef ENSURE
    } while (1);
}
