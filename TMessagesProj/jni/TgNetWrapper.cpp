#include <jni.h>
#include "tgnet/BuffersStorage.h"
#include "tgnet/NativeByteBuffer.h"
#include "tgnet/ConnectionsManager.h"
#include "tgnet/MTProtoScheme.h"
#include "tgnet/FileLog.h"

JavaVM *java;
jclass jclass_RequestDelegateInternal;
jmethodID jclass_RequestDelegateInternal_run;

jclass jclass_QuickAckDelegate;
jmethodID jclass_QuickAckDelegate_run;

jclass jclass_ConnectionsManager;
jmethodID jclass_ConnectionsManager_onUnparsedMessageReceived;
jmethodID jclass_ConnectionsManager_onUpdate;
jmethodID jclass_ConnectionsManager_onSessionCreated;
jmethodID jclass_ConnectionsManager_onLogout;
jmethodID jclass_ConnectionsManager_onConnectionStateChanged;
jmethodID jclass_ConnectionsManager_onInternalPushReceived;
jmethodID jclass_ConnectionsManager_onUpdateConfig;

jint getFreeBuffer(JNIEnv *env, jclass c, jint length) {
    return (jint) BuffersStorage::getInstance().getFreeBuffer(length);
}

jint limit(JNIEnv *env, jclass c, jint address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) address;
    return buffer->limit();
}

jint position(JNIEnv *env, jclass c, jint address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) address;
    return buffer->position();
}

void reuse(JNIEnv *env, jclass c, jint address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) address;
    buffer->reuse();
}

jobject getJavaByteBuffer(JNIEnv *env, jclass c, jint address) {
    NativeByteBuffer *buffer = (NativeByteBuffer *) address;
    return buffer->getJavaByteBuffer();
}

static const char *NativeByteBufferClassPathName = "org/telegram/tgnet/NativeByteBuffer";
static JNINativeMethod NativeByteBufferMethods[] = {
        {"native_getFreeBuffer", "(I)I", (void *) getFreeBuffer},
        {"native_limit", "(I)I", (void *) limit},
        {"native_position", "(I)I", (void *) position},
        {"native_reuse", "(I)V", (void *) reuse},
        {"native_getJavaByteBuffer", "(I)Ljava/nio/ByteBuffer;", (void *) getJavaByteBuffer}
};

jlong getCurrentTimeMillis(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().getCurrentTimeMillis();
}

jint getCurrentTime(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().getCurrentTime();
}

jint getTimeDifference(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().getTimeDifference();
}

void sendRequest(JNIEnv *env, jclass c, jint object, jobject onComplete, jobject onQuickAck, jint flags, jint datacenterId, jint connetionType, jboolean immediate, jint token) {
    TL_api_request *request = new TL_api_request();
    request->request = (NativeByteBuffer *) object;
    if (onComplete != nullptr) {
        onComplete = env->NewGlobalRef(onComplete);
    }
    if (onQuickAck != nullptr) {
        onQuickAck = env->NewGlobalRef(onQuickAck);
    }
    ConnectionsManager::getInstance().sendRequest(request, ([onComplete](TLObject *response, TL_error *error) {
        TL_api_response *resp = (TL_api_response *) response;
        jint ptr = 0;
        jint errorCode = 0;
        jstring errorText = nullptr;
        if (resp != nullptr) {
            ptr = (jint) resp->response.get();
        } else if (error != nullptr) {
            errorCode = error->code;
            errorText = jniEnv->NewStringUTF(error->text.c_str());
        }
        if (onComplete != nullptr) {
            jniEnv->CallVoidMethod(onComplete, jclass_RequestDelegateInternal_run, ptr, errorCode, errorText);
        }
        if (errorText != nullptr) {
            jniEnv->DeleteLocalRef(errorText);
        }
    }), ([onQuickAck] {
        if (onQuickAck != nullptr) {
            jniEnv->CallVoidMethod(onQuickAck, jclass_QuickAckDelegate_run);
        }
    }), flags, datacenterId, (ConnectionType) connetionType, immediate, token, onComplete, onQuickAck);
}

void cancelRequest(JNIEnv *env, jclass c, jint token, jboolean notifyServer) {
    return ConnectionsManager::getInstance().cancelRequest(token, notifyServer);
}

void cleanUp(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().cleanUp();
}

