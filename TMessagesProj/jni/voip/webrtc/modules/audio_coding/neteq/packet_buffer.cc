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
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
// Predicate used when inserting packets in the buffer list.
// Operator() returns true when `packet` goes before `new_packet`.
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

absl::optional<SmartFlushingConfig> GetSmartflushingConfig() {
  absl::optional<SmartFlushingConfig> result;
  std::string field_trial_string =
      field_trial::FindFullName("WebRTC-Audio-NetEqSmartFlushing");
  result = SmartFlushingConfig();
  bool enabled = false;
  auto parser = StructParametersParser::Create(
      "enabled", &enabled, "target_level_threshold_ms",
      &result->target_level_threshold_ms, "target_level_multiplier",
      &result->target_level_multiplier);
  parser->Parse(field_trial_string);
  if (!enabled) {
    return absl::nullopt;
  }
  RTC_LOG(LS_INFO) << "Using smart flushing, target_level_threshold_ms: "
                   << result->target_level_threshold_ms
                   << ", target_level_multiplier: "
                   << result->target_level_multiplier;
  return result;
}

}  // namespace

PacketBuffer::PacketBuffer(size_t max_number_of_packets,
                           const TickTimer* tick_timer)
    : smart_flushing_config_(GetSmartflushingConfig()),
      max_number_of_packets_(max_number_of_packets),
      tick_timer_(tick_timer) {}

// Destructor. All packets in the buffer will be destroyed.
PacketBuffer::~PacketBuffer() {
  buffer_.clear();
}

// Flush the buffer. All packets in the buffer will be destroyed.
void PacketBuffer::Flush(StatisticsCalculator* stats) {
  for (auto& p : buffer_) {
    LogPacketDiscarded(p.priority.codec_level, stats);
  }
  buffer_.clear();
  stats->FlushedPacketBuffer();
}

void PacketBuffer::PartialFlush(int target_level_ms,
                                size_t sample_rate,
                                size_t last_decoded_length,
                                StatisticsCalculator* stats) {
  // Make sure that at least half the packet buffer capacity will be available
  // after the flush. This is done to avoid getting stuck if the target level is
  // very high.
  int target_level_samples =
      std::min(target_level_ms * sample_rate / 1000,
               max_number_of_packets_ * last_decoded_length / 2);
  // We should avoid flushing to very low levels.
  target_level_samples = std::max(
      target_level_samples, smart_flushing_config_->target_level_threshold_ms);
  while (GetSpanSamples(last_decoded_length, sample_rate, true) >
             static_cast<size_t>(target_level_samples) ||
         buffer_.size() > max_number_of_packets_ / 2) {
    LogPacketDiscarded(PeekNextPacket()->priority.codec_level, stats);
    buffer_.pop_front();
  }
}

bool PacketBuffer::Empty() const {
  return buffer_.empty();
}

int PacketBuffer::InsertPacket(Packet&& packet,
                               StatisticsCalculator* stats,
                               size_t last_decoded_length,
                               size_t sample_rate,
                               int target_level_ms,
                               const DecoderDatabase& decoder_database) {
  if (packet.empty()) {
    RTC_LOG(LS_WARNING) << "InsertPacket invalid packet";
    return kInvalidPacket;
  }

  RTC_DCHECK_GE(packet.priority.codec_level, 0);
  RTC_DCHECK_GE(packet.priority.red_level, 0);

  int return_val = kOK;

  packet.waiting_time = tick_timer_->GetNewStopwatch();

  // Perform a smart flush if the buffer size exceeds a multiple of the target
  // level.
  const size_t span_threshold =
      smart_flushing_config_
          ? smart_flushing_config_->target_level_multiplier *
                std::max(smart_flushing_config_->target_level_threshold_ms,
                         target_level_ms) *
                sample_rate / 1000
          : 0;
  const bool smart_flush =
      smart_flushing_config_.has_value() &&
      GetSpanSamples(last_decoded_length, sample_rate, true) >= span_threshold;
  if (buffer_.size() >= max_number_of_packets_ || smart_flush) {
    size_t buffer_size_before_flush = buffer_.size();
    if (smart_flushing_config_.has_value()) {
      // Flush down to the target level.
      PartialFlush(target_level_ms, sample_rate, last_decoded_length, stats);
      return_val = kPartialFlush;
    } else {
      // Buffer is full.
      Flush(stats);
      return_val = kFlushed;
    }
    RTC_LOG(LS_WARNING) << "Packet buffer flushed, "
                        << (buffer_size_before_flush - buffer_.size())
                        << " packets discarded.";
  }

  // Get an iterator pointing to the place in the buffer where the new packet
  // should be inserted. The list is searched from the back, since the most
  // likely case is that the new packet should be near the end of the list.
  PacketList::reverse_iterator rit = std::find_if(
      buffer_.rbegin(), buffer_.rend(), NewTimestampIsLarger(packet));

  // The new packet is to be inserted to the right of `rit`. If it has the same
  // timestamp as `rit`, which has a higher priority, do not insert the new
  // packet to list.
  if (rit != buffer_.rend() && packet.timestamp == rit->timestamp) {
    LogPacketDiscarded(packet.priority.codec_level, stats);
    return return_val;
  }

  // The new packet is to be inserted to the left of `it`. If it has the same
  // timestamp as `it`, which has a lower priority, replace `it` with the new
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
    StatisticsCalculator* stats,
    size_t last_decoded_length,
    size_t sample_rate,
    int target_level_ms) {
  RTC_DCHECK(stats);
  bool flushed = false;
  for (auto& packet : *packet_list) {
    if (decoder_database.IsComfortNoise(packet.payload_type)) {
      if (*current_cng_rtp_payload_type &&
          **current_cng_rtp_payload_type != packet.payload_type) {
        // New CNG payload type implies new codec type.
        *current_rtp_payload_type = absl::nullopt;
        Flush(stats);
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
        Flush(stats);
        flushed = true;
      }
      *current_rtp_payload_type = packet.payload_type;
    }
    int return_val =
        InsertPacket(std::move(packet), stats, last_decoded_length, sample_rate,
                     target_level_ms, decoder_database);
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
