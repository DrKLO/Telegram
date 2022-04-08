/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/sctp_packet.h"

#include <stddef.h>

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/math.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/chunk/chunk.h"
#include "net/dcsctp/packet/crc32c.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_format.h"

namespace dcsctp {
namespace {
constexpr size_t kMaxUdpPacketSize = 65535;
constexpr size_t kChunkTlvHeaderSize = 4;
constexpr size_t kExpectedDescriptorCount = 4;
}  // namespace

/*
  0                   1                   2                   3
  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |     Source Port Number        |     Destination Port Number   |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |                      Verification Tag                         |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |                           Checksum                            |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
*/

SctpPacket::Builder::Builder(VerificationTag verification_tag,
                             const DcSctpOptions& options)
    : verification_tag_(verification_tag),
      source_port_(options.local_port),
      dest_port_(options.remote_port),
      max_packet_size_(RoundDownTo4(options.mtu)) {}

SctpPacket::Builder& SctpPacket::Builder::Add(const Chunk& chunk) {
  if (out_.empty()) {
    out_.reserve(max_packet_size_);
    out_.resize(SctpPacket::kHeaderSize);
    BoundedByteWriter<kHeaderSize> buffer(out_);
    buffer.Store16<0>(source_port_);
    buffer.Store16<2>(dest_port_);
    buffer.Store32<4>(*verification_tag_);
    // Checksum is at offset 8 - written when calling Build();
  }
  RTC_DCHECK(IsDivisibleBy4(out_.size()));

  chunk.SerializeTo(out_);
  if (out_.size() % 4 != 0) {
    out_.resize(RoundUpTo4(out_.size()));
  }

  RTC_DCHECK(out_.size() <= max_packet_size_)
      << "Exceeded max size, data=" << out_.size()
      << ", max_size=" << max_packet_size_;
  return *this;
}

size_t SctpPacket::Builder::bytes_remaining() const {
  if (out_.empty()) {
    // The packet header (CommonHeader) hasn't been written yet:
    return max_packet_size_ - kHeaderSize;
  } else if (out_.size() > max_packet_size_) {
    RTC_DCHECK_NOTREACHED() << "Exceeded max size, data=" << out_.size()
                            << ", max_size=" << max_packet_size_;
    return 0;
  }
  return max_packet_size_ - out_.size();
}

std::vector<uint8_t> SctpPacket::Builder::Build() {
  std::vector<uint8_t> out;
  out_.swap(out);

  if (!out.empty()) {
    uint32_t crc = GenerateCrc32C(out);
    BoundedByteWriter<kHeaderSize>(out).Store32<8>(crc);
  }

  RTC_DCHECK(out.size() <= max_packet_size_)
      << "Exceeded max size, data=" << out.size()
      << ", max_size=" << max_packet_size_;

  return out;
}

absl::optional<SctpPacket> SctpPacket::Parse(
    rtc::ArrayView<const uint8_t> data,
    bool disable_checksum_verification) {
  if (data.size() < kHeaderSize + kChunkTlvHeaderSize ||
      data.size() > kMaxUdpPacketSize) {
    RTC_DLOG(LS_WARNING) << "Invalid packet size";
    return absl::nullopt;
  }

  BoundedByteReader<kHeaderSize> reader(data);

  CommonHeader common_header;
  common_header.source_port = reader.Load16<0>();
  common_header.destination_port = reader.Load16<2>();
  common_header.verification_tag = VerificationTag(reader.Load32<4>());
  common_header.checksum = reader.Load32<8>();

  // Create a copy of the packet, which will be held by this object.
  std::vector<uint8_t> data_copy =
      std::vector<uint8_t>(data.begin(), data.end());

  // Verify the checksum. The checksum field must be zero when that's done.
  BoundedByteWriter<kHeaderSize>(data_copy).Store32<8>(0);
  uint32_t calculated_checksum = GenerateCrc32C(data_copy);
  if (!disable_checksum_verification &&
      calculated_checksum != common_header.checksum) {
    RTC_DLOG(LS_WARNING) << rtc::StringFormat(
        "Invalid packet checksum, packet_checksum=0x%08x, "
        "calculated_checksum=0x%08x",
        common_header.checksum, calculated_checksum);
    return absl::nullopt;
  }
  // Restore the checksum in the header.
  BoundedByteWriter<kHeaderSize>(data_copy).Store32<8>(common_header.checksum);

  // Validate and parse the chunk headers in the message.
  /*
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |   Chunk Type  | Chunk  Flags  |        Chunk Length           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  */

  std::vector<ChunkDescriptor> descriptors;
  descriptors.reserve(kExpectedDescriptorCount);
  rtc::ArrayView<const uint8_t> descriptor_data =
      rtc::ArrayView<const uint8_t>(data_copy).subview(kHeaderSize);
  while (!descriptor_data.empty()) {
    if (descriptor_data.size() < kChunkTlvHeaderSize) {
      RTC_DLOG(LS_WARNING) << "Too small chunk";
      return absl::nullopt;
    }
    BoundedByteReader<kChunkTlvHeaderSize> chunk_header(descriptor_data);
    uint8_t type = chunk_header.Load8<0>();
    uint8_t flags = chunk_header.Load8<1>();
    uint16_t length = chunk_header.Load16<2>();
    uint16_t padded_length = RoundUpTo4(length);
    if (padded_length > descriptor_data.size()) {
      RTC_DLOG(LS_WARNING) << "Too large chunk. length=" << length
                           << ", remaining=" << descriptor_data.size();
      return absl::nullopt;
    } else if (padded_length < kChunkTlvHeaderSize) {
      RTC_DLOG(LS_WARNING) << "Too small chunk. length=" << length;
      return absl::nullopt;
    }
    descriptors.emplace_back(type, flags,
                             descriptor_data.subview(0, padded_length));
    descriptor_data = descriptor_data.subview(padded_length);
  }

  // Note that iterators (and pointer) are guaranteed to be stable when moving a
  // std::vector, and `descriptors` have pointers to within `data_copy`.
  return SctpPacket(common_header, std::move(data_copy),
                    std::move(descriptors));
}
}  // namespace dcsctp
