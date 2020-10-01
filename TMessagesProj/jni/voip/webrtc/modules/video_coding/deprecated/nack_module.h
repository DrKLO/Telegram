/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_DEPRECATED_NACK_MODULE_H_
#define MODULES_VIDEO_CODING_DEPRECATED_NACK_MODULE_H_

#include <stdint.h>

#include <map>
#include <set>
#include <vector>

#include "api/units/time_delta.h"
#include "modules/include/module.h"
#include "modules/include/module_common_types.h"
#include "modules/video_coding/histogram.h"
#include "rtc_base/deprecation.h"
#include "rtc_base/numerics/sequence_number_util.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

class DEPRECATED_NackModule : public Module {
 public:
  DEPRECATED_NackModule(Clock* clock,
                        NackSender* nack_sender,
                        KeyFrameRequestSender* keyframe_request_sender);

  int OnReceivedPacket(uint16_t seq_num, bool is_keyframe);
  int OnReceivedPacket(uint16_t seq_num, bool is_keyframe, bool is_recovered);

  void ClearUpTo(uint16_t seq_num);
  void UpdateRtt(int64_t rtt_ms);
  void Clear();

  // Module implementation
  int64_t TimeUntilNextProcess() override;
  void Process() override;

 private:
  // Which fields to consider when deciding which packet to nack in
  // GetNackBatch.
  enum NackFilterOptions { kSeqNumOnly, kTimeOnly, kSeqNumAndTime };

  // This class holds the sequence number of the packet that is in the nack list
  // as well as the meta data about when it should be nacked and how many times
  // we have tried to nack this packet.
  struct NackInfo {
    NackInfo();
    NackInfo(uint16_t seq_num,
             uint16_t send_at_seq_num,
             int64_t created_at_time);

    uint16_t seq_num;
    uint16_t send_at_seq_num;
    int64_t created_at_time;
    int64_t sent_at_time;
    int retries;
  };

  struct BackoffSettings {
    BackoffSettings(TimeDelta min_retry, TimeDelta max_rtt, double base);
    static absl::optional<BackoffSettings> ParseFromFieldTrials();

    // Min time between nacks.
    const TimeDelta min_retry_interval;
    // Upper bound on link-delay considered for exponential backoff.
    const TimeDelta max_rtt;
    // Base for the exponential backoff.
    const double base;
  };

  void AddPacketsToNack(uint16_t seq_num_start, uint16_t seq_num_end)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Removes packets from the nack list until the next keyframe. Returns true
  // if packets were removed.
  bool RemovePacketsUntilKeyFrame() RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
  std::vector<uint16_t> GetNackBatch(NackFilterOptions options)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Update the reordering distribution.
  void UpdateReorderingStatistics(uint16_t seq_num)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Returns how many packets we have to wait in order to receive the packet
  // with probability |probabilty| or higher.
  int WaitNumberOfPackets(float probability) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  Mutex mutex_;
  Clock* const clock_;
  NackSender* const nack_sender_;
  KeyFrameRequestSender* const keyframe_request_sender_;

  // TODO(philipel): Some of the variables below are consistently used on a
  // known thread (e.g. see |initialized_|). Those probably do not need
  // synchronized access.
  std::map<uint16_t, NackInfo, DescendingSeqNumComp<uint16_t>> nack_list_
      RTC_GUARDED_BY(mutex_);
  std::set<uint16_t, DescendingSeqNumComp<uint16_t>> keyframe_list_
      RTC_GUARDED_BY(mutex_);
  std::set<uint16_t, DescendingSeqNumComp<uint16_t>> recovered_list_
      RTC_GUARDED_BY(mutex_);
  video_coding::Histogram reordering_histogram_ RTC_GUARDED_BY(mutex_);
  bool initialized_ RTC_GUARDED_BY(mutex_);
  int64_t rtt_ms_ RTC_GUARDED_BY(mutex_);
  uint16_t newest_seq_num_ RTC_GUARDED_BY(mutex_);

  // Only touched on the process thread.
  int64_t next_process_time_ms_;

  // Adds a delay before send nack on packet received.
  const int64_t send_nack_delay_ms_;

  const absl::optional<BackoffSettings> backoff_settings_;
};

using NackModule = RTC_DEPRECATED DEPRECATED_NackModule;

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_DEPRECATED_NACK_MODULE_H_
