#include <jni.h>
#include "tgnet/ApiScheme.h"
#include "tgnet/BuffersStorage.h"
#include "tgnet/NativeByteBuffer.h"
#include "tgnet/ConnectionsManager.h"
#include "tgnet/MTProtoScheme.h"
#include "tgnet/FileLoadOperation.h"

JavaVM *java;
jclass jclass_RequestDelegateInternal;
jmethodID jclass_RequestDelegateInternal_run;

jclass jclass_QuickAckDelegate;
jmethodID jclass_QuickAckDelegate_run;

jclass jclass_WriteToSocketDelegate;
jmethodID jclass_WriteToSocketDelegate_run;

jclass jclass_FileLoadOperationDelegate;
jmethodID jclass_FileLoadOperationDelegate_onFinished;
jmethodID jclass_FileLoadOperationDelegate_onFailed;
jmethodID jclass_FileLoadOperationDelegate_onProgressChanged;

jclass jclass_ConnectionsManager;
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

jint createLoadOpetation(JNIEnv *env, jclass c, jint dc_id, jlong id, jlong volume_id, jlong access_hash, jint local_id, jbyteArray encKey, jbyteArray encIv, jstring extension, jint version, jint size, jstring dest, jstring temp, jobject delegate) {
    if (encKey != nullptr && encIv == nullptr || encKey == nullptr && encIv != nullptr || extension == nullptr || dest == nullptr || temp == nullptr) {
        return 0;
    }
    FileLoadOperation *loadOperation = nullptr;
    bool error = false;

    const char *extensionStr = env->GetStringUTFChars(extension, NULL);
    const char *destStr = env->GetStringUTFChars(dest, NULL);
    const char *tempStr = env->GetStringUTFChars(temp, NULL);

    if (extensionStr == nullptr || destStr == nullptr || tempStr == nullptr) {
        error = true;
    }

    jbyte *keyBuff = nullptr;
    jbyte *ivBuff = nullptr;

    if (!error && encKey != nullptr) {
        keyBuff = env->GetByteArrayElements(encKey, NULL);
        ivBuff = env->GetByteArrayElements(encIv, NULL);
        if (keyBuff == nullptr || ivBuff == nullptr) {
            error = true;
        }
    }
    if (!error) {
        if (delegate != nullptr) {
            delegate = env->NewGlobalRef(delegate);
        }
        loadOperation = new FileLoadOperation(dc_id, id, volume_id, access_hash, local_id, (uint8_t *) keyBuff, (uint8_t *) ivBuff, extensionStr, version, size, destStr, tempStr);
        loadOperation->setDelegate([delegate](std::string path) {
            jstring pathText = jniEnv->NewStringUTF(path.c_str());
            if (delegate != nullptr) {
                jniEnv->CallVoidMethod(delegate, jclass_FileLoadOperationDelegate_onFinished, pathText);
            }
            if (pathText != nullptr) {
                jniEnv->DeleteLocalRef(pathText);
            }
        }, [delegate](FileLoadFailReason reason) {
            if (delegate != nullptr) {
                jniEnv->CallVoidMethod(delegate, jclass_FileLoadOperationDelegate_onFailed, reason);
            }
        }, [delegate](float progress) {
            if (delegate != nullptr) {
                jniEnv->CallVoidMethod(delegate, jclass_FileLoadOperationDelegate_onProgressChanged, progress);
            }
        });
        loadOperation->ptr1 = delegate;
    }
    if (keyBuff != nullptr) {
        env->ReleaseByteArrayElements(encKey, keyBuff, JNI_ABORT);
    }
    if (ivBuff != nullptr) {
        env->ReleaseByteArrayElements(encIv, ivBuff, JNI_ABORT);
    }
    if (extensionStr != nullptr) {
        env->ReleaseStringUTFChars(extension, extensionStr);
    }
    if (destStr != nullptr) {
        env->ReleaseStringUTFChars(dest, destStr);
    }
    if (tempStr != nullptr) {
        env->ReleaseStringUTFChars(temp, tempStr);
    }

    return (jint) loadOperation;
}

