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

#ifndef RTC_BASE_CONTAINERS_AS_CONST_H_
#define RTC_BASE_CONTAINERS_AS_CONST_H_

#include <type_traits>

namespace webrtc {

// C++14 implementation of C++17's std::as_const():
// https://en.cppreference.com/w/cpp/utility/as_const
template <typename T>
constexpr std::add_const_t<T>& as_const(T& t) noexcept {
  return t;
}

template <typename T>
void as_const(const T&& t) = delete;

}  // namespace webrtc

#endif  // RTC_BASE_CONTAINERS_AS_CONST_H_
