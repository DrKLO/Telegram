/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/noise_level_estimator.h"

#include <stddef.h>

#include <algorithm>
#include <cmath>
#include <numeric>

#include "api/array_view.h"
#include "common_audio/include/audio_util.h"
#include "modules/audio_processing/agc2/signal_classifier.h"
#include "modules/audio_processing/logging/apm_data_dumper.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {
constexpr int kFramesPerSecond = 100;

float FrameEnergy(const AudioFrameView<const float>& audio) {
  float energy = 0.0f;
  for (size_t k = 0; k < audio.num_channels(); ++k) {
    float channel_energy =
        std::accumulate(audio.channel(k).begin(), audio.channel(k).end(), 0.0f,
                        [](float a, float b) -> float { return a + b * b; });
    energy = std::max(channel_energy, energy);
  }
  return energy;
}

float EnergyToDbfs(float signal_energy, size_t num_samples) {
  const float rms = std::sqrt(signal_energy / num_samples);
  return FloatS16ToDbfs(rms);
}

class NoiseLevelEstimatorImpl : public NoiseLevelEstimator {
 public:
  NoiseLevelEstimatorImpl(ApmDataDumper* data_dumper)
      : data_dumper_(data_dumper), signal_classifier_(data_dumper) {
    // Initially assume that 48 kHz will be used. `Analyze()` will detect the
    // used sample rate and call `Initialize()` again if needed.
    Initialize(/*sample_rate_hz=*/48000);
  }
  NoiseLevelEstimatorImpl(const NoiseLevelEstimatorImpl&) = delete;
  NoiseLevelEstimatorImpl& operator=(const NoiseLevelEstimatorImpl&) = delete;
  ~NoiseLevelEstimatorImpl() = default;

  float Analyze(const AudioFrameView<const float>& frame) override {
    data_dumper_->DumpRaw("agc2_noise_level_estimator_hold_counter",
                          noise_energy_hold_counter_);
    const int sample_rate_hz =
        static_cast<int>(frame.samples_per_channel() * kFramesPerSecond);
    if (sample_rate_hz != sample_rate_hz_) {
      Initialize(sample_rate_hz);
    }
    const float frame_energy = FrameEnergy(frame);
    if (frame_energy <= 0.f) {
      RTC_DCHECK_GE(frame_energy, 0.f);
      data_dumper_->DumpRaw("agc2_noise_level_estimator_signal_type", -1);
      return EnergyToDbfs(noise_energy_, frame.samples_per_channel());
    }

    if (first_update_) {
      // Initialize the noise energy to the frame energy.
      first_update_ = false;
      data_dumper_->DumpRaw("agc2_noise_level_estimator_signal_type", -1);
      noise_energy_ = std::max(frame_energy, min_noise_energy_);
      return EnergyToDbfs(noise_energy_, frame.samples_per_channel());
    }

    const SignalClassifier::SignalType signal_type =
        signal_classifier_.Analyze(frame.channel(0));
    data_dumper_->DumpRaw("agc2_noise_level_estimator_signal_type",
                          static_cast<int>(signal_type));

    // Update the noise estimate in a minimum statistics-type manner.
    if (signal_type == SignalClassifier::SignalType::kStationary) {
      if (frame_energy > noise_energy_) {
        // Leak the estimate upwards towards the frame energy if no recent
        // downward update.
        noise_energy_hold_counter_ =
            std::max(noise_energy_hold_counter_ - 1, 0);

        if (noise_energy_hold_counter_ == 0) {
          constexpr float kMaxNoiseEnergyFactor = 1.01f;
          noise_energy_ =
              std::min(noise_energy_ * kMaxNoiseEnergyFactor, frame_energy);
        }
      } else {
        // Update smoothly downwards with a limited maximum update magnitude.
        constexpr float kMinNoiseEnergyFactor = 0.9f;
        constexpr float kNoiseEnergyDeltaFactor = 0.05f;
        noise_energy_ =
            std::max(noise_energy_ * kMinNoiseEnergyFactor,
                     noise_energy_ - kNoiseEnergyDeltaFactor *
                                         (noise_energy_ - frame_energy));
        // Prevent an energy increase for the next 10 seconds.
        constexpr int kNumFramesToEnergyIncreaseAllowed = 1000;
        noise_energy_hold_counter_ = kNumFramesToEnergyIncreaseAllowed;
      }
    } else {
      // TODO(bugs.webrtc.org/7494): Remove to not forget the estimated level.
      // For a non-stationary signal, leak the estimate downwards in order to
      // avoid estimate locking due to incorrect signal classification.
      noise_energy_ = noise_energy_ * 0.99f;
    }

    // Ensure a minimum of the estimate.
    noise_energy_ = std::max(noise_energy_, min_noise_energy_);
    return EnergyToDbfs(noise_energy_, frame.samples_per_channel());
  }

 private:
  void Initialize(int sample_rate_hz) {
    sample_rate_hz_ = sample_rate_hz;
    noise_energy_ = 1.0f;
    first_update_ = true;
    // Initialize the minimum noise energy to -84 dBFS.
    min_noise_energy_ = sample_rate_hz * 2.0f * 2.0f / kFramesPerSecond;
    noise_energy_hold_counter_ = 0;
    signal_classifier_.Initialize(sample_rate_hz);
  }

