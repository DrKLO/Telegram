/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/dsp_helper.h"

#include <string.h>  // Access to memset.

#include <algorithm>  // Access to min, max.

#include "common_audio/signal_processing/include/signal_processing_library.h"

namespace webrtc {

// Table of constants used in method DspHelper::ParabolicFit().
const int16_t DspHelper::kParabolaCoefficients[17][3] = {
    {120, 32, 64},   {140, 44, 75},   {150, 50, 80},   {160, 57, 85},
    {180, 72, 96},   {200, 89, 107},  {210, 98, 112},  {220, 108, 117},
    {240, 128, 128}, {260, 150, 139}, {270, 162, 144}, {280, 174, 149},
    {300, 200, 160}, {320, 228, 171}, {330, 242, 176}, {340, 257, 181},
    {360, 288, 192}};

// Filter coefficients used when downsampling from the indicated sample rates
// (8, 16, 32, 48 kHz) to 4 kHz. Coefficients are in Q12. The corresponding Q0
// values are provided in the comments before each array.

// Q0 values: {0.3, 0.4, 0.3}.
const int16_t DspHelper::kDownsample8kHzTbl[3] = {1229, 1638, 1229};

// Q0 values: {0.15, 0.2, 0.3, 0.2, 0.15}.
const int16_t DspHelper::kDownsample16kHzTbl[5] = {614, 819, 1229, 819, 614};

// Q0 values: {0.1425, 0.1251, 0.1525, 0.1628, 0.1525, 0.1251, 0.1425}.
const int16_t DspHelper::kDownsample32kHzTbl[7] = {584, 512, 625, 667,
                                                   625, 512, 584};

// Q0 values: {0.2487, 0.0952, 0.1042, 0.1074, 0.1042, 0.0952, 0.2487}.
const int16_t DspHelper::kDownsample48kHzTbl[7] = {1019, 390, 427, 440,
                                                   427,  390, 1019};

int DspHelper::RampSignal(const int16_t* input,
                          size_t length,
                          int factor,
                          int increment,
                          int16_t* output) {
  int factor_q20 = (factor << 6) + 32;
  // TODO(hlundin): Add 32 to factor_q20 when converting back to Q14?
  for (size_t i = 0; i < length; ++i) {
    output[i] = (factor * input[i] + 8192) >> 14;
    factor_q20 += increment;
    factor_q20 = std::max(factor_q20, 0);  // Never go negative.
    factor = std::min(factor_q20 >> 6, 16384);
  }
  return factor;
}

int DspHelper::RampSignal(int16_t* signal,
                          size_t length,
                          int factor,
                          int increment) {
  return RampSignal(signal, length, factor, increment, signal);
}

int DspHelper::RampSignal(AudioVector* signal,
                          size_t start_index,
                          size_t length,
                          int factor,
                          int increment) {
  int factor_q20 = (factor << 6) + 32;
  // TODO(hlundin): Add 32 to factor_q20 when converting back to Q14?
  for (size_t i = start_index; i < start_index + length; ++i) {
    (*signal)[i] = (factor * (*signal)[i] + 8192) >> 14;
    factor_q20 += increment;
    factor_q20 = std::max(factor_q20, 0);  // Never go negative.
    factor = std::min(factor_q20 >> 6, 16384);
  }
  return factor;
}

int DspHelper::RampSignal(AudioMultiVector* signal,
                          size_t start_index,
                          size_t length,
                          int factor,
                          int increment) {
  RTC_DCHECK_LE(start_index + length, signal->Size());
  if (start_index + length > signal->Size()) {
    // Wrong parameters. Do nothing and return the scale factor unaltered.
    return factor;
  }
  int end_factor = 0;
  // Loop over the channels, starting at the same `factor` each time.
  for (size_t channel = 0; channel < signal->Channels(); ++channel) {
    end_factor =
        RampSignal(&(*signal)[channel], start_index, length, factor, increment);
  }
  return end_factor;
}

void DspHelper::PeakDetection(int16_t* data,
                              size_t data_length,
                              size_t num_peaks,
                              int fs_mult,
                              size_t* peak_index,
                              int16_t* peak_value) {
  size_t min_index = 0;
  size_t max_index = 0;

  for (size_t i = 0; i <= num_peaks - 1; i++) {
    if (num_peaks == 1) {
      // Single peak.  The parabola fit assumes that an extra point is
      // available; worst case it gets a zero on the high end of the signal.
      // TODO(hlundin): This can potentially get much worse. It breaks the
      // API contract, that the length of `data` is `data_length`.
      data_length++;
    }

    peak_index[i] = WebRtcSpl_MaxIndexW16(data, data_length - 1);

    if (i != num_peaks - 1) {
      min_index = (peak_index[i] > 2) ? (peak_index[i] - 2) : 0;
      max_index = std::min(data_length - 1, peak_index[i] + 2);
    }

    if ((peak_index[i] != 0) && (peak_index[i] != (data_length - 2))) {
      ParabolicFit(&data[peak_index[i] - 1], fs_mult, &peak_index[i],
                   &peak_value[i]);
    } else {
      if (peak_index[i] == data_length - 2) {
        if (data[peak_index[i]] > data[peak_index[i] + 1]) {
          ParabolicFit(&data[peak_index[i] - 1], fs_mult, &peak_index[i],
                       &peak_value[i]);
        } else if (data[peak_index[i]] <= data[peak_index[i] + 1]) {
          // Linear approximation.
          peak_value[i] = (data[peak_index[i]] + data[peak_index[i] + 1]) >> 1;
          peak_index[i] = (peak_index[i] * 2 + 1) * fs_mult;
        }
      } else {
        peak_value[i] = data[peak_index[i]];
        peak_index[i] = peak_index[i] * 2 * fs_mult;
      }
    }

    if (i != num_peaks - 1) {
      memset(&data[min_index], 0,
             sizeof(data[0]) * (max_index - min_index + 1));
    }
  }
}

void DspHelper::ParabolicFit(int16_t* signal_points,
                             int fs_mult,
                             size_t* peak_index,
                             int16_t* peak_value) {
  uint16_t fit_index[13];
  if (fs_mult == 1) {
    fit_index[0] = 0;
    fit_index[1] = 8;
    fit_index[2] = 16;
  } else if (fs_mult == 2) {
    fit_index[0] = 0;
    fit_index[1] = 4;
    fit_index[2] = 8;
    fit_index[3] = 12;
    fit_index[4] = 16;
  } else if (fs_mult == 4) {
    fit_index[0] = 0;
    fit_index[1] = 2;
    fit_index[2] = 4;
    fit_index[3] = 6;
    fit_index[4] = 8;
    fit_index[5] = 10;
    fit_index[6] = 12;
    fit_index[7] = 14;
    fit_index[8] = 16;
  } else {
    fit_index[0] = 0;
    fit_index[1] = 1;
    fit_index[2] = 3;
    fit_index[3] = 4;
    fit_index[4] = 5;
    fit_index[5] = 7;
    fit_index[6] = 8;
    fit_index[7] = 9;
    fit_index[8] = 11;
    fit_index[9] = 12;
    fit_index[10] = 13;
    fit_index[11] = 15;
    fit_index[12] = 16;
  }

  //  num = -3 * signal_points[0] + 4 * signal_points[1] - signal_points[2];
  //  den =      signal_points[0] - 2 * signal_points[1] + signal_points[2];
  int32_t num =
      (signal_points[0] * -3) + (signal_points[1] * 4) - signal_points[2];
  int32_t den = signal_points[0] + (signal_points[1] * -2) + signal_points[2];
  int32_t temp = num * 120;
  int flag = 1;
  int16_t stp = kParabolaCoefficients[fit_index[fs_mult]][0] -
                kParabolaCoefficients[fit_index[fs_mult - 1]][0];
  int16_t strt = (kParabolaCoefficients[fit_index[fs_mult]][0] +
                  kParabolaCoefficients[fit_index[fs_mult - 1]][0]) /
                 2;
  int16_t lmt;
  if (temp < -den * strt) {
    lmt = strt - stp;
    while (flag) {
      if ((flag == fs_mult) || (temp > -den * lmt)) {
        *peak_value =
            (den * kParabolaCoefficients[fit_index[fs_mult - flag]][1] +
             num * kParabolaCoefficients[fit_index[fs_mult - flag]][2] +
             signal_points[0] * 256) /
            256;
        *peak_index = *peak_index * 2 * fs_mult - flag;
        flag = 0;
      } else {
        flag++;
        lmt -= stp;
      }
    }
  } else if (temp > -den * (strt + stp)) {
    lmt = strt + 2 * stp;
    while (flag) {
      if ((flag == fs_mult) || (temp < -den * lmt)) {
        int32_t temp_term_1 =
            den * kParabolaCoefficients[fit_index[fs_mult + flag]][1];
        int32_t temp_term_2 =
            num * kParabolaCoefficients[fit_index[fs_mult + flag]][2];
        int32_t temp_term_3 = signal_points[0] * 256;
        *peak_value = (temp_term_1 + temp_term_2 + temp_term_3) / 256;
        *peak_index = *peak_index * 2 * fs_mult + flag;
        flag = 0;
      } else {
        flag++;
        lmt += stp;
      }
    }
  } else {
    *peak_value = signal_points[1];
    *peak_index = *peak_index * 2 * fs_mult;
  }
}

size_t DspHelper::MinDistortion(const int16_t* signal,
                                size_t min_lag,
                                size_t max_lag,
                                size_t length,
                                int32_t* distortion_value) {
  size_t best_index = 0;
  int32_t min_distortion = WEBRTC_SPL_WORD32_MAX;
  for (size_t i = min_lag; i <= max_lag; i++) {
    int32_t sum_diff = 0;
    const int16_t* data1 = signal;
    const int16_t* data2 = signal - i;
    for (size_t j = 0; j < length; j++) {
      sum_diff += WEBRTC_SPL_ABS_W32(data1[j] - data2[j]);
    }
    // Compare with previous minimum.
    if (sum_diff < min_distortion) {
      min_distortion = sum_diff;
      best_index = i;
    }
  }
  *distortion_value = min_distortion;
  return best_index;
}

void DspHelper::CrossFade(const int16_t* input1,
                          const int16_t* input2,
                          size_t length,
                          int16_t* mix_factor,
                          int16_t factor_decrement,
                          int16_t* output) {
  int16_t factor = *mix_factor;
  int16_t complement_factor = 16384 - factor;
  for (size_t i = 0; i < length; i++) {
    output[i] =
        (factor * input1[i] + complement_factor * input2[i] + 8192) >> 14;
    factor -= factor_decrement;
    complement_factor += factor_decrement;
  }
  *mix_factor = factor;
}

void DspHelper::UnmuteSignal(const int16_t* input,
                             size_t length,
                             int16_t* factor,
                             int increment,
                             int16_t* output) {
  uint16_t factor_16b = *factor;
  int32_t factor_32b = (static_cast<int32_t>(factor_16b) << 6) + 32;
  for (size_t i = 0; i < length; i++) {
    output[i] = (factor_16b * input[i] + 8192) >> 14;
    factor_32b = std::max(factor_32b + increment, 0);
    factor_16b = std::min(16384, factor_32b >> 6);
  }
  *factor = factor_16b;
}

void DspHelper::MuteSignal(int16_t* signal, int mute_slope, size_t length) {
  int32_t factor = (16384 << 6) + 32;
  for (size_t i = 0; i < length; i++) {
    signal[i] = ((factor >> 6) * signal[i] + 8192) >> 14;
    factor -= mute_slope;
  }
}

int DspHelper::DownsampleTo4kHz(const int16_t* input,
                                size_t input_length,
                                size_t output_length,
                                int input_rate_hz,
                                bool compensate_delay,
                                int16_t* output) {
  // Set filter parameters depending on input frequency.
  // NOTE: The phase delay values are wrong compared to the true phase delay
  // of the filters. However, the error is preserved (through the +1 term) for
  // consistency.
  const int16_t* filter_coefficients;  // Filter coefficients.
  size_t filter_length;                // Number of coefficients.
  size_t filter_delay;                 // Phase delay in samples.
  int16_t factor;                      // Conversion rate (inFsHz / 8000).
  switch (input_rate_hz) {
    case 8000: {
      filter_length = 3;
      factor = 2;
      filter_coefficients = kDownsample8kHzTbl;
      filter_delay = 1 + 1;
      break;
    }
    case 16000: {
      filter_length = 5;
      factor = 4;
      filter_coefficients = kDownsample16kHzTbl;
      filter_delay = 2 + 1;
      break;
    }
    case 32000: {
      filter_length = 7;
      factor = 8;
      filter_coefficients = kDownsample32kHzTbl;
      filter_delay = 3 + 1;
      break;
    }
    case 48000: {
      filter_length = 7;
      factor = 12;
      filter_coefficients = kDownsample48kHzTbl;
      filter_delay = 3 + 1;
      break;
    }
    default: {
      RTC_DCHECK_NOTREACHED();
      return -1;
    }
  }

  if (!compensate_delay) {
    // Disregard delay compensation.
    filter_delay = 0;
  }

  // Returns -1 if input signal is too short; 0 otherwise.
  return WebRtcSpl_DownsampleFast(
      &input[filter_length - 1], input_length - filter_length + 1, output,
      output_length, filter_coefficients, filter_length, factor, filter_delay);
}

}  // namespace webrtc
