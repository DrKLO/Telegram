/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/rx/reassembly_streams.h"

#include <cstddef>
#include <map>
#include <utility>

namespace dcsctp {

ReassembledMessage AssembleMessage(std::map<UnwrappedTSN, Data>::iterator start,
                                   std::map<UnwrappedTSN, Data>::iterator end) {
  size_t count = std::distance(start, end);

  if (count == 1) {
    // Fast path - zero-copy
    Data& data = start->second;

    return ReassembledMessage{
        .tsns = {start->first},
        .message = DcSctpMessage(data.stream_id, data.ppid,
                                 std::move(start->second.payload)),
    };
  }

  // Slow path - will need to concatenate the payload.
  std::vector<UnwrappedTSN> tsns;
  std::vector<uint8_t> payload;

  size_t payload_size = std::accumulate(
      start, end, 0,
      [](size_t v, const auto& p) { return v + p.second.size(); });

  tsns.reserve(count);
  payload.reserve(payload_size);
  for (auto it = start; it != end; ++it) {
    Data& data = it->second;
    tsns.push_back(it->first);
    payload.insert(payload.end(), data.payload.begin(), data.payload.end());
  }

  return ReassembledMessage{
      .tsns = std::move(tsns),
      .message = DcSctpMessage(start->second.stream_id, start->second.ppid,
                               std::move(payload)),
  };
}
}  // namespace dcsctp