void startLoadOperation(JNIEnv *env, jclass c, jint address) {
    if (address != 0) {
        ((FileLoadOperation *) address)->start();
    }
}

void cancelLoadOperation(JNIEnv *env, jclass c, jint address) {
    if (address != 0) {
        ((FileLoadOperation *) address)->cancel();
    }
}

static const char *FileLoadOperationClassPathName = "org/telegram/tgnet/FileLoadOperation";
static JNINativeMethod FileLoadOperationMethods[] = {
        {"native_createLoadOpetation", "(IJJJI[B[BLjava/lang/String;IILjava/lang/String;Ljava/lang/String;Ljava/lang/Object;)I", (void *) createLoadOpetation},
        {"native_startLoadOperation", "(I)V", (void *) startLoadOperation},
        {"native_cancelLoadOperation", "(I)V", (void *) cancelLoadOperation}
};

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

jint isTestBackend(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().isTestBackend() ? 1 : 0;
}

jint getTimeDifference(JNIEnv *env, jclass c) {
    return ConnectionsManager::getInstance().getTimeDifference();
}

void sendRequest(JNIEnv *env, jclass c, jint object, jobject onComplete, jobject onQuickAck, jobject onWriteToSocket, jint flags, jint datacenterId, jint connetionType, jboolean immediate, jint token) {
    TL_api_request *request = new TL_api_request();
    request->request = (NativeByteBuffer *) object;
    if (onComplete != nullptr) {
        onComplete = env->NewGlobalRef(onComplete);
    }
    if (onQuickAck != nullptr) {
        onQuickAck = env->NewGlobalRef(onQuickAck);
    }
    if (onWriteToSocket != nullptr) {
        onWriteToSocket = env->NewGlobalRef(onWriteToSocket);
    }
    ConnectionsManager::getInstance().sendRequest(request, ([onComplete](TLObject *response, TL_error *error, int32_t networkType) {
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
            jniEnv->CallVoidMethod(onComplete, jclass_RequestDelegateInternal_run, ptr, errorCode, errorText, networkType);
        }
        if (errorText != nullptr) {
            jniEnv->DeleteLocalRef(errorText);
        }
    }), ([onQuickAck] {
        if (onQuickAck != nullptr) {
            jniEnv->CallVoidMethod(onQuickAck, jclass_QuickAckDelegate_run);
        }
    }), ([onWriteToSocket] {
        if (onWriteToSocket != nullptr) {
            jniEnv->CallVoidMethod(onWriteToSocket, jclass_WriteToSocketDelegate_run);
        }
    }), flags, datacenterId, (ConnectionType) connetionType, immediate, token, onComplete, onQuickAck, onWriteToSocket);
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

void setProxySettings(JNIEnv *env, jclass c, jstring address, jint port, jstring username, jstring password) {
    const char *addressStr = env->GetStringUTFChars(address, 0);
    const char *usernameStr = env->GetStringUTFChars(username, 0);
    const char *passwordStr = env->GetStringUTFChars(password, 0);

    ConnectionsManager::getInstance().setProxySettings(addressStr, (uint16_t) port, usernameStr, passwordStr);

    if (addressStr != 0) {
        env->ReleaseStringUTFChars(address, addressStr);
    }
    if (usernameStr != 0) {
        env->ReleaseStringUTFChars(username, usernameStr);
    }
    if (passwordStr != 0) {
        env->ReleaseStringUTFChars(password, passwordStr);
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
    ConnectionsManager::getInstance().updateDcSettings(0, false);
}

void setUseIpv6(JNIEnv *env, jclass c, bool value) {
    ConnectionsManager::getInstance().setUseIpv6(value);
}

void setNetworkAvailable(JNIEnv *env, jclass c, jboolean value, jint networkType) {
    ConnectionsManager::getInstance().setNetworkAvailable(value, networkType);
}

void setPushConnectionEnabled(JNIEnv *env, jclass c, jboolean value) {
    ConnectionsManager::getInstance().setPushConnectionEnabled(value);
}

void applyDnsConfig(JNIEnv *env, jclass c, jint address) {
    ConnectionsManager::getInstance().applyDnsConfig((NativeByteBuffer *) address);
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

    void onBytesReceived(int32_t amount, int32_t networkType) {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onBytesReceived, amount, networkType);
    }

    void onBytesSent(int32_t amount, int32_t networkType) {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onBytesSent, amount, networkType);
    }

    void onRequestNewServerIpAndPort(int32_t second) {
        jniEnv->CallStaticVoidMethod(jclass_ConnectionsManager, jclass_ConnectionsManager_onRequestNewServerIpAndPort, second);
    }
};

