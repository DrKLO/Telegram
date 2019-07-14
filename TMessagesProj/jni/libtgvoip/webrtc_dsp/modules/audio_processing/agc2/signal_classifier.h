/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_SIGNAL_CLASSIFIER_H_
#define MODULES_AUDIO_PROCESSING_AGC2_SIGNAL_CLASSIFIER_H_

#include <memory>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/agc2/down_sampler.h"
#include "modules/audio_processing/agc2/noise_spectrum_estimator.h"
#include "modules/audio_processing/utility/ooura_fft.h"
#include "rtc_base/constructormagic.h"

namespace webrtc {

class ApmDataDumper;
class AudioBuffer;

class SignalClassifier {
 public:
  enum class SignalType { kNonStationary, kStationary };

  explicit SignalClassifier(ApmDataDumper* data_dumper);
  ~SignalClassifier();

  void Initialize(int sample_rate_hz);
  SignalType Analyze(rtc::ArrayView<const float> signal);

 private:
  class FrameExtender {
   public:
    FrameExtender(size_t frame_size, size_t extended_frame_size);
    ~FrameExtender();

    void ExtendFrame(rtc::ArrayView<const float> x,
                     rtc::ArrayView<float> x_extended);

   private:
    std::vector<float> x_old_;

    RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(FrameExtender);
  };

  ApmDataDumper* const data_dumper_;
  DownSampler down_sampler_;
  std::unique_ptr<FrameExtender> frame_extender_;
  NoiseSpectrumEstimator noise_spectrum_estimator_;
  int sample_rate_hz_;
  int initialization_frames_left_;
  int consistent_classification_counter_;
  SignalType last_signal_type_;
  const OouraFft ooura_fft_;
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(SignalClassifier);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_SIGNAL_CLASSIFIER_H_
