/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/supported_extensions_parameter.h"

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/str_join.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc5061#section-4.2.7

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |     Parameter Type = 0x8008   |      Parameter Length         |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  | CHUNK TYPE 1  |  CHUNK TYPE 2 |  CHUNK TYPE 3 |  CHUNK TYPE 4 |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                             ....                              |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  | CHUNK TYPE N  |      PAD      |      PAD      |      PAD      |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int SupportedExtensionsParameter::kType;

absl::optional<SupportedExtensionsParameter>
SupportedExtensionsParameter::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  std::vector<uint8_t> chunk_types(reader->variable_data().begin(),
                                   reader->variable_data().end());
  return SupportedExtensionsParameter(std::move(chunk_types));
}

void SupportedExtensionsParameter::SerializeTo(
    std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, chunk_types_.size());
  writer.CopyToVariableData(chunk_types_);
}

std::string SupportedExtensionsParameter::ToString() const {
  rtc::StringBuilder sb;
  sb << "Supported Extensions (" << StrJoin(chunk_types_, ", ") << ")";
  return sb.Release();
}
}  // namespace dcsctp