void setLangCode(JNIEnv *env, jclass c, jstring langCode) {
    const char *langCodeStr = env->GetStringUTFChars(langCode, 0);

    ConnectionsManager::getInstance().setLangCode(std::string(langCodeStr));

    if (langCodeStr != 0) {
        env->ReleaseStringUTFChars(langCode, langCodeStr);
    }
}

void init(JNIEnv *env, jclass c, jint version, jint layer, jint apiId, jstring deviceModel, jstring systemVersion, jstring appVersion, jstring langCode, jstring systemLangCode, jstring configPath, jstring logPath, jint userId, jboolean enablePushConnection, jboolean hasNetwork, jint networkType) {
    const char *deviceModelStr = env->GetStringUTFChars(deviceModel, 0);
    const char *systemVersionStr = env->GetStringUTFChars(systemVersion, 0);
    const char *appVersionStr = env->GetStringUTFChars(appVersion, 0);
    const char *langCodeStr = env->GetStringUTFChars(langCode, 0);
    const char *systemLangCodeStr = env->GetStringUTFChars(systemLangCode, 0);
    const char *configPathStr = env->GetStringUTFChars(configPath, 0);
    const char *logPathStr = env->GetStringUTFChars(logPath, 0);

    ConnectionsManager::getInstance().init(version, layer, apiId, std::string(deviceModelStr), std::string(systemVersionStr), std::string(appVersionStr), std::string(langCodeStr), std::string(systemLangCodeStr), std::string(configPathStr), std::string(logPathStr), userId, true, enablePushConnection, hasNetwork, networkType);

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
}

void setJava(JNIEnv *env, jclass c, jboolean useJavaByteBuffers) {
    ConnectionsManager::useJavaVM(java, useJavaByteBuffers);
    ConnectionsManager::getInstance().setDelegate(new Delegate());
}

