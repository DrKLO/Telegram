/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/expand.h"

#include <string.h>  // memset

#include <algorithm>  // min, max
#include <limits>     // numeric_limits<T>

#include "common_audio/signal_processing/include/signal_processing_library.h"
#include "modules/audio_coding/neteq/audio_multi_vector.h"
#include "modules/audio_coding/neteq/background_noise.h"
#include "modules/audio_coding/neteq/cross_correlation.h"
#include "modules/audio_coding/neteq/dsp_helper.h"
#include "modules/audio_coding/neteq/random_vector.h"
#include "modules/audio_coding/neteq/statistics_calculator.h"
#include "modules/audio_coding/neteq/sync_buffer.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {

Expand::Expand(BackgroundNoise* background_noise,
               SyncBuffer* sync_buffer,
               RandomVector* random_vector,
               StatisticsCalculator* statistics,
               int fs,
               size_t num_channels)
    : random_vector_(random_vector),
      sync_buffer_(sync_buffer),
      first_expand_(true),
      fs_hz_(fs),
      num_channels_(num_channels),
      consecutive_expands_(0),
      background_noise_(background_noise),
      statistics_(statistics),
      overlap_length_(5 * fs / 8000),
      lag_index_direction_(0),
      current_lag_index_(0),
      stop_muting_(false),
      expand_duration_samples_(0),
      channel_parameters_(new ChannelParameters[num_channels_]) {
  RTC_DCHECK(fs == 8000 || fs == 16000 || fs == 32000 || fs == 48000);
  RTC_DCHECK_LE(fs,
                static_cast<int>(kMaxSampleRate));  // Should not be possible.
  RTC_DCHECK_GT(num_channels_, 0);
  memset(expand_lags_, 0, sizeof(expand_lags_));
  Reset();
}

Expand::~Expand() = default;

void Expand::Reset() {
  first_expand_ = true;
  consecutive_expands_ = 0;
  max_lag_ = 0;
  for (size_t ix = 0; ix < num_channels_; ++ix) {
    channel_parameters_[ix].expand_vector0.Clear();
    channel_parameters_[ix].expand_vector1.Clear();
  }
}

