/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_VOICE_DETECTION_IMPL_H_
#define MODULES_AUDIO_PROCESSING_VOICE_DETECTION_IMPL_H_

#include <stddef.h>
#include <memory>

#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/criticalsection.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class AudioBuffer;

class VoiceDetectionImpl : public VoiceDetection {
 public:
  explicit VoiceDetectionImpl(rtc::CriticalSection* crit);
  ~VoiceDetectionImpl() override;

  // TODO(peah): Fold into ctor, once public API is removed.
  void Initialize(int sample_rate_hz);
  void ProcessCaptureAudio(AudioBuffer* audio);

  // VoiceDetection implementation.
  int Enable(bool enable) override;
  bool is_enabled() const override;
  int set_stream_has_voice(bool has_voice) override;
  bool stream_has_voice() const override;
  int set_likelihood(Likelihood likelihood) override;
  Likelihood likelihood() const override;
  int set_frame_size_ms(int size) override;
  int frame_size_ms() const override;

 private:
  class Vad;

  rtc::CriticalSection* const crit_;
  bool enabled_ RTC_GUARDED_BY(crit_) = false;
  bool stream_has_voice_ RTC_GUARDED_BY(crit_) = false;
  bool using_external_vad_ RTC_GUARDED_BY(crit_) = false;
  Likelihood likelihood_ RTC_GUARDED_BY(crit_) = kLowLikelihood;
  int frame_size_ms_ RTC_GUARDED_BY(crit_) = 10;
  size_t frame_size_samples_ RTC_GUARDED_BY(crit_) = 0;
  int sample_rate_hz_ RTC_GUARDED_BY(crit_) = 0;
  std::unique_ptr<Vad> vad_ RTC_GUARDED_BY(crit_);
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(VoiceDetectionImpl);
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_VOICE_DETECTION_IMPL_H_
