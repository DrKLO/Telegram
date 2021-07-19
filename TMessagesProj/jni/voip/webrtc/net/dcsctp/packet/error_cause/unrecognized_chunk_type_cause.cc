/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/error_cause/unrecognized_chunk_type_cause.h"

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.10.6

//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |     Cause Code=6              |      Cause Length             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  /                  Unrecognized Chunk                           /
//  \                                                               \
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int UnrecognizedChunkTypeCause::kType;

absl::optional<UnrecognizedChunkTypeCause> UnrecognizedChunkTypeCause::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }
  std::vector<uint8_t> unrecognized_chunk(reader->variable_data().begin(),
                                          reader->variable_data().end());
  return UnrecognizedChunkTypeCause(std::move(unrecognized_chunk));
}

void UnrecognizedChunkTypeCause::SerializeTo(std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer =
      AllocateTLV(out, unrecognized_chunk_.size());
  writer.CopyToVariableData(unrecognized_chunk_);
}

std::string UnrecognizedChunkTypeCause::ToString() const {
  rtc::StringBuilder sb;
  sb << "Unrecognized Chunk Type, chunk_type=";
  if (!unrecognized_chunk_.empty()) {
    sb << static_cast<int>(unrecognized_chunk_[0]);
  } else {
    sb << "<missing>";
  }
  return sb.Release();
}

}  // namespace dcsctp
