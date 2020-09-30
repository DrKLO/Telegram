/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_packet/sdes.h"

#include <string.h>

#include <utility>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
constexpr uint8_t Sdes::kPacketType;
constexpr size_t Sdes::kMaxNumberOfChunks;
// Source Description (SDES) (RFC 3550).
//
//         0                   1                   2                   3
//         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// header |V=2|P|    SC   |  PT=SDES=202  |             length            |
//        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
// chunk  |                          SSRC/CSRC_1                          |
//   1    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |                           SDES items                          |
//        |                              ...                              |
//        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
// chunk  |                          SSRC/CSRC_2                          |
//   2    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |                           SDES items                          |
//        |                              ...                              |
//        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
//
// Canonical End-Point Identifier SDES Item (CNAME)
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |    CNAME=1    |     length    | user and domain name        ...
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
namespace {
const uint8_t kTerminatorTag = 0;
const uint8_t kCnameTag = 1;

size_t ChunkSize(const Sdes::Chunk& chunk) {
  // Chunk:
  // SSRC/CSRC (4 bytes) | CNAME=1 (1 byte) | length (1 byte) | cname | padding.
  size_t chunk_payload_size = 4 + 1 + 1 + chunk.cname.size();
  size_t padding_size = 4 - (chunk_payload_size % 4);  // Minimum 1.
  return chunk_payload_size + padding_size;
}
}  // namespace

Sdes::Sdes() : block_length_(RtcpPacket::kHeaderLength) {}

Sdes::~Sdes() {}

bool Sdes::Parse(const CommonHeader& packet) {
  RTC_DCHECK_EQ(packet.type(), kPacketType);

  uint8_t number_of_chunks = packet.count();
  std::vector<Chunk> chunks;  // Read chunk into temporary array, so that in
                              // case of an error original array would stay
                              // unchanged.
  size_t block_length = kHeaderLength;

  if (packet.payload_size_bytes() % 4 != 0) {
    RTC_LOG(LS_WARNING) << "Invalid payload size "
                        << packet.payload_size_bytes()
                        << " bytes for a valid Sdes packet. Size should be"
                           " multiple of 4 bytes";
  }
  const uint8_t* const payload_end =
      packet.payload() + packet.payload_size_bytes();
  const uint8_t* looking_at = packet.payload();
  chunks.resize(number_of_chunks);
  for (size_t i = 0; i < number_of_chunks;) {
    // Each chunk consumes at least 8 bytes.
    if (payload_end - looking_at < 8) {
      RTC_LOG(LS_WARNING) << "Not enough space left for chunk #" << (i + 1);
      return false;
    }
    chunks[i].ssrc = ByteReader<uint32_t>::ReadBigEndian(looking_at);
    looking_at += sizeof(uint32_t);
    bool cname_found = false;

    uint8_t item_type;
    while ((item_type = *(looking_at++)) != kTerminatorTag) {
      if (looking_at >= payload_end) {
        RTC_LOG(LS_WARNING)
            << "Unexpected end of packet while reading chunk #" << (i + 1)
            << ". Expected to find size of the text.";
        return false;
      }
      uint8_t item_length = *(looking_at++);
      const size_t kTerminatorSize = 1;
      if (looking_at + item_length + kTerminatorSize > payload_end) {
        RTC_LOG(LS_WARNING)
            << "Unexpected end of packet while reading chunk #" << (i + 1)
            << ". Expected to find text of size " << item_length;
        return false;
      }
      if (item_type == kCnameTag) {
        if (cname_found) {
          RTC_LOG(LS_WARNING)
              << "Found extra CNAME for same ssrc in chunk #" << (i + 1);
          return false;
        }
        cname_found = true;
        chunks[i].cname.assign(reinterpret_cast<const char*>(looking_at),
                               item_length);
      }
      looking_at += item_length;
    }
    if (cname_found) {
      // block_length calculates length of the packet that would be generated by
      // Build/Create functions. Adjust it same way WithCName function does.
      block_length += ChunkSize(chunks[i]);
      ++i;
    } else {
      // RFC states CNAME item is mandatory.
      // But same time it allows chunk without items.
      // So while parsing, ignore all chunks without cname,
      // but do not fail the parse.
      RTC_LOG(LS_WARNING) << "CNAME not found for ssrc " << chunks[i].ssrc;
      --number_of_chunks;
      chunks.resize(number_of_chunks);
    }
    // Adjust to 32bit boundary.
    looking_at += (payload_end - looking_at) % 4;
  }

  chunks_ = std::move(chunks);
  block_length_ = block_length;
  return true;
}

bool Sdes::AddCName(uint32_t ssrc, std::string cname) {
  RTC_DCHECK_LE(cname.length(), 0xffu);
  if (chunks_.size() >= kMaxNumberOfChunks) {
    RTC_LOG(LS_WARNING) << "Max SDES chunks reached.";
    return false;
  }
  Chunk chunk;
  chunk.ssrc = ssrc;
  chunk.cname = std::move(cname);
  chunks_.push_back(chunk);
  block_length_ += ChunkSize(chunk);
  return true;
}

size_t Sdes::BlockLength() const {
  return block_length_;
}

bool Sdes::Create(uint8_t* packet,
                  size_t* index,
                  size_t max_length,
                  PacketReadyCallback callback) const {
  while (*index + BlockLength() > max_length) {
    if (!OnBufferFull(packet, index, callback))
      return false;
  }
  const size_t index_end = *index + BlockLength();
  CreateHeader(chunks_.size(), kPacketType, HeaderLength(), packet, index);

  for (const Sdes::Chunk& chunk : chunks_) {
    ByteWriter<uint32_t>::WriteBigEndian(&packet[*index + 0], chunk.ssrc);
    ByteWriter<uint8_t>::WriteBigEndian(&packet[*index + 4], kCnameTag);
    ByteWriter<uint8_t>::WriteBigEndian(
        &packet[*index + 5], static_cast<uint8_t>(chunk.cname.size()));
    memcpy(&packet[*index + 6], chunk.cname.data(), chunk.cname.size());
    *index += (6 + chunk.cname.size());

    // In each chunk, the list of items must be terminated by one or more null
    // octets. The next chunk must start on a 32-bit boundary.
    // CNAME (1 byte) | length (1 byte) | name | padding.
    size_t padding_size = 4 - ((6 + chunk.cname.size()) % 4);
    const int kPadding = 0;
    memset(packet + *index, kPadding, padding_size);
    *index += padding_size;
  }

  RTC_CHECK_EQ(*index, index_end);
  return true;
}
}  // namespace rtcp
}  // namespace webrtc
