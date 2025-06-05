/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/flexfec_03_header_reader_writer.h"

#include <string.h>

#include "api/scoped_refptr.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/forward_error_correction_internal.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

// Maximum number of media packets that can be protected in one batch.
constexpr size_t kMaxMediaPackets = 48;  // Since we are reusing ULPFEC masks.

// Maximum number of media packets tracked by FEC decoder.
// Maintain a sufficiently larger tracking window than `kMaxMediaPackets`
// to account for packet reordering in pacer/ network.
constexpr size_t kMaxTrackedMediaPackets = 4 * kMaxMediaPackets;

// Maximum number of FEC packets stored inside ForwardErrorCorrection.
constexpr size_t kMaxFecPackets = kMaxMediaPackets;

// Size (in bytes) of packet masks, given number of K bits set.
constexpr size_t kFlexfecPacketMaskSizes[] = {2, 6, 14};

// Size (in bytes) of part of header which is not packet mask specific.
constexpr size_t kBaseHeaderSize = 12;

// Size (in bytes) of part of header which is stream specific.
constexpr size_t kStreamSpecificHeaderSize = 6;

// Size (in bytes) of header, given the single stream packet mask size, i.e.
// the number of K-bits set.
constexpr size_t kHeaderSizes[] = {
    kBaseHeaderSize + kStreamSpecificHeaderSize + kFlexfecPacketMaskSizes[0],
    kBaseHeaderSize + kStreamSpecificHeaderSize + kFlexfecPacketMaskSizes[1],
    kBaseHeaderSize + kStreamSpecificHeaderSize + kFlexfecPacketMaskSizes[2]};

// Flexfec03 implementation only support single-stream protection.
// For multi-stream protection use implementation of the FlexFEC final standard.
constexpr uint8_t kSsrcCount = 1;

// There are three reserved bytes that MUST be set to zero in the header.
constexpr uint32_t kReservedBits = 0;

constexpr size_t kPacketMaskOffset =
    kBaseHeaderSize + kStreamSpecificHeaderSize;

// Here we count the K-bits as belonging to the packet mask.
// This can be used in conjunction with
// Flexfec03HeaderWriter::MinPacketMaskSize, which calculates a bound on the
// needed packet mask size including K-bits, given a packet mask without K-bits.
size_t FlexfecHeaderSize(size_t packet_mask_size) {
  RTC_DCHECK_LE(packet_mask_size, kFlexfecPacketMaskSizes[2]);
  if (packet_mask_size <= kFlexfecPacketMaskSizes[0]) {
    return kHeaderSizes[0];
  } else if (packet_mask_size <= kFlexfecPacketMaskSizes[1]) {
    return kHeaderSizes[1];
  }
  return kHeaderSizes[2];
}

}  // namespace

Flexfec03HeaderReader::Flexfec03HeaderReader()
    : FecHeaderReader(kMaxTrackedMediaPackets, kMaxFecPackets) {}

Flexfec03HeaderReader::~Flexfec03HeaderReader() = default;

