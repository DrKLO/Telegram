/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_packet_history.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <utility>

#include "modules/include/module_common_types_public.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

RtpPacketHistory::StoredPacket::StoredPacket(
    std::unique_ptr<RtpPacketToSend> packet,
    Timestamp send_time,
    uint64_t insert_order)
    : packet_(std::move(packet)),
      pending_transmission_(false),
      send_time_(send_time),
      insert_order_(insert_order),
      times_retransmitted_(0) {}

RtpPacketHistory::StoredPacket::StoredPacket(StoredPacket&&) = default;
RtpPacketHistory::StoredPacket& RtpPacketHistory::StoredPacket::operator=(
    RtpPacketHistory::StoredPacket&&) = default;
RtpPacketHistory::StoredPacket::~StoredPacket() = default;

void RtpPacketHistory::StoredPacket::IncrementTimesRetransmitted(
    PacketPrioritySet* priority_set) {
  // Check if this StoredPacket is in the priority set. If so, we need to remove
  // it before updating `times_retransmitted_` since that is used in sorting,
  // and then add it back.
  const bool in_priority_set = priority_set && priority_set->erase(this) > 0;
  ++times_retransmitted_;
  if (in_priority_set) {
    auto it = priority_set->insert(this);
    RTC_DCHECK(it.second)
        << "ERROR: Priority set already contains matching packet! In set: "
           "insert order = "
        << (*it.first)->insert_order_
        << ", times retransmitted = " << (*it.first)->times_retransmitted_
        << ". Trying to add: insert order = " << insert_order_
        << ", times retransmitted = " << times_retransmitted_;
  }
}

bool RtpPacketHistory::MoreUseful::operator()(StoredPacket* lhs,
                                              StoredPacket* rhs) const {
  // Prefer to send packets we haven't already sent as padding.
  if (lhs->times_retransmitted() != rhs->times_retransmitted()) {
    return lhs->times_retransmitted() < rhs->times_retransmitted();
  }
  // All else being equal, prefer newer packets.
  return lhs->insert_order() > rhs->insert_order();
}

RtpPacketHistory::RtpPacketHistory(Clock* clock, bool enable_padding_prio)
    : clock_(clock),
      enable_padding_prio_(enable_padding_prio),
      number_to_store_(0),
      mode_(StorageMode::kDisabled),
      rtt_(TimeDelta::MinusInfinity()),
      packets_inserted_(0) {}

RtpPacketHistory::~RtpPacketHistory() {}

void RtpPacketHistory::SetStorePacketsStatus(StorageMode mode,
                                             size_t number_to_store) {
  RTC_DCHECK_LE(number_to_store, kMaxCapacity);
  MutexLock lock(&lock_);
  if (mode != StorageMode::kDisabled && mode_ != StorageMode::kDisabled) {
    RTC_LOG(LS_WARNING) << "Purging packet history in order to re-set status.";
  }
  Reset();
  mode_ = mode;
  number_to_store_ = std::min(kMaxCapacity, number_to_store);
}

RtpPacketHistory::StorageMode RtpPacketHistory::GetStorageMode() const {
  MutexLock lock(&lock_);
  return mode_;
}

void RtpPacketHistory::SetRtt(TimeDelta rtt) {
  MutexLock lock(&lock_);
  RTC_DCHECK_GE(rtt, TimeDelta::Zero());
  rtt_ = rtt;
  // If storage is not disabled,  packets will be removed after a timeout
  // that depends on the RTT. Changing the RTT may thus cause some packets
  // become "old" and subject to removal.
  if (mode_ != StorageMode::kDisabled) {
    CullOldPackets();
  }
}

void RtpPacketHistory::PutRtpPacket(std::unique_ptr<RtpPacketToSend> packet,
                                    Timestamp send_time) {
  RTC_DCHECK(packet);
  MutexLock lock(&lock_);
  if (mode_ == StorageMode::kDisabled) {
    return;
  }

  RTC_DCHECK(packet->allow_retransmission());
  CullOldPackets();

  // Store packet.
  const uint16_t rtp_seq_no = packet->SequenceNumber();
  int packet_index = GetPacketIndex(rtp_seq_no);
  if (packet_index >= 0 &&
      static_cast<size_t>(packet_index) < packet_history_.size() &&
      packet_history_[packet_index].packet_ != nullptr) {
    RTC_LOG(LS_WARNING) << "Duplicate packet inserted: " << rtp_seq_no;
    // Remove previous packet to avoid inconsistent state.
    RemovePacket(packet_index);
    packet_index = GetPacketIndex(rtp_seq_no);
  }

  // Packet to be inserted ahead of first packet, expand front.
  for (; packet_index < 0; ++packet_index) {
    packet_history_.emplace_front();
  }
  // Packet to be inserted behind last packet, expand back.
  while (static_cast<int>(packet_history_.size()) <= packet_index) {
    packet_history_.emplace_back();
  }

  RTC_DCHECK_GE(packet_index, 0);
  RTC_DCHECK_LT(packet_index, packet_history_.size());
  RTC_DCHECK(packet_history_[packet_index].packet_ == nullptr);

  packet_history_[packet_index] =
      StoredPacket(std::move(packet), send_time, packets_inserted_++);

  if (enable_padding_prio_) {
    if (padding_priority_.size() >= kMaxPaddingHistory - 1) {
      padding_priority_.erase(std::prev(padding_priority_.end()));
    }
    auto prio_it = padding_priority_.insert(&packet_history_[packet_index]);
    RTC_DCHECK(prio_it.second) << "Failed to insert packet into prio set.";
  }
}

