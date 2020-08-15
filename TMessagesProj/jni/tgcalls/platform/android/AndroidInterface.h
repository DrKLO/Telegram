#ifndef TGCALLS_ANDROID_INTERFACE_H
#define TGCALLS_ANDROID_INTERFACE_H

#include "sdk/android/native_api/video/video_source.h"
#include "platform/PlatformInterface.h"
#include "VideoCapturerInterface.h"

namespace tgcalls {

class AndroidInterface : public PlatformInterface {
public:
    void configurePlatformAudio() override;
    float getDisplayAspectRatio() override;
	std::unique_ptr<webrtc::VideoEncoderFactory> makeVideoEncoderFactory() override;
	std::unique_ptr<webrtc::VideoDecoderFactory> makeVideoDecoderFactory() override;
	bool supportsEncoding(const std::string &codecName) override;
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread) override;
    void adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height, int fps) override;
	std::unique_ptr<VideoCapturerInterface> makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) override;

private:
	rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> _source;
	std::unique_ptr<webrtc::VideoEncoderFactory> hardwareVideoEncoderFactory;
	std::unique_ptr<webrtc::VideoEncoderFactory> softwareVideoEncoderFactory;

};

} // namespace tgcalls

#endif
