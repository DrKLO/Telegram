/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_format_h264.h"

#include <string.h>

#include <cstddef>
#include <cstdint>
#include <iterator>
#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "absl/types/variant.h"
#include "common_video/h264/h264_common.h"
#include "common_video/h264/pps_parser.h"
#include "common_video/h264/sps_parser.h"
#include "common_video/h264/sps_vui_rewriter.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

static const size_t kNalHeaderSize = 1;
static const size_t kFuAHeaderSize = 2;
static const size_t kLengthFieldSize = 2;

// Bit masks for FU (A and B) indicators.
enum NalDefs : uint8_t { kFBit = 0x80, kNriMask = 0x60, kTypeMask = 0x1F };

// Bit masks for FU (A and B) headers.
enum FuDefs : uint8_t { kSBit = 0x80, kEBit = 0x40, kRBit = 0x20 };

}  // namespace

RtpPacketizerH264::RtpPacketizerH264(rtc::ArrayView<const uint8_t> payload,
                                     PayloadSizeLimits limits,
                                     H264PacketizationMode packetization_mode)
    : limits_(limits), num_packets_left_(0) {
  // Guard against uninitialized memory in packetization_mode.
  RTC_CHECK(packetization_mode == H264PacketizationMode::NonInterleaved ||
            packetization_mode == H264PacketizationMode::SingleNalUnit);

  for (const auto& nalu :
       H264::FindNaluIndices(payload.data(), payload.size())) {
    input_fragments_.push_back(
        payload.subview(nalu.payload_start_offset, nalu.payload_size));
  }

  if (!GeneratePackets(packetization_mode)) {
    // If failed to generate all the packets, discard already generated
    // packets in case the caller would ignore return value and still try to
    // call NextPacket().
    num_packets_left_ = 0;
    while (!packets_.empty()) {
      packets_.pop();
    }
  }
}

RtpPacketizerH264::~RtpPacketizerH264() = default;

size_t RtpPacketizerH264::NumPackets() const {
  return num_packets_left_;
}

bool RtpPacketizerH264::GeneratePackets(
    H264PacketizationMode packetization_mode) {
  for (size_t i = 0; i < input_fragments_.size();) {
    switch (packetization_mode) {
      case H264PacketizationMode::SingleNalUnit:
        if (!PacketizeSingleNalu(i))
          return false;
        ++i;
        break;
      case H264PacketizationMode::NonInterleaved:
        int fragment_len = input_fragments_[i].size();
        int single_packet_capacity = limits_.max_payload_len;
        if (input_fragments_.size() == 1)
          single_packet_capacity -= limits_.single_packet_reduction_len;
        else if (i == 0)
          single_packet_capacity -= limits_.first_packet_reduction_len;
        else if (i + 1 == input_fragments_.size())
          single_packet_capacity -= limits_.last_packet_reduction_len;

        if (fragment_len > single_packet_capacity) {
          if (!PacketizeFuA(i))
            return false;
          ++i;
        } else {
          i = PacketizeStapA(i);
        }
        break;
    }
  }
  return true;
}

