/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_DELAYABLE_H_
#define MEDIA_BASE_DELAYABLE_H_

#include <stdint.h>

#include "absl/types/optional.h"

namespace cricket {

// Delayable is used by user code through ApplyConstraints algorithm. Its
// methods must take precendence over similar functional in |syncable.h|.
class Delayable {
 public:
  virtual ~Delayable() {}
  // Set base minimum delay of the receive stream with specified ssrc.
  // Base minimum delay sets lower bound on minimum delay value which
  // determines minimum delay until audio playout.
  // Returns false if there is no stream with given ssrc.
  virtual bool SetBaseMinimumPlayoutDelayMs(uint32_t ssrc, int delay_ms) = 0;

  // Returns current value of base minimum delay in milliseconds.
  virtual absl::optional<int> GetBaseMinimumPlayoutDelayMs(
      uint32_t ssrc) const = 0;
};

}  // namespace cricket

#endif  // MEDIA_BASE_DELAYABLE_H_
