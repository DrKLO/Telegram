#include "VideoCapturerInterfaceImpl.h"

#include "VideoCapturerTrackSource.h"
#include "VideoCameraCapturer.h"

#include "api/video_track_source_proxy.h"

namespace tgcalls {
namespace {

static VideoCameraCapturer *GetCapturer(
	const rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> nativeSource) {
	const auto proxy = static_cast<webrtc::VideoTrackSourceProxy*>(nativeSource.get());
	const auto internal = static_cast<VideoCapturerTrackSource*>(proxy->internal());
	return internal->capturer();
}

} // namespace

VideoCapturerInterfaceImpl::VideoCapturerInterfaceImpl(
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source,
	bool useFrontCamera,
	std::function<void(VideoState)> stateUpdated)
: _source(source)
, _stateUpdated(stateUpdated) {
}

VideoCapturerInterfaceImpl::~VideoCapturerInterfaceImpl() {
}

void VideoCapturerInterfaceImpl::setState(VideoState state) {
	GetCapturer(_source)->setState(state);
	if (_stateUpdated) {
		_stateUpdated(state);
	}
}

void VideoCapturerInterfaceImpl::setPreferredCaptureAspectRatio(float aspectRatio) {
	GetCapturer(_source)->setPreferredCaptureAspectRatio(aspectRatio);
}

void VideoCapturerInterfaceImpl::setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	if (_uncroppedSink != nullptr) {
		_source->RemoveSink(_uncroppedSink.get());
	}
	_uncroppedSink = sink;
	if (_uncroppedSink != nullptr) {
		_source->AddOrUpdateSink(_uncroppedSink.get(), rtc::VideoSinkWants());
	}
}

} // namespace tgcalls
