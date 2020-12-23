/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc2/vad_with_level.h"

#include <algorithm>
#include <array>
#include <cmath>

#include "api/array_view.h"
#include "common_audio/include/audio_util.h"
#include "common_audio/resampler/include/push_resampler.h"
#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/agc2/rnn_vad/common.h"
#include "modules/audio_processing/agc2/rnn_vad/features_extraction.h"
#include "modules/audio_processing/agc2/rnn_vad/rnn.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

using VoiceActivityDetector = VadLevelAnalyzer::VoiceActivityDetector;

// Default VAD that combines a resampler and the RNN VAD.
// Computes the speech probability on the first channel.
class Vad : public VoiceActivityDetector {
 public:
  Vad() = default;
  Vad(const Vad&) = delete;
  Vad& operator=(const Vad&) = delete;
  ~Vad() = default;

  float ComputeProbability(AudioFrameView<const float> frame) override {
    // The source number of channels is 1, because we always use the 1st
    // channel.
    resampler_.InitializeIfNeeded(
        /*sample_rate_hz=*/static_cast<int>(frame.samples_per_channel() * 100),
        rnn_vad::kSampleRate24kHz,
        /*num_channels=*/1);

    std::array<float, rnn_vad::kFrameSize10ms24kHz> work_frame;
    // Feed the 1st channel to the resampler.
    resampler_.Resample(frame.channel(0).data(), frame.samples_per_channel(),
                        work_frame.data(), rnn_vad::kFrameSize10ms24kHz);

    std::array<float, rnn_vad::kFeatureVectorSize> feature_vector;
    const bool is_silence = features_extractor_.CheckSilenceComputeFeatures(
        work_frame, feature_vector);
    return rnn_vad_.ComputeVadProbability(feature_vector, is_silence);
  }

 private:
  PushResampler<float> resampler_;
  rnn_vad::FeaturesExtractor features_extractor_;
  rnn_vad::RnnBasedVad rnn_vad_;
};

// Returns an updated version of `p_old` by using instant decay and the given
// `attack` on a new VAD probability value `p_new`.
float SmoothedVadProbability(float p_old, float p_new, float attack) {
  RTC_DCHECK_GT(attack, 0.f);
  RTC_DCHECK_LE(attack, 1.f);
  if (p_new < p_old || attack == 1.f) {
    // Instant decay (or no smoothing).
    return p_new;
  } else {
    // Attack phase.
    return attack * p_new + (1.f - attack) * p_old;
  }
}

}  // namespace

VadLevelAnalyzer::VadLevelAnalyzer()
    : VadLevelAnalyzer(kDefaultSmoothedVadProbabilityAttack,
                       std::make_unique<Vad>()) {}

VadLevelAnalyzer::VadLevelAnalyzer(float vad_probability_attack)
    : VadLevelAnalyzer(vad_probability_attack, std::make_unique<Vad>()) {}

VadLevelAnalyzer::VadLevelAnalyzer(float vad_probability_attack,
                                   std::unique_ptr<VoiceActivityDetector> vad)
    : vad_(std::move(vad)), vad_probability_attack_(vad_probability_attack) {
  RTC_DCHECK(vad_);
}

VadLevelAnalyzer::~VadLevelAnalyzer() = default;

VadLevelAnalyzer::Result VadLevelAnalyzer::AnalyzeFrame(
    AudioFrameView<const float> frame) {
  // Compute levels.
  float peak = 0.f;
  float rms = 0.f;
  for (const auto& x : frame.channel(0)) {
    peak = std::max(std::fabs(x), peak);
    rms += x * x;
  }
  // Compute smoothed speech probability.
  vad_probability_ = SmoothedVadProbability(
      /*p_old=*/vad_probability_, /*p_new=*/vad_->ComputeProbability(frame),
      vad_probability_attack_);
  return {vad_probability_,
          FloatS16ToDbfs(std::sqrt(rms / frame.samples_per_channel())),
          FloatS16ToDbfs(peak)};
}

}  // namespace webrtc
