/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/block_processor_metrics.h"

#include "modules/audio_processing/aec3/aec3_common.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

enum class RenderUnderrunCategory {
  kNone,
  kFew,
  kSeveral,
  kMany,
  kConstant,
  kNumCategories
};

enum class RenderOverrunCategory {
  kNone,
  kFew,
  kSeveral,
  kMany,
  kConstant,
  kNumCategories
};

}  // namespace

void BlockProcessorMetrics::UpdateCapture(bool underrun) {
  ++capture_block_counter_;
  if (underrun) {
    ++render_buffer_underruns_;
  }

  if (capture_block_counter_ == kMetricsReportingIntervalBlocks) {
    metrics_reported_ = true;

    RenderUnderrunCategory underrun_category;
    if (render_buffer_underruns_ == 0) {
      underrun_category = RenderUnderrunCategory::kNone;
    } else if (render_buffer_underruns_ > (capture_block_counter_ >> 1)) {
      underrun_category = RenderUnderrunCategory::kConstant;
    } else if (render_buffer_underruns_ > 100) {
      underrun_category = RenderUnderrunCategory::kMany;
    } else if (render_buffer_underruns_ > 10) {
      underrun_category = RenderUnderrunCategory::kSeveral;
    } else {
      underrun_category = RenderUnderrunCategory::kFew;
    }
    RTC_HISTOGRAM_ENUMERATION(
        "WebRTC.Audio.EchoCanceller.RenderUnderruns",
        static_cast<int>(underrun_category),
        static_cast<int>(RenderUnderrunCategory::kNumCategories));

    RenderOverrunCategory overrun_category;
    if (render_buffer_overruns_ == 0) {
      overrun_category = RenderOverrunCategory::kNone;
    } else if (render_buffer_overruns_ > (buffer_render_calls_ >> 1)) {
      overrun_category = RenderOverrunCategory::kConstant;
    } else if (render_buffer_overruns_ > 100) {
      overrun_category = RenderOverrunCategory::kMany;
    } else if (render_buffer_overruns_ > 10) {
      overrun_category = RenderOverrunCategory::kSeveral;
    } else {
      overrun_category = RenderOverrunCategory::kFew;
    }
    RTC_HISTOGRAM_ENUMERATION(
        "WebRTC.Audio.EchoCanceller.RenderOverruns",
        static_cast<int>(overrun_category),
        static_cast<int>(RenderOverrunCategory::kNumCategories));

    ResetMetrics();
    capture_block_counter_ = 0;
  } else {
    metrics_reported_ = false;
  }
}

void BlockProcessorMetrics::UpdateRender(bool overrun) {
  ++buffer_render_calls_;
  if (overrun) {
    ++render_buffer_overruns_;
  }
}

void BlockProcessorMetrics::ResetMetrics() {
  render_buffer_underruns_ = 0;
  render_buffer_overruns_ = 0;
  buffer_render_calls_ = 0;
}

}  // namespace webrtc