void cancelRequestsForGuid(JNIEnv *env, jclass c, jint guid) {
    return ConnectionsManager::getInstance().cancelRequestsForGuid(guid);
}

void bindRequestToGuid(JNIEnv *env, jclass c, jint requestToken, jint guid) {
    return ConnectionsManager::getInstance().bindRequestToGuid(requestToken, guid);
}

void applyDatacenterAddress(JNIEnv *env, jclass c, jint datacenterId, jstring ipAddress, jint port) {
    const char *valueStr = env->GetStringUTFChars(ipAddress, 0);

    ConnectionsManager::getInstance().applyDatacenterAddress(datacenterId, std::string(valueStr), port);

    if (valueStr != 0) {
        env->ReleaseStringUTFChars(ipAddress, valueStr);
    }
}

jint getConnectionState(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().getConnectionState();
}

void setUserId(JNIEnv *env, jclass c, int32_t id) {
    ConnectionsManager::getInstance().setUserId(id);
}

void switchBackend(JNIEnv *env, jclass c) {
    ConnectionsManager::getInstance().switchBackend();
}

void pauseNetwork(JNIEnv *env, jclass c) {
    ConnectionsManager::getInstance().pauseNetwork();
}

void resumeNetwork(JNIEnv *env, jclass c, jboolean partial) {
    ConnectionsManager::getInstance().resumeNetwork(partial);
}

void updateDcSettings(JNIEnv *env, jclass c) {
    ConnectionsManager::getInstance().updateDcSettings(0);
}

void setUseIpv6(JNIEnv *env, jclass c, bool value) {
    ConnectionsManager::getInstance().setUseIpv6(value);
}

void setNetworkAvailable(JNIEnv *env, jclass c, jboolean value) {
    ConnectionsManager::getInstance().setNetworkAvailable(value);
}

void setPushConnectionEnabled(JNIEnv *env, jclass c, jboolean value) {
    ConnectionsManager::getInstance().setPushConnectionEnabled(value);
}

class Delegate : public ConnectiosManagerDelegate {
    
    void onUpdate() {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onUpdate);
    }
    
    void onSessionCreated() {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onSessionCreated);
    }
    
    void onConnectionStateChanged(ConnectionState state) {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onConnectionStateChanged, state);
    }
    
    void onUnparsedMessageReceived(int64_t reqMessageId, NativeByteBuffer *buffer, ConnectionType connectionType) {
        if (connectionType == ConnectionTypeGeneric) {
            jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onUnparsedMessageReceived, buffer);
        }
    }
    
    void onLogout() {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onLogout);
    }
    
    void onUpdateConfig(TL_config *config) {
        NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(config->getObjectSize());
        config->serializeToStream(buffer);
        buffer->position(0);
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onUpdateConfig, buffer);
        buffer->reuse();
    }
    
    void onInternalPushReceived() {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onInternalPushReceived);
    }
};

void init(JNIEnv *env, jclass c, jint version, jint layer, jint apiId, jstring deviceModel, jstring systemVersion, jstring appVersion, jstring langCode, jstring configPath, jstring logPath, jint userId, jboolean enablePushConnection) {
    const char *deviceModelStr = env->GetStringUTFChars(deviceModel, 0);
    const char *systemVersionStr = env->GetStringUTFChars(systemVersion, 0);
    const char *appVersionStr = env->GetStringUTFChars(appVersion, 0);
    const char *langCodeStr = env->GetStringUTFChars(langCode, 0);
    const char *configPathStr = env->GetStringUTFChars(configPath, 0);
    const char *logPathStr = env->GetStringUTFChars(logPath, 0);

    ConnectionsManager::getInstance().init(version, layer, apiId, std::string(deviceModelStr), std::string(systemVersionStr), std::string(appVersionStr), std::string(langCodeStr), std::string(configPathStr), std::string(logPathStr), userId, true, enablePushConnection);

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
    if (configPathStr != 0) {
        env->ReleaseStringUTFChars(configPath, configPathStr);
    }
    if (logPathStr != 0) {
        env->ReleaseStringUTFChars(logPath, logPathStr);
    }
}

void setJava(JNIEnv *env, jclass c, jboolean useJavaByteBuffers) {
    ConnectionsManager::useJavaVM(java, useJavaByteBuffers);
    ConnectionsManager::getInstance().setDelegate(new Delegate());
}

