#ifndef TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H
#define TGCALLS_VIDEO_CAPTURER_INTERFACE_IMPL_H

#include "VideoCapturerInterface.h"

#include "sdk/objc/native/src/objc_video_track_source.h"
#include "api/video_track_source_proxy.h"

@interface VideoCapturerInterfaceImplHolder : NSObject

@property (nonatomic) void *reference;

@end

namespace tgcalls {

class VideoCapturerInterfaceImpl : public VideoCapturerInterface {
public:
	VideoCapturerInterfaceImpl(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::pair<int, int> &outResolution);
	~VideoCapturerInterfaceImpl() override;

	void setState(VideoState state) override;
    void setPreferredCaptureAspectRatio(float aspectRatio) override;
    void setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;

private:
	rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;
	VideoCapturerInterfaceImplHolder *_implReference;
};

} // namespace tgcalls

#endif
