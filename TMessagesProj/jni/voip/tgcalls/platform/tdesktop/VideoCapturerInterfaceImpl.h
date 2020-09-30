#ifndef TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H
#define TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H

#include "VideoCapturerInterface.h"

#include "api/media_stream_interface.h"

namespace tgcalls {

class VideoCapturerInterfaceImpl final : public VideoCapturerInterface {
public:
	VideoCapturerInterfaceImpl(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated);
	~VideoCapturerInterfaceImpl() override;

	void setState(VideoState state) override;
	void setPreferredCaptureAspectRatio(float aspectRatio) override;
	void setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;

private:
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;
	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _uncroppedSink;
	std::function<void(VideoState)> _stateUpdated;

};

} // namespace tgcalls

#endif
