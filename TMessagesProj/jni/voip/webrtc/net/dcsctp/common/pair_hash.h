/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_COMMON_PAIR_HASH_H_
#define NET_DCSCTP_COMMON_PAIR_HASH_H_

#include <stddef.h>

#include <functional>
#include <utility>

namespace dcsctp {

// A custom hash function for std::pair, to be able to be used as key in a
// std::unordered_map. If absl::flat_hash_map would ever be used, this is
// unnecessary as it already has a hash function for std::pair.
struct PairHash {
  template <class T1, class T2>
  size_t operator()(const std::pair<T1, T2>& p) const {
    return (3 * std::hash<T1>{}(p.first)) ^ std::hash<T2>{}(p.second);
  }
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_COMMON_PAIR_HASH_H_
