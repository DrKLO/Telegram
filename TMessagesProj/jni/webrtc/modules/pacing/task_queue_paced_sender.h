/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_PACING_TASK_QUEUE_PACED_SENDER_H_
#define MODULES_PACING_TASK_QUEUE_PACED_SENDER_H_

#include <stddef.h>
#include <stdint.h>

#include <functional>
#include <memory>
#include <queue>
#include <vector>

#include "absl/types/optional.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/include/module.h"
#include "modules/pacing/pacing_controller.h"
#include "modules/pacing/packet_router.h"
#include "modules/pacing/rtp_packet_pacer.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/synchronization/sequence_checker.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {
class Clock;
class RtcEventLog;

class TaskQueuePacedSender : public RtpPacketPacer, public RtpPacketSender {
 public:
  // The |hold_back_window| parameter sets a lower bound on time to sleep if
  // there is currently a pacer queue and packets can't immediately be
  // processed. Increasing this reduces thread wakeups at the expense of higher
  // latency.
  // TODO(bugs.webrtc.org/10809): Remove default value for hold_back_window.
  TaskQueuePacedSender(
      Clock* clock,
      PacketRouter* packet_router,
      RtcEventLog* event_log,
      const WebRtcKeyValueConfig* field_trials,
      TaskQueueFactory* task_queue_factory,
      TimeDelta hold_back_window = PacingController::kMinSleepTime);

  ~TaskQueuePacedSender() override;

  // Methods implementing RtpPacketSender.

  // Adds the packet to the queue and calls PacketRouter::SendPacket() when
  // it's time to send.
  void EnqueuePackets(
      std::vector<std::unique_ptr<RtpPacketToSend>> packets) override;

  // Methods implementing RtpPacketPacer:

  void CreateProbeCluster(DataRate bitrate, int cluster_id) override;

  // Temporarily pause all sending.
  void Pause() override;

  // Resume sending packets.
  void Resume() override;

  void SetCongestionWindow(DataSize congestion_window_size) override;
  void UpdateOutstandingData(DataSize outstanding_data) override;

  // Sets the pacing rates. Must be called once before packets can be sent.
  void SetPacingRates(DataRate pacing_rate, DataRate padding_rate) override;

  // Currently audio traffic is not accounted for by pacer and passed through.
  // With the introduction of audio BWE, audio traffic will be accounted for
  // in the pacer budget calculation. The audio traffic will still be injected
  // at high priority.
  void SetAccountForAudioPackets(bool account_for_audio) override;

  void SetIncludeOverhead() override;
  void SetTransportOverhead(DataSize overhead_per_packet) override;

  // Returns the time since the oldest queued packet was enqueued.
  TimeDelta OldestPacketWaitTime() const override;

  // Returns total size of all packets in the pacer queue.
  DataSize QueueSizeData() const override;

  // Returns the time when the first packet was sent;
  absl::optional<Timestamp> FirstSentPacketTime() const override;

  // Returns the number of milliseconds it will take to send the current
  // packets in the queue, given the current size and bitrate, ignoring prio.
  TimeDelta ExpectedQueueTime() const override;

  // Set the max desired queuing delay, pacer will override the pacing rate
  // specified by SetPacingRates() if needed to achieve this goal.
  void SetQueueTimeLimit(TimeDelta limit) override;

 protected:
  // Exposed as protected for test.
  struct Stats {
    Stats()
        : oldest_packet_wait_time(TimeDelta::Zero()),
          queue_size(DataSize::Zero()),
          expected_queue_time(TimeDelta::Zero()) {}
    TimeDelta oldest_packet_wait_time;
    DataSize queue_size;
    TimeDelta expected_queue_time;
    absl::optional<Timestamp> first_sent_packet_time;
  };
  virtual void OnStatsUpdated(const Stats& stats);

 private:
  // Check if it is time to send packets, or schedule a delayed task if not.
  // Use Timestamp::MinusInfinity() to indicate that this call has _not_
  // been scheduled by the pacing controller. If this is the case, check if
  // can execute immediately otherwise schedule a delay task that calls this
  // method again with desired (finite) scheduled process time.
  void MaybeProcessPackets(Timestamp scheduled_process_time);

  void MaybeUpdateStats(bool is_scheduled_call) RTC_RUN_ON(task_queue_);
  Stats GetStats() const;

  Clock* const clock_;
  const TimeDelta hold_back_window_;
  PacingController pacing_controller_ RTC_GUARDED_BY(task_queue_);

  // We want only one (valid) delayed process task in flight at a time.
  // If the value of |next_process_time_| is finite, it is an id for a
  // delayed task that will call MaybeProcessPackets() with that time
  // as parameter.
  // Timestamp::MinusInfinity() indicates no valid pending task.
  Timestamp next_process_time_ RTC_GUARDED_BY(task_queue_);

  // Since we don't want to support synchronous calls that wait for a
  // task execution, we poll the stats at some interval and update
  // |current_stats_|, which can in turn be polled at any time.

  // True iff there is delayed task in flight that that will call
  // UdpateStats().
  bool stats_update_scheduled_ RTC_GUARDED_BY(task_queue_);
  // Last time stats were updated.
  Timestamp last_stats_time_ RTC_GUARDED_BY(task_queue_);

  // Indicates if this task queue is shutting down. If so, don't allow
  // posting any more delayed tasks as that can cause the task queue to
  // never drain.
  bool is_shutdown_ RTC_GUARDED_BY(task_queue_);

  mutable Mutex stats_mutex_;
  Stats current_stats_ RTC_GUARDED_BY(stats_mutex_);

  rtc::TaskQueue task_queue_;
};
}  // namespace webrtc
#endif  // MODULES_PACING_TASK_QUEUE_PACED_SENDER_H_
