/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This implementation is borrowed from Chromium.

#ifndef RTC_BASE_CONTAINERS_VOID_T_H_
#define RTC_BASE_CONTAINERS_VOID_T_H_

namespace webrtc {
namespace void_t_internal {
// Implementation detail of webrtc::void_t below.
template <typename...>
struct make_void {
  using type = void;
};

}  // namespace void_t_internal

// webrtc::void_t is an implementation of std::void_t from C++17.
//
// We use `webrtc::void_t_internal::make_void` as a helper struct to avoid a
// C++14 defect:
//   http://en.cppreference.com/w/cpp/types/void_t
//   http://open-std.org/JTC1/SC22/WG21/docs/cwg_defects.html#1558
template <typename... Ts>
using void_t = typename ::webrtc::void_t_internal::make_void<Ts...>::type;
}  // namespace webrtc

#endif  // RTC_BASE_CONTAINERS_VOID_T_H_
