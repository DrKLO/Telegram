/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/pacing/prioritized_packet_queue.h"

#include <utility>

#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr int kAudioPrioLevel = 0;

int GetPriorityForType(RtpPacketMediaType type) {
  // Lower number takes priority over higher.
  switch (type) {
    case RtpPacketMediaType::kAudio:
      // Audio is always prioritized over other packet types.
      return kAudioPrioLevel;
    case RtpPacketMediaType::kRetransmission:
      // Send retransmissions before new media.
      return kAudioPrioLevel + 1;
    case RtpPacketMediaType::kVideo:
    case RtpPacketMediaType::kForwardErrorCorrection:
      // Video has "normal" priority, in the old speak.
      // Send redundancy concurrently to video. If it is delayed it might have a
      // lower chance of being useful.
      return kAudioPrioLevel + 2;
    case RtpPacketMediaType::kPadding:
      // Packets that are in themselves likely useless, only sent to keep the
      // BWE high.
      return kAudioPrioLevel + 3;
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace

DataSize PrioritizedPacketQueue::QueuedPacket::PacketSize() const {
  return DataSize::Bytes(packet->payload_size() + packet->padding_size());
}

PrioritizedPacketQueue::StreamQueue::StreamQueue(Timestamp creation_time)
    : last_enqueue_time_(creation_time) {}

bool PrioritizedPacketQueue::StreamQueue::EnqueuePacket(QueuedPacket packet,
                                                        int priority_level) {
  bool first_packet_at_level = packets_[priority_level].empty();
  packets_[priority_level].push_back(std::move(packet));
  return first_packet_at_level;
}

PrioritizedPacketQueue::QueuedPacket
PrioritizedPacketQueue::StreamQueue::DequePacket(int priority_level) {
  RTC_DCHECK(!packets_[priority_level].empty());
  QueuedPacket packet = std::move(packets_[priority_level].front());
  packets_[priority_level].pop_front();
  return packet;
}

bool PrioritizedPacketQueue::StreamQueue::HasPacketsAtPrio(
    int priority_level) const {
  return !packets_[priority_level].empty();
}

bool PrioritizedPacketQueue::StreamQueue::IsEmpty() const {
  for (const std::deque<QueuedPacket>& queue : packets_) {
    if (!queue.empty()) {
      return false;
    }
  }
  return true;
}

Timestamp PrioritizedPacketQueue::StreamQueue::LeadingPacketEnqueueTime(
    int priority_level) const {
  RTC_DCHECK(!packets_[priority_level].empty());
  return packets_[priority_level].begin()->enqueue_time;
}

Timestamp PrioritizedPacketQueue::StreamQueue::LastEnqueueTime() const {
  return last_enqueue_time_;
}

PrioritizedPacketQueue::PrioritizedPacketQueue(Timestamp creation_time)
    : queue_time_sum_(TimeDelta::Zero()),
      pause_time_sum_(TimeDelta::Zero()),
      size_packets_(0),
      size_packets_per_media_type_({}),
      size_payload_(DataSize::Zero()),
      last_update_time_(creation_time),
      paused_(false),
      last_culling_time_(creation_time),
      top_active_prio_level_(-1) {}

void PrioritizedPacketQueue::Push(Timestamp enqueue_time,
                                  std::unique_ptr<RtpPacketToSend> packet) {
  StreamQueue* stream_queue;
  auto [it, inserted] = streams_.emplace(packet->Ssrc(), nullptr);
  if (inserted) {
    it->second = std::make_unique<StreamQueue>(enqueue_time);
  }
  stream_queue = it->second.get();

  auto enqueue_time_iterator =
      enqueue_times_.insert(enqueue_times_.end(), enqueue_time);
  RTC_DCHECK(packet->packet_type().has_value());
  RtpPacketMediaType packet_type = packet->packet_type().value();
  int prio_level = GetPriorityForType(packet_type);
  RTC_DCHECK_GE(prio_level, 0);
  RTC_DCHECK_LT(prio_level, kNumPriorityLevels);
  QueuedPacket queued_packed = {.packet = std::move(packet),
                                .enqueue_time = enqueue_time,
                                .enqueue_time_iterator = enqueue_time_iterator};
  // In order to figure out how much time a packet has spent in the queue
  // while not in a paused state, we subtract the total amount of time the
  // queue has been paused so far, and when the packet is popped we subtract
  // the total amount of time the queue has been paused at that moment. This
  // way we subtract the total amount of time the packet has spent in the
  // queue while in a paused state.
  UpdateAverageQueueTime(enqueue_time);
  queued_packed.enqueue_time -= pause_time_sum_;
  ++size_packets_;
  ++size_packets_per_media_type_[static_cast<size_t>(packet_type)];
  size_payload_ += queued_packed.PacketSize();

  if (stream_queue->EnqueuePacket(std::move(queued_packed), prio_level)) {
    // Number packets at `prio_level` for this steam is now non-zero.
    streams_by_prio_[prio_level].push_back(stream_queue);
  }
  if (top_active_prio_level_ < 0 || prio_level < top_active_prio_level_) {
    top_active_prio_level_ = prio_level;
  }

  static constexpr TimeDelta kTimeout = TimeDelta::Millis(500);
  if (enqueue_time - last_culling_time_ > kTimeout) {
    for (auto it = streams_.begin(); it != streams_.end();) {
      if (it->second->IsEmpty() &&
          it->second->LastEnqueueTime() + kTimeout < enqueue_time) {
        streams_.erase(it++);
      } else {
        ++it;
      }
    }
    last_culling_time_ = enqueue_time;
  }
}

std::unique_ptr<RtpPacketToSend> PrioritizedPacketQueue::Pop() {
  if (size_packets_ == 0) {
    return nullptr;
  }

  RTC_DCHECK_GE(top_active_prio_level_, 0);
  StreamQueue& stream_queue = *streams_by_prio_[top_active_prio_level_].front();
  QueuedPacket packet = stream_queue.DequePacket(top_active_prio_level_);
  --size_packets_;
  RTC_DCHECK(packet.packet->packet_type().has_value());
  RtpPacketMediaType packet_type = packet.packet->packet_type().value();
  --size_packets_per_media_type_[static_cast<size_t>(packet_type)];
  RTC_DCHECK_GE(size_packets_per_media_type_[static_cast<size_t>(packet_type)],
                0);
  size_payload_ -= packet.PacketSize();

  // Calculate the total amount of time spent by this packet in the queue
  // while in a non-paused state. Note that the `pause_time_sum_ms_` was
  // subtracted from `packet.enqueue_time_ms` when the packet was pushed, and
  // by subtracting it now we effectively remove the time spent in in the
  // queue while in a paused state.
  TimeDelta time_in_non_paused_state =
      last_update_time_ - packet.enqueue_time - pause_time_sum_;
  queue_time_sum_ -= time_in_non_paused_state;

  // Set the time spent in the send queue, which is the per-packet equivalent of
  // totalPacketSendDelay. The notion of being paused is an implementation
  // detail that we do not want to expose, so it makes sense to report the
  // metric excluding the pause time. This also avoids spikes in the metric.
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-totalpacketsenddelay
  packet.packet->set_time_in_send_queue(time_in_non_paused_state);

  RTC_DCHECK(size_packets_ > 0 || queue_time_sum_ == TimeDelta::Zero());

  RTC_CHECK(packet.enqueue_time_iterator != enqueue_times_.end());
  enqueue_times_.erase(packet.enqueue_time_iterator);

  // Remove StreamQueue from head of fifo-queue for this prio level, and
  // and add it to the end if it still has packets.
  streams_by_prio_[top_active_prio_level_].pop_front();
  if (stream_queue.HasPacketsAtPrio(top_active_prio_level_)) {
    streams_by_prio_[top_active_prio_level_].push_back(&stream_queue);
  } else if (streams_by_prio_[top_active_prio_level_].empty()) {
    // No stream queues have packets at this prio level, find top priority
    // that is not empty.
    if (size_packets_ == 0) {
      top_active_prio_level_ = -1;
    } else {
      for (int i = 0; i < kNumPriorityLevels; ++i) {
        if (!streams_by_prio_[i].empty()) {
          top_active_prio_level_ = i;
          break;
        }
      }
    }
  }

  return std::move(packet.packet);
}

int PrioritizedPacketQueue::SizeInPackets() const {
  return size_packets_;
}

DataSize PrioritizedPacketQueue::SizeInPayloadBytes() const {
  return size_payload_;
}

bool PrioritizedPacketQueue::Empty() const {
  return size_packets_ == 0;
}

const std::array<int, kNumMediaTypes>&
PrioritizedPacketQueue::SizeInPacketsPerRtpPacketMediaType() const {
  return size_packets_per_media_type_;
}

Timestamp PrioritizedPacketQueue::LeadingPacketEnqueueTime(
    RtpPacketMediaType type) const {
  const int priority_level = GetPriorityForType(type);
  if (streams_by_prio_[priority_level].empty()) {
    return Timestamp::MinusInfinity();
  }
  return streams_by_prio_[priority_level].front()->LeadingPacketEnqueueTime(
      priority_level);
}

Timestamp PrioritizedPacketQueue::OldestEnqueueTime() const {
  return enqueue_times_.empty() ? Timestamp::MinusInfinity()
                                : enqueue_times_.front();
}

TimeDelta PrioritizedPacketQueue::AverageQueueTime() const {
  if (size_packets_ == 0) {
    return TimeDelta::Zero();
  }
  return queue_time_sum_ / size_packets_;
}

void PrioritizedPacketQueue::UpdateAverageQueueTime(Timestamp now) {
  RTC_CHECK_GE(now, last_update_time_);
  if (now == last_update_time_) {
    return;
  }

  TimeDelta delta = now - last_update_time_;

  if (paused_) {
    pause_time_sum_ += delta;
  } else {
    queue_time_sum_ += delta * size_packets_;
  }

  last_update_time_ = now;
}

void PrioritizedPacketQueue::SetPauseState(bool paused, Timestamp now) {
  UpdateAverageQueueTime(now);
  paused_ = paused;
}

}  // namespace webrtc
