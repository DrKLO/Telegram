#include <jni.h>
#include <TgVoip.h>
#include <os/android/AudioOutputOpenSLES.h>
#include <os/android/AudioInputOpenSLES.h>
#include <os/android/JNIUtilities.h>
#include "org_telegram_messenger_voip_TgVoip.h"

using namespace tgvoip;

extern "C" int tgvoipOnJniLoad(JavaVM *vm, JNIEnv *env) {
    return JNI_TRUE;
}

#pragma mark - Helpers

class JavaObject {
private:
    JNIEnv *env;
    jobject obj;
    jclass clazz;

public:
    JavaObject(JNIEnv *env, jobject obj) : JavaObject(env, obj, env->GetObjectClass(obj)) {
    }

    JavaObject(JNIEnv *env, jobject obj, jclass clazz) {
        this->env = env;
        this->obj = obj;
        this->clazz = clazz;
    }

    jint getIntField(const char *name) {
        return env->GetIntField(obj, env->GetFieldID(clazz, name, "I"));
    }

    jlong getLongField(const char *name) {
        return env->GetLongField(obj, env->GetFieldID(clazz, name, "J"));
    }

    jboolean getBooleanField(const char *name) {
        return env->GetBooleanField(obj, env->GetFieldID(clazz, name, "Z"));
    }

    jdouble getDoubleField(const char *name) {
        return env->GetDoubleField(obj, env->GetFieldID(clazz, name, "D"));
    }

    jbyteArray getByteArrayField(const char *name) {
        return (jbyteArray) env->GetObjectField(obj, env->GetFieldID(clazz, name, "[B"));
    }

    jstring getStringField(const char *name) {
        return (jstring) env->GetObjectField(obj, env->GetFieldID(clazz, name, "Ljava/lang/String;"));
    }
};

struct InstanceHolder {
    TgVoip *nativeInstance;
    jobject javaInstance;
};

jlong getInstanceHolderId(JNIEnv *env, jobject obj) {
    return env->GetLongField(obj, env->GetFieldID(env->GetObjectClass(obj), "nativeInstanceId", "J"));
}

InstanceHolder *getInstanceHolder(JNIEnv *env, jobject obj) {
    return reinterpret_cast<InstanceHolder *>(getInstanceHolderId(env, obj));
}

TgVoip *getTgVoip(JNIEnv *env, jobject obj) {
    return getInstanceHolder(env, obj)->nativeInstance;
}

jint throwNewJavaException(JNIEnv *env, const char *className, const char *message) {
    return env->ThrowNew(env->FindClass(className), message);
}

jint throwNewJavaIllegalArgumentException(JNIEnv *env, const char *message) {
    return throwNewJavaException(env, "java/lang/IllegalStateException", message);
}

jbyteArray copyVectorToJavaByteArray(JNIEnv *env, const std::vector<uint8_t> &bytes) {
    unsigned int size = bytes.size();
    jbyteArray bytesArray = env->NewByteArray(size);
    env->SetByteArrayRegion(bytesArray, 0, size, (jbyte *) bytes.data());
    return bytesArray;
}

void readTgVoipPersistentState(const char *filePath, TgVoipPersistentState &tgVoipPersistentState) {
    FILE *persistentStateFile = fopen(filePath, "r");
    if (persistentStateFile) {
        fseek(persistentStateFile, 0, SEEK_END);
        auto len = static_cast<size_t>(ftell(persistentStateFile));
        fseek(persistentStateFile, 0, SEEK_SET);
        if (len < 1024 * 512 && len > 0) {
            auto *buffer = static_cast<uint8_t *>(malloc(len));
            fread(buffer, 1, len, persistentStateFile);
            tgVoipPersistentState.value = std::vector<uint8_t>(buffer, buffer + len);
            free(buffer);
        }
        fclose(persistentStateFile);
    }
}

void saveTgVoipPersistentState(const char *filePath, const TgVoipPersistentState &tgVoipPersistentState) {
    FILE *persistentStateFile = fopen(filePath, "w");
    if (persistentStateFile) {
        fwrite(tgVoipPersistentState.value.data(), 1, tgVoipPersistentState.value.size(), persistentStateFile);
        fclose(persistentStateFile);
    }
}

