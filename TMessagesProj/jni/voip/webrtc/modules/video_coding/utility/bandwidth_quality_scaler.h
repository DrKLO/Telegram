/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_UTILITY_BANDWIDTH_QUALITY_SCALER_H_
#define MODULES_VIDEO_CODING_UTILITY_BANDWIDTH_QUALITY_SCALER_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/video_codecs/video_encoder.h"
#include "rtc_base/experiments/encoder_info_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/rate_statistics.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/weak_ptr.h"

namespace webrtc {

class BandwidthQualityScalerUsageHandlerInterface {
 public:
  virtual ~BandwidthQualityScalerUsageHandlerInterface();

  virtual void OnReportUsageBandwidthHigh() = 0;
  virtual void OnReportUsageBandwidthLow() = 0;
};

// BandwidthQualityScaler runs asynchronously and monitors bandwidth values of
// encoded frames. It holds a reference to a
// BandwidthQualityScalerUsageHandlerInterface implementation to signal an
// overuse or underuse of bandwidth (which indicate a desire to scale the video
// stream down or up).
class BandwidthQualityScaler {
 public:
  explicit BandwidthQualityScaler(
      BandwidthQualityScalerUsageHandlerInterface* handler);
  virtual ~BandwidthQualityScaler();

  void ReportEncodeInfo(int frame_size_bytes,
                        int64_t time_sent_in_ms,
                        uint32_t encoded_width,
                        uint32_t encoded_height);

  // We prioritise to using the |resolution_bitrate_limits| provided by the
  // current decoder. If not provided, we will use the default data by
  // GetDefaultResolutionBitrateLimits().
  void SetResolutionBitrateLimits(
      const std::vector<VideoEncoder::ResolutionBitrateLimits>&
          resolution_bitrate_limits);

  const TimeDelta kBitrateStateUpdateInterval;

 private:
  enum class CheckBitrateResult {
    kInsufficientSamples,
    kNormalBitrate,
    kHighBitRate,
    kLowBitRate,
  };

  // We will periodically check encode bitrate, this function will make
  // resolution up or down decisions and report the decision to the adapter.
  void StartCheckForBitrate();
  CheckBitrateResult CheckBitrate();

  RTC_NO_UNIQUE_ADDRESS SequenceChecker task_checker_;
  BandwidthQualityScalerUsageHandlerInterface* const handler_
      RTC_GUARDED_BY(&task_checker_);

  absl::optional<int64_t> last_time_sent_in_ms_ RTC_GUARDED_BY(&task_checker_);
  RateStatistics encoded_bitrate_ RTC_GUARDED_BY(&task_checker_);
  absl::optional<int> last_frame_size_pixels_ RTC_GUARDED_BY(&task_checker_);
  rtc::WeakPtrFactory<BandwidthQualityScaler> weak_ptr_factory_;

  std::vector<VideoEncoder::ResolutionBitrateLimits> resolution_bitrate_limits_;
};

}  // namespace webrtc
#endif  // MODULES_VIDEO_CODING_UTILITY_BANDWIDTH_QUALITY_SCALER_H_
