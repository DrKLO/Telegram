#include "org_telegram_messenger_voip_Instance.h"

#include <jni.h>
#include <sdk/android/native_api/video/wrapper.h>
#include <VideoCapturerInterface.h>
#include <platform/android/AndroidInterface.h>
#include <platform/android/AndroidContext.h>
#include <rtc_base/ssl_adapter.h>
#include <modules/utility/include/jvm_android.h>
#include <sdk/android/native_api/base/init.h>

#include "pc/video_track.h"
#include "legacy/InstanceImplLegacy.h"
#include "InstanceImpl.h"
#include "reference/InstanceImplReference.h"
#include "libtgvoip/os/android/AudioOutputOpenSLES.h"
#include "libtgvoip/os/android/AudioInputOpenSLES.h"
#include "libtgvoip/os/android/JNIUtilities.h"
#include "tgcalls/VideoCaptureInterface.h"

using namespace tgcalls;

const auto RegisterTag = Register<InstanceImpl>();
const auto RegisterTagLegacy = Register<InstanceImplLegacy>();
const auto RegisterTagReference = tgcalls::Register<InstanceImplReference>();

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
    std::unique_ptr<Instance> nativeInstance;
    jobject javaInstance;
    std::shared_ptr<tgcalls::VideoCaptureInterface> _videoCapture;
};

jclass TrafficStatsClass;
jclass FinalStateClass;
jmethodID FinalStateInitMethod;

jlong getInstanceHolderId(JNIEnv *env, jobject obj) {
    return env->GetLongField(obj, env->GetFieldID(env->GetObjectClass(obj), "nativePtr", "J"));
}

InstanceHolder *getInstanceHolder(JNIEnv *env, jobject obj) {
    return reinterpret_cast<InstanceHolder *>(getInstanceHolderId(env, obj));
}

