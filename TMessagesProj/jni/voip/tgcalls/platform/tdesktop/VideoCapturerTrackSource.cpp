#include "VideoCapturerTrackSource.h"

#include "VideoCameraCapturer.h"

#include "modules/video_capture/video_capture_factory.h"

namespace tgcalls {

rtc::scoped_refptr<VideoCapturerTrackSource> VideoCapturerTrackSource::Create() {
	const size_t kWidth = 640;
	const size_t kHeight = 480;
	const size_t kFps = 30;

	std::unique_ptr<webrtc::VideoCaptureModule::DeviceInfo> info(
		webrtc::VideoCaptureFactory::CreateDeviceInfo());
	if (!info) {
		return nullptr;
	}
	int num_devices = info->NumberOfDevices();

	for (int i = 0; i < num_devices; ++i) {
		if (auto capturer = VideoCameraCapturer::Create(kWidth, kHeight, kFps, i)) {
			return new rtc::RefCountedObject<VideoCapturerTrackSource>(
				CreateTag{},
				std::move(capturer));
		}
	}
	return nullptr;
}

VideoCapturerTrackSource::VideoCapturerTrackSource(
	const CreateTag &,
	std::unique_ptr<VideoCameraCapturer> capturer) :
VideoTrackSource(/*remote=*/false),
_capturer(std::move(capturer)) {
}

VideoCameraCapturer *VideoCapturerTrackSource::capturer() const {
	return _capturer.get();
}

rtc::VideoSourceInterface<webrtc::VideoFrame>* VideoCapturerTrackSource::source() {
	return _capturer.get();
}

} // namespace tgcalls