#pragma mark - Mappers

TgVoipNetworkType parseTgVoipNetworkType(jint networkType) {
    switch (networkType) {
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_GPRS:
            return TgVoipNetworkType::Gprs;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_EDGE:
            return TgVoipNetworkType::Edge;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_3G:
            return TgVoipNetworkType::ThirdGeneration;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_HSPA:
            return TgVoipNetworkType::Hspa;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_LTE:
            return TgVoipNetworkType::Lte;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_WIFI:
            return TgVoipNetworkType::WiFi;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_ETHERNET:
            return TgVoipNetworkType::Ethernet;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_OTHER_HIGH_SPEED:
            return TgVoipNetworkType::OtherHighSpeed;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_OTHER_LOW_SPEED:
            return TgVoipNetworkType::OtherLowSpeed;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_DIALUP:
            return TgVoipNetworkType::Dialup;
        case org_telegram_messenger_voip_TgVoip_NET_TYPE_OTHER_MOBILE:
            return TgVoipNetworkType::OtherMobile;
        default:
            return TgVoipNetworkType::Unknown;
    }
}

TgVoipDataSaving parseTgVoipDataSaving(JNIEnv *env, jint dataSaving) {
    switch (dataSaving) {
        case org_telegram_messenger_voip_TgVoip_DATA_SAVING_NEVER:
            return TgVoipDataSaving::Never;
        case org_telegram_messenger_voip_TgVoip_DATA_SAVING_MOBILE:
            return TgVoipDataSaving::Mobile;
        case org_telegram_messenger_voip_TgVoip_DATA_SAVING_ALWAYS:
            return TgVoipDataSaving::Always;
        case org_telegram_messenger_voip_TgVoip_DATA_SAVING_ROAMING:
            throwNewJavaIllegalArgumentException(env, "DATA_SAVING_ROAMING is not supported");
            return TgVoipDataSaving::Never;
        default:
            throwNewJavaIllegalArgumentException(env, "Unknown data saving constant: " + dataSaving);
            return TgVoipDataSaving::Never;
    }
}

void parseTgVoipConfig(JNIEnv *env, jobject config, TgVoipConfig &tgVoipConfig) {
    JavaObject configObject(env, config);
    tgVoipConfig.initializationTimeout = configObject.getDoubleField("initializationTimeout");
    tgVoipConfig.receiveTimeout = configObject.getDoubleField("receiveTimeout");
    tgVoipConfig.dataSaving = parseTgVoipDataSaving(env, configObject.getIntField("dataSaving"));
    tgVoipConfig.enableP2P = configObject.getBooleanField("enableP2p") == JNI_TRUE;
    tgVoipConfig.enableAEC = configObject.getBooleanField("enableAec") == JNI_TRUE;
    tgVoipConfig.enableNS = configObject.getBooleanField("enableNs") == JNI_TRUE;
    tgVoipConfig.enableAGC = configObject.getBooleanField("enableAgc") == JNI_TRUE;
    tgVoipConfig.enableCallUpgrade = configObject.getBooleanField("enableCallUpgrade") == JNI_TRUE;
    tgVoipConfig.logPath = jni::JavaStringToStdString(env, configObject.getStringField("logPath"));
    tgVoipConfig.maxApiLayer = configObject.getIntField("maxApiLayer");
}

TgVoipEndpointType parseTgVoipEndpointType(JNIEnv *env, jint endpointType) {
    switch (endpointType) {
        case org_telegram_messenger_voip_TgVoip_ENDPOINT_TYPE_INET:
            return TgVoipEndpointType::Inet;
        case org_telegram_messenger_voip_TgVoip_ENDPOINT_TYPE_LAN:
            return TgVoipEndpointType::Lan;
        case org_telegram_messenger_voip_TgVoip_ENDPOINT_TYPE_TCP_RELAY:
            return TgVoipEndpointType::TcpRelay;
        case org_telegram_messenger_voip_TgVoip_ENDPOINT_TYPE_UDP_RELAY:
            return TgVoipEndpointType::UdpRelay;
        default:
            throwNewJavaIllegalArgumentException(env, std::string("Unknown endpoint type: ").append(std::to_string(endpointType)).c_str());
            return TgVoipEndpointType::UdpRelay;
    }
}

