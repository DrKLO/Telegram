/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_EXPERIMENTS_QUALITY_RAMPUP_EXPERIMENT_H_
#define RTC_BASE_EXPERIMENTS_QUALITY_RAMPUP_EXPERIMENT_H_

#include "absl/types/optional.h"
#include "api/transport/webrtc_key_value_config.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {

class QualityRampupExperiment final {
 public:
  static QualityRampupExperiment ParseSettings();

  absl::optional<int> MinPixels() const;
  absl::optional<int> MinDurationMs() const;
  absl::optional<double> MaxBitrateFactor() const;

  // Sets the max bitrate and the frame size.
  // The call has no effect if the frame size is less than `min_pixels_`.
  void SetMaxBitrate(int pixels, uint32_t max_bitrate_kbps);

  // Returns true if the available bandwidth is a certain percentage
  // (max_bitrate_factor_) above `max_bitrate_kbps_` for `min_duration_ms_`.
  bool BwHigh(int64_t now_ms, uint32_t available_bw_kbps);

  void Reset();
  bool Enabled() const;

 private:
  explicit QualityRampupExperiment(
      const WebRtcKeyValueConfig* const key_value_config);

  FieldTrialOptional<int> min_pixels_;
  FieldTrialOptional<int> min_duration_ms_;
  FieldTrialOptional<double> max_bitrate_factor_;

  absl::optional<int64_t> start_ms_;
  absl::optional<uint32_t> max_bitrate_kbps_;
};

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_QUALITY_RAMPUP_EXPERIMENT_H_
