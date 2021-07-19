/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk/init_chunk.h"

#include <stdint.h>

#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/parameter/parameter.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/strings/string_format.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.2

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Type = 1    |  Chunk Flags  |      Chunk Length             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                         Initiate Tag                          |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |           Advertised Receiver Window Credit (a_rwnd)          |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Number of Outbound Streams   |  Number of Inbound Streams    |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                          Initial TSN                          |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  \                                                               \
//  /              Optional/Variable-Length Parameters              /
//  \                                                               \
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int InitChunk::kType;

absl::optional<InitChunk> InitChunk::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  VerificationTag initiate_tag(reader->Load32<4>());
  uint32_t a_rwnd = reader->Load32<8>();
  uint16_t nbr_outbound_streams = reader->Load16<12>();
  uint16_t nbr_inbound_streams = reader->Load16<14>();
  TSN initial_tsn(reader->Load32<16>());

  absl::optional<Parameters> parameters =
      Parameters::Parse(reader->variable_data());
  if (!parameters.has_value()) {
    return absl::nullopt;
  }
  return InitChunk(initiate_tag, a_rwnd, nbr_outbound_streams,
                   nbr_inbound_streams, initial_tsn, *std::move(parameters));
}

void InitChunk::SerializeTo(std::vector<uint8_t>& out) const {
  rtc::ArrayView<const uint8_t> parameters = parameters_.data();
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, parameters.size());

  writer.Store32<4>(*initiate_tag_);
  writer.Store32<8>(a_rwnd_);
  writer.Store16<12>(nbr_outbound_streams_);
  writer.Store16<14>(nbr_inbound_streams_);
  writer.Store32<16>(*initial_tsn_);

  writer.CopyToVariableData(parameters);
}

std::string InitChunk::ToString() const {
  return rtc::StringFormat("INIT, initiate_tag=0x%0x, initial_tsn=%u",
                           *initiate_tag(), *initial_tsn());
}

}  // namespace dcsctp