  ApmDataDumper* const data_dumper_;
  int sample_rate_hz_;
  float min_noise_energy_;
  bool first_update_;
  float noise_energy_;
  int noise_energy_hold_counter_;
  SignalClassifier signal_classifier_;
};

// Updates the noise floor with instant decay and slow attack. This tuning is
// specific for AGC2, so that (i) it can promptly increase the gain if the noise
// floor drops (instant decay) and (ii) in case of music or fast speech, due to
// which the noise floor can be overestimated, the gain reduction is slowed
// down.
float SmoothNoiseFloorEstimate(float current_estimate, float new_estimate) {
  constexpr float kAttack = 0.5f;
  if (current_estimate < new_estimate) {
    // Attack phase.
    return kAttack * new_estimate + (1.0f - kAttack) * current_estimate;
  }
  // Instant attack.
  return new_estimate;
}

class NoiseFloorEstimator : public NoiseLevelEstimator {
 public:
  // Update the noise floor every 5 seconds.
  static constexpr int kUpdatePeriodNumFrames = 500;
  static_assert(kUpdatePeriodNumFrames >= 200,
                "A too small value may cause noise level overestimation.");
  static_assert(kUpdatePeriodNumFrames <= 1500,
                "A too large value may make AGC2 slow at reacting to increased "
                "noise levels.");

  NoiseFloorEstimator(ApmDataDumper* data_dumper) : data_dumper_(data_dumper) {
    // Initially assume that 48 kHz will be used. `Analyze()` will detect the
    // used sample rate and call `Initialize()` again if needed.
    Initialize(/*sample_rate_hz=*/48000);
  }
  NoiseFloorEstimator(const NoiseFloorEstimator&) = delete;
  NoiseFloorEstimator& operator=(const NoiseFloorEstimator&) = delete;
  ~NoiseFloorEstimator() = default;

  float Analyze(const AudioFrameView<const float>& frame) override {
    // Detect sample rate changes.
    const int sample_rate_hz =
        static_cast<int>(frame.samples_per_channel() * kFramesPerSecond);
    if (sample_rate_hz != sample_rate_hz_) {
      Initialize(sample_rate_hz);
    }

    const float frame_energy = FrameEnergy(frame);
    if (frame_energy <= min_noise_energy_) {
      // Ignore frames when muted or below the minimum measurable energy.
      data_dumper_->DumpRaw("agc2_noise_floor_estimator_preliminary_level",
                            noise_energy_);
      return EnergyToDbfs(noise_energy_, frame.samples_per_channel());
    }

    if (preliminary_noise_energy_set_) {
      preliminary_noise_energy_ =
          std::min(preliminary_noise_energy_, frame_energy);
    } else {
      preliminary_noise_energy_ = frame_energy;
      preliminary_noise_energy_set_ = true;
    }
    data_dumper_->DumpRaw("agc2_noise_floor_estimator_preliminary_level",
                          preliminary_noise_energy_);

    if (counter_ == 0) {
      // Full period observed.
      first_period_ = false;
      // Update the estimated noise floor energy with the preliminary
      // estimation.
      noise_energy_ = SmoothNoiseFloorEstimate(
          /*current_estimate=*/noise_energy_,
          /*new_estimate=*/preliminary_noise_energy_);
      // Reset for a new observation period.
      counter_ = kUpdatePeriodNumFrames;
      preliminary_noise_energy_set_ = false;
    } else if (first_period_) {
      // While analyzing the signal during the initial period, continuously
      // update the estimated noise energy, which is monotonic.
      noise_energy_ = preliminary_noise_energy_;
      counter_--;
    } else {
      // During the observation period it's only allowed to lower the energy.
      noise_energy_ = std::min(noise_energy_, preliminary_noise_energy_);
      counter_--;
    }
    return EnergyToDbfs(noise_energy_, frame.samples_per_channel());
  }

 private:
  void Initialize(int sample_rate_hz) {
    sample_rate_hz_ = sample_rate_hz;
    first_period_ = true;
    preliminary_noise_energy_set_ = false;
    // Initialize the minimum noise energy to -84 dBFS.
    min_noise_energy_ = sample_rate_hz * 2.0f * 2.0f / kFramesPerSecond;
    preliminary_noise_energy_ = min_noise_energy_;
    noise_energy_ = min_noise_energy_;
    counter_ = kUpdatePeriodNumFrames;
  }

  ApmDataDumper* const data_dumper_;
  int sample_rate_hz_;
  float min_noise_energy_;
  bool first_period_;
  bool preliminary_noise_energy_set_;
  float preliminary_noise_energy_;
  float noise_energy_;
  int counter_;
};

}  // namespace

std::unique_ptr<NoiseLevelEstimator> CreateStationaryNoiseEstimator(
    ApmDataDumper* data_dumper) {
  return std::make_unique<NoiseLevelEstimatorImpl>(data_dumper);
}

std::unique_ptr<NoiseLevelEstimator> CreateNoiseFloorEstimator(
    ApmDataDumper* data_dumper) {
  return std::make_unique<NoiseFloorEstimator>(data_dumper);
}

}  // namespace webrtc
