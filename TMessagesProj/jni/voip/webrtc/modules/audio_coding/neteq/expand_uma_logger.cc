/*  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/expand_uma_logger.h"

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
std::unique_ptr<TickTimer::Countdown> GetNewCountdown(
    const TickTimer& tick_timer,
    int logging_period_s) {
  return tick_timer.GetNewCountdown((logging_period_s * 1000) /
                                    tick_timer.ms_per_tick());
}
}  // namespace

ExpandUmaLogger::ExpandUmaLogger(absl::string_view uma_name,
                                 int logging_period_s,
                                 const TickTimer* tick_timer)
    : uma_name_(uma_name),
      logging_period_s_(logging_period_s),
      tick_timer_(*tick_timer),
      timer_(GetNewCountdown(tick_timer_, logging_period_s_)) {
  RTC_DCHECK(tick_timer);
  RTC_DCHECK_GT(logging_period_s_, 0);
}

ExpandUmaLogger::~ExpandUmaLogger() = default;

void ExpandUmaLogger::UpdateSampleCounter(uint64_t samples,
                                          int sample_rate_hz) {
  if ((last_logged_value_ && *last_logged_value_ > samples) ||
      sample_rate_hz_ != sample_rate_hz) {
    // Sanity checks. The incremental counter moved backwards, or sample rate
    // changed.
    last_logged_value_.reset();
  }
  last_value_ = samples;
  sample_rate_hz_ = sample_rate_hz;
  if (!last_logged_value_) {
    last_logged_value_ = absl::optional<uint64_t>(samples);
  }

  if (!timer_->Finished()) {
    // Not yet time to log.
    return;
  }

  RTC_DCHECK(last_logged_value_);
  RTC_DCHECK_GE(last_value_, *last_logged_value_);
  const uint64_t diff = last_value_ - *last_logged_value_;
  last_logged_value_ = absl::optional<uint64_t>(last_value_);
  // Calculate rate in percent.
  RTC_DCHECK_GT(sample_rate_hz, 0);
  const int rate = (100 * diff) / (sample_rate_hz * logging_period_s_);
  RTC_DCHECK_GE(rate, 0);
  RTC_DCHECK_LE(rate, 100);
  RTC_HISTOGRAM_PERCENTAGE_SPARSE(uma_name_, rate);
  timer_ = GetNewCountdown(tick_timer_, logging_period_s_);
}

}  // namespace webrtc
