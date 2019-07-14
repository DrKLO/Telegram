/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC2_SATURATION_PROTECTOR_H_
#define MODULES_AUDIO_PROCESSING_AGC2_SATURATION_PROTECTOR_H_

#include <array>

#include "modules/audio_processing/agc2/agc2_common.h"
#include "modules/audio_processing/agc2/vad_with_level.h"

namespace webrtc {

class ApmDataDumper;

class SaturationProtector {
 public:
  explicit SaturationProtector(ApmDataDumper* apm_data_dumper);

  SaturationProtector(ApmDataDumper* apm_data_dumper,
                      float extra_saturation_margin_db);

  // Update and return margin estimate. This method should be called
  // whenever a frame is reliably classified as 'speech'.
  //
  // Returned value is in DB scale.
  void UpdateMargin(const VadWithLevel::LevelAndProbability& vad_data,
                    float last_speech_level_estimate_dbfs);

  // Returns latest computed margin. Used in cases when speech is not
  // detected.
  float LastMargin() const;

  // Resets the internal memory.
  void Reset();

  void DebugDumpEstimate() const;

 private:
  // Computes a delayed envelope of peaks.
  class PeakEnveloper {
   public:
    PeakEnveloper();
    void Process(float frame_peak_dbfs);

    float Query() const;

   private:
    size_t speech_time_in_estimate_ms_ = 0;
    float current_superframe_peak_dbfs_ = -90.f;
    size_t elements_in_buffer_ = 0;
    std::array<float, kPeakEnveloperBufferSize> peak_delay_buffer_ = {};
  };

  ApmDataDumper* apm_data_dumper_;

  float last_margin_;
  PeakEnveloper peak_enveloper_;
  const float extra_saturation_margin_db_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_SATURATION_PROTECTOR_H_