void parseTgVoipEndpoint(JNIEnv *env, jobject endpoint, TgVoipEndpoint &tgVoipEndpoint) {
    JavaObject endpointObject(env, endpoint);
    tgVoipEndpoint.endpointId = endpointObject.getLongField("id");
    tgVoipEndpoint.host = jni::JavaStringToStdString(env, endpointObject.getStringField("ipv4"));
    tgVoipEndpoint.port = static_cast<uint16_t>(endpointObject.getIntField("port"));
    tgVoipEndpoint.type = parseTgVoipEndpointType(env, endpointObject.getIntField("type"));
    jbyteArray peerTag = endpointObject.getByteArrayField("peerTag");
    if (peerTag && env->GetArrayLength(peerTag)) {
        jbyte *peerTagBytes = env->GetByteArrayElements(peerTag, nullptr);
        memcpy(tgVoipEndpoint.peerTag, peerTagBytes, 16);
        env->ReleaseByteArrayElements(peerTag, peerTagBytes, JNI_ABORT);
    }
}

void parseTgVoipEndpoints(JNIEnv *env, jobjectArray endpoints, std::vector<TgVoipEndpoint> &tgVoipEndpoints) {
    for (int i = 0, size = env->GetArrayLength(endpoints); i < size; i++) {
        TgVoipEndpoint tgVoipEndpoint;
        parseTgVoipEndpoint(env, env->GetObjectArrayElement(endpoints, i), tgVoipEndpoint);
        tgVoipEndpoints.push_back(tgVoipEndpoint);
    }
}

void parseTgVoipEncryptionKey(JNIEnv *env, jobject encryptionKey, TgVoipEncryptionKey &tgVoipEncryptionKey) {
    JavaObject encryptionKeyObject(env, encryptionKey);
    tgVoipEncryptionKey.isOutgoing = encryptionKeyObject.getBooleanField("isOutgoing") == JNI_TRUE;
    jbyteArray valueByteArray = encryptionKeyObject.getByteArrayField("value");
    auto *valueBytes = (uint8_t *) env->GetByteArrayElements(valueByteArray, nullptr);
    tgVoipEncryptionKey.value = std::vector<uint8_t>(valueBytes, valueBytes + env->GetArrayLength(valueByteArray));
    env->ReleaseByteArrayElements(valueByteArray, (jbyte *) valueBytes, JNI_ABORT);
}

void parseTgVoipProxy(JNIEnv *env, jobject proxy, std::unique_ptr<TgVoipProxy> &tgVoipProxy) {
    if (!env->IsSameObject(proxy, nullptr)) {
        JavaObject proxyObject(env, proxy);
        tgVoipProxy = std::unique_ptr<TgVoipProxy>(new TgVoipProxy);
        tgVoipProxy->host = jni::JavaStringToStdString(env, proxyObject.getStringField("host"));
        tgVoipProxy->port = static_cast<uint16_t>(proxyObject.getIntField("port"));
        tgVoipProxy->login = jni::JavaStringToStdString(env, proxyObject.getStringField("login"));
        tgVoipProxy->password = jni::JavaStringToStdString(env, proxyObject.getStringField("password"));
    } else {
        tgVoipProxy = nullptr;
    }
}

jint asJavaState(const TgVoipState &tgVoipState) {
    switch (tgVoipState) {
        case TgVoipState::WaitInit:
            return org_telegram_messenger_voip_TgVoip_STATE_WAIT_INIT;
        case TgVoipState::WaitInitAck:
            return org_telegram_messenger_voip_TgVoip_STATE_WAIT_INIT_ACK;
        case TgVoipState::Estabilished:
            return org_telegram_messenger_voip_TgVoip_STATE_ESTABLISHED;
        case TgVoipState::Failed:
            return org_telegram_messenger_voip_TgVoip_STATE_FAILED;
        case TgVoipState::Reconnecting:
            return org_telegram_messenger_voip_TgVoip_STATE_RECONNECTING;
    }
}

