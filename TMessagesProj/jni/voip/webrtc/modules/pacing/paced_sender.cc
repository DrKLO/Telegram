/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/pacing/paced_sender.h"

#include <algorithm>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/strings/match.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "modules/utility/include/process_thread.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
const int64_t PacedSender::kMaxQueueLengthMs = 2000;
const float PacedSender::kDefaultPaceMultiplier = 2.5f;

PacedSender::PacedSender(Clock* clock,
                         PacketRouter* packet_router,
                         RtcEventLog* event_log,
                         const WebRtcKeyValueConfig* field_trials,
                         ProcessThread* process_thread)
    : process_mode_(
          (field_trials != nullptr &&
           absl::StartsWith(field_trials->Lookup("WebRTC-Pacer-DynamicProcess"),
                            "Enabled"))
              ? PacingController::ProcessMode::kDynamic
              : PacingController::ProcessMode::kPeriodic),
      pacing_controller_(clock,
                         packet_router,
                         event_log,
                         field_trials,
                         process_mode_),
      clock_(clock),
      process_thread_(process_thread) {
  if (process_thread_)
    process_thread_->RegisterModule(&module_proxy_, RTC_FROM_HERE);
}

PacedSender::~PacedSender() {
  if (process_thread_) {
    process_thread_->DeRegisterModule(&module_proxy_);
  }
}

void PacedSender::CreateProbeCluster(DataRate bitrate, int cluster_id) {
  MutexLock lock(&mutex_);
  return pacing_controller_.CreateProbeCluster(bitrate, cluster_id);
}

void PacedSender::Pause() {
  {
    MutexLock lock(&mutex_);
    pacing_controller_.Pause();
  }

  // Tell the process thread to call our TimeUntilNextProcess() method to get
  // a new (longer) estimate for when to call Process().
  if (process_thread_) {
    process_thread_->WakeUp(&module_proxy_);
  }
}

void PacedSender::Resume() {
  {
    MutexLock lock(&mutex_);
    pacing_controller_.Resume();
  }

  // Tell the process thread to call our TimeUntilNextProcess() method to
  // refresh the estimate for when to call Process().
  if (process_thread_) {
    process_thread_->WakeUp(&module_proxy_);
  }
}

void PacedSender::SetCongestionWindow(DataSize congestion_window_size) {
  {
    MutexLock lock(&mutex_);
    pacing_controller_.SetCongestionWindow(congestion_window_size);
  }
  MaybeWakupProcessThread();
}

void PacedSender::UpdateOutstandingData(DataSize outstanding_data) {
  {
    MutexLock lock(&mutex_);
    pacing_controller_.UpdateOutstandingData(outstanding_data);
  }
  MaybeWakupProcessThread();
}

void PacedSender::SetPacingRates(DataRate pacing_rate, DataRate padding_rate) {
  {
    MutexLock lock(&mutex_);
    pacing_controller_.SetPacingRates(pacing_rate, padding_rate);
  }
  MaybeWakupProcessThread();
}

void PacedSender::EnqueuePackets(
    std::vector<std::unique_ptr<RtpPacketToSend>> packets) {
  {
    TRACE_EVENT0(TRACE_DISABLED_BY_DEFAULT("webrtc"),
                 "PacedSender::EnqueuePackets");
    MutexLock lock(&mutex_);
    for (auto& packet : packets) {
      TRACE_EVENT2(TRACE_DISABLED_BY_DEFAULT("webrtc"),
                   "PacedSender::EnqueuePackets::Loop", "sequence_number",
                   packet->SequenceNumber(), "rtp_timestamp",
                   packet->Timestamp());

      RTC_DCHECK_GE(packet->capture_time_ms(), 0);
      pacing_controller_.EnqueuePacket(std::move(packet));
    }
  }
  MaybeWakupProcessThread();
}

void PacedSender::SetAccountForAudioPackets(bool account_for_audio) {
  MutexLock lock(&mutex_);
  pacing_controller_.SetAccountForAudioPackets(account_for_audio);
}

void PacedSender::SetIncludeOverhead() {
  MutexLock lock(&mutex_);
  pacing_controller_.SetIncludeOverhead();
}

void PacedSender::SetTransportOverhead(DataSize overhead_per_packet) {
  MutexLock lock(&mutex_);
  pacing_controller_.SetTransportOverhead(overhead_per_packet);
}

TimeDelta PacedSender::ExpectedQueueTime() const {
  MutexLock lock(&mutex_);
  return pacing_controller_.ExpectedQueueTime();
}

DataSize PacedSender::QueueSizeData() const {
  MutexLock lock(&mutex_);
  return pacing_controller_.QueueSizeData();
}

absl::optional<Timestamp> PacedSender::FirstSentPacketTime() const {
  MutexLock lock(&mutex_);
  return pacing_controller_.FirstSentPacketTime();
}

TimeDelta PacedSender::OldestPacketWaitTime() const {
  MutexLock lock(&mutex_);
  Timestamp oldest_packet = pacing_controller_.OldestPacketEnqueueTime();
  if (oldest_packet.IsInfinite())
    return TimeDelta::Zero();

  // (webrtc:9716): The clock is not always monotonic.
  Timestamp current = clock_->CurrentTime();
  if (current < oldest_packet)
    return TimeDelta::Zero();
  return current - oldest_packet;
}

int64_t PacedSender::TimeUntilNextProcess() {
  MutexLock lock(&mutex_);

  Timestamp next_send_time = pacing_controller_.NextSendTime();
  TimeDelta sleep_time =
      std::max(TimeDelta::Zero(), next_send_time - clock_->CurrentTime());
  if (process_mode_ == PacingController::ProcessMode::kDynamic) {
    return std::max(sleep_time, PacingController::kMinSleepTime).ms();
  }
  return sleep_time.ms();
}

void PacedSender::Process() {
  MutexLock lock(&mutex_);
  pacing_controller_.ProcessPackets();
}

void PacedSender::ProcessThreadAttached(ProcessThread* process_thread) {
  RTC_LOG(LS_INFO) << "ProcessThreadAttached 0x" << process_thread;
  RTC_DCHECK(!process_thread || process_thread == process_thread_);
}

void PacedSender::MaybeWakupProcessThread() {
  // Tell the process thread to call our TimeUntilNextProcess() method to get
  // a new time for when to call Process().
  if (process_thread_ &&
      process_mode_ == PacingController::ProcessMode::kDynamic) {
    process_thread_->WakeUp(&module_proxy_);
  }
}

void PacedSender::SetQueueTimeLimit(TimeDelta limit) {
  {
    MutexLock lock(&mutex_);
    pacing_controller_.SetQueueTimeLimit(limit);
  }
  MaybeWakupProcessThread();
}

}  // namespace webrtc