bool RtpPacketizerH264::PacketizeFuA(size_t fragment_index) {
  // Fragment payload into packets (FU-A).
  rtc::ArrayView<const uint8_t> fragment = input_fragments_[fragment_index];

  PayloadSizeLimits limits = limits_;
  // Leave room for the FU-A header.
  limits.max_payload_len -= kFuAHeaderSize;
  // Update single/first/last packet reductions unless it is single/first/last
  // fragment.
  if (input_fragments_.size() != 1) {
    // if this fragment is put into a single packet, it might still be the
    // first or the last packet in the whole sequence of packets.
    if (fragment_index == input_fragments_.size() - 1) {
      limits.single_packet_reduction_len = limits_.last_packet_reduction_len;
    } else if (fragment_index == 0) {
      limits.single_packet_reduction_len = limits_.first_packet_reduction_len;
    } else {
      limits.single_packet_reduction_len = 0;
    }
  }
  if (fragment_index != 0)
    limits.first_packet_reduction_len = 0;
  if (fragment_index != input_fragments_.size() - 1)
    limits.last_packet_reduction_len = 0;

  // Strip out the original header.
  size_t payload_left = fragment.size() - kNalHeaderSize;
  int offset = kNalHeaderSize;

  std::vector<int> payload_sizes = SplitAboutEqually(payload_left, limits);
  if (payload_sizes.empty())
    return false;

  for (size_t i = 0; i < payload_sizes.size(); ++i) {
    int packet_length = payload_sizes[i];
    RTC_CHECK_GT(packet_length, 0);
    packets_.push(PacketUnit(fragment.subview(offset, packet_length),
                             /*first_fragment=*/i == 0,
                             /*last_fragment=*/i == payload_sizes.size() - 1,
                             false, fragment[0]));
    offset += packet_length;
    payload_left -= packet_length;
  }
  num_packets_left_ += payload_sizes.size();
  RTC_CHECK_EQ(0, payload_left);
  return true;
}

size_t RtpPacketizerH264::PacketizeStapA(size_t fragment_index) {
  // Aggregate fragments into one packet (STAP-A).
  size_t payload_size_left = limits_.max_payload_len;
  if (input_fragments_.size() == 1)
    payload_size_left -= limits_.single_packet_reduction_len;
  else if (fragment_index == 0)
    payload_size_left -= limits_.first_packet_reduction_len;
  int aggregated_fragments = 0;
  size_t fragment_headers_length = 0;
  rtc::ArrayView<const uint8_t> fragment = input_fragments_[fragment_index];
  RTC_CHECK_GE(payload_size_left, fragment.size());
  ++num_packets_left_;

  auto payload_size_needed = [&] {
    size_t fragment_size = fragment.size() + fragment_headers_length;
    if (input_fragments_.size() == 1) {
      // Single fragment, single packet, payload_size_left already adjusted
      // with limits_.single_packet_reduction_len.
      return fragment_size;
    }
    if (fragment_index == input_fragments_.size() - 1) {
      // Last fragment, so STAP-A might be the last packet.
      return fragment_size + limits_.last_packet_reduction_len;
    }
    return fragment_size;
  };

  while (payload_size_left >= payload_size_needed()) {
    RTC_CHECK_GT(fragment.size(), 0);
    packets_.push(PacketUnit(fragment, aggregated_fragments == 0, false, true,
                             fragment[0]));
    payload_size_left -= fragment.size();
    payload_size_left -= fragment_headers_length;

    fragment_headers_length = kLengthFieldSize;
    // If we are going to try to aggregate more fragments into this packet
    // we need to add the STAP-A NALU header and a length field for the first
    // NALU of this packet.
    if (aggregated_fragments == 0)
      fragment_headers_length += kNalHeaderSize + kLengthFieldSize;
    ++aggregated_fragments;

    // Next fragment.
    ++fragment_index;
    if (fragment_index == input_fragments_.size())
      break;
    fragment = input_fragments_[fragment_index];
  }
  RTC_CHECK_GT(aggregated_fragments, 0);
  packets_.back().last_fragment = true;
  return fragment_index;
}

bool RtpPacketizerH264::PacketizeSingleNalu(size_t fragment_index) {
  // Add a single NALU to the queue, no aggregation.
  size_t payload_size_left = limits_.max_payload_len;
  if (input_fragments_.size() == 1)
    payload_size_left -= limits_.single_packet_reduction_len;
  else if (fragment_index == 0)
    payload_size_left -= limits_.first_packet_reduction_len;
  else if (fragment_index + 1 == input_fragments_.size())
    payload_size_left -= limits_.last_packet_reduction_len;
  rtc::ArrayView<const uint8_t> fragment = input_fragments_[fragment_index];
  if (payload_size_left < fragment.size()) {
    RTC_LOG(LS_ERROR) << "Failed to fit a fragment to packet in SingleNalu "
                         "packetization mode. Payload size left "
                      << payload_size_left << ", fragment length "
                      << fragment.size() << ", packet capacity "
                      << limits_.max_payload_len;
    return false;
  }
  RTC_CHECK_GT(fragment.size(), 0u);
  packets_.push(PacketUnit(fragment, true /* first */, true /* last */,
                           false /* aggregated */, fragment[0]));
  ++num_packets_left_;
  return true;
}

