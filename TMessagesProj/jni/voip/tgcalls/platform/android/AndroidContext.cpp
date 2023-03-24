#include <tgnet/FileLog.h>
#include "AndroidContext.h"

#include "sdk/android/native_api/jni/jvm.h"

namespace tgcalls {

AndroidContext::AndroidContext(JNIEnv *env, jobject instance, bool screencast) {
    DEBUG_REF("VideoCapturerDevice");
    VideoCapturerDeviceClass = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/VideoCapturerDevice"));
    jmethodID initMethodId = env->GetMethodID(VideoCapturerDeviceClass, "<init>", "(Z)V");
    DEBUG_REF("VideoCapturerDevice javaCapturer");
    javaCapturer = env->NewGlobalRef(env->NewObject(VideoCapturerDeviceClass, initMethodId, screencast));
    DEBUG_REF("VideoCapturerDevice javaInstance");
    javaInstance = env->NewGlobalRef(instance);
}

AndroidContext::~AndroidContext() {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    jmethodID onDestroyMethodId = env->GetMethodID(VideoCapturerDeviceClass, "onDestroy", "()V");
    env->CallVoidMethod(javaCapturer, onDestroyMethodId);
    DEBUG_DELREF("javaCapturer");
    env->DeleteGlobalRef(javaCapturer);
    javaCapturer = nullptr;

    DEBUG_DELREF("VideoCapturerDeviceClass");
    env->DeleteGlobalRef(VideoCapturerDeviceClass);

    if (javaInstance) {
        DEBUG_DELREF("javaInstance");
        env->DeleteGlobalRef(javaInstance);
    }
}

void AndroidContext::setJavaInstance(JNIEnv *env, jobject instance) {
    DEBUG_REF("setJavaInstance");
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
