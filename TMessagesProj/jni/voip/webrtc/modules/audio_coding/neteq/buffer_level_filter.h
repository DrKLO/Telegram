/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_BUFFER_LEVEL_FILTER_H_
#define MODULES_AUDIO_CODING_NETEQ_BUFFER_LEVEL_FILTER_H_

#include <stddef.h>

#include "rtc_base/constructor_magic.h"

namespace webrtc {

class BufferLevelFilter {
 public:
  BufferLevelFilter();
  virtual ~BufferLevelFilter() {}
  virtual void Reset();

  // Updates the filter. Current buffer size is |buffer_size_packets| (Q0).
  // |time_stretched_samples| is subtracted from the filtered value (thus
  // bypassing the filter operation).
  virtual void Update(size_t buffer_size_samples, int time_stretched_samples);

  // Set the current target buffer level in number of packets (obtained from
  // DelayManager::base_target_level()). Used to select the appropriate
  // filter coefficient.
  virtual void SetTargetBufferLevel(int target_buffer_level_packets);

  // Returns filtered current level in number of samples.
  virtual int filtered_current_level() const {
    // Round to nearest whole sample.
    return (filtered_current_level_ + (1 << 7)) >> 8;
  }

 private:
  int level_factor_;  // Filter factor for the buffer level filter in Q8.
  int filtered_current_level_;  // Filtered current buffer level in Q8.

  RTC_DISALLOW_COPY_AND_ASSIGN(BufferLevelFilter);
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_BUFFER_LEVEL_FILTER_H_
