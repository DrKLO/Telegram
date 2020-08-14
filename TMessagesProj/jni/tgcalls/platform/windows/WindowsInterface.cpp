#include "WindowsInterface.h"

#include "platform/tdesktop/VideoCapturerInterfaceImpl.h"
#include "platform/tdesktop/VideoCapturerTrackSource.h"

#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_track_source_proxy.h"

namespace tgcalls {

std::unique_ptr<webrtc::VideoEncoderFactory> WindowsInterface::makeVideoEncoderFactory() {
	return webrtc::CreateBuiltinVideoEncoderFactory();
}

std::unique_ptr<webrtc::VideoDecoderFactory> WindowsInterface::makeVideoDecoderFactory() {
	return webrtc::CreateBuiltinVideoDecoderFactory();
}

rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> WindowsInterface::makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread) {
	const auto videoTrackSource = VideoCapturerTrackSource::Create();
	return webrtc::VideoTrackSourceProxy::Create(signalingThread, workerThread, videoTrackSource);
}

bool WindowsInterface::supportsEncoding(const std::string &codecName) {
	return (codecName == cricket::kH264CodecName)
		|| (codecName == cricket::kVp8CodecName);
}

std::unique_ptr<VideoCapturerInterface> WindowsInterface::makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext) {
	return std::make_unique<VideoCapturerInterfaceImpl>(source, useFrontCamera, stateUpdated);
}

std::unique_ptr<PlatformInterface> CreatePlatformInterface() {
	return std::make_unique<WindowsInterface>();
}

} // namespace tgcalls
