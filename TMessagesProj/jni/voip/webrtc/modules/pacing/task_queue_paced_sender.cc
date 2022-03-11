/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/pacing/task_queue_paced_sender.h"

#include <algorithm>
#include <utility>
#include "absl/memory/memory.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/trace_event.h"

namespace webrtc {

TaskQueuePacedSender::TaskQueuePacedSender(
    Clock* clock,
    PacingController::PacketSender* packet_sender,
    RtcEventLog* event_log,
    const WebRtcKeyValueConfig* field_trials,
    TaskQueueFactory* task_queue_factory,
    TimeDelta max_hold_back_window,
    int max_hold_back_window_in_packets)
    : clock_(clock),
      max_hold_back_window_(max_hold_back_window),
      max_hold_back_window_in_packets_(max_hold_back_window_in_packets),
      pacing_controller_(clock,
                         packet_sender,
                         event_log,
                         field_trials,
                         PacingController::ProcessMode::kDynamic),
      next_process_time_(Timestamp::MinusInfinity()),
      is_started_(false),
      is_shutdown_(false),
      packet_size_(/*alpha=*/0.95),
      task_queue_(task_queue_factory->CreateTaskQueue(
          "TaskQueuePacedSender",
          TaskQueueFactory::Priority::NORMAL)) {
  packet_size_.Apply(1, 0);
}

TaskQueuePacedSender::~TaskQueuePacedSender() {
  // Post an immediate task to mark the queue as shutting down.
  // The rtc::TaskQueue destructor will wait for pending tasks to
  // complete before continuing.
  task_queue_.PostTask([&]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    is_shutdown_ = true;
  });
}

