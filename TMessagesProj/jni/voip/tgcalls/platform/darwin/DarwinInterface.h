#ifndef TGCALLS_DARWIN_INTERFACE_H
#define TGCALLS_DARWIN_INTERFACE_H

#include "platform/PlatformInterface.h"

#import <CoreVideo/CoreVideo.h>

namespace tgcalls {

class DarwinVideoFrame : public PlatformVideoFrame {
public:
    DarwinVideoFrame(CVPixelBufferRef pixelBuffer);
    virtual ~DarwinVideoFrame();
    
    CVPixelBufferRef pixelBuffer() const {
        return _pixelBuffer;
    }
    
private:
    CVPixelBufferRef _pixelBuffer = nullptr;
};

class DarwinInterface : public PlatformInterface {
public:
    std::unique_ptr<rtc::NetworkMonitorFactory> createNetworkMonitorFactory() override;
	void configurePlatformAudio(int numChannels) override;
	std::unique_ptr<webrtc::VideoEncoderFactory> makeVideoEncoderFactory(bool preferHardwareEncoding, bool isScreencast) override;
	std::unique_ptr<webrtc::VideoDecoderFactory> makeVideoDecoderFactory() override;
	bool supportsEncoding(const std::string &codecName) override;
	webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread) override;
    virtual void adaptVideoSource(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height, int fps) override;
	std::unique_ptr<VideoCapturerInterface> makeVideoCapturer(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated, std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) override;
    virtual webrtc::scoped_refptr<WrappedAudioDeviceModule> wrapAudioDeviceModule(webrtc::scoped_refptr<webrtc::AudioDeviceModule> module) override;
    virtual void setupVideoDecoding(AVCodecContext *codecContext) override;
    virtual webrtc::scoped_refptr<webrtc::VideoFrameBuffer> createPlatformFrameFromData(AVFrame const *frame) override;
};

} // namespace tgcalls

#endif