int Expand::Process(AudioMultiVector* output) {
  int16_t random_vector[kMaxSampleRate / 8000 * 120 + 30];
  int16_t scaled_random_vector[kMaxSampleRate / 8000 * 125];
  static const int kTempDataSize = 3600;
  int16_t temp_data[kTempDataSize];  // TODO(hlundin) Remove this.
  int16_t* voiced_vector_storage = temp_data;
  int16_t* voiced_vector = &voiced_vector_storage[overlap_length_];
  static const size_t kNoiseLpcOrder = BackgroundNoise::kMaxLpcOrder;
  int16_t unvoiced_array_memory[kNoiseLpcOrder + kMaxSampleRate / 8000 * 125];
  int16_t* unvoiced_vector = unvoiced_array_memory + kUnvoicedLpcOrder;
  int16_t* noise_vector = unvoiced_array_memory + kNoiseLpcOrder;

  int fs_mult = fs_hz_ / 8000;

  if (first_expand_) {
    // Perform initial setup if this is the first expansion since last reset.
    AnalyzeSignal(random_vector);
    first_expand_ = false;
    expand_duration_samples_ = 0;
  } else {
    // This is not the first expansion, parameters are already estimated.
    // Extract a noise segment.
    size_t rand_length = max_lag_;
    // This only applies to SWB where length could be larger than 256.
    RTC_DCHECK_LE(rand_length, kMaxSampleRate / 8000 * 120 + 30);
    GenerateRandomVector(2, rand_length, random_vector);
  }

  // Generate signal.
  UpdateLagIndex();

  // Voiced part.
  // Generate a weighted vector with the current lag.
  size_t expansion_vector_length = max_lag_ + overlap_length_;
  size_t current_lag = expand_lags_[current_lag_index_];
  // Copy lag+overlap data.
  size_t expansion_vector_position =
      expansion_vector_length - current_lag - overlap_length_;
  size_t temp_length = current_lag + overlap_length_;
  for (size_t channel_ix = 0; channel_ix < num_channels_; ++channel_ix) {
    ChannelParameters& parameters = channel_parameters_[channel_ix];
    if (current_lag_index_ == 0) {
      // Use only expand_vector0.
      RTC_DCHECK_LE(expansion_vector_position + temp_length,
                    parameters.expand_vector0.Size());
      parameters.expand_vector0.CopyTo(temp_length, expansion_vector_position,
                                       voiced_vector_storage);
    } else if (current_lag_index_ == 1) {
      std::unique_ptr<int16_t[]> temp_0(new int16_t[temp_length]);
      parameters.expand_vector0.CopyTo(temp_length, expansion_vector_position,
                                       temp_0.get());
      std::unique_ptr<int16_t[]> temp_1(new int16_t[temp_length]);
      parameters.expand_vector1.CopyTo(temp_length, expansion_vector_position,
                                       temp_1.get());
      // Mix 3/4 of expand_vector0 with 1/4 of expand_vector1.
      WebRtcSpl_ScaleAndAddVectorsWithRound(temp_0.get(), 3, temp_1.get(), 1, 2,
                                            voiced_vector_storage, temp_length);
    } else if (current_lag_index_ == 2) {
      // Mix 1/2 of expand_vector0 with 1/2 of expand_vector1.
      RTC_DCHECK_LE(expansion_vector_position + temp_length,
                    parameters.expand_vector0.Size());
      RTC_DCHECK_LE(expansion_vector_position + temp_length,
                    parameters.expand_vector1.Size());

      std::unique_ptr<int16_t[]> temp_0(new int16_t[temp_length]);
      parameters.expand_vector0.CopyTo(temp_length, expansion_vector_position,
                                       temp_0.get());
      std::unique_ptr<int16_t[]> temp_1(new int16_t[temp_length]);
      parameters.expand_vector1.CopyTo(temp_length, expansion_vector_position,
                                       temp_1.get());
      WebRtcSpl_ScaleAndAddVectorsWithRound(temp_0.get(), 1, temp_1.get(), 1, 1,
                                            voiced_vector_storage, temp_length);
    }

    // Get tapering window parameters. Values are in Q15.
    int16_t muting_window, muting_window_increment;
    int16_t unmuting_window, unmuting_window_increment;
    if (fs_hz_ == 8000) {
      muting_window = DspHelper::kMuteFactorStart8kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement8kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart8kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement8kHz;
    } else if (fs_hz_ == 16000) {
      muting_window = DspHelper::kMuteFactorStart16kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement16kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart16kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement16kHz;
    } else if (fs_hz_ == 32000) {
      muting_window = DspHelper::kMuteFactorStart32kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement32kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart32kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement32kHz;
    } else {  // fs_ == 48000
      muting_window = DspHelper::kMuteFactorStart48kHz;
      muting_window_increment = DspHelper::kMuteFactorIncrement48kHz;
      unmuting_window = DspHelper::kUnmuteFactorStart48kHz;
      unmuting_window_increment = DspHelper::kUnmuteFactorIncrement48kHz;
    }

    // Smooth the expanded if it has not been muted to a low amplitude and
    // `current_voice_mix_factor` is larger than 0.5.
    if ((parameters.mute_factor > 819) &&
        (parameters.current_voice_mix_factor > 8192)) {
      size_t start_ix = sync_buffer_->Size() - overlap_length_;
      for (size_t i = 0; i < overlap_length_; i++) {
        // Do overlap add between new vector and overlap.
        (*sync_buffer_)[channel_ix][start_ix + i] =
            (((*sync_buffer_)[channel_ix][start_ix + i] * muting_window) +
             (((parameters.mute_factor * voiced_vector_storage[i]) >> 14) *
              unmuting_window) +
             16384) >>
            15;
        muting_window += muting_window_increment;
        unmuting_window += unmuting_window_increment;
      }
    } else if (parameters.mute_factor == 0) {
      // The expanded signal will consist of only comfort noise if
      // mute_factor = 0. Set the output length to 15 ms for best noise
      // production.
      // TODO(hlundin): This has been disabled since the length of
      // parameters.expand_vector0 and parameters.expand_vector1 no longer
      // match with expand_lags_, causing invalid reads and writes. Is it a good
      // idea to enable this again, and solve the vector size problem?
      //      max_lag_ = fs_mult * 120;
      //      expand_lags_[0] = fs_mult * 120;
      //      expand_lags_[1] = fs_mult * 120;
      //      expand_lags_[2] = fs_mult * 120;
    }

    // Unvoiced part.
    // Filter `scaled_random_vector` through `ar_filter_`.
    memcpy(unvoiced_vector - kUnvoicedLpcOrder, parameters.ar_filter_state,
           sizeof(int16_t) * kUnvoicedLpcOrder);
    int32_t add_constant = 0;
    if (parameters.ar_gain_scale > 0) {
      add_constant = 1 << (parameters.ar_gain_scale - 1);
    }
    WebRtcSpl_AffineTransformVector(scaled_random_vector, random_vector,
                                    parameters.ar_gain, add_constant,
                                    parameters.ar_gain_scale, current_lag);
    WebRtcSpl_FilterARFastQ12(scaled_random_vector, unvoiced_vector,
                              parameters.ar_filter, kUnvoicedLpcOrder + 1,
                              current_lag);
    memcpy(parameters.ar_filter_state,
           &(unvoiced_vector[current_lag - kUnvoicedLpcOrder]),
           sizeof(int16_t) * kUnvoicedLpcOrder);

    // Combine voiced and unvoiced contributions.

    // Set a suitable cross-fading slope.
    // For lag =
    //   <= 31 * fs_mult            => go from 1 to 0 in about 8 ms;
    //  (>= 31 .. <= 63) * fs_mult  => go from 1 to 0 in about 16 ms;
    //   >= 64 * fs_mult            => go from 1 to 0 in about 32 ms.
    // temp_shift = getbits(max_lag_) - 5.
    int temp_shift =
        (31 - WebRtcSpl_NormW32(rtc::dchecked_cast<int32_t>(max_lag_))) - 5;
    int16_t mix_factor_increment = 256 >> temp_shift;
    if (stop_muting_) {
      mix_factor_increment = 0;
    }

    // Create combined signal by shifting in more and more of unvoiced part.
    temp_shift = 8 - temp_shift;  // = getbits(mix_factor_increment).
    size_t temp_length =
        (parameters.current_voice_mix_factor - parameters.voice_mix_factor) >>
        temp_shift;
    temp_length = std::min(temp_length, current_lag);
    DspHelper::CrossFade(voiced_vector, unvoiced_vector, temp_length,
                         &parameters.current_voice_mix_factor,
                         mix_factor_increment, temp_data);

    // End of cross-fading period was reached before end of expanded signal
    // path. Mix the rest with a fixed mixing factor.
    if (temp_length < current_lag) {
      if (mix_factor_increment != 0) {
        parameters.current_voice_mix_factor = parameters.voice_mix_factor;
      }
      int16_t temp_scale = 16384 - parameters.current_voice_mix_factor;
      WebRtcSpl_ScaleAndAddVectorsWithRound(
          voiced_vector + temp_length, parameters.current_voice_mix_factor,
          unvoiced_vector + temp_length, temp_scale, 14,
          temp_data + temp_length, current_lag - temp_length);
    }

    // Select muting slope depending on how many consecutive expands we have
    // done.
    if (consecutive_expands_ == 3) {
      // Let the mute factor decrease from 1.0 to 0.95 in 6.25 ms.
      // mute_slope = 0.0010 / fs_mult in Q20.
      parameters.mute_slope = std::max(parameters.mute_slope, 1049 / fs_mult);
    }
    if (consecutive_expands_ == 7) {
      // Let the mute factor decrease from 1.0 to 0.90 in 6.25 ms.
      // mute_slope = 0.0020 / fs_mult in Q20.
      parameters.mute_slope = std::max(parameters.mute_slope, 2097 / fs_mult);
    }

    // Mute segment according to slope value.
    if ((consecutive_expands_ != 0) || !parameters.onset) {
      // Mute to the previous level, then continue with the muting.
      WebRtcSpl_AffineTransformVector(
          temp_data, temp_data, parameters.mute_factor, 8192, 14, current_lag);

      if (!stop_muting_) {
        DspHelper::MuteSignal(temp_data, parameters.mute_slope, current_lag);

        // Shift by 6 to go from Q20 to Q14.
        // TODO(hlundin): Adding 8192 before shifting 6 steps seems wrong.
        // Legacy.
        int16_t gain = static_cast<int16_t>(
            16384 - (((current_lag * parameters.mute_slope) + 8192) >> 6));
        gain = ((gain * parameters.mute_factor) + 8192) >> 14;

        // Guard against getting stuck with very small (but sometimes audible)
        // gain.
        if ((consecutive_expands_ > 3) && (gain >= parameters.mute_factor)) {
          parameters.mute_factor = 0;
        } else {
          parameters.mute_factor = gain;
        }
      }
    }

    // Background noise part.
    background_noise_->GenerateBackgroundNoise(
        random_vector, channel_ix, channel_parameters_[channel_ix].mute_slope,
        TooManyExpands(), current_lag, unvoiced_array_memory);

    // Add background noise to the combined voiced-unvoiced signal.
    for (size_t i = 0; i < current_lag; i++) {
      temp_data[i] = temp_data[i] + noise_vector[i];
    }
    if (channel_ix == 0) {
      output->AssertSize(current_lag);
    } else {
      RTC_DCHECK_EQ(output->Size(), current_lag);
    }
    (*output)[channel_ix].OverwriteAt(temp_data, current_lag, 0);
  }

  // Increase call number and cap it.
  consecutive_expands_ = consecutive_expands_ >= kMaxConsecutiveExpands
                             ? kMaxConsecutiveExpands
                             : consecutive_expands_ + 1;
  expand_duration_samples_ += output->Size();
  // Clamp the duration counter at 2 seconds.
  expand_duration_samples_ = std::min(expand_duration_samples_,
                                      rtc::dchecked_cast<size_t>(fs_hz_ * 2));
  return 0;
}

