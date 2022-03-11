/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/time_stretch.h"

#include <algorithm>  // min, max
#include <memory>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/neteq/background_noise.h"
#include "modules/audio_coding/neteq/cross_correlation.h"
#include "modules/audio_coding/neteq/dsp_helper.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

TimeStretch::ReturnCodes TimeStretch::Process(const int16_t* input,
                                              size_t input_len,
                                              bool fast_mode,
                                              AudioMultiVector* output,
                                              size_t* length_change_samples) {
  // Pre-calculate common multiplication with `fs_mult_`.
  size_t fs_mult_120 =
      static_cast<size_t>(fs_mult_ * 120);  // Corresponds to 15 ms.

  const int16_t* signal;
  std::unique_ptr<int16_t[]> signal_array;
  size_t signal_len;
  if (num_channels_ == 1) {
    signal = input;
    signal_len = input_len;
  } else {
    // We want `signal` to be only the first channel of `input`, which is
    // interleaved. Thus, we take the first sample, skip forward `num_channels`
    // samples, and continue like that.
    signal_len = input_len / num_channels_;
    signal_array.reset(new int16_t[signal_len]);
    signal = signal_array.get();
    size_t j = kRefChannel;
    for (size_t i = 0; i < signal_len; ++i) {
      signal_array[i] = input[j];
      j += num_channels_;
    }
  }

  // Find maximum absolute value of input signal.
  max_input_value_ = WebRtcSpl_MaxAbsValueW16(signal, signal_len);

  // Downsample to 4 kHz sample rate and calculate auto-correlation.
  DspHelper::DownsampleTo4kHz(signal, signal_len, kDownsampledLen,
                              sample_rate_hz_, true /* compensate delay*/,
                              downsampled_input_);
  AutoCorrelation();

  // Find the strongest correlation peak.
  static const size_t kNumPeaks = 1;
  size_t peak_index;
  int16_t peak_value;
  DspHelper::PeakDetection(auto_correlation_, kCorrelationLen, kNumPeaks,
                           fs_mult_, &peak_index, &peak_value);
  // Assert that `peak_index` stays within boundaries.
  RTC_DCHECK_LE(peak_index, (2 * kCorrelationLen - 1) * fs_mult_);

  // Compensate peak_index for displaced starting position. The displacement
  // happens in AutoCorrelation(). Here, `kMinLag` is in the down-sampled 4 kHz
  // domain, while the `peak_index` is in the original sample rate; hence, the
  // multiplication by fs_mult_ * 2.
  peak_index += kMinLag * fs_mult_ * 2;
  // Assert that `peak_index` stays within boundaries.
  RTC_DCHECK_GE(peak_index, static_cast<size_t>(20 * fs_mult_));
  RTC_DCHECK_LE(peak_index,
                20 * fs_mult_ + (2 * kCorrelationLen - 1) * fs_mult_);

  // Calculate scaling to ensure that `peak_index` samples can be square-summed
  // without overflowing.
  int scaling = 31 - WebRtcSpl_NormW32(max_input_value_ * max_input_value_) -
                WebRtcSpl_NormW32(static_cast<int32_t>(peak_index));
  scaling = std::max(0, scaling);

  // `vec1` starts at 15 ms minus one pitch period.
  const int16_t* vec1 = &signal[fs_mult_120 - peak_index];
  // `vec2` start at 15 ms.
  const int16_t* vec2 = &signal[fs_mult_120];
  // Calculate energies for `vec1` and `vec2`, assuming they both contain
  // `peak_index` samples.
  int32_t vec1_energy =
      WebRtcSpl_DotProductWithScale(vec1, vec1, peak_index, scaling);
  int32_t vec2_energy =
      WebRtcSpl_DotProductWithScale(vec2, vec2, peak_index, scaling);

  // Calculate cross-correlation between `vec1` and `vec2`.
  int32_t cross_corr =
      WebRtcSpl_DotProductWithScale(vec1, vec2, peak_index, scaling);

  // Check if the signal seems to be active speech or not (simple VAD).
  bool active_speech =
      SpeechDetection(vec1_energy, vec2_energy, peak_index, scaling);

  int16_t best_correlation;
  if (!active_speech) {
    SetParametersForPassiveSpeech(signal_len, &best_correlation, &peak_index);
  } else {
    // Calculate correlation:
    // cross_corr / sqrt(vec1_energy * vec2_energy).

    // Start with calculating scale values.
    int energy1_scale = std::max(0, 16 - WebRtcSpl_NormW32(vec1_energy));
    int energy2_scale = std::max(0, 16 - WebRtcSpl_NormW32(vec2_energy));

    // Make sure total scaling is even (to simplify scale factor after sqrt).
    if ((energy1_scale + energy2_scale) & 1) {
      // The sum is odd.
      energy1_scale += 1;
    }

    // Scale energies to int16_t.
    int16_t vec1_energy_int16 =
        static_cast<int16_t>(vec1_energy >> energy1_scale);
    int16_t vec2_energy_int16 =
        static_cast<int16_t>(vec2_energy >> energy2_scale);

    // Calculate square-root of energy product.
    int16_t sqrt_energy_prod =
        WebRtcSpl_SqrtFloor(vec1_energy_int16 * vec2_energy_int16);

    // Calculate cross_corr / sqrt(en1*en2) in Q14.
    int temp_scale = 14 - (energy1_scale + energy2_scale) / 2;
    cross_corr = WEBRTC_SPL_SHIFT_W32(cross_corr, temp_scale);
    cross_corr = std::max(0, cross_corr);  // Don't use if negative.
    best_correlation = WebRtcSpl_DivW32W16(cross_corr, sqrt_energy_prod);
    // Make sure `best_correlation` is no larger than 1 in Q14.
    best_correlation = std::min(static_cast<int16_t>(16384), best_correlation);
  }

  // Check accelerate criteria and stretch the signal.
  ReturnCodes return_value =
      CheckCriteriaAndStretch(input, input_len, peak_index, best_correlation,
                              active_speech, fast_mode, output);
  switch (return_value) {
    case kSuccess:
      *length_change_samples = peak_index;
      break;
    case kSuccessLowEnergy:
      *length_change_samples = peak_index;
      break;
    case kNoStretch:
    case kError:
      *length_change_samples = 0;
      break;
  }
  return return_value;
}

