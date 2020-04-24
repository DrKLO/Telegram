/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "common_audio/smoothing_filter.h"

#include <cmath>

#include "rtc_base/checks.h"
#include "rtc_base/timeutils.h"

namespace webrtc {

SmoothingFilterImpl::SmoothingFilterImpl(int init_time_ms)
    : init_time_ms_(init_time_ms),
      // Duing the initalization time, we use an increasing alpha. Specifically,
      //   alpha(n) = exp(-powf(init_factor_, n)),
      // where |init_factor_| is chosen such that
      //   alpha(init_time_ms_) = exp(-1.0f / init_time_ms_),
      init_factor_(init_time_ms_ == 0
                       ? 0.0f
                       : powf(init_time_ms_, -1.0f / init_time_ms_)),
      // |init_const_| is to a factor to help the calculation during
      // initialization phase.
      init_const_(init_time_ms_ == 0
                      ? 0.0f
                      : init_time_ms_ -
                            powf(init_time_ms_, 1.0f - 1.0f / init_time_ms_)) {
  UpdateAlpha(init_time_ms_);
}

SmoothingFilterImpl::~SmoothingFilterImpl() = default;

void SmoothingFilterImpl::AddSample(float sample) {
  const int64_t now_ms = rtc::TimeMillis();

  if (!init_end_time_ms_) {
    // This is equivalent to assuming the filter has been receiving the same
    // value as the first sample since time -infinity.
    state_ = last_sample_ = sample;
    init_end_time_ms_ = now_ms + init_time_ms_;
    last_state_time_ms_ = now_ms;
    return;
  }

  ExtrapolateLastSample(now_ms);
  last_sample_ = sample;
}

absl::optional<float> SmoothingFilterImpl::GetAverage() {
  if (!init_end_time_ms_) {
    // |init_end_time_ms_| undefined since we have not received any sample.
    return absl::nullopt;
  }
  ExtrapolateLastSample(rtc::TimeMillis());
  return state_;
}

bool SmoothingFilterImpl::SetTimeConstantMs(int time_constant_ms) {
  if (!init_end_time_ms_ || last_state_time_ms_ < *init_end_time_ms_) {
    return false;
  }
  UpdateAlpha(time_constant_ms);
  return true;
}

void SmoothingFilterImpl::UpdateAlpha(int time_constant_ms) {
  alpha_ = time_constant_ms == 0 ? 0.0f : exp(-1.0f / time_constant_ms);
}

void SmoothingFilterImpl::ExtrapolateLastSample(int64_t time_ms) {
  RTC_DCHECK_GE(time_ms, last_state_time_ms_);
  RTC_DCHECK(init_end_time_ms_);

  float multiplier = 0.0f;

  if (time_ms <= *init_end_time_ms_) {
    // Current update is to be made during initialization phase.
    // We update the state as if the |alpha| has been increased according
    //   alpha(n) = exp(-powf(init_factor_, n)),
    // where n is the time (in millisecond) since the first sample received.
    // With algebraic derivation as shown in the Appendix, we can find that the
    // state can be updated in a similar manner as if alpha is a constant,
    // except for a different multiplier.
    if (init_time_ms_ == 0) {
      // This means |init_factor_| = 0.
      multiplier = 0.0f;
    } else if (init_time_ms_ == 1) {
      // This means |init_factor_| = 1.
      multiplier = exp(last_state_time_ms_ - time_ms);
    } else {
      multiplier =
          exp(-(powf(init_factor_, last_state_time_ms_ - *init_end_time_ms_) -
                powf(init_factor_, time_ms - *init_end_time_ms_)) /
              init_const_);
    }
  } else {
    if (last_state_time_ms_ < *init_end_time_ms_) {
      // The latest state update was made during initialization phase.
      // We first extrapolate to the initialization time.
      ExtrapolateLastSample(*init_end_time_ms_);
      // Then extrapolate the rest by the following.
    }
    multiplier = powf(alpha_, time_ms - last_state_time_ms_);
  }

  state_ = multiplier * state_ + (1.0f - multiplier) * last_sample_;
  last_state_time_ms_ = time_ms;
}

}  // namespace webrtc

// Appendix: derivation of extrapolation during initialization phase.
// (LaTeX syntax)
// Assuming
//   \begin{align}
//     y(n) &= \alpha_{n-1} y(n-1) + \left(1 - \alpha_{n-1}\right) x(m) \\*
//          &= \left(\prod_{i=m}^{n-1} \alpha_i\right) y(m) +
//             \left(1 - \prod_{i=m}^{n-1} \alpha_i \right) x(m)
//   \end{align}
// Taking $\alpha_{n} = \exp(-\gamma^n)$, $\gamma$ denotes init\_factor\_, the
// multiplier becomes
//   \begin{align}
//     \prod_{i=m}^{n-1} \alpha_i
//     &= \exp\left(-\sum_{i=m}^{n-1} \gamma^i \right) \\*
//     &= \begin{cases}
//          \exp\left(-\frac{\gamma^m - \gamma^n}{1 - \gamma} \right)
//          & \gamma \neq 1 \\*
//          m-n & \gamma = 1
//        \end{cases}
//   \end{align}
// We know $\gamma = T^{-\frac{1}{T}}$, where $T$ denotes init\_time\_ms\_. Then
// $1 - \gamma$ approaches zero when $T$ increases. This can cause numerical
// difficulties. We multiply $T$ (if $T > 0$) to both numerator and denominator
// in the fraction. See.
//   \begin{align}
//     \frac{\gamma^m - \gamma^n}{1 - \gamma}
//     &= \frac{T^\frac{T-m}{T} - T^\frac{T-n}{T}}{T - T^{1-\frac{1}{T}}}
//   \end{align}