void Expand::SetParametersForNormalAfterExpand() {
  current_lag_index_ = 0;
  lag_index_direction_ = 0;
  stop_muting_ = true;  // Do not mute signal any more.
  statistics_->LogDelayedPacketOutageEvent(expand_duration_samples_, fs_hz_);
  statistics_->EndExpandEvent(fs_hz_);
}

void Expand::SetParametersForMergeAfterExpand() {
  current_lag_index_ = -1;  /* out of the 3 possible ones */
  lag_index_direction_ = 1; /* make sure we get the "optimal" lag */
  stop_muting_ = true;
  statistics_->EndExpandEvent(fs_hz_);
}

bool Expand::Muted() const {
  if (first_expand_ || stop_muting_)
    return false;
  RTC_DCHECK(channel_parameters_);
  for (size_t ch = 0; ch < num_channels_; ++ch) {
    if (channel_parameters_[ch].mute_factor != 0)
      return false;
  }
  return true;
}

size_t Expand::overlap_length() const {
  return overlap_length_;
}

void Expand::InitializeForAnExpandPeriod() {
  lag_index_direction_ = 1;
  current_lag_index_ = -1;
  stop_muting_ = false;
  random_vector_->set_seed_increment(1);
  consecutive_expands_ = 0;
  for (size_t ix = 0; ix < num_channels_; ++ix) {
    channel_parameters_[ix].current_voice_mix_factor = 16384;  // 1.0 in Q14.
    channel_parameters_[ix].mute_factor = 16384;               // 1.0 in Q14.
    // Start with 0 gain for background noise.
    background_noise_->SetMuteFactor(ix, 0);
  }
}

