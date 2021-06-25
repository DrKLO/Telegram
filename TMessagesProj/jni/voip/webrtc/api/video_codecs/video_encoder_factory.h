/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_CODECS_VIDEO_ENCODER_FACTORY_H_
#define API_VIDEO_CODECS_VIDEO_ENCODER_FACTORY_H_

#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/units/data_rate.h"
#include "api/video_codecs/sdp_video_format.h"

namespace webrtc {

class VideoEncoder;

// A factory that creates VideoEncoders.
// NOTE: This class is still under development and may change without notice.
class VideoEncoderFactory {
 public:
  // TODO(magjed): Try to get rid of this struct.
  struct CodecInfo {
    // |has_internal_source| is true if encoders created by this factory of the
    // given codec will use internal camera sources, meaning that they don't
    // require/expect frames to be delivered via webrtc::VideoEncoder::Encode.
    // This flag is used as the internal_source parameter to
    // webrtc::ViEExternalCodec::RegisterExternalSendCodec.
    bool has_internal_source = false;
  };

  struct CodecSupport {
    bool is_supported = false;
    bool is_power_efficient = false;
  };

  // An injectable class that is continuously updated with encoding conditions
  // and selects the best encoder given those conditions.
  class EncoderSelectorInterface {
   public:
    virtual ~EncoderSelectorInterface() {}

    // Informs the encoder selector about which encoder that is currently being
    // used.
    virtual void OnCurrentEncoder(const SdpVideoFormat& format) = 0;

    // Called every time the available bitrate is updated. Should return a
    // non-empty if an encoder switch should be performed.
    virtual absl::optional<SdpVideoFormat> OnAvailableBitrate(
        const DataRate& rate) = 0;

    // Called if the currently used encoder reports itself as broken. Should
    // return a non-empty if an encoder switch should be performed.
    virtual absl::optional<SdpVideoFormat> OnEncoderBroken() = 0;
  };

  // Returns a list of supported video formats in order of preference, to use
  // for signaling etc.
  virtual std::vector<SdpVideoFormat> GetSupportedFormats() const = 0;

  // Returns a list of supported video formats in order of preference, that can
  // also be tagged with additional information to allow the VideoEncoderFactory
  // to separate between different implementations when CreateVideoEncoder is
  // called.
  virtual std::vector<SdpVideoFormat> GetImplementations() const {
    return GetSupportedFormats();
  }

  // Returns information about how this format will be encoded. The specified
  // format must be one of the supported formats by this factory.

  // TODO(magjed): Try to get rid of this method. Since is_hardware_accelerated
  // is unused, only factories producing internal source encoders (in itself a
  // deprecated feature) needs to override this method.
  virtual CodecInfo QueryVideoEncoder(const SdpVideoFormat& format) const {
    return CodecInfo();
  }

  // Query whether the specifed format is supported or not and if it will be
  // power efficient, which is currently interpreted as if there is support for
  // hardware acceleration.
  // See https://w3c.github.io/webrtc-svc/#scalabilitymodes* for a specification
  // of valid values for |scalability_mode|.
  // NOTE: QueryCodecSupport is currently an experimental feature that is
  // subject to change without notice.
  virtual CodecSupport QueryCodecSupport(
      const SdpVideoFormat& format,
      absl::optional<std::string> scalability_mode) const {
    // Default implementation, query for supported formats and check if the
    // specified format is supported. Returns false if scalability_mode is
    // specified.
    CodecSupport codec_support;
    if (!scalability_mode) {
      codec_support.is_supported = format.IsCodecInList(GetSupportedFormats());
    }
    return codec_support;
  }

  // Creates a VideoEncoder for the specified format.
  virtual std::unique_ptr<VideoEncoder> CreateVideoEncoder(
      const SdpVideoFormat& format) = 0;

  virtual std::unique_ptr<EncoderSelectorInterface> GetEncoderSelector() const {
    return nullptr;
  }

  virtual ~VideoEncoderFactory() {}
};

}  // namespace webrtc

#endif  // API_VIDEO_CODECS_VIDEO_ENCODER_FACTORY_H_
