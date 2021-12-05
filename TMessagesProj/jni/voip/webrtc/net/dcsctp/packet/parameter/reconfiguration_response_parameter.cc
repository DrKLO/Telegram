/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/parameter/reconfiguration_response_parameter.h"

#include <stddef.h>
#include <stdint.h>

#include <string>
#include <type_traits>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc6525#section-4.4

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |     Parameter Type = 16       |      Parameter Length         |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |         Re-configuration Response Sequence Number             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                            Result                             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                   Sender's Next TSN (optional)                |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                  Receiver's Next TSN (optional)               |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int ReconfigurationResponseParameter::kType;

absl::string_view ToString(ReconfigurationResponseParameter::Result result) {
  switch (result) {
    case ReconfigurationResponseParameter::Result::kSuccessNothingToDo:
      return "Success: nothing to do";
    case ReconfigurationResponseParameter::Result::kSuccessPerformed:
      return "Success: performed";
    case ReconfigurationResponseParameter::Result::kDenied:
      return "Denied";
    case ReconfigurationResponseParameter::Result::kErrorWrongSSN:
      return "Error: wrong ssn";
    case ReconfigurationResponseParameter::Result::
        kErrorRequestAlreadyInProgress:
      return "Error: request already in progress";
    case ReconfigurationResponseParameter::Result::kErrorBadSequenceNumber:
      return "Error: bad sequence number";
    case ReconfigurationResponseParameter::Result::kInProgress:
      return "In progress";
  }
}

absl::optional<ReconfigurationResponseParameter>
ReconfigurationResponseParameter::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  ReconfigRequestSN response_sequence_number(reader->Load32<4>());
  Result result;
  uint32_t result_nbr = reader->Load32<8>();
  switch (result_nbr) {
    case 0:
      result = ReconfigurationResponseParameter::Result::kSuccessNothingToDo;
      break;
    case 1:
      result = ReconfigurationResponseParameter::Result::kSuccessPerformed;
      break;
    case 2:
      result = ReconfigurationResponseParameter::Result::kDenied;
      break;
    case 3:
      result = ReconfigurationResponseParameter::Result::kErrorWrongSSN;
      break;
    case 4:
      result = ReconfigurationResponseParameter::Result::
          kErrorRequestAlreadyInProgress;
      break;
    case 5:
      result =
          ReconfigurationResponseParameter::Result::kErrorBadSequenceNumber;
      break;
    case 6:
      result = ReconfigurationResponseParameter::Result::kInProgress;
      break;
    default:
      RTC_DLOG(LS_WARNING) << "Invalid reconfig response result: "
                           << result_nbr;
      return absl::nullopt;
  }

  if (reader->variable_data().empty()) {
    return ReconfigurationResponseParameter(response_sequence_number, result);
  } else if (reader->variable_data_size() != kNextTsnHeaderSize) {
    RTC_DLOG(LS_WARNING) << "Invalid parameter size";
    return absl::nullopt;
  }

  BoundedByteReader<kNextTsnHeaderSize> sub_reader =
      reader->sub_reader<kNextTsnHeaderSize>(0);

  TSN sender_next_tsn(sub_reader.Load32<0>());
  TSN receiver_next_tsn(sub_reader.Load32<4>());

  return ReconfigurationResponseParameter(response_sequence_number, result,
                                          sender_next_tsn, receiver_next_tsn);
}

void ReconfigurationResponseParameter::SerializeTo(
    std::vector<uint8_t>& out) const {
  size_t variable_size =
      (sender_next_tsn().has_value() ? kNextTsnHeaderSize : 0);
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, variable_size);

  writer.Store32<4>(*response_sequence_number_);
  uint32_t result_nbr =
      static_cast<std::underlying_type<Result>::type>(result_);
  writer.Store32<8>(result_nbr);

  if (sender_next_tsn().has_value()) {
    BoundedByteWriter<kNextTsnHeaderSize> sub_writer =
        writer.sub_writer<kNextTsnHeaderSize>(0);

    sub_writer.Store32<0>(sender_next_tsn_.has_value() ? **sender_next_tsn_
                                                       : 0);
    sub_writer.Store32<4>(receiver_next_tsn_.has_value() ? **receiver_next_tsn_
                                                         : 0);
  }
}

std::string ReconfigurationResponseParameter::ToString() const {
  rtc::StringBuilder sb;
  sb << "Re-configuration Response, resp_seq_nbr="
     << *response_sequence_number();
  return sb.Release();
}
}  // namespace dcsctp