bool Expand::TooManyExpands() {
  return consecutive_expands_ >= kMaxConsecutiveExpands;
}

void Expand::AnalyzeSignal(int16_t* random_vector) {
  int32_t auto_correlation[kUnvoicedLpcOrder + 1];
  int16_t reflection_coeff[kUnvoicedLpcOrder];
  int16_t correlation_vector[kMaxSampleRate / 8000 * 102];
  size_t best_correlation_index[kNumCorrelationCandidates];
  int16_t best_correlation[kNumCorrelationCandidates];
  size_t best_distortion_index[kNumCorrelationCandidates];
  int16_t best_distortion[kNumCorrelationCandidates];
  int32_t correlation_vector2[(99 * kMaxSampleRate / 8000) + 1];
  int32_t best_distortion_w32[kNumCorrelationCandidates];
  static const size_t kNoiseLpcOrder = BackgroundNoise::kMaxLpcOrder;
  int16_t unvoiced_array_memory[kNoiseLpcOrder + kMaxSampleRate / 8000 * 125];
  int16_t* unvoiced_vector = unvoiced_array_memory + kUnvoicedLpcOrder;

  int fs_mult = fs_hz_ / 8000;

  // Pre-calculate common multiplications with fs_mult.
  size_t fs_mult_4 = static_cast<size_t>(fs_mult * 4);
  size_t fs_mult_20 = static_cast<size_t>(fs_mult * 20);
  size_t fs_mult_120 = static_cast<size_t>(fs_mult * 120);
  size_t fs_mult_dist_len = fs_mult * kDistortionLength;
  size_t fs_mult_lpc_analysis_len = fs_mult * kLpcAnalysisLength;

  const size_t signal_length = static_cast<size_t>(256 * fs_mult);

  const size_t audio_history_position = sync_buffer_->Size() - signal_length;
  std::unique_ptr<int16_t[]> audio_history(new int16_t[signal_length]);
  (*sync_buffer_)[0].CopyTo(signal_length, audio_history_position,
                            audio_history.get());

  // Initialize.
  InitializeForAnExpandPeriod();

  // Calculate correlation in downsampled domain (4 kHz sample rate).
  size_t correlation_length = 51;  // TODO(hlundin): Legacy bit-exactness.
  // If it is decided to break bit-exactness `correlation_length` should be
  // initialized to the return value of Correlation().
  Correlation(audio_history.get(), signal_length, correlation_vector);

  // Find peaks in correlation vector.
  DspHelper::PeakDetection(correlation_vector, correlation_length,
                           kNumCorrelationCandidates, fs_mult,
                           best_correlation_index, best_correlation);

  // Adjust peak locations; cross-correlation lags start at 2.5 ms
  // (20 * fs_mult samples).
  best_correlation_index[0] += fs_mult_20;
  best_correlation_index[1] += fs_mult_20;
  best_correlation_index[2] += fs_mult_20;

  // Calculate distortion around the `kNumCorrelationCandidates` best lags.
  int distortion_scale = 0;
  for (size_t i = 0; i < kNumCorrelationCandidates; i++) {
    size_t min_index =
        std::max(fs_mult_20, best_correlation_index[i] - fs_mult_4);
    size_t max_index =
        std::min(fs_mult_120 - 1, best_correlation_index[i] + fs_mult_4);
    best_distortion_index[i] = DspHelper::MinDistortion(
        &(audio_history[signal_length - fs_mult_dist_len]), min_index,
        max_index, fs_mult_dist_len, &best_distortion_w32[i]);
    distortion_scale = std::max(16 - WebRtcSpl_NormW32(best_distortion_w32[i]),
                                distortion_scale);
  }
  // Shift the distortion values to fit in 16 bits.
  WebRtcSpl_VectorBitShiftW32ToW16(best_distortion, kNumCorrelationCandidates,
                                   best_distortion_w32, distortion_scale);

  // Find the maximizing index `i` of the cost function
  // f[i] = best_correlation[i] / best_distortion[i].
  int32_t best_ratio = std::numeric_limits<int32_t>::min();
  size_t best_index = std::numeric_limits<size_t>::max();
  for (size_t i = 0; i < kNumCorrelationCandidates; ++i) {
    int32_t ratio;
    if (best_distortion[i] > 0) {
      ratio = (best_correlation[i] * (1 << 16)) / best_distortion[i];
    } else if (best_correlation[i] == 0) {
      ratio = 0;  // No correlation set result to zero.
    } else {
      ratio = std::numeric_limits<int32_t>::max();  // Denominator is zero.
    }
    if (ratio > best_ratio) {
      best_index = i;
      best_ratio = ratio;
    }
  }

  size_t distortion_lag = best_distortion_index[best_index];
  size_t correlation_lag = best_correlation_index[best_index];
  max_lag_ = std::max(distortion_lag, correlation_lag);

  // Calculate the exact best correlation in the range between
  // `correlation_lag` and `distortion_lag`.
  correlation_length = std::max(std::min(distortion_lag + 10, fs_mult_120),
                                static_cast<size_t>(60 * fs_mult));

  size_t start_index = std::min(distortion_lag, correlation_lag);
  size_t correlation_lags = static_cast<size_t>(
      WEBRTC_SPL_ABS_W16((distortion_lag - correlation_lag)) + 1);
  RTC_DCHECK_LE(correlation_lags, static_cast<size_t>(99 * fs_mult + 1));

  for (size_t channel_ix = 0; channel_ix < num_channels_; ++channel_ix) {
    ChannelParameters& parameters = channel_parameters_[channel_ix];
    if (channel_ix > 0) {
      // When channel_ix == 0, audio_history contains the correct audio. For the
      // other cases, we will have to copy the correct channel into
      // audio_history.
      (*sync_buffer_)[channel_ix].CopyTo(signal_length, audio_history_position,
                                         audio_history.get());
    }

    // Calculate suitable scaling.
    int16_t signal_max = WebRtcSpl_MaxAbsValueW16(
        &audio_history[signal_length - correlation_length - start_index -
                       correlation_lags],
        correlation_length + start_index + correlation_lags - 1);
    int correlation_scale =
        (31 - WebRtcSpl_NormW32(signal_max * signal_max)) +
        (31 - WebRtcSpl_NormW32(static_cast<int32_t>(correlation_length))) - 31;
    correlation_scale = std::max(0, correlation_scale);

    // Calculate the correlation, store in `correlation_vector2`.
    WebRtcSpl_CrossCorrelation(
        correlation_vector2,
        &(audio_history[signal_length - correlation_length]),
        &(audio_history[signal_length - correlation_length - start_index]),
        correlation_length, correlation_lags, correlation_scale, -1);

    // Find maximizing index.
    best_index = WebRtcSpl_MaxIndexW32(correlation_vector2, correlation_lags);
    int32_t max_correlation = correlation_vector2[best_index];
    // Compensate index with start offset.
    best_index = best_index + start_index;

    // Calculate energies.
    int32_t energy1 = WebRtcSpl_DotProductWithScale(
        &(audio_history[signal_length - correlation_length]),
        &(audio_history[signal_length - correlation_length]),
        correlation_length, correlation_scale);
    int32_t energy2 = WebRtcSpl_DotProductWithScale(
        &(audio_history[signal_length - correlation_length - best_index]),
        &(audio_history[signal_length - correlation_length - best_index]),
        correlation_length, correlation_scale);

    // Calculate the correlation coefficient between the two portions of the
    // signal.
    int32_t corr_coefficient;
    if ((energy1 > 0) && (energy2 > 0)) {
      int energy1_scale = std::max(16 - WebRtcSpl_NormW32(energy1), 0);
      int energy2_scale = std::max(16 - WebRtcSpl_NormW32(energy2), 0);
      // Make sure total scaling is even (to simplify scale factor after sqrt).
      if ((energy1_scale + energy2_scale) & 1) {
        // If sum is odd, add 1 to make it even.
        energy1_scale += 1;
      }
      int32_t scaled_energy1 = energy1 >> energy1_scale;
      int32_t scaled_energy2 = energy2 >> energy2_scale;
      int16_t sqrt_energy_product = static_cast<int16_t>(
          WebRtcSpl_SqrtFloor(scaled_energy1 * scaled_energy2));
      // Calculate max_correlation / sqrt(energy1 * energy2) in Q14.
      int cc_shift = 14 - (energy1_scale + energy2_scale) / 2;
      max_correlation = WEBRTC_SPL_SHIFT_W32(max_correlation, cc_shift);
      corr_coefficient =
          WebRtcSpl_DivW32W16(max_correlation, sqrt_energy_product);
      // Cap at 1.0 in Q14.
      corr_coefficient = std::min(16384, corr_coefficient);
    } else {
      corr_coefficient = 0;
    }

    // Extract the two vectors expand_vector0 and expand_vector1 from
    // `audio_history`.
    size_t expansion_length = max_lag_ + overlap_length_;
    const int16_t* vector1 = &(audio_history[signal_length - expansion_length]);
    const int16_t* vector2 = vector1 - distortion_lag;
    // Normalize the second vector to the same energy as the first.
    energy1 = WebRtcSpl_DotProductWithScale(vector1, vector1, expansion_length,
                                            correlation_scale);
    energy2 = WebRtcSpl_DotProductWithScale(vector2, vector2, expansion_length,
                                            correlation_scale);
    // Confirm that amplitude ratio sqrt(energy1 / energy2) is within 0.5 - 2.0,
    // i.e., energy1 / energy2 is within 0.25 - 4.
    int16_t amplitude_ratio;
    if ((energy1 / 4 < energy2) && (energy1 > energy2 / 4)) {
      // Energy constraint fulfilled. Use both vectors and scale them
      // accordingly.
      int32_t scaled_energy2 = std::max(16 - WebRtcSpl_NormW32(energy2), 0);
      int32_t scaled_energy1 = scaled_energy2 - 13;
      // Calculate scaled_energy1 / scaled_energy2 in Q13.
      int32_t energy_ratio =
          WebRtcSpl_DivW32W16(WEBRTC_SPL_SHIFT_W32(energy1, -scaled_energy1),
                              static_cast<int16_t>(energy2 >> scaled_energy2));
      // Calculate sqrt ratio in Q13 (sqrt of en1/en2 in Q26).
      amplitude_ratio =
          static_cast<int16_t>(WebRtcSpl_SqrtFloor(energy_ratio << 13));
      // Copy the two vectors and give them the same energy.
      parameters.expand_vector0.Clear();
      parameters.expand_vector0.PushBack(vector1, expansion_length);
      parameters.expand_vector1.Clear();
      if (parameters.expand_vector1.Size() < expansion_length) {
        parameters.expand_vector1.Extend(expansion_length -
                                         parameters.expand_vector1.Size());
      }
      std::unique_ptr<int16_t[]> temp_1(new int16_t[expansion_length]);
      WebRtcSpl_AffineTransformVector(
          temp_1.get(), const_cast<int16_t*>(vector2), amplitude_ratio, 4096,
          13, expansion_length);
      parameters.expand_vector1.OverwriteAt(temp_1.get(), expansion_length, 0);
    } else {
      // Energy change constraint not fulfilled. Only use last vector.
      parameters.expand_vector0.Clear();
      parameters.expand_vector0.PushBack(vector1, expansion_length);
      // Copy from expand_vector0 to expand_vector1.
      parameters.expand_vector0.CopyTo(&parameters.expand_vector1);
      // Set the energy_ratio since it is used by muting slope.
      if ((energy1 / 4 < energy2) || (energy2 == 0)) {
        amplitude_ratio = 4096;  // 0.5 in Q13.
      } else {
        amplitude_ratio = 16384;  // 2.0 in Q13.
      }
    }

    // Set the 3 lag values.
    if (distortion_lag == correlation_lag) {
      expand_lags_[0] = distortion_lag;
      expand_lags_[1] = distortion_lag;
      expand_lags_[2] = distortion_lag;
    } else {
      // `distortion_lag` and `correlation_lag` are not equal; use different
      // combinations of the two.
      // First lag is `distortion_lag` only.
      expand_lags_[0] = distortion_lag;
      // Second lag is the average of the two.
      expand_lags_[1] = (distortion_lag + correlation_lag) / 2;
      // Third lag is the average again, but rounding towards `correlation_lag`.
      if (distortion_lag > correlation_lag) {
        expand_lags_[2] = (distortion_lag + correlation_lag - 1) / 2;
      } else {
        expand_lags_[2] = (distortion_lag + correlation_lag + 1) / 2;
      }
    }

    // Calculate the LPC and the gain of the filters.

    // Calculate kUnvoicedLpcOrder + 1 lags of the auto-correlation function.
    size_t temp_index =
        signal_length - fs_mult_lpc_analysis_len - kUnvoicedLpcOrder;
    // Copy signal to temporary vector to be able to pad with leading zeros.
    int16_t* temp_signal =
        new int16_t[fs_mult_lpc_analysis_len + kUnvoicedLpcOrder];
    memset(temp_signal, 0,
           sizeof(int16_t) * (fs_mult_lpc_analysis_len + kUnvoicedLpcOrder));
    memcpy(&temp_signal[kUnvoicedLpcOrder],
           &audio_history[temp_index + kUnvoicedLpcOrder],
           sizeof(int16_t) * fs_mult_lpc_analysis_len);
    CrossCorrelationWithAutoShift(
        &temp_signal[kUnvoicedLpcOrder], &temp_signal[kUnvoicedLpcOrder],
        fs_mult_lpc_analysis_len, kUnvoicedLpcOrder + 1, -1, auto_correlation);
    delete[] temp_signal;

    // Verify that variance is positive.
    if (auto_correlation[0] > 0) {
      // Estimate AR filter parameters using Levinson-Durbin algorithm;
      // kUnvoicedLpcOrder + 1 filter coefficients.
      int16_t stability =
          WebRtcSpl_LevinsonDurbin(auto_correlation, parameters.ar_filter,
                                   reflection_coeff, kUnvoicedLpcOrder);

      // Keep filter parameters only if filter is stable.
      if (stability != 1) {
        // Set first coefficient to 4096 (1.0 in Q12).
        parameters.ar_filter[0] = 4096;
        // Set remaining `kUnvoicedLpcOrder` coefficients to zero.
        WebRtcSpl_MemSetW16(parameters.ar_filter + 1, 0, kUnvoicedLpcOrder);
      }
    }

    if (channel_ix == 0) {
      // Extract a noise segment.
      size_t noise_length;
      if (distortion_lag < 40) {
        noise_length = 2 * distortion_lag + 30;
      } else {
        noise_length = distortion_lag + 30;
      }
      if (noise_length <= RandomVector::kRandomTableSize) {
        memcpy(random_vector, RandomVector::kRandomTable,
               sizeof(int16_t) * noise_length);
      } else {
        // Only applies to SWB where length could be larger than
        // `kRandomTableSize`.
        memcpy(random_vector, RandomVector::kRandomTable,
               sizeof(int16_t) * RandomVector::kRandomTableSize);
        RTC_DCHECK_LE(noise_length, kMaxSampleRate / 8000 * 120 + 30);
        random_vector_->IncreaseSeedIncrement(2);
        random_vector_->Generate(
            noise_length - RandomVector::kRandomTableSize,
            &random_vector[RandomVector::kRandomTableSize]);
      }
    }

    // Set up state vector and calculate scale factor for unvoiced filtering.
    memcpy(parameters.ar_filter_state,
           &(audio_history[signal_length - kUnvoicedLpcOrder]),
           sizeof(int16_t) * kUnvoicedLpcOrder);
    memcpy(unvoiced_vector - kUnvoicedLpcOrder,
           &(audio_history[signal_length - 128 - kUnvoicedLpcOrder]),
           sizeof(int16_t) * kUnvoicedLpcOrder);
    WebRtcSpl_FilterMAFastQ12(&audio_history[signal_length - 128],
                              unvoiced_vector, parameters.ar_filter,
                              kUnvoicedLpcOrder + 1, 128);
    const int unvoiced_max_abs = [&] {
      const int16_t max_abs = WebRtcSpl_MaxAbsValueW16(unvoiced_vector, 128);
      // Since WebRtcSpl_MaxAbsValueW16 returns 2^15 - 1 when the input contains
      // -2^15, we have to conservatively bump the return value by 1
      // if it is 2^15 - 1.
      return max_abs == WEBRTC_SPL_WORD16_MAX ? max_abs + 1 : max_abs;
    }();
    // Pick the smallest n such that 2^n > unvoiced_max_abs; then the maximum
    // value of the dot product is less than 2^7 * 2^(2*n) = 2^(2*n + 7), so to
    // prevent overflows we want 2n + 7 <= 31, which means we should shift by
    // 2n + 7 - 31 bits, if this value is greater than zero.
    int unvoiced_prescale =
        std::max(0, 2 * WebRtcSpl_GetSizeInBits(unvoiced_max_abs) - 24);

    int32_t unvoiced_energy = WebRtcSpl_DotProductWithScale(
        unvoiced_vector, unvoiced_vector, 128, unvoiced_prescale);

    // Normalize `unvoiced_energy` to 28 or 29 bits to preserve sqrt() accuracy.
    int16_t unvoiced_scale = WebRtcSpl_NormW32(unvoiced_energy) - 3;
    // Make sure we do an odd number of shifts since we already have 7 shifts
    // from dividing with 128 earlier. This will make the total scale factor
    // even, which is suitable for the sqrt.
    unvoiced_scale += ((unvoiced_scale & 0x1) ^ 0x1);
    unvoiced_energy = WEBRTC_SPL_SHIFT_W32(unvoiced_energy, unvoiced_scale);
    int16_t unvoiced_gain =
        static_cast<int16_t>(WebRtcSpl_SqrtFloor(unvoiced_energy));
    parameters.ar_gain_scale =
        13 + (unvoiced_scale + 7 - unvoiced_prescale) / 2;
    parameters.ar_gain = unvoiced_gain;

    // Calculate voice_mix_factor from corr_coefficient.
    // Let x = corr_coefficient. Then, we compute:
    // if (x > 0.48)
    //   voice_mix_factor = (-5179 + 19931x - 16422x^2 + 5776x^3) / 4096;
    // else
    //   voice_mix_factor = 0;
    if (corr_coefficient > 7875) {
      int16_t x1, x2, x3;
      // `corr_coefficient` is in Q14.
      x1 = static_cast<int16_t>(corr_coefficient);
      x2 = (x1 * x1) >> 14;  // Shift 14 to keep result in Q14.
      x3 = (x1 * x2) >> 14;
      static const int kCoefficients[4] = {-5179, 19931, -16422, 5776};
      int32_t temp_sum = kCoefficients[0] * 16384;
      temp_sum += kCoefficients[1] * x1;
      temp_sum += kCoefficients[2] * x2;
      temp_sum += kCoefficients[3] * x3;
      parameters.voice_mix_factor =
          static_cast<int16_t>(std::min(temp_sum / 4096, 16384));
      parameters.voice_mix_factor =
          std::max(parameters.voice_mix_factor, static_cast<int16_t>(0));
    } else {
      parameters.voice_mix_factor = 0;
    }

    // Calculate muting slope. Reuse value from earlier scaling of
    // `expand_vector0` and `expand_vector1`.
    int16_t slope = amplitude_ratio;
    if (slope > 12288) {
      // slope > 1.5.
      // Calculate (1 - (1 / slope)) / distortion_lag =
      // (slope - 1) / (distortion_lag * slope).
      // `slope` is in Q13, so 1 corresponds to 8192. Shift up to Q25 before
      // the division.
      // Shift the denominator from Q13 to Q5 before the division. The result of
      // the division will then be in Q20.
      int16_t denom =
          rtc::saturated_cast<int16_t>((distortion_lag * slope) >> 8);
      int temp_ratio = WebRtcSpl_DivW32W16((slope - 8192) << 12, denom);
      if (slope > 14746) {
        // slope > 1.8.
        // Divide by 2, with proper rounding.
        parameters.mute_slope = (temp_ratio + 1) / 2;
      } else {
        // Divide by 8, with proper rounding.
        parameters.mute_slope = (temp_ratio + 4) / 8;
      }
      parameters.onset = true;
    } else {
      // Calculate (1 - slope) / distortion_lag.
      // Shift `slope` by 7 to Q20 before the division. The result is in Q20.
      parameters.mute_slope = WebRtcSpl_DivW32W16(
          (8192 - slope) * 128, static_cast<int16_t>(distortion_lag));
      if (parameters.voice_mix_factor <= 13107) {
        // Make sure the mute factor decreases from 1.0 to 0.9 in no more than
        // 6.25 ms.
        // mute_slope >= 0.005 / fs_mult in Q20.
        parameters.mute_slope = std::max(5243 / fs_mult, parameters.mute_slope);
      } else if (slope > 8028) {
        parameters.mute_slope = 0;
      }
      parameters.onset = false;
    }
  }
}

