#ifndef TGCALLS_VIDEO_CAPTURE_INTERFACE_H
#define TGCALLS_VIDEO_CAPTURE_INTERFACE_H

#include <string>
#include <memory>
#include <functional>

namespace rtc {
template <typename VideoFrameT>
class VideoSinkInterface;
} // namespace rtc

namespace webrtc {
class VideoFrame;
} // namespace webrtc

namespace tgcalls {

class PlatformContext;
class Threads;

enum class VideoState {
	Inactive,
	Paused,
	Active,
};


class VideoCaptureInterface {
protected:
	VideoCaptureInterface() = default;

public:
	static std::unique_ptr<VideoCaptureInterface> Create(
                std::shared_ptr<Threads> threads,
                std::string deviceId = std::string(),
                bool isScreenCapture = false,
		std::shared_ptr<PlatformContext> platformContext = nullptr);

	virtual ~VideoCaptureInterface();

	virtual void switchToDevice(std::string deviceId, bool isScreenCapture) = 0;
	virtual void setState(VideoState state) = 0;
    virtual void setPreferredAspectRatio(float aspectRatio) = 0;
	virtual void setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;
    virtual void setOnFatalError(std::function<void()> error) {
      // TODO: make this function pure virtual when everybody implements it.
    }
    virtual void setOnPause(std::function<void(bool)> pause) {
      // TODO: make this function pure virtual when everybody implements it.
    }
    virtual void setOnIsActiveUpdated(std::function<void(bool)> onIsActiveUpdated) {
      // TODO: make this function pure virtual when everybody implements it.
    }
    virtual void withNativeImplementation(std::function<void(void *)> completion) {
        completion(nullptr);
    }
};

} // namespace tgcalls

#endif