static const char *ConnectionsManagerClassPathName = "org/telegram/tgnet/ConnectionsManager";
static JNINativeMethod ConnectionsManagerMethods[] = {
        {"native_getCurrentTimeMillis", "()J", (void *) getCurrentTimeMillis},
        {"native_getCurrentTime", "()I", (void *) getCurrentTime},
        {"native_isTestBackend", "()I", (void *) isTestBackend},
        {"native_getTimeDifference", "()I", (void *) getTimeDifference},
        {"native_sendRequest", "(ILorg/telegram/tgnet/RequestDelegateInternal;Lorg/telegram/tgnet/QuickAckDelegate;Lorg/telegram/tgnet/WriteToSocketDelegate;IIIZI)V", (void *) sendRequest},
        {"native_cancelRequest", "(IZ)V", (void *) cancelRequest},
        {"native_cleanUp", "()V", (void *) cleanUp},
        {"native_cancelRequestsForGuid", "(I)V", (void *) cancelRequestsForGuid},
        {"native_bindRequestToGuid", "(II)V", (void *) bindRequestToGuid},
        {"native_applyDatacenterAddress", "(ILjava/lang/String;I)V", (void *) applyDatacenterAddress},
        {"native_setProxySettings", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V", (void *) setProxySettings},
        {"native_getConnectionState", "()I", (void *) getConnectionState},
        {"native_setUserId", "(I)V", (void *) setUserId},
        {"native_init", "(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZZI)V", (void *) init},
        {"native_setLangCode", "(Ljava/lang/String;)V", (void *) setLangCode},
        {"native_switchBackend", "()V", (void *) switchBackend},
        {"native_pauseNetwork", "()V", (void *) pauseNetwork},
        {"native_resumeNetwork", "(Z)V", (void *) resumeNetwork},
        {"native_updateDcSettings", "()V", (void *) updateDcSettings},
        {"native_setUseIpv6", "(Z)V", (void *) setUseIpv6},
        {"native_setNetworkAvailable", "(ZI)V", (void *) setNetworkAvailable},
        {"native_setPushConnectionEnabled", "(Z)V", (void *) setPushConnectionEnabled},
        {"native_setJava", "(Z)V", (void *) setJava},
        {"native_applyDnsConfig", "(I)V", (void *) applyDnsConfig}
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

    if (!registerNativeMethods(env, FileLoadOperationClassPathName, FileLoadOperationMethods, sizeof(FileLoadOperationMethods) / sizeof(FileLoadOperationMethods[0]))) {
        return JNI_FALSE;
    }
    
    if (!registerNativeMethods(env, ConnectionsManagerClassPathName, ConnectionsManagerMethods, sizeof(ConnectionsManagerMethods) / sizeof(ConnectionsManagerMethods[0]))) {
        return JNI_FALSE;
    }
    
    jclass_RequestDelegateInternal = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/RequestDelegateInternal"));
    if (jclass_RequestDelegateInternal == 0) {
        return JNI_FALSE;
    }
    jclass_RequestDelegateInternal_run = env->GetMethodID(jclass_RequestDelegateInternal, "run", "(IILjava/lang/String;I)V");
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

    jclass_WriteToSocketDelegate = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/WriteToSocketDelegate"));
    if (jclass_WriteToSocketDelegate == 0) {
        return JNI_FALSE;
    }
    jclass_WriteToSocketDelegate_run = env->GetMethodID(jclass_WriteToSocketDelegate, "run", "()V");
    if (jclass_WriteToSocketDelegate_run == 0) {
        return JNI_FALSE;
    }

    jclass_FileLoadOperationDelegate = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/tgnet/FileLoadOperationDelegate"));
    if (jclass_FileLoadOperationDelegate == 0) {
        return JNI_FALSE;
    }

    jclass_FileLoadOperationDelegate_onFinished = env->GetMethodID(jclass_FileLoadOperationDelegate, "onFinished", "(Ljava/lang/String;)V");
    if (jclass_FileLoadOperationDelegate_onFinished == 0) {
        return JNI_FALSE;
    }

    jclass_FileLoadOperationDelegate_onFailed = env->GetMethodID(jclass_FileLoadOperationDelegate, "onFailed", "(I)V");
    if (jclass_FileLoadOperationDelegate_onFailed == 0) {
        return JNI_FALSE;
    }

    jclass_FileLoadOperationDelegate_onProgressChanged = env->GetMethodID(jclass_FileLoadOperationDelegate, "onProgressChanged", "(F)V");
    if (jclass_FileLoadOperationDelegate_onProgressChanged == 0) {
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
    jclass_ConnectionsManager_onBytesSent = env->GetStaticMethodID(jclass_ConnectionsManager, "onBytesSent", "(II)V");
    if (jclass_ConnectionsManager_onBytesSent == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onBytesReceived = env->GetStaticMethodID(jclass_ConnectionsManager, "onBytesReceived", "(II)V");
    if (jclass_ConnectionsManager_onBytesReceived == 0) {
        return JNI_FALSE;
    }
    jclass_ConnectionsManager_onRequestNewServerIpAndPort = env->GetStaticMethodID(jclass_ConnectionsManager, "onRequestNewServerIpAndPort", "(I)V");
    if (jclass_ConnectionsManager_onRequestNewServerIpAndPort == 0) {
        return JNI_FALSE;
    }
    ConnectionsManager::getInstance().setDelegate(new Delegate());
    
    return JNI_TRUE;
}
