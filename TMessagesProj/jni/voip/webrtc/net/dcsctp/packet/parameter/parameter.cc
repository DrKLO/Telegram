/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/parameter.h"

#include <stddef.h>

#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/math.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/parameter/add_incoming_streams_request_parameter.h"
#include "net/dcsctp/packet/parameter/add_outgoing_streams_request_parameter.h"
#include "net/dcsctp/packet/parameter/forward_tsn_supported_parameter.h"
#include "net/dcsctp/packet/parameter/heartbeat_info_parameter.h"
#include "net/dcsctp/packet/parameter/incoming_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/outgoing_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/reconfiguration_response_parameter.h"
#include "net/dcsctp/packet/parameter/ssn_tsn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/state_cookie_parameter.h"
#include "net/dcsctp/packet/parameter/supported_extensions_parameter.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

constexpr size_t kParameterHeaderSize = 4;

Parameters::Builder& Parameters::Builder::Add(const Parameter& p) {
  // https://tools.ietf.org/html/rfc4960#section-3.2.1
  // "If the length of the parameter is not a multiple of 4 bytes, the sender
  // pads the parameter at the end (i.e., after the Parameter Value field) with
  // all zero bytes."
  if (data_.size() % 4 != 0) {
    data_.resize(RoundUpTo4(data_.size()));
  }

  p.SerializeTo(data_);
  return *this;
}

std::vector<ParameterDescriptor> Parameters::descriptors() const {
  rtc::ArrayView<const uint8_t> span(data_);
  std::vector<ParameterDescriptor> result;
  while (!span.empty()) {
    BoundedByteReader<kParameterHeaderSize> header(span);
    uint16_t type = header.Load16<0>();
    uint16_t length = header.Load16<2>();
    result.emplace_back(type, span.subview(0, length));
    size_t length_with_padding = RoundUpTo4(length);
    if (length_with_padding > span.size()) {
      break;
    }
    span = span.subview(length_with_padding);
  }
  return result;
}

absl::optional<Parameters> Parameters::Parse(
    rtc::ArrayView<const uint8_t> data) {
  // Validate the parameter descriptors
  rtc::ArrayView<const uint8_t> span(data);
  while (!span.empty()) {
    if (span.size() < kParameterHeaderSize) {
      RTC_DLOG(LS_WARNING) << "Insufficient parameter length";
      return absl::nullopt;
    }
    BoundedByteReader<kParameterHeaderSize> header(span);
    uint16_t length = header.Load16<2>();
    if (length < kParameterHeaderSize || length > span.size()) {
      RTC_DLOG(LS_WARNING) << "Invalid parameter length field";
      return absl::nullopt;
    }
    size_t length_with_padding = RoundUpTo4(length);
    if (length_with_padding > span.size()) {
      break;
    }
    span = span.subview(length_with_padding);
  }
  return Parameters(std::vector<uint8_t>(data.begin(), data.end()));
}
}  // namespace dcsctp