jobject asJavaTrafficStats(JNIEnv *env, const TgVoipTrafficStats &trafficStats) {
    jclass clazz = env->FindClass("org/telegram/messenger/voip/TgVoip$TrafficStats");
    jmethodID initMethodId = env->GetMethodID(clazz, "<init>", "(JJJJ)V");
    return env->NewObject(clazz, initMethodId, trafficStats.bytesSentWifi, trafficStats.bytesReceivedWifi, trafficStats.bytesSentMobile, trafficStats.bytesReceivedMobile);
}

jobject asJavaFinalState(JNIEnv *env, const TgVoipFinalState &tgVoipFinalState) {
    jbyteArray persistentState = copyVectorToJavaByteArray(env, tgVoipFinalState.persistentState.value);
    jstring debugLog = env->NewStringUTF(tgVoipFinalState.debugLog.c_str());
    jobject trafficStats = asJavaTrafficStats(env, tgVoipFinalState.trafficStats);
    auto isRatingSuggested = static_cast<jboolean>(tgVoipFinalState.isRatingSuggested);
    jclass finalStateClass = env->FindClass("org/telegram/messenger/voip/TgVoip$FinalState");
    jmethodID finalStateInitMethodId = env->GetMethodID(finalStateClass, "<init>", "([BLjava/lang/String;Lorg/telegram/messenger/voip/TgVoip$TrafficStats;Z)V");
    return env->NewObject(finalStateClass, finalStateInitMethodId, persistentState, debugLog, trafficStats, isRatingSuggested);
}

