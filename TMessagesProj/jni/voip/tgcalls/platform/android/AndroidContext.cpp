#include <tgnet/FileLog.h>
#include "AndroidContext.h"

#include "sdk/android/native_api/jni/jvm.h"

namespace tgcalls {

AndroidContext::AndroidContext(JNIEnv *env, jobject instance) {
    VideoCameraCapturerClass = (jclass) env->NewGlobalRef(env->FindClass("org/telegram/messenger/voip/VideoCameraCapturer"));
    jmethodID initMethodId = env->GetMethodID(VideoCameraCapturerClass, "<init>", "()V");
    javaCapturer = env->NewGlobalRef(env->NewObject(VideoCameraCapturerClass, initMethodId));
    javaInstance = env->NewGlobalRef(instance);
}

AndroidContext::~AndroidContext() {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    jmethodID onDestroyMethodId = env->GetMethodID(VideoCameraCapturerClass, "onDestroy", "()V");
    env->CallVoidMethod(javaCapturer, onDestroyMethodId);
    env->DeleteGlobalRef(javaCapturer);
    javaCapturer = nullptr;

    env->DeleteGlobalRef(VideoCameraCapturerClass);

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
    return VideoCameraCapturerClass;
}

}  // namespace tgcalls
