#ifndef TGCALLS_VIDEO_CAMERA_CAPTURER_H
#define TGCALLS_VIDEO_CAMERA_CAPTURER_H

#include "api/scoped_refptr.h"
#include "api/video/video_frame.h"
#include "api/video/video_source_interface.h"
#include "media/base/video_adapter.h"
#include "media/base/video_broadcaster.h"
#include "modules/video_capture/video_capture.h"

#include "VideoCaptureInterface.h"

#include <memory>
#include <vector>
#include <stddef.h>

namespace tgcalls {

class VideoCameraCapturer :
	public rtc::VideoSourceInterface<webrtc::VideoFrame>,
	public rtc::VideoSinkInterface<webrtc::VideoFrame> {
private:
	enum CreateTag {
	};

public:
	VideoCameraCapturer(const CreateTag &);
	~VideoCameraCapturer();

	static std::unique_ptr<VideoCameraCapturer> Create(size_t width,
		size_t height,
		size_t target_fps,
		size_t capture_device_index);

	void setState(VideoState state);
	void setPreferredCaptureAspectRatio(float aspectRatio);

	void AddOrUpdateSink(rtc::VideoSinkInterface<webrtc::VideoFrame>* sink,
		const rtc::VideoSinkWants& wants) override;
	void RemoveSink(rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) override;

	void OnFrame(const webrtc::VideoFrame &frame) override;

private:
	bool init(size_t width,
		size_t height,
		size_t target_fps,
		size_t capture_device_index);
	void destroy();
	void updateVideoAdapter();

	rtc::VideoBroadcaster _broadcaster;
	//cricket::VideoAdapter _videoAdapter;

	rtc::scoped_refptr<webrtc::VideoCaptureModule> _module;
	webrtc::VideoCaptureCapability _capability;

	VideoState _state = VideoState::Active;
	float _aspectRatio = 0.;

};

}  // namespace tgcalls

#endif
