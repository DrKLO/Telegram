/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTCP_PACKET_TRANSPORT_FEEDBACK_H_
#define MODULES_RTP_RTCP_SOURCE_RTCP_PACKET_TRANSPORT_FEEDBACK_H_

#include <memory>
#include <vector>

#include "api/units/time_delta.h"
#include "modules/rtp_rtcp/source/rtcp_packet/rtpfb.h"

namespace webrtc {
namespace rtcp {
class CommonHeader;

class TransportFeedback : public Rtpfb {
 public:
  class ReceivedPacket {
   public:
    ReceivedPacket(uint16_t sequence_number, int16_t delta_ticks)
        : sequence_number_(sequence_number),
          delta_ticks_(delta_ticks),
          received_(true) {}
    explicit ReceivedPacket(uint16_t sequence_number)
        : sequence_number_(sequence_number), received_(false) {}
    ReceivedPacket(const ReceivedPacket&) = default;
    ReceivedPacket& operator=(const ReceivedPacket&) = default;

    uint16_t sequence_number() const { return sequence_number_; }
    int16_t delta_ticks() const { return delta_ticks_; }
    int32_t delta_us() const { return delta_ticks_ * kDeltaScaleFactor; }
    TimeDelta delta() const { return TimeDelta::Micros(delta_us()); }
    bool received() const { return received_; }

   private:
    uint16_t sequence_number_;
    int16_t delta_ticks_;
    bool received_;
  };
  // TODO(sprang): IANA reg?
  static constexpr uint8_t kFeedbackMessageType = 15;
  // Convert to multiples of 0.25ms.
  static constexpr int kDeltaScaleFactor = 250;
  // Maximum number of packets (including missing) TransportFeedback can report.
  static constexpr size_t kMaxReportedPackets = 0xffff;

  TransportFeedback();

  // If `include_timestamps` is set to false, the created packet will not
  // contain the receive delta block.
  explicit TransportFeedback(bool include_timestamps,
                             bool include_lost = false);
  TransportFeedback(const TransportFeedback&);
  TransportFeedback(TransportFeedback&&);

  ~TransportFeedback() override;

  void SetBase(uint16_t base_sequence,     // Seq# of first packet in this msg.
               int64_t ref_timestamp_us);  // Reference timestamp for this msg.
  void SetFeedbackSequenceNumber(uint8_t feedback_sequence);
  // NOTE: This method requires increasing sequence numbers (excepting wraps).
  bool AddReceivedPacket(uint16_t sequence_number, int64_t timestamp_us);
  const std::vector<ReceivedPacket>& GetReceivedPackets() const;
  const std::vector<ReceivedPacket>& GetAllPackets() const;

  uint16_t GetBaseSequence() const;

  // Returns number of packets (including missing) this feedback describes.
  size_t GetPacketStatusCount() const { return num_seq_no_; }

  // Get the reference time in microseconds, including any precision loss.
  int64_t GetBaseTimeUs() const;
  TimeDelta GetBaseTime() const;

  // Get the unwrapped delta between current base time and `prev_timestamp_us`.
  int64_t GetBaseDeltaUs(int64_t prev_timestamp_us) const;
  TimeDelta GetBaseDelta(TimeDelta prev_timestamp) const;

  // Does the feedback packet contain timestamp information?
  bool IncludeTimestamps() const { return include_timestamps_; }

  bool Parse(const CommonHeader& packet);
  static std::unique_ptr<TransportFeedback> ParseFrom(const uint8_t* buffer,
                                                      size_t length);
  // Pre and postcondition for all public methods. Should always return true.
  // This function is for tests.
  bool IsConsistent() const;

  size_t BlockLength() const override;
  size_t PaddingLength() const;

  bool Create(uint8_t* packet,
              size_t* position,
              size_t max_length,
              PacketReadyCallback callback) const override;

 private:
  // Size in bytes of a delta time in rtcp packet.
  // Valid values are 0 (packet wasn't received), 1 or 2.
  using DeltaSize = uint8_t;
  // Keeps DeltaSizes that can be encoded into single chunk if it is last chunk.
  class LastChunk {
   public:
    using DeltaSize = TransportFeedback::DeltaSize;

    LastChunk();

    bool Empty() const;
    void Clear();
    // Return if delta sizes still can be encoded into single chunk with added
    // `delta_size`.
    bool CanAdd(DeltaSize delta_size) const;
    // Add `delta_size`, assumes `CanAdd(delta_size)`,
    void Add(DeltaSize delta_size);

    // Encode chunk as large as possible removing encoded delta sizes.
    // Assume CanAdd() == false for some valid delta_size.
    uint16_t Emit();
    // Encode all stored delta_sizes into single chunk, pad with 0s if needed.
    uint16_t EncodeLast() const;

    // Decode up to `max_size` delta sizes from `chunk`.
    void Decode(uint16_t chunk, size_t max_size);
    // Appends content of the Lastchunk to `deltas`.
    void AppendTo(std::vector<DeltaSize>* deltas) const;

   private:
    static constexpr size_t kMaxRunLengthCapacity = 0x1fff;
    static constexpr size_t kMaxOneBitCapacity = 14;
    static constexpr size_t kMaxTwoBitCapacity = 7;
    static constexpr size_t kMaxVectorCapacity = kMaxOneBitCapacity;
    static constexpr DeltaSize kLarge = 2;

    uint16_t EncodeOneBit() const;
    void DecodeOneBit(uint16_t chunk, size_t max_size);

    uint16_t EncodeTwoBit(size_t size) const;
    void DecodeTwoBit(uint16_t chunk, size_t max_size);

    uint16_t EncodeRunLength() const;
    void DecodeRunLength(uint16_t chunk, size_t max_size);

    DeltaSize delta_sizes_[kMaxVectorCapacity];
    size_t size_;
    bool all_same_;
    bool has_large_delta_;
  };

  // Reset packet to consistent empty state.
  void Clear();

  bool AddDeltaSize(DeltaSize delta_size);

  const bool include_lost_;
  uint16_t base_seq_no_;
  uint16_t num_seq_no_;
  int32_t base_time_ticks_;
  uint8_t feedback_seq_;
  bool include_timestamps_;

  int64_t last_timestamp_us_;
  std::vector<ReceivedPacket> received_packets_;
  std::vector<ReceivedPacket> all_packets_;
  // All but last encoded packet chunks.
  std::vector<uint16_t> encoded_chunks_;
  LastChunk last_chunk_;
  size_t size_bytes_;
};

}  // namespace rtcp
}  // namespace webrtc
#endif  // MODULES_RTP_RTCP_SOURCE_RTCP_PACKET_TRANSPORT_FEEDBACK_H_
