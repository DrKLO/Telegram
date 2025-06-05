/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/builtin_video_encoder_factory.h"

#include <memory>
#include <string>
#include <vector>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_encoder.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/engine/internal_encoder_factory.h"
#include "media/engine/simulcast_encoder_adapter.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// This class wraps the internal factory and adds simulcast.
class BuiltinVideoEncoderFactory : public VideoEncoderFactory {
 public:
  BuiltinVideoEncoderFactory()
      : internal_encoder_factory_(new InternalEncoderFactory()) {}

  std::unique_ptr<VideoEncoder> CreateVideoEncoder(
      const SdpVideoFormat& format) override {
    // Try creating an InternalEncoderFactory-backed SimulcastEncoderAdapter.
    // The adapter has a passthrough mode for the case that simulcast is not
    // used, so all responsibility can be delegated to it.
    std::unique_ptr<VideoEncoder> encoder;
    if (format.IsCodecInList(
            internal_encoder_factory_->GetSupportedFormats())) {
      encoder = std::make_unique<SimulcastEncoderAdapter>(
          internal_encoder_factory_.get(), format);
    }

    return encoder;
  }

  std::vector<SdpVideoFormat> GetSupportedFormats() const override {
    return internal_encoder_factory_->GetSupportedFormats();
  }

  CodecSupport QueryCodecSupport(
      const SdpVideoFormat& format,
      absl::optional<std::string> scalability_mode) const override {
    return internal_encoder_factory_->QueryCodecSupport(format,
                                                        scalability_mode);
  }

 private:
  const std::unique_ptr<VideoEncoderFactory> internal_encoder_factory_;
};

}  // namespace

std::unique_ptr<VideoEncoderFactory> CreateBuiltinVideoEncoderFactory() {
  return std::make_unique<BuiltinVideoEncoderFactory>();
}

}  // namespace webrtc
