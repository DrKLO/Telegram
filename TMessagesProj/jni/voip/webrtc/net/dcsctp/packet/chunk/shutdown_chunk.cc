/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk/shutdown_chunk.h"

#include <stdint.h>

#include <type_traits>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.8

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Type = 7    | Chunk  Flags  |      Length = 8               |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                      Cumulative TSN Ack                       |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int ShutdownChunk::kType;

absl::optional<ShutdownChunk> ShutdownChunk::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  TSN cumulative_tsn_ack(reader->Load32<4>());
  return ShutdownChunk(cumulative_tsn_ack);
}

void ShutdownChunk::SerializeTo(std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out);
  writer.Store32<4>(*cumulative_tsn_ack_);
}

std::string ShutdownChunk::ToString() const {
  return "SHUTDOWN";
}
}  // namespace dcsctp
