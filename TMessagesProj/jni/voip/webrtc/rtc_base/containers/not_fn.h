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

#ifndef RTC_BASE_CONTAINERS_NOT_FN_H_
#define RTC_BASE_CONTAINERS_NOT_FN_H_

#include <type_traits>
#include <utility>

#include "rtc_base/containers/invoke.h"

namespace webrtc {

namespace not_fn_internal {

template <typename F>
struct NotFnImpl {
  F f;

  template <typename... Args>
  constexpr decltype(auto) operator()(Args&&... args) & noexcept {
    return !webrtc::invoke(f, std::forward<Args>(args)...);
  }

  template <typename... Args>
  constexpr decltype(auto) operator()(Args&&... args) const& noexcept {
    return !webrtc::invoke(f, std::forward<Args>(args)...);
  }

  template <typename... Args>
  constexpr decltype(auto) operator()(Args&&... args) && noexcept {
    return !webrtc::invoke(std::move(f), std::forward<Args>(args)...);
  }

  template <typename... Args>
  constexpr decltype(auto) operator()(Args&&... args) const&& noexcept {
    return !webrtc::invoke(std::move(f), std::forward<Args>(args)...);
  }
};

}  // namespace not_fn_internal

// Implementation of C++17's std::not_fn.
//
// Reference:
// - https://en.cppreference.com/w/cpp/utility/functional/not_fn
// - https://wg21.link/func.not.fn
template <typename F>
constexpr not_fn_internal::NotFnImpl<std::decay_t<F>> not_fn(F&& f) {
  return {std::forward<F>(f)};
}

}  // namespace webrtc

#endif  // RTC_BASE_CONTAINERS_NOT_FN_H_
