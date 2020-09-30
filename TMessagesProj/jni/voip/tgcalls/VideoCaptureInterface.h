#ifndef TGCALLS_VIDEO_CAPTURE_INTERFACE_H
#define TGCALLS_VIDEO_CAPTURE_INTERFACE_H

#include <memory>

namespace rtc {
template <typename VideoFrameT>
class VideoSinkInterface;
} // namespace rtc

namespace webrtc {
class VideoFrame;
} // namespace webrtc

namespace tgcalls {

class PlatformContext;

enum class VideoState {
	Inactive,
	Paused,
	Active,
};

class VideoCaptureInterface {
protected:
	VideoCaptureInterface() = default;

public:
	static std::unique_ptr<VideoCaptureInterface> Create(std::shared_ptr<PlatformContext> platformContext = nullptr);

	virtual ~VideoCaptureInterface();

	virtual void switchCamera() = 0;
	virtual void setState(VideoState state) = 0;
    virtual void setPreferredAspectRatio(float aspectRatio) = 0;
	virtual void setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;
	virtual std::shared_ptr<PlatformContext> getPlatformContext() {
		return nullptr;
	}

};

} // namespace tgcalls

#endif
