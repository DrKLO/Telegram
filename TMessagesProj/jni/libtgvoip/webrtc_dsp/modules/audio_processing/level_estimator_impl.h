/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_LEVEL_ESTIMATOR_IMPL_H_
#define MODULES_AUDIO_PROCESSING_LEVEL_ESTIMATOR_IMPL_H_

#include <memory>

#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/criticalsection.h"

namespace webrtc {

class AudioBuffer;
class RmsLevel;

class LevelEstimatorImpl : public LevelEstimator {
 public:
  explicit LevelEstimatorImpl(rtc::CriticalSection* crit);
  ~LevelEstimatorImpl() override;

  // TODO(peah): Fold into ctor, once public API is removed.
  void Initialize();
  void ProcessStream(AudioBuffer* audio);

  // LevelEstimator implementation.
  int Enable(bool enable) override;
  bool is_enabled() const override;
  int RMS() override;

 private:
  rtc::CriticalSection* const crit_ = nullptr;
  bool enabled_ RTC_GUARDED_BY(crit_) = false;
  std::unique_ptr<RmsLevel> rms_ RTC_GUARDED_BY(crit_);
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(LevelEstimatorImpl);
};
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_LEVEL_ESTIMATOR_IMPL_H_
