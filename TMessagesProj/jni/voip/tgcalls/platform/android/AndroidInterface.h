#ifndef TGCALLS_ANDROID_INTERFACE_H
#define TGCALLS_ANDROID_INTERFACE_H

#include "sdk/android/native_api/video/video_source.h"
#include "platform/PlatformInterface.h"
#include "VideoCapturerInterface.h"

namespace tgcalls {

class AndroidInterface : public PlatformInterface {
public:
    void configurePlatformAudio(int numChannels = 1) override;
	std::unique_ptr<webrtc::VideoEncoderFactory> makeVideoEncoderFactory(std::shared_ptr<PlatformContext> platformContext,  bool preferHardwareEncoding = false, bool isScreencast = false) override;
	std::unique_ptr<webrtc::VideoDecoderFactory> makeVideoDecoderFactory(std::shared_ptr<PlatformContext> platformContext) override;
	bool supportsEncoding(const std::string &codecName, std::shared_ptr<PlatformContext> platformContext) override;
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread, bool screencapture) override;
    void adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height, int fps) override;
	std::unique_ptr<VideoCapturerInterface> makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated, std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) override;
	std::unique_ptr<rtc::NetworkMonitorFactory> createNetworkMonitorFactory() override;

private:
	rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> _source[2];
	std::unique_ptr<webrtc::VideoEncoderFactory> hardwareVideoEncoderFactory;
	std::unique_ptr<webrtc::VideoEncoderFactory> softwareVideoEncoderFactory;

};

} // namespace tgcalls

#endif
