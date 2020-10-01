#ifndef TGCALLS_VIDEO_CAMERA_CAPTURER_H
#define TGCALLS_VIDEO_CAMERA_CAPTURER_H

#include "api/scoped_refptr.h"
#include "api/media_stream_interface.h"
#include "modules/video_capture/video_capture.h"
#include "sdk/android/native_api/jni/scoped_java_ref.h"
#include "sdk/android/native_api/video/video_source.h"
#include "VideoCaptureInterface.h"

#include <memory>
#include <vector>
#include <stddef.h>
#include <jni.h>

namespace tgcalls {

class VideoCameraCapturer;

class VideoCameraCapturer {

public:
	VideoCameraCapturer(rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::shared_ptr<PlatformContext> platformContext);

	void setState(VideoState state);
	void setPreferredCaptureAspectRatio(float aspectRatio);
	void setUncroppedSink(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);

    webrtc::ScopedJavaLocalRef<jobject> GetJavaVideoCapturerObserver(JNIEnv* env);

private:
	rtc::scoped_refptr<webrtc::JavaVideoTrackSourceInterface> _source;

	std::function<void(VideoState)> _stateUpdated;
	VideoState _state;

	std::shared_ptr<PlatformContext> _platformContext;

	float _aspectRatio;
	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _uncroppedSink;
};

}  // namespace tgcalls

#endif
