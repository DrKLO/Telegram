/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/incoming_ssn_reset_request_parameter.h"

#include <stddef.h>

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

// https://tools.ietf.org/html/rfc6525#section-4.2

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |     Parameter Type = 14       |  Parameter Length = 8 + 2 * N |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |          Re-configuration Request Sequence Number             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Stream Number 1 (optional)   |    Stream Number 2 (optional) |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  /                            ......                             /
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Stream Number N-1 (optional) |    Stream Number N (optional) |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int IncomingSSNResetRequestParameter::kType;

absl::optional<IncomingSSNResetRequestParameter>
IncomingSSNResetRequestParameter::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  ReconfigRequestSN request_sequence_number(reader->Load32<4>());

  size_t stream_count = reader->variable_data_size() / kStreamIdSize;
  std::vector<StreamID> stream_ids;
  stream_ids.reserve(stream_count);
  for (size_t i = 0; i < stream_count; ++i) {
    BoundedByteReader<kStreamIdSize> sub_reader =
        reader->sub_reader<kStreamIdSize>(i * kStreamIdSize);

    stream_ids.push_back(StreamID(sub_reader.Load16<0>()));
  }

  return IncomingSSNResetRequestParameter(request_sequence_number,
                                          std::move(stream_ids));
}

void IncomingSSNResetRequestParameter::SerializeTo(
    std::vector<uint8_t>& out) const {
  size_t variable_size = stream_ids_.size() * kStreamIdSize;
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, variable_size);

  writer.Store32<4>(*request_sequence_number_);

  for (size_t i = 0; i < stream_ids_.size(); ++i) {
    BoundedByteWriter<kStreamIdSize> sub_writer =
        writer.sub_writer<kStreamIdSize>(i * kStreamIdSize);
    sub_writer.Store16<0>(*stream_ids_[i]);
  }
}

std::string IncomingSSNResetRequestParameter::ToString() const {
  rtc::StringBuilder sb;
  sb << "Incoming SSN Reset Request, req_seq_nbr="
     << *request_sequence_number();
  return sb.Release();
}

}  // namespace dcsctp
