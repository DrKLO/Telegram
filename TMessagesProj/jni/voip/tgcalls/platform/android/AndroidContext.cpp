#include <tgnet/FileLog.h>
#include "AndroidContext.h"

#include "sdk/android/native_api/jni/jvm.h"

namespace tgcalls {

AndroidContext::AndroidContext(JNIEnv *env, jobject instance, bool screencast) {
    VideoCapturerDeviceClass = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/VideoCapturerDevice"));
    jmethodID initMethodId = env->GetMethodID(VideoCapturerDeviceClass, "<init>", "(Z)V");
    javaCapturer = env->NewGlobalRef(env->NewObject(VideoCapturerDeviceClass, initMethodId, screencast));
    javaInstance = env->NewGlobalRef(instance);
}

AndroidContext::~AndroidContext() {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    jmethodID onDestroyMethodId = env->GetMethodID(VideoCapturerDeviceClass, "onDestroy", "()V");
    env->CallVoidMethod(javaCapturer, onDestroyMethodId);
    env->DeleteGlobalRef(javaCapturer);
    javaCapturer = nullptr;

    env->DeleteGlobalRef(VideoCapturerDeviceClass);

    if (javaInstance) {
        env->DeleteGlobalRef(javaInstance);
    }
}

void AndroidContext::setJavaInstance(JNIEnv *env, jobject instance) {
    javaInstance = env->NewGlobalRef(instance);
}

jobject AndroidContext::getJavaInstance() {
    return javaInstance;
}

jobject AndroidContext::getJavaCapturer() {
    return javaCapturer;
}

jclass AndroidContext::getJavaCapturerClass() {
    return VideoCapturerDeviceClass;
}

}  // namespace tgcalls
