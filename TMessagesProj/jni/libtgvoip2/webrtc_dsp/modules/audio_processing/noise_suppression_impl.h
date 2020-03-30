/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_NOISE_SUPPRESSION_IMPL_H_
#define MODULES_AUDIO_PROCESSING_NOISE_SUPPRESSION_IMPL_H_

#include <memory>
#include <vector>

#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/criticalsection.h"

namespace webrtc {

class AudioBuffer;

class NoiseSuppressionImpl : public NoiseSuppression {
 public:
  explicit NoiseSuppressionImpl(rtc::CriticalSection* crit);
  ~NoiseSuppressionImpl() override;

  // TODO(peah): Fold into ctor, once public API is removed.
  void Initialize(size_t channels, int sample_rate_hz);
  void AnalyzeCaptureAudio(AudioBuffer* audio);
  void ProcessCaptureAudio(AudioBuffer* audio);

  // NoiseSuppression implementation.
  int Enable(bool enable) override;
  bool is_enabled() const override;
  int set_level(Level level) override;
  Level level() const override;
  float speech_probability() const override;
  std::vector<float> NoiseEstimate() override;
  static size_t num_noise_bins();

 private:
  class Suppressor;
  rtc::CriticalSection* const crit_;
  bool enabled_ RTC_GUARDED_BY(crit_) = false;
  Level level_ RTC_GUARDED_BY(crit_) = kModerate;
  size_t channels_ RTC_GUARDED_BY(crit_) = 0;
  int sample_rate_hz_ RTC_GUARDED_BY(crit_) = 0;
  std::vector<std::unique_ptr<Suppressor>> suppressors_ RTC_GUARDED_BY(crit_);
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(NoiseSuppressionImpl);
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_NOISE_SUPPRESSION_IMPL_H_
