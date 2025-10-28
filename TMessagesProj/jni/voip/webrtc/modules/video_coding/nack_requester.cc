/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/nack_requester.h"

#include <algorithm>
#include <limits>

#include "api/sequence_checker.h"
#include "api/units/timestamp.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {
constexpr int kMaxPacketAge = 10'000;
constexpr int kMaxNackPackets = 1000;
constexpr TimeDelta kDefaultRtt = TimeDelta::Millis(100);
constexpr int kMaxNackRetries = 10;
constexpr int kMaxReorderedPackets = 128;
constexpr int kNumReorderingBuckets = 10;
constexpr TimeDelta kDefaultSendNackDelay = TimeDelta::Zero();

TimeDelta GetSendNackDelay(const FieldTrialsView& field_trials) {
  int64_t delay_ms = strtol(
      field_trials.Lookup("WebRTC-SendNackDelayMs").c_str(), nullptr, 10);
  if (delay_ms > 0 && delay_ms <= 20) {
    RTC_LOG(LS_INFO) << "SendNackDelay is set to " << delay_ms;
    return TimeDelta::Millis(delay_ms);
  }
  return kDefaultSendNackDelay;
}
}  // namespace

constexpr TimeDelta NackPeriodicProcessor::kUpdateInterval;

NackPeriodicProcessor::NackPeriodicProcessor(TimeDelta update_interval)
    : update_interval_(update_interval) {}

NackPeriodicProcessor::~NackPeriodicProcessor() {}

void NackPeriodicProcessor::RegisterNackModule(NackRequesterBase* module) {
  RTC_DCHECK_RUN_ON(&sequence_);
  modules_.push_back(module);
  if (modules_.size() != 1)
    return;
  repeating_task_ = RepeatingTaskHandle::DelayedStart(
      TaskQueueBase::Current(), update_interval_, [this] {
        RTC_DCHECK_RUN_ON(&sequence_);
        ProcessNackModules();
        return update_interval_;
      });
}

void NackPeriodicProcessor::UnregisterNackModule(NackRequesterBase* module) {
  RTC_DCHECK_RUN_ON(&sequence_);
  auto it = std::find(modules_.begin(), modules_.end(), module);
  RTC_DCHECK(it != modules_.end());
  modules_.erase(it);
  if (modules_.empty())
    repeating_task_.Stop();
}

void NackPeriodicProcessor::ProcessNackModules() {
  RTC_DCHECK_RUN_ON(&sequence_);
  for (NackRequesterBase* module : modules_)
    module->ProcessNacks();
}

ScopedNackPeriodicProcessorRegistration::
    ScopedNackPeriodicProcessorRegistration(NackRequesterBase* module,
                                            NackPeriodicProcessor* processor)
    : module_(module), processor_(processor) {
  processor_->RegisterNackModule(module_);
}

ScopedNackPeriodicProcessorRegistration::
    ~ScopedNackPeriodicProcessorRegistration() {
  processor_->UnregisterNackModule(module_);
}

NackRequester::NackInfo::NackInfo()
    : seq_num(0),
      send_at_seq_num(0),
      created_at_time(Timestamp::MinusInfinity()),
      sent_at_time(Timestamp::MinusInfinity()),
      retries(0) {}

NackRequester::NackInfo::NackInfo(uint16_t seq_num,
                                  uint16_t send_at_seq_num,
                                  Timestamp created_at_time)
    : seq_num(seq_num),
      send_at_seq_num(send_at_seq_num),
      created_at_time(created_at_time),
      sent_at_time(Timestamp::MinusInfinity()),
      retries(0) {}

NackRequester::NackRequester(TaskQueueBase* current_queue,
                             NackPeriodicProcessor* periodic_processor,
                             Clock* clock,
                             NackSender* nack_sender,
                             KeyFrameRequestSender* keyframe_request_sender,
                             const FieldTrialsView& field_trials)
    : worker_thread_(current_queue),
      clock_(clock),
      nack_sender_(nack_sender),
      keyframe_request_sender_(keyframe_request_sender),
      reordering_histogram_(kNumReorderingBuckets, kMaxReorderedPackets),
      initialized_(false),
      rtt_(kDefaultRtt),
      newest_seq_num_(0),
      send_nack_delay_(GetSendNackDelay(field_trials)),
      processor_registration_(this, periodic_processor) {
  RTC_DCHECK(clock_);
  RTC_DCHECK(nack_sender_);
  RTC_DCHECK(keyframe_request_sender_);
  RTC_DCHECK(worker_thread_);
  RTC_DCHECK(worker_thread_->IsCurrent());
}

NackRequester::~NackRequester() {
  RTC_DCHECK_RUN_ON(worker_thread_);
}

void NackRequester::ProcessNacks() {
  RTC_DCHECK_RUN_ON(worker_thread_);
  std::vector<uint16_t> nack_batch = GetNackBatch(kTimeOnly);
  if (!nack_batch.empty()) {
    // This batch of NACKs is triggered externally; there is no external
    // initiator who can batch them with other feedback messages.
    nack_sender_->SendNack(nack_batch, /*buffering_allowed=*/false);
  }
}

int NackRequester::OnReceivedPacket(uint16_t seq_num) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  return OnReceivedPacket(seq_num, false);
}

