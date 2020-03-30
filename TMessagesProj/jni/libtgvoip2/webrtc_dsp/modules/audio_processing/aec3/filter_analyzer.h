/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_FILTER_ANALYZER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_FILTER_ANALYZER_H_

#include <stddef.h>
#include <array>
#include <memory>
#include <vector>

#include "api/array_view.h"
#include "api/audio/echo_canceller3_config.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class ApmDataDumper;
class RenderBuffer;

// Class for analyzing the properties of an adaptive filter.
class FilterAnalyzer {
 public:
  explicit FilterAnalyzer(const EchoCanceller3Config& config);
  ~FilterAnalyzer();

  // Resets the analysis.
  void Reset();

  // Updates the estimates with new input data.
  void Update(rtc::ArrayView<const float> filter_time_domain,
              const std::vector<std::array<float, kFftLengthBy2Plus1>>&
                  filter_freq_response,
              const RenderBuffer& render_buffer);

  // Returns the delay of the filter in terms of blocks.
  int DelayBlocks() const { return delay_blocks_; }

  // Returns whether the filter is consistent in the sense that it does not
  // change much over time.
  bool Consistent() const { return consistent_estimate_; }

  // Returns the estimated filter gain.
  float Gain() const { return gain_; }

  // Returns the number of blocks for the current used filter.
  float FilterLengthBlocks() const { return filter_length_blocks_; }

  // Returns the preprocessed filter.
  rtc::ArrayView<const float> GetAdjustedFilter() const { return h_highpass_; }

 private:
  void UpdateFilterGain(rtc::ArrayView<const float> filter_time_domain,
                        size_t max_index);
  void PreProcessFilter(rtc::ArrayView<const float> filter_time_domain);

  static int instance_count_;
  std::unique_ptr<ApmDataDumper> data_dumper_;
  const bool use_preprocessed_filter_;
  const bool bounded_erl_;
  const float default_gain_;
  const float active_render_threshold_;
  std::vector<float> h_highpass_;
  int delay_blocks_ = 0;
  size_t blocks_since_reset_ = 0;
  bool consistent_estimate_ = false;
  size_t consistent_estimate_counter_ = 0;
  int consistent_delay_reference_ = -10;
  float gain_;
  int filter_length_blocks_;
  RTC_DISALLOW_COPY_AND_ASSIGN(FilterAnalyzer);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_FILTER_ANALYZER_H_
