/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"

#include <stddef.h>
#include <stdint.h>

#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc3758#section-3.2

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Type = 192  |  Flags = 0x00 |        Length = Variable      |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                      New Cumulative TSN                       |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |         Stream-1              |       Stream Sequence-1       |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  \                                                               /
//  /                                                               \
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |         Stream-N              |       Stream Sequence-N       |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int ForwardTsnChunk::kType;

absl::optional<ForwardTsnChunk> ForwardTsnChunk::Parse(
    rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }
  TSN new_cumulative_tsn(reader->Load32<4>());

  size_t streams_skipped =
      reader->variable_data_size() / kSkippedStreamBufferSize;

  std::vector<SkippedStream> skipped_streams;
  skipped_streams.reserve(streams_skipped);
  for (size_t i = 0; i < streams_skipped; ++i) {
    BoundedByteReader<kSkippedStreamBufferSize> sub_reader =
        reader->sub_reader<kSkippedStreamBufferSize>(i *
                                                     kSkippedStreamBufferSize);

    StreamID stream_id(sub_reader.Load16<0>());
    SSN ssn(sub_reader.Load16<2>());
    skipped_streams.emplace_back(stream_id, ssn);
  }
  return ForwardTsnChunk(new_cumulative_tsn, std::move(skipped_streams));
}

void ForwardTsnChunk::SerializeTo(std::vector<uint8_t>& out) const {
  rtc::ArrayView<const SkippedStream> skipped = skipped_streams();
  size_t variable_size = skipped.size() * kSkippedStreamBufferSize;
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, variable_size);

  writer.Store32<4>(*new_cumulative_tsn());
  for (size_t i = 0; i < skipped.size(); ++i) {
    BoundedByteWriter<kSkippedStreamBufferSize> sub_writer =
        writer.sub_writer<kSkippedStreamBufferSize>(i *
                                                    kSkippedStreamBufferSize);
    sub_writer.Store16<0>(*skipped[i].stream_id);
    sub_writer.Store16<2>(*skipped[i].ssn);
  }
}

std::string ForwardTsnChunk::ToString() const {
  rtc::StringBuilder sb;
  sb << "FORWARD-TSN, new_cumulative_tsn=" << *new_cumulative_tsn();
  return sb.str();
}
}  // namespace dcsctp
