/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_DELAY_MANAGER_H_
#define MODULES_AUDIO_CODING_NETEQ_DELAY_MANAGER_H_

#include <string.h>  // Provide access to size_t.

#include <deque>
#include <memory>

#include "absl/types/optional.h"
#include "api/neteq/tick_timer.h"
#include "modules/audio_coding/neteq/histogram.h"
#include "modules/audio_coding/neteq/relative_arrival_delay_tracker.h"
#include "modules/audio_coding/neteq/reorder_optimizer.h"
#include "modules/audio_coding/neteq/underrun_optimizer.h"

namespace webrtc {

class DelayManager {
 public:
  struct Config {
    Config();
    void Log();

    // Options that can be configured via field trial.
    double quantile = 0.95;
    double forget_factor = 0.983;
    absl::optional<double> start_forget_weight = 2;
    absl::optional<int> resample_interval_ms = 500;
    int max_history_ms = 2000;

    bool use_reorder_optimizer = true;
    double reorder_forget_factor = 0.9993;
    int ms_per_loss_percent = 20;

    // Options that are externally populated.
    int max_packets_in_buffer = 200;
    int base_minimum_delay_ms = 0;
  };

  DelayManager(const Config& config, const TickTimer* tick_timer);

  virtual ~DelayManager();

  DelayManager(const DelayManager&) = delete;
  DelayManager& operator=(const DelayManager&) = delete;

  // Updates the delay manager with a new incoming packet, with `timestamp` from
  // the RTP header. This updates the statistics and a new target buffer level
  // is calculated. Returns the relative delay if it can be calculated. If
  // `reset` is true, restarts the relative arrival delay calculation from this
  // packet.
  virtual absl::optional<int> Update(uint32_t timestamp,
                                     int sample_rate_hz,
                                     bool reset = false);

  // Resets all state.
  virtual void Reset();

  // Gets the target buffer level in milliseconds.
  virtual int TargetDelayMs() const;

  // Notifies the DelayManager of how much audio data is carried in each packet.
  virtual int SetPacketAudioLength(int length_ms);

  // Accessors and mutators.
  // Assuming `delay` is in valid range.
  virtual bool SetMinimumDelay(int delay_ms);
  virtual bool SetMaximumDelay(int delay_ms);
  virtual bool SetBaseMinimumDelay(int delay_ms);
  virtual int GetBaseMinimumDelay() const;

  // These accessors are only intended for testing purposes.
  int effective_minimum_delay_ms_for_test() const {
    return effective_minimum_delay_ms_;
  }

 private:
  // Provides value which minimum delay can't exceed based on current buffer
  // size and given `maximum_delay_ms_`. Lower bound is a constant 0.
  int MinimumDelayUpperBound() const;

  // Updates `effective_minimum_delay_ms_` delay based on current
  // `minimum_delay_ms_`, `base_minimum_delay_ms_` and `maximum_delay_ms_`
  // and buffer size.
  void UpdateEffectiveMinimumDelay();

  // Makes sure that `delay_ms` is less than maximum delay, if any maximum
  // is set. Also, if possible check `delay_ms` to be less than 75% of
  // `max_packets_in_buffer_`.
  bool IsValidMinimumDelay(int delay_ms) const;

  bool IsValidBaseMinimumDelay(int delay_ms) const;

  // TODO(jakobi): set maximum buffer delay instead of number of packets.
  const int max_packets_in_buffer_;
  UnderrunOptimizer underrun_optimizer_;
  std::unique_ptr<ReorderOptimizer> reorder_optimizer_;
  RelativeArrivalDelayTracker relative_arrival_delay_tracker_;

  int base_minimum_delay_ms_;
  int effective_minimum_delay_ms_;  // Used as lower bound for target delay.
  int minimum_delay_ms_;            // Externally set minimum delay.
  int maximum_delay_ms_;            // Externally set maximum allowed delay.

  int packet_len_ms_ = 0;
  int target_level_ms_;  // Currently preferred buffer level.
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_DELAY_MANAGER_H_
