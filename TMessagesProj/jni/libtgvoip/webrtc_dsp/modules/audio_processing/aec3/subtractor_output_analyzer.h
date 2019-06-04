/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AEC3_SUBTRACTOR_OUTPUT_ANALYZER_H_
#define MODULES_AUDIO_PROCESSING_AEC3_SUBTRACTOR_OUTPUT_ANALYZER_H_

#include "modules/audio_processing/aec3/subtractor_output.h"

namespace webrtc {

// Class for analyzing the properties subtractor output
class SubtractorOutputAnalyzer {
 public:
  SubtractorOutputAnalyzer();
  ~SubtractorOutputAnalyzer() = default;

  // Analyses the subtractor output.
  void Update(const SubtractorOutput& subtractor_output);

  bool ConvergedFilter() const {
    return main_filter_converged_ || shadow_filter_converged_;
  }

  bool DivergedFilter() const { return filter_diverged_; }

  // Handle echo path change.
  void HandleEchoPathChange();

 private:
  const bool strict_divergence_check_;
  bool shadow_filter_converged_ = false;
  bool main_filter_converged_ = false;
  bool filter_diverged_ = false;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AEC3_SUBTRACTOR_OUTPUT_ANALYZER_H_
