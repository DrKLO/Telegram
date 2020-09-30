/*
 *  Intel License
 */

#include <string.h>

#include "absl/types/optional.h"
#include "absl/types/variant.h"

#include "common_video/h264/h264_common.h"
#include "common_video/h265/h265_common.h"
#include "common_video/h265/h265_pps_parser.h"
#include "common_video/h265/h265_sps_parser.h"
#include "common_video/h265/h265_vps_parser.h"
#include "modules/include/module_common_types.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_format_h265.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "rtc_base/logging.h"

using namespace rtc;

namespace webrtc {
namespace {

enum NaluType {
  kTrailN = 0,
  kTrailR = 1,
  kTsaN = 2,
  kTsaR = 3,
  kStsaN = 4,
  kStsaR = 5,
  kRadlN = 6,
  kRadlR = 7,
  kBlaWLp = 16,
  kBlaWRadl = 17,
  kBlaNLp = 18,
  kIdrWRadl = 19,
  kIdrNLp = 20,
  kCra = 21,
  kVps = 32,
  kHevcSps = 33,
  kHevcPps = 34,
  kHevcAud = 35,
  kPrefixSei = 39,
  kSuffixSei = 40,
  kHevcAp = 48,
  kHevcFu = 49
};

/*
   0                   1                   2                   3
   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |    PayloadHdr (Type=49)       |   FU header   | DONL (cond)   |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-|
*/
// Unlike H.264, HEVC NAL header is 2-bytes.
static const size_t kHevcNalHeaderSize = 2;
// H.265's FU is constructed of 2-byte payload header, and 1-byte FU header
static const size_t kHevcFuHeaderSize = 1;
static const size_t kHevcLengthFieldSize = 2;

enum HevcNalHdrMasks {
  kHevcFBit = 0x80,
  kHevcTypeMask = 0x7E,
  kHevcLayerIDHMask = 0x1,
  kHevcLayerIDLMask = 0xF8,
  kHevcTIDMask = 0x7,
  kHevcTypeMaskN = 0x81,
  kHevcTypeMaskInFuHeader = 0x3F
};

// Bit masks for FU headers.
enum HevcFuDefs { kHevcSBit = 0x80, kHevcEBit = 0x40, kHevcFuTypeBit = 0x3F };

}  // namespace

RtpPacketizerH265::RtpPacketizerH265(
    rtc::ArrayView<const uint8_t> payload,
    PayloadSizeLimits limits,
    H265PacketizationMode packetization_mode)
    : limits_(limits),
      num_packets_left_(0) {
  // Guard against uninitialized memory in packetization_mode.
  RTC_CHECK(packetization_mode == H265PacketizationMode::NonInterleaved ||
            packetization_mode == H265PacketizationMode::SingleNalUnit);

  for (const auto& nalu :
    H265::FindNaluIndices(payload.data(), payload.size())) {
    input_fragments_.push_back(Fragment(payload.data() + nalu.payload_start_offset, nalu.payload_size));
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

RtpPacketizerH265::~RtpPacketizerH265() {}

size_t RtpPacketizerH265::NumPackets() const {
  return num_packets_left_;
}

RtpPacketizerH265::Fragment::Fragment(const uint8_t* buffer, size_t length)
    : buffer(buffer), length(length) {}
RtpPacketizerH265::Fragment::Fragment(const Fragment& fragment)
    : buffer(fragment.buffer), length(fragment.length) {}


bool RtpPacketizerH265::GeneratePackets(
    H265PacketizationMode packetization_mode) {
  // For HEVC we follow non-interleaved mode for the packetization,
  // and don't support single-nalu mode at present.
  for (size_t i = 0; i < input_fragments_.size();) {
    int fragment_len = input_fragments_[i].length;
    int single_packet_capacity = limits_.max_payload_len;
    if (input_fragments_.size() == 1)
      single_packet_capacity -= limits_.single_packet_reduction_len;
    else if (i == 0)
      single_packet_capacity -= limits_.first_packet_reduction_len;
    else if (i + 1 == input_fragments_.size()) {
      // Pretend that last fragment is larger instead of making last packet
      // smaller.
      single_packet_capacity -= limits_.last_packet_reduction_len;
    }
    if (fragment_len > single_packet_capacity) {
      PacketizeFu(i);
      ++i;
    } else {
      PacketizeSingleNalu(i);
      ++i;
    }
  }
  return true;
}

bool RtpPacketizerH265::PacketizeFu(size_t fragment_index) {
  // Fragment payload into packets (FU).
  // Strip out the original header and leave room for the FU header.
  const Fragment& fragment = input_fragments_[fragment_index];
  PayloadSizeLimits limits = limits_;
  limits.max_payload_len -= kHevcFuHeaderSize + kHevcNalHeaderSize;

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
  size_t payload_left = fragment.length - kHevcNalHeaderSize;
  int offset = kHevcNalHeaderSize;

  std::vector<int> payload_sizes = SplitAboutEqually(payload_left, limits);
  if (payload_sizes.empty())
    return false;

  for (size_t i = 0; i < payload_sizes.size(); ++i) {
    int packet_length = payload_sizes[i];
    RTC_CHECK_GT(packet_length, 0);
    uint16_t header = (fragment.buffer[0] << 8) | fragment.buffer[1];
    packets_.push(PacketUnit(Fragment(fragment.buffer + offset, packet_length),
                             /*first_fragment=*/i == 0,
                             /*last_fragment=*/i == payload_sizes.size() - 1,
                             false, header));
    offset += packet_length;
    payload_left -= packet_length;
  }
  num_packets_left_ += payload_sizes.size();
  RTC_CHECK_EQ(0, payload_left);
  return true;
}


bool RtpPacketizerH265::PacketizeSingleNalu(size_t fragment_index) {
  // Add a single NALU to the queue, no aggregation.
  size_t payload_size_left = limits_.max_payload_len;
  if (input_fragments_.size() == 1)
    payload_size_left -= limits_.single_packet_reduction_len;
  else if (fragment_index == 0)
    payload_size_left -= limits_.first_packet_reduction_len;
  else if (fragment_index + 1 == input_fragments_.size())
    payload_size_left -= limits_.last_packet_reduction_len;
  const Fragment* fragment = &input_fragments_[fragment_index];
  if (payload_size_left < fragment->length) {
    RTC_LOG(LS_ERROR) << "Failed to fit a fragment to packet in SingleNalu "
                         "packetization mode. Payload size left "
                      << payload_size_left << ", fragment length "
                      << fragment->length << ", packet capacity "
                      << limits_.max_payload_len;
    return false;
  }
  RTC_CHECK_GT(fragment->length, 0u);
  packets_.push(PacketUnit(*fragment, true /* first */, true /* last */,
                           false /* aggregated */, fragment->buffer[0]));
  ++num_packets_left_;
  return true;
}

int RtpPacketizerH265::PacketizeAp(size_t fragment_index) {
  // Aggregate fragments into one packet (STAP-A).
  size_t payload_size_left = limits_.max_payload_len;
  if (input_fragments_.size() == 1)
    payload_size_left -= limits_.single_packet_reduction_len;
  else if (fragment_index == 0)
    payload_size_left -= limits_.first_packet_reduction_len;
  int aggregated_fragments = 0;
  size_t fragment_headers_length = 0;
  const Fragment* fragment = &input_fragments_[fragment_index];
  RTC_CHECK_GE(payload_size_left, fragment->length);
  ++num_packets_left_;

  auto payload_size_needed = [&] {
    size_t fragment_size = fragment->length + fragment_headers_length;
    if (input_fragments_.size() == 1) {
      // Single fragment, single packet, payload_size_left already adjusted
      // with limits_.single_packet_reduction_len.
      return fragment_size;
    }
    if (fragment_index == input_fragments_.size() - 1) {
      // Last fragment, so StrapA might be the last packet.
      return fragment_size + limits_.last_packet_reduction_len;
    }
    return fragment_size;
  };

  while (payload_size_left >= payload_size_needed()) {
    RTC_CHECK_GT(fragment->length, 0);
    packets_.push(PacketUnit(*fragment, aggregated_fragments == 0, false, true,
                             fragment->buffer[0]));
    payload_size_left -= fragment->length;
    payload_size_left -= fragment_headers_length;

    fragment_headers_length = kHevcLengthFieldSize;
    // If we are going to try to aggregate more fragments into this packet
    // we need to add the STAP-A NALU header and a length field for the first
    // NALU of this packet.
    if (aggregated_fragments == 0)
      fragment_headers_length += kHevcNalHeaderSize + kHevcLengthFieldSize;
    ++aggregated_fragments;

    // Next fragment.
    ++fragment_index;
    if (fragment_index == input_fragments_.size())
      break;
    fragment = &input_fragments_[fragment_index];
  }
  RTC_CHECK_GT(aggregated_fragments, 0);
  packets_.back().last_fragment = true;
  return fragment_index;
}

bool RtpPacketizerH265::NextPacket(RtpPacketToSend* rtp_packet) {
  RTC_DCHECK(rtp_packet);

  if (packets_.empty()) {
    return false;
  }

  PacketUnit packet = packets_.front();

  if (packet.first_fragment && packet.last_fragment) {
    // Single NAL unit packet.
    size_t bytes_to_send = packet.source_fragment.length;
    uint8_t* buffer = rtp_packet->AllocatePayload(bytes_to_send);
    memcpy(buffer, packet.source_fragment.buffer, bytes_to_send);
    packets_.pop();
    input_fragments_.pop_front();
  } else if (packet.aggregated) {
    bool is_last_packet = num_packets_left_ == 1;
    NextAggregatePacket(rtp_packet, is_last_packet);
  } else {
    NextFragmentPacket(rtp_packet);
  }
  rtp_packet->SetMarker(packets_.empty());
  --num_packets_left_;
  return true;
}

void RtpPacketizerH265::NextAggregatePacket(RtpPacketToSend* rtp_packet,
                                            bool last) {
  size_t payload_capacity = rtp_packet->FreeCapacity();
  RTC_CHECK_GE(payload_capacity, kHevcNalHeaderSize);
  uint8_t* buffer = rtp_packet->AllocatePayload(payload_capacity);

  PacketUnit* packet = &packets_.front();
  RTC_CHECK(packet->first_fragment);
  uint8_t payload_hdr_h = packet->header >> 8;
  uint8_t payload_hdr_l = packet->header & 0xFF;
  uint8_t layer_id_h = payload_hdr_h & kHevcLayerIDHMask;

  payload_hdr_h =
      (payload_hdr_h & kHevcTypeMaskN) | (kHevcAp << 1) | layer_id_h;

  buffer[0] = payload_hdr_h;
  buffer[1] = payload_hdr_l;
  int index = kHevcNalHeaderSize;
  bool is_last_fragment = packet->last_fragment;
  while (packet->aggregated) {
    // Add NAL unit length field.
    const Fragment& fragment = packet->source_fragment;
    ByteWriter<uint16_t>::WriteBigEndian(&buffer[index], fragment.length);
    index += kHevcLengthFieldSize;
    // Add NAL unit.
    memcpy(&buffer[index], fragment.buffer, fragment.length);
    index += fragment.length;
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

void RtpPacketizerH265::NextFragmentPacket(RtpPacketToSend* rtp_packet) {
  PacketUnit* packet = &packets_.front();
  // NAL unit fragmented over multiple packets (FU).
  // We do not send original NALU header, so it will be replaced by the
  // PayloadHdr of the first packet.
  uint8_t payload_hdr_h =
      packet->header >> 8;  // 1-bit F, 6-bit type, 1-bit layerID highest-bit
  uint8_t payload_hdr_l = packet->header & 0xFF;
  uint8_t layer_id_h = payload_hdr_h & kHevcLayerIDHMask;
  uint8_t fu_header = 0;
  // S | E |6 bit type.
  fu_header |= (packet->first_fragment ? kHevcSBit : 0);
  fu_header |= (packet->last_fragment ? kHevcEBit : 0);
  uint8_t type = (payload_hdr_h & kHevcTypeMask) >> 1;
  fu_header |= type;
  // Now update payload_hdr_h with FU type.
  payload_hdr_h =
      (payload_hdr_h & kHevcTypeMaskN) | (kHevcFu << 1) | layer_id_h;
  const Fragment& fragment = packet->source_fragment;
  uint8_t* buffer = rtp_packet->AllocatePayload(
      kHevcFuHeaderSize + kHevcNalHeaderSize + fragment.length);
  buffer[0] = payload_hdr_h;
  buffer[1] = payload_hdr_l;
  buffer[2] = fu_header;

  if (packet->last_fragment) {
    memcpy(buffer + kHevcFuHeaderSize + kHevcNalHeaderSize, fragment.buffer,
           fragment.length);
  } else {
    memcpy(buffer + kHevcFuHeaderSize + kHevcNalHeaderSize, fragment.buffer,
           fragment.length);
  }
  packets_.pop();
}

}  // namespace webrtc
