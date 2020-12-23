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

#include "absl/types/optional.h"
#include "modules/audio_processing/agc2/agc2_common.h"
#include "rtc_base/numerics/safe_compare.h"

namespace webrtc {
namespace saturation_protector_impl {

// Ring buffer which only supports (i) push back and (ii) read oldest item.
class RingBuffer {
 public:
  bool operator==(const RingBuffer& b) const;
  inline bool operator!=(const RingBuffer& b) const { return !(*this == b); }

  // Maximum number of values that the buffer can contain.
  int Capacity() const { return buffer_.size(); }
  // Number of values in the buffer.
  int Size() const { return size_; }

  void Reset();
  // Pushes back `v`. If the buffer is full, the oldest value is replaced.
  void PushBack(float v);
  // Returns the oldest item in the buffer. Returns an empty value if the
  // buffer is empty.
  absl::optional<float> Front() const;

 private:
  inline int FrontIndex() const {
    return rtc::SafeEq(size_, buffer_.size()) ? next_ : 0;
  }
  // `buffer_` has `size_` elements (up to the size of `buffer_`) and `next_` is
  // the position where the next new value is written in `buffer_`.
  std::array<float, kPeakEnveloperBufferSize> buffer_;
  int next_ = 0;
  int size_ = 0;
};

}  // namespace saturation_protector_impl

// Saturation protector state. Exposed publicly for check-pointing and restore
// ops.
struct SaturationProtectorState {
  bool operator==(const SaturationProtectorState& s) const;
  inline bool operator!=(const SaturationProtectorState& s) const {
    return !(*this == s);
  }

  float margin_db;  // Recommended margin.
  saturation_protector_impl::RingBuffer peak_delay_buffer;
  float max_peaks_dbfs;
  int time_since_push_ms;  // Time since the last ring buffer push operation.
};

// Resets the saturation protector state.
void ResetSaturationProtectorState(float initial_margin_db,
                                   SaturationProtectorState& state);

// Updates `state` by analyzing the estimated speech level `speech_level_dbfs`
// and the peak power `speech_peak_dbfs` for an observed frame which is
// reliably classified as "speech". `state` must not be modified without calling
// this function.
void UpdateSaturationProtectorState(float speech_peak_dbfs,
                                    float speech_level_dbfs,
                                    SaturationProtectorState& state);

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC2_SATURATION_PROTECTOR_H_
