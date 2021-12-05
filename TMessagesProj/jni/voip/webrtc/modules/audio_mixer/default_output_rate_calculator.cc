/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_mixer/default_output_rate_calculator.h"

#include <algorithm>
#include <iterator>

#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/checks.h"

namespace webrtc {

int DefaultOutputRateCalculator::CalculateOutputRateFromRange(
    rtc::ArrayView<const int> preferred_sample_rates) {
  if (preferred_sample_rates.empty()) {
    return DefaultOutputRateCalculator::kDefaultFrequency;
  }
  using NativeRate = AudioProcessing::NativeRate;
  const int maximal_frequency = *std::max_element(
      preferred_sample_rates.cbegin(), preferred_sample_rates.cend());

  RTC_DCHECK_LE(NativeRate::kSampleRate8kHz, maximal_frequency);
  RTC_DCHECK_GE(NativeRate::kSampleRate48kHz, maximal_frequency);

  static constexpr NativeRate native_rates[] = {
      NativeRate::kSampleRate8kHz, NativeRate::kSampleRate16kHz,
      NativeRate::kSampleRate32kHz, NativeRate::kSampleRate48kHz};
  const auto* rounded_up_index = std::lower_bound(
      std::begin(native_rates), std::end(native_rates), maximal_frequency);
  RTC_DCHECK(rounded_up_index != std::end(native_rates));
  return *rounded_up_index;
}
}  // namespace webrtc
