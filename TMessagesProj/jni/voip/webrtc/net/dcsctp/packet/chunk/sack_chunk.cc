/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk/sack_chunk.h"

#include <stddef.h>

#include <cstdint>
#include <string>
#include <type_traits>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/str_join.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

// https://tools.ietf.org/html/rfc4960#section-3.3.4

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Type = 3    |Chunk  Flags   |      Chunk Length             |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                      Cumulative TSN Ack                       |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |          Advertised Receiver Window Credit (a_rwnd)           |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  | Number of Gap Ack Blocks = N  |  Number of Duplicate TSNs = X |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  Gap Ack Block #1 Start       |   Gap Ack Block #1 End        |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  /                                                               /
//  \                              ...                              \
//  /                                                               /
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |   Gap Ack Block #N Start      |  Gap Ack Block #N End         |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                       Duplicate TSN 1                         |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  /                                                               /
//  \                              ...                              \
//  /                                                               /
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |                       Duplicate TSN X                         |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr int SackChunk::kType;

absl::optional<SackChunk> SackChunk::Parse(rtc::ArrayView<const uint8_t> data) {
  absl::optional<BoundedByteReader<kHeaderSize>> reader = ParseTLV(data);
  if (!reader.has_value()) {
    return absl::nullopt;
  }

  TSN tsn_ack(reader->Load32<4>());
  uint32_t a_rwnd = reader->Load32<8>();
  uint16_t nbr_of_gap_blocks = reader->Load16<12>();
  uint16_t nbr_of_dup_tsns = reader->Load16<14>();

  if (reader->variable_data_size() != nbr_of_gap_blocks * kGapAckBlockSize +
                                          nbr_of_dup_tsns * kDupTsnBlockSize) {
    RTC_DLOG(LS_WARNING) << "Invalid number of gap blocks or duplicate TSNs";
    return absl::nullopt;
  }

  std::vector<GapAckBlock> gap_ack_blocks;
  gap_ack_blocks.reserve(nbr_of_gap_blocks);
  size_t offset = 0;
  for (int i = 0; i < nbr_of_gap_blocks; ++i) {
    BoundedByteReader<kGapAckBlockSize> sub_reader =
        reader->sub_reader<kGapAckBlockSize>(offset);

    uint16_t start = sub_reader.Load16<0>();
    uint16_t end = sub_reader.Load16<2>();
    gap_ack_blocks.emplace_back(start, end);
    offset += kGapAckBlockSize;
  }

  std::set<TSN> duplicate_tsns;
  for (int i = 0; i < nbr_of_dup_tsns; ++i) {
    BoundedByteReader<kDupTsnBlockSize> sub_reader =
        reader->sub_reader<kDupTsnBlockSize>(offset);

    duplicate_tsns.insert(TSN(sub_reader.Load32<0>()));
    offset += kDupTsnBlockSize;
  }
  RTC_DCHECK(offset == reader->variable_data_size());

  return SackChunk(tsn_ack, a_rwnd, gap_ack_blocks, duplicate_tsns);
}

void SackChunk::SerializeTo(std::vector<uint8_t>& out) const {
  int nbr_of_gap_blocks = gap_ack_blocks_.size();
  int nbr_of_dup_tsns = duplicate_tsns_.size();
  size_t variable_size =
      nbr_of_gap_blocks * kGapAckBlockSize + nbr_of_dup_tsns * kDupTsnBlockSize;
  BoundedByteWriter<kHeaderSize> writer = AllocateTLV(out, variable_size);

  writer.Store32<4>(*cumulative_tsn_ack_);
  writer.Store32<8>(a_rwnd_);
  writer.Store16<12>(nbr_of_gap_blocks);
  writer.Store16<14>(nbr_of_dup_tsns);

  size_t offset = 0;
  for (int i = 0; i < nbr_of_gap_blocks; ++i) {
    BoundedByteWriter<kGapAckBlockSize> sub_writer =
        writer.sub_writer<kGapAckBlockSize>(offset);

    sub_writer.Store16<0>(gap_ack_blocks_[i].start);
    sub_writer.Store16<2>(gap_ack_blocks_[i].end);
    offset += kGapAckBlockSize;
  }

  for (TSN tsn : duplicate_tsns_) {
    BoundedByteWriter<kDupTsnBlockSize> sub_writer =
        writer.sub_writer<kDupTsnBlockSize>(offset);

    sub_writer.Store32<0>(*tsn);
    offset += kDupTsnBlockSize;
  }

  RTC_DCHECK(offset == variable_size);
}

std::string SackChunk::ToString() const {
  rtc::StringBuilder sb;
  sb << "SACK, cum_ack_tsn=" << *cumulative_tsn_ack()
     << ", a_rwnd=" << a_rwnd();
  for (const GapAckBlock& gap : gap_ack_blocks_) {
    uint32_t first = *cumulative_tsn_ack_ + gap.start;
    uint32_t last = *cumulative_tsn_ack_ + gap.end;
    sb << ", gap=" << first << "--" << last;
  }
  if (!duplicate_tsns_.empty()) {
    sb << ", dup_tsns="
       << StrJoin(duplicate_tsns(), ",",
                  [](rtc::StringBuilder& sb, TSN tsn) { sb << *tsn; });
  }

  return sb.Release();
}

}  // namespace dcsctp
