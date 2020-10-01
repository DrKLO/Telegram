#ifndef TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H
#define TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H

#include "VideoCapturerInterface.h"
#include "VideoCameraCapturer.h"
#include "api/media_stream_interface.h"

namespace tgcalls {

class VideoCapturerInterfaceImpl final : public VideoCapturerInterface {
public:
	VideoCapturerInterfaceImpl(rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext);

	void setState(VideoState state) override;
	void setPreferredCaptureAspectRatio(float aspectRatio) override;
	void setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;

private:
	std::unique_ptr<VideoCameraCapturer> _capturer;

};

} // namespace tgcalls

#endif
