/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/flexfec_header_reader_writer.h"

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
constexpr size_t kBaseHeaderSize = 8;

// Size (in bytes) of part of header which is stream specific.
constexpr size_t kStreamSpecificHeaderSize = 2;

// Size (in bytes) of header, given the single stream packet mask size, i.e.
// the number of K-bits set.
constexpr size_t kHeaderSizes[] = {
    kBaseHeaderSize + kStreamSpecificHeaderSize + kFlexfecPacketMaskSizes[0],
    kBaseHeaderSize + kStreamSpecificHeaderSize + kFlexfecPacketMaskSizes[1],
    kBaseHeaderSize + kStreamSpecificHeaderSize + kFlexfecPacketMaskSizes[2]};

// Here we count the K-bits as belonging to the packet mask.
// This can be used in conjunction with FlexfecHeaderWriter::MinPacketMaskSize,
// which calculates a bound on the needed packet mask size including K-bits,
// given a packet mask without K-bits.
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

FlexfecHeaderReader::FlexfecHeaderReader()
    : FecHeaderReader(kMaxTrackedMediaPackets, kMaxFecPackets) {}

FlexfecHeaderReader::~FlexfecHeaderReader() = default;

// TODO(brandtr): Update this function when we support flexible masks,
// and retransmissions.
bool FlexfecHeaderReader::ReadFecHeader(
    ForwardErrorCorrection::ReceivedFecPacket* fec_packet) const {
  // Protected ssrcs should already be populated from RTP header.
  if (fec_packet->protected_streams.empty()) {
    RTC_LOG(LS_WARNING)
        << "Discarding FlexFEC packet with no protected sources.";
    return false;
  }
  if (fec_packet->pkt->data.size() <=
      kBaseHeaderSize + kStreamSpecificHeaderSize) {
    RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC packet.";
    return false;
  }
  uint8_t* const data = fec_packet->pkt->data.MutableData();
  bool r_bit = (data[0] & 0x80) != 0;
  if (r_bit) {
    RTC_LOG(LS_INFO)
        << "FlexFEC packet with retransmission bit set. We do not yet "
           "support this, thus discarding the packet.";
    return false;
  }
  bool f_bit = (data[0] & 0x40) != 0;
  if (f_bit) {
    RTC_LOG(LS_INFO)
        << "FlexFEC packet with inflexible generator matrix. We do "
           "not yet support this, thus discarding packet.";
    return false;
  }

  // First seq_num will be in byte index 8
  // (See FEC header schematic in flexfec_header_reader_writer.h.)
  size_t byte_index = 8;
  for (size_t i = 0; i < fec_packet->protected_streams.size(); ++i) {
    if (fec_packet->pkt->data.size() < byte_index + kStreamSpecificHeaderSize) {
      RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC packet.";
      return false;
    }

    fec_packet->protected_streams[i].seq_num_base =
        ByteReader<uint16_t>::ReadBigEndian(&data[byte_index]);
    byte_index += kStreamSpecificHeaderSize;

    // Parse the FlexFEC packet mask and remove the interleaved K-bits.
    // (See FEC header schematic in flexfec_header_reader_writer.h.)
    // We store the packed packet mask in-band, which "destroys" the standards
    // compliance of the header. That is fine though, since the code that
    // reads from the header (from this point and onwards) is aware of this.
    // TODO(brandtr): When the FEC packet classes have been refactored, store
    // the packed packet masks out-of-band, thus leaving the FlexFEC header as
    // is.
    //
    // We treat the mask parts as unsigned integers with host order endianness
    // in order to simplify the bit shifting between bytes.
    if (fec_packet->pkt->data.size() <
        (byte_index + kFlexfecPacketMaskSizes[0])) {
      RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC packet.";
      return false;
    }
    fec_packet->protected_streams[i].packet_mask_offset = byte_index;
    bool k_bit0 = (data[byte_index] & 0x80) != 0;
    uint16_t mask_part0 =
        ByteReader<uint16_t>::ReadBigEndian(&data[byte_index]);
    // Shift away K-bit 0, implicitly clearing the last bit.
    mask_part0 <<= 1;
    ByteWriter<uint16_t>::WriteBigEndian(&data[byte_index], mask_part0);
    byte_index += kFlexfecPacketMaskSizes[0];
    if (!k_bit0) {
      // The first K-bit is clear, and the packet mask is thus only 2 bytes
      // long. We have finished reading the properties for current ssrc.
      fec_packet->protected_streams[i].packet_mask_size =
          kFlexfecPacketMaskSizes[0];
    } else {
      if (fec_packet->pkt->data.size() <
          (byte_index + kFlexfecPacketMaskSizes[1] -
           kFlexfecPacketMaskSizes[0])) {
        return false;
      }
      bool k_bit1 = (data[byte_index] & 0x80) != 0;
      // We have already shifted the first two bytes of the packet mask one step
      // to the left, thus removing K-bit 0. We will now shift the next four
      // bytes of the packet mask two steps to the left. (One step for the
      // removed K-bit 0, and one step for the to be removed K-bit 1).
      uint8_t bit15 = (data[byte_index] >> 6) & 0x01;
      data[byte_index - 1] |= bit15;
      uint32_t mask_part1 =
          ByteReader<uint32_t>::ReadBigEndian(&data[byte_index]);
      // Shift away K-bit 1 and bit 15, implicitly clearing the last two bits.
      mask_part1 <<= 2;
      ByteWriter<uint32_t>::WriteBigEndian(&data[byte_index], mask_part1);
      byte_index += kFlexfecPacketMaskSizes[1] - kFlexfecPacketMaskSizes[0];
      if (!k_bit1) {
        // The first K-bit is set, but the second K-bit is clear. The packet
        // mask is thus 6 bytes long. We have finished reading the properties
        // for current ssrc.
        fec_packet->protected_streams[i].packet_mask_size =
            kFlexfecPacketMaskSizes[1];
      } else {
        if (fec_packet->pkt->data.size() <
            (byte_index + kFlexfecPacketMaskSizes[2] -
             kFlexfecPacketMaskSizes[1])) {
          RTC_LOG(LS_WARNING) << "Discarding truncated FlexFEC packet.";
          return false;
        }
        fec_packet->protected_streams[i].packet_mask_size =
            kFlexfecPacketMaskSizes[2];
        // At this point, K-bits 0 and 1 have been removed, and the front-most
        // part of the FlexFEC packet mask has been packed accordingly. We will
        // now shift the remaining part of the packet mask two steps to
        // the left. This corresponds to the (in total) two K-bits, which
        // have been removed.
        uint8_t tail_bits = (data[byte_index] >> 6) & 0x03;
        data[byte_index - 1] |= tail_bits;
        uint64_t mask_part2 =
            ByteReader<uint64_t>::ReadBigEndian(&data[byte_index]);
        // Shift away bit 46, and bit 47, which were copied to the previous
        // part of the mask, implicitly clearing the last two bits.
        mask_part2 <<= 2;
        ByteWriter<uint64_t>::WriteBigEndian(&data[byte_index], mask_part2);
        byte_index += kFlexfecPacketMaskSizes[2] - kFlexfecPacketMaskSizes[1];
      }
    }
  }

  fec_packet->fec_header_size = byte_index;

  // In FlexFEC, all media packets are protected in their entirety.
  fec_packet->protection_length =
      fec_packet->pkt->data.size() - fec_packet->fec_header_size;

  return true;
}