Expand::ChannelParameters::ChannelParameters()
    : mute_factor(16384),
      ar_gain(0),
      ar_gain_scale(0),
      voice_mix_factor(0),
      current_voice_mix_factor(0),
      onset(false),
      mute_slope(0) {
  memset(ar_filter, 0, sizeof(ar_filter));
  memset(ar_filter_state, 0, sizeof(ar_filter_state));
}

void Expand::Correlation(const int16_t* input,
                         size_t input_length,
                         int16_t* output) const {
  // Set parameters depending on sample rate.
  const int16_t* filter_coefficients;
  size_t num_coefficients;
  int16_t downsampling_factor;
  if (fs_hz_ == 8000) {
    num_coefficients = 3;
    downsampling_factor = 2;
    filter_coefficients = DspHelper::kDownsample8kHzTbl;
  } else if (fs_hz_ == 16000) {
    num_coefficients = 5;
    downsampling_factor = 4;
    filter_coefficients = DspHelper::kDownsample16kHzTbl;
  } else if (fs_hz_ == 32000) {
    num_coefficients = 7;
    downsampling_factor = 8;
    filter_coefficients = DspHelper::kDownsample32kHzTbl;
  } else {  // fs_hz_ == 48000.
    num_coefficients = 7;
    downsampling_factor = 12;
    filter_coefficients = DspHelper::kDownsample48kHzTbl;
  }

  // Correlate from lag 10 to lag 60 in downsampled domain.
  // (Corresponds to 20-120 for narrow-band, 40-240 for wide-band, and so on.)
  static const size_t kCorrelationStartLag = 10;
  static const size_t kNumCorrelationLags = 54;
  static const size_t kCorrelationLength = 60;
  // Downsample to 4 kHz sample rate.
  static const size_t kDownsampledLength =
      kCorrelationStartLag + kNumCorrelationLags + kCorrelationLength;
  int16_t downsampled_input[kDownsampledLength];
  static const size_t kFilterDelay = 0;
  WebRtcSpl_DownsampleFast(
      input + input_length - kDownsampledLength * downsampling_factor,
      kDownsampledLength * downsampling_factor, downsampled_input,
      kDownsampledLength, filter_coefficients, num_coefficients,
      downsampling_factor, kFilterDelay);

  // Normalize `downsampled_input` to using all 16 bits.
  int16_t max_value =
      WebRtcSpl_MaxAbsValueW16(downsampled_input, kDownsampledLength);
  int16_t norm_shift = 16 - WebRtcSpl_NormW32(max_value);
  WebRtcSpl_VectorBitShiftW16(downsampled_input, kDownsampledLength,
                              downsampled_input, norm_shift);

  int32_t correlation[kNumCorrelationLags];
  CrossCorrelationWithAutoShift(
      &downsampled_input[kDownsampledLength - kCorrelationLength],
      &downsampled_input[kDownsampledLength - kCorrelationLength -
                         kCorrelationStartLag],
      kCorrelationLength, kNumCorrelationLags, -1, correlation);

  // Normalize and move data from 32-bit to 16-bit vector.
  int32_t max_correlation =
      WebRtcSpl_MaxAbsValueW32(correlation, kNumCorrelationLags);
  int16_t norm_shift2 = static_cast<int16_t>(
      std::max(18 - WebRtcSpl_NormW32(max_correlation), 0));
  WebRtcSpl_VectorBitShiftW32ToW16(output, kNumCorrelationLags, correlation,
                                   norm_shift2);
}

