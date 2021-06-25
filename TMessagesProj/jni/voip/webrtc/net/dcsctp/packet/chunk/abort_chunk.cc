/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk/abort_chunk.h"

#include <stdint.h>

#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/error_cause/error_cause.h"
#include "net/dcsctp/packet/tlv_trait.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.7

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Type = 6    |Reserved     |T|           Length              |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  \                                                               \
//  /                   zero or more Error Causes                   /
//  \                                                               \
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int AbortChunk::kType;

absl::optional<AbortChunk> AbortChunk::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }
  absl::optional<Parameters> error_causes =
      Parameters::Parse(reader->variable_data());
  if (!error_causes.has_value()) {
    return absl::nullopt;
  }
  uint8_t flags = reader->Load8<1>();
  bool filled_in_verification_tag = (flags & (1 << kFlagsBitT)) == 0;
  return AbortChunk(filled_in_verification_tag, *std::move(error_causes));
}

void AbortChunk::SerializeTo(std::vector<uint8_t>& out) const {
  rtc::ArrayView<const uint8_t> error_causes = error_causes_.data();
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, error_causes.size());
  writer.Store8<1>(filled_in_verification_tag_ ? 0 : (1 << kFlagsBitT));
  writer.CopyToVariableData(error_causes);
}

std::string AbortChunk::ToString() const {
  return "ABORT";
}
}  // namespace dcsctp
