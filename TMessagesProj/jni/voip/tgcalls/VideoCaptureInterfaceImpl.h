#ifndef TGCALLS_VIDEO_CAPTURE_INTERFACE_IMPL_H
#define TGCALLS_VIDEO_CAPTURE_INTERFACE_IMPL_H

#include "VideoCaptureInterface.h"
#include <memory>
#include "ThreadLocalObject.h"
#include "api/media_stream_interface.h"
#include "platform/PlatformInterface.h"

namespace tgcalls {

class VideoCapturerInterface;
class Threads;

class VideoCaptureInterfaceObject {
public:
	VideoCaptureInterfaceObject(std::string deviceId, bool isScreenCapture, std::shared_ptr<PlatformContext> platformContext, Threads &threads);
	~VideoCaptureInterfaceObject();

	void switchToDevice(std::string deviceId, bool isScreenCapture);
    void withNativeImplementation(std::function<void(void *)> completion);
	void setState(VideoState state);
    void setPreferredAspectRatio(float aspectRatio);
	void setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
	void setStateUpdated(std::function<void(VideoState)> stateUpdated);
    void setRotationUpdated(std::function<void(int)> rotationUpdated);
    void setOnFatalError(std::function<void()> error);
    void setOnPause(std::function<void(bool)> pause);
    void setOnIsActiveUpdated(std::function<void(bool)> onIsActiveUpdated);
    rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source();
    int getRotation();
    bool isScreenCapture();

private:
    void updateAspectRateAdaptation();

    rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _videoSource;
	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentUncroppedSink;
	std::shared_ptr<PlatformContext> _platformContext;
    std::pair<int, int> _videoCapturerResolution;
	std::unique_ptr<VideoCapturerInterface> _videoCapturer;
	std::function<void(VideoState)> _stateUpdated;
    std::function<void()> _onFatalError;
    std::function<void(bool)> _onPause;
    std::function<void(bool)> _onIsActiveUpdated;
    std::function<void(int)> _rotationUpdated;
	VideoState _state = VideoState::Active;
    float _preferredAspectRatio = 0.0f;
    bool _shouldBeAdaptedToReceiverAspectRate = true;
    bool _isScreenCapture = false;
};

class VideoCaptureInterfaceImpl : public VideoCaptureInterface {
public:
	VideoCaptureInterfaceImpl(std::string deviceId, bool isScreenCapture, std::shared_ptr<PlatformContext> platformContext, std::shared_ptr<Threads> threads);
	virtual ~VideoCaptureInterfaceImpl();

	void switchToDevice(std::string deviceId, bool isScreenCapture) override;
    void withNativeImplementation(std::function<void(void *)> completion) override;
	void setState(VideoState state) override;
    void setPreferredAspectRatio(float aspectRatio) override;
	void setOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;
    void setOnFatalError(std::function<void()> error) override;
    void setOnPause(std::function<void(bool)> pause) override;
    void setOnIsActiveUpdated(std::function<void(bool)> onIsActiveUpdated) override;
    std::shared_ptr<PlatformContext> getPlatformContext() override;

	ThreadLocalObject<VideoCaptureInterfaceObject> *object();

private:
	ThreadLocalObject<VideoCaptureInterfaceObject> _impl;

    std::shared_ptr<PlatformContext> _platformContext;

};

} // namespace tgcalls

#endif
