#pragma once

#include "platform/PlatformInterface.h"
#include "VideoCapturerInterface.h"

namespace tgcalls {

class FakeInterface : public PlatformInterface {
 public:
  std::unique_ptr<webrtc::VideoEncoderFactory> makeVideoEncoderFactory() override;
  std::unique_ptr<webrtc::VideoDecoderFactory> makeVideoDecoderFactory() override;
  bool supportsEncoding(const std::string &codecName) override;
  rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> makeVideoSource(rtc::Thread *signalingThread,
                                                                        rtc::Thread *workerThread) override;
  void adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width, int height,
                        int fps) override;
  std::unique_ptr<VideoCapturerInterface> makeVideoCapturer(
      rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId,
      std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated,
      std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) override;
};

}  // namespace tgcalls