std::unique_ptr<RtpPacketToSend> RtpPacketHistory::GetPacketAndMarkAsPending(
    uint16_t sequence_number) {
  return GetPacketAndMarkAsPending(
      sequence_number, [](const RtpPacketToSend& packet) {
        return std::make_unique<RtpPacketToSend>(packet);
      });
}

std::unique_ptr<RtpPacketToSend> RtpPacketHistory::GetPacketAndMarkAsPending(
    uint16_t sequence_number,
    rtc::FunctionView<std::unique_ptr<RtpPacketToSend>(const RtpPacketToSend&)>
        encapsulate) {
  MutexLock lock(&lock_);
  if (mode_ == StorageMode::kDisabled) {
    return nullptr;
  }

  StoredPacket* packet = GetStoredPacket(sequence_number);
  if (packet == nullptr) {
    return nullptr;
  }

  if (packet->pending_transmission_) {
    // Packet already in pacer queue, ignore this request.
    return nullptr;
  }

  if (!VerifyRtt(*packet)) {
    // Packet already resent within too short a time window, ignore.
    return nullptr;
  }

  // Copy and/or encapsulate packet.
  std::unique_ptr<RtpPacketToSend> encapsulated_packet =
      encapsulate(*packet->packet_);
  if (encapsulated_packet) {
    packet->pending_transmission_ = true;
  }

  return encapsulated_packet;
}

void RtpPacketHistory::MarkPacketAsSent(uint16_t sequence_number) {
  MutexLock lock(&lock_);
  if (mode_ == StorageMode::kDisabled) {
    return;
  }

  StoredPacket* packet = GetStoredPacket(sequence_number);
  if (packet == nullptr) {
    return;
  }

  // Update send-time, mark as no longer in pacer queue, and increment
  // transmission count.
  packet->set_send_time(clock_->CurrentTime());
  packet->pending_transmission_ = false;
  packet->IncrementTimesRetransmitted(enable_padding_prio_ ? &padding_priority_
                                                           : nullptr);
}

bool RtpPacketHistory::GetPacketState(uint16_t sequence_number) const {
  MutexLock lock(&lock_);
  if (mode_ == StorageMode::kDisabled) {
    return false;
  }

  int packet_index = GetPacketIndex(sequence_number);
  if (packet_index < 0 ||
      static_cast<size_t>(packet_index) >= packet_history_.size()) {
    return false;
  }
  const StoredPacket& packet = packet_history_[packet_index];
  if (packet.packet_ == nullptr) {
    return false;
  }

  if (!VerifyRtt(packet)) {
    return false;
  }

  return true;
}

bool RtpPacketHistory::VerifyRtt(
    const RtpPacketHistory::StoredPacket& packet) const {
  if (packet.times_retransmitted() > 0 &&
      clock_->CurrentTime() - packet.send_time() < rtt_) {
    // This packet has already been retransmitted once, and the time since
    // that even is lower than on RTT. Ignore request as this packet is
    // likely already in the network pipe.
    return false;
  }

  return true;
}

std::unique_ptr<RtpPacketToSend> RtpPacketHistory::GetPayloadPaddingPacket() {
  // Default implementation always just returns a copy of the packet.
  return GetPayloadPaddingPacket([](const RtpPacketToSend& packet) {
    return std::make_unique<RtpPacketToSend>(packet);
  });
}

