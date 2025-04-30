#include "AndroidContext.h"

#include "sdk/android/native_api/jni/jvm.h"
#include "tgnet/FileLog.h"

namespace tgcalls {

AndroidContext::AndroidContext(JNIEnv *env, jobject peerInstance, jobject groupInstance, bool screencast) {
    DEBUG_D("new AndroidContext");
    VideoCapturerDeviceClass = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/VideoCapturerDevice"));
    jmethodID initMethodId = env->GetMethodID(VideoCapturerDeviceClass, "<init>", "(Z)V");
    javaCapturer = env->NewGlobalRef(env->NewObject(VideoCapturerDeviceClass, initMethodId, screencast));
    if (peerInstance) {
        javaPeerInstance = env->NewGlobalRef(peerInstance);
    }
    if (groupInstance) {
        javaGroupInstance = env->NewGlobalRef(groupInstance);
    }
}

AndroidContext::~AndroidContext() {
    DEBUG_D("~AndroidContext");
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    jmethodID onDestroyMethodId = env->GetMethodID(VideoCapturerDeviceClass, "onDestroy", "()V");
    env->CallVoidMethod(javaCapturer, onDestroyMethodId);
    env->DeleteGlobalRef(javaCapturer);
    javaCapturer = nullptr;

    env->DeleteGlobalRef(VideoCapturerDeviceClass);

    if (javaPeerInstance) {
        env->DeleteGlobalRef(javaPeerInstance);
    }
    if (javaGroupInstance) {
        env->DeleteGlobalRef(javaGroupInstance);
    }
}

void AndroidContext::setJavaPeerInstance(JNIEnv *env, jobject instance) {
    javaPeerInstance = env->NewGlobalRef(instance);
}

void AndroidContext::setJavaGroupInstance(JNIEnv *env, jobject instance) {
    javaGroupInstance = env->NewGlobalRef(instance);
}

jobject AndroidContext::getJavaPeerInstance() {
    return javaPeerInstance;
}

jobject AndroidContext::getJavaGroupInstance() {
    return javaGroupInstance;
}

jobject AndroidContext::getJavaCapturer() {
    return javaCapturer;
}

jclass AndroidContext::getJavaCapturerClass() {
    return VideoCapturerDeviceClass;
}

}  // namespace tgcalls