bool RtpPacketizerH264::NextPacket(RtpPacketToSend* rtp_packet) {
  RTC_DCHECK(rtp_packet);
  if (packets_.empty()) {
    return false;
  }

  PacketUnit packet = packets_.front();
  if (packet.first_fragment && packet.last_fragment) {
    // Single NAL unit packet.
    size_t bytes_to_send = packet.source_fragment.size();
    uint8_t* buffer = rtp_packet->AllocatePayload(bytes_to_send);
    memcpy(buffer, packet.source_fragment.data(), bytes_to_send);
    packets_.pop();
    input_fragments_.pop_front();
  } else if (packet.aggregated) {
    NextAggregatePacket(rtp_packet);
  } else {
    NextFragmentPacket(rtp_packet);
  }
  rtp_packet->SetMarker(packets_.empty());
  --num_packets_left_;
  return true;
}

void RtpPacketizerH264::NextAggregatePacket(RtpPacketToSend* rtp_packet) {
  // Reserve maximum available payload, set actual payload size later.
  size_t payload_capacity = rtp_packet->FreeCapacity();
  RTC_CHECK_GE(payload_capacity, kNalHeaderSize);
  uint8_t* buffer = rtp_packet->AllocatePayload(payload_capacity);
  RTC_DCHECK(buffer);
  PacketUnit* packet = &packets_.front();
  RTC_CHECK(packet->first_fragment);
  // STAP-A NALU header.
  buffer[0] = (packet->header & (kFBit | kNriMask)) | H264::NaluType::kStapA;
  size_t index = kNalHeaderSize;
  bool is_last_fragment = packet->last_fragment;
  while (packet->aggregated) {
    rtc::ArrayView<const uint8_t> fragment = packet->source_fragment;
    RTC_CHECK_LE(index + kLengthFieldSize + fragment.size(), payload_capacity);
    // Add NAL unit length field.
    ByteWriter<uint16_t>::WriteBigEndian(&buffer[index], fragment.size());
    index += kLengthFieldSize;
    // Add NAL unit.
    memcpy(&buffer[index], fragment.data(), fragment.size());
    index += fragment.size();
    packets_.pop();
    input_fragments_.pop_front();
    if (is_last_fragment)
      break;
    packet = &packets_.front();
    is_last_fragment = packet->last_fragment;
  }
  RTC_CHECK(is_last_fragment);
  rtp_packet->SetPayloadSize(index);
}

void RtpPacketizerH264::NextFragmentPacket(RtpPacketToSend* rtp_packet) {
  PacketUnit* packet = &packets_.front();
  // NAL unit fragmented over multiple packets (FU-A).
  // We do not send original NALU header, so it will be replaced by the
  // FU indicator header of the first packet.
  uint8_t fu_indicator =
      (packet->header & (kFBit | kNriMask)) | H264::NaluType::kFuA;
  uint8_t fu_header = 0;

  // S | E | R | 5 bit type.
  fu_header |= (packet->first_fragment ? kSBit : 0);
  fu_header |= (packet->last_fragment ? kEBit : 0);
  uint8_t type = packet->header & kTypeMask;
  fu_header |= type;
  rtc::ArrayView<const uint8_t> fragment = packet->source_fragment;
  uint8_t* buffer =
      rtp_packet->AllocatePayload(kFuAHeaderSize + fragment.size());
  buffer[0] = fu_indicator;
  buffer[1] = fu_header;
  memcpy(buffer + kFuAHeaderSize, fragment.data(), fragment.size());
  if (packet->last_fragment)
    input_fragments_.pop_front();
  packets_.pop();
}

}  // namespace webrtc
