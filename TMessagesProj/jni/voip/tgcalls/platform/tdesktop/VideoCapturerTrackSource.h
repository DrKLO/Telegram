#ifndef TGCALLS_VIDEO_CAPTURER_TRACK_SOURCE_H
#define TGCALLS_VIDEO_CAPTURER_TRACK_SOURCE_H

#include "pc/video_track_source.h"
#include "api/video/video_sink_interface.h"
#include "media/base/video_broadcaster.h"

#include "VideoCameraCapturer.h"

namespace tgcalls {

class VideoCameraCapturer;
class DesktopCapturer;

class VideoCapturerTrackSource : public webrtc::VideoTrackSource {
public:
	VideoCapturerTrackSource();

	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink();

private:
	rtc::VideoSourceInterface<webrtc::VideoFrame> *source() override;

	std::shared_ptr<rtc::VideoBroadcaster> _broadcaster;

};

} // namespace tgcalls

#endif
