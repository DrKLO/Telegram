#ifndef TGCALLS_VIDEO_CAPTURER_TRACK_SOURCE_H
#define TGCALLS_VIDEO_CAPTURER_TRACK_SOURCE_H

#include "pc/video_track_source.h"
#include "VideoCameraCapturer.h"

namespace tgcalls {

class VideoCameraCapturer;

class VideoCapturerTrackSource : public webrtc::VideoTrackSource {
private:
	struct CreateTag {
	};

public:
	static rtc::scoped_refptr<VideoCapturerTrackSource> Create();

	VideoCapturerTrackSource(
		const CreateTag &,
		std::unique_ptr<VideoCameraCapturer> capturer);

	VideoCameraCapturer *capturer() const;

private:
	rtc::VideoSourceInterface<webrtc::VideoFrame> *source() override;

	std::unique_ptr<VideoCameraCapturer> _capturer;

};

} // namespace tgcalls

#endif
