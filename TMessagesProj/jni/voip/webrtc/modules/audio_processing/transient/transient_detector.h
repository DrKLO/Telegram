/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_DETECTOR_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_DETECTOR_H_

#include <stddef.h>

#include <deque>
#include <memory>

#include "modules/audio_processing/transient/moving_moments.h"
#include "modules/audio_processing/transient/wpd_tree.h"

namespace webrtc {

// This is an implementation of the transient detector described in "Causal
// Wavelet based transient detector".
// Calculates the log-likelihood of a transient to happen on a signal at any
// given time based on the previous samples; it uses a WPD tree to analyze the
// signal.  It preserves its state, so it can be multiple-called.
class TransientDetector {
 public:
  // TODO(chadan): The only supported wavelet is Daubechies 8 using a WPD tree
  // of 3 levels. Make an overloaded constructor to allow different wavelets and
  // depths of the tree. When needed.

  // Creates a wavelet based transient detector.
  TransientDetector(int sample_rate_hz);

  ~TransientDetector();

  // Calculates the log-likelihood of the existence of a transient in |data|.
  // |data_length| has to be equal to |samples_per_chunk_|.
  // Returns a value between 0 and 1, as a non linear representation of this
  // likelihood.
  // Returns a negative value on error.
  float Detect(const float* data,
               size_t data_length,
               const float* reference_data,
               size_t reference_length);

  bool using_reference() { return using_reference_; }

 private:
  float ReferenceDetectionValue(const float* data, size_t length);

  static const size_t kLevels = 3;
  static const size_t kLeaves = 1 << kLevels;

  size_t samples_per_chunk_;

  std::unique_ptr<WPDTree> wpd_tree_;
  size_t tree_leaves_data_length_;

  // A MovingMoments object is needed for each leaf in the WPD tree.
  std::unique_ptr<MovingMoments> moving_moments_[kLeaves];

  std::unique_ptr<float[]> first_moments_;
  std::unique_ptr<float[]> second_moments_;

  // Stores the last calculated moments from the previous detection.
  float last_first_moment_[kLeaves];
  float last_second_moment_[kLeaves];

  // We keep track of the previous results from the previous chunks, so it can
  // be used to effectively give results according to the |transient_length|.
  std::deque<float> previous_results_;

  // Number of chunks that are going to return only zeros at the beginning of
  // the detection. It helps to avoid infs and nans due to the lack of
  // information.
  int chunks_at_startup_left_to_delete_;

  float reference_energy_;

  bool using_reference_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_TRANSIENT_DETECTOR_H_
