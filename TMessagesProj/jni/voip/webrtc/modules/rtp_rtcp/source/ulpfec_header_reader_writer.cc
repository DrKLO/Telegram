/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/ulpfec_header_reader_writer.h"

#include <string.h>

#include "api/scoped_refptr.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/forward_error_correction_internal.h"
#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// Maximum number of media packets that can be protected in one batch.
constexpr size_t kMaxMediaPackets = 48;

// Maximum number of media packets tracked by FEC decoder.
// Maintain a sufficiently larger tracking window than `kMaxMediaPackets`
// to account for packet reordering in pacer/ network.
constexpr size_t kMaxTrackedMediaPackets = 4 * kMaxMediaPackets;

// Maximum number of FEC packets stored inside ForwardErrorCorrection.
constexpr size_t kMaxFecPackets = kMaxMediaPackets;

// FEC Level 0 header size in bytes.
constexpr size_t kFecLevel0HeaderSize = 10;

// FEC Level 1 (ULP) header size in bytes (L bit is set).
constexpr size_t kFecLevel1HeaderSizeLBitSet = 2 + kUlpfecPacketMaskSizeLBitSet;

// FEC Level 1 (ULP) header size in bytes (L bit is cleared).
constexpr size_t kFecLevel1HeaderSizeLBitClear =
    2 + kUlpfecPacketMaskSizeLBitClear;

constexpr size_t kPacketMaskOffset = kFecLevel0HeaderSize + 2;

size_t UlpfecHeaderSize(size_t packet_mask_size) {
  RTC_DCHECK_LE(packet_mask_size, kUlpfecPacketMaskSizeLBitSet);
  if (packet_mask_size <= kUlpfecPacketMaskSizeLBitClear) {
    return kFecLevel0HeaderSize + kFecLevel1HeaderSizeLBitClear;
  } else {
    return kFecLevel0HeaderSize + kFecLevel1HeaderSizeLBitSet;
  }
}

}  // namespace

UlpfecHeaderReader::UlpfecHeaderReader()
    : FecHeaderReader(kMaxTrackedMediaPackets, kMaxFecPackets) {}

UlpfecHeaderReader::~UlpfecHeaderReader() = default;

bool UlpfecHeaderReader::ReadFecHeader(
    ForwardErrorCorrection::ReceivedFecPacket* fec_packet) const {
  uint8_t* data = fec_packet->pkt->data.MutableData();
  if (fec_packet->pkt->data.size() < kPacketMaskOffset) {
    return false;  // Truncated packet.
  }
  bool l_bit = (data[0] & 0x40) != 0u;
  size_t packet_mask_size =
      l_bit ? kUlpfecPacketMaskSizeLBitSet : kUlpfecPacketMaskSizeLBitClear;
  fec_packet->fec_header_size = UlpfecHeaderSize(packet_mask_size);
  uint16_t seq_num_base = ByteReader<uint16_t>::ReadBigEndian(&data[2]);
  fec_packet->protected_streams = {{.ssrc = fec_packet->ssrc,  // Due to RED.
                                    .seq_num_base = seq_num_base,
                                    .packet_mask_offset = kPacketMaskOffset,
                                    .packet_mask_size = packet_mask_size}};
  fec_packet->protection_length =
      ByteReader<uint16_t>::ReadBigEndian(&data[10]);

  // Store length recovery field in temporary location in header.
  // This makes the header "compatible" with the corresponding
  // FlexFEC location of the length recovery field, thus simplifying
  // the XORing operations.
  memcpy(&data[2], &data[8], 2);

  return true;
}

UlpfecHeaderWriter::UlpfecHeaderWriter()
    : FecHeaderWriter(kMaxMediaPackets,
                      kMaxFecPackets,
                      kFecLevel0HeaderSize + kFecLevel1HeaderSizeLBitSet) {}

UlpfecHeaderWriter::~UlpfecHeaderWriter() = default;

// TODO(brandtr): Consider updating this implementation (which actually
// returns a bound on the sequence number spread), if logic is added to
// UlpfecHeaderWriter::FinalizeFecHeader to truncate packet masks which end
// in a string of zeroes. (Similar to how it is done in the FlexFEC case.)
size_t UlpfecHeaderWriter::MinPacketMaskSize(const uint8_t* packet_mask,
                                             size_t packet_mask_size) const {
  return packet_mask_size;
}

size_t UlpfecHeaderWriter::FecHeaderSize(size_t packet_mask_size) const {
  return UlpfecHeaderSize(packet_mask_size);
}

void UlpfecHeaderWriter::FinalizeFecHeader(
    rtc::ArrayView<const ProtectedStream> protected_streams,
    ForwardErrorCorrection::Packet& fec_packet) const {
  RTC_CHECK_EQ(protected_streams.size(), 1);
  uint16_t seq_num_base = protected_streams[0].seq_num_base;
  const uint8_t* packet_mask = protected_streams[0].packet_mask.data();
  size_t packet_mask_size = protected_streams[0].packet_mask.size();

  uint8_t* data = fec_packet.data.MutableData();
  // Set E bit to zero.
  data[0] &= 0x7f;
  // Set L bit based on packet mask size. (Note that the packet mask
  // can only take on two discrete values.)
  bool l_bit = (packet_mask_size == kUlpfecPacketMaskSizeLBitSet);
  if (l_bit) {
    data[0] |= 0x40;  // Set the L bit.
  } else {
    RTC_DCHECK_EQ(packet_mask_size, kUlpfecPacketMaskSizeLBitClear);
    data[0] &= 0xbf;  // Clear the L bit.
  }
  // Copy length recovery field from temporary location.
  memcpy(&data[8], &data[2], 2);
  // Write sequence number base.
  ByteWriter<uint16_t>::WriteBigEndian(&data[2], seq_num_base);
  // Protection length is set to entire packet. (This is not
  // required in general.)
  const size_t fec_header_size = FecHeaderSize(packet_mask_size);
  ByteWriter<uint16_t>::WriteBigEndian(
      &data[10], fec_packet.data.size() - fec_header_size);
  // Copy the packet mask.
  memcpy(&data[12], packet_mask, packet_mask_size);
}

}  // namespace webrtc