FlexfecHeaderWriter::FlexfecHeaderWriter()
    : FecHeaderWriter(kMaxMediaPackets, kMaxFecPackets, kHeaderSizes[2]) {}

FlexfecHeaderWriter::~FlexfecHeaderWriter() = default;

size_t FlexfecHeaderWriter::MinPacketMaskSize(const uint8_t* packet_mask,
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

size_t FlexfecHeaderWriter::FecHeaderSize(size_t packet_mask_size) const {
  return FlexfecHeaderSize(packet_mask_size);
}

// This function adapts the precomputed ULPFEC packet masks to the
// FlexFEC header standard. Note that the header size is computed by
// FecHeaderSize(), so in this function we can be sure that we are
// writing in space that is intended for the header.
//
// TODO(brandtr): Update this function when we support offset-based masks
// and retransmissions.
void FlexfecHeaderWriter::FinalizeFecHeader(
    rtc::ArrayView<const ProtectedStream> protected_streams,
    ForwardErrorCorrection::Packet& fec_packet) const {
  uint8_t* data = fec_packet.data.MutableData();
  *data &= 0x7f;  // Clear R bit.
  *data &= 0xbf;  // Clear F bit.

  // First seq_num will be in byte index 8
  // (See FEC header schematic in flexfec_header_reader_writer.h.)
  uint8_t* write_at = data + 8;
  for (const ProtectedStream& protected_stream : protected_streams) {
    ByteWriter<uint16_t>::WriteBigEndian(write_at,
                                         protected_stream.seq_num_base);
    write_at += kStreamSpecificHeaderSize;
    // Adapt ULPFEC packet mask to FlexFEC header.
    //
    // We treat the mask parts as unsigned integers with host order endianness
    // in order to simplify the bit shifting between bytes.
    if (protected_stream.packet_mask.size() == kUlpfecPacketMaskSizeLBitSet) {
      // The packet mask is 48 bits long.
      uint16_t tmp_mask_part0 =
          ByteReader<uint16_t>::ReadBigEndian(&protected_stream.packet_mask[0]);
      uint32_t tmp_mask_part1 =
          ByteReader<uint32_t>::ReadBigEndian(&protected_stream.packet_mask[2]);

      tmp_mask_part0 >>= 1;  // Shift, thus clearing K-bit 0.
      ByteWriter<uint16_t>::WriteBigEndian(write_at, tmp_mask_part0);
      *write_at |= 0x80;  // Set K-bit 0.
      write_at += kFlexfecPacketMaskSizes[0];
      tmp_mask_part1 >>= 2;  // Shift twice, thus clearing K-bit 1 and bit 15.
      ByteWriter<uint32_t>::WriteBigEndian(write_at, tmp_mask_part1);

      bool bit15 = (protected_stream.packet_mask[1] & 0x01) != 0;
      if (bit15)
        *write_at |= 0x40;  // Set bit 15.

      bool bit46 = (protected_stream.packet_mask[5] & 0x02) != 0;
      bool bit47 = (protected_stream.packet_mask[5] & 0x01) != 0;
      if (!bit46 && !bit47) {
        write_at += kFlexfecPacketMaskSizes[1] - kFlexfecPacketMaskSizes[0];
      } else {
        *write_at |= 0x80;  // Set K-bit 1.
        write_at += kFlexfecPacketMaskSizes[1] - kFlexfecPacketMaskSizes[0];
        // Clear all trailing bits.
        memset(write_at, 0,
               kFlexfecPacketMaskSizes[2] - kFlexfecPacketMaskSizes[1]);
        if (bit46)
          *write_at |= 0x80;  // Set bit 46.
        if (bit47)
          *write_at |= 0x40;  // Set bit 47.
        write_at += kFlexfecPacketMaskSizes[2] - kFlexfecPacketMaskSizes[1];
      }
    } else if (protected_stream.packet_mask.size() ==
               kUlpfecPacketMaskSizeLBitClear) {
      // The packet mask is 16 bits long.
      uint16_t tmp_mask_part0 =
          ByteReader<uint16_t>::ReadBigEndian(&protected_stream.packet_mask[0]);

      tmp_mask_part0 >>= 1;  // Shift, thus clearing K-bit 0.
      ByteWriter<uint16_t>::WriteBigEndian(write_at, tmp_mask_part0);
      bool bit15 = (protected_stream.packet_mask[1] & 0x01) != 0;
      if (!bit15) {
        write_at += kFlexfecPacketMaskSizes[0];
      } else {
        *write_at |= 0x80;  // Set K-bit 0.
        write_at += kFlexfecPacketMaskSizes[0];
        // Clear all trailing bits.
        memset(write_at, 0U,
               kFlexfecPacketMaskSizes[1] - kFlexfecPacketMaskSizes[0]);
        *write_at |= 0x40;  // Set bit 15.
        write_at += kFlexfecPacketMaskSizes[1] - kFlexfecPacketMaskSizes[0];
      }
    } else {
      RTC_DCHECK_NOTREACHED() << "Incorrect packet mask size: "
                              << protected_stream.packet_mask.size() << ".";
    }
  }
}

}  // namespace webrtc