Instance *getInstance(JNIEnv *env, jobject obj) {
    return getInstanceHolder(env, obj)->nativeInstance.get();
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

void readPersistentState(const char *filePath, PersistentState &persistentState) {
    FILE *persistentStateFile = fopen(filePath, "r");
    if (persistentStateFile) {
        fseek(persistentStateFile, 0, SEEK_END);
        auto len = static_cast<size_t>(ftell(persistentStateFile));
        fseek(persistentStateFile, 0, SEEK_SET);
        if (len < 1024 * 512 && len > 0) {
            auto *buffer = static_cast<uint8_t *>(malloc(len));
            fread(buffer, 1, len, persistentStateFile);
            persistentState.value = std::vector<uint8_t>(buffer, buffer + len);
            free(buffer);
        }
        fclose(persistentStateFile);
    }
}

void savePersistentState(const char *filePath, const PersistentState &persistentState) {
    FILE *persistentStateFile = fopen(filePath, "w");
    if (persistentStateFile) {
        fwrite(persistentState.value.data(), 1, persistentState.value.size(), persistentStateFile);
        fclose(persistentStateFile);
    }
}

NetworkType parseNetworkType(jint networkType) {
    switch (networkType) {
        case org_telegram_messenger_voip_Instance_NET_TYPE_GPRS:
            return NetworkType::Gprs;
        case org_telegram_messenger_voip_Instance_NET_TYPE_EDGE:
            return NetworkType::Edge;
        case org_telegram_messenger_voip_Instance_NET_TYPE_3G:
            return NetworkType::ThirdGeneration;
        case org_telegram_messenger_voip_Instance_NET_TYPE_HSPA:
            return NetworkType::Hspa;
        case org_telegram_messenger_voip_Instance_NET_TYPE_LTE:
            return NetworkType::Lte;
        case org_telegram_messenger_voip_Instance_NET_TYPE_WIFI:
            return NetworkType::WiFi;
        case org_telegram_messenger_voip_Instance_NET_TYPE_ETHERNET:
            return NetworkType::Ethernet;
        case org_telegram_messenger_voip_Instance_NET_TYPE_OTHER_HIGH_SPEED:
            return NetworkType::OtherHighSpeed;
        case org_telegram_messenger_voip_Instance_NET_TYPE_OTHER_LOW_SPEED:
            return NetworkType::OtherLowSpeed;
        case org_telegram_messenger_voip_Instance_NET_TYPE_DIALUP:
            return NetworkType::Dialup;
        case org_telegram_messenger_voip_Instance_NET_TYPE_OTHER_MOBILE:
            return NetworkType::OtherMobile;
        default:
            return NetworkType::Unknown;
    }
}

DataSaving parseDataSaving(JNIEnv *env, jint dataSaving) {
    switch (dataSaving) {
        case org_telegram_messenger_voip_Instance_DATA_SAVING_NEVER:
            return DataSaving::Never;
        case org_telegram_messenger_voip_Instance_DATA_SAVING_MOBILE:
            return DataSaving::Mobile;
        case org_telegram_messenger_voip_Instance_DATA_SAVING_ALWAYS:
            return DataSaving::Always;
        case org_telegram_messenger_voip_Instance_DATA_SAVING_ROAMING:
            throwNewJavaIllegalArgumentException(env, "DATA_SAVING_ROAMING is not supported");
            return DataSaving::Never;
        default:
            throwNewJavaIllegalArgumentException(env, "Unknown data saving constant: " + dataSaving);
            return DataSaving::Never;
    }
}

EndpointType parseEndpointType(JNIEnv *env, jint endpointType) {
    switch (endpointType) {
        case org_telegram_messenger_voip_Instance_ENDPOINT_TYPE_INET:
            return EndpointType::Inet;
        case org_telegram_messenger_voip_Instance_ENDPOINT_TYPE_LAN:
            return EndpointType::Lan;
        case org_telegram_messenger_voip_Instance_ENDPOINT_TYPE_TCP_RELAY:
            return EndpointType::TcpRelay;
        case org_telegram_messenger_voip_Instance_ENDPOINT_TYPE_UDP_RELAY:
            return EndpointType::UdpRelay;
        default:
            throwNewJavaIllegalArgumentException(env, std::string("Unknown endpoint type: ").append(std::to_string(endpointType)).c_str());
            return EndpointType::UdpRelay;
    }
}

jint asJavaState(const State &state) {
    switch (state) {
        case State::WaitInit:
            return org_telegram_messenger_voip_Instance_STATE_WAIT_INIT;
        case State::WaitInitAck:
            return org_telegram_messenger_voip_Instance_STATE_WAIT_INIT_ACK;
        case State::Established:
            return org_telegram_messenger_voip_Instance_STATE_ESTABLISHED;
        case State::Failed:
            return org_telegram_messenger_voip_Instance_STATE_FAILED;
        case State::Reconnecting:
            return org_telegram_messenger_voip_Instance_STATE_RECONNECTING;
    }
}

jobject asJavaTrafficStats(JNIEnv *env, const TrafficStats &trafficStats) {
    jmethodID initMethodId = env->GetMethodID(TrafficStatsClass, "<init>", "(JJJJ)V");
    return env->NewObject(TrafficStatsClass, initMethodId, (jlong) trafficStats.bytesSentWifi, (jlong) trafficStats.bytesReceivedWifi, (jlong) trafficStats.bytesSentMobile, (jlong) trafficStats.bytesReceivedMobile);
}

jobject asJavaFinalState(JNIEnv *env, const FinalState &finalState) {
    jbyteArray persistentState = copyVectorToJavaByteArray(env, finalState.persistentState.value);
    jstring debugLog = env->NewStringUTF(finalState.debugLog.c_str());
    jobject trafficStats = asJavaTrafficStats(env, finalState.trafficStats);
    auto isRatingSuggested = static_cast<jboolean>(finalState.isRatingSuggested);
    return env->NewObject(FinalStateClass, FinalStateInitMethod, persistentState, debugLog, trafficStats, isRatingSuggested);
}

extern "C" {

bool webrtcLoaded = false;

void initWebRTC(JNIEnv *env) {
    if (webrtcLoaded) {
        return;
    }
    JavaVM* vm;
    env->GetJavaVM(&vm);
    webrtc::InitAndroid(vm);
    webrtc::JVM::Initialize(vm);
    rtc::InitializeSSL();
    webrtcLoaded = true;

    TrafficStatsClass = static_cast<jclass>(env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/Instance$TrafficStats")));
    FinalStateClass = static_cast<jclass>(env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/Instance$FinalState")));
    FinalStateInitMethod = env->GetMethodID(FinalStateClass, "<init>", "([BLjava/lang/String;Lorg/telegram/messenger/voip/Instance$TrafficStats;Z)V");
}

JNIEXPORT jlong JNICALL Java_org_telegram_messenger_voip_NativeInstance_makeNativeInstance(JNIEnv *env, jclass clazz, jstring version, jobject instanceObj, jobject config, jstring persistentStateFilePath, jobjectArray endpoints, jobject proxyClass, jint networkType, jobject encryptionKey, jobject remoteSink, jlong videoCapturer, jfloat aspectRatio) {
    initWebRTC(env);

    JavaObject configObject(env, config);
    JavaObject encryptionKeyObject(env, encryptionKey);
    std::string v = tgvoip::jni::JavaStringToStdString(env, version);

    jbyteArray valueByteArray = encryptionKeyObject.getByteArrayField("value");
    auto *valueBytes = (uint8_t *) env->GetByteArrayElements(valueByteArray, nullptr);
    auto encryptionKeyValue = std::make_shared<std::array<uint8_t, 256>>();
    memcpy(encryptionKeyValue->data(), valueBytes, 256);
    env->ReleaseByteArrayElements(valueByteArray, (jbyte *) valueBytes, JNI_ABORT);

    jobject globalRef = env->NewGlobalRef(instanceObj);
    std::shared_ptr<VideoCaptureInterface> videoCapture = videoCapturer ? std::shared_ptr<VideoCaptureInterface>(reinterpret_cast<VideoCaptureInterface *>(videoCapturer)) : nullptr;

    Descriptor descriptor = {
            .config = Config{
                    .initializationTimeout = configObject.getDoubleField("initializationTimeout"),
                    .receiveTimeout = configObject.getDoubleField("receiveTimeout"),
                    .dataSaving = parseDataSaving(env, configObject.getIntField("dataSaving")),
                    .enableP2P = configObject.getBooleanField("enableP2p") == JNI_TRUE,
                    .enableAEC = configObject.getBooleanField("enableAec") == JNI_TRUE,
                    .enableNS = configObject.getBooleanField("enableNs") == JNI_TRUE,
                    .enableAGC = configObject.getBooleanField("enableAgc") == JNI_TRUE,
                    .enableVolumeControl = true,
                    .logPath = tgvoip::jni::JavaStringToStdString(env, configObject.getStringField("logPath")),
                    .maxApiLayer = configObject.getIntField("maxApiLayer"),
                    .preferredAspectRatio = aspectRatio
            },
            .encryptionKey = EncryptionKey(
                    std::move(encryptionKeyValue),
                    encryptionKeyObject.getBooleanField("isOutgoing") == JNI_TRUE),
            .videoCapture =  videoCapture,
            .stateUpdated = [globalRef](State state) {
                jint javaState = asJavaState(state);
                tgvoip::jni::DoWithJNI([globalRef, javaState](JNIEnv *env) {
                    env->CallVoidMethod(globalRef, env->GetMethodID(env->GetObjectClass(globalRef), "onStateUpdated", "(I)V"), javaState);
                });
            },
            .signalBarsUpdated = [globalRef](int count) {
                tgvoip::jni::DoWithJNI([globalRef, count](JNIEnv *env) {
                    env->CallVoidMethod(globalRef, env->GetMethodID(env->GetObjectClass(globalRef), "onSignalBarsUpdated", "(I)V"), count);
                });
            },
            .remoteMediaStateUpdated = [globalRef](AudioState audioState, VideoState videoState) {
                tgvoip::jni::DoWithJNI([globalRef, audioState, videoState](JNIEnv *env) {
                    env->CallVoidMethod(globalRef, env->GetMethodID(env->GetObjectClass(globalRef), "onRemoteMediaStateUpdated", "(II)V"), audioState, videoState);
                });
            },
            .signalingDataEmitted = [globalRef](const std::vector<uint8_t> &data) {
                tgvoip::jni::DoWithJNI([globalRef, data](JNIEnv *env) {
                    jbyteArray arr = copyVectorToJavaByteArray(env, data);
                    env->CallVoidMethod(globalRef, env->GetMethodID(env->GetObjectClass(globalRef), "onSignalingData", "([B)V"), arr);
                });
            },
    };

    for (int i = 0, size = env->GetArrayLength(endpoints); i < size; i++) {
        JavaObject endpointObject(env, env->GetObjectArrayElement(endpoints, i));
        bool isRtc = endpointObject.getBooleanField("isRtc");
        if (isRtc) {
            RtcServer rtcServer;
            rtcServer.host = tgvoip::jni::JavaStringToStdString(env, endpointObject.getStringField("ipv4"));
            rtcServer.port = static_cast<uint16_t>(endpointObject.getIntField("port"));
            rtcServer.login = tgvoip::jni::JavaStringToStdString(env, endpointObject.getStringField("username"));
            rtcServer.password = tgvoip::jni::JavaStringToStdString(env, endpointObject.getStringField("password"));
            rtcServer.isTurn = endpointObject.getBooleanField("turn");
            descriptor.rtcServers.push_back(std::move(rtcServer));
        } else {
            Endpoint endpoint;
            endpoint.endpointId = endpointObject.getLongField("id");
            endpoint.host = EndpointHost{tgvoip::jni::JavaStringToStdString(env, endpointObject.getStringField("ipv4")), tgvoip::jni::JavaStringToStdString(env, endpointObject.getStringField("ipv6"))};
            endpoint.port = static_cast<uint16_t>(endpointObject.getIntField("port"));
            endpoint.type = parseEndpointType(env, endpointObject.getIntField("type"));
            jbyteArray peerTag = endpointObject.getByteArrayField("peerTag");
            if (peerTag && env->GetArrayLength(peerTag)) {
                jbyte *peerTagBytes = env->GetByteArrayElements(peerTag, nullptr);
                memcpy(endpoint.peerTag, peerTagBytes, 16);
                env->ReleaseByteArrayElements(peerTag, peerTagBytes, JNI_ABORT);
            }
            descriptor.endpoints.push_back(std::move(endpoint));
        }
    }

    if (!env->IsSameObject(proxyClass, nullptr)) {
        JavaObject proxyObject(env, proxyClass);
        descriptor.proxy = std::unique_ptr<Proxy>(new Proxy);
        descriptor.proxy->host = tgvoip::jni::JavaStringToStdString(env, proxyObject.getStringField("host"));
        descriptor.proxy->port = static_cast<uint16_t>(proxyObject.getIntField("port"));
        descriptor.proxy->login = tgvoip::jni::JavaStringToStdString(env, proxyObject.getStringField("login"));
        descriptor.proxy->password = tgvoip::jni::JavaStringToStdString(env, proxyObject.getStringField("password"));
    }

    readPersistentState(tgvoip::jni::JavaStringToStdString(env, persistentStateFilePath).c_str(), descriptor.persistentState);

    auto *holder = new InstanceHolder;
    holder->nativeInstance = tgcalls::Meta::Create(v, std::move(descriptor));
    holder->javaInstance = globalRef;
    holder->_videoCapture = videoCapture;
    holder->nativeInstance->setIncomingVideoOutput(webrtc::JavaToNativeVideoSink(env, remoteSink));
    holder->nativeInstance->setNetworkType(parseNetworkType(networkType));
    return reinterpret_cast<jlong>(holder);
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setGlobalServerConfig(JNIEnv *env, jobject obj, jstring serverConfigJson) {
    SetLegacyGlobalServerConfig(tgvoip::jni::JavaStringToStdString(env, serverConfigJson));
}

JNIEXPORT jstring JNICALL Java_org_telegram_messenger_voip_NativeInstance_getVersion(JNIEnv *env, jobject obj) {
    return env->NewStringUTF(tgvoip::VoIPController::GetVersion());
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setBufferSize(JNIEnv *env, jobject obj, jint size) {
    tgvoip::audio::AudioOutputOpenSLES::nativeBufferSize = (unsigned int) size;
    tgvoip::audio::AudioInputOpenSLES::nativeBufferSize = (unsigned int) size;
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setNetworkType(JNIEnv *env, jobject obj, jint networkType) {
    getInstance(env, obj)->setNetworkType(parseNetworkType(networkType));
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setMuteMicrophone(JNIEnv *env, jobject obj, jboolean muteMicrophone) {
    getInstance(env, obj)->setMuteMicrophone(muteMicrophone);
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setAudioOutputGainControlEnabled(JNIEnv *env, jobject obj, jboolean enabled) {
    getInstance(env, obj)->setAudioOutputGainControlEnabled(enabled);
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setEchoCancellationStrength(JNIEnv *env, jobject obj, jint strength) {
    getInstance(env, obj)->setEchoCancellationStrength(strength);
}

JNIEXPORT jstring JNICALL Java_org_telegram_messenger_voip_NativeInstance_getLastError(JNIEnv *env, jobject obj) {
    return env->NewStringUTF(getInstance(env, obj)->getLastError().c_str());
}

JNIEXPORT jstring JNICALL Java_org_telegram_messenger_voip_NativeInstance_getDebugInfo(JNIEnv *env, jobject obj) {
    return env->NewStringUTF(getInstance(env, obj)->getDebugInfo().c_str());
}

JNIEXPORT jlong JNICALL Java_org_telegram_messenger_voip_NativeInstance_getPreferredRelayId(JNIEnv *env, jobject obj) {
    return getInstance(env, obj)->getPreferredRelayId();
}

JNIEXPORT jobject JNICALL Java_org_telegram_messenger_voip_NativeInstance_getTrafficStats(JNIEnv *env, jobject obj) {
    return asJavaTrafficStats(env, getInstance(env, obj)->getTrafficStats());
}

JNIEXPORT jbyteArray JNICALL Java_org_telegram_messenger_voip_NativeInstance_getPersistentState(JNIEnv *env, jobject obj) {
    return copyVectorToJavaByteArray(env, getInstance(env, obj)->getPersistentState().value);
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_stopNative(JNIEnv *env, jobject obj) {
    InstanceHolder *instance = getInstanceHolder(env, obj);
    instance->nativeInstance->stop([instance](FinalState finalState) {
        JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
        const std::string &path = tgvoip::jni::JavaStringToStdString(env, JavaObject(env, instance->javaInstance).getStringField("persistentStateFilePath"));
        savePersistentState(path.c_str(), finalState.persistentState);
        env->CallVoidMethod(instance->javaInstance, env->GetMethodID(env->GetObjectClass(instance->javaInstance), "onStop", "(Lorg/telegram/messenger/voip/Instance$FinalState;)V"), asJavaFinalState(env, finalState));
        env->DeleteGlobalRef(instance->javaInstance);
        delete instance;
    });
}

JNIEXPORT long JNICALL Java_org_telegram_messenger_voip_NativeInstance_createVideoCapturer(JNIEnv *env, jclass clazz, jobject localSink) {
    initWebRTC(env);
    std::unique_ptr<VideoCaptureInterface> capture = tgcalls::VideoCaptureInterface::Create(std::make_shared<AndroidContext>(env));
    capture->setOutput(webrtc::JavaToNativeVideoSink(env, localSink));
    capture->setState(VideoState::Active);
    return reinterpret_cast<intptr_t>(capture.release());
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_destroyVideoCapturer(JNIEnv *env, jclass clazz, jlong videoCapturer) {
    VideoCaptureInterface *capturer = reinterpret_cast<VideoCaptureInterface *>(videoCapturer);
    delete capturer;
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_switchCameraCapturer(JNIEnv *env, jclass clazz, jlong videoCapturer) {
    VideoCaptureInterface *capturer = reinterpret_cast<VideoCaptureInterface *>(videoCapturer);
    capturer->switchCamera();
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setVideoStateCapturer(JNIEnv *env, jclass clazz, jlong videoCapturer, jint videoState) {
    VideoCaptureInterface *capturer = reinterpret_cast<VideoCaptureInterface *>(videoCapturer);
    capturer->setState(static_cast<VideoState>(videoState));
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_switchCamera(JNIEnv *env, jobject obj) {
    InstanceHolder *instance = getInstanceHolder(env, obj);
    if (instance->_videoCapture == nullptr) {
        return;
    }
    instance->_videoCapture->switchCamera();
}

JNIEXPORT void Java_org_telegram_messenger_voip_NativeInstance_setVideoState(JNIEnv *env, jobject obj, jint state) {
    InstanceHolder *instance = getInstanceHolder(env, obj);
    if (instance->_videoCapture == nullptr) {
        return;
    }
    instance->_videoCapture->setState(static_cast<VideoState>(state));
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_setupOutgoingVideo(JNIEnv *env, jobject obj, jobject localSink) {
    InstanceHolder *instance = getInstanceHolder(env, obj);
    if (instance->_videoCapture) {
        return;
    }
    instance->_videoCapture = tgcalls::VideoCaptureInterface::Create(std::make_shared<AndroidContext>(env));
    instance->_videoCapture->setOutput(webrtc::JavaToNativeVideoSink(env, localSink));
    instance->_videoCapture->setState(VideoState::Active);
    instance->nativeInstance->setVideoCapture(instance->_videoCapture);
}

JNIEXPORT void JNICALL Java_org_telegram_messenger_voip_NativeInstance_onSignalingDataReceive(JNIEnv *env, jobject obj, jbyteArray value) {
    InstanceHolder *instance = getInstanceHolder(env, obj);

    auto *valueBytes = (uint8_t *) env->GetByteArrayElements(value, nullptr);
    const size_t size = env->GetArrayLength(value);
    auto array = std::vector<uint8_t>(size);
    memcpy(&array[0], valueBytes, size);
    instance->nativeInstance->receiveSignalingData(std::move(array));
    env->ReleaseByteArrayElements(value, (jbyte *) valueBytes, JNI_ABORT);
}


}