void TimeStretch::AutoCorrelation() {
  // Calculate correlation from lag kMinLag to lag kMaxLag in 4 kHz domain.
  int32_t auto_corr[kCorrelationLen];
  CrossCorrelationWithAutoShift(
      &downsampled_input_[kMaxLag], &downsampled_input_[kMaxLag - kMinLag],
      kCorrelationLen, kMaxLag - kMinLag, -1, auto_corr);

  // Normalize correlation to 14 bits and write to `auto_correlation_`.
  int32_t max_corr = WebRtcSpl_MaxAbsValueW32(auto_corr, kCorrelationLen);
  int scaling = std::max(0, 17 - WebRtcSpl_NormW32(max_corr));
  WebRtcSpl_VectorBitShiftW32ToW16(auto_correlation_, kCorrelationLen,
                                   auto_corr, scaling);
}

bool TimeStretch::SpeechDetection(int32_t vec1_energy,
                                  int32_t vec2_energy,
                                  size_t peak_index,
                                  int scaling) const {
  // Check if the signal seems to be active speech or not (simple VAD).
  // If (vec1_energy + vec2_energy) / (2 * peak_index) <=
  // 8 * background_noise_energy, then we say that the signal contains no
  // active speech.
  // Rewrite the inequality as:
  // (vec1_energy + vec2_energy) / 16 <= peak_index * background_noise_energy.
  // The two sides of the inequality will be denoted `left_side` and
  // `right_side`.
  int32_t left_side = rtc::saturated_cast<int32_t>(
      (static_cast<int64_t>(vec1_energy) + vec2_energy) / 16);
  int32_t right_side;
  if (background_noise_.initialized()) {
    right_side = background_noise_.Energy(kRefChannel);
  } else {
    // If noise parameters have not been estimated, use a fixed threshold.
    right_side = 75000;
  }
  int right_scale = 16 - WebRtcSpl_NormW32(right_side);
  right_scale = std::max(0, right_scale);
  left_side = left_side >> right_scale;
  right_side =
      rtc::dchecked_cast<int32_t>(peak_index) * (right_side >> right_scale);

  // Scale `left_side` properly before comparing with `right_side`.
  // (`scaling` is the scale factor before energy calculation, thus the scale
  // factor for the energy is 2 * scaling.)
  if (WebRtcSpl_NormW32(left_side) < 2 * scaling) {
    // Cannot scale only `left_side`, must scale `right_side` too.
    int temp_scale = WebRtcSpl_NormW32(left_side);
    left_side = left_side << temp_scale;
    right_side = right_side >> (2 * scaling - temp_scale);
  } else {
    left_side = left_side << 2 * scaling;
  }
  return left_side > right_side;
}

}  // namespace webrtc