extern "C" {

    #pragma mark - Static JNI Methods

    JNIEXPORT jlong JNICALL Java_org_telegram_messenger_voip_NativeTgVoipDelegate_makeNativeInstance(JNIEnv *env, jobject obj, jobject instanceObj, jobject config, jstring persistentStateFilePath, jobjectArray endpoints, jobject proxy, jint networkType, jobject encryptionKey) {
        // reading persistent state
        TgVoipPersistentState tgVoipPersistentState;
        readTgVoipPersistentState(jni::JavaStringToStdString(env, persistentStateFilePath).c_str(), tgVoipPersistentState);

        // parsing config
        TgVoipConfig tgVoipConfig;
        parseTgVoipConfig(env, config, tgVoipConfig);

        // parsing endpoints
        std::vector<TgVoipEndpoint> tgVoipEndpoints;
        parseTgVoipEndpoints(env, endpoints, tgVoipEndpoints);

        // parsing proxy
        std::unique_ptr<TgVoipProxy> tgVoipProxy;
        parseTgVoipProxy(env, proxy, tgVoipProxy);

        // parse encryption key
        TgVoipEncryptionKey tgVoipEncryptionKey;
        parseTgVoipEncryptionKey(env, encryptionKey, tgVoipEncryptionKey);

        TgVoip *tgVoip = TgVoip::makeInstance(tgVoipConfig, tgVoipPersistentState, tgVoipEndpoints, tgVoipProxy, parseTgVoipNetworkType(networkType), tgVoipEncryptionKey);

        if (env->ExceptionCheck() == JNI_TRUE) {
            return 0;
        }

        jobject globalRef = env->NewGlobalRef(instanceObj);

        tgVoip->setOnStateUpdated([globalRef](TgVoipState tgVoipState) {
            jint state = asJavaState(tgVoipState);
            jni::DoWithJNI([globalRef, state](JNIEnv *env) {
                env->CallVoidMethod(globalRef, env->GetMethodID(env->GetObjectClass(globalRef), "onStateUpdated", "(I)V"), state);
            });
        });

        tgVoip->setOnSignalBarsUpdated([globalRef](int signalBars) {
            jni::DoWithJNI([globalRef, signalBars](JNIEnv *env) {
                env->CallVoidMethod(globalRef, env->GetMethodID(env->GetObjectClass(globalRef), "onSignalBarsUpdated", "(I)V"), signalBars);
            });
        });

        auto *instance = new InstanceHolder;
        instance->nativeInstance = tgVoip;
        instance->javaInstance = globalRef;
        return reinterpret_cast<jlong>(instance);
    }

    JNIEXPORT void JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipDelegate_setGlobalServerConfig(JNIEnv *env, jobject obj, jstring serverConfigJson) {
        TgVoip::setGlobalServerConfig(jni::JavaStringToStdString(env, serverConfigJson));
    }

    JNIEXPORT jint JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipDelegate_getConnectionMaxLayer(JNIEnv *env, jobject obj) {
        return TgVoip::getConnectionMaxLayer();
    }

    JNIEXPORT jstring JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipDelegate_getVersion(JNIEnv *env, jobject obj) {
        return env->NewStringUTF(TgVoip::getVersion().c_str());
    }

    JNIEXPORT void JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipDelegate_setBufferSize(JNIEnv *env, jobject obj, jint size) {
        tgvoip::audio::AudioOutputOpenSLES::nativeBufferSize = (unsigned int) size;
        tgvoip::audio::AudioInputOpenSLES::nativeBufferSize = (unsigned int) size;
    }

    #pragma mark - Virtual JNI Methods

    JNIEXPORT void JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_setNetworkType(JNIEnv *env, jobject obj, jint networkType) {
        getTgVoip(env, obj)->setNetworkType(parseTgVoipNetworkType(networkType));
    }

    JNIEXPORT void JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_setMuteMicrophone(JNIEnv *env, jobject obj, jboolean muteMicrophone) {
        getTgVoip(env, obj)->setMuteMicrophone(muteMicrophone);
    }

    JNIEXPORT void JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_setAudioOutputGainControlEnabled(JNIEnv *env, jobject obj, jboolean enabled) {
        getTgVoip(env, obj)->setAudioOutputGainControlEnabled(enabled);
    }

    JNIEXPORT void JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_setEchoCancellationStrength(JNIEnv *env, jobject obj, jint strength) {
        getTgVoip(env, obj)->setEchoCancellationStrength(strength);
    }

    JNIEXPORT jstring JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_getLastError(JNIEnv *env, jobject obj) {
        return env->NewStringUTF(getTgVoip(env, obj)->getLastError().c_str());
    }

    JNIEXPORT jstring JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_getDebugInfo(JNIEnv *env, jobject obj) {
        return env->NewStringUTF(getTgVoip(env, obj)->getDebugInfo().c_str());
    }

    JNIEXPORT jlong JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_getPreferredRelayId(JNIEnv *env, jobject obj) {
        return getTgVoip(env, obj)->getPreferredRelayId();
    }

    JNIEXPORT jobject JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_getTrafficStats(JNIEnv *env, jobject obj) {
        return asJavaTrafficStats(env, getTgVoip(env, obj)->getTrafficStats());
    }

    JNIEXPORT jbyteArray JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_getPersistentState(JNIEnv *env, jobject obj) {
        return copyVectorToJavaByteArray(env, getTgVoip(env, obj)->getPersistentState().value);
    }

    JNIEXPORT jobject JNICALL
    Java_org_telegram_messenger_voip_NativeTgVoipInstance_stop(JNIEnv *env, jobject obj) {
        InstanceHolder *instance = getInstanceHolder(env, obj);
        TgVoipFinalState tgVoipFinalState = instance->nativeInstance->stop();

        // saving persistent state
        const std::string &path = jni::JavaStringToStdString(env, JavaObject(env, obj).getStringField("persistentStateFilePath"));
        saveTgVoipPersistentState(path.c_str(), tgVoipFinalState.persistentState);

        // clean
        env->DeleteGlobalRef(instance->javaInstance);
        delete instance->nativeInstance;
        delete instance;

        return asJavaFinalState(env, tgVoipFinalState);
    }
}