#include "VideoCapturerInterfaceImpl.h"

#include "VideoCameraCapturer.h"

namespace tgcalls {

VideoCapturerInterfaceImpl::VideoCapturerInterfaceImpl(rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext) {
	_capturer = std::unique_ptr<VideoCameraCapturer>(new VideoCameraCapturer(source, useFrontCamera, stateUpdated, platformContext));
}

void VideoCapturerInterfaceImpl::setState(VideoState state) {
	_capturer->setState(state);
}

void VideoCapturerInterfaceImpl::setPreferredCaptureAspectRatio(float aspectRatio) {
	_capturer->setPreferredCaptureAspectRatio(aspectRatio);
}

void VideoCapturerInterfaceImpl::setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	_capturer->setUncroppedSink(sink);
}

} // namespace tgcalls
