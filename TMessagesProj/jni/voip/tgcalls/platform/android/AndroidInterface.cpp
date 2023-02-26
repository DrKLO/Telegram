#include "AndroidInterface.h"

#include <rtc_base/ssl_adapter.h>
#include <modules/utility/include/jvm_android.h>
#include <sdk/android/src/jni/android_video_track_source.h>
#include <sdk/android/src/jni/android_video_track_source.h>
#include <sdk/android/src/jni/pc/android_network_monitor.h>
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
#include "api/video_track_source_proxy_factory.h"
#include "AndroidContext.h"


namespace tgcalls {

void AndroidInterface::configurePlatformAudio(int numChannels) {

}

std::unique_ptr<webrtc::VideoEncoderFactory> AndroidInterface::makeVideoEncoderFactory(std::shared_ptr<PlatformContext> platformContext,  bool preferHardwareEncoding, bool isScreencast) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    AndroidContext *context = (AndroidContext *) platformContext.get();
    jmethodID methodId = env->GetMethodID(context->getJavaCapturerClass(), "getSharedEGLContext", "()Lorg/webrtc/EglBase$Context;");
    jobject eglContext = env->CallObjectMethod(context->getJavaCapturer(), methodId);

    webrtc::ScopedJavaLocalRef<jclass> factory_class = webrtc::GetClass(env, "org/webrtc/DefaultVideoEncoderFactory");
    jmethodID factory_constructor = env->GetMethodID(factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;ZZ)V");
    webrtc::ScopedJavaLocalRef<jobject> factory_object(env, env->NewObject(factory_class.obj(), factory_constructor, eglContext, false, true));
    return webrtc::JavaToNativeVideoEncoderFactory(env, factory_object.obj());
}

std::unique_ptr<webrtc::VideoDecoderFactory> AndroidInterface::makeVideoDecoderFactory(std::shared_ptr<PlatformContext> platformContext) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

    AndroidContext *context = (AndroidContext *) platformContext.get();
    jmethodID methodId = env->GetMethodID(context->getJavaCapturerClass(), "getSharedEGLContext", "()Lorg/webrtc/EglBase$Context;");
    jobject eglContext = env->CallObjectMethod(context->getJavaCapturer(), methodId);

    webrtc::ScopedJavaLocalRef<jclass> factory_class = webrtc::GetClass(env, "org/webrtc/DefaultVideoDecoderFactory");
    jmethodID factory_constructor = env->GetMethodID(factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;)V");
    webrtc::ScopedJavaLocalRef<jobject> factory_object(env, env->NewObject(factory_class.obj(), factory_constructor, eglContext));
    return webrtc::JavaToNativeVideoDecoderFactory(env, factory_object.obj());
}

void AndroidInterface::adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height, int fps) {

}

rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> AndroidInterface::makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread, bool screencapture) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    _source[screencapture ? 1 : 0] = webrtc::CreateJavaVideoSource(env, signalingThread, false, false);
    return webrtc::CreateVideoTrackSourceProxy(signalingThread, workerThread, _source[screencapture ? 1 : 0].get());
}

bool AndroidInterface::supportsEncoding(const std::string &codecName, std::shared_ptr<PlatformContext> platformContext) {
    if (hardwareVideoEncoderFactory == nullptr) {
        JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();

        AndroidContext *context = (AndroidContext *) platformContext.get();
        jmethodID methodId = env->GetMethodID(context->getJavaCapturerClass(), "getSharedEGLContext", "()Lorg/webrtc/EglBase$Context;");
        jobject eglContext = env->CallObjectMethod(context->getJavaCapturer(), methodId);

        webrtc::ScopedJavaLocalRef<jclass> factory_class = webrtc::GetClass(env, "org/webrtc/HardwareVideoEncoderFactory");
        jmethodID factory_constructor = env->GetMethodID(factory_class.obj(), "<init>", "(Lorg/webrtc/EglBase$Context;ZZ)V");
        webrtc::ScopedJavaLocalRef<jobject> factory_object(env, env->NewObject(factory_class.obj(), factory_constructor, eglContext, false, true));
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

std::unique_ptr<VideoCapturerInterface> AndroidInterface::makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated, std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) {
    return std::make_unique<VideoCapturerInterfaceImpl>(_source[deviceId == "screen" ? 1 : 0], deviceId, stateUpdated, platformContext);
}

std::unique_ptr<rtc::NetworkMonitorFactory> AndroidInterface::createNetworkMonitorFactory() {
    return std::make_unique<webrtc::jni::AndroidNetworkMonitorFactory>();
}

std::unique_ptr<PlatformInterface> CreatePlatformInterface() {
	return std::make_unique<AndroidInterface>();
}

} // namespace tgcalls
