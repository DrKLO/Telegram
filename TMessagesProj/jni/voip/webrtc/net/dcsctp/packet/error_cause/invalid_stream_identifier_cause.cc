/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/error_cause/invalid_stream_identifier_cause.h"

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

// https://tools.ietf.org/html/rfc4960#section-3.3.10.1

//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |     Cause Code=1              |      Cause Length=8           |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |        Stream Identifier      |         (Reserved)            |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int InvalidStreamIdentifierCause::kType;

absl::optional<InvalidStreamIdentifierCause>
InvalidStreamIdentifierCause::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  StreamID stream_id(reader->Load16<4>());
  return InvalidStreamIdentifierCause(stream_id);
}

void InvalidStreamIdentifierCause::SerializeTo(
    std::vector<uint8_t>& out) const {
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out);

  writer.Store16<4>(*stream_id_);
}

std::string InvalidStreamIdentifierCause::ToString() const {
  rtc::StringBuilder sb;
  sb << "Invalid Stream Identifier, stream_id=" << *stream_id_;
  return sb.Release();
}

}  // namespace dcsctp
