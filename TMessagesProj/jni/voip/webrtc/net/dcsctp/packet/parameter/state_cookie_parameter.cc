/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/state_cookie_parameter.h"

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

// https://tools.ietf.org/html/rfc4960#section-3.3.3.1

constexpr int StateCookieParameter::kType;

absl::optional<StateCookieParameter> StateCookieParameter::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }
  return StateCookieParameter(reader->variable_data());
}

void StateCookieParameter::SerializeTo(std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, data_.size());
  writer.CopyToVariableData(data_);
}

std::string StateCookieParameter::ToString() const {
  rtc::StringBuilder sb;
  sb << "State Cookie parameter (cookie_length=" << data_.size() << ")";
  return sb.Release();
}

}  // namespace dcsctp
