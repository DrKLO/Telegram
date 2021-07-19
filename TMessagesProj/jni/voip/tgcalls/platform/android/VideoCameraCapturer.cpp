#include "VideoCameraCapturer.h"

#include <cstdint>
#include <memory>
#include <algorithm>

#include "AndroidInterface.h"
#include "AndroidContext.h"
#include "sdk/android/native_api/jni/jvm.h"

namespace tgcalls {

VideoCameraCapturer::VideoCameraCapturer(rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext) : _source(source), _stateUpdated(stateUpdated), _platformContext(platformContext) {
    AndroidContext *context = (AndroidContext *) platformContext.get();
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    jmethodID methodId = env->GetMethodID(context->getJavaCapturerClass(), "init", "(JLjava/lang/String;)V");
    env->CallVoidMethod(context->getJavaCapturer(), methodId, (jlong) (intptr_t) this, env->NewStringUTF(deviceId.c_str()));
}

void VideoCameraCapturer::setState(VideoState state) {
    _state = state;
    if (_stateUpdated) {
        _stateUpdated(_state);
    }
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    auto context = (AndroidContext *) _platformContext.get();
    jmethodID methodId = env->GetMethodID(context->getJavaCapturerClass(), "onStateChanged", "(JI)V");
    env->CallVoidMethod(context->getJavaCapturer(), methodId, (jlong) (intptr_t) this, (jint) state);
}

void VideoCameraCapturer::setPreferredCaptureAspectRatio(float aspectRatio) {
    _aspectRatio = aspectRatio;
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    auto context = (AndroidContext *) _platformContext.get();
    jmethodID methodId = env->GetMethodID(context->getJavaCapturerClass(), "onAspectRatioRequested", "(F)V");
    env->CallVoidMethod(context->getJavaCapturer(), methodId, (jfloat) aspectRatio);
}

void VideoCameraCapturer::setUncroppedSink(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    if (_uncroppedSink != nullptr) {
        _source->RemoveSink(_uncroppedSink.get());
    }
    if (sink != nullptr) {
        _source->AddOrUpdateSink(sink.get(), rtc::VideoSinkWants());
    }
    _uncroppedSink = sink;
}

webrtc::ScopedJavaLocalRef<jobject> VideoCameraCapturer::GetJavaVideoCapturerObserver(JNIEnv *env) {
    return _source->GetJavaVideoCapturerObserver(env);
}

}  // namespace tgcalls

extern "C" {

JNIEXPORT jobject Java_org_telegram_messenger_voip_VideoCapturerDevice_nativeGetJavaVideoCapturerObserver(JNIEnv *env, jclass clazz, jlong ptr) {
    tgcalls::VideoCameraCapturer *capturer = (tgcalls::VideoCameraCapturer *) (intptr_t) ptr;
    return capturer->GetJavaVideoCapturerObserver(env).Release();
}

}