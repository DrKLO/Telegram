#ifndef TGCALLS_VIDEO_CAPTURER_INTERFACE_H
#define TGCALLS_VIDEO_CAPTURER_INTERFACE_H

#include "Instance.h"

#include <memory>

namespace rtc {
template <typename VideoFrameT>
class VideoSinkInterface;
} // namespace rtc

namespace webrtc {
class VideoFrame;
} // namespace webrtc

namespace tgcalls {

class VideoCapturerInterface {
public:
	virtual ~VideoCapturerInterface() = default;

	virtual void setState(VideoState state) = 0;
	virtual void setPreferredCaptureAspectRatio(float aspectRatio) = 0;
	virtual void setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;
    
};

} // namespace tgcalls

#endif