// Flexfec03 implementation doesn't support retransmission and flexible masks.
// When that features are desired, they should be implemented in the class that
// follows final version of the FlexFEC standard.
bool Flexfec03HeaderReader::ReadFecHeader(
    ForwardErrorCorrection::ReceivedFecPacket* fec_packet) const {
  if (fec_packet->pkt->data.size() <=
      kBaseHeaderSize + kStreamSpecificHeaderSize) {
    RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC packet.";
    return false;
  }
  uint8_t* const data = fec_packet->pkt->data.MutableData();
  bool r_bit = (data[0] & 0x80) != 0;
  if (r_bit) {
    RTC_LOG(LS_INFO)
        << "FlexFEC03 packet with retransmission bit set. We do not "
           "support this, thus discarding the packet.";
    return false;
  }
  bool f_bit = (data[0] & 0x40) != 0;
  if (f_bit) {
    RTC_LOG(LS_INFO)
        << "FlexFEC03 packet with inflexible generator matrix. We do "
           "not support this, thus discarding packet.";
    return false;
  }
  uint8_t ssrc_count = ByteReader<uint8_t>::ReadBigEndian(&data[8]);
  if (ssrc_count != 1) {
    RTC_LOG(LS_INFO)
        << "FlexFEC03 packet protecting multiple media SSRCs. We do not "
           "support this, thus discarding packet.";
    return false;
  }
  uint32_t protected_ssrc = ByteReader<uint32_t>::ReadBigEndian(&data[12]);
  uint16_t seq_num_base = ByteReader<uint16_t>::ReadBigEndian(&data[16]);

  // Parse the FlexFEC packet mask and remove the interleaved K-bits.
  // (See FEC header schematic in flexfec_header_reader_writer.h.)
  // We store the packed packet mask in-band, which "destroys" the standards
  // compliance of the header. That is fine though, since the code that
  // reads from the header (from this point and onwards) is aware of this.
  // TODO(brandtr): When the FEC packet classes have been refactored, store
  // the packed packet masks out-of-band, thus leaving the FlexFEC header as is.
  //
  // We treat the mask parts as unsigned integers with host order endianness
  // in order to simplify the bit shifting between bytes.
  if (fec_packet->pkt->data.size() < kHeaderSizes[0]) {
    RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC03 packet.";
    return false;
  }
  uint8_t* const packet_mask = data + kPacketMaskOffset;
  bool k_bit0 = (packet_mask[0] & 0x80) != 0;
  uint16_t mask_part0 = ByteReader<uint16_t>::ReadBigEndian(&packet_mask[0]);
  // Shift away K-bit 0, implicitly clearing the last bit.
  mask_part0 <<= 1;
  ByteWriter<uint16_t>::WriteBigEndian(&packet_mask[0], mask_part0);
  size_t packet_mask_size;
  if (k_bit0) {
    // The first K-bit is set, and the packet mask is thus only 2 bytes long.
    // We have now read the entire FEC header, and the rest of the packet
    // is payload.
    packet_mask_size = kFlexfecPacketMaskSizes[0];
  } else {
    if (fec_packet->pkt->data.size() < kHeaderSizes[1]) {
      return false;
    }
    bool k_bit1 = (packet_mask[2] & 0x80) != 0;
    // We have already shifted the first two bytes of the packet mask one step
    // to the left, thus removing K-bit 0. We will now shift the next four bytes
    // of the packet mask two steps to the left. (One step for the removed
    // K-bit 0, and one step for the to be removed K-bit 1).
    uint8_t bit15 = (packet_mask[2] >> 6) & 0x01;
    packet_mask[1] |= bit15;
    uint32_t mask_part1 = ByteReader<uint32_t>::ReadBigEndian(&packet_mask[2]);
    // Shift away K-bit 1 and bit 15, implicitly clearing the last two bits.
    mask_part1 <<= 2;
    ByteWriter<uint32_t>::WriteBigEndian(&packet_mask[2], mask_part1);
    if (k_bit1) {
      // The first K-bit is clear, but the second K-bit is set. The packet
      // mask is thus 6 bytes long.  We have now read the entire FEC header,
      // and the rest of the packet is payload.
      packet_mask_size = kFlexfecPacketMaskSizes[1];
    } else {
      if (fec_packet->pkt->data.size() < kHeaderSizes[2]) {
        RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC03 packet.";
        return false;
      }
      bool k_bit2 = (packet_mask[6] & 0x80) != 0;
      if (k_bit2) {
        // The first and second K-bits are clear, but the third K-bit is set.
        // The packet mask is thus 14 bytes long. We have now read the entire
        // FEC header, and the rest of the packet is payload.
        packet_mask_size = kFlexfecPacketMaskSizes[2];
      } else {
        RTC_LOG(LS_WARNING)
            << "Discarding FlexFEC03 packet with malformed header.";
        return false;
      }
      // At this point, K-bits 0 and 1 have been removed, and the front-most
      // part of the FlexFEC packet mask has been packed accordingly. We will
      // now shift the remaining part of the packet mask three steps to
      // the left. This corresponds to the (in total) three K-bits, which
      // have been removed.
      uint8_t tail_bits = (packet_mask[6] >> 5) & 0x03;
      packet_mask[5] |= tail_bits;
      uint64_t mask_part2 =
          ByteReader<uint64_t>::ReadBigEndian(&packet_mask[6]);
      // Shift away K-bit 2, bit 46, and bit 47, implicitly clearing the last
      // three bits.
      mask_part2 <<= 3;
      ByteWriter<uint64_t>::WriteBigEndian(&packet_mask[6], mask_part2);
    }
  }

  // Store "ULPFECized" packet mask info.
  fec_packet->fec_header_size = FlexfecHeaderSize(packet_mask_size);
  fec_packet->protected_streams = {{.ssrc = protected_ssrc,
                                    .seq_num_base = seq_num_base,
                                    .packet_mask_offset = kPacketMaskOffset,
                                    .packet_mask_size = packet_mask_size}};
  // In FlexFEC, all media packets are protected in their entirety.
  fec_packet->protection_length =
      fec_packet->pkt->data.size() - fec_packet->fec_header_size;

  return true;
}

Flexfec03HeaderWriter::Flexfec03HeaderWriter()
    : FecHeaderWriter(kMaxMediaPackets, kMaxFecPackets, kHeaderSizes[2]) {}

Flexfec03HeaderWriter::~Flexfec03HeaderWriter() = default;

size_t Flexfec03HeaderWriter::MinPacketMaskSize(const uint8_t* packet_mask,
                                                size_t packet_mask_size) const {
  if (packet_mask_size == kUlpfecPacketMaskSizeLBitClear &&
      (packet_mask[1] & 0x01) == 0) {
    // Packet mask is 16 bits long, with bit 15 clear.
    // It can be used as is.
    return kFlexfecPacketMaskSizes[0];
  } else if (packet_mask_size == kUlpfecPacketMaskSizeLBitClear) {
    // Packet mask is 16 bits long, with bit 15 set.
    // We must expand the packet mask with zeros in the FlexFEC header.
    return kFlexfecPacketMaskSizes[1];
  } else if (packet_mask_size == kUlpfecPacketMaskSizeLBitSet &&
             (packet_mask[5] & 0x03) == 0) {
    // Packet mask is 48 bits long, with bits 46 and 47 clear.
    // It can be used as is.
    return kFlexfecPacketMaskSizes[1];
  } else if (packet_mask_size == kUlpfecPacketMaskSizeLBitSet) {
    // Packet mask is 48 bits long, with at least one of bits 46 and 47 set.
    // We must expand it with zeros.
    return kFlexfecPacketMaskSizes[2];
  }
  RTC_DCHECK_NOTREACHED() << "Incorrect packet mask size: " << packet_mask_size
                          << ".";
  return kFlexfecPacketMaskSizes[2];
}

