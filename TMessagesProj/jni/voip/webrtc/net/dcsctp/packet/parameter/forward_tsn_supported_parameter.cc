/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/forward_tsn_supported_parameter.h"

#include <stdint.h>

#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc3758#section-3.1

//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |    Parameter Type = 49152     |  Parameter Length = 4         |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int ForwardTsnSupportedParameter::kType;

absl::optional<ForwardTsnSupportedParameter>
ForwardTsnSupportedParameter::Parse(rtc::ArrayView<const uint8_t> data) {
  if (!ParseTLV(data).has_value()) {
    return absl::nullopt;
  }
  return ForwardTsnSupportedParameter();
}

void ForwardTsnSupportedParameter::SerializeTo(
    std::vector<uint8_t>& out) const {
  AllocateTLV(out);
}

std::string ForwardTsnSupportedParameter::ToString() const {
  return "Forward TSN Supported";
}
}  // namespace dcsctp
