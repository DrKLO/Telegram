/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/background_noise.h"

#include <assert.h>
#include <string.h>  // memcpy

#include <algorithm>  // min, max

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/neteq/audio_multi_vector.h"
#include "modules/audio_coding/neteq/cross_correlation.h"
#include "modules/audio_coding/neteq/post_decode_vad.h"

namespace webrtc {
namespace {

constexpr size_t kMaxSampleRate = 48000;

}  // namespace

// static
constexpr size_t BackgroundNoise::kMaxLpcOrder;

BackgroundNoise::BackgroundNoise(size_t num_channels)
    : num_channels_(num_channels),
      channel_parameters_(new ChannelParameters[num_channels_]) {
  Reset();
}

BackgroundNoise::~BackgroundNoise() {}

void BackgroundNoise::Reset() {
  initialized_ = false;
  for (size_t channel = 0; channel < num_channels_; ++channel) {
    channel_parameters_[channel].Reset();
  }
}

bool BackgroundNoise::Update(const AudioMultiVector& input,
                             const PostDecodeVad& vad) {
  bool filter_params_saved = false;
  if (vad.running() && vad.active_speech()) {
    // Do not update the background noise parameters if we know that the signal
    // is active speech.
    return filter_params_saved;
  }

  int32_t auto_correlation[kMaxLpcOrder + 1];
  int16_t fiter_output[kMaxLpcOrder + kResidualLength];
  int16_t reflection_coefficients[kMaxLpcOrder];
  int16_t lpc_coefficients[kMaxLpcOrder + 1];

  for (size_t channel_ix = 0; channel_ix < num_channels_; ++channel_ix) {
    ChannelParameters& parameters = channel_parameters_[channel_ix];
    int16_t temp_signal_array[kVecLen + kMaxLpcOrder] = {0};
    int16_t* temp_signal = &temp_signal_array[kMaxLpcOrder];
    RTC_DCHECK_GE(input.Size(), kVecLen);
    input[channel_ix].CopyTo(kVecLen, input.Size() - kVecLen, temp_signal);
    int32_t sample_energy =
        CalculateAutoCorrelation(temp_signal, kVecLen, auto_correlation);

    if ((!vad.running() &&
         sample_energy < parameters.energy_update_threshold) ||
        (vad.running() && !vad.active_speech())) {
      // Generate LPC coefficients.
      if (auto_correlation[0] <= 0) {
        // Center value in auto-correlation is not positive. Do not update.
        return filter_params_saved;
      }

      // Regardless of whether the filter is actually updated or not,
      // update energy threshold levels, since we have in fact observed
      // a low energy signal.
      if (sample_energy < parameters.energy_update_threshold) {
        // Never go under 1.0 in average sample energy.
        parameters.energy_update_threshold = std::max(sample_energy, 1);
        parameters.low_energy_update_threshold = 0;
      }

      // Only update BGN if filter is stable, i.e., if return value from
      // Levinson-Durbin function is 1.
      if (WebRtcSpl_LevinsonDurbin(auto_correlation, lpc_coefficients,
                                   reflection_coefficients,
                                   kMaxLpcOrder) != 1) {
        return filter_params_saved;
      }

      // Generate the CNG gain factor by looking at the energy of the residual.
      WebRtcSpl_FilterMAFastQ12(temp_signal + kVecLen - kResidualLength,
                                fiter_output, lpc_coefficients,
                                kMaxLpcOrder + 1, kResidualLength);
      int32_t residual_energy = WebRtcSpl_DotProductWithScale(
          fiter_output, fiter_output, kResidualLength, 0);

      // Check spectral flatness.
      // Comparing the residual variance with the input signal variance tells
      // if the spectrum is flat or not.
      // If 5 * residual_energy >= 16 * sample_energy, the spectrum is flat
      // enough.  Also ensure that the energy is non-zero.
      if ((sample_energy > 0) &&
          (int64_t{5} * residual_energy >= int64_t{16} * sample_energy)) {
        // Spectrum is flat enough; save filter parameters.
        // |temp_signal| + |kVecLen| - |kMaxLpcOrder| points at the first of the
        // |kMaxLpcOrder| samples in the residual signal, which will form the
        // filter state for the next noise generation.
        SaveParameters(channel_ix, lpc_coefficients,
                       temp_signal + kVecLen - kMaxLpcOrder, sample_energy,
                       residual_energy);
        filter_params_saved = true;
      }
    } else {
      // Will only happen if post-decode VAD is disabled and |sample_energy| is
      // not low enough. Increase the threshold for update so that it increases
      // by a factor 4 in 4 seconds.
      IncrementEnergyThreshold(channel_ix, sample_energy);
    }
  }
  return filter_params_saved;
}

void BackgroundNoise::GenerateBackgroundNoise(
    rtc::ArrayView<const int16_t> random_vector,
    size_t channel,
    int mute_slope,
    bool too_many_expands,
    size_t num_noise_samples,
    int16_t* buffer) {
  constexpr size_t kNoiseLpcOrder = kMaxLpcOrder;
  int16_t scaled_random_vector[kMaxSampleRate / 8000 * 125];
  assert(num_noise_samples <= (kMaxSampleRate / 8000 * 125));
  RTC_DCHECK_GE(random_vector.size(), num_noise_samples);
  int16_t* noise_samples = &buffer[kNoiseLpcOrder];
  if (initialized()) {
    // Use background noise parameters.
    memcpy(noise_samples - kNoiseLpcOrder, FilterState(channel),
           sizeof(int16_t) * kNoiseLpcOrder);

    int dc_offset = 0;
    if (ScaleShift(channel) > 1) {
      dc_offset = 1 << (ScaleShift(channel) - 1);
    }

    // Scale random vector to correct energy level.
    WebRtcSpl_AffineTransformVector(scaled_random_vector, random_vector.data(),
                                    Scale(channel), dc_offset,
                                    ScaleShift(channel), num_noise_samples);

    WebRtcSpl_FilterARFastQ12(scaled_random_vector, noise_samples,
                              Filter(channel), kNoiseLpcOrder + 1,
                              num_noise_samples);

    SetFilterState(
        channel,
        {&(noise_samples[num_noise_samples - kNoiseLpcOrder]), kNoiseLpcOrder});

    // Unmute the background noise.
    int16_t bgn_mute_factor = MuteFactor(channel);
    if (bgn_mute_factor < 16384) {
      WebRtcSpl_AffineTransformVector(noise_samples, noise_samples,
                                      bgn_mute_factor, 8192, 14,
                                      num_noise_samples);
    }
    // Update mute_factor in BackgroundNoise class.
    SetMuteFactor(channel, bgn_mute_factor);
  } else {
    // BGN parameters have not been initialized; use zero noise.
    memset(noise_samples, 0, sizeof(int16_t) * num_noise_samples);
  }
}

int32_t BackgroundNoise::Energy(size_t channel) const {
  assert(channel < num_channels_);
  return channel_parameters_[channel].energy;
}

void BackgroundNoise::SetMuteFactor(size_t channel, int16_t value) {
  assert(channel < num_channels_);
  channel_parameters_[channel].mute_factor = value;
}

int16_t BackgroundNoise::MuteFactor(size_t channel) const {
  assert(channel < num_channels_);
  return channel_parameters_[channel].mute_factor;
}

const int16_t* BackgroundNoise::Filter(size_t channel) const {
  assert(channel < num_channels_);
  return channel_parameters_[channel].filter;
}

const int16_t* BackgroundNoise::FilterState(size_t channel) const {
  assert(channel < num_channels_);
  return channel_parameters_[channel].filter_state;
}

void BackgroundNoise::SetFilterState(size_t channel,
                                     rtc::ArrayView<const int16_t> input) {
  assert(channel < num_channels_);
  size_t length = std::min(input.size(), kMaxLpcOrder);
  memcpy(channel_parameters_[channel].filter_state, input.data(),
         length * sizeof(int16_t));
}

int16_t BackgroundNoise::Scale(size_t channel) const {
  assert(channel < num_channels_);
  return channel_parameters_[channel].scale;
}
int16_t BackgroundNoise::ScaleShift(size_t channel) const {
  assert(channel < num_channels_);
  return channel_parameters_[channel].scale_shift;
}

int32_t BackgroundNoise::CalculateAutoCorrelation(
    const int16_t* signal,
    size_t length,
    int32_t* auto_correlation) const {
  static const int kCorrelationStep = -1;
  const int correlation_scale =
      CrossCorrelationWithAutoShift(signal, signal, length, kMaxLpcOrder + 1,
                                    kCorrelationStep, auto_correlation);

  // Number of shifts to normalize energy to energy/sample.
  int energy_sample_shift = kLogVecLen - correlation_scale;
  return auto_correlation[0] >> energy_sample_shift;
}

void BackgroundNoise::IncrementEnergyThreshold(size_t channel,
                                               int32_t sample_energy) {
  // TODO(hlundin): Simplify the below threshold update. What this code
  // does is simply "threshold += (increment * threshold) >> 16", but due
  // to the limited-width operations, it is not exactly the same. The
  // difference should be inaudible, but bit-exactness would not be
  // maintained.
  assert(channel < num_channels_);
  ChannelParameters& parameters = channel_parameters_[channel];
  int32_t temp_energy =
      (kThresholdIncrement * parameters.low_energy_update_threshold) >> 16;
  temp_energy +=
      kThresholdIncrement * (parameters.energy_update_threshold & 0xFF);
  temp_energy +=
      (kThresholdIncrement * ((parameters.energy_update_threshold >> 8) & 0xFF))
      << 8;
  parameters.low_energy_update_threshold += temp_energy;

  parameters.energy_update_threshold +=
      kThresholdIncrement * (parameters.energy_update_threshold >> 16);
  parameters.energy_update_threshold +=
      parameters.low_energy_update_threshold >> 16;
  parameters.low_energy_update_threshold =
      parameters.low_energy_update_threshold & 0x0FFFF;

  // Update maximum energy.
  // Decrease by a factor 1/1024 each time.
  parameters.max_energy = parameters.max_energy - (parameters.max_energy >> 10);
  if (sample_energy > parameters.max_energy) {
    parameters.max_energy = sample_energy;
  }

  // Set |energy_update_threshold| to no less than 60 dB lower than
  // |max_energy_|. Adding 524288 assures proper rounding.
  int32_t energy_update_threshold = (parameters.max_energy + 524288) >> 20;
  if (energy_update_threshold > parameters.energy_update_threshold) {
    parameters.energy_update_threshold = energy_update_threshold;
  }
}

void BackgroundNoise::SaveParameters(size_t channel,
                                     const int16_t* lpc_coefficients,
                                     const int16_t* filter_state,
                                     int32_t sample_energy,
                                     int32_t residual_energy) {
  assert(channel < num_channels_);
  ChannelParameters& parameters = channel_parameters_[channel];
  memcpy(parameters.filter, lpc_coefficients,
         (kMaxLpcOrder + 1) * sizeof(int16_t));
  memcpy(parameters.filter_state, filter_state, kMaxLpcOrder * sizeof(int16_t));
  // Save energy level and update energy threshold levels.
  // Never get under 1.0 in average sample energy.
  parameters.energy = std::max(sample_energy, 1);
  parameters.energy_update_threshold = parameters.energy;
  parameters.low_energy_update_threshold = 0;

  // Normalize residual_energy to 29 or 30 bits before sqrt.
  int16_t norm_shift = WebRtcSpl_NormW32(residual_energy) - 1;
  if (norm_shift & 0x1) {
    norm_shift -= 1;  // Even number of shifts required.
  }
  residual_energy = WEBRTC_SPL_SHIFT_W32(residual_energy, norm_shift);

  // Calculate scale and shift factor.
  parameters.scale = static_cast<int16_t>(WebRtcSpl_SqrtFloor(residual_energy));
  // Add 13 to the |scale_shift_|, since the random numbers table is in
  // Q13.
  // TODO(hlundin): Move the "13" to where the |scale_shift_| is used?
  parameters.scale_shift =
      static_cast<int16_t>(13 + ((kLogResidualLength + norm_shift) / 2));

  initialized_ = true;
}

}  // namespace webrtc