void TaskQueuePacedSender::EnsureStarted() {
  task_queue_.PostTask([this]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    is_started_ = true;
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::CreateProbeCluster(DataRate bitrate,
                                              int cluster_id) {
  task_queue_.PostTask([this, bitrate, cluster_id]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.CreateProbeCluster(bitrate, cluster_id);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::Pause() {
  task_queue_.PostTask([this]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.Pause();
  });
}

void TaskQueuePacedSender::Resume() {
  task_queue_.PostTask([this]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.Resume();
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::SetCongestionWindow(
    DataSize congestion_window_size) {
  task_queue_.PostTask([this, congestion_window_size]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.SetCongestionWindow(congestion_window_size);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::UpdateOutstandingData(DataSize outstanding_data) {
  if (task_queue_.IsCurrent()) {
    RTC_DCHECK_RUN_ON(&task_queue_);
    // Fast path since this can be called once per sent packet while on the
    // task queue.
    pacing_controller_.UpdateOutstandingData(outstanding_data);
    MaybeProcessPackets(Timestamp::MinusInfinity());
    return;
  }

  task_queue_.PostTask([this, outstanding_data]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.UpdateOutstandingData(outstanding_data);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::SetPacingRates(DataRate pacing_rate,
                                          DataRate padding_rate) {
  task_queue_.PostTask([this, pacing_rate, padding_rate]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.SetPacingRates(pacing_rate, padding_rate);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::EnqueuePackets(
    std::vector<std::unique_ptr<RtpPacketToSend>> packets) {
#if RTC_TRACE_EVENTS_ENABLED
  TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("webrtc"),
               "TaskQueuePacedSender::EnqueuePackets");
  for (auto& packet : packets) {
    TRACE_EVENT2(TRACE_DISABLED_BY_DEFAULT("webrtc"),
                 "TaskQueuePacedSender::EnqueuePackets::Loop",
                 "sequence_number", packet->SequenceNumber(), "rtp_timestamp",
                 packet->Timestamp());
  }
#endif

  task_queue_.PostTask([this, packets_ = std::move(packets)]() mutable {
    RTC_DCHECK_RUN_ON(&task_queue_);
    for (auto& packet : packets_) {
      packet_size_.Apply(1, packet->size());
      RTC_DCHECK_GE(packet->capture_time(), Timestamp::Zero());
      pacing_controller_.EnqueuePacket(std::move(packet));
    }
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::SetAccountForAudioPackets(bool account_for_audio) {
  task_queue_.PostTask([this, account_for_audio]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.SetAccountForAudioPackets(account_for_audio);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::SetIncludeOverhead() {
  task_queue_.PostTask([this]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.SetIncludeOverhead();
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::SetTransportOverhead(DataSize overhead_per_packet) {
  task_queue_.PostTask([this, overhead_per_packet]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.SetTransportOverhead(overhead_per_packet);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

void TaskQueuePacedSender::SetQueueTimeLimit(TimeDelta limit) {
  task_queue_.PostTask([this, limit]() {
    RTC_DCHECK_RUN_ON(&task_queue_);
    pacing_controller_.SetQueueTimeLimit(limit);
    MaybeProcessPackets(Timestamp::MinusInfinity());
  });
}

TimeDelta TaskQueuePacedSender::ExpectedQueueTime() const {
  return GetStats().expected_queue_time;
}

DataSize TaskQueuePacedSender::QueueSizeData() const {
  return GetStats().queue_size;
}

absl::optional<Timestamp> TaskQueuePacedSender::FirstSentPacketTime() const {
  return GetStats().first_sent_packet_time;
}

TimeDelta TaskQueuePacedSender::OldestPacketWaitTime() const {
  Timestamp oldest_packet = GetStats().oldest_packet_enqueue_time;
  if (oldest_packet.IsInfinite())
    return TimeDelta::Zero();

  // (webrtc:9716): The clock is not always monotonic.
  Timestamp current = clock_->CurrentTime();
  if (current < oldest_packet)
    return TimeDelta::Zero();
  return current - oldest_packet;
}

void TaskQueuePacedSender::OnStatsUpdated(const Stats& stats) {
  MutexLock lock(&stats_mutex_);
  current_stats_ = stats;
}

void TaskQueuePacedSender::MaybeProcessPackets(
    Timestamp scheduled_process_time) {
  RTC_DCHECK_RUN_ON(&task_queue_);

  if (is_shutdown_ || !is_started_) {
    return;
  }

  // Normally, run ProcessPackets() only if this is the scheduled task.
  // If it is not but it is already time to process and there either is
  // no scheduled task or the schedule has shifted forward in time, run
  // anyway and clear any schedule.
  Timestamp next_process_time = pacing_controller_.NextSendTime();
  const Timestamp now = clock_->CurrentTime();
  const bool is_scheduled_call = next_process_time_ == scheduled_process_time;
  if (is_scheduled_call) {
    // Indicate no pending scheduled call.
    next_process_time_ = Timestamp::MinusInfinity();
  }
  if (is_scheduled_call ||
      (now >= next_process_time && (next_process_time_.IsInfinite() ||
                                    next_process_time < next_process_time_))) {
    pacing_controller_.ProcessPackets();
    next_process_time = pacing_controller_.NextSendTime();
  }

  TimeDelta hold_back_window = max_hold_back_window_;
  DataRate pacing_rate = pacing_controller_.pacing_rate();
  DataSize avg_packet_size = DataSize::Bytes(packet_size_.filtered());
  if (max_hold_back_window_in_packets_ > 0 && !pacing_rate.IsZero() &&
      !avg_packet_size.IsZero()) {
    TimeDelta avg_packet_send_time = avg_packet_size / pacing_rate;
    hold_back_window =
        std::min(hold_back_window,
                 avg_packet_send_time * max_hold_back_window_in_packets_);
  }

  absl::optional<TimeDelta> time_to_next_process;
  if (pacing_controller_.IsProbing() &&
      next_process_time != next_process_time_) {
    // If we're probing and there isn't already a wakeup scheduled for the next
    // process time, always post a task and just round sleep time down to
    // nearest millisecond.
    if (next_process_time.IsMinusInfinity()) {
      time_to_next_process = TimeDelta::Zero();
    } else {
      time_to_next_process =
          std::max(TimeDelta::Zero(),
                   (next_process_time - now).RoundDownTo(TimeDelta::Millis(1)));
    }
  } else if (next_process_time_.IsMinusInfinity() ||
             next_process_time <= next_process_time_ - hold_back_window) {
    // Schedule a new task since there is none currently scheduled
    // (`next_process_time_` is infinite), or the new process time is at least
    // one holdback window earlier than whatever is currently scheduled.
    time_to_next_process = std::max(next_process_time - now, hold_back_window);
  }

  if (time_to_next_process) {
    // Set a new scheduled process time and post a delayed task.
    next_process_time_ = next_process_time;

    task_queue_.PostDelayedHighPrecisionTask(
        [this, next_process_time]() { MaybeProcessPackets(next_process_time); },
        time_to_next_process->ms<uint32_t>());
  }

  UpdateStats();
}

void TaskQueuePacedSender::UpdateStats() {
  Stats new_stats;
  new_stats.expected_queue_time = pacing_controller_.ExpectedQueueTime();
  new_stats.first_sent_packet_time = pacing_controller_.FirstSentPacketTime();
  new_stats.oldest_packet_enqueue_time =
      pacing_controller_.OldestPacketEnqueueTime();
  new_stats.queue_size = pacing_controller_.QueueSizeData();
  OnStatsUpdated(new_stats);
}

TaskQueuePacedSender::Stats TaskQueuePacedSender::GetStats() const {
  MutexLock lock(&stats_mutex_);
  return current_stats_;
}

}  // namespace webrtc
