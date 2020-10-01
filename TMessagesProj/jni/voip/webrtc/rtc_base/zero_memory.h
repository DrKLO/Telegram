/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ZERO_MEMORY_H_
#define RTC_BASE_ZERO_MEMORY_H_

#include <stddef.h>

#include <type_traits>

#include "api/array_view.h"

namespace rtc {

// Fill memory with zeros in a way that the compiler doesn't optimize it away
// even if the pointer is not used afterwards.
void ExplicitZeroMemory(void* ptr, size_t len);

template <typename T,
          typename std::enable_if<!std::is_const<T>::value &&
                                  std::is_trivial<T>::value>::type* = nullptr>
void ExplicitZeroMemory(rtc::ArrayView<T> a) {
  ExplicitZeroMemory(a.data(), a.size());
}

}  // namespace rtc

#endif  // RTC_BASE_ZERO_MEMORY_H_
