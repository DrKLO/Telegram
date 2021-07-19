/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/error_cause/protocol_violation_cause.h"

#include <stdint.h>

#include <string>
#include <type_traits>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.10.13

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |         Cause Code=13         |      Cause Length=Variable    |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  /                    Additional Information                     /
//  \                                                               \
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int ProtocolViolationCause::kType;

absl::optional<ProtocolViolationCause> ProtocolViolationCause::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }
  return ProtocolViolationCause(
      std::string(reinterpret_cast<const char*>(reader->variable_data().data()),
                  reader->variable_data().size()));
}

void ProtocolViolationCause::SerializeTo(std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer =
      AllocateTLV(out, additional_information_.size());
  writer.CopyToVariableData(rtc::MakeArrayView(
      reinterpret_cast<const uint8_t*>(additional_information_.data()),
      additional_information_.size()));
}

std::string ProtocolViolationCause::ToString() const {
  rtc::StringBuilder sb;
  sb << "Protocol Violation, additional_information="
     << additional_information_;
  return sb.Release();
}

}  // namespace dcsctp