size_t Flexfec03HeaderWriter::FecHeaderSize(size_t packet_mask_size) const {
  return FlexfecHeaderSize(packet_mask_size);
}

// This function adapts the precomputed ULPFEC packet masks to the
// FlexFEC header standard. Note that the header size is computed by
// FecHeaderSize(), so in this function we can be sure that we are
// writing in space that is intended for the header.
void Flexfec03HeaderWriter::FinalizeFecHeader(
    rtc::ArrayView<const ProtectedStream> protected_streams,
    ForwardErrorCorrection::Packet& fec_packet) const {
  RTC_CHECK_EQ(protected_streams.size(), 1);
  uint32_t media_ssrc = protected_streams[0].ssrc;
  uint16_t seq_num_base = protected_streams[0].seq_num_base;
  const uint8_t* packet_mask = protected_streams[0].packet_mask.data();
  size_t packet_mask_size = protected_streams[0].packet_mask.size();

  uint8_t* data = fec_packet.data.MutableData();
  data[0] &= 0x7f;  // Clear R bit.
  data[0] &= 0xbf;  // Clear F bit.
  ByteWriter<uint8_t>::WriteBigEndian(&data[8], kSsrcCount);
  ByteWriter<uint32_t, 3>::WriteBigEndian(&data[9], kReservedBits);
  ByteWriter<uint32_t>::WriteBigEndian(&data[12], media_ssrc);
  ByteWriter<uint16_t>::WriteBigEndian(&data[16], seq_num_base);
  // Adapt ULPFEC packet mask to FlexFEC header.
  //
  // We treat the mask parts as unsigned integers with host order endianness
  // in order to simplify the bit shifting between bytes.
  uint8_t* const written_packet_mask = data + kPacketMaskOffset;
  if (packet_mask_size == kUlpfecPacketMaskSizeLBitSet) {
    // The packet mask is 48 bits long.
    uint16_t tmp_mask_part0 =
        ByteReader<uint16_t>::ReadBigEndian(&packet_mask[0]);
    uint32_t tmp_mask_part1 =
        ByteReader<uint32_t>::ReadBigEndian(&packet_mask[2]);

    tmp_mask_part0 >>= 1;  // Shift, thus clearing K-bit 0.
    ByteWriter<uint16_t>::WriteBigEndian(&written_packet_mask[0],
                                         tmp_mask_part0);
    tmp_mask_part1 >>= 2;  // Shift, thus clearing K-bit 1 and bit 15.
    ByteWriter<uint32_t>::WriteBigEndian(&written_packet_mask[2],
                                         tmp_mask_part1);
    bool bit15 = (packet_mask[1] & 0x01) != 0;
    if (bit15)
      written_packet_mask[2] |= 0x40;  // Set bit 15.
    bool bit46 = (packet_mask[5] & 0x02) != 0;
    bool bit47 = (packet_mask[5] & 0x01) != 0;
    if (!bit46 && !bit47) {
      written_packet_mask[2] |= 0x80;  // Set K-bit 1.
    } else {
      memset(&written_packet_mask[6], 0, 8);  // Clear all trailing bits.
      written_packet_mask[6] |= 0x80;         // Set K-bit 2.
      if (bit46)
        written_packet_mask[6] |= 0x40;  // Set bit 46.
      if (bit47)
        written_packet_mask[6] |= 0x20;  // Set bit 47.
    }
  } else if (packet_mask_size == kUlpfecPacketMaskSizeLBitClear) {
    // The packet mask is 16 bits long.
    uint16_t tmp_mask_part0 =
        ByteReader<uint16_t>::ReadBigEndian(&packet_mask[0]);

    tmp_mask_part0 >>= 1;  // Shift, thus clearing K-bit 0.
    ByteWriter<uint16_t>::WriteBigEndian(&written_packet_mask[0],
                                         tmp_mask_part0);
    bool bit15 = (packet_mask[1] & 0x01) != 0;
    if (!bit15) {
      written_packet_mask[0] |= 0x80;  // Set K-bit 0.
    } else {
      memset(&written_packet_mask[2], 0U, 4);  // Clear all trailing bits.
      written_packet_mask[2] |= 0x80;          // Set K-bit 1.
      written_packet_mask[2] |= 0x40;          // Set bit 15.
    }
  } else {
    RTC_DCHECK_NOTREACHED()
        << "Incorrect packet mask size: " << packet_mask_size << ".";
  }
}

}  // namespace webrtc
