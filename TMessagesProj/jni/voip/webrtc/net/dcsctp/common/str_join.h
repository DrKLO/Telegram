/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_COMMON_STR_JOIN_H_
#define NET_DCSCTP_COMMON_STR_JOIN_H_

#include <string>

#include "absl/strings/string_view.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

template <typename Range>
std::string StrJoin(const Range& seq, absl::string_view delimiter) {
  rtc::StringBuilder sb;
  int idx = 0;

  for (const typename Range::value_type& elem : seq) {
    if (idx > 0) {
      sb << delimiter;
    }
    sb << elem;

    ++idx;
  }
  return sb.Release();
}

template <typename Range, typename Functor>
std::string StrJoin(const Range& seq,
                    absl::string_view delimiter,
                    const Functor& fn) {
  rtc::StringBuilder sb;
  int idx = 0;

  for (const typename Range::value_type& elem : seq) {
    if (idx > 0) {
      sb << delimiter;
    }
    fn(sb, elem);

    ++idx;
  }
  return sb.Release();
}

}  // namespace dcsctp

#endif  // NET_DCSCTP_COMMON_STR_JOIN_H_
