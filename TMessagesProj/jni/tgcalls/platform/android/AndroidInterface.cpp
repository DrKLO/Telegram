#include "AndroidInterface.h"

#include <rtc_base/ssl_adapter.h>
#include <modules/utility/include/jvm_android.h>
#include <sdk/android/src/jni/android_video_track_source.h>
#include <media/base/media_constants.h>

#include "VideoCapturerInterfaceImpl.h"

#include "sdk/android/native_api/base/init.h"
#include "sdk/android/native_api/codecs/wrapper.h"
#include "sdk/android/native_api/jni/class_loader.h"
#include "sdk/android/native_api/jni/jvm.h"
#include "sdk/android/native_api/jni/scoped_java_ref.h"
#include "sdk/android/native_api/video/video_source.h"
#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_track_source_proxy.h"


namespace tgcalls {

void AndroidInterface::configurePlatformAudio() {

}

float AndroidInterface::getDisplayAspectRatio() {
    return 0;
}

std::unique_ptr<webrtc::VideoEncoderFactory> AndroidInterface::makeVideoEncoderFactory() {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    webrtc::ScopedJavaLocalRef<jclass> factory_class =
            webrtc::GetClass(env, "org/webrtc/DefaultVideoEncoderFactory");
    jmethodID factory_constructor = env->GetMethodID(
            factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;ZZ)V");
    webrtc::ScopedJavaLocalRef<jobject> factory_object(
            env, env->NewObject(factory_class.obj(), factory_constructor,
                                nullptr /* shared_context */,
                                false /* enable_intel_vp8_encoder */,
                                true /* enable_h264_high_profile */));
    return webrtc::JavaToNativeVideoEncoderFactory(env, factory_object.obj());
}

std::unique_ptr<webrtc::VideoDecoderFactory> AndroidInterface::makeVideoDecoderFactory() {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    webrtc::ScopedJavaLocalRef<jclass> factory_class =
            webrtc::GetClass(env, "org/webrtc/DefaultVideoDecoderFactory");
    jmethodID factory_constructor = env->GetMethodID(
            factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;)V");
    webrtc::ScopedJavaLocalRef<jobject> factory_object(
            env, env->NewObject(factory_class.obj(), factory_constructor,
                                nullptr /* shared_context */));
    return webrtc::JavaToNativeVideoDecoderFactory(env, factory_object.obj());
}

void AndroidInterface::adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height, int fps) {

}

rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> AndroidInterface::makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    _source = webrtc::CreateJavaVideoSource(env, signalingThread, false, false);
    return webrtc::VideoTrackSourceProxy::Create(signalingThread, workerThread, _source);
}

bool AndroidInterface::supportsEncoding(const std::string &codecName) {
    if (hardwareVideoEncoderFactory == nullptr) {
        JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
        webrtc::ScopedJavaLocalRef<jclass> factory_class =
                webrtc::GetClass(env, "org/webrtc/HardwareVideoEncoderFactory");
        jmethodID factory_constructor = env->GetMethodID(
                factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;ZZ)V");
        webrtc::ScopedJavaLocalRef<jobject> factory_object(
                env, env->NewObject(factory_class.obj(), factory_constructor,
                                    nullptr,
                                    false,
                                    true));
        hardwareVideoEncoderFactory = webrtc::JavaToNativeVideoEncoderFactory(env, factory_object.obj());
    }
    auto formats = hardwareVideoEncoderFactory->GetSupportedFormats();
    for (auto format : formats) {
        if (format.name == codecName) {
            return true;
        }
    }
    return codecName == cricket::kVp8CodecName;
}

std::unique_ptr<VideoCapturerInterface> AndroidInterface::makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) {
    return std::make_unique<VideoCapturerInterfaceImpl>(_source, useFrontCamera, stateUpdated, platformContext);
}


std::unique_ptr<PlatformInterface> CreatePlatformInterface() {
	return std::make_unique<AndroidInterface>();
}

} // namespace tgcalls
