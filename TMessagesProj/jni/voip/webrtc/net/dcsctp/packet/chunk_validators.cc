/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/chunk_validators.h"

#include <algorithm>
#include <utility>
#include <vector>

#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "rtc_base/logging.h"

namespace dcsctp {

SackChunk ChunkValidators::Clean(SackChunk&& sack) {
  if (Validate(sack)) {
    return std::move(sack);
  }

  RTC_DLOG(LS_WARNING) << "Received SACK is malformed; cleaning it";

  std::vector<SackChunk::GapAckBlock> gap_ack_blocks;
  gap_ack_blocks.reserve(sack.gap_ack_blocks().size());

  // First: Only keep blocks that are sane
  for (const SackChunk::GapAckBlock& gap_ack_block : sack.gap_ack_blocks()) {
    if (gap_ack_block.end > gap_ack_block.start) {
      gap_ack_blocks.emplace_back(gap_ack_block);
    }
  }

  // Not more than at most one remaining? Exit early.
  if (gap_ack_blocks.size() <= 1) {
    return SackChunk(sack.cumulative_tsn_ack(), sack.a_rwnd(),
                     std::move(gap_ack_blocks), sack.duplicate_tsns());
  }

  // Sort the intervals by their start value, to aid in the merging below.
  absl::c_sort(gap_ack_blocks, [&](const SackChunk::GapAckBlock& a,
                                   const SackChunk::GapAckBlock& b) {
    return a.start < b.start;
  });

  // Merge overlapping ranges.
  std::vector<SackChunk::GapAckBlock> merged;
  merged.reserve(gap_ack_blocks.size());
  merged.push_back(gap_ack_blocks[0]);

  for (size_t i = 1; i < gap_ack_blocks.size(); ++i) {
    if (merged.back().end + 1 >= gap_ack_blocks[i].start) {
      merged.back().end = std::max(merged.back().end, gap_ack_blocks[i].end);
    } else {
      merged.push_back(gap_ack_blocks[i]);
    }
  }

  return SackChunk(sack.cumulative_tsn_ack(), sack.a_rwnd(), std::move(merged),
                   sack.duplicate_tsns());
}

bool ChunkValidators::Validate(const SackChunk& sack) {
  if (sack.gap_ack_blocks().empty()) {
    return true;
  }

  // Ensure that gap-ack-blocks are sorted, has an "end" that is not before
  // "start" and are non-overlapping and non-adjacent.
  uint16_t prev_end = 0;
  for (const SackChunk::GapAckBlock& gap_ack_block : sack.gap_ack_blocks()) {
    if (gap_ack_block.end < gap_ack_block.start) {
      return false;
    }
    if (gap_ack_block.start <= (prev_end + 1)) {
      return false;
    }
    prev_end = gap_ack_block.end;
  }
  return true;
}

}  // namespace dcsctp
