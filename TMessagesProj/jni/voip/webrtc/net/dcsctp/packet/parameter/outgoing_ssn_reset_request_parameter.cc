/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/outgoing_ssn_reset_request_parameter.h"

#include <stddef.h>

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/internal_types.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "net/dcsctp/public/types.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc6525#section-4.1

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |     Parameter Type = 13       | Parameter Length = 16 + 2 * N |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |           Re-configuration Request Sequence Number            |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |           Re-configuration Response Sequence Number           |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                Sender's Last Assigned TSN                     |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Stream Number 1 (optional)   |    Stream Number 2 (optional) |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  /                            ......                             /
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Stream Number N-1 (optional) |    Stream Number N (optional) |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int OutgoingSSNResetRequestParameter::kType;

absl::optional<OutgoingSSNResetRequestParameter>
OutgoingSSNResetRequestParameter::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  ReconfigRequestSN request_sequence_number(reader->Load32<4>());
  ReconfigRequestSN response_sequence_number(reader->Load32<8>());
  TSN sender_last_assigned_tsn(reader->Load32<12>());

  size_t stream_count = reader->variable_data_size() / kStreamIdSize;
  std::vector<StreamID> stream_ids;
  stream_ids.reserve(stream_count);
  for (size_t i = 0; i < stream_count; ++i) {
    BoundedByteReader<kStreamIdSize> sub_reader =
        reader->sub_reader<kStreamIdSize>(i * kStreamIdSize);

    stream_ids.push_back(StreamID(sub_reader.Load16<0>()));
  }

  return OutgoingSSNResetRequestParameter(
      request_sequence_number, response_sequence_number,
      sender_last_assigned_tsn, std::move(stream_ids));
}

void OutgoingSSNResetRequestParameter::SerializeTo(
    std::vector<uint8_t>& out) const {
  size_t variable_size = stream_ids_.size() * kStreamIdSize;
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, variable_size);

  writer.Store32<4>(*request_sequence_number_);
  writer.Store32<8>(*response_sequence_number_);
  writer.Store32<12>(*sender_last_assigned_tsn_);

  for (size_t i = 0; i < stream_ids_.size(); ++i) {
    BoundedByteWriter<kStreamIdSize> sub_writer =
        writer.sub_writer<kStreamIdSize>(i * kStreamIdSize);
    sub_writer.Store16<0>(*stream_ids_[i]);
  }
}

std::string OutgoingSSNResetRequestParameter::ToString() const {
  rtc::StringBuilder sb;
  sb << "Outgoing SSN Reset Request, req_seq_nbr=" << *request_sequence_number()
     << ", resp_seq_nbr=" << *response_sequence_number()
     << ", sender_last_asg_tsn=" << *sender_last_assigned_tsn();
  return sb.Release();
}

}  // namespace dcsctp
