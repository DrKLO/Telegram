/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This is the implementation of the PacketBuffer class. It is mostly based on
// an STL list. The list is kept sorted at all times so that the next packet to
// decode is at the beginning of the list.

#include "modules/audio_coding/neteq/packet_buffer.h"

#include <algorithm>
#include <list>
#include <memory>
#include <type_traits>
#include <utility>

#include "api/audio_codecs/audio_decoder.h"
#include "api/neteq/tick_timer.h"
#include "modules/audio_coding/neteq/decoder_database.h"
#include "modules/audio_coding/neteq/statistics_calculator.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace {
// Predicate used when inserting packets in the buffer list.
// Operator() returns true when |packet| goes before |new_packet|.
class NewTimestampIsLarger {
 public:
  explicit NewTimestampIsLarger(const Packet& new_packet)
      : new_packet_(new_packet) {}
  bool operator()(const Packet& packet) { return (new_packet_ >= packet); }

 private:
  const Packet& new_packet_;
};

// Returns true if both payload types are known to the decoder database, and
// have the same sample rate.
bool EqualSampleRates(uint8_t pt1,
                      uint8_t pt2,
                      const DecoderDatabase& decoder_database) {
  auto* di1 = decoder_database.GetDecoderInfo(pt1);
  auto* di2 = decoder_database.GetDecoderInfo(pt2);
  return di1 && di2 && di1->SampleRateHz() == di2->SampleRateHz();
}

void LogPacketDiscarded(int codec_level, StatisticsCalculator* stats) {
  RTC_CHECK(stats);
  if (codec_level > 0) {
    stats->SecondaryPacketsDiscarded(1);
  } else {
    stats->PacketsDiscarded(1);
  }
}

}  // namespace

PacketBuffer::PacketBuffer(size_t max_number_of_packets,
                           const TickTimer* tick_timer)
    : max_number_of_packets_(max_number_of_packets), tick_timer_(tick_timer) {}

// Destructor. All packets in the buffer will be destroyed.
PacketBuffer::~PacketBuffer() {
  Flush();
}

// Flush the buffer. All packets in the buffer will be destroyed.
void PacketBuffer::Flush() {
  buffer_.clear();
}

bool PacketBuffer::Empty() const {
  return buffer_.empty();
}

int PacketBuffer::InsertPacket(Packet&& packet, StatisticsCalculator* stats) {
  if (packet.empty()) {
    RTC_LOG(LS_WARNING) << "InsertPacket invalid packet";
    return kInvalidPacket;
  }

  RTC_DCHECK_GE(packet.priority.codec_level, 0);
  RTC_DCHECK_GE(packet.priority.red_level, 0);

  int return_val = kOK;

  packet.waiting_time = tick_timer_->GetNewStopwatch();

  if (buffer_.size() >= max_number_of_packets_) {
    // Buffer is full. Flush it.
    Flush();
    stats->FlushedPacketBuffer();
    RTC_LOG(LS_WARNING) << "Packet buffer flushed";
    return_val = kFlushed;
  }

  // Get an iterator pointing to the place in the buffer where the new packet
  // should be inserted. The list is searched from the back, since the most
  // likely case is that the new packet should be near the end of the list.
  PacketList::reverse_iterator rit = std::find_if(
      buffer_.rbegin(), buffer_.rend(), NewTimestampIsLarger(packet));

  // The new packet is to be inserted to the right of |rit|. If it has the same
  // timestamp as |rit|, which has a higher priority, do not insert the new
  // packet to list.
  if (rit != buffer_.rend() && packet.timestamp == rit->timestamp) {
    LogPacketDiscarded(packet.priority.codec_level, stats);
    return return_val;
  }

  // The new packet is to be inserted to the left of |it|. If it has the same
  // timestamp as |it|, which has a lower priority, replace |it| with the new
  // packet.
  PacketList::iterator it = rit.base();
  if (it != buffer_.end() && packet.timestamp == it->timestamp) {
    LogPacketDiscarded(it->priority.codec_level, stats);
    it = buffer_.erase(it);
  }
  buffer_.insert(it, std::move(packet));  // Insert the packet at that position.

  return return_val;
}

int PacketBuffer::InsertPacketList(
    PacketList* packet_list,
    const DecoderDatabase& decoder_database,
    absl::optional<uint8_t>* current_rtp_payload_type,
    absl::optional<uint8_t>* current_cng_rtp_payload_type,
    StatisticsCalculator* stats) {
  RTC_DCHECK(stats);
  bool flushed = false;
  for (auto& packet : *packet_list) {
    if (decoder_database.IsComfortNoise(packet.payload_type)) {
      if (*current_cng_rtp_payload_type &&
          **current_cng_rtp_payload_type != packet.payload_type) {
        // New CNG payload type implies new codec type.
        *current_rtp_payload_type = absl::nullopt;
        Flush();
        flushed = true;
      }
      *current_cng_rtp_payload_type = packet.payload_type;
    } else if (!decoder_database.IsDtmf(packet.payload_type)) {
      // This must be speech.
      if ((*current_rtp_payload_type &&
           **current_rtp_payload_type != packet.payload_type) ||
          (*current_cng_rtp_payload_type &&
           !EqualSampleRates(packet.payload_type,
                             **current_cng_rtp_payload_type,
                             decoder_database))) {
        *current_cng_rtp_payload_type = absl::nullopt;
        Flush();
        flushed = true;
      }
      *current_rtp_payload_type = packet.payload_type;
    }
    int return_val = InsertPacket(std::move(packet), stats);
    if (return_val == kFlushed) {
      // The buffer flushed, but this is not an error. We can still continue.
      flushed = true;
    } else if (return_val != kOK) {
      // An error occurred. Delete remaining packets in list and return.
      packet_list->clear();
      return return_val;
    }
  }
  packet_list->clear();
  return flushed ? kFlushed : kOK;
}

