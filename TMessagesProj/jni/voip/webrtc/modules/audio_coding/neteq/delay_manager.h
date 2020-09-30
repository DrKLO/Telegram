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
#include "rtc_base/constructor_magic.h"

namespace webrtc {

class DelayManager {
 public:
  DelayManager(size_t max_packets_in_buffer,
               int base_minimum_delay_ms,
               int histogram_quantile,
               bool enable_rtx_handling,
               const TickTimer* tick_timer,
               std::unique_ptr<Histogram> histogram);

  // Create a DelayManager object. Notify the delay manager that the packet
  // buffer can hold no more than |max_packets_in_buffer| packets (i.e., this
  // is the number of packet slots in the buffer) and that the target delay
  // should be greater than or equal to |base_minimum_delay_ms|. Supply a
  // PeakDetector object to the DelayManager.
  static std::unique_ptr<DelayManager> Create(size_t max_packets_in_buffer,
                                              int base_minimum_delay_ms,
                                              bool enable_rtx_handling,
                                              const TickTimer* tick_timer);

  virtual ~DelayManager();

  // Updates the delay manager with a new incoming packet, with
  // |sequence_number| and |timestamp| from the RTP header. This updates the
  // inter-arrival time histogram and other statistics, as well as the
  // associated DelayPeakDetector. A new target buffer level is calculated.
  // Returns the relative delay if it can be calculated.
  virtual absl::optional<int> Update(uint16_t sequence_number,
                                     uint32_t timestamp,
                                     int sample_rate_hz);

  // Calculates a new target buffer level. Called from the Update() method.
  // Sets target_level_ (in Q8) and returns the same value. Also calculates
  // and updates base_target_level_, which is the target buffer level before
  // taking delay peaks into account.
  virtual int CalculateTargetLevel();

  // Notifies the DelayManager of how much audio data is carried in each packet.
  // The method updates the DelayPeakDetector too, and resets the inter-arrival
  // time counter. Returns 0 on success, -1 on failure.
  virtual int SetPacketAudioLength(int length_ms);

  // Resets the DelayManager and the associated DelayPeakDetector.
  virtual void Reset();

  // Reset the inter-arrival time counter to 0.
  virtual void ResetPacketIatCount();

  // Writes the lower and higher limits which the buffer level should stay
  // within to the corresponding pointers. The values are in (fractions of)
  // packets in Q8.
  virtual void BufferLimits(int* lower_limit, int* higher_limit) const;
  virtual void BufferLimits(int target_level,
                            int* lower_limit,
                            int* higher_limit) const;

  // Gets the target buffer level, in (fractions of) packets in Q8.
  virtual int TargetLevel() const;

  // Informs the delay manager whether or not the last decoded packet contained
  // speech.
  virtual void LastDecodedWasCngOrDtmf(bool it_was);

  // Notify the delay manager that empty packets have been received. These are
  // packets that are part of the sequence number series, so that an empty
  // packet will shift the sequence numbers for the following packets.
  virtual void RegisterEmptyPacket();

  // Accessors and mutators.
  // Assuming |delay| is in valid range.
  virtual bool SetMinimumDelay(int delay_ms);
  virtual bool SetMaximumDelay(int delay_ms);
  virtual bool SetBaseMinimumDelay(int delay_ms);
  virtual int GetBaseMinimumDelay() const;
  virtual int base_target_level() const;
  virtual int last_pack_cng_or_dtmf() const;
  virtual void set_last_pack_cng_or_dtmf(int value);

  // This accessor is only intended for testing purposes.
  int effective_minimum_delay_ms_for_test() const {
    return effective_minimum_delay_ms_;
  }

  // These accessors are only intended for testing purposes.
  int histogram_quantile() const { return histogram_quantile_; }
  Histogram* histogram() const { return histogram_.get(); }

 private:
  // Provides value which minimum delay can't exceed based on current buffer
  // size and given |maximum_delay_ms_|. Lower bound is a constant 0.
  int MinimumDelayUpperBound() const;

  // Provides 75% of currently possible maximum buffer size in milliseconds.
  int MaxBufferTimeQ75() const;

  // Updates |delay_history_|.
  void UpdateDelayHistory(int iat_delay_ms,
                          uint32_t timestamp,
                          int sample_rate_hz);

  // Calculate relative packet arrival delay from |delay_history_|.
  int CalculateRelativePacketArrivalDelay() const;

  // Updates |effective_minimum_delay_ms_| delay based on current
  // |minimum_delay_ms_|, |base_minimum_delay_ms_| and |maximum_delay_ms_|
  // and buffer size.
  void UpdateEffectiveMinimumDelay();

  // Makes sure that |target_level_| is not too large, taking
  // |max_packets_in_buffer_| into account. This method is called by Update().
  void LimitTargetLevel();

  // Makes sure that |delay_ms| is less than maximum delay, if any maximum
  // is set. Also, if possible check |delay_ms| to be less than 75% of
  // |max_packets_in_buffer_|.
  bool IsValidMinimumDelay(int delay_ms) const;

  bool IsValidBaseMinimumDelay(int delay_ms) const;

  bool first_packet_received_;
  const size_t max_packets_in_buffer_;  // Capacity of the packet buffer.
  std::unique_ptr<Histogram> histogram_;
  const int histogram_quantile_;
  const TickTimer* tick_timer_;
  int base_minimum_delay_ms_;
  // Provides delay which is used by LimitTargetLevel as lower bound on target
  // delay.
  int effective_minimum_delay_ms_;

  // Time elapsed since last packet.
  std::unique_ptr<TickTimer::Stopwatch> packet_iat_stopwatch_;
  int base_target_level_;  // Currently preferred buffer level before peak
                           // detection and streaming mode (Q0).
  // TODO(turajs) change the comment according to the implementation of
  // minimum-delay.
  int target_level_;         // Currently preferred buffer level in (fractions)
                             // of packets (Q8), before adding any extra delay.
  int packet_len_ms_;        // Length of audio in each incoming packet [ms].
  uint16_t last_seq_no_;     // Sequence number for last received packet.
  uint32_t last_timestamp_;  // Timestamp for the last received packet.
  int minimum_delay_ms_;     // Externally set minimum delay.
  int maximum_delay_ms_;     // Externally set maximum allowed delay.
  int last_pack_cng_or_dtmf_;
  const bool enable_rtx_handling_;
  int num_reordered_packets_ = 0;  // Number of consecutive reordered packets.

  struct PacketDelay {
    int iat_delay_ms;
    uint32_t timestamp;
  };
  std::deque<PacketDelay> delay_history_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DelayManager);
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_DELAY_MANAGER_H_
