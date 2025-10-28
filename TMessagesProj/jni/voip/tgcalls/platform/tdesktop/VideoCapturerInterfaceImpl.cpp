#include "tgcalls/platform/tdesktop/VideoCapturerInterfaceImpl.h"

#include "tgcalls/platform/tdesktop/VideoCapturerTrackSource.h"
#include "tgcalls/platform/tdesktop/VideoCameraCapturer.h"

#ifndef TGCALLS_UWP_DESKTOP
#include "tgcalls/desktop_capturer/DesktopCaptureSourceHelper.h"
#endif // TGCALLS_DISABLE_DESKTOP_CAPTURE

#include "pc/video_track_source_proxy.h"

namespace tgcalls {
namespace {

std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> GetSink(
	const rtc::scoped_refptr<
		webrtc::VideoTrackSourceInterface> &nativeSource) {
	const auto proxy = static_cast<webrtc::VideoTrackSourceProxy*>(
		nativeSource.get());
	const auto internal = static_cast<VideoCapturerTrackSource*>(
		proxy->internal());
	return internal->sink();
}

} // namespace

VideoCapturerInterfaceImpl::VideoCapturerInterfaceImpl(
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source,
	std::string deviceId,
	std::function<void(VideoState)> stateUpdated,
	std::shared_ptr<PlatformContext> platformContext,
	std::pair<int, int> &outResolution)
: _source(source)
, _sink(GetSink(source))
, _stateUpdated(stateUpdated) {
#ifdef TGCALLS_UWP_DESKTOP
	if (deviceId == "GraphicsCaptureItem")
	{
		auto uwpContext = std::static_pointer_cast<UwpContext>(platformContext);

		_screenCapturer = std::make_unique<UwpScreenCapturer>(_sink, uwpContext->item);
		_screenCapturer->setState(VideoState::Active);
		outResolution = _screenCapturer->resolution();
	}
	else
#else
	if (const auto source = DesktopCaptureSourceForKey(deviceId)) {
		const auto data = DesktopCaptureSourceData{
			/*.aspectSize = */{ 1280, 720 },
			/*.fps = */24.,
			/*.captureMouse = */true,
		};
		_desktopCapturer = std::make_unique<DesktopCaptureSourceHelper>(
			source,
			data);
		_desktopCapturer->setOutput(_sink);
		_desktopCapturer->start();
		outResolution = { 1280, 960 };
	} else if (!ShouldBeDesktopCapture(deviceId))
#endif // TGCALLS_UWP_DESKTOP
	{
		_cameraCapturer = std::make_unique<VideoCameraCapturer>(_sink);
		_cameraCapturer->setDeviceId(deviceId);
		_cameraCapturer->setState(VideoState::Active);
		outResolution = _cameraCapturer->resolution();
	}
}

VideoCapturerInterfaceImpl::~VideoCapturerInterfaceImpl() {
}

void VideoCapturerInterfaceImpl::setState(VideoState state) {
#ifdef TGCALLS_UWP_DESKTOP
	if (_screenCapturer) {
		_screenCapturer->setState(state);
	} else
#else
	if (_desktopCapturer) {
		if (state == VideoState::Active) {
			_desktopCapturer->start();
		} else {
			_desktopCapturer->stop();
		}
	} else
#endif // TGCALLS_UWP_DESKTOP
	if (_cameraCapturer) {
		_cameraCapturer->setState(state);
	}
	if (_stateUpdated) {
		_stateUpdated(state);
	}
}

void VideoCapturerInterfaceImpl::setPreferredCaptureAspectRatio(
		float aspectRatio) {
	if (_cameraCapturer) {
		_cameraCapturer->setPreferredCaptureAspectRatio(aspectRatio);
	}
}

void VideoCapturerInterfaceImpl::setUncroppedOutput(
		std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	if (_uncroppedSink != nullptr) {
		_source->RemoveSink(_uncroppedSink.get());
	}
	_uncroppedSink = sink;
	if (_uncroppedSink != nullptr) {
		_source->AddOrUpdateSink(
			_uncroppedSink.get(),
			rtc::VideoSinkWants());
	}
}

void VideoCapturerInterfaceImpl::setOnFatalError(std::function<void()> error) {
#ifdef TGCALLS_UWP_DESKTOP
	if (_screenCapturer) {
		_screenCapturer->setOnFatalError(std::move(error));
	} else if (!_screenCapturer && !_cameraCapturer && error) {
		error();
	}
#else // TGCALLS_UWP_DESKTOP
	if (_desktopCapturer) {
		_desktopCapturer->setOnFatalError(std::move(error));
	} else if (!_desktopCapturer && !_cameraCapturer && error) {
		error();
	}
#endif // TGCALLS_UWP_DESKTOP
	if (_cameraCapturer) {
		_cameraCapturer->setOnFatalError(std::move(error));
	}
}

void VideoCapturerInterfaceImpl::setOnPause(std::function<void(bool)> pause) {
#ifdef TGCALLS_UWP_DESKTOP
	if (_screenCapturer) {
		_screenCapturer->setOnPause(std::move(pause));
	}
#else // TGCALLS_UWP_DESKTOP
	if (_desktopCapturer) {
		_desktopCapturer->setOnPause(std::move(pause));
	}
#endif // TGCALLS_UWP_DESKTOP
}

} // namespace tgcalls