int PacketBuffer::NextTimestamp(uint32_t* next_timestamp) const {
  if (Empty()) {
    return kBufferEmpty;
  }
  if (!next_timestamp) {
    return kInvalidPointer;
  }
  *next_timestamp = buffer_.front().timestamp;
  return kOK;
}

int PacketBuffer::NextHigherTimestamp(uint32_t timestamp,
                                      uint32_t* next_timestamp) const {
  if (Empty()) {
    return kBufferEmpty;
  }
  if (!next_timestamp) {
    return kInvalidPointer;
  }
  PacketList::const_iterator it;
  for (it = buffer_.begin(); it != buffer_.end(); ++it) {
    if (it->timestamp >= timestamp) {
      // Found a packet matching the search.
      *next_timestamp = it->timestamp;
      return kOK;
    }
  }
  return kNotFound;
}

const Packet* PacketBuffer::PeekNextPacket() const {
  return buffer_.empty() ? nullptr : &buffer_.front();
}

absl::optional<Packet> PacketBuffer::GetNextPacket() {
  if (Empty()) {
    // Buffer is empty.
    return absl::nullopt;
  }

  absl::optional<Packet> packet(std::move(buffer_.front()));
  // Assert that the packet sanity checks in InsertPacket method works.
  RTC_DCHECK(!packet->empty());
  buffer_.pop_front();

  return packet;
}

int PacketBuffer::DiscardNextPacket(StatisticsCalculator* stats) {
  if (Empty()) {
    return kBufferEmpty;
  }
  // Assert that the packet sanity checks in InsertPacket method works.
  const Packet& packet = buffer_.front();
  RTC_DCHECK(!packet.empty());
  LogPacketDiscarded(packet.priority.codec_level, stats);
  buffer_.pop_front();
  return kOK;
}

void PacketBuffer::DiscardOldPackets(uint32_t timestamp_limit,
                                     uint32_t horizon_samples,
                                     StatisticsCalculator* stats) {
  buffer_.remove_if([timestamp_limit, horizon_samples, stats](const Packet& p) {
    if (timestamp_limit == p.timestamp ||
        !IsObsoleteTimestamp(p.timestamp, timestamp_limit, horizon_samples)) {
      return false;
    }
    LogPacketDiscarded(p.priority.codec_level, stats);
    return true;
  });
}

void PacketBuffer::DiscardAllOldPackets(uint32_t timestamp_limit,
                                        StatisticsCalculator* stats) {
  DiscardOldPackets(timestamp_limit, 0, stats);
}

void PacketBuffer::DiscardPacketsWithPayloadType(uint8_t payload_type,
                                                 StatisticsCalculator* stats) {
  buffer_.remove_if([payload_type, stats](const Packet& p) {
    if (p.payload_type != payload_type) {
      return false;
    }
    LogPacketDiscarded(p.priority.codec_level, stats);
    return true;
  });
}

size_t PacketBuffer::NumPacketsInBuffer() const {
  return buffer_.size();
}

size_t PacketBuffer::NumSamplesInBuffer(size_t last_decoded_length) const {
  size_t num_samples = 0;
  size_t last_duration = last_decoded_length;
  for (const Packet& packet : buffer_) {
    if (packet.frame) {
      // TODO(hlundin): Verify that it's fine to count all packets and remove
      // this check.
      if (packet.priority != Packet::Priority(0, 0)) {
        continue;
      }
      size_t duration = packet.frame->Duration();
      if (duration > 0) {
        last_duration = duration;  // Save the most up-to-date (valid) duration.
      }
    }
    num_samples += last_duration;
  }
  return num_samples;
}

size_t PacketBuffer::GetSpanSamples(size_t last_decoded_length,
                                    size_t sample_rate,
                                    bool count_dtx_waiting_time) const {
  if (buffer_.size() == 0) {
    return 0;
  }

  size_t span = buffer_.back().timestamp - buffer_.front().timestamp;
  if (buffer_.back().frame && buffer_.back().frame->Duration() > 0) {
    size_t duration = buffer_.back().frame->Duration();
    if (count_dtx_waiting_time && buffer_.back().frame->IsDtxPacket()) {
      size_t waiting_time_samples = rtc::dchecked_cast<size_t>(
          buffer_.back().waiting_time->ElapsedMs() * (sample_rate / 1000));
      duration = std::max(duration, waiting_time_samples);
    }
    span += duration;
  } else {
    span += last_decoded_length;
  }
  return span;
}

bool PacketBuffer::ContainsDtxOrCngPacket(
    const DecoderDatabase* decoder_database) const {
  RTC_DCHECK(decoder_database);
  for (const Packet& packet : buffer_) {
    if ((packet.frame && packet.frame->IsDtxPacket()) ||
        decoder_database->IsComfortNoise(packet.payload_type)) {
      return true;
    }
  }
  return false;
}

}  // namespace webrtc
