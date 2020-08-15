#ifndef TGCALLS_PLATFORM_INTERFACE_H
#define TGCALLS_PLATFORM_INTERFACE_H

#include "rtc_base/thread.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/media_stream_interface.h"

namespace tgcalls {

enum class VideoState;

class VideoCapturerInterface;
class PlatformContext;

class PlatformInterface {
public:
	static PlatformInterface *SharedInstance();
	virtual ~PlatformInterface() = default;

	virtual void configurePlatformAudio() {
	}
    virtual float getDisplayAspectRatio() {
        return 0.0f;
    }
	virtual std::unique_ptr<webrtc::VideoEncoderFactory> makeVideoEncoderFactory() = 0;
	virtual std::unique_ptr<webrtc::VideoDecoderFactory> makeVideoDecoderFactory() = 0;
	virtual bool supportsEncoding(const std::string &codecName) = 0;
	virtual rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> makeVideoSource(rtc::Thread *signalingThread, rtc::Thread *workerThread) = 0;
    virtual void adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height, int fps) = 0;
	virtual std::unique_ptr<VideoCapturerInterface> makeVideoCapturer(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) = 0;

};

std::unique_ptr<PlatformInterface> CreatePlatformInterface();

inline PlatformInterface *PlatformInterface::SharedInstance() {
	static const auto result = CreatePlatformInterface();
	return result.get();
}

} // namespace tgcalls

#endif
