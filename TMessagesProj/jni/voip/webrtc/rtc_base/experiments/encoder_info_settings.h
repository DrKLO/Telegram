/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_ENCODER_INFO_SETTINGS_H_
#define RTC_BASE_EXPERIMENTS_ENCODER_INFO_SETTINGS_H_

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/video_codecs/video_encoder.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {

class EncoderInfoSettings {
 public:
  virtual ~EncoderInfoSettings();

  // Bitrate limits per resolution.
  struct BitrateLimit {
    int frame_size_pixels = 0;      // The video frame size.
    int min_start_bitrate_bps = 0;  // The minimum bitrate to start encoding.
    int min_bitrate_bps = 0;        // The minimum bitrate.
    int max_bitrate_bps = 0;        // The maximum bitrate.
  };

  absl::optional<int> requested_resolution_alignment() const;
  bool apply_alignment_to_all_simulcast_layers() const {
    return apply_alignment_to_all_simulcast_layers_.Get();
  }
  std::vector<VideoEncoder::ResolutionBitrateLimits> resolution_bitrate_limits()
      const {
    return resolution_bitrate_limits_;
  }

  static std::vector<VideoEncoder::ResolutionBitrateLimits>
  GetDefaultSinglecastBitrateLimits(VideoCodecType codec_type);

  static absl::optional<VideoEncoder::ResolutionBitrateLimits>
  GetDefaultSinglecastBitrateLimitsForResolution(VideoCodecType codec_type,
                                                 int frame_size_pixels);

 protected:
  explicit EncoderInfoSettings(std::string name);

 private:
  FieldTrialOptional<int> requested_resolution_alignment_;
  FieldTrialFlag apply_alignment_to_all_simulcast_layers_;
  std::vector<VideoEncoder::ResolutionBitrateLimits> resolution_bitrate_limits_;
};

// EncoderInfo settings for SimulcastEncoderAdapter.
class SimulcastEncoderAdapterEncoderInfoSettings : public EncoderInfoSettings {
 public:
  SimulcastEncoderAdapterEncoderInfoSettings();
  ~SimulcastEncoderAdapterEncoderInfoSettings() override {}
};

// EncoderInfo settings for LibvpxVp8Encoder.
class LibvpxVp8EncoderInfoSettings : public EncoderInfoSettings {
 public:
  LibvpxVp8EncoderInfoSettings();
  ~LibvpxVp8EncoderInfoSettings() override {}
};

// EncoderInfo settings for LibvpxVp9Encoder.
class LibvpxVp9EncoderInfoSettings : public EncoderInfoSettings {
 public:
  LibvpxVp9EncoderInfoSettings();
  ~LibvpxVp9EncoderInfoSettings() override {}
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_ENCODER_INFO_SETTINGS_H_