std::unique_ptr<RtpPacketToSend> RtpPacketHistory::GetPayloadPaddingPacket(
    rtc::FunctionView<std::unique_ptr<RtpPacketToSend>(const RtpPacketToSend&)>
        encapsulate) {
  MutexLock lock(&lock_);
  if (mode_ == StorageMode::kDisabled) {
    return nullptr;
  }

  StoredPacket* best_packet = nullptr;
  if (enable_padding_prio_ && !padding_priority_.empty()) {
    auto best_packet_it = padding_priority_.begin();
    best_packet = *best_packet_it;
  } else if (!enable_padding_prio_ && !packet_history_.empty()) {
    // Prioritization not available, pick the last packet.
    for (auto it = packet_history_.rbegin(); it != packet_history_.rend();
         ++it) {
      if (it->packet_ != nullptr) {
        best_packet = &(*it);
        break;
      }
    }
  }
  if (best_packet == nullptr) {
    return nullptr;
  }

  if (best_packet->pending_transmission_) {
    // Because PacedSender releases it's lock when it calls
    // GeneratePadding() there is the potential for a race where a new
    // packet ends up here instead of the regular transmit path. In such a
    // case, just return empty and it will be picked up on the next
    // Process() call.
    return nullptr;
  }

  auto padding_packet = encapsulate(*best_packet->packet_);
  if (!padding_packet) {
    return nullptr;
  }

  best_packet->set_send_time(clock_->CurrentTime());
  best_packet->IncrementTimesRetransmitted(
      enable_padding_prio_ ? &padding_priority_ : nullptr);

  return padding_packet;
}

void RtpPacketHistory::CullAcknowledgedPackets(
    rtc::ArrayView<const uint16_t> sequence_numbers) {
  MutexLock lock(&lock_);
  for (uint16_t sequence_number : sequence_numbers) {
    int packet_index = GetPacketIndex(sequence_number);
    if (packet_index < 0 ||
        static_cast<size_t>(packet_index) >= packet_history_.size()) {
      continue;
    }
    RemovePacket(packet_index);
  }
}

void RtpPacketHistory::Clear() {
  MutexLock lock(&lock_);
  Reset();
}

void RtpPacketHistory::Reset() {
  packet_history_.clear();
  padding_priority_.clear();
}

void RtpPacketHistory::CullOldPackets() {
  Timestamp now = clock_->CurrentTime();
  TimeDelta packet_duration =
      rtt_.IsFinite()
          ? std::max(kMinPacketDurationRtt * rtt_, kMinPacketDuration)
          : kMinPacketDuration;
  while (!packet_history_.empty()) {
    if (packet_history_.size() >= kMaxCapacity) {
      // We have reached the absolute max capacity, remove one packet
      // unconditionally.
      RemovePacket(0);
      continue;
    }

    const StoredPacket& stored_packet = packet_history_.front();
    if (stored_packet.pending_transmission_) {
      // Don't remove packets in the pacer queue, pending tranmission.
      return;
    }

    if (stored_packet.send_time() + packet_duration > now) {
      // Don't cull packets too early to avoid failed retransmission requests.
      return;
    }

    if (packet_history_.size() >= number_to_store_ ||
        stored_packet.send_time() +
                (packet_duration * kPacketCullingDelayFactor) <=
            now) {
      // Too many packets in history, or this packet has timed out. Remove it
      // and continue.
      RemovePacket(0);
    } else {
      // No more packets can be removed right now.
      return;
    }
  }
}

std::unique_ptr<RtpPacketToSend> RtpPacketHistory::RemovePacket(
    int packet_index) {
  // Move the packet out from the StoredPacket container.
  std::unique_ptr<RtpPacketToSend> rtp_packet =
      std::move(packet_history_[packet_index].packet_);

  // Erase from padding priority set, if eligible.
  if (enable_padding_prio_) {
    padding_priority_.erase(&packet_history_[packet_index]);
  }

  if (packet_index == 0) {
    while (!packet_history_.empty() &&
           packet_history_.front().packet_ == nullptr) {
      packet_history_.pop_front();
    }
  }

  return rtp_packet;
}

int RtpPacketHistory::GetPacketIndex(uint16_t sequence_number) const {
  if (packet_history_.empty()) {
    return 0;
  }

  RTC_DCHECK(packet_history_.front().packet_ != nullptr);
  int first_seq = packet_history_.front().packet_->SequenceNumber();
  if (first_seq == sequence_number) {
    return 0;
  }

  int packet_index = sequence_number - first_seq;
  constexpr int kSeqNumSpan = std::numeric_limits<uint16_t>::max() + 1;

  if (IsNewerSequenceNumber(sequence_number, first_seq)) {
    if (sequence_number < first_seq) {
      // Forward wrap.
      packet_index += kSeqNumSpan;
    }
  } else if (sequence_number > first_seq) {
    // Backwards wrap.
    packet_index -= kSeqNumSpan;
  }

  return packet_index;
}

RtpPacketHistory::StoredPacket* RtpPacketHistory::GetStoredPacket(
    uint16_t sequence_number) {
  int index = GetPacketIndex(sequence_number);
  if (index < 0 || static_cast<size_t>(index) >= packet_history_.size() ||
      packet_history_[index].packet_ == nullptr) {
    return nullptr;
  }
  return &packet_history_[index];
}

}  // namespace webrtc
