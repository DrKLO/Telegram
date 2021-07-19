#include "FakeInterface.h"

#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_track_source_proxy.h"

namespace tgcalls {

std::unique_ptr<webrtc::VideoEncoderFactory> FakeInterface::makeVideoEncoderFactory() {
  return webrtc::CreateBuiltinVideoEncoderFactory();
}

std::unique_ptr<webrtc::VideoDecoderFactory> FakeInterface::makeVideoDecoderFactory() {
  return webrtc::CreateBuiltinVideoDecoderFactory();
}

rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> FakeInterface::makeVideoSource(rtc::Thread *signalingThread,
                                                                                     rtc::Thread *workerThread) {
  return nullptr;
}

bool FakeInterface::supportsEncoding(const std::string &codecName) {
  return false;
  //return (codecName == cricket::kH264CodecName) || (codecName == cricket::kVp8CodecName);
}

void FakeInterface::adaptVideoSource(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width,
                                     int height, int fps) {
}

std::unique_ptr<VideoCapturerInterface> FakeInterface::makeVideoCapturer(
    rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId,
    std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated,
    std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) {
  return nullptr;
  //return std::make_unique<VideoCapturerInterfaceImpl>(source, deviceId, stateUpdated, outResolution);
}

std::unique_ptr<PlatformInterface> CreatePlatformInterface() {
  return std::make_unique<FakeInterface>();
}

}  // namespace tgcalls
