/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_VIDEO_STREAM_ENCODER_SETTINGS_H_
#define API_VIDEO_VIDEO_STREAM_ENCODER_SETTINGS_H_

#include <string>

#include "api/video/video_bitrate_allocator_factory.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_factory.h"

namespace webrtc {

class EncoderSwitchRequestCallback {
 public:
  virtual ~EncoderSwitchRequestCallback() {}

  struct Config {
    std::string codec_name;
    absl::optional<std::string> param;
    absl::optional<std::string> value;
  };

  // Requests that encoder fallback is performed.
  virtual void RequestEncoderFallback() = 0;

  // Requests that a switch to a specific encoder is performed.
  virtual void RequestEncoderSwitch(const Config& conf) = 0;

  virtual void RequestEncoderSwitch(const SdpVideoFormat& format) = 0;
};

struct VideoStreamEncoderSettings {
  enum class BitrateAllocationCallbackType {
    kVideoBitrateAllocation,
    kVideoBitrateAllocationWhenScreenSharing,
    kVideoLayersAllocation
  };

  explicit VideoStreamEncoderSettings(
      const VideoEncoder::Capabilities& capabilities)
      : capabilities(capabilities) {}

  // Enables the new method to estimate the cpu load from encoding, used for
  // cpu adaptation.
  bool experiment_cpu_load_estimator = false;

  // Ownership stays with WebrtcVideoEngine (delegated from PeerConnection).
  VideoEncoderFactory* encoder_factory = nullptr;

  // Requests the WebRtcVideoChannel to perform a codec switch.
  EncoderSwitchRequestCallback* encoder_switch_request_callback = nullptr;

  // Ownership stays with WebrtcVideoEngine (delegated from PeerConnection).
  VideoBitrateAllocatorFactory* bitrate_allocator_factory = nullptr;

  // Negotiated capabilities which the VideoEncoder may expect the other
  // side to use.
  VideoEncoder::Capabilities capabilities;

  // TODO(bugs.webrtc.org/12000): Reporting of VideoBitrateAllocation is beeing
  // deprecated. Instead VideoLayersAllocation should be reported.
  BitrateAllocationCallbackType allocation_cb_type =
      BitrateAllocationCallbackType::kVideoBitrateAllocationWhenScreenSharing;
};

}  // namespace webrtc

#endif  // API_VIDEO_VIDEO_STREAM_ENCODER_SETTINGS_H_
