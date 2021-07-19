/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk/shutdown_complete_chunk.h"

#include <stdint.h>

#include <type_traits>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.13

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Type = 14   |Reserved     |T|      Length = 4               |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int ShutdownCompleteChunk::kType;

absl::optional<ShutdownCompleteChunk> ShutdownCompleteChunk::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }
  uint8_t flags = reader->Load8<1>();
  bool tag_reflected = (flags & (1 << kFlagsBitT)) != 0;
  return ShutdownCompleteChunk(tag_reflected);
}

void ShutdownCompleteChunk::SerializeTo(std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out);
  writer.Store8<1>(tag_reflected_ ? (1 << kFlagsBitT) : 0);
}

std::string ShutdownCompleteChunk::ToString() const {
  return "SHUTDOWN-COMPLETE";
}

}  // namespace dcsctp