static const char *ConnectionsManagerClassPathName = "org/telegram/tgnet/ConnectionsManager";
static JNINativeMethod ConnectionsManagerMethods[] = {
        {"native_getCurrentTimeMillis", "()J", (void *) getCurrentTimeMillis},
        {"native_getCurrentTime", "()I", (void *) getCurrentTime},
        {"native_getTimeDifference", "()I", (void *) getTimeDifference},
        {"native_sendRequest", "(ILorg/telegram/tgnet/RequestDelegateInternal;Lorg/telegram/tgnet/QuickAckDelegate;IIIZI)V", (void *) sendRequest},
        {"native_cancelRequest", "(IZ)V", (void *) cancelRequest},
        {"native_cleanUp", "()V", (void *) cleanUp},
        {"native_cancelRequestsForGuid", "(I)V", (void *) cancelRequestsForGuid},
        {"native_bindRequestToGuid", "(II)V", (void *) bindRequestToGuid},
        {"native_applyDatacenterAddress", "(ILjava/lang/String;I)V", (void *) applyDatacenterAddress},
        {"native_getConnectionState", "()I", (void *) getConnectionState},
        {"native_setUserId", "(I)V", (void *) setUserId},
        {"native_init", "(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ)V", (void *) init},
        {"native_switchBackend", "()V", (void *) switchBackend},
        {"native_pauseNetwork", "()V", (void *) pauseNetwork},
        {"native_resumeNetwork", "(Z)V", (void *) resumeNetwork},
        {"native_updateDcSettings", "()V", (void *) updateDcSettings},
        {"native_setUseIpv6", "(Z)V", (void *) setUseIpv6},
        {"native_setNetworkAvailable", "(Z)V", (void *) setNetworkAvailable},
        {"native_setPushConnectionEnabled", "(Z)V", (void *) setPushConnectionEnabled},
        {"native_setJava", "(Z)V", (void *) setJava}
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
    
    jclass_RequestDelegateInternal = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/RequestDelegateInternal"));
    if (jclass_RequestDelegateInternal == 0) {
        return JNI_FALSE;
    }
    jclass_RequestDelegateInternal_run = env->GetMethodID(jclass_RequestDelegateInternal, "run", "(IILjava/lang/String;)V");
    if (jclass_RequestDelegateInternal_run == 0) {
        return JNI_FALSE;
    }

    jclass_QuickAckDelegate = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/QuickAckDelegate"));
    if (jclass_RequestDelegateInternal == 0) {
        return JNI_FALSE;
    }
    jclass_QuickAckDelegate_run = env->GetMethodID(jclass_QuickAckDelegate, "run", "()V");
    if (jclass_QuickAckDelegate_run == 0) {
        return JNI_FALSE;
    }

    jclass_ConnectionsManager = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/ConnectionsManager"));
    if (jclass_ConnectionsManager == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onUnparsedMessageReceived = env->GetStaticMethodID(jclass_ConnectionsManager, "onUnparsedMessageReceived", "(I)V");
    if (jclass_ConnectionsManager_onUnparsedMessageReceived == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onUpdate = env->GetStaticMethodID(jclass_ConnectionsManager, "onUpdate", "()V");
    if (jclass_ConnectionsManager_onUpdate == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onSessionCreated = env->GetStaticMethodID(jclass_ConnectionsManager, "onSessionCreated", "()V");
    if (jclass_ConnectionsManager_onSessionCreated == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onLogout = env->GetStaticMethodID(jclass_ConnectionsManager, "onLogout", "()V");
    if (jclass_ConnectionsManager_onLogout == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onConnectionStateChanged = env->GetStaticMethodID(jclass_ConnectionsManager, "onConnectionStateChanged", "(I)V");
    if (jclass_ConnectionsManager_onConnectionStateChanged == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onInternalPushReceived = env->GetStaticMethodID(jclass_ConnectionsManager, "onInternalPushReceived", "()V");
    if (jclass_ConnectionsManager_onInternalPushReceived == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onUpdateConfig = env->GetStaticMethodID(jclass_ConnectionsManager, "onUpdateConfig", "(I)V");
    if (jclass_ConnectionsManager_onUpdateConfig == 0) {
        return JNI_FALSE;
    }
    ConnectionsManager::getInstance().setDelegate(new Delegate());
    
    return JNI_TRUE;
}
