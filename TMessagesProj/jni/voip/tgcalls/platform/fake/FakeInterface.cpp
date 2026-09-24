#include "FakeInterface.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "api/environment/environment.h"
#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_decoder.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "media/base/media_constants.h"
//#include "api/video_track_source_proxy.h"

namespace tgcalls {

namespace {

bool gUseBuiltinCodecOrder = false;

// Reorders a builtin factory's supported formats into the shape the iOS
// platform factories advertise: two H264 entries (packetization-mode 1)
// first, then VP8, then VP9 profile 0. (iOS then lists H265, which the host
// build lacks.)
//
// PeerConnection assigns dynamic payload types by walking this list —
// 96, 98, 100, 102, ... with RTX at +1 — so the ORDER decides which codec
// lands on which number. The raw builtin host order happens to put an H264
// profile at PT 104, which hid that GroupInstanceReferenceImpl's
// auto-generated receive table did not match the group-call convention
// (VP8 100, VP9 102, H264 104): on the device PT 104 was H265 and incoming
// H264 never decoded, while the host test passed. With this order PT 104 is
// not H264 on the host either, so the testbench sees what the device sees.
//
// The constrained-baseline H264 entry is kept deliberately: WebRTC's decoder
// table builder appends a CB variant for any H264 profile that lacks one,
// and an appended entry would land right after VP9 — on PT 104.
std::vector<webrtc::SdpVideoFormat> iosLikeFormatOrder(std::vector<webrtc::SdpVideoFormat> formats) {
  std::vector<webrtc::SdpVideoFormat> h264;
  std::vector<webrtc::SdpVideoFormat> vp8;
  std::vector<webrtc::SdpVideoFormat> vp9;
  for (const auto& format : formats) {
    if (absl::EqualsIgnoreCase(format.name, cricket::kH264CodecName)) {
      auto mode = format.parameters.find(cricket::kH264FmtpPacketizationMode);
      if (mode != format.parameters.end() && mode->second != "1") continue;
      h264.push_back(format);
    } else if (absl::EqualsIgnoreCase(format.name, cricket::kVp8CodecName)) {
      vp8.push_back(format);
    } else if (absl::EqualsIgnoreCase(format.name, cricket::kVp9CodecName)) {
      auto profile = format.parameters.find("profile-id");
      if (profile != format.parameters.end() && profile->second != "0") continue;
      vp9.push_back(format);
    }
  }

  std::vector<webrtc::SdpVideoFormat> result;
  // First H264 slot: the constrained-baseline entry if there is one.
  for (const auto& format : h264) {
    auto profile = format.parameters.find(cricket::kH264FmtpProfileLevelId);
    if (profile != format.parameters.end() && absl::StartsWithIgnoreCase(profile->second, "42e0")) {
      result.push_back(format);
      break;
    }
  }
  // Second H264 slot: the first entry not already taken.
  for (const auto& format : h264) {
    if (result.size() >= 2) break;
    if (!result.empty() && result[0] == format) continue;
    result.push_back(format);
  }
  if (!vp8.empty()) result.push_back(vp8[0]);
  if (!vp9.empty()) result.push_back(vp9[0]);
  return result;
}

class IosOrderVideoEncoderFactory : public webrtc::VideoEncoderFactory {
 public:
  explicit IosOrderVideoEncoderFactory(std::unique_ptr<webrtc::VideoEncoderFactory> inner) : _inner(std::move(inner)) {}

  std::vector<webrtc::SdpVideoFormat> GetSupportedFormats() const override {
    return iosLikeFormatOrder(_inner->GetSupportedFormats());
  }
  std::vector<webrtc::SdpVideoFormat> GetImplementations() const override {
    return iosLikeFormatOrder(_inner->GetImplementations());
  }
  CodecSupport QueryCodecSupport(const webrtc::SdpVideoFormat& format,
                                 absl::optional<std::string> scalability_mode) const override {
    return _inner->QueryCodecSupport(format, scalability_mode);
  }
  std::unique_ptr<webrtc::VideoEncoder> CreateVideoEncoder(const webrtc::SdpVideoFormat& format) override {
    return _inner->CreateVideoEncoder(format);
  }
  std::unique_ptr<EncoderSelectorInterface> GetEncoderSelector() const override {
    return _inner->GetEncoderSelector();
  }

 private:
  std::unique_ptr<webrtc::VideoEncoderFactory> _inner;
};

class IosOrderVideoDecoderFactory : public webrtc::VideoDecoderFactory {
 public:
  explicit IosOrderVideoDecoderFactory(std::unique_ptr<webrtc::VideoDecoderFactory> inner) : _inner(std::move(inner)) {}

  std::vector<webrtc::SdpVideoFormat> GetSupportedFormats() const override {
    return iosLikeFormatOrder(_inner->GetSupportedFormats());
  }
  CodecSupport QueryCodecSupport(const webrtc::SdpVideoFormat& format, bool reference_scaling) const override {
    return _inner->QueryCodecSupport(format, reference_scaling);
  }
  std::unique_ptr<webrtc::VideoDecoder> Create(const webrtc::Environment& env,
                                               const webrtc::SdpVideoFormat& format) override {
    return _inner->Create(env, format);
  }
  std::unique_ptr<webrtc::VideoDecoder> CreateVideoDecoder(const webrtc::SdpVideoFormat& format) override {
    return _inner->CreateVideoDecoder(format);
  }

 private:
  std::unique_ptr<webrtc::VideoDecoderFactory> _inner;
};

}  // namespace

void setFakePlatformBuiltinCodecOrder(bool useBuiltinOrder) {
  gUseBuiltinCodecOrder = useBuiltinOrder;
}

std::unique_ptr<webrtc::VideoEncoderFactory> FakeInterface::makeVideoEncoderFactory(bool preferHardwareEncoding, bool isScreencast) {
  auto factory = webrtc::CreateBuiltinVideoEncoderFactory();
  if (gUseBuiltinCodecOrder) return factory;
  return std::make_unique<IosOrderVideoEncoderFactory>(std::move(factory));
}

std::unique_ptr<webrtc::VideoDecoderFactory> FakeInterface::makeVideoDecoderFactory() {
  auto factory = webrtc::CreateBuiltinVideoDecoderFactory();
  if (gUseBuiltinCodecOrder) return factory;
  return std::make_unique<IosOrderVideoDecoderFactory>(std::move(factory));
}

webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> FakeInterface::makeVideoSource(rtc::Thread *signalingThread,
                                                                                     rtc::Thread *workerThread) {
  return nullptr;
}

bool FakeInterface::supportsEncoding(const std::string &codecName) {
  return false;
  //return (codecName == cricket::kH264CodecName) || (codecName == cricket::kVp8CodecName);
}

void FakeInterface::adaptVideoSource(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource, int width,
                                     int height, int fps) {
}

std::unique_ptr<VideoCapturerInterface> FakeInterface::makeVideoCapturer(
    webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId,
    std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated,
    std::shared_ptr<PlatformContext> platformContext, std::pair<int, int> &outResolution) {
  return nullptr;
  //return std::make_unique<VideoCapturerInterfaceImpl>(source, deviceId, stateUpdated, outResolution);
}

std::unique_ptr<PlatformInterface> CreatePlatformInterface() {
  return std::make_unique<FakeInterface>();
}

}  // namespace tgcalls
