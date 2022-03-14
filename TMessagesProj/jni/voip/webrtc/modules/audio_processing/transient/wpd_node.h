/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_TRANSIENT_WPD_NODE_H_
#define MODULES_AUDIO_PROCESSING_TRANSIENT_WPD_NODE_H_

#include <memory>

namespace webrtc {

class FIRFilter;

// A single node of a Wavelet Packet Decomposition (WPD) tree.
class WPDNode {
 public:
  // Creates a WPDNode. The data vector will contain zeros. The filter will have
  // the coefficients provided.
  WPDNode(size_t length, const float* coefficients, size_t coefficients_length);
  ~WPDNode();

  // Updates the node data. `parent_data` / 2 must be equals to `length_`.
  // Returns 0 if correct, and -1 otherwise.
  int Update(const float* parent_data, size_t parent_data_length);

  const float* data() const { return data_.get(); }
  // Returns 0 if correct, and -1 otherwise.
  int set_data(const float* new_data, size_t length);
  size_t length() const { return length_; }

 private:
  std::unique_ptr<float[]> data_;
  size_t length_;
  std::unique_ptr<FIRFilter> filter_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_TRANSIENT_WPD_NODE_H_
