#include "AndroidContext.h"

#include "sdk/android/native_api/jni/jvm.h"

namespace tgcalls {

AndroidContext::AndroidContext(JNIEnv *env) {
    VideoCameraCapturerClass = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/VideoCameraCapturer"));
    jmethodID initMethodId = env->GetMethodID(VideoCameraCapturerClass, "<init>", "()V");
    javaCapturer = env->NewGlobalRef(env->NewObject(VideoCameraCapturerClass, initMethodId));
}

AndroidContext::~AndroidContext() {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    jmethodID onDestroyMethodId = env->GetMethodID(VideoCameraCapturerClass, "onDestroy", "()V");
    env->CallVoidMethod(javaCapturer, onDestroyMethodId);
    env->DeleteGlobalRef(javaCapturer);
    javaCapturer = nullptr;

    env->DeleteGlobalRef(VideoCameraCapturerClass);
}

jobject AndroidContext::getJavaCapturer() {
    return javaCapturer;
}

jclass AndroidContext::getJavaCapturerClass() {
    return VideoCameraCapturerClass;
}

}  // namespace tgcalls
