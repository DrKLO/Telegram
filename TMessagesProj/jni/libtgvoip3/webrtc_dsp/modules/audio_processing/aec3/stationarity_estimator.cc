/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/stationarity_estimator.h"

#include <algorithm>
#include <array>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/aec3_common.h"
#include "modules/audio_processing/aec3/vector_buffer.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/atomicops.h"

namespace webrtc {

namespace {
constexpr float kMinNoisePower = 10.f;
constexpr int kHangoverBlocks = kNumBlocksPerSecond / 20;
constexpr int kNBlocksAverageInitPhase = 20;
constexpr int kNBlocksInitialPhase = kNumBlocksPerSecond * 2.;
}  // namespace

StationarityEstimator::StationarityEstimator()
    : data_dumper_(
          new ApmDataDumper(rtc::AtomicOps::Increment(&instance_count_))) {
  Reset();
}

StationarityEstimator::~StationarityEstimator() = default;

void StationarityEstimator::Reset() {
  noise_.Reset();
  hangovers_.fill(0);
  stationarity_flags_.fill(false);
}

// Update just the noise estimator. Usefull until the delay is known
void StationarityEstimator::UpdateNoiseEstimator(
    rtc::ArrayView<const float> spectrum) {
  noise_.Update(spectrum);
  data_dumper_->DumpRaw("aec3_stationarity_noise_spectrum", noise_.Spectrum());
  data_dumper_->DumpRaw("aec3_stationarity_is_block_stationary",
                        IsBlockStationary());
}

void StationarityEstimator::UpdateStationarityFlags(
    const VectorBuffer& spectrum_buffer,
    rtc::ArrayView<const float> render_reverb_contribution_spectrum,
    int idx_current,
    int num_lookahead) {
  std::array<int, kWindowLength> indexes;
  int num_lookahead_bounded = std::min(num_lookahead, kWindowLength - 1);
  int idx = idx_current;

  if (num_lookahead_bounded < kWindowLength - 1) {
    int num_lookback = (kWindowLength - 1) - num_lookahead_bounded;
    idx = spectrum_buffer.OffsetIndex(idx_current, num_lookback);
  }
  // For estimating the stationarity properties of the current frame, the
  // power for each band is accumulated for several consecutive spectra in the
  // method EstimateBandStationarity.
  // In order to avoid getting the indexes of the spectra for every band with
  // its associated overhead, those indexes are stored in an array and then use
  // when the estimation is done.
  indexes[0] = idx;
  for (size_t k = 1; k < indexes.size(); ++k) {
    indexes[k] = spectrum_buffer.DecIndex(indexes[k - 1]);
  }
  RTC_DCHECK_EQ(
      spectrum_buffer.DecIndex(indexes[kWindowLength - 1]),
      spectrum_buffer.OffsetIndex(idx_current, -(num_lookahead_bounded + 1)));

  for (size_t k = 0; k < stationarity_flags_.size(); ++k) {
    stationarity_flags_[k] = EstimateBandStationarity(
        spectrum_buffer, render_reverb_contribution_spectrum, indexes, k);
  }
  UpdateHangover();
  SmoothStationaryPerFreq();
}

bool StationarityEstimator::IsBlockStationary() const {
  float acum_stationarity = 0.f;
  RTC_DCHECK_EQ(stationarity_flags_.size(), kFftLengthBy2Plus1);
  for (size_t band = 0; band < stationarity_flags_.size(); ++band) {
    bool st = IsBandStationary(band);
    acum_stationarity += static_cast<float>(st);
  }
  return ((acum_stationarity * (1.f / kFftLengthBy2Plus1)) > 0.75f);
}

bool StationarityEstimator::EstimateBandStationarity(
    const VectorBuffer& spectrum_buffer,
    rtc::ArrayView<const float> reverb,
    const std::array<int, kWindowLength>& indexes,
    size_t band) const {
  constexpr float kThrStationarity = 10.f;
  float acum_power = 0.f;
  for (auto idx : indexes) {
    acum_power += spectrum_buffer.buffer[idx][band];
  }
  acum_power += reverb[band];
  float noise = kWindowLength * GetStationarityPowerBand(band);
  RTC_CHECK_LT(0.f, noise);
  bool stationary = acum_power < kThrStationarity * noise;
  data_dumper_->DumpRaw("aec3_stationarity_long_ratio", acum_power / noise);
  return stationary;
}

bool StationarityEstimator::AreAllBandsStationary() {
  for (auto b : stationarity_flags_) {
    if (!b)
      return false;
  }
  return true;
}

void StationarityEstimator::UpdateHangover() {
  bool reduce_hangover = AreAllBandsStationary();
  for (size_t k = 0; k < stationarity_flags_.size(); ++k) {
    if (!stationarity_flags_[k]) {
      hangovers_[k] = kHangoverBlocks;
    } else if (reduce_hangover) {
      hangovers_[k] = std::max(hangovers_[k] - 1, 0);
    }
  }
}

void StationarityEstimator::SmoothStationaryPerFreq() {
  std::array<bool, kFftLengthBy2Plus1> all_ahead_stationary_smooth;
  for (size_t k = 1; k < kFftLengthBy2Plus1 - 1; ++k) {
    all_ahead_stationary_smooth[k] = stationarity_flags_[k - 1] &&
                                     stationarity_flags_[k] &&
                                     stationarity_flags_[k + 1];
  }

  all_ahead_stationary_smooth[0] = all_ahead_stationary_smooth[1];
  all_ahead_stationary_smooth[kFftLengthBy2Plus1 - 1] =
      all_ahead_stationary_smooth[kFftLengthBy2Plus1 - 2];

  stationarity_flags_ = all_ahead_stationary_smooth;
}

int StationarityEstimator::instance_count_ = 0;

StationarityEstimator::NoiseSpectrum::NoiseSpectrum() {
  Reset();
}

StationarityEstimator::NoiseSpectrum::~NoiseSpectrum() = default;

void StationarityEstimator::NoiseSpectrum::Reset() {
  block_counter_ = 0;
  noise_spectrum_.fill(kMinNoisePower);
}

void StationarityEstimator::NoiseSpectrum::Update(
    rtc::ArrayView<const float> spectrum) {
  RTC_DCHECK_EQ(kFftLengthBy2Plus1, spectrum.size());
  ++block_counter_;
  float alpha = GetAlpha();
  for (size_t k = 0; k < spectrum.size(); ++k) {
    if (block_counter_ <= kNBlocksAverageInitPhase) {
      noise_spectrum_[k] += (1.f / kNBlocksAverageInitPhase) * spectrum[k];
    } else {
      noise_spectrum_[k] =
          UpdateBandBySmoothing(spectrum[k], noise_spectrum_[k], alpha);
    }
  }
}

float StationarityEstimator::NoiseSpectrum::GetAlpha() const {
  constexpr float kAlpha = 0.004f;
  constexpr float kAlphaInit = 0.04f;
  constexpr float kTiltAlpha = (kAlphaInit - kAlpha) / kNBlocksInitialPhase;

  if (block_counter_ > (kNBlocksInitialPhase + kNBlocksAverageInitPhase)) {
    return kAlpha;
  } else {
    return kAlphaInit -
           kTiltAlpha * (block_counter_ - kNBlocksAverageInitPhase);
  }
}

float StationarityEstimator::NoiseSpectrum::UpdateBandBySmoothing(
    float power_band,
    float power_band_noise,
    float alpha) const {
  float power_band_noise_updated = power_band_noise;
  if (power_band_noise < power_band) {
    RTC_DCHECK_GT(power_band, 0.f);
    float alpha_inc = alpha * (power_band_noise / power_band);
    if (block_counter_ > kNBlocksInitialPhase) {
      if (10.f * power_band_noise < power_band) {
        alpha_inc *= 0.1f;
      }
    }
    power_band_noise_updated += alpha_inc * (power_band - power_band_noise);
  } else {
    power_band_noise_updated += alpha * (power_band - power_band_noise);
    power_band_noise_updated =
        std::max(power_band_noise_updated, kMinNoisePower);
  }
  return power_band_noise_updated;
}

}  // namespace webrtc
