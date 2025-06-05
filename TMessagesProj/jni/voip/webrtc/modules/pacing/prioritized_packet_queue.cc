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

#include <algorithm>
#include <array>
#include <utility>

#include "absl/container/inlined_vector.h"
#include "absl/types/optional.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr int kAudioPrioLevel = 0;

int GetPriorityForType(
    RtpPacketMediaType type,
    absl::optional<RtpPacketToSend::OriginalType> original_type) {
  // Lower number takes priority over higher.
  switch (type) {
    case RtpPacketMediaType::kAudio:
      // Audio is always prioritized over other packet types.
      return kAudioPrioLevel;
    case RtpPacketMediaType::kRetransmission:
      // Send retransmissions before new media. If original_type is set, audio
      // retransmission is prioritized more than video retransmission.
      if (original_type == RtpPacketToSend::OriginalType::kVideo) {
        return kAudioPrioLevel + 2;
      }
      return kAudioPrioLevel + 1;
    case RtpPacketMediaType::kVideo:
    case RtpPacketMediaType::kForwardErrorCorrection:
      // Video has "normal" priority, in the old speak.
      // Send redundancy concurrently to video. If it is delayed it might have a
      // lower chance of being useful.
      return kAudioPrioLevel + 3;
    case RtpPacketMediaType::kPadding:
      // Packets that are in themselves likely useless, only sent to keep the
      // BWE high.
      return kAudioPrioLevel + 4;
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace

absl::InlinedVector<TimeDelta, PrioritizedPacketQueue::kNumPriorityLevels>
PrioritizedPacketQueue::ToTtlPerPrio(PacketQueueTTL packet_queue_ttl) {
  absl::InlinedVector<TimeDelta, PrioritizedPacketQueue::kNumPriorityLevels>
      ttl_per_prio(kNumPriorityLevels, TimeDelta::PlusInfinity());
  ttl_per_prio[GetPriorityForType(RtpPacketMediaType::kRetransmission,
                                  RtpPacketToSend::OriginalType::kAudio)] =
      packet_queue_ttl.audio_retransmission;
  ttl_per_prio[GetPriorityForType(RtpPacketMediaType::kRetransmission,
                                  RtpPacketToSend::OriginalType::kVideo)] =
      packet_queue_ttl.video_retransmission;
  ttl_per_prio[GetPriorityForType(RtpPacketMediaType::kVideo, absl::nullopt)] =
      packet_queue_ttl.video;
  return ttl_per_prio;
}

DataSize PrioritizedPacketQueue::QueuedPacket::PacketSize() const {
  return DataSize::Bytes(packet->payload_size() + packet->padding_size());
}

PrioritizedPacketQueue::StreamQueue::StreamQueue(Timestamp creation_time)
    : last_enqueue_time_(creation_time), num_keyframe_packets_(0) {}

bool PrioritizedPacketQueue::StreamQueue::EnqueuePacket(QueuedPacket packet,
                                                        int priority_level) {
  if (packet.packet->is_key_frame()) {
    ++num_keyframe_packets_;
  }
  bool first_packet_at_level = packets_[priority_level].empty();
  packets_[priority_level].push_back(std::move(packet));
  return first_packet_at_level;
}

PrioritizedPacketQueue::QueuedPacket
PrioritizedPacketQueue::StreamQueue::DequeuePacket(int priority_level) {
  RTC_DCHECK(!packets_[priority_level].empty());
  QueuedPacket packet = std::move(packets_[priority_level].front());
  packets_[priority_level].pop_front();
  if (packet.packet->is_key_frame()) {
    RTC_DCHECK_GT(num_keyframe_packets_, 0);
    --num_keyframe_packets_;
  }
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

std::array<std::deque<PrioritizedPacketQueue::QueuedPacket>,
           PrioritizedPacketQueue::kNumPriorityLevels>
PrioritizedPacketQueue::StreamQueue::DequeueAll() {
  std::array<std::deque<QueuedPacket>, kNumPriorityLevels> packets_by_prio;
  for (int i = 0; i < kNumPriorityLevels; ++i) {
    packets_by_prio[i].swap(packets_[i]);
  }
  num_keyframe_packets_ = 0;
  return packets_by_prio;
}

PrioritizedPacketQueue::PrioritizedPacketQueue(
    Timestamp creation_time,
    bool prioritize_audio_retransmission,
    PacketQueueTTL packet_queue_ttl)
    : prioritize_audio_retransmission_(prioritize_audio_retransmission),
      time_to_live_per_prio_(ToTtlPerPrio(packet_queue_ttl)),
      queue_time_sum_(TimeDelta::Zero()),
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
  int prio_level =
      GetPriorityForType(packet_type, prioritize_audio_retransmission_
                                          ? packet->original_packet_type()
                                          : absl::nullopt);
  PurgeOldPacketsAtPriorityLevel(prio_level, enqueue_time);
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
  QueuedPacket packet = stream_queue.DequeuePacket(top_active_prio_level_);
  DequeuePacketInternal(packet);

  // Remove StreamQueue from head of fifo-queue for this prio level, and
  // and add it to the end if it still has packets.
  streams_by_prio_[top_active_prio_level_].pop_front();
  if (stream_queue.HasPacketsAtPrio(top_active_prio_level_)) {
    streams_by_prio_[top_active_prio_level_].push_back(&stream_queue);
  } else {
    MaybeUpdateTopPrioLevel();
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
  RTC_DCHECK(type != RtpPacketMediaType::kRetransmission);
  const int priority_level = GetPriorityForType(type, absl::nullopt);
  if (streams_by_prio_[priority_level].empty()) {
    return Timestamp::MinusInfinity();
  }
  return streams_by_prio_[priority_level].front()->LeadingPacketEnqueueTime(
      priority_level);
}

Timestamp PrioritizedPacketQueue::LeadingPacketEnqueueTimeForRetransmission()
    const {
  if (!prioritize_audio_retransmission_) {
    const int priority_level =
        GetPriorityForType(RtpPacketMediaType::kRetransmission, absl::nullopt);
    if (streams_by_prio_[priority_level].empty()) {
      return Timestamp::PlusInfinity();
    }
    return streams_by_prio_[priority_level].front()->LeadingPacketEnqueueTime(
        priority_level);
  }
  const int audio_priority_level =
      GetPriorityForType(RtpPacketMediaType::kRetransmission,
                         RtpPacketToSend::OriginalType::kAudio);
  const int video_priority_level =
      GetPriorityForType(RtpPacketMediaType::kRetransmission,
                         RtpPacketToSend::OriginalType::kVideo);

  Timestamp next_audio =
      streams_by_prio_[audio_priority_level].empty()
          ? Timestamp::PlusInfinity()
          : streams_by_prio_[audio_priority_level]
                .front()
                ->LeadingPacketEnqueueTime(audio_priority_level);
  Timestamp next_video =
      streams_by_prio_[video_priority_level].empty()
          ? Timestamp::PlusInfinity()
          : streams_by_prio_[video_priority_level]
                .front()
                ->LeadingPacketEnqueueTime(video_priority_level);
  return std::min(next_audio, next_video);
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

void PrioritizedPacketQueue::RemovePacketsForSsrc(uint32_t ssrc) {
  auto kv = streams_.find(ssrc);
  if (kv != streams_.end()) {
    // Dequeue all packets from the queue for this SSRC.
    StreamQueue& queue = *kv->second;
    std::array<std::deque<QueuedPacket>, kNumPriorityLevels> packets_by_prio =
        queue.DequeueAll();
    for (int i = 0; i < kNumPriorityLevels; ++i) {
      std::deque<QueuedPacket>& packet_queue = packets_by_prio[i];
      if (packet_queue.empty()) {
        continue;
      }

      // First erase all packets at this prio level.
      while (!packet_queue.empty()) {
        QueuedPacket packet = std::move(packet_queue.front());
        packet_queue.pop_front();
        DequeuePacketInternal(packet);
      }

      // Next, deregister this `StreamQueue` from the round-robin tables.
      RTC_DCHECK(!streams_by_prio_[i].empty());
      if (streams_by_prio_[i].size() == 1) {
        // This is the last and only queue that had packets for this prio level.
        // Update the global top prio level if neccessary.
        RTC_DCHECK(streams_by_prio_[i].front() == &queue);
        streams_by_prio_[i].pop_front();
      } else {
        // More than stream had packets at this prio level, filter this one out.
        std::deque<StreamQueue*> filtered_queue;
        for (StreamQueue* queue_ptr : streams_by_prio_[i]) {
          if (queue_ptr != &queue) {
            filtered_queue.push_back(queue_ptr);
          }
        }
        streams_by_prio_[i].swap(filtered_queue);
      }
    }
  }
  MaybeUpdateTopPrioLevel();
}

bool PrioritizedPacketQueue::HasKeyframePackets(uint32_t ssrc) const {
  auto it = streams_.find(ssrc);
  if (it != streams_.end()) {
    return it->second->has_keyframe_packets();
  }
  return false;
}

void PrioritizedPacketQueue::DequeuePacketInternal(QueuedPacket& packet) {
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
}

void PrioritizedPacketQueue::MaybeUpdateTopPrioLevel() {
  if (top_active_prio_level_ != -1 &&
      !streams_by_prio_[top_active_prio_level_].empty()) {
    return;
  }
  // No stream queues have packets at top_active_prio_level_, find top priority
  // that is not empty.
  for (int i = 0; i < kNumPriorityLevels; ++i) {
    PurgeOldPacketsAtPriorityLevel(i, last_update_time_);
    if (!streams_by_prio_[i].empty()) {
      top_active_prio_level_ = i;
      break;
    }
  }
  if (size_packets_ == 0) {
    // There are no packets left to send. Last packet may have been purged. Prio
    // will change when a new packet is pushed.
    top_active_prio_level_ = -1;
  }
}

void PrioritizedPacketQueue::PurgeOldPacketsAtPriorityLevel(int prio_level,
                                                            Timestamp now) {
  RTC_DCHECK(prio_level >= 0 && prio_level < kNumPriorityLevels);
  TimeDelta time_to_live = time_to_live_per_prio_[prio_level];
  if (time_to_live.IsInfinite()) {
    return;
  }

  std::deque<StreamQueue*>& queues = streams_by_prio_[prio_level];
  auto iter = queues.begin();
  while (iter != queues.end()) {
    StreamQueue* queue_ptr = *iter;
    while (queue_ptr->HasPacketsAtPrio(prio_level) &&
           (now - queue_ptr->LeadingPacketEnqueueTime(prio_level)) >
               time_to_live) {
      QueuedPacket packet = queue_ptr->DequeuePacket(prio_level);
      RTC_LOG(LS_INFO) << "Dropping old packet on SSRC: "
                       << packet.packet->Ssrc()
                       << " seq:" << packet.packet->SequenceNumber()
                       << " time in queue:" << (now - packet.enqueue_time).ms()
                       << " ms";
      DequeuePacketInternal(packet);
    }
    if (!queue_ptr->HasPacketsAtPrio(prio_level)) {
      iter = queues.erase(iter);
    } else {
      ++iter;
    }
  }
}

}  // namespace webrtc
