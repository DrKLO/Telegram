#ifndef TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H
#define TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H

#include "VideoCapturerInterface.h"

#include "sdk/objc/native/src/objc_video_track_source.h"
//#include "api/video_track_source_proxy.h"

@interface VideoCapturerInterfaceImplHolder : NSObject

@property (nonatomic) void *reference;

@end

namespace tgcalls {

struct PlatformCaptureInfo;

class VideoCapturerInterfaceImpl : public VideoCapturerInterface {
public:
	VideoCapturerInterfaceImpl(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated, std::pair<int, int> &outResolution);
	~VideoCapturerInterfaceImpl() override;

	void setState(VideoState state) override;
    void setPreferredCaptureAspectRatio(float aspectRatio) override;
    void withNativeImplementation(std::function<void(void *)> completion) override;
    void setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;
    void setOnFatalError(std::function<void()> error) override;
    void setOnPause(std::function<void(bool)> pause) override;
    int getRotation() override;

    id getInternalReference();

private:
	webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;
	VideoCapturerInterfaceImplHolder *_implReference;
};

} // namespace tgcalls

#endif
