/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_MANAGER_H_
#define MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_MANAGER_H_

#include <memory>
#include <utility>

#include "api/neteq/tick_timer.h"
#include "modules/audio_coding/neteq/delay_manager.h"
#include "test/gmock.h"

namespace webrtc {

class MockDelayManager : public DelayManager {
 public:
  MockDelayManager(size_t max_packets_in_buffer,
                   int base_minimum_delay_ms,
                   int histogram_quantile,
                   absl::optional<int> resample_interval_ms,
                   int max_history_ms,
                   const TickTimer* tick_timer,
                   std::unique_ptr<Histogram> histogram)
      : DelayManager(max_packets_in_buffer,
                     base_minimum_delay_ms,
                     histogram_quantile,
                     resample_interval_ms,
                     max_history_ms,
                     tick_timer,
                     std::move(histogram)) {}
  MOCK_METHOD(int, TargetDelayMs, (), (const));
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_DELAY_MANAGER_H_
