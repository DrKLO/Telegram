#include "tgcalls/platform/tdesktop/VideoCapturerTrackSource.h"

namespace tgcalls {

VideoCapturerTrackSource::VideoCapturerTrackSource()
: VideoTrackSource(/*remote=*/false)
, _broadcaster(std::make_shared<rtc::VideoBroadcaster>()) {
}

auto VideoCapturerTrackSource::sink()
-> std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> {
	return _broadcaster;
}

rtc::VideoSourceInterface<webrtc::VideoFrame> *VideoCapturerTrackSource::source() {
	return _broadcaster.get();
}

} // namespace tgcalls
