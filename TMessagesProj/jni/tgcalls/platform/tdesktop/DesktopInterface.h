#ifndef TGCALLS_DESKTOP_INTERFACE_H
#define TGCALLS_DESKTOP_INTERFACE_H

#include "platform/PlatformInterface.h"
#include "VideoCapturerInterface.h"

namespace tgcalls {

class DesktopInterface : public PlatformInterface {
public:
	std::unique_ptr<webrtc::VideoEncoderFactory> makeVideoEncoderFactory() override;
	std::unique_ptr<webrtc::VideoDecoderFactory> makeVideoDecoderFactory() override;
	bool supportsEncoding(const std::string &codecName) override;
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread) override;
	std::unique_ptr<VideoCapturerInterface> makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext) override;

};

} // namespace tgcalls

#endif // TGCALLS_DESKTOP_INTERFACE_H