int NackRequester::OnReceivedPacket(uint16_t seq_num,
                                    bool is_recovered) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  // TODO(philipel): When the packet includes information whether it is
  //                 retransmitted or not, use that value instead. For
  //                 now set it to true, which will cause the reordering
  //                 statistics to never be updated.
  bool is_retransmitted = true;

  if (!initialized_) {
    newest_seq_num_ = seq_num;
    initialized_ = true;
    return 0;
  }

  // Since the `newest_seq_num_` is a packet we have actually received we know
  // that packet has never been Nacked.
  if (seq_num == newest_seq_num_)
    return 0;

  if (AheadOf(newest_seq_num_, seq_num)) {
    // An out of order packet has been received.
    auto nack_list_it = nack_list_.find(seq_num);
    int nacks_sent_for_packet = 0;
    if (nack_list_it != nack_list_.end()) {
      nacks_sent_for_packet = nack_list_it->second.retries;
      nack_list_.erase(nack_list_it);
    }
    if (!is_retransmitted)
      UpdateReorderingStatistics(seq_num);
    return nacks_sent_for_packet;
  }

  if (is_recovered) {
    recovered_list_.insert(seq_num);

    // Remove old ones so we don't accumulate recovered packets.
    auto it = recovered_list_.lower_bound(seq_num - kMaxPacketAge);
    if (it != recovered_list_.begin())
      recovered_list_.erase(recovered_list_.begin(), it);

    // Do not send nack for packets recovered by FEC or RTX.
    return 0;
  }

  AddPacketsToNack(newest_seq_num_ + 1, seq_num);
  newest_seq_num_ = seq_num;

  // Are there any nacks that are waiting for this seq_num.
  std::vector<uint16_t> nack_batch = GetNackBatch(kSeqNumOnly);
  if (!nack_batch.empty()) {
    // This batch of NACKs is triggered externally; the initiator can
    // batch them with other feedback messages.
    nack_sender_->SendNack(nack_batch, /*buffering_allowed=*/true);
  }

  return 0;
}

void NackRequester::ClearUpTo(uint16_t seq_num) {
  // TODO(bugs.webrtc.org/11993): This method is actually called on the worker
  // thread even though the caller stack to this call passes thread checkers
  // indicating they belong to the network thread. The inline execution below
  // needs to be posted to the worker thread if callers migrate to the network
  // thread.
  RTC_DCHECK_RUN_ON(worker_thread_);
  nack_list_.erase(nack_list_.begin(), nack_list_.lower_bound(seq_num));
  recovered_list_.erase(recovered_list_.begin(),
                        recovered_list_.lower_bound(seq_num));
}

void NackRequester::UpdateRtt(int64_t rtt_ms) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  rtt_ = TimeDelta::Millis(rtt_ms);
}

void NackRequester::AddPacketsToNack(uint16_t seq_num_start,
                                     uint16_t seq_num_end) {
  // Called on worker_thread_.
  // Remove old packets.
  auto it = nack_list_.lower_bound(seq_num_end - kMaxPacketAge);
  nack_list_.erase(nack_list_.begin(), it);

  uint16_t num_new_nacks = ForwardDiff(seq_num_start, seq_num_end);
  if (nack_list_.size() + num_new_nacks > kMaxNackPackets) {
    nack_list_.clear();
    RTC_LOG(LS_WARNING) << "NACK list full, clearing NACK"
                           " list and requesting keyframe.";
    keyframe_request_sender_->RequestKeyFrame();
    return;
  }

  for (uint16_t seq_num = seq_num_start; seq_num != seq_num_end; ++seq_num) {
    // Do not send nack for packets that are already recovered by FEC or RTX
    if (recovered_list_.find(seq_num) != recovered_list_.end())
      continue;
    NackInfo nack_info(seq_num, seq_num + WaitNumberOfPackets(0.5),
                       clock_->CurrentTime());
    RTC_DCHECK(nack_list_.find(seq_num) == nack_list_.end());
    nack_list_[seq_num] = nack_info;
  }
}

std::vector<uint16_t> NackRequester::GetNackBatch(NackFilterOptions options) {
  // Called on worker_thread_.

  bool consider_seq_num = options != kTimeOnly;
  bool consider_timestamp = options != kSeqNumOnly;
  Timestamp now = clock_->CurrentTime();
  std::vector<uint16_t> nack_batch;
  auto it = nack_list_.begin();
  while (it != nack_list_.end()) {
    bool delay_timed_out = now - it->second.created_at_time >= send_nack_delay_;
    bool nack_on_rtt_passed = now - it->second.sent_at_time >= rtt_;
    bool nack_on_seq_num_passed =
        it->second.sent_at_time.IsInfinite() &&
        AheadOrAt(newest_seq_num_, it->second.send_at_seq_num);
    if (delay_timed_out && ((consider_seq_num && nack_on_seq_num_passed) ||
                            (consider_timestamp && nack_on_rtt_passed))) {
      nack_batch.emplace_back(it->second.seq_num);
      ++it->second.retries;
      it->second.sent_at_time = now;
      if (it->second.retries >= kMaxNackRetries) {
        RTC_LOG(LS_WARNING) << "Sequence number " << it->second.seq_num
                            << " removed from NACK list due to max retries.";
        it = nack_list_.erase(it);
      } else {
        ++it;
      }
      continue;
    }
    ++it;
  }
  return nack_batch;
}

void NackRequester::UpdateReorderingStatistics(uint16_t seq_num) {
  // Running on worker_thread_.
  RTC_DCHECK(AheadOf(newest_seq_num_, seq_num));
  uint16_t diff = ReverseDiff(newest_seq_num_, seq_num);
  reordering_histogram_.Add(diff);
}

int NackRequester::WaitNumberOfPackets(float probability) const {
  // Called on worker_thread_;
  if (reordering_histogram_.NumValues() == 0)
    return 0;
  return reordering_histogram_.InverseCdf(probability);
}

}  // namespace webrtc