void Expand::UpdateLagIndex() {
  current_lag_index_ = current_lag_index_ + lag_index_direction_;
  // Change direction if needed.
  if (current_lag_index_ <= 0) {
    lag_index_direction_ = 1;
  }
  if (current_lag_index_ >= kNumLags - 1) {
    lag_index_direction_ = -1;
  }
}

Expand* ExpandFactory::Create(BackgroundNoise* background_noise,
                              SyncBuffer* sync_buffer,
                              RandomVector* random_vector,
                              StatisticsCalculator* statistics,
                              int fs,
                              size_t num_channels) const {
  return new Expand(background_noise, sync_buffer, random_vector, statistics,
                    fs, num_channels);
}

void Expand::GenerateRandomVector(int16_t seed_increment,
                                  size_t length,
                                  int16_t* random_vector) {
  // TODO(turajs): According to hlundin The loop should not be needed. Should be
  // just as good to generate all of the vector in one call.
  size_t samples_generated = 0;
  const size_t kMaxRandSamples = RandomVector::kRandomTableSize;
  while (samples_generated < length) {
    size_t rand_length = std::min(length - samples_generated, kMaxRandSamples);
    random_vector_->IncreaseSeedIncrement(seed_increment);
    random_vector_->Generate(rand_length, &random_vector[samples_generated]);
    samples_generated += rand_length;
  }
}

}  // namespace webrtc
